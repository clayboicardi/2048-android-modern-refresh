package com.clayboicardi.game2048.motion

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.CONTROLS_TAG
import com.clayboicardi.game2048.game.STATUS_LINE_TAG
import com.clayboicardi.game2048.game.board.BoardPresenter
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.header.HEADER_TAG
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.game.overlay.HINT_TAG
import com.clayboicardi.game2048.ui.components.HINT_GOT_IT_TAG
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.ceil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Layout changes in a live composition (MOTION_SPEC.md §3.16, §9.9; DESIGN_SYSTEM.md §5.3, §5.8;
 * D-047, D-095, D-188): bounds animate within one arrangement under Full, and an arrangement switch
 * composes the new arrangement's board and header anew from the settled values, so nothing is
 * replayed. Robolectric's window insets are zero, so `w411dp-h450dp` resolves to STACKED with the
 * COMPACT header and a board of about 255 dp, and to SCROLL with one of about 295 dp while the
 * TalkBack pad is shown: `resolveGameLayout`'s boards over the measured chrome, which the tests
 * compare with, and within 4 dp of the 258 and 298 dp that DESIGN_SYSTEM.md §5.3's nominal chrome
 * gives (D-251).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutMotionTest {
    @get:Rule val rule = MotionRule()

    /** AC-LAYOUT-1 */
    @Config(qualifiers = "w411dp-h560dp")
    @Test
    fun hintExitAnimatesBoardBounds() {
        // 411×560 (zero insets): STACKED with the COMPACT header with and without the hint, a
        // height-limited board that grows when the hint leaves.
        val h =
            MotionHarness.launch(
                rule,
                GameFixture(GoldenFixtures.FIX_FRESH_4),
                MotionPreference.FULL,
                onboardingDone = false,
            )
        assertWithMessage("the hint is shown").that(h.exists(HINT_TAG)).isTrue()
        val before = h.boardHeightPx()
        h.manualFilmstrip("hint_exit_bounds", FilmRegion.ROOT)

        tapGotIt(h)
        // t = 0: the bounds animation's first frame (F1, play time 0), one after its composition.
        val origin = h.now()
        h.captureFilmFrame("t=000")
        assertWithMessage("t = 0: the board starts at its bounds with the hint")
            .that(h.boardHeightPx())
            .isEqualTo(before)

        val heights = sortedMapOf(0L to before)
        while (h.now() - origin < HINT_FINAL_MS) {
            h.advanceFrames(1)
            val t = h.now() - origin
            heights[t] = h.boardHeightPx()
            if (t in HINT_STRIP_MS) h.captureFilmFrame("t=${t.toString().padStart(3, '0')}")
        }
        h.writeFilmstrip()
        assertWithMessage("t = 416: nothing runs").that(h.snapshot().isAnyRunning).isFalse()
        h.advanceFrames(SETTLED_CHECK_FRAMES)
        val final = h.boardHeightPx()
        assertWithMessage("t = 416: final ($heights)").that(heights[HINT_FINAL_MS]).isEqualTo(final)
        assertWithMessage("the board grew").that(final).isGreaterThan(before)

        // The bounds follow overlaySpatial (0.8 / 380): they rise on every frame until they are
        // within the spring's 1.52 % overshoot of the final size (its first peak is at 269 ms),
        // and then stay within it; they are final at 416.
        val band = ceil(OVERSHOOT * (final - before)).toInt() + 1
        var last = before
        for ((t, height) in heights) {
            if (t == 0L) continue
            if (last < final - band) {
                assertWithMessage("t = $t: rising ($heights)").that(height).isGreaterThan(last)
            } else {
                assertWithMessage("t = $t: within the overshoot ($heights)")
                    .that(abs(height - final))
                    .isAtMost(band)
            }
            last = height
        }
        assertWithMessage("t = $HINT_RISING_MS: still rising ($heights)")
            .that(checkNotNull(heights[HINT_RISING_MS]))
            .isLessThan(final - band)
        assertWithMessage("the hint is gone").that(h.exists(HINT_TAG)).isFalse()
    }

    /** AC-LAYOUT-1 */
    @Config(qualifiers = "w411dp-h560dp")
    @Test
    fun hintExitSnapsBoardBoundsUnderReduced() {
        assertHintExitSnaps(MotionPreference.REDUCED)
    }

    /** AC-LAYOUT-1 */
    @Config(qualifiers = "w411dp-h560dp")
    @Test
    fun hintExitSnapsBoardBoundsUnderOff() {
        assertHintExitSnaps(MotionPreference.OFF)
    }

    /** F18-AC13 AC-LAYOUT-2 */
    @Config(qualifiers = "w411dp-h450dp")
    @Test
    fun arrangementSwitchRendersSettledBoard() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), FULL)
        val stacked = stackedBoard(h)
        assertWithMessage("the STACKED board is the nominal 258 dp within the chips' rounding")
            .that(stacked)
            .isWithin(NOMINAL_SLACK_DP)
            .of(STACKED_BOARD_DP)
        assertBoard(h, stacked, "before the move")

        val origin = h.commitMove(Direction.LEFT)
        val moveId = h.vm.board.value.moveId
        val log = h.peekGame().state.moveLog
        while (h.t() < SWITCH_AT_MS) {
            h.advanceFrames(1)
            assertBoard(h, stacked, "t = ${h.t()}, before the switch")
        }

        h.setTouchExploration(true)
        h.advanceFrames(1)
        val scroll = scrollBoard(h)
        assertWithMessage("the SCROLL board is the nominal 298 dp within the chips' rounding")
            .that(scroll)
            .isWithin(NOMINAL_SLACK_DP)
            .of(SCROLL_BOARD_DP)
        assertBoard(h, scroll, "the first frame after the switch (t = ${h.t()})")
        assertSettled(h, h.snapshot(), "the first SCROLL frame")
        assertWithMessage("the first SCROLL frame: nothing runs")
            .that(h.snapshot().isAnyRunning)
            .isFalse()
        while (h.t() < WATCH_MS) {
            h.advanceFrames(1)
            assertBoard(h, scroll, "t = ${h.t()}, SCROLL")
            assertWithMessage("t = ${h.t()}: floats").that(h.snapshot().floats).isEmpty()
        }
        assertWithMessage("the move's id").that(h.vm.board.value.moveId).isEqualTo(moveId)
        assertWithMessage("the move log").that(h.peekGame().state.moveLog).isEqualTo(log)
        assertWithMessage("the MoveTimeline origin").that(h.probe.originMs).isEqualTo(origin)
        assertWithMessage("haptics through t = 1000")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.SegmentTick to SEGMENT_TICK_MS)

        assertWithMessage("nothing runs before switching back")
            .that(h.snapshot().isAnyRunning)
            .isFalse()
        h.setTouchExploration(false)
        h.advanceFrames(1)
        assertBoard(h, stacked, "the first STACKED frame")
        assertSettled(h, h.snapshot(), "the first STACKED frame")
    }

    /** F18-AC14 AC-LAYOUT-3 */
    @Config(qualifiers = "w411dp-h450dp")
    @Test
    fun arrangementSwitchAfterPresentedMoveReplaysNothing() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), FULL)
        val stacked = stackedBoard(h)
        val origin = h.commitMove(Direction.LEFT)
        val move = checkNotNull(h.committed)
        h.advanceTo(WATCH_MS)
        assertWithMessage("settled: nothing runs").that(h.snapshot().isAnyRunning).isFalse()
        assertWithMessage("settled: vm.board")
            .that(h.vm.board.value)
            .isEqualTo(BoardPresenter.settled(move))
        assertWithMessage("settled: lastDelta").that(h.vm.header.value.lastDelta).isEqualTo(0)
        val tick = listOf(HapticFeedbackType.SegmentTick to SEGMENT_TICK_MS)
        assertWithMessage("settled: haptics").that(h.hapticsFrom()).isEqualTo(tick)
        val board = h.vm.board.value
        val header = h.vm.header.value
        val log = h.peekGame().state.moveLog

        for ((on, name) in listOf(true to "SCROLL", false to "STACKED")) {
            h.setTouchExploration(on)
            h.advanceFrames(1)
            val side = if (on) scrollBoard(h) else stacked
            val switchedAt = h.now()
            while (h.now() - switchedAt <= WATCH_MS) {
                val at = "$name +${h.now() - switchedAt}"
                assertBoard(h, side, at)
                val frame = h.snapshot()
                assertSettled(h, frame, at)
                assertWithMessage("$at: nothing runs").that(frame.isAnyRunning).isFalse()
                assertWithMessage("$at: origin").that(h.probe.originMs).isEqualTo(origin)
                assertWithMessage("$at: haptics").that(h.hapticsFrom(origin)).isEqualTo(tick)
                h.advanceFrames(1)
            }
            assertWithMessage("$name: vm.board").that(h.vm.board.value).isEqualTo(board)
            assertWithMessage("$name: vm.header").that(h.vm.header.value).isEqualTo(header)
            assertWithMessage("$name: move log").that(h.peekGame().state.moveLog).isEqualTo(log)
        }
    }

    /** AC-LAYOUT-1 under Reduced and Off: the board is at its final bounds on F0. */
    private fun assertHintExitSnaps(motion: MotionPreference) {
        val h =
            MotionHarness.launch(
                rule,
                GameFixture(GoldenFixtures.FIX_FRESH_4),
                motion,
                onboardingDone = false,
            )
        assertWithMessage("$motion: the hint is shown").that(h.exists(HINT_TAG)).isTrue()
        val before = h.boardHeightPx()
        tapGotIt(h, toOrigin = false)
        val f0 = h.boardHeightPx()
        assertWithMessage("$motion F0: the board is at its final bounds")
            .that(f0)
            .isGreaterThan(before)
        h.advanceFrames(SETTLED_CHECK_FRAMES)
        assertWithMessage("$motion: unchanged after F0").that(h.boardHeightPx()).isEqualTo(f0)
        assertWithMessage("$motion: the hint is gone").that(h.exists(HINT_TAG)).isFalse()
    }

    /**
     * Taps "Got it" and advances past the frame that composes the hint's dismissal (F0) to the next
     * one, F1, where the bounds animation has play time 0 (the origin of MOTION_SPEC.md §0.1). The
     * main looper's pending work and the snapshot apply notifications run first, so the dismissal
     * is collected before F0. Under Reduced and Off the board is final on F0 already.
     */
    private fun tapGotIt(h: MotionHarness, toOrigin: Boolean = true) {
        h.rule.onNodeWithTag(HINT_GOT_IT_TAG).performClick()
        ShadowLooper.idleMainLooper()
        Snapshot.sendApplyNotifications()
        h.advanceFrames(if (toOrigin) 2 else 1)
    }

    /**
     * The STACKED board of this window (DESIGN_SYSTEM.md §5.3, COMPACT header) from the chrome
     * drawn now: `min(w0, availH − (header + 8 + 8 + controls))`, which the measured chrome must
     * give; the nominal chrome (64 dp header, 80 dp bar) gives 258 dp.
     */
    private fun stackedBoard(h: MotionHarness): Float {
        val chrome = heightDp(h, HEADER_TAG) + 2 * GAP + heightDp(h, CONTROLS_TAG)
        return minOf(W0, AVAIL_H - chrome)
    }

    /**
     * The SCROLL board of this window with the pad (DESIGN_SYSTEM.md §5.3): `availH − (header + 8)
     * − (8 + status line)` from the chrome drawn now; the nominal chrome gives 298 dp.
     */
    private fun scrollBoard(h: MotionHarness): Float =
        AVAIL_H - (heightDp(h, HEADER_TAG) + GAP) - (GAP + heightDp(h, STATUS_LINE_TAG))

    private fun heightDp(h: MotionHarness, tag: String): Float =
        h.rule.onNodeWithTag(tag).fetchSemanticsNode().size.height / h.rule.density.density

    /** The board node is [dp] dp square on this frame. */
    private fun assertBoard(h: MotionHarness, dp: Float, at: String) {
        assertWithMessage("$at: board side").that(h.boardSideDp()).isWithin(PX_DP).of(dp)
    }

    /**
     * [frame] draws the settled static board of the committed state: every tile `Static` at its
     * engine cell with scale 1 and alpha 1, none outgoing, no "+N" float, and the displayed score
     * equal to the true score.
     */
    private fun assertSettled(h: MotionHarness, frame: MotionSnapshot, at: String) {
        val cells = h.vm.board.value.cells
        val n = h.vm.board.value.size.n
        val drawn = frame.tiles
        assertWithMessage("$at: tiles drawn").that(drawn).hasSize(cells.count { it != 0 })
        for (tile in drawn) {
            assertWithMessage("$at: tile ${tile.id} kind")
                .that(tile.kind)
                .isEqualTo(TileKind.Static)
            assertWithMessage("$at: tile ${tile.id} outgoing").that(tile.outgoing).isFalse()
            assertWithMessage("$at: tile ${tile.id} scale")
                .that(tile.scale)
                .isWithin(Tol.SCALE)
                .of(1f)
            assertWithMessage("$at: tile ${tile.id} alpha")
                .that(tile.alpha)
                .isWithin(Tol.ALPHA)
                .of(1f)
            val index = Math.round(tile.y) * n + Math.round(tile.x)
            assertWithMessage("$at: tile ${tile.id} at a whole cell")
                .that(abs(tile.x - Math.round(tile.x)) + abs(tile.y - Math.round(tile.y)))
                .isLessThan(Tol.POSITION)
            assertWithMessage("$at: tile ${tile.id} on its engine cell")
                .that(cells[index])
                .isEqualTo(tile.exp)
        }
        assertWithMessage("$at: floats").that(frame.floats).isEmpty()
        assertWithMessage("$at: displayed score")
            .that(frame.header?.displayedScore)
            .isEqualTo(h.vm.header.value.score)
    }

    private companion object {
        val FULL = MotionPreference.FULL
        const val STACKED_BOARD_DP = 258f
        const val SCROLL_BOARD_DP = 298f

        /**
         * The measured COMPACT header may differ from DESIGN_SYSTEM.md §5.3's nominal 64 dp by the
         * chips' rounding and by the bundled numerals' score line box (DESIGN_SYSTEM.md §7.2),
         * 1.252 em against the M3 numerals' 1.172 em: about 2.3 dp more at 28 sp (D-251).
         */
        const val NOMINAL_SLACK_DP = 4f

        /**
         * `w411dp-h450dp` with zero insets: `availW` 379 (= `w0`), `availH` 418 (16 dp margins).
         */
        const val W0 = 379f
        const val AVAIL_H = 418f

        /** The COMPACT header's gaps (`Space8`). */
        const val GAP = 8f
        const val SWITCH_AT_MS = 48L
        const val SEGMENT_TICK_MS = 80L
        const val WATCH_MS = 1_000L
        const val HINT_FINAL_MS = 416L

        /** The bounds still rise well before overlaySpatial's first peak (269 ms; §1.1). */
        const val HINT_RISING_MS = 160L

        /** overlaySpatial's analytic first overshoot (MOTION_SPEC.md §1.1). */
        const val OVERSHOOT = 0.0152f
        val HINT_STRIP_MS = setOf(96L, 192L, 304L, 416L)
        const val SETTLED_CHECK_FRAMES = 5

        /** Half a pixel at density 1, in dp. */
        const val PX_DP = 0.5f
    }
}
