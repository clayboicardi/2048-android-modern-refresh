// app/src/main/kotlin/com/clayboicardi/game2048/ui/theme/GameMotion.kt
package com.clayboicardi.game2048.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Effective motion level (D-063): OFF when the system animator scale is 0, else the Motion setting.
 */
enum class MotionLevel {
    FULL,
    REDUCED,
    OFF,
}

/**
 * Game motion tokens (D-060; MOTION_SPEC.md §1). Values copy androidx material3
 * tokens/{Standard,Expressive}MotionTokens.kt (research/04 §3.1). Game code never reads
 * MaterialTheme.motionScheme (D-026).
 */
object GameMotion {
    // Springs: damping ratio / stiffness (unit mass).
    const val SLIDE_DAMPING = 0.9f
    const val SLIDE_STIFFNESS = 1400f
    const val POP_DAMPING = 0.6f
    const val POP_STIFFNESS = 800f
    const val OVERLAY_DAMPING = 0.8f
    const val OVERLAY_STIFFNESS = 380f
    const val SLOW_DAMPING = 0.8f
    const val SLOW_STIFFNESS = 200f
    const val STANDARD_DAMPING = 0.9f
    const val STANDARD_STIFFNESS = 700f
    const val EFFECTS_DAMPING = 1f
    const val FAST_EFFECTS_STIFFNESS = 3800f
    const val DEFAULT_EFFECTS_STIFFNESS = 1600f
    const val SLOW_EFFECTS_STIFFNESS = 800f
    const val NUDGE_DAMPING = 0.3f
    const val NUDGE_STIFFNESS = 1800f

    fun <T> slide(threshold: T? = null): SpringSpec<T> =
        spring(SLIDE_DAMPING, SLIDE_STIFFNESS, threshold)

    fun <T> popSpatial(threshold: T? = null): SpringSpec<T> =
        spring(POP_DAMPING, POP_STIFFNESS, threshold)

    fun <T> overlaySpatial(threshold: T? = null): SpringSpec<T> =
        spring(OVERLAY_DAMPING, OVERLAY_STIFFNESS, threshold)

    fun <T> slowSpatial(threshold: T? = null): SpringSpec<T> =
        spring(SLOW_DAMPING, SLOW_STIFFNESS, threshold)

    fun <T> standardSpatial(threshold: T? = null): SpringSpec<T> =
        spring(STANDARD_DAMPING, STANDARD_STIFFNESS, threshold)

    fun <T> fastEffects(threshold: T? = null): SpringSpec<T> =
        spring(EFFECTS_DAMPING, FAST_EFFECTS_STIFFNESS, threshold)

    fun <T> defaultEffects(threshold: T? = null): SpringSpec<T> =
        spring(EFFECTS_DAMPING, DEFAULT_EFFECTS_STIFFNESS, threshold)

    fun <T> slowEffects(threshold: T? = null): SpringSpec<T> =
        spring(EFFECTS_DAMPING, SLOW_EFFECTS_STIFFNESS, threshold)

    /** The D-060 NUDGE token, as a function so the 0.1 dp threshold (in px) can be passed. */
    fun <T> nudge(threshold: T? = null): SpringSpec<T> =
        spring(NUDGE_DAMPING, NUDGE_STIFFNESS, threshold)

    // Visibility thresholds (§1.2).
    const val POSITION_THRESHOLD_PX = 0.5f
    const val SCALE_THRESHOLD = 0.002f
    const val ALPHA_THRESHOLD = 1f / 255f
    const val FRACTION_THRESHOLD = 0.002f
    const val MORPH_THRESHOLD = 0.001f
    const val NUDGE_THRESHOLD_DP = 0.1f
    const val INT_OFFSET_THRESHOLD_PX = 1

