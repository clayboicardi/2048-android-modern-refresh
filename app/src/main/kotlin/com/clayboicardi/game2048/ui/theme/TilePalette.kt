package com.clayboicardi.game2048.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Tile colours of one palette and mode (D-071 verbatim; DESIGN_SYSTEM.md §2.1). `exp` is the tile
 * exponent (value = 2^exp), `exp >= 1`: implementations index a 17-entry ramp with
 * `exp.coerceAtMost(17)` and `require(exp >= 1)`. An empty cell (exp 0) is drawn with [empty] and
 * never asks for a fill. Dynamic colour never replaces any of these (DESIGN_SYSTEM.md §2.11).
 */
interface TilePalette {
    fun fill(exp: Int): Color

    fun ink(exp: Int): Color

    fun glow(exp: Int): Glow?

    val board: Color
    val empty: Color
    val background: Color
}

/**
 * A tile glow (DESIGN_SYSTEM.md §4.6, D-078). The outer `dropShadow` uses [color] at [alpha] with
 * radius `radiusFraction × tile side` and spread `spreadFraction × tile side`; the inner highlight
 * uses [innerColor] at [innerAlpha] with an effective radius of `innerRadiusDp +
 * innerRadiusFraction × tile side`, offset down by `innerOffsetYFraction × tile side`.
 */
@Immutable
data class Glow(
    val color: Color,
    val alpha: Float,
    val radiusFraction: Float,
    val spreadFraction: Float,
    val innerColor: Color,
    val innerAlpha: Float,
    val innerRadiusDp: Float,
    val innerRadiusFraction: Float,
    val innerOffsetYFraction: Float,
)

/**
 * The geometry and type of a palette, beside [TilePalette], which stays exactly D-071
 * (DESIGN_SYSTEM.md §2.1; `titleColor`: D-189).
 */
@Immutable
data class TileSkin(
    /**
     * "classic-light" | "classic-dark" | "neon-light" | "neon-dark", plus "-hc" when high contrast;
     * also the `paletteId` of the text-layout cache key (D-025).
     */
    val id: String,
    /** r of D-075: 0.14 Classic, 0.18 Neon. */
    val gapRatio: Float,
    /** D-075: tile radius = `radiusFraction × tile side`, clamped to [radiusMin]..[radiusMax]. */
    val radiusFraction: Float,
    val radiusMin: Dp,
    val radiusMax: Dp,
    /** 800 Classic, 900 Neon (D-076). */
    val numeralWeight: Int,
    /** 0 Classic, 100 Neon (D-076). */
    val numeralRond: Float,
    /** The "2048" wordmark (DESIGN_SYSTEM.md §2.2). */
    val titleColor: Color,
    /** Neon only (DESIGN_SYSTEM.md §4.5). */
    val boardInsetWell: Boolean,
    /** High contrast only: `colorScheme.outline` (D-073). */
    val tileOutline: Color?,
)

/** The active tile palette; provided by `GameTheme` (DESIGN_SYSTEM.md §2.1). */
@Suppress("ComposeCompositionLocalUsage") // DESIGN_SYSTEM.md §2.1: set once by GameTheme
val LocalTilePalette = staticCompositionLocalOf<TilePalette> { error("GameTheme not applied") }

/** The active tile skin; provided by `GameTheme` (DESIGN_SYSTEM.md §2.1). */
@Suppress("ComposeCompositionLocalUsage") // DESIGN_SYSTEM.md §2.1: set once by GameTheme
val LocalTileSkin = staticCompositionLocalOf<TileSkin> { error("GameTheme not applied") }
