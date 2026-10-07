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
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.streakguard.app.schedule.ReminderCheckWorker

/**
 * Fired by the daily exact alarm. Runs the check, then re-arms the alarm for
 * the next day so the cadence is self-perpetuating.
 */
class DailyCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as StreakGuardApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val tier = intent.getIntExtra("reminder_tier", -1)
                val day = intent.getStringExtra("platform_day")
                if (tier in 0..2 && day == TimeUtils.utcToday() &&
                    app.container.settingsStore.remindersEnabled.first()) {
                    // Queue the network work outside the receiver's short execution window.
                    val work = OneTimeWorkRequestBuilder<ReminderCheckWorker>()
                        .addTag("countdown_reminder")
                        .setInputData(workDataOf("tier" to tier, "day" to day))
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork("reminder_${day}_$tier", ExistingWorkPolicy.KEEP, work)
                }
            } finally {
                runCatching { app.container.alarmScheduler.rescheduleFromSettings() }
                pending.finish()
            }
        }
    }
}
