package com.clayboicardi.game2048.data.game

import com.clayboicardi.game2048.data.records.Finalization
import com.clayboicardi.game2048.engine.GameState

/**
 * The `:app` domain model of a saved game (ARCHITECTURE.md §4.3): the engine state plus app-owned
 * metadata. `GameDocumentMapper` converts it to and from `GameDocument`.
 */
data class SavedGame(
    val state: GameState,
    /** Play time (D-043); the engine reads no clock. */
    val elapsedMs: Long,
    /** `bestScore` of this size when the game started (D-044). */
    val bestAtStart: Long,
    /** The once-per-game new-best moment has fired (D-044). */
    val newBestAnnounced: Boolean,
    /** D-044: unique and strictly increasing per size, >= 1; the engine never sees it. */
    val gameId: Long,
    /**
     * D-044 recovery entries, ascending `gameId`, at most `MAX_PENDING_FINALIZATIONS`. Every save
     * of this game carries them unchanged; only a replacement (New game, R2) drops entries.
     */
    val pendingFinalizations: List<Finalization>,
)
