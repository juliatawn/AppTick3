# 3. Core Blocking Pipeline

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

## Key timing constants (`BackgroundChecker.kt`)

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
