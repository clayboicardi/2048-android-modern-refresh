package com.clayboicardi.game2048.data

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Finalization
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsMath
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.GameStatus.KEEP_PLAYING
import com.clayboicardi.game2048.engine.GameStatus.OVER
import com.clayboicardi.game2048.engine.GameStatus.PLAYING
import com.clayboicardi.game2048.engine.MoveLog
import com.clayboicardi.game2048.engine.Snapshot
import com.clayboicardi.game2048.engine.SplitMix64
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * `RecordsMath` (ARCHITECTURE.md §4.9; D-044, D-198; FEATURES.md §3; QUALITY.md §1.6): the
 * finalization arithmetic and its readings, the identity guard, Reset, `nextGameId`, `displayed`,
 * `aggregate`, and the no-best-decreases property over seeded sequences.
 */
class RecordsMathTest {
    /** F08-AC1 */
    @Test
    fun finalizeOverGame() {
        val r = RecordsMath.finalize(Records(), Finalization(S4, 1, OVER, 3012, 302, 401_000, 2, 7))

        val s = r.sizes.getValue("4")
        assertThat(s.gamesPlayed).isEqualTo(1)
        assertThat(s.gamesWon).isEqualTo(0)
        assertThat(s.gamesAbandoned).isEqualTo(0)
        assertThat(s.totalScore).isEqualTo(3012L)
        assertThat(s.totalMoves).isEqualTo(302L)
        assertThat(s.totalTimeMs).isEqualTo(401_000L)
        assertThat(s.totalUndos).isEqualTo(2L)
        assertThat(s.tileReach).containsExactly(1, 1, 1, 1, 1, 1, 1, 1).inOrder()
        assertThat(s.currentWinStreak).isEqualTo(0)
        assertThat(s.longestWinStreak).isEqualTo(0)
        // The progress arithmetic on the final values: the game used undo, so no no-undo best.
        assertThat(s.bestScore).isEqualTo(3012L)
        assertThat(s.bestScoreNoUndo).isEqualTo(0L)
        assertThat(s.bestTileExp).isEqualTo(7)
        assertThat(r.lastFinalizedGameId).containsExactly("4", 1L)
        assertThat(r.sizes.keys).containsExactly("4")
    }

    /** F08-AC2 */
    @Test
    fun finalizeAbandonedGame() {
        val r = RecordsMath.finalize(Records(), Finalization(S4, 1, PLAYING, 500, 40, 30_000, 0, 6))

        val s = r.sizes.getValue("4")
        assertThat(s.gamesPlayed).isEqualTo(1)
        assertThat(s.gamesAbandoned).isEqualTo(1)
        assertThat(s.gamesWon).isEqualTo(0)
        assertThat(s.totalScore).isEqualTo(500L)
        assertThat(s.bestScoreNoUndo).isEqualTo(500L)
    }

    /** F08-AC3 */
    @Test
    fun winsAndStreaks() {
        val win = { id: Long -> Finalization(S4, id, OVER, 20_000, 1_200, 600_000, 0, 11) }
        val loss = { id: Long -> Finalization(S4, id, OVER, 9_000, 700, 300_000, 0, 9) }
        val keptPlaying = Finalization(S4, 7, KEEP_PLAYING, 25_000, 1_400, 700_000, 0, 11)
        val undoneWin = Finalization(S4, 8, PLAYING, 21_000, 1_300, 650_000, 3, 10)
        val games = listOf(win(1), win(2), loss(3), win(4), win(5), win(6), keptPlaying, undoneWin)

        var r = Records()
        val streaks = mutableListOf<Int>()
        for (f in games) {
            val before = r.sizes["4"] ?: SizeRecord()
            r = RecordsMath.finalize(r, f)
            val after = r.sizes.getValue("4")
            streaks += after.currentWinStreak
            if (f === keptPlaying) {
                // D-198: a KEEP_PLAYING game replaced by New game is won and abandoned.
                assertThat(after.gamesWon).isEqualTo(before.gamesWon + 1)
                assertThat(after.gamesAbandoned).isEqualTo(before.gamesAbandoned + 1)
            }
        }

        val s = r.sizes.getValue("4")
        assertThat(streaks).containsExactly(1, 2, 0, 1, 2, 3, 4, 0).inOrder()
        assertThat(s.gamesPlayed).isEqualTo(8)
        assertThat(s.gamesWon).isEqualTo(6)
        assertThat(s.gamesAbandoned).isEqualTo(2)
        assertThat(s.currentWinStreak).isEqualTo(0)
        assertThat(s.longestWinStreak).isEqualTo(4)
        // The undone win is judged on its final board (max exponent 10): not a win (D-198).
        assertThat(s.tileReach).hasSize(12)
        assertThat(s.tileReach[11]).isEqualTo(6)
        assertThat(s.tileReach[10]).isEqualTo(7)
    }

