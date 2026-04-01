package com.juliacai.apptick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Wraps [content] with a [ViewConfiguration] whose [longPressTimeoutMillis]
 * is set to [timeoutMs] (default 500 ms, up from the system default ~400 ms).
 *
 * Use this around draggable items so that `detectDragGesturesAfterLongPress`
 * requires a longer hold before activating — preventing accidental reorders.
 */
@Composable
fun LongHoldDragArea(
    timeoutMs: Long = 500L,
    content: @Composable () -> Unit
) {
    val original = LocalViewConfiguration.current
    val custom = remember(original, timeoutMs) {
        object : ViewConfiguration {
            override val longPressTimeoutMillis: Long get() = timeoutMs
            override val doubleTapTimeoutMillis: Long get() = original.doubleTapTimeoutMillis
            override val doubleTapMinTimeMillis: Long get() = original.doubleTapMinTimeMillis
            override val touchSlop: Float get() = original.touchSlop
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides custom) {
        content()
    }
}
