package com.clayboicardi.game2048.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Classic Refined, light (DESIGN_SYSTEM.md §2.2, §2.3, §4.6; D-070, D-072): research/04 §8 A for
 * exponents 1–11, D-072's super tiles for 12–17; flat super tiles (D-078).
 */
object ClassicLightPalette : TilePalette {
    private val fills =
        arrayOf(
            Color(0xFFEEE4DA), // 1: 2
            Color(0xFFEDDBBE), // 2: 4
            Color(0xFFF3B27A), // 3: 8
            Color(0xFFF69664), // 4: 16
            Color(0xFFD9613F), // 5: 32
            Color(0xFFDE4A26), // 6: 64
            Color(0xFFF3D98A), // 7: 128
            Color(0xFFF0CC63), // 8: 256
            Color(0xFFEDBF45), // 9: 512
            Color(0xFFEAB12C), // 10: 1024
            Color(0xFFE6A11A), // 11: 2048
            Color(0xFF3C3A32), // 12: 4096
            Color(0xFF013D3D), // 13: 8192
            Color(0xFF062D59), // 14: 16384
            Color(0xFF36164A), // 15: 32768
            Color(0xFF420420), // 16: 65536
            Color(0xFF201710), // ≥ 17: ≥ 131072
        )

    private val inks =
        arrayOf(
            Color(0xFF5C5248), // 1
            Color(0xFF5C5248), // 2
            Color(0xFF4A2A0E), // 3
            Color(0xFF401C06), // 4
            Color(0xFFFFFFFF), // 5
            Color(0xFFFFFFFF), // 6
            Color(0xFF45360A), // 7
            Color(0xFF45360A), // 8
            Color(0xFF3F3006), // 9
            Color(0xFF3D2D04), // 10
            Color(0xFF3A2900), // 11
            Color(0xFFFFFFFF), // 12
            Color(0xFFFFFFFF), // 13
            Color(0xFFFFFFFF), // 14
            Color(0xFFFFFFFF), // 15
            Color(0xFFFFFFFF), // 16
            Color(0xFFFFFFFF), // ≥ 17
        )

    private val glows = classicGlows(alphaFactor = 1f)

    override fun fill(exp: Int): Color = fills[rampIndex(exp)]

    override fun ink(exp: Int): Color = inks[rampIndex(exp)]

    override fun glow(exp: Int): Glow? = classicGlow(glows, exp)

    override val board: Color = Color(0xFFBBADA0)

    override val empty: Color = Color(0xFFCDC1B4)

    override val background: Color = Color(0xFFFAF8EF)
}

/**
 * Classic Refined, dark (DESIGN_SYSTEM.md §2.2, §2.4, §4.6; D-070, D-072, D-207): exponent 1 is
 * `#524A42`; the glow alphas are the light ones × 1.15.
 */
object ClassicDarkPalette : TilePalette {
    private val fills =
        arrayOf(
            Color(0xFF524A42), // 1: 2 (D-207)
            Color(0xFF605545), // 2: 4
            Color(0xFFD99A64), // 3: 8
            Color(0xFFDE8455), // 4: 16
            Color(0xFFC95A3F), // 5: 32
            Color(0xFFC0412A), // 6: 64
            Color(0xFFDCC572), // 7: 128
            Color(0xFFD9B754), // 8: 256
            Color(0xFFD6A93D), // 9: 512
            Color(0xFFD39B2A), // 10: 1024
            Color(0xFFD08C1A), // 11: 2048
            Color(0xFFF1E8D8), // 12: 4096
            Color(0xFFB1EDEC), // 13: 8192
            Color(0xFFC3D6FE), // 14: 16384
            Color(0xFFE4BDE6), // 15: 32768
            Color(0xFFEFAFB3), // 16: 65536
            Color(0xFFFAFAFA), // ≥ 17: ≥ 131072
        )

    private val inks =
        arrayOf(
            Color(0xFFF1E9E0), // 1
            Color(0xFFF6EDDE), // 2
            Color(0xFF2E1806), // 3
            Color(0xFF2A1204), // 4
            Color(0xFFFFFFFF), // 5
            Color(0xFFFFFFFF), // 6
            Color(0xFF2E2405), // 7
            Color(0xFF2C2204), // 8
            Color(0xFF2A1F02), // 9
            Color(0xFF281D00), // 10
            Color(0xFF251A00), // 11
            Color(0xFF1B1916), // 12
            Color(0xFF1B1916), // 13
            Color(0xFF1B1916), // 14
            Color(0xFF1B1916), // 15
            Color(0xFF1B1916), // 16
            Color(0xFF1B1916), // ≥ 17
        )

