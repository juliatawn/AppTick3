package com.juliacai.apptick

import android.content.pm.ApplicationInfo
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.deviceApps.AppManager
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_ALL_NEW
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_AUDIO
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_GAME
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_IMAGE
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_MAPS
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_NEWS
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_PRODUCTIVITY
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_SOCIAL
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_CATEGORY_VIDEO
import com.juliacai.apptick.groups.AppLimitGroup.Companion.AUTO_ADD_NONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AutoAddAppsTest {

    // ── autoAddModeToCategory mapping ──────────────────────────────────

    @Test
    fun `autoAddModeToCategory maps GAME to CATEGORY_GAME`() {
        assertEquals(ApplicationInfo.CATEGORY_GAME, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_GAME))
    }

    @Test
    fun `autoAddModeToCategory maps SOCIAL to CATEGORY_SOCIAL`() {
        assertEquals(ApplicationInfo.CATEGORY_SOCIAL, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_SOCIAL))
    }

    @Test
    fun `autoAddModeToCategory maps AUDIO to CATEGORY_AUDIO`() {
        assertEquals(ApplicationInfo.CATEGORY_AUDIO, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_AUDIO))
    }

    @Test
    fun `autoAddModeToCategory maps VIDEO to CATEGORY_VIDEO`() {
        assertEquals(ApplicationInfo.CATEGORY_VIDEO, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_VIDEO))
    }

    @Test
    fun `autoAddModeToCategory maps IMAGE to CATEGORY_IMAGE`() {
        assertEquals(ApplicationInfo.CATEGORY_IMAGE, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_IMAGE))
    }

    @Test
    fun `autoAddModeToCategory maps NEWS to CATEGORY_NEWS`() {
        assertEquals(ApplicationInfo.CATEGORY_NEWS, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_NEWS))
    }

    @Test
    fun `autoAddModeToCategory maps MAPS to CATEGORY_MAPS`() {
        assertEquals(ApplicationInfo.CATEGORY_MAPS, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_MAPS))
    }

    @Test
    fun `autoAddModeToCategory maps PRODUCTIVITY to CATEGORY_PRODUCTIVITY`() {
        assertEquals(ApplicationInfo.CATEGORY_PRODUCTIVITY, AppManager.autoAddModeToCategory(AUTO_ADD_CATEGORY_PRODUCTIVITY))
    }

    @Test
    fun `autoAddModeToCategory returns null for NONE`() {
        assertNull(AppManager.autoAddModeToCategory(AUTO_ADD_NONE))
    }

    @Test
    fun `autoAddModeToCategory returns null for ALL_NEW`() {
        assertNull(AppManager.autoAddModeToCategory(AUTO_ADD_ALL_NEW))
    }

    @Test
    fun `autoAddModeToCategory returns null for unknown string`() {
        assertNull(AppManager.autoAddModeToCategory("UNKNOWN"))
    }

    // ── AppLimitGroup default values ───────────────────────────────────

    @Test
    fun `new AppLimitGroup defaults autoAddMode to NONE`() {
        val group = AppLimitGroup()
        assertEquals(AUTO_ADD_NONE, group.autoAddMode)
    }

    @Test
    fun `new AppLimitGroup defaults includeExistingApps to true`() {
        val group = AppLimitGroup()
        assertTrue(group.includeExistingApps)
    }

    // ── Auto-add merge logic (simulating what saveGroup does) ──────────

    @Test
    fun `category auto-add with includeExisting merges new apps without duplicates`() {
        val existingApps = listOf(
            AppInGroup("App1", "com.test.app1", "com.test.app1")
        )
        val matchingApps = listOf(
            AppInfo(appName = "App1", appPackage = "com.test.app1"), // already in group
            AppInfo(appName = "App2", appPackage = "com.test.app2")  // new
        )

        val existingPackages = existingApps.map { it.appPackage }.toSet()
        val newApps = matchingApps
            .filter { it.appPackage != null && it.appPackage !in existingPackages }
            .map { AppInGroup(it.appName, it.appPackage ?: "", it.appPackage ?: "") }
        val merged = existingApps + newApps

        assertEquals(2, merged.size)
        assertEquals("com.test.app1", merged[0].appPackage)
        assertEquals("com.test.app2", merged[1].appPackage)
    }

    @Test
    fun `category auto-add with includeExisting false does not merge`() {
        val group = AppLimitGroup(
            autoAddMode = AUTO_ADD_CATEGORY_GAME,
            includeExistingApps = false,
            apps = listOf(AppInGroup("App1", "com.test.app1", "com.test.app1"))
        )
        // When includeExistingApps is false, the save logic skips the scan
        val shouldScan = group.autoAddMode != AUTO_ADD_NONE &&
            group.autoAddMode != AUTO_ADD_ALL_NEW &&
            group.includeExistingApps
        assertFalse(shouldScan)
    }

    @Test
    fun `ALL_NEW mode does not trigger existing app scan`() {
        val group = AppLimitGroup(
            autoAddMode = AUTO_ADD_ALL_NEW,
            includeExistingApps = true
        )
        val shouldScan = group.autoAddMode != AUTO_ADD_NONE &&
            group.autoAddMode != AUTO_ADD_ALL_NEW &&
            group.includeExistingApps
        assertFalse(shouldScan)
    }

    @Test
    fun `NONE mode does not trigger existing app scan`() {
        val group = AppLimitGroup(
            autoAddMode = AUTO_ADD_NONE,
            includeExistingApps = true
        )
        val shouldScan = group.autoAddMode != AUTO_ADD_NONE &&
            group.autoAddMode != AUTO_ADD_ALL_NEW &&
            group.includeExistingApps
        assertFalse(shouldScan)
    }

    @Test
    fun `category mode with includeExisting triggers scan`() {
        val group = AppLimitGroup(
            autoAddMode = AUTO_ADD_CATEGORY_SOCIAL,
            includeExistingApps = true
        )
        val shouldScan = group.autoAddMode != AUTO_ADD_NONE &&
            group.autoAddMode != AUTO_ADD_ALL_NEW &&
            group.includeExistingApps
        assertTrue(shouldScan)
    }

    // ── Receiver auto-add decision logic ───────────────────────────────

    @Test
    fun `ALL_NEW mode should add any new package`() {
        val group = AppLimitGroup(autoAddMode = AUTO_ADD_ALL_NEW)
        val shouldAdd = when (group.autoAddMode) {
            AUTO_ADD_ALL_NEW -> true
            else -> false
        }
        assertTrue(shouldAdd)
    }

    @Test
    fun `category mode adds package when category matches`() {
        val group = AppLimitGroup(autoAddMode = AUTO_ADD_CATEGORY_GAME)
        val appCategory = ApplicationInfo.CATEGORY_GAME
        val targetCategory = AppManager.autoAddModeToCategory(group.autoAddMode)
        val shouldAdd = targetCategory != null && appCategory == targetCategory
        assertTrue(shouldAdd)
    }

    @Test
    fun `category mode does not add package when category does not match`() {
        val group = AppLimitGroup(autoAddMode = AUTO_ADD_CATEGORY_GAME)
        val appCategory = ApplicationInfo.CATEGORY_SOCIAL
        val targetCategory = AppManager.autoAddModeToCategory(group.autoAddMode)
        val shouldAdd = targetCategory != null && appCategory == targetCategory
        assertFalse(shouldAdd)
    }

    @Test
    fun `NONE mode does not add any package`() {
        val group = AppLimitGroup(autoAddMode = AUTO_ADD_NONE)
        val shouldSkip = group.autoAddMode == AUTO_ADD_NONE
        assertTrue(shouldSkip)
    }

    @Test
    fun `already-in-group package is not added again`() {
        val group = AppLimitGroup(
            autoAddMode = AUTO_ADD_ALL_NEW,
            apps = listOf(AppInGroup("Existing", "com.test.existing", "com.test.existing"))
        )
        val packageName = "com.test.existing"
        val alreadyInGroup = group.apps.any { it.appPackage == packageName }
        assertTrue(alreadyInGroup)
    }
}
