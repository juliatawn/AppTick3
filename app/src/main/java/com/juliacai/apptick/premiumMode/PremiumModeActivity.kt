//package com.juliacai.apptick.premiumMode
//
//import android.os.Bundle
//import android.widget.Toast
//import androidx.activity.compose.setContent
//import androidx.activity.viewModels
//import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.livedata.observeAsState
//import androidx.compose.runtime.mutableStateOf
//import com.android.billingclient.api.AcknowledgePurchaseParams
//import com.android.billingclient.api.BillingClient
//import com.android.billingclient.api.BillingClientStateListener
//import com.android.billingclient.api.BillingFlowParams
//import com.android.billingclient.api.BillingResult
//import com.android.billingclient.api.ProductDetails
//import com.android.billingclient.api.Purchase
//import com.android.billingclient.api.PurchasesUpdatedListener
//import com.android.billingclient.api.QueryProductDetailsParams
//import com.android.billingclient.api.QueryPurchasesParams
//import com.juliacai.apptick.MainViewModel
//
//class PremiumModeActivity : AppCompatActivity(), PurchasesUpdatedListener {
//
//    private lateinit var billingClient: BillingClient
//    private val productDetails = mutableStateOf<ProductDetails?>(null)
//    private val viewModel: MainViewModel by viewModels()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        setupBillingClient()
//
//        setContent {
//            val isPremium by viewModel.isPremium.observeAsState(false)
//            PremiumModeScreen(
//                productDetails = productDetails.value,
//                isPremium = isPremium,
//                onPurchaseClick = { product ->
//                    launchPurchaseFlow(product)
//                },
//                onBackClick = { finish() }
//            )
//        }
//    }
//
//    private fun setupBillingClient() {
//        billingClient = BillingClient.newBuilder(this)
//            .setListener(this)
//            .enablePendingPurchases()
//            .build()
//
//        billingClient.startConnection(object : BillingClientStateListener {
//            override fun onBillingSetupFinished(billingResult: BillingResult) {
//                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                    queryProductDetails()
//                    checkPendingPurchases()
//                }
//            }
//            override fun onBillingServiceDisconnected() {
//                // Try to restart the connection on the next request to
//                // Google Play by calling the startConnection() method.
//            }
//        })
//    }
//
//    private fun checkPendingPurchases() {
//        val params = QueryPurchasesParams.newBuilder()
//            .setProductType(BillingClient.ProductType.INAPP)
//            .build()
//        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
//            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
//                for (purchase in purchases) {
//                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
//                        handlePurchase(purchase)
//                    }
//                }
//            }
//        }
//    }
//
//    private fun queryProductDetails() {
//        val productList = listOf(
//            QueryProductDetailsParams.Product.newBuilder()
//                .setProductId("premium_mode")
//                .setProductType(BillingClient.ProductType.INAPP)
//                .build()
//        )
//        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)
//
//        billingClient.queryProductDetailsAsync(params.build()) { _, detailsList ->
//            if (detailsList.isNotEmpty()) {
//                productDetails.value = detailsList[0]
//            }
//        }
//    }
//
//    private fun launchPurchaseFlow(productDetails: ProductDetails) {
//        val productDetailsParamsList = listOf(
//            BillingFlowParams.ProductDetailsParams.newBuilder()
//                .setProductDetails(productDetails)
//                .build()
//        )
//        val billingFlowParams = BillingFlowParams.newBuilder()
//            .setProductDetailsParamsList(productDetailsParamsList)
//            .build()
//
//        billingClient.launchBillingFlow(this, billingFlowParams)
//    }
//
//    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
//        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
//            for (purchase in purchases) {
//                handlePurchase(purchase)
//            }
//        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
//            Toast.makeText(this, "Purchase canceled", Toast.LENGTH_SHORT).show()
//        } else {
//            Toast.makeText(this, "Error: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun handlePurchase(purchase: Purchase) {
//        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
//            if (!purchase.isAcknowledged) {
//                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
//                    .setPurchaseToken(purchase.purchaseToken)
//                    .build()
//                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
//                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                        viewModel.updatePremiumStatus(true)
//                        Toast.makeText(this, "Purchase successful!", Toast.LENGTH_SHORT).show()
//                        finish()
//                    }
//                }
//            }
//        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
//            Toast.makeText(this, "Purchase is pending. Please complete the transaction.", Toast.LENGTH_LONG).show()
//        }
//    }
//}
