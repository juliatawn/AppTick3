package com.juliacai.apptick

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
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
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class MainViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MainViewModel
    private lateinit var dao: AppLimitGroupDao
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = mock()
        val application = Mockito.mock(Application::class.java)
        sharedPreferences = Mockito.mock(SharedPreferences::class.java)
        editor = Mockito.mock(SharedPreferences.Editor::class.java)
        val liveData = MutableLiveData<List<AppLimitGroupEntity>>()
        whenever(dao.getAllAppLimitGroups()).thenReturn(liveData)
        Mockito.`when`(application.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        Mockito.`when`(sharedPreferences.getBoolean("premium", false)).thenReturn(false)
        Mockito.`when`(sharedPreferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.putBoolean(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(editor)
        runBlocking {
            whenever(dao.getActiveGroupCount()).thenReturn(0)
        }
        viewModel = MainViewModel(application, dao) { _, _ -> }
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
        verify(editor).putBoolean("premium", true)
        verify(editor).apply()
    }
}
