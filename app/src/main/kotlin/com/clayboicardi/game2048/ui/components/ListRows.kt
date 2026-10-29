package com.clayboicardi.game2048.ui.components

import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * The list rows of the sheets and sub-screens (DESIGN_SYSTEM.md §7.1, §7.9; D-026, D-246). In
 * material3 1.5 every `ListItem` overload that is not deprecated takes `ListItemShapes` and
 * `ListItemElevation`, which 1.4.0 does not have (its one `ListItem` takes the headline first and
 * is deprecated in 1.5), so `ListItem` is called only in `ui/components`.
 */

/**
 * One choice of a `selectableGroup()` (DESIGN_SYSTEM.md §6.7): the M3 1.5 `ListItem` with
 * [selected] and [onClick], spoken as selected when [selected], with M3's default colours, shape
 * and padding. [headline] is the row's headline; the slots are `ListItem`'s.
 */
@Composable
fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
    ) {
        headline()
    }
}

/**
 * A row that opens something (DESIGN_SYSTEM.md §6.11 link rows): the M3 1.5 `ListItem` with
 * [onClick], with M3's default colours, shape and padding. [headline] is the row's headline; the
 * slots are `ListItem`'s.
 */
@Composable
fun LinkRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
    ) {
        headline()
    }
}
