package com.streakguard.app.domain

import android.util.Log
import com.streakguard.app.data.local.AppDatabase
import com.streakguard.app.data.local.CheckLog
import com.streakguard.app.data.prefs.SettingsStore
import com.streakguard.app.notify.NotificationHelper
import com.streakguard.app.platform.PlatformRegistry
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.flow.first

/**
 * Runs the daily check for every enabled platform that has a username set:
 * fetch today's challenge, determine completion, log it, and notify when needed.
 *
 * A platform whose status can't be determined (network error, bad username)
 * is logged as "unknown" and never triggers a notification.
 */
class DailyCheckOrchestrator(
    private val registry: PlatformRegistry,
    private val settings: SettingsStore,
    private val db: AppDatabase,
    private val notifications: NotificationHelper,
) {

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

    /**
     * @param notify when false the check is silent (background refresh):
     *   cache is updated but no notification is posted.
     */
    suspend fun runCheck(manual: Boolean, notify: Boolean = true): List<PlatformStatus> {
        val confirmWhenDone = runCatching { settings.confirmWhenDone.first() }.getOrDefault(false)
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
                Log.w(TAG, "Check failed for ${platform.id} (manual=$manual)", e)
                completed = null
            }

            val fetchedAnything = completed != null || challengeTitle != null
            runCatching {
                // Never let a failed check wipe out today's good data: only
                // persist when we learned something or nothing was stored yet.
                val existing = db.checkLogDao().getForDate(platform.id, today)
                if (fetchedAnything || existing == null) {
                    db.checkLogDao().insert(
                        CheckLog(
                            platformId = platform.id,
                            date = today,
                            completed = completed == true,
                            known = completed != null,
                            checkedAtEpoch = System.currentTimeMillis(),
                            challengeTitle = challengeTitle,
                            challengeUrl = challengeUrl,
                            streak = streak,
                            difficulty = difficulty,
                            questionNumber = questionNumber,
                        )
                    )
                }
            }.onFailure { Log.w(TAG, "Failed to log check for ${platform.id}", it) }

            if (notify && completed == false) {
                notifications.notifyMissed(
                    platformName = platform.displayName,
                    platformId = platform.id,
                    challengeTitle = challengeTitle ?: "Today's challenge",
                    challengeUrl = challengeUrl ?: "https://leetcode.com",
                )
            } else if (completed == true && confirmWhenDone && notify) {
                notifications.notifyDone(platform.displayName, platform.id)
            }

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
        }
        return results
    }

    companion object {
        private const val TAG = "DailyCheck"
    }
}
