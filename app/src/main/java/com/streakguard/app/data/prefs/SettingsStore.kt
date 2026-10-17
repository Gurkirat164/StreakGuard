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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App settings backed by DataStore Preferences.
 *
 * Per-platform keys are namespaced by platform id so new platforms added to
 * the registry get their own settings automatically.
 */
class SettingsStore(private val context: Context) {
    /** Only non-identifying values may cross into diagnostics. Never expose usernames or raw preferences. */
    val diagnosticSettings = context.dataStore.data.map { prefs ->
        mapOf<String, Any?>(
            "reminders" to (prefs[booleanPreferencesKey("reminders_enabled")] ?: true),
            "haptics" to (prefs[booleanPreferencesKey("haptics_enabled")] ?: true),
            "alarms" to (prefs[booleanPreferencesKey("alarms_enabled")] ?: false),
            "sync_on_open" to (prefs[booleanPreferencesKey("sync_on_open")] ?: true),
            "sync_interval" to (prefs[intPreferencesKey("sync_interval")] ?: 15),
            "timezone" to (prefs[stringPreferencesKey("display_timezone")] ?: "UTC"),
            "use_24_hour" to (prefs[booleanPreferencesKey("use_24_hour_time")] ?: true),
            "username_configured" to !prefs[stringPreferencesKey("username_leetcode")].isNullOrBlank(),
            "profile_verified" to (prefs[stringPreferencesKey("username_leetcode")]?.let {
                it.isNotBlank() && it == prefs[stringPreferencesKey("verified_username_leetcode")]
            } ?: false),
            "platform_enabled" to (prefs[booleanPreferencesKey("enabled_leetcode")] ?: true),
            "reminder_minutes" to com.streakguard.app.schedule.ReminderSchedule.fromStoredValues(
                prefs[stringPreferencesKey("reminder_minutes")], prefs[stringPreferencesKey("reminder_hours")]).joinToString(","))
    }.distinctUntilChanged()
    fun completedDay(platformId: String, username: String): Flow<String?> =
        context.dataStore.data.map { it[stringPreferencesKey("completed_day_${platformId}_$username")] }

    suspend fun isCompleted(platformId: String = "leetcode", day: String = com.streakguard.app.util.TimeUtils.utcToday()): Boolean {
        val username = username(platformId).first()
        return com.streakguard.app.domain.CompletedDay.matches(completedDay(platformId, username).first(), day, username)
    }

    suspend fun markCompleted(platformId: String, username: String, day: String) {
        context.dataStore.edit { it[stringPreferencesKey("completed_day_${platformId}_$username")] = day }
    }

    val remindersEnabled = context.dataStore.data.map { it[booleanPreferencesKey("reminders_enabled")] ?: true }
    val hapticsEnabled = context.dataStore.data.map { it[booleanPreferencesKey("haptics_enabled")] ?: true }
    val syncOnOpen = context.dataStore.data.map { it[booleanPreferencesKey("sync_on_open")] ?: true }
    val syncInterval = context.dataStore.data.map { it[intPreferencesKey("sync_interval")] ?: 15 }
    val displayTimezone = context.dataStore.data.map { it[stringPreferencesKey("display_timezone")] ?: "UTC" }
    val use24HourTime = context.dataStore.data.map { it[booleanPreferencesKey("use_24_hour_time")] ?: true }
    val alarmsEnabled = context.dataStore.data.map { it[booleanPreferencesKey("alarms_enabled")] ?: false }
    val reminderMinutes = context.dataStore.data.map { prefs ->
        com.streakguard.app.schedule.ReminderSchedule.fromStoredValues(
            prefs[stringPreferencesKey("reminder_minutes")], prefs[stringPreferencesKey("reminder_hours")])
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
    suspend fun setUse24HourTime(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("use_24_hour_time")] = value } }
    suspend fun setAlarmsEnabled(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("alarms_enabled")] = value } }
    suspend fun setReminderMinutes(value: List<Int>) {
        require(com.streakguard.app.schedule.ReminderSchedule.valid(value))
        context.dataStore.edit {
            it[stringPreferencesKey("reminder_minutes")] = value.joinToString(",")
            it.remove(stringPreferencesKey("reminder_hours"))
        }
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

    /** Clear local preferences without replaying first-install onboarding. */
    suspend fun clearUserData() {
        context.dataStore.edit {
            it.clear()
            it[onboardingKey] = true
        }
    }
}
