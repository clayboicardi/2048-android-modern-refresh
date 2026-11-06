package com.clayboicardi.game2048.ios

import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveLog
import com.clayboicardi.game2048.engine.Snapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.use

/** Directory for the save file; `NSDocumentDirectory` on iOS, a temp dir on the JVM. */
expect fun gameStoreDirectory(): String

/** The platform file system; `FileSystem.SYSTEM` on both iOS and the JVM. */
expect fun gameStoreFileSystem(): FileSystem

/**
 * One in-progress game, flattened to primitives. We persist just enough to resume the board and
 * keep playing (D-272 v1 scope): the undo history is not restored across a cold start, so a resumed
 * game begins a fresh move log. Best scores are kept per board size.
 */
@Serializable
private data class SavedGame(
    val sizeN: Int,
    val seed: Long,
    val cells: List<Int>,
    val score: Long,
    val rngState: Long,
    val statusOrdinal: Int,
)

@Serializable
private data class SavedState(val current: SavedGame? = null, val best: Map<Int, Long> = emptyMap())

/** The whole save loaded from disk: the current game (if any) and best score per board size. */
data class LoadedState(val current: GameState?, val best: Map<Int, Long>)

/**
 * JSON-on-a-file persistence for the iOS app (D-272). Blocking: callers run it off the main thread
 * ([GameModel] uses a background dispatcher).
 *
 * Never loses the previous save:
 * - **Writes are atomic.** The new JSON goes to a sibling temp file, is flushed to disk, and only
 *   then renamed over the save. A kill or a failed write leaves the old save intact.
 * - **An unreadable save is moved aside, never overwritten.** If the file cannot be read or
 *   decoded, it is renamed to [CORRUPT_NAME] before the app starts fresh, so best scores remain
 *   recoverable instead of being replaced by the next write.
 * - **A game the engine rejects drops only the game.** Best scores still load.
 */
class IosGameStore(
    private val fs: FileSystem = gameStoreFileSystem(),
    directory: String = gameStoreDirectory(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val dir: Path = directory.toPath()
    private val file: Path = dir / FILE_NAME

    fun load(): LoadedState {
        if (!runCatching { fs.exists(file) }.getOrDefault(false)) return EMPTY
        val saved = runCatching {
            json.decodeFromString<SavedState>(fs.read(file) { readUtf8() })
        }
            .getOrNull()
        if (saved == null) {
            quarantine()
            return EMPTY
        }
        val current = saved.current?.let { runCatching { it.toGameState() }.getOrNull() }
        return LoadedState(current, saved.best)
    }

    fun save(current: GameState?, best: Map<Int, Long>) {
        val bytes = json.encodeToString(SavedState(current?.toSaved(), best)).encodeToByteArray()
        runCatching {
            fs.createDirectories(dir)
            val tmp = dir / TMP_NAME
            fs.openReadWrite(tmp).use { handle ->
                handle.resize(0)
                handle.write(0, bytes, 0, bytes.size)
                handle.flush()
            }
            fs.atomicMove(tmp, file)
        }
    }

    /** Keeps the unreadable save's bytes (replacing any older quarantined copy). */
    private fun quarantine() {
        runCatching { fs.atomicMove(file, dir / CORRUPT_NAME) }
    }

    companion object {
        const val FILE_NAME = "game2048-ios.json"
        const val TMP_NAME = "game2048-ios.json.tmp"
        const val CORRUPT_NAME = "game2048-ios.corrupt.json"
        private val EMPTY = LoadedState(null, emptyMap())
    }
}

private fun GameState.toSaved(): SavedGame =
    SavedGame(
        sizeN = size.n,
        seed = seed,
        cells = List(board.cellCount) { board[it] },
        score = score,
        rngState = rngState,
        statusOrdinal = status.ordinal,
    )

/**
 * Rebuilds a playable [GameState] with an empty move log (moveCount 0). The engine's `move`,
 * `keepGoing` and `canMove` work on it unchanged; only cross-restart undo is given up. Throws if
 * the stored bytes violate the engine's invariants — the caller treats that as "no game".
 */
private fun SavedGame.toGameState(): GameState {
    val size = BoardSize.of(sizeN)
    val board = Board.of(size, ByteArray(cells.size) { cells[it].toByte() })
    val status = GameStatus.entries[statusOrdinal]
    return GameState(
        size = size,
        seed = seed,
        board = board,
        score = score,
        rngState = rngState,
        status = status,
        moveCount = 0,
        keepGoingAtMove = if (status == GameStatus.KEEP_PLAYING) 0 else -1,
        moveLog = MoveLog.EMPTY,
        snapshots = listOf(Snapshot(0, score, rngState, status, board)),
        undoCount = 0,
    )
}
