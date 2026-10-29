package com.clayboicardi.game2048.game.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.GameSummary
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.ui.components.GameButton
import com.clayboicardi.game2048.ui.components.GameButtonIcon
import com.clayboicardi.game2048.ui.components.GameButtonStyle
import com.clayboicardi.game2048.ui.components.GameIconButton
import com.clayboicardi.game2048.ui.components.GameIconButtonStyle
import com.clayboicardi.game2048.ui.components.MiniTile
import com.clayboicardi.game2048.ui.components.ScoreChipLabel
import com.clayboicardi.game2048.ui.components.ScoreNumeralStyle
import com.clayboicardi.game2048.ui.components.ScoreValueText
import com.clayboicardi.game2048.ui.components.formatCount
import com.clayboicardi.game2048.ui.components.formatElapsed
import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * The game-over card (D-008, D-081, D-200; DESIGN_SYSTEM.md §6.4, §6.4a, §7.3): "Game over", the
 * score and the size's best as game numerals, the best tile as a `MiniTile`, the summary "{moves} ·
 * {time} · {undos}" in prose numbers (`game_over_summary`; `moves_count`, `formatElapsed`,
 * `undos_count` or `undos_none`), and New game (`Button`), Undo and Share: Regular, the SCORE value
 * above BEST and BEST TILE boxes and Undo (tonal) beside Share (outlined, `ic_share`) below New
 * game; [compact], three equal boxes and one 56 dp row of New game (weight 1), Undo
 * (`FilledTonalIconButton`) and Share (`OutlinedIconButton`). `surfaceContainerHigh` `ElevatedCard`
 * in `extraLarge` with the 6 dp elevation (§4.5).
 *
 * The buttons render and gate like the win card's ([WinCard]); [focus] (New game) takes focus on
 * the flip ([CardFocusEffect]). Their alphas are New game, Undo, Share in [CardVisuals.button].
 * `paneTitle` = `game_over_title`; the card cannot be dismissed (D-054).
 */
@Composable
internal fun GameOverCard(
    over: OverlayUi.GameOver,
    compact: Boolean,
    interactive: Boolean,
    measuring: Boolean,
    visuals: CardVisuals,
    actions: CardActions,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.game_over_title)
    val live = interactive && !measuring
    CardFocusEffect(interactive, measuring, focus)
    ElevatedCard(
        modifier = modifier.cardSemantics(title, GAME_OVER_CARD_TAG, measuring),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation =
            CardDefaults.elevatedCardElevation(defaultElevation = GameDimens.OverlayCardElevation),
    ) {
        val padding = if (compact) GameDimens.CardPaddingCompact else GameDimens.Space24
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardTitle(title, compact, Dp.Hairline, measuring)
            Spacer(Modifier.height(GameDimens.Space12))
            if (compact) CompactStats(over.summary) else RegularStats(over.summary)
            Spacer(Modifier.height(if (compact) GameDimens.Space8 else GameDimens.Space12))
            Text(
                text = summaryLine(over.summary),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (compact) 2 else Int.MAX_VALUE,
            )
            Spacer(Modifier.height(if (compact) GameDimens.Space16 else GameDimens.Space24))
            if (compact) {
                CompactOverButtons(live, measuring, visuals, actions, focus)
            } else {
                RegularOverButtons(live, measuring, visuals, actions, focus)
            }
        }
    }
}

/** Regular (DESIGN_SYSTEM.md §6.4): SCORE above the BEST and BEST TILE boxes. */
@Composable
private fun RegularStats(summary: GameSummary) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScoreChipLabel(stringResource(R.string.score_label), { secondary })
        FittedValue(summary.score.toString(), GameDimens.CardScoreValueMax, { primary })
        Spacer(Modifier.height(GameDimens.Space12))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        ) {
            StatBox(stringResource(R.string.best_label)) {
                FittedValue(
                    summary.best.toString(),
                    GameDimens.CardScoreValueCompactMax,
                    { primary },
                )
            }
            StatBox(stringResource(R.string.best_tile)) {
                MiniTile(summary.bestTileExp, GameDimens.MiniTileMedium)
            }
        }
    }
}

/** Compact (DESIGN_SYSTEM.md §6.4a): three equal boxes, SCORE, BEST and BEST TILE. */
@Composable
private fun CompactStats(summary: GameSummary) {
    val primary = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
    ) {
        StatBox(stringResource(R.string.score_label)) {
            FittedValue(summary.score.toString(), GameDimens.CardScoreValueCompactMax, { primary })
        }
        StatBox(stringResource(R.string.best_label)) {
            FittedValue(summary.best.toString(), GameDimens.CardScoreValueCompactMax, { primary })
        }
        StatBox(stringResource(R.string.best_tile)) {
            MiniTile(summary.bestTileExp, GameDimens.MiniTileSmall)
        }
    }
}

/**
 * A `surfaceContainerHighest` box in `shapes.large` (16 dp), at least `CardStatBoxMinHeight` tall,
 * weight 1: its [label] (`ScoreChipLabel`, upper-cased, `onSurfaceVariant`) above [value].
 */
