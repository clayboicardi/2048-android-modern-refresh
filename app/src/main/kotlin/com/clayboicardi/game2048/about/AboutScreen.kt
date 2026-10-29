package com.clayboicardi.game2048.about

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.ABOUT_LICENSES_TAG
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.LinkRow
import com.clayboicardi.game2048.ui.components.SubScreenScaffold

/**
 * The About destination (`AboutKey`; DESIGN_SYSTEM.md §6.11; ARCHITECTURE.md §7.2). M3 part 2 stub
 * (D-236): `SubScreenScaffold` titled `about` with the back arrow, which calls [onBack], and one
 * row, `about_licenses` (a `LinkRow`, D-246, tagged `ABOUT_LICENSES_TAG`), which calls
 * [onOpenLicenses] (FEATURES.md F21 item 2). M4 adds the header, the sections and the row's
 * chevron.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(
        title = stringResource(R.string.about),
        onBack = onBack,
        modifier = modifier,
    ) {
        LinkRow(onClick = onOpenLicenses, modifier = Modifier.testTag(ABOUT_LICENSES_TAG)) {
            Text(stringResource(R.string.about_licenses))
        }
    }
}
