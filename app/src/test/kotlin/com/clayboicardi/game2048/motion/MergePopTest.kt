package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.board.TileUi
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The merge pop (MOTION_SPEC.md §3.3, §9.1): an impulse tier per merged tile by its own value, the
 * sheen and glow pulse with the flash limiter, and the neighbour cap. Expected scales come from
 * `delayedSpec(popSpatial, 80)` with the tier's initial velocity (D-210).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MergePopTest {
    @get:Rule val rule = MotionRule()

    /** AC-MOVE-8 */
    @Test
    fun eachMergedTilePopsByItsOwnTier() {
        MotionFixtures.POP_TIERS_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_POP_TIERS_4), FULL)
        h.filmstrip("pop_tiers", FilmRegion.BOARD, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)
        val geometry = h.boardGeometry()

        h.advanceTo(POP_SAMPLE_MS)
        // Reference scales at t = 128: 1.1039 (64), 1.1385 (512), 1.1732 (1024), 1.2078 (4096).
        for (merged in merged(h)) {
            assertWithMessage("t = 128: scale of the merged ${1L shl merged.exp}")
                .that(h.tile(merged.id).scale)
                .isWithin(Tol.SCALE)
                .of(expectedPopScale(POP_SAMPLE_MS, expectedPopImpulse(merged.exp, geometry)))
        }
        h.writeFilmstrip()
    }

    /** AC-MOVE-9 */
    @Test
    fun sheenAndGlowPulseByMergedValue() {
        MotionFixtures.POP_TIERS_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_POP_TIERS_4), FULL)
        h.commitMove(Direction.LEFT)

        h.advanceTo(GameMotion.MERGE_REVEAL_MS.toLong())
        val expected =
            mapOf(
                EXP_64 to (0f to 0f),
                9 to (SHEEN to GLOW),
                10 to (SHEEN to GLOW),
                12 to (SHEEN to 0f),
            )
        for (merged in merged(h)) {
            val (sheen, glow) = checkNotNull(expected[merged.exp])
            val drawn = h.tile(merged.id)
            assertWithMessage("t = 80: sheen of the merged ${1L shl merged.exp}")
                .that(drawn.sheen)
                .isWithin(Tol.ALPHA)
                .of(sheen)
            // Classic super tiles (>= 4096) are flat (D-078): no glow, so no pulse.
            assertWithMessage("t = 80: glow boost of the merged ${1L shl merged.exp}")
                .that(drawn.glowBoost)
                .isWithin(Tol.ALPHA)
                .of(glow)
        }
    }

    /** AC-MOVE-10 */
    @Test
    fun flashLimiterStopsTheFourthFlashButNotItsPop() {
        val left = Game.move(MotionFixtures.FIX_FLASH_4.state, Direction.LEFT) as MoveOutcome.Moved
        val up = Game.move(left.state, Direction.UP) as MoveOutcome.Moved
        assertWithMessage("ENGINE_SPAWN_CHECK FIX_FLASH_4 LEFT merges")
            .that(left.mergeCount)
            .isEqualTo(3)
        assertWithMessage("ENGINE_SPAWN_CHECK FIX_FLASH_4 UP merges")
            .that(up.mergeCount)
            .isEqualTo(1)
        assertWithMessage("ENGINE_SPAWN_CHECK FIX_FLASH_4 UP max")
            .that(up.maxMergedExp)
            .isEqualTo(9)
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_FLASH_4), FULL)

        h.commitMove(Direction.LEFT)
        h.advanceTo(GameMotion.MERGE_REVEAL_MS.toLong())
        for (merged in merged(h)) {
            assertWithMessage("LEFT t = 80: sheen of a merged 256 (one of three flashes)")
                .that(h.tile(merged.id).sheen)
                .isWithin(Tol.ALPHA)
                .of(SHEEN)
        }
        h.advanceTo(SECOND_MOVE_MS)
        h.commitMove(Direction.UP)
        val fourth = merged(h).single()
        h.advanceTo(GameMotion.MERGE_REVEAL_MS.toLong())
        val limited = h.tile(fourth.id)
        assertWithMessage("UP t = 80: the fourth flash's sheen").that(limited.sheen).isEqualTo(0f)
        assertWithMessage("UP t = 80: the fourth flash's glow")
            .that(limited.glowBoost)
            .isEqualTo(0f)
        h.advanceTo(POP_SAMPLE_MS)
        assertWithMessage("UP t = 128: the 512 still pops")
            .that(h.tile(fourth.id).scale)
            .isWithin(Tol.SCALE)
            .of(expectedPopScale(POP_SAMPLE_MS, expectedPopImpulse(fourth.exp, h.boardGeometry())))
    }

    /** AC-MOVE-12 */
    @Test
    @Config(qualifiers = "+w800dp-h1280dp")
    fun popOfAWideTileStaysWithinTheGap() {
        val engine =
            Game.move(MotionFixtures.FIX_POP_CAP_3.state, Direction.LEFT) as MoveOutcome.Moved
        // The strongest pop 3×3 play makes: the 1024 tier (D-183) of the largest 3×3 tile (D-270).
        assertWithMessage("ENGINE_SPAWN_CHECK FIX_POP_CAP_3 LEFT")
            .that(engine.maxMergedExp)
            .isEqualTo(10)
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_POP_CAP_3), FULL)
        val geometry = h.boardGeometry()
        assertWithMessage("the 3×3 board is 720 dp at w800dp-h1280dp")
            .that(geometry.boardPx / rule.compose.density.density)
            .isWithin(1f)
            .of(BOARD_DP)
        h.commitMove(Direction.LEFT)
        val merged = merged(h).single()

        var peak = 0f
        while (h.t() < POP_END_MS) {
            h.advanceFrames(1)
            peak = maxOf(peak, h.tile(merged.id).scale)
        }
        val bound = 1f + 2f * geometry.gapPx / geometry.tilePx
        assertWithMessage("peak scale ≤ 1 + 2·gap/tile").that(peak).isAtMost(bound + Tol.SCALE)
        assertWithMessage("peak scale ≤ 1.188").that(peak).isAtMost(CAP_BOUND + Tol.SCALE)
    }

    /** The merged tiles of the latest commit. */
    private fun merged(h: MotionHarness): List<TileUi> =
        checkNotNull(h.committed).tiles.filter { it.kind == TileKind.Merged }

    private companion object {
        val FULL = MotionPreference.FULL
        const val POP_SAMPLE_MS = 128L
        const val SECOND_MOVE_MS = 160L
        const val POP_END_MS = 368L
        const val EXP_64 = 6
        const val SHEEN = GameMotion.Move.SHEEN_ALPHA
        const val GLOW = GameMotion.Move.GLOW_PULSE_BOOST
        const val BOARD_DP = 720f
        const val CAP_BOUND = 1.188f
        val STRIP_FRAMES = listOf(64L, 80, 96, 112, 128, 160, 208, 288, 432)
    }
}
