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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.material3.HorizontalDivider
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_NONE
import com.juliacai.apptick.groups.TimeRange
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
    val group by viewModel.group.observeAsState()
    val draft by viewModel.draft.observeAsState()
    val selectedApps by viewModel.selectedApps.observeAsState(emptyList())

    SetTimeLimitsScreenContent(
        group = group,
        draft = draft,
        selectedApps = selectedApps,
        onSelectedAppsChange = viewModel::setSelectedApps,
        onDraftChange = viewModel::updateDraft,
        onFinish = onFinish,
        onCancel = onCancel,
        onEditApps = onEditApps,
        onUpgradeToPremium = onUpgradeToPremium
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SetTimeLimitsScreenContent(
    group: AppLimitGroup?,
    draft: SetTimeLimitDraft?,
    selectedApps: List<AppInfo>,
    onSelectedAppsChange: (List<AppInfo>) -> Unit,
    onDraftChange: (SetTimeLimitDraft) -> Unit,
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
    val initialTimeRanges = draft?.timeRanges?.takeIf { it.isNotEmpty() }
        ?: existingGroup?.timeRanges?.takeIf { it.isNotEmpty() }
        ?: if (initialUseTimeRange) {
            listOf(
                TimeRange(
                    startHour = draft?.startHour ?: existingGroup?.startHour ?: 0,
                    startMinute = draft?.startMinute ?: existingGroup?.startMinute ?: 0,
                    endHour = draft?.endHour ?: existingGroup?.endHour ?: 23,
                    endMinute = draft?.endMinute ?: existingGroup?.endMinute ?: 59
                )
            )
        } else {
            emptyList()
        }
    val initialWeekDays = draft?.weekDays ?: existingGroup?.weekDays ?: emptyList()
    val initialCumulativeTime = draft?.cumulativeTime ?: existingGroup?.cumulativeTime ?: false
    val initialUseReset = draft?.useReset ?: ((existingGroup?.resetMinutes ?: 0) > 0)
    val groupResetMinutes = existingGroup?.resetMinutes ?: 0
    val initialResetHours = draft?.resetHours ?: (groupResetMinutes / 60).toString()
    val initialResetMinutes = draft?.resetMinutes ?: (groupResetMinutes % 60).toString()
    val initialAutoAddMode = draft?.autoAddMode ?: existingGroup?.autoAddMode ?: AUTO_ADD_NONE
    val initialIncludeExistingApps = draft?.includeExistingApps ?: existingGroup?.includeExistingApps ?: true

    val groupName = remember(initialGroupName) { mutableStateOf(initialGroupName) }
    val useTimeLimit = remember(initialUseTimeLimit) { mutableStateOf(initialUseTimeLimit) }
    val timeHrLimit = remember(initialTimeHrLimit) { mutableStateOf(initialTimeHrLimit) }
    val timeMinLimit = remember(initialTimeMinLimit) { mutableStateOf(initialTimeMinLimit) }
    val limitEach = remember(initialLimitEach) { mutableStateOf(initialLimitEach) }
    val useTimeRange = remember(initialUseTimeRange) { mutableStateOf(initialUseTimeRange) }
    val blockOutsideTimeRange = remember(initialBlockOutsideTimeRange) {
        mutableStateOf(initialBlockOutsideTimeRange)
    }
    val timeRanges = remember(initialTimeRanges) {
        mutableStateListOf<TimeRange>().apply {
            addAll(initialTimeRanges)
        }
    }
    val weekDays = remember(initialWeekDays) { mutableStateOf(initialWeekDays) }
    val cumulativeTime = remember(initialCumulativeTime) { mutableStateOf(initialCumulativeTime) }
    val useReset = remember(initialUseReset) { mutableStateOf(initialUseReset) }
    val resetHours = remember(initialResetHours) { mutableStateOf(initialResetHours) }
    val resetMinutes = remember(initialResetMinutes) { mutableStateOf(initialResetMinutes) }
    val autoAddMode = remember(initialAutoAddMode) { mutableStateOf(initialAutoAddMode) }
    val includeExistingApps = remember(initialIncludeExistingApps) { mutableStateOf(initialIncludeExistingApps) }

    val showNoTimeLimitWarning = remember { mutableStateOf(false) }
    val showZeroTimeSaveWarning = remember { mutableStateOf(false) }
    val timePickerTarget = remember { mutableStateOf<TimePickerTarget?>(null) }
    val isResetHoursError = remember { mutableStateOf(false) }

    LaunchedEffect(useTimeRange.value, timeRanges.size) {
        if (useTimeRange.value && timeRanges.isEmpty()) {
            timeRanges.add(TimeRange())
        }
    }

    fun persistDraft() {
        val draftRanges = timeRanges.toList()
        val fallbackRange = draftRanges.firstOrNull() ?: TimeRange()
        onDraftChange(
            SetTimeLimitDraft(
                groupName = groupName.value,
                useTimeLimit = useTimeLimit.value,
                timeHrLimit = timeHrLimit.value,
                timeMinLimit = timeMinLimit.value,
                limitEach = limitEach.value,
                useTimeRange = useTimeRange.value,
                blockOutsideTimeRange = blockOutsideTimeRange.value,
                timeRanges = draftRanges,
                startHour = fallbackRange.startHour,
                startMinute = fallbackRange.startMinute,
                endHour = fallbackRange.endHour,
                endMinute = fallbackRange.endMinute,
                weekDays = weekDays.value,
                cumulativeTime = cumulativeTime.value,
                useReset = useReset.value,
                resetHours = resetHours.value,
                resetMinutes = resetMinutes.value,
                autoAddMode = autoAddMode.value,
                includeExistingApps = includeExistingApps.value
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Set Time Limits",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val doSave = {
            val resetHr = resetHours.value.toIntOrNull() ?: 0
            val resetMin = resetMinutes.value.toIntOrNull() ?: 0
            val resetTotalMinutes = (resetHr * 60) + resetMin
            isResetHoursError.value = useReset.value && resetTotalMinutes <= 0
            if (!isResetHoursError.value) {
                val effectiveTimeHrLimit = if (useTimeLimit.value) (timeHrLimit.value.toIntOrNull() ?: 0) else 0
                val effectiveTimeMinLimit = if (useTimeLimit.value) (timeMinLimit.value.toIntOrNull() ?: 0) else 0
                val configuredTimeRanges = if (useTimeRange.value) {
                    if (timeRanges.isEmpty()) listOf(TimeRange()) else timeRanges.toList()
                } else {
                    emptyList()
                }
                val firstRange = configuredTimeRanges.firstOrNull() ?: TimeRange()
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
                    timeRanges = configuredTimeRanges,
                    startHour = firstRange.startHour,
                    startMinute = firstRange.startMinute,
                    endHour = firstRange.endHour,
                    endMinute = firstRange.endMinute,
                    cumulativeTime = useReset.value && cumulativeTime.value,
                    resetMinutes = if (useReset.value) resetTotalMinutes else 0,
                    apps = selectedApps.map {
                        AppInGroup(
                            it.appName ?: "",
                            it.appPackage ?: "",
                            it.appPackage ?: ""
                        )
                    },
                    autoAddMode = autoAddMode.value,
                    includeExistingApps = includeExistingApps.value
                )
                onFinish(newGroup)
            }
        }

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
                                onSelectedAppsChange(updated)
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
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            Spacer(modifier = Modifier.height(16.dp))

            // Auto-add apps section
            val useAutoAdd = autoAddMode.value != AUTO_ADD_NONE
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-Add Apps")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = useAutoAdd,
                    onCheckedChange = {
                        autoAddMode.value = if (it) AppLimitGroup.AUTO_ADD_ALL_NEW else AUTO_ADD_NONE
                    }
                )
            }
            Text(
                "Automatically add apps to this group when they are installed or match a category.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            if (useAutoAdd) {
                Spacer(modifier = Modifier.height(12.dp))

                val autoAddOptions = listOf(
                    AppLimitGroup.AUTO_ADD_ALL_NEW to "All newly installed apps",
                    AppLimitGroup.AUTO_ADD_CATEGORY_GAME to "Games",
                    AppLimitGroup.AUTO_ADD_CATEGORY_SOCIAL to "Social",
                    AppLimitGroup.AUTO_ADD_CATEGORY_AUDIO to "Audio",
                    AppLimitGroup.AUTO_ADD_CATEGORY_VIDEO to "Video",
                    AppLimitGroup.AUTO_ADD_CATEGORY_IMAGE to "Image",
                    AppLimitGroup.AUTO_ADD_CATEGORY_NEWS to "News",
                    AppLimitGroup.AUTO_ADD_CATEGORY_MAPS to "Maps",
                    AppLimitGroup.AUTO_ADD_CATEGORY_PRODUCTIVITY to "Productivity"
                )
                var dropdownExpanded by remember { mutableStateOf(false) }
                val selectedLabel = autoAddOptions.firstOrNull { it.first == autoAddMode.value }?.second ?: "All newly installed apps"

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Auto-add criteria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        autoAddOptions.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    autoAddMode.value = mode
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // "Include existing installed apps" checkbox — only for category modes
                if (autoAddMode.value != AppLimitGroup.AUTO_ADD_ALL_NEW) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeExistingApps.value,
                            onCheckedChange = { includeExistingApps.value = it }
                        )
                        Text("Include existing installed apps")
                    }
                    Text(
                        "When enabled, apps already installed that match this category will be added to the group on save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
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
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = limitEach.value,
                                onClick = { limitEach.value = true }
                            )
                            Text("Limit for EACH app")
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !limitEach.value,
                                onClick = { limitEach.value = false }
                            )
                            Text("Limit for ALL apps")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set Time Range")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = useTimeRange.value,
                    onCheckedChange = {
                        val isPremium = com.juliacai.apptick.PremiumStore.isPremium(context)
                        if (it && !isPremium) {
                            premiumFeatureDialogFor = "Set Time Range"
                            return@Switch
                        }
                        useTimeRange.value = it
                        if (it && timeRanges.isEmpty()) {
                            timeRanges.add(TimeRange())
                        }
                    }
                )
            }

            if (useTimeRange.value) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    timeRanges.forEachIndexed { index, range ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    timePickerTarget.value = TimePickerTarget(
                                        rangeIndex = index,
                                        isStart = true
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Start: ${formatTime(context, range.startHour, range.startMinute)}")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    timePickerTarget.value = TimePickerTarget(
                                        rangeIndex = index,
                                        isStart = false
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("End: ${formatTime(context, range.endHour, range.endMinute)}")
                            }
                            if (timeRanges.size > 1) {
                                OutlinedButton(
                                    onClick = { timeRanges.removeAt(index) },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Text("-")
                                }
                            }
                        }
                        if (index < timeRanges.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { timeRanges.add(TimeRange()) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Time Range")
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
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
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
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reset Time Limits Periodically")
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = useReset.value,
                    onCheckedChange = {
                        val isPremium = com.juliacai.apptick.PremiumStore.isPremium(context)
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
                     HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
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
                    val hr = timeHrLimit.value.toIntOrNull() ?: 0
                    val min = timeMinLimit.value.toIntOrNull() ?: 0
                    if (useTimeLimit.value && hr == 0 && min == 0) {
                        showZeroTimeSaveWarning.value = true
                    } else {
                        doSave()
                    }
                }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }

        if (showNoTimeLimitWarning.value) { AlertDialog(onDismissRequest = { showNoTimeLimitWarning.value = false; useTimeLimit.value = true }, title = { Text("Always Block App") }, text = { Text("Are you sure you want the app to be blocked with 0 time use?") }, confirmButton = { Button({ showNoTimeLimitWarning.value = false }) { Text("Continue") } }, dismissButton = { Button({ showNoTimeLimitWarning.value = false; useTimeLimit.value = true }) { Text("Cancel") } }) }
        if (showZeroTimeSaveWarning.value) {
            AlertDialog(
                onDismissRequest = { showZeroTimeSaveWarning.value = false },
                title = { Text("Always Block App") },
                text = { Text("Are you sure you want the app to be blocked with 0 time use?") },
                confirmButton = {
                    Button({
                        showZeroTimeSaveWarning.value = false
                        useTimeLimit.value = false
                        doSave()
                    }) { Text("Continue") }
                },
                dismissButton = {
                    Button({
                        showZeroTimeSaveWarning.value = false
                    }) { Text("Cancel") }
                }
            )
        }
        timePickerTarget.value?.let { pickerTarget ->
            val context = androidx.compose.ui.platform.LocalContext.current
            val currentRange = timeRanges.getOrNull(pickerTarget.rangeIndex) ?: TimeRange()
            val initialHour = if (pickerTarget.isStart) currentRange.startHour else currentRange.endHour
            val initialMinute = if (pickerTarget.isStart) currentRange.startMinute else currentRange.endMinute
            androidx.compose.runtime.LaunchedEffect(pickerTarget) {
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val existing = timeRanges.getOrNull(pickerTarget.rangeIndex) ?: return@TimePickerDialog
                        timeRanges[pickerTarget.rangeIndex] = if (pickerTarget.isStart) {
                            existing.copy(startHour = hour, startMinute = minute)
                        } else {
                            existing.copy(endHour = hour, endMinute = minute)
                        }
                        timePickerTarget.value = null
                    },
                    initialHour,
                    initialMinute,
                    android.text.format.DateFormat.is24HourFormat(context)
                ).apply {
                    setOnCancelListener { timePickerTarget.value = null }
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

private data class TimePickerTarget(
    val rangeIndex: Int,
    val isStart: Boolean
)

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun SetTimeLimitsScreenPreview() {
    var previewSelectedApps by remember {
        mutableStateOf(
            listOf(
                AppInfo(appName = "YouTube", appPackage = "com.google.android.youtube"),
                AppInfo(appName = "Instagram", appPackage = "com.instagram.android"),
                AppInfo(appName = "TikTok", appPackage = "com.zhiliaoapp.musically")
            )
        )
    }
    var previewDraft by remember {
        mutableStateOf(
            SetTimeLimitDraft(
                groupName = "Social Apps",
                useTimeLimit = true,
                timeHrLimit = "1",
                timeMinLimit = "30",
                limitEach = false,
                useTimeRange = true,
                blockOutsideTimeRange = true,
                timeRanges = listOf(TimeRange(startHour = 9, startMinute = 0, endHour = 22, endMinute = 0)),
                startHour = 9,
                startMinute = 0,
                endHour = 22,
                endMinute = 0,
                weekDays = listOf(1, 2, 3, 4, 5),
                cumulativeTime = false,
                useReset = true,
                resetHours = "4",
                resetMinutes = "0"
            )
        )
    }

    AppTheme {
        SetTimeLimitsScreenContent(
            group = null,
            draft = previewDraft,
            selectedApps = previewSelectedApps,
            onSelectedAppsChange = { previewSelectedApps = it },
            onDraftChange = { previewDraft = it },
            onFinish = {},
            onCancel = {},
            onEditApps = {},
            onUpgradeToPremium = {}
        )
    }
}
