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
 * test that needs other identities uses `copy(gameId = …)`. M2 added the fixtures that the M2 tests
 * name and M3 part 1 the rest of the table. Values the table leaves open are chosen here and named
 * in each KDoc.
 */
object GoldenFixtures {
    /**
     * 4×4 `PLAYING`, a genuine new game: seed `0x4A0` spawns exactly index 1 = 1 and index 11 = 2,
     * so `snapshots[0]` is the state itself; score, best, moves, undos and time 0.
     */
    val FIX_FRESH_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = cells(16, 1 to 1, 11 to 2),
            score = 0,
            best = 0,
            moves = 0,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            seed = 0x4A0,
        )

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

    /**
     * 8×8 `KEEP_PLAYING` (`keepGoingAtMove` 9000) with compact labels, score and best 1182372,
     * 23456 moves (a multiple of 32), no undo, 3:12:05.
     */
    val FIX_DENSE_8: SavedGame =
        fixtureGame(
            n = 8,
            cells =
                listOf(
                    1,
                    2,
                    3,
                    4,
                    5,
                    6,
                    7,
                    8,
                    16,
                    15,
                    14,
                    13,
                    12,
                    11,
                    10,
                    9,
                    1,
                    1,
                    2,
                    2,
                    3,
                    3,
                    0,
                    0,
                    4,
                    5,
                    6,
                    7,
                    8,
                    9,
                    10,
                    11,
                    2,
                    0,
                    1,
                    0,
                    2,
                    0,
                    1,
                    0,
                    12,
                    11,
                    10,
                    9,
                    8,
                    7,
                    6,
                    5,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    3,
                    2,
                    1,
                    0,
                    0,
                    1,
                    2,
                    3,
                ),
            score = 1_182_372,
            best = 1_182_372,
            moves = 23_456,
            undos = 0,
            elapsedMs = 11_525_000,
            status = GameStatus.KEEP_PLAYING,
            keepGoingAtMove = 9000,
        )

    /**
     * 5×5 `KEEP_PLAYING` with the full ramp: exponents 1..17 in reading order, then 8 empty cells;
     * score and best 2456788, 41000 moves, no undo, 6:02:00. `keepGoingAtMove` 9000 (the table
     * gives none).
     */
    val FIX_RAMP_5: SavedGame =
        fixtureGame(
            n = 5,
            cells = (1..17).toList() + List(8) { 0 },
            score = 2_456_788,
            best = 2_456_788,
            moves = 41_000,
            undos = 0,
            elapsedMs = 21_720_000,
            status = GameStatus.KEEP_PLAYING,
            keepGoingAtMove = 9000,
        )

    /** 4×4 `WON` (2048 at index 0), score 20124, best 24680, 980 moves, no undo, 21:15. */
    val FIX_WON_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(11, 1, 2, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 2),
            score = 20_124,
            best = 24_680,
            moves = 980,
            undos = 0,
            elapsedMs = 1_275_000,
            status = GameStatus.WON,
        )

    /**
     * The filmstrip board (MOTION_SPEC.md §8.3): 4×4 `PLAYING`, row 0 = `1,1,0,2`, `rngState`
     * 0x2048, score and best 0. LEFT merges the two 2s (+4) and slides the 4, and the spawn is
     * exponent 1 at index 4 (row 1, column 0). 32 moves (the table gives none), so the move's
     * snapshot is the fixture itself and an Undo after LEFT restores exactly this board
     * (MOTION_SPEC.md §3.9, AC-UNDO-1; D-216).
     */
    val FIX_FILM: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 1, 0, 2) + List(12) { 0 },
            score = 0,
            best = 0,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /**
     * The feature-graphic board (DESIGN_SYSTEM.md §8.5): 4×4 `KEEP_PLAYING`, score 20480, best
     * 24680. 1024 moves, `keepGoingAtMove` 1000, no undo, 0 ms (the table gives none).
     */
    val FIX_FEATURE: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0, 0, 1, 0, 2),
            score = 20_480,
            best = 24_680,
            moves = 1024,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.KEEP_PLAYING,
            keepGoingAtMove = 1000,
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
 * itself, so moves played from the fixture undo exactly back to it (at 0 moves, `snapshots[0]` is
 * the seed's new game). [rngState], when given, replaces the state's RNG state (and that of the
 * snapshot at the fixture's own move), so the next spawn is the one a test expects.
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
    rngState: Long? = null,
): SavedGame {
    val size = BoardSize.of(n)
    val start = Game.newGame(size, seed)
    val rng = rngState ?: start.rngState
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
                at == moves -> Snapshot(at, score, rng, lastStatus, board)
                else -> Snapshot(at, score * at / moves, start.rngState, GameStatus.PLAYING, board)
            }
        }
    val state =
        GameState(
            size = size,
            seed = seed,
            board = board,
            score = score,
            rngState = rng,
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

/** [n] row-major exponents, 0 except the given `index to exponent` pairs. */
private fun cells(n: Int, vararg set: Pair<Int, Int>): List<Int> =
    MutableList(n) { 0 }.apply { for ((i, e) in set) this[i] = e }
