package com.clayboicardi.game2048.data.records

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameStatus

/** Everything `RecordsMath.finalize` reads from a game (D-044). */
data class Finalization(
    val size: BoardSize,
    val gameId: Long,
    val status: GameStatus,
    val score: Long,
    val moveCount: Int,
    val elapsedMs: Long,
    val undoCount: Int,
    val maxExp: Int,
)

/** D-044, D-046: the most recovery entries one game file carries (ARCHITECTURE.md §4.9). */
const val MAX_PENDING_FINALIZATIONS = 16

/** D-044: New game counts the replaced game iff it is `OVER` or scored. */
val SavedGame.countsWhenReplaced: Boolean
    get() = state.status == GameStatus.OVER || state.score > 0

/** This game's finalization input (D-044). */
fun SavedGame.toFinalization(): Finalization =
    Finalization(
        size = state.size,
        gameId = gameId,
        status = state.status,
        score = state.score,
        moveCount = state.moveCount,
        elapsedMs = elapsedMs,
        undoCount = state.undoCount,
        maxExp = state.board.maxExp(),
    )
