# 8. App Limit Evaluation

## `AppLimitEvaluator.kt` — Pure logic, no side effects

**File:** `appLimit/AppLimitEvaluator.kt`

| Method | Logic |
|--------|-------|
| `shouldCheckLimit(group, now)` | Returns false if paused, wrong day, or outside time range |
| `isWithinActiveDays(group, now)` | Empty weekDays = always active. Converts Calendar day to Mon=1 format |
| `isWithinTimeRange(group, now)` | Delegates to `isNowWithinAnyTimeRange()` from TimeRangeUtils |
| `shouldBlockOutsideTimeRange(group, now)` | True when useTimeRange + blockOutsideTimeRange + NOT in range |
| `isLimitReached(group)` | `timeRemaining <= 0L` |

## `TimeRangeUtils.kt`

- `getConfiguredTimeRanges()` → returns timeRanges list, or constructs single range from legacy fields
- `isNowWithinTimeRange(range, nowMinutes)` → handles midnight-crossing ranges (e.g., 22:00→06:00)
- `isNowWithinAnyTimeRange(ranges, nowMillis)` → returns true if empty list (always active)

## `TimeManager.kt`

- `getTimeRemaining()` → converts hours+minutes to milliseconds
- `getNextResetTime()` → periodic (now + resetMinutes) or daily (next midnight)
- `nextAlignedResetTime(resetIntervalMinutes, useTimeRange, timeRanges, nowMillis)` → computes next periodic reset aligned to a grid anchored at the time range start (or midnight if no time range). E.g., range start 8:00 + 60min interval → resets at 9:00, 10:00, 11:00…
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

## Day-of-week convention

AppTick uses **Monday=1, Sunday=7** (ISO 8601 style).
Android's `Calendar.DAY_OF_WEEK` uses **Sunday=1, Saturday=7**.
Conversion: `((cal.get(DAY_OF_WEEK) + 5) % 7) + 1`
