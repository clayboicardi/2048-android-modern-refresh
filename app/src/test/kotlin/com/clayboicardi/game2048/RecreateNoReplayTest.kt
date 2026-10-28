package com.clayboicardi.game2048

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.TileKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/**
 * D-047: recreation and a pause/resume cycle replay nothing (ARCHITECTURE.md §4.11). In M2 `App()`
 * composes no board, so the test makes the presentation reports that `BoardView` and `GameHeader`
 * make from M3, and reads the activity-scoped `GameViewModel`.
 */
@RunWith(AndroidJUnit4::class)
class RecreateNoReplayTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val app: Game2048Application
        get() = ApplicationProvider.getApplicationContext()

    /** F07-AC7 */
    @Test
    fun recreateRendersStaticBoard() {
        val vm = readyViewModel()
        val presented = playPresentedMove(vm)
        val saved = savedGame()

        compose.activityRule.scenario.recreate()

        val after = activityViewModel()
        assertThat(after).isSameInstanceAs(vm)
        val board = after.board.value
        assertAllStatic(board)
        assertThat(board.event).isEqualTo(BoardEvent.Restored)
        assertThat(board.moveId).isEqualTo(presented.moveId)
        assertThat(board.epoch).isEqualTo(presented.epoch)
        assertThat(after.header.value.lastDelta).isEqualTo(0)
        assertThat(savedGame().state.moveLog).isEqualTo(saved.state.moveLog)
        assertThat(savedGame().state.moveCount).isEqualTo(1)
    }

    /** F07-AC8 */
    @Test
    fun pauseResumeDoesNotReplay() {
        val vm = readyViewModel()
        val presented = playPresentedMove(vm)
        val header = vm.header.value
        val scenario = compose.activityRule.scenario

        // From M3, GameScreen's LifecycleResumeEffect makes these two calls.
        scenario.moveToState(Lifecycle.State.CREATED)
        vm.onPaused()
        scenario.moveToState(Lifecycle.State.RESUMED)
        vm.onResumed()

        assertThat(activityViewModel()).isSameInstanceAs(vm)
        assertThat(vm.board.value).isEqualTo(presented)
        assertAllStatic(vm.board.value)
        assertThat(vm.header.value).isEqualTo(header)
        assertThat(vm.header.value.lastDelta).isEqualTo(0)
        assertThat(compose.onAllNodesWithText("+", substring = true).fetchSemanticsNodes())
            .isEmpty()
    }

    /** The activity's `GameViewModel`: the instance `by viewModels` created. */
    private fun activityViewModel(): GameViewModel {
        lateinit var vm: GameViewModel
        val factory = app.container.gameViewModelFactory()
        compose.activityRule.scenario.onActivity {
            vm = ViewModelProvider(it, factory)[GameViewModel::class.java]
        }
        return vm
    }

    /**
     * The activity's ViewModel once loaded. The load reads DataStore on real I/O threads and
     * resumes on the main looper, which Robolectric runs only when asked: each poll runs it.
     */
    private fun readyViewModel(): GameViewModel {
        val vm = activityViewModel()
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.ready.value
        }
        return vm
    }

    /** One accepted move from the fresh game, reported as presented (§4.11): a settled board. */
    private fun playPresentedMove(vm: GameViewModel): BoardUi {
        val start = vm.board.value.moveId
        Direction.entries.first { d ->
            vm.onMove(d)
            vm.board.value.moveId != start
        }
        val moveId = vm.board.value.moveId
        vm.onBoardPresented(moveId)
        vm.onHeaderPresented(moveId)
        return vm.board.value
    }

    /** The last game the ViewModel saved; `GameRepository` keeps it in memory, so nothing waits. */
    private fun savedGame(): SavedGame = runBlocking {
        checkNotNull(app.container.gameRepository.peek(BoardSize.DEFAULT))
    }

    private fun assertAllStatic(board: BoardUi) {
        assertThat(board.tiles).isNotEmpty()
        assertThat(board.tiles.map { it.kind }.toSet()).containsExactly(TileKind.Static)
    }

    private companion object {
        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
