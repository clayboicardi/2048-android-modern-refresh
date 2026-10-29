package com.clayboicardi.game2048.game.board

import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.ui.theme.GameDimens
import java.math.BigInteger

/**
 * A tile label before measurement (DESIGN_SYSTEM.md §3.3 steps 1–3, D-077): [text] is the full
 * decimal or, when [compact], the compact form ("16K"); [fontSizeDp] the nominal size in dp for the
 * tile side; [wdth] the numeral width axis (100, 87.5 or 75).
 */
@Immutable
data class TileLabel(
    val text: String,
    val fontSizeDp: Float,
    val wdth: Float,
    val compact: Boolean,
)

/**
 * Tile label text and size (DESIGN_SYSTEM.md §3.3, §3.4; D-077, D-103). Pure; used by the board's
 * text cache and by `MiniTile`. Latin digits always.
 */
object TileLabels {
    /** The label of exponent [exp] (≥ 1, < 70) on a tile of side [tileSizeDp] dp. */
    fun label(exp: Int, tileSizeDp: Float): TileLabel {
        require(exp in 1 until EXP_LIMIT) { "Tile exponent $exp is outside 1 until $EXP_LIMIT" }
        val full = fullDecimal(exp)
        val fullSize = nominalSizeDp(full.length, tileSizeDp)
        val compact = fullSize < GameDimens.TileLabelCompactBelow.value && exp >= COMPACT_MIN_EXP
        val text = if (compact) compactText(exp) else full
        val size = if (compact) nominalSizeDp(text.length, tileSizeDp) else fullSize
        return TileLabel(text, size, wdthFor(text.length), compact)
    }

    /**
     * The full decimal value of 2^[exp]: `(1L shl exp).toString()` for exp ≤ 62, else
     * `BigInteger.ONE.shiftLeft(exp).toString()` (the accessibility text, D-080).
     */
    fun fullDecimal(exp: Int): String {
        require(exp >= 0) { "Negative exponent $exp" }
        return if (exp <= LONG_EXP_MAX) {
            (1L shl exp).toString()
        } else {
            BigInteger.ONE.shiftLeft(exp).toString()
        }
    }

    /** `2^(exp % 10)` and the suffix of `exp / 10`: 2^14 → "16K", 2^65 → "32E" (§3.4). */
    private fun compactText(exp: Int): String =
        (1 shl (exp % COMPACT_STEP)).toString() + SUFFIXES[exp / COMPACT_STEP - 1]

    /** The nominal size of a [chars]-character label on a tile of side [tile] dp (§3.3 step 2). */
    private fun nominalSizeDp(chars: Int, tile: Float): Float =
        if (chars <= SIZE_BY_CHARS.size) {
            SIZE_BY_CHARS[chars - 1] * tile
        } else {
            SIZE_LONG_WIDTH * tile / (chars * DIGIT_ADVANCE_EM)
        }

    /** The `wdth` axis of a [chars]-character label (§3.3 step 2). */
    private fun wdthFor(chars: Int): Float = WDTH_BY_CHARS[(chars - 1).coerceAtMost(WDTH_LAST)]

    /** Exponents ≥ 70 cannot occur for N ≤ 8 (the maximum is 2^65, §3.4). */
    private const val EXP_LIMIT = 70

    /** Values below 1024 always show the full number (§3.4). */
    private const val COMPACT_MIN_EXP = 10

    private const val COMPACT_STEP = 10

    /** The suffixes of `exp / 10` = 1…6 (§3.4). */
    private const val SUFFIXES = "KMGTPE"

    /** `1L shl 62` is the largest power of two below 2^63 (D-080). */
    private const val LONG_EXP_MAX = 62

    /**
     * DESIGN_SYSTEM.md §3.3 step 2: the size of a 1…6-character label as a fraction of the tile.
     */
    private val SIZE_BY_CHARS = floatArrayOf(0.50f, 0.50f, 0.40f, 0.32f, 0.27f, 0.23f)

    /** Seven or more characters: `0.84 · t / (chars · 0.603)`, 0.603 em being the wdth-75 digit. */
    private const val SIZE_LONG_WIDTH = 0.84f

    private const val DIGIT_ADVANCE_EM = 0.603f

    /** The `wdth` of a 1, 2, 3, 4 and ≥ 5-character label. */
    private val WDTH_BY_CHARS = floatArrayOf(100f, 100f, 100f, 87.5f, 75f)

    private val WDTH_LAST = WDTH_BY_CHARS.size - 1
}
