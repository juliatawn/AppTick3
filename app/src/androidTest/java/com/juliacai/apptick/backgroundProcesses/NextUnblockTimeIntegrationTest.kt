package com.juliacai.apptick.backgroundProcesses

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.juliacai.apptick.TimeManager
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.block.BlockWindowActivity
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.TimeRange
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.AppLimitGroupDao
import com.juliacai.apptick.data.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.util.Calendar

/**
 * Integration tests verifying that the block screen receives the correct "next unblock time"
 * via the `next_reset_time` intent extra for various group configurations.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@MediumTest
class NextUnblockTimeIntegrationTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var database: AppTickDatabase
    private lateinit var dao: AppLimitGroupDao
    private lateinit var context: Context

    @Before
    fun setup() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        BackgroundChecker.disableBackgroundLoopForTesting = true
        database = AppTickDatabase.getDatabase(context)
        dao = database.appLimitGroupDao()
        clearAllGroups()
    }

    @After
    fun tearDown() = runTest {
        clearAllGroups()
        BackgroundChecker.disableBackgroundLoopForTesting = false
    }

    private suspend fun clearAllGroups() {
        val all = dao.getAllAppLimitGroupsImmediate()
        for (group in all) dao.deleteAppLimitGroup(group)
    }

    private fun bindService(): BackgroundChecker {
        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        return (binder as BackgroundChecker.LocalBinder).getService()
    }

    /**
     * Blocked outside time range (6am-6pm, now is 8pm):
     * next_reset_time should show next time range start (6am tomorrow), not midnight.
     */
    @Test
    fun outsideTimeRangeBlock_showsNextRangeStart() = runTest {
        // Create a group with time range 6am-6pm and blockOutsideTimeRange=true
        // We need "now" to be outside 6am-6pm. Since we can't control time in
        // integration tests, we construct the range so that NOW is definitely outside.
        val cal = Calendar.getInstance()
        val nowHour = cal.get(Calendar.HOUR_OF_DAY)
        val nowMinute = cal.get(Calendar.MINUTE)

        // Create a 1-hour range that ended 1 hour ago
        val rangeEndHour = if (nowHour >= 2) nowHour - 1 else 23
        val rangeStartHour = if (rangeEndHour >= 1) rangeEndHour - 1 else 23

        val group = AppLimitGroup(
            id = 1,
            name = "Outside Range Test",
            timeHrLimit = 1,
            timeMinLimit = 0,
            timeRemaining = 3_600_000L,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(
                TimeRange(
                    startHour = rangeStartHour,
                    startMinute = 0,
                    endHour = rangeEndHour,
                    endMinute = 0
                )
            ),
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")),
            nextResetTime = TimeManager.nextMidnight()
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.instagram.android")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull("Should block when outside configured time range", blockedActivity)

            // Verify the next_reset_time points to the range start, not midnight
            val nextResetFromIntent = blockedActivity?.intent?.getLongExtra("next_reset_time", 0L) ?: 0L
            assertTrue("next_reset_time should be > 0", nextResetFromIntent > 0L)

            // It should be the next occurrence of rangeStartHour:00
            val resultCal = Calendar.getInstance().apply { timeInMillis = nextResetFromIntent }
            assertEquals(
                "Next unblock hour should match range start",
                rangeStartHour,
                resultCal.get(Calendar.HOUR_OF_DAY)
            )

            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /**
     * Zero limit with time range (blocked always during range, no limit outside):
     * next_reset_time should show the time range end, not midnight.
     */
    @Test
    fun zeroLimitInTimeRange_showsRangeEnd() = runTest {
        val cal = Calendar.getInstance()
        val nowHour = cal.get(Calendar.HOUR_OF_DAY)

        // Create a range that includes NOW and ends in 2 hours
        val rangeEndHour = (nowHour + 2) % 24

        val group = AppLimitGroup(
            id = 1,
            name = "Zero Limit Range Test",
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 0L,
            useTimeRange = true,
            blockOutsideTimeRange = false,
            timeRanges = listOf(
                TimeRange(
                    startHour = nowHour,
                    startMinute = 0,
                    endHour = rangeEndHour,
                    endMinute = 0
                )
            ),
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")),
            nextResetTime = TimeManager.nextMidnight()
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.instagram.android")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull("Should block with zero limit in time range", blockedActivity)

            val nextResetFromIntent = blockedActivity?.intent?.getLongExtra("next_reset_time", 0L) ?: 0L

            // Should point to the range end, not midnight
            val resultCal = Calendar.getInstance().apply { timeInMillis = nextResetFromIntent }
            assertEquals(
                "Next unblock hour should match range end",
                rangeEndHour,
                resultCal.get(Calendar.HOUR_OF_DAY)
            )

            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /**
     * Limit reached, no time range: next_reset_time should be group.nextResetTime (daily reset).
     */
    @Test
    fun limitReachedNoTimeRange_showsNextResetTime() = runTest {
        val nextReset = TimeManager.nextMidnight()
        val group = AppLimitGroup(
            id = 1,
            name = "Daily Reset Test",
            timeHrLimit = 0,
            timeMinLimit = 1,
            timeRemaining = 500L,
            useTimeRange = false,
            nextResetTime = nextReset,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.instagram.android")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull("Should block when limit is reached", blockedActivity)

            val nextResetFromIntent = blockedActivity?.intent?.getLongExtra("next_reset_time", 0L) ?: 0L
            assertEquals(
                "next_reset_time should equal group nextResetTime for daily reset",
                nextReset,
                nextResetFromIntent
            )

            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
