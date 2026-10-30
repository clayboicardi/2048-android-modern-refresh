package com.clayboicardi.game2048.game

import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.isInProgress

/**
 * Play-time accounting of the current game (D-043, ARCHITECTURE.md §4.9). Pure: every call takes
 * the current `MonotonicClock` time [now] in milliseconds, so tests drive it with plain numbers.
 *
 * Time accrues only while both hold:
 * - the Game entry is resumed ([resume] until [pause]);
 * - the game is in progress: `moveCount >= 1` and the status is `PLAYING` or `KEEP_PLAYING`
 *   (`GameState.isInProgress`).
 *
 * The clock never goes back: an undo, a pause or a game end only stops accrual, and [elapsed] folds
 * the running interval without stopping it, so every save carries the current time.
 *
 * Calls, all on the main thread:
 * - [restart] when a game becomes current (load, New game, size switch, a replaced corrupt game);
 * - [onAccepted] after every change of the current game's state (move, undo, keep going);
 * - [resume] and [pause] from the Game entry's lifecycle;
 * - [elapsed] for each save.
 */
class PlayClock {
    private var folded = 0L
    private var runningSince: Long? = null
    private var resumed = false
    private var counting = false

    /** The current game is now [state], whose saved play time is [elapsedMs]. */
    fun restart(now: Long, elapsedMs: Long, state: GameState) {
        folded = elapsedMs
        counting = state.isInProgress
        runningSince = null
        settle(now)
    }

    /** The current game's state changed to [state]; time up to [now] belongs to the old state. */
    fun onAccepted(now: Long, state: GameState) {
        fold(now)
        counting = state.isInProgress
        settle(now)
    }

    /** The Game entry is resumed. */
    fun resume(now: Long) {
        fold(now)
        resumed = true
        settle(now)
    }

    /** The Game entry is paused; the time up to [now] is kept. */
    fun pause(now: Long) {
        fold(now)
        resumed = false
        settle(now)
    }

    /** The play time at [now], including a running interval, which keeps running. */
    fun elapsed(now: Long): Long = folded + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)

    private fun fold(now: Long) {
        folded = elapsed(now)
        runningSince = runningSince?.let { now }
    }

    private fun settle(now: Long) {
        runningSince = if (resumed && counting) runningSince ?: now else null
    }
}
