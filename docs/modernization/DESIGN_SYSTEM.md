# DESIGN_SYSTEM.md: visual and interaction design system for the 2048 native rewrite

Status: plan document, dated 2026-10-28. It implements the visual, layout, accessibility and identity decisions of `DECISIONS.md` (D-006–D-009, D-060–D-066, D-070–D-085, D-090–D-095, D-100–D-106, D-110–D-115, D-122–D-124, D-147), including the entries added during planning (D-181, D-186–D-190, D-199–D-202, D-204–D-207). `DECISIONS.md` is canonical: where this file and `DECISIONS.md` disagree, `DECISIONS.md` wins. Every place where this file interprets or extends a decision cites the entry that records it.

Audience: the implementer agents (D-194) executing milestones M3 (board, motion), M4 (screens, theming, layout, a11y) and M5 (identity, store assets). Everything here is meant to be implementable without other context: exact hexes, dp/sp values, formulas, file paths, composable names, strings and golden names.

Consumers: `QUALITY.md` takes the golden list in §10 and the contrast algorithm in §2.13.

---

## 0. Conventions

- **Units.** `dp` for geometry, `sp` for chrome text, `em` relative to the font size. Tile numerals are sized in dp (D-077).
- **Colors** are opaque sRGB hex. Contrast is WCAG 2.x. ΔE is OKLab Euclidean distance ×100 (method in §2.13).
- **"Computed 2026-10-28"** marks numbers recomputed for this file:
  - chrome schemes with material-color-utilities `SchemeContent` (spec 2021, contrast level 0.0) through the `materialyoucolor` 3.0.4 Python port; they match every role research/04-design-motion.md §8 lists;
  - contrast and ΔE with the research/04 color library;
  - the font subset with fontTools 4.68, using D-076's exact recipe.
- **Verified 2026-10-28 (class files)** marks API facts read from the `material3-android-1.5.0-beta02`, `foundation-*-1.12.1`, `ui-android-1.12.1` and `roborazzi-1.77.0` binaries with `javap`: opt-in markers (`ExperimentalMaterial3Api`, `ExperimentalMaterial3ExpressiveApi`, `ExperimentalRoborazziApi`) and `@Deprecated`.
- **Code locations** are in `:app`, under `app/src/main/kotlin/com/clayboicardi/game2048/`. Packages follow D-020:
  - `ui/theme/` holds every token: colors, fonts, type, shapes, dimensions and `GameMotion`. Detekt's `MagicNumber` rule is relaxed only there (D-144), so **no literal hex, dp or sp appears outside `ui/theme/`**. The pure board geometry (`game/board/BoardGeometry.kt`, §4.2) and the layout-mode selector (`ui/components/WindowMode.kt`, §5.1) live outside it and read every dp value and breakpoint from `GameDimens` and `TileSkin`.
  - `ui/components/` holds reusable components. With `ui/theme/`, it is the only place that calls material3-1.5-only APIs (D-026).
  - Screens live in `game/`, `settings/`, `stats/` and `about/`.
- **String resources.** Each piece of UI copy is given as `key` = "English text". All strings live in `app/src/main/res/values/strings.xml`. Every key is translated into the 20 locales in M5 (D-100), except keys marked `translatable="false"`.

---

## 1. Design principles

The rewrite should feel premium, calm and fast, and still be recognizably *the* 2048. Each principle comes with rules that can be checked.

| # | Principle | Rules |
|---|---|---|
| P1 | **The board is the hero** | Chrome is tonal (M3 surface containers). It has no drop shadows and no gradients. The Game screen uses at most two accent roles (`primary`, `secondaryContainer`) besides the tile ramp. There is no top app bar on Game (D-023). |
| P2 | **Honor the original identity** | Default palette Classic Refined keeps the cream → orange → gold ramp. Legacy hexes that already pass stay unchanged: background `#FAF8EF`, board `#BBADA0`, 2-tile `#EEE4DA`, super `#3C3A32`, title color `#776E65`. Tiles stay rounded squares. The "2048" wordmark uses a heavy rounded-sans digit face. The 2048 tile `#E6A11A` / `#3A2900` is also the launcher icon (D-111). |
| P3 | **Fast: input never waits** | Every accepted move applies at once (D-052). Slides use the 0.9/1400 spring (D-060). Overlays become interactive at ≤ 800 ms (D-008). The splash is held only while state loads, capped at 1000 ms (D-112). There are no loading spinners. |
| P4 | **Calm** | No infinite animation, and nothing flashes more than 3 times per second (D-063). At most one haptic per move (D-065). No moves/time HUD (D-006). Confirmation appears only when work would be lost (D-007). No badges, no notifications. Snackbars are short and never stack. |
| P5 | **Legible everywhere** | The number is always printed, so color is never the only cue (D-084). Tile-ink contrast is ≥ 3.0:1 for 1–3 digits and ≥ 4.5:1 for 4+ digits (D-083). Tile numerals are fitted to the cell (§3.3). Game numerals use Latin digits (D-103). Translated text always uses the system font (D-076). |
| P6 | **Respect the system and the player** | Follows system dark mode, contrast (API 34+), animator scale, touch-feedback setting, font scale up to 2.0 and per-app language. Offline, no ads. Undo counts are shown honestly (D-044, D-008). |
| P7 | **One source of truth** | Screens read tokens only (`MaterialTheme.colorScheme`, `LocalTilePalette`, `LocalTileSkin`, `GameDimens`, `GameTypography`, `GameMotion`). Palettes are data. Adding a palette means adding one object and its golden. |

---

## 2. Color

### 2.1 Token architecture

Tile colors come from the palette and chrome colors come from an M3 `ColorScheme`. They are independent: dynamic color (§2.11) can replace the chrome, but never a tile, the board or the background.

```kotlin
// ui/theme/TilePalette.kt
interface TilePalette {                 // D-071 verbatim; exp = tile exponent (value = 2^exp), exp >= 1
    fun fill(exp: Int): Color
    fun ink(exp: Int): Color
    fun glow(exp: Int): Glow?
    val board: Color
    val empty: Color
    val background: Color
}

@Immutable
data class Glow(                        // §4.6; effective inner radius = innerRadiusDp + innerRadiusFraction × tile side
    val color: Color, val alpha: Float,
    val radiusFraction: Float, val spreadFraction: Float,      // × tile side
    val innerColor: Color, val innerAlpha: Float,
    val innerRadiusDp: Float, val innerRadiusFraction: Float, val innerOffsetYFraction: Float,
)

@Immutable
data class TileSkin(                    // beside TilePalette, which stays exactly D-071 (titleColor: D-189)
    val id: String,                     // "classic-light" | "classic-dark" | "neon-light" | "neon-dark", plus "-hc" when high contrast
    val gapRatio: Float,                // r of D-075: 0.14 Classic, 0.18 Neon
    val radiusFraction: Float, val radiusMin: Dp, val radiusMax: Dp,   // D-075
    val numeralWeight: Int,             // 800 Classic, 900 Neon (D-076)
    val numeralRond: Float,             // 0 Classic, 100 Neon (D-076)
    val titleColor: Color,              // §2.2
    val boardInsetWell: Boolean,        // Neon only (§4.5)
    val tileOutline: Color?,            // high contrast only: colorScheme.outline (D-073)
)

val LocalTilePalette = staticCompositionLocalOf<TilePalette> { error("GameTheme not applied") }
val LocalTileSkin    = staticCompositionLocalOf<TileSkin>    { error("GameTheme not applied") }
```

- `fill(exp)` and `ink(exp)` index a 17-entry array with `exp.coerceAtMost(17)`. They `require(exp >= 1)`; an empty cell (exp 0) is drawn with `empty` and never asks the palette for a fill.
- High contrast is a decorator: `HighContrastPalette(base)` overrides only `ink` (§2.7). `TileSkin.tileOutline` carries the outline.
- The text-layout cache key of D-025 `(exp, tileSizePx, paletteId)` uses `TileSkin.id` as `paletteId`.
- Implementations: `ClassicLightPalette`, `ClassicDarkPalette`, `NeonLightPalette`, `NeonDarkPalette`. The two Neon objects share one tile array and differ only in `background`.

### 2.2 Surfaces, board and identity tokens

| Token | Classic light | Classic dark | Neon light | Neon dark | Source |
|---|---|---|---|---|---|
| `background` (every screen, drawn behind the system bars) | `#FAF8EF` | `#1B1916` | `#F3F1FA` | `#0E0C1A` | research/04 §8 A/C |
| `board` | `#BBADA0` | `#2C2824` | `#1A1730` | `#1A1730` | research/04 §8 A/C |
| `empty` (empty cell) | `#CDC1B4` | `#3A342E` | `#25213F` | `#25213F` | research/04 §8 A/C |
| board vs background (contrast) | 2.06 | 1.20 | 15.53 | 1.11 | computed 2026-10-28 |
| empty vs board | 1.24 | 1.19 | 1.13 | 1.13 | computed |
| `titleColor` ("2048" wordmark) | `#776E65` (4.70:1, legacy title color) | `#D1C4BA` (10.29:1) | `#4B4185` (7.84:1) | `#ECF33C` (16.07:1) | D-189 (P2) |
| Board effect | flat | flat | inset well (§4.5) | inset well | D-078 |
| Chrome seed (`SchemeContent`) | `#8F7A66` | `#8F7A66` | `#5B5BD6` | `#5B5BD6` | D-070, D-071 |

- Board, empty-cell and board-edge contrasts are decorative. WCAG SC 1.4.11 does not apply, because every tile prints its number (research/04 §6.4). With high contrast on, each empty cell also gets a stroke (§2.7, D-206).
- The Neon board is always dark, even in the light theme (D-071).
- `titleColor` belongs to the palette, so dynamic chrome (§2.11) never changes it.

### 2.3 Classic Refined, light: tile ramp

Columns:
- **CR** is ink-on-fill contrast.
- **Floor** is the D-083 minimum: 3.0 for 1–3 digits, 4.5 for 4 or more digits and for compact labels.
- **L** is OKLCH lightness.
- **ΔE n/p/d/t** is the difference from the previous exponent under normal, protan, deutan and tritan vision (§2.12).
- **HC ink** and **HC CR** apply when high contrast is on (§2.7).

Exponents 1–11 come from research/04 §8 A and 12–17 from D-072. All values were computed 2026-10-28.

| Exp | Value | Fill | Ink | CR | Floor | vs empty | L | ΔE n/p/d/t | HC ink | HC CR |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 2 | `#EEE4DA` | `#5C5248` | 6.08 | 3.0 | 1.41 | 0.924 | — | `#000000` | 16.75 |
| 2 | 4 | `#EDDBBE` | `#5C5248` | 5.62 | 3.0 | 1.30 | 0.899 | 3.7/4.0/3.6/3.0 | `#000000` | 15.48 |
| 3 | 8 | `#F3B27A` | `#4A2A0E` | 7.04 | 3.0 | 1.04 | 0.812 | 10.8/12.1/9.7/11.6 | `#000000` | 11.46 |
| 4 | 16 | `#F69664` | `#401C06` | 6.83 | 3.0 | 1.26 | 0.763 | 6.4/6.9/4.8/7.1 | `#000000` | 9.47 |
| 5 | 32 | `#D9613F` | `#FFFFFF` | **3.66** | 3.0 | 2.07 | 0.636 | 13.3/14.7/12.5/12.2 | `#000000` | 5.73 |
| 6 | 64 | `#DE4A26` | `#FFFFFF` | 4.11 | 3.0 | 2.32 | 0.612 | 4.0/4.6/2.6/4.5 | `#000000` | 5.11 |
| 7 | 128 | `#F3D98A` | `#45360A` | 8.46 | 3.0 | 1.27 | 0.890 | 32.1/35.8/27.0/32.8 | `#000000` | 15.10 |
| 8 | 256 | `#F0CC63` | `#45360A` | 7.58 | 3.0 | 1.14 | 0.856 | 4.4/5.0/4.2/3.8 | `#000000` | 13.52 |
| 9 | 512 | `#EDBF45` | `#3F3006` | 7.42 | 3.0 | 1.02 | 0.824 | 3.5/4.0/3.4/3.5 | `#000000` | 12.14 |
| 10 | 1024 | `#EAB12C` | `#3D2D04` | 6.87 | 4.5 | 1.10 | 0.792 | 3.5/3.9/3.2/3.6 | `#000000` | 10.82 |
| 11 | 2048 | `#E6A11A` | `#3A2900` | 6.33 | 4.5 | 1.25 | 0.757 | 3.9/4.3/3.5/4.0 | `#000000` | 9.48 |
| 12 | 4096 | `#3C3A32` | `#FFFFFF` | 11.39 | 4.5 | 6.45 | 0.348 | 43.3/39.4/44.2/43.3 | `#FFFFFF` | 11.39 |
| 13 | 8192 | `#013D3D` | `#FFFFFF` | 12.10 | 4.5 | 6.84 | 0.326 | 6.3/2.1/4.1/6.6 | `#FFFFFF` | 12.10 |
| 14 | 16384 | `#062D59` | `#FFFFFF` | 13.75 | 4.5 | 7.78 | 0.299 | 8.3/8.1/8.4/2.9 | `#FFFFFF` | 13.75 |
| 15 | 32768 | `#36164A` | `#FFFFFF` | 15.32 | 4.5 | 8.67 | 0.276 | 8.9/5.5/3.0/9.0 | `#FFFFFF` | 15.32 |
| 16 | 65536 | `#420420` | `#FFFFFF` | 16.54 | 4.5 | 9.36 | 0.251 | 8.4/8.6/8.1/7.9 | `#FFFFFF` | 16.54 |
| ≥ 17 | ≥ 131072 | `#201710` | `#FFFFFF` | 17.63 | 4.5 | 9.97 | 0.214 | 9.4/3.5/4.2/9.1 | `#FFFFFF` | 17.63 |

- Minimum CR is 3.66 (tile 32, a documented exception that passes the 1–3 digit floor; D-083, D-171).
- Minimum adjacent ΔE n/p/d/t is **3.5 / 2.1 / 2.6 / 2.9**, all at or above the gates of 3.0 normal and 2.0 CVD.
- Minimum HC CR is 5.11 (tile 64).

### 2.4 Classic Refined, dark: tile ramp

| Exp | Value | Fill | Ink | CR | Floor | vs empty | L | ΔE n/p/d/t | HC ink | HC CR |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 2 | `#524A42` | `#F1E9E0` | 7.23 | 3.0 | 1.41 | 0.414 | — | `#FFFFFF` | 8.69 |
| 2 | 4 | `#605545` | `#F6EDDE` | 6.27 | 3.0 | 1.69 | 0.456 | 4.3/4.1/4.4/4.2 | `#FFFFFF` | 7.28 |
| 3 | 8 | `#D99A64` | `#2E1806` | 7.01 | 3.0 | 5.12 | 0.736 | 29.0/25.8/29.4/29.7 | `#000000` | 8.76 |
| 4 | 16 | `#DE8455` | `#2A1204` | 6.35 | 3.0 | 4.41 | 0.700 | 5.1/5.3/3.4/5.5 | `#000000` | 7.54 |
| 5 | 32 | `#C95A3F` | `#FFFFFF` | 4.19 | 3.0 | 2.93 | 0.602 | 10.5/11.6/9.7/10.3 | `#000000` | 5.02 |
| 6 | 64 | `#C0412A` | `#FFFFFF` | 5.21 | 3.0 | 2.36 | 0.552 | 5.4/6.3/4.8/5.4 | `#FFFFFF` | 5.21 |
| 7 | 128 | `#DCC572` | `#2E2405` | 8.95 | 3.0 | 7.17 | 0.825 | 31.1/34.4/26.6/31.2 | `#000000` | 12.27 |
| 8 | 256 | `#D9B754` | `#2C2204` | 8.11 | 3.0 | 6.34 | 0.790 | 4.0/4.5/3.8/3.9 | `#000000` | 10.85 |
| 9 | 512 | `#D6A93D` | `#2A1F02` | 7.41 | 3.0 | 5.61 | 0.757 | 3.6/4.0/3.4/3.7 | `#000000` | 9.60 |
| 10 | 1024 | `#D39B2A` | `#281D00` | 6.71 | 4.5 | 4.96 | 0.725 | 3.5/3.9/3.2/3.6 | `#000000` | 8.49 |
| 11 | 2048 | `#D08C1A` | `#251A00` | 6.07 | 4.5 | 4.35 | 0.692 | 3.7/4.0/3.2/3.8 | `#000000` | 7.45 |
| 12 | 4096 | `#F1E8D8` | `#1B1916` | 14.43 | 4.5 | 10.10 | 0.934 | 26.9/30.0/25.9/26.2 | `#000000` | 17.27 |
| 13 | 8192 | `#B1EDEC` | `#1B1916` | 13.53 | 4.5 | 9.47 | 0.905 | 7.8/3.0/5.5/10.5 | `#000000` | 16.19 |
| 14 | 16384 | `#C3D6FE` | `#1B1916` | 12.00 | 4.5 | 8.40 | 0.875 | 7.5/6.5/5.2/5.1 | `#000000` | 14.37 |
| 15 | 32768 | `#E4BDE6` | `#1B1916` | 10.62 | 4.5 | 7.44 | 0.845 | 7.2/5.6/3.5/9.1 | `#000000` | 12.72 |
| 16 | 65536 | `#EFAFB3` | `#1B1916` | 9.57 | 4.5 | 6.70 | 0.815 | 6.9/7.3/6.7/6.1 | `#000000` | 11.45 |
| ≥ 17 | ≥ 131072 | `#FAFAFA` | `#1B1916` | 16.80 | 4.5 | 11.76 | 0.985 | 18.6/20.2/16.8/19.4 | `#000000` | 20.12 |

- Minimum CR is 4.19.
- Minimum ΔE n/p/d/t is **3.5 / 3.0 / 3.2 / 3.6**.
- Minimum HC CR is 5.02 (tile 32).
- **2-tile fill (D-207).** Exponent 1 is `#524A42`, not research/04 §8 A's `#4A433C`: `#4A433C` is only 1.26:1 against the dark empty cell `#3A342E`, while `#524A42` is 1.41:1, matching light mode. Every other Classic dark value is research/04 §8 A exactly, and the minima above are unchanged (computed 2026-10-28). Fallback: if `PaletteContrastTest` fails with `#524A42`, keep `#4A433C` (CR 8.09, vs empty 1.26, L 0.388, row-2 ΔE 6.9/6.7/7.1/6.9, HC CR 9.73) and record it in the PR body.

Dark tiles are about 8 % lower in L than light ones, so they don't glare (research/04 §7.5).

### 2.5 Neon Arcade: tile ramp (identical in light and dark)

Exponents 1–11 come from research/04 §8 C and 12–17 from D-072. The board and empty cell are the same in both modes (§2.2), so every column is mode-independent.

| Exp | Value | Fill | Ink | CR | Floor | vs empty | L | ΔE n/p/d/t | HC ink | HC CR |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 2 | `#323964` | `#FFFFFF` | 11.02 | 3.0 | 1.39 | 0.360 | — | `#FFFFFF` | 11.02 |
| 2 | 4 | `#4B4185` | `#FFFFFF` | 8.77 | 3.0 | 1.75 | 0.421 | 7.3/6.5/6.5/6.2 | `#FFFFFF` | 8.77 |
| 3 | 8 | `#7241A0` | `#FFFFFF` | 7.05 | 3.0 | 2.18 | 0.480 | 8.2/5.0/6.1/8.2 | `#FFFFFF` | 7.05 |
| 4 | 16 | `#A53D9F` | `#FFFFFF` | 5.57 | 3.0 | 2.75 | 0.541 | 9.8/3.4/8.3/11.9 | `#FFFFFF` | 5.57 |
| 5 | 32 | `#CB3C81` | `#FFFFFF` | 4.66 | 3.0 | 3.29 | 0.584 | 9.2/6.7/9.5/9.3 | `#FFFFFF` | 4.66 |
| 6 | 64 | `#F0555B` | `#12101F` | 5.50 | 3.0 | 4.50 | 0.660 | 11.6/11.4/11.9/7.3 | `#000000` | 6.16 |
| 7 | 128 | `#F97C3D` | `#12101F` | 7.13 | 3.0 | 5.83 | 0.720 | 9.7/10.5/7.5/6.4 | `#000000` | 7.98 |
| 8 | 256 | `#FC9F30` | `#12101F` | 9.06 | 3.0 | 7.41 | 0.780 | 8.3/8.8/6.2/7.7 | `#000000` | 10.14 |
| 9 | 512 | `#F7BE2B` | `#12101F` | 11.04 | 3.0 | 9.03 | 0.831 | 7.6/7.5/5.1/7.6 | `#000000` | 12.36 |
| 10 | 1024 | `#F2D935` | `#12101F` | 13.18 | 4.5 | 10.78 | 0.880 | 6.6/6.7/4.8/6.8 | `#000000` | 14.75 |
| 11 | 2048 | `#ECF33C` | `#12101F` | 15.59 | 4.5 | 12.76 | 0.929 | 6.5/5.9/4.3/6.5 | `#000000` | 17.45 |
| 12 | 4096 | `#EEF6FE` | `#12101F` | 17.19 | 4.5 | 14.07 | 0.969 | 20.4/21.1/18.9/6.0 | `#000000` | 19.25 |
| 13 | 8192 | `#AEF9EE` | `#12101F` | 15.71 | 4.5 | 12.85 | 0.930 | 8.1/2.2/4.1/9.2 | `#000000` | 17.58 |
| 14 | 16384 | `#DDD9FE` | `#12101F` | 13.78 | 4.5 | 11.28 | 0.900 | 10.6/7.8/4.7/9.4 | `#000000` | 15.43 |
| 15 | 32768 | `#FBBFE6` | `#12101F` | 12.23 | 4.5 | 10.01 | 0.870 | 7.1/5.7/3.5/8.7 | `#000000` | 13.70 |
| 16 | 65536 | `#FEB6A4` | `#12101F` | 11.11 | 4.5 | 9.09 | 0.839 | 8.5/9.0/8.3/5.0 | `#000000` | 12.44 |
| ≥ 17 | ≥ 131072 | `#FDFDFD` | `#12101F` | 18.44 | 4.5 | 15.09 | 0.994 | 17.8/19.6/15.8/19.5 | `#000000` | 20.65 |

- Minimum CR is 4.66.
- Minimum ΔE n/p/d/t is **6.5 / 2.2 / 3.5 / 5.0**.
- Minimum HC CR is 4.66 (tile 32; its normal ink is already white).

### 2.6 Super-tile rule

- **Exponents 12–16** (4096–65536) each have their own fill, listed above (D-072).
- **Exponents ≥ 17** (131072 and above) all use the exponent-17 entry. That is reachable only on boards of 5×5 and larger: the theoretical maximum is 2^(N²+1), and the engine caps the exponent at 127 (D-030).
- Ink, glow and outline follow the same index: `index = exp.coerceAtMost(17)`.
- **Super-tile effects:**
  - Classic super tiles are **flat**, with no glow (D-078).
  - Neon super tiles keep the Neon glow at alpha 0.65 (§4.6).
