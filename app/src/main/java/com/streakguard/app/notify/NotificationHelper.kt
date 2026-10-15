package com.streakguard.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.streakguard.app.R

/**
 * Countdown reminders use tier channels; test notifications use "potd_reminder".
 */
class NotificationHelper(private val context: Context) {

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "POTD Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Daily reminders when today's challenge isn't done yet"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun notificationsEnabled(): Boolean = manager.areNotificationsEnabled()

    private fun challengeIntent(url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Posted when today's challenge is confirmed not done. Tapping opens the problem. */
    fun notifyMissed(
        platformName: String,
        platformId: String,
        challengeTitle: String,
        challengeUrl: String,
        tier: Int,
        haptics: Boolean = true,
    ) {
        if (!notificationsEnabled()) return
        val channelId = tierChannel(tier, haptics)
        val channel = manager.getNotificationChannel(channelId)
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$platformName: today's challenge isn't done yet")
            .setContentText(challengeTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(challengeTitle))
            .setContentIntent(challengeIntent(challengeUrl))
            .setAutoCancel(true)
            .setPriority(if (tier == 0) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIF_MISSED_BASE + platformId.hashCode().and(0xFFF), notification)
    }

    private fun tierChannel(tier: Int, haptics: Boolean): String {
        val id = "reminder_${tier}_${if (haptics && tier > 0) "haptic" else "quiet"}"
        val names = listOf("Passive ping", "Warning nudge", "Urgent buzzer")
        val channel = NotificationChannel(id, names[tier.coerceIn(0, 2)],
            if (tier == 0) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Countdown reminders before the daily UTC reset"
            enableVibration(haptics && tier > 0)
            if (haptics && tier > 0) vibrationPattern = longArrayOf(0, 180, 120, 180)
            if (tier == 0) setSound(null, null)
        }
        manager.createNotificationChannel(channel)
        return id
    }

    fun sendTest() {
        if (!notificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("StreakGuard test notification")
            .setContentText("If you can read this, reminders will reach you.")
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_TEST, notification)
    }

    fun notifyFailure(willRetry: Boolean) {
        if (!notificationsEnabled()) return
        val network = context.getSystemService(android.net.ConnectivityManager::class.java)
        val online = network.getNetworkCapabilities(network.activeNetwork)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val reason = if (online) "Couldn't fetch challenge details." else "No internet connection."
        val message = reason + if (willRetry) " We'll check again in 10 minutes."
            else " We'll check at the next scheduled reminder."
        manager.createNotificationChannel(NotificationChannel(FAILURE_CHANNEL, "Connection issues",
            NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(NOTIF_FAILURE, NotificationCompat.Builder(context, FAILURE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("StreakGuard couldn't check")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true).build())
    }

    fun cancelFailure() = manager.cancel(NOTIF_FAILURE)

    fun checkingNotification(): android.app.Notification {
        val channel = "reminder_checks"
        manager.createNotificationChannel(NotificationChannel(channel, "Reminder checks",
            NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        return NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Checking today's challenge").setSilent(true).setOngoing(true).build()
    }

    fun alarmsEnabled(): Boolean {
        createAlarmChannel()
        return notificationsEnabled() && manager.getNotificationChannel(ALARM_CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun createAlarmChannel() {
        manager.createNotificationChannel(NotificationChannel(ALARM_CHANNEL, "StreakGuard alarms",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ringing alarms with Stop and Snooze controls"
            setSound(null, null)
            enableVibration(false)
        })
    }

    fun alarmNotification(alarm: ChallengeAlarm.Occurrence?, waiting: Boolean = false): android.app.Notification {
        createAlarmChannel()
        fun action(action: String, code: Int) = PendingIntent.getBroadcast(context, code,
            Intent(context, ChallengeAlarmReceiver::class.java).apply {
                this.action = action; putExtra("token", alarm?.token)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 3003,
            Intent(context, com.streakguard.app.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(if (waiting) "StreakGuard alarm paused" else "StreakGuard alarm")
            .setContentText(if (waiting) "Ringing again in 10 minutes. Tap Stop to cancel." else alarm?.title ?: "Checking today's challenge")
            .setContentIntent(open).setOngoing(true).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setOnlyAlertOnce(true)
            .addAction(0, "Stop", action(ChallengeAlarm.ACTION_STOP, 3001))
        if (!waiting && (alarm?.rings ?: 0) < com.streakguard.app.schedule.ReminderPolicy.MAX_RINGS)
            builder.addAction(0, "Snooze 10 min", action(ChallengeAlarm.ACTION_SNOOZE, 3002))
        return builder.build()
    }

    fun notifyAlarmUnavailable() {
        if (!notificationsEnabled()) return
        manager.notify(NOTIF_TEST, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("StreakGuard alarm needs permission")
            .setContentText("Allow Alarms & reminders in Settings for scheduled ringing.")
            .setAutoCancel(true).build())
    }

    fun cancelAll() = manager.cancelAll()

    companion object {
        const val CHANNEL_ID = "potd_reminder"
        private const val NOTIF_MISSED_BASE = 2000
        private const val NOTIF_TEST = 9999
        private const val FAILURE_CHANNEL = "connection_issues"
        private const val ALARM_CHANNEL = "streakguard_alarm"
        private const val NOTIF_FAILURE = 9998
    }
}
