package com.juliacai.apptick.lockModes

import android.content.Intent
import android.content.SharedPreferences
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.R

class EnterPasswordActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)

        AppTheme.applyTheme(this)

        setupBiometricAuth()

        setContent {
            AppTheme {
                EnterPasswordScreen(
                    onPasswordSubmit = { password -> verifyPassword(password) },
                    onCancelClick = { finish() },
                    onBiometricClick = { showBiometricPrompt() },
                    onUsbKeyClick = { authenticateWithSecurityKey() },
                    isBiometricVisible = isBiometricEnabledForPasswordMode() && canUseBiometric(),
                    isUsbKeyVisible = prefs.getBoolean("usb_key_enabled", false) &&
                        UsbSecurityKey.readRegisteredKey(prefs) != null
                )
            }
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

    private fun isBiometricEnabledForPasswordMode(): Boolean {
        return prefs.getBoolean("password_biometric_enabled", false)
    }

    private fun authenticateWithSecurityKey() {
        val registeredKey = UsbSecurityKey.readRegisteredKey(prefs)
        if (registeredKey == null) {
            Toast.makeText(this, "No USB security key configured", Toast.LENGTH_SHORT).show()
            return
        }
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val matching = UsbSecurityKey.findMatchingConnectedDevice(usbManager, registeredKey)
        if (matching == null) {
            Toast.makeText(this, "Registered USB key not detected", Toast.LENGTH_SHORT).show()
            return
        }
        unlockAndFinish()
    }

    private fun unlockAndFinish() {
        val isSettingsSessionUnlock = intent.getBooleanExtra(
            SettingsUnlockSession.EXTRA_SETTINGS_SESSION_UNLOCK,
            false
        )
        if (isSettingsSessionUnlock) {
            sendBroadcast(
                Intent(SettingsUnlockSession.ACTION_SETTINGS_SESSION_UNLOCKED).apply {
                    setPackage(packageName)
                    putExtra(SettingsUnlockSession.EXTRA_UNLOCK_MODE, SettingsUnlockSession.MODE_PASSWORD)
                }
            )
            finish()
            return
        }

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
