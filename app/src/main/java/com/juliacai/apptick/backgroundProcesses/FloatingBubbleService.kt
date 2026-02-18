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
 * Lightweight overlay service that draws a small, semi-transparent floating
 * bubble showing the active profile's time remaining while the user is inside
 * a time-limited app.
 *
 * The bubble is draggable. When the user begins dragging, a dismiss target (✕)
 * appears at the bottom of the screen. Dropping the bubble onto it dismisses
 * the bubble and shows a Toast instructing the user to re-show via the
 * AppTick notification action.
 */
class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var dismissTarget: View? = null
    private var timeTextView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var dismissParams: WindowManager.LayoutParams? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var timeRemainingMillis: Long = 0
    private var activeAppPackage: String? = null

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
                timeRemainingMillis = intent.getLongExtra(EXTRA_TIME_MILLIS, 0)
                val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE)
                if (bubbleView == null) {
                    val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
                    if (prefs.getBoolean(PREF_BUBBLE_DISMISSED, false)) {
                        return START_NOT_STICKY
                    }
                    activeAppPackage = appPackage
                    createBubble()
                    if (bubbleView == null) {
                        Log.e("FloatingBubbleService", "Failed to create bubble view on update")
                        return START_NOT_STICKY
                    }
                    startForegroundWithNotification()
                    startUpdatingTime()
                }
                maybeApplyPositionForApp(appPackage)
                updateBubbleText(text)
            }
            ACTION_HIDE -> hideBubble()
            ACTION_SHOW -> {
                val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean(PREF_BUBBLE_DISMISSED, false).apply()
                val text = intent.getStringExtra(EXTRA_BUBBLE_TEXT)
                if (text != null) {
                    timeRemainingMillis = intent.getLongExtra(EXTRA_TIME_MILLIS, 0)
                    val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE)
                    if (bubbleView == null) {
                        activeAppPackage = appPackage
                        createBubble()
                        if (bubbleView == null) {
                            Log.e("FloatingBubbleService", "Failed to create bubble view on show")
                            return START_NOT_STICKY
                        }
                        startForegroundWithNotification()
                        startUpdatingTime()
                    }
                    maybeApplyPositionForApp(appPackage)
                    updateBubbleText(text)
                }
            }
            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun startUpdatingTime() {
        serviceScope.launch {
            while (true) {
                delay(1000)
                timeRemainingMillis -= 1000
                if (timeRemainingMillis < 0) timeRemainingMillis = 0

                val timeString = formatBubbleCountdown(timeRemainingMillis)

                launch(Dispatchers.Main) {
                    updateBubbleText(timeString)
                }
            }
        }
    }

    private fun formatBubbleCountdown(timeRemainingMillis: Long): String {
        val safeMillis = timeRemainingMillis.coerceAtLeast(0L)
        val totalMinutes = if (safeMillis <= 0L) 0L else maxOf(1L, safeMillis / 60_000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return String.format("%02d:%02d", hours, minutes)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "FLOATING_BUBBLE_CHANNEL",
                "Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "FLOATING_BUBBLE_CHANNEL")
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

    // ── Dismiss target (✕ circle shown at bottom while dragging) ──────────

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
        container.alpha = 0f // start invisible
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

    /** Whether the bubble centre is hovering over the dismiss target. */
    private fun isOverDismissTarget(bubbleScreenX: Float, bubbleScreenY: Float): Boolean {
        val target = dismissTarget ?: return false
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val cx = loc[0] + target.width / 2f
        val cy = loc[1] + target.height / 2f
        val radius = dp(40f)  // generous hit area
        val dx = bubbleScreenX - cx
        val dy = bubbleScreenY - cy
        return dx * dx + dy * dy <= radius * radius
    }

    // ── Bubble view ──────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        createDismissTarget()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            alpha = 1f

            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                // 70% opacity background as requested
                setColor(android.graphics.Color.argb(178, 20, 20, 20))
                setStroke(dp(1f), android.graphics.Color.argb(220, 255, 255, 255))
            }
            background = bg
        }

        timeTextView = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            text = ""
            maxLines = 1
            setSingleLine(true)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        container.addView(timeTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        bubbleView = container

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Use START-based coordinates so drag direction matches finger movement consistently.
            gravity = Gravity.TOP or Gravity.START
            val defaultX = (resources.displayMetrics.widthPixels - dp(120f)).coerceAtLeast(dp(16f))
            val defaultY = dp(110f)
            val initialPosition = loadSavedBubblePosition(activeAppPackage, defaultX, defaultY)
            x = initialPosition.first
            y = initialPosition.second
            alpha = 1f
        }

        // ── Drag + drop-to-dismiss ───────────────────────────────────────
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams!!.x
                    initialY = bubbleParams!!.y
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
                        bubbleParams!!.x = initialX + dx
                        bubbleParams!!.y = initialY + dy
                        windowManager?.updateViewLayout(bubbleView, bubbleParams)

                        // Highlight dismiss target when hovering over it
                        val hovering = isOverDismissTarget(event.rawX, event.rawY)
                        dismissTarget?.scaleX = if (hovering) 1.3f else 1f
                        dismissTarget?.scaleY = if (hovering) 1.3f else 1f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging && isOverDismissTarget(event.rawX, event.rawY)) {
                        dismissBubble()
                    } else {
                        if (isDragging) {
                            saveBubblePosition(bubbleParams!!.x, bubbleParams!!.y, activeAppPackage)
                        }
                        hideDismissTarget()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            Log.e("FloatingBubbleService", "Unable to add floating bubble view", e)
            bubbleView = null
            timeTextView = null
        }
    }

    private fun updateBubbleText(text: String) {
        timeTextView?.text = text
    }

    private fun maybeApplyPositionForApp(appPackage: String?) {
        if (bubbleView == null || bubbleParams == null || activeAppPackage == appPackage) return
        activeAppPackage = appPackage
        val currentX = bubbleParams!!.x
        val currentY = bubbleParams!!.y
        val savedPosition = loadSavedBubblePosition(appPackage, currentX, currentY)
        bubbleParams!!.x = savedPosition.first
        bubbleParams!!.y = savedPosition.second
        try {
            windowManager?.updateViewLayout(bubbleView, bubbleParams)
        } catch (_: Exception) {}
    }

    private fun loadSavedBubblePosition(appPackage: String?, defaultX: Int, defaultY: Int): Pair<Int, Int> {
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val appXKey = appPackage?.let { bubblePositionXKey(it) }
        val appYKey = appPackage?.let { bubblePositionYKey(it) }
        val hasPerAppPosition = appXKey != null && appYKey != null &&
                prefs.contains(appXKey) && prefs.contains(appYKey)
        return if (hasPerAppPosition) {
            prefs.getInt(appXKey, defaultX) to prefs.getInt(appYKey, defaultY)
        } else {
            prefs.getInt(PREF_BUBBLE_POS_X, defaultX) to prefs.getInt(PREF_BUBBLE_POS_Y, defaultY)
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

    private fun hideBubble() {
        removeBubbleView()
        removeDismissTarget()
        stopSelf()
    }

    private fun dismissBubble() {
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_BUBBLE_DISMISSED, true).apply()

        removeBubbleView()
        removeDismissTarget()
        Toast.makeText(
            this,
            "Tap AppTick notification to show bubble again",
            Toast.LENGTH_LONG
        ).show()
        stopSelf()
    }

    private fun removeBubbleView() {
        try {
            if (bubbleView != null) windowManager?.removeView(bubbleView)
        } catch (_: Exception) {}
        bubbleView = null
        timeTextView = null
    }

    override fun onDestroy() {
        serviceJob.cancel()
        removeBubbleView()
        removeDismissTarget()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ACTION = "bubble_action"
        const val EXTRA_BUBBLE_TEXT = "bubble_text"
        const val EXTRA_TIME_MILLIS = "time_millis"
        const val EXTRA_APP_PACKAGE = "app_package"
        const val ACTION_UPDATE = "update"
        const val ACTION_HIDE = "hide"
        const val ACTION_SHOW = "show"
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
    }
}
