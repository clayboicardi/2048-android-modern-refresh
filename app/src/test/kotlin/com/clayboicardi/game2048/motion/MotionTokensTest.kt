package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import org.junit.Test

/**
 * The motion tokens (D-060, D-183, D-210; MOTION_SPEC.md §1): the §1.5 literals exactly, and
 * Compose's own settle estimate within one 16 ms frame of each §1.1 settle value. Plain JVM, no
 * Robolectric.
 */
class MotionTokensTest {
    /** AC-TOKENS-1 */
    @Test
    fun listingConstantsAreExact() {
        for ((name, actual, expected) in CONSTANTS) {
            assertWithMessage(name).that(actual).isEqualTo(expected)
        }
        for ((name, actual, expected) in EASINGS) {
            for (k in 0..EASING_SAMPLES) {
                val x = k.toFloat() / EASING_SAMPLES
                assertWithMessage("$name at $x")
                    .that(actual.transform(x))
                    .isEqualTo(
                        expected.transform(x),
                    )
            }
        }
        for ((name, spec, expected) in SPRINGS) {
            assertWithMessage("$name damping").that(spec.dampingRatio).isEqualTo(expected.first)
            assertWithMessage("$name stiffness").that(spec.stiffness).isEqualTo(expected.second)
        }
    }

    /** AC-TOKENS-1 */
    @Test
    fun composeSettleEstimatesMatchTheSpec() {
        for (case in SETTLES) {
            val spec = spring(case.dampingRatio, case.stiffness, case.threshold)
            val nanos =
                spec
                    .vectorize(Float.VectorConverter)
                    .getDurationNanos(
                        AnimationVector1D(case.from),
                        AnimationVector1D(case.to),
                        AnimationVector1D(case.velocity),
                    )
            assertSettle(case.name, nanos, case.settleMs)
        }

        // The tile slide in cells (`Offset`), 1 and 3 cells on a 241.33 px pitch (Pixel 7, 4×4).
        for ((cells, settleMs) in listOf(1f to 208, 3f to 241)) {
            val threshold = GameMotion.POSITION_THRESHOLD_PX / PIXEL_7_PITCH_PX
            val nanos =
                GameMotion.slide(Offset(threshold, threshold))
                    .vectorize(Offset.VectorConverter)
                    .getDurationNanos(
                        AnimationVector2D(0f, 0f),
                        AnimationVector2D(cells, 0f),
                        AnimationVector2D(0f, 0f),
                    )
            assertSettle("slide $cells cells (Offset)", nanos, settleMs)
        }
    }

    /** Within one 16 ms frame of the §1.1 value; prints both numbers for the D-210 record. */
    private fun assertSettle(name: String, nanos: Long, settleMs: Int) {
        val libraryMs = nanos / NS_PER_MS.toDouble()
        val message =
            String.format(
                Locale.ROOT,
                "%s: library %.1f ms, MOTION_SPEC.md §1.1 %d ms",
                name,
                libraryMs,
                settleMs,
            )
        println("MotionTokensTest: $message")
        assertWithMessage(message).that(libraryMs).isWithin(FRAME_MS).of(settleMs.toDouble())
    }

    /** One MOTION_SPEC.md §1.1 settle value: a unit-mass spring from rest or with [velocity]. */
    private data class Settle(
        val name: String,
        val dampingRatio: Float,
        val stiffness: Float,
        val from: Float,
        val to: Float,
        val velocity: Float,
        val threshold: Float,
        val settleMs: Int,
    )

