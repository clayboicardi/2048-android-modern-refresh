package com.clayboicardi.game2048.ios

import androidx.compose.ui.graphics.Color

/**
 * Classic Refined tile colours, ported verbatim from the Android app's `ClassicLightPalette` /
 * `ClassicDarkPalette` (DESIGN_SYSTEM.md §2; D-070, D-072) so the iPhone board matches the phone
 * it came from. A 17-entry ramp over tile exponents 1..17; exponents above 17 share the last entry.
 */
interface TileColors {
    fun fill(exp: Int): Color

    fun ink(exp: Int): Color

    val board: Color
    val empty: Color
    val background: Color
    val title: Color
    val onBackground: Color
}

private const val RAMP_SIZE = 17

private fun rampIndex(exp: Int): Int = exp.coerceIn(1, RAMP_SIZE) - 1

object ClassicLight : TileColors {
    private val fills =
        arrayOf(
            Color(0xFFEEE4DA), Color(0xFFEDDBBE), Color(0xFFF3B27A), Color(0xFFF69664),
            Color(0xFFD9613F), Color(0xFFDE4A26), Color(0xFFF3D98A), Color(0xFFF0CC63),
            Color(0xFFEDBF45), Color(0xFFEAB12C), Color(0xFFE6A11A), Color(0xFF3C3A32),
            Color(0xFF013D3D), Color(0xFF062D59), Color(0xFF36164A), Color(0xFF420420),
            Color(0xFF201710),
        )
    private val inks =
        arrayOf(
            Color(0xFF5C5248), Color(0xFF5C5248), Color(0xFF4A2A0E), Color(0xFF401C06),
            Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF45360A), Color(0xFF45360A),
            Color(0xFF3F3006), Color(0xFF3D2D04), Color(0xFF3A2900), Color(0xFFFFFFFF),
            Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF),
            Color(0xFFFFFFFF),
        )

    override fun fill(exp: Int): Color = fills[rampIndex(exp)]

    override fun ink(exp: Int): Color = inks[rampIndex(exp)]

    override val board = Color(0xFFBBADA0)
    override val empty = Color(0xFFCDC1B4)
    override val background = Color(0xFFFAF8EF)
    override val title = Color(0xFF776E65)
    override val onBackground = Color(0xFF776E65)
}

object ClassicDark : TileColors {
    private val fills =
        arrayOf(
            Color(0xFF524A42), Color(0xFF605545), Color(0xFFD99A64), Color(0xFFDE8455),
            Color(0xFFC95A3F), Color(0xFFC0412A), Color(0xFFDCC572), Color(0xFFD9B754),
            Color(0xFFD6A93D), Color(0xFFD39B2A), Color(0xFFD08C1A), Color(0xFFF1E8D8),
            Color(0xFFB1EDEC), Color(0xFFC3D6FE), Color(0xFFE4BDE6), Color(0xFFEFAFB3),
            Color(0xFFFAFAFA),
        )
    private val inks =
        arrayOf(
            Color(0xFFF1E9E0), Color(0xFFF6EDDE), Color(0xFF2E1806), Color(0xFF2A1204),
            Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF2E2405), Color(0xFF2C2204),
            Color(0xFF2A1F02), Color(0xFF281D00), Color(0xFF251A00), Color(0xFF1B1916),
            Color(0xFF1B1916), Color(0xFF1B1916), Color(0xFF1B1916), Color(0xFF1B1916),
            Color(0xFF1B1916),
        )

    override fun fill(exp: Int): Color = fills[rampIndex(exp)]

    override fun ink(exp: Int): Color = inks[rampIndex(exp)]

    override val board = Color(0xFF2C2824)
    override val empty = Color(0xFF3A342E)
    override val background = Color(0xFF1B1916)
    override val title = Color(0xFFD1C4BA)
    override val onBackground = Color(0xFFD1C4BA)
}
