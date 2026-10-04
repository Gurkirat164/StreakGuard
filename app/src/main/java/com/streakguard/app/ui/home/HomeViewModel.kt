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
    val difficulty: String?,
    val questionNumber: String?,
    /** True when today's question details were never fetched. */
    val questionMissing: Boolean,
    /** True when we know the question but its solved-status is unknown. */
    val statusUnknown: Boolean,
)

/**
 * Plain ViewModel (no DI framework): takes the [AppContainer] via a simple factory.
 *
 * On launch it shows the last persisted check for today's UTC platform day —
 * no network call. If today's data is missing or stale (e.g. right after the
 * UTC-day rollover) it triggers one automatic background refresh.
 *
 * The streak count is sticky: it falls back to the most recent known value
 * across all days and is only replaced when a newer value is fetched.
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
    /** True once the initial cache load finished (drives the splash screen). */
    var initialized by mutableStateOf(false)
        private set

    private var autoRefreshDone = false

    init {
        viewModelScope.launch {
            loadFromCache()
            initialized = true
            maybeAutoRefresh()
        }
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
            // Streak stays cached until a newer value arrives.
            val latest = runCatching {
                container.database.checkLogDao().getLatest(platform.id)
            }.getOrNull()
            cached?.checkedAtEpoch?.let { epoch ->
                if (latestEpoch == null || epoch > latestEpoch!!) latestEpoch = epoch
            }
            val known = cached?.known == true
            PlatformUiState(
                platformId = platform.id,
                displayName = platform.displayName,
                username = username,
                challengeTitle = cached?.challengeTitle,
                challengeUrl = cached?.challengeUrl,
                completed = if (known) cached!!.completed else null,
                streak = cached?.streak ?: latest?.streak,
                difficulty = cached?.difficulty,
                questionNumber = cached?.questionNumber,
                questionMissing = cached?.challengeTitle.isNullOrBlank(),
                statusUnknown = !known,
            )
        }
        lastCheckedText = latestEpoch?.let { "Last checked " + TimeUtils.formatLocalTime(it) }
    }

    /**
     * After a UTC-day rollover (or a never-completed first load), fetch once
     * automatically so the card doesn't sit on stale data. Runs at most once
     * per ViewModel lifetime.
     */
    private fun maybeAutoRefresh() {
        if (autoRefreshDone || isChecking) return
        val needsRefresh = platforms.any { platform ->
            platform.username.isNotBlank() &&
                (platform.questionMissing || platform.statusUnknown)
        }
        if (!needsRefresh) return
        autoRefreshDone = true
        checkNow()
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
