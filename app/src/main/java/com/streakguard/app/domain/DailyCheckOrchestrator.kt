package com.streakguard.app.domain

import android.util.Log
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
        val results = mutableListOf<PlatformStatus>()

        for (platform in registry.platforms) {
            val enabled = runCatching { settings.enabled(platform.id).first() }.getOrDefault(true)
            if (!enabled) continue

            val username = runCatching { settings.username(platform.id).first() }.getOrDefault("")
            if (username.isBlank()) continue

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
                streak = runCatching { platform.getStreak(username) }.getOrNull()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Check failed for ${platform.id} (manual=$manual)", e)
                completed = null
            }

            val fetchedAnything = completed != null || challengeTitle != null
            runCatching {
                // Never let a failed check wipe out today's good data: only
                // persist when we learned something or nothing was stored yet.
                val existing = db.checkLogDao().getForDate(platform.id, today)
                    .takeIf { settings.checkedUsername(platform.id).first() == username }
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
            }.onFailure { Log.w(TAG, "Failed to log check for ${platform.id}", it) }

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
            if (completed == true) alarm.stopFor(platform.id, today)
        }
        if (results.isNotEmpty() && results.all { it.completed != null }) {
            reminderState.recovered()
            notifications.cancelFailure()
        }
        results
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
