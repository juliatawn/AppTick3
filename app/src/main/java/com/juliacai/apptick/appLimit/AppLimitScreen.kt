package com.juliacai.apptick.appLimit

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppLimitGroupItem

@Composable
fun AppLimitScreen(groups: List<AppLimitGroup>) {
    LazyColumn {
        items(groups) { group ->
            AppLimitGroupItem(
                group = group,
                isEditingLocked = false,
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppLimitScreenPreview() {
    val sampleGroups = listOf(
        AppLimitGroup(
            id = 1,
            name = "Social Media",
            apps = listOf(
                AppInGroup(
                    appName = "Instagram",
                    appPackage = "com.instagram.android",
                    appIcon = "com.instagram.android"
                ),
                AppInGroup(
                    appName = "Facebook",
                    appPackage = "com.facebook.katana",
                    appIcon = "com.facebook.katana"
                ),
            ),
            timeHrLimit = 1,
            timeMinLimit = 30,
            limitEach = false,
            weekDays = listOf(1, 2, 3, 4, 5, 6, 7),
            useTimeRange = false,
            startHour = 0,
            startMinute = 0,
            endHour = 0,
            endMinute = 0,
            cumulativeTime = true,
            resetMinutes = 24,
            paused = false
        ),
        AppLimitGroup(
            id = 2,
            name = "Games",
            apps = listOf(
                AppInGroup(
                    appName = "Clash of Clans",
                    appPackage = "com.supercell.clashofclans",
                    appIcon = "com.supercell.clashofclans"
                ),
                AppInGroup(
                    appName = "Candy Crush",
                    appPackage = "com.king.candycrushsaga",
                    appIcon = "com.king.candycrushsaga"
                ),
            ),
            timeHrLimit = 2,
            timeMinLimit = 0,
            limitEach = true,
            weekDays = listOf(6, 7),
            useTimeRange = true,
            startHour = 18,
            startMinute = 0,
            endHour = 22,
            endMinute = 0,
            cumulativeTime = false,
            resetMinutes = 0,
            paused = true
        )
    )
    AppLimitScreen(groups = sampleGroups)
}
