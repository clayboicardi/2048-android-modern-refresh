@file:Suppress("TooManyFunctions") // the four arrangements of DESIGN_SYSTEM.md §5.4 and the probe

package com.clayboicardi.game2048.game

import android.content.res.Configuration
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.BoardView
import com.clayboicardi.game2048.game.board.ChromeMeasure
import com.clayboicardi.game2048.game.board.ContentEffects
import com.clayboicardi.game2048.game.board.GameLayoutSpec
import com.clayboicardi.game2048.game.board.HeaderStyle
import com.clayboicardi.game2048.game.board.LayoutWindow
import com.clayboicardi.game2048.game.board.resolveGameLayout
import com.clayboicardi.game2048.game.header.GameHeader
import com.clayboicardi.game2048.game.header.HeaderForm
import com.clayboicardi.game2048.game.header.form
import com.clayboicardi.game2048.game.input.DirectionPad
import com.clayboicardi.game2048.game.input.SwipeConfig
import com.clayboicardi.game2048.game.input.swipeToMove
import com.clayboicardi.game2048.game.overlay.FirstLaunchHint
import com.clayboicardi.game2048.game.overlay.HintContent
import com.clayboicardi.game2048.ui.components.GameSnackbarHost
import com.clayboicardi.game2048.ui.components.LayoutMode
import com.clayboicardi.game2048.ui.components.rememberGameButtonLabelFits
import com.clayboicardi.game2048.ui.components.stackedSnackbarTop
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import com.clayboicardi.game2048.ui.theme.MotionLevel
import com.clayboicardi.game2048.ui.theme.TileSkin
import kotlin.math.max

/**
 * True inside `GameLayout`'s measurement probe of the chrome (DESIGN_SYSTEM.md §5.3;
 * ARCHITECTURE.md §4.11 item 4): a never-placed copy whose chrome composables run no effect, start
 * no moment and report no presentation (`GameHeader`, `GameControls`).
 */
@Suppress("ComposeCompositionLocalUsage") // D-188: the probe's copy of the chrome measures only
val LocalChromeProbe = staticCompositionLocalOf { false }

/**
 * The test tag of the root node of the arrangement [mode] draws (relied on by the layout tests).
 */
fun layoutTag(mode: LayoutMode): String = "game:layout:${mode.name}"

/**
 * The resolved [GameLayoutSpec] on the root node of the arrangement drawn, read by the layout tests
 * (F17-AC23, F18-AC10: the board equals `resolveGameLayout`'s). A custom key, which accessibility
 * services never see.
 */
internal val LayoutSpecKey = SemanticsPropertyKey<GameLayoutSpec>("GameLayoutSpec")

/** The root of the arrangement of [spec]: its [layoutTag], [LayoutSpecKey] and [group]. */
private fun Modifier.arrangementRoot(spec: GameLayoutSpec, group: Boolean = true): Modifier =
    fillMaxSize().testTag(layoutTag(spec.mode)).semantics {
        this[LayoutSpecKey] = spec
        if (group) isTraversalGroup = true
    }

/**
 * The commands the arrangements' readers send (method references of the screen's ViewModel and the
 * screen's chrome requests).
 */
@Immutable
internal class GameCallbacks(
    val onMove: (Direction) -> Unit,
    val onUndo: () -> Unit,
    /** The screen's New game request: it confirms while the game is in progress (D-007). */
    val onNewGame: () -> Unit,
    val onBoardKey: (KeyEvent) -> Boolean,
    val onBoardPresented: (Long) -> Unit,
    val onHeaderPresented: (Long) -> Unit,
)

/**
 * The chrome commands of the arrangements: the size chip, the game menu, navigation (D-236) and the
 * first-launch hint's "Got it" (D-201).
 */
@Immutable
internal class ChromeCallbacks(
    /** Opens the board-size sheet (DESIGN_SYSTEM.md §6.7). */
    val onSizeChip: () -> Unit,
    /** Opens or closes the game menu (DESIGN_SYSTEM.md §6.1). */
    val onMenuOpenChange: (Boolean) -> Unit,
    /** Pushes a sub-screen (ARCHITECTURE.md §7.1). */
    val onNavigate: (NavKey) -> Unit,
    /** `GameViewModel.onHintDismissed`. */
    val onHintDismissed: () -> Unit,
)

/**
 * The board's bounds in the screen's inset box, for the overlay (the scrim and the card placed on
 * the board): written by the arrangement's layout, read by the overlay in layout only.
 */
@Stable
internal class BoardBounds {
    private val state = mutableStateOf(IntRect.Zero)

    val rect: State<IntRect>
        get() = state

    /** Stores [rect] when it changed, without observing the old value. */
    fun publish(rect: IntRect) {
        if (Snapshot.withoutReadObservation { state.value } != rect) state.value = rect
    }
}

/**
 * What `GameLayout` tells the screen: whether SCROLL is drawn, written in its measure and read by
 * the screen's gesture surface at gesture time only (D-051, D-188: in SCROLL only the board takes
 * swipes).
 */
@Stable
internal class LayoutReport {
    private val scroll = mutableStateOf(false)

    val isScroll: Boolean
        get() = scroll.value

    fun publish(mode: LayoutMode) {
        val now = mode == LayoutMode.SCROLL
        if (Snapshot.withoutReadObservation { scroll.value } != now) scroll.value = now
    }
}

/** Everything the arrangements read and call; built once by `GameScreen`. */
@Immutable
@Suppress("LongParameterList") // one field per input of DESIGN_SYSTEM.md §5.4's placement matrix
internal class LayoutInputs(
    /** The `board` and `header` collectors, never a retention state (ARCHITECTURE.md §4.11). */
    val board: State<BoardUi>,
    val header: State<HeaderUi>,
    val ui: State<GameUiState>,
    val nudge: State<Nudge?>,
    val talkBack: State<Boolean>,
    val clock: MonotonicClock,
    val effects: ContentEffects,
    val snackbars: SnackbarHostState,
    val boardFocus: FocusRequester,
    /** A card is shown (the screen scope's overlay is not `None`). */
    val cardShown: State<Boolean>,
    /** A card is composed (`OverlayController.shown`): SCROLL brings the board into view. */
    val cardComposed: State<Boolean>,
    val boardBounds: BoardBounds,
    val report: LayoutReport,
    val callbacks: GameCallbacks,
    val chrome: ChromeCallbacks,
    val swipeConfig: State<SwipeConfig>,
    val boardInputEnabled: State<Boolean>,
)

