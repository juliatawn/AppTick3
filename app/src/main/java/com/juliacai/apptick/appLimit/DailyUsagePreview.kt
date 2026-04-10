package com.juliacai.apptick.appLimit

import com.juliacai.apptick.groups.TimeRange

/**
 * Calculates the total available usage time per day for display on the Set Time Limits screen.
 *
 * Takes into account: time limit per period, reset interval, time ranges, and
 * whether apps are blocked or unrestricted outside time ranges.
 */
object DailyUsagePreview {

    data class PreviewResult(
        /** Total available minutes in the day. -1 means unlimited (no cap). */
        val totalMinutes: Int,
        /** Number of reset periods that fit in the active window. */
        val periodCount: Int,
        /** Minutes per reset period (the configured limit). */
        val minutesPerPeriod: Int,
        /** Active window duration in minutes (time range total, or 1440 for full day). */
        val activeWindowMinutes: Int
    )

    /**
     * Compute the daily usage preview.
     *
     * @param timeLimitHours  Configured hours of limit per period.
     * @param timeLimitMinutes Configured minutes of limit per period.
     * @param useTimeLimit    Whether a time limit is enabled at all.
     * @param resetIntervalMinutes Total reset interval in minutes (0 = daily reset / no periodic).
     * @param useTimeRange    Whether time ranges are active.
     * @param blockOutsideTimeRange True = Block Apps outside range; False = No Limits outside range.
     * @param timeRanges      The configured time ranges.
     * @param cumulativeTime  Whether cumulative time mode is enabled.
     */
    fun calculate(
        timeLimitHours: Int,
        timeLimitMinutes: Int,
        useTimeLimit: Boolean,
        resetIntervalMinutes: Int,
        useTimeRange: Boolean,
        blockOutsideTimeRange: Boolean,
        timeRanges: List<TimeRange>,
        cumulativeTime: Boolean
    ): PreviewResult {
        val limitPerPeriod = timeLimitHours * 60 + timeLimitMinutes

        // No time limit enabled means always blocked — 0 minutes available.
        if (!useTimeLimit) {
            return PreviewResult(
                totalMinutes = 0,
                periodCount = 0,
                minutesPerPeriod = 0,
                activeWindowMinutes = 0
            )
        }

        val activeWindowMinutes = if (useTimeRange && timeRanges.isNotEmpty()) {
            totalTimeRangeMinutes(timeRanges)
        } else {
            FULL_DAY_MINUTES
        }

        // No periodic reset — single period for the whole active window.
        if (resetIntervalMinutes <= 0) {
            val total = if (useTimeRange && !blockOutsideTimeRange) {
                // "No Limits" outside range: unlimited outside, limited inside.
                // Preview only shows the constrained portion.
                limitPerPeriod.coerceAtMost(activeWindowMinutes)
            } else {
                limitPerPeriod.coerceAtMost(activeWindowMinutes)
            }
            return PreviewResult(
                totalMinutes = total,
                periodCount = 1,
                minutesPerPeriod = limitPerPeriod,
                activeWindowMinutes = activeWindowMinutes
            )
        }

        // Periodic reset: count how many periods fit in the active window.
        val periodCount = activeWindowMinutes / resetIntervalMinutes
        // If the active window isn't an exact multiple, there's a partial period at the end.
        val remainderMinutes = activeWindowMinutes % resetIntervalMinutes
        val partialPeriodMinutes = if (remainderMinutes > 0) {
            limitPerPeriod.coerceAtMost(remainderMinutes)
        } else {
            0
        }

        val totalFromFullPeriods = periodCount * limitPerPeriod
        val rawTotal = totalFromFullPeriods + partialPeriodMinutes

        // Can't use more time than actually exists in the active window.
        val total = rawTotal.coerceAtMost(activeWindowMinutes)

        return PreviewResult(
            totalMinutes = total,
            periodCount = periodCount + if (remainderMinutes > 0) 1 else 0,
            minutesPerPeriod = limitPerPeriod,
            activeWindowMinutes = activeWindowMinutes
        )
    }

    /**
     * Calculate the total minutes covered by the given time ranges in a single day.
     * Handles overnight ranges (e.g., 22:00-06:00 = 480 minutes).
     * Does NOT handle overlapping ranges — they are summed independently.
     */
    internal fun totalTimeRangeMinutes(ranges: List<TimeRange>): Int {
        return ranges.sumOf { range ->
            val startMin = range.startHour * 60 + range.startMinute
            val endMin = range.endHour * 60 + range.endMinute
            if (endMin >= startMin) {
                endMin - startMin
            } else {
                // Overnight range: time until midnight + time after midnight
                (FULL_DAY_MINUTES - startMin) + endMin
            }
        }
    }

    /**
     * Format the preview result as a human-readable string.
     */
    fun formatPreview(result: PreviewResult): String {
        if (result.totalMinutes <= 0) return "0 min/day"
        val hours = result.totalMinutes / 60
        val minutes = result.totalMinutes % 60
        return buildString {
            if (hours > 0) {
                append("${hours}hr")
                if (minutes > 0) append(" ${minutes}min")
            } else {
                append("${minutes}min")
            }
            append("/day")
        }
    }

    private const val FULL_DAY_MINUTES = 24 * 60
}
