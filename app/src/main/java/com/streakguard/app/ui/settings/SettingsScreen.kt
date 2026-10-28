package com.streakguard.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.R
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.ui.theme.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://gurkirat164.github.io/StreakGuard/Policies/"
// Settings V2 palette and typography from the approved Figma design.
internal val SettingsCanvas = Color(0xFF0D1320)
internal val SettingsCard = Color(0xFF121826)
internal val SettingsButton = Color(0xFF1E273B)
internal val SettingsInset = Color(0xFF192033)
internal val SettingsText = Color(0xFFF1F5F9)
internal val SettingsMuted = Color(0xFF94A3B8)
internal val SettingsDescription = Color(0xFFAB8980)
internal val SettingsThumb = Color(0xFF3B0900)
internal val SettingsBorder = Color(0xFF1E263A)
internal val SettingsDivider = Color(0xFF1A2235)
internal val SettingsTelemetry = Color(0xFF8A99AD)
internal val SettingsInfo = Color(0xFF6C7D93)
internal val SettingsAmber = Color(0xFFFBBF24)
internal val SettingsUrgent = Color(0xFFFF3D00)
internal val SettingsSafe = Color(0xFF4EDEA3)
internal val CountdownFont = FontFamily(
    Font(R.font.settings_liberation_mono, FontWeight.Normal),
    Font(R.font.settings_liberation_mono_bold, FontWeight.Bold))
private val CountdownDescriptionFont = FontFamily(Font(R.font.settings_nimbus_sans))
internal val Sunken = Color(0xFF0B101C)
internal val Critical = Color(0xFFEF4444)
private val PermissionDescriptionFont = FontFamily(Font(R.font.settings_roboto_mono))
internal val SettingsCardFont = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.settings_geist_bold, FontWeight.Bold),
)

