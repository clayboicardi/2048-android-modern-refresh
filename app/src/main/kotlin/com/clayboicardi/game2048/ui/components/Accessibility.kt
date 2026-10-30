package com.clayboicardi.game2048.ui.components

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether touch exploration (TalkBack's explore-by-touch) is on (D-081, D-082; ARCHITECTURE.md
 * §6.4): `AccessibilityManager.isTouchExplorationEnabled`, followed through
 * `addTouchExplorationStateChangeListener` while this composition lives, so turning TalkBack on or
 * off mid-game shows or hides the direction pad and the status line without a recreation
 * (FEATURES.md F17 edge cases). The listener writes the state; composition only reads it.
 */
@Composable
fun rememberTouchExplorationEnabled(): State<Boolean> {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val enabled = remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager, enabled) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on ->
            enabled.value = on
        }
        manager?.addTouchExplorationStateChangeListener(listener)
        // A change between the composition and this effect has no listener to report it.
        enabled.value = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
