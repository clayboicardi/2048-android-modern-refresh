package com.clayboicardi.game2048.game.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.ProbeRing
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.InvalidMoveMotion
import com.clayboicardi.game2048.ui.theme.MotionLevel
import com.clayboicardi.game2048.ui.theme.MotionPlan
import com.clayboicardi.game2048.ui.theme.TilePalette
import com.clayboicardi.game2048.ui.theme.TileSkin
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The board-level animated values of one `BoardView` (ARCHITECTURE.md §5.3, §5.7; MOTION_SPEC.md
 * §3.7, §3.11–§3.13): the invalid-move nudge (Full) or outline pulse (Reduced), the tile
 * [saturation] (the game-over desaturation and the restore of a card exit) and the win glow [ring].
 * All of them are read only in layout and draw lambdas. Main thread only.
 */
@Stable
internal class BoardEffects(private val scope: CoroutineScope, initialSaturation: Float) {
    /** 1 = full colour; `GameOver.SATURATION_END` at the `OVER` end state (MOTION_SPEC.md §7.3). */
    val saturation = Animatable(initialSaturation, GameMotion.FRACTION_THRESHOLD)

    /** The win glow ring of `BoardEffectsCanvas` (MOTION_SPEC.md §3.11, D-187). */
    val ring = WinRing(scope)

    private var saturationJob: Job? = null

    private var nudgeX by mutableFloatStateOf(0f)
    private var nudgeY by mutableFloatStateOf(0f)

    /** The Reduced outline pulse's alpha (MOTION_SPEC.md §3.7). */
    var outlinePulseAlpha by mutableFloatStateOf(0f)
        private set

    private var nudgeJob: Job? = null
    private var pulseJob: Job? = null
    private var lastPulseMs: Long? = null

    /** The nudge offset in px, for the board's `offset {}` (layout phase). */
    fun nudgeOffset(): IntOffset = IntOffset(nudgeX.roundToInt(), nudgeY.roundToInt())

    /** The nudge offset in dp (`ProbeBoard.nudgeDp`). */
    fun nudgeDp(density: Float): Offset = Offset(nudgeX / density, nudgeY / density)

    /**
     * A board change: the nudge snaps to zero on its F0 (MOTION_SPEC.md §3.7, §4.1 step 5) and a
     * running ring is removed (§4.2: undo, new game and size switch remove it on F0).
     */
    fun onCommit() {
        nudgeJob?.cancel()
        nudgeJob = null
        nudgeX = 0f
        nudgeY = 0f
        ring.clear()
    }

    /**
     * Takes [saturation] to [target] with [spec] from the next frame (play time 0 there), or snaps
     * it now when [spec] is `null` (Off, or an end state). A running change is replaced.
     */
    fun saturateTo(target: Float, spec: FiniteAnimationSpec<Float>?) {
        saturationJob?.cancel()
        saturationJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (spec == null) {
                    if (saturation.value != target || saturation.isRunning) {
                        saturation.snapTo(target)
                    }
                } else {
                    saturation.animateTo(target, spec)
                }
            }
    }

    /** True while [saturation] runs towards full colour (a card exit's restore). */
    val restoring: Boolean
        get() = saturation.isRunning && saturation.targetValue == 1f

    /**
     * An invalid move along [direction] (MOTION_SPEC.md §3.7): Full restarts the `NUDGE` spring
     * from the current offset with `NUDGE_VELOCITY_DP_S` along the swipe; Reduced pulses the
     * outline, at most once per `REJECT_MIN_INTERVAL_MS` of [clock] time; Off shows nothing. The
     * first animation frame is reported as [MotionProbe.nudgeOriginMs].
     */
    fun nudge(
        direction: Direction,
        plan: MotionPlan,
        density: Float,
        clock: MonotonicClock,
        probe: MotionProbe?,
    ) {
        when (plan.invalidMove) {
            InvalidMoveMotion.NUDGE ->
                plan.nudge?.let { startNudge(direction, it.spec(density), density, probe) }
            InvalidMoveMotion.OUTLINE_PULSE ->
                plan.outlinePulse?.let { startPulse(it, clock.nowMs(), probe) }
            InvalidMoveMotion.NONE -> Unit
        }
    }

    /** True while the nudge, the pulse, a saturation change or the ring runs. */
    val isRunning: Boolean
        get() =
            nudgeJob?.isActive == true ||
                pulseJob?.isActive == true ||
                saturation.isRunning ||
                ring.isRunning

    /** Suspends until the nudge, the pulse, a saturation change and the ring have ended. */
    suspend fun awaitIdle() {
        nudgeJob?.join()
        pulseJob?.join()
        saturationJob?.join()
        ring.awaitIdle()
    }

    private fun startNudge(
        direction: Direction,
        spec: AnimationSpec<Offset>,
        density: Float,
        probe: MotionProbe?,
    ) {
        nudgeJob?.cancel()
        val unit = direction.unit()
        val speed = GameMotion.NUDGE_VELOCITY_DP_S * density
        val state =
            AnimationState(
                Offset.VectorConverter,
                Offset(nudgeX, nudgeY),
                Offset(unit.x * speed, unit.y * speed),
            )
        nudgeJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var first = true
                state.animateTo(Offset.Zero, spec) {
                    if (first) {
                        first = false
                        probe?.nudgeOriginMs = startTimeNanos / NANOS_PER_MS
                    }
                    nudgeX = value.x
                    nudgeY = value.y
                }
            }
    }

    private fun startPulse(
        spec: FiniteAnimationSpec<Float>,
        nowMs: Long,
        probe: MotionProbe?,
    ) {
        val last = lastPulseMs
        if (last != null && nowMs - last < GameMotion.Nudge.REJECT_MIN_INTERVAL_MS) return
        lastPulseMs = nowMs
        pulseJob?.cancel()
        pulseJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var first = true
                AnimationState(0f).animateTo(0f, spec) {
                    if (first) {
                        first = false
                        probe?.nudgeOriginMs = startTimeNanos / NANOS_PER_MS
                    }
                    outlinePulseAlpha = value
                }
                outlinePulseAlpha = 0f
            }
    }
}

