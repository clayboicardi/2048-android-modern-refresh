package com.clayboicardi.game2048.about

import androidx.lifecycle.ViewModelStore
import com.clayboicardi.game2048.AppViewModel
import com.clayboicardi.game2048.BuildConfig
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.testing.testAppContainer
import com.clayboicardi.game2048.testing.testAppViewModel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The What's new policy (ARCHITECTURE.md §4.13; FEATURES.md F24; D-009): `decideWhatsNew` as a pure
 * function, and `AppViewModel`'s one evaluation over real DataStore files, counted with the
 * settings repository's `submissions` (D-221). The scheduler is the main dispatcher's, run with
 * `runCurrent()` (D-215).
 */
class WhatsNewTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F24-AC1 */
    @Test
    fun freshInstallStoresVersionAndShowsNothing() = runTest {
        val decision = decideWhatsNew(lastSeen = 0, current = 30000, changelog = CHANGELOG)

        assertThat(decision.entries).isEmpty()
        assertThat(decision.storeVersionCode).isEqualTo(30000)

        // A fresh install: no settings file, so `lastSeenVersionCode` is the default 0.
        appViewModel(seed = null) { container, vm ->
            assertThat(vm.whatsNew.value).isNull()
            assertThat(container.settingsRepository.state.value.lastSeenVersionCode)
                .isEqualTo(TEST_VERSION_CODE)
            assertThat(container.settingsRepository.submissions).isEqualTo(1L)
            container.settingsRepository.awaitIdle()
            assertThat(container.fileSettings().lastSeenVersionCode).isEqualTo(TEST_VERSION_CODE)
        }
    }

    /** F24-AC2 */
    @Test
    fun updateShowsEntriesNewerThanLastSeen() = runTest {
        val decision = decideWhatsNew(lastSeen = 29999, current = 30000, changelog = CHANGELOG)

        assertThat(decision.entries)
            .containsExactly(
                ChangelogEntry(
                    30000,
                    persistentListOf(
                        R.string.whatsnew_30000_1,
                        R.string.whatsnew_30000_2,
                        R.string.whatsnew_30000_3,
                        R.string.whatsnew_30000_4,
                        R.string.whatsnew_30000_5,
                    ),
                ),
            )
        assertThat(decision.storeVersionCode).isEqualTo(30000)

        appViewModel(seed = SEEN.copy(lastSeenVersionCode = 29999)) { container, vm ->
            assertThat(vm.whatsNew.value).containsExactlyElementsIn(decision.entries).inOrder()
            assertThat(container.settingsRepository.state.value.lastSeenVersionCode)
                .isEqualTo(30000)

            vm.onWhatsNewDismissed()

            assertThat(vm.whatsNew.value).isNull()
        }
    }

    /** F24-AC3 */
    @Test
    fun entriesCoverExactlyTheSkippedRange() {
        val changelog = mapOf(20100 to listOf(LINE_A), 30000 to listOf(LINE_B))

        val fromOld = decideWhatsNew(lastSeen = 20000, current = 30000, changelog = changelog)
        val fromMiddle = decideWhatsNew(lastSeen = 20100, current = 30000, changelog = changelog)

        assertThat(fromOld.entries.map { it.versionCode }).containsExactly(20100, 30000).inOrder()
        assertThat(fromOld.entries.flatMap { it.lines }).containsExactly(LINE_A, LINE_B).inOrder()
        assertThat(fromOld.storeVersionCode).isEqualTo(30000)
        assertThat(fromMiddle.entries.map { it.versionCode }).containsExactly(30000)
        assertThat(fromMiddle.entries.flatMap { it.lines }).containsExactly(LINE_B)
        assertThat(fromMiddle.storeVersionCode).isEqualTo(30000)
        // An entry newer than the running version is never shown.
        val ahead = mapOf(30000 to listOf(LINE_A), 30100 to listOf(LINE_B))
        assertThat(decideWhatsNew(lastSeen = 20000, current = 30000, changelog = ahead).entries)
            .containsExactly(ChangelogEntry(30000, persistentListOf(LINE_A)))
    }

    /** F24-AC4 */
    @Test
    fun sameVersionShowsNothing() = runTest {
        val decision = decideWhatsNew(lastSeen = 30000, current = 30000, changelog = CHANGELOG)

        assertThat(decision.entries).isEmpty()
        assertThat(decision.storeVersionCode).isNull()

        appViewModel(seed = SEEN) { container, vm ->
            assertThat(vm.whatsNew.value).isNull()
            assertThat(container.settingsRepository.submissions).isEqualTo(0L)
        }
    }

    /** F24-AC5 */
    @Test
    fun downgradeShowsNothingAndKeepsLastSeen() = runTest {
        val decision = decideWhatsNew(lastSeen = 40000, current = 30000, changelog = CHANGELOG)

        assertThat(decision.entries).isEmpty()
        assertThat(decision.storeVersionCode).isNull()

        appViewModel(seed = SEEN.copy(lastSeenVersionCode = 40000)) { container, vm ->
            assertThat(vm.whatsNew.value).isNull()
            assertThat(container.settingsRepository.submissions).isEqualTo(0L)
            assertThat(container.settingsRepository.state.value.lastSeenVersionCode)
                .isEqualTo(40000)
            assertThat(container.fileSettings().lastSeenVersionCode).isEqualTo(40000)
        }
    }

    /** F24-AC6 */
    @Test
    fun versionStoredBeforeSheetIsShown() = runTest {
        val container = testAppContainer(tmp.newFolder())
        container.seedSettings(SEEN.copy(lastSeenVersionCode = 29999))
        val store = ViewModelStore()
        try {
            val vm = testAppViewModel(container, store)
            val settings = container.settingsRepository
            // What the settings held when `whatsNew` first carried entries, seen by a collector
            // that runs at once on each emission.
            var atEmission: Pair<Long, Int>? = null
            @Suppress("InjectDispatcher") // the probe collector must run at once on each emission
            val atOnce = Dispatchers.Unconfined
            backgroundScope.launch(atOnce) {
                vm.whatsNew.filterNotNull().first()
                atEmission = settings.submissions to settings.state.value.lastSeenVersionCode
            }
            assertThat(vm.whatsNew.value).isNull()

            testScheduler.runCurrent()

            assertWithMessage("submitted saves and lastSeenVersionCode at the first emission")
                .that(atEmission)
                .isEqualTo(1L to 30000)
            assertThat(vm.whatsNew.value).isNotEmpty()
            settings.awaitIdle()
            assertThat(container.fileSettings().lastSeenVersionCode).isEqualTo(30000)
        } finally {
            store.clear()
        }
    }

    /**
     * D-130: the About screen's version titles follow the versionCode scheme `MAJOR·10000 +
     * MINOR·100 + PATCH`, and the shipped entry's name is the build's.
     */
    @Test
    fun versionNameFollowsTheVersionScheme() {
        assertThat(versionNameOf(BuildConfig.VERSION_CODE)).isEqualTo(BuildConfig.VERSION_NAME)
        assertThat(versionNameOf(30000)).isEqualTo("3.0.0")
        assertThat(versionNameOf(30102)).isEqualTo("3.1.2")
        assertThat(versionNameOf(101112)).isEqualTo("10.11.12")
        assertThat(CHANGELOG.keys).contains(BuildConfig.VERSION_CODE)
    }

    /**
     * An `AppViewModel` over a fresh container whose settings file holds [seed] (none when `null`),
     * after its one evaluation has run; [block] runs on it, then the ViewModel is cleared.
     */
    private suspend fun TestScope.appViewModel(
        seed: Settings?,
        block: suspend (AppContainer, AppViewModel) -> Unit,
    ) {
        val container = testAppContainer(tmp.newFolder())
        seed?.let { container.seedSettings(it) }
        val store = ViewModelStore()
        try {
            val vm = testAppViewModel(container, store)
            testScheduler.runCurrent()
            block(container, vm)
        } finally {
            store.clear()
        }
    }

    /** `settings.json` as it is on disk (the store's own read, after the writes so far). */
    private suspend fun AppContainer.fileSettings(): Settings = settingsStore.data.first()

    private companion object {
        /** DESIGN_SYSTEM.md §10.2: onboarding done, the current version seen. */
        val SEEN = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE)

        /** Stand-ins for two string resources of a test changelog. */
        const val LINE_A = 1
        const val LINE_B = 2
    }
}
