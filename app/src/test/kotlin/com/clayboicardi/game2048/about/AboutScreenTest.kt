package com.clayboicardi.game2048.about

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.ABOUT_LICENSES_TAG
import com.clayboicardi.game2048.AboutKey
import com.clayboicardi.game2048.LicensesKey
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.MORE_TAG
import com.clayboicardi.game2048.menuItemTag
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.viewUrlIntent
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The About and Licenses screens (DESIGN_SYSTEM.md §6.11, §6.12; FEATURES.md F25, F26, F27 item 5;
 * D-009, D-122–D-124, D-134). Most tests compose the screen alone in `GameTheme` over an
 * `AboutViewModel` of the Robolectric application's resources and an unconfined test dispatcher;
 * the two that need the back stack or the stored settings compose `App` over a container of their
 * own, with real DataStore files in a `TemporaryFolder`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    private val store = ViewModelStore()

    /** The screens' I/O dispatcher, run by [awaitRead]. */
    private val io = StandardTestDispatcher()

    /** The `AboutViewModel` of [showAbout]. */
    private var shown: AboutViewModel? = null

    @After
    fun clearViewModels() {
        store.clear()
    }

    /** F25-AC1 */
    @Test
    fun versionLine() {
        showAbout()

        compose.onNodeWithTag(ABOUT_VERSION_TAG).assertTextEquals("Version 3.0.0 (30000)")
    }

    /** F25-AC2 */
    @Test
    fun whatsNewHistoryAlwaysShown() {
        AppRig(tmp.newFolder(), Settings(onboardingDone = true, lastSeenVersionCode = 30000)).use {
            rig ->
            rig.show()
            rig.openAboutFromMenu()
            val lines = WHATS_NEW_30000.map(app::getString)

            aboutList().performScrollToNode(hasText(lines.last()))

            assertWithMessage("the What's new heading")
                .that(topOf(compose.onNode(isHeading() and hasText("What's new"))))
                .isLessThan(topOf(compose.onNodeWithText("3.0.0")))
            val tops = lines.map { topOf(compose.onNodeWithText(it)) }
            assertThat(topOf(compose.onNodeWithText("3.0.0"))).isLessThan(tops.first())
            assertWithMessage("the five bullets, in order").that(tops).isInStrictOrder()
        }
    }

    /** F25-AC3 */
    @Test
    fun sourceLinkOpensRepository() {
        shadowOf(app).checkActivities(true)
        val browser = ComponentName(BROWSER_PACKAGE, "$BROWSER_PACKAGE.Browser")
        val packages = shadowOf(app.packageManager)
        packages.addActivityIfNotPresent(browser)
        packages.addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
        assertWithMessage("a browser resolves the link")
            .that(app.packageManager.resolveActivity(viewUrlIntent(URL), 0))
            .isNotNull()
        showAbout()

        aboutList().performScrollToNode(hasTestTag(ABOUT_SOURCE_TAG))
        val row = compose.onNodeWithTag(ABOUT_SOURCE_TAG)
        assertThat(clickLabel(row)).isEqualTo("Open")
        assertThat(textsOf(ABOUT_SOURCE_TAG))
            .containsExactly(
                "Source code",
                "github.com/clayboicardi/2048-android-modern-refresh",
            )
        row.performClick()
        compose.waitForIdle()

        val started = checkNotNull(shadowOf(app).nextStartedActivity) { "no activity started" }
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.dataString).isEqualTo(URL)
        compose.onNodeWithText(LINK_FAILED).assertDoesNotExist()
    }

    /** F25-AC4 */
    @Test
    fun sourceLinkWithoutBrowserShowsSnackbar() {
        shadowOf(app).checkActivities(true)
        showAbout()
        aboutList().performScrollToNode(hasTestTag(ABOUT_SOURCE_TAG))
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag(ABOUT_SOURCE_TAG).performClick()
        compose.mainClock.advanceTimeBy(SNACKBAR_SHOWN_MS)

        compose.onNodeWithText(LINK_FAILED).assertExists()
        assertThat(shadowOf(app).nextStartedActivity).isNull()
    }

    /** F25-AC5 */
    @Test
    fun licensesRowOpensLicenses() {
        AppRig(tmp.newFolder()).use { rig ->
            rig.show()
            rig.openAboutFromMenu()
            aboutList().performScrollToNode(hasTestTag(ABOUT_LICENSES_TAG))
            val row = compose.onNodeWithTag(ABOUT_LICENSES_TAG)
            assertThat(clickLabel(row)).isEqualTo("Open")

            row.performClick()
            compose.waitForIdle()

            compose.onNodeWithTag(screenTag(LicensesKey)).assertExists()
            compose.onNodeWithTag(screenTag(AboutKey)).assertDoesNotExist()
        }
    }

    /** F25-AC6 */
    @Test
    fun creditsRows() {
        showAbout()

        val rows =
            (0 until EXPECTED_CREDITS.size).map { index ->
                aboutList().performScrollToNode(hasTestTag(aboutCreditTag(index)))
                textsOf(aboutCreditTag(index)).joinToString("")
            }

        assertThat(rows[0]).isEqualTo(EXPECTED_CREDITS[0])
        assertThat(rows[1]).isEqualTo(EXPECTED_CREDITS[1])
        assertThat(rows[2]).startsWith(EXPECTED_CREDITS[2])
        assertThat(rows[3]).isEqualTo(EXPECTED_CREDITS[3])
        val contributors = rows[2].removePrefix(EXPECTED_CREDITS[2]).split(", ")
        assertWithMessage("the Contributors row (legacy-credits.md §1, D-209, D-213)")
            .that(contributors)
            .containsExactlyElementsIn(LEGACY_CONTRIBUTORS)
            .inOrder()
        assertThat(aboutHistory().credits).hasSize(EXPECTED_CREDITS.size)
    }

    /** F25-AC7 */
    @Test
    fun legacyHistoryListsNineReleases() {
        showAbout()

        val titles =
            LEGACY_RELEASES.indices.map { index ->
                aboutList().performScrollToNode(hasTestTag(aboutReleaseTag(index)))
                textsOf(aboutReleaseTag(index)).single()
            }

        assertThat(titles).containsExactlyElementsIn(LEGACY_RELEASES).inOrder()
        val history = aboutHistory()
        assertThat(history.releases.map { "${it.version} (${it.versionCode})" })
            .containsExactlyElementsIn(LEGACY_RELEASES)
            .inOrder()
        assertWithMessage("2.1's change, without the legacy strike-through markup")
            .that(history.releases[1].changes.single())
            .startsWith("Bad build. This version of the app crashed on startup")
        // The last release's change is the last row of the screen.
        aboutList().performScrollToNode(hasText(history.releases.last().changes.single()))
    }

    /** F26-AC3 */
    @Test
    fun licensesGroupedMitOflApache() {
        showLicenses()
        compose
            .onNodeWithTag(LICENSES_LIST_TAG)
            .performScrollToNode(isHeading() and hasText("Apache License 2.0"))

        val headers =
            listOf("MIT License", "SIL Open Font License 1.1", "Apache License 2.0").map {
                topOf(compose.onNode(isHeading() and hasText(it)))
            }

        assertWithMessage("group headers, in order").that(headers).isInStrictOrder()
        for (holder in listOf("Gabriele Cirulli", "Paul Sarbinowski")) {
            val top = topOf(compose.onNodeWithText("Copyright (c) 2014 $holder"))
            assertWithMessage("$holder's 2014 copyright line in the MIT group")
                .that(top)
                .isIn(Range.open(headers[0], headers[1]))
        }
    }

    /** F26-AC4 */
    @Test
    fun licenseTextToggles() {
        showLicenses()
        val toggle = compose.onNodeWithTag(licenseToggleTag(LicenseCatalog.MIT))
        assertThat(textsOf(licenseToggleTag(LicenseCatalog.MIT)))
            .containsExactly("Show license text")
        compose.onNodeWithTag(licenseTextTag(LicenseCatalog.MIT)).assertDoesNotExist()

        toggle.performClick()
        compose.waitForIdle()

        val mit =
            app.resources.openRawResource(R.raw.license_mit).bufferedReader().use { it.readText() }
        assertThat(mit).contains("Permission is hereby granted, free of charge")
        compose.onNodeWithTag(licenseTextTag(LicenseCatalog.MIT)).assertTextEquals(mit)
        assertThat(textsOf(licenseToggleTag(LicenseCatalog.MIT)))
            .containsExactly("Hide license text")

        toggle.performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(licenseTextTag(LicenseCatalog.MIT)).assertDoesNotExist()
        assertThat(textsOf(licenseToggleTag(LicenseCatalog.MIT)))
            .containsExactly("Show license text")
    }

    /** F27-AC4 */
    @Test
    fun privacyTextMatchesPolicy() {
        showAbout()

        aboutList().performScrollToNode(hasTestTag(ABOUT_PRIVACY_TAG))

        compose.onNodeWithTag(ABOUT_PRIVACY_TAG).assertTextEquals(D123_PRIVACY)
        assertThat(topOf(compose.onNode(isHeading() and hasText("Privacy"))))
            .isLessThan(topOf(compose.onNodeWithTag(ABOUT_PRIVACY_TAG)))
    }

    /**
     * DESIGN_SYSTEM.md §6.13, D-009: the What's new sheet shows its title, this build's version and
     * the bullets of its entries; "Got it" hides it, then calls `onDismissRequest` once.
     */
    @Test
    fun whatsNewSheetListsEntriesAndGotItDismisses() {
        val entries = decideWhatsNew(29999, 30000, CHANGELOG).entries.toImmutableList()
        var dismissed = 0
        var open by mutableStateOf(true)
        compose.setContent {
            GameTheme(darkTheme = false) {
                if (open) {
                    WhatsNewSheet(
                        entries = entries,
                        onDismissRequest = {
                            dismissed++
                            open = false
                        },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText("What's new")).assertExists()
        compose.onNodeWithText("Version 3.0.0").assertExists()
        for (line in WHATS_NEW_30000) compose.onNodeWithText(app.getString(line)).assertExists()

        compose.onNodeWithTag(WHATS_NEW_GOT_IT_TAG).performClick()
        compose.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        compose.onNodeWithTag(WHATS_NEW_SHEET_TAG).assertDoesNotExist()
    }

    private fun aboutViewModel(): AboutViewModel {
        val factory = viewModelFactory {
            initializer { AboutViewModel(app.resources, io) }
        }
        return ViewModelProvider.create(store, factory)[AboutViewModel::class]
    }

    /** Composes `AboutScreen` alone, in the light theme, and waits until its history is read. */
    private fun showAbout() {
        val vm = aboutViewModel().also { shown = it }
        compose.setContent {
            GameTheme(darkTheme = false) {
                AboutScreen(viewModel = vm, onBack = {}, onOpenLicenses = {})
            }
        }
        awaitRead { vm.history.value != null }
    }

    /** Composes `LicensesScreen` alone under [motion] and waits until its groups are read. */
    private fun showLicenses(motion: MotionPreference = MotionPreference.FULL) {
        val vm = aboutViewModel()
        compose.setContent {
            GameTheme(darkTheme = false, motion = motion) {
                LicensesScreen(viewModel = vm, onBack = {})
            }
        }
        awaitRead { vm.licenses.value != null }
    }

    /** Runs the I/O dispatcher and the main looper until [read], then waits for the screen. */
    private fun awaitRead(read: () -> Boolean) {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            io.scheduler.advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            read()
        }
        compose.waitForIdle()
    }

    private fun aboutHistory(): LegacyHistory = checkNotNull(checkNotNull(shown).history.value)

    private fun aboutList(): SemanticsNodeInteraction = compose.onNodeWithTag(ABOUT_LIST_TAG)

    private fun topOf(node: SemanticsNodeInteraction): Float =
        node.fetchSemanticsNode().boundsInRoot.top

    /** The label of [node]'s click action (the row's `onClickLabel`). */
    private fun clickLabel(node: SemanticsNodeInteraction): String? =
        node.fetchSemanticsNode().config[SemanticsActions.OnClick].label

    /** Every text under the node tagged [tag], in the unmerged tree, in tree order. */
    private fun textsOf(tag: String): List<String> =
        texts(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
            node.children.flatMap(::texts)

    /**
     * `App` over a container of its own: real DataStore files in [folder] seeded with [settings], a
     * `FakeClock`, a fixed seed and the fake animator scale; About opens through the game menu.
     */
    private inner class AppRig(
        folder: File,
        settings: Settings =
            Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE),
    ) : AutoCloseable {
        val container =
            AppContainer(
                files = { name -> File(folder, name) },
                versionCode = TEST_VERSION_CODE,
                clock = FakeClock(),
                seedSource = FixedSeedSource(RIG_SEED),
                animatorScale = FakeAnimatorScaleSource(),
            )
        private val rigStore = ViewModelStore()
        val vm: GameViewModel

        init {
            runBlocking { container.seedSettings(settings) }
            vm =
                ViewModelProvider.create(rigStore, container.gameViewModelFactory())[
                        GameViewModel::class]
        }

        /** Composes `App` and waits for the game's load (real I/O threads, the main looper). */
        fun show() {
            compose.setContent { TestApp(container, vm) }
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            compose.waitForIdle()
        }

        /** Opens About from the game menu and waits until its history is shown. */
        fun openAboutFromMenu() {
            compose.onNodeWithTag(MORE_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(menuItemTag(AboutKey)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(screenTag(AboutKey)).assertExists()
        }

        override fun close() {
            rigStore.clear()
            container.applicationScope.cancel()
        }
    }

    private companion object {
        const val URL = "https://github.com/clayboicardi/2048-android-modern-refresh"
        const val BROWSER_PACKAGE = "org.example.browser"
        const val LINK_FAILED = "No app can open this link"

        /** The snackbar's enter animation is well within this (DESIGN_SYSTEM.md §7.6). */
        const val SNACKBAR_SHOWN_MS = 1_000L

        /** A bound on the real-thread loads, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
        const val RIG_SEED = 0x2048L

        val WHATS_NEW_30000 =
            listOf(
                R.string.whatsnew_30000_1,
                R.string.whatsnew_30000_2,
                R.string.whatsnew_30000_3,
                R.string.whatsnew_30000_4,
                R.string.whatsnew_30000_5,
            )

        /** D-123, verbatim. */
        const val D123_PRIVACY =
            "This app collects nothing. It has no network access, no accounts, no ads and no " +
                "analytics. Game data stays on your device. Android's own backup may copy it to " +
                "your Google account if you enabled device backup."

        /** DESIGN_SYSTEM.md §6.11: the four credits rows (the third is a prefix). */
        val EXPECTED_CREDITS =
            listOf(
                "Gabriele Cirulli — original game",
                "Paul Sarbinowski (uberspot) — Android port",
                "Contributors — ",
                "clayboicardi — native rewrite",
            )

        /**
         * `docs/modernization/legacy-credits.md` §1's deduplicated list (32 names, with gurshabad,
         * D-209, and pierre, D-213), in its order.
         */
        val LEGACY_CONTRIBUTORS =
            listOf(
                "alexara",
                "amoseui",
                "Balázs Meskó",
                "BValeo",
                "Caio Melo",
                "dark159123",
                "Diogo Pereira",
                "djura-san",
                "fraggerfox",
                "gurshabad",
                "Hans-Christoph Steiner",
                "Igor A",
                "Laura Arjona Reina",
                "lishoujun",
                "Matej",
                "Mauro Scomparin",
                "Mongata",
                "Oluwaleke Fakorede",
                "Paul Verest",
                "pierre",
                "Poussinou",
                "princessruthie",
                "Robin van der Vliet",
                "RyuzakiKK",
                "shubham1701",
                "SMahdiFaghih",
                "Steven Lee",
                "TacoTheDank",
                "TeknoCepni",
                "Vladyslav Pohrebniakov",
                "xin",
                "zygimantus",
            )

        /** FEATURES.md F25 item 7: the nine legacy releases, newest first. */
        val LEGACY_RELEASES =
            listOf(
                "2.2 (25)",
                "2.1 (24)",
                "2.08 (23)",
                "2.06 (22)",
                "2.05 (21)",
                "2.0 (20)",
                "1.96 (19)",
                "1.95 (18)",
                "1.91 (18)",
            )
    }
}
