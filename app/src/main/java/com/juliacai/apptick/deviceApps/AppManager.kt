package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.juliacai.apptick.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppManager(private val context: Context) {

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(mainIntent, 0).mapNotNull {
            try {
                val appInfo = pm.getApplicationInfo(it.activityInfo.packageName, 0)
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
}
