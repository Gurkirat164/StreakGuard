package com.streakguard.app.ui.onboarding

import android.Manifest
import android.content.Context
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
import androidx.compose.runtime.rememberCoroutineScope
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

    fun finish() {
        scope.launch {
            runCatching {
                container.settingsStore.setOnboardingDone(true)
                container.alarmScheduler.rescheduleFromSettings()
                container.alarmScheduler.scheduleMidnightRefresh()
            }
            onDone()
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Continue to the exact-alarm grant regardless of the answer.
        openExactAlarmSettings(context)
        finish()
    }

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
                    "permission and exact alarms.",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openExactAlarmSettings(context)
                        finish()
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

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
