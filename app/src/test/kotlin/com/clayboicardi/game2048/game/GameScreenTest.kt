package com.clayboicardi.game2048.game

import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.AboutKey
import com.clayboicardi.game2048.Game2048Application
import com.clayboicardi.game2048.GameKey
import com.clayboicardi.game2048.HowToPlayKey
import com.clayboicardi.game2048.MainActivity
import com.clayboicardi.game2048.NAVIGATE_UP_TAG
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.StatsKey
import com.clayboicardi.game2048.about.WHATS_NEW_GOT_IT_TAG
import com.clayboicardi.game2048.about.WHATS_NEW_SHEET_TAG
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
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
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.fixtures.fixtureGame
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.game.board.GameLayoutSpec
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.header.BEST_STAR_TAG
import com.clayboicardi.game2048.game.header.BEST_TAG
import com.clayboicardi.game2048.game.header.HEADER_TAG
import com.clayboicardi.game2048.game.header.SCORE_TAG
import com.clayboicardi.game2048.game.header.SIZE_CHIP_TAG
import com.clayboicardi.game2048.game.header.TITLE_TAG
import com.clayboicardi.game2048.game.input.padButtonTag
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.game.overlay.CARD_NEW_GAME_TAG
import com.clayboicardi.game2048.game.overlay.CARD_SHARE_TAG
import com.clayboicardi.game2048.game.overlay.CARD_UNDO_TAG
import com.clayboicardi.game2048.game.overlay.GAME_OVER_CARD_TAG
import com.clayboicardi.game2048.game.overlay.HINT_TAG
import com.clayboicardi.game2048.game.overlay.KEEP_GOING_TAG
import com.clayboicardi.game2048.game.overlay.SCRIM_TAG
import com.clayboicardi.game2048.game.overlay.SIZE_CHECK_TAG
import com.clayboicardi.game2048.game.overlay.SIZE_SHEET_TAG
import com.clayboicardi.game2048.game.overlay.WIN_CARD_TAG
import com.clayboicardi.game2048.game.overlay.sizeRowTag
import com.clayboicardi.game2048.menuItemTag
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.stats.STATS_RESET_TAG
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FakeHapticFeedback
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.DIALOG_CONFIRM_TAG
import com.clayboicardi.game2048.ui.components.DIALOG_DISMISS_TAG
import com.clayboicardi.game2048.ui.components.HINT_GOT_IT_TAG
import com.clayboicardi.game2048.ui.components.LayoutMode
import com.clayboicardi.game2048.ui.components.SNACKBAR_TAG
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
@Suppress("LargeClass") // FEATURES.md §8 puts every GameScreen criterion in this one class
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

    /** F06-AC5 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun newGameFromCardsSkipsConfirmation() {
        // `WON` and `OVER` are not "in progress" (D-007, D-199): New game on the card and on the
        // controls bar starts a fresh game at once. One rig per case, composed in turn.
        val cases =
            listOf(GoldenFixtures.FIX_WON_4, GoldenFixtures.FIX_OVER_4).flatMap { game ->
                listOf(CARD_NEW_GAME_TAG, NEW_GAME_TAG).map { tag ->
                    Triple(ScreenRig(tmp.newFolder(), game, SETTINGS), game, tag)
                }
            }
        var shown by mutableStateOf(cases.first().first)
        compose.setContent { key(shown) { RigContent(shown) } }
        try {
            for ((rig, game, tag) in cases) {
                shown = rig
                rig.awaitReady(compose)
                compose.waitForIdle()
                val won = game.state.status == GameStatus.WON
                val card = if (won) WIN_CARD_TAG else GAME_OVER_CARD_TAG
                compose.onNodeWithTag(card).assertExists()

                compose.onNodeWithTag(tag).performClick()
                compose.waitForIdle()

                val label = "${game.state.status}, $tag"
                assertWithMessage("$label: dialog")
                    .that(compose.onAllNodes(isDialog()).fetchSemanticsNodes())
                    .isEmpty()
                val fresh = rig.saved()
                assertWithMessage("$label: moves").that(fresh.state.moveCount).isEqualTo(0)
                assertWithMessage("$label: status")
                    .that(fresh.state.status)
                    .isEqualTo(GameStatus.PLAYING)
                assertWithMessage("$label: game id").that(fresh.gameId).isGreaterThan(game.gameId)
                assertWithMessage("$label: card")
                    .that(compose.onAllNodesWithTag(card).fetchSemanticsNodes())
                    .isEmpty()
            }
        } finally {
            cases.forEach { it.first.close() }
        }
    }

    /** F06-AC6 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun cancelThenWinStillShowsWinCard() {
        // B5: the legacy game suppressed the win card after a cancelled restart.
        rig(MotionFixtures.FIX_WIN_PRE_4, SETTINGS.copy(motion = MotionPreference.OFF)).use { rig ->
            show(rig)
            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assertExists()
            compose.onNodeWithTag(DIALOG_DISMISS_TAG).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assertDoesNotExist()

            rig.vm.onMove(Direction.LEFT)
            compose.waitForIdle()

            assertThat(rig.vm.uiState.value.status).isEqualTo(GameStatus.WON)
            compose.onNodeWithTag(WIN_CARD_TAG).assertExists()
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
    @Config(qualifiers = "fa-w411dp-h914dp-xxhdpi") // the FULL header, which shows the title
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

    /** F17-AC23 */
    @Test
    @Config(qualifiers = "w1000dp-h1000dp-xhdpi")
    fun longScoresFitHeaderWithoutTruncation() {
        val game =
            GoldenFixtures.FIX_DENSE_8.let {
                it.copy(state = it.state.copy(score = LONG_SCORE), bestAtStart = LONG_BEST)
            }
        val records = Records(sizes = mapOf("8" to SizeRecord(bestScore = LONG_BEST)))
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, lastBoardSize = 8)
        val cases = LONG_SCORE_WINDOWS.flatMap { window -> FONT_SCALES.map { window to it } }
        val rigs = cases.associateWith { ScreenRig(tmp.newFolder(), game, settings, records) }
        var shown: Pair<DpSize, Float>? by mutableStateOf(null)
        compose.setContent {
            val density = LocalDensity.current.density
            shown?.let { case ->
                key(case) {
                    // The window as the content's constraints, at the case's font scale.
                    CompositionLocalProvider(LocalDensity provides Density(density, case.second)) {
                        Box(Modifier.requiredSize(case.first).testTag(WINDOW_TAG)) {
                            RigContent(rigs.getValue(case))
                        }
                    }
                }
            }
        }
        try {
            for ((case, rig) in rigs) {
                // The previous case leaves before the next one composes.
                shown = null
                compose.waitForIdle()
                shown = case
                rig.awaitReady(compose)
                compose.waitForIdle()
                assertLongScoresFit(case.first, case.second)
            }
        } finally {
            rigs.values.forEach { it.close() }
        }
    }

    /** F18-AC16 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun controlsLabelsFitOnOneLine() {
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, lastBoardSize = 4)
        val rigs = LABEL_WINDOWS.associateWith {
            ScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4, settings)
        }
        var shown: DpSize? by mutableStateOf(null)
        compose.setContent {
            shown?.let { window ->
                key(window) {
                    Box(Modifier.requiredSize(window)) { RigContent(rigs.getValue(window)) }
                }
            }
        }
        try {
            for ((window, rig) in rigs) {
                shown = null
                compose.waitForIdle()
                shown = window
                rig.awaitReady(compose)
                compose.waitForIdle()
                val label = "${window.width.value.toInt()}×${window.height.value.toInt()}"
                for ((tag, text) in listOf(UNDO_TAG to UNDO_LABEL, NEW_GAME_TAG to NEW_GAME)) {
                    val node =
                        compose
                            .onNode(
                                hasAnyAncestor(hasTestTag(tag)) and hasText(text),
                                useUnmergedTree = true,
                            )
                            .fetchSemanticsNode()
                    val layout = checkNotNull(textLayout(node)) { "$label: no layout of $text" }
                    assertWithMessage("$label: \"$text\" lines").that(layout.lineCount).isEqualTo(1)
                }
                compose
                    .onNode(
                        hasTestTag(MORE_TAG) and hasAnyAncestor(hasTestTag(HEADER_TAG)),
                        useUnmergedTree = true,
                    )
                    .assertExists()
                compose
                    .onAllNodes(
                        hasTestTag(MORE_TAG) and hasAnyAncestor(hasTestTag(CONTROLS_TAG)),
                        useUnmergedTree = true,
                    )
                    .assertCountEquals(0)
            }
        } finally {
            rigs.values.forEach { it.close() }
        }
    }

    /** F05-AC17 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun snackbarNeverCoversControls() {
        // FIX_MID_4's board as a 416-move game without undos (a multiple of 32, so a move played
        // from it undoes back to it, D-214), and no finished game that used undo: its first undo
        // shows undo_hint_no_undo_best (F05-AC11).
        val mid = GoldenFixtures.FIX_MID_4.state
        val game =
            fixtureGame(
                n = 4,
                cells = List(mid.board.cellCount) { mid.board[it] },
                score = mid.score,
                best = GoldenFixtures.FIX_MID_4.bestAtStart,
                moves = UNDOABLE_MOVES,
                undos = 0,
                elapsedMs = 0,
                status = GameStatus.PLAYING,
            )
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, lastBoardSize = 4)
        val rigs = SNACKBAR_FONT_SCALES.associateWith { ScreenRig(tmp.newFolder(), game, settings) }
        var shown: Float? by mutableStateOf(null)
        compose.setContent {
            val density = LocalDensity.current.density
            shown?.let { scale ->
                key(scale) {
                    // The window as the content's constraints, at the case's font scale.
                    CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                        Box(Modifier.requiredSize(SNACKBAR_WINDOW)) {
                            RigContent(rigs.getValue(scale))
                        }
                    }
                }
            }
        }
        try {
            for ((scale, rig) in rigs) {
                compose.mainClock.autoAdvance = true
                shown = null
                compose.waitForIdle()
                shown = scale
                rig.awaitReady(compose)
                compose.waitForIdle()
                rig.playOneMove()
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false

                compose.onNodeWithTag(UNDO_TAG).performClick()
                compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
                compose.onNodeWithText(UNDO_USED).assertExists()
                assertSnackbarPlaced("fontScale $scale")

                // The next accepted move dismisses it.
                rig.playOneMove()
                compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)
                compose.onNodeWithText(UNDO_USED).assertDoesNotExist()
            }
        } finally {
            compose.mainClock.autoAdvance = true
            rigs.values.forEach { it.close() }
        }
    }

    /** F17-AC15 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 2.0f)
    fun chromeTextNotTruncatedAtFontScale2() {
        val motions = listOf(MotionPreference.FULL, MotionPreference.OFF)
        val rigs = motions.associateWith {
            ScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4, SETTINGS.copy(motion = it))
        }
        var shown: MotionPreference? by mutableStateOf(null)
        compose.setContent {
            shown?.let { motion ->
                key(motion) {
                    Box(Modifier.fillMaxSize().testTag(WINDOW_TAG)) {
                        RigContent(rigs.getValue(motion))
                    }
                }
            }
        }
        try {
            for ((motion, rig) in rigs) {
                shown = null
                compose.waitForIdle()
                shown = motion
                rig.awaitReady(compose)
                compose.waitForIdle()
                compose.onNodeWithTag(HEADER_TAG).assertExists()
                compose.onNodeWithTag(CONTROLS_TAG).assertExists()
                assertNoTextOverflow("Motion $motion at fontScale 2.0")
            }
        } finally {
            rigs.values.forEach { it.close() }
        }
    }

    /** F17-AC21 */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi", fontScale = 2.0f)
    fun scoreDigitsShareOneSizeUnderFullMotion() {
        val game = GoldenFixtures.FIX_RAMP_5
        val records = Records(sizes = mapOf("5" to SizeRecord(bestScore = game.bestAtStart)))
        val settings = SETTINGS.copy(motion = MotionPreference.FULL, lastBoardSize = 5)
        rig(game, settings, records).use { rig ->
            show(rig)
            val nodes = digitNodes(SCORE_TAG)
            assertWithMessage("the score's digits")
                .that(digitLines(nodes).joinToString(""))
                .isEqualTo(game.state.score.toString())
            val sizes = nodes.map { checkNotNull(textLayout(it)).layoutInput.style.fontSize }
            assertWithMessage("one size for every digit Text").that(sizes.toSet()).hasSize(1)
            val chip = boundsOf(SCORE_TAG)
            val content = chip.width - with(compose.density) { CHIP_PADDING.toPx() } * 2
            for (line in digitRows(nodes)) {
                val width =
                    line.maxOf { it.boundsInRoot.right } - line.minOf { it.boundsInRoot.left }
                assertWithMessage("a digit row's width (px) within the chip's content width")
                    .that(width)
                    .isAtMost(content + PX)
            }
        }
    }

    /** F17-AC24 */
    @Test
    @Config(qualifiers = "w1000dp-h1000dp-xhdpi")
    fun longScoresFitGameOverCard() {
        val records = Records(sizes = mapOf("8" to SizeRecord(bestScore = LONG_BEST)))
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, lastBoardSize = 8)
        val cases = CARD_WINDOWS.flatMap { window -> FONT_SCALES.map { window to it } }
        val rigs = cases.associateWith {
            ScreenRig(tmp.newFolder(), LONG_OVER_8, settings, records)
        }
        var shown: Pair<DpSize, Float>? by mutableStateOf(null)
        compose.setContent {
            val density = LocalDensity.current.density
            shown?.let { case ->
                key(case) {
                    CompositionLocalProvider(LocalDensity provides Density(density, case.second)) {
                        Box(Modifier.requiredSize(case.first).testTag(WINDOW_TAG)) {
                            RigContent(rigs.getValue(case))
                        }
                    }
                }
            }
        }
        try {
            for ((case, rig) in rigs) {
                shown = null
                compose.waitForIdle()
                shown = case
                rig.awaitReady(compose)
                compose.waitForIdle()
                compose.onNodeWithTag(GAME_OVER_CARD_TAG).assertExists()
                assertLongScoresOnCard(case.first, case.second)
            }
        } finally {
            rigs.values.forEach { it.close() }
        }
    }

    /** F18-AC10 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun shortWindowsKeepBoardAndChromeOnScreen() {
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, lastBoardSize = 4)
        val cases = SHORT_WINDOWS.flatMap { window -> listOf(window to false, window to true) }
        val rigs = cases.associateWith {
            ScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4, settings)
        }
        var shown: Pair<DpSize, Boolean>? by mutableStateOf(null)
        compose.setContent {
            shown?.let { case ->
                key(case) {
                    Box(Modifier.requiredSize(case.first).testTag(WINDOW_TAG)) {
                        RigContent(rigs.getValue(case))
                    }
                }
            }
        }
        try {
            for ((case, rig) in rigs) {
                shown = null
                compose.waitForIdle()
                // Touch exploration is set before the case composes, as TalkBack would be.
                setTouchExploration(case.second)
                shown = case
                rig.awaitReady(compose)
                compose.waitForIdle()
                assertShortWindow(case.first, talkBack = case.second)
            }
        } finally {
            setTouchExploration(false)
            rigs.values.forEach { it.close() }
        }
    }

    /** F18-AC12 */
    @Test
    @Config(qualifiers = "w360dp-h360dp-xxhdpi")
    fun scrollModeCardBringsBoardIntoView() {
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, lastBoardSize = 4)
        rig(WIN_NEXT_LEFT_4, settings).use { rig ->
            show(rig)
            assertThat(layoutSpec().mode).isEqualTo(LayoutMode.SCROLL)
            // The column scrolled to its end, the controls bar in view (D-249 moved More into the
            // header, so New game stands for it; D-252).
            compose.onNodeWithTag(NEW_GAME_TAG).performScrollTo()
            compose.waitForIdle()
            assertWithMessage("scrolled before the move").that(scrollValue()).isGreaterThan(0f)

            compose.onNodeWithTag(BOARD_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
            compose.waitForIdle()

            compose.onNodeWithTag(WIN_CARD_TAG).assertExists()
            val viewport = scrollNode().boundsInRoot
            val board = compose.onNodeWithTag(BOARD_TAG).fetchSemanticsNode()
            assertInside("the board in the viewport", board.boundsInRoot, viewport)
            assertWithMessage("the board's visible height (px)")
                .that(board.boundsInRoot.height)
                .isWithin(1f)
                .of(board.size.height.toFloat())
        }
    }

    /** F22-AC5 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun gotItDismissesHintPermanently() {
        val folder = tmp.newFolder()
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, onboardingDone = false)
        val first = ScreenRig(folder, GoldenFixtures.FIX_FRESH_4, settings)
        var shown: ScreenRig? by mutableStateOf(first)
        compose.setContent { shown?.let { key(it) { RigContent(it) } } }
        first.awaitReady(compose)
        compose.waitForIdle()
        compose.onNodeWithTag(HINT_TAG).assertExists()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(HINT_GOT_IT_TAG).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(HINT_TAG).assertDoesNotExist()
        compose.mainClock.autoAdvance = true

        // Rebuild the container on the same files: the hint stays dismissed.
        shown = null
        compose.waitForIdle()
        runBlocking { first.container.settingsRepository.awaitIdle() }
        first.flush()
        first.closeAndWait(compose)
        ScreenRig(folder, game = null, seed = false).use { second ->
            shown = second
            second.awaitReady(compose)
            compose.waitForIdle()
            compose.onNodeWithTag(HINT_TAG).assertDoesNotExist()
            assertThat(second.container.settingsRepository.state.value.onboardingDone).isTrue()
            shown = null
            compose.waitForIdle()
        }
    }

    /** F22-AC6 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun hintTextUsesBoardTarget() {
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, onboardingDone = false)
        rig(GoldenFixtures.FIX_FRESH_4, settings).use { rig ->
            show(rig)
            assertThat(hintText())
                .isEqualTo("Swipe to move all tiles. Equal tiles merge. Reach 2048.")

            rig.vm.onSizeSelected(BoardSize.of(3))
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                rig.vm.board.value.size.n == 3 && !rig.vm.uiState.value.busy
            }
            compose.waitForIdle()

            assertThat(hintText())
                .isEqualTo("Swipe to move all tiles. Equal tiles merge. Reach 256.")
        }
    }

    /** F22-AC9 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun hintTextUnderTalkBack() {
        setTouchExploration(true)
        try {
            // A fresh install: no saved game, onboarding not done.
            val fresh =
                Settings(motion = MotionPreference.OFF, lastSeenVersionCode = TEST_VERSION_CODE)
            ScreenRig(tmp.newFolder(), game = null, settings = fresh).use { rig ->
                show(rig)
                assertThat(hintText())
                    .isEqualTo(
                        "Use the arrow buttons to move all tiles. Equal tiles merge. Reach 2048.",
                    )
            }
        } finally {
            setTouchExploration(false)
        }
    }

    /** F24-AC7 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun whatsNewSheetIsModalAndDismissible() {
        val settings =
            SETTINGS.copy(
                motion = MotionPreference.OFF,
                lastBoardSize = 4,
                lastSeenVersionCode = SEEN_BEFORE_UPDATE,
            )
        rig(GoldenFixtures.FIX_MID_4, settings).use { rig ->
            show(rig)
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                compose.onAllNodesWithTag(WHATS_NEW_SHEET_TAG).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            val before = rig.snapshot()

            compose.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
            compose.waitForIdle()
            assertThat(rig.snapshot()).isEqualTo(before)
            compose.onNodeWithTag(WHATS_NEW_SHEET_TAG).assertExists()

            compose.onNodeWithTag(WHATS_NEW_GOT_IT_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(WHATS_NEW_SHEET_TAG).assertDoesNotExist()
            assertThat(rig.container.settingsRepository.state.value.lastSeenVersionCode)
                .isEqualTo(TEST_VERSION_CODE)
        }
    }

    /**
     * D-252: under Full motion, which `App` turns on only once the game is ready (Off before), the
     * board is placed once in the default window: centred, inside the window and clear of the
     * header and the controls bar.
     */
    @Test
    fun boardPlacedOnceWhenMotionTurnsOn() {
        rig(GoldenFixtures.FIX_MID_4, SETTINGS.copy(motion = MotionPreference.FULL)).use { rig ->
            show(rig)
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val board = boundsOf(BOARD_TAG)
            assertWithMessage("the board's centre (px)")
                .that(board.center.x)
                .isWithin(1f)
                .of(root.center.x)
            assertInside("the board in the window", board, root)
            assertApart("the board and the header", board, boundsOf(HEADER_TAG))
            assertApart("the board and the controls bar", board, boundsOf(CONTROLS_TAG))
        }
    }

    /**
     * D-252: a motion-level change after a presented move (the system animator scale back from 0 to
     * 1 turns Off into Full) composes the arrangement from the settled board and header: no tile of
     * the last move enters again and no "+N" float starts.
     */
    @Test
    fun settledMoveNotReplayedWhenMotionTurnsOn() {
        rig(GoldenFixtures.FIX_MID_4, SETTINGS.copy(motion = MotionPreference.FULL)).use { rig ->
            rig.scale.scale.value = 0f
            show(rig)
            rig.playOneMove()
            val kinds = rig.vm.board.value.tiles.map { it.kind }
            assertWithMessage("the move merges and spawns")
                .that(kinds)
                .containsAtLeast(TileKind.Merged, TileKind.Spawned)
            assertWithMessage("the move's +N").that(rig.vm.header.value.lastDelta).isGreaterThan(0)
            compose.waitForIdle()
            assertWithMessage("the presented move settled")
                .that(rig.vm.board.value.event)
                .isEqualTo(BoardEvent.Restored)
            compose.mainClock.autoAdvance = false

            rig.scale.scale.value = 1f
            repeat(FRAMES_TO_SETTLE) { frame ->
                compose.mainClock.advanceTimeByFrame()
                val now = probe.snapshot()
                val tiles = now.tiles.filter { !it.outgoing }
                assertWithMessage("frame $frame: tiles drawn").that(tiles).isNotEmpty()
                for (tile in tiles) {
                    assertWithMessage("frame $frame: tile ${tile.id}'s kind")
                        .that(tile.kind)
                        .isEqualTo(TileKind.Static)
                    assertWithMessage("frame $frame: tile ${tile.id}'s scale")
                        .that(tile.scale)
                        .isEqualTo(1f)
                    assertWithMessage("frame $frame: tile ${tile.id}'s alpha")
                        .that(tile.alpha)
                        .isEqualTo(1f)
                }
                assertWithMessage("frame $frame: +N floats").that(now.floats).isEmpty()
            }
        }
    }

    /** F03-AC11 */
    @Test
    fun winCardInteractiveAtT0Plus800Full() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        rig(MotionFixtures.FIX_WIN_PRE_4, SETTINGS.copy(motion = MotionPreference.FULL)).use { rig
            ->
            val origin = playToOrigin(rig, Direction.LEFT)

            advanceTo(origin + WIN_LOCKED_FULL_MS)
            assertCardButtons(WIN_BUTTONS, enabled = false, at = "t = 864")
            advanceTo(origin + WIN_INTERACTIVE_FULL_MS)
            assertCardButtons(WIN_BUTTONS, enabled = true, at = "t = 880")
        }
    }

    /** F03-AC12 */
    @Test
    fun winCardInteractiveAt150Reduced() {
        val settings = SETTINGS.copy(motion = MotionPreference.REDUCED)
        rig(MotionFixtures.FIX_WIN_PRE_4, settings).use { rig ->
            val origin = playToOrigin(rig, Direction.LEFT)

            advanceTo(origin + REDUCED_LOCKED_MS)
            assertCardButtons(WIN_BUTTONS, enabled = false, at = "t = 144")
            advanceTo(origin + REDUCED_INTERACTIVE_MS)
            assertCardButtons(WIN_BUTTONS, enabled = true, at = "t = 160")
            // The confetti draws in the screen's one effects canvas (D-187), which the sparkles
            // share: under Reduced it never holds a particle (D-247).
            while (compose.mainClock.currentTime < origin + CARD_WATCH_MS) {
                assertWithMessage("confetti at t = ${compose.mainClock.currentTime - origin}")
                    .that(probe.snapshot().effects.confettiAlive)
                    .isEqualTo(0)
                compose.mainClock.advanceTimeByFrame()
            }
        }
    }

    /** F03-AC13 */
    @Test
    fun winCardInteractiveImmediatelyOff() {
        rig(MotionFixtures.FIX_WIN_PRE_4, SETTINGS.copy(motion = MotionPreference.OFF)).use { rig ->
            show(rig)
            compose.mainClock.autoAdvance = false

            rig.vm.onMove(Direction.LEFT)
            compose.mainClock.advanceTimeByFrame()

            compose.onNodeWithTag(WIN_CARD_TAG).assertExists()
            assertCardButtons(WIN_BUTTONS, enabled = true, at = "F0")
        }
    }

    /** F03-AC14 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun restoredWinCardInteractiveImmediately() {
        rig(GoldenFixtures.FIX_WON_4, SETTINGS.copy(motion = MotionPreference.FULL)).use { rig ->
            compose.mainClock.autoAdvance = false
            compose.setContent { RigContent(rig) }
            rig.awaitReady(compose)
            advanceToLoadedFrame()

            compose.onNodeWithTag(WIN_CARD_TAG).assertExists()
            assertCardButtons(WIN_BUTTONS, enabled = true, at = "the first loaded frame")
            repeat(FRAMES_TO_SETTLE) { frame ->
                val now = probe.snapshot()
                val overlay = checkNotNull(now.overlay) { "no card at frame $frame" }
                assertWithMessage("frame $frame: interactive").that(overlay.interactive).isTrue()
                assertWithMessage("frame $frame: badge morph")
                    .that(overlay.badgeMorph)
                    .isEqualTo(1f)
                assertWithMessage("frame $frame: confetti")
                    .that(now.effects.confettiAlive)
                    .isEqualTo(0)
                assertWithMessage("frame $frame: running").that(now.isAnyRunning).isFalse()
                compose.mainClock.advanceTimeByFrame()
            }
            assertThat(haptics.events).isEmpty()
        }
    }

    /** F03-AC16 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun winCardBlocksBoardInput() {
        rig(GoldenFixtures.FIX_WON_4).use { rig ->
            show(rig)
            compose.onNodeWithTag(WIN_CARD_TAG).assertExists()
            val game = rig.saved()
            val board = rig.vm.board.value

            assertBoardInputBlocked(rig)

            rig.flush()
            assertThat(rig.saved()).isEqualTo(game)
            assertThat(rig.vm.board.value).isEqualTo(board)
            assertThat(rig.vm.uiState.value.status).isEqualTo(GameStatus.WON)
            compose.onNodeWithTag(WIN_CARD_TAG).assertExists()
        }
    }

    /** F03-AC18 */
    @Test
    @Config(qualifiers = FIT_HOST)
    fun winCardFitsBoard() {
        assertCardFitsBoard(GoldenFixtures.FIX_WON_4, WIN_CARD_TAG)
    }

    /** F03-AC19 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun cardButtonsNeverRenderDisabledColors() {
        rig(MotionFixtures.FIX_WIN_PRE_4, SETTINGS.copy(motion = MotionPreference.FULL)).use { rig
            ->
            lateinit var scheme: ColorScheme
            compose.setContent {
                RigContent(rig)
                GameTheme(darkTheme = false) { scheme = MaterialTheme.colorScheme }
            }
            rig.awaitReady(compose)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            probe.originMs = null
            rig.vm.onMove(Direction.LEFT)
            val origin = advanceToOrigin()

            advanceTo(origin + WIN_LOCKED_FULL_MS)
            val keepGoing = compose.onNodeWithTag(KEEP_GOING_TAG)
            keepGoing.assertIsNotEnabled()
            keepGoing.performSemanticsAction(SemanticsActions.RequestFocus)
            keepGoing.assertIsNotFocused()
            val pixel = labelFreePixel(keepGoing.captureToImage())
            val disabled =
                scheme.onSurface
                    .copy(alpha = DISABLED_CONTAINER_ALPHA)
                    .compositeOver(scheme.surfaceContainerHigh)
            assertWithMessage("Keep going's container at t = 864 ($pixel)")
                .that(distance(pixel, scheme.primary))
                .isLessThan(distance(pixel, disabled))

            advanceTo(origin + WIN_INTERACTIVE_FULL_MS)
            keepGoing.assertIsEnabled()
        }
    }

    /** F04-AC2 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun gameOverCardContent() {
        rig(GoldenFixtures.FIX_OVER_4, records = GoldenFixtures.FIX_RECORDS).use { rig ->
            show(rig)

            val inCard = hasAnyAncestor(hasTestTag(GAME_OVER_CARD_TAG))
            compose.onNodeWithTag(GAME_OVER_CARD_TAG).assertExists()
            for (text in listOf("Game over", "3012", "24680", "302 moves · 6:41 · No undo")) {
                compose.onNode(inCard and hasText(text), useUnmergedTree = true).assertExists()
            }
            compose
                .onNode(inCard and hasContentDescription("Best tile, 128"), useUnmergedTree = true)
                .assertExists()
        }
    }

    /** F04-AC3 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun gameOverCardUndoPhrasing() {
        val rigs =
            listOf(1, 2).associateWith { undos ->
                ScreenRig(tmp.newFolder(), overWithUndos(undos), SETTINGS)
            }
        var shown by mutableStateOf(rigs.getValue(1))
        compose.setContent { key(shown) { RigContent(shown) } }
        try {
            for ((undos, rig) in rigs) {
                shown = rig
                rig.awaitReady(compose)
                compose.waitForIdle()
                val ending = if (undos == 1) "1 undo" else "$undos undos"
                compose
                    .onNode(
                        hasAnyAncestor(hasTestTag(GAME_OVER_CARD_TAG)) and
                            hasText("302 moves · 6:41 · $ending"),
                        useUnmergedTree = true,
                    )
                    .assertExists()
            }
        } finally {
            rigs.values.forEach { it.close() }
        }
    }

    /** F04-AC4 */
    @Test
    fun gameOverCardInteractiveAtT0Plus700Full() {
        MotionFixtures.OVER_PRE_LEFT.verify()
        val settings = SETTINGS.copy(motion = MotionPreference.FULL)
        rig(MotionFixtures.FIX_OVER_PRE_4, settings).use { rig ->
            val origin = playToOrigin(rig, Direction.LEFT)

            advanceTo(origin + OVER_LOCKED_FULL_MS)
            assertCardButtons(OVER_BUTTONS, enabled = false, at = "t = 992")
            advanceTo(origin + OVER_INTERACTIVE_FULL_MS)
            assertCardButtons(OVER_BUTTONS, enabled = true, at = "t = 1008")
        }
    }

    /** F04-AC5 */
    @Test
    fun gameOverCardInteractiveAt150Reduced() {
        val settings = SETTINGS.copy(motion = MotionPreference.REDUCED)
        rig(MotionFixtures.FIX_OVER_PRE_4, settings).use { rig ->
            val origin = playToOrigin(rig, Direction.LEFT)

            advanceTo(origin + REDUCED_LOCKED_MS)
            assertCardButtons(OVER_BUTTONS, enabled = false, at = "t = 144")
            advanceTo(origin + REDUCED_INTERACTIVE_MS)
            assertCardButtons(OVER_BUTTONS, enabled = true, at = "t = 160")
        }
    }

    /** F04-AC6 */
    @Test
    fun gameOverCardInteractiveImmediatelyOff() {
        val settings = SETTINGS.copy(motion = MotionPreference.OFF)
        rig(MotionFixtures.FIX_OVER_PRE_4, settings).use { rig ->
            show(rig)
            compose.mainClock.autoAdvance = false

            rig.vm.onMove(Direction.LEFT)
            compose.mainClock.advanceTimeByFrame()

            compose.onNodeWithTag(GAME_OVER_CARD_TAG).assertExists()
            assertCardButtons(OVER_BUTTONS, enabled = true, at = "F0")
        }
    }

    /** F04-AC7 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun gameOverCardNotDismissible() {
        rig(GoldenFixtures.FIX_OVER_4).use { rig ->
            show(rig)
            compose.onNodeWithTag(GAME_OVER_CARD_TAG).assertExists()
            val game = rig.saved()
            val board = rig.vm.board.value

            assertBoardInputBlocked(rig)

            rig.flush()
            assertThat(rig.saved()).isEqualTo(game)
            assertThat(rig.vm.board.value).isEqualTo(board)
            assertThat(rig.vm.uiState.value.status).isEqualTo(GameStatus.OVER)
            compose.onNodeWithTag(GAME_OVER_CARD_TAG).assertExists()
        }
    }

    /** F04-AC14 */
    @Test
    @Config(qualifiers = FIT_HOST)
    fun gameOverCardFitsBoard() {
        assertCardFitsBoard(GoldenFixtures.FIX_OVER_4, GAME_OVER_CARD_TAG)
    }

    /** F10-AC6 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun shareLaunchesChooserWithText() {
        rig(GoldenFixtures.FIX_OVER_4).use { rig ->
            show(rig)

            compose.onNodeWithTag(CARD_SHARE_TAG).performClick()
            compose.waitForIdle()

            assertChooser(
                "2048 · 4×4 · 3012 · best tile 128 · 302 moves · 6:41 · No undo\n" +
                    "$BROWN$BROWN$BROWN$BROWN\n" +
                    "$BROWN$BROWN$BROWN$BROWN\n" +
                    "$YELLOW$YELLOW$YELLOW$ORANGE\n" +
                    "$BROWN$BROWN$BROWN$BROWN",
            )
        }
    }

    /** F10-AC7 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun shareOnBothCardsNotInMenu() {
        val won = ScreenRig(tmp.newFolder(), GoldenFixtures.FIX_WON_4, SETTINGS)
        val over = ScreenRig(tmp.newFolder(), GoldenFixtures.FIX_OVER_4, SETTINGS)
        var shown by mutableStateOf(won)
        compose.setContent { key(shown) { RigContent(shown) } }
        try {
            for ((rig, card) in listOf(won to WIN_CARD_TAG, over to GAME_OVER_CARD_TAG)) {
                shown = rig
                rig.awaitReady(compose)
                compose.waitForIdle()
                compose
                    .onNode(hasTestTag(CARD_SHARE_TAG) and hasAnyAncestor(hasTestTag(card)))
                    .assertExists()

                // The game menu (M3-D3's GameMoreMenu) opens over the card; no Share outside it.
                compose.onNodeWithTag(MORE_TAG).performClick()
                compose.waitForIdle()
                for (key in listOf(StatsKey, SettingsKey, HowToPlayKey, AboutKey)) {
                    compose.onNodeWithTag(menuItemTag(key)).assertIsDisplayed()
                }
                compose
                    .onAllNodes(
                        (hasText(SHARE) or hasContentDescription(SHARE)) and
                            !hasAnyAncestor(hasTestTag(card)),
                        useUnmergedTree = true,
                    )
                    .assertCountEquals(0)
            }
        } finally {
            won.close()
            over.close()
        }
    }

    /** F10-AC8 */
    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
    fun winCardShareLaunchesChooser() {
        rig(GoldenFixtures.FIX_WON_4).use { rig ->
            show(rig)

            compose.onNodeWithTag(CARD_SHARE_TAG).performClick()
            compose.waitForIdle()

            // FIX_WON_4: 2048 at index 0, 2 and 4 beside it, 8 below, 2s at 8 and 15.
            assertChooser(
                "2048 · 4×4 · 20124 · best tile 2048 · 980 moves · 21:15 · No undo\n" +
                    "$BLUE$BROWN$BROWN$EMPTY\n" +
                    "$BROWN$EMPTY$EMPTY$EMPTY\n" +
                    "$BROWN$EMPTY$EMPTY$EMPTY\n" +
                    "$EMPTY$EMPTY$EMPTY$BROWN",
            )
        }
    }

    /** F21-AC2 */
    @Test
    fun menuOpensEveryDestination() {
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            var dispatcher: OnBackPressedDispatcher? = null
            compose.setContent {
                dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                RigContent(rig)
            }
            rig.awaitReady(compose)
            compose.waitForIdle()
            val destinations =
                listOf(
                    StatsKey to "Statistics",
                    SettingsKey to "Settings",
                    HowToPlayKey to "How to play",
                    AboutKey to "About",
                )

            for ((key, title) in destinations) {
                compose.onNodeWithTag(MORE_TAG).performClick()
                compose.waitForIdle()
                compose.onNodeWithTag(menuItemTag(key)).assert(hasText(title)).performClick()
                compose.waitForIdle()

                compose.onNodeWithTag(screenTag(key)).assertIsDisplayed()
                compose
                    .onNode(hasAnyAncestor(hasTestTag(screenTag(key))) and hasText(title))
                    .assertIsDisplayed()
                compose
                    .onNodeWithTag(NAVIGATE_UP_TAG)
                    .assertIsDisplayed()
                    .assert(hasContentDescription("Navigate up"))
                    .assert(hasClickAction())
                compose.onNodeWithTag(screenTag(GameKey)).assertDoesNotExist()

                checkNotNull(dispatcher).onBackPressed()
                compose.waitForIdle()
                compose.onNodeWithTag(screenTag(key)).assertDoesNotExist()
                compose.onNodeWithTag(BOARD_TAG).assertIsDisplayed()
            }

            // The back arrow pops as Back does.
            compose.onNodeWithTag(MORE_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(menuItemTag(SettingsKey)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(NAVIGATE_UP_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(screenTag(SettingsKey)).assertDoesNotExist()
            compose.onNodeWithTag(BOARD_TAG).assertIsDisplayed()
        }
    }

    /** F08-AC24 */
    @Test
    fun bestChipShowsCurrentScoreAfterStatisticsReset() {
        rig(GoldenFixtures.FIX_MID_4, records = GoldenFixtures.FIX_RECORDS).use { rig ->
            var dispatcher: OnBackPressedDispatcher? = null
            compose.setContent {
                dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                RigContent(rig)
            }
            rig.awaitReady(compose)
            compose.waitForIdle()
            // A move first: the header the player leaves is a Full-motion move's header.
            rig.playOneMove()
            compose.waitForIdle()
            val score = rig.vm.header.value.score
            val moveId = rig.vm.board.value.moveId
            assertBestChip("before the reset", FIX_RECORDS_BEST_4)

            compose.onNodeWithTag(MORE_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(menuItemTag(StatsKey)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(STATS_RESET_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(DIALOG_CONFIRM_TAG).assert(hasText("Reset")).performClick()
            compose.waitForIdle()
            compose
                .onNodeWithTag(DIALOG_CONFIRM_TAG)
                .assert(hasText("Reset everything"))
                .performClick()
            compose.waitForIdle()
            assertThat(rig.container.recordsRepository.state.value.sizes).isEmpty()
            checkNotNull(dispatcher).onBackPressed()
            compose.waitForIdle()

            compose.onNodeWithTag(BOARD_TAG).assertIsDisplayed()
            assertBestChip("after the reset", score)
            assertWithMessage("the board after the reset: no new move")
                .that(rig.vm.board.value.moveId)
                .isEqualTo(moveId)
        }
    }

    /** The BEST chip shows [best]: its drawn digits, its description and the probe's value. */
    private fun assertBestChip(label: String, best: Long) {
        assertWithMessage("$label: Best digits")
            .that(drawnDigits(BEST_TAG).joinToString("") { it.first })
            .isEqualTo(best.toString())
        assertWithMessage("$label: Best description")
            .that(compose.onNodeWithTag(BEST_TAG).fetchSemanticsNode().config[CONTENT_DESCRIPTION])
            .containsExactly("Best $best")
        assertWithMessage("$label: displayed best")
            .that(checkNotNull(probe.snapshot().header).displayedBest)
            .isEqualTo(best)
    }

    /** F21-AC10 */
    @Test
    fun navigationIsInstantWhenMotionOff() {
        rig(GoldenFixtures.FIX_MID_4, SETTINGS.copy(motion = MotionPreference.OFF)).use { rig ->
            show(rig)
            compose.onNodeWithTag(MORE_TAG).performClick()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false

            compose.onNodeWithTag(menuItemTag(SettingsKey)).performClick()
            // The frame after the tap reads the new back stack; `NavDisplay` starts its transition,
            // and composes Settings, on the frame after that (D-247).
            val maxFrames = 2
            val shown = compose.onAllNodesWithTag(screenTag(SettingsKey))
            var frames = 0
            while (shown.fetchSemanticsNodes().isEmpty() && frames < maxFrames) {
                compose.mainClock.advanceTimeByFrame()
                frames++
            }

            assertWithMessage("frames from the tap to Settings").that(frames).isAtMost(maxFrames)
            assertThat(probe.navOriginMs).isEqualTo(compose.mainClock.currentTime)
            val settings = compose.onNodeWithTag(screenTag(SettingsKey))
            settings.assertIsDisplayed()
            assertThat(settings.fetchSemanticsNode().boundsInRoot.left).isEqualTo(0f)
            compose.onNodeWithTag(screenTag(GameKey)).assertDoesNotExist()
            val first = settings.captureToImage().asAndroidBitmap()
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            val settled = settings.captureToImage().asAndroidBitmap()
            assertWithMessage("Settings' first frame equals its settled frame")
                .that(first.sameAs(settled))
                .isTrue()
        }
    }

    /** F02-AC7 */
    @Test
    fun sizeSheetRowsShowGoalBestAndInProgress() {
        // A saved 3×3 game in progress beside the current 4×4 game, seeded with it: the ViewModel's
        // load reads every size's file, so nothing may write one behind it.
        val others = listOf(GoldenFixtures.FIX_MID_3)
        rig(GoldenFixtures.FIX_MID_4, records = GoldenFixtures.FIX_RECORDS, others = others).use {
            rig ->
            show(rig)
            compose.onNodeWithTag(SIZE_CHIP_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(SIZE_SHEET_TAG).assertExists()

            val tops =
                (BoardSize.MIN_N..BoardSize.MAX_N).map { n ->
                    val supporting =
                        when (n) {
                            3 -> "Goal 256 · Best 2,100"
                            4 -> "Goal 2048 · Best 24,680"
                            else -> "Goal 2048 · No best score yet"
                        }
                    val row = compose.onNodeWithTag(sizeRowTag(n))
                    row.assert(hasText("$n×$n"))
                        .assert(hasText(supporting))
                        .assert(
                            SemanticsMatcher.expectValue(
                                SemanticsProperties.Role,
                                Role.RadioButton,
                            ),
                        )
                    if (n == 4) row.assertIsSelected() else row.assertIsNotSelected()
                    // 3×3 and the current 4×4 game are in progress (FIX_MID_3, FIX_MID_4).
                    val pill = hasText("In progress")
                    row.assert(
                        if (n <= 4) pill else SemanticsMatcher("no pill") { !pill.matches(it) },
                    )
                    val checks =
                        compose.onAllNodes(
                            hasTestTag(SIZE_CHECK_TAG) and
                                hasAnyAncestor(hasTestTag(sizeRowTag(n))),
                            useUnmergedTree = true,
                        )
                    checks.assertCountEquals(if (n == 4) 1 else 0)
                    row.fetchSemanticsNode().positionInRoot.y
                }
            assertWithMessage("rows 3×3 … 8×8 from the top").that(tops).isInStrictOrder()

            // A row hides the sheet, then switches to its size.
            compose.onNodeWithTag(sizeRowTag(3)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(SIZE_SHEET_TAG).assertDoesNotExist()
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                val ui = rig.vm.uiState.value
                ui.size == BoardSize.of(3) && ui.ready && !ui.busy
            }
            assertThat(rig.vm.board.value.size).isEqualTo(BoardSize.of(3))
        }
    }

    /**
     * D-245: the size chip stays enabled while the game loads (DESIGN_SYSTEM.md §6.1, the Loading
     * state), and a sheet opened then lists nothing until the ViewModel is ready, then its six
     * rows, read once ready (a sheet restored open after process death takes the same path). The
     * records file is held back, so the load waits in `records.awaitLoaded()` with the sheet open.
     */
    @Test
    fun sizeSheetOpenedBeforeLoadFillsOnceReady() {
        val records = CountDownLatch(1)
        rig(GoldenFixtures.FIX_MID_4, holdRecords = records).use { rig ->
            try {
                compose.setContent { RigContent(rig) }
                compose.waitForIdle()
                assertWithMessage("ready before the records load")
                    .that(rig.vm.ready.value)
                    .isFalse()
                compose.onNodeWithTag(SIZE_CHIP_TAG).performClick()
                compose.waitForIdle()
                compose.onNodeWithTag(SIZE_SHEET_TAG).assertExists()
                for (n in BoardSize.MIN_N..BoardSize.MAX_N) {
                    compose.onNodeWithTag(sizeRowTag(n)).assertDoesNotExist()
                }
            } finally {
                records.countDown()
            }

            rig.awaitReady(compose)
            compose.waitForIdle()

            compose.onNodeWithTag(SIZE_SHEET_TAG).assertExists()
            for (n in BoardSize.MIN_N..BoardSize.MAX_N) {
                val row = compose.onNodeWithTag(sizeRowTag(n)).assert(hasText("$n×$n"))
                if (n == 4) row.assertIsSelected() else row.assertIsNotSelected()
            }
            // FIX_MID_4 is the current game, in progress; no other size has a saved game.
            compose.onNodeWithTag(sizeRowTag(4)).assert(hasText("In progress"))
            compose
                .onAllNodes(hasText("In progress") and hasAnyAncestor(hasTestTag(SIZE_SHEET_TAG)))
                .assertCountEquals(1)
        }
    }

    /** Shows [rig] with a manual clock, commits [direction] and advances to its origin (F1). */
    /**
     * F17-AC23 in [window] at [fontScale] (DESIGN_SYSTEM.md §5.3, §7.2; D-188): no text overflows,
     * every digit of both chips is drawn at 14–28 sp and read line by line, the chips lie in the
     * root and their header without overlapping each other, the title, the size chip or the board,
     * the board is `resolveGameLayout`'s, and the chrome stays off the board outside SCROLL.
     */
    private fun assertLongScoresFit(window: DpSize, fontScale: Float) {
        val label = "${window.width.value.toInt()}×${window.height.value.toInt()} at $fontScale"
        assertNoTextOverflow(label)
        for ((tag, digits, spoken) in LONG_CHIPS) assertChipDigits(label, tag, digits, spoken)
        val spec = compose.onNode(HAS_LAYOUT_SPEC).fetchSemanticsNode().config[LayoutSpecKey]
        assertChipsAndBoard(label, spec)
        val score = boundsOf(SCORE_TAG)
        val best = boundsOf(BEST_TAG)
        if (fontScale == LARGE_FONT_SCALE && window.width == NARROW_WIDTH) {
            val header = boundsOf(HEADER_TAG)
            assertWithMessage("$label: Best below Score (STACKED chips)")
                .that(best.top)
                .isAtLeast(score.bottom - PX)
            for ((name, chip) in listOf("Score" to score, "Best" to best)) {
                assertWithMessage("$label: $name as wide as the header")
                    .that(chip.width)
                    .isWithin(1f)
                    .of(header.width)
            }
        }
        if (fontScale == LARGE_FONT_SCALE && window == RAIL_WINDOW) {
            assertWithMessage("$label: arrangement")
                .that(spec.mode)
                .isEqualTo(LayoutMode.THREE_COLUMN)
            assertWithMessage("$label: rail (dp)")
                .that(spec.rail)
                .isWithin(DP_EPSILON)
                .of(RAIL_WINDOW_RAIL_DP)
            assertWithMessage("$label: Best's digit lines")
                .that(digitLines(digitNodes(BEST_TAG)))
                .hasSize(2)
        }
    }

    /**
     * No text node in the window overflows (F17-AC23): each one's `TextLayoutResult`, laid out
     * again in the box it is drawn in, reports no `hasVisualOverflow`. The node's own
     * `GetTextLayoutResult` result is laid out at its last maximum width, not at its drawn width,
     * so it reports an overflow for every centred text narrower than that maximum.
     */
    private fun assertNoTextOverflow(label: String) {
        val texts =
            compose
                .onAllNodes(
                    hasAnyAncestor(hasTestTag(WINDOW_TAG)) and
                        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
                .filter { it.layoutInfo.isPlaced }
        for (node in texts) {
            val input = textLayout(node)?.layoutInput ?: continue
            val box = Constraints(maxWidth = node.size.width, maxHeight = node.size.height)
            val drawn =
                TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, 0)
                    .measure(
                        input.text,
                        input.style,
                        input.overflow,
                        input.softWrap,
                        input.maxLines,
                        input.placeholders,
                        box,
                    )
            val facts = "${node.size}, ${drawn.lineCount} lines, ${drawn.multiParagraph.height}"
            assertWithMessage("$label: \"${input.text}\" overflows its box ($facts)")
                .that(drawn.hasVisualOverflow)
                .isFalse()
        }
    }

    /**
     * The chip tagged [tag] draws exactly [digits], line by line, every digit `Text` at 14–28 sp,
     * and is spoken as [spoken] (F17-AC23; DESIGN_SYSTEM.md §7.2).
     */
    private fun assertChipDigits(label: String, tag: String, digits: String, spoken: String) {
        val nodes = digitNodes(tag)
        for (node in nodes) {
            val size = checkNotNull(textLayout(node)).layoutInput.style.fontSize
            assertWithMessage("$label: $tag digits in sp").that(size.isSp).isTrue()
            assertWithMessage("$label: $tag digit size (sp)")
                .that(size.value)
                .isAtLeast(SCORE_DIGITS_MIN_SP)
            assertWithMessage("$label: $tag digit size (sp)")
                .that(size.value)
                .isAtMost(SCORE_DIGITS_MAX_SP)
        }
        assertWithMessage("$label: $tag digits, line by line")
            .that(digitLines(nodes).joinToString(""))
            .isEqualTo(digits)
        assertWithMessage("$label: $tag description")
            .that(compose.onNodeWithTag(tag).fetchSemanticsNode().config[CONTENT_DESCRIPTION])
            .containsExactly(spoken)
    }

    /**
     * The chips lie in the root and their header (stats rail or panel) and overlap neither each
     * other, the title, the size chip nor the board; the board is square, at least 220 dp and
     * [spec]'s; outside SCROLL every chrome node lies in the root, off the board (F17-AC23).
     */
    private fun assertChipsAndBoard(label: String, spec: GameLayoutSpec) {
        val root = boundsOf(WINDOW_TAG)
        val header = boundsOf(HEADER_TAG)
        val score = boundsOf(SCORE_TAG)
        val best = boundsOf(BEST_TAG)
        val boardNode = compose.onNodeWithTag(BOARD_TAG).fetchSemanticsNode()
        val board = boardNode.boundsInRoot
        val avoided = listOfNotNull(tagged(TITLE_TAG), tagged(SIZE_CHIP_TAG)).flatten() + board
        for ((name, chip, other) in
            listOf(Triple("Score", score, best), Triple("Best", best, score))) {
            assertInside("$label: $name in the root", chip, root)
            assertInside("$label: $name in its header", chip, header)
            for (rect in avoided + other) assertApart("$label: $name and $rect", chip, rect)
        }
        val side = with(compose.density) { spec.board.dp.roundToPx() }
        assertWithMessage("$label: board square")
            .that(boardNode.size.height)
            .isEqualTo(boardNode.size.width)
        assertWithMessage("$label: resolved board (dp)").that(spec.board).isAtLeast(BOARD_FLOOR_DP)
        assertWithMessage("$label: board (px) = $spec").that(boardNode.size.width).isEqualTo(side)
        if (spec.mode == LayoutMode.SCROLL) return
        for (rect in CHROME_TAGS.flatMap { tagged(it).orEmpty() }) {
            assertInside("$label: $rect in the root", rect, root)
            assertApart("$label: $rect and the board", rect, board)
        }
    }

    /**
     * F05-AC17: the shown snackbar does not intersect the controls bar, and lies in the gap between
     * the board and the controls or over the board, inside it by 8 dp (D-186).
     */
    private fun assertSnackbarPlaced(label: String) {
        val snackbar = boundsOf(SNACKBAR_TAG)
        val controls = boundsOf(CONTROLS_TAG)
        val board = boundsOf(BOARD_TAG)
        assertWithMessage("$label: the snackbar $snackbar and the controls bar $controls overlap")
            .that(snackbar.overlaps(controls))
            .isFalse()
        val inset = with(compose.density) { SNACKBAR_BOARD_INSET.toPx() }
        val inGap = snackbar.top >= board.bottom - PX && snackbar.bottom <= controls.top + PX
        val overBoard =
            snackbar.left >= board.left + inset - PX &&
                snackbar.right <= board.right - inset + PX &&
                snackbar.top >= board.top + inset - PX &&
                snackbar.bottom <= board.bottom - inset + PX
        assertWithMessage("$label: the snackbar $snackbar in the gap or over the board $board")
            .that(inGap || overBoard)
            .isTrue()
    }

    /**
     * F17-AC24 in [window] at [fontScale]: no text overflows; every digit `Text` on the game-over
     * card is 14–40 sp (14–24 sp in the Compact card's boxes); the card's digits are exactly the
     * Score's and the Best's ten each, and both full values are on the card as a text or a content
     * description; the card lies inside the board minus 8 dp (D-084, D-200).
     */
    private fun assertLongScoresOnCard(window: DpSize, fontScale: Float) {
        val label = "${window.width.value.toInt()}×${window.height.value.toInt()} at $fontScale"
        assertNoTextOverflow(label)
        val digits =
            compose
                .onAllNodes(
                    hasAnyAncestor(hasTestTag(GAME_OVER_CARD_TAG)) and DIGITS,
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
                .filter { it.layoutInfo.isPlaced }
        val max =
            if (window.height == COMPACT_CARD_HEIGHT) BOX_DIGITS_MAX_SP else CARD_DIGITS_MAX_SP
        for (node in digits) {
            val size = checkNotNull(textLayout(node)).layoutInput.style.fontSize
            assertWithMessage("$label: card digit size (sp)").that(size.value).isAtLeast(14f)
            assertWithMessage("$label: card digit size (sp)").that(size.value).isAtMost(max)
        }
        val text = digits.joinToString("") { it.config[SemanticsProperties.Text].joinToString("") }
        assertWithMessage("$label: every digit of Score and Best on the card")
            .that(text)
            .isEqualTo(LONG_BEST.toString().repeat(2))
        val full = LONG_BEST.toString()
        val spoken =
            compose
                .onAllNodes(hasAnyAncestor(hasTestTag(GAME_OVER_CARD_TAG)), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .count { node ->
                    val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
                    val descriptions =
                        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    texts.any { it.text == full } || descriptions.contains(full)
                }
        assertWithMessage("$label: nodes that carry the full values").that(spoken).isAtLeast(2)
        val inset = with(compose.density) { CARD_BOARD_INSET.toPx() }
        val board = boundsOf(BOARD_TAG)
        val card = boundsOf(GAME_OVER_CARD_TAG)
        val inner =
            Rect(board.left + inset, board.top + inset, board.right - inset, board.bottom - inset)
        assertInside("$label: the card in the board minus 8 dp", card, inner)
    }

    /**
     * F18-AC10 in [window]: the board is square, at least 220 dp and `resolveGameLayout`'s, and
     * equals the board that the chrome drawn now leaves ([shortWindowBoard]), within 4 dp of the
     * nominal 258 dp (STACKED, off) or 298 dp (SCROLL, on) (D-251). Without [talkBack]: the COMPACT
     * header (no title), no scrolling, and every chrome node in the window and off the board. With
     * it: the column scrolls, the board and the status line are fully on screen, and scrolling to
     * "Move up" and to New game shows each fully.
     */
    private fun assertShortWindow(window: DpSize, talkBack: Boolean) {
        val label =
            "${window.width.value.toInt()}×${window.height.value.toInt()}, TalkBack $talkBack"
        val spec = layoutSpec()
        val boardNode = compose.onNodeWithTag(BOARD_TAG).fetchSemanticsNode()
        val side = with(compose.density) { spec.board.dp.roundToPx() }
        assertWithMessage("$label: resolved board (dp) of $spec")
            .that(spec.board)
            .isAtLeast(BOARD_FLOOR_DP)
        assertWithMessage("$label: board square")
            .that(boardNode.size.height)
            .isEqualTo(boardNode.size.width)
        assertWithMessage("$label: board (px)").that(boardNode.size.width).isEqualTo(side)
        val mode = if (talkBack) LayoutMode.SCROLL else LayoutMode.STACKED
        assertWithMessage("$label: arrangement").that(spec.mode).isEqualTo(mode)
        val expected = shortWindowBoard(window, mode)
        assertWithMessage("$label: board (dp) against the drawn chrome")
            .that(boardNode.size.width / compose.density.density)
            .isWithin(PX_DP)
            .of(expected)
        assertWithMessage("$label: board from the drawn chrome (dp) against the nominal chrome")
            .that(expected)
            .isWithin(NOMINAL_SLACK_DP)
            .of(if (talkBack) SCROLL_BOARD_DP else STACKED_BOARD_DP)
        val root = boundsOf(WINDOW_TAG)
        val scrolling =
            compose
                .onAllNodes(
                    hasAnyAncestor(hasTestTag(WINDOW_TAG)) and
                        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
                )
                .fetchSemanticsNodes()
        if (!talkBack) {
            compose.onAllNodesWithTag(TITLE_TAG).assertCountEquals(0)
            assertWithMessage("$label: scroll containers").that(scrolling).isEmpty()
            for (rect in CHROME_TAGS.flatMap { tagged(it).orEmpty() }) {
                assertInside("$label: $rect in the window", rect, root)
                assertApart("$label: $rect and the board", rect, boardNode.boundsInRoot)
            }
        } else {
            assertWithMessage("$label: scroll containers").that(scrolling).isNotEmpty()
            assertFullyShown(label, BOARD_TAG, root)
            assertFullyShown(label, STATUS_LINE_TAG, root)
            for (tag in listOf(padButtonTag(Direction.UP), NEW_GAME_TAG)) {
                compose.onNodeWithTag(tag).performScrollTo()
                compose.waitForIdle()
                assertFullyShown(label, tag, root)
            }
        }
    }

    /**
     * The node tagged [tag] is drawn whole (not clipped by its scroll container) inside [window].
     */
    private fun assertFullyShown(label: String, tag: String, window: Rect) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val shown = node.boundsInRoot
        assertWithMessage("$label: $tag's shown width (px)")
            .that(shown.width)
            .isWithin(1f)
            .of(node.size.width.toFloat())
        assertWithMessage("$label: $tag's shown height (px)")
            .that(shown.height)
            .isWithin(1f)
            .of(node.size.height.toFloat())
        assertInside("$label: $tag on screen", shown, window)
    }

    /**
     * The board that the chrome drawn now leaves in the short [window] (DESIGN_SYSTEM.md §5.3, the
     * COMPACT header; zero insets and 16 dp margins, §5.2): STACKED `min(availW, availH − (header +
     * 8 + 8 + controls))`, SCROLL with the pad `availH − (header + 8) − (8 + status line)`.
     */
    private fun shortWindowBoard(window: DpSize, mode: LayoutMode): Float {
        val availW = window.width.value - 2 * SHORT_MARGIN_DP
        val availH = window.height.value - 2 * SHORT_MARGIN_DP
        val header = heightDp(HEADER_TAG)
        return when (mode) {
            LayoutMode.STACKED ->
                minOf(availW, availH - (header + 2 * COMPACT_GAP_DP + heightDp(CONTROLS_TAG)))
            LayoutMode.SCROLL ->
                availH - (header + COMPACT_GAP_DP) - (COMPACT_GAP_DP + heightDp(STATUS_LINE_TAG))
            else -> error("$mode in a short window")
        }
    }

    /** The drawn height in dp of the node tagged [tag]. */
    private fun heightDp(tag: String): Float =
        compose.onNodeWithTag(tag).fetchSemanticsNode().size.height / compose.density.density

    /** The resolved arrangement that `GameLayout` drew. */
    private fun layoutSpec(): GameLayoutSpec =
        compose.onNode(HAS_LAYOUT_SPEC).fetchSemanticsNode().config[LayoutSpecKey]

    /** The SCROLL arrangement's column, the Game content's scroll container. */
    private fun scrollNode(): SemanticsNode =
        compose.onNodeWithTag(layoutTag(LayoutMode.SCROLL)).fetchSemanticsNode()

    /** The SCROLL column's scroll value in px. */
    private fun scrollValue(): Float =
        scrollNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** The first-launch hint's text. */
    private fun hintText(): String =
        compose
            .onNode(
                hasAnyAncestor(hasTestTag(HINT_TAG)) and hasText("Reach", substring = true),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("")

    /** Turns touch exploration on or off as TalkBack does (`ShadowAccessibilityManager`). */
    private fun setTouchExploration(on: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(AccessibilityManager::class.java))
            .setTouchExplorationEnabled(on)
    }

    /**
     * The digit nodes of [nodes] grouped into rows as [digitLines] groups them, each row's nodes in
     * no particular order.
     */
    private fun digitRows(nodes: List<SemanticsNode>): List<List<SemanticsNode>> {
        val rows = mutableListOf<MutableList<SemanticsNode>>()
        for (node in nodes.sortedBy { it.boundsInRoot.top }) {
            val row = rows.lastOrNull()
            if (row != null && node.boundsInRoot.center.y < row.first().boundsInRoot.bottom) {
                row += node
            } else {
                rows += mutableListOf(node)
            }
        }
        return rows
    }

    /** The placed digit-only text nodes inside the chip tagged [chipTag]. */
    private fun digitNodes(chipTag: String): List<SemanticsNode> =
        compose
            .onAllNodes(hasAnyAncestor(hasTestTag(chipTag)) and DIGITS, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }

    /**
     * The text of [nodes] line by line: nodes whose vertical centre lies above the bottom of a
     * line's first node join that line; each line reads its nodes from left to right.
     */
    private fun digitLines(nodes: List<SemanticsNode>): List<String> {
        val lines = mutableListOf<MutableList<SemanticsNode>>()
        for (node in nodes.sortedBy { it.boundsInRoot.top }) {
            val line = lines.lastOrNull()
            if (line != null && node.boundsInRoot.center.y < line.first().boundsInRoot.bottom) {
                line += node
            } else {
                lines += mutableListOf(node)
            }
        }
        return lines.map { line ->
            line
                .sortedBy { it.boundsInRoot.left }
                .joinToString("") { it.config[SemanticsProperties.Text].joinToString("") }
        }
    }

    /** [node]'s text layout as its `GetTextLayoutResult` action reports it, if it has one. */
    private fun textLayout(node: SemanticsNode): TextLayoutResult? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        val results = mutableListOf<TextLayoutResult>()
        return if (action != null && action(results)) results.singleOrNull() else null
    }

    private fun boundsOf(tag: String): Rect =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** The bounds of every node tagged [tag], or null when there is none. */
    private fun tagged(tag: String): List<Rect>? =
        compose
            .onAllNodesWithTag(tag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .ifEmpty { null }

    private fun assertInside(what: String, inner: Rect, outer: Rect) {
        assertWithMessage("$what: $inner in $outer")
            .that(
                inner.left >= outer.left - PX &&
                    inner.top >= outer.top - PX &&
                    inner.right <= outer.right + PX &&
                    inner.bottom <= outer.bottom + PX,
            )
            .isTrue()
    }

    private fun assertApart(what: String, a: Rect, b: Rect) {
        val overlap = a.intersect(b)
        assertWithMessage("$what: overlap $overlap")
            .that(overlap.width <= PX || overlap.height <= PX)
            .isTrue()
    }

    private fun playToOrigin(rig: ScreenRig, direction: Direction): Long {
        show(rig)
        compose.mainClock.autoAdvance = false
        probe.originMs = null
        rig.vm.onMove(direction)
        return advanceToOrigin()
    }

    /** Each card button tagged in [tags] is enabled (no `Disabled` semantics) iff [enabled]. */
    private fun assertCardButtons(tags: List<String>, enabled: Boolean, at: String) {
        for (tag in tags) {
            val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config
            assertWithMessage("$tag at $at: enabled")
                .that(SemanticsProperties.Disabled !in config)
                .isEqualTo(enabled)
        }
    }

    /**
     * A swipe on the board, an arrow key and a tap on the scrim beside the card (F03-AC16,
     * F04-AC7); each is followed by a settle.
     */
    private fun assertBoardInputBlocked(rig: ScreenRig) {
        val moveId = rig.vm.board.value.moveId
        compose.onNodeWithTag(BOARD_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag(BOARD_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithTag(SCRIM_TAG).performTouchInput { click(topLeft + Offset(2f, 2f)) }
        compose.waitForIdle()
        assertWithMessage("the board after a swipe, an arrow and a scrim tap")
            .that(rig.vm.board.value.moveId)
            .isEqualTo(moveId)
    }

    /**
     * F03-AC18, F04-AC14: [game]'s card [cardTag] under Motion Off in each window of the criteria
     * (the window as the arrangement's constraints, at the Pixel 7 density, in a host larger than
     * every window so no bound is clipped; D-244 adds the landscape one): inside the board inset by
     * 8 dp, not in a scroll container.
     */
    private fun assertCardFitsBoard(game: SavedGame, cardTag: String) {
        val settings = SETTINGS.copy(motion = MotionPreference.OFF, lastBoardSize = 4)
        val rigs = FIT_WINDOWS.associateWith { ScreenRig(tmp.newFolder(), game, settings) }
        var shown by mutableStateOf(FIT_WINDOWS.first())
        compose.setContent {
            key(shown) {
                Box(Modifier.requiredSize(shown)) { RigContent(rigs.getValue(shown)) }
            }
        }
        try {
            for ((window, rig) in rigs) {
                shown = window
                rig.awaitReady(compose)
                compose.waitForIdle()
                val inset = with(compose.density) { CARD_BOARD_INSET.toPx() }
                val board = compose.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot
                val card = compose.onNodeWithTag(cardTag).fetchSemanticsNode().boundsInRoot
                val label = "$cardTag in ${window.width}×${window.height}"
                assertWithMessage("$label: left").that(card.left).isAtLeast(board.left + inset - PX)
                assertWithMessage("$label: top").that(card.top).isAtLeast(board.top + inset - PX)
                assertWithMessage("$label: right")
                    .that(card.right)
                    .isAtMost(board.right - inset + PX)
                assertWithMessage("$label: bottom")
                    .that(card.bottom)
                    .isAtMost(board.bottom - inset + PX)
                compose
                    .onAllNodes(
                        hasAnyDescendant(hasTestTag(cardTag)) and
                            SemanticsMatcher.keyIsDefined(
                                SemanticsProperties.VerticalScrollAxisRange,
                            ),
                    )
                    .assertCountEquals(0)
            }
        } finally {
            rigs.values.forEach { it.close() }
        }
    }

    /** The next started activity is the share chooser over `text/plain` [text] (F10). */
    private fun assertChooser(text: String) {
        val app = ApplicationProvider.getApplicationContext<Game2048Application>()
        val chooser = checkNotNull(shadowOf(app).nextStartedActivity) { "no activity started" }
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
            .isEqualTo("Share result")
        val send =
            checkNotNull(
                IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java),
            ) {
                "the chooser wraps no intent"
            }
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("text/plain")
        assertThat(send.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(text)
    }

    /** `FIX_OVER_4` with [undos] undos used. */
    private fun overWithUndos(undos: Int): SavedGame =
        fixtureGame(
            n = 4,
            cells = List(BoardSize.DEFAULT.cellCount) { GoldenFixtures.FIX_OVER_4.state.board[it] },
            score = 3012,
            best = 24680,
            moves = 302,
            undos = undos,
            elapsedMs = 401_000,
            status = GameStatus.OVER,
        )

    /**
     * A pixel of [image]'s button container on its vertical centre, a tenth of its width in, clear
     * of the centred label.
     */
    private fun labelFreePixel(image: ImageBitmap): Color =
        image.toPixelMap()[image.width / LABEL_FREE_DIVISOR, image.height / 2]

    /** The RGB distance of [a] and [b]. */
    private fun distance(a: Color, b: Color): Float {
        val r = a.red - b.red
        val g = a.green - b.green
        val bl = a.blue - b.blue
        return sqrt(r * r + g * g + bl * bl)
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

    /**
     * A rig over [game] in a new folder, with [settings] and, when given, [records] and the saved
     * games of other sizes in [others]; [holdRecords] as `ScreenRig`'s.
     */
    private fun rig(
        game: SavedGame,
        settings: Settings = SETTINGS.copy(lastBoardSize = game.state.size.n),
        records: Records? = null,
        others: List<SavedGame> = emptyList(),
        holdRecords: CountDownLatch? = null,
    ) =
        ScreenRig(
            tmp.newFolder(),
            game,
            settings,
            records,
            others = others,
            holdRecords = holdRecords,
        )

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
            TestApp(rig.container, rig.vm)
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
@Suppress("LongParameterList") // one parameter per seeded file and per load seam (QUALITY.md §1.6)
private class ScreenRig(
    folder: File,
    game: SavedGame?,
    settings: Settings = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE),
    records: Records? = null,
    seed: Boolean = true,
    /** Saved games of other sizes, written with [game] before the ViewModel exists. */
    others: List<SavedGame> = emptyList(),
    /**
     * When given (with no [records]), the records store's first read waits on its I/O thread until
     * the latch opens, so the ViewModel stays not ready until then.
     */
    holdRecords: CountDownLatch? = null,
) : AutoCloseable {
    val clock = FakeClock()
    /** The system animator duration scale the container's effective motion level reads. */
    val scale = FakeAnimatorScaleSource()
    val container =
        AppContainer(
            files = { name ->
                if (name == DataStoreFiles.RECORDS) {
                    holdRecords?.await(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }
                File(folder, name)
            },
            versionCode = TEST_VERSION_CODE,
            clock = clock,
            seedSource = FixedSeedSource(RIG_SEED),
            animatorScale = scale,
        )
    private val size = game?.state?.size ?: BoardSize.of(settings.lastBoardSize)
    private val store = ViewModelStore()
    val vm: GameViewModel

    init {
        if (seed) {
            runBlocking {
                container.seedSettings(settings)
                game?.let { container.seedGame(it) }
                for (other in others) container.seedGame(other)
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

/** Keep going, New game, Undo and Share: every win-card button (D-200). */
private val WIN_BUTTONS = listOf(KEEP_GOING_TAG, CARD_NEW_GAME_TAG, CARD_UNDO_TAG, CARD_SHARE_TAG)

/** New game, Undo and Share: every game-over-card button (D-200). */
private val OVER_BUTTONS = listOf(CARD_NEW_GAME_TAG, CARD_UNDO_TAG, CARD_SHARE_TAG)

/** The last test frame before, and the first at, the cards' interactive flips (§3.11, §3.13). */
private const val WIN_LOCKED_FULL_MS = 864L
private const val WIN_INTERACTIVE_FULL_MS = 880L
private const val OVER_LOCKED_FULL_MS = 992L
private const val OVER_INTERACTIVE_FULL_MS = 1008L
private const val REDUCED_LOCKED_MS = 144L
private const val REDUCED_INTERACTIVE_MS = 160L

/** How long F03-AC12 watches for confetti after the winning move. */
private const val CARD_WATCH_MS = 1_000L

/** M3's disabled container: `onSurface` at 12 % (F03-AC19). */
private const val DISABLED_CONTAINER_ALPHA = 0.12f

/** [GameScreenTest.labelFreePixel]'s x: a tenth of the button's width. */
private const val LABEL_FREE_DIVISOR = 10

/** The windows of F03-AC18 and F04-AC14 (D-244): two portrait, and THREE_COLUMN's landscape. */
private val FIT_WINDOWS =
    listOf(DpSize(360.dp, 640.dp), DpSize(360.dp, 800.dp), DpSize(915.dp, 412.dp))

/** A host larger than every window of [FIT_WINDOWS], at the Pixel 7's 420 dpi. */
private const val FIT_HOST = "w1000dp-h1000dp-420dpi"

/** How far inside the board the card stays (D-200). */
private val CARD_BOARD_INSET = 8.dp

/** Half a pixel of layout rounding. */
private const val PX = 0.5f

private const val SHARE = "Share"

/** `GoldenFixtures.FIX_RECORDS`' 4×4 best score. */
private const val FIX_RECORDS_BEST_4 = 24_680L

/** The share emoji of FEATURES.md F10 (each one code point). */
private val EMPTY = String(Character.toChars(0x2B1C))
private val BROWN = String(Character.toChars(0x1F7EB))
private val YELLOW = String(Character.toChars(0x1F7E8))
private val ORANGE = String(Character.toChars(0x1F7E7))
private val BLUE = String(Character.toChars(0x1F7E6))

/** F17-AC23: an 8×8 record game's longest Score and Best (D-188). */
private const val LONG_SCORE = 999_999_999L
private const val LONG_BEST = 9_999_999_999L

/** Each chip's tag, its digits and its spoken description (F17-AC23). */
private val LONG_CHIPS =
    listOf(
        Triple(SCORE_TAG, "999999999", "Score 999999999"),
        Triple(BEST_TAG, "9999999999", "Best 9999999999"),
    )

private val NARROW_WIDTH = 360.dp
private val RAIL_WINDOW = DpSize(700.dp, 450.dp)
private val LONG_SCORE_WINDOWS =
    listOf(
        DpSize(NARROW_WIDTH, 800.dp),
        DpSize(NARROW_WIDTH, 450.dp),
        RAIL_WINDOW,
        DpSize(915.dp, 412.dp),
    )
private const val LARGE_FONT_SCALE = 2f
private val FONT_SCALES = listOf(1f, LARGE_FONT_SCALE)

/** DESIGN_SYSTEM.md §7.2: the fitted digit sizes. */
private const val SCORE_DIGITS_MIN_SP = 14f
private const val SCORE_DIGITS_MAX_SP = 28f

/** DESIGN_SYSTEM.md §5.3: the board floor and the 700 dp window's rail. */
private const val BOARD_FLOOR_DP = 220f
private const val RAIL_WINDOW_RAIL_DP = 160f
private const val DP_EPSILON = 0.01f

/** The chrome that must lie inside the root and off the board outside SCROLL (F17-AC23). */
private val CHROME_TAGS =
    listOf(
        HEADER_TAG,
        SCORE_TAG,
        BEST_TAG,
        SIZE_CHIP_TAG,
        TITLE_TAG,
        MORE_TAG,
        CONTROLS_TAG,
        UNDO_TAG,
        NEW_GAME_TAG,
    )

/** The node carrying the resolved arrangement (`GameLayout`). */
private val HAS_LAYOUT_SPEC = SemanticsMatcher.keyIsDefined(LayoutSpecKey)
private val CONTENT_DESCRIPTION = SemanticsProperties.ContentDescription

/** The box that stands for a window in the layout tests. */
private const val WINDOW_TAG = "test:window"

/** F18-AC16: the controls bar's windows, and the Undo label. */
private val LABEL_WINDOWS = listOf(DpSize(NARROW_WIDTH, 800.dp), DpSize(411.dp, 914.dp))
private const val UNDO_LABEL = "Undo"

/** F05-AC17: the window and its font scales. */
private val SNACKBAR_WINDOW = DpSize(360.dp, 640.dp)
private val SNACKBAR_FONT_SCALES = listOf(1f, 1.3f)

/** How far inside the board a snackbar over it stays (D-186). */
private val SNACKBAR_BOARD_INSET = 8.dp

/** DESIGN_SYSTEM.md §7.2: the score chip's horizontal padding on each side. */
private val CHIP_PADDING = 12.dp

/**
 * F17-AC24: an 8×8 `OVER` game at Score 9,999,999,999 (cells alternate 2 and 4, so no neighbours
 * are equal), with the size's record Best of the same value.
 */
private val LONG_OVER_8: SavedGame =
    fixtureGame(
        n = 8,
        cells = List(64) { index -> if ((index / 8 + index % 8) % 2 == 0) 1 else 2 },
        score = LONG_BEST,
        best = LONG_BEST,
        moves = 1_000,
        undos = 0,
        elapsedMs = 0,
        status = GameStatus.OVER,
    )

/** F17-AC24's windows: the Regular card's and the Compact card's. */
private val COMPACT_CARD_HEIGHT = 450.dp
private val CARD_WINDOWS =
    listOf(DpSize(NARROW_WIDTH, 800.dp), DpSize(NARROW_WIDTH, COMPACT_CARD_HEIGHT))

/** DESIGN_SYSTEM.md §6.4, §6.4a: the card's largest value size and its boxes'. */
private const val CARD_DIGITS_MAX_SP = 40f
private const val BOX_DIGITS_MAX_SP = 24f

/** F18-AC10's split-screen windows. */
private val SHORT_WINDOWS = listOf(DpSize(360.dp, 450.dp), DpSize(411.dp, 450.dp))

/** F18-AC10's boards with DESIGN_SYSTEM.md §5.3's nominal chrome (64 dp header, 80 dp bar). */
private const val STACKED_BOARD_DP = 258f
private const val SCROLL_BOARD_DP = 298f

/**
 * The measured COMPACT header may differ from the nominal 64 dp by the chips' rounding and by the
 * bundled numerals' score line box, 1.252 em against the M3 numerals' 1.172 em (D-251).
 */
private const val NOMINAL_SLACK_DP = 4f

/**
 * A short window's margins with zero insets (DESIGN_SYSTEM.md §5.2: under 480 dp tall, 600 wide).
 */
private const val SHORT_MARGIN_DP = 16f

/** The COMPACT header's gaps (`Space8`, DESIGN_SYSTEM.md §5.3). */
private const val COMPACT_GAP_DP = 8f

/** One device pixel at the Pixel 7's density (2.625), in dp, for a rounded node size. */
private const val PX_DP = 0.5f

/**
 * F18-AC12: `FIX_WON_4` with row 1 = 1024, 1024, 2, empty and status `PLAYING`, so LEFT makes 2048.
 */
private val WIN_NEXT_LEFT_4: SavedGame =
    fixtureGame(
        n = 4,
        cells = listOf(10, 10, 1, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 2),
        score = 18_076,
        best = 24_680,
        moves = 979,
        undos = 0,
        elapsedMs = 1_275_000,
        status = GameStatus.PLAYING,
    )

/** A move count that is a multiple of 32: moves played from such a fixture undo back to it. */
private const val UNDOABLE_MOVES = 416

/** DESIGN_SYSTEM.md §10.4 row 44: a version seen before this one, so What's new shows. */
private const val SEEN_BEFORE_UPDATE = 20000
