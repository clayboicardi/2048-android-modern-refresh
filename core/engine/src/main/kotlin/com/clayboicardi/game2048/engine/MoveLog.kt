package com.clayboicardi.game2048.engine

/** Packed 2-bit move log (D-039). Move i is in byte i/4, bits 2*(i%4)..2*(i%4)+1, LSB-first. */
class MoveLog private constructor(private val packed: ByteArray, val size: Int) {
    operator fun get(index: Int): Direction =
        Direction.fromCode(
            (packed[index / MOVES_PER_BYTE].toInt() shr shiftOf(index)) and MOVE_MASK,
        )

    /** Copy-on-write: this log is unchanged. */
    fun append(direction: Direction): MoveLog {
        val bytes = packed.copyOf(packedSize(size + 1))
        val at = size / MOVES_PER_BYTE
        bytes[at] = (bytes[at].toInt() or (direction.code shl shiftOf(size))).toByte()
        return MoveLog(bytes, size + 1)
    }

    /** The first [newSize] moves (0..size); unused high bits of the new last byte are 0. */
    fun truncate(newSize: Int): MoveLog {
        val bytes = packed.copyOf(packedSize(newSize))
        val usedInLast = newSize % MOVES_PER_BYTE
        if (usedInLast != 0) {
            val last = bytes.size - 1
            bytes[last] = (bytes[last].toInt() and ((1 shl shiftOf(usedInLast)) - 1)).toByte()
        }
        return MoveLog(bytes, newSize)
    }

    fun toPackedBytes(): ByteArray = packed.copyOf()

    fun toList(): List<Direction> = List(size, ::get)

    /** Size + packed content. */
    override fun equals(other: Any?): Boolean =
        other is MoveLog && other.size == size && other.packed.contentEquals(packed)

    override fun hashCode(): Int = 31 * size + packed.contentHashCode()

    override fun toString(): String = "MoveLog(size=$size)"

    companion object {
        private const val MOVES_PER_BYTE = 4
        private const val BITS_PER_MOVE = 2
        private const val MOVE_MASK = 0b11
        val EMPTY: MoveLog = MoveLog(ByteArray(0), 0)

        fun packedSize(moves: Int): Int = (moves + MOVES_PER_BYTE - 1) / MOVES_PER_BYTE

        /**
         * Requires size >= 0, bytes.size == packedSize(size) and unused high bits of the last byte
         * == 0, otherwise throws IllegalArgumentException.
         */
        fun fromPackedBytes(bytes: ByteArray, size: Int): MoveLog {
            require(size >= 0) { "A move log cannot hold $size moves" }
            require(bytes.size == packedSize(size)) {
                "A log of $size moves needs ${packedSize(size)} bytes, got ${bytes.size}"
            }
            val usedInLast = size % MOVES_PER_BYTE
            // The shift sign-extends, so a set top bit still leaves a non-zero result.
            require(usedInLast == 0 || (bytes.last().toInt() shr shiftOf(usedInLast)) == 0) {
                "The unused high bits of the last move-log byte must be 0"
            }
            return MoveLog(bytes.copyOf(), size)
        }

        /** Bit offset of move [index] inside its byte. */
        private fun shiftOf(index: Int): Int = BITS_PER_MOVE * (index % MOVES_PER_BYTE)
    }
}
