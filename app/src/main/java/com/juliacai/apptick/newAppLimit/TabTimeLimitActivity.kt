//package com.juliacai.apptick.newAppLimit
//
//import android.content.Intent
//import android.os.Bundle
//import android.widget.Toast
//import androidx.activity.compose.setContent
//import androidx.activity.viewModels
//import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.foundation.ExperimentalFoundationApi
//import androidx.compose.foundation.pager.HorizontalPager
//import androidx.compose.foundation.pager.rememberPagerState
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.ui.Modifier
//import androidx.lifecycle.lifecycleScope
//import com.juliacai.apptick.AppInfo
//import com.juliacai.apptick.AppLimitSettings
//import com.juliacai.apptick.AppSelectScreen
//import com.juliacai.apptick.MainActivity
//import com.juliacai.apptick.appLimit.AppInGroup
//import com.juliacai.apptick.groups.AppLimitGroup
//import com.juliacai.apptick.data.AppTickDatabase
//import com.juliacai.apptick.data.toDomainModel
//import com.juliacai.apptick.data.toEntity
//import com.juliacai.apptick.deviceApps.AppListViewModel
//import kotlinx.coroutines.launch
//
//@OptIn(ExperimentalFoundationApi::class)
//class TabTimeLimitActivity : AppCompatActivity(), AppLimitSettings {
//
//    private val db by lazy { AppTickDatabase.getDatabase(this) }
//    private val appLimitGroupDao by lazy { db.appLimitGroupDao() }
//    private val appListViewModel: AppListViewModel by viewModels()
//
//    private var isEditMode = false
//    private var editingGroupId: Long = -1
//
//    override var selected: List<AppInfo> = mutableListOf()
//    override var timeMinLimit: Int = 0
//    override var timeHrLimit: Int = 0
//    override var weekDays: List<Int> = mutableListOf()
//    override var limitEach: Boolean = false
//    override var cumulativeTime: Boolean = false
//    override var groupName: String? = null
//    override var resetHours: Int = 0
//    override var useTimeRange: Boolean = false
//    override var startHour: Int = 0
//    override var startMinute: Int = 0
//    override var endHour: Int = 23
//    override var endMinute: Int = 59
//    override var paused: Boolean = false
//    override var dwm: String? = "Daily"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        isEditMode = intent.getBooleanExtra("isEdit", false)
//        if (isEditMode) {
//            editingGroupId = intent.getLongExtra("groupId", -1)
//            if (editingGroupId != -1L) {
//                loadExistingGroupData(editingGroupId)
//            }
//        }
//
//        setContent {
//            val pagerState = rememberPagerState(pageCount = { 2 })
//            val scope = rememberCoroutineScope()
//
//            HorizontalPager(state = pagerState, modifier = Modifier) {
//                when(it) {
//                    0 -> AppSelectScreen(viewModel = appListViewModel, onNextClick = {
//                        selected = appListViewModel.selectedApps.value
//                        scope.launch {
//                            pagerState.animateScrollToPage(1)
//                        }
//                    })
//                    1 -> SetTimeLimitsScreen(settings = this@TabTimeLimitActivity, onFinish = { finished() }, onCancel = { finish() })
//                }
//            }
//        }
//    }
//
//    private fun loadExistingGroupData(groupId: Long) {
//        lifecycleScope.launch {
//            val group = appLimitGroupDao.getGroup(groupId)?.toDomainModel() ?: return@launch
//
//            groupName = group.name
//            timeHrLimit = group.timeHrLimit
//            timeMinLimit = group.timeMinLimit
//            limitEach = group.limitEach
//            resetHours = group.resetHours
//            weekDays = group.weekDays
//            selected = group.apps.map { appInGroup ->
//                AppInfo(
//                    appName = appInGroup.appName,
//                    appPackage = appInGroup.appPackage,
//                    appIcon = null
//                )
//            }
//            appListViewModel.setInitialSelection(selected)
//            useTimeRange = group.useTimeRange
//            startHour = group.startHour
//            startMinute = group.startMinute
//            endHour = group.endHour
//            endMinute = group.endMinute
//            paused = group.paused
//            cumulativeTime = group.cumulativeTime
//            dwm = group.dwm
//        }
//    }
//
//    override fun finished() {
//        if (selected.isEmpty()) {
//            Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val finalGroupName = if (groupName.isNullOrBlank()) "GROUP" else groupName!!
//        val appsInGroup = selected.mapNotNull { appInfo ->
//            appInfo.appName?.let { appName ->
//                appInfo.appPackage?.let { appPackage ->
//                    AppInGroup(appName, appPackage, appPackage)
//                }
//            }
//        }
//
//        val group = AppLimitGroup(
//            id = if (isEditMode) editingGroupId else 0,
//            name = finalGroupName,
//            timeHrLimit = timeHrLimit,
//            timeMinLimit = timeMinLimit,
//            limitEach = limitEach,
//            resetHours = resetHours,
//            weekDays = weekDays,
//            apps = appsInGroup,
//            paused = paused,
//            useTimeRange = useTimeRange,
//            startHour = startHour,
//            startMinute = startMinute,
//            endHour = endHour,
//            endMinute = endMinute,
//            cumulativeTime = cumulativeTime,
//            dwm = dwm
//        )
//
//        lifecycleScope.launch {
//            if (isEditMode) {
//                appLimitGroupDao.updateAppLimitGroup(group.toEntity())
//            } else {
//                appLimitGroupDao.insertAppLimitGroup(group.toEntity())
//            }
//
//            val intent = Intent(this@TabTimeLimitActivity, MainActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            }
//            startActivity(intent)
//            finish()
//        }
//    }
//
//    override fun setWhichPage(index: Int) {}
//
//    override fun setPagingEnabled(enabled: Boolean) {}
//}