/**
 * What the screen scope's `OverlayController` asks of the live `BoardView` (D-236; ARCHITECTURE.md
 * §4.11 item 2, §5.3): the board side of the card sequences that the controller times from a card
 * action's Ft, and the values its one effects probe reader reports.
 */
internal interface BoardCardHandle {
    /** The win ring now (`MotionProbe`'s `ProbeEffects.ring`), `null` while none is drawn. */
    fun ring(): ProbeRing?

    /** The win target tile's centre in root px, where the confetti bursts from. */
    fun targetCenterInRoot(): Offset?

    /**
     * Keep going on a board with no moves left (D-036; MOTION_SPEC.md §3.12): the desaturation and
     * the deflate ripple of §3.13 with T0 = the next frame (Ft), from the winning move's spawn cell
     * [originIndex] (`OverlayUi.Win.spawnIndex`, D-237), or, when it is `null`, from the cell the
     * board itself gives.
     */
    fun keepGoingIntoOver(originIndex: Int?)

    /** A card exit leaves `OVER` (MOTION_SPEC.md §3.12): the saturation restore from Ft. */
    fun restoreSaturation()
}

/**
 * The screen scope's link between `OverlayController`, the live `BoardView` and the screen's
 * [effects] (D-236): `GameScreen` creates one and provides it as [LocalBoardOverlayLink]; each
 * `BoardView` attaches its [BoardCardHandle] while it is composed, so a board composed anew
 * (recreation, M4's arrangement switch) takes over. Main thread only.
 */
@Stable
internal class BoardOverlayLink(
    /** The screen scope's particles: `OverlayController` emits the win confetti into them. */
    val effects: ContentEffects,
) {
    /** The live board's handle, `null` while no `BoardView` is composed. */
    var board: BoardCardHandle? = null
        private set

    fun attach(handle: BoardCardHandle) {
        board = handle
    }

    fun detach(handle: BoardCardHandle) {
        if (board === handle) board = null
    }
}

/** The screen scope's [BoardOverlayLink]; `null` (no link) outside the Game screen. */
@Suppress("ComposeCompositionLocalUsage") // D-236: the screen scope's link to the live board
internal val LocalBoardOverlayLink = staticCompositionLocalOf<BoardOverlayLink?> { null }

