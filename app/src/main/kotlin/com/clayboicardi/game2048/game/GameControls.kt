package com.clayboicardi.game2048.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.ui.components.GameButton
import com.clayboicardi.game2048.ui.components.GameButtonIcon
import com.clayboicardi.game2048.ui.components.GameButtonStyle
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Test tags of the controls (relied on by the input and motion tests). */
const val CONTROLS_TAG = "game:controls"
const val UNDO_TAG = "game:undo"
const val NEW_GAME_TAG = "game:newGame"

/** Where the controls sit (DESIGN_SYSTEM.md §5.4, §6.6; D-249). */
enum class ControlsForm {
    /** STACKED and SCROLL: the controls bar, Undo and New game at 1 : 1, at least 80 dp tall. */
    BAR,

    /** Beside the TalkBack pad in STACKED and SCROLL: New game only, text only, 104 dp wide. */
    PAD_COLUMN,

    /**
     * Below the TalkBack pad in STACKED and SCROLL when New game's label does not fit the
     * [PAD_COLUMN] on two lines: New game only, text only, the full width (D-266).
     */
    BELOW_PAD,

    /** The THREE_COLUMN actions rail: Undo and New game stacked, each the full rail width. */
    RAIL,

    /** The SIDE_PANEL: Undo and New game on one row at 1 : 1. */
    PANEL_ROW,
}

/**
 * The game controls (DESIGN_SYSTEM.md §5.4, §6.1, §7.3; D-039, D-199, D-249): Undo
 * (`FilledTonalButton`, `ic_undo`), enabled while the game is ready and can undo, and New game
 * (`OutlinedButton`, `ic_restart`), enabled once ready, 8 dp apart, labels on at most two lines.
 * [form] places them: the [ControlsForm.BAR] at weights 1 : 1, the [ControlsForm.PAD_COLUMN] beside
 * the pad or [ControlsForm.BELOW_PAD] below it (New game only, no icon), the rail's stacked buttons
 * or the panel's row. While the TalkBack pad is shown ([padShown]) Undo is left out: the pad has
 * it, so TalkBack meets no duplicate (DESIGN_SYSTEM.md §6.6). Both stay enabled while a card is
 * shown. [onNewGame] is the screen's request (it confirms while the game is in progress, D-007).
 * More is not here: the arrangements place `GameMoreButton` (D-249). Under `LocalChromeProbe` the
 * buttons are disabled and observe nothing.
 *
 * The Undo icon turns −360° with `plan.undoIconSpin` (Full only) for every undo of [board], the
 * arrangement's presented board, cumulatively, so a key-repeat undo keeps spinning with no jump;
 * when idle the angle is normalised (MOTION_SPEC.md §3.9). The board is observed without a
 * recomposition.
 */
@Composable
fun GameControls(
    board: State<BoardUi>,
    ui: State<GameUiState>,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
    form: ControlsForm = ControlsForm.BAR,
    padShown: Boolean = false,
) {
    val probe = LocalChromeProbe.current
    val state = ui.value
    val rotation = remember { Animatable(0f) }
    if (!probe) UndoSpin(board, rotation)
    val ready = state.ready && !probe
    val undo: @Composable (Modifier) -> Unit = { buttonModifier ->
        GameButton(
            onClick = onUndo,
            text = stringResource(R.string.undo),
            style = GameButtonStyle.TONAL,
            modifier = buttonModifier.testTag(UNDO_TAG),
            enabled = ready && state.canUndo,
            icon = {
                GameButtonIcon(
                    R.drawable.ic_undo,
                    Modifier.graphicsLayer { rotationZ = rotation.value },
                )
            },
        )
    }
    val newGame: @Composable (Modifier, Boolean) -> Unit = { buttonModifier, icon ->
        GameButton(
            onClick = onNewGame,
            text = stringResource(R.string.new_game),
            style = GameButtonStyle.OUTLINED,
            modifier = buttonModifier.testTag(NEW_GAME_TAG),
            enabled = ready,
            icon = if (icon) ({ GameButtonIcon(R.drawable.ic_restart) }) else null,
        )
    }
    val showUndo = !padShown && form != ControlsForm.PAD_COLUMN && form != ControlsForm.BELOW_PAD
    when (form) {
        ControlsForm.BAR,
        ControlsForm.PANEL_ROW ->
            Row(
                modifier =
                    modifier
                        .testTag(CONTROLS_TAG)
                        .fillMaxWidth()
                        .then(
                            if (form == ControlsForm.BAR) {
                                Modifier.heightIn(min = GameDimens.ControlsBarHeight)
                            } else {
                                Modifier
                            },
                        ),
                horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showUndo) undo(Modifier.weight(1f))
                newGame(Modifier.weight(1f), true)
            }
        ControlsForm.PAD_COLUMN,
        ControlsForm.BELOW_PAD ->
            Column(
                modifier =
                    modifier
                        .testTag(CONTROLS_TAG)
                        .then(
                            if (form == ControlsForm.PAD_COLUMN) {
                                Modifier.width(GameDimens.PadControlsWidth)
                            } else {
                                Modifier.fillMaxWidth()
                            },
                        ),
                verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
            ) {
                newGame(Modifier.fillMaxWidth(), false)
            }
        ControlsForm.RAIL ->
            Column(
                modifier = modifier.testTag(CONTROLS_TAG).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
            ) {
                if (showUndo) undo(Modifier.fillMaxWidth())
                newGame(Modifier.fillMaxWidth(), true)
            }
    }
}

/** The Undo icon's turn for every undo of [board], and its probe readers (MOTION_SPEC.md §3.9). */
@Composable
private fun UndoSpin(board: State<BoardUi>, rotation: Animatable<Float, AnimationVector1D>) {
    val level = LocalEffectiveMotion.current
    val spin = remember(level) { GameMotion.plan(level).undoIconSpin }
    val currentSpin = rememberUpdatedState(spin)
    val probe = LocalMotionProbe.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(board, rotation) {
        var spinning: Job? = null
        // The first value is the board this composition starts with: an undo not yet presented
        // spins once, a settled one (event `Restored`) does not.
        snapshotFlow { board.value }
            .distinctUntilChangedBy { it.moveId }
            .collect { presented ->
                val spec = currentSpin.value
                if (presented.event == BoardEvent.Undo && spec != null) {
                    // A new undo continues from the running turn's target: no jump.
                    spinning?.cancel()
                    spinning = scope.launch {
                        val turn = GameMotion.Board.UNDO_ICON_ROTATION_DEG
                        rotation.animateTo(rotation.targetValue + turn, spec)
                        rotation.snapTo(rotation.value % FULL_TURN_DEG)
                    }
                }
            }
    }
    DisposableEffect(probe, rotation) {
        val registrations =
            listOfNotNull(
                probe?.registerUndoIcon { rotation.value },
                probe?.registerRunning { rotation.isRunning },
            )
        onDispose { registrations.forEach { it.unregister() } }
    }
}

/** One full turn; the idle angle is kept within (−360°, 360°). */
private const val FULL_TURN_DEG = 360f
