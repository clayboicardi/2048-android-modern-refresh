package com.clayboicardi.game2048

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.SplitMix64
import com.clayboicardi.game2048.fixtures.fixtureGame
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.OverlayEntry
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.kill
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Process death on JVM (QUALITY.md §1.11, D-047): container A plays and is killed after its writes
 * have been attempted; container B over the same folder reproduces every saved game exactly.
 * `testScheduler.runCurrent()` runs the containers' background work, which `advanceUntilIdle()`
 * does not wait for.
 */
class ProcessDeathRestoreTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F07-AC1 */
    @Test
    fun everySizeRestoresExactly() = runTest {
        val clock = FakeClock()
        val a = testAppContainer(tmp.root, clock)
        val vmA = loadedViewModel(a)
        for (size in BoardSize.ALL) {
            vmA.onSizeSelected(size)
            testScheduler.runCurrent()
            vmA.onResumed()
            val rng = SplitMix64(0x2048L + size.n)
            playRandom(vmA, rng, clock, moves = 40)
            repeat(3) { vmA.onUndo() }
            playRandom(vmA, rng, clock, moves = 5)
            vmA.onPaused()
        }
        awaitIdle(a)
        val saved = BoardSize.ALL.associateWith { checkNotNull(a.gameRepository.peek(it)) }
        val records = a.recordsRepository.state.value
        kill(a)

        val b = testAppContainer(tmp.root, clock)
        for (size in BoardSize.ALL) {
            assertWithMessage("$size").that(b.gameRepository.load(size)).isEqualTo(saved[size])
            assertWithMessage("$size played")
                .that(saved.getValue(size).state.moveCount)
                .isAtLeast(1)
        }
        assertThat(b.recordsRepository.awaitLoaded()).isEqualTo(records)
        assertThat(b.settingsRepository.awaitLoaded().lastBoardSize).isEqualTo(8)
        val vmB = loadedViewModel(b)
        val first = vmB.board.value
        assertThat(first.event).isEqualTo(BoardEvent.Restored)
        assertThat(first.tiles.map { it.kind }.toSet()).containsExactly(TileKind.Static)
        assertThat(first.cells).isEqualTo(cellsOf(saved.getValue(BoardSize.of(8))))
    }

    /** F04-AC10 */
    @Test
    fun overStateRestoresWithCard() = runTest {
        val a = testAppContainer(tmp.root)
        a.seedGame(ONE_MOVE_FROM_OVER)
        val vmA = loadedViewModel(a)
        vmA.onMove(Direction.LEFT)
        assertThat(vmA.uiState.value.status).isEqualTo(GameStatus.OVER)
        awaitIdle(a)
        val over = checkNotNull(a.gameRepository.peek(FOUR))
        kill(a)

        val b = testAppContainer(tmp.root)
        val vmB = loadedViewModel(b)

        assertThat(b.gameRepository.load(FOUR)).isEqualTo(over)
        assertThat(vmB.board.value.cells).isEqualTo(cellsOf(over))
        val overlay = vmB.overlay.value as OverlayUi.GameOver
        assertThat(overlay.entry).isEqualTo(OverlayEntry.Restored)
        assertThat(overlay.summary.score).isEqualTo(over.state.score)
        assertThat(overlay.summary.moves).isEqualTo(over.state.moveCount)
    }

    /** F05-AC15 */
    @Test
    fun undoIsPersisted() = runTest {
        val a = testAppContainer(tmp.root)
        val vmA = loadedViewModel(a)
        playAccepted(vmA, moves = 10)
        vmA.onUndo()
        val board = vmA.board.value.cells
        awaitIdle(a)
        kill(a)

        val b = testAppContainer(tmp.root)
        val restored = checkNotNull(b.gameRepository.load(FOUR))

        assertThat(restored.state.moveCount).isEqualTo(9)
        assertThat(restored.state.undoCount).isEqualTo(1)
        assertThat(cellsOf(restored)).isEqualTo(board)
    }

    /** F05-AC16 */
    @Test
    fun firstUndoAfterRestartUndoesLastMove() = runTest {
        val a = testAppContainer(tmp.root)
        val played = playAccepted(loadedViewModel(a), moves = 10)
        awaitIdle(a)
        kill(a)

        val b = testAppContainer(tmp.root)
        val vmB = loadedViewModel(b)
        vmB.onUndo()

        val afterNine = Game.replay(FOUR, 0x2048, played.take(9))
        val undone = checkNotNull(b.gameRepository.peek(FOUR))
        assertThat(undone.state.moveCount).isEqualTo(9)
        assertThat(undone.state.board).isEqualTo(afterNine.board)
        assertThat(vmB.board.value.cells).isEqualTo(cellsOf(undone))
    }
}

private val FOUR: BoardSize = BoardSize.of(4)

/** LEFT fills the last cell with no merge left: `OVER` (D-037). */
private val ONE_MOVE_FROM_OVER: SavedGame =
    fixtureGame(
        n = 4,
        cells = listOf(3, 4, 3, 4, 4, 3, 4, 3, 3, 4, 3, 4, 0, 5, 6, 5),
        score = 3000,
        best = 0,
        moves = 96,
        undos = 0,
        elapsedMs = 5_000,
        status = GameStatus.PLAYING,
    )

private fun TestScope.loadedViewModel(container: AppContainer): GameViewModel {
    val vm =
        GameViewModel(
            container.gameRepository,
            container.recordsRepository,
            container.settingsRepository,
            container.clock,
            container.seedSource,
        )
    testScheduler.runCurrent()
    check(vm.ready.value) { "The ViewModel did not become ready" }
    return vm
}

/** Every writer of [container] has attempted every submitted write. */
private suspend fun awaitIdle(container: AppContainer) {
    container.settingsRepository.awaitIdle()
    container.recordsRepository.awaitIdle()
    container.gameRepository.awaitIdle()
}

/** [moves] calls with directions from [rng]; invalid ones change nothing. 1 s apart, resumed. */
private fun playRandom(vm: GameViewModel, rng: SplitMix64, clock: FakeClock, moves: Int) {
    repeat(moves) {
        vm.onMove(Direction.fromCode(rng.nextInt(Direction.entries.size)))
        clock.advance(1_000)
    }
}

/** [moves] accepted moves, each the first of DOWN, LEFT, RIGHT, UP that changes the board. */
private fun playAccepted(vm: GameViewModel, moves: Int): List<Direction> =
    List(moves) {
        val before = vm.board.value.moveId
        DLRU.first { d ->
            vm.onMove(d)
            vm.board.value.moveId != before
        }
    }

private val DLRU = listOf(Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP)

private fun cellsOf(game: SavedGame): List<Int> =
    List(game.state.board.cellCount) { game.state.board[it] }
