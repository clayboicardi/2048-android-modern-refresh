package com.clayboicardi.game2048.ios

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.engine.Direction
import kotlin.math.abs

/** Swipe commit threshold in dp and axis-dominance ratio (D-050: 24 dp, 1.4). */
private const val SWIPE_THRESHOLD_DP = 24
private const val SWIPE_DOMINANCE = 1.4f

/**
 * Turns one drag into at most one [onMove], from raw pointer deltas so a physical left swipe is
 * always LEFT (the board is never mirrored, D-104). Commits during the drag the moment it passes
 * the threshold on a dominant axis. While [enabled] is false (an overlay is up) it ignores input.
 */
fun Modifier.swipeToMove(enabled: Boolean, onMove: (Direction) -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val threshold = SWIPE_THRESHOLD_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var committed = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!committed) {
                    classify(change.position - down.position, threshold)?.let { direction ->
                        committed = true
                        change.consume()
                        onMove(direction)
                    }
                }
                if (!change.pressed) break
            }
        }
    }

/** The dominant swipe direction once past [threshold], or null while the drag is still small. */
private fun classify(delta: Offset, threshold: Float): Direction? {
    val ax = abs(delta.x)
    val ay = abs(delta.y)
    return when {
        ax >= threshold && ax >= ay * SWIPE_DOMINANCE -> if (delta.x > 0) Direction.RIGHT else Direction.LEFT
        ay >= threshold && ay >= ax * SWIPE_DOMINANCE -> if (delta.y > 0) Direction.DOWN else Direction.UP
        else -> null
    }
}
