package com.clayboicardi.game2048.ios

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.engine.Direction
import kotlin.math.abs

/** Swipe commit threshold in dp and axis-dominance ratio (D-050: 24 dp, 1.4). */
private const val SWIPE_THRESHOLD_DP = 24
private const val SWIPE_DOMINANCE = 1.4f

/**
 * Turns one drag on this element into at most one [onMove] (D-050, D-052), from raw pointer deltas
 * so a physical left swipe is always LEFT (the board is never mirrored, D-104).
 *
 * Like the Android detector it reads events in [PointerEventPass.Initial] and, once the drag passes
 * the touch slop, consumes the whole stream: an enclosing scroll container (the screen scrolls at
 * large font sizes) therefore never steals a board swipe. It commits during the drag as soon as the
 * movement passes the threshold on a dominant axis. While [enabled] is false (an overlay is up) it
 * leaves input alone.
 *
 * Keyed on [enabled] only: [onMove] is expected to close over stable objects.
 */
fun Modifier.swipeToMove(enabled: Boolean, onMove: (Direction) -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val threshold = SWIPE_THRESHOLD_DP.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pastSlop = false
            var committed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - down.position
                if (!pastSlop && delta.getDistance() > slop) pastSlop = true
                if (pastSlop) event.changes.forEach { it.consume() }
                if (pastSlop && !committed) {
                    classify(delta, threshold)?.let { direction ->
                        committed = true
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
        ax >= threshold && ax >= ay * SWIPE_DOMINANCE ->
            if (delta.x > 0) Direction.RIGHT else Direction.LEFT
        ay >= threshold && ay >= ax * SWIPE_DOMINANCE ->
            if (delta.y > 0) Direction.DOWN else Direction.UP
        else -> null
    }
}
