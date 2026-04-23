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
    private val SAMSUNG_BRANDS = setOf("samsung")
    private val OPPO_BRANDS = setOf("oppo", "realme", "oneplus")
    private val VIVO_BRANDS = setOf("vivo", "iqoo")
    private val OTHER_AGGRESSIVE_BRANDS = setOf("meizu", "asus", "lenovo", "nokia", "hmd global", "tecno", "infinix", "itel", "nothing")

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
        fun matchesBrands(brands: Set<String>) = brands.any { manufacturer.contains(it) || brand.contains(it) }
        val honorHuaweiDetected = matchesBrands(HONOR_HUAWEI_BRANDS)
        val xiaomiDetected = matchesBrands(XIAOMI_BRANDS)
        val samsungDetected = matchesBrands(SAMSUNG_BRANDS)
        val oppoDetected = matchesBrands(OPPO_BRANDS)
        val vivoDetected = matchesBrands(VIVO_BRANDS)
        val otherAggressiveDetected = matchesBrands(OTHER_AGGRESSIVE_BRANDS)
        val hasAdditionalOemRestrictions = honorHuaweiDetected || xiaomiDetected ||
            samsungDetected || oppoDetected || vivoDetected || otherAggressiveDetected
        val oemGuidance = when {
            honorHuaweiDetected -> "Also allow AppTick in App launch/Auto-start and set battery to No restrictions in Honor/Huawei system settings."
            xiaomiDetected -> "Also enable Auto-start for AppTick in Security app and set Battery saver to No restrictions on Xiaomi/Redmi/POCO."
            samsungDetected -> "Also check that AppTick is not in Sleeping or Deep sleeping apps lists in Device care > Battery settings."
            oppoDetected -> "Also enable Auto-launch for AppTick and disable battery optimization in your phone's Battery settings."
            vivoDetected -> "Also allow AppTick in Background power consumption settings and enable Auto-start."
            otherAggressiveDetected -> "Your phone manufacturer may aggressively restrict background apps. Check your phone's battery or app management settings to allow AppTick to run in the background."
            else -> null
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
            // OEM-specific per-app battery pages come first; when they resolve they
            // land the user directly on AppTick's battery entry instead of the
            // generic app-info screen.
            // Samsung (One UI) — opens app info battery page on many builds.
            Intent().setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.detail.AppBatteryDetailActivity",
            ).apply {
                putExtra("packageName", packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // Stock Android / Pixel undocumented action — resolves on some builds.
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                data = packageUri
            },
            Intent("android.settings.APP_BATTERY_USAGE_DETAILS", packageUri),
            // Fallback: app info page (user taps Battery row from there).
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
            // Xiaomi / Redmi / POCO
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent("miui.intent.action.OP_AUTO_START"),
            Intent().setClassName("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity").apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.packageName)
            },
            // Honor
            Intent().setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            // Huawei
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent("huawei.intent.action.HSM_PROTECTED_APPS"),
            // Samsung
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            Intent().setClassName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            // Oppo / Realme / OnePlus
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            Intent().setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            // Vivo / iQOO
            Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            // Asus
            Intent().setClassName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            // Nokia / HMD
            Intent().setClassName("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"),
            // Fallback: app details
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )
        return launchFirstAvailable(context, intents)
    }

    fun openDontKillMyApp(context: Context): Boolean {
        // Deep-link to the vendor-specific guide page when we can detect the OEM,
        // so the user lands on steps that apply to their phone instead of the
        // generic vendor-picker homepage.
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        fun matches(tokens: Set<String>) = tokens.any { manufacturer.contains(it) || brand.contains(it) }
        val vendorSlug = when {
            matches(setOf("samsung")) -> "samsung"
            matches(setOf("xiaomi", "redmi", "poco")) -> "xiaomi"
            matches(setOf("honor")) -> "honor"
            matches(setOf("huawei")) -> "huawei"
            matches(setOf("oneplus")) -> "oneplus"
            matches(setOf("oppo")) -> "oppo"
            matches(setOf("realme")) -> "realme"
            matches(setOf("vivo", "iqoo")) -> "vivo"
            matches(setOf("nokia", "hmd global")) -> "nokia"
            matches(setOf("asus")) -> "asus"
            matches(setOf("lenovo")) -> "lenovo"
            matches(setOf("meizu")) -> "meizu"
            matches(setOf("tecno")) -> "tecno"
            matches(setOf("nothing")) -> "nothing"
            else -> null
        }
        val url = if (vendorSlug != null) "$DONT_KILL_MY_APP_URL$vendorSlug" else DONT_KILL_MY_APP_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
