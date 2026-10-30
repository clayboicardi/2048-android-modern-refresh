package com.clayboicardi.game2048.engine

/** Bounded draws used by the spawner (D-021, D-038). */
fun interface RandomSource {
    fun nextInt(bound: Int): Int
}

/** App-owned SplitMix64 generator; its whole state is one Long (D-038). */
class SplitMix64(state: Long) : RandomSource {
    var state: Long = state
        private set

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr SHIFT_1)) * MIX1
        z = (z xor (z ushr SHIFT_2)) * MIX2
        return z xor (z ushr SHIFT_3)
    }

    /** Unbiased value in 0 until bound (rejection sampling, D-038). */
    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        var u = nextLong() ushr 1
        var r = u % bound
        // Overflow means u fell in the biased tail above the last whole multiple of bound.
        while (u - r + (bound - 1) < 0) {
            u = nextLong() ushr 1
            r = u % bound
        }
        return r.toInt()
    }

    companion object {
        const val GOLDEN_GAMMA: Long = -0x61C8864680B583EBL // 0x9E3779B97F4A7C15
        const val MIX1: Long = -0x40A7B892E31B1A47L // 0xBF58476D1CE4E5B9
        const val MIX2: Long = -0x6B2FB644ECCEEE15L // 0x94D049BB133111EB
        private const val SHIFT_1 = 30
        private const val SHIFT_2 = 27
        private const val SHIFT_3 = 31
    }
}
