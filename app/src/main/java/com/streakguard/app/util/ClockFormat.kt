package com.streakguard.app.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display preference only: never changes a scheduled instant or the UTC platform day. */
object ClockFormat {
    fun format(time: LocalTime, use24Hour: Boolean): String = time.format(
        DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "hh:mm a", Locale.US)
    )
}
