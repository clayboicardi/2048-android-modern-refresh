package com.clayboicardi.game2048.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.clayboicardi.game2048.BuildConfig
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.GameModalSheet
import com.clayboicardi.game2048.ui.theme.GameDimens
import kotlinx.collections.immutable.ImmutableList

/** The What's new sheet (relied on by the Game screen's What's new tests). */
const val WHATS_NEW_SHEET_TAG = "about:whatsNewSheet"

/** The sheet's "Got it" button. */
const val WHATS_NEW_GOT_IT_TAG = "about:whatsNewGotIt"

/**
 * The What's new sheet (DESIGN_SYSTEM.md §6.13; FEATURES.md F24 item 2; D-009, D-181), on
 * `GameModalSheet` and nothing else, over the Game root: the title `whats_new_title`, the subtitle
 * `whats_new_version` with this build's versionName (`BuildConfig.VERSION_NAME`, D-134), the bullet
 * lines of [entries] (`AppViewModel.whatsNew`, oldest version first) and a full-width
 * `FilledTonalButton` "Got it" with 24 dp margins. "Got it" hides the sheet with its motion
 * (`GameModalSheet`'s `dismiss`), then calls [onDismissRequest]; drag, Back and a scrim tap dismiss
 * it through `GameModalSheet` and call [onDismissRequest] as well. It never blocks the game beyond
 * being modal while shown (F15, F16). The content scrolls when the window is shorter than the
 * sheet. Tagged [WHATS_NEW_SHEET_TAG]; the button [WHATS_NEW_GOT_IT_TAG].
 */
@Composable
fun WhatsNewSheet(
    entries: ImmutableList<ChangelogEntry>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GameModalSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(WHATS_NEW_SHEET_TAG),
    ) { dismiss ->
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier.semantics { heading() }
                        .padding(
                            start = GameDimens.Space24,
                            top = GameDimens.Space16,
                            end = GameDimens.Space24,
                        ),
            )
            Text(
                text = stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        start = GameDimens.Space24,
                        top = GameDimens.Space4,
                        end = GameDimens.Space24,
                        bottom = GameDimens.Space8,
                    ),
            )
            for (entry in entries) {
                for (line in entry.lines) {
                    BulletRow(
                        text = stringResource(line),
                        modifier = Modifier.padding(horizontal = GameDimens.Space8),
                    )
                }
            }
            FilledTonalButton(
                onClick = { dismiss(onDismissRequest) },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(GameDimens.Space24)
                        .testTag(WHATS_NEW_GOT_IT_TAG),
            ) {
                Text(stringResource(R.string.got_it))
            }
        }
    }
}

/**
 * One bullet line (DESIGN_SYSTEM.md §6.11, §6.13): "•", 8 dp, then [text] in `bodyMedium`, 16 dp
 * from both edges, wrapping under itself. Spoken as [text] alone: the bullet glyph is decorative.
 */
@Composable
internal fun BulletRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = GameDimens.Space16, vertical = GameDimens.Space2),
    ) {
        Text(
            text = BULLET,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.width(GameDimens.Space8))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The bullet glyph of [BulletRow]: typography, not text (like the "–" placeholder, §6.14). */
private const val BULLET = "•"
