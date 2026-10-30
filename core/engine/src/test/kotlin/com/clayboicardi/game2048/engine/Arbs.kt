package com.clayboicardi.game2048.engine

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map

/**
 * kotest generators for the engine properties (QUALITY.md §1.4, ARCHITECTURE.md §10.2, D-145). All
 * randomness comes from kotest's seeded source (PropertyTesting.defaultSeed, TC-6) or from a
 * SplitMix64 with a fixed seed.
 */
object Arbs {
    const val ITERATIONS = 1_000

    /** Largest exponent of a generated tile (17 = 131072, D-145). */
    private const val MAX_GENERATED_EXP = 17

    /** Longest direction list of [playedGame]. */
    private const val MAX_PLAYED_MOVES = 300

    /** Move budget of one [overGame] attempt before the next seed is tried. */
    private const val OVER_GAME_MAX_MOVES = 2_000

    /** Smallest side on which random play reaches 2048 before OVER (about 1,200 moves). */
    private const val MIN_WON_N = 6

    /** Move budget of one [wonGame] attempt before the next seed is tried. */
    private const val WON_GAME_MAX_MOVES = 5_000

    /** Most accepted moves [wonGame] plays after Keep going: two snapshot intervals. */
    private const val MAX_MOVES_AFTER_WIN = 2 * Game.SNAPSHOT_INTERVAL

    /** Consecutive seeds [overGame] and [wonGame] try before failing instead of looping. */
    private const val MAX_SEEDS = 100

    val size: Arb<BoardSize> = Arb.int(BoardSize.MIN_N..BoardSize.MAX_N).map(BoardSize::of)
    val direction: Arb<Direction> = Arb.enum<Direction>()

    /** Exponents 0..17, weighted toward empty: 50 % empty, 40 % 2..2048, 10 % 4096..131072. */
    val exponent: Arb<Int> =
        Arb.choose(5 to Arb.constant(0), 4 to Arb.int(1..11), 1 to Arb.int(12..17))

    fun board(size: BoardSize): Arb<Board> =
        Arb.list(exponent, size.cellCount..size.cellCount).map { e ->
            Board.of(size, ByteArray(e.size) { e[it].toByte() })
        }

    val board: Arb<Board> = size.flatMap(::board)

    /** Full board with no equal orthogonal pair: each cell avoids its left and top neighbour. */
    val lockedBoard: Arb<Board> = arbitrary { rs ->
        val s = size.bind()
        Board.of(s, lockedCells(s.n) { bound -> rs.random.nextInt(bound) })
    }

    /** lockedBoard with one cell copied onto a neighbour, so exactly one merge exists. */
    val nearlyLockedBoard: Arb<Board> = arbitrary { rs ->
        val s = size.bind()
        val pick = { bound: Int -> rs.random.nextInt(bound) }
        var cells = copyOntoNeighbour(lockedCells(s.n, pick), s.n, pick)
        // A copy that also equals another neighbour of the target makes a second pair: redraw.
        while (equalPairCount(cells, s.n) != 1) {
            cells = copyOntoNeighbour(lockedCells(s.n, pick), s.n, pick)
        }
        Board.of(s, cells)
    }

    /**
     * A played game: seed, size and up to 300 directions; Invalid moves are skipped, WON is
     * continued with Game.keepGoing before the next move.
     */
    val playedGame: Arb<GameState> = arbitrary {
        play(size.bind(), Arb.long().bind(), Arb.list(direction, 0..MAX_PLAYED_MOVES).bind())
    }

    /** 3×3 games played from consecutive seeds until OVER (≤ 2,000 moves each). */
    val overGame: Arb<GameState> = arbitrary {
        val first = Arb.long().bind()
        checkNotNull(firstSeedGame(first, ::playUntilOver)) {
            "no 3x3 game from $MAX_SEEDS seeds at $first reached OVER"
        }
    }

    /**
     * A game that has won: 6×6..8×8 games played with random directions from consecutive seeds
     * until the first WON (≤ 5,000 moves each). It is either left WON, or continued with
     * Game.keepGoing and then up to 64 accepted random moves, so undo meets the D-036
     * reconstruction rule at every distance from the Keep going move.
     */
    val wonGame: Arb<GameState> = arbitrary { rs ->
        val size = BoardSize.of(Arb.int(MIN_WON_N..BoardSize.MAX_N).bind())
        val extra = Arb.int(-1..MAX_MOVES_AFTER_WIN).bind()
        val first = Arb.long().bind()
        val won =
            checkNotNull(firstSeedGame(first) { seed -> playUntilWon(size, seed) }) {
                "no $size game from $MAX_SEEDS seeds at $first reached WON"
            }
        if (extra < 0) won else keepGoingFor(won, extra) { bound -> rs.random.nextInt(bound) }
    }

    /**
     * Plays [moves] from `newGame(size, seed)`: an Invalid move is skipped, a WON state is
     * continued with [Game.keepGoing] before the next move, and play stops at OVER.
     */
    fun play(size: BoardSize, seed: Long, moves: List<Direction>): GameState {
        var state = Game.newGame(size, seed)
        for (direction in moves) {
            if (state.status == GameStatus.WON) state = Game.keepGoing(state)
            if (state.status == GameStatus.OVER) break
            val outcome = Game.move(state, direction)
            if (outcome is MoveOutcome.Moved) state = outcome.state
        }
        return state
    }

