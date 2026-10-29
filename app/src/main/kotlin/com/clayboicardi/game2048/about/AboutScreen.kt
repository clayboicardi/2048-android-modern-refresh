package com.clayboicardi.game2048.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clayboicardi.game2048.ABOUT_LICENSES_TAG
import com.clayboicardi.game2048.BuildConfig
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.components.LinkRow
import com.clayboicardi.game2048.ui.components.PlainRow
import com.clayboicardi.game2048.ui.components.SubScreenScaffold
import com.clayboicardi.game2048.ui.components.rememberIntentLauncher
import com.clayboicardi.game2048.ui.components.viewUrlIntent
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.GameTextStyles
import com.clayboicardi.game2048.ui.theme.LocalTileSkin

/** About's scrolling list. */
const val ABOUT_LIST_TAG = "about:list"

/** The header's version line. */
const val ABOUT_VERSION_TAG = "about:version"

/** The Source code row. */
const val ABOUT_SOURCE_TAG = "about:source"

/** The privacy text. */
const val ABOUT_PRIVACY_TAG = "about:privacy"

/** The [index]-th credits row, in file order. */
fun aboutCreditTag(index: Int): String = "about:credit:$index"

/** The title of the [index]-th legacy release ("2.2 (25)"), in file order (newest first). */
fun aboutReleaseTag(index: Int): String = "about:release:$index"

/**
 * The About destination (`AboutKey`; DESIGN_SYSTEM.md §6.11; FEATURES.md F25; D-009, D-123, D-124,
 * D-134): `SubScreenScaffold` titled `about`, whose back arrow calls [onBack], over a `LazyColumn`
 * (at most `ContentMaxWidth` wide, the scaffold's column):
 * - the header: the app icon composed from its two adaptive layers at `AboutIcon`, "2048" in the
 *   title numerals at `AboutTitle` in `skin.titleColor`, and `about_version` "Version 3.0.0
 *   (30000)" from `BuildConfig.VERSION_NAME` and `VERSION_CODE` (no `PackageManager`, D-134);
 * - What's new: every [CHANGELOG] entry, newest first, titled with its versionName, then bullets;
 * - Source code: a link row to the repository through `rememberIntentLauncher`, which shows
 *   `link_open_failed` in this screen's snackbar host when no app can open it (F25-AC4);
 * - Privacy: D-123's text (`privacy_text`);
 * - Open-source licenses: a link row (tagged `ABOUT_LICENSES_TAG`) that calls [onOpenLicenses];
 * - Credits and Legacy history from [viewModel]'s `legacy_history.json` (English static data,
 *   D-124), once read on its I/O dispatcher.
 *
 * Link rows are spoken with the `a11y_action_open` click label.
 */
@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val launch = rememberIntentLauncher(snackbars)
    val openLabel = stringResource(R.string.a11y_action_open)
    SubScreenScaffold(
        title = stringResource(R.string.about),
        onBack = onBack,
        modifier = modifier,
        snackbars = snackbars,
    ) {
        LazyColumn(Modifier.fillMaxSize().testTag(ABOUT_LIST_TAG)) {
            item(key = "header") { AboutHeader() }
            whatsNewHistory()
            item(key = "source") {
                LinkRow(
                    onClick = { launch(viewUrlIntent(SOURCE_URL)) },
                    modifier = Modifier.openLink(openLabel).testTag(ABOUT_SOURCE_TAG),
                    trailingContent = {
                        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                    },
                    supportingContent = { Text(stringResource(R.string.about_source_code_url)) },
                ) {
                    Text(stringResource(R.string.about_source_code))
                }
            }
            item(key = "privacyHeader") {
                InfoSectionHeader(stringResource(R.string.about_privacy))
            }
            item(key = "privacy") {
                InfoBody(
                    text = stringResource(R.string.privacy_text),
                    modifier = Modifier.testTag(ABOUT_PRIVACY_TAG),
                )
            }
            item(key = "licenses") {
                LinkRow(
                    onClick = onOpenLicenses,
                    modifier = Modifier.openLink(openLabel).testTag(ABOUT_LICENSES_TAG),
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                        )
                    },
                ) {
                    Text(stringResource(R.string.about_licenses))
                }
            }
            history?.let { legacy(it) }
        }
    }
}

