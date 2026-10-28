package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D-036, D-039: undo from snapshots plus the move log (QUALITY.md §1.4 other suites). */
class UndoTest {
    /** F05-AC1 */
    @Test
    fun undoAtMoveZeroReturnsNull() {
        for (size in BoardSize.ALL) {
            val fresh = Game.newGame(size, SEED)
            assertNoViolations(fresh)
            assertNull(Game.undo(fresh), "$size")
        }
    }

    /** F05-AC3 */
    @Test
    fun undoAcrossSnapshotBoundaries() {
        // moves to (snapshots before, snapshots after the undo); prefixes of the §9.4 long game.
        val cases = listOf(31 to (1 to 1), 32 to (2 to 1), 33 to (2 to 2), 64 to (3 to 2))
        for ((moves, counts) in cases) {
            val state = playDlru(EIGHT, moves)
            assertNoViolations(state)
            assertEquals(moves, state.moveCount)
            assertEquals(GameStatus.PLAYING, state.status)
            assertEquals(counts.first, state.snapshots.size, "$moves moves")
            val undone = undo(state)
            val expected =
                Game.replay(EIGHT, SEED, state.moveLog.toList().dropLast(1))
                    .copy(undoCount = state.undoCount + 1)
            assertEquals(expected, undone, "$moves moves")
            assertEquals(counts.second, undone.snapshots.size, "$moves moves")
            assertEquals(moves - 1, undone.moveCount)
        }
    }

    /** F05-AC4 */
    @Test
    fun sameMoveAfterUndoSpawnsSameTile() {
        for (moves in listOf(0, 1, 31, 32, 33)) {
            val state = playDlru(EIGHT, moves)
            assertNoViolations(state)
            for (direction in Direction.entries) {
                val first = Game.move(state, direction) as? MoveOutcome.Moved ?: continue
                val undone = undo(first.state)
                assertEquals(state.copy(undoCount = state.undoCount + 1), undone)
                val again = accepted(Game.move(undone, direction))
                assertNoViolations(again.state)
                assertEquals(first.spawn, again.spawn, "$direction after $moves moves")
                assertEquals(first.state, again.state.copy(undoCount = first.state.undoCount))
                assertEquals(first.transitions, again.transitions)
            }
        }
    }

    /** F05-AC5 */
    @Test
    fun undoFromPlayingKeepPlayingAndWon() {
        val playing = playDlru(EIGHT, WIN_MOVE - 1)
        assertEquals(GameStatus.PLAYING, playing.status)
        val win = dlruStep(playing)
        assertTrue(win.becameWon)
        assertEquals(WIN_MOVE, win.state.moveCount)
        assertEquals(GameStatus.WON, win.state.status)
        val fromWon = undo(win.state)
        assertEquals(GameStatus.PLAYING, fromWon.status)
        assertEquals(playing.copy(undoCount = 1), fromWon)

        val beforePlaying = playDlru(EIGHT, WIN_MOVE - 2)
        val fromPlaying = undo(dlruStep(beforePlaying).state)
        assertEquals(beforePlaying.copy(undoCount = 1), fromPlaying)
        assertEquals(GameStatus.PLAYING, fromPlaying.status)

        val kept = playDlruFrom(Game.keepGoing(win.state), WIN_MOVE + KEPT_MOVES)
        assertEquals(GameStatus.KEEP_PLAYING, kept.status)
        assertEquals(WIN_MOVE, kept.keepGoingAtMove)
        val fromKept = undo(dlruStep(kept).state)
        assertEquals(kept.copy(undoCount = 1), fromKept)
        assertEquals(GameStatus.KEEP_PLAYING, fromKept.status)
        assertEquals(WIN_MOVE, fromKept.keepGoingAtMove)
    }

