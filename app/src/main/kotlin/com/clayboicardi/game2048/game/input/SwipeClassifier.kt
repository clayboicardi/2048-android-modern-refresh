package com.clayboicardi.game2048.game.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.ui.theme.GameDimens
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * The pure swipe decisions of `Modifier.swipeToMove` (D-050; ARCHITECTURE.md §6.1). Deltas are
 * measured from pointer-down; every length (and velocity) is in one unit, which the detector keeps
 * in px. Directions are physical: x grows to the right and y downwards in every locale (D-104).
 */
object SwipeClassifier {
    /**
     * The direction of [delta] when its major axis reaches [threshold] and dominates the minor one
     * by at least [dominanceRatio] (`|major| ≥ T` and `|major| ≥ 1.4 · |minor|`); `null` for a
     * short drag or an ambiguous diagonal. A ratio of exactly [dominanceRatio] is accepted.
     */
    fun classify(
        delta: Offset,
        threshold: Float,
        dominanceRatio: Float = GameDimens.SwipeDominanceRatio,
    ): Direction? {
        val major = max(abs(delta.x), abs(delta.y))
        return if (major >= threshold) dominantDirection(delta, dominanceRatio) else null
    }

    /**
     * The fling fallback at pointer-up (D-050): the direction of [delta] when it is longer than
     * [slop], its dominant axis passes the [dominanceRatio] test, and the release [velocity] along
     * that axis has the sign of the displacement and a magnitude of at least [minVelocity];
     * otherwise `null`.
     */
    fun classifyFling(
        delta: Offset,
        velocity: Velocity,
        slop: Float,
        minVelocity: Float,
        dominanceRatio: Float = GameDimens.SwipeDominanceRatio,
    ): Direction? {
        val direction =
            if (delta.getDistance() > slop) dominantDirection(delta, dominanceRatio) else null
        return direction?.takeIf { fast(it, delta, velocity, minVelocity) }
    }

    /** The direction of the dominant axis of [delta], or `null` when no axis dominates. */
    private fun dominantDirection(delta: Offset, dominanceRatio: Float): Direction? {
        val ax = abs(delta.x)
        val ay = abs(delta.y)
        return when {
            max(ax, ay) < dominanceRatio * min(ax, ay) -> null
            ax >= ay -> if (delta.x > 0f) Direction.RIGHT else Direction.LEFT
            else -> if (delta.y > 0f) Direction.DOWN else Direction.UP
        }
    }

    /** True when [velocity] along [direction]'s axis has the sign of [delta] and reaches [min]. */
    private fun fast(direction: Direction, delta: Offset, velocity: Velocity, min: Float): Boolean {
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        val d = if (horizontal) delta.x else delta.y
        val v = if (horizontal) velocity.x else velocity.y
        return sign(v) == sign(d) && abs(v) >= min
    }
}
