package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D-036, D-037: win, Keep going, game over and Blocked (QUALITY.md §1.4 other suites). */
class StatusMachineTest {
    /** F03-AC1 */
    @Test
    fun winOnExactTarget() {
        val start = state(board(WIN_ROW))
        assertNoViolations(start)
        val moved = accepted(Game.move(start, Direction.LEFT))
        assertNoViolations(moved.state)
        assertTrue(moved.becameWon)
        assertFalse(moved.becameOver)
        assertEquals(GameStatus.WON, moved.state.status)
        assertEquals(TARGET_4X4, moved.state.board[0, 0])
        assertEquals(TARGET_4X4, moved.maxMergedExp)
        assertEquals(2048L, moved.scoreDelta)
        assertEquals(-1, moved.state.keepGoingAtMove)
    }

    /** F03-AC2 */
    @Test
    fun twoTargetTilesInOneMoveWinOnce() {
        val start = state(board("10 10 0 0 / 0 0 0 0 / 10 10 0 0 / 0 0 0 0"))
        assertNoViolations(start)
        val moved = accepted(Game.move(start, Direction.LEFT))
        assertNoViolations(moved.state)
        // One Boolean for the move, however many target tiles it makes.
        assertTrue(moved.becameWon)
        assertEquals(GameStatus.WON, moved.state.status)
        assertEquals(2, moved.mergeCount)
        assertEquals(TARGET_4X4, moved.maxMergedExp)
        assertEquals(4096L, moved.scoreDelta)
        assertEquals(TARGET_4X4, moved.state.board[0, 0])
        assertEquals(TARGET_4X4, moved.state.board[2, 0])
        // Nothing can report the win again: WON blocks every move until Keep going.
        for (direction in Direction.entries) {
            assertEquals(MoveOutcome.Blocked(GameStatus.WON), Game.move(moved.state, direction))
        }
    }

    /** F03-AC3 */
    @Test
    fun winWithNoMovesStaysWonUntilKeepGoing() {
        val start = state(board("10 10 3 2 / 4 5 4 5 / 5 4 5 4 / 4 5 4 5"))
        assertNoViolations(start)
        val moved = accepted(Game.move(start, Direction.LEFT))
        assertNoViolations(moved.state)
        // Spawn control: from 0x2048 the value draw is a 2, and index 3 is the only empty cell.
        assertEquals(Spawn(3, 1), moved.spawn)
        assertEquals(board("11 3 2 1 / 4 5 4 5 / 5 4 5 4 / 4 5 4 5"), moved.state.board)
        assertFalse(Game.canMove(moved.state))
        assertTrue(moved.becameWon)
        assertFalse(moved.becameOver)
        assertEquals(GameStatus.WON, moved.state.status)

        val kept = Game.keepGoing(moved.state)
        assertNoViolations(kept)
        assertEquals(GameStatus.OVER, kept.status)
        assertEquals(moved.state.moveCount, kept.keepGoingAtMove)
        assertEquals(moved.state.board, kept.board)
    }

    /** F03-AC4 */
    @Test
    fun keepGoingRecordsMove() {
        val won = accepted(Game.move(state(board(WIN_ROW)), Direction.LEFT)).state
        assertNoViolations(won)
        assertTrue(Game.canMove(won))
        val kept = Game.keepGoing(won)
        assertNoViolations(kept)
        assertEquals(GameStatus.KEEP_PLAYING, kept.status)
        assertEquals(won.moveCount, kept.keepGoingAtMove)
        assertEquals(1, kept.keepGoingAtMove)
        assertEquals(won.copy(status = GameStatus.KEEP_PLAYING, keepGoingAtMove = 1), kept)

        val wonAtZero = state(board(WIN_TILE), GameStatus.WON)
        assertNoViolations(wonAtZero)
        val keptAtZero = Game.keepGoing(wonAtZero)
        assertNoViolations(keptAtZero)
        assertEquals(GameStatus.KEEP_PLAYING, keptAtZero.status)
        assertEquals(0, keptAtZero.keepGoingAtMove)

        val outsideWon =
            listOf(
                state(board(WIN_ROW)),
                kept,
                state(board(LOCKED), GameStatus.OVER),
                Game.newGame(FOUR, SEED),
            )
        for (s in outsideWon) {
            assertNoViolations(s)
            assertThrows<IllegalArgumentException>("${s.status}") { Game.keepGoing(s) }
        }
    }

