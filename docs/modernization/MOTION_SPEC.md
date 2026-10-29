# MOTION_SPEC.md: motion, haptics and motion verification for the 2048 native rewrite

Status: plan document, dated 2026-10-28. It implements the motion and haptics decisions of `DECISIONS.md`: D-060–D-066, plus the motion parts of D-008, D-025, D-047, D-052, D-054, D-093–D-095, D-112, D-147 and D-150, and the entries added during planning (D-182–D-185, D-187, D-200, D-204, D-210 and technical correction TC-7). `DECISIONS.md` is canonical. Where this file interprets or extends a decision, it cites the entry that records it.

Audience: the implementer agents (D-194) executing milestone **M3** (board, input, motion, haptics; two pushed parts, D-211) and the motion parts of **M4** (screens, theming, layout). Every number here is meant to be typed into code or into a test assertion as written.

Sibling documents:
- `DESIGN_SYSTEM.md` owns colors, geometry, components and the golden list. This file references its draw order (§4.7), mode transitions (§5.8), cards (§6.3, §6.4), `ScoreChip` (§7.2), sheets (§7.4) and goldens (§10).
- `research/04-design-motion.md` §3.1, §4, §5, §6 and §10 is the evidence behind most values here.

---

## Contents

- [0. Conventions](#0-conventions)
- [1. Motion tokens (`GameMotion`)](#1-motion-tokens-gamemotion)
- [2. Motion level, animator scale and timing infrastructure](#2-motion-level-animator-scale-and-timing-infrastructure)
- [3. Per-event timelines](#3-per-event-timelines)
- [4. Interruption semantics and visual-consistency invariants](#4-interruption-semantics-and-visual-consistency-invariants)
- [5. Reduced and Off motion; system animator scale](#5-reduced-and-off-motion-system-animator-scale)
- [6. Haptics](#6-haptics)
- [7. Performance budgets and measurement](#7-performance-budgets-and-measurement)
- [8. Verifying motion without a device](#8-verifying-motion-without-a-device)
- [9. Acceptance criteria](#9-acceptance-criteria)
- [10. Implementation file map](#10-implementation-file-map)
- [Open issues](#open-issues)

---

## 0. Conventions

### 0.1 Time origin

- **Commit** is the input callback in which `GameViewModel` applies an accepted move, undo or new game synchronously (D-052).
- **F0** is the first frame that composes the new `BoardUi`. Effects launched during F0 call `withFrameNanos`, so their first animation frame is **F1 = F0 + one frame**.
- **Origin (t = 0)** is **F1**: the frame on which every animation of that board change has play time 0. All timeline numbers in this file are milliseconds from the origin. Where `DECISIONS.md` says "from commit" (D-061, D-064, D-065), it means "from the origin" (D-183). The difference is at most one frame.
- The origin is published once per board change by `MoveTimeline` (§2.4). Every other consumer (score chip, haptics, overlays, effects) measures its offsets from that published value, so separate state flows cannot drift apart.
- An offset is due on **the first frame at or after** origin + offset × scale. On the 16 ms test frame grid (`TestMonotonicFrameClock.DefaultFrameDelay` = 16 ms, verified 2026-10-28), an offset of 90 ms is due at 96 ms. §9 states every assertion on that grid.

### 0.2 Levels and words

- **Motion level** means the effective level `MotionLevel.FULL`, `REDUCED` or `OFF` from §2.1. "Full", "Reduced" and "Off" below are those levels.
- **Snap** means `Animatable.snapTo(end)`. It cancels a running animation on the same `Animatable`.
- **Settle** means the frame on which Compose ends the animation and snaps the value to its target. That frame comes from Compose's own duration estimate (`SpringEstimation`). Tables in §1 give the estimate. `MotionTokensTest` checks that Compose's real estimator lands within one 16 ms frame of them (§8.5, D-210).
- **Cells** are the board's coordinate unit: x = column, y = row, as `Float`. A tile's pixel position is `geometry.cellTopLeft(x, y)` with `fun cellTopLeft(x: Float, y: Float): IntOffset` (`game/board/BoardGeometry.kt`, `DESIGN_SYSTEM.md` §4.2), computed in the layout phase from the current geometry and rounded to whole pixels (D-075).

### 0.3 Evidence markers

- **Computed 2026-10-28** marks spring and easing numbers computed for this file: the closed-form solution of a unit-mass damped spring (Compose's `SpringSimulation` uses the same model), the androidx duration estimator re-implemented (envelope bound when damping < 1, exact root when damping = 1), and cubic-Bézier easing solved numerically. The research/04 §3.1 table was measured with a different numeric method. The small differences are listed under §1.1; the one that changes a decided number is technical correction TC-7.
- **Verified 2026-10-28 (class files)** marks API facts read with `javap` from the Gradle-cached artifacts of this project's catalog versions: `ui-android` 1.12.1, `animation-core-android` 1.12.1, `ui-test(-junit4)-android` 1.12.1, `material3-android` 1.5.0-beta02, `navigation3-ui-android` 1.2.0, `navigationevent-android` 1.1.1, `core` 1.19.1 and `foundation-android` 1.12.1.

---

## 1. Motion tokens (`GameMotion`)

`ui/theme/GameMotion.kt` holds every motion number in the app (D-060). Game code never reads `MaterialTheme.motionScheme` (D-026). Detekt's `MagicNumber` relaxation covers only `ui/theme/` (D-144, `DESIGN_SYSTEM.md` §0), so **no millisecond, dp or stiffness literal appears outside this file**.

### 1.1 Springs

Unit mass. "Overshoot" is the analytic first overshoot from rest, `exp(−ζπ/√(1−ζ²))`. Settle values are Compose's duration estimate for the stated displacement and visibility threshold (computed 2026-10-28).

| Token | Source token (research/04 §3.1) | ζ / k | Overshoot | Settle (Compose estimate) | Used for |
|---|---|---|---|---|---|
| `slide` | Standard fastSpatial | 0.9 / 1400 | 0.15 % | 208 ms (241 px, 1 cell on Pixel 7 4×4) · 229 ms (482 px) · 241 ms (724 px, 3 cells) · 260 ms (1400 px) · 271 ms (2000 px); threshold 0.5 px | Tile slides; odometer digit slide; card exit scale; label shifts |
| `popSpatial` | Expressive fastSpatial | 0.6 / 800 | 9.48 % | Pop from scale 1 with v0 = 6/8/10/12 s⁻¹: 288/305/318/329 ms (threshold 0.002). Spawn 0.6 → 1: 325 ms. Badge 0 → 1: 379 ms | Merge pop; spawn scale; "+N" scale; new-best star; win badge scale |
| `overlaySpatial` | Expressive defaultSpatial | 0.8 / 380 | 1.52 % | Card 0.92 → 1: 269 ms (0.002). Morph 0 → 1: 476 ms (0.001). 300 px bounds: 399 ms (1 px) | Overlay card scale; win badge morph; `animateBounds`; hint card exit; odometer leading digit |
| `slowSpatial` | Expressive slowSpatial | 0.8 / 200 | 1.52 % | — | **Reserved.** Kept because D-060 lists it; no v1.0 call site |
| `standardSpatial` (D-183) | Standard defaultSpatial | 0.9 / 700 | 0.15 % | Deflate 1 → 0.94: 178 ms (0.002). 30 dp nav shift at 2.625×: 218 ms (1 px) | Game-over deflate ripple (research/04 §10.10); screen-transition slide |
| `fastEffects` | fastEffects | 1.0 / 3800 | 0 | 0 → 1: 125 ms (1/255) · 108 ms (0.01) · 137 ms (0.002) | Alpha: spawn, card, buttons, undo crossfade, ghosts, sheen, odometer fades |
| `defaultEffects` | defaultEffects | 1.0 / 1600 | 0 | 0 → 1: 193 ms (1/255) · 166 ms (0.01) · 212 ms (0.002) | Glow pulse; BEST container color; nav and size-switch fade-in |
| `slowEffects` | slowEffects | 1.0 / 800 | 0 | 0 → 1: 272 ms (1/255) · 235 ms (0.01) · 299 ms (0.002) | Theme/palette color crossfade |
| `nudge` (D-060 `NUDGE`) | game-specific | 0.3 / 1800 | — | v0 = 330 dp/s from rest at 0: 346 ms (threshold 0.1 dp; TC-7) | Invalid-move board nudge |

- `standardSpatial` is D-183's addition: D-064 adopts research/04 §10.10, whose deflate uses "std defaultSpatial (0.9/700)". The thresholds of §1.2 and the tween constants of §1.5 are D-183's additions too.
- **Differences from research/04 §3.1** (computed 2026-10-28). Analytic overshoots are 9.48 % (0.6/800), 1.52 % (0.8/380) and 0.15 % (0.9/x); research/04 lists 9.3 %, 1.4 % and 0.1 % from a time-stepped simulation. Peaks and extrema agree within 0.003. The nudge settles at 346 ms under Compose's estimator with a 0.1 dp threshold; research/04 §10.7 says 393 ms, which used an extra velocity criterion (TC-7; the spring itself is unchanged). Tests use Compose's estimator (`MotionTokensTest`, D-210).

Spring progress reference (fraction of the path covered, from rest; computed 2026-10-28). Tests in §9 use these values.

| t (ms) | 16 | 32 | 48 | 64 | 80 | 96 | 112 | 128 | 160 | 192 |
|---|---|---|---|---|---|---|---|---|---|---|
| `slide` progress | 0.1255 | 0.3545 | 0.5700 | 0.7349 | 0.8475 | 0.9184 | 0.9599 | 0.9825 | 0.9992 | 1.0015 |
| `fastEffects` 0 → 1 | 0.2592 | 0.5865 | 0.7946 | 0.9043 | 0.9572 | 0.9814 | 0.9921 | 0.9967 | 0.9994 | 1.0000 |
| `defaultEffects` 0 → 1 | 0.1352 | 0.3661 | 0.5719 | 0.7248 | 0.8288 | 0.8960 | 0.9379 | 0.9634 | 0.9877 | — |
| `slowEffects` 0 → 1 | 0.0762 | 0.2294 | 0.3935 | 0.5402 | 0.6605 | 0.7541 | 0.8246 | 0.8763 | 0.9402 | — |

### 1.2 Visibility thresholds

Every spring passes its threshold explicitly. Compose's `spring()` without a threshold uses 0.01 per dimension, which for pixel positions would mean 0.01 px and several extra frames of invisible motion.

| Constant | Value | Applies to |
|---|---|---|
| `POSITION_THRESHOLD_PX` | 0.5 px, converted to cells as `0.5f / pitchPx` per axis | Tile position `Animatable<Offset>` (cells) |
| `SCALE_THRESHOLD` | 0.002 | Tile, card, badge, star and "+N" scale |
| `ALPHA_THRESHOLD` | 1/255 | Every alpha |
| `FRACTION_THRESHOLD` | 0.002 | Color-crossfade fractions |
| `MORPH_THRESHOLD` | 0.001 | Win badge morph progress |
| `NUDGE_THRESHOLD_DP` | 0.1 dp, converted to px | Board nudge |
| `INT_OFFSET_THRESHOLD_PX` | 1 px | `IntOffset`/`IntSize` transitions (odometer, nav, `animateBounds`) |

### 1.3 Tweens and keyframes

Easings are the M3 tokens (research/04 §3.1, `tokens/MotionTokens.kt`): **Standard** `(0.2, 0, 0, 1)`, **EmphasizedDecelerate** `(0.05, 0.7, 0.1, 1)`, **EmphasizedAccelerate** `(0.3, 0, 0.8, 0.15)`. `FastOutSlowInEasing` `(0.4, 0, 0.2, 1)` and `LinearEasing` come from Compose. Tweens are used only for one-shot effects that must line up with something else (research/04 §4.3).

| Name | From → to | Duration | Easing | Start (Full) | Section |
|---|---|---|---|---|---|
| "+N" rise | translationY 0 → −28 dp | 650 ms | EmphasizedDecelerate | frame after ⌈80⌉ | §3.4 |
| "+N" alpha | 1 (hold 250 ms) → 0 over 400 ms | 650 ms keyframes | Linear | frame after ⌈80⌉ | §3.4 |
| Reduced "+N" fade | alpha 1 → 0 | 400 ms | Linear | frame after the origin | §3.4 |
| Reduced merge crossfade | merged alpha 0 → 1 | 120 ms | Linear | +0 | §3.3 |
| Reduced invalid pulse | outline alpha 0 → 0.6 (at 100 ms) → 0 | 300 ms keyframes | Linear | +0 | §3.7 |
| New-best star rotation | 0° → 45° | 900 ms | EmphasizedDecelerate | S (frame after ⌈80⌉) | §3.6 |
| New-best star fade | alpha 1 → 0 | 300 ms | FastOutSlowIn | S + 1200 ms | §3.6 |
| Win glow ring | scale 1 → 1.8, stroke 6 dp → 0, alpha 0.9 → 0 | 700 ms | EmphasizedDecelerate | T0 + 60 = +140 ms | §3.11 |
| Win scrim | 0 → 0.60 | 300 ms | Standard | T0 + 600 = +680 ms | §3.11 |
| Win badge rotation | 0° → 60°, then stops | 5000 ms | Linear | O (frame after ⌈680⌉) | §3.11 |
| Game-over desaturation | saturation 1.0 → 0.2 | 600 ms | Standard | T0 = +300 ms | §3.13 |
| Game-over scrim | 0 → 0.78 | 300 ms | Standard | T0 + 450 = +750 ms | §3.13 |
| Card-exit scrim | current → 0 | 200 ms | EmphasizedAccelerate | Ft (§3.12) | §3.12 |
| Saturation restore | current → 1.0 | 200 ms | Standard | Ft, or the undo's origin | §3.12 |
| Reduced overlay fade | scrim and saturation (A); card alpha (B) | 150 ms | Linear | +0; the card from the next frame | §3.11, §3.13 |
| Reduced card exit | alpha and scrim → 0 | 150 ms | Linear | Ft | §3.12 |
| Undo icon | rotation −360° (cumulative) | 300 ms | EmphasizedDecelerate | on undo | §3.9 |
| Predictive back | scale, shift, fade-in (gesture-seeked) | 300 ms | Linear (fade-out: EmphasizedAccelerate) | gesture | §3.19 |

### 1.4 Level variants of every token

What each token becomes at each level (D-063; research/04 §6.2). "Snap" means the end value is set on the first frame, and "—" means the effect does not run.

| Token / effect | Full | Reduced | Off |
|---|---|---|---|
| `slide` (tiles) | spring | snap | snap |
| `popSpatial` (merge pop) | spring with `POP_IMPULSE` | — (replaced by the 120 ms merge crossfade) | — |
| `popSpatial` (spawn scale) | 0.6 → 1 | — (scale 1) | — |
| `popSpatial` ("+N" scale, star, badge scale) | spring | — | — |
| `overlaySpatial` (card scale, morph, bounds, hint exit) | spring | — (card fades only; morph shown complete; bounds snap) | — |
| `standardSpatial` (deflate, nav shift) | spring | — (tiles snap to 0.94; nav fades only) | — |
| `fastEffects` (alpha) | spring | **kept** for the spawn alpha, the undo crossfade, outgoing new-game tiles and the size-switch fade-out | snap |
| `defaultEffects` (glow pulse, BEST container, nav fade-in, size switch) | spring | kept for BEST container, nav fade-in and size switch; glow pulse — | snap |
| `slowEffects` (theme crossfade) | spring | snap (`DESIGN_SYSTEM.md` §5.8) | snap |
| `nudge` | spring | — (replaced by the outline pulse) | — |
| Tweens in §1.3 | as listed | only the "Reduced" rows, plus the predictive-back fades (§3.19) | none run |
| Confetti, sparkles, ring, sheen, glow pulse, badge rotation | run | — | — |

### 1.5 `GameMotion.kt` (complete listing)

```kotlin
// app/src/main/kotlin/com/clayboicardi/game2048/ui/theme/GameMotion.kt
package com.clayboicardi.game2048.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Effective motion level (D-063): OFF when the system animator scale is 0, else the Motion setting. */
enum class MotionLevel { FULL, REDUCED, OFF }

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

    fun <T> slide(threshold: T? = null): SpringSpec<T> = spring(SLIDE_DAMPING, SLIDE_STIFFNESS, threshold)
    fun <T> popSpatial(threshold: T? = null): SpringSpec<T> = spring(POP_DAMPING, POP_STIFFNESS, threshold)
    fun <T> overlaySpatial(threshold: T? = null): SpringSpec<T> = spring(OVERLAY_DAMPING, OVERLAY_STIFFNESS, threshold)
    fun <T> slowSpatial(threshold: T? = null): SpringSpec<T> = spring(SLOW_DAMPING, SLOW_STIFFNESS, threshold)
    fun <T> standardSpatial(threshold: T? = null): SpringSpec<T> = spring(STANDARD_DAMPING, STANDARD_STIFFNESS, threshold)
    fun <T> fastEffects(threshold: T? = null): SpringSpec<T> = spring(EFFECTS_DAMPING, FAST_EFFECTS_STIFFNESS, threshold)
    fun <T> defaultEffects(threshold: T? = null): SpringSpec<T> = spring(EFFECTS_DAMPING, DEFAULT_EFFECTS_STIFFNESS, threshold)
    fun <T> slowEffects(threshold: T? = null): SpringSpec<T> = spring(EFFECTS_DAMPING, SLOW_EFFECTS_STIFFNESS, threshold)
    /** The D-060 NUDGE token, as a function so the 0.1 dp threshold (in px) can be passed. */
    fun <T> nudge(threshold: T? = null): SpringSpec<T> = spring(NUDGE_DAMPING, NUDGE_STIFFNESS, threshold)

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
}
```

The M3 part 1 contracts append `object GameMotion.Input` (`KEY_REPEAT_MIN_INTERVAL_MS = 120L`, D-053; `NOTHING_TO_UNDO_MIN_INTERVAL_MS = 2000L`, D-186) and `GameMotion.plan(level)` with `MotionPlan` to this listing (D-222). `NewBest.SPARKLE_JITTER_DEG` is the ±10° direction jitter of §3.6, named at the M3 part 1 integration so the particle engine carries no literal.

---

## 2. Motion level, animator scale and timing infrastructure

### 2.1 Effective motion level

```kotlin
// ui/theme/EffectiveMotion.kt
interface AnimatorScaleSource { val scale: StateFlow<Float> }

/** Reads Settings.Global.ANIMATOR_DURATION_SCALE (default 1f) and observes it with a ContentObserver. */
class SystemAnimatorScaleSource(context: Context, scope: CoroutineScope) : AnimatorScaleSource

fun effectiveMotion(setting: MotionPreference, systemScale: Float): MotionLevel =
    if (systemScale == 0f) MotionLevel.OFF
    else when (setting) { MotionPreference.FULL -> MotionLevel.FULL; MotionPreference.REDUCED -> MotionLevel.REDUCED; MotionPreference.OFF -> MotionLevel.OFF }

val LocalEffectiveMotion: ProvidableCompositionLocal<MotionLevel> = compositionLocalOf { MotionLevel.FULL }
```

- `AppContainer` (D-022) owns one `SystemAnimatorScaleSource`. Tests inject a fake.
- `MotionPreference` is the persisted Motion setting (D-042). `MotionLevel` is declared in `ui/theme/GameMotion.kt` (§1.5).
- `GameTheme` (not `App()`) provides `LocalEffectiveMotion` from `effectiveMotion(settings.motion, scale)`, together with `gameMotionScheme(level)` (§2.2).
- The Settings row "Motion" is disabled with the summary `motion_off_system` while the scale is 0 (D-006, `DESIGN_SYSTEM.md` §6.8).
- **Battery Saver.** `ValueAnimator.areAnimatorsEnabled()` can be false under Battery Saver while `ANIMATOR_DURATION_SCALE` is non-zero. D-063 and Compose's `MotionDurationScale` read only the setting, so v1.0 follows D-063 and does not treat Battery Saver as Off.
- **Level changes mid-animation.** Running animations finish under the level they started with. The next event uses the new level. The exception is a system scale change to 0: Compose's `MotionDurationScale` makes running animations jump to their end on the next frame, and the custom loops of §2.5 end on the next frame.

### 2.2 Material 3 components follow the level through `MotionScheme`

Verified 2026-10-28 (class files): `androidx.compose.material3.MotionScheme` is a public `@Immutable interface` with six methods (`defaultSpatialSpec`, `fastSpatialSpec`, `slowSpatialSpec`, `defaultEffectsSpec`, `fastEffectsSpec`, `slowEffectsSpec`) and no opt-in marker. In 1.5.0-beta02, `BottomSheet` sets the sheet's show motion from `MaterialTheme.motionScheme.defaultSpatialSpec()`, its hide motion from `fastEffectsSpec()`, and its drag-settle motion from `defaultSpatialSpec()`. `ModalBottomSheet`'s scrim reads the DefaultEffects token.

`GameTheme` therefore passes a level-dependent scheme to `MaterialExpressiveTheme` (D-184, amending D-026's fixed `MotionScheme.expressive()`):

```kotlin
// ui/theme/GameMotionScheme.kt
private object ReducedMotionScheme : MotionScheme {
    private val expressive = MotionScheme.expressive()
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = expressive.defaultEffectsSpec()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = expressive.fastEffectsSpec()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = expressive.slowEffectsSpec()
}
private object OffMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
fun gameMotionScheme(level: MotionLevel): MotionScheme = when (level) {
    MotionLevel.FULL -> MotionScheme.expressive()
    MotionLevel.REDUCED -> ReducedMotionScheme
    MotionLevel.OFF -> OffMotionScheme
}
```

Consequence under Reduced: a sheet appears without sliding, and it still hides with the `fastEffects` spring (about 170 ms, no overshoot), because material3 uses an effects spec for the hide offset.

### 2.3 Timing rules

| # | Rule |
|---|---|
| T1 | **No raw `delay()` for anything visible** (D-183). A delay that precedes an animation is part of its spec: `tween(…, delayMillis = d)`, `snap(delayMillis = d)` or `delayedSpec(spec, d)`. Compose scales all three by `MotionDurationScale`, starts them on the same frame as the slides, and jumps them to the end at scale 0. A delay before a spring uses the app-local `delayedSpec(spec, delayMs)` in `game/motion/DelayedSpec.kt`: it wraps a `FiniteAnimationSpec`, holds the initial value and velocity for `delayMs`, then plays the wrapped spec with play time − delay; its duration is delay + the wrapped duration. animation-core's own `delayed` spec function is Kotlin-internal (its bytecode looks public) and must not be used. |
| T2 | **State flips at an offset** (score display update, "+N" creation, new-best start, overlay composition, card-button interactivity (D-200), confetti emission, haptics) use `MoveTimeline.awaitOffset(moveId, ms)` (§2.4). It waits on the frame clock, so the flip lands on the first frame ⌈X⌉ at or after origin + X·scale, never between frames. Two mechanisms follow, and §3 names the one each element uses: **(A) spec-delayed** animations are launched at the origin with the delay inside the spec, so at frame t their play time is exactly t − X. **(B) flip-started** elements appear on frame ⌈X⌉ at their initial values, and any animation they start has play time 0 on the *following* frame (⌈X⌉ + 16 ms on the test grid). A (B) flip that is drawn writes its state inside the due frame's callback, through `awaitOffset(moveId, ms) { … }`, because a frame resumes the coroutines it woke only after it has recomposed, so a write after `awaitOffset` returns would be drawn one frame late (D-224). Haptics, the interactivity flip and confetti emission act on ⌈X⌉ itself. §9 includes this lag. |
| T3 | **Custom frame loops** (confetti, sparkles) read `coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f` on every frame. They integrate with `dt = min((frameNanos − lastNanos) / 1e9 / scale, MAX_DT_S)`, and end at once, drawing nothing, when the scale is 0 or `LocalEffectiveMotion` is `OFF`. |
| T4 | **Rate limits are real time.** The 400 ms Reject limit, the flash limiter and the auto-repeat limit (D-053) use `MonotonicClock` (D-022) and are never multiplied by the animator scale. |
| T5 | **Off never launches an animation.** Initial values equal end values (§3 tables), and no effect coroutine starts. Everything that Full schedules with `awaitOffset` is derived directly from state in the F0 composition instead: overlays and their interactive buttons, the displayed score, the NEW BEST style. Only haptics still go through the timeline (§6). The whole screen is in its end state on F0. |
| T6 | **Haptic offsets** follow T2. Under Full they scale with the animator scale, so they stay on their visual cue. Under Reduced and Off they use unscaled real time (`awaitRealTime`), so the two win pulses stay 150 ms apart even at scale 0 (D-185). |

### 2.4 `MoveTimeline`

```kotlin
// game/motion/MoveTimeline.kt
@Stable
class MoveTimeline {
    /** Called by BoardView: LaunchedEffect(board.moveId) { publish(board.moveId, withFrameNanos { it }) }.
     *  A moveId that already has an origin keeps it: the BoardView an arrangement switch composes anew
     *  publishes the current moveId again, and the waits of the screen scope go on unchanged (§3.16). */
    fun publish(moveId: Long, originNanos: Long)
    /** Suspends until the first frame >= origin(moveId) + ms × scale. Returns false if a newer moveId was
     *  published first (the caller then drops its work, see §4). Returns at once when the scale is 0. */
    suspend fun awaitOffset(moveId: Long, ms: Int): Boolean
    /** Same, and runs onDue inside the due frame's callback, before that frame recomposes, so a (B) flip is
     *  drawn on frame ⌈X⌉ itself (T2, D-224). onDue runs only when it returns true. */
    suspend fun awaitOffset(moveId: Long, ms: Int, onDue: () -> Unit): Boolean
    /** Same, but the offset is never scaled (T6). */
    suspend fun awaitRealTime(moveId: Long, ms: Int): Boolean
    val latestOriginNanos: Long
}
val LocalMoveTimeline = staticCompositionLocalOf<MoveTimeline> { error("MoveTimeline not provided") }
```

`publish` runs inside the same `withFrameNanos` callback (F1) as the first frame of the tile animations, so the origin is identical for every consumer.

### 2.5 Frame loops

- **Confetti and sparkles** run in one `LaunchedEffect` per emission. It calls `withFrameNanos` until `system.aliveCount == 0`, then writes `tick` (`mutableLongStateOf`). The canvas reads `tick` inside its draw lambda, so each frame **invalidates draw only; nothing recomposes**.
- **The board frame-rate vote** (D-183): while any board animation runs, `BoardView` applies `Modifier.preferredFrameRate(FrameRateCategory.High)`, and `FrameRateCategory.Default` otherwise. The API is public in ui 1.12.1 (verified 2026-10-28). The flag flips at most twice per move burst: on at the commit composition, off when idle (§7.2).

---

## 3. Per-event timelines

Each event has a Full timeline, then Reduced and Off. The cross-event matrix is §5.1. Fixture names refer to §8.6. The sequences that no earlier decision specified are D-183's gap fills: the board-size switch (§3.10), card exits including Keep going → `OVER` (§3.12), the screen-transition and predictive-back specs (§3.17, §3.19), and the Reduced variants of undo, size switch, new game and card exits.

### 3.1 Accepted move: slide, merge, spawn

**Board model.** `BoardPresenter` (D-025; `game/board/BoardPresenter.kt`, M2) emits `BoardUi(moveId, epoch, size, tiles, cells, scoreDelta, event)` (`game/board/BoardUi.kt`, `ARCHITECTURE.md` §5.1). `event` is:

```kotlin
sealed interface BoardEvent {
    data object Restored : BoardEvent       // load, splash release, and every presented board after settled() (§3.14): Static, no animation at all (D-047)
    data class Moved(
        val direction: Direction,
        val mergedExps: ImmutableList<Int>,
        val mergeCount: Int,
        val maxMergedExp: Int,              // drives the >= 512 haptic and the "+N" pill (D-183, D-187)
        val maxSlideCells: Int,             // longest slide of the move, in cells: selects the spawn delay (D-183)
        val spawnIndex: Int,
        val scoreAfter: Long,
        val haptic: MoveHaptic,             // resolved by HapticsMap in GameViewModel; only scheduled by HapticsController (D-185)
        val newBest: Boolean,
        val becameWon: Boolean,
        val becameOver: Boolean,
    ) : BoardEvent
    data object Undo : BoardEvent           // Static tiles; only changed cells crossfade (D-064, D-182)
    data object NewGame : BoardEvent        // Static tiles; new-game cascade of outgoing and start tiles (D-064, D-182)
    data object SizeSwitched : BoardEvent   // Static tiles; the whole board fades through AnimatedContent(targetState = boardSize) (D-182)
}
```

An epoch `BoardUi` (event `Restored`, `Undo`, `NewGame` or `SizeSwitched`) carries only `Static` tiles (D-182). Invalid moves do not produce a `BoardUi`. They update `GameViewModel.nudge: StateFlow<Nudge?>` with `Nudge(id, direction)` (§3.7), so an invalid move never restarts tile effects.

**Presenter rules for a move:**
- persisting tiles keep their IDs as `Moved` (from = old cell, to = new cell; from == to if they did not move);
- both merge sources keep their IDs as `Ghost` (to = merge cell);
- the merged tile gets a new ID as `Merged`;
- the spawn gets a new ID as `Spawned`;
- ghosts of the previous `BoardUi` are dropped;
- list order is draw order: Ghost, then Static/Moved, then Merged, then Spawned (`DESIGN_SYSTEM.md` §4.7).

**Tile state** (`game/board/BoardAnimator.kt`: `BoardAnimator` holds one `TileAnim` per tile id):
- `pos: Animatable<Offset>` in cells, threshold `Offset(τ, τ)`, τ = `POSITION_THRESHOLD_PX / pitchPx`;
- `scale`, `alpha`, `sheen`, `glowBoost`: `Animatable<Float>`.

Per-frame values are read only in a custom layout modifier (placement from `geometryState` × `pos.value`), in `graphicsLayer {}` (scale, alpha) and in `drawBehind {}` (fill, label, sheen, glow). Cell units mean a geometry change during `animateBounds` (§3.16) never disturbs a running slide.

**Initial values and specs (Full).** D = spawn delay (below). v0 = pop impulse (§3.3).

| Kind | Position | Scale | Alpha | Extras |
|---|---|---|---|---|
| `Moved` | from → to, `slide` (skipped if from == to) | unchanged | unchanged | — |
| `Ghost` (merge source) | from → merge cell, `slide` | 1 | 1, then 0 via `snap(delayMillis = 200)` | — |
| `Merged` | at to | 1 → pop: `animateTo(1f, delayedSpec(popSpatial(0.002f), 80), initialVelocity = v0)` | 0, then 1 via `snap(delayMillis = 80)` | sheen 0.35 → 0 with `delayedSpec(fastEffects(), 80)` if exp ≥ 7; glow boost 0.25 → 0 with `delayedSpec(defaultEffects(), 80)` if exp ≥ 9 and `palette.glow(exp) != null`; both subject to the flash limiter |
| `Spawned` | at to | 0.6 → 1, `delayedSpec(popSpatial(0.002f), D)` | 0 → 1, `delayedSpec(fastEffects(1/255f), D)` | `transformOrigin` = centre |
| `Static` | at to | 1 | 1 | — |

Ghost removal is two steps. At 200 ms the ghost's alpha snaps to 0 (no recomposition, §7.2), which is D-025's "removed after 200 ms" as seen on screen. The ghost node itself leaves composition at the next board change (step 1 of §4.1). Hidden ghosts carry no semantics (D-080) and do not count for the invariants of §4.3.

**Spawn delay D (D-183).**

| Move's longest slide (`maxSlideCells`) | ≤ 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|
| D (ms) | 90 (`SPAWN_DELAY_MS`) | 100 | 105 | 110 | 115 |

A spawn can land in the cell right behind a tile that is still finishing a long slide. The table keeps the spawned tile's visible rectangle (alpha ≥ 0.05, scaled about its centre) clear of every moving tile. Computed 2026-10-28, the minimum delays are 61/81/92/99/104/108/111 ms for 1–7 cells (Classic gap ratio 0.14) and 58/79/90/97/102/106/110 ms (Neon 0.18). At 90 ms and 3 cells the residual 0.016-tile overlap occurs only while the spawn's alpha is below 0.05. A 4×4 board (longest slide 3) always uses 90 ms, as D-060 says.

**Timeline (Full; t in ms from origin).**

```
t:       0    16   32   48   64   80   96  112  128  160  200 208      288     368  416  432
         |----|----|----|----|----|----|----|----|----|----|--|--------|-------|----|----|
slide    [=== progress .13 .35 .57 .73 .85 .92 .96 .98 1.0 ==]  ends 208..271 (Compose estimate)
ghosts   [slide with their sources, drawn under the merged tile ......]x hidden at 200 (frame 208)
merged   . alpha 0 (hidden) ...... [revealed: alpha 1; pop v0 6..12, peak at +41 (121); settles +288..329]
sheen                              [0.35 -> 0, fastEffects, ends ~+125]            (merged >= 128)
glow                               [+0.25 -> 0, defaultEffects, ends ~+193]        (merged >= 512)
spawn    . scale .6, alpha 0 ..........[D=90: scale .6->1 (peak 1.038 at +139), alpha 0->1 (~+125)] ends 415
"+N"                               [created at 80; rise 650, hold 250 + fade 400 from 96] (§3.4)
score                              [digits retargeted at 80; roll from 96]     (§3.5)
haptic                             * one of SegmentTick / Confirm / GestureThresholdActivate (§6)
```

- Merged-tile pop peaks (computed 2026-10-28) are 1.1058 / 1.1411 / 1.1764 / 1.2116 for v0 = 6/8/10/12, all at 41.0 ms after the reveal, i.e. t = 121 ms. Each undershoots once (0.990 / 0.987 / 0.983 / 0.980) near t = 260 ms.
- Everything has settled by t = 415 ms (spawn with D = 90). With the 16 ms grid, no board animation is running at t = 432. D-061's "≈ 400 ms" holds.
- **Board-size generality.** Every rule above is per tile and in cells, so it applies to N = 3…8 unchanged (D-064).

**Reduced.** Positions snap at F0. Merge: ghosts sit in the merge cell, and the merged tile's alpha goes 0 → 1 over `tween(120, LinearEasing)` above them. Ghost alpha snaps to 0 at 120 ms. No pop, sheen or glow pulse. Spawn: scale 1, alpha 0 → 1 with `fastEffects` starting at t = 0 (no delay; nothing is sliding).

**Off.** All tiles at their end state on F0. Ghosts are composed with alpha 0. No sheen, pulse or pop.

### 3.2 Spawn detail

| Property | Full | Reduced | Off |
|---|---|---|---|
| Start | origin + D (90–115 ms) | origin | — |
| Scale | 0.6 → 1, `popSpatial`, threshold 0.002. Values at +6/+22/+38/+70/+150/+310 ms: 0.6054 / 0.6594 / 0.7438 / 0.9102 / 1.0363 / 0.9974. Peak 1.0379 at +138.8 ms. Ends +325 ms | 1 | 1 |
| Alpha | 0 → 1, `fastEffects`, threshold 1/255. Values at +6/+22/+38/+70 ms: 0.0537 / 0.3929 / 0.6788 / 0.9290. Ends +125 ms | 0 → 1 `fastEffects` from origin | 1 |

The two tiles of a new game use this spec at the new-game offsets (§3.8). Restored boards never spawn (D-047).

### 3.3 Merge pop detail

- **Impulse tier per merged tile** (D-183). The tile's own merged value selects v0: value ≤ 64 → 6 s⁻¹; 128–512 → 8; 1024 → 10; ≥ 2048 → 12. A move merging 2+2 and 1024+1024 pops the 4 gently and the 2048 strongly. The move-level `maxMergedExp` still drives the ≥ 512 `Confirm` (D-065) and the "+N" pill (D-187).
- **Neighbour cap** (D-183). `v0 = min(tier, (2 · gapPx / tilePx) / POP_PEAK_PER_VELOCITY)`. This keeps the scaled tile from reaching a resting neighbour (overflow per side ≤ gap). With D-075 geometry the cap only bites when the 20 dp gap clamp lowers the ratio below 0.106, which means tiles above about 189 dp (3×3 on boards ≥ 640 dp). Example: 3×3 on 720 dp gives tile 213.3 dp and gap 20 dp, so v0 ≤ 10.6.
- **Sheen** (merged value ≥ 128): white `drawRoundRect` over the tile fill inside the tile's own `drawBehind`, alpha 0.35 → 0 with `delayedSpec(fastEffects(), 80)`. It is tile-local, so it follows the tile if a new move slides it (D-187).
- **Glow pulse** (merged value ≥ 512 and the palette defines a glow for that exponent): the `dropShadow` block reads `base + glowBoost`. glowBoost goes 0.25 → 0 with `delayedSpec(defaultEffects(), 80)`. Classic super tiles (≥ 4096) are flat (D-078), so they get no pulse.
- **Flash limiter** (D-183): a tile that would get a sheen and/or a glow pulse counts once. At most 3 per rolling 1000 ms of real time across the board. Beyond that the tile still pops but gets no sheen or pulse. This enforces D-063's "nothing flashes more than 3 times per second" during key-repeat merging.
- **Reduced:** the 120 ms merge crossfade of §3.1, no sheen or pulse. **Off:** nothing.

### 3.4 Score "+N" float

Emitted for every move with `scoreDelta > 0` (`game/header/ScoreFloats.kt`). It is drawn in an overlay `Box` anchored to the Score value's baseline, centred on the value and not clipped by the chip. Text and colors follow `DESIGN_SYSTEM.md` §7.2 (`primary` text; a fill-colored pill when `maxMergedExp ≥ 9`, D-187).

Mechanism (B): the float is created by `awaitOffset(moveId, 80)` in Full, or `awaitOffset(moveId, 0)` in Reduced. It appears on that frame at its initial values, and its animations run from the next frame, which this table calls "start".

| Property | Full | Reduced | Off |
|---|---|---|---|
| Created | frame ⌈80⌉ (80 on the test grid), start 96 | origin frame, start 16 | — (not composed) |
| translationY | 0 → −28 dp, `tween(650, easing = EmphasizedDecelerate)`. At +48/+160/+320/+480 ms after start: −15.28 / −23.19 / −26.55 / −27.71 dp | 0 | — |
| Alpha | `keyframes { durationMillis = 650; 1f at 0; 1f at 250; 0f at 650 }`; 1 from creation | 1 → 0, `tween(400, LinearEasing)` | — |
| Scale | 0.8 → 1, `popSpatial(0.002f)` (peak 1.019 at +139 ms after start) | 1 | — |
| Removal | when the alpha animation ends (start + 650 ms; frame 752 on the test grid) | start + 400 ms | — |

At most **3** floats are alive (D-062). Starting a 4th removes the oldest at once, with no fade. Undo, new game and size switch remove all floats on their F0, also with no fade. `GameHeader` tells them from a move by `HeaderUi.event` and `HeaderUi.epoch`, which copy the event kind and epoch of the `BoardUi` the header accompanies: the float host is composed inside `key(header.epoch, header.event)`, so a header whose event is not `Moved`, or whose epoch changed, disposes every running float in the composition that reads it (`ARCHITECTURE.md` §4.11, AC-SCORE-5).

### 3.5 Score odometer and best value

- **Displayed value.** `ScoreChip` shows `displayedScore`. It updates to the true score at `awaitOffset(moveId, 80)` after an accepted move (mechanism B: the new digits are targeted on frame ⌈80⌉ and roll from the next frame) (D-061: "+N float and score odometer start" at 80 ms). It updates at once (t = 0) after undo, and with no animation at all after new game, size switch and restore. If a newer move publishes first, the pending update is dropped and the newer one applies at its own +80 ms. Semantics always carry the true score (D-080).
- **Best value.** BEST shows `max(records[size].bestScore, displayedScore)`, so it rolls together with the score once the score passes the stored best.
- **Size.** Every digit renders at the one size `ScoreValue` picks for the value's digit count and its `ScoreFit.valueWidth` (14..28 sp in 1 sp steps, `DESIGN_SYSTEM.md` §7.2), so digits never resize independently while they roll. `Text(autoSize = …)` is not used for the value. A value too long for its row at 14 sp wraps between digits (`scoreLines`, `DESIGN_SYSTEM.md` §7.2); the odometer then places its digit positions, still keyed by place value, in those lines.
- **Full** (`game/header/ScoreOdometer.kt`): one `AnimatedContent` per digit position, keyed by place value (units, tens, …), so a units change never animates the tens. Increase: `slideInVertically(GameMotion.slide(IntOffset(1, 1))) { it } + fadeIn(fastEffects)` togetherWith `slideOutVertically(slide) { -it } + fadeOut(fastEffects)`. Decrease (undo): the signs are reversed. No stagger. A new leading digit enters with `expandHorizontally(overlaySpatial(IntSize(1, 1))) + fadeIn(fastEffects)`; a removed one leaves with `shrinkHorizontally(overlaySpatial) + fadeOut(fastEffects)`. Each changed digit rolls once to its new value; it never counts through intermediate values. The digit slide settles in 146 ms for a 60 px glyph (computed).
- **Reduced and Off:** one plain `Text` at the same `ScoreValue` size. `displayedScore` follows the true score directly, so it changes in the F0 composition.

### 3.6 New best (once per game, D-044)

Triggered by `BoardEvent.Moved.newBest` (the first accepted move with `score > bestAtStart > 0`). Mechanism (B): the moment starts by `awaitOffset(moveId, 80)`, on frame ⌈80⌉ (80 on the test grid). **S** is the following frame (96 on the test grid), where its animations have play time 0.

| Time | Full |
|---|---|
| ⌈80⌉ | Targets flip: BEST container `surfaceContainerHigh` → `tertiaryContainer`; label and value → `onTertiaryContainer`; label `best_label` → `new_best_label`. The star badge (`game/header/NewBestBadge.kt`) is composed. Haptic `GestureThresholdActivate` fires when `event.haptic` is `NEW_BEST`, that is, unless a higher priority applies (§6, D-185) |
| ⌈80⌉ | 8 sparkles are emitted from the chip centre: 6 dp four-point stars in `tertiary`. Direction i·45° + 22.5° plus a jitter of U(−10°, 10°); speed U(120, 220) dp/s; no gravity or drag. Life 600 ms; alpha 1, then → 0 linearly over the last 250 ms. They are drawn by `ContentEffectsCanvas` (§3.11, D-187) with the shared particle engine |
| S | Colors animate with `animateColorAsState(defaultEffects(threshold))` (0.896 of the way at S + 96). The label uses `AnimatedContent`: `fadeIn(fastEffects) + slideInVertically(slide) { 8.dp }` togetherWith `fadeOut(fastEffects) + slideOutVertically(slide) { −8.dp }` |
| S | Star badge behind the label: `RoundedPolygon.star(10, innerRadius = 0.82f, rounding = CornerRounding(0.15f))`, 20 dp, `tertiary`. Scale 0 → 1 `popSpatial` (peak 1.095 at S + 139, ends S + 379). Rotation 0 → 45° `tween(900, EmphasizedDecelerate)`. Alpha 1 → 0 `tween(300, delayMillis = 1200, FastOutSlowInEasing)` |
| ⌈80⌉ + 600 | All sparkles dead (frame 688) |
| S + 1500 | Moment over (frame 1600). The NEW BEST color state stays for the rest of the game (`DESIGN_SYSTEM.md` §6.1) |

- Particle randomness comes from `ParticleRandom(seed = state.seed xor state.moveCount.toLong())` (§3.11), so it is deterministic in tests.
- **Reduced:** at origin, the label swaps instantly and the container colors crossfade with `defaultEffects`. No star or sparkles.
- **Off:** label and colors change on F0.
- Restore with `newBestAnnounced = true` renders the NEW BEST style statically. Undo does not stop a running new-best moment, because undo does not reset `newBestAnnounced`. New game and size switch cancel it: `GameHeader` cancels the moment when `HeaderUi.epoch` changes, unless `HeaderUi.event` is `Undo` and the epoch advanced by exactly one (`ARCHITECTURE.md` §4.11, AC-SCORE-5).

### 3.7 Invalid move: direction-aware nudge

Triggered by a new `Nudge(id, direction)` on `GameViewModel.nudge: StateFlow<Nudge?>` for `MoveOutcome.Invalid` (`BoardView` keys on `nudge?.id`). `MoveOutcome.Blocked` (status `WON` or `OVER`) produces **no motion and no haptic**: the celebration or card already explains the state.

- **Full:** a board-level `nudge: Animatable<Offset>` (px) is applied with `Modifier.offset {}` to the board container (tiles, empty cells and board effects move together). On each invalid move: `nudge.animateTo(Offset.Zero, GameMotion.nudge(Offset(t, t)), initialVelocity = unit(direction) * 330.dp.toPx())` from the current offset. Here t = 0.1 dp in px, and `unit(LEFT) = (−1, 0)`, `RIGHT = (1, 0)`, `UP = (0, −1)`, `DOWN = (0, 1)`. Directions are physical, because the board is a forced-LTR subtree (D-104).
- **Values along the swipe axis** (computed 2026-10-28): +5.22 dp peak at 31.3 ms; −1.95 at 108.9 ms; +0.72 at 186.5; −0.27 at 264.1; +0.10 at 341.8; Compose ends it at 346 ms. On the frame grid: 4.01 dp (16 ms), 5.22 (32), −1.93 (112), +0.71 (192), −0.02 (400).
- **Repeat.** A second invalid move restarts from the current offset with the new velocity. Velocity is not accumulated. A valid move snaps `nudge` to zero on its F0, so tiles always slide inside an aligned board.
- **Haptic** `Reject`, at most 1 per 400 ms of real time, and none below API 30 (§6, D-185).
- **Reduced:** no offset. A board-shaped 2 dp stroke in `primary` pulses with alpha `keyframes { durationMillis = 300; 0f at 0; 0.6f at 100; 0f at 300 }` (`DESIGN_SYSTEM.md` §2.10). It is rate-limited to 1 per 400 ms, which keeps it under D-063's 3 flashes per second.
- **Off:** no visual. The haptic still fires (rate-limited; API 30+).

### 3.8 New game: board clear and spawn cascade

`BoardEvent.NewGame` (D-182). The epoch `BoardUi` carries only `Static` tiles: the two start tiles, with new IDs (incoming). The outgoing tiles are the IDs present before the epoch and absent after it; `OutgoingTilesLayer` draws them from the previous `BoardUi`, below the new tiles, while they exit, and `MotionProbe` reports them as outgoing (§8.2). A `Static` tile never runs per-move specs (slide, reveal, pop, spawn delay); it animates only through its epoch's board-level entrance, selected by `BoardUi.event`. Let n = the number of outgoing tiles and `step = min(15, 240 / n)` ms. Outgoing tile i, counted in reading order (row-major), has index i.

| Element | Full |
|---|---|
| Outgoing tile i | scale 1 → 0.6 and alpha 1 → 0, both `delayedSpec(fastEffects(), i·step)`. Total stagger ≤ 240 ms for any N (4×4 full board: 16 × 15) |
| Incoming tile k (k = 0, 1, reading order) | The spawn spec of §3.2, starting at `T_in + 60·k` with `T_in = n·step`. Example: 14 outgoing tiles gives starts at 210 and 270 ms, all settled by 595 ms |
| Score / best | Instant (the score becomes 0); "+N" floats removed; celebrations removed (§4.3) |
| Overlay | If the old game showed a card: card exit (§3.12) runs in parallel |

- **Reduced:** outgoing alpha 1 → 0 `fastEffects` (no shrink, no stagger); incoming alpha 0 → 1 `fastEffects` from origin.
- **Off:** swap on F0.

### 3.9 Undo: crossfade (decided)

D-064 decides it: a `fastEffects` crossfade; the rewind animation is deferred to v1.x. The epoch `BoardUi` carries only `Static` tiles (D-182). `BoardPresenter.reset(state, Undo)` compares the outgoing and incoming boards cell by cell:
- a cell with the **same exponent** in both keeps its tile ID. IDs present in both boards neither animate nor recompose (no flicker on unchanged tiles);
- an outgoing tile whose cell changed (its ID is absent from the new board) is drawn by `OutgoingTilesLayer` from the previous `BoardUi`: alpha 1 → 0, `fastEffects` from origin;
- an incoming tile whose cell changed is `Static` with a new ID: alpha 0 → 1, `fastEffects` from origin. It is drawn above the outgoing layer.

| t (ms) | 0 | 16 | 32 | 64 | 128 |
|---|---|---|---|---|---|
| Incoming changed tiles, alpha | 0 | 0.2592 | 0.5865 | 0.9043 | 0.9967 (ends at 125) |
| Outgoing tiles, alpha | 1 | 0.7408 | 0.4135 | 0.0957 | 0.0033 |

- **Positions never animate on undo.**
- **Undo icon:** `rotation.animateTo(rotation.targetValue − 360f, tween(300, EmphasizedDecelerate))`. Cumulative, so a key-repeat undo every 120 ms keeps spinning with no jump. When idle, the value is normalized with `snapTo(value % 360f)`.
- **Score:** the reverse odometer roll at origin (§3.5).
- **Leaving a card:** card exit (§3.12) and saturation restore run in parallel.
- **Reduced:** the same crossfade (alpha only; it moves nothing), no icon rotation (D-183). **Off:** swap on F0.

### 3.10 Board-size switch

`BoardEvent.SizeSwitched` (D-007, D-182). Cell geometry changes with N, so tiles do not crossfade individually and the outgoing-tiles layer is not used. `BoardView` is wrapped in `AnimatedContent(targetState = boardSize, transitionSpec = { fadeIn(defaultEffects(ALPHA_THRESHOLD)) togetherWith fadeOut(fastEffects(ALPHA_THRESHOLD)) })`. Both boards are composed for 193 ms (the `defaultEffects` estimate). Tiles of the new board are `Static`. Score and best change instantly. If the target size's game is `WON` or `OVER`, its card renders in its **end state** at once, interactive, with no celebration (§3.14). Reduced: the same fade (D-183). Off: swap.

### 3.11 Win celebration

Triggered by `BoardEvent.Moved.becameWon`. **T0 = origin + 80 ms** (the merge reveal; D-064). Every overlay offset is relative to the origin through `MoveTimeline`, never through the overlay `StateFlow`, so board and overlay cannot drift (D-024 keeps the flows separate). The scrim, ring and tile pop are owned by `BoardView` and are mechanism (A). The card is mechanism (B): it composes on frame ⌈680⌉ (688 on the test grid), and its **overlay origin O** is the next frame (704), where card, badge and button animations have play time 0.

| t from origin (ms) | Element | Full spec |
|---|---|---|
| 80 (T0) | Target tile | (A) Merge reveal with pop v0 = 12 (cap of §3.3 applies): peak 1.2116 at t = 121 |
| ⌈120⌉ | Haptic | `Confirm` #1, at the pop peak (frame 128) |
| 140 (T0 + 60) | Glow ring | (A) In `BoardEffectsCanvas` (`game/board/EffectsCanvas.kt`), above tiles and below the scrim (`DESIGN_SYSTEM.md` §4.7, D-187). Visibility `snap(delayMillis = 140)`; one `tween(700, delayMillis = 140, EmphasizedDecelerate)` fraction f drives three properties of a stroked rounded rect equal to the tile rect: scale about the tile centre 1.0 → 1.8, stroke 6 dp → 0, alpha 0.9 → 0. Color: Classic `#F3D774`, Neon `palette.fill(targetExp)`. At +4/+48/+52/+96/+160/+320 ms of ring time: alpha 0.830/0.426/0.408/0.272/0.168/0.056 (scale 1.062/1.422/1.438/1.558/1.650/1.750). Ends at t = 840 |
| ⌈200⌉ (T0 + 120) | Confetti | Single burst from the target tile's centre (spec below), emitted on frame 208 with age 0 |
| ⌈270⌉ | Haptic | `Confirm` #2 (frame 272) |
| 680 (T0 + 600) | Scrim | (A) 0 → 0.60 `surface`, clipped to the board shape, `tween(300, delayMillis = 680, Standard)`: 0.0042 at t = 688, 0.449 at t = 784, 0.60 at t ≥ 992 |
| ⌈680⌉ | Overlay | (B) The overlay composes now (not earlier), so the card's `paneTitle` and the assertive announcement (D-081) coincide with its appearance. From O: card scale 0.92 → 1 `overlaySpatial(0.002f)` (peak 1.0012 at O + 269; ends O + 269), alpha 0 → 1 `fastEffects` (0.2592 at O + 16; ends O + 125) |
| O | Hero badge | Shape `Morph(RoundedPolygon.circle(numVertices = 12), RoundedPolygon.star(12, innerRadius = 0.88f, rounding = CornerRounding(0.14f)))`. Progress 0 → 1 `overlaySpatial(0.001f)` (the overshoot to 1.015 is clamped to [0, 1] at draw), ends O + 476. Badge scale 0 → 1 `popSpatial` (peak 1.095), ends O + 379. Rotation 0° → 60° `tween(5000, LinearEasing)`, then it **stops** (WCAG 2.2.2). `Morph.toPath(progress, path)` writes into one remembered `android.graphics.Path` |
| O + 50 / 100 / 150 | Buttons | Keep going, New game, Undo: alpha 0 → 1 `fastEffects` (`delayedSpec(fastEffects(), 50·(i+1))` from O). The top-end Share icon fades in with Keep going (O + 50). All are composed with `enabled = true`: visible with enabled colours, not interactive (D-200) |
| ⌈880⌉ (T0 + 800) | Buttons | `OverlayController.interactive` flips on frame 880: all buttons, including Share, become interactive together (clicks accepted, `disabled()` semantics removed, focusable); focus moves to Keep going (D-081, D-200). The card never blocks input longer (D-008) |
| emission + ≤ 2.2 s | Confetti | Last particle dead (frame 2416 at the latest); loop ends |
| O + 5000 | Badge | Rotation reaches 60° and stops (frame 5712) |

**Confetti spec** (research/04 §10.9, adopted by D-064):
- **Engine.** `ConfettiSystem` (`game/motion/ConfettiSystem.kt`) is pure Kotlin with no Compose types. Structure-of-arrays `FloatArray(CAP)` fields: x, y, vx, vy, rotationDeg, spinDegS, flutterPhase, flutterOmega, flipOmega, age, life, sizeScale. `ByteArray(CAP)` fields: shape, colorIndex. Nothing is allocated after construction.
- **Count:** 96 when the window width is < 840 dp, 144 when ≥ 840 dp (`DESIGN_SYSTEM.md` §5.1 "expanded"); cap 160.
- **Shapes:** 60 % rounded rects 6 × 10 dp (corner 1.5 dp), 25 % circles r = 3 dp, 15 % four-point stars 8 dp (one pre-built `Path`).
- **Emission:** angle U(−150°, −30°) (screen y down, so this fans upward), speed U(700, 1400) dp/s, spin U(−540, 540) °/s, flutter ω U(6, 12) rad/s with phase U(0, 2π), flip ω U(8, 16) rad/s, life U(1.4, 2.2) s, color index uniform over 7 colors: `palette.fill(7..11)`, `primary`, `tertiary`.
- **Step** (per frame, dt from T3): `vy += 1800·dt; f = exp(−2.2·dt); vx *= f; vy *= f; x += vx·dt; y += vy·dt; rotation += spin·dt; age += dt`. Dead when `age ≥ life`. Terminal fall speed ≈ 818 dp/s.
- **Draw** (`ContentEffectsCanvas`, above the scrim and below the card; D-187): `withTransform({ translate(x + 12dp·sin(flutterOmega·age + phase), y); rotate(rotation); scale(1f, cos(flipOmega·age) · s) })`, where `s = lerp(0.4, 1, min(age / 0.08, 1))`. Alpha 1, then → 0 linearly over the last 0.35 s of life. `drawRoundRect` / `drawCircle` / `drawPath` with value-class arguments only.
- **Randomness:** `ParticleRandom(seed = state.seed xor state.moveCount.toLong())`, wrapping the engine's `SplitMix64` (D-038). `nextFloat() = (next() ushr 40).toFloat() / 16_777_216f`. It is deterministic, so filmstrips are reproducible.
- **Performance budget** (§7.5): `step` ≤ 0.15 ms and draw ≤ 1.0 ms per frame at 160 particles on a Pixel 6a-class device; zero allocation per frame.

**Reduced:** T0 = origin. The target tile gets the 120 ms merge crossfade. No ring, confetti, morph, badge scale or rotation: the badge renders in its end state (cookie, 60°). Scrim (A) 0 → 0.60 with `tween(150, LinearEasing)` from the origin. The card (B) composes on the origin frame and fades 0 → 1 with `tween(150, LinearEasing)` from the next frame; its buttons are visible with enabled colours, not interactive. Buttons interactive at ⌈150⌉ (frame 160). Haptics at +0 and +150 ms real time (frames 0 and 160).

**Off:** card, scrim and badge end state on F0; buttons interactive on F0; haptics at +0 and +150 ms real time.

### 3.12 Card exits: Keep going, New game, Undo

When a card button is tapped, the card stops accepting input on that frame (`Modifier.pointerInput` and focus are disabled) while it animates out. Input reaches the board at once. The same exit runs when the controls-bar Undo or New game, or the U, Ctrl+Z, Backspace, N or R keys, act while a card is shown (D-199).

| Element | Full | Reduced | Off |
|---|---|---|---|
| Card | alpha → 0 `fastEffects`; scale 1 → 0.96 `slide(0.002f)` | alpha → 0 `tween(150, Linear)` | removed |
| Scrim | → 0 `tween(200, EmphasizedAccelerate)` | → 0 `tween(150, Linear)` | 0 |
| Board saturation (from `OVER`) | → 1.0 `tween(200, Standard)` | → 1.0 `tween(150, Linear)` | 1.0 |
| Deflated tiles (from `OVER`) | Replaced by the undo or new-game tiles (scale 1) | same | same |

Card exits are not board changes, so `OverlayController` uses its own origin: **Ft**, the first frame after the tap, captured with `withFrameNanos`. Exit animations have play time 0 on Ft.

**Keep going with no moves left** (D-036: `keepGoing()` sets `OVER` at once). The win card exits as above, and the game-over sequence of §3.13 starts in parallel with **T0 = Ft**:
- `Reject` fires on Ft: it is the game-over `Reject`, once per entry into `OVER`; the Keep going tap itself is silent (D-185);
- the scrim does not fade out: it animates from its current value to 0.78 with `tween(300, Standard)` from Ft;
- desaturation and the deflate ripple start at Ft; the deflate origin is the cell of the winning move's spawn;
- the game-over card composes on ⌈Ft + 500⌉ and its buttons become interactive on ⌈Ft + 700⌉ (D-200).

### 3.13 Game-over sequence

Triggered by `BoardEvent.Moved.becameOver`. **T0 = origin + 300 ms**, which lets the final spawn be seen (D-064). Desaturation, deflate and scrim are owned by `BoardView` and are mechanism (A). The card is mechanism (B), with overlay origin O = the frame after ⌈800⌉ (816 on the test grid).

| t from origin (ms) | Element | Full spec |
|---|---|---|
| ⌈300⌉ (T0) | Haptic | `Reject` (frame 304) |
| 300 → 900 | Desaturation | (A) Saturation s from 1.0 to 0.2, `tween(600, delayMillis = 300, Standard)`: 0.9997 at t = 304, 0.675 at t = 400, 0.421 at t = 496, 0.2 at t ≥ 912. Applied arithmetically to every tile fill, ink and glow color in draw (§7.3). The board and empty cells keep their colors (`DESIGN_SYSTEM.md` §2.10) |
| 300 + m·step | Deflate ripple | (A) Tile scale 1 → 0.94 with `delayedSpec(standardSpatial(0.002f), 300 + m·step)`: 0.977 at +48 ms, 0.954 at +96, ends +178. m = Manhattan distance from the final spawn's cell; `step = min(30, 180 / mMax)` ms, where mMax is the largest m on the board (≥ 1). On 4×4 from a corner: 30 ms, the last tile starting at T0 + 180. On 8×8 from a corner: 12.9 ms, so the ripple never runs under the scrim that arrives at T0 + 450 (D-183) |
| 750 (T0 + 450) | Scrim | (A) 0 → 0.78 `surface`, clipped to the board shape, `tween(300, delayMillis = 750, Standard)`: 0.0003 at t = 752, 0.564 at t = 848, 0.78 at t ≥ 1056 |
| ⌈800⌉ (T0 + 500) | Card | (B) The overlay composes on frame 800. From O: scale 0.92 → 1 `overlaySpatial`, alpha `fastEffects`. The buttons New game / Undo / Share fade in at O + 50 / 100 / 150, composed with `enabled = true`: visible with enabled colours, not interactive (D-200) |
| ⌈1000⌉ (T0 + 700) | Buttons | Interactive on frame 1008 (`OverlayController.interactive`, D-200); focus to New game (D-081) |

- **End state of `OVER`** (D-183), identical for the animated end, Reduced, Off, restore and size switch: tiles at scale 0.94, saturation 0.2, scrim 0.78, card visible with interactive buttons. Static renders and the `game_over-4x4_*` goldens draw the same state (`DESIGN_SYSTEM.md` §6.4).
- **Reduced:** T0 = origin. Tiles snap to 0.94. Saturation 1 → 0.2 and scrim 0 → 0.78 run (A) with `tween(150, LinearEasing)` from the origin. The card (B) composes on the origin frame and fades in with `tween(150, LinearEasing)` from the next frame. Buttons interactive at ⌈150⌉ (frame 160). `Reject` at the origin frame.
- **Off:** end state on F0; `Reject` at F0.

### 3.14 Restore, recreation, resume and splash: no motion

On `BoardEvent.Restored`:
- tiles are `Static` at scale 1 (0.94 if `OVER`);
- no "+N", odometer, haptic, celebration or nudge;
- cards and the NEW BEST chip render in their end state; card buttons are interactive at once (a restored overlay, D-008).

This covers cold start after the splash (D-112), `ActivityScenario.recreate()`, resume and the splash's 1000 ms timeout path (D-047). The splash itself uses the platform's default exit with no custom animation (`DESIGN_SYSTEM.md` §8.3).

**Recreation after a move: one-shot presentations** (D-047, `ARCHITECTURE.md` §4.11). The activity-scoped `GameViewModel` outlives the composition, so each animated value is presented once and then settled:
- `BoardView` reports the board at its origin F1 (`onBoardPresented(moveId)`, right after `MoveTimeline.publish`); `GameHeader` reports the "+N" delta (`onHeaderPresented`) and `OverlayController` an `Animated` card entry (`onOverlayPresented`), each from an effect of the composition that read it, on the same F1 for a move;
- the ViewModel replaces the value with its static form for the same state: `BoardPresenter.settled(board)` (same `moveId`, `epoch` and ids; every tile `Static`; event `Restored`), `lastDelta = 0`, entry `Restored`;
- each live presentation scope (the screen, and the arrangement `GameLayout` draws; `ARCHITECTURE.md` §4.11 item 2) reads these flows through its own `rememberPresented` retention state, which treats the replacement as the same presentation. Nothing it is animating changes, no effect relaunches and nothing recomposes (§7.2);
- a scope created afterwards (recreation, return from a sub-screen, the arrangement composed by a switch of §3.16) makes its own retention state and starts from the static values: the end state above on its first frame. Recreation in the middle of a move, a win or a game-over sequence therefore snaps to the final frame. A haptic still pending in the disposed composition (for example the win's second `Confirm`, or a `SegmentTick` due at 80 ms) is dropped and never fired later; no haptic already fired is repeated. An arrangement switch disposes no haptic: `HapticsController` belongs to the screen scope (§3.16);
- a nudge has no end state: a new composition never plays the nudge that existed before it (no shake, no `Reject`).

### 3.15 Theme, palette and contrast switch

- **Scope.** In-app changes while the composition is alive: Theme, Palette, High-contrast tiles, Dynamic color, and the API 34+ contrast listener (D-073).
  - System dark-mode and wallpaper changes recreate the activity (D-090 has no `configChanges`). They render statically.
- **Full:**
  - One `Animatable` fraction 0 → 1 with `slowEffects(FRACTION_THRESHOLD)` (0.5402 at 64 ms, 0.9402 at 160 ms, ends at 299 ms).
  - Every resolved color is `lerp(old, new, fraction)`: `TilePalette` roles and the M3 `ColorScheme` roles.
  - Tile and board colors are read in draw, so they invalidate draw only. Chrome colors necessarily recompose M3 consumers for those ~19 frames. This is the one documented exception to the recomposition budget (§7.2).
  - System-bar icon appearance switches at once at the start.
- **Reduced and Off:** new colors on F0 (`DESIGN_SYSTEM.md` §5.8).

### 3.16 Layout changes in a live composition

`animateBounds` runs only for layout changes without recreation **within one arrangement**: the first-launch hint exit, the TalkBack direction pad showing or hiding (D-082), Immersive mode toggles (D-093) and inset changes, while the resolved arrangement (`DESIGN_SYSTEM.md` §5.3, D-188) stays the same. Rotation, resize, fold and locale changes recreate the activity and render statically (D-095).

**Arrangement switch: no bounds animation** (D-095). Any of these live changes can also change the resolved arrangement, for example the pad turning a 411×450 window from STACKED into SCROLL. `GameLayout` composes each arrangement in its own subcomposition, so a switch disposes the old arrangement and composes the new one, board and header included; `movableContentOf` is not used. The new board is a new composition under the one-shot presentation contract (§3.14, `ARCHITECTURE.md` §4.11): on its first frame it draws the settled static frame of the committed state at its final bounds, with no bounds animation at any motion level. A move, merge, spawn or "+N" in flight snaps to its end as on recreation (F07-AC27), and nothing replays.
- **Each arrangement has its own presentation scope.** The new slot makes its own `rememberPresented` retention states for `board` and `header` before composing its `BoardView` and `GameHeader`, so their first values are the ViewModel's current ones: the settled board and `lastDelta = 0` once the move was presented at its F1. The retained animated instances die with the old slot. This holds whether the switch comes during the move (AC-LAYOUT-2) or long after it settled (AC-LAYOUT-3): the new `BoardAnimator` launches nothing, no "+N" float or new-best moment starts, and switching back composes yet another scope that is equally settled.
- **The screen scope lives on.** `GameScreen` provides `MoveTimeline` and composes `HapticsController`'s effects, `OverlayController`, the scrim, the cards and `ContentEffectsCanvas` outside the arrangement slots. They keep their presented values and their running sequences, fire what they had scheduled at its time (the move's `SegmentTick` at t = 80 in AC-LAYOUT-2) and schedule nothing new. The new `BoardView` publishes the current `moveId` again; `MoveTimeline` keeps the origin it holds for it (§2.4), so no wait of the screen scope moves.

Bounds animate again for the next change that keeps the new arrangement.
- **Full** (within one arrangement):
  - Board, panel and rails use `Modifier.animateBounds(lookaheadScope, boundsTransform = { _, _ -> GameMotion.overlaySpatial(Rect.VisibilityThreshold) })` (`DESIGN_SYSTEM.md` §5.8). A 300 px change settles in 399 ms.
  - The hint card exits with `shrinkVertically(overlaySpatial) + fadeOut(fastEffects)`.
  - The direction pad enters and exits with `fadeIn/fadeOut(fastEffects)`. When its appearance switches the arrangement, the new arrangement composes it already visible, with no fade.
  - Tile labels are measured at the lookahead (target) tile size and drawn scaled by `currentTilePx / targetTilePx` while the bounds animate. That means at most one text measurement per (exponent, target size), and no text layout during the animation frames.
- **Reduced and Off:** bounds snap; hint and pad appear and disappear on F0. An arrangement switch is static at every level, as above.

### 3.17 Screen transitions (Navigation 3)

`NavDisplay` (Nav3 1.2.0) receives three app-wide specs from `ui/theme/GameNavTransitions.kt`. Verified 2026-10-28 (class files): the signature has `transitionSpec`, `popTransitionSpec`: `AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform` and `predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform`, where the `Int` is the swipe edge. The Android defaults (a 700 ms `tween` crossfade, and a predictive `fadeIn(spring(1600))` with `scaleOut(0.7f)`) are replaced.

`shiftPx = 30.dp.roundToPx()`. `dir = +1` in LTR and −1 in RTL (chrome mirrors, D-104). Both are captured in the composable scope before `NavDisplay`, because the spec lambdas are not composable.

| Spec | Full | Reduced | Off |
|---|---|---|---|
| Push (Game → sub-screen, About → Licenses) | enter `slideInHorizontally(standardSpatial(IntOffset(1,1))) { dir·shiftPx } + fadeIn(defaultEffects)` togetherWith exit `slideOutHorizontally(standardSpatial) { −dir·shiftPx } + fadeOut(fastEffects)`. Settles ≈ 218 ms at 2.625× | `fadeIn(defaultEffects) togetherWith fadeOut(fastEffects)` | `EnterTransition.None togetherWith ExitTransition.None` |
| Pop (Back, Up) | mirror of push: enter from `−dir·shiftPx`, exit to `+dir·shiftPx` | fade only | none |
| Predictive pop | §3.19 | §3.19 | none |

### 3.18 Sheets, dialogs and snackbars

Dialogs and `SnackbarHost` keep platform and M3 default fades at every in-app level; sheets follow `gameMotionScheme(level)` (D-184).

- **Sheets** (`GameModalSheet`, `DESIGN_SYSTEM.md` §7.4): motion comes from the theme `MotionScheme` (§2.2).
  - Full: show with Expressive defaultSpatial 0.8/380, hide with fastEffects, scrim with defaultEffects.
  - Reduced: show snaps; hide uses the effects spring; scrim fades.
  - Off: everything snaps.
- **Dialogs** (`ConfirmDialog`, `ChoiceDialog`) are platform dialog windows. They keep the platform's enter and exit window animations at every in-app level. These are opacity fades, not spatial motion, and the platform disables them when the system animator scale is 0. The app never calls `setWindowAnimations` on a dialog window.
- **Snackbars:** `GameSnackbarHost` uses M3's `SnackbarHost` unchanged at every in-app level, including its built-in fade and its accessibility-aware timeout. `SnackbarHost`'s fade does not read `MotionScheme` (verified 2026-10-28: `SnackbarHostKt` has no `MotionScheme` reference, only fixed tween constants).

### 3.19 Predictive back

| Surface | Behavior |
|---|---|
| Game root | Never intercepted (D-094). The system draws its back-to-home animation; the app draws nothing |
| Sub-screens | `predictivePopTransitionSpec { edge -> … }`. Nav3 seeks it with the gesture progress, completes it on release and reverses it on cancel. Full: exiting screen `scaleOut(targetScale = 0.90f, tween(300, LinearEasing)) + slideOutHorizontally(tween(300, LinearEasing)) { s·shiftPx } + fadeOut(tween(300, easing = EmphasizedAccelerate))`; entering screen `fadeIn(tween(300, LinearEasing))`. s = +1 for `NavigationEvent.EDGE_LEFT` (0), −1 for `EDGE_RIGHT` (1), 0 for `EDGE_NONE` (2) (constants verified 2026-10-28). Linear tweens make the visual progress linear in gesture progress (at progress 0.5 the exiting screen is at scale 0.95). Reduced: `fadeIn(tween(300, Linear)) togetherWith fadeOut(tween(300, Linear))`. Off: none |
| Sheets | material3's built-in predictive back (sheet scales with progress). It is direct manipulation, not animation, so it is the same at every level |
| Dialogs, cards | Dialogs dismiss on Back with no preview. Cards do not intercept Back (D-054) |

---

## 4. Interruption semantics and visual-consistency invariants

### 4.1 What happens on a new commit (snap-then-animate, D-052, D-062)

On F0 of board change k+1, in this order:
1. **The presenter drops every ghost of change k** from the new `BoardUi`, so their nodes leave composition on F0.
2. **Positions.** Every persisting tile gets a new `TileUi` (`from` = its change-k target), so its position effect restarts. It calls `pos.snapTo(from)`. If `to != from`, it then calls `animateTo(to, slide)`, which starts at F1.
3. **Pending entrances snap.** If a persisting tile's `alpha.value == 0f` (its spawn or merge reveal has not started), it gets `scale.snapTo(1f)` and `alpha.snapTo(1f)`. That cancels the pending pop or spawn: the tile is about to slide and must be visible.
4. **Started entrances keep running.** A spawn or pop with alpha > 0 continues on its own curve while the tile slides, because scale and alpha are layer effects. Sheen and glow pulse continue too.
5. **Board nudge** snaps to `Offset.Zero`.
6. **Effects keep running:** "+N" floats (max 3), ring, confetti, sparkles, the new-best moment and the odometer's running transitions.
7. **Scheduled timeline work of change k:**
   - a pending score-display update is replaced by k+1's;
   - pending haptics follow §6.2: priority ≤ 2 fires on k+1's F1 (when k+1 publishes its origin), priority ≥ 3 is dropped;
   - pending overlay work is cancelled only by undo, new game or size switch, because a won or over board cannot take a move.

### 4.2 Element × new commit

| In flight when change k+1 commits | Move | Undo / New game / Size switch |
|---|---|---|
| Slide | snap to target, then the new slide | snap (tiles are replaced or crossfaded) |
| Ghost | removed on F0 | removed on F0 |
| Spawn / merge reveal not started (alpha 0) | snap to scale 1, alpha 1 | replaced |
| Spawn / pop / sheen / glow pulse started | keeps running | replaced (outgoing tiles fade) |
| Nudge | snap to 0 | snap to 0 |
| "+N" floats | keep running (cap 3) | removed |
| Odometer | rolls to the newest value at the new +80 ms | undo: reverse roll at origin; others: instant |
| New-best moment | keeps running | undo: keeps running; new game and size switch: removed |
| Ring, confetti | (cannot happen: a won board is blocked) | removed on F0 |
| Overlay card, scrim, desaturation, deflate | (cannot happen: blocked) | card exit (§3.12) |

**How the header tells the columns apart** (D-062, `ARCHITECTURE.md` §4.7, §4.11). The board reads `BoardUi.event`; the header has its own flow, so `HeaderUi` carries `event` (a `BoardEventKind`: `Restored`, `Moved`, `Undo`, `NewGame` or `SizeSwitched`) and `epoch`, copied from the `BoardUi` emitted with it. `GameHeader` removes the running "+N" floats when the presented `event` is not `Moved` or its `epoch` changed, and the new-best moment when the `epoch` changed, unless the `event` is `Undo` and the epoch advanced by exactly one. The epoch catches a reset that the conflated header flow skipped because a move followed it before the next frame: its floats and its moment are removed as well. Proved by AC-SCORE-4 and AC-SCORE-5.

### 4.3 Invariants (tested in §9.12)

| ID | Invariant |
|---|---|
| I1 | **No tile overlap during moves.** On every frame of a `Moved` change, any two drawn tiles with alpha ≥ 0.05 have disjoint rectangles. Moved, Static, Ghost and Merged tiles use their unscaled rects; Spawned tiles are scaled about the cell centre. The allowed overlaps are inside one merge group (a merged tile and its two ghosts) and between a merged tile and a Moved or Ghost tile of the same change that is still sliding towards its own cell: the reveal at +80 precedes the slide's end, and the merged tile is drawn above sliders (I7, D-225). Pop overflow is bounded by §3.3's cap and is not counted |
| I2 | **Visual order equals model order.** Along the move axis, the order of tiles in any row or column never changes during a slide (all tiles share one spring and one start frame) |
| I3 | **The final frame equals the engine state.** After a change has settled (§9 settle bounds), every tile's `pos` equals its engine cell exactly. The rendered board bitmap equals the bitmap of the same state rendered from `BoardEvent.Restored` |
| I4 | **No stale content.** On F0 of change k+1, nothing created by change k−1 or earlier is drawn, except the effects listed as "keep running" in §4.2 |
| I5 | **Celebrations match the state.** Ring, confetti, win card and game-over card exist only while the status is `WON` or `OVER`, or during their ≤ 200 ms exit. An undo or new game removes ring and confetti on F0 |
| I6 | **Input never waits.** No input is queued, delayed or dropped for animation (D-052). Blocked outcomes are no-ops without feedback |
| I7 | **Z-order.** Ghost and outgoing tiles < Static/Moved < Merged < Spawned < `BoardEffectsCanvas` < scrim < `ContentEffectsCanvas` < card (`DESIGN_SYSTEM.md` §4.7) |

---

## 5. Reduced and Off motion; system animator scale

### 5.1 Per-event matrix

| Event | Full | Reduced | Off |
|---|---|---|---|
| Slide | `slide` spring | instant | instant |
| Merge | reveal at 80 + pop | 120 ms alpha crossfade | instant |
| Spawn | +90–115 ms scale and alpha | alpha `fastEffects` from 0 | instant |
| "+N" | rise + hold + fade at +80 | in-place 400 ms fade | none |
| Odometer | per-digit roll at +80 | instant | instant |
| New best | label slide, color crossfade, star, sparkles | label swap + color crossfade | instant |
| Invalid move | nudge spring | outline pulse 0 → 0.6 → 0 / 300 ms, ≤ 1 per 400 ms | none |
| New game | stagger shrink-fade, then 2 spawns | outgoing fade, incoming fade | instant |
| Undo | changed-cell crossfade + icon spin | changed-cell crossfade | instant |
| Size switch | board fade | board fade | instant |
| Win | pop, ring, confetti, card at T0 + 600, interactive at +880 | 150 ms fade, static badge, interactive at 150 | end state at once |
| Game over | desaturate, ripple, card at T0 + 500, interactive at +1000 | 150 ms fade, tiles at 0.94, interactive at 150 | end state at once |
| Card exit | fade + scale + scrim | 150 ms fade | instant |
| Theme switch | `slowEffects` crossfade | instant | instant |
| Layout bounds within one arrangement | `animateBounds` | snap | snap |
| Arrangement switch (§3.16) | none: the board is composed anew and draws its settled frame | same | same |
| Screens | shared-axis slide + fade | fade | none |
| Predictive back | scale + shift + fade, seeked | fade, seeked | none |
| Sheets (`gameMotionScheme(level)`, D-184) | M3 Expressive | show snaps, hide springs, scrim fades | all snap |
| Dialogs (D-184) | platform enter and exit fades | same | same (none when the system animator scale is 0) |
| Snackbars (D-184) | M3 `SnackbarHost` fade | same | same |
| Haptics | Full timings (§6) | +0 (win +150) | +0 (win +150) |

No infinite animation exists at any level. The longest one-shot is the 5 s badge rotation, which stops (WCAG 2.2.2). Nothing flashes more than 3 times per second: the sheen and glow-pulse limiter, the 400 ms outline pulse limit, and no strobing effects anywhere (WCAG 2.3.1).

### 5.2 What the system animator scale does, and what this app must do itself

`WindowRecomposer` exposes `Settings.Global.ANIMATOR_DURATION_SCALE` as `MotionDurationScale` in the coroutine context. At 0 every Compose animation jumps to its end on the first frame (research/04 §6.1, `SuspendAnimation.kt`).

| Mechanism | Scale s > 0 | Scale 0 |
|---|---|---|
| `Animatable.animateTo`, `animate*AsState`, `Transition`, `AnimatedContent`, `AnimatedVisibility`, `animateBounds` | durations × s (automatic) | jump to end (automatic); also effective `OFF` |
| Delays inside specs (`tween(delayMillis)`, `snap(delayMillis)`, `delayedSpec`) | × s (automatic) | 0 |
| `MoveTimeline.awaitOffset` | × s (implemented in it) | returns at once |
| `MoveTimeline.awaitRealTime` (Reduced/Off haptics) | unscaled | unscaled |
| Confetti and sparkle `withFrameNanos` loops | `dt / s` (implemented) | never start; a running loop ends next frame and clears |
| Haptic offsets under Full | × s (via `awaitOffset`) | — (Full is impossible at scale 0) |
| Rate limits (Reject 400 ms, flash 3/1000 ms, key repeat 120 ms) | unscaled | unscaled |
| M3 components (`MotionScheme` specs) | × s (automatic) | jump (automatic) |
| Platform dialog window animations | system-controlled | none |
| Raw `kotlinx.coroutines.delay` | **forbidden** for anything visible (T1) | — |

**Example at s = 2.0** (developer option "Animator duration scale 2x"): reveal at 160 ms; spawn at 180 ms; ghosts hidden at 400 ms; "+N" and odometer at 160 ms; `SegmentTick` at 160 ms; confetti emitted at 400 ms, its particles following the scale-1 trajectories at half speed (same seed; they differ only by the integration step, AC-SCALE-3); win buttons interactive at 1760 ms.

---

## 6. Haptics

### 6.1 Map

Compose `HapticFeedbackType` names were verified 2026-10-28 (class files) in ui 1.12.1. The full set is `Confirm`, `ContextClick`, `GestureEnd`, `GestureThresholdActivate`, `KeyboardTap`, `LongPress`, `Reject`, `SegmentFrequentTick`, `SegmentTick`, `TextHandleMove`, `ToggleOff`, `ToggleOn`, `VirtualKey`.

`PlatformHapticFeedback` maps each type to its `HapticFeedbackConstants` value and calls `ViewCompat.performHapticFeedback`. core 1.19.1's `HapticFeedbackConstantsCompat.getFeedbackConstantOrFallback` then substitutes:
- below API 34: 21 → 6, 22 → 4, 23 → 6, 26 → 6, 27 → 4;
- below API 30: 16 → 1, 17 → 0.

Both mappings were verified 2026-10-28 (class files). So the "API 24–29 fallback" column below is applied automatically. The app passes only the Compose type.

| Priority | Event | Compose type | API ≥ 34 | API 30–33 | API 24–29 | Full timing (from origin) | Reduced / Off timing | Limit |
|---|---|---|---|---|---|---|---|---|
| 1 | Win | `Confirm`, then `Confirm` | `CONFIRM` (16) ×2 | `CONFIRM` (16) ×2 | `VIRTUAL_KEY` (1) ×2 | 120 and 270 ms (frames 128, 272), × scale | +0 and +150 ms real time | once per win |
| 2 | New best (D-044) | `GestureThresholdActivate` | `GESTURE_THRESHOLD_ACTIVATE` (23) | `CONTEXT_CLICK` (6) | `CONTEXT_CLICK` (6) | 80 ms | +0 | once per game |
| 3 | Max merged value ≥ 512 (`maxMergedExp ≥ 9`) | `Confirm` | 16 | 16 | 1 | 80 ms | +0 | — |
| 4 | ≥ 1 merge | `SegmentTick` | `SEGMENT_TICK` (26) | `CONTEXT_CLICK` (6) | `CONTEXT_CLICK` (6) | 80 ms | +0 | — |
| 5 | Accepted move, no merge | none | | | | | | |
| separate | Game over | `Reject` | `REJECT` (17) | `REJECT` (17) | `LONG_PRESS` (0) | 300 ms (frame 304); Ft after Keep going → `OVER` (§3.12) | +0 | once per entry into `OVER` (D-185) |
| separate | Invalid move | `Reject` | 17 | 17 | none (D-185) | on the nudge's start frame, its first animation frame (`nudgeOriginMs`; D-224) | same | ≤ 1 per 400 ms real time |
| separate | Settings switch turned on | `ToggleOn` | `TOGGLE_ON` (21) | `CONTEXT_CLICK` (6) | `CONTEXT_CLICK` (6) | on toggle | same | — |
| separate | Settings switch turned off | `ToggleOff` | `TOGGLE_OFF` (22) | `CLOCK_TICK` (4) | `CLOCK_TICK` (4) | on toggle | same | — |
| — | Undo, New game, other buttons, Blocked outcomes | none | | | | | | |

- **Priority.** Priorities 1–5 pick **at most one** haptic per accepted move, first match wins (D-065). The pure `game/feedback/HapticsMap` (M2) resolves them in `GameViewModel` and ships the result as `BoardEvent.Moved.haptic` (`MoveHaptic`: `WIN`, `NEW_BEST`, `BIG_MERGE`, `MERGE`, `NONE`; D-185). "Separate" events are independent: a move can fire `SegmentTick` at 80 ms and `Reject` (game over) at 300 ms.
- **Below API 30** the `Reject` fallback is `LONG_PRESS`, the strongest predefined effect (verified 2026-10-28, core 1.19.1). Invalid moves therefore fire **no** haptic below API 30, while the game-over `Reject` keeps its `LONG_PRESS` fallback on every API level (D-185).
- **The Haptics switch itself** fires `ToggleOn` when turned on. Turning it off fires nothing, because the post-change setting is evaluated.
- **No double haptics.** material3 1.5.0-beta02 `Switch` and foundation 1.12.1 `toggleable`/`clickable` perform no haptics of their own (verified 2026-10-28: only `TimePicker` references haptics). The app's `ToggleOn`/`ToggleOff` is therefore the only pulse.

### 6.2 Implementation rules

- **One wrapper.** `game/feedback/HapticsController.kt` holds the `HapticsController` class (M3 part 1), created in `GameScreen` from `LocalHapticFeedback.current`, the Haptics setting, `MoveTimeline`, `MonotonicClock` and `LocalEffectiveMotion`. Nothing else calls `performHapticFeedback` (D-066, D-185).
- **Scheduling.** `onBoardChange(board)` schedules `board.event.haptic`, which `GameViewModel` resolved with `HapticsMap` (D-185); `HapticsController` does not compute priority. It schedules with `awaitOffset` (Full) or `awaitRealTime` (Reduced/Off), keyed to `board.moveId` (§2.4). It also schedules the separate events of §6.1 (game over from `OverlayController`, invalid moves from `nudge?.id`). Its inputs are the presented values of §3.14: a composition created after a presentation was reported sees a `Restored` board and card entry and schedules nothing for them, it ignores the nudge that existed at its first composition, and haptics pending in a disposed composition are cancelled with it, never fired later (D-047).
- **Interruption** (D-185). When a newer change publishes its origin before a scheduled haptic fires:
  - priority 1–2 haptics (win, new best) fire immediately on the newer change's F1;
  - priority 3–4 haptics are dropped;
  - a pending game-over `Reject` is dropped (only undo or new game can interrupt it, and they revert the state).
- **Rate limit.** Invalid-move `Reject` fires only on API ≥ 30 and only if `clock.now() − lastInvalidReject ≥ 400 ms`. The clock is `MonotonicClock`, unscaled (T4).
- **Platform rules.** Never use `Vibrator`, `VibrationEffect`, `VIBRATE` or `FLAG_IGNORE_GLOBAL_SETTING` (D-002, D-066). The system touch-feedback setting is honored by the platform.
- **Tests.** `game/feedback/HapticsMapTest` covers the pure map (M2) and the controller (M3). It provides `FakeHapticFeedback` (`testing/FakeHapticFeedback.kt`) through `CompositionLocalProvider(LocalHapticFeedback provides fake)`; the fake records `(type, mainClock.currentTime − origin)`.

---

## 7. Performance budgets and measurement

### 7.1 Frame budgets (device; documented, not gated, D-150)

| Refresh rate | Frame period | UI-thread target per animation frame | Rule |
|---|---|---|---|
| 60 Hz | 16.67 ms | ≤ 6.0 ms P95 | `frameOverrunMs` P99 ≤ 0 |
| 90 Hz | 11.11 ms | ≤ 6.0 ms P95 | same |
| 120 Hz | 8.33 ms | ≤ 6.0 ms P95 (`frameDurationCpuMs`, D-150) | same |

The confetti frames get the same targets, with the particle system costing ≤ 1.15 ms of them (§7.5).

### 7.2 Recomposition budgets (JVM-gated)

| Composable | Budget per accepted move (no interruption) | Gate |
|---|---|---|
| `TileView` (each key; `onTile(id)`) | ≤ 1: the F0 composition when its `TileUi` changed or the tile is new. **0 on every later frame until the next change** | `TileRecompositionTest` (D-150) |
| `BoardView` (`onBoard()`) | ≤ 2: F0 (new `BoardUi` + frame-rate High) and idle (frame-rate Default). Ghost hiding is an alpha snap, not a recomposition. The settled board that follows the presentation report (§3.14) causes none, because its arrangement scope's `rememberPresented` state keeps the presented instance | `BoardRecompositionBudgetTest` |
| `GameScreen` root (`onScreen()`) | 0 (it reads no per-move state: it collects the D-024 flows and makes its screen scope's `rememberPresented` `State`s, `ARCHITECTURE.md` §4.11, and only its children read their values) | `BoardRecompositionBudgetTest` |
| `ScoreChip` | 1 when `displayedScore` changes, plus `AnimatedContent`'s own internal compositions | not gated |
| Theme crossfade | M3 consumers recompose for ≈ 19 frames (§3.15) | exempt |

Counting uses D-150's test-only `LocalRecompositionCounter` (default `null`), declared with `interface RecompositionCounter { fun onTile(id: Long); fun onBoard(); fun onScreen() }` in `game/board/RecompositionCounter.kt`. `TileView` calls `LocalRecompositionCounter.current?.onTile(id)`, `BoardView` calls `?.onBoard()` and `GameScreen` calls `?.onScreen()`, each from a `SideEffect`. They take no string arguments, so production does no allocation.

### 7.3 Allocation-free animation frames

These rules apply to code that runs on every animation frame: layout/draw lambdas, `graphicsLayer {}` blocks, `withFrameNanos` bodies and `ConfettiSystem.step`.

| Forbidden in a frame | Use instead |
|---|---|
| `Path()`, `Stroke(…)`, `Paint()`, `ColorMatrix()`, `ColorFilter`, `Brush` created per frame | Objects remembered once. For the ring stroke: `drawIntoCanvas { it.nativeCanvas.drawRoundRect(…, paint) }` with one remembered `android.graphics.Paint` whose `strokeWidth` and `alpha` are mutated |
| `ColorMatrix().setToSaturation(s)` as a `ColorFilter` | Arithmetic desaturation on value-class `Color` (below) |
| Text measurement | `TextLayoutResult` cached per `(exp, targetTilePx, paletteId)` (D-025) and `drawText(layout, color = …)` |
| Collections, boxing, lambdas capturing per-frame values, string building | `FloatArray`/`ByteArray` fields; inline `withTransform`; value classes (`Offset`, `IntOffset`, `Size`, `CornerRadius`, `Color`) |

**Desaturation formula.** It equals `ColorMatrix.setToSaturation`, so screenshot results match either implementation:

```kotlin
fun Color.desaturate(s: Float): Color {
    val inv = 1f - s
    val r0 = 0.213f * inv; val g0 = 0.715f * inv; val b0 = 0.072f * inv
    return Color(
        red = (r0 + s) * red + g0 * green + b0 * blue,
        green = r0 * red + (g0 + s) * green + b0 * blue,
        blue = r0 * red + g0 * green + (b0 + s) * blue,
        alpha = alpha,
    )
}
```

### 7.4 Frame-rate voting

- Translation goes through `offset`/layout placement, never `graphicsLayer { translationX }`. On Android 15+ adaptive-refresh devices, only placement changes vote `FrameRateCategory.High` (research/04 §4.2).
- While any board animation runs, `BoardView` also applies `Modifier.preferredFrameRate(FrameRateCategory.High)`, and `FrameRateCategory.Default` when idle (§2.5).

### 7.5 Event budgets

| Event | Budget |
|---|---|
| Move (16 tiles + up to 16 ghosts on 4×4; up to 128 nodes on 8×8) | No layout or text measurement on animation frames; only placement, layer and draw |
| Confetti | ≤ 160 particles. `step` ≤ 0.15 ms and draw ≤ 1.0 ms on device. JVM gate: after 20,000 warm-up steps, the fastest of 5 batches of 10,000 steps of 160 particles takes ≤ 200 ms of the thread's CPU time, with a new burst every 132 steps (D-230, D-235); 10,000 steps allocate ≤ 1 KiB after warm-up |
| Game over | Desaturation is arithmetic per tile in draw; the ripple is ≤ N² layer-scale animations |
| Theme crossfade | ≤ 19 frames of chrome recomposition |

### 7.6 How to measure

| Where | Tool | Gated |
|---|---|---|
| JVM (CI) | `TileRecompositionTest`, `BoardRecompositionBudgetTest`, `ConfettiSystemTest` (time and allocation via `com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes()`, JDK 21), `MotionTokensTest` | Yes, through `verify.sh` |
| Device, zero code | Install the CI **release** APK (R8-minified, so timing is representative; D-132). Turn on Developer options → "Profile HWUI rendering" → "On screen as bars". Run `adb shell dumpsys gfxinfo com.clayboicardi.game2048 reset`, play 100 swipes, then run `adb shell dumpsys gfxinfo com.clayboicardi.game2048` and read "Janky frames" and the 50th/90th/95th/99th percentiles | No |
| Device, trace | Perfetto system trace with the `gfx`, `view` and `input` categories; check the FrameTimeline track for frames overrunning their deadline during a slide, a win and a game over | No |
| Device, debug overlay | A JankStats overlay needs `androidx.metrics:metrics-performance:1.0.0`, which is not in the D-016 catalog, so it is **not built** in v1.0; the zero-dependency tools above cover device checks | No |
| Device, benchmark | Macrobenchmark, manual and out of tree (D-153: no `:baselineprofile` or macrobenchmark module in v1.0). Recipe below | No |

**Macrobenchmark recipe** (a scratch branch, never merged):
1. Add a `com.android.test` module with `androidx.benchmark:benchmark-macro-junit4:1.5.0` and `androidx.test.uiautomator:uiautomator:2.4.0`.
2. Add a `benchmark` build type to `:app`: `initWith(release)`, debuggable false, signed with `ciSideload`.
3. Write this test:

```kotlin
@Test fun swipes() = benchmarkRule.measureRepeated(
    packageName = "com.clayboicardi.game2048",
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
) {
    startActivityAndWait()
    val w = device.displayWidth; val h = device.displayHeight
    repeat(100) { i ->
        when (i % 4) {
            0 -> device.swipe(w * 3 / 4, h / 2, w / 4, h / 2, 8)
            1 -> device.swipe(w / 2, h * 3 / 5, w / 2, h * 2 / 5, 8)
            2 -> device.swipe(w / 4, h / 2, w * 3 / 4, h / 2, 8)
            else -> device.swipe(w / 2, h * 2 / 5, w / 2, h * 3 / 5, 8)
        }
        Thread.sleep(150)
    }
}
```

4. Pass when `frameDurationCpuMs` P95 ≤ 6.0 and `frameOverrunMs` P99 ≤ 0 on a Pixel 6a-class device at 120 Hz.

---

## 8. Verifying motion without a device

No device or emulator exists in CI (D-153). Motion is verified on the JVM with Robolectric 4.17 at SDK 36, `@GraphicsMode(NATIVE)`, the Compose v2 test rule and a manual clock. Three layers:
1. **numeric probes** at fixed virtual times (the gate);
2. **the D-147 golden filmstrip** (gate, exact pixels);
3. **inspection filmstrips** written to `build/reports` that the agent must Read (not a pixel gate).

### 8.1 Harness

`app/src/test/kotlin/com/clayboicardi/game2048/motion/MotionHarness.kt`:

```kotlin
class MotionHarness private constructor(val rule: ComposeContentTestRule, val probe: MotionProbe, val haptics: FakeHapticFeedback, val vm: GameViewModel) {
    var originMs = 0L; private set
    /** Applies the move synchronously (D-052), then advances to its origin F1 (§0.1). */
    fun commitMove(direction: Direction) = act({ vm.onMove(direction) }) { probe.originMs }
    fun commitUndo() = act({ vm.onUndo() }) { probe.originMs }
    /** Starts the new game directly; the screen's confirmation dialog is not involved (§3.8). */
    fun commitNewGame() = act({ vm.onNewGame() }) { probe.originMs }
    /** Invalid moves change no board, so the origin is the nudge's first animation frame. */
    fun invalidMove(direction: Direction) = act({ vm.onMove(direction) }) { probe.nudgeOriginMs }
    /** Card buttons: the origin is Ft, OverlayController's play-time-0 frame after the tap (§3.12). */
    fun tapCardButton(label: String) = act({ rule.onNodeWithText(label).performClick() }) { probe.overlayActionOriginMs }
    /** Runs [action], then advances one frame at a time (at most 3) until [published] reports a new origin
     *  equal to the current frame time. Never edit expectations to fit a different origin. */
    private fun act(action: () -> Unit, published: () -> Long?) {
        val before = published()
        action()
        repeat(3) {
            rule.mainClock.advanceTimeByFrame()
            val p = published()
            if (p != null && p != before) {
                check(p == rule.mainClock.currentTime) { "origin $p is not the current frame" }
                originMs = p
                return
            }
        }
        error("no origin published within 3 frames")
    }
    /** Advances to the first 16 ms grid frame >= origin + t. */
    fun advanceTo(tMs: Long) {
        val target = originMs + ((tMs + 15) / 16) * 16
        rule.mainClock.advanceTimeBy(target - rule.mainClock.currentTime)
    }
    /** Disposes the whole content and composes it again over the same vm, container, probe and haptics fake, which is
     *  what ActivityScenario.recreate() does to the activity-scoped GameViewModel (ARCHITECTURE.md §4.11, §3.14).
     *  It saves the content's rememberSaveable state through a SaveableStateRegistry of its own, disposes the content and
     *  composes it again restoring that state, advancing one frame per step (StateRestorationTester's
     *  emulateSavedInstanceStateRestore() waits for idle between dispose and restore, which never composes while
     *  autoAdvance is false; D-229). Returns on R0, the first frame of the new content; the probe then reads the new
     *  composition. autoAdvance stays false. */
    fun recreate()
    companion object {
        fun launch(rule: ComposeContentTestRule, fixture: GameFixture, motion: MotionPreference,
                   systemScale: Float = 1f, widthDp: Int? = null): MotionHarness
        // Builds AppContainer on TemporaryFolder files with fakes: MonotonicClock = rule.mainClock.currentTime,
        // SeedSource fixed, AnimatorScaleSource(systemScale), writes the fixture, sets content through
        // StateRestorationTester(rule).setContent with LocalMotionProbe and LocalHapticFeedback (FakeHapticFeedback)
        // provided, sets mainClock.autoAdvance = false, and advances frames until probe.boardLoaded.
    }
}
```

- Tests use `createComposeRule(effectContext = …)` (v2, D-146). The v2 signature with `effectContext: CoroutineContext` was verified 2026-10-28 (class files).
- For system-scale tests, pass `effectContext = object : MotionDurationScale { override val scaleFactor = s }` **and** `systemScale = s`, so Compose's scale and the app's effective level agree.
- `MainTestClock` provides `autoAdvance`, `advanceTimeByFrame()`, `advanceTimeBy(ms, ignoreFrameDuration = false)` (rounds up to whole 16 ms frames) and `advanceTimeUntil` (verified 2026-10-28).
- **Snapshot apply notifications.** With `autoAdvance` false, a state write made outside composition (the ViewModel's flows, the back stack, `recreate()`'s content switch) reaches the next frame only once the global snapshot's apply notifications are sent. The harness idles the main looper and then calls `Snapshot.sendApplyNotifications()` after a gesture, after a navigation action, between `recreate()`'s steps and while it waits for an origin, so the frame on which such a write appears does not depend on what earlier test classes in the same JVM left in Compose's notification scheduling. `staticRender()` keeps the event's `originMs`, and each test's cleanup settles the animations still running.
- **Off-level tests** check F0 itself (T5). They call the ViewModel and then `rule.mainClock.advanceTimeByFrame()` once, instead of `act`. A board change under Off still publishes its origin on F1 (haptics use it), but an invalid move under Off publishes no nudge origin.

### 8.2 `MotionProbe` (test hook, like D-150's counter)

```kotlin
// game/motion/MotionProbe.kt (main source set; production never provides one)
val LocalMotionProbe = staticCompositionLocalOf<MotionProbe?> { null }
```

Components register value readers in a `DisposableEffect` only when the probe is non-null, so the production cost is a null check at composition. Tests read `probe.snapshot()`.

| Field | Content |
|---|---|
| `originMs`, `nudgeOriginMs`, `overlayActionOriginMs`, `overlayOriginMs`, `paletteOriginMs`, `navOriginMs` | Latest `MoveTimeline` origin. First frame of the latest nudge or Reduced outline pulse (from the `animateTo` block's `startTimeNanos`). Ft of the latest card action (§3.12). O of the current overlay (§3.11, §3.13). First frame of the latest palette crossfade (§3.15). First frame of the latest `NavDisplay` transition (§3.17) |
| `boardLoaded`, `isAnyRunning` | First board composed; any registered `Animatable.isRunning` or alive particle |
| `tiles` | `id`, `exp`, `kind` (the `TileUi` kind), `outgoing: Boolean` (true for a previous epoch's tile drawn by `OutgoingTilesLayer`, D-182), `x`/`y` (cells), `scale`, `alpha`, `sheen`, `glowBoost` |
| `boardNudgeDp`, `outlinePulseAlpha`, `boardSaturation`, `boardFrameRate` | Board-level values |
| `floats` | Per "+N": `text`, `alpha`, `translationYDp`, `scale` |
| `displayedScore`, `displayedBest`, `bestChip` | Odometer values; label key, container fraction, star scale/rotation/alpha, sparkles alive |
| `ring`, `confettiAlive`, `scrimAlpha` | Ring `scale`/`strokeDp`/`alpha`; alive particle count; the scrim's alpha, also while no card is composed (`null` without a scrim, D-242) |
| `overlay` | `kind` (WIN/OVER), `scrimAlpha`, `cardAlpha`, `cardScale`, `buttonAlphas`, `interactive` (`OverlayController.interactive`, D-200; buttons are always composed enabled), `badgeMorph`, `badgeScale`, `badgeRotationDeg` |
| `paletteFraction`, `undoIconRotationDeg` | Theme crossfade fraction; undo icon |

### 8.3 Golden filmstrip (D-147; `DESIGN_SYSTEM.md` §10.4 rows 37–41)

`screenshots/MotionFilmstripTest.mergeAndSpawnFilmstrip` (F13-AC8, AC-GOLD-1):
- Pixel7, Classic light, Motion **Full**, fixture `FIX_FILM`, move LEFT.
- Each frame is captured with `onRoot().captureRoboImage("src/test/screenshots/$name.png")` through `captureGolden(name)`.
- Frame times come from `advanceTo(t)` on the 16 ms grid. The golden names keep their nominal values t000/t040/t080/t160/t400, and they capture the frames at 0, 48, 80, 160 and 400 ms from the origin (D-204).

| Golden | Frame (ms from origin) | Expected content (Pixel 7: pitch 241.2 px, tile 211.2 px; positions in cells) |
|---|---|---|
| `…_t000` | 0 | Identical to the pre-move board: 2 at row 0 column 0, 2 at row 0 column 1, 4 at row 0 column 3. Merged and spawn invisible |
| `…_t040` | 48 | Slide progress 0.5700: ghost 2 at x = 0.430; the 4 at x = 1.860; ghost 2 at x = 0 |
| `…_t080` | 80 | Progress 0.8475: the 4 at x = 1.305; merged 4 revealed at row 0 column 0, scale 1.000; spawn still invisible |
| `…_t160` | 160 | The 4 at x = 1.0017; merged scale 1.0663 (v0 = 6, 80 ms into the pop); spawn 2 at row 1 column 0 at scale 0.9102, alpha 0.9290 |
| `…_t400` | 400 | Visually settled: pop ended at 368; spawn at scale 0.9974 (ends at 415) |

`FIX_FILM`'s spawn comes from the D-038 algorithm with `rngState = 0x2048`: the value draw `nextInt(10)` = 8 gives exponent 1 (value 2); the cell draw `nextInt(14)` = 2 over the empty list `[2, 3, 4, …, 15]` gives **index 4, row 1, column 0** (computed 2026-10-28).

### 8.4 Inspection filmstrips (not goldens; agent must Read)

- **Helper:** `writeFilmstrip(name, frames)` in `MotionHarness`.
- **Capture:** each frame with `captureToImage()` on the region named in the table:
  - Board: the node with `CollectionInfo`;
  - Header: the score row;
  - Root: `onRoot()`, downscaled 50 %.
- **Strip format:** frames concatenated left to right with 8 px gaps. Each frame is labelled "t=NNN" with `android.graphics.Paint` text.
- **Output:** `app/build/reports/filmstrips/<TestClass>/<name>.png`. CI uploads them with the reports artifact (D-151 `**/build/reports/`).
- **Read rule** (D-210): M3 Reads every strip once (`theme_crossfade` and `hint_exit_bounds` arrive with their tests in M4, which Reads them once). A later commit that changes motion code (`ui/theme/GameMotion*.kt`, `ui/theme/EffectiveMotion.kt`, `game/motion/**`, `game/board/Board.kt`, `game/board/BoardAnimator.kt`, `game/board/EffectsCanvas.kt`, `game/overlay/**`, `game/header/**`) Reads only the strips of the events whose behaviour changed. Every Read strip is listed in the commit body under `Screenshots:` (D-164).

| Strip | Test class | Fixture → action | Level | Region | Frames (ms) |
|---|---|---|---|---|---|
| `move_merge_spawn` | `MoveTimelineTest` | `FIX_FILM` → LEFT | Full | Board | 0, 16, 32, 48, 64, 80, 96, 128, 160, 208, 256, 320, 432 |
| `pop_tiers` | `MergePopTest` | `FIX_POP_TIERS_4` → LEFT | Full | Board | 64, 80, 96, 112, 128, 160, 208, 288, 432 |
| `long_slide_8x8` | `SpawnDelayTest` | `FIX_LONG_SLIDE_8` → RIGHT | Full | Board | 0, 48, 96, 112, 128, 144, 160, 240, 448 |
| `nudge_left`, `nudge_up` | `InvalidNudgeTest` | `FIX_INVALID_4` → LEFT / UP | Full | Root | 0, 16, 32, 48, 64, 112, 192, 352 |
| `plus_n_float` | `ScoreMotionTest` | `FIX_POP_TIERS_4` → LEFT | Full | Header | 64, 80, 96, 144, 352, 560, 752 |
| `new_best` | `NewBestTest` | `FIX_NEW_BEST_4` → LEFT | Full | Header | 64, 80, 96, 192, 240, 480, 688, 1296, 1440, 1600 |
| `win_sequence` | `WinSequenceTest` | `FIX_WIN_PRE_4` → LEFT | Full | Root | 0, 80, 128, 144, 208, 320, 480, 688, 720, 784, 880, 1200, 2416, 5712 |
| `game_over_sequence` | `GameOverSequenceTest` | `FIX_OVER_PRE_4` → LEFT | Full | Root | 0, 288, 304, 400, 496, 608, 752, 800, 848, 912, 1008, 1200 |
| `keep_going_to_over` | `CardExitTest` | `FIX_WON_NO_MOVES_4` → Keep going | Full | Root | Ft + 0, 96, 208, 512, 704 |
| `undo_crossfade` | `UndoCrossfadeTest` | `FIX_FILM` → LEFT, settle, Undo | Full | Board | 0, 16, 32, 64, 128, 144 |
| `new_game_cascade` | `NewGameCascadeTest` | `FIX_MID_4` → New game | Full | Board | 0, 48, 112, 176, 224, 288, 352, 480, 624 |
| `size_switch` | `SizeSwitchTest` | `FIX_MID_4` → size 3 (`FIX_MID_3` saved) | Full | Board | 0, 48, 96, 208 |
| `rapid_input` | `InterruptionTest` | `FIX_FILM` → LEFT@0, RIGHT@48, LEFT@96 | Full | Board | every 16 ms, 0–560 |
| `reduced_move`, `reduced_invalid`, `reduced_win`, `reduced_over` | `ReducedMotionTest` | as above | Reduced | Board / Root | 0, 16, 64, 128, 160, 304 |
| `off_move`, `off_win`, `off_over` | `MotionOffTest` | as above | Off | Root | F0, 0, 16 |
| `theme_crossfade` | `ThemeCrossfadeTest` | Settings: Classic → Neon | Full | Root | 0, 32, 64, 128, 208, 320 |
| `nav_push_pop` | `NavTransitionsTest` | Game → Settings → Back | Full | Root | 0, 48, 96, 160, 256 |
| `predictive_back` | `PredictiveBackTest` | Settings, back progress 0 / 0.25 / 0.5 / 0.75, then cancel and commit | Full | Root | per progress step |
| `hint_exit_bounds` | `LayoutMotionTest` | First-launch hint → "Got it" | Full | Root | 0, 96, 192, 304, 416 |

### 8.5 Motion test classes

Paths are under `app/src/test/kotlin/com/clayboicardi/game2048/`. All classes are in `motion/` except `game/feedback/HapticsMapTest`, `screenshots/MotionFilmstripTest` and `ui/theme/EffectiveMotionTest`; `motion/DelayedSpecTest`, `MotionTokensTest` and `ConfettiSystemTest` are plain JVM tests (JUnit 4, no Robolectric runner). They add to the D-149 app test floor. All are created in M3 except `ThemeCrossfadeTest` and `LayoutMotionTest` (M4, D-211); `HapticsMapTest` starts in M2 with the pure map.

| Class | Kind | Verifies |
|---|---|---|
| `MotionTokensTest` | JVM | §1.5 token literals exact; Compose's settle estimate within one 16 ms frame of §1.1 (D-210), using Compose's own `spring(…).vectorize(Float.VectorConverter).getDurationNanos(…)` and `Offset.VectorConverter` |
| `DelayedSpecTest` | JVM | `delayedSpec` (§2.3 T1, D-183): value and velocity at play times 0, delay − 1 ns, delay and delay + 16 ms equal the plain spec shifted by the delay; duration = delay + wrapped duration |
| `TimelineOriginTest` | Robolectric | §0.1 origin: at t = 0 the moved tile is at `from`; at t = 16 progress is 0.1255 ± 0.002 |
| `MoveTimelineTest` | Robolectric | §3.1, §3.2; AC-MOVE-* |
| `MergePopTest` | Robolectric | §3.3 tiers, sheen, glow pulse, flash limiter, cap |
| `SpawnDelayTest` | Robolectric | §3.1 delay table; I1 on 8×8 |
| `ScoreMotionTest` | Robolectric | §3.4, §3.5; the header part of §4.2 (AC-SCORE-5) |
| `NewBestTest` | Robolectric | §3.6 |
| `InvalidNudgeTest` | Robolectric | §3.7 (direction-aware, repeat, Blocked) |
| `NewGameCascadeTest` | Robolectric | §3.8 |
| `UndoCrossfadeTest` | Robolectric | §3.9 |
| `SizeSwitchTest` | Robolectric | §3.10 |
| `WinSequenceTest` | Robolectric | §3.11 (compact and `widthDp = 900` expanded) |
| `CardExitTest` | Robolectric | §3.12 |
| `GameOverSequenceTest` | Robolectric | §3.13 |
| `RestoreNoMotionTest` | Robolectric | §3.14: AC-RESTORE-1, AC-RESTORE-2; board cases in M3 part 1, card cases in part 2 (complements D-047's `RecreateNoReplayTest`, whose `MotionHarness.recreate()` cases are FEATURES.md F07-AC26–AC30) |
| `ThemeCrossfadeTest` | Robolectric | §3.15 (M4) |
| `LayoutMotionTest` | Robolectric | §3.16 (M4) |
| `NavTransitionsTest` | Robolectric | §3.17 |
| `PredictiveBackTest` | Robolectric | §3.19 |
| `InterruptionTest` | Robolectric | §4.1, §4.2 |
| `ConsistencyPropertyTest` | Robolectric | I1–I4 over 20 seeded sequences × 15 moves |
| `FinalFrameEqualsStaticTest` | Robolectric | I3 bitmap equality for each event |
| `ReducedMotionTest` | Robolectric | §5.1 Reduced column |
| `MotionOffTest` | Robolectric | §5.1 Off column, T5 |
| `AnimatorScaleTest` | Robolectric | §5.2 (s = 0, s = 2) |
| `game/feedback/HapticsMapTest` | JVM (map, M2) + Robolectric (controller, M3) | §6 (the D-066 name): F14-AC1 `priorityResolution` (M2); AC-HAP-1–3; `invalidMoveSilentBelowApi30` (F14-AC9). The Settings parts of AC-HAP-4 are in `settings/SettingsScreenTest` (M4) |
| `ui/theme/EffectiveMotionTest` | Robolectric | §2.1: `effectiveMotion()`, the live `SystemAnimatorScaleSource`, `LocalEffectiveMotion` provided by `GameTheme` |
| `ConfettiSystemTest` | JVM | Physics, determinism, allocation, step time |
| `BoardRecompositionBudgetTest` | Robolectric | §7.2 (with D-150's `TileRecompositionTest`) |
| `screenshots/MotionFilmstripTest` | Robolectric + Roborazzi | §8.3; method `mergeAndSpawnFilmstrip` (F13-AC8, AC-GOLD-1) |

### 8.6 Motion fixtures

`app/src/test/kotlin/com/clayboicardi/game2048/fixtures/MotionFixtures.kt` sits next to `GoldenFixtures.kt` (`DESIGN_SYSTEM.md` §10.3), which supplies `FIX_FILM`, `FIX_MID_4` and `FIX_MID_3`. Cells are exponents in row-major order with 0 = empty. Spawns were computed 2026-10-28 with the D-034/D-038 algorithm. `ENGINE_SPAWN_CHECK` in each test asserts the computed spawn, so a mismatch fails loudly instead of shifting expectations.

| Fixture | N | Cells | Other fields | Action → expected outcome |
|---|---|---|---|---|
| `FIX_POP_TIERS_4` | 4 | `5,5,0,0, 8,8,0,0, 9,9,0,0, 11,11,0,0` | `KEEP_PLAYING`, `keepGoingAtMove` 400, `moveCount` 500, score 30000, best 40000, `rngState 0x2048` | LEFT: merges 64, 512, 1024, 4096 at column 0; +5696; maxMergedExp 12; spawn exp 1 at index 3 |
| `FIX_LONG_SLIDE_8` | 8 | index 0 = 1, the rest 0 | `PLAYING`, `rngState 0x207E` | RIGHT: a 7-cell slide to index 7; spawn exp 1 at index 6 (right behind it) |
| `FIX_INVALID_4` | 4 | `1,0,0,0, 2,0,0,0`, then 8 × 0 | `PLAYING`, `moveCount` 10 | LEFT and UP are invalid; RIGHT and DOWN are valid |
| `FIX_NEW_BEST_4` | 4 | `2,2,0,0`, then 12 × 0 | `PLAYING`, score 96, `bestAtStart` 100, records best 100, `newBestAnnounced` false, `rngState 0x2048` | LEFT: +8 → 104 > 100, so newBest; spawn exp 1 at index 12 |
| `FIX_WIN_PRE_4` | 4 | `10,10,0,0, 1,0,0,0, 0,0,2,0, 0,0,0,1` | `PLAYING`, score 18000, best 24680, `moveCount` 900, `rngState 0x2048` | LEFT: 2048 at index 0, becameWon; rows 2 and 3 slide 2 and 3 cells; spawn exp 1 at index 3 |
| `FIX_OVER_PRE_4` | 4 | `1,2,1,2, 2,3,2,1, 4,5,6,7, 0,1,2,3` | `PLAYING`, score 3012, best 24680, `rngState 0x2048` | LEFT: only row 3 moves (→ `1,2,3,0`); spawn exp 1 at index 15; becameOver; deflate origin (3,3), mMax 6, step 30 ms |
| `FIX_WON_NO_MOVES_4` | 4 | `11,1,2,1, 1,2,1,2, 2,1,2,1, 1,2,1,2` | `WON`, last spawn index 15 | Keep going → `OVER` at once (D-036) |

---

## 9. Acceptance criteria

Unless stated otherwise: Robolectric SDK 36, Pixel7 qualifiers, Classic light, Motion Full, system scale 1, `MotionHarness`. t is milliseconds from the origin on the 16 ms grid (§0.1). Tolerances: positions ±0.004 cells, scales ±0.002, alphas ±0.01, dp ±0.05, colors as fractions ±0.01. "Not running" means `probe.isAnyRunning == false`.

**Verification policy** (D-210). Event frames are binding; intermediate curve values are reference values checked against Compose's own spec evaluation. The binding event frames are the origin, the reveal, spawn-start and ghost-hide frames, "+N" creation, card compose and interactive frames, haptic frames, settle bounds, recomposition counts and invariants I1–I4; they are never edited to fit an implementation. An expected value at an intermediate frame comes from `spec.vectorize(converter).getValueFromNanos(playTimeNanos, initial, target, initialVelocity)`, with the play time implied by the event frames; if it differs from the number here by more than the tolerance (scale ±0.002, alpha ±0.01), the test keeps the library value and the commit body records both. Harness plumbing may be fixed, with a note in the commit body. A method proving several ACs lists every ID on its first KDoc line.

### 9.1 Move, merge, spawn (`MoveTimelineTest`, `MergePopTest`, `SpawnDelayTest`)

- **AC-MOVE-1** Given `FIX_FILM`, when LEFT commits, then at t = 0:
  - every tile is at its `from` cell;
  - the merged tile has alpha 0;
  - the spawn has scale 0.6 and alpha 0.
- **AC-MOVE-2** Then the 4 (from column 3) is at x = 2.749 at t = 16, 1.860 at t = 48 and 1.305 at t = 80.
- **AC-MOVE-3** Then at t = 64 the merged tile has alpha 0. At t = 80 it has alpha 1 and scale 1.000. At t = 96, 112 and 128 its scale is 1.0716, 1.1021 and 1.1039.
- **AC-MOVE-4** Then at t = 80 the spawn has alpha 0. At t = 96 it has alpha 0.0537 and scale 0.6054.
- **AC-MOVE-5** Then at t = 192 ghosts have alpha 1; at t = 208 ghosts have alpha 0.
- **AC-MOVE-6** Then at t = 288 every non-ghost tile's position equals its engine cell exactly.
- **AC-MOVE-7** Then at t = 432 no board animation is running (every tile at its end values) and `boardFrameRate == Default`. At t = 16 it was `High`. The move's "+N" float still runs until t = 752 (§3.4), so `isAnyRunning` is not checked here (D-229).
- **AC-MOVE-8** Given `FIX_POP_TIERS_4`, when LEFT commits, then at t = 128 (48 ms into the pops) the merged tiles' scales are 1.1039 (64), 1.1385 (512), 1.1732 (1024) and 1.2078 (4096).
- **AC-MOVE-9** Then at t = 80:
  - the 512, 1024 and 4096 tiles have sheen 0.35; the 64 has 0;
  - glowBoost is 0.25 for 512 and 1024 and 0 for 4096 (Classic super tiles are flat).
- **AC-MOVE-10** Given the flash limiter already counted 3 tiles within the last 1000 ms, when one more ≥ 128 merge happens, then that tile has sheen 0 and glowBoost 0, and its pop still runs.
- **AC-MOVE-11** Given `FIX_LONG_SLIDE_8`, when RIGHT commits, then:
  - at t = 112 the spawn has alpha 0 (delay 115);
  - at t = 128 its alpha is > 0;
  - on every frame from 0 to 448, I1 holds between the spawn (alpha ≥ 0.05, scaled rect) and the sliding tile.
- **AC-MOVE-12** Given a 3×3 board with a 720 dp board size (qualifier `w800dp-h1280dp`) and two adjacent 1024 tiles, when they merge, then the 2048's peak scale is ≤ 1 + 2·gap/tile (≤ 1.188).

### 9.2 "+N" and odometer (`ScoreMotionTest`)

- **AC-SCORE-1** Given `FIX_POP_TIERS_4`, when LEFT commits:
  - at t = 64 no float is visible and `displayedScore == 30000`;
  - at t = 80 one float "+5696" has alpha 1, translationY 0, scale 0.8, and `displayedScore == 35696`.
- **AC-SCORE-2** Then the float's animations start at t = 96 (mechanism B). At t = 96 its scale is still 0.8; at t = 144 (48 ms after start) translationY = −15.28 dp; at t = 352 alpha = 0.985; at t = 752 the float is gone.
- **AC-SCORE-3** Given 4 merging moves committed 48 ms apart, then no frame shows more than 3 floats. On the 4th move's F1 the 1st float is gone.
- **AC-SCORE-4** Given a move then an undo, when the undo commits, then:
  - on its F1 all floats are gone;
  - `displayedScore` equals the undone score at t = 0;
  - the changed digits' `AnimatedContent` targets change on the same frame.
- **AC-SCORE-5** (`ScoreMotionTest.resetsRemoveScoreFloats`, with F13-AC15; §3.4, §3.6, §4.2) Given `FIX_NEW_BEST_4`, when LEFT commits, then at t = 160 its "+8" float and the new-best moment are running, and, in separate runs from that point:
  - when Undo commits at t = 160, on the undo's F1 no float exists, while the label key is still `new_best_label` and the star badge is still composed and running;
  - when New game commits at t = 160, on its F1 no float exists, the star badge is not composed and the label key is `best_label`;
  - given also a saved 3×3 game with `newBestAnnounced = false`, when 3×3 is selected at t = 160, on the size switch's F1 no float exists, the star badge is not composed and the label key is `best_label`;
  - when Undo and then RIGHT commit at t = 160 with no frame between them, on RIGHT's F1 LEFT's float is gone, and RIGHT's own "+8" float is created at RIGHT's t = 80.

### 9.3 New best (`NewBestTest`)

- **AC-BEST-1** Given `FIX_NEW_BEST_4`, when LEFT commits:
  - at t = 64 the label key is `best_label`;
  - at t = 80 it is `new_best_label` and `GestureThresholdActivate` has been recorded at t = 80 (the only haptic of the move);
  - at t = 96 (S) the container fraction is 0, and at t = 192 (S + 96) it is 0.896.
- **AC-BEST-2** Then star scale is 1.0938 at t = 240 (S + 144; the 1.0948 peak falls at S + 139). Its rotation is 45° and not running at t = 1008. Sparkles alive: 8 at t = 96 and 0 at t = 688.
- **AC-BEST-3** Then star alpha is 1 at t = 1296 (S + 1200) and 0 at t = 1600, and nothing new-best related is running at t = 1616.
- **AC-BEST-4** Given the same game later exceeds the best again, then no second moment, haptic or star occurs.

### 9.4 Invalid move (`InvalidNudgeTest`)

- **AC-NUDGE-1** Given `FIX_INVALID_4`, when LEFT is invalid, then `boardNudgeDp.x` = −4.01 at t = 16, −5.22 at t = 32, +1.93 at t = 112 and −0.71 at t = 192, `boardNudgeDp.y == 0` throughout, and the engine state is unchanged.
- **AC-NUDGE-2** When UP is invalid, then `boardNudgeDp.y` = −5.22 at t = 32 and x == 0.
- **AC-NUDGE-3** Then at t = 400, |nudge| ≤ 0.1 dp and nothing is running.
- **AC-NUDGE-4** Given two invalid moves 200 ms apart (real time), then exactly one `Reject` is recorded. Given two invalid moves 416 ms apart, then two.
- **AC-NUDGE-5** Given `WON` or `OVER` status, when a move key is pressed, then the nudge stays 0 and no haptic is recorded.
- **AC-NUDGE-6** (Reduced) When LEFT is invalid, then `outlinePulseAlpha` is 0.576 at t = 96, 0.564 at t = 112 and 0 at t = 304 (keyframes 0 → 0.6 at 100 → 0 at 300, linear), and the board offset stays 0.

### 9.5 New game, undo, size switch (`NewGameCascadeTest`, `UndoCrossfadeTest`, `SizeSwitchTest`)

- **AC-NEW-1** Given `FIX_MID_4` (13 tiles, so `step` = 15 and `T_in` = 195), when New game is confirmed, then:
  - outgoing tile i (reported by `MotionProbe` with `outgoing = true`) begins shrinking on the first frame ≥ 15·i ms (its scale is < 1 one frame later);
  - every tile of the new `BoardUi` has kind `Static` (D-182);
  - incoming tile 0 has alpha 0 at t = 192 and > 0 at t = 208;
  - incoming tile 1 has alpha 0 at t = 240 and > 0 at t = 256;
  - nothing is running at t = 624.
- **AC-NEW-2** Then `displayedScore == 0` on F1, and the final frame equals the static render (I3).
- **AC-UNDO-1** Given `FIX_FILM` after LEFT has settled, when Undo commits, then:
  - changed-cell incoming alpha is 0.2592 at t = 16, 0.5865 at 32 and 0.9043 at 64;
  - outgoing tile alpha (`outgoing = true`, drawn by `OutgoingTilesLayer`) is 0.7408, 0.4135 and 0.0957 at the same times;
  - every tile of the new `BoardUi` has kind `Static`, and unchanged tiles keep their IDs, keep alpha 1 and do not recompose (D-182);
  - no tile position changes on any frame.
- **AC-UNDO-2** Then at t = 144 nothing is running except the undo icon. The icon rotation is −360° and not running at t = 320.
- **AC-SIZE-1** Given `FIX_MID_4` with a saved 3×3 game, when 3×3 is selected, then:
  - both boards are composed at t = 48;
  - only the 3×3 board is composed at t = 208;
  - score and best changed on F0.

### 9.6 Win (`WinSequenceTest`)

- **AC-WIN-1** Given `FIX_WIN_PRE_4`, when LEFT commits, then the 2048 tile has alpha 0 at t = 64, alpha 1 at t = 80, and scale 1.2078 at t = 128.
- **AC-WIN-2** Then:
  - `ring == null` (not drawn) at t = 128;
  - at t = 144 ring alpha is 0.830 and scale 1.062;
  - at t = 192 ring alpha is 0.408;
  - the ring is gone at t = 848.
- **AC-WIN-3** Then confetti alive is 0 at t = 192 and 96 at t = 208 (144 with `widthDp = 900`). It is 0 by t = 2416 at the latest, after which the loop has ended.
- **AC-WIN-4** Then:
  - `overlay == null` and the scrim is 0 at t = 672;
  - at t = 688 `overlay.kind == WIN`, card alpha is 0, card scale 0.92 and the scrim 0.0042;
  - at t = 720 (O + 16) card alpha is 0.2592;
  - at t = 784 the scrim is 0.449.
- **AC-WIN-5** Then `overlay.interactive == false` at t = 864 and true at t = 880, and Keep going is focused at t = 880. On both frames the buttons, including Share, are composed enabled (enabled colours), and before t = 880 they carry `disabled()` semantics and cannot take focus (D-200).
- **AC-WIN-6** Then the badge morph is 1.0 and not running at t = 1184 (O + 480). Badge rotation is 59.9° at t = 5696, and 60° and not running at t = 5712.
- **AC-WIN-7** Then the recorded haptics are exactly `Confirm` at t = 128 and `Confirm` at t = 272.
- **AC-WIN-8** Given the win sequence, when Undo is pressed at t = 320 (before the card), then:
  - on the undo's F1 confetti alive is 0, the ring is null and the overlay is null;
  - no overlay appears at any later frame;
  - the second `Confirm` (pending at 270, already fired) is not repeated.

### 9.7 Card exits (`CardExitTest`)

Times below are relative to Ft, the exit origin (§3.12; `MotionHarness.tapCardButton`).

- **AC-EXIT-1** Given the win card is interactive, when Keep going is tapped, then:
  - a move committed on the next frame is applied (I6);
  - card scale reaches 0.96 by Ft + 128 (the `slide` estimate is 114 ms) and card alpha 0 by Ft + 128;
  - the scrim is 0 and the overlay is removed at Ft + 208.
- **AC-EXIT-2** Given `FIX_WON_NO_MOVES_4`, when Keep going is tapped, then:
  - `Reject` is recorded at Ft;
  - the scrim moves from 0.60 toward 0.78 and is 0.78 at Ft + 304;
  - the overlay kind is OVER at Ft + 512;
  - the buttons are interactive at Ft + 704.

### 9.8 Game over (`GameOverSequenceTest`)

- **AC-OVER-1** Given `FIX_OVER_PRE_4`, when LEFT commits, then:
  - saturation is 1.0 at t = 288, 0.675 at t = 400 and 0.421 at t = 496;
  - it is 0.2 at t ≥ 912;
  - `Reject` is recorded at t = 304 and no other haptic.
- **AC-OVER-2** Then:
  - the tile at (3,3) has scale < 1 at t = 320;
  - a tile at Manhattan distance m has scale 1 at the last frame before 300 + 30·m and < 1 two frames later;
  - all tiles are at 0.94 and not running at t = 720.
- **AC-OVER-3** Then the scrim is 0 at t = 736, 0.0003 (> 0) at t = 752 and 0.564 at t = 848.
- **AC-OVER-4** Then the overlay is null at t = 784 and OVER at t = 800. `overlay.interactive` is false at t = 992 and true at t = 1008, with New game focused; the buttons are composed enabled on both frames (D-200).
- **AC-OVER-5** Then at t = 1200 the root bitmap equals the static `Restored` render of the same `OVER` state (I3, including scale 0.94 and saturation 0.2).

### 9.9 Restore, theme, layout, navigation, predictive back

- **AC-RESTORE-1** Given each of `FIX_MID_4`, `FIX_WON_4` and `FIX_OVER_4` persisted, when the screen first composes or `ActivityScenario.recreate()` runs, then on the first frame:
  - every tile is `Static` at its end values;
  - no float exists and no haptic is recorded;
  - cards are in their end state with interactive buttons;
  - nothing is running.
- **AC-RESTORE-2** (`RestoreNoMotionTest.reportsDoNotDisturbLivePresentation`, §3.14, D-047) Given `FIX_FILM` (Full), when LEFT commits, then:
  - at t = 0, `vm.board.value` equals `BoardPresenter.settled` of the move's board (same `moveId` and `epoch`, every tile `Static`, event `Restored`) and `vm.header.value.lastDelta == 0`;
  - yet the probe still reports the move's kinds (`Moved`, `Ghost`, `Merged`, `Spawned`), AC-MOVE-1, AC-MOVE-2, AC-MOVE-3, AC-MOVE-4 and AC-MOVE-5 hold unchanged, the "+4" float appears at t = 80 and `SegmentTick` is recorded at t = 80;
  - AC-PERF-1 and AC-PERF-2 hold over the same run.

  Given `FIX_WIN_PRE_4` (Full), when LEFT commits, then `vm.overlay.value` has entry `Restored` at t = 0, and AC-WIN-4 and AC-WIN-5 still hold (the card composes at 688 and becomes interactive at 880). This case needs `OverlayController`, so the method gains it in M3 part 2; the `FIX_FILM` case passes from M3 part 1.
- **AC-THEME-1** Given the Settings screen (Full), when Palette changes Classic → Neon, then `paletteFraction` is 0 on F1, 0.5402 at t = 64 and 0.9402 at t = 160, and nothing is running at t = 320. (Reduced/Off) Then the fraction is 1 on F0.
- **AC-LAYOUT-1** Given the first-launch hint (Full), when "Got it" is tapped, then the board bounds change continuously (strictly increasing height on each frame from 16 to 304) and are final at t = 416. (Reduced/Off) Then the board is at its final bounds on F0.
- **AC-LAYOUT-2** (`LayoutMotionTest.arrangementSwitchRendersSettledBoard`, with F18-AC13; §3.16, D-095) Given `FIX_FILM` at `w411dp-h450dp` (Full; STACKED with the COMPACT header and, with Robolectric's zero insets, a 258 dp board) and LEFT committed, when touch exploration turns on at t = 48 (mid-slide, before the reveal, the spawn, the "+N" and the `SegmentTick` due at 80), so the pad shows and the arrangement becomes SCROLL (a 298 dp board), then:
  - the board node measures 258 dp on every frame before the switch and 298 dp from the first frame that draws SCROLL; no frame shows another size;
  - on that frame every probe tile is `Static` at its engine cell with scale 1 and alpha 1, `displayedScore == vm.header.value.score`, and nothing is running; no "+N" float exists on it or on any later frame;
  - `vm.board.value.moveId`, the move log and `probe.originMs` are unchanged, and through t = 1,000 the recorded haptics are exactly the move's one `SegmentTick`, at t = 80: the screen scope's `HapticsController` keeps it and `MoveTimeline` keeps the origin (§2.4, §3.16).
  - Given then touch exploration off again with nothing running, the first frame that draws STACKED has the 258 dp board, with no intermediate size.
- **AC-LAYOUT-3** (`LayoutMotionTest.arrangementSwitchAfterPresentedMoveReplaysNothing`, with F18-AC14; §3.16, D-047, D-095) Given `FIX_FILM` at `w411dp-h450dp` (Full; STACKED, 258 dp board with zero insets), LEFT committed and run to t = 1,000, so the move was presented and has settled (nothing running, `vm.board.value` is `BoardPresenter.settled` of the move's board, `vm.header.value.lastDelta == 0`, and the haptics fake holds exactly the one `SegmentTick` at t = 80), when touch exploration turns on (the arrangement becomes SCROLL, 298 dp board) and, 1,000 ms after that switch with nothing running, turns off again (STACKED, 258 dp board), then after each switch, on every frame from the first frame that draws the new arrangement through 1,000 ms later:
  - every probe tile is `Static` at its engine cell with scale 1 and alpha 1, and no tile is `outgoing`;
  - `floats` is empty and `displayedScore == vm.header.value.score`;
  - `isAnyRunning` is false: the new arrangement's `BoardAnimator` launches nothing;
  - `probe.originMs` still equals the move's origin;
  - the haptics fake still holds exactly the one `SegmentTick` at t = 80;
  - `vm.board.value`, `vm.header.value` and the move log equal their values before the switch.

  With one retention state for the whole screen, the new `BoardView` and `GameHeader` would read the move's retained `Moved` board and `lastDelta > 0`, and this criterion fails on its first frame (`ARCHITECTURE.md` §4.11 item 2).
- **AC-NAV-1** Given Game (Full), when Settings is pushed, then:
  - at t = 0 the Settings content is offset +30 dp (LTR) with alpha 0;
  - at t = 256 it is at offset 0 with alpha 1 and Game is no longer composed.
  - (RTL `fa`) The offset sign is reversed.
  - (Off) At t = 16 only Settings is composed.
- **AC-BACK-1** Given Settings above Game (Full), when a predictive back gesture from the left edge reaches progress 0.5, then the outgoing content's scale is 0.95 ± 0.005 and its translation is +15 dp ± 0.5. On cancel it returns to scale 1 and Settings stays. On commit Game is the only destination. If Robolectric cannot drive `OnBackPressedDispatcher.dispatchOnBackStarted/Progressed` into Nav3 (check at first use), replace this criterion with a unit test of `GameNavTransitions.predictivePop(level, edge)` and record it in the PR body.
- **AC-BACK-2** (`BackOnRootTest.singleBackFromRootFinishes`, with F21-AC3) Given the Game root, when Back is dispatched, then no app callback is enabled (D-094), so the activity finishes.

### 9.10 Reduced, Off and system scale (`ReducedMotionTest`, `MotionOffTest`, `AnimatorScaleTest`)

- **AC-RED-1** (Reduced, `FIX_FILM` LEFT) Then:
  - on F0 every tile is at its final cell;
  - the merged tile alpha is 0.533 at t = 64 and 1 at t = 128;
  - ghosts have alpha 0 at t = 128;
  - spawn scale is 1 throughout and its alpha is 0.5865 at t = 32;
  - the "+N" float (created on the origin frame, fading from t = 16) has translation 0 and alpha 0.88 at t = 64.
- **AC-RED-2** (Reduced, `FIX_WIN_PRE_4`) Then:
  - the overlay exists at t = 0;
  - at t = 64 card alpha is 0.32 (48/150) and the scrim is 0.256 (0.6 × 64/150);
  - the buttons are interactive at t = 160 (composed enabled from t = 0);
  - confetti alive is 0 and the ring is null on every frame;
  - the badge shows morph 1 and 60°;
  - `Confirm` is recorded at t = 0 and t = 160.
- **AC-RED-3** (Reduced, `FIX_OVER_PRE_4`) Then:
  - tiles are at scale 0.94 on F0;
  - saturation is 0.2 at t = 160;
  - buttons are interactive at t = 160;
  - `Reject` is recorded at t = 0.
- **AC-OFF-1** (Off, each of move, merge, win, game over, new game and undo) Then on F0 the root bitmap equals the static render of the resulting state, and nothing is running on F0 or later.
- **AC-OFF-2** (Off) Then haptics still follow §6: win `Confirm` at t = 0 and t = 160, and every other haptic at t = 0.
- **AC-SCALE-0** Given system scale 0 (fake source + `MotionDurationScale(0f)`) with setting Full, then the results equal AC-OFF-1 and AC-OFF-2.
- **AC-SCALE-2** Given scale 2.0, when `FIX_FILM` LEFT commits, then:
  - the merged tile has alpha 0 at t = 144 and 1 at t = 160;
  - the spawn has alpha 0 at t = 176 and > 0 at t = 192;
  - `SegmentTick` is recorded at t = 160.
- **AC-SCALE-3** Given scale 2.0 and `FIX_WIN_PRE_4`, then:
  - confetti alive is 0 at t = 384 and 96 at t = 400 (emission at ⌈2 × 200⌉);
  - at t = 784 (192 ms of simulated time) every particle is within 4 dp of the same particle at scale 1 after 192 ms (t = 400). The only difference is the integration step: 8 ms versus 16 ms of simulated time (after a first step of 16 and 32 ms that covers the emission frame), which by itself puts up to 3.5 dp between the two with the step scheme of §3.11 (D-243);
  - on every frame from t = 416 to 4816, confetti alive equals the count of the same burst stepped as the canvas steps it at scale 2: no step on t = 416, 16 ms of simulated time on t = 432, then 8 ms per frame. So the live canvas steps the sequence that the trajectories above use (D-247), and all 96 are alive through t = 2416;
  - confetti alive is 0 by t = 4816.
- **AC-SCALE-4** (`SettingsScreenTest`, M4) Given system scale 0 (fake `AnimatorScaleSource`), the Settings Motion row is disabled with summary `motion_off_system`.

### 9.11 Haptics (`game/feedback/HapticsMapTest`; AC-HAP-4 in `SettingsScreenTest`)

- **AC-HAP-1** For each priority row of §6.1, given a move that triggers rows 1–4 together, then only the highest-priority haptic is recorded, at its Full time. Given `@Config(sdk = [24])`, the invalid-move `Reject` is not recorded, while the game-over `Reject` is (F14-AC9, `HapticsMapTest.invalidMoveSilentBelowApi30`, D-185).
- **AC-HAP-2** (`HapticsMapTest.hapticsOffSuppressesEverything`, with F14-AC6) Given Haptics = Off, then no haptic is recorded for any event, including settings toggles.
- **AC-HAP-3** Given a new-best move followed by another move at t = 48 (before 80), then `GestureThresholdActivate` is recorded on the second move's F1. Given a merge-only move followed by a move at t = 48, then its `SegmentTick` is not recorded.
- **AC-HAP-4** (proved by `SettingsScreenTest.switchesFireToggleHaptics` with F14-AC8, M4) Given the High-contrast switch toggled on then off, then `ToggleOn` then `ToggleOff` are recorded. Given the Haptics switch toggled off, then nothing is recorded; toggled on, `ToggleOn` is recorded.

### 9.12 Consistency, performance and goldens

- **AC-CONS-1** (`ConsistencyPropertyTest`) Given 20 sequences from `ParticleRandom(0x2048 + i)`, each of 15 random directions on N = 4 (10 sequences) and N = 8 (10 sequences), with gaps between commits drawn from {16, 32, 48, 96, 160, 448} ms, then on every frame I1, I2 and I4 hold, and after the last move's t = 448 I3 holds.
- **AC-CONS-2** (`InterruptionTest`) Given `FIX_FILM` with LEFT at 0, RIGHT at 48 and LEFT at 96, then:
  - all three are applied to successive states (D-052);
  - on each later commit's F1, every persisting tile starts exactly at its previous target;
  - a spawn whose alpha was 0 at the later commit has scale 1 and alpha 1 on that F1.
- **AC-CONS-3** (`FinalFrameEqualsStaticTest`) For move, merge, win, game over, undo, new game and size switch: after the event's settle bound, the root bitmap equals `MotionHarness.staticRender()`: the content composed anew after the settle bound, which draws the ViewModel's settled state as `BoardEvent.Restored` (D-229).
- **AC-PERF-1** (`TileRecompositionTest.noTileRecompositionDuringSlide`, with F13-AC12; D-150) Given `FIX_FILM`, when LEFT commits, then every tile key composes at most once on F0 and 0 times from F1 to t = 432.
- **AC-PERF-2** (`BoardRecompositionBudgetTest`) Then `BoardView` recomposes ≤ 2 times and `GameScreen` 0 times from F0 to t = 448.
- **AC-PERF-3** (`ConfettiSystemTest`) Given seed 0x2048 and 160 particles:
  - after 20,000 untimed warm-up steps, the fastest of 5 batches of 10,000 `step(1/60f)` calls takes ≤ 200 ms of the calling thread's CPU time; a new burst of 160 immortal particles starts every 132 steps, the longest confetti life, outside the timing (D-230, D-235);
  - 10,000 calls on one burst allocate ≤ 1 KiB after 100 warm-up steps;
  - a particle with v = (500, −1000) dp/s stepped 30 × 1/60 s ends at x = 148.86 ± 0.5 dp, y = −135.22 ± 0.5 dp, vy = 203.02 ± 0.5 dp/s (the discrete scheme of §3.11; the closed form is −142.26).
- **AC-PERF-4** (`ConfettiSystemTest`) Given the same seed twice, then the particle arrays are bit-identical after 120 steps.
- **AC-GOLD-1** (`MotionFilmstripTest.mergeAndSpawnFilmstrip`, with F13-AC8) The five filmstrip goldens (rows 37–41, D-204) match exactly in verify mode, and their content matches §8.3 when Read.
- **AC-TOKENS-1** (`MotionTokensTest`) Every §1.5 constant equals the table values exactly. Compose's estimator returns each §1.1 settle value within one 16 ms frame; a larger gap is fixed in §1.1 by a `docs:` change, with the library value as evidence (D-210).

---

## 10. Implementation file map

All paths are under `app/src/main/kotlin/com/clayboicardi/game2048/` unless stated. `ARCHITECTURE.md` §10 is the complete file map; this table lists the motion and haptics owners.

| File | Content |
|---|---|
| `ui/theme/GameMotion.kt` | §1.5 listing, `MotionLevel`, `MotionPlan` and `GameMotion.plan(level)` (D-183) |
| `ui/theme/GameMotionScheme.kt` | §2.2 schemes, `gameMotionScheme(level)` (D-184) |
| `ui/theme/EffectiveMotion.kt` | `AnimatorScaleSource`, `SystemAnimatorScaleSource`, `effectiveMotion()`, `LocalEffectiveMotion` (§2.1) |
| `ui/theme/GameTheme.kt` | Provides `LocalEffectiveMotion`, the level's `MotionScheme`, the palette/scheme crossfade (§3.15) |
| `ui/theme/GameNavTransitions.kt` | Push, pop and predictive specs per level (§3.17, §3.19) |
| `game/motion/DelayedSpec.kt` | `delayedSpec(spec, delayMs)` (§2.3 T1, D-183) |
| `game/motion/MoveTimeline.kt` | §2.4 |
| `game/motion/MotionProbe.kt` | §8.2 (`LocalMotionProbe`) |
| `game/motion/FlashLimiter.kt` | 3 per 1000 ms, real time |
| `game/motion/ParticleRandom.kt` | `SplitMix64` wrapper, `nextFloat()`, `uniform(a, b)` |
| `game/motion/ConfettiSystem.kt` | SoA engine: `emit(...)`, `step(dtSeconds)`, `aliveCount`, also used for sparkles |
| `game/motion/ParticleDraw.kt` | `DrawScope.drawParticles(system, colors, density)` |
| `game/board/BoardUi.kt` (M2) | `TileKind`, `TileUi`, `MoveHaptic`, `BoardEvent`, `BoardEventKind`, `BoardUi` (§3.1, §4.2) |
| `game/board/BoardPresenter.kt` (M2) | Stable tile IDs, kinds and ordering, epoch ID rule (§3.1, §3.8–§3.10, D-182) |
| `game/board/Board.kt` | `@Composable fun BoardView(…)`: board container, `OutgoingTilesLayer`, nudge, outline pulse, frame-rate vote, `MoveTimeline.publish`, size-switch `AnimatedContent` |
| `game/board/BoardAnimator.kt` | `BoardAnimator` with one `TileAnim` per tile ID: entrance and position effects (§3.1, §4.1) |
| `game/board/BoardEffects.kt` | Board-level effect state: glow pulse, desaturation, deflate ripple, win ring |
| `game/board/EffectsCanvas.kt` | `BoardEffectsCanvas` (win glow ring, below the scrim) and `ContentEffectsCanvas` (confetti and new-best sparkles, above the scrim, below the card; D-187) |
| `game/board/RecompositionCounter.kt` | `RecompositionCounter` (`onTile`, `onBoard`, `onScreen`), `LocalRecompositionCounter` (§7.2) |
| `game/overlay/OverlayController.kt` | Win and game-over sequencing, `interactive` state (D-200), card exits, Keep going → `OVER` |
| `game/overlay/BoardScrim.kt` | `OverlayCard` (Regular and Compact layouts) and the scrim clipped to the board shape |
| `game/overlay/WinCard.kt` | Win card, `WinBadge` morph, Share icon |
| `game/overlay/GameOverCard.kt` | Game-over card |
| `game/feedback/HapticsMap.kt` (M2) | Pure priority resolution into `MoveHaptic`, used by `GameViewModel` (D-185) |
| `game/feedback/HapticsController.kt` | §6.2: scheduling, interruption, invalid-move rate limit (D-185) |
| `ui/components/ScoreCard.kt` | `@Composable fun ScoreChip` and `ScoreValue` (`DESIGN_SYSTEM.md` §7.2) |
| `game/header/ScoreOdometer.kt` | Per-digit `AnimatedContent` (§3.5) |
| `game/header/ScoreFloats.kt` | "+N" floats (§3.4), composed inside `key(header.epoch, header.event)` (§4.2) |
| `game/header/NewBestBadge.kt` | New-best star (§3.6); its sparkles are drawn by `ContentEffectsCanvas` |
| `ui/components/GameModalSheet.kt` | Sheets; motion from `gameMotionScheme(level)` (§3.18) |
| `ui/components/Dialogs.kt`, `ui/components/GameSnackbar.kt` | Platform and M3 default fades at every level (§3.18, D-184) |
| `app/src/test/kotlin/com/clayboicardi/game2048/motion/*` | §8.5 tests, `MotionHarness` |
| `app/src/test/kotlin/com/clayboicardi/game2048/game/feedback/HapticsMapTest.kt`, `screenshots/MotionFilmstripTest.kt`, `ui/theme/EffectiveMotionTest.kt` | §8.5 tests outside `motion/` |
| `app/src/test/kotlin/com/clayboicardi/game2048/testing/FakeHapticFeedback.kt` | Records `(type, mainClock.currentTime − origin)` |
| `app/src/test/kotlin/com/clayboicardi/game2048/fixtures/MotionFixtures.kt` | §8.6 |

---

## Open issues

None open.

Resolved in M3: **AC-BACK-1 predictive back under Robolectric.** Robolectric drives `OnBackPressedDispatcher.dispatchOnBackStarted`, `dispatchOnBackProgressed`, `dispatchOnBackCancelled` and `onBackPressed` into Nav3 1.2.0's `NavDisplay` at SDK 36, so `PredictiveBackTest` proves AC-BACK-1 as written and the §9.9 fallback (a unit test of `GameNavTransitions.predictivePop(level, edge)`) was not used.

Resolved during planning: MS-OI-1 → D-182; MS-OI-2, -3, -4, -6, -8, -13, -18, -19 → D-183; MS-OI-5 → D-184; MS-OI-7, -17 → D-185; MS-OI-9 → D-187; MS-OI-10 → not built (§7.6); MS-OI-11 → removed by D-184; MS-OI-12 → D-063 kept (§2.1); MS-OI-14 → TC-7, D-210; MS-OI-15 → D-185; MS-OI-16 → D-204.
