package com.juliacai.apptick.permissions

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPermissionGranted) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    Toast.makeText(
                        this@NotificationPermissionPage,
                        "Please grant notification permission to continue",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

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
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isPermissionGranted = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }
}
