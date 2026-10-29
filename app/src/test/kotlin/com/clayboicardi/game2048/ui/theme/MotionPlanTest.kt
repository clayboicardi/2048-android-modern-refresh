package com.clayboicardi.game2048.ui.theme

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertWithMessage
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * `GameMotion.plan(level)` (ARCHITECTURE.md §5.6, MOTION_SPEC.md §1.4, §5.1; D-063, D-183): every
 * `MotionPlan` field per its KDoc, the pop-impulse tiers and neighbour cap, the spawn-delay table
 * and the new-game stagger. Expected values are the spec's literals. Plain JVM, no Robolectric.
 */
class MotionPlanTest {
    /** F13-AC5 */
    @Test
    fun fullPlanUsesGameMotionTokens() {
        val plan = GameMotion.plan(MotionLevel.FULL)
        assertFullBoardSpecs(plan)
        assertFullHeaderSpecs(plan)
        assertFullOffsets(plan)
        assertFullWinSpecs(plan)
        assertFullGameOverAndExitSpecs(plan)
    }

    /** Full: the win sequence (MOTION_SPEC.md §3.11; D-236). */
    private fun assertFullWinSpecs(plan: MotionPlan) {
        assertWithMessage("card").that(plan.card).isEqualTo(CardMotion.SEQUENCE)
        assertWithMessage("winT0Ms").that(plan.winT0Ms).isEqualTo(80)
        assertWithMessage("winRingAtMs").that(plan.winRingAtMs).isEqualTo(140)
        assertTween("winRing", plan.winRing, 700, EmphasizedDecelerate)
        assertWithMessage("winConfettiAtMs").that(plan.winConfettiAtMs).isEqualTo(200)
        assertWithMessage("winScrimAtMs").that(plan.winScrimAtMs).isEqualTo(680)
        assertTween("winScrim", plan.winScrim, 300, Standard)
        assertWithMessage("winCardAtMs").that(plan.winCardAtMs).isEqualTo(680)
        assertWithMessage("winInteractiveAtMs").that(plan.winInteractiveAtMs).isEqualTo(880)
        assertWithMessage("cardStartScale").that(plan.cardStartScale).isEqualTo(0.92f)
        assertSpring("cardScale", plan.cardScale, 0.8f, 380f, SCALE)
        assertWithMessage("cardStartAlpha").that(plan.cardStartAlpha).isEqualTo(0f)
        assertSpring("cardAlpha", plan.cardAlpha, 1f, 3800f, ALPHA)
        assertSpring("buttonAlpha", plan.buttonAlpha, 1f, 3800f, ALPHA)
        assertWithMessage("buttonStaggerMs").that(plan.buttonStaggerMs).isEqualTo(50)
        assertSpring("badgeMorph", plan.badgeMorph, 0.8f, 380f, MORPH)
        assertSpring("badgeScale", plan.badgeScale, 0.6f, 800f, SCALE)
        assertTween("badgeRotation", plan.badgeRotation, 5000, LinearEasing)
    }

    /** Full: the game-over sequence and the card exits (MOTION_SPEC.md §3.12, §3.13; D-236). */
    private fun assertFullGameOverAndExitSpecs(plan: MotionPlan) {
        assertWithMessage("overT0Ms").that(plan.overT0Ms).isEqualTo(300)
        assertTween("overDesaturate", plan.overDesaturate, 600, Standard)
        assertSpring("overDeflate", plan.overDeflate, 0.9f, 700f, SCALE)
        assertWithMessage("overScrimAtMs").that(plan.overScrimAtMs).isEqualTo(750)
        assertTween("overScrim", plan.overScrim, 300, Standard)
        assertWithMessage("overCardAtMs").that(plan.overCardAtMs).isEqualTo(800)
        assertWithMessage("overInteractiveAtMs").that(plan.overInteractiveAtMs).isEqualTo(1000)
        // 4×4 from a corner: mMax 6, step 30; 8×8 from a corner: mMax 14, step 180 / 14.
        val delays = mapOf((0 to 6) to 300, (3 to 6) to 390, (6 to 6) to 480)
        val delays8 = mapOf((1 to 14) to 313, (7 to 14) to 390, (14 to 14) to 480)
        for ((mm, delayMs) in delays + delays8) {
            assertWithMessage("overDeflateDelayMs$mm")
                .that(plan.overDeflateDelayMs(mm.first, mm.second))
                .isEqualTo(delayMs)
        }
        assertSpring("exitCardAlpha", plan.exitCardAlpha, 1f, 3800f, ALPHA)
        assertSpring("exitCardScale", plan.exitCardScale, 0.9f, 1400f, SCALE)
        assertTween("exitScrim", plan.exitScrim, 200, EmphasizedAccelerate)
        assertTween("exitSaturation", plan.exitSaturation, 200, Standard)
        assertTween("keepGoingScrim", plan.keepGoingScrim, 300, Standard)
    }

