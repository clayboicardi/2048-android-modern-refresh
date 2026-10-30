package com.clayboicardi.game2048.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.ui.components.GameIconButton
import com.clayboicardi.game2048.ui.components.GameIconButtonStyle
import com.clayboicardi.game2048.ui.components.MiniBoardGlyph
import com.clayboicardi.game2048.ui.components.MiniTile
import com.clayboicardi.game2048.ui.components.PlainRow
import com.clayboicardi.game2048.ui.components.SubScreenScaffold
import com.clayboicardi.game2048.ui.components.focusRing
import com.clayboicardi.game2048.ui.components.showGameSnackbar
import com.clayboicardi.game2048.ui.theme.GameDimens
import kotlinx.coroutines.launch

/** The Statistics top-bar Reset action (`ic_delete`, spoken `stats_reset`). */
const val STATS_RESET_TAG = "stats:reset"

/** The tab of [STATS_TABS] index [index]: 0 is "All sizes", 1 … 6 are 3×3 … 8×8. */
fun statsTabTag(index: Int): String = "stats:tab:$index"

/** The hero card of the selected tab (best score and best tile). */
const val STATS_HERO_TAG = "stats:hero"

/** The empty state of the selected tab. */
const val STATS_EMPTY_TAG = "stats:empty"

/** The rows of a Statistics tab, in order (DESIGN_SYSTEM.md §6.9). */
enum class StatsRow {
    GAMES_PLAYED,
    WINS,
    BEST_NO_UNDO,
    AVERAGE_SCORE,
    TOTAL_MOVES,
    TIME_PLAYED,
    UNDOS_USED,
}

/** The test tag of [row] in the selected tab. */
fun statsRowTag(row: StatsRow): String = "stats:row:${row.name}"

/**
 * The Statistics destination (`StatsKey`; DESIGN_SYSTEM.md §6.9; FEATURES.md F08 items 5–7): the
 * `SubScreenScaffold` titled `statistics` with the Reset action (`ic_delete`, `stats_reset`) and
 * this screen's snackbar host, above a `PrimaryScrollableTabRow` of [STATS_TABS] and the selected
 * tab: the hero card and the seven rows, or the empty state when all fields of the tab's record are
 * zero. It renders [viewModel]'s `ui` (the D-198 fold) once it is ready and opens on the current
 * board size; the selected tab is `rememberSaveable`. Reset takes the two `ResetStatsDialogs`, then
 * calls `StatsViewModel.reset` and shows `stats_reset_done`.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reset = rememberResetStatsState()
    val done = stringResource(R.string.stats_reset_done)
    SubScreenScaffold(
        title = stringResource(R.string.statistics),
        onBack = onBack,
        modifier = modifier,
        actions = {
            GameIconButton(
                onClick = reset::start,
                icon = R.drawable.ic_delete,
                contentDescription = stringResource(R.string.stats_reset),
                style = GameIconButtonStyle.PLAIN,
                modifier = Modifier.testTag(STATS_RESET_TAG),
            )
        },
        snackbars = snackbars,
    ) {
        ui?.let { StatsContent(it) }
    }
    ResetStatsDialogs(
        state = reset,
        onReset = {
            viewModel.reset()
            scope.launch { snackbars.showGameSnackbar(done) }
        },
    )
}

/** The tabs and the selected tab's content; the tab starts at [StatsUi.initialTab]. */
@Composable
private fun ColumnScope.StatsContent(ui: StatsUi) {
    var selected by rememberSaveable { mutableIntStateOf(ui.initialTab) }
    val locale = LocalConfiguration.current.locales[0]
    val tab = remember(ui, selected, locale) { ui.tab(selected, locale) }
    PrimaryScrollableTabRow(selectedTabIndex = selected) {
        STATS_TABS.forEachIndexed { index, size ->
            Tab(
                selected = index == selected,
                onClick = { selected = index },
                // The M3 focused state layer alone is below 3:1 (DESIGN_SYSTEM.md §7.7, D-269).
                modifier = Modifier.focusRing(RectangleShape).testTag(statsTabTag(index)),
                text = { Text(size?.let(::sizeLabel) ?: stringResource(R.string.stats_all_sizes)) },
            )
        }
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
        if (tab.empty) {
            item(key = EMPTY_KEY) { EmptyState(STATS_TABS[selected]) }
        } else {
            item(key = HERO_KEY) { HeroCard(tab) }
            statsRows(tab)
        }
    }
}

