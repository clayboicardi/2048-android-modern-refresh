package com.clayboicardi.game2048.game.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.data.settings.MoveTiming
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * Turns one drag on this element into at most one [onMove] (D-050, D-051, D-052; ARCHITECTURE.md
 * §6.1): measured from pointer-down in `PointerEventPass.Initial`, so it sees every event before
 * the buttons inside; past the touch slop it consumes the stream, so a button under the drag never
 * fires; a second pointer before the commit cancels the gesture; raw pointer deltas, so a physical
 * left swipe is LEFT in every locale (D-104). While [enabled] is false (a card, dialog or sheet is
 * open, D-054, D-199) it leaves every event alone. Applied to the whole Game content area (the
 * board alone in M4's SCROLL arrangement).
 *
 * A press that never leaves the touch slop stays a tap for the elements below, except a press held
 * for the system long-press timeout or longer: its release is consumed, so a long press neither
 * moves nor fires a button (there is no long-press gesture, D-093, F19-AC6). A release the system
 * cancelled (it arrives already consumed) commits nothing.
 */
fun Modifier.swipeToMove(
    config: State<SwipeConfig>,
    enabled: State<Boolean>,
    onMove: (Direction) -> Unit,
): Modifier =
    pointerInput(config, enabled, onMove) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (enabled.value) {
                trackSwipe(down, config.value) { if (enabled.value) onMove(it) }
            }
        }
    }

/**
 * How swipes commit (D-050; ARCHITECTURE.md §6.1), from the Move timing and Swipe sensitivity
 * settings.
 */
@Immutable
data class SwipeConfig(
    /** DURING_SWIPE (default) or ON_RELEASE (D-050). */
    val timing: MoveTiming,
    /** 40 / 24 / 12: `SwipeSensitivity.thresholdDp` (D-050). */
    val thresholdDp: Int,
    val dominanceRatio: Float = GameDimens.SwipeDominanceRatio,
    val flingDpPerSecond: Float = GameDimens.SwipeFlingDpPerSecond,
)

/** Follows one gesture from [down] until its first pointer is up (ARCHITECTURE.md §6.1). */
private suspend fun AwaitPointerEventScope.trackSwipe(
    down: PointerInputChange,
    config: SwipeConfig,
    onMove: (Direction) -> Unit,
) {
    val limits =
        SwipeLimits(
            slop = viewConfiguration.touchSlop,
            threshold = config.thresholdDp.dp.toPx(),
            minVelocity = config.flingDpPerSecond.dp.toPx(),
            longPressMs = viewConfiguration.longPressTimeoutMillis,
        )
    val gesture = SwipeGesture(down, config, limits, onMove)
    var active = true
    while (active) active = gesture.onEvent(awaitPointerEvent(PointerEventPass.Initial))
}

/** One gesture's limits: px, px per second and ms. */
private class SwipeLimits(
    val slop: Float,
    val threshold: Float,
    val minVelocity: Float,
    val longPressMs: Long,
)

/** The state of one gesture, which makes at most one [onMove] (D-050). */
private class SwipeGesture(
    private val down: PointerInputChange,
    private val config: SwipeConfig,
    private val limits: SwipeLimits,
    private val onMove: (Direction) -> Unit,
) {
    private val tracker = VelocityTracker().apply { addPointerInputChange(down) }
    private var pastSlop = false
    private var committed = false

    /** A second pointer went down before the commit: the rest is consumed and ignored. */
    private var abandoned = false

    /** Handles one Initial-pass event; false once the gesture is over. */
    fun onEvent(event: PointerEvent): Boolean {
        if (!committed && event.changes.count { it.pressed } > 1) abandoned = true
        val change = event.changes.firstOrNull { it.id == down.id }
        return when {
            abandoned -> consumeAll(event)
            change == null -> false
            else -> track(event, change)
        }
    }

    /** Consumes [event]; true while any pointer is still down. */
    private fun consumeAll(event: PointerEvent): Boolean {
        event.changes.forEach { it.consume() }
        return event.changes.any { it.pressed }
    }

    /** Follows the first pointer's [change]; true while it is still down. */
    private fun track(event: PointerEvent, change: PointerInputChange): Boolean {
        // A system cancellation delivers the release already consumed; this is the first reader.
        val systemCancel = !change.pressed && change.isConsumed
        tracker.addPointerInputChange(change)
        val delta = change.position - down.position
        if (!pastSlop && delta.getDistance() > limits.slop) pastSlop = true
        if (pastSlop) event.changes.forEach { it.consume() }
        if (!committed && pastSlop && config.timing == MoveTiming.DURING_SWIPE) {
            SwipeClassifier.classify(delta, limits.threshold, config.dominanceRatio)?.let(::commit)
        }
        if (!change.pressed && !systemCancel) release(change, delta)
        return change.pressed
    }

    /** Pointer-up without a commit: ON_RELEASE, then the fling fallback; or a long press. */
    private fun release(up: PointerInputChange, delta: Offset) {
        when {
            committed -> Unit
            pastSlop -> releaseDirection(delta)?.let(::commit)
            up.uptimeMillis - down.uptimeMillis >= limits.longPressMs -> up.consume()
        }
    }

    private fun releaseDirection(delta: Offset): Direction? {
        val onRelease =
            if (config.timing == MoveTiming.ON_RELEASE) {
                SwipeClassifier.classify(delta, limits.threshold, config.dominanceRatio)
            } else {
                null
            }
        return onRelease
            ?: SwipeClassifier.classifyFling(
                delta = delta,
                velocity = tracker.calculateVelocity(),
                slop = limits.slop,
                minVelocity = limits.minVelocity,
                dominanceRatio = config.dominanceRatio,
            )
    }

    private fun commit(direction: Direction) {
        committed = true
        onMove(direction)
    }
}
