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
                putString("active_lock_mode", "NONE")
                remove("lockdown_end_time")
                remove("lockdown_weekly_used_key")
            }
        }
        return decision.isLocked
    }

    private fun readLockState(prefs: android.content.SharedPreferences): LockState {
        val activeMode = try {
            LockMode.valueOf(prefs.getString("active_lock_mode", "NONE") ?: "NONE")
        } catch (_: Exception) {
            LockMode.NONE
        }
        val lockdownType = try {
            LockdownType.valueOf(prefs.getString("lockdown_type", "ONE_TIME") ?: "ONE_TIME")
        } catch (_: Exception) {
            LockdownType.ONE_TIME
        }
        val recurringDays = prefs.getString("lockdown_recurring_days", "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 1..7 }
            .distinct()
            .sorted()

        return LockState(
            activeLockMode = activeMode,
            passwordUnlocked = prefs.getBoolean("passUnlocked", false),
            securityKeyUnlocked = prefs.getBoolean("securityKeyUnlocked", false),
            lockdownType = lockdownType,
            lockdownEndTimeMillis = prefs.getLong("lockdown_end_time", 0L),
            lockdownRecurringDays = recurringDays,
            lockdownRecurringUsedKey = prefs.getString("lockdown_weekly_used_key", null)
        )
    }
}
