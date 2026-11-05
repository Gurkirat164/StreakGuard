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
import kotlinx.coroutines.flow.combine
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
    private var requestAttempted = false
    private var preferencesLoaded = false
    private var foreground = false
    private var ready = false

    init {
        viewModelScope.launch {
            combine(store.batteryPromptsSuppressed, store.batteryRequestAttempted) { suppression, attempted ->
                suppression to attempted
            }.collect { (suppression, attempted) ->
                suppressed = suppression
                requestAttempted = attempted
                val firstLoad = !preferencesLoaded
                preferencesLoaded = true
                if (suppression) state = state.copy(prompt = BatteryPrompt.NONE)
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
        if (!state.visitActive && !state.awaitingRequestReturn) {
            dontShowAgain = false
            error = null
        }
        state = state.foreground(status, suppressed, requestAttempted)
    }

    fun onBackground() { foreground = false; state = state.background() }

    fun awaitOtherPermissionReturn() { state = state.request() }

    fun dismiss() = act(launch = null)

    fun request(launch: () -> Boolean) = act(launch = launch)

    private fun act(launch: (() -> Boolean)?) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            error = null
            try {
                // Manual requests from Settings never change automatic-prompt preferences.
                if (state.prompt == BatteryPrompt.REMINDER && dontShowAgain) {
                    store.setBatteryPromptsSuppressed(true)
                    suppressed = true
                    state = state.copy(prompt = BatteryPrompt.NONE)
                }
                if (launch == null) {
                    state = state.dismiss()
                } else {
                    // Persist before opening Android so denying cannot repeat the first-run request.
                    store.markBatteryRequestAttempted()
                    requestAttempted = true
                    state = state.request()
                    if (!launch()) {
                        state = state.copy(prompt = if (suppressed) BatteryPrompt.NONE else BatteryPrompt.REMINDER,
                            awaitingRequestReturn = false)
                        error = "Couldn't open Android's battery request. Try again from Settings → Permissions."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (state.prompt == BatteryPrompt.NATIVE_REQUEST) {
                    state = state.copy(prompt = BatteryPrompt.REMINDER)
                }
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
