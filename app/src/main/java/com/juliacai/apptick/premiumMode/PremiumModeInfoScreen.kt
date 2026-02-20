package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumModeInfoScreen(
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium Mode Info") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Support the developer and gain these handy features:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Time Range")
                    Text("• Reset time limits periodically with optional Cumulative Time Mode")
                    Text("• Floating Time Left Bubble")
                    Text("• Lockdown mode")
                    Text("• Password mode")
                    Text("• Security key mode")
                    Text("• Custom AppTick color theming")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Details of Features", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Additional Time Limit Options", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Time Range: Set app limits for a specific time range. Outside the range, apps can be always blocked or have no time limits.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Reset time limits periodically: Reset app limits on any hour/minute interval you choose.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Example: If interval is 2h 30m and limit is 15m, every 2.5 hours you get another 15 minutes.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Optional Cumulative Time Mode: Unused time carries into the next interval until 12:00 AM, then resets fresh for the day.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Floating Time Left Bubble", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("A moveable bubble appears while using apps with available time and counts down remaining time. Position is remembered per app.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Three lock mode options", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Lockdown mode")
                    Text("• Password mode")
                    Text("• Security key mode")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Customize AppTick background, text, and app colors with recommended palettes or a color picker wheel.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Premium price starts at $4.99 USD and is localized in Google Play based on region/currency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
