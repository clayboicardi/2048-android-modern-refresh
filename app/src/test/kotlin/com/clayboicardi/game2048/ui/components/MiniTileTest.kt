package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.game.board.TileLabels
import com.clayboicardi.game2048.ui.theme.ClassicDarkPalette
import com.clayboicardi.game2048.ui.theme.ClassicDarkSkin
import com.clayboicardi.game2048.ui.theme.ClassicLightPalette
import com.clayboicardi.game2048.ui.theme.ClassicLightSkin
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.clayboicardi.game2048.ui.theme.Glow
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import com.clayboicardi.game2048.ui.theme.TilePalette
import com.clayboicardi.game2048.ui.theme.tileRadius
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.ceil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `MiniTile` rendering and its `a11y_best_tile` description, `MiniBoardGlyph` geometry and
 * rendering, and `PaletteSwatch` (DESIGN_SYSTEM.md §7.8; D-234).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MiniTileTest {
    @get:Rule val compose = createComposeRule()

    /**
     * D-070, DESIGN_SYSTEM.md §7.8: an 8×8 glyph at 32 dp on a 420 dpi screen (84 px) has every
     * cell at least 9 px wide and tall, in its geometry and in the drawn pixels.
     */
    @Test
    fun glyphCellsLegibleAt32dp() {
        compose.setContent {
            CompositionLocalProvider(LocalTilePalette provides TwoColours) {
                MiniBoardGlyph(n = N, size = GameDimens.MiniTileSmall, Modifier.testTag(GLYPH))
            }
        }
        val sizePx = with(compose.density) { GameDimens.MiniTileSmall.toPx() }
        assertThat(sizePx).isEqualTo(EXPECTED_SIZE_PX)

        val glyph = miniGlyphGeometry(N, sizePx)
        assertThat(glyph.gapPx).isEqualTo(1)
        for (i in 0 until N) {
            assertWithMessage("cell $i")
                .that(glyph.cellEnd(i) - glyph.cellStart(i))
                .isAtLeast(MIN_PX)
        }

        val pixels = compose.onNodeWithTag(GLYPH).captureToImage().toPixelMap()
        for (row in 0 until N) {
            val y = (glyph.cellStart(row) + glyph.cellEnd(row)) / 2
            val runs = runs(List(pixels.width) { x -> pixels[x, y] == CELL })
            assertWithMessage("row $row: cell runs").that(runs).hasSize(N)
            for (run in runs) assertWithMessage("row $row: run").that(run).isAtLeast(MIN_PX)
        }
        for (col in 0 until N) {
            val x = (glyph.cellStart(col) + glyph.cellEnd(col)) / 2
            val runs = runs(List(pixels.height) { y -> pixels[x, y] == CELL })
            assertWithMessage("column $col: cell runs").that(runs).hasSize(N)
            for (run in runs) assertWithMessage("column $col: run").that(run).isAtLeast(MIN_PX)
        }
    }

    /**
     * DESIGN_SYSTEM.md §3.3, §4.4, §7.8: a 32 dp `MiniTile` is spoken as `a11y_best_tile` with the
     * full decimal value, fills a rounded square with the active palette's fill and no outline, and
     * draws the §3.3 label for t = 32 ("2K", "16K") in the palette's ink, centred, at the same
     * pixel size under font scale 1 and 2. The label's ink is compared with a `BasicText` of the
     * same text and size centred in a plain 32 dp box at font scale 1.
     */
    @Test
    fun miniTileDrawsLabelAndBestTileDescription() {
        val palette = ClassicLightPalette
        compose.setContent { GameTheme(darkTheme = false) { LabelTiles(palette) } }

        val radius = tileRadius(GameDimens.MiniTileSmall, ClassicLightSkin)
        val inset = ceil(with(compose.density) { radius.toPx() }).toInt()
        for (tile in BEST_TILES) {
            assertWithMessage("2^${tile.exp}: the §3.3 label")
                .that(TileLabels.label(tile.exp, GameDimens.MiniTileSmall.value).text)
                .isEqualTo(tile.label)
            val fill = palette.fill(tile.exp)
            val ink = palette.ink(tile.exp)
            val reference =
                compose.onNodeWithTag(referenceTag(tile.exp)).captureToImage().toPixelMap()
            val expected =
                checkNotNull(inkBounds(reference, fill, ink, inset)) { "no reference ink" }
            for (fontScale in FONT_SCALES) {
                val at = "2^${tile.exp} at font scale $fontScale"
                val node = compose.onNodeWithTag(tileTag(tile.exp, fontScale))
                node.assertContentDescriptionEquals(tile.spoken)
                val pixels = node.captureToImage().toPixelMap()
                assertThat(IntSize(pixels.width, pixels.height))
                    .isEqualTo(IntSize(EXPECTED_SIZE_PX.toInt(), EXPECTED_SIZE_PX.toInt()))
                assertWithMessage("$at: fill at the left edge, no outline")
                    .that(pixels[0, pixels.height / 2])
                    .isEqualTo(fill)
                assertWithMessage("$at: the corner is rounded")
                    .that(pixels[0, 0])
                    .isNotEqualTo(fill)
                val drawn =
                    checkNotNull(inkBounds(pixels, fill, ink, inset)) { "$at: no label ink" }
                for ((edge, got, wanted) in edges(drawn, expected)) {
                    assertWithMessage("$at: ink $edge (drawn $drawn, reference $expected)")
                        .that(abs(got - wanted))
                        .isAtMost(INK_TOLERANCE_PX)
                }
            }
        }
    }

    /**
     * The label test's content: [BEST_TILES] as `MiniTile`s at each of [FONT_SCALES], one row per
     * scale, then a row of their references at font scale 1.
     */
    @Composable
    private fun LabelTiles(palette: TilePalette) {
        val density = LocalDensity.current.density
        Column {
            for (fontScale in FONT_SCALES) {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    Row {
                        for (tile in BEST_TILES) {
                            MiniTile(
                                tile.exp,
                                GameDimens.MiniTileSmall,
                                Modifier.testTag(tileTag(tile.exp, fontScale)),
                            )
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                Row { for (tile in BEST_TILES) ReferenceLabel(tile.exp, palette) }
            }
        }
    }

    /**
     * The reference for [exp]'s label: a plain 32 dp box in [palette]'s fill with a centred
     * `BasicText` of the §3.3 label at its nominal size in the game numerals and the palette's ink.
     */
    @Composable
    private fun ReferenceLabel(exp: Int, palette: TilePalette) {
        val label = TileLabels.label(exp, GameDimens.MiniTileSmall.value)
        Box(
            Modifier.size(GameDimens.MiniTileSmall)
                .background(palette.fill(exp))
                .testTag(referenceTag(exp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = label.text,
                style =
                    GameFonts.tile(LocalTileSkin.current, label.text.length)
                        .copy(fontSize = label.fontSizeDp.sp, color = palette.ink(exp)),
                softWrap = false,
                maxLines = 1,
            )
        }
    }

    /**
     * DESIGN_SYSTEM.md §2.7, §7.8: under a skin with a high-contrast outline, `MiniTile` strokes
     * its edge in that colour, `HcOutlineWidth` wide, inside the fill.
     */
    @Test
    fun miniTileDrawsHighContrastOutline() {
        compose.setContent {
            GameTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalTileSkin provides ClassicLightSkin.copy(tileOutline = OUTLINE),
                ) {
                    MiniTile(exp = 1, size = GameDimens.MiniTileSmall, Modifier.testTag(TILE))
                }
            }
        }

        val pixels = compose.onNodeWithTag(TILE).captureToImage().toPixelMap()
        val mid = pixels.height / 2
        val inside = with(compose.density) { GameDimens.HcOutlineWidth.toPx() }.toInt()
        assertWithMessage("left edge").that(pixels[0, mid]).isEqualTo(OUTLINE)
        assertWithMessage("right edge").that(pixels[pixels.width - 1, mid]).isEqualTo(OUTLINE)
        assertWithMessage("top edge").that(pixels[pixels.width / 2, 0]).isEqualTo(OUTLINE)
        assertWithMessage("inside the outline")
            .that(pixels[inside, mid])
            .isEqualTo(ClassicLightPalette.fill(1))
    }

    /**
     * DESIGN_SYSTEM.md §7.8, D-234: `PaletteSwatch` draws its own palette (Classic dark under a
     * light theme): four `MiniTile`s 2, 8, 128 and 2048 of side `SwatchTile`, `Space4` apart, on
     * the palette's board colour with `Space4` padding, and exposes no semantics.
     */
    @Test
    fun paletteSwatchShowsFourTilesWithoutSemantics() {
        compose.setContent {
            GameTheme(darkTheme = false) {
                PaletteSwatch(ClassicDarkPalette, ClassicDarkSkin, Modifier.testTag(SWATCH))
            }
        }

        val (tile, space) =
            with(compose.density) {
                GameDimens.SwatchTile.roundToPx() to GameDimens.Space4.roundToPx()
            }
        val node = compose.onNodeWithTag(SWATCH)
        assertThat(node.fetchSemanticsNode().size)
            .isEqualTo(IntSize(SWATCH_EXPS.size * (tile + space) + space, tile + 2 * space))
        node.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        // The merged tree, which accessibility services read: the tiles' descriptions are cleared.
        node.onChildren().assertCountEquals(0)
        compose.onAllNodesWithContentDescription(BEST_TILE, substring = true).assertCountEquals(0)

        val palette = ClassicDarkPalette
        val pixels = node.captureToImage().toPixelMap()
        // A row through the tiles above their labels, below their rounded corners.
        val y = space + tile / SWATCH_ROW_DIVISOR
        assertWithMessage("top padding")
            .that(pixels[pixels.width / 2, space / 2])
            .isEqualTo(palette.board)
        assertWithMessage("left padding").that(pixels[space / 2, y]).isEqualTo(palette.board)
        SWATCH_EXPS.forEachIndexed { i, exp ->
            val left = space + i * (tile + space)
            assertWithMessage("tile $i (2^$exp)")
                .that(pixels[left + tile / 2, y])
                .isEqualTo(palette.fill(exp))
            assertWithMessage("gap after tile $i")
                .that(pixels[left + tile + space / 2, y])
                .isEqualTo(palette.board)
        }
    }

    /** The lengths of the runs of `true` in [line]. */
    private fun runs(line: List<Boolean>): List<Int> {
        val lengths = mutableListOf<Int>()
        var current = 0
        for (inside in line) {
            if (inside) {
                current++
            } else if (current > 0) {
                lengths += current
                current = 0
            }
        }
        if (current > 0) lengths += current
        return lengths
    }

    /**
     * The tight bounds in px of the pixels of [pixels] closer to [ink] than to [fill], or `null`
     * when there are none. Only the square inset by [inset] px is read, which leaves out the
     * rounded corners, where the background shows.
     */
    private fun inkBounds(pixels: PixelMap, fill: Color, ink: Color, inset: Int): Rect? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in inset until pixels.height - inset) {
            for (x in inset until pixels.width - inset) {
                val pixel = pixels[x, y]
                if (distance(pixel, ink) < distance(pixel, fill)) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x + 1)
                    bottom = maxOf(bottom, y + 1)
                }
            }
        }
        return if (left > right) {
            null
        } else {
            Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        }
    }

    /** The four edges of [drawn] and [wanted], named. */
    private fun edges(drawn: Rect, wanted: Rect): List<Triple<String, Float, Float>> =
        listOf(
            Triple("left", drawn.left, wanted.left),
            Triple("top", drawn.top, wanted.top),
            Triple("right", drawn.right, wanted.right),
            Triple("bottom", drawn.bottom, wanted.bottom),
        )

    /** The squared RGB distance of [a] and [b]. */
    private fun distance(a: Color, b: Color): Float {
        val r = a.red - b.red
        val g = a.green - b.green
        val bl = a.blue - b.blue
        return r * r + g * g + bl * bl
    }

    private fun tileTag(exp: Int, fontScale: Float) = "test:tile-$exp-$fontScale"

    private fun referenceTag(exp: Int) = "test:reference-$exp"

    /** A tile of the label test: its exponent, its §3.3 label at t = 32 and its spoken text. */
    private data class BestTile(val exp: Int, val label: String, val spoken: String)

    /** A palette whose board is black and whose 2-tile fill is white, so cells are easy to see. */
    private object TwoColours : TilePalette {
        override fun fill(exp: Int): Color = CELL

        override fun ink(exp: Int): Color = Color.Black

        override fun glow(exp: Int): Glow? = null

        override val board: Color = Color.Black
        override val empty: Color = Color.Black
        override val background: Color = Color.Black
    }

    private companion object {
        const val N = 8
        const val GLYPH = "test:glyph"
        const val TILE = "test:tile"
        const val SWATCH = "test:swatch"
        const val MIN_PX = 9

        /** 2048 and 16384: both compact at 32 dp (§3.3 step 3). */
        val BEST_TILES =
            listOf(
                BestTile(exp = 11, label = "2K", spoken = "Best tile, 2048"),
                BestTile(exp = 14, label = "16K", spoken = "Best tile, 16384"),
            )
        val FONT_SCALES = listOf(1f, 2f)
        const val BEST_TILE = "Best tile"

        /** The swatch's tiles, 2, 8, 128 and 2048 (DESIGN_SYSTEM.md §7.8). */
        val SWATCH_EXPS = listOf(1, 3, 7, 11)

        /** The swatch row sampled at a quarter of the tile, between the corner and the label. */
        const val SWATCH_ROW_DIVISOR = 4

        /**
         * The reference box centres its text on a whole pixel and `MiniTile` on a half pixel, so
         * their ink edges may differ by one pixel, never two.
         */
        const val INK_TOLERANCE_PX = 1.5f
        val OUTLINE = Color(0xFF102030)

        /** 32 dp × 2.625 (420 dpi). */
        const val EXPECTED_SIZE_PX = 84f
        val CELL = Color.White
    }
}
