package com.clayboicardi.game2048.data

import com.clayboicardi.game2048.data.settings.MoveTiming
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.kill
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.lang.reflect.Modifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `SettingsRepository` over a real `settings.json` (ARCHITECTURE.md §4.5–§4.6; QUALITY.md §1.6;
 * FEATURES.md §2.1): defaults, coercion of `lastBoardSize`, round trips across containers, and
 * updates queued before the load.
 */
class SettingsRepositoryTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    /** F02-AC11 */
    @Test
    fun lastBoardSizeOutOfRangeCoercedToFour() = runTest {
        for (stored in listOf(9, 2, 0, -1)) {
            val folder = tmp.newFolder()
            val seeding = testAppContainer(folder)
            seeding.seedSettings(Settings(lastBoardSize = stored, themeMode = ThemeMode.DARK))
            kill(seeding)

            val c = testAppContainer(folder)
            val repo = c.settingsRepository
            assertThat(repo.awaitLoaded().lastBoardSize).isEqualTo(4)
            assertThat(repo.loaded.value).isTrue()
            assertThat(repo.state.value.lastBoardSize).isEqualTo(4)
            assertThat(repo.state.value.themeMode).isEqualTo(ThemeMode.DARK)
            // A transform sees the coerced value, and its result is coerced too.
            var seen = -100
            repo.update { s ->
                seen = s.lastBoardSize
                s.copy(lastBoardSize = 9)
            }
            assertThat(seen).isEqualTo(4)
            assertThat(repo.state.value.lastBoardSize).isEqualTo(4)
            kill(c)
        }
        // Every value in 3..8 is kept as stored.
        for (stored in 3..8) {
            val folder = tmp.newFolder()
            val seeding = testAppContainer(folder)
            seeding.seedSettings(Settings(lastBoardSize = stored))
            kill(seeding)
            val c = testAppContainer(folder)
            assertThat(c.settingsRepository.awaitLoaded().lastBoardSize).isEqualTo(stored)
            kill(c)
        }
    }

    /** F11-AC10 */
    @Test
    fun defaultsMatchCatalog() {
        val encoded = AppJson.encodeToJsonElement(Settings.serializer(), Settings()).jsonObject

        // FEATURES.md §2.1, in row order, with each row's default.
        val catalog =
            AppJson.parseToJsonElement(
                    """
                    {
                      "schemaVersion": 1,
                      "themeMode": "SYSTEM",
                      "palette": "CLASSIC",
                      "highContrast": false,
                      "dynamicChrome": false,
                      "motion": "FULL",
                      "haptics": true,
                      "moveTiming": "DURING_SWIPE",
                      "swipeSensitivity": "MEDIUM",
                      "immersive": false,
                      "keepScreenOn": false,
                      "lastBoardSize": 4,
                      "lastSeenVersionCode": 0,
                      "onboardingDone": false
                    }
                    """,
                )
                .jsonObject
        assertThat(encoded.keys).hasSize(14)
        assertThat(encoded.keys).containsExactlyElementsIn(catalog.keys).inOrder()
        assertThat(encoded).isEqualTo(catalog)
        assertThat(AppJson.decodeFromString(Settings.serializer(), "{}")).isEqualTo(Settings())
    }

    /** F11-AC11 */
    @Test
    fun everyFieldRoundTrips() = runTest {
        val folder = tmp.newFolder()
        val a = testAppContainer(folder)
        assertThat(a.settingsRepository.awaitLoaded()).isEqualTo(Settings())
        // Every setting changed; schemaVersion is format metadata with only the value 1 in v1.
        a.settingsRepository.update { V1Fixtures.SETTINGS }
        assertThat(a.settingsRepository.state.value).isEqualTo(V1Fixtures.SETTINGS)
        a.settingsRepository.awaitIdle()
        kill(a)

        val b = testAppContainer(folder)
        val read = b.settingsRepository.awaitLoaded()
        assertThat(read).isEqualTo(V1Fixtures.SETTINGS)
        for (field in Settings::class.java.declaredFields.filterNot { it.isSynthetic }) {
            if (field.name == "schemaVersion" || Modifier.isStatic(field.modifiers)) {
                continue
            }
            field.isAccessible = true
            assertThat(field.get(read)).isNotEqualTo(field.get(Settings()))
        }
        kill(b)
    }

    /** QUALITY.md §1.6: a missing file reads as the defaults and is not created by reading. */
    @Test
    fun missingFileReadsDefaults() = runTest {
        val folder = tmp.newFolder()
        val c = testAppContainer(folder)
        assertThat(c.settingsRepository.awaitLoaded()).isEqualTo(Settings())
        testScheduler.advanceUntilIdle()
        assertThat(File(folder, DataStoreFiles.SETTINGS).exists()).isFalse()
        kill(c)
    }

    /** QUALITY.md §1.6: updates made before the load are applied after it, in call order. */
    @Test
    fun updatesBeforeLoadAppliedAfterLoadInCallOrder() = runTest {
        val folder = tmp.newFolder()
        val seeding = testAppContainer(folder)
        seeding.seedSettings(Settings(themeMode = ThemeMode.DARK, lastBoardSize = 5))
        kill(seeding)

        val c = testAppContainer(folder)
        val repo = c.settingsRepository
        repo.update { it.copy(lastBoardSize = it.lastBoardSize + 1, haptics = false) }
        repo.update { it.copy(lastBoardSize = it.lastBoardSize + 1) }
        repo.update { it.copy(moveTiming = MoveTiming.ON_RELEASE) }
        // Nothing applies before the file has been read.
        assertThat(repo.loaded.value).isFalse()
        assertThat(repo.state.value).isEqualTo(Settings())

        val expected =
            Settings(
                themeMode = ThemeMode.DARK,
                lastBoardSize = 7,
                haptics = false,
                moveTiming = MoveTiming.ON_RELEASE,
            )
        assertThat(repo.awaitLoaded()).isEqualTo(expected)
        repo.awaitIdle()
        kill(c)

        // The merged value was written.
        val again = testAppContainer(folder)
        assertThat(again.settingsRepository.awaitLoaded()).isEqualTo(expected)
        kill(again)
    }
}
