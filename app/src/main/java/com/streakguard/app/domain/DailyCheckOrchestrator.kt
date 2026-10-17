package com.streakguard.app.domain

import com.streakguard.app.data.local.AppDatabase
import com.streakguard.app.data.local.CheckLog
import com.streakguard.app.data.prefs.SettingsStore
import com.streakguard.app.notify.NotificationHelper
import com.streakguard.app.platform.PlatformRegistry
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import com.streakguard.app.diagnostics.Diagnostics
import com.streakguard.app.diagnostics.DiagnosticEvent

/**
 * Runs the daily check for every enabled platform that has a username set:
 * fetch today's challenge, determine completion, and preserve the latest known data.
 * Scheduled notifications and bounded retries are owned by ReminderCheckWorker.
 */
class DailyCheckOrchestrator(
    private val registry: PlatformRegistry,
    private val settings: SettingsStore,
    private val db: AppDatabase,
    private val notifications: NotificationHelper,
    private val reminderState: com.streakguard.app.schedule.ReminderState,
    private val alarm: com.streakguard.app.notify.ChallengeAlarm,
    private val onCompleted: suspend () -> Unit,
) {
    private val checkMutex = Mutex()

    data class PlatformStatus(
        val platformId: String,
        val displayName: String,
        val challengeTitle: String?,
        val challengeUrl: String?,
        /** null = unknown */
        val completed: Boolean?,
        val streak: Int?,
        val difficulty: String?,
        val questionNumber: String?,
    )

    suspend fun runCheck(manual: Boolean): List<PlatformStatus> = checkMutex.withLock {
        val today = TimeUtils.utcToday()
        Diagnostics.log(DiagnosticEvent.CHECK_STARTED, "manual" to manual, "day" to today)
        val results = mutableListOf<PlatformStatus>()

        for (platform in registry.platforms) {
            val enabled = runCatching { settings.enabled(platform.id).first() }.getOrDefault(true)
            if (!enabled) { Diagnostics.log(DiagnosticEvent.CHECK_SKIPPED, "reason" to "platform_disabled"); continue }

            val username = runCatching { settings.username(platform.id).first() }.getOrDefault("")
            if (username.isBlank()) { Diagnostics.log(DiagnosticEvent.CHECK_SKIPPED, "reason" to "username_missing"); continue }

            val existing = db.checkLogDao().getForDate(platform.id, today)
                .takeIf { settings.checkedUsername(platform.id).first() == username }
            val completedDay = settings.isCompleted(platform.id, today)
            // Also adopt solved rows written by versions before the completion gate.
            if (completedDay || existing?.let { it.known && it.completed } == true) {
                Diagnostics.log(DiagnosticEvent.CHECK_SKIPPED, "reason" to "already_solved", "day" to today)
                if (!completedDay) {
                    settings.markCompleted(platform.id, username, today)
                }
                onCompleted()
                results.add(PlatformStatus(platform.id, platform.displayName, existing?.challengeTitle,
                    existing?.challengeUrl, true, existing?.streak, existing?.difficulty, existing?.questionNumber))
                continue
            }

            var challengeTitle: String? = null
            var challengeUrl: String? = null
            var completed: Boolean? = null
            var streak: Int? = null
            var difficulty: String? = null
            var questionNumber: String? = null
            try {
                val challenge = platform.getDailyChallenge()
                challengeTitle = challenge?.title
                challengeUrl = challenge?.url
                difficulty = challenge?.difficulty
                questionNumber = challenge?.questionNumber
                completed = platform.isCompletedToday(username)
                // Once solved, even the optional streak fetch waits until tomorrow.
                streak = if (completed == true) existing?.streak
                    else runCatching { platform.getStreak(username) }.getOrNull()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Diagnostics.log(DiagnosticEvent.API_FAILED, "error_type" to e.javaClass.simpleName)
                completed = null
            }

            val fetchedAnything = completed != null || challengeTitle != null
            // Never save or deliver a response for an account/day that changed in flight.
            if (TimeUtils.utcToday() != today || settings.username(platform.id).first() != username) {
                Diagnostics.log(DiagnosticEvent.CHECK_SKIPPED, "reason" to "day_or_account_changed"); continue
            }
            if (completed == true) settings.markCompleted(platform.id, username, today)
            runCatching {
                // Never let a failed check wipe out today's good data: only
                // persist when we learned something or nothing was stored yet.
                if (fetchedAnything || existing == null) {
                    db.checkLogDao().insert(
                        CheckLog(
                            platformId = platform.id,
                            date = today,
                            completed = completed ?: existing?.completed ?: false,
                            known = completed != null || existing?.known == true,
                            checkedAtEpoch = System.currentTimeMillis(),
                            challengeTitle = challengeTitle ?: existing?.challengeTitle,
                            challengeUrl = challengeUrl ?: existing?.challengeUrl,
                            streak = streak ?: existing?.streak,
                            difficulty = difficulty ?: existing?.difficulty,
                            questionNumber = questionNumber ?: existing?.questionNumber,
                        )
                    )
                    settings.setCheckedUsername(platform.id, username)
                }
            }.onFailure { Diagnostics.log(DiagnosticEvent.DATABASE_FAILED, "error_type" to it.javaClass.simpleName) }
            Diagnostics.log(DiagnosticEvent.CHECK_FINISHED, "day" to today, "completed" to completed, "known" to (completed != null))

            results.add(
                PlatformStatus(
                    platformId = platform.id,
                    displayName = platform.displayName,
                    challengeTitle = challengeTitle,
                    challengeUrl = challengeUrl,
                    completed = completed,
                    streak = streak,
                    difficulty = difficulty,
                    questionNumber = questionNumber,
                )
            )
            if (completed == true) {
                alarm.stopFor(platform.id, today)
                notifications.cancelReminders(platform.id)
                onCompleted()
            }
        }
        if (results.isNotEmpty() && results.all { it.completed != null }) {
            reminderState.recovered()
            notifications.cancelFailure()
        }
        results
    }

    /** Delivery shares the check lock: a concurrent solve cannot race a stale reminder. */
    suspend fun <T> ifUnsolved(day: String, action: suspend () -> T): T? = checkMutex.withLock {
        if (day != TimeUtils.utcToday() || settings.isCompleted(day = day)) null else action()
    }

    /** Serialize deletion with checks so an in-flight fetch cannot restore deleted data. */
    suspend fun clearUserData() = checkMutex.withLock {
        settings.clearUserData()
        db.checkLogDao().deleteAll()
        notifications.cancelAll()
        reminderState.clear()
        alarm.stop()
    }

    companion object {
        private const val TAG = "DailyCheck"
    }
}
