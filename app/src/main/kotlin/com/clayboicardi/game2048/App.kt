package com.clayboicardi.game2048

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.ui.theme.GameTheme

/**
 * The app root. M0 placeholder: the migrated `settings` string, centered (ARCHITECTURE.md §1.2).
 * Its ink is the scheme's `onBackground` role (D-083): nothing here draws a background, so the text
 * sits on the light or dark window background, and the theme provides no content color.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    GameTheme {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.settings),
                modifier = Modifier.testTag("placeholder"),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
