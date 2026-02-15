package com.juliacai.apptick

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeModeManager {
    private const val PREFS_UI = "AppTickPrefs"
    private const val PREFS_LEGACY = "groupPrefs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_CUSTOM_COLOR_MODE = "custom_color_mode"
    private const val KEY_PREMIUM = "premium"

    fun isCustomColorModeEnabled(context: Context): Boolean {
        if (!isPremium(context)) return false
        return context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
            .getBoolean(KEY_CUSTOM_COLOR_MODE, false)
    }

    fun isDarkModeEnabled(context: Context): Boolean {
        if (!isPremium(context)) return false
        if (isCustomColorModeEnabled(context)) return false

        val uiPrefs = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        if (uiPrefs.contains(KEY_DARK_MODE)) {
            return uiPrefs.getBoolean(KEY_DARK_MODE, false)
        }

        // Backward compatibility for older builds that wrote this key in groupPrefs.
        val legacyPrefs = context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)
        val legacyValue = legacyPrefs.getBoolean(KEY_DARK_MODE, false)
        uiPrefs.edit { putBoolean(KEY_DARK_MODE, legacyValue) }
        return legacyValue
    }

    fun persistDarkMode(context: Context, enabled: Boolean) {
        if (!isPremium(context)) {
            context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_DARK_MODE, false)
                putBoolean(KEY_CUSTOM_COLOR_MODE, false)
            }
            return
        }
        context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_DARK_MODE, enabled)
            if (enabled) putBoolean(KEY_CUSTOM_COLOR_MODE, false)
        }
    }

    fun persistCustomColorMode(context: Context, enabled: Boolean) {
        if (!isPremium(context)) {
            context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_CUSTOM_COLOR_MODE, false)
                putBoolean(KEY_DARK_MODE, false)
            }
            return
        }
        context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CUSTOM_COLOR_MODE, enabled)
            if (enabled) putBoolean(KEY_DARK_MODE, false)
        }
    }

    fun apply(context: Context) {
        val customColorMode = isCustomColorModeEnabled(context)
        val desiredMode = resolveNightMode(customColorMode, isDarkModeEnabled(context))

        if (AppCompatDelegate.getDefaultNightMode() != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }
    }

    internal fun resolveNightMode(customColorMode: Boolean, darkMode: Boolean): Int {
        return if (customColorMode) AppCompatDelegate.MODE_NIGHT_NO
        else if (darkMode) AppCompatDelegate.MODE_NIGHT_YES
        else AppCompatDelegate.MODE_NIGHT_NO
    }

    private fun isPremium(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREMIUM, false)
    }
}
