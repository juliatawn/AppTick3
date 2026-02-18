package com.juliacai.apptick

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                onBackClick = { finish() },
                onCustomizeColors = {},
                onUpgradeToPremium = {},
                onOpenPremiumModeInfo = {},
                onOpenAppLimitBackup = {}
            )
        }
    }
}
