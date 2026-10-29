package com.clayboicardi.game2048.testing

import com.clayboicardi.game2048.ui.theme.AnimatorScaleSource
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The test `AnimatorScaleSource` (ARCHITECTURE.md §4.2, MOTION_SPEC.md §2.1): the system animator
 * duration scale as a settable flow. A system-scale test sets it together with the compose rule's
 * `MotionDurationScale`, so Compose's scale and the app's effective level agree (§8.1).
 */
class FakeAnimatorScaleSource(initial: Float = 1f) : AnimatorScaleSource {
    override val scale: MutableStateFlow<Float> = MutableStateFlow(initial)
}
