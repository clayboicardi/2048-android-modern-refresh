package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** ARCHITECTURE.md §3.6: new-game and replay golden vectors from seed 0x2048. */
class EngineReplayGoldenTest {
    /** F01-AC13 */
    @Test
    fun newGameVectors() {
        checkNewGame(
            BoardSize.of(3),
            cells("0 0 1 / 2 0 0 / 0 0 0"),
            listOf(Spawn(2, 1), Spawn(3, 2)),
        )
        checkNewGame(
            BoardSize.of(4),
            cells("0 0 0 0 / 0 0 0 0 / 0 0 1 2 / 0 0 0 0"),
            listOf(Spawn(10, 1), Spawn(11, 2)),
        )
        val eight =
            ByteArray(BoardSize.of(8).cellCount).also {
                it[10] = 1
                it[62] = 2
            }
        checkNewGame(BoardSize.of(8), eight, listOf(Spawn(10, 1), Spawn(62, 2)))
    }

    /** F01-AC14 */
    @Test
    fun replayVectors() {
        var state = Game.newGame(FOUR, SEED)
        for ((i, row) in REPLAY.withIndex()) {
            val moved = accepted(Game.move(state, row.direction))
            checkRow(i + 1, row, state, moved)
            if (i + 1 == MOVE_WITH_MERGE_SOURCES) checkMoveThree(moved)
            state = moved.state
        }
        assertEquals(1, state.snapshots.size)
        val replayed = Game.replay(FOUR, SEED, REPLAY.map { it.direction })
        assertNoViolations(replayed)
        assertEquals(state, replayed)
    }

    private fun checkNewGame(size: BoardSize, expected: ByteArray, spawns: List<Spawn>) {
        val state = Game.newGame(size, SEED)
        assertNoViolations(state)
        assertEquals(expected.toList(), state.board.toByteArray().toList(), "$size cells")
        assertEquals(size, state.size)
        assertEquals(SEED, state.seed)
        assertEquals(NEW_GAME_RNG, state.rngState)
        assertEquals(0L, state.score)
        assertEquals(GameStatus.PLAYING, state.status)
        assertEquals(0, state.moveCount)
        assertEquals(-1, state.keepGoingAtMove)
        assertEquals(MoveLog.EMPTY, state.moveLog)
        assertEquals(0, state.undoCount)
        val start = Snapshot(0, 0L, NEW_GAME_RNG, GameStatus.PLAYING, Board.of(size, expected))
        assertEquals(listOf(start), state.snapshots)
        // newGame returns no spawns: replay its two draws (value, then cell) on an empty board.
        val raw = ByteArray(size.cellCount)
        val rng = SplitMix64(SEED)
        assertEquals(spawns, List(Game.START_TILES) { spawnInto(raw, rng) }, "$size spawns")
        assertEquals(expected.toList(), raw.toList(), "$size spawned cells")
        assertEquals(NEW_GAME_RNG, rng.state)
    }

    private fun checkRow(move: Int, row: ReplayRow, before: GameState, moved: MoveOutcome.Moved) {
        val label = "move $move (${row.direction})"
        val after = moved.state
        assertNoViolations(after)
        assertEquals(cells(row.cells).toList(), after.board.toByteArray().toList(), label)
        assertEquals(row.score, after.score, label)
        assertEquals(row.delta, moved.scoreDelta, label)
        assertEquals(row.spawn, moved.spawn, label)
        assertEquals(row.rngState, after.rngState, label)
        assertEquals(move, after.moveCount, label)
        assertEquals(REPLAY.take(move).map { it.direction }, after.moveLog.toList(), label)
        assertEquals(GameStatus.PLAYING, after.status, label)
        assertFalse(moved.becameWon, label)
        assertFalse(moved.becameOver, label)
        // One transition per occupied cell of the pre-move board (ARCHITECTURE.md §3.2).
        assertEquals(before.board.tileCount(), moved.transitions.size, label)
    }

