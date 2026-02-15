package com.juliacai.apptick.premiumMode

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockdownModeActivity : AppCompatActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private var isDeviceAdminGranted by mutableStateOf(false)

    private val deviceAdminLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isDeviceAdminGranted = isAdminGranted
        if (result.resultCode == Activity.RESULT_OK || isDeviceAdminGranted) {
            Toast.makeText(this, "Device admin enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Device admin not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, DeviceAdmin::class.java)
        isDeviceAdminGranted = isAdminGranted
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val lockdownEnd = prefs.getLong("lockdown_end_time", 0L)
        val lockdownEnabled = prefs.getBoolean("lockdown_enabled", false)
        var oneTimeWeeklyChange by mutableStateOf(prefs.getBoolean("lockdown_one_time_change", false))
        var protectSettingsUninstall by mutableStateOf(prefs.getBoolean("blockSettings", false))
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
                oneTimeWeeklyChange = oneTimeWeeklyChange,
                protectSettingsUninstall = protectSettingsUninstall,
                isDeviceAdminGranted = isDeviceAdminGranted,
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
                    oneTimeWeeklyChange = enabled
                },
                onProtectSettingsUninstallToggled = { enabled ->
                    if (enabled && !isDeviceAdminGranted) {
                        Toast.makeText(
                            this,
                            "Enable Device Admin before turning on uninstall protection",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        prefs.edit { putBoolean("blockSettings", enabled) }
                        protectSettingsUninstall = enabled
                        if (enabled) {
                            BackgroundChecker.startServiceIfNotRunning(applicationContext)
                        }
                    }
                },
                onEnableDeviceAdminClick = {
                    if (isDeviceAdminGranted) {
                        Toast.makeText(this, "Device admin already enabled", Toast.LENGTH_SHORT).show()
                    } else {
                        requestDeviceAdmin()
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
}
