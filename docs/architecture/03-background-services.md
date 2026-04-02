# 4. Background Services

## `BackgroundChecker.kt` — Foreground service

**File:** `backgroundProcesses/BackgroundChecker.kt` (~1287 lines)

The heart of the app. Runs as a foreground service with a single unified coroutine loop.

**Key methods:**

| Method | Purpose |
|--------|---------|
| `startUnifiedLoop()` | Main loop: poll/wake, detect app, check limits, update notification/bubble |
| `getForegroundApp()` | Queries both accessibility + UsageStats, picks newest timestamp. **See CLAUDE.md #1** |
| `checkAppLimits(app, elapsed)` | Iterates groups, handles resets (with missed-interval catch-up and midnight carryover guard), decrements time, triggers blocking |
| `launchBlockScreen(pkg)` | Launches BlockWindowActivity + calls tryCloseFloatingWindow. **See CLAUDE.md #2** |
| `computeElapsedDelta()` | Computes real elapsed time since last check, capped at MAX_ELAPSED |
| `pickNotificationGroup(groups, app)` | Selects group with lowest remaining time for notification display |
| `formatBubbleCountdown(ms)` | Formats MM:SS (under 1min) or HH:MM (over 1min) |
| `updateFloatingBubble(app, fallback, visible)` | Manages per-app floating bubbles in split-screen |
| `shouldTrackUsageNow()` | Returns false if screen off or device locked |

**Screen-off protection (two layers):**
1. Main loop checks `shouldTrackUsageNow()` and skips `checkAppLimits()` entirely when screen is off/locked
2. Inside `checkAppLimits()`, the timer decrement section checks `isScreenOn` as defense-in-depth
- `ScreenStateReceiver` resets `lastCheckElapsed` on both screen-on and screen-off to prevent large elapsed jumps

**Service lifecycle:**
- `onCreate()` → initializes managers, creates notification, registers receivers, starts loop
- `observeGroups()` → Flow-based DB cache (`cachedGroups`)
- `onDestroy()` → dismisses block screen, cancels jobs, schedules retry watchdog

**Split-screen support:**
- `visibleApps = AppTickAccessibilityService.getVisiblePackages()`
- When `visibleApps.size > 1`, charges time to ALL visible apps simultaneously
- Each visible app can independently trigger blocking

**Testing hooks:**
- `disableBackgroundLoopForTesting` — prevents auto-start of loop
- `setFixedElapsedForTesting(ms)` — injects deterministic elapsed time
- `setScreenOnForTesting(on)` — overrides screen state for screen-off timer tests
- `navigateHomeCallCount` — counts block screen launches

**Companion object statics:**
- `isRunning: Boolean` — service lifecycle flag
- `wakeUpChannel: Channel<Unit>` — conflated channel for immediate wakeup
- `requestImmediateCheck()` — called by AccessibilityService
- `applyDesiredServiceState(ctx, shouldRun)` — starts or stops service + watchdog
- `scheduleServiceWatchdog(ctx)` — AlarmManager-based recovery

## `FloatingBubbleService.kt` — Overlay service (premium)

**File:** `backgroundProcesses/FloatingBubbleService.kt` (~570 lines)

Draws draggable semi-transparent bubbles showing time remaining.

**Per-app bubbles:** Each limited visible app gets its own bubble (staggered vertically by 55dp).

**Actions:** `ACTION_UPDATE`, `ACTION_HIDE`, `ACTION_SHOW`, `ACTION_REMOVE_APP`

**Features:**
- Draggable via touch events
- Drop-to-dismiss target (bottom center)
- Per-app position persistence in SharedPreferences
- 1-second countdown timer (independent of BackgroundChecker)
- Dismiss flag (`PREF_BUBBLE_DISMISSED`) prevents re-showing until app change
- Y-position clamped to stay below status bar (prevents notification shade conflict)

**Intent factory methods:**
- `updateIntent(ctx, text, timeMillis, appPackage)`
- `hideIntent(ctx)`
- `showIntent(ctx, text, timeMillis, appPackage)`
- `removeAppIntent(ctx, appPackage)`

---

## 4b. Power & Battery Optimization

AppTick balances fast blocking responsiveness with battery conservation through several strategies:

### Adaptive Polling Interval

| Context | Interval | Rationale |
|---------|----------|-----------|
| Any app in foreground | 2000ms | Uniform 2s polling — ensures responsive blocking even when accessibility ("Enhanced App Detection") is off |
| Screen off or device locked | 2000ms (idle) | Loop runs but `shouldTrackUsageNow()` returns false — no app detection, no DB writes, just baseline reset |

When accessibility is enabled, `requestImmediateCheck()` wakeup overrides the interval entirely for instant blocking. When accessibility is off (UsageStats-only mode), the uniform 2s interval ensures worst-case detection is 2 seconds, not 4.

### Notification Channels — Minimal Badge/Sound

All three notification channels disable the app icon badge (`setShowBadge(false)`) and use low/min importance to avoid sounds, vibrations, and the notification dot on the launcher icon. Channels use `_v2` suffixes; legacy channels (without suffix) are deleted on startup so that importance downgrades take effect for existing users on update.

| Channel ID | Importance | Badge | Used by |
|---|---|---|---|
| `app_tick_channel_v2` | `IMPORTANCE_LOW` | Off | MainActivity (general alerts) |
| `APPTICK_CHANNEL_v2` | `IMPORTANCE_MIN` | Off | BackgroundChecker foreground service |
| `FLOATING_BUBBLE_CHANNEL_v2` | `IMPORTANCE_MIN` | Off | FloatingBubbleService |

### Notification Deduplication

`updateNotification()` caches the last notification text and skips the `NotificationManager.notify()` call when content hasn't changed. Building and posting a notification involves IPC to the system server, so avoiding redundant posts reduces CPU and binder overhead — particularly when the user stays on the same untracked app for extended periods.

### Bubble Display Uses Cached Groups

The floating bubble countdown display uses the in-memory `cachedGroups` (populated by Room Flow) instead of issuing a fresh suspend DB query every loop iteration. Since the bubble has its own independent 1-second countdown timer in `FloatingBubbleService`, the slight lag in cached data is imperceptible.

### Watchdog Scheduling

The AlarmManager watchdog (45-second interval) is only scheduled when the service starts or restarts. When `startServiceIfNotRunning()` is called while the service is already running, the redundant AlarmManager reschedule is skipped to avoid unnecessary system calls.

### Screen & Lock Awareness

- `shouldTrackUsageNow()` returns `false` when the screen is off (`!powerManager.isInteractive`) or the device is locked (`keyguardManager.isDeviceLocked`). The loop still runs at its normal interval but performs no work beyond resetting the elapsed baseline.
- `ScreenStateReceiver` updates `lastCheckElapsed` on both screen-on and screen-off transitions to prevent charging sleep/lock time as app usage.
