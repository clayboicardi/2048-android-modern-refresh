# 04 — Design & Motion research (track: design-motion)

Research date: 2026-10-28. This is a draft for `docs/DESIGN_SYSTEM.md` (sections 1–9) and `docs/MOTION_SPEC.md` (sections 10–12).

How the facts were checked. Library facts come from three places: the published `-sources.jar` artifacts on Google Maven, the androidx GitHub sources, and the local SDK stubs (`/opt/android-sdk/platforms/android-36` and `android-37.0`). Color metrics, spring timings and font metrics were computed by the scripts in the scratchpad. m3.material.io would not resolve from this sandbox (DNS blocked). I took M3 token values from the generated androidx token sources, which are built from the M3 token database.

Reproducible artifacts. All of these live in the scratchpad (`<sandbox-scratchpad>/`):
- `scripts/colorlib.py`: WCAG contrast, OKLab/OKLCH conversion, Machado-2009 CVD simulation.
- `scripts/palettes2.py`: all palette tables in this document.
- `scripts/springs.py` and `scripts/shake.py`: spring timings.
- `scripts/fontcheck.py` and `scripts/digitwidth.py`: font feature and width checks.
- `palette_preview.png`: a rendered preview of the palettes.

---

## 0. Key findings and recommended decisions

1. **The M3 Expressive Compose APIs are not public in the current stable BOM.**
   - `compose-bom:2026.09.00` maps `material3` to **1.4.0**. In 1.4.0, `MotionScheme`, `MaterialExpressiveTheme`, `MaterialTheme.motionScheme` and the `*Emphasized` type styles are all **`internal`**.
   - In **material3 1.5.0-beta02** (2026-10-21) they are **public and not experimental**: `MotionScheme`, `MotionScheme.expressive()`/`standard()`, `MaterialExpressiveTheme`, `Typography.displayLargeEmphasized` and the others, `expressiveLightColorScheme()`, `ButtonGroup`, `HorizontalFloatingToolbar`, `ToggleButton`, `Button(shapes = ButtonDefaults.shapes())`.
   - Still experimental in 1.5.0-beta02: `MaterialShapes` (35 shapes), `RoundedPolygon.toShape()`, `Morph.toPath()` and `LoadingIndicator`, all under `@ExperimentalMaterial3ExpressiveApi`.
   - **Recommendation:** define our own `GameMotion` token object that copies the token values (section 3.1). The game then never depends on the internal or experimental surface. Also pin `material3` to `1.5.0-beta02`, moving to `1.5.0` when it ships, if the dependency track agrees. Otherwise stay on BOM 1.4.0 and use our tokens only.
2. **Slides must use the Standard fast spatial spring (damping 0.9, stiffness 1400), not the Expressive one.**
   - The Expressive fast spatial spring (0.6 / 800) overshoots by **9.3%**, so a sliding tile would visibly pass into the next cell.
   - The KDoc says standard is "for utilitarian UI elements and **recurring interactions**" and expressive is "for prominent UI elements and **hero interactions**". A player makes hundreds of slides per game, so slides are recurring. Merge pop, spawn and the celebrations are hero moments and use the Expressive springs.
3. **Tiles are immutable and keyed by stable IDs.** Each tile ID has a fixed value; a merge creates a new ID, and the two source tiles become short-lived "ghost" tiles. As a result, a tile composable composes **once**. Every per-frame value is read in the layout or draw phase: `Modifier.offset {}`, `graphicsLayer {}` and `drawBehind {}`. **Use `offset {}` for translation, not `graphicsLayer { translationX }`.** In compose-ui 1.12.1, only layer *position/size* changes vote `FrameRateCategory.High` on Android 15+ adaptive refresh rate (ARR) devices. Layer *property* changes vote the layer's `frameRate`, which defaults to 0 (no preference). Details in section 4.2.
4. **Interrupting a move: snap, then animate.** When a new move is committed, every in-flight slide jumps to its target (`snapTo`), ghost tiles are removed, and the new slides start. Effect animations (pop, spawn, "+N" floats) keep running. Input is never queued or blocked.
5. **Haptics use only `View.performHapticFeedback` and Compose `LocalHapticFeedback`.** These need **no `VIBRATE` permission** (developer.android.com, quoted in section 5). That keeps the manifest permission-free. Events map to `SegmentTick` (merge), `Confirm` (big merge or win), `Reject` (invalid move or game over) and `GestureThresholdActivate` (new best).
6. **Reduced motion comes almost free in Compose, with three gaps.**
   - Compose reads `Settings.Global.ANIMATOR_DURATION_SCALE`. At scale 0, every Compose animation jumps to its end on the first frame (verified in `SuspendAnimation.kt`).
   - Gaps:
     - coroutine `delay()` calls are not scaled;
     - custom `withFrameNanos` loops such as confetti are not scaled;
     - the in-app "Reduce motion" setting has to be wired up by us.
   - Section 6 has the substitution table.
7. **Legacy palette accessibility failures, measured.**
   - White text on the 8–2048 tiles has a contrast of **1.42–2.96:1**, so all of them fail WCAG.
   - The five gold tiles from 128 to 2048 are nearly the same color. Adjacent ΔE_OK×100 is **1.0–1.6**, at or below the threshold where people can tell two colors apart.
   - Score text: white on `#BBADA0` is **2.19:1**.
   - Night mode only changes the page background, so body text is `#776E65` on `#2D302C` = **2.68:1**.
   - All three proposed palettes reach at least 3.66:1 on every tile, and at least 4.5:1 on all but two (section 8).
8. **Recommended visual direction:** ship **"Classic Refined" (A)** as the default. It keeps the recognizable 2048 identity with the contrast fixed. **"Material You" (B)** and **"Neon Arcade" (C)** are selectable themes. All three plug into the same `TilePalette` and `GameTheme` tokens, so palettes are just data.
9. **Font:** bundle **Google Sans Flex**.
   - License and source: OFL 1.1, variable font with `wght` 1–1000, `wdth` 25–151, `opsz` 1–144, `ROND` 0–100 and `slnt` −10–0 axes; has `tnum` and `lnum`. Source: google/fonts `ofl/googlesansflex` and `github.com/googlefonts/googlesans-flex`.
   - Size: the full file is 3.9 MB. A Latin subset with `wght` 400–900, `wdth` 75–100 and `ROND` 0–100 is **180 KB**. A digits-only subset is **18 KB**.
   - Use it for numerals and display text. Use the system font for UI text, because Google Sans Flex has no Cyrillic or Greek, and the ru, uk, el and sr translations need them.
10. **Adaptive layout:**
    - Use `currentWindowAdaptiveInfoV2()` from material3-adaptive 1.3.0.
    - Breakpoints are 600/840/1200/1600 dp for width and 480/900 dp for height.
    - Layout modes are stacked (compact/medium width), 3-column (compact height) and board + side panel (expanded width).
    - From targetSdk 36 (and with no opt-out at 37), the app cannot rely on locking orientation on screens of 600dp smallest width and up. The exception is apps declaring `android:appCategory="game"`. The layout should be fully responsive anyway.

---

## 1. Legacy baseline audit (what the rewrite replaces)

| Aspect | Legacy value | Where | Issue / note |
|---|---|---|---|
| Slide | `transition: transform 100ms ease-in-out` | `assets/2048/style/main.scss:22`, `:331` | Duration-based, so it can't be interrupted smoothly |
| Spawn | `appear` 200ms `ease`, delay 100ms, scale 0→1, opacity 0→1 | `main.scss:419-433` | Delay equals the slide duration |
| Merge pop | `pop` 200ms `ease`, delay 100ms, scale 0→1.2→1 | `main.scss:436-453` | The merged tile pops *from 0*, which reads as "new" rather than "combined" |
| Score "+N" | `move-up` 600ms `ease-in`, top 25px→−50px (75px rise), opacity 1→0 | `main.scss:50-60`, `:104` | |
| Win / lose overlay | `fade-in` 800ms `ease`, **delay 1200ms** (`$transition-speed*12`) | `main.scss:235` | 2.0s before the overlay is interactive |
| Restart / undo prompt | `fade-in` 400ms, delay 100ms | `main.css:203` | |
| Board geometry | 500px field, 15px gap (**3.0%** of the field), tile radius 3px, board radius 6px | `main.scss:4-8`, `:190` | Mobile: 280px field, 10px gap (`main.scss:479-483`) |
| Tile digit size | 55 / 45 / 35 / 30px on a 106.25px tile, i.e. **0.52 / 0.42 / 0.33 / 0.28** of the tile for 1–2, 3, 4 and 5+ digits | `main.scss:327`, `:387-399`, `:411` | Mobile 35 / 25 / 15 / **10px** (`main.scss:414`): the super tile text is unreadable |
| Tile colors | 2 `#EEE4DA`, 4 `#EDE0C8`, 8 `#F2B179`, 16 `#F59563`, 32 `#F67C5F`, 64 `#F65E3B`, 128 `#EDCF72`, 256 `#EDCC61`, 512 `#EDC850`, 1024 `#EDC53F`, 2048 `#EDC22E`, super `#3C3A32`; text `#776E65` or `#F9F6F2` | `main.css:362-424` (generated from `main.scss:336-415`) | See the contrast table below |
| Glow | 128–2048: `0 0 30px 10px rgba(243,215,116,α)` with α = 0.238 / 0.317 / 0.397 / 0.476 / 0.556; inset 1px white at α/… | `main.scss:379-384`, `main.css:380-416` | Worth carrying into direction A |
| Font | Clear Sans Light/Regular/Bold webfonts (Intel, Apache-2.0) | `style/fonts/clear-sans.css` | Has no `tnum`; digits come in two widths (1204 and 1280 units out of 2048) |
| Night mode | Sets only `html`/`body` background to `#2D302C` | `js/nightmode.js:3-12` | Text `#776E65` on `#2D302C` = **2.68:1** (fails) |
| Rendering | Every move clears and rebuilds the tile DOM | `js/html_actuator.js:13-22`, `:56-101` | No persistent tile identity |
| Swipe threshold | 10px, dominant axis wins | `js/keyboard_input_manager.js:127-130` | No dp scaling, no diagonal rejection |
| Keys | Arrows, Vim HJKL, WASD; R restarts | `js/keyboard_input_manager.js:37-50`, `:66` | Keep these for keyboard and ChromeOS users |
| Undo | Stack of game states | `js/game_manager.js:27-50` | Keep the feature and add an undo motion |
| Back | "Press back again to exit" toast | `src/com/uberspot/a2048/MainActivity.java:113` | On targetSdk 36+, `onBackPressed` is not called (predictive back). Drop this pattern. |
| Fullscreen | Preference toggle | `MainActivity.java:32`, `:94` | Replace with optional immersive mode (section 7.4) |

**Legacy contrast and CVD audit** (computed):

