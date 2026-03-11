package com.juliacai.apptick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    appLimitGroupCount: Int,
    showLockedIcon: Boolean,
    showLockNowButton: Boolean,
    showGroupDetailsHint: Boolean,
    showBatteryWarning: Boolean,
    batteryWarningDismissable: Boolean,
    showStandardBatteryWarning: Boolean,
    batteryWarningDetails: List<Pair<String, String>>,
    oemGuidance: String?,
    hasOemRestrictions: Boolean,
    onFabClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onLockNowClick: () -> Unit,
    onOpenAppBatterySettings: () -> Unit,
    onOpenGeneralBatterySettings: () -> Unit,
    onOpenDontKillMyApp: () -> Unit,
    onOpenOemStartupSettings: () -> Unit,
    onRefreshBatteryStatus: () -> Unit,
    onDismissBatteryWarning: () -> Unit,
    onDismissGroupDetailsHint: () -> Unit,
    listContent: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AppTick",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                actions = {
                    if (showLockNowButton) {
                        TextButton(onClick = onLockNowClick) {
                            Text(
                                text = "LOCK NOW",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    IconButton(
                        onClick = onPremiumClick,
                        modifier = Modifier.size(52.dp)
                    ) {
                        if (showLockedIcon) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock modes are locked"
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_unlocked),
                                contentDescription = "Open lock modes"
                            )
                        }
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_app_limit)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (showBatteryWarning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Battery Reliability",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (showStandardBatteryWarning) {
                            Text(
                                "Set AppTick to Unrestricted battery mode for stronger blocking reliability.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (batteryWarningDetails.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            batteryWarningDetails.forEach { (label, value) ->
                                Text(
                                    text = buildAnnotatedString {
                                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                        append(label)
                                        pop()
                                        append(" ")
                                        append(value)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                        if (oemGuidance != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                oemGuidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Some manufacturers aggressively kill apps in the background. If reliability issues continue, review device-specific steps at dontkillmyapp.com.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenAppBatterySettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open App Battery Settings")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenGeneralBatterySettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open General Battery Settings")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenDontKillMyApp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open dontkillmyapp.com")
                        }
                        if (hasOemRestrictions) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenOemStartupSettings,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open OEM Startup Settings")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRefreshBatteryStatus,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Refresh")
                        }
                        if (batteryWarningDismissable) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = onDismissBatteryWarning,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Dismiss This Warning")
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (appLimitGroupCount == 0) {
                    Text(
                        text = stringResource(id = R.string.add_app_limit),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Column {
                        if (showGroupDetailsHint) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Tap any group card to open its details page.\nYou can also hold and drag any group to reorder them",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    ) {
                                        TextButton(
                                            onClick = onDismissGroupDetailsHint,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                        ) {
                                            Text("DISMISS")
                                        }
                                    }
                                }
                            }
                        }
                        listContent()
                    }
                }
            }

        }
    }
}

@Preview
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreen(
            appLimitGroupCount = 1,
            showLockedIcon = true,
            showLockNowButton = false,
            showGroupDetailsHint = true,
            showBatteryWarning = true,
            batteryWarningDismissable = true,
            showStandardBatteryWarning = true,
            batteryWarningDetails = listOf("Detail 1" to "Value 1", "Detail 2" to "Value 2"),
            oemGuidance = "Also allow AppTick in App launch settings.",
            hasOemRestrictions = true,
            onFabClick = {},
            onSettingsClick = {},
            onPremiumClick = {},
            onLockNowClick = {},
            onOpenAppBatterySettings = {},
            onOpenGeneralBatterySettings = {},
            onOpenDontKillMyApp = {},
            onOpenOemStartupSettings = {},
            onRefreshBatteryStatus = {},
            onDismissBatteryWarning = {},
            onDismissGroupDetailsHint = {},
            listContent = { Text("List content") }
        )
    }
}
