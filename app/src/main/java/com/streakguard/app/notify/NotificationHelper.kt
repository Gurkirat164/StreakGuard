package com.streakguard.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    /**
     * Rings the alarm tone for a few seconds so a missed challenge is hard
     * to ignore, then stops itself.
     */
    fun playAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.play()
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { ringtone.stop() } },
                ALARM_RING_MILLIS,
            )
        } catch (e: Exception) {
            Log.w("NotificationHelper", "Could not play alarm sound", e)
        }
    }

    /** Posted when today's challenge is confirmed not done. Tapping opens the problem. */
    fun notifyMissed(
        platformName: String,
        platformId: String,
        challengeTitle: String,
        challengeUrl: String,
        tier: Int = 2,
        haptics: Boolean = true,
    ) {
        if (!notificationsEnabled()) return
        val channelId = tierChannel(tier, haptics)
        val channel = manager.getNotificationChannel(channelId)
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return
        if (tier == 2 && channel.sound != null) playAlarmSound()
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

    /** Optional confirmation posted when the challenge is already done. */
    fun notifyDone(platformName: String, platformId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$platformName: streak safe")
            .setContentText("Today's challenge is done. Nice work.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIF_DONE_BASE + platformId.hashCode().and(0xFFF), notification)
    }

    fun sendTest() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
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
        private const val ALARM_RING_MILLIS = 5_000L
    }
}
