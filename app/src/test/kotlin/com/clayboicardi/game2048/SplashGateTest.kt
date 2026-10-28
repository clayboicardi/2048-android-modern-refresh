package com.clayboicardi.game2048

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.TileKind
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** The splash gate (D-112, ARCHITECTURE.md §4.12). */
@RunWith(AndroidJUnit4::class)
class SplashGateTest {
    @get:Rule val compose = createEmptyComposeRule()

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

    private companion object {
        /** Written once in M2.4 (QUALITY.md §1.5); never edited. */
        const val FIXTURE = "/fixtures/v1/game-4.json"

        /** Reads the fixture's document; the app's own decoding is what the ViewModel uses. */
        val FIXTURE_JSON = Json { ignoreUnknownKeys = true }

        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
