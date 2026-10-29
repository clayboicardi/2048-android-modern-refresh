package com.clayboicardi.game2048.game.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.clayboicardi.game2048.game.motion.FlashLimiter
import com.clayboicardi.game2048.game.motion.ProbeTile
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.MotionPlan
import com.clayboicardi.game2048.ui.theme.NewGameMotion
import com.clayboicardi.game2048.ui.theme.TilePalette
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One tile's animated values (ARCHITECTURE.md §5.4; MOTION_SPEC.md §3.1). [offset] is in cells (x =
 * column, y = row); every value is read only in layout, layer or draw lambdas (D-025). Main thread
 * only.
 */
@Stable
internal class TileAnim(offset: Offset, scale: Float, alpha: Float) {
    val offset: Animatable<Offset, AnimationVector2D> = Animatable(offset, Offset.VectorConverter)
    val scale = Animatable(scale, visibilityThreshold = GameMotion.SCALE_THRESHOLD)
    val alpha = Animatable(alpha, visibilityThreshold = GameMotion.ALPHA_THRESHOLD)

    /** The tile-local merge sheen (D-187). */
    val sheen = Animatable(0f, visibilityThreshold = GameMotion.ALPHA_THRESHOLD)

    /** The merge glow pulse, added to the palette's glow alpha. */
    val glowBoost = Animatable(0f, visibilityThreshold = GameMotion.ALPHA_THRESHOLD)

    /** The cell the tile settles in. */
    var target: Offset = offset

    var slideJob: Job? = null

    /** Every job still animating this tile, cancelled when the tile is dropped. */
    val jobs = ArrayList<Job>()

    val isRunning: Boolean
        get() = offset.isRunning || scale.isRunning || alpha.isRunning || effectsRunning

    private val effectsRunning: Boolean
        get() = sheen.isRunning || glowBoost.isRunning
}

/**
 * The inputs of one board change for [BoardAnimator] (ARCHITECTURE.md §5.4): [staticScale] is the
 * scale of `Static` tiles (`GameOver.DEFLATE_SCALE` at the `OVER` end state, else 1) and [outgoing]
 * the previous epoch's tiles still leaving (§5.5).
 */
@Immutable
internal data class BoardChange(
    val board: BoardUi,
    val plan: MotionPlan,
    val staticScale: Float,
    val outgoing: ImmutableList<TileUi>,
)

/**
 * One `TileAnim` per tile id, snap-then-animate on every commit (ARCHITECTURE.md §5.4, §5.5;
 * MOTION_SPEC.md §3.1–§3.3, §3.8, §3.9, §4.1; D-052, D-062, D-182, D-183). Everything is launched
 * into [scope], the `BoardView`'s `rememberCoroutineScope()`, with `CoroutineStart.UNDISPATCHED`,
 * and every delay sits inside its spec, so each animation has play time 0 at the origin and the
 * next move's `LaunchedEffect` never cancels an entrance. Main thread only.
 */
@Stable
internal class BoardAnimator(scope: CoroutineScope) {
    private val anims = HashMap<Long, TileAnim>()

    /** The ids [prepare] created for the board being committed: they keep their F0 values. */
    private val fresh = HashSet<Long>()

    private val launcher = Launcher(scope)

    /** The anim of [id]; [prepare] created it earlier in the same composition. */
    fun anim(id: Long): TileAnim = anims[id] ?: TileAnim(Offset.Zero, 1f, 0f)

    /**
     * Creates the missing anims of [board] with their first-frame values (ARCHITECTURE.md §5.4
     * table), so the frame that composes it draws correctly before any effect runs. Called in
     * composition; idempotent. Returns the board's `moveId`.
     */
    fun prepare(board: BoardUi, plan: MotionPlan, staticScale: Float): Long {
        for (tile in board.tiles) {
            if (anims.containsKey(tile.id)) continue
            anims[tile.id] = firstFrame(tile, board.event, plan, staticScale)
            fresh += tile.id
        }
        return board.moveId
    }

