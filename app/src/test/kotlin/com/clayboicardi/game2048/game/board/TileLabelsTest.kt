package com.clayboicardi.game2048.game.board

import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.ui.theme.ClassicLightSkin
import com.clayboicardi.game2048.ui.theme.NeonLightSkin
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test

/** `TileLabels` (DESIGN_SYSTEM.md §3.3, §3.4; D-077, D-080, D-103). Plain JVM. */
class TileLabelsTest {
    /** F02-AC9 */
    @Test
    fun compactLabelsBelowTwelveDp() {
        // A 35.4 dp tile (8×8 Classic at 328 dp): exponents 9, 10, 14 and 65 read "512", "1K",
        // "16K" and "32E"; their full decimals stay exact.
        val cases =
            listOf(
                Triple(9, "512", "512"),
                Triple(10, "1K", "1024"),
                Triple(14, "16K", "16384"),
                Triple(65, "32E", "36893488147419103232"),
            )
        for ((exp, label, full) in cases) {
            val l = TileLabels.label(exp, 35.4f)
            assertWithMessage("label of 2^$exp").that(l.text).isEqualTo(label)
            assertWithMessage("compact flag of 2^$exp").that(l.compact).isEqualTo(exp >= 10)
            assertWithMessage("full decimal of 2^$exp")
                .that(TileLabels.fullDecimal(exp))
                .isEqualTo(full)
        }
    }

    /** F02-AC10 */
    @Test
    fun sizeByCharacterCount() {
        // On 40, 69.5 and 160 dp tiles a 1…7-character label is 0.50 / 0.50 / 0.40 / 0.32 / 0.27 /
        // 0.23 × t, then 0.84 · t / (chars · 0.603), with wdth 100 / 100 / 100 / 87.5 / 75. The
        // size
        // follows the label shown, so a label that turns compact is sized by its compact form.
        for (tile in listOf(40f, 69.5f, 160f)) {
            for ((chars, exp) in EXP_BY_CHARS) {
                val l = TileLabels.label(exp, tile)
                val shown = l.text.length
                assertWithMessage("2^$exp on $tile dp is not compact when it fits at 12 dp")
                    .that(l.compact)
                    .isEqualTo(nominal(chars, tile) < 12f)
                if (!l.compact) assertThat(shown).isEqualTo(chars)
                assertWithMessage("size of \"${l.text}\" on $tile dp")
                    .that(l.fontSizeDp)
                    .isWithin(EPS)
                    .of(nominal(shown, tile))
                assertWithMessage("wdth of \"${l.text}\" on $tile dp")
                    .that(l.wdth)
                    .isEqualTo(WDTH[(shown - 1).coerceAtMost(WDTH.size - 1)])
            }
        }
        assertWithMessage("40 dp: 16384 turns compact")
            .that(TileLabels.label(14, 40f).text)
            .isEqualTo("16K")
        assertWithMessage("40 dp: 1048576 turns compact")
            .that(TileLabels.label(20, 40f).text)
            .isEqualTo("1M")
    }

    /** D-077: DESIGN_SYSTEM.md §3.4's first compact exponent per board, Classic and Neon. */
    @Test
    fun firstCompactExponentTable() {
        // Density 1, exponents up to 2^17; Classic r = 0.14, Neon r = 0.18.
        for ((skin, table) in
            listOf(ClassicLightSkin to FIRST_COMPACT, NeonLightSkin to NEON_FIRST)) {
            for ((n, row) in table) {
                for ((i, board) in COMPACT_BOARDS.withIndex()) {
                    val tile = boardGeometry(n, board.dp, skin, 1f).tilePx
                    val first = (1..17).firstOrNull { TileLabels.label(it, tile).compact }
                    assertWithMessage("${skin.id} $n×$n at $board dp (tile $tile)")
                        .that(first)
                        .isEqualTo(row[i])
                }
            }
        }
    }

