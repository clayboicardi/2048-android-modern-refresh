package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.snap
import androidx.compose.ui.FrameRateCategory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The accepted move under Full (MOTION_SPEC.md §3.1, §3.2, §9.1) on `FIX_FILM` LEFT: the 4 slides
 * from column 3 to 1, the two 2s merge at column 0 (revealed at 80, pop v0 6), and a 2 spawns at
 * row 1, column 0 after D = 90 ms. Expected curve values come from the token specs (D-210); the
 * MOTION_SPEC.md reference numbers are in the comments.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MoveTimelineTest {
    @get:Rule val rule = MotionRule()

    /** AC-MOVE-1 */
    @Test
    fun originShowsEveryTileAtItsFromCell() {
        val h = film()
        val board = checkNotNull(h.committed)
        val film = FilmLeft(board)

        for (tile in board.tiles) {
            val drawn = h.tile(tile.id)
            assertWithMessage("t = 0: tile ${tile.id} (${tile.kind}) x")
                .that(drawn.x)
                .isEqualTo(tile.fromCol.toFloat())
            assertWithMessage("t = 0: tile ${tile.id} (${tile.kind}) y")
                .that(drawn.y)
                .isEqualTo(tile.fromRow.toFloat())
        }
        assertWithMessage("t = 0: merged alpha").that(h.tile(film.merged.id).alpha).isEqualTo(0f)
        val spawn = h.tile(film.spawn.id)
        assertWithMessage("t = 0: spawn scale").that(spawn.scale).isWithin(Tol.SCALE).of(0.6f)
        assertWithMessage("t = 0: spawn alpha").that(spawn.alpha).isEqualTo(0f)
    }

    /** AC-MOVE-2, AC-MOVE-6 */
    @Test
    fun slideFollowsTheSlideSpringAndEndsOnTheEngineCells() {
        val h = film()
        val board = checkNotNull(h.committed)
        val moved = FilmLeft(board).moved

        // Reference x: 2.749 at 16, 1.860 at 48, 1.305 at 80.
        for (t in listOf(16L, 48L, 80L)) {
            h.advanceTo(t)
            assertWithMessage("t = $t: the 4's x")
                .that(h.tile(moved.id).x)
                .isWithin(Tol.POSITION)
                .of(Curves.value(GameMotion.slide(), moved.fromCol.toFloat(), 1f, t))
        }

        h.advanceTo(SETTLED_MS)
        for (tile in board.tiles.filter { it.kind != TileKind.Ghost }) {
            val drawn = h.tile(tile.id)
            assertWithMessage("t = 288: tile ${tile.id} x")
                .that(drawn.x)
                .isEqualTo(tile.col.toFloat())
            assertWithMessage("t = 288: tile ${tile.id} y")
                .that(drawn.y)
                .isEqualTo(tile.row.toFloat())
        }
    }

    /** AC-MOVE-3 */
    @Test
    fun mergedTileIsRevealedAt80AndPops() {
        val h = film()
        val merged = FilmLeft(checkNotNull(h.committed)).merged
        val impulse = expectedPopImpulse(merged.exp, h.boardGeometry())

        h.advanceTo(64)
        assertWithMessage("t = 64: merged alpha").that(h.tile(merged.id).alpha).isEqualTo(0f)
        h.advanceTo(GameMotion.MERGE_REVEAL_MS.toLong())
        val revealed = h.tile(merged.id)
        assertWithMessage("t = 80: merged alpha").that(revealed.alpha).isEqualTo(1f)
        assertWithMessage("t = 80: merged scale").that(revealed.scale).isWithin(Tol.SCALE).of(1f)
        // Reference scales: 1.0716, 1.1021, 1.1039 (v0 = 6).
        for (t in listOf(96L, 112L, 128L)) {
            h.advanceTo(t)
            assertWithMessage("t = $t: merged scale")
                .that(h.tile(merged.id).scale)
                .isWithin(Tol.SCALE)
                .of(expectedPopScale(t, impulse))
        }
    }

    /** AC-MOVE-4 */
    @Test
    fun spawnStartsAfterItsDelay() {
        val h = film()
        val spawn = FilmLeft(checkNotNull(h.committed)).spawn
        val delay = GameMotion.SPAWN_DELAY_MS

        h.advanceTo(GameMotion.MERGE_REVEAL_MS.toLong())
        assertWithMessage("t = 80: spawn alpha").that(h.tile(spawn.id).alpha).isEqualTo(0f)
        h.advanceTo(SPAWN_FRAME_MS)
        val drawn = h.tile(spawn.id)
        // Reference: alpha 0.0537, scale 0.6054 (6 ms into the spawn).
        assertWithMessage("t = 96: spawn alpha")
            .that(drawn.alpha)
            .isWithin(Tol.ALPHA)
            .of(Curves.value(spawnAlpha(delay), 0f, 1f, SPAWN_FRAME_MS))
        assertWithMessage("t = 96: spawn scale")
            .that(drawn.scale)
            .isWithin(Tol.SCALE)
            .of(Curves.value(spawnScale(delay), 0.6f, 1f, SPAWN_FRAME_MS))
    }

    /** AC-MOVE-5 */
    @Test
    fun ghostsHideAt200() {
        val h = film()
        val ghosts = FilmLeft(checkNotNull(h.committed)).ghosts
        val hide = snap<Float>(GameMotion.GHOST_REMOVE_MS)

        for (t in listOf(192L, 208L)) {
            h.advanceTo(t)
            for (ghost in ghosts) {
                assertWithMessage("t = $t: ghost ${ghost.id} alpha")
                    .that(h.tile(ghost.id).alpha)
                    .isEqualTo(Curves.value(hide, 1f, 0f, t))
            }
        }
    }

    /** AC-MOVE-7 */
    @Test
    fun settlesBy432AndVotesTheFrameRate() {
        val h = film(strip = true)

        h.advanceTo(16)
        assertWithMessage("t = 16: frame rate")
            .that(checkNotNull(h.snapshot().board).frameRate)
            .isEqualTo(FrameRateCategory.High)
        h.advanceTo(IDLE_MS)
        // "Nothing is running" is the board's (MOTION_SPEC.md §3.1: no board animation runs at
        // 432): the "+N" float of this move runs to t = 746 by §3.4, so the probe's isAnyRunning,
        // which ORs every owner, stays true here. BoardView's own vote and the tiles' end values
        // are the board's state.
        val idle = h.snapshot()
        assertWithMessage("t = 432: frame rate")
            .that(checkNotNull(idle.board).frameRate)
            .isEqualTo(FrameRateCategory.Default)
        for (tile in checkNotNull(h.committed).tiles) {
            val drawn = h.tile(tile.id)
            val alpha = if (tile.kind == TileKind.Ghost) 0f else 1f
            assertWithMessage("t = 432: tile ${tile.id} (${tile.kind}) end values")
                .that(
                    listOf(
                        drawn.x,
                        drawn.y,
                        drawn.scale,
                        drawn.alpha,
                        drawn.sheen,
                        drawn.glowBoost,
                    ),
                )
                .containsExactly(tile.col.toFloat(), tile.row.toFloat(), 1f, alpha, 0f, 0f)
                .inOrder()
        }
        h.writeFilmstrip()
    }

    private fun film(strip: Boolean = false): MotionHarness {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), FULL)
        if (strip) h.filmstrip("move_merge_spawn", FilmRegion.BOARD, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)
        return h
    }

    private companion object {
        val FULL = MotionPreference.FULL
        const val SETTLED_MS = 288L
        const val SPAWN_FRAME_MS = 96L
        const val IDLE_MS = 432L
        val STRIP_FRAMES = listOf(0L, 16, 32, 48, 64, 80, 96, 128, 160, 208, 256, 320, 432)

        fun spawnAlpha(delay: Int) =
            delayedSpec(GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD), delay)

        fun spawnScale(delay: Int) =
            delayedSpec(GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD), delay)
    }
}
