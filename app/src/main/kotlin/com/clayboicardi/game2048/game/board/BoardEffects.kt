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
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.InvalidMoveMotion
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
 * §3.7, §3.13): the invalid-move nudge (Full) or outline pulse (Reduced), and the tile
 * [saturation]. All of them are read only in layout and draw lambdas. Main thread only.
 *
 * M3 part 1 holds the `OVER` end state as a snapped saturation; the animated game-over (deflate,
 * desaturation, scrim) and the win ring are M3 part 2.
 */
@Stable
internal class BoardEffects(private val scope: CoroutineScope, initialSaturation: Float) {
    /** 1 = full colour; `GameOver.SATURATION_END` at the `OVER` end state (MOTION_SPEC.md §7.3). */
    val saturation = Animatable(initialSaturation, GameMotion.FRACTION_THRESHOLD)

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

    /** A board change: the nudge snaps to zero on its F0 (MOTION_SPEC.md §3.7, §4.1 step 5). */
    fun onCommit() {
        nudgeJob?.cancel()
        nudgeJob = null
        nudgeX = 0f
        nudgeY = 0f
    }

    /** Snaps [saturation] to [target] (the `OVER` end state, or its reversal). */
    fun snapSaturation(target: Float) {
        if (saturation.value != target || saturation.isRunning) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { saturation.snapTo(target) }
        }
    }

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

    /** True while the nudge, the pulse or a saturation change runs. */
    val isRunning: Boolean
        get() = nudgeJob?.isActive == true || pulseJob?.isActive == true || saturation.isRunning

    /** Suspends until the nudge and the pulse have ended. */
    suspend fun awaitIdle() {
        nudgeJob?.join()
        pulseJob?.join()
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
