package com.clayboicardi.game2048.game.input

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent as NativeKeyEvent
import android.view.KeyboardShortcutGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.App
import com.clayboicardi.game2048.MainActivity
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Keyboard, D-pad and gamepad input (D-053, D-199, D-203; ARCHITECTURE.md §6.3, §6.6).
 * `GameKeyInput` and `KeyMap` are driven with `android.view.KeyEvent` values (a recording handler,
 * or a real `GameViewModel` through [GameScreenRig]); the dialog, focus and `MainActivity` methods
 * use the Game screen (`App`, which needs `GameScreen`, M3-D1) or the activity.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardInputTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val modalOpen = mutableStateOf(false)
    private val cardShown = mutableStateOf(false)
    private val boardInputEnabled = mutableStateOf(true)
    private val acted = mutableListOf<Any>()
    private val keys =
        GameKeyInput(
            modalOpen = modalOpen,
            cardShown = cardShown,
            boardInputEnabled = boardInputEnabled,
            onMove = { acted += it },
            onUndo = { acted += UNDO },
            onNewGame = { acted += NEW_GAME },
        )

    /** F16-AC1 */
    @Test
    fun directionKeyMap() {
        for ((code, direction) in LETTERS) {
            acted.clear()
            assertWithMessage("letter $code").that(keys.onPreviewKey(down(code))).isTrue()
            assertWithMessage("letter $code").that(acted).containsExactly(direction)
            assertWithMessage("letter $code on the board")
                .that(keys.onBoardKey(down(code)))
                .isFalse()
        }
        // The keyboard's arrows, then a gamepad's D-pad: the same four codes from another source.
        for (source in listOf(InputDevice.SOURCE_KEYBOARD, GAMEPAD)) {
            for ((code, direction) in ARROWS) {
                acted.clear()
                val event = down(code, source = source)
                assertWithMessage("arrow $code ($source)").that(keys.onBoardKey(event)).isTrue()
                assertWithMessage("arrow $code ($source)").that(acted).containsExactly(direction)
                // Outside the focused board, arrows stay focus keys.
                assertWithMessage("arrow $code at the root")
                    .that(keys.onPreviewKey(event))
                    .isFalse()
            }
        }
        assertThat(KeyMap.directionFor(Key.W)).isEqualTo(Direction.UP)
        assertThat(KeyMap.directionFor(Key.DirectionLeft)).isEqualTo(Direction.LEFT)
        assertThat(KeyMap.directionFor(Key.Q)).isNull()
    }

    /** F16-AC2 */
    @Test
    fun undoKeys() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_FRESH_4).use { rig ->
            rig.awaitReady(compose)
            val input = rig.keyInput()
            repeat(3) { rig.playOneMove(input) }
            assertThat(rig.saved().state.moveCount).isEqualTo(3)

            val undoKeys =
                listOf(
                    down(NativeKeyEvent.KEYCODE_U),
                    down(NativeKeyEvent.KEYCODE_Z, meta = CTRL),
                    down(NativeKeyEvent.KEYCODE_DEL),
                )
            val counts = undoKeys.map { event ->
                assertThat(input.onPreviewKey(event)).isTrue()
                rig.saved().state.moveCount
            }

            assertThat(counts).containsExactly(2, 1, 0).inOrder()
        }
    }

    /** F16-AC3 */
    @Test
    fun newGameKeysFollowConfirmation() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { mid ->
            GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_FRESH_4).use { fresh ->
                var shown by mutableStateOf(mid)
                compose.setContent {
                    key(shown) {
                        App(
                            gameViewModel = shown.vm,
                            settings = shown.container.settingsRepository.state,
                            animatorScale = shown.container.animatorScale,
                            clock = shown.container.clock,
                        )
                    }
                }
                mid.awaitReady(compose)
                compose.waitForIdle()

                val midEpoch = mid.vm.board.value.epoch
                compose.onNodeWithTag(BOARD).performKeyInput { pressKey(Key.N) }
                compose.onNode(isDialog()).assertExists()
                assertThat(mid.vm.board.value.epoch).isEqualTo(midEpoch)
                assertThat(mid.vm.header.value.event).isNotEqualTo(BoardEventKind.NewGame)

                shown = fresh
                fresh.awaitReady(compose)
                compose.waitForIdle()
                val epoch = fresh.vm.board.value.epoch
                compose.onNodeWithTag(BOARD).performKeyInput { pressKey(Key.R) }
                compose.waitForIdle()

                compose.onNode(isDialog()).assertDoesNotExist()
                // The board's event is settled once presented (ARCHITECTURE.md §4.11); the
                // header keeps the event kind.
                assertThat(fresh.vm.header.value.event).isEqualTo(BoardEventKind.NewGame)
                assertThat(fresh.vm.board.value.epoch).isGreaterThan(epoch)
            }
        }
    }

    /** F16-AC4 */
    @Test
    fun modifierRules() {
        val ignored =
            listOf(
                down(NativeKeyEvent.KEYCODE_W, meta = SHIFT),
                down(NativeKeyEvent.KEYCODE_DPAD_LEFT, meta = ALT),
                down(NativeKeyEvent.KEYCODE_D, meta = META),
                down(NativeKeyEvent.KEYCODE_W, meta = CTRL),
                down(NativeKeyEvent.KEYCODE_U, meta = CTRL),
                // Only key-down events count.
                up(NativeKeyEvent.KEYCODE_W),
            )
        for (event in ignored) {
            assertWithMessage("$event at the root").that(keys.onPreviewKey(event)).isFalse()
            assertWithMessage("$event on the board").that(keys.onBoardKey(event)).isFalse()
        }
        assertThat(acted).isEmpty()

        assertThat(keys.onPreviewKey(down(NativeKeyEvent.KEYCODE_Z, meta = CTRL))).isTrue()
        assertThat(acted).containsExactly(UNDO)
    }

    /** F16-AC5 */
    @Test
    fun autoRepeatLimitedTo120ms() {
        val movedAt = mutableListOf<Long>()
        for ((repeat, timeMs) in REPEATS.withIndex()) {
            val before = acted.size
            val event = down(NativeKeyEvent.KEYCODE_DPAD_LEFT, repeat = repeat, timeMs = timeMs)
            // Every event of a held arrow is consumed, so focus never leaves the board.
            assertThat(keys.onBoardKey(event)).isTrue()
            if (acted.size > before) movedAt += timeMs
        }

        assertThat(movedAt).containsExactly(0L, 130L, 250L).inOrder()
        assertThat(acted).containsExactly(Direction.LEFT, Direction.LEFT, Direction.LEFT)
    }

    /** F16-AC6 */
    @Test
    fun ignoredWhileModalOpen() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.showGame(rig)
            // Part 1 has the new-game dialog; the size sheet and What's new join in M3 part 2, M4.
            compose.onNodeWithTag(NEW_GAME_BUTTON).performClick()
            compose.onNode(isDialog()).assertExists()
            val game = rig.saved()
            val moveId = rig.vm.board.value.moveId

            // Sent to the Game content itself, behind the dialog: its handlers must ignore them.
            for (key in listOf(Key.W, Key.DirectionLeft, Key.U, Key.N)) {
                compose.onNodeWithTag(BOARD).performKeyInput { pressKey(key) }
                compose.waitForIdle()
            }

            assertThat(rig.saved()).isEqualTo(game)
            assertThat(rig.vm.board.value.moveId).isEqualTo(moveId)
            compose.onAllNodes(isDialog()).assertCountEquals(1)
        }
    }

    /** F16-AC7 */
    @Test
    fun boardTakesFocusAndTabReachesControls() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.showGame(rig)
            compose.onNodeWithTag(BOARD).assertIsFocused()
            val moveId = rig.vm.board.value.moveId

            compose.onNodeWithTag(BOARD).performKeyInput { pressKey(Key.Tab) }
            compose.onNodeWithTag(BOARD).assertIsNotFocused()
            compose.onNode(isFocused()).assertExists()

            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
            compose.waitForIdle()

            assertThat(rig.vm.board.value.moveId).isEqualTo(moveId)
            compose.onNodeWithTag(BOARD).assertIsNotFocused()
            compose.onNode(isFocused()).assertExists()
        }
    }

    /** F16-AC8 */
    @Test
    fun redoShortcutsIgnored() {
        GameScreenRig(tmp.newFolder(), GoldenFixtures.FIX_FRESH_4).use { rig ->
            rig.awaitReady(compose)
            val input = rig.keyInput()
            repeat(2) { rig.playOneMove(input) }
            assertThat(input.onPreviewKey(down(NativeKeyEvent.KEYCODE_U))).isTrue()
            val game = rig.saved()
            val board = rig.vm.board.value
            val saves = rig.container.gameRepository.submissions(game.state.size)
            assertThat(game.state.moveCount).isEqualTo(1)

            val redo =
                listOf(
                    down(NativeKeyEvent.KEYCODE_Y, meta = CTRL),
                    down(NativeKeyEvent.KEYCODE_Z, meta = CTRL or SHIFT),
                )
            for (event in redo) {
                assertThat(input.onPreviewKey(event)).isFalse()
                assertThat(input.onBoardKey(event)).isFalse()
            }

            assertThat(rig.saved()).isEqualTo(game)
            assertThat(rig.vm.board.value).isSameInstanceAs(board)
            assertThat(rig.container.gameRepository.submissions(game.state.size)).isEqualTo(saves)
        }
    }

    /** F16-AC10 */
    @Test
    fun providesShortcutGroup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val groups = mutableListOf<KeyboardShortcutGroup>()
                activity.onProvideKeyboardShortcuts(groups, null, KeyCharacterMap.VIRTUAL_KEYBOARD)

                val group = groups.single()
                assertThat(group.label.toString()).isEqualTo(activity.getString(R.string.app_name))
                val undo = activity.getString(R.string.undo)
                assertThat(
                        group.items.map { Triple(it.label.toString(), it.keycode, it.modifiers) },
                    )
                    .containsExactly(
                        Triple(activity.getString(R.string.move_up), ARROW_UP, 0),
                        Triple(activity.getString(R.string.move_down), ARROW_DOWN, 0),
                        Triple(activity.getString(R.string.move_left), ARROW_LEFT, 0),
                        Triple(activity.getString(R.string.move_right), ARROW_RIGHT, 0),
                        Triple(undo, NativeKeyEvent.KEYCODE_U, 0),
                        Triple(undo, NativeKeyEvent.KEYCODE_Z, NativeKeyEvent.META_CTRL_ON),
                        Triple(activity.getString(R.string.new_game), NativeKeyEvent.KEYCODE_N, 0),
                    )
                    .inOrder()
            }
        }
    }

    /** [GameKeyInput] over this rig's `GameViewModel`, no modal or card, board input enabled. */
    private fun GameScreenRig.keyInput() =
        GameKeyInput(
            modalOpen = mutableStateOf(false),
            cardShown = mutableStateOf(false),
            boardInputEnabled = mutableStateOf(true),
            onMove = vm::onMove,
            onUndo = vm::onUndo,
            onNewGame = vm::onNewGame,
        )

    /** Plays one accepted move with the letter keys (the first of A, D, W, S that moves). */
    private fun GameScreenRig.playOneMove(input: GameKeyInput) {
        val before = saved().state.moveCount
        val moved = MOVE_LETTERS.any { code ->
            input.onPreviewKey(down(code))
            saved().state.moveCount > before
        }
        check(moved) { "No letter key moved the board" }
    }

    private companion object {
        const val UNDO = "undo"
        const val NEW_GAME = "new game"
        const val BOARD = "game:board"
        const val NEW_GAME_BUTTON = "game:newGame"

        const val ARROW_UP = NativeKeyEvent.KEYCODE_DPAD_UP
        const val ARROW_RIGHT = NativeKeyEvent.KEYCODE_DPAD_RIGHT
        const val ARROW_DOWN = NativeKeyEvent.KEYCODE_DPAD_DOWN
        const val ARROW_LEFT = NativeKeyEvent.KEYCODE_DPAD_LEFT

        const val SHIFT = NativeKeyEvent.META_SHIFT_ON or NativeKeyEvent.META_SHIFT_LEFT_ON
        const val ALT = NativeKeyEvent.META_ALT_ON or NativeKeyEvent.META_ALT_LEFT_ON
        const val META = NativeKeyEvent.META_META_ON or NativeKeyEvent.META_META_LEFT_ON
        const val CTRL = NativeKeyEvent.META_CTRL_ON or NativeKeyEvent.META_CTRL_LEFT_ON
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD

        val LETTERS =
            listOf(
                NativeKeyEvent.KEYCODE_W to Direction.UP,
                NativeKeyEvent.KEYCODE_K to Direction.UP,
                NativeKeyEvent.KEYCODE_D to Direction.RIGHT,
                NativeKeyEvent.KEYCODE_L to Direction.RIGHT,
                NativeKeyEvent.KEYCODE_S to Direction.DOWN,
                NativeKeyEvent.KEYCODE_J to Direction.DOWN,
                NativeKeyEvent.KEYCODE_A to Direction.LEFT,
                NativeKeyEvent.KEYCODE_H to Direction.LEFT,
            )
        val ARROWS =
            listOf(
                ARROW_UP to Direction.UP,
                ARROW_RIGHT to Direction.RIGHT,
                ARROW_DOWN to Direction.DOWN,
                ARROW_LEFT to Direction.LEFT,
            )
        val MOVE_LETTERS =
            listOf(
                NativeKeyEvent.KEYCODE_A,
                NativeKeyEvent.KEYCODE_D,
                NativeKeyEvent.KEYCODE_W,
                NativeKeyEvent.KEYCODE_S,
            )

        /** F16-AC5: the initial press at 0 ms, then repeats at 50, 100, 130, 200 and 250 ms. */
        val REPEATS = listOf(0L, 50L, 100L, 130L, 200L, 250L)

        /**
         * A key-down of [code] as the framework delivers it, wrapped for Compose: pressed at 0 ms,
         * delivered at [timeMs] with [repeat] and [meta], from [source].
         */
        fun down(
            code: Int,
            meta: Int = 0,
            repeat: Int = 0,
            timeMs: Long = 0,
            source: Int = InputDevice.SOURCE_KEYBOARD,
        ) =
            KeyEvent(
                NativeKeyEvent(
                    /* downTime = */ 0L,
                    /* eventTime = */ timeMs,
                    NativeKeyEvent.ACTION_DOWN,
                    code,
                    repeat,
                    meta,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    /* scancode = */ 0,
                    /* flags = */ 0,
                    source,
                ),
            )

        /** The key-up of [code]. */
        fun up(code: Int) =
            KeyEvent(
                NativeKeyEvent.changeAction(down(code).nativeKeyEvent, NativeKeyEvent.ACTION_UP),
            )
    }
}
