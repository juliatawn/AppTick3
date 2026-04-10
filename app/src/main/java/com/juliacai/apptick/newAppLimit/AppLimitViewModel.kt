package com.juliacai.apptick.newAppLimit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.deviceApps.AppManager
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.groups.TimeRange
import com.juliacai.apptick.TimeManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max

class AppLimitViewModel(application: Application) : AndroidViewModel(application) {

    private val appLimitGroupDao = AppTickDatabase.getDatabase(application).appLimitGroupDao()

    private val _selectedApps = MutableLiveData<List<AppInfo>>(emptyList())
    val selectedApps: LiveData<List<AppInfo>> = _selectedApps

    private val _group = MutableLiveData<AppLimitGroup?>(null)
    val group: LiveData<AppLimitGroup?> = _group

    private val _draft = MutableLiveData<SetTimeLimitDraft?>(null)
    val draft: LiveData<SetTimeLimitDraft?> = _draft

    fun setSelectedApps(apps: List<AppInfo>) {
        _selectedApps.value = apps
    }

    fun clearState() {
        _selectedApps.value = emptyList()
        _group.value = null
        _draft.value = null
    }

    fun loadGroup(groupId: Long) {
        viewModelScope.launch {
            _group.postValue(appLimitGroupDao.getGroup(groupId)?.toDomainModel())
        }
    }

    fun loadGroupForEditing(groupId: Long) {
        viewModelScope.launch {
            val loadedGroup = appLimitGroupDao.getGroup(groupId)?.toDomainModel() ?: return@launch
            _group.postValue(loadedGroup)
            _draft.postValue(null)
            _selectedApps.postValue(
                loadedGroup.apps.map {
                    AppInfo(
                        appName = it.appName,
                        appPackage = it.appPackage
                    )
                }
            )
        }
    }

    fun loadGroupForDuplication(groupId: Long) {
        viewModelScope.launch {
            val loadedGroup = appLimitGroupDao.getGroup(groupId)?.toDomainModel() ?: return@launch
            startDuplicatingGroup(loadedGroup)
        }
    }

    fun startEditingGroup(group: AppLimitGroup) {
        _group.value = group
        _draft.value = null
        _selectedApps.value = group.apps.map {
            AppInfo(
                appName = it.appName,
                appPackage = it.appPackage
            )
        }
    }

    fun startDuplicatingGroup(group: AppLimitGroup) {
        _group.value = duplicateGroupForCreation(group)
        _draft.value = null
        _selectedApps.value = group.apps.map {
            AppInfo(
                appName = it.appName,
                appPackage = it.appPackage
            )
        }
    }

    fun updateDraft(draft: SetTimeLimitDraft) {
        _draft.value = draft
    }

    fun saveGroup(group: AppLimitGroup) {
        viewModelScope.launch {
            // If a category auto-add mode is enabled with includeExistingApps,
            // scan installed apps matching that category and merge them in.
            val groupWithAutoAdded = if (
                group.autoAddMode != AppLimitGroup.AUTO_ADD_NONE &&
                group.autoAddMode != AppLimitGroup.AUTO_ADD_ALL_NEW &&
                group.includeExistingApps
            ) {
                val appContext = getApplication<Application>()
                val appManager = AppManager(appContext)
                val matchingApps = appManager.getInstalledAppsByCategory(group.autoAddMode)
                val existingPackages = group.apps.map { it.appPackage }.toSet()
                val newApps = matchingApps
                    .filter { it.appPackage != null && it.appPackage !in existingPackages }
                    .map { AppInGroup(it.appName, it.appPackage ?: "", it.appPackage ?: "") }
                group.copy(apps = group.apps + newApps)
            } else {
                group
            }

            val allowedPackages = groupWithAutoAdded.apps.map { it.appPackage }.toSet()
            val normalizedUsage = groupWithAutoAdded.perAppUsage
                .filter { it.appPackage in allowedPackages }
                .map { it.copy(usedMillis = max(0L, it.usedMillis)) }

            // Fetch the previous version of this group to detect limit changes.
            val previousGroup = if (groupWithAutoAdded.id != 0L) {
                appLimitGroupDao.getGroup(groupWithAutoAdded.id)?.toDomainModel()
            } else null

            val normalizedGroup = normalizeGroupForPersistence(
                group = groupWithAutoAdded,
                normalizedUsage = normalizedUsage,
                previousGroup = previousGroup
            )

            if (groupWithAutoAdded.id == 0L) {
                appLimitGroupDao.insertAppLimitGroup(normalizedGroup.toEntity())
            } else {
                appLimitGroupDao.updateAppLimitGroup(normalizedGroup.toEntity())
            }
            _draft.postValue(null)

            // Clear the known-packages cache when auto-add is turned off or changed,
            // so re-enabling ALL_NEW later starts fresh.
            if (groupWithAutoAdded.id != 0L) {
                val appContext = getApplication<Application>()
                val prefs = appContext.getSharedPreferences("autoAddPrefs", android.content.Context.MODE_PRIVATE)
                val knownKey = "known_packages_${groupWithAutoAdded.id}"
                if (groupWithAutoAdded.autoAddMode != AppLimitGroup.AUTO_ADD_ALL_NEW) {
                    prefs.edit().remove(knownKey).apply()
                }
            }

            val appContext = getApplication<Application>()
            BackgroundChecker.applyDesiredServiceState(
                appContext,
                appLimitGroupDao.getActiveGroupCount() > 0
            )
        }
    }
}

