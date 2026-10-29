package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * The board-size chip (DESIGN_SYSTEM.md §7.3; D-007): an `AssistChip` with M3 defaults, labelled
 * "N×N" (built in code, U+00D7, system font, §3.7), `ic_grid_view` before and `ic_expand_more`
 * after the label at `SizeChipIcon`. It is spoken as `board_size_cd` with the action label
 * `board_size_change`, and [onClick] opens the size sheet.
 *
 * The chip is drawn at its own height inside a `minimumInteractiveComponentSize()` box, so its
 * focus ring (§7.7) follows the chip itself while its touch target stays 48 dp.
 */
@Composable
fun SizeChip(n: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = "$n×$n"
    val description = stringResource(R.string.board_size_cd, label)
    val actionLabel = stringResource(R.string.board_size_change)
    val shape = AssistChipDefaults.shape
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            AssistChip(
                onClick = onClick,
                label = { Text(label) },
                modifier =
                    Modifier.focusRing(shape).clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        onClick(label = actionLabel) {
                            onClick()
                            true
                        }
                    },
                leadingIcon = { ChipIcon(R.drawable.ic_grid_view) },
                trailingIcon = { ChipIcon(R.drawable.ic_expand_more) },
                shape = shape,
            )
        }
    }
}

@Composable
private fun ChipIcon(id: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(GameDimens.SizeChipIcon),
    )
}
