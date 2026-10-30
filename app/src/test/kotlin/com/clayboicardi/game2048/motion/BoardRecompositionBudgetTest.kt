package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The board and screen recomposition budget of MOTION_SPEC.md §7.2 (D-150; ARCHITECTURE.md §5.7),
 * counted through `LocalRecompositionCounter`: `BoardView` recomposes on F0 and once when idle, and
 * `GameScreen`, which reads no per-move state, not at all.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BoardRecompositionBudgetTest {
    @get:Rule val rule = MotionRule()

    /** AC-PERF-2 */
    @Test
    fun boardAndScreenStayWithinBudget() {
        MotionFixtures.FILM_LEFT.verify()
        val h =
            MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), MotionPreference.FULL)
        h.recompositions.reset()

        h.commitMove(Direction.LEFT)
        h.advanceTo(BUDGET_END_MS)

        assertWithMessage("F0..t = 448: BoardView recompositions")
            .that(h.recompositions.board)
            .isAtMost(2)
        assertWithMessage("F0..t = 448: GameScreen recompositions")
            .that(h.recompositions.screen)
            .isEqualTo(0)
    }

    private companion object {
        const val BUDGET_END_MS = 448L
    }
}
