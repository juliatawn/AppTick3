package com.juliacai.apptick.backgroundProcesses

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
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
import java.io.IOException
import org.junit.Assert.assertEquals

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@MediumTest
class BackgroundCheckerTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var database: AppTickDatabase
    private lateinit var dao: AppLimitGroupDao
    private lateinit var context: Context

    @Before
    fun setup() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppTickDatabase.getDatabase(context)
        dao = database.appLimitGroupDao()
        clearAllGroups()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() = runTest {
        clearAllGroups()
    }

    @Test
    @Throws(Exception::class)
    fun testServiceDoesNotBlockWhenTimeIsNotUp() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Social Media",
            timeHrLimit = 1,
            timeRemaining = 3600000,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()

        service.checkAppLimits("com.instagram.android")

        val updatedGroup = dao.getGroup(1)
        assertEquals(group.timeRemaining - 1000, updatedGroup?.timeRemaining)
    }

    @Test
    @Throws(Exception::class)
    fun testServiceBlocksWhenTimeIsUp() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Social Media",
            timeHrLimit = 1,
            timeRemaining = 1000,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()

        service.checkAppLimits("com.instagram.android")

        val updatedGroup = dao.getGroup(1)
        assertEquals(0L, updatedGroup?.timeRemaining)
    }

    @Test
    @Throws(Exception::class)
    fun testServiceTracksTimeAndBlocks() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Social Media",
            timeHrLimit = 0,
            timeMinLimit = 1,
            timeRemaining = 60000,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()

        // Simulate app usage for 30 seconds
        for (i in 0..29) {
            service.checkAppLimits("com.instagram.android")
        }

        var updatedGroup = dao.getGroup(1)
        assertEquals(30000L, updatedGroup?.timeRemaining)

        // Simulate app usage for another 30 seconds
        for (i in 0..29) {
            service.checkAppLimits("com.instagram.android")
        }

        updatedGroup = dao.getGroup(1)
        assertEquals(0L, updatedGroup?.timeRemaining)
    }

    private suspend fun clearAllGroups() {
        dao.getAllAppLimitGroupsImmediate().forEach { dao.deleteAppLimitGroup(it) }
    }
}
