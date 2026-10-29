package com.clayboicardi.game2048.game.board

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.clayboicardi.game2048.game.motion.ConfettiSystem
import com.clayboicardi.game2048.game.motion.ParticleColors
import com.clayboicardi.game2048.game.motion.ParticleRandom
import com.clayboicardi.game2048.game.motion.ProbeRing
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.game.motion.drawParticles
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.MotionLevel
import com.clayboicardi.game2048.ui.theme.MotionPlan
import com.clayboicardi.game2048.ui.theme.TilePalette
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Draws [effects] (D-187): fills its parent, which is the whole game content; converts root
 * positions with its own position in the root; runs one `withFrameNanos` loop while any particle
 * lives and invalidates draw only (MOTION_SPEC.md §2.5, §7.3); draws nothing and starts no loop
 * unless the level is Full and the animator scale is above 0 (MOTION_SPEC.md §2.3 T3). The confetti
 * is drawn below the new-best sparkles.
 */
@Composable
fun ContentEffectsCanvas(effects: ContentEffects, modifier: Modifier = Modifier) {
    val full = LocalEffectiveMotion.current == MotionLevel.FULL
    val density = LocalDensity.current.density
    val palette = LocalTilePalette.current
    val scheme = MaterialTheme.colorScheme
    val colors =
        remember(palette, scheme.primary, scheme.tertiary) {
            val fills = CONFETTI_FILL_EXPS.map { palette.fill(it) }
            ParticleColors(
                confetti = (fills + scheme.primary + scheme.tertiary).toImmutableList(),
                sparkle = scheme.tertiary,
            )
        }
    val scope = rememberCoroutineScope()
    DisposableEffect(effects, scope) {
        effects.scope = scope
        onDispose {
            effects.scope = null
            effects.clear()
        }
    }
    SideEffect {
        effects.density = density
        effects.enabled = full
        if (!full) effects.clear()
    }
    Spacer(
        modifier
            .fillMaxSize()
            .onPlaced { effects.originInRoot = it.positionInRoot() }
            .drawBehind {
                if (effects.tick != 0L) {
                    if (effects.confetti.aliveCount > 0) {
                        drawParticles(effects.confetti, colors, density)
                    }
                    if (effects.sparkles.aliveCount > 0) {
                        drawParticles(effects.sparkles, colors, density)
                    }
                }
            },
    )
}

/**
 * The particles drawn above the scrim and below the card (D-187; ARCHITECTURE.md §5.3,
 * DESIGN_SYSTEM.md §4.7): the new-best sparkles and the win confetti, each on its own instance of
 * the shared `game/motion/ConfettiSystem`. One per screen scope: `GameScreen` creates it, passes it
 * to the header (which emits the sparkles) and to `OverlayController` (which emits the confetti),
 * and draws it with [ContentEffectsCanvas] over the whole game content. Main thread only.
 */
@Stable
class ContentEffects {
    internal val sparkles = ConfettiSystem(SPARKLE_CAPACITY)

    /** The win confetti: one burst of at most `Confetti.CAP` (MOTION_SPEC.md §3.11). */
    internal val confetti = ConfettiSystem(GameMotion.Confetti.CAP)

    /** Bumped on every emission and loop step; read only in draw, so nothing recomposes. */
    internal var tick by mutableLongStateOf(0L)
        private set

    /** The canvas's scope (its composition's frame clock and `MotionDurationScale`). */
    internal var scope: CoroutineScope? = null

    /** The canvas's top-left in root px and its density, written by [ContentEffectsCanvas]. */
    internal var originInRoot: Offset = Offset.Zero
    internal var density: Float = 1f

    /** False unless the canvas is composed under Full motion. */
    internal var enabled: Boolean = false

    private var loop: Job? = null

    /**
     * Emits the 8 new-best sparkles from [centerInRoot] (root px, the BEST chip's centre) with
     * `ParticleRandom(seed)` (MOTION_SPEC.md §3.6). Called on frame ⌈80⌉ under Full only.
     */
    fun emitSparkles(centerInRoot: Offset, seed: Long) {
        if (scope == null || !enabled) return
        val local = centerInRoot - originInRoot
        sparkles.emitSparkles(local.x / density, local.y / density, ParticleRandom(seed))
        tick++
        ensureLoop()
    }

