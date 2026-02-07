package com.juliacai.apptick.deviceApps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.juliacai.apptick.AppInfo
import kotlinx.coroutines.launch

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val appManager = AppManager(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    fun loadInstalledApps() {
        viewModelScope.launch {
            _apps.value = appManager.getInstalledApps()
        }
    }
}
