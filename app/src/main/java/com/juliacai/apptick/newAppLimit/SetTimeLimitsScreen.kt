package com.juliacai.apptick.newAppLimit

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppLimitSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetTimeLimitsScreen(
    settings: AppLimitSettings,
    onFinish: () -> Unit,
    onFragmentInteraction: (Uri) -> Unit
) {
    var groupName by remember { mutableStateOf(settings.groupName) }
    var useTimeLimit by remember { mutableStateOf(settings.timeHrLimit > 0 || settings.timeMinLimit > 0) }
    var timeHrLimit by remember { mutableStateOf(settings.timeHrLimit.toString()) }
    var timeMinLimit by remember { mutableStateOf(settings.timeMinLimit.toString()) }
    var limitEach by remember { mutableStateOf(settings.limitEach) }
    var useTimeRange by remember { mutableStateOf(settings.useTimeRange) }
    var startHour by remember { mutableStateOf(settings.startHour) }
    var startMinute by remember { mutableStateOf(settings.startMinute) }
    var endHour by remember { mutableStateOf(settings.endHour) }
    var endMinute by remember { mutableStateOf(settings.endMinute) }
    var weekDays by remember { mutableStateOf(settings.weekDays) }
    var cumulativeTime by remember { mutableStateOf(settings.cumulativeTime) }
    var resetHours by remember { mutableStateOf(settings.resetHours.toString()) }
    var dwm by remember { mutableStateOf(settings.dwm) }

    val showNoTimeLimitWarning = remember { mutableStateOf(false) }

    if (showNoTimeLimitWarning.value) {
        AlertDialog(
            onDismissRequest = { showNoTimeLimitWarning.value = false },
            title = { Text("No Time Limit") },
            text = { Text("Without a time limit, apps in this group will not be blocked. Continue?") },
            confirmButton = {
                Button(onClick = { showNoTimeLimitWarning.value = false }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                Button(onClick = {
                    useTimeLimit = true
                    showNoTimeLimitWarning.value = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    settings.groupName = groupName
                    if (useTimeLimit) {
                        settings.timeHrLimit = timeHrLimit.toIntOrNull() ?: 0
                        settings.timeMinLimit = timeMinLimit.toIntOrNull() ?: 0
                        settings.limitEach = limitEach
                    } else {
                        settings.timeHrLimit = 0
                        settings.timeMinLimit = 0
                        settings.limitEach = false
                    }
                    settings.useTimeRange = useTimeRange
                    settings.startHour = startHour
                    settings.startMinute = startMinute
                    settings.endHour = endHour
                    settings.endMinute = endMinute
                    settings.weekDays = weekDays
                    settings.cumulativeTime = cumulativeTime
                    settings.resetHours = resetHours.toIntOrNull() ?: 0
                    settings.dwm = dwm
                    onFinish()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Finish")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = groupName.toString(),
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Time Limit Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Time Limit")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = useTimeLimit,
                            onCheckedChange = {
                                useTimeLimit = it
                                if (!it) {
                                    showNoTimeLimitWarning.value = true
                                }
                            }
                        )
                    }
                    if (useTimeLimit) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = timeHrLimit,
                                onValueChange = { timeHrLimit = it },
                                label = { Text("Hours") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = timeMinLimit,
                                onValueChange = { timeMinLimit = it },
                                label = { Text("Minutes") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = limitEach,
                                onClick = { limitEach = true }
                            )
                            Text("Limit Each App")
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                selected = !limitEach,
                                onClick = { limitEach = false }
                            )
                            Text("Limit All Apps")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time Range Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Time Range")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = useTimeRange,
                            onCheckedChange = { useTimeRange = it }
                        )
                    }
                    if (useTimeRange) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TimePicker(
                            state = rememberTimePickerState(
                                initialHour = startHour,
                                initialMinute = startMinute
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TimePicker(
                            state = rememberTimePickerState(
                                initialHour = endHour,
                                initialMinute = endMinute
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Days of the week
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Days Active")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val days = listOf("M", "T", "W", "T", "F", "S", "S")
                        days.forEachIndexed { index, day ->
                            val dayIndex = index + 1
                            val isSelected = weekDays.contains(dayIndex)
                            OutlinedButton(
                                onClick = {
                                    val newWeekDays = weekDays.toMutableList()
                                    if (isSelected) {
                                        newWeekDays.remove(dayIndex)
                                    } else {
                                        newWeekDays.add(dayIndex)
                                    }
                                    weekDays = newWeekDays.sorted()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text(day)
                            }
                        }
                    }
                }
            }
        }
    }
}
