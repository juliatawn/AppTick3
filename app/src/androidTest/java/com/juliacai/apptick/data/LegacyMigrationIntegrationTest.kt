package com.juliacai.apptick.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LegacyMigrationIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppTickDatabase
    private lateinit var dao: AppLimitGroupDao
    private lateinit var legacyFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppTickDatabase::class.java).build()
        dao = database.appLimitGroupDao()
        legacyFile = File(context.filesDir, "appLimitPrefs")
        if (legacyFile.exists()) legacyFile.delete()
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            .edit()
            .remove("legacy_app_name_repair_done_v1")
            .commit()
    }

    @After
    fun tearDown() {
        if (legacyFile.exists()) legacyFile.delete()
        context.getSharedPreferences("groupPrefs_legacy_migration_test", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            .edit()
            .remove("legacy_app_name_repair_done_v1")
            .commit()
        database.close()
    }

    @Test
    fun migrate_importsLegacyAppLimitFileAndDeletesIt() = runTest {
        legacyFile.writeText(
            buildString {
                appendLine("0:1700000000000:1:0:true:Focus:2:[1, 2, 7]:[com.instagram.android, com.google.android.youtube]:false")
                appendLine("1:1700000000000:0:30:false:Games:0:[3, 4]:[com.supercell.clashroyale]:true")
            }
        )

        LegacyDataMigrator(context, dao).migrate()

        val groups = dao.getAllAppLimitGroupsImmediate()
        assertThat(groups).hasSize(2)
        val focusGroup = groups.first { it.name == "Focus" }
        assertThat(focusGroup.resetMinutes).isEqualTo(120)
        assertThat(focusGroup.weekDays).containsExactly(1, 6, 7).inOrder()
        assertThat(focusGroup.apps.map { it.appPackage })
            .containsExactly("com.instagram.android", "com.google.android.youtube")
            .inOrder()
        assertThat(legacyFile.exists()).isFalse()
    }

    @Test
    fun migrate_isIdempotentWhenLegacyFileReappears() = runTest {
        val line =
            "0:1700000000000:1:30:true:Focus:2:[1, 2, 7]:[com.instagram.android, com.google.android.youtube]:false"
        val existing = LegacyAppLimitLineParser.parseLineToEntity(line, nowMillis = 1700000000000L)
        requireNotNull(existing)
        dao.insertAppLimitGroup(existing)

        legacyFile.writeText("$line\n")

        LegacyDataMigrator(context, dao).migrate()

        val groups = dao.getAllAppLimitGroupsImmediate()
        assertThat(groups).hasSize(1)
        assertThat(groups.first().name).isEqualTo("Focus")
        assertThat(legacyFile.exists()).isFalse()
    }

    @Test
    fun migrate_repairsStoredAppNamesThatEqualPackageName() = runTest {
        val packageName = context.packageName
        val expectedLabel = context.packageManager
            .getApplicationLabel(context.applicationInfo)
            .toString()
            .trim()
        dao.insertAppLimitGroup(
            AppLimitGroupEntity(
                name = "Repair",
                apps = listOf(
                    AppInGroup(
                        appName = packageName,
                        appPackage = packageName,
                        appIcon = ""
                    )
                )
            )
        )

        LegacyDataMigrator(context, dao).migrate()

        val repaired = dao.getAllAppLimitGroupsImmediate().single()
        assertThat(repaired.apps.single().appName).isEqualTo(expectedLabel)
    }

    @Test
    fun migrate_prefersNewLockModeAndSanitizesLegacyPasswordType() {
        val prefs = context.getSharedPreferences("groupPrefs_legacy_migration_test", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("locked", true)
            .putBoolean("password", true)
            .remove("active_lock_mode")
            .remove("passUnlocked")
            .commit()

        LegacyLockPrefsMigrator.migrate(prefs, nowMillis = 1700000000000L)

        assertThat(prefs.getString("active_lock_mode", null)).isEqualTo("PASSWORD")
        assertThat(prefs.getBoolean("passUnlocked", false)).isTrue()
        assertThat(prefs.getBoolean("force_recovery_email_setup", false)).isTrue()
        assertThat(prefs.all["password"]).isNull()
    }

    @Test
    fun migrate_doesNotForceRecoverySetupWhenEmailAlreadyPresent() {
        val prefs = context.getSharedPreferences("groupPrefs_legacy_migration_test", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("locked", true)
            .putBoolean("password", true)
            .putString("recovery_email", "legacy.user@example.com")
            .remove("active_lock_mode")
            .remove("passUnlocked")
            .commit()

        LegacyLockPrefsMigrator.migrate(prefs, nowMillis = 1700000000000L)

        assertThat(prefs.getString("active_lock_mode", null)).isEqualTo("PASSWORD")
        assertThat(prefs.getBoolean("force_recovery_email_setup", true)).isFalse()
    }
}
