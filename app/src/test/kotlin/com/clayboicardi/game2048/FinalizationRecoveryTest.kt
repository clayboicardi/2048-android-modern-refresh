package com.clayboicardi.game2048

import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.GameRepository
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsRepository
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.testing.FaultyDataStore
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.kill
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * D-044's finalization protocol across process deaths (QUALITY.md §1.11, ARCHITECTURE.md §4.9).
 * Container A runs New game with one of its two files failing (`FaultyDataStore`), or with records
 * writes that keep failing, and is killed after every write was attempted; containers B and C
 * relaunch over the same folder with `FixedSeedSource(0x2048)` again, so seeds repeat and only
 * `gameId` tells games apart. Every counted game must be counted exactly once.
 *
 * The scheduler is run with `testScheduler.runCurrent()`: the containers' stores and writers live
 * in children of `backgroundScope`, which `advanceUntilIdle()` does not wait for.
 */
class FinalizationRecoveryTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F06-AC14 */
    @Test
    fun overGameCountedOnceWhenGameWriteIsLost() = runTest {
        countsOnceWhenGameWriteIsLost(OVER_7) { r ->
            assertThat(r.gamesPlayed).isEqualTo(1)
            assertThat(r.totalScore).isEqualTo(3012)
            assertThat(r.totalMoves).isEqualTo(302)
            assertThat(r.totalTimeMs).isEqualTo(401_000)
        }
    }

    /** F06-AC15 */
    @Test
    fun abandonedGameCountedOnceWhenGameWriteIsLost() = runTest {
        countsOnceWhenGameWriteIsLost(MID_7) { r ->
            assertThat(r.gamesPlayed).isEqualTo(1)
            assertThat(r.gamesAbandoned).isEqualTo(1)
            assertThat(r.totalScore).isEqualTo(10_220)
            assertThat(r.totalMoves).isEqualTo(412)
            assertThat(r.totalUndos).isEqualTo(2)
        }
    }

    /** F06-AC16 */
    @Test
    fun overGameCountedOnceWhenRecordsWriteIsLost() = runTest {
        countsOnceWhenRecordsWriteIsLost(OVER_7) { r ->
            assertThat(r.gamesPlayed).isEqualTo(1)
            assertThat(r.totalScore).isEqualTo(3012)
        }
    }

    /** F06-AC17 */
    @Test
    fun abandonedGameCountedOnceWhenRecordsWriteIsLost() = runTest {
        countsOnceWhenRecordsWriteIsLost(MID_7) { r ->
            assertThat(r.gamesPlayed).isEqualTo(1)
            assertThat(r.gamesAbandoned).isEqualTo(1)
            assertThat(r.totalScore).isEqualTo(10_220)
        }
    }

    /** F06-AC18 */
    @Test
    fun everyDiskOutcomeCountsExactlyOnce() = runTest {
        val cases =
            listOf(OVER_7, MID_7).flatMap { old ->
                val (before, after) = newGameFiles(old)
                listOf(before to before, after to before, before to after, after to after).map {
                    (records, game) ->
                    DiskCase(old, records, game, after)
                }
            }
        assertThat(cases).hasSize(8)
        for (case in cases) countsOnceFrom(case)
    }

    /** F06-AC19 */
    @Test
    fun otherSizesRecoveredAfterLoad() = runTest {
        val folder = tmp.newFolder()
        val a = appA(folder, failRecords = true) { seedSettings(Settings(lastBoardSize = 5)) }
        assertThat(a.games.peek(FIVE)?.gameId).isEqualTo(1)
        playToScore(a.vm)
        a.vm.onNewGame()
        a.vm.onSizeSelected(FOUR)
        testScheduler.runCurrent()
        a.awaitIdle()
        kill(a.container)
        val game5 = File(folder, DataStoreFiles.game(FIVE)).readBytes()

        val b = relaunch(folder)

        assertThat(b.vm.uiState.value.size).isEqualTo(FOUR)
        val r5 = recordOf(b.records.state.value, FIVE)
        assertThat(r5.gamesPlayed).isEqualTo(1)
        assertThat(r5.gamesAbandoned).isEqualTo(1)
        assertThat(b.records.state.value.lastFinalizedGameId["5"]).isEqualTo(1)
        b.awaitIdle()
        assertThat(File(folder, DataStoreFiles.game(FIVE)).readBytes()).isEqualTo(game5)
    }

    /** F06-AC20 */
    @Test
    fun uncountedNewGameKeepsUnwrittenEntry() = runTest {
        val folder = tmp.newFolder()
        val a = appA(folder, failRecords = true) { seedGame(OVER_7) }
        a.vm.onNewGame()
        a.vm.onNewGame()
        a.awaitIdle()
        val game9 = checkNotNull(a.games.peek(FOUR))
        assertThat(game9.gameId).isEqualTo(9)
        assertThat(game9.pendingIds()).containsExactly(7L)
        kill(a.container)

        val b = relaunch(folder) { assertThat(recordsStore.data.first()).isEqualTo(Records()) }
        b.awaitIdle()
        val r = recordOf(b.records.state.value)
        assertThat(r.gamesPlayed).isEqualTo(1)
        assertThat(r.totalScore).isEqualTo(3012)
        assertThat(r.totalMoves).isEqualTo(302)
        assertThat(b.records.state.value.lastFinalizedGameId["4"]).isEqualTo(7)
        assertThat(b.games.peek(FOUR)).isEqualTo(game9)
        b.vm.onNewGame()
        b.awaitIdle()
        kill(b.container)

        val c = relaunch(folder)
        assertThat(recordOf(c.records.state.value).gamesPlayed).isEqualTo(1)
        val game10 = checkNotNull(c.games.peek(FOUR))
        assertThat(game10.gameId).isEqualTo(10)
        assertThat(game10.pendingFinalizations).isEmpty()
    }

    /** F06-AC21 */
    @Test
    fun repeatedRecordsFailuresKeepEveryEntry() = runTest {
        val folder = tmp.newFolder()
        val a = appA(folder, failRecords = true) { seedGame(OVER_7) }
        a.vm.onNewGame()
        val s8 = playToScore(a.vm)
        a.vm.onNewGame()
        val s9 = playToScore(a.vm)
        a.vm.onNewGame()
        a.vm.onNewGame()
        a.awaitIdle()
        val game11 = checkNotNull(a.games.peek(FOUR))
        assertThat(game11.gameId).isEqualTo(11)
        assertThat(game11.pendingIds()).containsExactly(7L, 8L, 9L).inOrder()
        kill(a.container)

        val b = relaunch(folder) { assertThat(recordsStore.data.first()).isEqualTo(Records()) }
        val r = recordOf(b.records.state.value)
        assertThat(r.gamesPlayed).isEqualTo(3)
        assertThat(r.gamesAbandoned).isEqualTo(2)
        assertThat(r.totalScore).isEqualTo(3012 + s8 + s9)
        assertThat(b.records.state.value.lastFinalizedGameId["4"]).isEqualTo(9)
        b.vm.onNewGame()
        b.awaitIdle()
        kill(b.container)

        val c = relaunch(folder)
        assertThat(recordOf(c.records.state.value).gamesPlayed).isEqualTo(3)
    }

    /** F06-AC22 */
    @Test
    fun overflowKeepsNewestEntriesAndPauseRetriesRecords() = runTest {
        val folder = tmp.newFolder()
        val a = appA(folder, failRecords = true) { seedGame(OVER_7) }
        a.vm.onNewGame()
        repeat(16) {
            playToScore(a.vm)
            a.vm.onNewGame()
        }
        a.awaitIdle()
        val game24 = checkNotNull(a.games.peek(FOUR))
        assertThat(game24.gameId).isEqualTo(24)
        assertThat(game24.pendingIds()).isEqualTo((8L..23L).toList())
        assertThat(recordOf(a.records.state.value).gamesPlayed).isEqualTo(17)
        assertThat(a.records.state.value.lastFinalizedGameId["4"]).isEqualTo(23)
        assertThat(a.container.recordsStore.data.first()).isEqualTo(Records())

        a.recordsStore.failWrites = false
        a.vm.onPaused()
        a.awaitIdle()
        val records = a.records.state.value
        assertThat(a.container.recordsStore.data.first()).isEqualTo(records)
        kill(a.container)

        val b = relaunch(folder)
        val r = recordOf(b.records.state.value)
        assertThat(r.gamesPlayed).isEqualTo(17)
        assertThat(b.records.state.value.lastFinalizedGameId["4"]).isEqualTo(23)
        assertThat(r.totalScore).isEqualTo(recordOf(records).totalScore)
        assertThat(checkNotNull(b.games.peek(FOUR)).gameId).isEqualTo(24)
    }

    /** F08-AC22 */
    @Test
    fun resetIsNotUndoneByRecovery() = runTest {
        val folder = tmp.newFolder()
        val a = appA(folder) { seedGame(OVER_7) }
        a.vm.onNewGame()
        a.records.resetAll()
        a.awaitIdle()
        assertThat(checkNotNull(a.fileGame(FOUR)).pendingIds()).containsExactly(7L)
        kill(a.container)

        val b = relaunch(folder)
        b.awaitIdle()

        assertThat(b.records.state.value.sizes).isEmpty()
        assertThat(b.records.state.value.lastFinalizedGameId["4"]).isEqualTo(7)
        assertThat(b.container.recordsStore.data.first().sizes).isEmpty()
    }

    /** F06-AC14, AC15: New game's records write landed, its game write failed, then a kill. */
    private suspend fun TestScope.countsOnceWhenGameWriteIsLost(
        old: SavedGame,
        expect: (SizeRecord) -> Unit,
    ) {
        val folder = tmp.newFolder()
        val a = appA(folder, failGame4 = true) { seedGame(old) }
        a.vm.onNewGame()
        a.awaitIdle()
        assertThat(a.gameStore(FOUR).writeAttempts).isAtLeast(1)
        kill(a.container)

        val b =
            relaunch(folder) {
                assertThat(recordsStore.data.first().lastFinalizedGameId).containsExactly("4", 7L)
                assertThat(fileGame(FOUR)?.gameId).isEqualTo(7)
            }
        expect(recordOf(b.records.state.value))
        assertThat(b.records.state.value.lastFinalizedGameId).containsExactly("4", 7L)
        val fresh = checkNotNull(b.games.peek(FOUR))
        assertThat(fresh.gameId).isEqualTo(8)
        assertThat(fresh.pendingFinalizations).isEmpty()
        assertThat(fresh.state.moveCount).isEqualTo(0)
        b.awaitIdle()
        assertThat(b.container.fileGame(FOUR)).isEqualTo(fresh)
        b.vm.onNewGame()
        b.awaitIdle()
        kill(b.container)

        val c = relaunch(folder)
        assertThat(recordOf(c.records.state.value).gamesPlayed).isEqualTo(1)
    }

    /** F06-AC16, AC17: New game's game write landed, its records write failed, then a kill. */
    private suspend fun TestScope.countsOnceWhenRecordsWriteIsLost(
        old: SavedGame,
        expect: (SizeRecord) -> Unit,
    ) {
        val folder = tmp.newFolder()
        val a = appA(folder, failRecords = true) { seedGame(old) }
        a.vm.onNewGame()
        val fresh = checkNotNull(a.games.peek(FOUR))
        a.awaitIdle()
        kill(a.container)

        val b =
            relaunch(folder) {
                assertThat(recordsStore.data.first()).isEqualTo(Records())
                assertThat(fileGame(FOUR)).isEqualTo(fresh)
            }
        expect(recordOf(b.records.state.value))
        assertThat(b.records.state.value.lastFinalizedGameId).containsExactly("4", 7L)
        assertThat(fresh.gameId).isEqualTo(8)
        assertThat(b.games.peek(FOUR)).isEqualTo(fresh)
        b.vm.onNewGame()
        b.awaitIdle()
        kill(b.container)

        val c = relaunch(folder)
        assertThat(recordOf(c.records.state.value).gamesPlayed).isEqualTo(1)
    }

    /** F06-AC18 step 3: the files before and after a fault-free New game over [old]. */
    private suspend fun TestScope.newGameFiles(old: SavedGame): Pair<Files, Files> {
        val folder = tmp.newFolder()
        val a =
            appA(folder) {
                seedRecords(Records())
                seedGame(old)
            }
        a.awaitIdle()
        val before = filesOf(folder)
        a.vm.onNewGame()
        a.awaitIdle()
        val after = filesOf(folder)
        kill(a.container)
        return before to after
    }

    /** One F06-AC18 combination: B loads, starts a New game; C counts the old game once. */
    private suspend fun TestScope.countsOnceFrom(case: DiskCase) {
        val folder = tmp.newFolder()
        case.files().forEach { (name, bytes) -> File(folder, name).writeBytes(bytes) }
        val b = relaunch(folder)
        b.vm.onNewGame()
        b.awaitIdle()
        kill(b.container)

        val c = relaunch(folder)
        val r = recordOf(c.records.state.value)
        val old = case.old
        assertWithMessage("${case.label}: gamesPlayed, score, moves, time, undos")
            .that(
                listOf(
                    r.gamesPlayed.toLong(),
                    r.totalScore,
                    r.totalMoves,
                    r.totalTimeMs,
                    r.totalUndos,
                ),
            )
            .isEqualTo(
                listOf(
                    1L,
                    old.state.score,
                    old.state.moveCount.toLong(),
                    old.elapsedMs,
                    old.state.undoCount.toLong(),
                ),
            )
        assertWithMessage("${case.label}: lastFinalizedGameId")
            .that(c.records.state.value.lastFinalizedGameId["4"])
            .isAtLeast(7L)
    }
}

