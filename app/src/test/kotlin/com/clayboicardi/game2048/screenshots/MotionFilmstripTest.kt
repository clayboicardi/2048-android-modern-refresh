package com.clayboicardi.game2048.screenshots

import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.motion.MotionHarness
import com.clayboicardi.game2048.motion.MotionRule
import com.clayboicardi.game2048.testing.captureGolden
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The golden motion filmstrip (D-147, D-204; MOTION_SPEC.md §8.3; DESIGN_SYSTEM.md §10.4 rows
 * 37–41): Pixel7, Classic light, Motion Full, `FIX_FILM` LEFT, five root frames on the 16 ms grid
 * from the origin. The golden names keep their nominal times; the frames are at 0, 48, 80, 160 and
 * 400 ms. `motion_*` goldens are exempt from ATF (D-204).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MotionFilmstripTest {
    @get:Rule val rule = MotionRule()

    /** F13-AC8, AC-GOLD-1 */
    @Test
    fun mergeAndSpawnFilmstrip() {
        MotionFixtures.FILM_LEFT.verify()
        val h =
            MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), MotionPreference.FULL)
        h.commitMove(Direction.LEFT)

        for ((frameMs, nominal) in FRAMES) {
            h.advanceTo(frameMs)
            assertWithMessage("$nominal: frame time from the origin").that(h.t()).isEqualTo(frameMs)
            rule.compose.onRoot().captureGolden("$NAME$nominal")
        }
    }

    private companion object {
        const val NAME = "motion_merge-spawn-4x4_classic-light_"
        val FRAMES =
            listOf(0L to "t000", 48L to "t040", 80L to "t080", 160L to "t160", 400L to "t400")
    }
}
