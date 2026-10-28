package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Spawn statistics of D-034 (QUALITY.md §1.4, ARCHITECTURE.md §10.2, D-145). */
class SpawnDistributionTest {
    /** F01-AC15 */
    @Test
    fun fourRateAndUniformCells() {
        // A fixed 4×4 board whose only empty cells are EMPTY_CELLS; the others hold tiles.
        val cells = ByteArray(CELLS) { if (it in EMPTY_CELLS) 0 else (it % 9 + 1).toByte() }
        val template = Board.of(BoardSize.of(4), cells).toByteArray()
        // One generator, continuing across all spawns.
        val rng = SplitMix64(0x2048L)
        val hits = IntArray(CELLS)
        var fours = 0
        repeat(SPAWNS) {
            val target = template.copyOf()
            val spawn = spawnInto(target, rng)
            assertTrue(spawn.exp == 1 || spawn.exp == 2, "$spawn")
            assertEquals(spawn.exp, target[spawn.index].toInt())
            // Nothing but the spawned cell changed.
            target[spawn.index] = 0
            assertArrayEquals(template, target)
            hits[spawn.index]++
            if (spawn.exp == 2) fours++
        }
        val fourRate = fours.toDouble() / SPAWNS
        assertTrue(fourRate in 0.095..0.105, "4-rate $fourRate")
        val uniform = SPAWNS.toDouble() / EMPTY_CELLS.size
        for (index in 0 until CELLS) {
            if (index in EMPTY_CELLS) {
                val share = hits[index].toDouble()
                assertTrue(share in uniform * 0.9..uniform * 1.1, "cell $index: $share")
            } else {
                assertEquals(0, hits[index], "cell $index is not empty but was chosen")
            }
        }
    }

    companion object {
        private const val SPAWNS = 100_000
        private const val CELLS = 16
        private val EMPTY_CELLS = setOf(0, 3, 5, 10, 12, 15)
    }
}
