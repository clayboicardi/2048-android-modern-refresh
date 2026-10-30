package com.clayboicardi.game2048.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import org.junit.Test

/**
 * Palette contrast and colour difference (D-072, D-083, D-205, D-207; DESIGN_SYSTEM.md §2.3–§2.5,
 * §2.9, §2.12, §2.13), plain JVM: every palette instance, Classic and Neon in light and dark, each
 * also with high contrast (`HighContrastPalette`), measured with §2.13's formulas exactly: WCAG
 * contrast and the Machado 2009 CVD matrices on linear RGB, clamped and not re-quantized, then
 * OKLab ΔE × 100. The tables below are §2.3–§2.5 and §2.9 verbatim.
 */
class PaletteContrastTest {
    /** F12-AC3 */
    @Test
    fun superTileHexesMatchD072() {
        for ((name, palette, row) in D072) {
            val (ink, fills) = row
            for (exp in 12..17) {
                assertWithMessage("$name fill($exp)")
                    .that(hex(palette.fill(exp)))
                    .isEqualTo(fills[exp - 12])
                assertWithMessage("$name ink($exp)").that(hex(palette.ink(exp))).isEqualTo(ink)
            }
            assertWithMessage("$name fill(18) == fill(17)")
                .that(palette.fill(18))
                .isEqualTo(palette.fill(17))
            assertWithMessage("$name ink(18) == ink(17)")
                .that(palette.ink(18))
                .isEqualTo(palette.ink(17))
        }
    }

    /** F12-AC4 */
    @Test
    fun inkContrastAndAdjacentDeltaE() {
        for ((name, palette) in instances()) {
            for (exp in 1..17) {
                val floor = if (exp >= 10) 4.5 else 3.0
                val cr = contrast(palette.fill(exp), palette.ink(exp))
                assertWithMessage("$name: ink of exponent $exp, contrast %s", cr)
                    .that(cr)
                    .isAtLeast(floor)
            }
            for (exp in 2..17) {
                val a = palette.fill(exp - 1)
                val b = palette.fill(exp)
                assertWithMessage("$name: ΔE normal ${exp - 1} → $exp")
                    .that(deltaE(a, b, null))
                    .isAtLeast(3.0)
                for ((vision, matrix) in CVD) {
                    assertWithMessage("$name: ΔE $vision ${exp - 1} → $exp")
                        .that(deltaE(a, b, matrix))
                        .isAtLeast(2.0)
                }
            }
        }
    }

    /**
     * DESIGN_SYSTEM.md §2.13 assertion 3: every row of §2.3–§2.5 is the palette's fill and ink, and
     * each of its contrast ratios (CR, vs empty, HC CR) equals `round(contrast, 2)`; this also pins
     * D-207's Classic dark 2-tile fill `#524A42`.
     */
    @Test
    fun rampTablesMatchComputedContrast() {
        for ((name, palette, rows) in RAMPS) {
            val hc = HighContrastPalette(palette)
            rows.forEachIndexed { i, row ->
                val exp = i + 1
                val columns = row.split(' ')
                val fill = columns[0]
                val ink = columns[1]
                val cr = columns[2]
                val vsEmpty = columns[3]
                val hcInk = columns[4]
                val hcCr = columns[5]
                val at = "$name exponent $exp"
                assertWithMessage("$at fill").that(hex(palette.fill(exp))).isEqualTo(fill)
                assertWithMessage("$at ink").that(hex(palette.ink(exp))).isEqualTo(ink)
                assertWithMessage("$at CR")
                    .that(round2(contrast(palette.fill(exp), palette.ink(exp))))
                    .isEqualTo(cr.toDouble())
                assertWithMessage("$at vs empty")
                    .that(round2(contrast(palette.fill(exp), palette.empty)))
                    .isEqualTo(vsEmpty.toDouble())
                assertWithMessage("$at HC ink").that(hex(hc.ink(exp))).isEqualTo(hcInk)
                assertWithMessage("$at HC CR")
                    .that(round2(contrast(hc.fill(exp), hc.ink(exp))))
                    .isEqualTo(hcCr.toDouble())
            }
        }
    }

    /**
     * DESIGN_SYSTEM.md §2.13 assertion 4: the §2.9 chrome pairs of all four schemes meet their
     * "Required" ratio, and each equals the table's value (`round(contrast, 2)`).
     */
    @Test
    fun chromePairsMeetRequiredContrast() {
        for (pair in CHROME_PAIRS) {
            SCHEMES.forEachIndexed { k, (name, scheme) ->
                val (fg, bg) = pair.colors(scheme)
                val cr = contrast(fg, bg)
                assertWithMessage("$name: ${pair.name}, contrast %s", cr)
                    .that(cr)
                    .isAtLeast(pair.required)
                assertWithMessage("$name: ${pair.name} equals §2.9")
                    .that(round2(cr))
                    .isEqualTo(pair.table[k])
            }
        }
    }

