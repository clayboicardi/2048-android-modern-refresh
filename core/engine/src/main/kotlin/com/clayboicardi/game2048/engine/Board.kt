package com.clayboicardi.game2048.engine

/**
 * Row-major exponents: index = row * n + col, row 0 at the top, 0 = empty, k = value 2^k (D-030).
 */
class Board private constructor(val size: BoardSize, private val cells: ByteArray) {
    val cellCount: Int
        get() = cells.size

    operator fun get(index: Int): Int = cells[index].toInt()

    operator fun get(row: Int, col: Int): Int = cells[row * size.n + col].toInt()

    fun emptyCount(): Int {
        var count = 0
        for (cell in cells) {
            if (cell.toInt() == 0) count++
        }
        return count
    }

    fun tileCount(): Int = cellCount - emptyCount()

    /** 0 for an empty board. */
    fun maxExp(): Int {
        var max = 0
        for (cell in cells) {
            if (cell > max) max = cell.toInt()
        }
        return max
    }

    /** Defensive copy. */
    fun toByteArray(): ByteArray = cells.copyOf()

    /** Size + contentEquals: cells.size == n², so equal contents already mean equal sizes. */
    override fun equals(other: Any?): Boolean = other is Board && other.cells.contentEquals(cells)

    /** 31 * size.hashCode() + contentHashCode. */
    override fun hashCode(): Int = 31 * size.hashCode() + cells.contentHashCode()

    /** Rows of exponents: cells joined by " ", rows by " / ", e.g. "0 0 1 / 2 0 0 / 0 0 0". */
    override fun toString(): String =
        (0 until size.n).joinToString(ROW_SEPARATOR) { row ->
            (0 until size.n).joinToString(CELL_SEPARATOR) { col -> get(row, col).toString() }
        }

    companion object {
        const val MAX_EXP = 127
        private const val ROW_SEPARATOR = " / "
        private const val CELL_SEPARATOR = " "

        fun empty(size: BoardSize): Board = Board(size, ByteArray(size.cellCount))

        /**
         * Copies; requires cells.size == size.cellCount and every byte in 0..MAX_EXP, otherwise
         * throws IllegalArgumentException.
         */
        fun of(size: BoardSize, cells: ByteArray): Board {
            require(cells.size == size.cellCount) {
                "A $size board needs ${size.cellCount} cells, got ${cells.size}"
            }
            // A Byte never exceeds MAX_EXP, so only a negative byte is out of range.
            require(cells.all { it >= 0 }) { "Every exponent must be in 0..$MAX_EXP" }
            return Board(size, cells.copyOf())
        }

        /**
         * Takes ownership of [cells] without copying or validating. The caller passes a freshly
         * built row-major array of size.cellCount exponents in 0..MAX_EXP and never writes to it
         * again: the boards that new games and accepted moves build.
         */
        internal fun adopt(size: BoardSize, cells: ByteArray): Board = Board(size, cells)
    }
}

/**
 * Value 2^exp of a tile exponent (exp >= 0), saturating at Long.MAX_VALUE for exp >= 63. Exponent 0
 * is an empty cell, not a tile: tileValue(0) == 1, so value sums over a board skip empty cells.
 */
fun tileValue(exp: Int): Long = if (exp < Long.SIZE_BITS - 1) 1L shl exp else Long.MAX_VALUE

/** a + b for non-negative operands, saturating at Long.MAX_VALUE (scores, D-030, D-033). */
internal fun saturatingAdd(a: Long, b: Long): Long {
    val sum = a + b
    // Two non-negative operands can only overflow into the negative range.
    return if (sum < 0) Long.MAX_VALUE else sum
}
