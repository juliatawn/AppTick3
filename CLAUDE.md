# AppTick - Critical Implementation Notes

## Overview

AppTick blocks apps by detecting the foreground app and launching `BlockWindowActivity` on top.
Two detection sources: **AccessibilityService** (event-driven, instant) and **UsageStatsManager** (polled).
Floating/freeform windows (Honor Magic V2, PiP) require special close strategies before blocking.

---

## Critical Invariants — DO NOT BREAK

### 1. `getForegroundApp()` Must Always Cross-Validate

**File:** `BackgroundChecker.kt` — `getForegroundApp()`

Always query **both** accessibility and UsageStats, then compare timestamps.
When both have data and disagree, prefer whichever has the **more recent** timestamp.

**Why:** Accessibility is event-driven and can miss events (e.g., notification shade open/close,
split-screen transitions). If we early-return with accessibility data without checking UsageStats,
a missed event causes stale data for up to `MAX_STALENESS_MS`, resulting in 10-60 second blocking delays.

**DO NOT** add an early return like:
```kotlin
// BAD — causes slow blocking when accessibility misses events
if (accessibilityApp != null) return accessibilityApp
```

The correct pattern is:
```kotlin
// GOOD — always query both, compare timestamps
if (accessibilityApp != null && !usageStatsApp.isNullOrBlank()) {
    return if (latestForegroundEventTime > accessibilityTimestamp) {
        usageStatsApp
    } else {
        accessibilityApp
    }
}
```

### 2. `launchBlockScreen()` Must Always Call `tryCloseFloatingWindow`

**File:** `BackgroundChecker.kt` — `launchBlockScreen()`

Every call to `launchBlockScreen()` must call `tryCloseFloatingWindow()` unconditionally.
The live `checkIfWindowIsFloating()` inside `closeFloatingWindow()` self-limits — it returns
`NOT_FLOATING` immediately for fullscreen/already-closed windows.

**DO NOT** add an `isNewBlock` guard like:
```kotlin
// BAD — prevents retries when strategies return false positives
if (isNewBlock && blockedPackage != null && AppTickAccessibilityService.isRunning) {
    AppTickAccessibilityService.tryCloseFloatingWindow(blockedPackage)
}
```

The correct pattern is:
```kotlin
// GOOD — always attempt, self-limits via floating check
if (blockedPackage != null && AppTickAccessibilityService.isRunning) {
    AppTickAccessibilityService.tryCloseFloatingWindow(blockedPackage)
}
```

### 3. `closeFloatingWindow()` Strategy Order

**File:** `AppTickAccessibilityService.kt` — `closeFloatingWindow()`

The 3-strategy order is tested and confirmed working on Honor Magic V2, Samsung Galaxy S23, and Pixel Fold:

1. **`tryClickCloseInAllWindows`** — Scans non-fullscreen windows that horizontally overlap the
   floating window bounds (within 60dp above). Finds OEM title bar close buttons. Honor puts the
   close "X" in a separate `com.android.systemui` TYPE_SYSTEM window, not in the app's own window.

2. **`tryActionDismiss`** — Performs only `ACTION_DISMISS` on the floating window's
   AccessibilityWindowInfo. Does **NOT** search the app's node tree for close buttons.

3. **BACK x2** — `performGlobalAction(GLOBAL_ACTION_BACK)` twice. Fallback that works on
   Honor/EMUI floating windows. Do **NOT** use HOME — it creates a floating thumbnail instead of
   closing the window.

### 4. Strategies That Must NOT Be in the Chain

These methods exist in the codebase but must **NOT** be called from `closeFloatingWindow()`:

- **`tryDismissFloatingWindowNode`** — Searches the app's own window for close buttons.
  Wrong for Honor — the close button is in a separate systemui window. Causes false positives
  by clicking unrelated app UI elements.

- **`tryClickCloseByPosition`** — Clicks any small ImageView/Button near top-right of the
  floating window. Too aggressive — matches app UI elements that aren't close buttons.

These are dead code from previous iterations. If they are removed, that is fine.

### 5. BlockWindowActivity Must NOT Use TYPE_APPLICATION_OVERLAY

**File:** `BlockWindowActivity.kt`

The block screen must **never** use `TYPE_APPLICATION_OVERLAY` (system overlay windows) to cover
split-screen panes. Split-screen bypass is handled by BACK x2 via the accessibility service —
the same approach used for floating window close (strategy 3).

**DO NOT** add overlay code like:
```kotlin
// BAD — overlays must not be used for the block screen
val params = WindowManager.LayoutParams(
    ..., WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, ...
)
windowManager.addView(overlayView, params)
```

The correct pattern for split-screen bypass:
```kotlin
// GOOD — BACK x2 closes the blocked app from split-screen
// Block screen's OnBackPressedCallback absorbs BACKs safely
AppTickAccessibilityService.requestExitMultiWindow()
```

---

## Key Architecture Details

### Immediate Check Wakeup

`BackgroundChecker.startUnifiedLoop()` uses `withTimeoutOrNull(loopDelayMs) { wakeUpChannel.receive() }`
instead of `delay(loopDelayMs)`. When `AccessibilityService.onAccessibilityEvent()` detects a new app,
it calls `BackgroundChecker.requestImmediateCheck()` which sends to the `wakeUpChannel`, waking the
loop instantly instead of waiting up to 2 seconds.

### Floating Window Detection

A window is considered floating if its area is < 85% of screen area. This threshold handles:
- Honor freeform windows
- PiP (picture-in-picture) windows
- Samsung split-screen (each half is ~50% — both considered "floating" size-wise, handled correctly)

### Close Button Matching

`findAndClickClose()` matches: English ("close", "dismiss"), Chinese ("关闭", "退出"),
Korean ("닫기"), Unicode symbols ("✕", "×", "✖", "╳", "✗"), and view IDs ("close", "dismiss", "exit").

### MAX_STALENESS_MS = 10,000

Accessibility data older than 10 seconds is considered stale. With cross-validation, this is
sufficient — UsageStats catches any missed events within the 2-second check loop.

---

## Test Devices

- **Honor Magic V2** (VER-N49) — Primary for floating window testing (EMUI freeform)
- **Samsung Galaxy S23** (SM-S911U1) — Split-screen, PiP
- **Pixel Fold** — Standard Android behavior

All 27 instrumentation tests + unit tests must pass on all 3 devices.
