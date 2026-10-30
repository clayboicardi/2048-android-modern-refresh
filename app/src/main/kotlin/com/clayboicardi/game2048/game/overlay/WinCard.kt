package com.clayboicardi.game2048.game.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.game.board.TileLabels
import com.clayboicardi.game2048.ui.components.GameButton
import com.clayboicardi.game2048.ui.components.GameButtonStyle
import com.clayboicardi.game2048.ui.components.GameIconButton
import com.clayboicardi.game2048.ui.components.GameIconButtonStyle
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.GameShapes
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.LocalTileSkin

/**
 * The win card (D-008, D-064, D-081, D-200; DESIGN_SYSTEM.md §6.3, §6.3a, §7.3): "You win!", the
 * hero [WinBadge] of the target and, Regular, Keep going (`Button`) above New game (tonal) and Undo
 * (text), weights 1:1, or, [compact], one 56 dp row of Keep going (weight 1), New game
 * (`FilledTonalIconButton`, `ic_restart`) and Undo (`IconButton`, `ic_undo`); the icon-only Share
 * (48 dp, `ic_share`) sits at its top end. `surfaceContainerHigh` `ElevatedCard` in `extraLarge`
 * with the 6 dp elevation (§4.5).
 *
 * Every button is composed enabled; until [interactive] its clicks are ignored, it carries
 * `disabled()` and cannot take focus (D-200), and on the flip [focus] (Keep going) takes focus
 * (D-081; [CardFocusEffect]). Each button fades in with its [CardVisuals.button] alpha (layout
 * order: Keep going, New game, Undo, Share). `paneTitle` = `win_title`. A [measuring] copy has no
 * semantics, focus or effects ([OverlayCard]).
 */
@Composable
internal fun WinCard(
    win: OverlayUi.Win,
    compact: Boolean,
    interactive: Boolean,
    measuring: Boolean,
    visuals: CardVisuals,
    actions: CardActions,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.win_title)
    val live = interactive && !measuring
    CardFocusEffect(interactive, measuring, focus)
    ElevatedCard(
        modifier = modifier.cardSemantics(title, WIN_CARD_TAG, measuring),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation =
            CardDefaults.elevatedCardElevation(defaultElevation = GameDimens.OverlayCardElevation),
    ) {
        Box {
            val padding = if (compact) GameDimens.CardPaddingCompact else GameDimens.Space24
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CardTitle(title, compact, ShareInset, measuring)
                Spacer(Modifier.height(if (compact) GameDimens.Space12 else GameDimens.Space16))
                WinBadge(win.targetExp, badgeSize(compact), visuals)
                Spacer(Modifier.height(if (compact) GameDimens.Space16 else GameDimens.Space24))
                if (compact) {
                    CompactWinButtons(live, measuring, visuals, actions, focus)
                } else {
                    RegularWinButtons(live, measuring, visuals, actions, focus)
                }
            }
            GameIconButton(
                onClick = actions.onShare,
                icon = R.drawable.ic_share,
                contentDescription = stringResource(R.string.share),
                style = GameIconButtonStyle.PLAIN,
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(GameDimens.Space4)
                        .layerAlpha { visuals.button(SHARE) }
                        .cardTag(CARD_SHARE_TAG, measuring),
                interactive = live,
                containerSize = DpSize(GameDimens.CardShareButton, GameDimens.CardShareButton),
            )
        }
    }
}

/** Regular (DESIGN_SYSTEM.md §6.3): Keep going, then New game and Undo side by side. */
@Composable
private fun RegularWinButtons(
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
            onClick = actions.onKeepGoing,
            text = stringResource(R.string.keep_going),
            style = GameButtonStyle.FILLED,
            modifier =
                Modifier.fillMaxWidth()
                    .layerAlpha { visuals.button(KEEP_GOING) }
                    .cardTag(KEEP_GOING_TAG, measuring)
                    .cardFocus(focus, measuring),
            interactive = interactive,
        )
        Spacer(Modifier.height(GameDimens.Space8))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        ) {
            GameButton(
                onClick = actions.onNewGame,
                text = stringResource(R.string.new_game),
                style = GameButtonStyle.TONAL,
                modifier =
                    Modifier.weight(1f)
                        .layerAlpha { visuals.button(NEW_GAME) }
                        .cardTag(CARD_NEW_GAME_TAG, measuring),
                interactive = interactive,
            )
            GameButton(
                onClick = actions.onUndo,
                text = stringResource(R.string.undo),
                style = GameButtonStyle.TEXT,
                modifier =
                    Modifier.weight(1f)
                        .layerAlpha { visuals.button(UNDO) }
                        .cardTag(CARD_UNDO_TAG, measuring),
                interactive = interactive,
            )
        }
    }
}

