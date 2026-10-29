package com.clayboicardi.game2048.game.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.TileSkin

/**
 * A measured tile label (DESIGN_SYSTEM.md §3.3): the layout and the font size it was measured at
 * after the auto-fit guard, in px, for the optical centering of step 7.
 */
@Stable internal class TileText(val layout: TextLayoutResult, val fontSizePx: Float)

/**
 * The board's tile labels, measured once per (label, font size px, skin id) (D-025;
 * DESIGN_SYSTEM.md §3.3 steps 4–7): an LRU of 64 measured labels from one `TextMeasurer`, so a
 * slide, a pop or a fade never measures text. Measurement ignores `fontScale` (D-084): tile
 * numerals follow density only. The ink colour is applied at draw time, so high contrast never
 * invalidates an entry.
 *
 * Numerals are `GameFonts.tile(skin, chars)` (DESIGN_SYSTEM.md §3.2: the skin's weight and ROND,
 * the label's width, `"lnum"`), laid out left to right whatever the measurer's layout direction
 * (D-104). Main thread only.
 */
@Stable
internal class TileTextCache(private val measurer: TextMeasurer) {
    private val entries =
        object : LinkedHashMap<Key, TileText>(CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TileText>?) =
                size > CAPACITY
        }

    /**
     * The label of [exp] on a tile of [tilePx] px at [density] px per dp, in [skin]'s numerals; the
     * cache key holds `skin.id` (D-025).
     */
    fun text(exp: Int, tilePx: Float, density: Float, skin: TileSkin): TileText {
        val label = TileLabels.label(exp, tilePx / density)
        val key = Key(label.text, label.fontSizeDp * density, skin.id)
        return entries.getOrPut(key) { measure(label, tilePx, density, skin) }
    }

    private fun measure(label: TileLabel, tilePx: Float, density: Float, skin: TileSkin): TileText {
        val noFontScale = Density(density, fontScale = 1f)
        val numerals = GameFonts.tile(skin, label.text.length)
        var sizeDp = label.fontSizeDp
        var layout = measureAt(label.text, numerals, sizeDp, noFontScale)
        val maxWidth = FIT_WIDTH * tilePx
        if (layout.size.width > maxWidth) {
            sizeDp *= maxWidth / layout.size.width
            layout = measureAt(label.text, numerals, sizeDp, noFontScale)
        }
        return TileText(layout, sizeDp * density)
    }

    private fun measureAt(
        text: String,
        numerals: TextStyle,
        sizeDp: Float,
        density: Density,
    ): TextLayoutResult =
        measurer.measure(
            text = text,
            style = numerals.merge(LabelStyle).copy(fontSize = sizeDp.sp),
            softWrap = false,
            maxLines = 1,
            density = density,
        )

    private data class Key(val text: String, val fontSizePx: Float, val skinId: String)

    private companion object {
        /** D-025: 64 entries. */
        const val CAPACITY = 64
        const val LOAD_FACTOR = 0.75f

        /** Step 4: a label wider than 0.84 × the tile shrinks to fit once. */
        const val FIT_WIDTH = 0.84f

        /** Step 5. */
        const val LETTER_SPACING_EM = -0.02f

        /**
         * Step 5 on top of the numerals: tracking, a 1 em line box trimmed both ends, no padding.
         */
        val LabelStyle =
            TextStyle(
                letterSpacing = LETTER_SPACING_EM.em,
                lineHeight = 1f.em,
                lineHeightStyle =
                    LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
    }
}

/** The board's one [TileTextCache] over one remembered `TextMeasurer` (no cache of its own). */
@Composable
internal fun rememberTileTextCache(): TileTextCache {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    return remember(measurer) { TileTextCache(measurer) }
}

/**
 * Step 7, optical centering: the label's top-left on a tile of [sidePx] px, putting half the cap
 * height (0.358 em) on the tile centre.
 */
internal fun TileText.topLeftX(sidePx: Float): Float = (sidePx - layout.size.width) / 2f

/** The y of step 7 (see [topLeftX]). */
internal fun TileText.topLeftY(sidePx: Float): Float =
    sidePx / 2f - layout.firstBaseline + HALF_CAP_HEIGHT_EM * fontSizePx

/** Half the numeral cap height, in em (DESIGN_SYSTEM.md §3.1, §3.3 step 7). */
private const val HALF_CAP_HEIGHT_EM = 0.358f
