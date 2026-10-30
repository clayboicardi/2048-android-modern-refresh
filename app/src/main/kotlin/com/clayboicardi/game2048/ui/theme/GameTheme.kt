package com.clayboicardi.game2048.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.MotionProbe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The app theme (D-026, D-070–D-074, D-184, D-189, D-248; DESIGN_SYSTEM.md §2; MOTION_SPEC.md §2.1,
 * §2.2, §3.15). It provides
 * - [LocalTilePalette] and [LocalTileSkin]: [palette] in light or [darkTheme] mode
 *   ([resolveTilePalette]), decorated for high contrast ([HighContrastPalette], [highContrastSkin])
 *   when [highContrast] is on or the system contrast level [systemContrast] forces it
 *   ([systemForcesHighContrast]; the source reports 0 below API 34);
 * - [LocalEffectiveMotion]: `effectiveMotion(motion, animatorScale)`, from the persisted Motion
 *   setting (D-042) and the live system animator duration scale (`AnimatorScaleSource.scale`);
 * - `MaterialExpressiveTheme` with the palette's chrome scheme ([chromeScheme], DESIGN_SYSTEM.md
 *   §2.8) and `gameMotionScheme(level)`. With [dynamicChrome] on API 31+ the scheme is the system's
 *   dynamic one with `background` and `surface` set to the palette background (§2.11); tiles,
 *   board, empty cells, glows, the title colour and the high-contrast ink always come from the
 *   palette (F12-AC7). Below API 31 [dynamicChrome] is ignored (F12-AC8).
 *
 * A change of these colours while the composition lives crossfades under Full (MOTION_SPEC.md
 * §3.15): one `Animatable` fraction 0 → 1 with `slowEffects`, every `TilePalette` and `ColorScheme`
 * role `lerp(old, new, fraction)`, and so are the skin's title and high-contrast outline colours.
 * Tile and board colours are read in draw; the chrome scheme recomposes its consumers for the ~19
 * frames. The skin's geometry and numerals change on the first frame. Under Reduced and Off the new
 * colours are shown on F0. With a `LocalMotionProbe`, the fraction is its palette reader, the
 * crossfade one of its running readers and the crossfade's first frame its `paletteOriginMs`.
 */
@Suppress("LongParameterList") // D-248: the GameTheme signature, one parameter per appearance input
@Composable
fun GameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: PaletteId = PaletteId.CLASSIC,
    highContrast: Boolean = false,
    systemContrast: Float = 0f,
    dynamicChrome: Boolean = false,
    motion: MotionPreference = MotionPreference.FULL,
    animatorScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val level = effectiveMotion(motion, animatorScale)
    val contrast = highContrast || systemForcesHighContrast(systemContrast)
    val dynamic = dynamicChrome && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val target =
        remember(palette, darkTheme, contrast, dynamic, context) {
            resolveTheme(palette, darkTheme, contrast, if (dynamic) context else null)
        }
    val shown = themeCrossfade(target, full = level == MotionLevel.FULL)
    CompositionLocalProvider(
        LocalTilePalette provides shown.palette,
        LocalTileSkin provides shown.skin,
        LocalEffectiveMotion provides level,
    ) {
        MaterialExpressiveTheme(
            colorScheme = shown.scheme,
            motionScheme = gameMotionScheme(level),
            content = content,
        )
    }
}

/** A palette option as it is drawn: its tile palette and skin (DESIGN_SYSTEM.md §2.1; D-234). */
@Immutable data class ResolvedPalette(val palette: TilePalette, val skin: TileSkin)

/**
 * The tile palette and skin of palette [id] in light or [dark] mode, without high contrast
 * (DESIGN_SYSTEM.md §2.1–§2.5; D-070, D-071, D-234): what `GameTheme` provides before applying high
 * contrast, and what the Palette dialog's swatches draw for each option.
 */
fun resolveTilePalette(id: PaletteId, dark: Boolean): ResolvedPalette =
    when (id) {
        PaletteId.CLASSIC -> if (dark) ClassicDark else ClassicLight
        PaletteId.NEON -> if (dark) NeonDark else NeonLight
    }

/** The static chrome scheme of palette [id] in light or [dark] mode (DESIGN_SYSTEM.md §2.8). */
fun chromeScheme(id: PaletteId, dark: Boolean): ColorScheme =
    when (id) {
        PaletteId.CLASSIC -> if (dark) ClassicDarkScheme else ClassicLightScheme
        PaletteId.NEON -> if (dark) NeonDarkScheme else NeonLightScheme
    }

private val ClassicLight = ResolvedPalette(ClassicLightPalette, ClassicLightSkin)
private val ClassicDark = ResolvedPalette(ClassicDarkPalette, ClassicDarkSkin)
private val NeonLight = ResolvedPalette(NeonLightPalette, NeonLightSkin)
private val NeonDark = ResolvedPalette(NeonDarkPalette, NeonDarkSkin)

