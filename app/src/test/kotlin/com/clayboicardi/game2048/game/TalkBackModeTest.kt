package com.clayboicardi.game2048.game

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.fixtures.fixtureGame
import com.clayboicardi.game2048.game.input.PAD_TAG
import com.clayboicardi.game2048.game.input.PAD_UNDO_TAG
import com.clayboicardi.game2048.game.input.padButtonTag
import com.clayboicardi.game2048.game.overlay.CARD_NEW_GAME_TAG
import com.clayboicardi.game2048.game.overlay.GAME_OVER_CARD_TAG
import com.clayboicardi.game2048.game.overlay.KEEP_GOING_TAG
import com.clayboicardi.game2048.game.overlay.WIN_CARD_TAG
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.formatCount
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.Locale
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The TalkBack mode of the Game screen (FEATURES.md F17 items 4–6; DESIGN_SYSTEM.md §6.6; D-081,
 * D-082, D-104, D-202): while touch exploration is on (`ShadowAccessibilityManager`), the polite
 * status line and the forced-LTR direction pad are shown and the controls-bar Undo is removed. Each
 * test composes `App` over a container of its own, Pixel 7, Motion Off unless it says otherwise.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TalkBackModeTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F17-AC8 */
    @Test
    fun padAndStatusLineFollowTouchExploration() {
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)
            compose.onNodeWithTag(STATUS_LINE_TAG).assertDoesNotExist()
            compose.onNodeWithTag(PAD_TAG).assertDoesNotExist()

            setTouchExploration(true)
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeByFrame()

            compose.onNodeWithTag(STATUS_LINE_TAG).assertExists()
            compose.onNodeWithTag(PAD_TAG).assertExists()
            compose.mainClock.autoAdvance = true

            setTouchExploration(false)
            compose.waitForIdle()
            compose.onNodeWithTag(STATUS_LINE_TAG).assertDoesNotExist()
            compose.onNodeWithTag(PAD_TAG).assertDoesNotExist()
        }
    }

    /** F17-AC9 */
    @Test
    fun statusLineDescribesMove() {
        // Row 1 = 32, 32, 8, 8 at score 1160: LEFT merges 64 and 16 to score 1240.
        val game = game(cells = listOf(5, 5, 3, 3) + List(12) { 0 }, score = 1160)
        val moved = Game.move(game.state, Direction.LEFT) as MoveOutcome.Moved
        assertThat(moved.state.score).isEqualTo(1240)
        val spawn = moved.spawn
        val value = 1L shl spawn.exp
        val row = spawn.index / 4 + 1
        val column = spawn.index % 4 + 1
        setTouchExploration(true)
        rig(game).use { rig ->
            show(rig)
            assertThat(statusText()).isEmpty()

            rig.vm.onMove(Direction.LEFT)
            compose.waitForIdle()

            assertThat(statusText())
                .isEqualTo(
                    "Moved left. Merged 64 and 16. New $value at row $row, column $column. " +
                        "Score 1,240.",
                )
        }
    }

    /** F17-AC10 */
    @Test
    fun statusLineSummarizesManyMerges() {
        // LEFT merges 32+32, 16+16, 8+8, 4+4 and 2+2: it creates 64, 32, 16, 8 and 4.
        val cells = listOf(5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0)
        val game = game(cells = cells, score = 5000)
        val moved = Game.move(game.state, Direction.LEFT) as MoveOutcome.Moved
        assertThat(moved.mergeCount).isEqualTo(5)
        setTouchExploration(true)
        rig(game).use { rig ->
            show(rig)

            rig.vm.onMove(Direction.LEFT)
            compose.waitForIdle()

            assertThat(statusText()).contains(" Merged 64, 32, 16 and 2 more. ")
            assertThat(statusText()).startsWith("Moved left. Merged 64, 32, 16 and 2 more. New ")
        }
    }

    /** F17-AC11 */
    @Test
    fun statusLineDescribesBlockedMove() {
        val game = MotionFixtures.FIX_INVALID_4
        assertThat(Game.move(game.state, Direction.UP))
            .isInstanceOf(MoveOutcome.Invalid::class.java)
        setTouchExploration(true)
        rig(game).use { rig ->
            show(rig)

            rig.vm.onMove(Direction.UP)
            compose.waitForIdle()

            assertThat(statusText()).isEqualTo("Can't move up.")
        }
    }

    /** F17-AC11 */
    @Test
    fun statusLineRepeatsBlockedMove() {
        // A second blocked move in the same direction is read again: its sentence equals the one
        // shown, so the line is blank for a frame before it shows the sentence again, and the
        // polite live region's text changes on both attempts (D-252).
        setTouchExploration(true)
        rig(MotionFixtures.FIX_INVALID_4).use { rig ->
            show(rig)
            compose.mainClock.autoAdvance = false
            val texts = mutableListOf(statusText())
            repeat(2) {
                rig.vm.onMove(Direction.UP)
                repeat(ATTEMPT_FRAMES) {
                    compose.mainClock.advanceTimeByFrame()
                    texts += statusText()
                }
            }

            val changes = texts.filterIndexed { i, text -> i == 0 || text != texts[i - 1] }
            assertWithMessage("the status line's texts, frame by frame: $texts")
                .that(changes)
                .containsExactly("", "Can't move up.", "", "Can't move up.")
                .inOrder()
        }
    }

    /** F17-AC12 */
    @Test
    fun cardsAnnounceAndTakeFocus() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        MotionFixtures.OVER_PRE_LEFT.verify()
        setTouchExploration(true)
        val win = rig(MotionFixtures.FIX_WIN_PRE_4)
        val over = rig(MotionFixtures.FIX_OVER_PRE_4)
        try {
            var shown by mutableStateOf(win)
            compose.setContent { key(shown) { shown.Content() } }
            win.awaitReady(compose)
            compose.waitForIdle()

            win.vm.onMove(Direction.LEFT)
            compose.waitForIdle()
            assertCard(WIN_CARD_TAG, "You win!", KEEP_GOING_TAG)

            shown = over
            over.awaitReady(compose)
            compose.waitForIdle()
            over.vm.onMove(Direction.LEFT)
            compose.waitForIdle()
            assertThat(over.vm.uiState.value.status).isEqualTo(GameStatus.OVER)
            assertCard(GAME_OVER_CARD_TAG, "Game over", CARD_NEW_GAME_TAG)
        } finally {
            win.close()
            over.close()
        }
    }

    /** F17-AC13 */
    @Config(qualifiers = "+fa")
    @Test
    fun directionPadForcedLtrUnderPersian() {
        val game = GoldenFixtures.FIX_MID_4
        val expected = Game.move(game.state, Direction.LEFT) as MoveOutcome.Moved
        setTouchExploration(true)
        rig(game).use { rig ->
            show(rig)
            val left = compose.onNodeWithTag(padButtonTag(Direction.LEFT)).fetchSemanticsNode()
            val right = compose.onNodeWithTag(padButtonTag(Direction.RIGHT)).fetchSemanticsNode()
            assertWithMessage("the left arrow is left of the right arrow")
                .that(left.boundsInRoot.right)
                .isAtMost(right.boundsInRoot.left)

            compose.onNodeWithTag(padButtonTag(Direction.LEFT)).performClick()
            compose.waitForIdle()

            val board = expected.state.board
            assertThat(rig.vm.board.value.cells).isEqualTo(List(board.cellCount) { board[it] })
        }
    }

    /** F17-AC14 */
    @Test
    fun controlsUndoRemovedWhilePadShown() {
        setTouchExploration(true)
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)

            val undo =
                compose
                    .onAllNodes(
                        hasClickAction() and (hasText("Undo") or hasContentDescription("Undo")),
                    )
                    .fetchSemanticsNodes()
            assertWithMessage("clickable Undo nodes").that(undo).hasSize(1)
            assertWithMessage("the one Undo is the pad's")
                .that(undo.single().config.getOrNull(SemanticsProperties.TestTag))
                .isEqualTo(PAD_UNDO_TAG)
            compose.onNodeWithTag(UNDO_TAG).assertDoesNotExist()
        }
    }

    /** F17-AC19 */
    @Test
    fun statusLineAfterUndo() {
        // FIX_MID_4's board and score as a 32-move game, so a move played from it undoes exactly
        // back to it (the fixture's own history does not replay, D-214).
        val mid = GoldenFixtures.FIX_MID_4.state
        val game = game(cells = List(mid.board.cellCount) { mid.board[it] }, score = mid.score)
        setTouchExploration(true)
        rig(game).use { rig ->
            show(rig)
            rig.vm.onMove(Direction.LEFT)
            compose.waitForIdle()

            compose.onNodeWithTag(PAD_UNDO_TAG).performClick()
            compose.waitForIdle()

            assertThat(rig.vm.header.value.score).isEqualTo(mid.score)
            val score = formatCount(mid.score, Locale.ENGLISH)
            assertThat(statusText()).isEqualTo("Move undone. Score $score.")
        }
    }

    /** F17-AC20 */
    @Test
    fun statusLineAfterNewGameAndSizeSwitch() {
        setTouchExploration(true)
        rig(GoldenFixtures.FIX_FRESH_4).use { rig ->
            show(rig)

            // A fresh board: New game starts at once (D-007), with its two start tiles.
            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            val cells = rig.vm.board.value.cells
            val spawned =
                cells.indices
                    .filter { cells[it] != 0 }
                    .joinToString(" ") { index ->
                        "New ${1L shl cells[index]} at row ${index / 4 + 1}, " +
                            "column ${index % 4 + 1}."
                    }
            assertThat(cells.count { it != 0 }).isEqualTo(2)
            assertThat(statusText()).isEqualTo("New game started. $spawned")

            rig.vm.onSizeSelected(BoardSize.of(5))
            compose.waitUntil(timeoutMillis = SWITCH_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                rig.vm.board.value.size.n == 5 && !rig.vm.uiState.value.busy
            }
            compose.waitForIdle()
            val score = formatCount(rig.vm.header.value.score, Locale.ENGLISH)
            assertThat(statusText()).isEqualTo("Board 5×5. Score $score.")
        }
    }

    /**
     * The card [tag] is shown: its `paneTitle` is [title], one of its nodes is an assertive live
     * region, and the primary button [primary] has the focus (D-081).
     */
    private fun assertCard(tag: String, title: String, primary: String) {
        val card = compose.onNodeWithTag(tag).fetchSemanticsNode()
        assertWithMessage("$tag paneTitle")
            .that(card.config.getOrNull(SemanticsProperties.PaneTitle))
            .isEqualTo(title)
        val assertive =
            compose
                .onAllNodes(
                    hasAnyAncestor(hasTestTag(tag)) and
                        SemanticsMatcher.expectValue(
                            SemanticsProperties.LiveRegion,
                            LiveRegionMode.Assertive,
                        ),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
        assertWithMessage("$tag: one assertive live region").that(assertive).hasSize(1)
        compose.onNodeWithTag(primary).assertIsFocused()
    }

    private fun statusText(): String =
        compose
            .onNodeWithTag(STATUS_LINE_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .joinToString("")

    private fun setTouchExploration(on: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(AccessibilityManager::class.java))
            .setTouchExplorationEnabled(on)
    }

    private fun rig(game: SavedGame) = TalkBackRig(tmp.newFolder(), game)

    private fun show(rig: TalkBackRig) {
        compose.setContent { rig.Content() }
        rig.awaitReady(compose)
        compose.waitForIdle()
    }

    /** A 4×4 `PLAYING` game on [cells] at [score], 32 moves (so its own snapshot is the state). */
    private fun game(cells: List<Int>, score: Long): SavedGame =
        fixtureGame(
            n = 4,
            cells = cells,
            score = score,
            best = score,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )
}

/** One game's container and `GameViewModel` (QUALITY.md §1.6), Motion Off, onboarding done. */
private class TalkBackRig(folder: File, game: SavedGame) : AutoCloseable {
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

/** A bound on a size switch's real-thread load, not a timing under test. */
private const val SWITCH_TIMEOUT_MS = 10_000L

/** Frames watched after each blocked move: the nudge and the status line settle well within. */
private const val ATTEMPT_FRAMES = 30
