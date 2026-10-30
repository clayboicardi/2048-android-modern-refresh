package com.clayboicardi.game2048.game.motion

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import com.clayboicardi.game2048.ui.theme.GameMotion.Confetti
import com.clayboicardi.game2048.ui.theme.GameMotion.NewBest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.collections.immutable.ImmutableList

/**
 * Draws every alive particle of [system] (MOTION_SPEC.md §3.11 draw step): shapes in dp converted
 * with [density] (px per dp), flutter, flip and spin, the 0.4 → 1 scale-in and the alpha fade.
 * Called from a canvas draw lambda on every frame, so it allocates nothing (MOTION_SPEC.md §7.3):
 * value-class arguments only, and the four-point star `Path` built once.
 */
fun DrawScope.drawParticles(system: ConfettiSystem, colors: ParticleColors, density: Float) {
    for (i in 0 until system.aliveCount) {
        if (system.shape[i] == SHAPE_SPARKLE) {
            drawSparkle(system, i, colors.sparkle, density)
        } else {
            drawConfetto(system, i, colors, density)
        }
    }
}

/**
 * The colours particles draw with (MOTION_SPEC.md §3.6, §3.11): [confetti] holds the 7 confetti
 * colours `palette.fill(7..11)`, `primary`, `tertiary`, indexed by a particle's colour index;
 * sparkles use [sparkle] (`tertiary`).
 */
@Immutable data class ParticleColors(val confetti: ImmutableList<Color>, val sparkle: Color)

/** Milliseconds per second. */
private const val MS_PER_S = 1000f

/** Half of a size, to centre a shape on the particle. */
private const val HALF = 0.5f

/** The star's four points and its inner radius as a fraction of the outer one. */
private const val STAR_POINTS = 4
private const val STAR_INNER_RATIO = 0.38f

/**
 * The four-point star of confetti and sparkles, built once: tip to tip 1 px, centred on the origin,
 * a point straight up. Draws scale it to 8 dp (confetti) or 6 dp (sparkles).
 */
private val UnitStar: Path =
    Path().apply {
        val vertices = 2 * STAR_POINTS
        for (k in 0 until vertices) {
            val radius = if (k % 2 == 0) HALF else HALF * STAR_INNER_RATIO
            val angle = PI * (k.toDouble() / STAR_POINTS - HALF)
            val px = (radius * cos(angle)).toFloat()
            val py = (radius * sin(angle)).toFloat()
            if (k == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }

/**
 * One confetti particle (MOTION_SPEC.md §3.11): `translate(x + 12 dp · sin(flutterOmega · age +
 * phase), y); rotate(rotation); scale(1, cos(flipOmega · age) · s)`, alpha 1 then → 0 linearly over
 * the last 0.35 s of life.
 */
private fun DrawScope.drawConfetto(
    system: ConfettiSystem,
    i: Int,
    colors: ParticleColors,
    density: Float,
) {
    val age = system.age[i]
    val alpha = ((system.life[i] - age) / Confetti.FADE_OUT_S).coerceIn(0f, 1f)
    val flutterDp =
        Confetti.FLUTTER_AMPLITUDE_DP * sin(system.flutterOmega[i] * age + system.flutterPhase[i])
    val flipScale = cos(system.flipOmega[i] * age) * system.sizeScale[i]
    val color = colors.confetti.getOrElse(system.colorIndex[i].toInt()) { colors.sparkle }
    val shape = system.shape[i]
    withTransform(
        {
            translate((system.x[i] + flutterDp) * density, system.y[i] * density)
            rotate(system.rotationDeg[i], Offset.Zero)
            scale(1f, flipScale, Offset.Zero)
        },
    ) {
        when (shape) {
            SHAPE_RECT -> {
                val w = Confetti.RECT_W_DP * density
                val h = Confetti.RECT_H_DP * density
                drawRoundRect(
                    color = color,
                    topLeft = Offset(-w * HALF, -h * HALF),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(Confetti.RECT_CORNER_DP * density),
                    alpha = alpha,
                )
            }
            SHAPE_CIRCLE ->
                drawCircle(
                    color = color,
                    radius = Confetti.CIRCLE_RADIUS_DP * density,
                    center = Offset.Zero,
                    alpha = alpha,
                )
            else -> {
                val side = Confetti.STAR_SIZE_DP * density
                scale(side, side, Offset.Zero) { drawPath(UnitStar, color, alpha = alpha) }
            }
        }
    }
}

/**
 * One new-best sparkle (MOTION_SPEC.md §3.6): a 6 dp four-point star at its position, alpha 1 then
 * → 0 linearly over the last 250 ms of its life.
 */
private fun DrawScope.drawSparkle(system: ConfettiSystem, i: Int, color: Color, density: Float) {
    val fadeS = NewBest.SPARKLE_FADE_MS / MS_PER_S
    val alpha = ((system.life[i] - system.age[i]) / fadeS).coerceIn(0f, 1f)
    val side = NewBest.SPARKLE_SIZE_DP * density
    withTransform(
        {
            translate(system.x[i] * density, system.y[i] * density)
            scale(side, side, Offset.Zero)
        },
    ) {
        drawPath(UnitStar, color, alpha = alpha)
    }
}
