package com.clayboicardi.game2048.game

import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.game.board.BoardEventKind
import kotlinx.collections.immutable.ImmutableList

/** Chrome-level state of the Game screen (ARCHITECTURE.md §4.7, D-024). */
@Immutable
data class GameUiState(
    val ready: Boolean = false,
    /** True while a size switch loads; input is ignored (F02). */
    val busy: Boolean = false,
    val size: BoardSize = BoardSize.DEFAULT,
    val status: GameStatus = GameStatus.PLAYING,
    val canUndo: Boolean = false,
    /** `GameState.isInProgress` (D-007): New game asks for confirmation. */
    val inProgress: Boolean = false,
    /** D-201: `!onboardingDone` and the start game has `moveCount == 0 && undoCount == 0`. */
    val showFirstLaunchHint: Boolean = false,
)

/**
 * Score header state (ARCHITECTURE.md §4.7, §4.11). Every header emitted with a new `BoardUi`
 * carries that board's `moveId`, `event.kind` and `epoch`; presentation reports change only
 * `lastDelta`.
 */
@Immutable
data class HeaderUi(
    val score: Long = 0,
    val best: Long = 0,
    /** The "+N" of the last move; 0 = none. Set to 0 once presented (§4.11). */
    val lastDelta: Long = 0,
    /** = the `moveId` of the `BoardUi` this header accompanies; keys the "+N" float. */
    val deltaMoveId: Long = 0,
    /** = that `BoardUi`'s `event.kind` when emitted; `onHeaderPresented` keeps it. */
    val event: BoardEventKind = BoardEventKind.Restored,
    /**
     * = that `BoardUi`'s `epoch`; with `event`, tells a move from undo, new game and size switch.
     */
    val epoch: Long = 0,
    /** Keys the once-per-game new-best moment (D-044); -1 = none. */
    val newBestMoveId: Long = -1,
    /** = the current `SavedGame.newBestAnnounced`: the static NEW BEST style (§4.11). */
    val newBestAnnounced: Boolean = false,
)

/** How a card enters (ARCHITECTURE.md §4.11, D-047). */
@Immutable
sealed interface OverlayEntry {
    /** Interactive immediately, no entry animation. */
    data object Restored : OverlayEntry

    /** Run the D-064 entry choreography; one-shot (§4.11). */
    data class Animated(val moveId: Long) : OverlayEntry
}

/** The win or game-over card, if any (ARCHITECTURE.md §4.7). */
@Immutable
sealed interface OverlayUi {
    data object None : OverlayUi

    /** `summary` feeds Share (D-200). */
    data class Win(val targetExp: Int, val summary: GameSummary, val entry: OverlayEntry) :
        OverlayUi

    data class GameOver(val summary: GameSummary, val entry: OverlayEntry) : OverlayUi
}

/** The same overlay with `entry = Restored`; `None` stays `None` (ARCHITECTURE.md §4.11). */
fun OverlayUi.restored(): OverlayUi =
    when (this) {
        OverlayUi.None -> this
        is OverlayUi.Win -> copy(entry = OverlayEntry.Restored)
        is OverlayUi.GameOver -> copy(entry = OverlayEntry.Restored)
    }

/** What a card and Share show about the finished game (D-008, D-200). */
@Immutable
data class GameSummary(
    val size: BoardSize,
    val score: Long,
    val best: Long,
    val bestTileExp: Int,
    val moves: Int,
    val elapsedMs: Long,
    val undos: Int,
    /** Row-major exponents, for the share emoji grid (D-008). */
    val cells: ImmutableList<Int>,
)

/** An invalid move (D-035): shakes the board; never changes `BoardUi`. */
@Immutable data class Nudge(val id: Long, val direction: Direction)
