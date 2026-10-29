package com.clayboicardi.game2048

import android.view.KeyEvent
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.MORE_TAG
import com.clayboicardi.game2048.game.NEW_GAME_TAG
import com.clayboicardi.game2048.game.header.SIZE_CHIP_TAG
import com.clayboicardi.game2048.game.overlay.GAME_OVER_CARD_TAG
import com.clayboicardi.game2048.game.overlay.SIZE_SHEET_TAG
import com.clayboicardi.game2048.game.overlay.WIN_CARD_TAG
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedSettings
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * Back (FEATURES.md F21 item 4; ARCHITECTURE.md §7.3; MOTION_SPEC.md AC-BACK-2; D-054, D-094):
 * `MainActivity` over files seeded through the application container's own stores. Back is
 * delivered where Android delivers it, to the top window: the activity's `onBackPressedDispatcher`
 * for the Game root and the sub-screens, the dialog window's own dispatcher for the size sheet and
 * the new-game dialog, and the Back key for the game menu's popup window. "Goes home" is
 * `isFinishing`: with no enabled callback the dispatcher's fallback finishes the activity.
 */
@RunWith(AndroidJUnit4::class)
class BackOnRootTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app: Game2048Application
        get() = ApplicationProvider.getApplicationContext()

    /** F21-AC3, AC-BACK-2 */
    @Test
    fun singleBackFromRootFinishes() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                assertWithMessage("an enabled back callback on the Game root (D-094)")
                    .that(activity.onBackPressedDispatcher.hasEnabledCallbacks())
                    .isFalse()
            }

            assertThat(backOnActivity(scenario)).isTrue()
        }
    }

    /** F21-AC4 */
    @Test
    fun backFromSubScreenPops() {
        launch().use { scenario ->
            openFromMenu(SettingsKey)
            assertShown(SettingsKey)

            assertThat(backOnActivity(scenario)).isFalse()
            assertShown(GameKey)

            openFromMenu(AboutKey)
            compose.onNodeWithTag(ABOUT_LICENSES_TAG).performClick()
            compose.waitForIdle()
            assertShown(LicensesKey)

            assertThat(backOnActivity(scenario)).isFalse()
            assertShown(AboutKey)
            assertThat(backOnActivity(scenario)).isFalse()
            assertShown(GameKey)
        }
    }

    /** F21-AC5 */
    @Test
    fun backDismissesSheetOrMenuOnly() {
        launch().use { scenario ->
            compose.onNodeWithTag(SIZE_CHIP_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(SIZE_SHEET_TAG).assertExists()

            backOnDialogWindow()

            compose.onNodeWithTag(SIZE_SHEET_TAG).assertDoesNotExist()
            assertNotFinishing(scenario)
            assertShown(GameKey)

            compose.onNodeWithTag(MORE_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(menuItemTag(StatsKey)).assertExists()

            backOnMenuWindow()

            compose.onNodeWithTag(menuItemTag(StatsKey)).assertDoesNotExist()
            assertNotFinishing(scenario)
            assertShown(GameKey)
        }
    }

    /** F21-AC6 */
    @Test
    fun backDismissesDialogOnly() {
        launch(GoldenFixtures.FIX_MID_4).use { scenario ->
            val vm = viewModel(scenario)
            val before = savedGame()
            val moveId = vm.board.value.moveId
            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assertExists()

            backOnDialogWindow()

            compose.onNode(isDialog()).assertDoesNotExist()
            assertNotFinishing(scenario)
            assertShown(GameKey)
            assertThat(vm.board.value.moveId).isEqualTo(moveId)
            assertThat(savedGame().state).isEqualTo(before.state)
            assertThat(savedGame().gameId).isEqualTo(before.gameId)
        }
    }

    /** F21-AC7 */
    @Test
    fun backWithWinCardGoesHome() {
        launch(GoldenFixtures.FIX_WON_4).use { scenario ->
            assertThat(viewModel(scenario).uiState.value.status).isEqualTo(GameStatus.WON)
            compose.onNodeWithTag(WIN_CARD_TAG).assertIsDisplayed()

            assertThat(backOnActivity(scenario)).isTrue()

            assertThat(fileGame().state.status).isEqualTo(GameStatus.WON)
        }
    }

    /** F21-AC8 */
    @Test
    fun backWithGameOverCardGoesHome() {
        launch(GoldenFixtures.FIX_OVER_4).use { scenario ->
            assertThat(viewModel(scenario).uiState.value.status).isEqualTo(GameStatus.OVER)
            compose.onNodeWithTag(GAME_OVER_CARD_TAG).assertIsDisplayed()

            assertThat(backOnActivity(scenario)).isTrue()

            assertThat(fileGame().state.status).isEqualTo(GameStatus.OVER)
        }
    }

    /** F21-AC9 */
    @Test
    fun backStackSurvivesRecreation() {
        launch().use { scenario ->
            openFromMenu(AboutKey)
            compose.onNodeWithTag(ABOUT_LICENSES_TAG).performClick()
            compose.waitForIdle()
            assertShown(LicensesKey)

            scenario.recreate()
            compose.waitForIdle()

            // The whole stack, read back by popping it: Licenses, About, Game, then home.
            assertShown(LicensesKey)
            assertThat(backOnActivity(scenario)).isFalse()
            assertShown(AboutKey)
            assertThat(backOnActivity(scenario)).isFalse()
            assertShown(GameKey)
            assertThat(backOnActivity(scenario)).isTrue()
        }
    }

    /**
     * Seeds the §10.2 settings and [game] (when given) through the container's stores, before any
     * repository reads them, then launches `MainActivity` and waits until its game is loaded.
     */
    private fun launch(game: SavedGame? = null): ActivityScenario<MainActivity> {
        runBlocking {
            app.container.seedSettings(SETTINGS)
            game?.let { app.container.seedGame(it) }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val vm = viewModel(scenario)
        // The load reads DataStore on real I/O threads and resumes on the main looper, which
        // Robolectric runs only when asked: each poll runs it.
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.ready.value
        }
        compose.waitForIdle()
        return scenario
    }

    /** The activity's `GameViewModel`: the instance `by viewModels` created. */
    private fun viewModel(scenario: ActivityScenario<MainActivity>): GameViewModel {
        lateinit var vm: GameViewModel
        val factory = app.container.gameViewModelFactory()
        scenario.onActivity { vm = ViewModelProvider(it, factory)[GameViewModel::class.java] }
        return vm
    }

    /** Opens the game menu and taps the item of [key]. */
    private fun openFromMenu(key: NavKey) {
        compose.onNodeWithTag(MORE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(menuItemTag(key)).performClick()
        compose.waitForIdle()
    }

    /** Back on the activity window; returns whether the activity is finishing. */
    private fun backOnActivity(scenario: ActivityScenario<MainActivity>): Boolean {
        var finishing = false
        scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            finishing = it.isFinishing
        }
        if (!finishing) compose.waitForIdle()
        return finishing
    }

    /** Back on the latest dialog window (the size sheet's or the new-game dialog's). */
    private fun backOnDialogWindow() {
        (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    /** The Back key on the game menu's popup window, whose root view dismisses it on key up. */
    private fun backOnMenuWindow() {
        val node = compose.onNodeWithTag(menuItemTag(StatsKey)).fetchSemanticsNode()
        val window = (checkNotNull(node.root) as ViewRootForTest).view.rootView
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            window.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
    }

    /** [key]'s destination is the one shown: its root exists and no other destination's does. */
    private fun assertShown(key: NavKey) {
        for (other in DESTINATIONS) {
            val node = compose.onNodeWithTag(screenTag(other))
            if (other == key) node.assertExists() else node.assertDoesNotExist()
        }
    }

    private fun assertNotFinishing(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { assertWithMessage("isFinishing").that(it.isFinishing).isFalse() }
    }

    /** The latest game the ViewModel saved (submitted or on disk). */
    private fun savedGame(): SavedGame = runBlocking {
        checkNotNull(app.container.gameRepository.peek(BoardSize.DEFAULT))
    }

    /** `game-4.json` as it is on disk once every submitted write has been attempted. */
    private fun fileGame(): SavedGame {
        runBlocking { app.container.gameRepository.awaitIdle() }
        val file = File(app.filesDir, "datastore/${DataStoreFiles.game(BoardSize.DEFAULT)}")
        val document = AppJson.decodeFromString(GameDocument.serializer(), file.readText())
        return GameDocumentMapper.toSavedGame(document)
    }

    private companion object {
        /** DESIGN_SYSTEM.md §10.2: onboarding done, the current version seen. */
        val SETTINGS = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE)

        val DESTINATIONS =
            listOf(GameKey, StatsKey, SettingsKey, HowToPlayKey, AboutKey, LicensesKey)

        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
