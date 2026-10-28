package com.clayboicardi.game2048.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * A DataStore `Serializer` for one JSON document through [AppJson] (ARCHITECTURE.md §4.4). Every
 * decode failure becomes `CorruptionException`, because DataStore hands only that exception to the
 * store's `ReplaceFileCorruptionHandler` (D-045).
 */
class JsonDocumentSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T =
        try {
            AppJson.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Undecodable document", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Invalid document", e)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(AppJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
