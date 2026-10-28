# 02 — Legacy web game audit (`assets/2048`)

Track: legacy-game. Audited 2026-10-28.

**Source:** submodule `assets/2048`, remote `https://github.com/uberspot/2048.git`, HEAD `7a477eb` (2019-01-10, "Merge pull request #9 from TacoTheDank/master").

**Upstream:** I cloned `gabrielecirulli/2048` at HEAD `478b6ec`. The fork's merge-base with upstream is `fc1ef4f` ("Remove Adsense code"), and the fork carries 33 commits on top of it.

All `file:line` references are relative to `assets/2048/` unless prefixed with the Android shell path.

**Verification:** I loaded the game logic verbatim (`tile.js`, `grid.js`, `local_storage_manager.js`, `game_manager.js`) into a Node 22 `vm` context, with a stub actuator, a stub input manager and a seeded `Math.random`. That harness confirmed every logic claim marked **[verified]**. The harness lives in the scratchpad at `.../scratchpad/harness/run.js` and `run2.js`; it is scratch work and is not meant to be committed. Claims marked **[reasoned]** come from reading the code and spec semantics and were not run on a device.

> **Source-of-truth warning:** `style/main.css` (what actually ships) was **hand-edited** after compilation. It has drifted from `style/main.scss` in about 20 places: title size, score padding, margins, the undo button, the confirm/undo overlay variants, intro width, and others. The SCSS was only updated for two of the fork's changes (tap-highlight and `translateZ`). **Use `main.css` values for parity, and use `main.scss` only to understand the formulas.** Ruby Sass also truncated colour channels, so recomputing `mix()` gives values about 1/255 off from the canonical shipped hex. For example, recomputing 4 gives `#eee1c9`, but `#ede0c8` is what ships. Use the compiled hex values.

---

## 1. Game rules as implemented

| Rule | Implementation | Ref |
|---|---|---|
| Grid size | 4×4 (`new GameManager(4, …)`), started inside a `requestAnimationFrame` | `js/application.js:2-4` |
| Coordinates | `x` = column (0 = left), `y` = row (0 = top). `grid.cells` is **column-major**: `cells[x][y]` | `js/grid.js:7-19`, `:80-86` |
| Start tiles | 2 (`this.startTiles = 2`), each placed by `addRandomTile()` | `js/game_manager.js:7`, `:106-110` |
| Spawn value | `Math.random() < 0.9 ? 2 : 4`, so P(2) = 0.9 and P(4) = 0.1 | `js/game_manager.js:115` |
| Spawn location | Uniform random over all empty cells: `cells[Math.floor(Math.random()*cells.length)]`. The candidate list is built in x-major order, which has no effect because selection is uniform | `js/grid.js:37-55` |
| Spawn guard | Only if `cellsAvailable()` | `js/game_manager.js:114` |
| Direction codes | `0` Up `{0,-1}`, `1` Right `{1,0}`, `2` Down `{0,1}`, `3` Left `{-1,0}` | `js/game_manager.js:240-250` |
| Traversal order | `x` and `y` each run `0..3`. An axis is **reversed** when that vector component is `+1`. The outer loop is `x` and the inner loop is `y`. Result: Right processes x = 3→0; Left x = 0→3; Down y = 3→0; Up y = 0→3. The tile nearest the leading wall is always processed first | `js/game_manager.js:192-193`, `:253-266` |
| Slide | `findFarthestPosition` steps along the vector until it leaves the board or hits an occupied cell. It returns `{farthest, next}` | `js/game_manager.js:268-282` |
| Merge | When `next` exists, has the same value, and `!next.mergedFrom`: create `new Tile(next, value*2)` with `mergedFrom=[tile,next]`, insert it (replacing `next`), remove `tile`, and set `tile.updatePosition(next)` (animation only) | `js/game_manager.js:202-216` |
| Once-per-move merge | The merged tile carries `mergedFrom`, so it cannot absorb a third tile in the same move. `prepareTiles()` clears `mergedFrom` and snapshots `previousPosition` before every move | `js/game_manager.js:159-166`, `:189` |
| Merge results **[verified]** | Left / Right on row `2,2,2,2` → `4,4,_,_` / `_,_,4,4` (+8). `2,2,2,_` → `4,2,_,_` / `_,_,2,4` (+4). `4,2,2,_` → `4,4,_,_` (+4, the 4s do **not** chain). `8,_,8,8` → `16,8,_,_` / `_,_,8,16` (+16). `2,2,4,4` → `4,8,_,_` (+12) | harness T6 |
| Scoring | `score += merged.value` for every merge, so a move scores the sum of all newly created tiles. There is no bonus or combo multiplier | `js/game_manager.js:213` |
| Win | `if (merged.value === 2048) self.won = true`. This is an exact match, so later 4096+ merges never set it again **[verified T10]** | `js/game_manager.js:216` |
| Input lock on win | `move()` returns early if `isGameTerminated()`, defined as `over \|\| (won && !keepPlaying)`. After a 2048 is created, every swipe is ignored until the player taps **Keep going** or restarts **[verified T9]** | `js/game_manager.js:74-76`, `:180` |
| Keep playing | Sets `keepPlaying = true`, clears the overlay, then calls `actuate()`. The `actuate()` call is a fork addition: it persists the flag and pushes an undo snapshot | `js/game_manager.js:67-71` |
| No-op move | `moved` is set only if some tile's cell changed. When `moved` is false there is **no spawn, no save, no undo push and no re-render** **[verified T5]** | `js/game_manager.js:186`, `:221-236` |
| After a successful move | `addRandomTile()`, then `if (!movesAvailable()) over = true`, then `actuate()`. The game-over check runs **after** the spawn | `js/game_manager.js:228-236` |
| Game-over detection | `movesAvailable = cellsAvailable() \|\| tileMatchesAvailable()`. `tileMatchesAvailable` checks each tile against only its Up (`y-1`) and Right (`x+1`) neighbours (`direction < 2`). This is a fork optimisation from 4 directions; it stays complete because every adjacent pair is visited from one side | `js/game_manager.js:284-314` (`:299`) |
| Simultaneous win and loss | If the 2048-creating move also leaves no moves, both `won` and `over` are true. The actuator shows **"You win!"** (fork fix `6be2116`). The state is already cleared because `over` is true. Tapping **Keep going** re-actuates and then shows **"Game over!"** | `js/html_actuator.js:27-41` |
| Restart | `clearGameState()` removes `gameState` and `gameStatesStack`, then `clearMessage()` and `setup()`. The best score survives | `js/game_manager.js:20-24` |
| Restart entry points | **New Game** button → "Start a new game?" with OK/Cancel. **Try again** (overlay) and the **R** key restart **immediately, with no confirmation** | `js/keyboard_input_manager.js:65-78` |
| Undo entry points | **Undo** button → "Undo the current move?" with Undo/Cancel → `undoMove()`. There is no keyboard shortcut | `js/keyboard_input_manager.js:74-76`, `js/game_manager.js:27-53` |

