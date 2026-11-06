package com.clayboicardi.game2048.ios

import androidx.compose.runtime.Composable

/**
 * True while the platform screen reader (VoiceOver) is running, and kept current as it is turned on
 * or off. With it on, swipes drive the screen reader instead of the board, so the game shows a
 * direction pad (as the Android app does under TalkBack).
 */
@Composable expect fun rememberScreenReaderOn(): Boolean

/** Speaks [message] through the screen reader, if one is running; otherwise does nothing. */
expect fun announceForAccessibility(message: String)
