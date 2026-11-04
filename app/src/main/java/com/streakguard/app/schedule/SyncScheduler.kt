package com.streakguard.app.schedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streakguard.app.StreakGuardApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.streakguard.app.diagnostics.Diagnostics
import com.streakguard.app.diagnostics.DiagnosticEvent

class SyncScheduler(private val context: Context) {
    suspend fun schedule(minutes: Int) {
        try { scheduleWork(minutes) } catch (error: Exception) {
            Diagnostics.failure("schedule_status_sync", error)
            throw error
        }
    }

    private suspend fun scheduleWork(minutes: Int) {
        require(minutes in listOf(15, 30, 60))
        if (com.streakguard.app.data.prefs.SettingsStore(context).isCompleted()) {
            cancel()
            return
        }
        val request = PeriodicWorkRequestBuilder<StatusSyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "challenge_status_sync", ExistingPeriodicWorkPolicy.UPDATE, request,
        )
        Diagnostics.log(DiagnosticEvent.SYNC_SCHEDULED, "minutes" to minutes)
    }

    fun cancel() { WorkManager.getInstance(context).cancelUniqueWork("challenge_status_sync")
        Diagnostics.log(DiagnosticEvent.SYNC_CANCELLED) }
}

class StatusSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        Diagnostics.log(DiagnosticEvent.WORKER_STARTED, "kind" to "status_sync")
        val container = (applicationContext as StreakGuardApp).container
        return try {
            if (container.settingsStore.isCompleted()) {
                Diagnostics.log(DiagnosticEvent.CHECK_SKIPPED, "reason" to "already_solved"); return Result.success()
            }
            val statuses = container.checkOrchestrator.runCheck(manual = false)
            val retry = statuses.isNotEmpty() && statuses.all { it.completed == null }
            Diagnostics.log(DiagnosticEvent.WORKER_FINISHED, "kind" to "status_sync", "reason" to if (retry) "retry" else "success")
            if (retry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            Diagnostics.log(DiagnosticEvent.WORKER_FINISHED, "kind" to "status_sync", "reason" to "retry_error", "error_type" to error.javaClass.simpleName)
            Result.retry()
        }
    }
}

class ReminderCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    companion object { private val deliveryMutex = Mutex() }
    private val traceId get() = inputData.getString("trace_id")
    // WorkManager uses a short foreground check for expedited work on Android 8–11.
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val helper = (applicationContext as StreakGuardApp).container.notificationHelper
        return androidx.work.ForegroundInfo(4000, helper.checkingNotification())
    }

    override suspend fun doWork(): Result {
        Diagnostics.checkpoint(traceId, "worker_started", "work_id" to id.toString())
        return try {
            deliveryMutex.withLock {
                checkReminder().also {
                    Diagnostics.checkpoint(traceId, "worker_finished")
                    Diagnostics.log(DiagnosticEvent.WORKER_FINISHED, "trace_id" to traceId, "kind" to "reminder", "reason" to "returned")
                }
            }
        } catch (e: CancellationException) {
            Diagnostics.checkpoint(traceId, "worker_cancelled", "stop_reason" to if (android.os.Build.VERSION.SDK_INT >= 31) stopReason else -1)
            Diagnostics.log(DiagnosticEvent.WORKER_FINISHED, "kind" to "reminder", "reason" to "cancelled"); throw e
        } catch (error: Exception) {
            Diagnostics.checkpoint(traceId, "worker_failed", "error_type" to error.javaClass.simpleName)
            Diagnostics.log(DiagnosticEvent.WORKER_FINISHED, "kind" to "reminder", "reason" to "failed", "error_type" to error.javaClass.simpleName); throw error
        }
    }

    private suspend fun checkReminder(): Result {
        val tier = inputData.getInt("tier", -1)
        val container = (applicationContext as StreakGuardApp).container
        fun skip(reason: String): Result {
            Diagnostics.checkpoint(traceId, "skipped", "reason" to reason)
            Diagnostics.log(DiagnosticEvent.CHECK_SKIPPED, "trace_id" to traceId, "tier" to tier, "reason" to reason)
            return Result.success()
        }
        val day = inputData.getString("day") ?: return skip("missing_day")
        val attempt = inputData.getInt("attempt", 0)
        val signature = inputData.getString("signature") ?: return skip("missing_signature")
        Diagnostics.log(DiagnosticEvent.WORKER_STARTED, "kind" to "reminder", "tier" to tier, "day" to day, "attempt" to attempt)
        val settings = container.settingsStore
        val scheduler = container.alarmScheduler
        val minutes = settings.reminderMinutes.first()
        val key = scheduler.deliveryKey(day, tier)
        val attemptKey = "$key:$signature"
        suspend fun inactiveReason(): String? = when {
            tier !in 0..2 -> "invalid_tier"
            attempt < 0 || (tier == 2 && attempt > ReminderPolicy.MAX_FINAL_RETRIES) -> "invalid_attempt"
            day != TimeUtils.utcToday() -> "wrong_platform_day"
            !settings.remindersEnabled.first() -> "reminders_disabled"
            signature != scheduler.signature() -> "settings_changed"
            container.reminderState.delivered(key) -> "already_delivered"
            settings.isCompleted(day = day) -> "already_solved"
            container.reminderState.attemptFinished(attemptKey, attempt) -> "attempt_already_finished"
            !ReminderPolicy.active(java.time.Instant.now(), day, minutes, tier) -> "outside_delivery_window"
            else -> null
        }

        inactiveReason()?.let { return skip(it) }
        Diagnostics.checkpoint(traceId, "fetch_started")
        val results = container.checkOrchestrator.runCheck(manual = false)
        Diagnostics.checkpoint(traceId, "fetch_finished", "completed" to results.firstOrNull()?.completed)
        // A fetch can cross a cutoff, a settings change, or midnight. Recheck before any side effects.
        inactiveReason()?.let { return skip("after_fetch_$it") }
        return container.checkOrchestrator.ifUnsolved(day) { deliverResult(results, container, tier, day, attempt, signature, minutes, key, attemptKey) }
            ?: skip("solved_before_delivery")
    }

    private suspend fun deliverResult(
        results: List<com.streakguard.app.domain.DailyCheckOrchestrator.PlatformStatus>,
        container: com.streakguard.app.di.AppContainer, tier: Int, day: String, attempt: Int,
        signature: String, minutes: List<Int>, key: String, attemptKey: String,
    ): Result {
        val settings = container.settingsStore
        val scheduler = container.alarmScheduler
        if (results.isEmpty()) {
            Diagnostics.checkpoint(traceId, "skipped", "reason" to "no_platform_results")
            scheduler.cancelReminderRetry(tier)
            return Result.success()
        }
        val status = results.first()
        if (status.completed != null) {
            container.reminderState.markDelivered(key)
            Diagnostics.checkpoint(traceId, "delivery_claimed", "completed" to status.completed)
            scheduler.cancelReminderRetry(tier)
            if (status.completed == false) {
                if (tier == 2 && com.streakguard.app.notify.AlarmFeature.ENABLED && settings.alarmsEnabled.first()) container.challengeAlarm.start(day, status.platformId,
                    settings.username(status.platformId).first(), status.challengeTitle ?: "Today's challenge")
                else container.notificationHelper.notifyMissed(status.displayName, status.platformId,
                    status.challengeTitle ?: "Today's challenge", status.challengeUrl ?: "https://leetcode.com",
                    tier, settings.hapticsEnabled.first(), traceId)
            } else {
                Diagnostics.checkpoint(traceId, "skipped", "reason" to "confirmed_solved")
            }
            return Result.success()
        }

        val next = ReminderPolicy.nextRetry(java.time.Instant.now(), day, minutes, tier, attempt)
        if (container.reminderState.claimFailureNotice()) {
            container.notificationHelper.notifyFailure(next != null, settings.hapticsEnabled.first(), traceId)
        }
        if (next != null) {
            Diagnostics.checkpoint(traceId, "retry_required", "at" to next.toString())
            scheduler.scheduleReminderRetry(day, tier, attempt + 1, next.toEpochMilli(), signature)
        } else {
            Diagnostics.checkpoint(traceId, "retry_exhausted", "reason" to "status_unknown")
            scheduler.cancelReminderRetry(tier)
            container.reminderState.markDelivered(key)
            val username = settings.username(status.platformId).first()
            val cache = container.database.checkLogDao().getForDate(status.platformId, day)
                .takeIf { settings.checkedUsername(status.platformId).first() == username }
            val lastCompleted = cache?.takeIf { it.known }?.completed
            if (com.streakguard.app.notify.AlarmFeature.ENABLED && settings.alarmsEnabled.first() && ReminderPolicy.shouldRingAfterFailure(tier, attempt, lastCompleted)) {
                container.challengeAlarm.start(day, status.platformId, username,
                    cache?.challengeTitle ?: "Today's challenge status is unknown")
            }
        }
        container.reminderState.finishAttempt(attemptKey, attempt)
        return Result.success()
    }
}
