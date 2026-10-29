package com.clayboicardi.game2048.motion

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
 * The system animator duration scale (MOTION_SPEC.md §5.2, §9.10; D-063): Compose's
 * `MotionDurationScale` and the app's `AnimatorScaleSource` are set together (§8.1). At 0 the board
 * behaves as Off; at 2 every offset of the move doubles. The confetti case (AC-SCALE-3) is M3
 * part 2.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AnimatorScaleTest {
    private val duration = TestDurationScale()

    @get:Rule val rule = MotionRule(duration)

    /** AC-SCALE-0 */
    @Test
    fun systemScaleZeroBehavesLikeOff() {
        MotionFixtures.FILM_LEFT.verify()
        duration.scaleFactor = 0f
        val fixture = GameFixture(GoldenFixtures.FIX_FILM, records = GoldenFixtures.FIX_RECORDS)
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL, systemScale = 0f)

        h.assertEndStateOnF0("merge") { h.commitMove(Direction.LEFT) }
        assertWithMessage("merge: haptics")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.SegmentTick to 0L)
        h.assertEndStateOnF0("undo") { h.commitUndo() }
        h.assertEndStateOnF0("move") { h.commitMove(Direction.DOWN) }
        h.assertEndStateOnF0("new game") { h.commitNewGame() }
    }

    /** AC-SCALE-2 */
    @Test
    fun doubleScaleDoublesTheMoveOffsets() {
        MotionFixtures.FILM_LEFT.verify()
        duration.scaleFactor = DOUBLE
        val h =
            MotionHarness.launch(
                rule,
                GameFixture(GoldenFixtures.FIX_FILM),
                MotionPreference.FULL,
                systemScale = DOUBLE,
            )
        h.commitMove(Direction.LEFT)
        val film = FilmLeft(checkNotNull(h.committed))

        h.advanceTo(144)
        assertWithMessage("t = 144: merged alpha").that(h.tile(film.merged.id).alpha).isEqualTo(0f)
        h.advanceTo(REVEAL_MS)
        assertWithMessage("t = 160: merged alpha").that(h.tile(film.merged.id).alpha).isEqualTo(1f)
        h.advanceTo(176)
        assertWithMessage("t = 176: spawn alpha").that(h.tile(film.spawn.id).alpha).isEqualTo(0f)
        h.advanceTo(192)
        assertWithMessage("t = 192: spawn alpha")
            .that(h.tile(film.spawn.id).alpha)
            .isGreaterThan(0f)
        h.advanceTo(QUIET_MS)
        assertWithMessage("haptics")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.SegmentTick to REVEAL_MS)
    }

    private companion object {
        const val DOUBLE = 2f
        const val REVEAL_MS = 160L
        const val QUIET_MS = 864L
    }
}