    /** F08-AC4 */
    @Test
    fun bestsNeverDecrease() {
        repeat(SEQUENCES) { i ->
            val rng = SplitMix64(0x2048L + i)
            var r = Records()
            repeat(STEPS) { step ->
                val size = BoardSize.ALL[rng.nextInt(BoardSize.ALL.size)]
                val score = rng.nextInt(MAX_SCORE).toLong()
                val undos = rng.nextInt(3)
                val maxExp = 1 + rng.nextInt(MAX_RANDOM_EXP)
                val next =
                    if (rng.nextInt(2) == 0) {
                        RecordsMath.progress(r, game(size, score, undos, maxExp))
                    } else {
                        val f =
                            Finalization(
                                size = size,
                                gameId = 1L + rng.nextInt(MAX_GAME_ID),
                                status = GameStatus.entries[rng.nextInt(GameStatus.entries.size)],
                                score = score,
                                moveCount = rng.nextInt(MAX_MOVES),
                                elapsedMs = rng.nextInt(MAX_ELAPSED).toLong(),
                                undoCount = undos,
                                maxExp = maxExp,
                            )
                        val after = RecordsMath.finalize(r, f)
                        val k = RecordsMath.key(size)
                        if (f.gameId <= (r.lastFinalizedGameId[k] ?: 0)) {
                            assertWithMessage("sequence %s step %s: finalized id", i, step)
                                .that(after)
                                .isSameInstanceAs(r)
                        } else {
                            assertWithMessage("sequence %s step %s: watermark", i, step)
                                .that(after.lastFinalizedGameId[k])
                                .isEqualTo(f.gameId)
                        }
                        after
                    }
                assertNoDecrease(r, next, i, step)
                r = next
            }
        }
    }

    /** F08-AC5 */
    @Test
    fun progressUpdatesBestsLive() {
        val start = Records(sizes = mapOf("4" to SizeRecord(bestScore = 1000, bestTileExp = 9)))

        val raised = RecordsMath.progress(start, game(S4, score = 1004, undoCount = 0, maxExp = 10))
        assertThat(raised.sizes.getValue("4").bestScore).isEqualTo(1004L)
        assertThat(raised.sizes.getValue("4").bestTileExp).isEqualTo(10)

        val lower = RecordsMath.progress(raised, game(S4, score = 900, undoCount = 0, maxExp = 9))
        assertThat(lower).isSameInstanceAs(raised)
        assertThat(lower.sizes.getValue("4").bestScore).isEqualTo(1004L)
        assertThat(lower.sizes.getValue("4").bestTileExp).isEqualTo(10)
        // Only bests change: no counter, no other size, no identity.
        assertThat(raised.sizes.getValue("4").gamesPlayed).isEqualTo(0)
        assertThat(raised.lastFinalizedGameId).isEmpty()
    }

    /** F08-AC6 */
    @Test
    fun progressFreezesNoUndoBestAfterUndo() {
        val before =
            RecordsMath.progress(Records(), game(S4, score = 2000, undoCount = 0, maxExp = 8))
        val after = RecordsMath.progress(before, game(S4, score = 3000, undoCount = 1, maxExp = 8))

        assertThat(after.sizes.getValue("4").bestScore).isEqualTo(3000L)
        assertThat(after.sizes.getValue("4").bestScoreNoUndo).isEqualTo(2000L)
    }

