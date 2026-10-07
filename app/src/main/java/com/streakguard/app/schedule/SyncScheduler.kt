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
            val statuses = container.checkOrchestrator.runCheck(manual = false, notify = false)
            if (statuses.isNotEmpty() && statuses.all { it.completed == null }) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class ReminderCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val tier = inputData.getInt("tier", -1)
        val container = (applicationContext as StreakGuardApp).container
        if (tier !in 0..2 || inputData.getString("day") != TimeUtils.utcToday() ||
            !container.settingsStore.remindersEnabled.first()) return Result.success()
        return try {
            val results = container.checkOrchestrator.runCheck(manual = false, reminderTier = tier)
            if (results.isNotEmpty() && results.all { it.completed == null } && runAttemptCount < 3)
                Result.retry() else Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