## 2. Persistence

- **Backend:** `window.localStorage`, enabled through `settings.setDomStorageEnabled(true)` (`src/com/uberspot/a2048/MainActivity.java:81`). If the storage probe throws, the game falls back to an in-memory `window.fakeStorage` (`js/local_storage_manager.js:1-41`).
- **Keys** (`js/local_storage_manager.js:22-24`):

| Key | Type | Written when | Cleared when |
|---|---|---|---|
| `bestScore` | string holding a number (e.g. `"6144"`) **[verified T11]** | in `actuate()` if `getBestScore() < score`. String/number coercion makes the comparison numeric. Never written while score is 0 | never (survives restart and game over) |
| `gameState` | JSON object | every `actuate()` while `!over` | on game over (`actuate()` with `over`), restart |
| `gameStatesStack` | JSON array of **JSON strings** (double-encoded) | every `actuate()` while `!over` (push) | on game over, restart |

- **`gameState` schema** (sample captured from the harness):
  ```json
  {"grid":{"size":4,"cells":[[null,null,null,null],[null,null,null,null],
    [null,{"position":{"x":2,"y":1},"value":2},null,null],
    [{"position":{"x":3,"y":0},"value":2},null,null,{"position":{"x":3,"y":3},"value":2}]]},
   "score":0,"over":false,"won":false,"keepPlaying":false}
  ```
  `cells[x][y]` is column-major and each tile is `{position:{x,y}, value}`. On restore, `Grid.fromState` rebuilds `Tile(tile.position, tile.value)`, so `previousPosition` and `mergedFrom` are lost (`js/grid.js:21-34`, `js/tile.js:19-27`, `js/game_manager.js:148-156`).
- **Restore:** `setup()` loads `gameState` if it is present (`js/game_manager.js:79-103`). A saved won-but-not-continued state (`won:true, keepPlaying:false`) shows the win overlay again on reload.
- **Undo stack:** `pushGameState` shifts the oldest entry when `length > 50` before pushing, so the stack peaks at **51 entries** (the current state plus 50 undo levels) **[verified T7]** (`js/local_storage_manager.js:93-106`).
- **`popGameState`:**
  1. Pops the top entry.
  2. If the top entry is a substring of the current `gameState` string (`indexOf`), it pops once more.
  3. If the stack is now empty, it pushes the result back, so the stack never empties.

  `js/local_storage_manager.js:68-91`
- **What `undoMove` does:** it restores `grid`, `score`, `over`, `won` and `keepPlaying`, then calls `actuator.actuate()` **directly**. It does **not** call `setGameState()` and does not touch the best score (`js/game_manager.js:27-53`).
- **Game over:** the save is wiped immediately (`js/game_manager.js:128-131`), so after a loss the next launch starts a fresh game.
- **Performance:** every move parses and re-serialises the whole stack (up to 51 × ~300 B) twice: once in push and once in `getLength`.
- **Migration:** the new app uses a different `applicationId` (`com.clayboicardi.game2048`), so it **cannot read** the old app's WebView localStorage (`/data/data/com.uberspot.a2048/app_webview/...`). No legacy save or best-score migration is possible. Every user starts fresh.

## 3. Visuals (from compiled `style/main.css`; SCSS formulas noted)

### 3.1 Tile palette

Formula (`style/main.scss:336-404`):
- `$tile-color:#eee4da`, `$tile-gold-color:#edc22e`.
- `base = mix(gold, #eee4da, (exp-1)/10)`.
- Tiles 8–64 additionally get `mix(special, base, 55%)`, with specials 8 `#f78e48`, 16 `#fc5e2e`, 32 `#ff3333`, 64 `#ff0000`.
- Text is `#f9f6f2` (`$bright-text-color`) from 8 upward. Tiles 2 and 4 inherit `#776e65`.
- Glow, only on non-special tiles: `box-shadow: 0 0 30px 10px rgba(#f3d774, g/1.8), inset 0 0 0 1px rgba(#fff, g/3)`, where `g = max(exp-4,0)/7`. `#f3d774` is `lighten(#edc22e,15%)`.

