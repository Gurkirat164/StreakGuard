package com.streakguard.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streakguard.app.di.AppContainer
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlatformUiState(
    val platformId: String,
    val displayName: String,
    val username: String,
    val challengeTitle: String?,
    val challengeUrl: String?,
    /** null = unknown */
    val completed: Boolean?,
    val streak: Int?,
)

/**
 * Plain ViewModel (no DI framework): takes the [AppContainer] via a simple factory.
 *
 * On launch it shows the last persisted check for today's UTC platform day —
 * no network call. A fresh check only happens via [checkNow] (manual) or the
 * scheduled daily alarm, which is what keeps the data from resetting between
 * app restarts.
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    var platforms by mutableStateOf<List<PlatformUiState>>(emptyList())
        private set
    var isChecking by mutableStateOf(false)
        private set
    var lastCheckedText by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { loadFromCache() }
    }

    /** Load the persisted check for today's UTC day; no network involved. */
    private suspend fun loadFromCache() {
        val todayUtc = TimeUtils.utcToday()
        var latestEpoch: Long? = null
        platforms = container.platformRegistry.platforms.map { platform ->
            val username = runCatching { container.settingsStore.username(platform.id).first() }
                .getOrDefault("")
            val cached = runCatching {
                container.database.checkLogDao().getForDate(platform.id, todayUtc)
            }.getOrNull()
            cached?.checkedAtEpoch?.let { epoch ->
                if (latestEpoch == null || epoch > latestEpoch!!) latestEpoch = epoch
            }
            PlatformUiState(
                platformId = platform.id,
                displayName = platform.displayName,
                username = username,
                challengeTitle = cached?.challengeTitle,
                challengeUrl = cached?.challengeUrl,
                completed = cached?.let { if (it.known) it.completed else null },
                streak = cached?.streak,
            )
        }
        lastCheckedText = latestEpoch?.let { "Last checked " + TimeUtils.formatLocalTime(it) }
    }

    fun checkNow() {
        if (isChecking) return
        viewModelScope.launch {
            isChecking = true
            errorMessage = null
            try {
                val results = container.checkOrchestrator.runCheck(manual = true)
                // Re-read what was just persisted: single source of truth.
                loadFromCache()
                when {
                    results.isEmpty() ->
                        errorMessage = "Add your username in Settings to start checking."
                    results.all { it.completed == null } ->
                        errorMessage = "Couldn't reach the platform. Check your connection and username."
                }
            } catch (e: Exception) {
                errorMessage = "Check failed: ${e.message}"
            } finally {
                isChecking = false
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(container) as T
    }
}
