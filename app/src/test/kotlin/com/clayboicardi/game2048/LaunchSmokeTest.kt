package com.clayboicardi.game2048

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Launches `MainActivity` at minSdk and targetSdk (D-146) and captures nothing. M0 placeholder form
 * of F30-AC5: the activity reaches RESUMED and shows the placeholder text. The text keeps D-083's
 * 4.5:1 UI-text contrast against the window background in light mode and, after a recreation in
 * night mode, in dark mode.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [24, 37])
class LaunchSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** F30-AC5 */
    @Test
    fun launchesOnMinAndTargetSdk() {
        assertThat(compose.activityRule.scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("placeholder").assertTextEquals("Settings")
        assertPlaceholderContrast("light")

        RuntimeEnvironment.setQualifiers("+night")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("placeholder").assertTextEquals("Settings")
        assertPlaceholderContrast("dark")
    }

    /** The placeholder's resolved text color against the window background color (D-083). */
    private fun assertPlaceholderContrast(mode: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithTag("placeholder").fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val ink = layouts.single().layoutInput.style.color
        assertWithMessage("$mode: placeholder text color").that(ink.isSpecified).isTrue()
        var background = 0
        compose.activityRule.scenario.onActivity {
            background = (it.window.decorView.background as ColorDrawable).color
        }
        val ratio = ColorUtils.calculateContrast(ink.toArgb(), background)
        assertWithMessage("$mode: contrast of %s on #%s", ink, Integer.toHexString(background))
            .that(ratio)
            .isAtLeast(MIN_TEXT_CONTRAST)
    }

    private companion object {
        /** D-083: UI text. */
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
