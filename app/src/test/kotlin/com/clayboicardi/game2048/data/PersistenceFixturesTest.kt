package com.clayboicardi.game2048.data

import androidx.datastore.core.Serializer
import androidx.lifecycle.ViewModelStore
import com.clayboicardi.game2048.BuildConfig
import com.clayboicardi.game2048.about.CHANGELOG
import com.clayboicardi.game2048.about.decideWhatsNew
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.GameDocumentSerializer
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Finalization
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsMath
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.MoveTiming
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SwipeSensitivity
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Direction.DOWN
import com.clayboicardi.game2048.engine.Direction.LEFT
import com.clayboicardi.game2048.engine.Direction.RIGHT
import com.clayboicardi.game2048.engine.Direction.UP
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameInvariants
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.testing.LongGameGenerator
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.kill
import com.clayboicardi.game2048.testing.testAppContainer
import com.clayboicardi.game2048.testing.testAppViewModel
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The write-once v1 fixtures (D-041; QUALITY.md §1.5; ARCHITECTURE.md §9.4). While a fixture is
 * missing from `src/test/resources/fixtures/v1/`, every test that reads one writes the generated
 * documents to `app/build/generated-fixtures/` and fails with the copy instruction. Once committed,
 * the files are decoded with the current serializers and compared with the literals below, which
 * are never regenerated: a failure after a code change means the on-disk format changed and needs a
 * `schemaVersion` bump and a `DataMigration`, not a new fixture.
 */
class PersistenceFixturesTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    /** F07-AC14 */
    @Test
    fun v1FixturesDecode() = runTest {
        val settings = SETTINGS_SERIALIZER.decode(V1Fixtures.text(V1Fixtures.SETTINGS_FILE))
        assertSettingsFixture(settings)

        val records = RECORDS_SERIALIZER.decode(V1Fixtures.text(V1Fixtures.RECORDS_FILE))
        assertThat(records.schemaVersion).isEqualTo(1)
        assertThat(records.sizes.keys).containsExactly("3", "4")
        assertThat(records.sizes["4"]).isEqualTo(RECORD_4)
        assertThat(records.sizes["3"]).isEqualTo(RECORD_3)
        assertThat(records.lastFinalizedGameId).containsExactly("4", 6L, "3", 5L)
        assertThat(RecordsMath.aggregate(records)).isEqualTo(AGGREGATE)

        val game4 = decodeGame(V1Fixtures.text(V1Fixtures.GAME_4_FILE))
        assertGame4Fixture(game4)
        assertThat(game4.elapsedMs).isEqualTo(61_000L)
        assertThat(game4.newBestAnnounced).isFalse()

        val long = decodeGame(V1Fixtures.text(V1Fixtures.GAME_8_LONG_FILE))
        assertLongGameFixture(long)

        // Consistency rule (QUALITY.md §1.5): neither recovery step runs on these two fixtures.
        assertThat(RecordsMath.isFinalized(records, S4, 6)).isTrue()
        assertThat(RecordsMath.isFinalized(records, S4, 7)).isFalse()
        assertThat(game4.pendingFinalizations.fold(records, RecordsMath::finalize))
            .isSameInstanceAs(records)

        // Decode → encode → decode gives an equal game (not a byte comparison, D-041).
        assertThat(decodeGame(encodeGame(game4))).isEqualTo(game4)
        assertThat(decodeGame(encodeGame(long))).isEqualTo(long)
        assertThat(SETTINGS_SERIALIZER.decode(SETTINGS_SERIALIZER.encode(settings)))
            .isEqualTo(settings)
        assertThat(RECORDS_SERIALIZER.decode(RECORDS_SERIALIZER.encode(records))).isEqualTo(records)

        // The same files through the real stores and repositories of one container.
        val folder = tmp.newFolder()
        File(folder, DataStoreFiles.SETTINGS).writeText(V1Fixtures.text(V1Fixtures.SETTINGS_FILE))
        File(folder, DataStoreFiles.RECORDS).writeText(V1Fixtures.text(V1Fixtures.RECORDS_FILE))
        File(folder, DataStoreFiles.game(S4)).writeText(V1Fixtures.text(V1Fixtures.GAME_4_FILE))
        File(folder, DataStoreFiles.game(S8))
            .writeText(V1Fixtures.text(V1Fixtures.GAME_8_LONG_FILE))
        val before = folder.listFiles().orEmpty().associate { it.name to it.readBytes().toList() }
        val container = testAppContainer(folder)
        assertThat(container.settingsRepository.awaitLoaded()).isEqualTo(settings)
        assertThat(container.recordsRepository.awaitLoaded()).isEqualTo(records)
        assertThat(container.gameRepository.load(S4)).isEqualTo(game4)
        assertThat(container.gameRepository.load(S8)).isEqualTo(long)
        assertThat(container.recordsRepository.isFinalized(game4)).isFalse()
        val loaded = container.recordsRepository.state.value
        container.recordsRepository.recover(game4)
        assertThat(container.recordsRepository.state.value).isSameInstanceAs(loaded)
        assertNoWhatsNew(container, settings)
        container.recordsRepository.awaitIdle()
        testScheduler.advanceUntilIdle()
        val after = folder.listFiles().orEmpty().associate { it.name to it.readBytes().toList() }
        assertThat(after).isEqualTo(before)
        kill(container)
    }

    /** F07-AC15 */
    @Test
    fun newerDocumentsDecodeWithDefaults() = runTest {
        val settingsText = V1Fixtures.text(V1Fixtures.SETTINGS_FILE)
        val settings = SETTINGS_SERIALIZER.decode(settingsText)
        val settingsJson = AppJson.parseToJsonElement(settingsText).jsonObject

        // An unknown key is ignored; unknown enum names coerce to the field default.
        val newer =
            settingsJson.withKeys(
                "futureField" to JsonPrimitive("from a later version"),
                "palette" to JsonPrimitive("SEPIA"),
                "motion" to JsonPrimitive("SUPER"),
            )
        val decodedNewer = SETTINGS_SERIALIZER.decode(newer.toString())
        assertThat(decodedNewer.palette).isEqualTo(PaletteId.CLASSIC)
        assertThat(decodedNewer.motion).isEqualTo(MotionPreference.FULL)
        assertThat(decodedNewer)
            .isEqualTo(settings.copy(palette = PaletteId.CLASSIC, motion = MotionPreference.FULL))

        // A missing field whose default is valid decodes to that default.
        val noKeepScreenOn =
            SETTINGS_SERIALIZER.decode(settingsJson.withoutKeys("keepScreenOn").toString())
        assertThat(noKeepScreenOn.keepScreenOn).isFalse()
        assertThat(noKeepScreenOn).isEqualTo(settings.copy(keepScreenOn = false))
        assertSettingsFixture(noKeepScreenOn.copy(keepScreenOn = true))

        // Unknown keys at the top level and inside a SizeRecord.
        val recordsText = V1Fixtures.text(V1Fixtures.RECORDS_FILE)
        val records = RECORDS_SERIALIZER.decode(recordsText)
        val recordsJson = AppJson.parseToJsonElement(recordsText).jsonObject
        val sizesJson = recordsJson.getValue("sizes").jsonObject
        val newerRecords =
            recordsJson.withKeys(
                "futureField" to JsonPrimitive(1),
                "sizes" to
                    sizesJson.withKeys(
                        "4" to
                            sizesJson
                                .getValue("4")
                                .jsonObject
                                .withKeys("futureField" to JsonPrimitive(2)),
                    ),
            )
        assertThat(RECORDS_SERIALIZER.decode(newerRecords.toString())).isEqualTo(records)

        // The game: an unknown key, then newBestAnnounced and elapsedMs removed.
        val gameText = V1Fixtures.text(V1Fixtures.GAME_4_FILE)
        val game = decodeGame(gameText)
        val gameJson = AppJson.parseToJsonElement(gameText).jsonObject
        val newerGame = gameJson.withKeys("futureField" to JsonPrimitive(true))
        assertThat(decodeGame(newerGame.toString())).isEqualTo(game)
        val older = decodeGame(gameJson.withoutKeys("newBestAnnounced", "elapsedMs").toString())
        assertThat(older.newBestAnnounced).isFalse()
        assertThat(older.elapsedMs).isEqualTo(0L)
        assertGame4Fixture(older)
        assertThat(older).isEqualTo(game.copy(newBestAnnounced = false, elapsedMs = 0))
    }

    /**
     * The fixture's settings hold versionCode 30000, from before D-271 renumbered the first release
     * to 10000, and D-009 shows What's new only when the last seen versionCode is below the current
     * one: an `AppViewModel` over the fixture files shows no sheet and stores nothing.
     */
    private fun TestScope.assertNoWhatsNew(container: AppContainer, settings: Settings) {
        assertThat(settings.lastSeenVersionCode).isGreaterThan(BuildConfig.VERSION_CODE)
        val decision =
            decideWhatsNew(settings.lastSeenVersionCode, BuildConfig.VERSION_CODE, CHANGELOG)
        assertThat(decision.entries).isEmpty()
        assertThat(decision.storeVersionCode).isNull()
        val store = ViewModelStore()
        try {
            val appViewModel = testAppViewModel(container, store)
            testScheduler.runCurrent()
            assertThat(appViewModel.whatsNew.value).isNull()
            assertThat(container.settingsRepository.submissions).isEqualTo(0L)
        } finally {
            store.clear()
        }
    }

    private fun assertSettingsFixture(s: Settings) {
        assertThat(s.schemaVersion).isEqualTo(1)
        assertThat(s.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(s.palette).isEqualTo(PaletteId.NEON)
        assertThat(s.highContrast).isTrue()
        assertThat(s.dynamicChrome).isTrue()
        assertThat(s.motion).isEqualTo(MotionPreference.REDUCED)
        assertThat(s.haptics).isFalse()
        assertThat(s.moveTiming).isEqualTo(MoveTiming.ON_RELEASE)
        assertThat(s.swipeSensitivity).isEqualTo(SwipeSensitivity.HIGH)
        assertThat(s.immersive).isTrue()
        assertThat(s.keepScreenOn).isTrue()
        assertThat(s.lastBoardSize).isEqualTo(6)
        // Written before D-271 renumbered the first release; the fixture is write-once (D-041).
        assertThat(s.lastSeenVersionCode).isEqualTo(30000)
        assertThat(s.onboardingDone).isTrue()
    }

    /**
     * Every `game-4.json` literal except `elapsedMs` and `newBestAnnounced` (F07-AC15 drops them).
     */
    private fun assertGame4Fixture(g: SavedGame) {
        val s = g.state
        assertThat(s.size).isEqualTo(S4)
        assertThat(s.seed).isEqualTo(0x2048L)
        assertThat(s.board.toString()).isEqualTo("1 3 1 1 / 2 1 1 0 / 0 0 0 0 / 0 0 0 0")
        assertThat(s.score).isEqualTo(16L)
        assertThat(s.rngState).isEqualTo(0x5C55827DF1D1D1ECL)
        assertThat(s.moveCount).isEqualTo(8)
        assertThat(s.undoCount).isEqualTo(1)
        assertThat(s.status).isEqualTo(GameStatus.PLAYING)
        assertThat(s.keepGoingAtMove).isEqualTo(-1)
        assertThat(s.moveLog.toList())
            .containsExactly(LEFT, UP, RIGHT, DOWN, LEFT, LEFT, UP, UP)
            .inOrder()
        assertThat(s.snapshots).hasSize(1)
        assertThat(s.snapshots[0]).isEqualTo(Game.newGame(S4, 0x2048).snapshots[0])
        assertThat(GameInvariants.violations(s)).isEmpty()
        assertThat(g.bestAtStart).isEqualTo(0L)
        assertThat(g.gameId).isEqualTo(7L)
        assertThat(g.pendingFinalizations)
            .containsExactly(Finalization(S4, 6, GameStatus.OVER, 3012, 302, 401_000, 2, 7))
    }

    /** The ARCHITECTURE.md §9.4 literals (`LongGameGeneratorTest`), `gameId 1`, no entries. */
    private fun assertLongGameFixture(g: SavedGame) {
        val s = g.state
        assertThat(s.size).isEqualTo(S8)
        assertThat(s.seed).isEqualTo(0x2048L)
        assertThat(s.moveCount).isEqualTo(10_000)
        assertThat(s.keepGoingAtMove).isEqualTo(1678)
        assertThat(s.score).isEqualTo(208236L)
        assertThat(s.rngState).isEqualTo(0x26E3A2DE9847F93CL)
        assertThat(s.board.maxExp()).isEqualTo(12)
        assertThat(s.status).isEqualTo(GameStatus.KEEP_PLAYING)
        assertThat(s.snapshots).hasSize(313)
        assertThat(s.moveLog.toPackedBytes()).hasLength(2500)
        assertThat(s.undoCount).isEqualTo(0)
        assertThat(GameInvariants.violations(s)).isEmpty()
        assertThat(g.elapsedMs).isEqualTo(3_600_000L)
        assertThat(g.bestAtStart).isEqualTo(0L)
        assertThat(g.newBestAnnounced).isFalse()
        assertThat(g.gameId).isEqualTo(1L)
        assertThat(g.pendingFinalizations).isEmpty()
    }

    private companion object {
        val S4 = BoardSize.of(4)
        val S8 = BoardSize.of(8)
        val SETTINGS_SERIALIZER = JsonDocumentSerializer(Settings.serializer(), Settings())
        val RECORDS_SERIALIZER = JsonDocumentSerializer(Records.serializer(), Records())

        val RECORD_4 =
            SizeRecord(
                bestScore = 24680,
                bestScoreNoUndo = 18212,
                bestTileExp = 11,
                gamesPlayed = 37,
                gamesWon = 9,
                gamesAbandoned = 6,
                totalScore = 412560,
                totalMoves = 23804,
                totalTimeMs = 41_234_000,
                totalUndos = 58,
                tileReach = listOf(37, 37, 37, 37, 37, 36, 34, 30, 24, 17, 12, 9),
                currentWinStreak = 1,
                longestWinStreak = 3,
            )
        val RECORD_3 =
            SizeRecord(
                bestScore = 2100,
                bestTileExp = 8,
                gamesPlayed = 5,
                gamesWon = 2,
                tileReach = listOf(5, 5, 5, 5, 5, 5, 4, 3, 2),
            )
        val AGGREGATE =
            SizeRecord(
                bestScore = 24680,
                bestScoreNoUndo = 18212,
                bestTileExp = 11,
                gamesPlayed = 42,
                gamesWon = 11,
                gamesAbandoned = 6,
                totalScore = 412560,
                totalMoves = 23804,
                totalTimeMs = 41_234_000,
                totalUndos = 58,
                tileReach = listOf(42, 42, 42, 42, 42, 41, 38, 33, 26, 17, 12, 9),
            )

        fun JsonObject.withKeys(vararg entries: Pair<String, JsonElement>): JsonObject =
            JsonObject(this + entries)

        fun JsonObject.withoutKeys(vararg keys: String): JsonObject =
            JsonObject(this - keys.toSet())
    }
}

