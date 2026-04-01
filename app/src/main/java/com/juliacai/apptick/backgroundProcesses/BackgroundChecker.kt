package com.juliacai.apptick.backgroundProcesses

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.usage.UsageStats
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.PremiumStore
import com.juliacai.apptick.Receiver
import com.juliacai.apptick.R
import com.juliacai.apptick.appLimit.AppLimitEvaluator
import com.juliacai.apptick.block.BlockWindowActivity
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.AppLimitGroupEntity
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.TimeManager
import com.juliacai.apptick.groups.AppUsageStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BackgroundChecker : Service() {

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var blockIntent: Intent

    private val db by lazy { AppTickDatabase.getDatabase(this) }
    private val appLimitGroupDao by lazy { db.appLimitGroupDao() }

    // Cached groups from Flow — updated automatically when DB changes
    @Volatile
    private var cachedGroups: List<AppLimitGroupEntity> = emptyList()

    private lateinit var mBuilder: NotificationCompat.Builder
    private var notifId: Int = 1
    private var mReceiver: BroadcastReceiver? = null
    private lateinit var mNotificationManager: NotificationManager

    private var lastForegroundApp: String? = null
    private var prevBubbleForegroundApp: String? = null
    private val activeBubbleApps = mutableSetOf<String>()
    private var lastCheckElapsed: Long = 0L
    private var isScreenOn = true
    private var bubbleShowReceiver: BroadcastReceiver? = null
    @Volatile
    private var fixedElapsedForTestingMs: Long? = null
    @VisibleForTesting
    var navigateHomeCallCount = 0
        private set
    private var lastBlockedForPackage: String? = null
    private var lastNotificationText: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundChecker = this@BackgroundChecker
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        // Initialize UsageStatsManager once
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        isScreenOn = powerManager.isInteractive

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val field = ServiceInfo::class.java.getField("FOREGROUND_SERVICE_TYPE_SPECIAL_USE")
                notifId = field.getInt(null)
            } catch (e: Exception) {
                println("WARNING - unable to set notifId to FOREGROUND_SERVICE_TYPE_SPECIAL_CASE: $e")
            }
        }

        createNotification()

        // Source - https://stackoverflow.com/a/77530440
        // Posted by Ondřej Skalický, modified by community. See post 'Timeline' for change history
        // Retrieved 2026-02-07, License - CC BY-SA 4.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(notifId, mBuilder.build())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notifId, mBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }

        blockIntent = Intent(this, BlockWindowActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        mReceiver = ScreenStateReceiver()
        registerReceiver(mReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        // Receiver for "Show Bubble" notification action
        bubbleShowReceiver = BubbleShowReceiver()
        registerReceiver(
            bubbleShowReceiver,
            IntentFilter(ACTION_SHOW_BUBBLE),
            Context.RECEIVER_NOT_EXPORTED
        )
        isRunning = true
        if (!disableBackgroundLoopForTesting) {
            observeGroups()
            startUnifiedLoop()
        }
    }

    // ── Flow-based group caching ──────────────────────────────────────────────

    private fun observeGroups() {
        serviceScope.launch {
            appLimitGroupDao.getAllAppLimitGroupsFlow().collect { groups ->
                cachedGroups = groups
            }
        }
    }

    // ── Single unified loop (replaces two separate loops) ─────────────────────

    private fun startUnifiedLoop() {
        serviceScope.launch {
            Log.i("BG_Service", "Unified background loop started")
            lastCheckElapsed = SystemClock.elapsedRealtime()

            while (isActive) {
                var loopDelayMs = CHECK_INTERVAL
                val shouldTrackUsage = shouldTrackUsageNow()
                isScreenOn = shouldTrackUsage

                if (!shouldTrackUsage) {
                    // Keep elapsed baseline fresh so lock/off time is never charged on resume.
                    lastCheckElapsed = SystemClock.elapsedRealtime()
                    try { startService(FloatingBubbleService.hideIntent(this@BackgroundChecker)) } catch (_: Exception) {}
                } else {
                    val foregroundApp = getForegroundApp()
                    if (foregroundApp != null) {
                        lastForegroundApp = foregroundApp
                    }
                    val appToCheck = foregroundApp ?: lastForegroundApp
                    loopDelayMs = currentCheckIntervalMs(appToCheck)

                    // In split-screen, charge time to ALL visible apps simultaneously.
                    val visibleApps = AppTickAccessibilityService.getVisiblePackages()
                    val isSplitScreen = visibleApps.size > 1

                    if (isSplitScreen) {
                        Log.d("BG_Service", "Split-screen detected: $visibleApps (focused=$appToCheck)")
                    }

                    // Compute elapsed once so each app gets the same delta
                    val elapsed = computeElapsedDelta()

                    var anyBlocked = false
                    if (isSplitScreen) {
                        // Process ALL visible apps so every timer gets charged,
                        // even when one app triggers the block screen.
                        for (app in visibleApps) {
                            try {
                                val blocked = checkAppLimits(app, elapsed)
                                if (blocked) anyBlocked = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        try {
                            anyBlocked = checkAppLimits(appToCheck, elapsed)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Skip notification and bubble updates when a block screen was launched
                    // to avoid WindowManager errors from state transitions.
                    if (!anyBlocked) {
                        try {
                            updateNotification(appToCheck)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        try {
                            val bubbleFallbackApp = if (
                                foregroundApp == null &&
                                !lastForegroundApp.isNullOrBlank()
                            ) {
                                lastForegroundApp
                            } else {
                                null
                            }
                            updateFloatingBubble(foregroundApp, bubbleFallbackApp, visibleApps)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                // Wait for the normal interval OR wake up early when AccessibilityService
                // detects a new foreground app, so blocking happens instantly.
                withTimeoutOrNull(loopDelayMs) { wakeUpChannel.receive() }
            }
        }
    }

    // ── Launch / dismiss block screen ───────────────────────────────────────────

    /**
     * Launches the block screen and attempts to close any floating window.
     *
     * tryCloseFloatingWindow is called every iteration — not just on the first
     * block — because it self-limits via a live floating-check: once the window
     * is actually closed it returns NOT_FLOATING immediately with no side effects.
     * This ensures retries if a previous close attempt didn't fully succeed.
     */
    private fun launchBlockScreen(blockedPackage: String?) {
        navigateHomeCallCount++ // keep counter for test compatibility

        // Hide the floating time-remaining bubble
        try {
            startService(FloatingBubbleService.hideIntent(this))
        } catch (_: Exception) {}

        // Try to close floating windows. The live checkIfWindowIsFloating()
        // inside closeFloatingWindow() returns NOT_FLOATING immediately for
        // fullscreen apps and for already-closed windows — no side effects.
        if (blockedPackage != null && AppTickAccessibilityService.isRunning) {
            AppTickAccessibilityService.tryCloseFloatingWindow(blockedPackage)
        }

        // Split-screen bypass: if the block screen is already showing but its
        // window is less than fullscreen (split-screen), dismiss it so
        // split-screen collapses. The blocked app goes fullscreen, and the
        // next loop iteration (triggered via requestImmediateCheck from the
        // accessibility event) relaunches the block screen fullscreen on top.
        if (BlockWindowActivity.isActive &&
            AppTickAccessibilityService.isRunning &&
            AppTickAccessibilityService.isBlockScreenInSplitScreen()) {
            sendBroadcast(Intent(BlockWindowActivity.ACTION_DISMISS_BLOCK).apply {
                setPackage(packageName)
            })
            lastBlockedForPackage = blockedPackage
            return
        }

        lastBlockedForPackage = blockedPackage

        startActivity(blockIntent)
    }

    /**
     * Dismisses the block screen (if showing) by broadcasting ACTION_DISMISS_BLOCK.
     * Called only from onDestroy when the service is shutting down.
     */
    private fun dismissBlockScreen() {
        if (lastBlockedForPackage != null) {
            lastBlockedForPackage = null
            sendBroadcast(
                Intent(BlockWindowActivity.ACTION_DISMISS_BLOCK).setPackage(packageName)
            )
        }
    }

    // ── Core limit checking ───────────────────────────────────────────────────

    /**
     * Returns true if the app was blocked (block screen was launched).
     * Callers in split-screen should stop processing further apps after a block.
     */
    suspend fun checkAppLimits(foregroundApp: String?, elapsedOverride: Long? = null): Boolean {
        // Never block AppTick itself — the user must always be able to manage their limits
        if (foregroundApp == packageName) return false

        val allGroups = if (fixedElapsedForTestingMs != null) {
            // Deterministic test mode: read latest DB snapshot every check.
            appLimitGroupDao.getAllAppLimitGroupsImmediate()
        } else if (cachedGroups.isNotEmpty()) {
            cachedGroups
        } else {
            // Fallback for early direct calls (e.g., tests) before Flow cache emits.
            appLimitGroupDao.getAllAppLimitGroupsImmediate()
        }
        val activeGroups = allGroups.filterNot { it.paused }

        if (activeGroups.isEmpty()) {
            stopSelf()
            return false
        }

        // Measure real elapsed time since last check.
        // elapsedOverride is used in split-screen mode to share a single delta across multiple calls.
        val elapsed = elapsedOverride ?: fixedElapsedForTestingMs ?: run {
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastCheckElapsed).coerceIn(0L, MAX_ELAPSED)
            lastCheckElapsed = now
            delta
        }

        var didBlock = false
        for (entity in activeGroups) {
            val group = entity.toDomainModel()
            val now = System.currentTimeMillis()
            val appInGroup = group.apps.firstOrNull { it.appPackage == foregroundApp }

            // ── Daily / periodic reset check ──────────────────────────────────
            if (group.nextResetTime in 1..now) {
                val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                val fullLimitMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
                val isPeriodicCumulative = group.cumulativeTime && group.resetMinutes > 0

                // Zero out all per-app usage
                val clearedUsage = group.perAppUsage.map { it.copy(usedMillis = 0L) }

                val newNextReset: Long
                val newTimeRemaining: Long

                if (isPeriodicCumulative) {
                    val intervalMs = TimeUnit.MINUTES.toMillis(group.resetMinutes.toLong())
                    val startOfToday = Calendar.getInstance().apply {
                        timeInMillis = now
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    // Advance nextResetTime along the interval grid to the next future point.
                    var nextReset = group.nextResetTime
                    while (nextReset <= now) nextReset += intervalMs
                    newNextReset = nextReset

                    // Did midnight cross since the last interval that actually ran?
                    val prevResetBeforeOverdue = group.nextResetTime - intervalMs
                    val midnightCrossed = prevResetBeforeOverdue < startOfToday

                    if (midnightCrossed) {
                        // Discard yesterday's carryover; count only today's resets.
                        var firstTodayReset = group.nextResetTime
                        while (firstTodayReset < startOfToday) firstTodayReset += intervalMs
                        val todayResetCount = if (firstTodayReset <= now) {
                            ((now - firstTodayReset) / intervalMs) + 1
                        } else {
                            // No today reset has passed yet, but the overdue reset
                            // crosses midnight — grant a fresh base limit.
                            1L
                        }
                        newTimeRemaining = todayResetCount * fullLimitMillis
                    } else {
                        // All missed resets are within the same calendar day — carry over.
                        val missedCount = ((now - group.nextResetTime) / intervalMs) + 1
                        newTimeRemaining = (group.timeRemaining.coerceAtLeast(0L) +
                            missedCount * fullLimitMillis).coerceAtLeast(0L)
                    }
                } else {
                    // Non-cumulative: reset to the full limit.
                    newNextReset = if (group.resetMinutes > 0) {
                        now + TimeUnit.MINUTES.toMillis(group.resetMinutes.toLong())
                    } else {
                        TimeManager.nextMidnight(now)
                    }
                    newTimeRemaining = fullLimitMillis
                }

                appLimitGroupDao.updateResetState(
                    groupId = entity.id,
                    timeRemaining = newTimeRemaining,
                    perAppUsage = clearedUsage,
                    nextResetTime = newNextReset,
                    nextAddTime = if (isPeriodicCumulative) newNextReset else 0L
                )
                // Skip further processing this tick — fresh data will be seen next loop
                continue
            }

            // If today is not active for this group, skip all enforcement and accounting.
            if (!AppLimitEvaluator.isWithinActiveDays(group, now)) continue

            // Optional strict mode: block listed apps completely outside the configured time range.
            if (
                appInGroup != null &&
                AppLimitEvaluator.shouldBlockOutsideTimeRange(group, now)
            ) {
                val usageMap = group.perAppUsage.associate { it.appPackage to it.usedMillis }
                blockIntent.putExtra("app_name", appInGroup.appName)
                blockIntent.putExtra("app_package", appInGroup.appPackage)
                blockIntent.putExtra("group_name", group.name)
                blockIntent.putExtra("app_time_spent", usageMap[appInGroup.appPackage] ?: 0L)
                blockIntent.putExtra("group_time_spent", usageMap.values.sum())
                blockIntent.putExtra("time_limit_minutes", group.timeHrLimit * 60 + group.timeMinLimit)
                blockIntent.putExtra("limit_each", group.limitEach)
                blockIntent.putExtra("use_time_range", group.useTimeRange)
                blockIntent.putExtra("block_outside_time_range", group.blockOutsideTimeRange)
                blockIntent.putExtra("blocked_for_outside_range", true)
                blockIntent.putExtra("next_reset_time", TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = true))
                blockIntent.putExtra("block_reason", "Outside configured time range")
                launchBlockScreen(appInGroup.appPackage)
                didBlock = true
                continue
            }

            // Check if the limit is active for today/now
            if (!AppLimitEvaluator.shouldCheckLimit(group, now)) continue

            val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
            // Defense-in-depth: skip timer decrement when screen is off.
            // The main loop already gates on shouldTrackUsageNow(), but this
            // prevents accidental charging if checkAppLimits is ever called directly.
            if (appInGroup != null && isScreenOn) {
                if (limitInMinutes <= 0) {
                    val usageMap = group.perAppUsage.associate { it.appPackage to it.usedMillis }
                    blockIntent.putExtra("app_name", appInGroup.appName)
                    blockIntent.putExtra("app_package", appInGroup.appPackage)
                    blockIntent.putExtra("group_name", group.name)
                    blockIntent.putExtra("app_time_spent", usageMap[appInGroup.appPackage] ?: 0L)
                    blockIntent.putExtra("group_time_spent", usageMap.values.sum())
                    blockIntent.putExtra("time_limit_minutes", 0)
                    blockIntent.putExtra("limit_each", group.limitEach)
                    blockIntent.putExtra("use_time_range", group.useTimeRange)
                    blockIntent.putExtra("block_outside_time_range", group.blockOutsideTimeRange)
                    blockIntent.putExtra("blocked_for_outside_range", false)
                    blockIntent.putExtra("next_reset_time", TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false))
                    blockIntent.putExtra("block_reason", "Used up time limit")
                    launchBlockScreen(appInGroup.appPackage)
                    didBlock = true
                    continue
                }

                val limitInMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
                val usageMap = group.perAppUsage.associate { it.appPackage to it.usedMillis }.toMutableMap()
                val currentAppUsage = usageMap[appInGroup.appPackage] ?: 0L

                // Project usage/remaining with this tick's elapsed time so blocking is immediate
                // at expiry, not delayed until the following check.
                val newAppUsage = (currentAppUsage + elapsed).coerceAtMost(limitInMillis)
                val newTimeRemaining = if (group.limitEach) {
                    (limitInMillis - newAppUsage).coerceAtLeast(0L)
                } else {
                    (group.timeRemaining - elapsed).coerceAtLeast(0L)
                }
                val isReached = if (group.limitEach) {
                    newAppUsage >= limitInMillis
                } else {
                    newTimeRemaining <= 0L
                }

                if (isReached) {
                    usageMap[appInGroup.appPackage] = newAppUsage
                    val updatedUsage = usageMap.entries.map { (pkg, millis) -> AppUsageStat(pkg, millis) }
                    appLimitGroupDao.updateTimeAndUsage(
                        groupId = entity.id,
                        timeRemaining = newTimeRemaining,
                        perAppUsage = updatedUsage
                    )

                    val appTimeSpent = newAppUsage
                    val groupTimeSpent = usageMap.values.sum()
                    blockIntent.putExtra("app_name", appInGroup.appName)
                    blockIntent.putExtra("app_package", appInGroup.appPackage)
                    blockIntent.putExtra("group_name", group.name)
                    blockIntent.putExtra("app_time_spent", appTimeSpent)
                    blockIntent.putExtra("group_time_spent", groupTimeSpent)
                    blockIntent.putExtra("time_limit_minutes", group.timeHrLimit * 60 + group.timeMinLimit)
                    blockIntent.putExtra("limit_each", group.limitEach)
                    blockIntent.putExtra("use_time_range", group.useTimeRange)
                    blockIntent.putExtra("block_outside_time_range", group.blockOutsideTimeRange)
                    blockIntent.putExtra("blocked_for_outside_range", false)
                    blockIntent.putExtra("next_reset_time", TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false))
                    blockIntent.putExtra("block_reason", "Out of Time")
                    launchBlockScreen(appInGroup.appPackage)
                    didBlock = true
                } else {
                    usageMap[appInGroup.appPackage] = newAppUsage
                    val updatedUsage = usageMap.entries.map { (pkg, millis) -> AppUsageStat(pkg, millis) }
                    appLimitGroupDao.updateTimeAndUsage(
                        groupId = entity.id,
                        timeRemaining = newTimeRemaining,
                        perAppUsage = updatedUsage
                    )
                }
            }
        }
        return didBlock
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotification() {
        val channelId = "APPTICK_CHANNEL_v2"
        val defaultContentText = "AppTick is running..."
        mBuilder = NotificationCompat.Builder(applicationContext, channelId)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // "Show Bubble" action — sends broadcast to re-show dismissed bubble
        val showBubbleIntent = Intent(ACTION_SHOW_BUBBLE).setPackage(packageName)
        val showBubblePendingIntent = PendingIntent.getBroadcast(
            this, 1, showBubbleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mBuilder.apply {
            setSmallIcon(R.drawable.ic_launcher_foreground)
            setContentTitle("AppTick")
            setContentText(defaultContentText)
            setStyle(NotificationCompat.BigTextStyle().bigText(defaultContentText))
            color = Color.argb(255, 0, 151, 167)
            priority = NotificationManager.IMPORTANCE_LOW
            setOnlyAlertOnce(true)
            setOngoing(true)
            setContentIntent(pendingIntent)
            addAction(
                R.drawable.ic_launcher_foreground,
                "Show Bubble",
                showBubblePendingIntent
            )
        }

        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Delete legacy channel so badge/importance changes take effect on update
        mNotificationManager.deleteNotificationChannel("APPTICK_CHANNEL")

        val channel = NotificationChannel(
            channelId,
            "AppTick app blocker notification (required for AppTick to run properly)",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        mNotificationManager.createNotificationChannel(channel)
        mBuilder.setChannelId(channelId)
    }

    private suspend fun updateNotification(currentApp: String?) {
        val contentText = if (currentApp != null) {
            val info = pickNotificationGroup(cachedGroups, currentApp)
            if (info != null) {
                val group = info.entity.toDomainModel()
                val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                val limitInMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
                val appUsed = group.perAppUsage.firstOrNull { stat -> stat.appPackage == currentApp }?.usedMillis ?: 0L
                val timeRemaining = if (group.limitEach) {
                    (limitInMillis - appUsed).coerceAtLeast(0L)
                } else {
                    group.timeRemaining
                }
                formatGroupNotificationText(
                    groupName = group.name ?: "Unnamed",
                    limitHours = group.timeHrLimit,
                    limitMinutes = group.timeMinLimit,
                    timeRemainingMillis = timeRemaining,
                    nextResetTimeMillis = group.nextResetTime,
                    isMultiProfile = info.isMultiProfile
                )
            } else {
                "AppTick is running..."
            }
        } else {
            "AppTick is running..."
        }
        // Skip the expensive NotificationManager.notify() call when content hasn't changed.
        if (contentText == lastNotificationText) return
        lastNotificationText = contentText
        mBuilder.setContentText(contentText)
        mBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        mNotificationManager.notify(notifId, mBuilder.build())
    }

    // ── Floating bubble management ────────────────────────────────────────────

    private suspend fun updateFloatingBubble(
        currentApp: String?,
        fallbackApp: String?,
        visibleApps: Set<String> = emptySet()
    ) {
        val prefs = getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)

        // Reset dismissed flag when the foreground app changes so the bubble
        // reappears the next time the user enters a limited app.
        if (currentApp != null && currentApp != prevBubbleForegroundApp) {
            prefs.edit().putBoolean(FloatingBubbleService.PREF_BUBBLE_DISMISSED, false).apply()
        }
        if (currentApp != null) {
            prevBubbleForegroundApp = currentApp
        }

        val isPremium = PremiumStore.isPremium(this)
        if (!isPremium) {
            activeBubbleApps.clear()
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }
        val bubbleEnabled = prefs.getBoolean(
            FloatingBubbleService.PREF_FLOATING_BUBBLE_ENABLED, false
        )
        if (!bubbleEnabled) {
            activeBubbleApps.clear()
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w("BG_Service", "Floating bubble enabled but overlay permission is missing")
            activeBubbleApps.clear()
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }
        val foregroundApp = currentApp ?: fallbackApp
        if (shouldHideFloatingBubbleForForegroundApp(foregroundApp, packageName)) {
            activeBubbleApps.clear()
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }

        val resolvedForegroundApp = foregroundApp ?: return

        // Use cached groups for bubble display — the bubble is cosmetic and the
        // independent 1-second countdown in FloatingBubbleService handles precision.
        // This avoids an expensive suspend DB query every loop iteration.
        val freshGroups = cachedGroups

        // Collect ALL visible apps that should show a bubble
        val appsToShow = mutableMapOf<String, Pair<Long, String>>() // package -> (timeRemaining, text)

        // Check foreground app
        computeBubbleDataForApp(freshGroups, resolvedForegroundApp)?.let {
            appsToShow[resolvedForegroundApp] = it
        }

        // Check other visible apps (split-screen, PiP) — each gets its own bubble
        for (visibleApp in visibleApps) {
            if (visibleApp in appsToShow) continue
            computeBubbleDataForApp(freshGroups, visibleApp)?.let {
                appsToShow[visibleApp] = it
            }
        }

        if (appsToShow.isEmpty()) {
            activeBubbleApps.clear()
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }

        // Remove bubbles for apps no longer visible/limited
        val appsToRemove = activeBubbleApps - appsToShow.keys
        for (app in appsToRemove) {
            try { startService(FloatingBubbleService.removeAppIntent(this, app)) } catch (_: Exception) {}
        }

        // Update/create bubbles for all visible limited apps
        for ((app, pair) in appsToShow) {
            val (timeRemaining, bubbleText) = pair
            try {
                startService(
                    FloatingBubbleService.updateIntent(this, bubbleText, timeRemaining, app)
                )
            } catch (_: Exception) {}
        }

        activeBubbleApps.clear()
        activeBubbleApps.addAll(appsToShow.keys)
    }

    /**
     * Returns (timeRemaining, formattedText) for an app if it has an active limit
     * with time remaining, or null if no bubble should be shown.
     */
    private fun computeBubbleDataForApp(
        groups: List<AppLimitGroupEntity>,
        appPackage: String
    ): Pair<Long, String>? {
        val info = pickNotificationGroup(groups, appPackage) ?: return null
        val group = info.entity.toDomainModel()
        val limitMillis = TimeUnit.MINUTES.toMillis(
            (group.timeHrLimit * 60 + group.timeMinLimit).toLong()
        )
        val appUsed = group.perAppUsage
            .firstOrNull { it.appPackage == appPackage }?.usedMillis ?: 0L
        val timeRemaining = if (group.limitEach) {
            (limitMillis - appUsed).coerceAtLeast(0L)
        } else {
            group.timeRemaining
        }
        if (timeRemaining <= 0L) return null
        return timeRemaining to formatBubbleCountdown(timeRemaining)
    }

    companion object {
        @VisibleForTesting
        internal fun shouldHideFloatingBubbleForForegroundApp(
            currentApp: String?,
            @Suppress("UNUSED_PARAMETER") appTickPackage: String
        ): Boolean = currentApp == null

        private const val FLOATING_CLOSE_DELAY_MS = 700L
        private const val CHECK_INTERVAL = 2000L // 2 seconds
        private const val IDLE_CHECK_INTERVAL = 2000L // 2 seconds — same as CHECK_INTERVAL to ensure responsive blocking when accessibility is off
        private const val MAX_ELAPSED = 10_000L // Cap to prevent huge jumps after long delays
        private const val FOREGROUND_EVENT_LOOKBACK_MS = 15_000L
        private const val FOREGROUND_USAGE_LOOKBACK_MS = 2 * 60_000L
        private const val FOREGROUND_USAGE_MAX_AGE_MS = 15_000L

        @Volatile
        var isRunning = false
            private set

        @VisibleForTesting
        @Volatile
        var disableBackgroundLoopForTesting: Boolean = false

        private const val WATCHDOG_REQUEST_CODE = 4812
        private const val WATCHDOG_INTERVAL_MS = 45_000L
        private const val WATCHDOG_RETRY_DELAY_MS = 3_000L

        private fun watchdogPendingIntent(
            context: Context,
            flags: Int
        ): PendingIntent? {
            return PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                Intent(context, Receiver::class.java).apply {
                    action = Receiver.ACTION_SERVICE_WATCHDOG
                    setPackage(context.packageName)
                },
                flags
            )
        }

        fun scheduleServiceWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = watchdogPendingIntent(
                context,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        }

        fun cancelServiceWatchdog(context: Context) {
            val existing = watchdogPendingIntent(
                context,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(existing)
            existing.cancel()
        }

        fun applyDesiredServiceState(context: Context, shouldRun: Boolean) {
            if (shouldRun) {
                startServiceIfNotRunning(context)
                scheduleServiceWatchdog(context)
            } else {
                context.stopService(Intent(context, BackgroundChecker::class.java))
                cancelServiceWatchdog(context)
            }
        }

        fun startServiceIfNotRunning(context: Context) {
            if (isRunning) {
                // Service is already running — skip redundant AlarmManager reschedule.
                return
            }

            val intent = Intent(context, BackgroundChecker::class.java)
            val started = runCatching {
                context.startForegroundService(intent)
            }.isSuccess

            if (!started) {
                scheduleImmediateServiceRetry(context)
            }

            scheduleServiceWatchdog(context)
        }

        private fun scheduleImmediateServiceRetry(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = watchdogPendingIntent(
                context,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_RETRY_DELAY_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        }

        /** Channel to wake the polling loop when the accessibility service detects a new app. */
        private val wakeUpChannel = Channel<Unit>(Channel.CONFLATED)

        /** Signal the loop to check immediately (called by AccessibilityService on new app). */
        fun requestImmediateCheck() {
            wakeUpChannel.trySend(Unit)
        }

        /** Broadcast action for the "Show Bubble" notification button. */
        const val ACTION_SHOW_BUBBLE = "com.juliacai.apptick.ACTION_SHOW_BUBBLE"

        /**
         * Picks the best group to display in the notification for [currentApp].
         * Filters out paused groups, then selects the active group with the
         * lowest effective time remaining. Returns null if no active group
         * covers [currentApp].
         */
        @VisibleForTesting
        fun pickNotificationGroup(
            groups: List<AppLimitGroupEntity>,
            currentApp: String,
            nowMillis: Long = System.currentTimeMillis()
        ): NotificationGroupInfo? {
            val candidates = groups.mapNotNull { entity ->
                val remaining = effectiveRemainingMillis(entity, currentApp, nowMillis) ?: return@mapNotNull null
                entity to remaining
            }
            if (candidates.isEmpty()) return null

            val best = candidates.minByOrNull { it.second } ?: return null

            return NotificationGroupInfo(
                entity = best.first,
                isMultiProfile = candidates.size > 1
            )
        }

        @VisibleForTesting
        fun effectiveRemainingMillis(
            entity: AppLimitGroupEntity,
            currentApp: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Long? {
            if (entity.paused) return null
            if (entity.apps.none { it.appPackage == currentApp }) return null

            val group = entity.toDomainModel()
            if (!AppLimitEvaluator.shouldCheckLimit(group, nowMillis)) return null

            val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
            if (limitInMinutes <= 0) return null

            val limitMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
            return if (group.limitEach) {
                val appUsed = group.perAppUsage
                    .firstOrNull { it.appPackage == currentApp }?.usedMillis ?: 0L
                (limitMillis - appUsed).coerceAtLeast(0L)
            } else {
                group.timeRemaining.coerceAtLeast(0L)
            }
        }
    }

    /** Holds the selected notification group and whether multiple active profiles exist. */
    data class NotificationGroupInfo(
        val entity: AppLimitGroupEntity,
        val isMultiProfile: Boolean
    )

    @VisibleForTesting
    fun formatGroupNotificationText(
        groupName: String,
        limitHours: Int,
        limitMinutes: Int,
        timeRemainingMillis: Long,
        nextResetTimeMillis: Long,
        isMultiProfile: Boolean
    ): String {
        val totalLimitMinutes = limitHours * 60 + limitMinutes
        val totalLimitMillis = TimeUnit.MINUTES.toMillis(totalLimitMinutes.toLong())
        val timeUsedMillis = (totalLimitMillis - timeRemainingMillis).coerceAtLeast(0L)

        val usedMinutes = TimeUnit.MILLISECONDS.toMinutes(timeUsedMillis)
        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMillis.coerceAtLeast(0L))
        val resetText = if (nextResetTimeMillis > 0L) {
            val formatter = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
            formatter.format(Date(nextResetTimeMillis))
        } else {
            "unscheduled"
        }

        val base = "$groupName: Used ${usedMinutes}m, left ${remainingMinutes}m, resets $resetText"
        return if (isMultiProfile) "$base (+ more profiles)" else base
    }

    @VisibleForTesting
    fun formatBubbleCountdown(timeRemainingMillis: Long): String {
        val safeMillis = timeRemainingMillis.coerceAtLeast(0L)
        if (safeMillis <= 60_000L) {
            val totalSeconds = (safeMillis + 999L) / 1_000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format("%02d:%02d", minutes, seconds)
        }

        val totalMinutes = safeMillis / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return String.format("%02d:%02d", hours, minutes)
    }

    /**
     * Computes the real-time elapsed delta since the last check, capped at MAX_ELAPSED.
     * Call once per loop iteration and pass to checkAppLimits to avoid double-counting.
     */
    private fun computeElapsedDelta(): Long {
        return fixedElapsedForTestingMs ?: run {
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastCheckElapsed).coerceIn(0L, MAX_ELAPSED)
            lastCheckElapsed = now
            delta
        }
    }

    // ── Foreground app detection ──────────────────────────────────────────────

    private fun getForegroundApp(): String? {
        // ── Source 1: AccessibilityService (instant, event-driven) ──
        val accessibilityApp = AppTickAccessibilityService.getForegroundPackage()
        val accessibilityTimestamp = if (accessibilityApp != null) {
            AppTickAccessibilityService.lastUpdateTimeMillis
        } else {
            0L
        }

        // ── Source 2: UsageStats events (OS ground truth) ──
        // Always queried as a cross-check — on some OEM skins (Honor/EMUI)
        // accessibility can report a stale/wrong package (missed events, launcher
        // transitions, etc.). The UsageStats cross-check catches these cases.
        // Speed comes from requestImmediateCheck() waking the loop, not from
        // skipping this query.
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - FOREGROUND_EVENT_LOOKBACK_MS, time)
        var usageStatsApp: String? = null
        var latestForegroundEventTime = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForegroundEvent && !event.packageName.isNullOrBlank()) {
                if (event.timeStamp >= latestForegroundEventTime) {
                    latestForegroundEventTime = event.timeStamp
                    usageStatsApp = event.packageName
                }
            }
        }

        // ── Pick whichever source has the most recent foreground transition ──
        if (accessibilityApp != null && !usageStatsApp.isNullOrBlank()) {
            return if (latestForegroundEventTime > accessibilityTimestamp) {
                usageStatsApp
            } else {
                accessibilityApp
            }
        }

        if (accessibilityApp != null) return accessibilityApp
        if (!usageStatsApp.isNullOrBlank()) return usageStatsApp

        // Neither source has recent events — fall back to most recently used package.
        // This covers service restarts where user stays in one app with no new events.
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - FOREGROUND_USAGE_LOOKBACK_MS,
            time
        )
            ?.asSequence()
            ?.filter { stat: UsageStats ->
                !stat.packageName.isNullOrBlank() &&
                        stat.lastTimeUsed > 0L &&
                        (time - stat.lastTimeUsed) <= FOREGROUND_USAGE_MAX_AGE_MS
            }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("BG_Service", "Service starting...")
        startForeground(notifId, mBuilder.build())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        dismissBlockScreen()
        serviceJob.cancel()
        unregisterReceiver(mReceiver)
        try { unregisterReceiver(bubbleShowReceiver) } catch (_: Exception) {}

        // Hide the floating bubble when the service stops
        try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
        // Recover quickly if OEM/user UI stops the foreground service while limits remain active.
        scheduleImmediateServiceRetry(this)
        Log.i("BG_Service", "Service destroyed")
    }

    // ── Screen state ──────────────────────────────────────────────────────────

    inner class ScreenStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = shouldTrackUsageNow()
                    // Prevent large elapsed jumps if checks resume before next loop iteration.
                    lastCheckElapsed = SystemClock.elapsedRealtime()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    // Screen-off/lock periods must not be counted as app usage.
                    lastCheckElapsed = SystemClock.elapsedRealtime()
                    // Reset so block screen re-triggers on next unlock if needed
                    lastBlockedForPackage = null
                }
            }
        }
    }

    private fun shouldTrackUsageNow(): Boolean {
        val interactive = powerManager.isInteractive
        val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager.isDeviceLocked
        } else {
            keyguardManager.inKeyguardRestrictedInputMode()
        }
        return interactive && !locked
    }

    // ── Companion object moved up next to updateNotification ──────────────────

    /**
     * Receives the "Show Bubble" broadcast from the notification action.
     * Clears the dismissed flag and re-shows the bubble with the latest text.
     */
    inner class BubbleShowReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("groupPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean(FloatingBubbleService.PREF_BUBBLE_DISMISSED, false).apply()

            // Use lastForegroundApp — getForegroundApp() returns the notification
            // shade when the user is pulling down to tap the action button.
            val app = lastForegroundApp ?: return
            val info = pickNotificationGroup(cachedGroups, app) ?: return
            val group = info.entity.toDomainModel()
            val limitMillis = TimeUnit.MINUTES.toMillis(
                (group.timeHrLimit * 60 + group.timeMinLimit).toLong()
            )
            val appUsed = group.perAppUsage
                .firstOrNull { it.appPackage == app }?.usedMillis ?: 0L
            val timeRemaining = if (group.limitEach) {
                (limitMillis - appUsed).coerceAtLeast(0L)
            } else {
                group.timeRemaining
            }
            if (timeRemaining <= 0L) {
                try { ctx.startService(FloatingBubbleService.hideIntent(ctx)) } catch (_: Exception) {}
                return
            }
            val bubbleText = formatBubbleCountdown(timeRemaining)
            try {
                ctx.startService(
                    FloatingBubbleService.showIntent(ctx, bubbleText, timeRemaining, app)
                )
            } catch (_: Exception) {}
        }
    }

    @VisibleForTesting
    fun setFixedElapsedForTesting(elapsedMs: Long?) {
        fixedElapsedForTestingMs = elapsedMs?.coerceAtLeast(0L)
        lastCheckElapsed = SystemClock.elapsedRealtime()
        // Default to screen-on in test mode so tests don't fail when the
        // physical device screen is off.  Explicit screen-off tests call
        // setScreenOnForTesting(false) after this method.
        if (elapsedMs != null) isScreenOn = true
    }

    @VisibleForTesting
    fun setScreenOnForTesting(on: Boolean) {
        isScreenOn = on
    }

    private fun currentCheckIntervalMs(foregroundApp: String?): Long {
        val packageName = foregroundApp ?: return IDLE_CHECK_INTERVAL
        // Use faster polling when the foreground app is in a tracked group,
        // slower polling otherwise to conserve battery.
        val isTracked = cachedGroups.any { entity ->
            !entity.paused && entity.apps.any { it.appPackage == packageName }
        }
        return if (isTracked) CHECK_INTERVAL else IDLE_CHECK_INTERVAL
    }

}
