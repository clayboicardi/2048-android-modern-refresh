package com.clayboicardi.game2048.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import kotlin.random.Random

/**
 * Holds the current [GameState] and best-score-per-size, drives moves through the shared engine,
 * and persists after every change through [IosGameStore]. Plain state holder (no Android
 * ViewModel): created once and remembered by the root composable.
 */
class GameModel(private val store: IosGameStore, private val random: Random = Random.Default) {
    private val best: MutableMap<Int, Long>

    var state by mutableStateOf(initialState())
        private set

    var bestScore by mutableStateOf(0L)
        private set

    /** Increments on every accepted move or new game, so the board can key its animations. */
    var generation by mutableStateOf(0)
        private set

    init {
        val loaded = store.load()
        best = loaded.best.toMutableMap()
        state = loaded.current ?: Game.newGame(BoardSize.DEFAULT, random.nextLong())
        bestScore = best[state.size.n] ?: 0L
        updateBest()
        persist()
    }

    private fun initialState(): GameState = Game.newGame(BoardSize.DEFAULT, 0L)

    /** Applies a swipe. Returns the outcome so the caller can fire the matching haptics. */
    fun move(direction: Direction): MoveOutcome {
        val outcome = Game.move(state, direction)
        if (outcome is MoveOutcome.Moved) {
            state = outcome.state
            generation++
            updateBest()
            persist()
        }
        return outcome
    }

    fun newGame(size: BoardSize = state.size) {
        state = Game.newGame(size, random.nextLong())
        generation++
        bestScore = best[size.n] ?: 0L
        persist()
    }

    fun keepGoing() {
        if (state.status == GameStatus.WON) {
            state = Game.keepGoing(state)
            persist()
        }
    }

    fun undo() {
        Game.undo(state)?.let {
            state = it
            generation++
            persist()
        }
    }

    private fun updateBest() {
        val n = state.size.n
        if (state.score > (best[n] ?: 0L)) {
            best[n] = state.score
            bestScore = state.score
        }
    }

    private fun persist() = store.save(state, best)
}
