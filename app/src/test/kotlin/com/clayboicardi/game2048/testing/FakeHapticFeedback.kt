package com.clayboicardi.game2048.testing

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Records every haptic with the time it fired (MOTION_SPEC.md §6.2, §8.1): tests provide it through
 * `CompositionLocalProvider(LocalHapticFeedback provides fake)` and read `(type, time − origin)`.
 * [now] is the test clock, normally `{ rule.mainClock.currentTime }`.
 */
class FakeHapticFeedback(private val now: () -> Long) : HapticFeedback {
    /** One performed haptic at test-clock time [atMs]. */
    data class Event(val type: HapticFeedbackType, val atMs: Long)

    private val recorded = mutableListOf<Event>()

    /** Every haptic so far, in firing order. */
    val events: List<Event>
        get() = recorded.toList()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        recorded += Event(hapticFeedbackType, now())
    }

    /** `(type, atMs − originMs)` for every haptic, in firing order. */
    fun relativeTo(originMs: Long): List<Pair<HapticFeedbackType, Long>> = recorded.map {
        it.type to it.atMs - originMs
    }

    /** Forgets everything recorded so far. */
    fun clear() {
        recorded.clear()
    }
}
