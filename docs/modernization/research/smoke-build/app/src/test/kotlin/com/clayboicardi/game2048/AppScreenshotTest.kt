package com.clayboicardi.game2048

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class AppScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Test fun renders() {
        rule.setContent { App() }
        rule.onNodeWithText("Play").assertExists()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/app.png")
    }
}
