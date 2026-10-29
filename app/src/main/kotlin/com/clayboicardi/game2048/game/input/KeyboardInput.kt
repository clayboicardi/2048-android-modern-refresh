package com.clayboicardi.game2048.game.input

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.ui.theme.GameMotion

/**
 * The Game screen's keyboard, D-pad and gamepad input (D-053, D-199, D-203; ARCHITECTURE.md §6.3,
 * §6.6). `GameScreen` remembers one per screen and attaches [onPreviewKey] to its root with
 * `Modifier.onPreviewKeyEvent` and passes [onBoardKey] to `BoardView`, which handles it while the
 * board has focus.
 *
 * Rules: only `KeyEventType.KeyDown`; events with Alt, Meta or Shift are ignored; Ctrl is ignored
 * except for Ctrl+Z; a repeat (`nativeKeyEvent.repeatCount > 0`) acts at most once per
 * `GameMotion.Input.KEY_REPEAT_MIN_INTERVAL_MS` of `nativeKeyEvent.eventTime`. Tab and Shift+Tab
 * move focus normally, and arrows move focus between controls while the board is not focused.
 *
 * - [modalOpen]: a dialog, sheet or menu is open. Every game key is ignored and left unconsumed, U,
 *   N and R included (D-199).
 * - [cardShown]: the win or game-over card is shown. Direction keys are ignored; U, Ctrl+Z and
 *   Backspace still undo, N and R still start a new game (D-199).
 * - [boardInputEnabled]: `!modalOpen && overlay == None && ready && !busy` (ARCHITECTURE.md §6.6),
 *   which gates the direction keys.
 * - [onNewGame] is the screen's New game request, which shows the confirmation while the game is in
 *   progress (D-007).
 *
 * Consumption: a handler returns true only for a game key it owns that is not ignored. That covers
 * the keys it acts on, a repeat the limit drops (so a held arrow never moves focus off the board)
 * and a direction key while [boardInputEnabled] alone is false (the game is loading). Keys ignored
 * under a modal or, for direction keys, a card are left unconsumed, so focus navigation and the
 * buttons there keep them.
 */
@Stable
class GameKeyInput(
    private val modalOpen: State<Boolean>,
    private val cardShown: State<Boolean>,
    private val boardInputEnabled: State<Boolean>,
    private val onMove: (Direction) -> Unit,
    private val onUndo: () -> Unit,
    private val onNewGame: () -> Unit,
) {
    private val repeats = RepeatLimiter(GameMotion.Input.KEY_REPEAT_MIN_INTERVAL_MS)

    /**
     * The screen root's handler: W A S D and H J K L (moves), U, Ctrl+Z and Backspace (undo), N and
     * R (new game). Returns true when it consumed the event.
     */
    fun onPreviewKey(event: KeyEvent): Boolean =
        handle(event, KeyMap.actionFor(event)?.takeUnless { KeyMap.isArrow(event.key) })

    /**
     * The focused board's handler: the arrow keys and `DPAD_UP/RIGHT/DOWN/LEFT` (gamepad D-pads and
     * hats deliver these). Returns true when it consumed the event.
     */
    fun onBoardKey(event: KeyEvent): Boolean =
        handle(event, KeyMap.actionFor(event)?.takeIf { KeyMap.isArrow(event.key) })

    private fun handle(event: KeyEvent, action: KeyAction?): Boolean {
        if (action == null || !accepts(action)) return false
        val native = event.nativeKeyEvent
        if (repeats.allow(native.repeatCount, native.eventTime)) perform(action)
        return true
    }

    private fun accepts(action: KeyAction): Boolean =
        when {
            modalOpen.value -> false
            action is KeyAction.Move -> !cardShown.value
            else -> true
        }

    private fun perform(action: KeyAction) {
        when (action) {
            is KeyAction.Move -> if (boardInputEnabled.value) onMove(action.direction)
            KeyAction.Undo -> onUndo()
            KeyAction.NewGame -> onNewGame()
        }
    }
}

