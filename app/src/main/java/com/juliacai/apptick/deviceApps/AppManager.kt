package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Telephony
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.groups.AppLimitGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppManager(private val context: Context) {

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val blockedPackages = getSafetyCriticalPackages(pm)
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(mainIntent, 0).mapNotNull {
            try {
                val appInfo = pm.getApplicationInfo(it.activityInfo.packageName, 0)
                if (appInfo.packageName in blockedPackages) {
                    return@mapNotNull null
                }
                AppInfo(
                    appName = appInfo.loadLabel(pm).toString(),
                    appPackage = appInfo.packageName,
                    appIcon = appInfo.loadIcon(pm)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    suspend fun getInstalledAppsByCategory(categoryMode: String): List<AppInfo> = withContext(Dispatchers.IO) {
        val androidCategory = autoAddModeToCategory(categoryMode) ?: return@withContext emptyList()
        val pm = context.packageManager
        val blockedPackages = getSafetyCriticalPackages(pm)
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(mainIntent, 0).mapNotNull {
            try {
                val appInfo = pm.getApplicationInfo(it.activityInfo.packageName, 0)
                if (appInfo.packageName in blockedPackages) return@mapNotNull null
                if (appInfo.category != androidCategory) return@mapNotNull null
                AppInfo(
                    appName = appInfo.loadLabel(pm).toString(),
                    appPackage = appInfo.packageName,
                    appIcon = appInfo.loadIcon(pm)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    fun getAppCategory(packageName: String): Int? {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getSafetyCriticalPackages(pm: PackageManager): Set<String> {
        val blocked = mutableSetOf(context.packageName)
        val dialerPkg = pm.resolveActivity(Intent(Intent.ACTION_DIAL), 0)?.activityInfo?.packageName
        val smsPkg = Telephony.Sms.getDefaultSmsPackage(context)
        if (!dialerPkg.isNullOrBlank()) blocked += dialerPkg
        if (!smsPkg.isNullOrBlank()) blocked += smsPkg

        blocked += setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.messaging",
            "com.google.android.apps.messaging"
        )
        return blocked
    }

    companion object {
        fun autoAddModeToCategory(mode: String): Int? = when (mode) {
            AppLimitGroup.AUTO_ADD_CATEGORY_GAME -> ApplicationInfo.CATEGORY_GAME
            AppLimitGroup.AUTO_ADD_CATEGORY_SOCIAL -> ApplicationInfo.CATEGORY_SOCIAL
            AppLimitGroup.AUTO_ADD_CATEGORY_AUDIO -> ApplicationInfo.CATEGORY_AUDIO
            AppLimitGroup.AUTO_ADD_CATEGORY_VIDEO -> ApplicationInfo.CATEGORY_VIDEO
            AppLimitGroup.AUTO_ADD_CATEGORY_IMAGE -> ApplicationInfo.CATEGORY_IMAGE
            AppLimitGroup.AUTO_ADD_CATEGORY_NEWS -> ApplicationInfo.CATEGORY_NEWS
            AppLimitGroup.AUTO_ADD_CATEGORY_MAPS -> ApplicationInfo.CATEGORY_MAPS
            AppLimitGroup.AUTO_ADD_CATEGORY_PRODUCTIVITY -> ApplicationInfo.CATEGORY_PRODUCTIVITY
            else -> null
        }
    }
}
