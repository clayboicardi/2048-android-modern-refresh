package com.clayboicardi.game2048.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation3.runtime.NavKey
import com.clayboicardi.game2048.AppViewModel
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.about.ChangelogEntry
import com.clayboicardi.game2048.about.WhatsNewSheet
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardOverlayLink
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.ContentEffects
import com.clayboicardi.game2048.game.board.ContentEffectsCanvas
import com.clayboicardi.game2048.game.board.LocalBoardOverlayLink
import com.clayboicardi.game2048.game.board.LocalRecompositionCounter
import com.clayboicardi.game2048.game.feedback.HapticsEffects
import com.clayboicardi.game2048.game.feedback.LocalHapticsEnabled
import com.clayboicardi.game2048.game.feedback.rememberHapticsController
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
import com.clayboicardi.game2048.ui.components.KeepScreenOn
import com.clayboicardi.game2048.ui.components.rememberTouchExplorationEnabled
import com.clayboicardi.game2048.ui.components.shareText
import com.clayboicardi.game2048.ui.components.showGameSnackbar
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/**
 * The Game route (`GameKey`; ARCHITECTURE.md §4.8, §4.11, §6.6; DESIGN_SYSTEM.md §6.1; D-007,
 * D-047, D-054, D-186, D-199). It collects `viewModel.board`, `header` and `overlay` once each with
 * `collectAsStateWithLifecycle()` and never reads their values in its own body, so it recomposes 0
 * times per move (MOTION_SPEC.md §7.2, `onScreen()`); it makes the screen scope's
 * `rememberPresented` states (ARCHITECTURE.md §4.11 item 2). It creates and provides the screen
 * scope's `MoveTimeline`, `HapticsController`, `ContentEffects`, board link
 * (`LocalBoardOverlayLink`) and `GameKeyInput`, applies `Modifier.swipeToMove` to the content area
 * inside `safeDrawing` and the key handlers, owns the new-game dialog, the board-size sheet and the
 * game menu (each `rememberSaveable`, ARCHITECTURE.md §7.2) and the snackbars (D-186), and reports
 * the presentations of the values its readers start. Its `OverlayController` (screen scope)
 * sequences the win and game-over cards, which the overlay host draws over the board outside the
 * arrangement (scrim, `ContentEffectsCanvas`, card; D-008, D-187, D-200); while a card is shown the
 * controls-bar Undo and New game and the U, Ctrl+Z, Backspace, N and R keys act like its buttons
 * (D-199). [appViewModel] gives the Move timing and Swipe sensitivity settings and the Loading size
 * of `AppViewModel.appearance` (the Haptics setting comes from `LocalHapticsEnabled`), and its
 * `whatsNew` makes the What's new sheet one of the modals (D-248, ARCHITECTURE.md §6.6, §7.2);
 * [clock] is the container's `MonotonicClock` (rate limits, flash limiter); [onNavigate] pushes a
 * sub-screen (`AppNavHost`'s `navigate`, ARCHITECTURE.md §7.1), called by the game menu.
 *
 * [GameLayout] draws the arrangement that `resolveGameLayout` picks (DESIGN_SYSTEM.md §5.3, §5.4;
 * D-188), each in its own subcomposition slot with its own presentation scope; the screen passes it
 * the `board` and `header` collectors, never a screen-scope retention state. Touch exploration
 * (`rememberTouchExplorationEnabled`) shows the TalkBack pad and status line (D-081, D-082), and
 * the Keep screen on setting holds the screen on while the game is in progress and Game is resumed
 * (F20).
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    appViewModel: AppViewModel,
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
    val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
    val whatsNew = appViewModel.whatsNew.collectAsStateWithLifecycle()
    // The screen scope: the readers composed outside the arrangement (haptics, snackbars, cards).
    // Each arrangement makes its own scope in its GameLayout slot (ARCHITECTURE.md §4.11 item 2).
    val screenBoard = boardIn.rememberPresented(sameBoardPresentation)
    val screenOverlay = overlayIn.rememberPresented(sameOverlayPresentation)
    val talkBack = rememberTouchExplorationEnabled()

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
    val onHintDismissed: () -> Unit = viewModel::onHintDismissed
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // D-007: New game asks first while the game is in progress, and starts at once otherwise.
    val onNewGameRequest: () -> Unit =
        remember(uiState, onNewGame) {
            { if (uiState.value.inProgress) dialogOpen = true else onNewGame() }
        }
    // ARCHITECTURE.md §6.6: the dialog, the sheets (size, What's new) and the menu are fully
    // modal; the board takes input only with no modal, no card, a loaded game and no size switch
    // loading.
    val modalOpen = remember {
        derivedStateOf { dialogOpen || sheetOpen || menuOpen || !whatsNew.value.isNullOrEmpty() }
    }
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
    val chromeActions =
        remember(onHintDismissed) {
            ChromeCallbacks(
                onSizeChip = { sheetOpen = true },
                onMenuOpenChange = { open -> menuOpen = open },
                onNavigate = { key -> currentOnNavigate.value(key) },
                onHintDismissed = onHintDismissed,
            )
        }
    val swipeConfig =
        rememberUpdatedState(
            SwipeConfig(appearance.moveTiming, appearance.swipeSensitivity.thresholdDp),
        )
    val timeline = remember { MoveTimeline() }
    val effects = remember { ContentEffects() }
    val link = remember(effects) { BoardOverlayLink(effects) }
    val snackbars = remember { SnackbarHostState() }
    val boardFocus = remember { FocusRequester() }
    val boardBounds = remember { BoardBounds() }
    val report = remember { LayoutReport() }
    // D-051, D-188: the whole content takes swipes, except in SCROLL, where only the board does.
    val surfaceSwipe =
        remember(boardInputEnabled, report) {
            derivedStateOf { boardInputEnabled.value && !report.isScroll }
        }
    val background = LocalTilePalette.current.background

    LifecycleResumeEffect(onResumed, onPaused) {
        onResumed()
        onPauseOrDispose { onPaused() }
    }
    SizeSheetEffects(sheetOpen, uiState, onSizeSheetOpened)
    KeepScreenOnWhilePlaying(appearance.keepScreenOn, uiState)

    CompositionLocalProvider(
        LocalMoveTimeline provides timeline,
        LocalBoardOverlayLink provides link,
    ) {
        val haptics = rememberHapticsController(LocalHapticsEnabled.current, clock)
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
        val cardComposed = remember(cards) { derivedStateOf { cards.shown != OverlayUi.None } }
        val inputs =
            remember(callbacks, chromeActions, cards) {
                LayoutInputs(
                    board = boardIn,
                    header = headerIn,
                    ui = uiState,
                    nudge = nudge,
                    talkBack = talkBack,
                    clock = clock,
                    effects = effects,
                    snackbars = snackbars,
                    boardFocus = boardFocus,
                    cardShown = cardShown,
                    cardComposed = cardComposed,
                    boardBounds = boardBounds,
                    report = report,
                    callbacks = callbacks,
                    chrome = chromeActions,
                    swipeConfig = swipeConfig,
                    boardInputEnabled = boardInputEnabled,
                )
            }
        Box(
            modifier.fillMaxSize().background(background).onPreviewKeyEvent(keyInput::onPreviewKey),
        ) {
            SnackbarEffects(screenBoard, message, snackbars)
            // The gesture surface (D-051): the content inside safeDrawing, around the arrangement
            // and the overlay, so a swipe that starts on an exiting card reaches the board (I6);
            // in SCROLL the board alone takes swipes (D-188).
            Box(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .swipeToMove(swipeConfig, surfaceSwipe, onMove),
            ) {
                GameLayout(
                    inputs = inputs,
                    loadingSize = appearance.lastBoardSize,
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
            WhatsNewHost(whatsNew, appViewModel::onWhatsNewDismissed)
        }
    }
}

/**
 * The What's new sheet over the Game root while [entries] (`AppViewModel.whatsNew`) holds entries
 * (D-009, D-248; ARCHITECTURE.md §7.2, §4.13). [entries] is read here only, so a change recomposes
 * this host, not the screen.
 */
@Composable
private fun WhatsNewHost(
    entries: State<ImmutableList<ChangelogEntry>?>,
    onDismissRequest: () -> Unit,
) {
    val shown = entries.value
    if (!shown.isNullOrEmpty()) WhatsNewSheet(shown, onDismissRequest)
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

/**
 * Keeps the screen on while [setting] (Keep screen on while playing, D-006) is on, the Game
 * destination is resumed and its game is in progress ([ui]; FEATURES.md F20): a card, a fresh
 * board, the Loading state and every sub-screen leave it off. [ui] is read here only, so the screen
 * does not recompose.
 */
@Composable
private fun KeepScreenOnWhilePlaying(setting: Boolean, ui: State<GameUiState>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val stage by lifecycle.currentStateAsState()
    val resumed = stage.isAtLeast(Lifecycle.State.RESUMED)
    KeepScreenOn(setting && resumed && ui.value.inProgress)
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
