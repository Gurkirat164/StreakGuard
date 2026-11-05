package com.streakguard.app.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Separate durable ledger: reopening must not erase evidence of yesterday's missed alarms. */
class ReminderTraceStore(private val directory: File, private val maxEntries: Int = 512,
                         private val now: () -> Long = System::currentTimeMillis) {
    private val week = 7L * 24 * 60 * 60 * 1000
    init { require(maxEntries > 0) }

    @Synchronized fun record(id: String, stage: String, fields: Map<String, String> = emptyMap()) {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")))
        directory.mkdirs()
        val file = File(directory, "$id.trace")
        val properties = read(file)
        // Only scheduling creates occurrences; old/corrupt IDs must not create ghost reminders.
        if (properties.isEmpty() && stage != "schedule_requested") return
        fields.forEach { (key, value) -> properties.setProperty(key, value) }
        properties.setProperty("trace_id", id)
        properties.setProperty("stage", stage)
        properties.setProperty(stage, now().toString())
        properties.setProperty("updated", now().toString())
        val temporary = File(directory, "$id.tmp")
        FileOutputStream(temporary).use { output -> properties.store(output, null); output.fd.sync() }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        prune()
    }

    @Synchronized fun cancelSlot(slot: Int, reason: String): List<String> {
        val ids = snapshots().filter {
            it["slot"] == slot.toString() && it["receiver_entered"] == null &&
                it["cancelled"] == null && it["schedule_failed"] == null
        }.mapNotNull { it["trace_id"] }
        ids.forEach { record(it, "cancelled", mapOf("reason" to reason)) }
        return ids
    }

    @Synchronized fun snapshots(): List<Map<String, String>> {
        prune()
        return directory.listFiles().orEmpty().filter { it.extension == "trace" }.map { file ->
            try {
                val values = read(file)
                values.stringPropertyNames().associateWith { values.getProperty(it) }
            } catch (error: Exception) {
                mapOf("trace_id" to file.nameWithoutExtension, "stage" to "ledger_unreadable",
                    "error_type" to error.javaClass.simpleName)
            }
        }.sortedBy { it["at_ms"]?.toLongOrNull() ?: 0 }
    }

    private fun read(file: File): Properties = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    private fun prune() {
        val files = directory.listFiles().orEmpty().filter { it.extension == "trace" }
        files.filter { it.lastModified() < now() - week }.forEach { check(it.delete()) }
        directory.listFiles().orEmpty().filter { it.extension == "trace" }
            .sortedByDescending { it.lastModified() }.drop(maxEntries).forEach { check(it.delete()) }
    }

    @Synchronized fun clear() { directory.listFiles().orEmpty().forEach { check(it.delete()) } }

    companion object {
        /** Findings describe missing evidence, never invent an OS/vendor cause. */
        fun finding(record: Map<String, String>, now: Long): String {
            val at = record["at_ms"]?.toLongOrNull()
            return when {
            record["stage"] == "ledger_unreadable" -> "diagnostic_ledger_unreadable"
            record["notification_posted"] != null -> if (record["kind"] == "failure")
                "connection_notice_posted_reminder_unconfirmed" else "posting_accepted_display_unconfirmed"
            record["notification_blocked"] != null -> "notification_blocked"
            record["notification_failed"] != null -> "notification_post_failed"
            record["skipped"] != null -> "skipped_${record["reason"]}"
            record["worker_failed"] != null -> "worker_failed"
            record["worker_cancelled"] != null -> "worker_cancelled"
            record["enqueue_failed"] != null -> "work_enqueue_failed"
            record["retry_required"] != null -> "status_unknown_retry_scheduled_see_events"
            record["retry_exhausted"] != null -> "status_unknown_retries_exhausted"
            record["worker_finished"] != null -> "worker_finished_without_notification_see_events"
            record["cancelled"] != null -> if (at != null && (record["cancelled"]?.toLongOrNull() ?: 0) > at + 60_000)
                "overdue_no_receiver_before_app_cancel" else "cancelled_by_app"
            record["schedule_failed"] != null -> "schedule_failed"
            at != null && now < at + 60_000 -> "pending_or_within_grace"
            record["worker_started"] != null -> "worker_started_no_terminal_evidence"
            record["enqueue_requested"] != null -> "work_requested_no_worker_start"
            record["receiver_entered"] != null -> "receiver_entered_no_work_request"
            record["scheduled"] != null -> "scheduled_no_receiver_evidence_cause_unknown"
            else -> "scheduling_interrupted_or_unconfirmed"
            }
        }
    }
}
