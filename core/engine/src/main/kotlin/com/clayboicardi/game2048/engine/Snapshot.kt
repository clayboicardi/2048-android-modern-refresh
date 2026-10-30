package com.clayboicardi.game2048.engine

/**
 * State at move 32k (D-039). Binary layout in ARCHITECTURE.md §3.4. keepGoingAtMove is deliberately
 * absent (D-042).
 */
data class Snapshot(
    val moveCount: Int,
    val score: Long,
    val rngState: Long,
    val status: GameStatus,
    val board: Board,
) {
    fun toBytes(): ByteArray {
        val bytes = ByteArray(HEADER_BYTES + board.cellCount)
        writeBigEndian(bytes, OFFSET_MOVE_COUNT, moveCount.toLong(), Int.SIZE_BYTES)
        writeBigEndian(bytes, OFFSET_SCORE, score, Long.SIZE_BYTES)
        writeBigEndian(bytes, OFFSET_RNG, rngState, Long.SIZE_BYTES)
        bytes[OFFSET_STATUS] = status.ordinal.toByte()
        for (i in 0 until board.cellCount) {
            bytes[HEADER_BYTES + i] = board[i].toByte()
        }
        return bytes
    }

    companion object {
        // Big-endian offsets of the §3.4 layout; the N² cell bytes start at HEADER_BYTES.
        private const val OFFSET_MOVE_COUNT = 0
        private const val OFFSET_SCORE = 4
        private const val OFFSET_RNG = 12
        private const val OFFSET_STATUS = 20
        private const val HEADER_BYTES = 21
        private const val BYTE_MASK = 0xFF

        /**
         * Throws IllegalArgumentException when bytes.size != HEADER_BYTES + size.cellCount, the
         * status byte is not a GameStatus ordinal, or a cell byte is outside 0..Board.MAX_EXP.
         */
        fun fromBytes(bytes: ByteArray, size: BoardSize): Snapshot {
            require(bytes.size == HEADER_BYTES + size.cellCount) {
                "A $size snapshot needs ${HEADER_BYTES + size.cellCount} bytes, got ${bytes.size}"
            }
            val ordinal = bytes[OFFSET_STATUS].toInt() and BYTE_MASK
            require(ordinal < GameStatus.entries.size) { "Unknown status ordinal $ordinal" }
            return Snapshot(
                moveCount = readBigEndian(bytes, OFFSET_MOVE_COUNT, Int.SIZE_BYTES).toInt(),
                score = readBigEndian(bytes, OFFSET_SCORE, Long.SIZE_BYTES),
                rngState = readBigEndian(bytes, OFFSET_RNG, Long.SIZE_BYTES),
                status = GameStatus.entries[ordinal],
                // Board.of rejects a cell byte outside 0..Board.MAX_EXP.
                board = Board.of(size, bytes.copyOfRange(HEADER_BYTES, bytes.size)),
            )
        }

        /** Writes the low [width] bytes of [value] at [offset], most significant first. */
        private fun writeBigEndian(bytes: ByteArray, offset: Int, value: Long, width: Int) {
            for (i in 0 until width) {
                bytes[offset + i] = (value ushr (Byte.SIZE_BITS * (width - 1 - i))).toByte()
            }
        }

        /** Reads [width] bytes at [offset], most significant first, as an unsigned value. */
        private fun readBigEndian(bytes: ByteArray, offset: Int, width: Int): Long {
            var value = 0L
            for (i in 0 until width) {
                value =
                    (value shl Byte.SIZE_BITS) or
                        (bytes[offset + i].toLong() and BYTE_MASK.toLong())
            }
            return value
        }
    }
}
