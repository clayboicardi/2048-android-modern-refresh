package com.clayboicardi.game2048

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.OverlayEntry
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.header.SIZE_CHIP_TAG
import com.clayboicardi.game2048.game.layoutTag
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.game.overlay.SIZE_SHEET_TAG
import com.clayboicardi.game2048.game.overlay.sizeRowTag
import com.clayboicardi.game2048.motion.FilmRegion
import com.clayboicardi.game2048.motion.MotionHarness
import com.clayboicardi.game2048.motion.MotionRule
import com.clayboicardi.game2048.motion.Tol
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.ui.components.LayoutMode
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * D-047: recreation and a pause/resume cycle replay nothing (ARCHITECTURE.md §4.11). In M2 `App()`
 * composes no board, so the test makes the presentation reports that `BoardView` and `GameHeader`
 * make from M3, and reads the activity-scoped `GameViewModel`.
 */
@RunWith(AndroidJUnit4::class)
class RecreateNoReplayTest {
    /** Outside the compose rule (default order), so its files are written before the launch. */
    @get:Rule(order = Rule.DEFAULT_ORDER - 1) val seeded = SeededGameRule()

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val app: Game2048Application
        get() = ApplicationProvider.getApplicationContext()

    /** F07-AC7 */
    @Test
    fun recreateRendersStaticBoard() {
        val vm = readyViewModel()
        val presented = playPresentedMove(vm)
        val saved = savedGame()

        compose.activityRule.scenario.recreate()

        val after = activityViewModel()
        assertThat(after).isSameInstanceAs(vm)
        val board = after.board.value
        assertAllStatic(board)
        assertThat(board.event).isEqualTo(BoardEvent.Restored)
        assertThat(board.moveId).isEqualTo(presented.moveId)
        assertThat(board.epoch).isEqualTo(presented.epoch)
        assertThat(after.header.value.lastDelta).isEqualTo(0)
        assertThat(savedGame().state.moveLog).isEqualTo(saved.state.moveLog)
        assertThat(savedGame().state.moveCount).isEqualTo(1)
    }

    /** F07-AC8 */
    @Test
    fun pauseResumeDoesNotReplay() {
        val vm = readyViewModel()
        val presented = playPresentedMove(vm)
        val header = vm.header.value
        val scenario = compose.activityRule.scenario

        // From M3, GameScreen's LifecycleResumeEffect makes these two calls.
        scenario.moveToState(Lifecycle.State.CREATED)
        vm.onPaused()
        scenario.moveToState(Lifecycle.State.RESUMED)
        vm.onResumed()

        assertThat(activityViewModel()).isSameInstanceAs(vm)
        assertThat(vm.board.value).isEqualTo(presented)
        assertAllStatic(vm.board.value)
        assertThat(vm.header.value).isEqualTo(header)
        assertThat(vm.header.value.lastDelta).isEqualTo(0)
        assertThat(compose.onAllNodesWithText("+", substring = true).fetchSemanticsNodes())
            .isEmpty()
    }

    /** F07-AC26 */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun recreateAfterMoveRendersFinalFrame() {
        underMotionHarness { rule ->
            MotionFixtures.FILM_LEFT.verify()
            val h = launchFull(rule, GoldenFixtures.FIX_FILM)
            val origin = h.commitMove(Direction.LEFT)
            h.advanceTo(FILM_SETTLED_MS)
            val moveId = h.vm.board.value.moveId
            val log = h.peekGame().state.moveLog

            h.recreate()

            val r0 = checkNotNull(h.r0)
            assertStaticTiles(r0, h.vm.board.value, "R0")
            assertWithMessage("R0: floats").that(r0.floats).isEmpty()
            assertWithMessage("R0: displayed score")
                .that(checkNotNull(r0.header).displayedScore)
                .isEqualTo(h.vm.header.value.score)
            assertWithMessage("R0: running").that(r0.isAnyRunning).isFalse()
            assertThat(h.vm.board.value.moveId).isEqualTo(moveId)
            assertThat(h.peekGame().state.moveLog).isEqualTo(log)
            h.advanceFrames(WATCH_1S_FRAMES)
            assertWithMessage("haptics through 1,000 ms after R0")
                .that(h.hapticsFrom(origin))
                .containsExactly(HapticFeedbackType.SegmentTick to FILM_REVEAL_MS)
        }
    }