| Tile | Fill | Text | Contrast | Verdict |
|---|---|---|---|---|
| 2 | `#EEE4DA` | `#776E65` | 3.98:1 | Large-text AA only |
| 4 | `#EDE0C8` | `#776E65` | 3.83:1 | Large-text AA only |
| 8 | `#F2B179` | `#F9F6F2` | **1.72:1** | Fail |
| 16 | `#F59563` | `#F9F6F2` | **2.08:1** | Fail |
| 32 | `#F67C5F` | `#F9F6F2` | **2.45:1** | Fail |
| 64 | `#F65E3B` | `#F9F6F2` | **2.96:1** | Fail |
| 128–2048 | `#EDCF72` … `#EDC22E` | `#F9F6F2` | **1.42–1.58:1** | Fail |
| super | `#3C3A32` | `#F9F6F2` | 10.57:1 | Pass |

- Adjacent-tile ΔE (OKLab×100) with normal vision: 2.3, 12.5, 6.4, 5.6, 5.9, 24.8, **1.6, 1.5, 1.2, 1.0**, 50.2. The minimum is **1.0**, between 1024 and 2048. Under tritan simulation the minimum is 0.9.
- Chrome: score value white on `#BBADA0` = 2.19:1; score label `#EEE4DA` on `#BBADA0` = 1.74:1; button `#F9F6F2` on `#8F7A66` = 3.79:1; body `#776E65` on `#FAF8EF` = 4.70:1 (passes).

---

## 2. Verified platform and library facts (2026-10-28)

| Artifact | Latest | Note |
|---|---|---|
| `androidx.compose:compose-bom` | **2026.09.00** | Maps `material3` 1.4.0, `ui` 1.12.1, `animation` 1.12.1, `material-icons-*` 1.7.8 (from the BOM pom) |
| `androidx.compose.material3:material3` | stable **1.4.0**; **1.5.0-beta02** (2026-10-21), beta01 2026-10-07, no RC yet | Expressive APIs are public in 1.5.0 (see 0.1) |
| `androidx.compose.ui` / `animation` | 1.12.1 stable; 1.13.0-beta02 | `Modifier.animateBounds` is **stable** in 1.12.1. `computedStateOf` is in runtime 1.13 (beta). |
| `androidx.compose.material3.adaptive:adaptive` | **1.3.0** stable; 1.4.0-alpha03 | `currentWindowAdaptiveInfoV2()`; the old `currentWindowAdaptiveInfo()` is deprecated |
| `androidx.window:window` | 1.5.1 stable | `WindowSizeClass` breakpoints, verified from window-core 1.5.1 sources: width 600 / 840 / **1200 / 1600**, height 480 / 900 |
| `androidx.graphics:graphics-shapes` | **1.1.0** stable | `RoundedPolygon`, `Morph`, `CornerRounding`, `star`/`pill`/`pillStar`/`circle`/`rectangle`, `toPath()`. New in 1.1: `SvgPathParser`, `FeatureSerializer` |
| `androidx.metrics:metrics-performance` (JankStats) | **1.0.0** | |
| `androidx.benchmark:benchmark-macro-junit4` | **1.5.0**; 1.6.0-alpha01 | |
| `androidx.baselineprofile` Gradle plugin | **1.5.0** | |
| `androidx.profileinstaller` | 1.4.1 | |
| `androidx.core` | 1.19.1 | `HapticFeedbackConstantsCompat` handles fallbacks (section 5) |
| `androidx.activity:activity-compose` | 1.13.0 | `enableEdgeToEdge()` |
| Platform SDK (local) | android-36, android-37.0 | `HapticFeedbackConstants` is identical in 36 and 37 (no new constants). `View.announceForAccessibility` is `@Deprecated` in both API 36 and API 37 stubs. |

---

## 3. Material 3 Expressive applied to the game

### 3.1 Motion physics tokens (exact values)

Source: `compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/{Standard,Expressive}MotionTokens.kt` (androidx-main). The values are identical in material3 1.4.0 and 1.5.0-beta02.

| Token | Standard: damping / stiffness | Expressive: damping / stiffness |
|---|---|---|
| fastSpatial | **0.9 / 1400** | **0.6 / 800** |
| defaultSpatial | **0.9 / 700** | **0.8 / 380** |
| slowSpatial | **0.9 / 300** | **0.8 / 200** |
| fastEffects | **1.0 / 3800** | **1.0 / 3800** |
| defaultEffects | **1.0 / 1600** | **1.0 / 1600** |
| slowEffects | **1.0 / 800** | **1.0 / 800** |

Usage rules, quoted from the `MotionScheme.kt` KDoc:
- Spatial specs are "for animations that may change the shape or bounds of the component".
- Effects specs are "for animations that do not change the shape or bounds … For example, color animation", and alpha.
- `standard()` is "Material's basic motion scheme for utilitarian UI elements and recurring interactions".
- `expressive()` is "Material's recommended motion scheme for prominent UI elements and hero interactions".

Measured spring behavior. These are numeric simulations with unit mass. "Settle" means the position is within 0.5px and the speed is below 5px/s. The test distances are 220px and 660px, which is 1 and 3 cells at an 80dp pitch on a 2.75× display.

| Spring | t90 | t99 | Settle, 1 cell | Settle, 3 cells | Overshoot |
|---|---|---|---|---|---|
| Std fastSpatial 0.9/1400 | 91ms | 139ms | 188ms | 271ms | 0.1% |
| Std defaultSpatial 0.9/700 | 129 | 196 | 261 | 367 | 0.1% |
| Std slowSpatial 0.9/300 | 197 | 298 | 388 | 521 | 0.1% |
| Expr fastSpatial 0.6/800 | 83 | 96* | 392 | 493 | **9.3%** |
| Expr defaultSpatial 0.8/380 | 153 | 203 | 452 | 492 | 1.4% |
| Expr slowSpatial 0.8/200 | 211 | 279 | 603 | 664 | 1.5% |
| fastEffects 1.0/3800 | 64 | 110 | 170 | 191 | 0 |
| defaultEffects 1.0/1600 | 98 | 168 | 248 | 279 | 0 |
| slowEffects 1.0/800 | 138 | 237 | 334 | 379 | 0 |
| Compose default `spring()` 1.0/1500 | 101 | 174 | 255 | 288 | 0 |

\* For underdamped springs, t99 is the first time the value crosses 99% (the overshoot), not the time it settles.

Compose built-in constants (animation-core 1.12.1):
- `Spring.StiffnessHigh` 10000, `Medium` 1500, `MediumLow` 400, `Low` 200, `VeryLow` 50.
- `DampingRatioHighBouncy` 0.2, `MediumBouncy` 0.5, `LowBouncy` 0.75, `NoBouncy` 1.0.
- `AnimationConstants.DefaultDurationMillis` 300.
- `Animatable(Float)` default visibility threshold: 0.01.

Duration and easing tokens, for the few tweens we use (`tokens/MotionTokens.kt`):
- Durations: Short1–4 = 50 / 100 / 150 / 200ms; Medium1–4 = 250 / 300 / 350 / 400ms; Long1–4 = 450 / 500 / 550 / 600ms; ExtraLong1–4 = 700 / 800 / 900 / 1000ms.
- Easings: Emphasized and Standard `(0.2, 0, 0, 1)`; EmphasizedDecelerate `(0.05, 0.7, 0.1, 1)`; EmphasizedAccelerate `(0.3, 0, 0.8, 0.15)`; StandardDecelerate `(0, 0, 0, 1)`; StandardAccelerate `(0.3, 0, 1, 1)`.

**Mapping to the game** (repeated in the motion spec, section 10):

| Game element | Token |
|---|---|
| Tile slide | Std fastSpatial |
| Spawn scale; merge pop impulse | Expr fastSpatial |
| Overlay card enter; panel and layout moves | Expr defaultSpatial |
| Win badge morph-back | Expr slowSpatial |
| Alpha and color | fastEffects / defaultEffects |
| Desaturation and scrim | tween (Long4 600ms or Medium2 300ms, Standard easing), because these are never interrupted |

Using the API on 1.5.0+ (our own tokens work on any version):
```kotlin
MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = typo) { … }
val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()      // only on material3 >= 1.5.0
```

### 3.2 Shape system

- **Corner tokens** (`tokens/ShapeTokens.kt`, 1.5.0): ExtraSmall 4, Small 8, Medium 12, Large 16, **LargeIncreased 20**, ExtraLarge 28, **ExtraLargeIncreased 32**, **ExtraExtraLarge 48** (dp), and Full (circle). The `Shapes` class gains `largeIncreased`, `extraLargeIncreased` and `extraExtraLarge`.
- **MaterialShapes** (1.5.0-beta02, experimental) has 35 `RoundedPolygon`s: Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond, ClamShell, Pentagon, Gem, Sunny, VerySunny, Cookie4/6/7/9/12Sided, Ghostish, Clover4Leaf, Clover8Leaf, Burst, SoftBurst, Boom, SoftBoom, Flower, Puffy, PuffyDiamond, PixelCircle, PixelTriangle, Bun, Heart. Helpers: `RoundedPolygon.toShape(startAngle)`, `RoundedPolygon.toPath()`, `Morph.toPath(progress, path, startAngle)`.
- **Morphing without the experimental API:** use `androidx.graphics:graphics-shapes:1.1.0` directly. Build `RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.88f, rounding = CornerRounding(0.14f))` for a cookie shape and `RoundedPolygon.rectangle(…, rounding = CornerRounding(r))` for the tile. Then `Morph(a, b)` → `morph.toPath(progress, androidPath)` → `asComposePath()` inside `drawBehind`. This is stable and free of opt-ins.
- **Where shapes appear in the game** (gameplay tiles always stay rounded squares, for clarity):
  1. The win hero badge morphs Circle → Cookie12Sided (section 10.9).
  2. The new-best badge is SoftBurst or a 10-point star (section 10.8).
  3. Buttons use `ButtonDefaults.shapes()` / `IconButtonDefaults` shapes, which morph round → squarer on press. This is built into M3 Expressive buttons and needs no code.
  4. The bottom controls use `ButtonGroup` or `HorizontalFloatingToolbar` (both stable in 1.5.0; release notes 1.5.0-alpha22).

### 3.3 Typography (baseline vs emphasized, from `TypeScaleTokens.kt` in 1.5.0)

| Style | Size / line height (sp) | Baseline weight → emphasized weight | Tracking (sp), baseline → emphasized |
|---|---|---|---|
| displayLarge | 57/64 | Regular → **Medium** | −0.2 → 0 |
| displayMedium | 45/52 | Regular → Medium | 0 |
| displaySmall | 36/44 | Regular → Medium | 0 |
| headlineLarge / Medium / Small | 32/40, 28/36, 24/32 | Regular → Medium | 0 |
| titleLarge | 22/28 | Regular → Medium | 0 |
| titleMedium | 16/24 | Medium → **Bold** | 0.2 → 0.15 |
| titleSmall | 14/20 | Medium → Bold | 0.1 |
| bodyLarge / bodyMedium | 16/24, 14/20 | Regular → Medium | 0.5→0.15, 0.2→0.25 |
| labelLarge / Medium / Small | 14/20, 12/16, 11/16 | Medium → **Bold** | 0.1, 0.5, 0.5 |

