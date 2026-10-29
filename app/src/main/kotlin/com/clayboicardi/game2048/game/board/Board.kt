package com.clayboicardi.game2048.game.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.game.Nudge
import com.clayboicardi.game2048.game.motion.FlashLimiter
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.ProbeBoard
import com.clayboicardi.game2048.ui.components.focusRing
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.InvalidMoveMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import com.clayboicardi.game2048.ui.theme.MotionLevel
import com.clayboicardi.game2048.ui.theme.MotionPlan
import com.clayboicardi.game2048.ui.theme.NewGameMotion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll

/**
 * The animated N×N board (ARCHITECTURE.md §5.3–§5.7; MOTION_SPEC.md §3.1–§3.10, §4; D-025, D-047,
 * D-080, D-104, D-182, D-183). The name avoids the engine type `Board`.
 *
 * Inputs:
 * - [board] is its arrangement scope's presented value (`rememberPresented(sameBoardPresentation)`,
 *   ARCHITECTURE.md §4.11 item 2), never a screen-scope retention state; [nudge] the latest invalid
 *   move (`GameViewModel.nudge`); [status] and [canUndo] come from `GameUiState`; [loading] is
 *   `!uiState.ready` (empty cells only, description `a11y_board_loading`, D-112).
 * - The board's side is given by the caller through [modifier] (for example `Modifier.size(board)`;
 *   in M4 `GameLayout`, which may animate it with `animateBounds`). `BoardView` derives its
 *   `BoardGeometry` from the size it is measured at, in layout, so a size change never disturbs a
 *   running slide and the first frame of any size draws correctly.
 * - [clock] times the flash limiter (`FlashLimiter`, real time).
 * - [onMove], [onUndo] and [onNewGame] are the board node's custom accessibility actions (D-080,
 *   ARCHITECTURE.md §6.4): "Move up/down/left/right", "Undo" only while [canUndo], "New game" (the
 *   screen shows its confirmation when the game is in progress, D-007).
 * - [onBoardKey] handles a key event while the board has focus (`GameKeyInput.onBoardKey`,
 *   ARCHITECTURE.md §6.3). The board is focusable through [focusRequester], which the screen uses
 *   to request focus when Game appears (D-053), and draws `Modifier.focusRing(boardShape)` (D-084).
 * - [onPresented] is `GameViewModel.onBoardPresented`, called from `LaunchedEffect(board.moveId)`
 *   right after `MoveTimeline.publish` (the origin F1).
 *
 * Reads `LocalMoveTimeline`, `LocalEffectiveMotion` (through `GameMotion.plan(level)`),
 * `LocalTilePalette`, `LocalTileSkin`, `LocalMotionProbe` and `LocalRecompositionCounter`. Forced
 * LTR inside (D-104). Tiles are keyed by id and carry no semantics; the fixed cell layer does. The
 * board node is tagged `"game:board"`.
 *
 * Recomposition (MOTION_SPEC.md §7.2): a move recomposes this body on its F0 (the new `BoardUi`,
 * frame rate High) and once more when every board animation has ended (frame rate Default); the
 * tiles that existed before the move are skipped. Static tiles of an `OVER` board that is not a
 * move render at `GameOver.DEFLATE_SCALE` and `SATURATION_END` (MOTION_SPEC.md §3.13).
 *
 * The board side of the cards (D-236; MOTION_SPEC.md §3.11–§3.13) is its `BoardCardSequence`: the
 * win glow ring in `BoardEffectsCanvas` above the tiles, the `OVER` move's desaturation and deflate
 * ripple, the saturation restore when the status leaves `OVER`, and Keep going into `OVER`, which
 * the screen scope's `OverlayController` starts through `LocalBoardOverlayLink` at its Ft.
 */
