package com.streakguard.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
internal fun LeetCodeUsernameDialog(
    username: String,
    enabled: Boolean,
    saving: Boolean,
    error: String?,
    onEdit: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var handle by rememberSaveable { mutableStateOf(username) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("LeetCode username") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Use your public handle. No password or cookies are needed.")
            OutlinedTextField(handle, { handle = it; onEdit() }, label = { Text("Username") },
                prefix = { Text("@") }, singleLine = true, enabled = !saving, isError = error != null)
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(
            enabled = enabled && !saving && handle.trim().removePrefix("@").isNotBlank() && !handle.trim().any { it.isWhitespace() },
            onClick = { onSave(handle) },
        ) { Text(if (saving) "Checking…" else "Save") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
