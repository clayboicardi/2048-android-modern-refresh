package com.clayboicardi.game2048.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every dimension token of the app (DESIGN_SYSTEM.md §4.1, §5; D-075, D-091, D-188). Code outside
 * `ui/theme/` reads its dp and sp values from here and from `TileSkin`; no dp or sp literal appears
 * elsewhere (D-144). Ratios that are not lengths are named next to the length they scale.
 */
object GameDimens {
    // 4-dp grid (2 dp only for focus-ring gaps).
    val Space2: Dp = 2.dp
    val Space4: Dp = 4.dp
    val Space8: Dp = 8.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp
    val Space48: Dp = 48.dp

    // D-075 margins, applied per axis by window class (DESIGN_SYSTEM.md §5.2, D-188).
    val MarginCompact: Dp = 16.dp
    val MarginMedium: Dp = 24.dp
    val MarginExpanded: Dp = 32.dp

    // Window-class breakpoints (DESIGN_SYSTEM.md §5.1, §5.2): width < 600 / 600–839 / ≥ 840;
    // height < 480 / 480–899 / ≥ 900.
    val WidthMediumMin: Dp = 600.dp
    val WidthExpandedMin: Dp = 840.dp
    val HeightMediumMin: Dp = 480.dp
    val HeightExpandedMin: Dp = 900.dp

    // Stacked chrome (D-091): 240 = HeaderHeight + 2 × StackGap + ControlsBarHeight.
    val HeaderRow1: Dp = 64.dp
    val HeaderRow2: Dp = 48.dp
    val HeaderHeight: Dp = 112.dp
    /** The COMPACT header of STACKED and SCROLL (DESIGN_SYSTEM.md §5.3). */
    val HeaderCompact: Dp = 64.dp
    /** Minimum height; the bar grows when its labels wrap (DESIGN_SYSTEM.md §3.6). */
    val ControlsBarHeight: Dp = 80.dp
    /** Header ↔ board and board ↔ controls; `Space8` replaces it with the COMPACT header. */
    val StackGap: Dp = 24.dp

    // Score chips (DESIGN_SYSTEM.md §7.2).
    val ScoreChipMinWidth: Dp = 88.dp
    val ScoreChipMinHeight: Dp = 64.dp
    /** `ScoreValue` sizes: the largest of 14..28 sp in 1 sp steps that fits (D-084). */
    val ScoreValueMin: TextUnit = 14.sp
    val ScoreValueMax: TextUnit = 28.sp
    val ScoreValueStep: TextUnit = 1.sp
    /** The "+N" float text (DESIGN_SYSTEM.md §3.5). */
    val PlusNText: TextUnit = 16.sp
    /** Padding of the "+N" pill for `maxMergedExp ≥ 9` (D-187). */
    val PlusNPillPaddingH: Dp = 6.dp
    val PlusNPillPaddingV: Dp = 2.dp

    // Title "2048" (DESIGN_SYSTEM.md §3.5): `TextAutoSize.StepBased(TitleMin, max, TitleStep)`.
    val TitleMin: TextUnit = 32.sp
    val TitleMax: TextUnit = 56.sp
    /** Window width ≥ 840 dp. */
    val TitleMaxExpanded: TextUnit = 64.sp
    val TitleStep: TextUnit = 1.sp

    /** Undo, New game, More and card buttons: `ButtonDefaults.MediumContainerHeight` (56 dp). */
    val ControlHeight: Dp = ButtonDefaults.MediumContainerHeight
    /** The board-size chip's leading and trailing icons (DESIGN_SYSTEM.md §7.3). */
    val SizeChipIcon: Dp = 18.dp

    // Swipe classification (D-050; ARCHITECTURE.md §6.1). The commit distances 40 / 24 / 12 dp
    // are `SwipeSensitivity.thresholdDp` (D-042).
    /** A swipe's major axis must be at least this many times its minor axis. */
    const val SwipeDominanceRatio: Float = 1.4f
    /** The fling fallback's minimum velocity along the dominant axis, dp per second. */
    const val SwipeFlingDpPerSecond: Float = 600f

    // TalkBack direction pad and status line (D-082).
    val PadButton: Dp = 64.dp
    val PadGap: Dp = 8.dp
    /** Two lines of `bodyMedium`. */
    val StatusLineMinHeight: Dp = 40.dp

