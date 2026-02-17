package com.juliacai.apptick

/**
 * This interface defines the contract for settings related to an app limit group.
 * It is likely implemented by an Activity or Fragment that guides the user through setting up a limit.
 */
interface AppLimitSettings {

    /** The list of applications selected for this limit group. */
    var selected: List<AppInfo>

    /** The time limit in minutes. */
    var timeMinLimit: Int

    /** The time limit in hours. */
    var timeHrLimit: Int

    /**
     * The days of the week when this limit is active.
     * Each integer typically represents a day (e.g., 1 for Monday, 7 for Sunday).
     */
    var weekDays: List<Int>

    /** If true, the time limit is applied to each app individually. Otherwise, it's a shared limit. */
    var limitEach: Boolean

    /** If true, unused time rolls over to the next period. */
    var cumulativeTime: Boolean

    /** The user-defined name for this limit group. */
    var groupName: String?

    /** The interval in hours after which the time usage resets. */
    var resetMinutes: Int

    /** If true, the app limit is only active within a specific time range. */
    var useTimeRange: Boolean

    /** If true, selected apps are fully blocked outside the configured time range. */
    var blockOutsideTimeRange: Boolean

    /** The start hour for the time range (0-23). */
    var startHour: Int

    /** The start minute for the time range (0-59). */
    var startMinute: Int

    /** The end hour for the time range (0-23). */
    var endHour: Int

    /** The end minute for the time range (0-59). */
    var endMinute: Int

    /** If true, the app limit group is temporarily paused. */
    var paused: Boolean

    /** Called when the user has finished configuring the settings. */
    fun finished()

    /** Navigates to a specific page or step in the settings configuration UI. */
    fun setWhichPage(index: Int)

    /** Enables or disables paging/swiping in the configuration UI. */
    fun setPagingEnabled(enabled: Boolean)
}