- **Labels** follow §3.3–§3.4. Accessibility text always carries the full decimal value (D-080). For exponents ≥ 63 it is computed with `java.math.BigInteger.ONE.shiftLeft(exp).toString()` (`1L shl exp` overflows).
- **Win target is independent of the ramp.** On 3×3 the target is 256 (D-036), and tiles 512 and up still use their normal ramp entries.

### 2.7 High-contrast variant

- **Active when** `settings.highContrast == true`, **or** on API 34+ `UiModeManager.getContrast() >= 0.5f`, observed with `addContrastChangeListener` and removed in `onDispose` (D-073).
  - Below API 34 the system term is `false`.
  - While the system term is true, the Settings row is forced On and disabled (§6.8).
- **Changes:**
  1. `ink(exp)` = `#000000` if `contrast(fill, #000000) >= contrast(fill, #FFFFFF)`, else `#FFFFFF`. The result is the **HC ink** column of §2.3–§2.5. All values were computed 2026-10-28, and the minimum is 4.66:1.
  2. Every tile, including compact-label and mini tiles (§7.8) and the hero badge (§6.3), gets a **1.5 dp** stroke in `MaterialTheme.colorScheme.outline`. It is inset by 0.75 dp so it stays inside the tile, with corner radius `tileRadius − 0.75 dp`.
  3. **Empty cells** (D-206): the single empty-cell `drawBehind` pass (§4.7) also strokes each empty cell with a **1 dp** line in `palette.ink(1)`, inset 0.5 dp, using the cell's corner radius. `ink(1)` against the empty fill is ≥ 4.31:1 in every palette, while the chrome `outline` against Classic light empty cells is only 2.55:1, so `outline` is never used for this stroke.
- **Unchanged:** fills, board, empty-cell fills, glows, the title color and the whole chrome scheme (D-073). Chrome already meets 4.5:1 on every text pair (§2.9).
- **Outline vs board** (decorative, not gated): Classic light 2.06, Classic dark 4.63, Neon light 3.86, Neon dark 5.49. The outline values are those of §2.8: `#7F756D`, `#9A8F86`, `#777585` and `#918F9F`.
- `TileSkin.id` gains the suffix `-hc`, so the text-layout cache never serves a normal-ink layout.

### 2.8 Chrome color schemes (all 48 `ColorScheme` roles)

- `ui/theme/ChromeColorSchemes.kt` defines `ClassicLightScheme`, `ClassicDarkScheme`, `NeonLightScheme` and `NeonDarkScheme`. Each is built with every one of the 48 named parameters of `lightColorScheme(...)` or `darkColorScheme(...)` (public and not deprecated in 1.5.0-beta02; verified 2026-10-28 from class files).
- Values: `SchemeContent` from the seed, with **`background` and `surface` overridden to the palette background** (D-070; for Neon light, D-189), so top app bars and the overlay scrim equal the background.

| Role | Classic light | Classic dark | Neon light | Neon dark |
|---|---|---|---|---|
| `primary` | `#6B5946` | `#DBC2AB` | `#4241BC` | `#C1C1FF` |
| `onPrimary` | `#FFFFFF` | `#3D2D1E` | `#FFFFFF` | `#1B119B` |
| `primaryContainer` | `#85715D` | `#A38D78` | `#5B5BD6` | `#5B5BD6` |
| `onPrimaryContainer` | `#FFFBFF` | `#1D1104` | `#EDEAFF` | `#EDEAFF` |
| `inversePrimary` | `#DBC2AB` | `#6E5B48` | `#C1C1FF` | `#4E4EC9` |
| `secondary` | `#675D54` | `#D2C4B9` | `#5A5A8A` | `#C2C2F8` |
| `onSecondary` | `#FFFFFF` | `#372F27` | `#FFFFFF` | `#2B2C58` |
| `secondaryContainer` | `#EFE0D4` | `#51473F` | `#C8C7FE` | `#464775` |
| `onSecondaryContainer` | `#6D6259` | `#C3B6AB` | `#515281` | `#B7B7ED` |
| `tertiary` | `#5D5D48` | `#C9C8AE` | `#882886` | `#FFAAF5` |
| `onTertiary` | `#FFFFFF` | `#31321F` | `#FFFFFF` | `#5B005C` |
| `tertiaryContainer` | `#76765F` | `#92927A` | `#A543A1` | `#A543A1` |
| `onTertiaryContainer` | `#FFFBFF` | `#131405` | `#FFE5F7` | `#FFE5F7` |
| `background` (override) | `#FAF8EF` | `#1B1916` | `#F3F1FA` | `#0E0C1A` |
| `onBackground` | `#1D1B1A` | `#E7E1DE` | `#1B1B22` | `#E4E1EC` |
| `surface` (override) | `#FAF8EF` | `#1B1916` | `#F3F1FA` | `#0E0C1A` |
| `onSurface` | `#1D1B1A` | `#E7E1DE` | `#1B1B22` | `#E4E1EC` |
| `surfaceVariant` | `#EEE0D6` | `#4E453E` | `#E3E0F2` | `#464553` |
| `onSurfaceVariant` | `#4E453E` | `#D1C4BA` | `#464553` | `#C7C4D6` |
| `surfaceTint` | `#6E5B48` | `#DBC2AB` | `#4E4EC9` | `#C1C1FF` |
| `inverseSurface` | `#33302E` | `#E7E1DE` | `#303038` | `#E4E1EC` |
| `inverseOnSurface` | `#F6EFEC` | `#33302E` | `#F2EFFA` | `#303038` |
| `error` | `#BA1A1A` | `#FFB4AB` | `#BA1A1A` | `#FFB4AB` |
| `onError` | `#FFFFFF` | `#690005` | `#FFFFFF` | `#690005` |
| `errorContainer` | `#FFDAD6` | `#93000A` | `#FFDAD6` | `#93000A` |
| `onErrorContainer` | `#93000A` | `#FFDAD6` | `#93000A` | `#FFDAD6` |
| `outline` | `#7F756D` | `#9A8F86` | `#777585` | `#918F9F` |
| `outlineVariant` | `#D1C4BA` | `#4E453E` | `#C7C4D6` | `#464553` |
| `scrim` | `#000000` | `#000000` | `#000000` | `#000000` |
| `surfaceBright` | `#FFF8F5` | `#3B3937` | `#FCF8FF` | `#393841` |
| `surfaceDim` | `#DFD9D6` | `#151311` | `#DCD8E3` | `#13131A` |
| `surfaceContainer` | `#F3EDE9` | `#211F1D` | `#F0ECF7` | `#1F1F27` |
| `surfaceContainerHigh` | `#EDE7E4` | `#2C2928` | `#EAE6F1` | `#2A2931` |
| `surfaceContainerHighest` | `#E7E1DE` | `#373432` | `#E4E1EC` | `#34343C` |
| `surfaceContainerLow` | `#F9F2EF` | `#1D1B1A` | `#F5F2FD` | `#1B1B22` |
| `surfaceContainerLowest` | `#FFFFFF` | `#100E0C` | `#FFFFFF` | `#0E0D15` |
| `primaryFixed` | `#F8DEC6` | `#F8DEC6` | `#E2DFFF` | `#E2DFFF` |
| `primaryFixedDim` | `#DBC2AB` | `#DBC2AB` | `#C1C1FF` | `#C1C1FF` |
| `onPrimaryFixed` | `#26190A` | `#26190A` | `#0A006B` | `#0A006B` |
| `onPrimaryFixedVariant` | `#554432` | `#554432` | `#3533B0` | `#3533B0` |
| `secondaryFixed` | `#EFE0D4` | `#EFE0D4` | `#E2DFFF` | `#E2DFFF` |
| `secondaryFixedDim` | `#D2C4B9` | `#D2C4B9` | `#C2C2F8` | `#C2C2F8` |
| `onSecondaryFixed` | `#211A13` | `#211A13` | `#161642` | `#161642` |
| `onSecondaryFixedVariant` | `#4E453D` | `#4E453D` | `#424270` | `#424270` |
| `tertiaryFixed` | `#E5E4C9` | `#E5E4C9` | `#FFD7F6` | `#FFD7F6` |
| `tertiaryFixedDim` | `#C9C8AE` | `#C9C8AE` | `#FFAAF5` | `#FFAAF5` |
| `onTertiaryFixed` | `#1C1D0C` | `#1C1D0C` | `#380038` | `#380038` |
| `onTertiaryFixedVariant` | `#474834` | `#474834` | `#7A1979` | `#7A1979` |

The un-overridden `SchemeContent` values were `#FFF8F5` (Classic light), `#151311` (Classic dark), `#FCF8FF` (Neon light) and `#13131A` (Neon dark). They are replaced by the palette background.

### 2.9 Chrome contrast checks (computed 2026-10-28)

| Foreground on background | Classic light | Classic dark | Neon light | Neon dark | Required |
|---|---|---|---|---|---|
| `onSurface` on `surface` | 16.13 | 13.55 | 15.31 | 14.99 | 4.5 |
| `onSurfaceVariant` on `surface` | 8.79 | 10.29 | 8.39 | 11.32 | 4.5 |
| `onSurface` on `surfaceContainerHigh` (score chip value, cards) | 14.01 | 11.15 | 13.93 | 11.15 | 4.5 |
| `onSurfaceVariant` on `surfaceContainerHigh` (chip label) | 7.64 | 8.47 | 7.64 | 8.42 | 4.5 |
| `onSurface` on `surfaceContainerLow` (sheets) | 15.50 | 13.25 | 15.49 | 13.27 | 4.5 |
| `primary` on `surface` (text buttons, section headers) | 6.28 | 10.29 | 6.92 | 11.36 | 4.5 |
| `primary` on `surfaceContainerHigh` ("+N" text) | 5.46 | 8.47 | 6.30 | 8.45 | 4.5 |
| `onPrimary` on `primary` (Keep going, card New game) | 6.68 | 7.73 | 7.74 | 7.71 | 4.5 |
| `onPrimaryContainer` on `primaryContainer` | 4.54 | 5.85 | 4.55 | 4.55 | 4.5 |
| `onSecondaryContainer` on `secondaryContainer` (Undo, hint card) | 4.60 | 4.56 | 4.54 | 4.55 | 4.5 |
| `onTertiaryContainer` on `tertiaryContainer` (NEW BEST chip) | 4.53 | 5.85 | 4.54 | 4.54 | 4.5 |
| `inverseOnSurface` on `inverseSurface` (snackbar) | 11.53 | 10.12 | 11.52 | 10.14 | 4.5 |
| `inversePrimary` on `inverseSurface` (snackbar action) | 7.68 | 4.99 | 7.69 | 5.00 | 4.5 |
| `outline` on `surface` (non-text) | 4.23 | 5.55 | 4.02 | 6.11 | 3.0 |
| `primary` focus ring on `background` (§7.7) | 6.28 | 10.29 | 6.92 | 11.36 | 3.0 |

`PaletteContrastTest` (D-083) also asserts this table, with the "Required" column as the gate.

### 2.10 Element → color token map

| Element | Token |
|---|---|
| Window and every screen background, including behind the system bars | `palette.background` (== `colorScheme.background` == `colorScheme.surface`) |
| Board / empty cell | `palette.board` / `palette.empty` |
| Tile fill / ink | `palette.fill(exp)` / `palette.ink(exp)` (HC-decorated when active) |
| HC tile outline | `colorScheme.outline`, 1.5 dp |
| HC empty-cell stroke | `palette.ink(1)`, 1 dp (D-206) |
| Title "2048" | `skin.titleColor` |
| Score/Best chip container · label · value | `surfaceContainerHigh` · `onSurfaceVariant` · `onSurface` |
| BEST chip after a new best (D-064) | container `tertiaryContainer`; label and value `onTertiaryContainer` (all three crossfade together with `defaultEffects`) |
| "+N", maximum merged exponent < 9 | text in `primary` |
| "+N", maximum merged exponent ≥ 9 | pill with fill `palette.fill(maxMergedExp)` and text `palette.ink(maxMergedExp)` (D-187) |
| Board-size chip | M3 `AssistChip` defaults (`AssistChipDefaults.assistChipColors()` / `assistChipBorder(enabled = true)`); no overrides |
| New game · Undo · More (controls bar) | `OutlinedButton` (border `outline`, content `onSurface`) · `FilledTonalButton` (`secondaryContainer`) · `FilledTonalIconButton` (`secondaryContainer`) |
| Win scrim / game-over scrim | `surface` at alpha 0.60 / 0.78, clipped to the board shape (D-008, D-064) |
| Overlay card · title · secondary text | `surfaceContainerHigh` · `onSurface` · `onSurfaceVariant` |
| Win hero badge | `palette.fill(targetExp)` + `palette.ink(targetExp)` |
| First-launch hint card | `secondaryContainer` / `onSecondaryContainer` |
| Snackbar · action | `inverseSurface` / `inverseOnSurface` · `inversePrimary` (M3 defaults) |
| Sheets · dialogs · menus | M3 defaults: `surfaceContainerLow` · `surfaceContainerHigh` · `surfaceContainer` |
| Top app bar (sub-screens) | container `background`, title `onSurface`, icons `onSurfaceVariant` |
| Settings/how-to section headers | `primary` |
| Focus ring | `primary`, 2 dp (§7.7) |
| TalkBack status line | `onSurfaceVariant` |
| Reduced-motion invalid-move pulse | board-shaped 2 dp stroke in `primary`, alpha 0 → 0.6 → 0 over 300 ms (D-063) |
| Win glow ring | Classic `#F3D774`; Neon `palette.fill(targetExp)` |
| Confetti | `palette.fill(7..11)` + `primary` + `tertiary` (research/04 §10.9) |
| New-best sparkles and star badge | `tertiary` |
| Game-over desaturation | `ColorMatrix().setToSaturation(s)` on tile fills, ink and glows, s = 1.0 → 0.2 (D-064) |

### 2.11 Dynamic color (app chrome only)

- **Setting:** "Dynamic color (app chrome)". Default Off. The row exists only on API 31+ (D-074, D-006).
- **When On:** `colorScheme = (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).copy(background = palette.background, surface = palette.background)`.
  - Dynamic tone-10 and tone-90 `onSurface` keep ≥ 13:1 on all four palette backgrounds.
- **Never dynamic:**
  - `TilePalette` (fills, inks, glows, board, empty, background);
  - `skin.titleColor`;
  - the high-contrast ink;
  - the splash (it is drawn before Compose; §8.3);
  - the launcher and store icons.
- **Follows dynamic:** every chrome role in §2.10 that reads `colorScheme`, including `outline` for the HC tile outline, `primary` for the focus ring, "+N" and confetti, and `tertiary`.
- **Tests and goldens:** dynamic color is Off in every golden (D-147). `GameThemeTest` (Robolectric, SDK 36, which has dynamic color) asserts:
  - with dynamic chrome On, `colorScheme.background == palette.background` and `LocalTilePalette.current.fill(11)` is unchanged;
  - with dynamic chrome On at SDK 24, the static scheme is used.
- **Deferred:** a dynamic *tile ramp* (research/04 §8 B, HCT) is v1.x (D-074).

### 2.12 Color-vision deficiency

- **The number is the primary channel.** Tiles always print their value; color is reinforcement (WCAG 1.4.1, D-084).
- **Lightness structure** (column **L**):
  - Neon's L rises strictly from 2 to 4096 (0.36 → 0.97, research/04 §8 C), so it reads in order even in grayscale.
  - Classic keeps the legacy family structure: cream 2–4, orange 8–64, gold 128–2048, supers 4096+. L is monotonic within the orange and gold families, and the jumps between families are large (64 → 128 ΔE ≥ 31; 2048 → 4096 ΔE ≥ 26).
  - Super tiles break away on purpose (2048 → 4096 ΔE ≥ 20 in every palette), so they never read as "another gold".
- **Gates** (D-083), for adjacent exponents 1→2 … 16→17: ΔE normal ≥ 3.0 and protan, deutan and tritan ≥ 2.0 each. Measured minima:

| Palette | Normal | Protan | Deutan | Tritan | Tightest pair |
|---|---|---|---|---|---|
| Classic light | 3.5 | 2.1 | 2.6 | 2.9 | protan 4096→8192; deutan 32→64 |
| Classic dark | 3.5 | 3.0 | 3.2 | 3.6 | protan 4096→8192 |
| Neon | 6.5 | 2.2 | 3.5 | 5.0 | protan 4096→8192 |

- **High contrast** never changes fills, so these minima hold for every `-hc` variant.
- **The share emoji grid** (D-008) is a text artifact; no on-screen meaning depends on it.

### 2.13 Contrast and ΔE algorithm (`PaletteContrastTest`, JVM)

The test runs in `app/src/test/kotlin/com/clayboicardi/game2048/ui/theme/PaletteContrastTest.kt`, with no Robolectric, using these exact formulas. They reproduce the D-072 figures, and D-072's and D-083's minima are evaluated with them (D-205). research/04 §8 re-quantized the simulated colors to 8-bit hex, which shifts some ΔE values by about 0.1 (for example Classic light deutan 32→64 = 2.7 instead of 2.6, and protan 4096→8192 = 2.0 instead of 2.1, exactly on the 2.0 gate); those quantized numbers are not the oracle.

```text
srgbToLinear(c) = c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055)^2.4        // c in [0,1]
luminance      = 0.2126 R + 0.7152 G + 0.0722 B                                // on linear channels
contrast(a,b)  = (max(La,Lb) + 0.05) / (min(La,Lb) + 0.05)

// Machado, Oliveira & Fernandes 2009, severity 1.0, applied to LINEAR RGB, clamped to [0,1], NOT re-quantized
PROTAN = [[0.152286, 1.052583,-0.204868],[0.114503, 0.786281, 0.099216],[-0.003882,-0.048116, 1.051998]]
DEUTAN = [[0.367322, 0.860646,-0.227968],[0.280085, 0.672501, 0.047413],[-0.011820, 0.042940, 0.968881]]
TRITAN = [[1.255528,-0.076749,-0.178779],[-0.078411, 0.930809, 0.147602],[ 0.004733, 0.691367, 0.303900]]

// OKLab (Ottosson) from linear RGB
l = 0.4122214708 R + 0.5363325363 G + 0.0514459929 B
m = 0.2119034982 R + 0.6806995451 G + 0.1073969566 B
s = 0.0883024619 R + 0.2817188376 G + 0.6299787005 B
l' = cbrt(l); m' = cbrt(m); s' = cbrt(s)
L = 0.2104542553 l' + 0.7936177850 m' - 0.0040720468 s'
a = 1.9779984951 l' - 2.4285922050 m' + 0.4505937099 s'
b = 0.0259040371 l' + 0.7827717662 m' - 0.8086757660 s'
ΔE = 100 * sqrt(ΔL² + Δa² + Δb²)
```

**Assertions.** The test covers every palette instance: Classic light, Classic dark, Neon light, Neon dark, and each with `-hc`.
1. The D-083 ink floor holds for exponents 1–17. The floor is 3.0 for exponents 1–9 and 4.5 for exponents ≥ 10, which always covers compact labels.
2. The ΔE gates of §2.12 hold.
3. Every CR in §2.3–§2.5 matches `round(contrast, 2)` exactly. This catches hex typos.
4. The chrome pairs of §2.9 meet their "Required" column.
5. HC ink equals the black/white rule.

---

## 3. Typography

### 3.1 Bundled numeral font (D-076)

| Item | Value |
|---|---|
| File | `app/src/main/res/font/google_sans_flex_numerals.ttf` |
| Source | `google/fonts` `ofl/googlesansflex/GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf` (OFL-1.1, no Reserved Font Name). The script header records the commit SHA and file sha256. The copy checked here had sha256 `a9ac770798857d5e8ac593ced42b22e40a7c08cec98319f92b0eccc2783a77ba` |
| Recipe | `scripts/fonts/subset-google-sans-flex.sh`, fontTools 4.68 |
| Glyphs | U+0020 space, U+002B `+`, U+0030–U+0039 `0–9`, U+0045 `E`, U+0047 `G`, U+004B `K`, U+004D `M`, U+0050 `P`, U+0054 `T` (31 glyphs including `.notdef` and the tnum/pnum alternates) |
| Axes kept | `wght` 700–900 (default 700), `wdth` 75–100 (default 100), `ROND` 0–100 (default 0). `opsz` is pinned to 18 and `slnt` to 0 |
| Features kept | GSUB `tnum`, `lnum`, `pnum`; GPOS `kern` |
| Name table | all IDs kept (`--name-IDs='*'`). ID 13 is the OFL notice |
| Size | **19,016 bytes**, computed 2026-10-28 with the recipe below. The budget is ≤ 32,768 (D-152) |
| License text | `app/src/main/res/raw/ofl_google_sans_flex.txt` (D-122) |

```bash
fonttools varLib.instancer "GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf" \
  wght=700:900 wdth=75:100 ROND=0:100 opsz=18 slnt=0 -o build/gsf-instanced.ttf
pyftsubset build/gsf-instanced.ttf \
  --unicodes="U+0020,U+002B,U+0030-0039,U+0045,U+0047,U+004B,U+004D,U+0050,U+0054" \
  --layout-features="tnum,lnum,pnum,kern" --name-IDs='*' \
  --output-file=app/src/main/res/font/google_sans_flex_numerals.ttf
```

The instancer prints harmless `OTLOffsetOverflowError` repair notices.

Measured advances, in em, at the instance locations used here:

| Instance | tnum digit | Proportional digits | `K` | `M` | `+` |
|---|---|---|---|---|---|
| wght 800, wdth 100, ROND 0 | 0.668 | 0.444–0.666 | 0.715 | 0.977 | 0.606 |
| wght 800, wdth 87.5, ROND 0 | 0.635 | 0.427–0.635 | 0.679 | 0.932 | 0.586 |
| wght 800, wdth 75, ROND 0 | 0.603 | 0.410–0.604 | 0.643 | 0.888 | 0.566 |
| wght 900, wdth 100, ROND 100 | 0.674 | 0.463–0.671 | 0.737 | 1.004 | 0.618 |
| wght 900, wdth 75, ROND 100 | 0.618 | 0.433–0.619 | 0.672 | 0.924 | 0.581 |
| wght 750, wdth 100, ROND 0 | 0.665 | 0.434–0.663 | 0.704 | 0.963 | 0.600 |

Cap height is 1432/2000 = **0.716 em**.

**Use the font for digit-only game numerals and the title only** (D-076). That means tile labels, compact labels, Score and Best values (header chips and the game-over card), "+N", the win hero badge, mini tiles and the "2048" wordmark. Everything else uses `FontFamily.Default`: every string resource, the board-size chip "4×4", Statistics values, prose numbers and button labels.

**Fallback (D-076):** if the TTF or fontTools cannot be obtained, `GameFonts` returns `FontFamily.Default` with `FontWeight.Bold` and `fontFeatureSettings = "tnum"` for every instance. The OFL raw file is omitted, the license JSON entry is dropped, and the PR body records it.

