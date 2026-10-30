package com.clayboicardi.game2048.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

/** A `NavDisplay` push or pop spec (navigation3 1.2.0 `transitionSpec`, `popTransitionSpec`). */
typealias NavTransitionSpec = AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform

/**
 * A `NavDisplay` predictive-pop spec (navigation3 1.2.0 `predictivePopTransitionSpec`); the `Int`
 * is the swipe edge, `NavigationEvent.EDGE_LEFT`, `EDGE_RIGHT` or `EDGE_NONE`.
 */
typealias NavPredictiveSpec =
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform

/**
 * The three app-wide screen-transition specs `NavDisplay` receives (MOTION_SPEC.md §3.17, §3.19;
 * ARCHITECTURE.md §7.1), with the parameter types of navigation3 1.2.0's `NavDisplay`.
 */
@Immutable
class GameNavTransitions(
    /** Game → sub-screen and About → Licenses. */
    val push: NavTransitionSpec,
    /** Back and Up: the mirror of [push]. */
    val pop: NavTransitionSpec,
    /** The predictive-back pop, seeked by the gesture. */
    val predictivePop: NavPredictiveSpec,
)

/**
 * The screen transitions of [level] (MOTION_SPEC.md §3.17, §3.19):
 * - Full: a shared-axis shift of `GameMotion.Nav.SHIFT_DP` with `standardSpatial` plus a fade in
 *   (`defaultEffects`) and out (`fastEffects`), in the reading direction for a push and mirrored
 *   for a pop; the predictive pop scales the exiting screen to 0.90, shifts it away from the swipe
 *   edge and fades it out with linear 300 ms tweens while the entering screen fades in.
 * - Reduced: the same fades without the shift; the predictive pop is a linear 300 ms crossfade.
 * - Off: `EnterTransition.None togetherWith ExitTransition.None` for all three.
 *
 * The shift in px and the layout direction are read here, because the spec lambdas are not
 * composable; chrome mirrors in RTL (D-104).
 */
@Composable
fun rememberGameNavTransitions(level: MotionLevel): GameNavTransitions {
    val shiftPx = with(LocalDensity.current) { GameMotion.Nav.SHIFT_DP.dp.roundToPx() }
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    return remember(level, shiftPx, dir) { gameNavTransitions(level, shiftPx, dir) }
}

private fun gameNavTransitions(level: MotionLevel, shiftPx: Int, dir: Int): GameNavTransitions =
    when (level) {
        MotionLevel.FULL ->
            GameNavTransitions(
                push = { shiftTransform(enterFromPx = dir * shiftPx, exitToPx = -dir * shiftPx) },
                pop = { shiftTransform(enterFromPx = -dir * shiftPx, exitToPx = dir * shiftPx) },
                predictivePop = { edge -> predictiveTransform(edgeSign(edge) * shiftPx) },
            )
        MotionLevel.REDUCED ->
            GameNavTransitions(
                push = { fadeTransform() },
                pop = { fadeTransform() },
                predictivePop = { predictiveFadeTransform() },
            )
        MotionLevel.OFF ->
            GameNavTransitions(
                push = { noTransform() },
                pop = { noTransform() },
                predictivePop = { noTransform() },
            )
    }

/** Full push or pop: shift and fade (MOTION_SPEC.md §3.17). */
private fun shiftTransform(enterFromPx: Int, exitToPx: Int): ContentTransform {
    val shift = GameMotion.standardSpatial(IntOffset(INT_THRESHOLD, INT_THRESHOLD))
    return (slideInHorizontally(shift) { enterFromPx } +
        fadeIn(GameMotion.defaultEffects(GameMotion.ALPHA_THRESHOLD))) togetherWith
        (slideOutHorizontally(shift) { exitToPx } +
            fadeOut(GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD)))
}

/** Reduced push or pop: fades only (MOTION_SPEC.md §3.17). */
private fun fadeTransform(): ContentTransform =
    fadeIn(GameMotion.defaultEffects(GameMotion.ALPHA_THRESHOLD)) togetherWith
        fadeOut(GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD))

/** Full predictive pop (MOTION_SPEC.md §3.19); [exitShiftPx] is `s · shiftPx`. */
private fun predictiveTransform(exitShiftPx: Int): ContentTransform =
    fadeIn(predictiveLinear()) togetherWith
        (scaleOut(predictiveLinear(), targetScale = GameMotion.Nav.PREDICTIVE_SCALE) +
            slideOutHorizontally(predictiveLinear()) { exitShiftPx } +
            fadeOut(
                tween(GameMotion.Nav.PREDICTIVE_MS, easing = GameMotion.EmphasizedAccelerate),
            ))

/** Reduced predictive pop: a linear crossfade (MOTION_SPEC.md §3.19). */
private fun predictiveFadeTransform(): ContentTransform =
    fadeIn(predictiveLinear()) togetherWith fadeOut(predictiveLinear())

/** Off: nothing animates (ARCHITECTURE.md §7.1). */
private fun noTransform(): ContentTransform = EnterTransition.None togetherWith ExitTransition.None

/** The linear 300 ms tween of the predictive pop, so progress is linear in gesture progress. */
private fun <T> predictiveLinear(): FiniteAnimationSpec<T> =
    tween(GameMotion.Nav.PREDICTIVE_MS, easing = LinearEasing)

/**
 * s of MOTION_SPEC.md §3.19: +1 for a swipe from the left edge, −1 from the right edge, 0 for none.
 */
private fun edgeSign(edge: Int): Int =
    when (edge) {
        NavigationEvent.EDGE_LEFT -> 1
        NavigationEvent.EDGE_RIGHT -> -1
        else -> 0
    }

/** `GameMotion.INT_OFFSET_THRESHOLD_PX` for the `IntOffset` shift spring (MOTION_SPEC.md §1.2). */
private const val INT_THRESHOLD = GameMotion.INT_OFFSET_THRESHOLD_PX
