package com.clayboicardi.game2048.screenshots

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.ABOUT_LICENSES_TAG
import com.clayboicardi.game2048.AboutKey
import com.clayboicardi.game2048.HowToPlayKey
import com.clayboicardi.game2048.LicensesKey
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.StatsKey
import com.clayboicardi.game2048.about.ABOUT_VERSION_TAG
import com.clayboicardi.game2048.about.HOW_TO_PLAY_CONTENT_TAG
import com.clayboicardi.game2048.about.LicenseCatalog
import com.clayboicardi.game2048.about.WHATS_NEW_GOT_IT_TAG
import com.clayboicardi.game2048.about.WHATS_NEW_SHEET_TAG
import com.clayboicardi.game2048.about.aboutCreditTag
import com.clayboicardi.game2048.about.licenseTextTag
import com.clayboicardi.game2048.about.licenseToggleTag
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.settings.SETTINGS_LIST_TAG
import com.clayboicardi.game2048.stats.STATS_HERO_TAG
import com.clayboicardi.game2048.stats.STATS_TABS
import com.clayboicardi.game2048.stats.statsTabTag
import com.clayboicardi.game2048.testing.assertAccessible
import com.clayboicardi.game2048.testing.assertDialogTouchTargets
import com.clayboicardi.game2048.testing.assertTouchTargets
import com.clayboicardi.game2048.testing.atfRule
import com.clayboicardi.game2048.testing.captureGolden
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sub-screen, font-scale and What's new goldens (DESIGN_SYSTEM.md §10.2, §10.4 rows 17–22,
 * 27–28 and 44; D-147, D-204) at SDK 36 on the Pixel 7 qualifier, under ATF and the touch-target
 * gate (D-085, D-205). Each test composes `App` over a container of its own with `FIX_MID_4` and
 * the §10.2 settings (Motion Off, so every navigation lands on its next frame), opens its screen
 * from the game menu as a player would, and captures the main window, or the sheet's own window.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreensScreenshotTest {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(atfRule(compose))
    @get:Rule val tmp = TemporaryFolder()

    /** F11-AC12 */
    @Test
    fun settingsGoldens() {
        onScreen(SettingsKey) {
            compose.onNodeWithTag(SETTINGS_LIST_TAG).assertExists()
            capture("settings_classic-light")
        }
    }

    /** F11-AC12 */
    @Test
    @Config(qualifiers = "+night")
    fun settingsDarkGolden() {
        // DESIGN_SYSTEM.md §10.2 and §10.4 row 18: themeMode DARK and the night qualifier.
        onScreen(SettingsKey, settings = { it.copy(themeMode = ThemeMode.DARK) }) {
            compose.onNodeWithTag(SETTINGS_LIST_TAG).assertExists()
            capture("settings_classic-dark")
        }
    }

    /** F08-AC17 */
    @Test
    fun statisticsGolden() {
        onScreen(StatsKey, records = true) {
            // StatsViewModel's ui is null until the records and every size's game are read.
            compose.awaitLoaded { exists(STATS_HERO_TAG) }
            capture("stats_classic-light")
            // D-262: after the capture, each tab is scrolled into the row's viewport and the
            // touch-target gate runs again, so the tabs past the screen edge are checked too.
            for (i in STATS_TABS.indices) {
                val tab = compose.onNodeWithTag(statsTabTag(i))
                tab.performScrollTo()
                compose.waitForIdle()
                val bounds = tab.fetchSemanticsNode().boundsInRoot
                assertWithMessage("the visible bounds of tab $i after the scroll")
                    .that(bounds.width > 0f && bounds.height > 0f)
                    .isTrue()
                compose.assertTouchTargets()
            }
        }
    }

    /** F23-AC7 */
    @Test
    fun howToPlayGolden() {
        onScreen(HowToPlayKey) {
            compose.onNodeWithTag(HOW_TO_PLAY_CONTENT_TAG).assertExists()
            capture("howtoplay_classic-light")
        }
    }

    /** F25-AC8 */
    @Test
    fun aboutGolden() {
        onScreen(AboutKey) {
            compose.onNodeWithTag(ABOUT_VERSION_TAG).assertExists()
            // The credits come from legacy_history.json, read once on the I/O dispatcher; on the
            // Pixel 7 the first one is on screen, so the capture waits for it.
            compose.awaitLoaded { exists(aboutCreditTag(0)) }
            capture("about_classic-light")
        }
    }

    /** F26-AC6 */
    @Test
    fun licensesGolden() {
        onScreen(AboutKey) {
            compose.onNodeWithTag(screenTag(AboutKey)).assertExists()
            compose.onNodeWithTag(ABOUT_LICENSES_TAG).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(screenTag(LicensesKey)).assertExists()
            // The groups are read once on the I/O dispatcher; then the MIT group is expanded.
            val mit = licenseToggleTag(LicenseCatalog.MIT)
            compose.awaitLoaded { exists(mit) }
            compose.onNodeWithTag(mit).performClick()
            compose.awaitLoaded { exists(licenseTextTag(LicenseCatalog.MIT)) }
            capture("licenses_classic-light")
        }
    }

    /** F17-AC17 */
    @Test
    @Config(fontScale = 2.0f)
    fun fontScale200Goldens() {
        GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.setContent { rig.Content() }
            rig.awaitReady(compose)
            compose.waitForIdle()
            capture("game_mid-4x4_classic-light_fs200")

            compose.openFromMenu(SettingsKey)
            compose.onNodeWithTag(SETTINGS_LIST_TAG).assertExists()
            capture("settings_classic-light_fs200")
        }
    }

    /** F24-AC9 */
    @Test
    fun whatsNewSheetGolden() {
        val game = GoldenFixtures.FIX_MID_4
        GoldenRig(
                tmp.newFolder(),
                game,
                settings = goldenSettings(game).copy(lastSeenVersionCode = SEEN_BEFORE_UPDATE),
            )
            .use { rig ->
                compose.setContent { rig.Content() }
                rig.awaitReady(compose)
                compose.awaitLoaded { exists(WHATS_NEW_SHEET_TAG) }
                val sheet = compose.onNode(isDialog())
                sheet.captureGolden("sheet_whatsnew_classic-light")
                // ATF and the touch targets of the sheet's own window, while it is open.
                sheet.assertAccessible()
                compose.assertDialogTouchTargets()

                // The main window's touch targets and atfRule's after-test check, with the sheet
                // dismissed by "Got it".
                compose.onNodeWithTag(WHATS_NEW_GOT_IT_TAG).performClick()
                compose.waitForIdle()
                compose.onNodeWithTag(WHATS_NEW_SHEET_TAG).assertDoesNotExist()
                compose.assertTouchTargets()
            }
    }

    /**
     * Composes `App` over `FIX_MID_4` (with [records] = `FIX_RECORDS`, and [settings] applied to
     * the §10.2 settings), waits for the game, opens [key] from the menu and runs [block] on it.
     */
    private fun onScreen(
        key: NavKey,
        settings: (Settings) -> Settings = { it },
        records: Boolean = false,
        block: () -> Unit,
    ) {
        val game = GoldenFixtures.FIX_MID_4
        GoldenRig(
                tmp.newFolder(),
                game,
                settings = settings(goldenSettings(game)),
                records = if (records) GoldenFixtures.FIX_RECORDS else recordsOf(game),
            )
            .use { rig ->
                compose.setContent { rig.Content() }
                rig.awaitReady(compose)
                compose.waitForIdle()
                compose.openFromMenu(key)
                compose.onNodeWithTag(screenTag(key)).assertExists()
                block()
            }
    }

    /** Captures the main window as [name], then runs ATF and the touch-target gate on it. */
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureGolden(name)
        compose.onRoot().assertAccessible()
        compose.assertTouchTargets()
    }

    private fun exists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** DESIGN_SYSTEM.md §10.4 row 44: a version seen before this one. */
        const val SEEN_BEFORE_UPDATE = 20000
    }
}