/** What `GameTheme` provides: the tile palette, the skin and the chrome scheme. */
@Stable
private class ThemeColors(val palette: TilePalette, val skin: TileSkin, val scheme: ColorScheme)

/**
 * The colours of palette [id] in light or [dark] mode, with high [contrast] and, given a
 * [dynamicContext] (API 31+ only), the dynamic chrome scheme over the palette background.
 */
private fun resolveTheme(
    id: PaletteId,
    dark: Boolean,
    contrast: Boolean,
    dynamicContext: Context?,
): ThemeColors {
    val base = resolveTilePalette(id, dark)
    val scheme =
        if (dynamicContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicScheme(dynamicContext, dark, base.palette.background)
        } else {
            chromeScheme(id, dark)
        }
    return if (contrast) {
        ThemeColors(
            HighContrastPalette(base.palette),
            highContrastSkin(base.skin, scheme.outline),
            scheme,
        )
    } else {
        ThemeColors(base.palette, base.skin, scheme)
    }
}

/** The dynamic chrome of [context] with `background` and `surface` = [background] (§2.11). */
@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicScheme(context: Context, dark: Boolean, background: Color): ColorScheme {
    val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return dynamic.copy(background = background, surface = background)
}

/**
 * The colours shown for [target] (MOTION_SPEC.md §3.15): a crossfade from the colours shown so far
 * when [target] changes under [full] motion, else [target] itself.
 */
@Composable
private fun themeCrossfade(target: ThemeColors, full: Boolean): ThemeColors {
    val probe = LocalMotionProbe.current
    val last = remember { LastShown() }
    val crossfade = remember(target) { ThemeCrossfade(last.colors, target, animate = full) }
    LaunchedEffect(crossfade) { crossfade.run(probe) }
    DisposableEffect(probe, last) {
        val registrations =
            if (probe == null) {
                emptyList()
            } else {
                listOf(
                    probe.registerPalette { last.crossfade?.fraction?.value ?: 1f },
                    probe.registerRunning { last.crossfade?.fraction?.isRunning == true },
                )
            }
        onDispose { registrations.forEach { it.unregister() } }
    }
    val shown = crossfade.shown(full)
    SideEffect {
        last.colors = shown
        last.crossfade = crossfade
    }
    return shown
}

/** What the last composition provided; a plain holder, written only from `SideEffect`. */
private class LastShown {
    var colors: ThemeColors? = null
    var crossfade: ThemeCrossfade? = null
}

/**
 * One crossfade to [to] (MOTION_SPEC.md §3.15) from [from], the colours shown when it is made; a
 * palette still crossfading is read once at its current fraction ([BakedPalette]), so crossfades
 * never nest. Without [from] (the first composition) or unless [animate], it is done from the start
 * and shows [to].
 */
@Stable
private class ThemeCrossfade(from: ThemeColors?, val to: ThemeColors, animate: Boolean) {
    private val start: ThemeColors? =
        if (animate && from != null) {
            ThemeColors(BakedPalette.of(from.palette), from.skin, from.scheme)
        } else {
            null
        }

    /** 0 → 1 while it runs; 1 when it does not animate. */
    val fraction: Animatable<Float, AnimationVector1D> = Animatable(if (start == null) 1f else 0f)

    private val mixed: TilePalette? = start?.let { CrossfadeTilePalette(it.palette, to.palette) }

    private var done by mutableStateOf(start == null)

    /**
     * [to], or while it runs under [full] motion the colours at [fraction]: the palette, the
     * scheme, and the skin's title and outline colours on [to]'s skin (its geometry and numerals
     * are [to]'s from the first frame). A missing outline is the other one at alpha 0.
     */
    fun shown(full: Boolean): ThemeColors {
        val from = start?.takeIf { full && !done }
        val palette = mixed
        return if (from == null || palette == null) to else at(from, palette)
    }

    private fun at(from: ThemeColors, palette: TilePalette): ThemeColors {
        // MOTION_SPEC.md §3.15, §7.2: the chrome scheme is a composition value, so its ~19 frames
        // are the one documented read of an animated value in composition.
        val f = fraction.value
        val skin =
            to.skin.copy(
                titleColor = lerp(from.skin.titleColor, to.skin.titleColor, f),
                tileOutline = lerpOutline(from.skin.tileOutline, to.skin.tileOutline, f),
            )
        return ThemeColors(palette, skin, lerpColorScheme(from.scheme, to.scheme, f))
    }

