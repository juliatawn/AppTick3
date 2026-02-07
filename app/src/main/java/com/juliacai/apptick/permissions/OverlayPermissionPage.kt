package com.juliacai.apptick.permissions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class OverlayPermissionPage : AppCompatActivity() {

    private var isPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OverlayPermissionScreen(
                onGoToSettingsClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                },
                onBackClick = { onBackPressedDispatcher.onBackPressed() },
                isPermissionGranted = isPermissionGranted
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isPermissionGranted = Settings.canDrawOverlays(this)
        if (isPermissionGranted) {
            Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onBackPressed() {
        if (isPermissionGranted) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "Please grant overlay permission to continue", Toast.LENGTH_SHORT).show()
        }
    }
}