private val FOUR: BoardSize = BoardSize.of(4)
private val FIVE: BoardSize = BoardSize.of(5)

private val OVER_7 = GoldenFixtures.FIX_OVER_4.copy(gameId = 7)
private val MID_7 = GoldenFixtures.FIX_MID_4.copy(gameId = 7)

private val DLRU = listOf(Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP)

/** File name → bytes of one folder. */
private typealias Files = Map<String, ByteArray>

private fun filesOf(folder: File): Files =
    checkNotNull(folder.listFiles()).filter { it.isFile }.associate { it.name to it.readBytes() }

/** F06-AC18: `records.json` from [records], `game-4.json` from [game], the rest from [base]. */
private class DiskCase(val old: SavedGame, val records: Files, val game: Files, val base: Files) {
    val label: String
        get() =
            "${old.state.status}: records ${if (records === base) "after" else "before"}, " +
                "game ${if (game === base) "after" else "before"}"

    fun files(): Files =
        base.filterKeys { it != RECORDS && it != GAME_4 } +
            records.filterKeys { it == RECORDS } +
            game.filterKeys { it == GAME_4 }
}

private val RECORDS = DataStoreFiles.RECORDS
private val GAME_4 = DataStoreFiles.game(FOUR)

private fun recordOf(records: Records, size: BoardSize = FOUR): SizeRecord =
    records.sizes["${size.n}"] ?: SizeRecord()

