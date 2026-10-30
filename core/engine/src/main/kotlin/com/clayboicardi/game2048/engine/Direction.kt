package com.clayboicardi.game2048.engine

/** Legacy direction codes; also the 2-bit move-log values (D-031). */
enum class Direction(val code: Int) {
    UP(0),
    RIGHT(1),
    DOWN(2),
    LEFT(3);

    companion object {
        fun fromCode(code: Int): Direction =
            requireNotNull(entries.firstOrNull { it.code == code }) {
                "Unknown direction code $code"
            }
    }
}
