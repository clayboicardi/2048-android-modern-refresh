package com.clayboicardi.game2048.game.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.App
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.MoveTiming
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SwipeSensitivity
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedSettings
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * `Modifier.swipeToMove` (D-050–D-052, D-104; ARCHITECTURE.md §6.1). The first six methods drive a
 * plain test box that records `onMove`; the rest drive the Game screen (`App` over a container of
 * its own, [GameScreenRig]) and need `GameScreen` (M3-D1).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeInputTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val moves = mutableListOf<Direction>()
    private var config by mutableStateOf(SwipeConfig(MoveTiming.DURING_SWIPE, MEDIUM))
    private var layoutDirection: LayoutDirection? = null
    private var clicks = 0

    /** F15-AC3 */
    @Test
    fun commitsDuringDragOncePerGesture() {
        setBox()

        surface().performTouchInput {
            down(center)
            dragX(30f)
        }
        assertThat(moves).containsExactly(Direction.RIGHT)

        surface().performTouchInput {
            dragX(100f)
            up()
        }
        assertThat(moves).containsExactly(Direction.RIGHT)
        assertThat(clicks).isEqualTo(0)
    }

    /** F15-AC4 */
    @Test
    fun onReleaseCommitsAtPointerUp() {
        config = SwipeConfig(MoveTiming.ON_RELEASE, MEDIUM)
        setBox()

        surface().performTouchInput {
            down(center)
            dragX(30f)
        }
        assertThat(moves).isEmpty()

        surface().performTouchInput {
            dragX(100f)
            up()
        }
        assertThat(moves).containsExactly(Direction.RIGHT)
        assertThat(clicks).isEqualTo(0)
    }

    /** F15-AC5 */
    @Test
    fun sensitivityThresholds() {
        setBox()
        val counts =
            SwipeSensitivity.entries.associateWith { sensitivity ->
                config = SwipeConfig(MoveTiming.DURING_SWIPE, sensitivity.thresholdDp)
                compose.waitForIdle()
                moves.clear()
                surface().performTouchInput {
                    down(center)
                    dragX(30f)
                    // Held still before the release: no fling.
                    advanceEventTime(STILL_MS)
                    up()
                }
                moves.toList()
            }

        assertThat(counts[SwipeSensitivity.LOW]).isEmpty()
        assertThat(counts[SwipeSensitivity.MEDIUM]).containsExactly(Direction.RIGHT)
        assertThat(counts[SwipeSensitivity.HIGH]).containsExactly(Direction.RIGHT)
        assertThat(clicks).isEqualTo(0)
    }

    /** F15-AC6 */
    @Test
    fun flingCommitsShortFastSwipe() {
        setBox()

        for (timing in MoveTiming.entries) {
            config = SwipeConfig(timing, MEDIUM)
            compose.waitForIdle()
            moves.clear()
            surface().performTouchInput {
                down(center)
                // 15 dp in 17 ms (about 900 dp/s), released at once.
                for (stepMs in FLICK_STEPS_MS) moveBy(Offset(3.dp.toPx(), 0f), stepMs)
                up()
            }
            assertWithMessage(timing.name).that(moves).containsExactly(Direction.RIGHT)
        }
        assertThat(clicks).isEqualTo(0)
    }

    /** F15-AC7 */
    @Test
    fun secondPointerCancels() {
        setBox()

        surface().performTouchInput {
            down(0, center)
            moveBy(0, Offset(10.dp.toPx(), 0f))
            down(1, center + Offset(0f, 40.dp.toPx()))
            repeat(6) {
                updatePointerBy(0, Offset(10.dp.toPx(), 0f))
                updatePointerBy(1, Offset(10.dp.toPx(), 0f))
                move()
            }
            up(0)
            up(1)
        }

        assertThat(moves).isEmpty()
        assertThat(clicks).isEqualTo(0)
    }

    /** F15-AC10 */
    @Test
    @Config(qualifiers = "fa")
    fun leftSwipeIsLeftInRtl() {
        setBox()

        surface().performTouchInput {
            down(center)
            dragX(-60f)
            up()
        }

        assertThat(layoutDirection).isEqualTo(LayoutDirection.Rtl)
        assertThat(moves).containsExactly(Direction.LEFT)
        assertThat(clicks).isEqualTo(0)
    }

    /** F15-AC8 */
    @Test
    fun dragStartingOnButtonDoesNotClick() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.showGame(rig)
            val before = rig.vm.board.value.moveId

            compose.onNodeWithTag(NEW_GAME).performTouchInput {
                down(center)
                dragX(-60f)
                up()
            }
            compose.waitForIdle()

            assertMovedLeft(rig, before)
            compose.onNode(isDialog()).assertDoesNotExist()
        }
    }

    /** F15-AC9 */
    @Test
    fun tapDoesNothing() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.showGame(rig)
            val before = rig.vm.board.value.moveId

            compose.onNodeWithTag(BOARD).performTouchInput { click(center) }
            // The title's corner of the header: no control there.
            compose.onNodeWithTag(HEADER).performTouchInput { click(Offset(2f, 2f)) }
            compose.waitForIdle()

            assertThat(rig.vm.board.value.moveId).isEqualTo(before)
            compose.onNode(isDialog()).assertDoesNotExist()
        }
    }

    /** F15-AC11 */
    @Test
    fun ignoredWhileModalOpen() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.showGame(rig)
            // Part 1 has the new-game dialog; the size sheet and win card (M3.9), the menu (M3.10)
            // and What's new (M4) join this test (D-233).
            compose.onNodeWithTag(NEW_GAME).performClick()
            compose.onNode(isDialog()).assertExists()
            val before = rig.vm.board.value.moveId

            compose.onNodeWithTag(BOARD).performTouchInput {
                down(center)
                dragX(-60f)
                up()
            }
            compose.waitForIdle()

            assertThat(rig.vm.board.value.moveId).isEqualTo(before)
            compose.onNode(isDialog()).assertExists()
        }
    }

    /** F15-AC12 */
    @Test
    fun swipeAnywhereOnGameContent() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.showGame(rig)
            val before = rig.vm.board.value.moveId

            compose.onNodeWithTag(HEADER).performTouchInput {
                down(center)
                dragX(-60f)
                up()
            }
            compose.waitForIdle()

            assertMovedLeft(rig, before)
        }
    }

    /** F19-AC6 */
    @Test
    fun longPressDoesNothing() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.showGame(rig)
            val moveId = rig.vm.board.value.moveId
            val settings = rig.container.settingsRepository.state.value

            for (tag in listOf(BOARD, NEW_GAME)) {
                compose.onNodeWithTag(tag).performTouchInput {
                    down(center)
                    advanceEventTime(LONG_PRESS_MS)
                    up()
                }
                compose.waitForIdle()
                ShadowLooper.idleMainLooper()
            }

            assertThat(rig.vm.board.value.moveId).isEqualTo(moveId)
            assertThat(rig.container.settingsRepository.state.value).isEqualTo(settings)
            compose.onNode(isDialog()).assertDoesNotExist()
        }
    }

    /**
     * The test box: the whole content over a clickable child, recording every `onMove`, every click
     * and the layout direction. Its touch slop is the platform's 8 dp
     * (`android.view.ViewConfiguration`'s TOUCH_SLOP, the slop of F15-AC2), where Robolectric
     * reports 16 px.
     */
    private fun setBox() {
        compose.setContent {
            layoutDirection = LocalLayoutDirection.current
            val configState = remember { derivedStateOf { config } }
            val enabled = remember { mutableStateOf(true) }
            val base = LocalViewConfiguration.current
            val slop = with(LocalDensity.current) { PLATFORM_TOUCH_SLOP_DP.dp.toPx() }
            val viewConfiguration = remember(base, slop) { PlatformSlop(base, slop) }
            CompositionLocalProvider(LocalViewConfiguration provides viewConfiguration) {
                Box(
                    Modifier.fillMaxSize().testTag(SURFACE).swipeToMove(configState, enabled) {
                        moves += it
                    },
                ) {
                    // A button under every drag: the consumed stream never clicks it (D-051).
                    Box(Modifier.fillMaxSize().clickable { clicks++ })
                }
            }
        }
    }

    /** [base] with the touch slop [touchSlop] (px). */
    private class PlatformSlop(base: ViewConfiguration, override val touchSlop: Float) :
        ViewConfiguration by base

    private fun surface() = compose.onNodeWithTag(SURFACE)

    private companion object {
        const val SURFACE = "test:swipe"
        const val BOARD = "game:board"
        const val HEADER = "game:header"
        const val NEW_GAME = "game:newGame"
        val MEDIUM = SwipeSensitivity.MEDIUM.thresholdDp

        /** `android.view.ViewConfiguration`'s TOUCH_SLOP on devices. */
        const val PLATFORM_TOUCH_SLOP_DP = 8f

        /** A pause long enough for the velocity tracker to read the pointer as stopped. */
        const val STILL_MS = 200L

        /** Three times the platform long-press timeout (F19-AC6). */
        const val LONG_PRESS_MS = 3_000L

        /** Five 3 dp steps over 17 ms of event time. */
        val FLICK_STEPS_MS = longArrayOf(4, 3, 3, 4, 3)
    }
}

