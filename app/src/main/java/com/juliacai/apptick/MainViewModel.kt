package com.juliacai.apptick

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.data.AppLimitGroupDao
import com.juliacai.apptick.data.AppLimitGroupEntity
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import kotlinx.coroutines.launch

class MainViewModel(application: Application, private val appLimitGroupDao: AppLimitGroupDao) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, AppTickDatabase.getDatabase(application).appLimitGroupDao())

    val groups: LiveData<List<AppLimitGroupEntity>> = appLimitGroupDao.getAllAppLimitGroups()

    private val _isPremium = MutableLiveData(
        getApplication<Application>()
            .getSharedPreferences("groupPrefs", Application.MODE_PRIVATE)
            .getBoolean("premium", false)
    )
    val isPremium: LiveData<Boolean> = _isPremium

    fun getGroup(groupId: Long): LiveData<AppLimitGroup?> {
        val liveData = MutableLiveData<AppLimitGroup?>()
        viewModelScope.launch {
            liveData.postValue(appLimitGroupDao.getGroup(groupId)?.toDomainModel())
        }
        return liveData
    }

    fun updatePremiumStatus(isPremium: Boolean) {
        _isPremium.postValue(isPremium)
        getApplication<Application>()
            .getSharedPreferences("groupPrefs", Application.MODE_PRIVATE)
            .edit {
                putBoolean("premium", isPremium)
            }
    }

    fun togglePause(group: AppLimitGroup) {
        viewModelScope.launch {
            val updatedGroup = group.copy(paused = !group.paused)
            appLimitGroupDao.updateAppLimitGroup(updatedGroup.toEntity())
            val appContext = getApplication<Application>()
            if (appLimitGroupDao.getActiveGroupCount() > 0) {
                BackgroundChecker.startServiceIfNotRunning(appContext)
            } else {
                appContext.stopService(Intent(appContext, BackgroundChecker::class.java))
            }
        }
    }

    fun deleteGroup(group: AppLimitGroup) {
        viewModelScope.launch {
            appLimitGroupDao.deleteAppLimitGroup(group.toEntity())
        }
    }
}
