package com.clayboicardi.game2048.game.board

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameScreen
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MoveTimeline
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.theme.ClassicLightSkin
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The board's accessibility tree (D-080; FEATURES.md F17; ARCHITECTURE.md §6.4): one board node
 * with `CollectionInfo(N, N)`, its description and custom actions, N² fixed cell nodes and no node
 * from a tile. Robolectric at SDK 36, Classic light, Motion Full.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BoardSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private val host by lazy { BoardHost(compose, folder) }

    @After
    fun closeHost() {
        host.close()
    }

    /** F17-AC1 */
    @Test
    fun boardNodeDescribesCollection() {
        host.show(host.load(GoldenFixtures.FIX_MID_4))

        val node = boardNode()
        val info = checkNotNull(node.config.getOrNull(SemanticsProperties.CollectionInfo))
        assertThat(info.rowCount).isEqualTo(4)
        assertThat(info.columnCount).isEqualTo(4)
        assertThat(node.config[SemanticsProperties.ContentDescription])
            .containsExactly("Game board, 4 by 4. 13 tiles. Highest tile 1024.")
    }

    /** F17-AC2 */
    @Config(qualifiers = "w360dp-h800dp")
    @Test
    fun cellLabelsUseFullDecimal() {
        // 8×8 on a 360 dp window: a 328 dp board (16 dp margins), so 2^14 renders "16K".
        val side = 360.dp - horizontalMargin(360.dp, 0.dp) * 2
        host.show(host.load(GoldenFixtures.FIX_DENSE_8), side)

        val cells = cellNodes()
        assertThat(cells).hasSize(64)
        val labels = cells.map { it.config[SemanticsProperties.ContentDescription].single() }
        assertThat(labels).contains("Row 2, column 3, 16384")
        assertThat(labels).contains("Row 3, column 7, empty")
        assertThat(labels).contains("Row 1, column 1, 2")
        assertThat(labels.toSet()).hasSize(64)
        val density = compose.density.density
        val tileDp = boardGeometry(8, side, ClassicLightSkin, density).tilePx / density
        assertWithMessage("the 16384 tile renders compact")
            .that(TileLabels.label(14, tileDp).text)
            .isEqualTo("16K")
        for (cell in cells) {
            val item = checkNotNull(cell.config.getOrNull(SemanticsProperties.CollectionItemInfo))
            val label = cell.config[SemanticsProperties.ContentDescription].single()
            assertThat(label)
                .startsWith("Row ${item.rowIndex + 1}, column ${item.columnIndex + 1}, ")
        }
    }

    /** F17-AC3 */
    @Test
    fun moveActionsApplyMoves() {
        val fixture = GoldenFixtures.FIX_MID_4
        val vm = host.show(host.load(fixture)).value
        val expected = Game.move(fixture.state, Direction.LEFT) as MoveOutcome.Moved

        assertThat(actionLabels())
            .containsAtLeast("Move up", "Move down", "Move left", "Move right")
            .inOrder()
        assertThat(perform("Move left")).isTrue()
        compose.mainClock.advanceTimeByFrame()

        val board = expected.state.board
        assertThat(vm.board.value.cells).isEqualTo(List(board.cellCount) { board[it] })
        assertThat(vm.board.value.event).isInstanceOf(BoardEvent.Moved::class.java)
    }

    /** F17-AC4 */
    @Test
    fun undoActionOnlyWhenAvailable() {
        val fixture = GoldenFixtures.FIX_FRESH_4
        val vm = host.show(host.load(fixture)).value
        assertThat(actionLabels()).doesNotContain("Undo")

        host.commit { vm.onMove(Direction.LEFT) }
        host.settle()
        assertThat(actionLabels()).contains("Undo")

        assertThat(perform("Undo")).isTrue()
        host.settle()
        val board = fixture.state.board
        assertThat(vm.board.value.cells).isEqualTo(List(board.cellCount) { board[it] })
        assertThat(vm.uiState.value.canUndo).isFalse()
        assertThat(actionLabels()).doesNotContain("Undo")
    }

    /** F17-AC5 */
    @Test
    fun newGameActionFollowsConfirmation() {
        // FIX_MID_4 is in progress, so the screen asks first (D-007) and nothing changes yet.
        val fixture = GoldenFixtures.FIX_MID_4
        val vm = host.load(fixture)
        host.showScreen(vm)

        assertThat(perform("New game")).isTrue()
        host.awaitDialog()

        compose.onNodeWithText("Start a new game?").assertExists()
        val board = fixture.state.board
        assertThat(vm.board.value.cells).isEqualTo(List(board.cellCount) { board[it] })
    }

    /** F17-AC6 */
    @Test
    fun tilesCarryNoSemantics() {
        val vm = host.show(host.load(GoldenFixtures.FIX_MID_4)).value
        val origin = host.commit { vm.onMove(Direction.LEFT) }
        host.advanceTo(origin, 48)

        val tree = compose.onNodeWithTag(BOARD_TAG, useUnmergedTree = true).printToString()
        val nodes = boardNode().descendants()
        val cells = nodes.filter { it.isCell() }
        assertWithMessage("cell nodes in\n$tree").that(cells).hasSize(16)
        for (cell in cells) {
            assertThat(cell.config[SemanticsProperties.ContentDescription].single())
                .startsWith("Row ")
            assertWithMessage("a cell has no child node").that(cell.children).isEmpty()
        }
        // Any other node is an empty layer that holds cells (the size switch's content), never a
        // tile, which would be a leaf.
        for (node in nodes.filterNot { it.isCell() }) {
            assertWithMessage("an extra node with properties in\n$tree")
                .that(node.config.toList())
                .isEmpty()
            assertWithMessage("an extra leaf node in\n$tree")
                .that(node.descendants().count { it.isCell() })
                .isEqualTo(16)
        }
        assertWithMessage("tiles are drawn while the move runs")
            .that(host.probe.snapshot().tiles.size)
            .isGreaterThan(13)
    }

    private fun boardNode(): SemanticsNode =
        compose.onNodeWithTag(BOARD_TAG, useUnmergedTree = true).fetchSemanticsNode()

    private fun cellNodes(): List<SemanticsNode> = boardNode().descendants().filter { it.isCell() }

    private fun actions(): List<CustomAccessibilityAction> =
        boardNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()

    private fun actionLabels(): List<String> = actions().map { it.label }

    private fun perform(label: String): Boolean {
        val action = actions().single { it.label == label }
        var handled = false
        compose.runOnUiThread { handled = action.action() }
        return handled
    }
}

