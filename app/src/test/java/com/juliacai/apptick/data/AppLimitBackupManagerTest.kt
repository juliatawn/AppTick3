package com.juliacai.apptick.data

import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import org.junit.Test

class AppLimitBackupManagerTest {

    @Test
    fun backupJson_roundTrip_preservesGroupsAndPreferences() {
        // Verifies settings survive export while runtime usage state is intentionally excluded.
        val group = AppLimitGroupEntity(
            id = 12L,
            name = "School Hours",
            timeHrLimit = 1,
            timeMinLimit = 45,
            limitEach = true,
            resetMinutes = 90,
            weekDays = listOf(1, 2, 3, 4, 5),
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", null)),
            paused = false,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            startHour = 8,
            startMinute = 0,
            endHour = 16,
            endMinute = 30,
            cumulativeTime = false,
            timeRemaining = 3_600_000L,
            nextResetTime = 123456789L,
            nextAddTime = 0L,
            perAppUsage = listOf(AppUsageStat("com.google.android.youtube", 120_000L))
        )

        val backup = AppLimitBackupManager.createBackup(
            groups = listOf(group),
            appSettings = BackupAppSettings(
                showTimeLeft = false,
                floatingBubbleEnabled = true,
                darkModeEnabled = true,
                customColorModeEnabled = true,
                customPrimaryColor = 111,
                customAccentColor = 222,
                customBackgroundColor = 333,
                customCardColor = 444,
                customIconColor = 555,
                appIconColorMode = "custom"
            )
        )
        val json = AppLimitBackupManager.toJson(backup)

        val parsed = AppLimitBackupManager.fromJson(json)

        assertThat(parsed.schemaVersion).isEqualTo(1)
        assertThat(parsed.groups).hasSize(1)
        assertThat(parsed.groups.first()).isEqualTo(
            group.copy(
                timeRemaining = 0L,
                nextResetTime = 0L,
                nextAddTime = 0L,
                perAppUsage = emptyList()
            )
        )
        assertThat(parsed.appSettings.showTimeLeft).isFalse()
        assertThat(parsed.appSettings.floatingBubbleEnabled).isTrue()
        assertThat(parsed.appSettings.darkModeEnabled).isTrue()
        assertThat(parsed.appSettings.customColorModeEnabled).isTrue()
        assertThat(parsed.appSettings.customPrimaryColor).isEqualTo(111)
        assertThat(parsed.appSettings.appIconColorMode).isEqualTo("custom")
    }

    @Test
    fun fromJson_withoutPreferences_usesDefaults() {
        // Older backups may not contain preference fields; defaults keep behavior stable.
        val json = """
            {
              "schemaVersion": 1,
              "groups": []
            }
        """.trimIndent()

        val parsed = AppLimitBackupManager.fromJson(json)

        assertThat(parsed.groups).isEmpty()
        assertThat(parsed.appSettings.showTimeLeft).isTrue()
        assertThat(parsed.appSettings.floatingBubbleEnabled).isFalse()
        assertThat(parsed.appSettings.darkModeEnabled).isFalse()
        assertThat(parsed.appSettings.customColorModeEnabled).isFalse()
    }
}
