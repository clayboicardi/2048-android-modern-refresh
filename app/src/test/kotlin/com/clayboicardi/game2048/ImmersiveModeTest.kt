package com.clayboicardi.game2048

import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.MORE_TAG
import com.clayboicardi.game2048.testing.FakeSystemBarsController
import com.clayboicardi.game2048.testing.FakeSystemBarsController.Call
import com.clayboicardi.game2048.testing.FakeSystemBarsController.Kind
import com.clayboicardi.game2048.testing.SeamsTestApplication
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Edge-to-edge and Immersive mode (FEATURES.md F19; D-093; ARCHITECTURE.md §6.2): `MainActivity`
 * over the production container of `SeamsTestApplication`, whose `FakeSystemBarsController` records
 * every system-bar call of `ImmersiveMode.apply`. Settings and the game are written where the
 * container reads them before the activity starts.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = SeamsTestApplication::class)
class ImmersiveModeTest {
    @get:Rule val compose = createComposeRule()

    private val app: SeamsTestApplication
        get() = ApplicationProvider.getApplicationContext()

    private val bars: FakeSystemBarsController
        get() = app.systemBars

    /** F19-AC1 */
    @Test
    fun edgeToEdgeAlwaysApplied() {
        launch(immersive = false) { scenario ->
            scenario.onActivity { activity ->
                assertWithMessage("layoutInDisplayCutoutMode")
                    .that(activity.window.attributes.layoutInDisplayCutoutMode)
                    .isEqualTo(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)
            }
        }
    }

    /** F19-AC2 */
    @Test
    fun onHidesSystemBars() {
        launch(immersive = true) {
            // Before the settings load, the default (Off) shows the bars, which are visible anyway.
            assertWithMessage("system-bar calls")
                .that(bars.calls)
                .containsAtLeast(Call(Kind.BEHAVIOR, TRANSIENT_BY_SWIPE), Call(Kind.HIDE, BARS))
                .inOrder()
            assertWithMessage("the last call hides the bars")
                .that(bars.calls.last())
                .isEqualTo(Call(Kind.HIDE, BARS))
        }
    }

    /** F19-AC3 */
    @Test
    fun offShowsSystemBars() {
        launch(immersive = true) { _ ->
            bars.clear()

            app.container.settingsRepository.update { it.copy(immersive = false) }
            settle()

            assertWithMessage("system-bar calls after Off")
                .that(bars.calls)
                .containsExactly(Call(Kind.SHOW, BARS))
        }
    }

    /** F19-AC4 */
    @Test
    fun reappliedOnFocusGain() {
        launch(immersive = true) { scenario ->
            // Another window showed the bars; the activity regains the focus.
            bars.clear()

            scenario.onActivity { it.onWindowFocusChanged(true) }

            assertWithMessage("system-bar calls on focus gain")
                .that(bars.calls)
                .containsExactly(Call(Kind.BEHAVIOR, TRANSIENT_BY_SWIPE), Call(Kind.HIDE, BARS))
                .inOrder()
        }
    }

    /** F19-AC5 */
    @Test
    fun appliesOnSubScreens() {
        launch(immersive = true) { _ ->
            bars.clear()
            for (key in listOf(SettingsKey, AboutKey)) {
                compose.onNodeWithTag(MORE_TAG).performClick()
                settle()
                compose.onNodeWithTag(menuItemTag(key)).performClick()
                settle()
                compose.onNodeWithTag(screenTag(key)).assertExists()
                assertWithMessage("$key: the bars are never shown")
                    .that(bars.calls.map { it.kind })
                    .doesNotContain(Kind.SHOW)
                bars.calls.lastOrNull()?.let { last ->
                    assertWithMessage("$key: the last call hides them")
                        .that(last)
                        .isEqualTo(Call(Kind.HIDE, BARS))
                }
                compose.onNodeWithTag(NAVIGATE_UP_TAG).performClick()
                settle()
            }
        }
    }

    /**
     * Writes `FIX_MID_4` and the settings ([immersive], Motion Off, onboarding done, the current
     * version seen) where the production container reads them, launches `MainActivity`, waits for
     * the game, and runs [test].
     */
    private fun launch(immersive: Boolean, test: (ActivityScenario<MainActivity>) -> Unit) {
        val settings =
            Settings(
                motion = MotionPreference.OFF,
                immersive = immersive,
                onboardingDone = true,
                lastSeenVersionCode = TEST_VERSION_CODE,
            )
        writeFiles(settings)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            val factory = app.container.gameViewModelFactory()
            scenario.onActivity { vm = ViewModelProvider(it, factory)[GameViewModel::class.java] }
            compose.waitUntil(LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            settle()
            test(scenario)
        }
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.waitForIdle()
    }

    private fun writeFiles(settings: Settings) {
        val dir = File(app.filesDir, "datastore").apply { mkdirs() }
        val game = GoldenFixtures.FIX_MID_4
        val document = GameDocumentMapper.toDocument(game)
        File(dir, DataStoreFiles.game(game.state.size))
            .writeText(AppJson.encodeToString(GameDocument.serializer(), document))
        File(dir, DataStoreFiles.SETTINGS)
            .writeText(AppJson.encodeToString(Settings.serializer(), settings))
    }

    private companion object {
        val BARS = WindowInsetsCompat.Type.systemBars()
        const val TRANSIENT_BY_SWIPE =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
