package com.clayboicardi.game2048.testing

import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.platform.SeedSource
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.ui.theme.AnimatorScaleSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** The `versionCode` every test container reports, this build's (D-130, D-271). */
const val TEST_VERSION_CODE = 10000

/**
 * The container of every JVM test (ARCHITECTURE.md §4.2, QUALITY.md §1.6): real DataStores over
 * files in [folder] (a fresh `TemporaryFolder` per test), [clock], [seedSource] and [animatorScale]
 * (a `FakeAnimatorScaleSource` at scale 1 unless a test passes another), and one
 * `StandardTestDispatcher` on this test's scheduler as the I/O and main dispatcher. Use it with
 * `MainDispatcherRule` so `viewModelScope` shares the scheduler.
 *
 * Its `applicationScope` is a child of `backgroundScope` with its own `SupervisorJob`: [kill] ends
 * one container like a process death, and the next container can then open the same files (one
 * DataStore per file, D-022).
 */
fun TestScope.testAppContainer(
    folder: File,
    clock: FakeClock = FakeClock(),
    seedSource: SeedSource = FixedSeedSource(0x2048),
    animatorScale: AnimatorScaleSource = FakeAnimatorScaleSource(),
): AppContainer {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val job = SupervisorJob(backgroundScope.coroutineContext[Job])
    return AppContainer(
        files = { name -> File(folder, name) },
        versionCode = TEST_VERSION_CODE,
        applicationScope = CoroutineScope(backgroundScope.coroutineContext + job + dispatcher),
        ioDispatcher = dispatcher,
        mainDispatcher = dispatcher,
        clock = clock,
        seedSource = seedSource,
        animatorScale = animatorScale,
    )
}

/**
 * Ends [container] like a process death (QUALITY.md §1.11): cancels its `applicationScope` and runs
 * the scheduler until the cancellation has completed, which closes its DataStores and releases
 * their files. The scope is background work, which `advanceUntilIdle()` does not run, so
 * `runCurrent()` follows it (D-220). Writes still queued are lost, as in a kill; call the
 * repositories' `awaitIdle()` first when the test needs them on disk.
 */
fun TestScope.kill(container: AppContainer) {
    container.applicationScope.cancel()
    testScheduler.advanceUntilIdle()
    testScheduler.runCurrent()
}

/**
 * Writes [game] into its size's file through the container's own store, bypassing the repository
 * (so before the repository is used). Encodes with `GameDocumentMapper`.
 */
suspend fun AppContainer.seedGame(game: SavedGame) {
    gameStore(game.state.size).updateData { GameDocumentMapper.toDocument(game) }
}

/** Writes [records] into `records.json` through the container's own store, before first use. */
suspend fun AppContainer.seedRecords(records: Records) {
    recordsStore.updateData { records }
}

/** Writes [settings] into `settings.json` through the container's own store, before first use. */
suspend fun AppContainer.seedSettings(settings: Settings) {
    settingsStore.updateData { settings }
}
