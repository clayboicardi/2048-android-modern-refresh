package com.clayboicardi.game2048.game.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.GameSummary
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.game.header.scoreLines
import com.clayboicardi.game2048.ui.components.GameButton
import com.clayboicardi.game2048.ui.components.GameButtonIcon
import com.clayboicardi.game2048.ui.components.GameButtonStyle
import com.clayboicardi.game2048.ui.components.GameIconButton
import com.clayboicardi.game2048.ui.components.GameIconButtonStyle
import com.clayboicardi.game2048.ui.components.MiniTile
import com.clayboicardi.game2048.ui.components.ScoreChipLabel
import com.clayboicardi.game2048.ui.components.ScoreValueLines
import com.clayboicardi.game2048.ui.components.ScoreValueText
import com.clayboicardi.game2048.ui.components.formatCount
import com.clayboicardi.game2048.ui.components.formatElapsed
import com.clayboicardi.game2048.ui.components.rememberGameButtonMinWidth
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.GameTextStyles
import com.clayboicardi.game2048.ui.theme.LocalTileSkin

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
            StatBox(stringResource(R.string.best_label), Modifier.weight(1f)) {
                FittedValue(
                    summary.best.toString(),
                    GameDimens.CardScoreValueCompactMax,
                    { primary },
                )
            }
            StatBox(stringResource(R.string.best_tile), Modifier.weight(1f)) {
                MiniTile(summary.bestTileExp, GameDimens.MiniTileMedium)
            }
        }
    }
}

/**
 * Compact (DESIGN_SYSTEM.md §6.4a): three equal boxes, SCORE, BEST and BEST TILE, in one row; in a
 * column of full-width boxes when a third of the row cannot hold a label's longest word on a line
 * (a large font scale on a small board, D-252), so no label is cut.
 */
@Composable
private fun CompactStats(summary: GameSummary) {
    val primary = MaterialTheme.colorScheme.onSurface
    val labels =
        listOf(
            stringResource(R.string.score_label),
            stringResource(R.string.best_label),
            stringResource(R.string.best_tile),
        )
    val boxes: List<@Composable (Modifier) -> Unit> =
        listOf(
            { modifier ->
                StatBox(labels[0], modifier) {
                    FittedValue(
                        summary.score.toString(),
                        GameDimens.CardScoreValueCompactMax,
                        { primary },
                    )
                }
            },
            { modifier ->
                StatBox(labels[1], modifier) {
                    FittedValue(
                        summary.best.toString(),
                        GameDimens.CardScoreValueCompactMax,
                        { primary },
                    )
                }
            },
            { modifier ->
                StatBox(labels[2], modifier) {
                    MiniTile(summary.bestTileExp, GameDimens.MiniTileSmall)
                }
            },
        )
    val measurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val style = GameTextStyles.chipLabel
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gaps = with(density) { (GameDimens.Space8 * 2).roundToPx() }
        val padding = with(density) { (GameDimens.Space8 * 2).roundToPx() }
        val third = (constraints.maxWidth - gaps) / labels.size - padding
        val stacked =
            remember(labels, third, style, density, measurer, locale) {
                labels.any { label ->
                    label.uppercase(locale).split(' ').any { word ->
                        measurer.measure(word, style, maxLines = 1).size.width > third
                    }
                }
            }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(GameDimens.Space8)) {
                for (box in boxes) box(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8)) {
                for (box in boxes) box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A `surfaceContainerHighest` box in `shapes.large` (16 dp), at least `CardStatBoxMinHeight` tall,
 * sized by [modifier] (weight 1 in a row): its [label] (`ScoreChipLabel`, upper-cased,
 * `onSurfaceVariant`) above [value].
 */
@Composable
private fun StatBox(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            modifier
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
 * (DESIGN_SYSTEM.md §6.4, §6.4a, §7.2). A value that does not fit even at `ScoreValueMin` wraps
 * between digits at that size, in the `scoreLines` of the digits a line holds, so no digit is ever
 * cut off (D-084).
 */
@Composable
private fun FittedValue(text: String, max: TextUnit, color: ColorProducer) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val skin = LocalTileSkin.current
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val fitted =
            remember(text, max, widthPx, density, measurer, skin) {
                fitValue(text, max, widthPx) { line, sp ->
                    val style = GameFonts.score(skin).copy(fontSize = sp.sp)
                    measurer.measure(line, style, softWrap = false, maxLines = 1).size.width
                }
            }
        if (fitted.lines.size == 1) {
            ScoreValueText(text, fitted.size, color)
        } else {
            // One node that speaks the whole value, as the one-line value does (D-084).
            ScoreValueLines(
                Modifier.semantics(mergeDescendants = true) { contentDescription = text },
            ) {
                for (line in fitted.lines) ScoreValueText(line, fitted.size, color)
            }
        }
    }
}

