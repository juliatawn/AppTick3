# 13. Test Suite

## Unit Tests (25 files, ~244 test methods)

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
| `AppLimitPersistenceNormalizationTest.kt` | 22 | Duplicate reset, config preservation, time remaining init/cap, daily vs periodic reset, aligned reset time |
| `DailyUsagePreviewTest.kt` | 20 | Daily usage preview: time range minutes, user example (8am-5pm hourly 1min = 9min), full day, partial periods, overnight ranges, format output |
| `TimeManagerAlignedResetTest.kt` | 12 | Aligned reset grid: midnight anchor, time range anchor, before-range, multiple ranges, exact grid point, future guarantee |
| `AppSelectionUtilsTest.kt` | 3 | Package-based matching, deduplication, toggle behavior |
| `LockdownSummaryFormatterTest.kt` | 4 | One-time date format, missing/past dates, recurring day sorting, invalid days |
| `AutoAddAppsTest.kt` | 17 | Category mapping, default values, merge logic, receiver decision logic |
| `UsagePeriodTest.kt` | 6 | UsagePeriod enum values, labels, calendar fields, ordering |
| `FormatUsageDurationTest.kt` | 10 | Duration formatting: zero, negative, sub-minute, minutes, hours, edge cases |
| `FormatUsageDurationShortTest.kt` | 8 | Compact duration formatting for calendar cells: empty, minutes, hours compact |
| `PeriodLabelTest.kt` | 16 | Period labels (Today/This Week/Last Month/N Ago), date range formatting, period range ordering |
| `DailyUsageStatsEntityTest.kt` | 6 | Entity fields, toDateString ISO format, month padding, December edge, composite key uniqueness |
| `ExampleUnitTest.kt` | 1 | Template (2+2=4) |

## Integration Tests (27 files, ~139 test methods)

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
| `AutoAddDaoIntegrationTest.kt` | 8 | Auto-add mode persistence, domain ↔ entity mapping, multi-group independence |
| `AppLimitGroupDaoTest.kt` | 7 | CRUD operations, active count, app search |
| `LegacyMigrationIntegrationTest.kt` | 5 | File→Room migration, idempotency, app name repair, lock pref migration |
| `ReceiverLegacyMigrationInstrumentationTest.kt` | 1 | Full startup migration pipeline |
| `AppLimitGroupItemTest.kt` | 7 | Card UI: lock mode, pause state, time display, collapse |
| `SetTimeLimitsScreenTest.kt` | 1 | Screen composition verification |
| `SetTimeLimitsPreviewIntegrationTest.kt` | 5 | Daily usage preview display, default Block Apps mode, periodic reset preview text, no-preview conditions |
| `AppSelectScreenKeyboardIntegrationTest.kt` | 1 | IME action clears focus |
| `PremiumModeScreenTest.kt` | 4 | Lock mode interactions, free vs premium messaging |
| `PremiumModeInfoScreenTest.kt` | 1 | Feature list display |
| `ColorPickerScreenTest.kt` | 2 | Removed UI elements, swatch-only picker |
| `AppLimitGroupsListDragTest.kt` | 4 | Drag-and-drop reordering: card follows finger, auto-scroll, order persistence |
| `LimitEditTimeBalanceSyncIntegrationTest.kt` | 3 | Time balance sync on limit edit via normalizeGroupForPersistence |
| `ChangelogDialogTest.kt` | 1 | @Ignore (flaky on device farms) |
| `AppUsagePageTest.kt` | 10 | App usage page composables: header card, detail card (all periods, averages), overview card (labels, values, empty state) |
| `AppUsageBreakdownTest.kt` | 14 | Weekly breakdown (title, day labels, usage values, empty), month calendar (month name, day headers, empty), yearly breakdown (title, month labels, empty), overview daily averages |
| `PeriodNavigatorTest.kt` | 8 | Period navigator: labels at offset 0/1/N, next button enabled/disabled, callback invocation, date range display |
| `DailyUsageStatsDaoTest.kt` | 11 | DAO: upsert, replace, batch insert, range queries, sum, count, delete old, lexicographic sort |
| `GroupAppItemClickTest.kt` | 5 | GroupAppItem onClick callback, null onClick safety, usage info display, shared time remaining |
| `ExampleInstrumentedTest.kt` | 1 | Package name verification |

## Critical invariants verified by tests

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

## Test infrastructure patterns

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

## Running tests

```bash
# Unit tests only
./gradlew testDebugUnitTest

# Instrumentation tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# CI tasks
./gradlew ciHost    # compile + unit tests + assemble instrumentation APK
./gradlew ciDevice  # run connected instrumentation tests
```
