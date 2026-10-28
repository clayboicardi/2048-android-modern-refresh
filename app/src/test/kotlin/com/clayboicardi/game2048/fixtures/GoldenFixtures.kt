package com.clayboicardi.game2048.fixtures

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameInvariants
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveLog
import com.clayboicardi.game2048.engine.Snapshot

/**
 * The fixtures of DESIGN_SYSTEM.md §10.3. Every game fixture is a `SavedGame` with `gameId = 1` and
 * empty `pendingFinalizations`, and `FIX_RECORDS` has an empty `lastFinalizedGameId` (D-044); a
 * test that needs other identities uses `copy(gameId = …)`. M2 holds the fixtures that the M2 tests
 * name; M3 part 1 adds the rest of the table.
 */
object GoldenFixtures {
    /** 4×4 `PLAYING`, score 10220, best 24680, 412 moves, 2 undos, 9:58. */
    val FIX_MID_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 0, 0, 1, 0),
            score = 10220,
            best = 24680,
            moves = 412,
            undos = 2,
            elapsedMs = 598_000,
            status = GameStatus.PLAYING,
        )

    /** 3×3 `PLAYING`, score 1184, best 2100, 96 moves, no undo, 1:40. LEFT is a no-op. */
    val FIX_MID_3: SavedGame =
        fixtureGame(
            n = 3,
            cells = listOf(1, 2, 3, 4, 5, 0, 6, 7, 1),
            score = 1184,
            best = 2100,
            moves = 96,
            undos = 0,
            elapsedMs = 100_000,
            status = GameStatus.PLAYING,
        )

    /** 4×4 `OVER` (full, no equal neighbour: D-037), score 3012, best 24680, 302 moves, 6:41. */
    val FIX_OVER_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 2, 1, 2, 2, 3, 2, 1, 4, 5, 6, 7, 1, 2, 3, 1),
            score = 3012,
            best = 24680,
            moves = 302,
            undos = 0,
            elapsedMs = 401_000,
            status = GameStatus.OVER,
        )

    /** Sizes "4" and "3" as DESIGN_SYSTEM.md §10.3 lists them; every other size absent. */
    val FIX_RECORDS: Records =
        Records(
            sizes =
                mapOf(
                    "4" to
                        SizeRecord(
                            bestScore = 24680,
                            bestScoreNoUndo = 18212,
                            bestTileExp = 11,
                            gamesPlayed = 37,
                            gamesWon = 9,
                            gamesAbandoned = 6,
                            totalScore = 412560,
                            totalMoves = 23804,
                            totalTimeMs = 41_234_000,
                            totalUndos = 58,
                            currentWinStreak = 1,
                            longestWinStreak = 3,
                        ),
                    "3" to
                        SizeRecord(
                            bestScore = 2100,
                            bestTileExp = 8,
                            gamesPlayed = 5,
                            gamesWon = 2,
                        ),
                ),
        )
}

/**
 * A `SavedGame` on [cells] (row-major exponents) whose engine state passes `GameInvariants`, with a
 * synthetic history: `snapshots[0]` is the new game of [seed] (I7), the move log is [moves] UPs,
 * and every later snapshot holds [cells] with a score rising to [score] (I10–I12). `bestAtStart` is
 * [best]. The history does not replay: an undo that reaches back before the fixture throws
 * `IllegalStateException` (D-214). When [moves] is a multiple of 32, the last snapshot is the state
 * itself, so moves played from the fixture undo exactly back to it.
 */
@Suppress("LongParameterList") // one parameter per DESIGN_SYSTEM.md §10.3 column
fun fixtureGame(
    n: Int,
    cells: List<Int>,
    score: Long,
    best: Long,
    moves: Int,
    undos: Int,
    elapsedMs: Long,
    status: GameStatus,
    keepGoingAtMove: Int = -1,
    seed: Long = 0x2048,
): SavedGame {
    val size = BoardSize.of(n)
    val start = Game.newGame(size, seed)
    val board = Board.of(size, ByteArray(cells.size) { cells[it].toByte() })
    val last = moves / Game.SNAPSHOT_INTERVAL
    // A snapshot keeps the status its move stored: Keep going at that very move leaves it WON.
    val lastStatus =
        if (status == GameStatus.KEEP_PLAYING && keepGoingAtMove >= moves) GameStatus.WON
        else status
    val snapshots =
        List(last + 1) { k ->
            val at = k * Game.SNAPSHOT_INTERVAL
            when {
                k == 0 -> start.snapshots[0]
                at == moves -> Snapshot(at, score, start.rngState, lastStatus, board)
                else -> Snapshot(at, score * at / moves, start.rngState, GameStatus.PLAYING, board)
            }
        }
    val state =
        GameState(
            size = size,
            seed = seed,
            board = board,
            score = score,
            rngState = start.rngState,
            status = status,
            moveCount = moves,
            keepGoingAtMove = keepGoingAtMove,
            moveLog = MoveLog.fromPackedBytes(ByteArray(MoveLog.packedSize(moves)), moves),
            snapshots = snapshots,
            undoCount = undos,
        )
    val violations = GameInvariants.violations(state)
    check(violations.isEmpty()) { "Fixture breaks the engine invariants: $violations" }
    return SavedGame(
        state = state,
        elapsedMs = elapsedMs,
        bestAtStart = best,
        newBestAnnounced = false,
        gameId = 1,
        pendingFinalizations = emptyList(),
    )
}
