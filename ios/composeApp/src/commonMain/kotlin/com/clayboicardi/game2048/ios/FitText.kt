package com.clayboicardi.game2048.ios

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** Average advance of a bold digit or capital, in ems (deliberately generous). */
private const val CHAR_EM = 0.66f

/**
 * Single-line text that honours the reader's text-size setting up to [maxSize] but shrinks to fit
 * the width it is given, so a large accessibility size or a long score never pushes a sibling off
 * screen or clips its own digits.
 */
@Composable
fun FitText(
    text: String,
    maxSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val fontScale = LocalDensity.current.fontScale
        // Rendered width in dp ≈ chars × CHAR_EM × size(sp) × fontScale.
        val size =
            if (constraints.hasBoundedWidth) {
                val fit = maxWidth.value / (max(text.length, 1) * CHAR_EM * fontScale)
                min(maxSize.value, fit).sp
            } else {
                maxSize
            }
        Text(
            text = text,
            color = color,
            fontWeight = fontWeight,
            fontSize = size,
            maxLines = 1,
            softWrap = false,
        )
    }
}
