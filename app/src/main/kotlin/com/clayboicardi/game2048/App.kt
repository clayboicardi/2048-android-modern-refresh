package com.clayboicardi.game2048

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.ui.theme.AnimatorScaleSource
import com.clayboicardi.game2048.ui.theme.GameTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * The app root (ARCHITECTURE.md §10.3): `GameTheme` around `AppNavHost(gameViewModel, settings,
 * clock)`, the M3 form of §7.1 (D-236). [settings] is `SettingsRepository.state` and
 * [animatorScale] the container's one `AnimatorScaleSource`: their values feed
 * `GameTheme(darkTheme, motion, animatorScale)` and `AppNavHost(…, settings, clock)`.
 * [gameViewModel] is the activity-scoped `GameViewModel` (ARCHITECTURE.md §4.7) and [clock] the
 * container's `MonotonicClock`. The Theme setting picks dark mode: System follows the system, Light
 * and Dark are fixed (D-006).
 */
@Suppress("ComposeViewModelForwarding") // the activity's GameViewModel serves the Game entry (§4.7)
@Composable
fun App(
    gameViewModel: GameViewModel,
    settings: StateFlow<Settings>,
    animatorScale: AnimatorScaleSource,
    clock: MonotonicClock,
    modifier: Modifier = Modifier,
) {
    val current by settings.collectAsStateWithLifecycle()
    val scale by animatorScale.scale.collectAsStateWithLifecycle()
    val darkTheme =
        when (current.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    GameTheme(darkTheme = darkTheme, motion = current.motion, animatorScale = scale) {
        AppNavHost(gameViewModel, current, clock, modifier)
    }
}