/**
 * The Game content's adaptive layout (DESIGN_SYSTEM.md §5.2–§5.5, §5.8; ARCHITECTURE.md §4.11 item
 * 2, §10.3; D-051, D-075, D-091, D-095, D-186, D-188), placed inside `safeDrawing` by the screen.
 * It is a `SubcomposeLayout` whose measure block
 * 1. reads the window (its own size plus the `safeDrawing` insets, the `systemGestures` side
 *    insets, the TalkBack pad and the first-launch hint) and applies the margins per axis;
 * 2. answers `resolveGameLayout`'s `ChromeMeasure` from a measurement probe of the chrome, a copy
 *    of the header in both styles, the hint, the controls bar, the status line, the pad row, both
 *    rails and the panel, each subcomposed at the width asked, under `LocalChromeProbe` and
 *    `clearAndSetSemantics {}`, never placed. Its Score and Best show `10^(d − 1)` for the live
 *    digit count `d` of the larger value, read from the `header` collector through
 *    `derivedStateOf`, so the probe recomposes only when `d` changes;
 * 3. subcomposes the resolved arrangement in the slot `GameLayoutSpec.mode`, which first makes its
 *    own presentation scope (`rememberPresented` over the `board` and `header` collectors), so an
 *    arrangement switch composes a new board and header from the settled values and replays nothing
 *    (D-047, D-095). The scope and the arrangement are keyed on whether the effective motion level
 *    is Full, so a level change does the same (D-252). The default `SubcomposeLayoutState` keeps no
 *    slot for reuse, and `movableContentOf` is not used.
 *
 * Within one arrangement, under Full motion, the board, rails and panel move with `animateBounds`
 * (`overlaySpatial`); under Reduced and Off they snap. The snackbar host is placed per arrangement
 * (D-186, D-188). [loadingSize] is the size chip's Loading size; [menuOpen] the screen's saveable
 * menu state.
 */
@Composable
internal fun GameLayout(
    inputs: LayoutInputs,
    loadingSize: Int,
    menuOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val probe = rememberProbeState(inputs)
    val hintShown = remember(inputs) { derivedStateOf { inputs.ui.value.showFirstLaunchHint } }
    val boardSize = remember(inputs) { derivedStateOf { inputs.ui.value.size.n } }
    val safe = WindowInsets.safeDrawing
    val gestures = WindowInsets.systemGestures
    // What the probe's heights depend on besides its inputs: a change measures them anew.
    val locals =
        ProbeLocals(
            LocalDensity.current,
            LocalConfiguration.current,
            LocalLayoutDirection.current,
            LocalTileSkin.current,
            LocalEffectiveMotion.current,
        )
    SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val talkBack = inputs.talkBack.value
        val window = window(constraints, safe, gestures, talkBack, hintShown.value)
        probe.begin(
            ProbeKey(locals, probe.digits.value, talkBack, hintShown.value, boardSize.value),
        )
        val chrome = ProbeChrome(this, probe, window)
        val spec = resolveGameLayout(window, chrome)
        inputs.report.publish(spec.mode)
        val railTitle =
            spec.mode == LayoutMode.THREE_COLUMN &&
                chrome.statsRailWithTitle(spec.rail) <= window.availH
        val status = if (talkBack) chrome.status(statusWidth(spec, window)) else 0f
        probe.end()
        val frame = ArrangementFrame(spec, window, railTitle, status)
        val content =
            subcompose(spec.mode) {
                // The arrangement scope (ARCHITECTURE.md §4.11 item 2): made first in this slot,
                // and made anew with the arrangement when the motion level turns Full or leaves it
                // (D-252), so the new composition starts from the settled board and header.
                key(LocalEffectiveMotion.current == MotionLevel.FULL) {
                    val board = inputs.board.rememberPresented(sameBoardPresentation)
                    val header = inputs.header.rememberPresented(sameHeaderPresentation)
                    ArrangementSlot(frame, board, header, inputs, loadingSize, menuOpen)
                }
            }
        val placeables = content.map { it.measure(Constraints.fixed(width, height)) }
        layout(width, height) { placeables.forEach { it.place(0, 0) } }
    }
}

/** The window whose content inside the insets is [constraints]' maximum, in dp (§5.2). */
private fun SubcomposeMeasureScope.window(
    constraints: Constraints,
    safe: WindowInsets,
    gestures: WindowInsets,
    talkBack: Boolean,
    hint: Boolean,
): LayoutWindow {
    val left = safe.getLeft(this, layoutDirection).toDp().value
    val right = safe.getRight(this, layoutDirection).toDp().value
    val top = safe.getTop(this).toDp().value
    val bottom = safe.getBottom(this).toDp().value
    return LayoutWindow(
        width = constraints.maxWidth.toDp().value + left + right,
        height = constraints.maxHeight.toDp().value + top + bottom,
        safeLeft = left,
        safeTop = top,
        safeRight = right,
        safeBottom = bottom,
        gestureLeft = gestures.getLeft(this, layoutDirection).toDp().value,
        gestureRight = gestures.getRight(this, layoutDirection).toDp().value,
        talkBack = talkBack,
        hint = hint,
    )
}

/** The resolved arrangement with its window, as one arrangement slot reads it. */
@Immutable
private data class ArrangementFrame(
    val spec: GameLayoutSpec,
    val window: LayoutWindow,
    /**
     * THREE_COLUMN: the stats rail keeps its title (it is dropped first, DESIGN_SYSTEM.md §5.4).
     */
    val railTitle: Boolean,
    /**
     * The status line's reserved height in dp while the pad is shown, else 0: the probe's
     * [ReservedStatusLine] at the width the arrangement draws it (DESIGN_SYSTEM.md §5.3, D-265).
     */
    val status: Float,
) {
    /** The STACKED and SCROLL content width `w0` (DESIGN_SYSTEM.md §5.3). */
    val w0: Float
        get() = contentWidth(window)

    /** The title's `autoSize` maximum: 64 sp in an expanded window, else 56 sp (§3.5, §5.1). */
    val titleMax: TextUnit
        get() = titleMaxFor(window)
}

