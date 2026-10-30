package com.clayboicardi.game2048.game.header

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The long-score fit chain `scoreFit` and `scoreLines` (DESIGN_SYSTEM.md §7.2; D-084). */
class ScoreFitTest {
    /** F17-AC22 */
    @Test
    fun fallbackChainTable() {
        for (case in CASES) {
            val fit = scoreFit(case.digits, case.width, case.lead, case.advance14, case.ownRow)
            val want = case.expected
            assertWithMessage("${case.name}: layout").that(fit.layout).isEqualTo(want.layout)
            assertWithMessage("${case.name}: chipSlot")
                .that(fit.chipSlot)
                .isWithin(EPSILON)
                .of(want.chipSlot)
            assertWithMessage("${case.name}: valueWidth")
                .that(fit.valueWidth)
                .isWithin(EPSILON)
                .of(want.valueWidth)
            assertWithMessage("${case.name}: digitsPerLine")
                .that(fit.digitsPerLine)
                .isEqualTo(want.digitsPerLine)
        }

        for ((text, perLine, expected) in LINES) {
            assertWithMessage("scoreLines(\"$text\", $perLine)")
                .that(scoreLines(text, perLine))
                .containsExactlyElementsIn(expected)
                .inOrder()
        }
    }

    private data class Case(
        val name: String,
        val digits: Int,
        val width: Float,
        val lead: Float?,
        val advance14: Float,
        val expected: ScoreFit,
        val ownRow: Boolean = true,
    )

    private companion object {
        const val EPSILON = 1e-3f
        const val A2 = 17.29f
        const val A1 = 9.31f
        const val FULL_2 = 100f
        const val COMPACT_2 = 115f
        const val FULL_1 = 80f
        const val COMPACT_1 = 98f
        const val RAIL = 201.3f
        val INLINE = ChipLayout.INLINE
        val OWN = ChipLayout.OWN_ROW

        /** The F17-AC22 vectors (DESIGN_SYSTEM.md §7.2). */
        val CASES =
            listOf(
                // fontScale 2.0: advance14 = 17.29 dp; FULL lead 100, COMPACT lead 115.
                Case("2.0, 9 digits, FULL 328", 9, 328f, FULL_2, A2, stacked(328f, 304f, 17)),
                Case("2.0, 10 digits, FULL 328", 10, 328f, FULL_2, A2, stacked(328f, 304f, 17)),
                Case("2.0, 9 digits, COMPACT 328", 9, 328f, COMPACT_2, A2, stacked(328f, 304f, 17)),
                Case(
                    "2.0, 10 digits, COMPACT 328",
                    10,
                    328f,
                    COMPACT_2,
                    A2,
                    stacked(328f, 304f, 17),
                ),
                Case("2.0, 9 digits, rail 160", 9, 160f, null, A2, stacked(160f, 136f, 7), false),
                Case("2.0, 10 digits, rail 160", 10, 160f, null, A2, stacked(160f, 136f, 7), false),
                Case(
                    "2.0, 9 digits, rail 201.3",
                    9,
                    RAIL,
                    null,
                    A2,
                    stacked(RAIL, 177.3f, 10),
                    false,
                ),
                Case(
                    "2.0, 10 digits, rail 201.3",
                    10,
                    RAIL,
                    null,
                    A2,
                    stacked(RAIL, 177.3f, 10),
                    false,
                ),
                Case("2.0, 9 digits, panel 280", 9, 280f, null, A2, stacked(280f, 256f, 14)),
                Case(
                    "2.0, 19 digits, COMPACT 220",
                    19,
                    220f,
                    COMPACT_2,
                    A2,
                    stacked(220f, 196f, 11),
                ),
                // fontScale 1.0: advance14 = 9.31 dp; FULL lead 80, COMPACT lead 98.
                Case("1.0, 9 digits, FULL 328", 9, 328f, FULL_1, A1, fit(INLINE, 116f, 64f, 9)),
                Case("1.0, 10 digits, FULL 328", 10, 328f, FULL_1, A1, fit(OWN, 160f, 136f, 14)),
                Case(
                    "1.0, 9 digits, COMPACT 328",
                    9,
                    328f,
                    COMPACT_1,
                    A1,
                    fit(OWN, 160f, 136f, 14),
                ),
                Case(
                    "1.0, 10 digits, COMPACT 328",
                    10,
                    328f,
                    COMPACT_1,
                    A1,
                    fit(OWN, 160f, 136f, 14),
                ),
                Case(
                    "1.0, 5 digits, COMPACT 328",
                    5,
                    328f,
                    COMPACT_1,
                    A1,
                    fit(INLINE, 107f, 64f, 8),
                ),
                Case("1.0, 5 digits, COMPACT 220", 5, 220f, COMPACT_1, A1, fit(OWN, 106f, 82f, 8)),
            )

        /** `scoreLines` splits, counted from the last digit. */
        val LINES =
            listOf(
                Triple("999999999", 7, listOf("9999", "99999")),
                Triple("9999999999", 7, listOf("99999", "99999")),
                Triple("9223372036854775807", 11, listOf("922337203", "6854775807")),
                Triple("999999999", 17, listOf("999999999")),
            )

        fun stacked(slot: Float, value: Float, perLine: Int) =
            ScoreFit(ChipLayout.STACKED, slot, value, perLine)

        fun fit(layout: ChipLayout, slot: Float, value: Float, perLine: Int) =
            ScoreFit(layout, slot, value, perLine)
    }
}