| Value | Background (shipped) | Text | Font desktop / mobile (≤520px) | Outer glow α / inset α | Text contrast | css ref |
|---|---|---|---|---|---|---|
| 2 | `#eee4da` | `#776e65` | 55px / 35px | 0 / 0 | 3.98 | `main.css:362-364` |
| 4 | `#ede0c8` | `#776e65` | 55 / 35 | 0 / 0 | 3.83 | `:365-367` |
| 8 | `#f2b179` | `#f9f6f2` | 55 / 35 | none | **1.72** | `:368-370` |
| 16 | `#f59563` | `#f9f6f2` | 55 / 35 | none | 2.08 | `:371-373` |
| 32 | `#f67c5f` | `#f9f6f2` | 55 / 35 | none | 2.45 | `:374-376` |
| 64 | `#f65e3b` | `#f9f6f2` | 55 / 35 | none | 2.96 | `:377-379` |
| 128 | `#edcf72` | `#f9f6f2` | 45 / 25 | 0.2381 / 0.14286 | **1.42** | `:380-387` |
| 256 | `#edcc61` | `#f9f6f2` | 45 / 25 | 0.31746 / 0.19048 | 1.45 | `:388-395` |
| 512 | `#edc850` | `#f9f6f2` | 45 / 25 | 0.39683 / 0.2381 | 1.50 | `:396-403` |
| 1024 | `#edc53f` | `#f9f6f2` | 35 / **15** | 0.47619 / 0.28571 | 1.54 | `:404-411` |
| 2048 | `#edc22e` | `#f9f6f2` | 35 / **15** | 0.55556 / 0.33333 | 1.58 | `:412-419` |
| >2048 (`.tile-super`) | `#3c3a32` = `mix(#333,#edc22e,95%)` | `#f9f6f2` | 30 / **10** | none | 10.57 | `:420-426` |

- Every tile above 2048 (4096, 8192, …, 131072) shares the **single** colour `#3c3a32`. The class list is `tile tile-4096 tile-super …` and `.tile-4096` has no rule (`js/html_actuator.js:68`).
- Mobile font cascade: the mobile rule `.tile .tile-inner{font-size:35px}` (`main.css:829-830`, specificity 0,2,0) loses to the per-value rules (0,3,0). That produces the 35/25/15/10 ladder in the table.
- All tile text is **bold** (700) and horizontally centred. Vertical centring comes from `line-height` = tile size (`main.css:257-264`, `:355-361`).
- Accessibility: white-on-gold tiles (8 and 128–2048) are 1.4–1.7:1 contrast and fail WCAG even at the 3:1 large-text bar. Gold tiles against the board are 1.29–1.43:1.

### 3.2 Board, grid and geometry

| Token | Desktop (>520px) | Mobile (≤520px, **what phones actually render**) | Ref |
|---|---|---|---|
| Breakpoint | `@media screen and (max-width: 520px)` (`$field-width + 20px`) | | `main.scss:10`, `main.css:586` |
| Board (`.game-container`) | 500×500, `padding:15px`, `#bbada0`, radius **6px**, `margin-top:10px` (SCSS: 40) | 280×280, `padding:10px`, radius 6px, `margin-top:15px` (SCSS: 17) | `main.css:138-156`, `:623-641` |
| Empty cell | 106.25px square, radius 3px, `rgba(238,228,218,.35)`, which composites to **`#cdc0b4`** on the board | 57.5px | `main.css:243-251`, `:725-733` |
| Gap | 15px (3.0% of the field) | 10px (3.57%) | `main.scss:5`, `:480` |
| Tile element | `ceil` → 107px, radius 3px, `transform:translateZ(0)` | 58px | `main.css:257-264`, `:739-746` |
| Tile offsets | `floor((tile+gap)*(n-1))` → 0, 121, 242, 363 px | 0, 67, 135, 202 px | `main.scss:301-309`, `main.css:266-345`, `:748-827` |
| Ratios (for responsive native) | tile/field 0.2125, gap/tile 0.141, cell radius/tile 0.028, board radius/field 0.012 | gap/tile 0.174 | derived |
| Z-order | grid z1 < tile-container z2 < tile-inner z10 < merged tile-inner z20 < message z100 | | `main.css:230-255`, `:360`, `:533`, `:165` |

The viewport is `width=device-width`. Phones are typically 360–412 CSS px wide, so Android users almost always get the **fixed 280px mobile board**; it does not scale to fill the screen. Only screens wider than 520px (tablets) get the 500px board. The viewport meta also includes `target-densitydpi=160dpi`, which is likely ignored by modern WebView (unverified) (`index.html:17`).

### 3.3 Page chrome and typography

