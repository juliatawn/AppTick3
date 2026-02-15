package com.juliacai.apptick.lockModes

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.juliacai.apptick.premiumMode.DeviceAdmin

class SetPassword : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private lateinit var prefs: SharedPreferences

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

        setContent {
            SetPasswordScreen(
                onSaveClick = { password, confirmPassword, recoveryEmail, enableSettingsLock ->
                    savePasswordAndFinish(password, confirmPassword, recoveryEmail, enableSettingsLock)
                },
                onCancelClick = { finish() },
                onEnableDeviceAdminClick = {
                    if (isAdminGranted) {
                        showInfoDialog("Device Admin", "Device admin permissions are already enabled.")
                    } else {
                        requestDeviceAdmin()
                    }
                },
                onSetupRecoveryEmailClick = {
                    startActivity(Intent(this, RecoveryEmailSetupActivity::class.java))
                },
                isAdminGranted = isAdminGranted
            )
        }
    }

    private fun savePasswordAndFinish(
        passFirst: String,
        passSecond: String,
        recoveryEmail: String,
        enableSettingsLock: Boolean
    ) {
        if (passFirst.isEmpty()) {
            showInfoDialog("Password", "Please enter a password.")
            return
        }

        if (passFirst != passSecond) {
            showInfoDialog("Password", "Passwords do not match.")
            return
        }

        if (enableSettingsLock && !isAdminGranted) {
            showInfoDialog(
                "Device Admin Required",
                "To prevent AppTick from being uninstalled, enable Device Admin first with the \"Enable Device Admin\" button."
            )
            return
        }

        prefs.edit {
            putString("password", passFirst)
            putString("recovery_email", recoveryEmail)
            putBoolean("blockSettings", enableSettingsLock)
            putBoolean("blockMain", true)
            putBoolean("locked", true)
            putBoolean("passUnlocked", false)
        }

        if (enableSettingsLock) {
            BackgroundChecker.startServiceIfNotRunning(applicationContext)
        }

        Toast.makeText(this, "Password lock enabled", Toast.LENGTH_SHORT).show()
        finish()
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

    private fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
