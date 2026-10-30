package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** D-032–D-036: the legacy slide, merge, no-op, spawn and win rules (QUALITY.md §1.2). */
class EngineGoldenTest {
    /** F01-AC1 */
    @ParameterizedTest
    @MethodSource("rowLeftCases")
    fun rowLeftGoldens(input: String, row: Int) {
        val case = ROW_LEFT.getValue(input)
        // The row sits in row `row` of an otherwise empty 4×4 board; every other row stays empty.
        val result = slide(lineBoard(FOUR, Direction.LEFT, row, case.input), Direction.LEFT)
        val expected = lineCells(FOUR, Direction.LEFT, row, case.output)
        assertEquals(expected.toList(), result.newCells.toList())
        assertEquals(case.delta, result.scoreDelta)
        assertEquals(case.moved, result.moved)
    }

    /** F01-AC2 */
    @ParameterizedTest
    @MethodSource("rowRightAndVerticalCases")
    fun rowRightAndVerticalGoldens(case: String, direction: Direction, line: Int) {
        // Every case is stored wall first: index 0 is the cell next to the wall the move goes
        // toward. For RIGHT that is the row reversed (so a row-right case sits in the row exactly
        // as D-032 writes it), for UP the row written down column `line`, for DOWN that column
        // reversed (ARCHITECTURE.md §3.3 line order).
        val wallFirst = WALL_FIRST.getValue(case)
        val result = slide(lineBoard(FOUR, direction, line, wallFirst.input), direction)
        val expected = lineCells(FOUR, direction, line, wallFirst.output)
        assertEquals(expected.toList(), result.newCells.toList())
        assertEquals(wallFirst.delta, result.scoreDelta)
        assertEquals(wallFirst.moved, result.moved)
    }

    /** D-032: larger boards pad the same way */
    @ParameterizedTest
    @MethodSource("largerBoardCases")
    fun largerBoards(input: String, n: Int) {
        val size = BoardSize.of(n)
        val case = ROW_LEFT.getValue(input).padded(n)
        for (row in 0 until n) {
            val result = slide(lineBoard(size, Direction.LEFT, row, case.input), Direction.LEFT)
            val expected = lineCells(size, Direction.LEFT, row, case.output)
            assertEquals(expected.toList(), result.newCells.toList(), "row $row")
            assertEquals(case.delta, result.scoreDelta, "row $row")
            assertEquals(case.moved, result.moved, "row $row")
        }
    }

    /** D-034, D-035: a no-op draws nothing; a spawn draws value then cell */
    @ParameterizedTest
    @MethodSource("rowPositions")
    fun noOpDrawsNothing(row: Int) {
        val board = lineBoard(FOUR, Direction.LEFT, row, FULL_ROW)
        val state = playing(board)
        assertNoViolations(state)
        // `2 4 8 16` never moves sideways; UP and DOWN are no-ops only at their own wall.
        val noOps =
            listOfNotNull(
                Direction.LEFT,
                Direction.RIGHT,
                Direction.UP.takeIf { row == 0 },
                Direction.DOWN.takeIf { row == FOUR.n - 1 },
            )
        for (direction in noOps) {
            assertEquals(MoveOutcome.Invalid(direction), Game.move(state, direction))
        }
        assertEquals(playing(board), state)
        assertEquals(SEED, state.rngState)

        val direction = if (row == FOUR.n - 1) Direction.UP else Direction.DOWN
        val moved = accepted(Game.move(state, direction))
        assertNoViolations(moved.state)
        assertEquals(listOf(direction), moved.state.moveLog.toList())
        // The accepted move drew k values from SplitMix64: one for the value, at least one for
        // the cell, so the state advanced by exactly k golden gammas.
        assertEquals(1L, SplitMix64.GOLDEN_GAMMA * GAMMA_INVERSE)
        val draws = (moved.state.rngState - state.rngState) * GAMMA_INVERSE
        assertTrue(draws in MIN_SPAWN_DRAWS..MAX_SPAWN_DRAWS, "draws = $draws")

        val recorder = DrawRecorder(SplitMix64(SEED))
        val cells = board.toByteArray()
        val spawn = spawnInto(cells, recorder)
        assertEquals(listOf(Game.FOUR_ONE_IN, EMPTY_AROUND_FULL_ROW), recorder.bounds)
        assertEquals(0, board[spawn.index])
        assertEquals(spawn.exp, cells[spawn.index].toInt())
        // D-038: the first nextInt(10) from 0x2048 is 8, so the value is a 2 (exponent 1).
        assertEquals(1, spawn.exp)
        cells[spawn.index] = 0
        assertEquals(board.toByteArray().toList(), cells.toList())
    }