    /**
     * Commits [change] (MOTION_SPEC.md §4.1): finishes the previous change (slides snap to their
     * targets, entrances not yet started become visible, started ones keep running), drops the
     * anims of tiles that left, then starts this change's slides, reveals, pops, flashes and
     * spawns. Never suspends: called from `LaunchedEffect(board.moveId)`.
     */
    fun onCommit(
        change: BoardChange,
        geometry: BoardGeometry,
        palette: TilePalette,
        flash: FlashLimiter,
    ) {
        launcher.prune()
        val board = change.board
        finishPrevious(board, change.outgoing)
        val move = TileMove(change.plan, geometry, palette, flash)
        for (tile in board.tiles) {
            val anim = anims[tile.id] ?: continue
            anim.target = Offset(tile.col.toFloat(), tile.row.toFloat())
            launcher.start(anim, tile, tile.id in fresh, change, move)
        }
        fresh.clear()
    }

    /**
     * MOTION_SPEC.md §4.1 steps 1–4: drops the anims of tiles that are neither on [board] nor
     * leaving with its epoch, and settles the others that existed before this commit.
     */
    private fun finishPrevious(board: BoardUi, outgoing: ImmutableList<TileUi>) {
        val next = HashMap<Long, TileUi>(board.tiles.size * 2)
        for (tile in board.tiles) next[tile.id] = tile
        val leaving = outgoing.mapTo(HashSet()) { it.id }
        val entries = anims.entries.iterator()
        while (entries.hasNext()) {
            val (id, anim) = entries.next()
            val tile = next[id]
            when {
                tile == null && id !in leaving -> {
                    anim.jobs.forEach { it.cancel() }
                    entries.remove()
                }
                id in fresh -> Unit
                else -> launcher.settle(anim, tile)
            }
        }
    }

    /**
     * Starts the epoch transition of [change] (ARCHITECTURE.md §5.5; MOTION_SPEC.md §3.8, §3.9):
     * the undo crossfade or the new-game cascade of [change]'s outgoing tiles and of [incoming],
     * the epoch's new tiles. Returns the outgoing tiles' jobs; the incoming ones run on their own.
     */
    fun startEpoch(change: BoardChange, incoming: ImmutableList<TileUi>): List<Job> {
        val out = change.outgoing.mapNotNull { t -> anims[t.id]?.let { t to it } }
        val inc = incoming.mapNotNull { t -> anims[t.id]?.let { t to it } }
        val plan = change.plan
        return when (change.board.event) {
            BoardEvent.Undo -> launcher.crossfade(out, inc, plan.undoCrossfade)
            BoardEvent.NewGame ->
                when (plan.newGame) {
                    NewGameMotion.CASCADE -> launcher.cascade(out, inc, plan)
                    NewGameMotion.FADE -> launcher.crossfade(out, inc, plan.newGameFade)
                    NewGameMotion.INSTANT -> emptyList()
                }
            else -> emptyList()
        }
    }

    /** Drops the anims of [tiles] once their exit has finished (ARCHITECTURE.md §5.5). */
    fun release(tiles: ImmutableList<TileUi>) {
        for (tile in tiles) anims.remove(tile.id)?.jobs?.forEach { it.cancel() }
    }

    /**
     * Snaps the idle `Static` tiles of [board] to [scale] when the `OVER` end state starts or ends
     * without a new board (Keep going into `OVER`); tiles still in an entrance keep it.
     */
    fun snapStatic(board: BoardUi, scale: Float) {
        for (tile in board.tiles) {
            val anim = anims[tile.id]
            if (tile.kind != TileKind.Static || anim == null) continue
            if (!anim.scale.isRunning && !anim.alpha.isRunning)
                launcher.snap(anim, anim.scale, scale)
        }
    }

    /** Suspends until no tile animation is running (the frame-rate vote, D-183). */
    suspend fun awaitIdle() = launcher.awaitIdle()

    /** True while any tile animation runs (`MotionProbe.registerRunning`). */
    fun isRunning(): Boolean = anims.values.any { it.isRunning }

    /** The drawn tiles of [board], then [outgoing] (MOTION_SPEC.md §8.2). */
    fun probeTiles(board: BoardUi, outgoing: ImmutableList<TileUi>): List<ProbeTile> {
        val list = ArrayList<ProbeTile>(board.tiles.size + outgoing.size)
        for (tile in board.tiles) anims[tile.id]?.let { list += it.probe(tile, false) }
        for (tile in outgoing) anims[tile.id]?.let { list += it.probe(tile, true) }
        return list
    }
}

