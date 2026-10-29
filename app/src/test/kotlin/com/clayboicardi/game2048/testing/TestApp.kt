package com.clayboicardi.game2048.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.clayboicardi.game2048.App
import com.clayboicardi.game2048.AppViewModel
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.appViewModelFactory
import com.clayboicardi.game2048.game.GameViewModel

/**
 * `App()` over a test [container] the way `MainActivity` composes it (ARCHITECTURE.md §4.12;
 * D-248): with an `AppViewModel` of [container] ([rememberTestAppViewModel]) beside the test's
 * [gameViewModel].
 */
@Suppress("ComposeViewModelForwarding") // the test host hands App its ViewModels, as MainActivity
@Composable
fun TestApp(container: AppContainer, gameViewModel: GameViewModel, modifier: Modifier = Modifier) {
    App(rememberTestAppViewModel(container), gameViewModel, container, modifier)
}

/**
 * An `AppViewModel` of [container] (`appViewModelFactory()`), made in a `ViewModelStore` that this
 * composition owns and clears when it leaves, so no ViewModel outlives the test content.
 */
@Composable
fun rememberTestAppViewModel(container: AppContainer): AppViewModel {
    val store = remember(container) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    return remember(store) {
        ViewModelProvider.create(store, container.appViewModelFactory())[AppViewModel::class]
    }
}

/** An `AppViewModel` of [container] in [store], for rigs that make their ViewModels up front. */
fun testAppViewModel(container: AppContainer, store: ViewModelStore): AppViewModel =
    ViewModelProvider.create(store, container.appViewModelFactory())[AppViewModel::class]
