package com.clayboicardi.game2048

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The merged manifest and identity resources as Robolectric's package manager reads them
 * (ARCHITECTURE.md §8.1; D-090, D-104, D-111, D-112; FEATURES.md OI-F13): the touchscreen is not
 * required, there is no orientation lock or `configChanges`, the category is game, EmojiCompat's
 * initializer is removed, RTL is supported, the launcher icon is adaptive with a monochrome layer
 * (the API 24–25 vector before that), and the splash theme uses the identity colours.
 */
@RunWith(AndroidJUnit4::class)
class ManifestPolicyTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val packageManager: PackageManager
        get() = context.packageManager

    /** F16-AC9 */
    @Test
    fun touchscreenNotRequired() {
        val info =
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
        val touchscreen =
            info.reqFeatures.orEmpty().singleOrNull {
                it.name == PackageManager.FEATURE_TOUCHSCREEN
            }
        assertWithMessage("the touchscreen feature is declared").that(touchscreen).isNotNull()
        assertWithMessage("FLAG_REQUIRED")
            .that(checkNotNull(touchscreen).flags and FeatureInfo.FLAG_REQUIRED)
            .isEqualTo(0)
    }

    /** F18-AC6 */
    @Test
    fun noOrientationLockOrConfigChanges() {
        val info =
            packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertWithMessage("screenOrientation")
            .that(info.screenOrientation)
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        // The package parser adds CONFIG_MCC | CONFIG_MNC to every activity that does not set
        // android:recreateOnConfigChanges (ParsingPackageUtils.getActivityConfigChanges); the
        // manifest declares no configChanges, so nothing else may be set.
        assertWithMessage("declared configChanges")
            .that(info.configChanges and IMPLICIT_CONFIG_CHANGES.inv())
            .isEqualTo(0)
    }

    /** F18-AC7 */
    @Test
    fun appCategoryIsGame() {
        assertWithMessage("category")
            .that(context.applicationInfo.category)
            .isEqualTo(ApplicationInfo.CATEGORY_GAME)
    }

    /** F27-AC6 */
    @Test
    fun emojiCompatInitializerRemoved() {
        val provider =
            packageManager.getProviderInfo(
                ComponentName(context, STARTUP_PROVIDER),
                PackageManager.GET_META_DATA,
            )
        val keys = provider.metaData?.keySet().orEmpty()
        assertWithMessage("InitializationProvider meta-data").that(keys).doesNotContain(EMOJI_INIT)
    }

    /** F28-AC10 */
    @Test
    fun supportsRtlEnabled() {
        assertWithMessage("FLAG_SUPPORTS_RTL")
            .that(context.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL)
            .isNotEqualTo(0)
    }

    /** F30-AC6 */
    @Test
    fun launcherIconIsAdaptiveWithMonochrome() {
        val icon = context.applicationInfo.loadIcon(packageManager)
        assertWithMessage("the launcher icon")
            .that(icon)
            .isInstanceOf(AdaptiveIconDrawable::class.java)
        assertWithMessage("its monochrome layer")
            .that((icon as AdaptiveIconDrawable).monochrome)
            .isNotNull()
    }

    /** F30-AC7 */
    @Test
    fun splashThemeUsesIdentityColors() {
        for ((night, background) in listOf(false to DAY_BACKGROUND, true to NIGHT_BACKGROUND)) {
            val mode =
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val configuration =
                Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
                }
            val themed =
                ContextThemeWrapper(
                    context.createConfigurationContext(configuration),
                    R.style.Theme_Game2048_Starting,
                )
            val attrs =
                intArrayOf(
                    androidx.core.splashscreen.R.attr.windowSplashScreenBackground,
                    androidx.core.splashscreen.R.attr.windowSplashScreenIconBackgroundColor,
                )
            val values = themed.obtainStyledAttributes(attrs)
            try {
                val name = if (night) "night" else "notnight"
                assertWithMessage("$name windowSplashScreenBackground")
                    .that(values.getColor(0, 0))
                    .isEqualTo(background)
                assertWithMessage("$name windowSplashScreenIconBackgroundColor")
                    .that(values.getColor(1, 0))
                    .isEqualTo(ICON_BACKGROUND)
            } finally {
                values.recycle()
            }
        }
    }

    /** L-AC12 */
    @Config(sdk = [24])
    @Test
    fun legacyLauncherIconOnApi24() {
        val icon = context.applicationInfo.loadIcon(packageManager)
        val density = context.resources.displayMetrics.density
        assertWithMessage("the API 24 icon is not adaptive")
            .that(icon.javaClass.name)
            .doesNotContain("AdaptiveIconDrawable")
        assertWithMessage("intrinsic width (dp)")
            .that(icon.intrinsicWidth / density)
            .isWithin(DP_TOLERANCE)
            .of(LEGACY_ICON_DP)
        assertWithMessage("intrinsic height (dp)")
            .that(icon.intrinsicHeight / density)
            .isWithin(DP_TOLERANCE)
            .of(LEGACY_ICON_DP)
    }

    private companion object {
        /** What the platform handles for every activity unless it opts out. */
        const val IMPLICIT_CONFIG_CHANGES = ActivityInfo.CONFIG_MCC or ActivityInfo.CONFIG_MNC
        const val STARTUP_PROVIDER = "androidx.startup.InitializationProvider"
        const val EMOJI_INIT = "androidx.emoji2.text.EmojiCompatInitializer"

        /** The identity colours (L10N_STORE_LEGAL.md §3.1; DESIGN_SYSTEM.md §8.3). */
        const val DAY_BACKGROUND = 0xFFFAF8EF.toInt()
        const val NIGHT_BACKGROUND = 0xFF1B1916.toInt()
        const val ICON_BACKGROUND = 0xFFE6A11A.toInt()

        /** The `mipmap-anydpi` vector of API 24–25 is 48 × 48 dp. */
        const val LEGACY_ICON_DP = 48f
        const val DP_TOLERANCE = 0.5f
    }
}
