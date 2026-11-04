package com.streakguard.app.ui.battery

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

internal fun readBatteryRestrictions(context: Context) = BatteryRestrictionStatus(
    // An unreadable status must not be reported as unrestricted.
    optimisationEnabled = runCatching {
        !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(true),
    backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && runCatching {
        context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
    }.getOrDefault(true),
)

/** Android owns the approval dialog. Fall back only if the device cannot handle the request. */
internal fun requestBatteryExemption(context: Context): Boolean {
    if (!readBatteryRestrictions(context).optimisationEnabled) return true
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri())
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    val optimisationList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    return listOf(request, optimisationList, appDetails).any { intent ->
        runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
