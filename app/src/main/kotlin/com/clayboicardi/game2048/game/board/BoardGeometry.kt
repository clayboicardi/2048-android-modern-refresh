package com.clayboicardi.game2048.game.board

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.clayboicardi.game2048.ui.components.LayoutMode
import com.clayboicardi.game2048.ui.components.preferredLayoutMode
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.TileSkin
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The pixel geometry of one board (DESIGN_SYSTEM.md §4.2, D-075). Pure; `boardGeometry` builds it.
 * Board radius = tile radius + gap, so the corners are concentric.
 */
@Immutable
data class BoardGeometry(
    val n: Int,
    val boardPx: Int,
    val gapPx: Int,
    val tilePx: Float,
    val tileRadiusPx: Float,
    val boardRadiusPx: Float,
) {
    /** x = column, y = row, in cells; fractional during a slide. Rounded to whole pixels (§4.7). */
    fun cellTopLeft(x: Float, y: Float): IntOffset =
        IntOffset(
            (gapPx + x * (tilePx + gapPx)).roundToInt(),
            (gapPx + y * (tilePx + gapPx)).roundToInt(),
        )
}

/**
 * The geometry of an [n]×[n] board of side [board] at [density] px per dp (DESIGN_SYSTEM.md §4.2,
 * §4.3; D-075): `boardPx = floor(board × density)`, the gap `round(gapRatio × raw tile)` clamped to
 * `BoardGapMin..BoardGapMax`, tiles filling the rest, the tile radius `radiusFraction × tile`
 * clamped to the skin's range. 4×4 Classic at 328 dp and density 1.0: gap 10 dp, tile 69.5 dp.
 */
fun boardGeometry(n: Int, board: Dp, skin: TileSkin, density: Float): BoardGeometry =
    boardGeometryPx(n, floor(board.value * density).toInt(), skin, density)

/**
 * [boardGeometry] for a board side already in whole pixels: `BoardView` measures its side in px, so
 * it never converts back to dp and loses a pixel to rounding.
 */
internal fun boardGeometryPx(n: Int, boardPx: Int, skin: TileSkin, density: Float): BoardGeometry {
    val tileRawPx = boardPx / (n + skin.gapRatio * (n + 1))
    val gapPx =
        (skin.gapRatio * tileRawPx)
            .roundToInt()
            .coerceIn(
                (GameDimens.BoardGapMin.value * density).roundToInt(),
                (GameDimens.BoardGapMax.value * density).roundToInt(),
            )
    val tilePx = (boardPx - (n + 1) * gapPx) / n.toFloat()
    val radiusPx =
        (skin.radiusFraction * tilePx).coerceIn(
            skin.radiusMin.value * density,
            skin.radiusMax.value * density,
        )
    return BoardGeometry(n, boardPx, gapPx, tilePx, radiusPx, radiusPx + gapPx)
}

/**
 * The horizontal content margin on one side (DESIGN_SYSTEM.md §5.2; D-051, D-075, D-188): 16, 24 or
 * 32 dp for a window narrower than 600 dp, narrower than 840 dp, or wider, and at least that side's
 * `WindowInsets.systemGestures` inset [gestureInset] (F15-AC14).
 */
fun horizontalMargin(windowWidth: Dp, gestureInset: Dp): Dp {
    val byWidth =
        when {
            windowWidth < GameDimens.WidthMediumMin -> GameDimens.MarginCompact
            windowWidth < GameDimens.WidthExpandedMin -> GameDimens.MarginMedium
            else -> GameDimens.MarginExpanded
        }
    return max(byWidth, gestureInset)
}

/**
 * The vertical content margin (DESIGN_SYSTEM.md §5.2, D-188): 16, 24 or 32 dp for a window shorter
 * than 480 dp, shorter than 900 dp, or taller.
 */
fun verticalMargin(windowHeight: Dp): Dp =
    when {
        windowHeight < GameDimens.HeightMediumMin -> GameDimens.MarginCompact
        windowHeight < GameDimens.HeightExpandedMin -> GameDimens.MarginMedium
        else -> GameDimens.MarginExpanded
    }

/** The geometry before the first measure: drawn by nobody, replaced in the first layout pass. */
internal fun placeholderGeometry(n: Int): BoardGeometry = BoardGeometry(n, 0, 0, 0f, 0f, 0f)

/**
 * Measures the board at the side its caller gives it and publishes the [n]×[n] geometry of that
 * side into [holder] before its children are measured, so the first frame of any size draws
 * correctly and a resize never recomposes a tile (only layout and draw read [holder]).
 */
