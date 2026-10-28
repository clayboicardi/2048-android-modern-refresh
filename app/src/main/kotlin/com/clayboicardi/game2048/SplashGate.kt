package com.clayboicardi.game2048

/**
 * The splash keep-on-screen condition (D-112, ARCHITECTURE.md §4.12): keep the splash until the
 * game is ready, but never longer than [timeoutMs] after construction. Polled before every frame,
 * so the cap needs no coroutine. [uptimeMs] is `SystemClock::uptimeMillis` in production.
 */
class SplashGate(private val timeoutMs: Long, private val uptimeMs: () -> Long) {
    private val start = uptimeMs()

    /** True while the splash must stay: not [ready] and the cap not yet reached. */
    fun keep(ready: Boolean): Boolean = !ready && uptimeMs() - start < timeoutMs
}
