package com.juliacai.apptick.groups

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juliacai.apptick.LongHoldDragArea
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.data.GroupCardOrderStore
import com.juliacai.apptick.lazyColumnScrollIndicator
import com.juliacai.apptick.rememberScrollbarColor

@Composable
fun AppLimitGroupsList(
    groups: List<AppLimitGroup>,
    onCardClick: (AppLimitGroup) -> Unit,
    onEditClick: (AppLimitGroup) -> Unit,
    onLockClick: (AppLimitGroup) -> Unit,
    onExpandToggle: (AppLimitGroup) -> Unit,
    isEditingLocked: Boolean,
    onPauseToggle: (AppLimitGroup) -> Unit = {},
    onDelete: (AppLimitGroup) -> Unit = {},
    onReorder: (List<Long>) -> Unit = {},
    autoScrollTargetSize: Int? = null,
    onAutoScrollHandled: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val orderedIds = remember { mutableStateListOf<Long>() }
    var draggingItemId by remember { mutableLongStateOf(-1L) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var autoScrollSpeedPxPerFrame by remember { mutableFloatStateOf(0f) }
    var scrollToAfterDrop by remember { mutableLongStateOf(-1L) }
    var dragAnchorY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 96.dp.toPx() }
    val maxAutoScrollPxPerFrame = with(density) { 22.dp.toPx() }
    val scrollbarColor = rememberScrollbarColor()
    val haptic = LocalHapticFeedback.current

    // Synchronise orderedIds with the groups list.
    // During an active drag we must NOT clear()+addAll() — that would
    // momentarily empty the LazyColumn, disposing the dragged item and
    // killing the gesture.  Instead, surgically add new IDs and remove
    // stale ones, preserving the current (possibly mid-drag) order.
    LaunchedEffect(groups, draggingItemId) {
        val availableIds = groups.map { it.id }
        if (draggingItemId != -1L) {
            // Drag is active — only add/remove, never reorder
            val availableSet = availableIds.toSet()
            orderedIds.removeAll { it !in availableSet }
            for (id in availableIds) {
                if (id !in orderedIds) orderedIds.add(id)
            }
        } else {
            // No drag — safe to apply the full stored order
            val sanitized = GroupCardOrderStore.sanitizeOrder(orderedIds.toList(), availableIds)
            if (sanitized != orderedIds.toList()) {
                orderedIds.clear()
                orderedIds.addAll(sanitized)
            }
        }
    }

    LaunchedEffect(scrollToAfterDrop) {
        if (scrollToAfterDrop == -1L) return@LaunchedEffect
        val idx = orderedIds.indexOf(scrollToAfterDrop)
        if (idx >= 0) listState.animateScrollToItem(idx)
        scrollToAfterDrop = -1L
    }

    LaunchedEffect(draggingItemId, autoScrollSpeedPxPerFrame) {
        if (draggingItemId == -1L || autoScrollSpeedPxPerFrame == 0f) return@LaunchedEffect
        while (draggingItemId != -1L && autoScrollSpeedPxPerFrame != 0f) {
            val consumed = listState.scrollBy(autoScrollSpeedPxPerFrame)
            if (consumed == 0f) break
            // Keep dragged item under the finger while auto-scrolling.
            // The next onDrag will overwrite with the direct formula, so
            // this adjustment only bridges the gap between pointer events.
            draggingOffsetY += consumed
            withFrameNanos { }

            // Swap during auto-scroll so the dragged item's layout index
            // stays near the finger.  Without this, the item stays at its
            // old index, scrolls off the viewport, and gets disposed.
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val draggedInfo = visibleItems.firstOrNull { it.key == draggingItemId } ?: continue
            // Reconstruct finger viewport position from current state:
            // fingerY = draggedInfo.offset + change.position.y
            //         = draggedInfo.offset + (draggingOffsetY + dragAnchorY)
            val fingerY = draggedInfo.offset + draggingOffsetY + dragAnchorY
            val targetInfo = visibleItems.firstOrNull { item ->
                item.key != draggingItemId &&
                    fingerY >= item.offset &&
                    fingerY <= item.offset + item.size
            } ?: continue

            val fromIndex = orderedIds.indexOf(draggingItemId)
            val targetId = targetInfo.key as? Long ?: continue
            val toIndex = orderedIds.indexOf(targetId)
            if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) continue

            orderedIds.removeAt(fromIndex)
            orderedIds.add(toIndex, draggingItemId)
            draggingOffsetY += (draggedInfo.offset - targetInfo.offset).toFloat()
            onReorder(orderedIds.toList())
        }
    }

    LaunchedEffect(autoScrollTargetSize, orderedIds.size) {
        val targetSize = autoScrollTargetSize ?: return@LaunchedEffect
        if (orderedIds.isEmpty() || orderedIds.size < targetSize) return@LaunchedEffect
        listState.scrollToItem(orderedIds.lastIndex)
        onAutoScrollHandled()
    }

    val groupById = remember(groups) { groups.associateBy { it.id } }
    val orderedGroups = orderedIds.mapNotNull { id -> groupById[id] }

    LongHoldDragArea(timeoutMs = 500L) {
        LazyColumn(
            state = listState,
            // Disable touch-scroll during drag so the LazyColumn's internal
            // scrollable can't consume pointer events and cancel the gesture.
            // Programmatic scrollBy() (auto-scroll loop) still works.
            userScrollEnabled = draggingItemId == -1L,
            modifier = Modifier
                .padding(8.dp)
                .lazyColumnScrollIndicator(listState, scrollbarColor),
            contentPadding = PaddingValues(bottom = 104.dp)
        ) {
            items(
                count = orderedGroups.size,
                key = { index -> orderedGroups[index].id }
            ) { index ->
                val group = orderedGroups[index]
                val isDragging = draggingItemId == group.id
                val wiggleRotation = remember { Animatable(0f) }

                LaunchedEffect(isDragging) {
                    if (isDragging) {
                        wiggleRotation.snapTo(-2f)
                        wiggleRotation.animateTo(
                            targetValue = 2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(100, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                    } else {
                        wiggleRotation.snapTo(0f)
                    }
                }

                val dragModifier = if (!isEditingLocked) {
                    // Key on group.id only — removing orderedIds.size prevents
                    // the pointerInput coroutine from restarting on swap.
                    Modifier.pointerInput(group.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingItemId = group.id
                                draggingOffsetY = 0f
                                dragAnchorY = offset.y
                                autoScrollSpeedPxPerFrame = 0f
                            },
                            onDrag = { change, _ ->
                                if (draggingItemId != group.id) return@detectDragGesturesAfterLongPress
                                change.consume()
                                // Direct formula: immune to drift because it
                                // overwrites every frame instead of accumulating.
                                draggingOffsetY = change.position.y - dragAnchorY

                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                val draggedInfo = visibleItems.firstOrNull { it.key == group.id } ?: return@detectDragGesturesAfterLongPress
                                // Finger position in viewport coordinates —
                                // works for any card height (expanded or collapsed)
                                val fingerY = draggedInfo.offset + change.position.y
                                val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
                                val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
                                val topEdge = viewportStart + edgeThresholdPx
                                val bottomEdge = viewportEnd - edgeThresholdPx
                                autoScrollSpeedPxPerFrame = when {
                                    fingerY < topEdge -> {
                                        val intensity = ((topEdge - fingerY) / edgeThresholdPx).coerceIn(0f, 1f)
                                        -maxAutoScrollPxPerFrame * intensity
                                    }
                                    fingerY > bottomEdge -> {
                                        val intensity = ((fingerY - bottomEdge) / edgeThresholdPx).coerceIn(0f, 1f)
                                        maxAutoScrollPxPerFrame * intensity
                                    }
                                    else -> 0f
                                }
                                // Swap when finger enters another card's bounds
                                val targetInfo = visibleItems.firstOrNull { item ->
                                    item.key != group.id &&
                                        fingerY >= item.offset &&
                                        fingerY <= item.offset + item.size
                                } ?: return@detectDragGesturesAfterLongPress

                                val fromIndex = orderedIds.indexOf(group.id)
                                val targetId = targetInfo.key as? Long ?: return@detectDragGesturesAfterLongPress
                                val toIndex = orderedIds.indexOf(targetId)
                                if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) {
                                    return@detectDragGesturesAfterLongPress
                                }

                                orderedIds.removeAt(fromIndex)
                                orderedIds.add(toIndex, group.id)
                                draggingOffsetY += (draggedInfo.offset - targetInfo.offset).toFloat()
                                onReorder(orderedIds.toList())
                            },
                            onDragEnd = {
                                scrollToAfterDrop = draggingItemId
                                draggingItemId = -1L
                                draggingOffsetY = 0f
                                autoScrollSpeedPxPerFrame = 0f
                            },
                            onDragCancel = {
                                scrollToAfterDrop = draggingItemId
                                draggingItemId = -1L
                                draggingOffsetY = 0f
                                autoScrollSpeedPxPerFrame = 0f
                            }
                        )
                    }
                } else {
                    Modifier
                }

                AppLimitGroupItem(
                    group = group,
                    isExpanded = group.isExpanded,
                    isEditingLocked = isEditingLocked,
                    onExpandToggle = onExpandToggle,
                    onLockClick = onLockClick,
                    onPauseToggle = onPauseToggle,
                    onEdit = onEditClick,
                    onDelete = onDelete,
                    onCardClick = onCardClick,
                    isDragging = isDragging,
                    modifier = dragModifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) draggingOffsetY else 0f
                            rotationZ = wiggleRotation.value
                            scaleX = if (isDragging) 1.04f else 1f
                            scaleY = if (isDragging) 1.04f else 1f
                        }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLimitGroupsListPreview() {
    AppTheme {
        AppLimitGroupsList(
            groups = listOf(
                AppLimitGroup(
                    id = 1L,
                    name = "Social",
                    timeHrLimit = 1,
                    timeMinLimit = 30,
                    weekDays = listOf(1, 2, 3, 4, 5),
                    apps = listOf(
                        AppInGroup("Instagram", "com.instagram.android", null),
                        AppInGroup("TikTok", "com.zhiliaoapp.musically", null)
                    )
                ),
                AppLimitGroup(
                    id = 2L,
                    name = "Streaming",
                    timeHrLimit = 2,
                    timeMinLimit = 0,
                    weekDays = listOf(6, 7),
                    apps = listOf(
                        AppInGroup("YouTube", "com.google.android.youtube", null)
                    )
                )
            ),
            onCardClick = {},
            onEditClick = {},
            onLockClick = {},
            onExpandToggle = {},
            isEditingLocked = true
        )
    }
}
