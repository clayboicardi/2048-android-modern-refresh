package com.clayboicardi.game2048.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.clayboicardi.game2048.game.board.BoardUi

/**
 * A retention state of one presentation scope (ARCHITECTURE.md §4.11 item 2; D-047): it follows
 * this collector, but a new value that [samePresentation] calls equivalent to the one it holds
 * keeps the held instance and invalidates no reader. So a presentation report (which replaces only
 * the presentation of the same committed state) never recomposes, relaunches or cuts short what the
 * live scope is presenting, while a scope created later starts from the static form.
 *
 * Remember it in the composition of the scope it serves: the screen scope in `GameScreen`'s body,
 * an arrangement scope first thing in its slot. It is created and forgotten with that scope.
 */
@Composable
fun <T> State<T>.rememberPresented(samePresentation: (T, T) -> Boolean): State<T> =
    remember(this) { derivedStateOf(policy = PresentationPolicy(samePresentation)) { value } }

/** Only `BoardPresenter.settled` keeps a `moveId`: the same `moveId` is the same presentation. */
val sameBoardPresentation: (BoardUi, BoardUi) -> Boolean = { a, b -> a.moveId == b.moveId }

/** `onHeaderPresented` changes only `lastDelta`. */
val sameHeaderPresentation: (HeaderUi, HeaderUi) -> Boolean = { a, b ->
    a.copy(lastDelta = 0) == b.copy(lastDelta = 0)
}

/** `onOverlayPresented` turns an `Animated` entry into `Restored` and changes nothing else. */
val sameOverlayPresentation: (OverlayUi, OverlayUi) -> Boolean = { a, b ->
    a.restored() == b.restored()
}

/** Equivalence by [same]: the derived state keeps its old instance for an equivalent value. */
private class PresentationPolicy<T>(private val same: (T, T) -> Boolean) :
    SnapshotMutationPolicy<T> {
    override fun equivalent(a: T, b: T): Boolean = same(a, b)
}
