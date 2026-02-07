package com.juliacai.apptick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker

class Receiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_SCREEN_ON -> {
                BackgroundChecker.startServiceIfNotRunning(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                val prefs = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
                prefs.edit { putBoolean("screenOn", false) }
            }
        }
    }
}
