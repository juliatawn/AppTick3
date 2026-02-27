package com.juliacai.apptick.newAppLimit

import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.AppInfo
import org.junit.Test

class AppSelectionUtilsTest {

    @Test
    fun isAppSelected_matchesByPackage_whenObjectsDiffer() {
        val selected = listOf(
            AppInfo(appName = "Settings", appPackage = "com.android.settings")
        )
        val candidate = AppInfo(
            appName = "Settings",
            appPackage = "com.android.settings"
        )

        assertThat(isAppSelected(candidate, selected)).isTrue()
    }

    @Test
    fun toggleSelectedApp_removesExistingByPackage_insteadOfAddingDuplicate() {
        val selected = listOf(
            AppInfo(appName = "Settings", appPackage = "com.android.settings")
        )
        val candidate = AppInfo(
            appName = "Settings",
            appPackage = "com.android.settings"
        )

        val result = toggleSelectedApp(candidate, selected)

        assertThat(result).isEmpty()
    }

    @Test
    fun toggleSelectedApp_addsWhenNotSelected() {
        val selected = listOf(
            AppInfo(appName = "Calculator", appPackage = "com.android.calculator2")
        )
        val candidate = AppInfo(
            appName = "Settings",
            appPackage = "com.android.settings"
        )

        val result = toggleSelectedApp(candidate, selected)

        assertThat(result).hasSize(2)
        assertThat(result.any { it.appPackage == "com.android.settings" }).isTrue()
    }
}
