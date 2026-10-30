package com.clayboicardi.game2048

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.GameRepository
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.NEW_GAME_TAG
import com.clayboicardi.game2048.game.UNDO_TAG
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.header.BEST_TAG
import com.clayboicardi.game2048.game.header.SCORE_TAG
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedSettings
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** The splash gate (D-112, ARCHITECTURE.md §4.12) and the first board it releases to. */
@RunWith(AndroidJUnit4::class)
class SplashGateTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F07-AC12 */
    @Test
    fun gateReleasesAtCapOrWhenReady() {
        var uptime = 0L
        val gate = SplashGate(timeoutMs = 1_000, uptimeMs = { uptime })

        uptime = 10
        assertThat(gate.keep(ready = true)).isFalse()
        uptime = 999
        assertThat(gate.keep(ready = false)).isTrue()
        uptime = 1_000
        assertThat(gate.keep(ready = false)).isFalse()
    }

    /** F07-AC11 */
    @Test
    fun firstBoardEqualsPersistedFixture() {
        val app = ApplicationProvider.getApplicationContext<Game2048Application>()
        val fixture = checkNotNull(javaClass.getResource(FIXTURE)) { "$FIXTURE (M2.4)" }.readText()
        File(app.filesDir, "datastore/game-4.json").apply {
            parentFile?.mkdirs()
            writeText(fixture)
        }
        val persisted =
            GameDocumentMapper.toSavedGame(
                FIXTURE_JSON.decodeFromString(GameDocument.serializer(), fixture),
            )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            // The instance `by viewModels` created (same store and key); the factory is only a
            // fallback should the splash condition not have been polled yet.
            val factory = app.container.gameViewModelFactory()
            scenario.onActivity { vm = ViewModelProvider(it, factory)[GameViewModel::class.java] }
            // The load reads DataStore on real I/O threads and resumes on the main looper, which
            // Robolectric runs only when asked: each poll runs it.
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }

            val first = vm.board.value
            assertThat(first.moveId).isEqualTo(1)
            assertThat(first.event).isEqualTo(BoardEvent.Restored)
            val board = persisted.state.board
            assertThat(first.cells).isEqualTo(List(board.cellCount) { board[it] })
            assertThat(first.tiles.map { it.kind }).doesNotContain(TileKind.Spawned)
            assertThat(first.tiles.map { it.kind }.toSet()).containsExactly(TileKind.Static)
        }
    }

    /** F07-AC36 */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun unrenderableTileSaveStartsFreshGame() {
        val app = ApplicationProvider.getApplicationContext<Game2048Application>()
        val fixture = checkNotNull(javaClass.getResource(FIXTURE)) { FIXTURE }.readText()
        val recordsText = checkNotNull(javaClass.getResource(RECORDS)) { RECORDS }.readText()
        // The v1 game with exponent 100 in its empty last cell, as a damaged or edited file holds:
        // TileLabels draws exponents below 70 only, and 4×4 play stops at 17 (D-270).
        val doc = AppJson.decodeFromString(GameDocument.serializer(), fixture)
        val cells = Base64.Default.decode(doc.cells).also { it[LAST_CELL] = DAMAGED_EXP }
        val damaged = doc.copy(cells = Base64.Default.encode(cells))
        val datastore = File(app.filesDir, "datastore").apply { mkdirs() }
        File(datastore, "game-4.json")
            .writeText(AppJson.encodeToString(GameDocument.serializer(), damaged))
        val recordsFile = File(datastore, "records.json").apply { writeText(recordsText) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            val factory = app.container.gameViewModelFactory()
            scenario.onActivity { vm = ViewModelProvider(it, factory)[GameViewModel::class.java] }
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            // Drawing the board measures every tile label: an exponent of 100 would throw here.
            compose.onNodeWithTag(BOARD).captureToImage()

            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
            // The corrupt-file fallback (D-045): a fresh 4×4 game of two start tiles.
            val board = vm.board.value
            assertThat(board.event).isEqualTo(BoardEvent.Restored)
            assertThat(board.cells.count { it != 0 }).isEqualTo(Game.START_TILES)
            assertThat(board.cells.max()).isAtMost(2)
            val fresh = runBlocking { app.container.gameRepository.peek(BoardSize.DEFAULT) }
            assertThat(checkNotNull(fresh).state.moveCount).isEqualTo(0)
            assertThat(fresh.pendingFinalizations).isEmpty()
            // The records survive untouched.
            assertThat(app.container.recordsRepository.state.value)
                .isEqualTo(AppJson.decodeFromString(Records.serializer(), recordsText))
            assertThat(recordsFile.readText()).isEqualTo(recordsText)
        }
    }

    /** F07-AC13 */
    @Test
    fun lateLoadRendersStaticTiles() {
        val fixture = GoldenFixtures.FIX_MID_4
        val gate = CompletableDeferred<Unit>()
        val (container, store) = containerWith(fixture)
        val vm = ViewModelProvider(store, gatedFactory(container, gate))[GameViewModel::class.java]
        val probe = MotionProbe()
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setContent {
                        CompositionLocalProvider(LocalMotionProbe provides probe) {
                            TestApp(container, vm)
                        }
                    }
                }
                compose.waitForIdle()
                for (step in 1..LOADING_STEPS) {
                    compose.mainClock.advanceTimeBy(LOADING_STEP_MS)
                    ShadowLooper.idleMainLooper()
                    assertLoading(vm, probe, "at ${step * LOADING_STEP_MS} ms")
                }

                gate.complete(Unit)
                compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                    ShadowLooper.idleMainLooper()
                    vm.ready.value
                }
                compose.waitForIdle()

                val board = vm.board.value
                val cells = fixture.state.board
                assertThat(board.cells).isEqualTo(List(cells.cellCount) { cells[it] })
                assertThat(board.tiles.map { it.kind }.toSet()).containsExactly(TileKind.Static)
                val drawn = probe.snapshot().tiles
                assertThat(drawn).hasSize(board.cells.count { it != 0 })
                assertThat(drawn.map { it.kind }.toSet()).containsExactly(TileKind.Static)
                assertThat(drawn.none { it.outgoing }).isTrue()
                compose
                    .onNodeWithTag(SCORE_TAG)
                    .assert(hasContentDescription("Score ${fixture.state.score}"))
                compose.onNodeWithTag(UNDO_TAG).assertIsEnabled()
                compose.onNodeWithTag(NEW_GAME_TAG).assertIsEnabled()
            }
        } finally {
            store.clear()
            container.applicationScope.cancel()
        }
    }

    /**
     * A container over a new folder holding [fixture], and a `ViewModelStore` for its ViewModel.
     */
    private fun containerWith(fixture: SavedGame): Pair<AppContainer, ViewModelStore> {
        val folder = tmp.newFolder()
        val container =
            AppContainer(
                files = { name -> File(folder, name) },
                versionCode = TEST_VERSION_CODE,
                clock = FakeClock(),
                seedSource = FixedSeedSource(SEED),
                animatorScale = FakeAnimatorScaleSource(),
            )
        runBlocking {
            container.seedSettings(
                Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE),
            )
            container.seedGame(fixture)
        }
        return container to ViewModelStore()
    }

    /**
     * A `GameViewModel` over [container] whose game store's first read waits for [gate]: the load
     * outlasts the splash's 1,000 ms cap (D-112).
     */
    private fun gatedFactory(container: AppContainer, gate: CompletableDeferred<Unit>) =
        viewModelFactory {
            initializer {
                val games =
                    GameRepository(
                        { size -> FirstReadGate(container.gameStore(size), gate) },
                        container.applicationScope,
                    )
                GameViewModel(
                    games,
                    container.recordsRepository,
                    container.settingsRepository,
                    container.clock,
                    container.seedSource,
                )
            }
        }

    /** DESIGN_SYSTEM.md §6.1 Loading: empty cells, "–" chips, Undo and New game disabled. */
    private fun assertLoading(vm: GameViewModel, probe: MotionProbe, at: String) {
        assertWithMessage("ready $at").that(vm.ready.value).isFalse()
        assertWithMessage("board tiles $at").that(vm.board.value.tiles).isEmpty()
        assertWithMessage("drawn tiles $at").that(probe.snapshot().tiles).isEmpty()
        compose.onNodeWithTag(SCORE_TAG).assert(hasText(LOADING, substring = true))
        compose.onNodeWithTag(BEST_TAG).assert(hasText(LOADING, substring = true))
        compose.onNodeWithTag(UNDO_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(NEW_GAME_TAG).assertIsNotEnabled()
    }

    /** Delegates to [delegate], except that the first read of [data] waits for [gate]. */
    private class FirstReadGate<T>(
        private val delegate: DataStore<T>,
        gate: CompletableDeferred<Unit>,
    ) : DataStore<T> {
        override val data: Flow<T> = flow {
            gate.await()
            emitAll(delegate.data)
        }

        override suspend fun updateData(transform: suspend (t: T) -> T): T =
            delegate.updateData(transform)
    }

    private companion object {
        /** Written once in M2.4 (QUALITY.md §1.5); never edited. */
        const val FIXTURE = "/fixtures/v1/game-4.json"

        /** The v1 records fixture, written with it; never edited. */
        const val RECORDS = "/fixtures/v1/records.json"

        /** The board node (`BoardView`). */
        const val BOARD = "game:board"

        /** The bottom-right cell of the v1 4×4 fixture, which is empty. */
        const val LAST_CELL = 15

        /** Far above 4×4's reachable 17 and at or above TileLabels' limit of 70. */
        const val DAMAGED_EXP: Byte = 100

        /** Reads the fixture's document; the app's own decoding is what the ViewModel uses. */
        val FIXTURE_JSON = Json { ignoreUnknownKeys = true }

        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L

        const val SEED = 0x2048L

        /** Five checks 300 ms apart: the loading frame holds until 1,500 ms. */
        const val LOADING_STEPS = 5
        const val LOADING_STEP_MS = 300L

        /** What the chips show while loading (DESIGN_SYSTEM.md §6.1). */
        const val LOADING = "\u2013"
    }
}
