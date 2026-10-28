package com.clayboicardi.game2048.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clayboicardi.game2048.game.GameViewModel

/** The activity-scoped `GameViewModel` over this container (D-022, ARCHITECTURE.md §4.7). */
fun AppContainer.gameViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        GameViewModel(gameRepository, recordsRepository, settingsRepository, clock, seedSource)
    }
}