/**
 * The v1 fixtures (QUALITY.md §1.5): how each one is generated, and how a test reads it from the
 * test classpath (`/fixtures/v1/<name>`). Shared by the persistence tests of this package.
 */
internal object V1Fixtures {
    const val SETTINGS_FILE = "settings.json"
    const val RECORDS_FILE = "records.json"
    const val GAME_4_FILE = "game-4.json"
    const val GAME_8_LONG_FILE = "game-8-long.json"
    val NAMES = listOf(SETTINGS_FILE, RECORDS_FILE, GAME_4_FILE, GAME_8_LONG_FILE)

    const val COPY_MESSAGE =
        "copy app/build/generated-fixtures/* to app/src/test/resources/fixtures/v1/ and commit"

    /** Relative to the Gradle test working directory, `app/`. */
    private val GENERATED = File("build/generated-fixtures")

    /** Every field set to a non-default value (`schemaVersion` has only 1 in v1). */
    val SETTINGS: Settings =
        Settings(
            themeMode = ThemeMode.DARK,
            palette = PaletteId.NEON,
            highContrast = true,
            dynamicChrome = true,
            motion = MotionPreference.REDUCED,
            haptics = false,
            moveTiming = MoveTiming.ON_RELEASE,
            swipeSensitivity = SwipeSensitivity.HIGH,
            immersive = true,
            keepScreenOn = true,
            lastBoardSize = 6,
            // The versionCode before D-271; the committed fixture keeps it (write-once, D-041).
            lastSeenVersionCode = 30000,
            onboardingDone = true,
        )