/**
 * The hero card (DESIGN_SYSTEM.md §6.9): `surfaceContainerHigh`, `shapes.large`, 16 dp padding;
 * "Best score" (`labelLarge`, `onSurfaceVariant`) over the value (`headlineLarge`, `"tnum"`), read
 * together; at the end the best tile's `MiniTile`, which speaks "Best tile, <value>", above its
 * visible label.
 */
@Composable
private fun HeroCard(tab: StatsTabUi, modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = GameDimens.Space16, vertical = GameDimens.Space8)
                .testTag(STATS_HERO_TAG),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(GameDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameDimens.Space16),
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    text = stringResource(R.string.stats_best_score),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tab.bestScore,
                    style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = TNUM),
                )
            }
            // D-270: a record without a best tile (0), which only a damaged or edited file holds,
            // shows the best score alone; MiniTile draws exponents 1 and up.
            if (tab.bestTileExp > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MiniTile(tab.bestTileExp, GameDimens.MiniTileHero)
                    Text(
                        text = stringResource(R.string.stats_best_tile),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.padding(top = GameDimens.Space4).clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/** The seven rows of DESIGN_SYSTEM.md §6.9, each read as headline then value. */
private fun LazyListScope.statsRows(tab: StatsTabUi) {
    for (row in StatsRow.entries) {
        item(key = row) {
            val (headline, value) =
                when (row) {
                    StatsRow.GAMES_PLAYED -> R.string.stats_games_played to tab.gamesPlayed
                    StatsRow.WINS ->
                        R.string.stats_wins to
                            stringResource(R.string.stats_wins_value, tab.wins, tab.winRate)
                    StatsRow.BEST_NO_UNDO -> R.string.stats_best_no_undo to tab.bestNoUndo
                    StatsRow.AVERAGE_SCORE -> R.string.stats_average_score to tab.averageScore
                    StatsRow.TOTAL_MOVES -> R.string.stats_total_moves to tab.totalMoves
                    StatsRow.TIME_PLAYED -> R.string.stats_time_played to tab.timePlayed
                    StatsRow.UNDOS_USED -> R.string.stats_undos_used to tab.undosUsed
                }
            PlainRow(
                modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(statsRowTag(row)),
                trailingContent = {
                    Text(
                        text = value,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TNUM),
                    )
                },
            ) {
                Text(stringResource(headline))
            }
        }
    }
}

/**
 * The empty state of tab [size] (DESIGN_SYSTEM.md §6.9; `null` = All sizes): a centred
 * `MiniBoardGlyph` (4×4 for All sizes) at `StatsEmptyGlyph`, the title and the body, and no rows.
 */
@Composable
private fun EmptyState(size: BoardSize?, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = GameDimens.Space24, vertical = GameDimens.Space32)
                .testTag(STATS_EMPTY_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiniBoardGlyph((size ?: BoardSize.DEFAULT).n, GameDimens.StatsEmptyGlyph)
        Spacer(Modifier.height(GameDimens.Space16))
        Text(
            text =
                size?.let { stringResource(R.string.stats_empty_title, sizeLabel(it)) }
                    ?: stringResource(R.string.stats_empty_title_all),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(GameDimens.Space8))
        Text(
            text = stringResource(R.string.stats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The size label "N×N" (U+00D7), built in code (DESIGN_SYSTEM.md §3.7). */
private fun sizeLabel(size: BoardSize): String = "${size.n}×${size.n}"

/** Tabular figures for the values (DESIGN_SYSTEM.md §3.5). */
private const val TNUM = "tnum"

private const val EMPTY_KEY = "empty"
private const val HERO_KEY = "hero"
