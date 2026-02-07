package com.juliacai.apptick

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.activity.compose.setContent
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var appTickPrefs: SharedPreferences
    private lateinit var groupPrefs: SharedPreferences

    private var isDarkMode by mutableStateOf(false)
    private var showTimeLeft by mutableStateOf(true)
    private var hasPassword by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appTickPrefs = getSharedPreferences("AppTickPrefs", Context.MODE_PRIVATE)
        groupPrefs = getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)

        isDarkMode = appTickPrefs.getBoolean(BaseActivity.PREF_DARK_MODE, false)
        showTimeLeft = groupPrefs.getBoolean("showTimeLeft", true)
        hasPassword = groupPrefs.getString("password", null) != null

        setContent {
            SettingsScreen(
                isDarkMode = isDarkMode,
                onDarkModeChange = { 
                    isDarkMode = it
                    appTickPrefs.edit { putBoolean(BaseActivity.PREF_DARK_MODE, it) }
                    AppCompatDelegate.setDefaultNightMode(if (it) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
                },
                showTimeLeft = showTimeLeft,
                onShowTimeLeftChange = { 
                    showTimeLeft = it
                    groupPrefs.edit { putBoolean("showTimeLeft", it) }
                },
                onColorCustomizationClick = {
//                    startActivity(Intent(this, ColorCustomizationActivity::class.java))
                },
                onRemovePasswordClick = { showPasswordConfirmationDialog() },
                hasPassword = hasPassword
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hasPassword = groupPrefs.getString("password", null) != null
    }

    private fun showPasswordConfirmationDialog() {
        val passwordInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter current password"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Password")
            .setView(passwordInput)
            .setPositiveButton("Confirm") { _, _ ->
                val enteredPassword = passwordInput.text.toString()
                val currentPassword = groupPrefs.getString("password", "")
                if (enteredPassword == currentPassword) {
                    showFinalConfirmationDialog()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Incorrect Password")
                        .setMessage("The password you entered is incorrect.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFinalConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Password Protection")
            .setMessage("Are you sure you want to remove password protection?")
            .setPositiveButton("Yes, Remove Password") { _, _ ->
                groupPrefs.edit {
                    remove("password")
                    putBoolean("locked", false)
                    putBoolean("blockMain", false)
                    putBoolean("blockSettings", false)
                }
                hasPassword = false
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