/** The STACKED and SCROLL content width `w0` of [window] (DESIGN_SYSTEM.md §5.3). */
private fun contentWidth(window: LayoutWindow): Float =
    minOf(maxOf(window.availW, GameDimens.BoardFloor.value), GameDimens.BoardMax.value)

/**
 * The width [spec]'s arrangement draws the status line at (DESIGN_SYSTEM.md §5.4): the STACKED
 * column (the board under the FULL header, `w0` under the COMPACT one), the SCROLL column, the rail
 * or the panel.
 */
private fun statusWidth(spec: GameLayoutSpec, window: LayoutWindow): Float =
    when (spec.mode) {
        LayoutMode.STACKED ->
            if (spec.header == HeaderStyle.FULL) spec.board else contentWidth(window)
        LayoutMode.SCROLL -> contentWidth(window)
        LayoutMode.THREE_COLUMN -> spec.rail
        LayoutMode.SIDE_PANEL -> spec.panel
    }

/** The title's `autoSize` maximum for [window] (DESIGN_SYSTEM.md §3.5: 64 sp when expanded). */
private fun titleMaxFor(window: LayoutWindow): TextUnit =
    if (window.width >= GameDimens.WidthExpandedMin.value) GameDimens.TitleMaxExpanded
    else GameDimens.TitleMax

/** The arrangement of [frame]'s mode (DESIGN_SYSTEM.md §5.4), over its presented values. */
@Composable
private fun ArrangementSlot(
    frame: ArrangementFrame,
    board: State<BoardUi>,
    header: State<HeaderUi>,
    inputs: LayoutInputs,
    loadingSize: Int,
    menuOpen: Boolean,
) {
    val presented = Presented(board, header)
    // `GameLayout` keys this slot and its presentation scope on whether the level is Full: a change
    // of the motion level (App turns Full on once the game is ready, or the system animator scale
    // changes) composes the arrangement anew, statically, from the settled values, because
    // `animateBounds` added to children already placed would offset them by their own position
    // once more, and a presentation scope kept across it would replay the last move (D-252).
    // Under Reduced and Off the bounds snap on F0.
    val animate = LocalEffectiveMotion.current == MotionLevel.FULL
    val start = remember { SlotStart() }
    SideEffect { start.composed = true }
    val parts = ArrangementParts(frame, presented, inputs, loadingSize, menuOpen, start)
    LookaheadScope {
        val bounds: Modifier.() -> Modifier = {
            if (animate) animateBounds(this@LookaheadScope, boundsTransform = BoundsSpring)
            else this
        }
        when (frame.spec.mode) {
            LayoutMode.STACKED -> StackedArrangement(frame, parts, inputs, bounds)
            LayoutMode.SCROLL -> ScrollArrangement(frame, parts, inputs, bounds)
            LayoutMode.THREE_COLUMN -> ThreeColumnArrangement(frame, parts, inputs, bounds)
            LayoutMode.SIDE_PANEL -> SidePanelArrangement(frame, parts, inputs, bounds)
        }
    }
    // D-053, D-240: the board takes focus when an arrangement appears (or is composed anew for a
    // motion change), unless a card is shown, whose primary button has it (D-081).
    LaunchedEffect(inputs.boardFocus) {
        if (!inputs.cardShown.value) inputs.boardFocus.requestFocus()
    }
}

/**
 * The visible chrome of one arrangement slot (DESIGN_SYSTEM.md §5.4): each part at one call site
 * for the slot's whole life, so the STACKED header going FULL ↔ COMPACT keeps its composition.
 */
@Stable
private class ArrangementParts(
    val frame: ArrangementFrame,
    presented: Presented,
    val inputs: LayoutInputs,
    val loadingSize: Int,
    val menuOpen: Boolean,
    private val start: SlotStart,
) {
    /** The slot's presented board and header (its arrangement scope). */
    val board: State<BoardUi> = presented.board
    val header: State<HeaderUi> = presented.header

    val talkBack: Boolean
        get() = inputs.talkBack.value

    /** The header of [form], with More where [form] puts it (D-249). */
    @Composable
    fun Header(form: HeaderForm, modifier: Modifier = Modifier) {
        val more: (@Composable () -> Unit)? =
            when (form) {
                HeaderForm.FULL,
                HeaderForm.COMPACT -> {
                    { More(containerSize = GameDimens.HeaderMoreSize) }
                }
                HeaderForm.PANEL -> {
                    { More() }
                }
                HeaderForm.RAIL -> null
            }
        GameHeader(
            header = header,
            board = board,
            ui = inputs.ui,
            loadingSize = loadingSize,
            form = form,
            titleMax = frame.titleMax,
            effects = inputs.effects,
            onHeaderPresented = inputs.callbacks.onHeaderPresented,
            onSizeChipClick = inputs.chrome.onSizeChip,
            modifier = modifier,
            more = more,
            showTitle = form != HeaderForm.RAIL || frame.railTitle,
        )
    }

    /** More (`GameMoreButton`) with the screen's menu. */
    @Composable
    fun More(modifier: Modifier = Modifier, containerSize: DpSize? = null) {
        GameMoreButton(
            menuOpen = menuOpen,
            onMenuOpenChange = inputs.chrome.onMenuOpenChange,
            onNavigate = inputs.chrome.onNavigate,
            modifier = modifier,
            containerSize = containerSize,
        )
    }

    /** The board of this slot: its presented `BoardUi` with the chrome state it needs. */
    @Composable
    fun Board(modifier: Modifier = Modifier) {
        val state = inputs.ui.value
        val callbacks = inputs.callbacks
        BoardView(
            board = board.value,
            nudge = inputs.nudge.value,
            status = state.status,
            canUndo = state.canUndo,
            loading = !state.ready,
            clock = inputs.clock,
            onMove = callbacks.onMove,
            onUndo = callbacks.onUndo,
            onNewGame = callbacks.onNewGame,
            onBoardKey = callbacks.onBoardKey,
            onPresented = callbacks.onBoardPresented,
            modifier = modifier,
            focusRequester = inputs.boardFocus,
        )
    }

    /** The first-launch hint (DESIGN_SYSTEM.md §6.2), with [padding] around it. */
    @Composable
    fun Hint(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues()) {
        val state = inputs.ui.value
        FirstLaunchHint(
            visible = state.showFirstLaunchHint,
            size = state.size,
            talkBack = talkBack,
            onDismiss = inputs.chrome.onHintDismissed,
            modifier = modifier,
            padding = padding,
        )
    }

    @Composable
    fun Controls(form: ControlsForm, modifier: Modifier = Modifier) {
        GameControls(
            board = board,
            ui = inputs.ui,
            onUndo = inputs.callbacks.onUndo,
            onNewGame = inputs.callbacks.onNewGame,
            modifier = modifier,
            form = form,
            padShown = talkBack,
        )
    }

    /** The status line, at least as tall as its reservation (DESIGN_SYSTEM.md §6.6; D-265). */
    @Composable
    fun Status(modifier: Modifier = Modifier) {
        StatusLine(board, header, inputs.nudge, modifier.heightIn(min = frame.status.dp))
    }

    /**
     * The pad and New game beside it, or below it when its label does not fit the column
     * (DESIGN_SYSTEM.md §5.5 E, §6.6; D-249, D-266).
     */
    @Composable
    fun PadRow(modifier: Modifier = Modifier) {
        PadRowLayout(
            pad = { Pad() },
            newGame = { form -> Controls(form, Modifier.traversal(TRAVERSAL_CONTROLS)) },
            modifier = modifier,
        )
    }

    /**
     * The direction pad (D-082). Under Full, a pad that appears in a live arrangement fades in with
     * `fastEffects` (MOTION_SPEC.md §3.16); one composed with its arrangement is already visible.
     */
    @Composable
    fun Pad(modifier: Modifier = Modifier) {
        val state = inputs.ui.value
        val full = LocalEffectiveMotion.current == MotionLevel.FULL
        val shown = remember { Animatable(if (full && start.composed) 0f else 1f) }
        LaunchedEffect(shown) {
            shown.animateTo(1f, GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD))
        }
        DirectionPad(
            onMove = inputs.callbacks.onMove,
            onUndo = inputs.callbacks.onUndo,
            canUndo = state.canUndo,
            modifier = modifier.graphicsLayer { alpha = shown.value }.traversal(TRAVERSAL_CONTROLS),
            enabled = state.ready,
        )
    }

    /** The snackbar host, read after the controls. */
    @Composable
    fun Snackbar(modifier: Modifier = Modifier) {
        GameSnackbarHost(inputs.snackbars, modifier.traversal(TRAVERSAL_SNACKBAR))
    }
}

