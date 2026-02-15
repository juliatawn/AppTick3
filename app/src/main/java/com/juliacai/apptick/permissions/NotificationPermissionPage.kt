package com.juliacai.apptick.permissions

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.juliacai.apptick.MainActivity

class NotificationPermissionPage : AppCompatActivity() {

    private var isPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isPermissionGranted) {
                LaunchedEffect(Unit) {
                    val intent = Intent(this@NotificationPermissionPage, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            } else {
                NotificationPermissionScreen(
                    onGoToSettingsClick = { 
                        val intent = Intent().apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            } else {
                                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                                putExtra("app_package", packageName)
                                putExtra("app_uid", applicationInfo.uid)
                            }
                        }
                        startActivity(intent)
                    },
                    onBackClick = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isPermissionGranted = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    override fun onBackPressed() {
        if (isPermissionGranted) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "Please grant notification permission to continue", Toast.LENGTH_SHORT).show()
        }
    }
}