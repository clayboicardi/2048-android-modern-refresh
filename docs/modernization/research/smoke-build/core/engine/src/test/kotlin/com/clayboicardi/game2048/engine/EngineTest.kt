package com.clayboicardi.game2048.engine

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EngineTest {
    @Test fun merges() = assertEquals(listOf(4, 4, 0, 0), slideRowLeft(listOf(2, 2, 2, 2)))
    @Test fun sumPreserved() {
        runBlocking {
            checkAll(Arb.list(Arb.element(0, 2, 4, 8), 4..4)) { row ->
                assertEquals(row.sum(), slideRowLeft(row).sum())
            }
        }
    }
}