### 3.2 Font instances (`ui/theme/NumeralFont.kt`, `object GameFonts`)

Variation settings are fixed per `Font` object. `GameFonts` therefore memoizes one `FontFamily` per `(weight, width, rond)`:

```kotlin
object GameFonts {
    private val cache = HashMap<Triple<Int, Float, Float>, FontFamily>()
    fun numerals(weight: Int, width: Float, rond: Float): FontFamily = cache.getOrPut(Triple(weight, width, rond)) {
        FontFamily(
            Font(
                resId = R.font.google_sans_flex_numerals,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width),
                    FontVariation.Setting("ROND", rond),
                ),
            )
        )
    }
}
```

| Use | weight | width | ROND | Features | Other |
|---|---|---|---|---|---|
| Tile label, Classic, 1–3 chars / 4 chars / ≥ 5 chars | 800 | 100 / 87.5 / 75 | 0 | `"lnum"` (proportional digits centre better) | §3.3 |
| Tile label, Neon, same widths | 900 | 100 / 87.5 / 75 | 100 | `"lnum"` | |
| Title "2048" | 850 | 100 | skin ROND | none | letterSpacing −0.01 em |
| Score / Best value, game-over score | 750 | 100 | skin ROND | `"tnum, lnum"` | |
| "+N" | 800 | 100 | skin ROND | `"tnum"` | |

- Every `TextStyle` built on these families sets `fontWeight = FontWeight(weight)` and `fontSynthesis = FontSynthesis.None`.
- **API 24–25** ignore variation settings (ui-text 1.12.1 applies them from API 26; research/04 §9.1). These devices render the subset's default instance, **wght 700, wdth 100, ROND 0**: the instancer moved the `wght` default to its range limit (verified 2026-10-28 in `fvar`). This is acceptable: digits stay bold and fitted, because the auto-fit guard (§3.3) measures the real advance.

### 3.3 Tile numerals (D-077)

Each tile label is measured once per `(exp, tileSizePx, skin.id)` with `rememberTextMeasurer(cacheSize = 64)`. It is drawn with `drawText` inside the tile's `drawBehind` (D-025).

Steps 1–3 are the pure `object TileLabels` in `game/board/TileLabels.kt`: `TileLabels.label(exp: Int, tileSizeDp: Float): TileLabel(text, fontSizeDp, wdth, compact)` and `TileLabels.fullDecimal(exp)`. Steps 4–7 happen at measure and draw time.

1. **Label text.** `full = TileLabels.fullDecimal(exp)`: `(1L shl exp).toString()` for exp ≤ 62, else `BigInteger.ONE.shiftLeft(exp).toString()`. Digits are always Latin (D-103).
2. **Nominal size** in dp, as a fraction of the tile side `t`:

| Characters | Size | `wdth` |
|---|---|---|
| 1–2 | 0.50·t | 100 |
| 3 | 0.40·t | 100 |
| 4 | 0.32·t | 87.5 |
| 5 | 0.27·t | 75 |
| 6 | 0.23·t | 75 |
| ≥ 7 | 0.84·t / (chars · 0.603) | 75 |

3. **Compact switch (§3.4).** If the nominal size of `full` is **< 12 dp** and `exp ≥ 10`, use the compact label and re-derive the size from the compact label's character count.
4. **Auto-fit guard.** Measure with the chosen size. If `layout.size.width > 0.84 · tilePx`, multiply the size by `0.84 · tilePx / width` and re-measure once. This covers wide glyphs such as `M` (0.977 em) and Neon's wider wght 900 digits (0.618 em at wdth 75 vs the 0.603 the table assumes).
5. **Style:**
   - `letterSpacing = (-0.02).em`;
   - `lineHeight = 1.0.em`, `lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)`;
   - `platformStyle = PlatformTextStyle(includeFontPadding = false)`;
   - color `palette.ink(exp)`.
