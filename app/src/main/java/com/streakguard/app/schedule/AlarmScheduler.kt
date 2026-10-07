package com.streakguard.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.streakguard.app.data.prefs.SettingsStore
import com.streakguard.app.receiver.DailyCheckReceiver
import com.streakguard.app.receiver.MidnightRefreshReceiver
import com.streakguard.app.util.TimeUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first

/**
 * Schedules the once-daily check with an exact alarm (fires even in Doze).
 *
 * The alarm is one-shot: [DailyCheckReceiver] re-arms it for the next day
 * after each run, and [com.streakguard.app.receiver.BootReceiver] re-arms it
 * after a reboot.
 */
class AlarmScheduler(
    private val context: Context,
    private val settings: SettingsStore,
) {

    fun isExactAlarmAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** Re-read hour/minute from settings and schedule the next trigger. */
    suspend fun rescheduleFromSettings() {
        cancel()
        if (!settings.remindersEnabled.first()) return
        settings.reminderHours.first().forEachIndexed { tier, hours ->
            val trigger = ReminderSchedule.nextTrigger(Instant.now(), hours)
            val intent = Intent(context, DailyCheckReceiver::class.java).apply {
                putExtra("reminder_tier", tier)
                putExtra("platform_day", trigger.atZone(ZoneOffset.UTC).toLocalDate().toString())
            }
            val pending = PendingIntent.getBroadcast(context, REQUEST_TIER_BASE + tier, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toEpochMilli(), pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toEpochMilli(), pending)
            }
        }
    }

    fun scheduleDaily(hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var next = now.with(LocalDate.now(ZoneId.systemDefault()).atTime(LocalTime.of(hour, minute)))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAtMillis = next.toInstant().toEpochMilli()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyCheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked: fall back to an inexact window.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        for (code in listOf(REQUEST_CODE) + (REQUEST_TIER_BASE..REQUEST_TIER_BASE + 2)) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, DailyCheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        }
    }

    /**
     * Schedules a silent background refresh at the next UTC midnight — the
     * moment the platform day rolls over. [MidnightRefreshReceiver] updates
     * the cache without posting notifications and re-arms itself.
     */
    fun scheduleMidnightRefresh() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_MIDNIGHT,
            Intent(context, MidnightRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                TimeUtils.nextUtcMidnightMillis(),
                pendingIntent,
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                TimeUtils.nextUtcMidnightMillis(),
                pendingIntent,
            )
        }
    }

    /**
     * Schedules a retry of the background refresh after [delayMinutes].
     * Used when the midnight refresh couldn't reach the network.
     */
    fun scheduleRefreshRetry(delayMinutes: Long, attempt: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, MidnightRefreshReceiver::class.java).apply {
            putExtra(MidnightRefreshReceiver.EXTRA_RETRY_ATTEMPT, attempt)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_RETRY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMinutes * 60_000,
            pendingIntent,
        )
    }

    companion object {
        private const val REQUEST_CODE = 1001
        private const val REQUEST_TIER_BASE = 1010
        private const val REQUEST_MIDNIGHT = 1002
        private const val REQUEST_RETRY = 1003
    }
}
