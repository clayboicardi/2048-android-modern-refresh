package com.clayboicardi.game2048.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.captureRoboImage

/** Writes or verifies `app/src/test/screenshots/<name>.png` (D-147, QUALITY.md §1.9). */
fun SemanticsNodeInteraction.captureGolden(name: String) {
    require(Regex("[a-z0-9][A-Za-z0-9_.-]*").matches(name)) { "Bad golden name: $name" }
    captureRoboImage("src/test/screenshots/$name.png")
}
