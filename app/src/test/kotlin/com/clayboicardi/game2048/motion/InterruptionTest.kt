package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Snap-then-animate (MOTION_SPEC.md §4.1, §4.2, §9.12; D-052, D-062) on `FIX_FILM` with LEFT at t =
 * 0 (its origin), RIGHT at 48 and LEFT at 96: every commit is applied at once, every persisting
 * tile starts its new slide exactly at its previous target, and an entrance that had not started
 * (alpha 0) snaps to scale 1 and alpha 1.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class InterruptionTest {
    @get:Rule val rule = MotionRule()

    /** AC-CONS-2 */
    @Test
    fun rapidInputSnapsInFlightTilesToTheirTargets() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), FULL)
        h.filmstrip("rapid_input", FilmRegion.BOARD, STRIP_FRAMES)

        val first = h.commitMove(Direction.LEFT)
        val boards = mutableListOf(checkNotNull(h.committed))
        val problems = ArrayList<String>()
        for ((direction, atMs) in LATER_MOVES) {
            while (h.now() < first + atMs) h.advanceFrames(1)
            val atCommit = h.snapshot()
            h.commitMove(direction)
            val board = checkNotNull(h.committed)
            problems += startProblems(h, boards.last(), board, atCommit, direction)
            boards += board
        }
        assertWithMessage("persisting tiles on each later commit's F1").that(problems).isEmpty()

        val directions = listOf(Direction.LEFT) + LATER_MOVES.map { it.first }
        boards.forEachIndexed { i, board ->
            assertWithMessage("move ${i + 1}: event direction")
                .that((board.event as BoardEvent.Moved).direction)
                .isEqualTo(directions[i])
        }
        assertWithMessage("successive move ids")
            .that(boards.zipWithNext { a, b -> b.moveId - a.moveId })
            .containsExactly(1L, 1L)
        assertWithMessage("all three moves applied")
            .that(h.peekGame().state.moveCount)
            .isEqualTo(GoldenFixtures.FIX_FILM.state.moveCount + directions.size)
        h.writeFilmstrip()
    }

    /**
     * On [board]'s F1: every tile that persists from [previous] starts its slide at its previous
     * target, and one whose alpha was 0 at the commit (a spawn or a merge reveal that had not
     * started; MOTION_SPEC.md §4.1 step 3, §4.2) has scale 1 and alpha 1.
     */
    private fun startProblems(
        h: MotionHarness,
        previous: BoardUi,
        board: BoardUi,
        atCommit: MotionSnapshot,
        direction: Direction,
    ): List<String> {
        val targets = previous.tiles.associateBy { it.id }
        val found = ArrayList<String>()
        for (tile in board.tiles) {
            val before = targets[tile.id] ?: continue
            val label = "$direction F1: tile ${tile.id} (${before.kind} → ${tile.kind})"
            if (tile.fromCol != before.col || tile.fromRow != before.row) {
                found += "$label: from ${tile.fromCol},${tile.fromRow} is not its previous target"
            }
            val drawn = h.tile(tile.id)
            if (drawn.x != before.col.toFloat() || drawn.y != before.row.toFloat()) {
                found += "$label: starts at ${drawn.x},${drawn.y}, not ${before.col},${before.row}"
            }
            val hidden = atCommit.tiles.firstOrNull { it.id == tile.id }?.alpha == 0f
            if (hidden && (drawn.scale != 1f || drawn.alpha != 1f)) {
                found += "$label, alpha 0 at the commit: scale ${drawn.scale}, alpha ${drawn.alpha}"
            }
        }
        return found
    }

    private companion object {
        val FULL = MotionPreference.FULL
        val LATER_MOVES = listOf(Direction.RIGHT to 48L, Direction.LEFT to 96L)
        val STRIP_FRAMES = (0L..560L step FRAME_MS).toList()
    }
}
