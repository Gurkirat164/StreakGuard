package com.streakguard.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.streakguard.app.data.prefs.SettingsStore
import com.streakguard.app.receiver.DailyCheckReceiver
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

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
        scheduleDaily(settings.getCheckHour(), settings.getCheckMinute())
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
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyCheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}
