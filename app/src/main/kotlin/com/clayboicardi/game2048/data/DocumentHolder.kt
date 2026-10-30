package com.clayboicardi.game2048.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The in-memory source of truth of one document (settings, records; ARCHITECTURE.md §4.5). The file
 * is read once, on [main]; every [update] applies in memory at once and submits the whole document
 * to a `ConflatedWriter` when it changed. Updates made before the load are applied after it, in
 * call order.
 *
 * Every state change submits, so the value a failed write tried to store is [state] until the next
 * change, and [retryFailed] therefore rewrites the current document.
 */
class DocumentHolder<T : Any>(
    private val store: DataStore<T>,
    default: T,
    scope: CoroutineScope,
    main: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(default)

    /** `default` until loaded, then the file's value with every update applied. */
    val state: StateFlow<T> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** True once the file has been read. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Updates made before the load, in call order. Main thread only. */
    private val beforeLoad = ArrayList<(T) -> T>()

    private val _durable = MutableStateFlow(default)

    /**
     * What the file is known to hold: the value read at load, then each value whose write returned
     * normally (§4.5, D-044).
     */
    val durable: StateFlow<T> = _durable.asStateFlow()

    private val writer =
        ConflatedWriter<T>(
            scope,
            write = { v -> store.updateData { v } },
            onWritten = { v -> _durable.value = v },
        )

    init {
        scope.launch(main) {
            // After a corruption fallback, this is the default the handler wrote (D-045).
            val disk = store.data.first()
            _durable.value = disk
            val merged = beforeLoad.fold(disk) { acc, f -> f(acc) }
            _state.value = merged
            _loaded.value = true
            if (beforeLoad.isNotEmpty()) writer.submit(merged)
            beforeLoad.clear()
        }
    }

    /**
     * Main thread only. Applies [transform] in memory immediately (or after load, in call order)
     * and enqueues a write when the value changed.
     */
    fun update(transform: (T) -> T) {
        if (!_loaded.value) {
            beforeLoad += transform
            return
        }
        val old = _state.value
        val new = transform(old)
        if (new != old) {
            _state.value = new
            writer.submit(new)
        }
    }

    /**
     * Main thread only. Rewrites [state] if the latest write threw and nothing newer is queued
     * (§4.9, D-044).
     */
    fun retryFailed() = writer.retryFailed()

    /** Suspends until loaded; returns [state]'s value. */
    suspend fun awaitLoaded(): T {
        loaded.first { it }
        return state.value
    }

    /** Suspends until every write submitted before this call has been attempted. */
    suspend fun awaitIdle() = writer.awaitIdle()

    /** The writer's `ConflatedWriter.submissions`: a test hook (D-221). */
    val submissions: Long
        get() = writer.submissions
}
