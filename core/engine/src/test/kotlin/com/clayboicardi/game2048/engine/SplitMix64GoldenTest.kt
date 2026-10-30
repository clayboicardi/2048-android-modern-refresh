package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D-038: SplitMix64 golden vectors and bounded draws (QUALITY.md §1.3). */
class SplitMix64GoldenTest {
    /** F01-AC16 */
    @Test
    fun vectorsAndBoundedDraws() {
        assertEquals(SEED_0_OUTPUTS, firstOutputs(0L))
        assertEquals(SEED_2048_OUTPUTS, firstOutputs(SEED))
        assertEquals(listOf(8, 6, 0, 0, 9, 9, 8, 3), firstDraws(10))
        assertEquals(listOf(8, 10, 10, 10, 1, 13, 4, 9), firstDraws(16))

        val rng = SplitMix64(SEED)
        assertThrows<IllegalArgumentException> { rng.nextInt(0) }
        assertThrows<IllegalArgumentException> { rng.nextInt(-1) }
        assertThrows<IllegalArgumentException> { rng.nextInt(Int.MIN_VALUE) }
        // require(bound > 0) runs before any draw, so the rejected calls left the state alone.
        assertEquals(SEED, rng.state)
        assertEquals(0, SplitMix64(SEED).nextInt(1))
    }

    /** D-038: state steps by GOLDEN_GAMMA per nextLong */
    @Test
    fun stateAdvancesByGoldenGamma() {
        assertEquals(0x9E3779B97F4A7C15uL.toLong(), SplitMix64.GOLDEN_GAMMA)
        assertEquals(0xBF58476D1CE4E5B9uL.toLong(), SplitMix64.MIX1)
        assertEquals(0x94D049BB133111EBuL.toLong(), SplitMix64.MIX2)
        for (seed in listOf(0L, SEED, -1L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val generator = SplitMix64(seed)
            assertEquals(seed, generator.state)
            repeat(STEPS) {
                val before = generator.state
                generator.nextLong()
                assertEquals(before + SplitMix64.GOLDEN_GAMMA, generator.state)
            }
        }
        // Without a rejection, nextInt(bound) is one nextLong: (nextLong() ushr 1) % bound.
        for (bound in 1..MAX_SMALL_BOUND) {
            val single = SplitMix64(SEED)
            val expected = ((SEED_2048_OUTPUTS[0] ushr 1) % bound).toInt()
            assertEquals(expected, single.nextInt(bound), "bound $bound")
            assertEquals(SEED + SplitMix64.GOLDEN_GAMMA, single.state, "bound $bound")
        }
    }

    /** D-038: every bounded draw lies in 0 until bound */
    @Test
    fun boundedDrawsStayInRange() {
        for (bound in (1..MAX_SMALL_BOUND) + Int.MAX_VALUE) {
            val rng = SplitMix64(SEED)
            repeat(DRAWS_PER_BOUND) {
                val value = rng.nextInt(bound)
                assertTrue(value in 0 until bound) { "nextInt($bound) returned $value" }
            }
        }
    }

    /** D-038: rejection sampling keeps nextInt uniform */
    @Test
    fun nextIntIsUniform() {
        val rng = SplitMix64(SEED)
        val counts = IntArray(SIDES)
        repeat(UNIFORM_DRAWS) { counts[rng.nextInt(SIDES)]++ }
        for ((value, count) in counts.withIndex()) {
            assertTrue(count in UNIFORM_LOW..UNIFORM_HIGH) { "value $value drawn $count times" }
        }
    }

    companion object {
        private const val SEED = 0x2048L
        private const val STEPS = 4
        private const val MAX_SMALL_BOUND = 64
        private const val DRAWS_PER_BOUND = 1_000
        private const val SIDES = 6
        private const val UNIFORM_DRAWS = 60_000
        private const val UNIFORM_LOW = 9_500
        private const val UNIFORM_HIGH = 10_500

        private val SEED_0_OUTPUTS =
            listOf(
                0xE220A8397B1DCDAFuL.toLong(),
                0x6E789E6AA1B965F4uL.toLong(),
                0x06C45D188009454FuL.toLong(),
                0xF88BB8A8724C81ECuL.toLong(),
            )

        private val SEED_2048_OUTPUTS =
            listOf(
                0xF7F3D05A9C290B51uL.toLong(),
                0x390005F078622C95uL.toLong(),
                0xC35B79300DF431F5uL.toLong(),
                0x2B84F35B753B4E34uL.toLong(),
            )

        private fun firstOutputs(seed: Long): List<Long> {
            val rng = SplitMix64(seed)
            return List(STEPS) { rng.nextLong() }
        }

        private fun firstDraws(bound: Int): List<Int> {
            val rng = SplitMix64(SEED)
            return List(DRAWS) { rng.nextInt(bound) }
        }

        private const val DRAWS = 8
    }
}
