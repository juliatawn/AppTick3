package com.juliacai.apptick.backgroundProcesses

import android.app.ActivityManager
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
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.juliacai.apptick.block.BlockWindowActivity
import com.juliacai.apptick.LockPolicy
import com.juliacai.apptick.LockState
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.R
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
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

    private lateinit var mUsageStatsManager: UsageStatsManager
    private lateinit var blockIntent: Intent

    private val db by lazy { AppTickDatabase.getDatabase(this) }
    private val appLimitGroupDao by lazy { db.appLimitGroupDao() }

    private lateinit var mBuilder: NotificationCompat.Builder
    private var notif_ID: Int = 1
    private var mReceiver: BroadcastReceiver? = null
    private lateinit var mNotificationManager: NotificationManager

    private var lastForegroundApp: String? = null
    private var lastUpdateTime: Long = 0
    private var isScreenOn = true

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundChecker = this@BackgroundChecker
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val field = ServiceInfo::class.java.getField("FOREGROUND_SERVICE_TYPE_SPECIAL_USE")
                notif_ID = field.getInt(null)
            } catch (e: Exception) {
                println("WARNING - unable to set notif_ID to FOREGROUND_SERVICE_TYPE_SPECIAL_CASE: $e")
            }
        }

        createNotification()

        // Source - https://stackoverflow.com/a/77530440
// Posted by Ondřej Skalický, modified by community. See post 'Timeline' for change history
// Retrieved 2026-02-07, License - CC BY-SA 4.0

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(notif_ID, mBuilder.build())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            startForeground(notif_ID, mBuilder.build(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        }


        blockIntent = Intent(this, BlockWindowActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        mReceiver = ScreenStateReceiver()
        registerReceiver(mReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        startNotificationUpdates()
        startBackgroundChecks()
    }

    private fun startNotificationUpdates() {
        serviceScope.launch {
            while (isActive) {
                val currentApp = getForegroundApp()
                val currentTime = System.currentTimeMillis()

                if (currentApp != lastForegroundApp ||
                    (currentTime - lastUpdateTime) >= NOTIFICATION_UPDATE_INTERVAL
                ) {
                    updateNotification(currentApp)
                    lastForegroundApp = currentApp
                    lastUpdateTime = currentTime
                }
                delay(NOTIFICATION_UPDATE_INTERVAL)
            }
        }
    }

    private fun startBackgroundChecks() {
        serviceScope.launch {
            Log.i("BG_Service", "Background processing started")
            while (isActive) {
                if (isScreenOn) {
                    checkAppLimits(getForegroundApp())
                }
                delay(BACKGROUND_CHECK_INTERVAL)
            }
        }
    }

    suspend fun checkAppLimits(foregroundApp: String?) {
        if (shouldBlockSettingsAccess(foregroundApp)) {
            blockIntent.putExtra("app_name", "Settings")
            blockIntent.putExtra("app_package", foregroundApp)
            blockIntent.putExtra("group_name", "Uninstall Protection")
            blockIntent.putExtra("app_time_spent", 0L)
            blockIntent.putExtra("group_time_spent", 0L)
            startActivity(blockIntent)
            return
        }

        val allGroups = appLimitGroupDao.getAllAppLimitGroupsImmediate()
        val activeGroups = allGroups.filterNot { it.paused }

        if (activeGroups.isEmpty() && !shouldKeepServiceForSettingsProtection()) {
            stopSelf()
            return
        }

        for (entity in activeGroups) {
            val group = entity.toDomainModel()

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
                    startActivity(blockIntent)
                } else {
                    val newAppUsage = (currentAppUsage + BACKGROUND_CHECK_INTERVAL).coerceAtMost(limitInMillis)
                    usageMap[appInGroup.appPackage] = newAppUsage

                    val newTimeRemaining = if (group.limitEach) {
                        (limitInMillis - newAppUsage).coerceAtLeast(0L)
                    } else {
                        (group.timeRemaining - BACKGROUND_CHECK_INTERVAL).coerceAtLeast(0L)
                    }

                    val updatedUsage = usageMap.entries.map { AppUsageStat(it.key, it.value) }
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
            val groupEntity = appLimitGroupDao.getGroupContainingApp(currentApp)
            if (groupEntity != null) {
                val group = groupEntity.toDomainModel()
                val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                val limitInMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
                val appUsed = group.perAppUsage.firstOrNull { it.appPackage == currentApp }?.usedMillis ?: 0L
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
        mNotificationManager.notify(notif_ID, mBuilder.build())
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

    private fun getForegroundApp(): String? {
        mUsageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = mUsageStatsManager.queryEvents(time - 1000 * 5, time)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("BG_Service", "Service starting...")
        startForeground(notif_ID, mBuilder.build())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        unregisterReceiver(mReceiver)
        Log.i("BG_Service", "Service destroyed")
    }

    inner class ScreenStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            }
        }
    }

    companion object {
        private const val BACKGROUND_CHECK_INTERVAL = 1000L // 1 second
        private const val NOTIFICATION_UPDATE_INTERVAL = 3000L // 3 seconds

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (BackgroundChecker::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

        fun startServiceIfNotRunning(context: Context) {
            if (!isServiceRunning(context)) {
                val intent = Intent(context, BackgroundChecker::class.java)
                context.startForegroundService(intent)
            }
        }
    }

    private fun shouldBlockSettingsAccess(foregroundApp: String?): Boolean {
        val packageName = foregroundApp ?: return false
        val prefs = getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("blockSettings", false)) return false

        val lockState = readLockState(prefs)
        val lockDecision = LockPolicy.evaluateEditingLock(lockState, System.currentTimeMillis())
        if (lockDecision.shouldClearExpiredLockdown) {
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
