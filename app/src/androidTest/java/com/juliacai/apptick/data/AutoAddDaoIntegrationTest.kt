package com.juliacai.apptick.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class AutoAddDaoIntegrationTest {

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
    fun insertGroup_withAutoAddMode_persistsCorrectly() = runTest {
        val group = AppLimitGroupEntity(
            id = 1,
            name = "Games",
            autoAddMode = "GAME",
            includeExistingApps = true,
            apps = listOf(AppInGroup("Test", "com.test.app", "com.test.app"))
        )
        dao.insertAppLimitGroup(group)

        val retrieved = dao.getGroup(1)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.autoAddMode).isEqualTo("GAME")
        assertThat(retrieved.includeExistingApps).isTrue()
    }

    @Test
    fun insertGroup_withAutoAddAllNew_persistsCorrectly() = runTest {
        val group = AppLimitGroupEntity(
            id = 1,
            name = "All New",
            autoAddMode = "ALL_NEW",
            includeExistingApps = false,
            apps = emptyList()
        )
        dao.insertAppLimitGroup(group)

        val retrieved = dao.getGroup(1)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.autoAddMode).isEqualTo("ALL_NEW")
        assertThat(retrieved.includeExistingApps).isFalse()
    }

    @Test
    fun insertGroup_defaultAutoAddMode_isNone() = runTest {
        val group = AppLimitGroupEntity(
            id = 1,
            name = "Default",
            apps = listOf(AppInGroup("Test", "com.test.app", "com.test.app"))
        )
        dao.insertAppLimitGroup(group)

        val retrieved = dao.getGroup(1)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.autoAddMode).isEqualTo("NONE")
        assertThat(retrieved.includeExistingApps).isTrue()
    }

    @Test
    fun updateGroup_autoAddMode_changesCorrectly() = runTest {
        val group = AppLimitGroupEntity(
            id = 1,
            name = "Social",
            autoAddMode = "NONE",
            apps = listOf(AppInGroup("Test", "com.test.app", "com.test.app"))
        )
        dao.insertAppLimitGroup(group)

        val updated = group.copy(autoAddMode = "SOCIAL", includeExistingApps = false)
        dao.updateAppLimitGroup(updated)

        val retrieved = dao.getGroup(1)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.autoAddMode).isEqualTo("SOCIAL")
        assertThat(retrieved.includeExistingApps).isFalse()
    }

    @Test
    fun entityToDomainModel_preservesAutoAddFields() = runTest {
        val entity = AppLimitGroupEntity(
            id = 1,
            name = "Games",
            autoAddMode = "GAME",
            includeExistingApps = false,
            apps = listOf(AppInGroup("Test", "com.test.app", "com.test.app"))
        )
        dao.insertAppLimitGroup(entity)

        val retrieved = dao.getGroup(1)!!
        val domain = retrieved.toDomainModel()
        assertThat(domain.autoAddMode).isEqualTo(AppLimitGroup.AUTO_ADD_CATEGORY_GAME)
        assertThat(domain.includeExistingApps).isFalse()
    }

    @Test
    fun domainToEntity_preservesAutoAddFields() {
        val domain = AppLimitGroup(
            id = 1,
            name = "Social",
            autoAddMode = AppLimitGroup.AUTO_ADD_CATEGORY_SOCIAL,
            includeExistingApps = true,
            apps = listOf(AppInGroup("Test", "com.test.app", "com.test.app"))
        )
        val entity = domain.toEntity()
        assertThat(entity.autoAddMode).isEqualTo("SOCIAL")
        assertThat(entity.includeExistingApps).isTrue()
    }

    @Test
    fun autoAddGroup_canHaveAppsAddedToIt() = runTest {
        val group = AppLimitGroupEntity(
            id = 1,
            name = "Games",
            autoAddMode = "GAME",
            includeExistingApps = true,
            apps = listOf(AppInGroup("Game1", "com.test.game1", "com.test.game1"))
        )
        dao.insertAppLimitGroup(group)

        // Simulate auto-adding a new app
        val retrieved = dao.getGroup(1)!!
        val domain = retrieved.toDomainModel()
        val newApp = AppInGroup("Game2", "com.test.game2", "com.test.game2")
        val updatedDomain = domain.copy(apps = domain.apps + newApp)
        dao.updateAppLimitGroup(updatedDomain.toEntity())

        val final = dao.getGroup(1)!!
        assertThat(final.apps).hasSize(2)
        assertThat(final.apps.map { it.appPackage }).containsExactly("com.test.game1", "com.test.game2")
    }

    @Test
    fun multipleGroupsWithAutoAdd_eachPersistsIndependently() = runTest {
        val gameGroup = AppLimitGroupEntity(
            id = 1,
            name = "Games",
            autoAddMode = "GAME",
            includeExistingApps = true,
            apps = emptyList()
        )
        val socialGroup = AppLimitGroupEntity(
            id = 2,
            name = "Social",
            autoAddMode = "SOCIAL",
            includeExistingApps = false,
            apps = emptyList()
        )
        val normalGroup = AppLimitGroupEntity(
            id = 3,
            name = "Normal",
            autoAddMode = "NONE",
            apps = emptyList()
        )
        dao.insertAppLimitGroup(gameGroup)
        dao.insertAppLimitGroup(socialGroup)
        dao.insertAppLimitGroup(normalGroup)

        val all = dao.getAllAppLimitGroupsImmediate()
        assertThat(all).hasSize(3)

        val game = all.first { it.id == 1L }
        val social = all.first { it.id == 2L }
        val normal = all.first { it.id == 3L }

        assertThat(game.autoAddMode).isEqualTo("GAME")
        assertThat(game.includeExistingApps).isTrue()
        assertThat(social.autoAddMode).isEqualTo("SOCIAL")
        assertThat(social.includeExistingApps).isFalse()
        assertThat(normal.autoAddMode).isEqualTo("NONE")
    }
}