    // Easings (M3 tokens/MotionTokens.kt).
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // D-060 game constants (ms from the move origin unless stated).
    const val MERGE_REVEAL_MS = 80
    const val SPAWN_DELAY_MS = 90
    const val GHOST_REMOVE_MS = 200
    /** s⁻¹ by the merged tile's own value: ≤ 64, 128–512, 1024, ≥ 2048 (D-183). */
    val POP_IMPULSE = floatArrayOf(6f, 8f, 10f, 12f)
    const val NUDGE_VELOCITY_DP_S = 330f

    object Move {
        /** Spawn delay when the move's longest slide is 4, 5, 6 or 7 cells (D-183). */
        val SPAWN_DELAY_LONG_MS = intArrayOf(100, 105, 110, 115)
        /** Pop peak − 1 = this × v0 for popSpatial started at rest on its target (linear in v0). */
        const val POP_PEAK_PER_VELOCITY = 0.017633f
        const val SHEEN_ALPHA = 0.35f
        const val GLOW_PULSE_BOOST = 0.25f
        const val FLASH_LIMIT_COUNT = 3
        const val FLASH_LIMIT_WINDOW_MS = 1000L
        const val REDUCED_MERGE_CROSSFADE_MS = 120
    }

    object Score {
        const val UPDATE_AT_MS = 80
        const val PLUS_N_RISE_DP = 28f
        const val PLUS_N_RISE_MS = 650
        const val PLUS_N_HOLD_MS = 250
        const val PLUS_N_FADE_MS = 400
        const val PLUS_N_START_SCALE = 0.8f
        const val PLUS_N_MAX_ALIVE = 3
        const val REDUCED_PLUS_N_FADE_MS = 400
    }

    object Nudge {
        const val REJECT_MIN_INTERVAL_MS = 400L
        const val PULSE_PEAK_ALPHA = 0.6f
        const val PULSE_PEAK_MS = 100
        const val PULSE_MS = 300
        const val PULSE_STROKE_DP = 2f
    }

    object NewBest {
        const val AT_MS = 80
        const val LABEL_SHIFT_DP = 8f
        const val STAR_POINTS = 10
        const val STAR_INNER_RADIUS = 0.82f
        const val STAR_ROUNDING = 0.15f
        const val STAR_SIZE_DP = 20f
        const val STAR_ROTATION_DEG = 45f
        const val STAR_ROTATION_MS = 900
        const val STAR_FADE_DELAY_MS = 1200
        const val STAR_FADE_MS = 300
        const val SPARKLE_COUNT = 8
        const val SPARKLE_SIZE_DP = 6f
        const val SPARKLE_SPEED_MIN_DP_S = 120f
        const val SPARKLE_SPEED_MAX_DP_S = 220f
        const val SPARKLE_JITTER_DEG = 10f
        const val SPARKLE_LIFE_MS = 600
        const val SPARKLE_FADE_MS = 250
    }

    /** Offsets relative to T0 = MERGE_REVEAL_MS (D-064). */
    object Win {
        const val RING_AT_MS = 60
        const val RING_MS = 700
        const val RING_END_SCALE = 1.8f
        const val RING_STROKE_DP = 6f
        const val RING_START_ALPHA = 0.9f
        const val CONFETTI_AT_MS = 120
        const val CARD_AT_MS = 600
        const val SCRIM_ALPHA = 0.60f
        const val SCRIM_MS = 300
        const val CARD_START_SCALE = 0.92f
        const val BUTTON_STAGGER_MS = 50
        const val INTERACTIVE_AT_MS = 800
        const val BADGE_POINTS = 12
        const val BADGE_INNER_RADIUS = 0.88f
        const val BADGE_ROUNDING = 0.14f
        const val BADGE_CIRCLE_VERTICES = 12
        const val BADGE_ROTATION_DEG = 60f
        const val BADGE_ROTATION_MS = 5000
        const val HAPTIC_1_FROM_ORIGIN_MS = 120
        const val HAPTIC_2_FROM_ORIGIN_MS = 270
        const val REDUCED_HAPTIC_GAP_MS = 150
    }

