package com.juliacai.apptick.block

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
import com.juliacai.apptick.AppTheme

class BlockWindowActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        updateContent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        updateContent(intent)
    }

    private fun updateContent(intent: Intent?) {
        val appPackage = intent?.getStringExtra("app_package") ?: return
        val appName = intent.getStringExtra("app_name") ?: ""
        val groupName = intent.getStringExtra("group_name") ?: ""
        val timeSpent = intent.getLongExtra("time_spent", 0)
        val isPremium = prefs.getBoolean("premium", false)
        val primaryColor = AppTheme.getPrimaryColor(this)
        val backgroundColor = AppTheme.getBackgroundColor(this)

        val appIcon = getAppIcon(appPackage)

        setContent {
            val iconPainter = appIcon?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
            BlockWindowScreen(
                appName = appName,
                appIcon = iconPainter,
                groupName = groupName,
                timeSpent = timeSpent,
                isPremium = isPremium,
                primaryColor = androidx.compose.ui.graphics.Color(primaryColor),
                backgroundColor = androidx.compose.ui.graphics.Color(backgroundColor)
            )
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
