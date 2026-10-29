package com.clayboicardi.game2048.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.LinkRow
import com.clayboicardi.game2048.ui.components.PlainRow
import com.clayboicardi.game2048.ui.components.SubScreenScaffold
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.MotionLevel

/** The Licenses screen's scrolling list. */
const val LICENSES_LIST_TAG = "licenses:list"

/** The show / hide toggle row of the [licenseId] group. */
fun licenseToggleTag(licenseId: String): String = "licenses:toggle:$licenseId"

/** The expanded license text of the [licenseId] group. */
fun licenseTextTag(licenseId: String): String = "licenses:text:$licenseId"

/**
 * The Licenses destination (`LicensesKey`, reached from About; DESIGN_SYSTEM.md §6.12; FEATURES.md
 * F26; D-122): `SubScreenScaffold` titled `licenses`, whose back arrow calls [onBack], over a
 * `LazyColumn` (at most `ContentMaxWidth` wide, the scaffold's column) of [viewModel]'s license
 * groups in the order MIT, OFL-1.1, Apache-2.0, once read on its I/O dispatcher. Each group has its
 * license name as a section header, one plain row per entry (name; copyright and, in `bodySmall`,
 * group or path) and, when its text ships, a toggle row ("Show license text" / "Hide license text")
 * that expands the raw text in `bodySmall` Monospace on a `surfaceContainerLow` box. The expansion
 * animates (`expandVertically(overlaySpatial)`) only under Full motion and is instant otherwise; it
 * survives recreation.
 */
@Composable
fun LicensesScreen(viewModel: AboutViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val groups by viewModel.licenses.collectAsStateWithLifecycle()
    SubScreenScaffold(
        title = stringResource(R.string.licenses),
        onBack = onBack,
        modifier = modifier,
    ) {
        LazyColumn(Modifier.fillMaxSize().testTag(LICENSES_LIST_TAG)) {
            for (group in groups.orEmpty()) {
                val id = group.licenseId
                item(key = "header:$id") {
                    InfoSectionHeader(stringResource(LicenseCatalog.nameOf(id)))
                }
                itemsIndexed(group.entries, key = { index, _ -> "entry:$id:$index" }) { _, entry ->
                    PlainRow(
                        supportingContent = {
                            Column {
                                Text(entry.copyright)
                                Text(entry.groupOrPath, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                    ) {
                        Text(entry.name)
                    }
                }
                group.text?.let { text -> item(key = "text:$id") { LicenseText(id, text) } }
            }
        }
    }
}

/**
 * The toggle row of the [licenseId] group and its [text], collapsed at first. Under Full motion the
 * text expands and collapses with `overlaySpatial` (DESIGN_SYSTEM.md §6.12); otherwise it appears
 * and disappears at once.
 */
@Composable
private fun LicenseText(licenseId: String, text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val full = LocalEffectiveMotion.current == MotionLevel.FULL
    Column(Modifier.fillMaxWidth()) {
        LinkRow(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag(licenseToggleTag(licenseId)),
            trailingContent = {
                Icon(
                    painter =
                        painterResource(
                            if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
                        ),
                    contentDescription = null,
                )
            },
        ) {
            Text(
                stringResource(
                    if (expanded) R.string.license_hide_text else R.string.license_show_text,
                ),
            )
        }
        if (full) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(GameMotion.overlaySpatial(IntSize.VisibilityThreshold)),
                exit = shrinkVertically(GameMotion.overlaySpatial(IntSize.VisibilityThreshold)),
            ) {
                LicenseTextBox(licenseId, text)
            }
        } else if (expanded) {
            LicenseTextBox(licenseId, text)
        }
    }
}

/** The raw license [text]: `bodySmall` Monospace, `onSurfaceVariant`, on `surfaceContainerLow`. */
@Composable
private fun LicenseTextBox(licenseId: String, text: String) {
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = GameDimens.Space16, vertical = GameDimens.Space8)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(GameDimens.Space16),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(licenseTextTag(licenseId)),
        )
    }
}
