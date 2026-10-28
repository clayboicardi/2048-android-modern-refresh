package com.clayboicardi.game2048.engine

/** Engine entry point (D-021, D-034–D-039). */
object Game {
    const val SNAPSHOT_INTERVAL = 32

    /** A 4 spawns when nextInt(10) == 0 (D-034). */
    const val FOUR_ONE_IN = 10
    const val START_TILES = 2

    fun newGame(size: BoardSize, seed: Long): GameState {
        val rng = SplitMix64(seed)
        val cells = ByteArray(size.cellCount)
        repeat(START_TILES) { spawnInto(cells, rng) }
        val board = Board.adopt(size, cells)
        return GameState(
            size = size,
            seed = seed,
            board = board,
            score = 0L,
            rngState = rng.state,
            status = GameStatus.PLAYING,
            moveCount = 0,
            keepGoingAtMove = -1,
            moveLog = MoveLog.EMPTY,
            snapshots = listOf(Snapshot(0, 0L, rng.state, GameStatus.PLAYING, board)),
            undoCount = 0,
        )
    }

    /**
     * Throws IllegalStateException if the move would merge two Board.MAX_EXP tiles, which play
     * never reaches (ARCHITECTURE.md §3.3); :app handles it as a corrupt game (D-214).
     */
    fun move(state: GameState, direction: Direction): MoveOutcome {
        if (state.status == GameStatus.WON || state.status == GameStatus.OVER) {
            return MoveOutcome.Blocked(state.status)
        }
        val r = slide(state.board, direction)
        // A no-op draws nothing and changes nothing (D-035).
        return if (r.moved) accept(state, direction, r) else MoveOutcome.Invalid(direction)
    }

    /** Requires status == WON. Returns KEEP_PLAYING, or OVER when no move is possible (D-036). */
    fun keepGoing(state: GameState): GameState {
        require(state.status == GameStatus.WON) { "Keep going needs WON, was ${state.status}" }
        val kept = state.copy(status = GameStatus.KEEP_PLAYING, keepGoingAtMove = state.moveCount)
        return if (canMove(kept.board)) kept else kept.copy(status = GameStatus.OVER)
    }

    /**
     * null when moveCount == 0 (D-039). Throws IllegalStateException when a logged move does not
     * replay from its snapshot, which only a corrupt history reaches; GameInvariants cannot see
     * that without a replay, so :app handles the exception as a corrupt game (D-214).
     */
    fun undo(state: GameState): GameState? {
        if (state.moveCount == 0) return null
        val t = state.moveCount - 1
        val k = t / SNAPSHOT_INTERVAL
        val snap = state.snapshots[k]
        var x = Core(snap.board, snap.score, snap.rngState, snap.status, snap.moveCount)
        for (i in SNAPSHOT_INTERVAL * k until t) {
            x = reconstructionRule(x, state.keepGoingAtMove)
            val r = slide(x.board, state.moveLog[i])
            check(r.moved) { "Logged move $i (${state.moveLog[i]}) was not accepted" }
            x = step(x, state.size, r).first
        }
        x = reconstructionRule(x, state.keepGoingAtMove)
        return GameState(
            size = state.size,
            seed = state.seed,
            board = x.board,
            score = x.score,
            rngState = x.rngState,
            status = x.status,
            moveCount = t,
            keepGoingAtMove = if (state.keepGoingAtMove > t) -1 else state.keepGoingAtMove,
            moveLog = state.moveLog.truncate(t),
            snapshots = state.snapshots.subList(0, k + 1).toList(),
            undoCount = state.undoCount + 1,
        )
    }

    fun canMove(state: GameState): Boolean = canMove(state.board)

    /** Right and down neighbours only: every orthogonal pair is seen once (D-037). */
    fun canMove(board: Board): Boolean {
        val n = board.size.n
        for (row in 0 until n) {
            for (col in 0 until n) {
                if (hasMoveAt(board, row, col)) return true
            }
        }
        return false
    }

    /** Deterministic replay from a seed; throws IllegalArgumentException on a non-accepted move. */
    fun replay(size: BoardSize, seed: Long, moves: List<Direction>): GameState =
        moves.fold(newGame(size, seed)) { state, direction ->
            val outcome = move(state, direction)
            require(outcome is MoveOutcome.Moved) {
                "Move ${state.moveCount + 1} ($direction) was not accepted: $outcome"
            }
            outcome.state
        }

    private fun accept(s: GameState, direction: Direction, r: SlideResult): MoveOutcome.Moved {
        val (c, spawn) = step(s.core(), s.size, r)
        val snapshots =
            if (c.moveCount % SNAPSHOT_INTERVAL == 0) {
                s.snapshots + Snapshot(c.moveCount, c.score, c.rngState, c.status, c.board)
            } else {
                s.snapshots
            }
        val next =
            GameState(
                size = s.size,
                seed = s.seed,
                board = c.board,
                score = c.score,
                rngState = c.rngState,
                status = c.status,
                moveCount = c.moveCount,
                keepGoingAtMove = s.keepGoingAtMove,
                moveLog = s.moveLog.append(direction),
                snapshots = snapshots,
                undoCount = s.undoCount,
            )
        return MoveOutcome.Moved(
            state = next,
            transitions = r.transitions,
            spawn = spawn,
            scoreDelta = r.scoreDelta,
            mergeCount = r.mergedExps.size,
            maxMergedExp = r.mergedExps.maxOrNull() ?: 0,
            becameWon = s.status == GameStatus.PLAYING && c.status == GameStatus.WON,
            becameOver = c.status == GameStatus.OVER,
        )
    }

    /**
     * D-036: a WON state at or after the Keep going move is KEEP_PLAYING. Applied before each
     * replayed move and after the replay.
     */
    private fun reconstructionRule(x: Core, keepGoingAtMove: Int): Core =
        if (keepGoingAtMove >= 0 && keepGoingAtMove <= x.moveCount && x.status == GameStatus.WON) {
            x.copy(status = GameStatus.KEEP_PLAYING)
        } else {
            x
        }

    private fun hasMoveAt(board: Board, row: Int, col: Int): Boolean {
        val n = board.size.n
        val e = board[row, col]
        return e == 0 ||
            (col + 1 < n && board[row, col + 1] == e) ||
            (row + 1 < n && board[row + 1, col] == e)
    }
}

/** History-free core shared by [Game.move] and undo reconstruction (ARCHITECTURE.md §3.3). */
internal data class Core(
    val board: Board,
    val score: Long,
    val rngState: Long,
    val status: GameStatus,
    val moveCount: Int,
)

/**
 * One accepted move over a [Core] (ARCHITECTURE.md §3.3): spawn from `c.rngState`, then the win
 * check, then the game-over check. The caller guarantees `r.moved`.
 */
internal fun step(c: Core, size: BoardSize, r: SlideResult): Pair<Core, Spawn> {
    val rng = SplitMix64(c.rngState)
    val cells = r.newCells.copyOf()
    val spawn = spawnInto(cells, rng)
    val board = Board.adopt(size, cells)
    var status = c.status
    // Exact match only (D-036); KEEP_PLAYING never re-enters WON.
    if (status == GameStatus.PLAYING && size.targetExp in r.mergedExps) status = GameStatus.WON
    // After the spawn (D-037); a win on a locked board stays WON until Keep going.
    if (status != GameStatus.WON && !Game.canMove(board)) status = GameStatus.OVER
    val next = Core(board, saturatingAdd(c.score, r.scoreDelta), rng.state, status, c.moveCount + 1)
    return next to spawn
}

private fun GameState.core(): Core = Core(board, score, rngState, status, moveCount)