`expressiveLightColorScheme()` differs from baseline in one way: `on*Container` roles use **tone 30** instead of tone 10, so container text carries more color. Inside `MaterialExpressiveTheme` the default `Typography()` is used; there is a TODO for expressive type defaults.

### 3.4 Color

- **Dynamic color** needs API 31+: `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`. From API 34, Compose reads the system color-role styles, which already reflect the user's contrast setting.
- **System tonal slots** (API 31+): `android.R.color.system_{accent1,accent2,accent3,neutral1,neutral2}_{0,10,50,100,200,…,900,1000}` map to tones **100, 99, 95, 90, 80, …, 10, 0**. This was verified in material3 1.4.0 `DynamicTonalPalette.android.kt:146-158`, where `primary100 = system_accent1_0` and `primary0 = system_accent1_1000`.
- **Thirteen slots are not enough for a 12-step tile ramp.** With seed `#B3261E`, the slot mapping produced two identical adjacent tiles (4 and 8 both `#FFDAD5`, ΔE 0). The dynamic theme should therefore compute HCT tones at runtime from `colorScheme.primary` (section 8.B).
- **User contrast** (API 34+): `UiModeManager.getContrast()` returns −1…1 (0 standard, 0.5 medium, 1.0 high), with `addContrastChangeListener`. When contrast ≥ 0.5, the tile ink becomes whichever of `#000000`/`#FFFFFF` contrasts more, and tiles get a 1.5dp outline in `outline`.

---

## 4. Compose animation architecture for the sliding grid

### 4.1 State model (owned by the game-logic track, required by motion)

```kotlin
@Immutable data class TileUi(val id: Long, val value: Int, val row: Int, val col: Int,
                             val fromRow: Int, val fromCol: Int, val kind: Kind)  // Kind: Moved, Spawned, Merged, Ghost
@Immutable data class BoardUi(val moveId: Long, val tiles: List<TileUi>, val scoreDelta: Int, val event: MoveEvent?)
```
- A tile's `value` never changes. A merge produces a **new** `id` of kind `Merged`. The two sources are emitted once more as kind `Ghost`, with `row`/`col` set to the merge cell, so they slide in under the merged tile and are then dropped.
- `MoveEvent` carries what the motion and haptics layers need: `Moved`, `Invalid(direction)`, `Won`, `GameOver`, `NewBest`, `maxMergeValue`, `mergeCount`.

### 4.2 Rendering approach (decision)

Per-tile composables in a `Box`, keyed by tile ID:

```kotlin
Box(Modifier.size(boardSize)) {
  EmptyCells()                                       // static, drawn once in one drawBehind
  for (t in board.tiles) key(t.id) { Tile(t, geometry, palette) }
  EffectsCanvas(effectsState)                        // confetti, rings, sparkles: one Canvas, own frame loop
}
@Composable fun Tile(t: TileUi, g: Geometry, p: TilePalette) {
  val pos = remember(t.id) { Animatable(g.offsetOf(t.fromRow, t.fromCol), Offset.VectorConverter, Offset(0.5f, 0.5f)) }
  val scale = remember(t.id) { Animatable(if (t.kind == Spawned) 0.6f else 1f, visibilityThreshold = 0.002f) }
  val alpha = remember(t.id) { Animatable(if (t.kind == Spawned) 0f else 1f, visibilityThreshold = 1f / 255) }
  LaunchedEffect(t.id, t.row, t.col) { /* run the specs in section 10 */ }
  Box(Modifier
      .offset { pos.value.round() }                           // layout phase: placement only, no re-measure
      .size(g.tile)
      .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }  // layer-property phase
      .drawBehind { drawTile(p, t.value, cachedTextLayout) }) // draw phase; text via a cached TextLayoutResult
}
```

Why it is built this way:
- **At most 32 nodes.** The board has 16 cells, and in the worst case 16 merges add 16 ghosts. A single-`Canvas` renderer would save little at this size. It would also cost per-tile semantics, `key()` state retention and simple per-tile `Animatable`s.
- **`offset {}` instead of `graphicsLayer { translationX }`.** In compose-ui 1.12.1 `GraphicsLayerOwnerLayer.android.kt`:
  - `move()` (layout position) calls `voteFrameRate(FrameRateCategory.High)` when `isArrEnabled`, which is `SDK_INT >= 35` (lines 243-249).
  - Layer-property updates vote the layer's `frameRate`, which defaults to `0f` (no preference; lines 85 and 196-201).
  - So on Android 15+ devices with adaptive refresh rate, translating through `graphicsLayer` might not request 120Hz.
  - Belt and braces: while any board animation runs, also apply `Modifier.preferredFrameRate(FrameRateCategory.High)` (`androidx.compose.ui.FrameRate.kt`) to the board, and reset it to `FrameRateCategory.Default` when idle.
- **Text.** Use `rememberTextMeasurer()` with an LRU cache keyed by `(value, tileSizePx, paletteId)` and `drawText(layout, color, topLeft)` inside `drawBehind`. A tile's value is immutable, so it is measured once. `BasicText` is an acceptable alternative because the tile composes only once anyway.
- **Glow and shadows.** `Modifier.dropShadow(shape) { radius; spread; color; alpha }` and `Modifier.innerShadow(shape) { … }` are stable in compose-ui 1.12.1 (`ui/draw/Shadow.kt:137-188`). The block-lambda form "allows for more efficient transformations for animated use cases without recomposition". Put them inside the same node as the tile so that translation re-places the layer rather than redrawing it.

**Alternatives considered:**

| Technique | Verdict for tiles | Where it *is* useful |
|---|---|---|
| Per-tile `Animatable` (chosen) | Full control of staging (ghosts, delayed spawn, pop impulse), clear interruption semantics | Tiles |
| `LookaheadScope` + `Modifier.animateBounds` (stable in 1.12.1) | Tiles would need a per-move layout pass plus an approach pass, and ghost staging is awkward | **Adaptive re-layout:** score cards and side panel moving between stacked and side layouts, and fold/unfold. Use `boundsTransform = { _, _ -> spring(0.8f, 380f) }` (Expr defaultSpatial). |
| `updateTransition` | Too coarse for 16 independent tiles | **Overlay state machine** (`Playing → Won → GameOver`): scrim alpha, card scale, board saturation and blur under one labelled transition, which is debuggable in Android Studio Animation Preview |
| `AnimatedContent` | No | **Score odometer** digits (10.6) and the "BEST → NEW BEST" label |
| One `Canvas` for all tiles | Cheapest drawing, but semantics and caches have to be hand-written | Only for the confetti and particle layer |

### 4.3 Spring or tween, element by element

- **Springs** for anything that moves or scales, or could be interrupted: slides, pop, spawn, panel moves, nudge. Compose docs: "`spring` can handle interruptions more smoothly than duration-based `AnimationSpec` types because it guarantees the continuity of velocity when target value changes".
- **Tweens or keyframes** only for one-shot, non-interruptible effects whose timing must line up with something else: the "+N" float path, game-over desaturation, scrim fade, glow-ring expansion.

### 4.4 Interrupting moves (when the user swipes faster than the animation)

Policy, recommended as the default:

| In-flight element when a new move commits | Action |
|---|---|
| Slides (position `Animatable`s) | `snapTo(target)` immediately, then start the new slide from that cell |
| Ghost tiles | Remove them immediately |
| Spawn scale/alpha, merge pop, "+N" floats, rings, confetti | Let them keep running. They don't conflict, because they are layer and draw effects. |
| Input | Never blocked or queued. One move per gesture; key auto-repeat is limited to one move per 120ms. |

Why:
- At about 6–8 moves per second, a std fastSpatial slide is already 90% complete after 91ms, so the snap jumps at most about 10% of the distance.
- Snapping keeps the visual order identical to the model order, so tiles never overlap.
- A queue adds perceived lag, because inputs pile up behind animations.
- An optional "fluid" mode calls `animateTo(newTarget)` without snapping and keeps the velocity. Its risk is transient overlap between tiles in the same row, because they have different velocities.

**Swipe recognition.**
- Commit **during the drag** once the dominant-axis distance reaches 24dp and |major| ≥ 1.4·|minor|, rather than waiting for the finger to lift.
- On release, also commit if the fling velocity is at least 600dp/s on a dominant axis and the gesture has not committed yet.
- Ignore anything below the platform touch slop (`ViewConfiguration.scaledTouchSlop`, about 8dp).
- These numbers are a design proposal to tune on device. The legacy threshold was 10px (`keyboard_input_manager.js:127`).

### 4.5 Avoiding recomposition storms (checklist)

1. Strong skipping is on by default from Kotlin 2.0.20: "all restartable composable functions become skippable" and lambdas are auto-remembered. Even so, mark `TileUi`/`BoardUi` `@Immutable` and use `kotlinx.collections.immutable` or read-only lists.
2. Read per-frame values **only** inside `offset {}`, `graphicsLayer {}`, `drawBehind {}`/`drawWithCache {}`, or `dropShadow {}` blocks. Never read `Animatable.value` in the composition body.
3. Key tiles with `key(tile.id)`. Never use an index or `hashCode` (Compose docs: "never use list indexes or `hashCode()` as keys").
4. Keep score, best and overlay state in separate `State`s so that a score tick doesn't recompose the board.
5. Use `remember { derivedStateOf { … } }` for booleans derived from fast-changing state, such as `isNewBest` and `canUndo`. On runtime ≥ 1.13, `computedStateOf` is preferred for cheap derivations.
6. No backwards writes: never write state during composition. Haptics and live-region updates fire from `LaunchedEffect(board.moveId)`.
7. Pre-allocate particle arrays as structure-of-arrays `FloatArray`s. Do no allocation per frame inside `withFrameNanos`.

### 4.6 Measuring jank

- **Debug builds:** `JankStats.createAndTrack(window) { frameData -> … }` (`androidx.metrics:metrics-performance:1.0.0`). Tag state with `PerformanceMetricsState.getHolderForHierarchy(view).state?.putState("Board", "Animating")`. By default, jank means a frame that took at least twice the refresh period. On API 31+, `FrameDataApi31.frameOverrunNanos` is available.
- **CI and local benchmarks:** a Macrobenchmark module (`benchmark-macro-junit4:1.5.0`) with a scenario of 100 scripted swipes through `UiDevice.swipe`, using `FrameTimingMetric()`. It reports `frameDurationCpuMs` and `frameOverrunMs` (API 31+) at P50/P90/P95/P99. Add `TraceSectionMetric("%Tile%", Mode.Sum)` with composition tracing (runtime-tracing) to catch recomposition.
- **Targets** (proposed, to validate on a Pixel 6a-class device at 120Hz):
  - `frameDurationCpuMs` P95 ≤ 6.0ms
  - `frameOverrunMs` P99 ≤ 0ms
  - zero tile recompositions during a slide
