package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.motion.ProbeTile
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
 * New game (MOTION_SPEC.md §3.8, §9.5; D-182) on `FIX_MID_4`: with n outgoing tiles and `step =
 * min(15, 240 / n)`, outgoing tile i (reading order) shrinks and fades from i·step, and start tile
 * k spawns from n·step + 60·k. `FIX_MID_4` holds 13 tiles (DESIGN_SYSTEM.md §10.3), so the start
 * tiles begin at 195 and 255 ms; AC-NEW-1's frames 208/224 and 256/272 assume 14 tiles. The frames
 * here follow from the §3.8 rule and the fixture's own count.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NewGameCascadeTest {
    @get:Rule val rule = MotionRule()

    /** AC-NEW-1 */
    @Test
    fun outgoingTilesShrinkInTurnThenTheStartTilesSpawn() {
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_MID_4), FULL)
        h.filmstrip("new_game_cascade", FilmRegion.BOARD, STRIP_FRAMES)
        val previous = h.vm.board.value.tiles.size
        h.commitNewGame()
        val board = checkNotNull(h.committed)
        val n = board.size.n
        assertWithMessage("the new board's kinds")
            .that(board.tiles.map { it.kind }.toSet())
            .containsExactly(TileKind.Static)
        val outgoing = h.snapshot().tiles.filter { it.outgoing }.sortedBy { readingIndex(it, n) }
        assertWithMessage("outgoing tiles").that(outgoing).hasSize(previous)
        val step =
            minOf(
                GameMotion.Board.NEW_GAME_STEP_MS,
                GameMotion.Board.NEW_GAME_WINDOW_MS / outgoing.size,
            )
        val incoming = board.tiles.sortedBy { it.row * n + it.col }
        val spawnAt =
            incoming.indices.map {
                outgoing.size * step + GameMotion.Board.NEW_GAME_SPAWN_GAP_MS * it
            }

        while (h.t() <= IDLE_MS) {
            outgoing.forEachIndexed { i, tile -> checkOutgoing(h, tile, i * step.toLong()) }
            incoming.forEachIndexed { k, tile -> checkIncoming(h, tile.id, spawnAt[k].toLong()) }
            h.advanceFrames(1)
        }
        h.advanceTo(IDLE_MS)
        assertWithMessage("t = 624: running").that(h.snapshot().isAnyRunning).isFalse()
        h.writeFilmstrip()
    }

    /** AC-NEW-2 */
    @Test
    fun scoreIsZeroOnTheOriginAndTheEndIsStatic() {
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_MID_4), FULL)

        h.commitNewGame()
        assertWithMessage("F1: displayed score")
            .that(checkNotNull(h.snapshot().header).displayedScore)
            .isEqualTo(0)
        h.advanceTo(IDLE_MS)
        h.settle()
        val live = h.capture(FilmRegion.ROOT, downscale = false)
        assertWithMessage("the final frame equals the static render (I3)")
            .that(live.sameAs(h.staticRender()))
            .isTrue()
    }

    /** Outgoing tile [tile] starts at [startMs]: untouched before it, shrinking a frame after. */
    private fun checkOutgoing(h: MotionHarness, tile: ProbeTile, startMs: Long) {
        val t = h.t()
        val drawn = h.snapshot().tiles.firstOrNull { it.id == tile.id && it.outgoing }
        val firstFrame = (startMs + FRAME_MS - 1) / FRAME_MS * FRAME_MS
        if (t < startMs) {
            val label = "t = $t: outgoing tile ${tile.id} (start $startMs)"
            assertWithMessage("$label is drawn").that(drawn).isNotNull()
            assertWithMessage("$label scale").that(checkNotNull(drawn).scale).isEqualTo(1f)
        } else if (t == firstFrame + FRAME_MS) {
            val label = "t = $t: outgoing tile ${tile.id} (start $startMs)"
            val scale = checkNotNull(drawn) { "$label is not drawn" }.scale
            assertWithMessage("$label scale").that(scale).isLessThan(1f)
            assertWithMessage("$label scale value")
                .that(scale)
                .isWithin(Tol.SCALE)
                .of(Curves.value(delayedSpec(FADE, startMs.toInt()), 1f, SHRINK, t))
        }
    }

    /** Start tile [id] spawns from [startMs]: alpha 0 up to it, above 0 on the next frame. */
    private fun checkIncoming(h: MotionHarness, id: Long, startMs: Long) {
        val t = h.t()
        val lastHidden = startMs / FRAME_MS * FRAME_MS
        val label = "t = $t: start tile $id (start $startMs)"
        if (t == lastHidden) {
            assertWithMessage("$label alpha").that(h.tile(id).alpha).isEqualTo(0f)
        } else if (t == lastHidden + FRAME_MS) {
            assertWithMessage("$label alpha").that(h.tile(id).alpha).isGreaterThan(0f)
        }
    }

    private fun readingIndex(tile: ProbeTile, n: Int): Int = tile.y.toInt() * n + tile.x.toInt()

    private companion object {
        val FULL = MotionPreference.FULL
        const val IDLE_MS = 624L
        const val SHRINK = GameMotion.Board.NEW_GAME_SHRINK_SCALE
        val FADE = GameMotion.fastEffects<Float>()
        val STRIP_FRAMES = listOf(0L, 48, 112, 176, 224, 288, 352, 480, 624)
    }
}
