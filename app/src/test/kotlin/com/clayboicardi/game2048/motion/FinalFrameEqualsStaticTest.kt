package com.clayboicardi.game2048.motion

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * I3 for each board event (MOTION_SPEC.md §4.3, §9.12): after the event's settle bound, the root
 * bitmap equals the static render of the same state. The static render is a recreation of the
 * content after the settle bound (`MotionHarness.staticRender`), which composes the ViewModel's
 * settled `Restored` board: the harness's stand-in for AC-CONS-3's `presenter.reemitStatic()`. Win
 * and game over are M3 part 2 (M3.11, D-233). `FIX_RECORDS` is seeded so that no "Undo used"
 * snackbar (D-186) sits over the undo case's final frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FinalFrameEqualsStaticTest {
    @get:Rule val rule = MotionRule()

    /** AC-CONS-3 */
    @Test
    fun moveEndsOnTheStaticFrame() {
        val h = launch(GameFixture(MotionFixtures.FIX_SLIDE_4))
        h.commitMove(Direction.RIGHT)
        assertSettlesToStatic(h, "move", MOVE_SETTLED_MS)
    }

    /** AC-CONS-3 */
    @Test
    fun mergeEndsOnTheStaticFrame() {
        MotionFixtures.FILM_LEFT.verify()
        val h = launch(GameFixture(GoldenFixtures.FIX_FILM))
        h.commitMove(Direction.LEFT)
        assertSettlesToStatic(h, "merge", MOVE_SETTLED_MS)
    }

    /** AC-CONS-3 */
    @Test
    fun undoEndsOnTheStaticFrame() {
        val h = launch(GameFixture(GoldenFixtures.FIX_FILM, records = GoldenFixtures.FIX_RECORDS))
        h.commitMove(Direction.LEFT)
        h.advanceTo(MOVE_SETTLED_MS)
        h.settle()
        h.commitUndo()
        assertSettlesToStatic(h, "undo", UNDO_SETTLED_MS)
    }

    /** AC-CONS-3 */
    @Test
    fun newGameEndsOnTheStaticFrame() {
        val h = launch(GameFixture(GoldenFixtures.FIX_MID_4))
        h.commitNewGame()
        assertSettlesToStatic(h, "new game", NEW_GAME_SETTLED_MS)
    }

    /** AC-CONS-3 */
    @Test
    fun sizeSwitchEndsOnTheStaticFrame() {
        val fixture =
            GameFixture(
                GoldenFixtures.FIX_MID_4,
                saved = listOf(GoldenFixtures.FIX_MID_3),
                records = GoldenFixtures.FIX_RECORDS,
            )
        val h = launch(fixture)
        h.commitSizeSwitch(GoldenFixtures.FIX_MID_3.state.size.n)
        assertSettlesToStatic(h, "size switch", SIZE_SETTLED_MS)
    }

    private fun launch(fixture: GameFixture): MotionHarness =
        MotionHarness.launch(rule, fixture, MotionPreference.FULL)

    private fun assertSettlesToStatic(h: MotionHarness, event: String, settledMs: Long) {
        h.advanceTo(settledMs)
        h.settle()
        val live = h.capture(FilmRegion.ROOT, downscale = false)
        val static = h.staticRender()
        val same = live.sameAs(static)
        if (!same) {
            write(live, "$event-live")
            write(static, "$event-static")
        }
        assertWithMessage("$event: the settled root bitmap equals the static render")
            .that(same)
            .isTrue()
    }

    /** A mismatching pair goes next to the filmstrips, for inspection. */
    private fun write(bitmap: Bitmap, name: String) {
        val dir = File("build/reports/filmstrips/FinalFrameEqualsStaticTest").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }
    }

    private companion object {
        const val MOVE_SETTLED_MS = 432L
        const val UNDO_SETTLED_MS = 320L
        const val NEW_GAME_SETTLED_MS = 624L
        const val SIZE_SETTLED_MS = 224L
        const val PNG_QUALITY = 100
    }
}
