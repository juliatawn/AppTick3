# AppTick Architecture Reference

> Comprehensive documentation for Claude Code and human developers.
> Read CLAUDE.md first — it contains critical invariants that must never be broken.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Package Structure](#2-package-structure)
3. [Core Blocking Pipeline](#3-core-blocking-pipeline)
4. [Background Services](#4-background-services)
5. [Accessibility Service & Floating Windows](#5-accessibility-service--floating-windows)
6. [Data Layer](#6-data-layer)
7. [Domain Models & Groups](#7-domain-models--groups)
8. [App Limit Evaluation](#8-app-limit-evaluation)
9. [UI Layer](#9-ui-layer)
10. [Lock Modes & Premium](#10-lock-modes--premium)
11. [Settings & Theme](#11-settings--theme)
12. [Device Apps & Utilities](#12-device-apps--utilities)
13. [Test Suite](#13-test-suite)
14. [SharedPreferences Key Reference](#14-sharedpreferences-key-reference)
15. [Intent & Broadcast Reference](#15-intent--broadcast-reference)

---

## 1. Project Overview

AppTick is an Android app-blocking application. Users create **app limit groups** — collections of apps with shared or per-app time limits, optional time ranges, weekly schedules, and reset periods. When a user exceeds their configured limit, AppTick launches a fullscreen **block screen** on top of the app.

**Build config:** Android SDK 36, min SDK 27, Kotlin + Jetpack Compose + Room + Coroutines.

**Key dependencies:** Room (database), Gson (serialization), Coil (image loading), Google Play Billing (premium), AndroidX Security-Crypto (encrypted prefs), Material3 (UI).

---

## 2. Package Structure

```
com.juliacai.apptick/
├── backgroundProcesses/
│   ├── BackgroundChecker.kt        ← Core foreground service (polling loop, limit enforcement)
│   ├── AppTickAccessibilityService.kt  ← Event-driven foreground app detection + floating window close
│   └── FloatingBubbleService.kt    ← Overlay bubble showing time remaining (premium)
├── block/
│   ├── BlockWindowActivity.kt      ← Fullscreen block screen activity
│   └── BlockWindowScreen.kt        ← Compose UI for the block screen
├── appLimit/
│   ├── AppLimitEvaluator.kt        ← Pure logic: day/time/pause checks
│   ├── AppInGroup.kt               ← Data class: app within a group (name, package, icon)
│   ├── AppLimitDetailsActivity.kt  ← Detail view for a group
│   ├── AppLimitDetailsScreen.kt    ← Compose UI: group details with usage bars
│   ├── AppLimitScreen.kt           ← Compose list of AppLimitGroupItem cards
│   ├── AppLimitSettings.kt         ← Legacy interface for limit settings
│   ├── AppUsageItem.kt             ← Compose row: app usage with progress bar
│   └── AppUsageRow.kt              ← Compose row: compact app usage display
├── groups/
│   ├── AppLimitGroup.kt            ← Domain model (Serializable)
│   ├── AppLimitGroupItem.kt        ← Compose card: collapsible group with icons/controls
│   ├── AppLimitGroups.kt           ← LazyColumn with drag-and-drop reordering
│   ├── AppUsageStat.kt             ← Data class: per-app usage (appPackage, usedMillis)
│   ├── GroupAppItem.kt             ← Compose card: single app within a group
│   └── TimeRange.kt                ← Data class: time window (startHour, startMinute, endHour, endMinute)
├── data/
│   ├── AppTickDatabase.kt          ← Room database (v8, migrations 1→8)
│   ├── AppLimitGroupDao.kt         ← Room DAO (CRUD + queries)
│   ├── AppLimitGroupEntity.kt      ← Room entity (22 columns)
│   ├── Converters.kt               ← Room TypeConverters (JSON ↔ lists via Gson)
│   ├── Mapper.kt                   ← Entity ↔ domain model conversion
│   ├── LegacyDataMigrator.kt       ← Migrates old appLimitPrefs file → Room
│   ├── LegacyLockPrefsMigrator.kt  ← Migrates old lock preferences
│   ├── AppLimitBackupManager.kt    ← JSON backup/restore (schema v3)
│   └── GroupCardOrderStore.kt      ← Persists card drag order in SharedPreferences
├── newAppLimit/
│   ├── AppLimitViewModel.kt        ← ViewModel for create/edit/duplicate group flow
│   ├── AppSelectScreen.kt          ← Compose: app picker with search + checkboxes
│   ├── AppSearchScreen.kt          ← Compose: search field (minimal)
│   ├── SetTimeLimitsScreen.kt      ← Compose: full limit configuration wizard
│   └── AppSelectionUtils.kt        ← Helpers: isAppSelected(), toggleSelectedApp()
├── lockModes/
│   ├── EnterPasswordActivity.kt    ← Password entry screen
│   ├── EnterPasswordScreen.kt      ← Compose UI for password entry
│   ├── SetPassword.kt              ← Activity: set/change password
│   ├── SetPasswordScreen.kt        ← Compose: password setup UI
│   ├── EnterSecurityKeyActivity.kt ← USB security key auth
│   ├── SecurityKeySettings.kt      ← Security key configuration data
│   ├── SecurityKeySettingsActivity.kt
│   ├── SecurityKeySettingsScreen.kt
│   ├── UsbSecurityKey.kt           ← USB FIDO2 key logic
│   ├── UsbSecurityKeySetupActivity.kt
├── premiumMode/
│   ├── PremiumModeScreen.kt        ← Compose: lock mode selector (premium)
│   ├── PremiumModeInfoScreen.kt    ← Compose: premium features list
│   ├── LockdownModeActivity.kt     ← Activity: lockdown configuration
│   ├── LockdownModeScreen.kt       ← Compose: lockdown setup UI
│   ├── LockdownSettings.kt         ← Data class: lockdown configuration
│   ├── LockdownTimeActivity.kt     ← One-time lockdown date/time picker
│   ├── LockdownTimeScreen.kt       ← Compose: lockdown time display
│   ├── LockdownSummaryFormatter.kt ← Formats lockdown target dates
│   ├── LockModesBlockedScreen.kt   ← Compose: "editing is locked" message
│   ├── PremiumModeFeatureInfoSection.kt
│   └── FeatureDetailText.kt
├── deviceApps/
│   ├── AppManager.kt               ← Queries installed apps via PackageManager
│   ├── AppListViewModel.kt         ← ViewModel: searchable installed app list
│   ├── AppSearchActivity.kt        ← Activity wrapper for app search
│   ├── AppUsageStats.kt            ← Queries UsageStatsManager for app usage
│   └── GroupPage.kt                ← Activity: group detail page
├── settings/
│   ├── ColorPickerActivity.kt      ← Activity: custom color picker (premium)
│   └── ColorPickerScreen.kt        ← Compose: swatch-based color picker
├── permissions/
│   ├── BatteryOptimizationHelper.kt ← Battery optimization status + OEM guidance
│   └── PermissionOnboardingScreen.kt ← First-run permission request UI
├── MainActivity.kt                 ← Main entry: navigation, billing, lock management
├── MainScreen.kt                   ← Compose: main screen scaffold with FAB
├── MainViewModel.kt                ← ViewModel: group CRUD, premium status, unpause-reset logic
├── PremiumStore.kt                 ← Encrypted premium entitlement storage (singleton)
├── BaseActivity.kt                 ← Base activity: theme + color change receiver
├── SettingsActivity.kt             ← Settings container activity
├── SettingsScreen.kt               ← Compose: settings + backup/restore
├── Receiver.kt                     ← BroadcastReceiver: boot, screen, watchdog
├── LockPolicy.kt                   ← Pure logic: lock mode evaluation
├── TimeManager.kt                  ← Time limit/reset calculations
├── TimeRangeUtils.kt               ← Time range evaluation helpers
├── TimeFormatting.kt               ← Locale-aware clock time formatting
├── ThemeModeManager.kt             ← Dark mode / custom color persistence
├── AppInfo.kt                      ← Data class: app with usage tracking
├── AppTheme.kt                     ← Theme system: palettes, color schemes
├── Changelog.kt                    ← Changelog dialog + version tracking
├── FeaturePhotoCarousel.kt         ← Onboarding photo carousel
├── AppLaunchLoadingScreen.kt       ← Splash screen composable
├── ScrollIndicators.kt             ← Custom scrollbar indicators
├── DateTimePickerDialog.kt         ← Date/time picker for lockdown
├── CustomViewPager.kt              ← ViewPager with swipe toggle
└── AppTickLogo.kt                  ← Circular logo composable
```

---

## 3. Core Blocking Pipeline

This is the most critical flow in the app. Changes here require extreme care.

```
┌──────────────────────┐     ┌──────────────────────────┐
│ AccessibilityService │     │ BackgroundChecker         │
│ onAccessibilityEvent │────▸│ wakeUpChannel.receive()   │
│ TYPE_WINDOW_CHANGED  │     │ or 2s polling timeout     │
└──────────────────────┘     └─────────┬────────────────┘
                                       │
                              getForegroundApp()
                              ┌────────┴────────┐
                              │ ALWAYS query     │
                              │ BOTH sources:    │
                              │ 1. Accessibility │
                              │ 2. UsageStats    │
                              │ Pick newest      │
                              └────────┬────────┘
                                       │
                              checkAppLimits(app, elapsed)
                              ┌────────┴────────────────────┐
                              │ For each active group:       │
                              │ 1. Check reset (expired?)    │
                              │ 2. Check active days         │
                              │ 3. Check outside time range  │
                              │ 4. Check shouldCheckLimit    │
                              │ 5. Check zero limit → block  │
                              │ 6. Decrement time remaining  │
                              │ 7. Check limit reached       │
                              └────────┬────────────────────┘
                                       │ (if blocked)
                              launchBlockScreen(pkg)
                              ┌────────┴──────────────────┐
                              │ 1. navigateHomeCallCount++ │
                              │ 2. Hide floating bubble    │
                              │ 3. tryCloseFloatingWindow  │  ← ALWAYS called (see CLAUDE.md #2)
                              │ 4. startActivity(block)    │
                              └─────────────────────────────┘
```

### Key timing constants (`BackgroundChecker.kt`)

| Constant | Value | Purpose |
|----------|-------|---------|
| `CHECK_INTERVAL` | 2000ms | Polling interval for all foreground apps |
| `IDLE_CHECK_INTERVAL` | 2000ms | Same as CHECK_INTERVAL — uniform 2s polling ensures responsive blocking even without accessibility |
| `MAX_ELAPSED` | 10,000ms | Cap on elapsed delta per tick |
| `MAX_STALENESS_MS` | 10,000ms | Accessibility data expiry (in `AppTickAccessibilityService`) |
| `FOREGROUND_EVENT_LOOKBACK_MS` | 15,000ms | UsageEvents query window |
| `FOREGROUND_USAGE_LOOKBACK_MS` | 120,000ms | UsageStats fallback query window |
| `FOREGROUND_USAGE_MAX_AGE_MS` | 15,000ms | Max age for UsageStats fallback |
| `WATCHDOG_INTERVAL_MS` | 45,000ms | AlarmManager watchdog interval |

---

## 4. Background Services

### `BackgroundChecker.kt` — Foreground service

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

### `FloatingBubbleService.kt` — Overlay service (premium)

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

The unified loop dynamically adjusts its polling interval based on context:

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

---

## 5. Accessibility Service & Floating Windows

### `AppTickAccessibilityService.kt`

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

---

## 6. Data Layer

### Database Schema (Room v8)

**File:** `data/AppTickDatabase.kt`

Table: `app_limit_groups`

| Column | Type | Default | Added in |
|--------|------|---------|----------|
| id | INTEGER PK AUTO | — | v1 |
| name | TEXT | NULL | v1 |
| timeHrLimit | INTEGER | — | v1 |
| timeMinLimit | INTEGER | — | v1 |
| limitEach | INTEGER | — | v1 |
| resetHours* | INTEGER | — | v1 |
| weekDays | TEXT (JSON) | — | v1 |
| apps | TEXT (JSON) | — | v1 |
| paused | INTEGER | — | v1 |
| useTimeRange | INTEGER | — | v1 |
| startHour | INTEGER | — | v1 |
| startMinute | INTEGER | — | v1 |
| endHour | INTEGER | — | v1 |
| endMinute | INTEGER | — | v1 |
| cumulativeTime | INTEGER | — | v1 |
| timeRemaining | INTEGER | — | v1 |
| nextResetTime | INTEGER | — | v1 |
| nextAddTime | INTEGER | — | v1 |
| perAppUsage | TEXT (JSON) | '[]' | v5 |
| blockOutsideTimeRange | INTEGER | 0 | v6 |
| isExpanded | INTEGER | 1 | v7 |
| timeRanges | TEXT (JSON) | '[]' | v8 |

*\*`resetHours` column name is legacy; the field is named `resetMinutes` in the entity and stores minutes.*

**Migrations:** v1-4 → v5 (unified), v5→v6, v6→v7, v7→v8 (incremental ALTERs).

### `AppLimitGroupEntity.kt`

Room entity with `@SerializedName` annotations using single-char alternates (a-v) for compact JSON backup serialization.

### `AppLimitGroupDao.kt`

| Method | Return | Notes |
|--------|--------|-------|
| `getAllAppLimitGroups()` | LiveData\<List\> | Ordered by name ASC |
| `getAllAppLimitGroupsFlow()` | Flow\<List\> | Used by BackgroundChecker cache |
| `getAllAppLimitGroupsImmediate()` | suspend List | Direct query |
| `getActiveGroupCount()` / `Sync()` | Int | WHERE paused=0 |
| `getGroup(id)` | suspend Entity? | By primary key |
| `getGroupLive(id)` | LiveData\<Entity?\> | Observable |
| `getGroupContainingApp(pkg)` | suspend Entity? | LIKE '%pkg%' |
| `insertAppLimitGroup(entity)` | suspend | REPLACE on conflict |
| `updateAppLimitGroup(entity)` | suspend | Standard update |
| `updateTimeRemaining(id, ms)` | suspend | Single-column update |
| `updateTimeAndUsage(id, ms, usage)` | suspend | Timer + perAppUsage only (no overwrite of paused/config) |
| `updateResetState(id, ms, usage, reset, add)` | suspend | Reset columns only (no overwrite of paused/config) |
| `updateGroupExpanded(id, bool)` | suspend | Single-column update |
| `deleteAppLimitGroup(entity)` | suspend | Standard delete |
| `deleteAllAppLimitGroups()` | suspend | Truncate |
| `replaceAllAppLimitGroups(list)` | suspend @Transaction | Delete all + insert |

### `Converters.kt`

Room TypeConverters using Gson for JSON serialization of:
- `List<Int>` (weekDays)
- `List<AppInGroup>` (apps)
- `List<AppUsageStat>` (perAppUsage)
- `List<TimeRange>` (timeRanges)

All use `@SerializedName` with single-char obfuscated keys for compact storage.

### `Mapper.kt`

Extension functions for Entity ↔ Domain conversion:
- `AppLimitGroupEntity.toDomainModel()` → `AppLimitGroup`
  - Uses `effectiveTimeRanges()` to bridge v5-7 (4 columns) to v8 (JSON array)
- `AppLimitGroup.toEntity()` → `AppLimitGroupEntity`
  - Extracts first TimeRange to legacy columns for backward compatibility

### `LegacyDataMigrator.kt`

Migrates old `appLimitPrefs` file format to Room:
- `migrate()` → reads legacy file, parses lines, deduplicates, inserts to DB, deletes file
- `normalizeStoredAppDisplayNamesIfNeeded()` → fixes apps where display name equals package name
- `LegacyAppLimitLineParser.parseLineToEntity()` → parses colon-separated legacy format
  - Converts Calendar-style day-of-week (Sun=1) to ISO (Mon=1)
  - Converts legacy resetHours to resetMinutes

### `LegacyLockPrefsMigrator.kt`

Migrates old lock preferences (boolean `password` key) to new system (`active_lock_mode` string).

### `AppLimitBackupManager.kt`

JSON backup/restore with schema versioning:
- `createBackup()` → strips runtime state (timeRemaining, perAppUsage, etc.)
- `fromJson()` → handles schema v1-3, obfuscated keys, legacy time range conversion
- `collectAppSettings()` / `applyAppSettings()` → SharedPreferences backup
- `writeBackupToUri()` / `readBackupFromUri()` → ContentResolver I/O

### `GroupCardOrderStore.kt`

Persists drag-and-drop card order as JSON array of group IDs in SharedPreferences:
- `sanitizeOrder(saved, available)` → removes deleted IDs, appends new ones
- `applyOrder(items, savedOrder, idSelector)` → generic reorder function

---

## 7. Domain Models & Groups

### `AppLimitGroup.kt` — Primary domain model

```kotlin
data class AppLimitGroup(
    val id: Long,
    val name: String?,
    val timeHrLimit: Int,           // Hours component of time limit
    val timeMinLimit: Int,          // Minutes component of time limit
    val limitEach: Boolean,         // true = per-app limit, false = shared limit
    val resetMinutes: Int,          // 0 = daily (midnight), >0 = periodic (every N minutes)
    val weekDays: List<Int>,        // Active days (1=Mon, 7=Sun), empty = all days
    val apps: List<AppInGroup>,     // Apps in this group
    val paused: Boolean,
    val useTimeRange: Boolean,
    val blockOutsideTimeRange: Boolean,
    val timeRanges: List<TimeRange>,
    val startHour: Int, startMinute: Int, endHour: Int, endMinute: Int,  // Legacy single range
    val cumulativeTime: Boolean,    // Carry over unused time across resets
    val timeRemaining: Long,        // Milliseconds remaining (runtime state)
    val nextResetTime: Long,        // Unix timestamp of next reset
    val nextAddTime: Long,          // Unix timestamp of next cumulative addition
    val perAppUsage: List<AppUsageStat>,  // Per-app usage tracking
    val isExpanded: Boolean         // UI collapse state
) : Serializable
```

### `AppInGroup.kt`

```kotlin
data class AppInGroup(
    val appName: String,      // Display name
    val appPackage: String,   // Package name
    val appIcon: String?      // Unused (icons loaded dynamically)
) : Serializable
```

### `AppUsageStat.kt`

```kotlin
data class AppUsageStat(
    val appPackage: String,   // Package name
    val usedMillis: Long      // Milliseconds used since last reset
)
```

### `TimeRange.kt`

```kotlin
data class TimeRange(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int = 23,
    val endMinute: Int = 59
)
```

### `AppLimitGroupItem.kt` — Group card composable

Collapsible card showing:
- Header: name, lock/pause/edit buttons, expand chevron
- App icons (horizontal LazyRow)
- Time remaining and configured limit
- Expanded details: active days, time ranges, reset schedule, cumulative indicator

Key formatting functions:
- `formatConfiguredTimeLimit()` → "X hr Y min" or "Blocks"
- `totalGroupTimeLeftMillis()` → calculates remaining considering limitEach mode
- `isGroupCurrentlyLimited()` → checks day + time range activity

### `AppLimitGroups.kt` — Draggable list

LazyColumn with long-press drag-and-drop reordering:
- Uses `LongHoldDragArea` (500 ms hold via custom `ViewConfiguration`) around the list
  to prevent accidental drags — longer than the default ~400 ms system long-press
- On drag start: haptic feedback (`LongPress`) confirms activation
- Wiggle animation (±2° rotation, 100 ms cycle via `Animatable`) + 1.04× scale while dragging
- **Direct-formula offset**: `draggingOffsetY = change.position.y - dragAnchorY` overwrites
  every frame instead of accumulating deltas. This is drift-proof — the auto-scroll
  `LaunchedEffect` still adds `consumed` for smooth inter-frame rendering, but the next
  `onDrag` overwrites it, so errors never compound.
- **Immediate swap** (in both `onDrag` AND the auto-scroll `LaunchedEffect`): items swap
  in `orderedIds` during drag so the dragged item's layout index stays near the finger.
  The auto-scroll loop must also perform swaps because the finger is stationary during
  auto-scroll (no `onDrag` fires). Without swaps in both places, the item stays at its
  old index, scrolls off the viewport, and gets disposed by LazyColumn (killing the gesture).
- **Finger-based triggers**: auto-scroll and swap detection use `fingerY`
  (finger viewport position) instead of card center — required because group cards
  have variable heights (expanded vs collapsed) and a tall card's center may never
  reach the edge zone.
- `pointerInput` keyed on `group.id` only (not `orderedIds.size`) to prevent
  coroutine restart during swap.
- **`userScrollEnabled = false` during drag**: the LazyColumn's internal `scrollable`
  can re-enter gesture detection after a swap recomposition and consume a pointer event
  before the item's `pointerInput` sees it. `awaitDragOrCancellation` returns null on
  consumed events, killing the drag. Disabling user scroll prevents this; programmatic
  `scrollBy()` (auto-scroll loop) is unaffected.
- **`LaunchedEffect(groups)` must never `clear()`+`addAll()` during drag**: the momentary
  empty list disposes all LazyColumn items, killing the `pointerInput` coroutine and
  cancelling the gesture. During an active drag, only surgical add/remove (for newly
  created or deleted groups) is allowed. Full stored-order resync happens after drag ends
  (the effect re-runs because `draggingItemId` is in its key list).
- Persists order via `GroupCardOrderStore`
- `zIndex`, `translationY`, `rotationZ`, and `scaleX/Y` transforms during drag

### `DragAfterLongHold.kt` — Long-hold ViewConfiguration wrapper

`LongHoldDragArea` composable overrides `LocalViewConfiguration.longPressTimeoutMillis`
so that `detectDragGesturesAfterLongPress` requires a longer hold (default 500 ms).
Used by both `AppLimitGroups.kt` and `GroupPage.kt` drag-and-drop lists.

### `AppLimitGroupItem.kt` — Drag-safe Card

Uses a **single** non-clickable `Card` composable at all times. Click handling is via
`Modifier.clickable` with an `isDragging` guard inside the handler — the `enabled` parameter
is always `true` to keep the modifier chain stable during drag.

**Critical:** Do NOT change `clickable`'s `enabled` parameter based on drag state
(`clickable(enabled = !isDragging)`) — toggling `enabled` restarts the `clickable`
modifier's internal pointer input, which can cancel the active drag gesture.

**Critical:** Do NOT use conditional `if (isDragging) Card(...) else Card(onClick = ...)`
— swapping between two Card composables causes Compose to dispose/recreate the layout node,
which kills the parent `pointerInput` coroutine mid-gesture and breaks drag-and-drop.

The card content is extracted to `AppLimitGroupItemContent` to keep the function concise.

### `GroupPage.kt` — App card reordering

Same drag UX as group cards: `LongHoldDragArea` wrapper, wiggle + scale + haptic feedback,
direct-formula offset (drift-proof). Uses `draggedCenterY` for auto-scroll and swap triggers
(app cards are uniform height so center-based works fine).
Hint card reads: "Hold down on a card until it wiggles, then drag to reorder."

---

## 8. App Limit Evaluation

### `AppLimitEvaluator.kt` — Pure logic, no side effects

**File:** `appLimit/AppLimitEvaluator.kt`

| Method | Logic |
|--------|-------|
| `shouldCheckLimit(group, now)` | Returns false if paused, wrong day, or outside time range |
| `isWithinActiveDays(group, now)` | Empty weekDays = always active. Converts Calendar day to Mon=1 format |
| `isWithinTimeRange(group, now)` | Delegates to `isNowWithinAnyTimeRange()` from TimeRangeUtils |
| `shouldBlockOutsideTimeRange(group, now)` | True when useTimeRange + blockOutsideTimeRange + NOT in range |
| `isLimitReached(group)` | `timeRemaining <= 0L` |

### `TimeRangeUtils.kt`

- `getConfiguredTimeRanges()` → returns timeRanges list, or constructs single range from legacy fields
- `isNowWithinTimeRange(range, nowMinutes)` → handles midnight-crossing ranges (e.g., 22:00→06:00)
- `isNowWithinAnyTimeRange(ranges, nowMillis)` → returns true if empty list (always active)

### `TimeManager.kt`

- `getTimeRemaining()` → converts hours+minutes to milliseconds
- `getNextResetTime()` → periodic (now + resetMinutes) or daily (next midnight)
- `nextMidnight(nowMillis)` → 00:00:00 tomorrow in device timezone
- `computeNextUnblockTime(group, nowMillis, blockedForOutsideRange)` → computes when the app will next be unblocked:
  - **Blocked outside time range:** returns next time range start (on an active day)
  - **Zero limit with time range (no blockOutside):** returns current time range end
  - **Zero limit, no time range or blockOutside:** returns 0L ("Not scheduled")
  - **Limit reached with time range (no blockOutside):** returns `min(nextResetTime, rangeEnd)`
  - **Limit reached with time range + blockOutside:** user needs BOTH reset AND to be in range. If `nextResetTime` falls within a time range, returns `nextResetTime`. Otherwise returns the next range start after the reset (reset will have already fired by then).
  - **Limit reached, no time range:** returns `nextResetTime`
- `computeEffectiveNextReset(group, nowMillis)` → adjusts `nextResetTime` for display on the Group Details page. Handles two cases: (1) **Zero limit + time range + Allow No Limits:** reset is meaningless (0→0), shows current range end instead; (2) **Non-zero limit + reset outside range:** shows next range start after reset. Returns `nextResetTime` unchanged when no adjustment needed. The Group Details label switches from "Next Reset:" to "Available At:" when the time is adjusted.
- `nextTimeRangeEntry(ranges, nowMillis, weekDays)` → finds soonest future range start on an active day
- `currentTimeRangeEnd(ranges, nowMillis)` → finds when the currently active time range ends (handles overnight)
- `nextOccurrenceOfTime(hour, minute, nowMillis, weekDays)` → next occurrence of a specific time on an active day

### Day-of-week convention

AppTick uses **Monday=1, Sunday=7** (ISO 8601 style).
Android's `Calendar.DAY_OF_WEEK` uses **Sunday=1, Saturday=7**.
Conversion: `((cal.get(DAY_OF_WEEK) + 5) % 7) + 1`

---

## 9. UI Layer

### Navigation (MainActivity.kt)

Routes managed via Jetpack Navigation Compose:
- `"main"` → MainScreen
- `"settings"` → SettingsScreen
- `"selectApps"` → AppSelectScreen (step 1)
- `"setTimeLimit"` → SetTimeLimitsScreen (step 2)
- `"premium"` → PremiumModeScreen
- `"appLimitDetails/{groupId}"` → AppLimitDetailsScreen
- `"appLimitBackup"` → AppLimitBackupScreen
- `"colorPicker"` → ColorPickerScreen
- `"lockModesBlocked"` → LockModesBlockedScreen

### Create/Edit Group Flow

```
AppSelectScreen (pick apps)
    ↓ selectedApps
SetTimeLimitsScreen (configure limits)
    ↓ AppLimitGroup
AppLimitViewModel.saveGroup()
    ↓ fetch previousGroup from DB (for edits)
    ↓ normalizeGroupForPersistence(previousGroup)
    ↓   → if limit changed: reset balance to new limit, clear usage, refresh reset time
    ↓ DAO.insert/update
BackgroundChecker.applyDesiredServiceState()
```

**Immediate-effect guarantees for group setting changes:**

| User Action | What takes effect immediately |
|-------------|------------------------------|
| Change time limit | `timeRemaining` reset to new limit, `perAppUsage` cleared, periodic `nextResetTime` recalculated from now |
| Change reset interval | `timeRemaining` reset to full limit, `perAppUsage` cleared, `nextResetTime` recalculated from now |
| Unpause group | If `nextResetTime` has passed while paused: `timeRemaining` reset to full limit, `perAppUsage` cleared, `nextResetTime` recalculated from now. BackgroundChecker woken via `requestImmediateCheck()` |
| Change active days / time ranges | Takes effect on next BackgroundChecker iteration (evaluated per-check) |

**SetTimeLimitsScreen sections:**
1. Selected apps (FlowRow of chips, removable)
2. Group name
3. Enable time limit toggle (off = always block)
4. Time limit hours/minutes + per-app vs total radio
5. Time range (premium) with multiple ranges, outside-range behavior
6. Active days (weekday circles)
7. Periodic reset (premium) + cumulative time

### `AppLimitViewModel.kt`

- `normalizeGroupForPersistence()` → sets timeRemaining, nextResetTime, validates usage stats
  - Accepts optional `previousGroup` to detect limit edits
  - **When the time limit changes** (timeHrLimit or timeMinLimit differ from previous):
    - `timeRemaining` is reset to the full new limit (e.g., 1min left → edit to 30min → 30min left)
    - `perAppUsage` is cleared (all app usage tracking reset)
    - For periodic reset groups (`resetMinutes > 0`), `nextResetTime` is recalculated from now
    - For daily reset groups, `nextResetTime` is preserved if still in the future
  - **When the reset interval changes** (resetMinutes differs from previous):
    - `timeRemaining` is reset to the full limit (fresh start on new schedule)
    - `perAppUsage` is cleared
    - `nextResetTime` is recalculated from now (periodic: now + interval, daily: next midnight)
    - This ensures changing reset interval from e.g. daily → 3 hours takes effect immediately
- `saveGroup()` → fetches the previous group from DB (for edits) to pass to normalization
- `duplicateGroupForCreation()` → clears ID/state for new group
- `SetTimeLimitDraft` → intermediate form state for cancel-safe editing

### Block Screen

**`BlockWindowActivity.kt`:**
- Separate task affinity (`com.juliacai.apptick.block`)
- Excluded from recents
- Back press blocked (OnBackPressedCallback)
- Dismissible only via `ACTION_DISMISS_BLOCK` broadcast
- Receives data via Intent extras: app_name, app_package, group_name, block_reason, times, etc.
- **Split-screen bypass prevention (dismiss + relaunch):**
  1. **Manifest:** `android:resizeableActivity="false"` prevents split-screen on regular phones (API 27+). Ignored on large-screen foldables (API 31+, sw >= 600dp).
  2. **Activity callback:** `onMultiWindowModeChanged(true)` calls `finish()` to collapse split-screen. Works on devices where the callback fires.
  3. **BackgroundChecker fallback (primary on Samsung foldables):** `launchBlockScreen()` calls `AppTickAccessibilityService.isBlockScreenInSplitScreen()` which checks whether AppTick's own window covers less than 85% of the screen (same threshold as floating window detection). If so, it dismisses the block screen via `ACTION_DISMISS_BLOCK` broadcast and returns early. Split-screen collapses, the blocked app goes fullscreen, the accessibility service fires a foreground event waking BackgroundChecker, and the next iteration relaunches the block screen fullscreen on top. This works even when `onMultiWindowModeChanged` is suppressed (Samsung foldables with `resizeableActivity="false"`).
  - **DO NOT use TYPE_APPLICATION_OVERLAY for the block screen.** Overlays must not be used to cover split-screen panes.

**`BlockWindowScreen.kt`:**
- Shows app icon, name, group name
- Block reason text
- Usage statistics
- "Available At" time — shows when the app will next be unblocked (computed by `TimeManager.computeNextUnblockTime()`)

### MainScreen.kt

Scaffold with:
- TopAppBar (title, "LOCK NOW" button, lock icon, settings)
  - "LOCK NOW" text button appears only when user is in Lockdown mode but currently unlocked; triggers the relock dialog
- FAB (add new group)
- Battery warning card (with system settings buttons)
- Group details hint (one-time)
- List content (delegated to AppLimitGroups)

---

## 10. Lock Modes & Premium

### `LockPolicy.kt` — Pure evaluation logic

```kotlin
data class LockState(
    val activeLockMode: LockMode,           // NONE, PASSWORD, SECURITY_KEY, LOCKDOWN
    val passwordUnlocked: Boolean,
    val securityKeyUnlocked: Boolean,
    val lockdownType: LockdownType,          // ONE_TIME or RECURRING
    val lockdownEndTimeMillis: Long,
    val lockdownRecurringDays: List<Int>,
    val lockdownRecurringUsedKey: String?
)

data class LockDecision(
    val isLocked: Boolean,
    val shouldClearExpiredLockdown: Boolean,
    val consumeKey: String?                  // Set for recurring: marks day as consumed
)
```

**Lock modes:**

| Mode | Locked when | Unlocked when |
|------|-------------|---------------|
| NONE | Never | Always |
| PASSWORD | `!passwordUnlocked` | User enters correct password |
| SECURITY_KEY | `!securityKeyUnlocked` | User authenticates with USB key |
| LOCKDOWN (ONE_TIME) | `now < lockdownEndTimeMillis` | After end time (auto-clears) |
| LOCKDOWN (RECURRING) | Not on allowed day, or day already consumed | On allowed day, not yet consumed |

**Auto-relock:** PASSWORD and SECURITY_KEY auto-relock when user leaves MainActivity (if unlocked).

### Premium Features

Gated by `PremiumStore.isPremium(context)` (EncryptedSharedPreferences):
- Dark mode
- Custom color mode
- Floating time bubble
- Time range configuration
- Periodic reset
- Cumulative time
- Backup/restore

### `PremiumStore.kt` — Encrypted premium storage

Singleton that stores premium entitlement in `EncryptedSharedPreferences` (AES-256).
All premium reads/writes across the app go through `PremiumStore.isPremium(context)` and
`PremiumStore.setPremium(context, value)`.

**On first access:** migrates the legacy plaintext `"premium"` key from `groupPrefs` into
the encrypted file, then removes the plaintext key.

**Billing re-query:** `MainActivity.checkPendingPurchases()` calls `queryPurchasesAsync` on
each billing connection. If Google Play returns OK with no valid purchases (e.g. refund),
premium is revoked. If the query fails (offline), the local encrypted state is preserved.

#### Cumulative Time — Reset & Midnight Carryover Rules

When `cumulativeTime = true` and `resetMinutes > 0`, unused time carries over across periodic
resets **within the same calendar day**:

1. **Intra-day periodic reset:** `newTimeRemaining = currentRemaining + missedCount × fullLimit`
   (e.g., 10 min left + 3 missed resets × 5 min limit = 25 min after catch-up).
2. **Missed-reset catch-up:** When the service is dormant (Doze, battery optimization, app kill)
   and multiple reset intervals elapse, `nextResetTime` is advanced along the interval grid
   (not jumped to `now + interval`). All missed intervals are credited in a single tick:
   `missedCount = ((now − nextResetTime) / intervalMs) + 1`.
3. **Midnight boundary:** If the previous reset occurred before today's start-of-day (00:00),
   carryover is suppressed. Only today's elapsed intervals count:
   `newTimeRemaining = todayResetCount × fullLimit`. Yesterday's accumulated time does not
   leak into the next day.
4. **Normalization:** `normalizeGroupForPersistence()` does **not** cap `timeRemaining` at
   `limitInMillis` when cumulative mode is active, so carried-over time survives group edits.
5. **`nextAddTime`:** Set to `nextResetTime` for cumulative periodic groups; used by UI to show
   "Next time addition" countdown. Set to `0L` for non-cumulative groups.
- Lockdown mode
- Group duplication

---

## 11. Settings & Theme

### `AppTheme.kt`

- `customPalette(seedColor)` → generates ThemePalette using ColorUtils blending
- `resolveSeedColor(ctx)` → reads custom color from prefs, defaults to #6F34AD
- `currentPalette(ctx)` → resolves dark/light + custom/default palette
- `colorSchemeFromPalette(palette)` → converts to Material3 ColorScheme
- `applyTheme(activity)` → sets window background and bar appearance

### `ThemeModeManager.kt`

- `apply(ctx)` → reads dark mode pref, calls AppCompatDelegate.setDefaultNightMode
- `persistDarkMode(ctx, enabled)` → saves and applies
- Broadcasts "COLORS_CHANGED" to recreate activities

### `SettingsScreen.kt` (~850 lines)

Settings sections:
- Premium upgrade card (free users)
- Dark mode toggle (premium)
- Show time left in notification
- Floating bubble (premium)
- Enhanced app detection (Accessibility)
- Custom color mode (premium)
- Color picker navigation
- Backup/restore (premium)
- Changelog
- Battery reliability dialog

**AppLimitBackupScreen** (embedded):
- Export: strips runtime state → JSON file
- Import: validates schema, filters removed apps, sanitizes order, restarts service

---

## 12. Device Apps & Utilities

### `AppManager.kt`

Queries PackageManager for installed apps with launcher intent. Returns `List<AppInfo>`.

### `AppListViewModel.kt`

ViewModel with search filtering over installed apps. Exposes `filteredApps: LiveData<List<AppInfo>>`.

### `AppUsageStats.kt`

Queries `UsageStatsManager.queryUsageStats()` for daily usage data. Returns sorted list by usage time.

### `BatteryOptimizationHelper.kt`

Checks battery optimization status, detects OEM restrictions (Samsung, Xiaomi, Huawei, OnePlus, etc.), provides OEM-specific guidance text.

### `PermissionOnboardingScreen.kt`

First-run permission request for: overlay, usage stats, notifications.

### `FeaturePhotoCarousel.kt`

Swipeable onboarding carousel (7 photos). Shown once per version.

---

## 13. Test Suite

### Unit Tests (18 files, ~155 test methods)

Located in `app/src/test/java/com/juliacai/apptick/`

| File | Tests | What it verifies |
|------|-------|------------------|
| `AppThemeTest.kt` | 1 | Custom color mode requires premium AND user toggle |
| `ChangelogTest.kt` | 4 | Changelog visibility based on version tracking |
| `FeaturePhotoCarouselTest.kt` | 11 | Photo carousel visibility, startup routing, resource IDs |
| `LockPolicyTest.kt` | 11 | All lock modes, auto-relock, one-time/recurring lockdown, day consumption |
| `MainViewModelTest.kt` | 8 | Group loading, pause/delete, premium status, unpause expired-reset logic |
| `TimeManagerTest.kt` | 7 | Time remaining calc, midnight boundaries, periodic vs daily reset |
| `NextUnblockTimeTest.kt` | 22 | Next unblock time: outside range, zero limit, limit reached, overnight ranges, active days, helper methods |
| `AppLimitEvaluatorTest.kt` | 9 | Day filtering, time ranges (incl. overnight), pause, outside-range blocking |
| `AppTickAccessibilityServiceTest.kt` | 23 | Staleness boundary (9s ok, 10.001s stale), service lifecycle, floating detection, split-screen |
| `BackgroundCheckerBubbleCountdownTest.kt` | 4 | Bubble format (MM:SS under 1min, HH:MM over), hide logic |
| `NotificationGroupSelectionTest.kt` | 12 | Group ranking (lowest remaining wins), limitEach effective time, multi-profile, formatting |
| `AppLimitBackupManagerTest.kt` | 6 | JSON round-trip, obfuscated keys, schema v1→v3, null handling, runtime state clearing |
| `ConvertersCompatibilityTest.kt` | 3 | Obfuscated JSON key parsing for Room TypeConverters |
| `LegacyAppLimitLineParserTest.kt` | 4 | Legacy format parsing, day conversion, empty lists, malformed input |
| `AppLimitPersistenceNormalizationTest.kt` | 22 | Duplicate reset, config preservation, time remaining init/cap, daily vs periodic reset |
| `AppSelectionUtilsTest.kt` | 3 | Package-based matching, deduplication, toggle behavior |
| `LockdownSummaryFormatterTest.kt` | 4 | One-time date format, missing/past dates, recurring day sorting, invalid days |
| `ExampleUnitTest.kt` | 1 | Template (2+2=4) |

### Integration Tests (21 files, ~88 test methods)

Located in `app/src/androidTest/java/com/juliacai/apptick/`

| File | Tests | What it verifies |
|------|-------|------------------|
| `AccessibilityBlockingIntegrationTest.kt` | 13 | **End-to-end blocking flow**: time decrement with/without accessibility, per-app tracking, floating window handling, PiP, re-launch on each cycle |
| `BackgroundCheckerTest.kt` | 23 | **Service time tracking**: decrement, blocking, paused group timer/usage/race-condition, screen-off timer protection (with/without accessibility, per-app usage, resume), reset (daily/periodic/cumulative/midnight-boundary/multi-missed-same-day), per-app usage, zero limits, outside time range, cross-expiry in single tick |
| `NextUnblockTimeIntegrationTest.kt` | 3 | **Next unblock time intent extra**: outside range shows range start, zero limit shows range end, limit reached shows reset time |
| `MainActivityTest.kt` | 4 | MainScreen rendering, empty state, callbacks, lock icon |
| `MainActivityDuplicateGroupIntegrationTest.kt` | 2 | Duplication flow: free→premium dialog, premium→new group |
| `MainActivityEditGroupSelectionIntegrationTest.kt` | 1 | Edit group: pre-selection in app picker |
| `MainActivityLockModesIntentTest.kt` | 4 | Lock icon/FAB → correct activity per mode |
| `MainActivityLockdownFabIntegrationTest.kt` | 1 | FAB works during lockdown |
| `AppLimitGroupDaoTest.kt` | 7 | CRUD operations, active count, app search |
| `LegacyMigrationIntegrationTest.kt` | 5 | File→Room migration, idempotency, app name repair, lock pref migration |
| `ReceiverLegacyMigrationInstrumentationTest.kt` | 1 | Full startup migration pipeline |
| `AppLimitGroupItemTest.kt` | 7 | Card UI: lock mode, pause state, time display, collapse |
| `SetTimeLimitsScreenTest.kt` | 1 | Screen composition verification |
| `AppSelectScreenKeyboardIntegrationTest.kt` | 1 | IME action clears focus |
| `PremiumModeScreenTest.kt` | 4 | Lock mode interactions, free vs premium messaging |
| `PremiumModeInfoScreenTest.kt` | 1 | Feature list display |
| `ColorPickerScreenTest.kt` | 2 | Removed UI elements, swatch-only picker |
| `AppLimitGroupsListDragTest.kt` | 4 | Drag-and-drop reordering: card follows finger, auto-scroll, order persistence |
| `LimitEditTimeBalanceSyncIntegrationTest.kt` | 3 | Time balance sync on limit edit via normalizeGroupForPersistence |
| `ChangelogDialogTest.kt` | 1 | @Ignore (flaky on device farms) |
| `ExampleInstrumentedTest.kt` | 1 | Package name verification |

### Critical invariants verified by tests

| Invariant (from CLAUDE.md) | Verified by |
|----------------------------|-------------|
| #1: getForegroundApp() cross-validates both sources | AccessibilityBlockingIntegrationTest (14 tests) |
| #2: launchBlockScreen always calls tryCloseFloatingWindow | AccessibilityBlockingIntegrationTest (tests 7-12) |
| #3: closeFloatingWindow strategy order | AppTickAccessibilityServiceTest |
| MAX_STALENESS_MS = 10s boundary | AppTickAccessibilityServiceTest (tests 5-6: 9s✓, 10.001s✗) |
| Paused groups skip checking | AccessibilityBlockingIntegrationTest (test 6), AppLimitEvaluatorTest (test 8) |
| Paused groups don't decrement timer | BackgroundCheckerTest (paused timer/usage/race tests) |
| Screen-off doesn't decrement timer | BackgroundCheckerTest (screen-off tests: no accessibility, with accessibility, per-app usage, resume) |
| Block screen re-launches each cycle | AccessibilityBlockingIntegrationTest (tests 10-11) |
| Time tracking accuracy | BackgroundCheckerTest (tests 1-5, 11-13) |
| Reset logic (daily/periodic/cumulative/midnight-boundary/multi-missed) | BackgroundCheckerTest (tests 11-15) |

### Test infrastructure patterns

**Unit tests:**
- Pure logic tests (no context): LockPolicyTest, AppLimitEvaluatorTest, TimeManagerTest
- Mocked SharedPreferences: ChangelogTest
- Injectable lambdas: MainViewModelTest (premium read/write + service state via constructor params)
- Companion object testing: AppTickAccessibilityServiceTest (uses `simulateForTesting()`, `resetForTesting()`)

**Integration tests:**
- In-memory Room database: `Room.inMemoryDatabaseBuilder()`
- Real service binding: `ServiceTestRule`
- Accessibility simulation: `AppTickAccessibilityService.simulateForTesting()`
- Fixed elapsed time: `BackgroundChecker.setFixedElapsedForTesting()`
- Disabled background loop: `BackgroundChecker.disableBackgroundLoopForTesting = true`
- Compose test rule: `createComposeRule()`
- Emulator-only guards: `Assume.assumeTrue(Build.FINGERPRINT.contains("generic"))`

### Running tests

```bash
# Unit tests only
./gradlew testDebugUnitTest

# Instrumentation tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# CI tasks
./gradlew ciHost    # compile + unit tests + assemble instrumentation APK
./gradlew ciDevice  # run connected instrumentation tests
```

---

## 14. SharedPreferences Key Reference

Most keys stored in `"groupPrefs"` (Context.MODE_PRIVATE).
Premium entitlement is in `"apptick_secure_prefs"` (EncryptedSharedPreferences) — see `PremiumStore.kt`.

### App Settings
| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `dark_mode` | Boolean | false | Dark mode enabled |
| `custom_color_mode` | Boolean | false | Custom color mode |
| `custom_primary_color` | Int | — | Seed color for custom palette |
| `custom_accent_color` | Int | — | Accent color |
| `custom_background_color` | Int | — | Background color |
| `custom_card_color` | Int | — | Card color |
| `custom_icon_color` | Int | — | Icon color |
| `app_icon_color_mode` | String | — | Icon color mode |
| `showTimeLeft` | Boolean | true | Show time in notification |
| `floatingBubbleEnabled` | Boolean | false | Floating bubble (premium) |
| `group_card_order` | String(JSON) | — | Card order as JSON array of IDs |
| `screenOn` | Boolean | true | Screen state tracking |

### Lock Modes
| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `active_lock_mode` | String | "NONE" | NONE/PASSWORD/SECURITY_KEY/LOCKDOWN |
| `passUnlocked` | Boolean | false | Password mode: currently unlocked |
| `securityKeyUnlocked` | Boolean | false | Security key: currently unlocked |
| `lockdown_type` | String | "ONE_TIME" | ONE_TIME or RECURRING |
| `lockdown_end_time` | Long | 0 | One-time lockdown end timestamp |
| `lockdown_recurring_days` | String | "" | Comma-separated day numbers (1-7) |
| `lockdown_weekly_used_key` | String | null | Date key for consumed recurring window |
| `lockdown_prompt_after_unlock` | Boolean | — | Show relock prompt after expiry |
| `password` | String | — | Hashed password |
| `recovery_email` | String | — | Recovery email address |
| `force_recovery_email_setup` | Boolean | — | Force email setup on next unlock |

### Bubble State
| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `bubbleDismissed` | Boolean | false | User dismissed bubble |
| `floatingBubblePosX` | Int | — | Global bubble X position |
| `floatingBubblePosY` | Int | — | Global bubble Y position |
| `floatingBubblePosX_{pkg}` | Int | — | Per-app bubble X position |
| `floatingBubblePosY_{pkg}` | Int | — | Per-app bubble Y position |

### UI State
| Key | Type | Purpose |
|-----|------|---------|
| `batteryOemWarningDismissed` | Boolean | Battery warning dismissed |
| `groupDetailsHintDismissed` | Boolean | Group reorder hint dismissed permanently |
| `appsReorderedHintDismissed` | Boolean | App reorder hint dismissed permanently |
| `has_seen_launch_loading` | Boolean | Loading screen shown |
| `lastSeenChangelogVersionCode` | Long | Changelog version tracking |
| `lastSeenFeaturePhotosVersionCode` | Long | Carousel version tracking |

### Migration Flags
| Key | Type | Purpose |
|-----|------|---------|
| `legacy_app_name_repair_done_v1` | Boolean | App name repair completed |

---

## 15. Intent & Broadcast Reference

### Activities

| Activity | Intent Extras | Purpose |
|----------|--------------|---------|
| `BlockWindowActivity` | app_name, app_package, group_name, block_reason, app_time_spent, group_time_spent, time_limit_minutes, limit_each, use_time_range, block_outside_time_range, blocked_for_outside_range, next_reset_time | Block screen |
| `MainActivity` | EXTRA_EDIT_GROUP_ID (Long) | Edit existing group |
| `EnterPasswordActivity` | EXTRA_SETTINGS_SESSION_UNLOCK (Boolean) | Password entry |
| `EnterSecurityKeyActivity` | EXTRA_SETTINGS_SESSION_UNLOCK (Boolean) | Security key auth |

### Broadcasts

| Action | Sender | Receiver | Purpose |
|--------|--------|----------|---------|
| `ACTION_DISMISS_BLOCK` | BackgroundChecker | BlockWindowActivity | Dismiss block screen |
| `ACTION_SHOW_BUBBLE` | Notification button | BackgroundChecker.BubbleShowReceiver | Re-show dismissed bubble |
| `COLORS_CHANGED` | ThemeModeManager | BaseActivity | Recreate activity for theme change |
| `ACTION_SETTINGS_SESSION_UNLOCKED` | EnterPassword/SecurityKey | BackgroundChecker | Settings session unlocked |
| `ACTION_SERVICE_WATCHDOG` | AlarmManager | Receiver | Restart service if killed |

### System Broadcasts (Receiver.kt)

| Action | Handler |
|--------|---------|
| `ACTION_BOOT_COMPLETED` | Run migration, start service |
| `ACTION_MY_PACKAGE_REPLACED` | Run migration, start service |
| `ACTION_USER_PRESENT` | Start service |
| `ACTION_SCREEN_ON` | Start service |
| `ACTION_SCREEN_OFF` | Set screenOn=false |
