package com.juliacai.apptick.data

import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.groups.TimeRange
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
                appIconColorMode = "custom",
                groupCardOrder = listOf(12L, 99L)
            )
        )
        val json = AppLimitBackupManager.toJson(backup)

        val parsed = AppLimitBackupManager.fromJson(json)

        assertThat(parsed.schemaVersion).isEqualTo(3)
        assertThat(parsed.groups).hasSize(1)
        assertThat(parsed.groups.first()).isEqualTo(
            group.copy(
                timeRemaining = 0L,
                nextResetTime = 0L,
                nextAddTime = 0L,
                perAppUsage = emptyList(),
                timeRanges = listOf(TimeRange(8, 0, 16, 30))
            )
        )
        assertThat(parsed.appSettings.showTimeLeft).isFalse()
        assertThat(parsed.appSettings.floatingBubbleEnabled).isTrue()
        assertThat(parsed.appSettings.darkModeEnabled).isTrue()
        assertThat(parsed.appSettings.customColorModeEnabled).isTrue()
        assertThat(parsed.appSettings.customPrimaryColor).isEqualTo(111)
        assertThat(parsed.appSettings.appIconColorMode).isEqualTo("custom")
        assertThat(parsed.appSettings.groupCardOrder).containsExactly(12L, 99L).inOrder()
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
        assertThat(parsed.appSettings.groupCardOrder).isNull()
    }

    @Test
    fun backupJson_roundTrip_preservesAppOrderInsideGroup() {
        val appsInOrder = listOf(
            AppInGroup("Instagram", "com.instagram.android", null),
            AppInGroup("TikTok", "com.zhiliaoapp.musically", null),
            AppInGroup("YouTube", "com.google.android.youtube", null)
        )
        val group = AppLimitGroupEntity(
            id = 42L,
            name = "Social",
            apps = appsInOrder
        )

        val backup = AppLimitBackupManager.createBackup(
            groups = listOf(group),
            appSettings = BackupAppSettings(
                showTimeLeft = true,
                floatingBubbleEnabled = false,
                darkModeEnabled = false,
                customColorModeEnabled = false,
                customPrimaryColor = null,
                customAccentColor = null,
                customBackgroundColor = null,
                customCardColor = null,
                customIconColor = null,
                appIconColorMode = null,
                groupCardOrder = listOf(42L)
            )
        )

        val parsed = AppLimitBackupManager.fromJson(AppLimitBackupManager.toJson(backup))

        assertThat(parsed.groups).hasSize(1)
        assertThat(parsed.groups.first().apps.map { it.appPackage })
            .containsExactly(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.google.android.youtube"
            )
            .inOrder()
    }

    @Test
    fun fromJson_legacyTimeRange_populatesTimeRangesFromStartEnd() {
        val json = """
            {
              "schemaVersion": 3,
              "groups": [
                {
                  "id": 7,
                  "name": "Legacy",
                  "timeHrLimit": 1,
                  "timeMinLimit": 0,
                  "limitEach": false,
                  "resetHours": 0,
                  "weekDays": [],
                  "apps": [],
                  "paused": false,
                  "useTimeRange": true,
                  "blockOutsideTimeRange": true,
                  "startHour": 9,
                  "startMinute": 15,
                  "endHour": 17,
                  "endMinute": 45,
                  "cumulativeTime": false,
                  "timeRemaining": 0,
                  "nextResetTime": 0,
                  "nextAddTime": 0,
                  "perAppUsage": [],
                  "isExpanded": true
                }
              ],
              "appSettings": {
                "showTimeLeft": true,
                "floatingBubbleEnabled": false,
                "darkModeEnabled": false,
                "customColorModeEnabled": false
              }
            }
        """.trimIndent()

        val parsed = AppLimitBackupManager.fromJson(json)

        assertThat(parsed.groups).hasSize(1)
        assertThat(parsed.groups.first().timeRanges).containsExactly(TimeRange(9, 15, 17, 45))
    }
}