internal fun duplicateGroupForCreation(group: AppLimitGroup): AppLimitGroup {
    return group.copy(
        id = 0L,
        paused = false,
        timeRemaining = 0L,
        nextResetTime = 0L,
        nextAddTime = 0L,
        perAppUsage = emptyList(),
        isExpanded = true
    )
}

internal fun normalizeGroupForPersistence(
    group: AppLimitGroup,
    normalizedUsage: List<AppUsageStat> = group.perAppUsage,
    previousGroup: AppLimitGroup? = null
): AppLimitGroup {
    val limitInMillis = ((group.timeHrLimit * 60L) + group.timeMinLimit.toLong()).coerceAtLeast(0L) * 60_000L
    val usageTotal = normalizedUsage.sumOf { max(0L, it.usedMillis) }
    val remainingFromUsage = (limitInMillis - usageTotal).coerceAtLeast(0L)
    val persistedRemaining = group.timeRemaining.coerceAtLeast(0L)
    val hasConfiguredLimit = limitInMillis > 0L
    val isNewGroup = group.id == 0L

    // Detect whether the time limit was changed during an edit.
    val limitChanged = previousGroup != null &&
        (group.timeHrLimit != previousGroup.timeHrLimit ||
         group.timeMinLimit != previousGroup.timeMinLimit)

    // Detect whether the reset interval was changed during an edit.
    val resetIntervalChanged = previousGroup != null &&
        group.resetMinutes != previousGroup.resetMinutes

    val effectiveUsage = if (limitChanged || resetIntervalChanged) emptyList() else normalizedUsage

    val normalizedTimeRemaining = when {
        !hasConfiguredLimit -> 0L
        limitChanged -> limitInMillis
        // When reset interval changes, reset the timer so the new schedule takes effect immediately.
        resetIntervalChanged -> limitInMillis
        group.limitEach -> if (isNewGroup && persistedRemaining == 0L) limitInMillis else persistedRemaining
        persistedRemaining > 0L -> persistedRemaining
        else -> remainingFromUsage
    }.let { remaining ->
        // In cumulative mode, carried-over time can legitimately exceed the base limit.
        if (group.cumulativeTime && group.resetMinutes > 0) remaining
        else remaining.coerceAtMost(limitInMillis)
    }

    // Set nextResetTime if unset (0) or already in the past.
    val now = System.currentTimeMillis()
    // Recalculate nextResetTime when the limit or reset interval changes.
    val resetTimeNeedsRefresh = limitChanged || resetIntervalChanged
    val normalizedNextReset = if (!resetTimeNeedsRefresh && group.nextResetTime > now) {
        // Already has a valid future reset time — keep it.
        group.nextResetTime
    } else if (group.resetMinutes > 0) {
        // Periodic: align reset grid to time range start (or midnight).
        TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = group.resetMinutes,
            useTimeRange = group.useTimeRange,
            timeRanges = group.timeRanges,
            nowMillis = now
        )
    } else {
        // Daily mode: next reset = 12:00 AM tomorrow.
        TimeManager.nextMidnight(now)
    }
    val normalizedNextAdd = if (group.cumulativeTime && group.resetMinutes > 0) {
        normalizedNextReset
    } else {
        0L
    }

    return group.copy(
        perAppUsage = effectiveUsage,
        timeRemaining = normalizedTimeRemaining,
        nextResetTime = normalizedNextReset,
        nextAddTime = normalizedNextAdd
    )
}

data class SetTimeLimitDraft(
    val groupName: String,
    val useTimeLimit: Boolean,
    val timeHrLimit: String,
    val timeMinLimit: String,
    val limitEach: Boolean,
    val useTimeRange: Boolean,
    val blockOutsideTimeRange: Boolean,
    val timeRanges: List<TimeRange>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val weekDays: List<Int>,
    val cumulativeTime: Boolean,
    val useReset: Boolean,
    val resetHours: String,
    val resetMinutes: String,
    val autoAddMode: String = AppLimitGroup.AUTO_ADD_NONE,
    val includeExistingApps: Boolean = true
)