- **Baseline profile:** `androidx.baselineprofile` 1.5.0 + `profileinstaller` 1.4.1, generated from the same swipe journey, to avoid jank on first launch from JIT.

---

## 5. Haptics

**Permission:** "Unlike other haptic APIs, using `HapticFeedbackConstants` with a `View` doesn't require the `VIBRATE` permission". The `Vibrator`/`VibrationEffect` path *does* need `VIBRATE`. That is an install-time, not runtime, permission, but leaving it out keeps the manifest permission-free. All haptics "respect the user's touch feedback settings by default" and honor `HAPTIC_FEEDBACK_ENABLED`.

**Compose `HapticFeedbackType`** (compose-ui 1.12.1, verified): `Confirm`, `ContextClick`, `GestureEnd`, `GestureThresholdActivate`, `KeyboardTap`, `LongPress`, `Reject`, `SegmentFrequentTick`, `SegmentTick`, `TextHandleMove`, `ToggleOff`, `ToggleOn`, `VirtualKey`.
- `PlatformHapticFeedback` maps each one to `HapticFeedbackConstantsCompat` through `ViewCompat.performHapticFeedback`.
- Constants with no Compose type: `GESTURE_START` (12), `GESTURE_THRESHOLD_DEACTIVATE` (24), `DRAG_START` (25), `CLOCK_TICK` (4). Call these through `LocalView.current` and `ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.X)`.

**Platform constants and fallbacks** (`HapticFeedbackConstantsCompat.getFeedbackConstantOrFallback`, core 1.19.1):

| Constant (value) | Native API | Fallback below that API |
|---|---|---|
| `CONFIRM` (16), `GESTURE_START` (12) | 30 | `VIRTUAL_KEY` |
| `REJECT` (17) | 30 | `LONG_PRESS` |
| `GESTURE_END` (13) | 30 | `CONTEXT_CLICK` |
| `SEGMENT_TICK` (26), `TOGGLE_ON` (21), `GESTURE_THRESHOLD_ACTIVATE` (23) | 34 | `CONTEXT_CLICK` |
| `SEGMENT_FREQUENT_TICK` (27), `TOGGLE_OFF` (22), `GESTURE_THRESHOLD_DEACTIVATE` (24) | 34 | `CLOCK_TICK` |
| `DRAG_START` (25) | 34 | `LONG_PRESS` |
| `TEXT_HANDLE_MOVE` (9), `KEYBOARD_RELEASE` (7), `VIRTUAL_KEY_RELEASE` (8) | 27 | `NO_HAPTICS` |

`FLAG_IGNORE_GLOBAL_SETTING` (2) still exists in the API 37 stubs. Don't use it.

Design rules from developer.android.com haptics principles:
- "less is more"
- "Given the choice of buzzy haptics or no haptics … choose no haptics"
- haptics for frequent events "should be very subtle"
- "co-design of visual, audio, and haptic effects"; out-of-sync haptics feel broken

**Haptic map for 2048.** Fire each on the same frame as its visual cue.

| Event | Haptic (Compose type or constant) | Timing | Mode |
|---|---|---|---|
| Valid move, no merge | none (`SegmentFrequentTick` in "Full" mode) | At the swipe-commit frame | Full only |
| Move with ≥1 merge (max merge < 128) | `SegmentTick` | At the pop frame, commit + 80ms. **Once per move**, whatever the number of merges. | Subtle and Full |
| Big merge (max merge ≥ 128) | `Confirm` | Commit + 80ms. Replaces `SegmentTick`. | Subtle and Full |
| New best (first crossing per game) | `GestureThresholdActivate` | At the badge-appear frame | Subtle and Full |
| Win (2048 reached) | `Confirm`, then `Confirm` again 150ms later | Commit + 120ms and + 270ms | Subtle and Full |
| Game over | `Reject` | Start of the sequence (T0, section 10.10) | Subtle and Full |
| Invalid move | `Reject`, limited to 1 per 400ms | Nudge start | Subtle and Full |
| Undo | `ToggleOff` | On tap | Subtle and Full |
| Settings switches | `ToggleOn` / `ToggleOff` | On toggle | Subtle and Full |

The settings screen offers **Haptics: Off / Subtle (default) / Full**. Avoid `VibrationEffect.Composition` primitives (`PRIMITIVE_THUD`, `SPIN` and others), because they need `VIBRATE`.

---

## 6. Reduced motion and accessibility

### 6.1 Detecting reduced motion

- Compose's `WindowRecomposer.android.kt` reads `Settings.Global.ANIMATOR_DURATION_SCALE` (lines 97-106) and observes changes. It exposes the value as `MotionDurationScale` in the coroutine context.
- In `SuspendAnimation.kt:328-338`, `durationScale == 0f` sets `playTimeNanos = anim.durationNanos`, so **every `Animatable`/`animate*` jumps to its end value on the first frame**. A scale of 0.5 or 2 stretches durations.
- Not covered automatically:
  1. `kotlinx.coroutines.delay(ms)` used for sequencing. Multiply it by the scale, or put the delay inside the spec with `tween(delayMillis = …)` / `keyframes`.
  2. Custom `withFrameNanos` loops (confetti, sparkles). Read `coroutineContext[MotionDurationScale]?.scaleFactor` and, when it is 0, skip or render the end state.
  3. Infinite or looping effects.
- Provide `LocalReducedMotion: Boolean = systemScale == 0f || appSetting == ReduceMotion.On`. The app setting is **Motion: System (default) / Reduced / Full**. `ValueAnimator.areAnimatorsEnabled()` (API 26+) is an equivalent system probe.
- WCAG 2.2 SC 2.3.3 (AAA, Animation from Interactions) asks that non-essential motion triggered by interaction can be turned off. SC 2.3.1 forbids more than 3 flashes per second. SC 2.2.2 says auto-moving content lasting more than 5s needs a pause, so **no infinite animations**.

### 6.2 Reduced-motion substitutions

| Moment | Full | Reduced |
|---|---|---|
| Slide | spring 0.9/1400 | **Instant** (state change only). Optionally a 100ms crossfade of moved tiles with fastEffects alpha. |
| Spawn | scale 0.6→1 + alpha | Alpha 0→1 only, fastEffects (about 110ms) |
| Merge pop | scale impulse | None. 120ms color crossfade. |
| "+N" float | Rise 28dp + fade | Fade only, in place, 400ms |
| Invalid nudge | 5.2dp spring nudge | Board outline alpha 0→0.6→0 over 300ms (one pulse) |
| New best | Badge burst, rotation, sparkles | Label swap + container color crossfade |
| Win | Pop, ring, morph, confetti | Overlay fades in over 150ms. Static badge. |
| Game over | Desaturate + tile deflate + card spring | Overlay fades in over 150ms |
| Score odometer | Digit roll | Instant text change |

### 6.3 TalkBack design for a 4×4 board

- **Traversal order:** score group (score, best), then the board, then controls. Set `isTraversalGroup = true` on each group and use `traversalIndex` if the layout order differs (for example in the 3-column landscape layout).
- **Board node:** `Modifier.semantics(mergeDescendants = false) { collectionInfo = CollectionInfo(rowCount = 4, columnCount = 4); contentDescription = "Game board. 9 tiles. Highest tile 256." }`. It carries **custom actions**: `CustomAccessibilityAction("Move up") { onMove(Up); true }`, plus Move down/left/right, Undo (only when available), and New game. TalkBack lists these in its Actions menu, and Switch Access exposes them too.
- **Cells:** each of the 16 cells is a semantics node with `collectionItemInfo = CollectionItemInfo(rowIndex = r, rowSpan = 1, columnIndex = c, columnSpan = 1)` and `contentDescription = "Row 2, column 3, 64"` or `"Row 2, column 3, empty"`. This makes explore-by-touch work. Cells are **not** the tiles, because tiles are transient during animation and the cell nodes stay stable.
- **Announcements:**
  - `View.announceForAccessibility` is `@Deprecated` in API 36 and 37, so use live regions.
  - The status line under the score is visible small text and a polite live region (`liveRegion = LiveRegionMode.Polite`). Its text after each move follows the pattern "Moved left. Merged 16 and 32. Score 1,240." or "Can't move left."
  - Game over and win use `LiveRegionMode.Assertive` once, and move focus to the overlay with `paneTitle = "Game over"` or `"You win"`.
  - A verbosity setting offers Brief, which omits the location of the new tile, and Detailed, which says "New 2 at row 4, column 1".
- **Swiping while TalkBack runs:** TalkBack consumes single-finger swipes. When `AccessibilityManager.isTouchExplorationEnabled` is true (watch it with `addTouchExplorationStateChangeListener`), automatically show a **direction pad** below the board. It has four 64dp buttons labelled "Move up" and so on, plus Undo.
- **Keyboard, D-pad and gamepad:** arrows, WASD, HJKL and R to restart (legacy parity), plus `KEYCODE_DPAD_*` and gamepad buttons. Provide a visible focus indicator with at least 3:1 contrast.
- **Touch targets:** at least 48×48dp. M3's `Modifier.minimumInteractiveComponentSize()` reserves 48dp. The board swipe surface covers the whole board.

### 6.4 Contrast policy

- **Tile numerals:**
  - Target at least 4.5:1 on every tile.
  - Hard floor of 3.0:1 for tiles with 1–3 digits. These count as "large text" because they are bold and at least 18.66px: on the smallest 280dp board, a 2-digit numeral is 0.50 × 57.5dp ≈ 29dp.
  - At least 4.5:1 on tiles with 4 or more digits. At 0.27 × 57.5dp ≈ 15.5dp they may fall below the large-text size.
- **UI text:** M3 color roles (on* over containers), at least 4.5:1.
- **Non-text:** focus rings, the outline on the high-contrast board, and toggles need at least 3:1 (SC 1.4.11).
- Tile fill against the empty cell has no requirement, because the value is always printed (SC 1.4.1, information not conveyed by color alone).

### 6.5 Font scaling

- Chrome text uses `sp` and must survive Android 14+ non-linear scaling up to 200%. Score values use `Text(autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp))`; the `autoSize` parameter exists on M3 `Text` from 1.4.0. Labels wrap to two lines and never truncate.
- **Tile numerals are sized in dp derived from the tile's pixel size, ignoring `fontScale`.** They are already the largest size that fits the fixed geometry (section 9.3). Low-vision users still get scaling through Display size (density), and the board grows to fill the window.

### 6.6 Color-blind safety

