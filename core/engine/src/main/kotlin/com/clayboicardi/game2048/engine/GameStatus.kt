package com.clayboicardi.game2048.engine

/** D-036. Ordinals are persisted inside snapshot bytes: never reorder or insert. */
enum class GameStatus {
    PLAYING,
    WON,
    KEEP_PLAYING,
    OVER,
}
