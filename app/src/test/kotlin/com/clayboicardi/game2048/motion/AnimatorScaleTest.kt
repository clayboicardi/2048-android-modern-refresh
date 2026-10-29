package com.clayboicardi.game2048.motion

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.game.motion.ConfettiSystem
import com.clayboicardi.game2048.game.motion.ParticleRandom
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
 * The system animator duration scale (MOTION_SPEC.md §5.2, §9.10; D-063): Compose's
 * `MotionDurationScale` and the app's `AnimatorScaleSource` are set together (§8.1). At 0 the
 * board, the win and the game over behave as Off; at 2 every offset of the move doubles (M3 part
 * 1), and the win confetti is emitted at 400 and flies at half speed (AC-SCALE-3, M3 part 2).
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

    /** AC-SCALE-0 */
    @Test
    fun systemScaleZeroWinBehavesLikeOff() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        duration.scaleFactor = 0f
        val fixture = GameFixture(MotionFixtures.FIX_WIN_PRE_4)
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL, systemScale = 0f)
        var origin = 0L

        h.assertEndStateOnF0("win", quietUntilMs = ZERO_QUIET_MS) {
            origin = h.commitMove(Direction.LEFT)
        }

        val card = checkNotNull(checkNotNull(h.f0).overlay) { "F0: no card" }
        assertWithMessage("F0: overlay kind").that(card.kind).isEqualTo(ProbeOverlayKind.WIN)
        assertWithMessage("F0: interactive").that(card.interactive).isTrue()
        assertWithMessage("win: haptics at +0 and +150 ms real time")
            .that(h.hapticsFrom(origin))
            .containsExactly(CONFIRM to 0L, CONFIRM to WIN_SECOND_REAL_MS)
            .inOrder()
    }

    /** AC-SCALE-0 */
    @Test
    fun systemScaleZeroGameOverBehavesLikeOff() {
        MotionFixtures.OVER_PRE_LEFT.verify()
        duration.scaleFactor = 0f
        val fixture = GameFixture(MotionFixtures.FIX_OVER_PRE_4)
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL, systemScale = 0f)
        var origin = 0L

        h.assertEndStateOnF0("game over", quietUntilMs = ZERO_QUIET_MS) {
            origin = h.commitMove(Direction.LEFT)
        }

        val card = checkNotNull(checkNotNull(h.f0).overlay) { "F0: no card" }
        assertWithMessage("F0: overlay kind").that(card.kind).isEqualTo(ProbeOverlayKind.OVER)
        assertWithMessage("F0: interactive").that(card.interactive).isTrue()
        assertWithMessage("game over: haptics")
            .that(h.hapticsFrom(origin))
            .containsExactly(HapticFeedbackType.Reject to 0L)
    }

    /** AC-SCALE-3 */
    @Test
    fun doubleScaleHalvesTheConfettiSpeed() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        duration.scaleFactor = DOUBLE
        val fixture = GameFixture(MotionFixtures.FIX_WIN_PRE_4)
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL, systemScale = DOUBLE)
        h.commitMove(Direction.LEFT)
        val count = GameMotion.Confetti.COUNT_COMPACT

        h.advanceTo(384)
        assertWithMessage("t = 384: confetti").that(h.snapshot().effects.confettiAlive).isEqualTo(0)
        h.advanceTo(CONFETTI_AT_MS)
        assertWithMessage("t = 400: confetti")
            .that(h.snapshot().effects.confettiAlive)
            .isEqualTo(count)
        val seed = (h.vm.overlay.value as OverlayUi.Win).particleSeed
        assertSameTrajectories(seed, count)

        // D-247: the live canvas steps the burst with the scale-2 sequence that the trajectories
        // above use. On every frame until it is gone, its count equals that of the burst stepped
        // with that sequence (nothing on the frame after the emission, then 16 ms of simulated
        // time, then 8 ms per frame), so a canvas dt off by any factor moves a death to another
        // frame. Half speed: at 2416 every particle has lived 1.008 s, less than the shortest life
        // (1.4 s), while at scale 1 the burst would be gone by then.
        val replica = ConfettiSystem().apply { emitConfetti(0f, 0f, count, ParticleRandom(seed)) }
        val half = FRAME_S / DOUBLE
        var frames = 0
        while (h.t() < CONFETTI_DONE_MS) {
            h.advanceFrames(1)
            frames++
            if (frames == FIRST_STEP_FRAME) replica.step(2 * half)
            if (frames > FIRST_STEP_FRAME) replica.step(half)
            val alive = h.snapshot().effects.confettiAlive
            assertWithMessage("t = ${h.t()}: confetti").that(alive).isEqualTo(replica.aliveCount)
            if (h.t() <= HALF_SPEED_CHECK_MS) {
                assertWithMessage("t = ${h.t()}: confetti").that(alive).isEqualTo(count)
            }
        }
        assertWithMessage("t = 4816: confetti")
            .that(h.snapshot().effects.confettiAlive)
            .isEqualTo(0)
    }

    /**
     * AC-SCALE-3's trajectories: the burst of [seed] after 192 ms of simulated time, stepped as
     * `ContentEffectsCanvas` steps it (MOTION_SPEC.md §2.5, §2.3 T3): its first step covers the
     * emission frame and the next one, so at scale 1 it is 32 ms and then 10 × 16 ms (t = 400), and
     * at scale 2 it is 16 ms and then 22 × 8 ms (t = 784). Every particle is within 4 dp (D-243:
     * the step scheme of §3.11 itself puts up to 3.5 dp between the two step sizes). The live
     * particles are not on the probe (`ProbeEffects` has only the count), so the engine the canvas
     * runs is stepped with the dt sequence of each scale; the frame-by-frame count of the test
     * proves that the canvas steps the scale-2 sequence (D-247).
     */
    private fun assertSameTrajectories(seed: Long, count: Int) {
        val scale1 = burst(seed, count, listOf(2 * FRAME_S) + List(SCALE_1_STEPS - 2) { FRAME_S })
        val half = FRAME_S / DOUBLE
        val scale2 = burst(seed, count, listOf(2 * half) + List(SCALE_1_STEPS * 2 - 2) { half })
        assertWithMessage("alive after 192 ms").that(scale2.aliveCount).isEqualTo(count)
        for (i in 0 until count) {
            assertWithMessage("particle $i: x (dp)")
                .that(scale2.x[i])
                .isWithin(TRAJECTORY_TOL_DP)
                .of(scale1.x[i])
            assertWithMessage("particle $i: y (dp)")
                .that(scale2.y[i])
                .isWithin(TRAJECTORY_TOL_DP)
                .of(scale1.y[i])
        }
    }

    private fun burst(seed: Long, count: Int, steps: List<Float>): ConfettiSystem =
        ConfettiSystem().apply {
            emitConfetti(0f, 0f, count, ParticleRandom(seed))
            for (dt in steps) step(dt)
        }

    private companion object {
        const val DOUBLE = 2f
        const val REVEAL_MS = 160L
        const val QUIET_MS = 864L
        const val WIN_SECOND_REAL_MS = 160L
        const val ZERO_QUIET_MS = 176L
        const val CONFETTI_AT_MS = 400L
        const val CONFETTI_DONE_MS = 4816L

        /** The canvas's first step is on the second frame after the emission frame (§2.5). */
        const val FIRST_STEP_FRAME = 2

        /** Until here every particle is alive at half speed. */
        const val HALF_SPEED_CHECK_MS = 2416L
        const val SCALE_1_STEPS = 12
        const val FRAME_S = 0.016f
        const val TRAJECTORY_TOL_DP = 4f
        val CONFIRM = HapticFeedbackType.Confirm
    }
}
