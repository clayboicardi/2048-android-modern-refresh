package com.clayboicardi.game2048.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.clayboicardi.game2048.data.settings.MotionPreference

/**
 * The app theme (D-026, D-184; MOTION_SPEC.md §2.1, §2.2). From M3 part 1 it provides the Classic
 * palette and skin of [darkTheme] ([LocalTilePalette], [LocalTileSkin]) and the effective motion
 * level `effectiveMotion(motion, animatorScale)` ([LocalEffectiveMotion]), and passes
 * `gameMotionScheme(level)` and the Classic chrome scheme to `MaterialExpressiveTheme`. [motion] is
 * the persisted Motion setting (D-042) and [animatorScale] the live system animator duration scale
 * (`AnimatorScaleSource.scale`). M4 adds the other palettes, high contrast and dynamic chrome.
 */
@Composable
fun GameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motion: MotionPreference = MotionPreference.FULL,
    animatorScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val level = effectiveMotion(motion, animatorScale)
    CompositionLocalProvider(
        LocalTilePalette provides if (darkTheme) ClassicDarkPalette else ClassicLightPalette,
        LocalTileSkin provides if (darkTheme) ClassicDarkSkin else ClassicLightSkin,
        LocalEffectiveMotion provides level,
    ) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) ClassicDarkScheme else ClassicLightScheme,
            motionScheme = gameMotionScheme(level),
            content = content,
        )
    }
}

/**
 * Classic light chrome (DESIGN_SYSTEM.md §2.8; D-070): `SchemeContent` from `#8F7A66` with
 * `background` and `surface` set to the palette background. Every one of the 48 roles is given.
 */
private val ClassicLightScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF6B5946),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF85715D),
        onPrimaryContainer = Color(0xFFFFFBFF),
        inversePrimary = Color(0xFFDBC2AB),
        secondary = Color(0xFF675D54),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEFE0D4),
        onSecondaryContainer = Color(0xFF6D6259),
        tertiary = Color(0xFF5D5D48),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF76765F),
        onTertiaryContainer = Color(0xFFFFFBFF),
        background = Color(0xFFFAF8EF),
        onBackground = Color(0xFF1D1B1A),
        surface = Color(0xFFFAF8EF),
        onSurface = Color(0xFF1D1B1A),
        surfaceVariant = Color(0xFFEEE0D6),
        onSurfaceVariant = Color(0xFF4E453E),
        surfaceTint = Color(0xFF6E5B48),
        inverseSurface = Color(0xFF33302E),
        inverseOnSurface = Color(0xFFF6EFEC),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF7F756D),
        outlineVariant = Color(0xFFD1C4BA),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFF8F5),
        surfaceContainer = Color(0xFFF3EDE9),
        surfaceContainerHigh = Color(0xFFEDE7E4),
        surfaceContainerHighest = Color(0xFFE7E1DE),
        surfaceContainerLow = Color(0xFFF9F2EF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFDFD9D6),
        primaryFixed = Color(0xFFF8DEC6),
        primaryFixedDim = Color(0xFFDBC2AB),
        onPrimaryFixed = Color(0xFF26190A),
        onPrimaryFixedVariant = Color(0xFF554432),
        secondaryFixed = Color(0xFFEFE0D4),
        secondaryFixedDim = Color(0xFFD2C4B9),
        onSecondaryFixed = Color(0xFF211A13),
        onSecondaryFixedVariant = Color(0xFF4E453D),
        tertiaryFixed = Color(0xFFE5E4C9),
        tertiaryFixedDim = Color(0xFFC9C8AE),
        onTertiaryFixed = Color(0xFF1C1D0C),
        onTertiaryFixedVariant = Color(0xFF474834),
    )

/**
 * Classic dark chrome (DESIGN_SYSTEM.md §2.8; D-070): `SchemeContent` from `#8F7A66` with
 * `background` and `surface` set to the palette background. Every one of the 48 roles is given.
 */
private val ClassicDarkScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFDBC2AB),
        onPrimary = Color(0xFF3D2D1E),
        primaryContainer = Color(0xFFA38D78),
        onPrimaryContainer = Color(0xFF1D1104),
        inversePrimary = Color(0xFF6E5B48),
        secondary = Color(0xFFD2C4B9),
        onSecondary = Color(0xFF372F27),
        secondaryContainer = Color(0xFF51473F),
        onSecondaryContainer = Color(0xFFC3B6AB),
        tertiary = Color(0xFFC9C8AE),
        onTertiary = Color(0xFF31321F),
        tertiaryContainer = Color(0xFF92927A),
        onTertiaryContainer = Color(0xFF131405),
        background = Color(0xFF1B1916),
        onBackground = Color(0xFFE7E1DE),
        surface = Color(0xFF1B1916),
        onSurface = Color(0xFFE7E1DE),
        surfaceVariant = Color(0xFF4E453E),
        onSurfaceVariant = Color(0xFFD1C4BA),
        surfaceTint = Color(0xFFDBC2AB),
        inverseSurface = Color(0xFFE7E1DE),
        inverseOnSurface = Color(0xFF33302E),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF9A8F86),
        outlineVariant = Color(0xFF4E453E),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3B3937),
        surfaceContainer = Color(0xFF211F1D),
        surfaceContainerHigh = Color(0xFF2C2928),
        surfaceContainerHighest = Color(0xFF373432),
        surfaceContainerLow = Color(0xFF1D1B1A),
        surfaceContainerLowest = Color(0xFF100E0C),
        surfaceDim = Color(0xFF151311),
        primaryFixed = Color(0xFFF8DEC6),
        primaryFixedDim = Color(0xFFDBC2AB),
        onPrimaryFixed = Color(0xFF26190A),
        onPrimaryFixedVariant = Color(0xFF554432),
        secondaryFixed = Color(0xFFEFE0D4),
        secondaryFixedDim = Color(0xFFD2C4B9),
        onSecondaryFixed = Color(0xFF211A13),
        onSecondaryFixedVariant = Color(0xFF4E453D),
        tertiaryFixed = Color(0xFFE5E4C9),
        tertiaryFixedDim = Color(0xFFC9C8AE),
        onTertiaryFixed = Color(0xFF1C1D0C),
        onTertiaryFixedVariant = Color(0xFF474834),
    )