- The number is always the primary channel. Each palette keeps lightness roughly monotonic within its ramp, so value order still reads in grayscale.
- Measured gate: the minimum adjacent ΔE_OK×100 under Machado-2009 protan/deutan/tritan simulation (severity 1.0) must be **≥ 2.0**, with a target of ≥ 3.0. Section 8 reports it for every palette. All proposed palettes pass at least 2.0; the legacy palette's minimum is 0.9.

---

## 7. Adaptive layout

### 7.1 Window classes

Use `currentWindowAdaptiveInfoV2()` (material3-adaptive 1.3.0), which includes the L and XL width classes. Test with `windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)` and the other breakpoints:
- Width breakpoints: 600 (medium), 840 (expanded), 1200 (large), 1600 (extra-large).
- Height breakpoints: 480 (medium), 900 (expanded).
- `windowPosture`: `isTabletop` and `hingeList`.

### 7.2 Layout modes

| Condition (checked in this order) | Mode | Composition |
|---|---|---|
| Height < 480dp (phone landscape) | **3-column** | `[stats rail] [board] [actions rail]`. Rails are 160–240dp wide; margins 12dp. |
| Width ≥ 840dp | **Board + side panel** | The board is vertically centred. The panel is `clamp(0.32·W, 280, 400)dp` and holds score cards, best, stats, undo, new game and settings. Margins 32dp. |
| Tabletop posture (horizontal hinge) | **Split** | Board in the upper half, controls and a swipe pad in the lower half. Swipes on the lower half drive the board. |
| Book posture (vertical hinge) | **Side panel** | Board on one side of the hinge, panel on the other. The board never crosses the hinge. |
| Otherwise (compact or medium width) | **Stacked** | Header (title + 2 score cards, 112dp), then the board, then the controls bar (80dp). Margins 16dp (compact) or 24dp (medium). |

### 7.3 Board geometry formula

The available space is the window minus `WindowInsets.safeDrawing` minus margins.

```
board = clamp(min(availW, availH - chrome), 280dp, 720dp)     // chrome = 240dp when stacked, 0 in panel modes
gap   = clamp(round(0.03·board), 6dp, 20dp)                   // legacy ratio 15/500 = 0.03
tile  = (board − 5·gap) / 4
tileRadius  = per direction (section 8);  boardRadius = tileRadius + gap   (concentric corners)
```

Worked examples. Insets assumed: portrait 48dp top + 24dp bottom; landscape 24dp top.

| Window | Mode | Board | Gap | Tile |
|---|---|---|---|---|
| 360×800 phone | Stacked | 328dp | 10 | 69.5 |
| 412×915 phone | Stacked | 380 | 11 | 81.2 |
| 915×412 phone landscape | 3-column | 364 | 11 | 77.3 |
| 673×841 foldable inner, portrait | Stacked | 529 | 16 | 112.2 |
| 841×673 foldable inner, landscape | Side panel (280) | 465 | 14 | 98.8 |
| 800×1280 tablet portrait | Stacked | 720 (cap) | 20 | 155 |
| 1280×800 tablet landscape | Side panel (400) | 664 | 20 | 141 |

In very small multi-window panes (below 280dp), allow down to 220dp and hide the title row.

Animate transitions between modes (fold/unfold, rotation in multi-window) with `LookaheadScope` + `Modifier.animateBounds` on the board and panel, using the Expr defaultSpatial spring (0.8/380).

### 7.4 Edge-to-edge, insets and gestures

- On targetSdk 36 running on Android 16+, `windowOptOutEdgeToEdgeEnforcement` "is deprecated and disabled". Call `enableEdgeToEdge()` in `onCreate`, draw the background behind the system bars, and pad content with `Modifier.safeDrawingPadding()`. That covers status bar, navigation bar, display cutout and IME.
- **Gesture conflict:** horizontal swipes that start in the back-gesture zone belong to the system. Keep the board inside `WindowInsets.systemGestures`: the side margin is at least max(16dp, the systemGestures left/right inset). `Modifier.systemGestureExclusion()` exists, but the platform honours at most **200dp of vertical exclusion per edge**, and the board is taller than that. The 200dp cap is documented for the View API; I did not confirm it on a developer.android.com page. Use the exclusion only for the bottom 200dp of the board on edge-hugging layouts.
- **Optional immersive mode** replaces the legacy fullscreen toggle: `WindowInsetsControllerCompat.hide(systemBars())` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. When the navigation bar is stickily hidden, the 200dp exclusion cap does not apply.
- **Predictive back:** on targetSdk 36 it is on by default and `onBackPressed` is not called. Use `BackHandler`/`PredictiveBackHandler` only to dismiss overlays and sheets. Drop "press back again to exit" (`MainActivity.java:113`), because the game state autosaves.
- **Orientation:** from targetSdk 36, orientation, resizability and aspect-ratio restrictions are ignored on displays with smallest width ≥ 600dp. The temporary opt-out `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` is removed at targetSdk 37. **Exception: games declared with `android:appCategory="game"`** (Android 16 and 17 docs). Recommendation: declare `appCategory="game"`, but still support every orientation and size.

### 7.5 Dark theme

- Follow the system by default. The setting is Theme: System / Light / Dark. The legacy app only had a manual night toggle.
- Every palette in section 8 defines a full dark variant. Tiles in dark mode are tuned lower in luminance (about 8% lower L in direction A) so they don't glare, and glows are about 15% stronger.

---

## 8. Visual directions (three options, complete tokens)

All metrics were computed by `scripts/palettes2.py`:
- "Ink contrast" is the WCAG 2.x contrast ratio.
- "vs empty" is the contrast between the tile fill and the empty cell.
- "L" is OKLCH lightness.
- The CVD line gives the minimum adjacent-tile ΔE_OK×100 under each simulation.

The rendered preview is `palette_preview.png` in the scratchpad.

### A. "Classic Refined" (recommended default)

The concept: keep the warm cream → orange → gold identity of the original, and fix the contrast. Changes from legacy:
- Dark ink on light tiles (2, 4, 8, 16 and all golds).
- Deeper coral and red for 32 and 64 so white ink passes.
- Golds pulled apart into an amber progression (ΔE ≥ 3.5 instead of 1.0).

**Light** — background `#FAF8EF`, board `#BBADA0`, empty cell `#CDC1B4`. Board vs background is 2.06:1.

| Tile | Fill | Ink | Ink contrast | vs empty | L |
|---|---|---|---|---|---|
| 2 | `#EEE4DA` | `#5C5248` | 6.08:1 | 1.41 | 0.924 |
| 4 | `#EDDBBE` | `#5C5248` | 5.62:1 | 1.30 | 0.899 |
| 8 | `#F3B27A` | `#4A2A0E` | 7.04:1 | 1.04 | 0.812 |
| 16 | `#F69664` | `#401C06` | 6.83:1 | 1.26 | 0.763 |
| 32 | `#D9613F` | `#FFFFFF` | **3.66:1** (2-digit, large text: AA) | 2.07 | 0.636 |
| 64 | `#DE4A26` | `#FFFFFF` | 4.11:1 | 2.32 | 0.612 |
| 128 | `#F3D98A` | `#45360A` | 8.46:1 | 1.27 | 0.890 |
| 256 | `#F0CC63` | `#45360A` | 7.58:1 | 1.14 | 0.856 |
| 512 | `#EDBF45` | `#3F3006` | 7.42:1 | 1.02 | 0.824 |
| 1024 | `#EAB12C` | `#3D2D04` | 6.87:1 | 1.10 | 0.792 |
| 2048 | `#E6A11A` | `#3A2900` | 6.33:1 | 1.25 | 0.757 |
| ≥4096 | `#3C3A32` | `#FFFFFF` | 11.39:1 | 6.45 | 0.348 |

CVD minimum adjacent ΔE: normal 3.5 (512→1024), protan 4.0, deutan 2.7 (32→64), tritan 3.0.

**Dark** — background `#1B1916`, board `#2C2824`, empty cell `#3A342E`.

| Tile | Fill | Ink | Ink contrast | vs empty | L |
|---|---|---|---|---|---|
| 2 | `#4A433C` | `#F1E9E0` | 8.09:1 | 1.26 | 0.388 |
| 4 | `#605545` | `#F6EDDE` | 6.27:1 | 1.69 | 0.456 |
| 8 | `#D99A64` | `#2E1806` | 7.01:1 | 5.12 | 0.736 |
| 16 | `#DE8455` | `#2A1204` | 6.35:1 | 4.41 | 0.700 |
| 32 | `#C95A3F` | `#FFFFFF` | 4.19:1 | 2.93 | 0.602 |
| 64 | `#C0412A` | `#FFFFFF` | 5.21:1 | 2.36 | 0.552 |
| 128 | `#DCC572` | `#2E2405` | 8.95:1 | 7.17 | 0.825 |
| 256 | `#D9B754` | `#2C2204` | 8.11:1 | 6.34 | 0.790 |
| 512 | `#D6A93D` | `#2A1F02` | 7.41:1 | 5.61 | 0.757 |
| 1024 | `#D39B2A` | `#281D00` | 6.71:1 | 4.96 | 0.725 |
| 2048 | `#D08C1A` | `#251A00` | 6.07:1 | 4.35 | 0.692 |
| ≥4096 | `#F1E8D8` | `#1B1916` | 14.43:1 | 10.10 | 0.934 |

CVD minimum adjacent ΔE: normal 3.5, protan 3.9, deutan 3.2, tritan 3.5.

- **Chrome scheme:** M3 `SchemeContent` from the legacy button color `#8F7A66`, with background and surface overridden to the values above.
  - Light: primary `#6B5946` (white on it 6.68:1), onPrimary `#FFFFFF`, primaryContainer `#85715D`, secondaryContainer `#EFE0D4`, surfaceContainer `#F3EDE9`, surfaceContainerHigh `#EDE7E4`, outline `#7F756D`, outlineVariant `#D1C4BA`.
  - Dark: primary `#DBC2AB`, onPrimary `#3D2D1E`, surfaceContainer `#211F1D`, surfaceContainerHigh `#2C2928`, outline `#9A8F86`.
  - Score cards: `surfaceContainerHigh`, with the value in `onSurface` and the label in `onSurfaceVariant`. This replaces white on `#BBADA0` at 2.19:1.
- **Geometry:** tile radius `clamp(round(0.07·tile), 4, 10)dp`, which is 6dp at an 81dp tile (legacy 3px). Board radius = tile radius + gap. Gap is 3% of the board.
- **Type:** numerals in Google Sans Flex `wght 800, ROND 0` (crisp, close to Clear Sans Bold). The title "2048" is the same face at `wght 850`, 56sp (compact) / 64sp (expanded), tracking −1%.
- **Effects:**
  - Flat tiles.
  - 128–2048 carry the legacy glow: `dropShadow(color = #F3D774, alpha = 0.19 / 0.25 / 0.32 / 0.38 / 0.44, radius = 0.20·tile, spread = 0.03·tile)`. The alphas are 0.8 × legacy; multiply by 1.15 in dark.
  - Inner 1dp highlight `innerShadow(white, alpha 0.05 → 0.11)`.

