package com.clayboicardi.game2048.motion

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.GameKey
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.CONTROLS_TAG
import com.clayboicardi.game2048.menuItemTag
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screen transitions (MOTION_SPEC.md §3.17, §9.9 AC-NAV-1; D-023, D-104): Game → Settings through
 * the game menu, measured on the Settings root (`screenTag(SettingsKey)`) from
 * `MotionProbe.navOriginMs`, the transition's play-time-0 frame. Under Full the pushed screen
 * enters `shiftPx = 30.dp.roundToPx()` towards the end side (the start side in RTL) with alpha 0
 * and settles by 256; under Off only Settings is composed from the next frame on. The offset is the
 * Settings root's x in the root (layout and layer transforms included); its alpha is read from
 * pixels. At t = 0 the main window above the controls bar (header and board, which an opaque
 * Settings would cover) equals the frame before the push: the controls bar itself is left out,
 * because the More button's state changes as the menu closes. At 256 the whole main window equals
 * the settled frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NavTransitionsTest {
    @get:Rule val rule = MotionRule()

    /** AC-NAV-1 */
    @Test
    fun pushShiftsAndFadesInSettings() {
        val h = launch(MotionPreference.FULL)
        h.filmstrip("nav_push_pop", FilmRegion.ROOT, STRIP_FRAMES)

        assertPush(h, sign = 1)

        h.continueFilmstripOnNextOrigin("back ")
        h.pressBack()
        h.advanceTo(SETTLED_MS)
        assertWithMessage("after Back, t = 256: Settings composed")
            .that(h.exists(screenTag(SettingsKey)))
            .isFalse()
        assertWithMessage("after Back, t = 256: the board composed")
            .that(h.exists(BOARD_TAG))
            .isTrue()
        h.writeFilmstrip()
    }

    /** AC-NAV-1 */
    @Test
    @Config(qualifiers = "+fa")
    fun rtlPushShiftsFromTheOtherSide() {
        val h = launch(MotionPreference.FULL)
        assertPush(h, sign = -1)
    }

    /** AC-NAV-1 */
    @Test
    fun offComposesOnlySettings() {
        val h = launch(MotionPreference.OFF)
        h.pushFromMenu(SettingsKey)
        h.advanceTo(16)
        assertWithMessage("Off, t = 16: Settings composed")
            .that(h.exists(screenTag(SettingsKey)))
            .isTrue()
        assertWithMessage("Off, t = 16: the board composed").that(h.exists(BOARD_TAG)).isFalse()
        assertWithMessage("Off, t = 16: the Game destination composed")
            .that(h.exists(screenTag(GameKey)))
            .isFalse()
    }

    /**
     * The Full push (§3.17): Settings enters from `sign · shiftPx` with alpha 0 at t = 0, and is at
     * 0 with alpha 1 at t = 256, when Game is no longer composed.
     */
    private fun assertPush(h: MotionHarness, sign: Int) {
        val shiftPx = with(h.rule.density) { GameMotion.Nav.SHIFT_DP.dp.roundToPx() }
        h.openMenu(SettingsKey)
        val controlsTop = h.rule.onNodeWithTag(CONTROLS_TAG).fetchSemanticsNode().boundsInRoot.top
        val before = aboveControls(h.capture(FilmRegion.ROOT, downscale = false), controlsTop)

        h.commitNavigation { h.rule.onNodeWithTag(menuItemTag(SettingsKey)).performClick() }

        assertWithMessage("t = 0: Settings x (px)")
            .that(settingsX(h))
            .isWithin(PX_TOL)
            .of(sign * shiftPx.toFloat())
        val t0 = aboveControls(h.capture(FilmRegion.ROOT, downscale = false), controlsTop)
        assertSameFrame(
            h,
            "t = 0: Settings at alpha 0 over an unchanged Game",
            before,
            "push-before",
            t0,
        )
        h.advanceTo(SETTLED_MS)
        assertWithMessage("t = 256: Settings x (px)").that(settingsX(h)).isWithin(PX_TOL).of(0f)
        assertWithMessage("t = 256: the board composed").that(h.exists(BOARD_TAG)).isFalse()
        assertWithMessage("t = 256: the Game destination composed")
            .that(h.exists(screenTag(GameKey)))
            .isFalse()
        val at256 = h.capture(FilmRegion.ROOT, downscale = false)
        h.advanceFrames(SETTLE_FRAMES)
        val settled = h.capture(FilmRegion.ROOT, downscale = false)
        assertSameFrame(
            h,
            "t = 256: the settled Settings at alpha 1",
            settled,
            "push-settled",
            at256,
        )
    }

    /**
     * The main window's [actual] frame (the current one by default) equals [expected]; on a
     * mismatch both go to the test's report directory, [name] and `<name>-actual`.
     */
    private fun assertSameFrame(
        h: MotionHarness,
        what: String,
        expected: Bitmap,
        name: String,
        actual: Bitmap = h.capture(FilmRegion.ROOT, downscale = false),
    ) {
        val same = actual.sameAs(expected)
        if (!same) {
            h.writeReport(expected, name)
            h.writeReport(actual, "$name-actual")
        }
        assertWithMessage(what).that(same).isTrue()
    }

    /** The rows of [frame] above [top] (px): the header and the board. */
    private fun aboveControls(frame: Bitmap, top: Float): Bitmap =
        Bitmap.createBitmap(frame, 0, 0, frame.width, top.toInt())

    /** The Settings root's left edge in the root, in px (slide and layer transforms included). */
    private fun settingsX(h: MotionHarness): Float {
        val node = h.rule.onNodeWithTag(screenTag(SettingsKey)).fetchSemanticsNode()
        return node.layoutInfo.coordinates.localToRoot(Offset.Zero).x
    }

    private fun launch(motion: MotionPreference): MotionHarness =
        MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_MID_4), motion)

    private companion object {
        const val SETTLED_MS = 256L
        const val SETTLE_FRAMES = 30
        const val PX_TOL = 0.5f
        val STRIP_FRAMES = listOf(0L, 48, 96, 160, 256)
    }
}
