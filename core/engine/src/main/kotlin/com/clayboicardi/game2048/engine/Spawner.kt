package com.clayboicardi.game2048.engine

/**
 * Spawns one tile into [cells] in place (D-034): the value first (a 4 when
 * `rng.nextInt(Game.FOUR_ONE_IN) == 0`, else a 2), then a uniform pick among the empty cells in
 * ascending row-major order. Requires at least one empty cell.
 */
internal fun spawnInto(cells: ByteArray, rng: RandomSource): Spawn {
    val exp = if (rng.nextInt(Game.FOUR_ONE_IN) == 0) 2 else 1
    val index = nthEmptyIndex(cells, rng.nextInt(cells.count { it.toInt() == 0 }))
    cells[index] = exp.toByte()
    return Spawn(index, exp)
}

/** The [k]-th (0-based) empty index in ascending row-major order, i.e. `empty[k]` of D-034. */
private fun nthEmptyIndex(cells: ByteArray, k: Int): Int {
    var index = -1
    var seen = -1
    while (seen < k) {
        index++
        if (cells[index].toInt() == 0) seen++
    }
    return index
}
