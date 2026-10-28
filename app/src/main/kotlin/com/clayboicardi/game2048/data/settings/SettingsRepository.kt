package com.clayboicardi.game2048.data.settings

import androidx.datastore.core.DataStore
import com.clayboicardi.game2048.data.DocumentHolder
import com.clayboicardi.game2048.engine.BoardSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * `settings.json` behind a `DocumentHolder` (ARCHITECTURE.md §4.5–§4.6). `state` is the in-memory
 * source of truth; `lastBoardSize` outside 3..8 reads as 4 (D-042).
 *
 * Coercion on read: the holder reads the file through [CoercingStore], so the loaded value is
 * already coerced when `loaded` turns true, and every update transform, including one queued before
 * the load, receives a coerced value. The result of each transform is coerced as well, so `state`
 * never holds an out-of-range size. The file keeps its raw value until the next update writes the
 * whole document (FEATURES.md §2.4 rule 5: values are validated when read).
 */
class SettingsRepository(
    store: DataStore<Settings>,
    scope: CoroutineScope,
    main: CoroutineDispatcher,
) {
    private val holder = DocumentHolder(CoercingStore(store), Settings(), scope, main)

    /** `Settings()` until loaded, then the file's value with every update applied. */
    val state: StateFlow<Settings> = holder.state

    /** True once the file has been read. */
    val loaded: StateFlow<Boolean> = holder.loaded

    /** Suspends until loaded; returns `state.value`. */
    suspend fun awaitLoaded(): Settings = holder.awaitLoaded()

    /**
     * Main thread only. Applies [transform] in memory at once (or after load, in call order) and
     * enqueues a write when the value changed.
     */
    fun update(transform: (Settings) -> Settings) {
        holder.update { coerce(transform(it)) }
    }

    /** Suspends until every write submitted before this call has been attempted. */
    suspend fun awaitIdle(): Unit = holder.awaitIdle()

    /** Saves submitted so far, conflated ones included: a test hook (D-221). */
    val submissions: Long
        get() = holder.submissions

    /** A view of [store] whose every read is [coerce]d; writes go to the file unchanged. */
    private class CoercingStore(private val store: DataStore<Settings>) : DataStore<Settings> {
        override val data: Flow<Settings> = store.data.map(::coerce)

        override suspend fun updateData(transform: suspend (t: Settings) -> Settings): Settings =
            store.updateData {
                transform(coerce(it))
            }
    }

    private companion object {
        /** D-042: a board side outside 3..8 reads as the default 4. */
        fun coerce(s: Settings): Settings =
            if (s.lastBoardSize in BoardSize.MIN_N..BoardSize.MAX_N) {
                s
            } else {
                s.copy(lastBoardSize = BoardSize.DEFAULT_N)
            }
    }
}
