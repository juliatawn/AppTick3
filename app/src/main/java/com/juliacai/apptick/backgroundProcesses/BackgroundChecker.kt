package com.juliacai.apptick.backgroundProcesses

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.LockPolicy
import com.juliacai.apptick.LockState
import com.juliacai.apptick.LockdownType
import com.juliacai.apptick.MainActivity
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BackgroundChecker : Service() {

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var powerManager: PowerManager
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
    private var lastCheckElapsed: Long = 0L
    private var isScreenOn = true
    private var bubbleShowReceiver: BroadcastReceiver? = null
    @Volatile
    private var fixedElapsedForTestingMs: Long? = null

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundChecker = this@BackgroundChecker
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        // Initialize UsageStatsManager once
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
                // Self-heal screen state in case SCREEN_ON/OFF broadcasts are delayed or missed.
                isScreenOn = powerManager.isInteractive
                if (isScreenOn) {
                    val foregroundApp = getForegroundApp()
                    if (foregroundApp != null) {
                        lastForegroundApp = foregroundApp
                    }
                    val appToCheck = foregroundApp ?: lastForegroundApp
                    
                    try {
                        checkAppLimits(appToCheck)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        updateNotification(appToCheck)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        updateFloatingBubble(appToCheck)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(CHECK_INTERVAL)
            }
        }
    }

    // ── Core limit checking ───────────────────────────────────────────────────

    suspend fun checkAppLimits(foregroundApp: String?) {
        // Never block AppTick itself — the user must always be able to manage their limits
        if (foregroundApp == packageName) return

        if (shouldBlockSettingsAccess(foregroundApp)) {
            blockIntent.putExtra("app_name", "Settings")
            blockIntent.putExtra("app_package", foregroundApp)
            blockIntent.putExtra("group_name", "Uninstall Protection")
            blockIntent.putExtra("app_time_spent", 0L)
            blockIntent.putExtra("group_time_spent", 0L)
            blockIntent.putExtra("time_limit_minutes", 0)
            blockIntent.putExtra("limit_each", false)
            blockIntent.putExtra("use_time_range", false)
            blockIntent.putExtra("block_outside_time_range", false)
            blockIntent.putExtra("blocked_for_outside_range", false)
            blockIntent.putExtra("block_reason", "Used up time limit")
            startActivity(blockIntent)
            return
        }

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

        if (activeGroups.isEmpty() && !shouldKeepServiceForSettingsProtection()) {
            stopSelf()
            return
        }

        // Measure real elapsed time since last check
        val elapsed = fixedElapsedForTestingMs ?: run {
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastCheckElapsed).coerceIn(0L, MAX_ELAPSED)
            lastCheckElapsed = now
            delta
        }

        for (entity in activeGroups) {
            val group = entity.toDomainModel()
            val now = System.currentTimeMillis()
            val appInGroup = group.apps.firstOrNull { it.appPackage == foregroundApp }

            // ── Daily / periodic reset check ──────────────────────────────────
            if (group.nextResetTime in 1..now) {
                val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                val fullLimitMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
                val isPeriodicCumulative = group.cumulativeTime && group.resetMinutes > 0

                // Advance nextResetTime based on mode
                val newNextReset = if (group.resetMinutes > 0) {
                    now + TimeUnit.MINUTES.toMillis(group.resetMinutes.toLong())
                } else {
                    TimeManager.nextMidnight(now)
                }

                // Zero out all per-app usage
                val clearedUsage = group.perAppUsage.map { it.copy(usedMillis = 0L) }
                val newTimeRemaining = if (isPeriodicCumulative) {
                    (group.timeRemaining.coerceAtLeast(0L) + fullLimitMillis).coerceAtLeast(0L)
                } else {
                    fullLimitMillis
                }

                appLimitGroupDao.updateAppLimitGroup(
                    entity.copy(
                        timeRemaining = newTimeRemaining,
                        nextResetTime = newNextReset,
                        nextAddTime = if (isPeriodicCumulative) newNextReset else 0L,
                        perAppUsage = clearedUsage
                    )
                )
                // Skip further processing this tick — fresh data will be seen next loop
                continue
            }

            // If today is not active for this group, skip all enforcement and accounting.
            if (!AppLimitEvaluator.isWithinActiveDays(group, now)) continue

            // Optional strict mode: block listed apps completely outside the configured time range.
            if (
                appInGroup != null &&
                group.useTimeRange &&
                group.blockOutsideTimeRange &&
                !AppLimitEvaluator.isWithinTimeRange(group, now)
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
                blockIntent.putExtra("block_reason", "Outside configured time range")
                startActivity(blockIntent)
                continue
            }

            // Check if the limit is active for today/now
            if (!AppLimitEvaluator.shouldCheckLimit(group, now)) continue

            val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
            if (appInGroup != null) {
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
                    blockIntent.putExtra("block_reason", "Used up time limit")
                    startActivity(blockIntent)
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
                    appLimitGroupDao.updateAppLimitGroup(
                        entity.copy(
                            timeRemaining = newTimeRemaining,
                            perAppUsage = updatedUsage
                        )
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
                    blockIntent.putExtra("block_reason", "Out of Time")
                    startActivity(blockIntent)
                } else {
                    usageMap[appInGroup.appPackage] = newAppUsage
                    val updatedUsage = usageMap.entries.map { (pkg, millis) -> AppUsageStat(pkg, millis) }
                    appLimitGroupDao.updateAppLimitGroup(
                        entity.copy(
                            timeRemaining = newTimeRemaining,
                            perAppUsage = updatedUsage
                        )
                    )
                }
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotification() {
        val channelId = "APPTICK_CHANNEL"
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

        val channel = NotificationChannel(
            channelId,
            "AppTick app blocker notification (required for AppTick to run properly)",
            NotificationManager.IMPORTANCE_MIN
        )
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
        mBuilder.setContentText(contentText)
        mBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        mNotificationManager.notify(notifId, mBuilder.build())
    }

    // ── Floating bubble management ────────────────────────────────────────────

    private suspend fun updateFloatingBubble(currentApp: String?) {
        val prefs = getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        val isPremium = prefs.getBoolean("premium", false)
        if (!isPremium) {
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }
        val bubbleEnabled = prefs.getBoolean(
            FloatingBubbleService.PREF_FLOATING_BUBBLE_ENABLED, false
        )
        if (!bubbleEnabled) {
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w("BG_Service", "Floating bubble enabled but overlay permission is missing")
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }
        // If currentApp is null (transient UsageStats gap), leave bubble as-is
        if (currentApp == null || currentApp == packageName) return

        // Read fresh data from DB to avoid stale cachedGroups lag
        val freshGroups = appLimitGroupDao.getAllAppLimitGroupsImmediate()
        val info = pickNotificationGroup(freshGroups, currentApp)
        if (info == null) {
            // User is in an app with no active limit — hide bubble
            try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
            return
        }

        val group = info.entity.toDomainModel()
        val limitMillis = TimeUnit.MINUTES.toMillis(
            (group.timeHrLimit * 60 + group.timeMinLimit).toLong()
        )
        val appUsed = group.perAppUsage
            .firstOrNull { it.appPackage == currentApp }?.usedMillis ?: 0L
        val timeRemaining = if (group.limitEach) {
            (limitMillis - appUsed).coerceAtLeast(0L)
        } else {
            group.timeRemaining
        }

        val bubbleText = formatBubbleCountdown(timeRemaining)

        try {
            startService(
                FloatingBubbleService.updateIntent(this, bubbleText, timeRemaining, currentApp)
            )
        } catch (_: Exception) {}
    }


    companion object {
        private const val CHECK_INTERVAL = 2000L // 2 seconds — balanced battery/responsiveness
        private const val MAX_ELAPSED = 10_000L // Cap to prevent huge jumps after long delays

        @Volatile
        var isRunning = false
            private set

        @VisibleForTesting
        @Volatile
        var disableBackgroundLoopForTesting: Boolean = false

        fun startServiceIfNotRunning(context: Context) {
            if (!isRunning) {
                val intent = Intent(context, BackgroundChecker::class.java)
                context.startForegroundService(intent)
            }
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
        val totalMinutes = if (safeMillis <= 0L) 0L else maxOf(1L, safeMillis / 60_000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return String.format("%02d:%02d", hours, minutes)
    }

    // ── Foreground app detection ──────────────────────────────────────────────

    private fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 1000 * 5, time)
        var foregroundApp: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.packageName
            }
        }
        return foregroundApp
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
        serviceJob.cancel()
        unregisterReceiver(mReceiver)
        try { unregisterReceiver(bubbleShowReceiver) } catch (_: Exception) {}
        // Hide the floating bubble when the service stops
        try { startService(FloatingBubbleService.hideIntent(this)) } catch (_: Exception) {}
        Log.i("BG_Service", "Service destroyed")
    }

    // ── Screen state ──────────────────────────────────────────────────────────

    inner class ScreenStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            }
        }
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
    }

    // ── Settings/uninstall protection ─────────────────────────────────────────

    private fun shouldBlockSettingsAccess(foregroundApp: String?): Boolean {
        val packageName = foregroundApp ?: return false
        val prefs = getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("blockSettings", false)) return false

        val lockState = readLockState(prefs)
        val lockDecision = LockPolicy.evaluateEditingLock(lockState, System.currentTimeMillis())
        if (lockDecision.shouldClearExpiredLockdown) {
            prefs.edit {
                putString("active_lock_mode", "NONE")
                remove("lockdown_end_time")
                remove("lockdown_weekly_used_key")
            }
        }

        // Block direct Settings access while the lock policy is actively locked.
        if (isSettingsPackage(packageName) && lockDecision.isLocked) {
            return true
        }

        // Uninstall confirmation often switches to package-installer/permission-controller apps.
        // Keep these blocked whenever uninstall protection is enabled and a lock mode is configured.
        if (isUninstallFlowPackage(packageName) && LockPolicy.hasAnyConfiguredLockMode(lockState)) {
            return true
        }

        return false
    }

    private fun shouldKeepServiceForSettingsProtection(): Boolean {
        val prefs = getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("blockSettings", false)) return false
        return LockPolicy.hasAnyConfiguredLockMode(readLockState(prefs))
    }

    private fun readLockState(prefs: android.content.SharedPreferences): LockState {
        val activeMode = try {
            LockMode.valueOf(prefs.getString("active_lock_mode", "NONE") ?: "NONE")
        } catch (_: Exception) {
            LockMode.NONE
        }
        val lockdownType = try {
            LockdownType.valueOf(prefs.getString("lockdown_type", "ONE_TIME") ?: "ONE_TIME")
        } catch (_: Exception) {
            LockdownType.ONE_TIME
        }
        val recurringDays = prefs.getString("lockdown_recurring_days", "")
            .orEmpty()
            .split(",")
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

    private fun isSettingsPackage(packageName: String): Boolean {
        if (packageName == "com.android.settings") return true
        if (packageName == "com.samsung.android.settings") return true
        return packageName.contains(".settings")
    }

    private fun isUninstallFlowPackage(packageName: String): Boolean {
        if (packageName == "com.android.packageinstaller") return true
        if (packageName == "com.google.android.packageinstaller") return true
        if (packageName == "com.samsung.android.packageinstaller") return true
        if (packageName == "com.miui.packageinstaller") return true
        if (packageName == "com.android.permissioncontroller") return true
        if (packageName == "com.google.android.permissioncontroller") return true
        return packageName.contains("packageinstaller") || packageName.contains("permissioncontroller")
    }
}
