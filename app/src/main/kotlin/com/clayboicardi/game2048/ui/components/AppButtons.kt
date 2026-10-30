package com.clayboicardi.game2048.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import com.clayboicardi.game2048.ui.theme.GameDimens
import kotlinx.coroutines.flow.Flow

/**
 * A labelled game button (DESIGN_SYSTEM.md §7.3; D-084): the Material 3 button of [style] with the
 * Expressive press morph (`ButtonDefaults.shapes()`), at least `ControlHeight` (56 dp) tall with
 * `contentPaddingFor(ControlHeight)`, the label on at most 2 lines, an optional leading [icon]
 * (`GameButtonIcon`) and the focus ring of the resting shape (§7.7).
 *
 * [interactive] serves the card buttons (D-200): they are composed enabled, so they never flash
 * from disabled to enabled colours, and until the card's entry makes them interactive their clicks
 * are ignored, they carry `disabled()` semantics and they cannot take focus. Every other caller
 * leaves it true. The focused state layer shows only in keyboard input mode, like the ring (D-240).
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
    val interactions = rememberKeyboardFocusInteractions()
    val content: @Composable RowScope.() -> Unit = { ButtonLabel(text, icon) }
    when (style) {
        GameButtonStyle.FILLED ->
            Button(
                click,
                shapes,
                chrome,
                enabled,
                contentPadding = padding,
                interactionSource = interactions,
                content = content,
            )
        GameButtonStyle.TONAL ->
            FilledTonalButton(
                click,
                shapes,
                chrome,
                enabled,
                contentPadding = padding,
                interactionSource = interactions,
                content = content,
            )
        GameButtonStyle.OUTLINED ->
            OutlinedButton(
                click,
                shapes,
                chrome,
                enabled,
                contentPadding = padding,
                interactionSource = interactions,
                content = content,
            )
        GameButtonStyle.TEXT ->
            TextButton(
                click,
                shapes,
                chrome,
                enabled,
                contentPadding = padding,
                interactionSource = interactions,
                content = content,
            )
    }
}

/**
 * An icon-only game button (DESIGN_SYSTEM.md §7.3): the Material 3 icon button of [style] with
 * `IconButtonDefaults.shapes()`, [containerSize] (`IconButtonDefaults.mediumContainerSize()` unless
 * given), [icon] described by [contentDescription] (the string DESIGN_SYSTEM.md §6 names) and drawn
 * at [iconSize] (the icon button's default when unspecified; the pad's `GameDimens.PadIcon`), the
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
    iconSize: Dp = Dp.Unspecified,
) {
    val shapes = IconButtonDefaults.shapes()
    val click = if (interactive) onClick else ({})
    val chrome = modifier.size(containerSize).controlModifier(shapes.shape, interactive)
    val interactions = rememberKeyboardFocusInteractions()
    val content: @Composable () -> Unit = {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            modifier = if (iconSize.isSpecified) Modifier.size(iconSize) else Modifier,
        )
    }
    when (style) {
        GameIconButtonStyle.TONAL ->
            FilledTonalIconButton(
                click,
                shapes,
                chrome,
                enabled,
                interactionSource = interactions,
                content = content,
            )
        GameIconButtonStyle.OUTLINED ->
            OutlinedIconButton(
                click,
                shapes,
                chrome,
                enabled,
                interactionSource = interactions,
                content = content,
            )
        GameIconButtonStyle.PLAIN ->
            IconButton(
                click,
                shapes,
                chrome,
                enabled,
                interactionSource = interactions,
                content = content,
            )
    }
}

/**
 * The interaction source of a game button (D-240): it passes every interaction to the button's
 * indication except a `FocusInteraction.Focus` outside keyboard input mode, so the Material 3
 * focused state layer, like the focus ring (D-227), shows only to a keyboard user. Focus itself
 * still moves, for example to a card's primary button (D-081); `Unfocus` always passes, so a focus
 * shown in keyboard mode always ends.
 */
@Composable
private fun rememberKeyboardFocusInteractions(): MutableInteractionSource {
    val inputMode = LocalInputModeManager.current
    return remember(inputMode) { KeyboardFocusInteractions(MutableInteractionSource(), inputMode) }
}

private class KeyboardFocusInteractions(
    private val source: MutableInteractionSource,
    private val inputMode: InputModeManager,
) : MutableInteractionSource {
    override val interactions: Flow<Interaction>
        get() = source.interactions

    override suspend fun emit(interaction: Interaction) {
        if (passes(interaction)) source.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean =
        !passes(interaction) || source.tryEmit(interaction)

    private fun passes(interaction: Interaction): Boolean =
        interaction !is FocusInteraction.Focus || inputMode.inputMode == InputMode.Keyboard
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
        maxLines = LABEL_MAX_LINES,
    )
}

/** A game button's label wraps to at most two lines (DESIGN_SYSTEM.md §7.3; D-084). */
private const val LABEL_MAX_LINES = 2

/**
 * The narrowest a [GameButton] of [text] (no icon) can be without cutting a word of its label, in
 * px: the label's longest word in the button's text style plus `contentPaddingFor(ControlHeight)`
 * (D-084). A row that cannot give the button this much puts it on a row of its own (D-252).
 */
@Composable
fun rememberGameButtonMinWidth(text: String): Int {
    val measurer = rememberTextMeasurer()
    val style = ButtonDefaults.textStyleFor(GameDimens.ControlHeight)
    val padding = ButtonDefaults.contentPaddingFor(GameDimens.ControlHeight, false)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return remember(text, style, padding, density, direction, measurer) {
        val word = text.split(' ').maxOf { measurer.measure(it, style, maxLines = 1).size.width }
        val sides =
            padding.calculateStartPadding(direction) + padding.calculateEndPadding(direction)
        word + with(density) { sides.roundToPx() }
    }
}

/**
 * Whether a [GameButton] of [text] (no icon), [width] wide, lays its label out on at most two lines
 * without breaking a word (D-084, D-266): no word of the label is wider than
 * [rememberGameButtonMinWidth] allows, and the label in the button's text style wraps to at most
 * two lines at [width] minus `contentPaddingFor(ControlHeight)`. A column that fails this puts the
 * button on a row of its own.
 */
@Composable
fun rememberGameButtonLabelFits(text: String, width: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val style = ButtonDefaults.textStyleFor(GameDimens.ControlHeight)
    val padding = ButtonDefaults.contentPaddingFor(GameDimens.ControlHeight, false)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val narrowest = rememberGameButtonMinWidth(text)
    return remember(text, width, style, padding, density, direction, measurer, narrowest) {
        val widthPx = with(density) { width.roundToPx() }
        val sides =
            with(density) {
                (padding.calculateStartPadding(direction) + padding.calculateEndPadding(direction))
                    .roundToPx()
            }
        val label = Constraints(maxWidth = (widthPx - sides).coerceAtLeast(0))
        narrowest <= widthPx &&
            measurer.measure(text, style, constraints = label).lineCount <= LABEL_MAX_LINES
    }
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