    /** Full: slide, merge, spawn, nudge, undo, new game and size switch (ARCHITECTURE.md §5.6). */
    private fun assertFullBoardSpecs(plan: MotionPlan) {
        assertWithMessage("level").that(plan.level).isEqualTo(MotionLevel.FULL)
        assertWithMessage("slide")
            .that(plan.slide)
            .isEqualTo(CellSpring(dampingRatio = 0.9f, stiffness = 1400f, thresholdPx = 0.5f))
        assertSnap("ghostHide", plan.ghostHide, delayMs = 200)
        assertSnap("mergeReveal", plan.mergeReveal, delayMs = 80)
        assertSpring("pop", plan.pop, 0.6f, 800f, SCALE)
        assertWithMessage("mergeRevealMs").that(plan.mergeRevealMs).isEqualTo(80)
        assertSpring("sheen", plan.sheen, 1f, 3800f, ALPHA)
        assertSpring("glowPulse", plan.glowPulse, 1f, 1600f, ALPHA)
        assertWithMessage("mergedStartAlpha").that(plan.mergedStartAlpha).isEqualTo(0f)
        assertWithMessage("spawnStartScale").that(plan.spawnStartScale).isEqualTo(0.6f)
        assertWithMessage("spawnStartAlpha").that(plan.spawnStartAlpha).isEqualTo(0f)
        assertSpring("spawnScale", plan.spawnScale, 0.6f, 800f, SCALE)
        assertSpring("spawnAlpha", plan.spawnAlpha, 1f, 3800f, ALPHA)
        assertWithMessage("invalidMove").that(plan.invalidMove).isEqualTo(InvalidMoveMotion.NUDGE)
        assertWithMessage("nudge")
            .that(plan.nudge)
            .isEqualTo(DpSpring(dampingRatio = 0.3f, stiffness = 1800f, thresholdDp = 0.1f))
        assertWithMessage("outlinePulse").that(plan.outlinePulse).isNull()
        assertSpring("undoCrossfade", plan.undoCrossfade, 1f, 3800f, ALPHA)
        assertTween("undoIconSpin", plan.undoIconSpin, 300, EmphasizedDecelerate)
        assertWithMessage("newGame").that(plan.newGame).isEqualTo(NewGameMotion.CASCADE)
        assertSpring("newGameFade", plan.newGameFade, 1f, 3800f, ALPHA)
        assertSpring("sizeSwitchIn", plan.sizeSwitchIn, 1f, 1600f, ALPHA)
        assertSpring("sizeSwitchOut", plan.sizeSwitchOut, 1f, 3800f, ALPHA)
    }

