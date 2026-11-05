package com.streakguard.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeWarningTest {
    @Test fun missingPermissionsTakePriorityOverUsername() {
        for ((notifications, alarms) in listOf(false to false, false to true, true to false)) {
            for (username in listOf(null, "", "gurkirat164")) {
                assertEquals(HomeWarning.PERMISSIONS, homeWarning(notifications, alarms, username))
            }
        }
    }

    @Test fun missingUsernameAppearsAfterPermissionsAreGranted() {
        for (username in listOf("", "  ")) {
            assertEquals(HomeWarning.USERNAME, homeWarning(true, true, username))
        }
    }

    @Test fun loadedUsernameOrPendingLoadDoesNotShowUsernameWarning() {
        assertNull(homeWarning(true, true, null))
        assertNull(homeWarning(true, true, "gurkirat164"))
    }
}
