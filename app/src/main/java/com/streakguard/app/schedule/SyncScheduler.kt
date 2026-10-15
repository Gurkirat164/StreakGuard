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

class SyncScheduler(private val context: Context) {
    fun schedule(minutes: Int) {
        require(minutes in listOf(15, 30, 60))
        val request = PeriodicWorkRequestBuilder<StatusSyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "challenge_status_sync", ExistingPeriodicWorkPolicy.UPDATE, request,
        )
    }
}

class StatusSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as StreakGuardApp).container
        return try {
            val statuses = container.checkOrchestrator.runCheck(manual = false)
            if (statuses.isNotEmpty() && statuses.all { it.completed == null }) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class ReminderCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    companion object { private val deliveryMutex = Mutex() }
    // WorkManager uses a short foreground check for expedited work on Android 8–11.
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val helper = (applicationContext as StreakGuardApp).container.notificationHelper
        return androidx.work.ForegroundInfo(4000, helper.checkingNotification())
    }

    override suspend fun doWork(): Result = deliveryMutex.withLock { checkReminder() }

    private suspend fun checkReminder(): Result {
        val tier = inputData.getInt("tier", -1)
        val container = (applicationContext as StreakGuardApp).container
        val day = inputData.getString("day") ?: return Result.success()
        val attempt = inputData.getInt("attempt", 0)
        val signature = inputData.getString("signature") ?: return Result.success()
        val settings = container.settingsStore
        val scheduler = container.alarmScheduler
        val hours = settings.reminderHours.first()
        val key = scheduler.deliveryKey(day, tier)
        val attemptKey = "$key:$signature"
        suspend fun active(): Boolean = tier in 0..2 && attempt >= 0 &&
            (tier != 2 || attempt <= ReminderPolicy.MAX_FINAL_RETRIES) &&
            day == TimeUtils.utcToday() && settings.remindersEnabled.first() &&
            signature == scheduler.signature() && !container.reminderState.delivered(key) &&
            !container.reminderState.attemptFinished(attemptKey, attempt) &&
            ReminderPolicy.active(java.time.Instant.now(), day, hours, tier)

        if (!active()) return Result.success()
        val results = container.checkOrchestrator.runCheck(manual = false)
        // A fetch can cross a cutoff, a settings change, or midnight. Recheck before any side effects.
        if (!active()) return Result.success()
        if (results.isEmpty()) {
            scheduler.cancelReminderRetry(tier)
            return Result.success()
        }
        val status = results.first()
        if (status.completed != null) {
            container.reminderState.markDelivered(key)
            scheduler.cancelReminderRetry(tier)
            if (status.completed == false) {
                if (tier == 2 && com.streakguard.app.notify.AlarmFeature.ENABLED) container.challengeAlarm.start(day, status.platformId,
                    settings.username(status.platformId).first(), status.challengeTitle ?: "Today's challenge")
                else container.notificationHelper.notifyMissed(status.displayName, status.platformId,
                    status.challengeTitle ?: "Today's challenge", status.challengeUrl ?: "https://leetcode.com",
                    tier, settings.hapticsEnabled.first())
            }
            return Result.success()
        }

        val next = ReminderPolicy.nextRetry(java.time.Instant.now(), day, hours, tier, attempt)
        if (container.reminderState.claimFailureNotice()) {
            container.notificationHelper.notifyFailure(next != null)
        }
        if (next != null) {
            scheduler.scheduleReminderRetry(day, tier, attempt + 1, next.toEpochMilli(), signature)
        } else {
            scheduler.cancelReminderRetry(tier)
            container.reminderState.markDelivered(key)
            val username = settings.username(status.platformId).first()
            val cache = container.database.checkLogDao().getForDate(status.platformId, day)
                .takeIf { settings.checkedUsername(status.platformId).first() == username }
            val lastCompleted = cache?.takeIf { it.known }?.completed
            if (ReminderPolicy.shouldRingAfterFailure(tier, attempt, lastCompleted)) {
                container.challengeAlarm.start(day, status.platformId, username,
                    cache?.challengeTitle ?: "Today's challenge status is unknown")
            }
        }
        container.reminderState.finishAttempt(attemptKey, attempt)
        return Result.success()
    }
}