    /**
     * Emits one burst of [count] confetti particles (96, or 144 in a window at least 840 dp wide)
     * from [centerInRoot] (root px, the target tile's centre) with `ParticleRandom(seed)`, the won
     * state's `OverlayUi.Win.particleSeed` (MOTION_SPEC.md §3.11). Called on frame ⌈200⌉ under Full
     * only; a new burst replaces one still alive.
     */
    fun emitConfetti(centerInRoot: Offset, count: Int, seed: Long) {
        if (scope == null || !enabled) return
        val local = centerInRoot - originInRoot
        confetti.clear()
        confetti.emitConfetti(local.x / density, local.y / density, count, ParticleRandom(seed))
        tick++
        ensureLoop()
    }

    /** Kills the confetti at once (MOTION_SPEC.md §4.2: an undo or new game removes it on F0). */
    fun clearConfetti() {
        if (confetti.aliveCount > 0) {
            confetti.clear()
            tick++
        }
    }

    /** New-best sparkles alive now (for `ProbeBestChip.sparklesAlive`). */
    val sparklesAlive: Int
        get() = sparkles.aliveCount

    /** Confetti particles alive now (for `ProbeEffects.confettiAlive`). */
    val confettiAlive: Int
        get() = confetti.aliveCount

    /** Kills every particle and stops the loop. */
    internal fun clear() {
        loop?.cancel()
        loop = null
        if (sparkles.aliveCount > 0 || confetti.aliveCount > 0) {
            sparkles.clear()
            confetti.clear()
            tick++
        }
    }

    /** Starts the frame loop on the emission frame unless it runs already. */
    private fun ensureLoop() {
        val canvas = scope ?: return
        if (loop?.isActive != true) {
            loop = canvas.launch(start = CoroutineStart.UNDISPATCHED) { run() }
        }
    }

    /**
     * The frame loop of MOTION_SPEC.md §2.5 and §2.3 T3, started on the emission frame: it steps
     * every alive particle by the frame time divided by the animator scale, capped at `MAX_DT_S`,
     * until none is alive, and ends at once, clearing, when the scale is 0 or the level leaves
     * Full. Its first frame is the one after the emission, so the first step covers two frames and
     * the particles age from their emission frame.
     */
    private suspend fun run() {
        var last = withFrameNanos { it }
        var frames = 2
        while (sparkles.aliveCount > 0 || confetti.aliveCount > 0) {
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale == 0f || !enabled) break
            val now = withFrameNanos { it }
            val dt =
                min((now - last) * frames / NANOS_PER_SECOND / scale, GameMotion.Confetti.MAX_DT_S)
            sparkles.step(dt)
            confetti.step(dt)
            last = now
            frames = 1
            tick++
        }
        loop = null
        if (sparkles.aliveCount > 0 || confetti.aliveCount > 0) {
            sparkles.clear()
            confetti.clear()
            tick++
        }
    }

    private companion object {
        /** One emission of 8, twice over for an overlap. */
        const val SPARKLE_CAPACITY = GameMotion.NewBest.SPARKLE_COUNT * 2
        const val NANOS_PER_SECOND = 1e9f
    }
}

/**
 * The win glow ring (MOTION_SPEC.md §3.11; D-064, D-187), drawn by [BoardEffectsCanvas] above the
 * tiles and below the scrim: visible from `winRingAtMs` (`snap(delayMillis)`), then one fraction f
 * with `delayedSpec(plan.winRing, winRingAtMs)` drives a stroked rounded rect equal to the target
 * tile's rect: scale about its centre 1 → `RING_END_SCALE`, stroke `RING_STROKE_DP` → 0, alpha
 * `RING_START_ALPHA` → 0. Both run from the move's origin (mechanism A). It is gone once f is 1.
 * Main thread only.
 */
@Stable
internal class WinRing(private val scope: CoroutineScope) {
    private val shown = Animatable(0f)
    private val fraction = Animatable(0f, GameMotion.FRACTION_THRESHOLD)

    /** The ring's tile cell. */
    var row = 0
        private set

    var col = 0
        private set

    private var job: Job? = null

