package com.streakguard.app.diagnostics

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.work.*
import com.streakguard.app.data.prefs.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** All disk work is serialized off the UI thread; logging failure never breaks reminders. */
class DiagnosticsService(
    context: Context,
    private val settings: SettingsStore,
    private val policy: DiagnosticsAccessPolicy = DiagnosticsFeaturePolicy,
) {
    // Never retain an Activity, including when a future caller changes the composition root.
    private val context = if (context.isDeviceProtectedStorage)
        context.applicationContext.createDeviceProtectedStorageContext() else context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<suspend () -> Unit>(256)
    private val dropped = AtomicInteger()
    private val store = RollingLogStore(File(context.noBackupFilesDir, "diagnostics"))
    private val exports = File(context.cacheDir, "diagnostic_reports")
    @Volatile var storageError: Boolean = false; private set
    private val currentAccess = MutableStateFlow(runCatching { policy.access() }.getOrDefault(DiagnosticsAccess.Disabled))
    val availability = currentAccess.asStateFlow()
    val access: DiagnosticsAccess get() = currentAccess.value
    suspend fun refreshAccess() {
        currentAccess.value = runCatching { policy.access() }.getOrDefault(DiagnosticsAccess.Disabled)
        cleanup(); scheduleCleanup()
    }

    init {
        scope.launch { for (command in commands) try { command() } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { storageError = true } }
        scope.launch {
            cleanup()
            if (access.collect) {
                log(DiagnosticEvent.APP_STARTED, deviceFields())
                settings.diagnosticSettings.collect { values ->
                    log(DiagnosticEvent.SETTINGS_SNAPSHOT, values)
                    log(DiagnosticEvent.PERMISSIONS_SNAPSHOT, permissionFields())
                }
            }
        }
    }

    fun log(event: DiagnosticEvent, fields: Map<String, Any?> = emptyMap()) {
        if (!access.collect) return
        // Encode before queueing so no raw response/exception/user object is retained in memory.
        val encoded = DiagnosticFields.encode(fields)
        val timestamp = System.currentTimeMillis()
        if (!commands.trySend {
            if (access.collect) {
                val lost = dropped.getAndSet(0)
                if (lost > 0) store.append(DiagnosticEvent.EVENTS_DROPPED.name, "count=$lost")
                store.append(event.name, encoded, timestamp)
                storageError = false
            } else purge()
        }.isSuccess) dropped.incrementAndGet()
    }

    private suspend fun <T> serialized(action: () -> T): T {
        val result = CompletableDeferred<T>()
        commands.send {
            try { result.complete(action()) } catch (error: Exception) {
                storageError = true; result.completeExceptionally(error)
            }
        }
        return result.await()
    }

    suspend fun cleanup() = serialized {
        if (!access.collect && !access.visible && !access.share) purge()
        else { store.prune(); cleanExports() }
    }
    suspend fun size(): Long = serialized { if (!access.visible) { purge(); 0L } else store.size() }
    suspend fun clear() = serialized { purge(); storageError = false }

    /** Call only after the share confirmation. Export destinations do not own collection. */
    suspend fun prepareReport(): File {
        check(access.visible && access.share) { "Debug sharing is unavailable" }
        val snapshot = deviceFields() + permissionFields() + settings.diagnosticSettings.first()
        val channels = context.getSystemService(NotificationManager::class.java).notificationChannels.joinToString("\n") {
            DiagnosticFields.encode(mapOf("channel" to it.id, "importance" to it.importance, "haptics" to it.shouldVibrate()))
        }
        return serialized {
            check(access.visible && access.share) { "Debug sharing is unavailable" }
            cleanExports(all = true)
            exports.mkdirs()
            val file = File(exports, "streakguard-debug-${System.currentTimeMillis()}-${UUID.randomUUID()}.zip")
            try {
                ZipOutputStream(file.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("report.txt"))
                    zip.write(("StreakGuard debug report\nGenerated UTC: ${Instant.now()}\n" +
                        "Notification POSTED means Android accepted the posting call; delivery/display is not guaranteed.\n" +
                        "No API bodies, queries, usernames, credentials, or device identifiers are included.\n\n" +
                        "CURRENT SETTINGS / PERMISSIONS / DEVICE\n${DiagnosticFields.encode(snapshot)}\n\nNOTIFICATION CHANNELS\n$channels\n\n" +
                        "TIMESTAMPED EVENTS (epoch milliseconds, UTC, event, fields)\n").toByteArray(Charsets.UTF_8))
                    store.copyTo(zip)
                    zip.closeEntry()
                }
                file
            } catch (error: Exception) { file.delete(); throw error }
        }
    }

    fun permissionFields(): Map<String, Any?> {
        val power = context.getSystemService(PowerManager::class.java)
        val network = context.getSystemService(ConnectivityManager::class.java)
        return mapOf("notifications" to context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
            "post_permission" to (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            "exact_permission" to (Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()),
            "battery_optimization_exempt" to power.isIgnoringBatteryOptimizations(context.packageName),
            "power_save" to power.isPowerSaveMode,
            "network_validated" to (network.getNetworkCapabilities(network.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true))
    }

    private fun deviceFields(): Map<String, Any?> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return mapOf("version" to info.versionName, "version_code" to
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
            "android_api" to Build.VERSION.SDK_INT, "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL,
            "device_timezone" to ZoneId.systemDefault().id, "device_time" to Instant.now().toString(),
            "alarm_feature" to com.streakguard.app.notify.AlarmFeature.ENABLED)
    }

    private fun purge() { store.clear(); cleanExports(all = true) }
    fun recordCrash(error: Throwable) {
        if (!access.collect) return
        runCatching {
            store.append(DiagnosticEvent.UNCAUGHT_ERROR.name,
                DiagnosticFields.encode(mapOf("error_type" to error.javaClass.simpleName)))
            error.stackTrace.take(12).forEach { frame ->
                store.append(DiagnosticEvent.UNCAUGHT_ERROR.name, DiagnosticFields.encode(mapOf(
                    "kind" to "frame", "error_type" to "${frame.className}.${frame.methodName}:${frame.lineNumber}")))
            }
        }
    }
    private fun cleanExports(all: Boolean = false) {
        exports.listFiles()?.forEach { file ->
            if (all || file.lastModified() <= System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)) {
                runCatching { context.revokeUriPermission(androidx.core.content.FileProvider.getUriForFile(context,
                    "${context.packageName}.diagnostics", file), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                check(file.delete())
            }
        }
    }

    fun scheduleCleanup() {
        val wm = WorkManager.getInstance(context)
        if (!access.collect && !access.visible) { wm.cancelUniqueWork("diagnostics_cleanup"); return }
        wm.enqueueUniquePeriodicWork("diagnostics_cleanup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DiagnosticsCleanupWorker>(1, TimeUnit.HOURS).build())
    }
}

class DiagnosticsCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            (applicationContext as com.streakguard.app.StreakGuardApp).container.diagnostics.cleanup()
            Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
    }
}

object Diagnostics {
    @android.annotation.SuppressLint("StaticFieldLeak") // Service retains only application/storage contexts.
    @Volatile private var service: DiagnosticsService? = null
    fun install(value: DiagnosticsService) {
        service = value; runCatching { value.scheduleCleanup() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous != null) Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            value.recordCrash(error)
            previous.uncaughtException(thread, error)
        }
    }
    fun log(event: DiagnosticEvent, vararg fields: Pair<String, Any?>) {
        runCatching { service?.log(event, fields.toMap()) }
    }
}
