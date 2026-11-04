package com.streakguard.app.diagnostics

enum class DiagnosticEvent {
    APP_STARTED, APP_OPENED, SCREEN_OPENED, SETTINGS_SNAPSHOT, SETTINGS_CHANGED,
    PERMISSIONS_SNAPSHOT, API_STARTED, API_FINISHED, API_FAILED, CHECK_STARTED,
    CHECK_FINISHED, CHECK_SKIPPED, DATABASE_FAILED, REMINDER_SCHEDULED, RETRY_SCHEDULED,
    RETRY_CANCELLED, REMINDER_RECEIVED, WORKER_STARTED, WORKER_FINISHED, SYNC_SCHEDULED,
    SYNC_CANCELLED, MIDNIGHT_RECEIVED, SYSTEM_EVENT, NOTIFICATION_POSTED,
    NOTIFICATION_BLOCKED, NOTIFICATION_FAILED, NOTIFICATION_CANCELLED,
    ALARM_EVENT, REPORT_PREPARED, EVENTS_DROPPED, UNCAUGHT_ERROR,
    REMINDER_TRACE, REMINDER_CANCELLED, DIAGNOSTIC_FAILURE, PROCESS_EXIT, RUNTIME_SNAPSHOT,
    WORK_SNAPSHOT, RECOVERY_AUDIT
}

/** An allowlist, not a generic message logger: bodies, queries, URLs and usernames cannot be recorded. */
object DiagnosticFields {
    private val allowed = setOf("screen", "setting", "enabled", "tier", "attempt", "day", "at", "exact",
        "reason", "manual", "completed", "known", "duration_ms", "http_status", "error_type", "kind",
        "channel", "importance", "haptics", "minutes", "reminder_minutes", "timezone", "use_24_hour",
        "reminders", "alarms", "alarm_feature", "sync_on_open", "sync_interval", "username_configured",
        "profile_verified", "platform_enabled", "notifications", "post_permission", "exact_permission",
        "battery_optimization_exempt", "power_save", "network_validated", "api", "count", "version",
        "version_code", "android_api", "manufacturer", "model", "device_timezone", "device_time",
        "trace_id", "stage", "slot", "at_ms", "work_id", "work_state", "stop_reason",
        "background_restricted", "standby_bucket", "device_idle", "interactive", "uptime_ms",
        "boot_count", "exit_at", "exit_reason", "exit_status", "exit_importance", "session_id",
        "active_after_post", "notification_id", "sound_enabled", "storage_error", "finding")
    fun safeValues(fields: Map<String, Any?>): Map<String, String> = fields.filterKeys { it in allowed }
        .mapValues { (_, value) ->
            when (value) {
                null -> "unknown"
                is Number, is Boolean -> value.toString()
                is Enum<*> -> value.name
                is String -> value.take(120).replace(Regex("[^A-Za-z0-9_ .,():+/=-]"), "_")
                else -> "omitted"
            }
        }
    fun encode(fields: Map<String, Any?>): String = safeValues(fields).entries
        .sortedBy { it.key }.joinToString(" ") { (key, value) -> "$key=$value" }
}