    /** Full: "+N", odometer and new best (MOTION_SPEC.md §3.4–§3.6). */
    private fun assertFullHeaderSpecs(plan: MotionPlan) {
        assertWithMessage("plusN").that(plan.plusN).isEqualTo(PlusNMotion.RISE)
        assertWithMessage("plusNAtMs").that(plan.plusNAtMs).isEqualTo(80)
        assertTween("plusNRise", plan.plusNRise, 650, EmphasizedDecelerate)
        // keyframes { durationMillis = 650; 1f at 0; 1f at 250; 0f at 650 }, linear.
        assertCurve(
            "plusNAlpha",
            plan.plusNAlpha,
            durationMs = 650,
            0 to 1f,
            250 to 1f,
            450 to 0.5f,
            650 to 0f,
        )
        assertSpring("plusNScale", plan.plusNScale, 0.6f, 800f, SCALE)
        assertWithMessage("scoreAtMs").that(plan.scoreAtMs).isEqualTo(80)
        assertSpring("odometerSlide", plan.odometerSlide, 0.9f, 1400f, IntOffset(1, 1))
        assertSpring("odometerFade", plan.odometerFade, 1f, 3800f, ALPHA)
        assertSpring("odometerResize", plan.odometerResize, 0.8f, 380f, IntSize(1, 1))
        assertWithMessage("newBest").that(plan.newBest).isEqualTo(NewBestMotion.MOMENT)
        assertWithMessage("newBestAtMs").that(plan.newBestAtMs).isEqualTo(80)
        assertSpring("newBestColor", plan.newBestColor, 1f, 1600f, threshold = null)
    }

    /** Full: pop impulses with the cap, spawn delays and the new-game stagger (D-183). */
    private fun assertFullOffsets(plan: MotionPlan) {
        // Per merged tile, by its own value; a 4×4-like gap ratio of 0.14 leaves the cap at 15.9.
        val tiers = mapOf(1..6 to 6f, 7..9 to 8f, 10..10 to 10f, 11..17 to 12f)
        for ((exps, v0) in tiers) {
            for (exp in exps) {
                assertWithMessage("popImpulse($exp)")
                    .that(plan.popImpulse(exp, gapPx = 14f, tilePx = 100f))
                    .isEqualTo(v0)
            }
        }
        // MOTION_SPEC.md §3.3: 3×3 on a 720 dp board, tile 213.3 dp and gap 20 dp: v0 ≤ 10.6.
        val capped = plan.popImpulse(11, gapPx = 20f, tilePx = 213.33f)
        assertWithMessage("capped popImpulse(11)").that(capped).isWithin(0.01f).of(10.63f)
        assertWithMessage("popImpulse(10) under the cap")
            .that(plan.popImpulse(10, gapPx = 20f, tilePx = 213.33f))
            .isEqualTo(10f)

        val delays =
            mapOf(0 to 90, 1 to 90, 2 to 90, 3 to 90, 4 to 100, 5 to 105, 6 to 110, 7 to 115)
        for ((cells, delayMs) in delays) {
            assertWithMessage("spawnDelayMs($cells)")
                .that(plan.spawnDelayMs(cells))
                .isEqualTo(delayMs)
        }

        // min(15, 240 / n): 16 outgoing tiles of a full 4×4 → 15; 64 of a full 8×8 → 3.
        val steps = mapOf(1 to 15, 2 to 15, 14 to 15, 16 to 15, 17 to 14, 24 to 10, 64 to 3)
        for ((n, stepMs) in steps) {
            assertWithMessage("newGameStepMs($n)").that(plan.newGameStepMs(n)).isEqualTo(stepMs)
        }
    }

    /** F13-AC6 */
    @Test
    fun reducedPlanSubstitutions() {
        val plan = GameMotion.plan(MotionLevel.REDUCED)
        assertReducedBoardSpecs(plan)
        assertReducedHeaderSpecs(plan)
        assertReducedOverlaySpecs(plan)
    }

