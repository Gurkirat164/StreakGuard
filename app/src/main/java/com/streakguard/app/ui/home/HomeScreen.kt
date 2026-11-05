package com.streakguard.app.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.streakguard.app.R
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.ui.components.PlatformCard
import com.streakguard.app.ui.settings.LeetCodeUsernameDialog
import com.streakguard.app.ui.settings.SettingsViewModel
import com.streakguard.app.ui.theme.AppBackground
import com.streakguard.app.ui.theme.DangerLight
import com.streakguard.app.ui.theme.TextMuted
import com.streakguard.app.ui.theme.Tertiary
import com.streakguard.app.ui.theme.TextPrimary

internal enum class HomeWarning { PERMISSIONS, USERNAME }

internal fun homeWarning(notificationsGranted: Boolean, exactAlarmsGranted: Boolean, username: String?): HomeWarning? = when {
    !notificationsGranted || !exactAlarmsGranted -> HomeWarning.PERMISSIONS
    username != null && username.isBlank() -> HomeWarning.USERNAME
    else -> null
}

@Composable
fun HomeScreen(container: AppContainer, onOpenPermissions: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.streakguard.app.diagnostics.Diagnostics.log(com.streakguard.app.diagnostics.DiagnosticEvent.SCREEN_OPENED, "screen" to "today")
    }
    // Activity-scoped so refresh-on-open and this page use the same state.
    val activity = LocalActivity.current as ComponentActivity
    val vm: HomeViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = remember { HomeViewModel.Factory(container) },
    )

    var notificationsGranted by remember { mutableStateOf(container.notificationHelper.notificationsEnabled()) }
    var exactAlarmsGranted by remember { mutableStateOf(container.alarmScheduler.isExactAlarmAllowed()) }
    fun refreshPermissions() {
        notificationsGranted = container.notificationHelper.notificationsEnabled()
        exactAlarmsGranted = container.alarmScheduler.isExactAlarmAllowed()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshPermissions() }
    val permissionSetupComplete by container.settingsStore.permissionSetupComplete.collectAsState(initial = false)
    LaunchedEffect(permissionSetupComplete) { refreshPermissions() }
    val username by remember(container) { container.settingsStore.username("leetcode") }.collectAsState(initial = null)
    var editUsername by rememberSaveable { mutableStateOf(false) }
    if (editUsername) {
        val settings: SettingsViewModel = viewModel(
            viewModelStoreOwner = activity,
            factory = remember { SettingsViewModel.Factory(container) },
        )
        LeetCodeUsernameDialog(
            username = username.orEmpty(), enabled = settings.initialized && !settings.isClearing,
            saving = settings.isSavingUsername, error = settings.usernameSaveError, onEdit = settings::clearUsernameError,
            onSave = { settings.changeUsername(it) { editUsername = false } },
            onDismiss = { editUsername = false; settings.clearUsernameError() },
        )
    }

    Column(
        modifier = Modifier
            .background(AppBackground)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        AppHeader()
        Spacer(Modifier.height(16.dp))
        when (homeWarning(notificationsGranted, exactAlarmsGranted, username)) {
            HomeWarning.PERMISSIONS -> {
                ReminderPermissionWarning(notificationsGranted, exactAlarmsGranted, onOpenPermissions)
                Spacer(Modifier.height(16.dp))
            }
            HomeWarning.USERNAME -> {
                SetupWarning(
                    title = "LeetCode username needed",
                    message = "Add your public LeetCode username to track your streak and receive daily reminders.",
                    actionLabel = "Add username", icon = R.drawable.ic_settings_account_circle,
                    onAction = { editUsername = true },
                )
                Spacer(Modifier.height(16.dp))
            }
            null -> Unit
        }

        val platforms = vm.platforms
        if (platforms.isEmpty()) {
            Text(
                "Loading…",
                color = TextMuted,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        platforms.forEach { platform ->
            PlatformCard(
                platform = platform,
                isChecking = vm.isChecking,
                onRefresh = { vm.checkNow() },
            )
            Spacer(Modifier.height(16.dp))
        }

        vm.lastCheckedText?.let {
            Text(it, color = TextMuted, fontSize = 12.sp)
        }
        vm.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DangerLight, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReminderPermissionWarning(
    notificationsGranted: Boolean,
    exactAlarmsGranted: Boolean,
    onOpenPermissions: () -> Unit,
) {
    val message = when {
        !notificationsGranted && !exactAlarmsGranted ->
            "Enable notifications and exact-alarm access so StreakGuard can show reminders on time."
        !notificationsGranted -> "Notifications are off. Enable them to receive your streak reminders."
        else -> "Exact-alarm access is off. Enable it to help reminders arrive on time."
    }
    SetupWarning("Reminder permissions needed", message, "Review permissions",
        R.drawable.ic_settings_notification_important, onOpenPermissions)
}

@Composable
private fun SetupWarning(title: String, message: String, actionLabel: String, icon: Int, onAction: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF292215), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(icon), contentDescription = null,
                    tint = Tertiary, modifier = Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(message, color = Color(0xFFE6CFA7), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel, color = Tertiary)
            }
        }
    }
}
