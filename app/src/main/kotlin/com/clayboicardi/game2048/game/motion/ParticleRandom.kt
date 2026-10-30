package com.clayboicardi.game2048.game.motion

import com.clayboicardi.game2048.engine.SplitMix64

/**
 * The particles' random source (MOTION_SPEC.md §3.6, §3.11; D-064): a wrapper over the engine's
 * `SplitMix64` (D-038), seeded with `state.seed xor state.moveCount.toLong()` of the game that
 * triggered the effect, so filmstrips and tests are reproducible. Not for game rules.
 */
class ParticleRandom(seed: Long) {
    private val source = SplitMix64(seed)

    /** Uniform in [0, 1): `(next() ushr 40).toFloat() / 16_777_216f`. */
    fun nextFloat(): Float = (source.nextLong() ushr FLOAT_SHIFT).toFloat() / FLOAT_SCALE

    /** Uniform in [a, b). */
    fun uniform(a: Float, b: Float): Float = a + (b - a) * nextFloat()

    private companion object {
        /** Keeps the top 24 bits, which a `Float` holds exactly. */
        const val FLOAT_SHIFT = 40

        /** 2^24. */
        const val FLOAT_SCALE = 16_777_216f
    }
}
