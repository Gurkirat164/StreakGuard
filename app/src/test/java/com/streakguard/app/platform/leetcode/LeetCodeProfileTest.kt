package com.streakguard.app.platform.leetcode

import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LeetCodeProfileTest {
    private val api = LeetCodeApi(OkHttpClient())

    @Test fun liveMissingProfileResponseIsInvalidRatherThanUnavailable() {
        val response = JSONObject("""{"errors":[{"message":"That user does not exist.","path":["matchedUser"],"extensions":{"handled":true,"skip_sentry":true}}],"data":{"matchedUser":null}}""")
        assertEquals(false, api.parseProfileExists(response))
    }

    @Test fun serverAndRateLimitErrorsAreNotInvalidUsernames() {
        for (message in listOf("Internal server error", "Too many requests")) {
            val response = JSONObject().put("errors", org.json.JSONArray()
                .put(JSONObject().put("message", message).put("path", org.json.JSONArray().put("matchedUser"))))
                .put("data", JSONObject().put("matchedUser", JSONObject.NULL))
            assertNull(api.parseProfileExists(response))
        }
    }

    @Test fun validProfileAndExplicitMissingProfileAreDistinct() {
        assertEquals(true, api.parseProfileExists(JSONObject("""{"data":{"matchedUser":{"username":"LeetCode"}}}""")))
        assertEquals(false, api.parseProfileExists(JSONObject("""{"data":{"matchedUser":null}}""")))
    }

    @Test fun incompleteAndMalformedResponsesRemainUnverified() {
        for (json in listOf("{}", """{"data":{}}""", """{"data":{"matchedUser":{}}}""", """{"data":{"matchedUser":"unexpected"}}""")) {
            assertNull(api.parseProfileExists(JSONObject(json)))
        }
    }
}
