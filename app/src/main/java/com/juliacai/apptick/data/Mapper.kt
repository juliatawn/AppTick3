package com.juliacai.apptick.data

import com.juliacai.apptick.groups.AppLimitGroup

fun AppLimitGroupEntity.toDomainModel(): AppLimitGroup {
    return AppLimitGroup(
        id = this.id,
        name = this.name,
        timeHrLimit = this.timeHrLimit,
        timeMinLimit = this.timeMinLimit,
        limitEach = this.limitEach,
        resetHours = this.resetHours,
        weekDays = this.weekDays,
        apps = this.apps,
        paused = this.paused,
        useTimeRange = this.useTimeRange,
        startHour = this.startHour,
        startMinute = this.startMinute,
        endHour = this.endHour,
        endMinute = this.endMinute,
        cumulativeTime = this.cumulativeTime,
        timeRemaining = this.timeRemaining,
        nextResetTime = this.nextResetTime,
        nextAddTime = this.nextAddTime,
        dwm = this.dwm
    )
}

fun AppLimitGroup.toEntity(): AppLimitGroupEntity {
    return AppLimitGroupEntity(
        id = this.id,
        name = this.name,
        timeHrLimit = this.timeHrLimit,
        timeMinLimit = this.timeMinLimit,
        limitEach = this.limitEach,
        resetHours = this.resetHours,
        weekDays = this.weekDays,
        apps = this.apps,
        paused = this.paused,
        useTimeRange = this.useTimeRange,
        startHour = this.startHour,
        startMinute = this.startMinute,
        endHour = this.endHour,
        endMinute = this.endMinute,
        cumulativeTime = this.cumulativeTime,
        timeRemaining = this.timeRemaining,
        nextResetTime = this.nextResetTime,
        nextAddTime = this.nextAddTime,
        dwm = this.dwm
    )
}
