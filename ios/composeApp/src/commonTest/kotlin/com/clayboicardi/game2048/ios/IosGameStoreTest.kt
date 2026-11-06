package com.clayboicardi.game2048.ios

import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path
import okio.Path.Companion.toPath

/** The save must never lose data: atomic writes, quarantine on damage, bests kept (D-272). */
class IosGameStoreTest {
    private val fs = gameStoreFileSystem()
    private val dirs = mutableListOf<Path>()

    private fun freshDir(name: String): Path {
        val dir = gameStoreDirectory().toPath() / "store-test-$name"
        fs.deleteRecursively(dir)
        fs.createDirectories(dir)
        dirs += dir
        return dir
    }

    @AfterTest fun cleanUp() = dirs.forEach { fs.deleteRecursively(it) }

    @Test
    fun saveThenLoadRoundTripsTheGameAndBests() {
        val dir = freshDir("roundtrip")
        var state = Game.newGame(BoardSize.of(4), 0x2048L)
        for (d in listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)) {
            val outcome = Game.move(state, d)
            if (outcome is MoveOutcome.Moved) state = outcome.state
        }
        val store = IosGameStore(fs, dir.toString())
        store.save(state, mapOf(4 to 1234L, 5 to 77L))

        val loaded = store.load()
        val current = assertNotNull(loaded.current)
        assertEquals(state.board, current.board)
        assertEquals(state.score, current.score)
        assertEquals(state.rngState, current.rngState)
        assertEquals(state.status, current.status)
        assertEquals(mapOf(4 to 1234L, 5 to 77L), loaded.best)
        // The resumed game keeps playing through the engine.
        assertTrue(Game.canMove(current) || current.status == GameStatus.OVER)
    }

    @Test
    fun everyStatusRebuildsAValidEngineState() {
        for (status in GameStatus.entries) {
            val dir = freshDir("status-${status.ordinal}")
            fs.write(dir / IosGameStore.FILE_NAME) { writeUtf8(saveJson(status.ordinal)) }

            val loaded = IosGameStore(fs, dir.toString()).load()
            val current = assertNotNull(loaded.current, "status $status did not rebuild")
            assertEquals(status, current.status)
            assertEquals(mapOf(4 to 100L), loaded.best)
            if (status == GameStatus.WON) {
                assertEquals(GameStatus.KEEP_PLAYING, Game.keepGoing(current).status)
            }
        }
    }

    @Test
    fun unreadableSaveIsQuarantinedNotOverwritten() {
        val dir = freshDir("corrupt")
        fs.write(dir / IosGameStore.FILE_NAME) { writeUtf8("{ this is not json") }
        val store = IosGameStore(fs, dir.toString())

        val loaded = store.load()
        assertNull(loaded.current)
        assertTrue(loaded.best.isEmpty())
        assertFalse(fs.exists(dir / IosGameStore.FILE_NAME))
        val kept = fs.read(dir / IosGameStore.CORRUPT_NAME) { readUtf8() }
        assertEquals("{ this is not json", kept)

        // A later save writes a fresh file and leaves the quarantined bytes alone.
        store.save(Game.newGame(BoardSize.DEFAULT, 1L), emptyMap())
        assertTrue(fs.exists(dir / IosGameStore.FILE_NAME))
        assertEquals("{ this is not json", fs.read(dir / IosGameStore.CORRUPT_NAME) { readUtf8() })
    }

    @Test
    fun saveLeavesNoTempFileBehind() {
        val dir = freshDir("tmp")
        IosGameStore(fs, dir.toString()).save(Game.newGame(BoardSize.DEFAULT, 2L), mapOf(4 to 8L))
        assertTrue(fs.exists(dir / IosGameStore.FILE_NAME))
        assertFalse(fs.exists(dir / IosGameStore.TMP_NAME))
    }

    @Test
    fun aGameTheEngineRejectsDropsOnlyTheGame() {
        val dir = freshDir("invalid-game")
        // 3 cells for a 4×4 board: Board.of rejects it.
        fs.write(dir / IosGameStore.FILE_NAME) {
            writeUtf8(
                """{"current":{"sizeN":4,"seed":1,"cells":[1,1,0],"score":4,"rngState":7,""" +
                    """"statusOrdinal":0},"best":{"4":512}}""",
            )
        }
        val loaded = IosGameStore(fs, dir.toString()).load()
        assertNull(loaded.current)
        assertEquals(mapOf(4 to 512L), loaded.best)
    }

    @Test
    fun missingSaveLoadsEmpty() {
        val dir = freshDir("missing")
        val loaded = IosGameStore(fs, dir.toString()).load()
        assertNull(loaded.current)
        assertTrue(loaded.best.isEmpty())
    }

    /** A 4×4 save with two tiles, score 4 and the given status ordinal. */
    private fun saveJson(statusOrdinal: Int): String {
        val cells = List(16) { if (it < 2) 1 else 0 }.joinToString(",")
        return """{"current":{"sizeN":4,"seed":1,"cells":[$cells],"score":4,"rngState":7,""" +
            """"statusOrdinal":$statusOrdinal},"best":{"4":100}}"""
    }
}
