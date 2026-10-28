package com.clayboicardi.game2048.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clayboicardi.game2048.data.game.GameRepository
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.platform.SeedSource
import com.clayboicardi.game2048.data.records.MAX_PENDING_FINALIZATIONS
import com.clayboicardi.game2048.data.records.RecordsRepository
import com.clayboicardi.game2048.data.records.countsWhenReplaced
import com.clayboicardi.game2048.data.records.toFinalization
import com.clayboicardi.game2048.data.settings.SettingsRepository
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.engine.canUndo
import com.clayboicardi.game2048.engine.isInProgress
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.game.board.BoardPresenter
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.kind
import com.clayboicardi.game2048.game.feedback.HapticsMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The state holder of the Game screen (ARCHITECTURE.md §4.7–§4.11; D-024, D-043, D-044, D-047,
 * D-052, D-054, D-185, D-201, D-214). Activity-scoped, so the splash gate can read [ready].
 *
 * Every command runs on the main thread, synchronously and without suspension (D-024, D-052): the
 * engine call, the new `BoardUi`, header, overlay and `uiState`, and the fire-and-forget saves.
 * Only the load at start and the load of a size switch suspend, in `viewModelScope`, and only
 * inside the repositories' reads. Every change of the current game submits one save (D-043).
 */
@Suppress("TooManyFunctions") // the 11 commands and reports of §4.7 plus the §4.8–§4.9 steps
class GameViewModel(
    private val games: GameRepository,
    private val records: RecordsRepository,
    private val settings: SettingsRepository,
    private val clock: MonotonicClock,
    private val seedSource: SeedSource,
) : ViewModel() {
    private val presenter = BoardPresenter()
    private val playClock = PlayClock()

    /** The current game, `null` until the first load completes. */
    private var current: SavedGame? = null

    /** True while a size switch loads (F02-AC6): every command is ignored. */
    private var busy = false

    /** D-201: shown from load until the first accepted move or "Got it". */
    private var hint = false

    private var nudgeId = 0L

    private val _ready = MutableStateFlow(false)

    /** True once settings, records and the current game are loaded (§4.9, D-112). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _board = MutableStateFlow(emptyBoard(BoardSize.DEFAULT))

    /** The empty board of the default size until ready (§4.12), then the presenter's boards. */
    val board: StateFlow<BoardUi> = _board.asStateFlow()

    private val _header = MutableStateFlow(HeaderUi())
    val header: StateFlow<HeaderUi> = _header.asStateFlow()

    private val _overlay = MutableStateFlow<OverlayUi>(OverlayUi.None)
    val overlay: StateFlow<OverlayUi> = _overlay.asStateFlow()

    private val _nudge = MutableStateFlow<Nudge?>(null)

    /** Invalid moves only (D-035); never changes `board`. */
    val nudge: StateFlow<Nudge?> = _nudge.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    /**
     * §4.8: ignored unless ready, not busy and no card is shown (D-054). `Blocked` does nothing;
     * `Invalid` emits only a [Nudge]; `Moved` commits, presents and saves.
     */
    fun onMove(direction: Direction) {
        val game = current?.takeIf { acceptsInput() && _overlay.value == OverlayUi.None } ?: return
        when (val outcome = engineOrCorrupt(game) { Game.move(game.state, direction) }) {
            null,
            is MoveOutcome.Blocked -> Unit
            is MoveOutcome.Invalid -> _nudge.value = Nudge(++nudgeId, direction)
            is MoveOutcome.Moved -> applyMove(game, direction, outcome)
        }
    }

    /**
     * §4.9 Undo, also from a card (D-199): an `Undo` epoch, overlay `None`, one save. A no-op at
     * `moveCount == 0`; the screen shows `nothing_to_undo` (M3).
     */
    fun onUndo() {
        val game = current?.takeIf { acceptsInput() } ?: return
        val undone = engineOrCorrupt(game) { Game.undo(game.state) } ?: return
        val next = commitState(game, undone)
        emitEpoch(next, BoardEvent.Undo)
        _overlay.value = OverlayUi.None
        games.save(next)
        publishUi()
    }

    /**
     * §4.9 New game: folds the running play time into the old game (D-043), then D-044's
     * [replaceWithFresh] and a `NewGame` epoch. The UI confirms first.
     */
    fun onNewGame() {
        val game = current?.takeIf { acceptsInput() } ?: return
        val folded = game.copy(elapsedMs = playClock.elapsed(clock.nowMs()))
        becomeCurrent(
            replaceWithFresh(folded, folded.state.size),
            BoardEvent.NewGame,
            OverlayUi.None,
        )
    }

    /**
     * §4.9 Keep going, from the win card: `KEEP_PLAYING`, or the game-over card at once (D-036).
     */
    fun onKeepGoing() {
        val game = current?.takeIf { acceptsInput() && it.state.status == GameStatus.WON } ?: return
        val next = commitState(game, Game.keepGoing(game.state))
        _overlay.value =
            if (next.state.status == GameStatus.OVER) {
                OverlayUi.GameOver(
                    summary(next),
                    OverlayEntry.Animated(_board.value.moveId),
                )
            } else {
                OverlayUi.None
            }
        games.save(next)
        publishUi()
    }

    /**
     * §4.9 Size switch (D-007): folds and saves the current game, stores `lastBoardSize`, then
     * loads the other size's game (or a fresh one) while `busy`. No confirmation, no finalization.
     */
    fun onSizeSelected(size: BoardSize) {
        val game = current?.takeIf { acceptsInput() && it.state.size != size } ?: return
        val folded = game.copy(elapsedMs = playClock.elapsed(clock.nowMs()))
        current = folded
        games.save(folded)
        settings.update { it.copy(lastBoardSize = size.n) }
        busy = true
        publishUi()
        viewModelScope.launch {
            val loaded = restore(size)
            busy = false
            becomeCurrent(loaded, BoardEvent.SizeSwitched, overlayFor(loaded))
        }
    }

    /** D-201 "Got it". */
    fun onHintDismissed() {
        hint = false
        settings.update { it.copy(onboardingDone = true) }
        publishUi()
    }

    /** The Game entry is resumed: play time may accrue again (D-043). */
    fun onResumed() {
        playClock.resume(clock.nowMs())
    }

    /** `ON_PAUSE`: folds the play time into a save and retries a failed records write (D-043). */
    fun onPaused() {
        val now = clock.nowMs()
        playClock.pause(now)
        if (!_ready.value) return
        // While a size switch loads, the old game was saved when the switch began.
        val game = current?.takeIf { !busy }
        if (game != null) {
            val next = game.copy(elapsedMs = playClock.elapsed(now))
            current = next
            games.save(next)
        }
        records.retryFailedWrite()
    }

    /**
     * §4.11: the board [moveId] was presented; it becomes `BoardPresenter.settled`. Only the
     * presentation changes; a stale or repeated report is a no-op.
     */
    fun onBoardPresented(moveId: Long) {
        val presented = _board.value
        if (presented.moveId == moveId && presented.event != BoardEvent.Restored) {
            _board.value = BoardPresenter.settled(presented)
        }
    }

    /** §4.11: the header moments of [deltaMoveId] were presented; `lastDelta` becomes 0. */
    fun onHeaderPresented(deltaMoveId: Long) {
        val presented = _header.value
        if (presented.deltaMoveId == deltaMoveId && presented.lastDelta != 0L) {
            _header.value = presented.copy(lastDelta = 0)
        }
    }

    /** §4.11: the card entry of [moveId] was presented; `Animated(moveId)` becomes `Restored`. */
    fun onOverlayPresented(moveId: Long) {
        val presented = _overlay.value
        val entry =
            when (presented) {
                OverlayUi.None -> null
                is OverlayUi.Win -> presented.entry
                is OverlayUi.GameOver -> presented.entry
            }
        if (entry == OverlayEntry.Animated(moveId)) _overlay.value = presented.restored()
    }

    /** §4.9 Load at start. */
    private suspend fun load() {
        val size = BoardSize.of(settings.awaitLoaded().lastBoardSize)
        records.awaitLoaded()
        val start = restore(size)
        hint =
            !settings.state.value.onboardingDone &&
                start.state.moveCount == 0 &&
                start.state.undoCount == 0
        becomeCurrent(start, BoardEvent.Restored, overlayFor(start))
        _ready.value = true
        publishUi()
        viewModelScope.launch { recoverOtherSizes(size) }
    }

    /** §4.8 step 4, after the engine accepted the move. */
    private fun applyMove(game: SavedGame, direction: Direction, outcome: MoveOutcome.Moved) {
        val now = clock.nowMs()
        playClock.onAccepted(now, outcome.state)
        val score = outcome.state.score
        val newBest = !game.newBestAnnounced && game.bestAtStart > 0 && score > game.bestAtStart
        val next =
            game.copy(
                state = outcome.state,
                elapsedMs = playClock.elapsed(now),
                newBestAnnounced = game.newBestAnnounced || newBest,
            )
        current = next
        val moved = presenter.onMoved(outcome, movedEvent(direction, outcome, newBest))
        _board.value = moved
        _header.value =
            HeaderUi(
                score = score,
                best = bestOf(next),
                lastDelta = outcome.scoreDelta,
                deltaMoveId = moved.moveId,
                event = BoardEventKind.Moved,
                epoch = moved.epoch,
                newBestMoveId = if (newBest) moved.moveId else _header.value.newBestMoveId,
                newBestAnnounced = next.newBestAnnounced,
            )
        val entry = OverlayEntry.Animated(moved.moveId)
        if (outcome.becameWon) {
            _overlay.value = OverlayUi.Win(next.state.size.targetExp, summary(next), entry)
        } else if (outcome.becameOver) {
            _overlay.value = OverlayUi.GameOver(summary(next), entry)
        }
        records.onProgress(next)
        if (hint) {
            hint = false
            settings.update { it.copy(onboardingDone = true) }
        }
        games.save(next)
        publishUi()
    }

    /** §4.8 step 4.5's event; the haptic is resolved here and nowhere else (D-185). */
    private fun movedEvent(direction: Direction, outcome: MoveOutcome.Moved, newBest: Boolean) =
        BoardEvent.Moved(
            direction = direction,
            mergedExps = outcome.mergedExps(),
            mergeCount = outcome.mergeCount,
            maxMergedExp = outcome.maxMergedExp,
            maxSlideCells = BoardPresenter.maxSlideCells(outcome.transitions, outcome.state.size.n),
            spawnIndex = outcome.spawn.index,
            scoreAfter = outcome.state.score,
            haptic = HapticsMap.forMove(outcome, newBest),
            newBest = newBest,
            becameWon = outcome.becameWon,
            becameOver = outcome.becameOver,
        )

    /** Undo and keep going: the new state with the play time folded in; `copy` keeps D-044. */
    private fun commitState(game: SavedGame, state: GameState): SavedGame {
        val now = clock.nowMs()
        playClock.onAccepted(now, state)
        return game.copy(state = state, elapsedMs = playClock.elapsed(now)).also { current = it }
    }

    /** [game] becomes the current game, presented as the epoch [event] with [overlay]. */
    private fun becomeCurrent(game: SavedGame, event: BoardEvent, overlay: OverlayUi) {
        current = game
        playClock.restart(clock.nowMs(), game.elapsedMs, game.state)
        emitEpoch(game, event)
        _overlay.value = overlay
        publishUi()
    }

    /** An epoch `BoardUi` and the header that accompanies it (§4.8 step 6, §4.11). */
    private fun emitEpoch(game: SavedGame, event: BoardEvent) {
        val epochBoard = presenter.reset(game.state, event)
        _board.value = epochBoard
        _header.value =
            HeaderUi(
                score = game.state.score,
                best = bestOf(game),
                lastDelta = 0,
                deltaMoveId = epochBoard.moveId,
                event = epochBoard.event.kind,
                epoch = epochBoard.epoch,
                // An undo keeps a running new-best moment (MOTION_SPEC.md §3.6); others end it.
                newBestMoveId =
                    if (event == BoardEvent.Undo) _header.value.newBestMoveId else NO_NEW_BEST,
                newBestAnnounced = game.newBestAnnounced,
            )
    }

    /**
     * Runs an engine [call] on [game]. D-214: an `IllegalStateException` means a restored game the
     * engine cannot play; it is replaced like a corrupt file, with nothing finalized, carried,
     * shown or logged, and the call yields `null`.
     */
    private inline fun <T : Any> engineOrCorrupt(game: SavedGame, call: () -> T?): T? =
        try {
            call()
        } catch (ignoredCorruption: IllegalStateException) {
            becomeCurrent(
                replaceWithFresh(null, game.state.size),
                BoardEvent.NewGame,
                OverlayUi.None,
            )
            null
        }

    private fun acceptsInput(): Boolean = _ready.value && !busy

    private fun publishUi() {
        val state = current?.state ?: return
        _uiState.value =
            GameUiState(
                ready = _ready.value,
                busy = busy,
                size = state.size,
                status = state.status,
                canUndo = state.canUndo,
                inProgress = state.isInProgress,
                showFirstLaunchHint = hint,
            )
    }

    /** The header's and the cards' best: the size's record, or the live score above it. */
    private fun bestOf(game: SavedGame): Long =
        maxOf(records.best(game.state.size), game.state.score)

    private fun summary(game: SavedGame): GameSummary =
        GameSummary(
            size = game.state.size,
            score = game.state.score,
            best = bestOf(game),
            bestTileExp = game.state.board.maxExp(),
            moves = game.state.moveCount,
            elapsedMs = game.elapsedMs,
            undos = game.state.undoCount,
            cells = List(game.state.board.cellCount) { game.state.board[it] }.toImmutableList(),
        )

    /** The card of a loaded game: interactive at once, no entry animation (§4.11). */
    private fun overlayFor(game: SavedGame): OverlayUi =
        when (game.state.status) {
            GameStatus.WON ->
                OverlayUi.Win(game.state.size.targetExp, summary(game), OverlayEntry.Restored)
            GameStatus.OVER -> OverlayUi.GameOver(summary(game), OverlayEntry.Restored)
            GameStatus.PLAYING,
            GameStatus.KEEP_PLAYING -> OverlayUi.None
        }

    /** §4.9 `fresh`: a new game of [size] with the next D-044 identity. */
    private fun fresh(size: BoardSize, replaced: SavedGame?): SavedGame =
        SavedGame(
            state = Game.newGame(size, seedSource.nextSeed()),
            elapsedMs = 0,
            bestAtStart = records.best(size),
            newBestAnnounced = false,
            gameId = records.nextGameId(size, replaced),
            pendingFinalizations = emptyList(),
        )

    /** New game, and step R2 of recovery (§4.9, D-044). Runs without suspension. */
    private fun replaceWithFresh(old: SavedGame?, size: BoardSize): SavedGame {
        records.retryFailedWrite()
        val counted = old?.takeIf { it.countsWhenReplaced }
        counted?.let(records::finalize)
        val carried =
            (old?.pendingFinalizations.orEmpty() + listOfNotNull(counted?.toFinalization()))
                .filterNot(records::isDurable)
                .takeLast(MAX_PENDING_FINALIZATIONS)
        val next = fresh(size, replaced = old).copy(pendingFinalizations = carried)
        games.save(next)
        return next
    }

    /** Recovery for a game read from disk: R1, then R2 for an interrupted New game (D-044). */
    private fun reconcile(game: SavedGame): SavedGame {
        records.recover(game)
        return if (records.isFinalized(game)) replaceWithFresh(game, game.state.size) else game
    }

    /** The game of [size] after recovery, or a fresh (saved) one when no file exists. */
    private suspend fun restore(size: BoardSize): SavedGame =
        games.load(size)?.let(::reconcile) ?: replaceWithFresh(null, size)

    /** After ready, for the sizes the player is not on: R1 only, never a game write (§4.9). */
    private suspend fun recoverOtherSizes(start: BoardSize) {
        for (n in BoardSize.ALL) if (n != start) games.peek(n)?.let(records::recover)
    }

    private companion object {
        /** `HeaderUi.newBestMoveId` when no new-best moment belongs to the current board. */
        const val NO_NEW_BEST = -1L
    }
}

/** The empty board frame shown until ready (ARCHITECTURE.md §4.12): no tiles, no epoch. */
private fun emptyBoard(size: BoardSize): BoardUi =
    BoardUi(
        moveId = 0,
        epoch = 0,
        size = size,
        tiles = persistentListOf(),
        cells = List(size.cellCount) { 0 }.toImmutableList(),
        scoreDelta = 0,
        event = BoardEvent.Restored,
    )

/** The exponent produced by each merge, in the engine's processing order. */
private fun MoveOutcome.Moved.mergedExps() =
    transitions
        .mapNotNull { it.mergedIntoIndex }
        .distinct()
        .map { state.board[it] }
        .toImmutableList()
