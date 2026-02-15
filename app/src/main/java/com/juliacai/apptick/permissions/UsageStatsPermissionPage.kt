package com.juliacai.apptick.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class UsageStatsPermissionPage : AppCompatActivity() {

    private var isPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UsageStatsPermissionScreen(
                onGoToSettingsClick = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    startActivity(intent)
                },
                onNextClick = {moveToNextPermission()},
                isPermissionGranted = isPermissionGranted
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isPermissionGranted = hasUsageStatsPermission()
        if (isPermissionGranted) {
            Toast.makeText(this, "Usage access permission granted", Toast.LENGTH_SHORT).show()
            moveToNextPermission()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun moveToNextPermission() {
        val intent = Intent(this, NotificationPermissionPage::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (!isPermissionGranted) {
            Toast.makeText(this, "Please grant usage access permission to continue", Toast.LENGTH_SHORT).show()
        } else {
             super.onBackPressed()
        }
    }
}
