package com.juliacai.apptick.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class AppLimitGroupDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppTickDatabase
    private lateinit var dao: AppLimitGroupDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppTickDatabase::class.java
        ).build()
        dao = database.appLimitGroupDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAppLimitGroup() = runTest {
        val appLimitGroup = AppLimitGroupEntity(id = 1, name = "Social Media", apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))
        dao.insertAppLimitGroup(appLimitGroup)

        val allGroups = dao.getAllAppLimitGroupsImmediate()
        assertThat(allGroups).contains(appLimitGroup)
    }

    @Test
    fun deleteAppLimitGroup() = runTest {
        val appLimitGroup = AppLimitGroupEntity(id = 1, name = "Social Media", apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))
        dao.insertAppLimitGroup(appLimitGroup)
        dao.deleteAppLimitGroup(appLimitGroup)

        val allGroups = dao.getAllAppLimitGroupsImmediate()
        assertThat(allGroups).doesNotContain(appLimitGroup)
    }

    @Test
    fun updateAppLimitGroup() = runTest {
        val appLimitGroup = AppLimitGroupEntity(id = 1, name = "Social Media", apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))
        dao.insertAppLimitGroup(appLimitGroup)

        val updatedGroup = appLimitGroup.copy(name = "Productivity")
        dao.updateAppLimitGroup(updatedGroup)

        val allGroups = dao.getAllAppLimitGroupsImmediate()
        assertThat(allGroups).contains(updatedGroup)
    }

    @Test
    fun getGroup() = runTest {
        val appLimitGroup = AppLimitGroupEntity(id = 1, name = "Social Media", apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))
        dao.insertAppLimitGroup(appLimitGroup)

        val retrievedGroup = dao.getGroup(1)
        assertEquals(appLimitGroup, retrievedGroup)
    }

    @Test
    fun getGroupContainingApp() = runTest {
        val appLimitGroup = AppLimitGroupEntity(id = 1, name = "Social Media", apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))
        dao.insertAppLimitGroup(appLimitGroup)

        val retrievedGroup = dao.getGroupContainingApp("com.instagram.android")
        assertNotNull(retrievedGroup)
        assertEquals(appLimitGroup.id, retrievedGroup?.id)
    }

    @Test
    fun updateTimeRemaining() = runTest {
        val appLimitGroup = AppLimitGroupEntity(id = 1, name = "Social Media", timeRemaining = 1000, apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))
        dao.insertAppLimitGroup(appLimitGroup)

        dao.updateTimeRemaining(1, 500)

        val retrievedGroup = dao.getGroup(1)
        assertEquals(500L, retrievedGroup?.timeRemaining)
    }

    @Test
    fun getActiveGroupCount_returnsOnlyUnpausedGroups() = runTest {
        val active = AppLimitGroupEntity(
            id = 1,
            name = "Active",
            paused = false,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        )
        val paused = AppLimitGroupEntity(
            id = 2,
            name = "Paused",
            paused = true,
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube"))
        )
        dao.insertAppLimitGroup(active)
        dao.insertAppLimitGroup(paused)

        val activeCount = dao.getActiveGroupCount()
        assertEquals(1, activeCount)
    }
}
