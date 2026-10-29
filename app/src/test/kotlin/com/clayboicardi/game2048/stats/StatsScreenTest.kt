package com.clayboicardi.game2048.stats

import androidx.activity.ComponentDialog
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.statsViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.DIALOG_CONFIRM_TAG
import com.clayboicardi.game2048.ui.components.DIALOG_DISMISS_TAG
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * The Statistics screen (DESIGN_SYSTEM.md §6.9; FEATURES.md F08 items 5–7; D-044, D-198):
 * `StatsScreen` in `GameTheme` over a `StatsViewModel` of a container of its own (real DataStore
 * files in a `TemporaryFolder`, read on real I/O threads and resumed on the main looper).
 */
@RunWith(AndroidJUnit4::class)
class StatsScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F08-AC12 */
    @Test
    fun tabsStartAtCurrentSize() {
        rig(settings = SETTINGS.copy(lastBoardSize = 6)).use { rig ->
            show(rig)

            val lefts = TAB_LABELS.mapIndexed { index, label ->
                val tab = compose.onNodeWithTag(statsTabTag(index))
                tab.assert(hasText(label))
                if (index == statsTabIndex(6)) tab.assertIsSelected() else tab.assertIsNotSelected()
                tab.fetchSemanticsNode().positionInRoot.x
            }
            assertWithMessage("tab order").that(lefts).isInStrictOrder()
            compose.onNodeWithText("No games on 6×6 yet").assertExists()
        }
    }

    /** F08-AC13 */
    @Test
    fun emptyStatePerTab() {
        rig().use { rig ->
            show(rig)
            compose.onNodeWithTag(STATS_HERO_TAG).assertExists()
            compose.onNodeWithTag(statsRowTag(StatsRow.WINS)).assert(hasText("9 (24%)"))

            compose.onNodeWithTag(statsTabTag(statsTabIndex(5))).performClick()
            compose.waitForIdle()

            compose.onNodeWithTag(STATS_EMPTY_TAG).assertExists()
            compose.onNodeWithText("No games on 5×5 yet").assertExists()
            compose.onNodeWithText("Finished and abandoned games appear here.").assertExists()
            compose.onNodeWithTag(STATS_HERO_TAG).assertDoesNotExist()
            for (row in StatsRow.entries) {
                compose.onNodeWithTag(statsRowTag(row)).assertDoesNotExist()
            }
        }
    }

    /** F08-AC14 */
    @Test
    fun resetRequiresTwoConfirmations() {
        val identity = mapOf("4" to 6L, "3" to 5L)
        val game = GoldenFixtures.FIX_MID_4.copy(gameId = 7)
        rig(records = GoldenFixtures.FIX_RECORDS.copy(lastFinalizedGameId = identity), game = game)
            .use { rig ->
                show(rig)
                val gameBytes = rig.gameBytes(BoardSize.DEFAULT)
                val recordsBytes = rig.recordsBytes()
                compose.mainClock.autoAdvance = false

                compose.onNodeWithTag(STATS_RESET_TAG).performClick()
                compose.mainClock.advanceTimeBy(SETTLE_MS)
                compose.onNode(isDialog()).assert(hasAnyText(RESET_TITLE))
                compose.onNodeWithTag(DIALOG_CONFIRM_TAG).assert(hasText("Reset")).performClick()
                compose.mainClock.advanceTimeBy(SETTLE_MS)
                compose.onNode(isDialog()).assert(hasAnyText(CONFIRM_TITLE))
                compose.onNodeWithText(RESET_TITLE).assertDoesNotExist()
                assertWithMessage("records after the first confirmation")
                    .that(rig.container.recordsRepository.submissions)
                    .isEqualTo(0)
                assertThat(rig.recordsBytes()).isEqualTo(recordsBytes)

                compose
                    .onNodeWithTag(DIALOG_CONFIRM_TAG)
                    .assert(hasText("Reset everything"))
                    .performClick()
                compose.mainClock.advanceTimeBy(SETTLE_MS)

                compose.onNodeWithText(RESET_DONE).assertExists()
                compose.onNode(isDialog()).assertDoesNotExist()
                rig.flush()
                assertThat(rig.fileRecords()).isEqualTo(Records(lastFinalizedGameId = identity))
                assertThat(rig.gameBytes(BoardSize.DEFAULT)).isEqualTo(gameBytes)
                compose.mainClock.autoAdvance = true
            }
    }

    /** F08-AC15 */
    @Test
    fun resetCancelAtEitherStepChangesNothing() {
        rig().use { rig ->
            show(rig)
            val before = rig.recordsBytes()

            // Cancel at the first step, then at the second.
            openReset()
            compose.onNodeWithTag(DIALOG_DISMISS_TAG).performClick()
            compose.waitForIdle()
            assertUnchanged(rig, before, "Cancel at step 1")

            openReset()
            compose.onNodeWithTag(DIALOG_CONFIRM_TAG).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assert(hasAnyText(CONFIRM_TITLE))
            compose.onNodeWithTag(DIALOG_DISMISS_TAG).performClick()
            compose.waitForIdle()
            assertUnchanged(rig, before, "Cancel at step 2")

            // Back at the first step, then at the second.
            openReset()
            backOnDialogWindow()
            assertUnchanged(rig, before, "Back at step 1")

            openReset()
            compose.onNodeWithTag(DIALOG_CONFIRM_TAG).performClick()
            compose.waitForIdle()
            backOnDialogWindow()
            assertUnchanged(rig, before, "Back at step 2")
        }
    }

    private fun openReset() {
        compose.onNodeWithTag(STATS_RESET_TAG).performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).assert(hasAnyText(RESET_TITLE))
    }

    /** Back on the latest dialog window, where Android delivers it. */
    private fun backOnDialogWindow() {
        (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    /** No dialog is open, and the records were neither changed nor saved. */
    private fun assertUnchanged(rig: StatsRig, bytes: ByteArray, what: String) {
        compose.onNode(isDialog()).assertDoesNotExist()
        rig.flush()
        assertWithMessage("$what: records saves")
            .that(rig.container.recordsRepository.submissions)
            .isEqualTo(0)
        assertWithMessage("$what: records.json").that(rig.recordsBytes()).isEqualTo(bytes)
        assertWithMessage("$what: records")
            .that(rig.container.recordsRepository.state.value)
            .isEqualTo(GoldenFixtures.FIX_RECORDS)
        compose.onNodeWithTag(STATS_HERO_TAG).assertExists()
    }

    private fun rig(
        settings: Settings = SETTINGS,
        records: Records = GoldenFixtures.FIX_RECORDS,
        game: SavedGame? = null,
    ) = StatsRig(tmp.newFolder(), settings, records, game)

    private fun show(rig: StatsRig) {
        compose.setContent { GameTheme { StatsScreen(rig.vm, onBack = {}) } }
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            rig.vm.ui.value != null
        }
        compose.waitForIdle()
    }

    private companion object {
        /** DESIGN_SYSTEM.md §10.2: onboarding done, the current version seen. */
        val SETTINGS = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE)

        val TAB_LABELS = listOf("All sizes", "3×3", "4×4", "5×5", "6×6", "7×7", "8×8")

        const val RESET_TITLE = "Reset statistics?"
        const val CONFIRM_TITLE = "Reset everything?"
        const val RESET_DONE = "Statistics reset"

        /** Long enough for a dialog or snackbar to appear, shorter than the snackbar's 4 s. */
        const val SETTLE_MS = 500L

        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}