    private fun checkMoveThree(moved: MoveOutcome.Moved) {
        val expected =
            listOf(
                Transition(2, 3, 3),
                Transition(1, 3, 3),
                Transition(0, 2, null),
                Transition(5, 7, null),
            )
        assertEquals(expected, moved.transitions)
        assertEquals(1, moved.mergeCount)
        assertEquals(2, moved.maxMergedExp)
    }

    /** One row of the ARCHITECTURE.md §3.6 replay table; cells are exponents. */
    private data class ReplayRow(
        val direction: Direction,
        val cells: String,
        val score: Long,
        val delta: Long,
        val spawn: Spawn,
        val rngState: Long,
    )

    companion object {
        private const val SEED = 0x2048L
        private const val NEW_GAME_RNG = 0x78DDE6E5FD2A109CL
        private const val MOVE_WITH_MERGE_SOURCES = 3
        private val FOUR = BoardSize.of(4)

        private val REPLAY =
            listOf(
                ReplayRow(
                    Direction.LEFT,
                    "0 0 0 0 / 0 1 0 0 / 1 2 0 0 / 0 0 0 0",
                    0L,
                    0L,
                    Spawn(5, 1),
                    0xB54CDA58FBBF08C6uL.toLong(),
                ),
                ReplayRow(
                    Direction.UP,
                    "1 1 1 0 / 0 2 0 0 / 0 0 0 0 / 0 0 0 0",
                    0L,
                    0L,
                    Spawn(2, 1),
                    0xF1BBCDCBFA5400F0uL.toLong(),
                ),
                ReplayRow(
                    Direction.RIGHT,
                    "0 0 1 2 / 0 1 0 2 / 0 0 0 0 / 0 0 0 0",
                    4L,
                    4L,
                    Spawn(5, 1),
                    0x2E2AC13EF8E8F91AuL.toLong(),
                ),
                ReplayRow(
                    Direction.DOWN,
                    "0 0 1 0 / 0 0 0 0 / 0 0 0 0 / 0 1 1 3",
                    12L,
                    8L,
                    Spawn(2, 1),
                    0x6A99B4B1F77DF144uL.toLong(),
                ),
                ReplayRow(
                    Direction.LEFT,
                    "1 0 0 0 / 0 0 0 0 / 0 0 0 0 / 2 3 0 1",
                    16L,
                    4L,
                    Spawn(15, 1),
                    0xA708A824F612E96EuL.toLong(),
                ),
                ReplayRow(
                    Direction.LEFT,
                    "1 0 0 1 / 0 0 0 0 / 0 0 0 0 / 2 3 1 0",
                    16L,
                    0L,
                    Spawn(3, 1),
                    0xE3779B97F4A7E198uL.toLong(),
                ),
                ReplayRow(
                    Direction.UP,
                    "1 3 1 1 / 2 0 0 0 / 0 1 0 0 / 0 0 0 0",
                    16L,
                    0L,
                    Spawn(9, 1),
                    0x1FE68F0AF33CD9C2uL.toLong(),
                ),
                ReplayRow(
                    Direction.UP,
                    "1 3 1 1 / 2 1 1 0 / 0 0 0 0 / 0 0 0 0",
                    16L,
                    0L,
                    Spawn(6, 1),
                    0x5C55827DF1D1D1ECuL.toLong(),
                ),
            )

        /** Row-major exponents from "a b c / d e f / …". */
        private fun cells(text: String): ByteArray =
            text.split(' ').filter { it != "/" }.map(String::toByte).toByteArray()

        private fun accepted(outcome: MoveOutcome): MoveOutcome.Moved =
            assertInstanceOf(MoveOutcome.Moved::class.java, outcome)

        private fun assertNoViolations(state: GameState) {
            assertEquals(emptyList<String>(), GameInvariants.violations(state))
        }
    }
}
