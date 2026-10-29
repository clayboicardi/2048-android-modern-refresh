package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.board.TileLabels
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import com.clayboicardi.game2048.ui.theme.TilePalette
import com.clayboicardi.game2048.ui.theme.TileSkin
import com.clayboicardi.game2048.ui.theme.tileRadius
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One tile of exponent [exp] at side [size] outside the board (DESIGN_SYSTEM.md §7.8): the active
 * palette's fill and ink, the §4.4 radius rule (`tileRadius(size, skin)`), the §3.3 label rule with
 * t = [size] (`TileLabels`, game numerals in Latin digits, independent of the font scale) and the
 * high-contrast outline; no glow and no motion. It is spoken as `a11y_best_tile` with the full
 * decimal value: its callers (the game-over card, the statistics hero) show the best tile.
 */
@Composable
fun MiniTile(exp: Int, size: Dp, modifier: Modifier = Modifier) {
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val measurer = rememberTextMeasurer()
    val description = stringResource(R.string.a11y_best_tile, TileLabels.fullDecimal(exp))
    val label = TileLabels.label(exp, size.value)
    Spacer(
        modifier
            .size(size)
            .semantics { contentDescription = description }
            .drawWithCache {
                val radius = CornerRadius(tileRadius(size, skin).toPx())
                val style = GameNumeralStyle.copy(fontSize = label.fontSizeDp.dp.toSp())
                val text = measurer.measure(label.text, style, softWrap = false, maxLines = 1)
                val textTopLeft =
                    Offset(
                        (this.size.width - text.size.width) / 2,
                        (this.size.height - text.size.height) / 2,
                    )
                val outlineWidth = GameDimens.HcOutlineWidth.toPx()
                onDrawBehind {
                    drawRoundRect(palette.fill(exp), cornerRadius = radius)
                    skin.tileOutline?.let { outline ->
                        drawRoundRect(outline, cornerRadius = radius, style = Stroke(outlineWidth))
                    }
                    drawText(text, color = palette.ink(exp), topLeft = textTopLeft)
                }
            },
    )
}

/**
 * The cell geometry of a [MiniBoardGlyph] of side [sizePx] (DESIGN_SYSTEM.md §7.8): its own rule,
 * not §4.2's: `gapPx = max(1, round(MiniGlyphGapRatio × sizePx / n))` with no dp floor, and cells
 * of `(sizePx − (n + 1) · gapPx) / n`.
 */
fun miniGlyphGeometry(n: Int, sizePx: Float): MiniGlyphGeometry {
    val gap = max(1, (GameDimens.MiniGlyphGapRatio * sizePx / n).roundToInt())
    return MiniGlyphGeometry(n = n, gapPx = gap, cellPx = (sizePx - (n + 1) * gap) / n)
}

/** One glyph's gap and cell size in px; [cellLeft] is a cell's left (or top) edge, snapped. */
data class MiniGlyphGeometry(val n: Int, val gapPx: Int, val cellPx: Float) {
    /** The left (or top) pixel edge of cell [i], rounded so every cell covers whole pixels. */
    fun cellStart(i: Int): Int = (gapPx + i * (cellPx + gapPx)).roundToInt()

    /** The right (or bottom) pixel edge of cell [i], exclusive. */
    fun cellEnd(i: Int): Int = (gapPx + i * (cellPx + gapPx) + cellPx).roundToInt()
}

/**
 * A decorative [n]×[n] grid at side [size] (DESIGN_SYSTEM.md §7.8): [miniGlyphGeometry] cells in
 * `palette.fill(1)` on the board colour (not `palette.empty`), so they stay legible at 32 dp. Each
 * cell covers whole pixels. It has no semantics (`contentDescription = null`).
 */
@Composable
fun MiniBoardGlyph(n: Int, size: Dp, modifier: Modifier = Modifier) {
    val palette = LocalTilePalette.current
    Spacer(
        modifier
            .size(size)
            .clearAndSetSemantics {}
            .drawBehind {
                val side = this.size.minDimension
                val glyph = miniGlyphGeometry(n, side)
                drawRect(palette.board, size = Size(side, side))
                val cell = palette.fill(1)
                for (row in 0 until n) {
                    for (col in 0 until n) {
                        val left = glyph.cellStart(col).toFloat()
                        val top = glyph.cellStart(row).toFloat()
                        drawRect(
                            color = cell,
                            topLeft = Offset(left, top),
                            size = Size(glyph.cellEnd(col) - left, glyph.cellEnd(row) - top),
                        )
                    }
                }
            },
    )
}

/**
 * A preview of [palette] and [skin] (DESIGN_SYSTEM.md §7.8): four [MiniTile]s (2, 8, 128, 2048) at
 * `SwatchTile`, `Space4` apart, on the palette's board colour with `Space4` padding. Decorative:
 * the row it sits in names the palette.
 */
@Composable
fun PaletteSwatch(palette: TilePalette, skin: TileSkin, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalTilePalette provides palette, LocalTileSkin provides skin) {
        Row(
            modifier =
                modifier
                    .clearAndSetSemantics {}
                    .drawBehind { drawRect(palette.board) }
                    .padding(GameDimens.Space4),
            horizontalArrangement = Arrangement.spacedBy(GameDimens.Space4),
        ) {
            for (exp in SWATCH_EXPS) MiniTile(exp, GameDimens.SwatchTile)
        }
    }
}

/** 2, 8, 128 and 2048. */
private val SWATCH_EXPS = intArrayOf(1, 3, 7, 11)
