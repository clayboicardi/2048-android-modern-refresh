package com.clayboicardi.game2048.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.assertTouchTargets
import com.clayboicardi.game2048.testing.atfRule
import com.clayboicardi.game2048.testing.captureGolden
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The layout goldens of DESIGN_SYSTEM.md §10.4 rows 29–35 (D-092 qualifiers; D-147, D-204): the
 * Game screen with `FIX_MID_4`, Classic light, at each window of the matrix, under ATF and the
 * touch-target gate (D-085, D-205). Settings: Motion Off, onboarding done, the current version seen
 * (§10.2). The method runs once per window: Robolectric's qualifiers are set before the content is,
 * so each window is a fresh activity of that size (a test can set its content once).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutScreenshotTest(private val window: String) {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(atfRule(compose))
    @get:Rule val tmp = TemporaryFolder()

    /** F18-AC4 */
    @Test
    fun qualifierMatrixGoldens() {
        RuntimeEnvironment.setQualifiers(window)
        LayoutRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.setContent { rig.Content() }
            rig.awaitReady(compose)
            compose.waitForIdle()
            val root = compose.onRoot().fetchSemanticsNode().size
            val widthDp = window.substringAfter("w").substringBefore("dp").toFloat()
            assertWithMessage("$window: window width")
                .that(abs(root.width / compose.density.density - widthDp))
                .isLessThan(WINDOW_TOLERANCE_DP)
            compose.onRoot().captureGolden("layout_$window")
            compose.assertTouchTargets()
        }
    }

    companion object {
        /** Half a dp of rounding in the root's measured width. */
        private const val WINDOW_TOLERANCE_DP = 1f

        /** DESIGN_SYSTEM.md §10.4 rows 29–35: the D-092 qualifiers, which name the goldens. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun windows(): List<Array<Any>> =
            listOf(
                    "w360dp-h800dp",
                    "w412dp-h915dp",
                    "w915dp-h412dp-land",
                    "w673dp-h841dp",
                    "w841dp-h673dp-land",
                    "w800dp-h1280dp",
                    "w1280dp-h800dp-land",
                )
                .map { arrayOf(it) }
    }
}

/** One window's container and `GameViewModel` (QUALITY.md §1.6), with the §10.2 settings. */
private class LayoutRig(folder: File, game: SavedGame) : AutoCloseable {
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
                    themeMode = ThemeMode.LIGHT,
                    motion = MotionPreference.OFF,
                    lastBoardSize = game.state.size.n,
                    onboardingDone = true,
                    lastSeenVersionCode = TEST_VERSION_CODE,
                ),
            )
            container.seedGame(game)
            // A fixture's "best" is its bestAtStart, the size's record when the game started.
            val size = game.state.size.n.toString()
            container.seedRecords(
                Records(sizes = mapOf(size to SizeRecord(bestScore = game.bestAtStart))),
            )
        }
        vm = ViewModelProvider(store, container.gameViewModelFactory())[GameViewModel::class.java]
    }

    @Composable
    fun Content() {
        TestApp(container, vm)
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
