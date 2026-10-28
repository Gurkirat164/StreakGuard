package com.streakguard.app.util

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFormatTest {
    @Test fun midnightAndNoonAreUnambiguousInTwelveHourFormat() {
        assertEquals("12:00 AM", ClockFormat.format(LocalTime.MIDNIGHT, false))
        assertEquals("12:00 PM", ClockFormat.format(LocalTime.NOON, false))
        assertEquals("06:25 PM", ClockFormat.format(LocalTime.of(18, 25), false))
    }
    @Test fun twentyFourHourFormatPreservesMinutePrecision() {
        assertEquals("00:00", ClockFormat.format(LocalTime.MIDNIGHT, true))
        assertEquals("18:25", ClockFormat.format(LocalTime.of(18, 25), true))
        assertEquals("23:59", ClockFormat.format(LocalTime.of(23, 59), true))
    }
}
