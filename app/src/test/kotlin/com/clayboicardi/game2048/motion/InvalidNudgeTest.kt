package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.keyframes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.motion.ProbeBoard
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The invalid-move nudge (MOTION_SPEC.md §3.7, §9.4): a board-level `NUDGE` spring along the swipe
 * from rest with 330 dp/s (Full), the outline pulse (Reduced), the 400 ms `Reject` limit and no
 * feedback for blocked states. Times are from the nudge's first frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class InvalidNudgeTest {
    @get:Rule val rule = MotionRule()

    /** AC-NUDGE-1, AC-NUDGE-2, AC-NUDGE-3 */
    @Test
    fun nudgeFollowsTheSwipeAxisAndSettles() {
        assertInvalid(MotionFixtures.FIX_INVALID_4, MotionFixtures.INVALID_REJECTED)
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_INVALID_4), FULL)
        val board = h.vm.board.value

        h.filmstrip("nudge_left", FilmRegion.ROOT, STRIP_FRAMES)
        h.invalidMove(Direction.LEFT)
        // Reference x: -4.01 at 16, -5.22 at 32, +1.93 at 112, -0.71 at 192.
        followNudge(h, Direction.LEFT, listOf(16L, 32L, 112L, 192L))
        h.writeFilmstrip()
        assertWithMessage("LEFT: the board did not change").that(h.vm.board.value).isEqualTo(board)

        h.filmstrip("nudge_up", FilmRegion.ROOT, STRIP_FRAMES)
        h.invalidMove(Direction.UP)
        // Reference y: -5.22 at 32.
        followNudge(h, Direction.UP, listOf(32L))
        h.writeFilmstrip()
        assertWithMessage("UP: the board did not change").that(h.vm.board.value).isEqualTo(board)
        assertWithMessage("the move count")
            .that(h.peekGame().state.moveCount)
            .isEqualTo(MotionFixtures.FIX_INVALID_4.state.moveCount)
    }

    /** AC-NUDGE-4 */
    @Test
    fun rejectIsRateLimitedInRealTime() {
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_INVALID_4), FULL)

        val first = h.now()
        h.invalidMove(Direction.LEFT)
        while (h.now() < first + CLOSE_GAP_MS) h.advanceFrames(1)
        h.invalidMove(Direction.UP)
        h.advanceTo(QUIET_MS)
        assertWithMessage("two invalid moves 200 ms apart: Rejects").that(rejects(h)).hasSize(1)
        while (h.now() < first + FAR_GAP_MS) h.advanceFrames(1)
        h.invalidMove(Direction.LEFT)
        h.advanceTo(QUIET_MS)
        assertWithMessage("invalid moves 416 ms apart: Rejects").that(rejects(h)).hasSize(2)
    }

    /** AC-NUDGE-5 */
    @Test
    fun wonBoardNeitherNudgesNorBuzzes() {
        assertBlockedGivesNoFeedback(GoldenFixtures.FIX_WON_4)
    }

    /** AC-NUDGE-5 */
    @Test
    fun overBoardNeitherNudgesNorBuzzes() {
        assertBlockedGivesNoFeedback(GoldenFixtures.FIX_OVER_4)
    }

    /** AC-NUDGE-6 */
    @Test
    fun reducedPulsesTheOutlineInstead() {
        val h =
            MotionHarness.launch(
                rule,
                GameFixture(MotionFixtures.FIX_INVALID_4),
                MotionPreference.REDUCED,
            )

        h.invalidMove(Direction.LEFT)
        while (h.t() <= PULSE_END_MS) {
            val board = board(h)
            assertWithMessage("t = ${h.t()}: board offset").that(board.nudgeDp.x).isEqualTo(0f)
            assertWithMessage("t = ${h.t()}: board offset").that(board.nudgeDp.y).isEqualTo(0f)
            // Reference: 0.576 at 96, 0.564 at 112, 0 at 304.
            if (h.t() in PULSE_SAMPLES) {
                assertWithMessage("t = ${h.t()}: outline pulse")
                    .that(board.outlinePulseAlpha)
                    .isWithin(Tol.ALPHA)
                    .of(Curves.value(PULSE, 0f, 0f, h.t()))
            }
            h.advanceFrames(1)
        }
    }

    /** Follows the nudge of [direction] to t = 400, checking the axis and the sample frames. */
    private fun followNudge(h: MotionHarness, direction: Direction, samples: List<Long>) {
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        val sign = if (direction == Direction.LEFT || direction == Direction.UP) -1f else 1f
        while (h.t() < SETTLED_MS) {
            val offset = board(h).nudgeDp
            val (along, across) = if (horizontal) offset.x to offset.y else offset.y to offset.x
            assertWithMessage("$direction t = ${h.t()}: off-axis offset").that(across).isEqualTo(0f)
            if (h.t() in samples) {
                assertWithMessage("$direction t = ${h.t()}: offset along the swipe (dp)")
                    .that(along)
                    .isWithin(Tol.DP)
                    .of(Curves.value(NUDGE, 0f, 0f, h.t(), sign * GameMotion.NUDGE_VELOCITY_DP_S))
            }
            h.advanceFrames(1)
        }
        h.advanceTo(SETTLED_MS)
        val rest = board(h).nudgeDp
        assertWithMessage("$direction t = 400: |nudge| (dp)")
            .that(hypot(rest.x, rest.y))
            .isAtMost(GameMotion.NUDGE_THRESHOLD_DP)
        assertWithMessage("$direction t = 400: running").that(h.snapshot().isAnyRunning).isFalse()
    }

    private fun assertBlockedGivesNoFeedback(game: SavedGame) {
        for (direction in Direction.entries) {
            assertWithMessage("ENGINE_SPAWN_CHECK ${game.state.status} $direction")
                .that(Game.move(game.state, direction))
                .isInstanceOf(MoveOutcome.Blocked::class.java)
        }
        val h = MotionHarness.launch(rule, GameFixture(game), FULL)

        for (direction in Direction.entries) {
            h.vm.onMove(direction)
            repeat(BLOCKED_FRAMES) {
                h.advanceFrames(1)
                val offset = board(h).nudgeDp
                assertWithMessage("${game.state.status} $direction: nudge")
                    .that(abs(offset.x) + abs(offset.y))
                    .isEqualTo(0f)
            }
        }
        assertWithMessage("${game.state.status}: haptics").that(h.haptics.events).isEmpty()
        assertWithMessage("${game.state.status}: nudge origin").that(h.probe.nudgeOriginMs).isNull()
    }

    private fun assertInvalid(game: SavedGame, directions: List<Direction>) {
        for (direction in directions) {
            assertWithMessage("ENGINE_SPAWN_CHECK FIX_INVALID_4 $direction")
                .that(Game.move(game.state, direction))
                .isInstanceOf(MoveOutcome.Invalid::class.java)
        }
    }

    private fun rejects(h: MotionHarness): List<Long> =
        h.haptics.events.filter { it.type == HapticFeedbackType.Reject }.map { it.atMs }

    private fun board(h: MotionHarness): ProbeBoard =
        checkNotNull(h.snapshot().board) { "no BoardView is composed" }

    private companion object {
        val FULL = MotionPreference.FULL
        const val SETTLED_MS = 400L
        const val CLOSE_GAP_MS = 200L
        const val FAR_GAP_MS = 416L
        const val QUIET_MS = 64L
        const val BLOCKED_FRAMES = 4
        const val PULSE_END_MS = 304L
        val PULSE_SAMPLES = listOf(96L, 112L, 304L)
        val STRIP_FRAMES = listOf(0L, 16, 32, 48, 64, 112, 192, 352)
        val NUDGE = GameMotion.nudge<Float>()
        val PULSE =
            keyframes<Float> {
                durationMillis = GameMotion.Nudge.PULSE_MS
                0f at 0
                GameMotion.Nudge.PULSE_PEAK_ALPHA at GameMotion.Nudge.PULSE_PEAK_MS
                0f at GameMotion.Nudge.PULSE_MS
            }
    }
}
