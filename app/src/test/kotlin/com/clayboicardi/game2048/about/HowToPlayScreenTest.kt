package com.clayboicardi.game2048.about

import android.app.Application
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent as NativeKeyEvent
import android.view.View
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.input.KeyAction
import com.clayboicardi.game2048.game.input.KeyMap
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The How to play screen (DESIGN_SYSTEM.md §6.10; FEATURES.md F23; D-009, D-053, D-104), composed
 * alone in the light `GameTheme` on the Pixel 7 window, English unless a test says otherwise.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HowToPlayScreenTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    /** F23-AC1 */
    @Test
    fun sectionsInOrder() {
        show()

        val headings =
            compose
                .onAllNodes(isHeading() and hasAnyAncestor(hasTestTag(HOW_TO_PLAY_CONTENT_TAG)))
                .fetchSemanticsNodes()
                .sortedBy { it.positionInRoot.y }
                .map { texts(it).single() }

        assertThat(headings)
            .containsExactly(
                "Goal",
                "Moving",
                "Merging",
                "New tiles",
                "Board sizes",
                "Undo",
                "Keyboard and controllers",
                "TalkBack",
            )
            .inOrder()
    }

    /** F23-AC2 */
    @Test
    fun goalStatesBothTargets() {
        show()

        val goal = textsOf(htpBodyTag(R.string.htp_goal)).single()

        assertThat(goal).contains("2048")
        assertThat(goal).contains("256")
        assertThat(goal)
            .isEqualTo(
                "Slide the tiles to merge equal numbers and build the 2048 tile. On a 3×3 board " +
                    "the goal is 256.",
            )
    }

    /** F23-AC3 */
    @Test
    fun spawnAndUndoRulesStated() {
        show()

        val newTiles = textsOf(htpBodyTag(R.string.htp_new_tiles)).single()
        val undo = textsOf(htpBodyTag(R.string.htp_undo)).single()

        assertThat(newTiles).contains("nine times out of ten")
        assertThat(undo).contains("also works after game over")
        assertThat(undo).contains("same next tile")
    }

    /** F23-AC4 */
    @Test
    fun boardSizeTableHasSixRows() {
        show()

        val rows = (3..8).map { n -> textsOf(htpSizeRowTag(n)) }

        assertThat(rows)
            .containsExactly(
                listOf("3×3", "Goal 256"),
                listOf("4×4", "Goal 2048"),
                listOf("5×5", "Goal 2048"),
                listOf("6×6", "Goal 2048"),
                listOf("7×7", "Goal 2048"),
                listOf("8×8", "Goal 2048"),
            )
            .inOrder()
        val tops = (3..8).map { n -> topOf(compose.onNodeWithTag(htpSizeRowTag(n))) }
        assertThat(tops).isInStrictOrder()
        compose.onNodeWithTag(htpSizeRowTag(2)).assertDoesNotExist()
        compose.onNodeWithTag(htpSizeRowTag(9)).assertDoesNotExist()
    }

    /** F23-AC5 */
    @Test
    fun keyTableMatchesKeyMap() {
        show()

        val rows = (0 until KEY_ROWS).map { textsOf(htpKeyRowTag(it)) }

        assertThat(rows)
            .containsExactly(
                listOf("Move", "Arrow keys · W A S D · H J K L · D-pad"),
                listOf("Undo", "U · Ctrl+Z · Backspace"),
                listOf("New game", "N · R (asks first while a game is in progress)"),
            )
            .inOrder()
        compose.onNodeWithTag(htpKeyRowTag(KEY_ROWS)).assertDoesNotExist()
        // The keys the table names, and what each does.
        val named =
            mapOf(
                NativeKeyEvent.KEYCODE_DPAD_UP to MOVE,
                NativeKeyEvent.KEYCODE_DPAD_RIGHT to MOVE,
                NativeKeyEvent.KEYCODE_DPAD_DOWN to MOVE,
                NativeKeyEvent.KEYCODE_DPAD_LEFT to MOVE,
                NativeKeyEvent.KEYCODE_W to MOVE,
                NativeKeyEvent.KEYCODE_A to MOVE,
                NativeKeyEvent.KEYCODE_S to MOVE,
                NativeKeyEvent.KEYCODE_D to MOVE,
                NativeKeyEvent.KEYCODE_H to MOVE,
                NativeKeyEvent.KEYCODE_J to MOVE,
                NativeKeyEvent.KEYCODE_K to MOVE,
                NativeKeyEvent.KEYCODE_L to MOVE,
                NativeKeyEvent.KEYCODE_U to UNDO,
                NativeKeyEvent.KEYCODE_DEL to UNDO,
                NativeKeyEvent.KEYCODE_N to NEW_GAME,
                NativeKeyEvent.KEYCODE_R to NEW_GAME,
            )
        val unmodified =
            (1..NativeKeyEvent.getMaxKeyCode())
                .mapNotNull { code -> KeyMap.actionFor(down(code))?.let { code to kind(it) } }
                .toMap()
        val withCtrl =
            (1..NativeKeyEvent.getMaxKeyCode())
                .mapNotNull { code -> KeyMap.actionFor(down(code, CTRL))?.let { code to kind(it) } }
                .toMap()

        assertWithMessage("KeyMap's keys without modifiers").that(unmodified).isEqualTo(named)
        assertWithMessage("KeyMap's keys with Ctrl (Ctrl+Z)")
            .that(withCtrl)
            .isEqualTo(mapOf(NativeKeyEvent.KEYCODE_Z to UNDO))
    }

    /** F23-AC6 */
    @Test
    @Config(qualifiers = "+fa")
    fun mergeIllustrationIsLtrWithDescription() {
        assertWithMessage("the fa layout direction")
            .that(app.resources.configuration.layoutDirection)
            .isEqualTo(View.LAYOUT_DIRECTION_RTL)
        show()

        val illustration = compose.onNodeWithTag(HTP_MERGE_TAG).fetchSemanticsNode()
        assertThat(illustration.config[SemanticsProperties.ContentDescription])
            .containsExactly(app.getString(R.string.htp_merging_illustration_cd))
        assertThat(illustration.config.getOrElse(SemanticsProperties.Text) { emptyList() })
            .isEmpty()

        val lefts =
            (0 until MERGE_TILES).map { index ->
                compose
                    .onNodeWithTag(htpMergeTileTag(index), useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .left
            }
        assertWithMessage("the first mini tile is the leftmost")
            .that(lefts.first())
            .isEqualTo(lefts.min())
        assertWithMessage("2 2 2 2, left to right").that(lefts.subList(0, 4)).isInStrictOrder()
        assertWithMessage("4 4, left to right").that(lefts.subList(4, 6)).isInStrictOrder()
        assertWithMessage("4 4 starts no further left than 2 2 2 2")
            .that(lefts[4])
            .isAtLeast(lefts[0])
    }

    /** Composes the screen alone and waits for the first settled frame. */
    private fun show() {
        compose.setContent { GameTheme(darkTheme = false) { HowToPlayScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    private fun topOf(node: SemanticsNodeInteraction): Float =
        node.fetchSemanticsNode().positionInRoot.y

    /** Every text under the node tagged [tag], in the unmerged tree, in tree order. */
    private fun textsOf(tag: String): List<String> =
        texts(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
            node.children.flatMap(::texts)

    private fun kind(action: KeyAction): String =
        when (action) {
            is KeyAction.Move -> MOVE
            KeyAction.Undo -> UNDO
            KeyAction.NewGame -> NEW_GAME
        }

    private companion object {
        const val KEY_ROWS = 3
        const val MERGE_TILES = 6
        const val MOVE = "move"
        const val UNDO = "undo"
        const val NEW_GAME = "new game"
        const val CTRL = NativeKeyEvent.META_CTRL_ON or NativeKeyEvent.META_CTRL_LEFT_ON

        /** A keyboard key-down of [code] with [meta], wrapped for Compose. */
        fun down(code: Int, meta: Int = 0) =
            KeyEvent(
                NativeKeyEvent(
                    /* downTime = */ 0L,
                    /* eventTime = */ 0L,
                    NativeKeyEvent.ACTION_DOWN,
                    code,
                    /* repeat = */ 0,
                    meta,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    /* scancode = */ 0,
                    /* flags = */ 0,
                    InputDevice.SOURCE_KEYBOARD,
                ),
            )
    }
}
