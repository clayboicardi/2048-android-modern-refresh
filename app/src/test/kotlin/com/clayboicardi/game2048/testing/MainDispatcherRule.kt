@file:OptIn(ExperimentalCoroutinesApi::class) // D-215: setMain/resetMain are experimental in 1.11.0

package com.clayboicardi.game2048.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sets `Dispatchers.Main` to [dispatcher] for one JVM test (ARCHITECTURE.md §4.10). `runTest` then
 * shares its scheduler, so `viewModelScope`, `DocumentHolder` loads and the container's dispatchers
 * all run on one virtual clock. Use `testScheduler.advanceUntilIdle()` and `runCurrent()`, never
 * the experimental `TestScope` extensions (D-215).
 */
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
