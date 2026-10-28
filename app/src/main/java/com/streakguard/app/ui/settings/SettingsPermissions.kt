package com.streakguard.app.ui.settings

internal enum class SettingsPermission { NOTIFICATIONS, EXACT_ALARMS }

/** Granted notification/alarm permissions produce no rows; battery settings remain separate. */
internal fun missingSettingsPermissions(
    notificationsGranted: Boolean,
    exactAlarmsGranted: Boolean,
): List<SettingsPermission> = buildList {
    if (!notificationsGranted) add(SettingsPermission.NOTIFICATIONS)
    if (!exactAlarmsGranted) add(SettingsPermission.EXACT_ALARMS)
}
