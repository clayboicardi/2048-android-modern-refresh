package com.clayboicardi.game2048.data.records

import androidx.datastore.core.DataStore
import com.clayboicardi.game2048.data.DocumentHolder
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.engine.BoardSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * `records.json` behind a `DocumentHolder` (ARCHITECTURE.md §4.5–§4.6, §4.9). All arithmetic is in
 * the pure `RecordsMath`. Every mutator is main-thread only and applies in memory at once; a write
 * is submitted only when the document changed (D-043, D-044), so a finalization that is already
 * recorded submits no write.
 */
class RecordsRepository(
    store: DataStore<Records>,
    scope: CoroutineScope,
    main: CoroutineDispatcher,
) {
    private val holder = DocumentHolder(store, Records(), scope, main)

    /** `Records()` until loaded, then the in-memory document. */
    val state: StateFlow<Records> = holder.state

    /** True once the file has been read. */
    val loaded: StateFlow<Boolean> = holder.loaded

    /** Suspends until loaded; returns `state.value`. */
    suspend fun awaitLoaded(): Records = holder.awaitLoaded()

    /** `update { RecordsMath.progress(it, game) }`: writes only when a best increases (D-043). */
    fun onProgress(game: SavedGame) {
        holder.update { RecordsMath.progress(it, game) }
    }

    /**
     * `update { RecordsMath.finalize(it, game.toFinalization()) }`; a no-op when already counted.
     */
    fun finalize(game: SavedGame) {
        val f = game.toFinalization()
        holder.update { RecordsMath.finalize(it, f) }
    }

    /**
     * R1: one `update` folding `game.pendingFinalizations` through `RecordsMath.finalize`, in
     * order.
     */
    fun recover(game: SavedGame) {
        holder.update { r -> game.pendingFinalizations.fold(r, RecordsMath::finalize) }
    }

    /** `RecordsMath.isFinalized(state.value, game.state.size, game.gameId)` (D-044). */
    fun isFinalized(game: SavedGame): Boolean =
        RecordsMath.isFinalized(state.value, game.state.size, game.gameId)

    /**
     * The records *file* is known to hold [f]: `RecordsMath.isFinalized(durable, f.size,
     * f.gameId)`, where `durable` is the value read at load or last written by a write that
     * returned normally (ARCHITECTURE.md §4.5, D-044).
     */
    fun isDurable(f: Finalization): Boolean =
        RecordsMath.isFinalized(holder.durable.value, f.size, f.gameId)

    /** Rewrites the current document if its latest write threw and nothing newer is queued. */
    fun retryFailedWrite() {
        holder.retryFailed()
    }

    /** `RecordsMath.nextGameId(state.value, size, replaced)` (D-044). */
    fun nextGameId(size: BoardSize, replaced: SavedGame?): Long =
        RecordsMath.nextGameId(state.value, size, replaced)

    /** Reset statistics: `update(RecordsMath::reset)` (D-044, D-198). */
    fun resetAll() {
        holder.update(RecordsMath::reset)
    }

    /** `bestScore` of [size] in the in-memory document, 0 when absent. */
    fun best(size: BoardSize): Long = state.value.sizes[RecordsMath.key(size)]?.bestScore ?: 0

    /** Suspends until every write submitted before this call has been attempted. */
    suspend fun awaitIdle(): Unit = holder.awaitIdle()

    /** Saves submitted so far, conflated ones included: a test hook (D-221). */
    val submissions: Long
        get() = holder.submissions
}
