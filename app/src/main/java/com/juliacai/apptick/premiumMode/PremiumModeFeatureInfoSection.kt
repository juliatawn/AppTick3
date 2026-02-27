package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PremiumModeFeatureInfoSection(
    modifier: Modifier = Modifier
) {
    PremiumModeKeyFeaturesCard(
        modifier = modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    PremiumModeFeatureDetailsCard(modifier = modifier.fillMaxWidth())
}

@Composable
private fun PremiumModeKeyFeaturesCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Key Features:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("• Time Range")
            Text("• Reset time limits periodically with optional Cumulative Time Mode")
            Text("• Floating Time Left Bubble")
            Text("• Lockdown mode")
            Text("• Password mode")
            Text("• Backup AppTick app limits and settings as a file, and import")
            Text("• Fingerprint/Biometrics, and USB security key alternative unlock for Password mode")
            Text("• Dark mode and AppTick color theming")
            Text("• Duplicate existing groups")
        }
    }
}

@Composable
private fun PremiumModeFeatureDetailsCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Details of Features", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Additional Time Limit Options", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            FeatureDetailText(
                label = "Time Range:",
                description = "Set app limits for a specific time range. Outside the range, apps can be always blocked or have no time limits."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureDetailText(
                label = "Reset time limits periodically:",
                description = "Reset app limits on any hour/minute interval you choose.\nExample - If interval is 2h 30m and limit is 15m, every 2.5 hours you get another 15 minutes."
            )
            Spacer(modifier = Modifier.height(6.dp))
            FeatureDetailText(
                label = "Optional Cumulative Time Mode:",
                description = "Unused time carries into the next interval until 12:00 AM, then resets fresh for the day."
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureDetailText(
                label = "Floating Time Left Bubble:",
                description = "A moveable bubble appears while using apps with available time and counts down remaining time. Position is remembered per app."
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Multiple lock mode options", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            FeatureDetailText(
                label = "Password mode:",
                description = "Use a Password and optionally biometrics (fingerprint), optionally a security key to lock your app limit settings from being changed, and allows only those with authorized access to change them."
            )
            Spacer(modifier = Modifier.height(6.dp))
            FeatureDetailText(
                label = "Lockdown mode:",
                description = "Have bad self control? Make it so you can only change your app limits on a chosen date or day(s) of the week, otherwise your app limits are unchangable."
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            FeatureDetailText(
                label = "Dark mode:",
                description = "Option to have the app in dark mode"
            )
            Spacer(modifier = Modifier.height(6.dp))
            FeatureDetailText(
                label = "Color theme customization:",
                description = "Option to change AppTick colors with a pallete of colors, also works with dark mode."
            )
            FeatureDetailText(
                label = "Duplicate existing groups:",
                description = "You can easily copy over the settings from one group to a new group, then modify it however you like - just click the 'edit' button"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
