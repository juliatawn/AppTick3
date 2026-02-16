package com.juliacai.apptick.lockModes

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.juliacai.apptick.AppTheme

class RecoveryEmailSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val initialEmail = prefs.getString("recovery_email", "") ?: ""

        AppTheme.applyTheme(this)

        setContent {
            AppTheme {
                RecoveryEmailSetupScreen(initialEmail = initialEmail) { email ->
                    if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        prefs.edit { putString("recovery_email", email) }
                        Toast.makeText(this, "Recovery email saved", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Invalid email address", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