    /** `FIX_RECORDS` plus its `tileReach` lists and the D-044 watermarks 6 and 5. */
    val RECORDS: Records =
        GoldenFixtures.FIX_RECORDS.let { r ->
            r.copy(
                sizes =
                    mapOf(
                        "4" to
                            r.sizes
                                .getValue("4")
                                .copy(
                                    tileReach =
                                        listOf(37, 37, 37, 37, 37, 36, 34, 30, 24, 17, 12, 9),
                                ),
                        "3" to
                            r.sizes
                                .getValue("3")
                                .copy(tileReach = listOf(5, 5, 5, 5, 5, 5, 4, 3, 2)),
                    ),
                lastFinalizedGameId = mapOf("4" to 6L, "3" to 5L),
            )
        }

    /**
     * The 8 moves of ARCHITECTURE.md §3.6 from `newGame(4×4, 0x2048)`, then undo, then UP again;
     * `gameId 7` with the one recovery entry of game 6.
     */
    val GAME_4: SavedGame by lazy {
        val size = BoardSize.of(4)
        val moves: List<Direction> = listOf(LEFT, UP, RIGHT, DOWN, LEFT, LEFT, UP, UP)
        val undone = checkNotNull(Game.undo(Game.replay(size, 0x2048, moves)))
        val again = (Game.move(undone, UP) as MoveOutcome.Moved).state
        SavedGame(
            state = again,
            elapsedMs = 61_000,
            bestAtStart = 0,
            newBestAnnounced = false,
            gameId = 7,
            pendingFinalizations =
                listOf(Finalization(size, 6, GameStatus.OVER, 3012, 302, 401_000, 2, 7)),
        )
    }

