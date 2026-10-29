package com.clayboicardi.game2048.screenshots

import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.App
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.NEW_GAME_TAG
import com.clayboicardi.game2048.game.header.SIZE_CHIP_TAG
import com.clayboicardi.game2048.game.overlay.GAME_OVER_CARD_TAG
import com.clayboicardi.game2048.game.overlay.SIZE_SHEET_TAG
import com.clayboicardi.game2048.game.overlay.WIN_CARD_TAG
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.assertAccessible
import com.clayboicardi.game2048.testing.assertDialogTouchTargets
import com.clayboicardi.game2048.testing.assertTouchTargets
import com.clayboicardi.game2048.testing.atfRule
import com.clayboicardi.game2048.testing.captureGolden
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.DIALOG_DISMISS_TAG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * The Game screen goldens of M3 part 1 (DESIGN_SYSTEM.md §10.2, §10.4 rows 1, 3 and 43; D-147,
 * D-204) at SDK 36 on the Pixel 7 qualifier, under ATF and the touch-target gate (D-085, D-205):
 * the main window after each test, the dialog's own window while it is open. Settings: Motion Off,
 * onboarding done, the current version seen (§10.2). Each game is loaded by a container of its own;
 * M4 adds the dark rows to [freshAndMidGoldens].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GameScreenshotTest {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(atfRule(compose))
    @get:Rule val tmp = TemporaryFolder()

    /** F12-AC11 */
    @Test
    fun freshAndMidGoldens() {
        val fresh = GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_FRESH_4)
        val mid = GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4)
        try {
            var shown by mutableStateOf(fresh)
            compose.setContent { key(shown) { shown.Content() } }
            fresh.awaitReady(compose)
            compose.waitForIdle()
            compose.onRoot().captureGolden("game_new-4x4_classic-light")
            compose.assertTouchTargets()

            shown = mid
            mid.awaitReady(compose)
            compose.waitForIdle()
            compose.onRoot().captureGolden("game_mid-4x4_classic-light")
            compose.assertTouchTargets()
        } finally {
            fresh.close()
            mid.close()
        }
    }

    /** F06-AC12 */
    @Test
    fun newGameDialogGolden() {
        GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.setContent { rig.Content() }
            rig.awaitReady(compose)
            compose.waitForIdle()

            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            val dialog = compose.onNode(isDialog())
            dialog.captureGolden("dialog_newgame_classic-light")
            // ATF and the touch targets of the dialog's own window, while it is open (D-085,
            // D-205).
            dialog.assertAccessible()
            compose.assertDialogTouchTargets()

            // The main window's touch targets, and the after-test ATF check of atfRule, which reads
            // the main window's root only: with the dialog closed.
            compose.onNodeWithTag(DIALOG_DISMISS_TAG).performClick()
            compose.waitForIdle()
            compose.assertTouchTargets()
        }
    }

    /** F03-AC17 */
    @Test
    fun winCardGoldens() {
        // Row 13; M4 adds the dark row 14 (D-204).
        GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_WON_4).use { rig ->
            compose.setContent { rig.Content() }
            rig.awaitReady(compose)
            compose.waitForIdle()

            compose.onNodeWithTag(WIN_CARD_TAG).assertExists()
            compose.onRoot().captureGolden("game_won-4x4_classic-light")
            compose.assertTouchTargets()
        }
    }

    /** F04-AC13 */
    @Test
    fun gameOverCardGoldens() {
        // Row 15; M4 adds the dark row 16 (D-204).
        GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_OVER_4).use { rig ->
            compose.setContent { rig.Content() }
            rig.awaitReady(compose)
            compose.waitForIdle()

            compose.onNodeWithTag(GAME_OVER_CARD_TAG).assertExists()
            compose.onRoot().captureGolden("game_over-4x4_classic-light")
            compose.assertTouchTargets()
        }
    }

    /** F02-AC14 */
    @Test
    fun boardSizeSheetGolden() {
        // Row 42's three fixtures, so the container is made here: FIX_MID_4 current, FIX_MID_3
        // saved in progress, FIX_RECORDS (DESIGN_SYSTEM.md §10.4), with the §10.2 settings.
        val folder = tmp.newFolder()
        val container =
            AppContainer(
                files = { name -> File(folder, name) },
                versionCode = TEST_VERSION_CODE,
                clock = FakeClock(),
                seedSource = FixedSeedSource(0x2048L),
                animatorScale = FakeAnimatorScaleSource(),
            )
        val store = ViewModelStore()
        try {
            runBlocking {
                container.seedSettings(
                    Settings(
                        motion = MotionPreference.OFF,
                        onboardingDone = true,
                        lastSeenVersionCode = TEST_VERSION_CODE,
                    ),
                )
                container.seedGame(GoldenFixtures.FIX_MID_4)
                container.seedGame(GoldenFixtures.FIX_MID_3)
                container.seedRecords(GoldenFixtures.FIX_RECORDS)
            }
            val vm =
                ViewModelProvider(store, container.gameViewModelFactory())[
                    GameViewModel::class.java]
            compose.setContent {
                App(
                    vm,
                    container.settingsRepository.state,
                    container.animatorScale,
                    container.clock,
                )
            }
            compose.waitUntil(timeoutMillis = 10_000L) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            compose.waitForIdle()

            compose.onNodeWithTag(SIZE_CHIP_TAG).performClick()
            compose.waitForIdle()
            val sheet = compose.onNode(isDialog())
            sheet.captureGolden("sheet_boardsize_classic-light")
            // ATF and the touch targets of the sheet's own window, while it is open (D-085, D-205).
            sheet.assertAccessible()
            compose.assertDialogTouchTargets()

            // The main window's touch targets and atfRule's after-test check, with the sheet
            // closed.
            (ShadowDialog.getLatestDialog() as ComponentDialog)
                .onBackPressedDispatcher
                .onBackPressed()
            compose.waitForIdle()
            compose.onNodeWithTag(SIZE_SHEET_TAG).assertDoesNotExist()
            compose.assertTouchTargets()
        } finally {
            store.clear()
            container.applicationScope.cancel()
        }
    }
}

