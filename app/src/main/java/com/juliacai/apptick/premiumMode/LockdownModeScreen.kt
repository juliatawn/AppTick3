package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    protectSettingsUninstall: Boolean,
    isDeviceAdminGranted: Boolean,
    onLockdownTypeChanged: (LockdownType) -> Unit,
    onRecurringDaysChanged: (List<Int>) -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onProtectSettingsUninstallToggled: (Boolean) -> Unit,
    onEnableDeviceAdminClick: () -> Unit,
    onStartLockdownClick: () -> Unit,
    onDisableLockdownClick: () -> Unit,
    onBackClick: () -> Unit,
    isLockdownActive: Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lockdown Mode") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
                .verticalScrollWithIndicator(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(14.dp)
                )
            }

            if (isLockdownActive) {
                Button(
                    onClick = onDisableLockdownClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Turn Off Lockdown")
                }
            } else {
                TabRow(selectedTabIndex = if (lockdownType == LockdownType.ONE_TIME) 0 else 1) {
                    Tab(
                        selected = lockdownType == LockdownType.ONE_TIME,
                        onClick = { onLockdownTypeChanged(LockdownType.ONE_TIME) },
                        text = { Text("One-Time Date") }
                    )
                    Tab(
                        selected = lockdownType == LockdownType.RECURRING,
                        onClick = { onLockdownTypeChanged(LockdownType.RECURRING) },
                        text = { Text("Recurring Schedule") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                    Button(onClick = onDateClick, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Date: $selectedDate")
                    }
                    Button(onClick = onTimeClick, modifier = Modifier.fillMaxWidth()) {
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

                Spacer(modifier = Modifier.height(8.dp))

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
                                "Uninstall protection",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = protectSettingsUninstall,
                                onCheckedChange = onProtectSettingsUninstallToggled
                            )
                            Text("Use Device Admin to reduce uninstall bypass")
                        }
                        Text(
                            "This does not lock the Settings app. It only helps prevent uninstall attempts."
                        )
                        Text("Device Admin: ${if (isDeviceAdminGranted) "Enabled" else "Not enabled"}")
                        if (!isDeviceAdminGranted) {
                            OutlinedButton(
                                onClick = onEnableDeviceAdminClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Device Admin")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onStartLockdownClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