@Composable
private fun RowScope.StatBox(label: String, value: @Composable () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            Modifier.weight(1f)
                .heightIn(min = GameDimens.CardStatBoxMinHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.large,
                )
                .padding(horizontal = GameDimens.Space8, vertical = GameDimens.Space8),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScoreChipLabel(label, { secondary })
        value()
    }
}

/**
 * [text] in the score numerals (game numerals, Latin digits) at the largest size of
 * `ScoreValueMin..`[max] in `ScoreValueStep` steps that fits the width it is given
 * (DESIGN_SYSTEM.md §6.4, §6.4a, §7.2), else `ScoreValueMin`.
 */
@Composable
private fun FittedValue(text: String, max: TextUnit, color: ColorProducer) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val size =
            remember(text, max, widthPx, density, measurer) {
                var sp = max.value
                while (sp > GameDimens.ScoreValueMin.value) {
                    val style = ScoreNumeralStyle.copy(fontSize = sp.sp)
                    val width = measurer.measure(text, style, softWrap = false, maxLines = 1).size
                    if (width.width <= widthPx) break
                    sp -= GameDimens.ScoreValueStep.value
                }
                sp.sp
            }
        ScoreValueText(text, size, color)
    }
}

/** "1,024 moves · 12:41 · No undo" (DESIGN_SYSTEM.md §6.4; prose numbers, D-103). */
@Composable
private fun summaryLine(summary: GameSummary): String {
    val locale = LocalConfiguration.current.locales[0]
    val moves =
        pluralStringResource(
            R.plurals.moves_count,
            summary.moves,
            formatCount(summary.moves.toLong(), locale),
        )
    val undos =
        if (summary.undos == 0) {
            stringResource(R.string.undos_none)
        } else {
            pluralStringResource(
                R.plurals.undos_count,
                summary.undos,
                formatCount(summary.undos.toLong(), locale),
            )
        }
    return stringResource(
        R.string.game_over_summary,
        moves,
        formatElapsed(summary.elapsedMs),
        undos,
    )
}

/** Regular (DESIGN_SYSTEM.md §6.4): New game, then Undo and Share side by side. */
@Composable
private fun RegularOverButtons(
    interactive: Boolean,
    measuring: Boolean,
    visuals: CardVisuals,
    actions: CardActions,
    focus: FocusRequester,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameButton(
            onClick = actions.onNewGame,
            text = stringResource(R.string.new_game),
            style = GameButtonStyle.FILLED,
            modifier =
                Modifier.fillMaxWidth()
                    .layerAlpha { visuals.button(NEW_GAME) }
                    .cardTag(CARD_NEW_GAME_TAG, measuring)
                    .cardFocus(focus, measuring),
            interactive = interactive,
        )
        Spacer(Modifier.height(GameDimens.Space8))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        ) {
            GameButton(
                onClick = actions.onUndo,
                text = stringResource(R.string.undo),
                style = GameButtonStyle.TONAL,
                modifier =
                    Modifier.weight(1f)
                        .layerAlpha { visuals.button(UNDO) }
                        .cardTag(CARD_UNDO_TAG, measuring),
                interactive = interactive,
            )
            GameButton(
                onClick = actions.onShare,
                text = stringResource(R.string.share),
                style = GameButtonStyle.OUTLINED,
                modifier =
                    Modifier.weight(1f)
                        .layerAlpha { visuals.button(SHARE) }
                        .cardTag(CARD_SHARE_TAG, measuring),
                interactive = interactive,
                icon = { GameButtonIcon(R.drawable.ic_share) },
            )
        }
    }
}

/** Compact (DESIGN_SYSTEM.md §6.4a): one 56 dp row of New game, Undo and Share. */
@Composable
private fun CompactOverButtons(
    interactive: Boolean,
    measuring: Boolean,
    visuals: CardVisuals,
    actions: CardActions,
    focus: FocusRequester,
) {
    val button = DpSize(GameDimens.ControlHeight, GameDimens.ControlHeight)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameButton(
            onClick = actions.onNewGame,
            text = stringResource(R.string.new_game),
            style = GameButtonStyle.FILLED,
            modifier =
                Modifier.weight(1f)
                    .layerAlpha { visuals.button(NEW_GAME) }
                    .cardTag(CARD_NEW_GAME_TAG, measuring)
                    .cardFocus(focus, measuring),
            interactive = interactive,
        )
        GameIconButton(
            onClick = actions.onUndo,
            icon = R.drawable.ic_undo,
            contentDescription = stringResource(R.string.undo),
            style = GameIconButtonStyle.TONAL,
            modifier =
                Modifier.layerAlpha { visuals.button(UNDO) }.cardTag(CARD_UNDO_TAG, measuring),
            interactive = interactive,
            containerSize = button,
        )
        GameIconButton(
            onClick = actions.onShare,
            icon = R.drawable.ic_share,
            contentDescription = stringResource(R.string.share),
            style = GameIconButtonStyle.OUTLINED,
            modifier =
                Modifier.layerAlpha { visuals.button(SHARE) }.cardTag(CARD_SHARE_TAG, measuring),
            interactive = interactive,
            containerSize = button,
        )
    }
}

/** The game-over card's button indices in [CardVisuals.button]. */
private const val NEW_GAME = 0
private const val UNDO = 1
private const val SHARE = 2