    /** F07-AC27 */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun recreateMidMoveSnapsToFinalFrame() {
        underMotionHarness { rule ->
            MotionFixtures.FILM_LEFT.verify()
            val h = launchFull(rule, GoldenFixtures.FIX_FILM)
            val origin = h.commitMove(Direction.LEFT)
            h.advanceTo(MID_SLIDE_MS)

            h.recreate()

            val r0 = checkNotNull(h.r0)
            val first = h.capture(FilmRegion.ROOT, downscale = false)
            assertStaticTiles(r0, h.vm.board.value, "R0")
            assertWithMessage("R0: displayed score")
                .that(checkNotNull(r0.header).displayedScore)
                .isEqualTo(h.vm.header.value.score)
            while (h.now() < origin + WATCH_1S_MS) {
                h.advanceFrames(1)
                assertWithMessage("floats at t = ${h.now() - origin}")
                    .that(h.snapshot().floats)
                    .isEmpty()
            }
            assertWithMessage("running after 1,000 ms").that(h.snapshot().isAnyRunning).isFalse()
            assertWithMessage("haptics through t = 1,000: the SegmentTick due at 80 is dropped")
                .that(h.hapticsFrom(origin))
                .isEmpty()
            assertWithMessage("the first frame after recreation equals the static render")
                .that(first.sameAs(h.staticRender()))
                .isTrue()
        }
    }

