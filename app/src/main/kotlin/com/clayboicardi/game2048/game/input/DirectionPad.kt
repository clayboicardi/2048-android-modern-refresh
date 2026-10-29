package com.clayboicardi.game2048.game.input

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.ui.components.GameIconButton
import com.clayboicardi.game2048.ui.components.GameIconButtonStyle
import com.clayboicardi.game2048.ui.theme.GameDimens

/** The direction pad and its buttons (relied on by the accessibility tests). */
const val PAD_TAG = "game:pad"
const val PAD_UNDO_TAG = "game:padUndo"

/** The pad button of [direction] (`game:pad:UP`, …). */
fun padButtonTag(direction: Direction): String = "game:pad:${direction.name}"

/**
 * The TalkBack direction pad (DESIGN_SYSTEM.md §6.6; D-082, D-104): a 2 × 3 grid of `PadButton` (64
 * dp) `FilledTonalIconButton`s through [GameIconButton], `PadGap` (8 dp) apart, so 208 × 136 dp:
 * - row 1: an empty cell, Up (`ic_pad_up`, `move_up`), Undo (`ic_undo`, `undo`);
 * - row 2: Left (`ic_pad_left`, `move_left`), Down (`ic_pad_down`, `move_down`), Right
 *   (`ic_pad_right`, `move_right`).
 *
 * The whole pad is a forced left-to-right subtree, so its buttons and icons never mirror. The
 * arrows call [onMove] and Undo [onUndo]; [canUndo] enables Undo like the controls-bar Undo, which
 * the pad replaces while it is shown (D-039). [enabled] is false while the game loads.
 */
@Composable
fun DirectionPad(
    onMove: (Direction) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.testTag(PAD_TAG),
            verticalArrangement = Arrangement.spacedBy(GameDimens.PadGap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(GameDimens.PadGap)) {
                Spacer(Modifier.size(GameDimens.PadButton))
                PadArrow(Direction.UP, onMove, enabled)
                PadButton(
                    icon = R.drawable.ic_undo,
                    label = R.string.undo,
                    onClick = onUndo,
                    enabled = enabled && canUndo,
                    modifier = Modifier.testTag(PAD_UNDO_TAG),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GameDimens.PadGap)) {
                PadArrow(Direction.LEFT, onMove, enabled)
                PadArrow(Direction.DOWN, onMove, enabled)
                PadArrow(Direction.RIGHT, onMove, enabled)
            }
        }
    }
}

@Composable
private fun PadArrow(direction: Direction, onMove: (Direction) -> Unit, enabled: Boolean) {
    val (icon, label) =
        when (direction) {
            Direction.UP -> R.drawable.ic_pad_up to R.string.move_up
            Direction.DOWN -> R.drawable.ic_pad_down to R.string.move_down
            Direction.LEFT -> R.drawable.ic_pad_left to R.string.move_left
            Direction.RIGHT -> R.drawable.ic_pad_right to R.string.move_right
        }
    PadButton(
        icon = icon,
        label = label,
        onClick = { onMove(direction) },
        enabled = enabled,
        modifier = Modifier.testTag(padButtonTag(direction)),
    )
}

@Composable
private fun PadButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    GameIconButton(
        onClick = onClick,
        icon = icon,
        contentDescription = stringResource(label),
        style = GameIconButtonStyle.TONAL,
        modifier = modifier,
        enabled = enabled,
        containerSize = DpSize(GameDimens.PadButton, GameDimens.PadButton),
        iconSize = GameDimens.PadIcon,
    )
}