@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = remember { SettingsViewModel.Factory(container) })
    val context = LocalContext.current
    val activity = LocalActivity.current
    val battery = com.streakguard.app.ui.battery.LocalBatterySettings.current
    var editUsername by remember { mutableStateOf(false) }
    var editTiers by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    val debugAccess by container.diagnostics.availability.collectAsState()
    LaunchedEffect(Unit) { com.streakguard.app.diagnostics.Diagnostics.log(com.streakguard.app.diagnostics.DiagnosticEvent.SCREEN_OPENED,
        "screen" to "settings") }
    if (showDebug && debugAccess.visible) DebugDialog(container.diagnostics, onDismiss = { showDebug = false })
    var zoneExpanded by remember { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(container.notificationHelper.notificationsEnabled()) }
    var exactGranted by remember { mutableStateOf(container.alarmScheduler.isExactAlarmAllowed()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                container.diagnostics.log(com.streakguard.app.diagnostics.DiagnosticEvent.PERMISSIONS_SNAPSHOT,
                    container.diagnostics.permissionFields())
                notifGranted = container.notificationHelper.notificationsEnabled()
                exactGranted = container.alarmScheduler.isExactAlarmAllowed()
                scope.launch { runCatching { container.alarmScheduler.rescheduleFromSettings() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = container.notificationHelper.notificationsEnabled()
        // Android stops showing the permission dialog after a permanent denial.
        // Keep Grant useful by taking the user to the app's notification settings.
        if (!granted && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    fun openAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")))
        }
    }

    if (editUsername) {
        var handle by remember { mutableStateOf(vm.username) }
        AlertDialog(
            onDismissRequest = { editUsername = false },
            title = { Text("LeetCode username") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use your public handle. No password or cookies are needed.")
                OutlinedTextField(handle, { handle = it }, label = { Text("Username") },
                    prefix = { Text("@") }, singleLine = true)
            } },
            confirmButton = { TextButton(enabled = handle.trim().removePrefix("@").isNotBlank() && !handle.trim().any { it.isWhitespace() },
                onClick = { vm.changeUsername(handle); editUsername = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editUsername = false }) { Text("Cancel") } },
        )
    }
    if (editTiers) {
        NotificationTimesDialog(
            initialMinutes = vm.reminderMinutes,
            zone = vm.zone,
            use24HourTime = vm.use24HourTime,
            saving = vm.isSavingTimes,
            errorMessage = vm.message,
            onDismiss = { if (!vm.isSavingTimes) editTiers = false },
            onSave = { times -> vm.saveReminderTimes(times) { editTiers = false } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all your data?") },
            text = { Text("This clears your saved username, settings, and local challenge history. Your LeetCode account is unaffected.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearData() }) {
                Text("Delete", color = Critical)
            } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val zone = vm.zone
    val now = Instant.now()
    val offset = zone.rules.getOffset(now).id.replace("Z", "+00:00")
    val zoneLabel = when (vm.displayTimezone) {
        "UTC" -> "UTC ($offset)"
        "DEVICE" -> "Device ($offset)"
        "Asia/Kolkata" -> "IST ($offset)"
        else -> zone.id.substringAfterLast('/').replace('_', ' ') + " ($offset)"
    }
    val timeSuffix = when (vm.displayTimezone) {
        "UTC" -> "UTC"
        "Asia/Kolkata" -> "IST"
        else -> DateTimeFormatter.ofPattern("z").withZone(zone).format(now)
    }
    Column(Modifier.fillMaxSize().background(SettingsCanvas).verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(0.dp))
        AppHeader()
        SettingsModule("LeetCode Connection", R.drawable.ic_settings_code) {
            Row(Modifier.fillMaxWidth().background(Sunken, RoundedCornerShape(12.dp))
                .border(1.dp, SettingsInset, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(SettingsInset, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = .04f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                    DesignIcon(R.drawable.ic_settings_account_circle, SettingsTelemetry, 24)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Mono(if (vm.username.isBlank()) "Add your username" else "@${vm.username}", SettingsText, 13,
                        weight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (vm.profileVerified) DesignIcon(R.drawable.ic_settings_check_circle, Secondary, 14)
                        Text(when { vm.isTesting -> "Verifying public profile…"; vm.profileVerified -> "Public Profile Synced";
                            vm.username.isBlank() -> "No profile connected"; else -> "Profile not verified" },
                            color = if (vm.profileVerified) Secondary else SettingsMuted, fontSize = 11.sp,
                            lineHeight = 16.sp, letterSpacing = 0.sp)
                    }
                }
                CompactButton("Change", vm.initialized && !vm.isTesting) { editUsername = true }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(Sunken, RoundedCornerShape(8.dp))
                .border(1.dp, SettingsInset, RoundedCornerShape(8.dp))
                .clickable(enabled = vm.initialized && vm.username.isNotBlank() && !vm.isTesting) { vm.testConnection() }
                .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesignIcon(R.drawable.ic_settings_wifi, Secondary, 20)
                Text(if (vm.isTesting) "Testing connection…" else "Test Connection" + (vm.connectionLatency?.let { " (${it}ms)" } ?: ""),
                    color = SettingsText, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp, modifier = Modifier.weight(1f))
                if (vm.isTesting) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Secondary)
                else Mono(when (vm.connectionOk) { true -> "OK"; false -> "FAIL"; null -> "TEST" },
                    if (vm.connectionOk == false) Critical else Secondary, 11, weight = FontWeight.SemiBold)
            }
        }
        SettingsModule("Streak Reminders", R.drawable.ic_settings_notifications_active) {
            ToggleRow("Alert Dispatcher", "Reminds you before your streak resets.", vm.remindersEnabled,
                { vm.setReminders(it); if (it && !notifGranted) openNotificationSettings() }, vm.initialized)
            Spacer(Modifier.height(28.dp))
            DesignDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Mono("COUNTDOWN ALERT TIERS", SettingsDescription, 9, Modifier.weight(1f), FontWeight.SemiBold,
                    letterSpacing = .45f)
                Mono("RESET: 00:00 UTC", SettingsAmber, 9)
                Spacer(Modifier.width(8.dp))
                CompactButton("Edit", vm.initialized, icon = R.drawable.ic_settings_tune, small = true) { editTiers = true }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val titles = listOf("Passive ping", "Warning nudge", "Urgent buzzer")
                val badges = listOf("INFO", "ALERT", "CRITICAL")
                val colors = listOf(SettingsMuted, SettingsAmber, SettingsUrgent)
                vm.reminderMinutes.forEachIndexed { tier, minutes ->
                    val instant = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC)
                        .minusMinutes(minutes.toLong()).toInstant()
                    val time = com.streakguard.app.util.ClockFormat.format(instant.atZone(zone).toLocalTime(), vm.use24HourTime)
                    Row(Modifier.fillMaxWidth().background(Sunken, RoundedCornerShape(8.dp))
                        .border(1.dp, SettingsInset, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = colors[tier], fontWeight = FontWeight.Bold)) { append(time) }
                            withStyle(SpanStyle(color = SettingsMuted, fontWeight = FontWeight.Normal)) {
                                append(" $timeSuffix")
                            }
                        }, fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, lineHeight = 18.sp)
                        Text(titles[tier], color = Color.White, fontFamily = CountdownDescriptionFont,
                            fontSize = 13.sp, lineHeight = 19.5.sp, letterSpacing = 0.sp, modifier = Modifier.weight(1f))
                        Mono(badges[tier], if (tier == 0) SettingsInfo else colors[tier], 11,
                            weight = FontWeight.Bold, letterSpacing = .6f, family = CountdownFont)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DesignIcon(R.drawable.ic_settings_schedule, SettingsText, 14)
                Spacer(Modifier.width(4.dp))
                Mono("Display Timezone:", SettingsText, 10, Modifier.weight(1f))
                Box {
                    CompactButton(zoneLabel, vm.initialized, trailingIcon = R.drawable.ic_settings_expand_more,
                        small = true) { zoneExpanded = true }
                    DropdownMenu(expanded = zoneExpanded, onDismissRequest = { zoneExpanded = false }) {
                        listOf("UTC" to "UTC (+00:00)", "DEVICE" to "Device timezone", "Asia/Kolkata" to "India (IST)",
                            "America/New_York" to "New York", "America/Los_Angeles" to "Los Angeles",
                            "Europe/London" to "London", "Europe/Berlin" to "Berlin", "Asia/Tokyo" to "Tokyo")
                            .forEach { (id, label) -> DropdownMenuItem(text = { Text(label) }, onClick = {
                                vm.setTimezone(id); zoneExpanded = false
                            }) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DesignDivider()
            Spacer(Modifier.height(12.dp))
            ToggleRow("Vibration Pulse", "Vibrate for all notifications",
                vm.hapticsEnabled, vm::setHaptics, vm.initialized)
        }
        SettingsModule("Alarm Settings", R.drawable.ic_settings_clock_bold,
            headerBadge = {
                Mono(if (vm.alarmsEnabled && com.streakguard.app.notify.AlarmFeature.ENABLED) "ON" else "OFF",
                    SettingsMuted, 10, Modifier.background(SettingsButton, RoundedCornerShape(5.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp))
            }) {
            DesignDivider()
            Spacer(Modifier.height(16.dp))
            ToggleRow("Ringing alarms", "Play sound for the final reminder.",
                vm.alarmsEnabled && com.streakguard.app.notify.AlarmFeature.ENABLED, vm::setAlarms,
                vm.initialized && com.streakguard.app.notify.AlarmFeature.ENABLED)
            if (!com.streakguard.app.notify.AlarmFeature.ENABLED) {
                Spacer(Modifier.height(8.dp))
                Text("Ringing alarms are not available yet.", color = SettingsMuted,
                    fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        SettingsModule("Sync Interval", R.drawable.ic_settings_sync, "Every ${vm.syncInterval} min") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    val selected = vm.syncInterval == minutes
                    Box(Modifier.weight(1f).height(30.dp)
                        .background(if (selected) Primary else Color(0xFF080E1B), RoundedCornerShape(4.dp))
                        .selectable(selected, enabled = vm.initialized, role = Role.RadioButton) { vm.setInterval(minutes) },
                        contentAlignment = Alignment.Center) {
                        Mono("${minutes}m", if (selected) SettingsThumb else SettingsMuted, 11,
                            weight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            DesignDivider()
            Spacer(Modifier.height(8.dp))
            ToggleRow("Sync when app opens", "Trigger instant Sync", vm.syncOnOpen, vm::onSyncOnOpenChange, vm.initialized)
        }
        val missingPermissions = missingSettingsPermissions(notifGranted, exactGranted)
        // Battery settings remain reachable even after all grants or permanent prompt suppression.
        run {
            SettingsModule("Permissions", R.drawable.ic_settings_shield, contentSpacing = 20.5.dp,
                headerFont = SettingsCardFont, headerBadge = {
                    if (missingPermissions.isNotEmpty() || battery.status.restricted) PermissionActionBadge()
                }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    missingPermissions.forEach { permission ->
                        when (permission) {
                            SettingsPermission.NOTIFICATIONS -> SettingsActionRow(
                                "Notifications", "Daily reminders", R.drawable.ic_settings_notification_important,
                                Primary, "Grant", primaryAction = true, status = "NOT GRANTED", statusColor = Color(0xFFF87171),
                                descriptionFont = PermissionDescriptionFont, onClick = ::openNotificationSettings)
                            SettingsPermission.EXACT_ALARMS -> SettingsActionRow(
                                "Exact alarms", "On-time reminders", R.drawable.ic_settings_alarm_on,
                                SettingsAmber, "Enable", primaryAction = true, status = "DISABLED", statusColor = SettingsAmber,
                                descriptionFont = PermissionDescriptionFont, onClick = ::openAlarmSettings)
                        }
                    }
                    BatteryPermissionCard(battery.status.restricted, battery.openSettings)
                    battery.error?.let { Text(it, color = Tertiary, fontSize = 12.sp) }
                }
            }
        }
        SettingsModule("Others", R.drawable.ic_settings_tune, contentSpacing = 14.dp, headerFont = SettingsCardFont) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeFormatRow(vm.use24HourTime, vm.initialized && !vm.isClearing, vm::setTimeFormat)
                SettingsActionRow("Send test notification", "Trigger instant test ping to verify", R.drawable.ic_settings_send,
                    SettingsMuted, "Test", enabled = notifGranted && vm.initialized,
                    onClick = { container.notificationHelper.sendTest(vm.hapticsEnabled) })
                SettingsActionRow("Delete all your data", "Clear local cache, stored", R.drawable.ic_settings_delete,
                    Color(0xFFF87171), if (vm.isClearing) "Clearing…" else "Clear", destructive = true,
                    enabled = vm.initialized && !vm.isClearing && !vm.isTesting, onClick = { confirmClear = true })
            }
        }
        vm.message?.let { Text(it, color = Tertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("App version v$version", color = SettingsMuted, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }) {
                Text("Privacy Policy", color = SettingsMuted, fontFamily = GeistFontFamily, fontSize = 13.sp,
                    letterSpacing = 0.sp)
            }
            if (debugAccess.visible) TextButton(onClick = { showDebug = true }) {
                Text("Debug", color = SettingsMuted, fontFamily = GeistFontFamily, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsModule(title: String, icon: Int, caption: String? = null,
                           contentSpacing: Dp = 16.dp, headerBadge: (@Composable () -> Unit)? = null,
                           headerFont: FontFamily = GeistFontFamily,
                           content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (icon == R.drawable.ic_settings_sync) SettingsButton else SettingsBorder),
        colors = CardDefaults.cardColors(containerColor = SettingsCard)) {
        Column(Modifier.padding(21.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesignIcon(icon, Primary, if (icon == R.drawable.ic_settings_notifications_active) 24 else 20)
                Text(title, color = Color.White, fontFamily = headerFont, fontSize = 17.sp, lineHeight = 25.5.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-.17).sp, modifier = Modifier.weight(1f))
                caption?.let { Mono(it, SettingsSafe, 12) }
                headerBadge?.invoke()
            }
            Spacer(Modifier.height(contentSpacing))
            content()
        }
    }
}

@Composable
private fun PermissionActionBadge() {
    Box(Modifier.background(Critical.copy(alpha = .15f), RoundedCornerShape(999.dp))
        .border(1.dp, Primary.copy(alpha = .3f), RoundedCornerShape(999.dp))
        .padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text("ACTION REQUIRED", color = Primary, fontFamily = CountdownFont, fontSize = 11.sp,
            lineHeight = 16.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .55.sp,
            maxLines = 1, softWrap = false)
    }
}

/** Approved battery card 2: compact action, status below the title, warning below the row. */
@Composable
private fun BatteryPermissionCard(restricted: Boolean, onOpenSettings: () -> Unit) {
    val color = if (restricted) SettingsAmber else SettingsSafe
    Column(Modifier.fillMaxWidth().heightIn(min = 112.dp)
        .background(Sunken, RoundedCornerShape(12.dp))
        .border(1.dp, SettingsInset, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = .1f), RoundedCornerShape(12.dp))
                .border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                DesignIcon(R.drawable.ic_settings_battery, color, 24)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Battery optimisation", color = Color.White, fontFamily = SettingsCardFont,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-.35).sp)
                Text(if (restricted) "RESTRICTED" else "UNRESTRICTED", color = color,
                    fontFamily = CountdownFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 15.sp,
                    modifier = Modifier.background(color.copy(alpha = .15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp))
            }
            Box(Modifier.background(Primary, RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text("Settings", color = SettingsThumb, fontFamily = SettingsCardFont,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = (-.12).sp)
            }
        }
        Text(if (restricted) "Battery restrictions may delay\nor stop reminders." else "Unrestricted background usage is allowed.",
            color = SettingsTelemetry, fontFamily = PermissionDescriptionFont, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SettingsActionRow(
    title: String, description: String, icon: Int, iconColor: Color, action: String,
    primaryAction: Boolean = false, destructive: Boolean = false, enabled: Boolean = true,
    status: String? = null, statusColor: Color = iconColor,
    descriptionFont: FontFamily = GeistFontFamily, onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp)
        .background(Sunken, RoundedCornerShape(12.dp))
        .border(1.dp, SettingsInset, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val iconFill = when { primaryAction -> iconColor.copy(alpha = .1f)
            destructive -> Critical.copy(alpha = .1f); else -> Color(0xFF141B2C) }
        val iconBorder = when { primaryAction -> iconColor.copy(alpha = .25f)
            destructive -> Critical.copy(alpha = .25f); else -> Color(0xFF1A2235) }
        Box(Modifier.size(40.dp).background(iconFill, RoundedCornerShape(12.dp))
            .border(1.dp, iconBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            DesignIcon(icon, iconColor, 24)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontFamily = SettingsCardFont, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = (-.35).sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                status?.let {
                    Text(it, color = statusColor, fontFamily = CountdownFont, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.sp,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.background(
                            (if (statusColor == SettingsAmber) SettingsAmber else Critical).copy(alpha = .15f),
                            RoundedCornerShape(2.dp)).padding(horizontal = 5.dp))
                }
            }
            Text(description, color = SettingsTelemetry, fontFamily = descriptionFont, fontSize = 11.sp,
                lineHeight = 15.1.sp, letterSpacing = 0.sp, maxLines = 1, softWrap = false,
                overflow = TextOverflow.Ellipsis, style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        Spacer(Modifier.width(10.dp))
        val buttonShape = RoundedCornerShape(8.dp)
        val buttonColor = when { primaryAction -> Primary; destructive -> Critical.copy(alpha = .15f); else -> SettingsButton }
        Row(Modifier.background(buttonColor.copy(alpha = if (enabled) buttonColor.alpha else buttonColor.alpha * .5f), buttonShape)
            .then(if (primaryAction) Modifier else Modifier.border(1.dp,
                if (destructive) Critical.copy(alpha = .35f) else Color(0xFF242F47), buttonShape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action, color = (when { primaryAction -> SettingsThumb; destructive -> Color(0xFFF87171); else -> SettingsMuted })
                .copy(alpha = if (enabled) 1f else .5f), fontFamily = SettingsCardFont,
                fontWeight = if (primaryAction) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = (-.12).sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun TimeFormatRow(use24Hour: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 70.dp)
        .background(Sunken, RoundedCornerShape(12.dp))
        .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(Color(0xFF151D2E), RoundedCornerShape(12.dp))
            .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            DesignIcon(R.drawable.ic_settings_schedule, SettingsMuted, 24)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Time format", color = Color.White, fontFamily = SettingsCardFont,
                fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text("Clock display style", color = SettingsMuted, fontFamily = SettingsCardFont,
                fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.background(SettingsButton, RoundedCornerShape(7.dp))
            .border(1.dp, Color(0xFF263049), RoundedCornerShape(7.dp)).padding(2.dp)
            .semantics { contentDescription = "Time format" }) {
            listOf(false to "12 hr", true to "24 hr").forEach { (value, label) ->
                Box(Modifier.size(43.dp, 22.dp)
                    .background(if (use24Hour == value) Primary else Color.Transparent, RoundedCornerShape(5.dp))
                    .selectable(selected = use24Hour == value, enabled = enabled,
                        role = Role.RadioButton, onClick = { onChange(value) }),
                    contentAlignment = Alignment.Center) {
                    Mono(label, if (use24Hour == value) SettingsThumb else SettingsMuted, 10,
                        weight = if (use24Hour == value) FontWeight.Bold else FontWeight.Normal,
                        family = CountdownFont)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "Settings switch")
    Row(Modifier.fillMaxWidth().heightIn(min = 42.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, lineHeight = 22.5.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
            Mono(description, SettingsDescription, 11)
        }
        Box(Modifier.size(44.dp, 24.dp).background(
            if (checked) Primary else SettingsButton, RoundedCornerShape(12.dp))) {
            Box(Modifier.padding(start = thumbOffset, top = 2.dp).size(20.dp)
                .background(if (checked) SettingsThumb else SettingsMuted, RoundedCornerShape(10.dp)))
        }
    }
}

@Composable
private fun CompactButton(label: String, enabled: Boolean = true, icon: Int? = null, trailingIcon: Int? = null,
                          small: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.background(SettingsButton, RoundedCornerShape(if (small) 4.dp else 8.dp))
        .border(1.dp, Color(0xFF242F47), RoundedCornerShape(if (small) 4.dp else 8.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = if (small) 8.dp else 16.dp, vertical = if (small) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.let { DesignIcon(it, SettingsText, 12) }
        Text(label, color = SettingsText, fontSize = if (small) 11.sp else 12.sp,
            lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
        trailingIcon?.let { DesignIcon(it, SettingsText, 12) }
    }
}

@Composable
private fun DesignIcon(resource: Int, color: Color, size: Int) {
    Icon(painterResource(resource), contentDescription = null, tint = color, modifier = Modifier.size(size.dp))
}

@Composable
private fun DesignDivider() {
    HorizontalDivider(color = SettingsDivider, thickness = 1.dp)
}

@Composable
private fun Mono(text: String, color: Color, size: Int, modifier: Modifier = Modifier,
                 weight: FontWeight = FontWeight.Medium, letterSpacing: Float = 0f,
                 family: FontFamily = JetBrainsMonoFontFamily) {
    Text(text, color = color, fontFamily = family, fontSize = size.sp,
        fontWeight = weight, lineHeight = (size * 1.5f).sp, letterSpacing = letterSpacing.sp, modifier = modifier)
}
