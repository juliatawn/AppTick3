package com.juliacai.apptick

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
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
class MainActivityDuplicateGroupIntegrationTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val setupRule = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
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
                        timeMinLimit = 30,
                        limitEach = true,
                        apps = listOf(
                            AppInGroup(
                                appName = "Calculator",
                                appPackage = "com.android.calculator2",
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
                .edit {
                    remove("test_skip_permissions")
                }
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
    fun duplicate_freeUser_showsPremiumDialog() {
        openGroupActions()
        composeRule.onNodeWithText("Duplicate").performClick()
        composeRule.onNodeWithText("Duplicate is available in Premium Mode.").assertIsDisplayed()
    }

    @Test
    fun duplicate_premiumUser_opensSetTimeLimitsAndSavesAsNewGroup() {
        updatePrefsAndRecreate {
            putBoolean("premium", true)
        }

        openGroupActions()
        composeRule.onNodeWithText("Duplicate").performClick()
        composeRule.onNodeWithText("Set Time Limits").assertIsDisplayed()
        composeRule.onNodeWithText("Selected Apps (1)").assertIsDisplayed()

        composeRule.onNodeWithText("Save").performClick()
        waitForMainScreen()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val groups = AppTickDatabase.getDatabase(context)
                .appLimitGroupDao()
                .getAllAppLimitGroupsImmediate()
                .map { it.toDomainModel() }
            assertThat(groups).hasSize(2)
            val duplicated = groups.first { it.id != SOURCE_GROUP_ID }
            assertThat(duplicated.name).isEqualTo(SOURCE_GROUP_NAME)
            assertThat(duplicated.apps).hasSize(1)
            assertThat(duplicated.paused).isFalse()
            assertThat(duplicated.timeRemaining).isAtLeast(0L)
        }
    }

    private fun openGroupActions() {
        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithText("Group Options").assertIsDisplayed()
    }

    private fun updatePrefsAndRecreate(mutator: android.content.SharedPreferences.Editor.() -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("test_skip_permissions", true)
            putBoolean("has_seen_launch_loading", true)
            mutator()
        }.apply()

        composeRule.activity.runOnUiThread {
            composeRule.activity.recreate()
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        waitForMainScreen()
        waitForSourceGroup()
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

    companion object {
        private const val SOURCE_GROUP_ID = 9001L
        private const val SOURCE_GROUP_NAME = "Focus Source Group"
    }
}
