package com.streakguard.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Time rules for the whole app:
 *
 * - Backend (platform APIs, day boundaries, stored dates) always works in UTC,
 *   because platforms like LeetCode flip their day at UTC midnight.
 * - Everything shown to the user is formatted in the device's local timezone.
 *
 * Each platform converts to UTC inside its own implementation; the UI never
 * deals with UTC directly.
 */
object TimeUtils {

    /** The current platform day (UTC date, yyyy-MM-dd). Used as the cache key. */
    fun utcToday(): String = LocalDate.now(ZoneOffset.UTC).toString()

    /** Start of the current UTC day, epoch millis. For backend comparisons. */
    fun startOfUtcDayMillis(): Long =
        LocalDate.now(ZoneOffset.UTC)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    /** Next UTC midnight, epoch millis. The "day closes in" countdown target. */
    fun nextUtcMidnightMillis(): Long =
        LocalDate.now(ZoneOffset.UTC)
            .plusDays(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    private val localTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val localTimeWithZoneFormat = DateTimeFormatter.ofPattern("HH:mm z")

    /** Format an epoch-millis timestamp in the device's local timezone. */
    fun formatLocalTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(localTimeFormat)

    /**
     * The local clock time of the coming UTC-midnight reset,
     * e.g. "05:30 IST".
     */
    fun utcMidnightInLocalTime(): String =
        LocalDate.now(ZoneOffset.UTC)
            .plusDays(1)
            .atStartOfDay(ZoneOffset.UTC)
            .withZoneSameInstant(ZoneId.systemDefault())
            .format(localTimeWithZoneFormat)
}
