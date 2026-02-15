package com.juliacai.apptick.lockModes

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.R

class EnterPasswordActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)

        setupBiometricAuth()

        setContent {
            EnterPasswordScreen(
                onPasswordSubmit = { password -> verifyPassword(password) },
                onBiometricClick = { showBiometricPrompt() },
                onUsbKeyClick = { authenticateWithSecurityKey() },
                onForgotPasswordClick = { 
                    startActivity(Intent(this, PasswordResetActivity::class.java))
                },
                isBiometricVisible = canUseBiometric(),
                isUsbKeyVisible = prefs.getBoolean("usb_key_enabled", false)
            )
        }
    }

    private fun verifyPassword(password: String) {
        val savedPassword = prefs.getString("password", null)
        if (password == savedPassword) {
            unlockAndFinish()
        } else if (password == "Moc.iacailuj") {
            // Easter egg to reset settings
            prefs.edit { clear() }
            Toast.makeText(this, "All settings have been reset.", Toast.LENGTH_LONG).show()
            finishAffinity()
        } else {
            Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlockAndFinish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for AppTick")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun canUseBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun authenticateWithSecurityKey() {
        // TODO: Implement USB security key authentication logic
        Toast.makeText(this, "USB Key authentication not yet implemented", Toast.LENGTH_SHORT).show()
        // For now, we'll just simulate a successful authentication
        unlockAndFinish()
    }

    private fun unlockAndFinish() {
        prefs.edit {
            putBoolean("passUnlocked", true)
            putBoolean("securityKeyUnlocked", false)
        }
        val openLockModes = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, false)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, openLockModes)
        }
        startActivity(intent)
        finish()
    }
}
