package com.clayboicardi.game2048.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.game.board.boardGeometry
import com.clayboicardi.game2048.game.board.drawBoardCells
import com.clayboicardi.game2048.ui.components.MiniTile
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.LocalTileSkin

/**
 * The 1024 × 500 px feature graphic (DESIGN_SYSTEM.md §8.5; D-113, D-115) at `w512dp-h250dp-xhdpi`:
 * on the palette background, the Classic 4×4 [board] (`FIX_FEATURE`) at (32, 32) dp, 186 dp wide;
 * from x = 258 dp, vertically centred and left-aligned, the "2048" wordmark (the title numerals, 96
 * dp, in the identity glyph colour #3A2900) above five 36 dp mini tiles 2, 8, 128, 512 and 2048
 * with 6 dp gaps. The wordmark is the only text, because every listing locale shares the graphic.
 * Wordmark and tiles use fixed dp sizes, whatever the font scale. Call it inside `GameTheme`
 * (Classic light) and the `"store:frame"` box of [FrameWidth] × [FrameHeight].
 */
@Composable
fun FeatureGraphic(board: Board, modifier: Modifier = Modifier) {
    val palette = LocalTilePalette.current
    Box(modifier.requiredSize(FrameWidth, FrameHeight).background(palette.background)) {
        FeatureBoard(board, Modifier.offset(Margin, Margin))
        Column(
            modifier = Modifier.offset(x = GroupStart).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Wordmark()
            Spacer(Modifier.height(WordmarkGap))
            Row(horizontalArrangement = Arrangement.spacedBy(MiniTileGap)) {
                for (exp in MINI_TILES) MiniTile(exp, MiniTileSize)
            }
        }
    }
}

/**
 * The board as the game draws it at rest (`drawBoardCells`, then each tile at its cell), without
 * glow or motion: the tiles are [MiniTile]s at the board geometry's tile side.
 */
@Composable
private fun FeatureBoard(board: Board, modifier: Modifier = Modifier) {
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val density = LocalDensity.current.density
    val n = board.size.n
    val geometry = remember(n, skin, density) { boardGeometry(n, BoardSide, skin, density) }
    val tileSide: Dp = (geometry.tilePx / density).dp
    Box(
        modifier.requiredSize(BoardSide).drawBehind { drawBoardCells(geometry, palette, skin) },
    ) {
        for (index in 0 until board.cellCount) {
            val exp = board[index]
            if (exp == 0) continue
            val at = geometry.cellTopLeft((index % n).toFloat(), (index / n).toFloat())
            MiniTile(exp, tileSide, Modifier.offset { IntOffset(at.x, at.y) })
        }
    }
}

/** "2048" in the title numerals (wght 850, the skin's ROND) at 96 dp, centred in a 96 dp row. */
@Composable
private fun Wordmark() {
    val skin = LocalTileSkin.current
    val fontSize = with(LocalDensity.current) { WordmarkSize.toSp() }
    val color = colorResource(R.color.ic_launcher_glyph)
    Box(Modifier.requiredHeight(WordmarkSize), contentAlignment = Alignment.CenterStart) {
        BasicText(
            text = WORDMARK,
            modifier = Modifier.wrapContentHeight(unbounded = true),
            style = GameFonts.title(skin).copy(fontSize = fontSize, color = color),
            softWrap = false,
            maxLines = 1,
        )
    }
}

/** The capture frame (§8.5): 512 × 250 dp, 1024 × 500 px at xhdpi. */
val FrameWidth: Dp = 512.dp
val FrameHeight: Dp = 250.dp

private val Margin = 32.dp
private val BoardSide = 186.dp
private val GroupStart = 258.dp
private val WordmarkSize = 96.dp
private val WordmarkGap = 16.dp
private val MiniTileSize = 36.dp
private val MiniTileGap = 6.dp

/** The wordmark is the game's name, never translated (D-113, U2). */
private const val WORDMARK = "2048"

/** The mini tiles' exponents: 2, 8, 128, 512, 2048. */
private val MINI_TILES = listOf(1, 3, 7, 9, 11)
