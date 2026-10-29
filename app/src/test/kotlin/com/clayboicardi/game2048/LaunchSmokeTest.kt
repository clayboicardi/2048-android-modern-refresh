package com.clayboicardi.game2048

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.robolectric.shadows.ShadowToast

/**
 * Launches `MainActivity` at minSdk and targetSdk (D-146) and captures nothing: the activity
 * reaches RESUMED with the Game content composed (the board node and the "2048" title). The title
 * keeps D-083's 4.5:1 UI-text contrast against the window background in light mode and, after a
 * recreation in night mode, in dark mode.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A phone window (Pixel 7 size), whose STACKED arrangement shows the FULL header with the title.
// The Pixel7 qualifier's wide colour gamut does not exist at SDK 24, so the size is spelled out.
@Config(sdk = [24, 37], qualifiers = "w411dp-h914dp-420dpi")
class LaunchSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** F30-AC5 */
    @Test
    fun launchesOnMinAndTargetSdk() {
        assertThat(compose.activityRule.scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        compose.onNodeWithTag(BOARD).assertExists()
        assertTitleContrast("light")

        RuntimeEnvironment.setQualifiers("+night")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag(BOARD).assertExists()
        assertTitleContrast("dark")
    }

    /** F21-AC1 */
    @Test
    fun launchesIntoGameWithoutTopBar() {
        compose.onNodeWithTag(BOARD).assertExists()
        compose
            .onNodeWithText(TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onAllNodesWithContentDescription(NAVIGATE_UP).assertCountEquals(0)
    }

    /** F22-AC7 */
    @Test
    fun noToastOnLaunchOrRecreate() {
        compose.onNodeWithTag(BOARD).assertExists()
        assertThat(ShadowToast.getLatestToast()).isNull()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag(BOARD).assertExists()
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    /** The title's resolved text color against the window background color (D-083). */
    private fun assertTitleContrast(mode: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(TITLE).fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val ink = layouts.single().layoutInput.style.color
        assertWithMessage("$mode: title text color").that(ink.isSpecified).isTrue()
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

        /** The board node (`BoardView`). */
        const val BOARD = "game:board"

        /** `app_name`, the Game screen's title. */
        const val TITLE = "2048"

        /** The spoken label of a top app bar's back arrow (`navigate_up`, M4). */
        const val NAVIGATE_UP = "Navigate up"
    }
}
