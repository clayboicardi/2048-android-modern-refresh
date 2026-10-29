package com.clayboicardi.game2048.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star

/** A tile: `RoundedCornerShape(tileRadius)` (DESIGN_SYSTEM.md §4.2, §4.4). */
fun tileShape(tileRadius: Dp): Shape = RoundedCornerShape(tileRadius)

/** The board and the win and game-over scrim: concentric with the tiles, radius + gap (D-008). */
fun boardShape(tileRadius: Dp, gap: Dp): Shape = RoundedCornerShape(tileRadius + gap)

/**
 * The corner radius of a mini tile or any tile of side [side] outside the board: `clamp(
 * skin.radiusFraction · side, skin.radiusMin, skin.radiusMax)` (DESIGN_SYSTEM.md §4.4, §7.8).
 */
fun tileRadius(side: Dp, skin: TileSkin): Dp =
    (side * skin.radiusFraction).coerceIn(skin.radiusMin, skin.radiusMax)

/**
 * Game shapes (DESIGN_SYSTEM.md §4.4; D-012, D-064). Tiles are always rounded squares, never
 * `MaterialShapes` (D-012). Chrome uses the M3 default `MaterialTheme.shapes` (score chips `large`,
 * 16 dp; cards, dialogs and sheets `extraLarge`, 28 dp; the size chip `small`, 8 dp). The
 * graphics-shapes polygons are unit-sized (radius 1, centred on the origin): scale them to the
 * drawn size, and build their `android.graphics.Path` once and reuse it (MOTION_SPEC.md §7.3).
 */
object GameShapes {
    /**
     * The new-best star: `RoundedPolygon.star(10, innerRadius = 0.82f, rounding =
     * CornerRounding(0.15f))` (MOTION_SPEC.md §3.6, D-064).
     */
    val NewBestStar: RoundedPolygon =
        RoundedPolygon.star(
            numVerticesPerRadius = GameMotion.NewBest.STAR_POINTS,
            innerRadius = GameMotion.NewBest.STAR_INNER_RADIUS,
            rounding = CornerRounding(GameMotion.NewBest.STAR_ROUNDING),
        )

    /** The win hero badge's start shape: a 12-vertex circle (MOTION_SPEC.md §3.11). */
    val WinBadgeCircle: RoundedPolygon =
        RoundedPolygon.circle(numVertices = GameMotion.Win.BADGE_CIRCLE_VERTICES)

    /**
     * The win hero badge's end shape: the 12-point cookie `RoundedPolygon.star(12, innerRadius =
     * 0.88f, rounding = CornerRounding(0.14f))` (MOTION_SPEC.md §3.11, D-064).
     */
    val WinBadgeCookie: RoundedPolygon =
        RoundedPolygon.star(
            numVerticesPerRadius = GameMotion.Win.BADGE_POINTS,
            innerRadius = GameMotion.Win.BADGE_INNER_RADIUS,
            rounding = CornerRounding(GameMotion.Win.BADGE_ROUNDING),
        )

    /**
     * A new `Morph(WinBadgeCircle, WinBadgeCookie)`: progress 0 is the circle, 1 the cookie. Create
     * it once per badge (`remember`); `Morph.toPath(progress, path)` then writes into one path.
     */
    fun winBadgeMorph(): Morph = Morph(WinBadgeCircle, WinBadgeCookie)
}
