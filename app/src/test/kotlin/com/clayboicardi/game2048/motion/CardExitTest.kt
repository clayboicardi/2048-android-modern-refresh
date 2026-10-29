package com.clayboicardi.game2048.motion

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.game.overlay.KEEP_GOING_TAG
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Card exits under Full motion (MOTION_SPEC.md §3.12, §9.7; D-036, D-185, D-199): the exit runs
 * from Ft, the first frame after the tap (`MotionHarness.tapCardButtonTag`), while input reaches
 * the board at once; Keep going on a board with no move left turns the win card into the game-over
 * sequence with T0 = Ft. Times are ms from Ft on the 16 ms grid.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CardExitTest {
    @get:Rule val rule = MotionRule()

    /** AC-EXIT-1 */
    @Test
    fun keepGoingExitsWhileTheBoardTakesTheNextMove() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        val h = launch(GameFixture(MotionFixtures.FIX_WIN_PRE_4))
        h.commitMove(Direction.LEFT)
        h.advanceTo(WIN_INTERACTIVE_MS)
        val entry = checkNotNull(h.snapshot().overlay) { "t = 880: no card" }
        assertWithMessage("t = 880: the win card is interactive").that(entry.interactive).isTrue()

        val ft = h.tapCardButtonTag(KEEP_GOING_TAG)
        // §3.12: the exit animations have play time 0 on Ft. The entry's scale spring and scrim
        // tween are still rising then (they end at O + 269 = 973 and t = 980, §3.11), so the exit
        // starts from the values they had reached on the tap's frame: no lower than at t = 880,
        // and not yet moving towards 0.96 and 0.
        val start = checkNotNull(h.snapshot().overlay) { "Ft: no card" }
        assertWithMessage("Ft: card alpha").that(start.cardAlpha).isWithin(Tol.ALPHA).of(1f)
        assertWithMessage("Ft: card scale")
            .that(start.cardScale)
            .isAtLeast(entry.cardScale - Tol.SCALE)
        assertWithMessage("Ft: card scale").that(start.cardScale).isAtMost(1f + Tol.SCALE)
        assertWithMessage("Ft: scrim").that(start.scrimAlpha).isAtLeast(entry.scrimAlpha)
        assertWithMessage("Ft: scrim")
            .that(start.scrimAlpha)
            .isAtMost(GameMotion.Win.SCRIM_ALPHA + Tol.ALPHA)
        val moveId = h.vm.board.value.moveId
        h.commitMove(Direction.RIGHT)

        assertWithMessage("the move committed on the frame after the tap: applied")
            .that(h.vm.board.value.moveId)
            .isEqualTo(moveId + 1)
        assertWithMessage("the move committed on the frame after the tap: event")
            .that(checkNotNull(h.committed).event)
            .isInstanceOf(BoardEvent.Moved::class.java)
        h.advanceTo(EXIT_CARD_DONE_MS, ft)
        val exiting = checkNotNull(h.snapshot().overlay) { "Ft + 128: the card left too early" }
        assertWithMessage("Ft + 128: card scale (slide settles in 114 ms)")
            .that(exiting.cardScale)
            .isWithin(Tol.SCALE)
            .of(GameMotion.Card.EXIT_SCALE)
        assertWithMessage("Ft + 128: card alpha").that(exiting.cardAlpha).isWithin(Tol.ALPHA).of(0f)
        h.advanceTo(EXIT_SCRIM_LAST_MS, ft)
        val last = checkNotNull(h.snapshot().overlay) { "Ft + 192: the card left too early" }
        assertWithMessage("Ft + 192: scrim").that(last.scrimAlpha).isGreaterThan(0f)
        h.advanceTo(EXIT_DONE_MS, ft)
        val after = h.snapshot()
        assertWithMessage("Ft + 208: the overlay").that(after.overlay).isNull()
        assertWithMessage("Ft + 208: the scrim").that(after.scrimReading ?: 0f).isEqualTo(0f)
    }

    /** AC-EXIT-2 */
    @Test
    fun keepGoingWithNoMoveLeftTurnsIntoGameOver() {
        MotionFixtures.verifyWonNoMoves()
        val h = launch(GameFixture(MotionFixtures.FIX_WON_NO_MOVES_4))
        val restored = checkNotNull(h.snapshot().overlay) { "no restored win card" }
        assertWithMessage("the restored card").that(restored.kind).isEqualTo(ProbeOverlayKind.WIN)
        assertWithMessage("the restored card: interactive").that(restored.interactive).isTrue()
        h.filmstrip("keep_going_to_over", FilmRegion.ROOT, STRIP_FRAMES)

        val ft = h.tapCardButtonTag(KEEP_GOING_TAG)

        assertWithMessage("Ft: haptics")
            .that(h.hapticsFrom(ft))
            .containsExactly(HapticFeedbackType.Reject to 0L)
        assertScrimRises(h, ft)
        h.advanceTo(OVER_CARD_LAST_BEFORE_MS, ft)
        assertWithMessage("Ft + 496: overlay kind")
            .that(h.snapshot().overlay?.kind)
            .isNotEqualTo(ProbeOverlayKind.OVER)
        h.advanceTo(OVER_CARD_AT_MS, ft)
        assertWithMessage("Ft + 512: overlay kind")
            .that(h.snapshot().overlay?.kind)
            .isEqualTo(ProbeOverlayKind.OVER)
        h.advanceTo(OVER_LOCKED_MS, ft)
        assertWithMessage("Ft + 688: interactive")
            .that(checkNotNull(h.snapshot().overlay).interactive)
            .isFalse()
        h.advanceTo(OVER_INTERACTIVE_MS, ft)
        assertWithMessage("Ft + 704: interactive")
            .that(checkNotNull(h.snapshot().overlay).interactive)
            .isTrue()
        h.writeFilmstrip()
        h.advanceTo(QUIET_MS, ft)
        assertWithMessage("haptics: one Reject at Ft, the tap itself silent")
            .that(h.hapticsFrom(ft))
            .containsExactly(HapticFeedbackType.Reject to 0L)
    }

    /**
     * Keep going into `OVER` (§3.12): the scrim does not fade out; it goes from the win's 0.60
     * towards 0.78 with `tween(300, Standard)` from Ft and is 0.78 on Ft + 304.
     */
    private fun assertScrimRises(h: MotionHarness, ft: Long) {
        val start = checkNotNull(h.snapshot().scrimReading) { "Ft: no scrim reading" }
        assertWithMessage("Ft: scrim")
            .that(start)
            .isWithin(Tol.ALPHA)
            .of(GameMotion.Win.SCRIM_ALPHA)
        var previous = start
        while (h.now() < ft + SCRIM_DONE_MS) {
            h.advanceFrames(1)
            val scrim = h.snapshot().scrimReading ?: continue
            assertWithMessage("Ft + ${h.now() - ft}: scrim never falls")
                .that(scrim)
                .isAtLeast(previous - Tol.ALPHA)
            previous = scrim
        }
        assertWithMessage("Ft + 304: scrim")
            .that(checkNotNull(h.snapshot().scrimReading) { "Ft + 304: no scrim reading" })
            .isWithin(Tol.ALPHA)
            .of(GameMotion.GameOver.SCRIM_ALPHA)
    }

    private fun launch(fixture: GameFixture): MotionHarness =
        MotionHarness.launch(rule, fixture, MotionPreference.FULL)

    private companion object {
        const val EXIT_CARD_DONE_MS = 128L
        const val EXIT_SCRIM_LAST_MS = 192L
        const val EXIT_DONE_MS = 208L
        const val SCRIM_DONE_MS = 304L
        const val OVER_CARD_LAST_BEFORE_MS = 496L
        const val OVER_CARD_AT_MS = 512L
        const val OVER_LOCKED_MS = 688L
        const val OVER_INTERACTIVE_MS = 704L
        const val QUIET_MS = 1200L
        val STRIP_FRAMES = listOf(0L, 96, 208, 512, 704)
    }
}
