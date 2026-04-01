package com.juliacai.apptick.backgroundProcesses

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lightweight overlay service that draws semi-transparent floating bubbles
 * showing time remaining for time-limited apps.
 *
 * In split-screen mode, each visible limited app gets its own bubble so the
 * user can see both countdowns simultaneously. Both timers decrement in real
 * time. Each bubble is independently draggable and remembers its position
 * per app.
 *
 * Dropping any bubble onto the dismiss target dismisses all bubbles.
 */
class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var dismissTarget: View? = null
    private var dismissParams: WindowManager.LayoutParams? = null
    private var foregroundStarted = false
    private var timerStarted = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /** Per-app bubble state. */
    private class BubbleEntry {
        var view: View? = null
        var textView: TextView? = null
        var params: WindowManager.LayoutParams? = null
        var timeRemainingMillis: Long = 0
    }

    /** All active bubbles, keyed by app package name. */
    private val bubbles = LinkedHashMap<String, BubbleEntry>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION)
        when (action) {
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_BUBBLE_TEXT) ?: return START_NOT_STICKY
                val timeMillis = intent.getLongExtra(EXTRA_TIME_MILLIS, 0)
                val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE)
                    ?: return START_NOT_STICKY

                val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
                if (prefs.getBoolean(PREF_BUBBLE_DISMISSED, false)) {
                    return START_NOT_STICKY
                }

                val entry = bubbles.getOrPut(appPackage) { BubbleEntry() }
                entry.timeRemainingMillis = timeMillis

                if (entry.view == null) {
                    createBubble(appPackage, entry)
                    if (entry.view == null) {
                        bubbles.remove(appPackage)
                        Log.e(TAG, "Failed to create bubble view for $appPackage")
                        return START_NOT_STICKY
                    }
                }
                ensureForegroundAndTimer()
                entry.textView?.text = text
            }
            ACTION_HIDE -> hideAllBubbles()
            ACTION_SHOW -> {
                val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean(PREF_BUBBLE_DISMISSED, false).apply()
                val text = intent.getStringExtra(EXTRA_BUBBLE_TEXT)
                val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE)
                if (text != null && appPackage != null) {
                    val timeMillis = intent.getLongExtra(EXTRA_TIME_MILLIS, 0)
                    val entry = bubbles.getOrPut(appPackage) { BubbleEntry() }
                    entry.timeRemainingMillis = timeMillis
                    if (entry.view == null) {
                        createBubble(appPackage, entry)
                        if (entry.view == null) {
                            bubbles.remove(appPackage)
                            return START_NOT_STICKY
                        }
                    }
                    ensureForegroundAndTimer()
                    entry.textView?.text = text
                }
            }
            ACTION_REMOVE_APP -> {
                val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE) ?: return START_NOT_STICKY
                removeBubbleForApp(appPackage)
                if (bubbles.isEmpty()) {
                    hideAllBubbles()
                }
            }
            else -> stopSelf()
        }
        return START_STICKY
    }

    // ── Timer ─────────────────────────────────────────────────────────────

    private fun ensureForegroundAndTimer() {
        if (!foregroundStarted) {
            startForegroundWithNotification()
            foregroundStarted = true
        }
        if (!timerStarted) {
            timerStarted = true
            startUpdatingTime()
        }
    }

    private fun startUpdatingTime() {
        serviceScope.launch {
            while (true) {
                delay(1000)
                // Snapshot the entries to avoid ConcurrentModificationException
                val entries = synchronized(bubbles) { bubbles.values.toList() }
                for (entry in entries) {
                    entry.timeRemainingMillis -= 1000
                    if (entry.timeRemainingMillis < 0) entry.timeRemainingMillis = 0
                    val timeString = formatBubbleCountdown(entry.timeRemainingMillis)
                    launch(Dispatchers.Main) {
                        entry.textView?.text = timeString
                    }
                }
            }
        }
    }

    private fun formatBubbleCountdown(timeRemainingMillis: Long): String {
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

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            // Delete legacy channel so badge/importance changes take effect on update
            nm.deleteNotificationChannel("FLOATING_BUBBLE_CHANNEL")

            val channel = NotificationChannel(
                "FLOATING_BUBBLE_CHANNEL_v2",
                "Floating Bubble",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "FLOATING_BUBBLE_CHANNEL_v2")
            .setContentTitle("AppTick")
            .setContentText("Floating bubble is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
        ).toInt()

    /** Returns the status bar height in pixels so the bubble stays below it. */
    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24f)
    }

    // ── Dismiss target ───────────────────────────────────────────────────

    private fun createDismissTarget() {
        if (dismissTarget != null) return

        val size = dp(48f)
        val container = FrameLayout(this).apply {
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.argb(200, 60, 60, 60))
            }
            background = bg
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(android.graphics.Color.WHITE)
            contentDescription = "Dismiss zone"
        }
        val iconSize = dp(24f)
        val iconParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
            gravity = Gravity.CENTER
        }
        container.addView(icon, iconParams)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        dismissParams = WindowManager.LayoutParams(
            size, size,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(48f)
        }

        dismissTarget = container
        container.alpha = 0f
        try { windowManager?.addView(container, dismissParams) } catch (_: Exception) {}
    }

    private fun showDismissTarget() {
        dismissTarget?.animate()?.alpha(1f)?.setDuration(150)?.start()
    }

    private fun hideDismissTarget() {
        dismissTarget?.animate()?.alpha(0f)?.setDuration(100)?.start()
    }

    private fun removeDismissTarget() {
        try {
            if (dismissTarget != null) windowManager?.removeView(dismissTarget)
        } catch (_: Exception) {}
        dismissTarget = null
    }

    private fun isOverDismissTarget(bubbleScreenX: Float, bubbleScreenY: Float): Boolean {
        val target = dismissTarget ?: return false
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val cx = loc[0] + target.width / 2f
        val cy = loc[1] + target.height / 2f
        val radius = dp(40f)
        val dx = bubbleScreenX - cx
        val dy = bubbleScreenY - cy
        return dx * dx + dy * dy <= radius * radius
    }

    // ── Bubble view creation ─────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble(appPackage: String, entry: BubbleEntry) {
        createDismissTarget()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            alpha = 1f

            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(android.graphics.Color.argb(178, 20, 20, 20))
                setStroke(dp(1f), android.graphics.Color.argb(220, 255, 255, 255))
            }
            background = bg
        }

        val textView = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            text = ""
            maxLines = 1
            setSingleLine(true)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        container.addView(textView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Stagger default Y position for multiple bubbles so they don't overlap
        val bubbleIndex = bubbles.keys.indexOf(appPackage).coerceAtLeast(0)
        val staggerOffset = bubbleIndex * dp(55f)
        val defaultX = 0
        val defaultY = resources.displayMetrics.heightPixels / 2 + staggerOffset
        val (loadedX, loadedY) = loadSavedBubblePosition(appPackage, defaultX, defaultY)

        // If another bubble already occupies this exact position, apply stagger offset
        val posX = loadedX
        val posY = if (bubbleIndex > 0 && bubbles.values.any { entry ->
                entry.params?.let { it.x == loadedX && it.y == loadedY } == true
            }) {
            loadedY + staggerOffset
        } else {
            loadedY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
            alpha = 1f
        }

        entry.view = container
        entry.textView = textView
        entry.params = params

        // ── Drag + drop-to-dismiss ───────────────────────────────────────
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            val p = entry.params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!isDragging && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
                        isDragging = true
                        showDismissTarget()
                    }
                    if (isDragging) {
                        p.x = initialX + dx
                        p.y = (initialY + dy).coerceAtLeast(statusBarHeight())
                        try { windowManager?.updateViewLayout(entry.view, p) } catch (_: Exception) {}

                        val hovering = isOverDismissTarget(event.rawX, event.rawY)
                        dismissTarget?.scaleX = if (hovering) 1.3f else 1f
                        dismissTarget?.scaleY = if (hovering) 1.3f else 1f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging && isOverDismissTarget(event.rawX, event.rawY)) {
                        dismissAllBubbles()
                    } else {
                        if (isDragging) {
                            saveBubblePosition(p.x, p.y, appPackage)
                        }
                        hideDismissTarget()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to add floating bubble view for $appPackage", e)
            entry.view = null
            entry.textView = null
            entry.params = null
        }
    }

    // ── Position persistence ─────────────────────────────────────────────

    private fun loadSavedBubblePosition(appPackage: String?, defaultX: Int, defaultY: Int): Pair<Int, Int> {
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val appXKey = appPackage?.let { bubblePositionXKey(it) }
        val appYKey = appPackage?.let { bubblePositionYKey(it) }
        val hasPerAppPosition = appXKey != null && appYKey != null &&
                prefs.contains(appXKey) && prefs.contains(appYKey)
        val minY = statusBarHeight()
        return if (hasPerAppPosition) {
            prefs.getInt(appXKey, defaultX) to prefs.getInt(appYKey, defaultY).coerceAtLeast(minY)
        } else {
            prefs.getInt(PREF_BUBBLE_POS_X, defaultX) to prefs.getInt(PREF_BUBBLE_POS_Y, defaultY).coerceAtLeast(minY)
        }
    }

    private fun saveBubblePosition(x: Int, y: Int, appPackage: String?) {
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt(PREF_BUBBLE_POS_X, x)
            .putInt(PREF_BUBBLE_POS_Y, y)
        if (!appPackage.isNullOrBlank()) {
            editor.putInt(bubblePositionXKey(appPackage), x)
            editor.putInt(bubblePositionYKey(appPackage), y)
        }
        editor.apply()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /** Remove a single app's bubble view from the window. */
    private fun removeBubbleForApp(appPackage: String) {
        val entry = bubbles.remove(appPackage) ?: return
        try {
            if (entry.view != null) windowManager?.removeView(entry.view)
        } catch (_: Exception) {}
    }

    /** Remove all bubble views from the window. */
    private fun removeAllBubbleViews() {
        for ((_, entry) in bubbles) {
            try {
                if (entry.view != null) windowManager?.removeView(entry.view)
            } catch (_: Exception) {}
        }
        bubbles.clear()
    }

    private fun hideAllBubbles() {
        removeAllBubbleViews()
        removeDismissTarget()
        foregroundStarted = false
        timerStarted = false
        stopSelf()
    }

    private fun dismissAllBubbles() {
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_BUBBLE_DISMISSED, true).apply()

        removeAllBubbleViews()
        removeDismissTarget()
        Toast.makeText(
            this,
            "Tap AppTick notification to show bubble again",
            Toast.LENGTH_LONG
        ).show()
        foregroundStarted = false
        timerStarted = false
        stopSelf()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        removeAllBubbleViews()
        removeDismissTarget()
        foregroundStarted = false
        timerStarted = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingBubbleService"
        const val EXTRA_ACTION = "bubble_action"
        const val EXTRA_BUBBLE_TEXT = "bubble_text"
        const val EXTRA_TIME_MILLIS = "time_millis"
        const val EXTRA_APP_PACKAGE = "app_package"
        const val ACTION_UPDATE = "update"
        const val ACTION_HIDE = "hide"
        const val ACTION_SHOW = "show"
        const val ACTION_REMOVE_APP = "remove_app"
        const val PREF_BUBBLE_DISMISSED = "bubbleDismissed"
        const val PREF_FLOATING_BUBBLE_ENABLED = "floatingBubbleEnabled"
        const val PREF_BUBBLE_POS_X = "floatingBubblePosX"
        const val PREF_BUBBLE_POS_Y = "floatingBubblePosY"

        private fun bubblePositionXKey(appPackage: String): String = "floatingBubblePosX_$appPackage"
        private fun bubblePositionYKey(appPackage: String): String = "floatingBubblePosY_$appPackage"

        fun updateIntent(
            context: Context,
            text: String,
            timeMillis: Long,
            appPackage: String?
        ): Intent =
            Intent(context, FloatingBubbleService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_UPDATE)
                putExtra(EXTRA_BUBBLE_TEXT, text)
                putExtra(EXTRA_TIME_MILLIS, timeMillis)
                putExtra(EXTRA_APP_PACKAGE, appPackage)
            }

        fun hideIntent(context: Context): Intent =
            Intent(context, FloatingBubbleService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_HIDE)
            }

        fun showIntent(
            context: Context,
            text: String,
            timeMillis: Long,
            appPackage: String?
        ): Intent =
            Intent(context, FloatingBubbleService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_SHOW)
                putExtra(EXTRA_BUBBLE_TEXT, text)
                putExtra(EXTRA_TIME_MILLIS, timeMillis)
                putExtra(EXTRA_APP_PACKAGE, appPackage)
            }

        fun removeAppIntent(context: Context, appPackage: String): Intent =
            Intent(context, FloatingBubbleService::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_REMOVE_APP)
                putExtra(EXTRA_APP_PACKAGE, appPackage)
            }
    }
}
