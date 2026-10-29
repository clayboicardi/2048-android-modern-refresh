package com.clayboicardi.game2048.game

import android.view.MotionEvent
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.App
import com.clayboicardi.game2048.Game2048Application
import com.clayboicardi.game2048.MainActivity
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.fixtureGame
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.header.BEST_STAR_TAG
import com.clayboicardi.game2048.game.header.BEST_TAG
import com.clayboicardi.game2048.game.header.HEADER_TAG
import com.clayboicardi.game2048.game.header.SCORE_TAG
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FakeHapticFeedback
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.DIALOG_CONFIRM_TAG
import com.clayboicardi.game2048.ui.components.DIALOG_DISMISS_TAG
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * The Game screen shell (M3 part 1; ARCHITECTURE.md §4.11, §6.6; DESIGN_SYSTEM.md §6.1, §6.5, §7.6;
 * MOTION_SPEC.md §3.4–§3.7; D-007, D-039, D-047, D-062, D-186). Each test composes `App` over a
 * container of its own (a `FakeClock`, `FixedSeedSource`, fake animator scale and real DataStore
 * files in a `TemporaryFolder`), except the recreation test, which launches `MainActivity`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val haptics = FakeHapticFeedback { compose.mainClock.currentTime }
    private val probe = MotionProbe()

    /** F05-AC10 */
    @Test
    fun undoButtonDisabledAtMoveZero() {
        rig(GoldenFixtures.FIX_FRESH_4).use { rig ->
            show(rig)
            compose.onNodeWithTag(UNDO_TAG).assertIsNotEnabled()

            rig.playOneMove()
            compose.waitForIdle()

            compose.onNodeWithTag(UNDO_TAG).assertIsEnabled()
        }
    }

    /** F05-AC11 */
    @Test
    fun undoUsedSnackbarUntilFirstFinalizedUndoGame() {
        val folder = tmp.newFolder()
        val first = ScreenRig(folder, GoldenFixtures.FIX_FRESH_4, records = Records())
        var shown: ScreenRig? by mutableStateOf(first)
        compose.setContent { shown?.let { key(it) { RigContent(it) } } }
        first.awaitReady(compose)
        compose.waitForIdle()
        // A game that still scores after three undos, so New game finalizes it (F08).
        while (first.saved().state.score == 0L) first.playOneMove()
        repeat(UNDOS_IN_TEST) { first.playOneMove() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        // The first undo of a game while no finished game has used undo: the hint shows.
        compose.onNodeWithTag(UNDO_TAG).performClick()
        compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
        compose.onNodeWithText(UNDO_USED).assertExists()
        val hint = checkNotNull(first.vm.message.value)
        assertThat(hint.kind).isEqualTo(GameMessageKind.UNDO_USED)

        // The second undo dismisses it and asks for nothing.
        compose.onNodeWithTag(UNDO_TAG).performClick()
        compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
        compose.onNodeWithText(UNDO_USED).assertDoesNotExist()
        assertThat(first.vm.message.value).isEqualTo(hint)
        assertThat(first.saved().state.undoCount).isEqualTo(2)

        // Relaunch over the same files: the game has used undo already.
        compose.mainClock.autoAdvance = true
        shown = null
        compose.waitForIdle()
        first.flush()
        first.closeAndWait(compose)
        val second = ScreenRig(folder, game = null, seed = false)
        second.use {
            shown = second
            second.awaitReady(compose)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag(UNDO_TAG).performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            assertThat(second.saved().state.undoCount).isEqualTo(3)
            compose.onNodeWithText(UNDO_USED).assertDoesNotExist()

            // New game finalizes the game that used undo; an undo in the new game says nothing.
            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            compose.onNodeWithTag(DIALOG_CONFIRM_TAG).performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            assertThat(second.saved().state.moveCount).isEqualTo(0)
            assertThat(second.container.recordsRepository.state.value.sizes["4"]?.totalUndos)
                .isEqualTo(3)
            second.playOneMove()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            compose.onNodeWithTag(UNDO_TAG).performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            assertThat(second.saved().state.undoCount).isEqualTo(1)
            compose.onNodeWithText(UNDO_USED).assertDoesNotExist()
            assertThat(second.vm.message.value).isNull()
            shown = null
            compose.mainClock.advanceTimeByFrame()
        }
    }

    /** F05-AC12 */
    @Test
    fun nothingToUndoSnackbarRateLimited() {
        rig(GoldenFixtures.FIX_FRESH_4).use { rig ->
            show(rig)
            compose.mainClock.autoAdvance = false

            // t = 0 of the container clock: shown.
            pressUndoKey()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            compose.onNodeWithText(NOTHING_TO_UNDO).assertExists()
            assertThat(rig.vm.message.value?.kind).isEqualTo(GameMessageKind.NOTHING_TO_UNDO)
            val firstId = checkNotNull(rig.vm.message.value).id
            compose.mainClock.advanceTimeBy(SNACKBAR_GONE_MS)
            compose.onNodeWithText(NOTHING_TO_UNDO).assertDoesNotExist()

            // t = 1,000: within 2 s of the last one, so nothing.
            rig.clock.nowMs = 1_000
            pressUndoKey()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            compose.onNodeWithText(NOTHING_TO_UNDO).assertDoesNotExist()
            assertThat(rig.vm.message.value?.id).isEqualTo(firstId)

            // t = 2,100: shown again.
            rig.clock.nowMs = 2_100
            pressUndoKey()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
            compose.onNodeWithText(NOTHING_TO_UNDO).assertExists()
            assertThat(rig.vm.message.value?.id).isGreaterThan(firstId)
            assertThat(rig.saved().state.moveCount).isEqualTo(0)
        }
    }

    /** F05-AC13 */
    @Test
    fun undoHasNoConfirmation() {
        rig(GoldenFixtures.FIX_FRESH_4).use { rig ->
            show(rig)
            repeat(2) { rig.playOneMove() }
            compose.waitForIdle()

            compose.onNodeWithTag(UNDO_TAG).performClick()

            assertThat(rig.saved().state.moveCount).isEqualTo(1)
            assertThat(rig.vm.board.value.event).isAnyOf(BoardEvent.Undo, BoardEvent.Restored)
            compose.onNode(isDialog()).assertDoesNotExist()
        }
    }

    /** F06-AC1 */
    @Test
    fun newGameAsksWhenInProgress() {
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)
            val before = rig.saved()
            val moveId = rig.vm.board.value.moveId

            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()

            compose.onNode(isDialog()).assertExists()
            compose.onNode(hasText(NEW_GAME_TITLE)).assertExists()
            compose.onNodeWithTag(DIALOG_CONFIRM_TAG).assert(hasText(NEW_GAME))
            compose.onNodeWithTag(DIALOG_DISMISS_TAG).assert(hasText(CANCEL))
            assertThat(rig.saved()).isEqualTo(before)
            assertThat(rig.vm.board.value.moveId).isEqualTo(moveId)
        }
    }

    /** F06-AC2 */
    @Test
    fun cancelNewGameChangesNothing() {
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)
            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            val unchanged = rig.snapshot()

            compose.onNodeWithTag(DIALOG_DISMISS_TAG).performClick()
            compose.waitForIdle()

            compose.onNode(isDialog()).assertDoesNotExist()
            assertThat(rig.snapshot()).isEqualTo(unchanged)
        }
    }

    /** F06-AC3 */
    @Test
    fun dismissNewGameDialogByBackOrOutsideChangesNothing() {
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)
            val unchanged = rig.snapshot()

            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assertExists()
            (ShadowDialog.getLatestDialog() as ComponentDialog)
                .onBackPressedDispatcher
                .onBackPressed()
            compose.waitForIdle()
            compose.onNode(isDialog()).assertDoesNotExist()
            assertThat(rig.snapshot()).isEqualTo(unchanged)

            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assertExists()
            val dialog = ShadowDialog.getLatestDialog()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0L, 0L, action, OUTSIDE_PX, OUTSIDE_PX, 0)
                dialog.onTouchEvent(event)
                event.recycle()
            }
            compose.waitForIdle()
            compose.onNode(isDialog()).assertDoesNotExist()
            assertThat(rig.snapshot()).isEqualTo(unchanged)
        }
    }

    /** F06-AC4 */
    @Test
    fun newGameImmediateOnFreshBoard() {
        rig(GoldenFixtures.FIX_FRESH_4).use { rig ->
            show(rig)
            val epoch = rig.vm.board.value.epoch

            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()

            compose.onNode(isDialog()).assertDoesNotExist()
            assertThat(rig.vm.board.value.epoch).isGreaterThan(epoch)
            assertThat(rig.vm.header.value.event).isEqualTo(BoardEventKind.NewGame)
            assertThat(rig.saved().state.seed).isNotEqualTo(GoldenFixtures.FIX_FRESH_4.state.seed)
            assertThat(rig.saved().gameId).isGreaterThan(GoldenFixtures.FIX_FRESH_4.gameId)
        }
    }

    /** F06-AC10 */
    @Test
    fun newGameDialogSurvivesRecreation() {
        val app = ApplicationProvider.getApplicationContext<Game2048Application>()
        writeAppFiles(app, GoldenFixtures.FIX_MID_4, SETTINGS)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val factory = app.container.gameViewModelFactory()
            lateinit var vm: GameViewModel
            scenario.onActivity { vm = ViewModelProvider(it, factory)[GameViewModel::class.java] }
            compose.waitUntil(LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            compose.waitForIdle()

            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assertExists()

            scenario.recreate()
            compose.waitForIdle()

            compose.onNode(isDialog()).assertExists()
            compose.onNode(hasText(NEW_GAME_TITLE)).assertExists()
            assertThat(vm.uiState.value.inProgress).isTrue()
        }
    }

    /** F09-AC5 */
    @Test
    fun restoredNewBestShowsLabelWithoutReplay() {
        val announced = GoldenFixtures.FIX_MID_4.copy(newBestAnnounced = true)
        rig(announced).use { rig ->
            // A manual clock from the first composition on: a replayed label flip or star moment
            // would otherwise finish inside waitForIdle, before anything is read.
            compose.mainClock.autoAdvance = false
            compose.setContent { RigContent(rig) }
            rig.awaitReady(compose)
            val first = advanceToLoadedFrame()

            val header = checkNotNull(first.header) { "no header on the first loaded frame" }
            assertWithMessage("first frame: the loaded score")
                .that(header.displayedScore)
                .isEqualTo(announced.state.score)
            assertWithMessage("first frame: label")
                .that(header.bestChip.labelKey)
                .isEqualTo("new_best_label")
            assertWithMessage("first frame: container")
                .that(header.bestChip.containerFraction)
                .isEqualTo(1f)
            assertWithMessage("first frame: star").that(header.bestChip.starScale).isNull()
            assertWithMessage("first frame: sparkles")
                .that(header.bestChip.sparklesAlive)
                .isEqualTo(0)
            assertWithMessage("first frame: running").that(first.isAnyRunning).isFalse()
            compose.onNodeWithText(NEW_BEST_UPPER, useUnmergedTree = true).assertExists()
            compose.onNodeWithTag(BEST_STAR_TAG).assertDoesNotExist()
            assertWithMessage("first frame: haptics").that(haptics.events).isEmpty()

            // Nor does anything start later: every frame keeps the static NEW BEST chip.
            repeat(FRAMES_TO_SETTLE) { frame ->
                compose.mainClock.advanceTimeByFrame()
                val now = probe.snapshot()
                val chip = checkNotNull(now.header).bestChip
                assertWithMessage("frame $frame: label")
                    .that(chip.labelKey)
                    .isEqualTo("new_best_label")
                assertWithMessage("frame $frame: container")
                    .that(chip.containerFraction)
                    .isEqualTo(1f)
                assertWithMessage("frame $frame: star").that(chip.starScale).isNull()
                assertWithMessage("frame $frame: running").that(now.isAnyRunning).isFalse()
            }
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            compose.onNodeWithTag(BEST_STAR_TAG).assertDoesNotExist()
            assertThat(haptics.events).isEmpty()
        }
    }

    /** F09-AC6 */
    @Test
    fun newBestReducedMotionSwapsLabelOnly() {
        // FIX_FILM's LEFT scores 4 above a best of 2: the first new best of the game.
        val game = GoldenFixtures.FIX_FILM.copy(bestAtStart = 2)
        rig(game, settings = SETTINGS.copy(motion = MotionPreference.REDUCED)).use { rig ->
            show(rig)
            compose.mainClock.autoAdvance = false
            assertThat(checkNotNull(probe.snapshot().header).bestChip.labelKey)
                .isEqualTo("best_label")

            rig.vm.onMove(Direction.LEFT)
            assertThat(rig.vm.header.value.newBestMoveId).isEqualTo(rig.vm.board.value.moveId)
            repeat(FRAMES_TO_SETTLE) {
                compose.mainClock.advanceTimeByFrame()
                val chip = checkNotNull(probe.snapshot().header).bestChip
                assertWithMessage("star at frame $it").that(chip.starScale).isNull()
                assertWithMessage("sparkles at frame $it").that(chip.sparklesAlive).isEqualTo(0)
                compose.onNodeWithTag(BEST_STAR_TAG).assertDoesNotExist()
            }

            val chip = checkNotNull(probe.snapshot().header).bestChip
            assertThat(chip.labelKey).isEqualTo("new_best_label")
            assertThat(chip.containerFraction).isEqualTo(1f)
            compose.onNodeWithText(NEW_BEST_UPPER, useUnmergedTree = true).assertExists()
        }
    }

    /** F13-AC9 */
    @Test
    fun newMoveSnapsInFlightSlides() {
        rig(GoldenFixtures.FIX_FRESH_4).use { rig ->
            show(rig)
            compose.mainClock.autoAdvance = false
            probe.originMs = null

            rig.vm.onMove(Direction.LEFT)
            val origin = advanceToOrigin()
            val first = rig.vm.board.value
            val targets = first.tiles.filter { it.kind != TileKind.Ghost }.associateBy { it.id }
            advanceTo(origin + SECOND_MOVE_AT_MS)
            // Move 1 is still sliding: the 4 on row 2 travels three cells.
            val sliding =
                probe
                    .snapshot()
                    .tiles
                    .filter { !it.outgoing && it.id in targets }
                    .filter {
                        abs(it.x - targets.getValue(it.id).col) > POSITION_EPSILON
                    }
            assertWithMessage("a tile still in flight at t = 48").that(sliding).isNotEmpty()

            rig.vm.onMove(Direction.RIGHT)
            assertThat(rig.vm.board.value.moveId).isGreaterThan(first.moveId)
            compose.mainClock.advanceTimeByFrame()

            val drawn = probe.snapshot().tiles.filter { !it.outgoing && it.id in targets }
            assertThat(drawn).isNotEmpty()
            for (tile in drawn) {
                val target = targets.getValue(tile.id)
                assertWithMessage("tile ${tile.id} x on move 2's F0")
                    .that(tile.x)
                    .isWithin(POSITION_EPSILON)
                    .of(target.col.toFloat())
                assertWithMessage("tile ${tile.id} y on move 2's F0")
                    .that(tile.y)
                    .isWithin(POSITION_EPSILON)
                    .of(target.row.toFloat())
            }
        }
    }

    /** F13-AC10 */
    @Test
    fun atMostThreeScoreFloats() {
        rig(ALL_TWOS_4).use { rig ->
            show(rig)
            assertThat(rig.saved().state).isEqualTo(ALL_TWOS_4.state)
            // The engine is deterministic: find five moves in a row that each merge.
            val moves = checkNotNull(mergingMoves(rig.saved().state, MERGING_MOVES))
            compose.mainClock.autoAdvance = false
            var most = 0
            moves.forEachIndexed { move, direction ->
                rig.vm.onMove(direction)
                repeat(FRAMES_PER_48_MS) {
                    compose.mainClock.advanceTimeByFrame()
                    most = maxOf(most, countFloats("move ${move + 1}"))
                }
            }
            repeat(FRAMES_TO_SETTLE) {
                compose.mainClock.advanceTimeByFrame()
                most = maxOf(most, countFloats("after the moves"))
            }
            // The floats overlap: the cap, not their number, is what the test proves.
            assertThat(most).isIn(2..GAME_MAX_FLOATS)
            assertThat(countFloats("settled")).isEqualTo(0)
        }
    }

    /** F13-AC13 */
    @Test
    fun invalidMoveFeedbackPerMotionMode() {
        val rigs =
            MotionPreference.entries.associateWith { motion ->
                ScreenRig(tmp.newFolder(), GoldenFixtures.FIX_FILM, SETTINGS.copy(motion = motion))
            }
        var shown by mutableStateOf(rigs.getValue(MotionPreference.FULL))
        compose.setContent { key(shown) { RigContent(shown) } }
        try {
            for ((motion, rig) in rigs) {
                shown = rig
                rig.awaitReady(compose)
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                probe.nudgeOriginMs = null
                // UP is invalid on FIX_FILM: every tile is already on the top row.
                compose.onNodeWithTag(BOARD_TAG).performTouchInput {
                    down(center)
                    repeat(SWIPE_STEPS) { moveBy(Offset(0f, -SWIPE_STEP_DP.dp.toPx())) }
                    up()
                }
                assertWithMessage("$motion: the swipe is an invalid move")
                    .that(rig.vm.nudge.value?.direction)
                    .isEqualTo(Direction.UP)
                assertInvalidMoveFeedback(motion)
                assertWithMessage("$motion: the game is unchanged")
                    .that(rig.saved().state)
                    .isEqualTo(GoldenFixtures.FIX_FILM.state)
            }
        } finally {
            rigs.values.forEach { it.close() }
        }
    }

    /**
     * D-104, DESIGN_SYSTEM.md §3.7, §5.7: under `fa` the chrome mirrors (the Score chip sits to the
     * right of the Best chip), and the Full-motion odometer still draws each value's digits left to
     * right, most significant first: one digit node per place, read by its left bound.
     */
    @Test
    @Config(qualifiers = "fa")
    fun scoreDigitsStayLeftToRightInRtl() {
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)
            val header = rig.vm.header.value
            assertWithMessage("RTL chrome: the Score chip right of the Best chip")
                .that(chipLeft(SCORE_TAG))
                .isGreaterThan(chipLeft(BEST_TAG))
            for ((tag, value) in listOf(SCORE_TAG to header.score, BEST_TAG to header.best)) {
                val digits = drawnDigits(tag)
                assertWithMessage("$tag: one node per digit (the Full odometer)")
                    .that(digits.map { it.first })
                    .hasSize(value.toString().length)
                assertWithMessage("$tag: digits in their left-to-right order")
                    .that(digits.joinToString("") { it.first })
                    .isEqualTo(value.toString())
            }
        }
    }

    /**
     * D-104, DESIGN_SYSTEM.md §3.7, §5.7: under `fa` every game numeral of the header (the title,
     * each Score and Best digit, the "+N" float) lays out as an LTR paragraph, and the float's "+"
     * is drawn left of its first digit. In an RTL paragraph the leading "+", a neutral, is drawn
     * after the digits ("4+").
     */
    @Test
    @Config(qualifiers = "fa")
    fun gameNumeralsLayOutLeftToRightInRtl() {
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)
            val direction = checkNotNull(mergingMoves(rig.saved().state, 1)).single()
            compose.mainClock.autoAdvance = false
            rig.vm.onMove(direction)
            var frames = 0
            while (plusNNodes().isEmpty() && frames++ < FRAMES_TO_SETTLE) {
                compose.mainClock.advanceTimeByFrame()
            }
            val float = drawnLayout(plusNNodes().single())
            val text = float.layoutInput.text.text
            assertWithMessage("the float's text").that(text).matches(PLUS_N_TEXT.pattern)
            assertWithMessage("RTL chrome")
                .that(float.layoutInput.layoutDirection)
                .isEqualTo(LayoutDirection.Rtl)
            assertWithMessage("\"$text\": the \"+\" left of the first digit")
                .that(float.getBoundingBox(0).left)
                .isLessThan(float.getBoundingBox(1).left)
            assertWithMessage("\"$text\": paragraph direction")
                .that(float.getParagraphDirection(0))
                .isEqualTo(ResolvedTextDirection.Ltr)
            val numerals =
                compose
                    .onAllNodes(
                        hasAnyAncestor(hasTestTag(HEADER_TAG)) and NUMERAL,
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
            val texts = numerals.map { it.config[SemanticsProperties.Text].joinToString("") }
            assertWithMessage("the header's numerals").that(texts).contains(TITLE)
            for (node in numerals) {
                val layout = drawnLayout(node)
                assertWithMessage("\"${layout.layoutInput.text}\": paragraph direction")
                    .that(layout.getParagraphDirection(0))
                    .isEqualTo(ResolvedTextDirection.Ltr)
            }
        }
    }

    /**
     * F13-AC13's frames after an invalid move under [motion]: from the nudge's first frame (or the
     * swipe, under Off), the board offset and outline pulse at 48, 160 and 400 ms.
     */
    private fun assertInvalidMoveFeedback(motion: MotionPreference) {
        val start = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val origin = probe.nudgeOriginMs ?: start
        val frames =
            listOf(EARLY_MS, PULSE_MS, NUDGE_DONE_MS).map { t ->
                advanceTo(origin + t)
                checkNotNull(probe.snapshot().board)
            }
        val (early, pulse, done) = frames
        when (motion) {
            MotionPreference.FULL -> {
                assertWithMessage("Full: offset at 48 ms")
                    .that(early.nudgeDp.getDistance())
                    .isGreaterThan(0f)
                assertWithMessage("Full: offset at 400 ms")
                    .that(done.nudgeDp.getDistance())
                    .isAtMost(NUDGE_REST_DP)
            }
            MotionPreference.REDUCED -> {
                assertWithMessage("Reduced: pulse at 160 ms")
                    .that(pulse.outlinePulseAlpha)
                    .isGreaterThan(0f)
                for (b in frames) {
                    assertWithMessage("Reduced: no offset").that(b.nudgeDp).isEqualTo(Offset.Zero)
                }
            }
            MotionPreference.OFF -> {
                for (b in frames) {
                    assertWithMessage("Off: no offset").that(b.nudgeDp).isEqualTo(Offset.Zero)
                    assertWithMessage("Off: no pulse").that(b.outlinePulseAlpha).isEqualTo(0f)
                }
            }
        }
    }

    /** A rig over [game] in a new folder, with [settings] and, when given, [records]. */
    private fun rig(
        game: SavedGame,
        settings: Settings = SETTINGS.copy(lastBoardSize = game.state.size.n),
        records: Records? = null,
    ) = ScreenRig(tmp.newFolder(), game, settings, records)

    /** Composes `App` over [rig] with the fake haptics and the probe, and waits for the load. */
    private fun show(rig: ScreenRig) {
        compose.setContent { RigContent(rig) }
        rig.awaitReady(compose)
        compose.waitForIdle()
    }

    @Composable
    private fun RigContent(rig: ScreenRig) {
        CompositionLocalProvider(
            LocalHapticFeedback provides haptics,
            LocalMotionProbe provides probe,
        ) {
            App(
                gameViewModel = rig.vm,
                settings = rig.container.settingsRepository.state,
                animatorScale = rig.container.animatorScale,
                clock = rig.container.clock,
            )
        }
    }

    private fun pressUndoKey() {
        compose.onNodeWithTag(BOARD_TAG).performKeyInput { pressKey(Key.U) }
    }

    /**
     * With `autoAdvance` false and the game loaded, advances one frame at a time until `BoardView`
     * has composed the loaded board; returns the probe on that frame (the first loaded frame).
     */
    private fun advanceToLoadedFrame(): MotionSnapshot {
        repeat(MAX_LOAD_FRAMES) {
            if (probe.boardLoaded) return probe.snapshot()
            compose.mainClock.advanceTimeByFrame()
        }
        check(probe.boardLoaded) { "no loaded board within $MAX_LOAD_FRAMES frames" }
        return probe.snapshot()
    }

    /** Advances frame by frame until `BoardView` has published an origin; returns it. */
    private fun advanceToOrigin(): Long {
        repeat(FRAMES_TO_SETTLE) {
            probe.originMs?.let { origin ->
                return origin
            }
            compose.mainClock.advanceTimeByFrame()
        }
        return checkNotNull(probe.originMs) { "no origin published" }
    }

    /** Advances whole frames until the clock is at or after [timeMs]. */
    private fun advanceTo(timeMs: Long) {
        while (compose.mainClock.currentTime < timeMs) compose.mainClock.advanceTimeByFrame()
    }

    /** [count] directions from [state] whose moves each score, depth first; `null` if none. */
    private fun mergingMoves(state: GameState, count: Int): List<Direction>? =
        if (count == 0) {
            emptyList()
        } else {
            MERGE_ORDER.firstNotNullOfOrNull { direction ->
                val outcome = Game.move(state, direction)
                if (outcome is MoveOutcome.Moved && outcome.scoreDelta > 0) {
                    mergingMoves(outcome.state, count - 1)?.let { rest -> listOf(direction) + rest }
                } else {
                    null
                }
            }
        }

    /** The "+N" text nodes now, checked against the cap (D-062). */
    private fun countFloats(at: String): Int {
        val nodes =
            compose
                .onAllNodesWithText("+", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size
        val probed = probe.snapshot().floats.size
        assertWithMessage("+N nodes $at").that(nodes).isAtMost(GAME_MAX_FLOATS)
        assertWithMessage("probed floats $at").that(probed).isAtMost(GAME_MAX_FLOATS)
        return nodes
    }

    /** The "+N" text nodes of the Score chip now. */
    private fun plusNNodes(): List<SemanticsNode> =
        compose
            .onAllNodes(hasAnyAncestor(hasTestTag(SCORE_TAG)) and PLUS_N, useUnmergedTree = true)
            .fetchSemanticsNodes()

    /**
     * [node]'s text laid out as its one-line `BasicText` draws it: the node's own layout input
     * (text, style, layout direction, density, fonts) measured at its intrinsic width. The
     * `GetTextLayoutResult` action itself lays out at the node's last constraints, unbounded for a
     * "+N" float, where an RTL line would start near `Int.MAX_VALUE` px.
     */
    private fun drawnLayout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = checkNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)
        check(action(results)) { "no text layout for ${node.config[SemanticsProperties.Text]}" }
        val input = results.single().layoutInput
        val measurer =
            TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, 0)
        return measurer.measure(input.text, input.style, softWrap = false, maxLines = 1)
    }

    /** The left bound in px of the node tagged [tag]. */
    private fun chipLeft(tag: String): Float =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left

    /**
     * The digit-only text nodes inside the chip tagged [chipTag], sorted by their left bound: each
     * node's text with that bound in px.
     */
    private fun drawnDigits(chipTag: String): List<Pair<String, Float>> =
        compose
            .onAllNodes(hasAnyAncestor(hasTestTag(chipTag)) and DIGITS, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node ->
                node.config[SemanticsProperties.Text].joinToString("") to node.boundsInRoot.left
            }
            .sortedBy { it.second }

    private companion object {
        /** DESIGN_SYSTEM.md §10.2: onboarding done, the current version seen. */
        val SETTINGS = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE)

        /** 4×4, all sixteen cells 2: every move of the first ones merges. 64 moves, score 0. */
        val ALL_TWOS_4: SavedGame =
            fixtureGame(
                n = 4,
                cells = List(BoardSize.DEFAULT.cellCount) { 1 },
                score = 0,
                best = 0,
                moves = 64,
                undos = 0,
                elapsedMs = 0,
                status = GameStatus.PLAYING,
            )

        val MERGE_ORDER = listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN)

        /** A text node whose text is one or more digits (an odometer place or a plain value). */
        val DIGITS =
            SemanticsMatcher("text is digits") { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("")
                !text.isNullOrEmpty() && text.all(Char::isDigit)
            }

        /** A game numeral's text: digits, or "+" and digits. */
        val NUMERAL_TEXT = Regex("\\+?[0-9]+")

        /** A "+N" float's text. */
        val PLUS_N_TEXT = Regex("\\+[0-9]+")

        /** A text node whose text is a game numeral: digits, or "+" and digits (a "+N" float). */
        val NUMERAL =
            SemanticsMatcher("text is a game numeral") { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("")
                text != null && text.matches(NUMERAL_TEXT)
            }

        /** A text node whose text is "+" and digits (a "+N" float). */
        val PLUS_N =
            SemanticsMatcher("text is +N") { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("")
                text != null && text.matches(PLUS_N_TEXT)
            }

        /** The title, a game numeral (DESIGN_SYSTEM.md §6.1). */
        const val TITLE = "2048"

        const val BOARD_TAG = "game:board"
        const val UNDO_USED = "Undo used. This game no longer counts for Best without undo."
        const val NOTHING_TO_UNDO = "Nothing to undo"
        const val NEW_GAME_TITLE = "Start a new game?"
        const val NEW_GAME = "New game"
        const val CANCEL = "Cancel"
        const val NEW_BEST_UPPER = "NEW BEST"

        /** Long enough for a snackbar to fade in, well inside its 4 s `Short` duration. */
        const val SNACKBAR_SETTLE_MS = 500L

        /** Past `SnackbarDuration.Short` (4 s) and its exit fade. */
        const val SNACKBAR_GONE_MS = 4_500L

        const val SETTLE_MS = 2_000L
        const val FRAMES_TO_SETTLE = 120
        const val MAX_LOAD_FRAMES = 20
        const val FRAMES_PER_48_MS = 3
        const val MERGING_MOVES = 5
        const val UNDOS_IN_TEST = 3
        const val GAME_MAX_FLOATS = 3
        const val SECOND_MOVE_AT_MS = 48L
        const val POSITION_EPSILON = 0.001f

        /** A press outside the dialog's content, in its window's px (above and left of it). */
        const val OUTSIDE_PX = -10f

        const val SWIPE_STEPS = 12
        const val SWIPE_STEP_DP = 5f
        const val EARLY_MS = 48L
        const val PULSE_MS = 160L
        const val NUDGE_DONE_MS = 400L
        const val NUDGE_REST_DP = 0.1f

        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}

