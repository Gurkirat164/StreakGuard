package com.streakguard.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streakguard.app.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        viewModelScope.launch { loadShells() }
    }

    /** Load platform rows with usernames but no check results yet. */
    private suspend fun loadShells() {
        platforms = container.platformRegistry.platforms.map { platform ->
            val username = runCatching { container.settingsStore.username(platform.id).first() }
                .getOrDefault("")
            PlatformUiState(
                platformId = platform.id,
                displayName = platform.displayName,
                username = username,
                challengeTitle = null,
                challengeUrl = null,
                completed = null,
                streak = null,
            )
        }
    }

    fun checkNow() {
        if (isChecking) return
        viewModelScope.launch {
            isChecking = true
            errorMessage = null
            try {
                loadShells()
                val results = container.checkOrchestrator.runCheck(manual = true)
                platforms = results.map { result ->
                    val username = platforms
                        .firstOrNull { it.platformId == result.platformId }
                        ?.username ?: ""
                    PlatformUiState(
                        platformId = result.platformId,
                        displayName = result.displayName,
                        username = username,
                        challengeTitle = result.challengeTitle,
                        challengeUrl = result.challengeUrl,
                        completed = result.completed,
                        streak = result.streak,
                    )
                }
                lastCheckedText = "Last checked " +
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
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
