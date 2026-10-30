package com.clayboicardi.game2048.stats

import com.clayboicardi.game2048.data.records.Finalization
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.fixtures.fixtureGame
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `StatsViewModel` over real DataStore files (ARCHITECTURE.md §4.7; FEATURES.md F08 items 3, 5–6,
 * §3.2; D-198). The virtual clock is driven with `testScheduler.runCurrent()` only (D-215, D-220).
 * Values are read through the ViewModel's `ui`, formatted for English.
 */
class StatsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F08-AC9 */
    @Test
    fun fourByFourDerivedValues() = runTest {
        val vm = loaded(tmp.newFolder()) { seedRecords(GoldenFixtures.FIX_RECORDS) }

        val tab = checkNotNull(vm.ui.value).tab(statsTabIndex(4), Locale.ENGLISH)

        assertThat(tab.empty).isFalse()
        assertThat(tab.gamesPlayed).isEqualTo("37")
        assertThat(tab.wins).isEqualTo("9")
        assertThat(tab.winRate).isEqualTo("24%")
        assertThat(tab.bestScore).isEqualTo("24,680")
        assertThat(tab.bestNoUndo).isEqualTo("18,212")
        assertThat(tab.averageScore).isEqualTo("11,150")
        assertThat(tab.totalMoves).isEqualTo("23,804")
        assertThat(tab.timePlayed).isEqualTo("11:27:14")
        assertThat(tab.undosUsed).isEqualTo("58")
        assertThat(tab.bestTileExp).isEqualTo(11)
    }

    /** F08-AC10 */
    @Test
    fun allSizesAggregation() = runTest {
        val vm = loaded(tmp.newFolder()) { seedRecords(GoldenFixtures.FIX_RECORDS) }

        val tab = checkNotNull(vm.ui.value).tab(ALL_SIZES, Locale.ENGLISH)

        assertThat(STATS_TABS[ALL_SIZES]).isNull()
        assertThat(tab.empty).isFalse()
        assertThat(tab.gamesPlayed).isEqualTo("42")
        assertThat(tab.wins).isEqualTo("11")
        assertThat(tab.winRate).isEqualTo("26%")
        assertThat(tab.bestScore).isEqualTo("24,680")
        assertThat(tab.averageScore).isEqualTo("9,822")
        assertThat(tab.bestTileExp).isEqualTo(11)
    }

    /** F08-AC11 */
    @Test
    fun emptyRecordIsEmptyState() = runTest {
        val vm = loaded(tmp.newFolder()) { seedRecords(GoldenFixtures.FIX_RECORDS) }
        val ui = checkNotNull(vm.ui.value)

        assertThat(ui.tab(statsTabIndex(5), Locale.ENGLISH).empty).isTrue()
        for (n in listOf(6, 7, 8)) {
            assertThat(ui.tab(statsTabIndex(n), Locale.ENGLISH).empty).isTrue()
        }
        assertThat(ui.tab(statsTabIndex(3), Locale.ENGLISH).empty).isFalse()
        assertThat(ui.tab(statsTabIndex(4), Locale.ENGLISH).empty).isFalse()
    }

    /** F08-AC21 */
    @Test
    fun foldCountsEachGameIdOnce() = runTest {
        // Case 1: the saved OVER game is already finalized (gameId 7 = lastFinalizedGameId["4"]).
        val finalized = Records(lastFinalizedGameId = mapOf("4" to 7L))
        val first = tmp.newFolder()
        val over = GoldenFixtures.FIX_OVER_4.copy(gameId = 7)
        val skipped =
            loaded(first) {
                seedRecords(finalized)
                seedGame(over)
            }
        val one = checkNotNull(skipped.ui.value).tab(statsTabIndex(4), Locale.ENGLISH)
        assertThat(one.gamesPlayed).isEqualTo("0")
        assertThat(container(first).recordsStore.data.first()).isEqualTo(finalized)

        // Case 2: a fresh game (gameId 8) carrying two OVER recovery entries, oldest first.
        val second = tmp.newFolder()
        val fresh =
            GoldenFixtures.FIX_FRESH_4.copy(
                gameId = 8,
                pendingFinalizations = listOf(entry(6, score = 1000), entry(7, score = 3012)),
            )
        val counted =
            loaded(second) {
                seedRecords(Records())
                seedGame(fresh)
            }
        val two = checkNotNull(counted.ui.value).tab(statsTabIndex(4), Locale.ENGLISH)
        assertThat(two.gamesPlayed).isEqualTo("2")
        assertThat(two.averageScore).isEqualTo("2,006")
        assertThat(container(second).recordsStore.data.first()).isEqualTo(Records())
        assertThat(container(second).recordsRepository.submissions).isEqualTo(0)
        assertThat(container(first).recordsRepository.submissions).isEqualTo(0)
    }

    /** F08-AC18 */
    @Test
    fun overGameCountedBeforeNextNewGame() = runTest {
        val folder = tmp.newFolder()
        // FIX_OVER_PRE_4's board at score 1,000 and 32 moves, so the move that ends it undoes
        // exactly back to the fixture: LEFT spawns the last empty cell and leaves no move.
        MotionFixtures.OVER_PRE_LEFT.verify()
        val pre =
            fixtureGame(
                n = 4,
                cells = MotionFixtures.FIX_OVER_PRE_4.state.board.let { b -> List(16) { b[it] } },
                score = 1_000,
                best = 0,
                moves = 32,
                undos = 0,
                elapsedMs = 0,
                status = GameStatus.PLAYING,
                rngState = 0x2048,
            )
        val moved = Game.move(pre.state, Direction.LEFT) as MoveOutcome.Moved
        assertThat(moved.state.status).isEqualTo(GameStatus.OVER)
        assertThat(moved.state.score).isEqualTo(1_000L)
        val over = pre.copy(state = moved.state)
        val counted =
            loaded(folder) {
                seedRecords(Records())
                seedGame(over)
            }
        val before = checkNotNull(counted.ui.value).tab(statsTabIndex(4), Locale.ENGLISH)
        assertThat(before.gamesPlayed).isEqualTo("1")
        assertThat(before.bestScore).isEqualTo("1,000")
        assertThat(container(folder).recordsStore.data.first()).isEqualTo(Records())
        assertThat(container(folder).recordsRepository.submissions).isEqualTo(0)

        // Undo takes the game back out of OVER before any new game finalized it (D-198): the
        // next Statistics screen, a new ViewModel, no longer counts it.
        val c = container(folder)
        val undone = checkNotNull(Game.undo(over.state)) { "the game has a move to undo" }
        c.gameRepository.save(over.copy(state = undone))
        val after = StatsViewModel(c.recordsRepository, c.gameRepository, c.settingsRepository)
        testScheduler.runCurrent()
        val tab = checkNotNull(after.ui.value).tab(statsTabIndex(4), Locale.ENGLISH)
        assertThat(tab.gamesPlayed).isEqualTo("0")
        assertThat(c.recordsStore.data.first()).isEqualTo(Records())
        assertThat(c.recordsRepository.submissions).isEqualTo(0)
    }

    private val containers = HashMap<File, AppContainer>()

    /** The container over [folder], made once per folder (one DataStore per file, D-022). */
    private fun TestScope.container(folder: File): AppContainer =
        containers.getOrPut(folder) { testAppContainer(folder) }

    /** A `StatsViewModel` over [folder] after [seed], with its first fold done. */
    private suspend fun TestScope.loaded(
        folder: File,
        seed: suspend AppContainer.() -> Unit,
    ): StatsViewModel {
        val c = container(folder)
        c.seed()
        val vm = StatsViewModel(c.recordsRepository, c.gameRepository, c.settingsRepository)
        testScheduler.runCurrent()
        checkNotNull(vm.ui.value) { "The ViewModel did not fold the records" }
        return vm
    }

    private companion object {
        /** The "All sizes" tab. */
        const val ALL_SIZES = 0

        /** A 4×4 `OVER` recovery entry of [gameId] with [score] (F08-AC21). */
        fun entry(gameId: Long, score: Long): Finalization =
            Finalization(
                size = BoardSize.DEFAULT,
                gameId = gameId,
                status = GameStatus.OVER,
                score = score,
                moveCount = MOVES,
                elapsedMs = ELAPSED_MS,
                undoCount = 0,
                maxExp = MAX_EXP,
            )

        const val MOVES = 100
        const val ELAPSED_MS = 60_000L
        const val MAX_EXP = 8
    }
}
