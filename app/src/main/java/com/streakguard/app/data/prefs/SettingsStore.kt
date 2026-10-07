package com.streakguard.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App settings backed by DataStore Preferences.
 *
 * Per-platform keys are namespaced by platform id so new platforms added to
 * the registry get their own settings automatically.
 */
class SettingsStore(private val context: Context) {

    val remindersEnabled = context.dataStore.data.map { it[booleanPreferencesKey("reminders_enabled")] ?: true }
    val hapticsEnabled = context.dataStore.data.map { it[booleanPreferencesKey("haptics_enabled")] ?: true }
    val syncOnOpen = context.dataStore.data.map { it[booleanPreferencesKey("sync_on_open")] ?: true }
    val syncInterval = context.dataStore.data.map { it[intPreferencesKey("sync_interval")] ?: 15 }
    val displayTimezone = context.dataStore.data.map { it[stringPreferencesKey("display_timezone")] ?: "UTC" }
    val reminderHours = context.dataStore.data.map { prefs ->
        val hours = prefs[stringPreferencesKey("reminder_hours")]?.split(",")?.mapNotNull { it.toIntOrNull() }
        hours?.takeIf { com.streakguard.app.schedule.ReminderSchedule.valid(it) }
            ?: com.streakguard.app.schedule.ReminderSchedule.defaultHours
    }

    suspend fun setRemindersEnabled(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("reminders_enabled")] = value } }
    suspend fun setHapticsEnabled(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("haptics_enabled")] = value } }
    suspend fun setSyncOnOpen(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("sync_on_open")] = value } }
    suspend fun setSyncInterval(value: Int) {
        require(value in listOf(15, 30, 60))
        context.dataStore.edit { it[intPreferencesKey("sync_interval")] = value }
    }
    suspend fun setDisplayTimezone(value: String) {
        if (value != "DEVICE") java.time.ZoneId.of(value)
        context.dataStore.edit { it[stringPreferencesKey("display_timezone")] = value }
    }
    suspend fun setReminderHours(value: List<Int>) {
        require(com.streakguard.app.schedule.ReminderSchedule.valid(value))
        context.dataStore.edit { it[stringPreferencesKey("reminder_hours")] = value.joinToString(",") }
    }
    fun verifiedUsername(platformId: String) = context.dataStore.data.map {
        it[stringPreferencesKey("verified_username_$platformId")] ?: ""
    }
    fun checkedUsername(platformId: String) = context.dataStore.data.map {
        it[stringPreferencesKey("checked_username_$platformId")] ?: ""
    }
    suspend fun setCheckedUsername(platformId: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey("checked_username_$platformId")] = value }
    }
    suspend fun setVerifiedUsername(platformId: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey("verified_username_$platformId")] = value }
    }

    // --- Per-platform username ---

    fun username(platformId: String): Flow<String> =
        context.dataStore.data.map { it[stringPreferencesKey("username_$platformId")] ?: "" }

    suspend fun setUsername(platformId: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey("username_$platformId")] = value }
    }

    // --- Daily check time (24h) ---

    val checkHour: Flow<Int> =
        context.dataStore.data.map { it[intPreferencesKey("check_hour")] ?: 21 }

    val checkMinute: Flow<Int> =
        context.dataStore.data.map { it[intPreferencesKey("check_minute")] ?: 0 }

    suspend fun setCheckTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[intPreferencesKey("check_hour")] = hour
            it[intPreferencesKey("check_minute")] = minute
        }
    }

    suspend fun getCheckHour(): Int = checkHour.first()

    suspend fun getCheckMinute(): Int = checkMinute.first()

    // --- Per-platform enabled toggle ---

    fun enabled(platformId: String): Flow<Boolean> =
        context.dataStore.data.map { it[booleanPreferencesKey("enabled_$platformId")] ?: true }

    suspend fun setEnabled(platformId: String, value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("enabled_$platformId")] = value }
    }

    // --- First-launch onboarding ---

    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[onboardingKey] ?: false }

    suspend fun setOnboardingDone(value: Boolean) {
        context.dataStore.edit { it[onboardingKey] = value }
    }

    // --- Optional "streak safe" confirmation when the challenge is done ---

    val confirmWhenDone: Flow<Boolean> =
        context.dataStore.data.map { it[booleanPreferencesKey("confirm_when_done")] ?: false }

    suspend fun setConfirmWhenDone(value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("confirm_when_done")] = value }
    }
}
