package com.juliacai.apptick

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.data.AppLimitGroupDao
import com.juliacai.apptick.data.AppLimitGroupEntity
import com.juliacai.apptick.data.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito

@ExperimentalCoroutinesApi
class MainViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MainViewModel
    private lateinit var dao: AppLimitGroupDao
    private var savedPremium = false

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = mock()
        val application = Mockito.mock(Application::class.java)
        val liveData = MutableLiveData<List<AppLimitGroupEntity>>()
        whenever(dao.getAllAppLimitGroups()).thenReturn(liveData)
        runBlocking {
            whenever(dao.getActiveGroupCount()).thenReturn(0)
        }
        savedPremium = false
        viewModel = MainViewModel(
            application, dao,
            applyServiceState = { _, _ -> },
            getPremium = { false },
            savePremium = { _, value -> savedPremium = value }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test loading groups`() = runTest {
        val groups = listOf(
            AppLimitGroup(id = 1, name = "Social Media", apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))),
            AppLimitGroup(id = 2, name = "Games", apps = listOf(AppInGroup("Clash of Clans", "com.supercell.clashofclans", "com.supercell.clashofclans")))
        )
        val entities = groups.map { it.toEntity() }
        val liveData = viewModel.groups as MutableLiveData
        liveData.value = entities

        val value = viewModel.groups.value

        assertThat(value).isEqualTo(entities)
    }

    @Test
    fun `test pause group`() = runTest {
        val group = AppLimitGroup(id = 1, name = "Social Media", paused = false, apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))
        val updatedGroup = group.copy(paused = true)

        viewModel.togglePause(group)
        advanceUntilIdle()
        Mockito.verify(dao).updateAppLimitGroup(updatedGroup.toEntity())

    }

    @Test
    fun `test delete group`() = runTest {
        val group = AppLimitGroup(id = 1, name = "Social Media", apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")))

        viewModel.deleteGroup(group)
        advanceUntilIdle()
        Mockito.verify(dao).deleteAppLimitGroup(group.toEntity())

    }

    @Test
    fun `initial premium state comes from preferences`() {
        assertThat(viewModel.isPremium.value).isFalse()
    }

    @Test
    fun `updatePremiumStatus true updates liveData and prefs`() {
        viewModel.updatePremiumStatus(true)
        assertThat(viewModel.isPremium.value).isTrue()
        assertThat(savedPremium).isTrue()
    }

    // ── Unpause expired-reset tests ──────────────────────────────────────

    @Test
    fun `applyExpiredResetOnUnpause resets timer when nextResetTime has passed`() {
        val group = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 0, // daily
            timeRemaining = 1_200_000L, // 20 min left
            nextResetTime = System.currentTimeMillis() - 3_600_000L, // 1 hour ago
            perAppUsage = listOf(AppUsageStat("com.test.app", 2_400_000L))
        )

        val result = MainViewModel.applyExpiredResetOnUnpause(group)

        assertThat(result.timeRemaining).isEqualTo(3_600_000L) // Full 1hr
        assertThat(result.perAppUsage.all { it.usedMillis == 0L }).isTrue()
        assertThat(result.nextResetTime).isGreaterThan(System.currentTimeMillis())
    }

    @Test
    fun `applyExpiredResetOnUnpause preserves state when nextResetTime is in the future`() {
        val futureReset = System.currentTimeMillis() + 3_600_000L
        val group = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            timeRemaining = 1_200_000L,
            nextResetTime = futureReset,
            perAppUsage = listOf(AppUsageStat("com.test.app", 2_400_000L))
        )

        val result = MainViewModel.applyExpiredResetOnUnpause(group)

        assertThat(result.timeRemaining).isEqualTo(1_200_000L) // Unchanged
        assertThat(result.nextResetTime).isEqualTo(futureReset)
        assertThat(result.perAppUsage.first().usedMillis).isEqualTo(2_400_000L)
    }

    @Test
    fun `applyExpiredResetOnUnpause periodic reset sets nextResetTime from now`() {
        val group = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 30,
            resetMinutes = 180, // 3 hours
            timeRemaining = 0L,
            nextResetTime = System.currentTimeMillis() - 7_200_000L // 2 hours ago
        )

        val before = System.currentTimeMillis()
        val result = MainViewModel.applyExpiredResetOnUnpause(group)
        val after = System.currentTimeMillis()

        val threeHoursMs = 180 * 60 * 1000L
        assertThat(result.nextResetTime).isAtLeast(before + threeHoursMs - 100)
        assertThat(result.nextResetTime).isAtMost(after + threeHoursMs + 100)
        assertThat(result.timeRemaining).isEqualTo(1_800_000L) // 30 min
    }
}
