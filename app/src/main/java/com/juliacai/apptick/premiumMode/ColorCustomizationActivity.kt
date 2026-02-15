package com.juliacai.apptick.premiumMode

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.juliacai.apptick.BaseActivity
import com.juliacai.apptick.ThemeModeManager

class ColorCustomizationActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("AppTickPrefs", MODE_PRIVATE)

        setContent {
            ColorCustomizationScreen(
                onColorSelected = { color ->
                    prefs.edit {
                        putInt(BaseActivity.Companion.PREF_PRIMARY_COLOR, color.toArgb())
                    }
                    sendBroadcast(Intent("COLORS_CHANGED").setPackage(packageName))
                },
                onDarkModeChanged = { isDarkMode ->
                    ThemeModeManager.persistDarkMode(this@ColorCustomizationActivity, isDarkMode)
                    ThemeModeManager.apply(this@ColorCustomizationActivity)
                }
            )
        }
    }
}
