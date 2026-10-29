package com.clayboicardi.game2048.game.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.ui.theme.ClassicLightSkin
import com.clayboicardi.game2048.ui.theme.TileSkin
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** `boardGeometry` and the content margins (DESIGN_SYSTEM.md §4.2, §4.3, §5.2; D-075, D-188). */
class BoardGeometryTest {
    /** F02-AC8 */
    @Test
    fun classicGeometryAllSizes() {
        // DESIGN_SYSTEM.md §4.3's Classic table (gap / tile / radius, dp at density 1) for N = 3…8
        // on 280, 328, 380 and 720 dp, the D-075 check (4×4 Classic at 328 dp: gap 10, tile 69.5)
        // and the 4×4 Neon row with DESIGN_SYSTEM.md §4.2's Neon values.
        for ((n, row) in CLASSIC) {
            for ((i, board) in BOARDS.withIndex()) {
                assertCell("Classic $n×$n at $board dp", n, board, ClassicLightSkin, row[i])
            }
        }
        for ((i, board) in BOARDS.withIndex()) {
            assertCell("Neon 4×4 at $board dp", 4, board, NEON_SKIN, NEON_4[i])
        }

        val d075 = boardGeometry(4, 328.dp, ClassicLightSkin, 1f)
        assertWithMessage("D-075 gap").that(d075.gapPx).isEqualTo(10)
        assertWithMessage("D-075 tile").that(d075.tilePx).isWithin(EXACT).of(69.5f)
        assertWithMessage("board radius = tile radius + gap")
            .that(d075.boardRadiusPx)
            .isWithin(EXACT)
            .of(d075.tileRadiusPx + d075.gapPx)
        assertWithMessage("column 1, row 2: 10 + 79.5 rounds to 90; 10 + 159 is 169")
            .that(d075.cellTopLeft(1f, 2f))
            .isEqualTo(IntOffset(90, 169))
    }

    /** F15-AC14 */
    @Test
    fun sideMarginRespectsGestureInsets() {
        // A 411 dp window with gesture insets of 0, 24 and 40 dp: side margins 16, 24 and 40 dp.
        // The margins step at the window-class breakpoints (DESIGN_SYSTEM.md §5.2).
        for ((inset, expected) in listOf(0 to 16, 24 to 24, 40 to 40)) {
            assertWithMessage("411 dp, inset $inset dp")
                .that(horizontalMargin(411.dp, inset.dp))
                .isEqualTo(expected.dp)
        }
        val widths = listOf(599 to 16, 600 to 24, 839 to 24, 840 to 32, 1280 to 32)
        for ((width, expected) in widths) {
            assertWithMessage("width $width dp")
                .that(horizontalMargin(width.dp, 0.dp))
                .isEqualTo(expected.dp)
        }
        assertWithMessage("an inset below the margin keeps the margin")
            .that(horizontalMargin(900.dp, 20.dp))
            .isEqualTo(32.dp)
        val heights = listOf(479 to 16, 480 to 24, 899 to 24, 900 to 32, 1400 to 32)
        for ((height, expected) in heights) {
            assertWithMessage("height $height dp")
                .that(verticalMargin(height.dp))
                .isEqualTo(expected.dp)
        }
    }

    private fun assertCell(what: String, n: Int, board: Int, skin: TileSkin, cell: Cell) {
        val g = boardGeometry(n, board.dp, skin, 1f)
        assertWithMessage("$what: board px").that(g.boardPx).isEqualTo(board)
        assertWithMessage("$what: gap").that(g.gapPx).isEqualTo(cell.gap)
        assertWithMessage("$what: tile").that(g.tilePx).isWithin(TABLE).of(cell.tile)
        assertWithMessage("$what: radius").that(g.tileRadiusPx).isWithin(TABLE).of(cell.radius)
    }

    private data class Cell(val gap: Int, val tile: Float, val radius: Float)

    private companion object {
        /** The table rounds to 0.1 dp. */
        const val TABLE = 0.051f
        const val EXACT = 1e-4f

        val BOARDS = listOf(280, 328, 380, 720)

        /** DESIGN_SYSTEM.md §4.3, Classic Refined (r = 0.14). */
        val CLASSIC =
            mapOf(
                3 to
                    listOf(
                        Cell(11, 78.7f, 5.5f),
                        Cell(13, 92.0f, 6.4f),
                        Cell(15, 106.7f, 7.5f),
                        Cell(20, 213.3f, 10.0f),
                    ),
                4 to
                    listOf(
                        Cell(8, 60.0f, 4.2f),
                        Cell(10, 69.5f, 4.9f),
                        Cell(11, 81.2f, 5.7f),
                        Cell(20, 155.0f, 10.0f),
                    ),
                5 to
                    listOf(
                        Cell(7, 47.6f, 3.3f),
                        Cell(8, 56.0f, 3.9f),
                        Cell(9, 65.2f, 4.6f),
                        Cell(17, 123.6f, 8.7f),
                    ),
                6 to
                    listOf(
                        Cell(6, 39.7f, 2.8f),
                        Cell(7, 46.5f, 3.3f),
                        Cell(8, 54.0f, 3.8f),
                        Cell(14, 103.7f, 7.3f),
                    ),
                7 to
                    listOf(
                        Cell(5, 34.3f, 2.4f),
                        Cell(6, 40.0f, 2.8f),
                        Cell(7, 46.3f, 3.2f),
                        Cell(12, 89.1f, 6.2f),
                    ),
                8 to
                    listOf(
                        Cell(4, 30.5f, 2.1f),
                        Cell(5, 35.4f, 2.5f),
                        Cell(6, 40.8f, 2.9f),
                        Cell(11, 77.6f, 5.4f),
                    ),
            )

        /** DESIGN_SYSTEM.md §4.3, Neon Arcade (r = 0.18), the 4×4 row. */
        val NEON_4 =
            listOf(
                Cell(10, 57.5f, 12.7f),
                Cell(12, 67.0f, 14.7f),
                Cell(14, 77.5f, 17.1f),
                Cell(20, 155.0f, 34.0f),
            )

        /** DESIGN_SYSTEM.md §4.2 Neon geometry; the Neon palettes and skins arrive in M4. */
        val NEON_SKIN =
            TileSkin(
                id = "neon-test",
                gapRatio = 0.18f,
                radiusFraction = 0.22f,
                radiusMin = 4.dp,
                radiusMax = 34.dp,
                numeralWeight = 900,
                numeralRond = 100f,
                titleColor = Color.Black,
                boardInsetWell = true,
                tileOutline = null,
            )
    }
}
