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
 * Re-arms the daily alarm after a reboot. Note: Android only delivers
 * BOOT_COMPLETED to apps the user has launched at least once.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as StreakGuardApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.alarmScheduler.rescheduleFromSettings()
                app.container.alarmScheduler.scheduleMidnightRefresh()
            } finally {
                pending.finish()
            }
        }
    }
}
