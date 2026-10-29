package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.board.boardGeometryPx
import com.clayboicardi.game2048.ui.theme.ClassicLightPalette
import com.clayboicardi.game2048.ui.theme.ClassicLightSkin
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.NeonLightPalette
import com.clayboicardi.game2048.ui.theme.NeonLightSkin
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The theme crossfade (MOTION_SPEC.md §3.15, §9.9 AC-THEME-1; D-064, D-248): on the Settings
 * screen, Palette changes Classic → Neon through the settings repository, as the Palette dialog
 * does. Under Full `MotionProbe.paletteFraction` runs 0 → 1 with `slowEffects` from
 * `paletteOriginMs`, the crossfade's first frame (t = 0); under Reduced and Off it reads 1 on F0
 * and no crossfade starts. On the Game screen the background and an empty cell are `lerp(Classic,
 * Neon, fraction)` under Full and Neon on F0 under Reduced and Off. The fraction at 64 and 160 is
 * checked against the spec's numbers and against Compose's own evaluation of the spring (D-210).
 * Full also writes the `theme_crossfade` inspection strip (§8.4).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ThemeCrossfadeTest {
    @get:Rule val rule = MotionRule()

    /** AC-THEME-1 */
    @Test
    fun fullCrossfadesPaletteWithSlowEffects() {
        val h = launchOnSettings(MotionPreference.FULL)
        h.manualFilmstrip("theme_crossfade", FilmRegion.ROOT)
        val origin = changePalette(h)

        assertWithMessage("t = 0 (F1): fraction").that(fraction(h)).isEqualTo(0f)
        h.captureFilmFrame("t=000")
        for (t in TIMELINE) {
            h.advanceTo(t, origin)
            if (t in STRIP_MS) h.captureFilmFrame("t=${t.toString().padStart(3, '0')}")
            when (t) {
                64L,
                160L -> {
                    val spec = if (t == 64L) AT_64 else AT_160
                    val library = library(t)
                    assertWithMessage("t = $t: Compose's slowEffects value vs the spec's")
                        .that(abs(library - spec))
                        .isAtMost(FRACTION_TOLERANCE)
                    assertWithMessage("t = $t: fraction")
                        .that(abs(fraction(h) - library))
                        .isAtMost(PROBE_TOLERANCE)
                }
                320L -> {
                    assertWithMessage("t = 320: nothing running")
                        .that(h.snapshot().isAnyRunning)
                        .isFalse()
                    assertWithMessage("t = 320: fraction").that(fraction(h)).isEqualTo(1f)
                }
            }
        }
        h.writeFilmstrip()
    }

    /** AC-THEME-1 */
    @Test
    fun reducedShowsNewColoursOnF0() {
        assertNewColoursOnF0(MotionPreference.REDUCED)
    }

    /** AC-THEME-1 */
    @Test
    fun offShowsNewColoursOnF0() {
        assertNewColoursOnF0(MotionPreference.OFF)
    }

    /** AC-THEME-1 */
    @Test
    fun fullMixesScreenAndBoardColours() {
        // The colours on the Game screen under Full: the background (read in composition) and an
        // empty cell (read in the board's draw) are lerp(Classic, Neon, fraction), Neon at 320.
        val h =
            MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_MID_4), MotionPreference.FULL)
        val origin = changePalette(h)
        for (t in listOf(0L, 64L, 160L, 320L)) {
            h.advanceTo(t, origin)
            val f = if (t == 320L) 1f else library(t)
            assertColours(h, "Full, t = $t", f)
        }
    }

    /** AC-THEME-1 */
    @Test
    fun reducedShowsNeonColoursOnF0() {
        // The colours under Reduced: the Game screen is Neon on F0 (Off takes the same path in
        // GameTheme, which crossfades under Full only).
        val h =
            MotionHarness.launch(
                rule,
                GameFixture(GoldenFixtures.FIX_MID_4),
                MotionPreference.REDUCED,
            )
        h.container.settingsRepository.update { it.copy(palette = PaletteId.NEON) }
        flush()
        h.advanceFrames(1)
        assertColours(h, "Reduced, F0", 1f)
    }

    /**
     * The Game screen's corner (the palette background) and the centre of the first empty cell
     * (`palette.empty`) at crossfade fraction [f].
     */
    private fun assertColours(h: MotionHarness, label: String, f: Float) {
        val root = h.capture(FilmRegion.ROOT, downscale = false)
        assertNear(
            "$label: screen background",
            root.getPixel(CORNER_PX, CORNER_PX),
            lerp(ClassicLightPalette.background, NeonLightPalette.background, f),
        )
        val board = h.capture(FilmRegion.BOARD, downscale = false)
        val skin = if (f > 0f) NeonLightSkin else ClassicLightSkin
        val g = boardGeometryPx(N, board.width, skin, h.rule.density.density)
        val board0 = GoldenFixtures.FIX_MID_4.state.board
        val empty = (0 until board0.cellCount).first { board0[it] == 0 }
        val at = g.cellTopLeft((empty % N).toFloat(), (empty / N).toFloat())
        val half = (g.tilePx / 2f).toInt()
        assertNear(
            "$label: empty cell $empty",
            board.getPixel(at.x + half, at.y + half),
            lerp(ClassicLightPalette.empty, NeonLightPalette.empty, f),
        )
    }

    /** Reduced and Off: F0 shows Neon, the fraction reads 1 and no crossfade starts. */
    private fun assertNewColoursOnF0(motion: MotionPreference) {
        val h = launchOnSettings(motion)
        val before = h.probe.paletteOriginMs
        h.container.settingsRepository.update { it.copy(palette = PaletteId.NEON) }
        flush()
        h.advanceFrames(1)
        assertWithMessage("$motion, F0: fraction").that(fraction(h)).isEqualTo(1f)
        h.advanceFrames(2)
        assertWithMessage("$motion: no crossfade origin")
            .that(h.probe.paletteOriginMs)
            .isEqualTo(before)
        assertWithMessage("$motion: nothing running").that(h.snapshot().isAnyRunning).isFalse()
    }

    /** `FIX_MID_4`, Classic light, pushed to Settings through the game menu, the push ended. */
    private fun launchOnSettings(motion: MotionPreference): MotionHarness {
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_MID_4), motion)
        h.pushFromMenu(SettingsKey)
        h.advanceTo(NAV_SETTLED_MS)
        h.settle()
        return h
    }

    /**
     * Writes Palette = Neon, delivers the write to the composition (the main looper's work and the
     * snapshot apply notifications, as the harness does after a gesture) and advances, one frame at
     * a time (at most 3), to the crossfade's first frame, `paletteOriginMs`: t = 0.
     */
    private fun changePalette(h: MotionHarness): Long {
        val before = h.probe.paletteOriginMs
        h.container.settingsRepository.update { it.copy(palette = PaletteId.NEON) }
        flush()
        repeat(MAX_ORIGIN_FRAMES) {
            h.advanceFrames(1)
            val origin = h.probe.paletteOriginMs
            if (origin != null && origin != before) {
                check(origin == h.now()) { "palette origin $origin is not the current frame" }
                return origin
            }
        }
        error("no palette crossfade origin within $MAX_ORIGIN_FRAMES frames")
    }

    private fun flush() {
        ShadowLooper.idleMainLooper()
        Snapshot.sendApplyNotifications()
    }

    private fun fraction(h: MotionHarness): Float =
        checkNotNull(h.snapshot().chrome) { "no chrome readers registered" }.paletteFraction

    private fun assertNear(label: String, pixel: Int, expected: Color) {
        val e = expected.toArgb()
        val distance =
            maxOf(
                abs(((pixel shr 16) and 0xFF) - ((e shr 16) and 0xFF)),
                abs(((pixel shr 8) and 0xFF) - ((e shr 8) and 0xFF)),
                abs((pixel and 0xFF) - (e and 0xFF)),
            )
        assertWithMessage(
                "$label: #%s, expected #%s",
                "%06X".format(Locale.ROOT, pixel and 0xFFFFFF),
                "%06X".format(Locale.ROOT, e and 0xFFFFFF),
            )
            .that(distance)
            .isAtMost(COLOR_TOLERANCE)
    }

    private companion object {
        /** MOTION_SPEC.md §8.4's `theme_crossfade` frames after t = 0. */
        val STRIP_MS = listOf(32L, 64L, 128L, 208L, 320L)

        /** Every checked or captured frame, in order. */
        val TIMELINE = listOf(32L, 64L, 128L, 160L, 208L, 320L)

        /** MOTION_SPEC.md §3.15: `slowEffects` at 64 and 160 ms. */
        const val AT_64 = 0.5402f
        const val AT_160 = 0.9402f

        /** The spec's numbers vs Compose's (MOTION_SPEC.md §9 tolerances for fractions). */
        const val FRACTION_TOLERANCE = 0.01f

        /** The probe vs Compose's evaluation of the same spring at the same play time. */
        const val PROBE_TOLERANCE = 0.002f

        /** One 8-bit step of anti-aliasing or colour-space rounding, twice. */
        const val COLOR_TOLERANCE = 2
        const val CORNER_PX = 2
        const val MAX_ORIGIN_FRAMES = 3

        /** The push to Settings has ended (MOTION_SPEC.md §3.17, AC-NAV-1: 256 from its origin). */
        const val NAV_SETTLED_MS = 256L

        /** `FIX_MID_4`'s side. */
        const val N = 4

        /** Compose's `slowEffects` fraction 0 → 1 at play time [tMs]. */
        fun library(tMs: Long): Float =
            GameMotion.slowEffects(GameMotion.FRACTION_THRESHOLD)
                .vectorize(Float.VectorConverter)
                .getValueFromNanos(
                    tMs * 1_000_000L,
                    AnimationVector1D(0f),
                    AnimationVector1D(1f),
                    AnimationVector1D(0f),
                )
                .value
    }
}
