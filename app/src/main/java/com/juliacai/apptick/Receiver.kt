package com.juliacai.apptick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.LegacyDataMigrator
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.deviceApps.AppManager
import com.juliacai.apptick.groups.AppLimitGroup
import kotlinx.coroutines.runBlocking

class Receiver : BroadcastReceiver() {

    internal fun handleStartupSignal(context: Context) {
        val database = AppTickDatabase.getDatabase(context)
        runBlocking {
            LegacyDataMigrator(context, database.appLimitGroupDao()).migrate()
        }
        val activeGroupCount = database.appLimitGroupDao().getActiveGroupCountSync()
        val shouldRun = activeGroupCount > 0
        BackgroundChecker.applyDesiredServiceState(context, shouldRun)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action == null) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON -> {
                val pendingResult = goAsync()
                Thread {
                    try {
                        handleStartupSignal(context)
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
            }
            ACTION_SERVICE_WATCHDOG -> {
                val pendingResult = goAsync()
                Thread {
                    try {
                        handleStartupSignal(context)
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
            }
            Intent.ACTION_SCREEN_OFF -> {
                val prefs = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("screenOn", false).apply()
            }
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                val packageName = intent.data?.schemeSpecificPart ?: return
                val pendingResult = goAsync()
                Thread {
                    try {
                        handleNewAppInstalled(context, packageName)
                    } finally {
                        pendingResult.finish()
                    }
                }.start()
            }
        }
    }

    private fun handleNewAppInstalled(context: Context, packageName: String) {
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        // Only process launcher apps
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
        val appName = appInfo.loadLabel(pm).toString()
        val appCategory = appInfo.category

        val database = AppTickDatabase.getDatabase(context)
        val dao = database.appLimitGroupDao()

        runBlocking {
            val allGroups = dao.getAllAppLimitGroupsImmediate()
            for (entity in allGroups) {
                val group = entity.toDomainModel()
                if (group.autoAddMode == AppLimitGroup.AUTO_ADD_NONE) continue

                val alreadyInGroup = group.apps.any { it.appPackage == packageName }
                if (alreadyInGroup) continue

                val shouldAdd = when (group.autoAddMode) {
                    AppLimitGroup.AUTO_ADD_ALL_NEW -> true
                    else -> {
                        val targetCategory = AppManager.autoAddModeToCategory(group.autoAddMode)
                        targetCategory != null && appCategory == targetCategory
                    }
                }

                if (shouldAdd) {
                    val updatedApps = group.apps + AppInGroup(appName, packageName, packageName)
                    val updatedEntity = group.copy(apps = updatedApps).toEntity()
                    dao.updateAppLimitGroup(updatedEntity)
                }
            }

            // Ensure the service is running if we may have added apps to active groups.
            BackgroundChecker.applyDesiredServiceState(
                context,
                dao.getActiveGroupCount() > 0
            )
        }
    }

    companion object {
        const val ACTION_SERVICE_WATCHDOG = "com.juliacai.apptick.ACTION_SERVICE_WATCHDOG"
    }
}
