package com.clayboicardi.game2048.game.share

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.GameSummary
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

/**
 * `ShareText` on the plain JVM with the English templates of L10N_STORE_LEGAL.md §1.3 table D
 * (FEATURES.md F10; D-008, D-103); `GameScreenTest` covers resolving them from resources.
 */
class ShareTextTest {
    /** F10-AC1 */
    @Test
    fun englishSummaryAndGridForOverFixture() {
        val text = ShareText.build(summaryOf(GoldenFixtures.FIX_OVER_4), ENGLISH)

        assertWithMessage("FIX_OVER_4's share text")
            .that(text)
            .isEqualTo(
                "2048 · 4×4 · 3012 · best tile 128 · 302 moves · 6:41 · No undo\n" +
                    "$BROWN$BROWN$BROWN$BROWN\n" +
                    "$BROWN$BROWN$BROWN$BROWN\n" +
                    "$YELLOW$YELLOW$YELLOW$ORANGE\n" +
                    "$BROWN$BROWN$BROWN$BROWN",
            )
    }

    /** F10-AC2 */
    @Test
    fun emojiBucketsPerExponent() {
        val expected =
            listOf(EMPTY) +
                List(3) { BROWN } +
                List(3) { YELLOW } +
                List(2) { ORANGE } +
                listOf(RED, PURPLE, BLUE) +
                List(6) { BLACK }
        for (exp in 0..17) {
            val emoji = ShareText.emoji(exp)
            assertWithMessage("exponent $exp").that(emoji).isEqualTo(expected[exp])
            assertWithMessage("exponent $exp: one code point")
                .that(emoji.codePointCount(0, emoji.length))
                .isEqualTo(1)
        }

        // One cell with each exponent 0..17 on a 5×5 board, then 7 empty cells.
        val cells = (0..17).toList() + List(7) { 0 }
        val text = ShareText.build(summary(n = 5, cells = cells), ENGLISH)
        val grid = text.lines().drop(1).joinToString("")
        assertWithMessage("the grid in reading order")
            .that(grid)
            .isEqualTo((expected + List(7) { EMPTY }).joinToString(""))
    }

    /** F10-AC3 */
    @Test
    fun gridShapeMatchesBoardSize() {
        for ((name, game) in
            listOf(
                "FIX_MID_3" to GoldenFixtures.FIX_MID_3,
                "FIX_DENSE_8" to GoldenFixtures.FIX_DENSE_8,
            )) {
            val n = game.state.size.n
            val text = ShareText.build(summaryOf(game), ENGLISH)
            val lines = text.split('\n')

            assertWithMessage("$name: lines").that(lines).hasSize(1 + n)
            assertWithMessage("$name: summary line")
                .that(lines.first())
                .startsWith("2048 · $n×$n · ${game.state.score} · ")
            for ((row, line) in lines.drop(1).withIndex()) {
                assertWithMessage("$name: row $row emoji")
                    .that(line.codePointCount(0, line.length))
                    .isEqualTo(n)
            }
            assertWithMessage("$name: no trailing newline").that(text).doesNotContain("\n\n")
            assertWithMessage("$name: ends with the last row's emoji")
                .that(text.last())
                .isNotEqualTo('\n')
        }
    }

    /** F10-AC4 */
    @Test
    fun latinDigitsUnderPersianLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa"))
            val text =
                ShareText.build(
                    summary(n = 4, cells = List(16) { 1 }, score = 20316, moves = 1024)
                        .copy(elapsedMs = 3_725_000),
                    ENGLISH,
                )

            val nonLatin = text.filter { Character.isDigit(it) && it !in '0'..'9' }
            assertWithMessage("non-Latin digits in \"$text\"").that(nonLatin).isEmpty()
            assertWithMessage("the score").that(text).contains("20316")
            assertWithMessage("the moves").that(text).contains("1024 moves")
            assertWithMessage("the time").that(text).contains("1:02:05")
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** F10-AC5 */
    @Test
    fun undoPhrasing() {
        for ((undos, ending) in listOf(0 to "No undo", 1 to "1 undo", 3 to "3 undos")) {
            val text =
                ShareText.build(summary(n = 4, cells = List(16) { 0 }, undos = undos), ENGLISH)
            assertWithMessage("undoCount $undos").that(text.lines().first()).endsWith(" · $ending")
        }
    }

    private companion object {
        /** `share_summary`, `moves_count`, `undos_count` and `undos_none` in English. */
        val ENGLISH =
            ShareStrings(
                summary = "2048 · %1\$s · %2\$s · best tile %3\$s · %4\$s · %5\$s · %6\$s",
                moves = { count, value -> plural(count, value, "move", "moves") },
                undos = { count, value -> plural(count, value, "undo", "undos") },
                noUndo = "No undo",
            )

        val EMPTY = codePoint(0x2B1C)
        val BROWN = codePoint(0x1F7EB)
        val YELLOW = codePoint(0x1F7E8)
        val ORANGE = codePoint(0x1F7E7)
        val RED = codePoint(0x1F7E5)
        val PURPLE = codePoint(0x1F7EA)
        val BLUE = codePoint(0x1F7E6)
        val BLACK = codePoint(0x2B1B)

        /** English `one` / `other` selection of a `%1$s` plural. */
        fun plural(count: Int, value: String, one: String, other: String): String =
            if (count == 1) "$value $one" else "$value $other"

        fun codePoint(cp: Int): String = String(Character.toChars(cp))

        /** The summary the ViewModel builds for [game] (`GameViewModel.summary`). */
        fun summaryOf(game: SavedGame): GameSummary {
            val state = game.state
            return GameSummary(
                size = state.size,
                score = state.score,
                best = game.bestAtStart,
                bestTileExp = state.board.maxExp(),
                moves = state.moveCount,
                elapsedMs = game.elapsedMs,
                undos = state.undoCount,
                cells = List(state.board.cellCount) { state.board[it] }.toImmutableList(),
            )
        }

        fun summary(
            n: Int,
            cells: List<Int>,
            score: Long = 0,
            moves: Int = 0,
            undos: Int = 0,
        ): GameSummary =
            GameSummary(
                size = BoardSize.of(n),
                score = score,
                best = score,
                bestTileExp = cells.max(),
                moves = moves,
                elapsedMs = 0,
                undos = undos,
                cells = cells.toImmutableList(),
            )
    }
}
