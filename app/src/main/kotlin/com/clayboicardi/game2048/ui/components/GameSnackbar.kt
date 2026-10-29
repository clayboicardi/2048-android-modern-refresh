package com.clayboicardi.game2048.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The screen's one snackbar host (DESIGN_SYSTEM.md §7.6; D-184, D-186): Material 3 `SnackbarHost`
 * and `Snackbar` defaults, unchanged at every motion level (their fade and accessibility-aware
 * timeout included). Show messages with [showGameSnackbar]. The Game arrangements place the host
 * per `GameLayoutSpec.mode` (D-186, D-188): STACKED at [stackedSnackbarTop], board − 16 dp wide;
 * SCROLL over the board, 8 dp above its bottom edge; THREE_COLUMN at the bottom of the actions
 * rail; SIDE_PANEL at the bottom of the panel, on the board's bottom edge. It never covers the
 * controls bar.
 */
@Composable
fun GameSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier.testTag(SNACKBAR_TAG)) { data ->
        Snackbar(data)
    }
}

/** The Game screen's snackbar host, as large as its snackbar while one shows (the layout tests). */
const val SNACKBAR_TAG = "game:snackbar"

/**
 * Shows [message] as the only visible snackbar (DESIGN_SYSTEM.md §7.6): the current one is
 * dismissed first, then [message] shows for `SnackbarDuration.Short` with no action. Suspends until
 * it is dismissed or times out, so call it from a scope that outlives the request.
 */
suspend fun SnackbarHostState.showGameSnackbar(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}

/**
 * The top of the STACKED snackbar host (D-186; DESIGN_SYSTEM.md §5.4, §7.6), in px: centred in the
 * gap between the board ([boardBottom]) and the controls ([controlsTop]) when that gap is at least
 * the snackbar's [height] plus [gapMargin] (16 dp); otherwise over the board, [boardMargin] (8 dp)
 * above its bottom edge.
 */
fun stackedSnackbarTop(
    boardBottom: Int,
    controlsTop: Int,
    height: Int,
    gapMargin: Int,
    boardMargin: Int,
): Int {
    val gap = controlsTop - boardBottom
    return if (gap >= height + gapMargin) {
        boardBottom + (gap - height) / 2
    } else {
        boardBottom - boardMargin - height
    }
}
