package com.juliacai.apptick.newAppLimit

import com.juliacai.apptick.AppInfo

internal fun AppInfo.selectionKey(): String = appPackage ?: appName

internal fun isAppSelected(app: AppInfo, selectedApps: List<AppInfo>): Boolean {
    val key = app.selectionKey()
    return selectedApps.any { it.selectionKey() == key }
}

internal fun toggleSelectedApp(app: AppInfo, selectedApps: List<AppInfo>): List<AppInfo> {
    val key = app.selectionKey()
    val mutable = selectedApps.toMutableList()
    if (mutable.any { it.selectionKey() == key }) {
        mutable.removeAll { it.selectionKey() == key }
    } else {
        mutable.add(app)
    }
    return mutable
}
