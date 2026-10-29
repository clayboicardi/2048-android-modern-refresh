package com.clayboicardi.game2048.motion

import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.GameKey
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Predictive back on a sub-screen (MOTION_SPEC.md §3.19, §9.9 AC-BACK-1; D-094): the gesture is
 * dispatched to the host activity's `OnBackPressedDispatcher` (`dispatchOnBackStarted`,
 * `dispatchOnBackProgressed`, `dispatchOnBackCancelled`, `onBackPressed`, through
 * `MotionHarness.backStarted` and its siblings) from the left edge, which reaches Navigation 3's
 * `NavDisplay` on Robolectric (checked at first use, MOTION_SPEC.md §9.9), and the outgoing
 * Settings root is measured through its layout coordinates: its scale is its drawn width over its
 * width, its translation the shift of its centre. The `predictive_back` strip has one frame per
 * gesture step.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PredictiveBackTest {
    @get:Rule val rule = MotionRule()

    /** AC-BACK-1 */
    @Test
    fun gestureSeeksThePopThenCancelsAndCommits() {
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_MID_4), FULL)
        h.pushFromMenu(SettingsKey)
        h.advanceFrames(SETTLE_FRAMES)
        h.manualFilmstrip("predictive_back", FilmRegion.ROOT)
        h.captureFilmFrame("settings")

        h.backStarted(event(0f))
        h.captureFilmFrame("p=0.00")
        for (progress in listOf(QUARTER, HALF, THREE_QUARTERS)) {
            h.backProgressed(event(progress))
            h.captureFilmFrame("p=$progress")
            if (progress == HALF) assertHalfway(h)
        }

        h.backCancelled()
        h.captureFilmFrame("cancel")
        val restored = outgoing(h)
        assertWithMessage("after cancel: Settings scale")
            .that(restored.scale)
            .isWithin(0.001f)
            .of(1f)
        assertWithMessage("after cancel: Settings shift (dp)")
            .that(restored.shiftDp)
            .isWithin(Tol.DP)
            .of(0f)
        assertWithMessage("after cancel: Settings composed")
            .that(h.exists(screenTag(SettingsKey)))
            .isTrue()

        h.backStarted(event(0f))
        h.backProgressed(event(HALF))
        h.backCommitted()
        h.captureFilmFrame("commit")
        assertWithMessage("after commit: Settings composed")
            .that(h.exists(screenTag(SettingsKey)))
            .isFalse()
        assertWithMessage("after commit: Game composed").that(h.exists(BOARD_TAG)).isTrue()
        assertWithMessage("after commit: the Game destination")
            .that(h.exists(screenTag(GameKey)))
            .isTrue()
        h.writeFilmstrip()
    }

    /** At progress 0.5 the outgoing screen is at scale 0.95 and +15 dp (linear 300 ms tweens). */
    private fun assertHalfway(h: MotionHarness) {
        val half = outgoing(h)
        val scale = 1 - (1 - GameMotion.Nav.PREDICTIVE_SCALE) * HALF
        assertWithMessage("progress 0.5: outgoing scale")
            .that(half.scale)
            .isWithin(SCALE_TOL)
            .of(scale)
        assertWithMessage("progress 0.5: outgoing translation (dp)")
            .that(half.shiftDp)
            .isWithin(SHIFT_TOL_DP)
            .of(GameMotion.Nav.SHIFT_DP * HALF)
    }

    /**
     * The Settings root as drawn: scale (drawn width / width) and the shift of its centre in dp.
     */
    private fun outgoing(h: MotionHarness): Drawn {
        val node = h.rule.onNodeWithTag(screenTag(SettingsKey)).fetchSemanticsNode()
        val coordinates = node.layoutInfo.coordinates
        val width = coordinates.size.width.toFloat()
        val left = coordinates.localToRoot(Offset.Zero).x
        val right = coordinates.localToRoot(Offset(width, 0f)).x
        val shiftPx = (left + right) / 2 - width / 2
        return Drawn(scale = (right - left) / width, shiftDp = shiftPx / h.rule.density.density)
    }

    /** A left-edge back event at [progress], at mid height. */
    private fun event(progress: Float): BackEventCompat =
        BackEventCompat(TOUCH_X, TOUCH_Y, progress, BackEventCompat.EDGE_LEFT)

    private class Drawn(val scale: Float, val shiftDp: Float)

    private companion object {
        val FULL = MotionPreference.FULL
        const val QUARTER = 0.25f
        const val HALF = 0.5f
        const val THREE_QUARTERS = 0.75f
        const val SCALE_TOL = 0.005f
        const val SHIFT_TOL_DP = 0.5f
        const val SETTLE_FRAMES = 40
        const val TOUCH_X = 0f
        const val TOUCH_Y = 800f
    }
}
