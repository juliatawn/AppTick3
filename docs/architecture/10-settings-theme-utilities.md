# 11. Settings & Theme

## `AppTheme.kt`

- `customPalette(seedColor)` → generates ThemePalette using ColorUtils blending
- `resolveSeedColor(ctx)` → reads custom color from prefs, defaults to #6F34AD
- `currentPalette(ctx)` → resolves dark/light + custom/default palette
- `colorSchemeFromPalette(palette)` → converts to Material3 ColorScheme
- `applyTheme(activity)` → sets window background and bar appearance

## `ThemeModeManager.kt`

- `apply(ctx)` → reads dark mode pref, calls AppCompatDelegate.setDefaultNightMode
- `persistDarkMode(ctx, enabled)` → saves and applies
- Broadcasts "COLORS_CHANGED" to recreate activities

## `SettingsScreen.kt` (~850 lines)

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

# 12. Device Apps & Utilities

## `AppManager.kt`

Queries PackageManager for installed apps with launcher intent. Returns `List<AppInfo>`.

## `AppListViewModel.kt`

ViewModel with search filtering over installed apps. Exposes `filteredApps: LiveData<List<AppInfo>>`.

## `AppUsageStats.kt`

Queries `UsageStatsManager.queryUsageStats()` for daily usage data. Returns sorted list by usage time.

## `BatteryOptimizationHelper.kt`

Checks battery optimization status, detects OEM restrictions (Samsung, Xiaomi, Huawei, OnePlus, etc.), provides OEM-specific guidance text.

## `PermissionOnboardingScreen.kt`

Single-page permissions list (Overlay, Usage Access, Notifications — required; Accessibility — optional). Each row shows a one-line "why", a Required/Optional tag, and an Allow button that flips to a check when granted. State refreshes on lifecycle resume so returning from system settings updates the UI automatically. Each Allow deep-links to AppTick's row in the relevant settings screen (package-URI intents for overlay/notifications/usage access; `:settings:fragment_args_key` for accessibility). Top of page includes a disclaimer that all data stays on-device.

## `FeaturePhotoCarousel.kt`

Swipeable onboarding carousel (7 photos). Shown once per version.