    /** D-036: the exact target wins, per board size */
    @ParameterizedTest
    @MethodSource("winRowCases")
    fun winRows(n: Int, row: String, won: Boolean) {
        val size = BoardSize.of(n)
        val tiles = values(row)
        val state = playing(lineBoard(size, Direction.LEFT, 0, tiles))
        assertNoViolations(state)
        val moved = accepted(Game.move(state, Direction.LEFT))
        assertNoViolations(moved.state)
        assertEquals(won, moved.becameWon)
        assertEquals(if (won) GameStatus.WON else GameStatus.PLAYING, moved.state.status)
        assertEquals(2L * tiles[0], moved.scoreDelta)
        assertEquals(exponent(tiles[0]) + 1, moved.state.board[0, 0])
        assertEquals(exponent(tiles[0]) + 1, moved.maxMergedExp)
    }

    /** D-030, D-033: tile values and saturating score addition */
    @Test
    fun tileValueAndSaturatingAdd() {
        assertEquals(1L, tileValue(0))
        assertEquals(2L, tileValue(1))
        assertEquals(2048L, tileValue(11))
        assertEquals(1L shl 62, tileValue(62))
        assertEquals(Long.MAX_VALUE, tileValue(63))
        assertEquals(Long.MAX_VALUE, tileValue(Board.MAX_EXP))
        for (exp in 0..62) {
            assertEquals(1L shl exp, tileValue(exp), "exp $exp")
        }
        assertEquals(0L, saturatingAdd(0L, 0L))
        assertEquals(3L, saturatingAdd(1L, 2L))
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE - 1, 1L))
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE - 1, 2L))
        assertEquals(Long.MAX_VALUE, saturatingAdd(1L shl 62, 1L shl 62))
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE, tileValue(63)))
    }

    /** D-030, D-033: merges at the top of the exponent range keep I1 or fail fast */
    @Test
    fun mergeAtMaxExp() {
        // 126 + 126 is the largest merge that still fits: it produces MAX_EXP, and the score
        // delta saturates because tileValue(MAX_EXP) does.
        val top = Board.MAX_EXP - 1
        val fits = playing(exponentRow(top, top))
        assertNoViolations(fits)
        val merged = slide(fits.board, Direction.LEFT)
        assertEquals(Board.MAX_EXP, merged.newCells[0].toInt())
        assertEquals(listOf(Board.MAX_EXP), merged.mergedExps)
        assertEquals(Long.MAX_VALUE, merged.scoreDelta)
        val moved = accepted(Game.move(fits, Direction.LEFT))
        assertNoViolations(moved.state)
        assertEquals(Board.MAX_EXP, moved.state.board[0, 0])
        assertEquals(Long.MAX_VALUE, moved.state.score)

        // MAX_EXP + MAX_EXP has no exponent byte. The state is consistent (I1 accepts 127), so
        // the merge check of ARCHITECTURE.md §3.3 rejects the move instead of storing -128, and
        // :app handles the exception as a corrupt game (D-214).
        val overflow = playing(exponentRow(Board.MAX_EXP, Board.MAX_EXP))
        assertNoViolations(overflow)
        assertThrows<IllegalStateException> { slide(overflow.board, Direction.LEFT) }
        assertThrows<IllegalStateException> { Game.move(overflow, Direction.LEFT) }
        // A move that leaves the pair unmerged is still accepted.
        assertNoViolations(accepted(Game.move(overflow, Direction.DOWN)).state)
        // Consistent, but above the 4×4 reachable maximum: :app rejects it at decode (D-270).
        assertNotNull(GameInvariants.reachability(overflow))
    }

    /** D-030, D-270: the reachable maximum N² + 1 and GameInvariants.reachability */
    @Test
    fun reachableMaximum() {
        // N² + 1 written out: 2^10 = 1024 on 3×3, 2^17 = 131072 on 4×4, 2^65 on 8×8.
        assertEquals(listOf(10, 17, 26, 37, 50, 65), BoardSize.ALL.map { it.maxReachableExp })
        for (size in BoardSize.ALL) {
            val bound = size.maxReachableExp
            val atBound = playing(cornerTile(size, bound))
            assertNoViolations(atBound)
            assertNull(GameInvariants.reachability(atBound), "$size at $bound")
            // One above it is still consistent: the bound is not one of the invariants.
            val above = playing(cornerTile(size, bound + 1))
            assertNoViolations(above)
            assertEquals(
                "Reachability: every exponent on $size must be at most $bound, but the board " +
                    "holds ${bound + 1}",
                GameInvariants.reachability(above),
            )
        }

        // Only in a snapshot: snapshots[1] of a 32-move 4×4 game, the base of its next undo.
        var game = Game.newGame(FOUR, SEED)
        while (game.moveCount < Game.SNAPSHOT_INTERVAL) {
            game =
                Direction.entries.firstNotNullOf { Game.move(game, it) as? MoveOutcome.Moved }.state
        }
        assertNull(GameInvariants.reachability(game))
        val base = game.snapshots[1].copy(board = cornerTile(FOUR, FOUR.maxReachableExp + 1))
        val inSnapshot = game.copy(snapshots = listOf(game.snapshots[0], base))
        assertNoViolations(inSnapshot)
        assertEquals(
            "Reachability: every exponent on 4x4 must be at most 17, but snapshots[1] holds 18",
            GameInvariants.reachability(inSnapshot),
        )

        // Moves do not keep the bound: two adjacent 17s, which a decoded 4×4 game may hold, merge
        // into 18, so :app checks the state of every move and undo too (D-270).
        val pair = playing(exponentRow(FOUR.maxReachableExp, FOUR.maxReachableExp))
        assertNull(GameInvariants.reachability(pair))
        val merged = accepted(Game.move(pair, Direction.LEFT)).state
        assertNoViolations(merged)
        assertEquals(FOUR.maxReachableExp + 1, merged.board.maxExp())
        assertNotNull(GameInvariants.reachability(merged))

        // Play from a new game stays within it: a 3×3 game cycling the four directions to OVER.
        val three = BoardSize.of(3)
        val over = Arbs.play(three, SEED, List(OVER_GAME_MOVES) { Direction.entries[it % 4] })
        assertEquals(GameStatus.OVER, over.status)
        assertNull(GameInvariants.reachability(over))
    }

    /** D-030: Board construction, queries, equality and text form */
    @Test
    fun boardBasics() {
        val three = BoardSize.of(3)
        val source = byteArrayOf(0, 0, 1, 2, 0, 0, 0, 0, 0)
        val board = Board.of(three, source)
        source[0] = 5
        assertEquals(0, board[0])
        assertEquals(three, board.size)
        assertEquals(9, board.cellCount)
        assertEquals(1, board[0, 2])
        assertEquals(2, board[1, 0])
        assertEquals(2, board[3])
        assertEquals(7, board.emptyCount())
        assertEquals(2, board.tileCount())
        assertEquals(2, board.maxExp())
        assertEquals("0 0 1 / 2 0 0 / 0 0 0", board.toString())

        val copy = board.toByteArray()
        copy[0] = 9
        assertEquals(0, board[0])
        assertEquals(listOf<Byte>(0, 0, 1, 2, 0, 0, 0, 0, 0), board.toByteArray().toList())

        val same = Board.of(three, byteArrayOf(0, 0, 1, 2, 0, 0, 0, 0, 0))
        assertEquals(board, same)
        assertEquals(board.hashCode(), same.hashCode())
        assertNotEquals(board, Board.empty(three))
        assertNotEquals(Board.empty(three), Board.empty(FOUR))

        val empty = Board.empty(FOUR)
        assertEquals(16, empty.emptyCount())
        assertEquals(0, empty.tileCount())
        assertEquals(0, empty.maxExp())
        assertEquals("0 0 0 0 / 0 0 0 0 / 0 0 0 0 / 0 0 0 0", empty.toString())
        assertEquals(Board.of(FOUR, ByteArray(16)), empty)

        val top = Board.of(three, ByteArray(9).also { it[8] = Board.MAX_EXP.toByte() })
        assertEquals(Board.MAX_EXP, top.maxExp())
        assertEquals(Board.MAX_EXP, top[2, 2])

        assertThrows<IllegalArgumentException> { Board.of(three, ByteArray(8)) }
        assertThrows<IllegalArgumentException> { Board.of(three, ByteArray(10)) }
        assertThrows<IllegalArgumentException> { Board.of(FOUR, ByteArray(9)) }
        // Byte 128 is (-128).toByte(); -1 is 0xFF. Both lie outside 0..MAX_EXP.
        assertThrows<IllegalArgumentException> {
            Board.of(three, ByteArray(9).also { it[4] = 128.toByte() })
        }
        assertThrows<IllegalArgumentException> { Board.of(three, ByteArray(9).also { it[0] = -1 }) }
    }

    /** Wall-first line case in tile values (2, 4, …; 0 = empty), as D-032 writes them. */
    private class LineCase(
        val input: List<Int>,
        val output: List<Int>,
        val delta: Long,
        val moved: Boolean,
    ) {
        fun reversed(): LineCase = LineCase(input.reversed(), output.reversed(), delta, moved)

        fun padded(n: Int): LineCase =
            LineCase(
                input + List(n - input.size) { 0 },
                output + List(n - output.size) { 0 },
                delta,
                moved,
            )
    }

    /** Records every bound it is asked for and forwards the draw. */
    private class DrawRecorder(private val delegate: RandomSource) : RandomSource {
        val bounds = mutableListOf<Int>()

        override fun nextInt(bound: Int): Int {
            bounds += bound
            return delegate.nextInt(bound)
        }
    }

    companion object {
        private const val SEED = 0x2048L
        private const val MIN_SPAWN_DRAWS = 2L
        private const val MAX_SPAWN_DRAWS = 8L
        private const val EMPTY_AROUND_FULL_ROW = 12

        /** Directions [reachableMaximum] cycles through, more than a 3×3 game lasts. */
        private const val OVER_GAME_MOVES = 2_000
        private val FOUR = BoardSize.of(4)
        private val FULL_ROW = listOf(2, 4, 8, 16)

        /** Multiplicative inverse of the odd GOLDEN_GAMMA mod 2^64 (Newton iteration). */
        private val GAMMA_INVERSE: Long = inverse(SplitMix64.GOLDEN_GAMMA)

        /** D-032 row-left table, keyed by its input; values are tile values. */
        private val ROW_LEFT: Map<String, LineCase> =
            linkedMapOf(
                row("2 2 2 2", "4 4 0 0", 8),
                row("2 2 2 0", "4 2 0 0", 4),
                row("4 2 2 0", "4 4 0 0", 4),
                row("8 0 8 8", "16 8 0 0", 16),
                row("2 2 4 4", "4 8 0 0", 12),
                row("2 2 4 0", "4 4 0 0", 4),
                row("4 4 8 0", "8 8 0 0", 8),
                row("8 8 8 0", "16 8 0 0", 16),
                row("2 0 2 4", "4 4 0 0", 4),
                row("2 2 0 2", "4 2 0 0", 4),
                row("0 0 0 2", "2 0 0 0", 0),
                row("2 4 8 16", "2 4 8 16", 0, moved = false),
                row("1024 1024 0 0", "2048 0 0 0", 2048),
            )

        /** D-032 row-right table as written (the wall is on the right). */
        private val ROW_RIGHT: Map<String, LineCase> =
            linkedMapOf(
                row("2 2 2 2", "0 0 4 4", 8),
                row("2 2 2 0", "0 0 2 4", 4),
                row("8 0 8 8", "0 0 8 16", 16),
            )

        /** All 16 cases, wall first. */
        private val WALL_FIRST: Map<String, LineCase> =
            ROW_LEFT.mapKeys { "left ${it.key}" } +
                ROW_RIGHT.mapKeys { "right ${it.key}" }.mapValues { it.value.reversed() }

        @JvmStatic
        fun rowLeftCases(): List<Arguments> =
            ROW_LEFT.keys.flatMap { input -> rowPositions().map { Arguments.of(input, it) } }

        @JvmStatic
        fun rowRightAndVerticalCases(): List<Arguments> =
            WALL_FIRST.keys.flatMap { case ->
                listOf(Direction.RIGHT, Direction.UP, Direction.DOWN).flatMap { direction ->
                    rowPositions().map { Arguments.of(case, direction, it) }
                }
            }

        @JvmStatic
        fun largerBoardCases(): List<Arguments> =
            ROW_LEFT.keys.flatMap { input -> (5..8).map { Arguments.of(input, it) } }

        @JvmStatic fun rowPositions(): List<Int> = (0 until 4).toList()

        @JvmStatic
        fun winRowCases(): List<Arguments> =
            listOf(
                Arguments.of(4, "1024 1024 0 0", true),
                Arguments.of(3, "128 128 0", true),
                Arguments.of(4, "128 128 0 0", false),
            )

        private fun row(
            input: String,
            output: String,
            delta: Long,
            moved: Boolean = true,
        ): Pair<String, LineCase> = input to LineCase(values(input), values(output), delta, moved)

        private fun values(text: String): List<Int> = text.split(' ').map(String::toInt)

        private fun exponent(value: Int): Int =
            if (value == 0) 0 else Integer.numberOfTrailingZeros(value)

        /** Cell indices of line `line` for [direction], wall first (ARCHITECTURE.md §3.3). */
        private fun lineIndices(n: Int, direction: Direction, line: Int): List<Int> =
            (0 until n).map { i ->
                when (direction) {
                    Direction.LEFT -> line * n + i
                    Direction.RIGHT -> line * n + (n - 1 - i)
                    Direction.UP -> i * n + line
                    Direction.DOWN -> (n - 1 - i) * n + line
                }
            }

        private fun lineCells(
            size: BoardSize,
            direction: Direction,
            line: Int,
            values: List<Int>,
        ): ByteArray {
            val cells = ByteArray(size.cellCount)
            lineIndices(size.n, direction, line).forEachIndexed { i, index ->
                cells[index] = exponent(values[i]).toByte()
            }
            return cells
        }

        private fun lineBoard(
            size: BoardSize,
            direction: Direction,
            line: Int,
            values: List<Int>,
        ): Board = Board.of(size, lineCells(size, direction, line, values))

        /** A [size] board with one tile of exponent [exp] in the top-left corner. */
        private fun cornerTile(size: BoardSize, exp: Int): Board =
            Board.of(size, ByteArray(size.cellCount).also { it[0] = exp.toByte() })

        /** 4×4 board whose row 0 starts with the exponents [first] and [second]. */
        private fun exponentRow(first: Int, second: Int): Board =
            Board.of(
                FOUR,
                ByteArray(FOUR.cellCount).also {
                    it[0] = first.toByte()
                    it[1] = second.toByte()
                },
            )

        /** Hand-built PLAYING state at move 0 whose snapshot is newGame's (I7 holds). */
        private fun playing(board: Board): GameState =
            GameState(
                size = board.size,
                seed = SEED,
                board = board,
                score = 0L,
                rngState = SEED,
                status = GameStatus.PLAYING,
                moveCount = 0,
                keepGoingAtMove = -1,
                moveLog = MoveLog.EMPTY,
                snapshots = Game.newGame(board.size, SEED).snapshots,
                undoCount = 0,
            )

        private fun accepted(outcome: MoveOutcome): MoveOutcome.Moved =
            assertInstanceOf(MoveOutcome.Moved::class.java, outcome)

        private fun assertNoViolations(state: GameState) {
            assertEquals(emptyList<String>(), GameInvariants.violations(state))
        }

        private fun inverse(odd: Long): Long {
            var x = odd
            repeat(NEWTON_STEPS) { x *= 2 - odd * x }
            return x
        }

        private const val NEWTON_STEPS = 5
    }
}
