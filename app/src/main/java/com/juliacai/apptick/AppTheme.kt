package com.juliacai.apptick

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity

object AppTheme {
    private const val PREFS_NAME = "groupPrefs"
    private const val KEY_PRIMARY_COLOR = "custom_primary_color"
    private const val KEY_ACCENT_COLOR = "custom_accent_color"
    private const val KEY_BACKGROUND_COLOR = "custom_background_color"
    private const val KEY_ICON_COLOR = "custom_icon_color"

    fun applyTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val primaryColor = prefs.getInt(KEY_PRIMARY_COLOR, Color.parseColor("#3949AB"))
        val backgroundColor = prefs.getInt(KEY_BACKGROUND_COLOR, Color.WHITE)

        if (context is AppCompatActivity) {
            context.window.decorView.setBackgroundColor(backgroundColor)
            context.window.statusBarColor = primaryColor
            context.window.navigationBarColor = primaryColor
        }
    }

    fun getPrimaryColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PRIMARY_COLOR, Color.parseColor("#3949AB"))
    }

    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACCENT_COLOR, Color.parseColor("#FF4081"))
    }

    fun getBackgroundColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BACKGROUND_COLOR, Color.WHITE)
    }

    fun getIconColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ICON_COLOR, Color.WHITE)
    }
}
