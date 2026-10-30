package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * While the element after this modifier in the chain has focus, draws a `FocusRingWidth` (2 dp)
 * `primary` stroke outside it, `FocusRingGap` (2 dp) away, with the outset shape's corner radius
 * the element's radius + 2 + 1 dp (DESIGN_SYSTEM.md §7.7; D-053, D-084). The ring sits on the
 * background, never on the board or a tile. Place it before the focus target:
 * `Modifier.focusRing(shape).focusable()`. Applied by every `ui/components` wrapper and to the
 * board (`shape` = the board shape).
 *
 * The ring shows only in keyboard input mode (`LocalInputModeManager`), like a `:focus-visible`
 * indicator: the board takes focus whenever Game appears (D-053), and a touch player sees no ring.
 * While it shows, the element is drawn above its later siblings, so a list row's ring is not
 * covered by the next row's container (D-267). Its colour is the theme's `primary`, read as
 * `LocalTextSelectionColors.handleColor`, which `MaterialTheme` sets to `colorScheme.primary`
 * (material3 1.5.0-beta02, verified from the class files): a modifier node cannot read
 * `MaterialTheme.colorScheme` itself.
 */
fun Modifier.focusRing(shape: Shape): Modifier = this then FocusRingElement(shape)

private data class FocusRingElement(val shape: Shape) : ModifierNodeElement<FocusRingNode>() {
    override fun create(): FocusRingNode = FocusRingNode(shape)

    override fun update(node: FocusRingNode) {
        node.update(shape)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRing"
        properties["shape"] = shape
    }
}

/**
 * Observes the next focus target and draws the ring after the content while it is focused, placing
 * the element above its later siblings meanwhile.
 */
private class FocusRingNode(private var shape: Shape) :
    Modifier.Node(),
    LayoutModifierNode,
    DrawModifierNode,
    FocusEventModifierNode,
    CompositionLocalConsumerModifierNode {
    private var focused = false

    /** The ring's path, rebuilt only when the size, direction, density or shape changes. */
    private val ring = Path()
    private var ringKey: RingKey? = null

    fun update(shape: Shape) {
        if (shape != this.shape) {
            this.shape = shape
            ringKey = null
            invalidateDraw()
        }
    }

    /**
     * The ring shows: focused, in keyboard input mode (a snapshot read, observed by the caller).
     */
    private val shown: Boolean
        get() = focused && currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused != focused) {
            focused = focusState.isFocused
            invalidateDraw()
            invalidatePlacement()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0, zIndex = if (shown) RING_Z_INDEX else 0f)
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!shown) return
        val key = RingKey(size, layoutDirection, density, shape)
        if (key != ringKey) {
            ringKey = key
            val inset = (GameDimens.FocusRingGap + GameDimens.FocusRingWidth / 2).toPx()
            ring.reset()
            ring.addRoundRect(outsetRect(shape.createOutline(size, layoutDirection, this), inset))
        }
        drawPath(
            path = ring,
            color = currentValueOf(LocalTextSelectionColors).handleColor,
            style = Stroke(width = GameDimens.FocusRingWidth.toPx()),
        )
    }
}

/** What the cached ring path depends on. */
private data class RingKey(
    val size: Size,
    val layoutDirection: LayoutDirection,
    val density: Float,
    val shape: Shape,
)

/** The outline grown by [inset] on every side, each corner radius grown by [inset] too. */
private fun outsetRect(outline: Outline, inset: Float): RoundRect =
    when (outline) {
        is Outline.Rounded -> {
            val r = outline.roundRect
            RoundRect(
                left = r.left - inset,
                top = r.top - inset,
                right = r.right + inset,
                bottom = r.bottom + inset,
                topLeftCornerRadius = r.topLeftCornerRadius.grow(inset),
                topRightCornerRadius = r.topRightCornerRadius.grow(inset),
                bottomRightCornerRadius = r.bottomRightCornerRadius.grow(inset),
                bottomLeftCornerRadius = r.bottomLeftCornerRadius.grow(inset),
            )
        }
        is Outline.Rectangle -> RoundRect(outline.rect.inflate(inset), CornerRadius(inset))
        // A generic outline: its bounds, with the outset radius only.
        is Outline.Generic -> RoundRect(outline.bounds.inflate(inset), CornerRadius(inset))
    }

private fun CornerRadius.grow(by: Float): CornerRadius = CornerRadius(x + by, y + by)

/** Above every sibling at the default z-index 0, so the shown ring is drawn over them. */
private const val RING_Z_INDEX = 1f