    object Confetti {
        const val COUNT_COMPACT = 96
        const val COUNT_EXPANDED = 144
        const val CAP = 160
        const val SHARE_RECT = 0.60f
        const val SHARE_CIRCLE = 0.25f // the remaining 0.15 are stars
        const val RECT_W_DP = 6f
        const val RECT_H_DP = 10f
        const val RECT_CORNER_DP = 1.5f
        const val CIRCLE_RADIUS_DP = 3f
        const val STAR_SIZE_DP = 8f
        const val ANGLE_MIN_DEG = -150f
        const val ANGLE_MAX_DEG = -30f
        const val SPEED_MIN_DP_S = 700f
        const val SPEED_MAX_DP_S = 1400f
        const val GRAVITY_DP_S2 = 1800f
        const val DRAG_PER_S = 2.2f
        const val FLUTTER_AMPLITUDE_DP = 12f
        const val FLUTTER_OMEGA_MIN = 6f
        const val FLUTTER_OMEGA_MAX = 12f
        const val FLIP_OMEGA_MIN = 8f
        const val FLIP_OMEGA_MAX = 16f
        const val SPIN_MAX_DEG_S = 540f
        const val LIFE_MIN_S = 1.4f
        const val LIFE_MAX_S = 2.2f
        const val SCALE_IN_FROM = 0.4f
        const val SCALE_IN_S = 0.08f
        const val FADE_OUT_S = 0.35f
        const val MAX_DT_S = 1f / 30f
    }

    /** Offsets relative to T0 = GameOver.AT_MS (D-064). */
    object GameOver {
        const val AT_MS = 300
        const val DESATURATE_MS = 600
        const val SATURATION_END = 0.2f
        const val DEFLATE_SCALE = 0.94f
        const val DEFLATE_STEP_MS = 30
        const val DEFLATE_WINDOW_MS = 180
        const val SCRIM_AT_MS = 450
        const val SCRIM_ALPHA = 0.78f
        const val SCRIM_MS = 300
        const val CARD_AT_MS = 500
        const val BUTTON_STAGGER_MS = 50
        const val INTERACTIVE_AT_MS = 700
    }

    object Card {
        const val EXIT_SCALE = 0.96f
        const val EXIT_SCRIM_MS = 200
        const val SATURATION_RESTORE_MS = 200
        const val REDUCED_FADE_MS = 150
    }

    object Board {
        const val UNDO_ICON_ROTATION_DEG = -360f
        const val UNDO_ICON_MS = 300
        const val NEW_GAME_STEP_MS = 15
        const val NEW_GAME_WINDOW_MS = 240
        const val NEW_GAME_SHRINK_SCALE = 0.6f
        const val NEW_GAME_SPAWN_GAP_MS = 60
    }

    object Nav {
        const val SHIFT_DP = 30f
        const val PREDICTIVE_MS = 300
        const val PREDICTIVE_SCALE = 0.90f
    }

    /**
     * Input rate limits in real time, never scaled (MOTION_SPEC.md §2.3 T4): not motion tokens,
     * kept here with every other millisecond value of the app (M3.1 addition to the §1.5 listing).
     */
    object Input {
        /** A key repeat (`repeatCount > 0`) acts at most once per 120 ms of event time (D-053). */
        const val KEY_REPEAT_MIN_INTERVAL_MS = 120L
        /** "Nothing to undo" shows at most once per 2 s (D-186). */
        const val NOTHING_TO_UNDO_MIN_INTERVAL_MS = 2000L
    }

    /**
     * The [MotionPlan] of [level] (ARCHITECTURE.md §5.6, MOTION_SPEC.md §5.1, D-183): exactly the
     * values its KDoc lists for each level. Returns one shared instance per level, so a plan held
     * in a `remember(level)` or passed as a parameter compares equal across recompositions.
     */
    fun plan(level: MotionLevel): MotionPlan =
        when (level) {
            MotionLevel.FULL -> MotionPlans.full
            MotionLevel.REDUCED -> MotionPlans.reduced
            MotionLevel.OFF -> MotionPlans.off
        }
}

