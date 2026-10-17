package com.streakguard.app.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingLogStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun delayedWritesUseEventTimeRatherThanQueueProcessingTime() {
        val store = RollingLogStore(temporary.newFolder(), maxAgeMillis = 1000, now = { 2000 })
        store.append("EXPIRED_BEFORE_WRITE", timestamp = 500)
        store.append("CURRENT", timestamp = 1900)
        assertFalse(store.read().contains("EXPIRED_BEFORE_WRITE"))
        assertTrue(store.read().contains("1900\t"))
    }

    @Test fun expiresEntriesAtSevenDaysWithoutDeletingNewerEntriesInSameSegment() {
        val week = 7L * 24 * 60 * 60 * 1000
        var now = week
        val store = RollingLogStore(temporary.newFolder(), now = { now })
        store.append("OLD")
        now += 1
        store.append("NEW")
        now = week * 2
        assertFalse(store.read().contains("OLD"))
        assertTrue(store.read().contains("NEW"))
        now++
        assertEquals("", store.read())
    }

    @Test fun sizeLimitRemovesOldestEntriesFirstAndStaysWithinByteBudget() {
        var now = 1_000L
        val store = RollingLogStore(temporary.newFolder(), maxBytes = 160, now = { now }, segmentBytes = 80)
        repeat(20) { store.append("EVENT_$it"); now++ }
        assertTrue(store.size() <= 160)
        assertTrue(store.read().contains("EVENT_19"))
        assertFalse(store.read().contains("EVENT_0\t"))
    }

    @Test fun whicheverLimitIsReachedFirstWins() {
        var now = 1_000L
        val store = RollingLogStore(temporary.newFolder(), maxBytes = 150, maxAgeMillis = 100, now = { now })
        repeat(5) { store.append("BEFORE_$it"); now++ }
        assertTrue(store.size() <= 150)
        now += 100
        store.append("AFTER")
        assertFalse(store.read().contains("BEFORE"))
        assertTrue(store.read().contains("AFTER"))
    }

    @Test fun restartPrunesWithoutNeedingAnotherEventAndClearAllowsFreshLogging() {
        val dir = temporary.newFolder()
        RollingLogStore(dir, maxAgeMillis = 100, now = { 1000 }).append("STALE")
        val restarted = RollingLogStore(dir, maxAgeMillis = 100, now = { 1200 })
        assertEquals(0, restarted.size())
        restarted.append("FRESH")
        restarted.clear()
        assertEquals("", restarted.read())
        restarted.append("NEW")
        assertTrue(restarted.read().contains("NEW"))
    }

    @Test fun concurrentWritersDoNotLoseOrInterleaveRecords() {
        val store = RollingLogStore(temporary.newFolder())
        val threads = (1..4).map { writer -> Thread { repeat(20) { store.append("W${writer}_$it") } }.apply { start() } }
        threads.forEach { it.join() }
        assertEquals(80, store.read().lineSequence().count { it.isNotBlank() })
    }

    @Test fun clockRollbackEvictsByTimestampRatherThanWriteOrder() {
        var now = 10_000L
        val store = RollingLogStore(temporary.newFolder(), maxBytes = 95, now = { now })
        store.append("LATER")
        now = 9000
        store.append("EARLIER")
        now = 11_000
        store.append("LATEST")
        assertFalse(store.read().contains("EARLIER"))
        assertTrue(store.read().contains("LATEST"))
    }

    @Test fun timestampEvictionWorksAcrossMultipleSegmentsAfterClockChanges() {
        var now = 2000L
        val store = RollingLogStore(temporary.newFolder(), maxBytes = 85, segmentBytes = 40, now = { now })
        store.append("A")
        now = 1000
        store.append("B")
        now = 3000
        store.append("C")
        assertFalse(store.read().contains("\tB\t"))
        assertTrue(store.read().contains("\tC\t"))
        assertTrue(store.size() <= 85)
    }

    @Test fun unapprovedFieldsAndObjectsCannotLeakIntoTheLog() {
        val fields = DiagnosticFields.encode(mapOf("api_response" to "secret-body", "username" to "private-handle",
            "token" to "secret-token", "kind" to object { override fun toString() = "secret-object" },
            "notifications" to true, "reason" to "line1\nline2\tline3"))
        assertFalse(fields.contains("secret"))
        assertFalse(fields.contains("private-handle"))
        assertFalse(fields.contains('\n'))
        assertFalse(fields.contains('\t'))
        assertTrue(fields.contains("notifications=true"))
    }
}