/** What's new: the section header, then every [CHANGELOG] entry, newest version first (F25-AC2). */
private fun LazyListScope.whatsNewHistory() {
    item(key = "whatsNewHeader") { InfoSectionHeader(stringResource(R.string.about_whats_new)) }
    for ((code, lines) in CHANGELOG.entries.sortedByDescending { it.key }) {
        item(key = "whatsNew:$code") {
            ReleaseTitle(versionNameOf(code))
        }
        itemsIndexed(lines, key = { index, _ -> "whatsNew:$code:$index" }) { _, line ->
            BulletRow(stringResource(line))
        }
    }
}

/**
 * Credits ("name — role" rows in file order, F25-AC6) and Legacy history (the releases newest
 * first, each titled "version (versionCode)" over its changes, F25-AC7), from [history].
 */
private fun LazyListScope.legacy(history: LegacyHistory) {
    item(key = "creditsHeader") { InfoSectionHeader(stringResource(R.string.about_credits)) }
    itemsIndexed(history.credits, key = { index, _ -> "credit:$index" }) { index, credit ->
        PlainRow(Modifier.testTag(aboutCreditTag(index))) {
            Text("${credit.name}$CREDIT_SEPARATOR${credit.role}")
        }
    }
    item(key = "legacyHeader") { InfoSectionHeader(stringResource(R.string.about_legacy_history)) }
    history.releases.forEachIndexed { index, release ->
        item(key = "release:$index") {
            ReleaseTitle(
                "${release.version} (${release.versionCode})",
                Modifier.testTag(aboutReleaseTag(index)),
            )
        }
        itemsIndexed(release.changes, key = { line, _ -> "release:$index:$line" }) { _, text ->
            BulletRow(text)
        }
    }
}

/**
 * The header (DESIGN_SYSTEM.md §6.11): the launcher icon's layers (`ic_launcher_background`, a
 * colour resource, under `ic_launcher_foreground`, clipped to a circle: `painterResource` cannot
 * load the `<adaptive-icon>` itself), "2048" as the game numeral (D-226: never `app_name`, which
 * reads "2048 Debug" in debug builds) and the version line.
 */
@Composable
private fun AboutHeader(modifier: Modifier = Modifier) {
    val skin = LocalTileSkin.current
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = GameDimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
    ) {
        Box(
            Modifier.size(GameDimens.AboutIcon)
                .clip(CircleShape)
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = TITLE_VALUE.toString(),
            style =
                GameFonts.title(skin)
                    .copy(fontSize = GameDimens.AboutTitle, lineHeight = GameDimens.AboutTitle),
            color = skin.titleColor,
            maxLines = 1,
        )
        Text(
            text =
                stringResource(
                    R.string.about_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE.toString(),
                ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(ABOUT_VERSION_TAG),
        )
    }
}

/** A version title ("3.0.0", "2.2 (25)"): `titleSmall`, 16 dp from the edges. */
@Composable
private fun ReleaseTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier =
            modifier.padding(
                start = GameDimens.Space16,
                top = GameDimens.Space16,
                end = GameDimens.Space16,
                bottom = GameDimens.Space4,
            ),
    )
}

/**
 * A section header of How to play, About and Licenses (DESIGN_SYSTEM.md §3.5, §6.10–§6.12): [text]
 * in `GameTextStyles.sectionHeader` (`titleSmallEmphasized`) and `primary`, 16 dp from the start,
 * 24 dp below the previous row and 8 dp above the next, spoken as a heading (as Settings' headers,
 * §6.8).
 */
@Composable
internal fun InfoSectionHeader(text: String, modifier: Modifier = Modifier) {
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
 * Body copy of How to play and About (DESIGN_SYSTEM.md §3.5): `bodyLarge`, 16 dp from the edges.
 */
@Composable
internal fun InfoBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth().padding(horizontal = GameDimens.Space16),
    )
}

/**
 * The `a11y_action_open` ("Open") click label of a link row (DESIGN_SYSTEM.md §6.8, §6.11): it adds
 * only the label to the row's own click action, which `ListItem(onClick)` provides.
 */
private fun Modifier.openLink(label: String): Modifier = semantics {
    onClick(label = label, action = null)
}

/** The repository that About's Source code row opens (D-009; F25 item 3). */
private const val SOURCE_URL = "https://github.com/clayboicardi/2048-android-modern-refresh"

/** The "2048" of the header: the game numeral, `Long.toString()` (D-103, D-226). */
private const val TITLE_VALUE = 2048L

/**
 * Joins a credit's name and role ("Gabriele Cirulli — original game"; L10N_STORE_LEGAL.md §4.5).
 */
private const val CREDIT_SEPARATOR = " — "
