package com.juliacai.apptick.lockModes

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.juliacai.apptick.AppTheme

class RecoveryEmailSetupActivity : AppCompatActivity() {
    private var forceSetup: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        forceSetup = intent.getBooleanExtra(EXTRA_FORCE_SETUP, false)
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val initialEmail = prefs.getString("recovery_email", "") ?: ""

        if (forceSetup) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Toast.makeText(
                        this@RecoveryEmailSetupActivity,
                        "Recovery email is required to continue.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }

        AppTheme.applyTheme(this)

        setContent {
            AppTheme {
                RecoveryEmailSetupScreen(initialEmail = initialEmail) { email ->
                    if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        prefs.edit {
                            putString("recovery_email", email)
                            putBoolean("force_recovery_email_setup", false)
                        }
                        Toast.makeText(this, "Recovery email saved", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Invalid email address", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_FORCE_SETUP = "extra_force_recovery_email_setup"
    }
}
