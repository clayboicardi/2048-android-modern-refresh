package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.game.motion.ProbeFloat
import com.clayboicardi.game2048.game.motion.ProbeHeader
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
 * The "+N" float and the score odometer (MOTION_SPEC.md §3.4, §3.5, §4.2, §9.2): mechanism B
 * creation at +80, the 650 ms rise, hold and fade, at most 3 alive, and their removal by undo, new
 * game and size switch (D-062). A float counts as visible while its alpha is above 0.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScoreMotionTest {
    @get:Rule val rule = MotionRule()

    /** Collects every failed check of a method, so one run shows all of its frames. */
    @get:Rule val expect: Expect = Expect.create()

    /** AC-SCORE-1, AC-SCORE-2 */
    @Test
    fun plusNIsCreatedAt80AndRises() {
        MotionFixtures.POP_TIERS_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_POP_TIERS_4), FULL)
        h.filmstrip("plus_n_float", FilmRegion.HEADER, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)

        h.advanceTo(64)
        expect.withMessage("t = 64: visible floats").that(visible(h.snapshot())).isEmpty()
        expect
            .withMessage("t = 64: displayed score")
            .that(header(h).displayedScore)
            .isEqualTo(30_000)
        h.advanceTo(CREATED_MS)
        val created = h.snapshot().floats.single()
        expect.withMessage("t = 80: text").that(created.text).isEqualTo("+5696")
        expect.withMessage("t = 80: alpha").that(created.alpha).isWithin(Tol.ALPHA).of(1f)
        expect.withMessage("t = 80: rise").that(created.translationYDp).isWithin(Tol.DP).of(0f)
        expect.withMessage("t = 80: scale").that(created.scale).isWithin(Tol.SCALE).of(START_SCALE)
        expect
            .withMessage("t = 80: displayed score")
            .that(header(h).displayedScore)
            .isEqualTo(35_696)

        // Mechanism B: the float's animations have play time 0 on the frame after its creation.
        h.advanceTo(START_MS)
        expect.withMessage("t = 96: scale").that(float(h).scale).isWithin(Tol.SCALE).of(START_SCALE)
        h.advanceTo(START_MS + RISE_SAMPLE_MS)
        expect
            .withMessage("t = 144: rise (reference -15.28 dp)")
            .that(float(h).translationYDp)
            .isWithin(Tol.DP)
            .of(Curves.value(RISE, 0f, -GameMotion.Score.PLUS_N_RISE_DP, RISE_SAMPLE_MS))
        h.advanceTo(START_MS + FADE_SAMPLE_MS)
        expect
            .withMessage("t = 352: alpha (reference 0.985)")
            .that(float(h).alpha)
            .isWithin(Tol.ALPHA)
            .of(Curves.value(ALPHA, 1f, 0f, FADE_SAMPLE_MS))
        h.advanceTo(GONE_MS)
        expect.withMessage("t = 752: floats").that(h.snapshot().floats).isEmpty()
        h.writeFilmstrip()
    }

    /** AC-SCORE-3 */
    @Test
    fun noMoreThanThreeFloatsAreAlive() {
        assertChainDeltas()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_CHAIN_8), FULL)
        val moves = MotionFixtures.CHAIN_MOVES

        h.commitMove(moves.first().first)
        assertAtMostThree(h, checkNotNull(h.f0))
        for ((direction, _) in moves.drop(1)) {
            while (h.t() < NEXT_COMMIT_MS) {
                h.advanceFrames(1)
                assertAtMostThree(h, h.snapshot())
            }
            h.commitMove(direction)
            assertAtMostThree(h, checkNotNull(h.f0))
            assertAtMostThree(h, h.snapshot())
        }
        val first = "+${moves.first().second}"
        assertWithMessage("on the 4th move's F1: the 1st float")
            .that(h.snapshot().floats.map { it.text })
            .doesNotContain(first)
        val last = "+${moves.last().second}"
        while (h.t() < GONE_MS) {
            h.advanceFrames(1)
            assertAtMostThree(h, h.snapshot())
            val lastShown = visible(h.snapshot()).filter { it.text == last }
            if (h.t() == CREATED_MS - FRAME_MS) {
                assertWithMessage("4th move t = 64: its float").that(lastShown).isEmpty()
            }
            if (h.t() == CREATED_MS) {
                assertWithMessage("4th move t = 80: its float").that(lastShown).hasSize(1)
            }
        }
    }

    /** AC-SCORE-4 */
    @Test
    fun undoRemovesFloatsAndShowsTheUndoneScore() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), FULL)
        h.commitMove(Direction.LEFT)
        h.advanceTo(RESET_AT_MS)
        assertWithMessage("t = 160: the +4 float")
            .that(visible(h.snapshot()).map { it.text })
            .containsExactly("+4")
        assertWithMessage("t = 160: displayed score").that(header(h).displayedScore).isEqualTo(4)

        h.commitUndo()

        assertWithMessage("undo F1: floats").that(h.snapshot().floats).isEmpty()
        assertWithMessage("undo t = 0: displayed score (the odometer's targets)")
            .that(header(h).displayedScore)
            .isEqualTo(GoldenFixtures.FIX_FILM.state.score)
    }

    /** F13-AC15, AC-SCORE-5 */
    @Test
    fun resetsRemoveScoreFloats() {
        // The four "separate runs" share one composition (one compose rule per test method), so
        // each plays FIX_NEW_BEST_4's board, score and best on its own board size.
        MotionFixtures.NEW_BEST_LEFT.verify()
        for (n in RUN_SIZES) {
            val outcome = Game.move(MotionFixtures.newBestGame(n).state, Direction.LEFT)
            assertWithMessage("ENGINE_SPAWN_CHECK new best on $n×$n")
                .that((outcome as MoveOutcome.Moved).scoreDelta)
                .isEqualTo(NEW_BEST_DELTA)
        }
        val fixture =
            GameFixture(
                game = MotionFixtures.FIX_NEW_BEST_4,
                saved = RUN_SIZES.map { MotionFixtures.newBestGame(it) } + GoldenFixtures.FIX_MID_3,
                records = MotionFixtures.NEW_BEST_RECORDS,
            )
        val h = MotionHarness.launch(rule, fixture, FULL)

        undoKeepsTheMoment(h)
        nextRun(h, RUN_SIZES[0])
        newGameEndsTheMoment(h)
        nextRun(h, RUN_SIZES[1])
        sizeSwitchEndsTheMoment(h)
        nextRun(h, RUN_SIZES[2])
        undoAndMoveWithoutAFrame(h)
    }

    private fun undoKeepsTheMoment(h: MotionHarness) {
        startMoment(h, "undo run")
        h.commitUndo()
        assertWithMessage("undo F1: floats").that(h.snapshot().floats).isEmpty()
        val chip = header(h).bestChip
        assertWithMessage("undo F1: label").that(chip.labelKey).isEqualTo(NEW_BEST_LABEL)
        val star = checkNotNull(chip.starScale) { "undo F1: the star badge is not composed" }
        val rotation = chip.starRotationDeg
        h.advanceFrames(1)
        val next = header(h).bestChip
        assertWithMessage("undo F1 + 16: the star badge is composed")
            .that(next.starScale)
            .isNotNull()
        assertWithMessage("undo F1 + 16: the star is still turning")
            .that(next.starRotationDeg to next.starScale)
            .isNotEqualTo(rotation to star)
    }

    private fun newGameEndsTheMoment(h: MotionHarness) {
        startMoment(h, "new game run")
        h.commitNewGame()
        assertMomentRemoved(h, "new game F1")
    }

    private fun sizeSwitchEndsTheMoment(h: MotionHarness) {
        startMoment(h, "size switch run")
        h.commitSizeSwitch(GoldenFixtures.FIX_MID_3.state.size.n)
        assertMomentRemoved(h, "size switch F1")
    }

    private fun undoAndMoveWithoutAFrame(h: MotionHarness) {
        startMoment(h, "undo + RIGHT run")
        h.commit {
            h.vm.onUndo()
            h.vm.onMove(Direction.RIGHT)
        }
        assertWithMessage("RIGHT F1: LEFT's float").that(visible(h.snapshot())).isEmpty()
        h.advanceTo(CREATED_MS - FRAME_MS)
        assertWithMessage("RIGHT t = 64: floats").that(visible(h.snapshot())).isEmpty()
        h.advanceTo(CREATED_MS)
        val created = visible(h.snapshot()).single()
        assertWithMessage("RIGHT t = 80: text").that(created.text).isEqualTo("+$NEW_BEST_DELTA")
        assertWithMessage("RIGHT t = 80: alpha").that(created.alpha).isWithin(Tol.ALPHA).of(1f)
        assertWithMessage("RIGHT t = 80: rise").that(created.translationYDp).isWithin(Tol.DP).of(0f)
        assertWithMessage("RIGHT t = 80: scale")
            .that(created.scale)
            .isWithin(Tol.SCALE)
            .of(START_SCALE)
    }

    /** LEFT on the current new-best board, to t = 160, where the float and the moment run. */
    private fun startMoment(h: MotionHarness, run: String) {
        h.commitMove(Direction.LEFT)
        h.advanceTo(RESET_AT_MS)
        val snapshot = h.snapshot()
        assertWithMessage("$run t = 160: the +8 float")
            .that(visible(snapshot).map { it.text })
            .containsExactly("+$NEW_BEST_DELTA")
        val chip = checkNotNull(snapshot.header).bestChip
        assertWithMessage("$run t = 160: label").that(chip.labelKey).isEqualTo(NEW_BEST_LABEL)
        assertWithMessage("$run t = 160: the star badge").that(chip.starScale).isNotNull()
    }

    private fun assertMomentRemoved(h: MotionHarness, frame: String) {
        val snapshot = h.snapshot()
        assertWithMessage("$frame: floats").that(snapshot.floats).isEmpty()
        val chip = checkNotNull(snapshot.header).bestChip
        assertWithMessage("$frame: the star badge").that(chip.starScale).isNull()
        assertWithMessage("$frame: label").that(chip.labelKey).isEqualTo(BEST_LABEL)
    }

    /** Lets the previous run end, then switches to the next run's board size. */
    private fun nextRun(h: MotionHarness, n: Int) {
        h.settle()
        h.commitSizeSwitch(n)
        h.settle()
    }

    private fun assertAtMostThree(h: MotionHarness, snapshot: MotionSnapshot) {
        assertWithMessage("t = ${h.t()}: visible floats").that(visible(snapshot).size).isAtMost(3)
    }

    private fun assertChainDeltas() {
        var state: GameState = MotionFixtures.FIX_CHAIN_8.state
        for ((direction, delta) in MotionFixtures.CHAIN_MOVES) {
            val moved = Game.move(state, direction) as MoveOutcome.Moved
            assertWithMessage("ENGINE_SPAWN_CHECK FIX_CHAIN_8 $direction")
                .that(moved.scoreDelta)
                .isEqualTo(delta)
            state = moved.state
        }
    }

    private fun visible(snapshot: MotionSnapshot): List<ProbeFloat> =
        snapshot.floats.filter { it.alpha > 0f }

    private fun float(h: MotionHarness): ProbeFloat = h.snapshot().floats.single()

    private fun header(h: MotionHarness): ProbeHeader =
        checkNotNull(h.snapshot().header) { "no GameHeader is composed" }

    private companion object {
        val FULL = MotionPreference.FULL
        const val CREATED_MS = 80L
        const val START_MS = 96L
        const val RISE_SAMPLE_MS = 48L
        const val FADE_SAMPLE_MS = 256L
        const val GONE_MS = 752L
        const val RESET_AT_MS = 160L
        const val NEXT_COMMIT_MS = 16L
        const val START_SCALE = GameMotion.Score.PLUS_N_START_SCALE
        const val NEW_BEST_DELTA = 8L
        const val NEW_BEST_LABEL = "new_best_label"
        const val BEST_LABEL = "best_label"
        val RUN_SIZES = listOf(5, 6, 7)
        val STRIP_FRAMES = listOf(64L, 80, 96, 144, 352, 560, 752)
        val RISE =
            tween<Float>(
                GameMotion.Score.PLUS_N_RISE_MS,
                easing = GameMotion.EmphasizedDecelerate,
            )
        val ALPHA =
            keyframes<Float> {
                durationMillis = GameMotion.Score.PLUS_N_RISE_MS
                1f at 0
                1f at GameMotion.Score.PLUS_N_HOLD_MS
                0f at GameMotion.Score.PLUS_N_RISE_MS
            }
    }
}
