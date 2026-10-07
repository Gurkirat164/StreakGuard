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

/**
 * Re-arms the daily alarm after a reboot. Note: Android only delivers
 * BOOT_COMPLETED to apps the user has launched at least once.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        val app = context.applicationContext as StreakGuardApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.alarmScheduler.rescheduleFromSettings()
                app.container.alarmScheduler.scheduleMidnightRefresh()
                app.container.syncScheduler.schedule(app.container.settingsStore.syncInterval.first())
            } finally {
                pending.finish()
            }
        }
    }
}
