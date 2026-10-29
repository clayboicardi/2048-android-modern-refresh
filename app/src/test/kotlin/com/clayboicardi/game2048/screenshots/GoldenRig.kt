package com.clayboicardi.game2048.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavKey
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.MORE_TAG
import com.clayboicardi.game2048.menuItemTag
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowLooper

/**
 * The capture settings of DESIGN_SYSTEM.md §10.2 for [game]: Motion Off, onboarding done, the
 * current version seen, the game's size as the last one.
 */
internal fun goldenSettings(game: SavedGame): Settings =
    Settings(
        motion = MotionPreference.OFF,
        lastBoardSize = game.state.size.n,
        onboardingDone = true,
        lastSeenVersionCode = TEST_VERSION_CODE,
    )

/**
 * The records a game fixture implies: its "best" is its `bestAtStart` (DESIGN_SYSTEM.md §10.3,
 * D-216), the size's record when the game started, which the header shows; none when it is 0.
 */
internal fun recordsOf(game: SavedGame): Records? =
    if (game.bestAtStart > 0) {
        Records(
            sizes = mapOf(game.state.size.n.toString() to SizeRecord(bestScore = game.bestAtStart)),
        )
    } else {
        null
    }

/**
 * One golden's container and `GameViewModel` (QUALITY.md §1.6): [game] (and the saved games of
 * other sizes in [others]) with [settings] and [records], written into [folder] before the
 * ViewModel, in a `ViewModelStore` of its own, loads them. `App` over it is [Content].
 */
internal class GoldenRig(
    folder: File,
    game: SavedGame,
    settings: Settings = goldenSettings(game),
    records: Records? = recordsOf(game),
    others: List<SavedGame> = emptyList(),
) : AutoCloseable {
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
            container.seedSettings(settings)
            container.seedGame(game)
            for (other in others) container.seedGame(other)
            records?.let { container.seedRecords(it) }
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

/** Opens [key] from the Game screen's menu (More, then the item; D-249), Motion Off. */
internal fun ComposeContentTestRule.openFromMenu(key: NavKey) {
    onNodeWithTag(MORE_TAG).performClick()
    waitForIdle()
    onNodeWithTag(menuItemTag(key)).performClick()
    waitForIdle()
}

/**
 * Waits until [condition] holds, running the main looper between checks (what a screen reads on its
 * I/O dispatcher resumes there), then until Compose is idle.
 */
internal fun ComposeContentTestRule.awaitLoaded(condition: () -> Boolean) {
    waitUntil(timeoutMillis = 10_000L) {
        ShadowLooper.idleMainLooper()
        condition()
    }
    waitForIdle()
}
