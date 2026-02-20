package com.juliacai.apptick.lockModes

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.premiumMode.DeviceAdmin

class SetPassword : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private lateinit var prefs: SharedPreferences
    private var passwordRecoveryVerified by mutableStateOf(false)

    private val deviceAdminLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Device admin enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Device admin not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, DeviceAdmin::class.java)
        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)

        AppTheme.applyTheme(this)

        val isPasswordEnabled = prefs.getString("active_lock_mode", "NONE") == "PASSWORD"
        passwordRecoveryVerified = isPasswordRecoveryVerified()

        setContent {
            AppTheme {
                SetPasswordScreen(
                    onSaveClick = { password, confirmPassword, recoveryEmail, enableAdminProtection, enableBiometric ->
                        savePasswordAndFinish(
                            passFirst = password,
                            passSecond = confirmPassword,
                            recoveryEmail = recoveryEmail,
                            enableAdminProtection = enableAdminProtection,
                            enableBiometric = enableBiometric
                        )
                    },
                    onVerifyRecoveryEmailClick = { email ->
                        sendRecoveryVerificationEmail(email)
                    },
                    onCancelClick = { finish() },
                    onBackClick = { finish() },
                    onEnableDeviceAdminClick = {
                        if (isAdminGranted) {
                            showInfoDialog("Device Admin", "Device admin permissions are already enabled.")
                        } else {
                            requestDeviceAdmin()
                        }
                    },
                    onDisableClick = {
                        disablePasswordMode()
                    },
                    isAdminGranted = isAdminGranted,
                    isPasswordEnabled = isPasswordEnabled,
                    activeLockMode = activeLockMode(),
                    isRecoveryEmailVerified = passwordRecoveryVerified,
                    initialRecoveryEmail = prefs.getString("recovery_email", "").orEmpty(),
                    initialAdminProtection = prefs.getBoolean("useDeviceAdminUninstallProtection", false),
                    initialBiometricEnabled = prefs.getBoolean("password_biometric_enabled", true),
                    isBiometricSupported = canUseBiometric()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        passwordRecoveryVerified = isPasswordRecoveryVerified()
    }

    private fun savePasswordAndFinish(
        passFirst: String,
        passSecond: String,
        recoveryEmail: String,
        enableAdminProtection: Boolean,
        enableBiometric: Boolean
    ) {
        val activeMode = activeLockMode()
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

        if (!Patterns.EMAIL_ADDRESS.matcher(recoveryEmail).matches()) {
            showInfoDialog("Recovery Email", "Enter a valid recovery email.")
            return
        }

        if (!isPasswordRecoveryVerifiedForEmail(recoveryEmail)) {
            showInfoDialog(
                "Recovery Email",
                "Verify the recovery email first using the verification link."
            )
            return
        }

        if (enableAdminProtection && !isAdminGranted) {
            showInfoDialog(
                "Device Admin Required",
                "Enable Device Admin first to turn on uninstall protection."
            )
            return
        }

        prefs.edit {
            putString("password", passFirst)
            putString("recovery_email", recoveryEmail)
            putBoolean("recovery_email_password_verified", true)
            putString("active_lock_mode", "PASSWORD")
            putBoolean("useDeviceAdminUninstallProtection", enableAdminProtection)
            putBoolean("password_biometric_enabled", enableBiometric)
            putBoolean("blockMain", true)
            putBoolean("locked", true)
            putBoolean("passUnlocked", false)
            putBoolean("securityKeyUnlocked", false)
        }

        if (enableAdminProtection) {
            BackgroundChecker.startServiceIfNotRunning(applicationContext)
        }

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
            putBoolean("locked", false)
            putBoolean("passUnlocked", true)
        }
        Toast.makeText(this, "Password lock disabled", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun sendRecoveryVerificationEmail(email: String) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showInfoDialog("Recovery Email", "Enter a valid recovery email before verification.")
            return
        }

        prefs.edit {
            putString("recovery_email", email)
            putBoolean("recovery_email_password_verified", false)
        }
        passwordRecoveryVerified = false

        RecoveryEmailHelper.sendRecoveryLink(
            context = this,
            email = email,
            purpose = RecoveryEmailHelper.PURPOSE_SETUP_PASSWORD,
            onSuccess = {
                showInfoDialog(
                    "Verification Email Sent",
                    "Check $email and open the verification link, then return here to save Password mode."
                )
            },
            onError = { message ->
                showInfoDialog("Verification Failed", message)
            }
        )
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Granting this permission lets AppTick block Settings uninstall pages while lock mode is active."
            )
        }
        deviceAdminLauncher.launch(intent)
    }

    private val isAdminGranted: Boolean
        get() = devicePolicyManager.isAdminActive(adminComponentName)

    private fun isPasswordRecoveryVerified(): Boolean {
        val stored = prefs.getString("recovery_email", null)
        return !stored.isNullOrBlank() && isPasswordRecoveryVerifiedForEmail(stored)
    }

    private fun isPasswordRecoveryVerifiedForEmail(email: String): Boolean {
        val stored = prefs.getString("recovery_email", null)
        val verified = prefs.getBoolean("recovery_email_password_verified", false)
        return verified && !stored.isNullOrBlank() && stored.equals(email, ignoreCase = true)
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
}
