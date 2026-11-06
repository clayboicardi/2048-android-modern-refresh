package com.clayboicardi.game2048.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome

/**
 * Warm "classic 2048" accent for buttons and the selected size. White on it is 4.08:1, which passes
 * for the bold ≥ 14 pt labels it carries (large text, 3:1).
 */
private val Accent = Color(0xFF8F7A66)

/** Entry composable shared by the iOS app and the desktop/CI harness. */
@Composable
fun MainView() {
    val scope = rememberCoroutineScope()
    val model = remember { GameModel(IosGameStore(), scope) }
    LaunchedEffect(model) { model.load() }
    Game2048App(model)
}

@Composable
fun Game2048App(model: GameModel) {
    val dark = isSystemInDarkTheme()
    val colors: TileColors = if (dark) ClassicDark else ClassicLight
    val scheme =
        if (dark) {
            darkColorScheme(
                primary = Accent,
                onPrimary = Color.White,
                background = colors.background,
            )
        } else {
            lightColorScheme(
                primary = Accent,
                onPrimary = Color.White,
                background = colors.background,
            )
        }
    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            // Nothing interactive until the save is read (off the main thread), so no move can race
            // the load.
            if (model.loaded) GameScreen(model, colors)
        }
    }
}

@Composable
private fun GameScreen(model: GameModel, colors: TileColors) {
    val state = model.state
    val haptics = LocalHapticFeedback.current
    val overlayUp = state.status == GameStatus.WON || state.status == GameStatus.OVER
    // A pending "start over" (New Game or a size chip) that needs confirming; null when none.
    var confirmSize by remember { mutableStateOf<BoardSize?>(null) }

    // Mirrors the Android app (D-007): only a game with progress asks before it is replaced.
    fun requestNewGame(size: BoardSize) {
        if (hasProgress(state)) confirmSize = size else model.newGame(size)
    }

    // Moves are allowed only while no card or dialog is up (D-054); every input route checks it.
    val canMove = !overlayUp && confirmSize == null
    val screenReader = rememberScreenReaderOn()

    // A screen-reader move (pad button or board action) is also spoken, since the board itself
    // gives no audible feedback.
    fun accessibleMove(direction: Direction) {
        val outcome = model.move(direction)
        if (outcome is MoveOutcome.Moved) fireHaptics(haptics, outcome)
        announceForAccessibility(moveAnnouncement(direction, outcome, model.state.score))
    }

    val moveActions =
        if (canMove) {
            Direction.entries.map { direction ->
                CustomAccessibilityAction("Move ${direction.spoken}") {
                    accessibleMove(direction)
                    true
                }
            }
        } else {
            emptyList()
        }

    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Only scrolls when content outgrows the screen (large text sizes, small phones). The
            // board's swipe detector claims its drags first, so a swipe never scrolls the page.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Header(colors = colors, onNewGame = { requestNewGame(state.size) })
            Spacer(Modifier.size(12.dp))
            Scores(score = state.score, best = model.bestScore, colors = colors)
            Spacer(Modifier.size(12.dp))
            SizeChips(current = state.size, colors = colors, onSize = { requestNewGame(it) })
            Spacer(Modifier.size(16.dp))
            Box {
                BoardView(
                    board = state.board,
                    colors = colors,
                    moveActions = moveActions,
                    modifier =
                        Modifier.swipeToMove(enabled = canMove) { direction ->
                            val outcome = model.move(direction)
                            if (outcome is MoveOutcome.Moved) fireHaptics(haptics, outcome)
                        },
                )
                if (overlayUp) {
                    GameOverlay(
                        won = state.status == GameStatus.WON,
                        colors = colors,
                        onKeepGoing = { model.keepGoing() },
                        onNewGame = { model.newGame() },
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            if (screenReader) {
                Spacer(Modifier.size(16.dp))
                DirectionPad(enabled = canMove, onMove = { accessibleMove(it) })
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

    confirmSize?.let { size ->
        AlertDialog(
            onDismissRequest = { confirmSize = null },
            title = { Text("Start a new game?") },
            text = { Text("Your current game will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSize = null
                        model.newGame(size)
                    },
                ) {
                    Text("New game")
                }
            },
            dismissButton = { TextButton(onClick = { confirmSize = null }) { Text("Cancel") } },
        )
    }
}

private val Direction.spoken: String
    get() =
        when (this) {
            Direction.UP -> "up"
            Direction.RIGHT -> "right"
            Direction.DOWN -> "down"
            Direction.LEFT -> "left"
        }

/** What a screen reader says after a pad or board-action move. */
private fun moveAnnouncement(direction: Direction, outcome: MoveOutcome, score: Long): String =
    when (outcome) {
        is MoveOutcome.Moved ->
            buildString {
                append("Moved ${direction.spoken}. Score $score.")
                if (outcome.becameWon) append(" You win!")
                if (outcome.becameOver) append(" Game over.")
            }
        is MoveOutcome.Invalid -> "Can't move ${direction.spoken}."
        is MoveOutcome.Blocked -> "The game has ended."
    }

/**
 * Up, left, right and down buttons, shown only while VoiceOver is running: with it on, swipes drive
 * the screen reader rather than the board (the Android app shows the same pad under TalkBack). Each
 * button is at least 56 dp and reads as "Move …".
 */
@Composable
private fun DirectionPad(enabled: Boolean, onMove: (Direction) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PadButton(Direction.UP, "↑", enabled, onMove)
        Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
            PadButton(Direction.LEFT, "←", enabled, onMove)
            PadButton(Direction.RIGHT, "→", enabled, onMove)
        }
        PadButton(Direction.DOWN, "↓", enabled, onMove)
    }
}

@Composable
private fun PadButton(
    direction: Direction,
    arrow: String,
    enabled: Boolean,
    onMove: (Direction) -> Unit,
) {
    Button(
        onClick = { onMove(direction) },
        enabled = enabled,
        modifier =
            Modifier.size(56.dp).semantics { contentDescription = "Move ${direction.spoken}" },
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
    ) {
        Text(arrow, fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** A game worth confirming before it is discarded: still playable and with a score. */
private fun hasProgress(state: GameState): Boolean =
    state.score > 0 &&
        (state.status == GameStatus.PLAYING || state.status == GameStatus.KEEP_PLAYING)

private fun fireHaptics(haptics: HapticFeedback, moved: MoveOutcome.Moved) {
    when {
        moved.becameWon -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        moved.becameOver -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
        moved.mergeCount > 0 -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
}

/** Title and New Game. The title shrinks to the space the button leaves, so neither is clipped. */
@Composable
private fun Header(colors: TileColors, onNewGame: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FitText(
            "2048",
            maxSize = 48.sp,
            color = colors.title,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
            alignment = Alignment.CenterStart,
        )
        Spacer(Modifier.size(8.dp))
        Button(
            onClick = onNewGame,
            colors =
                ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
        ) {
            Text("New Game", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

/** SCORE and BEST share the row equally; each figure shrinks to its card rather than overflow. */
@Composable
private fun Scores(score: Long, best: Long, colors: TileColors) {
    Row(Modifier.fillMaxWidth()) {
        StatBox("SCORE", score.toString(), colors, Modifier.weight(1f))
        Spacer(Modifier.size(8.dp))
        StatBox("BEST", best.toString(), colors, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: String, colors: TileColors, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.scoreCard)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FitText(label, 12.sp, Color.White, FontWeight.Bold, Modifier.fillMaxWidth())
        FitText(value, 24.sp, Color.White, FontWeight.ExtraBold, Modifier.fillMaxWidth())
    }
}

/** Board sizes 3×3–8×8 as a radio group; each chip takes an equal share and is ≥ 48 dp tall. */
@Composable
private fun SizeChips(current: BoardSize, colors: TileColors, onSize: (BoardSize) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (size in BoardSize.ALL) {
            val selected = size == current
            val shape = RoundedCornerShape(8.dp)
            val fill =
                if (selected) Modifier.background(Accent, shape)
                else Modifier.border(1.5.dp, colors.title, shape)
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .then(fill)
                    .selectable(selected = selected, role = Role.RadioButton) { onSize(size) }
                    .semantics { contentDescription = "${size.n} by ${size.n} board" },
                contentAlignment = Alignment.Center,
            ) {
                FitText(
                    "${size.n}×${size.n}",
                    maxSize = 16.sp,
                    color = if (selected) Color.White else colors.title,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun GameOverlay(
    won: Boolean,
    colors: TileColors,
    onKeepGoing: () -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(colors.background.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FitText(
                if (won) "You win!" else "Game over",
                maxSize = 40.sp,
                color = colors.title,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(16.dp))
            if (won) {
                Button(
                    onClick = onKeepGoing,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.White,
                        ),
                ) {
                    Text("Keep going", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.size(8.dp))
            }
            Button(
                onClick = onNewGame,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White,
                    ),
            ) {
                Text(
                    if (won) "New game" else "Try again",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