    /** F07-AC28 */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun recreateDuringAndAfterWinShowsRestoredCard() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        for ((at, confirms) in listOf(WIN_EARLY_MS to 1, CARD_SHOWN_MS to 2)) {
            underMotionHarness { rule ->
                val h = launchFull(rule, MotionFixtures.FIX_WIN_PRE_4)
                val origin = h.commitMove(Direction.LEFT)
                h.advanceTo(at)
                val before = h.hapticsFrom(origin)
                assertWithMessage("t = $at: Confirms before recreation")
                    .that(before.count { it.first == HapticFeedbackType.Confirm })
                    .isEqualTo(confirms)

                h.recreate()

                val r0 = checkNotNull(h.r0)
                val label = "t = $at, R0"
                val card = checkNotNull(r0.overlay) { "$label: no card" }
                assertThat(card.kind).isEqualTo(ProbeOverlayKind.WIN)
                assertWithMessage("$label: interactive").that(card.interactive).isTrue()
                assertWithMessage("$label: card alpha").that(card.cardAlpha).isEqualTo(1f)
                assertWithMessage("$label: card scale").that(card.cardScale).isEqualTo(1f)
                assertWithMessage("$label: scrim")
                    .that(card.scrimAlpha)
                    .isWithin(Tol.ALPHA)
                    .of(GameMotion.Win.SCRIM_ALPHA)
                assertWithMessage("$label: badge morph").that(card.badgeMorph).isEqualTo(1f)
                assertWithMessage("$label: ring").that(r0.effects.ring).isNull()
                assertWithMessage("$label: confetti").that(r0.effects.confettiAlive).isEqualTo(0)
                assertStaticTiles(r0, h.vm.board.value, label)
                assertWithMessage("$label: the ViewModel's card entry")
                    .that((h.vm.overlay.value as OverlayUi.Win).entry)
                    .isEqualTo(OverlayEntry.Restored)
                h.advanceFrames(WATCH_3S_FRAMES)
                assertWithMessage("t = $at: haptics through 3,000 ms after R0")
                    .that(h.hapticsFrom(origin))
                    .isEqualTo(before)
            }
        }
    }

    /** F07-AC29 */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun recreateDuringAndAfterGameOverShowsRestoredCard() {
        MotionFixtures.OVER_PRE_LEFT.verify()
        for (at in listOf(OVER_RIPPLE_MS, CARD_SHOWN_MS)) {
            underMotionHarness { rule ->
                val h = launchFull(rule, MotionFixtures.FIX_OVER_PRE_4)
                val origin = h.commitMove(Direction.LEFT)
                h.advanceTo(at)

                h.recreate()

                val r0 = checkNotNull(h.r0)
                val label = "t = $at, R0"
                val card = checkNotNull(r0.overlay) { "$label: no card" }
                assertThat(card.kind).isEqualTo(ProbeOverlayKind.OVER)
                assertWithMessage("$label: interactive").that(card.interactive).isTrue()
                assertWithMessage("$label: scrim")
                    .that(card.scrimAlpha)
                    .isWithin(Tol.ALPHA)
                    .of(GameMotion.GameOver.SCRIM_ALPHA)
                assertWithMessage("$label: saturation")
                    .that(checkNotNull(r0.board).saturation)
                    .isWithin(Tol.FRACTION)
                    .of(GameMotion.GameOver.SATURATION_END)
                assertThat(r0.tiles).isNotEmpty()
                for (tile in r0.tiles) {
                    assertWithMessage("$label: tile ${tile.id} scale")
                        .that(tile.scale)
                        .isWithin(Tol.SCALE)
                        .of(GameMotion.GameOver.DEFLATE_SCALE)
                }
                assertWithMessage("$label: running").that(r0.isAnyRunning).isFalse()
                h.advanceFrames(WATCH_3S_FRAMES)
                assertWithMessage("t = $at: haptics through 3,000 ms after R0")
                    .that(h.hapticsFrom(origin))
                    .containsExactly(HapticFeedbackType.Reject to OVER_REJECT_MS)
            }
        }
    }

    /** F07-AC30 */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun recreateAfterInvalidMoveDoesNotNudge() {
        underMotionHarness { rule ->
            val h = launchFull(rule, MotionFixtures.FIX_INVALID_4)
            val origin = h.invalidMove(Direction.LEFT)
            h.advanceTo(NUDGE_SETTLED_MS)
            assertThat(h.haptics.events.map { it.type }).containsExactly(HapticFeedbackType.Reject)

            h.recreate()

            val recreated = h.now()
            assertWithMessage("R0: nudge")
                .that(checkNotNull(checkNotNull(h.r0).board).nudgeDp)
                .isEqualTo(Offset.Zero)
            while (h.now() < recreated + NUDGE_SETTLED_MS) {
                h.advanceFrames(1)
                assertWithMessage("nudge at ${h.now() - recreated} ms after recreation")
                    .that(checkNotNull(h.snapshot().board).nudgeDp)
                    .isEqualTo(Offset.Zero)
            }
            assertWithMessage("haptics from the nudge's origin ($origin)")
                .that(h.haptics.events.map { it.type })
                .containsExactly(HapticFeedbackType.Reject)
        }
    }

    /** F07-AC9 */
    @Test
    fun openSheetSurvivesRecreation() {
        readyViewModel()
        compose.waitForIdle()
        compose.onNodeWithTag(SIZE_CHIP_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SIZE_SHEET_TAG).assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithTag(SIZE_SHEET_TAG).assertExists()
        // The new composition reads the rows again (D-236).
        for (n in BoardSize.MIN_N..BoardSize.MAX_N) {
            compose.onNodeWithTag(sizeRowTag(n)).assertExists()
        }
    }

    /** F18-AC5 */
    @Test
    @Config(qualifiers = "w411dp-h914dp")
    @SeededGame
    fun rotationKeepsStateStatic() {
        val vm = readyViewModel()
        compose.waitForIdle()
        val state = savedGame().state
        assertThat(state).isEqualTo(GoldenFixtures.FIX_MID_4.state)
        compose.onNodeWithTag(layoutTag(LayoutMode.STACKED)).assertExists()

        RuntimeEnvironment.setQualifiers("w914dp-h411dp-land")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        val after = activityViewModel()
        assertThat(after).isSameInstanceAs(vm)
        assertThat(savedGame().state).isEqualTo(state)
        assertAllStatic(after.board.value)
        compose.onNodeWithTag(layoutTag(LayoutMode.THREE_COLUMN)).assertExists()
        compose.onNodeWithTag(layoutTag(LayoutMode.STACKED)).assertDoesNotExist()
    }

    /**
     * Runs [test] under a `MotionRule` of its own (MOTION_SPEC.md §8.1), nested inside this class's
     * `MainActivity` rule, so the harness composes the game with the probe and the haptics fake in
     * a window of its own; each call is one harness launch (the harness composes once per rule).
     */
    private fun underMotionHarness(test: (MotionRule) -> Unit) {
        // The harness's window is the only one: this class's MainActivity is closed first.
        compose.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        val rule = MotionRule()
        val body =
            object : Statement() {
                override fun evaluate() {
                    test(rule)
                }
            }
        val description = Description.createTestDescription(javaClass, "motionHarness")
        rule.apply(body, description).evaluate()
    }

    private fun launchFull(rule: MotionRule, game: SavedGame): MotionHarness =
        MotionHarness.launch(rule, GameFixture(game), MotionPreference.FULL)

    /**
     * Every probe tile of [frame] is [board]'s tile, `Static` at its engine cell, scale and
     * alpha 1.
     */
    private fun assertStaticTiles(frame: MotionSnapshot, board: BoardUi, label: String) {
        assertWithMessage("$label: tiles")
            .that(frame.tiles.map { it.id })
            .containsExactlyElementsIn(board.tiles.map { it.id })
        val cells = board.tiles.associateBy { it.id }
        for (tile in frame.tiles) {
            val cell = checkNotNull(cells[tile.id])
            val name = "$label: tile ${tile.id}"
            assertWithMessage("$name kind").that(tile.kind).isEqualTo(TileKind.Static)
            assertWithMessage("$name outgoing").that(tile.outgoing).isFalse()
            assertWithMessage("$name cell")
                .that(tile.x to tile.y)
                .isEqualTo(cell.col.toFloat() to cell.row.toFloat())
            assertWithMessage("$name scale").that(tile.scale).isWithin(Tol.SCALE).of(1f)
            assertWithMessage("$name alpha").that(tile.alpha).isWithin(Tol.ALPHA).of(1f)
        }
    }

    /** The activity's `GameViewModel`: the instance `by viewModels` created. */
    private fun activityViewModel(): GameViewModel {
        lateinit var vm: GameViewModel
        val factory = app.container.gameViewModelFactory()
        compose.activityRule.scenario.onActivity {
            vm = ViewModelProvider(it, factory)[GameViewModel::class.java]
        }
        return vm
    }

    /**
     * The activity's ViewModel once loaded. The load reads DataStore on real I/O threads and
     * resumes on the main looper, which Robolectric runs only when asked: each poll runs it.
     */
    private fun readyViewModel(): GameViewModel {
        val vm = activityViewModel()
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.ready.value
        }
        return vm
    }

    /** One accepted move from the fresh game, reported as presented (§4.11): a settled board. */
    private fun playPresentedMove(vm: GameViewModel): BoardUi {
        val start = vm.board.value.moveId
        Direction.entries.first { d ->
            vm.onMove(d)
            vm.board.value.moveId != start
        }
        val moveId = vm.board.value.moveId
        vm.onBoardPresented(moveId)
        vm.onHeaderPresented(moveId)
        return vm.board.value
    }

    /** The last game the ViewModel saved; `GameRepository` keeps it in memory, so nothing waits. */
    private fun savedGame(): SavedGame = runBlocking {
        checkNotNull(app.container.gameRepository.peek(BoardSize.DEFAULT))
    }

    private fun assertAllStatic(board: BoardUi) {
        assertThat(board.tiles).isNotEmpty()
        assertThat(board.tiles.map { it.kind }.toSet()).containsExactly(TileKind.Static)
    }

    private companion object {
        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}

