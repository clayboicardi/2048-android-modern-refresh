package com.clayboicardi.game2048.game.board

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.clayboicardi.game2048.game.motion.ConfettiSystem
import com.clayboicardi.game2048.game.motion.ParticleColors
import com.clayboicardi.game2048.game.motion.ParticleRandom
import com.clayboicardi.game2048.game.motion.drawParticles
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.MotionLevel
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Draws [effects] (D-187): fills its parent, which is the whole game content; converts root
 * positions with its own position in the root; runs one `withFrameNanos` loop per emission and
 * invalidates draw only (MOTION_SPEC.md §2.5, §7.3); draws nothing and starts no loop unless the
 * level is Full and the animator scale is above 0 (MOTION_SPEC.md §2.3 T3).
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
                if (effects.tick != 0L && effects.sparkles.aliveCount > 0) {
                    drawParticles(effects.sparkles, colors, density)
                }
            },
    )
}

/**
 * The particles drawn above the scrim and below the card (D-187; ARCHITECTURE.md §5.3,
 * DESIGN_SYSTEM.md §4.7): the new-best sparkles (M3 part 1) and the win confetti (M3 part 2), on
 * the shared `game/motion/ConfettiSystem`. One per screen scope: `GameScreen` creates it, passes it
 * to the header (which emits the sparkles) and draws it with [ContentEffectsCanvas] over the whole
 * game content. Main thread only.
 */
@Stable
class ContentEffects {
    internal val sparkles = ConfettiSystem(SPARKLE_CAPACITY)

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
        val canvas = scope
        if (canvas == null || !enabled) return
        val local = centerInRoot - originInRoot
        sparkles.emitSparkles(local.x / density, local.y / density, ParticleRandom(seed))
        tick++
        if (loop?.isActive != true) {
            loop = canvas.launch(start = CoroutineStart.UNDISPATCHED) { run() }
        }
    }

    /** New-best sparkles alive now (for `ProbeBestChip.sparklesAlive`). */
    val sparklesAlive: Int
        get() = sparkles.aliveCount

    /** Kills every particle and stops the loop. */
    internal fun clear() {
        loop?.cancel()
        loop = null
        if (sparkles.aliveCount > 0) {
            sparkles.clear()
            tick++
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
        while (sparkles.aliveCount > 0) {
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale == 0f || !enabled) break
            val now = withFrameNanos { it }
            val dt = (now - last) * frames / NANOS_PER_SECOND / scale
            sparkles.step(min(dt, GameMotion.Confetti.MAX_DT_S))
            last = now
            frames = 1
            tick++
        }
        loop = null
        if (sparkles.aliveCount > 0) {
            sparkles.clear()
            tick++
        }
    }

    private companion object {
        /** One emission of 8, twice over for an overlap. */
        const val SPARKLE_CAPACITY = GameMotion.NewBest.SPARKLE_COUNT * 2
        const val NANOS_PER_SECOND = 1e9f
    }
}

/** The confetti colours `palette.fill(7..11)` (MOTION_SPEC.md §3.11). */
private val CONFETTI_FILL_EXPS = 7..11