/** A node with a descendant whose text contains [text] (a dialog's title or body). */
private fun hasAnyText(text: String): SemanticsMatcher = hasAnyDescendant(hasText(text))

/**
 * One container over [folder] with [settings], [records] and an optional [game] seeded through its
 * own stores, and the `StatsViewModel` of `statsViewModelFactory()` in a store of its own.
 */
private class StatsRig(
    private val folder: File,
    settings: Settings,
    records: Records,
    game: SavedGame?,
) : AutoCloseable {
    val container =
        AppContainer(
            files = { name -> File(folder, name) },
            versionCode = TEST_VERSION_CODE,
            clock = FakeClock(),
            seedSource = FixedSeedSource(RIG_SEED),
            animatorScale = FakeAnimatorScaleSource(),
        )
    private val store = ViewModelStore()
    val vm: StatsViewModel

    init {
        runBlocking {
            container.seedSettings(settings)
            container.seedRecords(records)
            game?.let { container.seedGame(it) }
        }
        vm = ViewModelProvider(store, container.statsViewModelFactory())[StatsViewModel::class.java]
    }

    fun recordsBytes(): ByteArray = File(folder, DataStoreFiles.RECORDS).readBytes()

    fun gameBytes(size: BoardSize): ByteArray = File(folder, DataStoreFiles.game(size)).readBytes()

    fun fileRecords(): Records =
        AppJson.decodeFromString(
            Records.serializer(),
            File(folder, DataStoreFiles.RECORDS).readText(),
        )

    /** Waits until every submitted write has been attempted. */
    fun flush() = runBlocking {
        container.recordsRepository.awaitIdle()
        container.gameRepository.awaitIdle()
    }

    override fun close() {
        store.clear()
        container.applicationScope.cancel()
    }

    private companion object {
        const val RIG_SEED = 0x2048L
    }
}