6. **fontScale is ignored.** Measure with `density = Density(LocalDensity.current.density, fontScale = 1f)` passed to `TextMeasurer.measure(...)` and `fontSize = fontSizeDp.sp` (the label's size after any step-4 scaling). Tile numerals then scale with Display size (density) only (D-084).
7. **Optical centering:**
   - `x = tileCenterX − layout.size.width / 2`;
   - `y = tileCenterY − layout.firstBaseline + 0.358 · fontSizePx`. 0.358 em is half the cap height, so the visual centre of the digits sits on the tile centre.

Worked sizes for 4×4 Classic on the 328 dp board (t = 69.5 dp):
- "2" is 34.8 dp;
- "128" is 27.8 dp;
- "2048" is 22.2 dp at wdth 87.5;
- "16384" is 18.8 dp at wdth 75;
- "131072" is 16.0 dp.

Nothing is compact.

### 3.4 Compact labels (D-077)

- **Format:** `2^(exp % 10)` + suffix for `exp / 10`, where 1 → `K`, 2 → `M`, 3 → `G`, 4 → `T`, 5 → `P`, 6 → `E`.
  - Examples: 2^10 → "1K", 2^14 → "16K", 2^17 → "128K", 2^20 → "1M", 2^65 → "32E".
  - Exponents ≥ 70 cannot occur for N ≤ 8 (maximum 2^65). The renderer applies `require(exp < 70)`.
- **Never for values < 1024.** Exponents 1–9 always show the full number, even below 12 dp.
- **No second fallback.** A compact label below 12 dp (for example "128K" on an 8×8 board at the 220 dp floor) is still drawn, and the auto-fit guard applies. Its contrast floor is 4.5 (D-083), which every exponent ≥ 10 meets (§2.3–§2.5).
- **Accessibility:** the cell node always speaks the full decimal value (D-080), never "16K".

First exponent that becomes compact, per board size and board side (Classic r = 0.14 / Neon r = 0.18; density 1.0):

| N | Board 280 dp | Board 328 dp | Board 380 dp | Board 481 dp | Board 720 dp |
|---|---|---|---|---|---|
| 3, 4 | none ≤ 2^17 | none | none | none | none |
| 5 | 2^17 "128K" | none | none | none | none |
| 6 | 2^14 "16K" | 2^17 "128K" | none | none | none |
| 7 | 2^10 "1K" | 2^14 "16K" | 2^17 "128K" | none | none |
| 8 | 2^10 "1K" | 2^10 "1K" | 2^14 "16K" | none (Classic) / 2^17 "128K" (Neon) | none |

`game/board/TileLabelsTest` (JVM; the pure `TileLabels.label(exp: Int, tileSizeDp: Float): TileLabel(text, fontSizeDp, wdth, compact)` and `TileLabels.fullDecimal(exp)`) asserts every cell of this table, the D-077 examples and the `require`.

### 3.5 Chrome type scale

Chrome text uses `MaterialTheme.typography`, the M3 baseline `Typography()` with `FontFamily.Default` (D-076). It is provided through `MaterialExpressiveTheme` (D-026). The emphasized styles come from material3 1.5.0-beta02 and are read only inside `ui/theme` and `ui/components` wrappers (D-026).

| Element | Style | Size / line (sp) | Weight | Color |
|---|---|---|---|---|
| Title "2048" | GSF title instance (§3.2), `maxLines = 1`, `autoSize = TextAutoSize.StepBased(32.sp, max, 1.sp)` with max 56 sp, or 64 sp when the window width ≥ 840 dp | 56/56 or 64/64 | 850 | `skin.titleColor` |
| Score/Best label | `labelMediumEmphasized`, text `uppercase(locale)` | 12/16, tracking +0.5 | Bold | `onSurfaceVariant` |
| Score/Best value | GSF score instance at one fitted size per value (`ScoreValue`, §7.2), `maxLines = 1` | 14–28 | 750 | `onSurface` |
| "+N" | GSF "+N" instance | 16/16 | 800 | §2.10 |
| Board-size chip label | `labelLarge` | 14/20 | Medium | `onSurface` |
| Game buttons (56 dp) | `ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)` | 16/24 | Medium | role default |
| Overlay card title (Regular) | `displaySmallEmphasized` | 36/44 | Medium | `onSurface` |
| Overlay card title (Compact, §6.3a, §6.4a) | `headlineSmallEmphasized` | 24/32 | Medium | `onSurface` |
| Game-over score value (Regular) | GSF score instance, `autoSize = StepBased(20.sp, 40.sp, 1.sp)` | ≤ 40 | 750 | `onSurface` |
| Game-over box values (Compact) | GSF score instance, `autoSize = StepBased(14.sp, 24.sp, 1.sp)` | ≤ 24 | 750 | `onSurface` |
| Overlay card secondary lines | `bodyMedium` | 14/20 | Regular | `onSurfaceVariant` |
| Hint card text | `bodyMedium` | 14/20 | Regular | `onSecondaryContainer` |
| Top app bar title | `titleLarge` | 22/28 | Regular | `onSurface` |
| Sheet title | `titleLarge` | 22/28 | Regular | `onSurface` |
| Dialog title / body | `headlineSmall` / `bodyMedium` (AlertDialog defaults) | 24/32, 14/20 | | |
| Section header (Settings, How to play, About) | `titleSmallEmphasized` | 14/20 | Bold | `primary` |
| List item headline / supporting | `bodyLarge` / `bodyMedium` (ListItem defaults) | 16/24, 14/20 | | `onSurface` / `onSurfaceVariant` |
| Statistics value | `titleMedium`, `fontFeatureSettings = "tnum"` | 16/24 | Medium | `onSurface` |
| Statistics hero "Best score" value | `headlineLarge`, `"tnum"` | 32/40 | Regular | `onSurface` |
| Body copy (How to play, About, Privacy) | `bodyLarge` | 16/24 | Regular | `onSurface` |
| License text | `bodySmall` with `fontFamily = FontFamily.Monospace` | 12/16 | Regular | `onSurfaceVariant` |
| TalkBack status line | `bodyMedium` | 14/20 | Regular | `onSurfaceVariant` |

### 3.6 Font scaling

- **Chrome text** is in sp and follows Android 14+ non-linear scaling up to `fontScale` 2.0. It never truncates; labels wrap to at most 2 lines (D-084). Implementation rules:
  - Buttons use `Modifier.heightIn(min = …)`, never a fixed height, and their labels use `maxLines = 2`. The Game controls bar grows when they wrap (§5.3 uses measured chrome heights).
  - The title uses `TextAutoSize`, and Score/Best values use `ScoreValue`'s single fitted size (§7.2), so both shrink to fit their slot instead of clipping.
  - List rows, dialogs and sheets scroll vertically. They are outside the gesture surface or modal, so scrolling never competes with swipes.
- **Tile numerals, compact labels and mini tiles ignore `fontScale`** (§3.3, D-077, D-084). They follow density, and the board itself grows with the window.
- Goldens cover `fontScale` 2.0 for Game and Settings (§10).

### 3.7 Digits, locales and casing

- Game numerals use `Long.toString()` (Latin digits, no grouping). Prose numbers use `formatCount(n, locale)` (Latin digits with the locale's grouping) passed as `%1$s` (D-103).
- The formatters live in `ui/components/Formatting.kt` (M3): `formatCount(n, locale)`, `formatPercent`, `formatElapsed` and `toLatinDigits` (L10N_STORE_LEGAL.md §1.10).
- Percentages use `formatPercent`, which wraps `NumberFormat.getPercentInstance(latnLocale)`. `latnLocale` is the locale with `-u-nu-latn`, as in `formatCount`.
- Durations use `formatElapsed(ms)`: `"%d:%02d"` (m:ss) under one hour, else `"%d:%02d:%02d"` (h:mm:ss), with `Locale.ROOT`. The result is Latin digits with a colon separator in every locale.
- Upper-casing (the chip labels) uses `text.uppercase(LocalConfiguration.current.locales[0])`. It is never applied to Google Sans Flex text.
- The board-size label is built in code as `"$n×$n"` (U+00D7), in the system font. It is not a string resource.

---

## 4. Shape, spacing, elevation, effects

### 4.1 Spacing and dimension tokens (`ui/theme/GameDimens.kt`)

| Token | Value | Use |
|---|---|---|
| `Space2` / `Space4` / `Space8` / `Space12` / `Space16` / `Space24` / `Space32` / `Space48` | 2 / 4 / 8 / 12 / 16 / 24 / 32 / 48 dp | 4-dp grid (2 dp only for focus-ring gaps) |
| `MarginCompact` / `MarginMedium` / `MarginExpanded` | 16 / 24 / 32 dp | D-075 margins (§5.2) |
| `HeaderRow1` / `HeaderRow2` / `HeaderHeight` | 64 / 48 / 112 dp | stacked header (D-091) |
| `HeaderCompact` | 64 dp | COMPACT header of STACKED and SCROLL (§5.3) |
| `ControlsBarHeight` | 80 dp (minimum) | stacked controls (D-091) |
| `StackGap` | 24 dp | header↔board and board↔controls (240 = 112 + 24 + 24 + 80); `Space8` replaces it with the COMPACT header (§5.3) |
| `ScoreChipMinWidth` / `ScoreChipMinHeight` | 88 / 64 dp | |
| `ControlHeight` | `ButtonDefaults.MediumContainerHeight` (56 dp) | Undo, New game, More, card buttons |
| `PadButton` / `PadGap` | 64 / 8 dp | TalkBack direction pad (D-082) |
| `StatusLineMinHeight` | 40 dp | 2 lines of `bodyMedium` |
| `BoardMin` / `BoardMax` / `BoardFloor` | 280 / 720 / 220 dp | D-075 |
| `BoardGapMin` / `BoardGapMax` | 2 / 20 dp | D-075 gap clamp (§4.2) |
| `WidthExpandedMin` / `HeightMediumMin` | 840 / 480 dp | window-class breakpoints of `layoutModeFor` (§5.1) |
| `RailMin` / `RailMax` / `RailGap` | 160 / 240 / 12 dp | 3-column (D-075) |
| `PanelMin` / `PanelMax` / `PanelGap` | 280 / 400 / 32 dp | side panel (D-075) |
| `ContentMaxWidth` | 640 dp | Settings, Stats, How to play, About and Licenses columns |
| `OverlayCardMinWidth` / `OverlayCardMaxWidth` | 264 / 400 dp, never wider than board − 16 dp | §6.3, §6.4 (D-200) |
| `OverlayCardInset` | 16 dp | card height budget = board − 16 dp (D-200) |
| `HeroBadge` | 96 dp (72 dp when the window height < 480 dp, 120 dp when the window width ≥ 840 dp); 64 dp in the Compact win card | §6.3, §6.3a |
| `MiniTile` | 32 dp (size sheet, statistics, Compact game-over card), 40 dp (Regular game-over card), 56 dp (statistics hero) | §7.8 |
| `MiniGlyphGapRatio` | 0.10 (of `sizePx / n`) | `MiniBoardGlyph` gap (§7.8) |
| `FocusRingWidth` / `FocusRingGap` | 2 / 2 dp | §7.7 |
| `HcOutlineWidth` | 1.5 dp | §2.7 |

### 4.2 Board geometry (D-075), implementation

```kotlin
// game/board/BoardGeometry.kt — pure; covered by game/board/BoardGeometryTest (JVM)
@Immutable
data class BoardGeometry(
    val n: Int, val boardPx: Int, val gapPx: Int, val tilePx: Float,
    val tileRadiusPx: Float, val boardRadiusPx: Float,
) {
    /** x = column, y = row, in cells; fractional during a slide. Rounded to whole pixels (§4.7). */
    fun cellTopLeft(x: Float, y: Float): IntOffset = IntOffset(
        (gapPx + x * (tilePx + gapPx)).roundToInt(),
        (gapPx + y * (tilePx + gapPx)).roundToInt(),
    )
}

fun boardGeometry(n: Int, board: Dp, skin: TileSkin, density: Float): BoardGeometry {
    val boardPx = floor(board.value * density).toInt()
    val tileRawPx = boardPx / (n + skin.gapRatio * (n + 1))
    val gapPx = (skin.gapRatio * tileRawPx).roundToInt()
        .coerceIn((GameDimens.BoardGapMin.value * density).roundToInt(), (GameDimens.BoardGapMax.value * density).roundToInt())
    val tilePx = (boardPx - (n + 1) * gapPx) / n.toFloat()
    val radiusPx = (skin.radiusFraction * tilePx)
        .coerceIn(skin.radiusMin.value * density, skin.radiusMax.value * density)
    return BoardGeometry(n, boardPx, gapPx, tilePx, radiusPx, radiusPx + gapPx)
}
```

- Classic: `gapRatio 0.14`, `radiusFraction 0.07`, radius 2–10 dp. Neon: `0.18`, `0.22`, 4–34 dp (D-075).
- `cellTopLeft(x, y)` takes cell coordinates as `Float` (x = column, y = row), so a sliding tile's fractional position maps straight to pixels. An `Int` overload `cellTopLeft(x: Int, y: Int)` is optional; if present, it delegates to the `Float` form.
- `resolveGameLayout(window: LayoutWindow, chrome: ChromeMeasure): GameLayoutSpec` (M4), in the same file, implements the per-arrangement board size and the arrangement choice of §5.3.
- Board radius = tile radius + gap, so the corners are concentric.
- Required check (D-075): 4×4 Classic at 328 dp and density 1.0 gives gap 10 dp and tile 69.5 dp.

### 4.3 Geometry per board size (density 1.0; gap / tile / tile radius in dp)

The gap as a fraction of the board side is `r / (N + r·(N+1))`.

**Classic Refined (r = 0.14)**

| N | Gap / board | Board 280 | Board 328 | Board 380 | Board 720 |
|---|---|---|---|---|---|
| 3×3 | 3.93 % | 11 / 78.7 / 5.5 | 13 / 92.0 / 6.4 | 15 / 106.7 / 7.5 | 20 / 213.3 / 10.0 |
| 4×4 | 2.98 % | 8 / 60.0 / 4.2 | 10 / 69.5 / 4.9 | 11 / 81.2 / 5.7 | 20 / 155.0 / 10.0 |
| 5×5 | 2.40 % | 7 / 47.6 / 3.3 | 8 / 56.0 / 3.9 | 9 / 65.2 / 4.6 | 17 / 123.6 / 8.7 |
| 6×6 | 2.01 % | 6 / 39.7 / 2.8 | 7 / 46.5 / 3.3 | 8 / 54.0 / 3.8 | 14 / 103.7 / 7.3 |
| 7×7 | 1.72 % | 5 / 34.3 / 2.4 | 6 / 40.0 / 2.8 | 7 / 46.3 / 3.2 | 12 / 89.1 / 6.2 |
| 8×8 | 1.51 % | 4 / 30.5 / 2.1 | 5 / 35.4 / 2.5 | 6 / 40.8 / 2.9 | 11 / 77.6 / 5.4 |

**Neon Arcade (r = 0.18)**

| N | Gap / board | Board 280 | Board 328 | Board 380 | Board 720 |
|---|---|---|---|---|---|
| 3×3 | 4.84 % | 14 / 74.7 / 16.4 | 16 / 88.0 / 19.4 | 18 / 102.7 / 22.6 | 20 / 213.3 / 34.0 |
| 4×4 | 3.67 % | 10 / 57.5 / 12.7 | 12 / 67.0 / 14.7 | 14 / 77.5 / 17.1 | 20 / 155.0 / 34.0 |
| 5×5 | 2.96 % | 8 / 46.4 / 10.2 | 10 / 53.6 / 11.8 | 11 / 62.8 / 13.8 | 20 / 120.0 / 26.4 |
| 6×6 | 2.48 % | 7 / 38.5 / 8.5 | 8 / 45.3 / 10.0 | 9 / 52.8 / 11.6 | 18 / 99.0 / 21.8 |
| 7×7 | 2.13 % | 6 / 33.1 / 7.3 | 7 / 38.9 / 8.5 | 8 / 45.1 / 9.9 | 15 / 85.7 / 18.9 |
| 8×8 | 1.87 % | 5 / 29.4 / 6.5 | 6 / 34.2 / 7.5 | 7 / 39.6 / 8.7 | 13 / 75.4 / 16.6 |

The legacy gap/tile ratio was 0.141 (research/02-legacy-game.md §3.2), so Classic r = 0.14 keeps the original proportions at every N. `BoardGeometryTest` asserts the 4×4 rows of both tables and the D-075 check.

### 4.4 Corner radii

| Element | Shape |
|---|---|
| Tile | `RoundedCornerShape(tileRadius)` from §4.2. Tiles are always rounded squares, never `MaterialShapes` (D-012) |
| Board | `RoundedCornerShape(tileRadius + gap)` |
| Win/game-over scrim | the board shape (D-008, fixes B19) |
| Score chips, hint card, statistics hero card | `MaterialTheme.shapes.large` (16 dp) |
| Overlay cards, dialogs, sheets (top corners) | `MaterialTheme.shapes.extraLarge` (28 dp) |
| Buttons and icon buttons | `ButtonDefaults.shapes()` / `IconButtonDefaults.shapes()`: round at rest, squarer when pressed (M3 Expressive, public in 1.5.0-beta02) |
| Board-size chip | M3 chip default (`shapes.small`, 8 dp) |
| "+N" pill | `CircleShape` |
| Win hero badge | graphics-shapes morph Circle → 12-point cookie (D-064) |
| New-best star | `RoundedPolygon.star(10, innerRadius = 0.82f, rounding = CornerRounding(0.15f))` (D-064) |
| Mini tile | `RoundedCornerShape(clamp(0.07·t, 2dp, 10dp))` Classic, `clamp(0.22·t, 4dp, 34dp)` Neon |

### 4.5 Elevation and shadows

- **Chrome** is tonal, with no shadows (P1). Score chips, the size chip, buttons and the hint card have `shadowElevation = 0`.
- **The overlay cards** (win, game over) are the only chrome with a shadow: `ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = surfaceContainerHigh), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp))`.
- **Sheets and dialogs** use M3 defaults.
- **Board:**
  - Classic: flat, a filled rounded rect.
  - Neon: an inset well, `Modifier.innerShadow(boardShape) { radius = 12.dp.toPx(); color = Color.Black; alpha = 0.5f }` (research/04 §8 C; `innerShadow` is public in ui 1.12.1, verified 2026-10-28).
- **Tiles** are flat except for the glow treatment below. Shadows use the block-lambda form `Modifier.dropShadow(shape) { … }` / `Modifier.innerShadow(shape) { … }`, so animated alpha needs no recomposition (research/04 §4.2).
- Shadow-block radius, spread and offset are in px; convert with `toPx()` (the scope is a `Density`). The `t`-relative values of §4.6 are computed from the tile side in px.

### 4.6 Tile glow (D-078, modernized from the legacy formula)

- **Legacy formula** (research/02 §3.1): `g = (exp − 4)/7`; outer `0 0 30px 10px rgba(#F3D774, g/1.8)`; inset `1px rgba(#fff, g/3)`.
- **Classic Refined** keeps the color and the curve at **0.8 × legacy alpha**. It scales radius and spread with the tile instead of fixed pixels, and replaces the hard 1 px inset ring with a soft inner highlight.

| Exp (value) | Classic outer alpha, light | Classic outer alpha, dark (× 1.15) | Classic inner white alpha, light | Inner, dark (× 1.15) | Neon outer alpha (both modes) |
|---|---|---|---|---|---|
| 7 (128) | 0.19 | 0.2185 | 0.050 | 0.0575 | 0.35 |
| 8 (256) | 0.25 | 0.2875 | 0.065 | 0.0748 | 0.425 |
| 9 (512) | 0.32 | 0.368 | 0.080 | 0.092 | 0.50 |
| 10 (1024) | 0.38 | 0.437 | 0.095 | 0.1093 | 0.575 |
| 11 (2048) | 0.44 | 0.506 | 0.110 | 0.1265 | 0.65 |
| ≥ 12 (super) | none (flat) | none | none | none | 0.65 |
| 1–6 | none | none | none | none | none |

Code computes the dark Classic values as `lightAlpha * 1.15f`, unrounded.

| Parameter | Classic | Neon |
|---|---|---|
| `dropShadow` color | `#F3D774` | `palette.fill(exp)` |
| `dropShadow` radius / spread | `0.20·t` / `0.03·t` | `0.25·t` / `0` |
| `innerShadow` | white, `alpha` per table, radius **1 dp** (the "inner 1 dp highlight" of research/04 §8 A; it replaces the legacy `inset 0 0 0 1px` ring), offset 0, 128–2048 only | white, alpha **0.18**, radius `0.08·t`, offset `(0, 0.04·t)`, applied to **every** Neon tile, including 2–64 (glassy highlight) |

- The glow lives in the tile's own node, so translation re-places it rather than redrawing (research/04 §4.2).
- **No glow ever animates continuously.** The only animations are one-shots on merge (D-064, research/04 §10.4):
  - a white sheen at alpha 0.35 → 0 over `fastEffects` on merges ≥ 128, drawn inside the tile's own `drawBehind` (D-187);
  - a glow pulse of base + 0.25 → base over `defaultEffects` on merges ≥ 512.
- **Reduced motion:** the pulse and sheen become the 120 ms color crossfade (D-063).
- **Game over:** glows are desaturated with the tile (§2.10).

### 4.7 Draw order inside the board and pixel snapping

1. Board background, plus the Neon inset well.
2. Empty cells: a **single** `drawBehind` over N² rounded rects (D-025), plus the high-contrast empty-cell strokes (§2.7, D-206).
3. Ghost tiles (D-025) and, after an undo or new game, the previous board's outgoing tiles in `OutgoingTilesLayer` (D-182).
4. Static and moved tiles.
5. Merged tiles, revealed above the ghosts at +80 ms (D-061).
6. Spawned tiles.
7. `BoardEffectsCanvas` (`game/board/EffectsCanvas.kt`): the win glow ring only. The merge sheen is tile-local, drawn in the tile's own `drawBehind` (D-187).
8. Scrim, clipped to the board shape.
9. Outside the board, as an overlay on the whole game content: `ContentEffectsCanvas` (same file) with confetti and the new-best sparkles, then the overlay card on top. Both canvases share one particle engine (`game/motion/ConfettiSystem`); `ContentEffectsCanvas` draws above the scrim and below the card (D-187).

- Tile positions go through `Modifier.offset { geometry.cellTopLeft(anim.offset.value.x, anim.offset.value.y) }`, with the animated position in cells (x = column, y = row), so they are integer pixels.
- Tile size is `Modifier.size(tilePx.toDp())`. Fractional sizes are fine, because edges are anti-aliased once.
- The board never clips its children. Glows may spill past the board edge onto the background, as in the legacy CSS.

---

## 5. Layout

### 5.1 Mode selection (D-091, D-092)

```kotlin
// ui/components/WindowMode.kt
enum class LayoutMode { STACKED, THREE_COLUMN, SIDE_PANEL, SCROLL }

/** The window-class preference (D-091). Never returns SCROLL; §5.3 resolves the arrangement actually drawn. */
fun layoutModeFor(widthDp: Int, heightDp: Int): LayoutMode = when {
    heightDp < GameDimens.HeightMediumMin.value -> LayoutMode.THREE_COLUMN   // isHeightAtLeastBreakpoint(480) == false
    widthDp >= GameDimens.WidthExpandedMin.value -> LayoutMode.SIDE_PANEL    // isWidthAtLeastBreakpoint(840)
    else -> LayoutMode.STACKED
}

@Composable
fun rememberLayoutMode(): LayoutMode   // the preference, from currentWindowAdaptiveInfoV2().windowSizeClass
```

- The window size comes from `currentWindowAdaptiveInfoV2().windowSizeClass` (D-026, D-092), read by `rememberLayoutMode()`. That is only the **preferred** mode: `GameLayout` draws the arrangement that `resolveGameLayout` (§5.3) picks from the board size each candidate would give, so a window whose rails or panel would leave too small a board gets STACKED or SCROLL instead (D-188). Everything that depends on the arrangement (hint and snackbar placement, traversal order) reads `GameLayoutSpec.mode`, never the preference.
- **"Expanded"** in this file always means window width ≥ 840 dp. It controls the 64 sp title, the 120 dp hero badge and the 144-particle confetti (D-064).
- Sub-screens (Settings, Stats, How to play, About, Licenses) use one column of at most `ContentMaxWidth` (640 dp), centered, with the horizontal margin of §5.2. They have no mode switch.

### 5.2 Insets, margins, edge-to-edge, cutouts, gestures

- **Edge-to-edge, always** (D-093). `enableEdgeToEdge()` runs before `setContent`.
  - The window background is `palette.background`, drawn behind the status and navigation bars.
  - System-bar icon appearance follows the **effective** theme, not the system theme: `LaunchedEffect(effectiveDark) { activity.enableEdgeToEdge(SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { effectiveDark }, SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { effectiveDark }) }`.
  - Neon light uses a light background, so its bar icons are dark.
- **Content padding** = `WindowInsets.safeDrawing`, which covers the status bar, navigation bar, display cutout and IME, plus the margins below.
- **Margins** (D-075 "compact 16 / medium 24 / expanded 32", applied per axis by D-188; by width class alone, the 915×412 board would shrink from 356 to 324 dp):
  - horizontal margin by window **width** class: < 600 dp → 16; 600–839 → 24; ≥ 840 → 32;
  - vertical margin by window **height** class: < 480 dp → 16; 480–899 → 24; ≥ 900 → 32;
  - the horizontal margin is additionally `max(margin, WindowInsets.systemGestures left/right)` (D-051).
- **Cutouts:** `safeDrawing` includes `displayCutout`, so in landscape the 3-column rails never sit under a cutout.
- **Immersive mode** (D-093): `WindowInsetsControllerCompat.hide(systemBars())` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
  - `safeDrawing` then shrinks to the cutout only, so the board may grow.
  - While the arrangement stays the same, that change animates with `animateBounds` under Full motion only. If it switches the arrangement (§5.3), the board is composed anew and appears at its new size with no bounds animation (D-095, §5.8).
- **The gesture surface** is the whole Game content area inside `safeDrawing` (D-051), except in SCROLL, where it is the board alone (§5.3, D-188). `systemGestureExclusion` is never used.

### 5.3 Board size per arrangement, and the arrangement chosen

`availW` and `availH` = window − `safeDrawing` − 2 × the margins of §5.2. `W` is the window width.

| Arrangement | Board | Chrome term |
|---|---|---|
| STACKED | `min(max(availW, 220dp), availH − stacked(h, width), 720dp)`, with header `h` = FULL while that leaves ≥ 280 dp and `availW ≥ 280dp`, otherwise COMPACT | `stacked(h, w)` below (D-188). Nominal at fontScale 1.0: FULL **240 dp** (112 + 24 + 24 + 80, D-075), **336 dp** with the TalkBack pad; COMPACT **160 dp**, **264 dp** with the pad |
| THREE_COLUMN | `min(availH, availW − 2·rail − 24dp, 720dp)`, `rail = clamp(0.22·W, 160dp, 240dp)`, and `rail = max(rail, 208dp)` while the TalkBack pad is shown (the pad is 208 dp wide) | rails are full-height columns; their content must fit `availH` |
| SIDE_PANEL | `min(availH, availW − panel − 32dp, 720dp)`, `panel = clamp(0.32·W, 280dp, 400dp)` | the panel is as tall as the board and top-aligned with it (D-188); its content must fit that height |
| SCROLL | `clamp(min(availW, availH − top(COMPACT, w0) − statusBlock), 220dp, 720dp)` | a scrolling column: the chrome below the board may start below the fold |

D-075's narrow-window rule (`availW < 280dp`: board `max(220dp, availW)`, title hidden) is the STACKED row with the COMPACT header; its `max(availW, 220dp)` term lets a 220 dp board spill into the side margins when `availW < 220dp`.

**Measured chrome** (D-188). Terms in dp at content width `w`; `h` is FULL or COMPACT. With the FULL header, header and controls are as wide as the board (§5.5 A). With the COMPACT header (STACKED and SCROLL) they span the content width `w0 = min(max(availW, 220dp), 720dp)`, because the one header row needs about 290 dp at fontScale 1.0 (size chip, two 88 dp chips, two 8 dp gaps) and a COMPACT board can be narrower:

| Term | Definition | Nominal at fontScale 1.0 |
|---|---|---|
| `header(FULL, w)` | title row (the taller of 64 dp and the chips) + size-chip row | 112 |
| `header(COMPACT, w)` | one row [size chip][Score][Best], no title | 64 |
| `gap(h)` | header↔board and board↔controls: `StackGap` for FULL, `Space8` for COMPACT | 24; 8 |
| `top(h, w)` | `header(h, w)` + (hint shown ? 8 + hint card + 8 : `gap(h)`) | FULL 136; COMPACT 72 (no hint) |
| `bottom(h, w)` | without the pad: `gap(h)` + controls bar. With it: (FULL ? 16 : 8) + status line + 8 + pad row (§6.6) | FULL 104 / 200; COMPACT 88 / 192 |
| `stacked(h, w)` | `top(h, w) + bottom(h, w)` | FULL 240 / 336; COMPACT 160 / 264 |
| `statusBlock` | SCROLL only: 8 + status line at `w0` while the pad is shown, else 0 | 48 / 0 |
| rails | the taller of the stats rail without its title and the actions rail, at the rail width (§5.4) | 192; 312 with the pad |
| panel | the panel's content at the panel width (§5.4) | 256; 448 with the pad |

The status line enters every term at its reserved two-line height, not with its current text, so an announcement never changes the arrangement; §6.6's absorption of a longer status line then shrinks the board, never below `BoardFloor`.

`GameLayout` (`game/GameLayout.kt`) is a `SubcomposeLayout`, and the resolution runs in its measure block:
1. It subcomposes a **measurement probe** of the chrome: the header in both styles, the hint, the controls bar, the status line, the pad row, both rails and the panel. The probe is wrapped in `Modifier.clearAndSetSemantics {}` and composed under `LocalChromeProbe provides true`, which turns off every effect in chrome composables (`GameHeader`'s header moments and its `onHeaderPresented` report, ARCHITECTURE.md §4.11). It shows placeholder values (scores 0; the status line is its `Text` with `minLines = 2` and no text), so it does not recompose on moves. It is never placed, so it draws nothing and takes no input, focus or semantics.
2. A `ChromeMeasure` answers each term with the probe's `maxIntrinsicHeight` at the asked width.
3. `resolveGameLayout` picks the arrangement. `GameLayout` subcomposes that arrangement, measures each child once and places it. Each arrangement is its own subcomposition slot, keyed by `GameLayoutSpec.mode`, and holds the board, header, controls and the rest of the visible chrome.

**Arrangement switches** (D-095). A live change that keeps `GameLayoutSpec.mode` (the hint leaving, the pad, Immersive mode, insets, or the STACKED header going FULL ↔ COMPACT) reuses the slot, so board, panel and rail bounds animate as §5.8 says. A change of `mode` disposes the old slot and composes the new one; `movableContentOf` is not used. The new `BoardView` and `GameHeader` are new compositions under the one-shot presentation contract (`ARCHITECTURE.md` §4.11): on their first frame they draw the settled static frame of the committed state at the new size, with no bounds animation at any motion level, and a move, "+N" or new-best moment in flight snaps to its end as on recreation (MOTION_SPEC.md §3.16).

At `fontScale` 1.0 `stacked(FULL, w)` equals D-075's constant 240 dp. At 2.0, with the TalkBack pad, and in short windows the measurement prevents overflow (D-188).

**Resolution** (D-091, D-188):

```kotlin
// game/board/BoardGeometry.kt (M4): pure, JVM-tested by BoardGeometryTest. All values in dp.
enum class HeaderStyle { FULL, COMPACT }

data class LayoutWindow(
    val width: Float, val height: Float,
    val safeLeft: Float, val safeTop: Float, val safeRight: Float, val safeBottom: Float,  // WindowInsets.safeDrawing
    val gestureLeft: Float, val gestureRight: Float,                                         // WindowInsets.systemGestures (§5.2)
    val talkBack: Boolean,                                                                    // the direction pad is shown (§6.6)
    val hint: Boolean,                                                                        // the first-launch hint is shown (§6.2)
)

interface ChromeMeasure {   // the measured-chrome terms above; each is non-increasing in its width argument
    fun header(style: HeaderStyle, width: Float): Float
    fun hint(width: Float): Float
    fun controls(width: Float): Float
    fun status(width: Float): Float      // the reserved two-line height
    fun padRow(width: Float): Float
    fun rails(rail: Float): Float
    fun panel(panel: Float): Float
}

data class GameLayoutSpec(
    val mode: LayoutMode,        // the arrangement drawn: STACKED, THREE_COLUMN, SIDE_PANEL or SCROLL
    val header: HeaderStyle,     // STACKED: FULL or COMPACT; SCROLL: COMPACT; FULL otherwise (unused)
    val board: Float,            // always within [BoardFloor, BoardMax]
    val rail: Float = 0f,        // THREE_COLUMN only
    val panel: Float = 0f,       // SIDE_PANEL only
)

fun resolveGameLayout(window: LayoutWindow, chrome: ChromeMeasure): GameLayoutSpec
```

```text
w0 = min(max(availW, 220), 720)                            // STACKED and SCROLL content width

STACKED       h = FULL if availW ≥ 280 and availH − stacked(FULL, w0) ≥ 280, else COMPACT
              s = min(w0, availH − stacked(h, w0))
              if h == FULL: s = s − max(0, stacked(FULL, s) − stacked(FULL, w0))   // FULL chrome is as wide as the board: re-measured once
                                                                                    // COMPACT chrome spans w0, so no re-measure
              fitsS = s ≥ 220
THREE_COLUMN  rail = clamp(0.22·W, 160, 240); if talkBack: rail = max(rail, 208)
              t = min(availH, availW − 2·rail − 24, 720)
              fitsT = t ≥ 220 and rails(rail) ≤ availH
SIDE_PANEL    panel = clamp(0.32·W, 280, 400)
              p = min(availH, availW − panel − 32, 720)
              fitsP = p ≥ 220 and panel(panel) ≤ p

pref = layoutModeFor(W, H); the first matching rule wins:
1. pref == THREE_COLUMN and fitsT and (not fitsS or t ≥ s)   → THREE_COLUMN, board t
2. pref == SIDE_PANEL and fitsP and (not fitsS or p ≥ s)     → SIDE_PANEL, board p
3. fitsS                                                      → STACKED, header h, board s
4. fitsT                                                      → THREE_COLUMN, board t (whatever the height class)
5. otherwise                                                  → SCROLL, header COMPACT,
                                                                board clamp(min(availW, availH − top(COMPACT, w0) − statusBlock), 220, 720)
```

Guarantees, asserted by `BoardGeometryTest.boardNeverBelowFloorOrOffWindow` (F18-AC9): the board is never below 220 dp or above 720 dp, so never negative; THREE_COLUMN and SIDE_PANEL are drawn only with a board of at least 220 dp and at least STACKED's whenever STACKED fits; outside SCROLL, the board plus its chrome heights fit `availH` (STACKED: `board + stacked(h, …) ≤ availH`; THREE_COLUMN: `rails(rail) ≤ availH`; SIDE_PANEL: `panel(panel) ≤ board`) and the board plus rails or panel and their gaps fit `availW`. The only horizontal excess is D-075's: a STACKED board of 220 dp when `availW < 220dp`.

**SCROLL** (D-188) is the last resort, for windows where no other arrangement keeps a 220 dp board, such as a 360×450 split-screen pane with the TalkBack pad:
- One centered column of width `w0` inside `Modifier.verticalScroll(rememberScrollState())`: the COMPACT header, the hint if shown (8 dp gaps), the board, then 8 dp, status line, 8 dp and pad row with the pad, or 8 dp and the controls bar without it (§6.6's STACKED pad row). The board fills the first screen below the header, together with the status line when the pad is shown, so live announcements stay on screen; the rest starts below the fold.
- `swipeToMove` is attached to the board instead of the whole content area (D-051): a drag that starts on the board is a move and never scrolls; a drag that starts anywhere else scrolls the column. Keys, the pad and the board's accessibility actions are unchanged, and focus moving to a control scrolls it into view.
- The hint and the snackbar host are placed as in STACKED; the snackbar overlays the board (§5.4). When a win or game-over card composes, the column scrolls until the whole board is visible: `ScrollState.animateScrollTo(target, GameMotion.overlaySpatial(1f))` under Full, `scrollTo(target)` under Reduced and Off.
- Traversal order and RTL follow STACKED (F17-AC7, §5.7).

**Overlay cards fit the board** (D-200): a win or game-over card's height budget is board − 16 dp, and its width is at most board − 16 dp. §6.3 and §6.4 give the Regular and Compact layouts and the selection rule.

**Worked examples.** Reference insets: portrait 48 dp top + 24 dp bottom; landscape and split-screen pane 24 dp top + 0 bottom; gesture insets ≤ margin; chrome at its nominal fontScale 1.0 heights. `game/board/BoardGeometryTest.boardSizePerMode` (the first nine rows) and `resolvedLayoutTable` (the rest) (JVM, M4) inject these insets and a nominal `ChromeMeasure` and assert each row's arrangement and board; `narrowWindowFloor` covers D-075's narrow rule. In the Mode column, a value in parentheses is what the rejected candidate would give.

| Window (dp) | Mode | Margins h / v | availW × availH | Board | 4×4 Classic gap / tile | 4×4 Neon gap / tile |
|---|---|---|---|---|---|---|
| 360×800 | STACKED | 16 / 24 | 328 × 680 | **328** | 10 / 69.5 | 12 / 67.0 |
| 411×914 (Pixel 7, default golden qualifier) | STACKED | 16 / 32 | 379 × 778 | **379** | 11 / 81.0 | 14 / 77.2 |
| 412×915 | STACKED | 16 / 32 | 380 × 779 | **380** | 11 / 81.2 | 14 / 77.5 |
| 915×412 | THREE_COLUMN, rail 201.3 | 32 / 16 | 851 × 356 | **356** | 11 / 75.2 | 13 / 72.8 |
| 673×841 | STACKED | 24 / 24 | 625 × 721 | **481** | 14 / 102.8 | 18 / 97.8 |
| 841×673 | SIDE_PANEL, panel 280 | 32 / 24 | 777 × 601 | **465** | 14 / 98.8 | 17 / 95.0 |
| 800×1280 | STACKED | 24 / 32 | 752 × 1144 | **720** (cap) | 20 / 155.0 | 20 / 155.0 |
| 1280×800 | SIDE_PANEL, panel 400 | 32 / 24 | 1216 × 728 | **720** | 20 / 155.0 | 20 / 155.0 |
| 360×640 (store phone) | STACKED | 16 / 24 | 328 × 520 | **280** | 8 / 60.0 | 10 / 57.5 |
| 360×450 (split-screen pane) | STACKED, COMPACT header (THREE_COLUMN: −16) | 16 / 16 | 328 × 394 | **234** | 7 / 49.8 | 9 / 47.2 |
| 360×450, TalkBack pad | SCROLL (THREE_COLUMN, rail 208: −112; STACKED: 130) | 16 / 16 | 328 × 394 | **274** | 8 / 58.5 | 10 / 56.0 |
| 411×450 (split-screen pane, F18) | STACKED, COMPACT header (THREE_COLUMN: 35) | 16 / 16 | 379 × 394 | **234** | 7 / 49.8 | 9 / 47.2 |
| 411×450, TalkBack pad | SCROLL (THREE_COLUMN, rail 208: −61; STACKED: 130) | 16 / 16 | 379 × 394 | **274** | 8 / 58.5 | 10 / 56.0 |
| 620×450 | STACKED, COMPACT header (THREE_COLUMN: 228, smaller) | 24 / 16 | 572 × 394 | **234** | 7 / 49.8 | 9 / 47.2 |
| 700×450 | THREE_COLUMN, rail 160 (STACKED: 234) | 24 / 16 | 652 × 394 | **308** | 9 / 65.8 | 11 / 63.2 |
| 900×1400 | STACKED (SIDE_PANEL, panel 288: 516) | 32 / 32 | 836 × 1264 | **720** (cap) | 20 / 155.0 | 20 / 155.0 |

### 5.4 Placement matrix

| Item | STACKED | THREE_COLUMN | SIDE_PANEL |
|---|---|---|---|
| Title "2048" | header row 1, start | stats rail top (autoSize 32–40 sp; dropped first if the rail overflows) | title row at the panel top, start (D-188) |
| Score, Best chips | header row 1, end (Score then Best) | stats rail, stacked, full rail width | panel, one row, each `(panel − 8)/2` |
| Moves / time | not on the HUD (D-006); shown on the game-over card, in the share text and in Statistics | same | same |
| Board-size chip | header row 2, start | stats rail, below Best | panel, below the chips |
| First-launch hint (§6.2) | between header and board, above the board (D-188) | actions rail, replacing Undo and New game while shown (D-188) | panel, above the buttons (D-188) |
| Undo, New game | controls bar (weights 1 : 1) | actions rail, stacked, full width | panel, one row (weights 1 : 1) |
| More (menu) | controls bar, end (56 dp) | actions rail, last | panel, title row end |
| Snackbar host (D-186) | in the gap between board and controls bar when that gap ≥ the snackbar's measured height + 16 dp; otherwise over the board, bottom-centred, width board − 16 dp, 8 dp above the board's bottom edge. Never over the controls bar | bottom of the actions rail | bottom of the panel, aligned with the board's bottom edge |
| TalkBack status line + pad | between board and controls; New game + More sit beside the pad (§6.6) | actions rail top (rail ≥ 208 dp) | panel, below the buttons |
| Confetti | full game-content overlay | same | same |
| Win / game-over card | centered on the board; never past its edges (D-200) | same | same |

STACKED, THREE_COLUMN and SIDE_PANEL put the board on the vertical centre line of the available area. Side-panel and 3-column groups are centered horizontally in `availW`.

**SCROLL** (§5.3) places every item as the STACKED column does with the COMPACT header (no title), top-aligned in its scrolling column; the snackbar uses the over-the-board placement.

**SIDE_PANEL alignment** (D-188): the panel's height equals the board's, and its top aligns with the board's top. The title row (title and More) is at the top of the panel, and the snackbar host at its bottom, on the board's bottom edge.

### 5.5 Wireframes

**A. Compact portrait, 360×800, STACKED.** The board is 328 dp. Header and controls are as wide as the board.

```
┌──────────────────────────── 360 ────────────────────────────┐
│ status bar (safeDrawing ≈ 48)                               │
│ 16 ┌──────────────────────── 328 ────────────────────────┐ 16│
│    │ 2048                  ┌─ SCORE ─┐ 8 ┌── BEST ──┐     │  │ row 1, 64
│    │ (GSF 850, ≤56 sp)     │  10220  │   │  24680   │     │  │ chips ≥ 88 × 64
│    │                       └─────────┘   └──────────┘     │  │
│    │ ( ▦ 4×4 ▾ )                                          │  │ row 2, 48
│    └──────────────────────────────────────────────────────┘  │
│                         ↕ ≥ 24 (flex)                        │
│    ┌──────────────── board 328 (r 4.9 + 10) ──────────────┐  │
│    │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   gap 10         │  │
│    │ │   2  │ │   4  │ │   8  │ │  16  │   tile 69.5      │  │
│    │ └──────┘ └──────┘ └──────┘ └──────┘                  │  │
│    │   …  (4 rows)                                        │  │
│    └──────────────────────────────────────────────────────┘  │
│                         ↕ ≥ 24 (flex)                        │
│    ┌──────────────────────────────────────────────────────┐  │
│    │ [ ↶ Undo     ]  8 [ ↻ New game   ]  8 [ ⋮ ]           │  │ controls, 80 (buttons 56)
│    └──────────────────────────────────────────────────────┘  │
│ navigation bar (safeDrawing ≈ 24)                            │
└──────────────────────────────────────────────────────────────┘
```

**B. Phone landscape, 915×412, THREE_COLUMN.** Rails are 201 dp and the board is 356 dp. Content width 782 dp is centered in 851 dp.

```
┌──────────────────────────────────── 915 ─────────────────────────────────────┐
│ status (24)                                                                  │
│ 32 ┌── stats rail 201 ──┐ 12 ┌────── board 356 ──────┐ 12 ┌─ actions rail 201 ─┐ 32│
│    │ 2048 (≤ 40 sp)     │    │                       │    │ [ ↶ Undo         ] │   │
│    │ ┌ SCORE ─────────┐ │    │                       │    │ [ ↻ New game      ] │   │
│    │ │ 10220          │ │    │       4×4 board       │    │ [ ⋮ More          ] │   │
│    │ └────────────────┘ │    │     gap 11 tile 75.2  │    │                    │   │
│    │ ┌ BEST ──────────┐ │    │                       │    │ (snackbar host)    │   │
│    │ │ 24680          │ │    │                       │    │                    │   │
│    │ └────────────────┘ │    │                       │    │                    │   │
│    │ ( ▦ 4×4 ▾ )        │    │                       │    │                    │   │
│    └────────────────────┘    └───────────────────────┘    └────────────────────┘   │
│ 16 (vertical margin)                                                         │
└──────────────────────────────────────────────────────────────────────────────┘
```

**C. Side panel: foldable landscape 841×673 (panel 280, board 465) and tablet landscape 1280×800 (panel 400, board 720).** The panel is as tall as the board and top-aligned with it (D-188).

```
┌──────────────────────────────── W ──────────────────────────────────┐
│ status (24)                                                         │
│ 32 ┌──────────── board (465 | 720) ────────────┐ 32 ┌── panel (280 | 400) ──┐ 32│
│    │                                           │    │ 2048 (≤ 64 sp)    [ ⋮ ]│   │
│    │                                           │    │ ┌ SCORE ┐ 8 ┌ BEST ──┐ │   │
│    │                                           │    │ │ 10220 │   │ 24680  │ │   │
│    │              N×N board                    │    │ └───────┘   └────────┘ │   │
│    │       (vertically centered)               │    │ ( ▦ 4×4 ▾ )            │   │
│    │                                           │    │ [hint card, if shown]  │   │
│    │                                           │    │ [ ↶ Undo ] [ ↻ New ]   │   │
│    │                                           │    │ (status line + pad)    │   │
│    │                                           │    │ (snackbar host)        │   │
│    └───────────────────────────────────────────┘    └────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

**D. Medium portrait 673×841 and tablet portrait 800×1280 (STACKED).** Same as A, scaled. The board is 481 / 720 dp, and header and controls are 481 / 720 dp wide, centered. The title max is 56 sp (width < 840).

**E. STACKED with the TalkBack pad, 360×800.** Nominal chrome is 336 dp and the board is still 328 dp.

```
│    ┌──────────────── board 328 ───────────────────┐
│    └──────────────────────────────────────────────┘
│                        16
│    Moved left. Merged 4 and 4. New 2 at row 4,       ← status line, ≥ 40, polite live region
│    column 1. Score 10,228.
│                        8
│    ┌─────── pad 208 ───────┐ 16 ┌──── 104 ────┐
│    │      [  ↑  ] [ ↶ ]    │    │ [ New game ] │  56
│    │ [ ← ] [  ↓  ] [ → ]   │    │ [    ⋮     ] │  56
│    └───── 136 high ────────┘    └──────────────┘
```

**F. Narrow window, `availW < 280dp`.** The board is `max(220, availW)`, and the header becomes the compact 64 dp row (size chip, Score, Best) with no title (D-075). If the window is also too short for that board, the arrangement is SCROLL (§5.3).

**G. SCROLL, 360×450 split-screen pane with the TalkBack pad.** THREE_COLUMN would leave −112 dp and STACKED 130 dp, so the column scrolls. The first screen (394 dp of `availH`) holds the compact header, the 274 dp board and the status line; the pad row starts below the fold.

```
┌──────────────────────── 360 ────────────────────────┐
│ status bar (24), then the 16 dp vertical margin     │
│ 16 ┌──────────────────── 328 ───────────────────┐ 16│
│    │ ( ▦ 4×4 ▾ )   ┌─ SCORE ─┐ 8 ┌── BEST ──┐   │   │ compact header, 64
│    └───────────────────────────────────────────┘   │
│                         8                           │
│          ┌──────── board 274 ─────────┐             │ swipes start here
│          │   gap 8, tile 58.5         │             │
│          └────────────────────────────┘             │
│                         8                           │
│    Moved left. Merged 4 and 4. New 2 at …           │ status line, 40
│ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  fold  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│                         8                           │ (scrolled into view)
│    ┌──── pad 208 ────┐ 16 ┌──── 104 ────┐            │ pad row, 136
│    └─────────────────┘    └─────────────┘            │
└─────────────────────────────────────────────────────┘
```

### 5.6 Touch targets and the gesture surface

- Every interactive element is ≥ 48×48 dp (`minimumInteractiveComponentSize`; D-084).
  - Game controls are 56 dp (`ButtonDefaults.MediumContainerHeight`, `IconButtonDefaults.mediumContainerSize()`).
  - Pad buttons are 64 dp (D-082).
- A drag that starts on a button, chip or chip area is a swipe. The control must not fire (D-051).
  - The gesture detector sits on the parent with `PointerEventPass.Initial` and consumes the down/move events once the touch slop is exceeded.
  - The buttons' own `clickable` then sees a cancelled gesture.
  - `SwipeInputTest` asserts that a swipe starting on New game moves the board and does not open the dialog.
- Scrollable regions (sheets, dialogs, sub-screens) are never inside the Game gesture surface. In SCROLL the gesture surface shrinks to the board, so the scrolling column lies outside it (§5.3; `SwipeInputTest.scrollModeMovesOnlyFromBoard`).
- Every screen-level screenshot test runs `assertTouchTargets()`, the size-and-overlap rule of `QUALITY.md` §1.12, which gates like ATF (D-205).

### 5.7 RTL (D-104)

- Chrome mirrors:
  - title and chips swap sides;
  - the controls bar order becomes [⋮][New game][Undo];
  - the 3-column rails swap: actions rail on the left, stats rail on the right;
  - the side panel moves to the left of the board;
  - `ic_arrow_back`, `ic_chevron_right`, `ic_open_in_new`, `ic_help` and `ic_undo` auto-mirror.
- **Never mirrored**, all inside `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`:
  - the board subtree, including cell semantics order;
  - the gesture detector;
  - the key map;
  - the TalkBack move actions;
  - the direction pad and its icons (`ic_pad_*`, `autoMirrored` removed).
- Game numerals stay Latin (§3.7). `fa` is the RTL golden locale and `ar-XB` the bidi pseudo-locale (§10).

### 5.8 Mode transitions and recreation

- Rotation, resize, fold and locale or `uiMode` changes recreate the activity and recompose from the ViewModel (an Immersive toggle is a live inset change, below). Tiles are `Static` (D-047, D-095): a composition created after a move, win or game over was shown draws its final frame, because the ViewModel settles each animated value once it has been presented (`ARCHITECTURE.md` §4.11).
- Within one arrangement, under Full motion, board, panel and rail bounds animate with `Modifier.animateBounds(lookaheadScope, boundsTransform = { _, _ -> GameMotion.overlaySpatial() })`. Under Reduced and Off they snap (D-063, D-095).
- A switch between arrangements is never animated: the new arrangement's board is composed anew and draws its settled static frame at its final bounds (§5.3, D-095).
- A theme or palette switch crossfades every palette color with `GameMotion.slowEffects()` via `animateColorAsState`, read in draw (D-064). Under Reduced and Off it is instant.

---

## 6. Screens

### 6.1 Game: playing (`GameKey`, `game/GameScreen.kt`)

**Components**

| Component | Composable | Spec |
|---|---|---|
| Title | `GameTitle` | §3.5. `semantics { heading() }`. Its text is the non-translatable `app_name` "2048" |
| Score chip | `ScoreChip(kind = SCORE)` | §7.2 |
| Best chip | `ScoreChip(kind = BEST)` | §7.2. Shows `max(records[size].bestScore, score)`, updated live (D-043) |
| Board-size chip | `SizeChip` | §7.3. Opens §6.7 |
| Board | `BoardView` (`game/board/Board.kt`) | §4, D-025. Semantics D-080 |
| Undo | `GameButton.Undo` | `FilledTonalButton`, icon `ic_undo`, `undo` = "Undo". Disabled when `moveCount == 0` (D-039) |
| New game | `GameButton.NewGame` | `OutlinedButton` (border `outline`, content `onSurface`), icon `ic_restart` (Material Symbols `restart_alt`), `new_game` = "New game". Shows a confirmation when the game is in progress (§6.5) |
| More | `GameMoreMenu` | `FilledTonalIconButton`, `ic_more_vert`, `more_options` = "More options". Opens a `DropdownMenu` with items `menu_statistics` "Statistics" (`ic_bar_chart`), `settings` "Settings" (`ic_settings`, the migrated legacy key, D-101), `menu_how_to_play` "How to play" (`ic_help`) and `menu_about` "About" (`ic_info`). Uses the classic `DropdownMenuItem(text, onClick, leadingIcon)` overload |
| Snackbar host | `SnackbarHost` | §7.6 |
| Effect canvases | `BoardEffectsCanvas` (win glow ring) and `ContentEffectsCanvas` (confetti, new-best sparkles), both in `game/board/EffectsCanvas.kt` | D-064, D-187, §4.7 |

**States**

| State | Condition | Visual |
|---|---|---|
| Loading | The splash was released at its 1000 ms cap before the game loaded (D-112) | Board with empty cells only; its description is `a11y_board_loading` = "Game board, %1$s by %1$s. Loading.". Chips show "–" (U+2013). The size chip shows `settings.lastBoardSize`. Undo and New game are disabled. No hint and no spinner. When data arrives, tiles render `Static` |
| Fresh | `moveCount == 0`, `PLAYING` | Undo disabled. New game starts at once (no confirmation; D-007) |
| First launch | `onboardingDone == false` and the starting game has `moveCount == 0` and `undoCount == 0`; a missing game file counts as `moveCount == 0` (D-201) | Hint card (§6.2) |
| In progress | `moveCount ≥ 1` and `PLAYING` / `KEEP_PLAYING` | Undo enabled. New game asks first (§6.5) |
| Invalid move | `MoveOutcome.Invalid` | Nudge (D-064) or the Reduced outline pulse (§2.10). Reject haptic, at most 1 per 400 ms, and none below API 30 (D-185) |
| New best | First accepted move with `score > bestAtStart > 0` | BEST chip turns NEW BEST (§7.2) and **stays** that way for the rest of the game, because `newBestAnnounced` persists (D-044) |
| Won | `WON` | Win card (§6.3) |
| Keep playing | `KEEP_PLAYING` | Normal play. Tiles above the target continue on the ramp (§2.6) |
| Over | `OVER` | Game-over card (§6.4) |
| Modal open | A dialog, sheet or menu is visible | Fully modal: swipes and game keys are ignored (D-054, D-199) |
| Card shown | The win or game-over card is visible | Board swipes and direction keys are ignored. The controls-bar Undo and New game, the size chip and More stay enabled and act like the card's buttons. U, Ctrl+Z and Backspace undo; N and R start a new game with no confirmation; Enter and Space activate the focused card button (D-199) |

### 6.2 Game: first-launch hint (D-009, D-201)

- **Shown when** `onboardingDone == false` and the starting game has `moveCount == 0` and `undoCount == 0`; a missing game file counts as `moveCount == 0` (D-201).
- `HintCard` is a `Card` with color `secondaryContainer` and shape `shapes.large`. Its padding is 16 dp start/end and 12 dp top/bottom.
- **Content:** a `Row` of the text `hint_first_launch` = "Swipe to move all tiles. Equal tiles merge. Reach %1$s." (`bodyMedium`, weight 1, wraps) and a `TextButton` `got_it` = "Got it".
  - `%1$s` is the target as a game numeral: `"2048"`, or `"256"` on 3×3.
  - While touch exploration is on, the text is `hint_first_launch_talkback` = "Use the arrow buttons to move all tiles. Equal tiles merge. Reach %1$s." instead, because TalkBack consumes swipes (D-202).
- **Placement** follows §5.4 (D-188). In STACKED and SCROLL it sits between header and board, with 8 dp gaps, and is part of the measured chrome (§5.3 `top`).
- **Dismissal:** it disappears on the first accepted move, or on "Got it", and sets `onboardingDone = true`.
  - Under Full motion it exits with `shrinkVertically(GameMotion.overlaySpatial()) + fadeOut(GameMotion.fastEffects())`; it is instant otherwise.
  - The board grows back with `animateBounds` under Full motion while the arrangement stays the same; if the hint's exit switches the arrangement, the board appears at its new size at once (§5.3).
- **Semantics:** a polite live region, read once when shown.

### 6.3 Game: win card (D-008, D-064, D-200)

Regular layout (`game/overlay/WinCard.kt`, container and scrim from `game/overlay/BoardScrim.kt`):

```
┌─────── ElevatedCard  w = min(clamp(board − 32, 264, 400), board − 16) ───────┐
│ 24                                                                     [ ⤴ ] │  Share: 48 dp IconButton, top-end
│                       You win!                                               │  displaySmallEmphasized
│ 16                                                                           │
│                     ✹  2048  ✹                                               │  hero badge 96 dp (72 | 120)
│ 24                                                                           │
│  [             Keep going  (Button, 56)             ]                        │
│ 8                                                                            │
│  [ New game (FilledTonalButton) ] 8 [ Undo (TextButton) ]                    │  weights 1 : 1
│ 24                                                                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Strings:** `win_title` = "You win!", `keep_going` = "Keep going", `new_game`, `undo`, `share`.
- **Share** (D-200): a 48 dp `IconButton` with `ic_share` and content description `share`, at the card's top-end corner. It sends D-008's text for the current state through `Intent.createChooser(intent, share_chooser_title)`, as on the game-over card (§6.4).
- **Hero badge** (`WinBadge`, in `game/overlay/WinCard.kt`):
  - The shape is a graphics-shapes `Morph(circle, star(12, 0.88, CornerRounding(0.14)))` drawn with `Morph.toPath`. It is stable; no `MaterialShapes` (D-012).
  - Fill and ink are `palette.fill(targetExp)` / `ink(targetExp)`, plus the HC outline when active.
  - The label is the target (`2048` or `256`), sized by §3.3 with `t = 0.8 · badge`.
- **Fit** (D-200):
  - The card is centered on the board's centre. Its height budget is board − 16 dp, and its width is at most board − 16 dp, so it never extends past the board's edges.
  - **Selection rule.** The Regular layout is measured once per (board size, fontScale, locale). Regular is used when its height ≤ board − 16 dp, otherwise the Compact layout (§6.3a). If even Compact is taller, it scrolls vertically inside the card.
  - At fontScale 1.0 on every golden qualifier, the card stays within the board.
  - The scrim is `surface` at alpha 0.60, clipped to the board shape.
- **Interactivity** (D-008, D-200): the buttons, including Share, are composed with `enabled = true` from their first frame, so they always draw in enabled colours. `OverlayController.interactive` gates `onClick`, adds `semantics { disabled() }` and sets `focusProperties { canFocus = interactive }` until T0 + 800 ms under Full (T0 = origin + 80 ms, so origin + 880; `MOTION_SPEC.md` §3.11), 150 ms under Reduced, and at once under Off. The card never blocks input longer.
- **Focus and a11y:** `paneTitle = win_title`. One assertive announcement. Focus moves to Keep going on the interactive frame (D-081). Back is not intercepted (D-054).
- **Golden capture state:** Motion Off (§10.2): morph complete, rotation 60°, no confetti.

### 6.3a Game: Compact win card (D-200)

Used when the Regular layout does not fit the board − 16 dp budget (§6.3), for example on the 280 dp board of a 360×640 window (store screenshot 2, §8.6). Height ≈ 220 dp.

```
┌─────── ElevatedCard  w = min(clamp(board − 32, 264, 400), board − 16) ───────┐
│ 20                                                                     [ ⤴ ] │  Share: 48 dp IconButton, top-end
│                       You win!                                               │  headlineSmallEmphasized
│ 12                                                                           │
│                       ✹ 2048 ✹                                               │  WinBadge 64 dp (label t = 0.8 · 64)
│ 16                                                                           │
│  [ Keep going (Button, weight 1) ] 8 [ ↻ ] 8 [ ↶ ]                           │  one 56 dp row
│ 20                                                                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Row:** `Keep going` is a `Button` with weight 1 and `maxLines = 2`; New game is a `FilledTonalIconButton` of `IconButtonDefaults.mediumContainerSize()` with `ic_restart` and content description `new_game`; Undo is a 56 dp `IconButton` with `ic_undo` and content description `undo`.
- Share, badge, scrim, interactivity, focus (Keep going) and a11y are exactly as in §6.3.

### 6.4 Game: game-over card (D-008, D-200)

Regular layout (`game/overlay/GameOverCard.kt`, container and scrim from `game/overlay/BoardScrim.kt`):

```
┌────────────────── ElevatedCard (width: Fit) ──────────────────┐
│ 24                                                            │
│                      Game over                                │  displaySmallEmphasized
│ 12                                                            │
│                        SCORE                                  │  labelMediumEmphasized
│                        20316                                  │  GSF 750, autoSize 20–40 sp
│ 12                                                            │
│   ┌ BEST ────────┐  8  ┌ BEST TILE ─────┐                     │  surfaceContainerHighest boxes, 16 dp radius
│   │ 24680        │     │ [2048 mini 40] │                     │
│   └──────────────┘     └────────────────┘                     │
│ 12                                                            │
│        1,024 moves · 12:41 · No undo                          │  bodyMedium, onSurfaceVariant
│ 24                                                            │
│  [               New game  (Button, 56)               ]       │
│ 8                                                             │
│  [ Undo (FilledTonalButton) ] 8 [ ⤴ Share (OutlinedButton) ]  │
│ 24                                                            │
└───────────────────────────────────────────────────────────────┘
```

- **Strings:**
  - `game_over_title` = "Game over", `score_label` = "Score", `best_label` = "Best", `best_tile` = "Best tile", `share` = "Share";
  - plurals `moves_count` (one "%1$s move", other "%1$s moves");
  - plurals `undos_count` (one "%1$s undo", other "%1$s undos"), or `undos_none` = "No undo" when `undoCount == 0`;
  - the summary is joined with " · " (U+00B7, written into the format string `game_over_summary` = "%1$s · %2$s · %3$s"). Time is `formatElapsed(elapsedMs)` (§3.7).
- **Values:** Score and Best are game numerals (GSF, `Long.toString`). Best is the size's `bestScore` after this game. The best-tile `MiniTile` is described by `a11y_best_tile` = "Best tile, %1$s" with the full decimal value.
- **Share** builds D-008's text and calls `Intent.createChooser(intent, share_chooser_title)` with `share_chooser_title` = "Share result".
- **Scrim and board:** `surface` at alpha 0.78, clipped to the board shape. Tiles at scale 0.94 and saturation 0.2 (D-064, D-183); the board and empty cells keep their colours. This is also the static `OVER` render used by restore, Motion Off and the goldens.
- **Fit** (D-200): as in §6.3. Regular when it fits board − 16 dp, otherwise the Compact layout (§6.4a); if Compact is still taller, it scrolls inside the card.
- **Interactivity** (D-008, D-200): the buttons are composed with `enabled = true` from their first frame. `OverlayController.interactive` gates `onClick`, adds `semantics { disabled() }` and sets `focusProperties { canFocus = interactive }` until T0 + 700 ms under Full (T0 = origin + 300 ms, D-183), 150 ms under Reduced, and at once under Off.
- **Focus:** `paneTitle = game_over_title`, assertive once, focus on New game on the interactive frame (D-081). The card cannot be dismissed; only New game and Undo leave it (D-054), including the controls-bar buttons and the U/N/R keys (D-199).

### 6.4a Game: Compact game-over card (D-200)

Used when the Regular layout does not fit the board − 16 dp budget (§6.4). Height ≈ 248 dp with a one-line summary.

```
┌─────── ElevatedCard  w = min(clamp(board − 32, 264, 400), board − 16) ───────┐
│ 20                                                                           │
│                      Game over                                               │  headlineSmallEmphasized
│ 12                                                                           │
│  ┌ SCORE ──┐ 8 ┌ BEST ───┐ 8 ┌ BEST TILE ┐                                   │  3 boxes, equal weights, min height 64
│  │ 20316   │   │ 24680   │   │ [2048 32] │                                   │
│  └─────────┘   └─────────┘   └───────────┘                                   │
│ 8                                                                            │
│        1,024 moves · 12:41 · No undo                                         │  bodyMedium, onSurfaceVariant, maxLines 2
│ 16                                                                           │
│  [ New game (Button, weight 1) ] 8 [ ↶ ] 8 [ ⤴ ]                             │  one 56 dp row
│ 20                                                                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Boxes:** `surfaceContainerHighest`, 16 dp radius, 8 dp gaps, minimum height 64 dp, equal weights. Labels `score_label`, `best_label` and `best_tile` (`labelMediumEmphasized`, upper-cased with the locale). The SCORE and BEST values use the GSF score instance with `autoSize = StepBased(14.sp, 24.sp, 1.sp)`; BEST TILE shows a 32 dp `MiniTile` described by `a11y_best_tile`.
- **Summary:** the same `game_over_summary` line as §6.4, `bodyMedium`, `onSurfaceVariant`, `maxLines = 2`.
- **Row:** New game is a `Button` with weight 1; Undo is a 56 dp `FilledTonalIconButton` with `ic_undo` and content description `undo`; Share is a 56 dp `OutlinedIconButton` with `ic_share` and content description `share`.
- Scrim, board state, interactivity, focus (New game) and a11y are exactly as in §6.4.

### 6.5 Game: new-game confirmation (D-007)

- An M3 `AlertDialog` (the android overload; public; verified 2026-10-28):
  - `title = new_game_confirm_title` = "Start a new game?";
  - `confirmButton = TextButton(new_game)`;
  - `dismissButton = TextButton(cancel)`, with `cancel` = "Cancel".
- It has no body and no icon. Container `surfaceContainerHigh`, shape 28 dp (M3 defaults).
- Cancel, Back and an outside tap dismiss it with no state change.
- **Shown only** when the game is in progress. From the win or game-over card, New game acts immediately, whether it comes from a card button, the controls bar or the N and R keys (`WON` and `OVER` are not "in progress"; D-007, D-199).
- **Triggers:** the New game button, the N or R key, and the TalkBack "New game" action.

### 6.6 Game: TalkBack direction pad and status line (D-081, D-082)

- **Visible while** `AccessibilityManager.isTouchExplorationEnabled` (listener-observed).
- **Status line** (`game/StatusLine.kt`):
  - `Text` with `bodyMedium`, `onSurfaceVariant`, `minHeight = 40.dp`, no `maxLines` (it wraps; the measured chrome of §5.3 absorbs the growth, D-084);
  - `semantics { liveRegion = LiveRegionMode.Polite }`;
  - content per D-081, completed by D-202. Besides D-081's move and merge sentences, it reads:
    - **after an accepted move,** `a11y_spawned` = "New %1$s at row %2$s, column %3$s.", placed after the merged sentence and before the score. The value is a game numeral; row and column are 1-based, as in `a11y_cell_tile`. Example: "Moved left. Merged 64 and 16. New 2 at row 4, column 1. Score 1,240.";
    - **after every undo,** `a11y_undone` = "Move undone. Score %1$s.";
    - **after New game,** `a11y_new_game` = "New game started.", then one `a11y_spawned` per start tile;
    - **after a size switch,** `a11y_size_switched` = "Board %1$s. Score %2$s.", with the board as `"$n×$n"` and the score from `formatCount(score, locale)`.
- **Pad:**
  - a 2-row × 3-column grid of `FilledTonalIconButton`s, each `Modifier.size(64.dp)`, 8 dp apart (208 × 136 dp):
    - row 1: [empty][↑ `ic_pad_up`, `move_up` = "Move up"][↶ `ic_undo`, `undo`];
    - row 2: [← `ic_pad_left`, `move_left` = "Move left"][↓ `ic_pad_down`, `move_down` = "Move down"][→ `ic_pad_right`, `move_right` = "Move right"];
  - icons are 32 dp;
  - the whole pad sits in a forced-LTR subtree (D-104).
- While the pad is visible, the controls bar's Undo is **removed** (the pad has Undo), so TalkBack meets no duplicate.
  - In STACKED and SCROLL the controls become a 104 dp column of New game (text only) and More, beside the pad (§5.5 E, G).
- The `move_*` strings are the same resources as the board's custom actions (D-080).

### 6.7 Board-size picker (D-007; `game/overlay/BoardSizeSheet.kt`)

- A `GameModalSheet` (§7.4) with title `board_size_title` = "Board size" (`titleLarge`, 24 dp start padding, 16 dp top).
- **Six rows**, 3×3 to 8×8, each a `ListItem(selected = n == current, onClick = …) { headline }` (§7.9) at least 72 dp tall, inside a `selectableGroup()`, with `semantics { role = Role.RadioButton }`:

| Slot | Content |
|---|---|
| Leading | `MiniBoardGlyph(n)`: 32 dp, N×N cells in `palette.fill(1)` on the board colour, drawn in code (§7.8) |
| Headline (the trailing `content` lambda) | `"$n×$n"` (system font `bodyLarge`) |
| Supporting | `board_size_goal` = "Goal %1$s" (`"2048"` or `"256"`), then " · ", then `board_size_best` = "Best %1$s" (`formatCount(best, locale)`) or `board_size_no_best` = "No best score yet". Two strings joined with `board_size_supporting` = "%1$s · %2$s" |
| Trailing | `in_progress` = "In progress" pill (`labelMedium`, `secondaryContainer` / `onSecondaryContainer`, `CircleShape`, padding 8×4 dp) when that size's game is in progress; then `ic_check` (`primary`) for the current size |

- **Behavior:** tapping a row selects it, dismisses the sheet and switches size with no confirmation (D-007). The current size is announced as selected.
- The sheet's maximum width is the M3 default (640 dp). It opens with `skipPartiallyExpanded` (§7.4).

### 6.8 Settings (`SettingsKey`, `settings/SettingsScreen.kt`)

- A `TopAppBar` titled `settings` with an `ic_arrow_back` navigation icon (`navigate_up` = "Navigate up").
- The body is a `LazyColumn` of at most 640 dp, centered. Rows are in exactly the D-006 order, grouped by section headers (`titleSmallEmphasized`, `primary`, 16 dp start, 24 dp top, 8 dp bottom).

| # | Section | Row (`key` = headline) | Control | Values (**default**) | Supporting text | Visibility and behavior | Field (D-042) |
|---|---|---|---|---|---|---|---|
| 1 | `settings_section_appearance` "Appearance" | `setting_theme` "Theme" | Choice dialog | `theme_system` "System default" (**default**) / `theme_light` "Light" / `theme_dark` "Dark" | current value | | `themeMode` |
| 2 | | `setting_palette` "Palette" | Choice dialog with swatches | `palette_classic` "Classic Refined" (**default**) / `palette_neon` "Neon Arcade" (both `translatable="false"`) | current value. Trailing: a 4-tile `PaletteSwatch` of the current palette | Each dialog option shows tiles 2, 8, 128 and 2048 (D-006) | `palette` |
| 3 | | `setting_high_contrast` "High-contrast tiles" | Switch | **Off** / On | `setting_high_contrast_summary` "Black or white numbers and tile outlines" | While system contrast ≥ 0.5 (API 34+): checked, disabled, summary `setting_high_contrast_forced` "On because the system contrast setting is high" | `highContrast` |
| 4 | | `setting_dynamic_color` "Dynamic color" | Switch | **Off** / On | `setting_dynamic_color_summary` "Use wallpaper colors for buttons and panels. Tiles keep their palette." | API 31+ only (D-074) | `dynamicChrome` |
| 5 | `settings_section_motion` "Motion and feedback" | `setting_motion` "Motion" | Choice dialog | `motion_full` "Full" (**default**) / `motion_reduced` "Reduced" / `motion_off` "Off" | current value | When the animator scale is 0: disabled, summary `motion_off_system` "Off (system animations are off)" (D-006) | `motion` |
| 6 | | `setting_haptics` "Haptics" | Switch | **On** / Off | `setting_haptics_summary` "Feedback for merges, wins and blocked moves" | Toggle fires `ToggleOn`/`ToggleOff` (D-065) | `haptics` |
| 7 | `settings_section_controls` "Controls" | `setting_move_timing` "Move timing" | Choice dialog | `move_timing_swipe` "As you swipe" (**default**) / `move_timing_release` "When you lift your finger" | current value | | `moveTiming` |
| 8 | | `setting_swipe_sensitivity` "Swipe sensitivity" | Choice dialog | `sensitivity_low` "Low" / `sensitivity_medium` "Medium" (**default**) / `sensitivity_high` "High" | current value | Option supporting lines: `sensitivity_low_detail` "Needs a longer swipe", `sensitivity_medium_detail` "Recommended", `sensitivity_high_detail` "Reacts to short swipes" (40 / 24 / 12 dp, D-050) | `swipeSensitivity` |
| 9 | `settings_section_display` "Display" | `setting_immersive` "Immersive mode" | Switch | **Off** / On | `setting_immersive_summary` "Hide the status and navigation bars" (D-006) | Applied app-wide immediately (D-093) | `immersive` |
| 10 | | `setting_keep_screen_on` "Keep screen on while playing" | Switch | **Off** / On | `setting_keep_screen_on_summary` "Only while a game is in progress" | `LocalView.current.keepScreenOn` while Game is visible and in progress | `keepScreenOn` |
| 11 | `settings_section_language` "Language" | `setting_app_language` "App language" | Link row | (system page) | current app locale display name, or `setting_app_language_system` "System default". Trailing `ic_open_in_new` | API 33+ only. Opens `Settings.ACTION_APP_LOCALE_SETTINGS` (D-102) | — |

- **Rows** (`ui/components/SettingRows.kt`) use the non-deprecated `ListItem` overloads of §7.9, with the headline as the trailing `content` lambda: `ListItem(…) { headline }`. Each row is at least 56 dp (one line) or 72 dp (two lines).
  - **Switch rows:** `ListItem(checked, onCheckedChange) { headline }`, with the summary as `supportingContent` and the `Switch` as `trailingContent` (role `Switch`).
  - **Choice rows:** `ListItem(modifier = Modifier.clickable(onClickLabel = stringResource(R.string.a11y_action_change), role = Role.Button, onClick = …), supportingContent = …, trailingContent = …) { headline }`. `a11y_action_change` = "Change".
  - **Link rows:** the same, with `onClickLabel` = `a11y_action_open` = "Open".
- **The choice dialog** (`ChoiceDialog`, §7.5) is an `AlertDialog` whose radio rows are `ListItem(selected, onClick) { label }` with a leading `RadioButton(selected, onClick = null)`, role `RadioButton`, 56 dp each, plus an optional supporting line. Selecting an option applies it, persists it and dismisses the dialog; there is no OK button. `dismissButton = TextButton(cancel)`.
- **Persistence:** every change persists immediately (D-043 writer). There is no Save button.
- **Not in Settings:** Reset statistics lives in Statistics (D-006).

### 6.9 Statistics (`StatsKey`, `stats/StatsScreen.kt`)

- **Top bar:** `TopAppBar` titled `statistics` = "Statistics", back navigation, and the action `ic_delete` with content description `stats_reset` = "Reset statistics".
- **Tabs:** `PrimaryScrollableTabRow` (the non-deprecated overload; verified 2026-10-28) with `Tab`s `stats_all_sizes` = "All sizes", then "3×3" … "8×8". The initially selected tab is the current board size. Tab state is `rememberSaveable`.
- **Content per tab:** a `LazyColumn` of at most 640 dp.
  1. **Hero card** (`surfaceContainerHigh`, `shapes.large`, 16 dp padding):
     - label `stats_best_score` = "Best score" (`labelLarge`, `onSurfaceVariant`);
     - value (`headlineLarge`, `"tnum"`, `formatCount(bestScore, locale)`);
     - at the end, a `MiniTile(bestTileExp, 56.dp)` labeled `stats_best_tile` = "Best tile". Its a11y text is `a11y_best_tile` = "Best tile, %1$s" with the full decimal value.
  2. **Rows:** `ListItem(trailingContent = { value }) { headline }` (§7.9), headline `bodyLarge`, trailing value `titleMedium` `"tnum"`:

| Key | Headline | Value |
|---|---|---|
| `stats_games_played` | "Games played" | `formatCount(gamesPlayed, locale)` |
| `stats_wins` | "Wins" | `stats_wins_value` = "%1$s (%2$s)": count (`formatCount(gamesWon, locale)`) and win-rate percent (`formatPercent(gamesWon, gamesPlayed, locale)`; "0%" when 0 games) |
| `stats_best_no_undo` | "Best score without undo" | `formatCount(bestScoreNoUndo, locale)` |
| `stats_average_score` | "Average score" | `formatCount(totalScore / gamesPlayed, locale)`, or "–" when 0 games |
| `stats_total_moves` | "Total moves" | `formatCount(totalMoves, locale)` |
| `stats_time_played` | "Time played" | `formatElapsed(totalTimeMs)` |
| `stats_undos_used` | "Undos used" | `formatCount(totalUndos, locale)` |

- **"All sizes"** shows sums for counts, maximums for bests, `max(bestTileExp)`, and `Σ totalScore / Σ gamesPlayed` for the average.
- **Unfinalized finished games** are folded in for display only: for each size, the tab shows `RecordsMath.displayed(records, game)` without persisting it. That adds the saved game's uncounted recovery entry and the saved game itself when it is `OVER`, and skips any `gameId` that is already finalized (D-198, D-044).
- **Empty state** applies when every field of the tab's record is 0:
  - a centered `MiniBoardGlyph(n)` at 96 dp (4×4 for "All sizes");
  - `stats_empty_title` = "No games on %1$s yet" (the `"$n×$n"` label), or `stats_empty_title_all` = "No games yet";
  - `stats_empty_body` = "Finished and abandoned games appear here.";
  - no zero rows.
- **Reset** takes two sequential `AlertDialog`s (D-044):
  1. `stats_reset_title` "Reset statistics?" / `stats_reset_body` "Best scores and statistics for every board size will be cleared. Games in progress are kept." / confirm `reset` "Reset" / `cancel`.
  2. `stats_reset_confirm_title` "Reset everything?" / `stats_reset_confirm_body` "This can't be undone." / confirm `stats_reset_confirm_button` "Reset everything" / `cancel`.
  3. Then the snackbar `stats_reset_done` = "Statistics reset".

### 6.10 How to play (`HowToPlayKey`, `about/HowToPlayScreen.kt`)

- A `TopAppBar` titled `how_to_play` = "How to play" with back navigation. The body is a scrolling column of at most 640 dp.
- Sections use a `titleSmallEmphasized` `primary` header (`semantics { heading() }`) and a `bodyLarge` body:

| Section key | Header | Body |
|---|---|---|
| `htp_goal` | "Goal" | `htp_goal_body` "Slide the tiles to merge equal numbers and build the %1$s tile. On a 3×3 board the goal is %2$s." (`"2048"`, `"256"`) |
| `htp_moving` | "Moving" | `htp_moving_body` "Swipe up, down, left or right. Every tile slides as far as it can in that direction." |
| `htp_merging` | "Merging" | `htp_merging_body` "Two touching tiles with the same number merge into one tile with their sum. A tile merges only once per move." Followed by `MergeIllustration`: two mini-tile rows (40 dp tiles, palette colors, forced LTR) "2 2 2 2" → "4 4 · ·", with a 24 dp `ic_pad_right` between, and the a11y text `htp_merging_illustration_cd` "Example: 2, 2, 2, 2 moved left becomes 4, 4" |
| `htp_new_tiles` | "New tiles" | `htp_new_tiles_body` "After every move that changes the board, a new tile appears in a random empty cell: a 2 nine times out of ten, otherwise a 4." |
| `htp_board_sizes` | "Board sizes" | A 2-column table: `"$n×$n"` with Goal 256 / 2048 / 2048 / 2048 / 2048 / 2048, plus `htp_board_sizes_body` "Each size keeps its own game, best score and statistics." |
| `htp_undo` | "Undo" | `htp_undo_body` "Undo is unlimited and also works after game over. Undo brings back the same next tile, so repeating a move gives the same result. Undos are counted, and games with undo don't count toward Best without undo." |
| `htp_keys` | "Keyboard and controllers" | A 2-column table of D-053. Each row is a label plus one translatable value string: `htp_key_move` "Move" → `htp_key_move_keys` "Arrow keys · W A S D · H J K L · D-pad"; `undo` → `htp_key_undo_keys` "U · Ctrl+Z · Backspace"; `new_game` → `htp_key_new_game_keys` "N · R (asks first while a game is in progress)". Translators localize only the key *names* ("Arrow keys", "Backspace", "D-pad"); the letters W A S D, H J K L, U, Z, N and R stay Latin. The glossary (D-105) records this |
| `htp_accessibility` | "TalkBack" | `htp_accessibility_body` "With TalkBack on, a direction pad appears below the board, and the board offers Move, Undo and New game in the TalkBack actions menu." |

### 6.11 About (`AboutKey`, `about/AboutScreen.kt`)

- A `TopAppBar` titled `about` = "About". The body is a `LazyColumn` of at most 640 dp.
- **Header:** the app icon at 72 dp, then `app_name` (GSF title instance at 40 sp, `titleColor`), then `about_version` = "Version %1$s (%2$s)" → "Version 3.0.0 (30000)" (`bodyMedium`).
  - Compose's `painterResource` cannot load an `<adaptive-icon>` XML, so the icon is composed from its layers: `Box(Modifier.size(72.dp).clip(CircleShape).background(GameColors.IconBackground /* #E6A11A */)) { Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize()) }`.
  - The foreground's 108-unit viewport puts the glyph band (56/108 of the side) in the centre, so it is 37 dp wide at 72 dp.
- **Section `about_whats_new` "What's new":** for each versionCode in `Changelog` (descending), a `titleSmall` "3.0.0", then the entries as bullet rows (`bodyMedium`, "•" + 8 dp).
- **Row `about_source_code` "Source code":** a link row as in §6.8 (`ListItem(…) { headline }` with `onClickLabel` = `a11y_action_open`):
  - supporting text `about_source_code_url` = "github.com/clayboicardi/2048-android-modern-refresh" (`translatable="false"`); trailing `ic_open_in_new`;
  - it opens `ACTION_VIEW`. If `ActivityNotFoundException` is thrown, it shows the snackbar `link_open_failed` = "No app can open this link" (D-009).
- **Section `about_privacy` "Privacy":** `privacy_text` = D-123's text verbatim: "This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup."
- **Row `about_licenses` "Open-source licenses":** a link row (`onClickLabel` = `a11y_action_open`), trailing `ic_chevron_right`; pushes `LicensesKey`.
- **Section `about_credits` "Credits":** rows from `res/raw/legacy_history.json` (`credits[]`: `name`, `role`), as static English data (D-124). Expected rows:
  - "Gabriele Cirulli — original game";
  - "Paul Sarbinowski (uberspot) — Android port";
  - one row "Contributors" listing the deduplicated legacy contributors;
  - "clayboicardi — native rewrite".
- **Section `about_legacy_history` "Legacy history":** the 9 releases of `changelog_master.xml` (research/01-legacy-android-shell.md §5.2), each a `titleSmall` "2.2 (25)" plus `bodyMedium` entries. English, not translated (D-124).

### 6.12 Licenses (`LicensesKey`, `about/LicensesScreen.kt`)

- A `TopAppBar` titled `licenses` = "Open-source licenses". The body is a `LazyColumn` of at most 640 dp, grouped by `licenseId` from `res/raw/third_party_licenses.json` (D-122), in the order MIT, OFL-1.1, Apache-2.0.
- **Group header:** `license_mit_name` "MIT License", `license_ofl_name` "SIL Open Font License 1.1", `license_apache_name` "Apache License 2.0" (`titleSmallEmphasized`, `primary`).
- **Entry rows:** `ListItem(supportingContent = { copyright on line 1, groupOrPath on line 2 (bodySmall) }) { Text(name) }` (§7.9).
- **After each group:** a toggle row, `ListItem(onClick = …, trailingContent = { icon }) { Text(label) }` with `license_show_text` "Show license text" / `license_hide_text` "Hide license text" and `ic_expand_more` / `ic_expand_less`. It expands a `surfaceContainerLow` box (`shapes.medium`, 16 dp padding) with the raw text (`license_mit.txt`, `ofl_google_sans_flex.txt` or `license_apache_2_0.txt`) in `bodySmall` Monospace.
- **Expansion:** under Full motion it uses `AnimatedVisibility(expandVertically(GameMotion.overlaySpatial()))`; otherwise it is instant.

### 6.13 What's new sheet (D-009; `about/WhatsNewSheet.kt`)

- A `GameModalSheet` with:
  - title `whats_new_title` = "What's new" (`titleLarge`);
  - subtitle `whats_new_version` = "Version %1$s" (`bodyMedium`, `onSurfaceVariant`);
  - bullet rows for each versionCode > `lastSeenVersionCode`;
  - a full-width `FilledTonalButton(got_it)` at the bottom, with 24 dp margins.
- It never blocks the game: dismissible by drag, Back, scrim tap or "Got it".
- **v1.0 entries** (`Changelog.kt`, versionCode 30000):
  - `whatsnew_30000_1` "Rebuilt from scratch as a fast native app";
  - `whatsnew_30000_2` "Board sizes from 3×3 to 8×8, each with its own best score";
  - `whatsnew_30000_3` "Unlimited undo, even after game over";
  - `whatsnew_30000_4` "Light, dark, Neon Arcade and high-contrast themes";
  - `whatsnew_30000_5` "Statistics, keyboard controls and TalkBack support".
- The sheet cannot appear in v1.0, because versionCode 30000 is the first for this appId (D-009 note). It is verified by `WhatsNewTest` and the Required golden 44 (`sheet_whatsnew_classic-light`, §10.4, D-204).

### 6.14 Empty and loading states

| Where | Empty / loading behavior |
|---|---|
| App start | The splash stays up until settings, records and the current game have loaded, capped at 1000 ms (D-112). There is no in-app loading indicator anywhere |
| Game, after the cap | Empty-cells board described by `a11y_board_loading`; "–" in the chips; controls disabled (§6.1). Tiles appear `Static` |
| Game, fresh | Two tiles, Undo disabled |
| Board-size sheet | "No best score yet" on sizes with best 0 |
| Statistics | Empty state per tab (§6.9) |
| About → What's new | Always has the 30000 entry |
| Licenses | Always populated; `LicensesCoverageTest` guards it (D-122) |
| Corrupt data | Defaults are shown silently (D-045). No error UI exists |

---

## 7. Components (`ui/components/`)

### 7.1 Inventory

File names are relative to `ui/components/`; files in other packages are given from the `com.clayboicardi.game2048` root.

| Component | File | M3 base | Notes |
|---|---|---|---|
| `ScoreChip` | `ScoreCard.kt` (`@Composable fun ScoreChip`); its motion parts are `game/header/ScoreOdometer.kt`, `game/header/ScoreFloats.kt` and `game/header/NewBestBadge.kt`, and its sparkles are drawn by `ContentEffectsCanvas` (D-187) | `Surface` | §7.2 |
| `SizeChip` | `SizeChip.kt` | `AssistChip` (non-deprecated overload) | §7.3 |
| `GameButton*` wrappers (Undo, NewGame, More, card buttons) | `AppButtons.kt` | `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`, `FilledTonalIconButton`, `OutlinedIconButton`, `IconButton` with `ButtonDefaults.shapes()` / `IconButtonDefaults.shapes()` | §7.3 |
| `GameModalSheet` | `GameModalSheet.kt` | `ModalBottomSheet` | **The only main-source file with an opt-in** (§7.4, D-181) |
| `ChoiceDialog`, `ConfirmDialog` | `Dialogs.kt` | `AlertDialog` | §7.5 |
| `GameSnackbarHost` | `GameSnackbar.kt` | `SnackbarHost` | §7.6 |
| `Modifier.focusRing(shape)` | `FocusRing.kt` | — | §7.7 |
| `MiniTile`, `MiniBoardGlyph`, `PaletteSwatch` | `MiniTile.kt` | `Canvas` | §7.8 |
| `HintCard` | `HintCard.kt` | `Card` | §6.2 |
| `DirectionPad` | `game/input/DirectionPad.kt` | `FilledTonalIconButton` | §6.6 |
| `StatusLine` | `game/StatusLine.kt` | `Text` | §6.6 |
| `SectionHeader`, `SettingsSwitchRow`, `SettingsChoiceRow`, `SettingsLinkRow` | `SettingRows.kt` | `ListItem`, `Switch` | §6.8 |
| `OverlayCard` (win/over container, Regular and Compact layouts) + scrim clipped to the board shape | `game/overlay/BoardScrim.kt`; `WinBadge` in `game/overlay/WinCard.kt` | `ElevatedCard` | §6.3, §6.4 |

### 7.2 `ScoreChip` and the "+N" float

- **Container:** `Surface(color = surfaceContainerHigh, shape = shapes.large)` with `Modifier.widthIn(min = 88.dp).heightIn(min = 64.dp)`, padding 12 dp horizontal × 8 dp vertical. It is a `Column` centered horizontally.
- **Label:** `score_label` "Score" / `best_label` "Best" / `new_best_label` "New best", upper-cased with the locale (§3.7), `labelMediumEmphasized`, `onSurfaceVariant`.
- **Value** (`ScoreValue(value, slotWidth)`): GSF score instance (§3.2), `maxLines = 1`, `onSurface`. `Text(autoSize = …)` is **not** used for the value.
  - **Size.** `ScoreValue` measures `Long.toString(value)` with `rememberTextMeasurer` in the GSF score instance at 28 sp, once per (digit count, `slotWidth`, `fontScale`). It picks the largest size in 14..28 sp, in 1 sp steps, whose width ≤ `slotWidth`; `tnum` gives every digit the same advance, so the width depends only on the digit count.
  - **One size for every digit.** Under Full motion it is an odometer: one `AnimatedContent` per digit position (`game/header/ScoreOdometer.kt`), each digit at that one size, with `slideInVertically { it } + fadeIn(fastEffects)` replacing `slideOutVertically { -it } + fadeOut(fastEffects)` using the slide spring, and no stagger (D-064). Under Reduced and Off it is one `Text` at that size, and it changes instantly.
  - **Below 14 sp** (very long values; an 8×8 game can pass 9 digits) the value stays at 14 sp and the chip widens to its slot.
- **Width:** the chip widens with its value up to its slot.
- **"+N" float** (Score chip only; `game/header/ScoreFloats.kt`):
  - Text is `"+" + scoreDelta.toString()` in the GSF "+N" instance. It is drawn in an overlay `Box` anchored to the value baseline and is **not** clipped by the chip.
  - Motion: `translationY` 0 → −28 dp with `tween(650, EmphasizedDecelerate)`; alpha holds for 250 ms, then fades with `tween(400)`; scale 0.8 → 1 with `popSpatial`. At most 3 are alive (D-062, D-064). Reduced: fades in place over 400 ms. Off: none.
  - Color: `primary` when `maxMergedExp < 9`. When `maxMergedExp ≥ 9` it is a pill: `palette.fill(maxMergedExp)` background, `palette.ink(maxMergedExp)` text, `CircleShape`, padding 6 × 2 dp. Contrast ≥ 6.07 in every palette; tinted text would be about 1.5:1 in Classic light (D-187).
- **NEW BEST style:**
  - container `tertiaryContainer`, label `new_best_label`, label and value `onTertiaryContainer`, all crossfaded with `defaultEffects`;
  - behind the label, the 10-point star badge (`tertiary`, 20 dp; `game/header/NewBestBadge.kt`) and 8 sparkles drawn by `ContentEffectsCanvas` (D-064, D-187). The star and sparkles are one-shot;
  - the color state remains for the rest of the game (§6.1).
- **Semantics:** `val cd = stringResource(R.string.a11y_chip_value, label, valueLatin)`, then `Modifier.semantics(mergeDescendants = true) { contentDescription = cd }`, with `a11y_chip_value` = "%1$s %2$s" (label in sentence case, value in Latin digits), never string concatenation. There is no live region; the status line announces (D-081).

### 7.3 Buttons and chips

| Button | Type | Height | Icon | Label | Enabled |
|---|---|---|---|---|---|
| New game (controls bar) | `OutlinedButton` (border `outline`, content `onSurface`) | `heightIn(min = 56.dp)` | `ic_restart` (`ButtonDefaults.iconSizeFor(56.dp)`) | `new_game` | always (disabled only while Loading) |
| Undo | `FilledTonalButton` | 56 | `ic_undo` | `undo` | `moveCount > 0` |
| More | `FilledTonalIconButton` | `IconButtonDefaults.mediumContainerSize()` | `ic_more_vert` | — (`contentDescription = more_options`) | always |
| Keep going (win card, Regular and Compact) | `Button` | 56 | — | `keep_going` | composed enabled; interactive after the D-008 delay (D-200) |
| Win card New game (Regular) | `FilledTonalButton` | 56 | — | `new_game` | composed enabled; interactive after the D-008 delay (D-200) |
| Win card Undo (Regular) | `TextButton` | 56 | — | `undo` | same |
| Win card New game (Compact) | `FilledTonalIconButton` | `IconButtonDefaults.mediumContainerSize()` | `ic_restart` | — (`contentDescription = new_game`) | same |
| Win card Undo (Compact) | `IconButton` | 56 | `ic_undo` | — (`contentDescription = undo`) | same |
| Win card Share (both layouts) | `IconButton`, card top-end | 48 | `ic_share` | — (`contentDescription = share`) | same |
| Game-over New game (Regular and Compact) | `Button` | 56 | — | `new_game` | same |
| Game-over Undo (Regular) | `FilledTonalButton` | 56 | — | `undo` | same |
| Game-over Share (Regular) | `OutlinedButton` | 56 | `ic_share` | `share` | same |
| Game-over Undo (Compact) | `FilledTonalIconButton` | 56 | `ic_undo` | — (`contentDescription = undo`) | same |
| Game-over Share (Compact) | `OutlinedIconButton` | 56 | `ic_share` | — (`contentDescription = share`) | same |

- Every button uses `shapes = ButtonDefaults.shapes()` (the Expressive press morph; `IconButtonDefaults.shapes()` for icon buttons), `contentPadding = ButtonDefaults.contentPaddingFor(56.dp)` and labels with `maxLines = 2`.
- **Card buttons** are composed with `enabled = true`, so they never flash from disabled to enabled colours. Until `OverlayController.interactive` flips, clicks are ignored, they carry `disabled()` semantics and they cannot take focus (§6.3, D-200).
- **`SizeChip`:** `AssistChip(onClick, label = { Text("$n×$n") }, leadingIcon = ic_grid_view 18 dp, trailingIcon = ic_expand_more 18 dp)`, wrapped in `minimumInteractiveComponentSize()`.
  - Semantics: `contentDescription = board_size_cd` = "Board size %1$s", `onClickLabel = board_size_change` = "Change board size".
- **Not used in v1.0:**
  - `ButtonGroup`, `ToggleButton` and `HorizontalFloatingToolbar`. They are public in 1.5.0-beta02, but Game has only three controls, and settings use dialogs so they survive `fontScale` 2.0 and long translations. The controls bar uses three individual buttons (weights 1:1 plus More).
  - `SegmentedButton`, which has a deprecated overload.

### 7.4 Sheets (`GameModalSheet`, the single opt-in)

- `ModalBottomSheet` and `rememberBottomSheetState` carry `@ExperimentalMaterial3Api` in material3 1.5.0-beta02 (and in 1.4.0). `rememberModalBottomSheetState` is also `@Deprecated` ("Use rememberBottomSheetState with Hidden initial value"). All verified 2026-10-28 from class files.
- D-007 and D-009 require a `ModalBottomSheet`. D-181 (amending D-143) allows the opt-in in this one file only; `check_optin_allowlist` (`scripts/check-metadata.sh`) enforces it, and the deprecated `rememberModalBottomSheetState` is never used:

```kotlin
// ui/components/GameModalSheet.kt — the ONLY @OptIn(ExperimentalMaterial3Api::class) in the codebase (D-181)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModalSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),   // == skipPartiallyExpanded
    )
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = state, content = content)
}
```

- The container is M3 default `surfaceContainerLow`, with a 28 dp top radius, the drag handle and `BottomSheetDefaults.modalWindowInsets`.
- Content pads `navigationBars` at the bottom.
- `BoardSizeSheet` and `WhatsNewSheet` call only `GameModalSheet`.

### 7.5 Dialogs

- **`ConfirmDialog(title, body?, confirmText, onConfirm, onDismiss)`** is an `AlertDialog` (android overload) with `TextButton`s. Uses: new game (§6.5) and reset statistics (§6.9).
- **`ChoiceDialog<T>(title, options: List<Choice<T>>, selected, onSelect, onDismiss)`:**
  - the body is a `Column(Modifier.selectableGroup())` of radio rows, each `ListItem(selected, onClick) { label }` (§7.9) at least 56 dp tall, with role `RadioButton`, a leading `RadioButton(selected, onClick = null)`, the label (`bodyLarge`) and an optional supporting line (`bodyMedium`);
  - the Palette dialog adds a `PaletteSwatch` under each label;
  - selecting an option calls `onSelect`, then `onDismiss`.
- **Keyboard and focus:** dialogs take focus. Move keys are ignored while one is open (D-054).
- **Motion:** dialog windows keep their platform enter and exit fades at every in-app motion level (D-184).

### 7.6 Snackbars (undo hints and failures)

`GameSnackbarHost` is a single `SnackbarHostState` per screen, with M3 `Snackbar` defaults. Rules:
- Duration is `SnackbarDuration.Short`, with no action button.
- At most one is visible. A new message replaces the current one (`currentSnackbarData?.dismiss()` before `showSnackbar`).
- **Placement on Game** (D-186; §5.4). A snackbar never covers the controls bar.
  - STACKED: in the gap between the board and the controls bar when that gap is at least the snackbar's measured height + 16 dp. Otherwise it overlays the board, bottom-centred, with width board − 16 dp, 8 dp above the board's bottom edge.
  - THREE_COLUMN: at the bottom of the actions rail.
  - SIDE_PANEL: at the bottom of the panel, aligned with the board's bottom edge.
  - SCROLL: over the board, as STACKED's second case (§5.3, D-188).
  - Any accepted move or undo dismisses it.
- `SnackbarHost` keeps its M3 enter and exit fades at every in-app motion level (D-184).

| Key | Text | Trigger | Screen |
|---|---|---|---|
| `undo_hint_no_undo_best` | "Undo used. This game no longer counts for Best without undo." | A game's `undoCount` goes from 0 to 1, and only while `RecordsMath.aggregate(records).totalUndos == 0`, that is, until the first finalized game that used undo. No persisted key (D-186) | Game |
| `nothing_to_undo` | "Nothing to undo" | U, Ctrl+Z or Backspace when `moveCount == 0` (the button is disabled, so touch cannot trigger it). At most 1 per 2 s (D-186) | Game |
| `link_open_failed` | "No app can open this link" | `ActivityNotFoundException` from the source link or the language page | About, Settings |
| `stats_reset_done` | "Statistics reset" | After the second reset confirmation | Statistics |

TalkBack announces snackbars politely by default. No snackbar is used for the win, game-over or new-best moments; those have their own UI.

### 7.7 Focus ring (D-053, D-084)

- `Modifier.focusRing(shape: Shape)` draws, while focused, a 2 dp `primary` stroke **outside** the element. There is a 2 dp gap, and the outset shape's corner radius is the element's radius + 2 + 1 dp.
- The ring therefore sits on the background (`primary` on `background` ≥ 6.28:1, §2.9) and never on the board or a tile.
- Applied by every `ui/components` wrapper and to the board (`shape` = board shape). M3 state layers alone are below 3:1.
- The board requests focus when Game appears (D-053).

### 7.8 `MiniTile`, `MiniBoardGlyph`, `PaletteSwatch`

- **`MiniTile(exp, size)`** draws one tile with the active palette:
  - fill, ink, radius rule (§4.4), the §3.3 label rule with `t = size`, and the HC outline;
  - no glow and no motion;
  - semantics `contentDescription` = the full decimal value.
- **`MiniBoardGlyph(n, size)`** draws an N×N grid with its own geometry, not §4.2's: `gapPx = max(1, round(0.10 × sizePx / n))`, with no dp floor, and cells of `(sizePx − (n + 1) · gapPx) / n`. The cells are drawn in `palette.fill(1)` on the board colour (not `palette.empty`), so they stay legible at 32 dp. It is decorative (`contentDescription = null`).
  - Test: `ui/components/MiniTileTest.glyphCellsLegibleAt32dp` asserts that for N = 8 at 420 dpi every cell is ≥ 9 px.
- **`PaletteSwatch(palette, darkTheme)`** is a row of 4 `MiniTile`s (2, 8, 128, 2048) at 24 dp, 4 dp apart, on that palette's board color with 4 dp padding. In the Palette dialog each option previews its own palette in the current light or dark mode.

### 7.9 Material 3 API allow and deny list (verified 2026-10-28 from class files)

| Status | APIs |
|---|---|
| **Use** (public, no opt-in, not deprecated) | `MaterialExpressiveTheme`, `MotionScheme.expressive()` and the `MotionScheme` interface (`gameMotionScheme(level)`, D-184), `lightColorScheme`/`darkColorScheme` (48-parameter overloads), `dynamicLightColorScheme`/`dynamicDarkColorScheme`, `Typography.*Emphasized`, `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`, `ButtonDefaults.shapes()`/`MediumContainerHeight`/`contentPaddingFor`/`textStyleFor`/`iconSizeFor`, `FilledTonalIconButton`, `OutlinedIconButton` and `IconButton` (non-deprecated overloads), `IconButtonDefaults.shapes()`/`mediumContainerSize()`, `ElevatedCard`, `Card`, `AssistChip`, `ListItem` through its headline-as-trailing-lambda overloads only: `ListItem(modifier, …, supportingContent, trailingContent) { headline }`, `ListItem(checked, onCheckedChange) { … }`, `ListItem(selected, onClick) { … }` and `ListItem(onClick) { … }`, `Switch`, `RadioButton`, `HorizontalDivider`, `AlertDialog` (android), `TopAppBar`, `Scaffold`, `SnackbarHost`, `PrimaryScrollableTabRow`, `Tab`, `DropdownMenu` + classic `DropdownMenuItem`, `Surface`, `Text(autoSize = …)` + foundation `TextAutoSize.StepBased`, ui `Modifier.dropShadow`/`innerShadow`/`preferredFrameRate` |
| **Isolated opt-in** (one file) | `ModalBottomSheet`, `rememberBottomSheetState`, `SheetValue` (`ExperimentalMaterial3Api`) → `GameModalSheet.kt` only (D-181) |
| **Never use** (opt-in) | `TooltipBox`, `PlainTooltip`, `rememberTooltipState` (`ExperimentalMaterial3Api`); `MaterialShapes`, `LoadingIndicator`, `DropdownMenuItem` checked/selected overloads (`ExperimentalMaterial3ExpressiveApi`); foundation `FlowRow`/`FlowColumn` experimental overloads; Roborazzi `captureScreenRoboImage` and `RoborazziOptions(taskType = …)` (`ExperimentalRoborazziApi`; Roborazzi opt-ins are allowed only in `testing/Accessibility.kt` for ATF, D-174) |
| **Never use** (deprecated, fatal under D-143) | `rememberModalBottomSheetState`, `TabRow`, `ScrollableTabRow`, old `PrimaryScrollableTabRow`/`SecondaryScrollableTabRow` overloads, old `SegmentedButton`, `BasicText` old overloads, and the deprecated overloads of `IconButton`, `FilterChip`, `AssistChip`, `ListItem`, `TopAppBar`, `CenterAlignedTopAppBar`, `Text` and `AlertDialog`. If a call resolves to one, follow its `ReplaceWith` |

---

## 8. Iconography and identity assets

### 8.1 Material Symbols (D-079)

- **Source:** Material Symbols **Rounded**, weight 400, fill 0, grade 0, 24 px, Apache-2.0.
- **Download URL:** `https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<symbol>/materialsymbolsrounded/<symbol>_24px.xml`. Every symbol below returned HTTP 200 on 2026-10-28.
- **Pin:** record the repository commit, from `git ls-remote https://github.com/google/material-design-icons refs/heads/master`, in an XML comment on line 2 of every file: `<!-- Material Symbols Rounded "<symbol>", Apache-2.0, google/material-design-icons@<sha> -->`.
- **Normalization** (each file under `app/src/main/res/drawable/`):
  - keep `width`/`height` 24 dp and viewport 960;
  - **delete `android:tint`** (it references `?attr/colorControlNormal`; Compose's `Icon` tints);
  - set every path's `android:fillColor="#FF000000"`;
  - keep or remove `android:autoMirrored` as listed.
- **No dependency:** there is no `material-icons-*` dependency (frozen at 1.7.8; D-016, D-079).

| File | Symbol | `autoMirrored` | Used in |
|---|---|---|---|
| `ic_undo.xml` | `undo` | true (as downloaded) | Undo button, pad, compact cards |
| `ic_restart.xml` | `restart_alt` | — (as downloaded) | New game (controls bar, compact cards) |
| `ic_more_vert.xml` | `more_vert` | — | More |
| `ic_arrow_back.xml` | `arrow_back` | true | Top-app-bar navigation |
| `ic_bar_chart.xml` | `bar_chart` | — | Menu → Statistics |
| `ic_settings.xml` | `settings` | — | Menu → Settings |
| `ic_help.xml` | `help` | true | Menu → How to play |
| `ic_info.xml` | `info` | — | Menu → About |
| `ic_grid_view.xml` | `grid_view` | — | Size chip |
| `ic_expand_more.xml` / `ic_expand_less.xml` | `expand_more` / `expand_less` | — | Size chip, licenses |
| `ic_check.xml` | `check` | — | Size sheet |
| `ic_share.xml` | `share` | — | Share (win card, game-over card) |
| `ic_delete.xml` | `delete` | — | Reset statistics |
| `ic_open_in_new.xml` | `open_in_new` | true | Source link, App language |
| `ic_chevron_right.xml` | `chevron_right` | true | About → Licenses |
| `ic_pad_up.xml` / `ic_pad_down.xml` | `arrow_upward` / `arrow_downward` | — | Pad, How to play |
| `ic_pad_left.xml` / `ic_pad_right.xml` | `arrow_back` / `arrow_forward` | **removed** (D-079) | Pad, merge illustration |

- **Content descriptions:**
  - Icons inside labeled buttons have `contentDescription = null`.
  - Icon-only buttons carry the string named in §6.
- **Sizes:**
  - 24 dp default;
  - 18 dp in chips;
  - `ButtonDefaults.iconSizeFor(56.dp)` in 56 dp buttons;
  - 32 dp in pad buttons.

### 8.2 Launcher icon (D-111)

Geometry (research/01 §5.3, research/07-critique.md G4):
- The legacy glyph band (four paths: "2", "0", "4", "8") spans 39.687 × 13.229 units of a 52.916-unit viewport, centered at (26.458, 26.458).
- `scale 1.411` + `translate 16.667` in a 108-unit viewport maps it to a **56 × 18.67 dp** band centered at (54, 54). Its 59.0 dp diagonal fits the 66 dp safe zone.
- Glyphs are `#3A2900` on `#E6A11A`: **6.33:1**. These are the Classic 2048 tile colors (§2.3).

| File | Content |
|---|---|
| `res/values/ic_launcher_background.xml` | `<color name="ic_launcher_background">#E6A11A</color>` |
| `res/drawable/ic_launcher_foreground.xml` | 108×108 dp vector, viewport 108; `<group scaleX="1.411" scaleY="1.411" translateX="16.667" translateY="16.667">` holding the 4 legacy `pathData` strings copied verbatim from `res/drawable/ic_2048.xml` in M0, each `fillColor="#3A2900"`, with no stroke attributes |
| `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | identical adaptive icons (below) |
| `res/mipmap-anydpi/ic_launcher.xml` (API 24–25) | 48×48 dp vector, viewport 108: rounded square `M24,6h60a18,18 0 0 1 18,18v60a18,18 0 0 1 -18,18h-60a18,18 0 0 1 -18,-18v-60a18,18 0 0 1 18,-18z` in `#E6A11A`, plus the same glyph group |
| `res/mipmap-anydpi/ic_launcher_round.xml` (API 24–25) | same, with the circle `M54,6a48,48 0 1 1 0,96a48,48 0 1 1 0,-96z` |

```xml
<!-- res/mipmap-anydpi-v26/ic_launcher.xml (and ic_launcher_round.xml) -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

- The monochrome layer reuses the foreground. Themed icons use only its alpha, which gives the glyph silhouette.
- The icon is built in **M0** (D-111), from `ic_2048.xml` before that file is deleted.
- Debug and release share the icon. The debug label "2048 Debug" (D-110) distinguishes them.

### 8.3 Splash (D-112)

```xml
<!-- res/values/themes.xml -->
<style name="Theme.Game2048.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#FAF8EF</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="windowSplashScreenIconBackgroundColor">#E6A11A</item>
    <item name="postSplashScreenTheme">@style/Theme.Game2048</item>
</style>
<style name="Theme.Game2048" parent="android:Theme.Material.Light.NoActionBar">
    <item name="android:windowBackground">#FAF8EF</item>
</style>
<!-- res/values-night/themes.xml: same names; splash background #1B1916; Theme.Game2048 parent
     android:Theme.Material.NoActionBar with windowBackground #1B1916 -->
```

- **Geometry:** with an icon background, the splash icon is 240 dp, and its content must fit a 160 dp circle. The glyph band maps to 124 × 41 dp, and its 131 dp diagonal is less than 160 dp. The `#E6A11A` disc carries the identity on both splash backgrounds.
- **Static:** the splash follows only the system night mode. It ignores the in-app palette and theme, because it is drawn before any state loads. The first Compose frame then crossfades to the effective theme with no animation (Static).
- **Hold:** `installSplashScreen().setKeepOnScreenCondition { !ready }`, capped at 1000 ms (D-112). No exit animation is customized.

### 8.4 Store icon, 512 × 512 (`StoreAssetsTest`, D-115)

- **Rendering:** `Bitmap(512, 512, ARGB_8888)`, fill `#E6A11A`, then draw `ic_launcher_foreground` with `setBounds(-128, -128, 640, 640)`.
  - The 108-unit canvas is scaled so that its visible 72-unit centre fills 512 px, which matches what launchers show.
  - The glyph band becomes 398 × 133 px. No mask is applied; Play applies its own.
- **Output:** an opaque PNG at `fastlane/metadata/android/en-US/images/icon.png`, written by the test-only `store/OpaquePngWriter` (`java.util.zip`, bit depth 8, color type 2; D-190).

### 8.5 Feature graphic, 1024 × 500 (D-115)

- **Rendering:** composable `FeatureGraphic()` (`app/src/test/kotlin/com/clayboicardi/game2048/store/FeatureGraphic.kt`) at `w512dp-h250dp-xhdpi`. `StoreAssetsTest` captures it with `composeRule.onNodeWithTag("store:frame").captureToImage().asAndroidBitmap()` and writes it through `OpaquePngWriter.write(bitmap, background, file)` as an 8-bit PNG with color type 2 (D-190).
- **Layout** (dp; ×2 = px):

```
┌──────────────────────────────── 512 × 250, background #FAF8EF ────────────────────────────────┐
│ 32                                                                                            │
│   ┌──── Classic 4×4 board 186 ────┐   40   2048          ← GSF 850, 96 sp-equivalent dp,     │
│   │  2    4    8    16            │        (wordmark)       color #3A2900, ROND 0           │
│   │  32   64   128  256           │                                                         │
│   │  512  1024 2048  ·            │        [2][8][128][512][2048]  ← 5 MiniTiles 36 dp,     │
│   │  ·    2    ·    4             │                                   gap 6, Classic light  │
│   └───────────────────────────────┘                                                         │
│ 32                                                                                            │
└───────────────────────────────────────────────────────────────────────────────────────────────┘
```

- Board top-left is (32, 32). The right group starts at x = 258, is vertically centered (wordmark 96 + 16 + tiles 36), and is left-aligned.
- Wordmark and tiles are drawn at fontScale 1 with fixed dp sizes.
- The only text is the "2048" wordmark, because the graphic is shared by every listing locale (D-113).
- Board fixture `FIX_FEATURE`, exponents row-major: `1,2,3,4, 5,6,7,8, 9,10,11,0, 0,1,0,2`.

### 8.6 Store screenshots (D-115)

| File | Qualifier | Content |
|---|---|---|
| `phoneScreenshots/1.png` | `w360dp-h640dp-xxhdpi` (1080×1920) | `FIX_MID_4`, Classic light |
| `phoneScreenshots/2.png` | same | `FIX_WON_4`, Classic light, win card in its Compact layout (board 280 dp at `w360dp-h640dp`, §6.3a) |
| `phoneScreenshots/3.png` | same | `FIX_MID_4`, Neon dark |
| `phoneScreenshots/4.png` | same | Statistics, 4×4 tab, `FIX_RECORDS`, Classic light |
| `sevenInchScreenshots/1.png` | `w1067dp-h600dp-land-xhdpi` (2134×1200) | `FIX_MID_4`, Classic light, side panel |
| `tenInchScreenshots/1.png` | `w1280dp-h720dp-land-xhdpi` (2560×1440) | `FIX_MID_4`, Neon dark, side panel |

All six use Motion Off, dynamic color off and locale `en-US`. Each is captured from the `"store:frame"` node with `captureToImage()` and written by `OpaquePngWriter` (8-bit, color type 2; D-190).

---

## 9. Theme previews as token diffs

Each table lists only what changes between two variants. Anything not listed is identical.

### 9.1 Classic Refined light → dark

| Token | Light | Dark |
|---|---|---|
| background / board / empty | `#FAF8EF` / `#BBADA0` / `#CDC1B4` | `#1B1916` / `#2C2824` / `#3A342E` |
| Tile fills and inks | §2.3 | §2.4 (about 8 % lower L; supers become light fills with ink `#1B1916`) |
| Glow alphas (128–2048) | 0.19–0.44 outer, 0.05–0.11 inner | × 1.15 |
| `titleColor` | `#776E65` | `#D1C4BA` |
| Chrome scheme | `ClassicLightScheme` | `ClassicDarkScheme` (§2.8) |
| Splash background | `#FAF8EF` | `#1B1916` |
| System-bar icons | dark | light |

### 9.2 Classic Refined → Neon Arcade (same mode)

| Token | Classic | Neon |
|---|---|---|
| Tile ramp | warm cream → orange → gold → dark supers | indigo → violet → magenta → coral → amber → yellow-lime → pale supers (§2.5) |
| board / empty | `#BBADA0`/`#CDC1B4` (light), `#2C2824`/`#3A342E` (dark) | `#1A1730` / `#25213F` in both modes |
| background | `#FAF8EF` / `#1B1916` | `#F3F1FA` / `#0E0C1A` |
| Gap ratio r | 0.14 | 0.18 |
| Tile radius | `clamp(0.07·t, 2, 10) dp` | `clamp(0.22·t, 4, 34) dp` |
| Numerals | GSF wght 800, ROND 0 | GSF wght 900, ROND 100 |
| Title | wght 850, ROND 0 | wght 850, ROND 100 |
| Board effect | flat | inset well |
| Glow | gold `#F3D774` on 128–2048 only | own-fill glow on ≥ 128, including supers; glassy inner highlight on every tile |
| Chrome seed | `#8F7A66` | `#5B5BD6` |
| `titleColor` | `#776E65` / `#D1C4BA` | `#4B4185` / `#ECF33C` |

### 9.3 Any palette → high contrast

| Token | Normal | High contrast |
|---|---|---|
| `ink(exp)` | palette ink | black or white, whichever is higher (HC columns in §2.3–§2.5) |
| Tile outline | none | 1.5 dp `colorScheme.outline`, inset 0.75 dp |
| `TileSkin.id` | `<palette>-<mode>` | `<palette>-<mode>-hc` |
| Settings row | user-controlled | forced On and disabled while system contrast ≥ 0.5 (API 34+) |
| Everything else | — | unchanged |

### 9.4 Static chrome → dynamic chrome (API 31+, opt-in)

| Token | Static | Dynamic |
|---|---|---|
| 48 `ColorScheme` roles | §2.8 | `dynamic{Light,Dark}ColorScheme(context)` |
| `background`, `surface` | palette background | palette background (override applied after dynamic) |
| Tiles, board, empty, glows, title, HC ink | palette | palette (never dynamic) |
| HC outline, focus ring, "+N" (< 512), confetti accents, sparkles | static `outline` / `primary` / `tertiary` | dynamic `outline` / `primary` / `tertiary` |
| Splash, launcher, store assets | static | static |

---

## 10. Screenshot goldens

### 10.1 Naming grammar

Format: `<screen>[_<state>[-<N>x<N>]]_<palette>-<mode>[-hc][_<variant>]`. Layout goldens are the one exception: they are named `layout_<qualifier>`, and all of them use Classic light with `FIX_MID_4`. The pieces:
- `screen` ∈ {`game`, `settings`, `stats`, `howtoplay`, `about`, `licenses`, `sheet`, `dialog`, `layout`, `motion`}.
- `palette-mode` ∈ {`classic-light`, `classic-dark`, `neon-light`, `neon-dark`}.
- `variant` is any of:
  - a locale tag (`fa`, `de`, `en-XA`, `ar-XB`);
  - `fs200` for fontScale 2.0;
  - `dpad` for the TalkBack pad;
  - a Robolectric qualifier for layout goldens;
  - `tNNN` for filmstrip frames, in milliseconds.
- **Defaults are omitted:** locale `en`, fontScale 1.0, the default qualifier `RobolectricDeviceQualifiers.Pixel7` (w411dp-h914dp, 420 dpi).
- **Files:** `app/src/test/screenshots/<name>.png`, written by `captureGolden(name)` (D-147).

### 10.2 Capture conditions (D-147, plus this file)

- SDK 36, `@GraphicsMode(NATIVE)`, dynamic color off, bundled font, static fixtures (§10.3).
- **Motion:**
  - every golden except the filmstrip uses settings `motion = OFF`, so end states render deterministically (win badge fully morphed at 60°, no confetti);
  - the filmstrip uses `FULL` with `mainClock.autoAdvance = false`, advancing to each frame time measured from the move's origin on the 16 ms test-frame grid (`MOTION_SPEC.md` §0.1, §8.3).
- **Dark goldens** set `themeMode = DARK` **and** the qualifier `+night`, so platform-drawn surfaces match.
- `onboardingDone = true` everywhere except `game_hint-4x4_classic-light`. `lastSeenVersionCode = 30000` everywhere except the What's new golden.
- **Capture API:** in-window content is captured with `onRoot().captureRoboImage(...)`. Dialog and sheet windows are captured by node: `onNode(isDialog()).captureRoboImage(...)`, or by the sheet content test tag `"sheet:boardSize"` / `"sheet:whatsNew"`. **Never** use `captureScreenRoboImage`; it is `@ExperimentalRoborazziApi` (verified 2026-10-28) and would need an opt-in outside `testing/Accessibility.kt` (D-143, D-174).
- **ATF** (D-085) runs on every `game_*`, `layout_*`, `settings_*`, `stats_*`, `howtoplay_*`, `about_*`, `licenses_*`, `sheet_*` and `dialog_*` golden. Only `motion_*` is exempt (D-204).
- **Touch targets:** every screen-level screenshot test also runs `assertTouchTargets()` (`QUALITY.md` §1.12, D-205).
- **Inspection:** the agent Reads every new or changed PNG and lists it in the commit body (D-147, D-164).

### 10.3 Fixtures (`app/src/test/kotlin/com/clayboicardi/game2048/fixtures/GoldenFixtures.kt`)

Cells are exponents, row-major, with 0 = empty. Every game fixture is a `SavedGame` with `gameId = 1` and no `previousFinalization`; `FIX_RECORDS` has an empty `lastFinalizedGameId` (D-044). A test that needs other identities uses `copy(gameId = …)`.

| Fixture | N | Cells | Score / best / moves / undos / time | Status |
|---|---|---|---|---|
| `FIX_FRESH_4` | 4 | index 1 = 1, index 11 = 2, others 0 | 0 / 0 / 0 / 0 / 0 | PLAYING |
| `FIX_MID_4` | 4 | `1,2,3,4, 5,6,7,8, 9,10,1,2, 0,0,1,0` | 10220 / 24680 / 412 / 2 / 9:58 | PLAYING |
| `FIX_MID_3` | 3 | `1,2,3, 4,5,0, 6,7,1` | 1184 / 2100 / 96 / 0 / 1:40 | PLAYING |
| `FIX_DENSE_8` | 8 | `1,2,3,4,5,6,7,8, 16,15,14,13,12,11,10,9, 1,1,2,2,3,3,0,0, 4,5,6,7,8,9,10,11, 2,0,1,0,2,0,1,0, 12,11,10,9,8,7,6,5, 0,0,0,1,0,0,0,0, 3,2,1,0,0,1,2,3` | 1182372 / 1182372 / 23456 / 0 / 3:12:05 | KEEP_PLAYING (keepGoingAtMove 9000) |
| `FIX_RAMP_5` | 5 | `1..17` in reading order, then 8 × 0 | 2456788 / 2456788 / 41000 / 0 / 6:02:00 | KEEP_PLAYING |
| `FIX_WON_4` | 4 | `11,1,2,0, 3,0,0,0, 1,0,0,0, 0,0,0,2` | 20124 / 24680 / 980 / 0 / 21:15 | WON |
| `FIX_OVER_4` | 4 | `1,2,1,2, 2,3,2,1, 4,5,6,7, 1,2,3,1` (no empty cell, no equal neighbour) | 3012 / 24680 / 302 / 0 / 6:41 | OVER |
| `FIX_FILM` | 4 | `1,1,0,2, then 12 × 0`, `rngState = 0x2048`, then move LEFT (row → `2,2,0,0`, +4, then one spawn from the SplitMix64 draws of D-034) | 0 → 4 | PLAYING |
| `FIX_FEATURE` | 4 | `1,2,3,4, 5,6,7,8, 9,10,11,0, 0,1,0,2` | 20480 / 24680 | KEEP_PLAYING |
| `FIX_RECORDS` | — | size "4": `bestScore 24680`, `bestScoreNoUndo 18212`, `bestTileExp 11`, `gamesPlayed 37`, `gamesWon 9`, `gamesAbandoned 6`, `totalScore 412560`, `totalMoves 23804`, `totalTimeMs 41234000`, `totalUndos 58`, streaks 1/3; size "3": `bestScore 2100`, `bestTileExp 8`, `gamesPlayed 5`, `gamesWon 2`; other sizes empty | — | — |

Note on `FIX_OVER_4`: no adjacent pair is equal and the board is full, which is D-037's game-over condition.

### 10.4 Golden list

The golden matrix is 45 PNGs (D-204): D-147's set plus four rows for the board-size sheet, the new-game dialog, the What's new sheet and the first-launch hint (rows 42–45). Every row is Required, compared by a test and Read.

**Milestones** (D-204): M3 part 1 records rows 1, 3, 37–41 and 43; M3 part 2 rows 13, 15 and 42; M4 records or re-records rows 1–22 and 27–45 and Reads them; M5 records rows 23–26.

| # | Name | Screen / state | Palette-mode | Qualifiers / locale / scale | Fixture | Status |
|---|---|---|---|---|---|---|
| 1 | `game_new-4x4_classic-light` | Game, fresh | classic-light | Pixel7 | `FIX_FRESH_4` | Required |
| 2 | `game_new-4x4_classic-dark` | Game, fresh | classic-dark | Pixel7 +night | `FIX_FRESH_4` | Required |
| 3 | `game_mid-4x4_classic-light` | Game, playing | classic-light | Pixel7 | `FIX_MID_4` | Required |
| 4 | `game_mid-4x4_classic-dark` | Game, playing | classic-dark | Pixel7 +night | `FIX_MID_4` | Required |
| 5 | `game_mid-3x3_classic-light` | Game, 3×3 | classic-light | Pixel7 | `FIX_MID_3` | Required |
| 6 | `game_dense-8x8_classic-light` | Game, 8×8 with compact labels | classic-light | Pixel7 | `FIX_DENSE_8` | Required |
| 7 | `game_dense-8x8_classic-dark` | Game, 8×8 | classic-dark | Pixel7 +night | `FIX_DENSE_8` | Required |
| 8 | `game_ramp-5x5_classic-light` | Game, full ramp + super tiles | classic-light | Pixel7 | `FIX_RAMP_5` | Required ("super tiles light") |
| 9 | `game_ramp-5x5_classic-dark` | same | classic-dark | Pixel7 +night | `FIX_RAMP_5` | Required ("super tiles dark") |
| 10 | `game_ramp-5x5_neon-light` | same | neon-light | Pixel7 | `FIX_RAMP_5` | Required ("Neon light") |
| 11 | `game_ramp-5x5_neon-dark` | same | neon-dark | Pixel7 +night | `FIX_RAMP_5` | Required ("Neon dark") |
| 12 | `game_ramp-5x5_classic-light-hc` | same, high contrast | classic-light-hc | Pixel7 | `FIX_RAMP_5` | Required ("high contrast") |
| 13 | `game_won-4x4_classic-light` | Win card | classic-light | Pixel7 | `FIX_WON_4` | Required |
| 14 | `game_won-4x4_classic-dark` | Win card | classic-dark | Pixel7 +night | `FIX_WON_4` | Required |
| 15 | `game_over-4x4_classic-light` | Game-over card | classic-light | Pixel7 | `FIX_OVER_4` | Required |
| 16 | `game_over-4x4_classic-dark` | Game-over card | classic-dark | Pixel7 +night | `FIX_OVER_4` | Required |
| 17 | `settings_classic-light` | Settings, top of list | classic-light | Pixel7 | defaults | Required |
| 18 | `settings_classic-dark` | Settings | classic-dark | Pixel7 +night | `themeMode = DARK` | Required |
| 19 | `stats_classic-light` | Statistics, 4×4 tab | classic-light | Pixel7 | `FIX_RECORDS` | Required |
| 20 | `howtoplay_classic-light` | How to play, top | classic-light | Pixel7 | — | Required |
| 21 | `about_classic-light` | About, top | classic-light | Pixel7 | — | Required |
| 22 | `licenses_classic-light` | Licenses, MIT group expanded | classic-light | Pixel7 | — | Required |
| 23 | `game_mid-4x4_classic-light_fa` | Game, RTL chrome + LTR board | classic-light | `fa` + Pixel7 | `FIX_MID_4` | Required |
| 24 | `settings_classic-light_de` | Settings, long strings | classic-light | `de` + Pixel7 | defaults | Required |
| 25 | `settings_classic-light_en-XA` | Settings, pseudo-expansion | classic-light | `en-rXA` + Pixel7 | defaults | Required (dropped with D-106 if the DSL fails) |
| 26 | `game_mid-4x4_classic-light_ar-XB` | Game, pseudo-bidi | classic-light | `ar-rXB` + Pixel7 | `FIX_MID_4` | Required (same caveat) |
| 27 | `game_mid-4x4_classic-light_fs200` | Game, fontScale 2.0 | classic-light | Pixel7, `fontScale = 2.0` | `FIX_MID_4` | Required |
| 28 | `settings_classic-light_fs200` | Settings, fontScale 2.0 | classic-light | Pixel7, `fontScale = 2.0` | defaults | Required |
| 29 | `layout_w360dp-h800dp` | Game, STACKED | classic-light | `w360dp-h800dp` | `FIX_MID_4` | Required |
| 30 | `layout_w412dp-h915dp` | Game, STACKED | classic-light | `w412dp-h915dp` | `FIX_MID_4` | Required |
| 31 | `layout_w915dp-h412dp-land` | Game, THREE_COLUMN | classic-light | `w915dp-h412dp-land` | `FIX_MID_4` | Required |
| 32 | `layout_w673dp-h841dp` | Game, STACKED (medium) | classic-light | `w673dp-h841dp` | `FIX_MID_4` | Required |
| 33 | `layout_w841dp-h673dp-land` | Game, SIDE_PANEL (280) | classic-light | `w841dp-h673dp-land` | `FIX_MID_4` | Required |
| 34 | `layout_w800dp-h1280dp` | Game, STACKED (720 cap) | classic-light | `w800dp-h1280dp` | `FIX_MID_4` | Required |
| 35 | `layout_w1280dp-h800dp-land` | Game, SIDE_PANEL (400) | classic-light | `w1280dp-h800dp-land` | `FIX_MID_4` | Required |
| 36 | `game_mid-4x4_classic-light_dpad` | Game, TalkBack pad + status line | classic-light | Pixel7, touch exploration on via `ShadowAccessibilityManager.setTouchExplorationEnabled(true)` | `FIX_MID_4` | Required |
| 37 | `motion_merge-spawn-4x4_classic-light_t000` | Filmstrip, origin frame (0 ms) | classic-light | Pixel7, Motion FULL | `FIX_FILM` | Required |
| 38 | `motion_merge-spawn-4x4_classic-light_t040` | Filmstrip, mid-slide (48 ms) | classic-light | same | `FIX_FILM` | Required |
| 39 | `motion_merge-spawn-4x4_classic-light_t080` | Filmstrip, merge reveal (80 ms) | classic-light | same | `FIX_FILM` | Required |
| 40 | `motion_merge-spawn-4x4_classic-light_t160` | Filmstrip, pop + spawn (160 ms) | classic-light | same | `FIX_FILM` | Required |
| 41 | `motion_merge-spawn-4x4_classic-light_t400` | Filmstrip, settled (400 ms) | classic-light | same | `FIX_FILM` | Required |
| 42 | `sheet_boardsize_classic-light` | Board-size sheet over `FIX_MID_4`, 3×3 "In progress" | classic-light | Pixel7 | `FIX_MID_4` + `FIX_MID_3` + `FIX_RECORDS` | Required |
| 43 | `dialog_newgame_classic-light` | New-game confirmation | classic-light | Pixel7 | `FIX_MID_4` | Required |
| 44 | `sheet_whatsnew_classic-light` | What's new sheet | classic-light | Pixel7, `lastSeenVersionCode = 20000` | `FIX_MID_4` | Required |
| 45 | `game_hint-4x4_classic-light` | First-launch hint | classic-light | Pixel7, `onboardingDone = false` | `FIX_FRESH_4` | Required |

Size budget (D-147, ≤ 10 MiB): flat UI at 1080×2400 px compresses to roughly 60–200 KB, so 45 PNGs fit. `QUALITY.md` owns the size check.

### 10.5 Coverage matrix

The matrix is a covering design, not a full cartesian product. Every axis value appears at least once against the Game screen, and the riskiest pairs are covered explicitly. The full product of 8 screens × 4 palette-modes × 7 windows × 21 locales × 2 font scales would be about 9,400 PNGs, far beyond D-147's 10 MiB.

| Axis | Values | Covered by goldens |
|---|---|---|
| Palette × mode | classic-light, classic-dark, neon-light, neon-dark | 1–16 (Classic), 10–11 (Neon), 17–18 |
| High contrast | on | 12 |
| Board size | 3, 4, 5, 8 | 5, 1–4, 8–12, 6–7. Sizes 6 and 7 are covered by `game/board/BoardGeometryTest` and `game/board/TileLabelsTest` (JVM) |
| Layout mode | STACKED, THREE_COLUMN, SIDE_PANEL, compact-medium-expanded widths | 29–35. The COMPACT STACKED header and SCROLL (§5.3) have no golden: `GameScreenTest.shortWindowsKeepBoardAndChromeOnScreen` (F18-AC10) asserts their bounds at `w360dp-h450dp` and `w411dp-h450dp`, with and without the TalkBack pad |
| Locale / direction | en, fa (RTL), de (long), en-XA, ar-XB | 23–26 |
| Font scale | 2.0 | 27–28 |
| Accessibility mode | TalkBack pad | 36 |
| Game state | fresh, mid, dense, ramp, won, over, hint | 1–16, 45 |
| Overlays | win card, game-over card, size sheet, new-game dialog, What's new | 13–16, 42–44 |
| Motion | merge + spawn timeline | 37–41 |
| Sub-screens | Settings, Statistics, How to play, About, Licenses | 17–22, 24–25, 28 |

### 10.6 Store assets (not goldens)

`StoreAssetsTest` is excluded from normal runs and runs only with `-PrecordStoreAssets=true` (D-115). It writes:
- `icon.png` (§8.4);
- `featureGraphic.png` (§8.5);
- 4 phone, 1 seven-inch and 1 ten-inch screenshot (§8.6).

Each is captured with `captureToImage()` (or drawn into a `Bitmap`, for the icon) and written by `OpaquePngWriter` as an 8-bit PNG; every store image is a PNG. They are not compared by Roborazzi. `check_store_images` in `scripts/check-metadata.sh` validates the exact image set, pixel sizes, bit depth and color types (D-190), and the agent Reads each one in M5.

---

## Open issues

None open. Resolved during planning: DS-OI-1 → D-181; DS-OI-2, DS-OI-3, DS-OI-7 → D-188; DS-OI-4 → D-187; DS-OI-5 → D-204; DS-OI-6 → D-205; DS-OI-8 → D-186; DS-OI-9, DS-OI-10 → D-189.
