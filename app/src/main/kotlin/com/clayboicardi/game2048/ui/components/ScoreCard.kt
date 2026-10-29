package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * A Score or Best chip (DESIGN_SYSTEM.md §7.2; D-064, D-084): the `surfaceContainerHigh` container
 * in `shapes.large`, at least `ScoreChipMinWidth` × `ScoreChipMinHeight`, padded 12 × 8 dp, with
 * [label] above [value], centred. It is one accessibility node spoken as `a11y_chip_value`
 * ([spokenLabel] in sentence case, then [spokenValue] in Latin digits), with no live region.
 *
 * [container] gives the container colour at draw time, so the BEST chip's crossfade to
 * `tertiaryContainer` (MOTION_SPEC.md §3.6) recomposes nothing. [overlay] (the "+N" floats of the
 * Score chip) is drawn above the value, centred on it, with its first baseline on the value's, and
 * is not clipped by the chip.
 */
@Composable
fun ScoreChip(
    spokenLabel: String,
    spokenValue: String,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    container: ColorProducer? = null,
    overlay: @Composable () -> Unit = {},
    value: @Composable () -> Unit,
) {
    val description = stringResource(R.string.a11y_chip_value, spokenLabel, spokenValue)
    val shape = MaterialTheme.shapes.large
    val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = container ?: ColorProducer { fallback }
    Column(
        modifier =
            modifier
                .widthIn(min = GameDimens.ScoreChipMinWidth)
                .heightIn(min = GameDimens.ScoreChipMinHeight)
                .containerBehind(shape, fill)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = GameDimens.Space12, vertical = GameDimens.Space8),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        label()
        BaselineOverlay(value = value, overlay = overlay)
    }
}

/**
 * A chip label (DESIGN_SYSTEM.md §3.5, §3.7): [text] upper-cased with the current locale in
 * `labelMediumEmphasized`, at most 2 lines, coloured by [color] at draw time.
 */
@Composable
fun ScoreChipLabel(text: String, color: ColorProducer, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    BasicText(
        text = text.uppercase(locale),
        modifier = modifier,
        style = MaterialTheme.typography.labelMediumEmphasized.copy(textAlign = TextAlign.Center),
        maxLines = 2,
        color = color,
    )
}

/**
 * Score or Best digits (DESIGN_SYSTEM.md §7.2): [text] in the score numerals at [fontSize] (one
 * fitted size, [rememberScoreValueSize]), one line, never truncated, coloured by [color].
 */
@Composable
fun ScoreValueText(
    text: String,
    fontSize: TextUnit,
    color: ColorProducer,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = ScoreNumeralStyle.copy(fontSize = fontSize),
        softWrap = false,
        maxLines = 1,
        color = color,
    )
}

/**
 * The one size of a [digits]-digit Score or Best value (DESIGN_SYSTEM.md §7.2; D-084): the largest
 * of `ScoreValueMin..ScoreValueMax` in `ScoreValueStep` steps whose width in the score numerals is
 * at most [valueWidth] (INLINE: `ScoreChipMinWidth` − the chip's 2 × 12 dp padding), or
 * `ScoreValueMin`. `tnum` gives every digit one advance, so the width depends only on [digits]. It
 * is measured once per digit count, width, density and font scale.
 */
@Composable
fun rememberScoreValueSize(
    digits: Int,
    valueWidth: Dp = GameDimens.ScoreChipMinWidth - GameDimens.Space12 * 2,
): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(digits, valueWidth, density, measurer) {
        val text = "0".repeat(digits.coerceAtLeast(1))
        val maxPx = with(density) { valueWidth.roundToPx() }
        val step = GameDimens.ScoreValueStep.value
        var size = GameDimens.ScoreValueMax.value
        while (size > GameDimens.ScoreValueMin.value) {
            val style = ScoreNumeralStyle.copy(fontSize = size.sp)
            if (measurer.measure(text, style, softWrap = false, maxLines = 1).size.width <= maxPx) {
                break
            }
            size -= step
        }
        size.sp
    }
}

/**
 * The score numerals until M4's Google Sans Flex subset (D-076 fallback): `FontFamily.Default`,
 * bold, tabular lining figures, laid out left to right in every locale (D-104, DESIGN_SYSTEM.md
 * §3.7).
 */
val ScoreNumeralStyle: TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum, lnum",
        textDirection = TextDirection.Ltr,
    )

/**
 * The "+N", title and mini-tile numerals until M4 (D-076 fallback): `FontFamily.Default`, bold,
 * `tnum`, laid out left to right in every locale (D-104, DESIGN_SYSTEM.md §3.7): in an RTL
 * paragraph the leading "+" of "+N", a neutral, would be drawn after the digits.
 */
val GameNumeralStyle: TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        textDirection = TextDirection.Ltr,
    )

/** Fills [shape] behind the content with [color], read at draw time; nothing is clipped. */
private fun Modifier.containerBehind(shape: Shape, color: ColorProducer): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind { drawOutline(outline, color()) }
}

/**
 * [value], with [overlay] measured without constraints and placed above it: centred horizontally,
 * its first baseline on the value's first baseline (top-aligned when either has no baseline). The
 * box has the value's size and a minimum height of one 28 sp score line, so the fitted size never
 * changes the chip's height (DESIGN_SYSTEM.md §7.2).
 */
@Composable
private fun BaselineOverlay(value: @Composable () -> Unit, overlay: @Composable () -> Unit) {
    val lineHeight = rememberScoreLineHeight()
    Layout(
        contents = listOf({ Box(contentAlignment = Alignment.Center) { value() } }, overlay),
    ) { (valueMeasurables, overlayMeasurables), constraints ->
        val minHeight = lineHeight.coerceAtMost(constraints.maxHeight)
        val valueBox =
            valueMeasurables.first().measure(constraints.copy(minWidth = 0, minHeight = minHeight))
        val overlays = overlayMeasurables.map { it.measure(Constraints()) }
        val width = valueBox.width.coerceAtLeast(constraints.minWidth)
        val height = valueBox.height.coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            valueBox.place((width - valueBox.width) / 2, 0)
            val valueBaseline = valueBox[FirstBaseline]
            for (o in overlays) {
                val baseline = o[FirstBaseline]
                val y =
                    if (
                        valueBaseline == AlignmentLine.Unspecified ||
                            baseline == AlignmentLine.Unspecified
                    ) {
                        0
                    } else {
                        valueBaseline - baseline
                    }
                o.place((width - o.width) / 2, y)
            }
        }
    }
}

/** The height in px of one line of the score numerals at `ScoreValueMax`. */
@Composable
private fun rememberScoreLineHeight(): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(density, measurer) {
        val style = ScoreNumeralStyle.copy(fontSize = GameDimens.ScoreValueMax)
        measurer.measure("0", style, softWrap = false, maxLines = 1).size.height
    }
}
