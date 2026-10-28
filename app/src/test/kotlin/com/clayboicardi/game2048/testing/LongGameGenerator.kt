package com.clayboicardi.game2048.testing

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.MoveOutcome

/**
 * The 10,000-move 8×8 game of ARCHITECTURE.md §9.4: the D-046 budget document and the D-041 fixture
 * `game-8-long.json`. From `Game.newGame(8×8, 0x2048)`, each move is the first of `DOWN`, `LEFT`,
 * `RIGHT`, `UP` that the engine accepts, and Keep going follows the win at once.
 */
object LongGameGenerator {
    const val MOVES = 10_000
    const val SEED = 0x2048L
    const val ELAPSED_MS = 3_600_000L

    private val ORDER = listOf(Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP)

    /** The generated game, computed once per test JVM. */
    val game: SavedGame by lazy { generate() }

    /** Generates the game again; see [game] for the shared copy. */
    fun generate(): SavedGame {
        var s = Game.newGame(BoardSize.of(8), SEED)
        while (s.moveCount < MOVES) {
            val moved = ORDER.firstNotNullOf { Game.move(s, it) as? MoveOutcome.Moved }
            s = if (moved.becameWon) Game.keepGoing(moved.state) else moved.state
        }
        return SavedGame(
            state = s,
            elapsedMs = ELAPSED_MS,
            bestAtStart = 0,
            newBestAnnounced = false,
            gameId = 1,
            pendingFinalizations = emptyList(),
        )
    }
}
