package com.clayboicardi.game2048.game

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.clayboicardi.game2048.game.motion.ProbeChrome
import com.clayboicardi.game2048.ui.components.GameButton
import com.clayboicardi.game2048.ui.components.GameButtonIcon
import com.clayboicardi.game2048.ui.components.GameButtonStyle
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Test tags of the controls bar (relied on by the input and motion tests). */
const val CONTROLS_TAG = "game:controls"
const val UNDO_TAG = "game:undo"
const val NEW_GAME_TAG = "game:newGame"

/**
 * The controls bar (DESIGN_SYSTEM.md §6.1, §7.3; D-039, D-199): Undo (`FilledTonalButton`,
 * `ic_undo`), enabled while the game is ready and can undo, and New game (`OutlinedButton`,
 * `ic_restart`), enabled once ready; weights 1:1, at least `ControlsBarHeight` tall. Both stay
 * enabled while a card is shown. [onNewGame] is the screen's request (it confirms while the game is
 * in progress, D-007). M3 part 2 adds the More menu.
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
) {
    val state = ui.value
    val level = LocalEffectiveMotion.current
    val spin = remember(level) { GameMotion.plan(level).undoIconSpin }
    val currentSpin = rememberUpdatedState(spin)
    val rotation = remember { Animatable(0f) }
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
                probe?.registerChrome { ProbeChrome(1f, rotation.value) },
                probe?.registerRunning { rotation.isRunning },
            )
        onDispose { registrations.forEach { it.unregister() } }
    }

    Row(
        modifier =
            modifier
                .testTag(CONTROLS_TAG)
                .fillMaxWidth()
                .heightIn(min = GameDimens.ControlsBarHeight),
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameButton(
            onClick = onUndo,
            text = stringResource(R.string.undo),
            style = GameButtonStyle.TONAL,
            modifier = Modifier.weight(1f).testTag(UNDO_TAG),
            enabled = state.ready && state.canUndo,
            icon = {
                GameButtonIcon(
                    R.drawable.ic_undo,
                    Modifier.graphicsLayer { rotationZ = rotation.value },
                )
            },
        )
        GameButton(
            onClick = onNewGame,
            text = stringResource(R.string.new_game),
            style = GameButtonStyle.OUTLINED,
            modifier = Modifier.weight(1f).testTag(NEW_GAME_TAG),
            enabled = state.ready,
            icon = { GameButtonIcon(R.drawable.ic_restart) },
        )
    }
}

/** One full turn; the idle angle is kept within (−360°, 360°). */
private const val FULL_TURN_DEG = 360f
