package com.clayboicardi.game2048.game

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.R
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
import com.clayboicardi.game2048.game.board.verticalMargin
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
import com.clayboicardi.game2048.testing.assertTouchTargets
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.formatCount
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
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

    /** F17-AC25 */
    @Test
    @Config(qualifiers = "de-w360dp-h640dp-xxhdpi")
    fun longStatusLineKeepsBoardInPlace() {
        // Rows 1–3 = 32 32 16 16, 8 8 4 4, 2 2 0 0 at score 123,456: LEFT creates 64, 32, 16, 8
        // and 4, so the German status names three merges and "2 weiteren" and needs three lines
        // at the 328 dp content width (the owner review of D-265). Best stays above the score, so
        // no new-best moment changes the header.
        val cells = listOf(5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0)
        val game = game(cells = cells, score = 123_456, best = 234_567)
        setTouchExploration(true)
        rig(game).use { rig ->
            show(rig)
            val before = boundsOf(BOARD_TAG)

            rig.vm.onMove(Direction.LEFT)
            compose.waitForIdle()

            assertThat(statusText()).contains(" und 2 weiteren. ")
            val status = compose.onNodeWithTag(STATUS_LINE_TAG).fetchSemanticsNode()
            assertWithMessage("the status line's lines")
                .that(checkNotNull(textLayout(status)).lineCount)
                .isAtLeast(LONG_STATUS_LINES)
            val board = boundsOf(BOARD_TAG)
            assertWithMessage("the board after the move").that(board).isEqualTo(before)
            val spec = compose.onNode(HAS_LAYOUT_SPEC).fetchSemanticsNode().config[LayoutSpecKey]
            assertWithMessage("the board (px) against $spec")
                .that(board.width.roundToInt())
                .isEqualTo(with(compose.density) { spec.board.dp.roundToPx() })
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val margin = with(compose.density) { verticalMargin(WINDOW_HEIGHT).toPx() }
            for (tag in listOf(STATUS_LINE_TAG, PAD_TAG, NEW_GAME_TAG)) {
                val chrome = boundsOf(tag)
                val overlap = chrome.intersect(board)
                assertWithMessage("$tag ($chrome) and the board ($board): overlap $overlap")
                    .that(overlap.width <= PX || overlap.height <= PX)
                    .isTrue()
                assertWithMessage("$tag ($chrome) inside the available area of $root")
                    .that(chrome.bottom)
                    .isAtMost(root.bottom - margin + PX)
            }
        }
    }

    /** F17-AC26 */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun reservedStatusFitsEveryAnnouncementInEveryLocale() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        var shown: String? by mutableStateOf(null)
        val reserved = mutableMapOf<Int, Int>()
        lateinit var measurer: TextMeasurer
        lateinit var style: TextStyle
        compose.setContent {
            GameTheme(darkTheme = false, motion = MotionPreference.OFF) {
                measurer = rememberTextMeasurer(cacheSize = 0)
                style = MaterialTheme.typography.bodyMedium
                shown?.let { tag ->
                    key(tag) { Localized(app, tag) { ReservedLines { d, px -> reserved[d] = px } } }
                }
            }
        }
        val width = with(compose.density) { STATUS_WIDTH.roundToPx() }
        for (tag in LOCALES) {
            reserved.clear()
            shown = tag
            compose.waitForIdle()
            val resources = localized(app, tag).resources
            val locale = Locale.forLanguageTag(tag)
            val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            for (digits in RESERVED_DIGITS) {
                val height = checkNotNull(reserved[digits]) { "$tag: no reservation for $digits" }
                for (text in announcements(resources, locale, digits)) {
                    val layout =
                        measurer.measure(
                            text,
                            style,
                            constraints = Constraints(maxWidth = width),
                            layoutDirection = direction,
                        )
                    assertWithMessage(
                            "$tag, $digits digits: \"$text\" (${layout.lineCount} lines) " +
                                "in the reserved $height px",
                        )
                        .that(layout.size.height)
                        .isAtMost(height)
                }
            }
        }
    }

    /** F17-AC27 */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi", fontScale = 2.0f)
    fun padNewGameLabelFitsAtFontScale2() {
        assertPadNewGameLabelFits("New game")
    }

    /** F17-AC27 */
    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-xxhdpi", fontScale = 2.0f)
    fun padNewGameLabelFitsAtFontScale2InFrench() {
        assertPadNewGameLabelFits("Nouvelle partie")
    }

    /**
     * With touch exploration on, the pad row's New game shows [label] whole (F17-AC27, D-266): laid
     * out again in the box it is drawn in, on at most two lines, with no visual overflow and no
     * word broken across lines; it keeps a 48 dp touch target and overlaps no other clickable or
     * the pad.
     */
    private fun assertPadNewGameLabelFits(label: String) {
        setTouchExploration(true)
        rig(GoldenFixtures.FIX_MID_4).use { rig ->
            show(rig)
            if (compose.onAllNodes(HAS_SCROLL).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag(NEW_GAME_TAG).performScrollTo()
                compose.waitForIdle()
            }

            val node =
                compose
                    .onNode(
                        hasAnyAncestor(hasTestTag(NEW_GAME_TAG)) and hasText(label),
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNode()
            val drawn = drawnLayout(node)
            val text = drawn.layoutInput.text.text
            val lines =
                (0 until drawn.lineCount).map { line ->
                    text.substring(drawn.getLineStart(line), drawn.getLineEnd(line))
                }
            assertWithMessage("\"$label\" in ${node.size}: lines $lines")
                .that(drawn.hasVisualOverflow)
                .isFalse()
            assertWithMessage("\"$label\": lines").that(lines.size).isAtMost(2)
            assertWithMessage("\"$label\": the words of its lines")
                .that(lines.joinToString(" ") { it.trim() })
                .isEqualTo(label)
            val newGame = boundsOf(NEW_GAME_TAG)
            val pad = boundsOf(PAD_TAG)
            val overlap = newGame.intersect(pad)
            assertWithMessage("New game ($newGame) and the pad ($pad): overlap $overlap")
                .that(overlap.width <= PX || overlap.height <= PX)
                .isTrue()
            compose.assertTouchTargets()
        }
    }

    /**
     * F17 item 5's announcements of a game whose larger of Score and Best has [digits] digits, from
     * [resources] for [locale], written out here from the specification: each move direction with 0
     * to 32 merges of the largest tile below `10^digits` (an 8×8 move merges at most 32 pairs) and
     * a spawned 2 or 4 at row 1, column 1 or row 8, column 8, at the largest score of [digits]
     * digits; Move undone; New game with two start tiles; every size switch; every blocked move.
     */
    private fun announcements(resources: Resources, locale: Locale, digits: Int): List<String> {
        val top = (1..digits).fold(0L) { value, _ -> value * 10 + 9 }
        val score = formatCount(top, locale)
        val tile = formatCount(top.takeHighestOneBit(), locale)
        val corners = listOf(1 to 1, BoardSize.MAX_N to BoardSize.MAX_N)
        val spawns =
            listOf("2", "4").flatMap { value ->
                corners.map { (row, column) ->
                    resources.getString(
                        R.string.a11y_spawned,
                        value,
                        row.toString(),
                        column.toString(),
                    )
                }
            }
        val merges = (0..MAX_MERGES).map { count -> mergedSentence(resources, locale, tile, count) }
        val moved = MOVED.flatMap { moved ->
            merges.flatMap { merged ->
                spawns.map { spawned ->
                    listOfNotNull(
                            resources.getString(moved),
                            merged,
                            spawned,
                            resources.getString(R.string.a11y_score, score),
                        )
                        .joinToString(" ")
                }
            }
        }
        val started = resources.getString(R.string.a11y_new_game)
        val newGame = spawns.flatMap { first ->
            spawns.map { second -> listOf(started, first, second).joinToString(" ") }
        }
        val sizes =
            (BoardSize.MIN_N..BoardSize.MAX_N).map { n ->
                resources.getString(R.string.a11y_size_switched, "$n×$n", score)
            }
        return moved +
            newGame +
            sizes +
            resources.getString(R.string.a11y_undone, score) +
            BLOCKED.map { resources.getString(it) }
    }

    /** `a11y_merged_*` for [count] created tiles of the prose value [tile]; null for none. */
    private fun mergedSentence(
        resources: Resources,
        locale: Locale,
        tile: String,
        count: Int,
    ): String? =
        when (count) {
            0 -> null
            1 -> resources.getString(R.string.a11y_merged_1, tile)
            2 -> resources.getString(R.string.a11y_merged_2, tile, tile)
            NAMED_MERGES -> resources.getString(R.string.a11y_merged_3, tile, tile, tile)
            else ->
                resources.getQuantityString(
                    R.plurals.a11y_merged_more,
                    count - NAMED_MERGES,
                    tile,
                    tile,
                    tile,
                    formatCount((count - NAMED_MERGES).toLong(), locale),
                )
        }

    /** The probe's status reservation for each of [RESERVED_DIGITS], in px, to [onMeasured]. */
    @Composable
    private fun ReservedLines(onMeasured: (digits: Int, px: Int) -> Unit) {
        Column {
            for (digits in RESERVED_DIGITS) {
                Box(Modifier.width(STATUS_WIDTH).onSizeChanged { onMeasured(digits, it.height) }) {
                    ReservedStatusLine(digits)
                }
            }
        }
    }

    /** [content] in the resources, configuration and layout direction of the locale [tag]. */
    @Composable
    private fun Localized(app: Context, tag: String, content: @Composable () -> Unit) {
        val context = remember(tag) { localized(app, tag) }
        val configuration = context.resources.configuration
        val rtl = configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides configuration,
            LocalResources provides context.resources,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content,
        )
    }

    private fun localized(app: Context, tag: String): Context {
        val configuration =
            Configuration(app.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
        return app.createConfigurationContext(configuration)
    }

    /** [node]'s text laid out again in the box it is drawn in (as `GameScreenTest` does). */
    private fun drawnLayout(node: SemanticsNode): TextLayoutResult {
        val input = checkNotNull(textLayout(node)) { "no text layout" }.layoutInput
        val box = Constraints(maxWidth = node.size.width, maxHeight = node.size.height)
        return TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, 0)
            .measure(
                input.text,
                input.style,
                input.overflow,
                input.softWrap,
                input.maxLines,
                input.placeholders,
                box,
            )
    }

    /** [node]'s text layout as its `GetTextLayoutResult` action reports it, if it has one. */
    private fun textLayout(node: SemanticsNode): TextLayoutResult? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        val results = mutableListOf<TextLayoutResult>()
        return if (action != null && action(results)) results.singleOrNull() else null
    }

    private fun boundsOf(tag: String): Rect =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

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

    /**
     * A 4×4 `PLAYING` game on [cells] at [score] with [best], 32 moves (so its own snapshot is the
     * state).
     */
    private fun game(cells: List<Int>, score: Long, best: Long = score): SavedGame =
        fixtureGame(
            n = 4,
            cells = cells,
            score = score,
            best = best,
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

/** The board's test tag (`game/board/Board.kt`). */
private const val BOARD_TAG = "game:board"

/** The root of the arrangement drawn carries the resolved `GameLayoutSpec`. */
private val HAS_LAYOUT_SPEC = SemanticsMatcher.keyIsDefined(LayoutSpecKey)

/** A scroll container: the SCROLL arrangement's column. */
private val HAS_SCROLL = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)