/**
 * The board side of the win and game-over sequences of one `BoardView` (D-236; MOTION_SPEC.md
 * §3.11–§3.13; ARCHITECTURE.md §5.3, §5.6): the win ring of a winning move, the `OVER` move's
 * desaturation `delayedSpec(overDesaturate, overT0Ms)` and deflate ripple from its spawn cell (both
 * from the origin, mechanism A), the `OVER` end state of a board that arrives in `OVER`, the
 * saturation restore when the status leaves `OVER`, and Keep going into `OVER` from Ft. `BoardView`
 * writes [plan], [board], [geometry] and [coordinates] and calls [onCommit] from its
 * `LaunchedEffect(board.moveId)` and [onStatus] from its `LaunchedEffect(status)`. Main thread
 * only.
 */
@Stable
internal class BoardCardSequence(
    private val scope: CoroutineScope,
    private val animator: BoardAnimator,
    private val effects: BoardEffects,
) : BoardCardHandle {
    /** The plan of the live composition's level. */
    var plan: MotionPlan = GameMotion.plan(MotionLevel.FULL)

    /** The live (presented) board, written from `SideEffect`. */
    var board: BoardUi? = null

    /** The board's geometry holder (`BoardView`'s outer geometry). */
    var geometry: State<BoardGeometry>? = null

    /** The board's layout coordinates, from `onPlaced`. */
    var coordinates: LayoutCoordinates? = null

    /**
     * True while Keep going into `OVER` animates this board without a new `BoardUi`: it holds the
     * D-183 frame-rate vote at High (read by `BoardView`'s composition).
     */
    var running by mutableStateOf(false)
        private set

    /** The status this sequence has handled last; `null` before the first commit. */
    private var handledStatus: GameStatus? = null

    private var keepGoingStarted = false

    /** A board change, on its F0, after the tiles' commit (MOTION_SPEC.md §4.1). */
    fun onCommit(board: BoardUi, status: GameStatus, plan: MotionPlan) {
        val leavingOver = handledStatus == GameStatus.OVER
        handledStatus = status
        keepGoingStarted = false
        val moved = board.event as? BoardEvent.Moved
        when {
            moved?.becameOver == true -> {
                val desaturate = plan.overDesaturate?.let { delayedSpec(it, plan.overT0Ms) }
                effects.saturateTo(GameMotion.GameOver.SATURATION_END, desaturate)
                animator.deflate(board, plan, moved.spawnIndex, sinceT0Ms = 0)
            }
            status == GameStatus.OVER ->
                effects.saturateTo(GameMotion.GameOver.SATURATION_END, null)
            leavingOver || effects.saturation.value != 1f -> restoreSaturation()
        }
        if (moved?.becameWon == true) {
            winTile(board)?.let { effects.ring.start(it.row, it.col, plan) }
        }
    }

    /**
     * A status change without a new board: `WON` → `OVER` is Keep going on a board with no moves
     * left (D-036), unless the controller has started it already at Ft.
     */
    fun onStatus(status: GameStatus) {
        val previous = handledStatus
        if (previous == status) return
        handledStatus = status
        if (previous == GameStatus.WON && status == GameStatus.OVER) keepGoingIntoOver(null)
    }

    override fun keepGoingIntoOver(originIndex: Int?) {
        val live = board
        if (keepGoingStarted || live == null) return
        keepGoingStarted = true
        handledStatus = GameStatus.OVER
        effects.saturateTo(GameMotion.GameOver.SATURATION_END, plan.overDesaturate)
        val origin = originIndex ?: winSpawnIndex(live)
        animator.deflate(live, plan, origin, sinceT0Ms = plan.overT0Ms)
        if (plan.level != MotionLevel.OFF) {
            running = true
            scope.launch {
                animator.awaitIdle()
                effects.awaitIdle()
                running = false
            }
        }
    }

    override fun restoreSaturation() {
        if (!effects.restoring && effects.saturation.value != 1f) {
            effects.saturateTo(1f, plan.exitSaturation)
        }
    }

    override fun ring(): ProbeRing? = effects.ring.probe()

    override fun targetCenterInRoot(): Offset? {
        val coords = coordinates?.takeIf { it.isAttached }
        val target = board?.let(::winTile)
        val g = geometry?.value
        if (coords == null || target == null || g == null) return null
        val at = g.cellTopLeft(target.col.toFloat(), target.row.toFloat())
        val half = g.tilePx / 2f
        return coords.localToRoot(Offset(at.x + half, at.y + half))
    }
}

/**
 * The target tile of a winning [board]: its merged tile of the size's target exponent, else (a
 * restored `WON` board) its first tile of that exponent.
 */
