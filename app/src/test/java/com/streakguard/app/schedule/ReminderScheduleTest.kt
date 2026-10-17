package com.streakguard.app.schedule

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReminderScheduleTest {
    @Test fun beforeTierSchedulesToday() {
        assertEquals(Instant.parse("2026-10-07T18:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-07T12:00:00Z"), 360))
    }
    @Test fun elapsedTierSchedulesTomorrow() {
        assertEquals(Instant.parse("2026-10-08T18:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-07T19:00:00Z"), 360))
    }
    @Test fun exactTriggerDoesNotRefire() {
        assertEquals(Instant.parse("2026-10-08T23:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-07T23:00:00Z"), 60))
    }
    @Test fun utcRolloverAndYearBoundary() {
        assertEquals(Instant.parse("2027-01-01T23:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-12-31T23:01:00Z"), 60))
        assertEquals(Instant.parse("2027-01-01T23:00:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2027-01-01T00:00:00Z"), 60))
    }
    @Test fun customTimesRetainMinutesAndRearmAcrossMidnight() {
        val offsets = ReminderSchedule.offsetsForUtcTimes(listOf(18 * 60 + 25, 21 * 60 + 10, 23 * 60 + 15))!!
        assertEquals(listOf(335, 170, 45), offsets)
        val now = Instant.parse("2026-10-17T18:24:59Z")
        assertEquals(Instant.parse("2026-10-17T18:25:00Z"), ReminderSchedule.nextTrigger(now, offsets[0]))
        assertEquals(Instant.parse("2026-10-18T18:25:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-17T18:25:00Z"), offsets[0]))
    }
    @Test fun rejectsInvalidTimesAndEmptyRetryWindows() {
        listOf(listOf(1080, 1080, 1380), listOf(1380, 1260, 1080), listOf(0, 1260, 1380),
            listOf(1080, 1260, 1440), listOf(1080, 1110, 1380), listOf(1080, 1380))
            .forEach { assertNull(ReminderSchedule.offsetsForUtcTimes(it)) }
        assertNotNull(ReminderSchedule.offsetsForUtcTimes(listOf(1, 32, 1439)))
        assertFalse(ReminderSchedule.valid(listOf(1500, 180, 60)))
    }
    @Test fun legacyHourPreferencesKeepTheSameDailyTimes() {
        assertEquals(listOf(360, 180, 60), ReminderSchedule.fromStoredValues(null, "6,3,1"))
        assertEquals(listOf(1380, 480, 120), ReminderSchedule.fromStoredValues(null, "23,8,2"))
        assertEquals(listOf(335, 170, 45), ReminderSchedule.fromStoredValues("335,170,45", "6,3,1"))
    }
    @Test fun corruptStoredTimesFallBackWithoutDroppingFields() {
        assertEquals(ReminderSchedule.defaultMinutes, ReminderSchedule.fromStoredValues("bad", "bad"))
        assertEquals(listOf(360, 180, 60), ReminderSchedule.fromStoredValues("335,bad,170,45", "6,3,1"))
        assertEquals(ReminderSchedule.defaultMinutes, ReminderSchedule.fromStoredValues(null, "24,3,1"))
    }
    @Test fun customTimesStillRespectStageCutoffs() {
        val offsets = listOf(335, 170, 45)
        assertEquals(Instant.parse("2026-10-17T20:40:00Z"), ReminderPolicy.cutoff("2026-10-17", offsets, 0))
        assertTrue(ReminderPolicy.active(Instant.parse("2026-10-17T18:25:00Z"), "2026-10-17", offsets, 0))
        assertFalse(ReminderPolicy.active(Instant.parse("2026-10-17T20:40:00Z"), "2026-10-17", offsets, 0))
    }
}
