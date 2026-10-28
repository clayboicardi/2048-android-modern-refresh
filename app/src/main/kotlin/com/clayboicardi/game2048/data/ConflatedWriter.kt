package com.clayboicardi.game2048.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The D-024 writer, one per file (ARCHITECTURE.md §4.5). Submissions are conflated: while a write
 * runs, newer values replace each other and only the latest is written next. A plain `collect`
 * (never `collectLatest`) means a write in progress is never cancelled. An `IOException` is
 * swallowed and counted: the in-memory state stays authoritative and the next submission rewrites
 * the whole document (§9.3).
 *
 * A write is durable when [write] returned normally: `DataStore.updateData` returns only after it
 * has written the temp file and renamed it over the old one. The writer reports exactly those
 * values through [onWritten], in write order (D-044).
 */
class ConflatedWriter<T : Any>(
    /** `applicationScope`: never cancelled in production. */
    scope: CoroutineScope,
    /** `{ value -> store.updateData { value } }`. */
    private val write: suspend (T) -> Unit,
    /** D-044: called with each value whose write returned normally, in write order. */
    private val onWritten: (T) -> Unit = {},
) {
    private data class Pending<T>(val version: Long, val value: T)

    private val pending = MutableStateFlow<Pending<T>?>(null)
    private val written = MutableStateFlow(0L)
    private val versions = AtomicLong(0)

    /** The latest attempt when it threw; null after a write returns normally. */
    @Volatile private var lastFailed: Pending<T>? = null

    /** Writes that threw `IOException`. A test hook: never shown in UI, never logged. */
    val failures = AtomicInteger(0)

    /**
     * Every [submit] so far, the values a newer one replaced and the [retryFailed] resubmissions
     * included, so it counts saves where the file writes conflate (D-221). A test hook: never shown
     * in UI, never logged.
     */
    val submissions: Long
        get() = versions.get()

    init {
        scope.launch {
            // Plain collect: conflated, and it never cancels a write in flight (D-024).
            pending.filterNotNull().collect { p ->
                try {
                    write(p.value)
                    lastFailed = null
                    onWritten(p.value)
                } catch (ignored: IOException) {
                    failures.incrementAndGet()
                    lastFailed = p
                }
                written.value = p.version
            }
        }
    }

    /** Replaces any value not yet being written; returns at once. */
    fun submit(value: T) {
        pending.value = Pending(versions.incrementAndGet(), value)
    }

    /**
     * Main thread. Resubmits the latest value if its write threw and nothing newer was submitted
     * since; otherwise a no-op (D-044).
     */
    fun retryFailed() {
        val f = lastFailed ?: return
        if (f.version == versions.get()) submit(f.value)
    }

    /** Suspends until everything submitted before this call has been attempted. */
    suspend fun awaitIdle() {
        val target = versions.get()
        written.first { it >= target }
    }
}
