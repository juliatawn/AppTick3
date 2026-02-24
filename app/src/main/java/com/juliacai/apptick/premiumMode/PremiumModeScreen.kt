package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.android.billingclient.api.ProductDetails
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.R
import com.juliacai.apptick.lockModes.SetPassword
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumModeScreen(
    productDetails: ProductDetails?,
    isPremium: Boolean,
    activeLockMode: LockMode,
    onPurchaseClick: (ProductDetails) -> Unit,
    navController: NavController,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val titleText = if (isPremium) "Lock Modes" else "Premium Mode"
    val localizedPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$4.99"
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        maxLines = 1,
                        softWrap = false
                    )
                },
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
        },
        bottomBar = {
            if (!isPremium) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = { productDetails?.let(onPurchaseClick) },
                            enabled = productDetails != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Buy Premium - $localizedPrice")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (productDetails == null) {
                            Text(
                                text = "Loading localized price and purchase option...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "One-time purchase. Price is localized by Google Play.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScrollWithIndicator(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            if (isPremium) {
                var isLockModesInfoExpanded by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "What Lock Modes do",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lock Modes protect your app-limit settings from changes.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (isLockModesInfoExpanded) {
                            Text(
                                text = buildAnnotatedString {
                                    append("• ")
                                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                    append("Password Mode")
                                    pop()
                                    append(": requires your password (optional: fingerprint/biometrics, security key) to add/edit limits or open this page.")
                                },
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = buildAnnotatedString {
                                    append("• ")
                                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                    append("Lockdown Mode")
                                    pop()
                                    append(": blocks editing until your allowed date/time window.\nMake it so you can only change your app limits on a chosen date or day(s) of the week, otherwise your app limits are unchangable.")
                                },
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OutlinedButton(onClick = { isLockModesInfoExpanded = !isLockModesInfoExpanded }) {
                                Text(if (isLockModesInfoExpanded) "COLLAPSE" else "EXPAND")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Helper to render mode button or disabled state
                @Composable
                fun LockModeButton(
                    modeName: String,
                    targetMode: LockMode,
                    onClick: () -> Unit
                ) {
                    val isEnabled = activeLockMode == LockMode.NONE || activeLockMode == targetMode
                    if (isEnabled) {
                        Button(
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(modeName)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disable ${activeLockMode.name} to use $modeName")
                        }
                    }
                }

                LockModeButton(
                    modeName = "Lockdown Mode",
                    targetMode = LockMode.LOCKDOWN,
                    onClick = { context.startActivity(android.content.Intent(context, LockdownModeActivity::class.java)) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                LockModeButton(
                    modeName = "Password Mode",
                    targetMode = LockMode.PASSWORD,
                    onClick = { context.startActivity(android.content.Intent(context, SetPassword::class.java)) }
                )

            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_premium_mode_emblem),
                    contentDescription = "Premium mode emblem",
                    modifier = Modifier
                        .size(112.dp)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Support the developer and unlock Premium Mode",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Details of Features", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Additional Time Limit Options",
                            style = MaterialTheme.typography.titleSmall
                        )
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
                        Text(
                            "Multiple lock mode options",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FeatureDetailText(
                            label = "Password mode:",
                            description = "Use a Password and optionally biometrics (fingerprint), optionally a security key to lock your app limit settings from being changed, and allows only those with authorized access to change them."
                        )
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
                        FeatureDetailText(
                            label = "Color theme customization:",
                            description = "Option to change AppTick colors with a pallete of colors, also works with dark mode."
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (productDetails == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
                Spacer(modifier = Modifier.height(92.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PremiumModeScreenPreview() {
    PremiumModeScreen(
        productDetails = null, 
        isPremium = true, 
        activeLockMode = LockMode.NONE,
        onPurchaseClick = {}, 
        navController = rememberNavController(), 
        onBackClick = {}
    )
}
