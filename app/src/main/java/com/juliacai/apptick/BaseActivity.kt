package com.juliacai.apptick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

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
        enableEdgeToEdge()
        sharedPreferences = getSharedPreferences("AppTickPrefs", MODE_PRIVATE)
        ThemeModeManager.apply(this)

        super.onCreate(savedInstanceState)

        val filter = IntentFilter("COLORS_CHANGED")
        ContextCompat.registerReceiver(
            this,
            colorChangeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