/** One fixture's container and `GameViewModel` (QUALITY.md §1.6), with the §10.2 settings. */
private class GoldenRig(folder: File, game: SavedGame) : AutoCloseable {
    val container =
        AppContainer(
            files = { name -> File(folder, name) },
            versionCode = TEST_VERSION_CODE,
            clock = FakeClock(),
            seedSource = FixedSeedSource(SEED),
            animatorScale = FakeAnimatorScaleSource(),
        )
    private val store = ViewModelStore()
    val vm: GameViewModel

    init {
        runBlocking {
            container.seedSettings(
                Settings(
                    motion = MotionPreference.OFF,
                    lastBoardSize = game.state.size.n,
                    onboardingDone = true,
                    lastSeenVersionCode = TEST_VERSION_CODE,
                ),
            )
            container.seedGame(game)
            // A fixture's "best" is its bestAtStart (DESIGN_SYSTEM.md §10.3, D-216), the size's
            // record when the game started; the header shows the record.
            if (game.bestAtStart > 0) {
                val size = game.state.size.n.toString()
                container.seedRecords(
                    Records(sizes = mapOf(size to SizeRecord(bestScore = game.bestAtStart))),
                )
            }
        }
        vm = ViewModelProvider(store, container.gameViewModelFactory())[GameViewModel::class.java]
    }

    @Composable
    fun Content() {
        App(
            gameViewModel = vm,
            settings = container.settingsRepository.state,
            animatorScale = container.animatorScale,
            clock = container.clock,
        )
    }

    /** Waits for the load (the stores read on real I/O threads and resume on the main looper). */
    fun awaitReady(compose: ComposeContentTestRule) {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.ready.value
        }
    }

    override fun close() {
        store.clear()
        container.applicationScope.cancel()
    }

    private companion object {
        const val SEED = 0x2048L
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