    /** F03-AC5 */
    @Test
    fun keepPlayingIsUnbounded() {
        val start =
            state(board("11 10 10 0 / 0 0 0 0 / 0 0 0 0 / 0 0 0 0"), GameStatus.KEEP_PLAYING, 0)
        assertNoViolations(start)
        // 10 + 10 makes a second 2048 next to the first.
        val second2048 = accepted(Game.move(start, Direction.LEFT))
        assertNoViolations(second2048.state)
        assertFalse(second2048.becameWon)
        assertEquals(GameStatus.KEEP_PLAYING, second2048.state.status)
        assertEquals(TARGET_4X4, second2048.maxMergedExp)
        assertEquals(TARGET_4X4, second2048.state.board[0, 0])
        assertEquals(TARGET_4X4, second2048.state.board[0, 1])
        // The two 2048s are leftmost in row 0, so LEFT merges them whatever the spawn was.
        val tile4096 = accepted(Game.move(second2048.state, Direction.LEFT))
        assertNoViolations(tile4096.state)
        assertFalse(tile4096.becameWon)
        assertEquals(GameStatus.KEEP_PLAYING, tile4096.state.status)
        assertEquals(TARGET_4X4 + 1, tile4096.maxMergedExp)
        assertEquals(TARGET_4X4 + 1, tile4096.state.board[0, 0])
        assertEquals(TARGET_4X4 + 1, tile4096.state.board.maxExp())
        assertEquals(0, tile4096.state.keepGoingAtMove)
    }

    /** F01-AC17 */
    @Test
    fun overEvaluatedAfterSpawn() {
        val start = state(board(LAST_CELL))
        assertNoViolations(start)
        // Before the spawn the slid board still has an empty cell, so it is not over.
        val preSpawn = Board.of(FOUR, slide(start.board, Direction.LEFT).newCells)
        assertEquals(board("4 5 2 0 / 6 7 6 7 / 7 6 7 6 / 6 7 6 7"), preSpawn)
        assertTrue(Game.canMove(preSpawn))

        val moved = accepted(Game.move(start, Direction.LEFT))
        assertNoViolations(moved.state)
        assertEquals(Spawn(3, 1), moved.spawn)
        assertEquals(board("4 5 2 1 / 6 7 6 7 / 7 6 7 6 / 6 7 6 7"), moved.state.board)
        assertFalse(Game.canMove(moved.state))
        assertTrue(moved.becameOver)
        assertFalse(moved.becameWon)
        assertEquals(GameStatus.OVER, moved.state.status)
    }

    /** F01-AC18 */
    @Test
    fun blockedInWonAndOver() {
        val won = accepted(Game.move(state(board(WIN_ROW)), Direction.LEFT)).state
        val over = accepted(Game.move(state(board(LAST_CELL)), Direction.LEFT)).state
        assertEquals(GameStatus.WON, won.status)
        assertEquals(GameStatus.OVER, over.status)
        val blocked =
            listOf(
                won,
                over,
                state(board(WIN_TILE), GameStatus.WON),
                state(board(LOCKED), GameStatus.OVER),
            )
        for (s in blocked) {
            assertNoViolations(s)
            for (direction in Direction.entries) {
                assertEquals(MoveOutcome.Blocked(s.status), Game.move(s, direction), "$direction")
            }
        }
    }

