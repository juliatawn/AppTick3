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
                    // Wake the background loop immediately so blocking is instant
                    // instead of waiting up to 2s for the next polling cycle.
                    BackgroundChecker.requestImmediateCheck()
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                refreshVisibleApps()
                // Also check if focused app changed (split-screen pane switch)
                updateFocusedApp()
                // Wake the background loop on window changes too (split-screen focus)
                BackgroundChecker.requestImmediateCheck()
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
     * Checks if any visible application window is a true floating/freeform/PiP window.
     *
     * A window is considered floating only when it is small (< 85% of screen area) AND
     * there is at least one fullscreen app window (>= 85%) behind it. This distinguishes:
     * - **Floating/PiP**: small window over a fullscreen app → returns true (close strategies apply)
     * - **Split-screen**: multiple medium windows, no fullscreen behind → returns false
     *   (block screen activity just covers the blocked pane; no close action needed)
     */
    private fun checkIfWindowIsFloating(@Suppress("UNUSED_PARAMETER") windowId: Int): Boolean {
        try {
            val windowList = windows ?: return false
            val display = resources.displayMetrics
            val screenArea = display.widthPixels.toLong() * display.heightPixels.toLong()
            if (screenArea <= 0) return false

            var hasSmallWindow = false
            var hasFullscreenWindow = false

            for (window in windowList) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val bounds = android.graphics.Rect()
                    window.getBoundsInScreen(bounds)
                    val windowArea = bounds.width().toLong() * bounds.height().toLong()
                    // A fullscreen app with system bars excluded still covers ~90%+ of the screen.
                    // Only flag windows covering less than 85% as floating (PiP, freeform, etc.).
                    if (windowArea < screenArea * 85 / 100) {
                        hasSmallWindow = true
                        Log.d(TAG, "Small window detected (${bounds.width()}x${bounds.height()} " +
                                "area=$windowArea vs screen ${display.widthPixels}x${display.heightPixels} " +
                                "area=$screenArea, ratio=${windowArea * 100 / screenArea}%)")
                    } else {
                        hasFullscreenWindow = true
                    }
                }
            }

            // True floating/PiP: small window over a fullscreen window.
            // Split-screen: multiple small windows with no fullscreen behind → not floating.
            if (hasSmallWindow && hasFullscreenWindow) {
                Log.d(TAG, "Floating window confirmed (small window over fullscreen)")
                return true
            }
            if (hasSmallWindow) {
                Log.d(TAG, "Small windows detected but no fullscreen behind — likely split-screen, not floating")
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
     * Strategy order is intentional — confirmed working on Honor Magic V2,
     * Samsung Galaxy S23, and Pixel Fold:
     *
     * 1. Cross-window close button search (OEMs put the X in a separate systemui window)
     * 2. ACTION_DISMISS on the app's floating window node (standard PiP)
     * 3. BACK x2 (last resort — confirmed working on Honor/EMUI freeform windows)
     *
     * Returns [FloatingCloseResult.NOT_FLOATING] for fullscreen apps (no action taken).
     */
    fun closeFloatingWindow(blockedPackage: String): FloatingCloseResult {
        // Always use the live window check — the cached isCurrentAppFloating flag
        // can be stale (e.g., floating window already closed, or split-screen transitioned
        // to fullscreen) and would cause BACK x2 to fire on fullscreen apps.
        val isFloatingNow = checkIfWindowIsFloating(-1)
        if (!isFloatingNow) {
            Log.d(TAG, "closeFloatingWindow: $blockedPackage is FULLSCREEN, skipping")
            return FloatingCloseResult.NOT_FLOATING
        }

        Log.d(TAG, "closeFloatingWindow: $blockedPackage is FLOATING — attempting to close")
        logAllWindows()

        // Strategy 1: Find and click the close button in nearby non-fullscreen windows.
        // On Honor/EMUI, the title bar (with the X button) lives in a separate
        // TYPE_SYSTEM window from com.android.systemui — not in the app's own window.
        if (tryClickCloseInAllWindows(blockedPackage)) {
            Log.d(TAG, "Strategy 1 SUCCESS: clicked close button for $blockedPackage")
            return FloatingCloseResult.CLOSED_INSTANTLY
        }
        Log.d(TAG, "Strategy 1 FAILED: no close button found")

        // Strategy 2: ACTION_DISMISS on the app's floating window node (works for PiP)
        if (tryActionDismiss(blockedPackage)) {
            Log.d(TAG, "Strategy 2 SUCCESS: ACTION_DISMISS for $blockedPackage")
            return FloatingCloseResult.CLOSED_INSTANTLY
        }
        Log.d(TAG, "Strategy 2 FAILED: ACTION_DISMISS didn't work")

        // Strategy 3: BACK x2 — no HOME (HOME creates a thumbnail on Honor/EMUI
        // instead of closing the floating window).
        Log.d(TAG, "Strategy 3: sending BACK x2 for $blockedPackage")
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_BACK)
        return FloatingCloseResult.NOT_FLOATING // Return NOT_FLOATING so caller shows block screen immediately
    }

    enum class FloatingCloseResult {
        NOT_FLOATING,      // App wasn't floating — no action taken
        CLOSED_INSTANTLY,  // Close button clicked — block screen can show immediately
        NEEDS_DELAY        // BACK/HOME sent — need delay before block screen
    }

    /**
     * Logs all current accessibility windows for debugging OEM floating window layouts.
     * Shows window type, package, bounds, and screen area percentage.
     */
    private fun logAllWindows() {
        try {
            val windowList = windows ?: return
            val display = resources.displayMetrics
            val screenArea = display.widthPixels.toLong() * display.heightPixels.toLong()
            Log.d(TAG, "=== All windows (${windowList.size}) ===")
            for (window in windowList) {
                val bounds = android.graphics.Rect()
                window.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()
                val pct = if (screenArea > 0) area * 100 / screenArea else 0
                val typeName = when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
                    AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "IME"
                    AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "A11Y_OVERLAY"
                    else -> "TYPE_${window.type}"
                }
                val pkg = try {
                    window.getRoot()?.let { r -> r.packageName?.toString().also { r.recycle() } }
                } catch (_: Exception) { null }
                Log.d(TAG, "  $typeName — pkg=$pkg bounds=$bounds area=$pct%")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error logging windows", e)
        }
    }

    /**
     * Searches ALL window types (system, app, overlay) for close buttons near the
     * floating window. OEMs like Honor draw the floating window title bar in a
     * separate TYPE_SYSTEM window from com.android.systemui.
     *
     * Only searches windows that:
     * 1. Are not fullscreen (< 85% screen area) — skips status bar, nav bar, launcher
     * 2. Horizontally overlap the floating window bounds
     * 3. Are within ~60dp above the floating window (where the title bar would be)
     */
    private fun tryClickCloseInAllWindows(targetPackage: String): Boolean {
        try {
            val windowList = windows ?: return false
            val display = resources.displayMetrics
            val screenArea = display.widthPixels.toLong() * display.heightPixels.toLong()
            val density = display.density
            val titleBarMargin = (60 * density).toInt() // 60dp above floating window

            // First, find the floating app window to get its bounds
            var floatingBounds: android.graphics.Rect? = null
            for (window in windowList) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val bounds = android.graphics.Rect()
                window.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (screenArea > 0 && area < screenArea * 85 / 100) {
                    val root = try { window.getRoot() } catch (_: Exception) { null }
                    val pkg = root?.packageName?.toString()
                    root?.recycle()
                    if (pkg == targetPackage) {
                        floatingBounds = bounds
                        break
                    }
                }
            }

            if (floatingBounds == null) {
                Log.d(TAG, "tryClickCloseInAllWindows: no floating window found for $targetPackage")
                return false
            }

            // Now search ALL windows for close buttons
            for (window in windowList) {
                val bounds = android.graphics.Rect()
                window.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()

                // Skip fullscreen windows (launcher, status bar, nav bar)
                if (screenArea > 0 && area >= screenArea * 85 / 100) continue

                // Must horizontally overlap the floating window
                if (bounds.right < floatingBounds.left || bounds.left > floatingBounds.right) continue

                // Must be within title bar range (at or above the floating window top)
                if (bounds.top > floatingBounds.top + titleBarMargin) continue

                val root = try { window.getRoot() } catch (_: Exception) { null } ?: continue
                val typeName = when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> "APP"
                    AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
                    else -> "TYPE_${window.type}"
                }
                Log.d(TAG, "Searching $typeName window at $bounds for close buttons")
                val closed = findAndClickClose(root)
                root.recycle()
                if (closed) {
                    Log.d(TAG, "Found close button in $typeName window at $bounds")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in tryClickCloseInAllWindows for $targetPackage", e)
        }
        return false
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
     * Tries ACTION_DISMISS on the floating app window (works for standard PiP).
     * Does NOT search the app's node tree for close buttons — that can cause
     * false positives by clicking unrelated app UI elements.
     */
    private fun tryActionDismiss(targetPackage: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        try {
            val windowList = windows ?: return false
            val display = resources.displayMetrics
            val screenArea = display.widthPixels.toLong() * display.heightPixels.toLong()

            for (window in windowList) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val bounds = android.graphics.Rect()
                window.getBoundsInScreen(bounds)
                val windowArea = bounds.width().toLong() * bounds.height().toLong()
                if (screenArea <= 0 || windowArea >= screenArea * 85 / 100) continue

                val root = try { window.getRoot() } catch (_: Exception) { null } ?: continue
                val pkg = root.packageName?.toString()
                if (pkg != targetPackage) {
                    root.recycle()
                    continue
                }

                val dismissed = root.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id
                )
                root.recycle()
                if (dismissed) return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error trying ACTION_DISMISS for $targetPackage", e)
        }
        return false
    }

    /**
     * Recursively searches for a close/dismiss button in the node tree and clicks it.
     * Checks English, Chinese, Korean close terms, Unicode symbols, and view IDs
     * so it works universally across Samsung, Honor/EMUI, and other OEM devices.
     */
    private fun findAndClickClose(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isCloseButton =
            // English
            desc.contains("close") || desc.contains("dismiss") ||
            text.contains("close") || text.contains("dismiss") ||
            // Chinese: 关闭 (close), 退出 (exit/quit)
            desc.contains("关闭") || text.contains("关闭") ||
            desc.contains("退出") || text.contains("退出") ||
            // Korean: 닫기 (close)
            desc.contains("닫기") || text.contains("닫기") ||
            // Unicode close/X symbols (works regardless of locale)
            CLOSE_SYMBOLS.any { sym -> desc.contains(sym) || text.contains(sym) } ||
            // View ID patterns (e.g. "btn_close", "iv_dismiss")
            viewId.contains("close") || viewId.contains("dismiss") || viewId.contains("exit")

        if (isCloseButton && node.isClickable) {
            Log.d(TAG, "Clicking close button: desc='$desc' text='$text' viewId='$viewId'")
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

    /**
     * Last-resort close strategy: find small clickable buttons near the top-right
     * corner of the floating window. Many OEM floating windows (Honor/EMUI, Samsung
     * DeX) have an unlabeled "X" icon (ImageView with no text/contentDescription).
     */
    private fun tryClickCloseByPosition(targetPackage: String): Boolean {
        try {
            val windowList = windows ?: return false
            val display = resources.displayMetrics
            val screenArea = display.widthPixels.toLong() * display.heightPixels.toLong()

            for (window in windowList) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

                val windowBounds = android.graphics.Rect()
                window.getBoundsInScreen(windowBounds)
                val windowArea = windowBounds.width().toLong() * windowBounds.height().toLong()

                if (screenArea <= 0 || windowArea >= screenArea * 85 / 100) continue

                val root = try { window.getRoot() } catch (_: Exception) { null } ?: continue
                val pkg = root.packageName?.toString()
                if (pkg != targetPackage) {
                    root.recycle()
                    continue
                }

                val clicked = findClickableNearTopRight(root, windowBounds)
                root.recycle()
                if (clicked) return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in position-based close for $targetPackage", e)
        }
        return false
    }

    /**
     * Recursively searches for a small clickable button/image near the top-right
     * corner of the given window bounds. Returns true if one was found and clicked.
     */
    private fun findClickableNearTopRight(
        node: AccessibilityNodeInfo,
        windowBounds: android.graphics.Rect
    ): Boolean {
        if (node.isClickable) {
            val className = node.className?.toString() ?: ""
            val isButtonLike = className.contains("Button") ||
                className.contains("ImageView") || className.contains("Image")

            if (isButtonLike) {
                val nodeBounds = android.graphics.Rect()
                node.getBoundsInScreen(nodeBounds)
                val windowW = windowBounds.width()
                val windowH = windowBounds.height()

                // Small relative to the window (< 25% each dimension)
                val isSmall = nodeBounds.width() > 0 && nodeBounds.height() > 0 &&
                    nodeBounds.width() < windowW / 4 && nodeBounds.height() < windowH / 4

                // Near top-right corner (within right 20% and top ~16%)
                val isNearTopRight = nodeBounds.right >= windowBounds.right - windowW / 5 &&
                    nodeBounds.top <= windowBounds.top + windowH / 6

                if (isSmall && isNearTopRight) {
                    Log.d(TAG, "Position-based close click at $nodeBounds " +
                            "(window=$windowBounds, class=$className)")
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findClickableNearTopRight(child, windowBounds)) {
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

        /** Unicode symbols commonly used as close/dismiss buttons across locales. */
        private val CLOSE_SYMBOLS = setOf("✕", "×", "✖", "╳", "✗", "❌")

        /** Live reference to the service instance for calling instance methods. */
        @Volatile
        private var instance: AppTickAccessibilityService? = null

        /**
         * Attempts to close any floating window for the given package.
         * Returns [FloatingCloseResult] indicating what happened.
         */
        fun tryCloseFloatingWindow(blockedPackage: String): FloatingCloseResult {
            val svc = instance ?: return FloatingCloseResult.NOT_FLOATING
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