/** Every node below this one, depth first (the unmerged tree). */
private fun SemanticsNode.descendants(): List<SemanticsNode> = children.flatMap {
    listOf(it) + it.descendants()
}

/** A board cell node: it carries its `CollectionItemInfo` (D-080). */
private fun SemanticsNode.isCell(): Boolean =
    config.getOrNull(SemanticsProperties.CollectionItemInfo) != null

/** The board node's test tag (`BoardView`). */
internal const val BOARD_TAG = "game:board"

/**
 * Loads fixture games into `GameViewModel`s over real DataStores in a temporary folder (one
 * `AppContainer` per game, the pattern of `SplashGateTest`) and shows a board the way the game
 * screen does: inside `GameTheme`, with a screen-scope `MoveTimeline`, the presented board of
 * ARCHITECTURE.md §4.11 item 2, and the test's `MotionProbe` and recomposition counter. The main
 * clock is driven by hand (`autoAdvance = false`) once a board is shown.
 */
internal class BoardHost(
    private val rule: ComposeContentTestRule,
    private val folder: TemporaryFolder,
) {
    val probe = MotionProbe()
    val counter = CountingRecompositions()
    val clock = FakeClock()
    /** The containers' application scope; cancelled by [close]. */
    private val scope = CoroutineScope(SupervisorJob())

    /** A ready `GameViewModel` whose current game is [fixture]. */
    fun load(fixture: SavedGame): GameViewModel {
        val dir = folder.newFolder()
        val container =
            AppContainer(
                files = { name -> File(dir, name) },
                versionCode = TEST_VERSION_CODE,
                applicationScope = scope,
                clock = clock,
                seedSource = FixedSeedSource(SEED),
                animatorScale = FakeAnimatorScaleSource(),
            )
        runBlocking {
            container.seedSettings(Settings(lastBoardSize = fixture.state.size.n))
            container.seedGame(fixture)
        }
        val vm =
            GameViewModel(
                container.gameRepository,
                container.recordsRepository,
                container.settingsRepository,
                clock,
                container.seedSource,
            )
        // The load reads DataStore on real I/O threads and resumes on the main looper.
        rule.waitUntil(LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.ready.value
        }
        return vm
    }

    /**
     * Shows [first]'s board at [side] under [motion] and settles it; the returned state switches
     * the board to another game (a new `BoardView` and `MoveTimeline`).
     */
    fun show(
        first: GameViewModel,
        side: Dp = BOARD_SIDE,
        motion: MotionPreference = MotionPreference.FULL,
    ): MutableState<GameViewModel> {
        val active = mutableStateOf(first)
        rule.setContent {
            GameTheme(darkTheme = false, motion = motion) {
                key(active.value) {
                    val timeline = remember { MoveTimeline() }
                    CompositionLocalProvider(
                        LocalMoveTimeline provides timeline,
                        LocalMotionProbe provides probe,
                        LocalRecompositionCounter provides counter,
                    ) {
                        HostedBoard(active.value, clock, side)
                    }
                }
            }
        }
        rule.mainClock.autoAdvance = false
        settle()
        return active
    }

    /** Shows the whole Game screen for [vm] (its dialog, its own `MoveTimeline`). */
    fun showScreen(vm: GameViewModel) {
        rule.setContent {
            GameTheme(darkTheme = false) {
                CompositionLocalProvider(LocalMotionProbe provides probe) {
                    GameScreen(vm, Settings(), clock)
                }
            }
        }
        rule.mainClock.autoAdvance = false
        settle()
    }

    /**
     * Advances frames, running the main looper before each, until a dialog window exists (at most
     * [MAX_SETTLE_FRAMES]): the dialog is a window of its own, which Robolectric shows only when
     * the looper runs, and its content composes on a later frame.
     */
    fun awaitDialog() {
        var frames = 0
        while (
            rule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() &&
                frames < MAX_SETTLE_FRAMES
        ) {
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeByFrame()
            frames++
        }
    }

    /** Advances frames until a board is loaded and nothing runs. */
    fun settle() {
        var frames = 0
        do {
            rule.mainClock.advanceTimeByFrame()
            frames++
            val snapshot = probe.snapshot()
        } while ((!snapshot.boardLoaded || snapshot.isAnyRunning) && frames < MAX_SETTLE_FRAMES)
        check(frames < MAX_SETTLE_FRAMES) { "the board did not settle" }
    }

    /**
     * Runs [action], then advances one frame at a time (at most 3) until `BoardView` publishes a
     * new origin, which must be the current frame (MOTION_SPEC.md §8.1). Returns the origin.
     */
    fun commit(action: () -> Unit): Long {
        val before = probe.originMs
        action()
        var origin: Long? = null
        var frames = 0
        while (origin == null && frames < MAX_ORIGIN_FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            frames++
            origin = probe.originMs?.takeIf { it != before }
        }
        checkNotNull(origin) { "no origin published within $MAX_ORIGIN_FRAMES frames" }
        check(origin == rule.mainClock.currentTime) { "origin $origin is not the current frame" }
        return origin
    }

    /** Advances to the first 16 ms grid frame at or after [origin] + [tMs]. */
    fun advanceTo(origin: Long, tMs: Long) {
        val target = origin + (tMs + FRAME_MS - 1) / FRAME_MS * FRAME_MS
        rule.mainClock.advanceTimeBy(target - rule.mainClock.currentTime)
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val SEED = 0x2048L
        const val LOAD_TIMEOUT_MS = 10_000L
        const val MAX_SETTLE_FRAMES = 200
        const val MAX_ORIGIN_FRAMES = 3
        const val FRAME_MS = 16L

        /** The 4×4 Classic board of DESIGN_SYSTEM.md §4.3 (D-075). */
        val BOARD_SIDE = 328.dp
    }
}

