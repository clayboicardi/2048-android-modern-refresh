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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardOverlayLink
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.BoardView
import com.clayboicardi.game2048.game.board.ContentEffects
import com.clayboicardi.game2048.game.board.ContentEffectsCanvas
import com.clayboicardi.game2048.game.board.LocalBoardOverlayLink
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
import com.clayboicardi.game2048.game.overlay.BoardScrim
import com.clayboicardi.game2048.game.overlay.BoardSizeSheet
import com.clayboicardi.game2048.game.overlay.CardAction
import com.clayboicardi.game2048.game.overlay.CardActions
import com.clayboicardi.game2048.game.overlay.GameOverCard
import com.clayboicardi.game2048.game.overlay.NewGameDialog
import com.clayboicardi.game2048.game.overlay.OverlayCard
import com.clayboicardi.game2048.game.overlay.OverlayCommands
import com.clayboicardi.game2048.game.overlay.OverlayController
import com.clayboicardi.game2048.game.overlay.WinCard
import com.clayboicardi.game2048.game.overlay.rememberOverlayController
import com.clayboicardi.game2048.game.share.ShareStrings
import com.clayboicardi.game2048.game.share.ShareText
import com.clayboicardi.game2048.ui.components.GameSnackbarHost
import com.clayboicardi.game2048.ui.components.shareText
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
 * It creates and provides the screen scope's `MoveTimeline`, `HapticsController`, `ContentEffects`,
 * board link (`LocalBoardOverlayLink`) and `GameKeyInput`, applies `Modifier.swipeToMove` to the
 * content area inside `safeDrawing` and the key handlers, owns the new-game dialog, the board-size
 * sheet and the game menu (each `rememberSaveable`, ARCHITECTURE.md §7.2) and the snackbars
 * (D-186), and reports the presentations of the values its readers start. Its `OverlayController`
 * (screen scope) sequences the win and game-over cards, which the overlay host draws over the board
 * outside the arrangement (scrim, `ContentEffectsCanvas`, card; D-008, D-187, D-200); while a card
 * is shown the controls-bar Undo and New game and the U, Ctrl+Z, Backspace, N and R keys act like
 * its buttons (D-199). [settings] gives the Haptics, Move timing and Swipe sensitivity settings;
 * [clock] is the container's `MonotonicClock` (rate limits, flash limiter); [onNavigate] pushes a
 * sub-screen (`AppNavHost`'s `navigate`, ARCHITECTURE.md §7.1), called by the game menu.
 *
 * M3 part 1 composes the one STACKED arrangement with the FULL header (DESIGN_SYSTEM.md §5.3, §5.5
 * A); M4's `GameLayout` replaces it.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    settings: Settings,
    clock: MonotonicClock,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val boardIn = viewModel.board.collectAsStateWithLifecycle()
    val headerIn = viewModel.header.collectAsStateWithLifecycle()
    val overlayIn = viewModel.overlay.collectAsStateWithLifecycle()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val nudge = viewModel.nudge.collectAsStateWithLifecycle()
    val message = viewModel.message.collectAsStateWithLifecycle()
    val sizeOptions = viewModel.sizeOptions.collectAsStateWithLifecycle()
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
    val onKeepGoing: () -> Unit = viewModel::onKeepGoing
    val onOverlayPresented: (Long) -> Unit = viewModel::onOverlayPresented
    val onResumed: () -> Unit = viewModel::onResumed
    val onPaused: () -> Unit = viewModel::onPaused
    val onSizeSheetOpened: () -> Unit = viewModel::onSizeSheetOpened
    val onSizeSelected: (BoardSize) -> Unit = viewModel::onSizeSelected
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // D-007: New game asks first while the game is in progress, and starts at once otherwise.
    val onNewGameRequest: () -> Unit =
        remember(uiState, onNewGame) {
            { if (uiState.value.inProgress) dialogOpen = true else onNewGame() }
        }
    // ARCHITECTURE.md §6.6: the dialog, the sheet and the menu are fully modal; the board takes
    // input only with no modal, no card, a loaded game and no size switch loading.
    val modalOpen = remember { derivedStateOf { dialogOpen || sheetOpen || menuOpen } }
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
    val commands =
        remember(onKeepGoing, onNewGame, onUndo, onOverlayPresented) {
            OverlayCommands(onKeepGoing, onNewGame, onUndo, onOverlayPresented)
        }
    val onBoardPresented: (Long) -> Unit = viewModel::onBoardPresented
    val onHeaderPresented: (Long) -> Unit = viewModel::onHeaderPresented
    val currentOnNavigate = rememberUpdatedState(onNavigate)
    val chromeActions = remember {
        ChromeCallbacks(
            onSizeChip = { sheetOpen = true },
            onMenuOpenChange = { open -> menuOpen = open },
            onNavigate = { key -> currentOnNavigate.value(key) },
        )
    }
    val swipeConfig =
        rememberUpdatedState(
            SwipeConfig(settings.moveTiming, settings.swipeSensitivity.thresholdDp),
        )
    val timeline = remember { MoveTimeline() }
    val effects = remember { ContentEffects() }
    val link = remember(effects) { BoardOverlayLink(effects) }
    val snackbars = remember { SnackbarHostState() }
    val boardFocus = remember { FocusRequester() }
    val boardBounds = remember { BoardBounds() }
    val background = LocalTilePalette.current.background

    LifecycleResumeEffect(onResumed, onPaused) {
        onResumed()
        onPauseOrDispose { onPaused() }
    }
    SizeSheetEffects(sheetOpen, uiState, onSizeSheetOpened)

    CompositionLocalProvider(
        LocalMoveTimeline provides timeline,
        LocalBoardOverlayLink provides link,
    ) {
        val haptics = rememberHapticsController(settings.haptics, clock)
        HapticsEffects(haptics, screenBoard, nudge)
        val cards = rememberOverlayController(screenOverlay, screenBoard, haptics, commands)
        // D-199: while a card is shown, the chrome and the U, Ctrl+Z, Backspace, N and R keys act
        // like its buttons, so its exit runs; otherwise they go to the ViewModel.
        val chromeUndo: () -> Unit =
            remember(cards, onUndo) {
                { if (cards.cardActs) cards.act(CardAction.UNDO) else onUndo() }
            }
        val chromeNewGame: () -> Unit =
            remember(cards, onNewGameRequest) {
                { if (cards.cardActs) cards.act(CardAction.NEW_GAME) else onNewGameRequest() }
            }
        val keyInput =
            remember(onMove, chromeUndo, chromeNewGame) {
                GameKeyInput(
                    modalOpen,
                    cardShown,
                    boardInputEnabled,
                    onMove,
                    chromeUndo,
                    chromeNewGame,
                )
            }
        val callbacks =
            remember(
                onMove,
                chromeUndo,
                chromeNewGame,
                keyInput,
                onBoardPresented,
                onHeaderPresented,
            ) {
                GameCallbacks(
                    onMove = onMove,
                    onUndo = chromeUndo,
                    onNewGame = chromeNewGame,
                    onBoardKey = keyInput::onBoardKey,
                    onBoardPresented = onBoardPresented,
                    onHeaderPresented = onHeaderPresented,
                )
            }
        Box(
            modifier.fillMaxSize().background(background).onPreviewKeyEvent(keyInput::onPreviewKey),
        ) {
            SnackbarEffects(screenBoard, message, snackbars)
            // The gesture surface (D-051): the content inside safeDrawing, around the arrangement
            // and the overlay, so a swipe that starts on an exiting card reaches the board (I6).
            Box(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .swipeToMove(swipeConfig, boardInputEnabled, onMove),
            ) {
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
                    cardShown = cardShown,
                    boardBounds = boardBounds,
                    callbacks = callbacks,
                    chromeActions = chromeActions,
                    menuOpen = menuOpen,
                )
                OverlayHost(cards, effects, screenBoard, boardBounds.rect, boardFocus)
            }
            if (dialogOpen) {
                NewGameDialog(
                    onConfirm = {
                        dialogOpen = false
                        onNewGame()
                    },
                    onDismiss = { dialogOpen = false },
                )
            }
            if (sheetOpen) {
                // D-007: a row switches size with no confirmation; the current size only closes.
                BoardSizeSheet(
                    options = sizeOptions,
                    onSelect = { size ->
                        sheetOpen = false
                        onSizeSelected(size)
                    },
                    onDismissRequest = { sheetOpen = false },
                )
            }
        }
    }
}

