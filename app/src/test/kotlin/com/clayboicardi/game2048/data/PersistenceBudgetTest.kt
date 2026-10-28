package com.clayboicardi.game2048.data

import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.GameDocumentSerializer
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsMath
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.testing.LongGameGenerator
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.kill
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The D-046 budgets (ARCHITECTURE.md §9.4, QUALITY.md §1.6), measured in memory through the
 * serializers and on disk after a real `updateData`. The timing budget is the one place a test
 * reads a clock (`System.nanoTime`), as a median after warm-ups. Measured values go to standard
 * output.
 */
class PersistenceBudgetTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    /** F07-AC18 */
    @Test
    fun longGameWithinSizeAndTimeBudget() = runTest {
        val game = LongGameGenerator.game
        val doc = GameDocumentMapper.toDocument(game)

        val inMemory =
            ByteArrayOutputStream().also { GameDocumentSerializer.writeTo(doc, it) }.size()
        assertThat(inMemory).isAtMost(LONG_GAME_BUDGET_BYTES)

        val folder = tmp.newFolder()
        val c = testAppContainer(folder)
        c.gameStore(S8).updateData { doc }
        val onDisk = File(folder, DataStoreFiles.game(S8)).length()
        assertThat(onDisk).isAtMost(LONG_GAME_BUDGET_BYTES.toLong())
        kill(c)

        // Encode (mapping + JSON) and decode (JSON + validation + mapping), the full save/load
        // path.
        val runs =
            List(WARM_UPS + RUNS) {
                val start = System.nanoTime()
                val out = ByteArrayOutputStream()
                GameDocumentSerializer.writeTo(GameDocumentMapper.toDocument(game), out)
                val read = GameDocumentSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
                val decoded = GameDocumentMapper.toSavedGame(read)
                val elapsed = System.nanoTime() - start
                check(decoded == game) { "The long game did not round-trip" }
                elapsed
            }
        val medianMs = runs.drop(WARM_UPS).sorted()[RUNS / 2] / NANOS_PER_MS
        println(
            "PersistenceBudgetTest: long game $inMemory B in memory, $onDisk B on disk, " +
                "encode+decode median ${ms(medianMs)} ms (runs: " +
                runs.drop(WARM_UPS).joinToString { ms(it / NANOS_PER_MS) } +
                " ms)",
        )
        assertThat(medianMs).isAtMost(TIME_BUDGET_MS)
    }

    /** F07-AC19 */
    @Test
    fun recordsAndSettingsWithinBudget() = runTest {
        val big = BILLION
        val full =
            SizeRecord(
                bestScore = big.toLong(),
                bestScoreNoUndo = big.toLong(),
                bestTileExp = big,
                gamesPlayed = big,
                gamesWon = big,
                gamesAbandoned = big,
                totalScore = big.toLong(),
                totalMoves = big.toLong(),
                totalTimeMs = big.toLong(),
                totalUndos = big.toLong(),
                tileReach = List(TILE_REACH_ENTRIES) { big },
                currentWinStreak = big,
                longestWinStreak = big,
            )
        val records =
            Records(
                sizes = BoardSize.ALL.associate { RecordsMath.key(it) to full },
                lastFinalizedGameId =
                    BoardSize.ALL.associate { RecordsMath.key(it) to big.toLong() },
            )
        val recordsSerializer = JsonDocumentSerializer(Records.serializer(), Records())
        val settingsSerializer = JsonDocumentSerializer(Settings.serializer(), Settings())

        val recordsBytes = recordsSerializer.encode(records).encodeToByteArray().size
        val settingsBytes = settingsSerializer.encode(Settings()).encodeToByteArray().size
        val busySettingsBytes =
            settingsSerializer.encode(V1Fixtures.SETTINGS).encodeToByteArray().size
        assertThat(recordsBytes).isAtMost(RECORDS_BUDGET_BYTES)
        assertThat(settingsBytes).isAtMost(SETTINGS_BUDGET_BYTES)
        assertThat(busySettingsBytes).isAtMost(SETTINGS_BUDGET_BYTES)

        // The same documents on disk, written by the real stores.
        val folder = tmp.newFolder()
        val c = testAppContainer(folder)
        c.recordsStore.updateData { records }
        c.settingsStore.updateData { Settings(haptics = false) }
        c.settingsStore.updateData { Settings() }
        val recordsOnDisk = File(folder, DataStoreFiles.RECORDS).length()
        val settingsOnDisk = File(folder, DataStoreFiles.SETTINGS).length()
        assertThat(recordsOnDisk).isAtMost(RECORDS_BUDGET_BYTES.toLong())
        assertThat(settingsOnDisk).isAtMost(SETTINGS_BUDGET_BYTES.toLong())
        kill(c)

        println(
            "PersistenceBudgetTest: records $recordsBytes B in memory, $recordsOnDisk B on disk; " +
                "settings $settingsBytes B (defaults) / $busySettingsBytes B (every field set), " +
                "$settingsOnDisk B on disk",
        )
    }

    private companion object {
        val S8 = BoardSize.of(8)
        const val LONG_GAME_BUDGET_BYTES = 262_144
        const val RECORDS_BUDGET_BYTES = 65_536
        const val SETTINGS_BUDGET_BYTES = 4_096
        const val TIME_BUDGET_MS = 50.0
        const val WARM_UPS = 3
        const val RUNS = 5
        const val NANOS_PER_MS = 1_000_000.0
        const val BILLION = 1_000_000_000
        const val TILE_REACH_ENTRIES = 40

        fun ms(value: Double): String = "%.3f".format(Locale.ROOT, value)
    }
}
