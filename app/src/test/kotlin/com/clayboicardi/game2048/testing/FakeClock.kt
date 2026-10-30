package com.clayboicardi.game2048.testing

import com.clayboicardi.game2048.data.platform.MonotonicClock

/** A `MonotonicClock` that moves only when the test says so (QUALITY.md §1.1: no wall clock). */
class FakeClock(var nowMs: Long = 0) : MonotonicClock {
    override fun nowMs(): Long = nowMs

    /** Moves the clock forward by [ms] (>= 0). */
    fun advance(ms: Long) {
        require(ms >= 0) { "A monotonic clock never goes back ($ms ms)" }
        nowMs += ms
    }
}
