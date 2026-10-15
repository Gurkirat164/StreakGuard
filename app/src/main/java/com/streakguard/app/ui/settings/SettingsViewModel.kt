package com.streakguard.app.ui.settings

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streakguard.app.di.AppContainer
import com.streakguard.app.schedule.ReminderSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    var username by mutableStateOf("")
        private set
    var profileVerified by mutableStateOf(false)
        private set
    var remindersEnabled by mutableStateOf(true)
        private set
    var hapticsEnabled by mutableStateOf(true)
        private set
    var syncOnOpen by mutableStateOf(true)
        private set
    var syncInterval by mutableStateOf(15)
        private set
    var reminderHours by mutableStateOf(ReminderSchedule.defaultHours)
        private set
    var displayTimezone by mutableStateOf("UTC")
        private set
    var isClearing by mutableStateOf(false)
        private set
    var initialized by mutableStateOf(false)
        private set
    var isTesting by mutableStateOf(false)
        private set
    var connectionLatency by mutableStateOf<Long?>(null)
        private set
    var connectionOk by mutableStateOf<Boolean?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private val edits = Mutex()
    val zone: ZoneId get() = if (displayTimezone == "DEVICE") ZoneId.systemDefault()
        else runCatching { ZoneId.of(displayTimezone) }.getOrDefault(ZoneId.of("UTC"))

    init {
        viewModelScope.launch {
            try {
                loadSettings()
            } catch (_: Exception) { message = "Couldn't load settings. Please reopen this screen." }
            finally { initialized = true }
        }
    }
    private suspend fun loadSettings() {
        val store = container.settingsStore
        username = store.username("leetcode").first()
        profileVerified = username.isNotBlank() && store.verifiedUsername("leetcode").first() == username
        remindersEnabled = store.remindersEnabled.first()
        hapticsEnabled = store.hapticsEnabled.first()
        syncOnOpen = store.syncOnOpen.first()
        syncInterval = store.syncInterval.first()
        reminderHours = store.reminderHours.first()
        displayTimezone = store.displayTimezone.first()
    }
    private fun edit(action: suspend () -> Unit) {
        if (!initialized || isClearing) return
        viewModelScope.launch {
            edits.withLock {
                try { action(); message = null }
                catch (_: Exception) { message = "Couldn't save the change. Please try again." }
            }
        }
    }
    fun changeUsername(value: String) {
        val handle = value.trim().removePrefix("@")
        if (handle.isBlank() || handle.any { it.isWhitespace() }) {
            message = "Enter a public LeetCode username without spaces."; return
        }
        edit {
            container.settingsStore.setUsername("leetcode", handle)
            container.challengeAlarm.stop()
            container.settingsStore.setEnabled("leetcode", true)
            username = handle; profileVerified = false; connectionOk = null; connectionLatency = null
            container.alarmScheduler.rescheduleFromSettings()
            testConnection()
        }
    }
    fun setReminders(value: Boolean) = edit {
        container.settingsStore.setRemindersEnabled(value); remindersEnabled = value
        if (!value) androidx.work.WorkManager.getInstance(container.appContext()).cancelAllWorkByTag("countdown_reminder")
        if (!value) container.challengeAlarm.stop()
        container.alarmScheduler.rescheduleFromSettings()
    }
    fun setHaptics(value: Boolean) = edit {
        container.settingsStore.setHapticsEnabled(value); hapticsEnabled = value
    }
    fun onSyncOnOpenChange(value: Boolean) = edit {
        container.settingsStore.setSyncOnOpen(value); syncOnOpen = value
    }
    fun setInterval(value: Int) = edit {
        container.settingsStore.setSyncInterval(value); syncInterval = value
        container.syncScheduler.schedule(value)
    }
    fun setTimezone(value: String) = edit {
        container.settingsStore.setDisplayTimezone(value); displayTimezone = value
    }
    fun clearData() {
        if (!initialized || isClearing || isTesting) return
        isClearing = true
        viewModelScope.launch {
            try {
                edits.withLock {
                    val workManager = androidx.work.WorkManager.getInstance(container.appContext())
                    workManager.cancelAllWorkByTag("countdown_reminder")
                    workManager.cancelUniqueWork("challenge_status_sync")
                    container.alarmScheduler.cancel()
                    container.checkOrchestrator.clearUserData()
                    loadSettings()
                    connectionOk = null
                    connectionLatency = null
                    container.syncScheduler.schedule(syncInterval)
                    message = "Local data cleared."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "Couldn't clear local data. Please try again."
            } finally {
                isClearing = false
            }
        }
    }
    fun setTiers(hours: List<Int>): Boolean {
        if (!ReminderSchedule.valid(hours)) return false
        edit {
            container.settingsStore.setReminderHours(hours); reminderHours = hours
            container.challengeAlarm.stop()
            androidx.work.WorkManager.getInstance(container.appContext()).cancelAllWorkByTag("countdown_reminder")
            container.alarmScheduler.rescheduleFromSettings()
        }
        return true
    }
    fun testConnection() {
        if (isTesting || isClearing || username.isBlank()) return
        val handle = username
        isTesting = true
        viewModelScope.launch {
            try {
                val started = SystemClock.elapsedRealtime()
                val exists = container.leetCodeApi.profileExists(handle)
                if (username != handle) return@launch
                connectionLatency = SystemClock.elapsedRealtime() - started
                connectionOk = exists == true; profileVerified = exists == true
                container.settingsStore.setVerifiedUsername("leetcode", if (exists == true) handle else "")
                message = when (exists) {
                    true -> null
                    false -> "That public LeetCode profile wasn't found. Check your username."
                    null -> "Couldn't reach LeetCode. Check your connection and try again."
                }
            } catch (_: Exception) { connectionOk = false; message = "Connection test failed. Please try again." }
            finally { isTesting = false }
        }
    }
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
    }
}
