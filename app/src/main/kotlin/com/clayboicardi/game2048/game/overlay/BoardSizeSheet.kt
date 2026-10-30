package com.clayboicardi.game2048.game.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.game.SizeOption
import com.clayboicardi.game2048.ui.components.GameModalSheet
import com.clayboicardi.game2048.ui.components.MiniBoardGlyph
import com.clayboicardi.game2048.ui.components.SelectableRow
import com.clayboicardi.game2048.ui.components.formatCount
import com.clayboicardi.game2048.ui.theme.GameDimens
import kotlinx.collections.immutable.ImmutableList

/** The board-size sheet (relied on by the sheet, input and recreation tests). */
const val SIZE_SHEET_TAG = "overlay:sizeSheet"

/** The row of the N×N size in the board-size sheet. */
fun sizeRowTag(n: Int): String = "overlay:sizeRow:$n"

/** The check mark of the current size's row (decorative: the row is spoken as selected). */
internal const val SIZE_CHECK_TAG = "overlay:sizeCheck"

/**
 * The board-size picker (DESIGN_SYSTEM.md §6.7; FEATURES.md F02 item 2; D-007, D-181), on
 * `GameModalSheet` and nothing else. [options] is `GameViewModel.sizeOptions` (D-236), read here
 * and not in the screen's body: six rows 3×3 … 8×8, each a `SelectableRow` (D-246) at least 72 dp
 * tall inside a `selectableGroup()` with `Role.RadioButton`: `MiniBoardGlyph(n)`, the headline
 * `"$n×$n"`, the goal and best (or "No best score yet") supporting line, the "In progress" pill and
 * the check of the current size. A row hides the sheet with its motion (`GameModalSheet`'s
 * `dismiss`), then calls [onSelect] with its size, the current size too (the ViewModel ignores that
 * one, F02-AC5); [onDismissRequest] is the sheet's own dismissal (Back, scrim, drag). The sheet is
 * tagged [SIZE_SHEET_TAG] and each row [sizeRowTag].
 *
 * The rows render from `options.value` only, so the sheet lists nothing until the ViewModel's first
 * reading; the title and the rows scroll together when the window is shorter than the sheet.
 */
@Composable
fun BoardSizeSheet(
    options: State<ImmutableList<SizeOption>>,
    onSelect: (BoardSize) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GameModalSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(SIZE_SHEET_TAG),
    ) { dismiss ->
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.board_size_title),
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier.semantics { heading() }
                        .padding(
                            start = GameDimens.Space24,
                            top = GameDimens.Space16,
                            end = GameDimens.Space24,
                            bottom = GameDimens.Space8,
                        ),
            )
            Column(Modifier.selectableGroup()) {
                for (option in options.value) {
                    SizeRow(option, onClick = { dismiss { onSelect(option.size) } })
                }
            }
        }
    }
}

/**
 * One size of the sheet (DESIGN_SYSTEM.md §6.7): selected and checked when it is the current size,
 * the "In progress" pill when its saved game is in progress. The headline and the goal are game
 * numerals built in code (Latin digits, U+00D7); the best is a prose count (`formatCount`).
 */
@Composable
private fun SizeRow(option: SizeOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = option.size.n
    val locale = LocalConfiguration.current.locales[0]
    val goal = stringResource(R.string.board_size_goal, (1L shl option.size.targetExp).toString())
    val best =
        if (option.best > 0) {
            stringResource(R.string.board_size_best, formatCount(option.best, locale))
        } else {
            stringResource(R.string.board_size_no_best)
        }
    SelectableRow(
        selected = option.current,
        onClick = onClick,
        modifier = modifier.heightIn(min = GameDimens.SizeSheetRowMinHeight).testTag(sizeRowTag(n)),
        leadingContent = { MiniBoardGlyph(n, GameDimens.MiniTileSmall) },
        trailingContent = { SizeRowTrailing(option) },
        supportingContent = { Text(stringResource(R.string.board_size_supporting, goal, best)) },
    ) {
        Text("$n×$n")
    }
}

/**
 * The pill of a size in progress, then the check of the current size (DESIGN_SYSTEM.md §6.7). On
 * the current size's row, whose container is `secondaryContainer`, the pill is `secondary` with
 * `onSecondary` text, so its shape stays visible (D-250); on the other rows it is
 * `secondaryContainer` with `onSecondaryContainer` text.
 */
@Composable
private fun SizeRowTrailing(option: SizeOption, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.inProgress) {
            val scheme = MaterialTheme.colorScheme
            val pill = if (option.current) scheme.secondary else scheme.secondaryContainer
            val ink = if (option.current) scheme.onSecondary else scheme.onSecondaryContainer
            Text(
                text = stringResource(R.string.in_progress),
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                modifier =
                    Modifier.background(pill, CircleShape)
                        .padding(horizontal = GameDimens.Space8, vertical = GameDimens.Space4),
            )
        }
        if (option.current) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.testTag(SIZE_CHECK_TAG),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
