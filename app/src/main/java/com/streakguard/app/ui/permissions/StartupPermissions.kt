package com.streakguard.app.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.streakguard.app.di.AppContainer
import com.streakguard.app.diagnostics.Diagnostics

private enum class PermissionStep { NOTIFICATIONS, EXACT_ALARMS, COMPLETE }

/** Android owns each permission screen; returning advances even when access is denied. */
@Composable
internal fun StartupPermissions(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(PermissionStep.NOTIFICATIONS) }
    var awaitingResult by rememberSaveable { mutableStateOf(false) }
    val alarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        awaitingResult = false
        step = PermissionStep.COMPLETE
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        awaitingResult = false
        step = PermissionStep.EXACT_ALARMS
    }

    LaunchedEffect(step) {
        if (awaitingResult) return@LaunchedEffect
        when (step) {
            PermissionStep.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !container.notificationHelper.notificationsEnabled()) {
                    try {
                        awaitingResult = true
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (e: Exception) {
                        awaitingResult = false
                        Diagnostics.failure("notification_permission_request", e)
                        step = PermissionStep.EXACT_ALARMS
                    }
                } else step = PermissionStep.EXACT_ALARMS
            }
            PermissionStep.EXACT_ALARMS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !container.alarmScheduler.isExactAlarmAllowed()) {
                    try {
                        awaitingResult = true
                        alarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")))
                    } catch (e: Exception) {
                        awaitingResult = false
                        Diagnostics.failure("exact_alarm_permission_request", e)
                        step = PermissionStep.COMPLETE
                    }
                } else step = PermissionStep.COMPLETE
            }
            PermissionStep.COMPLETE -> {
                container.settingsStore.setPermissionSetupComplete(true)
                runCatching { container.alarmScheduler.rescheduleFromSettings() }
                runCatching { container.alarmScheduler.scheduleMidnightRefresh() }
                onDone()
            }
        }
    }
}