    /** The generated document of fixture [name]: exactly what its store's serializer writes. */
    fun generated(name: String): String =
        when (name) {
            SETTINGS_FILE -> AppJson.encodeToString(Settings.serializer(), SETTINGS)
            RECORDS_FILE -> AppJson.encodeToString(Records.serializer(), RECORDS)
            GAME_4_FILE -> encodeGame(GAME_4)
            GAME_8_LONG_FILE -> encodeGame(LongGameGenerator.game)
            else -> error("Unknown fixture $name")
        }

    /**
     * The committed text of fixture [name]. While any fixture is missing, writes the generated
     * documents of the missing ones and fails with [COPY_MESSAGE] (ARCHITECTURE.md §9.4).
     */
    fun text(name: String): String {
        val missing = NAMES.filter { read(it) == null }
        if (missing.isNotEmpty()) {
            GENERATED.mkdirs()
            for (n in NAMES) {
                val out = File(GENERATED, n)
                if (n in missing) out.writeText(generated(n)) else out.delete()
            }
            fail(COPY_MESSAGE)
        }
        return checkNotNull(read(name)) { "Unknown fixture $name" }
    }

    private fun read(name: String): String? =
        V1Fixtures::class.java.getResourceAsStream("/fixtures/v1/$name")?.use {
            it.readBytes().decodeToString()
        }
}

/** [GameDocumentSerializer]'s bytes of [game]. */
internal fun encodeGame(game: SavedGame): String =
    AppJson.encodeToString(GameDocument.serializer(), GameDocumentMapper.toDocument(game))

/** Decodes [text] with [GameDocumentSerializer] (JSON plus validation), then maps it. */
internal suspend fun decodeGame(text: String): SavedGame =
    GameDocumentMapper.toSavedGame(GameDocumentSerializer.decode(text))

internal suspend fun <T> Serializer<T>.decode(text: String): T =
    readFrom(ByteArrayInputStream(text.encodeToByteArray()))

internal suspend fun <T> Serializer<T>.encode(value: T): String =
    ByteArrayOutputStream().also { writeTo(value, it) }.toByteArray().decodeToString()
