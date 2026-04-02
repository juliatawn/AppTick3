# 9. UI Layer

## Navigation (MainActivity.kt)

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

## Create/Edit Group Flow

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

## `AppLimitViewModel.kt`

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

## Block Screen

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

## MainScreen.kt

Scaffold with:
- TopAppBar (title, "LOCK NOW" button, lock icon, settings)
  - "LOCK NOW" text button appears only when user is in Lockdown mode but currently unlocked; triggers the relock dialog
- FAB (add new group)
- Battery warning card (with system settings buttons)
- Group details hint (one-time)
- List content (delegated to AppLimitGroups)
