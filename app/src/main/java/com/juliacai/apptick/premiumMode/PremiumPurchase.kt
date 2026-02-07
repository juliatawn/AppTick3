//package com.juliacai.apptick.premiumMode
//
//import android.content.Intent
//import android.os.Bundle
//import android.widget.TextView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.lifecycleScope
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
//import com.juliacai.apptick.AppTheme
//import com.juliacai.apptick.BaseActivity
//import com.juliacai.apptick.R
//import com.juliacai.apptick.databinding.ActivityPremiumPurchaseBinding
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//class PremiumPurchase : BaseActivity(), PurchasesUpdatedListener {
//
//    private lateinit var binding: ActivityPremiumPurchaseBinding
//    private lateinit var mBillingClient: BillingClient
//    private var premiumModeProductDetails: ProductDetails? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityPremiumPurchaseBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        AppTheme.applyTheme(this)
//
//        setupFeatureTexts()
//        setupClickListeners()
//        setupBillingClient()
//    }
//
//    private fun setupBillingClient() {
//        mBillingClient = BillingClient.newBuilder(this)
//            .enablePendingPurchases()
//            .setListener(this)
//            .build()
//
//        mBillingClient.startConnection(object : BillingClientStateListener {
//            override fun onBillingSetupFinished(billingResult: BillingResult) {
//                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                    queryPurchases()
//                    queryProducts()
//                }
//            }
//
//            override fun onBillingServiceDisconnected() {
//                // Retry connection logic
//                setupBillingClient()
//            }
//        })
//    }
//
//    private fun setupFeatureTexts() {
//        (findViewById<TextView>(R.id.feature1).findViewById<TextView>(android.R.id.text1)).text = "Remove all ads"
//        (findViewById<TextView>(R.id.feature2).findViewById<TextView>(android.R.id.text1)).text = "Unlimited app limits"
//        (findViewById<TextView>(R.id.feature3).findViewById<TextView>(android.R.id.text1)).text = "Password protection"
//        (findViewById<TextView>(R.id.feature4).findViewById<TextView>(android.R.id.text1)).text = "Custom app colors"
//    }
//
//    private fun setupClickListeners() {
//        binding.btnPurchase.isEnabled = false // Disable until price is loaded
//        binding.btnPurchase.setOnClickListener {
//            premiumModeProductDetails?.let { productDetails ->
//                val productDetailsParamsList = listOf(
//                    BillingFlowParams.ProductDetailsParams.newBuilder()
//                        .setProductDetails(productDetails)
//                        .build()
//                )
//                val billingFlowParams = BillingFlowParams.newBuilder()
//                    .setProductDetailsParamsList(productDetailsParamsList)
//                    .build()
//
//                mBillingClient.launchBillingFlow(this, billingFlowParams)
//            } ?: run {
//                Toast.makeText(this, "Premium features are currently unavailable", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        binding.btnWatchAd.setOnClickListener {
//            startActivity(Intent(this, AdBasedPremiumActivity::class.java))
//        }
//
//        binding.restoreButton.setOnClickListener {
//            queryPurchases()
//        }
//
//        binding.buttonCancel.setOnClickListener {
//            finish() // Finish activity instead of restarting MainActivity
//        }
//    }
//
//    private fun queryProducts() {
//        val productList = listOf(
//            QueryProductDetailsParams.Product.newBuilder()
//                .setProductId("premium_mode") // Your product ID
//                .setProductType(BillingClient.ProductType.INAPP)
//                .build()
//        )
//        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)
//
//        mBillingClient.queryProductDetailsAsync(params.build()) { billingResult, productDetailsList ->
//            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
//                premiumModeProductDetails = productDetailsList.find { it.productId == "premium_mode" }
//                premiumModeProductDetails?.let {
//                    val price = it.oneTimePurchaseOfferDetails?.formattedPrice
//                    binding.textViewPrice.text = price
//                    binding.btnPurchase.isEnabled = true
//                }
//            }
//        }
//    }
//
//    private fun queryPurchases() {
//        val params = QueryPurchasesParams.newBuilder()
//            .setProductType(BillingClient.ProductType.INAPP)
//            .build()
//        mBillingClient.queryPurchasesAsync(params) { billingResult, purchases ->
//            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                handlePurchases(purchases, isRestored = true)
//            }
//        }
//    }
//
//    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
//        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
//            handlePurchases(purchases, isRestored = false)
//        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
//            Toast.makeText(this, "Purchase canceled", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun handlePurchases(purchases: List<Purchase>, isRestored: Boolean) {
//        lifecycleScope.launch(Dispatchers.Main) {
//            for (purchase in purchases) {
//                if ("premium_mode" in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
//                    if (!purchase.isAcknowledged) {
//                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
//                            .setPurchaseToken(purchase.purchaseToken)
//                            .build()
//                        mBillingClient.acknowledgePurchase(acknowledgePurchaseParams) { ackResult ->
//                            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                                grantPremiumAndFinish(isRestored)
//                            }
//                        }
//                    } else {
//                        grantPremiumAndFinish(isRestored)
//                    }
//                    return@launch
//                }
//            }
//        }
//    }
//
//    private fun grantPremiumAndFinish(isRestored: Boolean) {
//        val editor = getSharedPreferences("groupPrefs", MODE_PRIVATE).edit()
//        editor.putBoolean("premium", true)
//        editor.apply()
//
//        val message = if (isRestored) "Premium restored successfully!" else "Premium purchased successfully!"
//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
//        finish()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        if (::mBillingClient.isInitialized) {
//            mBillingClient.endConnection()
//        }
//    }
//
//    override fun applyColors() {
//        super.applyColors()
//        sharedPreferences?.let { prefs ->
//            val primaryColor = prefs.getInt(PREF_PRIMARY_COLOR, ContextCompat.getColor(this, R.color.colorPrimary))
//            toolbar?.setBackgroundColor(primaryColor)
//        }
//    }
//}
