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
 * All notifications go through one high-importance channel ("potd_reminder").
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
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("$platformName: today's challenge isn't done yet")
            .setContentText(challengeTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(challengeTitle))
            .setContentIntent(challengeIntent(challengeUrl))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIF_MISSED_BASE + platformId.hashCode().and(0xFFF), notification)
    }

    /** Optional confirmation posted when the challenge is already done. */
    fun notifyDone(platformName: String, platformId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("$platformName: streak safe")
            .setContentText("Today's challenge is done. Nice work.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIF_DONE_BASE + platformId.hashCode().and(0xFFF), notification)
    }

    fun sendTest() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("StreakGuard test notification")
            .setContentText("If you can read this, reminders will reach you.")
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_TEST, notification)
    }

    companion object {
        const val CHANNEL_ID = "potd_reminder"
        private const val NOTIF_MISSED_BASE = 2000
        private const val NOTIF_DONE_BASE = 3000
        private const val NOTIF_TEST = 9999
    }
}
