package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.juliacai.apptick.lockModes.SetPassword

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumModeScreen(
    productDetails: ProductDetails?,
    isPremium: Boolean,
    onPurchaseClick: (ProductDetails) -> Unit,
    navController: NavController,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val titleText = if (isPremium) "Lock Modes" else "Premium Mode"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPremium) {
                Text(
                    text = "Lock Modes",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { context.startActivity(android.content.Intent(context, LockdownModeActivity::class.java)) }) {
                    Text("Lockdown Mode")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { context.startActivity(android.content.Intent(context, SetPassword::class.java)) }) {
                    Text("Password Mode")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navController.navigate("securityKeySettings") }) {
                    Text("Security Key Mode")
                }
            } else {
                Text(
                    text = "Unlock Premium Features!",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Support the developer and get access to exclusive features like Lockdown Mode, Password Mode, and more!",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                if (productDetails != null) {
                    Button(onClick = { onPurchaseClick(productDetails) }) {
                        Text("Purchase for ${productDetails.oneTimePurchaseOfferDetails?.formattedPrice}")
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PremiumModeScreenPreview() {
    PremiumModeScreen(productDetails = null, isPremium = true, onPurchaseClick = {}, navController = rememberNavController(), onBackClick = {})
}
