# 7. Domain Models & Groups

## `AppLimitGroup.kt` — Primary domain model

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
    val isExpanded: Boolean,        // UI collapse state
    val autoAddMode: String,        // NONE, ALL_NEW, GAME, SOCIAL, AUDIO, VIDEO, IMAGE, NEWS, MAPS, PRODUCTIVITY
    val includeExistingApps: Boolean // When category mode: also add already-installed matching apps on save
) : Serializable
```

## `AppInGroup.kt`

```kotlin
data class AppInGroup(
    val appName: String,      // Display name
    val appPackage: String,   // Package name
    val appIcon: String?      // Unused (icons loaded dynamically)
) : Serializable
```

## `AppUsageStat.kt`

```kotlin
data class AppUsageStat(
    val appPackage: String,   // Package name
    val usedMillis: Long      // Milliseconds used since last reset
)
```

## `TimeRange.kt`

```kotlin
data class TimeRange(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int = 23,
    val endMinute: Int = 59
)
```

## `AppLimitGroupItem.kt` — Group card composable

Collapsible card showing:
- Header: name, lock/pause/edit buttons, expand chevron
- App icons (horizontal LazyRow)
- Time remaining and configured limit
- Expanded details: active days, time ranges, reset schedule, cumulative indicator

Key formatting functions:
- `formatConfiguredTimeLimit()` → "X hr Y min" or "Blocks"
- `totalGroupTimeLeftMillis()` → calculates remaining considering limitEach mode
- `isGroupCurrentlyLimited()` → checks day + time range activity

## `AppLimitGroups.kt` — Draggable list

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

## `DragAfterLongHold.kt` — Long-hold ViewConfiguration wrapper

`LongHoldDragArea` composable overrides `LocalViewConfiguration.longPressTimeoutMillis`
so that `detectDragGesturesAfterLongPress` requires a longer hold (default 500 ms).
Used by both `AppLimitGroups.kt` and `GroupPage.kt` drag-and-drop lists.

## `AppLimitGroupItem.kt` — Drag-safe Card

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

## `GroupPage.kt` — App card reordering

Same drag UX as group cards: `LongHoldDragArea` wrapper, wiggle + scale + haptic feedback,
direct-formula offset (drift-proof). Uses `draggedCenterY` for auto-scroll and swap triggers
(app cards are uniform height so center-based works fine).
Hint card reads: "Hold down on a card until it wiggles, then drag to reorder."
