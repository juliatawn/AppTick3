package com.juliacai.apptick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.LegacyDataMigrator
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
        }
    }

    companion object {
        const val ACTION_SERVICE_WATCHDOG = "com.juliacai.apptick.ACTION_SERVICE_WATCHDOG"
    }
}
