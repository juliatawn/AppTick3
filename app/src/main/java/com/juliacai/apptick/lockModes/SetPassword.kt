package com.juliacai.apptick.lockModes

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.LockMode

class SetPassword : AppCompatActivity() {

    companion object {
        private const val PREF_PASSWORD_MODE_CONFIGURED = "password_mode_configured"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var biometricPrompt: BiometricPrompt
    private var biometricEnabled by mutableStateOf(false)
    private var usbSecurityKeyEnabled by mutableStateOf(false)
    private var isConfigurationEnabled by mutableStateOf(false)
    private var hasSavedConfiguration by mutableStateOf(false)

    private val usbSecurityKeySetupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val hasRegisteredKey = UsbSecurityKey.readRegisteredKey(prefs) != null
            usbSecurityKeyEnabled = result.resultCode == Activity.RESULT_OK && hasRegisteredKey
            if (!usbSecurityKeyEnabled) {
                Toast.makeText(this, "USB security key setup not completed", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)

        AppTheme.applyTheme(this)
        setupBiometricAuth()

        val isPasswordEnabled = prefs.getString("active_lock_mode", "NONE") == "PASSWORD"
        hasSavedConfiguration = isPasswordModeConfiguredNow()
        isConfigurationEnabled = isPasswordEnabled && hasSavedConfiguration
        biometricEnabled = if (isPasswordEnabled) {
            prefs.getBoolean("password_biometric_enabled", false)
        } else {
            false
        }
        usbSecurityKeyEnabled =
            prefs.getBoolean("usb_key_enabled", false) && UsbSecurityKey.readRegisteredKey(prefs) != null

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitRequest()
            }
        })

        setContent {
            AppTheme {
                SetPasswordScreen(
                    onSaveClick = { password, confirmPassword, enableBiometric, enableUsbKey ->
                        savePasswordAndFinish(
                            passFirst = password,
                            passSecond = confirmPassword,
                            enableBiometric = enableBiometric,
                            enableUsbKey = enableUsbKey
                        )
                    },
                    onCancelClick = { handleExitRequest() },
                    onOpenFamilyLinkClick = { openFamilyLinkSetup() },
                    onBiometricToggleRequest = { requestedEnabled ->
                        if (!canUseBiometric()) return@SetPasswordScreen
                        if (!requestedEnabled) {
                            biometricEnabled = false
                            return@SetPasswordScreen
                        }
                        showBiometricVerificationPrompt()
                    },
                    onUsbSecurityKeyToggleRequest = { requestedEnabled ->
                        if (!requestedEnabled) {
                            usbSecurityKeyEnabled = false
                            return@SetPasswordScreen
                        }
                        usbSecurityKeySetupLauncher.launch(
                            Intent(this, UsbSecurityKeySetupActivity::class.java)
                        )
                    },
                    onDisabledInteraction = { showEnablePasswordToggleDialog() },
                    isConfigurationEnabled = isConfigurationEnabled,
                    onConfigurationEnabledChange = { requestedEnabled ->
                        if (!requestedEnabled && activeLockMode() == LockMode.PASSWORD) {
                            disablePasswordMode()
                        } else {
                            isConfigurationEnabled = requestedEnabled
                        }
                    },
                    isPasswordEnabled = isPasswordEnabled,
                    activeLockMode = activeLockMode(),
                    biometricEnabled = biometricEnabled,
                    isBiometricSupported = canUseBiometric(),
                    usbSecurityKeyEnabled = usbSecurityKeyEnabled
                )
            }
        }
    }

    private fun savePasswordAndFinish(
        passFirst: String,
        passSecond: String,
        enableBiometric: Boolean,
        enableUsbKey: Boolean
    ) {
        val activeMode = activeLockMode()
        if (prefs.getBoolean("lockdown_prompt_after_unlock", false)) {
            showInfoDialog(
                "Lockdown Mode Pending",
                "Finish Lockdown mode first before enabling Password mode."
            )
            return
        }
        if (activeMode != LockMode.NONE && activeMode != LockMode.PASSWORD) {
            showInfoDialog(
                "Lock Mode Active",
                "Only one lock mode can be enabled at a time. Disable $activeMode first."
            )
            return
        }

        if (passFirst.isEmpty()) {
            showInfoDialog("Password", "Please enter a password.")
            return
        }

        if (passFirst != passSecond) {
            showInfoDialog("Password", "Passwords do not match.")
            return
        }

        if (enableUsbKey && UsbSecurityKey.readRegisteredKey(prefs) == null) {
            showInfoDialog(
                "USB Security Key",
                "Set up your USB security key first."
            )
            usbSecurityKeyEnabled = false
            return
        }

        prefs.edit {
            putString("password", passFirst)
            putString("active_lock_mode", "PASSWORD")
            putBoolean("security_key_enabled", false)
            putBoolean("useDeviceAdminUninstallProtection", false)
            putBoolean("password_biometric_enabled", enableBiometric)
            putBoolean("usb_key_enabled", enableUsbKey)
            putBoolean("blockMain", true)
            putBoolean("locked", true)
            putBoolean("passUnlocked", false)
            putBoolean("securityKeyUnlocked", false)
            putBoolean(PREF_PASSWORD_MODE_CONFIGURED, true)
        }
        hasSavedConfiguration = true

        Toast.makeText(this, "Password lock enabled", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun disablePasswordMode() {
        if (activeLockMode() != LockMode.PASSWORD) {
            showInfoDialog("Password Mode", "Password mode is already off.")
            return
        }
        prefs.edit {
            putString("active_lock_mode", "NONE")
            remove("password")
            putBoolean("locked", false)
            putBoolean("passUnlocked", true)
            putBoolean("password_biometric_enabled", false)
            putBoolean("usb_key_enabled", false)
            putBoolean("useDeviceAdminUninstallProtection", false)
            putBoolean(PREF_PASSWORD_MODE_CONFIGURED, false)
        }
        hasSavedConfiguration = false
        isConfigurationEnabled = false
        Toast.makeText(this, "Password lock disabled", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun handleExitRequest() {
        if (shouldWarnUnconfiguredExit()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Password Mode")
                .setMessage("Password Mode Not Configured, Disabling")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK") { _, _ ->
                    prefs.edit { putString("active_lock_mode", LockMode.NONE.name) }
                    finish()
                }
                .show()
            return
        }
        finish()
    }

    private fun isPasswordModeConfiguredNow(): Boolean {
        val storedPassword = prefs.getString("password", null)?.trim().orEmpty()
        val isPasswordModeActive = activeLockMode() == LockMode.PASSWORD
        return storedPassword.isNotEmpty() && isPasswordModeActive
    }

    private fun shouldWarnUnconfiguredExit(): Boolean {
        // Warn only when user intentionally enabled this page's configuration flow
        // but Password mode is not currently configured/saved.
        return isConfigurationEnabled && !isPasswordModeConfiguredNow()
    }

    private fun openFamilyLinkSetup() {
        val familySettingsIntent = Intent("android.settings.FAMILY_SETTINGS")
        if (familySettingsIntent.resolveActivity(packageManager) != null) {
            startActivity(familySettingsIntent)
            return
        }
        val familyLinkPackage = "com.google.android.apps.kids.familylink"
        val appIntent = packageManager.getLaunchIntentForPackage(familyLinkPackage)
        if (appIntent != null) {
            startActivity(appIntent)
            return
        }
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$familyLinkPackage"))
        if (marketIntent.resolveActivity(packageManager) != null) {
            startActivity(marketIntent)
            return
        }
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$familyLinkPackage")
            )
        )
    }

    private fun showEnablePasswordToggleDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage("Enable Password Mode Toggle to On")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun activeLockMode(): LockMode {
        val mode = prefs.getString("active_lock_mode", "NONE") ?: "NONE"
        return try {
            LockMode.valueOf(mode)
        } catch (_: IllegalArgumentException) {
            LockMode.NONE
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun canUseBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun setupBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricEnabled = true
                    Toast.makeText(
                        applicationContext,
                        "Biometric unlock enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    biometricEnabled = false
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        Toast.makeText(
                            applicationContext,
                            "Biometric verification failed: $errString",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    biometricEnabled = false
                }
            })
    }

    private fun showBiometricVerificationPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable biometric unlock")
            .setSubtitle("Verify your biometric to enable biometric unlock for Password mode")
            .setNegativeButtonText("Cancel")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }
}
