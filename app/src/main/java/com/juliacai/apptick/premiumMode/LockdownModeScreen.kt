package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.LockdownType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownModeScreen(
    statusText: String,
    lockdownType: LockdownType,
    recurringDays: List<Int>, // 1=Mon, 7=Sun
    protectSettingsUninstall: Boolean,
    isDeviceAdminGranted: Boolean,
    onLockdownTypeChanged: (LockdownType) -> Unit,
    onRecurringDaysChanged: (List<Int>) -> Unit,
    onProtectSettingsUninstallToggled: (Boolean) -> Unit,
    onEnableDeviceAdminClick: () -> Unit,
    onConfigureEndTimeClick: () -> Unit,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = statusText, style = MaterialTheme.typography.bodyLarge)
            
            if (isLockdownActive) {
                // If active, user can only Disable (if allowed)
                // "Each lock mode when set has a START MODE button or CANCEL button at the bottom... If user sets up lockmode, when they reenter it will show settings..."
                // Logic: If locked, they can only "Disable Lockdown".
                // But wait, if they are here, they passed the security check (if needed).
                // Lockdown doesn't have a password itself (unless Password Mode is separate).
                // If Lockdown is active, they can't change settings UNLESS they are in a window?
                // If they are in a window, they can change settings OR disable it.
                // StatusText should say "Active until..." or "Window open until...".
                
                Button(
                    onClick = onDisableLockdownClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disable Lockdown")
                }
            } else {
                // Configuration Mode
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
                    Text("Lock editing until a specific date and time.")
                    Button(
                        onClick = onConfigureEndTimeClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set End Date/Time")
                    }
                } else {
                    Text("Allow editing only on specific days of the week.")
                    // Day Selector
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
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = protectSettingsUninstall,
                        onCheckedChange = onProtectSettingsUninstallToggled
                    )
                    Text("Protect uninstall from Settings")
                }
                
                if (protectSettingsUninstall) {
                    Text("Device Admin: ${if (isDeviceAdminGranted) "Enabled" else "Not enabled"}")
                    if (!isDeviceAdminGranted) {
                         Button(
                            onClick = onEnableDeviceAdminClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Device Admin")
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onStartLockdownClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Lockdown")
                }
            }
        }
    }
}