- **Page:** `html, body` background `#faf8ef`, text `#776e65`, font stack `"Clear Sans","Helvetica Neue",Arial,sans-serif`, 18px desktop / 15px mobile (`main.css:2-8`, `:587-588`). Mobile body: `margin:20px 0; padding:0 20px` (`:590-592`). The fork removed the desktop `body{margin:80px 0}`. `.container` is 500px wide, or 280px on mobile (`:116-118`, `:598-600`).
- **Clear Sans:**
  - Three `@font-face` weights: Light 200, Regular `normal`, Bold 700 (`style/fonts/clear-sans.css:1-29`).
  - Formats: eot, svg and woff only. There is no ttf or woff2, and the WOFF files contain TrueType `glyf` outlines.
  - Name table: "Copyright © 2012 Intel Corporation. Licensed under the Apache License, Version 2.0". Version 1.00. I read this directly from the WOFF tables.
  - Coverage: 832 code points (Latin-1, Latin Ext-A, Greek 75, Cyrillic 98). There is **no Arabic, Persian, CJK, Thai or Devanagari**. The fork's commit `1196dc8` added the Greek and Cyrillic glyphs.
  - Weight 200 is declared but never used. Only 400 and 700 appear.
- **Title** `h1.title` "2048": **72px** bold (SCSS says 80), float left, `#776e65`. Mobile: 27px, `margin-top:15px` (`main.css:15-20`, `:594-596`).
- **Score and Best boxes:**
  - Base: `#bbada0`, `padding:12px 20px` (SCSS: 15/25), value 25px bold white, `height:25px`, `line-height:47px`, radius 3px, `margin-top:8px`. Mobile: `padding:15px 10px`, `min-width:40px`, `margin-top:0` (`main.css:50-62`, `:602-605`).
  - Label: `::after` with `content:"Score"` / `"Best"`, absolute at `top:10px`, 13px uppercase, `line-height 13px`, colour `#eee4da` (`:63-72`, `:89-93`). The labels are **not localized**.
  - Layout: the boxes are `inline-block` inside a right-floated `.scores-container`. The only gap between them is inline whitespace.
- **"+N" score addition:**
  - A `div.score-addition` is appended inside `.score-container` whenever the score delta is positive (`js/html_actuator.js:117-132`).
  - Style: absolute, `right:30px`, 25px bold, `line-height 25px`, colour `rgba(119,110,101,.9)` (about `#7e746b` on the box), `z-index:100` (`main.css:73-87`).
  - Animation: see §4.
- **Above-game row** (`index.html:30-37`):
  - `#night` div (inline `padding:10px`, block) holding a 22×21 moon PNG. It sits on its own row.
  - `.game-intro`: 14px, desktop width 285px with `line-height 42px`. Mobile: width 100%, `line-height 1.65`. Text: "Join the numbers and get to the **2048 tile!**".
  - Two buttons, both floated right, so the visual order is [Undo][New Game]. Shared style: `#8f7a66` (`darken(#bbada0,20%)`), text `#f9f6f2`, radius 3px, `padding:0 20px`, height 40, `line-height 42`, 16px bold, `margin-left:5px`. Mobile: New Game only gets `width:42%; padding:0` (`main.css:546-581`, `:610-621`).
- **Game message overlay** (`.game-message`): absolute `inset:0` over the board, `z-index 100`, centred text (`main.css:157-228`).

| State class | Background | Text colour | Buttons visible | `<p>` | `.lower` margin-top |
|---|---|---|---|---|---|
| `game-over` | `rgba(238,228,218,.5)` (≈ `#d4c8bd` over the board) | `#776e65` | Try again | 60px bold, `margin-top:222px` (mobile **30px**, `margin-top:90px`, `!important`) | 59px (mobile 30px) |
| `game-won` | `rgba(237,194,46,.5)` (≈ `#d4b867`) | `#f9f6f2` | Keep going, Try again | same | 59 / 30 |
| `restart-game` | `rgba(238,228,218,.5)` | `#776e65` | OK, Cancel | same | **85px** (mobile 30, `!important`) |
| `undo-move` | `rgba(238,228,218,.5)` | `#776e65` | Undo, Cancel | same | 85 / 30 |

Overlay buttons use the same button mixin with `margin-left:9px` and inherit 18px/15px bold (`main.css:182-193`). The overlay has **no border-radius**, so its square corners paint past the board's 6px rounded corners. This is a minor upstream glitch.

- **Night mode** (fork-only: `js/nightmode.js:1-14`, `index.html:31-33`, `style/night.png`):
  - `window.onload` binds `onclick` on the `#night` **div**, not on the `<a id="nightbtn">` inside it.
  - It toggles inline `background-color` on `<html>` and `<body>` between `#faf8ef` and `#2D302C`. The current state is detected by comparing the string `"rgb(45, 48, 44)"`.
  - Nothing else changes. Board, tiles, buttons and text keep their light-theme colours. The title and intro (`#776e65`) on `#2D302C` drop to **2.68:1** contrast.
  - `night.png` is a 22×21 palette PNG of a near-black crescent moon on transparent. On the night background it is about 1.6:1 and **nearly invisible**, so the toggle is hard to find again.
  - The choice is **not persisted**. It resets on every page load, and `MainActivity.onResume` reloads the page on every resume (`src/com/uberspot/a2048/MainActivity.java:117-121`).
  - Upstream issue uberspot/2048-android#15, "Dark, please, late night play" (https://github.com/uberspot/2048-android/issues/15), has been **open since 2014-08-24**.

## 4. Motion

`$transition-speed: 100ms` (`main.scss:22`). The CSS keyword easings map to Compose cubic-béziers as follows (per the CSS Easing spec):
- `ease` = `(0.25, 0.1, 0.25, 1)`
- `ease-in` = `(0.42, 0, 1, 1)`
- `ease-in-out` = `(0.42, 0, 0.58, 1)`

