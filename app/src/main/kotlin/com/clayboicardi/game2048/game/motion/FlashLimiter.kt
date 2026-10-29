package com.clayboicardi.game2048.game.motion

import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.ui.theme.GameMotion

/**
 * At most `GameMotion.Move.FLASH_LIMIT_COUNT` (3) sheen or glow pulses per rolling
 * `FLASH_LIMIT_WINDOW_MS` (1000 ms) of real time across the board (D-063, D-183; MOTION_SPEC.md
 * §3.3). A merged tile that would get a sheen and/or a glow pulse calls [tryAcquire] once; on false
 * it still pops but gets neither. The window is [clock] time, never scaled by the animator scale
 * (MOTION_SPEC.md §2.3 T4). Main thread only; one per `BoardView`.
 *
 * The window is half-open: at time t a flash is allowed when fewer than 3 were acquired after t −
 * 1000 ms. Only the last 3 acquisition times are kept, so the limiter never grows.
 */
class FlashLimiter(private val clock: MonotonicClock) {
    /** The last acquisition times, a ring buffer whose oldest entry is at [oldest]. */
    private val times = LongArray(GameMotion.Move.FLASH_LIMIT_COUNT)
    private var oldest = 0
    private var count = 0

    /** True, and counted, when a flash may start now; false while 3 started in the last 1000 ms. */
    fun tryAcquire(): Boolean {
        val now = clock.nowMs()
        val full = count == times.size
        val allowed = !full || now - times[oldest] >= GameMotion.Move.FLASH_LIMIT_WINDOW_MS
        if (allowed) {
            if (full) {
                times[oldest] = now
                oldest = (oldest + 1) % times.size
            } else {
                times[(oldest + count) % times.size] = now
                count++
            }
        }
        return allowed
    }
}
