package com.juliacai.apptick

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt

object AppTheme {
    private const val PREFS_NAME = "groupPrefs"
    private const val KEY_PRIMARY_COLOR = "custom_primary_color"
    private const val KEY_ACCENT_COLOR = "custom_accent_color"
    private const val KEY_BACKGROUND_COLOR = "custom_background_color"
    private const val KEY_ICON_COLOR = "custom_icon_color"
    private const val KEY_APP_ICON_COLOR_MODE = "app_icon_color_mode"
    private const val DEFAULT_PRIMARY_COLOR = "#3949AB"

    fun applyTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customModeEnabled = ThemeModeManager.isCustomColorModeEnabled(context)
        val primaryColor = if (customModeEnabled) {
            prefs.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR.toColorInt())
        } else {
            DEFAULT_PRIMARY_COLOR.toColorInt()
        }
        val backgroundColor = if (customModeEnabled) {
            prefs.getInt(KEY_BACKGROUND_COLOR, Color.WHITE)
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
        if (!ThemeModeManager.isCustomColorModeEnabled(context)) {
            return DEFAULT_PRIMARY_COLOR.toColorInt()
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR.toColorInt())
    }

    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACCENT_COLOR, "#FF4081".toColorInt())
    }

    fun getBackgroundColor(context: Context): Int {
        if (!ThemeModeManager.isCustomColorModeEnabled(context)) {
            return if (isSystemDarkMode(context)) Color.BLACK else Color.WHITE
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_BACKGROUND_COLOR)) {
            return prefs.getInt(KEY_BACKGROUND_COLOR, Color.WHITE)
        }
        return if (isSystemDarkMode(context)) Color.BLACK else Color.WHITE
    }

    fun getIconColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val premiumEnabled = prefs.getBoolean("premium", false)
        val iconColorMode = prefs.getString(KEY_APP_ICON_COLOR_MODE, "system") ?: "system"
        val useCustomIconColor =
            ThemeModeManager.isCustomColorModeEnabled(context) && premiumEnabled && iconColorMode == "custom"

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

    private fun isSystemDarkMode(context: Context): Boolean {
        val currentNightMode =
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
