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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.unit.dp
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
import com.juliacai.apptick.data.LegacyLockPrefsMigrator
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
import com.juliacai.apptick.premiumMode.LockdownModeActivity
import com.juliacai.apptick.premiumMode.PremiumModeInfoScreen
import com.juliacai.apptick.premiumMode.PremiumModeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lifecycle-aware navigation helpers that prevent double-tap navigation.
 * A rapid second tap during the navigation animation is ignored because the
 * departing composable's lifecycle is no longer RESUMED.
 */
internal fun NavController.safePop(): Boolean {
    return if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        popBackStack()
    } else false
}

internal fun NavController.safePop(route: String, inclusive: Boolean): Boolean {
    return if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        popBackStack(route, inclusive)
    } else false
}

internal fun NavController.safeNav(route: String, builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        navigate(route, builder)
    }
}

class MainActivity : BaseActivity(), PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient
    private val viewModel: MainViewModel by viewModels()
    private val appLimitViewModel: AppLimitViewModel by viewModels()
    private lateinit var prefs: SharedPreferences
    private val productDetailsState = androidx.compose.runtime.mutableStateOf<ProductDetails?>(null)
    private var appInitialized = false
    private var appVersionCode: Long = -1L
    private var launchEditGroupId: Long? = null
    private var launchDuplicateGroupId: Long? = null
    private var launchOpenLockModes = false
    private var lockEvaluationNow by androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
    private var lockStateUi by androidx.compose.runtime.mutableStateOf(
        LockState(
            activeLockMode = LockMode.NONE,
            passwordUnlocked = false,
            securityKeyUnlocked = false,
            lockdownType = LockdownType.ONE_TIME,
            lockdownEndTimeMillis = 0L,
            lockdownRecurringDays = emptyList(),
            lockdownRecurringUsedKey = null
        )
    )
    private val lockPrefKeys = setOf(
        "active_lock_mode",
        "passUnlocked",
        "securityKeyUnlocked",
        "lockdown_type",
        "lockdown_end_time",
        "lockdown_recurring_days",
        "lockdown_weekly_used_key",
        "lockdown_prompt_after_unlock"
    )
    private val lockPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key in lockPrefKeys) {
            refreshLockUiState()
            lockEvaluationNow = System.currentTimeMillis()
        }
    }

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
        launchDuplicateGroupId =
            intent.getLongExtra(EXTRA_DUPLICATE_GROUP_ID, -1L).takeIf { it != -1L }
        launchOpenLockModes = intent.getBooleanExtra(EXTRA_OPEN_LOCK_MODES, false)

        lifecycleScope.launch(Dispatchers.IO) {
            val appDatabase = AppTickDatabase.getDatabase(applicationContext)
            LegacyDataMigrator(applicationContext, appDatabase.appLimitGroupDao()).migrate()
        }

        createNotificationChannel()

        prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        appVersionCode = readAppVersionCode()

        // On app update, re-show accessibility onboarding if the service isn't enabled
        val lastAccessibilityPromptVersion = prefs.getLong("accessibility_prompt_version", -1L)
        if (lastAccessibilityPromptVersion != appVersionCode &&
            !com.juliacai.apptick.backgroundProcesses.AppTickAccessibilityService
                .isAccessibilityServiceEnabled(this)
        ) {
            prefs.edit { putBoolean("accessibility_onboarding_seen", false) }
        }

        LegacyLockPrefsMigrator.migrate(prefs)
        maybeAutoUnlockExpiredLockdown()
        refreshLockUiState()
        lockEvaluationNow = System.currentTimeMillis()
        prefs.registerOnSharedPreferenceChangeListener(lockPrefsListener)
        batteryOptimizationStatusState.value = BatteryOptimizationHelper.getStatus(applicationContext)
        val hasSeenLoading = prefs.getBoolean("has_seen_launch_loading", false)
        if (!hasSeenLoading) {
            setContent {
                AppTheme {
                    AppLaunchLoadingScreen()
                }
            }
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
            val standardBatteryIssue = !batteryStatus.ignoringBatteryOptimizations || batteryStatus.backgroundRestricted

            var oemBatteryWarningDismissed by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(
                    prefs.getBoolean(PREF_BATTERY_OEM_WARNING_DISMISSED, false)
                )
            }
            var showGroupDetailsHint by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(
                    prefs.getLong(PREF_GROUP_DETAILS_HINT_SEEN_VERSION, -1L) != appVersionCode
                )
            }
            val lockState = lockStateUi
            val nowMillis = lockEvaluationNow
            val lockDecision = LockPolicy.evaluateEditingLock(lockState, nowMillis)
            val isLockModesLocked = lockDecision.isLocked
            val canAddWhileLocked = isLockModesLocked && lockState.activeLockMode == LockMode.LOCKDOWN
            var pendingEditGroupId by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(launchEditGroupId)
            }
            var pendingDuplicateGroupId by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(launchDuplicateGroupId)
            }
            var pendingOpenLockModes by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(launchOpenLockModes)
            }
            var shouldPromptRelock by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }
            var startedFromExistingGroupEdit by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }
            var selectedGroupForActions by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<AppLimitGroup?>(null)
            }
            var savedGroupOrder by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(GroupCardOrderStore.readOrder(prefs))
            }
            var pendingScrollToBottomTargetSize by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf<Int?>(null)
            }
            var hasSeenNonEmptyGroups by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(false)
            }
            val orderedGroups = androidx.compose.runtime.remember(groups, savedGroupOrder) {
                GroupCardOrderStore.applyOrder(groups, savedGroupOrder) { it.id }
            }
            var pendingLaunchChangelog by rememberSaveable {
                androidx.compose.runtime.mutableStateOf(
                    shouldShowChangelogOnLaunch(prefs, appVersionCode)
                )
            }
            var premiumFeatureDialogFor by rememberSaveable {
                androidx.compose.runtime.mutableStateOf<String?>(null)
            }
            var showChangelogDialog by rememberSaveable {
                androidx.compose.runtime.mutableStateOf(false)
            }

            AppTheme {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    while (true) {
                        delay(30_000L)
                        lockEvaluationNow = System.currentTimeMillis()
                    }
                }

                androidx.compose.runtime.LaunchedEffect(groups) {
                    if (groups.isNotEmpty()) {
                        hasSeenNonEmptyGroups = true
                    } else if (!hasSeenNonEmptyGroups) {
                        // Room can briefly emit an empty list before initial data arrives.
                        // Avoid wiping a valid saved order during that transient state.
                        return@LaunchedEffect
                    }

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
                    startedFromExistingGroupEdit = true
                    appLimitViewModel.loadGroupForEditing(groupId)
                    navController.navigate("setTimeLimit")
                    pendingEditGroupId = null
                    launchEditGroupId = null
                }

                androidx.compose.runtime.LaunchedEffect(pendingDuplicateGroupId, premiumEnabled) {
                    val groupId = pendingDuplicateGroupId ?: return@LaunchedEffect
                    if (!premiumEnabled) {
                        premiumFeatureDialogFor = "Duplicate"
                        pendingDuplicateGroupId = null
                        launchDuplicateGroupId = null
                        return@LaunchedEffect
                    }
                    startedFromExistingGroupEdit = false
                    appLimitViewModel.loadGroupForDuplication(groupId)
                    navController.navigate("setTimeLimit")
                    pendingDuplicateGroupId = null
                    launchDuplicateGroupId = null
                }

                androidx.compose.runtime.LaunchedEffect(pendingOpenLockModes, premiumEnabled) {
                    if (!pendingOpenLockModes) return@LaunchedEffect
                    if (isPremiumEnabledNow() && !isLimitEditingLocked()) {
                        navController.navigate("premium")
                    }
                    pendingOpenLockModes = false
                    launchOpenLockModes = false
                }

                val photoResIds = featurePhotoResIds()
                // Show onboarding if missing required permissions OR if user hasn't seen the accessibility step yet
                val needsPermissions = !hasAllPermissions() ||
                    !prefs.getBoolean("accessibility_onboarding_seen", false)
                val shouldShowFeaturePhotos = shouldShowFeaturePhotosOnLaunch(
                    prefs = prefs,
                    versionCode = appVersionCode,
                    photoResIds = photoResIds
                )
                NavHost(
                    navController = navController,
                    startDestination = startupDestinationRoute(
                        shouldShowFeaturePhotos = shouldShowFeaturePhotos,
                        needsPermissions = needsPermissions
                    )
                ) {
                    composable(STARTUP_ROUTE_FEATURE_PHOTOS) {
                        val onFeaturePhotosFinished = {
                            markFeaturePhotosSeen(prefs, appVersionCode)
                            val nextRoute = postFeaturePhotosRoute(
                                needsPermissions = !hasAllPermissions() ||
                                    !prefs.getBoolean("accessibility_onboarding_seen", false)
                            )
                            navController.navigate(nextRoute) {
                                popUpTo(STARTUP_ROUTE_FEATURE_PHOTOS) { inclusive = true }
                            }
                        }

                        FeaturePhotoCarouselScreen(
                            photoResIds = photoResIds,
                            onSkip = onFeaturePhotosFinished,
                            onComplete = onFeaturePhotosFinished
                        )
                    }

                    composable(STARTUP_ROUTE_PERMISSION_ONBOARDING) {
                        PermissionOnboardingScreen(
                            onAllGranted = {
                                prefs.edit {
                                    putBoolean("accessibility_onboarding_seen", true)
                                    putLong("accessibility_prompt_version", appVersionCode)
                                }
                                navController.navigate(STARTUP_ROUTE_MAIN) {
                                    popUpTo(STARTUP_ROUTE_PERMISSION_ONBOARDING) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(STARTUP_ROUTE_MAIN) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            if (pendingLaunchChangelog) {
                                showChangelogDialog = true
                                pendingLaunchChangelog = false
                            }
                        }

                        val persistGroupOrder: (List<Long>) -> Unit = { newOrder ->
                            savedGroupOrder = newOrder
                            GroupCardOrderStore.writeOrder(prefs, newOrder)
                        }
                        val dismissGroupDetailsHint: () -> Unit = {
                            if (showGroupDetailsHint) {
                                showGroupDetailsHint = false
                                prefs.edit {
                                    putLong(PREF_GROUP_DETAILS_HINT_SEEN_VERSION, appVersionCode)
                                }
                            }
                        }

                        MainScreen(
                            appLimitGroupCount = orderedGroups.size,
                            showLockedIcon = isLockModesLocked,
                            showGroupDetailsHint = showGroupDetailsHint && orderedGroups.isNotEmpty(),
                            showBatteryWarning = standardBatteryIssue || (batteryStatus.hasAdditionalOemRestrictions && !oemBatteryWarningDismissed),
                            batteryWarningDismissable = !standardBatteryIssue && batteryStatus.hasAdditionalOemRestrictions && !oemBatteryWarningDismissed,
                            showStandardBatteryWarning = standardBatteryIssue,
                            batteryWarningDetails = buildList {
                                add("Ignore battery optimizations:" to if (batteryStatus.ignoringBatteryOptimizations) "On" else "Off")
                                add("Background restricted:" to if (batteryStatus.backgroundRestricted) "Yes" else "No")
                                if (batteryStatus.hasAdditionalOemRestrictions && !oemBatteryWarningDismissed) {
                                    add("OEM startup controls:" to "Detected")
                                }
                            },
                            oemGuidance = if (batteryStatus.hasAdditionalOemRestrictions && !oemBatteryWarningDismissed) {
                                batteryStatus.oemGuidance ?: "Enable AppTick auto-start in system manager."
                            } else null,
                            hasOemRestrictions = batteryStatus.hasAdditionalOemRestrictions && !oemBatteryWarningDismissed,
                            onFabClick = {
                                if (isLimitEditingLocked() && !canAddWhileLocked) {
                                    launchUnlockFlow()
                                } else {
                                    shouldPromptRelock = false
                                    startedFromExistingGroupEdit = false
                                    appLimitViewModel.clearState()
                                    navController.safeNav("selectApps")
                                }
                            },
                            onSettingsClick = {
                                navController.safeNav("settings") {
                                    launchSingleTop = true
                                }
                            },
                            onPremiumClick = {
                                if (!isPremiumEnabledNow()) {
                                    navController.safeNav("premium") {
                                        launchSingleTop = true
                                    }
                                    return@MainScreen
                                }

                                val currentState = readLockState()
                                val decision = LockPolicy.evaluateEditingLock(
                                    currentState,
                                    System.currentTimeMillis()
                                )
                                if (!decision.isLocked) {
                                    navController.safeNav("premium") {
                                        launchSingleTop = true
                                    }
                                } else if (
                                    currentState.activeLockMode == LockMode.PASSWORD ||
                                    currentState.activeLockMode == LockMode.SECURITY_KEY
                                ) {
                                    launchUnlockFlow(openLockModesAfterUnlock = true)
                                } else if (currentState.activeLockMode == LockMode.LOCKDOWN) {
                                    navController.safeNav("lockModesBlocked")
                                } else {
                                    navController.safeNav("premium") {
                                        launchSingleTop = true
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
                            onOpenDontKillMyApp = {
                                if (!BatteryOptimizationHelper.openDontKillMyApp(this@MainActivity)) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open dontkillmyapp.com",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onOpenOemStartupSettings = {
                                if (!BatteryOptimizationHelper.openManufacturerBackgroundSettings(this@MainActivity)) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Unable to open OEM startup settings",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onRefreshBatteryStatus = {
                                batteryOptimizationStatusState.value =
                                    BatteryOptimizationHelper.getStatus(applicationContext)
                            },
                            onDismissBatteryWarning = {
                                oemBatteryWarningDismissed = true
                                prefs.edit { putBoolean(PREF_BATTERY_OEM_WARNING_DISMISSED, true) }
                            },
                            onDismissGroupDetailsHint = dismissGroupDetailsHint,
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
                                            timeRanges = appLimitGroup.timeRanges,
                                            startHour = appLimitGroup.startHour,
                                            startMinute = appLimitGroup.startMinute,
                                            endHour = appLimitGroup.endHour,
                                            endMinute = appLimitGroup.endMinute,
                                            cumulativeTime = appLimitGroup.cumulativeTime,
                                            timeRemaining = appLimitGroup.timeRemaining,
                                            nextResetTime = appLimitGroup.nextResetTime,
                                            nextAddTime = appLimitGroup.nextAddTime,
                                            perAppUsage = appLimitGroup.perAppUsage,
                                            isExpanded = appLimitGroup.isExpanded
                                        )
                                    },
                                    onCardClick = { group ->
                                        dismissGroupDetailsHint()
                                        startActivity(GroupPage.newIntent(this@MainActivity, group))
                                    },
                                    onEditClick = { group ->
                                        selectedGroupForActions = group
                                    },
                                    onLockClick = { launchUnlockFlow() },
                                    onExpandToggle = { group ->
                                        viewModel.setGroupExpanded(group.id, !group.isExpanded)
                                    },
                                    isEditingLocked = isLockModesLocked,
                                    onPauseToggle = { group ->
                                        viewModel.togglePause(group)
                                        if (shouldShowLockdownRelockPrompt()) {
                                            shouldPromptRelock = true
                                        }
                                    },
                                    onDelete = { group -> viewModel.deleteGroup(group) },
                                    onReorder = { reorderedIds ->
                                        persistGroupOrder(reorderedIds)
                                        dismissGroupDetailsHint()
                                    },
                                    autoScrollTargetSize = pendingScrollToBottomTargetSize,
                                    onAutoScrollHandled = { pendingScrollToBottomTargetSize = null }
                                )
                            }
                        )

                        val currentGroup = selectedGroupForActions
                        if (currentGroup != null) {
                            GroupActionsDialog(
                                onDismiss = { selectedGroupForActions = null },
                                onEdit = {
                                    selectedGroupForActions = null
                                    startedFromExistingGroupEdit = true
                                    appLimitViewModel.startEditingGroup(currentGroup)
                                    navController.safeNav("setTimeLimit")
                                },
                                onDuplicate = {
                                    selectedGroupForActions = null
                                    if (!premiumEnabled) {
                                        premiumFeatureDialogFor = "Duplicate"
                                        return@GroupActionsDialog
                                    }
                                    startedFromExistingGroupEdit = false
                                    appLimitViewModel.startDuplicatingGroup(currentGroup)
                                    navController.safeNav("setTimeLimit")
                                },
                                onDelete = {
                                    selectedGroupForActions = null
                                    viewModel.deleteGroup(currentGroup)
                                }
                            )
                        }

                        premiumFeatureDialogFor?.let { featureName ->
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { premiumFeatureDialogFor = null },
                                title = { androidx.compose.material3.Text("Premium Feature") },
                                text = { androidx.compose.material3.Text("$featureName is available in Premium Mode.") },
                                confirmButton = {
                                    androidx.compose.material3.Button(
                                        onClick = {
                                            premiumFeatureDialogFor = null
                                            navController.safeNav("premium")
                                        }
                                    ) {
                                        androidx.compose.material3.Text("Buy Premium")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { premiumFeatureDialogFor = null }
                                    ) {
                                        androidx.compose.material3.Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (shouldPromptRelock) {
                            val currentState = readLockState()
                            val isRecurringUnlockWindow =
                                currentState.activeLockMode == LockMode.LOCKDOWN &&
                                    currentState.lockdownType == LockdownType.RECURRING &&
                                    !LockPolicy.evaluateEditingLock(
                                        currentState,
                                        System.currentTimeMillis()
                                    ).isLocked
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { shouldPromptRelock = false },
                                title = { androidx.compose.material3.Text("Lockdown this again?") },
                                text = {
                                    if (isRecurringUnlockWindow) {
                                        val unlockDaysText =
                                            formatRecurringUnlockDays(currentState.lockdownRecurringDays)
                                        androidx.compose.material3.Text(
                                            text = androidx.compose.ui.text.buildAnnotatedString {
                                                append("Do you want to lockdown ALL app limits from being changed? ")
                                                append("The current unlock day(s) are ")
                                                pushStyle(
                                                    androidx.compose.ui.text.SpanStyle(
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                    )
                                                )
                                                append(unlockDaysText)
                                                pop()
                                                append(".")
                                            }
                                        )
                                    } else {
                                        androidx.compose.material3.Text(
                                            "Do you want to lockdown this app limit from being changed? " +
                                                "You can choose a specific date or weekday schedule, or turn Lockdown off."
                                        )
                                    }
                                },
                                confirmButton = {
                                    if (isRecurringUnlockWindow) {
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                shouldPromptRelock = false
                                                val decision = LockPolicy.evaluateEditingLock(
                                                    currentState,
                                                    System.currentTimeMillis()
                                                )
                                                if (decision.consumeKey != null) {
                                                    prefs.edit {
                                                        putString("lockdown_weekly_used_key", decision.consumeKey)
                                                        putBoolean("lockdown_prompt_after_unlock", false)
                                                    }
                                                    refreshLockUiState()
                                                    lockEvaluationNow = System.currentTimeMillis()
                                                }
                                            }
                                        ) {
                                            androidx.compose.material3.Text("YES")
                                        }
                                    } else {
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                shouldPromptRelock = false
                                                startActivity(Intent(this@MainActivity, LockdownModeActivity::class.java))
                                            }
                                        ) {
                                            androidx.compose.material3.Text("Yes")
                                        }
                                    }
                                },
                                dismissButton = {
                                    if (isRecurringUnlockWindow) {
                                        androidx.compose.foundation.layout.Row(
                                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                                                8.dp
                                            )
                                        ) {
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = { shouldPromptRelock = false }
                                            ) {
                                                androidx.compose.material3.Text("NO")
                                            }
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    shouldPromptRelock = false
                                                    startActivity(
                                                        Intent(
                                                            this@MainActivity,
                                                            LockdownModeActivity::class.java
                                                        )
                                                    )
                                                }
                                            ) {
                                                androidx.compose.material3.Text("CHANGE")
                                            }
                                        }
                                    } else {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = {
                                                shouldPromptRelock = false
                                                prefs.edit {
                                                    putString("active_lock_mode", "NONE")
                                                    putString("lockdown_type", LockdownType.ONE_TIME.name)
                                                    remove("lockdown_end_time")
                                                    remove("lockdown_recurring_days")
                                                    remove("lockdown_weekly_used_key")
                                                    putBoolean("lockdown_prompt_after_unlock", false)
                                                }
                                                refreshLockUiState()
                                                lockEvaluationNow = System.currentTimeMillis()
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Lockdown mode turned off.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        ) {
                                            androidx.compose.material3.Text("Turn Off Lockdown")
                                        }
                                    }
                                }
                            )
                        }
                    }

                    composable("settings") {
                        SettingsScreen(
                            onBackClick = { navController.safePop("main", inclusive = false) },
                            onCustomizeColors = { navController.safeNav("colorPicker") },
                            onUpgradeToPremium = { navController.safeNav("premium") },
                            onOpenPremiumModeInfo = { navController.safeNav("premiumModeInfo") },
                            onOpenAppLimitBackup = { navController.safeNav("appLimitBackup") },
                            onOpenChangelog = { showChangelogDialog = true },
                            onOpenAccessibilityOnboarding = { navController.safeNav("accessibilityOnboarding") }
                        )
                    }

                    composable("accessibilityOnboarding") {
                        PermissionOnboardingScreen(
                            onAllGranted = { navController.safePop() }
                        )
                    }

                    composable("appLimitBackup") {
                        AppLimitBackupScreen(
                            onBackClick = { navController.safePop() }
                        )
                    }

                    composable("colorPicker") {
                        com.juliacai.apptick.settings.ColorPickerScreen(
                            onBackClick = { navController.safePop() }
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
                                onBackClick = { navController.safePop() },
                                onEditClick = { group ->
                                    startedFromExistingGroupEdit = true
                                    appLimitViewModel.startEditingGroup(group)
                                    navController.safeNav("setTimeLimit")
                                }
                            )
                        }
                    }

                    composable("selectApps") {
                        AppSelectScreen(
                            viewModel = appLimitViewModel,
                            onNextClick = {
                                if (navController.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return@AppSelectScreen
                                if (!navController.popBackStack("setTimeLimit", inclusive = false)) {
                                    navController.navigate("setTimeLimit")
                                }
                            },
                            onCancel = {
                                if (navController.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return@AppSelectScreen
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
                                if (it.id == 0L) {
                                    pendingScrollToBottomTargetSize = orderedGroups.size + 1
                                    shouldPromptRelock = false
                                }
                                appLimitViewModel.saveGroup(it)
                                if (
                                    startedFromExistingGroupEdit &&
                                    it.id != 0L &&
                                    shouldShowLockdownRelockPrompt()
                                ) {
                                    shouldPromptRelock = true
                                }
                                relockCredentialModesIfNeeded()
                                startedFromExistingGroupEdit = false
                                navController.safePop("main", inclusive = false)
                            },
                            onCancel = {
                                startedFromExistingGroupEdit = false
                                navController.safePop("main", inclusive = false)
                            },
                            onEditApps = {
                                if (navController.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return@SetTimeLimitsScreen
                                if (!navController.popBackStack("selectApps", inclusive = false)) {
                                    navController.navigate("selectApps")
                                }
                            },
                            onUpgradeToPremium = { navController.safeNav("premium") }
                        )
                    }

                    composable("premium") {
                        PremiumModeScreen(
                            productDetails = productDetailsState.value,
                            isPremium = premiumEnabled,
                            activeLockMode = lockState.activeLockMode,
                            onPurchaseClick = { product -> launchPurchaseFlow(product) },
                            navController = navController,
                            onBackClick = {
                                relockCredentialModesIfNeeded()
                                navController.safePop()
                            }
                        )
                    }

                    composable("premiumModeInfo") {
                        PremiumModeInfoScreen(
                            onBackClick = { navController.safePop() }
                        )
                    }

                    composable("lockModesBlocked") {
                        LockModesBlockedScreen(
                            message = buildLockModesBlockedMessage(),
                            onBackClick = { navController.safePop() }
                        )
                    }
                }

                if (showChangelogDialog) {
                    ChangelogDialog(
                        onDismiss = {
                            showChangelogDialog = false
                            markChangelogSeen(prefs, appVersionCode)
                        }
                    )
                }
            }
        }
    }

    private fun readAppVersionCode(): Long {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
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
                putBoolean("lockdown_prompt_after_unlock", true)
            }
            refreshLockUiState()
            lockEvaluationNow = System.currentTimeMillis()
        }
        return decision.isLocked
    }

    private fun refreshLockUiState() {
        if (!::prefs.isInitialized) return
        lockStateUi = readLockState()
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
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lockdownEnd))
                return "Lockdown active until $formattedEnd."
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

    private fun shouldShowLockdownRelockPrompt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (prefs.getBoolean("lockdown_prompt_after_unlock", false)) return true
        val state = readLockState()
        if (state.activeLockMode != LockMode.LOCKDOWN) return false
        val decision = LockPolicy.evaluateEditingLock(state, nowMillis)
        return !decision.isLocked
    }

    private fun formatRecurringUnlockDays(days: List<Int>): String {
        if (days.isEmpty()) return "none"
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        return days.sorted().joinToString(", ") { dayNames.getOrElse(it - 1) { "Unknown" } }
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
        relockCredentialModesIfNeeded()
        if (mBound) {
            unbindService(serviceConnection)
            mBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        batteryOptimizationStatusState.value = BatteryOptimizationHelper.getStatus(applicationContext)
        if (::prefs.isInitialized) {
            maybeAutoUnlockExpiredLockdown()
            refreshLockUiState()
            lockEvaluationNow = System.currentTimeMillis()
            viewModel.updatePremiumStatus(prefs.getBoolean("premium", false))
            syncBackgroundServiceState()
        }
    }

    private fun maybeAutoUnlockExpiredLockdown(nowMillis: Long = System.currentTimeMillis()) {
        val state = readLockState()
        if (state.activeLockMode != LockMode.LOCKDOWN || state.lockdownType != LockdownType.ONE_TIME) return
        val decision = LockPolicy.evaluateEditingLock(state, nowMillis)
        if (!decision.shouldClearExpiredLockdown) return
        prefs.edit {
            putString("active_lock_mode", "NONE")
            remove("lockdown_end_time")
            remove("lockdown_weekly_used_key")
            putBoolean("lockdown_prompt_after_unlock", true)
        }
    }

    private fun relockCredentialModesIfNeeded() {
        if (!::prefs.isInitialized) return
        val state = readLockState()
        if (!LockPolicy.shouldAutoRelockOnExit(state)) return
        prefs.edit {
            putBoolean("passUnlocked", false)
            putBoolean("securityKeyUnlocked", false)
        }
    }

    override fun onDestroy() {
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(lockPrefsListener)
        }
        super.onDestroy()
    }

    private fun syncBackgroundServiceState() {
        lifecycleScope.launch(Dispatchers.IO) {
            val activeGroupCount =
                AppTickDatabase.getDatabase(applicationContext).appLimitGroupDao().getActiveGroupCountSync()
            val shouldRun = activeGroupCount > 0
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
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase, showSuccessToast = false)
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

    private fun handlePurchase(purchase: Purchase, showSuccessToast: Boolean = true) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        grantPremiumEntitlement()
                        if (showSuccessToast) {
                            Toast.makeText(this, "Purchase successful!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                grantPremiumEntitlement()
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Toast.makeText(
                this,
                "Purchase is pending. Please complete the transaction.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun grantPremiumEntitlement() {
        viewModel.updatePremiumStatus(true)
        // Apply premium defaults once so users can still turn the bubble off later.
        if (!prefs.getBoolean(PREF_PREMIUM_DEFAULTS_APPLIED, false)) {
            prefs.edit {
                putBoolean("floatingBubbleEnabled", true)
                putBoolean("bubbleDismissed", false)
                putBoolean(PREF_PREMIUM_DEFAULTS_APPLIED, true)
            }
        }
    }

    companion object {
        const val EXTRA_EDIT_GROUP_ID = "extra_edit_group_id"
        const val EXTRA_DUPLICATE_GROUP_ID = "extra_duplicate_group_id"
        const val EXTRA_OPEN_LOCK_MODES = "extra_open_lock_modes"
        private const val PREF_PREMIUM_DEFAULTS_APPLIED = "premiumDefaultsApplied"
        private const val PREF_BATTERY_OEM_WARNING_DISMISSED = "batteryOemWarningDismissed"
        private const val PREF_GROUP_DETAILS_HINT_SEEN_VERSION = "groupDetailsHintSeenVersion"
    }
}
