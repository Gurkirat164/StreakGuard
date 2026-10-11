package com.streakguard.app.ui.settings

internal enum class SettingsPermission { NOTIFICATIONS, EXACT_ALARMS }

/** Granted permissions never produce a row; an empty list hides the whole card. */
internal fun missingSettingsPermissions(
    notificationsGranted: Boolean,
    exactAlarmsGranted: Boolean,
): List<SettingsPermission> = buildList {
    if (!notificationsGranted) add(SettingsPermission.NOTIFICATIONS)
    if (!exactAlarmsGranted) add(SettingsPermission.EXACT_ALARMS)
}