    private val glows = classicGlows(alphaFactor = CLASSIC_DARK_GLOW_FACTOR)

    override fun fill(exp: Int): Color = fills[rampIndex(exp)]

    override fun ink(exp: Int): Color = inks[rampIndex(exp)]

    override fun glow(exp: Int): Glow? = classicGlow(glows, exp)

    override val board: Color = Color(0xFF2C2824)

    override val empty: Color = Color(0xFF3A342E)

    override val background: Color = Color(0xFF1B1916)
}

/** The Classic skin, light (DESIGN_SYSTEM.md §2.1, §2.2, §4.2; D-075, D-076, D-189). */
val ClassicLightSkin: TileSkin =
    TileSkin(
        id = "classic-light",
        gapRatio = 0.14f,
        radiusFraction = 0.07f,
        radiusMin = 2.dp,
        radiusMax = 10.dp,
        numeralWeight = 800,
        numeralRond = 0f,
        titleColor = Color(0xFF776E65),
        boardInsetWell = false,
        tileOutline = null,
    )

/** The Classic skin, dark: as [ClassicLightSkin] with its own id and title colour. */
val ClassicDarkSkin: TileSkin =
    ClassicLightSkin.copy(id = "classic-dark", titleColor = Color(0xFFD1C4BA))

/** The ramp length (DESIGN_SYSTEM.md §2.1): exponents ≥ 17 share the last entry. */
private const val RAMP_SIZE = 17

/** The first and last exponents with a Classic glow: 128 and 2048 (DESIGN_SYSTEM.md §4.6). */
private const val GLOW_FIRST_EXP = 7
private const val GLOW_LAST_EXP = 11

/** Classic dark multiplies the light glow alphas by 1.15, unrounded (DESIGN_SYSTEM.md §4.6). */
private const val CLASSIC_DARK_GLOW_FACTOR = 1.15f

/** The Classic glow colour (D-078, from the legacy formula). */
private val ClassicGlowColor = Color(0xFFF3D774)

/** Light outer `dropShadow` alphas for exponents 7–11 (DESIGN_SYSTEM.md §4.6). */
private val ClassicOuterAlphas = floatArrayOf(0.19f, 0.25f, 0.32f, 0.38f, 0.44f)

/** Light inner white highlight alphas for exponents 7–11 (DESIGN_SYSTEM.md §4.6). */
private val ClassicInnerAlphas = floatArrayOf(0.050f, 0.065f, 0.080f, 0.095f, 0.110f)

/** The index of [exp] in a 17-entry ramp (DESIGN_SYSTEM.md §2.1). */
private fun rampIndex(exp: Int): Int {
    require(exp >= 1) { "Tile exponent must be >= 1, was $exp" }
    return exp.coerceAtMost(RAMP_SIZE) - 1
}

/** The Classic glow of [exp] from [glows] (exponents 7–11 only; D-078 flat super tiles). */
private fun classicGlow(glows: Array<Glow>, exp: Int): Glow? =
    if (exp in GLOW_FIRST_EXP..GLOW_LAST_EXP) glows[exp - GLOW_FIRST_EXP] else null

/**
 * The five Classic glows (DESIGN_SYSTEM.md §4.6), built once per palette: colour `#F3D774`, radius
 * `0.20·t`, spread `0.03·t`, a 1 dp inner white highlight with no offset; both alphas ×
 * [alphaFactor].
 */
private fun classicGlows(alphaFactor: Float): Array<Glow> =
    Array(ClassicOuterAlphas.size) { i ->
        Glow(
            color = ClassicGlowColor,
            alpha = ClassicOuterAlphas[i] * alphaFactor,
            radiusFraction = 0.20f,
            spreadFraction = 0.03f,
            innerColor = Color.White,
            innerAlpha = ClassicInnerAlphas[i] * alphaFactor,
            innerRadiusDp = 1f,
            innerRadiusFraction = 0f,
            innerOffsetYFraction = 0f,
        )
    }
