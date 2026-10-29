package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Reduced column of MOTION_SPEC.md §5.1 for the board (§3.1, §3.4, §3.7, §9.10): positions snap
 * at F0, the merge is a 120 ms crossfade, the spawn only fades in, "+N" fades in place, and an
 * invalid move pulses the board outline. The win and game-over methods are M3 part 2.
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
    }
}
