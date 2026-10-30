package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * D-183: `delayedSpec(spec, delayMs)` holds the initial value and velocity for the delay, then
 * plays [spec] shifted by the delay; its duration is the delay plus the wrapped duration
 * (MOTION_SPEC.md §2.3 T1, §8.5). Plain JVM, no Robolectric.
 */
class DelayedSpecTest {
    /**
     * D-183: the merge pop (a spring from rest on its target with an initial velocity), the spawn
     * scale (a spring from 0.6) and the "+N" rise (a tween), each at the move's delays.
     */
    @Test
    fun valueAndVelocityFollowTheShiftedSpec() {
        val cases =
            listOf(
                Case("pop", GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD), 1f, 1f, POP_V0),
                Case(
                    "spawn scale",
                    GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD),
                    0.6f,
                    1f,
                    0f,
                ),
                Case(
                    "rise",
                    tween(RISE_MS, easing = GameMotion.EmphasizedDecelerate),
                    0f,
                    -28f,
                    0f,
                ),
            )
        for (case in cases) {
            for (delayMs in DELAYS_MS) {
                assertShifted(case, delayMs)
            }
        }
    }

    /** D-183: a delay of 0 or less is the plain spec itself. */
    @Test
    fun noDelayReturnsTheSpecItself() {
        val spec = GameMotion.fastEffects<Float>(GameMotion.ALPHA_THRESHOLD)
        assertWithMessage("delay 0").that(delayedSpec(spec, 0)).isSameInstanceAs(spec)
        assertWithMessage("delay -5").that(delayedSpec(spec, -5)).isSameInstanceAs(spec)
        assertWithMessage("equal delayed specs")
            .that(delayedSpec(spec, MERGE_DELAY_MS))
            .isEqualTo(
                delayedSpec(GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD), MERGE_DELAY_MS),
            )
    }

    private class Case(
        val name: String,
        val spec: FiniteAnimationSpec<Float>,
        val from: Float,
        val to: Float,
        val velocity: Float,
    )

    private fun assertShifted(case: Case, delayMs: Int) {
        val converter = Float.VectorConverter
        val plain = case.spec.vectorize(converter)
        val delayed = delayedSpec(case.spec, delayMs).vectorize(converter)
        val from = AnimationVector1D(case.from)
        val to = AnimationVector1D(case.to)
        val v0 = AnimationVector1D(case.velocity)
        val delayNs = delayMs * NS_PER_MS
        val label = "${case.name} delayed by $delayMs ms"

        for (t in listOf(0L, delayNs - 1)) {
            assertWithMessage("$label: value at $t ns")
                .that(delayed.getValueFromNanos(t, from, to, v0).value)
                .isEqualTo(case.from)
            assertWithMessage("$label: velocity at $t ns")
                .that(delayed.getVelocityFromNanos(t, from, to, v0).value)
                .isEqualTo(case.velocity)
        }
        for (shift in listOf(0L, FRAME_MS * NS_PER_MS)) {
            val t = delayNs + shift
            assertWithMessage("$label: value at $t ns")
                .that(delayed.getValueFromNanos(t, from, to, v0).value)
                .isEqualTo(plain.getValueFromNanos(shift, from, to, v0).value)
            assertWithMessage("$label: velocity at $t ns")
                .that(delayed.getVelocityFromNanos(t, from, to, v0).value)
                .isEqualTo(plain.getVelocityFromNanos(shift, from, to, v0).value)
        }
        assertWithMessage("$label: duration")
            .that(delayed.getDurationNanos(from, to, v0))
            .isEqualTo(delayNs + plain.getDurationNanos(from, to, v0))
    }

    private companion object {
        const val NS_PER_MS = 1_000_000L
        const val FRAME_MS = 16L
        const val RISE_MS = 650
        const val MERGE_DELAY_MS = 80
        const val POP_V0 = 6f
        val DELAYS_MS = listOf(MERGE_DELAY_MS, 90, 115)
    }
}
