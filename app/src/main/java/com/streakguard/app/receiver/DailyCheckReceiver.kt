package com.streakguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.streakguard.app.StreakGuardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
                app.container.checkOrchestrator.runCheck(manual = false)
            } finally {
                runCatching { app.container.alarmScheduler.rescheduleFromSettings() }
                pending.finish()
            }
        }
    }
}