    /** Starts the ring of the tile at ([row], [col]) from the origin, where the plan has one. */
    fun start(row: Int, col: Int, plan: MotionPlan) {
        val spec = plan.winRing ?: return
        job?.cancel()
        this.row = row
        this.col = col
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                shown.snapTo(0f)
                fraction.snapTo(0f)
                launch(start = CoroutineStart.UNDISPATCHED) {
                    shown.animateTo(1f, snap(delayMillis = plan.winRingAtMs))
                }
                fraction.animateTo(1f, delayedSpec(spec, plan.winRingAtMs))
            }
    }

    /** Removes the ring at once (a newer board change, MOTION_SPEC.md §4.2). */
    fun clear() {
        if (job?.isActive != true && !visible) return
        job?.cancel()
        job = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            shown.snapTo(0f)
            fraction.snapTo(0f)
        }
    }

    /** True while the ring is drawn: after its start and before its fraction reaches 1. */
    val visible: Boolean
        get() = shown.value > 0f && fraction.value < 1f

    /** The fraction f, read in draw. */
    val progress: Float
        get() = fraction.value

    val isRunning: Boolean
        get() = job?.isActive == true

    /** Suspends until the ring has ended. */
    suspend fun awaitIdle() {
        job?.join()
    }

    /** `MotionProbe`'s ring (MOTION_SPEC.md §8.2), or `null` while none is drawn. */
    fun probe(): ProbeRing? {
        if (!visible) return null
        val f = fraction.value
        return ProbeRing(
            scale = 1f + (GameMotion.Win.RING_END_SCALE - 1f) * f,
            strokeDp = GameMotion.Win.RING_STROKE_DP * (1f - f),
            alpha = GameMotion.Win.RING_START_ALPHA * (1f - f),
        )
    }
}

/**
 * The board's effect layer above the tiles and below the scrim (D-187; DESIGN_SYSTEM.md §4.7 step
 * 7): the win glow [ring] in [color] (`palette.glow(targetExp)`'s colour, Classic `#F3D774`, or the
 * target's fill), on the board's [geometry]. It reads the ring only in draw, with one remembered
 * `android.graphics.Paint` whose stroke width and alpha are set per frame (MOTION_SPEC.md §7.3). It
 * has no semantics.
 */
@Composable
internal fun BoardEffectsCanvas(
    ring: WinRing,
    geometry: State<BoardGeometry>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    val argb = color.toArgb()
    Spacer(modifier.drawBehind { if (ring.visible) drawRing(ring, geometry.value, paint, argb) })
}

/**
 * The win ring's colour (DESIGN_SYSTEM.md §2.10): the glow colour of the target exponent
 * [targetExp] (Classic `#F3D774`), or its fill where the palette has no glow for it (Neon's glow is
 * the fill).
 */
internal fun winRingColor(palette: TilePalette, targetExp: Int): Color =
    palette.glow(targetExp)?.color ?: palette.fill(targetExp)

/** One frame of [ring] on [g] (MOTION_SPEC.md §3.11): allocation-free. */
private fun DrawScope.drawRing(ring: WinRing, g: BoardGeometry, paint: Paint, argb: Int) {
    val f = ring.progress
    val scale = 1f + (GameMotion.Win.RING_END_SCALE - 1f) * f
    val at = g.cellTopLeft(ring.col.toFloat(), ring.row.toFloat())
    val half = g.tilePx / 2f
    val cx = at.x + half
    val cy = at.y + half
    val reach = half * scale
    val radius = g.tileRadiusPx * scale
    paint.color = argb
    paint.alpha = (GameMotion.Win.RING_START_ALPHA * (1f - f) * OPAQUE).roundToInt()
    paint.strokeWidth = GameMotion.Win.RING_STROKE_DP * (1f - f) * density
    drawIntoCanvas {
        it.nativeCanvas.drawRoundRect(
            cx - reach,
            cy - reach,
            cx + reach,
            cy + reach,
            radius,
            radius,
            paint,
        )
    }
}

/** `android.graphics.Paint`'s full alpha. */
private const val OPAQUE = 255f

/** The confetti colours `palette.fill(7..11)` (MOTION_SPEC.md §3.11). */
private val CONFETTI_FILL_EXPS = 7..11