/**
 * The board-size sheet's readings (D-236, D-245): while [sheetOpen], each time [ui] becomes ready
 * with no size switch loading, [onSizeSheetOpened] reads the rows. With the game ready that is once
 * per opening, also when the sheet comes back open; a sheet opened or restored before the load
 * fills in once it ends. [ui] is read in the effect only, so the screen does not recompose.
 */
@Composable
private fun SizeSheetEffects(
    sheetOpen: Boolean,
    ui: State<GameUiState>,
    onSizeSheetOpened: () -> Unit,
) {
    LaunchedEffect(sheetOpen, ui, onSizeSheetOpened) {
        if (sheetOpen) {
            snapshotFlow { ui.value.let { it.ready && !it.busy } }
                .collect { readable -> if (readable) onSizeSheetOpened() }
        }
    }
}

/**
 * The screen scope's overlay over the board (ARCHITECTURE.md §5.3; DESIGN_SYSTEM.md §4.7 steps 8–9;
 * D-187): the [BoardScrim], then `ContentEffectsCanvas` over the whole game content (confetti and
 * sparkles, above the scrim), then the card ([WinCard] or [GameOverCard] in [OverlayCard]) on the
 * board's bounds [boardRect], all composed from [cards] outside the arrangement. Share sends the
 * shown card's summary (D-008, D-200). When a card leaves, the board takes focus back (D-053).
 */
