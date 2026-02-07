package com.juliacai.apptick

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.juliacai.apptick.data.AppLimitGroupEntity
import com.juliacai.apptick.data.AppTickDatabase

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appLimitGroupDao = AppTickDatabase.getDatabase(application).appLimitGroupDao()

    val groups: LiveData<List<AppLimitGroupEntity>> = appLimitGroupDao.getAllAppLimitGroups()

    private val _isPremium = MutableLiveData(false)
    val isPremium: LiveData<Boolean> = _isPremium

    fun updatePremiumStatus(isPremium: Boolean) {
        _isPremium.postValue(isPremium)
        if (isPremium) {
            val editor = getApplication<Application>().getSharedPreferences("groupPrefs", Application.MODE_PRIVATE).edit()
            editor.putBoolean("premium", true)
            editor.apply()
        }
    }
}
