package com.clayboicardi.game2048.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.SubScreenScaffold

/**
 * The Statistics destination (`StatsKey`; DESIGN_SYSTEM.md §6.9; ARCHITECTURE.md §7.2). M3 part 2
 * stub (D-236): `SubScreenScaffold` titled `statistics` with the back arrow, which calls [onBack].
 * M4 adds the size tabs, the records and the Reset action.
 */
@Composable
fun StatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(
        title = stringResource(R.string.statistics),
        onBack = onBack,
        modifier = modifier,
    ) {}
}
