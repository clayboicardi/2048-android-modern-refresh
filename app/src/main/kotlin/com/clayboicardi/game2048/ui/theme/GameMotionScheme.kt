package com.clayboicardi.game2048.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MotionScheme

/**
 * The Material 3 `MotionScheme` of [level] (D-184; MOTION_SPEC.md §2.2), which `GameTheme` passes
 * to `MaterialExpressiveTheme`: `MotionScheme.expressive()` under Full; under Reduced the spatial
 * specs snap and the effects specs stay expressive; under Off every spec snaps. Game motion never
 * reads it (D-026); it drives the Material components, such as the sheets.
 */
fun gameMotionScheme(level: MotionLevel): MotionScheme =
    when (level) {
        MotionLevel.FULL -> MotionScheme.expressive()
        MotionLevel.REDUCED -> ReducedMotionScheme
        MotionLevel.OFF -> OffMotionScheme
    }

/** Reduced: spatial motion snaps, effects (fades, colours) keep the expressive springs. */
private object ReducedMotionScheme : MotionScheme {
    private val expressive = MotionScheme.expressive()

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = expressive.defaultEffectsSpec()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = expressive.fastEffectsSpec()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = expressive.slowEffectsSpec()
}

/** Off: everything snaps. */
private object OffMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