/** How an invalid move shows (MOTION_SPEC.md §3.7, §5.1). */
enum class InvalidMoveMotion {
    /** Full: the board-level `NUDGE` spring along the swipe ([MotionPlan.nudge]). */
    NUDGE,

    /** Reduced: the 2 dp board outline pulse ([MotionPlan.outlinePulse]), at most 1 per 400 ms. */
    OUTLINE_PULSE,

    /** Off: no visual (the haptic still fires). */
    NONE,
}

/** How the "+N" float shows (MOTION_SPEC.md §3.4). */
enum class PlusNMotion {
    /** Full: created at +80, rises 28 dp, holds, fades and scales 0.8 → 1. */
    RISE,

    /** Reduced: created at the origin, fades in place over 400 ms. */
    FADE,

    /** Off: never composed. */
    NONE,
}

/** How the once-per-game new-best moment shows (MOTION_SPEC.md §3.6). */
enum class NewBestMotion {
    /** Full: at +80 colours crossfade, the label slides, the star and 8 sparkles run. */
    MOMENT,

    /** Reduced: at the origin the label swaps and the container colours crossfade. */
    CROSSFADE,

    /** Off: label and colours change on F0. */
    INSTANT,
}

/** How New game replaces the board (MOTION_SPEC.md §3.8, ARCHITECTURE.md §5.5). */
enum class NewGameMotion {
    /** Full: outgoing tiles shrink and fade with a stagger, then the start tiles spawn. */
    CASCADE,

    /** Reduced: outgoing alpha 1 → 0 and incoming alpha 0 → 1, both from the origin. */
    FADE,

    /** Off: swap on F0. */
    INSTANT,
}

/**
 * A spring for tile positions in cell units whose visibility threshold is given in px
 * (MOTION_SPEC.md §1.2): the threshold in cells is `thresholdPx / pitchPx` per axis, so it depends
 * on the board.
 */
@Immutable
data class CellSpring(val dampingRatio: Float, val stiffness: Float, val thresholdPx: Float) {
    /** The spec for a board whose pitch (tile + gap) is [pitchPx] pixels. */
    fun spec(pitchPx: Float): SpringSpec<Offset> {
        val t = thresholdPx / pitchPx
        return spring(dampingRatio, stiffness, Offset(t, t))
    }
}

/**
 * A spring for a pixel offset whose visibility threshold is given in dp (MOTION_SPEC.md §1.2): the
 * board nudge's 0.1 dp threshold in px per axis.
 */
@Immutable
data class DpSpring(val dampingRatio: Float, val stiffness: Float, val thresholdDp: Float) {
    /** The spec at [density] (px per dp). */
    fun spec(density: Float): SpringSpec<Offset> {
        val t = thresholdDp * density
        return spring(dampingRatio, stiffness, Offset(t, t))
    }
}

/**
 * The motion of one effective [MotionLevel] (ARCHITECTURE.md §5.6, MOTION_SPEC.md §1.4, §5.1;
 * D-063, D-183). Only [GameMotion.plan] builds it. Times are milliseconds from the origin
 * (MOTION_SPEC.md §0.1) before animator scaling; a delay before an animation goes inside its spec
 * (`delayedSpec`, `snap(delayMillis)`), and a state flip at an offset goes through
 * `MoveTimeline.awaitOffset` (D-183, MOTION_SPEC.md §2.3 T1, T2).
 *
 * A `null` spec means the element does not animate at this level: under Off every spec is `null`,
 * every delay is 0 and every start value equals its end value (F13-AC7, T5); under Reduced the KDoc
 * of each field says what replaces it. This holds the board and header rows of the §5.6 table (M3
 * part 1); M3.8 adds the overlay rows (win, game over, card exit).
 */
