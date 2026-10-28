package com.clayboicardi.game2048

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.testing.assertTouchTargets
import com.clayboicardi.game2048.testing.atfRule
import com.clayboicardi.game2048.testing.captureGolden
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The M0 golden `launch_placeholder.png` (D-147) at the default SDK 36, under ATF and the touch
 * target gate (D-085, D-205). Deleted with its PNG in M3 part 1.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlaceholderScreenshotTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(atfRule(compose))

    @Test
    fun launchPlaceholder() {
        compose.onRoot().captureGolden("launch_placeholder")
        compose.assertTouchTargets()
    }
}
