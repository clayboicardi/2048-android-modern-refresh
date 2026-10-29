package com.clayboicardi.game2048.game.header

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.GameShapes

/**
 * The new-best star (MOTION_SPEC.md §3.6; DESIGN_SYSTEM.md §4.4, §7.2; D-064): `GameShapes
 * .NewBestStar` at `NewBest.STAR_SIZE_DP` in `tertiary`, drawn behind the BEST label. Its [scale],
 * [rotationDeg] and [alpha] are read in the layer phase, so the running moment recomposes nothing.
 * Decorative: the label says "New best".
 */
@Composable
fun NewBestBadge(
    scale: () -> Float,
    rotationDeg: () -> Float,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.tertiary
    Spacer(
        modifier
            .size(GameMotion.NewBest.STAR_SIZE_DP.dp)
            .clearAndSetSemantics {}
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
                rotationZ = rotationDeg()
                this.alpha = alpha()
            }
            .drawWithCache {
                // The polygon is unit-sized around the origin: scale it to the badge once.
                val half = size.minDimension / 2
                val path = GameShapes.NewBestStar.toPath().asComposePath()
                path.transform(
                    Matrix().apply {
                        translate(size.width / 2, size.height / 2)
                        scale(half, half)
                    },
                )
                onDrawBehind { drawPath(path, color) }
            },
    )
}
