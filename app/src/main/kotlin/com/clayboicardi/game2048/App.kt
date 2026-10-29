package com.clayboicardi.game2048

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.feedback.LocalHapticsEnabled
import com.clayboicardi.game2048.ui.components.ImmersiveMode
import com.clayboicardi.game2048.ui.theme.GameTheme

/**
 * The app root (ARCHITECTURE.md §4.7, §7.1, §10.3; D-248): `GameTheme` around
 * `AppNavHost(gameViewModel, appViewModel, container)`. It collects `appViewModel.appearance` and
 * the [container]'s animator scale, and from them
 * - picks dark mode: System follows the system, Light and Dark are fixed (D-006), and makes the
 *   system-bar icons follow the effective theme, not the system's (D-093, DESIGN_SYSTEM.md §5.2;
 *   F12-AC9);
 * - applies the Immersive setting whenever it changes, on every destination (D-093, F19), through
 *   the container's `SystemBarsController`; `MainActivity` re-applies it on focus gain;
 * - provides `LocalHapticsEnabled` (ARCHITECTURE.md §4.7, §5.8); `GameTheme` provides the palette,
 *   skin and effective-motion locals;
 * - passes Motion Off to `GameTheme` until the game is ready and a composition has shown the loaded
 *   appearance (`AppearanceUi.loaded`), so a cold start never crossfades from the defaults
 *   (MOTION_SPEC.md §3.14; D-251, D-255).
 *
 * [gameViewModel] and [appViewModel] are the activity-scoped ViewModels (§4.7); the container
 * supplies the system sources, the clock and the entry ViewModel factories. M4.2 adds the palette,
 * high-contrast, system-contrast and dynamic-chrome arguments of `GameTheme` (D-248).
 */
@Suppress("ComposeViewModelForwarding") // the activity's ViewModels serve the Game entry (§4.7)
@Composable
fun App(
    appViewModel: AppViewModel,
    gameViewModel: GameViewModel,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
    val scale by container.animatorScale.scale.collectAsStateWithLifecycle()
    val darkTheme =
        when (appearance.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val activity = LocalActivity.current
    DisposableEffect(activity, darkTheme) {
        (activity as? ComponentActivity)?.let { a ->
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
            a.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        onDispose {}
    }
    DisposableEffect(activity, appearance.immersive) {
        activity?.let { ImmersiveMode.apply(it.window, appearance.immersive, container.systemBars) }
        onDispose {}
    }
    CompositionLocalProvider(LocalHapticsEnabled provides appearance.haptics) {
        val systemContrast by container.contrastLevel.contrast.collectAsStateWithLifecycle()
        // MOTION_SPEC.md §3.14, §3.15 (D-251, D-255): the theme is static until the game is ready
        // and a composition has shown the loaded appearance, so a saved appearance never
        // crossfades from the defaults composed before the settings file was read, even when the
        // settings and the game land in one frame. A composition that starts with the loaded
        // appearance (a recreation) has no defaults to leave.
        val ready by gameViewModel.ready.collectAsStateWithLifecycle()
        var loadedShown by remember { mutableStateOf(appearance.loaded) }
        if (appearance.loaded && !loadedShown) SideEffect { loadedShown = true }
        GameTheme(
            darkTheme = darkTheme,
            palette = appearance.palette,
            highContrast = appearance.highContrast,
            systemContrast = systemContrast,
            dynamicChrome = appearance.dynamicChrome,
            motion = if (ready && loadedShown) appearance.motion else MotionPreference.OFF,
            animatorScale = scale,
        ) {
            AppNavHost(gameViewModel, appViewModel, container, modifier)
        }
    }
}
