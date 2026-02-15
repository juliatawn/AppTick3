package com.juliacai.apptick

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.juliacai.apptick.appLimit.AppLimitDetailsScreen
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.LegacyDataMigrator
import com.juliacai.apptick.deviceApps.GroupPage
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppLimitGroupsList
import com.juliacai.apptick.lockModes.EnterPasswordActivity
import com.juliacai.apptick.lockModes.EnterSecurityKeyActivity
import com.juliacai.apptick.lockModes.SecurityKeySettingsScreen
import com.juliacai.apptick.newAppLimit.AppLimitViewModel
import com.juliacai.apptick.newAppLimit.AppSelectScreen
import com.juliacai.apptick.newAppLimit.SetTimeLimitsScreen
import com.juliacai.apptick.permissions.NotificationPermissionPage
import com.juliacai.apptick.permissions.OverlayPermissionPage
import com.juliacai.apptick.permissions.UsageStatsPermissionPage
import com.juliacai.apptick.premiumMode.PremiumModeScreen
import com.juliacai.apptick.premiumMode.LockModesBlockedScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity(), PurchasesUpdatedListener {

    private lateinit var mBillingClient: BillingClient
    private val viewModel: MainViewModel by viewModels()
    private val appLimitViewModel: AppLimitViewModel by viewModels()
    private lateinit var prefs: SharedPreferences
    private val productDetails = mutableStateOf<ProductDetails?>(null)
    private var appInitialized = false
    private var launchEditGroupId: Long? = null
    private var launchOpenLockModes = false

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

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueStartupFlow()
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        continueStartupFlow()
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        continueStartupFlow()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        continueStartupFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeModeManager.apply(this)
        super.onCreate(savedInstanceState)
        launchEditGroupId = intent.getLongExtra(EXTRA_EDIT_GROUP_ID, -1L).takeIf { it != -1L }
        launchOpenLockModes = intent.getBooleanExtra(EXTRA_OPEN_LOCK_MODES, false)

        lifecycleScope.launch(Dispatchers.IO) {
            val appDatabase = AppTickDatabase.getDatabase(applicationContext)
            val migrator = LegacyDataMigrator(applicationContext, appDatabase.appLimitGroupDao())
            migrator.migrate()
        }

        createNotificationChannel()

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val hasSeenLoading = prefs.getBoolean("has_seen_launch_loading", false)
        if (!hasSeenLoading) {
            setContent { AppLaunchLoadingScreen() }
            lifecycleScope.launch {
                delay(900L)
                prefs.edit { putBoolean("has_seen_launch_loading", true) }
                continueStartupFlow()
            }
        } else {
            continueStartupFlow()
        }
    }

    private fun continueStartupFlow() {
        if (checkAllPermissions()) {
            initApp()
        }
    }

    private fun initApp() {
        if (appInitialized) return
        appInitialized = true

        setupBilling()

        setContent {
            val navController = rememberNavController()
            val groups by viewModel.groups.observeAsState(emptyList())
            val isPremium by viewModel.isPremium.observeAsState(false)
            val premiumEnabled = isPremium || isPremiumEnabledNow()
            val lockState = readLockState()
            val lockDecision = LockPolicy.evaluateEditingLock(lockState, System.currentTimeMillis())
            val isLockModesLocked = lockDecision.isLocked
            var pendingEditGroupId by rememberSaveable { mutableStateOf(launchEditGroupId) }
            var pendingOpenLockModes by rememberSaveable { mutableStateOf(launchOpenLockModes) }

            val isSystemDark = isSystemInDarkTheme()
            val customColorModeEnabled = ThemeModeManager.isCustomColorModeEnabled(this)
            
            val savedPrimaryColor = prefs.getInt("custom_primary_color", 0)
            val savedBackgroundColor = prefs.getInt("custom_background_color", 0)
            val savedIconColor = prefs.getInt("custom_icon_color", 0)
            val appIconColorMode = prefs.getString("app_icon_color_mode", "system") ?: "system"

            val composePrimary =
                if (savedPrimaryColor != 0) androidx.compose.ui.graphics.Color(savedPrimaryColor)
                else androidx.compose.ui.graphics.Color("#3949AB".toColorInt())
            
            val defaultBackground = if (isSystemDark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
            val composeBackground = if (savedBackgroundColor != 0) androidx.compose.ui.graphics.Color(savedBackgroundColor) else defaultBackground

            val systemThemeIconColor =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isSystemDark) dynamicDarkColorScheme(this).primary
                    else dynamicLightColorScheme(this).primary
                } else {
                    null
                }
            val fallbackIconColor =
                if (androidx.core.graphics.ColorUtils.calculateLuminance(composeBackground.toArgb()) > 0.5) androidx.compose.ui.graphics.Color.Black
                else androidx.compose.ui.graphics.Color.White
            val composeIconColor =
                if (isPremium && appIconColorMode == "custom" && savedIconColor != 0) {
                    androidx.compose.ui.graphics.Color(savedIconColor)
                } else {
                    systemThemeIconColor?.takeIf { ThemeModeManager.isCustomColorModeEnabled(this) } ?: fallbackIconColor
                }

            val colorScheme = if (customColorModeEnabled) {
                val useDarkScheme = composeBackground.luminance() < 0.4f
                if (useDarkScheme) {
                    androidx.compose.material3.darkColorScheme(
                        primary = composePrimary,
                        background = composeBackground,
                        surface = composeBackground,
                        primaryContainer = composePrimary.copy(alpha = 0.24f),
                        onPrimary = composeIconColor,
                        onBackground = composeIconColor,
                        onSurface = composeIconColor,
                        onPrimaryContainer = composeIconColor
                    )
                } else {
                    androidx.compose.material3.lightColorScheme(
                        primary = composePrimary,
                        background = composeBackground,
                        surface = composeBackground,
                        primaryContainer = composePrimary.copy(alpha = 0.16f),
                        onPrimary = composeIconColor,
                        onBackground = composeIconColor,
                        onSurface = composeIconColor,
                        onPrimaryContainer = composeIconColor
                    )
                }
            } else if (isSystemDark) {
                androidx.compose.material3.darkColorScheme()
            } else {
                androidx.compose.material3.lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                LaunchedEffect(pendingEditGroupId) {
                    val groupId = pendingEditGroupId ?: return@LaunchedEffect
                    appLimitViewModel.loadGroupForEditing(groupId)
                    navController.navigate("setTimeLimit")
                    pendingEditGroupId = null
                    launchEditGroupId = null
                }
                LaunchedEffect(pendingOpenLockModes, isPremium) {
                    if (!pendingOpenLockModes) return@LaunchedEffect
                    if (isPremiumEnabledNow() && !isLimitEditingLocked()) {
                        navController.navigate("premium")
                    }
                    pendingOpenLockModes = false
                    launchOpenLockModes = false
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            appLimitGroupCount = groups.size,
                            showLockedIcon = isLockModesLocked,
                            onFabClick = {
                                if (isLimitEditingLocked()) {
                                    launchUnlockFlow()
                                } else {
                                    markWeeklyLockdownWindowUsedIfNeeded()
                                    appLimitViewModel.clearState()
                                    navController.navigate("selectApps")
                                }
                            },
                            onSettingsClick = { navController.navigate("settings") },
                            onPremiumClick = {
                                if (!isPremiumEnabledNow()) {
                                    navController.navigate("premium")
                                    return@MainScreen
                                }

                                val currentState = readLockState()
                                val decision = LockPolicy.evaluateEditingLock(
                                    currentState,
                                    System.currentTimeMillis()
                                )
                                if (!decision.isLocked) {
                                    navController.navigate("premium")
                                } else if (currentState.hasSecurityKey || currentState.hasPassword) {
                                    launchUnlockFlow(openLockModesAfterUnlock = true)
                                } else if (currentState.lockdownEnabled) {
                                    navController.navigate("lockModesBlocked")
                                } else {
                                    navController.navigate("premium")
                                }
                            },
                            listContent = {
                                AppLimitGroupsList(
                                    groups = groups.map { appLimitGroup ->
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
                                            nextAddTime = appLimitGroup.nextAddTime,
                                            perAppUsage = appLimitGroup.perAppUsage
                                        )
                                    },
                                    onGroupClick = { group ->
                                        startActivity(GroupPage.newIntent(this@MainActivity, group))
                                    },
                                    onLockClick = {
                                        if (prefs.getBoolean("security_key_enabled", false)) {
                                            startActivity(
                                                Intent(
                                                    this@MainActivity,
                                                    EnterSecurityKeyActivity::class.java
                                                )
                                            )
                                        } else {
                                            startActivity(
                                                Intent(
                                                    this@MainActivity,
                                                    EnterPasswordActivity::class.java
                                                )
                                            )
                                        }
                                    },
                                    onPauseToggle = { group -> viewModel.togglePause(group) },
                                    onDelete = { group -> viewModel.deleteGroup(group) },
                                    prefs = prefs
                                )
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBackClick = { navController.popBackStack() },
                            onCustomizeColors = { navController.navigate("colorPicker") },
                            onUpgradeToPremium = { navController.navigate("premium") }
                        )
                    }
                    composable("colorPicker") {
                        com.juliacai.apptick.settings.ColorPickerScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("securityKeySettings") {
                        SecurityKeySettingsScreen(navController)
                    }
                    composable(
                        "appLimitDetails/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val groupId = backStackEntry.arguments?.getLong("groupId")
                        if (groupId != null) {
                            AppLimitDetailsScreen(
                                groupId = groupId,
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onEditClick = { group ->
                                    appLimitViewModel.startEditingGroup(group)
                                    navController.navigate("setTimeLimit")
                                }
                            )
                        }
                    }
                    composable("selectApps") {
                        AppSelectScreen(
                            viewModel = appLimitViewModel,
                            onNextClick = {
                                if (!navController.popBackStack("setTimeLimit", inclusive = false)) {
                                    navController.navigate("setTimeLimit")
                                }
                            },
                            onCancel = {
                                if (!navController.popBackStack("setTimeLimit", inclusive = false)) {
                                    navController.popBackStack("main", inclusive = false)
                                }
                            }
                        )
                    }
                    composable("setTimeLimit") {
                        SetTimeLimitsScreen(
                            viewModel = appLimitViewModel,
                            onFinish = {
                                appLimitViewModel.saveGroup(it)
                                navController.popBackStack(route = "main", inclusive = false)
                            },
                            onCancel = { navController.popBackStack(route = "main", inclusive = false) },
                            onEditApps = {
                                if (!navController.popBackStack("selectApps", inclusive = false)) {
                                    navController.navigate("selectApps")
                                }
                            }
                        )
                    }
                    composable("premium") {
                        PremiumModeScreen(
                            productDetails = productDetails.value,
                            isPremium = premiumEnabled,
                            onPurchaseClick = { product ->
                                launchPurchaseFlow(product)
                            },
                            navController = navController,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("lockModesBlocked") {
                        LockModesBlockedScreen(
                            message = buildLockModesBlockedMessage(),
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun launchUnlockFlow(openLockModesAfterUnlock: Boolean = false) {
        if (prefs.getBoolean("security_key_enabled", false)) {
            startActivity(
                Intent(this, EnterSecurityKeyActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_LOCK_MODES, openLockModesAfterUnlock)
                }
            )
        } else if (!prefs.getString("password", null).isNullOrBlank()) {
            startActivity(
                Intent(this, EnterPasswordActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_LOCK_MODES, openLockModesAfterUnlock)
                }
            )
        } else {
            Toast.makeText(this, "Lockdown mode is active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLimitEditingLocked(): Boolean {
        val decision = LockPolicy.evaluateEditingLock(readLockState(), System.currentTimeMillis())
        if (decision.shouldClearExpiredLockdown) {
            prefs.edit {
                putBoolean("lockdown_enabled", false)
                remove("lockdown_end_time")
                remove("lockdown_weekly_day")
                remove("lockdown_weekly_hour")
                remove("lockdown_weekly_minute")
                remove("lockdown_one_time_change")
                remove("lockdown_weekly_used_key")
            }
        }
        return decision.isLocked
    }

    private fun markWeeklyLockdownWindowUsedIfNeeded() {
        val decision = LockPolicy.evaluateEditingLock(readLockState(), System.currentTimeMillis())
        decision.consumeWeeklyWindowKey?.let { weekKey ->
            prefs.edit { putString("lockdown_weekly_used_key", weekKey) }
        }
    }

    private fun readLockState(): LockState {
        return LockState(
            hasPassword = !prefs.getString("password", null).isNullOrBlank(),
            hasSecurityKey = prefs.getBoolean("security_key_enabled", false),
            passwordUnlocked = prefs.getBoolean("passUnlocked", false),
            securityKeyUnlocked = prefs.getBoolean("securityKeyUnlocked", false),
            lockdownEnabled = prefs.getBoolean("lockdown_enabled", false),
            lockdownEndTimeMillis = prefs.getLong("lockdown_end_time", 0L),
            lockdownOneTimeWeeklyChange = prefs.getBoolean("lockdown_one_time_change", false),
            lockdownWeeklyDayMondayOne = prefs.getInt("lockdown_weekly_day", -1),
            lockdownWeeklyHour = prefs.getInt("lockdown_weekly_hour", -1),
            lockdownWeeklyMinute = prefs.getInt("lockdown_weekly_minute", -1),
            lockdownWeeklyUsedKey = prefs.getString("lockdown_weekly_used_key", null)
        )
    }

    private fun buildLockModesBlockedMessage(nowMillis: Long = System.currentTimeMillis()): String {
        val lockdownEnd = prefs.getLong("lockdown_end_time", 0L)
        if (lockdownEnd > nowMillis) {
            val formattedEnd = SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault())
                .format(Date(lockdownEnd))
            return "Lockdown mode is active. You can change lock settings after $formattedEnd."
        }

        val weeklyEnabled = prefs.getBoolean("lockdown_one_time_change", false)
        val weeklyDay = prefs.getInt("lockdown_weekly_day", -1)
        val weeklyHour = prefs.getInt("lockdown_weekly_hour", -1)
        val weeklyMinute = prefs.getInt("lockdown_weekly_minute", -1)
        if (weeklyEnabled && weeklyDay in 1..7 && weeklyHour in 0..23 && weeklyMinute in 0..59) {
            val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, weeklyHour)
                set(Calendar.MINUTE, weeklyMinute)
            }
            val timeFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
            return "Lockdown mode is active. Your next change window starts on ${dayNames[weeklyDay - 1]} at $timeFormatted."
        }

        return "Lockdown mode is active. You cannot change lock settings right now."
    }

    private fun shouldKeepServiceForSettingsProtection(): Boolean {
        if (!prefs.getBoolean("blockSettings", false)) return false
        return LockPolicy.hasAnyConfiguredLockMode(readLockState())
    }

    private fun isPremiumEnabledNow(): Boolean {
        return prefs.getBoolean("premium", false)
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("app_tick_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, BackgroundChecker::class.java).also { intent ->
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (mBound) {
            unbindService(serviceConnection)
            mBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            viewModel.updatePremiumStatus(prefs.getBoolean("premium", false))
            if (shouldKeepServiceForSettingsProtection()) {
                BackgroundChecker.startServiceIfNotRunning(applicationContext)
            }
        }
    }

    private fun checkAllPermissions(): Boolean {
        if (!Settings.canDrawOverlays(applicationContext)) {
            overlayPermissionLauncher.launch(Intent(this, OverlayPermissionPage::class.java))
            return false
        }
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            usageStatsPermissionLauncher.launch(Intent(this, UsageStatsPermissionPage::class.java))
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return false
            }
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            notificationPermissionLauncher.launch(Intent(this, NotificationPermissionPage::class.java))
            return false
        }
        return true
    }

    private fun setupBilling() {
        mBillingClient = BillingClient.newBuilder(applicationContext)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        mBillingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    checkPendingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    private fun checkPendingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        mBillingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        handlePurchase(purchase)
                    }
                }
            }
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("premium_mode")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList)

        mBillingClient.queryProductDetailsAsync(params.build()) { _, detailsList ->
            if (detailsList.isNotEmpty()) {
                productDetails.value = detailsList[0]
            }
        }
    }

    private fun launchPurchaseFlow(productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        mBillingClient.launchBillingFlow(this, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase canceled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                mBillingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        viewModel.updatePremiumStatus(true)
                        Toast.makeText(this, "Purchase successful!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Toast.makeText(this, "Purchase is pending. Please complete the transaction.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_EDIT_GROUP_ID = "extra_edit_group_id"
        const val EXTRA_OPEN_LOCK_MODES = "extra_open_lock_modes"
    }
}
