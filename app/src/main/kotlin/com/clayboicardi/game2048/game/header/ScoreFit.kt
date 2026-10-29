package com.clayboicardi.game2048.game.header

import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.ui.theme.GameDimens
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * How a header, rail or panel lays out its Score and Best chips (DESIGN_SYSTEM.md §6.1, §7.2;
 * D-084): beside the title or size chip, on a row of their own side by side, or stacked.
 */
enum class ChipLayout {
    INLINE,
    OWN_ROW,
    STACKED,
}

/**
 * The fit of the two chips of one header (DESIGN_SYSTEM.md §7.2): their [layout], the most width
 * one chip may take ([chipSlot]), the width `ScoreValue` fits the value into ([valueWidth]) and how
 * many digits one line holds ([digitsPerLine], below the digit count only for a STACKED value that
 * wraps). Widths in dp.
 */
@Immutable
data class ScoreFit(
    val layout: ChipLayout,
    val chipSlot: Float,
    val valueWidth: Float,
    val digitsPerLine: Int,
)

/**
 * The long-score fit chain of D-084 (DESIGN_SYSTEM.md §7.2; pure, F17-AC22). Score and Best share
 * one slot, so one fit serves both chips of a header. Widths in dp:
 * - [digits]: the digit count of the larger true value (1 while Loading shows "–");
 * - [width]: what the chips are laid out in (the board for the FULL header, `w0` for the COMPACT
 *   one, the rail, the panel);
 * - [lead]: what shares the chips' row in INLINE (the title's "2048" at its 32 sp minimum, or the
 *   COMPACT size chip with More and their gap, D-249); `null` in the rail and the panel;
 * - [advance14]: one digit's advance in the score numerals at 14 sp, at the current density and
 *   font scale;
 * - [ownRow]: false in the THREE_COLUMN stats rail, whose chips always stack.
 *
 * First match wins, with `fits(slot) = slot ≥ 88 and digits · advance14 ≤ slot − 24`: INLINE with
 * `chipSlot = (width − lead − 16) / 2` and `valueWidth = 64`; OWN_ROW with `chipSlot = (width − 8)
 * / 2`; otherwise STACKED with `chipSlot = width`. Then `digitsPerLine = max(1, ⌊(chipSlot − 24) /
 * advance14⌋)`.
 */
fun scoreFit(
    digits: Int,
    width: Float,
    lead: Float?,
    advance14: Float,
    ownRow: Boolean = true,
): ScoreFit {
    val padding = GameDimens.Space12.value * 2
    val minChip = GameDimens.ScoreChipMinWidth.value
    val gap = GameDimens.Space8.value
    val fits = { slot: Float -> slot >= minChip && digits * advance14 <= slot - padding }
    val inline = lead?.let { (width - it - 2 * gap) / 2 }
    val own = (width - gap) / 2
    val (layout, slot, value) =
        when {
            inline != null && fits(inline) -> Triple(ChipLayout.INLINE, inline, minChip - padding)
            ownRow && fits(own) -> Triple(ChipLayout.OWN_ROW, own, own - padding)
            else -> Triple(ChipLayout.STACKED, width, width - padding)
        }
    val perLine = max(1, floor((slot - padding) / advance14).toInt())
    return ScoreFit(layout, slot, value, perLine)
}

/**
 * [text]'s digits cut into lines of at most [digitsPerLine] (DESIGN_SYSTEM.md §7.2): `L = ⌈d /
 * digitsPerLine⌉` lines of `⌈d / L⌉` digits counted from the end, so only the first line can be
 * shorter. Lines are cut between digits only, most significant first.
 */
fun scoreLines(text: String, digitsPerLine: Int): List<String> {
    val perLine = max(1, digitsPerLine)
    if (text.length <= perLine) return listOf(text)
    val lines = ceil(text.length / perLine.toDouble()).toInt()
    val each = ceil(text.length / lines.toDouble()).toInt()
    val out = ArrayDeque<String>()
    var end = text.length
    while (end > 0) {
        val start = max(0, end - each)
        out.addFirst(text.substring(start, end))
        end = start
    }
    return out.toList()
}