private fun SavedGame.pendingIds(): List<Long> = pendingFinalizations.map { it.gameId }

private suspend fun AppContainer.fileGame(size: BoardSize): SavedGame? {
    val doc = gameStore(size).data.first()
    return if (doc.size == 0) null else GameDocumentMapper.toSavedGame(doc)
}

/**
 * "Played to a score" (QUALITY.md §1.11 step 6): `onMove` with the first of DOWN, LEFT, RIGHT, UP
 * that changes the board, until `score > 0`, the game still `PLAYING`. Returns that score.
 */
private fun playToScore(vm: GameViewModel): Long {
    repeat(MAX_MOVES_TO_SCORE) {
        if (vm.header.value.score > 0) {
            check(vm.uiState.value.status == GameStatus.PLAYING)
            return vm.header.value.score
        }
        val before = vm.board.value.moveId
        DLRU.first { d ->
            vm.onMove(d)
            vm.board.value.moveId != before
        }
    }
    error("No score after $MAX_MOVES_TO_SCORE moves")
}

private const val MAX_MOVES_TO_SCORE = 100

/**
 * Container A (QUALITY.md §1.11 step 1): repositories over A's own stores, every one wrapped in a
 * `FaultyDataStore` (failing only where the test says), never A's own repositories, and A's
 * `GameViewModel` built directly from them.
 */
