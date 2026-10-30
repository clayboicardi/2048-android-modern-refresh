package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The time origin of MOTION_SPEC.md §0.1 (D-183): F0 composes the new `BoardUi`, and F1, the next
 * frame, is the origin where every animation of the change has play time 0.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TimelineOriginTest {
    @get:Rule val rule = MotionRule()

    /**
     * D-183: the origin is F1 (MOTION_SPEC.md §0.1, §8.5): two frames after the commit, the sliding
     * 4 of `FIX_FILM` LEFT is still at its `from` cell, and one frame later it has covered the
     * slide spring's first 16 ms (reference 0.1255 ± 0.002).
     */
    @Test
    fun originIsTheFirstAnimationFrame() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), FULL)
        val commitFrame = h.now()

        val origin = h.commitMove(Direction.LEFT)

        assertWithMessage("the origin is F1 = commit + 2 frames")
            .that(origin)
            .isEqualTo(commitFrame + 2 * FRAME_MS)
        val moved = FilmLeft(checkNotNull(h.committed)).moved
        assertWithMessage("t = 0: the 4 is at its from cell")
            .that(h.tile(moved.id).x)
            .isEqualTo(moved.fromCol.toFloat())

        h.advanceTo(FIRST_FRAME_MS)
        val travelled = moved.fromCol - h.tile(moved.id).x
        val progress = travelled / (moved.fromCol - moved.col)
        assertWithMessage("t = 16: slide progress (reference 0.1255)")
            .that(progress)
            .isWithin(PROGRESS_TOLERANCE)
            .of(Curves.value(GameMotion.slide(), 0f, 1f, FIRST_FRAME_MS))
    }

    private companion object {
        val FULL = MotionPreference.FULL
        const val FIRST_FRAME_MS = 16L
        const val PROGRESS_TOLERANCE = 0.002f
    }
}
