package com.juliacai.apptick.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.graphics.drawable.toBitmap
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.backgroundProcesses.FloatingBubbleService

class BlockWindowActivity : AppCompatActivity() {

    companion object {
        const val ACTION_DISMISS_BLOCK = "com.juliacai.apptick.DISMISS_BLOCK_SCREEN"
    }

    private lateinit var prefs: SharedPreferences

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Block back press so the user can't dismiss the block screen
        // and so async BACK actions from floating window dismissal don't close it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing — block screen should not be dismissible via back
            }
        })

        registerReceiver(
            dismissReceiver,
            IntentFilter(ACTION_DISMISS_BLOCK),
            Context.RECEIVER_NOT_EXPORTED
        )

        hideFloatingBubble()
        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        AppTheme.applyTheme(this)
        updateContent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hideFloatingBubble()
        updateContent(intent)
    }

    private fun hideFloatingBubble() {
        try {
            startService(FloatingBubbleService.hideIntent(this))
        } catch (_: Exception) {}
    }

    private fun updateContent(intent: Intent?) {
        val appPackage = intent?.getStringExtra("app_package") ?: return
        val appName = intent.getStringExtra("app_name") ?: ""
        val groupName = intent.getStringExtra("group_name") ?: ""
        val blockReason = intent.getStringExtra("block_reason")
        val appTimeSpent = intent.getLongExtra("app_time_spent", 0)
        val groupTimeSpent = intent.getLongExtra("group_time_spent", 0)
        val timeLimitMinutes = intent.getIntExtra("time_limit_minutes", 0)
        val limitEach = intent.getBooleanExtra("limit_each", false)
        val useTimeRange = intent.getBooleanExtra("use_time_range", false)
        val blockOutsideTimeRange = intent.getBooleanExtra("block_outside_time_range", false)
        val blockedForOutsideRange = intent.getBooleanExtra("blocked_for_outside_range", false)
        val nextResetTime = intent.getLongExtra("next_reset_time", 0L)
        val resolvedBlockReason = blockReason?.takeIf { it.isNotBlank() } ?: when {
            blockedForOutsideRange -> "Outside configured time range"
            timeLimitMinutes <= 0 -> "Used up time limit"
            else -> "Out of Time"
        }
        val isPremium = prefs.getBoolean("premium", false)
        val palette = AppTheme.currentPalette(this)

        val composePrimary = Color(palette.primary)
        val composeBackground = Color(palette.background)
        val colorScheme = AppTheme.colorSchemeFromPalette(palette)

        val appIcon = getAppIcon(appPackage)

        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                val iconPainter = appIcon?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
                BlockWindowScreen(
                    appName = appName,
                    appIcon = iconPainter,
                    groupName = groupName,
                    blockReason = resolvedBlockReason,
                    appTimeSpent = appTimeSpent,
                    groupTimeSpent = groupTimeSpent,
                    timeLimitMinutes = timeLimitMinutes,
                    limitEach = limitEach,
                    useTimeRange = useTimeRange,
                    blockOutsideTimeRange = blockOutsideTimeRange,
                    blockedForOutsideRange = blockedForOutsideRange,
                    nextResetTime = nextResetTime,
                    isPremium = isPremium,
                    primaryColor = composePrimary,
                    backgroundColor = composeBackground
                )
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(dismissReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
