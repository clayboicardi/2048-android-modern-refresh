package com.clayboicardi.game2048.testing

import com.clayboicardi.game2048.ui.theme.ContrastLevelSource
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The test `ContrastLevelSource` (PLAN.md §2.6 E; FEATURES.md F11-AC8, F12-AC6): the system
 * contrast level as a settable flow, 0 (standard) unless a test passes another.
 */
class FakeContrastLevelSource(initial: Float = 0f) : ContrastLevelSource {
    override val contrast: MutableStateFlow<Float> = MutableStateFlow(initial)
}
