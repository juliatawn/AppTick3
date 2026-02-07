package com.juliacai.apptick

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.billingclient.api.*
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.newAppLimit.TabTimeLimitActivity
import com.juliacai.apptick.permissions.OverlayPermissionPage
import com.juliacai.apptick.appLimit.AppLimitGroup

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var mBillingClient: BillingClient
    private val viewModel: MainViewModel by viewModels()

    private var mService: BackgroundChecker? = null
    private var mBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BackgroundChecker.LocalBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("AppTickPrefs", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        checkUsageStatsPermission()
        checkOverlayPermission()

        setupBilling()

        setContent {
            val navController = rememberNavController()
            val groups by viewModel.groups.observeAsState(emptyList())
            val isPremium by viewModel.isPremium.observeAsState(false)

            MaterialTheme {
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            appLimitGroupCount = groups.size,
                            isPremium = isPremium,
                            onFabClick = {
                                startActivity(Intent(this@MainActivity, TabTimeLimitActivity::class.java))
                            },
                            onSettingsClick = {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            },
                            listContent = {
                                AppLimitGroupsList(groups = groups.map { appLimitGroup ->
                                    AppLimitGroup(
                                        id = appLimitGroup.id,
                                        name = appLimitGroup.name,
                                        timeHrLimit = appLimitGroup.timeHrLimit,
                                        timeMinLimit = appLimitGroup.timeMinLimit,
                                        limitEach = appLimitGroup.limitEach,
                                        resetHours = appLimitGroup.resetHours,
                                        weekDays = appLimitGroup.weekDays,
                                        apps = appLimitGroup.apps,
                                        paused = appLimitGroup.paused,
                                        useTimeRange = appLimitGroup.useTimeRange,
                                        startHour = appLimitGroup.startHour,
                                        startMinute = appLimitGroup.startMinute,
                                        endHour = appLimitGroup.endHour,
                                        endMinute = appLimitGroup.endMinute,
                                        cumulativeTime = appLimitGroup.cumulativeTime,
                                        timeRemaining = appLimitGroup.timeRemaining,
                                        nextResetTime = appLimitGroup.nextResetTime,
                                        nextAddTime = appLimitGroup.nextAddTime
                                    )
                                })
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, BackgroundChecker::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (mBound) {
            unbindService(serviceConnection)
            mBound = false
        }
    }

    private fun checkUsageStatsPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(applicationContext)) {
            startActivity(Intent(this, OverlayPermissionPage::class.java))
        }
    }

    private fun setupBilling() {
        mBillingClient = BillingClient.newBuilder(applicationContext)
            .enablePendingPurchases()
            .setListener(this)
            .build()
        mBillingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                setupBilling()
            }
        })
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        mBillingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = purchases.any { "premium_mode" in it.products }
                viewModel.updatePremiumStatus(hasPremium)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            val hasPremium = purchases.any { "premium_mode" in it.products }
            viewModel.updatePremiumStatus(hasPremium)
        }
    }
}
