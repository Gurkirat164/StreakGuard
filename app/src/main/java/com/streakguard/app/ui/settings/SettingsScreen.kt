package com.streakguard.app.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.ui.components.MonoCaption
import com.streakguard.app.ui.theme.FigmaAccent
import com.streakguard.app.ui.theme.FigmaAmber
import com.streakguard.app.ui.theme.FigmaBackground
import com.streakguard.app.ui.theme.FigmaCard
import com.streakguard.app.ui.theme.FigmaCardInner
import com.streakguard.app.ui.theme.FigmaDangerLight
import com.streakguard.app.ui.theme.FigmaGreen
import com.streakguard.app.ui.theme.FigmaOnAccent
import com.streakguard.app.ui.theme.FigmaPill
import com.streakguard.app.ui.theme.FigmaTextMuted
import com.streakguard.app.ui.theme.FigmaTextPrimary

@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = remember { SettingsViewModel.Factory(container) })
    val context = LocalContext.current

    // --- Time picker dialog ---
    var showTimePicker by remember { mutableStateOf(false) }
    if (showTimePicker) {
        LaunchedEffect(Unit) {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    vm.onTimeChange(hour, minute)
                    showTimePicker = false
                },
                vm.checkHour,
                vm.checkMinute,
                false,
            ).apply {
                setOnCancelListener { showTimePicker = false }
                show()
            }
        }
    }

    // --- Notification permission (Android 13+) ---
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val notifLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notifGranted = granted
        }

    Column(
        modifier = Modifier
            .background(FigmaBackground)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        AppHeader()
        Spacer(Modifier.height(16.dp))

        // --- Target identity (username) ---
        SettingsCard(title = "Target Identity", caption = "LOCAL STORAGE") {
            Text(
                "Change the target public handle checked by your device. No cookies or auth tokens are transmitted.",
                color = FigmaTextMuted,
                fontSize = 13.sp,
            )
            vm.platforms.forEach { platform ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        platform.displayName,
                        color = FigmaTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Switch(
                        checked = platform.enabled,
                        onCheckedChange = { vm.onEnabledChange(platform.platformId, it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = FigmaAccent,
                            checkedThumbColor = FigmaOnAccent,
                        ),
                    )
                }
                OutlinedTextField(
                    value = platform.username,
                    onValueChange = { vm.onUsernameChange(platform.platformId, it) },
                    label = { Text("Username") },
                    prefix = { Text("@", color = FigmaAmber) },
                    singleLine = true,
                    enabled = platform.enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FigmaTextPrimary,
                        unfocusedTextColor = FigmaTextPrimary,
                        disabledTextColor = FigmaTextMuted,
                        focusedLabelColor = FigmaAmber,
                        unfocusedLabelColor = FigmaTextMuted,
                        focusedBorderColor = FigmaAccent,
                        unfocusedBorderColor = FigmaPill,
                        disabledBorderColor = FigmaCardInner,
                        cursorColor = FigmaAccent,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Check time ---
        SettingsCard(title = "Daily check time", caption = "SCHEDULE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02d:%02d".format(vm.checkHour, vm.checkMinute),
                    color = FigmaTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Change", color = FigmaAmber)
                }
            }
            Text(
                "You'll be notified at this time if today's challenge isn't done yet.",
                color = FigmaTextMuted,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- Confirm when done ---
        SettingsCard(title = "Notifications", caption = "PREFERENCES") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Confirm when done", color = FigmaTextPrimary, fontSize = 14.sp)
                    Text(
                        "Also send a notification when the challenge is already done.",
                        color = FigmaTextMuted,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = vm.confirmWhenDone,
                    onCheckedChange = { vm.onConfirmWhenDoneChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = FigmaAccent,
                        checkedThumbColor = FigmaOnAccent,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.save() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FigmaAccent,
                contentColor = FigmaOnAccent,
            ),
        ) {
            Text(
                "SAVE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
            )
        }
        vm.savedMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = FigmaGreen, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))

        // --- Permissions ---
        Text(
            "Permissions",
            color = FigmaTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (!notifGranted) {
            SettingsCard(title = "Notifications are off", caption = "PERMISSION") {
                Text(
                    "StreakGuard needs notification permission to remind you.",
                    color = FigmaTextMuted,
                    fontSize = 13.sp,
                )
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("GRANT", color = FigmaAmber, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !container.alarmScheduler.isExactAlarmAllowed()
        ) {
            SettingsCard(title = "Exact alarms not allowed", caption = "PERMISSION") {
                Text(
                    "Without this, the daily check may not fire at your exact set time.",
                    color = FigmaTextMuted,
                    fontSize = 13.sp,
                )
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) {
                    Text("ALLOW", color = FigmaAmber, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = { container.notificationHelper.sendTest() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = FigmaAmber)
            Spacer(Modifier.width(8.dp))
            Text("Send test notification", color = FigmaTextPrimary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    caption: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaCard),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonoCaption(caption)
            Text(
                title,
                color = FigmaTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            content()
        }
    }
}
