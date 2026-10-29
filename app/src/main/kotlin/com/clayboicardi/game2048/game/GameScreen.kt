package com.clayboicardi.game2048.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.BoardView
import com.clayboicardi.game2048.game.board.ContentEffects
import com.clayboicardi.game2048.game.board.ContentEffectsCanvas
import com.clayboicardi.game2048.game.board.LocalRecompositionCounter
import com.clayboicardi.game2048.game.board.horizontalMargin
import com.clayboicardi.game2048.game.board.verticalMargin
import com.clayboicardi.game2048.game.feedback.HapticsEffects
import com.clayboicardi.game2048.game.feedback.rememberHapticsController
import com.clayboicardi.game2048.game.header.GameHeader
import com.clayboicardi.game2048.game.input.GameKeyInput
import com.clayboicardi.game2048.game.input.SwipeConfig
import com.clayboicardi.game2048.game.input.swipeToMove
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.MoveTimeline
import com.clayboicardi.game2048.game.overlay.NewGameDialog
import com.clayboicardi.game2048.ui.components.GameSnackbarHost
import com.clayboicardi.game2048.ui.components.showGameSnackbar
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import kotlinx.coroutines.launch

/**
 * The Game route (`GameKey`; ARCHITECTURE.md §4.8, §4.11, §6.6; DESIGN_SYSTEM.md §6.1; D-007,
 * D-047, D-054, D-186, D-199). It collects `viewModel.board`, `header` and `overlay` once each with
 * `collectAsStateWithLifecycle()` and never reads their values in its own body, so it recomposes 0
 * times per move (MOTION_SPEC.md §7.2, `onScreen()`); it makes the screen scope's
 * `rememberPresented` states and, until M4.3, the one arrangement's (ARCHITECTURE.md §4.11 item 2).
 * It creates and provides the screen scope's `MoveTimeline`, `HapticsController`, `ContentEffects`
 * and `GameKeyInput`, applies `Modifier.swipeToMove` to the content area and the key handlers, owns
 * the new-game dialog (`rememberSaveable`) and the snackbars (D-186), and reports the presentations
 * of the values its readers start. [settings] gives the Haptics, Move timing and Swipe sensitivity
 * settings; [clock] is the container's `MonotonicClock` (rate limits, flash limiter).
 *
 * M3 part 1 composes the one STACKED arrangement with the FULL header (DESIGN_SYSTEM.md §5.3, §5.5
 * A); M4's `GameLayout` replaces it.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    settings: Settings,
    clock: MonotonicClock,
    modifier: Modifier = Modifier,
) {
    val boardIn = viewModel.board.collectAsStateWithLifecycle()
    val headerIn = viewModel.header.collectAsStateWithLifecycle()
    val overlayIn = viewModel.overlay.collectAsStateWithLifecycle()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val nudge = viewModel.nudge.collectAsStateWithLifecycle()
    val message = viewModel.message.collectAsStateWithLifecycle()
    // The screen scope: the readers composed outside the arrangement (haptics, snackbars, cards).
    val screenBoard = boardIn.rememberPresented(sameBoardPresentation)
    val screenOverlay = overlayIn.rememberPresented(sameOverlayPresentation)
    // Until M4.3, the one arrangement's scope, made here beside the screen scope.
    val board = boardIn.rememberPresented(sameBoardPresentation)
    val header = headerIn.rememberPresented(sameHeaderPresentation)

    val counter = LocalRecompositionCounter.current
    SideEffect { counter?.onScreen() }

    val onMove: (Direction) -> Unit = viewModel::onMove
    val onUndo: () -> Unit = viewModel::onUndo
    val onNewGame: () -> Unit = viewModel::onNewGame
    val onResumed: () -> Unit = viewModel::onResumed
    val onPaused: () -> Unit = viewModel::onPaused
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    // D-007: New game asks first while the game is in progress, and starts at once otherwise.
    val onNewGameRequest: () -> Unit =
        remember(uiState, onNewGame) {
            { if (uiState.value.inProgress) dialogOpen = true else onNewGame() }
        }
    // ARCHITECTURE.md §6.6: the dialog is fully modal; the board takes input only with no modal,
    // no card, a loaded game and no size switch loading.
    val modalOpen = remember { derivedStateOf { dialogOpen } }
    val cardShown =
        remember(screenOverlay) {
            derivedStateOf { screenOverlay.value != OverlayUi.None }
        }
    val boardInputEnabled =
        remember(overlayIn, uiState) {
            derivedStateOf {
                val ui = uiState.value
                !modalOpen.value && overlayIn.value == OverlayUi.None && ui.ready && !ui.busy
            }
        }
    val keyInput =
        remember(onMove, onUndo, onNewGameRequest) {
            GameKeyInput(modalOpen, cardShown, boardInputEnabled, onMove, onUndo, onNewGameRequest)
        }
    val onBoardPresented: (Long) -> Unit = viewModel::onBoardPresented
    val onHeaderPresented: (Long) -> Unit = viewModel::onHeaderPresented
    val callbacks =
        remember(onMove, onUndo, onNewGameRequest, keyInput, onBoardPresented, onHeaderPresented) {
            GameCallbacks(
                onMove = onMove,
                onUndo = onUndo,
                onNewGame = onNewGameRequest,
                onBoardKey = keyInput::onBoardKey,
                onBoardPresented = onBoardPresented,
                onHeaderPresented = onHeaderPresented,
            )
        }
    val swipeConfig =
        rememberUpdatedState(
            SwipeConfig(settings.moveTiming, settings.swipeSensitivity.thresholdDp),
        )
    val timeline = remember { MoveTimeline() }
    val effects = remember { ContentEffects() }
    val snackbars = remember { SnackbarHostState() }
    val boardFocus = remember { FocusRequester() }
    val background = LocalTilePalette.current.background

    LifecycleResumeEffect(onResumed, onPaused) {
        onResumed()
        onPauseOrDispose { onPaused() }
    }

    CompositionLocalProvider(LocalMoveTimeline provides timeline) {
        val haptics = rememberHapticsController(settings.haptics, clock)
        HapticsEffects(haptics, screenBoard, nudge)
        Box(
            modifier.fillMaxSize().background(background).onPreviewKeyEvent(keyInput::onPreviewKey),
        ) {
            SnackbarEffects(screenBoard, message, snackbars)
            StackedArrangement(
                board = board,
                header = header,
                ui = uiState,
                nudge = nudge,
                loadingSize = settings.lastBoardSize,
                clock = clock,
                effects = effects,
                snackbars = snackbars,
                boardFocus = boardFocus,
                callbacks = callbacks,
                swipeConfig = swipeConfig,
                swipeEnabled = boardInputEnabled,
            )
            ContentEffectsCanvas(effects, Modifier.fillMaxSize())
            if (dialogOpen) {
                NewGameDialog(
                    onConfirm = {
                        dialogOpen = false
                        onNewGame()
                    },
                    onDismiss = { dialogOpen = false },
                )
            }
        }
    }
}

/** The commands the arrangement's readers send (method references of the screen's ViewModel). */
@Immutable
private class GameCallbacks(
    val onMove: (Direction) -> Unit,
    val onUndo: () -> Unit,
    /** The screen's New game request: it confirms while the game is in progress (D-007). */
    val onNewGame: () -> Unit,
    val onBoardKey: (KeyEvent) -> Boolean,
    val onBoardPresented: (Long) -> Unit,
    val onHeaderPresented: (Long) -> Unit,
)

