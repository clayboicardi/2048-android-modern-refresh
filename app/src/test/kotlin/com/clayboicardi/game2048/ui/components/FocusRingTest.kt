package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.statsViewModelFactory
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameMoreMenu
import com.clayboicardi.game2048.menuItemTag
import com.clayboicardi.game2048.stats.StatsScreen
import com.clayboicardi.game2048.stats.StatsViewModel
import com.clayboicardi.game2048.stats.statsTabTag
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
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
 * The focus ring of the interactive list rows, the Statistics tabs and the game menu's items
 * (DESIGN_SYSTEM.md §7.7; D-084, D-227, D-267, D-269): each, focused in keyboard input mode, draws
 * the 2 dp `primary` ring outside its bounds, and a row draws none in touch mode. Light Classic,
 * Motion Off.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FocusRingTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F16-AC12 */
    @Test
    fun listRowsDrawFocusRingInKeyboardMode() {
        val focus = ROWS.associateWith { FocusRequester() }
        lateinit var inputMode: InputModeManager
        var primary = Color.Unspecified
        var background = Color.Unspecified
        compose.setContent {
            GameTheme(darkTheme = false, motion = MotionPreference.OFF) {
                inputMode = LocalInputModeManager.current
                primary = MaterialTheme.colorScheme.primary
                background = MaterialTheme.colorScheme.background
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(GameDimens.Space16),
                    verticalArrangement = Arrangement.spacedBy(GameDimens.Space16),
                ) {
                    for (row in ROWS) {
                        Row(row, Modifier.testTag(row).focusRequester(focus.getValue(row)))
                    }
                }
            }
        }

        // Touch mode (the test's start, like a touch player's): a focused row draws no ring.
        compose.runOnIdle { focus.getValue(ROWS.first()).requestFocus() }
        compose.waitForIdle()
        assertRing(ROWS.first(), primary, background, drawn = false, at = "touch mode")

        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.waitForIdle()
        assertWithMessage("input mode")
            .that(compose.runOnIdle { inputMode.inputMode })
            .isEqualTo(InputMode.Keyboard)
        for (row in ROWS) {
            compose.runOnIdle { focus.getValue(row).requestFocus() }
            compose.waitForIdle()
            for (other in ROWS) {
                assertRing(other, primary, background, drawn = other == row, at = "$row focused")
            }
        }
    }

    /** F16-AC12 */
    @Test
    fun choiceDialogRowsDrawFocusRingInKeyboardMode() {
        // The dialog is a window of its own with its own input mode, read inside it.
        lateinit var inputMode: InputModeManager
        var primary = Color.Unspecified
        var surface = Color.Unspecified
        compose.setContent {
            GameTheme(darkTheme = false, motion = MotionPreference.OFF) {
                primary = MaterialTheme.colorScheme.primary
                surface = MaterialTheme.colorScheme.surfaceContainerHigh
                ChoiceDialog(
                    title = "Theme",
                    options = persistentListOf(Choice(0, "One"), Choice(1, "Two")),
                    selected = 0,
                    onSelect = {},
                    onDismiss = {},
                    optionExtra = { inputMode = LocalInputModeManager.current },
                )
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.waitForIdle()

        for (index in 0..1) {
            compose
                .onNodeWithTag(choiceOptionTag(index))
                .performSemanticsAction(SemanticsActions.RequestFocus)
            compose.waitForIdle()
            val pixels = compose.onNode(isDialog()).captureToImage().toPixelMap()
            val origin = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.topLeft
            val row =
                compose
                    .onNodeWithTag(choiceOptionTag(index))
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .translate(-origin)
            // The rows are stacked, so the ring's sides (left and right of the row) are checked;
            // the dialog's container is under them.
            for ((side, point) in ringPoints(row).filterKeys { it == LEFT || it == RIGHT }) {
                val pixel = pixels[point.first, point.second]
                assertWithMessage("option $index focused: the ring's $side edge at $point ($pixel)")
                    .that(pixels.near(point, primary))
                    .isTrue()
            }
            val other = 1 - index
            val otherRow =
                compose
                    .onNodeWithTag(choiceOptionTag(other))
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .translate(-origin)
            for ((side, point) in ringPoints(otherRow).filterKeys { it == LEFT || it == RIGHT }) {
                assertWithMessage("option $index focused: option $other's $side edge at $point")
                    .that(pixels.near(point, surface))
                    .isTrue()
            }
        }
    }

    /** F16-AC12 */
    @Test
    fun listRowRingDrawsOverNextRow() {
        // Full-width rows on their opaque containers, as in Settings: the focused row's ring is
        // drawn over the row below it, not covered by it (D-267). Its sides are off the window.
        val focus = FocusRequester()
        lateinit var inputMode: InputModeManager
        var primary = Color.Unspecified
        compose.setContent {
            GameTheme(darkTheme = false, motion = MotionPreference.OFF) {
                inputMode = LocalInputModeManager.current
                primary = MaterialTheme.colorScheme.primary
                LazyColumn {
                    items(LIST_ROWS) { index ->
                        val tagged = Modifier.testTag(listRowTag(index))
                        SettingsChoiceRow(
                            headline = "Row $index",
                            summary = "Summary",
                            onClick = {},
                            modifier = if (index == 1) tagged.focusRequester(focus) else tagged,
                        )
                    }
                }
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.runOnIdle { focus.requestFocus() }
        compose.waitForIdle()

        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = compose.onNodeWithTag(listRowTag(1)).fetchSemanticsNode().boundsInRoot
        for ((side, point) in ringPoints(row).filterKeys { it == TOP || it == BOTTOM }) {
            val pixel = pixels[point.first, point.second]
            assertWithMessage("row 1 focused: the ring's $side edge at $point ($pixel)")
                .that(pixels.near(point, primary))
                .isTrue()
        }
    }

    /** F16-AC13 */
    @Test
    fun statisticsTabDrawsFocusRingInKeyboardMode() {
        StatsRig(tmp.newFolder()).use { rig ->
            lateinit var inputMode: InputModeManager
            var primary = Color.Unspecified
            compose.setContent {
                GameTheme(darkTheme = false, motion = MotionPreference.OFF) {
                    inputMode = LocalInputModeManager.current
                    primary = MaterialTheme.colorScheme.primary
                    StatsScreen(rig.vm, onBack = {})
                }
            }
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                rig.vm.ui.value != null
            }
            compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
            compose
                .onNodeWithTag(statsTabTag(1))
                .performSemanticsAction(SemanticsActions.RequestFocus)
            compose.waitForIdle()

            // The tab row clips its tabs to its height, so the ring's sides show (D-269).
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val tab = compose.onNodeWithTag(statsTabTag(1)).fetchSemanticsNode().boundsInRoot
            for ((side, point) in ringPoints(tab).filterKeys { it == LEFT || it == RIGHT }) {
                val pixel = pixels[point.first, point.second]
                assertWithMessage("tab 1 focused: the ring's $side edge at $point ($pixel)")
                    .that(pixels.near(point, primary))
                    .isTrue()
            }
        }
    }

    /** F16-AC13 */
    @Test
    fun gameMenuItemDrawsFocusRingInKeyboardMode() {
        var open by mutableStateOf(false)
        var primary = Color.Unspecified
        compose.setContent {
            GameTheme(darkTheme = false, motion = MotionPreference.OFF) {
                primary = MaterialTheme.colorScheme.primary
                Box { GameMoreMenu(menuOpen = open, onMenuOpenChange = {}, onNavigate = {}) }
            }
        }
        // The menu is a window of its own: it opens out of touch mode, as after a key press.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.setInTouchMode(false)
        try {
            open = true
            compose.waitForIdle()
            val item = menuItemTag(SettingsKey)
            compose.onNodeWithTag(item).performSemanticsAction(SemanticsActions.RequestFocus)
            compose.waitForIdle()

            // The menu clips its full-width items at its sides, so the ring's top and bottom
            // edges show (D-269).
            val popup = compose.onNode(isPopup())
            val pixels = popup.captureToImage().toPixelMap()
            val origin = popup.fetchSemanticsNode().boundsInRoot.topLeft
            val row =
                compose.onNodeWithTag(item).fetchSemanticsNode().boundsInRoot.translate(-origin)
            for ((side, point) in ringPoints(row).filterKeys { it == TOP || it == BOTTOM }) {
                val pixel = pixels[point.first, point.second]
                assertWithMessage("Settings focused: the ring's $side edge at $point ($pixel)")
                    .that(pixels.near(point, primary))
                    .isTrue()
            }
        } finally {
            instrumentation.setInTouchMode(true)
        }
    }

    /**
     * Whether the ring of the row tagged [tag] is [drawn]: the pixel at the middle of each of its
     * four edges, on the ring's centre line, is [primary] when drawn and [background] otherwise.
     */
    private fun assertRing(
        tag: String,
        primary: Color,
        background: Color,
        drawn: Boolean,
        at: String,
    ) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val row = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val expected = if (drawn) primary else background
        for ((side, point) in ringPoints(row)) {
            assertWithMessage("$at: $tag's ring $side edge at $point (drawn: $drawn)")
                .that(pixels.near(point, expected))
                .isTrue()
        }
    }

    /** The middle of each edge of the ring around [row], on the stroke's centre line, in px. */
    private fun ringPoints(row: Rect): Map<String, Pair<Int, Int>> {
        val out =
            with(compose.density) {
                (GameDimens.FocusRingGap + GameDimens.FocusRingWidth / 2).toPx()
            }
        val x = row.center.x.roundToInt()
        val y = row.center.y.roundToInt()
        return mapOf(
            TOP to (x to (row.top - out).roundToInt()),
            BOTTOM to (x to (row.bottom + out).roundToInt()),
            LEFT to ((row.left - out).roundToInt() to y),
            RIGHT to ((row.right + out).roundToInt() to y),
        )
    }

    /** Whether the pixel at [point] is [color] within [COLOR_TOLERANCE] per channel. */
    private fun PixelMap.near(point: Pair<Int, Int>, color: Color): Boolean {
        val pixel = this[point.first, point.second]
        return abs(pixel.red - color.red) <= COLOR_TOLERANCE &&
            abs(pixel.green - color.green) <= COLOR_TOLERANCE &&
            abs(pixel.blue - color.blue) <= COLOR_TOLERANCE
    }

    /** The row wrapper named [row], with [modifier]. */
    @Composable
    private fun Row(row: String, modifier: Modifier = Modifier) {
        when (row) {
            SWITCH ->
                SettingsSwitchRow(
                    headline = "Switch",
                    summary = "Summary",
                    checked = true,
                    onCheckedChange = {},
                    modifier = modifier,
                )
            CHOICE ->
                SettingsChoiceRow(
                    headline = "Choice",
                    summary = "Summary",
                    onClick = {},
                    modifier = modifier,
                )
            SETTINGS_LINK ->
                SettingsLinkRow(
                    headline = "Link",
                    summary = "Summary",
                    onClick = {},
                    modifier = modifier,
                )
            SELECTABLE ->
                SelectableRow(selected = false, onClick = {}, modifier = modifier) {
                    Text("Selectable")
                }
            LINK -> LinkRow(onClick = {}, modifier = modifier) { Text("Link row") }
            else -> error(row)
        }
    }

    private companion object {
        /** The rows of [listRowRingDrawsOverNextRow]'s list; row 1 is focused. */
        const val LIST_ROWS = 3

        fun listRowTag(index: Int): String = "list:row:$index"

        const val SWITCH = "row:switch"
        const val CHOICE = "row:choice"
        const val SETTINGS_LINK = "row:settingsLink"
        const val SELECTABLE = "row:selectable"
        const val LINK = "row:link"

        /** Every interactive row wrapper of `SettingRows.kt` and `ListRows.kt`. */
        val ROWS = listOf(SWITCH, CHOICE, SETTINGS_LINK, SELECTABLE, LINK)

        const val TOP = "top"
        const val BOTTOM = "bottom"
        const val LEFT = "left"
        const val RIGHT = "right"

        /** Per channel: anti-aliasing at the stroke's centre line stays well within it. */
        const val COLOR_TOLERANCE = 0.06f
    }
}

/** A container over [folder] with the golden records and the `StatsViewModel` of its factory. */
private class StatsRig(folder: File) : AutoCloseable {
    private val container =
        AppContainer(
            files = { name -> File(folder, name) },
            versionCode = TEST_VERSION_CODE,
            clock = FakeClock(),
            seedSource = FixedSeedSource(SEED),
            animatorScale = FakeAnimatorScaleSource(),
        )
    private val store = ViewModelStore()
    val vm: StatsViewModel

    init {
        runBlocking {
            container.seedSettings(
                Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE),
            )
            container.seedRecords(GoldenFixtures.FIX_RECORDS)
        }
        vm = ViewModelProvider(store, container.statsViewModelFactory())[StatsViewModel::class.java]
    }

    override fun close() {
        store.clear()
        container.applicationScope.cancel()
    }

    private companion object {
        const val SEED = 0x2048L
    }
}

/** A bound on the real-thread DataStore load, not a timing under test. */
private const val LOAD_TIMEOUT_MS = 10_000L