    /**
     * A hand-made PLAYING state at move 0 that holds [board]. snapshots[0] is newGame's, so I7
     * holds and GameInvariants stays empty. For single moves only, never for undo: undo rebuilds
     * from the snapshots and the log, not from [board].
     */
    fun stateOf(board: Board, seed: Long = 0x2048L): GameState =
        GameState(
            size = board.size,
            seed = seed,
            board = board,
            score = 0L,
            rngState = seed,
            status = GameStatus.PLAYING,
            moveCount = 0,
            keepGoingAtMove = -1,
            moveLog = MoveLog.EMPTY,
            snapshots = Game.newGame(board.size, seed).snapshots,
            undoCount = 0,
        )

    /** The first non-null [game] of the seeds first, first + 1, …, or null after 100 seeds. */
    private fun firstSeedGame(first: Long, game: (Long) -> GameState?): GameState? =
        generateSequence(first) { seed -> seed + 1 }.take(MAX_SEEDS).firstNotNullOfOrNull(game)

    /** Row-major fill from 1..17; each cell avoids the exponents of its left and top neighbour. */
    private fun lockedCells(n: Int, pick: (Int) -> Int): ByteArray {
        val cells = ByteArray(n * n)
        for (index in cells.indices) {
            val left = if (index % n > 0) cells[index - 1].toInt() else 0
            val top = if (index >= n) cells[index - n].toInt() else 0
            val candidates = (1..MAX_GENERATED_EXP).filter { it != left && it != top }
            cells[index] = candidates[pick(candidates.size)].toByte()
        }
        return cells
    }

    /** Copies a random cell onto one of its orthogonal neighbours, in place. */
    private fun copyOntoNeighbour(cells: ByteArray, n: Int, pick: (Int) -> Int): ByteArray {
        val from = pick(cells.size)
        val row = from / n
        val col = from % n
        val neighbours =
            listOfNotNull(
                (from - n).takeIf { row > 0 },
                (from + n).takeIf { row < n - 1 },
                (from - 1).takeIf { col > 0 },
                (from + 1).takeIf { col < n - 1 },
            )
        cells[neighbours[pick(neighbours.size)]] = cells[from]
        return cells
    }

    /** Number of equal orthogonal pairs, each counted once from its left or upper cell. */
    private fun equalPairCount(cells: ByteArray, n: Int): Int =
        cells.indices.sumOf { index ->
            val right = index % n < n - 1 && cells[index + 1] == cells[index]
            val down = index + n < cells.size && cells[index + n] == cells[index]
            (if (right) 1 else 0) + (if (down) 1 else 0)
        }

    /**
     * A 3×3 game from [seed] with directions drawn from SplitMix64(seed); Keep going on WON. Null
     * when it is not OVER within 2,000 moves.
     */
    private fun playUntilOver(seed: Long): GameState? {
        val directions = SplitMix64(seed)
        var state = Game.newGame(BoardSize.of(BoardSize.MIN_N), seed)
        var moves = 0
        while (state.status != GameStatus.OVER && moves < OVER_GAME_MAX_MOVES) {
            state = randomStep(state, directions)
            moves++
        }
        return state.takeIf { it.status == GameStatus.OVER }
    }

    /**
     * A [size] game from [seed] with directions drawn from SplitMix64(seed), up to its first WON.
     * Null when it ends OVER first or is not WON within 5,000 moves.
     */
    private fun playUntilWon(size: BoardSize, seed: Long): GameState? {
        val directions = SplitMix64(seed)
        var state = Game.newGame(size, seed)
        var moves = 0
        while (state.status == GameStatus.PLAYING && moves < WON_GAME_MAX_MOVES) {
            state = randomStep(state, directions)
            moves++
        }
        return state.takeIf { it.status == GameStatus.WON }
    }

    /** Keep going from [won], then [moves] accepted random moves, fewer when the game ends. */
    private fun keepGoingFor(won: GameState, moves: Int, directions: RandomSource): GameState {
        var state = Game.keepGoing(won)
        val target = state.moveCount + moves
        var attempts = 0
        while (
            state.moveCount < target &&
                state.status != GameStatus.OVER &&
                attempts < WON_GAME_MAX_MOVES
        ) {
            state = randomStep(state, directions)
            attempts++
        }
        return state
    }

    /** Keep going on WON; otherwise one move in a random direction, skipped when Invalid. */
    private fun randomStep(state: GameState, directions: RandomSource): GameState {
        if (state.status == GameStatus.WON) return Game.keepGoing(state)
        val next = Direction.fromCode(directions.nextInt(Direction.entries.size))
        return (Game.move(state, next) as? MoveOutcome.Moved)?.state ?: state
    }
}

/** A [RandomSource] that records the bound of every nextInt call before delegating (P3). */
class CountingRandomSource(private val delegate: RandomSource) : RandomSource {
    private val recorded = mutableListOf<Int>()

    /** The bounds of the nextInt calls so far, in call order. */
    val bounds: List<Int>
        get() = recorded.toList()

    /** The number of nextInt calls so far. */
    val count: Int
        get() = recorded.size

    override fun nextInt(bound: Int): Int {
        recorded += bound
        return delegate.nextInt(bound)
    }
}