/** The per-commit values a move's animations need. */
private class TileMove(
    val plan: MotionPlan,
    val geometry: BoardGeometry,
    val palette: TilePalette,
    val flash: FlashLimiter,
)

/**
 * Launches tile animations into the board's scope, undispatched, and keeps their jobs for the idle
 * wait and for cancellation when a tile is dropped.
 */
private class Launcher(private val scope: CoroutineScope) {
    private val running = ArrayList<Job>()

    fun go(anim: TileAnim, block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)
        if (job.isActive) {
            anim.jobs += job
            running += job
        }
        return job
    }

    fun prune() {
        running.removeAll { !it.isActive }
    }

    suspend fun awaitIdle() {
        while (true) {
            val job = running.firstOrNull { it.isActive } ?: return
            job.join()
        }
    }

    /**
     * MOTION_SPEC.md §4.1 steps 2–3 for a tile that existed before this commit: its slide snaps to
     * its target (unless it slides again now, which restarts from that target), and an entrance
     * that has not started (alpha 0) becomes visible at once, because the tile is about to move.
     * Entrances that have started keep running (D-062).
     */
    fun settle(anim: TileAnim, next: TileUi?) {
        anim.jobs.removeAll { !it.isActive }
        anim.slideJob?.cancel()
        anim.slideJob = null
        val slidesAgain =
            next != null && (next.kind == TileKind.Moved || next.kind == TileKind.Ghost)
        if (!slidesAgain && anim.offset.value != anim.target) {
            go(anim) { anim.offset.snapTo(anim.target) }
        }
        if (next != null && anim.alpha.value == 0f) {
            go(anim) { anim.scale.snapTo(1f) }
            go(anim) { anim.alpha.snapTo(1f) }
        }
    }

    /**
     * A `Moved` tile: `from` → its cell with the `slide` spring, or a snap at F0 where slides snap
     * (Reduced, Off) or it did not move (§3.1).
     */
    fun slide(anim: TileAnim, tile: TileUi, move: TileMove) {
        val from = Offset(tile.fromCol.toFloat(), tile.fromRow.toFloat())
        val to = anim.target
        val pitch = move.geometry.tilePx + move.geometry.gapPx
        val spec = if (pitch > 0f) move.plan.slide?.spec(pitch) else null
        if (spec != null && from != to) {
            anim.slideJob =
                go(anim) {
                    anim.offset.snapTo(from)
                    anim.offset.animateTo(to, spec)
                }
        } else if (anim.offset.value != to || anim.offset.isRunning) {
            anim.slideJob = go(anim) { anim.offset.snapTo(to) }
        }
    }

    /**
     * A `Ghost`: slides with its source, then its alpha snaps to 0 at +200 (§3.1). A persisting
     * tile whose entrance had not started (alpha 0) is visible while it slides (§4.1 step 3): the
     * hide replaces [settle]'s pending alpha snap, which an `Animatable` mutation cancels, so it
     * applies that snap first.
     */
    fun ghost(anim: TileAnim, tile: TileUi, move: TileMove) {
        slide(anim, tile, move)
        val hide = move.plan.ghostHide
        go(anim) {
            if (hide == null) {
                anim.alpha.snapTo(0f)
            } else {
                if (anim.alpha.value == 0f) anim.alpha.snapTo(1f)
                anim.alpha.animateTo(0f, hide)
            }
        }
    }

    /**
     * A `Merged` tile: revealed at +80 (Reduced: the 120 ms crossfade), the pop with its own
     * impulse, and the sheen and glow pulse within the flash limit (§3.3, D-183).
     */
    fun reveal(anim: TileAnim, exp: Int, move: TileMove) {
        val plan = move.plan
        val reveal = plan.mergeReveal
        if (reveal != null) go(anim) { anim.alpha.animateTo(1f, reveal) }
        else snap(anim, anim.alpha, 1f)
        val pop = plan.pop
        if (pop != null) {
            val g = move.geometry
            val v0 = plan.popImpulse(exp, g.gapPx.toFloat(), g.tilePx)
            go(anim) {
                anim.scale.animateTo(1f, delayedSpec(pop, plan.mergeRevealMs), initialVelocity = v0)
            }
        }
        val sheen = plan.sheen?.takeIf { exp >= SHEEN_MIN_EXP }
        val glow = plan.glowPulse?.takeIf { exp >= GLOW_MIN_EXP && move.palette.glow(exp) != null }
        if ((sheen != null || glow != null) && move.flash.tryAcquire()) {
            if (sheen != null) {
                flashOnce(anim, anim.sheen, GameMotion.Move.SHEEN_ALPHA, sheen, plan.mergeRevealMs)
            }
            if (glow != null) {
                val boost = GameMotion.Move.GLOW_PULSE_BOOST
                flashOnce(anim, anim.glowBoost, boost, glow, plan.mergeRevealMs)
            }
        }
    }

    /** A `Spawned` tile: scale 0.6 → 1 and alpha 0 → 1 after the spawn delay (§3.2, D-183). */
    fun spawn(anim: TileAnim, event: BoardEvent, plan: MotionPlan) {
        val delay = plan.spawnDelayMs((event as? BoardEvent.Moved)?.maxSlideCells ?: 0)
        enter(anim, plan.spawnScale, plan.spawnAlpha, delay)
    }

    /** Snaps [anim] to [at], [scale] and alpha 1: a `Static` tile that existed before. */
    fun toEnd(anim: TileAnim, at: Offset, scale: Float) {
        anim.slideJob?.cancel()
        anim.slideJob = null
        if (anim.offset.value != at) go(anim) { anim.offset.snapTo(at) }
        snap(anim, anim.scale, scale)
        snap(anim, anim.alpha, 1f)
    }

    /** [value] jumps to [peak] now and returns to 0 with [spec] from [delay] ms (§3.3). */
    private fun flashOnce(
        anim: TileAnim,
        value: Animatable<Float, AnimationVector1D>,
        peak: Float,
        spec: FiniteAnimationSpec<Float>,
        delay: Int,
    ) {
        go(anim) {
            value.snapTo(peak)
            value.animateTo(0f, delayedSpec(spec, delay))
        }
    }
}

