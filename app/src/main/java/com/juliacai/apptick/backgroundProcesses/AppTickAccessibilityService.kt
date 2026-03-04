package com.juliacai.apptick.backgroundProcesses

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Lightweight AccessibilityService that receives TYPE_WINDOW_STATE_CHANGED events
 * to instantly detect the foreground app package name. Used as the primary detection
 * source by BackgroundChecker, with UsageStatsManager as fallback when this service
 * is not enabled or data is stale.
 *
 * This service does NOT read any window content (canRetrieveWindowContent="false").
 */
class AppTickAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && !isOverlayOrSystemPackage(pkg)) {
                currentForegroundPackage = pkg
                lastUpdateTimeMillis = System.currentTimeMillis()
            }
        }
    }

    /**
     * Returns true only for packages that are passive system overlays — things that
     * appear on top of apps without the user intentionally switching away. This keeps
     * the timer running for the real foreground app underneath.
     *
     * Regular apps (including chat bubbles, floating windows, etc.) are NOT filtered
     * because the user is actively using them and they should count as foreground.
     * When those windows close, Android fires TYPE_WINDOW_STATE_CHANGED for the
     * underlying app, so the timer resumes automatically.
     */
    private fun isOverlayOrSystemPackage(pkg: String): Boolean {
        // Our own package — floating bubble, blocking overlay
        if (pkg == applicationContext.packageName) return true
        // Keyboard / IME packages — user is still in the underlying app while typing
        if (pkg.contains(".inputmethod") || pkg.contains(".keyboard") || pkg.contains(".ime")) return true
        if (pkg in KNOWN_SYSTEM_PACKAGES) return true
        // Permission dialogs — transient system prompts
        if (pkg == "com.android.permissioncontroller") return true
        return false
    }

    override fun onInterrupt() {
        // Required override — nothing needed
    }

    /**
     * Attempts to close a floating window for the given package by performing
     * GLOBAL_ACTION_BACK. Floating/freeform windows close on BACK press,
     * unlike HOME which leaves them visible on some OEMs (e.g. Honor/Huawei).
     *
     * Returns true if a BACK action was dispatched.
     */
    fun closeFloatingWindow(blockedPackage: String): Boolean {
        // Try to detect if there's a non-fullscreen window for the blocked package
        try {
            val windows = windows // AccessibilityService.getWindows()
            if (windows != null) {
                for (window in windows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        // Check if this looks like a floating window (not fullscreen)
                        val bounds = android.graphics.Rect()
                        window.getBoundsInScreen(bounds)
                        val display = resources.displayMetrics
                        val isFloating = bounds.width() < display.widthPixels ||
                                bounds.height() < display.heightPixels
                        if (isFloating) {
                            Log.i(TAG, "Found floating window (${bounds.width()}x${bounds.height()}), " +
                                    "sending BACK to close it")
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking windows, falling back to BACK action", e)
        }

        // Fallback: always send BACK if we're trying to block a floating app.
        // This handles cases where window enumeration isn't available.
        Log.i(TAG, "Sending BACK action to dismiss potential floating window for $blockedPackage")
        performGlobalAction(GLOBAL_ACTION_BACK)
        return true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
        currentForegroundPackage = null
        Log.i(TAG, "AccessibilityService destroyed")
    }

    companion object {
        private const val TAG = "AppTickA11y"

        /** Max age before accessibility data is considered stale. */
        private const val MAX_STALENESS_MS = 10_000L

        /** Live reference to the service instance for calling instance methods. */
        @Volatile
        private var instance: AppTickAccessibilityService? = null

        /**
         * Attempts to close any floating window for the given package.
         * Returns true if a BACK action was dispatched.
         */
        fun tryCloseFloatingWindow(blockedPackage: String): Boolean {
            val svc = instance ?: return false
            return svc.closeFloatingWindow(blockedPackage)
        }

        /** Keyboard packages that are passive overlays and should not replace the foreground app. */
        private val KNOWN_SYSTEM_PACKAGES = setOf(
            "com.google.android.inputmethod.latin",  // Gboard
            "com.samsung.android.honeyboard",         // Samsung keyboard
            "com.swiftkey.swiftkey",                   // SwiftKey
            "com.huawei.ohos.inputmethod",             // Huawei/HONOR keyboard
            "com.microsoft.swiftkey",                  // Microsoft SwiftKey
            "com.touchtype.swiftkey",                  // SwiftKey (alt package)
            "com.baidu.input",                         // Baidu IME
        )

        @Volatile
        var currentForegroundPackage: String? = null
            private set

        @Volatile
        var lastUpdateTimeMillis: Long = 0L
            private set

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Returns the accessibility-reported foreground package if the service
         * is running and the data is fresh (< 10 seconds old), otherwise null.
         */
        fun getForegroundPackage(): String? {
            if (!isRunning) return null
            val age = System.currentTimeMillis() - lastUpdateTimeMillis
            if (age > MAX_STALENESS_MS) return null
            return currentForegroundPackage
        }

        /**
         * Resets all companion state. Used for testing only.
         */
        @androidx.annotation.VisibleForTesting
        fun resetForTesting() {
            instance = null
            isRunning = false
            currentForegroundPackage = null
            lastUpdateTimeMillis = 0L
        }

        /**
         * Simulates the accessibility service detecting a foreground app.
         * Used for testing only.
         */
        @androidx.annotation.VisibleForTesting
        fun simulateForTesting(packageName: String?, running: Boolean) {
            isRunning = running
            currentForegroundPackage = packageName
            lastUpdateTimeMillis = if (running && packageName != null) {
                System.currentTimeMillis()
            } else {
                0L
            }
        }

        /**
         * Sets the last update time directly. Used for staleness testing only.
         */
        @androidx.annotation.VisibleForTesting
        fun setLastUpdateTimeForTesting(timeMillis: Long) {
            lastUpdateTimeMillis = timeMillis
        }

        /**
         * Checks whether this accessibility service is enabled in system settings.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedName = "${context.packageName}/" +
                AppTickAccessibilityService::class.java.canonicalName
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expectedName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
