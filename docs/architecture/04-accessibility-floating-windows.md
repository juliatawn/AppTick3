# 5. Accessibility Service & Floating Windows

## `AppTickAccessibilityService.kt`

**File:** `backgroundProcesses/AppTickAccessibilityService.kt` (~740 lines)

Lightweight AccessibilityService for instant foreground app detection and floating window management.

**Service lifecycle:**

| Lifecycle Event | Actions Taken |
|-----------------|---------------|
| `onServiceConnected()` | Sets `instance`/`isRunning`, performs initial window scan (`refreshVisibleApps` + `updateFocusedApp`) to immediately populate foreground/visible state, wakes BackgroundChecker via `requestImmediateCheck()` |
| `onDestroy()` | Clears all companion state (package, timestamp, floating flag, visible set, window map), resets `lastUpdateTimeMillis` to 0 so `getForegroundPackage()` returns null instantly, wakes BackgroundChecker so it falls back to UsageStats within one loop cycle |

**Why the initial window scan matters:** Without it, there's a gap between toggle-on and the first `TYPE_WINDOW_STATE_CHANGED` event (which only fires on the *next* app switch). If the user is already sitting in a blocked app, accessibility would contribute nothing until they switch away and back. The scan ensures BackgroundChecker has accurate data from the very first iteration.

**Why waking on destroy matters:** Without it, BackgroundChecker could run for up to 2 seconds with `isRunning=false` and stale `lastForegroundApp`, causing the Floating Time Left bubble to show outdated data. The wakeup forces an immediate UsageStats-based refresh.

**Event handling:**
- `TYPE_WINDOW_STATE_CHANGED` → updates `currentForegroundPackage`, `lastUpdateTimeMillis`, `isCurrentAppFloating`, refreshes visible apps, wakes BackgroundChecker
- `TYPE_WINDOWS_CHANGED` → refreshes visible apps, updates focused app (split-screen pane switch)

**Companion object (static state):**

| Field | Type | Purpose |
|-------|------|---------|
| `currentForegroundPackage` | String? | Last detected foreground package |
| `lastUpdateTimeMillis` | Long | Timestamp of last detection |
| `isRunning` | Boolean | Service connected flag |
| `isCurrentAppFloating` | Boolean | Whether current app is in floating/PiP window |
| `visibleAppPackages` | Set\<String\> | All visible app packages (split-screen) |
| `instance` | AppTickAccessibilityService? | Live reference for calling instance methods |
| `MAX_STALENESS_MS` | 10,000L | Data expiry threshold |

**Key static methods:**
- `getForegroundPackage()` → returns package if running + fresh, else null
- `getVisiblePackages()` → returns visible set if running + fresh, else empty
- `tryCloseFloatingWindow(pkg)` → delegates to instance.closeFloatingWindow()
- `requestExitMultiWindow()` → performs GLOBAL_ACTION_BACK x2 to close the blocked app from split-screen (similar to floating window close strategy 3)
- `isAccessibilityServiceEnabled(ctx)` → checks system Settings

**Floating window detection (`checkIfWindowIsFloating`):**
- Returns true when: (a) small window (< 85%) over a fullscreen window (>= 85%), OR (b) exactly one small window with no other app windows (floating over home screen — launcher may use a non-TYPE_APPLICATION window on some OEMs)
- Returns false for split-screen: multiple small/medium windows with no fullscreen behind
- Handles Honor freeform, PiP; correctly skips close strategies for split-screen

**Floating window close strategies (CLAUDE.md #3):**

```
closeFloatingWindow(blockedPackage) → FloatingCloseResult
  1. Live re-check: checkIfWindowIsFloating(-1)
     (uses only the live window check — not the cached isCurrentAppFloating flag)
     → If NOT floating (fullscreen or split-screen): return NOT_FLOATING (no action)

  2. Strategy 1: tryClickCloseInAllWindows(pkg)
     → Searches non-fullscreen windows overlapping floating window bounds
     → Within 60dp above the floating window top (title bar region)
     → Calls findAndClickClose(root) on each candidate
     → Success: return CLOSED_INSTANTLY

  3. Strategy 2: tryActionDismiss(pkg)
     → Finds floating window for target package
     → Performs ACTION_DISMISS on root node (API 29+)
     → Success: return CLOSED_INSTANTLY

  4. Strategy 3: BACK × 2
     → performGlobalAction(GLOBAL_ACTION_BACK) twice
     → Returns NOT_FLOATING (block screen shows immediately)
```

**Close button matching (`findAndClickClose`):**
- English: "close", "dismiss"
- Chinese: "关闭", "退出"
- Korean: "닫기"
- Unicode: "✕", "×", "✖", "╳", "✗", "❌"
- View IDs: contains "close", "dismiss", "exit"
- Recursively searches node tree

**Dead code (CLAUDE.md #4) — must NOT be in closeFloatingWindow() chain:**
- `tryDismissFloatingWindowNode()` — searches app's own window for close buttons (wrong for Honor)
- `tryClickCloseByPosition()` — clicks small buttons near top-right (too aggressive)

**Overlay/system package filtering (`isOverlayOrSystemPackage`):**
- Own package (AppTick)
- Keyboard/IME packages (Gboard, Samsung, SwiftKey, Huawei, Baidu, etc.)
- `KNOWN_SYSTEM_PACKAGES` set
- Permission controller