/** MOTION_SPEC.md §4.1 step 3: starts this change's animation of [tile] by its kind. */
private fun Launcher.start(
    anim: TileAnim,
    tile: TileUi,
    fresh: Boolean,
    change: BoardChange,
    move: TileMove,
) {
    when (tile.kind) {
        TileKind.Moved -> slide(anim, tile, move)
        TileKind.Ghost -> ghost(anim, tile, move)
        TileKind.Merged -> reveal(anim, tile.exp, move)
        TileKind.Spawned -> spawn(anim, change.board.event, change.plan)
        TileKind.Static -> if (!fresh) toEnd(anim, anim.target, change.staticScale)
    }
}

/** Undo's changed-cell crossfade, or the Reduced new-game fade, from the origin (§3.8, §3.9). */
private fun Launcher.crossfade(
    out: List<Pair<TileUi, TileAnim>>,
    inc: List<Pair<TileUi, TileAnim>>,
    spec: FiniteAnimationSpec<Float>?,
): List<Job> {
    if (spec == null) return emptyList()
    for ((_, anim) in inc) go(anim) { anim.alpha.animateTo(1f, spec) }
    return out.map { (_, anim) -> go(anim) { anim.alpha.animateTo(0f, spec) } }
}

/**
 * The new-game cascade (MOTION_SPEC.md §3.8): outgoing tile i, in reading order, shrinks to 0.6 and
 * fades from i · step; start tile k takes the spawn specs from n · step + 60 · k.
 */
private fun Launcher.cascade(
    out: List<Pair<TileUi, TileAnim>>,
    inc: List<Pair<TileUi, TileAnim>>,
    plan: MotionPlan,
): List<Job> {
    val step = if (out.isEmpty()) 0 else plan.newGameStepMs(out.size)
    val jobs = ArrayList<Job>(out.size * 2)
    val fade = plan.newGameFade
    if (fade != null) {
        for ((i, pair) in out.sortedBy { readingOrder(it.first) }.withIndex()) {
            val anim = pair.second
            val spec = delayedSpec(fade, i * step)
            val shrunk = GameMotion.Board.NEW_GAME_SHRINK_SCALE
            jobs += go(anim) { anim.scale.animateTo(shrunk, spec) }
            jobs += go(anim) { anim.alpha.animateTo(0f, spec) }
        }
    }
    val start = out.size * step
    for ((k, pair) in inc.sortedBy { readingOrder(it.first) }.withIndex()) {
        val delay = start + GameMotion.Board.NEW_GAME_SPAWN_GAP_MS * k
        enter(pair.second, plan.spawnScale, plan.spawnAlpha, delay)
    }
    return jobs
}

