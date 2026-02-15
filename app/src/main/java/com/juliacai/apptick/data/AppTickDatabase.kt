package com.juliacai.apptick.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppLimitGroupEntity::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppTickDatabase : RoomDatabase() {

    abstract fun appLimitGroupDao(): AppLimitGroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppTickDatabase? = null

        @JvmStatic
        fun getDatabase(context: Context): AppTickDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppTickDatabase::class.java,
                    "app_tick_database"
                )
                    .addMigrations(MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToVersion5(db)
            }
        }

        private val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToVersion5(db)
            }
        }

        private val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToVersion5(db)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToVersion5(db)
            }
        }

        private fun migrateToVersion5(db: SupportSQLiteDatabase) {
            val hasPerAppUsage = hasColumn(db, "app_limit_groups", "perAppUsage")
            val perAppUsageExpr = if (hasPerAppUsage) "perAppUsage" else "'[]'"

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_limit_groups_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT,
                    timeHrLimit INTEGER NOT NULL,
                    timeMinLimit INTEGER NOT NULL,
                    limitEach INTEGER NOT NULL,
                    resetHours INTEGER NOT NULL,
                    weekDays TEXT NOT NULL,
                    apps TEXT NOT NULL,
                    paused INTEGER NOT NULL,
                    useTimeRange INTEGER NOT NULL,
                    startHour INTEGER NOT NULL,
                    startMinute INTEGER NOT NULL,
                    endHour INTEGER NOT NULL,
                    endMinute INTEGER NOT NULL,
                    cumulativeTime INTEGER NOT NULL,
                    timeRemaining INTEGER NOT NULL,
                    nextResetTime INTEGER NOT NULL,
                    nextAddTime INTEGER NOT NULL,
                    perAppUsage TEXT NOT NULL DEFAULT '[]'
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO app_limit_groups_new (
                    id, name, timeHrLimit, timeMinLimit, limitEach, resetHours, weekDays, apps,
                    paused, useTimeRange, startHour, startMinute, endHour, endMinute,
                    cumulativeTime, timeRemaining, nextResetTime, nextAddTime, perAppUsage
                )
                SELECT
                    id, name, timeHrLimit, timeMinLimit, limitEach, resetHours, weekDays, apps,
                    paused, useTimeRange, startHour, startMinute, endHour, endMinute,
                    cumulativeTime, timeRemaining, nextResetTime, nextAddTime, $perAppUsageExpr
                FROM app_limit_groups
                """.trimIndent()
            )

            db.execSQL("DROP TABLE app_limit_groups")
            db.execSQL("ALTER TABLE app_limit_groups_new RENAME TO app_limit_groups")
        }

        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
