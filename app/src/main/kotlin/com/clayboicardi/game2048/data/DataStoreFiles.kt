package com.clayboicardi.game2048.data

import com.clayboicardi.game2048.engine.BoardSize
import java.io.File

/** Where a DataStore file lives: `context.dataStoreFile(name)` in production (D-040). */
fun interface DataStoreFileProvider {
    fun file(name: String): File
}

/** The on-disk names of the eight documents (D-040). Never rename one: it is the saved data. */
object DataStoreFiles {
    const val SETTINGS = "settings.json"
    const val RECORDS = "records.json"

    fun game(size: BoardSize): String = "game-${size.n}.json"
}
