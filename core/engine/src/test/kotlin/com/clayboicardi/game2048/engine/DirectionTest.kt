package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D-031: the legacy direction codes, which are also the 2-bit move-log values. */
class DirectionTest {
    @Test
    fun fromCodeRoundTrips() {
        assertEquals(listOf(0, 1, 2, 3), Direction.entries.map { it.code })
        for (direction in Direction.entries) {
            assertEquals(direction, Direction.fromCode(direction.code))
        }
    }

    @Test
    fun fromCodeOfUnknownCodeThrows() {
        assertThrows<IllegalArgumentException> { Direction.fromCode(4) }
    }
}