    // Board (D-075).
    val BoardMin: Dp = 280.dp
    val BoardMax: Dp = 720.dp
    val BoardFloor: Dp = 220.dp
    /** The gap clamp of `boardGeometry` (DESIGN_SYSTEM.md §4.2). */
    val BoardGapMin: Dp = 2.dp
    val BoardGapMax: Dp = 20.dp
    /** Neon's inset well, `innerShadow` radius (DESIGN_SYSTEM.md §4.5). */
    val BoardWellRadius: Dp = 12.dp
    /**
     * Tile labels: a full label whose nominal size is below this uses the compact form when `exp ≥
     * 10` (DESIGN_SYSTEM.md §3.3 step 3, D-077).
     */
    val TileLabelCompactBelow: Dp = 12.dp

    // 3-column and side panel (D-075, DESIGN_SYSTEM.md §5.3).
    val RailMin: Dp = 160.dp
    val RailMax: Dp = 240.dp
    val RailGap: Dp = 12.dp
    /** `rail = clamp(RailFraction · W, RailMin, RailMax)`. */
    const val RailFraction: Float = 0.22f
    /** The rail while the TalkBack pad (208 dp wide) is shown. */
    val RailWithPadMin: Dp = 208.dp
    val PanelMin: Dp = 280.dp
    val PanelMax: Dp = 400.dp
    val PanelGap: Dp = 32.dp
    /** `panel = clamp(PanelFraction · W, PanelMin, PanelMax)`. */
    const val PanelFraction: Float = 0.32f

    /** Settings, Stats, How to play, About and Licenses columns. */
    val ContentMaxWidth: Dp = 640.dp

    // Overlay cards (D-200): never wider than board − 16 dp; height budget board − 16 dp.
    val OverlayCardMinWidth: Dp = 264.dp
    val OverlayCardMaxWidth: Dp = 400.dp
    val OverlayCardInset: Dp = 16.dp
    /** `ElevatedCard` default elevation of the win and game-over cards (DESIGN_SYSTEM.md §4.5). */
    val OverlayCardElevation: Dp = 6.dp

    /** The win card's icon-only Share at its top end (DESIGN_SYSTEM.md §6.3, §6.3a). */
    val CardShareButton: Dp = 48.dp
    /** The Compact win and game-over cards' padding (DESIGN_SYSTEM.md §6.3a, §6.4a). */
    val CardPaddingCompact: Dp = 20.dp
    /** The game-over card's SCORE, BEST and BEST TILE boxes (DESIGN_SYSTEM.md §6.4a). */
    val CardStatBoxMinHeight: Dp = 64.dp
    /** The Regular game-over card's SCORE value: `ScoreValue` fitted 14..40 sp (§6.4). */
    val CardScoreValueMax: TextUnit = 40.sp
    /** The Compact game-over card's SCORE and BEST values: 14..24 sp (§6.4a). */
    val CardScoreValueCompactMax: TextUnit = 24.sp

    // Win hero badge (DESIGN_SYSTEM.md §6.3, §6.3a).
    val HeroBadge: Dp = 96.dp
    /** Window height < 480 dp. */
    val HeroBadgeShort: Dp = 72.dp
    /** Window width ≥ 840 dp. */
    val HeroBadgeExpanded: Dp = 120.dp
    /** The Compact win card. */
    val HeroBadgeCompact: Dp = 64.dp

    // Board-size sheet (DESIGN_SYSTEM.md §6.7).
    /** A size row's minimum height. */
    val SizeSheetRowMinHeight: Dp = 72.dp

    // Mini tiles (DESIGN_SYSTEM.md §7.8).
    /** Size sheet, statistics, Compact game-over card. */
    val MiniTileSmall: Dp = 32.dp
    /** Regular game-over card. */
    val MiniTileMedium: Dp = 40.dp
    /** Statistics hero. */
    val MiniTileHero: Dp = 56.dp
    /** `MiniBoardGlyph` gap as a fraction of `sizePx / n`. */
    const val MiniGlyphGapRatio: Float = 0.10f
    /** `PaletteSwatch` tiles; `Space4` apart, on `Space4` padding. */
    val SwatchTile: Dp = 24.dp

    // Focus ring (DESIGN_SYSTEM.md §7.7, D-084): outset radius = radius + gap + width / 2.
    val FocusRingWidth: Dp = 2.dp
    val FocusRingGap: Dp = 2.dp

    // High contrast (DESIGN_SYSTEM.md §2.7, D-073, D-206).
    val HcOutlineWidth: Dp = 1.5.dp
    val HcEmptyCellStroke: Dp = 1.dp
}
