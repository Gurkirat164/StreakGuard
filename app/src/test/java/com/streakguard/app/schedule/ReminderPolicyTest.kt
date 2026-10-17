package com.streakguard.app.schedule

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private val day = "2026-10-15"
    private val minutes = listOf(360, 180, 60)
    private fun at(time: String) = Instant.parse("${day}T${time}Z")

    @Test fun earlierStagesStopThirtyMinutesBeforeNextStage() {
        assertEquals(at("20:30:00"), ReminderPolicy.cutoff(day, minutes, 0))
        assertEquals(at("22:30:00"), ReminderPolicy.cutoff(day, minutes, 1))
        assertNotNull(ReminderPolicy.nextRetry(at("20:19:59"), day, minutes, 0, 2))
        assertNull(ReminderPolicy.nextRetry(at("20:20:00"), day, minutes, 0, 2))
        assertFalse(ReminderPolicy.active(at("20:30:00"), day, minutes, 0))
        assertFalse(ReminderPolicy.active(at("21:00:00"), day, minutes, 0))
    }

    @Test fun finalStageGetsExactlyThreeRetries() {
        assertEquals(at("23:10:00"), ReminderPolicy.nextRetry(at("23:00:00"), day, minutes, 2, 0))
        assertEquals(at("23:20:00"), ReminderPolicy.nextRetry(at("23:10:00"), day, minutes, 2, 1))
        assertEquals(at("23:30:00"), ReminderPolicy.nextRetry(at("23:20:00"), day, minutes, 2, 2))
        assertNull(ReminderPolicy.nextRetry(at("23:30:00"), day, minutes, 2, 3))
    }

    @Test fun failureFallbackRingsForUnknownOrUnsolvedButNeverSolved() {
        assertTrue(ReminderPolicy.shouldRingAfterFailure(2, 3, null))
        assertTrue(ReminderPolicy.shouldRingAfterFailure(2, 3, false))
        assertFalse(ReminderPolicy.shouldRingAfterFailure(2, 3, true))
        assertFalse(ReminderPolicy.shouldRingAfterFailure(2, 2, null))
        assertFalse(ReminderPolicy.shouldRingAfterFailure(1, 3, false))
    }

    @Test fun delayedWorkDoesNotCrossUtcMidnightOrStartEarly() {
        assertFalse(ReminderPolicy.active(at("17:59:59"), day, minutes, 0))
        assertTrue(ReminderPolicy.active(at("18:00:00"), day, minutes, 0))
        assertFalse(ReminderPolicy.active(Instant.parse("2026-10-16T00:00:00Z"), day, minutes, 2))
        assertNull(ReminderPolicy.nextRetry(at("23:55:00"), day, minutes, 2, 1))
    }

    @Test fun customTimesDetermineCutoff() {
        assertEquals(at("15:30:00"), ReminderPolicy.cutoff(day, listOf(1380, 480, 120), 0))
    }

    @Test fun ringLimitIncludesInitialRingAndRespectsReset() {
        assertNotNull(ReminderPolicy.nextRing(at("23:01:00"), day, 1))
        assertNotNull(ReminderPolicy.nextRing(at("23:12:00"), day, 2))
        assertNull(ReminderPolicy.nextRing(at("23:23:00"), day, 3))
        assertNull(ReminderPolicy.nextRing(at("23:50:00"), day, 1))
        assertEquals(60_000L, ReminderPolicy.RING_MILLIS)
    }
}
