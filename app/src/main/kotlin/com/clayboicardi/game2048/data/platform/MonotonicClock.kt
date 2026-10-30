package com.clayboicardi.game2048.data.platform

import android.os.SystemClock

/** Monotonic milliseconds for play time (D-022, D-043). Tests use `testing/FakeClock`. */
fun interface MonotonicClock {
    fun nowMs(): Long
}

/** `SystemClock.elapsedRealtime()`: monotonic, includes deep sleep, never a wall clock. */
object SystemMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}
