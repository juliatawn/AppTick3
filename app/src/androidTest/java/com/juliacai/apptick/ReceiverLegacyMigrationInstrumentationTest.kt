package com.juliacai.apptick

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.AppLimitGroupDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReceiverLegacyMigrationInstrumentationTest {

    private lateinit var context: Context
    private lateinit var dao: AppLimitGroupDao
    private lateinit var legacyFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dao = AppTickDatabase.getDatabase(context).appLimitGroupDao()
        legacyFile = File(context.filesDir, "appLimitPrefs")
        if (legacyFile.exists()) legacyFile.delete()
        runBlocking {
            dao.deleteAllAppLimitGroups()
        }
    }

    @After
    fun tearDown() {
        if (legacyFile.exists()) legacyFile.delete()
        runBlocking {
            dao.deleteAllAppLimitGroups()
        }
    }

    @Test
    fun handleStartupSignal_migratesLegacyFileIntoRoom() = runBlocking {
        legacyFile.writeText(
            "0:1700000000000:1:30:true:Focus:2:[1, 2, 7]:[com.instagram.android, com.google.android.youtube]:false\n"
        )

        Receiver().handleStartupSignal(context)

        val groups = dao.getAllAppLimitGroupsImmediate()
        assertThat(groups).hasSize(1)
        val group = groups.first()
        assertThat(group.name).isEqualTo("Focus")
        assertThat(group.timeHrLimit).isEqualTo(1)
        assertThat(group.timeMinLimit).isEqualTo(30)
        assertThat(group.limitEach).isTrue()
        assertThat(group.resetMinutes).isEqualTo(120)
        assertThat(group.weekDays).containsExactly(1, 6, 7).inOrder()
        assertThat(group.apps.map { it.appPackage })
            .containsExactly("com.instagram.android", "com.google.android.youtube")
            .inOrder()
        assertThat(group.paused).isFalse()
        assertThat(legacyFile.exists()).isFalse()
    }
}
