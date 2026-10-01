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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Platforms are built from the registry: new platforms appear here automatically.
            Text("Platforms", style = MaterialTheme.typography.titleMedium)
            vm.platforms.forEach { platform ->
                PlatformSettingsRow(
                    displayName = platform.displayName,
                    username = platform.username,
                    enabled = platform.enabled,
                    onUsernameChange = { vm.onUsernameChange(platform.platformId, it) },
                    onEnabledChange = { vm.onEnabledChange(platform.platformId, it) },
                )
            }

            Text("Daily check time", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "%02d:%02d".format(vm.checkHour, vm.checkMinute),
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text("Change")
                }
            }
            Text(
                "You'll be notified at this time if today's challenge isn't done yet.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Confirm when done")
                    Text(
                        "Also send a notification when the challenge is already done.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = vm.confirmWhenDone,
                    onCheckedChange = { vm.onConfirmWhenDoneChange(it) },
                )
            }

            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
            vm.savedMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Text("Permissions", style = MaterialTheme.typography.titleMedium)

            if (!notifGranted) {
                PermissionCard(
                    title = "Notifications are off",
                    body = "StreakGuard needs notification permission to remind you.",
                    buttonText = "Grant",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !container.alarmScheduler.isExactAlarmAllowed()
            ) {
                PermissionCard(
                    title = "Exact alarms not allowed",
                    body = "Without this, the daily check may not fire at your exact set time.",
                    buttonText = "Allow",
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                )
            }

            OutlinedButton(
                onClick = { container.notificationHelper.sendTest() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send test notification")
            }
        }
    }
}

@Composable
private fun PlatformSettingsRow(
    displayName: String,
    username: String,
    enabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(displayName, style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}
