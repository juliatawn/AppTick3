package com.juliacai.apptick.permissions

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class BatteryOptimizationStatus(
    val unrestricted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val backgroundRestricted: Boolean
)

object BatteryOptimizationHelper {

    fun getStatus(context: Context): BatteryOptimizationStatus {
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
        } else {
            true
        }

        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = appContext.getSystemService(ActivityManager::class.java)
            activityManager?.isBackgroundRestricted == true
        } else {
            false
        }

        return BatteryOptimizationStatus(
            unrestricted = ignoringBatteryOptimizations && !backgroundRestricted,
            ignoringBatteryOptimizations = ignoringBatteryOptimizations,
            backgroundRestricted = backgroundRestricted
        )
    }

    fun openAppBatterySettings(context: Context): Boolean {
        val packageName = context.packageName
        val packageUri = Uri.parse("package:$packageName")
        val intents = listOfNotNull(
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                data = packageUri
            },
            Intent("android.settings.APP_BATTERY_USAGE_DETAILS", packageUri),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                null
            }
        )
        return launchFirstAvailable(context, intents)
    }

    fun openGeneralBatterySettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstAvailable(context, intents)
    }

    private fun launchFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        val packageManager = context.packageManager
        intents.forEach { intent ->
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(packageManager) == null) return@forEach
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
