package com.clayboicardi.game2048.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome

/** Warm "classic 2048" accent for buttons, in both schemes. */
private val Accent = Color(0xFF8F7A66)

/** Entry composable shared by the iOS app and the desktop/CI harness. */
@Composable
fun MainView() {
    val model = remember { GameModel(IosGameStore()) }
    Game2048App(model)
}

@Composable
fun Game2048App(model: GameModel) {
    val dark = isSystemInDarkTheme()
    val colors: TileColors = if (dark) ClassicDark else ClassicLight
    val scheme =
        if (dark) {
            darkColorScheme(primary = Accent, onPrimary = Color.White, background = colors.background)
        } else {
            lightColorScheme(primary = Accent, onPrimary = Color.White, background = colors.background)
        }
    MaterialTheme(colorScheme = scheme) {
        val state = model.state
        val haptics = LocalHapticFeedback.current
        val overlayUp = state.status == GameStatus.WON || state.status == GameStatus.OVER

        Box(
            Modifier.fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
                Header(score = state.score, best = model.bestScore, colors = colors)
                Spacer(Modifier.size(12.dp))
                Controls(current = state.size, colors = colors, onNewGame = { model.newGame() }, onSize = { model.newGame(it) })
                Spacer(Modifier.size(16.dp))
                Box {
                    BoardView(
                        board = state.board,
                        colors = colors,
                        modifier =
                            Modifier.swipeToMove(enabled = !overlayUp) { direction ->
                                when (val outcome = model.move(direction)) {
                                    is MoveOutcome.Moved -> fireHaptics(haptics, outcome)
                                    else -> Unit
                                }
                            },
                    )
                    if (overlayUp) {
                        GameOverlay(
                            won = state.status == GameStatus.WON,
                            colors = colors,
                            onKeepGoing = { model.keepGoing() },
                            onNewGame = { model.newGame() },
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    "Join the tiles, get to 2048!",
                    color = colors.onBackground,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }
    }
}

private fun fireHaptics(haptics: androidx.compose.ui.hapticfeedback.HapticFeedback, moved: MoveOutcome.Moved) {
    when {
        moved.becameWon -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        moved.becameOver -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
        moved.mergeCount > 0 -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
}

@Composable
private fun Header(score: Long, best: Long, colors: TileColors) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("2048", color = colors.title, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp)
        Spacer(Modifier.weight(1f))
        StatBox("SCORE", score.toString(), colors)
        Spacer(Modifier.size(8.dp))
        StatBox("BEST", best.toString(), colors)
    }
}

@Composable
private fun StatBox(label: String, value: String, colors: TileColors) {
    Column(
        Modifier.clip(RoundedCornerShape(6.dp)).background(colors.board).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Controls(current: BoardSize, colors: TileColors, onNewGame: () -> Unit, onSize: (BoardSize) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (size in BoardSize.ALL) {
                val selected = size == current
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Accent else colors.board.copy(alpha = 0.55f))
                        .clickable { onSize(size) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${size.n}",
                        color = Color.White,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNewGame, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
            Text("New Game", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameOverlay(won: Boolean, colors: TileColors, onKeepGoing: () -> Unit, onNewGame: () -> Unit) {
    Box(
        Modifier.matchParentSizeOrFill().clip(RoundedCornerShape(8.dp)).background(colors.background.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (won) "You win!" else "Game over",
                color = colors.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
            )
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (won) {
                    Button(onClick = onKeepGoing, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Keep going", fontWeight = FontWeight.Bold)
                    }
                }
                Button(onClick = onNewGame, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text(if (won) "New game" else "Try again", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The overlay fills the board Box it is stacked into. */
@Composable
private fun Modifier.matchParentSizeOrFill(): Modifier = this.fillMaxSize()
