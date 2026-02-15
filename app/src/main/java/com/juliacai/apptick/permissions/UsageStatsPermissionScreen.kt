package com.juliacai.apptick.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun UsageStatsPermissionScreen(
    onGoToSettingsClick: () -> Unit,
    onNextClick: () -> Unit,
    isPermissionGranted: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AppTick needs access to your app usage stats to work correctly.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This allows us to monitor app usage and enforce the limits you set.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGoToSettingsClick) {
            Text("Go to Settings")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNextClick, enabled = isPermissionGranted) {
            Text("Next")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UsageStatsPermissionScreenPreview() {
    UsageStatsPermissionScreen(
        onGoToSettingsClick = {},
        onNextClick = {},
        isPermissionGranted = false
    )
}