### B. "Material You" (dynamic, adapts to the wallpaper)

The concept: the ramp is generated at runtime in **HCT** from the seed hue h₀ = `Hct(colorScheme.primary).hue`, with dynamic color on API 31+ and fallback seed `#EDC22E`.
- Hue rotates from primary toward tertiary: **h = h₀ + 6°·i** for i = 0…10. TonalSpot puts tertiary at h₀ + 60°.
- Chroma C = [12, 16, 24, 32, 40, 44, 48, 48, 48, 48, 52] for 2…2048.
- Tones (strictly monotonic, steps of about 6–7):
  - Light: [96, 91, 85, 78, 71, 64, 57, 50, 43, 36, 29]
  - Dark: [30, 36, 43, 50, 57, 64, 71, 78, 85, 91, 96]
- **Ink rule:** pick whichever of tone 10 and tone 98 of the same palette gives more contrast. If neither reaches 4.5:1, use `#000000` or `#FFFFFF`. This guarantees at least 4.5:1 for any seed: the measured minimum is 4.66:1 across 5 seeds × 2 themes.
- Super tile:
  - Light: neutral T12 fill, primary T80 ink.
  - Dark: `TonalPalette(h₀+60°, 48)` T24 fill, T92 ink, plus a 1.5dp border at T80. This gives 9.4:1 ink contrast and ΔE ≥ 61 against 2048.
- Board: neutral-variant T80 (light) / T17 (dark). Empty cell: NV T88 / T24. Background: N T98 / T6.
- Implementation: vendor the Apache-2.0 HCT subset of `material-foundation/material-color-utilities/java`: `hct/Hct`, `Cam16`, `ViewingConditions`, `HctSolver`, `utils/ColorUtils`, `MathUtils`, `palettes/TonalPalette`. That is 7 files, 2,098 lines including comments, with license headers kept; the repo is MIT, so this is compatible. The system tonal slots (section 3.4) are **not** enough on their own.

Fallback seed `#EDC22E`, light (background `#FFF8F0`, board `#CFC6B4`, empty `#E5DCCA`):

| Tile | 2 | 4 | 8 | 16 | 32 | 64 | 128 | 256 | 512 | 1024 | 2048 | ≥4096 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Fill | `#FFF2D8` | `#F1E5BF` | `#DED69C` | `#C6C57A` | `#ABB45B` | `#8FA448` | `#729437` | `#578330` | `#3B7229` | `#1A6223` | `#005023` | `#231F17` |
| Ink | `#211B0A` | `#211B04` | `#1F1C00` | `#1D1D00` | `#1A1D00` | `#171E00` | `#131F00` | `#000000` | `#EDFFE0` | `#ECFFE5` | `#EAFFE9` | `#E2C46D` |
| Contrast | 15.45 | 13.65 | 11.60 | 9.50 | 7.72 | 6.20 | 4.91 | 4.69 | 5.51 | 7.10 | 9.20 | 9.65 |

CVD minimum adjacent ΔE: normal 4.6, protan 4.5, deutan 4.5, tritan 3.8.

Fallback seed `#EDC22E`, dark (background `#16130B`, board `#2E2A1E`, empty `#3E392C`):

| Tile | 2 | 4 | 8 | 16 | 32 | 64 | 128 | 256 | 512 | 1024 | 2048 | ≥4096 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Fill | `#4E4631` | `#5C5537` | `#6D6738` | `#7B7A38` | `#868F3A` | `#8FA448` | `#96BA58` | `#9ECF72` | `#A6E58D` | `#ACF8A7` | `#D2FFD5` | `#00431C` |
| Ink | `#FFF8F0` | `#FFF9EE` | `#FFF9E7` | `#000000` | `#1A1D00` | `#171E00` | `#131F00` | `#0D2000` | `#032100` | `#002204` | `#00210B` | `#A5FCB4` |
| Contrast | 8.88 | 7.13 | 5.47 | 4.68 | 4.92 | 6.20 | 7.73 | 9.50 | 11.64 | 13.60 | 15.55 | 9.44 |

Robustness across other seeds (minimum ink contrast, then minimum adjacent ΔE in light/dark):
- `#1A73E8`: 4.67, 4.6 / 4.70, 3.8
- `#2E7D32`: 4.67, 4.5 / 4.66, 2.5
- `#B3261E`: 4.67, 4.6 / 4.66, 3.1
- `#6750A4`: 4.67, 4.6 / 4.67, 3.0
- The dark-mode minimum ΔE occurs at 2048 → super *before* the super-tile fix above. After the fix it is ≥ 61.
- Caveat: yellow seeds go olive at mid tones, which is an HCT gamut effect. That is acceptable for a "follows your wallpaper" theme.

- **Chrome:** `MaterialExpressiveTheme(colorScheme = dynamic…ColorScheme(ctx))`.
- **Geometry:** tile radius `clamp(round(0.14·tile), 8, 22)dp` (11dp at 81dp, close to the Medium token of 12). Board radius = tile + gap (≈ LargeIncreased 20). Gap 3%.
- **Type:** numerals Google Sans Flex `wght 700, ROND 50`. UI uses the emphasized styles (`displaySmallEmphasized` for the title, `titleLargeEmphasized` for scores).
- **Effects:** no glow. Tonal elevation: board `surfaceContainerHigh`. 2048 gets a 2dp outline at primary T80 (dark) / T40 (light).

### C. "Neon Arcade" (bold option)

The concept: a dark arcade cabinet. The board is always dark, even in the light theme, and tiles glow on a cool-to-hot ramp: indigo → violet → magenta → coral → orange → amber → yellow-lime → white-hot. Lightness rises strictly (OKLCH L 0.36 → 0.97), so order still reads in grayscale and under CVD.

OKLCH spec (L, C, H) for 2…≥4096: (0.36, 0.075, 275), (0.42, 0.11, 288), (0.48, 0.15, 305), (0.54, 0.18, 330), (0.585, 0.19, 355), (0.66, 0.19, 22), (0.72, 0.17, 45), (0.78, 0.16, 65), (0.83, 0.16, 85), (0.88, 0.17, 100), (0.93, 0.19, 112), (0.97, 0.03, 250). Chroma is clipped to the sRGB gamut.

| Tile | Fill | Ink | Ink contrast | vs empty `#25213F` |
|---|---|---|---|---|
| 2 | `#323964` | `#FFFFFF` | 11.02:1 | 1.39 |
| 4 | `#4B4185` | `#FFFFFF` | 8.77:1 | 1.75 |
| 8 | `#7241A0` | `#FFFFFF` | 7.05:1 | 2.18 |
| 16 | `#A53D9F` | `#FFFFFF` | 5.57:1 | 2.75 |
| 32 | `#CB3C81` | `#FFFFFF` | 4.66:1 | 3.29 |
| 64 | `#F0555B` | `#12101F` | 5.50:1 | 4.50 |
| 128 | `#F97C3D` | `#12101F` | 7.13:1 | 5.83 |
| 256 | `#FC9F30` | `#12101F` | 9.06:1 | 7.41 |
| 512 | `#F7BE2B` | `#12101F` | 11.04:1 | 9.03 |
| 1024 | `#F2D935` | `#12101F` | 13.18:1 | 10.78 |
| 2048 | `#ECF33C` | `#12101F` | 15.59:1 | 12.76 |
| ≥4096 | `#EEF6FE` | `#12101F` | 17.19:1 | 14.07 |

CVD minimum adjacent ΔE: normal 6.5, protan 3.5 (8→16), deutan 4.3, tritan 6.0.

- Chrome: dark background `#0E0C1A`, board `#1A1730`, empty `#25213F`. The light theme only changes the app background to `#F3F1FA` (board vs background 15.5:1). Chrome scheme: `SchemeContent` from seed `#5B5BD6`.
  - Dark: primary `#C1C1FF`, onPrimary `#1B119B`, tertiary `#FFAAF5`, surfaceContainer `#1F1F27`.
  - Light: primary `#4241BC`, surfaceContainer `#F0ECF7`.
- **Geometry:** tile radius `clamp(round(0.22·tile), 12, 34)dp` (18dp at 81dp). Board radius = tile + gap (≈ ExtraLarge 28). Gap **3.6%** of the board, so glows have room.
- **Type:** numerals Google Sans Flex `wght 900, ROND 100` (bubbly). Title in uppercase with +2% tracking.
- **Effects:**
  - Tiles ≥ 128 get an outer glow in their own fill color: `dropShadow(color = fill, alpha 0.35 → 0.65 across 128 → 2048, radius 0.25·tile)`.
  - Glassy highlight: `innerShadow(white α 0.18, radius 0.08·tile, offset (0, 0.04·tile))`.
  - The board is an inset well: `innerShadow(black α 0.5, radius 12dp)`.
  - **No infinite "breathing" glows** (WCAG 2.2.2). There is a single pulse on a big merge.

### Recommendation

- **Default:** A (Classic Refined). It is the 2048 visual identity that existing users and store screenshots recognize, now accessible.
- **Settings → Theme:** Classic (default), Material You (only on API 31+ when dynamic color is available; otherwise uses fallback seed `#EDC22E`), Neon. Each has Light, Dark or System variants. The picker shows a live 4×4 mini-board preview of the ramp.
- The tile palette interface stays tiny: `interface TilePalette { fun fill(value: Int): Color; fun ink(value: Int): Color; fun glow(value: Int): Glow?; val board: Color; val empty: Color }`.

---

## 9. Typography and bundled font

### 9.1 Recommendation: Google Sans Flex (OFL 1.1)

- **Source:** `github.com/google/fonts/tree/main/ofl/googlesansflex`. The upstream project is `github.com/googlefonts/googlesans-flex` (named in the OFL.txt copyright line). METADATA `license: "OFL"`; the `OFL.txt` declares no Reserved Font Name, so subsetting does not require renaming.
- **Checked file:** `GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf`, version 5.000, 2000 units per em, 686 glyphs.
  - Axes: `opsz` 1–144 (default 18), `wdth` 25–151 (default 100), `wght` 1–1000 (default 400), `ROND` 0–100, `slnt` −10–0.
  - GSUB features include **`tnum`, `pnum`, `lnum`**, `ss01`.
  - Subsets: latin, latin-ext, vietnamese, plus symbols. **No Cyrillic or Greek.**
- **Why this font:**
  - The `wdth` axis condenses 4–6 digit tiles without shrinking them too far.
  - `ROND` lets each direction pick crisp (A) or soft (C) numerals from one file.
  - `tnum` keeps the score odometer from jittering.
  - It is the variable family of Google's brand typeface, so it suits the Material Expressive look.
- **APK cost** (fontTools 4.68):

