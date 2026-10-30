package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.header.ScoreFit
import com.clayboicardi.game2048.game.header.scoreLines
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A Score or Best chip (DESIGN_SYSTEM.md §7.2; D-064, D-084): the `surfaceContainerHigh` container
 * in `shapes.large`, at least `ScoreChipMinWidth` × `ScoreChipMinHeight`, padded 12 × 8 dp, with
 * [label] above [value], centred. Its width comes from [modifier]: content-sized up to the fit's
 * `chipSlot` in INLINE, the slot in OWN_ROW, the full width in STACKED (`GameHeader`). It is one
 * accessibility node spoken as `a11y_chip_value` ([spokenLabel] in sentence case, then
 * [spokenValue] in Latin digits), with no live region.
 *
 * [container] gives the container colour at draw time, so the BEST chip's crossfade to
 * `tertiaryContainer` (MOTION_SPEC.md §3.6) recomposes nothing. [overlay] (the "+N" floats of the
 * Score chip) is drawn above the value, centred on it, with its first baseline on the value's, and
 * is not clipped by the chip.
 *
 * [reserved] holds labels the chip also makes room for (the BEST chip's labels of both styles,
 * D-268): each is measured at the chip's width and never placed, and the chip is at least as wide
 * as the widest and as tall as the tallest of them above the value. The label shown and the value
 * stay one unit, centred in the chip, with no reserved space between them.
 */
@Composable
fun ScoreChip(
    spokenLabel: String,
    spokenValue: String,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    container: ColorProducer? = null,
    overlay: @Composable () -> Unit = {},
    reserved: @Composable () -> Unit = {},
    value: @Composable () -> Unit,
) {
    val description = stringResource(R.string.a11y_chip_value, spokenLabel, spokenValue)
    val shape = MaterialTheme.shapes.large
    val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = container ?: ColorProducer { fallback }
    Layout(
        contents = listOf(label, { BaselineOverlay(value = value, overlay = overlay) }, reserved),
        modifier =
            modifier
                .widthIn(min = GameDimens.ScoreChipMinWidth)
                .heightIn(min = GameDimens.ScoreChipMinHeight)
                .containerBehind(shape, fill)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = GameDimens.Space12, vertical = GameDimens.Space8),
    ) { (labels, values, reserves), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val shown = labels.map { it.measure(loose) }
        val valueP = values.single().measure(loose)
        val others = reserves.map { it.measure(loose) }
        val labelH = shown.sumOf { it.height }
        val unit = labelH + valueP.height
        val roomH = maxOf(labelH, others.maxOfOrNull { it.height } ?: 0) + valueP.height
        val widest = (shown + others + valueP).maxOf { it.width }
        val width = widest.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = roomH.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            // As a centred `Column`: the label, then the value, centred together.
            var y = ((height - unit) / 2f).roundToInt()
            for (placeable in shown + valueP) {
                val x = Alignment.CenterHorizontally.align(placeable.width, width, layoutDirection)
                placeable.place(x, y)
                y += placeable.height
            }
        }
    }
}

/**
 * A chip label (DESIGN_SYSTEM.md §3.5, §3.7): [text] upper-cased with the current locale
 * ([chipLabelText]) in `labelMediumEmphasized`, at most 2 lines, coloured by [color] at draw time.
 */
@Composable
fun ScoreChipLabel(text: String, color: ColorProducer, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    BasicText(
        text = chipLabelText(text, locale),
        modifier = modifier,
        style = MaterialTheme.typography.labelMediumEmphasized.copy(textAlign = TextAlign.Center),
        maxLines = 2,
        color = color,
    )
}

/**
 * [text] upper-cased in [locale] for a chip label (DESIGN_SYSTEM.md §3.7). Greek capitals carry no
 * stress mark ("ΡΕΚΟΡ"), which the JVM's case mapping keeps ("ΡΕΚΌΡ"), so for `el` the acute U+0301
 * is removed after upper-casing (L10N_STORE_LEGAL.md L-OI-13).
 */
fun chipLabelText(text: String, locale: Locale): String {
    val upper = text.uppercase(locale)
    if (locale.language != GREEK) return upper
    val decomposed = Normalizer.normalize(upper, Normalizer.Form.NFD)
    return Normalizer.normalize(decomposed.replace(COMBINING_ACUTE, ""), Normalizer.Form.NFC)
}

private const val GREEK = "el"
private const val COMBINING_ACUTE = "\u0301"

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
    val skin = LocalTileSkin.current
    BasicText(
        text = text,
        modifier = modifier,
        style = GameFonts.score(skin).copy(fontSize = fontSize),
        softWrap = false,
        maxLines = 1,
        color = color,
    )
}

/**
 * A Score or Best value without an odometer (DESIGN_SYSTEM.md §7.2; D-084): [text] cut into the
 * `scoreLines` of [fit] (one line unless a STACKED value wraps between digits), one
 * [ScoreValueText] per line at [fontSize], in [ScoreValueLines]. Every digit is shown; nothing is
 * ellipsized or abbreviated.
 */
@Composable
fun ScoreValue(
    text: String,
    fit: ScoreFit,
    fontSize: TextUnit,
    color: ColorProducer,
    modifier: Modifier = Modifier,
) {
    val lines = scoreLines(text, fit.digitsPerLine)
    if (lines.size == 1) {
        ScoreValueText(text = text, fontSize = fontSize, color = color, modifier = modifier)
    } else {
        ScoreValueLines(modifier) {
            for (line in lines) ScoreValueText(text = line, fontSize = fontSize, color = color)
        }
    }
}