/** The spawn entrance: scale to 1 and alpha to 1, each [delay] ms late (or snapped). */
private fun Launcher.enter(
    anim: TileAnim,
    scale: FiniteAnimationSpec<Float>?,
    alpha: FiniteAnimationSpec<Float>?,
    delay: Int,
) {
    if (scale != null) go(anim) { anim.scale.animateTo(1f, delayedSpec(scale, delay)) }
    else snap(anim, anim.scale, 1f)
    if (alpha != null) go(anim) { anim.alpha.animateTo(1f, delayedSpec(alpha, delay)) }
    else snap(anim, anim.alpha, 1f)
}

/** Snaps [value] to [end] unless it is already there and idle. */
private fun Launcher.snap(anim: TileAnim, value: Animatable<Float, AnimationVector1D>, end: Float) {
    if (value.value != end || value.isRunning) go(anim) { value.snapTo(end) }
}

/**
 * The first-frame values of a tile [prepare][BoardAnimator.prepare] creates (ARCHITECTURE.md §5.4
 * table, §5.5): an epoch's new tiles start where its entrance starts.
 */
private fun firstFrame(
    tile: TileUi,
    event: BoardEvent,
    plan: MotionPlan,
    staticScale: Float,
): TileAnim {
    val at = Offset(tile.col.toFloat(), tile.row.toFloat())
    val anim =
        when (tile.kind) {
            TileKind.Moved -> TileAnim(slideStart(tile, at, plan), 1f, 1f)
            TileKind.Ghost ->
                TileAnim(slideStart(tile, at, plan), 1f, if (plan.ghostHide == null) 0f else 1f)
            TileKind.Merged -> TileAnim(at, 1f, plan.mergedStartAlpha)
            TileKind.Spawned -> TileAnim(at, plan.spawnStartScale, plan.spawnStartAlpha)
            TileKind.Static -> staticFirstFrame(at, event, plan, staticScale)
        }
    anim.target = at
    return anim
}

/** A slide starts at `from`, unless slides snap at this level (Reduced, Off). */
private fun slideStart(tile: TileUi, at: Offset, plan: MotionPlan): Offset =
    if (plan.slide == null) at else Offset(tile.fromCol.toFloat(), tile.fromRow.toFloat())

/** An epoch's new `Static` tile: invisible while its undo or new-game entrance is pending. */
private fun staticFirstFrame(
    at: Offset,
    event: BoardEvent,
    plan: MotionPlan,
    staticScale: Float,
): TileAnim =
    when {
        event == BoardEvent.Undo && plan.undoCrossfade != null -> TileAnim(at, staticScale, 0f)
        event == BoardEvent.NewGame && plan.newGame == NewGameMotion.CASCADE ->
            TileAnim(at, plan.spawnStartScale, plan.spawnStartAlpha)
        event == BoardEvent.NewGame && plan.newGame == NewGameMotion.FADE -> TileAnim(at, 1f, 0f)
        else -> TileAnim(at, staticScale, 1f)
    }

private fun readingOrder(tile: TileUi): Int = tile.row * MAX_N + tile.col

private fun TileAnim.probe(tile: TileUi, outgoing: Boolean): ProbeTile {
    val at = offset.value
    return ProbeTile(
        id = tile.id,
        exp = tile.exp,
        kind = tile.kind,
        outgoing = outgoing,
        x = at.x,
        y = at.y,
        scale = scale.value,
        alpha = alpha.value,
        sheen = sheen.value,
        glowBoost = glowBoost.value,
    )
}

/** Merged values ≥ 128 get the sheen; ≥ 512 the glow pulse when the palette has a glow (§3.3). */
private const val SHEEN_MIN_EXP = 7
private const val GLOW_MIN_EXP = 9

/** The largest board side: a row stride that orders cells row-major on every size. */
private const val MAX_N = 8
