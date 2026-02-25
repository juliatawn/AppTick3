package com.juliacai.apptick.lockModes

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.juliacai.apptick.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Ignore("Legacy PasswordResetActivity flow is no longer exported/wired in the active app flow.")
class PasswordResetActivityNavigationTest {

    @Before
    fun setUp() {
        Intents.init()
        intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun navigateAfterSuccess_resetPassword_opensEnterPassword() {
        launchAndNavigate(
            resetMode = "password",
            purpose = RecoveryEmailHelper.PURPOSE_RESET_PASSWORD
        )
        intended(hasComponent(EnterPasswordActivity::class.java.name))
    }

    @Test
    fun navigateAfterSuccess_resetSecurityKey_opensEnterSecurityKey() {
        launchAndNavigate(
            resetMode = "security_key",
            purpose = RecoveryEmailHelper.PURPOSE_RESET_SECURITY_KEY
        )
        intended(hasComponent(EnterSecurityKeyActivity::class.java.name))
    }

    @Test
    fun navigateAfterSuccess_setupPassword_opensSetPassword() {
        launchAndNavigate(
            resetMode = "password",
            purpose = RecoveryEmailHelper.PURPOSE_SETUP_PASSWORD
        )
        intended(hasComponent(SetPassword::class.java.name))
    }

    @Test
    fun navigateAfterSuccess_setupSecurityKey_opensMainWithLockModes() {
        launchAndNavigate(
            resetMode = "security_key",
            purpose = RecoveryEmailHelper.PURPOSE_SETUP_SECURITY_KEY
        )
        intended(
            allOf(
                hasComponent(MainActivity::class.java.name),
                hasExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, true)
            )
        )
    }

    @Test
    fun navigateAfterSuccess_unknownPurpose_usesResetModeFallback() {
        launchAndNavigate(resetMode = "security_key", purpose = null)
        intended(hasComponent(EnterSecurityKeyActivity::class.java.name))
    }

    private fun launchAndNavigate(resetMode: String, purpose: String?) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, PasswordResetActivity::class.java).putExtra("reset_mode", resetMode)

        ActivityScenario.launch<PasswordResetActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.navigateAfterSuccessForTest(purpose)
            }
        }
    }
}