    /**
     * DESIGN_SYSTEM.md §2.13 assertion 5: with high contrast every ink is `#000000` when
     * `contrast(fill, black) ≥ contrast(fill, white)`, else `#FFFFFF` (§2.7 item 1), and fills,
     * glows, board, empty and background are the base palette's.
     */
    @Test
    fun highContrastInkFollowsBlackWhiteRule() {
        for ((name, base) in BASES) {
            val hc = HighContrastPalette(base)
            for (exp in 1..17) {
                val fill = base.fill(exp)
                val black = contrast(fill, Color.Black) >= contrast(fill, Color.White)
                assertWithMessage("$name-hc ink($exp)")
                    .that(hc.ink(exp))
                    .isEqualTo(if (black) Color.Black else Color.White)
                assertWithMessage("$name-hc fill($exp)").that(hc.fill(exp)).isEqualTo(fill)
                assertWithMessage("$name-hc glow($exp)")
                    .that(hc.glow(exp))
                    .isEqualTo(base.glow(exp))
            }
            assertWithMessage("$name-hc board").that(hc.board).isEqualTo(base.board)
            assertWithMessage("$name-hc empty").that(hc.empty).isEqualTo(base.empty)
            assertWithMessage("$name-hc background").that(hc.background).isEqualTo(base.background)
        }
    }

    private class ChromePair(
        val name: String,
        val required: Double,
        val table: List<Double>,
        val colors: (ColorScheme) -> Pair<Color, Color>,
    )

    private data class Ramp(val name: String, val palette: TilePalette, val rows: List<String>)

    private data class SuperRow(
        val name: String,
        val palette: TilePalette,
        val row: Pair<String, List<String>>,
    )

