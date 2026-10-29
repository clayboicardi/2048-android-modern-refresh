package com.clayboicardi.game2048.game.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.Glow
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/**
 * One tile (ARCHITECTURE.md §5.3, §5.7; DESIGN_SYSTEM.md §4.6, §4.7; D-025, D-078, D-080). It takes
 * only its [id], [exp] and stable holders, so a tile that slides keeps equal parameters and is
 * skipped by recomposition. Its position is read in the layout phase, scale and alpha in its layer,
 * fill, label, sheen and glow in draw. Tiles carry no semantics (the fixed cell layer does).
 */
@Composable
internal fun TileView(
    id: Long,
    exp: Int,
    animator: BoardAnimator,
    geometry: State<BoardGeometry>,
    style: TileStyle,
    modifier: Modifier = Modifier,
) {
    val anim = remember(animator, id) { animator.anim(id) }
    val glow = remember(style, exp) { style.palette.glow(exp) }
    val counter = LocalRecompositionCounter.current
    SideEffect { counter?.onTile(id) }
    Box(
        modifier
            .tilePlacement(anim, geometry)
            .then(if (glow != null) Modifier.tileGlow(glow, anim, geometry, style) else Modifier)
            .tileFace(exp, anim, geometry, style),
    )
}

/**
 * The tile's size (measure), position (placement) and scale (its own scale times the game-over
 * deflate) and alpha (its layer), all read in their own phase (ARCHITECTURE.md §5.3, §5.7). The
 * layer is the placement's own, not a `graphicsLayer` modifier: that modifier is a semantics node
 * in ui 1.12, and tiles carry no semantics (D-080).
 */
private fun Modifier.tilePlacement(anim: TileAnim, geometry: State<BoardGeometry>): Modifier {
    val layer: GraphicsLayerScope.() -> Unit = {
        val s = anim.scale.value * anim.deflate.value
        scaleX = s
        scaleY = s
        alpha = anim.alpha.value
    }
    return layout { measurable, _ ->
        val side = geometry.value.tilePx.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(side, side))
        layout(side, side) {
            val at = anim.offset.value
            placeable.placeWithLayer(geometry.value.cellTopLeft(at.x, at.y), layerBlock = layer)
        }
    }
}

