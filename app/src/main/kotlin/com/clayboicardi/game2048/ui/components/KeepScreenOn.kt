package com.clayboicardi.game2048.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the screen on while [on] (D-006; FEATURES.md F20): `LocalView.current.keepScreenOn`, the
 * window flag of the Compose root view, which needs no permission. It is set after each change of
 * [on] and cleared when this composition leaves, so a destination that does not compose it (every
 * sub-screen) never keeps the screen on.
 */
@Composable
fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}
