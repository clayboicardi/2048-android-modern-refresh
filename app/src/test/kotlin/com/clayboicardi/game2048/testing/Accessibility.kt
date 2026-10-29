// The only opt-in in the test source set (D-174): every Roborazzi ATF API is
// @ExperimentalRoborazziApi in 1.77.0.
@file:OptIn(ExperimentalRoborazziApi::class)

package com.clayboicardi.game2048.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.AccessibilityCheckAfterTestStrategy
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset

/** D-085: ATF after every test, LATEST preset, Error level, on the main window. */
fun atfRule(compose: ComposeTestRule): RoborazziRule =
    RoborazziRule(
        composeRule = compose,
        captureRoot = compose.onRoot(),
        options =
            RoborazziRule.Options(
                roborazziAccessibilityOptions = atfOptions(),
                accessibilityCheckStrategy = AccessibilityCheckAfterTestStrategy(),
            ),
    )

/**
 * D-085 on the window of this node while it is open, with [atfRule]'s preset and level: a dialog or
 * a sheet (`onNode(isDialog())`), which the after-test check on the main window never sees.
 */
fun SemanticsNodeInteraction.assertAccessible() {
    checkRoboAccessibility(atfOptions())
}

/** The LATEST preset at the Error level (D-085). */
private fun atfOptions() =
    RoborazziATFAccessibilityCheckOptions(
        checker = RoborazziATFAccessibilityChecker(preset = AccessibilityCheckPreset.LATEST),
        failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
    )

/**
 * D-084: every clickable has a touch area of at least 48 x 48 dp, and the touch areas of two
 * different clickables never overlap (Compose extends small targets to 48 dp, so overlap is the
 * failure that matters). Nested clickables (one an ancestor of the other) are exempt.
 */
fun ComposeTestRule.assertTouchTargets() {
    touchTargets(hasClickAction())
}

/**
 * [assertTouchTargets] for the clickables of the open dialog or sheet window (`isDialog()`), in
 * that window's coordinates, while the main window's are left to [assertTouchTargets].
 */
fun ComposeTestRule.assertDialogTouchTargets() {
    val checked = touchTargets(hasClickAction() and hasAnyAncestor(isDialog()))
    check(checked > 0) { "No clickable in an open dialog window" }
}

/** Checks the clickables that [matcher] selects; returns how many there were. */
private fun ComposeTestRule.touchTargets(matcher: SemanticsMatcher): Int {
    val minPx = with(density) { 48.dp.toPx() } - 0.5f
    val nodes = onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
    val tooSmall =
        nodes
            .filter { it.touchBoundsInRoot.width < minPx || it.touchBoundsInRoot.height < minPx }
            .map { "too small: ${it.id} ${it.touchBoundsInRoot}" }
    val problems = tooSmall + overlaps(nodes)
    check(problems.isEmpty()) { "Touch-target violations: " + problems.joinToString() }
    return nodes.size
}

private fun overlaps(nodes: List<SemanticsNode>): List<String> = nodes.flatMapIndexed { i, a ->
    nodes.drop(i + 1).mapNotNull { b ->
        val o = a.touchBoundsInRoot.intersect(b.touchBoundsInRoot)
        val nested = a.isAncestorOf(b) || b.isAncestorOf(a)
        if (!nested && o.width > 0.5f && o.height > 0.5f) "overlap: ${a.id}/${b.id} $o" else null
    }
}

private fun SemanticsNode.isAncestorOf(other: SemanticsNode): Boolean =
    generateSequence(other.parent) { it.parent }.any { it.id == id }