/** Measures to the board's tile side (layout phase, so a resize never recomposes a tile). */
internal fun Modifier.tileSize(geometry: State<BoardGeometry>): Modifier = layout { measurable, _ ->
    val side = geometry.value.tilePx.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

/**
 * The tile's fill, high-contrast outline, cached label and merge sheen (DESIGN_SYSTEM.md §2.7,
 * §3.3, §4.6; D-187). The label is looked up once per size; every frame only draws.
 */
private fun Modifier.tileFace(
    exp: Int,
    anim: TileAnim,
    geometry: State<BoardGeometry>,
    style: TileStyle,
): Modifier = drawWithCache {
    val g = geometry.value
    val side = size.width
    val text = style.textCache.text(exp, g.tilePx, density, style.skin.id)
    val textTopLeft = Offset(text.topLeftX(side), text.topLeftY(side))
    val fill = style.palette.fill(exp)
    val ink = style.palette.ink(exp)
    val radius = CornerRadius(g.tileRadiusPx)
    val outline = style.skin.tileOutline
    val outlineWidth = GameDimens.HcOutlineWidth.toPx()
    val outlineStroke = Stroke(outlineWidth)
    val inset = outlineWidth / 2f
    val outlineSize = Size(side - outlineWidth, size.height - outlineWidth)
    val outlineRadius = CornerRadius((g.tileRadiusPx - inset).coerceAtLeast(0f))
    onDrawBehind {
        val saturation = style.effects.saturation.value
        drawRoundRect(fill.desaturate(saturation), cornerRadius = radius)
        if (outline != null) {
            drawRoundRect(
                outline,
                topLeft = Offset(inset, inset),
                size = outlineSize,
                cornerRadius = outlineRadius,
                style = outlineStroke,
            )
        }
        drawText(text.layout, color = ink.desaturate(saturation), topLeft = textTopLeft)
        val sheen = anim.sheen.value
        if (sheen > 0f) drawRoundRect(Color.White, cornerRadius = radius, alpha = sheen)
    }
}

/**
 * The tile glow (DESIGN_SYSTEM.md §4.6, D-078): the outer `dropShadow` at the palette's alpha plus
 * the merge pulse's boost, and the inner highlight, both desaturated with the tile at game over.
 */
private fun Modifier.tileGlow(
    glow: Glow,
    anim: TileAnim,
    geometry: State<BoardGeometry>,
    style: TileStyle,
): Modifier =
    dropShadow(style.shape) {
            val tile = geometry.value.tilePx
            radius = glow.radiusFraction * tile
            spread = glow.spreadFraction * tile
            color = glow.color.desaturate(style.effects.saturation.value)
            alpha = (glow.alpha + anim.glowBoost.value).coerceAtMost(1f)
        }
        .innerShadow(style.shape) {
            val tile = geometry.value.tilePx
            radius = glow.innerRadiusDp * density + glow.innerRadiusFraction * tile
            offset = Offset(0f, glow.innerOffsetYFraction * tile)
            color = glow.innerColor.desaturate(style.effects.saturation.value)
            alpha = glow.innerAlpha
        }

/**
 * Arithmetic desaturation, equal to `ColorMatrix.setToSaturation(s)` (MOTION_SPEC.md §7.3): no
 * allocation, so it runs on every frame of the game-over desaturation.
 */
internal fun Color.desaturate(s: Float): Color {
    if (s >= 1f) return this
    val inv = 1f - s
    val r0 = LUMA_R * inv
    val g0 = LUMA_G * inv
    val b0 = LUMA_B * inv
    return Color(
        red = ((r0 + s) * red + g0 * green + b0 * blue).coerceIn(0f, 1f),
        green = (r0 * red + (g0 + s) * green + b0 * blue).coerceIn(0f, 1f),
        blue = (r0 * red + g0 * green + (b0 + s) * blue).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/** `ColorMatrix.setToSaturation`'s luminance weights. */
private const val LUMA_R = 0.213f
private const val LUMA_G = 0.715f
private const val LUMA_B = 0.072f

/** Tiles in z-order, keyed by id (ARCHITECTURE.md §5.3; D-025). */
@Composable
internal fun TileLayer(
    tiles: ImmutableList<TileUi>,
    animator: BoardAnimator,
    geometry: State<BoardGeometry>,
    style: TileStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        for (tile in tiles) {
            key(tile.id) { TileView(tile.id, tile.exp, animator, geometry, style) }
        }
    }
}

/**
 * The fixed N² cell nodes (D-080; FEATURES.md F17): each at its cell, with `collectionItemInfo(row,
 * 1, col, 1)` and "Row r, column c, value" (1-based, the full decimal of `TileLabels.fullDecimal`)
 * or "Row r, column c, empty".
 */
@Composable
internal fun CellSemanticsLayer(
    cells: ImmutableList<Int>,
    n: Int,
    geometry: State<BoardGeometry>,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        for (index in cells.indices) {
            val row = index / n
            val col = index % n
            val label = cellLabel(row, col, cells[index])
            Box(
                Modifier.offset { geometry.value.cellTopLeft(col.toFloat(), row.toFloat()) }
                    .tileSize(geometry)
                    .semantics {
                        contentDescription = label
                        collectionItemInfo = CollectionItemInfo(row, 1, col, 1)
                    },
            )
        }
    }
}

@Composable
private fun cellLabel(row: Int, col: Int, exp: Int): String {
    val r = (row + 1).toString()
    val c = (col + 1).toString()
    return if (exp == 0) {
        stringResource(R.string.a11y_cell_empty, r, c)
    } else {
        stringResource(R.string.a11y_cell_tile, r, c, TileLabels.fullDecimal(exp))
    }
}
