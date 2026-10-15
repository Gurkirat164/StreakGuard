package com.streakguard.app.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Platform deadlines are UTC instants; changing the display zone must not move them. */
object ReminderPolicy {
    const val RETRY_SECONDS = 600L
    const val MAX_FINAL_RETRIES = 3
    const val MAX_RINGS = 3
    const val RING_MILLIS = 60_000L

    fun reset(day: String): Instant = LocalDate.parse(day).plusDays(1)
        .atStartOfDay(ZoneOffset.UTC).toInstant()

    fun trigger(day: String, hours: List<Int>, tier: Int): Instant =
        reset(day).minusSeconds(hours[tier] * 3600L)

    fun cutoff(day: String, hours: List<Int>, tier: Int): Instant =
        if (tier < 2) trigger(day, hours, tier + 1).minusSeconds(1800) else reset(day)

    fun active(now: Instant, day: String, hours: List<Int>, tier: Int): Boolean =
        tier in 0..2 && !now.isBefore(trigger(day, hours, tier)) && now.isBefore(cutoff(day, hours, tier))

    fun nextRetry(now: Instant, day: String, hours: List<Int>, tier: Int, attempt: Int): Instant? {
        if (tier == 2 && attempt >= MAX_FINAL_RETRIES) return null
        return now.plusSeconds(RETRY_SECONDS).takeIf { active(it, day, hours, tier) }
    }

    fun shouldRingAfterFailure(tier: Int, attempt: Int, lastCompleted: Boolean?): Boolean =
        tier == 2 && attempt == MAX_FINAL_RETRIES && lastCompleted != true

    fun nextRing(now: Instant, day: String, rings: Int): Instant? =
        now.plusSeconds(RETRY_SECONDS).takeIf { rings < MAX_RINGS && it.isBefore(reset(day)) }
}