/** F17-AC25's window height: `de-w360dp-h640dp`. */
private val WINDOW_HEIGHT = 640.dp

/** F17-AC25's announcement wraps to at least three lines at 328 dp in German. */
private const val LONG_STATUS_LINES = 3

/** Half a pixel: the rounding allowed between two placed bounds. */
private const val PX = 0.5f

/** en (the default resources) and the 20 translations (D-100). */
private val LOCALES =
    listOf(
        "en",
        "cs",
        "da",
        "de",
        "el",
        "eo",
        "es",
        "fa",
        "fr",
        "hu",
        "it",
        "ko",
        "lt",
        "nl",
        "pl",
        "pt",
        "ru",
        "sl",
        "sr",
        "tr",
        "uk",
    )

/** F17-AC26's digit counts of the larger of Score and Best: a mid game and a record game. */
private val RESERVED_DIGITS = listOf(5, 10)

/** The content width of a 360 dp window (16 dp margins): the STACKED COMPACT status line's. */
private val STATUS_WIDTH = 328.dp

/** An 8×8 move merges at most 4 pairs in each of its 8 lines. */
private const val MAX_MERGES = 32

/** `a11y_merged_more` names three merges before it counts the rest. */
private const val NAMED_MERGES = 3

private val MOVED =
    listOf(
        R.string.a11y_moved_up,
        R.string.a11y_moved_down,
        R.string.a11y_moved_left,
        R.string.a11y_moved_right,
    )

private val BLOCKED =
    listOf(
        R.string.a11y_blocked_up,
        R.string.a11y_blocked_down,
        R.string.a11y_blocked_left,
        R.string.a11y_blocked_right,
    )

/** Frames watched after each blocked move: the nudge and the status line settle well within. */
private const val ATTEMPT_FRAMES = 30
