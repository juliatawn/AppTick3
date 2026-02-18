package com.juliacai.apptick.groups

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juliacai.apptick.data.GroupCardOrderStore

@Composable
fun AppLimitGroupsList(
    groups: List<AppLimitGroup>,
    onCardClick: (AppLimitGroup) -> Unit,
    onEditClick: (AppLimitGroup) -> Unit,
    onLockClick: (AppLimitGroup) -> Unit,
    isEditingLocked: Boolean,
    onPauseToggle: (AppLimitGroup) -> Unit = {},
    onDelete: (AppLimitGroup) -> Unit = {},
    onReorder: (List<Long>) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val orderedIds = remember { mutableStateListOf<Long>() }
    var draggingItemId by remember { mutableLongStateOf(-1L) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(groups) {
        val availableIds = groups.map { it.id }
        val sanitized = GroupCardOrderStore.sanitizeOrder(orderedIds.toList(), availableIds)
        if (sanitized != orderedIds.toList()) {
            orderedIds.clear()
            orderedIds.addAll(sanitized)
        }
    }

    val groupById = remember(groups) { groups.associateBy { it.id } }
    val orderedGroups = orderedIds.mapNotNull { id -> groupById[id] }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(8.dp)
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
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingItemId != group.id) return@detectDragGesturesAfterLongPress
                            change.consume()
                            draggingOffsetY += dragAmount.y

                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val draggedInfo = visibleItems.firstOrNull { it.key == group.id } ?: return@detectDragGesturesAfterLongPress
                            val draggedCenterY = draggedInfo.offset + (draggedInfo.size / 2f) + draggingOffsetY
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
                        },
                        onDragCancel = {
                            draggingItemId = -1L
                            draggingOffsetY = 0f
                        }
                    )
                }
            } else {
                Modifier
            }

            AppLimitGroupItem(
                group = group,
                isEditingLocked = isEditingLocked,
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
