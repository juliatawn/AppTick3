package com.juliacai.apptick

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.data.AppLimitGroupDao
import com.juliacai.apptick.data.AppLimitGroupEntity
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainViewModel(
    application: Application,
    private val appLimitGroupDao: AppLimitGroupDao,
    private val applyServiceState: (android.content.Context, Boolean) -> Unit = BackgroundChecker::applyDesiredServiceState,
    private val getPremium: (android.content.Context) -> Boolean = PremiumStore::isPremium,
    private val savePremium: (android.content.Context, Boolean) -> Unit = PremiumStore::setPremium
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, AppTickDatabase.getDatabase(application).appLimitGroupDao())

    val groups: LiveData<List<AppLimitGroupEntity>> = appLimitGroupDao.getAllAppLimitGroups()

    private val _isPremium = MutableLiveData(
        getPremium(getApplication())
    )
    val isPremium: LiveData<Boolean> = _isPremium

    fun getGroup(groupId: Long): LiveData<AppLimitGroup?> {
        return appLimitGroupDao.getGroupLive(groupId).map { entity ->
            entity?.toDomainModel()
        }
    }

    fun updatePremiumStatus(isPremium: Boolean) {
        _isPremium.postValue(isPremium)
        savePremium(getApplication(), isPremium)
    }

    fun togglePause(group: AppLimitGroup) {
        viewModelScope.launch {
            var updatedGroup = group.copy(paused = !group.paused)

            // When unpausing, apply any resets that should have fired while the group was paused
            // so the user sees accurate time remaining immediately.
            if (!updatedGroup.paused) {
                updatedGroup = applyExpiredResetOnUnpause(updatedGroup)
            }

            appLimitGroupDao.updateAppLimitGroup(updatedGroup.toEntity())
            val appContext = getApplication<Application>()
            applyServiceState(
                appContext,
                appLimitGroupDao.getActiveGroupCount() > 0
            )
            // Wake the background checker immediately for responsive enforcement.
            BackgroundChecker.requestImmediateCheck()
        }
    }

    companion object {
        /**
         * If the group's nextResetTime has passed (e.g., a reset was due while paused),
         * applies the reset: restores the full time limit, clears per-app usage, and
         * sets the next reset time from now.
         */
        internal fun applyExpiredResetOnUnpause(group: AppLimitGroup): AppLimitGroup {
            val now = System.currentTimeMillis()
            if (group.nextResetTime <= 0L || group.nextResetTime > now) {
                return group // No expired reset — nothing to adjust.
            }

            val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
            val fullLimitMillis = TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
            val clearedUsage = group.perAppUsage.map { it.copy(usedMillis = 0L) }

            val newNextReset = if (group.resetMinutes > 0) {
                now + TimeUnit.MINUTES.toMillis(group.resetMinutes.toLong())
            } else {
                TimeManager.nextMidnight(now)
            }

            return group.copy(
                timeRemaining = fullLimitMillis,
                nextResetTime = newNextReset,
                nextAddTime = if (group.cumulativeTime && group.resetMinutes > 0) newNextReset else 0L,
                perAppUsage = clearedUsage
            )
        }
    }

    fun deleteGroup(group: AppLimitGroup) {
        viewModelScope.launch {
            appLimitGroupDao.deleteAppLimitGroup(group.toEntity())
        }
    }

    fun setGroupExpanded(groupId: Long, isExpanded: Boolean) {
        viewModelScope.launch {
            appLimitGroupDao.updateGroupExpanded(groupId, isExpanded)
        }
    }
}
