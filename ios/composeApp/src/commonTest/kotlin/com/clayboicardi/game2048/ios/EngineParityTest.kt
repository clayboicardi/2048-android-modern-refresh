package com.clayboicardi.game2048.ios

import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.engine.SplitMix64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the shared :core:engine runs identically on this target as on the Android app: it is pure
 * Long/Byte math (SplitMix64, the board), so a divergence here would mean a Kotlin/Native vs JVM
 * arithmetic difference. Seeds follow the repo convention (0x2048).
 */
class EngineParityTest {
    private val moves =
        listOf(
            Direction.UP,
            Direction.LEFT,
            Direction.DOWN,
            Direction.RIGHT,
            Direction.UP,
            Direction.LEFT,
            Direction.DOWN,
            Direction.RIGHT,
            Direction.UP,
            Direction.LEFT,
            Direction.DOWN,
            Direction.RIGHT,
        )

    @Test
    fun newGameIsSeededAndStartsWithTwoTiles() {
        val a = Game.newGame(BoardSize.of(4), 0x2048L)
        val b = Game.newGame(BoardSize.of(4), 0x2048L)
        assertEquals(a.board, b.board)
        assertEquals(a.rngState, b.rngState)
        assertEquals(2, a.board.tileCount())
    }

    @Test
    fun replayFromAseedIsDeterministic() {
        val r1 = replay(0x2048L, moves)
        val r2 = replay(0x2048L, moves)
        assertEquals(r1.board, r2.board)
        assertEquals(r1.score, r2.score)
        assertEquals(r1.rngState, r2.rngState)
    }

    @Test
    fun splitMix64IsUniformAndPortable() {
        val rng = SplitMix64(0x2048L)
        val counts = IntArray(6)
        repeat(6_000) { counts[rng.nextInt(6)]++ }
        counts.forEachIndexed { value, n -> assertTrue(n in 900..1100, "bucket $value had $n") }
    }

    private fun replay(seed: Long, directions: List<Direction>): GameState {
        var state = Game.newGame(BoardSize.of(4), seed)
        for (direction in directions) {
            val outcome = Game.move(state, direction)
            if (outcome is MoveOutcome.Moved) state = outcome.state
        }
        return state
    }
}
