package com.clayboicardi.game2048.game

import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** D-052: state-first input with no queue; a burst of moves applies in order, none dropped. */
class InputBurstOrderingTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F15-AC13 */
    @Test
    fun threeSwipesApplyInOrder() = runTest {
        val container = testAppContainer(tmp.root)
        val vm =
            GameViewModel(
                container.gameRepository,
                container.recordsRepository,
                container.settingsRepository,
                container.clock,
                container.seedSource,
            )
        testScheduler.runCurrent()
        val swipes = listOf(Direction.LEFT, Direction.UP, Direction.RIGHT)

        // No clock or scheduler advance between the three calls: each applies synchronously to
        // the state the previous one produced (D-024, D-052).
        swipes.forEachIndexed { i, direction ->
            val before = vm.board.value.moveId
            vm.onMove(direction)
            val expected = Game.replay(FOUR, SEED, swipes.take(i + 1))
            val board = vm.board.value
            assertWithMessage("swipe $i moveId").that(board.moveId).isEqualTo(before + 1)
            assertWithMessage("swipe $i direction")
                .that((board.event as BoardEvent.Moved).direction)
                .isEqualTo(direction)
            assertWithMessage("swipe $i board")
                .that(board.cells)
                .isEqualTo(List(expected.board.cellCount) { expected.board[it] })
        }

        val expected = Game.replay(FOUR, SEED, swipes)
        assertThat(checkNotNull(container.gameRepository.peek(FOUR)).state).isEqualTo(expected)
        container.gameRepository.awaitIdle()
        val file = GameDocumentMapper.toSavedGame(container.gameStore(FOUR).data.first())
        assertThat(file.state).isEqualTo(expected)
        assertThat(file.state.moveLog.toList()).isEqualTo(swipes)
    }

    private companion object {
        val FOUR: BoardSize = BoardSize.of(4)

        /** The first seed of the container's `FixedSeedSource(0x2048)`: the fresh start game. */
        const val SEED = 0x2048L
    }
}