/**
 * The STACKED arrangement (DESIGN_SYSTEM.md §5.3 STACKED row, §5.5 A): inside `safeDrawing` and the
 * D-188 margins, the FULL header, the board and the controls bar, as wide as the board, whose side
 * is `min(max(availW, BoardFloor), availH − (HeaderHeight + 2·StackGap + ControlsBarHeight),
 * BoardMax)`. The whole content area inside `safeDrawing` is the gesture surface (D-051). It
 * requests the board's focus when it appears (D-053).
 */
@Composable
private fun StackedArrangement(
    board: State<BoardUi>,
    header: State<HeaderUi>,
    ui: State<GameUiState>,
    nudge: State<Nudge?>,
    loadingSize: Int,
    clock: MonotonicClock,
    effects: ContentEffects,
    snackbars: SnackbarHostState,
    boardFocus: FocusRequester,
    callbacks: GameCallbacks,
    swipeConfig: State<SwipeConfig>,
    swipeEnabled: State<Boolean>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val safe = WindowInsets.safeDrawing
        val gestures = WindowInsets.systemGestures
        val safeWidth =
            with(density) {
                (safe.getLeft(this, direction) + safe.getRight(this, direction)).toDp()
            }
        val safeHeight = with(density) { (safe.getTop(this) + safe.getBottom(this)).toDp() }
        val marginLeft =
            horizontalMargin(maxWidth, with(density) { gestures.getLeft(this, direction).toDp() })
        val marginRight =
            horizontalMargin(maxWidth, with(density) { gestures.getRight(this, direction).toDp() })
        val marginVertical = verticalMargin(maxHeight)
        val availW = maxWidth - safeWidth - marginLeft - marginRight
        val availH = maxHeight - safeHeight - marginVertical * 2
        val chrome =
            GameDimens.HeaderHeight + GameDimens.StackGap * 2 + GameDimens.ControlsBarHeight
        val side =
            minOf(maxOf(availW, GameDimens.BoardFloor), availH - chrome, GameDimens.BoardMax)
                .coerceAtLeast(Dp.Hairline)
        val titleMax =
            if (maxWidth >= GameDimens.WidthExpandedMin) {
                GameDimens.TitleMaxExpanded
            } else {
                GameDimens.TitleMax
            }
        StackedLayout(
            boardSide = side,
            header = {
                GameHeader(
                    header = header,
                    board = board,
                    ui = ui,
                    loadingSize = loadingSize,
                    titleMax = titleMax,
                    effects = effects,
                    onHeaderPresented = callbacks.onHeaderPresented,
                    // The board-size sheet arrives in M3 part 2 (M3-D2).
                    onSizeChipClick = {},
                )
            },
            board = { BoardSlot(board, ui, nudge, clock, boardFocus, callbacks) },
            controls = { GameControls(board, ui, callbacks.onUndo, callbacks.onNewGame) },
            snackbar = { GameSnackbarHost(snackbars) },
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(safe)
                    .swipeToMove(swipeConfig, swipeEnabled, callbacks.onMove)
                    .absolutePadding(
                        left = marginLeft,
                        top = marginVertical,
                        right = marginRight,
                        bottom = marginVertical,
                    ),
        )
        LaunchedEffect(boardFocus) { boardFocus.requestFocus() }
    }
}

