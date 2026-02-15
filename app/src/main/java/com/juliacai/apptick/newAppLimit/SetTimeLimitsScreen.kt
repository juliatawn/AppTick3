package com.juliacai.apptick.newAppLimit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetTimeLimitsScreen(
    viewModel: AppLimitViewModel = viewModel(),
    onFinish: (AppLimitGroup) -> Unit,
    onCancel: () -> Unit,
    onEditApps: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val group by viewModel.group.observeAsState()
    val selectedApps by viewModel.selectedApps.observeAsState(emptyList())

    val groupName = remember { mutableStateOf(group?.name ?: "") }
    val useTimeLimit = remember { mutableStateOf((group?.timeHrLimit ?: 0 > 0 || group?.timeMinLimit ?: 0 > 0)) }
    val timeHrLimit = remember { mutableStateOf(group?.timeHrLimit?.toString() ?: "0") }
    val timeMinLimit = remember { mutableStateOf(group?.timeMinLimit?.toString() ?: "0") }
    val limitEach = remember { mutableStateOf(group?.limitEach ?: false) }
    val useTimeRange = remember { mutableStateOf(group?.useTimeRange ?: false) }
    val startHour = remember { mutableStateOf(group?.startHour ?: 0) }
    val startMinute = remember { mutableStateOf(group?.startMinute ?: 0) }
    val endHour = remember { mutableStateOf(group?.endHour ?: 23) }
    val endMinute = remember { mutableStateOf(group?.endMinute ?: 59) }
    val weekDays = remember { mutableStateOf(group?.weekDays ?: emptyList()) }
    val cumulativeTime = remember { mutableStateOf(group?.cumulativeTime ?: false) }
    val useReset = remember { mutableStateOf((group?.resetHours ?: 0) > 0) }
    val resetHours = remember { mutableStateOf(group?.resetHours?.toString() ?: "0") }
    val dwm = remember { mutableStateOf(group?.dwm ?: "Daily") }
    val dwmExpanded = remember { mutableStateOf(false) }

    val showNoTimeLimitWarning = remember { mutableStateOf(false) }
    val showStartTimePicker = remember { mutableStateOf(false) }
    val showEndTimePicker = remember { mutableStateOf(false) }
    val isResetHoursError = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Time Limits") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Selected Apps Section
            Text("Selected Apps (${selectedApps.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (selectedApps.isEmpty()) {
                Text("No apps selected", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedApps.forEach { app ->
                        val context = LocalContext.current
                        val iconBitmap = remember(app.appPackage) {
                            try {
                                app.appPackage?.let { pkg ->
                                    val drawable = context.packageManager.getApplicationIcon(pkg)
                                    val bitmap = android.graphics.Bitmap.createBitmap(
                                        drawable.intrinsicWidth.coerceAtLeast(1),
                                        drawable.intrinsicHeight.coerceAtLeast(1),
                                        android.graphics.Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bitmap)
                                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                                    drawable.draw(canvas)
                                    bitmap.asImageBitmap()
                                }
                            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                                null
                            }
                        }
                        InputChip(
                            selected = true,
                            onClick = {
                                val updated = selectedApps.toMutableList()
                                updated.remove(app)
                                viewModel.setSelectedApps(updated)
                            },
                            label = { Text(app.appName ?: app.appPackage ?: "") },
                            leadingIcon = {
                                iconBitmap?.let {
                                    Image(
                                        bitmap = it,
                                        contentDescription = app.appName,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onEditApps, modifier = Modifier.fillMaxWidth()) {
                Text("Edit Selected Apps")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(value = groupName.value, onValueChange = { groupName.value = it }, label = { Text("App Limit Group Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Time Limit")
                Spacer(Modifier.weight(1f))
                Switch(checked = useTimeLimit.value, onCheckedChange = { 
                    useTimeLimit.value = it
                    if (!it) showNoTimeLimitWarning.value = true
                })
            }

            if (useTimeLimit.value) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        OutlinedTextField(value = timeHrLimit.value, onValueChange = { timeHrLimit.value = it }, label = { Text("HH") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = timeMinLimit.value, onValueChange = { timeMinLimit.value = it }, label = { Text("MM") }, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = limitEach.value, onCheckedChange = { limitEach.value = it })
                        Text("Limit for EACH app")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set Time Range")
                Spacer(Modifier.weight(1f))
                Switch(checked = useTimeRange.value, onCheckedChange = { useTimeRange.value = it })
            }

            if (useTimeRange.value) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = { showStartTimePicker.value = true }, modifier = Modifier.weight(1f)) { Text("Start: ${startHour.value}:${startMinute.value}") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showEndTimePicker.value = true }, modifier = Modifier.weight(1f)) { Text("End: ${endHour.value}:${endMinute.value}") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Active Days")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                days.forEachIndexed { index, day ->
                    val dayIndex = index + 1
                    val selected = weekDays.value.contains(dayIndex)
                    OutlinedButton(
                        onClick = { 
                            val newWeekDays = weekDays.value.toMutableList()
                            if (newWeekDays.contains(dayIndex)) newWeekDays.remove(dayIndex) else newWeekDays.add(dayIndex)
                            weekDays.value = newWeekDays.sorted()
                        },
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(day)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

             Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = cumulativeTime.value, onCheckedChange = { cumulativeTime.value = it })
                Text("Cumulative Time")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if(cumulativeTime.value) "Add More Time Periodically" else "Reset Limits Periodically")
                Spacer(Modifier.weight(1f))
                Switch(checked = useReset.value, onCheckedChange = { useReset.value = it }, enabled = !cumulativeTime.value)
            }
            Text(if (cumulativeTime.value) "Additional time will be added after each interval. Unused time carries over." else "Reset time limits after a specified number of hours.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            if(useReset.value) {
                 Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = resetHours.value, onValueChange = { resetHours.value = it }, label = { Text("After how many hours?") }, modifier = Modifier.fillMaxWidth(), isError = isResetHoursError.value, supportingText = { if (isResetHoursError.value) Text("Please enter a valid reset period > 0") })
                     ExposedDropdownMenuBox(expanded = dwmExpanded.value, onExpandedChange = { dwmExpanded.value = !dwmExpanded.value }) {
                        OutlinedTextField(value = dwm.value, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dwmExpanded.value) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = dwmExpanded.value, onDismissRequest = { dwmExpanded.value = false }) {
                            listOf("Daily", "Weekly", "Monthly").forEach { selectionOption ->
                                DropdownMenuItem(text = { Text(selectionOption) }, onClick = { dwm.value = selectionOption; dwmExpanded.value = false })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    isResetHoursError.value = useReset.value && (resetHours.value.toIntOrNull() ?: 0) <= 0
                    if (!isResetHoursError.value) {
                        val newGroup = (group ?: AppLimitGroup()).copy(
                            name = if (groupName.value.isNotBlank()) groupName.value else {
                                if (selectedApps.isNotEmpty()) "${selectedApps[0].appName} Group" else "App Limit Group"
                            },
                            timeHrLimit = timeHrLimit.value.toIntOrNull() ?: 0,
                            timeMinLimit = timeMinLimit.value.toIntOrNull() ?: 0,
                            limitEach = limitEach.value,
                            weekDays = weekDays.value,
                            useTimeRange = useTimeRange.value,
                            startHour = startHour.value,
                            startMinute = startMinute.value,
                            endHour = endHour.value,
                            endMinute = endMinute.value,
                            cumulativeTime = cumulativeTime.value,
                            resetHours = resetHours.value.toIntOrNull() ?: 0,
                            dwm = dwm.value,
                            apps = selectedApps.map {
                                AppInGroup(
                                    it.appName ?: "",
                                    it.appPackage ?: "",
                                    it.appPackage ?: ""
                                )
                            }
                        )
                        onFinish(newGroup)
                    }
                }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }

        if (showNoTimeLimitWarning.value) { AlertDialog(onDismissRequest = { showNoTimeLimitWarning.value = false }, title = { Text("No Time Limit") }, text = { Text("Without a time limit, apps in this group will not be blocked. Continue?") }, confirmButton = { Button({ showNoTimeLimitWarning.value = false }) { Text("Continue") } }, dismissButton = { Button({ showNoTimeLimitWarning.value = false; useTimeLimit.value = true }) { Text("Cancel") } }) }
        if (showStartTimePicker.value) {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> startHour.value = hour; startMinute.value = minute; showStartTimePicker.value = false },
                    startHour.value,
                    startMinute.value,
                    false
                ).apply {
                    setOnCancelListener { showStartTimePicker.value = false }
                    show()
                }
            }
        }
        if (showEndTimePicker.value) {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> endHour.value = hour; endMinute.value = minute; showEndTimePicker.value = false },
                    endHour.value,
                    endMinute.value,
                    false
                ).apply {
                    setOnCancelListener { showEndTimePicker.value = false }
                    show()
                }
            }
        }
    }
}
