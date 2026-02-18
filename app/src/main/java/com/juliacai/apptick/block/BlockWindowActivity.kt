package com.juliacai.apptick.block

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.ThemeModeManager

class BlockWindowActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        AppTheme.applyTheme(this)
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
        val blockReason = intent.getStringExtra("block_reason")
        val appTimeSpent = intent.getLongExtra("app_time_spent", 0)
        val groupTimeSpent = intent.getLongExtra("group_time_spent", 0)
        val timeLimitMinutes = intent.getIntExtra("time_limit_minutes", 0)
        val limitEach = intent.getBooleanExtra("limit_each", false)
        val useTimeRange = intent.getBooleanExtra("use_time_range", false)
        val blockOutsideTimeRange = intent.getBooleanExtra("block_outside_time_range", false)
        val blockedForOutsideRange = intent.getBooleanExtra("blocked_for_outside_range", false)
        val resolvedBlockReason = blockReason?.takeIf { it.isNotBlank() } ?: when {
            blockedForOutsideRange -> "Outside configured time range"
            timeLimitMinutes <= 0 -> "Used up time limit"
            else -> "Out of Time"
        }
        val isPremium = prefs.getBoolean("premium", false)
        val primaryColor = AppTheme.getPrimaryColor(this)
        val backgroundColor = AppTheme.getBackgroundColor(this)
        val cardColor = AppTheme.getCardColor(this)
        val iconColor = AppTheme.getIconColor(this)

        val composePrimary = Color(primaryColor)
        val composeBackground = Color(backgroundColor)
        val composeCard = Color(cardColor)
        val composeIconColor = Color(iconColor)

        val customColorModeEnabled = ThemeModeManager.isCustomColorModeEnabled(this)
        val isSystemDark = AppTheme.isSystemDarkMode(this)

        val colorScheme = if (customColorModeEnabled) {
            val useDarkScheme = composeBackground.luminance() < 0.4f
            if (useDarkScheme) {
                darkColorScheme(
                    primary = composePrimary,
                    background = composeBackground,
                    surface = composeCard,
                    primaryContainer = composePrimary.copy(alpha = 0.24f),
                    onPrimary = composeIconColor,
                    onBackground = composeIconColor,
                    onSurface = composeIconColor,
                    onPrimaryContainer = composeIconColor
                ).copy(
                    surfaceVariant = composeCard,
                    surfaceContainerLowest = composeCard,
                    surfaceContainerLow = composeCard,
                    surfaceContainer = composeCard,
                    surfaceContainerHigh = composeCard,
                    surfaceContainerHighest = composeCard
                )
            } else {
                lightColorScheme(
                    primary = composePrimary,
                    background = composeBackground,
                    surface = composeCard,
                    primaryContainer = composePrimary.copy(alpha = 0.16f),
                    onPrimary = composeIconColor,
                    onBackground = composeIconColor,
                    onSurface = composeIconColor,
                    onPrimaryContainer = composeIconColor
                ).copy(
                    surfaceVariant = composeCard,
                    surfaceContainerLowest = composeCard,
                    surfaceContainerLow = composeCard,
                    surfaceContainer = composeCard,
                    surfaceContainerHigh = composeCard,
                    surfaceContainerHighest = composeCard
                )
            }
        } else if (isSystemDark) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }

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
                    isPremium = isPremium,
                    primaryColor = composePrimary,
                    backgroundColor = composeBackground
                )
            }
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
