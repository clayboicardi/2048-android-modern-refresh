package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Engine budget of D-150 (ARCHITECTURE.md §3.7). The only clock read is in this test. */
class EnginePerformanceTest {
    /** F01-AC20 */
    @Test
    fun hundredThousandMovesUnderTwoSeconds() {
        val player = RandomPlayer()
        player.play(WARM_UP_MOVES)
        val start = System.nanoTime()
        player.play(TIMED_MOVES)
        val elapsed = System.nanoTime() - start
        val millis = elapsed / NANOS_PER_MILLI
        println("EnginePerformanceTest: $TIMED_MOVES moves in $millis ms")
        assertTrue(elapsed <= BUDGET_NANOS, "$TIMED_MOVES moves took $millis ms, budget 2000 ms")
        assertEquals(emptyList<String>(), GameInvariants.violations(player.state))
    }

    /**
     * 8×8 games from consecutive seeds 0x2048 + i, with directions from a fixed-seed SplitMix64.
     * Only accepted moves count; WON continues with Keep going, and OVER (also straight after Keep
     * going) starts the next seed.
     */
    private class RandomPlayer {
        private val size = BoardSize.of(BoardSize.MAX_N)
        private val directions = SplitMix64(0x2048L)
        private var nextSeed = 0x2048L

        var state: GameState = newGame()
            private set

        fun play(moves: Int) {
            var accepted = 0
            var attempts = 0L
            while (accepted < moves) {
                // Fails instead of looping forever when moves stop being accepted.
                check(++attempts <= ATTEMPTS_PER_MOVE * moves) { "$accepted of $moves accepted" }
                val direction = Direction.fromCode(directions.nextInt(Direction.entries.size))
                val outcome = Game.move(state, direction)
                if (outcome is MoveOutcome.Moved) {
                    accepted++
                    state = continued(outcome.state)
                }
            }
        }

        private fun continued(next: GameState): GameState {
            val kept = if (next.status == GameStatus.WON) Game.keepGoing(next) else next
            return if (kept.status == GameStatus.OVER) newGame() else kept
        }

        private fun newGame(): GameState = Game.newGame(size, nextSeed++)
    }

    companion object {
        private const val WARM_UP_MOVES = 10_000
        private const val TIMED_MOVES = 100_000
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val BUDGET_NANOS = 2_000L * NANOS_PER_MILLI

        /** Random directions per accepted move before the player gives up. */
        private const val ATTEMPTS_PER_MOVE = 100L
    }
}
