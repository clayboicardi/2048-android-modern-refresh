package com.clayboicardi.game2048.engine

/** Complete game state, including the generator state and the undo history (D-030, D-039). */
data class GameState(
    val size: BoardSize,
    val seed: Long,
    val board: Board,
    val score: Long,
    val rngState: Long,
    val status: GameStatus,
    val moveCount: Int,
    /** -1 when unset. */
    val keepGoingAtMove: Int,
    val moveLog: MoveLog,
    /** snapshots[k] = state at move 32k. */
    val snapshots: List<Snapshot>,
    val undoCount: Int,
) {
    init {
        // O(1) structural checks only; they run on every copy and make a corrupt DTO fail fast.
        require(board.size == size)
        require(moveCount >= 0 && moveLog.size == moveCount)
        require(snapshots.size == moveCount / Game.SNAPSHOT_INTERVAL + 1)
        require(keepGoingAtMove in -1..moveCount)
        require(score >= 0 && undoCount >= 0)
    }
}

val GameState.isWon: Boolean
    get() = status == GameStatus.WON

/** Used for stats (D-044). */
val GameState.hasWon: Boolean
    get() = board.maxExp() >= size.targetExp

val GameState.isKeepPlaying: Boolean
    get() = status == GameStatus.KEEP_PLAYING

val GameState.isOver: Boolean
    get() = status == GameStatus.OVER

/** D-007 / Conventions: moveCount >= 1 and status PLAYING or KEEP_PLAYING. */
val GameState.isInProgress: Boolean
    get() = moveCount >= 1 && (status == GameStatus.PLAYING || status == GameStatus.KEEP_PLAYING)

val GameState.canUndo: Boolean
    get() = moveCount > 0
