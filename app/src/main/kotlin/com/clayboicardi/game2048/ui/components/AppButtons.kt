package com.clayboicardi.game2048.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * A labelled game button (DESIGN_SYSTEM.md §7.3; D-084): the Material 3 button of [style] with the
 * Expressive press morph (`ButtonDefaults.shapes()`), at least `ControlHeight` (56 dp) tall with
 * `contentPaddingFor(ControlHeight)`, the label on at most 2 lines, an optional leading [icon]
 * (`GameButtonIcon`) and the focus ring of the resting shape (§7.7).
 *
 * [interactive] serves the card buttons (D-200): they are composed enabled, so they never flash
 * from disabled to enabled colours, and until the card's entry makes them interactive their clicks
 * are ignored, they carry `disabled()` semantics and they cannot take focus. Every other caller
 * leaves it true.
 */
@Composable
fun GameButton(
    onClick: () -> Unit,
    text: String,
    style: GameButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val shapes = ButtonDefaults.shapes()
    val click = if (interactive) onClick else ({})
    val chrome = modifier.controlModifier(shapes.shape, interactive)
    val padding = ButtonDefaults.contentPaddingFor(GameDimens.ControlHeight, icon != null)
    val content: @Composable RowScope.() -> Unit = { ButtonLabel(text, icon) }
    when (style) {
        GameButtonStyle.FILLED ->
            Button(click, shapes, chrome, enabled, contentPadding = padding, content = content)
        GameButtonStyle.TONAL ->
            FilledTonalButton(
                click,
                shapes,
                chrome,
                enabled,
                contentPadding = padding,
                content = content,
            )
        GameButtonStyle.OUTLINED ->
            OutlinedButton(
                click,
                shapes,
                chrome,
                enabled,
                contentPadding = padding,
                content = content,
            )
        GameButtonStyle.TEXT ->
            TextButton(click, shapes, chrome, enabled, contentPadding = padding, content = content)
    }
}

/**
 * An icon-only game button (DESIGN_SYSTEM.md §7.3): the Material 3 icon button of [style] with
 * `IconButtonDefaults.shapes()`, [containerSize] (`IconButtonDefaults.mediumContainerSize()` unless
 * given), [icon] described by [contentDescription] (the string DESIGN_SYSTEM.md §6 names), the
 * focus ring and the [interactive] gate of [GameButton].
 */
@Composable
fun GameIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    contentDescription: String,
    style: GameIconButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
    containerSize: DpSize = IconButtonDefaults.mediumContainerSize(),
) {
    val shapes = IconButtonDefaults.shapes()
    val click = if (interactive) onClick else ({})
    val chrome = modifier.size(containerSize).controlModifier(shapes.shape, interactive)
    val content: @Composable () -> Unit = {
        Icon(painterResource(icon), contentDescription = contentDescription)
    }
    when (style) {
        GameIconButtonStyle.TONAL ->
            FilledTonalIconButton(click, shapes, chrome, enabled, content = content)
        GameIconButtonStyle.OUTLINED ->
            OutlinedIconButton(click, shapes, chrome, enabled, content = content)
        GameIconButtonStyle.PLAIN -> IconButton(click, shapes, chrome, enabled, content = content)
    }
}

/**
 * The leading icon of a labelled game button, at `ButtonDefaults.iconSizeFor(ControlHeight)`. It is
 * decorative: the button's label names it (DESIGN_SYSTEM.md §8.1).
 */
@Composable
fun GameButtonIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(ButtonDefaults.iconSizeFor(GameDimens.ControlHeight)),
    )
}

@Composable
private fun RowScope.ButtonLabel(text: String, icon: (@Composable () -> Unit)?) {
    if (icon != null) {
        icon()
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(GameDimens.ControlHeight)))
    }
    Text(
        text = text,
        style = ButtonDefaults.textStyleFor(GameDimens.ControlHeight),
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

/** The minimum height, the focus ring and, while not [interactive], no focus and `disabled()`. */
private fun Modifier.controlModifier(shape: Shape, interactive: Boolean): Modifier =
    heightIn(min = GameDimens.ControlHeight)
        .focusRing(shape)
        .focusProperties { canFocus = interactive }
        .then(if (interactive) Modifier else Modifier.semantics { disabled() })

/** The Material 3 button behind a [GameButton] (DESIGN_SYSTEM.md §7.3). */
enum class GameButtonStyle {
    /** `Button`: Keep going, the game-over card's New game. */
    FILLED,

    /** `FilledTonalButton`: Undo (controls bar), the cards' second buttons. */
    TONAL,

    /** `OutlinedButton`: New game (controls bar), the game-over card's Share. */
    OUTLINED,

    /** `TextButton`: the win card's Undo. */
    TEXT,
}

/** The Material 3 icon button behind a [GameIconButton] (DESIGN_SYSTEM.md §7.3). */
enum class GameIconButtonStyle {
    /** `FilledTonalIconButton`: More, compact card buttons. */
    TONAL,

    /** `OutlinedIconButton`: the compact game-over card's Share. */
    OUTLINED,

    /** `IconButton`: the win card's Share and compact Undo. */
    PLAIN,
}