    /** F05-AC6 */
    @Test
    fun undoAllTheWayToMoveZero() {
        var state = playDlru(EIGHT, LONG_GAME_MOVES)
        assertLongGame(state)
        for (undos in 1..LONG_GAME_MOVES) {
            val undone = undo(state)
            // undoCount grows by exactly one per undo and never decreases.
            assertEquals(undos, undone.undoCount)
            assertEquals(state.moveCount - 1, undone.moveCount)
            // D-036: at or above the Keep going move the game is KEEP_PLAYING, below it PLAYING.
            val kept = undone.moveCount >= WIN_MOVE
            assertEquals(if (kept) GameStatus.KEEP_PLAYING else GameStatus.PLAYING, undone.status)
            assertEquals(if (kept) WIN_MOVE else -1, undone.keepGoingAtMove)
            state = undone
        }
        val fresh = Game.newGame(EIGHT, SEED)
        assertEquals(fresh.board, state.board)
        assertEquals(fresh.score, state.score)
        assertEquals(fresh.rngState, state.rngState)
        assertEquals(LONG_GAME_MOVES, state.undoCount)
        assertEquals(1, state.snapshots.size)
        assertEquals(fresh.copy(undoCount = LONG_GAME_MOVES), state)
        assertNull(Game.undo(state))
    }

    /** F05-AC7 */
    @Test
    fun undoAfterUndoThenMoveReturnsToPriorState() {
        // S0 and m1–m3 are the ARCHITECTURE.md §3.6 replay; m4 = DOWN moves its move-2 board.
        val s0 = Game.newGame(FOUR, SEED)
        assertNoViolations(s0)
        val s1 = accepted(Game.move(s0, Direction.LEFT)).state
        val s2 = accepted(Game.move(s1, Direction.UP)).state
        val s3 = accepted(Game.move(s2, Direction.RIGHT)).state
        val afterUndo = undo(s3)
        assertEquals(s2.copy(undoCount = 1), afterUndo)
        val t = accepted(Game.move(afterUndo, Direction.DOWN)).state
        assertNoViolations(t)
        val back = undo(t)
        assertEquals(2, back.undoCount)
        assertEquals(s2, back.copy(undoCount = 0))
        assertNotEquals(s1, back.copy(undoCount = 0))
    }

    /** F03-AC7 */
    @Test
    fun undoBelowKeepGoingResetsWin() {
        // Anchored at move 96: RIGHT, LEFT, RIGHT are accepted whatever spawns (rows 0–1 are full
        // and cannot move sideways), then UP merges the 1024s of column 0 at move 100.
        val anchor = anchored(board(WIN_AT_100), ANCHOR_96, GameStatus.PLAYING, -1)
        assertNoViolations(anchor)
        val s99 =
            listOf(Direction.RIGHT, Direction.LEFT, Direction.RIGHT).fold(anchor) { s, d ->
                val moved = accepted(Game.move(s, d))
                assertFalse(moved.becameWon)
                moved.state
            }
        val win = accepted(Game.move(s99, Direction.UP))
        assertTrue(win.becameWon)
        assertEquals(WIN_AT, win.state.moveCount)
        val kept = Game.keepGoing(win.state)
        assertNoViolations(kept)
        assertEquals(GameStatus.KEEP_PLAYING, kept.status)
        assertEquals(WIN_AT, kept.keepGoingAtMove)
        val s101 = dlruStep(kept).state
        val s102 = dlruStep(s101).state
        assertNoViolations(s102)

        val undo1 = undo(s102)
        assertEquals(s101.copy(undoCount = 1), undo1)
        val undo2 = undo(undo1)
        assertEquals(kept.copy(undoCount = 2), undo2)
        assertEquals(GameStatus.KEEP_PLAYING, undo2.status)
        val undo3 = undo(undo2)
        assertEquals(-1, undo3.keepGoingAtMove)
        assertEquals(GameStatus.PLAYING, undo3.status)
        assertEquals(s99.copy(undoCount = 3), undo3)

        val again = accepted(Game.move(undo3, Direction.UP))
        assertNoViolations(again.state)
        assertTrue(again.becameWon)
        assertEquals(win.spawn, again.spawn)
        assertEquals(win.state.copy(undoCount = 3), again.state)
    }

