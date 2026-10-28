package com.streakguard.app.diagnostics

data class DiagnosticsAccess(val collect: Boolean, val visible: Boolean, val share: Boolean) {
    companion object { val Disabled = DiagnosticsAccess(false, false, false) }
}

/** Replace at the composition root with cached remote flags/cohort eligibility later. */
fun interface DiagnosticsAccessPolicy { fun access(): DiagnosticsAccess }

object DiagnosticsFeaturePolicy : DiagnosticsAccessPolicy {
    // One switch removes collection, exports, UI and retained files on the next app start/update.
    private const val ENABLED = true
    override fun access() = if (ENABLED) DiagnosticsAccess(true, true, true) else DiagnosticsAccess.Disabled
}

/** Future server transport can implement this without changing event collection or retention. */
fun interface DiagnosticReportDestination { fun deliver(context: android.content.Context, report: java.io.File) }

object AndroidShareDestination : DiagnosticReportDestination {
    override fun deliver(context: android.content.Context, report: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", report)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "StreakGuard debug report")
            clipData = android.content.ClipData.newRawUri("Debug report", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share debug report"))
    }
}
