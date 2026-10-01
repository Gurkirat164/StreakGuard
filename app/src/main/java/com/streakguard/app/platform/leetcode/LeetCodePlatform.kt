package com.streakguard.app.platform.leetcode

import com.streakguard.app.platform.DailyChallenge
import com.streakguard.app.platform.StreakPlatform
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * LeetCode implementation of [StreakPlatform].
 *
 * Completion rule: the POTD counts as done when the user has an accepted
 * submission for its titleSlug with a timestamp at or after the start of the
 * current UTC day (LeetCode's day flips at UTC midnight).
 */
class LeetCodePlatform(private val api: LeetCodeApi) : StreakPlatform {

    override val id: String = "leetcode"
    override val displayName: String = "LeetCode"

    override suspend fun getDailyChallenge(): DailyChallenge? {
        val question = api.getDailyQuestion() ?: return null
        return DailyChallenge(
            title = question.title,
            slug = question.titleSlug,
            url = "https://leetcode.com" + question.link,
        )
    }

    override suspend fun isCompletedToday(username: String): Boolean? {
        val question = api.getDailyQuestion() ?: return null
        val submissions = api.getRecentSubmissions(username) ?: return null
        val startOfUtcDay: Long = LocalDate.now(ZoneOffset.UTC)
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()
        return submissions.any { it.titleSlug == question.titleSlug && it.timestamp >= startOfUtcDay }
    }

    override suspend fun getStreak(username: String): Int? = api.getStreak(username)
}
