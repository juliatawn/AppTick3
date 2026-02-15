package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import com.juliacai.apptick.AppInfo
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
}
