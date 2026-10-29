package com.clayboicardi.game2048.ui.components

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The window-class preference `layoutModeFor` (D-091, D-092; DESIGN_SYSTEM.md §5.1). */
class LayoutModeTest {
    /** F18-AC1 */
    @Test
    fun modeTableAndBoundaries() {
        val table =
            listOf(
                Triple(360, 800, LayoutMode.STACKED),
                Triple(915, 412, LayoutMode.THREE_COLUMN),
                Triple(673, 841, LayoutMode.STACKED),
                Triple(841, 673, LayoutMode.SIDE_PANEL),
                Triple(800, 1280, LayoutMode.STACKED),
                Triple(1280, 800, LayoutMode.SIDE_PANEL),
                Triple(1000, 400, LayoutMode.THREE_COLUMN),
                Triple(600, 479, LayoutMode.THREE_COLUMN),
                Triple(600, 480, LayoutMode.STACKED),
                Triple(839, 600, LayoutMode.STACKED),
                Triple(840, 600, LayoutMode.SIDE_PANEL),
            )
        for ((width, height, mode) in table) {
            assertWithMessage("${width}×$height").that(layoutModeFor(width, height)).isEqualTo(mode)
        }
        // The preference is never SCROLL, whatever the window (D-188).
        for (width in 1..GRID_MAX step GRID_STEP) {
            for (height in 1..GRID_MAX step GRID_STEP) {
                assertWithMessage("${width}×$height is never SCROLL")
                    .that(layoutModeFor(width, height))
                    .isNotEqualTo(LayoutMode.SCROLL)
            }
        }
    }

    private companion object {
        const val GRID_MAX = 2000
        const val GRID_STEP = 7
    }
}
