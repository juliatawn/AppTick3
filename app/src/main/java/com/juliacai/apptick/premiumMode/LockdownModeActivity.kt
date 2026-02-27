package com.juliacai.apptick.premiumMode

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.LockDecision
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.LockPolicy
import com.juliacai.apptick.LockState
import com.juliacai.apptick.LockdownType
import com.juliacai.apptick.MainActivity
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
    // State variables
    private var lockdownType by mutableStateOf(LockdownType.ONE_TIME)
    private var recurringDays by mutableStateOf(listOf<Int>())
    private var statusText by mutableStateOf("")
    private var isLockdownActive by mutableStateOf(false)
    private var isConfigurationEnabled by mutableStateOf(false)
    private var canEditCurrentLockdownSettings by mutableStateOf(false)
    private var selectedDateTime by mutableStateOf(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AppTheme.applyTheme(this)
        
        refreshState() // Load initial state
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitRequest()
            }
        })

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
                    isLockdownActive = isLockdownActive,
                    canEditCurrentLockdownSettings = canEditCurrentLockdownSettings,
                    isConfigurationEnabled = isConfigurationEnabled,
                    onConfigurationEnabledChange = { isEnabled ->
                        if (!isEnabled && isLockdownActive) {
                            getSharedPreferences("groupPrefs", MODE_PRIVATE).edit {
                                putString("active_lock_mode", LockMode.NONE.name)
                                putBoolean("lockdown_prompt_after_unlock", false)
                                putBoolean("useDeviceAdminUninstallProtection", false)
                            }
                            refreshState()
                            Toast.makeText(this, "Lockdown Disabled.", Toast.LENGTH_SHORT).show()
                        } else {
                            isConfigurationEnabled = isEnabled
                        }
                    },
                    onDisabledInteraction = { showEnableLockdownToggleDialog() },
                    
                    onLockdownTypeChanged = { newType ->
                        lockdownType = newType
                    },
                    onRecurringDaysChanged = { newDays ->
                        recurringDays = newDays
                    },
                    onDateClick = { showDatePicker() },
                    onTimeClick = { showTimePicker() },
                    onOpenFamilyLinkClick = { openFamilyLinkSetup() },
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
                                    putString("lockdown_type", lockdownType.name)
                                    if (lockdownType == LockdownType.ONE_TIME) {
                                        putLong("lockdown_end_time", selectedDateTime.timeInMillis)
                                        remove("lockdown_recurring_days")
                                    } else {
                                        putString("lockdown_recurring_days", recurringDays.joinToString(","))
                                        remove("lockdown_end_time")
                                    }
                                    putBoolean("useDeviceAdminUninstallProtection", false)
                                    putBoolean("lockdown_prompt_after_unlock", false)
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
                    onCancelClick = { handleExitRequest() }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshState()
    }
    
    private fun refreshState() {
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()
        
        // Active Mode
        val activeModeStr = prefs.getString("active_lock_mode", "NONE") ?: "NONE"
        isLockdownActive = activeModeStr == "LOCKDOWN"
        
        // Config
        val typeStr = prefs.getString("lockdown_type", "ONE_TIME") ?: "ONE_TIME"
        lockdownType = try { LockdownType.valueOf(typeStr) } catch(e:Exception) { LockdownType.ONE_TIME }
        
        val daysStr = prefs.getString("lockdown_recurring_days", "") ?: ""
        recurringDays = if (daysStr.isEmpty()) emptyList() 
                        else daysStr.split(",").mapNotNull { it.toIntOrNull() }.sorted()
        
        val lockdownEnd = prefs.getLong("lockdown_end_time", 0L)
        if (
            activeModeStr == LockMode.LOCKDOWN.name &&
            lockdownType == LockdownType.ONE_TIME &&
            lockdownEnd > 0L &&
            lockdownEnd <= nowMillis
        ) {
            prefs.edit {
                putString("active_lock_mode", "NONE")
                remove("lockdown_end_time")
                remove("lockdown_weekly_used_key")
                putBoolean("lockdown_prompt_after_unlock", true)
            }
            isLockdownActive = false
        }
        val lockDecision: LockDecision = if (isLockdownActive) {
            LockPolicy.evaluateEditingLock(
                state = LockState(
                    activeLockMode = LockMode.LOCKDOWN,
                    passwordUnlocked = false,
                    securityKeyUnlocked = false,
                    lockdownType = lockdownType,
                    lockdownEndTimeMillis = lockdownEnd,
                    lockdownRecurringDays = recurringDays,
                    lockdownRecurringUsedKey = prefs.getString("lockdown_weekly_used_key", null)
                ),
                nowMillis = nowMillis
            )
        } else {
            LockDecision(isLocked = false)
        }
        canEditCurrentLockdownSettings = !lockDecision.isLocked
        isConfigurationEnabled = isLockdownActive
        if (lockdownEnd > 0L) {
            selectedDateTime = Calendar.getInstance().apply { timeInMillis = lockdownEnd }
        }
        
        // Status Text Generation
        statusText = if (isLockdownActive) {
             if (lockdownType == LockdownType.ONE_TIME) {
                 if (lockdownEnd > nowMillis) {
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

    private fun handleExitRequest() {
        if (isConfigurationEnabled && !isLockdownActive) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Lockdown Mode")
                .setMessage("Lockdown Mode Not Configured, Disabling")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK") { _, _ ->
                    getSharedPreferences("groupPrefs", MODE_PRIVATE).edit {
                        putString("active_lock_mode", LockMode.NONE.name)
                    }
                    finish()
                }
                .show()
            return
        }
        finish()
    }

    private fun showEnableLockdownToggleDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage("Enable Lockdown Mode Toggle to On")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun buildLockdownTargetText(nowMillis: Long = System.currentTimeMillis()): String {
        return LockdownSummaryFormatter.formatTarget(
            lockdownType = lockdownType,
            lockdownEndTimeMillis = selectedDateTime.timeInMillis,
            recurringDays = recurringDays,
            nowMillis = nowMillis
        )
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
}
