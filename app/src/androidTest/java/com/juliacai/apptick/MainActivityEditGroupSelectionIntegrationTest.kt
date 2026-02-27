package com.juliacai.apptick

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityEditGroupSelectionIntegrationTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var launchableApp: LaunchableApp

    private val setupRule = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            launchableApp = pickLaunchableApp(context)
            val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putBoolean("test_skip_permissions", true)
                .putBoolean("has_seen_launch_loading", true)
                .putString("active_lock_mode", "NONE")
                .putLong("last_seen_changelog_version", versionCode)
                .apply()

            runBlocking {
                val dao = AppTickDatabase.getDatabase(context).appLimitGroupDao()
                dao.deleteAllAppLimitGroups()
                dao.insertAppLimitGroup(
                    AppLimitGroup(
                        id = SOURCE_GROUP_ID,
                        name = SOURCE_GROUP_NAME,
                        timeHrLimit = 1,
                        timeMinLimit = 0,
                        apps = listOf(
                            AppInGroup(
                                appName = launchableApp.name,
                                appPackage = launchableApp.packageName,
                                appIcon = null
                            )
                        )
                    ).toEntity()
                )
            }
        }

        override fun after() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("test_skip_permissions")
                .apply()
            runBlocking {
                AppTickDatabase.getDatabase(context).appLimitGroupDao().deleteAllAppLimitGroups()
            }
        }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(setupRule).around(composeRule)

    @Before
    fun setUp() {
        assumeTrue(
            "MainActivity compose instrumentation is only stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
        waitForMainScreen()
        waitForSourceGroup()
    }

    @Test
    fun editGroup_existingAppIsPreselectedInPicker_andTogglesOffWithSingleTap() {
        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithText("Group Options").assertIsDisplayed()
        composeRule.onNodeWithText("Edit").performClick()

        composeRule.onNodeWithText("Set Time Limits").assertIsDisplayed()
        composeRule.onNodeWithText("Selected Apps (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Edit Selected Apps").performClick()

        composeRule.onNodeWithText("Select Apps to Limit").assertIsDisplayed()
        composeRule.onNodeWithText("Search Apps").performTextInput(launchableApp.name)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(launchableApp.name).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(launchableApp.name).performClick()
        composeRule.onNodeWithContentDescription("Next").performClick()

        composeRule.onNodeWithText("Selected Apps (0)").assertIsDisplayed()
    }

    private fun waitForMainScreen() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("AppTick").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForSourceGroup() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(SOURCE_GROUP_NAME).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pickLaunchableApp(context: Context): LaunchableApp {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != context.packageName }

        val labelsByPackage = activities.associate {
            it.activityInfo.packageName to it.loadLabel(pm).toString()
        }
        val labelCounts = labelsByPackage.values.groupingBy { it }.eachCount()
        val unique = labelsByPackage.entries.firstOrNull { labelCounts[it.value] == 1 }
            ?: labelsByPackage.entries.firstOrNull()
            ?: throw IllegalStateException("No launchable apps available for test")

        return LaunchableApp(name = unique.value, packageName = unique.key)
    }

    private data class LaunchableApp(
        val name: String,
        val packageName: String
    )

    companion object {
        private const val SOURCE_GROUP_ID = 9300L
        private const val SOURCE_GROUP_NAME = "Edit Selection Source Group"
    }
}
