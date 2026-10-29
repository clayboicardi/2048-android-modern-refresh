package com.clayboicardi.game2048.game.board

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.max
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
