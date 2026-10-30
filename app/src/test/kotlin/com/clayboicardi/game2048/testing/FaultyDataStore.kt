package com.clayboicardi.game2048.testing

import androidx.datastore.core.DataStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow

/**
 * Wraps one store of a container so that its file's writes can fail (QUALITY.md §1.11): [data]
 * delegates; [updateData] throws `IOException` while [failWrites] is true, which `ConflatedWriter`
 * swallows (ARCHITECTURE.md §9.3), and delegates otherwise. [writeAttempts] counts every
 * [updateData] call, failed or not: file writes, not saves. Saves submitted before the writer runs
 * conflate into one write, even within one action, so tests count saves with the repositories'
 * `submissions` (D-221).
 *
 * Build repositories over the wrapper and never use the container's own repository for the same
 * store: that would put two document holders on one file.
 */
class FaultyDataStore<T>(private val delegate: DataStore<T>) : DataStore<T> {
    /** While true, every write throws `IOException` and leaves the file as it was. */
    @Volatile var failWrites: Boolean = false

    private val attempts = AtomicInteger(0)

    /** The number of [updateData] calls so far. */
    val writeAttempts: Int
        get() = attempts.get()

    override val data: Flow<T>
        get() = delegate.data

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        attempts.incrementAndGet()
        if (failWrites) throw IOException("FaultyDataStore: writes fail")
        return delegate.updateData(transform)
    }
}
