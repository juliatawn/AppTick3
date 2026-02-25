package com.juliacai.apptick.premiumMode

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class LockdownTimeActivity : AppCompatActivity() {

    private var selectedDateTime by mutableStateOf(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.applyTheme(this)
        setContent {
            LockdownTimeScreen(
                selectedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDateTime.time),
                selectedTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(selectedDateTime.time),
                onDateClick = { showDatePicker() },
                onTimeClick = { showTimePicker() },
                onConfirmClick = { confirmLockdown() },
                onBackClick = { finish() }
            )
        }
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

        timePicker.addOnPositiveButtonClickListener { _ ->
            val newDateTime = selectedDateTime.clone() as Calendar
            newDateTime.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            newDateTime.set(Calendar.MINUTE, timePicker.minute)
            selectedDateTime = newDateTime
        }

        timePicker.show(supportFragmentManager, "TIME_PICKER")
    }

    private fun confirmLockdown() {
        if (selectedDateTime.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, "Please select a future date and time", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        prefs.edit {
            putLong("lockdown_end_time", selectedDateTime.timeInMillis)
        }

        val formattedDateTime = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(selectedDateTime.time)
        Toast.makeText(this, "Lockdown on until $formattedDateTime", Toast.LENGTH_LONG).show()

        finish()
    }
}