@Immutable
data class MotionPlan(
    val level: MotionLevel,
    /**
     * `Moved` and `Ghost` positions: `CellSpring(SLIDE_DAMPING, SLIDE_STIFFNESS,
     * POSITION_THRESHOLD_PX)` under Full; `null` (snap at F0) under Reduced and Off.
     */
    val slide: CellSpring?,
    /**
     * Ghost alpha 1 → 0: `snap(delayMillis = GHOST_REMOVE_MS)` under Full, `snap(delayMillis =
     * REDUCED_MERGE_CROSSFADE_MS)` under Reduced; `null` under Off (ghosts are composed at alpha
     * 0).
     */
    val ghostHide: FiniteAnimationSpec<Float>?,
    /**
     * Merged alpha 0 → 1: `snap(delayMillis = MERGE_REVEAL_MS)` under Full, `tween(120, easing =
     * LinearEasing)` from the origin under Reduced; `null` under Off.
     */
    val mergeReveal: FiniteAnimationSpec<Float>?,
    /**
     * The merged tile's pop from scale 1 with [popImpulse] as initial velocity, played as
     * `delayedSpec(pop, mergeRevealMs)`: `popSpatial(SCALE_THRESHOLD)` under Full; `null`
     * otherwise.
     */
    val pop: SpringSpec<Float>?,
    /**
     * The merge reveal offset, also the start of the pop, sheen and glow pulse: 80 Full; else 0.
     */
    val mergeRevealMs: Int,
    /**
     * Sheen 0.35 → 0 on merged exponents ≥ 7, within the flash limit:
     * `fastEffects(ALPHA_THRESHOLD)` under Full; `null` otherwise.
     */
    val sheen: SpringSpec<Float>?,
    /**
     * Glow boost 0.25 → 0 on merged exponents ≥ 9 whose palette has a glow, within the flash limit:
     * `defaultEffects(ALPHA_THRESHOLD)` under Full; `null` otherwise.
     */
    val glowPulse: SpringSpec<Float>?,
    /** A `Merged` tile's alpha on F0: 0 under Full and Reduced, 1 under Off. */
    val mergedStartAlpha: Float,
    /** A `Spawned` tile's scale on F0: `0.6` under Full, 1 otherwise. */
    val spawnStartScale: Float,
    /** A `Spawned` tile's alpha on F0: 0 under Full and Reduced, 1 under Off. */
    val spawnStartAlpha: Float,
    /**
     * Spawn scale 0.6 → 1, played as `delayedSpec(spawnScale, spawnDelayMs(…))`:
     * `popSpatial(SCALE_THRESHOLD)` under Full; `null` otherwise.
     */
    val spawnScale: SpringSpec<Float>?,
    /**
     * Spawn alpha 0 → 1, played as `delayedSpec(spawnAlpha, spawnDelayMs(…))`:
     * `fastEffects(ALPHA_THRESHOLD)` under Full and Reduced (Reduced: no delay); `null` under Off.
     */
    val spawnAlpha: SpringSpec<Float>?,
    /** NUDGE (Full), OUTLINE_PULSE (Reduced) or NONE (Off). */
    val invalidMove: InvalidMoveMotion,
    /**
     * The board nudge: `DpSpring(NUDGE_DAMPING, NUDGE_STIFFNESS, NUDGE_THRESHOLD_DP)` with initial
     * velocity `NUDGE_VELOCITY_DP_S` along the swipe, under Full; `null` otherwise.
     */
    val nudge: DpSpring?,
    /**
     * The outline pulse alpha: `keyframes { durationMillis = 300; 0f at 0; 0.6f at 100; 0f at 300
     * }` (linear) under Reduced; `null` otherwise.
     */
    val outlinePulse: FiniteAnimationSpec<Float>?,
    /**
     * Undo's changed-cell crossfade, outgoing 1 → 0 and incoming 0 → 1 from the origin:
     * `fastEffects(ALPHA_THRESHOLD)` under Full and Reduced; `null` under Off (D-183).
     */
    val undoCrossfade: SpringSpec<Float>?,
    /** The Undo icon's −360° turn: `tween(300, easing = EmphasizedDecelerate)` under Full only. */
    val undoIconSpin: FiniteAnimationSpec<Float>?,
    /** CASCADE (Full), FADE (Reduced) or INSTANT (Off). */
    val newGame: NewGameMotion,
    /**
     * New game's outgoing alpha (and, under CASCADE, scale 1 → 0.6, each `delayedSpec(…, i·step)`)
     * and, under FADE, the incoming alpha: `fastEffects(ALPHA_THRESHOLD)` under Full and Reduced;
     * `null` under Off. Incoming tiles under CASCADE use the spawn specs at `n·step + 60·k`.
     */
    val newGameFade: SpringSpec<Float>?,
    /**
     * The size switch's `AnimatedContent` fade-in: `defaultEffects(ALPHA_THRESHOLD)` under Full and
     * Reduced; `null` (swap) under Off.
     */
    val sizeSwitchIn: FiniteAnimationSpec<Float>?,
    /** Its fade-out: `fastEffects(ALPHA_THRESHOLD)` under Full and Reduced; `null` under Off. */
    val sizeSwitchOut: FiniteAnimationSpec<Float>?,
    /** RISE (Full), FADE (Reduced) or NONE (Off). */
    val plusN: PlusNMotion,
    /**
     * The offset at which a "+N" float is created (mechanism B): 80 under Full, 0 under Reduced, 0
     * (unused) under Off.
     */
    val plusNAtMs: Int,
    /** RISE: translationY 0 → −28 dp, `tween(650, easing = EmphasizedDecelerate)`; else `null`. */
    val plusNRise: FiniteAnimationSpec<Float>?,
    /**
     * RISE: `keyframes { durationMillis = 650; 1f at 0; 1f at 250; 0f at 650 }`; FADE: `tween(400,
     * easing = LinearEasing)`; NONE: `null`.
     */
    val plusNAlpha: FiniteAnimationSpec<Float>?,
    /** RISE: scale 0.8 → 1, `popSpatial(SCALE_THRESHOLD)`; else `null`. */
    val plusNScale: SpringSpec<Float>?,
    /**
     * The offset of the displayed-score update after an accepted move (mechanism B): 80 under Full;
     * `null` under Reduced and Off, where the displayed score follows the true score on F0.
     */
    val scoreAtMs: Int?,
    /** The odometer digit slide: `slide(IntOffset(1, 1))` under Full; `null` (one plain `Text`). */
    val odometerSlide: SpringSpec<IntOffset>?,
    /** The odometer digit fades: `fastEffects(ALPHA_THRESHOLD)` under Full; `null` otherwise. */
    val odometerFade: SpringSpec<Float>?,
    /**
     * A new or removed leading digit: `overlaySpatial(IntSize(1, 1))` expand or shrink under Full;
     * `null` otherwise.
     */
    val odometerResize: SpringSpec<IntSize>?,
    /** MOMENT (Full), CROSSFADE (Reduced) or INSTANT (Off). */
    val newBest: NewBestMotion,
    /** The offset at which the new-best targets flip (mechanism B): 80 under Full, else 0. */
    val newBestAtMs: Int,
    /**
     * The BEST chip's container, label and value colours: `defaultEffects()` under Full and
     * Reduced; `null` (instant) under Off.
     */
    val newBestColor: SpringSpec<Color>?,
) {
    /**
     * The pop impulse in s⁻¹ for a merged tile of exponent [exp] (MOTION_SPEC.md §3.3, D-183): the
     * tier of [GameMotion.POP_IMPULSE] by the tile's own value (≤ 64, 128–512, 1024, ≥ 2048),
     * capped at `(2 · gapPx / tilePx) / POP_PEAK_PER_VELOCITY`; 0 unless Full.
     */
    fun popImpulse(exp: Int, gapPx: Float, tilePx: Float): Float {
        if (level != MotionLevel.FULL) return 0f
        val tiers = GameMotion.POP_IMPULSE
        val tier =
            when {
                exp <= 6 -> tiers[0] // ≤ 64
                exp <= 9 -> tiers[1] // 128–512
                exp == 10 -> tiers[2] // 1024
                else -> tiers[3] // ≥ 2048
            }
        val cap = 2f * gapPx / tilePx / GameMotion.Move.POP_PEAK_PER_VELOCITY
        return if (tilePx > 0f && cap < tier) cap else tier
    }

    /**
     * The spawn delay for a move whose longest slide is [maxSlideCells] cells (D-183): 90 for ≤ 3,
     * then 100, 105, 110, 115 for 4–7, under Full; 0 otherwise.
     */
    fun spawnDelayMs(maxSlideCells: Int): Int {
        val long = GameMotion.Move.SPAWN_DELAY_LONG_MS
        return when {
            level != MotionLevel.FULL -> 0
            maxSlideCells <= 3 -> GameMotion.SPAWN_DELAY_MS
            else -> long[(maxSlideCells - 4).coerceAtMost(long.lastIndex)]
        }
    }

    /**
     * New game's stagger per outgoing tile for [outgoing] ≥ 1 tiles (MOTION_SPEC.md §3.8):
     * `min(NEW_GAME_STEP_MS, NEW_GAME_WINDOW_MS / outgoing)` under Full; 0 otherwise.
     */
    fun newGameStepMs(outgoing: Int): Int =
        if (level == MotionLevel.FULL) {
            minOf(
                GameMotion.Board.NEW_GAME_STEP_MS,
                GameMotion.Board.NEW_GAME_WINDOW_MS / outgoing.coerceAtLeast(1),
            )
        } else {
            0
        }
}

