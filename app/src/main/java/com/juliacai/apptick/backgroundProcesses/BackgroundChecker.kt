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
import com.juliacai.apptick.block.BlockWindowActivity
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.R
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
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
        val allGroups = appLimitGroupDao.getAllAppLimitGroupsImmediate()
        val activeGroups = allGroups.filterNot { it.paused }

        if (activeGroups.isEmpty()) {
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

                if (com.juliacai.apptick.appLimit.AppLimitEvaluator.isLimitReached(group)) {
                    blockIntent.putExtra("app_name", appInGroup.appName)
                    blockIntent.putExtra("app_package", appInGroup.appPackage)
                    blockIntent.putExtra("group_name", group.name)
                    blockIntent.putExtra("time_spent", limitInMillis)
                    startActivity(blockIntent)
                } else {
                    val newTimeRemaining = (group.timeRemaining - BACKGROUND_CHECK_INTERVAL).coerceAtLeast(0L)
                    appLimitGroupDao.updateTimeRemaining(group.id, newTimeRemaining)
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
            val group = appLimitGroupDao.getGroupContainingApp(currentApp)
            if (group != null) {
                formatGroupNotificationText(group.timeHrLimit, group.timeMinLimit, group.timeRemaining, group.nextResetTime)
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
}
