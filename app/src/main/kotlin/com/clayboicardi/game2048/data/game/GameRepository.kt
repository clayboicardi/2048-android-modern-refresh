package com.clayboicardi.game2048.data.game

import androidx.datastore.core.DataStore
import com.clayboicardi.game2048.data.ConflatedWriter
import com.clayboicardi.game2048.engine.BoardSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

/**
 * The six `game-N.json` files (ARCHITECTURE.md §4.6). One `ConflatedWriter` per size, created
 * lazily; `latest[size]` keeps the last submitted value, so `load` and `peek` of a size with a
 * pending write return what was submitted, not the older file.
 *
 * Main-thread callers only: the two maps are not synchronized, and nothing here blocks. The file is
 * read through the store (DataStore I/O runs on the store's own dispatcher), and each write maps
 * the game to its document inside `updateData`, off the main thread.
 */
class GameRepository(
    private val storeFor: (BoardSize) -> DataStore<GameDocument>,
    private val scope: CoroutineScope,
) {
    /** Keyed by `BoardSize.n`. */
    private val writers = HashMap<Int, ConflatedWriter<SavedGame>>()

    /** The last submitted game of each size, keyed by `BoardSize.n`. */
    private val latest = HashMap<Int, SavedGame>()

    /**
     * The saved game of [size]: the last submitted value if any, else the file. `null` for "no
     * saved game" (the `size == 0` document: a missing or corrupt file, D-045).
     */
    suspend fun load(size: BoardSize): SavedGame? =
        latest[size.n]
            ?: read(size).let { disk ->
                // A save submitted while the file was being read is newer than the file.
                latest[size.n] ?: disk
            }

    /** Enqueues a full-document write of [game] on its size's writer; returns at once (D-024). */
    fun save(game: SavedGame) {
        val n = game.state.size.n
        latest[n] = game
        writers.getOrPut(n) { writerFor(game.state.size) }.submit(game)
    }

    /** Like [load], for read-only callers (recovery of other sizes, statistics, size sheet). */
    suspend fun peek(size: BoardSize): SavedGame? = load(size)

    /** Suspends until every write submitted before this call, on every size, has been attempted. */
    suspend fun awaitIdle() {
        for (writer in writers.values.toList()) writer.awaitIdle()
    }

    /**
     * The [save]s of [size] so far, conflated ones included (`ConflatedWriter.submissions`): a test
     * hook (D-221).
     */
    fun submissions(size: BoardSize): Long = writers[size.n]?.submissions ?: 0

    /**
     * The file's game, already validated by `GameDocumentSerializer`. The `size == 0` document is
     * "no saved game"; so is a document of another size, which only a file copied by hand holds:
     * its game cannot be played on this size.
     */
    private suspend fun read(size: BoardSize): SavedGame? {
        val doc = storeFor(size).data.first()
        return if (doc.size == size.n) GameDocumentMapper.toSavedGame(doc) else null
    }

    private fun writerFor(size: BoardSize): ConflatedWriter<SavedGame> =
        ConflatedWriter(
            scope,
            write = { g -> storeFor(size).updateData { GameDocumentMapper.toDocument(g) } },
        )
}
