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
 * Fired at each UTC midnight (and on retries). Silently refreshes the cache
 * for the new platform day — no notifications — so the card shows fresh data
 * when the user opens the app.
 *
 * When the network/API fails, a retry is scheduled [RETRY_DELAY_MINUTES]
 * later, up to [MAX_RETRY_ATTEMPTS] times.
 */
class MidnightRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val attempt = intent.getIntExtra(EXTRA_RETRY_ATTEMPT, 0)
        val app = context.applicationContext as StreakGuardApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val results = app.container.checkOrchestrator.runCheck(
                    manual = false,
                    notify = false,
                )
                if (attempt == 0) {
                    // The midnight tick: re-arm for the next UTC midnight.
                    runCatching { app.container.alarmScheduler.scheduleMidnightRefresh() }
                }
                val allUnknown = results.isEmpty() ||
                    results.all { it.completed == null && it.challengeTitle == null }
                if (allUnknown && attempt < MAX_RETRY_ATTEMPTS) {
                    runCatching {
                        app.container.alarmScheduler.scheduleRefreshRetry(
                            RETRY_DELAY_MINUTES,
                            attempt + 1,
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_RETRY_ATTEMPT = "retry_attempt"
        private const val MAX_RETRY_ATTEMPTS = 6
        private const val RETRY_DELAY_MINUTES = 30L
    }
}
