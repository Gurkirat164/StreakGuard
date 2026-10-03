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
    )

    suspend fun runCheck(manual: Boolean): List<PlatformStatus> {
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
            try {
                val challenge = platform.getDailyChallenge()
                challengeTitle = challenge?.title
                challengeUrl = challenge?.url
                completed = platform.isCompletedToday(username)
                streak = runCatching { platform.getStreak(username) }.getOrNull()
            } catch (e: Exception) {
                Log.w(TAG, "Check failed for ${platform.id} (manual=$manual)", e)
                completed = null
            }

            runCatching {
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
                    )
                )
            }.onFailure { Log.w(TAG, "Failed to log check for ${platform.id}", it) }

            if (completed == false) {
                notifications.notifyMissed(
                    platformName = platform.displayName,
                    platformId = platform.id,
                    challengeTitle = challengeTitle ?: "Today's challenge",
                    challengeUrl = challengeUrl ?: "https://leetcode.com",
                )
            } else if (completed == true && confirmWhenDone) {
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
                )
            )
        }
        return results
    }

    companion object {
        private const val TAG = "DailyCheck"
    }
}