/** A fitted value: its one size and its lines (one, unless it wraps at `ScoreValueMin`). */
private class FittedText(val size: TextUnit, val lines: List<String>)

/**
 * The [FittedText] of [text] in [widthPx], where [width] measures a line at a size in sp: the
 * largest size from [max] down to `ScoreValueMin` that fits on one line, or `ScoreValueMin` with
 * the digits cut into lines of as many `tnum` advances as fit.
 */
private inline fun fitValue(
    text: String,
    max: TextUnit,
    widthPx: Int,
    width: (String, Float) -> Int,
): FittedText {
    val min = GameDimens.ScoreValueMin.value
    var sp = max.value
    while (sp > min && width(text, sp) > widthPx) sp -= GameDimens.ScoreValueStep.value
    sp = sp.coerceAtLeast(min)
    val lines =
        if (sp > min || width(text, min) <= widthPx) {
            listOf(text)
        } else {
            val advance = width(ADVANCE_SAMPLE, min) / ADVANCE_SAMPLE.length.toFloat()
            scoreLines(text, (widthPx / advance).toInt().coerceAtLeast(1))
        }
    return FittedText(sp.sp, lines)
}

/** Ten digits: their width over ten is one `tnum` advance, without one digit's side bearings. */
private const val ADVANCE_SAMPLE = "0000000000"

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

/**
 * The Compact card's buttons (DESIGN_SYSTEM.md §6.4a): New game (`Button`, weight 1), Undo
 * (`FilledTonalIconButton`, `ic_undo`) and Share (`OutlinedIconButton`, `ic_share`) in one 56 dp
 * row; when that row leaves New game too narrow for a word of its label (a large font scale on a
 * small board), New game takes a row of its own above Undo and Share (D-252).
 */
@Composable
private fun CompactOverButtons(
    interactive: Boolean,
    measuring: Boolean,
    visuals: CardVisuals,
    actions: CardActions,
    focus: FocusRequester,
) {
    val button = DpSize(GameDimens.ControlHeight, GameDimens.ControlHeight)
    val newGameText = stringResource(R.string.new_game)
    val newGameMin = rememberGameButtonMinWidth(newGameText)
    val newGame: @Composable (Modifier) -> Unit = { modifier ->
        GameButton(
            onClick = actions.onNewGame,
            text = newGameText,
            style = GameButtonStyle.FILLED,
            modifier =
                modifier
                    .layerAlpha { visuals.button(NEW_GAME) }
                    .cardTag(CARD_NEW_GAME_TAG, measuring)
                    .cardFocus(focus, measuring),
            interactive = interactive,
        )
    }
    val icons: @Composable () -> Unit = {
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val others =
            with(LocalDensity.current) { (button.width * 2 + GameDimens.Space8 * 2).roundToPx() }
        if (constraints.maxWidth - others >= newGameMin) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                newGame(Modifier.weight(1f))
                icons()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GameDimens.Space8)) {
                newGame(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(GameDimens.Space8, Alignment.CenterHorizontally),
                ) {
                    icons()
                }
            }
        }
    }
}

/** The game-over card's button indices in [CardVisuals.button]. */
private const val NEW_GAME = 0
private const val UNDO = 1
private const val SHARE = 2