    private companion object {
        val BASES =
            listOf(
                "classic-light" to ClassicLightPalette,
                "classic-dark" to ClassicDarkPalette,
                "neon-light" to NeonLightPalette,
                "neon-dark" to NeonDarkPalette,
            )

        val SCHEMES =
            listOf(
                "Classic light" to ClassicLightScheme,
                "Classic dark" to ClassicDarkScheme,
                "Neon light" to NeonLightScheme,
                "Neon dark" to NeonDarkScheme,
            )

        /** The eight instances of §2.13: each palette and its high-contrast decorator. */
        fun instances(): List<Pair<String, TilePalette>> =
            BASES + BASES.map { (name, p) -> "$name-hc" to HighContrastPalette(p) }

        /** D-072: ink and fills 4096 … ≥ 131072 per palette and mode. */
        val D072 =
            listOf(
                SuperRow(
                    "classic-light",
                    ClassicLightPalette,
                    "FFFFFF" to listOf("3C3A32", "013D3D", "062D59", "36164A", "420420", "201710"),
                ),
                SuperRow(
                    "classic-dark",
                    ClassicDarkPalette,
                    "1B1916" to listOf("F1E8D8", "B1EDEC", "C3D6FE", "E4BDE6", "EFAFB3", "FAFAFA"),
                ),
                SuperRow(
                    "neon-light",
                    NeonLightPalette,
                    "12101F" to listOf("EEF6FE", "AEF9EE", "DDD9FE", "FBBFE6", "FEB6A4", "FDFDFD"),
                ),
                SuperRow(
                    "neon-dark",
                    NeonDarkPalette,
                    "12101F" to listOf("EEF6FE", "AEF9EE", "DDD9FE", "FBBFE6", "FEB6A4", "FDFDFD"),
                ),
            )

        /** DESIGN_SYSTEM.md §2.3: fill, ink, CR, vs empty, HC ink, HC CR per exponent 1–17. */
        val CLASSIC_LIGHT_ROWS =
            listOf(
                "EEE4DA 5C5248 6.08 1.41 000000 16.75",
                "EDDBBE 5C5248 5.62 1.30 000000 15.48",
                "F3B27A 4A2A0E 7.04 1.04 000000 11.46",
                "F69664 401C06 6.83 1.26 000000 9.47",
                "D9613F FFFFFF 3.66 2.07 000000 5.73",
                "DE4A26 FFFFFF 4.11 2.32 000000 5.11",
                "F3D98A 45360A 8.46 1.27 000000 15.10",
                "F0CC63 45360A 7.58 1.14 000000 13.52",
                "EDBF45 3F3006 7.42 1.02 000000 12.14",
                "EAB12C 3D2D04 6.87 1.10 000000 10.82",
                "E6A11A 3A2900 6.33 1.25 000000 9.48",
                "3C3A32 FFFFFF 11.39 6.45 FFFFFF 11.39",
                "013D3D FFFFFF 12.10 6.84 FFFFFF 12.10",
                "062D59 FFFFFF 13.75 7.78 FFFFFF 13.75",
                "36164A FFFFFF 15.32 8.67 FFFFFF 15.32",
                "420420 FFFFFF 16.54 9.36 FFFFFF 16.54",
                "201710 FFFFFF 17.63 9.97 FFFFFF 17.63",
            )

        /** DESIGN_SYSTEM.md §2.4 (exponent 1: D-207). */
        val CLASSIC_DARK_ROWS =
            listOf(
                "524A42 F1E9E0 7.23 1.41 FFFFFF 8.69",
                "605545 F6EDDE 6.27 1.69 FFFFFF 7.28",
                "D99A64 2E1806 7.01 5.12 000000 8.76",
                "DE8455 2A1204 6.35 4.41 000000 7.54",
                "C95A3F FFFFFF 4.19 2.93 000000 5.02",
                "C0412A FFFFFF 5.21 2.36 FFFFFF 5.21",
                "DCC572 2E2405 8.95 7.17 000000 12.27",
                "D9B754 2C2204 8.11 6.34 000000 10.85",
                "D6A93D 2A1F02 7.41 5.61 000000 9.60",
                "D39B2A 281D00 6.71 4.96 000000 8.49",
                "D08C1A 251A00 6.07 4.35 000000 7.45",
                "F1E8D8 1B1916 14.43 10.10 000000 17.27",
                "B1EDEC 1B1916 13.53 9.47 000000 16.19",
                "C3D6FE 1B1916 12.00 8.40 000000 14.37",
                "E4BDE6 1B1916 10.62 7.44 000000 12.72",
                "EFAFB3 1B1916 9.57 6.70 000000 11.45",
                "FAFAFA 1B1916 16.80 11.76 000000 20.12",
            )

        /** DESIGN_SYSTEM.md §2.5, identical in light and dark. */
        val NEON_ROWS =
            listOf(
                "323964 FFFFFF 11.02 1.39 FFFFFF 11.02",
                "4B4185 FFFFFF 8.77 1.75 FFFFFF 8.77",
                "7241A0 FFFFFF 7.05 2.18 FFFFFF 7.05",
                "A53D9F FFFFFF 5.57 2.75 FFFFFF 5.57",
                "CB3C81 FFFFFF 4.66 3.29 FFFFFF 4.66",
                "F0555B 12101F 5.50 4.50 000000 6.16",
                "F97C3D 12101F 7.13 5.83 000000 7.98",
                "FC9F30 12101F 9.06 7.41 000000 10.14",
                "F7BE2B 12101F 11.04 9.03 000000 12.36",
                "F2D935 12101F 13.18 10.78 000000 14.75",
                "ECF33C 12101F 15.59 12.76 000000 17.45",
                "EEF6FE 12101F 17.19 14.07 000000 19.25",
                "AEF9EE 12101F 15.71 12.85 000000 17.58",
                "DDD9FE 12101F 13.78 11.28 000000 15.43",
                "FBBFE6 12101F 12.23 10.01 000000 13.70",
                "FEB6A4 12101F 11.11 9.09 000000 12.44",
                "FDFDFD 12101F 18.44 15.09 000000 20.65",
            )

        val RAMPS =
            listOf(
                Ramp("classic-light", ClassicLightPalette, CLASSIC_LIGHT_ROWS),
                Ramp("classic-dark", ClassicDarkPalette, CLASSIC_DARK_ROWS),
                Ramp("neon-light", NeonLightPalette, NEON_ROWS),
                Ramp("neon-dark", NeonDarkPalette, NEON_ROWS),
            )

        /** DESIGN_SYSTEM.md §2.9: Classic light, Classic dark, Neon light, Neon dark. */
        val CHROME_PAIRS =
            listOf(
                ChromePair("onSurface on surface", 4.5, listOf(16.13, 13.55, 15.31, 14.99)) {
                    it.onSurface to it.surface
                },
                ChromePair("onSurfaceVariant on surface", 4.5, listOf(8.79, 10.29, 8.39, 11.32)) {
                    it.onSurfaceVariant to it.surface
                },
                ChromePair(
                    "onSurface on surfaceContainerHigh",
                    4.5,
                    listOf(14.01, 11.15, 13.93, 11.15),
                ) {
                    it.onSurface to it.surfaceContainerHigh
                },
                ChromePair(
                    "onSurfaceVariant on surfaceContainerHigh",
                    4.5,
                    listOf(7.64, 8.47, 7.64, 8.42),
                ) {
                    it.onSurfaceVariant to it.surfaceContainerHigh
                },
                ChromePair(
                    "onSurface on surfaceContainerLow",
                    4.5,
                    listOf(15.50, 13.25, 15.49, 13.27),
                ) {
                    it.onSurface to it.surfaceContainerLow
                },
                ChromePair("primary on surface", 4.5, listOf(6.28, 10.29, 6.92, 11.36)) {
                    it.primary to it.surface
                },
                ChromePair(
                    "primary on surfaceContainerHigh",
                    4.5,
                    listOf(5.46, 8.47, 6.30, 8.45),
                ) {
                    it.primary to it.surfaceContainerHigh
                },
                ChromePair("onPrimary on primary", 4.5, listOf(6.68, 7.73, 7.74, 7.71)) {
                    it.onPrimary to it.primary
                },
                ChromePair(
                    "onPrimaryContainer on primaryContainer",
                    4.5,
                    listOf(4.54, 5.85, 4.55, 4.55),
                ) {
                    it.onPrimaryContainer to it.primaryContainer
                },
                ChromePair(
                    "onSecondaryContainer on secondaryContainer",
                    4.5,
                    listOf(4.60, 4.56, 4.54, 4.55),
                ) {
                    it.onSecondaryContainer to it.secondaryContainer
                },
                ChromePair(
                    "onTertiaryContainer on tertiaryContainer",
                    4.5,
                    listOf(4.53, 5.85, 4.54, 4.54),
                ) {
                    it.onTertiaryContainer to it.tertiaryContainer
                },
                ChromePair(
                    "inverseOnSurface on inverseSurface",
                    4.5,
                    listOf(11.53, 10.12, 11.52, 10.14),
                ) {
                    it.inverseOnSurface to it.inverseSurface
                },
                ChromePair(
                    "inversePrimary on inverseSurface",
                    4.5,
                    listOf(7.68, 4.99, 7.69, 5.00),
                ) {
                    it.inversePrimary to it.inverseSurface
                },
                ChromePair("outline on surface", 3.0, listOf(4.23, 5.55, 4.02, 6.11)) {
                    it.outline to it.surface
                },
                ChromePair(
                    "primary focus ring on background",
                    3.0,
                    listOf(6.28, 10.29, 6.92, 11.36),
                ) {
                    it.primary to it.background
                },
            )

        /** Machado, Oliveira & Fernandes 2009, severity 1.0 (DESIGN_SYSTEM.md §2.13). */
        val CVD =
            listOf(
                "protan" to
                    arrayOf(
                        doubleArrayOf(0.152286, 1.052583, -0.204868),
                        doubleArrayOf(0.114503, 0.786281, 0.099216),
                        doubleArrayOf(-0.003882, -0.048116, 1.051998),
                    ),
                "deutan" to
                    arrayOf(
                        doubleArrayOf(0.367322, 0.860646, -0.227968),
                        doubleArrayOf(0.280085, 0.672501, 0.047413),
                        doubleArrayOf(-0.011820, 0.042940, 0.968881),
                    ),
                "tritan" to
                    arrayOf(
                        doubleArrayOf(1.255528, -0.076749, -0.178779),
                        doubleArrayOf(-0.078411, 0.930809, 0.147602),
                        doubleArrayOf(0.004733, 0.691367, 0.303900),
                    ),
            )

        /** `#RRGGBB` of an opaque [color], upper case without the `#`. */
        fun hex(color: Color): String = "%06X".format(Locale.ROOT, color.toArgb() and 0xFFFFFF)

        fun round2(x: Double): Double = round(x * 100.0) / 100.0

        /** The three linear sRGB channels of [color] (§2.13 `srgbToLinear`). */
        fun linear(color: Color): DoubleArray {
            val argb = color.toArgb()
            return doubleArrayOf(
                channel((argb shr 16) and 0xFF),
                channel((argb shr 8) and 0xFF),
                channel(argb and 0xFF),
            )
        }

        fun channel(v8: Int): Double {
            val c = v8 / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        fun luminance(color: Color): Double {
            val (r, g, b) = linear(color)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** OKLab ΔE × 100 of [a] and [b], each first simulated with [matrix] (null: normal). */
        fun deltaE(a: Color, b: Color, matrix: Array<DoubleArray>?): Double {
            val x = oklab(simulate(linear(a), matrix))
            val y = oklab(simulate(linear(b), matrix))
            val dl = x[0] - y[0]
            val da = x[1] - y[1]
            val db = x[2] - y[2]
            return 100.0 * sqrt(dl * dl + da * da + db * db)
        }

        fun simulate(rgb: DoubleArray, matrix: Array<DoubleArray>?): DoubleArray =
            if (matrix == null) {
                rgb
            } else {
                DoubleArray(3) { i ->
                    (matrix[i][0] * rgb[0] + matrix[i][1] * rgb[1] + matrix[i][2] * rgb[2])
                        .coerceIn(0.0, 1.0)
                }
            }

        fun oklab(rgb: DoubleArray): DoubleArray {
            val (r, g, b) = rgb
            val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            return doubleArrayOf(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
            )
        }
    }
}