    /** D-077: the worked sizes of DESIGN_SYSTEM.md §3.3 (4×4 Classic on 328 dp, tile 69.5). */
    @Test
    fun workedSizesOn328() {
        // "2" 34.8 dp, "128" 27.8, "2048" 22.2 at wdth 87.5, "16384" 18.8 at wdth 75, "131072"
        // 16.0; nothing is compact.
        val tile = boardGeometry(4, 328.dp, ClassicLightSkin, 1f).tilePx
        val cases =
            listOf(
                Triple(1, 34.8f, 100f),
                Triple(7, 27.8f, 100f),
                Triple(11, 22.2f, 87.5f),
                Triple(14, 18.8f, 75f),
                Triple(17, 16.0f, 75f),
            )
        for ((exp, size, wdth) in cases) {
            val l = TileLabels.label(exp, tile)
            assertWithMessage("2^$exp compact").that(l.compact).isFalse()
            assertWithMessage("2^$exp text").that(l.text).isEqualTo(TileLabels.fullDecimal(exp))
            assertWithMessage("2^$exp size").that(l.fontSizeDp).isWithin(TABLE).of(size)
            assertWithMessage("2^$exp wdth").that(l.wdth).isEqualTo(wdth)
        }
    }

    /** D-077: the compact format `2^(exp % 10)` + K/M/G/T/P/E, never below 1024. */
    @Test
    fun compactFormatExamples() {
        // 2^10 "1K", 2^14 "16K", 2^17 "128K", 2^20 "1M", 2^65 "32E"; 2..512 stay full even on a
        // 1 dp tile.
        val tiny = 1f
        val cases =
            mapOf(10 to "1K", 14 to "16K", 17 to "128K", 20 to "1M", 30 to "1G", 65 to "32E")
        for ((exp, text) in cases) {
            val l = TileLabels.label(exp, tiny)
            assertWithMessage("2^$exp").that(l.text).isEqualTo(text)
            assertWithMessage("2^$exp compact").that(l.compact).isTrue()
        }
        for (exp in 1..9) {
            val l = TileLabels.label(exp, tiny)
            assertWithMessage("2^$exp stays full").that(l.compact).isFalse()
            assertWithMessage("2^$exp text").that(l.text).isEqualTo((1 shl exp).toString())
        }
    }

    /** D-077: exact full decimals above `Long` (D-080) and `require(exp < 70)`. */
    @Test
    fun fullDecimalAndExponentRange() {
        assertThat(TileLabels.fullDecimal(62)).isEqualTo("4611686018427387904")
        assertThat(TileLabels.fullDecimal(63)).isEqualTo("9223372036854775808")
        assertThat(TileLabels.fullDecimal(65)).isEqualTo("36893488147419103232")
        assertThat(TileLabels.fullDecimal(65).all { it in '0'..'9' }).isTrue()
        assertThat(TileLabels.label(69, 35.4f).text).isEqualTo("512E")
        assertThrows(IllegalArgumentException::class.java) { TileLabels.label(70, 35.4f) }
        assertThrows(IllegalArgumentException::class.java) { TileLabels.label(0, 35.4f) }
    }

    private fun nominal(chars: Int, tile: Float): Float =
        when (chars) {
            1,
            2 -> 0.50f * tile
            3 -> 0.40f * tile
            4 -> 0.32f * tile
            5 -> 0.27f * tile
            6 -> 0.23f * tile
            else -> 0.84f * tile / (chars * 0.603f)
        }

    private companion object {
        const val EPS = 1e-4f

        /** The worked sizes are rounded to 0.1 dp. */
        const val TABLE = 0.051f

        /** An exponent whose full decimal has 1…7 characters. */
        val EXP_BY_CHARS = listOf(1 to 1, 2 to 4, 3 to 7, 4 to 10, 5 to 14, 6 to 17, 7 to 20)

        val WDTH = listOf(100f, 100f, 100f, 87.5f, 75f)

        val COMPACT_BOARDS = listOf(280, 328, 380, 481, 720)

        /** DESIGN_SYSTEM.md §3.4, Classic; `null` = none up to 2^17. */
        val FIRST_COMPACT: Map<Int, List<Int?>> =
            mapOf(
                3 to listOf(null, null, null, null, null),
                4 to listOf(null, null, null, null, null),
                5 to listOf(17, null, null, null, null),
                6 to listOf(14, 17, null, null, null),
                7 to listOf(10, 14, 17, null, null),
                8 to listOf(10, 10, 14, null, null),
            )

        /** The same table for Neon: only 8×8 at 481 dp differs (2^17 "128K"). */
        val NEON_FIRST: Map<Int, List<Int?>> = FIRST_COMPACT + (8 to listOf(10, 10, 14, 17, null))
    }
}
