package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import kotlinx.collections.immutable.ImmutableList

/** The test tag of a [ConfirmDialog]'s confirm button. */
const val DIALOG_CONFIRM_TAG = "dialog:confirm"

/** The test tag of a [ConfirmDialog]'s or a [ChoiceDialog]'s dismiss (`cancel`) button. */
const val DIALOG_DISMISS_TAG = "dialog:dismiss"

/** The test tag of option [index] (0-based, in display order) of a [ChoiceDialog]. */
fun choiceOptionTag(index: Int): String = "dialog:choice:$index"

/**
 * A confirmation (DESIGN_SYSTEM.md §7.5; D-007, D-184): the Material 3 `AlertDialog` (android
 * overload) with [title], an optional [body], a `TextButton` labelled [confirmText] that calls
 * [onConfirm] and a `cancel` `TextButton` that calls [onDismiss]. Back and a tap outside also call
 * [onDismiss]. The dialog window keeps its platform enter and exit fades at every motion level
 * (D-184). Both buttons draw the focus ring (§7.7). Uses: new game (§6.5), reset statistics (§6.9).
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.focusRing(ButtonDefaults.textShape).testTag(DIALOG_CONFIRM_TAG),
            ) {
                Text(confirmText)
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRing(ButtonDefaults.textShape).testTag(DIALOG_DISMISS_TAG),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(title) },
        text = body?.let { { Text(it) } },
    )
}

/**
 * One option of a [ChoiceDialog] (DESIGN_SYSTEM.md §7.5): the [value] it applies, its [label] and
 * an optional [supporting] line (the swipe sensitivity details, §6.8).
 */
@Immutable data class Choice<T>(val value: T, val label: String, val supporting: String? = null)

/**
 * A single choice (DESIGN_SYSTEM.md §6.8, §7.5; FEATURES.md F11 item 2): the Material 3
 * `AlertDialog` (android overload) titled [title], whose body is a `selectableGroup()` of radio
 * rows, one per entry of [options]. Each row is the M3 1.5 `ListItem(selected, onClick)` (spoken
 * with `Role.RadioButton` and as selected when its value is [selected]), at least 56 dp tall, with
 * a leading `RadioButton(selected, onClick = null)`, the label in `bodyLarge` and, below it, the
 * option's supporting line in `bodyMedium` and then [optionExtra] of the value when given (the
 * Palette dialog's `PaletteSwatch`). The rows keep the dialog's container colour (they are
 * transparent, with M3's selected container on the selected one) and scroll when the dialog is
 * taller than the window.
 *
 * Tapping a row calls [onSelect] with its value, then [onDismiss]: the choice applies at once and
 * the dialog closes; there is no OK button. The one button is the `cancel` `TextButton`
 * ([DIALOG_DISMISS_TAG], with the focus ring), which, like Back and a tap outside, calls only
 * [onDismiss]. The dialog window keeps its platform fades at every motion level (D-184). Row i is
 * tagged [choiceOptionTag].
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: ImmutableList<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    optionExtra: (@Composable (T) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = modifier,
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRing(ButtonDefaults.textShape).testTag(DIALOG_DISMISS_TAG),
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    ChoiceRow(
                        option = option,
                        selected = option.value == selected,
                        onClick = {
                            onSelect(option.value)
                            onDismiss()
                        },
                        modifier = Modifier.testTag(choiceOptionTag(index)),
                        extra = optionExtra,
                    )
                }
            }
        },
    )
}

/** One radio row of a [ChoiceDialog] (DESIGN_SYSTEM.md §7.5). */
@Composable
private fun <T> ChoiceRow(
    option: Choice<T>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable (T) -> Unit)? = null,
) {
    val supporting = option.supporting
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent =
            if (supporting != null || extra != null) {
                {
                    Column {
                        supporting?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        extra?.invoke(option.value)
                    }
                }
            } else {
                null
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(option.label, style = MaterialTheme.typography.bodyLarge)
    }
}
