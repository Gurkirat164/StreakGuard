package com.streakguard.app.platform.leetcode

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Submission(
    val titleSlug: String,
    /** Epoch seconds, as returned by LeetCode. */
    val timestamp: Long,
)

data class DailyQuestion(
    val title: String,
    val titleSlug: String,
    /** Relative link, e.g. "/problems/two-sum/". */
    val link: String,
    /** "Easy", "Medium" or "Hard"; empty when unknown. */
    val difficulty: String = "",
    /** Frontend question id, e.g. "3152"; empty when unknown. */
    val questionFrontendId: String = "",
)

private const val GRAPHQL_URL = "https://leetcode.com/graphql"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Thin client over LeetCode's public (unofficial) GraphQL endpoint.
 *
 * No auth needed. Every method is fully defensive: any null, missing field
 * or error results in null / empty, never a crash.
 */
class LeetCodeApi(private val client: OkHttpClient) {

    private suspend fun postGraphql(query: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("query", query).toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(GRAPHQL_URL)
                .post(body)
                .header("User-Agent", "StreakGuard/1.0")
                .header("Referer", "https://leetcode.com/")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val text = response.body?.string() ?: return@runCatching null
                JSONObject(text)
            }
        }.getOrNull()
    }

    /** Escape a username so it can be safely inlined into a GraphQL string literal. */
    private fun safeUsername(username: String): String =
        username.replace("\\", "\\\\").replace("\"", "\\\"")

    suspend fun getDailyQuestion(): DailyQuestion? {
        val root = postGraphql(
            "{ activeDailyCodingChallengeQuestion { date link question { title titleSlug difficulty questionFrontendId } } }"
        ) ?: return null
        return runCatching {
            val node = root.getJSONObject("data")
                .getJSONObject("activeDailyCodingChallengeQuestion")
            val link = node.optString("link", "")
            val question = node.getJSONObject("question")
            DailyQuestion(
                title = question.optString("title", ""),
                titleSlug = question.optString("titleSlug", ""),
                link = link,
                difficulty = question.optString("difficulty", ""),
                questionFrontendId = question.optString("questionFrontendId", ""),
            ).takeIf { it.titleSlug.isNotBlank() }
        }.getOrNull()
    }

    suspend fun getRecentSubmissions(username: String, limit: Int = 20): List<Submission>? {
        val query = """{ recentAcSubmissionList(username: "${safeUsername(username)}", limit: $limit) { titleSlug timestamp } }"""
        val root = postGraphql(query) ?: return null
        return runCatching {
            val arr = root.getJSONObject("data").optJSONArray("recentAcSubmissionList")
                ?: return@runCatching emptyList<Submission>()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val slug = obj.optString("titleSlug", "")
                    val ts = obj.optString("timestamp", "").toLongOrNull() ?: continue
                    if (slug.isNotBlank()) add(Submission(slug, ts))
                }
            }
        }.getOrNull()
    }

    suspend fun getStreak(username: String): Int? {
        val query = """{ matchedUser(username: "${safeUsername(username)}") { userCalendar { streak } } }"""
        val root = postGraphql(query) ?: return null
        return runCatching {
            root.getJSONObject("data")
                .optJSONObject("matchedUser")
                ?.optJSONObject("userCalendar")
                ?.optInt("streak", -1)
                ?.takeIf { it >= 0 }
        }.getOrNull()
    }
}
