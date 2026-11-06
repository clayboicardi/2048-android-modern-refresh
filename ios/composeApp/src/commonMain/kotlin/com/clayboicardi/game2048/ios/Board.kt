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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.engine.Board

/** Classic gap-to-tile ratio and tile-corner radius fraction (D-075: 0.14, 0.07). */
private const val GAP_RATIO = 0.14f
private const val RADIUS_FRACTION = 0.07f

/**
 * The N×N board, drawn from the committed engine [board] (never from in-flight state). Tiles snap
 * to their cells; a cell whose exponent changes pops in with a spring (spawn and merge feedback).
 * Full slide animation is a follow-up (D-272).
 */
@Composable
fun BoardView(board: Board, colors: TileColors, modifier: Modifier = Modifier) {
    val n = board.size.n
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val boardSide = maxWidth
        // total = tile*(n + (n+1)*gapRatio); gap = gapRatio*tile.
        val tile: Dp = boardSide / (n + (n + 1) * GAP_RATIO)
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (col in 0 until n) {
                        TileCell(board[row, col], tile, radius, colors)
                    }
                }
            }
        }
    }
}

/**
 * One grid cell. Composed by position, so its `remember`ed pop state stays with the cell: when the
 * exponent here changes to a non-empty value, it springs from 0.72 to 1.
 */
@Composable
private fun TileCell(exp: Int, tile: Dp, radius: Dp, colors: TileColors) {
    val scale = remember { Animatable(1f) }
    var prevExp by remember { mutableStateOf(exp) }
    LaunchedEffect(exp) {
        if (exp != 0 && exp != prevExp) {
            scale.snapTo(POP_FROM)
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
        }
        prevExp = exp
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
            Text(
                text = label,
                color = colors.ink(exp),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                fontSize = fontSizeFor(label.length, tile),
                lineHeight = fontSizeFor(label.length, tile),
                modifier = Modifier.padding(horizontal = tile * 0.06f),
            )
        }
    }
}

private const val POP_FROM = 0.72f

/** Shrinks the numeral as it gets longer so it never overflows the tile. */
private fun fontSizeFor(length: Int, tile: Dp) =
    when {
        length <= 2 -> tile.value * 0.42f
        length == 3 -> tile.value * 0.34f
        length == 4 -> tile.value * 0.27f
        length <= 6 -> tile.value * 0.20f
        else -> tile.value * 0.15f
    }.sp
