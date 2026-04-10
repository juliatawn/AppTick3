# 6. Data Layer

## Database Schema (Room v10)

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
| autoAddMode | TEXT | 'NONE' | v9 |
| includeExistingApps | INTEGER | 1 | v9 |

*\*`resetHours` column name is legacy; the field is named `resetMinutes` in the entity and stores minutes.*

**Migrations:** v1-4 → v5 (unified), v5→v6, v6→v7, v7→v8, v8→v9, v9→v10 (incremental).

### Table: `daily_usage_stats` (added in v10)

Stores per-app daily usage data locally, bypassing Android's ~7-10 day `INTERVAL_DAILY` retention limit. Controlled by the "Store Long-Term Usage Stats" setting (`storeLongTermUsageStats` in SharedPreferences, default `true`).

| Column | Type | Notes |
|--------|------|-------|
| dateString | TEXT PK | ISO format "yyyy-MM-dd" (1-based month) |
| packageName | TEXT PK | Composite PK with dateString |
| appName | TEXT | Display name (survives uninstalls) |
| totalForegroundMs | INTEGER | Total foreground time for that day |

**Files:** `data/DailyUsageStatsEntity.kt`, `data/DailyUsageStatsDao.kt`

**Recording:** `BackgroundChecker` snapshots today's per-app usage from Android's `INTERVAL_DAILY` into local DB every ~15 minutes. On first enable, backfills the past 7 days from whatever Android still has.

**Querying:** `AppUsageStats` has `*Local()` suspend methods (e.g., `getUsageForPeriodLocal`, `getWeeklyDailyBreakdownLocal`) that check local DB first, falling back to Android when no local data exists. The original non-suspend methods remain for backward compatibility.

**Toggle off behavior:** Recording stops immediately. Existing data is preserved (not deleted). Queries fall back to Android's UsageStatsManager. User can re-enable later and data resumes accumulating.

## `AppLimitGroupEntity.kt`

Room entity with `@SerializedName` annotations using single-char alternates (a-x) for compact JSON backup serialization.

## `AppLimitGroupDao.kt`

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

## `Converters.kt`

Room TypeConverters using Gson for JSON serialization of:
- `List<Int>` (weekDays)
- `List<AppInGroup>` (apps)
- `List<AppUsageStat>` (perAppUsage)
- `List<TimeRange>` (timeRanges)

All use `@SerializedName` with single-char obfuscated keys for compact storage.

## `Mapper.kt`

Extension functions for Entity ↔ Domain conversion:
- `AppLimitGroupEntity.toDomainModel()` → `AppLimitGroup`
  - Uses `effectiveTimeRanges()` to bridge v5-7 (4 columns) to v8 (JSON array)
- `AppLimitGroup.toEntity()` → `AppLimitGroupEntity`
  - Extracts first TimeRange to legacy columns for backward compatibility

## `LegacyDataMigrator.kt`

Migrates old `appLimitPrefs` file format to Room:
- `migrate()` → reads legacy file, parses lines, deduplicates, inserts to DB, deletes file
- `normalizeStoredAppDisplayNamesIfNeeded()` → fixes apps where display name equals package name
- `LegacyAppLimitLineParser.parseLineToEntity()` → parses colon-separated legacy format
  - Converts Calendar-style day-of-week (Sun=1) to ISO (Mon=1)
  - Converts legacy resetHours to resetMinutes

## `LegacyLockPrefsMigrator.kt`

Migrates old lock preferences (boolean `password` key) to new system (`active_lock_mode` string).

## `AppLimitBackupManager.kt`

JSON backup/restore with schema versioning:
- `createBackup()` → strips runtime state (timeRemaining, perAppUsage, etc.)
- `fromJson()` → handles schema v1-3, obfuscated keys, legacy time range conversion
- `collectAppSettings()` / `applyAppSettings()` → SharedPreferences backup
- `writeBackupToUri()` / `readBackupFromUri()` → ContentResolver I/O

## `GroupCardOrderStore.kt`

Persists drag-and-drop card order as JSON array of group IDs in SharedPreferences:
- `sanitizeOrder(saved, available)` → removes deleted IDs, appends new ones
- `applyOrder(items, savedOrder, idSelector)` → generic reorder function
