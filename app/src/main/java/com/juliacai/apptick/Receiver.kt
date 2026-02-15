package com.juliacai.apptick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase

class Receiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action == null) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_SCREEN_ON -> {
                val pendingResult = goAsync()
                Thread {
                    try {
                        val activeGroupCount =
                            AppTickDatabase.getDatabase(context).appLimitGroupDao().getActiveGroupCountSync()
                        if (activeGroupCount > 0 || shouldKeepServiceForSettingsProtection(context)) {
                            BackgroundChecker.startServiceIfNotRunning(context)
                        }
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

    private fun shouldKeepServiceForSettingsProtection(context: Context): Boolean {
        val prefs = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("blockSettings", false)) return false
        val now = System.currentTimeMillis()
        val decision = LockPolicy.evaluateEditingLock(readLockState(prefs), now)
        if (decision.shouldClearExpiredLockdown) {
            prefs.edit {
                putBoolean("lockdown_enabled", false)
                remove("lockdown_end_time")
                remove("lockdown_weekly_day")
                remove("lockdown_weekly_hour")
                remove("lockdown_weekly_minute")
                remove("lockdown_one_time_change")
                remove("lockdown_weekly_used_key")
            }
        }
        return decision.isLocked
    }

    private fun readLockState(prefs: android.content.SharedPreferences): LockState {
        return LockState(
            hasPassword = !prefs.getString("password", null).isNullOrBlank(),
            hasSecurityKey = prefs.getBoolean("security_key_enabled", false),
            passwordUnlocked = prefs.getBoolean("passUnlocked", false),
            securityKeyUnlocked = prefs.getBoolean("securityKeyUnlocked", false),
            lockdownEnabled = prefs.getBoolean("lockdown_enabled", false),
            lockdownEndTimeMillis = prefs.getLong("lockdown_end_time", 0L),
            lockdownOneTimeWeeklyChange = prefs.getBoolean("lockdown_one_time_change", false),
            lockdownWeeklyDayMondayOne = prefs.getInt("lockdown_weekly_day", -1),
            lockdownWeeklyHour = prefs.getInt("lockdown_weekly_hour", -1),
            lockdownWeeklyMinute = prefs.getInt("lockdown_weekly_minute", -1),
            lockdownWeeklyUsedKey = prefs.getString("lockdown_weekly_used_key", null)
        )
    }
}
