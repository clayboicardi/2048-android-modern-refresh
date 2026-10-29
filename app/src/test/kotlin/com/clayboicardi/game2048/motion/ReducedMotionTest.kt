package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.motion.ProbeOverlay
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.game.overlay.CARD_NEW_GAME_TAG
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
 * The Reduced column of MOTION_SPEC.md §5.1 (§3.1, §3.4, §3.7, §3.11, §3.13, §9.10): positions snap
 * at F0, the merge is a 120 ms crossfade, the spawn only fades in, "+N" fades in place, and an
 * invalid move pulses the board outline (M3 part 1); the win and game-over cards compose on the
 * origin frame and fade in over 150 ms with a static badge, the game-over tiles snap to 0.94, and
 * the buttons become interactive at 160 (M3 part 2).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ReducedMotionTest {
    @get:Rule val rule = MotionRule()

    /** AC-RED-1 */
    @Test
    fun reducedMoveSnapsAndFades() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), REDUCED)
        h.filmstrip("reduced_move", FilmRegion.BOARD, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)
        val board = checkNotNull(h.committed)
        val film = FilmLeft(board)
        val f0 = checkNotNull(h.f0)
        for (tile in board.tiles) {
            val drawn = checkNotNull(f0.tiles.firstOrNull { it.id == tile.id && !it.outgoing })
            assertWithMessage("F0: tile ${tile.id} (${tile.kind}) at its final cell")
                .that(drawn.x to drawn.y)
                .isEqualTo(tile.col.toFloat() to tile.row.toFloat())
        }
        assertWithMessage("t = 0: the +N float is created on the origin frame")
            .that(h.snapshot().floats.map { it.text })
            .containsExactly("+4")

        while (h.t() <= STRIP_FRAMES.last()) {
            assertWithMessage("t = ${h.t()}: spawn scale")
                .that(h.tile(film.spawn.id).scale)
                .isEqualTo(1f)
            h.advanceFrames(1)
            when (h.t()) {
                32L ->
                    assertNear(
                        h,
                        "spawn alpha",
                        h.tile(film.spawn.id).alpha,
                        Curves.value(SPAWN, 0f, 1f, 32),
                    )
                64L -> {
                    // Reference: merged 0.533 (64/120); "+N" alpha 0.88 (48 ms into 400 ms).
                    val merged = h.tile(film.merged.id).alpha
                    assertNear(h, "merged alpha", merged, Curves.value(MERGE, 0f, 1f, 64))
                    val float = h.snapshot().floats.single()
                    assertWithMessage("t = 64: +N rise").that(float.translationYDp).isEqualTo(0f)
                    val fade = Curves.value(PLUS_N, 1f, 0f, 64 - FRAME_MS)
                    assertNear(h, "+N alpha", float.alpha, fade)
                }
                128L -> {
                    assertWithMessage("t = 128: merged alpha")
                        .that(h.tile(film.merged.id).alpha)
                        .isEqualTo(1f)
                    for (ghost in film.ghosts) {
                        assertWithMessage("t = 128: ghost alpha")
                            .that(h.tile(ghost.id).alpha)
                            .isEqualTo(0f)
                    }
                }
            }
        }
        h.writeFilmstrip()
    }

    /** AC-NUDGE-6 */
    @Test
    fun reducedInvalidMoveOnlyPulsesTheOutline() {
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_INVALID_4), REDUCED)
        h.filmstrip("reduced_invalid", FilmRegion.BOARD, STRIP_FRAMES)

        h.invalidMove(Direction.LEFT)
        while (h.t() <= STRIP_FRAMES.last()) {
            val board = checkNotNull(h.snapshot().board)
            assertWithMessage("t = ${h.t()}: board offset")
                .that(board.nudgeDp.x to board.nudgeDp.y)
                .isEqualTo(0f to 0f)
            if (h.t() in PULSE_SAMPLES) {
                val pulse = Curves.value(PULSE, 0f, 0f, h.t())
                assertNear(h, "outline pulse", board.outlinePulseAlpha, pulse)
            }
            h.advanceFrames(1)
        }
        h.writeFilmstrip()
    }

    /** AC-RED-2 */
    @Test
    fun reducedWinFadesInWithAStaticBadge() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_WIN_PRE_4), REDUCED)
        h.filmstrip("reduced_win", FilmRegion.ROOT, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)

        val first = checkNotNull(h.snapshot().overlay) { "t = 0: no card" }
        assertWithMessage("t = 0: overlay kind").that(first.kind).isEqualTo(ProbeOverlayKind.WIN)
        while (h.t() <= STRIP_FRAMES.last()) {
            val frame = h.snapshot()
            assertWithMessage("t = ${h.t()}: confetti")
                .that(frame.effects.confettiAlive)
                .isEqualTo(0)
            assertWithMessage("t = ${h.t()}: ring").that(frame.effects.ring).isNull()
            val card = checkNotNull(frame.overlay) { "t = ${h.t()}: no card" }
            assertStaticBadge(h, card)
            if (h.t() == 64L) {
                // Reference 0.32 (48/150) and 0.256 (0.6 × 64/150).
                assertNear(
                    h,
                    "card alpha",
                    card.cardAlpha,
                    Curves.value(FADE, 0f, 1f, 64 - FRAME_MS),
                )
                val scrim = Curves.value(FADE, 0f, GameMotion.Win.SCRIM_ALPHA, 64)
                assertNear(h, "scrim", card.scrimAlpha, scrim)
            }
            if (h.t() < REDUCED_INTERACTIVE_MS) h.assertLockedNow(WIN_BUTTONS)
            if (h.t() == REDUCED_LOCKED_MS) {
                for (tag in WIN_BUTTONS) h.button(tag).assertCannotTakeFocus()
            }
            if (h.t() == REDUCED_INTERACTIVE_MS) {
                h.assertButtonsInteractive(WIN_BUTTONS, KEEP_GOING_TAG)
            }
            h.advanceFrames(1)
        }
        h.writeFilmstrip()
        assertWithMessage("haptics: Confirm at +0 and +150 ms real time")
            .that(h.hapticsFrom())
            .containsExactly(CONFIRM to 0L, CONFIRM to REDUCED_INTERACTIVE_MS)
            .inOrder()
    }

    /** AC-RED-3 */
    @Test
    fun reducedGameOverSnapsTilesAndFades() {
        MotionFixtures.OVER_PRE_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_OVER_PRE_4), REDUCED)
        h.filmstrip("reduced_over", FilmRegion.ROOT, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)

        val f0 = checkNotNull(h.f0)
        for (tile in f0.tiles.filter { !it.outgoing }) {
            assertWithMessage("F0: tile ${tile.id} (${tile.kind}) scale")
                .that(tile.scale)
                .isWithin(Tol.SCALE)
                .of(GameMotion.GameOver.DEFLATE_SCALE)
        }
        h.assertButtonsLocked(OVER_BUTTONS, 0, REDUCED_LOCKED_MS)
        h.advanceTo(REDUCED_INTERACTIVE_MS)
        assertWithMessage("t = 160: saturation")
            .that(checkNotNull(h.snapshot().board).saturation)
            .isWithin(Tol.FRACTION)
            .of(GameMotion.GameOver.SATURATION_END)
        h.assertButtonsInteractive(OVER_BUTTONS, CARD_NEW_GAME_TAG)
        h.advanceTo(STRIP_FRAMES.last())
        h.writeFilmstrip()
        assertWithMessage("haptics: Reject at the origin")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.Reject to 0L)
    }

    /** Reduced: no morph, badge scale or rotation; the badge is in its end state (§3.11). */
    private fun assertStaticBadge(h: MotionHarness, card: ProbeOverlay) {
        assertWithMessage("t = ${h.t()}: badge morph").that(card.badgeMorph).isEqualTo(1f)
        assertWithMessage("t = ${h.t()}: badge scale").that(card.badgeScale).isEqualTo(1f)
        assertWithMessage("t = ${h.t()}: badge rotation")
            .that(card.badgeRotationDeg)
            .isEqualTo(GameMotion.Win.BADGE_ROTATION_DEG)
    }

    private fun assertNear(h: MotionHarness, what: String, actual: Float, expected: Float) {
        assertWithMessage("t = ${h.t()}: $what").that(actual).isWithin(Tol.ALPHA).of(expected)
    }

    private companion object {
        val REDUCED = MotionPreference.REDUCED
        val STRIP_FRAMES = listOf(0L, 16, 64, 128, 160, 304)
        val PULSE_SAMPLES = listOf(96L, 112L, 304L)
        val SPAWN = GameMotion.fastEffects<Float>(GameMotion.ALPHA_THRESHOLD)
        val MERGE = tween<Float>(GameMotion.Move.REDUCED_MERGE_CROSSFADE_MS, easing = LinearEasing)
        val PLUS_N = tween<Float>(GameMotion.Score.REDUCED_PLUS_N_FADE_MS, easing = LinearEasing)
        val PULSE =
            keyframes<Float> {
                durationMillis = GameMotion.Nudge.PULSE_MS
                0f at 0
                GameMotion.Nudge.PULSE_PEAK_ALPHA at GameMotion.Nudge.PULSE_PEAK_MS
                0f at GameMotion.Nudge.PULSE_MS
            }

        /** The Reduced card and scrim fade: `tween(150, LinearEasing)` (§3.11, §3.13). */
        val FADE = tween<Float>(GameMotion.Card.REDUCED_FADE_MS, easing = LinearEasing)
        val CONFIRM = HapticFeedbackType.Confirm

        /** The last frame before the Reduced interactive flip ⌈150⌉. */
        const val REDUCED_LOCKED_MS = 144L
        const val REDUCED_INTERACTIVE_MS = 160L
    }
}
