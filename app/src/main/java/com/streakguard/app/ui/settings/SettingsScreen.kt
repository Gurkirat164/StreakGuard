package com.streakguard.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://gurkirat164.github.io/StreakGuard/Policies/"
// Values inspected in the approved Figma Old Screen, scoped to Settings.
private val SettingsCanvas = Color(0xFF0D1320)
private val SettingsCard = Color(0xFF121826)
private val SettingsButton = Color(0xFF1E273B)
private val SettingsInset = Color(0xFF192033)
private val SettingsText = Color(0xFFF1F5F9)
private val SettingsMuted = Color(0xFF94A3B8)
private val SettingsDescription = Color(0xFFAB8980)
private val SettingsThumb = Color(0xFF3B0900)
private val SettingsBorder = Color(0xFF1E263A)
private val SettingsDivider = Color(0xFF1A2235)
private val SettingsTelemetry = Color(0xFF8A99AD)
private val SettingsInfo = Color(0xFF6C7D93)
private val SettingsAmber = Color(0xFFFBBF24)
private val SettingsUrgent = Color(0xFFFF3D00)
private val SettingsSafe = Color(0xFF4EDEA3)
private val CountdownFont = FontFamily(
    Font(R.font.settings_liberation_mono, FontWeight.Normal),
    Font(R.font.settings_liberation_mono_bold, FontWeight.Bold))
private val CountdownDescriptionFont = FontFamily(Font(R.font.settings_nimbus_sans))
private val Sunken = Color(0xFF0B101C)
private val Critical = Color(0xFFEF4444)

@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = remember { SettingsViewModel.Factory(container) })
    val context = LocalContext.current
    var editUsername by remember { mutableStateOf(false) }
    var editTiers by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var zoneExpanded by remember { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(container.notificationHelper.notificationsEnabled()) }
    var exactGranted by remember { mutableStateOf(container.alarmScheduler.isExactAlarmAllowed()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = container.notificationHelper.notificationsEnabled()
                exactGranted = container.alarmScheduler.isExactAlarmAllowed()
                scope.launch { runCatching { container.alarmScheduler.rescheduleFromSettings() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = container.notificationHelper.notificationsEnabled()
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
        var hours by remember { mutableStateOf(vm.reminderHours.map { it.toString() }) }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editTiers = false },
            title = { Text("Countdown alert tiers") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hours before the 00:00 UTC reset. Use decreasing values from 1 to 23.")
                listOf("Passive ping", "Warning nudge", "Urgent buzzer").forEachIndexed { index, title ->
                    OutlinedTextField(hours[index], { value ->
                        hours = hours.toMutableList().also { it[index] = value.filter(Char::isDigit).take(2) }
                        invalid = false
                    }, label = { Text(title) }, suffix = { Text("hours") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (invalid) Text("Enter three decreasing, different values between 1 and 23.", color = Critical)
            } },
            confirmButton = { TextButton(onClick = {
                if (vm.setTiers(hours.map { it.toIntOrNull() ?: 0 })) editTiers = false else invalid = true
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editTiers = false }) { Text("Cancel") } },
        )
    }
    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text("Notification options") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleRow("Confirm when done", "Also notify when today's challenge is complete.", vm.confirmWhenDone,
                    vm::onConfirmWhenDoneChange, vm.initialized)
                TextButton(onClick = { openNotificationSettings() }) { Text(if (notifGranted) "Notification settings" else "Enable notifications") }
                if (!exactGranted) TextButton(onClick = { openAlarmSettings() }) { Text("Allow exact reminders") }
                OutlinedButton(enabled = notifGranted, onClick = { container.notificationHelper.sendTest() }) {
                    Text("Send test notification")
                }
            } },
            confirmButton = { TextButton(onClick = { showOptions = false }) { Text("Done") } },
        )
    }

    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.4.0"
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
                vm.reminderHours.forEachIndexed { tier, hours ->
                    val instant = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC)
                        .minusHours(hours.toLong()).toInstant()
                    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(instant)
                    Row(Modifier.fillMaxWidth().background(Sunken, RoundedCornerShape(8.dp))
                        .border(1.dp, SettingsInset, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Mono("T-${hours}h", colors[tier], 13, weight = FontWeight.Bold, family = CountdownFont)
                        Mono("$time $timeSuffix", SettingsTelemetry, 12, weight = FontWeight.Normal, family = CountdownFont)
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
            ToggleRow("Tactile Urgency Haptics", "Vibration pulse at T-${vm.reminderHours[1]}h and T-${vm.reminderHours[2]}h",
                vm.hapticsEnabled, vm::setHaptics, vm.initialized)
            if (vm.remindersEnabled && (!notifGranted || !exactGranted)) {
                TextButton(onClick = { if (!notifGranted) openNotificationSettings() else openAlarmSettings() },
                    contentPadding = PaddingValues(0.dp)) {
                    Text(if (!notifGranted) "Enable notifications to receive reminders" else "Allow exact alarms for timely reminders",
                        color = Tertiary, fontSize = 11.sp)
                }
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
        vm.message?.let { Text(it, color = Tertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        // Preserve the existing options until their own design is approved.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = { showOptions = true }, contentPadding = PaddingValues(4.dp)) {
                Text("Notification options", color = SettingsMuted, fontSize = 11.sp)
            }
            Text("App version v$version", color = SettingsMuted, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }) {
                Text("Privacy Policy", color = SettingsMuted, fontFamily = GeistFontFamily, fontSize = 13.sp,
                    letterSpacing = 0.sp, textDecoration = TextDecoration.Underline)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsModule(title: String, icon: Int, caption: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (icon == R.drawable.ic_settings_sync) SettingsButton else SettingsBorder),
        colors = CardDefaults.cardColors(containerColor = SettingsCard)) {
        Column(Modifier.padding(21.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesignIcon(icon, Primary, if (icon == R.drawable.ic_settings_notifications_active) 24 else 20)
                Text(title, color = Color.White, fontSize = 17.sp, lineHeight = 25.5.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-.17).sp, modifier = Modifier.weight(1f))
                caption?.let { Mono(it, SettingsSafe, 12) }
            }
            Spacer(Modifier.height(16.dp))
            content()
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
