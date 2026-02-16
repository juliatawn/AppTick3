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
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.LockdownType
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockdownModeActivity : AppCompatActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private var isDeviceAdminGranted by mutableStateOf(false)
    
    // State variables
    private var lockdownType by mutableStateOf(LockdownType.ONE_TIME)
    private var recurringDays by mutableStateOf(listOf<Int>())
    private var protectSettingsUninstall by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var isLockdownActive by mutableStateOf(false)

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
        
        AppTheme.applyTheme(this)
        
        refreshState() // Load initial state

        setContent {
            AppTheme {
                LockdownModeScreen(
                    statusText = statusText,
                    lockdownType = lockdownType,
                    recurringDays = recurringDays,
                    protectSettingsUninstall = protectSettingsUninstall,
                    isDeviceAdminGranted = isDeviceAdminGranted,
                    isLockdownActive = isLockdownActive,
                    
                    onLockdownTypeChanged = { newType ->
                        lockdownType = newType
                        getSharedPreferences("groupPrefs", MODE_PRIVATE).edit { putString("lockdown_type", newType.name) }
                    },
                    onRecurringDaysChanged = { newDays ->
                        recurringDays = newDays
                        getSharedPreferences("groupPrefs", MODE_PRIVATE).edit { 
                            putString("lockdown_recurring_days", newDays.joinToString(",")) 
                        }
                    },
                    onProtectSettingsUninstallToggled = { enabled ->
                        if (enabled && !isDeviceAdminGranted) {
                            Toast.makeText(
                                this,
                                "Enable Device Admin before turning on uninstall protection",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            getSharedPreferences("groupPrefs", MODE_PRIVATE).edit { putBoolean("blockSettings", enabled) }
                            protectSettingsUninstall = enabled
                            if (enabled && isLockdownActive) {
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
                    onStartLockdownClick = {
                        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
                        if (lockdownType == LockdownType.ONE_TIME &&
                            prefs.getLong("lockdown_end_time", 0L) <= System.currentTimeMillis()
                        ) {
                            Toast.makeText(this, "Please set a future end time first.", Toast.LENGTH_SHORT).show()
                            return@LockdownModeScreen
                        }
                        if (lockdownType == LockdownType.RECURRING && recurringDays.isEmpty()) {
                            Toast.makeText(this, "Please select at least one allowed day.", Toast.LENGTH_SHORT).show()
                            return@LockdownModeScreen
                        }

                        prefs.edit {
                            putString("active_lock_mode", "LOCKDOWN")
                        }
                        if (protectSettingsUninstall) {
                            BackgroundChecker.startServiceIfNotRunning(applicationContext)
                        }
                        refreshState() // Update UI to active state immediately
                        Toast.makeText(this, "Lockdown Started!", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDisableLockdownClick = {
                        getSharedPreferences("groupPrefs", MODE_PRIVATE).edit {
                            putString("active_lock_mode", "NONE")
                        }
                        refreshState()
                        Toast.makeText(this, "Lockdown Disabled.", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshState()
    }
    
    private fun refreshState() {
        isDeviceAdminGranted = isAdminGranted
        
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        
        // Active Mode
        val activeModeStr = prefs.getString("active_lock_mode", "NONE") ?: "NONE"
        isLockdownActive = activeModeStr == "LOCKDOWN"
        
        // Config
        val typeStr = prefs.getString("lockdown_type", "ONE_TIME") ?: "ONE_TIME"
        lockdownType = try { LockdownType.valueOf(typeStr) } catch(e:Exception) { LockdownType.ONE_TIME }
        
        val daysStr = prefs.getString("lockdown_recurring_days", "") ?: ""
        recurringDays = if (daysStr.isEmpty()) emptyList() 
                        else daysStr.split(",").mapNotNull { it.toIntOrNull() }.sorted()
                        
        protectSettingsUninstall = prefs.getBoolean("blockSettings", false)
        
        val lockdownEnd = prefs.getLong("lockdown_end_time", 0L)
        
        // Status Text Generation
        statusText = if (isLockdownActive) {
             if (lockdownType == LockdownType.ONE_TIME) {
                 if (lockdownEnd > System.currentTimeMillis()) {
                     "Lockdown active until ${SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date(lockdownEnd))}"
                 } else {
                     "Lockdown expired/finished (One-Time)."
                 }
             } else {
                 val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                 val activeDays = recurringDays.joinToString(", ") { dayNames.getOrElse(it-1) { "?" } }
                 "Lockdown active. Editing allowed on: $activeDays"
             }
        } else {
             "Configure Lockdown Mode below."
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
