package com.juliacai.apptick.premiumMode

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockdownModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val lockdownEnd = prefs.getLong("lockdown_end_time", 0L)
        val lockdownEnabled = prefs.getBoolean("lockdown_enabled", false)
        val status = if (lockdownEnabled && lockdownEnd > System.currentTimeMillis()) {
            "Lockdown active until ${
                SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(Date(lockdownEnd))
            }"
        } else {
            "Lockdown is currently disabled."
        }

        setContent {
            LockdownModeScreen(
                statusText = status,
                oneTimeWeeklyChange = prefs.getBoolean("lockdown_one_time_change", false),
                onOneTimeWeeklyChangeToggled = { enabled ->
                    prefs.edit {
                        putBoolean("lockdown_one_time_change", enabled)
                        if (enabled) {
                            putInt("lockdown_weekly_day", 1)
                            putInt("lockdown_weekly_hour", 9)
                            putInt("lockdown_weekly_minute", 0)
                        } else {
                            remove("lockdown_weekly_day")
                            remove("lockdown_weekly_hour")
                            remove("lockdown_weekly_minute")
                            remove("lockdown_weekly_used_key")
                        }
                    }
                },
                onConfigureEndTimeClick = {
                    startActivity(Intent(this, LockdownTimeActivity::class.java))
                },
                onDisableLockdownClick = {
                    prefs.edit {
                        putBoolean("lockdown_enabled", false)
                        remove("lockdown_end_time")
                        remove("lockdown_weekly_day")
                        remove("lockdown_weekly_hour")
                        remove("lockdown_weekly_minute")
                        remove("lockdown_weekly_used_key")
                    }
                    finish()
                },
                onBackClick = { finish() }
            )
        }
    }
}
