package com.clayboicardi.game2048.motion

import android.graphics.Bitmap
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.board.TileUi
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.game.overlay.CARD_NEW_GAME_TAG
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.min
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game-over sequence under Full motion (MOTION_SPEC.md §3.13, §9.8; D-064, D-183, D-185,
 * D-200): `FIX_OVER_PRE_4` and LEFT fill the board with no move left. From T0 = origin + 300 the
 * board desaturates and deflates in a ripple from the final spawn's cell (3,3), the scrim follows
 * at T0 + 450, the card at T0 + 500 and its buttons become interactive at T0 + 700; the end state
 * equals the static `Restored` render. Times are ms from the origin on the 16 ms grid; curve values
 * come from the library (D-210), with the MOTION_SPEC.md reference in a comment.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GameOverSequenceTest {
    @get:Rule val rule = MotionRule()

    /** AC-OVER-1 */
    @Test
    fun boardDesaturatesFromT0() {
        val h = launchOver()

        h.advanceTo(288)
        assertSaturation(h, 1f)
        // Reference 0.675.
        h.advanceTo(400)
        assertSaturation(h, Curves.value(DESATURATE, 1f, SATURATION_END, 400))
        // Reference 0.421.
        h.advanceTo(496)
        assertSaturation(h, Curves.value(DESATURATE, 1f, SATURATION_END, 496))
        h.advanceTo(DESATURATED_MS)
        while (h.t() <= QUIET_MS) {
            assertSaturation(h, SATURATION_END)
            h.advanceFrames(1)
        }
        assertWithMessage("haptics of the losing move")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.Reject to REJECT_MS)
    }

    /** AC-OVER-2 */
    @Test
    fun tilesDeflateInARippleFromTheFinalSpawn() {
        val h = launchOver()
        val board = checkNotNull(h.committed)
        val spawn = board.tiles.single { it.kind == TileKind.Spawned }
        assertWithMessage("the deflate origin (row, column)")
            .that(spawn.row to spawn.col)
            .isEqualTo(ORIGIN_ROW to ORIGIN_COL)
        val tiles = board.tiles.filter { it.kind != TileKind.Ghost }
        val mMax = tiles.maxOf { distance(it) }
        val step = min(STEP_MS.toFloat(), WINDOW_MS.toFloat() / mMax)
        assertWithMessage("mMax").that(mMax).isEqualTo(6)
        assertWithMessage("step (ms)").that(step).isEqualTo(30f)

        while (h.t() < SETTLED_MS) {
            h.advanceFrames(1)
            val t = h.t()
            if (t == SPAWN_DEFLATING_MS) {
                assertWithMessage("t = 320: the spawn at (3,3) scale")
                    .that(h.tile(spawn.id).scale)
                    .isLessThan(1f)
            }
            for (tile in tiles.filter { it.kind != TileKind.Spawned }) {
                val start = GameMotion.GameOver.AT_MS + step * distance(tile)
                val lastBefore = lastFrameBefore(start)
                val scale = h.tile(tile.id).scale
                val label = "t = $t: tile ${tile.id} at m = ${distance(tile)} scale"
                if (t == lastBefore) assertWithMessage(label).that(scale).isWithin(EXACT).of(1f)
                if (t == lastBefore + 2 * FRAME_MS)
                    assertWithMessage(label).that(scale).isLessThan(1f)
            }
        }
        repeat(2) {
            for (tile in tiles) {
                assertWithMessage("t = ${h.t()}: tile ${tile.id} scale")
                    .that(h.tile(tile.id).scale)
                    .isWithin(EXACT)
                    .of(GameMotion.GameOver.DEFLATE_SCALE)
            }
            h.advanceFrames(1)
        }
    }

    /** AC-OVER-3 */
    @Test
    fun scrimFadesInFromT0Plus450() {
        val h = launchOver()

        h.advanceTo(736)
        assertWithMessage("t = 736: scrim").that(h.snapshot().scrimReading ?: 0f).isEqualTo(0f)
        h.advanceTo(752)
        val first = checkNotNull(h.snapshot().scrimReading) { "t = 752: no scrim reading" }
        assertWithMessage("t = 752: scrim").that(first).isGreaterThan(0f)
        // Reference 0.0003.
        assertWithMessage("t = 752: scrim")
            .that(first)
            .isWithin(Tol.ALPHA)
            .of(Curves.value(OVER_SCRIM, 0f, GameMotion.GameOver.SCRIM_ALPHA, 752))
        h.advanceTo(848)
        // Reference 0.564.
        assertWithMessage("t = 848: scrim")
            .that(checkNotNull(h.snapshot().scrimReading) { "t = 848: no scrim reading" })
            .isWithin(Tol.ALPHA)
            .of(Curves.value(OVER_SCRIM, 0f, GameMotion.GameOver.SCRIM_ALPHA, 848))
    }

    /** AC-OVER-4 */
    @Test
    fun cardComposesAndBecomesInteractive() {
        val h = launchOver()

        h.advanceTo(784)
        assertWithMessage("t = 784: the overlay").that(h.snapshot().overlay).isNull()
        h.advanceTo(OVER_CARD_AT_MS)
        assertWithMessage("t = 800: overlay kind")
            .that(h.snapshot().overlay?.kind)
            .isEqualTo(ProbeOverlayKind.OVER)
        h.advanceTo(OVER_O_MS)
        assertWithMessage("the overlay origin O (ms from the origin)")
            .that(checkNotNull(h.snapshot().overlayOriginMs) { "no overlay origin" } - h.originMs)
            .isEqualTo(OVER_O_MS)
        h.assertButtonsLocked(OVER_BUTTONS, OVER_CARD_AT_MS, OVER_LOCKED_MS)
        h.advanceTo(OVER_INTERACTIVE_MS)
        h.assertButtonsInteractive(OVER_BUTTONS, CARD_NEW_GAME_TAG)
    }

    /** AC-OVER-5 */
    @Test
    fun endStateEqualsTheStaticRender() {
        MotionFixtures.OVER_PRE_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_OVER_PRE_4), FULL)
        h.filmstrip("game_over_sequence", FilmRegion.ROOT, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)

        h.advanceTo(QUIET_MS)
        val frame = h.snapshot()
        for (tile in frame.tiles.filter { !it.outgoing }) {
            assertWithMessage("t = 1200: tile ${tile.id} scale")
                .that(tile.scale)
                .isWithin(Tol.SCALE)
                .of(GameMotion.GameOver.DEFLATE_SCALE)
        }
        assertSaturation(h, SATURATION_END)
        val live = h.capture(FilmRegion.ROOT, downscale = false)
        h.writeFilmstrip()
        val static = h.staticRender()
        val same = live.sameAs(static)
        if (!same) {
            write(live, "over-live")
            write(static, "over-static")
        }
        assertWithMessage("t = 1200: the root bitmap equals the static Restored render")
            .that(same)
            .isTrue()
    }

    private fun launchOver(): MotionHarness {
        MotionFixtures.OVER_PRE_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_OVER_PRE_4), FULL)
        h.commitMove(Direction.LEFT)
        return h
    }

    private fun assertSaturation(h: MotionHarness, expected: Float) {
        val board = checkNotNull(h.snapshot().board) { "t = ${h.t()}: no board" }
        assertWithMessage("t = ${h.t()}: saturation")
            .that(board.saturation)
            .isWithin(Tol.FRACTION)
            .of(expected)
    }

    /** Manhattan distance from the final spawn's cell (3,3). */
    private fun distance(tile: TileUi): Int =
        abs(tile.row - ORIGIN_ROW) + abs(tile.col - ORIGIN_COL)

    /** The last 16 ms grid frame strictly before [ms] from the origin. */
    private fun lastFrameBefore(ms: Float): Long {
        var frame = 0L
        while (frame + FRAME_MS < ms) frame += FRAME_MS
        return frame
    }

    /** A mismatching pair goes next to the filmstrips, for inspection. */
    private fun write(bitmap: Bitmap, name: String) {
        val dir = File("build/reports/filmstrips/GameOverSequenceTest").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }
    }

    private companion object {
        val FULL = MotionPreference.FULL
        const val SATURATION_END = GameMotion.GameOver.SATURATION_END
        const val DESATURATED_MS = 912L
        const val QUIET_MS = 1200L
        const val REJECT_MS = 304L
        const val ORIGIN_ROW = 3
        const val ORIGIN_COL = 3
        const val STEP_MS = GameMotion.GameOver.DEFLATE_STEP_MS
        const val WINDOW_MS = GameMotion.GameOver.DEFLATE_WINDOW_MS
        const val SPAWN_DEFLATING_MS = 320L
        const val SETTLED_MS = 720L
        const val OVER_CARD_AT_MS = 800L
        const val OVER_O_MS = 816L
        const val OVER_LOCKED_MS = 992L
        const val OVER_INTERACTIVE_MS = 1008L
        const val EXACT = 1e-4f
        const val PNG_QUALITY = 100
        val STRIP_FRAMES = listOf(0L, 288, 304, 400, 496, 608, 752, 800, 848, 912, 1008, 1200)

        /** Saturation 1 → 0.2: `tween(600, delayMillis = 300, Standard)` from the origin. */
        val DESATURATE: FiniteAnimationSpec<Float> =
            tween(
                GameMotion.GameOver.DESATURATE_MS,
                delayMillis = GameMotion.GameOver.AT_MS,
                easing = GameMotion.Standard,
            )

        /** The scrim 0 → 0.78: `tween(300, delayMillis = T0 + 450, Standard)` from the origin. */
        val OVER_SCRIM: FiniteAnimationSpec<Float> =
            tween(
                GameMotion.GameOver.SCRIM_MS,
                delayMillis = GameMotion.GameOver.AT_MS + GameMotion.GameOver.SCRIM_AT_MS,
                easing = GameMotion.Standard,
            )
    }
}
