package com.juliacai.apptick.deviceApps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juliacai.apptick.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val appManager = AppManager(application)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _searchTerm = MutableStateFlow("")
    val searchTerm = _searchTerm.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = _searchTerm
        .combine(_apps) { term, apps ->
            if (term.isBlank()) {
                apps
            } else {
                apps.filter { it.appName?.contains(term, ignoreCase = true) == true }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _selectedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val selectedApps = _selectedApps.asStateFlow()

    init {
        viewModelScope.launch {
            _apps.value = appManager.getInstalledApps()
        }
    }

    fun onSearchTermChanged(term: String) {
        _searchTerm.value = term
    }

    fun onAppSelected(app: AppInfo) {
        val currentSelected = _selectedApps.value.toMutableList()
        if (currentSelected.any { it.appPackage == app.appPackage }) {
            currentSelected.removeAll { it.appPackage == app.appPackage }
        } else {
            currentSelected.add(app)
        }
        _selectedApps.value = currentSelected
    }

    fun setInitialSelection(apps: List<AppInfo>) {
        _selectedApps.value = apps
    }
}
