package com.streakguard.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streakguard.app.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlatformSettingsUi(
    val platformId: String,
    val displayName: String,
    val username: String,
    val enabled: Boolean,
)

/**
 * Plain ViewModel (no DI framework): takes the [AppContainer] via a simple factory.
 * Edits are held in memory and persisted with [save], which also re-arms the daily alarm.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    var platforms by mutableStateOf<List<PlatformSettingsUi>>(emptyList())
        private set
    var checkHour by mutableStateOf(21)
        private set
    var checkMinute by mutableStateOf(0)
        private set
    var confirmWhenDone by mutableStateOf(false)
        private set
    var savedMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val store = container.settingsStore
        platforms = container.platformRegistry.platforms.map { platform ->
            PlatformSettingsUi(
                platformId = platform.id,
                displayName = platform.displayName,
                username = runCatching { store.username(platform.id).first() }.getOrDefault(""),
                enabled = runCatching { store.enabled(platform.id).first() }.getOrDefault(true),
            )
        }
        checkHour = runCatching { store.getCheckHour() }.getOrDefault(21)
        checkMinute = runCatching { store.getCheckMinute() }.getOrDefault(0)
        confirmWhenDone = runCatching { store.confirmWhenDone.first() }.getOrDefault(false)
    }

    fun onUsernameChange(platformId: String, value: String) {
        savedMessage = null
        platforms = platforms.map {
            if (it.platformId == platformId) it.copy(username = value) else it
        }
    }

    fun onEnabledChange(platformId: String, value: Boolean) {
        savedMessage = null
        platforms = platforms.map {
            if (it.platformId == platformId) it.copy(enabled = value) else it
        }
    }

    fun onTimeChange(hour: Int, minute: Int) {
        savedMessage = null
        checkHour = hour
        checkMinute = minute
    }

    fun onConfirmWhenDoneChange(value: Boolean) {
        savedMessage = null
        confirmWhenDone = value
    }

    fun save() {
        viewModelScope.launch {
            val store = container.settingsStore
            platforms.forEach { platform ->
                store.setUsername(platform.platformId, platform.username.trim())
                store.setEnabled(platform.platformId, platform.enabled)
            }
            store.setCheckTime(checkHour, checkMinute)
            store.setConfirmWhenDone(confirmWhenDone)
            runCatching { container.alarmScheduler.rescheduleFromSettings() }
            savedMessage = "Saved. Daily check at %02d:%02d.".format(checkHour, checkMinute)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}
