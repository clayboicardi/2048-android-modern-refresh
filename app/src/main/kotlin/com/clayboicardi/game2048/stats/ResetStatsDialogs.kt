package com.clayboicardi.game2048.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.ConfirmDialog

/** Where the two-step reset of Statistics stands (DESIGN_SYSTEM.md §6.9; D-044). */
enum class ResetStep {
    /** No reset dialog. */
    NONE,

    /** "Reset statistics?" is open. */
    FIRST,

    /** "Reset everything?" is open. */
    SECOND,
}

/**
 * The step counter of the two-step reset: [ResetStep.NONE] until [start], the top-bar Reset action.
 * Saved with [rememberResetStatsState], so an open reset dialog survives recreation at its step.
 */
@Stable
class ResetStatsState(initial: ResetStep = ResetStep.NONE) {
    /** The dialog shown, if any. */
    var step: ResetStep by mutableStateOf(initial)
        private set

    /** Opens the first confirmation. */
    fun start() {
        step = ResetStep.FIRST
    }

    internal fun advance() {
        step = ResetStep.SECOND
    }

    internal fun close() {
        step = ResetStep.NONE
    }

    companion object {
        /** Saves the step by its name. */
        val Saver: Saver<ResetStatsState, String> =
            Saver(save = { it.step.name }, restore = { ResetStatsState(ResetStep.valueOf(it)) })
    }
}

/** A [ResetStatsState] that survives recreation (`rememberSaveable`). */
@Composable
fun rememberResetStatsState(): ResetStatsState =
    rememberSaveable(saver = ResetStatsState.Saver) { ResetStatsState() }

/**
 * The two sequential confirmations of Reset statistics (DESIGN_SYSTEM.md §6.9; FEATURES.md F08 item
 * 7; D-044), each a `ConfirmDialog` shown while [state] is at its step:
 * 1. `stats_reset_title` / `stats_reset_body`, confirm `reset`, which moves to the second step;
 * 2. `stats_reset_confirm_title` / `stats_reset_confirm_body`, confirm
 *    `stats_reset_confirm_button`, which closes the dialogs and calls [onReset].
 *
 * Cancel, Back or a tap outside at either step closes the dialogs and changes nothing.
 */
@Composable
fun ResetStatsDialogs(state: ResetStatsState, onReset: () -> Unit, modifier: Modifier = Modifier) {
    when (state.step) {
        ResetStep.NONE -> Unit
        ResetStep.FIRST ->
            ConfirmDialog(
                title = stringResource(R.string.stats_reset_title),
                confirmText = stringResource(R.string.reset),
                onConfirm = state::advance,
                onDismiss = state::close,
                modifier = modifier,
                body = stringResource(R.string.stats_reset_body),
            )
        ResetStep.SECOND ->
            ConfirmDialog(
                title = stringResource(R.string.stats_reset_confirm_title),
                confirmText = stringResource(R.string.stats_reset_confirm_button),
                onConfirm = {
                    state.close()
                    onReset()
                },
                onDismiss = state::close,
                modifier = modifier,
                body = stringResource(R.string.stats_reset_confirm_body),
            )
    }
}
