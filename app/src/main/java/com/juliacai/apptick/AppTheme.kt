package com.juliacai.apptick

import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt

internal fun shouldUseCustomColorMode(isPremium: Boolean, customColorModeEnabled: Boolean): Boolean {
    return isPremium && customColorModeEnabled
}

object AppTheme {
    private const val PREFS_NAME = "groupPrefs"
    private const val KEY_PRIMARY_COLOR = "custom_primary_color"
    private const val KEY_ACCENT_COLOR = "custom_accent_color"
    private const val KEY_BACKGROUND_COLOR = "custom_background_color"
    private const val KEY_ICON_COLOR = "custom_icon_color"
    private const val KEY_APP_ICON_COLOR_MODE = "app_icon_color_mode"
    private const val DEFAULT_PRIMARY_COLOR = "#3949AB" // Keep your purple default.

    fun applyTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val premiumEnabled = prefs.getBoolean("premium", false)
        val customModeEnabled =
            shouldUseCustomColorMode(premiumEnabled, ThemeModeManager.isCustomColorModeEnabled(context))

        val primaryColor = if (customModeEnabled) {
            prefs.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR.toColorInt())
        } else {
            DEFAULT_PRIMARY_COLOR.toColorInt()
        }

        val backgroundColor = if (customModeEnabled) {
            prefs.getInt(KEY_BACKGROUND_COLOR, if (isSystemDarkMode(context)) Color.BLACK else Color.WHITE)
        } else {
            if (isSystemDarkMode(context)) Color.BLACK else Color.WHITE
        }

        if (context is AppCompatActivity) {
            context.window.decorView.setBackgroundColor(backgroundColor)
            context.window.statusBarColor = primaryColor
            context.window.navigationBarColor = primaryColor
        }
    }

    fun getPrimaryColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val premiumEnabled = prefs.getBoolean("premium", false)
        val customModeEnabled =
            shouldUseCustomColorMode(premiumEnabled, ThemeModeManager.isCustomColorModeEnabled(context))

        if (!customModeEnabled) return DEFAULT_PRIMARY_COLOR.toColorInt()
        return prefs.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR.toColorInt())
    }

    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACCENT_COLOR, "#FF4081".toColorInt())
    }

    fun getBackgroundColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val premiumEnabled = prefs.getBoolean("premium", false)
        val customModeEnabled =
            shouldUseCustomColorMode(premiumEnabled, ThemeModeManager.isCustomColorModeEnabled(context))

        if (!customModeEnabled) {
            return if (isSystemDarkMode(context)) Color.BLACK else Color.WHITE
        }

        if (prefs.contains(KEY_BACKGROUND_COLOR)) {
            return prefs.getInt(KEY_BACKGROUND_COLOR, if (isSystemDarkMode(context)) Color.BLACK else Color.WHITE)
        }

        return if (isSystemDarkMode(context)) Color.BLACK else Color.WHITE
    }

    fun getIconColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val premiumEnabled = prefs.getBoolean("premium", false)
        val iconColorMode = prefs.getString(KEY_APP_ICON_COLOR_MODE, "system") ?: "system"
        val useCustomIconColor =
            shouldUseCustomColorMode(premiumEnabled, ThemeModeManager.isCustomColorModeEnabled(context)) &&
                iconColorMode == "custom"

        if (useCustomIconColor) {
            return prefs.getInt(KEY_ICON_COLOR, Color.BLACK)
        }

        val background = getBackgroundColor(context)
        return if (androidx.core.graphics.ColorUtils.calculateLuminance(background) > 0.5) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    fun isSystemDarkMode(context: Context): Boolean {
        val currentNightMode =
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isSystemDark = isSystemInDarkTheme()
    val isPremium = prefs.getBoolean("premium", false)
    val customColorModeEnabled =
        shouldUseCustomColorMode(isPremium, ThemeModeManager.isCustomColorModeEnabled(context))

    val savedPrimaryColor = prefs.getInt(KEY_PRIMARY_COLOR, 0)
    val savedBackgroundColor = prefs.getInt(KEY_BACKGROUND_COLOR, 0)
    val savedIconColor = prefs.getInt(KEY_ICON_COLOR, 0)
    val appIconColorMode = prefs.getString(KEY_APP_ICON_COLOR_MODE, "system") ?: "system"

    val composePrimary =
        if (savedPrimaryColor != 0) ComposeColor(savedPrimaryColor)
        else ComposeColor(0xFF3949AB)

    val defaultBackground = if (isSystemDark) ComposeColor.Black else ComposeColor.White
    val composeBackground = if (savedBackgroundColor != 0) ComposeColor(savedBackgroundColor) else defaultBackground

    val systemThemeIconColor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isSystemDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
        } else {
            null
        }

    val fallbackIconColor =
        if (androidx.core.graphics.ColorUtils.calculateLuminance(composeBackground.toArgb()) > 0.5) {
            ComposeColor.Black
        } else {
            ComposeColor.White
        }

    val composeIconColor =
        if (isPremium && appIconColorMode == "custom" && savedIconColor != 0) {
            ComposeColor(savedIconColor)
        } else {
            systemThemeIconColor?.takeIf { customColorModeEnabled } ?: fallbackIconColor
        }

    val colorScheme = if (customColorModeEnabled) {
        val useDarkScheme = composeBackground.luminance() < 0.4f
        if (useDarkScheme) {
            darkColorScheme(
                primary = composePrimary,
                background = composeBackground,
                surface = composeBackground,
                primaryContainer = composePrimary.copy(alpha = 0.24f),
                onPrimary = composeIconColor,
                onBackground = composeIconColor,
                onSurface = composeIconColor,
                onPrimaryContainer = composeIconColor
            )
        } else {
            lightColorScheme(
                primary = composePrimary,
                background = composeBackground,
                surface = composeBackground,
                primaryContainer = composePrimary.copy(alpha = 0.16f),
                onPrimary = composeIconColor,
                onBackground = composeIconColor,
                onSurface = composeIconColor,
                onPrimaryContainer = composeIconColor
            )
        }
    } else if (isSystemDark) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

private const val PREFS_NAME = "groupPrefs"
private const val KEY_PRIMARY_COLOR = "custom_primary_color"
private const val KEY_BACKGROUND_COLOR = "custom_background_color"
private const val KEY_ICON_COLOR = "custom_icon_color"
private const val KEY_APP_ICON_COLOR_MODE = "app_icon_color_mode"