private class AppA(scope: TestScope, val container: AppContainer) {
    val recordsStore = FaultyDataStore(container.recordsStore)
    private val gameStores = HashMap<Int, FaultyDataStore<GameDocument>>()
    val records =
        RecordsRepository(
            recordsStore,
            container.applicationScope,
            StandardTestDispatcher(scope.testScheduler),
        )
    val games = GameRepository(::gameStore, container.applicationScope)
    val vm =
        GameViewModel(
            games,
            records,
            container.settingsRepository,
            container.clock,
            container.seedSource,
        )

    fun gameStore(size: BoardSize): FaultyDataStore<GameDocument> =
        synchronized(gameStores) {
            gameStores.getOrPut(size.n) { FaultyDataStore(container.gameStore(size)) }
        }

    suspend fun awaitIdle() {
        container.settingsRepository.awaitIdle()
        records.awaitIdle()
        games.awaitIdle()
    }

    suspend fun fileGame(size: BoardSize): SavedGame? = container.fileGame(size)
}

/** Seeds [folder], then starts container A with the given stores failing, loaded. */
private suspend fun TestScope.appA(
    folder: File,
    failRecords: Boolean = false,
    failGame4: Boolean = false,
    seed: suspend AppContainer.() -> Unit = {},
): AppA {
    val container = testAppContainer(folder)
    container.seed()
    val a = AppA(this, container)
    a.recordsStore.failWrites = failRecords
    a.gameStore(FOUR).failWrites = failGame4
    testScheduler.runCurrent()
    check(a.vm.ready.value) { "Container A's ViewModel did not become ready" }
    return a
}

/** A relaunch (containers B and C): no faults, the container's own repositories. */
private class App(val container: AppContainer, val vm: GameViewModel) {
    val records: RecordsRepository
        get() = container.recordsRepository

    val games: GameRepository
        get() = container.gameRepository

    suspend fun awaitIdle() {
        container.settingsRepository.awaitIdle()
        records.awaitIdle()
        games.awaitIdle()
    }
}

/**
 * A new process over [folder] with `FixedSeedSource(0x2048)` again; [beforeLoad] reads its stores
 * before anything else does. Returns once its `GameViewModel` is ready and the scheduler is idle.
 */
private suspend fun TestScope.relaunch(
    folder: File,
    beforeLoad: suspend AppContainer.() -> Unit = {},
): App {
    val container = testAppContainer(folder)
    container.beforeLoad()
    val vm =
        GameViewModel(
            container.gameRepository,
            container.recordsRepository,
            container.settingsRepository,
            container.clock,
            container.seedSource,
        )
    testScheduler.runCurrent()
    check(vm.ready.value) { "The relaunched ViewModel did not become ready" }
    return App(container, vm)
}
