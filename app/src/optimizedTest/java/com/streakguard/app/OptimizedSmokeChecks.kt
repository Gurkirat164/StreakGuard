package com.streakguard.app

import android.content.Context
import android.app.Instrumentation
import android.os.Bundle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.streakguard.app.domain.DailyCheckOrchestrator
import com.streakguard.app.platform.DailyChallenge
import com.streakguard.app.platform.PlatformRegistry
import com.streakguard.app.platform.StreakPlatform
import com.streakguard.app.schedule.StatusSyncWorker
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.concurrent.TimeUnit


/** Checks are linked and optimized together with the isolated QA app. */
object OptimizedSmokeChecks {
    @JvmStatic fun run(context: Context): String {
            runBlocking {
                val app = context.applicationContext as StreakGuardApp
                val c = app.container
                val settings = c.settingsStore
                c.checkOrchestrator.clearUserData()
                settings.setUsername("leetcode", "smoke-alice")
                val fake = FakePlatform()
                fun checker() = DailyCheckOrchestrator(PlatformRegistry(listOf(fake)), settings,
                    c.database, c.notificationHelper, c.reminderState, c.challengeAlarm, c::stopCompletedDayWork)
                val today = TimeUtils.utcToday()
                c.alarmScheduler.scheduleReminderRetry(today, 1, 1, System.currentTimeMillis() + 600_000,
                    c.alarmScheduler.signature())
                c.syncScheduler.schedule(15)
                val first = checker().runCheck(manual = false).single()
                check(first.completed == true && fake.fetches == 1 && fake.streakFetches == 0)
                check(settings.isCompleted())
                check(c.database.checkLogDao().getForDate("leetcode", today)?.completed == true)
                // New orchestrator + SettingsStore read durable completion, not an in-memory flag.
                check(checker().runCheck(manual = true).single().completed == true)
                check(fake.fetches == 1) { "Manual refresh fetched a solved day" }
                check(checker().ifUnsolved(today) { error("Solved day delivered a reminder") } == null)
                val retryPrefs = context.getSharedPreferences("reminder_retries", 0)
                check(!retryPrefs.contains("day_1")) { "Reminder retry survived completion" }
                val wm = WorkManager.getInstance(context)
                check(wm.getWorkInfosForUniqueWork("challenge_status_sync").get(10, TimeUnit.SECONDS)
                    .all { it.state.isFinished }) { "Periodic polling survived completion" }
                // Real WorkManager constructs an optimized worker by its persisted class name.
                val work = OneTimeWorkRequestBuilder<StatusSyncWorker>().build()
                wm.enqueue(work).result.get(10, TimeUnit.SECONDS)
                var info: WorkInfo? = null
                repeat(100) {
                    info = wm.getWorkInfoById(work.id).get(5, TimeUnit.SECONDS)
                    if (info?.state?.isFinished != true) kotlinx.coroutines.delay(100)
                }
                check(info?.state == WorkInfo.State.SUCCEEDED) { "Optimized background worker failed: ${info?.state}" }
                // A different account must be checked, even on a previously solved day.
                settings.setUsername("leetcode", "smoke-bob")
                fake.solved = false
                check(checker().runCheck(manual = true).single().completed == false)
                check(fake.fetches == 2 && !settings.isCompleted())
                // Simulate an expired completion marker; tomorrow automatically resumes checks.
                settings.markCompleted("leetcode", "smoke-bob", LocalDate.parse(today).minusDays(1).toString())
                check(checker().runCheck(manual = false).single().completed == false)
                check(fake.fetches == 3)
                // A stale response crossing UTC midnight must not deliver anything.
                check(checker().ifUnsolved(LocalDate.parse(today).minusDays(1).toString()) {
                    error("Expired day delivered a reminder")
                } == null)
                
                c.checkOrchestrator.clearUserData()
                settings.setUsername("leetcode", "smoke-alice")
                settings.setSyncOnOpen(false)
                fake.solved = true
                checker().runCheck(manual = false)
            }
        return "PASS: optimized Room, persisted completion, no-fetch solved refresh, account change, UTC rollover, retry/poll cancellation, reflective worker startup, stale delivery guard."
    }
    @JvmStatic fun verifyRestart(context: Context): String = runBlocking {
        val c = (context.applicationContext as StreakGuardApp).container
        val fake = FakePlatform()
        check(c.settingsStore.isCompleted()) { "Solved marker did not survive process restart" }
        val checks = DailyCheckOrchestrator(PlatformRegistry(listOf(fake)), c.settingsStore,
            c.database, c.notificationHelper, c.reminderState, c.challengeAlarm, c::stopCompletedDayWork)
        check(checks.runCheck(manual = true).single().completed == true)
        check(fake.fetches == 0) { "Restart fetched an already solved question" }
        "PASS: process restart preserves completion and cached Room data; zero platform fetches."
    }
    private class FakePlatform : StreakPlatform {
        override val id = "leetcode"
        override val displayName = "LeetCode"
        var solved = true
        var fetches = 0
        var streakFetches = 0
        override suspend fun getDailyChallenge(): DailyChallenge {
            fetches++
            return DailyChallenge("Smoke challenge", "smoke", "https://example.com/smoke")
        }
        override suspend fun isCompletedToday(username: String) = solved
        override suspend fun getStreak(username: String): Int { streakFetches++; return 4 }
    }
}