/** The board of this arrangement: its presented `BoardUi` with the chrome state it needs. */
@Composable
private fun BoardSlot(
    board: State<BoardUi>,
    ui: State<GameUiState>,
    nudge: State<Nudge?>,
    clock: MonotonicClock,
    focus: FocusRequester,
    callbacks: GameCallbacks,
    modifier: Modifier = Modifier,
) {
    val state = ui.value
    BoardView(
        board = board.value,
        nudge = nudge.value,
        status = state.status,
        canUndo = state.canUndo,
        loading = !state.ready,
        clock = clock,
        onMove = callbacks.onMove,
        onUndo = callbacks.onUndo,
        onNewGame = callbacks.onNewGame,
        onBoardKey = callbacks.onBoardKey,
        onPresented = callbacks.onBoardPresented,
        modifier = modifier,
        focusRequester = focus,
    )
}

/**
 * Places the STACKED column (DESIGN_SYSTEM.md §5.5 A): [header] at the top and [controls] at the
 * bottom, both [boardSide] wide, and the [boardSide] square [board] centred between them, so the
 * two gaps flex equally (at least `StackGap` by the board-size rule). The [snackbar] host, board −
 * 16 dp wide, sits in the gap between the board and the controls when that gap is at least its
 * height + 16 dp, and otherwise over the board, bottom-centred, 8 dp above its bottom edge: it
 * never covers the controls bar (D-186).
 */
