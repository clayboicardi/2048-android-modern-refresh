package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * The app's only modal bottom sheet (DESIGN_SYSTEM.md §7.4; D-007, D-009, D-181), and the only
 * `@OptIn(ExperimentalMaterial3Api::class)` in the app's main code: `ModalBottomSheet` over
 * `rememberBottomSheetState(Hidden, {Hidden, Expanded})`, which skips the partially expanded state;
 * the deprecated `rememberModalBottomSheetState` is never used. The container is M3's default
 * `surfaceContainerLow` with the 28 dp top radius, the drag handle and
 * `BottomSheetDefaults.modalWindowInsets`, and the content pads the `navigationBars` inset at the
 * bottom. Its motion comes from the theme's `MotionScheme` (`gameMotionScheme(level)`, D-184;
 * MOTION_SPEC.md §3.18), and Back, a scrim tap or a drag down hide it and then call
 * [onDismissRequest]. [modifier] is the sheet's own (its caller's test tag). `BoardSizeSheet` and
 * `WhatsNewSheet` call only this.
 *
 * [content] receives `dismiss(then)` (D-236): a sheet that closes itself (a size row, "Got it")
 * calls it to hide with the sheet motion first (MOTION_SPEC.md §3.18), and `then` runs once the
 * sheet is hidden, instead of leaving the composition mid-frame.
 */
@OptIn(ExperimentalMaterial3Api::class) // D-181: ModalBottomSheet and its state
@Composable
fun GameModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(dismiss: (then: () -> Unit) -> Unit) -> Unit,
) {
    val state =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = state,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets },
    ) {
        val dismiss: (then: () -> Unit) -> Unit =
            remember(state, scope) {
                { then ->
                    scope
                        .launch { state.hide() }
                        .invokeOnCompletion { if (!state.isVisible) then() }
                }
            }
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
            ),
        ) {
            content(dismiss)
        }
    }
}