| Effect | Property / keyframes | Duration | Delay | Easing | Fill | Ref |
|---|---|---|---|---|---|---|
| Tile slide | `transition: transform` between position classes | **100ms** | 0 | ease-in-out | n/a | `main.css:347-354` |
| New tile "appear" | `opacity 0→1` and `scale(0)→scale(1)` | **200ms** | **100ms** (waits for the slide) | ease | `backwards`: invisible during the delay | `main.css:456-476` |
| Merge "pop" | `scale(0)` → 50% `scale(1.2)` → `scale(1)`. CSS applies `ease` **per segment** (each 100ms half) | **200ms** | **100ms** | ease | `backwards`: hidden while sources slide | `main.css:514-539` |
| Score "+N" | `top:25px→-50px` (75px rise), `opacity 1→0` | **600ms** | 0 | ease-in | both | `main.css:38-45`, `:73-87` |
| Win/lose overlay | `fade-in` opacity 0→1 | **800ms** | **1200ms** (`100ms × 12`) | ease | both | `main.css:132-137`, `:157-172` |
| Restart/Undo prompt | `fade-in` | **400ms** | **100ms** | ease | both | `main.css:202-208`, `:216-222` |

**Actuator sequencing** (`js/html_actuator.js`):
1. `actuate()` runs inside `requestAnimationFrame`. It clears the whole `.tile-container`, re-creates **every** tile DOM node, then updates the score, best score and message (`:10-44`).
2. A moved tile (one with `previousPosition`) is first rendered with its **old** position class. A **nested** `requestAnimationFrame` swaps in the new class so the 100ms transform transition fires. The fork's `5ea18a3` skips the rAF when the position is unchanged (`:75-83`).
3. A merged tile (one with `mergedFrom`) gets `.tile-merged` at its final cell, then recursively renders both source tiles. The sources slide from their previous positions into the merge cell. The merged wrapper is appended **after** the sources, so it sits on top in DOM order and with `z-index 20` (`:84-91`, `:97-101`). The source DOM nodes stay underneath until the next `actuate()` clears them.
4. Every other tile gets `.tile-new` and the appear animation (`:92-95`). This includes **every tile after restore or undo**, because `previousPosition` is null then.
5. Effective per-move timeline: slide 0–100ms, then pop/appear 100–300ms, so the board settles at about **300ms**. The overlay starts at 1200ms and is fully opaque at 2000ms.
6. Score: `updateScore` empties the container, which **kills any in-flight "+N"**, then appends a new one if the delta is positive (`:117-132`). `HTMLActuator.score` starts at 0 (`:7`), so the first actuate after page load flashes **"+<entire score>"**.

## 5. Input

**Keyboard** (`js/keyboard_input_manager.js:37-69`):
- Handled on `keydown` via deprecated `event.which`, and ignored if Alt, Ctrl, Meta or Shift is held.
- Mapped keys call `preventDefault()`.

| Action | Keys (keyCode) |
|---|---|
| Up | ↑ 38, **W** 87, **K** 75 (vim) |
| Right | → 39, **D** 68, **L** 76 (vim) |
| Down | ↓ 40, **S** 83, **J** 74 (vim) |
| Left | ← 37, **A** 65, **H** 72 (vim) |
| Restart (no confirmation) | **R** 82 |
| Undo | none |

**Touch / swipe** (`js/keyboard_input_manager.js:80-131`):
- **Listener target:** the whole `.container` column. Upstream used `.game-container`; the fork changed it in `c870b2a`. The column includes the title, scores, buttons and board. Swipes in the side margins outside the 280px column are ignored.
- **`touchstart`:** ignored with more than one finger. Otherwise it records `touches[0].clientX/Y` and calls **`preventDefault()`**.
- **`touchmove`:** always calls `preventDefault()`, which blocks scrolling.
- **`touchend`:** ignored if any finger is still down. Otherwise:
  - `dx`/`dy` come from `changedTouches[0]`.
  - A swipe fires when `max(|dx|,|dy|) > 10` CSS px. There is no velocity check and no time limit.
  - The axis is chosen by `|dx| > |dy|` (strict), so exact diagonals resolve vertical.
  - The move is emitted **only on finger lift**; there is no early commit once the threshold is crossed.
- **IE10 `MSPointer*`:** a dead branch (`:4-13`, `:85-96`, `:106-119`).
- **Buttons** (`bindButtonPress`, `:159-163`): bound to **both** `click` and `touchend`. The `touchend` handler calls `preventDefault`, which suppresses the synthetic click. There is no movement check. This gives the side effects listed in §8.
- **Shell interaction:** `MainActivity` toggles "fullscreen" (really the status bar) on any touch held **>2000ms** between DOWN and UP. That includes a slow swipe (`src/com/uberspot/a2048/MainActivity.java:96-111`). The store listing says "3+ seconds" (`metadata/en-US/full_description.txt`). The flag logic is also inverted: `isFullScreen=true` *clears* `FLAG_FULLSCREEN` (`MainActivity.java:151-158`).
- There is no pointer-events or mouse-drag support, no D-pad/gamepad handling beyond whatever WebView maps to arrow keydowns, and no haptics.

## 6. i18n

**How the language is chosen:** `i18n.js` reads the `?lang=` query parameter (`findGetParameter`, `js/i18n.js:95-108`). MainActivity passes `Locale.getDefault().getLanguage()`, a bare ISO-639 code with no region (`MainActivity.java:91`, `:120`). Unknown codes fall back to `en`. Strings are injected by `document.write` at parse time (`index.html:34-47`). The `intro` string contains HTML.

