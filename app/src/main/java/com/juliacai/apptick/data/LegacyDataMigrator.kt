package com.juliacai.apptick.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.juliacai.apptick.TimeManager
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LegacyDataMigrator(
    private val context: Context,
    private val appLimitGroupDao: AppLimitGroupDao
) {

    suspend fun migrate() {
        MIGRATION_MUTEX.withLock {
            migrateLegacyFileIfPresent()
            normalizeStoredAppDisplayNamesIfNeeded()
        }
    }

    private suspend fun migrateLegacyFileIfPresent() {
        val legacyFile = File(context.filesDir, "appLimitPrefs")
        if (!legacyFile.exists()) {
            Log.i("LegacyDataMigrator", "No legacy data file found.")
            return
        }

        try {
            val lines = legacyFile.readLines()
            val existingKeys = appLimitGroupDao
                .getAllAppLimitGroupsImmediate()
                .mapTo(mutableSetOf(), ::dedupeKey)

            var insertedCount = 0
            for (line in lines) {
                if (line.isBlank()) continue
                val entity = LegacyAppLimitLineParser.parseLineToEntity(
                    line = line,
                    appNameResolver = ::resolveInstalledAppLabel
                ) ?: continue
                val key = dedupeKey(entity)
                if (existingKeys.add(key)) {
                    appLimitGroupDao.insertAppLimitGroup(entity)
                    insertedCount++
                }
            }
            legacyFile.delete()
            Log.i(
                "LegacyDataMigrator",
                "Legacy data migration completed. Inserted $insertedCount new groups."
            )
        } catch (e: Exception) {
            Log.e("LegacyDataMigrator", "Error migrating legacy data", e)
        }
    }

    private suspend fun normalizeStoredAppDisplayNamesIfNeeded() {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_APP_NAME_REPAIR_DONE, false)) return

        try {
            val groups = appLimitGroupDao.getAllAppLimitGroupsImmediate()
            var repairedGroups = 0
            for (group in groups) {
                var repaired = false
                val updatedApps = group.apps.map { app ->
                    val resolvedName = resolveInstalledAppLabel(app.appPackage)
                    if (shouldRepairAppName(app.appName, app.appPackage) && !resolvedName.isNullOrBlank()) {
                        repaired = true
                        app.copy(appName = resolvedName)
                    } else {
                        app
                    }
                }
                if (repaired) {
                    appLimitGroupDao.updateAppLimitGroup(group.copy(apps = updatedApps))
                    repairedGroups++
                }
            }
            prefs.edit().putBoolean(KEY_APP_NAME_REPAIR_DONE, true).apply()
            Log.i(
                "LegacyDataMigrator",
                "App name normalization complete. Repaired $repairedGroups groups."
            )
        } catch (e: Exception) {
            Log.e("LegacyDataMigrator", "Error normalizing stored app names", e)
        }
    }

    private fun shouldRepairAppName(appName: String, appPackage: String): Boolean {
        return appName.isBlank() || appName == appPackage
    }

    private fun resolveInstalledAppLabel(appPackage: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(appPackage, 0)
            context.packageManager.getApplicationLabel(appInfo).toString().trim().ifBlank { null }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun dedupeKey(entity: AppLimitGroupEntity): String {
        val apps = entity.apps.map { it.appPackage.trim() }.sorted().joinToString("|")
        val days = entity.weekDays.sorted().joinToString(",")
        return listOf(
            entity.name.orEmpty().trim(),
            entity.timeHrLimit.toString(),
            entity.timeMinLimit.toString(),
            entity.limitEach.toString(),
            entity.resetMinutes.toString(),
            days,
            apps,
            entity.paused.toString()
        ).joinToString("::")
    }

    companion object {
        private val MIGRATION_MUTEX = Mutex()
        private const val MIGRATION_PREFS = "groupPrefs"
        private const val KEY_APP_NAME_REPAIR_DONE = "legacy_app_name_repair_done_v1"
    }
}

internal object LegacyAppLimitLineParser {
    fun parseLineToEntity(
        line: String,
        nowMillis: Long = System.currentTimeMillis(),
        appNameResolver: (String) -> String? = { null }
    ): AppLimitGroupEntity? {
        try {
            val parts = line.split(":", limit = 10)
            if (parts.size < 10) return null

            val startTimeMillis = parts[1].toLongOrNull() ?: 0L
            val timeHrLimit = parts[2].toIntOrNull() ?: 0
            val timeMinLimit = parts[3].toIntOrNull() ?: 0
            val limitEach = parts[4].toBooleanStrictOrNull() ?: parts[4].equals("true", ignoreCase = true)
            val name = parts[5].ifBlank { "App Limit Group" }

            // Legacy field 6 is reset HOURS. Current schema persists reset MINUTES.
            val resetMinutes = ((parts[6].toIntOrNull() ?: 0).coerceAtLeast(0)) * 60

            val legacyWeekDays = parseIntList(parts[7])
            val weekDays = legacyWeekDays
                .map(::legacyCalendarDayToMondayOne)
                .filter { it in 1..7 }
                .distinct()
                .sorted()

            val apps = parseStringList(parts[8])
                .filter { it.isNotBlank() }
                .map { appPackage ->
                    val normalizedPackage = appPackage.trim()
                    val resolvedName = appNameResolver(normalizedPackage).orEmpty().trim()
                    AppInGroup(
                        appPackage = normalizedPackage,
                        appName = resolvedName.ifBlank { normalizedPackage },
                        appIcon = ""
                    )
                }

            val paused = parts[9].toBooleanStrictOrNull() ?: parts[9].equals("true", ignoreCase = true)

            val limitInMillis = ((timeHrLimit * 60L) + timeMinLimit.toLong()).coerceAtLeast(0L) * 60_000L
            val nextResetTime = when {
                resetMinutes > 0 && startTimeMillis > 0L ->
                    startTimeMillis + (resetMinutes * 60_000L)
                resetMinutes > 0 ->
                    nowMillis + (resetMinutes * 60_000L)
                else -> TimeManager.nextMidnight(nowMillis)
            }

            return AppLimitGroupEntity(
                name = name,
                timeHrLimit = timeHrLimit,
                timeMinLimit = timeMinLimit,
                limitEach = limitEach,
                resetMinutes = resetMinutes,
                weekDays = weekDays,
                apps = apps,
                paused = paused,
                useTimeRange = false,
                startHour = 0,
                startMinute = 0,
                endHour = 0,
                endMinute = 0,
                cumulativeTime = false,
                timeRemaining = limitInMillis,
                nextResetTime = nextResetTime,
                nextAddTime = 0,
                perAppUsage = emptyList<AppUsageStat>()
            )
        } catch (e: Exception) {
            Log.e("LegacyDataMigrator", "Error parsing line: $line", e)
            return null
        }
    }

    private fun parseIntList(raw: String): List<Int> {
        val body = raw.removeSurrounding("[", "]").trim()
        if (body.isBlank()) return emptyList()
        return body.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun parseStringList(raw: String): List<String> {
        val body = raw.removeSurrounding("[", "]").trim()
        if (body.isBlank()) return emptyList()
        return body.split(",").map { it.trim() }
    }

    // Legacy file stored Calendar.DAY_OF_WEEK values where Sunday=1.
    private fun legacyCalendarDayToMondayOne(day: Int): Int {
        return when (day) {
            1 -> 7
            in 2..7 -> day - 1
            else -> day
        }
    }
}
