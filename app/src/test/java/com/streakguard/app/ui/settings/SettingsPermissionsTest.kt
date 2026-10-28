package com.streakguard.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPermissionsTest {
    @Test fun bothDeniedShowsBothActionsInDesignOrder() {
        assertEquals(listOf(SettingsPermission.NOTIFICATIONS, SettingsPermission.EXACT_ALARMS),
            missingSettingsPermissions(notificationsGranted = false, exactAlarmsGranted = false))
    }

    @Test fun notificationGrantRemovesOnlyItsRow() {
        assertEquals(listOf(SettingsPermission.EXACT_ALARMS),
            missingSettingsPermissions(notificationsGranted = true, exactAlarmsGranted = false))
    }

    @Test fun exactAlarmGrantRemovesOnlyItsRow() {
        assertEquals(listOf(SettingsPermission.NOTIFICATIONS),
            missingSettingsPermissions(notificationsGranted = false, exactAlarmsGranted = true))
    }

    @Test fun bothGrantedLeavesNoCardContent() {
        assertTrue(missingSettingsPermissions(notificationsGranted = true, exactAlarmsGranted = true).isEmpty())
    }
}
