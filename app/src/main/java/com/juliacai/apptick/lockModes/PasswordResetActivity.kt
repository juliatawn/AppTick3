package com.juliacai.apptick.lockModes

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

class PasswordResetActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val resetMode = intent.getStringExtra("reset_mode") ?: "password"

        setContent {
            PasswordResetScreen(prefs = prefs, resetMode = resetMode) {
                finish()
            }
        }
    }
}

@Composable
fun PasswordResetScreen(prefs: SharedPreferences, resetMode: String, onPasswordReset: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf(TextFieldValue("")) }
    val isSecurityKeyReset = resetMode == "security_key"
    val title = if (isSecurityKeyReset) "Reset Security Key" else "Reset Password"

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Recovery Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Enter your recovery email address to receive password reset instructions.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        Button(
            onClick = {
                val recoveryEmail = if (isSecurityKeyReset) {
                    prefs.getString("recovery_email_security_key", null)
                } else {
                    prefs.getString("recovery_email", null)
                }

                if (email.text.isEmpty()) {
                    Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (recoveryEmail == null) {
                    Toast.makeText(context, "No recovery email has been set up.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                if (email.text.equals(recoveryEmail, ignoreCase = true)) {
                    if (isSecurityKeyReset) {
                        prefs.edit {
                            remove("security_key_value")
                            remove("recovery_email_security_key")
                            putBoolean("security_key_enabled", false)
                            putBoolean("securityKeyUnlocked", false)
                        }
                        Toast.makeText(context, "Security key has been reset.", Toast.LENGTH_LONG).show()
                    } else {
                        prefs.edit {
                            remove("password")
                            putBoolean("locked", false)
                            putBoolean("passUnlocked", false)
                        }
                        Toast.makeText(context, "Password has been reset. You can now set a new one.", Toast.LENGTH_LONG).show()
                    }
                    onPasswordReset()
                } else {
                    Toast.makeText(context, "The email does not match the recovery email.", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Reset Link")
        }
    }
}
