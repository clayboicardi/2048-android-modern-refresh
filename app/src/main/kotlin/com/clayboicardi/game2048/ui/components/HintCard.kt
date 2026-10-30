package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.theme.GameDimens

/** The hint card's "Got it" button (relied on by the screen and motion tests). */
const val HINT_GOT_IT_TAG = "game:hintGotIt"

/**
 * An informational hint card (DESIGN_SYSTEM.md §6.2; D-009): a `Card` in `secondaryContainer` with
 * `shapes.large`, padded 16 dp at the start and end and 12 dp at the top and bottom, holding [text]
 * (`bodyMedium`, `onSecondaryContainer`, taking the row's free width and wrapping) and a `got_it`
 * text button that calls [onDismiss]. It is a polite live region, so TalkBack reads it once when it
 * appears.
 */
@Composable
fun HintCard(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = GameDimens.Space16,
                    vertical = GameDimens.Space12,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(GameDimens.Space8))
            GameButton(
                onClick = onDismiss,
                text = stringResource(R.string.got_it),
                style = GameButtonStyle.TEXT,
                modifier = Modifier.testTag(HINT_GOT_IT_TAG),
            )
        }
    }
}
