package com.streakguard.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.streakguard.app.schedule.ReminderPolicy
import java.time.Instant
import java.util.UUID

/** One durable alarm occurrence, independent of the phone's Clock app. */
@android.annotation.SuppressLint("ApplySharedPref") // Ring limits must survive termination between callbacks.
class ChallengeAlarm(private val context: Context) {
    private val prefs = context.getSharedPreferences("challenge_alarm", Context.MODE_PRIVATE)
    data class Occurrence(val token: String, val day: String, val platform: String,
        val username: String, val title: String, val rings: Int)

    @Synchronized fun current(): Occurrence? {
        val token = prefs.getString("token", null) ?: return null
        return Occurrence(token, prefs.getString("day", "")!!, prefs.getString("platform", "")!!,
            prefs.getString("username", "")!!, prefs.getString("title", "Today's challenge")!!,
            prefs.getInt("rings", 0))
    }

    @Synchronized fun start(day: String, platform: String, username: String, title: String) {
        com.streakguard.app.diagnostics.Diagnostics.log(com.streakguard.app.diagnostics.DiagnosticEvent.ALARM_EVENT,
            "kind" to "start_requested", "enabled" to AlarmFeature.ENABLED, "day" to day)
        if (!AlarmFeature.ENABLED) { stop(); return }
        if (!(context.applicationContext as com.streakguard.app.StreakGuardApp).container.notificationHelper.alarmsEnabled()) return
        if (current()?.let { it.day == day && it.platform == platform && it.username == username } == true) return
        stop()
        prefs.edit().putString("token", UUID.randomUUID().toString()).putString("day", day)
            .putString("platform", platform).putString("username", username).putString("title", title)
            .putInt("rings", 0).commit()
        // Start via an exact-alarm broadcast, which permits background foreground-service launches.
        schedule(System.currentTimeMillis() + 1000)
    }

    @Synchronized fun claimRing(token: String): Occurrence? {
        if (!AlarmFeature.ENABLED) { stop(); return null }
        val alarm = current() ?: return null
        if (alarm.token != token || alarm.rings >= ReminderPolicy.MAX_RINGS ||
            !Instant.now().isBefore(ReminderPolicy.reset(alarm.day))) return null
        prefs.edit().putInt("rings", alarm.rings + 1).remove("next").commit()
        return alarm.copy(rings = alarm.rings + 1)
    }

    @Synchronized fun repeat(token: String) {
        if (!AlarmFeature.ENABLED) { stop(); return }
        val alarm = current()?.takeIf { it.token == token } ?: return
        val next = ReminderPolicy.nextRing(Instant.now(), alarm.day, alarm.rings)
        if (next == null) { stop(); return }
        schedule(next.toEpochMilli())
    }

    @Synchronized fun stopFor(platform: String, day: String) {
        if (current()?.let { it.platform == platform && it.day == day } == true) stop()
    }

    @Synchronized fun stop(token: String? = null) {
        com.streakguard.app.diagnostics.Diagnostics.log(com.streakguard.app.diagnostics.DiagnosticEvent.ALARM_EVENT, "kind" to "stop")
        if (token != null && current()?.token != token) return
        val pending = pendingIntent(PendingIntent.FLAG_NO_CREATE)
        if (pending != null) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pending)
            pending.cancel()
        }
        prefs.edit().clear().commit()
        context.stopService(Intent(context, ChallengeAlarmService::class.java))
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(WAITING_NOTIFICATION_ID)
    }

    @Synchronized fun restore() {
        if (!AlarmFeature.ENABLED) { stop(); return }
        if (ChallengeAlarmService.isRinging) return
        val alarm = current() ?: return
        if (!Instant.now().isBefore(ReminderPolicy.reset(alarm.day)) || alarm.rings >= ReminderPolicy.MAX_RINGS) {
            stop(); return
        }
        val next = prefs.getLong("next", 0)
        // If the process died mid-ring, count that session and resume at the normal interval.
        schedule(if (next > 0) maxOf(next, System.currentTimeMillis() + 1000)
            else System.currentTimeMillis() + ReminderPolicy.RETRY_SECONDS * 1000)
    }

    private fun pendingIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(context, 3000,
        Intent(context, ChallengeAlarmReceiver::class.java).apply {
            action = ACTION_RING; putExtra("token", current()?.token)
        }, flags or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(at: Long) {
        prefs.edit().putLong("next", at).commit()
        val pending = pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val show = PendingIntent.getActivity(context, 3005,
            Intent(context, com.streakguard.app.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending) }
        catch (_: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
        current()?.takeIf { it.rings > 0 }?.let {
            val helper = (context.applicationContext as com.streakguard.app.StreakGuardApp).container.notificationHelper
            if (helper.notificationsEnabled()) context.getSystemService(android.app.NotificationManager::class.java)
                ?.notify(WAITING_NOTIFICATION_ID, helper.alarmNotification(it, waiting = true))
        }
    }

    companion object {
        const val ACTION_RING = "com.streakguard.app.RING"
        const val ACTION_STOP = "com.streakguard.app.STOP_ALARM"
        const val ACTION_SNOOZE = "com.streakguard.app.SNOOZE_ALARM"
        const val NOTIFICATION_ID = 3000
        const val WAITING_NOTIFICATION_ID = 3004
    }
}