/**
 * The three shared plans that [GameMotion.plan] returns (ARCHITECTURE.md §5.6, MOTION_SPEC.md §1.4,
 * §5.1), each filled exactly as the [MotionPlan] field KDoc states for its level.
 */
private object MotionPlans {
    /** A `Spawned` tile's scale on F0 under Full (MOTION_SPEC.md §3.2). */
    private const val SPAWN_START_SCALE = 0.6f

    private val alphaFast: SpringSpec<Float> = GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD)
    private val alphaDefault: SpringSpec<Float> =
        GameMotion.defaultEffects(GameMotion.ALPHA_THRESHOLD)
    private val scalePop: SpringSpec<Float> = GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD)
    private val intOffsetThreshold =
        IntOffset(GameMotion.INT_OFFSET_THRESHOLD_PX, GameMotion.INT_OFFSET_THRESHOLD_PX)
    private val intSizeThreshold =
        IntSize(GameMotion.INT_OFFSET_THRESHOLD_PX, GameMotion.INT_OFFSET_THRESHOLD_PX)

    val full: MotionPlan =
        MotionPlan(
            level = MotionLevel.FULL,
            slide =
                CellSpring(
                    GameMotion.SLIDE_DAMPING,
                    GameMotion.SLIDE_STIFFNESS,
                    GameMotion.POSITION_THRESHOLD_PX,
                ),
            ghostHide = snap(delayMillis = GameMotion.GHOST_REMOVE_MS),
            mergeReveal = snap(delayMillis = GameMotion.MERGE_REVEAL_MS),
            pop = scalePop,
            mergeRevealMs = GameMotion.MERGE_REVEAL_MS,
            sheen = alphaFast,
            glowPulse = alphaDefault,
            mergedStartAlpha = 0f,
            spawnStartScale = SPAWN_START_SCALE,
            spawnStartAlpha = 0f,
            spawnScale = scalePop,
            spawnAlpha = alphaFast,
            invalidMove = InvalidMoveMotion.NUDGE,
            nudge =
                DpSpring(
                    GameMotion.NUDGE_DAMPING,
                    GameMotion.NUDGE_STIFFNESS,
                    GameMotion.NUDGE_THRESHOLD_DP,
                ),
            outlinePulse = null,
            undoCrossfade = alphaFast,
            undoIconSpin =
                tween(GameMotion.Board.UNDO_ICON_MS, easing = GameMotion.EmphasizedDecelerate),
            newGame = NewGameMotion.CASCADE,
            newGameFade = alphaFast,
            sizeSwitchIn = alphaDefault,
            sizeSwitchOut = alphaFast,
            plusN = PlusNMotion.RISE,
            plusNAtMs = GameMotion.Score.UPDATE_AT_MS,
            plusNRise =
                tween(GameMotion.Score.PLUS_N_RISE_MS, easing = GameMotion.EmphasizedDecelerate),
            plusNAlpha =
                keyframes {
                    durationMillis =
                        GameMotion.Score.PLUS_N_HOLD_MS + GameMotion.Score.PLUS_N_FADE_MS
                    1f at 0
                    1f at GameMotion.Score.PLUS_N_HOLD_MS
                    0f at GameMotion.Score.PLUS_N_HOLD_MS + GameMotion.Score.PLUS_N_FADE_MS
                },
            plusNScale = scalePop,
            scoreAtMs = GameMotion.Score.UPDATE_AT_MS,
            odometerSlide = GameMotion.slide(intOffsetThreshold),
            odometerFade = alphaFast,
            odometerResize = GameMotion.overlaySpatial(intSizeThreshold),
            newBest = NewBestMotion.MOMENT,
            newBestAtMs = GameMotion.NewBest.AT_MS,
            newBestColor = GameMotion.defaultEffects(),
        )

    val reduced: MotionPlan =
        full.copy(
            level = MotionLevel.REDUCED,
            slide = null,
            ghostHide = snap(delayMillis = GameMotion.Move.REDUCED_MERGE_CROSSFADE_MS),
            mergeReveal = tween(GameMotion.Move.REDUCED_MERGE_CROSSFADE_MS, easing = LinearEasing),
            pop = null,
            mergeRevealMs = 0,
            sheen = null,
            glowPulse = null,
            spawnStartScale = 1f,
            spawnScale = null,
            invalidMove = InvalidMoveMotion.OUTLINE_PULSE,
            nudge = null,
            outlinePulse =
                keyframes {
                    durationMillis = GameMotion.Nudge.PULSE_MS
                    0f at 0
                    GameMotion.Nudge.PULSE_PEAK_ALPHA at GameMotion.Nudge.PULSE_PEAK_MS
                    0f at GameMotion.Nudge.PULSE_MS
                },
            undoIconSpin = null,
            newGame = NewGameMotion.FADE,
            plusN = PlusNMotion.FADE,
            plusNAtMs = 0,
            plusNRise = null,
            plusNAlpha = tween(GameMotion.Score.REDUCED_PLUS_N_FADE_MS, easing = LinearEasing),
            plusNScale = null,
            scoreAtMs = null,
            odometerSlide = null,
            odometerFade = null,
            odometerResize = null,
            newBest = NewBestMotion.CROSSFADE,
            newBestAtMs = 0,
        )

    val off: MotionPlan =
        MotionPlan(
            level = MotionLevel.OFF,
            slide = null,
            ghostHide = null,
            mergeReveal = null,
            pop = null,
            mergeRevealMs = 0,
            sheen = null,
            glowPulse = null,
            mergedStartAlpha = 1f,
            spawnStartScale = 1f,
            spawnStartAlpha = 1f,
            spawnScale = null,
            spawnAlpha = null,
            invalidMove = InvalidMoveMotion.NONE,
            nudge = null,
            outlinePulse = null,
            undoCrossfade = null,
            undoIconSpin = null,
            newGame = NewGameMotion.INSTANT,
            newGameFade = null,
            sizeSwitchIn = null,
            sizeSwitchOut = null,
            plusN = PlusNMotion.NONE,
            plusNAtMs = 0,
            plusNRise = null,
            plusNAlpha = null,
            plusNScale = null,
            scoreAtMs = null,
            odometerSlide = null,
            odometerFade = null,
            odometerResize = null,
            newBest = NewBestMotion.INSTANT,
            newBestAtMs = 0,
            newBestColor = null,
        )
}
