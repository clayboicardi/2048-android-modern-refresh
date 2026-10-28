package com.clayboicardi.game2048.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app theme. M0: Material 3 Expressive with the default light or dark scheme and the expressive
 * motion scheme (D-026). Palettes, skins and motion locals arrive in M3.
 */
@Composable
fun GameTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
