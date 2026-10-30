@file:Suppress("MatchingDeclarationName") // the file map names WindowMode.kt (ARCHITECTURE.md §1)

package com.clayboicardi.game2048.ui.components

import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * The Game screen's arrangements (DESIGN_SYSTEM.md §5.1, §5.3; D-091, D-188). [layoutModeFor] gives
 * the preferred one; `resolveGameLayout` picks the one drawn, which may be [STACKED] or [SCROLL]
 * instead.
 */
enum class LayoutMode {
    /** Header, board, controls in one column (DESIGN_SYSTEM.md §5.5 A, D, E). */
    STACKED,

    /** `[stats rail] [board] [actions rail]` (§5.5 B). */
    THREE_COLUMN,

    /** The board and a side panel (§5.5 C). */
    SIDE_PANEL,

    /** The last resort: one scrolling column with the COMPACT header (§5.3, §5.5 G). */
    SCROLL,
}

/**
 * The window-class preference (D-091, D-092, D-256; DESIGN_SYSTEM.md §5.1): a window under 480 dp
 * tall prefers THREE_COLUMN, one at least 840 dp wide SIDE_PANEL, every other STACKED, at the
 * `WindowSizeClass` breakpoints (`HEIGHT_DP_MEDIUM_LOWER_BOUND`, `WIDTH_DP_EXPANDED_LOWER_BOUND`).
 * Never SCROLL; `resolveGameLayout` resolves the arrangement actually drawn (D-188). `GameLayout`
 * applies it to its measured window, its constraints plus the safe insets (D-256).
 */
fun layoutModeFor(widthDp: Int, heightDp: Int): LayoutMode =
    preferredLayoutMode(widthDp.toFloat(), heightDp.toFloat())

/** [layoutModeFor] for a window size in fractional dp (`LayoutWindow`). */
internal fun preferredLayoutMode(widthDp: Float, heightDp: Float): LayoutMode =
    when {
        heightDp < GameDimens.HeightMediumMin.value -> LayoutMode.THREE_COLUMN
        widthDp >= GameDimens.WidthExpandedMin.value -> LayoutMode.SIDE_PANEL
        else -> LayoutMode.STACKED
    }
