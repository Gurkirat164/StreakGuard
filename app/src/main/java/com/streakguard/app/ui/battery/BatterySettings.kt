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

/** Use normal Settings pages; no direct exemption request or additional permission is needed. */
internal fun openBatterySettings(context: Context, status: BatteryRestrictionStatus): Boolean {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    val optimisationList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val destinations = if (status.backgroundRestricted) listOf(appDetails, optimisationList)
        else listOf(optimisationList, appDetails)
    return (destinations + Intent(Settings.ACTION_SETTINGS)).any { intent ->
        runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