    /** F08-AC26 */
    @Test
    fun coerceBestTilesDropsOnlyUnreachableBests() {
        // N² + 1 per size, written out (D-270): 10, 17, 26, 37, 50, 65.
        val bound = mapOf(3 to 10, 4 to 17, 5 to 26, 6 to 37, 7 to 50, 8 to 65)
        for ((n, max) in bound) {
            val size = BoardSize.of(n)
            val kept = Records(sizes = mapOf("$n" to SizeRecord(bestScore = 50, bestTileExp = max)))
            assertWithMessage("$size at $max")
                .that(RecordsMath.coerceBestTiles(kept))
                .isSameInstanceAs(kept)
            for (bad in listOf(max + 1, 70, Board.MAX_EXP, -1)) {
                val record =
                    SizeRecord(
                        bestScore = 50,
                        bestTileExp = bad,
                        gamesPlayed = 2,
                        tileReach = listOf(2, 2),
                    )
                val read = RecordsMath.coerceBestTiles(Records(sizes = mapOf("$n" to record)))
                assertWithMessage("$size at $bad")
                    .that(read.sizes.getValue("$n"))
                    .isEqualTo(record.copy(bestTileExp = 0))
            }
        }

        // Valid records, the other sizes, other keys and the identity are kept as they are.
        assertThat(RecordsMath.coerceBestTiles(GoldenFixtures.FIX_RECORDS))
            .isSameInstanceAs(GoldenFixtures.FIX_RECORDS)
        val other = SizeRecord(bestTileExp = Board.MAX_EXP)
        val mixed =
            GoldenFixtures.FIX_RECORDS.copy(
                sizes =
                    GoldenFixtures.FIX_RECORDS.sizes +
                        mapOf("8" to SizeRecord(bestTileExp = 66), "daily:4" to other),
                lastFinalizedGameId = mapOf("4" to 6L),
            )
        val read = RecordsMath.coerceBestTiles(mixed)
        assertThat(read.sizes - "8").isEqualTo(mixed.sizes - "8")
        assertThat(read.sizes.getValue("8")).isEqualTo(SizeRecord())
        assertThat(read.lastFinalizedGameId).isEqualTo(mixed.lastFinalizedGameId)

        // The next accepted move raises the dropped best tile again.
        val raised =
            RecordsMath.progress(read, game(BoardSize.of(8), score = 0, undoCount = 0, maxExp = 12))
        assertThat(raised.sizes.getValue("8").bestTileExp).isEqualTo(12)
    }

    /** F08-AC7 */
    @Test
    fun aggregateAllSizes() {
        val daily = SizeRecord(bestScore = 999_999, bestTileExp = 15, gamesPlayed = 100)
        val records =
            V1Fixtures.RECORDS.copy(sizes = V1Fixtures.RECORDS.sizes + ("daily:4" to daily))

        assertThat(RecordsMath.aggregate(records))
            .isEqualTo(
                SizeRecord(
                    bestScore = 24680,
                    bestScoreNoUndo = 18212,
                    bestTileExp = 11,
                    gamesPlayed = 42,
                    gamesWon = 11,
                    gamesAbandoned = 6,
                    totalScore = 412560,
                    totalMoves = 23804,
                    totalTimeMs = 41_234_000,
                    totalUndos = 58,
                    tileReach = listOf(42, 42, 42, 42, 42, 41, 38, 33, 26, 17, 12, 9),
                    currentWinStreak = 0,
                    longestWinStreak = 0,
                ),
            )

        val finalized =
            RecordsMath.finalize(records, Finalization(S4, 7, OVER, 3012, 302, 401_000, 0, 7))
        assertThat(finalized.sizes["daily:4"]).isEqualTo(daily)
        assertThat(finalized.sizes.getValue("4").gamesPlayed).isEqualTo(38)
        assertThat(finalized.lastFinalizedGameId).doesNotContainKey("daily:4")
    }

    /** F08-AC19 */
    @Test
    fun finalizeIsIdempotentPerGameId() {
        val f = Finalization(S4, 7, OVER, 3012, 302, 401_000, 2, 7)

        val first = RecordsMath.finalize(Records(), f)
        assertThat(first.sizes.getValue("4").gamesPlayed).isEqualTo(1)
        assertThat(first.sizes.getValue("4").totalScore).isEqualTo(3012L)
        assertThat(first.lastFinalizedGameId).containsExactly("4", 7L)

        assertThat(RecordsMath.finalize(first, f)).isSameInstanceAs(first)
        assertThat(RecordsMath.finalize(first, f.copy(gameId = 5))).isSameInstanceAs(first)
    }

    /** F08-AC20 */
    @Test
    fun resetKeepsOnlyFinalizationIdentity() {
        val records =
            GoldenFixtures.FIX_RECORDS.copy(lastFinalizedGameId = mapOf("4" to 6L, "3" to 5L))

        val reset = RecordsMath.reset(records)

        assertThat(reset.sizes).isEmpty()
        assertThat(reset.lastFinalizedGameId).containsExactly("4", 6L, "3", 5L)
        assertThat(RecordsMath.nextGameId(reset, S4, null)).isEqualTo(7L)
        assertThat(RecordsMath.isFinalized(reset, S4, 6)).isTrue()
        assertThat(RecordsMath.isFinalized(reset, S4, 7)).isFalse()
        // nextGameId also respects the replaced game and the other sizes' watermarks.
        assertThat(RecordsMath.nextGameId(reset, S4, game(S4, 0, 0, 1).copy(gameId = 9)))
            .isEqualTo(10L)
        assertThat(RecordsMath.nextGameId(reset, BoardSize.of(3), null)).isEqualTo(6L)
        assertThat(RecordsMath.nextGameId(reset, BoardSize.of(8), null)).isEqualTo(1L)
    }

