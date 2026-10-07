package com.streakguard.app.schedule

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReminderScheduleTest {
    @Test fun beforeTierSchedulesToday() {
        assertEquals(Instant.parse("2026-10-07T18:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-07T12:00:00Z"), 6))
    }
    @Test fun elapsedTierSchedulesTomorrow() {
        assertEquals(Instant.parse("2026-10-08T18:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-07T19:00:00Z"), 6))
    }
    @Test fun exactTriggerDoesNotRefire() {
        assertEquals(Instant.parse("2026-10-08T23:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-07T23:00:00Z"), 1))
    }
    @Test fun utcRolloverAndYearBoundary() {
        assertEquals(Instant.parse("2027-01-01T23:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-12-31T23:01:00Z"), 1))
        assertEquals(Instant.parse("2027-01-01T23:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2027-01-01T00:00:00Z"), 1))
    }
    @Test fun rejectsDuplicateUnorderedOrOutOfRangeTiers() {
        assertTrue(ReminderSchedule.valid(listOf(6, 3, 1)))
        assertTrue(ReminderSchedule.valid(listOf(23, 8, 2)))
        listOf(listOf(6, 6, 1), listOf(1, 3, 6), listOf(24, 3, 1), listOf(6, 3, 0), listOf(6, 1))
            .forEach { assertFalse(ReminderSchedule.valid(it)) }
    }
}
