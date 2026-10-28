package com.clayboicardi.game2048.data.game

import com.clayboicardi.game2048.data.records.Finalization
import com.clayboicardi.game2048.data.records.MAX_PENDING_FINALIZATIONS
import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveLog
import com.clayboicardi.game2048.engine.Snapshot
import kotlin.io.encoding.Base64

/**
 * `SavedGame` ↔ `GameDocument` (ARCHITECTURE.md §4.3–§4.4). `GameState` fields map 1:1, `StatusDto`
 * ↔ `GameStatus` by name, byte fields through `kotlin.io.encoding.Base64.Default`, and each
 * `pendingFinalizations` element field by field with `size` from the document.
 */
object GameDocumentMapper {
    /** The document of [game] (`schemaVersion = 1`). */
    fun toDocument(game: SavedGame): GameDocument {
        val s = game.state
        return GameDocument(
            schemaVersion = 1,
            size = s.size.n,
            seed = s.seed,
            cells = Base64.Default.encode(s.board.toByteArray()),
            score = s.score,
            rngState = s.rngState,
            status = StatusDto.valueOf(s.status.name),
            moveCount = s.moveCount,
            keepGoingAtMove = s.keepGoingAtMove,
            moveLog = Base64.Default.encode(s.moveLog.toPackedBytes()),
            snapshots = s.snapshots.map { Base64.Default.encode(it.toBytes()) },
            undoCount = s.undoCount,
            elapsedMs = game.elapsedMs,
            bestAtStart = game.bestAtStart,
            newBestAnnounced = game.newBestAnnounced,
            gameId = game.gameId,
            pendingFinalizations = game.pendingFinalizations.map(::entryOf),
        )
    }

    /**
     * The game of [doc], which must have `size != 0`. Throws `IllegalArgumentException` for any
     * invalid mapping (`BoardSize.of`, `Board.of`, `MoveLog`, `Snapshot`, Base64, `GameState.init`)
     * and for the D-044 identity checks of §4.4; `GameDocumentSerializer` turns that, and a
     * non-empty `GameInvariants.violations`, into `CorruptionException`.
     */
    fun toSavedGame(doc: GameDocument): SavedGame {
        require(doc.size != 0) { "The size-0 document means \"no saved game\"" }
        val size = BoardSize.of(doc.size)
        requireIdentity(doc)
        val state =
            GameState(
                size = size,
                seed = doc.seed,
                board = Board.of(size, Base64.Default.decode(doc.cells)),
                score = doc.score,
                rngState = doc.rngState,
                status = GameStatus.valueOf(doc.status.name),
                moveCount = doc.moveCount,
                keepGoingAtMove = doc.keepGoingAtMove,
                moveLog =
                    MoveLog.fromPackedBytes(Base64.Default.decode(doc.moveLog), doc.moveCount),
                snapshots =
                    doc.snapshots.map { Snapshot.fromBytes(Base64.Default.decode(it), size) },
                undoCount = doc.undoCount,
            )
        return SavedGame(
            state = state,
            elapsedMs = doc.elapsedMs,
            bestAtStart = doc.bestAtStart,
            newBestAnnounced = doc.newBestAnnounced,
            gameId = doc.gameId,
            pendingFinalizations = doc.pendingFinalizations.map { finalizationOf(it, size) },
        )
    }

    /**
     * The D-044 identity checks of ARCHITECTURE.md §4.4: `gameId >= 1` (a missing key decodes to 0,
     * so it fails here); at most `MAX_PENDING_FINALIZATIONS` entries, with strictly increasing ids,
     * each in `1 until gameId` (recovery R1 depends on that order); no negative counter; `maxExp`
     * in `1..Board.MAX_EXP`, which also bounds the `tileReach` growth of a finalization.
     */
    private fun requireIdentity(doc: GameDocument) {
        require(doc.gameId >= 1) { "gameId must be at least 1, is ${doc.gameId}" }
        val entries = doc.pendingFinalizations
        require(entries.size <= MAX_PENDING_FINALIZATIONS) {
            "At most $MAX_PENDING_FINALIZATIONS recovery entries, found ${entries.size}"
        }
        var previous = 0L
        for (e in entries) {
            require(e.gameId > previous && e.gameId < doc.gameId) {
                "Recovery entry ids must increase strictly within 1 until ${doc.gameId}, " +
                    "found ${e.gameId} after $previous"
            }
            require(minOf(e.score, e.elapsedMs, minOf(e.moveCount, e.undoCount).toLong()) >= 0) {
                "Recovery entry ${e.gameId} has a negative counter"
            }
            require(e.maxExp in 1..Board.MAX_EXP) {
                "Recovery entry ${e.gameId} has maxExp ${e.maxExp}, outside 1..${Board.MAX_EXP}"
            }
            previous = e.gameId
        }
    }

    private fun entryOf(f: Finalization): FinalizationDocument =
        FinalizationDocument(
            gameId = f.gameId,
            status = StatusDto.valueOf(f.status.name),
            score = f.score,
            moveCount = f.moveCount,
            elapsedMs = f.elapsedMs,
            undoCount = f.undoCount,
            maxExp = f.maxExp,
        )

    private fun finalizationOf(e: FinalizationDocument, size: BoardSize): Finalization =
        Finalization(
            size = size,
            gameId = e.gameId,
            status = GameStatus.valueOf(e.status.name),
            score = e.score,
            moveCount = e.moveCount,
            elapsedMs = e.elapsedMs,
            undoCount = e.undoCount,
            maxExp = e.maxExp,
        )
}
