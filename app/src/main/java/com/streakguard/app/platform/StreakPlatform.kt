package com.streakguard.app.platform

/**
 * Today's challenge on a platform.
 */
data class DailyChallenge(
    val title: String,
    val slug: String,
    val url: String,
)

/**
 * Plugin interface for a coding platform (LeetCode, Codeforces, ...).
 *
 * Implement this to add a platform. The rest of the app (scheduling,
 * notifications, settings UI, history) works unchanged.
 */
interface StreakPlatform {

    /** Stable id, e.g. "leetcode". Used for DataStore keys and DB rows. */
    val id: String

    /** Human-readable name shown in the UI, e.g. "LeetCode". */
    val displayName: String

    /** Today's challenge, or null when it couldn't be fetched. */
    suspend fun getDailyChallenge(): DailyChallenge?

    /**
     * Whether [username] completed today's challenge.
     * Returns null when the status is unknown (network error, bad username, ...).
     */
    suspend fun isCompletedToday(username: String): Boolean?

    /** Current streak for [username], or null when unknown. */
    suspend fun getStreak(username: String): Int?
}
