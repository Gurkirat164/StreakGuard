package com.streakguard.app.ui.settings

internal enum class SettingsPermission { NOTIFICATIONS, EXACT_ALARMS, BATTERY }

/** Only missing grants belong in Settings; an empty list hides the entire module. */
internal fun missingSettingsPermissions(
    notificationsGranted: Boolean,
    exactAlarmsGranted: Boolean,
    batteryExempt: Boolean,
): List<SettingsPermission> = buildList {
    if (!notificationsGranted) add(SettingsPermission.NOTIFICATIONS)
    if (!exactAlarmsGranted) add(SettingsPermission.EXACT_ALARMS)
    if (!batteryExempt) add(SettingsPermission.BATTERY)
}
