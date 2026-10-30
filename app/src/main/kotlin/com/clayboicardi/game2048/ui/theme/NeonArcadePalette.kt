package com.clayboicardi.game2048.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Neon Arcade, light (DESIGN_SYSTEM.md §2.2, §2.5, §4.6; D-071, D-072, D-189): research/04 §8 C for
 * exponents 1–11, D-072's super tiles for 12–17. The tiles, board and empty cell are those of
 * [NeonDarkPalette]; only the app background differs.
 */
object NeonLightPalette : TilePalette by NeonTiles {
    override val background: Color = Color(0xFFF3F1FA)
}

/** Neon Arcade, dark (DESIGN_SYSTEM.md §2.2, §2.5, §4.6; D-071, D-072): as [NeonLightPalette]. */
object NeonDarkPalette : TilePalette by NeonTiles {
    override val background: Color = Color(0xFF0E0C1A)
}

/** The Neon skin, light (DESIGN_SYSTEM.md §2.1, §2.2, §4.2, §4.5; D-075, D-076, D-189). */
val NeonLightSkin: TileSkin =
    TileSkin(
        id = "neon-light",
        gapRatio = 0.18f,
        radiusFraction = 0.22f,
        radiusMin = 4.dp,
        radiusMax = 34.dp,
        numeralWeight = 900,
        numeralRond = 100f,
        titleColor = Color(0xFF4B4185),
        boardInsetWell = true,
        tileOutline = null,
    )

/** The Neon skin, dark: as [NeonLightSkin] with its own id and title colour (D-189). */
val NeonDarkSkin: TileSkin = NeonLightSkin.copy(id = "neon-dark", titleColor = Color(0xFFECF33C))

/** Neon's inset well (DESIGN_SYSTEM.md §4.5): `innerShadow` colour and alpha. */
val NeonWellColor: Color = Color(0xFF000000)

/** See [NeonWellColor]. */
const val NEON_WELL_ALPHA: Float = 0.5f

/**
 * The tiles both Neon palettes share (DESIGN_SYSTEM.md §2.5; the board and empty cell are the same
 * in both modes, §2.2). [background] is the dark one; the objects above override it.
 */
private object NeonTiles : TilePalette {
    private val fills =
        arrayOf(
            Color(0xFF323964), // 1: 2
            Color(0xFF4B4185), // 2: 4
            Color(0xFF7241A0), // 3: 8
            Color(0xFFA53D9F), // 4: 16
            Color(0xFFCB3C81), // 5: 32
            Color(0xFFF0555B), // 6: 64
            Color(0xFFF97C3D), // 7: 128
            Color(0xFFFC9F30), // 8: 256
            Color(0xFFF7BE2B), // 9: 512
            Color(0xFFF2D935), // 10: 1024
            Color(0xFFECF33C), // 11: 2048
            Color(0xFFEEF6FE), // 12: 4096
            Color(0xFFAEF9EE), // 13: 8192
            Color(0xFFDDD9FE), // 14: 16384
            Color(0xFFFBBFE6), // 15: 32768
            Color(0xFFFEB6A4), // 16: 65536
            Color(0xFFFDFDFD), // ≥ 17: ≥ 131072
        )

    /** White ink on 2–32, `#12101F` from 64 on (DESIGN_SYSTEM.md §2.5). */
    private val inks =
        Array(fills.size) { i -> if (i < LAST_WHITE_INK_EXP) Color.White else NeonDarkInk }

    /**
     * Every tile's glow (DESIGN_SYSTEM.md §4.6): the glassy inner highlight on all of them, the
     * own-fill outer glow from 128 on, super tiles included.
     */
    private val glows =
        Array(fills.size) { i ->
            val exp = i + 1
            Glow(
                color = fills[i],
                alpha = neonOuterAlpha(exp),
                radiusFraction = 0.25f,
                spreadFraction = 0f,
                innerColor = Color.White,
                innerAlpha = 0.18f,
                innerRadiusDp = 0f,
                innerRadiusFraction = 0.08f,
                innerOffsetYFraction = 0.04f,
            )
        }

    override fun fill(exp: Int): Color = fills[neonIndex(exp)]

    override fun ink(exp: Int): Color = inks[neonIndex(exp)]

    override fun glow(exp: Int): Glow = glows[neonIndex(exp)]

    override val board: Color = Color(0xFF1A1730)

    override val empty: Color = Color(0xFF25213F)

    override val background: Color = Color(0xFF0E0C1A)
}

/** Neon's dark ink (DESIGN_SYSTEM.md §2.5). */
private val NeonDarkInk = Color(0xFF12101F)

/** The last exponent with white ink: 32 (DESIGN_SYSTEM.md §2.5). */
private const val LAST_WHITE_INK_EXP = 5

/**
 * Neon outer glow alphas for exponents 7–11; 12 and above keep the last (DESIGN_SYSTEM.md §4.6).
 */
private val NeonOuterAlphas = floatArrayOf(0.35f, 0.425f, 0.50f, 0.575f, 0.65f)

/** The first exponent with an outer glow: 128 (DESIGN_SYSTEM.md §4.6). */
private const val NEON_GLOW_FIRST_EXP = 7

/** The outer glow alpha of [exp]: 0 below 128, the table from 128, 0.65 from 2048 on. */
private fun neonOuterAlpha(exp: Int): Float =
    if (exp < NEON_GLOW_FIRST_EXP) {
        0f
    } else {
        NeonOuterAlphas[(exp - NEON_GLOW_FIRST_EXP).coerceAtMost(NeonOuterAlphas.size - 1)]
    }

/** The index of [exp] in the 17-entry ramp (DESIGN_SYSTEM.md §2.1). */
private fun neonIndex(exp: Int): Int {
    require(exp >= 1) { "Tile exponent must be >= 1, was $exp" }
    return exp.coerceAtMost(NEON_RAMP_SIZE) - 1
}

private const val NEON_RAMP_SIZE = 17
