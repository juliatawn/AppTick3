package com.juliacai.apptick.newAppLimit

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import kotlinx.coroutines.launch

class AppLimitViewModel(application: Application) : AndroidViewModel(application) {

    private val appLimitGroupDao = AppTickDatabase.getDatabase(application).appLimitGroupDao()

    private val _selectedApps = MutableLiveData<List<AppInfo>>(emptyList())
    val selectedApps: LiveData<List<AppInfo>> = _selectedApps

    private val _group = MutableLiveData<AppLimitGroup?>(null)
    val group: LiveData<AppLimitGroup?> = _group

    fun setSelectedApps(apps: List<AppInfo>) {
        _selectedApps.value = apps
    }

    fun clearState() {
        _selectedApps.value = emptyList()
        _group.value = null
    }

    fun loadGroup(groupId: Long) {
        viewModelScope.launch {
            _group.postValue(appLimitGroupDao.getGroup(groupId)?.toDomainModel())
        }
    }

    fun startEditingGroup(group: AppLimitGroup) {
        _group.value = group
        _selectedApps.value = group.apps.map {
            AppInfo(
                appName = it.appName,
                appPackage = it.appPackage
            )
        }
    }

    fun saveGroup(group: AppLimitGroup) {
        viewModelScope.launch {
            if (group.id == 0L) {
                appLimitGroupDao.insertAppLimitGroup(group.toEntity())
            } else {
                appLimitGroupDao.updateAppLimitGroup(group.toEntity())
            }

            val appContext = getApplication<Application>()
            if (appLimitGroupDao.getActiveGroupCount() > 0) {
                BackgroundChecker.startServiceIfNotRunning(appContext)
            } else {
                appContext.stopService(Intent(appContext, BackgroundChecker::class.java))
            }
        }
    }
}
