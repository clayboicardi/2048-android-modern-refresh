package com.clayboicardi.game2048.game.header

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.ui.components.ScoreNumeralStyle
import com.clayboicardi.game2048.ui.components.ScoreValueText
import com.clayboicardi.game2048.ui.theme.MotionPlan
import kotlin.math.roundToInt

/**
 * A Score or Best value (MOTION_SPEC.md §3.5; DESIGN_SYSTEM.md §7.2; D-064). Under Full
 * ([MotionPlan.odometerSlide] set) it is an odometer: one `AnimatedContent` per digit position,
 * keyed by place value, so a units change never animates the tens. A rise slides the new digit in
 * from below and the old one out upwards; a fall (undo) reverses it; no stagger. A new leading
 * digit grows in and a removed one shrinks out (`odometerResize`, `odometerFade`). Each changed
 * digit rolls once to its new value. Under Reduced and Off ([MotionPlan.odometerSlide] `null`) it
 * is one plain text that changes at once.
 *
 * The places run left to right, most significant first, in LTR and RTL alike (D-104). Every digit
 * is drawn at the one [fontSize], in [color]. [value] is the displayed value, never the semantics
 * value (the chip speaks the true one). [animate] is false for a change that must not roll (new
 * game, size switch, restore: MOTION_SPEC.md §4.2), which then swaps on its composition with no
 * frame showing both values. [placeholder] ("–", Loading) replaces the digits when given.
 */
@Composable
fun ScoreOdometer(
    value: Long,
    fontSize: TextUnit,
    color: ColorProducer,
    plan: MotionPlan,
    animate: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    val text = placeholder ?: value.toString()
    if (placeholder != null || plan.odometerSlide == null) {
        ScoreValueText(text = text, fontSize = fontSize, color = color, modifier = modifier)
        return
    }
    // The direction of the latest change: the previous value is kept outside the snapshot system
    // and written after the composition that reads it (D-064: rises roll up, falls roll down).
    val previous = remember { LongHolder(value) }
    val rising = value >= previous.value
    SideEffect { previous.value = value }
    val digits = text.length
    val baseline = rememberRestingBaseline(fontSize)
    // The places keep their physical left-to-right order in every layout direction: the chips
    // mirror in RTL, a number's digits do not (D-104, DESIGN_SYSTEM.md §3.7, §5.7).
    Row(
        modifier = modifier.restingBaseline(baseline),
        horizontalArrangement = Arrangement.Absolute.Left,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Places from the most significant down to the units; one spare place on the left lets
        // a removed leading digit shrink out.
        for (place in digits downTo 0) {
            key(place) {
                OdometerPlace(
                    digit = if (place < digits) text[digits - 1 - place] else BLANK,
                    rising = rising,
                    animate = animate,
                    fontSize = fontSize,
                    color = color,
                    plan = plan,
                )
            }
        }
    }
}

/** One place value of the odometer: [digit], or [BLANK] when the value has no digit there. */
@Composable
private fun OdometerPlace(
    digit: Char,
    rising: Boolean,
    animate: Boolean,
    fontSize: TextUnit,
    color: ColorProducer,
    plan: MotionPlan,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = digit, label = "odometerDigit")
    val probe = LocalMotionProbe.current
    DisposableEffect(probe, transition) {
        val registration = probe?.registerRunning { transition.isRunning }
        onDispose { registration?.unregister() }
    }
    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = { odometerTransform(rising, animate, plan) },
        contentAlignment = Alignment.Center,
    ) { shown ->
        // An instant change draws only the new digit, so no frame shows both.
        if (shown != BLANK && (animate || shown == digit)) {
            ScoreValueText(text = shown.toString(), fontSize = fontSize, color = color)
        }
    }
}

/**
 * MOTION_SPEC.md §3.5 for one place: a digit change slides vertically with `odometerSlide` and
 * fades with `odometerFade`; a digit appearing or disappearing at the left resizes with
 * `odometerResize`.
 */
private fun AnimatedContentTransitionScope<Char>.odometerTransform(
    rising: Boolean,
    animate: Boolean,
    plan: MotionPlan,
): ContentTransform {
    val slide = plan.odometerSlide
    val fade = plan.odometerFade
    val resize = plan.odometerResize
    val animated = animate && slide != null && fade != null
    return when {
        !animated || resize == null ->
            (EnterTransition.None togetherWith ExitTransition.None).using(null)
        initialState == BLANK || targetState == BLANK ->
            (fadeIn(fade) togetherWith fadeOut(fade)).using(
                SizeTransform(clip = true) { _, _ -> resize },
            )
        else -> {
            val enter = slideInVertically(slide) { if (rising) it else -it } + fadeIn(fade)
            val exit = slideOutVertically(slide) { if (rising) -it else it } + fadeOut(fade)
            (enter togetherWith exit).using(SizeTransform(clip = true) { _, _ -> resize })
        }
    }
}

/** The first baseline in px of one line of score digits at [fontSize] (a digit at rest). */
@Composable
private fun rememberRestingBaseline(fontSize: TextUnit): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(fontSize, density, measurer) {
        val style = ScoreNumeralStyle.copy(fontSize = fontSize)
        measurer.measure("0", style, softWrap = false, maxLines = 1).firstBaseline.roundToInt()
    }
}

/**
 * States the odometer's first baseline as [baselinePx], a digit's at rest. Inherited from the
 * places, it would follow the digits while they slide, and the Score chip aligns the "+N" floats to
 * it (`ScoreChip`'s overlay), so they would jump with every roll.
 */
private fun Modifier.restingBaseline(baselinePx: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height, mapOf(FirstBaseline to baselinePx)) {
            placeable.place(0, 0)
        }
    }

/** A plain holder, never snapshot state: only a `SideEffect` writes it. */
private class LongHolder(var value: Long)

/** A place the value has no digit in (a leading digit growing in or shrinking out). */
private const val BLANK = ' '
