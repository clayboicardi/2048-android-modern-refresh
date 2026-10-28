package com.clayboicardi.game2048.data.records

import kotlinx.serialization.Serializable

/**
 * One size's statistics in `records.json` (D-042, D-044; FEATURES.md §3.1). A missing size key
 * reads as `SizeRecord()`. All arithmetic is in `RecordsMath`.
 */
@Serializable
data class SizeRecord(
    val bestScore: Long = 0,
    val bestScoreNoUndo: Long = 0,
    val bestTileExp: Int = 0,
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val gamesAbandoned: Int = 0,
    val totalScore: Long = 0,
    val totalMoves: Long = 0,
    val totalTimeMs: Long = 0,
    val totalUndos: Long = 0,
    /** Index k = finalized games whose final max exponent is >= k, k >= 0 (D-044, D-218). */
    val tileReach: List<Int> = emptyList(),
    val currentWinStreak: Int = 0,
    val longestWinStreak: Int = 0,
)

/**
 * `records.json` (D-042, D-044). Keys of both maps are `"3"` … `"8"` (`RecordsMath.key`); any other
 * key is preserved on write and ignored by v1.0 (FEATURES.md §3). No named companion object
 * (D-134).
 */
@Serializable
data class Records(
    val schemaVersion: Int = 1,
    val sizes: Map<String, SizeRecord> = emptyMap(),
    /**
     * The highest `gameId` finalized on each size (D-044). Bookkeeping, not a statistic: Reset
     * statistics keeps it, and it never decreases.
     */
    val lastFinalizedGameId: Map<String, Long> = emptyMap(),
)
