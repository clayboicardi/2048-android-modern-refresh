package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameTextStyles

/*
 * The Settings rows (DESIGN_SYSTEM.md §6.8; D-006, D-246): the M3 1.5 `ListItem` overloads with the
 * headline as the trailing lambda, M3's default colours, shape and padding. Every row has a
 * supporting line, so each is a two-line `ListItem`, at least 72 dp tall. Each interactive row
 * draws the focus ring of its resting shape (`ListItemDefaults.shapes().shape`, §7.7; D-267).
 */

/**
 * A section header of Settings (DESIGN_SYSTEM.md §6.8): [text] in `GameTextStyles.sectionHeader`
 * (`titleSmallEmphasized`) and `primary`, 16 dp from the start, 24 dp below the previous row and 8
 * dp above the next, spoken as a heading.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = GameTextStyles.sectionHeader,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .semantics { heading() }
                .padding(
                    start = GameDimens.Space16,
                    top = GameDimens.Space24,
                    end = GameDimens.Space16,
                    bottom = GameDimens.Space8,
                ),
    )
}

/**
 * A switch row (DESIGN_SYSTEM.md §6.8): `ListItem(checked, onCheckedChange)` with [headline], the
 * [summary] as its supporting line and a `Switch` as trailing content. The whole row toggles, and
 * it is spoken with `Role.Switch` and its on or off state; the `Switch` itself takes no input
 * (`onCheckedChange = null`). [enabled] false shows the row and the switch disabled and ignores
 * taps (the High-contrast lock, F11 item 5). [onCheckedChange] receives the new value; the caller
 * persists it and fires the toggle haptic.
 *
 * A checked row keeps the colours of an unchecked one ([uncheckedLook]): M3 draws a checked
 * `ListItem` as a selected one, on `secondaryContainer` with `onSecondaryContainer` text, which
 * would mark every switch that is on like a current choice; the `Switch` alone shows the state.
 */
@Composable
fun SettingsSwitchRow(
    headline: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier =
            modifier.semantics { role = Role.Switch }.focusRing(ListItemDefaults.shapes().shape),
        enabled = enabled,
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = { Text(summary) },
        colors = uncheckedLook(),
    ) {
        Text(headline)
    }
}

/** M3's default list colours with the selected (checked) ones replaced by the unselected ones. */
@Composable
private fun uncheckedLook(): ListItemColors {
    val colors = ListItemDefaults.colors()
    return colors.copy(
        selectedContainerColor = colors.containerColor,
        selectedContentColor = colors.contentColor,
        selectedLeadingContentColor = colors.leadingContentColor,
        selectedTrailingContentColor = colors.trailingContentColor,
        selectedOverlineContentColor = colors.overlineContentColor,
        selectedSupportingContentColor = colors.supportingContentColor,
    )
}

/**
 * A choice row (DESIGN_SYSTEM.md §6.8): the M3 1.5 `ListItem` made clickable with `Role.Button` and
 * the `a11y_action_change` ("Change") click label, [headline], the current value as [summary] and
 * an optional [trailingContent] (the Palette row's swatch). [onClick] opens the row's
 * `ChoiceDialog`. [enabled] false shows the row disabled and ignores taps (the Motion lock, D-006).
 */
@Composable
fun SettingsChoiceRow(
    headline: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier =
            modifier
                .focusRing(ListItemDefaults.shapes().shape)
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.a11y_action_change),
                    role = Role.Button,
                    onClick = onClick,
                ),
        enabled = enabled,
        trailingContent = trailingContent,
        supportingContent = { Text(summary) },
    ) {
        Text(headline)
    }
}

/**
 * A link row (DESIGN_SYSTEM.md §6.8): like [SettingsChoiceRow], with the `a11y_action_open`
 * ("Open") click label and the decorative `ic_open_in_new` as trailing content. [onClick] opens the
 * system page (App language, D-102).
 */
@Composable
fun SettingsLinkRow(
    headline: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier =
            modifier
                .focusRing(ListItemDefaults.shapes().shape)
                .clickable(
                    onClickLabel = stringResource(R.string.a11y_action_open),
                    role = Role.Button,
                    onClick = onClick,
                ),
        trailingContent = {
            Icon(painter = painterResource(R.drawable.ic_open_in_new), contentDescription = null)
        },
        supportingContent = { Text(summary) },
    ) {
        Text(headline)
    }
}