| Variant | Size |
|---|---|
| Full file | 3.91 MB |
| Instanced to `wght 400–900`, `wdth 75–100`, `ROND 0–100`, `slnt 0`, `opsz 18` | 364 KB |
| + Latin / Latin-1 subset with features kern, liga, tnum, lnum, pnum, case, ss01, ss02, ccmp, locl, mark, mkmk | **180 KB** |
| Digits-only subset (`0-9 + , . × − space`) | **18 KB** |
| One static instance (`wght 800`, full glyph set) | 136 KB |

  Pass `--name-IDs='*'` to `pyftsubset` to keep the license name records, and ship `OFL.txt` in an "Open-source licenses" screen.
- **Compose usage:** `Font(R.font.google_sans_flex, weight = FontWeight(800), variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(100f), FontVariation.Setting("ROND", 0f)))`. Variation settings apply on **API 26+** (ui-text 1.12.1 `AndroidFont.android.kt:40`); below that the default instance is used. Use `TextStyle(fontFeatureSettings = "tnum")` for the score.
- **UI and body text** use `FontFamily.Default` (the system font). That costs nothing and covers all 22 `values-*` locales, including ru, uk, el, sr, fa and ko. If a missing glyph falls back to a system font, that is acceptable for display strings.

### 9.2 Alternatives (all verified)

| Font | License | tnum | Axes | Coverage | Size |
|---|---|---|---|---|---|
| **Inter** v4.001 (`rsms/inter`) | OFL | yes | `opsz` 14–32, `wght` 100–900 | Latin, Cyrillic, Greek, Vietnamese | 877 KB full; 115 KB Latin subset |
| **Clear Sans** (`intel/clear-sans`) — the legacy font | **Apache-2.0** | **no** (digit widths 1204/1280 of 2048) | static | Latin | 270 KB (Bold) — maximum fidelity for direction A |
| Outfit | OFL | yes | `wght` 100–900 | Latin | 111 KB — geometric, good for C |
| Space Grotesk | OFL | yes | `wght` 300–700 | Latin, Vietnamese | 137 KB |
| Rubik | OFL | yes | `wght` 300–900 | Latin, Cyrillic, Hebrew | 360 KB — rounded |
| Roboto Flex | OFL | no feature (digits are tabular by default) | 13 axes | Latin, Cyrillic, Greek | 1.79 MB |
| Nunito | OFL | no feature (digits are tabular by default) | `wght` 200–1000 | Latin, Cyrillic | 277 KB — rounded |
| DM Sans | OFL | no | `opsz`, `wght` | Latin | 240 KB |

### 9.3 Numeral sizing in tiles (in dp relative to tile side t; fontScale ignored)

Measured advance of the `tnum` digit at `wght 800`: 0.668em (`wdth 100`), 0.635em (`wdth 87.5`), 0.603em (`wdth 75`). Cap height is 0.716em. The usable width is 0.84·t.

| Digits | Example | Font size | `wdth` | Width used | Legacy ratio |
|---|---|---|---|---|---|
| 1–2 | 2–64 | **0.50·t** | 100 | ≤ 0.67·t | 0.52 |
| 3 | 128–512 | **0.40·t** | 100 | 0.80·t | 0.42 |
| 4 | 1024–8192 | **0.32·t** | 87.5 | 0.81·t | 0.33 |
| 5 | 16384–65536 | **0.27·t** | 75 | 0.81·t | 0.28 |
| 6 | 131072 | **0.23·t** | 75 | 0.83·t | (10px on mobile) |

- Tiles use proportional `lnum` digits (`"lnum"`): a narrow "1" centres better.
- Score and best values use `"tnum, lnum"`.
- Letter spacing −0.02em on numerals.
- Line height equals the font size, with `LineHeightStyle(Alignment.Center, Trim.Both)` for true optical centring.

### 9.4 UI type scale (shared by all directions)

| Element | Style |
|---|---|
| Title "2048" | Google Sans Flex 56sp (compact) / 64sp (expanded), weight per direction |
| Score label | `labelMediumEmphasized` 12/16 Bold, uppercase, +0.5sp |
| Score value | Google Sans Flex 24sp `wght 750`, tnum, `TextAutoSize.StepBased(14.sp, 28.sp)` |
| "+N" | 16sp `wght 800`, tnum |
| Overlay title | `displaySmallEmphasized` 36/44 |
| Body and buttons | M3 defaults (`labelLarge` 14/20 Medium; emphasized: Bold) |

---

## 10. MOTION_SPEC draft — signature moments with exact numbers

Kotlin token object. Values are the M3 tokens from section 3.1 plus game-specific constants.

```kotlin
object GameMotion {
    // M3 tokens (androidx Standard/ExpressiveMotionTokens)
    fun <T> slide(threshold: T? = null) = spring(dampingRatio = 0.9f, stiffness = 1400f, visibilityThreshold = threshold) // standard.fastSpatial
    fun <T> popSpatial() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)      // expressive.fastSpatial
    fun <T> overlaySpatial() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)  // expressive.defaultSpatial
    fun <T> slowSpatial() = spring<T>(dampingRatio = 0.8f, stiffness = 200f)     // expressive.slowSpatial
    fun <T> fastEffects() = spring<T>(dampingRatio = 1f, stiffness = 3800f)
    fun <T> defaultEffects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)
    fun <T> slowEffects() = spring<T>(dampingRatio = 1f, stiffness = 800f)
    // Game constants
    const val SPAWN_DELAY_MS = 90L          // ≈ slide t90 (91 ms); legacy 100 ms
    const val MERGE_REVEAL_MS = 80L         // merged tile appears; sources ≈ 85% there
    const val GHOST_REMOVE_MS = 200L        // or when the next move commits
    val POP_IMPULSE = floatArrayOf(6f, 8f, 10f, 12f)  // 1/s for max merge ≤64, 128–512, 1024, ≥2048
    const val NUDGE_VELOCITY_DP_S = 330f
    val NUDGE = spring<Float>(dampingRatio = 0.3f, stiffness = 1800f)
}
```

### 10.1 Timeline of one move (full motion)

```
t=0     swipe commit → model updated → BoardUi(moveId+1) emitted; haptic (Full mode only: SegmentFrequentTick)
t=0     slides start (std fastSpatial 0.9/1400): t90 = 91 ms, t99 = 139 ms, settled ≤ 271 ms (3 cells)
t=80    merged tiles revealed at destination (z above ghosts) + pop impulse; merge haptic
t=90    spawn starts (scale 0.6→1 expr fastSpatial, alpha 0→1 fastEffects)
t=80    "+N" float starts; score odometer rolls
t=200   ghosts removed
t≈400   everything has visually settled (pop tail amplitude < 1.5%)
```

### 10.2 Slide

- `pos.animateTo(target, GameMotion.slide(Offset(0.5f, 0.5f)))`, i.e. damping **0.9**, stiffness **1400**, threshold 0.5px.
- The spring is critically damped in practice (0.1% overshoot), so there is no visible bounce into neighbouring cells. Every tile uses the same spring, regardless of distance.
- Interruption: `snapTo` then animate (section 4.4).
- Why not tween: the legacy used 100ms ease-in-out. The spring reaches 90% at 91ms, which feels as fast, settles naturally and keeps velocity if fluid mode is enabled.

### 10.3 Spawn

- Starts at **+90ms** (`SPAWN_DELAY_MS × durationScale`).
- **scale 0.6 → 1.0** with expr fastSpatial (0.6/800): t90 83ms, **peak 1.037**, settled about 390ms (threshold 0.001).
- **alpha 0 → 1** with fastEffects (1.0/3800): t99 about 110ms.
- `transformOrigin` is the tile centre.
- Legacy for comparison: scale 0 → 1 over 200ms with a 100ms delay.

### 10.4 Merge pop

1. Ghost sources slide into the merge cell (10.2).
2. At **+80ms**, show the merged tile (alpha 1, new color) on top.
3. `scale.animateTo(1f, popSpatial(), initialVelocity = v0)` starting **from 1.0**, so the impulse creates the overshoot. The peak comes at about **41ms** after the start.

| Max merged value | v0 (1/s) | Peak scale |
|---|---|---|
| ≤ 64 | 6 | **1.104** |
| 128–512 | 8 | **1.139** |
| 1024 | 10 | **1.174** |
| ≥ 2048 | 12 | **≈1.21** (linear extrapolation) |

The next extremum is an undershoot of about 0.99, and the tile settles within 0.001 at about 390–410ms.

- **Sheen** (≥128): a white overlay drawn in the tile's `drawBehind` at alpha **0.35 → 0** with fastEffects (about 110ms).
- **Glow pulse** (≥512): the `dropShadow` alpha rises to base + 0.25 instantly, then returns to base with defaultEffects (1.0/1600, t99 about 168ms).
- Legacy for comparison: scale 0 → 1.2 → 1 over 200ms with a 100ms delay.

### 10.5 Score "+N" float (one per move: sum of merges)

- Origin: the score value baseline.
- **translationY 0 → −28dp** with `tween(650, easing = EmphasizedDecelerate (0.05, 0.7, 0.1, 1.0))`.
- **alpha** holds at 1 for 250ms, then 1 → 0 with `tween(400, easing = Linear)`.
- **scale 0.8 → 1.0** with expr fastSpatial.
- Color is `primary`; for merges ≥ 512 use the palette's 512–2048 fill tint.
- At most 3 alive at once; the oldest is removed early.
- Legacy for comparison: 600ms ease-in, 75px rise.

### 10.6 Score odometer

- Each digit is `AnimatedContent(targetState = digit)`.
- Enter: `slideInVertically { it }` with the slide spring (0.9/1400) + `fadeIn(fastEffects)`.
- Exit: `slideOutVertically { -it }` + `fadeOut(fastEffects)`.
- Digits are `tnum`, so width is constant. No stagger.
- When the value grows by a whole digit, the new leading digit enters with `expandHorizontally(overlaySpatial)`.

### 10.7 Invalid move (nudge in the swipe direction)

- Board-level `Animatable(0f)` driving `offset { }`: `animateTo(0f, spring(0.3f, 1800f), initialVelocity = 330.dp.toPx())` along the swipe direction.
- Measured: **+5.2dp at 32ms**, −1.93dp at 109ms, +0.72dp at 187ms, settled within 0.1dp at **393ms**.
- Haptic `Reject`, at most 1 per 400ms.
- The direction-specific nudge says "nothing moves this way" better than a generic shake. Legacy had no feedback at all.

### 10.8 New best (first crossing of the stored best in a game; skipped when the stored best is 0)

- BEST card container: `surfaceContainerHigh → tertiaryContainer` with defaultEffects (1.0/1600).
- Label: "BEST" → "NEW BEST" with `AnimatedContent`: fadeIn and slideIn 8dp, slide spring.
- Badge behind the label is a 10-point soft star (`RoundedPolygon.star(10, innerRadius = 0.82f, rounding = CornerRounding(0.15f))`, or `MaterialShapes.SoftBurst`):
  - scale 0 → 1 with expr fastSpatial (peak about 1.04)
  - rotation 0 → 45° with `tween(900, EmphasizedDecelerate)`
  - after 1200ms, alpha → 0 with `tween(300)`