/** The retention states of one arrangement scope (ARCHITECTURE.md §4.11 item 2). */
private class Presented(val board: State<BoardUi>, val header: State<HeaderUi>)

/** Whether an arrangement slot has committed its first composition (a plain holder). */
private class SlotStart {
    var composed = false
}

/**
 * The pad row (DESIGN_SYSTEM.md §5.5 E, §6.6; D-249, D-266): the [pad], 16 dp and New game's
 * `PadControlsWidth` column, centred on one row. When New game's label does not lay out on two
 * lines in that column without breaking a word (a large font scale, a long translation), New game
 * takes the full width 8 dp below the pad instead. [newGame] composes New game in the form given.
 */
@Composable
private fun PadRowLayout(
    pad: @Composable () -> Unit,
    newGame: @Composable (ControlsForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.new_game)
    if (rememberGameButtonLabelFits(label, GameDimens.PadControlsWidth)) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(GameDimens.Space16, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
        ) {
            pad()
            newGame(ControlsForm.PAD_COLUMN)
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            pad()
            newGame(ControlsForm.BELOW_PAD)
        }
    }
}

/**
 * STACKED (DESIGN_SYSTEM.md §5.3, §5.5 A, D, E): the header (FULL: as wide as the board; COMPACT:
 * `w0`), the hint with 8 dp gaps, the board, and the controls bar (or, with the pad, the status
 * line and the pad row) at the bottom of the available area. The board sits between them with equal
 * flexible gaps of at least `gap(h)`; the snackbar in that lower gap when it fits, else over the
 * board (D-186).
 */
@Composable
private fun StackedArrangement(
    frame: ArrangementFrame,
    parts: ArrangementParts,
    inputs: LayoutInputs,
    bounds: Modifier.() -> Modifier,
) {
    val style = frame.spec.header
    val talkBack = parts.talkBack
    Layout(
        contents =
            listOf(
                { parts.Header(style.form(), Modifier.traversal(TRAVERSAL_HEADER)) },
                { parts.Hint(padding = PaddingValues(vertical = GameDimens.Space8)) },
                { BoardBox(parts, Modifier.bounds()) },
                {
                    if (talkBack) {
                        Column(verticalArrangement = Arrangement.spacedBy(GameDimens.Space8)) {
                            parts.Status(Modifier.traversal(TRAVERSAL_CONTROLS))
                            parts.PadRow()
                        }
                    } else {
                        parts.Controls(ControlsForm.BAR, Modifier.traversal(TRAVERSAL_CONTROLS))
                    }
                },
                { parts.Snackbar() },
            ),
        modifier = Modifier.arrangementRoot(frame.spec),
    ) { slots, constraints ->
        val (header, hint, board) = slots
        val bottom = slots[COLUMN_BOTTOM]
        val snackbar = slots[COLUMN_SNACKBAR]
        val m = Margins(this, frame.window)
        val side = frame.spec.board.dp.roundToPx()
        val column = if (style == HeaderStyle.FULL) side else frame.w0.dp.roundToPx()
        val headerP = header.measureWidth(column)
        val hintP = hint.measureWidth(column)
        val bottomP = bottom.measureWidth(column)
        val boardP = board.single().measure(Constraints.fixed(side, side))
        val snackP = snackbar.measureWidth((side - GameDimens.Space16.roundToPx()).coerceAtLeast(0))
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val x = m.left + (m.availW - column) / 2
        val boardX = m.left + (m.availW - side) / 2
        val top = m.vertical
        val headerH = headerP?.height ?: 0
        val gap = gapPx(style)
        // The hint with its two 8 dp gaps replaces gap(h); while it shrinks out, gap(h) is the
        // floor.
        val minAbove = top + headerH + max(hintP?.height ?: 0, gap)
        val belowGap = if (talkBack) padGapPx(style) else gap
        val bottomAt = height - m.vertical - (bottomP?.height ?: 0)
        val free = (bottomAt - belowGap - minAbove - side).coerceAtLeast(0)
        val boardY = minAbove + free / 2
        // The bottom chrome is as tall as the probe reserved; should it ever be taller (the frame
        // of a size switch that still shows the previous status, D-265), it moves down into the
        // margin rather than over the board.
        val bottomTop = max(bottomAt, boardY + side + belowGap)
        if (!isLookingAhead) {
            val placed = IntOffset(m.x(boardX, side), boardY)
            inputs.boardBounds.publish(IntRect(placed, IntSize(side, side)))
        }
        layout(width, height) {
            headerP?.let { it.place(m.x(x, it.width), top) }
            hintP?.let { it.place(m.x(x, it.width), top + headerH) }
            boardP.place(m.x(boardX, side), boardY)
            bottomP?.let { it.place(m.x(x, it.width), bottomTop) }
            snackP?.let { snack ->
                val snackY =
                    stackedSnackbarTop(
                        boardBottom = boardY + side,
                        controlsTop = bottomTop,
                        height = snack.height,
                        gapMargin = GameDimens.Space16.roundToPx(),
                        boardMargin = GameDimens.Space8.roundToPx(),
                    )
                snack.place(m.x(boardX + (side - snack.width) / 2, snack.width), snackY)
            }
        }
    }
}

