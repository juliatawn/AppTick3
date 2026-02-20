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
    val backgroundRestricted: Boolean,
    val hasAdditionalOemRestrictions: Boolean,
    val oemGuidance: String?
)

object BatteryOptimizationHelper {
    const val DONT_KILL_MY_APP_URL = "https://dontkillmyapp.com/"

    private val HONOR_HUAWEI_BRANDS = setOf("honor", "huawei")
    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

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

        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val honorHuaweiDetected = HONOR_HUAWEI_BRANDS.any { manufacturer.contains(it) || brand.contains(it) }
        val xiaomiDetected = XIAOMI_BRANDS.any { manufacturer.contains(it) || brand.contains(it) }
        val hasAdditionalOemRestrictions = honorHuaweiDetected || xiaomiDetected
        val oemGuidance = if (honorHuaweiDetected) {
            "Also allow AppTick in App launch/Auto-start and set battery to No restrictions in Honor/Huawei system settings."
        } else if (xiaomiDetected) {
            "Also enable Auto-start for AppTick in Security app and set Battery saver to No restrictions on Xiaomi/Redmi/POCO."
        } else {
            null
        }

        return BatteryOptimizationStatus(
            unrestricted = ignoringBatteryOptimizations && !backgroundRestricted && !hasAdditionalOemRestrictions,
            ignoringBatteryOptimizations = ignoringBatteryOptimizations,
            backgroundRestricted = backgroundRestricted,
            hasAdditionalOemRestrictions = hasAdditionalOemRestrictions,
            oemGuidance = oemGuidance
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

    fun openManufacturerBackgroundSettings(context: Context): Boolean {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = listOf(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent("miui.intent.action.OP_AUTO_START"),
            Intent().setClassName("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity").apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.packageName)
            },
            Intent().setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent("huawei.intent.action.HSM_PROTECTED_APPS"),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )
        return launchFirstAvailable(context, intents)
    }

    fun openDontKillMyApp(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DONT_KILL_MY_APP_URL))
        return launchFirstAvailable(context, listOf(intent))
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