@Composable
private fun OverlayHost(
    cards: OverlayController,
    effects: ContentEffects,
    board: State<BoardUi>,
    boardRect: State<IntRect>,
    boardFocus: FocusRequester,
) {
    val shown = cards.shown
    val share = rememberShare()
    if (cards.scrimActive) {
        BoardScrim(
            visuals = cards.visuals,
            n = { board.value.size.n },
            board = boardRect,
            blocking = shown != OverlayUi.None && !cards.exiting,
        )
    }
    ContentEffectsCanvas(effects, Modifier.fillMaxSize())
    if (shown != OverlayUi.None) {
        val focus = remember { FocusRequester() }
        val actions =
            remember(cards, shown, share) {
                CardActions(
                    onKeepGoing = { cards.act(CardAction.KEEP_GOING) },
                    onNewGame = { cards.act(CardAction.NEW_GAME) },
                    onUndo = { cards.act(CardAction.UNDO) },
                    onShare = { shown.summaryOrNull()?.let(share) },
                )
            }
        val interactive = cards.interactive && !cards.exiting
        OverlayCard(
            key = shown,
            board = boardRect,
            visuals = cards.visuals,
            regular = { measuring ->
                Card(shown, false, interactive, measuring, cards, actions, focus)
            },
            compact = { measuring ->
                Card(shown, true, interactive, measuring, cards, actions, focus)
            },
        )
    }
    val hadCard = remember { Holder(false) }
    LaunchedEffect(shown == OverlayUi.None) {
        if (shown == OverlayUi.None && hadCard.value) boardFocus.requestFocus()
        hadCard.value = shown != OverlayUi.None
    }
}

/** The card of [shown], Regular or [compact]. */
@Composable
private fun Card(
    shown: OverlayUi,
    compact: Boolean,
    interactive: Boolean,
    measuring: Boolean,
    cards: OverlayController,
    actions: CardActions,
    focus: FocusRequester,
) {
    when (shown) {
        is OverlayUi.Win ->
            WinCard(shown, compact, interactive, measuring, cards.visuals, actions, focus)
        is OverlayUi.GameOver ->
            GameOverCard(shown, compact, interactive, measuring, cards.visuals, actions, focus)
        OverlayUi.None -> Unit
    }
}

/**
 * The Share action (D-008, D-200; FEATURES.md F10): `ShareText` of a card's summary with the
 * current locale's templates (`share_summary`, the `moves_count` and `undos_count` plurals with
 * game numerals, `undos_none`), sent through the chooser titled `share_chooser_title`.
 */
@Composable
private fun rememberShare(): (GameSummary) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val summary = stringResource(R.string.share_summary)
    val noUndo = stringResource(R.string.undos_none)
    val chooser = stringResource(R.string.share_chooser_title)
    return remember(context, resources, summary, noUndo, chooser) {
        val strings =
            ShareStrings(
                summary = summary,
                moves = { count, value ->
                    resources.getQuantityString(R.plurals.moves_count, count, value)
                },
                undos = { count, value ->
                    resources.getQuantityString(R.plurals.undos_count, count, value)
                },
                noUndo = noUndo,
            )
        val send: (GameSummary) -> Unit = { s ->
            shareText(context, ShareText.build(s, strings), chooser)
        }
        send
    }
}

/** The summary a card shares; `None` has none. */
private fun OverlayUi.summaryOrNull(): GameSummary? =
    when (this) {
        OverlayUi.None -> null
        is OverlayUi.Win -> summary
        is OverlayUi.GameOver -> summary
    }

/** A plain holder, not snapshot state, written only from effects. */
private class Holder<T>(var value: T)

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

/** The chrome commands of the arrangement: the size chip, the game menu and navigation (D-236). */
@Immutable
private class ChromeCallbacks(
    /** Opens the board-size sheet (DESIGN_SYSTEM.md §6.7). */
    val onSizeChip: () -> Unit,
    /** Opens or closes the game menu (DESIGN_SYSTEM.md §6.1). */
    val onMenuOpenChange: (Boolean) -> Unit,
    /** Pushes a sub-screen (ARCHITECTURE.md §7.1). */
    val onNavigate: (NavKey) -> Unit,
)