/**
 * SCROLL (DESIGN_SYSTEM.md §5.3, §5.5 G; D-051, D-188): one column of width `w0` in
 * `verticalScroll`, top-aligned: the COMPACT header, the hint if shown, the board (which alone
 * takes swipes), then the status line and the pad row with the pad, or the controls bar. The board
 * fills the first screen with the header (and the status line); the rest starts below the fold. The
 * snackbar overlays the board. When a card composes, the column scrolls until the whole board is
 * visible: `animateScrollTo(…, overlaySpatial)` under Full, `scrollTo` otherwise.
 */
@Composable
private fun ScrollArrangement(
    frame: ArrangementFrame,
    parts: ArrangementParts,
    inputs: LayoutInputs,
    bounds: Modifier.() -> Modifier,
) {
    val scroll = rememberScrollState()
    val span = remember { BoardSpan() }
    val talkBack = parts.talkBack
    val level = LocalEffectiveMotion.current
    LaunchedEffect(scroll, span) {
        snapshotFlow { inputs.cardComposed.value }
            .collect { composed -> if (composed) bringBoardIntoView(scroll, span, level) }
    }
    Box(Modifier.arrangementRoot(frame.spec, group = false).verticalScroll(scroll)) {
        Layout(
            contents =
                listOf(
                    { parts.Header(HeaderForm.COMPACT, Modifier.traversal(TRAVERSAL_HEADER)) },
                    { parts.Hint(padding = PaddingValues(top = GameDimens.Space8)) },
                    {
                        BoardBox(
                            parts,
                            Modifier.bounds()
                                .swipeToMove(
                                    inputs.swipeConfig,
                                    inputs.boardInputEnabled,
                                    inputs.callbacks.onMove,
                                ),
                        )
                    },
                    {
                        if (talkBack) {
                            Column(verticalArrangement = Arrangement.spacedBy(GameDimens.Space8)) {
                                parts.Status(Modifier.traversal(TRAVERSAL_CONTROLS))
                                parts.PadRow()
                            }
                        } else {
                            parts.Controls(
                                ControlsForm.BAR,
                                Modifier.traversal(TRAVERSAL_CONTROLS),
                            )
                        }
                    },
                    { parts.Snackbar() },
                ),
            modifier = Modifier.fillMaxWidth().semantics { isTraversalGroup = true },
        ) { slots, constraints ->
            val (header, hint, board) = slots
            val bottom = slots[COLUMN_BOTTOM]
            val snackbar = slots[COLUMN_SNACKBAR]
            val m = Margins(this, frame.window)
            val side = frame.spec.board.dp.roundToPx()
            val column = frame.w0.dp.roundToPx()
            val gap = GameDimens.Space8.roundToPx()
            val headerP = header.measureWidth(column)
            val hintP = hint.measureWidth(column)
            val boardP = board.single().measure(Constraints.fixed(side, side))
            val bottomP = bottom.measureWidth(column)
            val snackP =
                snackbar.measureWidth((side - GameDimens.Space16.roundToPx()).coerceAtLeast(0))
            val width = constraints.maxWidth
            val x = m.left + (m.availW - column) / 2
            val boardX = m.left + (m.availW - side) / 2
            val headerH = headerP?.height ?: 0
            val boardY = m.vertical + headerH + (hintP?.height ?: 0) + gap
            val bottomY = boardY + side + gap
            val height = bottomY + (bottomP?.height ?: 0) + m.vertical
            span.top = boardY
            span.bottom = boardY + side
            layout(width, height) {
                headerP?.let { it.place(m.x(x, it.width), m.vertical) }
                hintP?.let { it.place(m.x(x, it.width), m.vertical + headerH) }
                boardP.place(m.x(boardX, side), boardY)
                bottomP?.let { it.place(m.x(x, it.width), bottomY) }
                snackP?.let { snack ->
                    snack.place(
                        m.x(boardX + (side - snack.width) / 2, snack.width),
                        boardY + side - GameDimens.Space8.roundToPx() - snack.height,
                    )
                }
                // The board's bounds in the screen's inset box, where the column is scrolled.
                if (!isLookingAhead) {
                    val placed = IntOffset(m.x(boardX, side), boardY - scroll.value)
                    inputs.boardBounds.publish(IntRect(placed, IntSize(side, side)))
                }
            }
        }
    }
}

/** The board's span in the SCROLL column, written by its layout and read by the scroll effect. */
private class BoardSpan {
    var top = 0
    var bottom = 0
}

/** Scrolls [scroll] until the board ([span]) is wholly in the viewport (FEATURES.md F18 item 8). */
private suspend fun bringBoardIntoView(scroll: ScrollState, span: BoardSpan, level: MotionLevel) {
    val viewport = scroll.viewportSize
    val target =
        when {
            span.top < scroll.value -> span.top
            span.bottom > scroll.value + viewport -> span.bottom - viewport
            else -> return
        }.coerceIn(0, scroll.maxValue)
    if (level == MotionLevel.FULL) {
        scroll.animateScrollTo(target, GameMotion.overlaySpatial(SCROLL_THRESHOLD_PX))
    } else {
        scroll.scrollTo(target)
    }
}