**Languages supported:** `en` (default), `ru`, `uk` (`js/i18n.js:28-59`). That is three languages.

**Keys** (11): `intro`, `new_game`, `undo`, `keep_going`, `try_again`, `ok`, `cancel`, `start_a_new_game`, `undo_the_current_move`, `you_win`, `game_over` (`js/i18n.js:10-22`).

| key | en | ru | uk |
|---|---|---|---|
| intro | Join the numbers and get to the `<strong>2048 tile!</strong>` | Объединяйте числа и получите `<strong>2048!</strong>` | Об'єднуйте числа і отримаєте `<strong>2048!</strong>` |
| new_game | New Game | Новая игра | Нова гра |
| undo | Undo | Отменить ход | Скасувати хід |
| keep_going | Keep going | Продолжайте | Продовжуйте |
| try_again | Try again | Попробовать ещё раз | Спробувати ще раз |
| ok | OK | ОК | ОК |
| cancel | Cancel | Отмена | Відміна |
| start_a_new_game | Start a new game? | Начать новую игру? | Почати нову гру? |
| undo_the_current_move | Undo the current move? | Отменить текущий ход? | Скасувати поточний хід? |
| you_win | You win! | Вы победили! | Ви виграли! |
| game_over | Game over! | Игра закончена! | Гра завершена! |

**Not localized:** "Score" and "Best" (CSS `content`, `main.css:89-93`), the "2048" title, and `<title>`. The Ukrainian `cancel` = "Відміна" is a calque; "Скасувати" is more idiomatic.

**Locale coverage across the three places:**

| Source | Locales |
|---|---|
| Game JS | en, ru, uk |
| Android `res/values-*/strings.xml` (4 keys: `app_name`, `action_settings`, `press_back_again_to_exit`, `toggle_fullscreen`) | default(en), cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, pt, ru, sl, sr, tr, uk (20 + default) |
| Store `metadata/` | af, am, ar, bg, ca, de, el, en-US, es, et, fi, fr, hi, id, it, ja, ko, lt, lv, pt-BR, pt-PT, ro, ru, sv, sw, th, tr, uk, vi (29) |

- **In all three:** ru, uk. The game JS has **no** locale that is missing from res.
- **Android res only (not in game JS):** cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, pt, sl, sr, tr (18). Users on these locales see an English game inside a localized shell.
- **Metadata only:** af, am, ar, bg, ca, et, fi, hi, id, ja, lv, ro, sv, sw, th, vi, plus the pt-BR/pt-PT split (res has only `pt`).
- `values-fa` overrides `app_name` to **"۲۰۴۸"** (Persian digits). This conflicts with the locked label "2048".
- **RTL:** fa (in res) and ar (in metadata) are RTL. The board and the swipe/keyboard directions **must not mirror**.
- **Glyphs:** Clear Sans has no Arabic or Persian glyphs.
- **Digits:** the legacy game always renders ASCII digits. A Kotlin `String.format`/`NumberFormat` with the default locale can emit Arabic-Indic digits. Use `Int.toString()` for tile and score text.

## 7. uberspot-fork changes vs upstream (git log `fc1ef4f..7a477eb`, plus `diff -r` against upstream HEAD)

1. **Undo** (fraggerfox, `965eb1d`, `ffaaab0`, `f13d056`; `0407c9e` clears buttons): Undo button, confirmation overlay, 50-level localStorage stack (`js/local_storage_manager.js:24`, `:67-113`; `js/game_manager.js:27-64`; `index.html:36`, `:46`; `main.css:192-228`, `:568-581`).
2. **Restart confirmation** (`2af4c4e`): New Game → "Start a new game?" with OK/Cancel. Cancel is wired to `keepPlaying` (`js/keyboard_input_manager.js:72-78`, `js/html_actuator.js:146-151`).
3. **Night-mode button** (`f889036`, credit gurshabad): `js/nightmode.js`, `style/night.png`.
4. **i18n en/ru/uk** (a4vi2r, `aa7e344`…`4e9733d`): `js/i18n.js`, `document.write` in `index.html`. Fonts re-subset with Latin, Cyrillic and Greek (`1196dc8`).
5. **Mobile compaction** (`c870b2a`, plus the undo commit): removed the upstream "How to play" text, the official-version note and the credits footer (`df9321e`). Swipe target changed to `.container`. Title 72px. Tighter paddings and margins.
6. **Win-and-no-moves fix** (`6be2116`): `keepPlaying` passed to the actuator, plus the edge-case branch.
7. **Perf and rendering tweaks:**
   - `tileMatchesAvailable` checks 2 directions instead of 4 (`04a6d41`).
   - Skip the no-op position rAF (`5ea18a3`).
   - `translateZ(0)` against Galaxy S/SII flicker (`ae476ed`).
   - `-webkit-tap-highlight-color: transparent` (`4157c3b`).
8. **`keepPlaying()` now calls `actuate()`** (persists the flag and pushes an undo state).
9. **Ads and analytics:** none ever. Upstream's own `ffa8559` added AdSense and `fc1ef4f` removed it, and that removal is the merge-base. Upstream's `gh-pages` sharing and analytics were never in `master`.
10. **Licence:** the MIT `LICENSE.txt` (© 2014 Gabriele Cirulli) is retained. The in-game credit footer was removed. The new app must keep MIT attribution in About/Licenses, and add Apache-2.0 attribution if Clear Sans is reused.

