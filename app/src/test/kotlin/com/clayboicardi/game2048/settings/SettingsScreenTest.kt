package com.clayboicardi.game2048.settings

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import android.provider.Settings.ACTION_APP_LOCALE_SETTINGS
import android.provider.Settings.Global
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.Game2048Application
import com.clayboicardi.game2048.MainActivity
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.data.AppJson
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SwipeSensitivity
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.di.settingsViewModelFactory
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.MORE_TAG
import com.clayboicardi.game2048.menuItemTag
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FakeContrastLevelSource
import com.clayboicardi.game2048.testing.FakeHapticFeedback
import com.clayboicardi.game2048.testing.FakeSystemBarsController
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.components.DIALOG_DISMISS_TAG
import com.clayboicardi.game2048.ui.components.choiceOptionTag
import com.clayboicardi.game2048.ui.theme.AnimatorScaleSource
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.clayboicardi.game2048.ui.theme.SystemAnimatorScaleSource
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The Settings screen (DESIGN_SYSTEM.md §6.8; FEATURES.md F11, F14 item 3; MOTION_SPEC.md §6.1;
 * L10N_STORE_LEGAL.md §1.13; D-006, D-063, D-073, D-102). Most tests compose `SettingsScreen` in
 * `GameTheme` over a `SettingsViewModel` of a container of their own (real DataStore files in a
 * `TemporaryFolder`, fake animator-scale and contrast sources). The theme and haptics tests compose
 * the whole app (`TestApp`) and open Settings from the game menu, so `App()` applies the theme and
 * provides `LocalHapticsEnabled`; the recreation test launches `MainActivity`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val haptics = FakeHapticFeedback { compose.mainClock.currentTime }

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    /** F11-AC1 */
    @Test
    @Config(qualifiers = TALL_WINDOW)
    fun rowsInCanonicalOrderWithDefaults() {
        rig(Settings()).use { rig ->
            showScreen(rig)

            val tops =
                SettingRow.entries.map { row ->
                    val node = compose.onNodeWithTag(settingRowTag(row)).fetchSemanticsNode()
                    val minHeight = with(compose.density) { TWO_LINE_ROW.roundToPx() }
                    assertWithMessage("$row height (px)")
                        .that(node.size.height)
                        .isAtLeast(minHeight)
                    node.positionInRoot.y
                }
            assertWithMessage("row order").that(tops).isInStrictOrder()

            for ((row, summary) in DEFAULT_SUMMARIES) {
                compose.onNodeWithTag(settingRowTag(row)).assert(hasText(summary))
            }
            for (row in SWITCHES) {
                val node = compose.onNodeWithTag(settingRowTag(row)).assert(hasRole(Role.Switch))
                if (row == SettingRow.HAPTICS) node.assertIsOn() else node.assertIsOff()
            }
            for (row in SettingRow.entries - SWITCHES) {
                compose.onNodeWithTag(settingRowTag(row)).assert(hasRole(Role.Button))
            }
            for (header in SECTION_HEADERS) compose.onNodeWithText(header).assertExists()
            compose.onAllNodesWithText("Reset", substring = true).assertCountEquals(0)
        }
    }

    /** F11-AC2 */
    @Test
    fun choiceDialogAppliesOnSelect() {
        // Under Motion Off a theme switch shows its new colours on its first frame (MOTION_SPEC.md
        // §3.15); under Full the chrome crossfades for 299 ms.
        rig(SETTINGS.copy(motion = MotionPreference.OFF)).use { rig ->
            showSettingsInApp(rig)
            assertWithMessage("background before")
                .that(backgroundLuminance())
                .isGreaterThan(LIGHT_MIN)
            assertWithMessage("top bar before").that(topBarLuminance()).isGreaterThan(LIGHT_MIN)
            row(SettingRow.THEME).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assert(hasAnyDescendant(hasText("Theme")))
            compose.mainClock.autoAdvance = false

            compose
                .onNodeWithTag(choiceOptionTag(ThemeMode.DARK.ordinal))
                .assert(hasText("Dark"))
                .performClick()
            // What the main thread runs before the next frame (the settings' collectors), then
            // that frame.
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()

            assertThat(rig.container.settingsRepository.state.value.themeMode)
                .isEqualTo(ThemeMode.DARK)
            assertWithMessage("background on the next frame")
                .that(backgroundLuminance())
                .isLessThan(DARK_MAX)
            // M3's TopAppBar animates its container colour with the motion scheme's effects spec:
            // under Off a snap, which its animation applies on the frame after the recomposition.
            compose.mainClock.advanceTimeByFrame()
            assertWithMessage("top bar on the second frame")
                .that(topBarLuminance())
                .isLessThan(DARK_MAX)
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertThat(rig.fileSettings().themeMode).isEqualTo(ThemeMode.DARK)
            row(SettingRow.THEME).assert(hasText("Dark"))
        }
    }

    /** F11-AC3 */
    @Test
    fun choiceDialogCancelKeepsValue() {
        rig(SETTINGS).use { rig ->
            showScreen(rig)
            row(SettingRow.SWIPE_SENSITIVITY).performClick()
            compose.waitForIdle()
            compose
                .onNode(isDialog())
                .assert(hasAnyDescendant(hasText("Swipe sensitivity")))
                .assert(hasAnyDescendant(hasText("Reacts to short swipes")))
            val minHeight = with(compose.density) { ONE_LINE_ROW.roundToPx() }
            for (option in SwipeSensitivity.entries) {
                val node =
                    compose
                        .onNodeWithTag(choiceOptionTag(option.ordinal))
                        .assert(hasRole(Role.RadioButton))
                        .fetchSemanticsNode()
                assertWithMessage("$option option height (px)")
                    .that(node.size.height)
                    .isAtLeast(minHeight)
            }
            compose
                .onNodeWithTag(choiceOptionTag(SwipeSensitivity.MEDIUM.ordinal))
                .assertIsSelected()

            compose.onNodeWithTag(DIALOG_DISMISS_TAG).performClick()
            compose.waitForIdle()

            compose.onNode(isDialog()).assertDoesNotExist()
            assertThat(rig.container.settingsRepository.state.value.swipeSensitivity)
                .isEqualTo(SwipeSensitivity.MEDIUM)
            assertThat(rig.container.settingsRepository.submissions).isEqualTo(0)
            row(SettingRow.SWIPE_SENSITIVITY).assert(hasText("Medium"))
        }
    }

    /** F11-AC4 */
    @Test
    @Config(sdk = [24], qualifiers = TALL_WINDOW)
    fun apiGatedRowsAbsentOnApi24() {
        rig(SETTINGS).use { rig ->
            showScreen(rig)

            compose.onNodeWithTag(settingRowTag(SettingRow.KEEP_SCREEN_ON)).assertExists()
            compose.onNodeWithTag(settingRowTag(SettingRow.DYNAMIC_COLOR)).assertDoesNotExist()
            compose.onNodeWithTag(settingRowTag(SettingRow.APP_LANGUAGE)).assertDoesNotExist()
            compose.onNodeWithText("Dynamic color").assertDoesNotExist()
            compose.onNodeWithText("App language").assertDoesNotExist()
            compose.onNodeWithText("Language").assertDoesNotExist()
        }
    }

    /** F11-AC5 */
    @Test
    fun appLanguageRowOpensSystemPage() {
        rig(SETTINGS).use { rig ->
            showScreen(rig)

            row(SettingRow.APP_LANGUAGE).performClick()
            compose.waitForIdle()

            val started = checkNotNull(shadowOf(app).nextStartedActivity) { "no activity started" }
            assertThat(started.action).isEqualTo(ACTION_APP_LOCALE_SETTINGS)
            assertThat(started.dataString).isEqualTo("package:com.clayboicardi.game2048.debug")
        }
    }

    /** F11-AC6 */
    @Test
    fun appLanguageFallbackSnackbar() {
        shadowOf(app).checkActivities(true)
        rig(SETTINGS).use { rig ->
            showScreen(rig)
            val language = row(SettingRow.APP_LANGUAGE)
            compose.mainClock.autoAdvance = false

            language.performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SETTLE_MS)

            compose.onNodeWithText("No app can open this link").assertExists()
            assertThat(shadowOf(app).nextStartedActivity).isNull()
            compose.onNodeWithTag(SETTINGS_LIST_TAG).assertExists()
            compose.mainClock.autoAdvance = true
        }
    }

    /** F11-AC7 */
    @Test
    fun motionRowLockedWhenSystemAnimationsOff() {
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        val sourceScope = CoroutineScope(Job())
        try {
            val stored = SETTINGS.copy(motion = MotionPreference.REDUCED)
            rig(stored, scale = SystemAnimatorScaleSource(app, sourceScope)).use { rig ->
                showScreen(rig)

                assertMotionLocked()
                row(SettingRow.MOTION).performClick()
                compose.waitForIdle()

                compose.onNode(isDialog()).assertDoesNotExist()
                assertThat(rig.container.settingsRepository.state.value.motion)
                    .isEqualTo(MotionPreference.REDUCED)
                assertThat(rig.container.settingsRepository.submissions).isEqualTo(0)
            }
        } finally {
            sourceScope.cancel()
        }
    }

    /** F11-AC8 */
    @Test
    fun highContrastLockedBySystemContrast() {
        val contrast = FakeContrastLevelSource(SYSTEM_CONTRAST_HIGH)
        rig(SETTINGS, contrast = contrast).use { rig ->
            showScreen(rig)

            row(SettingRow.HIGH_CONTRAST)
                .assertIsOn()
                .assertIsNotEnabled()
                .assert(hasText("On because the system contrast setting is high"))
                .performClick()
            compose.waitForIdle()
            assertThat(rig.container.settingsRepository.state.value.highContrast).isFalse()
            assertThat(rig.container.settingsRepository.submissions).isEqualTo(0)

            // When the system condition ends, the stored setting applies again.
            contrast.contrast.value = 0f
            compose.waitForIdle()
            row(SettingRow.HIGH_CONTRAST)
                .assertIsOff()
                .assertIsEnabled()
                .assert(hasText("Black or white numbers and tile outlines"))
        }
    }

    /** F11-AC9 */
    @Test
    fun choiceDialogSurvivesRecreation() {
        val application = ApplicationProvider.getApplicationContext<Game2048Application>()
        writeAppFiles(application, SETTINGS)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            val factory = application.container.gameViewModelFactory()
            scenario.onActivity { vm = ViewModelProvider(it, factory)[GameViewModel::class.java] }
            compose.waitUntil(LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            compose.waitForIdle()
            openSettingsFromMenu()
            row(SettingRow.PALETTE).performClick()
            compose.waitForIdle()
            compose.onNode(isDialog()).assert(hasAnyDescendant(hasText("Neon Arcade")))

            scenario.recreate()
            compose.waitForIdle()

            compose
                .onNode(isDialog())
                .assert(hasAnyDescendant(hasText("Palette")))
                .assert(hasAnyDescendant(hasText("Classic Refined")))
                .assert(hasAnyDescendant(hasText("Neon Arcade")))
            compose.onNodeWithTag(screenTag(SettingsKey)).assertExists()
        }
    }

    /** F14-AC8, AC-HAP-4 */
    @Test
    fun switchesFireToggleHaptics() {
        rig(SETTINGS).use { rig ->
            showSettingsInApp(rig)
            haptics.clear()
            val settings = rig.container.settingsRepository.state

            toggle(SettingRow.HIGH_CONTRAST)
            assertThat(settings.value.highContrast).isTrue()
            toggle(SettingRow.HIGH_CONTRAST)
            assertThat(settings.value.highContrast).isFalse()
            toggle(SettingRow.IMMERSIVE)
            assertThat(settings.value.immersive).isTrue()
            toggle(SettingRow.IMMERSIVE)
            assertThat(settings.value.immersive).isFalse()
            assertThat(types())
                .containsExactly(
                    HapticFeedbackType.ToggleOn,
                    HapticFeedbackType.ToggleOff,
                    HapticFeedbackType.ToggleOn,
                    HapticFeedbackType.ToggleOff,
                )
                .inOrder()
            haptics.clear()

            // Haptics off fires nothing, and so does any switch while Haptics is off.
            toggle(SettingRow.HAPTICS)
            assertThat(settings.value.haptics).isFalse()
            toggle(SettingRow.KEEP_SCREEN_ON)
            assertThat(settings.value.keepScreenOn).isTrue()
            assertThat(types()).isEmpty()

            // Haptics on fires ToggleOn (the new value decides), then switches fire again.
            toggle(SettingRow.HAPTICS)
            assertThat(settings.value.haptics).isTrue()
            toggle(SettingRow.KEEP_SCREEN_ON)
            assertThat(settings.value.keepScreenOn).isFalse()
            assertThat(types())
                .containsExactly(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff)
                .inOrder()
        }
    }

    /** L-AC6 */
    @Test
    fun appLanguageSummaryDefaultsToSystem() {
        rig(SETTINGS).use { rig ->
            showScreen(rig)

            row(SettingRow.APP_LANGUAGE).assert(hasText("System default"))
            assertThat(appLanguageSummary(app)).isEqualTo("System default")

            // Once a per-app locale is set, the summary names it in that locale.
            app.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags("de")
            assertThat(appLanguageSummary(app)).isEqualTo("Deutsch")
        }
    }

    /** AC-SCALE-4 */
    @Test
    fun motionRowDisabledAtSystemScaleZero() {
        val scale = FakeAnimatorScaleSource(0f)
        rig(SETTINGS, scale = scale).use { rig ->
            showScreen(rig)

            assertMotionLocked()

            // The lock follows the live scale; the stored value shows again when it ends.
            scale.scale.value = 1f
            compose.waitForIdle()
            row(SettingRow.MOTION).assertIsEnabled().assert(hasText("Full"))
            assertThat(rig.container.settingsRepository.submissions).isEqualTo(0)
        }
    }

    private fun assertMotionLocked() {
        row(SettingRow.MOTION)
            .assertIsNotEnabled()
            .assert(hasText("Off (system animations are off)"))
    }

    /** Taps switch [row] and waits until the change has reached every collector. */
    private fun toggle(row: SettingRow) {
        row(row).performClick()
        compose.waitForIdle()
    }

    private fun types(): List<HapticFeedbackType> = haptics.events.map { it.type }

    /** Scrolls the Settings list to [row] and returns its node. */
    private fun row(row: SettingRow): SemanticsNodeInteraction {
        compose.onNodeWithTag(SETTINGS_LIST_TAG).performScrollToNode(hasTestTag(settingRowTag(row)))
        compose.waitForIdle()
        return compose.onNodeWithTag(settingRowTag(row))
    }

    /** The luminance of the background at the end of the Appearance header (its end padding). */
    private fun backgroundLuminance(): Float {
        val screen = compose.onNodeWithTag(screenTag(SettingsKey)).fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithText("Appearance").fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithTag(screenTag(SettingsKey)).captureToImage().toPixelMap()
        return image[
                (header.right - screen.left).toInt() - 4, (header.center.y - screen.top).toInt()]
            .luminance()
    }

    /** The luminance of the Settings top bar's end, away from its title (light or dark theme). */
    private fun topBarLuminance(): Float {
        val image = compose.onNodeWithTag(screenTag(SettingsKey)).captureToImage().toPixelMap()
        return image[image.width - 2, 2].luminance()
    }

    private fun rig(
        settings: Settings,
        scale: AnimatorScaleSource = FakeAnimatorScaleSource(),
        contrast: FakeContrastLevelSource = FakeContrastLevelSource(),
    ) = SettingsRig(tmp.newFolder(), settings, scale, contrast)

    /** `SettingsScreen` alone, in `GameTheme`, with the fake haptics. */
    private fun showScreen(rig: SettingsRig) {
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                GameTheme { SettingsScreen(rig.vm, onBack = {}) }
            }
        }
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            rig.container.settingsRepository.loaded.value
        }
        compose.waitForIdle()
    }

    /** The whole app with the fake haptics, then Settings opened from the game menu. */
    private fun showSettingsInApp(rig: SettingsRig) {
        val game = rig.gameViewModel()
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                TestApp(rig.container, game)
            }
        }
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            game.ready.value
        }
        compose.waitForIdle()
        openSettingsFromMenu()
    }

    private fun openSettingsFromMenu() {
        compose.onNodeWithTag(MORE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(menuItemTag(SettingsKey)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(screenTag(SettingsKey)).assertExists()
    }

    private companion object {
        /** DESIGN_SYSTEM.md §10.2: onboarding done, the current version seen. */
        val SETTINGS = Settings(onboardingDone = true, lastSeenVersionCode = TEST_VERSION_CODE)

        /** A window tall enough for the `LazyColumn` to compose every row at once. */
        const val TALL_WINDOW = "w411dp-h2000dp"

        /** The DESIGN_SYSTEM.md §6.8 minimums of a one-line and a two-line row. */
        val ONE_LINE_ROW = 56.dp
        val TWO_LINE_ROW = 72.dp

        val SWITCHES =
            setOf(
                SettingRow.HIGH_CONTRAST,
                SettingRow.DYNAMIC_COLOR,
                SettingRow.HAPTICS,
                SettingRow.IMMERSIVE,
                SettingRow.KEEP_SCREEN_ON,
            )

        /** The FEATURES.md §2.1 defaults as each choice and link row shows them. */
        val DEFAULT_SUMMARIES =
            mapOf(
                SettingRow.THEME to "System default",
                SettingRow.PALETTE to "Classic Refined",
                SettingRow.MOTION to "Full",
                SettingRow.MOVE_TIMING to "As you swipe",
                SettingRow.SWIPE_SENSITIVITY to "Medium",
                SettingRow.APP_LANGUAGE to "System default",
            )

        val SECTION_HEADERS =
            listOf("Appearance", "Motion and feedback", "Controls", "Display", "Language")

        /** 0.5 = Android 14's "Medium" contrast: the lowest level that forces high contrast. */
        const val SYSTEM_CONTRAST_HIGH = 0.5f

        /** The Classic light and dark top bars are far apart; these bounds tell them apart. */
        const val LIGHT_MIN = 0.6f
        const val DARK_MAX = 0.2f

        const val SNACKBAR_SETTLE_MS = 500L

        /** A bound on the real-thread DataStore load, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}

private fun hasRole(role: Role): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

/** Writes a 4×4 game and [settings] where the production container reads them (D-040). */
private fun writeAppFiles(app: Game2048Application, settings: Settings) {
    val dir = File(app.filesDir, "datastore").apply { mkdirs() }
    val game = GoldenFixtures.FIX_MID_4
    File(dir, DataStoreFiles.game(game.state.size))
        .writeText(
            AppJson.encodeToString(GameDocument.serializer(), GameDocumentMapper.toDocument(game)),
        )
    File(dir, DataStoreFiles.SETTINGS)
        .writeText(AppJson.encodeToString(Settings.serializer(), settings))
}

/**
 * One container over [folder] with [settings] seeded through its own store, the given animator
 * scale and contrast sources and a recording system-bar controller, and its `SettingsViewModel`
 * (`settingsViewModelFactory()`) in a store of its own.
 */
private class SettingsRig(
    private val folder: File,
    settings: Settings,
    scale: AnimatorScaleSource,
    contrast: FakeContrastLevelSource,
) : AutoCloseable {
    val container =
        AppContainer(
            files = { name -> File(folder, name) },
            versionCode = TEST_VERSION_CODE,
            clock = FakeClock(),
            seedSource = FixedSeedSource(RIG_SEED),
            animatorScale = scale,
            contrastLevel = contrast,
            systemBars = FakeSystemBarsController(),
        )
    private val store = ViewModelStore()
    val vm: SettingsViewModel

    init {
        runBlocking { container.seedSettings(settings) }
        vm =
            ViewModelProvider(store, container.settingsViewModelFactory())[
                SettingsViewModel::class.java]
    }

    /** The activity-scoped `GameViewModel` of a `TestApp` over this container. */
    fun gameViewModel(): GameViewModel =
        ViewModelProvider(store, container.gameViewModelFactory())[GameViewModel::class.java]

    /** `settings.json` once every submitted write has been attempted. */
    fun fileSettings(): Settings {
        runBlocking { container.settingsRepository.awaitIdle() }
        return AppJson.decodeFromString(
            Settings.serializer(),
            File(folder, DataStoreFiles.SETTINGS).readText(),
        )
    }

    override fun close() {
        store.clear()
        container.applicationScope.cancel()
    }

    private companion object {
        const val RIG_SEED = 0x2048L
    }
}
