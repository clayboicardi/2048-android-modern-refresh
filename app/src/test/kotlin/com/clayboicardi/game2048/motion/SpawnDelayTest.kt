package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.TileKind
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The spawn delay by the move's longest slide (MOTION_SPEC.md §3.1, D-183) on `FIX_LONG_SLIDE_8`
 * RIGHT: a 7-cell slide gives D = 115 ms, which keeps the spawn right behind the sliding tile clear
 * of it on every frame (invariant I1).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SpawnDelayTest {
    @get:Rule val rule = MotionRule()

    /** AC-MOVE-11 */
    @Test
    fun longSlideDelaysTheSpawnAndKeepsItClear() {
        MotionFixtures.LONG_SLIDE_RIGHT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_LONG_SLIDE_8), FULL)
        h.filmstrip("long_slide_8x8", FilmRegion.BOARD, STRIP_FRAMES)
        h.commitMove(Direction.RIGHT)
        val board = checkNotNull(h.committed)
        val moved = board.event as BoardEvent.Moved
        assertWithMessage("the longest slide").that(moved.maxSlideCells).isEqualTo(LONGEST_SLIDE)
        val pair = board.tiles.filter { it.kind == TileKind.Spawned || it.kind == TileKind.Moved }
        val ids = pair.mapTo(HashSet()) { it.id }
        assertWithMessage("the slider and the spawn").that(ids).hasSize(2)
        val spawn = pair.single { it.kind == TileKind.Spawned }
        val geometry = h.boardGeometry()
        val side = geometry.tilePx / (geometry.tilePx + geometry.gapPx)

        while (h.t() <= LAST_MS) {
            val tiles = h.snapshot().tiles.filter { it.id in ids }
            assertWithMessage("t = ${h.t()}: I1 between the spawn and the sliding tile")
                .that(Invariants.overlaps(tiles, board, side))
                .isEmpty()
            when (h.t()) {
                BEFORE_DELAY_MS ->
                    assertWithMessage("t = 112: spawn alpha (D = 115)")
                        .that(h.tile(spawn.id).alpha)
                        .isEqualTo(0f)
                AFTER_DELAY_MS ->
                    assertWithMessage("t = 128: spawn alpha")
                        .that(h.tile(spawn.id).alpha)
                        .isGreaterThan(0f)
            }
            h.advanceFrames(1)
        }
        h.writeFilmstrip()
    }

    private companion object {
        val FULL = MotionPreference.FULL
        const val LONGEST_SLIDE = 7
        const val BEFORE_DELAY_MS = 112L
        const val AFTER_DELAY_MS = 128L
        const val LAST_MS = 448L
        val STRIP_FRAMES = listOf(0L, 48, 96, 112, 128, 144, 160, 240, 448)
    }
}