    /** Reduced: 150 ms linear fades from the origin, interactive at 150 (MOTION_SPEC.md §3.11). */
    private fun assertReducedOverlaySpecs(plan: MotionPlan) {
        assertWithMessage("card").that(plan.card).isEqualTo(CardMotion.FADE)
        val atOrigin =
            mapOf(
                "winT0Ms" to plan.winT0Ms,
                "winRingAtMs" to plan.winRingAtMs,
                "winScrimAtMs" to plan.winScrimAtMs,
                "winCardAtMs" to plan.winCardAtMs,
                "buttonStaggerMs" to plan.buttonStaggerMs,
                "overT0Ms" to plan.overT0Ms,
                "overScrimAtMs" to plan.overScrimAtMs,
                "overCardAtMs" to plan.overCardAtMs,
                "overDeflateDelayMs(3, 6)" to plan.overDeflateDelayMs(3, 6),
            )
        for ((name, ms) in atOrigin) assertWithMessage(name).that(ms).isEqualTo(0)
        assertWithMessage("winInteractiveAtMs").that(plan.winInteractiveAtMs).isEqualTo(150)
        assertWithMessage("overInteractiveAtMs").that(plan.overInteractiveAtMs).isEqualTo(150)
        val fades =
            mapOf(
                "winScrim" to plan.winScrim,
                "cardAlpha" to plan.cardAlpha,
                "overDesaturate" to plan.overDesaturate,
                "overScrim" to plan.overScrim,
                "exitCardAlpha" to plan.exitCardAlpha,
                "exitScrim" to plan.exitScrim,
                "exitSaturation" to plan.exitSaturation,
                "keepGoingScrim" to plan.keepGoingScrim,
            )
        for ((name, spec) in fades) assertTween(name, spec, 150, LinearEasing)
        val none =
            mapOf(
                "winRing" to plan.winRing,
                "winConfettiAtMs" to plan.winConfettiAtMs,
                "cardScale" to plan.cardScale,
                "buttonAlpha" to plan.buttonAlpha,
                "badgeMorph" to plan.badgeMorph,
                "badgeScale" to plan.badgeScale,
                "badgeRotation" to plan.badgeRotation,
                "overDeflate" to plan.overDeflate,
                "exitCardScale" to plan.exitCardScale,
            )
        for ((name, value) in none) assertWithMessage(name).that(value).isNull()
        assertWithMessage("cardStartScale").that(plan.cardStartScale).isEqualTo(1f)
        assertWithMessage("cardStartAlpha").that(plan.cardStartAlpha).isEqualTo(0f)
    }

    /**
     * Reduced: slide, merge, spawn, nudge, undo, new game and size switch (MOTION_SPEC.md §5.1).
     */
    private fun assertReducedBoardSpecs(plan: MotionPlan) {
        assertWithMessage("level").that(plan.level).isEqualTo(MotionLevel.REDUCED)
        assertWithMessage("slide").that(plan.slide).isNull()
        // The 120 ms merge crossfade above the ghosts, which hide at +120.
        assertSnap("ghostHide", plan.ghostHide, delayMs = 120)
        assertTween("mergeReveal", plan.mergeReveal, 120, LinearEasing)
        assertWithMessage("pop").that(plan.pop).isNull()
        assertWithMessage("mergeRevealMs").that(plan.mergeRevealMs).isEqualTo(0)
        assertWithMessage("sheen").that(plan.sheen).isNull()
        assertWithMessage("glowPulse").that(plan.glowPulse).isNull()
        assertWithMessage("mergedStartAlpha").that(plan.mergedStartAlpha).isEqualTo(0f)
        // Spawn alpha only, from +0: scale 1, no delay.
        assertWithMessage("spawnStartScale").that(plan.spawnStartScale).isEqualTo(1f)
        assertWithMessage("spawnStartAlpha").that(plan.spawnStartAlpha).isEqualTo(0f)
        assertWithMessage("spawnScale").that(plan.spawnScale).isNull()
        assertSpring("spawnAlpha", plan.spawnAlpha, 1f, 3800f, ALPHA)
        for (cells in 0..7) {
            assertWithMessage("spawnDelayMs($cells)").that(plan.spawnDelayMs(cells)).isEqualTo(0)
        }
        // The nudge becomes the 300 ms outline pulse 0 → 0.6 → 0.
        assertWithMessage("invalidMove")
            .that(plan.invalidMove)
            .isEqualTo(InvalidMoveMotion.OUTLINE_PULSE)
        assertWithMessage("nudge").that(plan.nudge).isNull()
        assertCurve(
            "outlinePulse",
            plan.outlinePulse,
            durationMs = 300,
            0 to 0f,
            50 to 0.3f,
            100 to 0.6f,
            200 to 0.3f,
            300 to 0f,
        )
        assertSpring("undoCrossfade", plan.undoCrossfade, 1f, 3800f, ALPHA)
        assertWithMessage("undoIconSpin").that(plan.undoIconSpin).isNull()
        assertWithMessage("newGame").that(plan.newGame).isEqualTo(NewGameMotion.FADE)
        assertSpring("newGameFade", plan.newGameFade, 1f, 3800f, ALPHA)
        assertWithMessage("newGameStepMs(16)").that(plan.newGameStepMs(16)).isEqualTo(0)
        assertSpring("sizeSwitchIn", plan.sizeSwitchIn, 1f, 1600f, ALPHA)
        assertSpring("sizeSwitchOut", plan.sizeSwitchOut, 1f, 3800f, ALPHA)
        assertWithMessage("popImpulse(11)").that(plan.popImpulse(11, 14f, 100f)).isEqualTo(0f)
    }