    /** The D-198 fold: recovery entries oldest first, skipping finalized ids, then an OVER game. */
    @Test
    fun displayedFoldsEntriesOldestFirstThenOverGame() {
        val records = Records(lastFinalizedGameId = mapOf("4" to 5L))
        val entries =
            listOf(
                Finalization(S4, 4, OVER, 50, 10, 1_000, 0, 4),
                Finalization(S4, 6, OVER, 100, 20, 2_000, 0, 5),
                Finalization(S4, 8, PLAYING, 200, 30, 3_000, 1, 6),
            )
        val over = game(S4, score = 300, undoCount = 0, maxExp = 7, status = OVER).copy(gameId = 9)

        val shown = RecordsMath.displayed(records, over.copy(pendingFinalizations = entries))

        val s = shown.sizes.getValue("4")
        assertThat(s.gamesPlayed).isEqualTo(3)
        assertThat(s.totalScore).isEqualTo(600L)
        assertThat(shown.lastFinalizedGameId).containsExactly("4", 9L)
        // A PLAYING game shows only its entries; no game shows the records unchanged.
        val playing = over.copy(state = over.state.copy(status = PLAYING))
        val withEntries =
            RecordsMath.displayed(records, playing.copy(pendingFinalizations = entries))
        assertThat(withEntries.sizes.getValue("4").gamesPlayed).isEqualTo(2)
        assertThat(RecordsMath.displayed(records, null)).isSameInstanceAs(records)
        // An OVER game whose id is already finalized is skipped.
        val counted = over.copy(gameId = 5)
        assertThat(RecordsMath.displayed(records, counted)).isSameInstanceAs(records)
        assertThat(RecordsMath.key(S4)).isEqualTo("4")
    }

    private fun assertNoDecrease(before: Records, after: Records, i: Int, step: Int) {
        for ((k, b) in before.sizes) {
            assertWithMessage("sequence %s step %s: size %s kept", i, step, k)
                .that(after.sizes)
                .containsKey(k)
            val a = after.sizes.getValue(k)
            assertWithMessage("sequence %s step %s: bestScore %s", i, step, k)
                .that(a.bestScore)
                .isAtLeast(b.bestScore)
            assertWithMessage("sequence %s step %s: bestScoreNoUndo %s", i, step, k)
                .that(a.bestScoreNoUndo)
                .isAtLeast(b.bestScoreNoUndo)
            assertWithMessage("sequence %s step %s: bestTileExp %s", i, step, k)
                .that(a.bestTileExp)
                .isAtLeast(b.bestTileExp)
        }
        for ((k, id) in before.lastFinalizedGameId) {
            assertWithMessage("sequence %s step %s: lastFinalizedGameId %s", i, step, k)
                .that(after.lastFinalizedGameId[k])
                .isAtLeast(id)
        }
    }

    private companion object {
        val S4 = BoardSize.of(4)
        const val SEQUENCES = 1_000
        const val STEPS = 50
        const val MAX_GAME_ID = 60
        const val MAX_SCORE = 1_000_000
        const val MAX_RANDOM_EXP = 17
        const val MAX_MOVES = 10_000
        const val MAX_ELAPSED = 10_000_000

        /**
         * A game (`gameId` 1) with [score], [undoCount] and one tile of [maxExp]: the only fields
         * `progress` and `toFinalization` read besides the size and id. Its history is a bare
         * move-0 snapshot, which `GameState.init` accepts; pure records math never replays it.
         */
        fun game(
            size: BoardSize,
            score: Long,
            undoCount: Int,
            maxExp: Int,
            status: GameStatus = PLAYING,
        ): SavedGame {
            val cells = ByteArray(size.cellCount).also { it[0] = maxExp.toByte() }
            val board = Board.of(size, cells)
            val state =
                GameState(
                    size = size,
                    seed = 0x2048,
                    board = board,
                    score = score,
                    rngState = 0,
                    status = status,
                    moveCount = 0,
                    keepGoingAtMove = -1,
                    moveLog = MoveLog.EMPTY,
                    snapshots = listOf(Snapshot(0, 0, 0, PLAYING, board)),
                    undoCount = undoCount,
                )
            return SavedGame(state, 0, 0, false, 1, emptyList())
        }
    }
}
