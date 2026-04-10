package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.BaseActivity
import com.juliacai.apptick.lazyColumnScrollIndicator
import com.juliacai.apptick.rememberScrollbarColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.util.Calendar

class AppUsagePage : BaseActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE) ?: ""

        AppUsageStats.initialize(this)

        setContent {
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val palette = AppTheme.currentPalette(this, isSystemDark)
            val colorScheme = AppTheme.colorSchemeFromPalette(palette)

            MaterialTheme(colorScheme = colorScheme) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "AppTick",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { padding ->
                    AppUsageContent(
                        appName = appName,
                        appPackage = appPackage,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_APP_PACKAGE = "extra_app_package"

        fun newIntent(context: Context, appName: String, appPackage: String): Intent {
            return Intent(context, AppUsagePage::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_APP_PACKAGE, appPackage)
            }
        }
    }
}

/** Maximum number of periods the user can go back. */
private const val MAX_OFFSET = 52

@Composable
private fun AppUsageContent(
    appName: String,
    appPackage: String,
    modifier: Modifier = Modifier
) {
    val periods = UsagePeriod.entries
    var selectedTab by remember { mutableIntStateOf(0) }

    // Each tab has its own offset (how far back in time the user has scrolled).
    // mutableStateMapOf so mutations trigger recomposition.
    val offsets = remember { mutableStateMapOf<UsagePeriod, Int>() }
    val selectedPeriod = periods[selectedTab]
    val currentOffset = offsets[selectedPeriod] ?: 0

    // Data states — keyed by (period, offset) to cache
    var totalUsage by remember { mutableStateOf(0L) }
    var weeklyBreakdown by remember { mutableStateOf<List<DailyUsage>>(emptyList()) }
    var monthlyDailyBreakdown by remember { mutableStateOf<List<DailyUsage>>(emptyList()) }
    var yearlyBreakdown by remember { mutableStateOf<List<MonthlyUsage>>(emptyList()) }
    var yearDaysWithData by remember { mutableIntStateOf(0) }

    // Overview data (always for offset=0)
    var overviewMillis by remember { mutableStateOf<Map<UsagePeriod, Long>>(emptyMap()) }

    // Load overview data once (uses local DB when available)
    LaunchedEffect(appPackage) {
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<UsagePeriod, Long>()
            for (period in periods) {
                result[period] = AppUsageStats.getUsageForPeriodLocal(appPackage, period, 0)
            }
            overviewMillis = result
        }
    }

    // Load data for the selected tab+offset (prefers local DB, falls back to Android)
    LaunchedEffect(appPackage, selectedTab, currentOffset) {
        withContext(Dispatchers.IO) {
            totalUsage = AppUsageStats.getUsageForPeriodLocal(appPackage, selectedPeriod, currentOffset)
            when (selectedPeriod) {
                UsagePeriod.DAY -> { /* no breakdown for single day */ }
                UsagePeriod.WEEK -> {
                    weeklyBreakdown = AppUsageStats.getWeeklyDailyBreakdownLocal(appPackage, currentOffset)
                }
                UsagePeriod.MONTH -> {
                    monthlyDailyBreakdown = AppUsageStats.getMonthlyDailyBreakdownLocal(appPackage, currentOffset)
                }
                UsagePeriod.YEAR -> {
                    yearlyBreakdown = AppUsageStats.getYearlyMonthlyBreakdownLocal(appPackage, currentOffset)
                    yearDaysWithData = AppUsageStats.countDaysWithData(appPackage, UsagePeriod.YEAR, currentOffset)
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val scrollbarColor = rememberScrollbarColor()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .lazyColumnScrollIndicator(listState, scrollbarColor),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AppHeaderCard(appName = appName, appPackage = appPackage)
        }

        item {
            Spacer(Modifier.height(4.dp))
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {}
            ) {
                periods.forEachIndexed { index, period ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = period.label,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        // Period navigation (left/right arrows with label)
        item {
            PeriodNavigator(
                period = selectedPeriod,
                offset = currentOffset,
                onPrevious = {
                    if (currentOffset < MAX_OFFSET) {
                        offsets[selectedPeriod] = currentOffset + 1
                    }
                },
                onNext = {
                    if (currentOffset > 0) {
                        offsets[selectedPeriod] = currentOffset - 1
                    }
                }
            )
        }

        item {
            // Count days that have actual usage data (not zero) for accurate daily avg.
            val daysWithData = when (selectedPeriod) {
                UsagePeriod.WEEK -> weeklyBreakdown.count { it.usageMillis > 0 }
                UsagePeriod.MONTH -> monthlyDailyBreakdown.count { it.usageMillis > 0 }
                UsagePeriod.YEAR -> yearDaysWithData // queried from local DB
                UsagePeriod.DAY -> 0
            }
            UsageDetailCard(
                period = selectedPeriod,
                usageMillis = totalUsage,
                daysWithData = daysWithData
            )
        }

        // Breakdown card for the selected period.
        // Android only retains per-day stats for ~7-10 days. If daily data is
        // unavailable (all days zero) but the period total is > 0, show a note.
        item {
            when (selectedPeriod) {
                UsagePeriod.WEEK -> {
                    val hasDailyData = weeklyBreakdown.any { it.usageMillis > 0 }
                    if (hasDailyData) {
                        WeeklyBreakdownCard(dailyUsages = weeklyBreakdown)
                    } else if (totalUsage > 0) {
                        DailyDataUnavailableCard()
                    }
                }
                UsagePeriod.MONTH -> {
                    val hasDailyData = monthlyDailyBreakdown.any { it.usageMillis > 0 }
                    if (hasDailyData) {
                        MonthCalendarCard(dailyUsages = monthlyDailyBreakdown)
                    } else if (totalUsage > 0) {
                        DailyDataUnavailableCard()
                    }
                }
                UsagePeriod.YEAR -> YearlyBreakdownCard(monthlyUsages = yearlyBreakdown)
                UsagePeriod.DAY -> {
                    if (totalUsage == 0L && currentOffset > 7) {
                        DailyDataUnavailableCard()
                    }
                }
            }
        }

        item {
            UsageOverviewCard(usageMillis = overviewMillis)
        }
    }
}

// ── Period Navigator ────────────────────────────────────────────────────

@Composable
internal fun PeriodNavigator(
    period: UsagePeriod,
    offset: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val label = AppUsageStats.periodLabel(period, offset)
    val dateRange = AppUsageStats.periodDateRange(period, offset)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = offset < MAX_OFFSET
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous"
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNext,
                enabled = offset > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next"
                )
            }
        }
    }
}

