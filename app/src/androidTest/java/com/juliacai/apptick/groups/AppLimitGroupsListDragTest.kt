package com.juliacai.apptick.groups

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.appLimit.AppInGroup
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for AppLimitGroupsList drag-and-drop reordering.
 * Verifies the card tracks the finger during drag, auto-scroll works,
 * and items swap correctly with mixed expanded/collapsed card heights.
 */
@RunWith(AndroidJUnit4::class)
class AppLimitGroupsListDragTest {

    @get:Rule
    val rule = createComposeRule()

    // No device filter — tests must run on both physical devices and emulators

    private fun makeGroups(count: Int = 5): List<AppLimitGroup> = (1..count).map { i ->
        AppLimitGroup(
            id = i.toLong(),
            name = "Group $i",
            timeHrLimit = 1,
            timeMinLimit = 30,
            weekDays = listOf(1, 2, 3, 4, 5, 6, 7),
            apps = listOf(
                AppInGroup("App A$i", "com.example.a$i", null),
                AppInGroup("App B$i", "com.example.b$i", null)
            ),
            // Alternate expanded/collapsed to get mixed heights
            isExpanded = (i % 2 == 0)
        )
    }

    /**
     * After a long-press + short drag, the dragged card's text should
     * still be visible (card didn't disappear / get disposed).
     */
    @Test
    fun draggedCard_remainsVisible() {
        rule.setContent {
            AppTheme {
                AppLimitGroupsList(
                    groups = makeGroups(),
                    onCardClick = {},
                    onEditClick = {},
                    onLockClick = {},
                    onExpandToggle = {},
                    isEditingLocked = false
                )
            }
        }

        rule.onNodeWithText("Group 1").performTouchInput {
            down(center)
            advanceEventTime(600) // hold for long press
            moveBy(Offset(0f, 80f))
            advanceEventTime(100)
            moveBy(Offset(0f, 80f))
            advanceEventTime(100)
        }

        // Card must still be on-screen
        rule.onNodeWithText("Group 1").assertIsDisplayed()

        rule.onNodeWithText("Group 1").performTouchInput { up() }
    }

    /**
     * Drag a card down and then back up to the starting position.
     * Its bounds should be within a small tolerance of where it started,
     * proving there is no accumulated drift.
     */
    @Test
    fun dragDownThenBack_noDrift() {
        rule.setContent {
            AppTheme {
                AppLimitGroupsList(
                    groups = makeGroups(),
                    onCardClick = {},
                    onEditClick = {},
                    onLockClick = {},
                    onExpandToggle = {},
                    isEditingLocked = false
                )
            }
        }

        val beforeBounds = rule.onNodeWithText("Group 1").getBoundsInRoot()

        // Small drag (50px) — not enough to trigger a swap, so we
        // can measure pure offset drift without layout position changes.
        rule.onNodeWithText("Group 1").performTouchInput {
            down(center)
            advanceEventTime(600)
            // drag down 50px in small steps
            repeat(5) {
                moveBy(Offset(0f, 10f))
                advanceEventTime(16)
            }
            // drag back up 50px
            repeat(5) {
                moveBy(Offset(0f, -10f))
                advanceEventTime(16)
            }
            up()
        }

        rule.waitForIdle()
        val afterBounds = rule.onNodeWithText("Group 1").getBoundsInRoot()

        // Should be very close to original — any drift means offset math is wrong
        val drift = Math.abs(afterBounds.top.value - beforeBounds.top.value)
        assertThat(drift).isLessThan(10f)
    }

    /**
     * Drag a card down far enough to swap with the next card.
     * The order callback should fire with the swapped order.
     */
    @Test
    fun dragPastNextCard_triggersReorder() {
        val reorderedIds = mutableListOf<List<Long>>()

        rule.setContent {
            AppTheme {
                AppLimitGroupsList(
                    groups = makeGroups(3),
                    onCardClick = {},
                    onEditClick = {},
                    onLockClick = {},
                    onExpandToggle = {},
                    isEditingLocked = false,
                    onReorder = { reorderedIds.add(it) }
                )
            }
        }

        rule.onNodeWithText("Group 1").performTouchInput {
            down(center)
            advanceEventTime(600)
            // Drag down aggressively to trigger swap
            repeat(30) {
                moveBy(Offset(0f, 30f))
                advanceEventTime(16)
            }
            up()
        }

        rule.waitForIdle()
        // At least one reorder should have happened
        assertThat(reorderedIds).isNotEmpty()
        // Group 1 should no longer be first
        val lastOrder = reorderedIds.last()
        assertThat(lastOrder.first()).isNotEqualTo(1L)
    }

    /**
     * With a mix of expanded (tall) and collapsed (short) cards,
     * dragging an expanded card upward should still trigger auto-scroll
     * and the card should remain visible throughout.
     */
    @Test
    fun expandedCard_dragUp_remainsVisible() {
        // Put expanded card at position 3 (somewhere in the middle)
        val groups = makeGroups(6)
        rule.setContent {
            AppTheme {
                AppLimitGroupsList(
                    groups = groups,
                    onCardClick = {},
                    onEditClick = {},
                    onLockClick = {},
                    onExpandToggle = {},
                    isEditingLocked = false
                )
            }
        }

        // Group 2 is expanded (even index)
        rule.onNodeWithText("Group 2").performTouchInput {
            down(center)
            advanceEventTime(600)
            // Drag upward in steps
            repeat(20) {
                moveBy(Offset(0f, -30f))
                advanceEventTime(16)
            }
        }

        rule.onNodeWithText("Group 2").assertIsDisplayed()
        rule.onNodeWithText("Group 2").performTouchInput { up() }
    }
}
