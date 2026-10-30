package com.clayboicardi.game2048.game.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.ConfirmDialog

/**
 * The new-game confirmation (DESIGN_SYSTEM.md §6.5; D-007): "Start a new game?" with New game
 * ([onConfirm]) and Cancel; Cancel, Back and a tap outside call [onDismiss] and change nothing. No
 * body and no icon. `GameScreen` shows it only while the game is in progress and keeps it open
 * across recreation (`rememberSaveable`).
 */
@Composable
fun NewGameDialog(onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ConfirmDialog(
        title = stringResource(R.string.new_game_confirm_title),
        confirmText = stringResource(R.string.new_game),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
