package com.juliacai.apptick.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [AppLimitGroupEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppTickDatabase : RoomDatabase() {

    abstract fun appLimitGroupDao(): AppLimitGroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppTickDatabase? = null

        fun getDatabase(context: Context): AppTickDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppTickDatabase::class.java,
                    "app_tick_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
