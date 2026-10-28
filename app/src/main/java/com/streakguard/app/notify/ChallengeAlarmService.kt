package com.streakguard.app.notify

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.streakguard.app.R
import com.streakguard.app.StreakGuardApp
import com.streakguard.app.schedule.ReminderPolicy
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ChallengeAlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var job: Job? = null
    private var activeToken: String? = null
    private val container get() = (application as StreakGuardApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra("token")
        val current = container.challengeAlarm.current()
        // Always enter the foreground promptly, including stale start requests.
        ServiceCompat.startForeground(this, ChallengeAlarm.NOTIFICATION_ID,
            container.notificationHelper.alarmNotification(current),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        getSystemService(android.app.NotificationManager::class.java).cancel(ChallengeAlarm.WAITING_NOTIFICATION_ID)
        if (!AlarmFeature.ENABLED) {
            container.challengeAlarm.stop(); stopSelf(); return START_NOT_STICKY
        }
        if (token == null || current?.token != token || !container.notificationHelper.alarmsEnabled()) {
            stopSelf(); return START_NOT_STICKY
        }
        if (activeToken == token) return START_NOT_STICKY
        activeToken = token
        isRinging = true
        val occurrence = container.challengeAlarm.claimRing(token)
        if (occurrence == null) {
            container.challengeAlarm.stop(token); stopSelf(); return START_NOT_STICKY
        }
        getSystemService(android.app.NotificationManager::class.java).notify(ChallengeAlarm.NOTIFICATION_ID,
            container.notificationHelper.alarmNotification(occurrence))
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "StreakGuard:alarm").apply { acquire(90_000) }
        job = scope.launch {
            val settings = container.settingsStore
            val valid = settings.remindersEnabled.first() && settings.enabled(occurrence.platform).first() &&
                settings.username(occurrence.platform).first() == occurrence.username && occurrence.day == TimeUtils.utcToday()
            val cached = withContext(Dispatchers.IO) {
                container.database.checkLogDao().getForDate(occurrence.platform, occurrence.day)
            }.takeIf { settings.checkedUsername(occurrence.platform).first() == occurrence.username }
            if (!valid || (cached?.known == true && cached.completed)) {
                container.challengeAlarm.stop(token); return@launch
            }
            try {
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            player?.pause()
                            getSystemService(Vibrator::class.java)?.cancel()
                        }
                        if (change == AudioManager.AUDIOFOCUS_GAIN) player?.start()
                    }.build()
                val audioManager = getSystemService(AudioManager::class.java)
                if (audioManager.requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    player = MediaPlayer().apply {
                        setAudioAttributes(attributes)
                        resources.openRawResourceFd(R.raw.streakguard_alarm).use { fd ->
                            setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                        }
                        isLooping = true
                        prepare()
                        start()
                    }
                }
                if (settings.hapticsEnabled.first()) {
                    getSystemService(Vibrator::class.java)?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 1000), 0), attributes)
                }
                val beforeReset = ReminderPolicy.reset(occurrence.day).toEpochMilli() - System.currentTimeMillis()
                delay(minOf(ReminderPolicy.RING_MILLIS, beforeReset.coerceAtLeast(0)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("ChallengeAlarm", "Playback failed", e) }
            finally { releasePlayback() }
            container.challengeAlarm.repeat(token)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun releasePlayback() {
        player?.release(); player = null
        getSystemService(Vibrator::class.java)?.cancel()
        focus?.let { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }; focus = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    override fun onDestroy() {
        isRinging = false
        scope.cancel()
        releasePlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        @Volatile var isRinging: Boolean = false
            private set
    }
}
