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
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Off column of MOTION_SPEC.md §5.1 (§2.3 T5, §9.10): the screen is in its end state on F0 and
 * nothing runs, while haptics still follow §6 at the origin, the win's two `Confirm`s 150 ms of
 * real time apart. The board cases are M3 part 1; the win and game-over cases, with their cards, M3
 * part 2.
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

    /** AC-OFF-1, AC-OFF-2 */
    @Test
    fun winIsInItsEndStateOnF0() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_WIN_PRE_4), OFF)
        h.filmstrip("off_win", FilmRegion.ROOT, listOf(0L, 16L), withF0 = true)
        var origin = 0L
        h.assertEndStateOnF0("win", quietUntilMs = QUIET_MS) {
            origin = h.commitMove(Direction.LEFT)
        }
        h.writeFilmstrip()
        assertCardEndOnF0(h, ProbeOverlayKind.WIN, GameMotion.Win.SCRIM_ALPHA)
        assertWithMessage("win: haptics at +0 and +150 ms real time")
            .that(h.hapticsFrom(origin))
            .containsExactly(CONFIRM to 0L, CONFIRM to WIN_SECOND_REAL_MS)
            .inOrder()
    }

    /** AC-OFF-1, AC-OFF-2 */
    @Test
    fun gameOverIsInItsEndStateOnF0() {
        MotionFixtures.OVER_PRE_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_OVER_PRE_4), OFF)
        h.filmstrip("off_over", FilmRegion.ROOT, listOf(0L, 16L), withF0 = true)
        var origin = 0L
        h.assertEndStateOnF0("game over", quietUntilMs = QUIET_MS) {
            origin = h.commitMove(Direction.LEFT)
        }
        h.writeFilmstrip()
        assertCardEndOnF0(h, ProbeOverlayKind.OVER, GameMotion.GameOver.SCRIM_ALPHA)
        val tiles = checkNotNull(h.f0).tiles.filter { !it.outgoing }
        for (tile in tiles) {
            assertWithMessage("F0: tile ${tile.id} scale")
                .that(tile.scale)
                .isWithin(Tol.SCALE)
                .of(GameMotion.GameOver.DEFLATE_SCALE)
        }
        assertWithMessage("game over: haptics")
            .that(h.hapticsFrom(origin))
            .containsExactly(HapticFeedbackType.Reject to 0L)
    }

    /**
     * T5 (§3.11, §3.13): on the commit's F0 the card is composed in its end state over its scrim,
     * with interactive buttons and no ring or confetti.
     */
    private fun assertCardEndOnF0(h: MotionHarness, kind: ProbeOverlayKind, scrim: Float) {
        val f0 = checkNotNull(h.f0)
        val card = checkNotNull(f0.overlay) { "F0: no card" }
        assertWithMessage("F0: overlay kind").that(card.kind).isEqualTo(kind)
        assertWithMessage("F0: interactive").that(card.interactive).isTrue()
        assertWithMessage("F0: card alpha").that(card.cardAlpha).isEqualTo(1f)
        assertWithMessage("F0: card scale").that(card.cardScale).isEqualTo(1f)
        assertWithMessage("F0: scrim").that(card.scrimAlpha).isWithin(Tol.ALPHA).of(scrim)
        assertWithMessage("F0: button alphas").that(card.buttonAlphas.toSet()).containsExactly(1f)
        if (kind == ProbeOverlayKind.WIN) {
            assertWithMessage("F0: badge morph").that(card.badgeMorph).isEqualTo(1f)
            assertWithMessage("F0: badge rotation")
                .that(card.badgeRotationDeg)
                .isEqualTo(GameMotion.Win.BADGE_ROTATION_DEG)
        }
        assertWithMessage("F0: ring").that(f0.effects.ring).isNull()
        assertWithMessage("F0: confetti").that(f0.effects.confettiAlive).isEqualTo(0)
    }

    private companion object {
        val OFF = MotionPreference.OFF
        const val SETTLE_FRAMES = 20

        /** The win's second `Confirm`, +150 ms of real time: frame 160 (§6.1). */
        const val WIN_SECOND_REAL_MS = 160L

        /** Frames past it are checked before the static render recreates the content. */
        const val QUIET_MS = 176L
        val SEGMENT = HapticFeedbackType.SegmentTick
        val CONFIRM = HapticFeedbackType.Confirm
        val THRESHOLD = HapticFeedbackType.GestureThresholdActivate
    }
}
