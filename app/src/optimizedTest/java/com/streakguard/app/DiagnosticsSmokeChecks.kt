package com.streakguard.app

import android.content.Context
import androidx.core.content.FileProvider
import com.streakguard.app.diagnostics.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipFile

object DiagnosticsSmokeChecks {
    @JvmStatic fun run(context: Context): String = runBlocking {
        val c = (context.applicationContext as StreakGuardApp).container
        val diagnostics = c.diagnostics
        c.settingsStore.setSyncOnOpen(false)
        c.settingsStore.setOnboardingDone(true)
        c.settingsStore.setUsername("leetcode", "private-diagnostic-handle")
        diagnostics.clear()
        diagnostics.log(DiagnosticEvent.API_FINISHED, mapOf("http_status" to 200, "api_response" to "secret-api-response"))
        c.notificationHelper.sendTest(haptics = true)
        val report = diagnostics.prepareReport()
        val text = ZipFile(report).use { zip -> zip.getInputStream(zip.getEntry("report.txt")).bufferedReader().readText() }
        check(text.contains("NOTIFICATION_POSTED")) { "Successful notification posting wasn't recorded" }
        check(text.contains("http_status=200") && text.contains("exact_permission=") && text.contains("reminder_minutes="))
        check(!text.contains("secret-api-response") && !text.contains("private-diagnostic-handle"))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", report)
        check(context.contentResolver.openInputStream(uri)?.use { it.read() } == 80) { "ZIP URI wasn't readable" }
        check(runCatching { FileProvider.getUriForFile(context, "${context.packageName}.diagnostics",
            File(context.noBackupFilesDir, "diagnostics/log-1.txt")) }.isFailure) { "Provider exposes private logs" }
        diagnostics.clear()
        check(diagnostics.size() == 0L && !report.exists()) { "Clear did not remove logs and exports" }
        check(runCatching { context.contentResolver.openInputStream(uri)?.close() }.isFailure)
        // A separate private directory avoids competing writers while testing feature/cohort revocation.
        var policy = DiagnosticsAccess(true, true, true)
        val isolated = DiagnosticsService(context.createDeviceProtectedStorageContext(), c.settingsStore,
            DiagnosticsAccessPolicy { policy })
        isolated.log(DiagnosticEvent.CHECK_STARTED)
        check(isolated.size() > 0)
        policy = DiagnosticsAccess.Disabled
        isolated.refreshAccess()
        isolated.log(DiagnosticEvent.CHECK_STARTED)
        check(!isolated.availability.value.visible && isolated.size() == 0L)
        check(runCatching { isolated.prepareReport() }.isFailure)
        c.settingsStore.setUsername("leetcode", "")
        "PASS: timestamped report ZIP, notification-post success, settings/permission snapshot, response/username exclusion, private FileProvider scope/readability, clear/revocation, and feature-access disable/purge."
    }
}
