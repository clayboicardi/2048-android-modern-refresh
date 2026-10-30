package com.clayboicardi.game2048.game.board

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.ui.components.LayoutMode
import com.clayboicardi.game2048.ui.theme.ClassicLightSkin
import com.clayboicardi.game2048.ui.theme.NeonLightSkin
import com.clayboicardi.game2048.ui.theme.TileSkin
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * `boardGeometry`, the content margins and `resolveGameLayout` (DESIGN_SYSTEM.md §4.2, §4.3, §5.2,
 * §5.3; D-075, D-091, D-188), with the reference insets and the nominal chrome of §5.3.
 */
@Suppress("LargeClass") // one method per FEATURES.md F18 criterion of the resolution (§8)
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
            assertCell("Neon 4×4 at $board dp", 4, board, NeonLightSkin, NEON_4[i])
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

    /** F18-AC2 */
    @Test
    fun boardSizePerMode() {
        // DESIGN_SYSTEM.md §5.3's first nine worked examples: the D-091 windows plus 411×914,
        // 412×915 and 360×640, with the reference insets and a nominal ChromeMeasure.
        val rows =
            listOf(
                Expect(360, 800, LayoutMode.STACKED, 328f),
                Expect(411, 914, LayoutMode.STACKED, 379f),
                Expect(412, 915, LayoutMode.STACKED, 380f),
                Expect(915, 412, LayoutMode.THREE_COLUMN, 356f, rail = 201.3f),
                Expect(673, 841, LayoutMode.STACKED, 481f),
                Expect(841, 673, LayoutMode.SIDE_PANEL, 465f, panel = 280f),
                Expect(800, 1280, LayoutMode.STACKED, 720f),
                Expect(1280, 800, LayoutMode.SIDE_PANEL, 720f, panel = 400f),
                Expect(360, 640, LayoutMode.STACKED, 280f),
            )
        for (row in rows) {
            val spec = resolveGameLayout(reference(row.width, row.height), NominalChrome())
            row.assertMatches(spec)
            assertWithMessage("${row.name}: at most 720 dp").that(spec.board).isAtMost(BOARD_MAX)
            if (spec.mode == LayoutMode.STACKED) {
                assertWithMessage("${row.name}: FULL header")
                    .that(spec.header)
                    .isEqualTo(HeaderStyle.FULL)
            }
        }
    }

    /** F18-AC3 */
    @Test
    fun narrowWindowFloor() {
        // Zero insets, 800 dp tall: an available width of 250 dp, then 200 dp (16 dp margins).
        for ((availW, board) in listOf(250 to 250f, 200 to 220f)) {
            val window = window(availW + 2 * COMPACT_MARGIN, 800)
            assertWithMessage("availW").that(window.availW).isWithin(EXACT).of(availW.toFloat())
            val spec = resolveGameLayout(window, NominalChrome())
            assertWithMessage("availW $availW: mode").that(spec.mode).isEqualTo(LayoutMode.STACKED)
            assertWithMessage("availW $availW: COMPACT header (no title)")
                .that(spec.header)
                .isEqualTo(HeaderStyle.COMPACT)
            assertWithMessage("availW $availW: board").that(spec.board).isWithin(EXACT).of(board)
        }
    }

    /** F18-AC8 */
    @Test
    fun resolvedLayoutTable() {
        // Split-screen reference insets (24 dp top, 0 bottom); 900×1400 with the portrait ones.
        val rows =
            listOf(
                Expect(360, 450, LayoutMode.STACKED, 234f, header = HeaderStyle.COMPACT),
                Expect(360, 450, LayoutMode.SCROLL, 274f, HeaderStyle.COMPACT, talkBack = true),
                Expect(411, 450, LayoutMode.STACKED, 234f, header = HeaderStyle.COMPACT),
                Expect(411, 450, LayoutMode.SCROLL, 274f, HeaderStyle.COMPACT, talkBack = true),
                Expect(620, 450, LayoutMode.STACKED, 234f, header = HeaderStyle.COMPACT),
                Expect(700, 450, LayoutMode.THREE_COLUMN, 308f, rail = 160f),
                Expect(900, 1400, LayoutMode.STACKED, 720f, header = HeaderStyle.FULL),
                // FEATURES.md F18's wide and short edge case: THREE_COLUMN with a 344 dp board.
                Expect(1000, 400, LayoutMode.THREE_COLUMN, 344f, rail = 220f),
            )
        for (row in rows) {
            val window = reference(row.width, row.height, row.talkBack)
            val spec = resolveGameLayout(window, NominalChrome(talkBack = row.talkBack))
            row.assertMatches(spec)
        }
        // The rejected candidates of DESIGN_SYSTEM.md §5.3's table: 620×450's THREE_COLUMN board
        // (228 dp) is smaller than STACKED's; 900×1400's SIDE_PANEL board (516 dp) too.
        assertWithMessage("620×450 THREE_COLUMN candidate")
            .that(threeColumnBoard(reference(620, 450), talkBack = false))
            .isWithin(TABLE)
            .of(228f)
        assertWithMessage("900×1400 SIDE_PANEL candidate")
            .that(sidePanelBoard(reference(900, 1400)))
            .isWithin(TABLE)
            .of(516f)
    }

    /** F18-AC9 */
    @Test
    fun boardNeverBelowFloorOrOffWindow() {
        for (scale in listOf(1f, 1.5f)) {
            forEveryWindow { window ->
                val chrome = NominalChrome(window.talkBack, window.hint, scale)
                assertGuarantees(window, chrome, "scale $scale")
            }
        }
    }

    /** F18-AC15 */
    @Test
    fun growingHeaderKeepsBoardAndChromeInWindow() {
        val header = { style: HeaderStyle, w: Float ->
            when (style) {
                HeaderStyle.FULL ->
                    when {
                        w >= GROW_FULL_1 -> NOMINAL_FULL
                        w >= GROW_FULL_2 -> FULL_OWN_ROW
                        else -> FULL_STACKED
                    }
                HeaderStyle.COMPACT ->
                    when {
                        w >= GROW_COMPACT_1 -> NOMINAL_COMPACT
                        w >= GROW_COMPACT_2 -> COMPACT_OWN_ROW
                        else -> COMPACT_STACKED
                    }
            }
        }
        forEveryWindow { window ->
            val chrome = NominalChrome(window.talkBack, window.hint, header = header)
            val spec = assertGuarantees(window, chrome, "growing header")
            if (spec.mode == LayoutMode.STACKED && spec.header == HeaderStyle.FULL) {
                val name = window.name()
                val terms = LayoutTerms(window, chrome)
                assertWithMessage("$name: a FULL board is at least 280 dp")
                    .that(spec.board)
                    .isAtLeast(BOARD_MIN)
                assertWithMessage("$name: board + stacked(FULL, board) fits availH")
                    .that(spec.board + terms.stacked(HeaderStyle.FULL, spec.board))
                    .isAtMost(window.availH + EXACT)
            }
        }
        // 412×638, the pad and the hint off: one re-measure of FULL would give 286 dp with 662 dp
        // of board and chrome in 590; the COMPACT header keeps a 380 dp board.
        val window = window(412, 638)
        assertWithMessage("availW").that(window.availW).isWithin(EXACT).of(380f)
        assertWithMessage("availH").that(window.availH).isWithin(EXACT).of(590f)
        val spec = resolveGameLayout(window, NominalChrome(header = header))
        assertWithMessage("412×638: mode").that(spec.mode).isEqualTo(LayoutMode.STACKED)
        assertWithMessage("412×638: header").that(spec.header).isEqualTo(HeaderStyle.COMPACT)
        assertWithMessage("412×638: board").that(spec.board).isWithin(EXACT).of(380f)
    }

    /**
     * F18-AC9's guarantees for [window] and [chrome] (DESIGN_SYSTEM.md §5.3): the board within
     * [220, 720] dp; a side arrangement only with a board at least STACKED's while STACKED fits;
     * outside SCROLL the board and its chrome inside `availH` and `availW` (a STACKED board wider
     * than `availW` only when `availW` < 220 dp, and then 220 dp); SCROLL only when STACKED,
     * THREE_COLUMN and the preferred SIDE_PANEL all fail. Returns the spec.
     */
    private fun assertGuarantees(
        window: LayoutWindow,
        chrome: ChromeMeasure,
        what: String,
    ): GameLayoutSpec {
        val spec = resolveGameLayout(window, chrome)
        val name = "$what, ${window.name()} → $spec"
        assertWithMessage("$name: board ≥ 220").that(spec.board).isAtLeast(BOARD_FLOOR)
        assertWithMessage("$name: board ≤ 720").that(spec.board).isAtMost(BOARD_MAX)
        val stacked = stackedCandidate(window, chrome)
        val stackedFits = stacked >= BOARD_FLOOR
        when (spec.mode) {
            LayoutMode.STACKED -> assertStackedFits(name, spec, window, chrome)
            LayoutMode.THREE_COLUMN,
            LayoutMode.SIDE_PANEL -> {
                assertSideFits(name, spec, window, chrome)
                if (stackedFits) {
                    assertWithMessage("$name: at least STACKED's board")
                        .that(spec.board)
                        .isAtLeast(stacked)
                }
            }
            LayoutMode.SCROLL -> {
                val t = threeColumnBoard(window, window.talkBack)
                val threeFits = t >= BOARD_FLOOR && chrome.rails(rail(window)) <= window.availH
                val p = sidePanelBoard(window)
                val panelFits = p >= BOARD_FLOOR && chrome.panel(panel(window)) <= p
                val preferred = preferred(window)
                assertWithMessage("$name: SCROLL only when nothing else fits")
                    .that(
                        !stackedFits &&
                            !threeFits &&
                            (preferred != LayoutMode.SIDE_PANEL || !panelFits),
                    )
                    .isTrue()
                assertWithMessage("$name: SCROLL header")
                    .that(spec.header)
                    .isEqualTo(HeaderStyle.COMPACT)
            }
        }
        return spec
    }

    /** STACKED: the board and its chrome fit `availH`, the board `availW` (or D-075's spill). */
    private fun assertStackedFits(
        name: String,
        spec: GameLayoutSpec,
        window: LayoutWindow,
        chrome: ChromeMeasure,
    ) {
        val terms = LayoutTerms(window, chrome)
        val w = if (spec.header == HeaderStyle.FULL) spec.board else terms.w0
        assertWithMessage("$name: board + chrome fit availH")
            .that(spec.board + terms.stacked(spec.header, w))
            .isAtMost(window.availH + EXACT)
        if (window.availW >= BOARD_FLOOR) {
            assertWithMessage("$name: board fits availW")
                .that(spec.board)
                .isAtMost(window.availW + EXACT)
        } else {
            assertWithMessage("$name: D-075's spill board")
                .that(spec.board)
                .isWithin(EXACT)
                .of(BOARD_FLOOR)
        }
    }

    /** THREE_COLUMN and SIDE_PANEL: the board, the rails or panel and the gaps fit the window. */
    private fun assertSideFits(
        name: String,
        spec: GameLayoutSpec,
        window: LayoutWindow,
        chrome: ChromeMeasure,
    ) {
        assertWithMessage("$name: board fits availH")
            .that(spec.board)
            .isAtMost(window.availH + EXACT)
        if (spec.mode == LayoutMode.THREE_COLUMN) {
            assertWithMessage("$name: rails fit availH")
                .that(chrome.rails(spec.rail))
                .isAtMost(window.availH + EXACT)
            assertWithMessage("$name: board, rails and gaps fit availW")
                .that(spec.board + 2 * spec.rail + 2 * RAIL_GAP)
                .isAtMost(window.availW + EXACT)
        } else {
            assertWithMessage("$name: panel fits the board")
                .that(chrome.panel(spec.panel))
                .isAtMost(spec.board + EXACT)
            assertWithMessage("$name: board, panel and gap fit availW")
                .that(spec.board + spec.panel + PANEL_GAP)
                .isAtMost(window.availW + EXACT)
        }
    }

    /** Every window from 220×220 to 1600×1600 dp in 20 dp steps, zero insets, pad and hint. */
    private fun forEveryWindow(check: (LayoutWindow) -> Unit) {
        for (width in GRID_MIN..GRID_MAX step GRID_STEP) {
            for (height in GRID_MIN..GRID_MAX step GRID_STEP) {
                for (talkBack in listOf(false, true)) {
                    for (hint in listOf(false, true)) {
                        check(window(width, height, talkBack = talkBack, hint = hint))
                    }
                }
            }
        }
    }

    /**
     * STACKED's candidate board of DESIGN_SYSTEM.md §5.3, written out from the spec's pseudo-code
     * (FULL with its one re-measure, else COMPACT).
     */
    private fun stackedCandidate(window: LayoutWindow, chrome: ChromeMeasure): Float {
        val terms = LayoutTerms(window, chrome)
        val w0 = terms.w0
        val availH = window.availH
        val full = HeaderStyle.FULL
        val compact = HeaderStyle.COMPACT
        val fullFirst = window.availW >= BOARD_MIN && availH - terms.stacked(full, w0) >= BOARD_MIN
        if (fullFirst) {
            val first = minOf(w0, availH - terms.stacked(full, w0))
            val s = first - maxOf(0f, terms.stacked(full, first) - terms.stacked(full, w0))
            if (s >= BOARD_MIN && s + terms.stacked(full, s) <= availH) return s
        }
        return minOf(w0, availH - terms.stacked(compact, w0))
    }

    private fun rail(window: LayoutWindow): Float {
        val rail = (RAIL_FRACTION * window.width).coerceIn(RAIL_MIN, RAIL_MAX)
        return if (window.talkBack) maxOf(rail, RAIL_WITH_PAD) else rail
    }

    private fun threeColumnBoard(window: LayoutWindow, talkBack: Boolean): Float {
        val rail = rail(window.copy(talkBack = talkBack))
        return minOf(window.availH, window.availW - 2 * rail - 2 * RAIL_GAP, BOARD_MAX)
    }

    private fun panel(window: LayoutWindow): Float =
        (PANEL_FRACTION * window.width).coerceIn(PANEL_MIN, PANEL_MAX)

    private fun sidePanelBoard(window: LayoutWindow): Float =
        minOf(window.availH, window.availW - panel(window) - PANEL_GAP, BOARD_MAX)

    private fun preferred(window: LayoutWindow): LayoutMode =
        when {
            window.height < HEIGHT_MEDIUM -> LayoutMode.THREE_COLUMN
            window.width >= WIDTH_EXPANDED -> LayoutMode.SIDE_PANEL
            else -> LayoutMode.STACKED
        }

    /**
     * The reference insets of DESIGN_SYSTEM.md §5.3's worked examples: portrait 48 dp top and 24 dp
     * bottom; landscape and split-screen panes (height ≤ 450 dp or wider than tall) 24 dp top, 0
     * bottom; gesture insets below the margins.
     */
    private fun reference(width: Int, height: Int, talkBack: Boolean = false): LayoutWindow {
        val portrait = height > width && height > SPLIT_PANE_HEIGHT
        return window(width, height, talkBack = talkBack)
            .copy(
                safeTop = if (portrait) PORTRAIT_TOP else LANDSCAPE_TOP,
                safeBottom = if (portrait) PORTRAIT_BOTTOM else 0f,
            )
    }

    /** A [width] × [height] dp window with zero insets. */
    private fun window(width: Int, height: Int, talkBack: Boolean = false, hint: Boolean = false) =
        LayoutWindow(
            width = width.toFloat(),
            height = height.toFloat(),
            safeLeft = 0f,
            safeTop = 0f,
            safeRight = 0f,
            safeBottom = 0f,
            gestureLeft = 0f,
            gestureRight = 0f,
            talkBack = talkBack,
            hint = hint,
        )

    private fun LayoutWindow.name(): String =
        "${width.toInt()}×${height.toInt()}" +
            (if (talkBack) " pad" else "") +
            (if (hint) " hint" else "")

    /** One row of a worked-example table. */
    private data class Expect(
        val width: Int,
        val height: Int,
        val mode: LayoutMode,
        val board: Float,
        val header: HeaderStyle? = null,
        val talkBack: Boolean = false,
        val rail: Float? = null,
        val panel: Float? = null,
    ) {
        val name: String
            get() = "${width}×$height" + if (talkBack) " with the pad" else ""

        fun assertMatches(spec: GameLayoutSpec) {
            assertWithMessage("$name: mode").that(spec.mode).isEqualTo(mode)
            assertWithMessage("$name: board").that(spec.board).isWithin(TABLE).of(board)
            header?.let { assertWithMessage("$name: header").that(spec.header).isEqualTo(it) }
            rail?.let { assertWithMessage("$name: rail").that(spec.rail).isWithin(TABLE).of(it) }
            panel?.let {
                assertWithMessage("$name: panel").that(spec.panel).isWithin(TABLE).of(it)
            }
        }
    }

    /**
     * DESIGN_SYSTEM.md §5.3's nominal chrome at fontScale 1.0, times [scale]: header FULL 112 and
     * COMPACT 64 ([header] replaces them), the hint card 72, the controls bar 80, the status line
     * 40, the pad row 136, the rails 192 (312 with the pad) and the panel 256 (448 with the pad,
     * plus the hint and its gap while it is shown).
     */
    private class NominalChrome(
        private val talkBack: Boolean = false,
        private val hint: Boolean = false,
        private val scale: Float = 1f,
        private val header: ((HeaderStyle, Float) -> Float)? = null,
    ) : ChromeMeasure {
        override fun header(style: HeaderStyle, width: Float): Float =
            header?.invoke(style, width)
                ?: scale * if (style == HeaderStyle.FULL) NOMINAL_FULL else NOMINAL_COMPACT

        override fun hint(width: Float): Float = scale * NOMINAL_HINT

        override fun controls(width: Float): Float = scale * NOMINAL_CONTROLS

        override fun status(width: Float): Float = scale * NOMINAL_STATUS

        override fun padRow(width: Float): Float = scale * NOMINAL_PAD_ROW

        override fun rails(rail: Float): Float =
            scale * if (talkBack) NOMINAL_RAILS_PAD else NOMINAL_RAILS

        override fun panel(panel: Float): Float =
            scale *
                ((if (talkBack) NOMINAL_PANEL_PAD else NOMINAL_PANEL) +
                    if (hint) NOMINAL_HINT + PANEL_HINT_GAP else 0f)
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

        // DESIGN_SYSTEM.md §5.3's nominal chrome at fontScale 1.0 (dp).
        const val NOMINAL_FULL = 112f
        const val NOMINAL_COMPACT = 64f
        const val NOMINAL_HINT = 72f
        const val NOMINAL_CONTROLS = 80f
        const val NOMINAL_STATUS = 40f
        const val NOMINAL_PAD_ROW = 136f
        const val NOMINAL_RAILS = 192f
        const val NOMINAL_RAILS_PAD = 312f
        const val NOMINAL_PANEL = 256f
        const val NOMINAL_PANEL_PAD = 448f
        const val PANEL_HINT_GAP = 8f

        // F18-AC15's header that grows in steps as it narrows.
        const val GROW_FULL_1 = 360f
        const val GROW_FULL_2 = 300f
        const val FULL_OWN_ROW = 176f
        const val FULL_STACKED = 248f
        const val GROW_COMPACT_1 = 300f
        const val GROW_COMPACT_2 = 240f
        const val COMPACT_OWN_ROW = 112f
        const val COMPACT_STACKED = 184f

        // DESIGN_SYSTEM.md §5.2, §5.3 (D-075, D-188).
        const val BOARD_FLOOR = 220f
        const val BOARD_MIN = 280f
        const val BOARD_MAX = 720f
        const val COMPACT_MARGIN = 16
        const val RAIL_FRACTION = 0.22f
        const val RAIL_MIN = 160f
        const val RAIL_MAX = 240f
        const val RAIL_WITH_PAD = 208f
        const val RAIL_GAP = 12f
        const val PANEL_FRACTION = 0.32f
        const val PANEL_MIN = 280f
        const val PANEL_MAX = 400f
        const val PANEL_GAP = 32f
        const val HEIGHT_MEDIUM = 480f
        const val WIDTH_EXPANDED = 840f

        // The reference insets of the worked examples (dp).
        const val PORTRAIT_TOP = 48f
        const val PORTRAIT_BOTTOM = 24f
        const val LANDSCAPE_TOP = 24f
        const val SPLIT_PANE_HEIGHT = 450

        // F18-AC9's grid (dp).
        const val GRID_MIN = 220
        const val GRID_MAX = 1600
        const val GRID_STEP = 20

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
    }
}
