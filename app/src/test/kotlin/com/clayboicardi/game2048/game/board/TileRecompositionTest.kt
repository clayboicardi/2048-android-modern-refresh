package com.clayboicardi.game2048.game.board

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tile recomposition budget (D-150; MOTION_SPEC.md §7.2; ARCHITECTURE.md §5.7): a sliding tile
 * keeps equal parameters and is skipped, so only new ids compose on the commit frame and nothing
 * recomposes on the animation frames. Counted through `LocalRecompositionCounter`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TileRecompositionTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private val host by lazy { BoardHost(compose, folder) }

    @After
    fun closeHost() {
        host.close()
    }

    /** F13-AC12, AC-PERF-1 */
    @Test
    fun noTileRecompositionDuringSlide() {
        // F13-AC12: one accepted move on FIX_MID_4 under Full, sampled every 16 ms to 400 ms.
        val mid = host.load(GoldenFixtures.FIX_MID_4)
        val film = host.load(GoldenFixtures.FIX_FILM)
        val active = host.show(mid)
        assertMoveBudget("FIX_MID_4", mid, untilMs = 400, idleAtEnd = false)

        // AC-PERF-1: FIX_FILM LEFT; every key composes at most once on F0, 0 times to t = 432.
        active.value = film
        host.settle()
        assertMoveBudget("FIX_FILM", film, untilMs = 432, idleAtEnd = true)
    }

    /** LEFT on [model], then the commit-frame and animation-frame budgets up to [untilMs]. */
    private fun assertMoveBudget(
        fixture: String,
        model: GameViewModel,
        untilMs: Long,
        idleAtEnd: Boolean,
    ) {
        val before = model.board.value.tiles.mapTo(HashSet()) { it.id }
        host.counter.reset()
        val origin = host.commit { model.onMove(Direction.LEFT) }

        val atCommit = host.counter.tiles.toMap()
        val after = model.board.value.tiles.mapTo(HashSet()) { it.id }
        assertWithMessage("$fixture: pre-existing tiles composed on the commit frame")
            .that(atCommit.keys.filter { it in before })
            .isEmpty()
        assertWithMessage("$fixture: new tiles compose at most once")
            .that(atCommit.filterValues { it > 1 })
            .isEmpty()
        assertWithMessage("$fixture: every new tile composed")
            .that(atCommit.keys)
            .containsExactlyElementsIn(after - before)
        assertWithMessage("$fixture: the board recomposed on F0")
            .that(host.counter.board)
            .isAtLeast(1)

        host.counter.reset()
        var t = 0L
        while (t < untilMs) {
            t += FRAME_MS
            host.advanceTo(origin, t)
            assertWithMessage("$fixture: tiles recomposed by t = $t")
                .that(host.counter.tiles)
                .isEmpty()
        }
        if (idleAtEnd) {
            assertWithMessage("$fixture: nothing runs at t = $untilMs")
                .that(host.probe.snapshot().isAnyRunning)
                .isFalse()
        }
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
