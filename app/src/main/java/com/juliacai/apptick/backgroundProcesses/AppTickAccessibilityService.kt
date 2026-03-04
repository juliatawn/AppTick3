package com.juliacai.apptick.backgroundProcesses

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight AccessibilityService that receives TYPE_WINDOW_STATE_CHANGED events
 * to instantly detect the foreground app package name. Used as the primary detection
 * source by BackgroundChecker, with UsageStatsManager as fallback when this service
 * is not enabled or data is stale.
 *
 * Window content access is enabled only to read root node package names for
 * split-screen detection — no actual window content/text is accessed.
 */
class AppTickAccessibilityService : AccessibilityService() {

    /** Maps window IDs to package names, built from TYPE_WINDOW_STATE_CHANGED events. */
    private val windowPackageMap = ConcurrentHashMap<Int, String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank() && !isOverlayOrSystemPackage(pkg)) {
                    windowPackageMap[event.windowId] = pkg
                    currentForegroundPackage = pkg
                    lastUpdateTimeMillis = System.currentTimeMillis()
                    isCurrentAppFloating = checkIfWindowIsFloating(event.windowId)
                    // Refresh the visible-apps set so split-screen detection works
                    // even when TYPE_WINDOWS_CHANGED doesn't fire (or fires late).
                    refreshVisibleApps()
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                refreshVisibleApps()
                // Also check if focused app changed (split-screen pane switch)
                updateFocusedApp()
            }
        }
    }

    /**
     * Scans the current window list to build the set of all visible app packages.
     * Called from both TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOWS_CHANGED handlers
     * to ensure split-screen detection is always up-to-date.
     *
     * Uses windowPackageMap first (fast), then falls back to window.getRoot().packageName
     * for windows not yet in the map (requires canRetrieveWindowContent="true").
     */
    private fun refreshVisibleApps() {
        try {
            val windowList = windows ?: return
            val visible = mutableSetOf<String>()

            // Clean stale window-to-package mappings
            val currentIds = HashSet<Int>(windowList.size)
            for (w in windowList) currentIds.add(w.id)
            windowPackageMap.keys.retainAll(currentIds)

            for (window in windowList) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    // Try fast lookup first, then fall back to root node
                    var pkg = windowPackageMap[window.id]
                    if (pkg == null) {
                        try {
                            val root = window.getRoot()
                            pkg = root?.packageName?.toString()
                            root?.recycle()
                            if (pkg != null) {
                                windowPackageMap[window.id] = pkg
                            }
                        } catch (_: Exception) {}
                    }
                    if (pkg != null && !isOverlayOrSystemPackage(pkg)) {
                        visible.add(pkg)
                    }
                }
            }

            visibleAppPackages = visible
        } catch (e: Exception) {
            Log.w(TAG, "Error refreshing visible apps", e)
        }
    }

    /**
     * Checks the focused application window and updates the foreground package
     * if focus has shifted (e.g. tapping the other pane in split-screen).
     */
    private fun updateFocusedApp() {
        try {
            val windowList = windows ?: return
            for (window in windowList) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused) {
                    var pkg = windowPackageMap[window.id]
                    if (pkg == null) {
                        try {
                            val root = window.getRoot()
                            pkg = root?.packageName?.toString()
                            root?.recycle()
                            if (pkg != null) windowPackageMap[window.id] = pkg
                        } catch (_: Exception) {}
                    }
                    if (pkg != null && !isOverlayOrSystemPackage(pkg) && pkg != currentForegroundPackage) {
                        Log.d(TAG, "Split-screen focus changed to $pkg (visible: $visibleAppPackages)")
                        currentForegroundPackage = pkg
                        lastUpdateTimeMillis = System.currentTimeMillis()
                        isCurrentAppFloating = checkIfWindowIsFloating(-1)
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error updating focused app", e)
        }
    }

    /**
     * Checks if ANY visible application window is significantly smaller than
     * the screen, indicating a floating/freeform/PiP window.
     *
     * Uses an 85% area threshold to avoid false positives from fullscreen apps
     * whose window bounds exclude system bars (status bar, navigation bar).
     */
    private fun checkIfWindowIsFloating(@Suppress("UNUSED_PARAMETER") windowId: Int): Boolean {
        try {
            val windowList = windows ?: return false
            val display = resources.displayMetrics
            val screenArea = display.widthPixels.toLong() * display.heightPixels.toLong()
            for (window in windowList) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val bounds = android.graphics.Rect()
                    window.getBoundsInScreen(bounds)
                    val windowArea = bounds.width().toLong() * bounds.height().toLong()
                    // A fullscreen app with system bars excluded still covers ~90%+ of the screen.
                    // Only flag windows covering less than 85% as floating (PiP, freeform, etc.).
                    if (screenArea > 0 && windowArea < screenArea * 85 / 100) {
                        Log.d(TAG, "Floating window detected (${bounds.width()}x${bounds.height()} " +
                                "area=$windowArea vs screen ${display.widthPixels}x${display.heightPixels} " +
                                "area=$screenArea, ratio=${windowArea * 100 / screenArea}%)")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking window floating state", e)
        }
        return false
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
     * Closes a floating window by sending accessibility actions.
     *
     * For PiP windows: tries to dismiss via the accessibility node tree first,
     * which is more reliable than BACK for picture-in-picture.
     *
     * For other floating windows (freeform): sends BACK twice for robustness.
     *
     * Returns true if actions were dispatched (app was floating).
     * Returns false for fullscreen apps — no action is sent.
     */
    fun closeFloatingWindow(blockedPackage: String): Boolean {
        // Also re-check live window state in case the initial detection was stale
        val isFloatingNow = isCurrentAppFloating || checkIfWindowIsFloating(-1)
        if (!isFloatingNow) {
            Log.d(TAG, "App $blockedPackage is fullscreen, skipping close actions")
            return false
        }

        // Try to dismiss PiP/floating windows via accessibility node actions first.
        // This is more reliable than BACK for PiP windows which don't respond to BACK.
        if (tryDismissFloatingWindowNode(blockedPackage)) {
            Log.i(TAG, "Dismissed floating window for $blockedPackage via node action")
        }

        // Also send BACK as fallback for freeform/floating windows that
        // don't support node-level dismiss (e.g. Honor floating capsule).
        Log.i(TAG, "App $blockedPackage is floating, sending BACK to close it")
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_BACK)
        // NOTE: Do NOT send HOME here — the block screen activity is launched
        // immediately after this method returns, and HOME would dismiss it.
        return true
    }

    /**
     * Attempts to dismiss a floating/PiP window for the given package by finding
     * its accessibility window and performing dismiss/close actions on its node tree.
     *
     * Returns true if a dismiss action was successfully performed.
     */
    private fun tryDismissFloatingWindowNode(targetPackage: String): Boolean {
        try {
            val windowList = windows ?: return false
            val display = resources.displayMetrics
            val screenArea = display.widthPixels.toLong() * display.heightPixels.toLong()

            for (window in windowList) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

                val bounds = android.graphics.Rect()
                window.getBoundsInScreen(bounds)
                val windowArea = bounds.width().toLong() * bounds.height().toLong()

                // Only target genuinely small windows (PiP/floating)
                if (screenArea <= 0 || windowArea >= screenArea * 85 / 100) continue

                // Check if this window belongs to the target package
                val root = try { window.getRoot() } catch (_: Exception) { null } ?: continue
                val pkg = root.packageName?.toString()
                if (pkg != targetPackage) {
                    root.recycle()
                    continue
                }

                // Try ACTION_DISMISS (API 29+) on the root node
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dismissed = root.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id
                    )
                    if (dismissed) {
                        Log.d(TAG, "ACTION_DISMISS succeeded for $targetPackage PiP window")
                        root.recycle()
                        return true
                    }
                }

                // Try clicking the close button in PiP controls
                val closed = findAndClickClose(root)
                root.recycle()
                if (closed) {
                    Log.d(TAG, "Found and clicked close button for $targetPackage PiP window")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error trying to dismiss floating window node for $targetPackage", e)
        }
        return false
    }

    /**
     * Recursively searches for a close/dismiss button in the node tree and clicks it.
     * Looks for common close button patterns in PiP controls.
     */
    private fun findAndClickClose(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val isCloseButton = (desc.contains("close") || desc.contains("dismiss") ||
                text.contains("close") || text.contains("dismiss"))

        if (isCloseButton && node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickClose(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
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
        isCurrentAppFloating = false
        visibleAppPackages = emptySet()
        windowPackageMap.clear()
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

        /** Whether the current foreground app was detected as a floating/freeform window. */
        @Volatile
        var isCurrentAppFloating: Boolean = false
            private set

        /** All app packages currently visible (for split-screen tracking). */
        @Volatile
        var visibleAppPackages: Set<String> = emptySet()
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
         * Returns the set of all visible app packages if the service is running
         * and data is fresh, otherwise an empty set. In split-screen mode this
         * will contain multiple packages.
         */
        fun getVisiblePackages(): Set<String> {
            if (!isRunning) return emptySet()
            val age = System.currentTimeMillis() - lastUpdateTimeMillis
            if (age > MAX_STALENESS_MS) return emptySet()
            return visibleAppPackages
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
            isCurrentAppFloating = false
            visibleAppPackages = emptySet()
        }

        /**
         * Simulates the accessibility service detecting a foreground app.
         * Used for testing only.
         */
        @androidx.annotation.VisibleForTesting
        fun simulateForTesting(
            packageName: String?,
            running: Boolean,
            floating: Boolean = false,
            visiblePackages: Set<String> = packageName?.let { setOf(it) } ?: emptySet()
        ) {
            isRunning = running
            currentForegroundPackage = packageName
            isCurrentAppFloating = floating
            visibleAppPackages = visiblePackages
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
