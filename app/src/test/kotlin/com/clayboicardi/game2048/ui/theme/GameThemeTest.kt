package com.clayboicardi.game2048.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.board.boardGeometryPx
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FakeContrastLevelSource
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.TestApp
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedSettings
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * `GameTheme` (FEATURES.md F12; D-070–D-074, D-189, D-206, D-248; DESIGN_SYSTEM.md §2.7, §2.11):
 * theme mode, palette, high contrast from the setting and from the system contrast level, dynamic
 * chrome at SDK 36 and below API 31, and the system-bar icons. Robolectric SDK 36 on the Pixel 7
 * qualifier unless a method says otherwise. Values provided by `GameTheme` are read by a probe
 * composable; what `App()` makes of the settings is read from the rendered window (the screen
 * background, the board's pixels) and from the activity's `WindowInsetsControllerCompat`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GameThemeTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F12-AC1 */
    @Test
    fun themeModeFollowsSystemAndOverrides() {
        val seen = Seen()
        var night by mutableStateOf(false)
        var mode by mutableStateOf(ThemeMode.SYSTEM)
        compose.setContent {
            Night(night) {
                // GameTheme alone follows the system; App() maps the Theme setting onto it.
                val dark =
                    when (mode) {
                        ThemeMode.SYSTEM -> null
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    }
                if (dark == null) GameTheme { Probe(seen) } else GameTheme(dark) { Probe(seen) }
            }
        }
        for (isNight in listOf(false, true)) {
            for (theme in ThemeMode.entries) {
                night = isNight
                mode = theme
                compose.waitForIdle()
                val expectDark = theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && isNight)
                assertWithMessage("GameTheme, night = $isNight, $theme: skin")
                    .that(seen.skin.id)
                    .isEqualTo(if (expectDark) "classic-dark" else "classic-light")
                assertWithMessage("GameTheme, night = $isNight, $theme: scheme")
                    .that(seen.scheme)
                    .isSameInstanceAs(if (expectDark) ClassicDarkScheme else ClassicLightScheme)
            }
        }
    }

    /** F12-AC1 */
    @Test
    fun appMapsThemeSettingOntoGameTheme() {
        var night by mutableStateOf(false)
        AppRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4, BASE_SETTINGS).use { rig ->
            compose.setContent { Night(night) { TestApp(rig.container, rig.vm) } }
            rig.awaitReady(compose)
            for (isNight in listOf(false, true)) {
                for (theme in ThemeMode.entries) {
                    night = isNight
                    rig.container.settingsRepository.update { it.copy(themeMode = theme) }
                    compose.waitForIdle()
                    val dark = theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && isNight)
                    val expected = if (dark) ClassicDarkPalette else ClassicLightPalette
                    val corner = compose.onRoot().captureToImage().asAndroidBitmap()
                    assertNear(
                        "App, night = $isNight, $theme: the screen background",
                        corner.getPixel(CORNER_PX, CORNER_PX),
                        expected.background,
                    )
                }
            }
        }
    }

    /** F12-AC2 */
    @Test
    fun paletteSelectionProvidesTilePalette() {
        val seen = Seen()
        var palette by mutableStateOf(PaletteId.CLASSIC)
        var dark by mutableStateOf(false)
        compose.setContent { GameTheme(darkTheme = dark, palette = palette) { Probe(seen) } }
        for ((id, isDark) in PALETTE_CASES) {
            palette = id
            dark = isDark
            compose.waitForIdle()
            val label = "$id ${if (isDark) "dark" else "light"}"
            val resolved = resolveTilePalette(id, isDark)
            assertWithMessage("$label: LocalTilePalette")
                .that(seen.palette)
                .isSameInstanceAs(resolved.palette)
            assertWithMessage("$label: LocalTileSkin").that(seen.skin).isEqualTo(resolved.skin)
            assertWithMessage("$label: chrome scheme")
                .that(seen.scheme)
                .isSameInstanceAs(chromeScheme(id, isDark))
            when (id) {
                PaletteId.CLASSIC ->
                    if (!isDark) {
                        assertWithMessage("$label: fill(11)")
                            .that(seen.palette.fill(11))
                            .isEqualTo(Color(0xFFE6A11A))
                    }
                PaletteId.NEON -> {
                    assertWithMessage("$label: board")
                        .that(seen.palette.board)
                        .isEqualTo(Color(0xFF1A1730))
                    assertWithMessage("$label: empty")
                        .that(seen.palette.empty)
                        .isEqualTo(Color(0xFF25213F))
                    val background = if (isDark) Color(0xFF0E0C1A) else Color(0xFFF3F1FA)
                    assertWithMessage("$label: background")
                        .that(seen.palette.background)
                        .isEqualTo(background)
                    assertWithMessage("$label: surface (D-189)")
                        .that(seen.scheme.surface)
                        .isEqualTo(background)
                }
            }
        }
    }

    /** F12-AC5 */
    @Test
    fun highContrastSettingInkAndOutline() {
        val seen = Seen()
        val settings = BASE_SETTINGS.copy(highContrast = true, lastBoardSize = 5)
        AppRig(tmp.newFolder(), GoldenFixtures.FIX_RAMP_5, settings).use { rig ->
            var app by mutableStateOf(false)
            compose.setContent {
                if (app) {
                    TestApp(rig.container, rig.vm)
                } else {
                    GameTheme(darkTheme = false, highContrast = true) { Probe(seen) }
                }
            }
            compose.waitForIdle()
            assertWithMessage("the scheme is the non-HC scheme")
                .that(seen.scheme)
                .isSameInstanceAs(ClassicLightScheme)
            assertWithMessage("skin id").that(seen.skin.id).isEqualTo("classic-light-hc")
            assertWithMessage("outline")
                .that(seen.skin.tileOutline)
                .isEqualTo(ClassicLightScheme.outline)
            for (exp in 1..17) {
                assertWithMessage("ink($exp)")
                    .that(seen.palette.ink(exp))
                    .isEqualTo(highContrastInk(ClassicLightPalette.fill(exp)))
                assertWithMessage("fill($exp)")
                    .that(seen.palette.fill(exp))
                    .isEqualTo(ClassicLightPalette.fill(exp))
            }

            // The rendered board: FIX_RAMP_5 under the High contrast setting.
            app = true
            rig.awaitReady(compose)
            val hc = HighContrastPalette(ClassicLightPalette)
            val board = renderBoard(rig)
            cellsOf(rig.game).forEachIndexed { index, exp ->
                if (exp == 0) {
                    assertNear(
                        "empty cell $index: 1 dp ink(1) stroke",
                        board.edge(index),
                        hc.ink(1),
                    )
                } else {
                    assertNear(
                        "tile $index (exp $exp): 1.5 dp outline",
                        board.edge(index),
                        ClassicLightScheme.outline,
                    )
                    assertWithMessage("tile $index (exp $exp): a pixel of ink %s", hc.ink(exp))
                        .that(board.hasInkPixel(index, hc.ink(exp)))
                        .isTrue()
                }
            }
        }
    }

    /** F12-AC6 */
    @Test
    fun systemContrastForcesHighContrast() {
        val seen = Seen()
        val source = FakeContrastLevelSource(SYSTEM_HIGH_CONTRAST_MIN)
        val system = FakeContrastLevelSource(SYSTEM_HIGH_CONTRAST_MIN)
        val settings = BASE_SETTINGS.copy(lastBoardSize = 5)
        AppRig(tmp.newFolder(), GoldenFixtures.FIX_RAMP_5, settings, system).use { rig ->
            var app by mutableStateOf(false)
            compose.setContent {
                if (app) {
                    TestApp(rig.container, rig.vm)
                } else {
                    val level by source.contrast.collectAsStateWithLifecycle()
                    GameTheme(darkTheme = false, highContrast = false, systemContrast = level) {
                        Probe(seen)
                    }
                }
            }
            compose.waitForIdle()
            assertWithMessage("contrast 0.5: skin").that(seen.skin.id).isEqualTo("classic-light-hc")
            assertWithMessage("contrast 0.5: outline")
                .that(seen.skin.tileOutline)
                .isEqualTo(ClassicLightScheme.outline)
            assertWithMessage("contrast 0.5: ink(5)")
                .that(seen.palette.ink(5))
                .isEqualTo(Color.Black)
            source.contrast.value = 0f
            compose.waitForIdle()
            assertWithMessage("contrast 0: skin").that(seen.skin.id).isEqualTo("classic-light")
            assertWithMessage("contrast 0: outline").that(seen.skin.tileOutline).isNull()
            assertWithMessage("contrast 0: ink(5)").that(seen.palette.ink(5)).isEqualTo(Color.White)

            // Through the container's source, as App() reads it: the empty cells' D-206 stroke.
            app = true
            rig.awaitReady(compose)
            val empty = cellsOf(rig.game).indexOf(0)
            assertNear(
                "App, contrast 0.5: empty-cell stroke",
                renderBoard(rig).edge(empty),
                Color.Black,
            )
            system.contrast.value = 0f
            assertNear(
                "App, contrast 0: no stroke",
                renderBoard(rig).edge(empty),
                ClassicLightPalette.empty,
            )
        }
    }

    /** F12-AC7 */
    @Test
    fun dynamicChromeLeavesTilesUnchanged() {
        val seen = Seen()
        lateinit var context: Context
        compose.setContent {
            context = LocalContext.current
            GameTheme(darkTheme = false, dynamicChrome = true) { Probe(seen) }
        }
        compose.waitForIdle()
        val dynamic = dynamicLightColorScheme(context)
        assertWithMessage("primary").that(seen.scheme.primary).isEqualTo(dynamic.primary)
        assertWithMessage("outline").that(seen.scheme.outline).isEqualTo(dynamic.outline)
        assertWithMessage("background")
            .that(seen.scheme.background)
            .isEqualTo(ClassicLightPalette.background)
        assertWithMessage("surface")
            .that(seen.scheme.surface)
            .isEqualTo(ClassicLightPalette.background)
        for (exp in 1..17) {
            assertWithMessage("fill($exp)")
                .that(seen.palette.fill(exp))
                .isEqualTo(ClassicLightPalette.fill(exp))
            assertWithMessage("ink($exp)")
                .that(seen.palette.ink(exp))
                .isEqualTo(ClassicLightPalette.ink(exp))
        }
        assertWithMessage("board").that(seen.palette.board).isEqualTo(ClassicLightPalette.board)
        assertWithMessage("empty").that(seen.palette.empty).isEqualTo(ClassicLightPalette.empty)
        assertWithMessage("palette background")
            .that(seen.palette.background)
            .isEqualTo(ClassicLightPalette.background)
        assertWithMessage("title colour (D-189)")
            .that(seen.skin.titleColor)
            .isEqualTo(ClassicLightSkin.titleColor)
    }

    /** F12-AC8 */
    @Test
    // The Pixel 7 qualifier's wide colour gamut does not exist at SDK 24: its size and density.
    @Config(sdk = [24], qualifiers = "w411dp-h914dp-420dpi")
    fun dynamicChromeIgnoredBelowApi31() {
        val seen = Seen()
        compose.setContent { GameTheme(darkTheme = false, dynamicChrome = true) { Probe(seen) } }
        compose.waitForIdle()
        assertWithMessage("scheme").that(seen.scheme).isSameInstanceAs(ClassicLightScheme)
        assertWithMessage("palette").that(seen.palette).isSameInstanceAs(ClassicLightPalette)
    }

    /** F12-AC9 */
    @Test
    fun systemBarIconsFollowEffectiveTheme() {
        lateinit var activity: Activity
        val settings = BASE_SETTINGS.copy(themeMode = ThemeMode.LIGHT)
        AppRig(tmp.newFolder(), GoldenFixtures.FIX_MID_4, settings).use { rig ->
            compose.setContent {
                activity = checkNotNull(LocalActivity.current)
                TestApp(rig.container, rig.vm)
            }
            rig.awaitReady(compose)
            compose.waitForIdle()
            assertWithMessage("Light: light status bars (dark icons)")
                .that(lightStatusBars(activity))
                .isTrue()
            rig.container.settingsRepository.update { it.copy(themeMode = ThemeMode.DARK) }
            compose.waitForIdle()
            assertWithMessage("Dark: dark status bars (light icons)")
                .that(lightStatusBars(activity))
                .isFalse()
        }
    }

    private fun lightStatusBars(activity: Activity): Boolean =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars

    /** The board node's pixels and its geometry (`boardGeometryPx`, the board's own). */
    private fun renderBoard(rig: AppRig): BoardPixels {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(BOARD_TAG).captureToImage().asAndroidBitmap()
        val n = rig.game.state.size.n
        val density = compose.density.density
        return BoardPixels(bitmap, n, density)
    }

    /** Pixel reads of one rendered board (DESIGN_SYSTEM.md §2.7, §4.2). */
    private class BoardPixels(val bitmap: Bitmap, val n: Int, val density: Float) {
        private val g = boardGeometryPx(n, bitmap.width, ClassicLightSkin, density)
        private val side = g.tilePx.roundToInt()

        /**
         * The pixel at the middle of cell [index]'s top edge, one pixel row in: inside both the 1.5
         * dp tile outline and the 1 dp empty-cell stroke, which start at the cell's edge.
         */
        fun edge(index: Int): Int {
            val at = g.cellTopLeft((index % n).toFloat(), (index / n).toFloat())
            return bitmap.getPixel(at.x + side / 2, at.y + 1)
        }

        /** True when the middle of cell [index] holds a pixel of [ink] (a glyph's core). */
        fun hasInkPixel(index: Int, ink: Color): Boolean {
            val at = g.cellTopLeft((index % n).toFloat(), (index / n).toFloat())
            val inset = side / INTERIOR_INSET_DIVISOR
            for (y in at.y + inset until at.y + side - inset) {
                for (x in at.x + inset until at.x + side - inset) {
                    if (distance(bitmap.getPixel(x, y), ink) <= INK_TOLERANCE) return true
                }
            }
            return false
        }
    }

    /** One game in its own container over [folder], with [settings] and [contrast]. */
    private class AppRig(
        folder: File,
        val game: SavedGame,
        settings: Settings,
        contrast: FakeContrastLevelSource = FakeContrastLevelSource(),
    ) : AutoCloseable {
        val container =
            AppContainer(
                files = { name -> File(folder, name) },
                versionCode = TEST_VERSION_CODE,
                clock = FakeClock(),
                seedSource = FixedSeedSource(SEED),
                animatorScale = FakeAnimatorScaleSource(),
                contrastLevel = contrast,
            )
        private val store = ViewModelStore()
        val vm: GameViewModel

        init {
            runBlocking {
                container.seedSettings(settings)
                container.seedGame(game)
            }
            vm =
                ViewModelProvider(store, container.gameViewModelFactory())[
                    GameViewModel::class.java]
        }

        /**
         * Waits for the load (the stores read on real I/O threads, resuming on the main looper).
         */
        fun awaitReady(compose: ComposeContentTestRule) {
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            compose.waitForIdle()
        }

        override fun close() {
            store.clear()
            container.applicationScope.cancel()
        }
    }

    private companion object {
        const val SEED = 0x2048L
        const val LOAD_TIMEOUT_MS = 10_000L
        const val BOARD_TAG = "game:board"

        /** A pixel of the window's margin, which shows the screen background. */
        const val CORNER_PX = 2

        /** Colour channel tolerance of a solid pixel (anti-aliasing stays inside it). */
        const val TOLERANCE = 6
        const val INK_TOLERANCE = 6
        const val INTERIOR_INSET_DIVISOR = 5

        /** Motion Off (no crossfade to wait for), onboarding done, the current version seen. */
        val BASE_SETTINGS =
            Settings(
                themeMode = ThemeMode.LIGHT,
                motion = MotionPreference.OFF,
                lastBoardSize = 4,
                onboardingDone = true,
                lastSeenVersionCode = TEST_VERSION_CODE,
            )

        val PALETTE_CASES =
            listOf(
                PaletteId.CLASSIC to false,
                PaletteId.CLASSIC to true,
                PaletteId.NEON to false,
                PaletteId.NEON to true,
            )

        fun distance(argb: Int, color: Color): Int {
            val c = color.toArgb()
            return maxOf(
                abs(((argb shr 16) and 0xFF) - ((c shr 16) and 0xFF)),
                abs(((argb shr 8) and 0xFF) - ((c shr 8) and 0xFF)),
                abs((argb and 0xFF) - (c and 0xFF)),
            )
        }

        fun assertNear(label: String, argb: Int, expected: Color) {
            assertWithMessage("$label: #%s vs #%s", hex(argb), hex(expected.toArgb()))
                .that(distance(argb, expected))
                .isAtMost(TOLERANCE)
        }

        fun hex(argb: Int): String = "%06X".format(Locale.ROOT, argb and 0xFFFFFF)

        /** The exponents of [game]'s cells in reading order, 0 = empty. */
        fun cellsOf(game: SavedGame): List<Int> =
            List(game.state.board.cellCount) { game.state.board[it] }
    }
}

/** What `GameTheme` provided to its content. */
private class Seen {
    lateinit var palette: TilePalette
    lateinit var skin: TileSkin
    lateinit var scheme: ColorScheme
}

/** Reads what `GameTheme` provides into [seen], after each composition. */
@Composable
private fun Probe(seen: Seen) {
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val scheme = MaterialTheme.colorScheme
    SideEffect {
        seen.palette = palette
        seen.skin = skin
        seen.scheme = scheme
    }
}

/** [content] under a configuration whose night mode is [night] (the `night` qualifier). */
@Composable
private fun Night(night: Boolean, content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val config =
        Configuration(base).apply {
            uiMode =
                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    CompositionLocalProvider(LocalConfiguration provides config, content = content)
}
