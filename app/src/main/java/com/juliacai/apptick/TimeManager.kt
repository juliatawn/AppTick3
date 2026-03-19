package com.juliacai.apptick

import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.TimeRange
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TimeManager(private val group: AppLimitGroup) {

    fun getTimeRemaining(): Long {
        val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
        return TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
    }

    /**
     * Returns the next reset time for this group.
     * - If a periodic reset interval is set (`resetMinutes > 0`), returns `now + resetMinutes` in millis.
     * - Otherwise, defaults to midnight (12:00 AM) tomorrow in the device's timezone.
     */
    fun getNextResetTime(): Long {
        return if (group.resetMinutes > 0) {
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(group.resetMinutes.toLong())
        } else {
            nextMidnight()
        }
    }

    companion object {
        /**
         * Returns epoch millis for 12:00 AM tomorrow in the device's default timezone.
         * Uses [Calendar] which inherits the device locale and timezone automatically.
         */
        fun nextMidnight(nowMillis: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        /**
         * Computes the next time the app will be unblocked, accounting for time ranges,
         * active days, reset schedules, and the reason for blocking.
         *
         * @param group The limit group configuration.
         * @param nowMillis Current time in epoch millis.
         * @param blockedForOutsideRange True if blocked because current time is outside
         *        the configured time range (with blockOutsideTimeRange enabled).
         * @return Epoch millis of next unblock, or 0L if no unblock is scheduled.
         */
        fun computeNextUnblockTime(
            group: AppLimitGroup,
            nowMillis: Long,
            blockedForOutsideRange: Boolean
        ): Long {
            val ranges = group.getConfiguredTimeRanges()
            val hasTimeRange = group.useTimeRange && ranges.isNotEmpty()
            val limitMinutes = group.timeHrLimit * 60 + group.timeMinLimit
            val isZeroLimit = limitMinutes <= 0

            if (blockedForOutsideRange) {
                // Blocked because we're outside the configured time range.
                // Next unblock = when the next time range starts on an active day.
                return nextTimeRangeEntry(ranges, nowMillis, group.weekDays)
            }

            if (isZeroLimit) {
                // Zero limit = always blocked whenever the limit is active.
                if (hasTimeRange && !group.blockOutsideTimeRange) {
                    // Only blocked during the time range; unblocked when range ends.
                    return currentTimeRangeEnd(ranges, nowMillis)
                }
                // No time range, or also blocked outside range → always blocked.
                return 0L
            }

            // Non-zero limit that has been reached.
            if (hasTimeRange && !group.blockOutsideTimeRange) {
                // Unblocked at whichever comes first: limit reset or time range end.
                val rangeEnd = currentTimeRangeEnd(ranges, nowMillis)
                return if (rangeEnd in 1 until group.nextResetTime) rangeEnd else group.nextResetTime
            }

            if (hasTimeRange && group.blockOutsideTimeRange) {
                // User needs BOTH: limit reset AND to be within the time range.
                val nextReset = group.nextResetTime
                if (nextReset <= 0L) return 0L

                // If the reset happens while inside a time range, user is unblocked at reset.
                if (currentTimeRangeEnd(ranges, nextReset) > 0L) {
                    return nextReset
                }

                // Reset occurs outside the range. The reset will have already fired by
                // the time the next range starts, so the user is unblocked at range start.
                val nextEntry = nextTimeRangeEntry(ranges, nextReset, group.weekDays)
                return if (nextEntry > 0L) nextEntry else 0L
            }

            return group.nextResetTime
        }

        /**
         * Computes the effective next reset/available time for display on the Group Details page.
         *
         * Handles two cases where the raw nextResetTime is misleading:
         * 1. **Zero limit + time range + Allow No Limits:** Reset is meaningless (0 resets to 0).
         *    Shows when the current range ends (when the app becomes available).
         * 2. **Non-zero limit + time range + reset outside range:** Shows the next range start
         *    after the reset, since the reset outside the active window isn't useful.
         *
         * @param group The limit group configuration.
         * @param nowMillis Current time in epoch millis (for zero-limit range-end lookup).
         * @return Epoch millis of the effective time, or the raw nextResetTime if no
         *         adjustment is needed.
         */
        fun computeEffectiveNextReset(
            group: AppLimitGroup,
            nowMillis: Long = System.currentTimeMillis()
        ): Long {
            val nextReset = group.nextResetTime
            val ranges = group.getConfiguredTimeRanges()
            val hasTimeRange = group.useTimeRange && ranges.isNotEmpty()
            val limitMinutes = group.timeHrLimit * 60 + group.timeMinLimit
            val isZeroLimit = limitMinutes <= 0

            // Zero limit: reset is meaningless (0 resets to 0).
            if (isZeroLimit) {
                if (hasTimeRange && !group.blockOutsideTimeRange) {
                    // Blocked during range, free outside → show when blocking lifts.
                    // If currently in range, show when this range ends.
                    val rangeEnd = currentTimeRangeEnd(ranges, nowMillis)
                    if (rangeEnd > 0L) return rangeEnd
                    // Not in range: find when the NEXT range ends.
                    val nextStart = nextTimeRangeEntry(ranges, nowMillis, group.weekDays)
                    if (nextStart > 0L) {
                        val nextRangeEnd = currentTimeRangeEnd(ranges, nextStart)
                        if (nextRangeEnd > 0L) return nextRangeEnd
                    }
                    return nextReset
                }
                // Always blocked (blockOutside or no range) → reset doesn't help.
                return 0L
            }

            if (nextReset <= 0L) return nextReset
            if (!hasTimeRange) return nextReset

            // Check if the reset time falls within a time range
            if (currentTimeRangeEnd(ranges, nextReset) > 0L) {
                return nextReset // Reset happens inside a range — useful as-is
            }

            // Reset falls outside all ranges. Show when the next range starts instead.
            val nextEntry = nextTimeRangeEntry(ranges, nextReset, group.weekDays)
            return if (nextEntry > 0L) nextEntry else nextReset
        }

        /**
         * Finds the soonest future time when any of the configured time ranges starts,
         * considering active days. Searches up to 7 days ahead.
         *
         * @return Epoch millis of next range entry, or 0L if none found.
         */
        fun nextTimeRangeEntry(
            ranges: List<TimeRange>,
            nowMillis: Long,
            weekDays: List<Int>
        ): Long {
            if (ranges.isEmpty()) return 0L

            var earliest = Long.MAX_VALUE

            for (range in ranges) {
                val candidate = nextOccurrenceOfTime(
                    hour = range.startHour,
                    minute = range.startMinute,
                    nowMillis = nowMillis,
                    weekDays = weekDays
                )
                if (candidate in 1 until earliest) {
                    earliest = candidate
                }
            }

            return if (earliest == Long.MAX_VALUE) 0L else earliest
        }

        /**
         * Finds when the currently active time range ends.
         * Checks which range(s) contain "now" and returns the soonest end time.
         *
         * For overnight ranges (e.g. 22:00-06:00) where now is before midnight,
         * the end is tomorrow. Where now is after midnight, the end is today.
         *
         * @return Epoch millis of range end, or 0L if not currently in any range.
         */
        fun currentTimeRangeEnd(
            ranges: List<TimeRange>,
            nowMillis: Long
        ): Long {
            if (ranges.isEmpty()) return 0L

            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val nowMinutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            var earliest = Long.MAX_VALUE

            for (range in ranges) {
                val startMin = range.startHour * 60 + range.startMinute
                val endMin = range.endHour * 60 + range.endMinute

                val isInRange = if (startMin <= endMin) {
                    nowMinutesOfDay in startMin..endMin
                } else {
                    nowMinutesOfDay >= startMin || nowMinutesOfDay <= endMin
                }

                if (!isInRange) continue

                val endCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, range.endHour)
                    set(Calendar.MINUTE, range.endMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (startMin > endMin && nowMinutesOfDay >= startMin) {
                    // Overnight range, we're in the before-midnight part → end is tomorrow.
                    endCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                // For overnight range where nowMinutesOfDay <= endMin, end is today — no adjustment.

                if (endCal.timeInMillis < earliest) {
                    earliest = endCal.timeInMillis
                }
            }

            return if (earliest == Long.MAX_VALUE) 0L else earliest
        }

        /**
         * Finds the next occurrence of [hour]:[minute] from [nowMillis],
         * only considering active days. Searches up to 7 days.
         *
         * @return Epoch millis, or 0L if no active day found within 7 days.
         */
        fun nextOccurrenceOfTime(
            hour: Int,
            minute: Int,
            nowMillis: Long,
            weekDays: List<Int>
        ): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If that time already passed today, start from tomorrow.
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            for (i in 0 until 7) {
                val dayMondayOne = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                if (weekDays.isEmpty() || weekDays.contains(dayMondayOne)) {
                    return cal.timeInMillis
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            return 0L
        }
    }
}
