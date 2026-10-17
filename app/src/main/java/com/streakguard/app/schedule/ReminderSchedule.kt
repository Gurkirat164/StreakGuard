package com.streakguard.app.schedule

import java.time.Instant
import java.time.ZoneOffset

/** Tier offsets always refer to the platform's UTC reset, never the display timezone. */
object ReminderSchedule {
    val defaultMinutes = listOf(360, 180, 60)

    // Earlier stages must have a delivery window before the next stage's 30-minute cutoff.
    fun valid(minutes: List<Int>): Boolean =
        minutes.size == 3 && minutes.all { it in 1..1439 } &&
            minutes.zipWithNext().all { (a, b) -> a - b > 30 }

    fun nextTrigger(now: Instant, minutesBeforeReset: Int, completedToday: Boolean = false): Instant {
        require(minutesBeforeReset in 1..1439)
        val reset = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant()
        val trigger = reset.minusSeconds(minutesBeforeReset * 60L)
        return if (!completedToday && trigger.isAfter(now)) trigger else trigger.plusSeconds(86400)
    }

    /** Preserve existing hour-based preferences until a custom minute schedule is saved. */
    fun fromStoredValues(minutes: String?, legacyHours: String?): List<Int> {
        fun parse(value: String?): List<Int>? = value?.split(",")?.map {
            it.toIntOrNull() ?: return null
        }
        return parse(minutes)?.takeIf(::valid)
            ?: parse(legacyHours)?.takeIf { hours ->
                hours.size == 3 && hours.all { it in 1..23 } &&
                    hours.zipWithNext().all { (a, b) -> a > b }
            }?.map { it * 60 } ?: defaultMinutes
    }

    /** UTC minutes after midnight, in stage order. Midnight itself is the reset, not a reminder. */
    fun offsetsForUtcTimes(times: List<Int>): List<Int>? =
        times.takeIf { it.all { time -> time in 1..1439 } }
            ?.map { 1440 - it }?.takeIf(::valid)
}
