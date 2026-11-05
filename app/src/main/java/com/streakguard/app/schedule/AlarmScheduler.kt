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
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import com.streakguard.app.diagnostics.Diagnostics
import com.streakguard.app.diagnostics.DiagnosticEvent

/**
 * Schedules the once-daily check with an exact alarm (fires even in Doze).
 *
 * The alarm is one-shot: [DailyCheckReceiver] re-arms it for the next day
 * after each run, and [com.streakguard.app.receiver.BootReceiver] re-arms it
 * after a reboot.
 */
@android.annotation.SuppressLint("ApplySharedPref") // Retry state must reach disk before its alarm is armed.
class AlarmScheduler(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val retryPrefs = context.getSharedPreferences("reminder_retries", Context.MODE_PRIVATE)

    suspend fun signature(): String = settings.username("leetcode").first() + ":" +
        settings.enabled("leetcode").first() + ":" + "minutes:" + settings.reminderMinutes.first().joinToString(",")

    suspend fun deliveryKey(day: String, tier: Int): String =
        "$day:$tier:${settings.username("leetcode").first()}"

    fun isExactAlarmAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** Re-arm daily triggers and restore valid in-flight retries after reboot. */
    suspend fun rescheduleFromSettings(reason: String = "settings_refresh") {
        try { reschedule(reason) } catch (error: Exception) {
            Diagnostics.failure("reschedule_$reason", error)
            throw error
        }
    }

    private suspend fun reschedule(reason: String) {
        cancelDaily(reason)
        if (!settings.remindersEnabled.first() || !settings.enabled("leetcode").first() ||
            settings.username("leetcode").first().isBlank()) {
            (0..2).forEach(::cancelReminderRetry)
            return
        }
        val signature = signature()
        val now = Instant.now()
        val completed = settings.isCompleted()
        settings.reminderMinutes.first().forEachIndexed { tier, minutes ->
            val trigger = ReminderSchedule.nextTrigger(now, minutes, completedToday = completed)
            val traceId = java.util.UUID.randomUUID().toString()
            val intent = Intent(context, DailyCheckReceiver::class.java).apply {
                putExtra("trace_id", traceId)
                putExtra("reminder_tier", tier)
                putExtra("platform_day", trigger.atZone(ZoneOffset.UTC).toLocalDate().toString())
                putExtra("signature", signature)
            }
            val pending = PendingIntent.getBroadcast(context, REQUEST_TIER_BASE + tier, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            scheduleAt(trigger.toEpochMilli(), pending, traceId, REQUEST_TIER_BASE + tier,
                trigger.atZone(ZoneOffset.UTC).toLocalDate().toString(), tier, 0)
            Diagnostics.log(DiagnosticEvent.REMINDER_SCHEDULED, "tier" to tier, "at" to trigger.toString(), "completed" to completed)
            val day = retryPrefs.getString("day_$tier", null)
            if (day != null) {
                val valid = !completed && retryPrefs.getString("signature_$tier", null) == signature &&
                    day == TimeUtils.utcToday() &&
                    now.isBefore(ReminderPolicy.cutoff(day, settings.reminderMinutes.first(), tier))
                if (valid) {
                    scheduleReminderRetry(day, tier, retryPrefs.getInt("attempt_$tier", 1),
                        maxOf(now.toEpochMilli() + 1000, retryPrefs.getLong("at_$tier", 0)), signature)
                } else cancelReminderRetry(tier)
            }
        }
    }

    private fun scheduleAt(at: Long, pending: PendingIntent, traceId: String, slot: Int,
                           day: String, tier: Int, attempt: Int) {
        Diagnostics.checkpoint(traceId, "schedule_requested", "slot" to slot, "day" to day,
            "tier" to tier, "attempt" to attempt, "at_ms" to at)
        try {
            val manager = checkNotNull(context.getSystemService(AlarmManager::class.java))
            var exact = true
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
            catch (error: SecurityException) {
                Diagnostics.critical(DiagnosticEvent.REMINDER_TRACE, "trace_id" to traceId,
                    "stage" to "exact_fallback", "error_type" to error.javaClass.simpleName)
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                exact = false
            }
            Diagnostics.checkpoint(traceId, "scheduled", "exact" to exact)
            Diagnostics.log(DiagnosticEvent.REMINDER_SCHEDULED, "trace_id" to traceId,
                "at" to Instant.ofEpochMilli(at).toString(), "exact" to exact)
        } catch (error: Exception) {
            Diagnostics.checkpoint(traceId, "schedule_failed", "error_type" to error.javaClass.simpleName)
            throw error
        }
    }

    fun scheduleReminderRetry(day: String, tier: Int, attempt: Int, at: Long, signature: String) {
        Diagnostics.log(DiagnosticEvent.RETRY_SCHEDULED, "day" to day, "tier" to tier, "attempt" to attempt, "at" to Instant.ofEpochMilli(at).toString())
        retryPrefs.edit().putString("day_$tier", day).putInt("attempt_$tier", attempt)
            .putLong("at_$tier", at).putString("signature_$tier", signature).commit()
        val intent = Intent(context, DailyCheckReceiver::class.java).apply {
            putExtra("platform_day", day); putExtra("reminder_tier", tier)
            putExtra("attempt", attempt); putExtra("signature", signature)
        }
        val traceId = java.util.UUID.randomUUID().toString()
        intent.putExtra("trace_id", traceId)
        Diagnostics.cancelSlot(REQUEST_REMINDER_RETRY + tier, "retry_replaced")
        scheduleAt(at, PendingIntent.getBroadcast(context, REQUEST_REMINDER_RETRY + tier, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE), traceId,
            REQUEST_REMINDER_RETRY + tier, day, tier, attempt)
    }

    fun cancelReminderRetry(tier: Int) {
        Diagnostics.log(DiagnosticEvent.RETRY_CANCELLED, "tier" to tier)
        retryPrefs.edit().remove("day_$tier").remove("attempt_$tier").remove("at_$tier")
            .remove("signature_$tier").commit()
        val pending = PendingIntent.getBroadcast(context, REQUEST_REMINDER_RETRY + tier,
            Intent(context, DailyCheckReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending == null) {
            Diagnostics.cancelSlot(REQUEST_REMINDER_RETRY + tier, "retry_pending_intent_absent")
            return
        }
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
        Diagnostics.cancelSlot(REQUEST_REMINDER_RETRY + tier, "retry_cancelled")
    }

    fun cancel() {
        cancelDaily("reminders_cancelled")
        (0..2).forEach(::cancelReminderRetry)
    }

    private fun cancelDaily(reason: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        for (code in listOf(REQUEST_CODE) + (REQUEST_TIER_BASE..REQUEST_TIER_BASE + 2)) {
        Diagnostics.critical(DiagnosticEvent.REMINDER_TRACE, "stage" to "cancel_requested", "slot" to code, "reason" to reason)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, DailyCheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Diagnostics.cancelSlot(code, reason)
        }
    }

    /**
     * Schedules a silent background refresh at the next UTC midnight — the
     * moment the platform day rolls over. [MidnightRefreshReceiver] updates
     * the cache without posting notifications and re-arms itself.
     */
    fun scheduleMidnightRefresh() {
        try { scheduleMidnight() } catch (error: Exception) {
            Diagnostics.failure("schedule_midnight", error)
            throw error
        }
    }

    private fun scheduleMidnight() {
        Diagnostics.log(DiagnosticEvent.REMINDER_SCHEDULED, "kind" to "midnight", "at" to Instant.ofEpochMilli(TimeUtils.nextUtcMidnightMillis()).toString())
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
        try { scheduleRefreshRetryUnchecked(delayMinutes, attempt) } catch (error: Exception) {
            Diagnostics.failure("schedule_midnight_retry", error)
            throw error
        }
    }

    private fun scheduleRefreshRetryUnchecked(delayMinutes: Long, attempt: Int) {
        Diagnostics.log(DiagnosticEvent.RETRY_SCHEDULED, "kind" to "midnight", "minutes" to delayMinutes, "attempt" to attempt)
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
        private const val REQUEST_REMINDER_RETRY = 1020
    }
}
