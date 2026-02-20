package com.juliacai.apptick.newAppLimit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.rememberScrollbarColor
import com.juliacai.apptick.verticalScrollWithIndicator
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetTimeLimitsScreen(
    viewModel: AppLimitViewModel = viewModel(),
    onFinish: (AppLimitGroup) -> Unit,
    onCancel: () -> Unit,
    onEditApps: () -> Unit = {},
    onUpgradeToPremium: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val groupPrefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    var premiumFeatureDialogFor by remember { mutableStateOf<String?>(null) }
    val group by viewModel.group.observeAsState()
    val draft by viewModel.draft.observeAsState()
    val selectedApps by viewModel.selectedApps.observeAsState(emptyList())
    val existingGroup = group

    val initialGroupName = draft?.groupName ?: existingGroup?.name ?: ""
    val initialUseTimeLimit = draft?.useTimeLimit ?: if (existingGroup == null) {
        true
    } else {
        (existingGroup.timeHrLimit > 0 || existingGroup.timeMinLimit > 0)
    }
    val initialTimeHrLimit = draft?.timeHrLimit ?: existingGroup?.timeHrLimit?.toString() ?: "0"
    val initialTimeMinLimit = draft?.timeMinLimit ?: existingGroup?.timeMinLimit?.toString() ?: "0"
    val initialLimitEach = draft?.limitEach ?: existingGroup?.limitEach ?: false
    val initialUseTimeRange = draft?.useTimeRange ?: existingGroup?.useTimeRange ?: false
    val initialBlockOutsideTimeRange =
        draft?.blockOutsideTimeRange ?: existingGroup?.blockOutsideTimeRange ?: false
    val initialStartHour = draft?.startHour ?: existingGroup?.startHour ?: 0
    val initialStartMinute = draft?.startMinute ?: existingGroup?.startMinute ?: 0
    val initialEndHour = draft?.endHour ?: existingGroup?.endHour ?: 23
    val initialEndMinute = draft?.endMinute ?: existingGroup?.endMinute ?: 59
    val initialWeekDays = draft?.weekDays ?: existingGroup?.weekDays ?: emptyList()
    val initialCumulativeTime = draft?.cumulativeTime ?: existingGroup?.cumulativeTime ?: false
    val initialUseReset = draft?.useReset ?: ((existingGroup?.resetMinutes ?: 0) > 0)
    val groupResetMinutes = existingGroup?.resetMinutes ?: 0
    val initialResetHours = draft?.resetHours ?: (groupResetMinutes / 60).toString()
    val initialResetMinutes = draft?.resetMinutes ?: (groupResetMinutes % 60).toString()

    val groupName = remember(initialGroupName) { mutableStateOf(initialGroupName) }
    val useTimeLimit = remember(initialUseTimeLimit) { mutableStateOf(initialUseTimeLimit) }
    val timeHrLimit = remember(initialTimeHrLimit) { mutableStateOf(initialTimeHrLimit) }
    val timeMinLimit = remember(initialTimeMinLimit) { mutableStateOf(initialTimeMinLimit) }
    val limitEach = remember(initialLimitEach) { mutableStateOf(initialLimitEach) }
    val useTimeRange = remember(initialUseTimeRange) { mutableStateOf(initialUseTimeRange) }
    val blockOutsideTimeRange = remember(initialBlockOutsideTimeRange) {
        mutableStateOf(initialBlockOutsideTimeRange)
    }
    val startHour = remember(initialStartHour) { mutableStateOf(initialStartHour) }
    val startMinute = remember(initialStartMinute) { mutableStateOf(initialStartMinute) }
    val endHour = remember(initialEndHour) { mutableStateOf(initialEndHour) }
    val endMinute = remember(initialEndMinute) { mutableStateOf(initialEndMinute) }
    val weekDays = remember(initialWeekDays) { mutableStateOf(initialWeekDays) }
    val cumulativeTime = remember(initialCumulativeTime) { mutableStateOf(initialCumulativeTime) }
    val useReset = remember(initialUseReset) { mutableStateOf(initialUseReset) }
    val resetHours = remember(initialResetHours) { mutableStateOf(initialResetHours) }
    val resetMinutes = remember(initialResetMinutes) { mutableStateOf(initialResetMinutes) }

    val showNoTimeLimitWarning = remember { mutableStateOf(false) }
    val showStartTimePicker = remember { mutableStateOf(false) }
    val showEndTimePicker = remember { mutableStateOf(false) }
    val isResetHoursError = remember { mutableStateOf(false) }

    fun persistDraft() {
        viewModel.updateDraft(
            SetTimeLimitDraft(
                groupName = groupName.value,
                useTimeLimit = useTimeLimit.value,
                timeHrLimit = timeHrLimit.value,
                timeMinLimit = timeMinLimit.value,
                limitEach = limitEach.value,
                useTimeRange = useTimeRange.value,
                blockOutsideTimeRange = blockOutsideTimeRange.value,
                startHour = startHour.value,
                startMinute = startMinute.value,
                endHour = endHour.value,
                endMinute = endMinute.value,
                weekDays = weekDays.value,
                cumulativeTime = cumulativeTime.value,
                useReset = useReset.value,
                resetHours = resetHours.value,
                resetMinutes = resetMinutes.value
            )
        )
    }

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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                .verticalScrollWithIndicator(scrollState, rememberScrollbarColor())
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
                                    val bitmap = createBitmap(
                                        drawable.intrinsicWidth.coerceAtLeast(1),
                                        drawable.intrinsicHeight.coerceAtLeast(1)
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
            OutlinedButton(onClick = {
                persistDraft()
                onEditApps()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Edit Selected Apps")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = groupName.value,
                onValueChange = { groupName.value = it },
                label = { Text("App Limit Group Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Time Limit")
                Spacer(Modifier.weight(1f))
                Switch(checked = useTimeLimit.value, onCheckedChange = { 
                    useTimeLimit.value = it
                    if (!it) showNoTimeLimitWarning.value = true
                })
            }
            if (!useTimeLimit.value) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "With time limit off, apps in this group will always be blocked when active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (useTimeLimit.value) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        OutlinedTextField(
                            value = timeHrLimit.value,
                            onValueChange = { timeHrLimit.value = it.filter(Char::isDigit) },
                            label = { Text("HH") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right) }
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = timeMinLimit.value,
                            onValueChange = { timeMinLimit.value = it.filter(Char::isDigit) },
                            label = { Text("MM") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = limitEach.value, onClick = { limitEach.value = true })
                        Text("Limit for EACH app")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = !limitEach.value, onClick = { limitEach.value = false })
                        Text("Limit for ALL apps")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set Time Range")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = useTimeRange.value,
                    onCheckedChange = {
                        val isPremium = groupPrefs.getBoolean("premium", false)
                        if (it && !isPremium) {
                            premiumFeatureDialogFor = "Set Time Range"
                            return@Switch
                        }
                        useTimeRange.value = it
                    }
                )
            }

            if (useTimeRange.value) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(
                            onClick = { showStartTimePicker.value = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start: ${formatTime(context, startHour.value, startMinute.value)}")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { showEndTimePicker.value = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("End: ${formatTime(context, endHour.value, endMinute.value)}")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Outside Time Range", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose whether selected apps are fully blocked outside this range, or allowed with no limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isBlockAppsSelected = blockOutsideTimeRange.value
                        OutlinedButton(
                            onClick = { blockOutsideTimeRange.value = true },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                1.dp,
                                if (isBlockAppsSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isBlockAppsSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                contentColor = if (isBlockAppsSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        ) {
                            Text("Block Apps")
                        }
                        val isNoLimitsSelected = !isBlockAppsSelected
                        OutlinedButton(
                            onClick = { blockOutsideTimeRange.value = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                1.dp,
                                if (isNoLimitsSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isNoLimitsSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                contentColor = if (isNoLimitsSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        ) {
                            Text("Allow No Limits")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Active Days")
            val isEverydaySelected = weekDays.value.isEmpty()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isEverydaySelected,
                    onCheckedChange = { checked ->
                        if (checked) {
                            weekDays.value = emptyList()
                        } else if (weekDays.value.isEmpty()) {
                            weekDays.value = listOf(1)
                        }
                    }
                )
                Text("Everyday")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
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
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(day, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reset Time Limits Periodically")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = useReset.value,
                    onCheckedChange = {
                        val isPremium = groupPrefs.getBoolean("premium", false)
                        if (it && !isPremium) {
                            premiumFeatureDialogFor = "Reset Time Limits Periodically"
                            return@Switch
                        }
                        useReset.value = it
                        if (!it) {
                            cumulativeTime.value = false
                        }
                    }
                )
            }
            Text(
                "Automatically resets available time after the interval you set below.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            if(useReset.value) {
                 Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        OutlinedTextField(
                            value = resetHours.value,
                            onValueChange = { resetHours.value = it.filter(Char::isDigit) },
                            label = { Text("Reset HH") },
                            modifier = Modifier.weight(1f),
                            isError = isResetHoursError.value,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right) }
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = resetMinutes.value,
                            onValueChange = { resetMinutes.value = it.filter(Char::isDigit) },
                            label = { Text("Reset MM") },
                            modifier = Modifier.weight(1f),
                            isError = isResetHoursError.value,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            )
                        )
                    }
                    if (isResetHoursError.value) {
                        Text(
                            "Please enter a valid reset period > 0",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cumulative Time")
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = cumulativeTime.value,
                            onCheckedChange = { cumulativeTime.value = it }
                        )
                    }
                    Text(
                        "If enabled, unused time from each periodic reset carries over and can be used until 12:00 AM that day, or until the time-range end time if a time range is set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    val resetHr = resetHours.value.toIntOrNull() ?: 0
                    val resetMin = resetMinutes.value.toIntOrNull() ?: 0
                    val resetTotalMinutes = (resetHr * 60) + resetMin
                    isResetHoursError.value = useReset.value && resetTotalMinutes <= 0
                    if (!isResetHoursError.value) {
                        val effectiveTimeHrLimit = if (useTimeLimit.value) (timeHrLimit.value.toIntOrNull() ?: 0) else 0
                        val effectiveTimeMinLimit = if (useTimeLimit.value) (timeMinLimit.value.toIntOrNull() ?: 0) else 0
                        val newGroup = (group ?: AppLimitGroup()).copy(
                            name = if (groupName.value.isNotBlank()) groupName.value else {
                                if (selectedApps.isNotEmpty()) "${selectedApps[0].appName} Group" else "App Limit Group"
                            },
                            timeHrLimit = effectiveTimeHrLimit,
                            timeMinLimit = effectiveTimeMinLimit,
                            limitEach = limitEach.value,
                            weekDays = weekDays.value,
                            useTimeRange = useTimeRange.value,
                            blockOutsideTimeRange = useTimeRange.value && blockOutsideTimeRange.value,
                            startHour = startHour.value,
                            startMinute = startMinute.value,
                            endHour = endHour.value,
                            endMinute = endMinute.value,
                            cumulativeTime = useReset.value && cumulativeTime.value,
                            resetMinutes = if (useReset.value) resetTotalMinutes else 0,
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

        if (showNoTimeLimitWarning.value) { AlertDialog(onDismissRequest = { showNoTimeLimitWarning.value = false }, title = { Text("Always Block App") }, text = { Text("Are you sure you want the app to be blocked with 0 time use?") }, confirmButton = { Button({ showNoTimeLimitWarning.value = false }) { Text("Continue") } }, dismissButton = { Button({ showNoTimeLimitWarning.value = false; useTimeLimit.value = true }) { Text("Cancel") } }) }
        if (showStartTimePicker.value) {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> startHour.value = hour; startMinute.value = minute; showStartTimePicker.value = false },
                    startHour.value,
                    startMinute.value,
                    android.text.format.DateFormat.is24HourFormat(context)
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
                    android.text.format.DateFormat.is24HourFormat(context)
                ).apply {
                    setOnCancelListener { showEndTimePicker.value = false }
                    show()
                }
            }
        }

        premiumFeatureDialogFor?.let { featureName ->
            AlertDialog(
                onDismissRequest = { premiumFeatureDialogFor = null },
                title = { Text("Premium Feature") },
                text = { Text("$featureName is available in Premium Mode.") },
                confirmButton = {
                    Button(
                        onClick = {
                            premiumFeatureDialogFor = null
                            onUpgradeToPremium()
                        }
                    ) {
                        Text("Buy Premium")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { premiumFeatureDialogFor = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun formatTime(context: Context, hourOfDay: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}
