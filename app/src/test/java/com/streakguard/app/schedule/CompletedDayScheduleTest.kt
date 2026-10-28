package com.streakguard.app.schedule

import com.streakguard.app.domain.CompletedDay
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CompletedDayScheduleTest {
    @Test fun solvedBeforeAllStagesMovesEveryReminderToTomorrow() {
        val now = Instant.parse("2026-10-17T01:00:00Z")
        val expected = listOf("18:00:00", "21:00:00", "23:00:00")
        ReminderSchedule.defaultMinutes.forEachIndexed { tier, offset ->
            assertEquals(Instant.parse("2026-10-18T${expected[tier]}Z"),
                ReminderSchedule.nextTrigger(now, offset, completedToday = true))
        }
    }

    @Test fun solvingBetweenStagesDoesNotSkipTwoDays() {
        val now = Instant.parse("2026-10-17T20:00:00Z")
        assertEquals(Instant.parse("2026-10-18T18:00:00Z"), ReminderSchedule.nextTrigger(now, 360, true))
        assertEquals(Instant.parse("2026-10-18T21:00:00Z"), ReminderSchedule.nextTrigger(now, 180, true))
    }

    @Test fun midnightExpiresCompletionAndAllowsTodaysCustomTimes() {
        assertFalse(CompletedDay.matches("2026-10-17", "2026-10-18", "alice"))
        assertEquals(Instant.parse("2026-10-18T18:25:00Z"),
            ReminderSchedule.nextTrigger(Instant.parse("2026-10-18T00:00:00Z"), 335))
    }
}
