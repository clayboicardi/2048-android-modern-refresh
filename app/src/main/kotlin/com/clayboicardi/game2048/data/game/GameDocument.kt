package com.clayboicardi.game2048.data.game

import kotlinx.serialization.Serializable

/** Frozen persisted names of `GameStatus`, mapped by name (D-042). Never rename an entry. */
@Serializable
enum class StatusDto {
    PLAYING,
    WON,
    KEEP_PLAYING,
    OVER,
}

/**
 * `game-3.json` … `game-8.json` (D-042). `size == 0` is "no saved game": the default value and the
 * corruption fallback (D-045). Byte fields are `kotlin.io.encoding.Base64.Default`. Every field has
 * a default (D-041), but `gameId` has no *valid* one: a document with `size != 0` and `gameId < 1`,
 * including a missing `gameId` key, is corrupt (ARCHITECTURE.md §4.4). No named companion object
 * (D-134).
 */
@Serializable
data class GameDocument(
    val schemaVersion: Int = 1,
    val size: Int = 0,
    val seed: Long = 0,
    /** Base64 of the N² exponent bytes, row-major. */
    val cells: String = "",
    val score: Long = 0,
    val rngState: Long = 0,
    val status: StatusDto = StatusDto.PLAYING,
    val moveCount: Int = 0,
    val keepGoingAtMove: Int = -1,
    /** Base64 of `MoveLog.toPackedBytes()`. */
    val moveLog: String = "",
    /** Base64 of each `Snapshot.toBytes()`, in order. */
    val snapshots: List<String> = emptyList(),
    val undoCount: Int = 0,
    val elapsedMs: Long = 0,
    val bestAtStart: Long = 0,
    val newBestAnnounced: Boolean = false,
    /** D-044 finalization identity: >= 1 whenever `size != 0`. */
    val gameId: Long = 0,
    /**
     * D-044 recovery entries: ascending `gameId`, each in `1 until gameId`, at most
     * `MAX_PENDING_FINALIZATIONS`; empty when none is pending (ARCHITECTURE.md §4.9).
     */
    val pendingFinalizations: List<FinalizationDocument> = emptyList(),
)

/**
 * The finalization input of a counted game that an earlier New game on this size replaced and that
 * `records.json` is not yet known to hold (D-044). The size comes from the enclosing document.
 */
@Serializable
data class FinalizationDocument(
    val gameId: Long = 0,
    val status: StatusDto = StatusDto.PLAYING,
    val score: Long = 0,
    val moveCount: Int = 0,
    val elapsedMs: Long = 0,
    val undoCount: Int = 0,
    val maxExp: Int = 0,
)
