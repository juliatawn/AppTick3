package com.juliacai.apptick.permissions

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class UsagePermissionPage : AppCompatActivity() {

    private var isPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UsagePermissionScreen(
                onGoToSettingsClick = { 
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onBackClick = { onBackPressedDispatcher.onBackPressed() },
                isPermissionGranted = isPermissionGranted
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isPermissionGranted = hasUsagePermission()
        if (isPermissionGranted) {
            Toast.makeText(this, "Usage permission granted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onBackPressed() {
        if (isPermissionGranted) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "Please grant usage permission to continue", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
