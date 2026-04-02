# 1. Project Overview

AppTick is an Android app-blocking application. Users create **app limit groups** — collections of apps with shared or per-app time limits, optional time ranges, weekly schedules, and reset periods. When a user exceeds their configured limit, AppTick launches a fullscreen **block screen** on top of the app.

**Build config:** Android SDK 36, min SDK 27, Kotlin + Jetpack Compose + Room + Coroutines.

**Key dependencies:** Room (database), Gson (serialization), Coil (image loading), Google Play Billing (premium), AndroidX Security-Crypto (encrypted prefs), Material3 (UI).

---

# 2. Package Structure

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