/**
 * The STACKED arrangement (DESIGN_SYSTEM.md §5.3 STACKED row, §5.5 A): inside `safeDrawing` (the
 * screen's gesture surface around it, D-051) and the D-188 margins, the FULL header, the board and
 * the controls bar, as wide as the board, whose side is `min(max(availW, BoardFloor), availH −
 * (HeaderHeight + 2·StackGap + ControlsBarHeight), BoardMax)`. It publishes the board's bounds in
 * its parent into [boardBounds] for the screen's overlay and requests the board's focus when it
 * appears (D-053), unless a card is shown then ([cardShown]): the card's primary button has it
 * (D-081), and the board takes it back when the card leaves.
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
    cardShown: State<Boolean>,
    boardBounds: BoardBounds,
    callbacks: GameCallbacks,
    chromeActions: ChromeCallbacks,
    menuOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    // Placed inside safeDrawing by the screen's gesture surface: the window is this box plus the
    // safe insets.
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
        val windowWidth = maxWidth + safeWidth
        val windowHeight = maxHeight + safeHeight
        val marginLeft =
            horizontalMargin(
                windowWidth,
                with(density) { gestures.getLeft(this, direction).toDp() },
            )
        val marginRight =
            horizontalMargin(
                windowWidth,
                with(density) { gestures.getRight(this, direction).toDp() },
            )
        val marginVertical = verticalMargin(windowHeight)
        val availW = maxWidth - marginLeft - marginRight
        val availH = maxHeight - marginVertical * 2
        val chrome =
            GameDimens.HeaderHeight + GameDimens.StackGap * 2 + GameDimens.ControlsBarHeight
        val side =
            minOf(maxOf(availW, GameDimens.BoardFloor), availH - chrome, GameDimens.BoardMax)
                .coerceAtLeast(Dp.Hairline)
        val titleMax =
            if (windowWidth >= GameDimens.WidthExpandedMin) {
                GameDimens.TitleMaxExpanded
            } else {
                GameDimens.TitleMax
            }
        val origin = with(density) { IntOffset(marginLeft.roundToPx(), marginVertical.roundToPx()) }
        StackedLayout(
            boardSide = side,
            boardBounds = boardBounds,
            boardOrigin = origin,
            header = {
                GameHeader(
                    header = header,
                    board = board,
                    ui = ui,
                    loadingSize = loadingSize,
                    titleMax = titleMax,
                    effects = effects,
                    onHeaderPresented = callbacks.onHeaderPresented,
                    onSizeChipClick = chromeActions.onSizeChip,
                )
            },
            board = { BoardSlot(board, ui, nudge, clock, boardFocus, callbacks) },
            controls = {
                GameControls(
                    board = board,
                    ui = ui,
                    onUndo = callbacks.onUndo,
                    onNewGame = callbacks.onNewGame,
                    menuOpen = menuOpen,
                    onMenuOpenChange = chromeActions.onMenuOpenChange,
                    onNavigate = chromeActions.onNavigate,
                )
            },
            snackbar = { GameSnackbarHost(snackbars) },
            modifier =
                Modifier.fillMaxSize()
                    .absolutePadding(
                        left = marginLeft,
                        top = marginVertical,
                        right = marginRight,
                        bottom = marginVertical,
                    ),
        )
        // A card shown when the arrangement appears keeps its primary button focused (D-081).
        LaunchedEffect(boardFocus) { if (!cardShown.value) boardFocus.requestFocus() }
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
 * The board's bounds in the screen's inset box, for the overlay (the scrim and the card placed on
 * the board): written by the arrangement's measure, read by the overlay in layout only.
 */
@Stable
private class BoardBounds {
    private val state = mutableStateOf(IntRect.Zero)

    val rect: State<IntRect>
        get() = state

    /** Stores [rect] when it changed, without observing the old value. */
    fun publish(rect: IntRect) {
        if (Snapshot.withoutReadObservation { state.value } != rect) state.value = rect
    }
}

/**
 * Places the STACKED column (DESIGN_SYSTEM.md §5.5 A): [header] at the top and [controls] at the
 * bottom, both [boardSide] wide, and the [boardSide] square [board] centred between them, so the
 * two gaps flex equally (at least `StackGap` by the board-size rule); the board's bounds in the
 * parent, offset by [boardOrigin] (this layout's position there), go to [boardBounds] in measure.
 * The [snackbar] host, board − 16 dp wide, sits in the gap between the board and the controls when
 * that gap is at least its height + 16 dp, and otherwise over the board, bottom-centred, 8 dp above
 * its bottom edge: it never covers the controls bar (D-186).
 */
@Composable
private fun StackedLayout(
    boardSide: Dp,
    boardBounds: BoardBounds,
    boardOrigin: IntOffset,
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
        val x = (width - side) / 2
        val controlsY = height - controlsPlaceable.height
        val boardY = headerPlaceable.height + (controlsY - headerPlaceable.height - side) / 2
        // Measured before the screen's overlay, which reads the bounds in its own measure pass.
        val rect =
            IntRect(IntOffset(boardOrigin.x + x, boardOrigin.y + boardY), IntSize(side, side))
        boardBounds.publish(rect)
        layout(width, height) {
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
