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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the current [GameState] and best-score-per-size, drives moves through the shared engine,
 * and persists after every change. Plain state holder (no Android ViewModel), remembered by the
 * root composable.
 *
 * All file I/O runs on [io], never on the main thread: [load] reads there before the board is
 * shown, and saves go through a single conflated writer, so they stay in order and only the newest
 * pending state is written.
 */
class GameModel(
    private val store: IosGameStore,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
) {
    private val best = mutableMapOf<Int, Long>()
    private val pendingSaves = Channel<PendingSave>(Channel.CONFLATED)

    /** False until [load] finishes; the UI shows no board before then, so no move can race it. */
    var loaded by mutableStateOf(false)
        private set

    var state by mutableStateOf(Game.newGame(BoardSize.DEFAULT, 0L))
        private set

    var bestScore by mutableStateOf(0L)
        private set

    init {
        scope.launch(io) { for (save in pendingSaves) store.save(save.state, save.best) }
    }

    /** Reads the save off the main thread; a fresh random game when there is none. */
    suspend fun load() {
        if (loaded) return
        val saved = withContext(io) { store.load() }
        best.putAll(saved.best)
        state = saved.current ?: Game.newGame(BoardSize.DEFAULT, random.nextLong())
        bestScore = best[state.size.n] ?: 0L
        updateBest()
        loaded = true
    }

    /** Applies a swipe. Returns the outcome so the caller can fire the matching haptics. */
    fun move(direction: Direction): MoveOutcome {
        val outcome = Game.move(state, direction)
        if (outcome is MoveOutcome.Moved) {
            state = outcome.state
            updateBest()
            persist()
        }
        return outcome
    }

    fun newGame(size: BoardSize = state.size) {
        state = Game.newGame(size, random.nextLong())
        bestScore = best[size.n] ?: 0L
        persist()
    }

    fun keepGoing() {
        if (state.status == GameStatus.WON) {
            state = Game.keepGoing(state)
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

    /** Hands an immutable snapshot to the writer; never blocks the caller. */
    private fun persist() {
        pendingSaves.trySend(PendingSave(state, best.toMap()))
    }

    private class PendingSave(val state: GameState, val best: Map<Int, Long>)
}
