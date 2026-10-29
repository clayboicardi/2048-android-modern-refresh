package com.clayboicardi.game2048.screenshots

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.NEW_GAME_TAG
import com.clayboicardi.game2048.game.header.SIZE_CHIP_TAG
import com.clayboicardi.game2048.game.input.PAD_TAG
import com.clayboicardi.game2048.game.overlay.GAME_OVER_CARD_TAG
import com.clayboicardi.game2048.game.overlay.HINT_TAG
import com.clayboicardi.game2048.game.overlay.SIZE_SHEET_TAG
import com.clayboicardi.game2048.game.overlay.WIN_CARD_TAG
import com.clayboicardi.game2048.testing.assertAccessible
import com.clayboicardi.game2048.testing.assertDialogTouchTargets
import com.clayboicardi.game2048.testing.assertTouchTargets
import com.clayboicardi.game2048.testing.atfRule
import com.clayboicardi.game2048.testing.captureGolden
import com.clayboicardi.game2048.ui.components.DIALOG_DISMISS_TAG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The Game screen goldens (DESIGN_SYSTEM.md §10.2, §10.4 rows 1–16, 36, 42, 43 and 45; D-147,
 * D-204) at SDK 36 on the Pixel 7 qualifier, under ATF and the touch-target gate (D-085, D-205):
 * the main window after each test, the dialog's or sheet's own window while it is open. Settings:
 * Motion Off, onboarding done, the current version seen (§10.2). Each game is loaded by a container
 * of its own. The dark rows set `themeMode = DARK` and the `+night` qualifier, so platform-drawn
 * surfaces match (§10.2).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GameScreenshotTest {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(atfRule(compose))
    @get:Rule val tmp = TemporaryFolder()

    /** F12-AC11 */
    @Test
    fun freshAndMidGoldens() {
        captureGame(
            Golden(GoldenFixtures.FIX_FRESH_4, "game_new-4x4_classic-light"),
            Golden(GoldenFixtures.FIX_MID_4, "game_mid-4x4_classic-light"),
        )
    }

    /** F12-AC11 */
    @Test
    @Config(qualifiers = NIGHT)
    fun freshAndMidDarkGoldens() {
        captureGame(
            Golden(GoldenFixtures.FIX_FRESH_4, "game_new-4x4_classic-dark", DARK),
            Golden(GoldenFixtures.FIX_MID_4, "game_mid-4x4_classic-dark", DARK),
        )
    }

    /** F02-AC13 */
    @Test
    fun otherSizesGoldens() {
        captureGame(
            Golden(GoldenFixtures.FIX_MID_3, "game_mid-3x3_classic-light"),
            Golden(GoldenFixtures.FIX_DENSE_8, "game_dense-8x8_classic-light"),
        )
    }

    /** F02-AC13 */
    @Test
    @Config(qualifiers = NIGHT)
    fun otherSizesDarkGoldens() {
        captureGame(Golden(GoldenFixtures.FIX_DENSE_8, "game_dense-8x8_classic-dark", DARK))
    }

    /** F12-AC10 */
    @Test
    fun paletteRampGoldens() {
        val ramp = GoldenFixtures.FIX_RAMP_5
        captureGame(
            Golden(ramp, "game_ramp-5x5_classic-light"),
            Golden(ramp, "game_ramp-5x5_neon-light", { it.copy(palette = PaletteId.NEON) }),
            Golden(ramp, "game_ramp-5x5_classic-light-hc", { it.copy(highContrast = true) }),
        )
    }

    /** F12-AC10 */
    @Test
    @Config(qualifiers = NIGHT)
    fun paletteRampDarkGoldens() {
        val ramp = GoldenFixtures.FIX_RAMP_5
        captureGame(
            Golden(ramp, "game_ramp-5x5_classic-dark", DARK),
            Golden(ramp, "game_ramp-5x5_neon-dark", { DARK(it).copy(palette = PaletteId.NEON) }),
        )
    }

    /** F06-AC12 */
    @Test
    fun newGameDialogGolden() {
        GoldenRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4).use { rig ->
            compose.setContent { rig.Content() }
            rig.awaitReady(compose)
            compose.waitForIdle()

            compose.onNodeWithTag(NEW_GAME_TAG).performClick()
            compose.waitForIdle()
            val dialog = compose.onNode(isDialog())
            dialog.captureGolden("dialog_newgame_classic-light")
            // ATF and the touch targets of the dialog's own window, while it is open (D-085,
            // D-205).
            dialog.assertAccessible()
            compose.assertDialogTouchTargets()

            // The main window's touch targets, and the after-test ATF check of atfRule, which reads
            // the main window's root only: with the dialog closed.
            compose.onNodeWithTag(DIALOG_DISMISS_TAG).performClick()
            compose.waitForIdle()
            compose.assertTouchTargets()
        }
    }

    /** F03-AC17 */
    @Test
    fun winCardGoldens() {
        captureGame(
            Golden(GoldenFixtures.FIX_WON_4, "game_won-4x4_classic-light"),
            tag = WIN_CARD_TAG,
        )
    }

    /** F03-AC17 */
    @Test
    @Config(qualifiers = NIGHT)
    fun winCardDarkGolden() {
        captureGame(
            Golden(GoldenFixtures.FIX_WON_4, "game_won-4x4_classic-dark", DARK),
            tag = WIN_CARD_TAG,
        )
    }

    /** F04-AC13 */
    @Test
    fun gameOverCardGoldens() {
        captureGame(
            Golden(GoldenFixtures.FIX_OVER_4, "game_over-4x4_classic-light"),
            tag = GAME_OVER_CARD_TAG,
        )
    }

    /** F04-AC13 */
    @Test
    @Config(qualifiers = NIGHT)
    fun gameOverCardDarkGolden() {
        captureGame(
            Golden(GoldenFixtures.FIX_OVER_4, "game_over-4x4_classic-dark", DARK),
            tag = GAME_OVER_CARD_TAG,
        )
    }

    /** F17-AC18 */
    @Test
    fun talkBackPadGolden() {
        // Touch exploration on before the content composes, as TalkBack would be (row 36).
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(AccessibilityManager::class.java))
            .setTouchExplorationEnabled(true)
        captureGame(
            Golden(GoldenFixtures.FIX_MID_4, "game_mid-4x4_classic-light_dpad"),
            tag = PAD_TAG,
        )
    }

    /** F22-AC8 */
    @Test
    fun firstLaunchHintGolden() {
        captureGame(
            Golden(GoldenFixtures.FIX_FRESH_4, "game_hint-4x4_classic-light") {
                it.copy(onboardingDone = false)
            },
            tag = HINT_TAG,
        )
    }

    /** F02-AC14 */
    @Test
    fun boardSizeSheetGolden() {
        // Row 42's three fixtures: FIX_MID_4 current, FIX_MID_3 saved in progress, FIX_RECORDS
        // (DESIGN_SYSTEM.md §10.4), with the §10.2 settings.
        GoldenRig(
                tmp.newFolder(),
                GoldenFixtures.FIX_MID_4,
                records = GoldenFixtures.FIX_RECORDS,
                others = listOf(GoldenFixtures.FIX_MID_3),
            )
            .use { rig ->
                compose.setContent { rig.Content() }
                rig.awaitReady(compose)
                compose.waitForIdle()

                compose.onNodeWithTag(SIZE_CHIP_TAG).performClick()
                compose.waitForIdle()
                val sheet = compose.onNode(isDialog())
                sheet.captureGolden("sheet_boardsize_classic-light")
                // ATF and the touch targets of the sheet's own window, while it is open (D-085,
                // D-205).
                sheet.assertAccessible()
                compose.assertDialogTouchTargets()

                // The main window's touch targets and atfRule's after-test check, with the sheet
                // closed.
                (ShadowDialog.getLatestDialog() as ComponentDialog)
                    .onBackPressedDispatcher
                    .onBackPressed()
                compose.waitForIdle()
                compose.onNodeWithTag(SIZE_SHEET_TAG).assertDoesNotExist()
                compose.assertTouchTargets()
            }
    }

    /**
     * Captures each of [goldens] in turn, each game over a container of its own, once its load is
     * done (and the node tagged [tag] exists, when given), then runs ATF and the touch-target gate
     * on it (DESIGN_SYSTEM.md §10.2: every golden, not only the last one atfRule sees).
     */
    private fun captureGame(vararg goldens: Golden, tag: String? = null) {
        val rigs = goldens.map {
            GoldenRig(tmp.newFolder(), it.game, it.settings(goldenSettings(it.game)))
        }
        try {
            var shown: GoldenRig? by mutableStateOf(null)
            compose.setContent { shown?.let { rig -> key(rig) { rig.Content() } } }
            for ((rig, golden) in rigs.zip(goldens)) {
                shown = rig
                rig.awaitReady(compose)
                compose.waitForIdle()
                tag?.let { compose.onNodeWithTag(it).assertExists() }
                compose.onRoot().captureGolden(golden.name)
                compose.onRoot().assertAccessible()
                compose.assertTouchTargets()
            }
        } finally {
            rigs.forEach { it.close() }
        }
    }

    /** One golden row: [game] as [name], with the §10.2 settings changed by [settings]. */
    private class Golden(
        val game: SavedGame,
        val name: String,
        val settings: (Settings) -> Settings = { it },
    )

    private companion object {
        /** DESIGN_SYSTEM.md §10.2: the dark rows add the night qualifier to the Pixel 7's. */
        const val NIGHT = "+night"

        /** DESIGN_SYSTEM.md §10.2: the dark rows also set `themeMode = DARK`. */
        val DARK: (Settings) -> Settings = { it.copy(themeMode = ThemeMode.DARK) }
    }
}
