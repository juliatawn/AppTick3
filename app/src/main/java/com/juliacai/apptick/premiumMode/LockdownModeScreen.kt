package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.LockdownType
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownModeScreen(
    statusText: String,
    lockdownType: LockdownType,
    recurringDays: List<Int>, // 1=Mon, 7=Sun
    selectedDate: String,
    selectedTime: String,
    onLockdownTypeChanged: (LockdownType) -> Unit,
    onRecurringDaysChanged: (List<Int>) -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onStartLockdownClick: () -> Unit,
    onCancelClick: () -> Unit,
    isLockdownActive: Boolean,
    canEditCurrentLockdownSettings: Boolean,
    isConfigurationEnabled: Boolean,
    onConfigurationEnabledChange: (Boolean) -> Unit,
    onDisabledInteraction: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lockdown Mode",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancelClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScrollWithIndicator()
                .clickable(
                    enabled = !isConfigurationEnabled,
                    interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDisabledInteraction()
                },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(14.dp)
                )
            }
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clickable(
                            enabled = !isConfigurationEnabled,
                            interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Lockdown Mode", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = isConfigurationEnabled,
                        onCheckedChange = onConfigurationEnabledChange
                    )
                }
            }

            if (!isLockdownActive || canEditCurrentLockdownSettings) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TabRow(selectedTabIndex = if (lockdownType == LockdownType.ONE_TIME) 0 else 1) {
                            Tab(
                                selected = lockdownType == LockdownType.ONE_TIME,
                                enabled = isConfigurationEnabled,
                                onClick = { onLockdownTypeChanged(LockdownType.ONE_TIME) },
                                text = { Text("One-Time Date") }
                            )
                            Tab(
                                selected = lockdownType == LockdownType.RECURRING,
                                enabled = isConfigurationEnabled,
                                onClick = { onLockdownTypeChanged(LockdownType.RECURRING) },
                                text = { Text("Recurring Schedule") }
                            )
                        }

                        if (lockdownType == LockdownType.ONE_TIME) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(
                                            "Unlock on one date",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Text("Pick a date/time when editing can happen.")
                                }
                            }
                            Button(
                                onClick = onDateClick,
                                enabled = isConfigurationEnabled,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Date: $selectedDate")
                            }
                            Button(
                                onClick = onTimeClick,
                                enabled = isConfigurationEnabled,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Time: $selectedTime")
                            }
                        } else {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(
                                            "Unlock on selected weekdays",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Text("Choose the day(s) when edits and start/stop are allowed.")
                                    Text(
                                        "Outside selected days, limits lock again automatically until the next selected day. On selected days, you can still lock sooner after changes if you want. While Lockdown mode is configured, Password and Security Key modes stay unavailable."
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                                days.forEachIndexed { index, day ->
                                    val dayIndex = index + 1 // 1=Mon
                                    val selected = recurringDays.contains(dayIndex)
                                    OutlinedButton(
                                        onClick = {
                                            val newDays = recurringDays.toMutableList()
                                            if (newDays.contains(dayIndex)) newDays.remove(dayIndex) else newDays.add(dayIndex)
                                            onRecurringDaysChanged(newDays.sorted())
                                        },
                                        enabled = isConfigurationEnabled,
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
                        }

                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Button(
                            onClick = onStartLockdownClick,
                            enabled = isConfigurationEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun LockdownModeScreenPreview() {
    AppTheme {
        LockdownModeScreen(
            statusText = "Lockdown is currently off.",
            lockdownType = LockdownType.RECURRING,
            recurringDays = listOf(1, 3, 5),
            selectedDate = "Mar 1, 2026",
            selectedTime = "07:00 PM",
            onLockdownTypeChanged = {},
            onRecurringDaysChanged = {},
            onDateClick = {},
            onTimeClick = {},
            onStartLockdownClick = {},
            onCancelClick = {},
            isLockdownActive = false,
            canEditCurrentLockdownSettings = true,
            isConfigurationEnabled = true,
            onConfigurationEnabledChange = {},
            onDisabledInteraction = {}
        )
    }
}
