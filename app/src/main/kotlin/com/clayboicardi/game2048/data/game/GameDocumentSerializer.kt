package com.clayboicardi.game2048.data.game

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.clayboicardi.game2048.data.JsonDocumentSerializer
import com.clayboicardi.game2048.engine.GameInvariants
import java.io.InputStream
import java.io.OutputStream

/**
 * The `game-N.json` serializer (ARCHITECTURE.md §4.4): JSON through `JsonDocumentSerializer`, then,
 * for a document with `size != 0`, one validation through `GameDocumentMapper.toSavedGame` (the
 * mapping and the D-044 identity checks), `GameInvariants.violations` and
 * `GameInvariants.reachability` (no exponent of the board or a snapshot above N² + 1, D-270). Any
 * failure is a `CorruptionException`, so the store's handler replaces the file with
 * `GameDocument()` (D-045).
 */
object GameDocumentSerializer : Serializer<GameDocument> {
    private val json = JsonDocumentSerializer(GameDocument.serializer(), GameDocument())

    override val defaultValue: GameDocument
        get() = json.defaultValue

    override suspend fun readFrom(input: InputStream): GameDocument {
        val doc = json.readFrom(input)
        if (doc.size != 0) validate(doc)
        return doc
    }

    override suspend fun writeTo(t: GameDocument, output: OutputStream) {
        json.writeTo(t, output)
    }

    private fun validate(doc: GameDocument) {
        val violations =
            try {
                val state = GameDocumentMapper.toSavedGame(doc).state
                // D-270: a tile play cannot make is as corrupt as a broken invariant; the board
                // would otherwise be drawn, or replayed into, a tile the renderer cannot label.
                GameInvariants.violations(state) + listOfNotNull(GameInvariants.reachability(state))
            } catch (e: IllegalArgumentException) {
                throw CorruptionException("Invalid game document", e)
            }
        if (violations.isNotEmpty()) {
            throw CorruptionException("Game document breaks the engine invariants: $violations")
        }
    }
}
