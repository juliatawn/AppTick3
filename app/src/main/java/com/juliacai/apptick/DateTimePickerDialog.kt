package com.juliacai.apptick

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateTimePickerDialogFragment : DialogFragment() {

    var onDateTimeSet: ((Calendar) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            setContentView(ComposeView(requireContext()).apply {
                setContent {
                    DateTimePickerDialog {
                        onDateTimeSet?.invoke(it)
                        dismiss()
                    }
                }
            })
        }
    }
}

@Composable
fun DateTimePickerDialog(onConfirm: (Calendar) -> Unit) {
    var selectedDateTime by remember { mutableStateOf(Calendar.getInstance()) }
    val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val context = LocalContext.current

    Surface(shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Lockdown Mode",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Set the date and time that you will be allowed to change your app limits. Your app limits will be locked until then and are NOT able to be changed until the date and time has passed.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val datePickerDialog = DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val newDateTime = selectedDateTime.clone() as Calendar
                            newDateTime.set(year, month, dayOfMonth)
                            selectedDateTime = newDateTime
                        },
                        selectedDateTime.get(Calendar.YEAR),
                        selectedDateTime.get(Calendar.MONTH),
                        selectedDateTime.get(Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.show()
                }) {
                    Text(text = "Date: ${dateFormatter.format(selectedDateTime.time)}")
                }
                Button(onClick = {
                    val timePickerDialog = TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            val newDateTime = selectedDateTime.clone() as Calendar
                            newDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            newDateTime.set(Calendar.MINUTE, minute)
                            selectedDateTime = newDateTime
                        },
                        selectedDateTime.get(Calendar.HOUR_OF_DAY),
                        selectedDateTime.get(Calendar.MINUTE),
                        false
                    )
                    timePickerDialog.show()
                }) {
                    Text(text = "Time: ${timeFormatter.format(selectedDateTime.time)}")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onConfirm(selectedDateTime) }) {
                    Text("Confirm")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DateTimePickerDialogPreview() {
    AppTheme {
        DateTimePickerDialog(onConfirm = {})
    }
}