/** A test `RecompositionCounter` (D-150): counts per tile id, board and screen. */
internal class CountingRecompositions : RecompositionCounter {
    val tiles = HashMap<Long, Int>()
    var board = 0
        private set

    var screen = 0
        private set

    override fun onTile(id: Long) {
        tiles[id] = (tiles[id] ?: 0) + 1
    }

    override fun onBoard() {
        board++
    }

    override fun onScreen() {
        screen++
    }

    fun reset() {
        tiles.clear()
        board = 0
        screen = 0
    }
}

/** The board the game screen shows: its arrangement scope's presented value (§4.11 item 2). */
@Composable
private fun HostedBoard(vm: GameViewModel, clock: MonotonicClock, side: Dp) {
    val boardIn = vm.board.collectAsState()
    val board by remember(boardIn) { derivedStateOf(SamePresentation) { boardIn.value } }
    val ui by vm.uiState.collectAsState()
    val nudge by vm.nudge.collectAsState()
    BoardView(
        board = board,
        nudge = nudge,
        status = ui.status,
        canUndo = ui.canUndo,
        loading = !ui.ready,
        clock = clock,
        onMove = vm::onMove,
        onUndo = vm::onUndo,
        onNewGame = vm::onNewGame,
        onBoardKey = { false },
        onPresented = vm::onBoardPresented,
        modifier = Modifier.size(side),
    )
}

/** `sameBoardPresentation`: only `settled()` keeps a `moveId` (ARCHITECTURE.md §4.11). */
private object SamePresentation : SnapshotMutationPolicy<BoardUi> {
    override fun equivalent(a: BoardUi, b: BoardUi): Boolean = a.moveId == b.moveId
}
