package com.juliacai.apptick

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun rememberScrollbarColor(): Color {
    return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
}

fun Modifier.verticalScrollWithIndicator(
    scrollState: ScrollState,
    indicatorColor: Color,
    thickness: Dp = 3.dp,
    minThumbHeight: Dp = 24.dp,
    endPadding: Dp = 2.dp
): Modifier = composed {
    this
        .verticalScroll(scrollState)
        .drawWithContent {
            drawContent()
            if (!scrollState.isScrollInProgress || scrollState.maxValue <= 0) return@drawWithContent

            val totalContentHeight = size.height + scrollState.maxValue
            if (totalContentHeight <= 0f) return@drawWithContent

            val thicknessPx = thickness.toPx()
            val minThumbHeightPx = minThumbHeight.toPx()
            val endPaddingPx = endPadding.toPx()
            val thumbHeight = max((size.height / totalContentHeight) * size.height, minThumbHeightPx)
                .coerceAtMost(size.height)
            val availableTravel = (size.height - thumbHeight).coerceAtLeast(0f)
            val progress = (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
            val top = availableTravel * progress

            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset(size.width - thicknessPx - endPaddingPx, top),
                size = Size(thicknessPx, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thicknessPx, thicknessPx)
            )
        }
}

@Composable
fun Modifier.verticalScrollWithIndicator(
    indicatorColor: Color = rememberScrollbarColor(),
    thickness: Dp = 3.dp,
    minThumbHeight: Dp = 24.dp,
    endPadding: Dp = 2.dp
): Modifier {
    val scrollState = rememberScrollState()
    return verticalScrollWithIndicator(
        scrollState = scrollState,
        indicatorColor = indicatorColor,
        thickness = thickness,
        minThumbHeight = minThumbHeight,
        endPadding = endPadding
    )
}

fun Modifier.lazyColumnScrollIndicator(
    listState: LazyListState,
    indicatorColor: Color,
    thickness: Dp = 3.dp,
    minThumbHeight: Dp = 24.dp,
    endPadding: Dp = 2.dp
): Modifier = composed {
    this.drawWithContent {
        drawContent()
        if (!listState.isScrollInProgress) return@drawWithContent

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty() || layoutInfo.totalItemsCount <= 0) return@drawWithContent

        val viewportHeight = size.height
        if (viewportHeight <= 0f) return@drawWithContent

        val avgItemSize = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
        val estimatedContentHeight = avgItemSize * layoutInfo.totalItemsCount.toFloat()
        if (estimatedContentHeight <= viewportHeight) return@drawWithContent

        val scrolledPx = (listState.firstVisibleItemIndex * avgItemSize) +
            listState.firstVisibleItemScrollOffset
        val maxScroll = (estimatedContentHeight - viewportHeight).coerceAtLeast(1f)
        val progress = (scrolledPx / maxScroll).coerceIn(0f, 1f)

        val thicknessPx = thickness.toPx()
        val minThumbHeightPx = minThumbHeight.toPx()
        val endPaddingPx = endPadding.toPx()
        val thumbHeight = max((viewportHeight / estimatedContentHeight) * viewportHeight, minThumbHeightPx)
            .coerceAtMost(viewportHeight)
        val availableTravel = (viewportHeight - thumbHeight).coerceAtLeast(0f)
        val top = availableTravel * progress

        drawRoundRect(
            color = indicatorColor,
            topLeft = Offset(size.width - thicknessPx - endPaddingPx, top),
            size = Size(thicknessPx, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thicknessPx, thicknessPx)
        )
    }
}
