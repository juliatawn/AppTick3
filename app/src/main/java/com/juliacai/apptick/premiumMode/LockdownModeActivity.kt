package com.juliacai.apptick.premiumMode

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
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
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    private var selectedDateTime by mutableStateOf(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    )

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
                    selectedDate = SimpleDateFormat(
                        "MMM dd, yyyy",
                        Locale.getDefault()
                    ).format(selectedDateTime.time),
                    selectedTime = SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(selectedDateTime.time),
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
                    onDateClick = { showDatePicker() },
                    onTimeClick = { showTimePicker() },
                    onProtectSettingsUninstallToggled = { enabled ->
                        if (enabled && !isDeviceAdminGranted) {
                            Toast.makeText(
                                this,
                                "Enable Device Admin before turning on uninstall protection",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            getSharedPreferences("groupPrefs", MODE_PRIVATE).edit {
                                putBoolean("useDeviceAdminUninstallProtection", enabled)
                            }
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
                    onStartLockdownClick = {
                        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
                        val activeMode = prefs.getString("active_lock_mode", "NONE") ?: "NONE"
                        if (activeMode != LockMode.NONE.name && activeMode != LockMode.LOCKDOWN.name) {
                            Toast.makeText(
                                this,
                                "Only one lock mode can be enabled at a time. Disable $activeMode first.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@LockdownModeScreen
                        }
                        if (lockdownType == LockdownType.ONE_TIME &&
                            selectedDateTime.timeInMillis <= System.currentTimeMillis()
                        ) {
                            Toast.makeText(this, "Please set a future end time first.", Toast.LENGTH_SHORT).show()
                            return@LockdownModeScreen
                        }
                        if (lockdownType == LockdownType.RECURRING && recurringDays.isEmpty()) {
                            Toast.makeText(this, "Please select at least one allowed day.", Toast.LENGTH_SHORT).show()
                            return@LockdownModeScreen
                        }

                        val targetText = buildLockdownTargetText()
                        val confirmationText = SpannableStringBuilder(
                            "Are you sure you want to lockdown your app limits from being changed until "
                        )
                        val start = confirmationText.length
                        confirmationText.append(targetText)
                        confirmationText.setSpan(
                            StyleSpan(android.graphics.Typeface.BOLD),
                            start,
                            confirmationText.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        confirmationText.append("?")

                        MaterialAlertDialogBuilder(this)
                            .setTitle("Confirm Lockdown")
                            .setMessage(confirmationText)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Confirm") { _, _ ->
                                prefs.edit {
                                    putString("active_lock_mode", "LOCKDOWN")
                                    if (lockdownType == LockdownType.ONE_TIME) {
                                        putLong("lockdown_end_time", selectedDateTime.timeInMillis)
                                    }
                                    putBoolean("useDeviceAdminUninstallProtection", protectSettingsUninstall)
                                    putBoolean("lockdown_prompt_after_unlock", false)
                                }
                                if (protectSettingsUninstall) {
                                    BackgroundChecker.startServiceIfNotRunning(applicationContext)
                                }
                                refreshState()
                                Toast.makeText(this, "Lockdown saved.", Toast.LENGTH_SHORT).show()
                                startActivity(
                                    Intent(this, MainActivity::class.java).apply {
                                        // Recreate MainActivity so it always lands on the home cards screen.
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                )
                                finish()
                            }
                            .show()
                    },
                    onDisableLockdownClick = {
                        getSharedPreferences("groupPrefs", MODE_PRIVATE).edit {
                            putString("active_lock_mode", "NONE")
                            putBoolean("lockdown_prompt_after_unlock", false)
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
                        
        protectSettingsUninstall = prefs.getBoolean("useDeviceAdminUninstallProtection", false)
        
        val lockdownEnd = prefs.getLong("lockdown_end_time", 0L)
        if (
            activeModeStr == LockMode.LOCKDOWN.name &&
            lockdownType == LockdownType.ONE_TIME &&
            lockdownEnd > 0L &&
            lockdownEnd <= System.currentTimeMillis()
        ) {
            prefs.edit {
                putString("active_lock_mode", "NONE")
                remove("lockdown_end_time")
                remove("lockdown_weekly_used_key")
                putBoolean("lockdown_prompt_after_unlock", true)
            }
            isLockdownActive = false
        }
        if (lockdownEnd > 0L) {
            selectedDateTime = Calendar.getInstance().apply { timeInMillis = lockdownEnd }
        }
        
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
                 "Configure Lockdown Mode. New app-limit groups can still be added while lockdown is active."
        }
    }

    private fun buildLockdownTargetText(nowMillis: Long = System.currentTimeMillis()): String {
        return LockdownSummaryFormatter.formatTarget(
            lockdownType = lockdownType,
            lockdownEndTimeMillis = selectedDateTime.timeInMillis,
            recurringDays = recurringDays,
            nowMillis = nowMillis
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

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Lockdown End Date")
            .setSelection(selectedDateTime.timeInMillis)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            val newDateTime = selectedDateTime.clone() as Calendar
            newDateTime.set(Calendar.YEAR, calendar.get(Calendar.YEAR))
            newDateTime.set(Calendar.MONTH, calendar.get(Calendar.MONTH))
            newDateTime.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH))
            selectedDateTime = newDateTime
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun showTimePicker() {
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(selectedDateTime.get(Calendar.HOUR_OF_DAY))
            .setMinute(selectedDateTime.get(Calendar.MINUTE))
            .setTitleText("Select Lockdown End Time")
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val newDateTime = selectedDateTime.clone() as Calendar
            newDateTime.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            newDateTime.set(Calendar.MINUTE, timePicker.minute)
            selectedDateTime = newDateTime
        }

        timePicker.show(supportFragmentManager, "TIME_PICKER")
    }

    private val isAdminGranted: Boolean
        get() = devicePolicyManager.isAdminActive(adminComponentName)
}