    /** Reduced: "+N", odometer and new best (MOTION_SPEC.md §3.4–§3.6). */
    private fun assertReducedHeaderSpecs(plan: MotionPlan) {
        // "+N" fades in place over 400 ms from the origin.
        assertWithMessage("plusN").that(plan.plusN).isEqualTo(PlusNMotion.FADE)
        assertWithMessage("plusNAtMs").that(plan.plusNAtMs).isEqualTo(0)
        assertWithMessage("plusNRise").that(plan.plusNRise).isNull()
        assertTween("plusNAlpha", plan.plusNAlpha, 400, LinearEasing)
        assertWithMessage("plusNScale").that(plan.plusNScale).isNull()
        // The score follows the true score on F0: one plain Text.
        assertWithMessage("scoreAtMs").that(plan.scoreAtMs).isNull()
        assertWithMessage("odometerSlide").that(plan.odometerSlide).isNull()
        assertWithMessage("odometerFade").that(plan.odometerFade).isNull()
        assertWithMessage("odometerResize").that(plan.odometerResize).isNull()
        // Label swap plus the container crossfade, from the origin.
        assertWithMessage("newBest").that(plan.newBest).isEqualTo(NewBestMotion.CROSSFADE)
        assertWithMessage("newBestAtMs").that(plan.newBestAtMs).isEqualTo(0)
        assertSpring("newBestColor", plan.newBestColor, 1f, 1600f, threshold = null)
    }

    /** F13-AC7 */
    @Test
    fun offPlanIsInstant() {
        val plan = GameMotion.plan(MotionLevel.OFF)

        assertWithMessage("level").that(plan.level).isEqualTo(MotionLevel.OFF)
        // Every spec is null and every delay 0, including fields added after this test (T5).
        val fields =
            MotionPlan::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        for (field in fields) {
            field.isAccessible = true
            val value = field.get(plan)
            when (field.type) {
                MotionLevel::class.java,
                InvalidMoveMotion::class.java,
                PlusNMotion::class.java,
                NewBestMotion::class.java,
                NewGameMotion::class.java,
                CardMotion::class.java,
                Float::class.javaPrimitiveType -> Unit
                Int::class.javaPrimitiveType ->
                    assertWithMessage("delay ${field.name}").that(value).isEqualTo(0)
                else -> assertWithMessage("spec ${field.name}").that(value).isNull()
            }
        }
        // Every start value equals its end value.
        assertWithMessage("mergedStartAlpha").that(plan.mergedStartAlpha).isEqualTo(1f)
        assertWithMessage("spawnStartScale").that(plan.spawnStartScale).isEqualTo(1f)
        assertWithMessage("spawnStartAlpha").that(plan.spawnStartAlpha).isEqualTo(1f)
        assertWithMessage("invalidMove").that(plan.invalidMove).isEqualTo(InvalidMoveMotion.NONE)
        assertWithMessage("newGame").that(plan.newGame).isEqualTo(NewGameMotion.INSTANT)
        assertWithMessage("plusN").that(plan.plusN).isEqualTo(PlusNMotion.NONE)
        assertWithMessage("newBest").that(plan.newBest).isEqualTo(NewBestMotion.INSTANT)
        assertWithMessage("card").that(plan.card).isEqualTo(CardMotion.INSTANT)
        assertWithMessage("cardStartScale").that(plan.cardStartScale).isEqualTo(1f)
        assertWithMessage("cardStartAlpha").that(plan.cardStartAlpha).isEqualTo(1f)
        assertWithMessage("overDeflateDelayMs(3, 6)")
            .that(plan.overDeflateDelayMs(3, 6))
            .isEqualTo(0)
        assertWithMessage("popImpulse(11)").that(plan.popImpulse(11, 14f, 100f)).isEqualTo(0f)
        assertWithMessage("spawnDelayMs(7)").that(plan.spawnDelayMs(7)).isEqualTo(0)
        assertWithMessage("newGameStepMs(16)").that(plan.newGameStepMs(16)).isEqualTo(0)
    }

