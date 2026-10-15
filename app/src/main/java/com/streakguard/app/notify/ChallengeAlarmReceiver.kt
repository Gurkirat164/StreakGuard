package com.streakguard.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.streakguard.app.StreakGuardApp

class ChallengeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarm = (context.applicationContext as StreakGuardApp).container.challengeAlarm
        if (!AlarmFeature.ENABLED) { alarm.stop(); return }
        val token = intent.getStringExtra("token") ?: return
        if (alarm.current()?.token != token) return
        when (intent.action) {
            ChallengeAlarm.ACTION_STOP -> alarm.stop(token)
            ChallengeAlarm.ACTION_SNOOZE -> {
                context.stopService(Intent(context, ChallengeAlarmService::class.java))
                alarm.repeat(token)
            }
            ChallengeAlarm.ACTION_RING -> {
                try {
                    ContextCompat.startForegroundService(context,
                        Intent(context, ChallengeAlarmService::class.java).putExtra("token", token))
                } catch (e: RuntimeException) {
                    // Exact-alarm access may have been revoked since scheduling.
                    android.util.Log.w("ChallengeAlarm", "Alarm service launch unavailable", e)
                    (context.applicationContext as StreakGuardApp).container.notificationHelper.notifyAlarmUnavailable()
                    alarm.stop(token)
                }
            }
        }
    }
}
