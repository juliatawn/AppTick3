package com.juliacai.apptick.data

import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.TimeRange

fun AppLimitGroupEntity.toDomainModel(): AppLimitGroup {
    return AppLimitGroup(
        id = this.id,
        name = this.name,
        timeHrLimit = this.timeHrLimit,
        timeMinLimit = this.timeMinLimit,
        limitEach = this.limitEach,
        resetMinutes = this.resetMinutes,
        weekDays = this.weekDays,
        apps = this.apps,
        paused = this.paused,
        useTimeRange = this.useTimeRange,
        blockOutsideTimeRange = this.blockOutsideTimeRange,
        timeRanges = this.effectiveTimeRanges(),
        startHour = this.startHour,
        startMinute = this.startMinute,
        endHour = this.endHour,
        endMinute = this.endMinute,
        cumulativeTime = this.cumulativeTime,
        timeRemaining = this.timeRemaining,
        nextResetTime = this.nextResetTime,
        nextAddTime = this.nextAddTime,
        perAppUsage = this.perAppUsage,
        isExpanded = this.isExpanded
    )
}

fun AppLimitGroup.toEntity(): AppLimitGroupEntity {
    val firstRange = timeRanges.firstOrNull()
    return AppLimitGroupEntity(
        id = this.id,
        name = this.name,
        timeHrLimit = this.timeHrLimit,
        timeMinLimit = this.timeMinLimit,
        limitEach = this.limitEach,
        resetMinutes = this.resetMinutes,
        weekDays = this.weekDays,
        apps = this.apps,
        paused = this.paused,
        useTimeRange = this.useTimeRange,
        blockOutsideTimeRange = this.blockOutsideTimeRange,
        timeRanges = this.timeRanges,
        startHour = firstRange?.startHour ?: this.startHour,
        startMinute = firstRange?.startMinute ?: this.startMinute,
        endHour = firstRange?.endHour ?: this.endHour,
        endMinute = firstRange?.endMinute ?: this.endMinute,
        cumulativeTime = this.cumulativeTime,
        timeRemaining = this.timeRemaining,
        nextResetTime = this.nextResetTime,
        nextAddTime = this.nextAddTime,
        perAppUsage = this.perAppUsage,
        isExpanded = this.isExpanded
    )
}

private fun AppLimitGroupEntity.effectiveTimeRanges(): List<TimeRange> {
    if (timeRanges.isNotEmpty()) return timeRanges
    if (!useTimeRange) return emptyList()
    return listOf(
        TimeRange(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute
        )
    )
}
