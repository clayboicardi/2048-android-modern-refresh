package com.clayboicardi.game2048.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The screen's one snackbar host (DESIGN_SYSTEM.md §7.6; D-184, D-186): Material 3 `SnackbarHost`
 * and `Snackbar` defaults, unchanged at every motion level (their fade and accessibility-aware
 * timeout included). Show messages with [showGameSnackbar]; the caller places the host.
 */
@Composable
fun GameSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data -> Snackbar(data) }
}

/**
 * Shows [message] as the only visible snackbar (DESIGN_SYSTEM.md §7.6): the current one is
 * dismissed first, then [message] shows for `SnackbarDuration.Short` with no action. Suspends until
 * it is dismissed or times out, so call it from a scope that outlives the request.
 */
suspend fun SnackbarHostState.showGameSnackbar(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}
