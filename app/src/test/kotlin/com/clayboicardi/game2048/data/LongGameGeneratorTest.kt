package com.clayboicardi.game2048.data

import com.clayboicardi.game2048.engine.GameInvariants
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.testing.LongGameGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The ARCHITECTURE.md §9.4 expected values of the 10,000-move game. */
class LongGameGeneratorTest {
    /** F07-AC22 */
    @Test
    fun matchesExpectedValues() {
        val game = LongGameGenerator.generate()
        val s = game.state

        assertThat(s.size.n).isEqualTo(8)
        assertThat(s.seed).isEqualTo(0x2048L)
        assertThat(s.moveCount).isEqualTo(10_000)
        assertThat(s.keepGoingAtMove).isEqualTo(1678)
        assertThat(s.score).isEqualTo(208236L)
        assertThat(s.rngState).isEqualTo(0x26E3A2DE9847F93CL)
        assertThat(s.board.maxExp()).isEqualTo(12)
        assertThat(s.status).isEqualTo(GameStatus.KEEP_PLAYING)
        assertThat(s.snapshots).hasSize(313)
        assertThat(s.moveLog.toPackedBytes()).hasLength(2500)
        assertThat(s.undoCount).isEqualTo(0)
        assertThat(GameInvariants.violations(s)).isEmpty()
        assertThat(game.elapsedMs).isEqualTo(3_600_000L)
        assertThat(game.bestAtStart).isEqualTo(0L)
        assertThat(game.newBestAnnounced).isFalse()
        assertThat(game.gameId).isEqualTo(1L)
        assertThat(game.pendingFinalizations).isEmpty()
        // The shared copy is the same game.
        assertThat(LongGameGenerator.game).isEqualTo(game)
    }
}
