package com.clayboicardi.game2048.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R

/** The test tag of a [ConfirmDialog]'s confirm button. */
const val DIALOG_CONFIRM_TAG = "dialog:confirm"

/** The test tag of a [ConfirmDialog]'s dismiss (`cancel`) button. */
const val DIALOG_DISMISS_TAG = "dialog:dismiss"

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
