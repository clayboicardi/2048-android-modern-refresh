package com.clayboicardi.game2048.data.platform

import java.security.SecureRandom

/** The seed of each fresh game (D-022, D-038). Tests use `testing/FixedSeedSource`. */
fun interface SeedSource {
    fun nextSeed(): Long
}

/** `SecureRandom().nextLong()` (D-022). */
class SecureRandomSeedSource : SeedSource {
    private val random = SecureRandom()

    override fun nextSeed(): Long = random.nextLong()
}