/**
 * THREE_COLUMN (DESIGN_SYSTEM.md §5.3, §5.5 B): `[stats rail] 12 [board] 12 [actions rail]`,
 * centred in `availW`; the rails are full-height columns, the board on the vertical centre line.
 * The stats rail is the RAIL header (its title dropped first when the rail overflows); the actions
 * rail holds the hint (replacing Undo and New game while shown), the status line and the pad while
 * touch exploration is on, Undo and New game, and More last; the snackbar sits at its bottom
 * (D-186). The rails swap in RTL.
 */
@Composable
private fun ThreeColumnArrangement(
    frame: ArrangementFrame,
    parts: ArrangementParts,
    inputs: LayoutInputs,
    bounds: Modifier.() -> Modifier,
) {
    val talkBack = parts.talkBack
    val hint = inputs.ui.value.showFirstLaunchHint
    Layout(
        contents =
            listOf(
                { parts.Header(HeaderForm.RAIL, Modifier.bounds().traversal(TRAVERSAL_HEADER)) },
                { BoardBox(parts, Modifier.bounds()) },
                {
                    Column(
                        modifier = Modifier.bounds(),
                        verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
                    ) {
                        parts.Hint(Modifier.traversal(TRAVERSAL_CONTROLS))
                        if (talkBack) {
                            parts.Status(Modifier.traversal(TRAVERSAL_CONTROLS))
                            parts.Pad()
                        }
                        if (!hint) {
                            parts.Controls(
                                ControlsForm.RAIL,
                                Modifier.traversal(TRAVERSAL_CONTROLS),
                            )
                        }
                        parts.More(Modifier.traversal(TRAVERSAL_MORE))
                    }
                },
                { parts.Snackbar() },
            ),
        modifier = Modifier.arrangementRoot(frame.spec),
    ) { slots, constraints ->
        val (stats, board, actions) = slots
        val snackbar = slots[RAILS_SNACKBAR]
        val m = Margins(this, frame.window)
        val rail = frame.spec.rail.dp.roundToPx()
        val side = frame.spec.board.dp.roundToPx()
        val gap = GameDimens.RailGap.roundToPx()
        val group = 2 * rail + 2 * gap + side
        val x0 = m.left + (m.availW - group) / 2
        val railConstraints = Constraints(minWidth = rail, maxWidth = rail, maxHeight = m.availH)
        val statsP = stats.single().measure(railConstraints)
        val actionsP = actions.single().measure(railConstraints)
        val boardP = board.single().measure(Constraints.fixed(side, side))
        val snackP = snackbar.measureWidth(rail)
        val boardX = x0 + rail + gap
        val boardY = m.vertical + (m.availH - side) / 2
        if (!isLookingAhead) {
            val placed = IntOffset(m.x(boardX, side), boardY)
            inputs.boardBounds.publish(IntRect(placed, IntSize(side, side)))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val actionsX = boardX + side + gap
            statsP.place(m.x(x0, rail), m.vertical)
            boardP.place(m.x(boardX, side), boardY)
            actionsP.place(m.x(actionsX, rail), m.vertical)
            snackP?.place(m.x(actionsX, snackP.width), m.vertical + m.availH - snackP.height)
        }
    }
}

/**
 * SIDE_PANEL (DESIGN_SYSTEM.md §5.3, §5.5 C; D-188): the board and, 32 dp beside it, the panel, as
 * tall as the board and top-aligned with it, the group centred in `availW` and the board on the
 * vertical centre line. The panel holds the PANEL header (title row with More, the chips, the size
 * chip), the hint, Undo and New game on one row, and the status line and pad while touch
 * exploration is on; the snackbar sits at its bottom, on the board's bottom edge (D-186). The panel
 * moves to the left of the board in RTL.
 */
@Composable
private fun SidePanelArrangement(
    frame: ArrangementFrame,
    parts: ArrangementParts,
    inputs: LayoutInputs,
    bounds: Modifier.() -> Modifier,
) {
    val talkBack = parts.talkBack
    Layout(
        contents =
            listOf(
                { BoardBox(parts, Modifier.bounds()) },
                {
                    Column(
                        modifier = Modifier.bounds(),
                        verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
                    ) {
                        parts.Header(HeaderForm.PANEL, Modifier.traversal(TRAVERSAL_HEADER))
                        parts.Hint(Modifier.traversal(TRAVERSAL_CONTROLS))
                        parts.Controls(
                            ControlsForm.PANEL_ROW,
                            Modifier.traversal(TRAVERSAL_CONTROLS),
                        )
                        if (talkBack) {
                            parts.Status(Modifier.traversal(TRAVERSAL_CONTROLS))
                            parts.Pad()
                        }
                    }
                },
                { parts.Snackbar() },
            ),
        modifier = Modifier.arrangementRoot(frame.spec),
    ) { (board, panel, snackbar), constraints ->
        val m = Margins(this, frame.window)
        val width = frame.spec.panel.dp.roundToPx()
        val side = frame.spec.board.dp.roundToPx()
        val gap = GameDimens.PanelGap.roundToPx()
        val x0 = m.left + (m.availW - (side + gap + width)) / 2
        val boardY = m.vertical + (m.availH - side) / 2
        val boardP = board.single().measure(Constraints.fixed(side, side))
        val panelP =
            panel
                .single()
                .measure(Constraints(minWidth = width, maxWidth = width, maxHeight = side))
        val snackP = snackbar.measureWidth(width)
        if (!isLookingAhead) {
            val placed = IntOffset(m.x(x0, side), boardY)
            inputs.boardBounds.publish(IntRect(placed, IntSize(side, side)))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val panelX = x0 + side + gap
            boardP.place(m.x(x0, side), boardY)
            panelP.place(m.x(panelX, width), boardY)
            snackP?.place(m.x(panelX, snackP.width), boardY + side - snackP.height)
        }
    }
}

/** The STACKED and SCROLL `contents` after the header, the hint and the board. */
private const val COLUMN_BOTTOM = 3
private const val COLUMN_SNACKBAR = 4

