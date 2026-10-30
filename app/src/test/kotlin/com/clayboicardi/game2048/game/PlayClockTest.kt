package com.clayboicardi.game2048.game

import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.fixtureGame
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** `PlayClock` (D-043, ARCHITECTURE.md §4.9): pure, driven with plain millisecond values. */
class PlayClockTest {
    /** F04-AC12 */
    @Test
    fun stopsWhenGameEnds() {
        val clock = PlayClock()
        clock.restart(now = 0, elapsedMs = 0, state = inProgress)
        clock.resume(now = 0)
        assertThat(clock.elapsed(now = 5_000)).isEqualTo(5_000)

        clock.onAccepted(now = 10_000, state = over)
        assertThat(clock.elapsed(now = 10_000)).isEqualTo(10_000)
        assertThat(clock.elapsed(now = 20_000)).isEqualTo(10_000)
    }

    /** F05-AC14 */
    @Test
    fun undoDoesNotRewindElapsed() {
        val clock = PlayClock()
        clock.restart(now = 100_000, elapsedMs = 60_000, state = afterTwoMoves)
        clock.resume(now = 100_000)
        assertThat(clock.elapsed(now = 100_000)).isEqualTo(60_000)

        clock.onAccepted(now = 100_000, state = checkNotNull(Game.undo(afterTwoMoves)))
        assertThat(clock.elapsed(now = 100_000)).isEqualTo(60_000)
        assertThat(clock.elapsed(now = 105_000)).isEqualTo(65_000)
    }

    /** F07-AC4 */
    @Test
    fun countsOnlyWhileResumedAndPlaying() {
        val clock = PlayClock()
        clock.restart(now = 0, elapsedMs = 0, state = fresh)
        clock.resume(now = 0)
        assertWithMessage("before the first move").that(clock.elapsed(now = 1_000)).isEqualTo(0)

        clock.onAccepted(now = 1_000, state = afterOneMove)
        clock.pause(now = 1_000)
        assertWithMessage("while paused").that(clock.elapsed(now = 2_000)).isEqualTo(0)

        clock.resume(now = 2_000)
        clock.onAccepted(now = 2_000, state = won)
        assertWithMessage("in WON").that(clock.elapsed(now = 3_000)).isEqualTo(0)

        clock.onAccepted(now = 3_000, state = over)
        assertWithMessage("in OVER").that(clock.elapsed(now = 4_000)).isEqualTo(0)

        clock.onAccepted(now = 4_000, state = afterOneMove)
        assertWithMessage("PLAYING, resumed").that(clock.elapsed(now = 5_000)).isEqualTo(1_000)

        clock.onAccepted(now = 5_000, state = keepPlaying)
        assertWithMessage("KEEP_PLAYING, resumed").that(clock.elapsed(now = 6_000)).isEqualTo(2_000)

        // The running interval is folded without stopping: elapsed() keeps counting.
        assertThat(clock.elapsed(now = 7_000)).isEqualTo(3_000)
        clock.pause(now = 7_000)
        assertThat(clock.elapsed(now = 9_000)).isEqualTo(3_000)
    }

    private companion object {
        val FOUR: BoardSize = BoardSize.of(4)

        /** moveCount 0: no time accrues yet (D-043). */
        val fresh: GameState = Game.newGame(FOUR, 0x2048)

        val afterOneMove: GameState = firstAccepted(fresh)
        val afterTwoMoves: GameState = firstAccepted(afterOneMove)
        val inProgress: GameState = GoldenFixtures.FIX_MID_4.state
        val over: GameState = GoldenFixtures.FIX_OVER_4.state

        /** Full board without equal neighbours and a 2048 tile: `WON`; Keep going then plays on. */
        val won: GameState =
            fixtureGame(
                    n = 4,
                    cells = listOf(11, 2, 1, 2, 2, 1, 2, 1, 1, 2, 1, 2, 3, 0, 4, 1),
                    score = 30_000,
                    best = 0,
                    moves = 900,
                    undos = 0,
                    elapsedMs = 0,
                    status = GameStatus.WON,
                )
                .state

        val keepPlaying: GameState = Game.keepGoing(won)

        fun firstAccepted(state: GameState): GameState =
            Direction.entries.firstNotNullOf { Game.move(state, it) as? MoveOutcome.Moved }.state
    }
}