/**
 * The Game screen over a container of its own (QUALITY.md §1.6): [game], [settings] and [records]
 * are written into [folder] before the `GameViewModel` (in a `ViewModelStore` of its own) loads
 * them, with a `FakeClock`, `FixedSeedSource` and a fake animator scale. With [seed] false the
 * folder's files are used as they are (a relaunch).
 */
private class ScreenRig(
    folder: File,
    game: SavedGame?,
    settings: Settings = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE),
    records: Records? = null,
    seed: Boolean = true,
) : AutoCloseable {
    val clock = FakeClock()
    val container =
        AppContainer(
            files = { name -> File(folder, name) },
            versionCode = TEST_VERSION_CODE,
            clock = clock,
            seedSource = FixedSeedSource(RIG_SEED),
            animatorScale = FakeAnimatorScaleSource(),
        )
    private val size = game?.state?.size ?: BoardSize.of(settings.lastBoardSize)
    private val store = ViewModelStore()
    val vm: GameViewModel

    init {
        if (seed) {
            runBlocking {
                container.seedSettings(settings)
                game?.let { container.seedGame(it) }
                records?.let { container.seedRecords(it) }
            }
        }
        vm = ViewModelProvider(store, container.gameViewModelFactory())[GameViewModel::class.java]
    }

    /** Waits for the load (the stores read on real I/O threads and resume on the main looper). */
    fun awaitReady(compose: ComposeContentTestRule) {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.ready.value
        }
    }

    /** The latest game the ViewModel saved (submitted or on disk). */
    fun saved(): SavedGame = runBlocking { checkNotNull(container.gameRepository.peek(size)) }

    /** One accepted move: the first direction that moves the board. */
    fun playOneMove() {
        val before = vm.board.value.moveId
        val moved =
            Direction.entries.any { d ->
                vm.onMove(d)
                vm.board.value.moveId != before
            }
        check(moved) { "No direction moved the board" }
    }

    /** What a cancelled dialog must leave as it was: the game, the saves and the records. */
    fun snapshot(): List<Any> =
        listOf(
            saved(),
            container.gameRepository.submissions(size),
            container.recordsRepository.state.value,
            vm.uiState.value,
            vm.board.value.moveId,
        )

    /** Waits until every submitted write has been attempted. */
    fun flush() = runBlocking {
        container.gameRepository.awaitIdle()
        container.recordsRepository.awaitIdle()
    }

    /**
     * Closes the container and waits until its stores have released their files, so a new container
     * may open them (one active DataStore per file).
     *
     * Each DataStore releases its file in a completion handler that it registered on the
     * container's job when it was built (its write actor's). The job runs its completion handlers
     * on the thread that completes it, one after another in the order they were registered, and
     * only after `isCompleted` is already true; `join()` on a completed job returns at once
     * (kotlinx.coroutines `JobSupport`). So neither `isCompleted` nor `join()` proves the files
     * released. The rig registers one more handler once the ViewModel is cleared, after every store
     * has been built, and waits for it: it runs after each store's release.
     */
    fun closeAndWait(compose: ComposeContentTestRule) {
        store.clear()
        val job = checkNotNull(container.applicationScope.coroutineContext[Job])
        val released = CountDownLatch(1)
        job.invokeOnCompletion { released.countDown() }
        container.applicationScope.cancel()
        // The repositories resume on the main looper, which must run for the job to complete.
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            released.count == 0L
        }
    }

    override fun close() {
        store.clear()
        container.applicationScope.cancel()
    }

    private companion object {
        const val RIG_SEED = 0x2048L
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}

/** Writes [game] and [settings] where the production container reads them (D-040). */
private fun writeAppFiles(app: Game2048Application, game: SavedGame, settings: Settings) {
    val dir = File(app.filesDir, "datastore").apply { mkdirs() }
    val document = GameDocumentMapper.toDocument(game)
    File(dir, DataStoreFiles.game(game.state.size))
        .writeText(AppJson.encodeToString(GameDocument.serializer(), document))
    File(dir, DataStoreFiles.SETTINGS)
        .writeText(AppJson.encodeToString(Settings.serializer(), settings))
}
