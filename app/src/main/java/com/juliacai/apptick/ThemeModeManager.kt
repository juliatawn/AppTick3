package com.juliacai.apptick

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeModeManager {
    private const val PREFS = "groupPrefs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_CUSTOM_COLOR_MODE = "custom_color_mode"

    fun apply(context: Context) {
        val mode = if (isDarkModeEnabled(context)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isDarkModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)
    }

    fun persistDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
    }

    fun isCustomColorModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CUSTOM_COLOR_MODE, false)
    }

    fun persistCustomColorMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CUSTOM_COLOR_MODE, enabled)
            .apply()
    }
}