/** What a game key does (ARCHITECTURE.md §6.3). */
@Immutable
sealed interface KeyAction {
    /** A move key: the letters, arrows and D-pad codes of [KeyMap.directionFor]. */
    data class Move(val direction: Direction) : KeyAction

    /** U, Ctrl+Z and Backspace. */
    data object Undo : KeyAction

    /** N and R; the screen confirms while the game is in progress (D-007). */
    data object NewGame : KeyAction
}

/** The direction keys of ARCHITECTURE.md §6.3 (D-053). Pure. */
object KeyMap {
    private val directions: Map<Key, Direction> =
        mapOf(
            Key.DirectionUp to Direction.UP,
            Key.W to Direction.UP,
            Key.K to Direction.UP,
            Key.DirectionRight to Direction.RIGHT,
            Key.D to Direction.RIGHT,
            Key.L to Direction.RIGHT,
            Key.DirectionDown to Direction.DOWN,
            Key.S to Direction.DOWN,
            Key.J to Direction.DOWN,
            Key.DirectionLeft to Direction.LEFT,
            Key.A to Direction.LEFT,
            Key.H to Direction.LEFT,
        )

    private val arrows: Set<Key> =
        setOf(Key.DirectionUp, Key.DirectionRight, Key.DirectionDown, Key.DirectionLeft)

    private val commands: Map<Key, KeyAction> =
        mapOf(
            Key.U to KeyAction.Undo,
            Key.Backspace to KeyAction.Undo,
            Key.N to KeyAction.NewGame,
            Key.R to KeyAction.NewGame,
        )

    /**
     * `Direction.UP` for `Key.DirectionUp`, `Key.W`, `Key.K`; RIGHT for `DirectionRight`, `D`, `L`;
     * DOWN for `DirectionDown`, `S`, `J`; LEFT for `DirectionLeft`, `A`, `H`; `null` for any other
     * key. Physical keys, so the map is the same in every locale (D-104).
     */
    fun directionFor(key: Key): Direction? = directions[key]

    /**
     * True for the arrow keys, which are also the `DPAD_UP/RIGHT/DOWN/LEFT` codes of gamepads: the
     * focused board's keys (ARCHITECTURE.md §6.3).
     */
    fun isArrow(key: Key): Boolean = key in arrows

    /**
     * The action of [event] under the modifier rules (D-053): `null` unless it is a `KeyDown`
     * without Alt, Meta or Shift; with Ctrl only Ctrl+Z (Undo) counts, so Ctrl+Y, Ctrl+W and
     * Ctrl+Shift+Z do nothing (F16-AC4, F16-AC8). Repeats are limited by the caller.
     */
    fun actionFor(event: KeyEvent): KeyAction? =
        when {
            event.type != KeyEventType.KeyDown || event.hasIgnoredModifier() -> null
            event.isCtrlPressed -> KeyAction.Undo.takeIf { event.key == Key.Z }
            else -> directionFor(event.key)?.let(KeyAction::Move) ?: commands[event.key]
        }

    private fun KeyEvent.hasIgnoredModifier(): Boolean =
        isAltPressed || isMetaPressed || isShiftPressed
}

/**
 * D-053's auto-repeat limit (MOTION_SPEC.md §2.3 T4: event time, never scaled): an initial press
 * (`repeatCount == 0`) always acts, and a repeat acts only when at least [minIntervalMs] of event
 * time have passed since the last event that acted. Main thread only.
 */
class RepeatLimiter(private val minIntervalMs: Long) {
    private var lastActedMs: Long? = null

    /** True when the event with [repeatCount] at [eventTimeMs] may act; it then counts as acted. */
    fun allow(repeatCount: Int, eventTimeMs: Long): Boolean {
        val last = lastActedMs
        val allowed = repeatCount == 0 || last == null || eventTimeMs - last >= minIntervalMs
        if (allowed) lastActedMs = eventTimeMs
        return allowed
    }
}
