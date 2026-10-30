package com.clayboicardi.game2048.data

import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsMath
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameInvariants
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.kill
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The QUALITY.md §1.6 corruption table on real DataStore files (D-045). Every case starts from a
 * valid install of all eight files in a fresh folder, writes the case's bytes into one file and
 * reads it through the repository API of a new container. It then checks that the read yields that
 * document's defaults, that the file now holds the defaults, and that every other file in the
 * folder is byte-identical (sha256). Nothing may throw out of the repository API. For a game file,
 * a `GameViewModel` over the container then starts a fresh game of that size and saves it once.
 */
class PersistenceCorruptionTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    /** F02-AC12 */
    @Test
    fun corruptGameFileAffectsOnlyItsSize() = runTest {
        val folder = tmp.newFolder()
        // The v1 settings select 6×6 (`lastBoardSize 6`), so the app starts on that size.
        check(V1Fixtures.SETTINGS.lastBoardSize == S6.n)
        write(folder, DataStoreFiles.SETTINGS, SETTINGS_JSON)
        write(folder, DataStoreFiles.RECORDS, RECORDS_JSON)
        write(folder, DataStoreFiles.game(S4), validGameJson(S4))
        val game6 = DataStoreFiles.game(S6)
        File(folder, game6).writeBytes(GARBAGE)
        val before = hashesExcept(folder, game6)

        val c = testAppContainer(folder)
        val fresh = assertStartsFresh(c, folder, S6, "6x6")
        // RecordsMath.nextGameId(records, 6, null): records.json has no 6×6 entry.
        assertThat(fresh.gameId).isEqualTo(1)
        assertThat(c.settingsRepository.state.value).isEqualTo(V1Fixtures.SETTINGS)
        assertThat(c.recordsRepository.state.value).isEqualTo(V1Fixtures.RECORDS)
        assertThat(c.gameRepository.load(S4)).isEqualTo(VALID_GAMES.getValue(4))
        assertThat(c.gameRepository.submissions(S4)).isEqualTo(0L)
        assertThat(c.settingsRepository.submissions).isEqualTo(0L)
        assertThat(c.recordsRepository.submissions).isEqualTo(0L)
        assertThat(hashesExcept(folder, game6)).isEqualTo(before)
        kill(c)
    }

    /** F07-AC16 */
    @Test
    fun recordsAndSettingsFallBackIndependently() = runTest {
        // Garbage in records.json: the store's first value is Records(); nothing else changes.
        val a = tmp.newFolder()
        writeValidInstall(a)
        File(a, DataStoreFiles.RECORDS).writeBytes(GARBAGE)
        val beforeA = hashesExcept(a, DataStoreFiles.RECORDS)
        val ca = testAppContainer(a)
        assertThat(ca.recordsStore.data.first()).isEqualTo(Records())
        assertThat(ca.recordsRepository.awaitLoaded()).isEqualTo(Records())
        assertThat(ca.settingsRepository.awaitLoaded()).isEqualTo(V1Fixtures.SETTINGS)
        for (size in BoardSize.ALL) {
            assertThat(ca.gameRepository.load(size)).isEqualTo(VALID_GAMES.getValue(size.n))
        }
        testScheduler.advanceUntilIdle()
        assertThat(File(a, DataStoreFiles.RECORDS).readText()).isEqualTo(DEFAULT_RECORDS_JSON)
        assertThat(hashesExcept(a, DataStoreFiles.RECORDS)).isEqualTo(beforeA)
        kill(ca)

        // Separately, garbage in settings.json: Settings(); nothing else changes.
        val b = tmp.newFolder()
        writeValidInstall(b)
        File(b, DataStoreFiles.SETTINGS).writeBytes(GARBAGE)
        val beforeB = hashesExcept(b, DataStoreFiles.SETTINGS)
        val cb = testAppContainer(b)
        assertThat(cb.settingsStore.data.first()).isEqualTo(Settings())
        assertThat(cb.settingsRepository.awaitLoaded()).isEqualTo(Settings())
        assertThat(cb.recordsRepository.awaitLoaded()).isEqualTo(V1Fixtures.RECORDS)
        for (size in BoardSize.ALL) {
            assertThat(cb.gameRepository.load(size)).isEqualTo(VALID_GAMES.getValue(size.n))
        }
        testScheduler.advanceUntilIdle()
        assertThat(File(b, DataStoreFiles.SETTINGS).readText()).isEqualTo(DEFAULT_SETTINGS_JSON)
        assertThat(hashesExcept(b, DataStoreFiles.SETTINGS)).isEqualTo(beforeB)
        kill(cb)
    }

    /** F07-AC17 */
    @Test
    fun invariantViolationIsCorruption() = runTest {
        val fixture = fixtureGame4()
        assertLoads(fixture)
        val cells = fixture.getValue("cells").jsonPrimitive.content
        val snapshots = fixture.getValue("snapshots").jsonArray

        val cases =
            listOf(
                "cells: invalid Base64" to fixture.withKeys("cells" to JsonPrimitive("!!")),
                "cells: 15 bytes" to
                    fixture.withKeys(
                        "cells" to JsonPrimitive(Base64.Default.encode(bytesOf(cells).copyOf(15))),
                    ),
                "moveCount 9: log size mismatch (I2)" to
                    fixture.withKeys("moveCount" to JsonPrimitive(9)),
                "an extra snapshot (I3)" to
                    fixture.withKeys("snapshots" to JsonArray(snapshots + snapshots[0])),
                "keepGoingAtMove 99 > moveCount (I4)" to
                    fixture.withKeys("keepGoingAtMove" to JsonPrimitive(99)),
                "status OVER on a board that can move (I8)" to
                    fixture.withKeys("status" to JsonPrimitive("OVER")),
                "snapshot status ordinal 9" to
                    fixture.withKeys(
                        "snapshots" to
                            JsonArray(listOf(snapshotWithStatus(snapshots[0], ordinal = 9))),
                    ),
                "snapshots[1] OVER on a board that can move (I12)" to overSnapshotAt32(),
            )
        for ((label, doc) in cases) assertFallsBack(DataStoreFiles.game(S4), doc.bytes(), label)
    }

    /** F07-AC23 */
    @Test
    fun invalidFinalizationIdentityIsCorruption() = runTest {
        val fixture = fixtureGame4()
        assertLoads(fixture)
        val entry = fixture.getValue("pendingFinalizations").jsonArray[0].jsonObject

        val cases =
            listOf(
                "gameId 0" to fixture.withKeys("gameId" to JsonPrimitive(0)),
                "no gameId key" to JsonObject(fixture - "gameId"),
                "entry id equal to gameId" to
                    fixture.withEntries(entry.withKeys("gameId" to JsonPrimitive(7))),
                "entry score -1" to
                    fixture.withEntries(entry.withKeys("score" to JsonPrimitive(-1))),
                "entry maxExp 128" to
                    fixture.withEntries(entry.withKeys("maxExp" to JsonPrimitive(128))),
                "entry twice (ids 6, 6)" to fixture.withEntries(entry, entry),
                "17 entries, ids 1..17, gameId 18" to
                    fixture
                        .withEntries(
                            *Array(17) { i -> entry.withKeys("gameId" to JsonPrimitive(i + 1)) },
                        )
                        .withKeys("gameId" to JsonPrimitive(18)),
            )
        for ((label, doc) in cases) assertFallsBack(DataStoreFiles.game(S4), doc.bytes(), label)
    }

    /** F07-AC34 */
    @Test
    fun unreachableExponentIsCorruption() = runTest {
        // The fixture's last cell is empty; 17 there is the 4×4 maximum (N² + 1) and still loads.
        val fixture = fixtureGame4()
        assertLoads(fixture.withCell(LAST_CELL_4, 17))
        val entry = fixture.getValue("pendingFinalizations").jsonArray[0].jsonObject
        val eight = AppJson.parseToJsonElement(validGameJson(S8)).jsonObject
        assertLoads(eight.withCell(0, 65), S8)

        val cases =
            listOf(18, 70, 100, Board.MAX_EXP).map { exp ->
                "cells: exponent $exp" to fixture.withCell(LAST_CELL_4, exp)
            } +
                listOf(
                    "entry maxExp 18" to
                        fixture.withEntries(entry.withKeys("maxExp" to JsonPrimitive(18))),
                    "entry maxExp 70" to
                        fixture.withEntries(entry.withKeys("maxExp" to JsonPrimitive(70))),
                )
        for ((label, doc) in cases) assertFallsBack(DataStoreFiles.game(S4), doc.bytes(), label)
        // 8×8's maximum is 65: 66 is past it, though a tile label could still draw it.
        assertFallsBack(DataStoreFiles.game(S8), eight.withCell(0, 66).bytes(), "8x8: exponent 66")
    }

    /** F07-AC35 */
    @Test
    fun unreachableSnapshotExponentIsCorruption() = runTest {
        // snapshots[1] is the base every undo of moves 33..40 replays from; the board stays valid.
        val game = fortyMoveGame()
        assertLoads(game.withSnapshotCell(1, LAST_CELL_4, 17))
        val cases =
            listOf(18, 70, 100, Board.MAX_EXP).map { exp ->
                "snapshots[1]: exponent $exp" to game.withSnapshotCell(1, LAST_CELL_4, exp)
            }
        for ((label, doc) in cases) assertFallsBack(DataStoreFiles.game(S4), doc.bytes(), label)
    }

    /** QUALITY.md §1.6 rows 1–2: 64 × 0xFF, and valid JSON cut after 10 bytes, in each file. */
    @Test
    fun garbageAndTruncationInEveryFileFallBack() = runTest {
        for (name in ALL_FILES) {
            assertFallsBack(name, GARBAGE, "$name: 64 x 0xFF")
            assertFallsBack(
                name,
                validJson(name).encodeToByteArray().copyOf(10),
                "$name: truncated",
            )
        }
    }

    /** QUALITY.md §1.6 rows 3–4: wrong types are corruption, not coercion. */
    @Test
    fun wrongTypesFallBack() = runTest {
        assertFallsBack(
            DataStoreFiles.SETTINGS,
            """{"lastBoardSize": "six"}""".encodeToByteArray(),
            "settings: lastBoardSize \"six\"",
        )
        assertFallsBack(
            DataStoreFiles.RECORDS,
            """{"sizes": {"4": {"bestScore": "x"}}}""".encodeToByteArray(),
            "records: bestScore \"x\"",
        )
    }

    /** QUALITY.md §1.6 last row: a stray temp file of an interrupted write is ignored. */
    @Test
    fun strayTempFileIsIgnored() = runTest {
        val folder = tmp.newFolder()
        writeValidInstall(folder)
        File(folder, DataStoreFiles.game(S4) + ".tmp").writeBytes(GARBAGE)
        val before = hashesExcept(folder, except = null)

        val c = testAppContainer(folder)
        assertThat(c.gameRepository.load(S4)).isEqualTo(VALID_GAMES.getValue(4))
        assertThat(c.recordsRepository.awaitLoaded()).isEqualTo(V1Fixtures.RECORDS)
        testScheduler.advanceUntilIdle()
        assertThat(hashesExcept(folder, except = null)).isEqualTo(before)
        kill(c)
    }

    /** A valid game of another size in `game-4.json` (a hand-copied file) reads as no game. */
    @Test
    fun gameOfAnotherSizeLoadsAsNoGame() = runTest {
        val folder = tmp.newFolder()
        writeValidInstall(folder)
        write(folder, DataStoreFiles.game(S4), validGameJson(BoardSize.of(6)))
        val before = hashesExcept(folder, except = null)

        val c = testAppContainer(folder)
        assertThat(c.gameRepository.load(S4)).isNull()
        assertThat(c.gameRepository.peek(S4)).isNull()
        assertThat(c.gameRepository.load(BoardSize.of(6))).isEqualTo(VALID_GAMES.getValue(6))
        testScheduler.advanceUntilIdle()
        assertThat(hashesExcept(folder, except = null)).isEqualTo(before)
        kill(c)
    }

    /**
     * One corruption case: [bytes] in [target] of an otherwise valid install, then a read through
     * the repository API. Asserts the defaults, the rewritten file and the untouched other files.
     */
    private suspend fun TestScope.assertFallsBack(target: String, bytes: ByteArray, label: String) {
        val folder = tmp.newFolder()
        writeValidInstall(folder)
        val gameSize = BoardSize.ALL.singleOrNull { DataStoreFiles.game(it) == target }
        // A game file case selects its size, so that the app starts on it.
        if (gameSize != null) write(folder, DataStoreFiles.SETTINGS, settingsJson(gameSize))
        File(folder, target).writeBytes(bytes)
        val before = hashesExcept(folder, target)

        val c = testAppContainer(folder)
        when (target) {
            DataStoreFiles.SETTINGS ->
                assertWithMessage(label)
                    .that(c.settingsRepository.awaitLoaded())
                    .isEqualTo(Settings())
            DataStoreFiles.RECORDS ->
                assertWithMessage(label)
                    .that(c.recordsRepository.awaitLoaded())
                    .isEqualTo(Records())
            else -> {
                assertWithMessage(label).that(c.gameRepository.load(sizeOf(target))).isNull()
                // Nothing is counted from a rejected document: the records are as they were.
                assertWithMessage("$label: records")
                    .that(c.recordsRepository.awaitLoaded())
                    .isEqualTo(V1Fixtures.RECORDS)
            }
        }
        testScheduler.advanceUntilIdle()
        assertWithMessage("$label: the file holds the defaults")
            .that(File(folder, target).readText())
            .isEqualTo(defaultJson(target))
        // "No saved game": the app starts a fresh game of that size (F07-AC17, F07-AC23).
        if (gameSize != null) assertStartsFresh(c, folder, gameSize, label)
        assertWithMessage("$label: every other file is unchanged")
            .that(hashesExcept(folder, target))
            .isEqualTo(before)
        kill(c)
    }

    /**
     * The app's path for a size whose file holds no saved game (ARCHITECTURE.md §4.9 `load`,
     * `restore`): a `GameViewModel` over [c], whose settings select [size], becomes ready on [size]
     * with a fresh game, saves it once and writes it into the size's file; settings and records see
     * no save. Returns that game.
     */
    private suspend fun TestScope.assertStartsFresh(
        c: AppContainer,
        folder: File,
        size: BoardSize,
        label: String,
    ): SavedGame {
        val vm =
            GameViewModel(
                c.gameRepository,
                c.recordsRepository,
                c.settingsRepository,
                c.clock,
                c.seedSource,
            )
        testScheduler.runCurrent()
        assertWithMessage("$label: ready").that(vm.ready.value).isTrue()
        assertWithMessage("$label: size").that(vm.uiState.value.size).isEqualTo(size)
        val records = c.recordsRepository.state.value
        val fresh = checkNotNull(c.gameRepository.peek(size)) { "$label: no current game" }
        // The first seed of the container's FixedSeedSource(0x2048).
        assertWithMessage("$label: fresh state")
            .that(fresh.state)
            .isEqualTo(Game.newGame(size, 0x2048L))
        assertWithMessage("$label: moveCount").that(fresh.state.moveCount).isEqualTo(0)
        assertWithMessage("$label: gameId")
            .that(fresh.gameId)
            .isEqualTo(RecordsMath.nextGameId(records, size, null))
        assertWithMessage("$label: bestAtStart")
            .that(fresh.bestAtStart)
            .isEqualTo(c.recordsRepository.best(size))
        assertWithMessage("$label: elapsedMs").that(fresh.elapsedMs).isEqualTo(0)
        assertWithMessage("$label: pending").that(fresh.pendingFinalizations).isEmpty()
        assertWithMessage("$label: board")
            .that(vm.board.value.cells)
            .containsExactlyElementsIn(List(size.cellCount) { fresh.state.board[it] })
            .inOrder()
        c.settingsRepository.awaitIdle()
        c.recordsRepository.awaitIdle()
        c.gameRepository.awaitIdle()
        testScheduler.runCurrent()
        assertWithMessage("$label: one game save")
            .that(c.gameRepository.submissions(size))
            .isEqualTo(1L)
        assertWithMessage("$label: no settings save")
            .that(c.settingsRepository.submissions)
            .isEqualTo(0L)
        assertWithMessage("$label: no records save")
            .that(c.recordsRepository.submissions)
            .isEqualTo(0L)
        assertWithMessage("$label: records")
            .that(c.recordsRepository.state.value)
            .isEqualTo(records)
        assertWithMessage("$label: the file holds the fresh game")
            .that(decodeGame(File(folder, DataStoreFiles.game(size)).readText()))
            .isEqualTo(fresh)
        return fresh
    }

    /** Control case: [doc] in the file of [size] loads as the game it encodes. */
    private suspend fun TestScope.assertLoads(doc: JsonObject, size: BoardSize = S4) {
        val folder = tmp.newFolder()
        writeValidInstall(folder)
        File(folder, DataStoreFiles.game(size)).writeBytes(doc.bytes())
        val c = testAppContainer(folder)
        assertThat(c.gameRepository.load(size)).isEqualTo(decodeGame(doc.toString()))
        kill(c)
    }

    private fun writeValidInstall(folder: File) {
        for (name in ALL_FILES) write(folder, name, validJson(name))
    }

    private fun write(folder: File, name: String, json: String) {
        File(folder, name).writeText(json)
    }

    /** sha256 of every file in [folder] except [except], by name. */
    private fun hashesExcept(folder: File, except: String?): Map<String, String> =
        folder
            .listFiles()
            .orEmpty()
            .filter { it.name != except }
            .associate { f ->
                val digest = MessageDigest.getInstance("SHA-256").digest(f.readBytes())
                f.name to digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
            }
            .toSortedMap()

    private fun fixtureGame4(): JsonObject =
        AppJson.parseToJsonElement(V1Fixtures.text(V1Fixtures.GAME_4_FILE)).jsonObject

    private fun JsonObject.withEntries(vararg entries: JsonObject): JsonObject =
        withKeys("pendingFinalizations" to JsonArray(entries.toList()))

    private companion object {
        val S4 = BoardSize.of(4)
        val S8 = BoardSize.of(8)

        /** The bottom-right cell of a 4×4 board, empty in the v1 fixture. */
        const val LAST_CELL_4 = 15

        /** Snapshot bytes before the N² cells (ARCHITECTURE.md §3.4). */
        const val SNAPSHOT_CELLS_OFFSET = 21
        val GARBAGE = ByteArray(64) { 0xFF.toByte() }

        val ALL_FILES: List<String> =
            listOf(DataStoreFiles.SETTINGS, DataStoreFiles.RECORDS) +
                BoardSize.ALL.map(DataStoreFiles::game)

        /** A valid game per size: the v1 game-4 content, FIX_MID_3, and new games elsewhere. */
        val VALID_GAMES: Map<Int, SavedGame> =
            BoardSize.ALL.associate { size ->
                size.n to
                    when (size.n) {
                        3 -> GoldenFixtures.FIX_MID_3
                        4 -> V1Fixtures.GAME_4
                        else ->
                            SavedGame(
                                Game.newGame(size, 0x2048L + size.n),
                                0,
                                0,
                                false,
                                1,
                                emptyList(),
                            )
                    }
            }

        val S6 = BoardSize.of(6)
        val SETTINGS_JSON = AppJson.encodeToString(Settings.serializer(), V1Fixtures.SETTINGS)
        val RECORDS_JSON = AppJson.encodeToString(Records.serializer(), V1Fixtures.RECORDS)
        val DEFAULT_SETTINGS_JSON = AppJson.encodeToString(Settings.serializer(), Settings())
        val DEFAULT_RECORDS_JSON = AppJson.encodeToString(Records.serializer(), Records())
        val DEFAULT_GAME_JSON = AppJson.encodeToString(GameDocument.serializer(), GameDocument())

        fun sizeOf(name: String): BoardSize =
            BoardSize.ALL.single { DataStoreFiles.game(it) == name }

        fun validGameJson(size: BoardSize): String = encodeGame(VALID_GAMES.getValue(size.n))

        /** The v1 settings with [size] selected. */
        fun settingsJson(size: BoardSize): String =
            AppJson.encodeToString(
                Settings.serializer(),
                V1Fixtures.SETTINGS.copy(lastBoardSize = size.n),
            )

        fun validJson(name: String): String =
            when (name) {
                DataStoreFiles.SETTINGS -> SETTINGS_JSON
                DataStoreFiles.RECORDS -> RECORDS_JSON
                else -> validGameJson(sizeOf(name))
            }

        fun defaultJson(name: String): String =
            when (name) {
                DataStoreFiles.SETTINGS -> DEFAULT_SETTINGS_JSON
                DataStoreFiles.RECORDS -> DEFAULT_RECORDS_JSON
                else -> DEFAULT_GAME_JSON
            }

        fun bytesOf(base64: String): ByteArray = Base64.Default.decode(base64)

        fun JsonObject.withKeys(vararg entries: Pair<String, JsonElement>): JsonObject =
            JsonObject(this + entries)

        fun JsonObject.bytes(): ByteArray = toString().encodeToByteArray()

        /** [snapshot] (Base64) with its status byte (offset 20, ARCHITECTURE.md §3.4) replaced. */
        fun snapshotWithStatus(snapshot: JsonElement, ordinal: Int): JsonPrimitive {
            val bytes = bytesOf(snapshot.jsonPrimitive.content)
            bytes[SNAPSHOT_STATUS_OFFSET] = ordinal.toByte()
            return JsonPrimitive(Base64.Default.encode(bytes))
        }

        /**
         * A valid 40-move 4×4 game (each move the first accepted of DOWN, LEFT, RIGHT, UP from seed
         * 0x2048) whose `snapshots[1]` claims `OVER` at move 32, which no accepted move stores
         * (D-214, I12): the game went on to move 40, and that board can still move.
         */
        fun overSnapshotAt32(): JsonObject {
            val json = fortyMoveGame()
            val snapshots = json.getValue("snapshots").jsonArray
            val over = snapshotWithStatus(snapshots[1], GameStatus.OVER.ordinal)
            return json.withKeys("snapshots" to JsonArray(listOf(snapshots[0], over)))
        }

        /**
         * The document of a valid 40-move 4×4 game: each move the first accepted of DOWN, LEFT,
         * RIGHT, UP from seed 0x2048, so `snapshots[1]` (move 32) can still move.
         */
        fun fortyMoveGame(): JsonObject {
            val order = listOf(Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP)
            var s = Game.newGame(S4, 0x2048)
            repeat(40) {
                s = order.firstNotNullOf { d -> Game.move(s, d) as? MoveOutcome.Moved }.state
            }
            check(
                s.moveCount == 40 &&
                    s.snapshots.size == 2 &&
                    GameInvariants.violations(s).isEmpty(),
            )
            check(s.snapshots[1].status == GameStatus.PLAYING && Game.canMove(s.snapshots[1].board))
            val doc = GameDocumentMapper.toDocument(SavedGame(s, 0, 0, false, 1, emptyList()))
            return AppJson.encodeToJsonElement(GameDocument.serializer(), doc).jsonObject
        }

        /** This document with cell [index] of the current board set to exponent [exp]. */
        fun JsonObject.withCell(index: Int, exp: Int): JsonObject {
            val cells = bytesOf(getValue("cells").jsonPrimitive.content)
            cells[index] = exp.toByte()
            return withKeys("cells" to JsonPrimitive(Base64.Default.encode(cells)))
        }

        /** This document with cell [index] of `snapshots[k]` set to exponent [exp]. */
        fun JsonObject.withSnapshotCell(k: Int, index: Int, exp: Int): JsonObject {
            val snapshots = getValue("snapshots").jsonArray.toMutableList()
            val bytes = bytesOf(snapshots[k].jsonPrimitive.content)
            bytes[SNAPSHOT_CELLS_OFFSET + index] = exp.toByte()
            snapshots[k] = JsonPrimitive(Base64.Default.encode(bytes))
            return withKeys("snapshots" to JsonArray(snapshots))
        }

        const val SNAPSHOT_STATUS_OFFSET = 20
    }
}
