package com.juliacai.apptick

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue

@RunWith(AndroidJUnit4::class)
class MainActivityLockdownFabIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        assumeTrue(
            "MainActivity compose instrumentation is only stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("test_skip_permissions", true)
            .putBoolean("has_seen_launch_loading", true)
            .putString("active_lock_mode", "LOCKDOWN")
            .putString("lockdown_type", "ONE_TIME")
            .putLong("lockdown_end_time", System.currentTimeMillis() + 3_600_000L)
            .apply()

        composeRule.activity.runOnUiThread {
            composeRule.activity.recreate()
        }
        waitForMainScreen()
    }

    @Test
    fun fabClick_lockdownLocked_stillAllowsAddingNewGroup() {
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_app_limit))
            .performClick()

        composeRule.onNodeWithText("Select Apps to Limit").assertIsDisplayed()
    }

    private fun waitForMainScreen() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("AppTick").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