internal fun Modifier.measureGeometry(
    holder: MutableState<BoardGeometry>,
    n: Int,
    skin: TileSkin,
    density: Float,
): Modifier = layout { measurable, constraints ->
    val side = boardSide(constraints)
    val geometry = boardGeometryPx(n, side, skin, density)
    if (Snapshot.withoutReadObservation { holder.value } != geometry) holder.value = geometry
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

/** The square side within [constraints]: the smaller bounded maximum. */
private fun boardSide(constraints: Constraints): Int =
    when {
        constraints.hasBoundedWidth && constraints.hasBoundedHeight ->
            min(constraints.maxWidth, constraints.maxHeight)
        constraints.hasBoundedWidth -> constraints.maxWidth
        constraints.hasBoundedHeight -> constraints.maxHeight
        else -> constraints.minWidth
    }

/**
 * The STACKED and SCROLL header (DESIGN_SYSTEM.md §5.3, §6.1; D-188): with the title, or without.
 */
enum class HeaderStyle {
    FULL,
    COMPACT,
}

/**
 * The window `resolveGameLayout` lays out (DESIGN_SYSTEM.md §5.2, §5.3), in dp: its size, the
 * `WindowInsets.safeDrawing` insets, the `WindowInsets.systemGestures` side insets (D-051), whether
 * the TalkBack direction pad ([talkBack], §6.6) and the first-launch hint ([hint], §6.2) are shown.
 */
@Immutable
data class LayoutWindow(
    val width: Float,
    val height: Float,
    val safeLeft: Float,
    val safeTop: Float,
    val safeRight: Float,
    val safeBottom: Float,
    val gestureLeft: Float,
    val gestureRight: Float,
    val talkBack: Boolean,
    val hint: Boolean,
) {
    /**
     * The margins of DESIGN_SYSTEM.md §5.2, per axis (D-188), each side at least its gesture inset.
     */
    val marginLeft: Float
        get() = horizontalMargin(width.dp, gestureLeft.dp).value

    val marginRight: Float
        get() = horizontalMargin(width.dp, gestureRight.dp).value

    val marginVertical: Float
        get() = verticalMargin(height.dp).value

    /** The window minus `safeDrawing` minus the margins (DESIGN_SYSTEM.md §5.3). */
    val availW: Float
        get() = width - safeLeft - safeRight - marginLeft - marginRight

    val availH: Float
        get() = height - safeTop - safeBottom - 2 * marginVertical
}

/**
 * The measured-chrome terms of DESIGN_SYSTEM.md §5.3 (D-188), in dp at the asked width; each is
 * non-increasing in its width argument. `GameLayout` answers them from its measurement probe.
 */
interface ChromeMeasure {
    /**
     * The STACKED/SCROLL header of [style] at [width], in the chip layout `scoreFit` gives there.
     */
    fun header(style: HeaderStyle, width: Float): Float

    /** The first-launch hint card. */
    fun hint(width: Float): Float

    /** The controls bar (Undo and New game, D-249). */
    fun controls(width: Float): Float

    /**
     * The TalkBack status line at its reservation: its longest announcements, at least two lines.
     */
    fun status(width: Float): Float

    /** The direction pad with the New game column beside it (DESIGN_SYSTEM.md §6.6, D-249). */
    fun padRow(width: Float): Float

    /** The taller of the stats rail without its title and the actions rail, at [rail]. */
    fun rails(rail: Float): Float

    /** The side panel's content at [panel]. */
    fun panel(panel: Float): Float
}

/**
 * The arrangement `GameLayout` draws (DESIGN_SYSTEM.md §5.3): [mode], the STACKED/SCROLL [header]
 * (FULL or COMPACT; SCROLL is always COMPACT; FULL, unused, otherwise), the [board] side in
 * `[BoardFloor, BoardMax]` dp, and the [rail] (THREE_COLUMN) or [panel] (SIDE_PANEL) width.
 */
@Immutable
data class GameLayoutSpec(
    val mode: LayoutMode,
    val header: HeaderStyle,
    val board: Float,
    val rail: Float = 0f,
    val panel: Float = 0f,
)

/**
 * Resolves the arrangement and its board (DESIGN_SYSTEM.md §5.3; D-091, D-188; pure, JVM-tested by
 * `BoardGeometryTest`). Each candidate's board is computed for [window] with the measured [chrome];
 * the first rule that holds wins: the preferred THREE_COLUMN or SIDE_PANEL when it fits and its
 * board is at least STACKED's (or STACKED does not fit), STACKED (FULL header while its re-measured
 * board stays at least `BoardMin` and fits, else COMPACT), THREE_COLUMN whatever the height class,
 * and otherwise SCROLL with the COMPACT header. Every board is within `[BoardFloor, BoardMax]`.
 */
fun resolveGameLayout(window: LayoutWindow, chrome: ChromeMeasure): GameLayoutSpec {
    val terms = LayoutTerms(window, chrome)
    val floor = GameDimens.BoardFloor.value
    val boardMax = GameDimens.BoardMax.value
    val (h, s) = terms.stackedBoard()
    val fitsS = s >= floor
    val rail = terms.rail()
    val t = minOf(window.availH, window.availW - 2 * rail - 2 * GameDimens.RailGap.value, boardMax)
    val fitsT = t >= floor && chrome.rails(rail) <= window.availH
    val panel = terms.panel()
    val p = minOf(window.availH, window.availW - panel - GameDimens.PanelGap.value, boardMax)
    val fitsP = p >= floor && chrome.panel(panel) <= p
    val pref = preferredLayoutMode(window.width, window.height)
    return when {
        pref == LayoutMode.THREE_COLUMN && fitsT && (!fitsS || t >= s) ->
            GameLayoutSpec(LayoutMode.THREE_COLUMN, HeaderStyle.FULL, t, rail = rail)
        pref == LayoutMode.SIDE_PANEL && fitsP && (!fitsS || p >= s) ->
            GameLayoutSpec(LayoutMode.SIDE_PANEL, HeaderStyle.FULL, p, panel = panel)
        fitsS -> GameLayoutSpec(LayoutMode.STACKED, h, s)
        fitsT -> GameLayoutSpec(LayoutMode.THREE_COLUMN, HeaderStyle.FULL, t, rail = rail)
        else -> {
            val board =
                minOf(
                        window.availW,
                        window.availH -
                            terms.top(HeaderStyle.COMPACT, terms.w0) -
                            terms.statusBlock(),
                    )
                    .coerceIn(floor, boardMax)
            GameLayoutSpec(LayoutMode.SCROLL, HeaderStyle.COMPACT, board)
        }
    }
}

/**
 * The composite chrome terms of DESIGN_SYSTEM.md §5.3 over [chrome] for [window]: `w0`, `gap(h)`,
 * `top(h, w)`, `bottom(h, w)`, `stacked(h, w)` and SCROLL's `statusBlock`.
 */
internal class LayoutTerms(private val window: LayoutWindow, private val chrome: ChromeMeasure) {
    /** The STACKED and SCROLL content width. */
    val w0: Float =
        min(maxOf(window.availW, GameDimens.BoardFloor.value), GameDimens.BoardMax.value)

    /** Header ↔ board and board ↔ controls. */
    fun gap(h: HeaderStyle): Float =
        if (h == HeaderStyle.FULL) GameDimens.StackGap.value else GameDimens.Space8.value

    /** The header with the hint and its 8 dp gaps, or the header and `gap(h)`. */
    fun top(h: HeaderStyle, w: Float): Float =
        chrome.header(h, w) +
            if (window.hint) 2 * GameDimens.Space8.value + chrome.hint(w) else gap(h)

    /** `gap(h)` and the controls bar, or the status line and the pad row with their gaps (§6.6). */
    fun bottom(h: HeaderStyle, w: Float): Float =
        if (window.talkBack) {
            padGap(h) + chrome.status(w) + GameDimens.Space8.value + chrome.padRow(w)
        } else {
            gap(h) + chrome.controls(w)
        }

    fun stacked(h: HeaderStyle, w: Float): Float = top(h, w) + bottom(h, w)

    /** SCROLL: 8 dp and the status line at `w0` while the pad is shown, else 0. */
    fun statusBlock(): Float =
        if (window.talkBack) GameDimens.Space8.value + chrome.status(w0) else 0f

    /** Board ↔ status line with the pad: 16 dp under the FULL header, 8 dp under COMPACT. */
    fun padGap(h: HeaderStyle): Float =
        if (h == HeaderStyle.FULL) GameDimens.Space16.value else GameDimens.Space8.value

    /**
     * STACKED's header and board: FULL when the window is at least `BoardMin` wide and FULL's board
     * at `w0` is at least `BoardMin`; FULL chrome is as wide as the board, so it is measured once
     * more at that board and dropped for COMPACT (at `w0`) when the board falls below `BoardMin` or
     * the board and chrome no longer fit `availH` (D-188).
     */
    fun stackedBoard(): Pair<HeaderStyle, Float> {
        val boardMin = GameDimens.BoardMin.value
        val availH = window.availH
        val full = HeaderStyle.FULL
        if (window.availW < boardMin || availH - stacked(full, w0) < boardMin) return compact()
        var s = min(w0, availH - stacked(full, w0))
        s -= maxOf(0f, stacked(full, s) - stacked(full, w0))
        return if (s < boardMin || s + stacked(full, s) > availH) compact() else full to s
    }

    /** THREE_COLUMN's rail: a fraction of the width within its bounds, wider with the pad. */
    fun rail(): Float {
        val rail =
            (GameDimens.RailFraction * window.width).coerceIn(
                GameDimens.RailMin.value,
                GameDimens.RailMax.value,
            )
        return if (window.talkBack) maxOf(rail, GameDimens.RailWithPadMin.value) else rail
    }

    /** SIDE_PANEL's panel: a fraction of the width within its bounds. */
    fun panel(): Float =
        (GameDimens.PanelFraction * window.width).coerceIn(
            GameDimens.PanelMin.value,
            GameDimens.PanelMax.value,
        )

    private fun compact(): Pair<HeaderStyle, Float> =
        HeaderStyle.COMPACT to min(w0, window.availH - stacked(HeaderStyle.COMPACT, w0))
}
