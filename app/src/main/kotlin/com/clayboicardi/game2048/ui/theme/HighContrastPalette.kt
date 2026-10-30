package com.clayboicardi.game2048.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The high-contrast decorator of [base] (D-073; DESIGN_SYSTEM.md §2.1, §2.7, §9.3): every ink is
 * `#000000` or `#FFFFFF`, whichever has the higher contrast against the fill ([highContrastInk]);
 * fills, glows, the board, the empty cell and the background are [base]'s. The 1.5 dp tile outline
 * is the skin's ([highContrastSkin]), so it follows the chrome `outline`, dynamic or not.
 */
class HighContrastPalette(val base: TilePalette) : TilePalette {
    private val inks = Array(HC_RAMP_SIZE) { i -> highContrastInk(base.fill(i + 1)) }

    override fun fill(exp: Int): Color = base.fill(exp)

    override fun ink(exp: Int): Color {
        require(exp >= 1) { "Tile exponent must be >= 1, was $exp" }
        return inks[exp.coerceAtMost(HC_RAMP_SIZE) - 1]
    }

    override fun glow(exp: Int): Glow? = base.glow(exp)

    override val board: Color
        get() = base.board

    override val empty: Color
        get() = base.empty

    override val background: Color
        get() = base.background

    override fun equals(other: Any?): Boolean = other is HighContrastPalette && other.base == base

    override fun hashCode(): Int = base.hashCode() * HASH_PRIME + 1
}

/**
 * The high-contrast ink of [fill] (DESIGN_SYSTEM.md §2.7 item 1): black when `contrast(fill, black)
 * ≥ contrast(fill, white)`, else white, with WCAG 2.x relative luminance (`Color.luminance()`).
 */
fun highContrastInk(fill: Color): Color {
    val l = fill.luminance()
    val onBlack = (l + CONTRAST_OFFSET) / CONTRAST_OFFSET
    val onWhite = (1f + CONTRAST_OFFSET) / (l + CONTRAST_OFFSET)
    return if (onBlack >= onWhite) Color.Black else Color.White
}

/**
 * The high-contrast skin of [skin] (D-073; DESIGN_SYSTEM.md §2.7, §9.3): its id plus
 * [HIGH_CONTRAST_SUFFIX], so the text-layout cache never serves a normal-ink layout, and the tile
 * outline in the chrome [outline] colour.
 */
fun highContrastSkin(skin: TileSkin, outline: Color): TileSkin =
    skin.copy(id = skin.id + HIGH_CONTRAST_SUFFIX, tileOutline = outline)

/** The suffix of a high-contrast skin id (DESIGN_SYSTEM.md §2.1). */
const val HIGH_CONTRAST_SUFFIX: String = "-hc"

/** WCAG's 0.05 flare term. */
private const val CONTRAST_OFFSET = 0.05f

private const val HC_RAMP_SIZE = 17
private const val HASH_PRIME = 31