    /** F04-AC8 */
    @Test
    fun undoFromOverIsPlayable() {
        // From PLAYING: the 3×3 DLRU game from 0x2048 is over at move 43 without a win.
        val over3 = playDlruUntilStuck(BoardSize.of(3))
        assertNoViolations(over3)
        assertEquals(GameStatus.OVER, over3.status)
        assertEquals(THREE_BY_THREE_OVER_AT, over3.moveCount)
        assertEquals(-1, over3.keepGoingAtMove)
        val fromOver3 = undo(over3)
        assertEquals(GameStatus.PLAYING, fromOver3.status)
        assertTrue(Game.canMove(fromOver3))
        assertEquals(over3.moveCount - 1, fromOver3.moveCount)
        val replayed = Game.replay(BoardSize.of(3), SEED, over3.moveLog.toList().dropLast(1))
        assertEquals(replayed.copy(undoCount = 1), fromOver3)

        // From KEEP_PLAYING: an anchored state whose LEFT fills the last cell (spawn control).
        val keptAnchor = anchored(board(OVER_NEXT), ANCHOR_32, GameStatus.KEEP_PLAYING, KEPT_AT)
        assertNoViolations(keptAnchor)
        val toOver = accepted(Game.move(keptAnchor, Direction.LEFT))
        assertNoViolations(toOver.state)
        assertTrue(toOver.becameOver)
        assertEquals(GameStatus.OVER, toOver.state.status)
        val fromOver = undo(toOver.state)
        assertEquals(GameStatus.KEEP_PLAYING, fromOver.status)
        assertTrue(Game.canMove(fromOver))
        assertEquals(toOver.state.moveCount - 1, fromOver.moveCount)
        assertEquals(KEPT_AT, fromOver.keepGoingAtMove)
        assertEquals(keptAnchor.copy(undoCount = 1), fromOver)
    }

    /** D-214: undo of a log that does not replay from its snapshot throws, never returns */
    @Test
    fun undoOfInconsistentHistoryThrows() {
        // Only a replay sees a snapshot board that the logged moves do not follow from, so
        // GameInvariants accepts the state; undo fails fast, and :app treats the exception as a
        // corrupt game (D-214). Undo from move 40 replays move 32 (LEFT) first: a lone tile in
        // the top-left corner cannot move left.
        val played = playDlru(FOUR, FORTY)
        assertEquals(Direction.LEFT, played.moveLog[Game.SNAPSHOT_INTERVAL])
        val lone = Board.of(FOUR, ByteArray(FOUR.cellCount).also { it[0] = 1 })
        val corrupt =
            played.copy(
                snapshots = listOf(played.snapshots[0], played.snapshots[1].copy(board = lone)),
            )
        assertNoViolations(corrupt)
        assertThrows<IllegalStateException> { Game.undo(corrupt) }
    }

    private fun assertLongGame(state: GameState) {
        // ARCHITECTURE.md §9.4 preconditions.
        assertNoViolations(state)
        assertEquals(LONG_GAME_MOVES, state.moveCount)
        assertEquals(LONG_GAME_SCORE, state.score)
        assertEquals(LONG_GAME_RNG, state.rngState)
        assertEquals(LONG_GAME_MAX_EXP, state.board.maxExp())
        assertEquals(GameStatus.KEEP_PLAYING, state.status)
        assertEquals(WIN_MOVE, state.keepGoingAtMove)
        assertEquals(LONG_GAME_SNAPSHOTS, state.snapshots.size)
        assertEquals(LONG_GAME_LOG_BYTES, state.moveLog.toPackedBytes().size)
        assertEquals(0, state.undoCount)
    }

