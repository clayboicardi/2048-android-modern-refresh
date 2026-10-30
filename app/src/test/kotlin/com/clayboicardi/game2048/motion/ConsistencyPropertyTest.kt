package com.clayboicardi.game2048.motion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.game.motion.ParticleRandom
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The visual-consistency invariants I1–I4 (MOTION_SPEC.md §4.3, §9.12) over 20 seeded sequences of
 * 15 random directions with random gaps, 10 on 4×4 and 10 on 8×8. One composition plays them all
 * (one compose rule per test method): each sequence after the first starts from a new game, the
 * first 8×8 one from a switch to the 8×8 board, and each waits until nothing runs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ConsistencyPropertyTest {
    @get:Rule val rule = MotionRule()

    /** Every violation found, so that one run reports them all. */
    private val violations = ArrayList<String>()

    /** AC-CONS-1 */
    @Test
    fun invariantsHoldOverSeededSequences() {
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FRESH_4), FULL)
        for (i in 0 until SEQUENCES) {
            val n = if (i < SEQUENCES / 2) SMALL else LARGE
            prepare(h, i, n)
            play(h, i)
        }
        assertWithMessage("I1–I4 violations (${violations.size}; the first $REPORTED shown)")
            .that(violations.take(REPORTED))
            .isEmpty()
    }

    private fun prepare(h: MotionHarness, i: Int, n: Int) {
        when {
            i == 0 -> return
            h.vm.board.value.size.n != n -> h.commitSizeSwitch(n)
            else -> h.commitNewGame()
        }
        h.settle()
    }

    /** Sequence [i]: 15 moves from `ParticleRandom(0x2048 + i)`, then I3 after t = 448. */
    private fun play(h: MotionHarness, i: Int) {
        val random = ParticleRandom(SEED + i)
        val geometry = h.boardGeometry()
        val side = geometry.tilePx / (geometry.tilePx + geometry.gapPx)
        var change = h.originMs
        repeat(MOVES) { m ->
            val direction = Direction.entries[pick(random, Direction.entries.size)]
            val gap = GAPS_MS[pick(random, GAPS_MS.size)]
            val label = "sequence $i (${h.vm.board.value.size.n}×), move $m $direction"
            if (h.tryMove(direction)) {
                change = h.originMs
                checkNoStaleTiles(h, checkNotNull(h.f0), "$label F0")
            }
            // Frames are labelled with their time from the origin of the change they show.
            checkFrame(h, side, "$label, change t = ${h.now() - change}")
            val until = h.now() + gap
            while (h.now() < until) {
                h.advanceFrames(1)
                checkFrame(h, side, "$label, change t = ${h.now() - change}")
            }
        }
        val end = h.now() + SETTLE_MS
        while (h.now() < end) {
            h.advanceFrames(1)
            checkFrame(h, side, "sequence $i after its last move, change t = ${h.now() - change}")
        }
        h.settle()
        checkFinalFrame(h, "sequence $i")
    }

    /** I1 and I2 on the current frame of a `Moved` change. */
    private fun checkFrame(h: MotionHarness, side: Float, label: String) {
        val board = h.committed ?: return
        val moved = board.event as? BoardEvent.Moved ?: return
        val tiles = h.snapshot().tiles
        Invariants.overlaps(tiles, board, side).forEach { violations += "$label: I1 $it" }
        Invariants.orderViolations(tiles, board, moved.direction).forEach {
            violations += "$label: I2 $it"
        }
    }

    /** I4 on a change's F0: only tiles of the new board are drawn. */
    private fun checkNoStaleTiles(h: MotionHarness, f0: MotionSnapshot, label: String) {
        val ids = checkNotNull(h.committed).tiles.mapTo(HashSet()) { it.id }
        val stale = f0.tiles.filter { !it.outgoing && it.id !in ids }.map { it.id }
        if (stale.isNotEmpty()) violations += "$label: I4 tiles of an earlier change $stale"
    }

    /** I3: every tile at its engine cell exactly, and the board equals its static render. */
    private fun checkFinalFrame(h: MotionHarness, label: String) {
        val board = checkNotNull(h.committed)
        for (tile in board.tiles.filter { it.kind != TileKind.Ghost }) {
            val drawn = h.tile(tile.id)
            if (drawn.x != tile.col.toFloat() || drawn.y != tile.row.toFloat()) {
                violations += "$label: I3 tile ${tile.id} at ${drawn.x},${drawn.y}"
            }
        }
        val live = h.capture(FilmRegion.BOARD)
        if (!live.sameAs(h.staticRender(FilmRegion.BOARD))) {
            violations += "$label: I3 the board differs from its static render"
        }
    }

    private fun pick(random: ParticleRandom, size: Int): Int =
        (random.nextFloat() * size).toInt().coerceAtMost(size - 1)

    private companion object {
        val FULL = MotionPreference.FULL
        const val SEQUENCES = 20
        const val MOVES = 15
        const val SMALL = 4
        const val LARGE = 8
        const val SEED = 0x2048L
        const val SETTLE_MS = 448L
        const val REPORTED = 40
        val GAPS_MS = listOf(16L, 32L, 48L, 96L, 160L, 448L)
    }
}