/** THREE_COLUMN's snackbar, after the stats rail, the board and the actions rail. */
private const val RAILS_SNACKBAR = 3

/** The board in its traversal group (read after the header, before the controls; D-080). */
@Composable
private fun BoardBox(parts: ArrangementParts, modifier: Modifier = Modifier) {
    Box(modifier.traversal(TRAVERSAL_BOARD), propagateMinConstraints = true) { parts.Board() }
}

/** A traversal group at [index] among the arrangement's groups (D-080, D-249). */
private fun Modifier.traversal(index: Float): Modifier = semantics {
    isTraversalGroup = true
    traversalIndex = index
}

/**
 * The margins and available area of [window] in px (DESIGN_SYSTEM.md §5.2, §5.3), and the
 * arrangement's mirroring in RTL (§5.7): positions are computed left to right and [x] mirrors them
 * inside the available area, so the physical safe margins stay where the insets are.
 */
private class Margins(scope: IntrinsicMeasureScope, window: LayoutWindow) {
    val left: Int
    val vertical: Int
    val availW: Int
    val availH: Int
    private val rtl = scope.layoutDirection == LayoutDirection.Rtl

    /** The placed left edge of a part [width] px wide whose left-to-right left edge is [ltr]. */
    fun x(ltr: Int, width: Int): Int = if (rtl) 2 * left + availW - ltr - width else ltr

    init {
        with(scope) {
            left = window.marginLeft.dp.roundToPx()
            vertical = window.marginVertical.dp.roundToPx()
            availW = window.availW.dp.roundToPx()
            availH = window.availH.dp.roundToPx()
        }
    }
}

/** The first part of this slot measured at [width], or `null` when it composes nothing. */
private fun List<Measurable>.measureWidth(width: Int): Placeable? =
    firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width))

/** `gap(h)` in px: `StackGap` under the FULL header, `Space8` under the COMPACT one. */
private fun Density.gapPx(style: HeaderStyle): Int =
    (if (style == HeaderStyle.FULL) GameDimens.StackGap else GameDimens.Space8).roundToPx()

/** Board ↔ status line with the pad: 16 dp under FULL, 8 dp under COMPACT (§5.3). */
private fun Density.padGapPx(style: HeaderStyle): Int =
    (if (style == HeaderStyle.FULL) GameDimens.Space16 else GameDimens.Space8).roundToPx()

// ---- The measurement probe (DESIGN_SYSTEM.md §5.3, D-188). ----

/**
 * What the probe composes from: the live digit count `d` (from the `header` collector through
 * `derivedStateOf`) and its placeholder header, `10^(d − 1)` for both values.
 */
@Stable
private class ProbeState(val inputs: LayoutInputs) {
    val digits: State<Int> = derivedStateOf {
        val h = inputs.header.value
        max(h.score, h.best).toString().length
    }
    val header: State<HeaderUi> = derivedStateOf {
        val value = PLACEHOLDER_BASE.pow(digits.value - 1)
        HeaderUi(score = value, best = value)
    }
    val ui: State<GameUiState> = mutableStateOf(GameUiState(ready = true))

    /** The measured terms of [key]; cleared when the key changes. */
    val heights = HashMap<ProbeSlot, Float>()

    /**
     * True only inside the measure pass that composes copies: once they are measured, the copies
     * recompose to nothing, so no probe node stays in the semantics tree (as `OverlayCard`'s).
     */
    val probing = mutableStateOf(false)

    private var key: ProbeKey? = null

    /** Starts a measure pass for [key]: the heights of another key are dropped. */
    fun begin(key: ProbeKey) {
        if (key != this.key) {
            heights.clear()
            this.key = key
        }
    }

    /** Called before a copy is composed in this pass. */
    fun compose() {
        if (!Snapshot.withoutReadObservation { probing.value }) probing.value = true
    }

    /** Ends the measure pass: the copies composed in it empty themselves. */
    fun end() {
        if (Snapshot.withoutReadObservation { probing.value }) probing.value = false
    }
}

/** The composition locals the probe's heights depend on. */
private data class ProbeLocals(
    val density: Density,
    val configuration: Configuration,
    val direction: LayoutDirection,
    val skin: TileSkin,
    val motion: MotionLevel,
)

/** Everything the probe's heights depend on. */
private data class ProbeKey(
    val locals: ProbeLocals,
    val digits: Int,
    val talkBack: Boolean,
    val hint: Boolean,
    val boardSize: Int,
)

@Composable
private fun rememberProbeState(inputs: LayoutInputs): ProbeState =
    remember(inputs) { ProbeState(inputs) }

private fun Long.pow(exponent: Int): Long {
    var result = 1L
    repeat(exponent.coerceAtLeast(0)) { result *= this }
    return result
}

/** One probe subcomposition: a chrome term at a width in px. */
private data class ProbeSlot(val term: ProbeTerm, val style: HeaderStyle?, val widthPx: Int)

private enum class ProbeTerm {
    HEADER,
    HINT,
    CONTROLS,
    STATUS,
    PAD_ROW,
    RAILS,
    STATS_RAIL_TITLED,
    PANEL,
}

/**
 * `resolveGameLayout`'s `ChromeMeasure` (DESIGN_SYSTEM.md §5.3): each term is the height of the
 * probe's copy of that chrome measured at the asked width, subcomposed once per measure pass.
 */
