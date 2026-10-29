package com.clayboicardi.game2048

import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.MORE_TAG
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedSettings
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/**
 * Keep screen on while playing (FEATURES.md F20; D-006): `keepScreenOn` of the Compose root view is
 * true only while the setting is on, Game is the resumed destination and its game is in progress.
 * Each test composes `App` over a container of its own (Motion Off).
 */
@RunWith(AndroidJUnit4::class)
class KeepScreenOnTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private var view: View? = null
    private var back: OnBackPressedDispatcher? = null

    /** F20-AC1 */
    @Test
    fun onWhileGameInProgress() {
        rig(GoldenFixtures.FIX_MID_4, keepScreenOn = true).use { rig ->
            show(rig)
            assertKeepScreenOn(true, "FIX_MID_4")
        }
    }

    /** F20-AC2 */
    @Test
    fun offOnFreshBoard() {
        rig(GoldenFixtures.FIX_FRESH_4, keepScreenOn = true).use { rig ->
            show(rig)
            assertKeepScreenOn(false, "a fresh board")

            val before = rig.vm.board.value.moveId
            Direction.entries.first { d ->
                rig.vm.onMove(d)
                rig.vm.board.value.moveId != before
            }
            compose.waitForIdle()

            assertKeepScreenOn(true, "after one accepted move")
        }
    }

    /** F20-AC3 */
    @Test
    fun offWhileCardShown() {
        val won = rig(GoldenFixtures.FIX_WON_4, keepScreenOn = true)
        val over = rig(GoldenFixtures.FIX_OVER_4, keepScreenOn = true)
        try {
            var shown: KeepRig? by mutableStateOf(won)
            compose.setContent { shown?.let { rig -> key(rig) { Content(rig) } } }
            won.awaitReady(compose)
            compose.waitForIdle()
            assertKeepScreenOn(false, "the win card")

            // The first content leaves before the second composes.
            shown = null
            compose.waitForIdle()
            shown = over
            over.awaitReady(compose)
            compose.waitForIdle()
            assertKeepScreenOn(false, "the game-over card")
        } finally {
            won.close()
            over.close()
        }
    }

    /** F20-AC4 */
    @Test
    fun offOnSubScreens() {
        rig(GoldenFixtures.FIX_MID_4, keepScreenOn = true).use { rig ->
            show(rig)
            assertKeepScreenOn(true, "Game")

            compose.onNodeWithTag(MORE_TAG).performClick()
            settle()
            compose.onNodeWithTag(menuItemTag(SettingsKey)).performClick()
            settle()
            compose.onNodeWithTag(screenTag(SettingsKey)).assertExists()
            assertKeepScreenOn(false, "Settings")

            compose.runOnUiThread { checkNotNull(back).onBackPressed() }
            settle()
            compose.onNodeWithTag(screenTag(GameKey)).assertExists()
            assertKeepScreenOn(true, "Game again")
        }
    }

    /** F20-AC5 */
    @Test
    fun offWhenSettingOff() {
        rig(GoldenFixtures.FIX_MID_4, keepScreenOn = false).use { rig ->
            show(rig)
            assertKeepScreenOn(false, "the setting off")
        }
    }

    private fun assertKeepScreenOn(expected: Boolean, at: String) {
        val root = checkNotNull(view) { "no Compose root view" }
        assertWithMessage("$at: keepScreenOn").that(root.keepScreenOn).isEqualTo(expected)
    }

    /** `App` over [rig], with the Compose root view and the back dispatcher captured. */
    @Composable
    private fun Content(rig: KeepRig) {
        val root = LocalView.current
        val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        SideEffect {
            view = root
            back = dispatcher
        }
        TestApp(rig.container, rig.vm)
    }

    private fun show(rig: KeepRig) {
        compose.setContent { Content(rig) }
        rig.awaitReady(compose)
        compose.waitForIdle()
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.waitForIdle()
    }

    private fun rig(game: SavedGame, keepScreenOn: Boolean) =
        KeepRig(tmp.newFolder(), game, keepScreenOn)
}

/** One game's container and `GameViewModel` (QUALITY.md §1.6), with Keep screen on as given. */
private class KeepRig(folder: File, game: SavedGame, keepScreenOn: Boolean) : AutoCloseable {
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
                    keepScreenOn = keepScreenOn,
                    lastBoardSize = game.state.size.n,
                    onboardingDone = true,
                    lastSeenVersionCode = TEST_VERSION_CODE,
                ),
            )
            container.seedGame(game)
        }
        vm = ViewModelProvider(store, container.gameViewModelFactory())[GameViewModel::class.java]
    }

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
