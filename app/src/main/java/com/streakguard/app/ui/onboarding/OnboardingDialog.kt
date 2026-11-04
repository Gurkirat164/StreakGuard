package com.streakguard.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.theme.CardBackground
import com.streakguard.app.ui.theme.Tertiary
import com.streakguard.app.ui.theme.TextMuted
import com.streakguard.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Shown once on first launch: asks for the permissions the app needs
 * (notifications + exact alarms) up front instead of making the user hunt
 * through Settings one by one.
 */
@Composable
fun OnboardingDialog(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf("intro") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun finish() {
        step = "finishing"
        scope.launch {
            runCatching {
                container.settingsStore.setOnboardingDone(true)
            }.onSuccess {
                runCatching { container.alarmScheduler.rescheduleFromSettings() }
                runCatching { container.alarmScheduler.scheduleMidnightRefresh() }
                onDone()
            }.onFailure {
                error = "Couldn't save setup. Please try again."
                step = "intro"
            }
        }
    }

    val alarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Read actual grants later; Settings activities need not return RESULT_OK.
        finish()
    }
    fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !container.alarmScheduler.isExactAlarmAllowed()) {
            step = "alarms"
            runCatching {
                alarmLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")))
            }.onFailure { finish() }
        } else finish()
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Continue to the exact-alarm grant regardless of the answer.
        requestExactAlarms()
    }

    if (step != "intro") return
    AlertDialog(
        onDismissRequest = { /* must pick an option */ },
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextMuted,
        title = {
            Text(
                "Enable reminders",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Text(
                "StreakGuard checks your LeetCode streak every day and reminds " +
                    "you before time runs out. To do that it needs notification " +
                    "permission and exact-alarm access. After that, Android will " +
                    "ask whether to allow background use for more reliable reminders." +
                    (error?.let { "\n\n$it" } ?: ""),
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !container.notificationHelper.notificationsEnabled()) {
                        step = "notifications"
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        requestExactAlarms()
                    }
                }
            ) {
                Text("ENABLE", color = Tertiary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { finish() }) {
                Text("SKIP", color = TextMuted)
            }
        },
    )
}
