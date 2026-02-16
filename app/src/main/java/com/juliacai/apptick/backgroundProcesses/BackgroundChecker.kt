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
import android.os.SystemClock
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
                if (isScreenOn) {
                    val foregroundApp = getForegroundApp()
                    checkAppLimits(foregroundApp)
                    updateNotification(foregroundApp)
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

            // ── Daily / periodic reset check ──────────────────────────────────
            val now = System.currentTimeMillis()
            if (group.nextResetTime in 1..now) {
                val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                val fullLimitMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())

                // Advance nextResetTime based on mode
                val newNextReset = if (group.resetHours > 0) {
                    now + TimeUnit.HOURS.toMillis(group.resetHours.toLong())
                } else {
                    TimeManager.nextMidnight(now)
                }

                // Zero out all per-app usage
                val clearedUsage = group.perAppUsage.map { it.copy(usedMillis = 0L) }

                appLimitGroupDao.updateAppLimitGroup(
                    entity.copy(
                        timeRemaining = fullLimitMillis,
                        nextResetTime = newNextReset,
                        perAppUsage = clearedUsage
                    )
                )
                // Skip further processing this tick — fresh data will be seen next loop
                continue
            }

            // Check if the limit is active for today/now
            if (!com.juliacai.apptick.appLimit.AppLimitEvaluator.shouldCheckLimit(group)) continue

            val appInGroup = group.apps.firstOrNull { it.appPackage == foregroundApp }
            if (appInGroup != null) {
                val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                val limitInMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
                val usageMap = group.perAppUsage.associate { it.appPackage to it.usedMillis }.toMutableMap()
                val currentAppUsage = usageMap[appInGroup.appPackage] ?: 0L

                val appLimitReached = currentAppUsage >= limitInMillis
                val groupLimitReached = group.timeRemaining <= 0L
                val isReached = if (group.limitEach) appLimitReached else groupLimitReached

                if (isReached) {
                    val appTimeSpent = currentAppUsage
                    val groupTimeSpent = usageMap.values.sum()
                    blockIntent.putExtra("app_name", appInGroup.appName)
                    blockIntent.putExtra("app_package", appInGroup.appPackage)
                    blockIntent.putExtra("group_name", group.name)
                    blockIntent.putExtra("app_time_spent", appTimeSpent)
                    blockIntent.putExtra("group_time_spent", groupTimeSpent)
                    blockIntent.putExtra("time_limit_minutes", group.timeHrLimit * 60 + group.timeMinLimit)
                    blockIntent.putExtra("limit_each", group.limitEach)
                    startActivity(blockIntent)
                } else {
                    // Use real elapsed time instead of assuming exact interval
                    val newAppUsage = (currentAppUsage + elapsed).coerceAtMost(limitInMillis)
                    usageMap[appInGroup.appPackage] = newAppUsage

                    val newTimeRemaining = if (group.limitEach) {
                        (limitInMillis - newAppUsage).coerceAtLeast(0L)
                    } else {
                        (group.timeRemaining - elapsed).coerceAtLeast(0L)
                    }

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
        mBuilder = NotificationCompat.Builder(applicationContext, channelId)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        mBuilder.apply {
            setSmallIcon(R.drawable.ic_launcher_foreground)
            setContentTitle("AppTick")
            setContentText("AppTick is running...")
            color = Color.argb(255, 0, 151, 167)
            priority = NotificationManager.IMPORTANCE_LOW
            setOnlyAlertOnce(true)
            setOngoing(true)
            setContentIntent(pendingIntent)
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
            val groupEntity = cachedGroups.firstOrNull { entity ->
                entity.apps.any { it.appPackage == currentApp }
            }
            if (groupEntity != null) {
                val group = groupEntity.toDomainModel()
                val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                val limitInMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
                val appUsed = group.perAppUsage.firstOrNull { stat -> stat.appPackage == currentApp }?.usedMillis ?: 0L
                val timeRemaining = if (group.limitEach) {
                    (limitInMillis - appUsed).coerceAtLeast(0L)
                } else {
                    group.timeRemaining
                }
                formatGroupNotificationText(group.timeHrLimit, group.timeMinLimit, timeRemaining, group.nextResetTime)
            } else {
                "AppTick is running..."
            }
        } else {
            "AppTick is running..."
        }
        mBuilder.setContentText(contentText)
        mNotificationManager.notify(notifId, mBuilder.build())
    }

    private fun formatGroupNotificationText(
        limitHours: Int,
        limitMinutes: Int,
        timeRemainingMillis: Long,
        nextResetTimeMillis: Long
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

        return "Used ${usedMinutes}m, left ${remainingMinutes}m, resets $resetText"
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

    // ── Companion: service running check (no deprecated API) ──────────────────

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
