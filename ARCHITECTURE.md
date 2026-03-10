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

**Key dependencies:** Room (database), Gson (serialization), Coil (image loading), Google Play Billing (premium), Material3 (UI).

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
├── MainViewModel.kt                ← ViewModel: group CRUD, premium status
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
| `CHECK_INTERVAL` | 2000ms | Active polling — foreground app is in a tracked group |
| `IDLE_CHECK_INTERVAL` | 4000ms | Idle polling — foreground app is NOT in any group (battery saver) |
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
| `checkAppLimits(app, elapsed)` | Iterates groups, handles resets, decrements time, triggers blocking |
| `launchBlockScreen(pkg)` | Launches BlockWindowActivity + calls tryCloseFloatingWindow. **See CLAUDE.md #2** |
| `computeElapsedDelta()` | Computes real elapsed time since last check, capped at MAX_ELAPSED |
| `pickNotificationGroup(groups, app)` | Selects group with lowest remaining time for notification display |
| `formatBubbleCountdown(ms)` | Formats MM:SS (under 1min) or HH:MM (over 1min) |
| `updateFloatingBubble(app, fallback, visible)` | Manages per-app floating bubbles in split-screen |
| `shouldTrackUsageNow()` | Returns false if screen off or device locked |

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
| Tracked app in foreground | 2000ms | Fast enforcement — user is actively using a limited app |
| Untracked app in foreground | 4000ms | No limits to enforce — halves CPU wakeups for the common case |
| Screen off or device locked | 2000ms (idle) | Loop runs but `shouldTrackUsageNow()` returns false — no app detection, no DB writes, just baseline reset |

The `requestImmediateCheck()` wakeup from AccessibilityService overrides the interval entirely, ensuring instant blocking regardless of which interval is active.

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

**File:** `backgroundProcesses/AppTickAccessibilityService.kt` (~727 lines)

Lightweight AccessibilityService for instant foreground app detection and floating window management.

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
- `isAccessibilityServiceEnabled(ctx)` → checks system Settings

**Floating window detection:**
- `checkIfWindowIsFloating(windowId)` → returns true if ANY visible app window covers < 85% of screen area
- Handles Honor freeform, PiP, Samsung split-screen

**Floating window close strategies (CLAUDE.md #3):**

```
closeFloatingWindow(blockedPackage) → FloatingCloseResult
  1. Live re-check: isCurrentAppFloating || checkIfWindowIsFloating(-1)
     → If NOT floating: return NOT_FLOATING (no action)

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
- Auto-scrolls near edges (96dp threshold, 22dp/frame max)
- Persists order via `GroupCardOrderStore`
- `zIndex` and `translationY` transforms during drag

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
    ↓ normalizeGroupForPersistence()
    ↓ DAO.insert/update
BackgroundChecker.applyDesiredServiceState()
```

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
- `duplicateGroupForCreation()` → clears ID/state for new group
- `SetTimeLimitDraft` → intermediate form state for cancel-safe editing

### Block Screen

**`BlockWindowActivity.kt`:**
- Separate task affinity (`com.juliacai.apptick.block`)
- Excluded from recents
- Back press blocked (OnBackPressedCallback)
- Dismissible only via `ACTION_DISMISS_BLOCK` broadcast
- Receives data via Intent extras: app_name, app_package, group_name, block_reason, times, etc.

**`BlockWindowScreen.kt`:**
- Shows app icon, name, group name
- Block reason text
- Usage statistics
- Next reset time

### MainScreen.kt

Scaffold with:
- TopAppBar (title, lock icon, settings)
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

Gated by `prefs.getBoolean("premium", false)`:
- Dark mode
- Custom color mode
- Floating time bubble
- Time range configuration
- Periodic reset
- Cumulative time
- Backup/restore
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

### Unit Tests (17 files, ~85 test methods)

Located in `app/src/test/java/com/juliacai/apptick/`

| File | Tests | What it verifies |
|------|-------|------------------|
| `AppThemeTest.kt` | 1 | Custom color mode requires premium AND user toggle |
| `ChangelogTest.kt` | 4 | Changelog visibility based on version tracking |
| `FeaturePhotoCarouselTest.kt` | 11 | Photo carousel visibility, startup routing, resource IDs |
| `LockPolicyTest.kt` | 11 | All lock modes, auto-relock, one-time/recurring lockdown, day consumption |
| `MainViewModelTest.kt` | 5 | Group loading, pause/delete, premium status |
| `TimeManagerTest.kt` | 7 | Time remaining calc, midnight boundaries, periodic vs daily reset |
| `AppLimitEvaluatorTest.kt` | 9 | Day filtering, time ranges (incl. overnight), pause, outside-range blocking |
| `AppTickAccessibilityServiceTest.kt` | 21 | Staleness boundary (9s ok, 10.001s stale), service lifecycle, floating detection, split-screen |
| `BackgroundCheckerBubbleCountdownTest.kt` | 4 | Bubble format (MM:SS under 1min, HH:MM over), hide logic |
| `NotificationGroupSelectionTest.kt` | 12 | Group ranking (lowest remaining wins), limitEach effective time, multi-profile, formatting |
| `AppLimitBackupManagerTest.kt` | 6 | JSON round-trip, obfuscated keys, schema v1→v3, null handling, runtime state clearing |
| `ConvertersCompatibilityTest.kt` | 3 | Obfuscated JSON key parsing for Room TypeConverters |
| `LegacyAppLimitLineParserTest.kt` | 4 | Legacy format parsing, day conversion, empty lists, malformed input |
| `AppLimitPersistenceNormalizationTest.kt` | 9 | Duplicate reset, config preservation, time remaining init/cap, daily vs periodic reset |
| `AppSelectionUtilsTest.kt` | 3 | Package-based matching, deduplication, toggle behavior |
| `LockdownSummaryFormatterTest.kt` | 4 | One-time date format, missing/past dates, recurring day sorting, invalid days |
| `ExampleUnitTest.kt` | 1 | Template (2+2=4) |

### Integration Tests (19 files, ~65 test methods)

Located in `app/src/androidTest/java/com/juliacai/apptick/`

| File | Tests | What it verifies |
|------|-------|------------------|
| `AccessibilityBlockingIntegrationTest.kt` | 13 | **End-to-end blocking flow**: time decrement with/without accessibility, per-app tracking, floating window handling, PiP, re-launch on each cycle |
| `BackgroundCheckerTest.kt` | 14 | **Service time tracking**: decrement, blocking, reset (daily/periodic/cumulative), per-app usage, zero limits, outside time range, cross-expiry in single tick |
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
| Block screen re-launches each cycle | AccessibilityBlockingIntegrationTest (tests 10-11) |
| Time tracking accuracy | BackgroundCheckerTest (tests 1-5, 11-13) |
| Reset logic (daily/periodic/cumulative) | BackgroundCheckerTest (tests 11-13) |

### Test infrastructure patterns

**Unit tests:**
- Pure logic tests (no context): LockPolicyTest, AppLimitEvaluatorTest, TimeManagerTest
- Mocked SharedPreferences: ChangelogTest, MainViewModelTest
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

All stored in `"groupPrefs"` (Context.MODE_PRIVATE).

### App Settings
| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `premium` | Boolean | false | Premium mode active |
| `debug_force_free` | Boolean | false | Debug: force free mode |
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
| `groupDetailsHintSeenVersion` | Int | Group hint shown for version |
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