/**
 * The lines of a wrapped value (DESIGN_SYSTEM.md §7.2): each child is one line, centred, top to
 * bottom with no extra spacing; the first baseline is the last line's, so the "+N" float anchors to
 * it.
 */
@Composable
fun ScoreValueLines(modifier: Modifier = Modifier, lines: @Composable () -> Unit) {
    Layout(content = lines, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceAtLeast(constraints.minWidth)
        val height = placeables.sumOf { it.height }.coerceAtLeast(constraints.minHeight)
        val lastTop = height - (placeables.lastOrNull()?.height ?: 0)
        val lastBaseline = placeables.lastOrNull()?.get(FirstBaseline) ?: AlignmentLine.Unspecified
        val baselines: Map<AlignmentLine, Int> =
            if (lastBaseline == AlignmentLine.Unspecified) emptyMap()
            else mapOf(FirstBaseline to lastTop + lastBaseline)
        layout(width, height, baselines) {
            var y = 0
            for (p in placeables) {
                p.place((width - p.width) / 2, y)
                y += p.height
            }
        }
    }
}

/**
 * The one size of a [digits]-digit Score or Best value in [fit] (DESIGN_SYSTEM.md §7.2; D-084): the
 * largest of `ScoreValueMin..ScoreValueMax` in `ScoreValueStep` steps whose width in the score
 * numerals is at most `fit.valueWidth`, or `ScoreValueMin`, which is also the size of a value that
 * wraps (`digits > fit.digitsPerLine`). `tnum` gives every digit one advance, so the width depends
 * only on [digits]. It is measured once per digit count, fit, density and font scale.
 */
@Composable
fun rememberScoreValueSize(digits: Int, fit: ScoreFit): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val skin = LocalTileSkin.current
    return remember(digits, fit, density, measurer, skin) {
        if (digits > fit.digitsPerLine) return@remember GameDimens.ScoreValueMin
        val text = "0".repeat(digits.coerceAtLeast(1))
        val maxPx = with(density) { fit.valueWidth.dp.roundToPx() }
        val step = GameDimens.ScoreValueStep.value
        var size = GameDimens.ScoreValueMax.value
        while (size > GameDimens.ScoreValueMin.value) {
            val style = GameFonts.score(skin).copy(fontSize = size.sp)
            if (measurer.measure(text, style, softWrap = false, maxLines = 1).size.width <= maxPx) {
                break
            }
            size -= step
        }
        size.sp
    }
}

/**
 * The width in dp of one digit of the score numerals at `ScoreValueMin` (14 sp) at the current
 * density and font scale: `scoreFit`'s `advance14` (DESIGN_SYSTEM.md §7.2), so Android 14+
 * non-linear font scaling applies.
 */
@Composable
fun rememberScoreAdvance14(): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val skin = LocalTileSkin.current
    return remember(density, measurer, skin) {
        val style = GameFonts.score(skin).copy(fontSize = GameDimens.ScoreValueMin)
        val ten = measurer.measure(ADVANCE_SAMPLE, style, softWrap = false, maxLines = 1)
        ten.size.width / density.density / ADVANCE_SAMPLE.length
    }
}

/** Ten digits: their width over ten is one `tnum` advance, without one digit's side bearings. */
private const val ADVANCE_SAMPLE = "0000000000"

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
    val policy = remember(lineHeight) { BaselineOverlayPolicy(lineHeight) }
    Layout(
        contents = listOf({ Box(contentAlignment = Alignment.Center) { value() } }, overlay),
        measurePolicy = policy,
    )
}

/**
 * [BaselineOverlay]'s layout, whose intrinsic sizes are the value's with the one-line minimum and
 * leave out the overlay, so a row that sizes its chips by their intrinsic height (the INLINE and
 * OWN_ROW Score and Best, D-268) gets the height they are measured at.
 */
private class BaselineOverlayPolicy(private val lineHeight: Int) : MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (valueMeasurables, overlayMeasurables) = measurables
        val minHeight = lineHeight.coerceAtMost(constraints.maxHeight)
        val valueBox =
            valueMeasurables.first().measure(constraints.copy(minWidth = 0, minHeight = minHeight))
        val overlays = overlayMeasurables.map { it.measure(Constraints()) }
        val width = valueBox.width.coerceAtLeast(constraints.minWidth)
        val height = valueBox.height.coerceAtLeast(constraints.minHeight)
        return layout(width, height) {
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

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = maxOf(lineHeight, measurables.first().first().minIntrinsicHeight(width))

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = maxOf(lineHeight, measurables.first().first().maxIntrinsicHeight(width))

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = measurables.first().first().minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = measurables.first().first().maxIntrinsicWidth(height)
}

/** The height in px of one line of the score numerals at `ScoreValueMax`. */
@Composable
private fun rememberScoreLineHeight(): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val skin = LocalTileSkin.current
    return remember(density, measurer, skin) {
        val style = GameFonts.score(skin).copy(fontSize = GameDimens.ScoreValueMax)
        measurer.measure("0", style, softWrap = false, maxLines = 1).size.height
    }
}
