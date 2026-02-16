package com.juliacai.apptick.lockModes

import android.content.SharedPreferences
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.MainActivity

class EnterSecurityKeyActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)

        AppTheme.applyTheme(this)

        setContent {
            AppTheme {
                EnterPasswordScreen(
                    onPasswordSubmit = { verifySecurityKey(it) },
                    onBiometricClick = { },
                    onUsbKeyClick = { },
                    onForgotPasswordClick = {
                        startActivity(
                            Intent(this, PasswordResetActivity::class.java)
                                .putExtra("reset_mode", "security_key")
                        )
                    },
                    isBiometricVisible = false,
                    isUsbKeyVisible = false
                )
            }
        }
    }

    private fun verifySecurityKey(enteredKey: String) {
        val securityKey = prefs.getString("security_key_value", null)
        if (securityKey.isNullOrBlank()) {
            Toast.makeText(this, "No security key configured", Toast.LENGTH_SHORT).show()
            return
        }

        if (enteredKey == securityKey) {
            prefs.edit {
                putBoolean("securityKeyUnlocked", true)
                putBoolean("passUnlocked", false)
            }
            Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
            val openLockModes = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, false)
            if (openLockModes) {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, true)
                    }
                )
            }
            finish()
        } else {
            Toast.makeText(this, "Incorrect security key", Toast.LENGTH_SHORT).show()
        }
    }
}
