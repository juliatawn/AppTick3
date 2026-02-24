package com.juliacai.apptick.groups

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 96.dp.toPx() }
    val maxAutoScrollPxPerFrame = with(density) { 22.dp.toPx() }
    val scrollbarColor = rememberScrollbarColor()

    LaunchedEffect(groups) {
        val availableIds = groups.map { it.id }
        val sanitized = GroupCardOrderStore.sanitizeOrder(orderedIds.toList(), availableIds)
        if (sanitized != orderedIds.toList()) {
            orderedIds.clear()
            orderedIds.addAll(sanitized)
        }
    }

    LaunchedEffect(draggingItemId, autoScrollSpeedPxPerFrame) {
        if (draggingItemId == -1L || autoScrollSpeedPxPerFrame == 0f) return@LaunchedEffect
        while (draggingItemId != -1L && autoScrollSpeedPxPerFrame != 0f) {
            val consumed = listState.scrollBy(autoScrollSpeedPxPerFrame)
            if (consumed == 0f) break
            draggingOffsetY += consumed
            withFrameNanos { }
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

    LazyColumn(
        state = listState,
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

            val dragModifier = if (!isEditingLocked) {
                Modifier.pointerInput(group.id, orderedIds.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingItemId = group.id
                            draggingOffsetY = 0f
                            autoScrollSpeedPxPerFrame = 0f
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingItemId != group.id) return@detectDragGesturesAfterLongPress
                            change.consume()
                            draggingOffsetY += dragAmount.y

                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val draggedInfo = visibleItems.firstOrNull { it.key == group.id } ?: return@detectDragGesturesAfterLongPress
                            val draggedCenterY = draggedInfo.offset + (draggedInfo.size / 2f) + draggingOffsetY
                            val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
                            val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
                            val topEdge = viewportStart + edgeThresholdPx
                            val bottomEdge = viewportEnd - edgeThresholdPx
                            autoScrollSpeedPxPerFrame = when {
                                draggedCenterY < topEdge -> {
                                    val intensity = ((topEdge - draggedCenterY) / edgeThresholdPx).coerceIn(0f, 1f)
                                    -maxAutoScrollPxPerFrame * intensity
                                }
                                draggedCenterY > bottomEdge -> {
                                    val intensity = ((draggedCenterY - bottomEdge) / edgeThresholdPx).coerceIn(0f, 1f)
                                    maxAutoScrollPxPerFrame * intensity
                                }
                                else -> 0f
                            }
                            val targetInfo = visibleItems.firstOrNull { item ->
                                item.key != group.id &&
                                    draggedCenterY >= item.offset &&
                                    draggedCenterY <= item.offset + item.size
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
                            draggingItemId = -1L
                            draggingOffsetY = 0f
                            autoScrollSpeedPxPerFrame = 0f
                        },
                        onDragCancel = {
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
                modifier = dragModifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) draggingOffsetY else 0f }
            )
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
