package com.streakguard.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.streakguard.app.diagnostics.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun DebugDialog(service: DiagnosticsService, onDismiss: () -> Unit,
                         destination: DiagnosticReportDestination = AndroidShareDestination) {
    val access by service.availability.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmShare by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(access) {
        if (!access.visible) { onDismiss(); return@LaunchedEffect }
        try { bytes = service.size() } catch (_: Exception) { message = "Couldn't read debug log storage." }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = SettingsCard,
        title = { Text("Debug") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Timestamped app events, settings, permissions, checks, retries, and notification posting results are saved on this device. API responses and usernames are excluded.")
            Text("Logs older than 7 days are automatically deleted. If logs reach 10 MB, the oldest entries are deleted first.")
            bytes?.let { Text("Stored logs: ${String.format(Locale.ROOT, "%.2f", it / 1_000_000.0)} MB / 10 MB") }
            if (service.storageError) Text("Some logs couldn't be saved on this device.", color = Critical)
            message?.let { Text(it) }
            OutlinedButton(onClick = { confirmShare = true }, enabled = !busy && access.share,
                modifier = Modifier.fillMaxWidth()) { Text("Share debug report") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("Clear logs", color = Critical) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } })
    if (confirmShare) AlertDialog(onDismissRequest = { confirmShare = false },
        containerColor = SettingsCard, title = { Text("Share debug report?") },
        text = { Text("The ZIP report will include timestamps, app and Android versions, device manufacturer/model, timezone, current app settings, permission and battery/network status, checks, retries, errors, and notification posting results.\n\nIt excludes API responses, usernames, passwords, tokens, and device identifiers.\n\nYou will choose an app and recipient. StreakGuard does not upload this report automatically. Are you sure you want to continue?") },
        dismissButton = { TextButton(onClick = { confirmShare = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            confirmShare = false; busy = true; message = null
            scope.launch {
                try {
                    val report = service.prepareReport()
                    if (service.access.share) {
                        destination.deliver(context, report)
                        Diagnostics.log(DiagnosticEvent.REPORT_PREPARED)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "Couldn't prepare or share the report. Please try again." }
                finally { busy = false }
            }
        }) { Text("Continue to share") } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        containerColor = SettingsCard, title = { Text("Clear all debug logs?") },
        text = { Text("This permanently deletes all saved debug logs and temporary report copies on this device. New app events will be logged again afterward. Copies you already sent to other apps or people cannot be deleted here.\n\nLogs are also automatically deleted once older than 7 days, or oldest-first when the 10 MB limit is reached. Your settings and challenge history will stay unchanged.") },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            confirmClear = false; busy = true
            scope.launch {
                try { service.clear(); bytes = 0; message = "Debug logs cleared." }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "Couldn't clear all logs. Please try again." }
                finally { busy = false }
            }
        }) { Text("Clear all logs", color = Critical) } })
}
