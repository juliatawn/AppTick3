package com.juliacai.apptick

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.setValue
import androidx.core.content.edit
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
import com.juliacai.apptick.data.GroupCardOrderStore
import com.juliacai.apptick.data.LegacyDataMigrator
import com.juliacai.apptick.deviceApps.GroupActionsDialog
import com.juliacai.apptick.deviceApps.GroupPage
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppLimitGroupsList
import com.juliacai.apptick.lockModes.EnterPasswordActivity
import com.juliacai.apptick.lockModes.EnterSecurityKeyActivity
import com.juliacai.apptick.lockModes.SecurityKeySettingsScreen
import com.juliacai.apptick.newAppLimit.AppLimitViewModel
import com.juliacai.apptick.newAppLimit.AppSelectScreen
import com.juliacai.apptick.newAppLimit.SetTimeLimitsScreen
import com.juliacai.apptick.permissions.BatteryOptimizationHelper
import com.juliacai.apptick.permissions.BatteryOptimizationStatus
import com.juliacai.apptick.permissions.PermissionOnboardingScreen
import com.juliacai.apptick.premiumMode.LockModesBlockedScreen
import com.juliacai.apptick.premiumMode.PremiumModeInfoScreen
import com.juliacai.apptick.premiumMode.PremiumModeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity(), PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient
    private val viewModel: MainViewModel by viewModels()
    private val appLimitViewModel: AppLimitViewModel by viewModels()
    private lateinit var prefs: SharedPreferences
    private val productDetailsState = androidx.compose.runtime.mutableStateOf<ProductDetails?>(null)
    private var appInitialized = false
    private var launchEditGroupId: Long? = null
    private var launchOpenLockModes = false

    private var mService: BackgroundChecker? = null
    private var mBound = false
    private val batteryOptimizationStatusState =
        androidx.compose.runtime.mutableStateOf<BatteryOptimizationStatus?>(null)

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
        ThemeModeManager.apply(this)
        super.onCreate(savedInstanceState)

        launchEditGroupId = intent.getLongExtra(EXTRA_EDIT_GROUP_ID, -1L).takeIf { it != -1L }
        launchOpenLockModes = intent.getBooleanExtra(EXTRA_OPEN_LOCK_MODES, false)

        lifecycleScope.launch(Dispatchers.IO) {
            val appDatabase = AppTickDatabase.getDatabase(applicationContext)
            LegacyDataMigrator(applicationContext, appDatabase.appLimitGroupDao()).migrate()
        }

        createNotificationChannel()

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        batteryOptimizationStatusState.value = BatteryOptimizationHelper.getStatus(applicationContext)
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
        initApp()
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
            val batteryStatus =
                batteryOptimizationStatusState.value ?: BatteryOptimizationHelper.getStatus(applicationContext)
            val lockState = readLockState()
            val lockDecision = LockPolicy.evaluateEditingLock(lockState, System.currentTimeMillis())
            val isLockModesLocked = lockDecision.isLocked
            var pendingEditGroupId by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(launchEditGroupId)
            }
            var pendingOpenLockModes by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(launchOpenLockModes)
            }
            var selectedGroupForActions by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<AppLimitGroup?>(null)
            }
            var savedGroupOrder by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(GroupCardOrderStore.readOrder(prefs))
            }
            val orderedGroups = androidx.compose.runtime.remember(groups, savedGroupOrder) {
                GroupCardOrderStore.applyOrder(groups, savedGroupOrder) { it.id }
            }

            AppTheme {
                androidx.compose.runtime.LaunchedEffect(groups) {
                    val sanitizedOrder = GroupCardOrderStore.sanitizeOrder(
                        savedGroupOrder,
                        groups.map { it.id }
                    )
                    if (sanitizedOrder != savedGroupOrder) {
                        savedGroupOrder = sanitizedOrder
                        GroupCardOrderStore.writeOrder(prefs, sanitizedOrder)
                    }
                }

                androidx.compose.runtime.LaunchedEffect(pendingEditGroupId) {
                    val groupId = pendingEditGroupId ?: return@LaunchedEffect
                    appLimitViewModel.loadGroupForEditing(groupId)
                    navController.navigate("setTimeLimit")
                    pendingEditGroupId = null
                    launchEditGroupId = null
                }

                androidx.compose.runtime.LaunchedEffect(pendingOpenLockModes, premiumEnabled) {
                    if (!pendingOpenLockModes) return@LaunchedEffect
                    if (isPremiumEnabledNow() && !isLimitEditingLocked()) {
                        navController.navigate("premium")
                    }
                    pendingOpenLockModes = false
                    launchOpenLockModes = false
                }

                val needsPermissions = !hasAllPermissions()
                NavHost(
                    navController = navController,
                    startDestination = if (needsPermissions) "permissionOnboarding" else "main"
                ) {
                    composable("permissionOnboarding") {
                        PermissionOnboardingScreen(
                            onAllGranted = {
                                navController.navigate("main") {
                                    popUpTo("permissionOnboarding") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("main") {
                        val persistGroupOrder: (List<Long>) -> Unit = { newOrder ->
                            savedGroupOrder = newOrder
                            GroupCardOrderStore.writeOrder(prefs, newOrder)
                        }

                        MainScreen(
                            appLimitGroupCount = orderedGroups.size,
                            showLockedIcon = isLockModesLocked,
                            showBatteryWarning = !batteryStatus.unrestricted,
                            batteryWarningText = buildString {
                                append("AppTick may not block reliably until battery mode is set to Unrestricted.")
                                append(" Ignore battery optimizations: ")
                                append(if (batteryStatus.ignoringBatteryOptimizations) "On" else "Off")
                                append(". Background restricted: ")
                                append(if (batteryStatus.backgroundRestricted) "Yes" else "No")
                                append(".")
                            },
                            onFabClick = {
                                if (isLimitEditingLocked()) {
                                    launchUnlockFlow()
                                } else {
                                    markWeeklyLockdownWindowUsedIfNeeded()
                                    appLimitViewModel.clearState()
                                    navController.navigate("selectApps")
                                }
                            },
                            onSettingsClick = {
                                if (navController.currentDestination?.route != "settings") {
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onPremiumClick = {
                                if (!isPremiumEnabledNow()) {
                                    if (navController.currentDestination?.route != "premium") {
                                        navController.navigate("premium") {
                                            launchSingleTop = true
                                        }
                                    }
                                    return@MainScreen
                                }

                                val currentState = readLockState()
                                val decision = LockPolicy.evaluateEditingLock(
                                    currentState,
                                    System.currentTimeMillis()
                                )
                                if (!decision.isLocked) {
                                    if (navController.currentDestination?.route != "premium") {
                                        navController.navigate("premium") {
                                            launchSingleTop = true
                                        }
                                    }
                                } else if (
                                    currentState.activeLockMode == LockMode.PASSWORD ||
                                    currentState.activeLockMode == LockMode.SECURITY_KEY
                                ) {
                                    launchUnlockFlow(openLockModesAfterUnlock = true)
                                } else if (currentState.activeLockMode == LockMode.LOCKDOWN) {
                                    navController.navigate("lockModesBlocked")
                                } else {
                                    if (navController.currentDestination?.route != "premium") {
                                        navController.navigate("premium") {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            },
                            onOpenAppBatterySettings = {
                                if (!BatteryOptimizationHelper.openAppBatterySettings(this@MainActivity)) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open battery settings",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onOpenGeneralBatterySettings = {
                                if (!BatteryOptimizationHelper.openGeneralBatterySettings(this@MainActivity)) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open battery settings",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onRefreshBatteryStatus = {
                                batteryOptimizationStatusState.value =
                                    BatteryOptimizationHelper.getStatus(applicationContext)
                            },
                            listContent = {
                                AppLimitGroupsList(
                                    groups = orderedGroups.map { appLimitGroup ->
                                        AppLimitGroup(
                                            id = appLimitGroup.id,
                                            name = appLimitGroup.name,
                                            timeHrLimit = appLimitGroup.timeHrLimit,
                                            timeMinLimit = appLimitGroup.timeMinLimit,
                                            limitEach = appLimitGroup.limitEach,
                                            resetMinutes = appLimitGroup.resetMinutes,
                                            weekDays = appLimitGroup.weekDays,
                                            apps = appLimitGroup.apps,
                                            paused = appLimitGroup.paused,
                                            useTimeRange = appLimitGroup.useTimeRange,
                                            blockOutsideTimeRange = appLimitGroup.blockOutsideTimeRange,
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
                                    onCardClick = { group ->
                                        startActivity(GroupPage.newIntent(this@MainActivity, group))
                                    },
                                    onEditClick = { group ->
                                        selectedGroupForActions = group
                                    },
                                    onLockClick = { launchUnlockFlow() },
                                    isEditingLocked = isLockModesLocked,
                                    onPauseToggle = { group -> viewModel.togglePause(group) },
                                    onDelete = { group -> viewModel.deleteGroup(group) },
                                    onReorder = { reorderedIds ->
                                        persistGroupOrder(reorderedIds)
                                    }
                                )
                            }
                        )

                        val currentGroup = selectedGroupForActions
                        if (currentGroup != null) {
                            GroupActionsDialog(
                                onDismiss = { selectedGroupForActions = null },
                                onEdit = {
                                    selectedGroupForActions = null
                                    appLimitViewModel.startEditingGroup(currentGroup)
                                    navController.navigate("setTimeLimit")
                                },
                                onDelete = {
                                    selectedGroupForActions = null
                                    viewModel.deleteGroup(currentGroup)
                                }
                            )
                        }
                    }

                    composable("settings") {
                        SettingsScreen(
                            onBackClick = { navController.popBackStack() },
                            onCustomizeColors = { navController.navigate("colorPicker") },
                            onUpgradeToPremium = { navController.navigate("premium") },
                            onOpenPremiumModeInfo = { navController.navigate("premiumModeInfo") },
                            onOpenAppLimitBackup = { navController.navigate("appLimitBackup") }
                        )
                    }

                    composable("appLimitBackup") {
                        AppLimitBackupScreen(
                            onBackClick = { navController.popBackStack() }
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
                            onCancel = {
                                navController.popBackStack(route = "main", inclusive = false)
                            },
                            onEditApps = {
                                if (!navController.popBackStack("selectApps", inclusive = false)) {
                                    navController.navigate("selectApps")
                                }
                            },
                            onUpgradeToPremium = { navController.navigate("premium") }
                        )
                    }

                    composable("premium") {
                        PremiumModeScreen(
                            productDetails = productDetailsState.value,
                            isPremium = premiumEnabled,
                            activeLockMode = lockState.activeLockMode,
                            onPurchaseClick = { product -> launchPurchaseFlow(product) },
                            navController = navController,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable("premiumModeInfo") {
                        PremiumModeInfoScreen(
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
        val state = readLockState()
        when (state.activeLockMode) {
            LockMode.SECURITY_KEY -> {
                startActivity(
                    Intent(this, EnterSecurityKeyActivity::class.java).apply {
                        putExtra(EXTRA_OPEN_LOCK_MODES, openLockModesAfterUnlock)
                    }
                )
            }

            LockMode.PASSWORD -> {
                startActivity(
                    Intent(this, EnterPasswordActivity::class.java).apply {
                        putExtra(EXTRA_OPEN_LOCK_MODES, openLockModesAfterUnlock)
                    }
                )
            }

            LockMode.LOCKDOWN -> {
                Toast.makeText(this, buildLockModesBlockedMessage(), Toast.LENGTH_LONG).show()
            }

            LockMode.NONE -> {
                if (!prefs.getString("password", null).isNullOrBlank()) {
                    startActivity(
                        Intent(this, EnterPasswordActivity::class.java).apply {
                            putExtra(EXTRA_OPEN_LOCK_MODES, openLockModesAfterUnlock)
                        }
                    )
                } else if (prefs.getBoolean("security_key_enabled", false)) {
                    startActivity(
                        Intent(this, EnterSecurityKeyActivity::class.java).apply {
                            putExtra(EXTRA_OPEN_LOCK_MODES, openLockModesAfterUnlock)
                        }
                    )
                }
            }
        }
    }

    private fun isLimitEditingLocked(): Boolean {
        val decision = LockPolicy.evaluateEditingLock(readLockState(), System.currentTimeMillis())
        if (decision.shouldClearExpiredLockdown) {
            prefs.edit {
                putString("active_lock_mode", "NONE")
                remove("lockdown_end_time")
                remove("lockdown_weekly_used_key")
            }
        }
        return decision.isLocked
    }

    private fun markWeeklyLockdownWindowUsedIfNeeded() {
        val decision = LockPolicy.evaluateEditingLock(readLockState(), System.currentTimeMillis())
        decision.consumeKey?.let { key ->
            prefs.edit { putString("lockdown_weekly_used_key", key) }
        }
    }

    private fun readLockState(): LockState {
        val activeModeStr = prefs.getString("active_lock_mode", "NONE") ?: "NONE"
        val activeMode = try {
            LockMode.valueOf(activeModeStr)
        } catch (_: Exception) {
            LockMode.NONE
        }

        val typeStr = prefs.getString("lockdown_type", "ONE_TIME") ?: "ONE_TIME"
        val lockdownType = try {
            LockdownType.valueOf(typeStr)
        } catch (_: Exception) {
            LockdownType.ONE_TIME
        }

        val recurringDays = prefs.getString("lockdown_recurring_days", "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 1..7 }
            .distinct()
            .sorted()

        return LockState(
            activeLockMode = activeMode,
            passwordUnlocked = prefs.getBoolean("passUnlocked", false),
            securityKeyUnlocked = prefs.getBoolean("securityKeyUnlocked", false),
            lockdownType = lockdownType,
            lockdownEndTimeMillis = prefs.getLong("lockdown_end_time", 0L),
            lockdownRecurringDays = recurringDays,
            lockdownRecurringUsedKey = prefs.getString("lockdown_weekly_used_key", null)
        )
    }

    private fun buildLockModesBlockedMessage(nowMillis: Long = System.currentTimeMillis()): String {
        val state = readLockState()
        if (state.activeLockMode != LockMode.LOCKDOWN) {
            return "Time-limit editing is locked right now."
        }

        if (state.lockdownType == LockdownType.ONE_TIME) {
            val lockdownEnd = state.lockdownEndTimeMillis
            if (lockdownEnd > nowMillis) {
                val formattedEnd =
                    SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(Date(lockdownEnd))
                return "Lockdown mode is active. You can change limits after $formattedEnd."
            }
            return "Lockdown mode just expired. Reopen the page to update settings."
        }

        val days = state.lockdownRecurringDays
        if (days.isEmpty()) {
            return "Lockdown mode is active. No recurring edit days are configured."
        }

        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val readableDays = days.joinToString(", ") { dayNames[it - 1] }
        return "Lockdown mode is active. Editing is allowed on: $readableDays."
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
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
        batteryOptimizationStatusState.value = BatteryOptimizationHelper.getStatus(applicationContext)
        if (::prefs.isInitialized) {
            viewModel.updatePremiumStatus(prefs.getBoolean("premium", false))
            syncBackgroundServiceState()
        }
    }

    private fun syncBackgroundServiceState() {
        lifecycleScope.launch(Dispatchers.IO) {
            val activeGroupCount =
                AppTickDatabase.getDatabase(applicationContext).appLimitGroupDao().getActiveGroupCountSync()
            val shouldRun = activeGroupCount > 0 || shouldKeepServiceForSettingsProtection()
            BackgroundChecker.applyDesiredServiceState(applicationContext, shouldRun)
        }
    }

    private fun hasAllPermissions(): Boolean {
        val isDebuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable && isRunningUnderInstrumentation() && prefs.getBoolean("test_skip_permissions", false)) {
            return true
        }
        if (!Settings.canDrawOverlays(applicationContext)) return false

        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) return false

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) return false

        return true
    }

    private fun isRunningUnderInstrumentation(): Boolean {
        return try {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private fun setupBilling() {
        billingClient = BillingClient.newBuilder(applicationContext)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    checkPendingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // No-op: BillingClient reconnects on next request.
            }
        })
    }

    private fun checkPendingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
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

        billingClient.queryProductDetailsAsync(params.build()) { _, detailsList ->
            if (detailsList.isNotEmpty()) {
                productDetailsState.value = detailsList[0]
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

        billingClient.launchBillingFlow(this, billingFlowParams)
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

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        viewModel.updatePremiumStatus(true)
                        Toast.makeText(this, "Purchase successful!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                viewModel.updatePremiumStatus(true)
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Toast.makeText(
                this,
                "Purchase is pending. Please complete the transaction.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        const val EXTRA_EDIT_GROUP_ID = "extra_edit_group_id"
        const val EXTRA_OPEN_LOCK_MODES = "extra_open_lock_modes"
    }
}
