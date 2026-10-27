package com.streakguard.app.ui.battery

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streakguard.app.data.prefs.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class BatteryReliabilityViewModel(
    context: Context,
    private val store: SettingsStore,
) : ViewModel() {
    private val readStatus = context.applicationContext.let { appContext ->
        { readBatteryRestrictions(appContext) }
    }
    var status by mutableStateOf(readStatus())
        private set
    var state by mutableStateOf(BatteryPromptState())
        private set
    var dontShowAgain by mutableStateOf(false)
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var suppressed = false
    private var preferencesLoaded = false
    private var foreground = false
    private var ready = false

    init {
        viewModelScope.launch {
            store.batteryPromptsSuppressed.collect { value ->
                suppressed = value
                val firstLoad = !preferencesLoaded
                preferencesLoaded = true
                if (value) state = state.copy(prompt = BatteryPrompt.NONE)
                if (firstLoad) checkLaunch()
            }
        }
    }

    fun setReady(value: Boolean) { ready = value; checkLaunch() }

    fun onForeground() {
        foreground = true
        status = readStatus()
        checkLaunch()
    }

    private fun checkLaunch() {
        if (!foreground || !ready || !preferencesLoaded) return
        if (!state.visitActive && !state.awaitingSettingsReturn) {
            dontShowAgain = false
            error = null
        }
        state = state.foreground(status, suppressed)
    }

    fun onBackground() { foreground = false; state = state.background() }

    fun dismiss() = act(openSettings = null)

    fun openSettings(launch: () -> Boolean) = act(openSettings = launch)

    private fun act(openSettings: (() -> Boolean)?) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            error = null
            try {
                if (state.prompt == BatteryPrompt.CONFIRMATION && dontShowAgain) {
                    store.setBatteryPromptsSuppressed(true)
                    suppressed = true
                    state = state.copy(prompt = BatteryPrompt.NONE)
                }
                if (openSettings == null) {
                    state = state.dismiss()
                } else {
                    state = state.openSettings()
                    if (!openSettings()) {
                        state = state.copy(awaitingSettingsReturn = false)
                        error = "Couldn't open Android settings. Please try again."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save your preference. Please try again."
            } finally {
                saving = false
            }
        }
    }

    class Factory(private val context: Context, private val store: SettingsStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BatteryReliabilityViewModel(context.applicationContext, store) as T
    }
}
