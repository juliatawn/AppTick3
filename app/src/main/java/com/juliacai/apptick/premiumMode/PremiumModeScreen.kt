package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.android.billingclient.api.ProductDetails
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.lockModes.SetPassword

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
    val localizedPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$4.99 USD"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText) },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            if (isPremium) {
                Text(
                    text = "Lock Modes",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Start
                )
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
                Spacer(modifier = Modifier.height(16.dp))
                
                LockModeButton(
                    modeName = "Security Key Mode",
                    targetMode = LockMode.SECURITY_KEY,
                    onClick = { navController.navigate("securityKeySettings") }
                )

            } else {
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
                        Text(
                            "Additional Time Limit Options",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Time Range: Set app limits for a specific time range. Outside the range, apps can be always blocked or have no time limits."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Reset time limits periodically: Reset app limits on any hour/minute interval you choose."
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Example: If interval is 2h 30m and limit is 15m, every 2.5 hours you get another 15 minutes."
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Optional Cumulative Time Mode: Unused time carries into the next interval until 12:00 AM, then resets fresh for the day."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Floating Time Left Bubble",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "A moveable bubble appears while using apps with available time and counts down remaining time. Position is remembered per app."
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Three lock mode options",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Lockdown mode")
                        Text("• Password mode")
                        Text("• Security key mode")
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Customize AppTick background, text, and app colors with recommended palettes or a color picker wheel."
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
