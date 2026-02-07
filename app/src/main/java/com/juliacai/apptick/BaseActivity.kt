package com.juliacai.apptick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

open class BaseActivity : AppCompatActivity() {

    protected lateinit var sharedPreferences: SharedPreferences

    private val colorChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "COLORS_CHANGED") {
                recreate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences("AppTickPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean(PREF_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        val filter = IntentFilter("COLORS_CHANGED")
        registerReceiver(colorChangeReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(colorChangeReceiver)
    }

    companion object {
        const val PREF_PRIMARY_COLOR = "primary_color"
        const val PREF_PRIMARY_DARK_COLOR = "primary_dark_color"
        const val PREF_ACCENT_COLOR = "accent_color"
        const val PREF_DARK_MODE = "dark_mode"
    }
}