/** FIX_FILM LEFT (MOTION_SPEC.md §8.3): settled at 448, the reveal's `SegmentTick` at 80. */
private const val FILM_SETTLED_MS = 448L
private const val FILM_REVEAL_MS = 80L

/** Mid-slide: before the reveal, the spawn, the "+N" and the `SegmentTick` (F07-AC27). */
private const val MID_SLIDE_MS = 48L

/** After the win's first `Confirm` (128), before the second (272) and the card (680). */
private const val WIN_EARLY_MS = 200L

/** Mid-ripple, before the game-over card at 800. */
private const val OVER_RIPPLE_MS = 500L

/** Both cards are shown and interactive. */
private const val CARD_SHOWN_MS = 1_200L

/** The game-over `Reject` at T0 (origin + 300, test frame 304). */
private const val OVER_REJECT_MS = 304L

/** FIX_INVALID_4's nudge has settled. */
private const val NUDGE_SETTLED_MS = 400L

private const val WATCH_1S_MS = 1_000L

/** 1,000 and 3,000 ms of 16 ms test frames. */
private const val WATCH_1S_FRAMES = 63
private const val WATCH_3S_FRAMES = 188

/** The test starts on `FIX_MID_4` with onboarding done ([SeededGameRule]). */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
private annotation class SeededGame

/**
 * For a test marked [SeededGame], writes `FIX_MID_4` and the settled settings (onboarding done, the
 * current version seen) where the production container reads them (D-040), before the activity rule
 * launches `MainActivity`.
 */
class SeededGameRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                if (description.getAnnotation(SeededGame::class.java) != null) writeFiles()
                base.evaluate()
            }
        }

    private fun writeFiles() {
        val app = ApplicationProvider.getApplicationContext<Game2048Application>()
        val dir = File(app.filesDir, "datastore").apply { mkdirs() }
        val game = GoldenFixtures.FIX_MID_4
        File(dir, DataStoreFiles.game(game.state.size))
            .writeText(
                AppJson.encodeToString(
                    GameDocument.serializer(),
                    GameDocumentMapper.toDocument(game),
                ),
            )
        val settings = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE)
        File(dir, DataStoreFiles.SETTINGS)
            .writeText(AppJson.encodeToString(Settings.serializer(), settings))
    }
}
