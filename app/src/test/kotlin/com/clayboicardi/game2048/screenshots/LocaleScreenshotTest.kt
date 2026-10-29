package com.clayboicardi.game2048.screenshots

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.header.SIZE_CHIP_TAG
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.settings.SETTINGS_LIST_TAG
import com.clayboicardi.game2048.testing.assertAccessible
import com.clayboicardi.game2048.testing.assertTouchTargets
import com.clayboicardi.game2048.testing.atfRule
import com.clayboicardi.game2048.testing.captureGolden
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The locale goldens (DESIGN_SYSTEM.md §10.2, §10.4 rows 23–26; L10N_STORE_LEGAL.md §1.11, §1.12;
 * D-104, D-106, D-147, D-204) at SDK 36 on the Pixel 7 qualifier, under ATF and the touch-target
 * gate (D-085, D-205): `fa` (RTL chrome, LTR board), `de` (long strings) and the pseudo-locales
 * `en-XA` (expansion, the hard-coded-text detector) and `ar-XB` (pseudo-bidi). Each golden sets its
 * locale with `RuntimeEnvironment.setQualifiers` and then starts an activity of its own, so the
 * activity's resources and layout direction come from that locale; the content is `App` over
 * `FIX_MID_4` with the §10.2 settings (Motion Off, onboarding done, the current version seen).
 *
 * The call restates the Pixel 7 window (`"+fa-w411dp-h914dp"`): a `+` call recomputes the window
 * from its pixel size rounded down to whole dp, so `"+fa"` alone leaves a 410 × 913 dp window (1076
 * × 2396 px instead of golden 3's 1078 × 2399) and a second call 409 × 912 dp. [show] checks that
 * the captured window is the whole Pixel 7 display, so the board matches golden 3.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LocaleScreenshotTest {
    private val activities = Activities()
    private val compose = createEmptyComposeRule()

    // The activities close last, after atfRule's after-test check has read the open one.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(activities).around(compose).around(atfRule(compose))
    @get:Rule val tmp = TemporaryFolder()

    /** F28-AC11 */
    @Test
    fun persianGameGolden() {
        onGame("fa", "fa") { capture("game_mid-4x4_classic-light_fa") }
    }

    /** F28-AC12 */
    @Test
    fun germanSettingsGolden() {
        onSettings("de", "de") { capture("settings_classic-light_de") }
    }

    /** F28-AC13 */
    @Test
    fun pseudoLocaleGoldens() {
        onSettings("en-rXA", "en-XA") { capture("settings_classic-light_en-XA") }
        onGame("ar-rXB", "ar-XB") { capture("game_mid-4x4_classic-light_ar-XB") }
    }

    /**
     * Sets the [locale] qualifier (on top of the Pixel 7's), then shows the Game screen over
     * `FIX_MID_4` in a new activity whose locale must be [tag], and runs [block] once the game has
     * loaded.
     */
    private fun onGame(locale: String, tag: String, block: () -> Unit) {
        show(locale, tag) {
            compose.onNodeWithTag(SIZE_CHIP_TAG).assertExists()
            block()
        }
    }

    /** [onGame], then opens Settings from the game menu as a player would and runs [block]. */
    private fun onSettings(locale: String, tag: String, block: () -> Unit) {
        show(locale, tag) {
            compose.openFromMenu(SettingsKey)
            compose.onNodeWithTag(screenTag(SettingsKey)).assertExists()
            compose.onNodeWithTag(SETTINGS_LIST_TAG).assertExists()
            block()
        }
    }

    private fun show(locale: String, tag: String, block: () -> Unit) {
        assertWithMessage("the class qualifier's window")
            .that(RobolectricDeviceQualifiers.Pixel7)
            .startsWith("$PIXEL7_WINDOW-")
        // One activity at a time: the previous golden's window leaves before the next locale.
        activities.closeAll()
        RuntimeEnvironment.setQualifiers("+$locale-$PIXEL7_WINDOW")
        GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            val scenario = activities.launch()
            var activityLocale = ""
            var windowDp = IntSize.Zero
            var displayPx = IntSize.Zero
            scenario.onActivity { activity ->
                val configuration = activity.resources.configuration
                activityLocale = configuration.locales[0].toLanguageTag()
                windowDp = IntSize(configuration.screenWidthDp, configuration.screenHeightDp)
                val metrics = activity.resources.displayMetrics
                displayPx = IntSize(metrics.widthPixels, metrics.heightPixels)
                activity.setContent { rig.Content() }
            }
            assertWithMessage("the activity's locale").that(activityLocale).isEqualTo(tag)
            assertWithMessage("the activity's window in dp").that(windowDp).isEqualTo(PIXEL7_DP)
            rig.awaitReady(compose)
            compose.waitForIdle()
            // The golden is the whole edge-to-edge window, which must be the Pixel 7 display.
            assertWithMessage("the captured window in px")
                .that(compose.onRoot().fetchSemanticsNode().size)
                .isEqualTo(displayPx)
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

    /** The activities a test started; whatever is still open closes after the test's rules. */
    private class Activities : ExternalResource() {
        private val open = mutableListOf<ActivityScenario<ComponentActivity>>()

        fun launch(): ActivityScenario<ComponentActivity> =
            ActivityScenario.launch(ComponentActivity::class.java).also { open += it }

        fun closeAll() {
            open.forEach { it.close() }
            open.clear()
        }

        override fun after() {
            closeAll()
        }
    }

    private companion object {
        /** The Pixel 7 window of every other golden, in dp: 411 × 914 at 420 dpi. */
        val PIXEL7_DP = IntSize(411, 914)

        /** [PIXEL7_DP] as the qualifiers that restate it. */
        val PIXEL7_WINDOW = "w${PIXEL7_DP.width}dp-h${PIXEL7_DP.height}dp"
    }
}
