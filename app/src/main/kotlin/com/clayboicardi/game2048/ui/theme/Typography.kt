@file:Suppress("MatchingDeclarationName") // ARCHITECTURE.md §10.3 name (D-248)

package com.clayboicardi.game2048.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle

/**
 * The chrome text styles that are material3 1.5-only (DESIGN_SYSTEM.md §3.5; D-026, D-246): the
 * emphasized styles of the M3 baseline `Typography()` that `MaterialExpressiveTheme` provides. Code
 * outside `ui/theme` and `ui/components` reads them only through these wrappers, so PLAN.md §5.7's
 * A3 check (`Emphasized\b`) stays clean and a 1.4 fallback touches this file only.
 *
 * M4.1 contract (D-248).
 */
object GameTextStyles {
    /** Section headers of Settings, How to play, About and Licenses (`titleSmallEmphasized`). */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleSmallEmphasized

    /** The Regular win and game-over card titles (`displaySmallEmphasized`, §6.3, §6.4). */
    val cardTitle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.displaySmallEmphasized

    /** The Compact win and game-over card titles (`headlineSmallEmphasized`, §6.3a, §6.4a). */
    val cardTitleCompact: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineSmallEmphasized

    /** The Score and Best chip labels (`labelMediumEmphasized`, §7.2). */
    val chipLabel: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMediumEmphasized
}
