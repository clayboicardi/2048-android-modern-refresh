package com.clayboicardi.game2048.game.share

import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.game.GameSummary
import com.clayboicardi.game2048.game.board.TileLabels
import com.clayboicardi.game2048.ui.components.formatElapsed
import java.util.Locale

/**
 * The resolved templates of the share text (FEATURES.md F10 item 5; L10N_STORE_LEGAL.md §1.3 table
 * D), so [ShareText] stays pure and runs on the plain JVM: [summary] is `share_summary` with its
 * six `%n$s` placeholders, [moves] and [undos] select the `moves_count` and `undos_count` plural
 * for a count and fill it with the already formatted value, and [noUndo] is `undos_none`. The
 * screen resolves them from the resources of the current locale.
 */
@Immutable
class ShareStrings(
    val summary: String,
    val moves: (count: Int, value: String) -> String,
    val undos: (count: Int, value: String) -> String,
    val noUndo: String,
)

/**
 * The text a Share button sends (D-008, D-200; FEATURES.md F10): the summary line, then N lines of
 * N emoji in reading order, joined with `\n` and with no trailing newline. Every number is a game
 * numeral with Latin digits and no grouping (D-103, L10N_STORE_LEGAL.md §1.10): the size `"$n×$n"`,
 * the score and the counts by `toString()`, the best tile as its full decimal (exact above 2^62,
 * `TileLabels.fullDecimal`) and the time by `formatElapsed`. Pure.
 */
object ShareText {
    /** The share text of [summary] with [strings]. */
    fun build(summary: GameSummary, strings: ShareStrings): String {
        val n = summary.size.n
        val undos =
            if (summary.undos == 0) {
                strings.noUndo
            } else {
                strings.undos(summary.undos, summary.undos.toString())
            }
        val line =
            String.format(
                Locale.ROOT,
                strings.summary,
                "$n$TIMES$n",
                summary.score.toString(),
                TileLabels.fullDecimal(summary.bestTileExp),
                strings.moves(summary.moves, summary.moves.toString()),
                formatElapsed(summary.elapsedMs),
                undos,
            )
        return buildString {
            append(line)
            for (row in 0 until n) {
                append('\n')
                for (col in 0 until n) append(emoji(summary.cells[row * n + col]))
            }
        }
    }

    /**
     * The emoji of a cell of exponent [exp] (D-008; FEATURES.md F10 table): empty ⬜, 2–8 🟫, 16–64
     * 🟨, 128–256 🟧, 512 🟥, 1024 🟪, 2048 🟦, 4096 and above ⬛. Each is one code point.
     */
    fun emoji(exp: Int): String =
        when {
            exp <= 0 -> EMPTY
            exp <= EXP_8 -> BROWN
            exp <= EXP_64 -> YELLOW
            exp <= EXP_256 -> ORANGE
            exp == EXP_512 -> RED
            exp == EXP_1024 -> PURPLE
            exp == EXP_2048 -> BLUE
            else -> BLACK
        }

    /** U+00D7 MULTIPLICATION SIGN, of the size label `"$n×$n"` (L10N_STORE_LEGAL.md §1.3). */
    private const val TIMES = '×'

    private const val EXP_8 = 3
    private const val EXP_64 = 6
    private const val EXP_256 = 8
    private const val EXP_512 = 9
    private const val EXP_1024 = 10
    private const val EXP_2048 = 11

    private val EMPTY = codePoint(0x2B1C)
    private val BROWN = codePoint(0x1F7EB)
    private val YELLOW = codePoint(0x1F7E8)
    private val ORANGE = codePoint(0x1F7E7)
    private val RED = codePoint(0x1F7E5)
    private val PURPLE = codePoint(0x1F7EA)
    private val BLUE = codePoint(0x1F7E6)
    private val BLACK = codePoint(0x2B1B)

    private fun codePoint(cp: Int): String = String(Character.toChars(cp))
}