private class ProbeChrome(
    private val scope: SubcomposeMeasureScope,
    private val probe: ProbeState,
    private val window: LayoutWindow,
) : ChromeMeasure {

    override fun header(style: HeaderStyle, width: Float): Float =
        measure(ProbeTerm.HEADER, width, style) { ProbeHeader(style.form()) }

    override fun hint(width: Float): Float =
        measure(ProbeTerm.HINT, width) {
            HintContent(probe.inputs.ui.value.size, window.talkBack, onDismiss = {})
        }

    override fun controls(width: Float): Float =
        measure(ProbeTerm.CONTROLS, width) { ProbeControls(ControlsForm.BAR) }

    /** The status line's reservation at [width]: [ReservedStatusLine] (D-265). */
    override fun status(width: Float): Float =
        measure(ProbeTerm.STATUS, width) { ReservedStatusLine(probe.digits.value) }

    override fun padRow(width: Float): Float =
        measure(ProbeTerm.PAD_ROW, width) {
            PadRowLayout(
                pad = { DirectionPad(onMove = {}, onUndo = {}, canUndo = false, enabled = false) },
                newGame = { form -> ProbeControls(form) },
            )
        }

    override fun rails(rail: Float): Float {
        val status = reservedStatus(rail)
        return measure(ProbeTerm.RAILS, rail) {
            TallestOf(
                { ProbeHeader(HeaderForm.RAIL, showTitle = false) },
                { ProbeActionsRail(status) },
            )
        }
    }

    /** The stats rail with its title, at [rail]: the title stays while this fits `availH`. */
    fun statsRailWithTitle(rail: Float): Float =
        measure(ProbeTerm.STATS_RAIL_TITLED, rail) { ProbeHeader(HeaderForm.RAIL) }

    override fun panel(panel: Float): Float {
        val status = reservedStatus(panel)
        return measure(ProbeTerm.PANEL, panel) {
            Column(verticalArrangement = Arrangement.spacedBy(GameDimens.Space8)) {
                ProbeHeader(HeaderForm.PANEL)
                if (window.hint) ProbeHint()
                ProbeControls(ControlsForm.PANEL_ROW)
                if (window.talkBack) {
                    ProbeStatus(status)
                    DirectionPad(onMove = {}, onUndo = {}, canUndo = false, enabled = false)
                }
            }
        }
    }

    /** The status line's reservation at [width] while the pad is shown, else 0. */
    private fun reservedStatus(width: Float): Float = if (window.talkBack) status(width) else 0f

    private fun measure(
        term: ProbeTerm,
        width: Float,
        style: HeaderStyle? = null,
        body: @Composable () -> Unit,
    ): Float {
        val px = with(scope) { width.coerceAtLeast(0f).dp.roundToPx() }
        val slot = ProbeSlot(term, style, px)
        return probe.heights.getOrPut(slot) {
            probe.compose()
            val placeables =
                scope
                    .subcompose(slot) { if (probe.probing.value) Probe(body) }
                    .map { it.measure(Constraints(minWidth = px, maxWidth = px)) }
            with(scope) { (placeables.maxOfOrNull { it.height } ?: 0).toDp().value }
        }
    }

    @Composable
    private fun ProbeHeader(form: HeaderForm, showTitle: Boolean = true) {
        val more: (@Composable () -> Unit)? =
            when (form) {
                HeaderForm.FULL,
                HeaderForm.COMPACT -> {
                    { ProbeMore(GameDimens.HeaderMoreSize) }
                }
                HeaderForm.PANEL -> {
                    { ProbeMore(null) }
                }
                HeaderForm.RAIL -> null
            }
        GameHeader(
            header = probe.header,
            board = probe.inputs.board,
            ui = probe.ui,
            loadingSize = BoardSize.DEFAULT.n,
            form = form,
            titleMax = titleMax(),
            effects = probe.inputs.effects,
            onHeaderPresented = {},
            onSizeChipClick = {},
            more = more,
            showTitle = showTitle,
        )
    }

    @Composable
    private fun ProbeMore(size: DpSize?) {
        GameMoreButton(
            menuOpen = false,
            onMenuOpenChange = {},
            onNavigate = {},
            containerSize = size,
            enabled = false,
        )
    }

    @Composable
    private fun ProbeHint() {
        HintContent(probe.inputs.ui.value.size, window.talkBack, onDismiss = {})
    }

    @Composable
    private fun ProbeControls(form: ControlsForm) {
        GameControls(
            board = probe.inputs.board,
            ui = probe.ui,
            onUndo = {},
            onNewGame = {},
            form = form,
            padShown = window.talkBack,
        )
    }

    /**
     * The status line inside a rail or the panel, as the arrangement draws it: blank, at its
     * reserved height [status] in dp (D-265).
     */
    @Composable
    private fun ProbeStatus(status: Float) {
        StatusLine(null, null, null, Modifier.heightIn(min = status.dp), text = "")
    }

    /** The actions rail's content (DESIGN_SYSTEM.md §5.4): what the arrangement composes there. */
    @Composable
    private fun ProbeActionsRail(status: Float) {
        Column(verticalArrangement = Arrangement.spacedBy(GameDimens.Space8)) {
            if (window.hint) ProbeHint()
            if (window.talkBack) {
                ProbeStatus(status)
                DirectionPad(onMove = {}, onUndo = {}, canUndo = false, enabled = false)
            }
            if (!window.hint) ProbeControls(ControlsForm.RAIL)
            ProbeMore(null)
        }
    }

    private fun titleMax(): TextUnit = titleMaxFor(window)
}

/** One probe copy: under `LocalChromeProbe`, its semantics cleared, filling the asked width. */
@Composable
private fun Probe(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChromeProbe provides true) {
        Box(
            modifier = Modifier.clearAndSetSemantics {}.testTag(PROBE_TAG),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** Lays out [first] and [second] at the same width; its height is the taller one's. */
@Composable
private fun TallestOf(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Layout(contents = listOf(first, second)) { (a, b), constraints ->
        val pa = a.map { it.measure(constraints) }
        val pb = b.map { it.measure(constraints) }
        val height = (pa + pb).maxOfOrNull { it.height } ?: 0
        layout(constraints.minWidth, height) {}
    }
}

/** The board's, rails' and panel's bounds transform under Full (DESIGN_SYSTEM.md §5.8). */
private val BoundsSpring = BoundsTransform { _, _ ->
    GameMotion.overlaySpatial(Rect.VisibilityThreshold)
}

// Traversal order of the arrangements' groups (D-080, D-249): header, More, board, controls.
private const val TRAVERSAL_HEADER = 0f
private const val TRAVERSAL_MORE = 0.5f
private const val TRAVERSAL_BOARD = 1f
private const val TRAVERSAL_CONTROLS = 2f
private const val TRAVERSAL_SNACKBAR = 3f

/** The probe's root, never placed (for tests that assert it stays out of the tree). */
private const val PROBE_TAG = "game:chromeProbe"

/** `10^(d − 1)`: the placeholder value with the live digit count. */
private const val PLACEHOLDER_BASE = 10L

/** The scroll target's visibility threshold, in px. */
private const val SCROLL_THRESHOLD_PX = 1f
