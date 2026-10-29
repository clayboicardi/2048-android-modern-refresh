package com.clayboicardi.game2048.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.SubScreenScaffold

/**
 * The Settings destination (`SettingsKey`; DESIGN_SYSTEM.md §6.8; ARCHITECTURE.md §7.2). M3 part 2
 * stub (D-236): `SubScreenScaffold` titled `settings` with the back arrow, which calls [onBack]. M4
 * adds the D-006 rows.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        modifier = modifier,
    ) {}
}
