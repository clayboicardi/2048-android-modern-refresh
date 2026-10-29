package com.clayboicardi.game2048.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.StatsKey
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.overlay.WIN_CARD_TAG
import com.clayboicardi.game2048.screenTag
import com.clayboicardi.game2048.screenshots.GoldenRig
import com.clayboicardi.game2048.screenshots.awaitLoaded
import com.clayboicardi.game2048.screenshots.goldenSettings
import com.clayboicardi.game2048.screenshots.openFromMenu
import com.clayboicardi.game2048.screenshots.recordsOf
import com.clayboicardi.game2048.stats.STATS_HERO_TAG
import com.clayboicardi.game2048.ui.theme.GameTheme
import com.clayboicardi.game2048.ui.theme.resolveTilePalette
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The store images of D-115 (L10N_STORE_LEGAL.md §2.5; DESIGN_SYSTEM.md §8.4–§8.6, §10.6), one
 * method per file, written into `System.getProperty("game2048.storeAssetsDir")`
 * (`fastlane/metadata/android/en-US/images/`). Gradle runs this class only with
 * `-PrecordStoreAssets=true` (`ARCHITECTURE.md` §2.6, §2.11). Conditions: SDK 36, locale en-US,
 * Motion Off, dynamic color off, onboarding done, the current version seen, the bundled font and
 * the static fixtures of DESIGN_SYSTEM.md §10.3. A composable capture runs inside a `"store:frame"`
 * box of the exact size, so the pixels are the qualifier's density times that size; every image is
 * written by [OpaquePngWriter] (8-bit RGB, no alpha, D-190), composited over the frame's opaque
 * background. The images are not goldens: nothing compares them, and a second run reproduces them
 * byte for byte (L-AC9).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAssetsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * F30-AC9, L-AC9
     *
     * The 512 × 512 icon (§8.4, L10N_STORE_LEGAL.md §3.7): the adaptive layers, no mask.
     */
    @Test
    fun icon() {
        val fg = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
        val background = ContextCompat.getColor(context, R.color.ic_launcher_background)
        val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(background)
            // The 108-unit layer at 768 px: its central 72 units fill 512 px, as a launcher shows.
            fg.setBounds(
                -ICON_INSET_PX,
                -ICON_INSET_PX,
                ICON_PX + ICON_INSET_PX,
                ICON_PX + ICON_INSET_PX,
            )
            fg.draw(this)
        }
        OpaquePngWriter.write(bmp, background, output("icon.png"))
    }

    /**
     * F30-AC9, L-AC9
     *
     * The 1024 × 500 feature graphic (§8.5): `FeatureGraphic` over `FIX_FEATURE`, Classic light.
     */
    @Test
    @Config(qualifiers = "en-rUS-w512dp-h250dp-xhdpi")
    fun featureGraphic() {
        val palette = PaletteId.CLASSIC
        compose.setContent {
            Frame(DpSize(FrameWidth, FrameHeight)) {
                GameTheme(darkTheme = false, palette = palette, motion = MotionPreference.OFF) {
                    FeatureGraphic(GoldenFixtures.FIX_FEATURE.state.board)
                }
            }
        }
        compose.waitForIdle()
        capture("featureGraphic.png", background(palette, dark = false))
    }

    /**
     * F30-AC9, L-AC9
     *
     * Phone 1 (§8.6): `FIX_MID_4`, Classic light.
     */
    @Test
    @Config(qualifiers = PHONE)
    fun phone1() {
        game(GoldenFixtures.FIX_MID_4, PHONE_SIZE, "phoneScreenshots/1.png")
    }

    /**
     * F30-AC9, L-AC9
     *
     * Phone 2 (§8.6): `FIX_WON_4`, the win card in its Compact layout, Classic light.
     */
    @Test
    @Config(qualifiers = PHONE)
    fun phone2() {
        game(GoldenFixtures.FIX_WON_4, PHONE_SIZE, "phoneScreenshots/2.png", WIN_CARD_TAG)
    }

    /**
     * F30-AC9, L-AC9
     *
     * Phone 3 (§8.6): `FIX_MID_4`, Neon dark.
     */
    @Test
    @Config(qualifiers = PHONE_NIGHT)
    fun phone3() {
        game(GoldenFixtures.FIX_MID_4, PHONE_SIZE, "phoneScreenshots/3.png", dark = NEON_DARK)
    }

    /**
     * F30-AC9, L-AC9
     *
     * Phone 4 (§8.6): Statistics, the 4×4 tab, `FIX_RECORDS`, Classic light.
     */
    @Test
    @Config(qualifiers = PHONE)
    fun phone4() {
        val game = GoldenFixtures.FIX_MID_4
        GoldenRig(tmp.newFolder(), game, records = GoldenFixtures.FIX_RECORDS).use { rig ->
            show(rig, PHONE_SIZE)
            compose.openFromMenu(StatsKey)
            compose.onNodeWithTag(screenTag(StatsKey)).assertExists()
            // StatsViewModel's ui is null until the records and every size's game are read.
            compose.awaitLoaded {
                compose.onAllNodesWithTag(STATS_HERO_TAG).fetchSemanticsNodes().isNotEmpty()
            }
            capture("phoneScreenshots/4.png", background(PaletteId.CLASSIC, dark = false))
        }
    }

    /**
     * F30-AC9, L-AC9
     *
     * The seven-inch screenshot (§8.6): `FIX_MID_4`, Classic light, side panel.
     */
    @Test
    @Config(qualifiers = SEVEN_INCH)
    fun sevenInch() {
        game(GoldenFixtures.FIX_MID_4, SEVEN_INCH_SIZE, "sevenInchScreenshots/1.png")
    }

    /**
     * F30-AC9, L-AC9
     *
     * The ten-inch screenshot (§8.6): `FIX_MID_4`, Neon dark, side panel.
     */
    @Test
    @Config(qualifiers = TEN_INCH)
    fun tenInch() {
        game(GoldenFixtures.FIX_MID_4, TEN_INCH_SIZE, "tenInchScreenshots/1.png", dark = NEON_DARK)
    }

    /**
     * Captures the Game screen over [game] in a frame of [size] as [file], once the game has loaded
     * (and the node tagged [tag] exists, when given); [dark] changes the §10.2 settings.
     */
    private fun game(
        game: SavedGame,
        size: DpSize,
        file: String,
        tag: String? = null,
        dark: ((Settings) -> Settings)? = null,
    ) {
        val settings = (dark ?: { it })(goldenSettings(game))
        GoldenRig(tmp.newFolder(), game, settings, recordsOf(game)).use { rig ->
            show(rig, size)
            tag?.let { compose.onNodeWithTag(it).assertExists() }
            val isDark = settings.themeMode == ThemeMode.DARK
            capture(file, background(settings.palette, isDark))
        }
    }

    private fun show(rig: GoldenRig, size: DpSize) {
        compose.setContent { Frame(size) { rig.Content() } }
        rig.awaitReady(compose)
        compose.waitForIdle()
    }

    /** The exact-size capture box (L10N_STORE_LEGAL.md §2.5). */
    @Composable
    private fun Frame(size: DpSize, content: @Composable () -> Unit) {
        Box(Modifier.requiredSize(size).testTag(FRAME_TAG)) { content() }
    }

    /** Captures the `"store:frame"` node and writes it over [background] to [file]. */
    private fun capture(file: String, background: Int) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(FRAME_TAG).captureToImage().asAndroidBitmap()
        val out = output(file)
        OpaquePngWriter.write(bitmap, background, out)
        assertWithMessage("$file was written").that(out.length()).isGreaterThan(0L)
    }

    private fun output(name: String): File {
        val dir =
            checkNotNull(System.getProperty(STORE_ASSETS_DIR)) {
                "$STORE_ASSETS_DIR is not set: run with -PrecordStoreAssets=true"
            }
        return File(dir, name).apply { parentFile?.mkdirs() }
    }

    /** The frame's opaque background: the palette background, which the screen fills. */
    private fun background(palette: PaletteId, dark: Boolean): Int =
        resolveTilePalette(palette, dark).palette.background.toArgb()

    private companion object {
        const val STORE_ASSETS_DIR = "game2048.storeAssetsDir"
        const val FRAME_TAG = "store:frame"

        const val ICON_PX = 512
        const val ICON_INSET_PX = 128

        /** 1080 × 1920 px; the dark rows add the night qualifier (DESIGN_SYSTEM.md §10.2). */
        const val PHONE = "en-rUS-w360dp-h640dp-xxhdpi"
        const val PHONE_NIGHT = "en-rUS-w360dp-h640dp-night-xxhdpi"
        val PHONE_SIZE = DpSize(360.dp, 640.dp)

        /** 2134 × 1200 px. */
        const val SEVEN_INCH = "en-rUS-w1067dp-h600dp-land-xhdpi"
        val SEVEN_INCH_SIZE = DpSize(1067.dp, 600.dp)

        /** 2560 × 1440 px, Neon dark. */
        const val TEN_INCH = "en-rUS-w1280dp-h720dp-land-night-xhdpi"
        val TEN_INCH_SIZE = DpSize(1280.dp, 720.dp)

        /** Neon Arcade in dark mode (`themeMode = DARK` with the night qualifier). */
        val NEON_DARK: (Settings) -> Settings = {
            it.copy(themeMode = ThemeMode.DARK, palette = PaletteId.NEON)
        }
    }
}
