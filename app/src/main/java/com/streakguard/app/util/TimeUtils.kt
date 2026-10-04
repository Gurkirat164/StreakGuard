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

    /** Fraction of the current UTC day that has elapsed, 0..1. */
    fun utcDayProgress(): Float {
        val start = startOfUtcDayMillis()
        val now = System.currentTimeMillis()
        return ((now - start).coerceAtLeast(0) / 86_400_000f).coerceIn(0f, 1f)
    }

    private val localTimeWithZoneFormat = DateTimeFormatter.ofPattern("HH:mm z")

    /**
     * Format an epoch-millis timestamp in the device's local timezone,
     * following the device's 12/24-hour clock setting. Falls back to
     * 12-hour format when the setting can't be read.
     */
    fun formatLocalTime(context: android.content.Context, epochMillis: Long): String {
        val use24Hour = runCatching {
            android.text.format.DateFormat.is24HourFormat(context)
        }.getOrDefault(false)
        val pattern = if (use24Hour) "HH:mm" else "hh:mm a"
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern))
    }

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