- Sparkles: **8** four-point stars, 6dp, from the card centre. Speed U(120, 220) dp/s, radial, no gravity. Lifetime 600ms, alpha → 0 over the last 250ms.
- Haptic: `GestureThresholdActivate`.
- Total: 1.5s, and no repeat within the same game.

### 10.9 Win (first tile reaching 2048)

| Time | What happens |
|---|---|
| T0 (merge reveal) | Pop with v0 = 12 (peak about 1.21) |
| T0 + 60ms | **Glow ring:** stroked rounded rect around the tile. Scale 1.0 → 1.8, stroke 6dp → 0, alpha 0.9 → 0, `tween(700, EmphasizedDecelerate)`. |
| T0 + 120ms | **Confetti burst** from the tile centre (spec below). Haptic `Confirm`; second `Confirm` at T0 + 270ms. |
| T0 + 600ms | Scrim alpha 0 → **0.60** (surface color), `tween(300, Standard)`. Overlay card: scale **0.92 → 1** with expr defaultSpatial (0.8/380, overshoot 1.4%) + alpha 0 → 1 with fastEffects. Buttons ("Keep going" filled, "New game" tonal) stagger **+50ms** each and are interactive at T0 + 800ms. |
| T0 + 600ms | **Hero badge** in the card: "2048" on a shape that morphs **Circle → Cookie12Sided** (`Morph`, progress 0 → 1, expr defaultSpatial) while scaling 0 → 1 with expr fastSpatial. Rotation 0 → 60° with `tween(5000, Linear)`, then it **stops** (WCAG 2.2.2). |

Legacy for comparison: overlay fade 800ms after a 1200ms delay.

**Confetti** (one `Canvas` overlay with structure-of-arrays state and its own `withFrameNanos` loop; dt clamped to ≤ 1/30s):
- Count: **96** on compact, **144** on expanded, cap 160.
- Shapes: 60% rects 6×10dp (corner 1.5dp), 25% circles r = 3dp, 15% four-point stars 8dp (pre-built `Path`).
- Emission: angle U(−150°, −30°) (an upward fan), speed U(700, 1400) dp/s.
- Gravity **1800 dp/s²**. Drag v ← v·e^(−2.2·dt), giving a terminal velocity of about 818 dp/s.
- Flutter: x += A·sin(ωt + φ) with A = 12dp and ω U(6, 12) rad/s. Paper flip: scaleY = cos(ω_f·t) with ω_f U(8, 16) rad/s.
- Spin U(−540, 540) °/s.
- Lifetime U(1.4, 2.2)s. Scale-in 0.4 → 1 over the first 80ms; alpha → 0 over the last 350ms.
- Colors: palette fills for 128, 256, 512, 1024 and 2048, plus `primary` and `tertiary`.
- Draw: `withTransform({ translate; rotate; scale }) { drawRect / drawCircle / drawPath }`. No allocation per frame.
- The loop ends when every particle is dead, after at most 2.4s.
- Reduced motion: no confetti, ring, morph or rotation; the card fades in over 150ms.

### 10.10 Game over

| Time | What happens |
|---|---|
| T0 = commit + 300ms | Lets the final spawn be seen. Haptic `Reject`. |
| T0 → T0 + 600ms | Board **desaturates 1.0 → 0.2**, via `ColorMatrix().setToSaturation(s)` applied as each tile's `ColorFilter` in `drawBehind` (works on every API level), `tween(600, Standard (0.2, 0, 0, 1))` |
| T0 → T0 + 180ms | **Deflate ripple:** tile scale 1 → 0.94 with std defaultSpatial (0.9/700), staggered **30ms per Manhattan step** from the last-spawned tile (≤ 6 steps) |
| T0 + 450ms | Scrim alpha 0 → **0.78**, `tween(300, Standard)` |
| T0 + 500ms | Card (title "Game over", score, best, **Undo**, New game) enters: scale 0.92 → 1 with expr defaultSpatial + alpha with fastEffects. Buttons stagger +50ms. **Interactive at T0 + 700ms.** |

- Optional on API 31+: board `RenderEffect.createBlurEffect(0 → 8dp)` with `tween(600)`.
- Legacy for comparison: 2.0s before the overlay was interactive.

### 10.11 Undo and new game

- **Undo (P0):** crossfade. Outgoing tiles go alpha 1 → 0 and incoming tiles 0 → 1, both with fastEffects (about 110ms). The undo icon rotates −360° with `tween(300, EmphasizedDecelerate)`.
- **Undo (P1, "rewind"):** needs tile IDs in the snapshots.
  - Moved tiles slide back with the slide spring.
  - A merged tile fades out with fastEffects, and its two sources appear at the merge cell and slide back.
  - The spawned tile goes scale 1 → 0.6 and alpha → 0 with fastEffects.
- **New game:** current tiles go scale 1 → 0.6 and alpha → 0 with fastEffects, staggered **15ms** in reading order (≤ 240ms in total). Then the two start tiles spawn with the 10.3 spec, 60ms apart.

### 10.12 Overlay and layout motion

- Overlay card: expr defaultSpatial (0.8/380). Scrim: `tween(300, Standard)`.
- Sheets (settings, stats): M3 `ModalBottomSheet` defaults, which follow the theme's motion scheme on 1.5.0.
- Panel and score card re-layout on size change: `animateBounds` with expr defaultSpatial.
- Theme switch: colors crossfade with slowEffects (1.0/800, about 237ms to 99%), via `animateColorAsState` on palette tokens, read in draw.

---

## 11. Risks and open questions for the plan

1. **material3 version.** 1.5.0 is beta. If the plan stays on BOM 2026.09.00 (1.4.0), `MaterialExpressiveTheme`, `MotionScheme` and the emphasized typography are not reachable: they are internal. Our own `GameMotion` and `Typography` tokens cover this. `ButtonGroup`, `FloatingToolbar` and shape-morph buttons would then not be available. **The dependency track should decide.**
2. **ARR frame-rate voting** comes from reading compose-ui 1.12.1 sources (section 4.2). Verify on an Android 15+ device with ARR that tile slides reach 120Hz, using `dumpsys SurfaceFlinger` or the GPU profiler. If not, apply `preferredFrameRate(High)` during animations.
3. **Gesture-exclusion cap (200dp)** is taken from a secondary source (the View API docs mirrored on learn.microsoft.com). Confirm against the platform reference.
4. **Performance targets** (P95 ≤ 6ms CPU, 160 particles costing well under 1ms of draw) are proposals. They need Macrobenchmark validation.
5. **Google Sans Flex coverage:** no Cyrillic or Greek. Numerals and the "2048" title are Latin, so this is fine. Translated headings fall back to the system font. If the plan wants one family for every string, use **Inter** for UI text at +115 KB for Latin, more with Cyrillic and Greek.
6. **The dynamic theme needs vendored HCT** (2,098 lines, Apache-2.0) or an OKLCH approximation (about 80 lines; contrast is still guaranteed by checking the ink at runtime). Using only the system tonal slots produces duplicate tiles for some seeds (measured).
7. `android:appCategory="game"` exempts the app from the large-screen orientation override, and probably also affects Play Store and Game Dashboard behavior. That should be a conscious product decision.

---

## 12. Sources

- androidx token sources (androidx-main), all under `https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/`:
  - `tokens/ExpressiveMotionTokens.kt`
  - `tokens/StandardMotionTokens.kt`
  - `MotionScheme.kt`
- Google Maven metadata and source jars (all retrieved 2026-10-28):
  - `https://dl.google.com/android/maven2/androidx/compose/material3/material3/maven-metadata.xml`
  - `https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom`
  - `material3-android-1.4.0-sources.jar`, `material3-android-1.5.0-beta02-sources.jar`
  - `ui-android-1.12.1-sources.jar`, `ui-text-android-1.12.1-sources.jar`
  - `animation(-core)-android-1.12.1-sources.jar`
  - `adaptive-android-1.3.0-sources.jar`, `window-core-jvm-1.5.1-sources.jar`
  - `graphics-shapes-android-1.1.0-sources.jar`, `core-1.19.1-sources.jar`
  - `runtime-android-1.13.0-beta02-sources.jar`
- Local SDK: `/opt/android-sdk/platforms/android-36` and `android-37.0` (`android.jar`, `android-stubs-src.jar`): `HapticFeedbackConstants`, `View.announceForAccessibility` `@Deprecated`, `UiModeManager.getContrast` (API 34), `android.R.color.system_*`.
- Compose material3 release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- Customize animations (spring vs tween, interruption): https://developer.android.com/develop/ui/compose/animation/customize
- Compose performance best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Strong skipping: https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Macrobenchmark metrics: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics
- JankStats: https://developer.android.com/topic/performance/jankstats
- Haptic feedback: https://developer.android.com/develop/ui/views/haptics/haptic-feedback
- Haptics principles: https://developer.android.com/develop/ui/views/haptics/haptics-principles
- Android 16 behavior changes: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17 behavior changes: https://developer.android.com/about/versions/17/behavior-changes-17
- Android 17 orientation and resizability: https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored
- Android Developers Blog, resizability (Feb 2026): https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html
- `systemGestureExclusion` reference: https://developer.android.com/reference/kotlin/androidx/compose/foundation/systemGestureExclusion.modifier
- 200dp cap (secondary source): https://learn.microsoft.com/dotnet/api/android.views.view.systemgestureexclusionrects
- `LiveRegionMode`: https://developer.android.com/reference/kotlin/androidx/compose/ui/semantics/LiveRegionMode
- Reduced motion on Android (community): https://proandroiddev.com/making-basicmarquee-modifier-more-accessible-8b3737307dee
- Fonts:
  - https://github.com/google/fonts/tree/main/ofl/googlesansflex (METADATA.pb, OFL.txt)
  - https://github.com/googlefonts/googlesans-flex
  - https://github.com/rsms/inter
  - https://github.com/intel/clear-sans
- material-color-utilities (Apache-2.0): https://github.com/material-foundation/material-color-utilities
- WCAG 2.2 (SC 1.4.1, 1.4.3, 1.4.11, 2.2.2, 2.3.1, 2.3.3, 2.5.8): https://www.w3.org/TR/WCAG22/
- OKLab: https://bottosson.github.io/posts/oklab/
- Machado, Oliveira & Fernandes (2009), CVD simulation: https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html
- Legacy code: `assets/2048/style/main.scss`, `assets/2048/style/main.css`, `assets/2048/js/html_actuator.js`, `assets/2048/js/keyboard_input_manager.js`, `assets/2048/js/nightmode.js`, `assets/2048/js/game_manager.js`, `src/com/uberspot/a2048/MainActivity.java`.
