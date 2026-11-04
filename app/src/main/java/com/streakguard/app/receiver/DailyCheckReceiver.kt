package com.streakguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.streakguard.app.StreakGuardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.streakguard.app.util.TimeUtils
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.streakguard.app.schedule.ReminderCheckWorker
import com.streakguard.app.diagnostics.Diagnostics

/**
 * Fired by the daily exact alarm. Runs the check, then re-arms the alarm for
 * the next day so the cadence is self-perpetuating.
 */
class DailyCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val traceId = intent.getStringExtra("trace_id")
        // Persist at entry, before coroutine dispatch or WorkManager can fail.
        Diagnostics.checkpoint(traceId, "receiver_entered")
        Diagnostics.critical(com.streakguard.app.diagnostics.DiagnosticEvent.REMINDER_RECEIVED,
            "trace_id" to traceId, "tier" to intent.getIntExtra("reminder_tier", -1),
            "day" to intent.getStringExtra("platform_day"), "attempt" to intent.getIntExtra("attempt", 0))
        val pending = goAsync()
        val app = context.applicationContext as StreakGuardApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    app.container.diagnostics.critical(com.streakguard.app.diagnostics.DiagnosticEvent.RUNTIME_SNAPSHOT,
                        app.container.diagnostics.permissionFields() + mapOf("reason" to "reminder_received", "trace_id" to traceId))
                }.onFailure { Diagnostics.failure("receiver_runtime_snapshot", it) }
                val tier = intent.getIntExtra("reminder_tier", -1)
                val day = intent.getStringExtra("platform_day")
                val inactiveReason = when {
                    tier !in 0..2 -> "invalid_tier"
                    day != TimeUtils.utcToday() -> "wrong_platform_day"
                    !app.container.settingsStore.remindersEnabled.first() -> "reminders_disabled"
                    app.container.settingsStore.isCompleted(day = day) -> "already_solved"
                    else -> null
                }
                if (inactiveReason == null) {
                    // Queue the network work outside the receiver's short execution window.
                    val work = OneTimeWorkRequestBuilder<ReminderCheckWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .addTag("countdown_reminder")
                        .setInputData(workDataOf("tier" to tier, "day" to day, "trace_id" to traceId,
                            "attempt" to intent.getIntExtra("attempt", 0),
                            "signature" to intent.getStringExtra("signature")))
                        .build()
                    val attempt = intent.getIntExtra("attempt", 0)
                    val signature = intent.getStringExtra("signature").orEmpty().hashCode()
                    Diagnostics.checkpoint(traceId, "enqueue_requested", "work_id" to work.id.toString())
                    try {
                        WorkManager.getInstance(context).enqueueUniqueWork("reminder_${day}_${tier}_${attempt}_$signature", ExistingWorkPolicy.KEEP, work)
                            .result.get(5, java.util.concurrent.TimeUnit.SECONDS)
                        Diagnostics.checkpoint(traceId, "enqueue_completed", "work_id" to work.id.toString())
                    } catch (error: java.util.concurrent.TimeoutException) {
                        // A timeout is not proof of rejection: work may still be committed.
                        Diagnostics.checkpoint(traceId, "enqueue_unconfirmed", "error_type" to error.javaClass.simpleName)
                    } catch (error: Exception) {
                        Diagnostics.checkpoint(traceId, "enqueue_failed", "error_type" to error.javaClass.simpleName)
                        throw error
                    }
                } else {
                    Diagnostics.checkpoint(traceId, "skipped", "reason" to inactiveReason)
                    Diagnostics.log(com.streakguard.app.diagnostics.DiagnosticEvent.CHECK_SKIPPED,
                        "trace_id" to traceId, "reason" to inactiveReason, "tier" to tier)
                }
            } catch (error: Exception) {
                Diagnostics.failure("daily_receiver", error)
                throw error
            } finally {
                runCatching { app.container.alarmScheduler.rescheduleFromSettings("receiver_rearm") }
                pending.finish()
            }
        }
    }
}