@Composable
fun BoardView(
    board: BoardUi,
    nudge: Nudge?,
    status: GameStatus,
    canUndo: Boolean,
    loading: Boolean,
    clock: MonotonicClock,
    onMove: (Direction) -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onBoardKey: (KeyEvent) -> Boolean,
    onPresented: (moveId: Long) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val level = LocalEffectiveMotion.current
    val plan = remember(level) { GameMotion.plan(level) }
    val timeline = LocalMoveTimeline.current
    val probe = LocalMotionProbe.current
    val counter = LocalRecompositionCounter.current
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val animator = remember { BoardAnimator(scope) }
    val flash = remember(clock) { FlashLimiter(clock) }
    val overEnd = status == GameStatus.OVER && board.event !is BoardEvent.Moved
    val staticScale = if (overEnd) GameMotion.GameOver.DEFLATE_SCALE else 1f
    val saturation = if (overEnd) GameMotion.GameOver.SATURATION_END else 1f
    val effects = remember { BoardEffects(scope, saturation) }
    val cards = remember { BoardCardSequence(scope, animator, effects) }
    val textCache = rememberTileTextCache()
    remember(board.moveId) { animator.prepare(board, plan, staticScale) }
    val epoch = rememberEpochChange(board, plan)
    var exitDone by remember(board.epoch) { mutableStateOf(epoch.outgoing.isEmpty()) }
    val outgoing = if (exitDone) persistentListOf() else epoch.outgoing
    val change = BoardChange(board, plan, staticScale, outgoing)
    val firstNudge = remember { nudge?.id }
    var idle by remember { mutableStateOf(IdleMark.initial(board, firstNudge)) }
    val busy = isAnimating(board, nudge, plan, idle) || cards.running
    val frameRate = if (busy) FrameRateCategory.High else FrameRateCategory.Default
    val sizeTransition = updateTransition(board.size, label = "boardSize")
    val outer = remember(board.size.n) { mutableStateOf(placeholderGeometry(board.size.n)) }
    val live = remember { LiveBoard() }

    LaunchedEffect(board.moveId) {
        animator.onCommit(change, outer.value, palette, flash)
        effects.onCommit()
        cards.onCommit(board, status, plan)
    }
    LaunchedEffect(board.moveId) {
        val origin = withFrameNanos { it }
        timeline.publish(board.moveId, origin)
        probe?.originMs = origin / NANOS_PER_MS
        onPresented(board.moveId)
    }
    LaunchedEffect(board.epoch) {
        animator.startEpoch(change, epoch.incoming).joinAll()
        animator.release(epoch.outgoing)
        exitDone = true
    }
    // Keep going into OVER changes the status without a new board (D-036, MOTION_SPEC.md §3.12).
    LaunchedEffect(status) { cards.onStatus(status) }
    val link = LocalBoardOverlayLink.current
    DisposableEffect(link, cards) {
        link?.attach(cards)
        onDispose { link?.detach(cards) }
    }
    LaunchedEffect(nudge?.id) {
        if (nudge != null && nudge.id != firstNudge) {
            effects.nudge(nudge.direction, plan, density, clock, probe)
        }
    }
    LaunchedEffect(board.moveId, nudge?.id) {
        if (!busy) return@LaunchedEffect
        animator.awaitIdle()
        effects.awaitIdle()
        snapshotFlow { sizeTransition.isRunning }.first { !it }
        idle = IdleMark(board.moveId, nudge?.id)
    }
    ProbeRegistrations(probe, animator, effects, live, sizeTransition)
    SideEffect {
        live.board = board
        live.outgoing = outgoing
        live.frameRate = frameRate
        live.density = density
        live.bySize[board.size] = board
        cards.plan = plan
        cards.board = board
        cards.geometry = outer
        counter?.onBoard()
        if (!loading) probe?.boardLoaded = true
    }

    val labels = boardLabels(board, loading)
    val focusShape = remember(outer) { GeometryShape(outer, board = true) }
    val pulseColor = MaterialTheme.colorScheme.primary
    val ringColor = winRingColor(palette, board.size.targetExp)
    val switchSpec = remember(plan) { sizeSwitch(plan) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .measureGeometry(outer, board.size.n, skin, density)
                .offset { effects.nudgeOffset() }
                .onPlaced { cards.coordinates = it }
                .preferredFrameRate(frameRate)
                .boardSemantics(labels, canUndo, onMove, onUndo, onNewGame)
                .testTag(BOARD_TAG)
                .onKeyEvent(onBoardKey)
                .focusRing(focusShape)
                .focusRequester(focusRequester)
                .focusable()
                .pulseOutline(effects, outer, pulseColor),
        ) {
            sizeTransition.AnimatedContent(Modifier.fillMaxSize(), transitionSpec = switchSpec) {
                size ->
                val current = size == board.size
                BoardFrame(
                    board = if (current) board else live.bySize[size] ?: board,
                    outgoing = if (current) outgoing else persistentListOf(),
                    loading = loading,
                    animator = animator,
                    effects = effects,
                    textCache = textCache,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            BoardEffectsCanvas(effects.ring, outer, ringColor, Modifier.matchParentSize())
        }
    }
}

/**
 * One size's board inside the size-switch `AnimatedContent` (DESIGN_SYSTEM.md §4.7): the board and
 * empty cells in one draw pass, the fixed cell semantics layer, the previous epoch's outgoing tiles
 * and the tiles in z-order. Its geometry comes from the size it is measured at.
 */
@Composable
private fun BoardFrame(
    board: BoardUi,
    outgoing: ImmutableList<TileUi>,
    loading: Boolean,
    animator: BoardAnimator,
    effects: BoardEffects,
    textCache: TileTextCache,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val density = LocalDensity.current.density
    val n = board.size.n
    val geometry = remember(n) { mutableStateOf(placeholderGeometry(n)) }
    val style =
        remember(palette, skin, textCache, effects, geometry) {
            TileStyle(palette, skin, textCache, effects, GeometryShape(geometry, board = false))
        }
    Box(
        modifier.measureGeometry(geometry, n, skin, density).drawBehind {
            drawBoardCells(geometry.value, palette, skin)
        },
    ) {
        CellSemanticsLayer(board.cells, n, geometry, Modifier.matchParentSize())
        if (outgoing.isNotEmpty()) {
            TileLayer(outgoing, animator, geometry, style, Modifier.matchParentSize())
        }
        if (!loading) TileLayer(board.tiles, animator, geometry, style, Modifier.matchParentSize())
    }
}

/**
 * The previous epoch's outgoing tiles and this epoch's new ones (ARCHITECTURE.md §5.5), derived in
 * composition once per epoch so the outgoing layer is drawn from the epoch's F0, with no gap.
 */
@Composable
private fun rememberEpochChange(board: BoardUi, plan: MotionPlan): EpochChange {
    val previous = remember { Holder<BoardUi?>(null) }
    val change = remember(board.epoch) { epochChange(previous.value, board, plan) }
    SideEffect { previous.value = board }
    return change
}

/**
 * The outgoing and incoming tiles of [board]'s epoch after [old]: the ids present only before and
 * only after it, for an undo or a new game whose transition animates at this level (D-182).
 * Restored and size-switch epochs have none (ARCHITECTURE.md §5.5).
 */
private fun epochChange(old: BoardUi?, board: BoardUi, plan: MotionPlan): EpochChange {
    val animated =
        when (board.event) {
            BoardEvent.Undo -> plan.undoCrossfade != null
            BoardEvent.NewGame -> plan.newGame != NewGameMotion.INSTANT
            else -> false
        }
    if (!animated || old?.epoch == board.epoch) return EpochChange.NONE
    val kept = board.tiles.mapTo(HashSet()) { it.id }
    val before = old?.tiles.orEmpty().filter { it.kind != TileKind.Ghost }
    val beforeIds = before.mapTo(HashSet()) { it.id }
    return EpochChange(
        outgoing = before.filter { it.id !in kept }.toImmutableList(),
        incoming = board.tiles.filter { it.id !in beforeIds }.toImmutableList(),
    )
}

/**
 * Whether a board animation runs (the D-183 frame-rate vote): from the F0 of a change that is not
 * `Restored`, or of a nudge that plays, until the idle effect marks it done. Nothing runs under
 * Off.
 */
private fun isAnimating(board: BoardUi, nudge: Nudge?, plan: MotionPlan, idle: IdleMark): Boolean {
    if (plan.level == MotionLevel.OFF) return false
    val boardRuns = board.event != BoardEvent.Restored && board.moveId != idle.moveId
    val nudgeRuns =
        nudge != null && nudge.id != idle.nudgeId && plan.invalidMove != InvalidMoveMotion.NONE
    return boardRuns || nudgeRuns
}

/** `MotionProbe` registrations, only while a probe is provided (MOTION_SPEC.md §8.2). */
@Composable
private fun ProbeRegistrations(
    probe: MotionProbe?,
    animator: BoardAnimator,
    effects: BoardEffects,
    live: LiveBoard,
    sizeTransition: Transition<BoardSize>,
) {
    DisposableEffect(probe, animator, effects) {
        val registrations =
            if (probe == null) {
                emptyList()
            } else {
                listOf(
                    probe.registerTiles {
                        live.board?.let { animator.probeTiles(it, live.outgoing) }.orEmpty()
                    },
                    probe.registerBoard {
                        ProbeBoard(
                            nudgeDp = effects.nudgeDp(live.density),
                            outlinePulseAlpha = effects.outlinePulseAlpha,
                            saturation = effects.saturation.value,
                            frameRate = live.frameRate,
                        )
                    },
                    probe.registerRunning {
                        animator.isRunning() || effects.isRunning || sizeTransition.isRunning
                    },
                )
            }
        onDispose { registrations.forEach { it.unregister() } }
    }
}

/** The board node's spoken texts (D-080; L10N_STORE_LEGAL.md §1.3 table E). */
@Composable
private fun boardLabels(board: BoardUi, loading: Boolean): BoardLabels {
    val n = board.size.n
    val side = n.toString()
    val count = board.cells.count { it != 0 }
    val description =
        if (loading || count == 0) {
            stringResource(R.string.a11y_board_loading, side)
        } else {
            val tiles = pluralStringResource(R.plurals.a11y_tiles, count, count.toString())
            val highest = TileLabels.fullDecimal(board.cells.max())
            stringResource(R.string.a11y_board_description, side, tiles, highest)
        }
    return BoardLabels(
        n = n,
        description = description,
        up = stringResource(R.string.move_up),
        down = stringResource(R.string.move_down),
        left = stringResource(R.string.move_left),
        right = stringResource(R.string.move_right),
        undo = stringResource(R.string.undo),
        newGame = stringResource(R.string.new_game),
    )
}

/**
 * The one board semantics node (D-080; ARCHITECTURE.md §6.4): `CollectionInfo(N, N)`, the board
 * description and the custom actions, "Undo" only while [canUndo].
 */
private fun Modifier.boardSemantics(
    labels: BoardLabels,
    canUndo: Boolean,
    onMove: (Direction) -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
): Modifier = semantics {
    contentDescription = labels.description
    collectionInfo = CollectionInfo(labels.n, labels.n)
    customActions = buildList {
        add(moveAction(labels.up, Direction.UP, onMove))
        add(moveAction(labels.down, Direction.DOWN, onMove))
        add(moveAction(labels.left, Direction.LEFT, onMove))
        add(moveAction(labels.right, Direction.RIGHT, onMove))
        if (canUndo) {
            add(
                CustomAccessibilityAction(labels.undo) {
                    onUndo()
                    true
                },
            )
        }
        add(
            CustomAccessibilityAction(labels.newGame) {
                onNewGame()
                true
            },
        )
    }
}

/** "Move up" and its siblings: the same `onMove` as a swipe or key (ARCHITECTURE.md §6.4). */
private fun moveAction(
    label: String,
    direction: Direction,
    onMove: (Direction) -> Unit,
): CustomAccessibilityAction =
    CustomAccessibilityAction(label) {
        onMove(direction)
        true
    }

/** The Reduced invalid-move pulse above the board content (MOTION_SPEC.md §3.7). */
private fun Modifier.pulseOutline(
    effects: BoardEffects,
    geometry: State<BoardGeometry>,
    color: Color,
): Modifier = drawWithCache {
    val stroke = Stroke(GameMotion.Nudge.PULSE_STROKE_DP * density)
    onDrawWithContent {
        drawContent()
        val alpha = effects.outlinePulseAlpha
        if (alpha > 0f) drawOutlinePulse(geometry.value, color, alpha, stroke)
    }
}

/**
 * The size switch (MOTION_SPEC.md §3.10): `fadeIn(sizeSwitchIn)` together with
 * `fadeOut(sizeSwitchOut)`, a swap where the plan has none; no size transform, so glows are not
 * clipped.
 */
private fun sizeSwitch(
    plan: MotionPlan,
): AnimatedContentTransitionScope<BoardSize>.() -> ContentTransform = {
    val enter = plan.sizeSwitchIn?.let { fadeIn(it) } ?: EnterTransition.None
    val exit = plan.sizeSwitchOut?.let { fadeOut(it) } ?: ExitTransition.None
    (enter togetherWith exit).using(null)
}

/** The tiles that leave and enter with an epoch (ARCHITECTURE.md §5.5). */
@Immutable
private data class EpochChange(
    val outgoing: ImmutableList<TileUi>,
    val incoming: ImmutableList<TileUi>,
) {
    companion object {
        val NONE = EpochChange(persistentListOf(), persistentListOf())
    }
}

/** The board node's spoken texts. */
@Immutable
private data class BoardLabels(
    val n: Int,
    val description: String,
    val up: String,
    val down: String,
    val left: String,
    val right: String,
    val undo: String,
    val newGame: String,
)

/** The move and nudge whose animations have all ended (the frame-rate vote). */
@Immutable
private data class IdleMark(val moveId: Long, val nudgeId: Long?) {
    companion object {
        /** A first composition is idle unless its board animates; its nudge never plays. */
        fun initial(board: BoardUi, nudgeId: Long?): IdleMark =
            IdleMark(if (board.event == BoardEvent.Restored) board.moveId else NOT_IDLE, nudgeId)

        private const val NOT_IDLE = Long.MIN_VALUE
    }
}

/** A plain holder, not snapshot state, written only from `SideEffect`. */
private class Holder<T>(var value: T)

/** What the probe readers and the outgoing size of a switch read, written from `SideEffect`. */
private class LiveBoard {
    var board: BoardUi? = null
    var outgoing: ImmutableList<TileUi> = persistentListOf()
    var frameRate: FrameRateCategory = FrameRateCategory.Default
    var density: Float = 1f

    /** The last board composed per size: the outgoing content of a size switch draws it. */
    val bySize = HashMap<BoardSize, BoardUi>()
}

/** The test tag of the board node (QUALITY.md, MOTION_SPEC.md §8). */
private const val BOARD_TAG = "game:board"

private const val NANOS_PER_MS = 1_000_000L