    companion object {
        private const val SEED = 0x2048L
        private const val LONG_GAME_MOVES = 10_000
        private const val LONG_GAME_SCORE = 208_236L
        private const val LONG_GAME_RNG = 0x26E3A2DE9847F93CL
        private const val LONG_GAME_MAX_EXP = 12
        private const val LONG_GAME_SNAPSHOTS = 313
        private const val LONG_GAME_LOG_BYTES = 2_500
        private const val WIN_MOVE = 1_678
        private const val KEPT_MOVES = 22
        private const val THREE_BY_THREE_OVER_AT = 43
        private const val ANCHOR_32 = 32
        private const val ANCHOR_96 = 96
        private const val WIN_AT = 100
        private const val KEPT_AT = 10
        private const val FORTY = 40
        private const val ANCHOR_SCORE = 20_000L
        private val FOUR = BoardSize.of(4)
        private val EIGHT = BoardSize.of(8)

        /** Column 0 holds two 1024s; rows 0–1 are full with no other equal neighbours. */
        private const val WIN_AT_100 = "10 3 4 5 / 10 4 5 3 / 1 0 0 0 / 0 0 0 0"

        /** LEFT leaves index 3 as the only empty cell; a 2 there leaves no equal pair. */
        private const val OVER_NEXT = "3 3 5 2 / 11 7 6 7 / 7 6 7 6 / 6 7 6 7"

        /** ARCHITECTURE.md §9.4 policy: the first accepted of DOWN, LEFT, RIGHT, UP. */
        private val DLRU = listOf(Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP)

        private fun board(text: String): Board {
            val cells = text.split(' ').filter { it != "/" }.map(String::toByte).toByteArray()
            val n = (BoardSize.MIN_N..BoardSize.MAX_N).first { it * it == cells.size }
            return Board.of(BoardSize.of(n), cells)
        }

        private fun dlruStepOrNull(state: GameState): MoveOutcome.Moved? =
            DLRU.firstNotNullOfOrNull {
                Game.move(state, it) as? MoveOutcome.Moved
            }

        private fun dlruStep(state: GameState): MoveOutcome.Moved {
            val moved = dlruStepOrNull(state)
            assertNotNull(moved, "no accepted move at ${state.moveCount}")
            assertNoViolations(moved!!.state)
            return moved
        }

        /** Continues [start] with DLRU until [moves] moves, keeping going at once on a win. */
        private fun playDlruFrom(start: GameState, moves: Int): GameState {
            assertNoViolations(start)
            var state = start
            while (state.moveCount < moves) {
                val moved = dlruStep(state)
                state = if (moved.becameWon) Game.keepGoing(moved.state) else moved.state
            }
            assertNoViolations(state)
            return state
        }

        /** The ARCHITECTURE.md §9.4 generator: DLRU from newGame(size, 0x2048). */
        private fun playDlru(size: BoardSize, moves: Int): GameState =
            playDlruFrom(Game.newGame(size, SEED), moves)

        private fun playDlruUntilStuck(size: BoardSize): GameState {
            var state = Game.newGame(size, SEED)
            var moved = dlruStepOrNull(state)
            while (moved != null) {
                state = if (moved.becameWon) Game.keepGoing(moved.state) else moved.state
                moved = dlruStepOrNull(state)
            }
            return state
        }

        /**
         * A state at [moveCount] (a multiple of 32) whose last snapshot is [board] itself: undo
         * back to the anchor rebuilds from that snapshot. The earlier moves are filler.
         */
        private fun anchored(
            board: Board,
            moveCount: Int,
            status: GameStatus,
            keepGoingAtMove: Int,
        ): GameState {
            val start = Game.newGame(board.size, SEED).snapshots[0]
            val fillers =
                (1 until moveCount / Game.SNAPSHOT_INTERVAL).map {
                    start.copy(moveCount = it * Game.SNAPSHOT_INTERVAL)
                }
            val anchor = Snapshot(moveCount, ANCHOR_SCORE, SEED, status, board)
            val log =
                (0 until moveCount).fold(MoveLog.EMPTY) { log, i ->
                    log.append(Direction.entries[i % Direction.entries.size])
                }
            return GameState(
                size = board.size,
                seed = SEED,
                board = board,
                score = ANCHOR_SCORE,
                rngState = SEED,
                status = status,
                moveCount = moveCount,
                keepGoingAtMove = keepGoingAtMove,
                moveLog = log,
                snapshots = listOf(start) + fillers + anchor,
                undoCount = 0,
            )
        }

        /** Undo of a state with moves; asserts the input and the result are consistent states. */
        private fun undo(state: GameState): GameState {
            assertNoViolations(state)
            val undone = Game.undo(state)
            assertNotNull(undone, "undo at ${state.moveCount}")
            assertNoViolations(undone!!)
            assertTrue(undone.undoCount > state.undoCount)
            return undone
        }

        /** The accepted outcome; asserts its state is consistent. */
        private fun accepted(outcome: MoveOutcome): MoveOutcome.Moved {
            val moved = assertInstanceOf(MoveOutcome.Moved::class.java, outcome)
            assertNoViolations(moved.state)
            return moved
        }

        private fun assertNoViolations(state: GameState) {
            assertEquals(emptyList<String>(), GameInvariants.violations(state))
        }
    }
}
