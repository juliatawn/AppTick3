package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownModeScreen(
    statusText: String,
    oneTimeWeeklyChange: Boolean,
    onOneTimeWeeklyChangeToggled: (Boolean) -> Unit,
    onConfigureEndTimeClick: () -> Unit,
    onDisableLockdownClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lockdown Mode") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
            Text(text = statusText)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = oneTimeWeeklyChange,
                    onCheckedChange = onOneTimeWeeklyChangeToggled
                )
                Text("Allow one weekly limit-change window")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onConfigureEndTimeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Lockdown End Date/Time")
            }

            Button(
                onClick = onDisableLockdownClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disable Lockdown")
            }
        }
    }
}
