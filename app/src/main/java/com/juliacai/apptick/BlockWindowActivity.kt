package com.juliacai.apptick

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.graphics.drawable.toBitmap

class BlockWindowActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        updateContent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        updateContent(intent)
    }

    private fun updateContent(intent: Intent?) {
        val appPackName = intent?.getStringExtra("appName") ?: return
        val timeUsed = intent.getStringExtra("timeUsed") ?: ""
        val resetTime = intent.getStringExtra("timeReset") ?: ""
        val isPremium = prefs.getBoolean("premium", false)

        val appName = getAppName(appPackName)
        val appIcon = getAppIcon(appPackName)

        setContent {
            val iconPainter = appIcon?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
            BlockWindowScreen(
                appName = appName,
//                appIcon = iconPainter,
//                timeUsed = timeUsed,
//                resetTime = "Resets at $resetTime",
//                isPremium = isPremium
            )
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