    private companion object {
        const val NS_PER_MS = 1_000_000L
        const val FRAME_MS = 16.0
        const val EASING_SAMPLES = 8
        const val ALPHA = 1f / 255f
        const val SCALE = 0.002f

        /** One 4×4 cell on a Pixel 7 (724 px for 3 cells, MOTION_SPEC.md §1.1). */
        const val PIXEL_7_PITCH_PX = 724f / 3f

        /** The §1.5 listing (and D-222's `Input`), as written in MOTION_SPEC.md. */
        val CONSTANTS: List<Triple<String, Any, Any>> =
            listOf(
                Triple("SLIDE_DAMPING", GameMotion.SLIDE_DAMPING, 0.9f),
                Triple("SLIDE_STIFFNESS", GameMotion.SLIDE_STIFFNESS, 1400f),
                Triple("POP_DAMPING", GameMotion.POP_DAMPING, 0.6f),
                Triple("POP_STIFFNESS", GameMotion.POP_STIFFNESS, 800f),
                Triple("OVERLAY_DAMPING", GameMotion.OVERLAY_DAMPING, 0.8f),
                Triple("OVERLAY_STIFFNESS", GameMotion.OVERLAY_STIFFNESS, 380f),
                Triple("SLOW_DAMPING", GameMotion.SLOW_DAMPING, 0.8f),
                Triple("SLOW_STIFFNESS", GameMotion.SLOW_STIFFNESS, 200f),
                Triple("STANDARD_DAMPING", GameMotion.STANDARD_DAMPING, 0.9f),
                Triple("STANDARD_STIFFNESS", GameMotion.STANDARD_STIFFNESS, 700f),
                Triple("EFFECTS_DAMPING", GameMotion.EFFECTS_DAMPING, 1f),
                Triple("FAST_EFFECTS_STIFFNESS", GameMotion.FAST_EFFECTS_STIFFNESS, 3800f),
                Triple("DEFAULT_EFFECTS_STIFFNESS", GameMotion.DEFAULT_EFFECTS_STIFFNESS, 1600f),
                Triple("SLOW_EFFECTS_STIFFNESS", GameMotion.SLOW_EFFECTS_STIFFNESS, 800f),
                Triple("NUDGE_DAMPING", GameMotion.NUDGE_DAMPING, 0.3f),
                Triple("NUDGE_STIFFNESS", GameMotion.NUDGE_STIFFNESS, 1800f),
                Triple("POSITION_THRESHOLD_PX", GameMotion.POSITION_THRESHOLD_PX, 0.5f),
                Triple("SCALE_THRESHOLD", GameMotion.SCALE_THRESHOLD, 0.002f),
                Triple("ALPHA_THRESHOLD", GameMotion.ALPHA_THRESHOLD, 1f / 255f),
                Triple("FRACTION_THRESHOLD", GameMotion.FRACTION_THRESHOLD, 0.002f),
                Triple("MORPH_THRESHOLD", GameMotion.MORPH_THRESHOLD, 0.001f),
                Triple("NUDGE_THRESHOLD_DP", GameMotion.NUDGE_THRESHOLD_DP, 0.1f),
                Triple("INT_OFFSET_THRESHOLD_PX", GameMotion.INT_OFFSET_THRESHOLD_PX, 1),
                Triple("MERGE_REVEAL_MS", GameMotion.MERGE_REVEAL_MS, 80),
                Triple("SPAWN_DELAY_MS", GameMotion.SPAWN_DELAY_MS, 90),
                Triple("GHOST_REMOVE_MS", GameMotion.GHOST_REMOVE_MS, 200),
                Triple("POP_IMPULSE", GameMotion.POP_IMPULSE.toList(), listOf(6f, 8f, 10f, 12f)),
                Triple("NUDGE_VELOCITY_DP_S", GameMotion.NUDGE_VELOCITY_DP_S, 330f),
                Triple(
                    "Move.SPAWN_DELAY_LONG_MS",
                    GameMotion.Move.SPAWN_DELAY_LONG_MS.toList(),
                    listOf(100, 105, 110, 115),
                ),
                Triple(
                    "Move.POP_PEAK_PER_VELOCITY",
                    GameMotion.Move.POP_PEAK_PER_VELOCITY,
                    0.017633f,
                ),
                Triple("Move.SHEEN_ALPHA", GameMotion.Move.SHEEN_ALPHA, 0.35f),
                Triple("Move.GLOW_PULSE_BOOST", GameMotion.Move.GLOW_PULSE_BOOST, 0.25f),
                Triple("Move.FLASH_LIMIT_COUNT", GameMotion.Move.FLASH_LIMIT_COUNT, 3),
                Triple("Move.FLASH_LIMIT_WINDOW_MS", GameMotion.Move.FLASH_LIMIT_WINDOW_MS, 1000L),
                Triple(
                    "Move.REDUCED_MERGE_CROSSFADE_MS",
                    GameMotion.Move.REDUCED_MERGE_CROSSFADE_MS,
                    120,
                ),
                Triple("Score.UPDATE_AT_MS", GameMotion.Score.UPDATE_AT_MS, 80),
                Triple("Score.PLUS_N_RISE_DP", GameMotion.Score.PLUS_N_RISE_DP, 28f),
                Triple("Score.PLUS_N_RISE_MS", GameMotion.Score.PLUS_N_RISE_MS, 650),
                Triple("Score.PLUS_N_HOLD_MS", GameMotion.Score.PLUS_N_HOLD_MS, 250),
                Triple("Score.PLUS_N_FADE_MS", GameMotion.Score.PLUS_N_FADE_MS, 400),
                Triple("Score.PLUS_N_START_SCALE", GameMotion.Score.PLUS_N_START_SCALE, 0.8f),
                Triple("Score.PLUS_N_MAX_ALIVE", GameMotion.Score.PLUS_N_MAX_ALIVE, 3),
                Triple(
                    "Score.REDUCED_PLUS_N_FADE_MS",
                    GameMotion.Score.REDUCED_PLUS_N_FADE_MS,
                    400,
                ),
                Triple(
                    "Nudge.REJECT_MIN_INTERVAL_MS",
                    GameMotion.Nudge.REJECT_MIN_INTERVAL_MS,
                    400L,
                ),
                Triple("Nudge.PULSE_PEAK_ALPHA", GameMotion.Nudge.PULSE_PEAK_ALPHA, 0.6f),
                Triple("Nudge.PULSE_PEAK_MS", GameMotion.Nudge.PULSE_PEAK_MS, 100),
                Triple("Nudge.PULSE_MS", GameMotion.Nudge.PULSE_MS, 300),
                Triple("Nudge.PULSE_STROKE_DP", GameMotion.Nudge.PULSE_STROKE_DP, 2f),
                Triple("NewBest.AT_MS", GameMotion.NewBest.AT_MS, 80),
                Triple("NewBest.LABEL_SHIFT_DP", GameMotion.NewBest.LABEL_SHIFT_DP, 8f),
                Triple("NewBest.STAR_POINTS", GameMotion.NewBest.STAR_POINTS, 10),
                Triple("NewBest.STAR_INNER_RADIUS", GameMotion.NewBest.STAR_INNER_RADIUS, 0.82f),
                Triple("NewBest.STAR_ROUNDING", GameMotion.NewBest.STAR_ROUNDING, 0.15f),
                Triple("NewBest.STAR_SIZE_DP", GameMotion.NewBest.STAR_SIZE_DP, 20f),
                Triple("NewBest.STAR_ROTATION_DEG", GameMotion.NewBest.STAR_ROTATION_DEG, 45f),
                Triple("NewBest.STAR_ROTATION_MS", GameMotion.NewBest.STAR_ROTATION_MS, 900),
                Triple("NewBest.STAR_FADE_DELAY_MS", GameMotion.NewBest.STAR_FADE_DELAY_MS, 1200),
                Triple("NewBest.STAR_FADE_MS", GameMotion.NewBest.STAR_FADE_MS, 300),
                Triple("NewBest.SPARKLE_COUNT", GameMotion.NewBest.SPARKLE_COUNT, 8),
                Triple("NewBest.SPARKLE_SIZE_DP", GameMotion.NewBest.SPARKLE_SIZE_DP, 6f),
                Triple(
                    "NewBest.SPARKLE_SPEED_MIN_DP_S",
                    GameMotion.NewBest.SPARKLE_SPEED_MIN_DP_S,
                    120f,
                ),
                Triple(
                    "NewBest.SPARKLE_SPEED_MAX_DP_S",
                    GameMotion.NewBest.SPARKLE_SPEED_MAX_DP_S,
                    220f,
                ),
                Triple("NewBest.SPARKLE_JITTER_DEG", GameMotion.NewBest.SPARKLE_JITTER_DEG, 10f),
                Triple("NewBest.SPARKLE_LIFE_MS", GameMotion.NewBest.SPARKLE_LIFE_MS, 600),
                Triple("NewBest.SPARKLE_FADE_MS", GameMotion.NewBest.SPARKLE_FADE_MS, 250),
                Triple("Win.RING_AT_MS", GameMotion.Win.RING_AT_MS, 60),
                Triple("Win.RING_MS", GameMotion.Win.RING_MS, 700),
                Triple("Win.RING_END_SCALE", GameMotion.Win.RING_END_SCALE, 1.8f),
                Triple("Win.RING_STROKE_DP", GameMotion.Win.RING_STROKE_DP, 6f),
                Triple("Win.RING_START_ALPHA", GameMotion.Win.RING_START_ALPHA, 0.9f),
                Triple("Win.CONFETTI_AT_MS", GameMotion.Win.CONFETTI_AT_MS, 120),
                Triple("Win.CARD_AT_MS", GameMotion.Win.CARD_AT_MS, 600),
                Triple("Win.SCRIM_ALPHA", GameMotion.Win.SCRIM_ALPHA, 0.60f),
                Triple("Win.SCRIM_MS", GameMotion.Win.SCRIM_MS, 300),
                Triple("Win.CARD_START_SCALE", GameMotion.Win.CARD_START_SCALE, 0.92f),
                Triple("Win.BUTTON_STAGGER_MS", GameMotion.Win.BUTTON_STAGGER_MS, 50),
                Triple("Win.INTERACTIVE_AT_MS", GameMotion.Win.INTERACTIVE_AT_MS, 800),
                Triple("Win.BADGE_POINTS", GameMotion.Win.BADGE_POINTS, 12),
                Triple("Win.BADGE_INNER_RADIUS", GameMotion.Win.BADGE_INNER_RADIUS, 0.88f),
                Triple("Win.BADGE_ROUNDING", GameMotion.Win.BADGE_ROUNDING, 0.14f),
                Triple("Win.BADGE_CIRCLE_VERTICES", GameMotion.Win.BADGE_CIRCLE_VERTICES, 12),
                Triple("Win.BADGE_ROTATION_DEG", GameMotion.Win.BADGE_ROTATION_DEG, 60f),
                Triple("Win.BADGE_ROTATION_MS", GameMotion.Win.BADGE_ROTATION_MS, 5000),
                Triple("Win.HAPTIC_1_FROM_ORIGIN_MS", GameMotion.Win.HAPTIC_1_FROM_ORIGIN_MS, 120),
                Triple("Win.HAPTIC_2_FROM_ORIGIN_MS", GameMotion.Win.HAPTIC_2_FROM_ORIGIN_MS, 270),
                Triple("Win.REDUCED_HAPTIC_GAP_MS", GameMotion.Win.REDUCED_HAPTIC_GAP_MS, 150),
                Triple("Confetti.COUNT_COMPACT", GameMotion.Confetti.COUNT_COMPACT, 96),
                Triple("Confetti.COUNT_EXPANDED", GameMotion.Confetti.COUNT_EXPANDED, 144),
                Triple("Confetti.CAP", GameMotion.Confetti.CAP, 160),
                Triple("Confetti.SHARE_RECT", GameMotion.Confetti.SHARE_RECT, 0.60f),
                Triple("Confetti.SHARE_CIRCLE", GameMotion.Confetti.SHARE_CIRCLE, 0.25f),
                Triple("Confetti.RECT_W_DP", GameMotion.Confetti.RECT_W_DP, 6f),
                Triple("Confetti.RECT_H_DP", GameMotion.Confetti.RECT_H_DP, 10f),
                Triple("Confetti.RECT_CORNER_DP", GameMotion.Confetti.RECT_CORNER_DP, 1.5f),
                Triple("Confetti.CIRCLE_RADIUS_DP", GameMotion.Confetti.CIRCLE_RADIUS_DP, 3f),
                Triple("Confetti.STAR_SIZE_DP", GameMotion.Confetti.STAR_SIZE_DP, 8f),
                Triple("Confetti.ANGLE_MIN_DEG", GameMotion.Confetti.ANGLE_MIN_DEG, -150f),
                Triple("Confetti.ANGLE_MAX_DEG", GameMotion.Confetti.ANGLE_MAX_DEG, -30f),
                Triple("Confetti.SPEED_MIN_DP_S", GameMotion.Confetti.SPEED_MIN_DP_S, 700f),
                Triple("Confetti.SPEED_MAX_DP_S", GameMotion.Confetti.SPEED_MAX_DP_S, 1400f),
                Triple("Confetti.GRAVITY_DP_S2", GameMotion.Confetti.GRAVITY_DP_S2, 1800f),
                Triple("Confetti.DRAG_PER_S", GameMotion.Confetti.DRAG_PER_S, 2.2f),
                Triple(
                    "Confetti.FLUTTER_AMPLITUDE_DP",
                    GameMotion.Confetti.FLUTTER_AMPLITUDE_DP,
                    12f,
                ),
                Triple("Confetti.FLUTTER_OMEGA_MIN", GameMotion.Confetti.FLUTTER_OMEGA_MIN, 6f),
                Triple("Confetti.FLUTTER_OMEGA_MAX", GameMotion.Confetti.FLUTTER_OMEGA_MAX, 12f),
                Triple("Confetti.FLIP_OMEGA_MIN", GameMotion.Confetti.FLIP_OMEGA_MIN, 8f),
                Triple("Confetti.FLIP_OMEGA_MAX", GameMotion.Confetti.FLIP_OMEGA_MAX, 16f),
                Triple("Confetti.SPIN_MAX_DEG_S", GameMotion.Confetti.SPIN_MAX_DEG_S, 540f),
                Triple("Confetti.LIFE_MIN_S", GameMotion.Confetti.LIFE_MIN_S, 1.4f),
                Triple("Confetti.LIFE_MAX_S", GameMotion.Confetti.LIFE_MAX_S, 2.2f),
                Triple("Confetti.SCALE_IN_FROM", GameMotion.Confetti.SCALE_IN_FROM, 0.4f),
                Triple("Confetti.SCALE_IN_S", GameMotion.Confetti.SCALE_IN_S, 0.08f),
                Triple("Confetti.FADE_OUT_S", GameMotion.Confetti.FADE_OUT_S, 0.35f),
                Triple("Confetti.MAX_DT_S", GameMotion.Confetti.MAX_DT_S, 1f / 30f),
                Triple("GameOver.AT_MS", GameMotion.GameOver.AT_MS, 300),
                Triple("GameOver.DESATURATE_MS", GameMotion.GameOver.DESATURATE_MS, 600),
                Triple("GameOver.SATURATION_END", GameMotion.GameOver.SATURATION_END, 0.2f),
                Triple("GameOver.DEFLATE_SCALE", GameMotion.GameOver.DEFLATE_SCALE, 0.94f),
                Triple("GameOver.DEFLATE_STEP_MS", GameMotion.GameOver.DEFLATE_STEP_MS, 30),
                Triple("GameOver.DEFLATE_WINDOW_MS", GameMotion.GameOver.DEFLATE_WINDOW_MS, 180),
                Triple("GameOver.SCRIM_AT_MS", GameMotion.GameOver.SCRIM_AT_MS, 450),
                Triple("GameOver.SCRIM_ALPHA", GameMotion.GameOver.SCRIM_ALPHA, 0.78f),
                Triple("GameOver.SCRIM_MS", GameMotion.GameOver.SCRIM_MS, 300),
                Triple("GameOver.CARD_AT_MS", GameMotion.GameOver.CARD_AT_MS, 500),
                Triple("GameOver.BUTTON_STAGGER_MS", GameMotion.GameOver.BUTTON_STAGGER_MS, 50),
                Triple("GameOver.INTERACTIVE_AT_MS", GameMotion.GameOver.INTERACTIVE_AT_MS, 700),
                Triple("Card.EXIT_SCALE", GameMotion.Card.EXIT_SCALE, 0.96f),
                Triple("Card.EXIT_SCRIM_MS", GameMotion.Card.EXIT_SCRIM_MS, 200),
                Triple("Card.SATURATION_RESTORE_MS", GameMotion.Card.SATURATION_RESTORE_MS, 200),
                Triple("Card.REDUCED_FADE_MS", GameMotion.Card.REDUCED_FADE_MS, 150),
                Triple(
                    "Board.UNDO_ICON_ROTATION_DEG",
                    GameMotion.Board.UNDO_ICON_ROTATION_DEG,
                    -360f,
                ),
                Triple("Board.UNDO_ICON_MS", GameMotion.Board.UNDO_ICON_MS, 300),
                Triple("Board.NEW_GAME_STEP_MS", GameMotion.Board.NEW_GAME_STEP_MS, 15),
                Triple("Board.NEW_GAME_WINDOW_MS", GameMotion.Board.NEW_GAME_WINDOW_MS, 240),
                Triple("Board.NEW_GAME_SHRINK_SCALE", GameMotion.Board.NEW_GAME_SHRINK_SCALE, 0.6f),
                Triple("Board.NEW_GAME_SPAWN_GAP_MS", GameMotion.Board.NEW_GAME_SPAWN_GAP_MS, 60),
                Triple("Nav.SHIFT_DP", GameMotion.Nav.SHIFT_DP, 30f),
                Triple("Nav.PREDICTIVE_MS", GameMotion.Nav.PREDICTIVE_MS, 300),
                Triple("Nav.PREDICTIVE_SCALE", GameMotion.Nav.PREDICTIVE_SCALE, 0.90f),
                Triple(
                    "Input.KEY_REPEAT_MIN_INTERVAL_MS",
                    GameMotion.Input.KEY_REPEAT_MIN_INTERVAL_MS,
                    120L,
                ),
                Triple(
                    "Input.NOTHING_TO_UNDO_MIN_INTERVAL_MS",
                    GameMotion.Input.NOTHING_TO_UNDO_MIN_INTERVAL_MS,
                    2000L,
                ),
            )

        /** The M3 easing tokens of the §1.5 listing. */
        val EASINGS: List<Triple<String, Easing, Easing>> =
            listOf(
                Triple("Standard", GameMotion.Standard, CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                Triple(
                    "EmphasizedDecelerate",
                    GameMotion.EmphasizedDecelerate,
                    CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
                ),
                Triple(
                    "EmphasizedAccelerate",
                    GameMotion.EmphasizedAccelerate,
                    CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
                ),
            )

        /** Each spring token function's damping ratio and stiffness (D-060, D-183). */
        val SPRINGS: List<Triple<String, SpringSpec<Float>, Pair<Float, Float>>> =
            listOf(
                Triple("slide", GameMotion.slide(), 0.9f to 1400f),
                Triple("popSpatial", GameMotion.popSpatial(), 0.6f to 800f),
                Triple("overlaySpatial", GameMotion.overlaySpatial(), 0.8f to 380f),
                Triple("slowSpatial", GameMotion.slowSpatial(), 0.8f to 200f),
                Triple("standardSpatial", GameMotion.standardSpatial(), 0.9f to 700f),
                Triple("fastEffects", GameMotion.fastEffects(), 1f to 3800f),
                Triple("defaultEffects", GameMotion.defaultEffects(), 1f to 1600f),
                Triple("slowEffects", GameMotion.slowEffects(), 1f to 800f),
                Triple("nudge", GameMotion.nudge(), 0.3f to 1800f),
            )

        /** MOTION_SPEC.md §1.1, "Settle (Compose estimate)". */
        val SETTLES: List<Settle> =
            listOf(
                Settle("slide 241 px", 0.9f, 1400f, 0f, 241f, 0f, 0.5f, 208),
                Settle("slide 482 px", 0.9f, 1400f, 0f, 482f, 0f, 0.5f, 229),
                Settle("slide 724 px", 0.9f, 1400f, 0f, 724f, 0f, 0.5f, 241),
                Settle("slide 1400 px", 0.9f, 1400f, 0f, 1400f, 0f, 0.5f, 260),
                Settle("slide 2000 px", 0.9f, 1400f, 0f, 2000f, 0f, 0.5f, 271),
                Settle("pop v0 6", 0.6f, 800f, 1f, 1f, 6f, SCALE, 288),
                Settle("pop v0 8", 0.6f, 800f, 1f, 1f, 8f, SCALE, 305),
                Settle("pop v0 10", 0.6f, 800f, 1f, 1f, 10f, SCALE, 318),
                Settle("pop v0 12", 0.6f, 800f, 1f, 1f, 12f, SCALE, 329),
                Settle("spawn 0.6 → 1", 0.6f, 800f, 0.6f, 1f, 0f, SCALE, 325),
                Settle("badge 0 → 1", 0.6f, 800f, 0f, 1f, 0f, SCALE, 379),
                Settle("card 0.92 → 1", 0.8f, 380f, 0.92f, 1f, 0f, SCALE, 269),
                Settle("morph 0 → 1", 0.8f, 380f, 0f, 1f, 0f, 0.001f, 476),
                Settle("bounds 300 px", 0.8f, 380f, 0f, 300f, 0f, 1f, 399),
                Settle("deflate 1 → 0.94", 0.9f, 700f, 1f, 0.94f, 0f, SCALE, 178),
                Settle("nav shift 30 dp at 2.625x", 0.9f, 700f, 0f, 78.75f, 0f, 1f, 218),
                Settle("fastEffects 1/255", 1f, 3800f, 0f, 1f, 0f, ALPHA, 125),
                Settle("fastEffects 0.01", 1f, 3800f, 0f, 1f, 0f, 0.01f, 108),
                Settle("fastEffects 0.002", 1f, 3800f, 0f, 1f, 0f, 0.002f, 137),
                Settle("defaultEffects 1/255", 1f, 1600f, 0f, 1f, 0f, ALPHA, 193),
                Settle("defaultEffects 0.01", 1f, 1600f, 0f, 1f, 0f, 0.01f, 166),
                Settle("defaultEffects 0.002", 1f, 1600f, 0f, 1f, 0f, 0.002f, 212),
                Settle("slowEffects 1/255", 1f, 800f, 0f, 1f, 0f, ALPHA, 272),
                Settle("slowEffects 0.01", 1f, 800f, 0f, 1f, 0f, 0.01f, 235),
                Settle("slowEffects 0.002", 1f, 800f, 0f, 1f, 0f, 0.002f, 299),
                Settle("nudge v0 330 dp/s", 0.3f, 1800f, 0f, 0f, 330f, 0.1f, 346),
            )
    }
}
