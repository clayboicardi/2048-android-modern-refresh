package com.clayboicardi.game2048.ios

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.engine.Board

/** Classic gap-to-tile ratio and tile-corner radius fraction (D-075: 0.14, 0.07). */
private const val GAP_RATIO = 0.14f
private const val RADIUS_FRACTION = 0.07f
private const val POP_FROM = 0.72f

/**
 * The N×N board, drawn from the committed engine [board] (never from in-flight state). Tiles snap
 * to their cells; a cell whose exponent changes to a tile pops in with a spring (spawn and merge
 * feedback). Every cell settles at scale 1, so the last frame always equals the static render. Full
 * slide animation is a follow-up (D-272).
 */
@Composable
fun BoardView(board: Board, colors: TileColors, modifier: Modifier = Modifier) {
    val n = board.size.n
    // Tile numerals are sized to the tile, not to the reader's text-size setting: scaling them
    // with it would only push the digits out of the tile.
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        // total = tile*(n + (n+1)*gapRatio); gap = gapRatio*tile.
        val tile: Dp = maxWidth / (n + (n + 1) * GAP_RATIO)
        val gap: Dp = tile * GAP_RATIO
        val radius = tile * RADIUS_FRACTION
        Column(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(gap))
                .background(colors.board)
                .padding(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (row in 0 until n) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until n) {
                        TileCell(board[row, col], tile, radius, fontScale, colors)
                    }
                }
            }
        }
    }
}

/**
 * One grid cell, composed by position so its pop state stays with the cell. Each new exponent
 * restarts the effect: a new tile value pops from [POP_FROM] to 1, anything else (an emptied cell,
 * or a return to the previous value) snaps straight to 1. An interrupted pop therefore can never
 * leave a cell at an intermediate scale.
 */
@Composable
private fun TileCell(exp: Int, tile: Dp, radius: Dp, fontScale: Float, colors: TileColors) {
    val scale = remember { Animatable(1f) }
    // Plain holder, not State: read and written only inside the effect.
    val shownExp = remember { intArrayOf(exp) }
    LaunchedEffect(exp) {
        val pop = exp != 0 && exp != shownExp[0]
        shownExp[0] = exp
        if (pop) {
            scale.snapTo(POP_FROM)
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
        } else {
            scale.snapTo(1f)
        }
    }
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(tile)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(shape)
            .background(if (exp == 0) colors.empty else colors.fill(exp)),
        contentAlignment = Alignment.Center,
    ) {
        if (exp != 0) {
            val label = tileLabel(exp)
            val size = numeralSize(label.length, tile, fontScale)
            Text(
                text = label,
                color = colors.ink(exp),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                fontSize = size,
                lineHeight = size,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The numeral size for a label of [length] characters on a [tile]: a fraction of the tile that
 * shrinks as the label grows, divided by [fontScale] so 1 sp renders as the intended tile fraction
 * whatever the text-size setting.
 */
private fun numeralSize(length: Int, tile: Dp, fontScale: Float): TextUnit {
    val fraction =
        when {
            length <= 2 -> 0.42f
            length == 3 -> 0.34f
            length == 4 -> 0.27f
            length <= 6 -> 0.20f
            else -> 0.15f
        }
    return (tile.value * fraction / fontScale).sp
}
