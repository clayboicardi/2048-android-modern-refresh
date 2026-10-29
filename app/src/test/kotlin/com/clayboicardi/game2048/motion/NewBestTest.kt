package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.motion.ProbeBestChip
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The once-per-game new-best moment (MOTION_SPEC.md §3.6, §9.3; D-044) on `FIX_NEW_BEST_4` LEFT: it
 * starts on frame ⌈80⌉ (mechanism B) and its animations have play time 0 on S = 96.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NewBestTest {
    @get:Rule val rule = MotionRule()

    /** Collects every failed check of a method, so one run shows all of its frames. */
    @get:Rule val expect: Expect = Expect.create()

    /** AC-BEST-1 */
    @Test
    fun labelFlipsAt80WithItsOneHaptic() {
        val h = newBest()

        h.advanceTo(64)
        expect.withMessage("t = 64: label").that(chip(h).labelKey).isEqualTo(BEST_LABEL)
        h.advanceTo(AT_MS)
        expect.withMessage("t = 80: label").that(chip(h).labelKey).isEqualTo(NEW_BEST_LABEL)
        expect
            .withMessage("t = 80: haptics")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.GestureThresholdActivate to AT_MS)
        h.advanceTo(S_MS)
        expect
            .withMessage("t = 96 (S): container fraction")
            .that(chip(h).containerFraction)
            .isWithin(Tol.FRACTION)
            .of(0f)
        h.advanceTo(S_MS + COLOR_SAMPLE_MS)
        expect
            .withMessage("t = 192 (S + 96): container fraction (reference 0.896)")
            .that(chip(h).containerFraction)
            .isWithin(Tol.FRACTION)
            .of(Curves.value(GameMotion.defaultEffects(), 0f, 1f, COLOR_SAMPLE_MS))
        h.advanceTo(OVER_MS)
        expect
            .withMessage("t = 1616: the move's only haptic")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.GestureThresholdActivate to AT_MS)
    }

    /** AC-BEST-2 */
    @Test
    fun starPopsAndTurnsWhileSparklesFly() {
        val h = newBest()

        h.advanceTo(S_MS)
        assertWithMessage("t = 96: sparkles alive").that(chip(h).sparklesAlive).isEqualTo(SPARKLES)
        h.advanceTo(S_MS + STAR_SAMPLE_MS)
        assertWithMessage("t = 240 (S + 144): star scale (reference 1.0938)")
            .that(checkNotNull(chip(h).starScale))
            .isWithin(Tol.SCALE)
            .of(
                Curves.value(
                    GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD),
                    0f,
                    1f,
                    STAR_SAMPLE_MS,
                ),
            )
        h.advanceTo(SPARKLES_DEAD_MS)
        assertWithMessage("t = 688: sparkles alive").that(chip(h).sparklesAlive).isEqualTo(0)
        h.advanceTo(TURNED_MS)
        val turned = chip(h).starRotationDeg
        assertWithMessage("t = 1008: star rotation")
            .that(turned)
            .isWithin(ROTATION_TOLERANCE)
            .of(GameMotion.NewBest.STAR_ROTATION_DEG)
        h.advanceFrames(1)
        assertWithMessage("t = 1024: the rotation has stopped")
            .that(chip(h).starRotationDeg)
            .isEqualTo(turned)
    }

    /** AC-BEST-3 */
    @Test
    fun starFadesAndTheMomentEnds() {
        val h = newBest(strip = true)
        val fade =
            tween<Float>(
                GameMotion.NewBest.STAR_FADE_MS,
                delayMillis = GameMotion.NewBest.STAR_FADE_DELAY_MS,
                easing = FastOutSlowInEasing,
            )

        h.advanceTo(S_MS + GameMotion.NewBest.STAR_FADE_DELAY_MS)
        assertWithMessage("t = 1296 (S + 1200): star alpha")
            .that(chip(h).starAlpha)
            .isWithin(Tol.ALPHA)
            .of(Curves.value(fade, 1f, 0f, GameMotion.NewBest.STAR_FADE_DELAY_MS.toLong()))
        h.advanceTo(MOMENT_END_MS)
        assertWithMessage("t = 1600: star alpha").that(chip(h).starAlpha).isWithin(Tol.ALPHA).of(0f)
        h.advanceTo(OVER_MS)
        assertWithMessage("t = 1616: running").that(h.snapshot().isAnyRunning).isFalse()
        h.writeFilmstrip()
    }

    /** AC-BEST-4 */
    @Test
    fun aLaterBestIsNotCelebratedAgain() {
        val path = listOf(Direction.RIGHT, Direction.LEFT, Direction.UP)
        var state = MotionFixtures.NEW_BEST_LEFT.verify().state
        for (direction in path) state = (Game.move(state, direction) as MoveOutcome.Moved).state
        assertWithMessage("ENGINE_SPAWN_CHECK: UP raises the score past the best again")
            .that(state.score)
            .isGreaterThan(MotionFixtures.FIX_NEW_BEST_4.state.score + NEW_BEST_DELTA)
        val h = newBest()
        h.advanceTo(OVER_MS)
        h.settle()
        val before = h.haptics.events.size

        for (direction in path) {
            h.commitMove(direction)
            for (t in listOf(AT_MS, S_MS, S_MS + STAR_SAMPLE_MS, SPARKLES_DEAD_MS)) {
                h.advanceTo(t)
                val chip = chip(h)
                assertWithMessage("$direction t = $t: label")
                    .that(chip.labelKey)
                    .isEqualTo(NEW_BEST_LABEL)
                assertWithMessage("$direction t = $t: sparkles")
                    .that(chip.sparklesAlive)
                    .isEqualTo(0)
                assertWithMessage("$direction t = $t: no star shown")
                    .that(chip.starScale == null || chip.starAlpha == 0f)
                    .isTrue()
                assertWithMessage("$direction t = $t: container fraction")
                    .that(chip.containerFraction)
                    .isWithin(Tol.FRACTION)
                    .of(1f)
            }
        }
        assertWithMessage("no second new-best haptic")
            .that(h.haptics.events.drop(before).map { it.type })
            .doesNotContain(HapticFeedbackType.GestureThresholdActivate)
    }

    private fun newBest(strip: Boolean = false): MotionHarness {
        MotionFixtures.NEW_BEST_LEFT.verify()
        val fixture =
            GameFixture(MotionFixtures.FIX_NEW_BEST_4, records = MotionFixtures.NEW_BEST_RECORDS)
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL)
        if (strip) h.filmstrip("new_best", FilmRegion.HEADER, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)
        return h
    }

    private fun chip(h: MotionHarness): ProbeBestChip =
        checkNotNull(h.snapshot().header) { "no GameHeader is composed" }.bestChip

    private companion object {
        const val AT_MS = 80L
        const val S_MS = 96L
        const val COLOR_SAMPLE_MS = 96L
        const val STAR_SAMPLE_MS = 144L
        const val SPARKLES = GameMotion.NewBest.SPARKLE_COUNT
        const val SPARKLES_DEAD_MS = 688L
        const val TURNED_MS = 1008L
        const val MOMENT_END_MS = 1600L
        const val OVER_MS = 1616L
        const val ROTATION_TOLERANCE = 0.01f
        const val NEW_BEST_DELTA = 8L
        const val NEW_BEST_LABEL = "new_best_label"
        const val BEST_LABEL = "best_label"
        val STRIP_FRAMES = listOf(64L, 80, 96, 192, 240, 480, 688, 1296, 1440, 1600)
    }
}