/** Compact (DESIGN_SYSTEM.md §6.3a): one 56 dp row of Keep going, New game and Undo. */
@Composable
private fun CompactWinButtons(
    interactive: Boolean,
    measuring: Boolean,
    visuals: CardVisuals,
    actions: CardActions,
    focus: FocusRequester,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameButton(
            onClick = actions.onKeepGoing,
            text = stringResource(R.string.keep_going),
            style = GameButtonStyle.FILLED,
            modifier =
                Modifier.weight(1f)
                    .layerAlpha { visuals.button(KEEP_GOING) }
                    .cardTag(KEEP_GOING_TAG, measuring)
                    .cardFocus(focus, measuring),
            interactive = interactive,
        )
        GameIconButton(
            onClick = actions.onNewGame,
            icon = R.drawable.ic_restart,
            contentDescription = stringResource(R.string.new_game),
            style = GameIconButtonStyle.TONAL,
            modifier =
                Modifier.layerAlpha { visuals.button(NEW_GAME) }
                    .cardTag(CARD_NEW_GAME_TAG, measuring),
            interactive = interactive,
        )
        GameIconButton(
            onClick = actions.onUndo,
            icon = R.drawable.ic_undo,
            contentDescription = stringResource(R.string.undo),
            style = GameIconButtonStyle.PLAIN,
            modifier =
                Modifier.layerAlpha { visuals.button(UNDO) }.cardTag(CARD_UNDO_TAG, measuring),
            interactive = interactive,
            containerSize = DpSize(GameDimens.ControlHeight, GameDimens.ControlHeight),
        )
    }
}

/**
 * The win card's hero badge (D-064; DESIGN_SYSTEM.md §4.4, §6.3; MOTION_SPEC.md §3.11): the
 * graphics-shapes `Morph` from the 12-vertex circle to the 12-point cookie at [CardVisuals.morph]
 * (clamped to [0, 1]), turned by [CardVisuals.rotation], filled with `palette.fill(targetExp)` and,
 * in high contrast, outlined; the target's label in `palette.ink(targetExp)`, sized by the tile
 * label rule with t = 0.8 · [size] (§3.3). The whole badge scales by [CardVisuals.badgeScale] in
 * its placement layer. `Morph.toPath` writes into one remembered path, and nothing is allocated per
 * frame (MOTION_SPEC.md §7.3). Decorative: the card's title names the win.
 */
@Composable
internal fun WinBadge(
    targetExp: Int,
    size: Dp,
    visuals: CardVisuals,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val measurer = rememberTextMeasurer()
    val morph = remember { GameShapes.winBadgeMorph() }
    val androidPath = remember { android.graphics.Path() }
    val path = remember(androidPath) { androidPath.asComposePath() }
    val label = TileLabels.label(targetExp, size.value * BADGE_LABEL_FRACTION)
    Spacer(
        modifier
            .size(size)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeWithLayer(0, 0) {
                        val s = visuals.badgeScale()
                        scaleX = s
                        scaleY = s
                    }
                }
            }
            .drawWithCache {
                val style =
                    GameFonts.tile(skin, label.text.length)
                        .copy(fontSize = label.fontSizeDp.dp.toSp())
                val text = measurer.measure(label.text, style, softWrap = false, maxLines = 1)
                val textTopLeft =
                    Offset(
                        (this.size.width - text.size.width) / 2,
                        (this.size.height - text.size.height) / 2,
                    )
                val radius = this.size.minDimension / 2
                val fill = palette.fill(targetExp)
                val ink = palette.ink(targetExp)
                val outline = skin.tileOutline
                val stroke = Stroke(GameDimens.HcOutlineWidth.toPx() / radius)
                onDrawBehind {
                    morph.toPath(visuals.morph(), androidPath)
                    translate(center.x, center.y) {
                        rotate(visuals.rotation(), Offset.Zero) {
                            scale(radius, radius, Offset.Zero) {
                                drawPath(path, fill)
                                if (outline != null) drawPath(path, outline, style = stroke)
                            }
                        }
                    }
                    drawText(text, color = ink, topLeft = textTopLeft)
                }
            },
    )
}

/** 96 dp (72 below a 480 dp window height, 120 from an 840 dp width); 64 dp Compact (§6.3a). */
@Composable
private fun badgeSize(compact: Boolean): Dp {
    val window = LocalWindowInfo.current.containerDpSize
    return when {
        compact -> GameDimens.HeroBadgeCompact
        window.width >= GameDimens.WidthExpandedMin -> GameDimens.HeroBadgeExpanded
        window.height < GameDimens.HeightMediumMin -> GameDimens.HeroBadgeShort
        else -> GameDimens.HeroBadge
    }
}

/** A card button's test [tag], none in a [measuring] copy. */
internal fun Modifier.cardTag(tag: String, measuring: Boolean): Modifier =
    if (measuring) this else testTag(tag)

/** The primary button's focus target (D-081), none in a [measuring] copy. */
internal fun Modifier.cardFocus(focus: FocusRequester, measuring: Boolean): Modifier =
    if (measuring) this else focusRequester(focus)

/** The win card's button indices in [CardVisuals.button]. */
private const val KEEP_GOING = 0
private const val NEW_GAME = 1
private const val UNDO = 2
private const val SHARE = 3

/** The badge label is sized like a tile of 0.8 × the badge (DESIGN_SYSTEM.md §6.3). */
private const val BADGE_LABEL_FRACTION = 0.8f

/** Clear of the 48 dp Share at the top end: its overlap with the content area, rounded up. */
private val ShareInset: Dp = GameDimens.CardShareButton - GameDimens.Space16
