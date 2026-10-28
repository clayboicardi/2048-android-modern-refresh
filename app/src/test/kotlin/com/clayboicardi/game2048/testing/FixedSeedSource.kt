package com.clayboicardi.game2048.testing

import com.clayboicardi.game2048.data.platform.SeedSource

/**
 * A `SeedSource` that returns [first], then `first + 1`, `first + 2`, … (FEATURES.md F01-AC22). A
 * new instance starts again at [first], so containers built with `FixedSeedSource(0x2048)` repeat
 * their seeds on purpose and only `gameId` tells their games apart (QUALITY.md §1.11).
 */
class FixedSeedSource(private val first: Long) : SeedSource {
    private var next = first

    /** The seeds handed out so far, in order. */
    val issued: List<Long>
        get() = (first until next).toList()

    override fun nextSeed(): Long = next++
}