@Composable
private fun StackedLayout(
    boardSide: Dp,
    header: @Composable () -> Unit,
    board: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    snackbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(header, board, controls, snackbar), modifier = modifier) {
        slots,
        constraints ->
        val side = boardSide.roundToPx()
        val column =
            Constraints(minWidth = side, maxWidth = side, maxHeight = constraints.maxHeight)
        val headerPlaceable = slots[SLOT_HEADER].first().measure(column)
        val controlsPlaceable = slots[SLOT_CONTROLS].first().measure(column)
        val boardPlaceable = slots[SLOT_BOARD].first().measure(Constraints.fixed(side, side))
        val snackWidth = (side - GameDimens.Space16.roundToPx()).coerceAtLeast(0)
        val snackPlaceable =
            slots[SLOT_SNACKBAR]
                .first()
                .measure(
                    Constraints(
                        minWidth = snackWidth,
                        maxWidth = snackWidth,
                        maxHeight = constraints.maxHeight,
                    ),
                )
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            val x = (width - side) / 2
            val controlsY = height - controlsPlaceable.height
            val boardY = headerPlaceable.height + (controlsY - headerPlaceable.height - side) / 2
            val boardBottom = boardY + side
            val gap = controlsY - boardBottom
            val snackHeight = snackPlaceable.height
            val snackY =
                if (gap >= snackHeight + GameDimens.Space16.roundToPx()) {
                    boardBottom + (gap - snackHeight) / 2
                } else {
                    boardBottom - GameDimens.Space8.roundToPx() - snackHeight
                }
            headerPlaceable.place(x, 0)
            boardPlaceable.place(x, boardY)
            controlsPlaceable.place(x, controlsY)
            snackPlaceable.place((width - snackPlaceable.width) / 2, snackY)
        }
    }
}

/**
 * The undo snackbars (D-186; DESIGN_SYSTEM.md §7.6): each new [message] shows once, as the only
 * visible snackbar; the message present when this composition starts is not shown again. Any
 * accepted move or undo of [board] (the screen scope's presented board) dismisses the current one,
 * except that a message that arrives with its undo is shown, not dismissed.
 */
@Composable
private fun SnackbarEffects(
    board: State<BoardUi>,
    message: State<GameMessage?>,
    host: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val undoUsed = stringResource(R.string.undo_hint_no_undo_best)
    val nothingToUndo = stringResource(R.string.nothing_to_undo)
    val shown = remember { MessageMark(message.value?.id) }
    val presented = board.value
    val latest = message.value
    LaunchedEffect(presented.moveId, latest?.id) {
        if (latest != null && latest.id != shown.id) {
            shown.id = latest.id
            val text =
                when (latest.kind) {
                    GameMessageKind.UNDO_USED -> undoUsed
                    GameMessageKind.NOTHING_TO_UNDO -> nothingToUndo
                }
            // The host's own scope: a later effect run must not cut the snackbar short.
            scope.launch { host.showGameSnackbar(text) }
        } else if (presented.event is BoardEvent.Moved || presented.event == BoardEvent.Undo) {
            host.currentSnackbarData?.dismiss()
        }
    }
}

/** The id of the last message shown; only the snackbar effect writes it. */
private class MessageMark(var id: Long?)

/** The content slots of [StackedLayout], in the order it lists them. */
private const val SLOT_HEADER = 0
private const val SLOT_BOARD = 1
private const val SLOT_CONTROLS = 2
private const val SLOT_SNACKBAR = 3
