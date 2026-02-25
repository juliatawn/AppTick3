package com.juliacai.apptick

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.juliacai.apptick.lockModes.EnterPasswordActivity
import com.juliacai.apptick.lockModes.EnterSecurityKeyActivity
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

@RunWith(AndroidJUnit4::class)
class MainActivityLockModesIntentTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val prefsSetupRule = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .clear()
                .putBoolean("test_skip_permissions", true)
                .putBoolean("has_seen_launch_loading", true)
                .apply()
        }

        override fun after() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("test_skip_permissions")
                .apply()
        }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(prefsSetupRule).around(composeRule)

    @Before
    fun setUp() {
        assumeTrue(
            "MainActivity compose instrumentation is only stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
        waitForMainScreen()
    }

    @Test
    fun lockedTopBarIcon_passwordMode_opensEnterPassword() {
        withInitializedIntents {
            updatePrefsAndRecreate {
                putBoolean("premium", true)
                putString("active_lock_mode", "PASSWORD")
                putString("password", "1234")
                putBoolean("passUnlocked", false)
                putBoolean("securityKeyUnlocked", false)
            }

            composeRule.onNodeWithContentDescription("Lock modes are locked").performClick()

            intended(hasComponent(EnterPasswordActivity::class.java.name))
        }
    }

    @Test
    fun lockedTopBarIcon_securityKeyMode_opensEnterSecurityKey() {
        withInitializedIntents {
            updatePrefsAndRecreate {
                putBoolean("premium", true)
                putString("active_lock_mode", "SECURITY_KEY")
                putBoolean("security_key_enabled", true)
                putString("security_key_value", "abcd")
                putBoolean("securityKeyUnlocked", false)
                putBoolean("passUnlocked", false)
            }

            composeRule.onNodeWithContentDescription("Lock modes are locked").performClick()

            intended(hasComponent(EnterSecurityKeyActivity::class.java.name))
        }
    }

    @Test
    fun lockedTopBarIcon_lockdownMode_showsBlockedScreen() {
        updatePrefsAndRecreate {
            putBoolean("premium", true)
            putString("active_lock_mode", "LOCKDOWN")
            putString("lockdown_type", "ONE_TIME")
            putLong("lockdown_end_time", System.currentTimeMillis() + 3_600_000L)
        }

        composeRule.onNodeWithContentDescription("Lock modes are locked").performClick()

        composeRule.onNodeWithText("Settings changes are locked").assertIsDisplayed()
    }

    @Test
    fun fabClick_passwordLocked_opensEnterPassword() {
        withInitializedIntents {
            updatePrefsAndRecreate {
                putString("active_lock_mode", "PASSWORD")
                putString("password", "1234")
                putBoolean("passUnlocked", false)
                putBoolean("securityKeyUnlocked", false)
            }

            composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_app_limit))
                .performClick()

            intended(hasComponent(EnterPasswordActivity::class.java.name))
        }
    }

    private fun withInitializedIntents(block: () -> Unit) {
        Intents.init()
        intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        try {
            block()
        } finally {
            Intents.release()
        }
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
    }

    private fun waitForMainScreen() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("AppTick").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