private fun winTile(board: BoardUi): TileUi? {
    val target = board.size.targetExp
    return board.tiles.firstOrNull { it.kind == TileKind.Merged && it.exp == target }
        ?: board.tiles.firstOrNull { it.kind != TileKind.Ghost && it.exp == target }
}

/**
 * The origin cell of Keep going into `OVER`'s deflate ripple when the controller gives none
 * (MOTION_SPEC.md §3.12): the winning move's spawn cell when [board] is that move, else its target
 * tile's cell. `OverlayController` passes `OverlayUi.Win.spawnIndex` (D-237).
 */
private fun winSpawnIndex(board: BoardUi): Int {
    val moved = (board.event as? BoardEvent.Moved)?.takeIf { it.becameWon }
    val tile = winTile(board)
    return when {
        moved != null -> moved.spawnIndex
        tile != null -> tile.row * board.size.n + tile.col
        else -> 0
    }
}

/** The physical unit vector of a swipe (forced-LTR board, D-104). */
private fun Direction.unit(): Offset =
    when (this) {
        Direction.UP -> Offset(0f, -1f)
        Direction.RIGHT -> Offset(1f, 0f)
        Direction.DOWN -> Offset(0f, 1f)
        Direction.LEFT -> Offset(-1f, 0f)
    }

/**
 * A rounded rectangle whose radius is read from [geometry] when the outline is created: the tile
 * shape or, with [board], the board shape (DESIGN_SYSTEM.md §4.4), for a geometry that is known
 * only once the board has been measured.
 */
@Stable
internal class GeometryShape(
    private val geometry: State<BoardGeometry>,
    private val board: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val g = geometry.value
        val radius = if (board) g.boardRadiusPx else g.tileRadiusPx
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radius)))
    }
}

/**
 * What every tile of one board draws with (ARCHITECTURE.md §5.3): one instance per board, so a
 * `TileView`'s parameters stay equal while it slides and it is skipped by recomposition.
 */
@Stable
internal class TileStyle(
    val palette: TilePalette,
    val skin: TileSkin,
    val textCache: TileTextCache,
    val effects: BoardEffects,
    /** The tile shape of the board's geometry, for the glow (`GeometryShape`). */
    val shape: Shape,
)

/**
 * The board and its N² empty cells in one pass (DESIGN_SYSTEM.md §4.7 steps 1–2, D-025), plus the
 * high-contrast empty-cell strokes in `palette.ink(1)` (D-206) when [skin] has a tile outline.
 */
internal fun DrawScope.drawBoardCells(g: BoardGeometry, palette: TilePalette, skin: TileSkin) {
    drawRoundRect(palette.board, cornerRadius = CornerRadius(g.boardRadiusPx))
    val cell = Size(g.tilePx, g.tilePx)
    val radius = CornerRadius(g.tileRadiusPx)
    val hcStroke = GameDimens.HcEmptyCellStroke.toPx()
    val inset = hcStroke / 2f
    val strokeSize = Size(g.tilePx - hcStroke, g.tilePx - hcStroke)
    val strokeRadius = CornerRadius((g.tileRadiusPx - inset).coerceAtLeast(0f))
    val stroke = Stroke(hcStroke)
    for (row in 0 until g.n) {
        for (col in 0 until g.n) {
            val at = g.cellTopLeft(col.toFloat(), row.toFloat())
            val topLeft = Offset(at.x.toFloat(), at.y.toFloat())
            drawRoundRect(palette.empty, topLeft = topLeft, size = cell, cornerRadius = radius)
            if (skin.tileOutline != null) {
                drawRoundRect(
                    palette.ink(1),
                    topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
                    size = strokeSize,
                    cornerRadius = strokeRadius,
                    style = stroke,
                )
            }
        }
    }
}

/**
 * The Reduced invalid-move pulse (MOTION_SPEC.md §3.7, DESIGN_SYSTEM.md §2.10): a board-shaped
 * `PULSE_STROKE_DP` stroke in [color] at [alpha], inside the board edge.
 */
internal fun DrawScope.drawOutlinePulse(
    g: BoardGeometry,
    color: Color,
    alpha: Float,
    stroke: Stroke,
) {
    val inset = stroke.width / 2f
    drawRoundRect(
        color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke.width, size.height - stroke.width),
        cornerRadius = CornerRadius((g.boardRadiusPx - inset).coerceAtLeast(0f)),
        alpha = alpha,
        style = stroke,
    )
}

private const val NANOS_PER_MS = 1_000_000L
