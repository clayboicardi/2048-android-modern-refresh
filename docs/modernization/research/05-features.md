# 05 — Feature landscape and prioritized roadmap (offline, zero-permission, ad-free 2048)

Track: **features**. Research date: 2026-10-28. Live sources are cited inline as [S#] and listed in §11.
Fixed decisions from the brief: native Kotlin + Compose + Material 3, applicationId `com.clayboicardi.game2048`, label "2048", fully offline, **no runtime permissions and no INTERNET**.
This doc also assumes the toolchain track's numbers (03-toolchain.md): minSdk 24, targetSdk/compileSdk 37.

Effort scale (one experienced developer, including tests): **S** is 1 day or less. **M** is 2 to 5 days. **L** is more than 1 week.
Tiers: **v1.0** = must ship in the overhaul PR. **v1.x** = next increments with no schema rework. **later** = backlog. **never** = conflicts with the constraints, or not worth doing.

---

## 0. TL;DR

1. **Table stakes in 2026** (every well-rated app has them):
   - an undo that does more than one step;
   - board sizes 3×3 to 8×8, each with its own best score;
   - auto-save that never loses a game;
   - "Keep going" after 2048;
   - local statistics;
   - dark theme.

   Among the top-reviewed apps, the ones without ads advertise that fact as their main feature. Store reviews say most of the "cheap" feel comes from **input**: phantom swipes, one swipe registering as two moves, and lag caused by ads [S1][S7][S10]. Graphics are a smaller factor.
2. **v1.0 must lock in the data model** that later features need, even where their UI ships later:
   - a seeded, app-owned PRNG (not `kotlin.random.Random(seed)`, whose algorithm may change between Kotlin versions [S30]);
   - a per-game move log;
   - a per-board-size statistics schema;
   - backup rules.

   These make undo honest and unlimited, and they make daily challenges, replays and shareable games possible without a migration.
3. **Permission traps (verified):**
   - **Glance widgets** pull in WorkManager. WorkManager merges `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`. This was checked in the AAR manifests [S24]. Build any widget with plain `RemoteViews` instead.
   - **Notifications** (for example "daily challenge ready") need runtime `POST_NOTIFICATIONS` on API 33+. **never.**
   - **Custom vibration** (`Vibrator`/`VibrationEffect`) needs `VIBRATE`. Use `View.performHapticFeedback` / Compose `LocalHapticFeedback`, which need none [S19][S20].
   - **Play Games Services** needs network. **never.**
4. **The legacy best score cannot be imported.** It lives in the WebView localStorage LevelDB inside `com.uberspot.a2048`'s private sandbox. A different applicationId cannot read it [S27][S28]. **never** (details in §4.20).
5. **Legacy behaviours to drop and replace:**
   - the undo confirmation prompt;
   - the game-over wipe that makes the losing move un-undoable;
   - Cancel silently setting `keepPlaying=true`;
   - "R" restarting with no confirmation;
   - press-back-twice to exit (incompatible with predictive back at target 36+ [S16]);
   - night mode that is not persisted.

   All are listed with file:line in §2.

---

## 1. Hard constraints and what they rule out

| Android capability | Permission it needs | Kind | Verdict |
|---|---|---|---|
| Network (ads, analytics, crash SDKs, cloud sync, online leaderboards, Play Games Services) | `INTERNET` | install-time, but locked out by decision | **never** |
| Notifications (daily reminder, streak reminder) | `POST_NOTIFICATIONS` (API 33+) | **runtime** | **never** |
| Exact alarms for reminders | `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | special | **never** |
| Custom vibration waveforms (`Vibrator`, `VibrationEffect`) | `VIBRATE` [S20] | install-time | avoid. Use `performHapticFeedback`, which needs no permission [S19] |
| `View.performHapticFeedback` / Compose `LocalHapticFeedback` | none [S19][S20] | — | **use** |
| Keep screen on (`FLAG_KEEP_SCREEN_ON` / `keepScreenOn`) | none ("doesn't require a special permission") [S33] | — | OK |
| Share text or image through the Sharesheet (`ACTION_SEND`, `FileProvider` in `cacheDir`, `FLAG_GRANT_READ_URI_PERMISSION`) | none (per-URI grant) [S25] | — | OK |
| Export/import save file (SAF `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`) | none | — | OK |
| Open the GitHub/licence URL in a browser (`ACTION_VIEW`) | none (the browser does the networking) | — | OK |
| Static app shortcuts (`res/xml/shortcuts.xml`) | none [S26] | — | OK |
| Home-screen widget with plain `AppWidgetProvider` + `RemoteViews` | none (the `BIND_REMOTEVIEWS` / `BIND_APPWIDGET` guards are held by the system) | — | OK |
| Home-screen widget with **Glance 1.2.0** | pulls `androidx.work:work-runtime-ktx`. Its manifest merges `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` [S24] | install-time (normal) | **avoid** (breaks the "no permissions" listing) |
| Quick Settings tile (`TileService`) | the service is guarded by `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`. That guard is held by SystemUI; the app requests nothing [S17] | — | allowed, but low value (§4.13) |
| Auto Backup / device-to-device transfer | none. The system performs it [S21][S22] | — | **use** |
| `androidx.core` 1.9+ self permission `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | merged automatically, `protectionLevel=signature` | not runtime | Keep, as 03-toolchain.md recommends. F-Droid lists it under "Permissions": Privacy Friendly 2048 1.4.2 and 2048 Open Fun Game 1.16.2 both show it, and the earlier OFG 1.15.1 showed "No permissions" [S3][S4][S35] |

**CI gate (v1.0, S):** run `aapt2 dump permissions app-release.apk` and fail if anything other than the signature self-permission appears. This catches Glance, WorkManager or any other library that silently adds permissions.

---

## 2. Legacy feature inventory (what parity means)

All paths are relative to the repo root. The game lives in the `assets/2048` submodule.

| Legacy behaviour | Where | Keep / change |
|---|---|---|
| Board fixed at 4×4 | `assets/2048/js/application.js:17` (`new GameManager(4, …)`), `assets/2048/index.html:51-76` (16 static cells), `style/main.scss:6` (`$grid-row-cells: 4`) | Generalise to 3×3–8×8 (the engine was already size-generic: `game_manager.js:1-2, 256-259`) |
| 2 start tiles | `js/game_manager.js:7` | Keep |
| Spawn 90 % "2" / 10 % "4" in a uniform random empty cell | `js/game_manager.js:115`, `js/grid.js:37-43` (`Math.random()`) | Keep the rule exactly (reviewers notice the 10 % rate [S10]). Replace `Math.random` with a seeded PRNG |
| A tile merges at most once per move; score += merged value | `js/game_manager.js:202, 213` | Keep |
| Win at 2048, then "Keep going" (endless) | `js/game_manager.js:66-71, 216`, `js/html_actuator.js:27-41` | Keep. Target scales for 3×3 (§4.2) |
| Game over = no empty cell and no adjacent equal pair | `js/game_manager.js:231-233, 284-314` | Keep |
| Best score (a single global value) | `js/local_storage_manager.js:22, 44-50`; updated live in `game_manager.js:124-126` | Change to per board size and mode, plus a separate "no undo" best |
| Save state after every move in WebView localStorage (`gameState`). Falls back to an in-memory `fakeStorage`, losing all state, if localStorage is unavailable | `js/local_storage_manager.js:1-28, 58-60`; `MainActivity.java:81` (`setDomStorageEnabled`) | Replace with atomic on-disk persistence (§4.17) |
| **Undo stack capped at "50"** (actually holds up to 51 entries: `shift()` only when `length > 50`) | `js/local_storage_manager.js:93-106`; changelog "Limit undo button to last 50 moves" (`res/raw/changelog_master.xml`) | Make undo unlimited (§4.1) |
| **Every undo asks for confirmation** ("Undo the current move?") | `js/keyboard_input_manager.js:74`, `js/html_actuator.js:161-166` | Drop the prompt (undo can be reversed) |
| **Game over wipes the state and the undo stack, so the losing move cannot be undone**. The overlay's undo path then just clears the message and leaves a frozen board (`over` still true). Found by reading the code | `js/game_manager.js:128-131`, `js/local_storage_manager.js:62-65`, `js/game_manager.js:27-53` | Fix: undo must work on the game-over screen |
| **Cancel in the restart/undo prompt is bound to `keepPlaying`**, so it sets `keepPlaying=true`. A later 2048 then shows no win overlay. Found by reading the code | `js/keyboard_input_manager.js:78`, `js/game_manager.js:67-71, 74-76` | Do not port |
| New Game asks for confirmation, but the **"R" key restarts immediately** and Try again restarts directly | `js/keyboard_input_manager.js:66-68, 72-73` | Confirm only when a game is in progress |
| Keyboard: arrows, Vim HJKL, WASD | `js/keyboard_input_manager.js:37-50` | Keep. Add D-pad, U = undo, N = new game |
| Swipe threshold 10 CSS px; the swipe commits on touch **end**; multi-touch ignored; swipe anywhere in `.container` | `js/keyboard_input_manager.js:80-131` (threshold line 127) | Keep commit-on-release and swipe-anywhere. Make the threshold dp-based and configurable (§4.16) |
| Animation timing: slide 100 ms; new tile "appear" 200 ms after a 100 ms delay; merge "pop" 200 ms after a 100 ms delay; score "+N" float 600 ms; win/lose overlay fade 800 ms after a 1200 ms delay | `style/main.scss:22, 104, 235, 432, 452` | 04-design-motion.md owns the retune. This doc only adds the speed setting (§4.14) |
| "Night mode": swaps only the page background `#faf8ef` and `#2D302C`. **Not persisted**, and `onResume` reloads the page (`MainActivity.java:118-120`), so it resets on every resume | `js/nightmode.js:1-14` | Replace with real System/Light/Dark themes (§4.4) |
| Fullscreen toggle on a long press of more than 2000 ms, plus a toast on **every** launch | `MainActivity.java:34, 94-111, 130-158` | Replace with a "Hide system bars" setting (S) |
| Press back twice within 3500 ms to exit | `MainActivity.java:31, 160-176` | **Drop.** At target 36+, `onBackPressed` is not called [S16]. State is auto-saved, so exiting costs nothing |
| Changelog dialog on first run (ckchangelog `2.0.0-SNAPSHOT` from a Sonatype snapshot repo) | `MainActivity.java:72-75`, `build.gradle` | Drop the dependency. An optional "What's new" sheet is v1.x |
| Portrait-locked; sensor orientation only on LARGE/XLARGE screens with auto-rotate on | `AndroidManifest.xml:14`, `MainActivity.java:55-68` | Adaptive layout (§4.22) |
| In-game strings localised only in en / ru / uk; Android `res/values-*` exists for 20 locales but holds just 4 shell strings; store metadata in 29 locales | `js/i18n.js:29, 44`; `res/values-*`; `metadata/` | §4.24 |
| No permissions at all | `AndroidManifest.xml` (no `<uses-permission>`) | Keep (§1) |

---

## 3. Market survey (Android, iOS, web, F-Droid), 2025–2026

| App (platform) | Ratings / installs / last update | Ads | Board sizes | Undo | Modes / extras | Stats / other |
|---|---|---|---|---|---|---|
| **2048, official** (Solebon LLC; Play `com.gabrielecirulli.app2048`) [S1] | 4.7★ (30–31.9K reviews), 1M+, updated 2026-03-23 | **Yes** (between games); iOS has a "Remove ads $9.99" IAP [S2] | 4×4 only (a reviewer asks for 3×3 and 5×5–8×8 [S2]) | "Undo button to reverse a swipe" (single-step); undo can be toggled off. A reviewer reports an undo glitch [S2] | Classic + **Time trial** ("tiles won't wait for your move"); **Tile Play** (restart from the most recent game where 128+ was reached); hidden "Hack mode" (2+2 = 512, separate stats) [S5][S6] | "Detailed statistics including best game scores"; sounds with an on/off switch; swipe anywhere; latest update: "**Remove metrics (moves and timer) from the game board** for carefree play" [S1][S5] |
| **2048** (Androbaby) [S7] | 4.7★, 290K reviews, 10M+, updated 2026-04-27 | Yes | 3×3, 4×4, 5×5, 6×6, 8×8 | **One** undo | "2248 puzzle"; keep playing after 2048 | High score and leaderboard per size; auto-save; play anywhere on screen |
| **2048** (tpcstld, open source) [S8] | 4.3★, 32.3K reviews, 10M+, updated 2023-08-31 | **Never** | 4×4 | **One** undo (top review: "wish it undid further back, maybe 3 moves") | — | "No data collected"; praised for tiny size and responsiveness |
| **2048: Number Puzzle Game** (Quovad Studio) [S9] | 5+ installs, updated 2026-10-05 | Banner + interstitial | 4×4, 5×5, 6×6, 8×8 | Yes | Achievements per board; local game history | "Detailed statistics"; 19 languages; layouts for phone, tablet and foldable |
| **2048** (Drew Developer) [S9b] | 10+ installs, updated 2026-07-28 | **None**, no tracking | 4×4 | **Up to 20 moves** | Classic + **Time Attack** (time bonuses for merges); 3 themes (Classic, Cyberpunk, Nordic Ice) | Top-5 local "Hall of Fame" per mode; haptics; built on DataStore |
| **play2048.co** (Gabriele Cirulli, web, 2025–26 rebuild) [S11] | — | Yes | 4×4 | **Power-up**: 2 undos, more earned by making a 128 tile. **Classic mode has no undo** | Power-ups: **Swap two tiles** (earned at 256) and **Delete tiles by number** (earned at 512); interactive tutorial | — |
| **No Ads 2048** (iOS, Talking Chicken Friend) [S10] | 4.8★, 523 ratings, v1.6.1 (Oct 2026) | None | 3×3, 4×4, 5×5 | Separate "Undo 20" modes per size | "Spawn 32" and "Spawn 128" variant modes; one active game per variation | Reviews in §9 |
| **2048 (Privacy Friendly)** (SECUSO, F-Droid) [S3] | v1.4.2, added 2025-07-28, Android 5.0+, 8.9 MiB | None | 4×4, 5×5, 6×6, 7×7 | Yes | Up to 4 saved games (one per size) | Statistics: swipes, highscores per mode; "no permissions" claim, though F-Droid lists the androidx self-permission |
| **2048 Open Fun Game** (andstatus, F-Droid, Apache-2.0, KorGE) [S4][S12] | v1.16.2, added 2026-07-21, Android 7.0+, 1.3 MiB | None | **3×3–8×8**, high score per size | **Unlimited, animated undo + redo**; "Retries" counter (counts moves made after an undo) | AI autoplay with several algorithms and speed control; bookmarks; history of recent games; "Watch" replay forward/back at any speed; share a game as a JSON file | Theme System/Dark/Light; portrait and landscape layouts; move number and duration (the timer pauses on undo and pause); variable animation speed; tile labels shortened to 16K…128K, 256K, 512K, 1M; keep-screen-on |
| **Legacy uberspot 2048** (this repo) | F-Droid and Play | None | 4×4 | 50-step, confirmed each time | — | Single best score |

Takeaways:
- **Board sizes:** 3×3–8×8 is the common range (Androbaby, OFG; Quovad 4–8; PF 4–7). No surveyed app ships rectangular boards.
- **Undo depth:** spans 0 (play2048 Classic), 1 (official, Androbaby, tpcstld), 20 (Drew, No Ads 2048), 50 (legacy) and unlimited with redo (OFG). One-step undo is the most common complaint about otherwise well-liked apps [S2][S7][S8].
- **Modes beyond classic:**
  - Time trial / time attack (official, Drew).
  - Daily seeded challenges (web: 2048.is "board, the entire sequence of tiles that will spawn, and the target itself are all derived from the date" [S13]; onlinepuzzles, puzzle.now, 2048.now).
  - Variant spawn values (No Ads 2048).
  - Power-ups (play2048.co).
  - Zen/endless (various small apps).

---

## 4. Feature deep-dives (survey → recommendation)

### 4.1 Undo
- **Survey:** see §3. User asks: "more than one undo — make it five" and "fix the undo glitch" [S2]; "only undoes one move … ruin a days-long game" [S8]. A tournament player wants the **number of undos shown on the game-over screen** so scores can be checked [S2]. OFG's Retries counter exists "to compare results more fairly" [S4].
- **v1.0 recommendation:**
  - **Unlimited multi-step undo** within a game. It persists across process death.
  - **No confirmation** prompt.
  - **Undo works on the game-over screen** (fixes the legacy bug).
  - An **undo counter** on the HUD (optional) and on the game-over card.
  - Setting "Undo: On / Off". Off is a purist mode: games played with undo off count toward a separate "No-undo best".
- **Anti-reroll rule:** undo restores the PRNG state too, so repeating the same move gives the same spawn. Undo fixes misswipes; it should not fish for better spawns. Document this in "How to play".
- **Implementation note:** keep the move log (2 bits per move) plus a snapshot every 32 moves (about 100 B each: exponent bytes + score + RNG state). Undo then costs at most 31 replayed moves. A 10,000-move game is about 2.5 KB of log plus about 31 KB of snapshots.
- **Redo:** v1.x (S once the log exists).

### 4.2 Board sizes and win targets
- **Recommendation (v1.0):** square boards 3×3, 4×4, 5×5, 6×6, 7×7, 8×8. Each size has its own in-progress game, best score and statistics. The new-game sheet selects the size. 4×4 is the default.
- **Win targets.** The theoretical max tile is 2^(cells+1) when spawns can be 4s [S31][S32]:

  | Size | Theoretical max tile | Win target | Notes |
  |---|---|---|---|
  | 3×3 | 1024 (only with lucky 4-spawns; 512 with 2-spawns only) | **256** | Optimal play reaches 1024 only about 1 % of the time [S32]. Web 3×3 variants use 256 [S31] |
  | 4×4 | 131,072 | **2048** | Classic |
  | 5×5 | 2^26 | 2048 | Easier; still the canonical milestone |
  | 6×6 | 2^37 | 2048 | |
  | 7×7 | 2^50 | 2048 | |
  | 8×8 | 2^65 | 2048 | Values do not fit in a `Long`: store **tile exponents as `Byte`**, the score as `Long` |

- **Tile labels:** use the plain number up to 4 digits. From 5 digits on boards ≥ 6×6, use OFG-style suffixes (16K, 32K, 64K, 128K, 256K, 512K, 1M…) [S12]. An 8×8 cell on a 360 dp phone is about 38 dp wide, too narrow for "131072". The accessibility label always carries the full number.
- **Rectangular boards:** **never/later**. No surveyed app has them, and layout and stats get complicated for little value.

### 4.3 Game modes
| Mode | Who has it | Recommendation |
|---|---|---|
| Classic + Keep going (endless after the target) | all | **v1.0** (parity) |
| Daily challenge. Seed from the local date (`yyyy-MM-dd`, puzzle # = days since launch). 4×4, **undo off**, one scored attempt per day, unscored replays allowed, past days playable offline. Emoji/text share card | web dailies [S13] | **v1.x** (M). Needs only the v1.0 PRNG, move log and stats schema. No network. **No reminder notifications** (runtime permission) |
| Time trial (tiles spawn on a timer) / time attack (time bonuses per merge) | official [S5], Drew [S9b] | **later** (M). Needs pause/resume semantics, background handling and separate stats |
| Limited moves ("reach X in N moves") | rare | later |
| Zen (no metrics, no game over: shuffle or clear the smallest tiles when stuck) | small apps; review asks for "jumble all the tiles" when stuck [S10] | later |
| Power-ups (swap two tiles, delete all of a value; earned at milestones) | play2048.co [S11] | later. Must be flagged "assisted" in stats |
| Custom rules (4-spawn %, spawn values 2/32/128, custom target) | No Ads 2048 (Spawn 32/128) [S10]; review asks for "control the 4 spawn rate" [S10] | later. Kept out of the main stats |
| Tile Play / bookmarks / checkpoints | official [S5], OFG [S4] | later |
| AI autoplay ("watch the AI play") | OFG [S4] | later (once hints exist) |

### 4.4 Themes
- **v1.0:**
  - Theme: **System / Light / Dark**.
  - "Dynamic color (Material You)" toggle on API 31+ for the app chrome only. The tile palette stays classic so the game keeps its identity.
  - A **high-contrast** tile palette.
  - Distinct colours for tiles above 2048, up to at least 2^17. A reviewer complains that once you reach 4096 "the black color gets a little bit boring" [S2]. Legacy uses a single `.tile-super` colour for everything above 2048 (`js/html_actuator.js:68`).
- Exact hex values come from 04-design-motion.md.
- **v1.x:** 2–3 extra palettes (for example a warm "Classic", a cool one, and an AMOLED black). Drew ships 3 themes [S9b].
- **later:** user-editable palettes.

### 4.5 Statistics (local only)
Kept per **(mode, board size)**, plus an "All" roll-up. **v1.0** builds the full schema. The screen shows the first block; the rest can render in v1.x.

| Stat | Definition |
|---|---|
| Games played | Games that ended in game over, plus games abandoned through New Game with score > 0. Show "abandoned" as a sub-count |
| Wins / win rate | Games that reached the target, even if continued, divided by games played |
| Best score / average score | Max and mean final score. **Plus "Best score (no undo)"** |
| Best tile / tile distribution | Highest tile ever; histogram of the number of games that reached ≥ 2^k for k = 7…17 (128 … 131072) |
| Total moves / avg moves | — |
| Time played / fastest win / fewest moves to win | Monotonic clock (`SystemClock.elapsedRealtime`). Starts at the first move, pauses in the background. A reviewer times 2048 under 10 min and 4096 under 22 min [S2]. Drew moved its timer to a monotonic clock [S9b] |
| Win streak (current / longest) | Consecutive games ending in a win |
| Undo usage | Total undos; number of games won with no undo |
| Daily (v1.x) | Days played, current and longest daily streak, best daily score |

There is also an optional "Show moves and time" HUD toggle, **off by default**. The official app added an option to hide them in 2026 [S1]. Stats are still recorded when it is off. "Reset statistics" needs two-step confirmation.

### 4.6 Local leaderboards and history
- **v1.0:** "Top 10" per board size: score, max tile, moves, duration, undo count, date. Purely local. Drew has a top-5 "Hall of Fame" [S9b].
- **v1.x:**
  - History of the last 100 finished games with the final board.
  - Tap a game to **replay** it (the move log is stored from v1.0).
  - A user asks for exactly this: "a 'replay review' function … to see just HOW that happened" [S10].
- **Size check:** 100 games × ~2.5 KB ≈ 250 KB, which fits easily in the 25 MB Auto Backup quota [S21].

### 4.7 Achievements (local, offline)
**v1.x** (M). About 15 achievements stored locally, with a toast and a light haptic. They are **not** Play Games achievements.

Suggested set:
- Reach 2048, 4096, 8192, 16384, 32768 (4×4).
- 2048 with no undo.
- 2048 in under 10 min.
- 256 and 512 on 3×3.
- 2048 on every size 3×3–8×8, with the target scaled for 3×3.
- 100 / 1000 games played.
- 7-day daily streak (once daily ships).

Never award achievements for hint- or power-up-assisted games.

### 4.8 Replay and share
- **v1.0 (S):** "Share result" on the game-over card and from history uses an `ACTION_SEND` text intent through the Sharesheet. No permission. Example text:
  ```
  2048 · 4×4 · 36,120 pts · best tile 4096 · 2,341 moves · 18:42 · no undo
  🟨🟧🟥🟪
  🟧🟥🟪⬛
  ⬜🟨🟧🟥
  ⬜⬜🟨🟧
  ```
  Map tile exponents to about 8 emoji squares: empty ⬜, ≤8 🟫, 16–64 🟨, 128–256 🟧, 512 🟥, 1024 🟪, 2048 🟦, ≥4096 ⬛. Daily shares use "2048 Daily #N".
- **v1.x (M):**
  - **Image share card.** Draw a Bitmap off-screen with an Android `Canvas`, or capture a composable with `rememberGraphicsLayer()` + `toImageBitmap()` (Compose 1.7+ [S29]).
  - Write the PNG to `cacheDir/share/`, which is excluded from backup by default.
  - Hand it over with `FileProvider` + `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData` for the Sharesheet preview [S25].
- **v1.x:** replay viewer (play/pause, step, speed 0.5×–8×).
- **later:** share or import a whole game as a JSON file via the Sharesheet or SAF (OFG has this [S4]).

### 4.9 Home-screen widget
- **Value:** moderate. It shows best score, the current game's score and a "Continue" tap target. A mini board can come later.
- **Glance check (verified 2026-10-28):**
  - `androidx.glance:glance-appwidget` stable is **1.2.0**; 1.3.0-alpha02 is the newest.
  - 1.2.0's runtime dependencies include `androidx.work:work-runtime-ktx:2.7.1`.
  - The `work-runtime:2.12.0` AAR manifest declares `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` [S24].
  - Stripping them with `tools:node="remove"` is possible, but WorkManager may then hit a `SecurityException` on wake-lock paths. **Not verified; don't risk it.**
- **Recommendation:**
  - **v1.x**, built as a classic `AppWidgetProvider` + `RemoteViews` (zero dependencies, zero permissions).
  - The app updates it with `AppWidgetManager.updateAppWidget` after saving a finished game or a new best, throttled to at most once every 2 s.
  - Set `updatePeriodMillis=0`, so the widget never wakes up on its own.
  - Effort M, risk low.

### 4.10 App shortcuts
- **v1.x (S).** Static `shortcuts.xml` with "New game (4×4)", "Statistics" and, once daily ships, "Daily challenge".
- Most launchers show 4. Google recommends publishing only 4 distinct shortcuts, even though the API allows 15 [S26].
- No permission. Routing is a single-activity deep link.

### 4.11 Hints / AI solver (optional assist)
- **Survey numbers:**
  - nneonneo's expectimax uses bitboards and row-table lookups. It reaches 2048/4096/8192 in 100 % of 100 games, 16384 in 94 % and 32768 in 36 %. Depth is 4–8, at 10–200 ms per move on a 2014 laptop and "10,000,000 game states per second" [S14].
  - A lighter JS expectimax with adaptive depth 4–7 wins (reaches 2048) in ~93 % of games at ~1.8 ms per move [S15].
- **Recommendation:** **v1.x** (M–L). A "Hint" button that briefly highlights the suggested direction.
  - **4×4 first:** 64-bit `Long` bitboard, 65,536-entry row move and score tables (~128 KiB each).
  - Adaptive depth by number of empty cells: >8 → 2, >4 → 3, else 4. Hard time budget of 100 ms on `Dispatchers.Default`; cancel on input.
  - Other sizes: generic array-based expectimax at depth 2, or hints disabled.
  - Every game that uses a hint is flagged **assisted**. It is excluded from best scores and achievements but kept in history.
  - An AI autoplay "watch" mode is **later**.
- **Risk:** medium. Performance on low-end ART devices is unverified, and it needs a benchmark test.

### 4.12 Tutorial / onboarding
- **v1.0 (S):** on first launch, show a 3-step inline coach mark on the live board: "Swipe to move all tiles" → "Equal numbers merge" → "Reach 2048". It can be skipped and is never shown again. Plus a "How to play" sheet in the menu covering rules, the 90/10 spawn, undo semantics and keyboard keys. play2048.co opens with an offer to "Play Tutorial" [S11].
- No multi-page carousel.

### 4.13 Quick Settings tile
- **never** (re-evaluate on request). It is technically permission-free [S17]. But a tile whose only job is to launch a game adds a service for no value. QS tiles are for toggles and status.

### 4.14 Settings
v1.0 settings and defaults:

| Setting | Values (default **bold**) | Notes |
|---|---|---|
| Theme | **System** / Light / Dark | Persisted. Fixes the legacy night mode that reset |
| Dynamic color (API 31+) | On / **Off** | Chrome only |
| Haptics | **On** / Off | `performHapticFeedback`; also respects the system touch-feedback setting [S19] |
| Animation speed | Off / Fast (0.6×) / **Normal (1.0×)** / Relaxed (1.5×) | Multiplies on top of the system animator scale. Compose already honours "Remove animations" through `MotionDurationScale` (Compose ≥1.2) [S18]. OFG exposes an animation-speed control [S4] |
| Swipe sensitivity | Low (40 dp) / **Medium (24 dp)** / High (12 dp) | Minimum travel before a swipe registers. Legacy is about 10 px (`keyboard_input_manager.js:127`). Reviewers want an adjustable sensitivity [S2] |
| Move on | **Release** / As soon as swipe is detected | Release matches legacy and the request "finalize the move … when the finger is lifted" [S10] |
| Confirm new game | **On** / Off | Only prompts when a game is in progress. Restart confirmation is praised in reviews [S10] |
| Show moves and time | On / **Off** | [S1] |
| Undo | **On** / Off (purist) | §4.1 |
| Keep screen on while playing | On / **Off** | No permission [S33] |
| Hide system bars (immersive) | On / **Off** | Replaces the legacy long-press fullscreen |
| Sound effects | On / **Off** | v1.x, when sounds ship |
| Language | opens system per-app language settings (API 33+) | §4.24 |
| Reset statistics; About (version, MIT licence, source link); open-source licences | — | — |

### 4.15 Sound effects
- **v1.x (M).**
  - About 5 one-shots: slide, merge (pitch-shift with `SoundPool` rate 0.8–1.6 by tile exponent), new best/win, game over, UI tap.
  - OGG, mono, under 20 KB each, under 150 KB in total.
  - Played through `SoundPool` with `AudioAttributes.USAGE_GAME`.
  - **Default Off.** Media volume ignores silent mode, and the original game has no sound. The official app ships "Sounds with on/off switch" [S5].
- **Licensing:** Kenney "Interface Sounds" (100 files) and "UI Audio" (50 files) are **CC0 1.0**: "free to use in personal, educational and commercial projects", credit optional [S23]. They are compatible with MIT. Keep a `LICENSES/audio/` note with the source URLs.

### 4.16 Input and gestures (the biggest perceived-quality lever)
- **v1.0 rules:**
  1. Exactly one move per gesture.
  2. Direction = the dominant axis (|dx| vs |dy|, as in legacy). Ignore the gesture if travel is below the threshold, or if it lasts more than 1 s and is under 2× the threshold (to block slow drags).
  3. Ignore multi-touch.
  4. Swipe anywhere on the game screen except the toolbar and buttons. The official app advertises this [S1].
  5. **Apply the move to the state immediately.** Animation is presentation only and interruptible. A new input during an animation snaps the current animation to its end and applies the next move. Queue depth is 1; drop anything beyond that.
  6. Edge back-gesture conflicts:
     - add `Modifier.systemGestureExclusion` on the board's left and right edges. The system honours at most **200 dp per screen edge**, counted from the bottom [S34];
     - inset the board ≥ 16 dp from the screen edges;
     - do **not** intercept back on the game screen. A Reddit tester of an all-in-one game app reports "while playing 2048 it happens often to slide back to previous menu" [S36].
  7. Keyboard and D-pad: arrows, WASD, HJKL, `DPAD_*`, U (undo), N (new game, with confirmation).
- **Review evidence:**
  - "swipe once and the game inputs two moves" [S7].
  - "ads … cause the actual game to lag … extra move" [S7].
  - "registered a literal tap as a swipe" [S2].
  - "number tiles feel sluggish / sloppy … should be more crisp" [S10].

### 4.17 Save/resume reliability (process death)
- **v1.0 (M), must:**
  - Persist after **every committed move**, and in `onStop`, using DataStore. Files land in `files/datastore/` and are written atomically [S22].
  - Saves cover the board, score, PRNG state, move log, undo snapshots, the per-size active games and the settings.
  - The UI is a pure function of the saved state. A move interrupted mid-animation restores to its final board without animation.
- **Tests:**
  - unit test: serialise → deserialise round-trip for all sizes;
  - instrumented test with "Don't keep activities" and `adb shell am kill`;
  - a rotation/resize test on a ≥600 dp window.
- **Context:** players highlight "Session Recovery" (Drew changelog: "game progress is never lost") [S9b] and "saves your progress … never get lost" (tpcstld review) [S8].

### 4.18 Backup
- **v1.0 (S):** `android:allowBackup="true"` plus:
  - `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+). Include `domain="file" path="datastore/"` and any database in both `<cloud-backup>` and `<device-transfer>`.
  - `android:fullBackupContent="@xml/backup_rules"` for API 24–30 (minSdk 24).
- **Background:**
  - DataStore files live in `files/datastore/` and "are included in Android Auto Backup and D2D transfers" by default [S22].
  - Auto Backup is capped at 25 MB per app, end-to-end encrypted on Android 9+, and excludes `cacheDir`/`noBackupFilesDir` [S21].
  - De-Googled users: Seedvault presents itself as a D2D transfer and backs up app data [S37].
- **v1.x (S–M):** offline **Export / Import** of all data (stats, history, settings, games) as a versioned JSON file through SAF. No permission. This serves F-Droid users without Google backup.

### 4.19 Play Games Services
**never.** It requires network authentication against Play Games servers and Google Play services [S38], and violates the offline / no-INTERNET decision.

### 4.20 Import of the legacy best score
**Not possible.** Confirmed:
- The legacy app keeps `bestScore`, `gameState` and `gameStatesStack` in WebView `localStorage` (`js/local_storage_manager.js:22-24`).
- WebView stores that as LevelDB under `/data/data/com.uberspot.a2048/app_webview/Default/Local Storage/` [S27].
- That is app-private internal storage: "Other apps cannot access files stored within internal storage" [S28].
- Shared-UID tricks would need the same signing key. Uberspot's Play key and F-Droid's key are not available to us.
- The old app cannot be updated by us to add an exporter.

The only theoretical path is a power-user one. The legacy app targets SDK 28, which is below the Android 12 `adb backup` cutoff of target ≥31 [S39], so `adb backup com.uberspot.a2048` still yields its data. That would still need a manual entry UI that anyone could fake. **never.**

Instead, say in the store listing and README: "new app with a new package name; you can keep the old one installed side by side".

### 4.21 Accessibility
**v1.0 (M), must:**
- Board semantics:
  - `collectionInfo(rows, cols)`;
  - each cell labelled "Row 2, column 3, 128" or "empty";
  - one focusable board node with **custom accessibility actions** "Move up/down/left/right", "Undo" and "Read board". TalkBack consumes swipe gestures, so swipes alone are not enough.
- After each move, a polite live-region announcement: "Merged two 64s into 128. Score 1,234".
- Win and game over announced, with focus moved to the dialog.
- Minimum touch targets of 48 dp.
- Tile text auto-fits, and numbers are always present so colour is never the only cue.
- "Remove animations" is respected (Compose `MotionDurationScale`) [S18].
- Prior art: VoiceOver-first 2048 apps use "Magic Tap" to hear the game status and give a sound per tile [S40].

### 4.22 Adaptive layout (tablets, foldables, landscape, desktop windowing)
- **v1.0 (M), must.** For target 36/37 on sw ≥ 600 dp, `screenOrientation`, `resizeableActivity`, `min/maxAspectRatio` and `setRequestedOrientation()` are ignored. The API-36 opt-out is removed at API 37 [S16][S41].
- Apps that declare `android:appCategory="game"` are exempt [S41]. The exemption is **not a reason to skip adaptivity**: split-screen, desktop windows and unfolded foldables still produce wide or short windows, and a portrait-only layout wastes them.
- **Layout:**
  - compact width: portrait stack (header, board, actions);
  - medium/expanded or landscape: board on the left or centre, score and stats panel and actions on the side.
- OFG has dedicated portrait and landscape layouts [S4]. Quovad advertises "layouts for phones, tablets, and foldable screens" [S9].

### 4.23 Haptics mapping
- **v1.0 (S).** Use Compose `LocalHapticFeedback` (Compose ≥1.8 adds `Confirm`, `Reject`, `SegmentTick`, `SegmentFrequentTick`, `GestureThresholdActivate`, `ToggleOn/Off`, …) [S20b] or `View.performHapticFeedback(HapticFeedbackConstants.*)` [S19].
- **Mapping:**
  - move with no merge → none;
  - merge → `SegmentTick` (one per move, not per merge);
  - merge ≥ 512 → `Confirm`;
  - new best / win → `Confirm`;
  - game over / invalid swipe → `Reject`;
  - settings toggles → `ToggleOn/Off`.
- The same mapping is in 04-design-motion.md §5.

### 4.24 Localisation
- **v1.0 (S):**
  - English source strings.
  - Port the ru/uk game strings from `js/i18n.js:29-58` where meanings match.
  - Declare `android:localeConfig` via AGP `generateLocaleConfig` (AGP 8.1+) so the app appears in the API 33+ per-app language settings [S26b].
  - Set `supportsRtl="true"`: legacy ships `values-fa`.
- **v1.x:** translations for the 20 legacy locales.
- **Risk:** low. The legacy `res/values-*` hold only 4 shell strings, so little can be reused. Machine translation needs native review.

### 4.25 Other items considered
| Item | Verdict |
|---|---|
| Ads, IAP, analytics, crash-reporting SDKs | **never** (INTERNET; also the #1 complaint in every rated listing [S1][S2][S7]) |
| Notifications / reminders | **never** (runtime `POST_NOTIFICATIONS`) |
| Cloud sync, online leaderboards, multiplayer | **never** (network) |
| Wear OS app / tile | later (separate module; low demand) |
| Android TV launcher entry | later (needs a leanback banner; D-pad already handled by v1.0 keyboard support) |
| "What's new" sheet (replaces ckchangelog) | v1.x (S) |
| Crash diagnostics without network: "Copy diagnostic info" / share a log through the Sharesheet | later (S) |
| Multiple save slots per size | later. v1.0 already keeps one active game per size, like PF2048's 4 slots [S3] |

---

## 5. Master roadmap table

| # | Feature | User value | Effort | Risk | Tier | Rationale |
|---|---|---|---|---|---|---|
| 1 | Classic rules parity (2 start tiles, 90/10 spawn, merge-once, score, win 2048, Keep going, game-over detection) | Core | M | Low (golden tests from legacy rules) | **v1.0** | Parity is non-negotiable; reviewers notice the 10 % 4-rate [S2] |
| 2 | Seeded app-owned PRNG (e.g. SplitMix64 seeding + xoshiro/PCG) with serialisable state and golden vectors | Fairness, enables undo determinism, daily, replay | S | Low | **v1.0** | `kotlin.random.Random(seed)` may change algorithm between Kotlin versions [S30] |
| 3 | Per-game move log + snapshots | Unlimited undo, replay, history | S–M | Low | **v1.0** | Cheap now, a migration later |
| 4 | Board sizes 3×3–8×8, per-size saves and bests, 3×3 target 256 | High; standard in top apps | M | Medium (tile text fitting at 7×7/8×8) | **v1.0** | Androbaby 10M+, OFG, PF2048, Quovad [S3][S4][S7][S9] |
| 5 | Unlimited undo, no prompt, works after game over, counter, PRNG restore, Off switch | Very high; top complaint elsewhere | M | Low | **v1.0** | [S2][S8]; fixes legacy bugs (§2) |
| 6 | Robust save/resume (every move, atomic, process death) | Very high | M | Medium (needs instrumented tests) | **v1.0** | Losing a long game is unforgivable |
| 7 | Input: one move per gesture, dp threshold, commit on release, immediate state + interruptible animation, queue 1, gesture exclusion, keyboard/D-pad | Very high; main "premium feel" driver | M | Medium (tuning across devices) | **v1.0** | [S2][S7][S10][S36] |
| 8 | Settings (theme, dynamic color, haptics, animation speed, swipe sensitivity, move-on, confirm new game, show moves/time, undo, keep screen on, hide system bars) | High | M | Low | **v1.0** | Replaces legacy night mode and fullscreen |
| 9 | Themes: System/Light/Dark + high-contrast + distinct colours for tiles above 2048 | High | S–M | Low | **v1.0** | Legacy night mode was not persisted; reviewer request [S2] |
| 10 | Haptics via `performHapticFeedback` | Medium–high | S | Low | **v1.0** | No `VIBRATE` needed [S19] |
| 11 | Statistics schema (full) + stats screen (core block) + local top-10 | High | M | Low | **v1.0** | "Detailed statistics" is a headline feature of the official app [S1] |
| 12 | Game-over / win cards with score, best tile, moves, time, undos; "Keep going"; "Share" (text + emoji) | High | S | Low | **v1.0** | Undo transparency request [S2] |
| 13 | Onboarding coach marks + How to play | Medium | S | Low | **v1.0** | play2048 tutorial [S11] |
| 14 | Accessibility (TalkBack actions, announcements, reduced motion, contrast) | High for affected users | M | Medium (needs TalkBack QA) | **v1.0** | [S18][S40] |
| 15 | Adaptive layouts (landscape, tablet, foldable, desktop window) | High on large screens | M | Medium | **v1.0** | Required behaviour at target 36/37 [S16][S41] |
| 16 | Backup rules (dataExtractionRules + fullBackupContent) | Medium (device migration) | S | Low | **v1.0** | [S21][S22] |
| 17 | Per-app language (localeConfig) + supportsRtl + ru/uk game strings | Medium | S | Low | **v1.0** | [S26b] |
| 18 | CI permission gate (`aapt2 dump permissions`) | Protects the core promise | S | Low | **v1.0** | Catches Glance/WorkManager drift [S24] |
| 19 | About + OSS licences + source link | Low–medium | S | Low | **v1.0** | Replaces the changelog dialog |
| 20 | Daily challenge (seeded, local date, no undo, streak, emoji share, archive) | High engagement | M | Low–medium (seed spec must be frozen) | **v1.x** | Popular on the web [S13]; offline-friendly |
| 21 | History (last 100) + replay viewer | Medium–high | M | Low | **v1.x** | OFG [S4]; review request [S10] |
| 22 | Sound effects (CC0 Kenney, `SoundPool`, default off) | Medium | M | Low | **v1.x** | Official app has sounds with a switch [S5]; CC0 [S23] |
| 23 | Achievements (local) | Medium | M | Low | **v1.x** | Quovad has per-board achievements [S9] |
| 24 | Hints (expectimax 4×4, ≤100 ms, assisted flag) | Medium | M–L | Medium (perf on low-end) | **v1.x** | [S14][S15] |
| 25 | Image share card (Canvas/GraphicsLayer → FileProvider) | Medium | S–M | Low | **v1.x** | [S25][S29] |
| 26 | Home-screen widget (RemoteViews, not Glance) | Low–medium | M | Low (Glance: High) | **v1.x** | Glance adds 4 permissions [S24] |
| 27 | App shortcuts (≤4 static) | Low–medium | S | Low | **v1.x** | [S26] |
| 28 | Export/Import data (SAF JSON) | Medium for F-Droid users | S–M | Low | **v1.x** | Covers users without Google Drive backup |
| 29 | Redo | Low–medium | S | Low | **v1.x** | Trivial once #3 exists |
| 30 | Extra palettes / tile-label styles | Low–medium | S | Low | **v1.x** | Drew's themes [S9b] |
| 31 | "What's new" sheet | Low | S | Low | **v1.x** | — |
| 32 | Time trial / time attack | Medium | M | Medium | later | [S5][S9b] |
| 33 | Zen / limited moves / custom rules / spawn variants | Low–medium | M each | Low | later | [S10] |
| 34 | Power-ups (swap, delete-by-value), checkpoints / Tile Play / bookmarks | Low–medium | M | Low | later | [S5][S11] |
| 35 | AI autoplay "watch" mode | Low | S (after #24) | Low | later | OFG [S4] |
| 36 | Share/import whole games as files | Low | M | Low | later | OFG [S4] |
| 37 | Wear OS, Android TV launcher | Low | L | Medium | later | — |
| 38 | Rectangular boards | Low | M | Medium | never/later | No surveyed app has them |
| 39 | Quick Settings tile | Very low | S | Low | never | §4.13 |
| 40 | Notifications / reminders | — | — | Violates constraint | **never** | Runtime permission |
| 41 | Play Games, online leaderboards, cloud sync, ads, analytics | — | — | Violates constraint | **never** | INTERNET |
| 42 | Custom vibration patterns (`Vibrator`) | Low | S | Adds `VIBRATE` | **never** | [S20] |
| 43 | Legacy best-score import | — | — | Impossible | **never** | §4.20 |

---

## 6. v1.0 acceptance checklist (concrete)

1. All 6 square sizes are playable. Each keeps its own active game, best score, no-undo best and stats bucket. The 3×3 target is 256; every other size is 2048. "Keep going" works after the target.
2. Spawn rules: a uniform empty cell, value 4 with probability exactly 1/10. The engine's PRNG is app-owned and seeded per game. Unit tests check golden sequences for fixed seeds, and that undo then the same move gives the same spawn.
3. Undo:
   - unlimited, with no dialog;
   - available on the game-over screen;
   - the counter shows on the game-over card;
   - with "Undo: Off" the button is hidden and results feed the no-undo bests.
4. Process death: kill the app mid-game on any size, relaunch, and the board, score, undo history and timer state are identical. Covered by an instrumented test.
5. Gestures:
   - one move per gesture;
   - default threshold 24 dp;
   - commit on release by default;
   - rapid swipes during animation are neither lost nor duplicated (queue depth 1);
   - board edges carry gesture exclusion of ≤200 dp per side.
6. Keyboard: arrows, WASD, HJKL and D-pad move; U undoes; N starts a new game (with confirmation if a game is in progress).
7. Settings from §4.14 persist and are honoured. Animation speed Off and the system "Remove animations" both give instant transitions.
8. TalkBack: the board reads by rows, the four move actions plus Undo work as custom actions, and merges and score changes are announced.
9. Layouts verified at 360×640 dp (portrait), 640×360 dp (landscape), 600×960, 840×1200 and a free-form desktop window. No clipping at 8×8, and tile labels fit.
10. Backup XMLs exist and include `datastore/`.
11. The CI permission gate passes: no `android.permission.*` in the release APK.
12. Share: the game-over "Share" sends text plus an emoji grid through the Sharesheet with no permission prompt.

---

## 7. Data-model decisions to make in v1.0 (so v1.x needs no migration)

- **GameState:**
  - `size: Int` (3..8)
  - `cells: ByteArray(size*size)` holding exponents, 0 = empty
  - `score: Long`
  - `rngState: LongArray(2..4)`
  - `seed: Long`
  - `moves: Int`
  - `moveLog: ByteArray` (packed 2 bits per move)
  - `snapshots` (every 32 moves)
  - `undoCount: Int`
  - `assisted: Boolean`
  - `reachedTarget: Boolean`, `keepPlaying: Boolean`
  - `elapsedMs: Long`
  - `mode` (CLASSIC, DAILY, later others)
  - `schemaVersion`
- **Stats:** a bucket per (mode, size) as in §4.5. Store the **tile-reach histogram as counts per exponent** so new stats can be derived later without migration.
- **Daily seed spec (freeze in v1.x before release):**
  - `seed = first 8 bytes of SHA-256("2048-daily-v1|" + yyyy-MM-dd)` (local date);
  - puzzle number = `epochDay - launchEpochDay + 1`.
- **History:** a ring buffer of 100 finished games: GameState summary plus move log, so replay is exact.
- **Settings:** a separate DataStore file, so a user could later exclude it from backup.

---

## 8. Notes on specific numbers

- Win-target table and theoretical max tiles: §4.2 [S31][S32].
- Expected moves to the first 2048 on 4×4: each move adds exactly one tile (expected value 2.2). The board sum at that moment is ≥ 2048, so a win takes at least ~930 moves on average. "Fewest moves to 2048" therefore varies little; "fastest time" is the more meaningful record. This is derived arithmetic, not a sourced figure.
- Expectimax reference performance: [S14][S15].
- Undo memory: 8×8 snapshot ≈ 64 B cells + 8 B score + 16–32 B RNG ≈ ~100 B. Snapshots every 32 moves plus a 2-bit log give O(n/32) memory.

---

## 9. What makes top-rated 2048 apps feel premium vs cheap (from reviews)

**Cheap:**
- **Ads.** This is the dominant complaint on the official app (4.7★ yet "an ad after every game … you can't exit out of" [S1]; "Ads … unusable" [S2]) and on Androbaby ("ad-delivery software" [S7]).
- **Ad-induced lag causing unintended extra moves**: "when the ads swap, it causes the actual game to lag … extra move" [S7].
- **Input errors**:
  - "swipe once and the game inputs two moves" [S7];
  - "registered a literal tap as a swipe" and "no indication … whether or not the undo button was used" [S2];
  - "I swiped right and it went down" [S2].
- **Stingy or unreliable undo**: "only undoes one move" [S8]; "undo glitch … doesn't undo" [S2].
- **Suspicion of rigged RNG** ("starts spitting out 4-tiles … counting three swipes instead of one" [S7]). This is perception, but opaque randomness feeds it.
- **Sluggish motion**: "tiles feel sluggish / sloppy … tuned to be more 'crisp'" [S10].
- **Monotone high tiles** ("black color gets … boring" [S2]).
- **Bloat**: the official iOS app is 75.7 MB [S2].
- **Back-gesture conflicts** [S36].

**Premium:**
- **No ads, no tracking, offline**. This is the headline for the best-loved small apps: "Literally is nothing but everything it needs to be … perfectly responsive … generous undo" [S8]; "Good gameplay, no ads … no loading time … takes up kilobytes" [S10].
- **Crisp, fast motion** (the original's 100 ms slide) with a satisfying merge pop. The "silky-smooth movements" claim on the official listing [S1].
- **Input that is never wrong**: deterministic one-move-per-swipe, adjustable sensitivity, optional commit-on-release [S2][S10].
- **Never losing progress**: "saves your progress … would never get lost" [S8].
- **A generous, transparent undo**, with honest scoring (undo counts, no-undo bests, a retries counter [S4]).
- **Confirmation only where it protects work** ("asking for confirmation before restarting — Love it!" [S10]).
- **Calm presentation**: an option to hide timer and moves [S1]; "not too many colours. no ads" helps an ADHD player use it as "digital doodling" [S1].
- **Depth for enthusiasts**: statistics, sizes, history and replay, without clutter for casual players.
- **Respect for the system**: dark theme, reduced motion, haptics setting, adaptive layouts, accessibility.

---

## 10. Open questions and assumptions for the plan

1. **Hint/AI scope.** Recommended v1.x, flagged assisted. If the user wants v1.0 "modernization" to include it, ship 4×4-only hints behind a setting.
2. **Daily challenge date basis.** Local date (Wordle-style) is recommended over UTC. Either way, freeze the spec before the first release that ships it.
3. **Widget.** Recommended RemoteViews in v1.x. Do not adopt Glance unless the permission gate is relaxed.
4. **Defaults for timer/moves HUD, sound and keep-screen-on** are product calls. Recommended: off, off, off.

---

## 11. Sources

- [S1] Google Play, "2048" by Solebon LLC (official). Rating, features, 2026 reviews, "What's new": https://play.google.com/store/apps/details?id=com.gabrielecirulli.app2048&hl=en_US (fetched via Parallel extract, acquired 2026-10-10)
- [S2] App Store, "2048 by Gabriele Cirulli". Reviews, IAP "Remove ads $9.99", size 75.7 MB, v5.2.1: https://apps.apple.com/us/app/2048-by-gabriele-cirulli/id868076805
- [S3] F-Droid, 2048 (Privacy Friendly), v1.4.2, 2025-07-28, permissions list: https://f-droid.org/en/packages/org.secuso.privacyfriendly2048/
- [S4] F-Droid, 2048 Open Fun Game, v1.16.2, 2026-07-21; 1.15.1 "No permissions": https://f-droid.org/en/packages/org.andstatus.game2048/
- [S5] 2048original.com support (Time trial, Tile Play, pausing): http://www.2048original.com/support.html ; home: https://www.2048original.com/ ; Solebon iOS page: https://www.solebon.com/2048---ios.html
- [S6] Hack mode: https://www.2048original.com/hack-mode.html
- [S7] Google Play, "2048" by Androbaby (290K reviews, 10M+, one undo, review complaints): https://play.google.com/store/apps/details?hl=en_US&id=com.androbaby.game2048
- [S8] Google Play, "2048" by tpcstld (no ads, one undo, reviews): https://play.google.com/store/apps/details?id=com.tpcstld.twozerogame&hl=en
- [S9] Google Play, "2048: Number Puzzle Game" (Quovad Studio): https://play.google.com/store/apps/details?id=play.game2048.blocks.number.puzzle.games&hl=en_US
- [S9b] Google Play, "2048" (Drew Developer): https://play.google.com/store/apps/details?id=io.github.drew_developer.game2048&hl=en_US
- [S10] App Store, "No Ads 2048" (modes, reviews): https://apps.apple.com/us/app/no-ads-2048/id6465691202
- [S11] play2048.co (power-ups: Undo 2 uses/128, Swap/256, Delete/512; tutorial): https://play2048.co/ ; about: https://play2048.co/about
- [S12] andstatus/game2048 README (features, tile labels, v1.16.2): https://github.com/andstatus/game2048
- [S13] 2048.is daily challenge (date-derived board, spawn sequence and target): https://2048.is/daily ; also https://www.onlinepuzzles.org/daily/2048 , https://2048.now/daily
- [S14] nneonneo expectimax: StackOverflow answer (performance table, depth, 10M states/s): https://stackoverflow.com/questions/22342854/what-is-the-optimal-algorithm-for-the-game-2048/22498940 ; repo: https://github.com/nneonneo/2048-ai
- [S15] LK Forge, "How the 2048 AI Solver Works" (adaptive depth, ~93 % wins, 1.8 ms/move): https://lkforge.com/games/2048/blog-how-the-ai-solver-works
- [S16] Android 16 behaviour changes (predictive back default, `onBackPressed` not called; edge-to-edge opt-out removed; large-screen orientation ignored): https://developer.android.com/about/versions/16/behavior-changes-16
- [S17] Quick Settings tiles guide and TileService reference: https://developer.android.com/develop/ui/views/quicksettings-tiles ; https://developer.android.com/reference/android/service/quicksettings/TileService
- [S18] Compose `MotionDurationScale` and the animator duration scale: https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale ; https://issuetracker.google.com/issues/265177763 ; Compose Animation release notes ("supports 'Animator duration scale'"): https://developer.android.com/jetpack/androidx/releases/compose-animation
- [S19] "Add haptic feedback to events": View approach "doesn't require the VIBRATE permission": https://developer.android.com/develop/ui/views/haptics/haptic-feedback
- [S20] Android haptics API reference ("Playing any VibrationEffect … requires the VIBRATE permission"): https://developer.android.com/develop/ui/views/haptics/haptics-apis
- [S20b] Compose `HapticFeedbackType` (new types in 1.8): https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType ; https://developer.android.com/jetpack/androidx/releases/compose-ui
- [S21] Auto Backup (25 MB, E2E encryption on Android 9+, include/exclude, D2D): https://developer.android.com/identity/data/autobackup ; Android 12 backup changes: https://developer.android.com/about/versions/12/behavior-changes-12
- [S22] DataStore guide ("files/datastore/ … included in Auto Backup and D2D", sample rules): https://developer.android.com/topic/libraries/architecture/datastore
- [S23] Kenney Interface Sounds (CC0, 100 files): https://kenney.nl/assets/interface-sounds ; UI Audio (CC0, 50 files): https://kenney.nl/assets/ui-audio
- [S24] Google Maven (verified 2026-10-28): glance-appwidget 1.2.0 module (runtime dep `androidx.work:work-runtime-ktx:2.7.1`): https://dl.google.com/android/maven2/androidx/glance/glance-appwidget/1.2.0/glance-appwidget-1.2.0.module ; work-runtime 2.12.0 AAR manifest (WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE): https://dl.google.com/android/maven2/androidx/work/work-runtime/2.12.0/work-runtime-2.12.0.aar ; core 1.19.1 AAR (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION): https://dl.google.com/android/maven2/androidx/core/core/1.19.1/core-1.19.1.aar ; glance metadata (latest 1.3.0-alpha02): https://dl.google.com/android/maven2/androidx/glance/glance-appwidget/maven-metadata.xml
- [S25] Send data to other apps (Sharesheet, FileProvider `<cache-path>`, per-URI permissions): https://developer.android.com/develop/ui/compose/sharing/send ; https://developer.android.com/training/sharing/send
- [S26] App shortcuts (static `shortcuts.xml`; "publish only four distinct shortcuts", API allows 15; launchers show 4): https://developer.android.com/guide/topics/ui/shortcuts/best-practices ; https://developer.android.com/develop/ui/compose/system/shortcuts
- [S26b] Per-app language preferences (`localeConfig`, AGP 8.1 auto-generation): https://developer.android.com/guide/topics/resources/app-languages
- [S27] WebView data location (`app_webview/Default/Local Storage`, LevelDB): https://issues.chromium.org/40664296 ; https://stackoverflow.com/questions/72817369/how-does-android-webview-handle-data
- [S28] App-specific storage ("Other apps cannot access files stored within internal storage"): https://developer.android.com/training/data-storage/app-specific
- [S29] Compose: write a composable to a bitmap (`rememberGraphicsLayer`, `toImageBitmap`, Compose 1.7+): https://developer.android.com/develop/ui/compose/graphics/draw/modifiers
- [S30] Kotlin `Random(seed)`: "Future versions of Kotlin may change the algorithm of this seeded number generator": https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.random/-random.html
- [S31] 3×3 variant uses a 256 win target; max 512 with 2-spawns, 1024 with 4-spawns: https://www.2048quest.com/3x3/ ; max-tile formula: https://math.stackexchange.com/questions/2500222/is-it-possible-to-win-2048-in-a-3-times-3-grid ; 4×4 max 131072: https://puzzling.stackexchange.com/questions/48/what-is-the-largest-tile-possible-in-2048
- [S32] jdlm.info MDP analysis (3×3 to 1024: ~1 % win with optimal play): https://jdlm.info/articles/2018/03/18/markov-decision-process-2048.html
- [S33] Keep the screen on (no special permission): https://developer.android.com/develop/background-work/background-tasks/awake/screen-on ; PowerManager source note: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/core/java/android/os/PowerManager.java
- [S34] `systemGestureExclusion` (Compose): https://developer.android.com/reference/kotlin/androidx/compose/foundation/systemGestureExclusion.modifier ; 200 dp per-edge limit (AOSP androidx comment): https://android.googlesource.com/platform/frameworks/support/+/2cba1950335165bd8e8250f15d62ab0a08ed05d9/pdf/pdf-viewer/src/main/java/androidx/pdf/util/SystemGestureExclusionHelper.java
- [S35] androidx DYNAMIC_RECEIVER permission background: https://gitlab.com/fdroid/fdroidserver/-/merge_requests/1336 ; https://stackoverflow.com/questions/74146297/android-adding-dynamic-receiver-not-exported-permission-in-release-build
- [S36] Reddit r/iosgaming feedback on a 2048 implementation (back gesture during swipes): https://www.reddit.com/r/iosgaming/comments/1plhoso/i_got_tired_of_downloading_20_different_apps_just/
- [S37] Seedvault (D2D trick, allowBackup): https://blog.matthewbrunelle.com/the-backup-and-restore-experience-on-grapheneos ; https://github.com/seedvault-app/seedvault/wiki/FAQ
- [S38] Play Games Services v2 sign-in (server-side auth): https://developers.google.com/games/services/android/signin ; https://developer.android.com/games/pgs/android/migrate-to-v2
- [S39] Android 12 adb backup restriction (target ≥31 only): https://developer.android.com/about/versions/12/behavior-changes-12 ; https://developer.android.com/about/versions/12/reference/compat-framework-changes
- [S40] Accessible 2048 (VoiceOver, Magic Tap status, sounds): https://www.applevis.com/apps/ios/games/accessible-2048
- [S41] Android 17: restrictions ignored on sw ≥ 600dp, no opt-out, games exempt via `android:appCategory`: https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored ; https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability ; https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html

Network note: `play.google.com` and `f-droid.org` are blocked in this session's WebFetch. Those pages were read through the Parallel Search extract service, with cached copies dated 2026-02 to 2026-10.