    /**
     * Runs [fraction] to 1. With a [probe], the frame on which it starts (play time 0) is its
     * [MotionProbe.paletteOriginMs]: a frame callback registered with the animation's first one.
     */
    suspend fun run(probe: MotionProbe?) {
        if (done) return
        coroutineScope {
            if (probe != null) {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    withFrameNanos { probe.paletteOriginMs = it / NANOS_PER_MS }
                }
            }
            fraction.animateTo(1f, GameMotion.slowEffects(GameMotion.FRACTION_THRESHOLD))
        }
        done = true
    }

    /**
     * [from] mixed into [to] by [fraction], read in the caller's phase: tiles and the board read
     * their colours in draw, so the crossfade invalidates their draw only. Glows mix field by field
     * ([lerpGlow]); the mixed glows of one fraction are made once.
     */
    private inner class CrossfadeTilePalette(val from: TilePalette, val to: TilePalette) :
        TilePalette {
        private val glows = arrayOfNulls<Glow>(RAMP_SIZE)
        private var glowsAt = Float.NaN

        override fun fill(exp: Int): Color = lerp(from.fill(exp), to.fill(exp), fraction.value)

        override fun ink(exp: Int): Color = lerp(from.ink(exp), to.ink(exp), fraction.value)

        override fun glow(exp: Int): Glow? {
            val f = fraction.value
            if (f != glowsAt) {
                glows.fill(null)
                glowsAt = f
            }
            val i = exp.coerceIn(1, RAMP_SIZE) - 1
            return glows[i] ?: lerpGlow(from.glow(exp), to.glow(exp), f)?.also { glows[i] = it }
        }

        override val board: Color
            get() = lerp(from.board, to.board, fraction.value)

        override val empty: Color
            get() = lerp(from.empty, to.empty, fraction.value)

        override val background: Color
            get() = lerp(from.background, to.background, fraction.value)
    }

    /** A palette's colours read once: the start of a crossfade that interrupts another. */
    private class BakedPalette(source: TilePalette) : TilePalette {
        private val fills = Array(RAMP_SIZE) { source.fill(it + 1) }
        private val inks = Array(RAMP_SIZE) { source.ink(it + 1) }
        private val glows = Array(RAMP_SIZE) { source.glow(it + 1) }

        override fun fill(exp: Int): Color = fills[rampIndex(exp)]

        override fun ink(exp: Int): Color = inks[rampIndex(exp)]

        override fun glow(exp: Int): Glow? = glows[rampIndex(exp)]

        override val board: Color = source.board
        override val empty: Color = source.empty
        override val background: Color = source.background

        companion object {
            /** [palette] itself, or its current colours while it crossfades. */
            fun of(palette: TilePalette): TilePalette =
                if (palette is CrossfadeTilePalette) BakedPalette(palette) else palette
        }
    }
}

/**
 * [a] mixed into [b] by [f], field by field (MOTION_SPEC.md §3.15): a missing glow is the other at
 * alpha 0 (outer and inner); `null` when both are missing.
 */
private fun lerpGlow(a: Glow?, b: Glow?, f: Float): Glow? {
    val x = a ?: b ?: return null
    val y = b ?: x
    return Glow(
        color = lerp(x.color, y.color, f),
        alpha = mix(a?.alpha ?: 0f, b?.alpha ?: 0f, f),
        radiusFraction = mix(x.radiusFraction, y.radiusFraction, f),
        spreadFraction = mix(x.spreadFraction, y.spreadFraction, f),
        innerColor = lerp(x.innerColor, y.innerColor, f),
        innerAlpha = mix(a?.innerAlpha ?: 0f, b?.innerAlpha ?: 0f, f),
        innerRadiusDp = mix(x.innerRadiusDp, y.innerRadiusDp, f),
        innerRadiusFraction = mix(x.innerRadiusFraction, y.innerRadiusFraction, f),
        innerOffsetYFraction = mix(x.innerOffsetYFraction, y.innerOffsetYFraction, f),
    )
}

/** [a] mixed into [b] by [f]; a missing outline is the other at alpha 0, none when both are. */
private fun lerpOutline(a: Color?, b: Color?, f: Float): Color? {
    val x = a ?: b ?: return null
    val y = b ?: x
    return lerp(a ?: x.copy(alpha = 0f), b ?: y.copy(alpha = 0f), f)
}

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/** The index of [exp] in a 17-entry ramp (DESIGN_SYSTEM.md §2.1). */
private fun rampIndex(exp: Int): Int {
    require(exp >= 1) { "Tile exponent must be >= 1, was $exp" }
    return exp.coerceAtMost(RAMP_SIZE) - 1
}

/** The ramp length (DESIGN_SYSTEM.md §2.1): exponents ≥ 17 share the last entry. */
private const val RAMP_SIZE = 17

private const val NANOS_PER_MS = 1_000_000L
