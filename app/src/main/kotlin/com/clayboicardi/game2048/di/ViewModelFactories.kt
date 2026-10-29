package com.clayboicardi.game2048.di

import android.content.res.Resources
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clayboicardi.game2048.AppViewModel
import com.clayboicardi.game2048.about.AboutViewModel
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.settings.SettingsViewModel
import com.clayboicardi.game2048.stats.StatsViewModel

/** The activity-scoped `GameViewModel` over this container (D-022, ARCHITECTURE.md §4.7). */
fun AppContainer.gameViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        GameViewModel(gameRepository, recordsRepository, settingsRepository, clock, seedSource)
    }
}

/** The activity-scoped `AppViewModel`: appearance and What's new (ARCHITECTURE.md §4.7, §4.13). */
fun AppContainer.appViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer { AppViewModel(settingsRepository, versionCode) }
}

/** The `SettingsKey` entry's `SettingsViewModel` (ARCHITECTURE.md §4.7). */
fun AppContainer.settingsViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer { SettingsViewModel(settingsRepository, animatorScale, contrastLevel) }
}

/** The `StatsKey` entry's `StatsViewModel` (ARCHITECTURE.md §4.7; D-198). */
fun AppContainer.statsViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer { StatsViewModel(recordsRepository, gameRepository, settingsRepository) }
}

/**
 * The `AboutKey` and `LicensesKey` entries' `AboutViewModel` (ARCHITECTURE.md §4.7) over the
 * activity's [resources] (`LocalResources.current`, so the per-app locale applies) and the
 * container's I/O dispatcher (D-248).
 */
fun AppContainer.aboutViewModelFactory(resources: Resources): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { AboutViewModel(resources, ioDispatcher) }
    }