## 8. Bugs and quirks

| # | Bug / quirk | Evidence | Severity |
|---|---|---|---|
| B1 | **Undo after "undo, then move" skips a state.** Undo pops both S_n and S_{n-1} off the stack. After a new move T, the next undo goes to S_{n-2} instead of S_{n-1} | `js/local_storage_manager.js:68-91` **[verified T1]**: expected S2, got S1 | High (core feature) |
| B2 | **Undo is not persisted.** `undoMove` never calls `setGameState`. Storage keeps the pre-undo state, and MainActivity reloads on **every resume**, so screen-off/on silently reverts the undo | `js/game_manager.js:27-53`, `MainActivity.java:117-121` **[verified T1: storage stale]** | High |
| B3 | **First undo after any reload is a no-op.** `setup()` → `actuate()` pushes a duplicate of the current state | `js/game_manager.js:102`, `:133` **[verified T2]**: stack 3→4, undo no-op | Medium |
| B4 | **Undo after game over soft-locks the game.** The game-over `actuate()` already wiped the stack, so `undoMove` only clears the overlay. The board stays dead with no overlay and no message, and swipes are ignored until New Game or R | `js/game_manager.js:128-131`, `:27-30` **[verified T4]** | High |
| B5 | **"Cancel" means "Keep playing".** Cancelling either confirmation sets `keepPlaying = true` and persists it. That game then **never shows "You win!"** at 2048. Cancelling the undo prompt over a win overlay also silently continues | `js/keyboard_input_manager.js:78` **[verified T3]** | High |
| B6 | `keepPlaying` instance **field shadows** the prototype **method** of the same name. Binding at `:14` happens before `setup()`, so the event still works, but any later `gm.keepPlaying()` call throws | `js/game_manager.js:14`, `:67`, `:89`, `:95` **[verified]**: harness hit `TypeError: gm.keepPlaying is not a function` | Low (port hazard) |
| B7 | A **"+<whole score>"** float plays on every page load or resume, because the actuator's score starts at 0 | `js/html_actuator.js:7`, `:120-131` **[reasoned]** | Low, visible |
| B8 | All tiles replay the **appear** animation on every load, resume and undo, because restored tiles have no `previousPosition` | `js/grid.js:29`, `js/html_actuator.js:92-95` **[reasoned]** | Low |
| B9 | The game-over board is **lost on resume**: the state was cleared, so the reload starts a fresh game with no overlay | `js/game_manager.js:128-131` + shell reload **[reasoned]** | Low |
| B10 | **Night mode:** not persisted, resets on every resume, recolours only the page background, leaves text at 2.68:1, and the icon is nearly invisible in night mode. The `onclick` sits inside `.container`, whose `touchstart` calls `preventDefault`, which in Chrome normally suppresses the synthetic click on touch, so it may not fire on touch at all | `js/nightmode.js`, `js/keyboard_input_manager.js:98` **[reasoned; on-device behaviour unverified]** | Medium |
| B11 | **Swipe starting on a button activates the button.** `touchend` targets the touchstart element and there is no movement check, so a swipe beginning on New Game or Undo opens the prompt **and** moves. A swipe starting on "Try again" restarts | `js/keyboard_input_manager.js:159-163` **[reasoned]** | Medium |
| B12 | Keyboard **R** and overlay **Try again** restart with **no confirmation**. New Game asks | `js/keyboard_input_manager.js:65-73` | Low (inconsistent) |
| B13 | The swipe threshold of 10 CSS px is tiny and fires only on lift. There is no velocity or fling check, and diagonals resolve vertical | `js/keyboard_input_manager.js:127-129` | Low |
| B14 | The long-press >2000ms fullscreen toggle collides with slow swipes and thinking holds. The listing says 3s | `MainActivity.java:96-111` | Medium (shell) |
| B15 | Mobile font sizes of **15px** (1024, 2048) and **10px** (super tiles) on a 58px tile are hard to read. All super tiles share one colour | `main.css:409-426` | Medium (visual) |
| B16 | White-on-gold contrast is 1.4–1.7:1. "Score"/"Best" labels are 1.74:1 (`#eee4da` on `#bbada0`) | §3.1 | Medium (a11y) |
| B17 | "Score"/"Best" are not localized (CSS `content`). `document.write` is used for the strings | `main.css:89-93`, `index.html:34-47` | Low |
| B18 | The board is a fixed 280px on phones and does not use the available width. `main.scss` is stale relative to `main.css` | §3.2 | Medium (visual) |
| B19 | The overlay's square corners bleed past the board's 6px radius | `main.css:157-166` | Cosmetic |
| B20 | Every move does an O(stack) JSON parse and stringify (up to 51 states, twice). `bestScore` is stored as a string | `js/local_storage_manager.js:93-113` | Low |
| B21 | `nightmode.js` assigns `window.onload`, clobbering other handlers. The comment in `i18n.get` says "default english" but the function returns `''`; the fallback actually works through `mergeObjects` | `js/nightmode.js:1`, `js/i18n.js:64-73` | Cosmetic |

## 9. Parity checklist (legacy behaviour → decision for the Kotlin/Compose rewrite)

