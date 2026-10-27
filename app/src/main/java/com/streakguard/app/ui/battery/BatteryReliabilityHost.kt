package com.streakguard.app.ui.battery

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.R
import com.streakguard.app.data.prefs.SettingsStore
import com.streakguard.app.ui.settings.*
import com.streakguard.app.ui.theme.Primary

internal data class BatterySettingsAccess(
    val status: BatteryRestrictionStatus,
    val error: String?,
    val openSettings: () -> Unit,
)

internal val LocalBatterySettings = staticCompositionLocalOf<BatterySettingsAccess> {
    error("Battery settings must be hosted by BatteryReliabilityHost")
}

/** Activity-scoped so navigation and rotation cannot restart a dismissed prompt. */
@Composable
internal fun BatteryReliabilityHost(store: SettingsStore, ready: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    val vm: BatteryReliabilityViewModel = viewModel(factory = remember {
        BatteryReliabilityViewModel.Factory(context.applicationContext, store)
    })
    LaunchedEffect(ready) { vm.setReady(ready) }
    DisposableEffect(owner, activity, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onForeground()
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) vm.onBackground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.onForeground()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val openSettings = { vm.openSettings { openBatterySettings(context, vm.status) } }
    CompositionLocalProvider(LocalBatterySettings provides BatterySettingsAccess(vm.status, vm.error, openSettings)) {
        content()
        if (ready && vm.state.prompt != BatteryPrompt.NONE && !vm.state.awaitingSettingsReturn) {
            BatteryReliabilityDialog(
                confirmation = vm.state.prompt == BatteryPrompt.CONFIRMATION,
                dontShowAgain = vm.dontShowAgain,
                saving = vm.saving,
                error = vm.error,
                onDontShowAgain = { vm.dontShowAgain = it },
                onDismiss = vm::dismiss,
                onOpenSettings = openSettings,
            )
        }
    }
}

@Composable
private fun BatteryReliabilityDialog(
    confirmation: Boolean,
    dontShowAgain: Boolean,
    saving: Boolean,
    error: String?,
    onDontShowAgain: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val maxHeight = with(LocalDensity.current) { windowSize.height.toDp() - 48.dp }.coerceAtLeast(160.dp)
    Dialog(onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false,
            dismissOnBackPress = !saving, dismissOnClickOutside = !saving)) {
        Surface(Modifier.padding(horizontal = 16.dp).widthIn(max = 358.dp)
            .fillMaxWidth().heightIn(max = maxHeight),
            color = SettingsCard, shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SettingsBorder)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.ic_settings_notifications_active), null,
                        tint = Primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (confirmation) "Leave battery settings unchanged?" else "Keep reminders reliable",
                        color = SettingsText, fontFamily = SettingsCardFont,
                        fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_settings_close), "Close battery reminder prompt",
                            tint = SettingsMuted, modifier = Modifier.size(18.dp))
                    }
                }
                Text(if (confirmation)
                    "Battery restrictions may delay or stop your reminders. You can change this anytime in Settings → Permissions → Battery optimisation."
                    else "Battery restrictions may delay or stop your reminders. Open battery settings and allow unrestricted background usage for StreakGuard.",
                    color = SettingsMuted, fontFamily = SettingsCardFont, fontSize = 14.sp, lineHeight = 21.sp)
                if (confirmation) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(dontShowAgain, enabled = !saving, role = Role.Checkbox, onValueChange = onDontShowAgain),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(dontShowAgain, onCheckedChange = null, enabled = !saving,
                            colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = SettingsMuted))
                        Spacer(Modifier.width(10.dp))
                        Text("Don’t show again", color = SettingsText, fontFamily = SettingsCardFont, fontSize = 12.sp)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, SettingsBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsButton, contentColor = SettingsText)) {
                        Text("Not now", fontFamily = SettingsCardFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(onClick = onOpenSettings, enabled = !saving, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = SettingsThumb)) {
                        Text("Open settings", fontFamily = SettingsCardFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
