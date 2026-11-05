package com.streakguard.app.schedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streakguard.app.StreakGuardApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class UsernameValidationScheduler(private val context: Context) {
    fun enqueue() {
        val work = OneTimeWorkRequestBuilder<UsernameValidationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("username_validation", ExistingWorkPolicy.REPLACE, work)
    }

    fun cancel() { WorkManager.getInstance(context).cancelUniqueWork("username_validation") }
}

class UsernameValidationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as StreakGuardApp).container
        return try {
            if (!container.usernameManager.validatePending()) return Result.retry()
            container.alarmScheduler.rescheduleFromSettings("username_validated")
            container.syncScheduler.schedule(container.settingsStore.syncInterval.first())
            val statuses = container.checkOrchestrator.runCheck(manual = false)
            if (statuses.isNotEmpty() && statuses.all { it.completed == null }) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            com.streakguard.app.diagnostics.Diagnostics.failure("username_validation", e)
            Result.retry()
        }
    }
}
