package com.clayboicardi.game2048.game.motion

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec

/** Nanoseconds per millisecond. */
private const val NANOS_PER_MS = 1_000_000L

/**
 * [spec] started [delayMs] milliseconds late (D-183; MOTION_SPEC.md §2.3 T1). During the delay the
 * value and velocity stay at the animation's initial value and velocity; afterwards [spec] plays at
 * play time − delay, so at play time `delay + t` the value and velocity equal [spec]'s at `t`. Its
 * duration is the delay plus [spec]'s duration. Like `tween(delayMillis)` and `snap(delayMillis)`,
 * the delay scales with `MotionDurationScale` and is 0 at scale 0, because Compose scales the play
 * time of the whole spec.
 *
 * This replaces animation-core's `delayed()`, which is Kotlin-internal although its bytecode looks
 * public (D-183). A [delayMs] of 0 or less returns [spec] itself.
 */
fun <T> delayedSpec(spec: FiniteAnimationSpec<T>, delayMs: Int): FiniteAnimationSpec<T> =
    if (delayMs <= 0) spec else DelayedSpec(spec, delayMs * NANOS_PER_MS)

private class DelayedSpec<T>(
    private val spec: FiniteAnimationSpec<T>,
    private val delayNanos: Long,
) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<T, V>,
    ): VectorizedFiniteAnimationSpec<V> =
        DelayedVectorizedSpec(spec.vectorize(converter), delayNanos)

    override fun equals(other: Any?): Boolean =
        other is DelayedSpec<*> && other.spec == spec && other.delayNanos == delayNanos

    override fun hashCode(): Int = 31 * spec.hashCode() + delayNanos.hashCode()

    override fun toString(): String = "delayedSpec($spec, ${delayNanos / NANOS_PER_MS} ms)"
}

private class DelayedVectorizedSpec<V : AnimationVector>(
    private val spec: VectorizedFiniteAnimationSpec<V>,
    private val delayNanos: Long,
) : VectorizedFiniteAnimationSpec<V> {
    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V =
        if (playTimeNanos < delayNanos) {
            initialValue
        } else {
            spec.getValueFromNanos(
                playTimeNanos - delayNanos,
                initialValue,
                targetValue,
                initialVelocity,
            )
        }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V =
        if (playTimeNanos < delayNanos) {
            initialVelocity
        } else {
            spec.getVelocityFromNanos(
                playTimeNanos - delayNanos,
                initialValue,
                targetValue,
                initialVelocity,
            )
        }

    override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
        delayNanos + spec.getDurationNanos(initialValue, targetValue, initialVelocity)
}