/** A slow drag of [dp] along x (negative: leftwards) in 5 dp steps, one event period apart. */
private fun TouchInjectionScope.dragX(dp: Float) {
    val steps = (kotlin.math.abs(dp) / STEP_DP).toInt()
    val step = Offset((dp / steps).dp.toPx(), 0f)
    repeat(steps) { moveBy(step) }
}

private const val STEP_DP = 5f

/**
 * The Game screen over a container of its own (QUALITY.md §1.6): [game] and [settings] are written
 * into [folder] before the `GameViewModel` (in a `ViewModelStore` of its own) loads them, with a
 * `FakeClock`, `FixedSeedSource(0x2048)` and a fake animator scale, so no test depends on the
 * device clock or an unseeded random source. Shared by `SwipeInputTest` and `KeyboardInputTest`.
 */
internal class GameScreenRig(
    folder: File,
    game: SavedGame,
    settings: Settings = Settings(lastBoardSize = game.state.size.n, onboardingDone = true),
) : AutoCloseable {
    val container =
        AppContainer(
            files = { name -> File(folder, name) },
            versionCode = TEST_VERSION_CODE,
            clock = FakeClock(),
            seedSource = FixedSeedSource(RIG_SEED),
            animatorScale = FakeAnimatorScaleSource(),
        )
    private val size = game.state.size
    private val store = ViewModelStore()
    val vm: GameViewModel

    init {
        runBlocking {
            container.seedSettings(settings)
            container.seedGame(game)
        }
        vm = ViewModelProvider(store, container.gameViewModelFactory())[GameViewModel::class.java]
    }

    /**
     * Waits for the load: the stores read on real I/O threads and resume on the main looper, which
     * Robolectric runs only when asked, so each poll runs it (the `SplashGateTest` pattern).
     */
    fun awaitReady(compose: ComposeTestRule) {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.ready.value
        }
    }

    /** The latest game the ViewModel saved for the fixture's size (submitted or on disk). */
    fun saved(): SavedGame = runBlocking { checkNotNull(container.gameRepository.peek(size)) }

    override fun close() {
        store.clear()
        container.applicationScope.cancel()
    }
}

/**
 * One LEFT move was applied since [beforeMoveId]. The board's own event is settled to `Restored`
 * once `BoardView` reports it presented (ARCHITECTURE.md §4.11), so the move is read from the
 * header, which keeps the event kind, and from the saved game's move log.
 */
internal fun assertMovedLeft(rig: GameScreenRig, beforeMoveId: Long) {
    assertThat(rig.vm.board.value.moveId).isGreaterThan(beforeMoveId)
    assertThat(rig.vm.header.value.event).isEqualTo(BoardEventKind.Moved)
    assertThat(rig.saved().state.moveLog.toList().last()).isEqualTo(Direction.LEFT)
}

/** Composes `App` over [rig] and waits until its game has loaded. */
internal fun ComposeContentTestRule.showGame(rig: GameScreenRig) {
    setContent {
        App(
            gameViewModel = rig.vm,
            settings = rig.container.settingsRepository.state,
            animatorScale = rig.container.animatorScale,
            clock = rig.container.clock,
        )
    }
    rig.awaitReady(this)
    waitForIdle()
}

private const val RIG_SEED = 0x2048L

/** A bound on the real-thread DataStore load, not a timing under test. */
private const val LOAD_TIMEOUT_MS = 10_000L