    /** D-183: one shared plan per level, so a held plan compares equal across recompositions. */
    @Test
    fun planIsSharedPerLevel() {
        for (level in MotionLevel.entries) {
            assertWithMessage("plan($level)")
                .that(GameMotion.plan(level))
                .isSameInstanceAs(GameMotion.plan(level))
        }
    }

    private fun assertSpring(
        name: String,
        spec: Any?,
        dampingRatio: Float,
        stiffness: Float,
        threshold: Any?,
    ) {
        assertWithMessage(name).that(spec).isInstanceOf(SpringSpec::class.java)
        val spring = spec as SpringSpec<*>
        assertWithMessage("$name damping").that(spring.dampingRatio).isEqualTo(dampingRatio)
        assertWithMessage("$name stiffness").that(spring.stiffness).isEqualTo(stiffness)
        assertWithMessage("$name threshold").that(spring.visibilityThreshold).isEqualTo(threshold)
    }

    private fun assertSnap(name: String, spec: Any?, delayMs: Int) {
        assertWithMessage(name).that(spec).isInstanceOf(SnapSpec::class.java)
        assertWithMessage("$name delay").that((spec as SnapSpec<*>).delay).isEqualTo(delayMs)
    }

    private fun assertTween(name: String, spec: Any?, durationMs: Int, easing: Easing) {
        assertWithMessage(name).that(spec).isInstanceOf(TweenSpec::class.java)
        val tween = spec as TweenSpec<*>
        assertWithMessage("$name duration").that(tween.durationMillis).isEqualTo(durationMs)
        assertWithMessage("$name delay").that(tween.delay).isEqualTo(0)
        for (k in 0..EASING_SAMPLES) {
            val x = k.toFloat() / EASING_SAMPLES
            assertWithMessage("$name easing at $x")
                .that(tween.easing.transform(x))
                .isEqualTo(easing.transform(x))
        }
    }

    /** A Float spec's duration and its values at the given play times (ms to value). */
    private fun assertCurve(
        name: String,
        spec: FiniteAnimationSpec<Float>?,
        durationMs: Int,
        vararg points: Pair<Int, Float>,
    ) {
        assertWithMessage(name).that(spec).isNotNull()
        val vectorized = requireNotNull(spec).vectorize(Float.VectorConverter)
        val start = AnimationVector1D(0f)
        val end = AnimationVector1D(0f)
        val v0 = AnimationVector1D(0f)
        assertWithMessage("$name duration")
            .that(vectorized.getDurationNanos(start, end, v0))
            .isEqualTo(durationMs * NS_PER_MS)
        for ((ms, value) in points) {
            assertWithMessage("$name at $ms ms")
                .that(vectorized.getValueFromNanos(ms * NS_PER_MS, start, end, v0).value)
                .isWithin(CURVE_TOLERANCE)
                .of(value)
        }
    }

    private companion object {
        const val SCALE = 0.002f
        const val ALPHA = 1f / 255f
        const val MORPH = 0.001f
        const val NS_PER_MS = 1_000_000L
        const val EASING_SAMPLES = 8
        const val CURVE_TOLERANCE = 1e-4f
        val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }
}
