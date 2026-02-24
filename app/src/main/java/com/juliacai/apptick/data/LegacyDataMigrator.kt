package com.juliacai.apptick.data

import android.content.Context
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
                    val entity = LegacyAppLimitLineParser.parseLineToEntity(line) ?: continue
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
    }
}

internal object LegacyAppLimitLineParser {
    fun parseLineToEntity(line: String, nowMillis: Long = System.currentTimeMillis()): AppLimitGroupEntity? {
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
                    AppInGroup(
                        appPackage = appPackage.trim(),
                        appName = appPackage.trim(),
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
