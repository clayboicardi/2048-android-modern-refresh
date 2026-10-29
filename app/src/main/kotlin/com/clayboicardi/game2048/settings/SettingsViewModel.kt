package com.clayboicardi.game2048.settings

import androidx.lifecycle.ViewModel
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SettingsRepository
import com.clayboicardi.game2048.ui.theme.AnimatorScaleSource
import com.clayboicardi.game2048.ui.theme.ContrastLevelSource
import kotlinx.coroutines.flow.StateFlow

/**
 * The Settings screen's state (ARCHITECTURE.md §4.7; DESIGN_SYSTEM.md §6.8; FEATURES.md F11; D-006,
 * D-073): the settings, the live system animator scale (the Motion row's lock) and the live system
 * contrast (the High-contrast row's lock). Entry-scoped: `container.settingsViewModelFactory()`
 * inside the `SettingsKey` entry. A lock never rewrites the stored value (F11 item 5).
 *
 * M4.1 contract (D-248): the constructor is fixed; M4.4 owns the members below and may add to them.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    animatorScale: AnimatorScaleSource,
    contrastLevel: ContrastLevelSource,
) : ViewModel() {
    /** `SettingsRepository.state`. */
    val settings: StateFlow<Settings> = repository.state

    /** The system animator duration scale; 0 locks the Motion row (D-006). */
    val animatorScale: StateFlow<Float> = animatorScale.scale

    /** The system contrast level; ≥ 0.5 locks the High-contrast row (D-073). */
    val systemContrast: StateFlow<Float> = contrastLevel.contrast

    /** Persists one change at once through the conflated writer (D-043; F11 item 3). */
    fun update(transform: (Settings) -> Settings) {
        repository.update(transform)
    }
}
