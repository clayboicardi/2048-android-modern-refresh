package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Undo (MOTION_SPEC.md §3.9, §9.5; D-064, D-182) on `FIX_FILM` after LEFT has settled: only cells
 * whose tile id changed crossfade with `fastEffects` from the origin, positions never move, and the
 * Undo icon turns −360°. `FIX_RECORDS` is seeded so that the records already hold an undo and the
 * "Undo used" snackbar (D-186) stays away from the board.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class UndoCrossfadeTest {
    @get:Rule val rule = MotionRule()

    /** AC-UNDO-1 */
    @Test
    fun changedCellsCrossfadeAndNothingMoves() {
        val h = settledFilm()
        val before = h.vm.board.value.tiles.mapTo(HashSet()) { it.id }
        h.filmstrip("undo_crossfade", FilmRegion.BOARD, STRIP_FRAMES)
        h.recompositions.reset()
        h.commitUndo()
        val board = checkNotNull(h.committed)
        assertWithMessage("the undo board's kinds")
            .that(board.tiles.map { it.kind }.toSet())
            .containsExactly(TileKind.Static)
        val kept = board.tiles.map { it.id }.filter { it in before }.toSet()
        val incoming = board.tiles.map { it.id }.filter { it !in kept }
        val positions = h.snapshot().tiles.associate { (it.id to it.outgoing) to (it.x to it.y) }

        while (h.t() <= STRIP_FRAMES.last()) {
            val snapshot = h.snapshot()
            for (tile in snapshot.tiles) {
                assertWithMessage("t = ${h.t()}: tile ${tile.id} position")
                    .that(tile.x to tile.y)
                    .isEqualTo(positions[tile.id to tile.outgoing] ?: (tile.x to tile.y))
            }
            for (id in kept) {
                assertWithMessage("t = ${h.t()}: kept tile $id alpha")
                    .that(h.tile(id).alpha)
                    .isEqualTo(1f)
            }
            if (h.t() in SAMPLES) {
                // Reference: incoming 0.2592 / 0.5865 / 0.9043; outgoing 0.7408 / 0.4135 / 0.0957.
                val inAlpha = Curves.value(CROSSFADE, 0f, 1f, h.t())
                for (id in incoming) {
                    assertWithMessage("t = ${h.t()}: incoming $id alpha")
                        .that(h.tile(id).alpha)
                        .isWithin(Tol.ALPHA)
                        .of(inAlpha)
                }
                val out = snapshot.tiles.filter { it.outgoing }
                assertWithMessage("t = ${h.t()}: outgoing tiles").that(out).isNotEmpty()
                for (tile in out) {
                    assertWithMessage("t = ${h.t()}: outgoing ${tile.id} alpha")
                        .that(tile.alpha)
                        .isWithin(Tol.ALPHA)
                        .of(Curves.value(CROSSFADE, 1f, 0f, h.t()))
                }
            }
            h.advanceFrames(1)
        }
        assertWithMessage("kept tiles recomposed")
            .that(h.recompositions.tiles.keys.filter { it in kept })
            .isEmpty()
        h.writeFilmstrip()
    }

    /** AC-UNDO-2 */
    @Test
    fun onlyTheUndoIconRunsAt144() {
        val h = settledFilm()
        h.commitUndo()
        val board = checkNotNull(h.committed)

        h.advanceTo(QUIET_MS)
        val quiet = h.snapshot()
        assertWithMessage("t = 144: outgoing tiles")
            .that(quiet.tiles.filter { it.outgoing })
            .isEmpty()
        for (tile in board.tiles) {
            assertWithMessage("t = 144: tile ${tile.id} alpha")
                .that(h.tile(tile.id).alpha)
                .isEqualTo(1f)
        }
        assertWithMessage("t = 144: floats").that(quiet.floats).isEmpty()
        val turning = checkNotNull(quiet.chrome) { "no chrome reader" }.undoIconRotationDeg
        assertWithMessage("t = 144: the Undo icon is mid-turn")
            .that(turning)
            .isIn(Range.open(GameMotion.Board.UNDO_ICON_ROTATION_DEG, 0f))

        h.advanceTo(TURNED_MS)
        val turned = checkNotNull(h.snapshot().chrome).undoIconRotationDeg
        assertWithMessage("t = 320: the icon turned a full −360° (≡ 0 mod 360)")
            .that(offFullTurn(turned))
            .isAtMost(ROTATION_TOLERANCE)
        assertWithMessage("t = 320: running").that(h.snapshot().isAnyRunning).isFalse()
        h.advanceFrames(1)
        assertWithMessage("t = 336: the icon is still")
            .that(checkNotNull(h.snapshot().chrome).undoIconRotationDeg)
            .isEqualTo(turned)
    }

    /** `FIX_FILM` after LEFT has settled. */
    private fun settledFilm(): MotionHarness {
        MotionFixtures.FILM_LEFT.verify()
        val fixture = GameFixture(GoldenFixtures.FIX_FILM, records = GoldenFixtures.FIX_RECORDS)
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL)
        h.commitMove(Direction.LEFT)
        h.advanceTo(SETTLED_MS)
        h.settle()
        return h
    }

    /** Degrees from the nearest whole turn. */
    private fun offFullTurn(degrees: Float): Float {
        val r = abs(degrees % FULL_TURN)
        return minOf(r, FULL_TURN - r)
    }

    private companion object {
        const val SETTLED_MS = 432L
        const val QUIET_MS = 144L
        const val TURNED_MS = 320L
        const val FULL_TURN = 360f
        const val ROTATION_TOLERANCE = 0.5f
        val SAMPLES = listOf(16L, 32L, 64L)
        val STRIP_FRAMES = listOf(0L, 16, 32, 64, 128, 144)
        val CROSSFADE = GameMotion.fastEffects<Float>(GameMotion.ALPHA_THRESHOLD)
    }
}
