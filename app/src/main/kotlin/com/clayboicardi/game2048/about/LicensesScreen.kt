package com.clayboicardi.game2048.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.SubScreenScaffold

/**
 * The Licenses destination (`LicensesKey`, reached from About; DESIGN_SYSTEM.md §6.12;
 * ARCHITECTURE.md §7.2). M3 part 2 stub (D-236): `SubScreenScaffold` titled `licenses` with the
 * back arrow, which calls [onBack]. M4 renders `third_party_licenses.json` and the license texts.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(
        title = stringResource(R.string.licenses),
        onBack = onBack,
        modifier = modifier,
    ) {}
}
