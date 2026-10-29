package com.clayboicardi.game2048.motion

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.MoveHaptic
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Off column of MOTION_SPEC.md §5.1 for the board (§2.3 T5, §9.10): the screen is in its end
 * state on F0 and nothing runs, while haptics still follow §6 at the origin. The win and game-over
 * cases are M3 part 2.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MotionOffTest {
    @get:Rule val rule = MotionRule()

    /** AC-OFF-1 */
    @Test
    fun moveIsInItsEndStateOnF0() {
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_SLIDE_4), OFF)
        h.assertEndStateOnF0("move") { h.commitMove(Direction.RIGHT) }
    }

    /** AC-OFF-1, AC-OFF-2 */
    @Test
    fun mergeIsInItsEndStateOnF0() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), OFF)
        h.filmstrip("off_move", FilmRegion.ROOT, listOf(0L, 16L), withF0 = true)
        h.assertEndStateOnF0("merge") { h.commitMove(Direction.LEFT) }
        h.writeFilmstrip()
        assertWithMessage("merge: haptics")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.SegmentTick to 0L)
    }

    /** AC-OFF-1 */
    @Test
    fun newGameIsInItsEndStateOnF0() {
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_MID_4), OFF)
        h.assertEndStateOnF0("new game") { h.commitNewGame() }
    }

    /** AC-OFF-1 */
    @Test
    fun undoIsInItsEndStateOnF0() {
        val fixture = GameFixture(GoldenFixtures.FIX_FILM, records = GoldenFixtures.FIX_RECORDS)
        val h = MotionHarness.launch(rule, fixture, OFF)
        h.commitMove(Direction.LEFT)
        h.advanceFrames(2)
        h.assertEndStateOnF0("undo") { h.commitUndo() }
    }

    /** AC-OFF-2 */
    @Test
    fun moveHapticsFireOnTheOrigin() {
        val cases =
            listOf(
                Triple(MotionFixtures.pairGame(5, 1, 0, 0), MoveHaptic.MERGE, SEGMENT),
                Triple(MotionFixtures.pairGame(6, 8, 5000, 100_000), MoveHaptic.BIG_MERGE, CONFIRM),
                Triple(MotionFixtures.newBestGame(7), MoveHaptic.NEW_BEST, THRESHOLD),
            )
        val fixture =
            GameFixture(
                MotionFixtures.FIX_SLIDE_4,
                saved = cases.map { it.first },
                records = Records(sizes = mapOf("7" to SizeRecord(bestScore = 100))),
            )
        val h = MotionHarness.launch(rule, fixture, OFF)

        for ((game, haptic, type) in cases) {
            h.commitSizeSwitch(game.state.size.n)
            h.advanceFrames(2)
            val before = h.haptics.events.size
            val origin = h.commitMove(Direction.LEFT)
            assertWithMessage("${game.state.size}: the resolved haptic")
                .that((checkNotNull(h.committed).event as BoardEvent.Moved).haptic)
                .isEqualTo(haptic)
            h.advanceFrames(SETTLE_FRAMES)
            assertWithMessage("$haptic: haptics from the origin")
                .that(h.haptics.events.drop(before).map { it.type to it.atMs - origin })
                .containsExactly(type to 0L)
        }
    }

    private companion object {
        val OFF = MotionPreference.OFF
        const val SETTLE_FRAMES = 20
        val SEGMENT = HapticFeedbackType.SegmentTick
        val CONFIRM = HapticFeedbackType.Confirm
        val THRESHOLD = HapticFeedbackType.GestureThresholdActivate
    }
}
