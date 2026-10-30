package com.clayboicardi.game2048.engine

import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * P1–P13 of ARCHITECTURE.md §3.5 and QUALITY.md §1.4 (D-145 with TC-6). Expected values come from
 * D-032–D-039 and ARCHITECTURE.md §3.3; every state a property produces has no GameInvariants
 * violation.
 */
class EnginePropertiesTest {
    @BeforeEach
    fun seed() {
        PropertyTesting.defaultSeed = 0x2048L
        PropertyTesting.writeFailedSeed = false
    }

    /** F01-AC6 */
    @Test
    fun p1SumConservedPlusSpawn() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.board, Arbs.direction) { board, direction ->
                val r = slide(board, direction)
                assertEquals(
                    valueSum(board.toByteArray()),
                    valueSum(r.newCells),
                    "$direction $board",
                )
            }
            checkAll(Arbs.ITERATIONS, Arbs.playedGame, Arbs.direction) { game, direction ->
                assertNoViolations(game)
                val outcome = Game.move(game, direction)
                if (outcome is MoveOutcome.Moved) {
                    assertNoViolations(outcome.state)
                    val before = valueSum(game.board.toByteArray())
                    val grown = valueSum(outcome.state.board.toByteArray()) - before
                    // The slide conserves the sum, so the move adds exactly the spawned tile.
                    assertTrue(
                        grown == 2L || grown == 4L,
                        "grew by $grown: $direction ${game.board}",
                    )
                    assertEquals(value(outcome.spawn.exp), grown)
                }
            }
        }
    }

    /** F01-AC3 */
    @Test
    fun p2MergeOnceMatchesOracle() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.board, Arbs.direction) { board, direction ->
                val r = slide(board, direction)
                val n = board.size.n
                val expectedMerges = mutableListOf<Int>()
                for (l in 0 until n) {
                    val indices = line(n, direction, l)
                    val (expected, merged) = oracle(indices.map { board[it] })
                    val actual = indices.map { r.newCells[it].toInt() }
                    assertEquals(expected, actual, "line $l, $direction $board")
                    expectedMerges += merged
                }
                // Lines are processed in order l = 0 until n, each from its leading wall.
                assertEquals(expectedMerges, r.mergedExps, "$direction $board")
                assertEquals(expectedMerges.sumOf(::value), r.scoreDelta, "$direction $board")
            }
        }
    }

    /** F01-AC4 */
    @Test
    fun p3NoOpConsumesNothing() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.board, Arbs.direction) { board, direction ->
                val state = Arbs.stateOf(board)
                assertNoViolations(state)
                val r = slide(board, direction)
                // moved iff a tile changed cell or merged, which is iff the cells change (D-032).
                assertEquals(!r.newCells.contentEquals(board.toByteArray()), r.moved)
                val outcome = Game.move(state, direction)
                // The caller's state is untouched, rngState included.
                assertEquals(Arbs.stateOf(board), state)
                assertEquals(0x2048L, state.rngState)
                if (r.moved) {
                    assertSpawnMadeTwoDraws(state, r, outcome)
                } else {
                    assertEquals(MoveOutcome.Invalid(direction), outcome, "$direction $board")
                    assertArrayEquals(board.toByteArray(), r.newCells)
                }
            }
        }
    }

    /** F01-AC5 */
    @Test
    fun p4ScoreDeltaEqualsMergedValues() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.playedGame, Arbs.direction) { game, direction ->
                assertNoViolations(game)
                val outcome = Game.move(game, direction)
                if (outcome is MoveOutcome.Moved) {
                    val merged = slide(game.board, direction).mergedExps
                    val delta = merged.sumOf(::value)
                    assertEquals(delta, outcome.scoreDelta, "$direction ${game.board}")
                    assertEquals(merged.size, outcome.mergeCount)
                    assertEquals(merged.maxOrNull() ?: 0, outcome.maxMergedExp)
                    assertEquals(game.score + delta, outcome.state.score)
                    assertTrue(outcome.state.score >= game.score)
                    assertNoViolations(outcome.state)
                }
            }
        }
    }

    /** F01-AC7 */
    @Test
    fun p5TileCountAfterMerges() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.board, Arbs.direction) { board, direction ->
                val before = board.toByteArray().count { it.toInt() != 0 }
                assertEquals(before, board.tileCount())
                val r = slide(board, direction)
                val after = r.newCells.count { it.toInt() != 0 }
                assertEquals(before - r.mergedExps.size, after, "$direction $board")
                assertEquals(before, r.transitions.size)
                val state = Arbs.stateOf(board)
                assertNoViolations(state)
                val outcome = Game.move(state, direction)
                if (outcome is MoveOutcome.Moved) {
                    assertEquals(after + 1, outcome.state.board.tileCount(), "$direction $board")
                    assertEquals(r.mergedExps.size, outcome.mergeCount)
                    assertNoViolations(outcome.state)
                }
            }
        }
    }

    /** F01-AC8 */
    @Test
    fun p6MaxTileNeverDecreases() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.playedGame, Arb.list(Arbs.direction, 1..50)) {
                game,
                moves ->
                assertNoViolations(game)
                var state = game
                for (direction in moves) {
                    if (state.status == GameStatus.WON) {
                        state = Game.keepGoing(state)
                        assertNoViolations(state)
                    }
                    val outcome = Game.move(state, direction)
                    if (outcome is MoveOutcome.Moved) {
                        val previous = state.board.maxExp()
                        assertTrue(outcome.state.board.maxExp() >= previous, "$direction $state")
                        assertNoViolations(outcome.state)
                        state = outcome.state
                    }
                }
            }
        }
    }

    /** F01-AC9 */
    @Test
    fun p7RotationAndTransposeCommute() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.board, Arbs.direction) { board, direction ->
                val n = board.size.n
                val r = slide(board, direction)
                for (t in SYMMETRIES) {
                    val image = Board.of(board.size, t.cells(board.toByteArray(), n))
                    val tr = slide(image, t.direction(direction))
                    val context = "${t.name}, $direction $board"
                    assertArrayEquals(t.cells(r.newCells, n), tr.newCells, context)
                    assertEquals(r.scoreDelta, tr.scoreDelta, context)
                    assertEquals(r.moved, tr.moved, context)
                    assertEquals(r.mergedExps.sorted(), tr.mergedExps.sorted(), context)
                }
            }
        }
    }

    /** F01-AC10 */
    @Test
    fun p8GameOverEquivalence() {
        var locked = 0
        runBlocking {
            val boards = Arb.choice(Arbs.board, Arbs.lockedBoard, Arbs.nearlyLockedBoard)
            checkAll(Arbs.ITERATIONS, boards) { board ->
                val noMove = isFull(board) && equalPairs(board) == 0
                val state = Arbs.stateOf(board)
                assertNoViolations(state)
                val allInvalid =
                    Direction.entries.all { Game.move(state, it) == MoveOutcome.Invalid(it) }
                assertEquals(noMove, !Game.canMove(board), "$board")
                assertEquals(noMove, !Game.canMove(state), "$board")
                assertEquals(noMove, allInvalid, "$board")
                assertEquals(noMove, Direction.entries.none { slide(board, it).moved }, "$board")
                if (noMove) locked++
            }
            // The generators produce what their KDoc says.
            checkAll(Arbs.ITERATIONS, Arbs.lockedBoard) { board ->
                assertTrue(isFull(board) && equalPairs(board) == 0, "$board")
            }
            checkAll(Arbs.ITERATIONS, Arbs.nearlyLockedBoard) { board ->
                assertTrue(isFull(board) && equalPairs(board) == 1, "$board")
                // One pair: the two moves along its axis merge it, the other two are no-ops.
                assertEquals(2, Direction.entries.count { slide(board, it).moved }, "$board")
            }
        }
        // Both sides of the equivalence were exercised.
        assertTrue(locked in 1 until Arbs.ITERATIONS, "locked boards: $locked")
    }

    /** F03-AC6 */
    @Test
    fun p9WinReportedOnce() {
        var wins = 0
        runBlocking {
            val moves = Arb.list(Arbs.direction, 0..300)
            checkAll(Arbs.ITERATIONS, Arbs.size, Arb.long(), moves) { size, seed, directions ->
                wins += playCheckingWins(Game.newGame(size, seed), directions)
            }
            // Random play from a new game rarely reaches the target, so hand-made boards with
            // exponents up to 17 add games that win, and games that make the target again after
            // Keep going.
            checkAll(Arbs.ITERATIONS, Arbs.board, Arb.list(Arbs.direction, 1..50)) {
                board,
                directions ->
                wins += playCheckingWins(Arbs.stateOf(board), directions)
            }
        }
        assertTrue(wins > 0, "no game won, so the win rule was never exercised")
    }

    /** F01-AC11 */
    @Test
    fun p10ReplayIsDeterministic() {
        runBlocking {
            val moves = Arb.list(Arbs.direction, 0..300)
            checkAll(Arbs.ITERATIONS, Arbs.size, Arb.long(), moves) { size, seed, directions ->
                val (accepted, played) = acceptedPrefix(size, seed, directions)
                val first = Game.replay(size, seed, accepted)
                val second = Game.replay(size, seed, accepted)
                assertEquals(first, second)
                assertEquals(played, first)
                assertNoViolations(first)
            }
        }
    }

    /** F05-AC2 */
    @Test
    fun p11UndoInvertsAcceptedMove() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.playedGame, Arbs.direction) { game, direction ->
                assertUndoAroundMove(game, direction)
            }
            // Random play from a new game rarely wins, so won games bring WON, KEEP_PLAYING and
            // the D-036 reconstruction rule into the history that undo replays.
            checkAll(Arbs.ITERATIONS, Arbs.wonGame, Arbs.direction) { game, direction ->
                assertTrue(game.status == GameStatus.WON || game.keepGoingAtMove >= 0, "$game")
                assertUndoAroundMove(game, direction)
            }
            checkAll(Arbs.ITERATIONS, Arbs.overGame) { over ->
                assertEquals(GameStatus.OVER, over.status)
                assertNoViolations(over)
                val s = assertUndoIsPlayable(over)
                // The logged move is accepted again from the undone state: same spawn (D-039).
                val again = Game.move(s, over.moveLog[over.moveCount - 1])
                val moved = assertInstanceOf(MoveOutcome.Moved::class.java, again)
                assertEquals(over.board, moved.state.board)
                assertEquals(over.score, moved.state.score)
                assertEquals(over.rngState, moved.state.rngState)
                assertUndoInverts(s, moved)
            }
        }
    }

    /** F07-AC20 */
    @Test
    fun p12HistoryBytesRoundTrip() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.playedGame) { game ->
                assertNoViolations(game)
                val log = game.moveLog
                val packed = log.toPackedBytes()
                assertEquals(MoveLog.packedSize(log.size), packed.size)
                val decoded = MoveLog.fromPackedBytes(packed, log.size)
                assertEquals(log, decoded)
                assertEquals(log.toList(), decoded.toList())
                for (snapshot in game.snapshots) {
                    val bytes = snapshot.toBytes()
                    assertEquals(SNAPSHOT_HEADER_BYTES + game.size.cellCount, bytes.size)
                    assertEquals(snapshot, Snapshot.fromBytes(bytes, game.size))
                }
            }
        }
    }

    /** F01-AC12 */
    @Test
    fun p13SpawnLandsOnEmptyCell() {
        runBlocking {
            checkAll(Arbs.ITERATIONS, Arbs.playedGame, Arbs.direction) { game, direction ->
                assertNoViolations(game)
                val outcome = Game.move(game, direction)
                if (outcome is MoveOutcome.Moved) {
                    val cells = slide(game.board, direction).newCells
                    val spawn = outcome.spawn
                    assertEquals(0, cells[spawn.index].toInt(), "$spawn, $direction ${game.board}")
                    assertTrue(spawn.exp == 1 || spawn.exp == 2, "$spawn")
                    // The spawn is the only cell the move adds to the slid board.
                    cells[spawn.index] = spawn.exp.toByte()
                    assertEquals(Board.of(game.size, cells), outcome.state.board)
                    assertNoViolations(outcome.state)
                }
            }
        }
    }

    /** One of the 4 rotations or the transpose, applied to cells and to directions alike. */
    private class Symmetry(val name: String, val quarterTurns: Int, val transpose: Boolean) {
        fun cells(cells: ByteArray, n: Int): ByteArray {
            var out = if (transpose) transposed(cells, n) else cells.copyOf()
            repeat(quarterTurns) { out = rotated(out, n) }
            return out
        }

        fun direction(direction: Direction): Direction {
            var out = if (transpose) transposed(direction) else direction
            repeat(quarterTurns) { out = rotated(out) }
            return out
        }
    }

    companion object {
        /** Snapshot bytes before the N² cells (ARCHITECTURE.md §3.4). */
        private const val SNAPSHOT_HEADER_BYTES = 21

        private val SYMMETRIES =
            listOf(
                Symmetry("rotation 0", 0, transpose = false),
                Symmetry("rotation 90", 1, transpose = false),
                Symmetry("rotation 180", 2, transpose = false),
                Symmetry("rotation 270", 3, transpose = false),
                Symmetry("transpose", 0, transpose = true),
            )

        private fun assertNoViolations(state: GameState) {
            assertEquals(emptyList<String>(), GameInvariants.violations(state), "$state")
        }

        /** 2^exp, written out here so that expected values do not come from the engine. */
        private fun value(exp: Int): Long = 1L shl exp

        /** Σ of tile values over the non-empty cells. */
        private fun valueSum(cells: ByteArray): Long = cells.sumOf {
            if (it.toInt() == 0) 0L else value(it.toInt())
        }

        /** Cell indices of line [l], starting at the wall [direction] goes toward (§3.3). */
        private fun line(n: Int, direction: Direction, l: Int): List<Int> =
            List(n) { i ->
                when (direction) {
                    Direction.LEFT -> l * n + i
                    Direction.RIGHT -> l * n + (n - 1 - i)
                    Direction.UP -> i * n + l
                    Direction.DOWN -> (n - 1 - i) * n + l
                }
            }

        /**
         * Naive oracle: drop zeros, merge equal neighbours left to right once, pad with zeros.
         * Returns the line and the merged exponents in order.
         */
        private fun oracle(line: List<Int>): Pair<List<Int>, List<Int>> {
            val tiles = line.filter { it != 0 }
            val out = mutableListOf<Int>()
            val merged = mutableListOf<Int>()
            var i = 0
            while (i < tiles.size) {
                if (i + 1 < tiles.size && tiles[i] == tiles[i + 1]) {
                    out += tiles[i] + 1
                    merged += tiles[i] + 1
                    i += 2
                } else {
                    out += tiles[i]
                    i += 1
                }
            }
            return (out + List(line.size - out.size) { 0 }) to merged
        }

        /** 90° clockwise: (row, col) goes to (col, n - 1 - row). */
        private fun rotated(cells: ByteArray, n: Int): ByteArray {
            val out = ByteArray(cells.size)
            for (index in cells.indices) {
                out[(index % n) * n + (n - 1 - index / n)] = cells[index]
            }
            return out
        }

        /** 90° clockwise maps UP → RIGHT → DOWN → LEFT → UP. */
        private fun rotated(direction: Direction): Direction =
            when (direction) {
                Direction.UP -> Direction.RIGHT
                Direction.RIGHT -> Direction.DOWN
                Direction.DOWN -> Direction.LEFT
                Direction.LEFT -> Direction.UP
            }

        /** (row, col) goes to (col, row). */
        private fun transposed(cells: ByteArray, n: Int): ByteArray {
            val out = ByteArray(cells.size)
            for (index in cells.indices) {
                out[(index % n) * n + index / n] = cells[index]
            }
            return out
        }

        /** The transpose swaps UP ↔ LEFT and DOWN ↔ RIGHT. */
        private fun transposed(direction: Direction): Direction =
            when (direction) {
                Direction.UP -> Direction.LEFT
                Direction.LEFT -> Direction.UP
                Direction.DOWN -> Direction.RIGHT
                Direction.RIGHT -> Direction.DOWN
            }

        private fun isFull(board: Board): Boolean = board.toByteArray().none { it.toInt() == 0 }

        /** Equal orthogonal pairs, from all four neighbours of every cell, each pair once. */
        private fun equalPairs(board: Board): Int {
            val n = board.size.n
            var ordered = 0
            for (index in 0 until board.cellCount) {
                ordered += neighbours(index, n).count { board[it] == board[index] }
            }
            return ordered / 2
        }

        private fun neighbours(index: Int, n: Int): List<Int> {
            val row = index / n
            val col = index % n
            return listOfNotNull(
                (index - n).takeIf { row > 0 },
                (index + n).takeIf { row < n - 1 },
                (index - 1).takeIf { col > 0 },
                (index + 1).takeIf { col < n - 1 },
            )
        }

        /**
         * The accepted move consumed exactly the 2 draws of spawnInto (value, then cell): the same
         * spawn through a counting source over SplitMix64(state.rngState) gives the same spawn,
         * board and rngState.
         */
        private fun assertSpawnMadeTwoDraws(
            state: GameState,
            r: SlideResult,
            outcome: MoveOutcome,
        ) {
            val moved = assertInstanceOf(MoveOutcome.Moved::class.java, outcome)
            val rng = SplitMix64(state.rngState)
            val counting = CountingRandomSource(rng)
            val cells = r.newCells.copyOf()
            val spawn = spawnInto(cells, counting)
            val empty = r.newCells.count { it.toInt() == 0 }
            assertEquals(2, counting.count)
            assertEquals(listOf(Game.FOUR_ONE_IN, empty), counting.bounds)
            assertEquals(spawn, moved.spawn)
            assertEquals(Board.of(state.size, cells), moved.state.board)
            assertEquals(rng.state, moved.state.rngState)
            assertNoViolations(moved.state)
        }

        /**
         * Plays [moves] from [start] (Keep going on WON) and checks D-036 on every accepted move:
         * becameWon exactly when a merge makes targetExp from PLAYING. Returns the number of wins.
         */
        private fun playCheckingWins(start: GameState, moves: List<Direction>): Int {
            assertNoViolations(start)
            var state = start
            var wins = 0
            for (direction in moves) {
                if (state.status == GameStatus.WON) {
                    state = Game.keepGoing(state)
                    assertNoViolations(state)
                }
                val outcome = Game.move(state, direction)
                if (outcome is MoveOutcome.Moved) {
                    val merged = slide(state.board, direction).mergedExps
                    val expected =
                        state.status == GameStatus.PLAYING && state.size.targetExp in merged
                    val context = "${state.status} $direction ${state.board}"
                    assertEquals(expected, outcome.becameWon, context)
                    assertEquals(expected, outcome.state.status == GameStatus.WON, context)
                    assertNoViolations(outcome.state)
                    if (outcome.becameWon) wins++
                    state = outcome.state
                }
            }
            assertTrue(wins <= 1, "won $wins times from ${start.board}")
            return wins
        }

        /**
         * The accepted moves of [moves] from newGame(size, seed), stopping at the first WON or OVER
         * (replay cannot Keep going), and the state they reach.
         */
        private fun acceptedPrefix(
            size: BoardSize,
            seed: Long,
            moves: List<Direction>,
        ): Pair<List<Direction>, GameState> {
            var state = Game.newGame(size, seed)
            val accepted = mutableListOf<Direction>()
            for (direction in moves) {
                if (state.status == GameStatus.WON || state.status == GameStatus.OVER) break
                val outcome = Game.move(state, direction)
                if (outcome is MoveOutcome.Moved) {
                    state = outcome.state
                    accepted += direction
                }
            }
            return accepted to state
        }

        /** undo is null at move 0; otherwise it is PLAYING or KEEP_PLAYING. Returns the undo. */
        private fun assertUndoIsPlayable(state: GameState): GameState {
            val undone = Game.undo(state)
            if (state.moveCount == 0) {
                assertNull(undone)
                return state
            }
            assertNotNull(undone)
            val playable = setOf(GameStatus.PLAYING, GameStatus.KEEP_PLAYING)
            assertTrue(undone!!.status in playable, "undo of ${state.status} gave ${undone.status}")
            assertNoViolations(undone)
            return undone
        }

        /**
         * Undo of [game], and of the state s that Keep going makes of a WON [game], is playable;
         * undo inverts the move of [direction] from s when it is accepted.
         */
        private fun assertUndoAroundMove(game: GameState, direction: Direction) {
            assertNoViolations(game)
            val s = if (game.status == GameStatus.WON) Game.keepGoing(game) else game
            assertNoViolations(s)
            assertUndoIsPlayable(game)
            assertUndoIsPlayable(s)
            // Keep going adds no move, so its undo goes below keepGoingAtMove and resets it: the
            // same state as undoing the winning move (D-036, D-039).
            if (game.status == GameStatus.WON) assertEquals(Game.undo(game), Game.undo(s))
            val outcome = Game.move(s, direction)
            if (outcome is MoveOutcome.Moved) assertUndoInverts(s, outcome)
        }

        /** undo(m.state)!!.copy(undoCount = s.undoCount) == s, and undoCount grows by one. */
        private fun assertUndoInverts(s: GameState, m: MoveOutcome.Moved) {
            assertNoViolations(m.state)
            val undone = Game.undo(m.state)
            assertNotNull(undone)
            assertEquals(s, undone!!.copy(undoCount = s.undoCount))
            assertEquals(m.state.undoCount + 1, undone.undoCount)
            assertNoViolations(undone)
        }
    }
}