// ── App Header ──────────────────────────────────────────────────────────

@Composable
internal fun AppHeaderCard(appName: String, appPackage: String) {
    val context = LocalContext.current
    val iconBitmap = remember(appPackage) {
        try {
            val drawable = context.packageManager.getApplicationIcon(appPackage)
            val bitmap = createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1)
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = "$appName icon",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

// ── Usage Detail Card ───────────────────────────────────────────────────

@Composable
internal fun UsageDetailCard(period: UsagePeriod, usageMillis: Long, daysWithData: Int = 0) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = period.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = formatUsageDuration(usageMillis),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            val maxExpectedMillis = when (period) {
                UsagePeriod.DAY -> 8 * 3_600_000L
                UsagePeriod.WEEK -> 56 * 3_600_000L
                UsagePeriod.MONTH -> 240 * 3_600_000L
                UsagePeriod.YEAR -> 2920 * 3_600_000L
            }
            val progress = (usageMillis.toFloat() / maxExpectedMillis.toFloat()).coerceIn(0f, 1f)

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            HorizontalDivider()

            val avgLabel = when (period) {
                UsagePeriod.DAY -> null
                UsagePeriod.WEEK -> "Daily avg"
                UsagePeriod.MONTH -> "Daily avg"
                UsagePeriod.YEAR -> "Daily avg"
            }
            if (avgLabel != null) {
                // Use actual days with recorded data when available,
                // so "no data" days (beyond Android retention) don't drag the average down.
                val avgMillis = if (daysWithData > 0) {
                    usageMillis / daysWithData
                } else when (period) {
                    UsagePeriod.WEEK -> usageMillis / 7
                    UsagePeriod.MONTH -> usageMillis / 30
                    UsagePeriod.YEAR -> usageMillis / 365
                    else -> 0L
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = avgLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatUsageDuration(avgMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Daily Data Unavailable ──────────────────────────────────────────────

@Composable
internal fun DailyDataUnavailableCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Text(
            text = "Daily breakdown unavailable — Android only retains per-day data for about 7-10 days. The total for this period is still shown above.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// ── Weekly Breakdown ────────────────────────────────────────────────────

@Composable
internal fun WeeklyBreakdownCard(dailyUsages: List<DailyUsage>) {
    if (dailyUsages.isEmpty()) return

    val dayNames = DateFormatSymbols.getInstance().weekdays // full names (e.g. "Monday")
    val maxMillis = dailyUsages.maxOf { it.usageMillis }.coerceAtLeast(1L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Daily Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            for (day in dailyUsages) {
                val dayLabel = dayNames[day.dayOfWeek] ?: ""
                val dateLabel = "%s %d/%d".format(dayLabel, day.month + 1, day.dayOfMonth)
                val barProgress = (day.usageMillis.toFloat() / maxMillis.toFloat()).coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatUsageDuration(day.usageMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

// ── Month Calendar View ─────────────────────────────────────────────────

@Composable
internal fun MonthCalendarCard(dailyUsages: List<DailyUsage>) {
    if (dailyUsages.isEmpty()) return

    val maxMillis = dailyUsages.maxOf { it.usageMillis }.coerceAtLeast(1L)

    val currentMonth = dailyUsages.first()

    val monthNames = DateFormatSymbols.getInstance().months
    val monthLabel = "${monthNames[currentMonth.month]} ${currentMonth.year}"

    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentMonth.year)
        set(Calendar.MONTH, currentMonth.month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val usageByDay = mutableMapOf<Int, Long>()
    for (du in dailyUsages) {
        if (du.year == currentMonth.year && du.month == currentMonth.month) {
            usageByDay[du.dayOfMonth] = du.usageMillis
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Monday-first day headers to match Mon-Sun week convention
            val dayHeaders = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dayHeaders.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Convert Sunday=1..Saturday=7 to Monday-first offset (Monday=0..Sunday=6)
            val mondayOffset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - Calendar.MONDAY
            val totalCells = mondayOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - mondayOffset + 1

                        if (dayNum in 1..daysInMonth) {
                            val millis = usageByDay[dayNum] ?: 0L
                            val intensity = if (maxMillis > 0) {
                                (millis.toFloat() / maxMillis.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            CalendarDayCell(
                                dayNum = dayNum,
                                usageMillis = millis,
                                intensity = intensity,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNum: Int,
    usageMillis: Long,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val bgColor = if (intensity > 0f) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + intensity * 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNum.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
            if (usageMillis > 0) {
                Text(
                    text = formatUsageDurationShort(usageMillis),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

// ── Year Breakdown ──────────────────────────────────────────────────────

@Composable
internal fun YearlyBreakdownCard(monthlyUsages: List<MonthlyUsage>) {
    if (monthlyUsages.isEmpty()) return

    val monthNames = DateFormatSymbols.getInstance().shortMonths
    val maxMillis = monthlyUsages.maxOf { it.usageMillis }.coerceAtLeast(1L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Monthly Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            for (mu in monthlyUsages) {
                val label = "${monthNames[mu.month]} ${mu.year}"
                val barProgress = (mu.usageMillis.toFloat() / maxMillis.toFloat()).coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatUsageDuration(mu.usageMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

// ── Overview Card ───────────────────────────────────────────────────────

@Composable
internal fun UsageOverviewCard(usageMillis: Map<UsagePeriod, Long>) {
    if (usageMillis.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            val maxMillis = usageMillis.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L

            for (period in UsagePeriod.entries) {
                val millis = usageMillis[period] ?: 0L
                val barProgress = (millis.toFloat() / maxMillis.toFloat()).coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatUsageDuration(millis),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )

                    val dailyAvg = when (period) {
                        UsagePeriod.DAY -> null
                        UsagePeriod.WEEK -> millis / 7
                        UsagePeriod.MONTH -> millis / 30
                        UsagePeriod.YEAR -> millis / 365
                    }
                    if (dailyAvg != null) {
                        Text(
                            text = "Avg ${formatUsageDuration(dailyAvg)}/day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Formatting ──────────────────────────────────────────────────────────

internal fun formatUsageDuration(millis: Long): String {
    if (millis <= 0) return "No usage"
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

internal fun formatUsageDurationShort(millis: Long): String {
    if (millis <= 0) return ""
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h${if (minutes > 0) "${minutes}m" else ""}"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
