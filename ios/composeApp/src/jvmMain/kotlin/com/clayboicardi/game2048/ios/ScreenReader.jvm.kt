package com.clayboicardi.game2048.ios

import androidx.compose.runtime.Composable

/** The desktop/CI harness has no screen reader integration. */
@Composable actual fun rememberScreenReaderOn(): Boolean = false

actual fun announceForAccessibility(message: String) = Unit
