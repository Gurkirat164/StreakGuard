package com.streakguard.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPermissionsTest {
    @Test fun everyGrantCombinationShowsOnlyMissingPermissions() {
        for (notifications in listOf(false, true)) {
            for (alarms in listOf(false, true)) {
                for (battery in listOf(false, true)) {
                    val missing = missingSettingsPermissions(notifications, alarms, battery)
                    assertEquals(!notifications, SettingsPermission.NOTIFICATIONS in missing)
                    assertEquals(!alarms, SettingsPermission.EXACT_ALARMS in missing)
                    assertEquals(!battery, SettingsPermission.BATTERY in missing)
                }
            }
        }
    }

    @Test fun batteryOnlyRemainsVisibleUntilGranted() {
        assertEquals(listOf(SettingsPermission.BATTERY), missingSettingsPermissions(true, true, false))
    }

    @Test fun allGrantedLeavesNoPermissionModule() {
        assertTrue(missingSettingsPermissions(true, true, true).isEmpty())
    }
}
