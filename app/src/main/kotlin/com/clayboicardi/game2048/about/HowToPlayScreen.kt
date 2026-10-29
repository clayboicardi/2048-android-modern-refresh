package com.clayboicardi.game2048.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.SubScreenScaffold

/**
 * The How to play destination (`HowToPlayKey`; DESIGN_SYSTEM.md §6.10; ARCHITECTURE.md §7.2). M3
 * part 2 stub (D-236): `SubScreenScaffold` titled `how_to_play` with the back arrow, which calls
 * [onBack]. M4 adds the rules.
 */
@Composable
fun HowToPlayScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(
        title = stringResource(R.string.how_to_play),
        onBack = onBack,
        modifier = modifier,
    ) {}
}