    /** F02-AC1 */
    @Test
    fun targetsPerSize() {
        for (n in BoardSize.MIN_N..BoardSize.MAX_N) {
            val size = BoardSize.of(n)
            assertEquals(n, size.n)
            assertEquals(n * n, size.cellCount)
            assertEquals(if (n == 3) 8 else 11, size.targetExp, "$size")
        }
        for (n in listOf(2, 9, 0, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertThrows<IllegalArgumentException>("n = $n") { BoardSize.of(n) }
        }
    }

    /** F02-AC2 */
    @Test
    fun threeByThreeWinsAt256() {
        val start = state(board("7 7 0 / 0 0 0 / 0 0 0"))
        assertNoViolations(start)
        val moved = accepted(Game.move(start, Direction.LEFT))
        assertNoViolations(moved.state)
        assertTrue(moved.becameWon)
        assertEquals(GameStatus.WON, moved.state.status)
        assertEquals(BoardSize.TARGET_EXP_3X3, moved.state.board[0, 0])
        assertEquals(256L, moved.scoreDelta)
        // A 128 is not the 3×3 target.
        val notYet = accepted(Game.move(state(board("6 6 0 / 0 0 0 / 0 0 0")), Direction.LEFT))
        assertNoViolations(notYet.state)
        assertFalse(notYet.becameWon)
        assertEquals(GameStatus.PLAYING, notYet.state.status)
    }

    /** D-036: status queries and BoardSize constants */
    @Test
    fun statusQueries() {
        val fresh = Game.newGame(FOUR, SEED)
        val playing = accepted(Game.move(fresh, Direction.LEFT)).state
        val won = accepted(Game.move(state(board(WIN_ROW)), Direction.LEFT)).state
        val kept = Game.keepGoing(won)
        val over = accepted(Game.move(state(board(LAST_CELL)), Direction.LEFT)).state
        // isWon, hasWon, isKeepPlaying, isOver, isInProgress, canUndo
        val expected =
            listOf(
                fresh to "000000",
                playing to "000011",
                won to "110001",
                kept to "011011",
                over to "000101",
                state(board(WIN_TILE), GameStatus.KEEP_PLAYING, 0) to "011000",
                state(board(LOCKED), GameStatus.OVER) to "000100",
                state(board("8 0 0 / 0 0 0 / 0 0 0")) to "010000",
                state(board("8 0 0 0 / 0 0 0 0 / 0 0 0 0 / 0 0 0 0")) to "000000",
            )
        for ((s, flags) in expected) {
            assertNoViolations(s)
            assertEquals(flags, flagsOf(s), "${s.status} at move ${s.moveCount}: ${s.board}")
        }
        assertEquals((3..8).map { BoardSize.of(it) }, BoardSize.ALL)
        assertEquals(BoardSize.of(BoardSize.DEFAULT_N), BoardSize.DEFAULT)
        assertEquals(4, BoardSize.DEFAULT.n)
        assertEquals("4x4", BoardSize.DEFAULT.toString())
        assertEquals("3x3", BoardSize.of(3).toString())
        assertEquals("8x8", BoardSize.of(8).toString())
    }

    /** D-036, D-037: GameInvariants reports status violations (I4, I5, I8) */
    @Test
    fun invariantsReportStatusViolations() {
        val valid =
            listOf(
                state(board(WIN_TILE), GameStatus.WON),
                state(board(WIN_TILE), GameStatus.KEEP_PLAYING, 0),
                state(board(LOCKED), GameStatus.OVER),
                state(board(WIN_TILE)),
            )
        for (s in valid) {
            assertNoViolations(s)
        }
        val broken =
            listOf(
                // I4: KEEP_PLAYING needs keepGoingAtMove >= 0.
                state(board(WIN_TILE), GameStatus.KEEP_PLAYING, -1),
                // I5: WON needs keepGoingAtMove == -1 and the target on the board.
                state(board(WIN_TILE), GameStatus.WON, 0),
                state(board("10 0 0 0 / 0 0 0 0 / 0 0 0 0 / 0 0 0 0"), GameStatus.WON),
                state(board("7 0 0 / 0 0 0 / 0 0 0"), GameStatus.WON),
                // I8: OVER needs a board without a move (an empty cell, then an equal pair).
                state(board(WIN_TILE), GameStatus.OVER),
                state(board("1 1 1 2 / 2 1 2 1 / 1 2 1 2 / 2 1 2 1"), GameStatus.OVER),
                state(board("1 2 1 2 / 2 1 2 1 / 1 2 1 2 / 2 1 2 2"), GameStatus.OVER),
            )
        for ((i, s) in broken.withIndex()) {
            assertTrue(GameInvariants.violations(s).isNotEmpty(), "case $i: ${s.status} ${s.board}")
        }
    }

    companion object {
        private const val SEED = 0x2048L
        private const val TARGET_4X4 = BoardSize.TARGET_EXP_DEFAULT
        private const val WIN_ROW = "10 10 0 0 / 0 0 0 0 / 0 0 0 0 / 0 0 0 0"
        private const val WIN_TILE = "11 0 0 0 / 0 0 0 0 / 0 0 0 0 / 0 0 0 0"
        private const val LAST_CELL = "3 3 5 2 / 6 7 6 7 / 7 6 7 6 / 6 7 6 7"
        private const val LOCKED = "1 2 1 2 / 2 1 2 1 / 1 2 1 2 / 2 1 2 1"
        private val FOUR = BoardSize.of(4)

        /** Row-major exponents from "a b c / d e f / …"; the size follows from the cell count. */
        private fun board(text: String): Board {
            val cells = text.split(' ').filter { it != "/" }.map(String::toByte).toByteArray()
            val n = (BoardSize.MIN_N..BoardSize.MAX_N).first { it * it == cells.size }
            return Board.of(BoardSize.of(n), cells)
        }

        /**
         * Hand-built state at move 0 whose only snapshot is newGame's, so I7 holds; rngState 0x2048
         * makes the first spawn a 2 (D-038: the first nextInt(10) is 8).
         */
        private fun state(
            board: Board,
            status: GameStatus = GameStatus.PLAYING,
            keepGoingAtMove: Int = -1,
        ): GameState =
            GameState(
                size = board.size,
                seed = SEED,
                board = board,
                score = 0L,
                rngState = SEED,
                status = status,
                moveCount = 0,
                keepGoingAtMove = keepGoingAtMove,
                moveLog = MoveLog.EMPTY,
                snapshots = Game.newGame(board.size, SEED).snapshots,
                undoCount = 0,
            )

        private fun flagsOf(s: GameState): String =
            listOf(s.isWon, s.hasWon, s.isKeepPlaying, s.isOver, s.isInProgress, s.canUndo)
                .joinToString("") { if (it) "1" else "0" }

        private fun accepted(outcome: MoveOutcome): MoveOutcome.Moved =
            assertInstanceOf(MoveOutcome.Moved::class.java, outcome)

        private fun assertNoViolations(state: GameState) {
            assertEquals(emptyList<String>(), GameInvariants.violations(state))
        }
    }
}