| Legacy behaviour | Decision | Notes for the native port |
|---|---|---|
| 4×4 grid, 2 start tiles | **Must-keep** | Engine parameterised by size so 3×3 / 5×5 / 6×6 modes are possible later; default 4 |
| Spawn 90% "2" / 10% "4", uniform empty cell, only after an effective move | **Must-keep** | Inject `Random` for deterministic tests |
| Traversal from the leading edge; single merge per tile per move; `mergedFrom` | **Must-keep** | Port the T6 harness rows as unit tests (`2222→44`, `222_→42`, `422_→44`, `8_88→16 8`, `2244→48`) |
| Score = sum of merged values | **Must-keep** | Emit per-move `scoreDelta` for the "+N" animation |
| Win at the first exact 2048; overlay; Keep going; win + game-over edge case | **Must-keep** | Model as a state machine (`Playing`, `Won(awaitingContinue)`, `Continuing`, `Over`). Keep the 2048 target, optionally configurable |
| Input locked while the win overlay is up | **Improve** | Keep the overlay, but allow undo from it |
| No-op move: no spawn, no save, no history | **Must-keep** | Add subtle feedback (e.g. a small board nudge/haptic) instead of silence |
| Game-over detection (empty cell, or any equal orthogonal neighbour) | **Must-keep** | |
| Best score persists forever, never decreases | **Must-keep** | Store as an `Int`/`Long` in DataStore. No migration is possible because of the new applicationId |
| Auto-save every move, resume on launch | **Must-keep** | Persist the full model (board, score, flags, undo history) atomically. Also persist on undo (fixes B2) |
| Save wiped on game over | **Improve** | Keep the final board and allow undo from game over (fixes B4, B9) |
| Undo, 50 levels, stack in localStorage | **Improve** | Correct in-memory deque of immutable snapshots, persisted; fixes B1 and B3. Decide whether undo needs confirmation; recommend no confirmation, plus a snackbar or animation |
| Restart confirmation dialog | **Must-keep** (improve) | Use an M3 dialog. Cancel must not mutate state (fixes B5). Make R / "Try again" consistent |
| Keep going / Try again overlay buttons | **Must-keep** | |
| Tile palette (2 → 2048 hex values, §3.1) | **Must-keep as the "Classic" theme**; **improve** contrast | Ship the exact legacy hexes as the Classic palette; improve text contrast (dark text on 8, darker gold text or outline for 128–2048) |
| Single `#3c3a32` for all >2048 tiles | **Improve** | Distinct colours for 4096 … 131072 |
| Gold glow on 128–2048 (box-shadow formula) | **Improve** | Re-implement as a soft glow scaled by `g = (exp-4)/7`; keep it subtle |
| Fixed 500/280px board, 15/10px gap, 3/6px radii | **Improve** | Responsive board filling the width (max ~560dp), gap ≈ 0.14 × tile, modern larger radii |
| Clear Sans font (Apache-2.0, Latin/Greek/Cyrillic) | **Improve / decide** | Either convert WOFF→TTF (lossless, `glyf` outlines) and bundle it with an Apache notice, or switch font. Needs system fallback for fa/ar/ja/ko/th/hi |
| Font-size ladder 55/45/35/30 (mobile 35/25/15/10) | **Improve** | Auto-fit text by digit count; minimum readable size |
| Slide 100ms ease-in-out; appear 200ms ease @100ms; pop 0→1.2→1 200ms @100ms; +N 600ms ease-in 75px; overlay fade 800ms @1200ms; prompt 400ms @100ms | **Must-keep timings as the baseline**; **improve** feel | Keep the ~300ms move budget; optional spring physics; respect the system "remove animations" setting; never block input more than necessary. Queue or fast-forward rapid swipes rather than re-rendering everything (legacy rebuilt the whole DOM each move) |
| Replay of appear animation on resume/undo; "+whole score" on load | **Drop** (fix B7, B8) | Undo should animate as a reverse slide, or as a quick crossfade |
| Arrow / WASD / HJKL keys; R restart | **Must-keep** | Add an Undo shortcut (e.g. U / Ctrl+Z / Backspace) and D-pad; works on Chromebooks and with hardware keyboards |
| IE10 MSPointer branch, polyfills (bind, classList, rAF) | **Drop** | |
| Swipe on `.container`, >10px, fire on lift | **Improve** | Board-plus-surroundings gesture area; dp-based threshold (use touch slop or a ~24dp floor); commit once the threshold is crossed; dominant-axis selection; ignore multi-touch (fixes B11, B13) |
| Long-press (>2s) toggles fullscreen (shell) | **Drop** | Use edge-to-edge with an optional immersive setting in Settings (fixes B14) |
| Double-back-to-exit toast (shell) | **Drop or decide** | Shell track |
| Night-mode button (page background only, not persisted) | **Drop as implemented**; **replace** | Full M3 light/dark theme following the system, plus a manual override persisted in DataStore; optional dynamic colour (fixes B10, closes upstream #15) |
| i18n en/ru/uk with 11 keys; Score/Best unlocalized | **Must-keep and expand** | Move all strings to `strings.xml`; localize Score/Best; cover the union of res and metadata locales; force LTR for the board and gestures; ASCII digits on tiles |
| `values-fa` `app_name` = "۲۰۴۸" | **Decide** | The locked label "2048" implies removing that override |
| `bestScore` / `gameState` / `gameStatesStack` localStorage schema | **Drop** | New typed persistence; no legacy import possible |
| MIT attribution (Gabriele Cirulli 2014) | **Must-keep** | About / open-source licenses screen |
| Ads/analytics: none; zero permissions | **Must-keep** | Matches the locked "zero permissions" decision |
