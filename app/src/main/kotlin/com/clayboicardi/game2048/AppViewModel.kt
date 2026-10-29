package com.clayboicardi.game2048

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clayboicardi.game2048.about.CHANGELOG
import com.clayboicardi.game2048.about.ChangelogEntry
import com.clayboicardi.game2048.about.decideWhatsNew
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.MoveTiming
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SettingsRepository
import com.clayboicardi.game2048.data.settings.SwipeSensitivity
import com.clayboicardi.game2048.data.settings.ThemeMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app-wide settings that `App()`, `GameTheme` and the Game screen read (ARCHITECTURE.md §4.7;
 * D-006, D-248): the ten appearance and input settings, plus [lastBoardSize], the size the board
 * chip shows while the game loads (DESIGN_SYSTEM.md §6.1 Loading state), and [loaded], false while
 * the values are the defaults shown before the settings file has been read (D-255). Equal settings
 * give an equal value, so a write of another field (`onboardingDone`, `lastSeenVersionCode`)
 * recomposes nothing.
 */
@Immutable
data class AppearanceUi(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: PaletteId = PaletteId.CLASSIC,
    val highContrast: Boolean = false,
    val dynamicChrome: Boolean = false,
    val motion: MotionPreference = MotionPreference.FULL,
    val haptics: Boolean = true,
    val moveTiming: MoveTiming = MoveTiming.DURING_SWIPE,
    val swipeSensitivity: SwipeSensitivity = SwipeSensitivity.MEDIUM,
    val immersive: Boolean = false,
    val keepScreenOn: Boolean = false,
    val lastBoardSize: Int = Settings().lastBoardSize,
    val loaded: Boolean = false,
)

/** The [AppearanceUi] of these settings, [loaded] once they are the settings file's (D-255). */
fun Settings.toAppearance(loaded: Boolean): AppearanceUi =
    AppearanceUi(
        themeMode = themeMode,
        palette = palette,
        highContrast = highContrast,
        dynamicChrome = dynamicChrome,
        motion = motion,
        haptics = haptics,
        moveTiming = moveTiming,
        swipeSensitivity = swipeSensitivity,
        immersive = immersive,
        keepScreenOn = keepScreenOn,
        lastBoardSize = lastBoardSize,
        loaded = loaded,
    )

/**
 * The activity-scoped app state (ARCHITECTURE.md §4.7, §4.13; D-006, D-009): [appearance], derived
 * from `SettingsRepository.state`, and the What's new sheet's entries [whatsNew] with
 * [onWhatsNewDismissed]. Created by `container.appViewModelFactory()` in `MainActivity`.
 *
 * What's new (§4.13; FEATURES.md F24 item 1): once, after `settings.awaitLoaded()`, the ViewModel
 * evaluates `decideWhatsNew(lastSeenVersionCode, versionCode, CHANGELOG)`. It submits the decided
 * `storeVersionCode` at once (`settings.update`), before [whatsNew] carries the entries, so the
 * sheet appears at most once even if the process dies while it is open; a fresh install stores
 * [versionCode] and shows nothing, and the same version or a downgrade writes nothing.
 * [versionCode] is the container's (`BuildConfig.VERSION_CODE`, D-134).
 */
class AppViewModel(settings: SettingsRepository, val versionCode: Int) : ViewModel() {
    /**
     * The appearance settings; equal settings keep the value (no recomposition).
     * `SettingsRepository` publishes the file's value before it reports `loaded`, so a loaded value
     * is never the defaults.
     */
    val appearance: StateFlow<AppearanceUi> =
        combine(settings.state, settings.loaded) { value, loaded -> value.toAppearance(loaded) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                settings.state.value.toAppearance(settings.loaded.value),
            )

    private val pendingWhatsNew = MutableStateFlow<ImmutableList<ChangelogEntry>?>(null)

    /**
     * The entries newer than the last seen version while the What's new sheet is shown over the
     * Game root (D-009); `null` otherwise, and in every v1.0 build (versionCode 30000 is the
     * first).
     */
    val whatsNew: StateFlow<ImmutableList<ChangelogEntry>?> = pendingWhatsNew.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = settings.awaitLoaded()
            val decision = decideWhatsNew(loaded.lastSeenVersionCode, versionCode, CHANGELOG)
            decision.storeVersionCode?.let { code ->
                settings.update { it.copy(lastSeenVersionCode = code) }
            }
            if (decision.entries.isNotEmpty()) {
                pendingWhatsNew.value = decision.entries.toImmutableList()
            }
        }
    }

    /** The sheet was dismissed (drag, Back, scrim or "Got it"): it is not shown again. */
    fun onWhatsNewDismissed() {
        pendingWhatsNew.value = null
    }
}
