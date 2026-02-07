package com.juliacai.apptick.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AppLimitGroupDao {

    @Query("SELECT * FROM app_limit_groups ORDER BY name ASC")
    fun getAllAppLimitGroups(): LiveData<List<AppLimitGroupEntity>>

    @Query("SELECT * FROM app_limit_groups")
    suspend fun getAllAppLimitGroupsImmediate(): List<AppLimitGroupEntity>

    @Query("SELECT * FROM app_limit_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: Long): AppLimitGroupEntity?

    @Query("SELECT * FROM app_limit_groups WHERE apps LIKE '%' || :appPackage || '%' LIMIT 1")
    suspend fun getGroupContainingApp(appPackage: String): AppLimitGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppLimitGroup(appLimitGroup: AppLimitGroupEntity)

    @Update
    suspend fun updateAppLimitGroup(appLimitGroup: AppLimitGroupEntity)

    @Delete
    suspend fun deleteAppLimitGroup(appLimitGroup: AppLimitGroupEntity)
}
