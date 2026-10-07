package com.streakguard.app.schedule

import java.time.Instant
import java.time.ZoneOffset

/** Tier offsets always refer to the platform's UTC reset, never the display timezone. */
object ReminderSchedule {
    val defaultHours = listOf(6, 3, 1)

    fun valid(hours: List<Int>): Boolean =
        hours.size == 3 && hours.all { it in 1..23 } && hours.zipWithNext().all { (a, b) -> a > b }

    fun nextTrigger(now: Instant, hoursBeforeReset: Int): Instant {
        require(hoursBeforeReset in 1..23)
        val reset = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant()
        val trigger = reset.minusSeconds(hoursBeforeReset * 3600L)
        return if (trigger.isAfter(now)) trigger else trigger.plusSeconds(86400)
    }
}
