package com.clayboicardi.game2048.motion

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.motion.ProbeHeader
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The board-size switch (MOTION_SPEC.md §3.10, §9.5; D-007, D-182): `BoardView` fades the old board
 * out (`fastEffects`) and the new one in (`defaultEffects`) through `AnimatedContent`, with both
 * composed meanwhile; score and best change at once. A composed board is counted by its fixed cell
 * semantics layer (D-080: one node with `CollectionItemInfo` per cell).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SizeSwitchTest {
    @get:Rule val rule = MotionRule()

    /** AC-SIZE-1 */
    @Test
    fun boardsCrossfadeAndScoresChangeOnF0() {
        val fixture =
            GameFixture(
                GoldenFixtures.FIX_MID_4,
                saved = listOf(GoldenFixtures.FIX_MID_3),
                records = GoldenFixtures.FIX_RECORDS,
            )
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL)
        val four = header(h)
        assertWithMessage("before: score").that(four.displayedScore).isEqualTo(MID_4_SCORE)
        assertWithMessage("before: best").that(four.displayedBest).isEqualTo(MID_4_BEST)
        h.filmstrip("size_switch", FilmRegion.BOARD, STRIP_FRAMES)

        h.commitSizeSwitch(GoldenFixtures.FIX_MID_3.state.size.n)

        assertWithMessage("the switch's event")
            .that(checkNotNull(h.committed).event)
            .isEqualTo(BoardEvent.SizeSwitched)
        val f0 = checkNotNull(checkNotNull(h.f0).header) { "F0: no GameHeader" }
        assertWithMessage("F0: score").that(f0.displayedScore).isEqualTo(MID_3_SCORE)
        assertWithMessage("F0: best").that(f0.displayedBest).isEqualTo(MID_3_BEST)
        h.advanceTo(BOTH_MS)
        assertWithMessage("t = 48: cells of both boards").that(cellCount(h)).isEqualTo(16 + 9)
        h.advanceTo(NEW_ONLY_MS)
        assertWithMessage("t = 208: cells of the 3×3 board only").that(cellCount(h)).isEqualTo(9)
        h.writeFilmstrip()
    }

    private fun cellCount(h: MotionHarness): Int =
        h.rule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .size

    private fun header(h: MotionHarness): ProbeHeader =
        checkNotNull(h.snapshot().header) { "no GameHeader is composed" }

    private companion object {
        const val MID_4_SCORE = 10_220L
        const val MID_4_BEST = 24_680L
        const val MID_3_SCORE = 1184L
        const val MID_3_BEST = 2100L
        const val BOTH_MS = 48L
        const val NEW_ONLY_MS = 208L
        val STRIP_FRAMES = listOf(0L, 48, 96, 208)
    }
}
