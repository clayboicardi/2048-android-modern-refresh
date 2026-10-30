package com.clayboicardi.game2048.game.board

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Counts recompositions for `TileRecompositionTest` and `BoardRecompositionBudgetTest` (D-150;
 * MOTION_SPEC.md §7.2, ARCHITECTURE.md §5.7). `TileView` calls `SideEffect {
 * LocalRecompositionCounter.current?.onTile(id) }`, `BoardView` calls `onBoard()` and `GameScreen`
 * calls `onScreen()` the same way. No string arguments, so production allocates nothing.
 */
interface RecompositionCounter {
    fun onTile(id: Long)

    fun onBoard()

    fun onScreen()
}

/** `null` in production; tests provide a counting [RecompositionCounter]. */
@Suppress("ComposeCompositionLocalUsage") // D-150: test-only hook, null in production
val LocalRecompositionCounter = staticCompositionLocalOf<RecompositionCounter?> { null }
