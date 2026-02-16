package com.juliacai.apptick.data

import android.content.Context
import android.util.Log
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import java.io.File

class LegacyDataMigrator(
    private val context: Context,
    private val appLimitGroupDao: AppLimitGroupDao
) {

    suspend fun migrate() {
        val legacyFile = File(context.filesDir, "appLimitPrefs")
        if (!legacyFile.exists()) {
            Log.i("LegacyDataMigrator", "No legacy data file found.")
            return
        }

        try {
            val lines = legacyFile.readLines()
            for (line in lines) {
                if (line.isNotBlank()) {
                    val entity = parseLineToEntity(line)
                    if (entity != null) {
                        appLimitGroupDao.insertAppLimitGroup(entity)
                    }
                }
            }
            legacyFile.delete()
            Log.i("LegacyDataMigrator", "Legacy data migrated successfully.")
        } catch (e: Exception) {
            Log.e("LegacyDataMigrator", "Error migrating legacy data", e)
        }
    }

    private fun parseLineToEntity(line: String): AppLimitGroupEntity? {
        try {
            val parts = line.split(":", limit = 10)
            if (parts.size < 10) return null

            // Legacy value: countGroup (Integer) - The purpose of this value is unknown.
            // val countGroup = parts[0].toInt()

            // Legacy value: startTime (Long) - The purpose of this value is unknown.
            // val startTime = parts[1].toLong()

            val timeHrLimit = parts[2].toInt()
            val timeMinLimit = parts[3].toInt()
            val limitEach = parts[4].toBoolean()

            val name = parts[5].ifBlank { "App Limit Group" }

            val resetHours = parts[6].toInt()

            val daysString = parts[7].removeSurrounding("[", "]")
            val weekDays = if (daysString.isBlank()) emptyList() else daysString.split(", ").mapNotNull { it.trim().toIntOrNull() }

            val appsString = parts[8].removeSurrounding("[", "]")
            val apps = if (appsString.isBlank()) {
                emptyList()
            } else {
                appsString.split(", ").map { appPackage -> AppInGroup(appPackage = appPackage.trim(), appName = appPackage.trim(), appIcon = "") }
            }

            val paused = parts[9].toBoolean()

            return AppLimitGroupEntity(
                name = name,
                timeHrLimit = timeHrLimit,
                timeMinLimit = timeMinLimit,
                limitEach = limitEach,
                resetHours = resetHours,
                weekDays = weekDays,
                apps = apps,
                paused = paused,
                // The following fields are not present in the legacy data and will be set to default values.
                useTimeRange = false,
                startHour = 0,
                startMinute = 0,
                endHour = 0,
                endMinute = 0,
                cumulativeTime = false,
                timeRemaining = 0,
                nextResetTime = com.juliacai.apptick.TimeManager.nextMidnight(),
                nextAddTime = 0,
                perAppUsage = emptyList<AppUsageStat>()
            )
        } catch (e: Exception) {
            Log.e("LegacyDataMigrator", "Error parsing line: $line", e)
            return null
        }
    }
}
