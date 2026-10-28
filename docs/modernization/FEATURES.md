# FEATURES.md: v1.0 feature specification, settings, statistics, legacy parity and roadmap

Status: plan document for the execution PR (D-160), dated 2026-10-28.

`DECISIONS.md` is canonical. This file turns its product decisions into user-visible behavior, edge cases and acceptance criteria that name the test which proves each one. When this file and `DECISIONS.md` disagree, `DECISIONS.md` wins. The tensions found during planning are settled by D-174–D-211, and this file already uses their decided text; [Open issues](#open-issues) keeps only the verification risk that remains, with its fallback.

Audience: the agents that execute milestones M0–M6 (D-162, D-194) with no other context. Read `DECISIONS.md`, then `ARCHITECTURE.md` (code shape, file map, data flows), then `DESIGN_SYSTEM.md` (visual tokens, screens, goldens), then this file.

What this file owns:
- the behavior of every v1.0 feature (§1), with numbered acceptance criteria (ACs) mapped 1:1 to test methods;
- the settings catalog (§2) and the statistics catalog (§3);
- the legacy parity matrix (§4) and the legacy bug-fix proofs (§5);
- the roadmap beyond v1.0 and the data-model hooks it relies on (§6);
- the non-goals (§7) and the test class index (§8).

What it does not restate: build files, module layout and class APIs (`ARCHITECTURE.md`), colors, type, geometry and screen layouts (`DESIGN_SYSTEM.md`). It points to them by section.

"v1.0" in this file means the scope delivered by the execution PR: app version `3.0.0` / versionCode `30000` (D-130).

---

## 0. Conventions

### 0.1 IDs and traceability

- Features are `F01`–`F30`. Acceptance criteria are `Fnn-ACk`; there are 371, numbered without gaps within each feature.
- Every AC maps to **exactly one** test method, written `Class.method`, or to one gate step (`Gate: …`). Every test method named in §1 implements exactly one AC of this file. The few cross-cutting rules (the ATF rule on every golden except `motion_*`) say so explicitly.
- A test method may prove several ACs that assert the same fact, for example an F-AC of this file and an AC of MOTION_SPEC.md or L10N_STORE_LEGAL.md; its first KDoc line lists every ID (e.g. `/** F13-AC8, AC-GOLD-1 */`). The Test column names those extra IDs as "(also …)".
- The first line of each test method's KDoc lists its AC IDs, for example `/** F05-AC4 */`, so `grep -rn "F05-AC4" app/src/test core/engine/src/test` finds the proof.
- §4 (parity) and §5 (bug fixes) reference AC IDs; they add no test methods of their own.
- A renamed or moved test method is updated here in the same commit (D-173, "other docs … stay consistent").
- Gate references:
  - `Gate: check-apk.sh step N` is item N of D-148, as implemented by `scripts/check-apk.sh` (ARCHITECTURE.md §8.6).
  - `Gate: check-metadata.sh <function>` is a function of `scripts/check-metadata.sh` (D-152), named as in QUALITY.md §1.16: for example `check_license_file`, `check_no_bom` and `check_icon_glyphs` (M0, D-179), `check_font` (M4), and `check_store_listing`, `check_store_images`, `check_legal_and_privacy`, `check_legal_texts` and `check_strings_final` (M5, D-190).
  - `Gate: lintDebug <IssueId>` is Android Lint under D-142.

### 0.2 Test kinds and harness

| Kind | Where | Harness |
|---|---|---|
| Engine | `core/engine/src/test/kotlin/com/clayboicardi/game2048/engine/` | JUnit 6 + kotest-property; `PropertyTesting.defaultSeed = 0x2048` set in `@BeforeEach`; 1,000 iterations via `checkAll(1_000, …)`; block-bodied tests (D-145, TC-6) |
| App JVM | `app/src/test/kotlin/com/clayboicardi/game2048/…` | JUnit 4, `MainDispatcherRule(StandardTestDispatcher())`, Turbine, the real DataStore over `TemporaryFolder` files through `TestAppContainer` (ARCHITECTURE.md §4.2) with `FakeClock` and `FixedSeedSource(0x2048)` |
| Robolectric | same tree | `robolectric.properties` `sdk=36`; Compose ui-test **v2** `createComposeRule()`/`createAndroidComposeRule<MainActivity>()`; `@GraphicsMode(NATIVE)` on rendering tests (D-146) |
| Screenshot | `…/screenshots/` | Roborazzi goldens numbered as in DESIGN_SYSTEM.md §10.4 (45 PNGs, D-204); ATF on every golden except `motion_*` (D-085, D-204); `assertTouchTargets()` on every screen-level test (D-205) |
| Gate | `scripts/*.sh`, Lint | Run by `scripts/verify.sh` (D-140) |

Timing ACs in Robolectric:
- Timing ACs use MOTION_SPEC.md §0.1 as the only time base (D-183): "t = X ms" means X ms after the origin F1, the first animation frame after the frame (F0) that composes the new `BoardUi`, on the 16 ms test-frame grid. An offset is due on the first frame ≥ origin + offset × animator scale. Tests set `composeRule.mainClock.autoAdvance = false` and step with `MotionHarness.advanceTo`.
- The system animator duration scale is 1.0 unless the AC sets it: through the `AnimatorScaleSource` fake that `TestAppContainer` injects, or, when the AC tests `SystemAnimatorScaleSource` itself, through `Settings.Global.ANIMATOR_DURATION_SCALE` (Robolectric `Settings.Global.putFloat`, followed by `contentResolver.notifyChange(Settings.Global.getUriFor(…), null)` when the live observer must fire).

API-gated ACs:
- "Present on API ≥ N" runs at the default SDK 36.
- "Absent below API N" runs with `@Config(sdk = [24])`, the SDK that `LaunchSmokeTest` already downloads (D-146), so no extra android-all jar is fetched. SDK 24 runs on JDK 21 under Robolectric 4.17 (QUALITY.md V4).

Fixtures:
- `FIX_*` board and records fixtures are DESIGN_SYSTEM.md §10.3 (`fixtures/GoldenFixtures.kt`); the motion fixtures (`FIX_WIN_PRE_4`, `FIX_OVER_PRE_4`, `FIX_WON_NO_MOVES_4`, …) are MOTION_SPEC.md §8.6 (`fixtures/MotionFixtures.kt`).
- The 10,000-move 8×8 game is `testing/LongGameGenerator.kt` (ARCHITECTURE.md §9.4).
- Committed JSON fixtures are `app/src/test/resources/fixtures/v1/*.json` (D-041).

### 0.3 Terms

| Term | Meaning |
|---|---|
| Accepted move | A move that changed the board (D-035, DECISIONS Conventions) |
| In progress | `moveCount ≥ 1` and status `PLAYING` or `KEEP_PLAYING` (DECISIONS Conventions; `GameState.isInProgress`) |
| Card | The win card or the game-over card (D-008) |
| Modal | A card, dialog, bottom sheet, the game menu or the What's new sheet (D-054, ARCHITECTURE.md §6.6). Every modal blocks board swipes and move keys; under a card the chrome controls and the U/N/R keys stay active (D-199) |
| Full / Reduced / Off | The **effective** motion level, `MotionLevel` (D-063): Off whenever the system animator scale is 0 |
| Commit | The input callback in which an accepted move, undo or new game is applied (D-052) |
| Origin | F1: the first animation frame after the frame (F0) that composes the new `BoardUi` (MOTION_SPEC.md §0.1, D-183). Every "t = X ms" and every "from commit" time counts from it |
| T0 | Win: merge reveal = origin + 80 ms. Game over: origin + 300 ms (D-064, D-183) |
| Game numeral | Tile values, score, best, "+N", share text: `Long.toString()`, Latin digits, no grouping (D-103) |
| Prose number | Any other number in a sentence: `formatCount(n, locale)`, Latin digits with locale grouping (D-103) |
| Fresh game | `SavedGame(Game.newGame(size, seedSource.nextSeed()), elapsedMs = 0, bestAtStart = records.best(size), newBestAnnounced = false, gameId = records.nextGameId(size, replaced), previousFinalization = null)` (ARCHITECTURE.md §4.9) |
| Finalize | The D-044 record update when New game starts on a size, applied only if the old game is `OVER` or has `score > 0`. It is keyed by the game's `gameId` and is a no-op for a game already finalized |
| Recovery entry | A fresh game's `previousFinalization`: the finalization input of the game it replaced, so that a lost records write is redone on the next load (D-044, ARCHITECTURE.md §4.9) |

### 0.4 Feature index

"Milestones" lists the milestones that create the files of the "Main code" column and the milestones in which the feature's ACs first pass (§8). A gate AC counts from the milestone that adds its gate.

| F | Feature | Milestones | Decisions | Main code (ARCHITECTURE.md §10) |
|---|---|---|---|---|
| F01 | Classic rules | M1–M2 | D-030–D-038 | `engine/*`, `game/GameViewModel.kt` |
| F02 | Board sizes and size switching | M1–M4 | D-007, D-030, D-036, D-075, D-077, D-181, D-182 | `overlay/BoardSizeSheet.kt`, `board/BoardGeometry.kt`, `board/TileLabels.kt` |
| F03 | Win and Keep going | M1–M4 | D-008, D-036, D-064, D-183, D-199, D-200 | `overlay/WinCard.kt`, `overlay/OverlayController.kt` |
| F04 | Game over | M1–M4 | D-008, D-037, D-064, D-183, D-185, D-200 | `overlay/GameOverCard.kt`, `overlay/OverlayController.kt` |
| F05 | Unlimited undo | M1–M4 | D-039, D-043, D-182, D-186 | `engine/Game.kt`, `game/GameViewModel.kt` |
| F06 | New game and confirmation | M2–M3 | D-007, D-044, D-182 | `overlay/NewGameDialog.kt` |
| F07 | Auto-save, resume, process death, splash | M1–M3 | D-040–D-047, D-112 | `data/*`, `SplashGate.kt` |
| F08 | Statistics and records | M2, M4 | D-044, D-198 | `data/records/*`, `stats/*` |
| F09 | New-best moment | M2–M3 | D-044, D-064, D-065, D-183 | `header/NewBestBadge.kt` |
| F10 | Share result (text) | M3 | D-008, D-103, D-200 | `share/ShareText.kt` |
| F11 | Settings screen | M2, M4 | D-006 | `settings/*`, `data/settings/*` |
| F12 | Themes, palettes, high contrast, dynamic chrome | M3–M4 | D-070–D-074, D-078, D-189, D-205–D-207 | `ui/theme/*` |
| F13 | Motion setting and reduced motion | M2–M3 | D-060–D-064, D-183, D-184, D-210 | `ui/theme/GameMotion.kt`, `ui/theme/EffectiveMotion.kt`, `motion/MoveTimeline.kt` |
| F14 | Haptics | M2–M4 | D-065, D-066, D-185 | `feedback/HapticsMap.kt`, `feedback/HapticsController.kt` |
| F15 | Swipe input | M2–M4 | D-050–D-052 | `input/SwipeDetector.kt`, `input/SwipeClassifier.kt` |
| F16 | Keyboard, D-pad and gamepad | M3–M4 | D-053, D-054, D-199, D-203 | `input/KeyboardInput.kt` |
| F17 | Accessibility | M3–M4 | D-080–D-085, D-202, D-204, D-205 | `board/Board.kt`, `StatusLine.kt`, `input/DirectionPad.kt` |
| F18 | Adaptive layouts and orientation | M4 | D-051, D-090–D-092, D-095, D-188 | `GameLayout.kt`, `ui/components/WindowMode.kt`, `game/board/BoardGeometry.kt` (`resolveGameLayout`) |
| F19 | Edge-to-edge and Immersive mode | M0, M3–M4 | D-093 | `MainActivity.kt`, `ui/components/ImmersiveMode.kt` |
| F20 | Keep screen on | M4 | D-006 | `ui/components/KeepScreenOn.kt` |
| F21 | Navigation and predictive back | M3 | D-023, D-054, D-094 | `NavKeys.kt`, `AppNavHost.kt`, `GameMenu.kt` |
| F22 | First-launch hint | M2–M4 | D-009, D-188, D-201, D-202 | `overlay/FirstLaunchHint.kt` |
| F23 | How to play | M4 | D-009 | `about/HowToPlayScreen.kt` |
| F24 | What's new | M4–M5 | D-009, D-181 | `about/WhatsNewPolicy.kt`, `about/WhatsNewSheet.kt` |
| F25 | About and credits | M4 | D-009, D-124, D-209 | `about/AboutScreen.kt` |
| F26 | Open-source licenses and legal files | M0, M4–M5 | D-120–D-122, D-190 | `about/LicensesScreen.kt`, `LICENSE`, `NOTICE` |
| F27 | Privacy and the offline guarantee | M0, M4–M5 | D-002, D-123, D-148, D-190 | manifest, `PRIVACY.md` |
| F28 | Localization, per-app language, RTL, digits | M0, M3–M5 | D-100–D-106, D-208 | `values*/strings.xml`, `ui/components/Formatting.kt` |
| F29 | Backup and device transfer | M0, M2 | D-048 | `xml/*_rules.xml` |
| F30 | Identity: label, icon, splash, versions, store listing | M0, M2–M5 | D-110–D-116, D-130–D-132, D-134, D-190 | manifest, `res/mipmap*`, `fastlane/**`, `di/AppContainer.kt` (version from `BuildConfig`) |

---

## 1. v1.0 features

Each feature lists: user story, behavior, edge cases, settings involved, persistence touched, and the acceptance criteria. Behavior items cite the governing D-IDs; UI details defer to the DESIGN_SYSTEM.md section named.

### F01 — Classic rules

**User story.** As a player of the original 2048, I want identical sliding, merging, spawning and scoring, so that the game feels right and my scores compare with any classic 2048.

**Behavior.**
1. A move takes one `Direction` (codes Up 0, Right 1, Down 2, Left 3; D-031). Each of the N lines is processed starting at the wall the move goes toward. Each tile slides to the farthest empty cell. A tile that meets an equal exponent which was not produced by a merge in this move merges into exponent + 1 at that cell. A merged tile never merges again in the same move (D-032).
2. Score increases by the value of every tile created by a merge, `2^(exp+1)`. There are no bonuses or multipliers (D-033). The move's `scoreDelta` drives the "+N" float (F13) and the TalkBack status line (F17).
3. A move is **accepted** iff at least one tile changed cell or merged. After an accepted move exactly one tile spawns: value first (`rng.nextInt(10) == 0` → 4, else 2), then a uniform pick among the empty cells listed in row-major order (D-034). Then the win check (F03) runs, then the game-over check (F04). Both run **after** the spawn.
4. A no-op move returns `MoveOutcome.Invalid`: no RNG draw, no spawn, no history entry, no change to score, `moveCount` or `undoCount`, and no write (D-035). The UI shows the invalid-move nudge (F13), the rate-limited `Reject` haptic (F14) and, under TalkBack, "Can't move {direction}." (F17).
5. In `WON` or `OVER`, `Game.move` returns `Blocked(status)`. The UI never calls it there, because cards are modal (D-054).
6. Randomness is the app-owned SplitMix64 (D-038). Each game's seed comes from `SeedSource` (`SecureRandom().nextLong()`) and is stored as `seed`; the generator state is stored as `rngState`. `kotlin.random.Random` and `java.util.Random` are never used for game logic.
7. A new game has an empty board, `rngState = seed`, then two spawns (D-034).

**Edge cases.**
- `2 2 2 2` LEFT gives `4 4 _ _` (+8), never `8 _ _ _`. `4 2 2 _` LEFT gives `4 4 _ _` (+4); merges do not chain.
- A merge-only move (`2 2 _ _` LEFT) is accepted.
- On a full board with one mergeable pair, the move is accepted and the spawn lands in a cell freed by the merge.
- After every accepted slide at least one cell is empty (ARCHITECTURE.md §3.5 I9), so an accepted move always spawns.
- A spawn with exactly one empty cell still draws `nextInt(1)`. Rejection sampling can consume more than one draw per bounded call; the golden vectors freeze the exact draw count.
- `score` saturates at `Long.MAX_VALUE` and exponents cap at 127 (D-030). Neither is reachable: the largest possible exponent is N² + 1 = 65 on 8×8.
- Diagonal, too-short and multi-touch gestures never reach the engine (F15).

**Settings involved.** None. Rules and win targets are not configurable (D-036 rejects a configurable target).

**Persistence touched.** `game-N.json` after every accepted move (D-043). `records.json` when a best increases (F08). Nothing for `Invalid` or `Blocked`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F01-AC1 | A 4×4 board whose row 0 is each of the 13 D-032 row-left inputs, other rows empty | The internal `slide(cells, LEFT)` (ARCHITECTURE.md §3.3) runs | Row 0 equals the D-032 output, `scoreDelta` equals Δ, and `moved` matches (only `2 4 8 16` is not moved) | `EngineGoldenTest.rowLeftGoldens` |
| F01-AC2 | The 3 D-032 row-right rows, and all 16 rows transposed into column 0 | `slide` runs RIGHT, UP and DOWN | Each result is the D-032 output mirrored or transposed accordingly | `EngineGoldenTest.rowRightAndVerticalGoldens` |
| F01-AC3 | Random boards, N ∈ 3..8, exponents 0..17 | `slide` runs in each direction | The result equals the naive per-line oracle (filter zeros, merge equal neighbours once left to right, pad) | `EnginePropertiesTest.p2MergeOnceMatchesOracle` |
| F01-AC4 | A random state and a direction that changes nothing | `Game.move` | `Invalid(direction)`; the state is unchanged; a counting `RandomSource` saw 0 draws | `EnginePropertiesTest.p3NoOpConsumesNothing` |
| F01-AC5 | Random states | An accepted move | `scoreDelta` equals Σ `tileValue(e)` over the merged exponents, and score never decreases | `EnginePropertiesTest.p4ScoreDeltaEqualsMergedValues` |
| F01-AC6 | Random states | `slide`, then an accepted move | The value sum is unchanged by the slide and grows by exactly 2 or 4 after the spawn | `EnginePropertiesTest.p1SumConservedPlusSpawn` |
| F01-AC7 | Random states | `slide` runs | Tile count after the slide = before − `mergeCount`; the spawn adds exactly 1 | `EnginePropertiesTest.p5TileCountAfterMerges` |
| F01-AC8 | Random undo-free move sequences | Accepted moves | `board.maxExp()` never decreases | `EnginePropertiesTest.p6MaxTileNeverDecreases` |
| F01-AC9 | Random boards | Rotated or transposed board and direction | `slide(T(b), T(d)) == T(slide(b, d))` for the 4 rotations and the transpose | `EnginePropertiesTest.p7RotationAndTransposeCommute` |
| F01-AC10 | Random boards | `canMove` | `!canMove(b)` ⇔ no empty cell and no equal orthogonal pair ⇔ all four directions are no-ops | `EnginePropertiesTest.p8GameOverEquivalence` |
| F01-AC11 | A seed and a random direction list | `Game.replay` twice | Both states are equal | `EnginePropertiesTest.p10ReplayIsDeterministic` |
| F01-AC12 | Random states | An accepted move | The spawn lands on a cell that was empty after the slide, with `exp ∈ {1, 2}` | `EnginePropertiesTest.p13SpawnLandsOnEmptyCell` |
| F01-AC13 | Seed `0x2048` | `Game.newGame` for N = 3, 4, 8 | Cells, spawns and `rngState` equal ARCHITECTURE.md §3.6 | `EngineReplayGoldenTest.newGameVectors` |
| F01-AC14 | `newGame(BoardSize.of(4), 0x2048)` | The 8 moves of ARCHITECTURE.md §3.6 | Every row of the table matches, including move 3's `transitions` | `EngineReplayGoldenTest.replayVectors` |
| F01-AC15 | 100,000 spawns from fixed seeds | Counted | The 4-rate lies in [0.095, 0.105]; every empty cell is chosen within ±10 % of uniform | `SpawnDistributionTest.fourRateAndUniformCells` |
| F01-AC16 | Seeds `0` and `0x2048` | `SplitMix64.nextLong()` ×4, `nextInt(10)` ×8, `nextInt(16)` ×8 | The D-038 vectors; `nextInt(0)` throws | `SplitMix64GoldenTest.vectorsAndBoundedDraws` |
| F01-AC17 | A 4×4 `PLAYING` state whose accepted move fills the last empty cell with no equal pair left | `Game.move` | Status `OVER` and `becameOver = true`; the same board before the spawn was not `OVER` | `StatusMachineTest.overEvaluatedAfterSpawn` |
| F01-AC18 | Status `WON`, then status `OVER` | `Game.move` in each direction | `Blocked(status)`; the state is unchanged | `StatusMachineTest.blockedInWonAndOver` |
| F01-AC19 | The engine sources | The guard scans `core/engine/src/main/kotlin` | No `java.time`, `System.currentTimeMillis`, `System.nanoTime`, `Math.random`, `kotlin.random`, `java.util.Random`, `SecureRandom`, `import android.` or `import kotlinx.` | `EngineImportsGuardTest.noClockRngOrAndroidInEngine` |
| F01-AC20 | Consecutive seeds on 8×8 | 100,000 accepted random moves | Total time ≤ 2 s on the CI JVM | `EnginePerformanceTest.hundredThousandMovesUnderTwoSeconds` |
| F01-AC21 | A `GameViewModel` on `FIX_MID_4` | `onMove` with a direction that is a no-op | `nudge` emits; `board`, `header` and `uiState` are unchanged; no game or records write is submitted | `GameViewModelTest.invalidMoveEmitsNudgeOnly` |
| F01-AC22 | A `TestAppContainer` with `FixedSeedSource` returning `0x2048`, then `0x2049` | Two fresh games start | Their `seed` fields are `0x2048` and `0x2049` and their boards equal `newGame(size, seed)` | `GameViewModelTest.freshGameSeedComesFromSeedSource` |

### F02 — Board sizes and size switching

**User story.** As a player, I want any square board from 3×3 to 8×8, each with its own saved game, best score and statistics, so that I can change difficulty without losing progress anywhere.

**Behavior.**
1. Sizes N ∈ {3, 4, 5, 6, 7, 8}; the start size is `settings.lastBoardSize` (default 4). The win target is 256 (exponent 8) on 3×3 and 2048 (exponent 11) on every other size (D-036).
2. The game header shows a size chip labeled `"$n×$n"` (U+00D7, system font, not a string resource; DESIGN_SYSTEM.md §3.7). Tapping it opens the board-size sheet (`GameModalSheet`, DESIGN_SYSTEM.md §6.7; its `ExperimentalMaterial3Api` opt-in is isolated in `ui/components/GameModalSheet.kt`, D-181) with six rows, 3×3 to 8×8. Each row shows:
   - a `MiniBoardGlyph(n)`;
   - the headline `"$n×$n"`;
   - "Goal 256" or "Goal 2048", then "Best {formatCount}" or "No best score yet";
   - an "In progress" pill when that size's saved game is in progress, and a check mark on the current size.
3. Selecting **another** size needs no confirmation, because nothing is lost (D-007). The ViewModel (ARCHITECTURE.md §4.9 "Size switch"):
   1. folds the play clock and saves the current game;
   2. writes `lastBoardSize`;
   3. sets `busy`, during which input is ignored;
   4. loads that size's saved game **whatever its status** (`PLAYING`, `WON`, `KEEP_PLAYING`, `OVER`), or starts a fresh game when none exists;
   5. clears `busy`. A loaded `WON` or `OVER` game shows its card in the `Restored` form: interactive at once, no entry animation (F03, F04).
4. Selecting the **current** size dismisses the sheet. Nothing is written.
5. A size switch never finalizes a game (D-007, D-044). Each size keeps exactly one saved game (`game-N.json`). Loading the new size may only complete an interrupted New game of that size, whose old game was already counted or is counted from its recovery entry (F06 item 5, D-044); it never counts the game being left or a game being played.
6. The board changes with the `SizeSwitched` transition of MOTION_SPEC.md §3.10 (`AnimatedContent(targetState = boardSize)`, D-182) under Full and Reduced, and swaps instantly under Off. The new board's tiles are `Static`.
7. Board geometry follows D-075 for every N. Tile labels follow D-077: digit-count sizing; a compact `K/M/G/T/P/E` label when the full decimal would be below 12 dp and `exp ≥ 10`; values below 1024 always print in full. The cell's accessibility text always carries the full decimal (F17).
8. The header Best chip shows the current size's best (F08).

**Edge cases.**
- On a 360×800 dp phone the stacked 8×8 board is 328 dp, so a tile is ≈ 35.4 dp. Then 1024 renders "1K" (the 4-digit label would be 0.32·t ≈ 11.3 dp < 12 dp), 16384 renders "16K", and 512 renders "512". On 7×7 the tile is ≈ 40 dp, so "1024" prints in full and 16384 renders "16K". DESIGN_SYSTEM.md §4.3 lists the geometry for every size.
- The full decimal of an exponent ≥ 63 does not fit in a `Long`; the engine's `tileValue` saturates there. `TileLabels.fullDecimal(exp)` and `ShareText` (`:app` only) compute it as `java.math.BigInteger.ONE.shiftLeft(exp).toString()`, so accessibility labels and the share text always carry the exact value. Exponents 63–65 are reachable only on 8×8 and are theoretical, but F02-AC9 pins 65. Example: 2^65 → compact "32E", label "36893488147419103232".
- A corrupt `game-N.json` for the selected size starts a fresh game of that size; no other file is touched (D-045).
- The "In progress" pills need every size's state: `GameRepository.peek(size)` for the six sizes reads the pending-write cache first, then disk. A missing file means "not in progress". So does a saved game whose `gameId` is already finalized (`records.isFinalized(game)`): that is an interrupted New game, which `restore` replaces with a fresh game when the size is selected (D-044).
- `lastBoardSize` outside 3..8 (a hand-edited file or a backup from a future version) is coerced to 4 on read.
- A switch during an in-flight slide snaps the slide, then crossfades (D-052).
- The sheet is modal, so swipes and move keys are ignored while it is open (D-054).

**Settings involved.** `lastBoardSize` (read at start, written on switch); `palette` (mini-board glyph colors).

**Persistence touched.** On a switch: the old size's `game-N.json` (folded `elapsedMs`), `settings.json` (`lastBoardSize`), and the new size's `game-N.json` when a fresh game is created.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F02-AC1 | `BoardSize.of(n)` | n = 3..8, then n = 2 and 9 | `targetExp` is 8 for 3 and 11 for 4..8; 2 and 9 throw `IllegalArgumentException` | `StatusMachineTest.targetsPerSize` |
| F02-AC2 | A 3×3 `PLAYING` state one merge away from 256 | The merging move | Status `WON`, `becameWon = true` | `StatusMachineTest.threeByThreeWinsAt256` |
| F02-AC3 | Saved games: 4×4 in progress (current) and 6×6 `OVER` | `onSizeSelected(6)` | The 6×6 state loads unchanged with `overlay = GameOver(…, Restored)`; no dialog; records unchanged; `lastBoardSize == 6`; `game-4.json` holds the 4×4 state with folded `elapsedMs` | `GameViewModelTest.sizeSwitchLoadsSavedGameWithoutConfirmation` |
| F02-AC4 | No `game-5.json` | `onSizeSelected(5)` | A fresh 5×5 game: `moveCount == 0`, two tiles, `seed` from `SeedSource`, `bestAtStart` = records' 5×5 best; `game-5.json` written | `GameViewModelTest.sizeSwitchStartsFreshGameWhenNoneSaved` |
| F02-AC5 | Current size 4 | `onSizeSelected(4)` | No write is submitted and every flow is unchanged | `GameViewModelTest.selectingCurrentSizeIsNoOp` |
| F02-AC6 | A size switch whose load is suspended (`busy == true`) | `onMove(LEFT)` | The move is ignored | `GameViewModelTest.inputIgnoredWhileSizeSwitchLoads` |
| F02-AC7 | `FIX_RECORDS`, current size 4, a saved 3×3 game in progress | The size sheet opens | Six rows 3×3…8×8; the 4×4 row reads "Goal 2048 · Best 24,680" with the check; the 3×3 row has the "In progress" pill; 5×5–8×8 read "No best score yet" | `GameScreenTest.sizeSheetRowsShowGoalBestAndInProgress` |
| F02-AC8 | Board 328 dp, Classic, N = 3..8 | `BoardGeometry` computes | Gap, tile and radius equal DESIGN_SYSTEM.md §4.3; 4×4 gives gap 10 dp and tile 69.5 dp | `BoardGeometryTest.classicGeometryAllSizes` |
| F02-AC9 | Tile side 35.4 dp | `TileLabels` for exponents 9, 10, 14, 65 | Labels "512", "1K", "16K", "32E"; full decimals "512", "1024", "16384", "36893488147419103232" | `TileLabelsTest.compactLabelsBelowTwelveDp` |
| F02-AC10 | Tile sides 40, 69.5 and 160 dp | `TileLabels` for 1–7 characters | Font size follows the D-077 table (0.50 / 0.40 / 0.32 / 0.27 / 0.23·t, then 0.84·t / (chars·0.603)) and `wdth` 100 / 87.5 / 75 | `TileLabelsTest.sizeByCharacterCount` |
| F02-AC11 | `settings.json` with `lastBoardSize = 9` | The repository loads | `state.value.lastBoardSize == 4` | `SettingsRepositoryTest.lastBoardSizeOutOfRangeCoercedToFour` |
| F02-AC12 | Garbage in `game-6.json`; valid `game-4.json`, `records.json`, `settings.json` | 6×6 is selected | A fresh 6×6 game; the three other files are byte-identical to before | `PersistenceCorruptionTest.corruptGameFileAffectsOnlyItsSize` |
| F02-AC13 | Fixtures `FIX_MID_3`, `FIX_DENSE_8` | Goldens 5–7 are captured | They match | `GameScreenshotTest.otherSizesGoldens` |
| F02-AC14 | `FIX_MID_4` + `FIX_MID_3` + `FIX_RECORDS` | Golden 42 (board-size sheet) is captured | It matches | `GameScreenshotTest.boardSizeSheetGolden` |

### F03 — Win and Keep going

**User story.** As a player, I want a clear win moment when I reach the target, and the choice to keep playing toward bigger tiles without the game ever locking me out.

**Behavior.**
1. An accepted move from `PLAYING` that creates a tile whose exponent **equals** the size's target sets `WON` and `becameWon = true` (D-036). If the same move also leaves no moves, the status is still `WON`; `OVER` is evaluated after Keep going.
2. The win card (DESIGN_SYSTEM.md §6.3) shows "You win!", a hero badge with the target value (2048, or 256 on 3×3), the buttons **Keep going** (filled), **New game** (tonal) and **Undo** (text), and an icon-only **Share** button (`ic_share`) at its top end (D-200, F10). The scrim is clipped to the board's rounded shape (fixes B19).
   - **Fit** (D-200): the card's height budget is the board size − 16 dp. It uses its Regular layout (DESIGN_SYSTEM.md §6.3) when that fits, otherwise its Compact layout (§6.3a); if even Compact does not fit, the card scrolls inside itself. At `fontScale` 1.0 it never extends past the board on any golden qualifier.
3. Entry under Full (D-064, D-183), with T0 = merge reveal = origin + 80 ms:
   - pop with the merged target tile's own impulse tier (12 for 2048, 8 for 256 on 3×3), subject to the neighbour cap of MOTION_SPEC.md §3.3 (D-183); glow ring at T0 + 60 ms; confetti at T0 + 120 ms (96 particles compact, 144 expanded, cap 160, done within 2.4 s);
   - scrim 0 → 0.60 at T0 + 600 ms; card scale 0.92 → 1 with `overlaySpatial`;
   - badge morph Circle → 12-point cookie; rotation 0 → 60° over 5,000 ms, then it stops;
   - buttons interactive at **T0 + 800 ms** (origin + 880 ms, test frame 880).

   Under Reduced: a 150 ms fade, a static badge, interactive at 150 ms (test frame 160). Under Off: instant and interactive at once (D-008). The card never blocks input longer than its entry.

   Button rendering (D-200): the card's buttons are composed with `enabled = true` from their first frame, so they never show disabled colors. Interactivity follows `OverlayController.interactive`; until it flips, clicks are ignored, and the buttons carry `disabled()` semantics and cannot take focus.
4. A **restored** `WON` state (relaunch, recreation, switching back to that size) shows the card with no entry animation; its buttons work immediately.
5. **Keep going** calls `Game.keepGoing`: status `KEEP_PLAYING`, `keepGoingAtMove = moveCount`; the card closes; the game is saved. If the board has no moves, the status becomes `OVER` and the game-over card replaces the win card (F04). The tap itself is silent (D-065: buttons are silent); the entry into `OVER` fires the game-over `Reject` (D-185, F14).
6. In `KEEP_PLAYING`, tiles grow without bound (super tiles per D-072; exponents ≥ 17 share the last color) and `WON` is never re-entered, so 4096 and beyond show no card.
7. **New game** on the card starts immediately with no dialog (D-007). Finalization counts the game as won because its final board holds the target (F08).
8. **Undo** on the card returns to the state before the winning move (status `PLAYING`) and closes the card. Repeating the same move reproduces the same spawn and the win card again (D-036, D-039).
9. While the card is visible, board swipes and direction keys are ignored (D-054). Chrome controls outside the board (controls-bar Undo and New game, size chip, More menu) stay enabled and act exactly like the card's buttons, and so do the keys: U, Ctrl+Z and Backspace undo; N and R start a new game with no confirmation; Enter and Space activate the focused card button (D-199; DESIGN_SYSTEM.md §6.1). Back is not intercepted: the system goes home, and the card is restored next time (F21).
10. Haptic: `Confirm`, then `Confirm` (F14). TalkBack: `paneTitle` "You win!", one assertive announcement, focus on Keep going (F17).

**Edge cases.**
- One move that creates two target tiles (two 1024 pairs on different rows) reports one win and shows one card.
- On 3×3, reaching 512 or 1024 after Keep going shows nothing.
- A win on the move that also sets a new best shows both visuals; the haptic is the win's (priority, F14).
- A win while the first-launch hint is visible: the accepted move also dismisses the hint (F22).
- Process death during the entry choreography: the card comes back restored and interactive.
- An abandoned `KEEP_PLAYING` game is counted both as a win and as abandoned (F08, §3).

**Settings involved.** `motion` (entry and interactivity timing); `haptics`; `palette` and `highContrast` (badge colors).

**Persistence touched.** `game-N.json` after the winning move (`status = WON`) and after Keep going (`KEEP_PLAYING`, `keepGoingAtMove`); `records.json` at finalization.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F03-AC1 | A 4×4 `PLAYING` state with row 0 = `1024 1024 _ _` | `Game.move(LEFT)` | `becameWon`, status `WON`, row 0 starts with exponent 11 | `StatusMachineTest.winOnExactTarget` |
| F03-AC2 | Two 1024 pairs on different rows | The move merging both | `becameWon` is true once; status `WON` | `StatusMachineTest.twoTargetTilesInOneMoveWinOnce` |
| F03-AC3 | A move that creates 2048 and leaves no empty cell and no equal pair | Applied | Status `WON`, not `OVER`; `keepGoing` then gives `OVER` | `StatusMachineTest.winWithNoMovesStaysWonUntilKeepGoing` |
| F03-AC4 | A `WON` state with moves available | `Game.keepGoing` | `KEEP_PLAYING` and `keepGoingAtMove == moveCount`; calling it outside `WON` throws `IllegalArgumentException` | `StatusMachineTest.keepGoingRecordsMove` |
| F03-AC5 | `KEEP_PLAYING` | Merges create another 2048, then a 4096 | `becameWon` stays false, status stays `KEEP_PLAYING`, exponent 12 is on the board | `StatusMachineTest.keepPlayingIsUnbounded` |
| F03-AC6 | Random undo-free move sequences | Accepted moves | `becameWon` is true at most once, exactly on the first merge that produces `targetExp`, and never in `KEEP_PLAYING` | `EnginePropertiesTest.p9WinReportedOnce` |
| F03-AC7 | Won at move 100, kept going at 100, two more moves | Undo three times, then the winning direction again | After the third undo `keepGoingAtMove == -1` and status `PLAYING`; the replayed move gives `becameWon = true` again | `UndoTest.undoBelowKeepGoingResetsWin` |
| F03-AC8 | A `GameViewModel` one merge from 2048 | `onMove` with the winning direction | `overlay = Win(11, Animated(moveId))`; the submitted game document has `status = WON` | `GameViewModelTest.winningMoveShowsWinCard` |
| F03-AC9 | `WON` with moves available | `onKeepGoing` | `overlay = None`, status `KEEP_PLAYING`, exactly one game save submitted | `GameViewModelTest.keepGoingClosesCard` |
| F03-AC10 | `WON` with no moves | `onKeepGoing` | `overlay = GameOver(summary, …)` and the saved status is `OVER` | `GameViewModelTest.keepGoingWithoutMovesShowsGameOver` |
| F03-AC11 | Full motion; the winning move's origin is t = 0 | t = 864 ms, then t = 880 ms | Keep going, New game and Undo are disabled at 864 and enabled at 880 | `GameScreenTest.winCardInteractiveAtT0Plus800Full` |
| F03-AC12 | Reduced motion | t = 144 ms, then 160 ms | Disabled, then enabled; no confetti canvas is composed | `GameScreenTest.winCardInteractiveAt150Reduced` |
| F03-AC13 | Motion Off | F0, the first frame that composes the card | The buttons are enabled on that frame | `GameScreenTest.winCardInteractiveImmediatelyOff` |
| F03-AC14 | `game-4.json` = `FIX_WON_4`, Full motion | The app launches | The win card shows with buttons enabled on the first frame and no confetti or morph running | `GameScreenTest.restoredWinCardInteractiveImmediately` |
| F03-AC15 | The win card after move m | Tap Undo, then repeat the winning direction | After Undo: `moveCount == m − 1`, status `PLAYING`, `overlay = None`. After the repeat: the same spawn index and value as the first time, and the win card again | `GameViewModelTest.undoFromWinThenRepeatWinsAgain` |
| F03-AC16 | The win card is visible | A swipe on the board, an arrow key and a tap on the scrim | Board, status and files are unchanged; the card stays | `GameScreenTest.winCardBlocksBoardInput` |
| F03-AC17 | `FIX_WON_4` light and dark | Goldens 13–14 | They match | `GameScreenshotTest.winCardGoldens` |
| F03-AC18 | `FIX_WON_4`; windows `w360dp-h640dp`, `w360dp-h800dp`, `w915dp-h412dp-land`; `fontScale` 1.0; Motion Off | Win card shown | The card's bounds lie inside the board's bounds inset by 8 dp and the card does not scroll (Compact layout where Regular exceeds board − 16 dp, D-200) | `GameScreenTest.winCardFitsBoard` |
| F03-AC19 | Full; the `FIX_WIN_PRE_4` winning move | t = 864 | Keep going carries `Disabled` semantics and is not focusable, yet its centre pixel (`captureToImage`) is closer to `colorScheme.primary` than to M3's disabled container color (`onSurface` at 12 % over the card surface); at t = 880 it is enabled (D-200) | `GameScreenTest.cardButtonsNeverRenderDisabledColors` |

New game from the card is F06-AC5; Back is F21-AC7; card semantics are F17-AC12.

### F04 — Game over

**User story.** As a player, when no move is left I want a summary of my game, and I want to undo the losing move, start over or share, without ever losing the final board.

**Behavior.**
1. Status becomes `OVER` after the spawn of an accepted move when no cell is empty and no orthogonal pair is equal, unless the move set `WON` (D-037, D-036).
2. The game-over card (DESIGN_SYSTEM.md §6.4) shows:
   - "Game over";
   - Score and Best as game numerals (Best = this size's `bestScore` after this game);
   - Best tile as a `MiniTile` of the board's maximum exponent;
   - the summary "{moves} · {time} · {undos}": `moves_count` plurals with `formatCount`, `formatElapsed(elapsedMs)`, and `undos_count` plurals or "No undo" when `undoCount == 0`. These are prose numbers ("1,024 moves"); the share text uses game numerals ("1024 moves") on purpose (D-103, F10);
   - the buttons **New game** (filled), **Undo** (tonal) and **Share** (outlined, F10).

   The card fits the board like the win card: Regular layout (DESIGN_SYSTEM.md §6.4) within board − 16 dp, otherwise Compact (§6.4a), otherwise it scrolls inside itself (D-200). Its buttons render like the win card's: enabled colors from the first frame, gated by `OverlayController.interactive` (D-200).
3. Entry under Full (D-064, D-183), with T0 = origin + 300 ms: desaturate 1.0 → 0.2 over `tween(600)`; deflate ripple to 0.94, staggered `step = min(30, 180 / mMax)` ms per Manhattan step, where mMax is the largest Manhattan distance on the board (30 ms on 4×4); scrim 0 → 0.78 at T0 + 450 ms; card at T0 + 500 ms; **interactive at T0 + 700 ms** (origin + 1,000 ms, test frame 1,008). In the `OVER` end state tiles sit at scale 0.94 and saturation 0.2, also in static renders and goldens (D-183). Reduced: a 150 ms fade, interactive at 150 ms (test frame 160). Off: instant and interactive at once. Restored: interactive immediately.
4. The card cannot be dismissed. A scrim tap does nothing; only New game, Undo and Share act. Back is not intercepted (D-054).
5. `OVER` is persisted with the final board and the full history, so relaunching shows the same card (fixes B9, G11).
6. **Undo** restores the state before the losing move, with status `PLAYING` or `KEEP_PLAYING`, closes the card, and play continues (fixes B4).
7. **New game** starts immediately and finalizes the game: `gamesPlayed + 1`, not abandoned (F08).
8. The play clock stops when the status leaves `PLAYING`/`KEEP_PLAYING` (D-043).
9. Haptic: `Reject` at T0 (origin + 300 ms, test frame 304) under Full and at the origin otherwise, once per entry into `OVER`, **in addition to** that move's merge haptic (D-065 lists game over as a separate event; D-185; F14). TalkBack: `paneTitle` "Game over", one assertive announcement, focus on New game (F17).

**Edge cases.**
- Game over after Keep going (`KEEP_PLAYING` → `OVER`) shows the same card; undo returns to `KEEP_PLAYING`.
- Game over on 3×3 without reaching 256 is an ordinary loss.
- A win that leaves no moves shows the win card first; the game-over card appears only after Keep going (F03). That Keep going goes `WON` → `OVER` at once and fires the game-over `Reject` at Ft, the card-exit origin (MOTION_SPEC.md §3.12, AC-EXIT-2; D-185).
- Undoing a losing move at move 33 crosses a snapshot boundary; it still replays at most 31 moves (D-039).
- Best tile can exceed the target (a `KEEP_PLAYING` game).

**Settings involved.** `motion`, `haptics`, `palette`, `highContrast`.

**Persistence touched.** `game-N.json` with `status = OVER` after the losing move; `records.json` at finalization.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F04-AC1 | A `GameViewModel` whose next accepted move ends the game | `onMove` | `overlay = GameOver(summary, Animated(moveId))`; `summary` holds size, score, best (≥ score), `bestTileExp` = board max, `moves = moveCount`, `elapsedMs`, `undos = undoCount` and the N² cells | `GameViewModelTest.gameOverSummaryMatchesState` |
| F04-AC2 | `game-4.json` = `FIX_OVER_4`, records best 24680, locale en | The card renders | "Game over", "3012", "24680", best tile 128, "302 moves · 6:41 · No undo" | `GameScreenTest.gameOverCardContent` |
| F04-AC3 | `FIX_OVER_4` with `undoCount` 1, then 2 | The card renders | The summary ends with "1 undo", then "2 undos" | `GameScreenTest.gameOverCardUndoPhrasing` |
| F04-AC4 | Full motion; the losing move's origin is t = 0 | t = 992 ms, then 1,008 ms | New game, Undo and Share are disabled, then enabled | `GameScreenTest.gameOverCardInteractiveAtT0Plus700Full` |
| F04-AC5 | Reduced motion | t = 144 ms, then 160 ms | Disabled, then enabled | `GameScreenTest.gameOverCardInteractiveAt150Reduced` |
| F04-AC6 | Motion Off | F0, the first frame that composes the card | Enabled on that frame | `GameScreenTest.gameOverCardInteractiveImmediatelyOff` |
| F04-AC7 | The game-over card is visible | A scrim tap and a swipe | The card stays and nothing changes | `GameScreenTest.gameOverCardNotDismissible` |
| F04-AC8 | An `OVER` state (from `PLAYING`, and from `KEEP_PLAYING`) | `Game.undo` | Status `PLAYING` (respectively `KEEP_PLAYING`), `canMove == true`, `moveCount` − 1 | `UndoTest.undoFromOverIsPlayable` |
| F04-AC9 | The game-over card | `onUndo`, then an accepted swipe | `overlay = None`; the swipe applies | `GameViewModelTest.undoFromGameOverResumesPlay` |
| F04-AC10 | Container A ends a 4×4 game (`OVER`), awaits idle, is cancelled | Container B and a new `GameViewModel` load | Board and history equal A's; `overlay = GameOver(…, Restored)` | `ProcessDeathRestoreTest.overStateRestoresWithCard` |
| F04-AC11 | An `OVER` game with score 3012 | `onNewGame` | Records: `gamesPlayed + 1`, `gamesAbandoned` unchanged, `totalScore + 3012`; a fresh game starts with no dialog | `GameViewModelTest.newGameAfterOverCountsPlayedNotAbandoned` |
| F04-AC12 | A running `PlayClock` | The status becomes `OVER` at clock time 10,000 ms; the clock advances to 20,000 ms | `elapsed` stays at the value reached at 10,000 ms | `PlayClockTest.stopsWhenGameEnds` |
| F04-AC13 | `FIX_OVER_4` light and dark | Goldens 15–16 | They match | `GameScreenshotTest.gameOverCardGoldens` |
| F04-AC14 | `FIX_OVER_4`; windows `w360dp-h640dp`, `w360dp-h800dp`, `w915dp-h412dp-land`; `fontScale` 1.0; Motion Off | Game-over card shown | The card's bounds lie inside the board's bounds inset by 8 dp and the card does not scroll (Compact layout where Regular exceeds board − 16 dp, D-200) | `GameScreenTest.gameOverCardFitsBoard` |

### F05 — Unlimited undo

**User story.** As a player, I want to take back any number of moves, including the one that ended my game, without dialogs and without being able to re-roll the next tile, so that undo fixes slips but stays honest.

**Behavior.**
1. Entry points: the controls-bar Undo button (enabled iff `moveCount > 0`), the U, Ctrl+Z and Backspace keys (F16), the TalkBack "Undo" custom action and the direction-pad Undo (F17), and the Undo buttons of both cards.
2. Each undo restores the state before the last accepted move: board, score, `rngState`, status and `moveCount − 1`. It reconstructs from the nearest snapshot and replays at most 31 moves (D-039, ARCHITECTURE.md §3.3).
3. Depth is unlimited, down to move 0. There is no confirmation and no redo (O8).
4. The RNG state is restored, so the same move after an undo spawns the same tile (anti-reroll). How to play explains this (F23).
5. `undoCount` grows by one per undo, is never decremented, persists, appears on the game-over card and in the share text, and is added to `totalUndos` at finalization (F08).
6. Undo works in `PLAYING`, `KEEP_PLAYING`, `WON` and `OVER`. From `WON` or `OVER` it closes the card.
7. Undoing to a move earlier than `keepGoingAtMove` resets it to −1 (F03).
8. Presentation: a `fastEffects` board crossfade under Full and Reduced, instant under Off (MOTION_SPEC.md §3.9). All tiles are `Static`; a cell whose exponent is unchanged keeps its tile id and does not recompose; no "+N", no spawn animation and no haptic (D-064, D-065, D-182).
9. Records: no best is ever lowered. `bestScoreNoUndo` stops updating for this game after its first undo (F08).
10. Snackbars (D-186; DESIGN_SYSTEM.md §5.4, §7.6):
    - `undo_hint_no_undo_best` "Undo used. This game no longer counts for Best without undo." shows when a game's `undoCount` goes from 0 to 1, but only while `RecordsMath.aggregate(records).totalUndos == 0`, that is, until the first finalized game that used undo. There is no new persisted key.
    - `nothing_to_undo` "Nothing to undo" shows when U, Ctrl+Z or Backspace is pressed at `moveCount == 0`, at most once per 2 s. The button is disabled at move 0, so touch cannot trigger it.
    - Placement: a snackbar never covers the controls bar. STACKED: in the gap between the board and the controls when that gap is at least its measured height + 16 dp, otherwise over the board, bottom-centred, width board − 16 dp, 8 dp above the board's bottom edge. THREE_COLUMN: at the bottom of the actions rail. SIDE_PANEL: at the bottom of the panel, aligned with the board's bottom edge. SCROLL: over the board, as STACKED's second case (D-188).
    - At most one snackbar is visible; each is `SnackbarDuration.Short` with no action; any accepted move or undo dismisses it.
11. Undo never rewinds the play clock: `elapsedMs` only grows within a game.
12. Every undo is persisted at once (D-043; fixes B2).

**Edge cases.**
- Undo directly after Keep going (no move since): `t = moveCount − 1 < keepGoingAtMove`, so `keepGoingAtMove` resets and the status is `PLAYING`; the win card does not come back until the target is reached again.
- Undo from `OVER` in a game that had kept going returns `KEEP_PLAYING`.
- The first undo after a relaunch undoes the last move (fixes B3).
- Undoing the move that triggered the new-best moment keeps `newBestAnnounced = true` and the NEW BEST label (F09).
- A 10,000-move game can be undone step by step to move 0; the result equals `newGame(size, seed)` apart from `undoCount`.
- Undo during an in-flight slide snaps the slide, then crossfades (D-052).
- At move 0 the board is exactly `newGame(size, seed)`'s board (ARCHITECTURE.md §3.5 I7).

**Settings involved.** `motion` (crossfade).

**Persistence touched.** `game-N.json` on every undo. `records.json` is never lowered by an undo.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F05-AC1 | A new game (`moveCount == 0`) | `Game.undo` | `null` | `UndoTest.undoAtMoveZeroReturnsNull` |
| F05-AC2 | Random states and accepted moves `m = move(s, d)` | `Game.undo(m.state)` | `undo(m.state)!!.copy(undoCount = s.undoCount) == s`, and `undo` never returns `WON` or `OVER` | `EnginePropertiesTest.p11UndoInvertsAcceptedMove` |
| F05-AC3 | Games with exactly 31, 32, 33 and 64 accepted moves | `Game.undo` | Each result equals `Game.replay` of the log minus its last move, with `undoCount + 1`, and the expected snapshot count | `UndoTest.undoAcrossSnapshotBoundaries` |
| F05-AC4 | A state `s` and an accepted direction `d` | `move(s, d)`, `undo`, `move(…, d)` | Both `Moved` outcomes have the same `spawn` and equal states apart from `undoCount` | `UndoTest.sameMoveAfterUndoSpawnsSameTile` |
| F05-AC5 | States in `PLAYING`, `KEEP_PLAYING` and `WON` | `Game.undo` | Each returns the pre-move state; from `WON` the status is `PLAYING` | `UndoTest.undoFromPlayingKeepPlayingAndWon` |
| F05-AC6 | The 10,000-move long game (ARCHITECTURE.md §9.4) | 10,000 undos | The board, score and `rngState` equal `newGame(BoardSize.of(8), 0x2048)`; `undoCount == 10000`; one snapshot left | `UndoTest.undoAllTheWayToMoveZero` |
| F05-AC7 | Moves m1, m2, m3 from S0 (states S1, S2, S3) | Undo, move m4 (state T), undo | The state equals S2 (B1 returned S1) | `UndoTest.undoAfterUndoThenMoveReturnsToPriorState` |
| F05-AC8 | A `GameViewModel` with a card visible | `onUndo` | `overlay = None`; `undoCount + 1`; exactly one game save; `board.event == BoardEvent.Undo` | `GameViewModelTest.undoPersistsIncrementsAndClosesCard` |
| F05-AC9 | A `BoardPresenter` mid-game | `reset` after an undo | Every `TileUi.kind == Static`; no `Spawned`, `Merged` or `Ghost` | `BoardPresenterTest.undoEpochHasOnlyStaticTiles` |
| F05-AC10 | `FIX_FRESH_4` (`moveCount == 0`) | The Game screen renders | The Undo button is disabled; after one accepted move it is enabled | `GameScreenTest.undoButtonDisabledAtMoveZero` |
| F05-AC11 | Records with `totalUndos == 0`; a game with `undoCount == 0` | Undo twice; relaunch; undo again; then New game (finalizing it) and undo in the new game | `undo_hint_no_undo_best` shows exactly once, after the first undo, and not in the new game (D-186) | `GameScreenTest.undoUsedSnackbarUntilFirstFinalizedUndoGame` |
| F05-AC12 | `moveCount == 0` | U at t = 0, 1,000 and 2,100 ms | "Nothing to undo" shows at 0 and 2,100 ms only | `GameScreenTest.nothingToUndoSnackbarRateLimited` |
| F05-AC13 | A game in progress | Tap Undo | The state changes at once; no dialog node exists | `GameScreenTest.undoHasNoConfirmation` |
| F05-AC14 | A `PlayClock` with elapsed 60,000 ms | An undo, then 5,000 ms of play | Elapsed is 65,000 ms | `PlayClockTest.undoDoesNotRewindElapsed` |
| F05-AC15 | Container A: 10 moves, then 1 undo, then idle and cancel | Container B loads | `moveCount == 9`, `undoCount == 1`, board equals A's post-undo board (fixes B2) | `ProcessDeathRestoreTest.undoIsPersisted` |
| F05-AC16 | Container A: 10 moves, idle, cancel | Container B: `onUndo` once | `moveCount == 9` and the board equals the state after move 9 (fixes B3) | `ProcessDeathRestoreTest.firstUndoAfterRestartUndoesLastMove` |
| F05-AC17 | `FIX_MID_4` at `w360dp-h640dp`, `fontScale` 1.0 and 1.3 | Undo shows `undo_hint_no_undo_best` | The snackbar never intersects the controls bar; it lies in the board-to-controls gap or over the board inset by 8 dp; the next accepted move dismisses it | `GameScreenTest.snackbarNeverCoversControls` |

Undo keys are F16-AC2; the TalkBack Undo action is F17-AC4; records under undo are F08-AC6.

### F06 — New game and confirmation

**User story.** As a player, I want New game to be one tap away, but never to throw away a game in progress by accident.

**Behavior.**
1. Entry points: the controls-bar New game button, the N and R keys (F16), the TalkBack "New game" action (F17), and the New game buttons of both cards (D-007).
2. If the game is **in progress**, an M3 `AlertDialog` asks "Start a new game?" with "New game" and "Cancel" (DESIGN_SYSTEM.md §6.5). Cancel, Back and a tap outside dismiss it with **no state change** and no write (fixes B5).
3. Otherwise (`moveCount == 0`, `WON` or `OVER`) the new game starts immediately; on a card, the card is the decision point.
4. Starting a new game (ARCHITECTURE.md §4.9 `replaceWithFresh`; D-044):
   1. finalizes the old game iff `status == OVER || score > 0` (F08). This is a no-op when the old game's `gameId` is already finalized;
   2. creates a fresh game of the same size with a new seed from `SeedSource` and `gameId = max(old gameId, lastFinalizedGameId[size]) + 1`;
   3. sets `bestAtStart = records.best(size)`, read **after** finalization;
   4. resets `elapsedMs`, `undoCount` and `newBestAnnounced`;
   5. sets the fresh game's `previousFinalization` to the old game's finalization input when the old game is `OVER` or has `score > 0`, whether step 1 counted it now or earlier; otherwise leaves it absent;
   6. closes any card; submits `records.json` (if finalized) and then `game-N.json`. The two writes may land in either order.
5. **Interrupted New game.** If the process dies, or one of the two writes fails, the next load of that size recovers (D-044):
   - when the game file still holds the old game but the records already count it (`gameId ≤ lastFinalizedGameId`), the New game is completed: a fresh game replaces the old one and nothing is counted again;
   - when the game file holds the fresh game but the records lack its `previousFinalization`, that entry is counted then;
   - when neither write landed, the old game comes back as it was and is counted at the next New game.
6. Animation (D-064; MOTION_SPEC.md §3.8): under Full, the old tiles shrink to 0.6 and fade, staggered in reading order (`step = min(15, 240 / n)` ms for n outgoing tiles, so 15 ms on a typical board), then the two start tiles appear 60 ms apart. Reduced crossfades; Off swaps instantly. The new board's tiles are `Static` in `BoardUi` with new ids; the outgoing tiles are drawn from the previous `BoardUi` by `OutgoingTilesLayer`, so the reveal is board-level choreography (D-182). A New game completed by recovery at load (item 5) shows the fresh board `Restored`, with no animation (D-047).
7. The dialog survives recreation and process death (`rememberSaveable`).
8. No haptic (D-065).

**Edge cases.**
- New game on a fresh board (`moveCount == 0`) starts immediately with a new seed; nothing is finalized because the score is 0.
- `KEEP_PLAYING` after a win is in progress, so the dialog is shown.
- During `WON`, New game from the controls bar also starts immediately (`WON` is not "in progress").
- N or R while the dialog is open does nothing (the dialog holds focus; D-054).
- N or R while a card is shown starts the new game at once, like the card's New game (D-199, F16-AC11).
- New game does not dismiss the first-launch hint; only an accepted move or "Got it" does (D-009).
- New game from a game with score 0 that is not `OVER` counts nothing, and the fresh game's `previousFinalization` is absent; its `gameId` still increases.

**Settings involved.** `motion` (animation).

**Persistence touched.** `records.json` (finalization, including `lastFinalizedGameId`), `game-N.json` (fresh game with `gameId` and `previousFinalization`).

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F06-AC1 | `FIX_MID_4` (in progress) | Tap New game | The dialog "Start a new game?" with "New game" and "Cancel" shows; the state is unchanged | `GameScreenTest.newGameAsksWhenInProgress` |
| F06-AC2 | The dialog is open | Tap Cancel | The dialog closes; state, status and all files are unchanged | `GameScreenTest.cancelNewGameChangesNothing` |
| F06-AC3 | The dialog is open | Back, and separately a tap outside the dialog | The dialog closes with no change | `GameScreenTest.dismissNewGameDialogByBackOrOutsideChangesNothing` |
| F06-AC4 | `FIX_FRESH_4` (`moveCount == 0`) | Tap New game | No dialog; a new board with a different seed appears | `GameScreenTest.newGameImmediateOnFreshBoard` |
| F06-AC5 | `FIX_WON_4`, then `FIX_OVER_4` | Tap the card's New game, and separately the controls-bar New game | No dialog; a fresh game starts | `GameScreenTest.newGameFromCardsSkipsConfirmation` |
| F06-AC6 | A `PLAYING` game one merge from 2048 | Open the dialog, Cancel, then make the winning merge | The win card shows (B5 suppressed it) | `GameScreenTest.cancelThenWinStillShowsWinCard` |
| F06-AC7 | A game in progress with score 4,000 | `onNewGame` (after confirmation) | `RecordsMath.finalize` ran once; the fresh game has a new seed, `moveCount 0`, `undoCount 0`, `elapsedMs 0`, `newBestAnnounced false`; one records save and one game save | `GameViewModelTest.newGameFinalizesThenStartsFresh` |
| F06-AC8 | A `PLAYING` game with moves but score 0 | `onNewGame` | Records are unchanged (not finalized) | `GameViewModelTest.newGameWithZeroScoreIsNotCounted` |
| F06-AC9 | An `OVER` game whose score 9,000 exceeds the previous best 8,000 | `onNewGame` | The fresh game's `bestAtStart == 9000` | `GameViewModelTest.newGameBestAtStartReadAfterFinalization` |
| F06-AC10 | The dialog is open | `ActivityScenario.recreate()` | The dialog is still open | `GameScreenTest.newGameDialogSurvivesRecreation` |
| F06-AC11 | A `BoardPresenter` | `reset` for a new game | Every `TileUi.kind == Static`; the event is `BoardEvent.NewGame` | `BoardPresenterTest.newGameEpochHasOnlyStaticTiles` |
| F06-AC12 | `FIX_MID_4` | Golden 43 (new-game dialog) | It matches | `GameScreenshotTest.newGameDialogGolden` |
| F06-AC13 | Empty records and a saved `FIX_OVER_4` with `gameId 7`; separately, a saved `PLAYING` 4×4 game with moves, score 0 and `gameId 7` | `onNewGame` | First case: `lastFinalizedGameId["4"] == 7`. The fresh game has `gameId 8` and `previousFinalization == old.toFinalization()`. Calling `RecordsRepository.finalize(old)` again leaves `records.state` equal and submits no records write. Second case: records unchanged; the fresh game has `gameId 8` and no `previousFinalization` (D-044) | `GameViewModelTest.newGameAssignsIdentityAndRecoveryEntry` |
| F06-AC14 | Container A: empty records, `game-4.json` = `FIX_OVER_4` with `gameId 7`, the 4×4 game store wrapped in `FaultyDataStore` with `failWrites = true` | `onNewGame`; `awaitIdle()` on every writer; A's scope cancelled (the records write landed, the game write failed, the process died) | Container B on the same files has `records.sizes["4"]` with `gamesPlayed 1`, `totalScore 3012`, `totalMoves 302`, `totalTimeMs 401000`, and `lastFinalizedGameId["4"] == 7`. B's game is a fresh 4×4 game with `gameId 8` and `previousFinalization.gameId == 7`, and `game-4.json` holds it after `awaitIdle()`. After `onNewGame` in B (score 0) and a container C: still `gamesPlayed 1` (D-044 R2) | `FinalizationRecoveryTest.overGameCountedOnceWhenGameWriteIsLost` |
| F06-AC15 | As F06-AC14, with `FIX_MID_4` (`PLAYING`, score 10220) and `gameId 7` | Same | `gamesPlayed 1`, `gamesAbandoned 1`, `totalScore 10220`, `totalMoves 412`, `totalUndos 2`. B's game is the fresh game, not `FIX_MID_4`. Container C: still `gamesPlayed 1` | `FinalizationRecoveryTest.abandonedGameCountedOnceWhenGameWriteIsLost` |
| F06-AC16 | As F06-AC14, but the **records** store is wrapped in `FaultyDataStore` (`failWrites = true`) instead | Same (the game write landed, the records write failed) | `records.json` is still empty before B loads. After B's load: `gamesPlayed 1`, `totalScore 3012`, `lastFinalizedGameId["4"] == 7`, counted from the recovery entry. B's game is A's fresh game (`gameId 8`), unchanged. Container C: still `gamesPlayed 1` (D-044 R1) | `FinalizationRecoveryTest.overGameCountedOnceWhenRecordsWriteIsLost` |
| F06-AC17 | As F06-AC16, with `FIX_MID_4` and `gameId 7` | Same | After B's load: `gamesPlayed 1`, `gamesAbandoned 1`, `totalScore 10220`. Container C: still `gamesPlayed 1` | `FinalizationRecoveryTest.abandonedGameCountedOnceWhenRecordsWriteIsLost` |
| F06-AC18 | Container A: empty records and `game-4.json` = `FIX_OVER_4`, and separately `FIX_MID_4`, each with `gameId 7`. The bytes of `records.json` and `game-4.json` are saved before `onNewGame` and after `onNewGame` + `awaitIdle()` | Each of the 4 combinations (records before/after × game before/after) is copied into a fresh folder. Container B loads, runs `onNewGame` and `awaitIdle()`; container C loads | In all 8 cases, C's `records.sizes["4"]` has `gamesPlayed 1` with the old game's score, moves, time and undos exactly once, and `lastFinalizedGameId["4"] ≥ 7` | `FinalizationRecoveryTest.everyDiskOutcomeCountsExactlyOnce` |
| F06-AC19 | Container A: the records store wrapped in `FaultyDataStore` (`failWrites = true`), `lastBoardSize 5`, no game files. The fresh 5×5 game (`gameId 1`) plays accepted moves until `score > 0` | `onNewGame`; `onSizeSelected(4)`; `awaitIdle()`; A's scope cancelled | Container B starts on 4×4. After `ready` and `advanceUntilIdle()`, without selecting 5×5: `records.sizes["5"]` has `gamesPlayed 1` and `gamesAbandoned 1`, and `lastFinalizedGameId["5"] == 1`. `game-5.json` is byte-identical (other sizes get R1 only) | `FinalizationRecoveryTest.otherSizesRecoveredAfterLoad` |

The N/R keys are F16-AC3; the TalkBack action is F17-AC5.

### F07 — Auto-save, resume, process death and splash

**User story.** As a player, I never want to lose a move. If the app is closed, killed, rotated, resized or restored onto a new phone, the game is exactly where I left it, and nothing replays.

**Behavior.**
1. Persistence uses typed JSON DataStore files under `files/datastore/` (D-040): `settings.json`, `records.json` and `game-3.json` … `game-8.json`. Exactly one DataStore instance per file exists, owned by `AppContainer` (D-022).
2. A full game document is enqueued after every accepted move, undo, keep-going, new game and size switch, and on `ON_PAUSE`, which also folds the elapsed time (D-043). Records are written live when a best increases (F08). Writes are conflated (the latest value wins) and a write in progress is never cancelled (D-024). A process kill loses at most the one change whose write is in flight; README says so (D-043). New game writes two files. It is recovered as a unit on the next load, so its old game is counted exactly once (F06, D-044).
3. Cold start: `installSplashScreen()` keeps the splash up until settings, records and the start size's game have loaded, or for at most **1,000 ms** (D-112). The first composed board equals the persisted board; every tile is `Static`; no "+N" floats; a persisted `WON` or `OVER` shows its card in the `Restored` form.
4. If the 1,000 ms cap releases the splash first, the Game screen shows the board frame with empty cells (board description `a11y_board_loading`), "–" in the score chips, and disabled Undo and New game (DESIGN_SYSTEM.md §6.1 "Loading"). The tiles then appear `Static` when the data arrives.
5. Recreation (rotation, resize, fold, locale or `uiMode` change) and pause/resume never reload the game and never replay animations (D-047, D-095). An animated board change, "+N" or card entry is shown once: the composition that starts presenting it reports it, and the ViewModel replaces it with its static form for the same state (ARCHITECTURE.md §4.11). A later composition (recreation, the return from a sub-screen, an arrangement switch of F18) therefore draws the final frame at once, and a recreation in the middle of an animation snaps to its end without firing any haptic again.
6. Transient UI survives recreation and process death: open sheets, dialogs and the menu use `rememberSaveable`; sub-screens are on the Nav3 back stack (ARCHITECTURE.md §4.11). The What's new sheet is the exception: it is stored as seen when it is shown (F24).
7. A corrupt file falls back to that file's defaults only, and the app never crashes. A game document that decodes but violates an engine invariant counts as corrupt. Nothing is logged or shown (D-045).
8. Compatibility (D-041): every document has `schemaVersion = 1`; the decoder ignores unknown keys and coerces unknown enum names to the field default; a missing key decodes to the field default; the committed v1 fixtures are never edited. A default is not always a valid value: `gameId` has none, so a game document without it decodes to `gameId = 0` and counts as corrupt (D-044, F07-AC23).
9. Budgets (D-046): an 8×8 game with 10,000 moves encodes to ≤ 256 KiB with encode + decode ≤ 50 ms; `records.json` ≤ 64 KiB; `settings.json` ≤ 4 KiB.
10. `elapsedMs` accumulates `MonotonicClock` time only while the Game destination is resumed **and** the status is `PLAYING` or `KEEP_PLAYING` **and** `moveCount ≥ 1` (D-043, ARCHITECTURE.md §4.9 `PlayClock`).

**Edge cases.**
- A kill during a write leaves the previous complete file, because DataStore writes a temp file and renames it.
- A write failure (disk full) is swallowed; the in-memory state stays authoritative and the next change rewrites the whole document (ARCHITECTURE.md §9.3). No UI appears.
- A file written by a newer app version decodes in this version with defaults for unknown values (downgrade-safe).
- Time spent in the background, on a sub-screen, on a card, or before the first move is not counted.
- `game-N.json` for a size never played does not exist; loading it yields "no saved game".

**Settings involved.** `lastBoardSize` (which game loads first).

**Persistence touched.** All files.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F07-AC1 | Container A plays 40 moves and 3 undos on each size 3..8 (`FixedSeedSource`), awaits idle on every writer, then its scope is cancelled | Container B is built on the same files | For every size: board, score, `rngState`, move log, snapshots, `undoCount`, `elapsedMs`, status, `gameId` and `previousFinalization` equal A's | `ProcessDeathRestoreTest.everySizeRestoresExactly` |
| F07-AC2 | A `GameViewModel` with a counting `GameRepository` | An accepted move, an undo, a keep-going, a new game, a switch to a size with no saved game, and `onPaused`, in turn | Exactly one game save per action, except the size switch: one for the old size and one for the fresh game of the new size | `GameViewModelTest.everyStateChangeSubmitsOneSave` |
| F07-AC3 | A resumed game in progress, `FakeClock` advanced 30,000 ms | `onPaused` | The saved document has `elapsedMs == 30000` | `GameViewModelTest.pauseFoldsElapsedAndSaves` |
| F07-AC4 | A `PlayClock` | The clock advances before the first move, while paused, in `WON`, in `OVER`, and in `PLAYING`/`KEEP_PLAYING` after the first move while resumed | Only the last case accumulates time | `PlayClockTest.countsOnlyWhileResumedAndPlaying` |
| F07-AC5 | A `ConflatedWriter` whose write suspends on a gate | Submit v1 (it starts), then v2…v100, then open the gate | v1 completes (not cancelled), then exactly one more write with v100; 2 writes in total | `ConflatedWriterTest.conflatesWithoutCancellingInFlightWrite` |
| F07-AC6 | A write lambda that throws `IOException` once | Submit v1, then v2 | No exception escapes; `failures == 1`; v2 is written | `ConflatedWriterTest.ioFailureIsSwallowedAndNextWriteSucceeds` |
| F07-AC7 | A game after one accepted move whose board and header were reported as presented (`onBoardPresented` and `onHeaderPresented` with its `moveId`: the test makes the calls in M2, when `App.kt` has no `BoardView` yet; from M3 the composed `BoardView` and `GameHeader` make them) | `ActivityScenario.recreate()` | The same `GameViewModel` instance (no reload); every `TileUi.kind == Static`, event `Restored`, `moveId` and `epoch` unchanged; `header.lastDelta == 0`; the move log is unchanged | `RecreateNoReplayTest.recreateRendersStaticBoard` |
| F07-AC8 | A game in progress | The activity goes to `CREATED`, then `RESUMED` | No reload: the board is unchanged, all tiles `Static`, no "+N" node | `RecreateNoReplayTest.pauseResumeDoesNotReplay` |
| F07-AC9 | The board-size sheet is open | `ActivityScenario.recreate()` | The sheet is still open | `RecreateNoReplayTest.openSheetSurvivesRecreation` |
| F07-AC10 | `game-4.json` = `FIX_MID_4` (score 10220) | A `GameViewModel` loads | The first `header` emission after `ready` has `lastDelta == 0` (fixes B7) | `GameViewModelTest.loadEmitsNoScoreDelta` |
| F07-AC11 | The `game-4.json` fixture | The app launches | The first `BoardUi` composed after `ready` equals the fixture's board and contains no `Spawned` tile | `SplashGateTest.firstBoardEqualsPersistedFixture` |
| F07-AC12 | `SplashGate(timeoutMs = 1000)` with a fake uptime | `keep(ready = false)` at 999 and 1,000 ms; `keep(ready = true)` at 10 ms | `true`, `false`, `false` | `SplashGateTest.gateReleasesAtCapOrWhenReady` |
| F07-AC13 | A game store whose first read is delayed 1,500 ms | The app launches | Until 1,500 ms: empty-cell frame, "–" chips, Undo and New game disabled; afterwards the fixture board with only `Static` tiles | `SplashGateTest.lateLoadRendersStaticTiles` |
| F07-AC14 | The committed `fixtures/v1/` files | Decoded with the current serializers | They decode, and their key fields equal the literals written in the test (`game-8-long.json` matches ARCHITECTURE.md §9.4) | `PersistenceFixturesTest.v1FixturesDecode` |
| F07-AC15 | Documents with an unknown key `"futureField"`, `palette = "SEPIA"` and `motion = "SUPER"`; the `settings.json` fixture without `keepScreenOn`; the `game-4.json` fixture without `newBestAnnounced` and `elapsedMs` (fields whose defaults are valid values, QUALITY.md §1.5) | Decoded | They decode, none as corruption; the unknown key is ignored; `palette == CLASSIC`, `motion == FULL`; `keepScreenOn == false` with every other settings value as in the fixture; `newBestAnnounced == false` and `elapsedMs == 0` with `gameId == 7`, the cells, history and `previousFinalization` as in the fixture | `PersistenceFixturesTest.newerDocumentsDecodeWithDefaults` |
| F07-AC16 | Garbage bytes in `records.json`, then separately in `settings.json` | The app starts | The store's first value is `Records()` (respectively `Settings()`), before any D-044 recovery: a surviving game file's recovery entry is counted afterwards by design; every other file is byte-identical | `PersistenceCorruptionTest.recordsAndSettingsFallBackIndependently` |
| F07-AC17 | Valid JSON game documents with: bad Base64; wrong snapshot count; `keepGoingAtMove > moveCount`; snapshot status ordinal 9 | Loaded | Each counts as corrupt: a fresh game of that size; `records.json` untouched | `PersistenceCorruptionTest.invariantViolationIsCorruption` |
| F07-AC18 | The 10,000-move 8×8 game | Encode + decode, median of 5 runs after 3 warm-ups | ≤ 256 KiB and ≤ 50 ms | `PersistenceBudgetTest.longGameWithinSizeAndTimeBudget` |
| F07-AC19 | `records.json` with all six sizes populated; default `settings.json` | Encoded | ≤ 64 KiB and ≤ 4 KiB | `PersistenceBudgetTest.recordsAndSettingsWithinBudget` |
| F07-AC20 | Random logs and snapshots | Encoded to bytes and decoded | `MoveLog` and `Snapshot` round-trip exactly | `EnginePropertiesTest.p12HistoryBytesRoundTrip` |
| F07-AC21 | Byte arrays with a wrong length, a status ordinal of 9, an exponent of 128, and non-zero unused high bits | `Snapshot.fromBytes` / `MoveLog.fromPackedBytes` | Each throws `IllegalArgumentException` | `HistoryCodecTest.rejectsMalformedHistoryBytes` |
| F07-AC22 | `LongGameGenerator` | Generates the long game | Win at move 1,678, score 208236, `rngState = 0x26E3A2DE9847F93C`, max exponent 12, `KEEP_PLAYING`, 313 snapshots, 2,500-byte log | `LongGameGeneratorTest.matchesExpectedValues` |
| F07-AC23 | Valid game documents built from the `game-4.json` fixture with: `gameId = 0`; no `gameId` key (it decodes to 0: `gameId` has no valid default); `previousFinalization.gameId` equal to `gameId`; `previousFinalization.score = -1`; `previousFinalization.maxExp = 128` | Loaded | Each counts as corrupt: a fresh game of that size; `records.json` untouched (D-044, D-045) | `PersistenceCorruptionTest.invalidFinalizationIdentityIsCorruption` |
| F07-AC24 | A `GameViewModel` one merge from 2048 (as F03-AC8) after its winning move, with a merge and a score delta; `b = board.value`, `m = b.moveId` | `onBoardPresented(m)`, `onHeaderPresented(m)`, `onOverlayPresented(m)`; then each again; then `onBoardPresented(m - 1)` | `board.value == BoardPresenter.settled(b)` (same `moveId`, `epoch`, cells and non-`Ghost` tile ids; every tile `Static`; event `Restored`); `header.lastDelta == 0` and every other header field unchanged; `overlay == Win(…, Restored)` with the same summary; game state, move log, `elapsedMs` and records unchanged; the counting `GameRepository` saw no save; the repeated and stale calls change nothing (D-047) | `GameViewModelTest.presentationReportsSettleWithoutStateChange` |
| F07-AC25 | Seeded random replays of moves, undos, new games and size switches through `BoardPresenter` (D-145) | `settled(b)` for every emitted `b` | Same `moveId`, `epoch`, `size` and `cells`; its tiles are `b`'s non-`Ghost` tiles with the same ids and cells, in row-major order, all `Static` with `from == to`; `scoreDelta == 0`; event `Restored`; `settled(settled(b)) == settled(b)`; the presenter's next emission is the same as without the call (D-047, D-182) | `BoardPresenterTest.settledKeepsIdentityAndIsStatic` |
| F07-AC26 | `FIX_FILM` under `MotionHarness` (Full); LEFT committed and settled (t = 448) | `harness.recreate()` | On the new composition's first frame: every probe tile `Static` at its engine cell with scale 1 and alpha 1; no "+N" float; `displayedScore == vm.header.value.score`; nothing running; `vm.board.value.moveId` and the move log unchanged. Through the next 1,000 ms no haptic is recorded beyond the move's one `SegmentTick` | `RecreateNoReplayTest.recreateAfterMoveRendersFinalFrame` |
| F07-AC27 | `FIX_FILM` under `MotionHarness` (Full); LEFT committed | `harness.recreate()` at t = 48: mid-slide, before the reveal, the spawn, the "+N" and the `SegmentTick` due at 80 | The first frame after recreation equals the static render of the moved state (the `presenter.reemitStatic()` bitmap of AC-CONS-3): every tile at its engine cell, merged tile and spawn fully visible; no float on any later frame; `displayedScore` is the new score; nothing running. No haptic is recorded through t = 1,000: the pending `SegmentTick` is dropped, not replayed | `RecreateNoReplayTest.recreateMidMoveSnapsToFinalFrame` |
| F07-AC28 | `FIX_WIN_PRE_4` under `MotionHarness` (Full); LEFT committed | `harness.recreate()` at t = 200 (after `Confirm` #1 at 128, before #2 at 272 and the card at 680); in a second run at t = 1,200 | On the first frame after recreation: `overlay.kind == WIN`, `interactive == true`, card alpha 1 and scale 1, scrim 0.60, badge morph 1, `ring == null`, `confettiAlive == 0`, every tile `Static`; `vm.overlay.value` has entry `Restored`. Through the next 3,000 ms the recorded haptics stay exactly those recorded before recreation: one `Confirm` in the first run, two in the second | `RecreateNoReplayTest.recreateDuringAndAfterWinShowsRestoredCard` |
| F07-AC29 | `FIX_OVER_PRE_4` under `MotionHarness` (Full); LEFT committed | `harness.recreate()` at t = 500 (mid-ripple, before the card at 800); in a second run at t = 1,200 | On the first frame after recreation: every tile at scale 0.94, `boardSaturation == 0.2`, scrim 0.78, `overlay.kind == OVER`, `interactive == true`; nothing running. Through the next 3,000 ms the recorded haptics stay exactly the one `Reject` at t = 304 | `RecreateNoReplayTest.recreateDuringAndAfterGameOverShowsRestoredCard` |
| F07-AC30 | `FIX_INVALID_4` under `MotionHarness` (Full); LEFT is invalid and its nudge has settled (t = 400) | `harness.recreate()` | `boardNudgeDp == (0, 0)` on every frame through 400 ms after recreation; the recorded haptics stay exactly the one `Reject` | `RecreateNoReplayTest.recreateAfterInvalidMoveDoesNotNudge` |

### F08 — Statistics and records

**User story.** As a player, I want honest local statistics for each board size: best scores with and without undo, best tile, games, wins, time. They must never drop on their own, and I must be able to reset them on purpose.

**Behavior.**
1. Records live in `records.json` as `sizes["3"…"8"]` of `SizeRecord` (D-042). A missing key reads as an all-zero record. §3 defines every field.
2. **Live progress** on every accepted move (`RecordsMath.progress`, ARCHITECTURE.md §4.9):
   - `bestScore = max(bestScore, score)`;
   - `bestScoreNoUndo = max(…, score)` only while the game's `undoCount == 0`;
   - `bestTileExp = max(…, board max)`.

   A write is submitted only when one of them increases (D-043).
3. **Finalization** happens when New game starts on that size, and only if the old game is `OVER` or has `score > 0` (D-044; rules in §3.1, readings in D-198).
   - **Identity** (D-044): finalization is keyed by the game's `gameId`. `records.json` keeps `lastFinalizedGameId[size]`, and finalizing a game whose `gameId` is at or below it changes nothing. A fresh game carries the replaced game's finalization input as `previousFinalization`, which is counted at the next load if the records write was lost (F06 item 5).
   - **Display-time fold** (D-198): `StatsViewModel` combines `records` with `GameRepository.peek(n)` for n = 3..8 and, for each size, displays `RecordsMath.displayed(records, game)` without persisting it. That counts the saved game's `previousFinalization` if not yet counted, then the saved game if it is `OVER` and not yet finalized. Persisted finalization still happens only at New game and in its recovery, so nothing is counted twice; undoing out of `OVER` removes the game from the display.
4. Bests never decrease, except through Reset statistics (D-044).
5. **Statistics screen** (`StatsKey`; DESIGN_SYSTEM.md §6.9):
   - top bar: "Statistics", back navigation, and the Reset action;
   - tabs: "All sizes", then "3×3" … "8×8", starting on the current board size;
   - a hero card with the best score and a `MiniTile` of the best tile;
   - rows: Games played, Wins "count (rate)", Best score without undo, Average score, Total moves, Time played, Undos used;
   - an empty state when every field of the tab's record is 0.
6. "All sizes" aggregates sums and maxima (§3.2).
7. **Reset statistics** takes two sequential dialogs ("Reset statistics?", then "Reset everything?"). Confirming both writes `RecordsMath.reset(records)`: `sizes` empty, with `lastFinalizedGameId` kept so that no recovery entry or stale game file can re-count a game after the reset. It leaves every game file untouched (including the current game's `bestAtStart` and `previousFinalization`) and shows the snackbar "Statistics reset". Cancel or Back at either step changes nothing (D-044).
8. The header Best chip shows `max(records[size].bestScore, current score)` and updates on the same move (D-043).
9. Stored but not displayed in v1.0: `gamesAbandoned`, `tileReach`, `currentWinStreak`, `longestWinStreak` (roadmap §6.2).

**Edge cases.**
- A game that ended `OVER`: persisted finalization happens at New game; Statistics already displays an `OVER` game through the display-time fold, and undo out of `OVER` removes it (D-198). Its bests are live from the moves themselves (D-044 rejects finalizing at game over because an undo would double-count).
- A size switch never finalizes the games it switches between; its load only completes an interrupted New game (F06 item 5).
- An abandoned game with score 0 is not counted.
- A `KEEP_PLAYING` game abandoned through New game counts as both won and abandoned (D-198).
- A win counts by the **final** board: if the winning tile was undone before New game, the game is not a win (D-198).
- After a reset, the current game's `bestAtStart` keeps its pre-reset value, so its new-best moment can still fire against the old best; only that one game is affected (D-198). The header Best then shows the current score.
- A corrupt `records.json` yields empty records; game files are untouched (F07). The recovery entries of the surviving game files are counted again at their next load, which restores those games' contributions.
- A kill between New game's records and game writes counts the old game exactly once, whichever write landed (F06 item 5, D-044).

**Settings involved.** `lastBoardSize` (initial tab).

**Persistence touched.** `records.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F08-AC1 | Empty records | `finalize` of a 4×4 `OVER` game: score 3012, 302 moves, `elapsedMs` 401,000, 2 undos, max exponent 7 | `gamesPlayed 1`, `gamesWon 0`, `gamesAbandoned 0`, `totalScore 3012`, `totalMoves 302`, `totalTimeMs 401000`, `totalUndos 2`, `tileReach == [0,1,1,1,1,1,1,1]`, `currentWinStreak 0` | `RecordsMathTest.finalizeOverGame` |
| F08-AC2 | Empty records | `finalize` of a `PLAYING` game with score 500 | `gamesPlayed 1`, `gamesAbandoned 1` | `RecordsMathTest.finalizeAbandonedGame` |
| F08-AC3 | Empty 4×4 records | Finalize: win, win, loss, win, win, win; then a `KEEP_PLAYING` game holding 2048; then a game whose 2048 was undone (max exponent 10) | `gamesWon 6`; `currentWinStreak 0` after the last game, `longestWinStreak 4`; the `KEEP_PLAYING` game counted as won **and** abandoned | `RecordsMathTest.winsAndStreaks` |
| F08-AC4 | Random sequences of `progress` and `finalize` | Applied | No best ever decreases | `RecordsMathTest.bestsNeverDecrease` |
| F08-AC5 | `bestScore 1000`, `bestTileExp 9` | `progress` with score 1004 and max exponent 10, then with score 900 | `bestScore 1004`, `bestTileExp 10`; unchanged after 900 | `RecordsMathTest.progressUpdatesBestsLive` |
| F08-AC6 | A game with `undoCount 0` at score 2000, later `undoCount 1` at score 3000 | `progress` twice | `bestScore 3000`, `bestScoreNoUndo 2000` | `RecordsMathTest.progressFreezesNoUndoBestAfterUndo` |
| F08-AC7 | Records for sizes 3 and 4, plus an entry under the unknown key `"daily:4"` | `aggregate`, then `finalize` of a 4×4 game | Counts and totals summed over "3" and "4" only, bests maximal, `tileReach` summed per index, streaks not aggregated; the `"daily:4"` entry is ignored by `aggregate` and preserved unchanged by `finalize` | `RecordsMathTest.aggregateAllSizes` |
| F08-AC8 | 4×4 best 1000, a game at score 996 | The move that reaches 1004 | `header.best == 1004` on that move; a records save with `bestScore 1004` was submitted | `GameViewModelTest.headerBestTracksLiveScore` |
| F08-AC9 | `FIX_RECORDS`, locale en, tab 4×4 | `StatsViewModel` derives | Games "37", Wins "9 (24%)", Best "24,680", Best without undo "18,212", Average "11,150", Total moves "23,804", Time "11:27:14", Undos "58", best tile exponent 11 | `StatsViewModelTest.fourByFourDerivedValues` |
| F08-AC10 | `FIX_RECORDS`, tab All sizes | Derived | Games "42", Wins "11 (26%)", Best "24,680", Average "9,822", best tile exponent 11 | `StatsViewModelTest.allSizesAggregation` |
| F08-AC11 | `FIX_RECORDS`, tab 5×5 | Derived | The tab is in its empty state | `StatsViewModelTest.emptyRecordIsEmptyState` |
| F08-AC12 | `lastBoardSize = 6` | Statistics opens | Tabs read "All sizes", "3×3" … "8×8"; "6×6" is selected | `StatsScreenTest.tabsStartAtCurrentSize` |
| F08-AC13 | `FIX_RECORDS` | The 5×5 tab is selected | "No games on 5×5 yet" and "Finished and abandoned games appear here." show; no stat rows | `StatsScreenTest.emptyStatePerTab` |
| F08-AC14 | `FIX_RECORDS` and a 4×4 game in progress | Reset → confirm → confirm | `records.json` decodes to `Records(lastFinalizedGameId = <the pre-reset map>)`: `sizes` empty, which is `Records()` for `FIX_RECORDS`; `game-4.json` is byte-identical; the snackbar "Statistics reset" shows | `StatsScreenTest.resetRequiresTwoConfirmations` |
| F08-AC15 | `FIX_RECORDS` | Reset → Cancel; then Reset → confirm → Cancel; then Back at each step | `records.json` is unchanged every time | `StatsScreenTest.resetCancelAtEitherStepChangesNothing` |
| F08-AC16 | A 4×4 game in progress with `bestAtStart 24680` | Statistics are reset | The `GameViewModel`'s current game keeps `bestAtStart 24680`, `newBestAnnounced` and its board | `GameViewModelTest.resetStatisticsLeavesCurrentGameUntouched` |
| F08-AC17 | `FIX_RECORDS` | Golden 19 | It matches | `ScreensScreenshotTest.statisticsGolden` |
| F08-AC18 | Empty 4×4 records; saved 4×4 game `OVER` with score 1,000 | `StatsViewModel` derives; then Undo | Games played shows 1 and the records file is unchanged; after Undo it shows 0 (D-198) | `StatsViewModelTest.overGameCountedBeforeNextNewGame` |
| F08-AC19 | Empty records; a 4×4 `Finalization` of an `OVER` game with `gameId 7` and score 3012 | `finalize` twice; then `finalize` of an `OVER` `Finalization` with `gameId 5` | The first call counts it (`gamesPlayed 1`, `totalScore 3012`) and sets `lastFinalizedGameId["4"] = 7`. The second and third return the same `Records` instance (`===`) (D-044) | `RecordsMathTest.finalizeIsIdempotentPerGameId` |
| F08-AC20 | `FIX_RECORDS` plus `lastFinalizedGameId = {"4": 6, "3": 5}` (the `records.json` fixture's map, QUALITY.md §1.5) | `reset`; then `nextGameId(…, 4×4, null)`, `isFinalized(…, 4×4, 6)` and `isFinalized(…, 4×4, 7)` | `sizes` empty and `lastFinalizedGameId` unchanged; `7`; `true`; `false` (D-044) | `RecordsMathTest.resetKeepsOnlyFinalizationIdentity` |
| F08-AC21 | Case 1: empty 4×4 records with `lastFinalizedGameId["4"] = 7` and a saved `OVER` game with `gameId 7`. Case 2: empty records and a saved fresh 4×4 game (`gameId 8`) whose `previousFinalization` is an `OVER` game with `gameId 7` and score 3012 | `StatsViewModel` derives | Case 1: Games played 0 (the already finalized game is skipped). Case 2: Games played 1 and Average "3,012". `records.json` is unchanged in both cases (D-198) | `StatsViewModelTest.foldCountsEachGameIdOnce` |
| F08-AC22 | Container A: a 4×4 `FIX_OVER_4` with `gameId 7`, then `onNewGame` (the fresh `game-4.json` carries the recovery entry) | `records.resetAll()`; `awaitIdle()`; A's scope cancelled; container B loads | B's `records.sizes` is empty and `lastFinalizedGameId["4"] == 7`: the recovery entry is not counted again after the reset (D-044) | `FinalizationRecoveryTest.resetIsNotUndoneByRecovery` |

### F09 — New-best moment

**User story.** As a player, I want a short celebration the first time in a game that I beat my previous best on this board size, without it repeating on every move.

**Behavior.**
1. `bestAtStart` is the size's `bestScore` when the game is created; it is stored in the game document (D-042).
2. The moment fires on the first accepted move where `score > bestAtStart`, and only if `bestAtStart > 0`. It fires once per game, and `newBestAnnounced` persists (D-044).
3. Visual (D-064): under Full, the BEST chip crossfades to `tertiaryContainer`, its label changes "BEST" → "NEW BEST", and a 10-point star plus 8 sparkles play for 1.5 s (`game/header/NewBestBadge.kt`; the sparkles are drawn by `ContentEffectsCanvas`, D-187). Under Reduced: the label swap and a container crossfade. Under Off: an instant swap. The chip stays "NEW BEST" for the rest of the game, including after a relaunch (DESIGN_SYSTEM.md §6.1, §7.2).
4. Haptic: `GestureThresholdActivate` at origin + 80 ms (Full) or at the origin (Reduced/Off), unless the same move wins (F14, D-185).

**Edge cases.**
- The first game on a size (`bestAtStart == 0`) never shows the moment.
- `score == bestAtStart` does not fire; it must be strictly greater.
- Undoing below `bestAtStart` and passing it again does not fire a second time.
- A move that both wins and sets a new best shows both visuals; the haptic is the win's.
- After a relaunch the label is restored from `newBestAnnounced`, with no animation and no haptic.
- Switching sizes and back keeps both fields, because they are persisted.

**Settings involved.** `motion`, `haptics`.

**Persistence touched.** `game-N.json` (`bestAtStart`, `newBestAnnounced`).

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F09-AC1 | `bestAtStart 1000` | Accepted moves reach scores 996, 1004, 1012 | `header.newBestMoveId` changes only on the 1004 move; documents saved from that move on have `newBestAnnounced = true` | `GameViewModelTest.newBestFiresOnceAboveBestAtStart` |
| F09-AC2 | `bestAtStart 0` | Scores rise to 5000 | `newBestMoveId` stays −1 | `GameViewModelTest.newBestSkippedWhenBestAtStartIsZero` |
| F09-AC3 | `bestAtStart 1000` | A move reaches exactly 1000 | No moment | `GameViewModelTest.newBestRequiresStrictlyGreaterScore` |
| F09-AC4 | The moment fired at 1004 | Undo to 996, then move to 1008 | No second moment | `GameViewModelTest.newBestNotRepeatedAfterUndo` |
| F09-AC5 | `game-4.json` with `newBestAnnounced = true` | The app launches | The chip reads "NEW BEST" on the first frame; no star node; `FakeHapticFeedback` recorded nothing | `GameScreenTest.restoredNewBestShowsLabelWithoutReplay` |
| F09-AC6 | Reduced motion | The new-best move | The label swaps; no star or sparkle nodes are composed | `GameScreenTest.newBestReducedMotionSwapsLabelOnly` |

### F10 — Share result (text)

**User story.** As a player, I want to share a win or a finished game as plain text with an emoji picture of the board, without the app asking for any permission.

**Behavior.**
1. **Share** exists on both cards and not in the menu: on the game-over card as an outlined button (D-008), and on the win card as an icon-only button (`ic_share`, content description `share`) at its top end (D-200). Both send the text below for the current state.
2. The text has a summary line, then N lines of N emoji (row-major), joined with `\n` and with no trailing newline.
   - The summary is the string `share_summary` = "2048 · %1$s · %2$s · best tile %3$s · %4$s · %5$s · %6$s". The leading "2048" is the non-translatable app name and stays literal in every locale (the glossary records this, D-105).
   - Arguments: `%1$s` = `"$n×$n"`; `%2$s` = score; `%3$s` = the best tile's full decimal (`BigInteger.ONE.shiftLeft(exp)`, exact even above exponent 62, F02); `%4$s` = `moves_count` plural formatted with `Long.toString(moveCount)`; `%5$s` = `formatElapsed(elapsedMs)`; `%6$s` = `undos_count` plural with `Long.toString(undoCount)`, or `undos_none` "No undo" when 0.
   - All numbers are game numerals (Latin digits, no grouping; D-103): "1024 moves". The game-over card uses prose numbers ("1,024 moves"). The difference is intentional and covered by F10-AC4 and F04-AC2.
3. Emoji per cell exponent (D-008):

| Exponent | Value | Emoji |
|---|---|---|
| 0 | empty | ⬜ (U+2B1C) |
| 1–3 | 2–8 | 🟫 (U+1F7EB) |
| 4–6 | 16–64 | 🟨 (U+1F7E8) |
| 7–8 | 128–256 | 🟧 (U+1F7E7) |
| 9 | 512 | 🟥 (U+1F7E5) |
| 10 | 1024 | 🟪 (U+1F7EA) |
| 11 | 2048 | 🟦 (U+1F7E6) |
| ≥ 12 | ≥ 4096 | ⬛ (U+2B1B) |

4. The app sends `Intent(ACTION_SEND)` with type `text/plain` and `EXTRA_TEXT`, wrapped in `Intent.createChooser(intent, "Share result")`. No file, no `FileProvider`, no permission (D-002).
5. `share/ShareText.kt` is pure: it takes the summary and pre-resolved templates (`ShareStrings`), so `ShareTextTest` runs on the plain JVM. `GameScreenTest` covers resource resolution.

**Edge cases.**
- The 8×8 text has 8 lines of 8 emoji. Every emoji above is a single code point.
- The 3×3 text uses the 3×3 board; the best tile may be below the target.
- When the chooser closes, Immersive mode is re-applied on window focus (F19).
- If no target exists, the system chooser handles it; the app does not crash.

**Settings involved.** None (locale only).

**Persistence touched.** None (read-only).

Expected English text for `FIX_OVER_4` (score 3012, 302 moves, 6:41, no undo, max exponent 7):

```
2048 · 4×4 · 3012 · best tile 128 · 302 moves · 6:41 · No undo
🟫🟫🟫🟫
🟫🟫🟫🟫
🟨🟨🟨🟧
🟫🟫🟫🟫
```

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F10-AC1 | `FIX_OVER_4` and the English templates | `ShareText.build` | Exactly the five lines above | `ShareTextTest.englishSummaryAndGridForOverFixture` |
| F10-AC2 | One cell with each exponent 0..17 | Built | Each emoji follows the table | `ShareTextTest.emojiBucketsPerExponent` |
| F10-AC3 | `FIX_MID_3` and `FIX_DENSE_8` | Built | 1 + 3 lines of 3 emoji; 1 + 8 lines of 8 emoji | `ShareTextTest.gridShapeMatchesBoardSize` |
| F10-AC4 | `Locale.setDefault(fa)`, score 20316, 1,024 moves, 3,725,000 ms | Built | Every digit in the text is in `0`–`9`; it contains "20316", "1024" and "1:02:05" | `ShareTextTest.latinDigitsUnderPersianLocale` |
| F10-AC5 | `undoCount` 0, 1 and 3 | Built | Ends with "No undo", "1 undo", "3 undos" | `ShareTextTest.undoPhrasing` |
| F10-AC6 | The game-over card for `FIX_OVER_4` | Tap Share | The next started activity is `ACTION_CHOOSER`, titled "Share result", wrapping `ACTION_SEND` / `text/plain` whose `EXTRA_TEXT` equals F10-AC1's text | `GameScreenTest.shareLaunchesChooserWithText` |
| F10-AC7 | The win card, the game menu, and the game-over card | Inspect the semantics tree | A Share node exists on the win card and the game-over card and not in the menu | `GameScreenTest.shareOnBothCardsNotInMenu` |
| F10-AC8 | `FIX_WON_4` | Tap Share on the win card | A chooser with `ACTION_SEND` text equal to `ShareText` for the current state | `GameScreenTest.winCardShareLaunchesChooser` |

### F11 — Settings screen

**User story.** As a player, I want a short, predictable settings list whose changes apply at once and survive restarts.

**Behavior.**
1. `SettingsKey` shows exactly the D-006 rows in this order, grouped by section headers (DESIGN_SYSTEM.md §6.8): Theme, Palette, High-contrast tiles, Dynamic color, Motion, Haptics, Move timing, Swipe sensitivity, Immersive mode, Keep screen on while playing, App language. §2 is the field-level catalog.
2. Switch rows toggle in place. Choice rows open a `ChoiceDialog` with radio options: selecting one applies it, persists it and closes the dialog (there is no OK button); Cancel keeps the old value.
3. Every change persists immediately through `SettingsRepository.update` and its conflated writer; there is no Save button (D-043).
4. Visibility: Dynamic color only on API 31+ (D-074); App language only on API 33+ (D-102).
5. Locks:
   - While the system animator scale is 0, the Motion row is disabled and reads "Off (system animations are off)" (D-006).
   - While system contrast is ≥ 0.5 (API 34+), High-contrast tiles is checked, disabled, and reads "On because the system contrast setting is high" (D-073).
   - A lock never rewrites the stored value; when the system condition ends, the stored setting applies again.
6. App language opens `Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null))`. With no per-app locale set, its summary reads `setting_app_language_system` "System default" (L10N_STORE_LEGAL.md L-AC6). If no activity handles the intent, the snackbar "No app can open this link" shows; nothing crashes.
7. Switch rows fire `ToggleOn`/`ToggleOff` haptics through `HapticsController` (F14, D-185).
8. Every change takes effect live, without a restart: theme and palette recompose; motion applies from the next animation; haptics from the next event; move timing and sensitivity from the next gesture; immersive mode at once; keep-screen-on when Game is next visible.
9. Reset statistics is not here; it lives in Statistics (D-006).

**Edge cases.**
- A `settings.json` restored from a newer phone onto API < 31 keeps `dynamicChrome = true` on disk, but the row is hidden and the flag is ignored (F12).
- A choice dialog open during rotation survives (`rememberSaveable`).
- A corrupt `settings.json` falls back to defaults (F07).

**Settings involved.** All of §2.1 except `lastBoardSize`, `lastSeenVersionCode` and `onboardingDone`, which have no row.

**Persistence touched.** `settings.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F11-AC1 | Default settings at SDK 36 | Settings opens | The 11 rows appear in D-006 order with the §2.1 defaults shown; no "Reset statistics" row exists | `SettingsScreenTest.rowsInCanonicalOrderWithDefaults` |
| F11-AC2 | The Theme dialog is open | Tap "Dark" | The dialog closes; `settings.themeMode == DARK`; the screen is dark on the next frame | `SettingsScreenTest.choiceDialogAppliesOnSelect` |
| F11-AC3 | The Swipe sensitivity dialog is open | Tap Cancel | The value stays `MEDIUM` | `SettingsScreenTest.choiceDialogCancelKeepsValue` |
| F11-AC4 | `@Config(sdk = [24])` | Settings opens | Neither "Dynamic color" nor "App language" exists | `SettingsScreenTest.apiGatedRowsAbsentOnApi24` |
| F11-AC5 | SDK 36 | Tap "App language" | The next started activity has action `ACTION_APP_LOCALE_SETTINGS` and data `package:com.clayboicardi.game2048.debug` | `SettingsScreenTest.appLanguageRowOpensSystemPage` |
| F11-AC6 | Robolectric `checkActivities(true)` with no handler registered | Tap "App language" | The snackbar "No app can open this link" shows; no exception | `SettingsScreenTest.appLanguageFallbackSnackbar` |
| F11-AC7 | `ANIMATOR_DURATION_SCALE = 0` | Settings opens | The Motion row is disabled and reads "Off (system animations are off)"; the stored `motion` is unchanged | `SettingsScreenTest.motionRowLockedWhenSystemAnimationsOff` |
| F11-AC8 | System contrast 0.5 (API 34+, through the injected `ContrastLevel` fake), stored `highContrast = false` | Settings opens | The switch is checked and disabled with the forced summary; the stored value stays `false` | `SettingsScreenTest.highContrastLockedBySystemContrast` |
| F11-AC9 | The Palette dialog is open | `ActivityScenario.recreate()` | The dialog is still open | `SettingsScreenTest.choiceDialogSurvivesRecreation` |
| F11-AC10 | `Settings()` | Encoded | It equals the §2.1 defaults and has exactly the 14 keys of §2.1 | `SettingsRepositoryTest.defaultsMatchCatalog` |
| F11-AC11 | Container A updates every field to a non-default value, awaits idle, is cancelled | Container B loads | Every field reads back as written | `SettingsRepositoryTest.everyFieldRoundTrips` |
| F11-AC12 | Defaults, light and dark | Goldens 17–18 | They match | `ScreensScreenshotTest.settingsGoldens` |

### F12 — Themes, palettes, high contrast and dynamic chrome

**User story.** As a player, I want the familiar 2048 look in light or dark that follows my phone, an optional bold Neon look, readable tiles at every value, and an extra-contrast mode when I need it.

**Behavior.**
1. Theme mode: **System** (default; follows `isSystemInDarkTheme()`), Light, Dark (D-071).
2. Palette (D-070, D-071), provided through `LocalTilePalette` (`TilePalette` interface):
   - **Classic Refined** (default): exactly research/04-design-motion.md §8 A, light and dark, except the Classic dark 2-tile fill `#524A42` (D-207);
   - **Neon Arcade**: exactly research/04 §8 C. Its board is always dark (`#1A1730`, empty cells `#25213F`); the light theme changes only the app background and `surface` (both `#F3F1FA`, D-189) and the chrome scheme (seed `#5B5BD6`).
   - Each palette's `TileSkin` also carries its `titleColor` (Classic light `#776E65`); dynamic chrome never changes it (D-189).
3. Super tiles (exponent ≥ 12) use the D-072 ramp per palette and mode; exponents ≥ 17 share the last color.
4. **High contrast** (D-073) is active when the setting is On, or on API 34+ when `UiModeManager.getContrast() ≥ 0.5` (observed with `addContrastChangeListener`). Then every tile's ink becomes whichever of `#000000` and `#FFFFFF` has the higher contrast against its fill, and every tile gets a 1.5 dp outline in the chrome `outline` color. Every empty cell also gets a 1 dp stroke in `palette.ink(1)`, inset 0.5 dp, with the cell's corner radius (D-206). Fills and chrome are unchanged.
5. **Dynamic color (app chrome)** (D-074), API 31+ only, default Off: the M3 `ColorScheme` comes from `dynamicLightColorScheme`/`dynamicDarkColorScheme`. The board, empty cells, tiles and app background always come from the palette.
6. Effects (D-078): Classic glow on 128–2048; Neon fill-colored glows from 128; everything else flat; no infinite glow.
7. System-bar icon appearance follows the effective theme (D-093).
8. A theme change crossfades colors with `slowEffects` under Full and is instant otherwise (D-064).
9. Everything is wrapped by `GameTheme` (`MaterialExpressiveTheme`, D-026). The window and splash background use the Classic background (D-112).

**Edge cases.**
- Neon in light mode keeps the dark board.
- High contrast with Neon picks black or white ink per tile by contrast.
- `dynamicChrome = true` on API < 31 (restored backup) is ignored.
- System contrast dropping below 0.5 with the setting Off turns high contrast off live.
- A system dark-mode switch recreates the activity; the game restores `Static` (F07).
- Goldens always capture with dynamic color off (D-147).

**Settings involved.** `themeMode`, `palette`, `highContrast`, `dynamicChrome`, `motion` (crossfade).

**Persistence touched.** `settings.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F12-AC1 | Qualifiers `notnight` and `night` × theme modes System, Light, Dark | `GameTheme` composes | Dark iff (System and `night`) or Dark | `GameThemeTest.themeModeFollowsSystemAndOverrides` |
| F12-AC2 | Palette Classic light, Neon light, Neon dark | `LocalTilePalette` is read | Classic `fill(11)` is the research/04 §8 A 2048 fill (`#E6A11A`); Neon `board == #1A1730` in both modes; Neon light `background == #F3F1FA` | `GameThemeTest.paletteSelectionProvidesTilePalette` |
| F12-AC3 | Each palette and mode | `fill(12…17)` and `ink(12…17)` | Equal the D-072 table; `fill(18) == fill(17)` | `PaletteContrastTest.superTileHexesMatchD072` |
| F12-AC4 | Classic light/dark and Neon light/dark, each with high contrast off and on, exponents 1–17 | Contrast and ΔE are computed (unquantized CVD simulation, DESIGN_SYSTEM.md §2.13, D-205) | Ink ≥ 3.0:1 for 1–3 digits, ≥ 4.5:1 for ≥ 4 digits and compact labels; adjacent ΔE_OK×100 ≥ 3.0 normal and ≥ 2.0 protan/deutan/tritan | `PaletteContrastTest.inkContrastAndAdjacentDeltaE` |
| F12-AC5 | `highContrast = true` | The board renders `FIX_RAMP_5` | Each tile's ink is `#000000` or `#FFFFFF` (the higher-contrast one) and each tile has a 1.5 dp outline in `colorScheme.outline`; with high contrast, every empty cell carries a 1 dp `ink(1)` stroke (D-206; visible in golden 12); `colorScheme` equals the non-HC scheme | `GameThemeTest.highContrastSettingInkAndOutline` |
| F12-AC6 | API 34+, system contrast 0.5 through the injected `ContrastLevel` fake, `highContrast = false` | `GameTheme` composes | High contrast is active; at contrast 0.0 it is inactive | `GameThemeTest.systemContrastForcesHighContrast` |
| F12-AC7 | SDK 36, `dynamicChrome = true` | `GameTheme` composes | `colorScheme.primary == dynamicLightColorScheme(context).primary`; `LocalTilePalette` `fill(1…17)`, `board`, `empty`, `background` equal Classic's | `GameThemeTest.dynamicChromeLeavesTilesUnchanged` |
| F12-AC8 | `@Config(sdk = [24])`, `dynamicChrome = true` | `GameTheme` composes | `colorScheme` equals the static Classic scheme | `GameThemeTest.dynamicChromeIgnoredBelowApi31` |
| F12-AC9 | Effective light, then dark | The activity renders | `WindowInsetsControllerCompat.isAppearanceLightStatusBars` is `true`, then `false` | `GameThemeTest.systemBarIconsFollowEffectiveTheme` |
| F12-AC10 | `FIX_RAMP_5` | Goldens 8–12 (Classic light/dark, Neon light/dark, Classic high contrast) | They match | `GameScreenshotTest.paletteRampGoldens` |
| F12-AC11 | `FIX_FRESH_4`, `FIX_MID_4` | Goldens 1–4 (light and dark) | They match | `GameScreenshotTest.freshAndMidGoldens` |

### F13 — Motion setting and reduced motion

**User story.** As a player, I want crisp motion that never delays my next move, and a way to reduce or turn off animation, which happens automatically when my phone's animations are off.

**Behavior.**
1. Setting Motion (`MotionPreference`): **Full** (default) / Reduced / Off. The effective `MotionLevel` is Off whenever `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, observed live through the `AnimatorScaleSource` that `AppContainer` owns; `effectiveMotion()` in `ui/theme/EffectiveMotion.kt` combines both, and `GameTheme` provides the result as `LocalEffectiveMotion` (D-063).
2. **Full** uses the `GameMotion` tokens and timeline (D-060, D-061, D-183), every time measured from the origin:
   - slides on `slide` 0.9/1400 (never an expressive spring);
   - merge reveal at +80 ms with a `popSpatial` 0.6/800 pop. Each merged tile takes its initial velocity from its own value: ≤ 64 → 6 s⁻¹, 128–512 → 8, 1024 → 10, ≥ 2048 → 12, capped so a scaled tile never reaches a resting neighbour (`v0 = min(tier, (2 · gapPx / tilePx) / POP_PEAK_PER_VELOCITY)`, MOTION_SPEC.md §3.3). The move-level `maxMergedExp` still drives the ≥ 512 haptic and the "+N" pill;
   - spawn (scale 0.6 → 1 plus alpha) after a delay set by the move's longest slide, `maxSlideCells`: 90 ms (`SPAWN_DELAY_MS`) for ≤ 3 cells, 100 / 105 / 110 / 115 ms for 4 / 5 / 6 / 7 cells;
   - ghost alpha snaps to 0 at +200 ms (test frame 208); settled at about 400 ms;
   - the "+N" float (a pill in the merged tile's fill and ink when `maxMergedExp ≥ 9`, D-187), score odometer, invalid-move nudge, new-best, win, game-over (deflate `step = min(30, 180 / mMax)` ms per Manhattan step), undo, new-game, size-switch, layout and theme motions of D-064 and MOTION_SPEC.md §3.
3. **Reduced** (research/04 §6.2): instant slides; alpha-only spawn; a 120 ms color crossfade instead of the pop; "+N" fades in place over 400 ms; the nudge becomes a board-outline pulse (0 → 0.6 → 0 over 300 ms); new best is a label swap plus crossfade; win and game over are 150 ms fades with a static badge; the undo crossfade stays (`fastEffects` alpha) but the undo icon does not spin (D-183); no confetti, rings, morph or rotation; the score changes instantly.
4. **Off**: everything is instant, including overlay entries; no "+N" and no nudge.
5. MoveTimeline offsets scale with the animator scale (D-183): a state flip at an offset waits on `MoveTimeline.awaitOffset(moveId, ms)` (or `awaitRealTime(moveId, ms)` for real-time waits), a delay before an animation is part of its spec (`tween(…, delayMillis)`, `snap(delayMillis)` or `delayedSpec(spec, delayMs)` from `game/motion/DelayedSpec.kt`), and no raw `delay()` drives anything visible. `Animatable` animations honor `MotionDurationScale` themselves (ARCHITECTURE.md §5.4, §5.8).
6. Interruption (D-062): a new commit snaps in-flight slides and removes ghosts; spawn, pop, "+N", rings and confetti keep running; at most 3 "+N" floats are alive and the oldest is removed early. An undo, new game or size switch removes every running "+N" float at once; an undo keeps a running new-best moment, while new game and size switch remove it. The header tells these apart by `HeaderUi.event` and `HeaderUi.epoch`, copied from the `BoardUi` emitted with it (ARCHITECTURE.md §4.7, §4.11; MOTION_SPEC.md §4.2). Input never waits for animation (F15).
7. There are no infinite animations. A flash limiter allows at most 3 sheen or glow pulses per rolling 1,000 ms of real time (D-183). The win badge rotation stops after 5 s (D-063).
8. Budget: zero tile recompositions during a slide (D-150). `BoardView` votes `Modifier.preferredFrameRate(FrameRateCategory.High)` while any board animation runs and `Default` when idle (D-183).
9. `GameTheme` passes `gameMotionScheme(level)` to `MaterialExpressiveTheme`: `MotionScheme.expressive()` under Full; `snap()` spatial specs with expressive effects specs under Reduced; `snap()` for every spec under Off. Dialog windows and snackbars keep their platform and Material 3 opacity fades at every level; the platform disables them when the system animator scale is 0 (D-184).

**Edge cases.**
- Animator scale 0.5 halves every duration and delay; overlays become interactive sooner.
- Turning the system scale to 0 mid-game switches to Off on the next frame and locks the Settings row (F11).
- A Motion change during an animation applies from the next event.
- Restored states never animate (F07).

**Settings involved.** `motion`.

**Persistence touched.** `settings.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F13-AC1 | `ANIMATOR_DURATION_SCALE = 0`, setting `FULL` | `LocalEffectiveMotion` is read | `OFF` | `EffectiveMotionTest.systemScaleZeroForcesOff` |
| F13-AC2 | Scale 1.0, settings `FULL`, `REDUCED`, `OFF` | Read | Equals the setting | `EffectiveMotionTest.settingAppliesWhenSystemAnimationsOn` |
| F13-AC3 | Scale 1.0, then set to 0 and the URI notified | The next frame | Effective motion is `OFF` without recreation | `EffectiveMotionTest.observesLiveScaleChange` |
| F13-AC4 | Scale 2.0, Full; the winning move's origin is t = 0 | t = 1,744 ms, then 1,760 ms | Win-card buttons are disabled, then enabled | `EffectiveMotionTest.delaysScaleWithAnimatorScale` |
| F13-AC5 | `MotionPlan` for Full | Built | `slide` spring 0.9/1400 with visibility threshold 0.5 px; pop 0.6/800; per-merged-tile impulses 6/8/10/12 s⁻¹ for merged value ≤ 64, 128–512, 1024, ≥ 2048 with the MOTION_SPEC §3.3 neighbour cap; spawn delay 90/100/105/110/115 ms for `maxSlideCells` ≤ 3/4/5/6/7; merge reveal 80; ghost removal 200 | `MotionPlanTest.fullPlanUsesGameMotionTokens` |
| F13-AC6 | `MotionPlan` for Reduced | Built | `slide == null`; spawn alpha only at +0; merged reveal `tween(120)`; ghost removal at +120; nudge = outline pulse 300 ms | `MotionPlanTest.reducedPlanSubstitutions` |
| F13-AC7 | `MotionPlan` for Off | Built | Every spec `null` and every delay 0 | `MotionPlanTest.offPlanIsInstant` |
| F13-AC8 | `FIX_FILM`, Full | Frames at 0, 48, 80, 160 and 400 ms from the origin, golden names `t000`/`t040`/`t080`/`t160`/`t400` (goldens 37–41, D-204) | They match and were Read by the agent | `MotionFilmstripTest.mergeAndSpawnFilmstrip` (also AC-GOLD-1; KDoc `/** F13-AC8, AC-GOLD-1 */`) |
| F13-AC9 | Full; move 1's origin is t = 0 | Move 2 commits at t = 48 ms | On move 2's F0 every tile of move 1 sits at its move-1 target offset before move 2's slide starts | `GameScreenTest.newMoveSnapsInFlightSlides` |
| F13-AC10 | Full | Five merging moves 48 ms apart | At most 3 "+N" nodes exist at any frame | `GameScreenTest.atMostThreeScoreFloats` |
| F13-AC11 | Full; a winning move committed with `mainClock.autoAdvance = false` | Set `autoAdvance = true` and call `composeRule.waitForIdle()` | It returns (an infinite animation or `withFrameNanos` loop would keep Compose busy and fail with `ComposeNotIdleException`), and `mainClock.currentTime` advanced by ≤ 6,000 ms | `EffectiveMotionTest.noAnimationRunsSixSecondsAfterWin` |
| F13-AC12 | Full; one accepted move on `FIX_MID_4` | Frames sampled every 16 ms up to 400 ms | Pre-existing `TileView`s recompose 0 times after F0 | `TileRecompositionTest.noTileRecompositionDuringSlide` (also AC-PERF-1) |
| F13-AC13 | An invalid swipe under Full, Reduced and Off | t = 48 and 160 ms | Full: board offset ≠ 0 at 48 ms and 0 by 400 ms. Reduced: outline alpha > 0 at 160 ms, no offset. Off: neither | `GameScreenTest.invalidMoveFeedbackPerMotionMode` |
| F13-AC14 | A `GameViewModel` on a 4×4 game with a merge available, and a saved 3×3 game | Load; an accepted merging move; `onBoardPresented` and `onHeaderPresented` with its `moveId`; an undo; a new game; a switch to 3×3, in turn | After each step `header.value.deltaMoveId == board.value.moveId` and `header.value.epoch == board.value.epoch`; `header.value.event` is `Restored`, `Moved`, still `Moved` after the reports (while the board's event becomes `Restored`), then `Undo`, `NewGame` and `SizeSwitched`; the epoch is unchanged by the move and the reports and grows by 1 at each reset (D-062) | `GameViewModelTest.headerCarriesBoardEventAndEpoch` |
| F13-AC15 | `FIX_NEW_BEST_4`, Full; LEFT committed, its "+8" float and the new-best moment running at t = 160 | In separate runs at t = 160: Undo; New game; a switch to a saved 3×3 game; Undo and RIGHT with no frame between them | On that change's F1 no float of LEFT exists. The new-best moment keeps running after Undo and is gone after New game and the size switch. RIGHT's own "+8" float is created at its t = 80 (D-062) | `ScoreMotionTest.resetsRemoveScoreFloats` (also AC-SCORE-5) |

### F14 — Haptics

**User story.** As a player, I want subtle haptics that confirm merges and big moments, never buzz constantly, respect my phone's settings, and need no permission.

**Behavior.**
1. Setting Haptics: **On** (default) / Off.
2. Each accepted move fires **at most one** haptic, by priority (D-065). Full times are measured from the origin and scale with the animator scale (`MoveTimeline.awaitOffset`); Reduced and Off times are real time (`awaitRealTime`), so the two win pulses stay 150 ms apart even at animator scale 0 (D-185):

| Priority | Event | Type | Full timing (from the origin; test frame) | Reduced / Off timing (real time) |
|---|---|---|---|---|
| 1 | Win | `Confirm`, then `Confirm` | 120 and 270 ms (frames 128 and 272) | +0, +150 ms |
| 2 | New best (F09) | `GestureThresholdActivate` | 80 ms (frame 80) | +0 |
| 3 | Max merged value ≥ 512 | `Confirm` | 80 ms (frame 80) | +0 |
| 4 | ≥ 1 merge | `SegmentTick` | 80 ms (frame 80) | +0 |
| 5 | Otherwise | none | — | — |

3. Separate events:
   - game over → `Reject` at 300 ms from the origin (test frame 304) under Full and at the origin otherwise, once per entry into `OVER`, including Keep going on a board with no moves left (`WON` → `OVER`, D-185);
   - invalid move → `Reject`, at most one per 400 ms (uptime-based), and **none below API 30**, where the `Reject` fallback is `LONG_PRESS` (D-185). The game-over `Reject` is kept on every API level;
   - settings switches → `ToggleOn`/`ToggleOff`;
   - Undo, New game, Keep going and other buttons → none.
4. Implementation (D-066, D-185):
   - the pure `game/feedback/HapticsMap` (M2) resolves the D-065 priority in `GameViewModel` and ships the result as `BoardEvent.Moved.haptic` (`MoveHaptic`);
   - `game/feedback/HapticsController` (M3) only schedules `event.haptic` and the separate events, applies interruption and the invalid-move rate limit, and calls `LocalHapticFeedback.current.performHapticFeedback(type)`. Nothing else fires haptics;
   - interruption: when a newer change publishes its origin before a scheduled haptic fires, pending priority 1–2 haptics fire on the newer change's F1, priority 3–4 haptics are dropped, and a pending game-over `Reject` is dropped;
   - never `Vibrator`, `VibrationEffect` or `FLAG_IGNORE_GLOBAL_SETTING`. The system touch-feedback setting is always honored. Below API 30/34, `HapticFeedbackConstantsCompat` falls back to older constants (MOTION_SPEC.md §6.1).
5. With Haptics Off, nothing fires.

**Edge cases.**
- Toggling the Haptics switch itself: turning it On fires `ToggleOn` (the new value is On); turning it Off fires nothing.
- A losing move that merged fires `SegmentTick` at 80 ms **and** `Reject` at 300 ms from the origin (test frame 304): two separate events.
- A winning move whose merge is ≥ 512 fires only the win's two `Confirm`s.
- Keep going on a board with no moves left fires the game-over `Reject` at Ft (the tap itself is silent; MOTION_SPEC AC-EXIT-2, D-185).
- Rapid accepted moves each fire their own haptic, subject to the interruption rule; only invalid moves are rate-limited.

**Settings involved.** `haptics`, `motion` (timing).

**Persistence touched.** `settings.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F14-AC1 | Synthetic `Moved` outcomes combining win, new best, max merge 512 and 1 merge | `HapticsMap.forMove` | `WIN` > `NEW_BEST` > `BIG_MERGE` > `MERGE` > `NONE` | `HapticsMapTest.priorityResolution` |
| F14-AC2 | Full motion and a `FakeHapticFeedback` | One move per `MoveHaptic` value | Types and frames equal the Full column: win at 128 and 272 ms, the others at 80 ms from the origin | `HapticsMapTest.fullMotionTimings` |
| F14-AC3 | Reduced, then Off | The same moves | Types equal; times 0 (win 0 and 150 ms, real time) | `HapticsMapTest.reducedAndOffTimings` |
| F14-AC4 | Full; a losing move with one merge | Committed | `SegmentTick` at 80 ms and `Reject` at 304 ms from the origin; under Off both at 0 | `HapticsMapTest.gameOverRejectIsSeparateAndDelayed` |
| F14-AC5 | Invalid moves at t = 0, 100 and 450 ms | Nudges | `Reject` at 0 and 450 ms only | `HapticsMapTest.invalidMoveRejectRateLimited` |
| F14-AC6 | Haptics Off | Win, new best, big merge, merge, game over and invalid moves | The fake records nothing | `HapticsMapTest.hapticsOffSuppressesEverything` (also AC-HAP-2) |
| F14-AC7 | Haptics On | Undo, New game (immediate and confirmed), Keep going (with moves left), size switch | The fake records nothing | `HapticsMapTest.buttonsAreSilent` |
| F14-AC8 | Settings screen | Turn High contrast and Immersive on and off; turn Haptics off, then on | `ToggleOn`/`ToggleOff` for each; nothing for Haptics → Off; `ToggleOn` for Haptics → On | `SettingsScreenTest.switchesFireToggleHaptics` (also AC-HAP-4; KDoc `/** F14-AC8, AC-HAP-4 */`) |
| F14-AC9 | `@Config(sdk = [24])`, Haptics On | An invalid move; then a losing move | Nothing recorded for the invalid move; `Reject` recorded for the game over (D-185) | `HapticsMapTest.invalidMoveSilentBelowApi30` |

The absence of `VIBRATE` is F27-AC1.

### F15 — Swipe input

**User story.** As a player, I want every swipe to make exactly one move in the direction I meant, immediately, even while tiles are still animating.

**Behavior.**
1. The gesture surface is the whole Game content area: header, board and controls (D-051). `Modifier.swipeToMove` runs in `PointerEventPass.Initial` (ARCHITECTURE.md §6.1). Buttons, the size chip and the menu handle their own taps, but a drag that **starts** on any of them is a swipe and the control does not fire (fixes B11).
2. Displacement is measured from pointer-down, once the system touch slop is exceeded (D-050).
3. Move timing:
   - **As you swipe** (default, `DURING_SWIPE`): commit on the first frame where `|major| ≥ T` and `|major| ≥ 1.4·|minor|`.
   - **When you lift your finger** (`ON_RELEASE`): the same two conditions, evaluated on the full displacement at pointer-up.
   - **Both**: if nothing committed by pointer-up, commit when the release velocity along the dominant axis is ≥ 600 dp/s with the same sign as the displacement, the ratio condition holds, and the displacement exceeds touch slop (fling fallback).
4. Threshold `T`: Low 40 dp, **Medium 24 dp** (default), High 12 dp (D-050).
5. Exactly one move per gesture: after a commit the rest of the gesture is consumed; a second pointer before the commit cancels the gesture; a ratio below 1.4 is ignored; a tap does nothing.
6. There is no input queue: each committed move applies synchronously to the latest state, and in-flight slides snap (D-052).
7. Direction mapping is physical and forced LTR: a physical left swipe is `LEFT` in every locale (D-104).
8. The surface is disabled while any modal is open, before `ready`, and while a size switch is loading (ARCHITECTURE.md §6.6).
9. The board keeps a side margin of at least `max(16 dp, systemGestures left/right inset)`. `Modifier.systemGestureExclusion` is not used (D-051).
10. No long-press gesture exists (D-093).

**Edge cases.**
- An exact diagonal (ratio 1.0) is ignored (the legacy game resolved it as vertical, B13).
- A ratio of exactly 1.4 is accepted.
- A slow drag that never reaches `T` and is released without a fling does nothing.
- A short, fast flick below `T` but ≥ 600 dp/s moves, in both timing modes.
- A drag starting on a card button while a card is visible: the surface is disabled, so the button behaves as a normal button.
- With TalkBack on, TalkBack consumes single-finger swipes; the direction pad and custom actions are the input (F17).
- A swipe that starts in the system edge zone may be taken by the system back gesture; the board inset makes this rare, and Back on the root goes home (F21).

**Settings involved.** `moveTiming`, `swipeSensitivity`.

**Persistence touched.** Through F01 (one save per accepted move).

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F15-AC1 | `classify(delta, T = 24)` | Deltas (24, 0), (23.9, 0), (24, 17.1), (24, 17.2), (−30, 30) | RIGHT, null, RIGHT (ratio ≥ 1.4), null (ratio < 1.4), null (diagonal) | `SwipeClassifierTest.thresholdAndDominance` |
| F15-AC2 | `classifyFling` with slop 8 dp, `vMin` 600 dp/s | Delta (10, 0) with velocity (700, 0); (10, 0) with (−700, 0); (10, 0) with (500, 0); (6, 0) with (900, 0) | RIGHT, null, null, null | `SwipeClassifierTest.flingFallback` |
| F15-AC3 | `DURING_SWIPE`, Medium, `FIX_MID_4` | A 30 dp right drag, still pressed; then the drag continues 100 dp and lifts | `onMove(RIGHT)` was called before pointer-up; exactly one move in total | `SwipeInputTest.commitsDuringDragOncePerGesture` |
| F15-AC4 | `ON_RELEASE`, Medium | The same drag | No move while pressed; RIGHT at pointer-up | `SwipeInputTest.onReleaseCommitsAtPointerUp` |
| F15-AC5 | Low, Medium and High; a slow 30 dp right drag released without a fling | Each sensitivity | A move on Medium and High, none on Low | `SwipeInputTest.sensitivityThresholds` |
| F15-AC6 | Medium, both timing modes | A 15 dp right flick at 900 dp/s | RIGHT in both modes | `SwipeInputTest.flingCommitsShortFastSwipe` |
| F15-AC7 | A first pointer dragged 10 dp | A second pointer goes down, then both drag 60 dp | No move | `SwipeInputTest.secondPointerCancels` |
| F15-AC8 | `FIX_MID_4` (in progress) | A 60 dp left drag that starts on the New game button | The board moves LEFT; no dialog; the button's `onClick` did not run | `SwipeInputTest.dragStartingOnButtonDoesNotClick` |
| F15-AC9 | `FIX_MID_4` | A tap on the board, and a tap on an empty area of the header | No move | `SwipeInputTest.tapDoesNothing` |
| F15-AC10 | Locale `fa` (RTL chrome) | A physical 60 dp leftward swipe | `onMove(LEFT)` | `SwipeInputTest.leftSwipeIsLeftInRtl` |
| F15-AC11 | In turn: new-game dialog, size sheet, win card, game menu, What's new sheet | A 60 dp swipe on the board area | No move | `SwipeInputTest.ignoredWhileModalOpen` |
| F15-AC12 | A 60 dp swipe that starts on the header (outside the board), in any arrangement but SCROLL (there it scrolls, F18-AC11) | Released | The move applies | `SwipeInputTest.swipeAnywhereOnGameContent` |
| F15-AC13 | Three swipes LEFT, UP, RIGHT within 50 ms under Full | Committed | Three outcomes apply in order, each to the state produced by the previous one; none dropped or duplicated | `InputBurstOrderingTest.threeSwipesApplyInOrder` |
| F15-AC14 | Gesture insets 0, 24 and 40 dp on each side | `BoardGeometry` computes the side margin | 16, 24 and 40 dp | `BoardGeometryTest.sideMarginRespectsGestureInsets` |

### F16 — Keyboard, D-pad and gamepad

**User story.** As a player with a keyboard, Chromebook or gamepad, I want to play entirely without touch.

**Behavior.**
1. Key map (D-053):

| Action | Keys |
|---|---|
| Up | ↑, W, K, `KEYCODE_DPAD_UP` |
| Right | →, D, L, `KEYCODE_DPAD_RIGHT` |
| Down | ↓, S, J, `KEYCODE_DPAD_DOWN` |
| Left | ←, A, H, `KEYCODE_DPAD_LEFT` |
| Undo | U, Ctrl+Z, Backspace |
| New game | N, R (with the F06 confirmation when in progress) |

2. Gamepad D-pads and hats deliver `DPAD_*` key codes and act like the arrows. No other gamepad buttons are mapped (D-053 rejects a separate mapping).
3. Only `KeyDown` events count. Events with Alt, Meta or Shift are ignored; Ctrl is ignored except for Ctrl+Z.
4. Auto-repeat events (`repeatCount > 0`) are accepted at most once per 120 ms of event time; the initial press always counts.
5. Focus (ARCHITECTURE.md §6.3): the board is focusable and requests focus when Game appears. The letter keys (W A S D, H J K L, U, N, R), Backspace and Ctrl+Z are handled by `onPreviewKeyEvent` on the Game root and act whichever control has focus. Arrows and D-pad keys move tiles while the board has focus; after Tab moves focus to a control, arrows move focus as usual. The focus ring has ≥ 3:1 contrast (D-084, DESIGN_SYSTEM.md §7.7).
6. While a dialog or sheet is open, game keys are ignored and keys go to it: Tab and arrows move focus, Enter and Space activate (D-054).
7. While a card is shown, the keys match the enabled chrome (D-199): direction keys (letters, arrows, D-pad) do not move tiles; U, Ctrl+Z and Backspace undo; N and R start a new game with no confirmation (`WON` and `OVER` are not in progress, D-007); Enter and Space activate the focused card button.
8. An undo key at move 0 shows "Nothing to undo" (F05).
9. The manifest declares the touchscreen not required, so touch-less devices can install (D-090).
10. `MainActivity.onProvideKeyboardShortcuts` returns one `KeyboardShortcutGroup` titled with `app_name`, listing `move_up`, `move_down`, `move_left` and `move_right` (the arrow keys), `undo` (U and Ctrl+Z) and `new_game` (N). It reuses existing strings; the system shows it with Meta+/ (D-203).

**Edge cases.**
- Ctrl+Y and Ctrl+Shift+Z do nothing (there is no redo).
- Holding an arrow: the first press moves, then repeats move at most every 120 ms.
- R on a fresh board starts a new game at once (not in progress).
- On sub-screens, game keys are inactive.
- Under an RTL locale, physical arrow keys still map physically.

**Settings involved.** None.

**Persistence touched.** Through F01, F05 and F06.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F16-AC1 | The board has focus | Each of the 12 letter/arrow keys and the 4 `DPAD_*` codes | Each produces the mapped `Direction` via `onMove` | `KeyboardInputTest.directionKeyMap` |
| F16-AC2 | A game with 3 moves | U, then Ctrl+Z, then Backspace | Three undos (`moveCount` 3 → 0) | `KeyboardInputTest.undoKeys` |
| F16-AC3 | `FIX_MID_4`, then `FIX_FRESH_4` | N, and separately R | The confirmation dialog for the first; an immediate new game for the second | `KeyboardInputTest.newGameKeysFollowConfirmation` |
| F16-AC4 | The board has focus | Shift+W, Alt+←, Meta+D, Ctrl+W, Ctrl+U; then Ctrl+Z | The first five do nothing; Ctrl+Z undoes | `KeyboardInputTest.modifierRules` |
| F16-AC5 | ← held: initial press at 0 ms, repeats (`repeatCount > 0`) at 50, 100, 130, 200 and 250 ms | Delivered | Moves at 0, 130 and 250 ms only | `KeyboardInputTest.autoRepeatLimitedTo120ms` |
| F16-AC6 | In turn: new-game dialog, size sheet, What's new sheet | W, ←, U, N | The game state is unchanged | `KeyboardInputTest.ignoredWhileModalOpen` |
| F16-AC7 | Game appears | No input; then Tab; then → | The board is focused; after Tab a control is focused; → moves focus, not tiles | `KeyboardInputTest.boardTakesFocusAndTabReachesControls` |
| F16-AC8 | A game with an undo just made | Ctrl+Y and Ctrl+Shift+Z | Nothing changes | `KeyboardInputTest.redoShortcutsIgnored` |
| F16-AC9 | The merged debug manifest | Robolectric reads `PackageInfo.reqFeatures` | `android.hardware.touchscreen` is present with `FLAG_REQUIRED` unset | `ManifestPolicyTest.touchscreenNotRequired` |
| F16-AC10 | `MainActivity` resumed | `onProvideKeyboardShortcuts` | One `KeyboardShortcutGroup` titled `app_name` with move up/down/left/right (arrow keys), undo (U, Ctrl+Z) and new game (N) (D-203) | `KeyboardInputTest.providesShortcutGroup` |
| F16-AC11 | Win card, then game-over card | W and ←; then U; then, with the card shown again, N | W and ← change nothing; U undoes the winning or losing move; N starts a new game with no dialog (D-199) | `KeyboardInputTest.cardKeysActLikeChrome` |

### F17 — Accessibility

**User story.** As a TalkBack, Switch Access, low-vision or motion-sensitive player, I want to play the whole game, hear what each move did, and read every tile.

**Behavior.**
1. **Board node** (D-080):
   - `collectionInfo = CollectionInfo(N, N)`;
   - description `a11y_board_description` = "Game board, %1$s by %1$s. %2$s Highest tile %3$s." with `%2$s` from plurals `a11y_tiles` ("%1$s tile." / "%1$s tiles.");
   - custom actions "Move up", "Move down", "Move left", "Move right" (the `move_*` strings shared with the pad), "Undo" (only while `moveCount > 0`) and "New game" (with the F06 confirmation). Each action calls the same ViewModel function as a swipe or key.
2. **Cells**: N² stable nodes with `collectionItemInfo(row, 1, col, 1)` and the label "Row %1$s, column %2$s, %3$s" (`a11y_cell_tile`) or "Row %1$s, column %2$s, empty" (`a11y_cell_empty`). Rows and columns are 1-based, row 1 at the top and column 1 at the left in every locale. The value is the full decimal from `TileLabels.fullDecimal(exp)` (`BigInteger` for exponents ≥ 63, F02). Animated tiles carry no semantics.
3. **Traversal**: score group → board → controls; `traversalIndex` is set in the 3-column and side-panel layouts (D-080).
4. **Touch exploration** (observed with `addTouchExplorationStateChangeListener`; D-081, D-082). While it is on:
   - a status line (polite live region) is composed above the direction pad;
   - a direction pad appears below the board: four 64 dp buttons (Up, Left, Right, Down) plus Undo, in a forced-LTR subtree;
   - the controls-bar Undo is removed, so TalkBack meets no duplicate (DESIGN_SYSTEM.md §6.6).
5. **Status line text** (polite live region; D-081, D-202). Sentences are joined by one space:
   - after an accepted move, "{moved} {merged} {spawned} {score}":
     - moved: `a11y_moved_up` "Moved up." (and `_down`, `_left`, `_right`);
     - merged, only when `mergeCount ≥ 1`. Values are the created tiles, largest first, each a prose number: `a11y_merged_1` "Merged %1$s.", `a11y_merged_2` "Merged %1$s and %2$s.", `a11y_merged_3` "Merged %1$s, %2$s and %3$s.", and plurals `a11y_merged_more` "Merged %1$s, %2$s, %3$s and %4$s more." (quantity = the extra count);
     - spawned: `a11y_spawned` "New %1$s at row %2$s, column %3$s." The value is a game numeral; row and column are 1-based, as in `a11y_cell_tile`;
     - score: `a11y_score` "Score %1$s." with `formatCount(score, locale)`.

     Example: "Moved left. Merged 64 and 16. New 2 at row 4, column 1. Score 1,240."
   - after an invalid move: `a11y_blocked_up` "Can't move up." (and `_down`, `_left`, `_right`);
   - after every undo: `a11y_undone` "Move undone. Score %1$s.";
   - after New game: `a11y_new_game` "New game started.", then one `a11y_spawned` per start tile;
   - after a size switch: `a11y_size_switched` "Board %1$s. Score %2$s.", with the board as `"$n×$n"` and the score from `formatCount`.

   There is no verbosity setting (D-202).
6. **Cards**: `paneTitle` "You win!" / "Game over"; one assertive live-region announcement of the title; focus moves to the primary button (Keep going / New game). `View.announceForAccessibility` is never used (D-081).
7. Contrast follows D-083 (F12-AC4). Every interactive element is ≥ 48×48 dp. Chrome text is in sp and survives `fontScale` 2.0 by wrapping to two lines. Tile numerals ignore `fontScale`. Focus indicators have ≥ 3:1 contrast. Color is never the only cue (D-084).
8. ATF runs at `CheckLevel.Error` on every golden except `motion_*` (D-085, D-204), and every screen-level screenshot test also runs `assertTouchTargets()`, QUALITY.md §1.12's size-and-overlap rule (D-205).
9. Reduced motion is F13.

**Edge cases.**
- 8×8 can merge many pairs in one move: "Merged 64, 32, 16 and 17 more."
- Equal merge values are each listed: "Merged 16, 16 and 8."
- Turning TalkBack on mid-game shows the pad without recreation.
- Switch Access reaches the same custom actions.
- A compact-labelled tile ("16K") reads its full decimal ("16384").
- The first-launch hint card is a polite live region read once (DESIGN_SYSTEM.md §6.2).

**Settings involved.** `motion`, `highContrast`.

**Persistence touched.** None beyond F01/F05/F06.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F17-AC1 | `FIX_MID_4` | The semantics tree is read | The board node has `CollectionInfo(4, 4)` and the description "Game board, 4 by 4. 13 tiles. Highest tile 1024." | `BoardSemanticsTest.boardNodeDescribesCollection` |
| F17-AC2 | `FIX_DENSE_8` on a 360×800 window | The cell nodes are read | 64 cell nodes; a cell rendering "16K" reads "Row r, column c, 16384"; empty cells read "…, empty" | `BoardSemanticsTest.cellLabelsUseFullDecimal` |
| F17-AC3 | `FIX_MID_4` | Perform the "Move left" action | The same outcome as `onMove(LEFT)` | `BoardSemanticsTest.moveActionsApplyMoves` |
| F17-AC4 | `FIX_FRESH_4`, then after one move | Read the actions | "Undo" is absent at move 0 and present after the move; performing it undoes | `BoardSemanticsTest.undoActionOnlyWhenAvailable` |
| F17-AC5 | `FIX_MID_4` | Perform "New game" | The confirmation dialog appears | `BoardSemanticsTest.newGameActionFollowsConfirmation` |
| F17-AC6 | A move in flight under Full | Read the tree at t = 48 ms | Exactly N² cell-labelled nodes; no node comes from a `TileView` | `BoardSemanticsTest.tilesCarryNoSemantics` |
| F17-AC7 | STACKED, THREE_COLUMN and SIDE_PANEL windows, and SCROLL (`w360dp-h450dp`, touch exploration on) | Traversal order is read | Score group, then board, then controls (with the pad: status line, pad, New game, More) | `BoardSemanticsTest.traversalOrderInAllLayoutModes` |
| F17-AC8 | Touch exploration off, then turned on via `ShadowAccessibilityManager` | The next frame | Status line and direction pad are absent, then present | `TalkBackModeTest.padAndStatusLineFollowTouchExploration` |
| F17-AC9 | Touch exploration on; a LEFT move merging 32+32 and 8+8 to score 1240 | Committed | The status line reads "Moved left. Merged 64 and 16. New 2 at row R, column C. Score 1,240.", with R and C (and the value, should the computed spawn be a 4) from the fixture's computed spawn | `TalkBackModeTest.statusLineDescribesMove` |
| F17-AC10 | A move with merges creating 64, 32, 16, 8, 4 | Committed | "… Merged 64, 32, 16 and 2 more. …" | `TalkBackModeTest.statusLineSummarizesManyMerges` |
| F17-AC11 | An UP move that is a no-op | Attempted | The status line reads "Can't move up." | `TalkBackModeTest.statusLineDescribesBlockedMove` |
| F17-AC12 | Touch exploration on; a winning move, then (another game) a losing move | Each card appears | `paneTitle` "You win!" / "Game over"; an assertive live region; focus on Keep going / New game | `TalkBackModeTest.cardsAnnounceAndTakeFocus` |
| F17-AC13 | Locale `fa`, touch exploration on | The pad renders; tap its left-arrow button | The left button is left of the right button; the move is `LEFT` | `TalkBackModeTest.directionPadForcedLtrUnderPersian` |
| F17-AC14 | Touch exploration on | Count clickable nodes labelled "Undo" | Exactly one (the pad's) | `TalkBackModeTest.controlsUndoRemovedWhilePadShown` |
| F17-AC15 | `fontScale = 2.0`, `FIX_MID_4`, Pixel7, under Motion Full and Off | Header and controls render | No text node reports `TextLayoutResult.hasVisualOverflow` | `GameScreenTest.chromeTextNotTruncatedAtFontScale2` |
| F17-AC16 | Every golden except `motion_*` | Captured | ATF reports no `Error`-level result. Cross-cutting: enforced by the ATF rule (`testing/Accessibility.kt`) on every method of `GameScreenshotTest`, `ScreensScreenshotTest`, `LocaleScreenshotTest` and `LayoutScreenshotTest` (`layout_*` included); `MotionFilmstripTest` (`motion_*`) is exempt (DESIGN_SYSTEM.md §10.2, D-204) | ATF rule (D-085) |
| F17-AC17 | `fontScale = 2.0` | Goldens 27–28 | They match | `ScreensScreenshotTest.fontScale200Goldens` |
| F17-AC18 | Touch exploration on, `FIX_MID_4` | Golden 36 | It matches | `GameScreenshotTest.talkBackPadGolden` |
| F17-AC19 | Touch exploration on, `FIX_MID_4` | Undo | The status line reads "Move undone. Score {score}." | `TalkBackModeTest.statusLineAfterUndo` |
| F17-AC20 | Touch exploration on | New game on a fresh 4×4, then switch to 5×5 | "New game started." followed by one "New {v} at row r, column c." per start tile; then "Board 5×5. Score {score}." | `TalkBackModeTest.statusLineAfterNewGameAndSizeSwitch` |
| F17-AC21 | Motion Full, `fontScale` 2.0, `FIX_RAMP_5` (score 2456788), `w360dp-h800dp` | Render | Every score digit `Text` reports the same `fontSize`, and the digit row width ≤ the chip's content width | `GameScreenTest.scoreDigitsShareOneSizeUnderFullMotion` |

### F18 — Adaptive layouts and orientation

**User story.** As a player on a phone in landscape, a foldable, a tablet or a desktop window, I want the biggest board that fits with the controls where my hands are, and no orientation lock.

**Behavior.**
1. The manifest has no `screenOrientation`, no `configChanges`, no resizeability or aspect-ratio attributes, and `android:appCategory="game"` (D-090). Every orientation is supported.
2. **Preferred** mode, from `currentWindowAdaptiveInfoV2().windowSizeClass` (D-092), evaluated in this order (D-091):
   1. window height < 480 dp → **THREE_COLUMN**: `[stats rail] [board] [actions rail]`;
   2. window width ≥ 840 dp → **SIDE_PANEL**: the board plus a side panel holding the title row, score cards, actions, size chip and menu, with the board vertically centered. The panel's height equals the board's and its top aligns with the board's top (D-188);
   3. otherwise → **STACKED**: header (112 dp), board, controls bar (80 dp). The chrome is measured, not fixed: header + controls + 2 × 24 dp, which is 240 dp at `fontScale` 1.0 (D-188).
3. The arrangement drawn is resolved by `resolveGameLayout` (D-188, DESIGN_SYSTEM.md §5.2–§5.4) from the board each candidate would give, with D-075's margins applied per axis (horizontal by width class, vertical by height class), the 208 dp rails while the TalkBack pad is shown, and the measured chrome. The first rule that holds wins:
   1. the preferred THREE_COLUMN or SIDE_PANEL, when its board is at least `BoardFloor` = 220 dp, its rail or panel content fits, and its board is at least STACKED's (or STACKED does not fit);
   2. STACKED, with the full header while its board stays at least 280 dp and otherwise the COMPACT 64 dp header (size chip, Score, Best; no title; 8 dp gaps), when its board is at least 220 dp;
   3. THREE_COLUMN, whatever the height class, when it fits;
   4. **SCROLL**: one scrolling column with the COMPACT header, the board sized to fill the first screen (together with the status line while the pad is shown), and the rest of the chrome below the fold.

   The board is never below 220 dp or above 720 dp. When the available width is below 280 dp, STACKED uses the COMPACT header and a board of `max(220 dp, availW)` while the height allows it.
4. A live layout change that keeps the arrangement (the hint leaving, the pad showing or hiding, an Immersive toggle or an inset change) animates board and panel bounds with `animateBounds(overlaySpatial)` under Full only; otherwise it is instant. A live change that switches the arrangement is never animated: `GameLayout` composes the new arrangement anew, so the board renders its settled static frame at its new size, and a move animation in flight snaps to its end as on recreation (F07; D-095, MOTION_SPEC.md §3.16).
5. Rotation, resize and fold recreate the activity, and the game restores `Static` (F07).
6. Foldable posture layouts (tabletop, book) are deferred (§6.2).
7. The first-launch hint sits above the board in STACKED and SCROLL, in the actions rail in THREE_COLUMN (replacing Undo and New game while shown) and in the panel in SIDE_PANEL; the snackbar host follows D-186, and overlays the board in SCROLL (D-188).
8. In SCROLL, a drag that starts on the board is a move and a drag that starts anywhere else scrolls the column (D-051, D-188). Keys, the pad and the board's accessibility actions are unchanged. When a win or game-over card composes, the column scrolls until the whole board is visible (animated under Full, instant otherwise).

**Edge cases.**
- 1000×400 dp (wide and short) is THREE_COLUMN: the height rule prefers it and its rails still leave a 344 dp board (24 dp status bar), while STACKED would leave 184 dp, below the floor.
- A phone split-screen pane of 411×450 dp prefers THREE_COLUMN, but its rails would leave a 35 dp board (−61 dp with the 208 dp TalkBack rails). It is STACKED with the COMPACT header (234 dp board), or SCROLL (274 dp) with the pad. At 360×450 the rails would leave −16 dp (−112 dp); the result is the same.
- A window at least 840 dp wide whose STACKED board is larger is STACKED: 900×1400 gives 720 dp stacked against 516 dp with the panel.
- Height exactly 480 dp does not prefer THREE_COLUMN; width exactly 840 dp prefers SIDE_PANEL.
- A freeform desktop window resized live switches modes and keeps the game. The pad appearing or disappearing, or the hint leaving, can switch the arrangement without recreation (for example STACKED ↔ SCROLL at 411×450); the board then appears at its new size at once, static, with no bounds animation (item 4, F18-AC13).

**Settings involved.** `motion` (bounds animation).

**Persistence touched.** None.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F18-AC1 | Window sizes 360×800, 915×412, 673×841, 841×673, 800×1280, 1280×800, 1000×400, 600×479, 600×480, 839×600, 840×600 dp | `layoutModeFor(width, height)` (the preference) | STACKED, THREE_COLUMN, STACKED, SIDE_PANEL, STACKED, SIDE_PANEL, THREE_COLUMN, THREE_COLUMN, STACKED, STACKED, SIDE_PANEL; never SCROLL | `LayoutModeTest.modeTableAndBoundaries` |
| F18-AC2 | The first nine windows of DESIGN_SYSTEM.md §5.3's worked examples (the D-091 windows plus 411×914, 412×915 and 360×640), with its reference insets and a nominal `ChromeMeasure` | `resolveGameLayout` | Arrangement and board equal the first nine rows of DESIGN_SYSTEM.md §5.3's worked examples; 360×800 gives STACKED, 328 dp; no board exceeds 720 dp | `BoardGeometryTest.boardSizePerMode` |
| F18-AC3 | A window 800 dp tall with available width 250 dp, then 200 dp, zero insets, nominal chrome | `resolveGameLayout` | STACKED with the COMPACT header (no title) and a 250 dp board; then STACKED, COMPACT, 220 dp | `BoardGeometryTest.narrowWindowFloor` |
| F18-AC4 | `FIX_MID_4` | The 7 layout goldens 29–35 (D-092 qualifiers) | They match | `LayoutScreenshotTest.qualifierMatrixGoldens` |
| F18-AC5 | `FIX_MID_4` in `w411dp-h914dp` | Qualifiers change to `w914dp-h411dp-land` and the activity recreates | The same game state; layout mode THREE_COLUMN; every tile `Static` | `RecreateNoReplayTest.rotationKeepsStateStatic` |
| F18-AC6 | The merged manifest | Robolectric reads `ActivityInfo` of `MainActivity` | `screenOrientation == SCREEN_ORIENTATION_UNSPECIFIED` and `configChanges == 0` | `ManifestPolicyTest.noOrientationLockOrConfigChanges` |
| F18-AC7 | The merged manifest | Robolectric reads `ApplicationInfo` | `category == ApplicationInfo.CATEGORY_GAME` | `ManifestPolicyTest.appCategoryIsGame` |
| F18-AC8 | Split-screen reference insets (24 dp top, 0 bottom), nominal chrome, the pad off unless stated | `resolveGameLayout` for 360×450, 360×450 with the pad, 411×450, 411×450 with the pad, 620×450, 700×450, and 900×1400 (portrait insets) | STACKED COMPACT 234 dp; SCROLL 274 dp; STACKED COMPACT 234 dp; SCROLL 274 dp; STACKED COMPACT 234 dp (THREE_COLUMN's 228 dp is smaller); THREE_COLUMN 308 dp, rail 160; STACKED 720 dp (SIDE_PANEL's 516 dp is smaller). None of the 450 dp tall windows is THREE_COLUMN except 700×450 | `BoardGeometryTest.resolvedLayoutTable` |
| F18-AC9 | Every window from 220×220 to 1600×1600 dp in 20 dp steps, zero insets, the pad off and on, the hint off and on, nominal chrome scaled by 1.0 and 1.5 | `resolveGameLayout` | 220 ≤ board ≤ 720 dp; THREE_COLUMN or SIDE_PANEL only with a board at least STACKED's whenever STACKED fits; outside SCROLL the board plus its chrome terms fit `availH`, and the board plus rails or panel and gaps fit `availW` (a STACKED board exceeds `availW` only when `availW` < 220 dp, and then equals 220 dp); SCROLL only when STACKED, THREE_COLUMN and the preferred SIDE_PANEL all fail | `BoardGeometryTest.boardNeverBelowFloorOrOffWindow` |
| F18-AC10 | `FIX_MID_4` at `w360dp-h450dp` and `w411dp-h450dp`, touch exploration off and on | Rendered | The board node is square, at least 220 dp, and equals `resolveGameLayout`'s board for the window and the root's insets (258 dp off, 298 dp on with zero insets). Off: no "2048" heading (COMPACT header), no scroll action, and every chrome node lies inside the root and does not intersect the board. On: the content has a vertical scroll action; the board and the status line are fully on screen; `performScrollTo()` on "Move up" and on "New game" makes each fully visible | `GameScreenTest.shortWindowsKeepBoardAndChromeOnScreen` |
| F18-AC11 | `FIX_MID_4` at `w360dp-h360dp` (SCROLL without the pad, board 256 dp with zero insets), each case from a fresh `FIX_MID_4` at scroll 0 | A 60 dp left swipe starting on the board; a 60 dp upward drag starting on the header; a 60 dp upward swipe starting on the board | `LEFT` applies and the scroll value is unchanged; no move applies and the scroll value grows; `UP` applies and the scroll value is unchanged | `SwipeInputTest.scrollModeMovesOnlyFromBoard` |
| F18-AC12 | `w360dp-h360dp`, Motion Off, a 4×4 game with row 1 = `10,10,1,0` (`FIX_WON_4` with that row and status `PLAYING`), the column scrolled until More is visible | The Left key is pressed and the win card composes | The scroll value places the whole board inside the viewport | `GameScreenTest.scrollModeCardBringsBoardIntoView` |
| F18-AC13 | `FIX_FILM` at `w411dp-h450dp`, Motion Full (STACKED, 258 dp board with zero insets); LEFT committed | Touch exploration turns on at t = 48, mid-slide, so the arrangement becomes SCROLL (298 dp board); later it turns off again | The board is 258 dp until the switch and 298 dp from the first SCROLL frame, with no intermediate size, and back to 258 dp on the first STACKED frame; on the first SCROLL frame every tile is `Static` at its engine cell with scale 1 and alpha 1, no "+N" float appears then or later, and nothing is running; the move log is unchanged and no haptic fires twice (D-095) | `LayoutMotionTest.arrangementSwitchRendersSettledBoard` (also AC-LAYOUT-2) |

### F19 — Edge-to-edge and Immersive mode

**User story.** As a player, I want the game to use the whole screen, and optionally to hide the status and navigation bars, without a hidden gesture that fights my swipes.

**Behavior.**
1. `enableEdgeToEdge()` runs before `setContent`, always (D-023, D-093). The background draws behind the system bars; content pads with `WindowInsets.safeDrawing`, which includes display cutouts. System-bar icon appearance follows the effective theme (F12).
2. Setting **Immersive mode** (default Off): when On, `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. It applies app-wide on every destination and is re-applied whenever the window regains focus (after the share chooser, a system dialog or a multi-window focus change). Switching it Off shows the bars at once. `ImmersiveMode.apply(window, on)` delegates to a small `SystemBarsController` interface whose default wraps `WindowInsetsControllerCompat`; tests inject a recording fake (PLAN.md §2.6 E). Production behavior is the same either way.
3. An edge swipe reveals the bars transiently; game swipes are unaffected because the board is inset (F15).
4. There is no gesture that toggles bars. The legacy long press is gone (D-093).

**Edge cases.**
- With gesture navigation, the edge swipe shows the bars transiently, and Back still works.
- In multi-window, the system may decline to hide bars; nothing errors.
- Cutouts stay respected in Immersive mode (`safeDrawing`).

**Settings involved.** `immersive`.

**Persistence touched.** `settings.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F19-AC1 | Default settings, SDK 36 | The activity starts | `window.attributes.layoutInDisplayCutoutMode == LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (what `enableEdgeToEdge()` sets on API 30+; verify against activity 1.13.0 at first use and record any difference in the PR body) | `ImmersiveModeTest.edgeToEdgeAlwaysApplied` |
| F19-AC2 | `immersive = true` | Game is shown | System bars are hidden with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, as recorded by the injected `SystemBarsController` fake (hide call and behavior flag) | `ImmersiveModeTest.onHidesSystemBars` |
| F19-AC3 | Immersive on | The setting is switched Off | System bars are shown | `ImmersiveModeTest.offShowsSystemBars` |
| F19-AC4 | Immersive on; the bars were shown by another window | `onWindowFocusChanged(true)` | The bars are hidden again | `ImmersiveModeTest.reappliedOnFocusGain` |
| F19-AC5 | Immersive on | Navigate to Settings and to About | The bars stay hidden on both | `ImmersiveModeTest.appliesOnSubScreens` |
| F19-AC6 | `FIX_MID_4` | A 3,000 ms press without movement on the board, then on the New game button | No move, no setting change; the long press on the button does not fire it | `SwipeInputTest.longPressDoesNothing` |

### F20 — Keep screen on

**User story.** As a player who thinks for a long time between moves, I want the screen to stay on during a game, without draining the battery otherwise.

**Behavior.**
1. Setting "Keep screen on while playing" (default Off; D-006). It needs no permission (research/05-features.md §1).
2. When On, `LocalView.current.keepScreenOn` is `true` only while the Game destination is on top of the back stack and resumed **and** the game is in progress. Otherwise it is `false`.
3. It updates live: a card appearing turns it off; Keep going turns it on; a new game turns it off until the first accepted move.

**Edge cases.**
- Off while a card is shown, on a fresh board, on any sub-screen, and during the loading state.
- The system ignores the flag while the app is in the background.

**Settings involved.** `keepScreenOn`.

**Persistence touched.** `settings.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F20-AC1 | `keepScreenOn = true`, `FIX_MID_4` | Game is visible | `keepScreenOn == true` on the Compose root view | `KeepScreenOnTest.onWhileGameInProgress` |
| F20-AC2 | `keepScreenOn = true`, `FIX_FRESH_4` | Game is visible; then one accepted move | `false`, then `true` | `KeepScreenOnTest.offOnFreshBoard` |
| F20-AC3 | `keepScreenOn = true` | `FIX_WON_4`, then `FIX_OVER_4` | `false` for both | `KeepScreenOnTest.offWhileCardShown` |
| F20-AC4 | `keepScreenOn = true`, `FIX_MID_4` | Navigate to Settings, then back | `false` on Settings, `true` again on Game | `KeepScreenOnTest.offOnSubScreens` |
| F20-AC5 | `keepScreenOn = false`, `FIX_MID_4` | Game is visible | `false` | `KeepScreenOnTest.offWhenSettingOff` |

### F21 — Navigation and predictive back

**User story.** As a player, I want the app to open straight into the game, reach every screen from one menu, and have Back behave like every other modern Android app.

**Behavior.**
1. One activity; Navigation 3 with the keys `GameKey` (start), `StatsKey`, `SettingsKey`, `HowToPlayKey`, `AboutKey` and `LicensesKey` (D-023). Launch opens the game directly, and Game has no top app bar (keeps legacy S1).
2. The Game menu (More button, `DropdownMenu`) has Statistics, Settings, How to play and About. About links to Open-source licenses.
3. Sub-screens have a top app bar with back navigation.
4. **Back** (D-054, D-094):
   - dismisses sheets, dialogs and the menu;
   - pops sub-screens through `NavDisplay`;
   - on the Game root is **never intercepted**, including while a card is shown, so the system goes home with the predictive animation.

   There is no double-back-to-exit and no "press back again" toast.
5. Sub-screen transitions and the predictive-back pop follow MOTION_SPEC.md §3.17 and §3.19 (`ui/theme/GameNavTransitions.kt`, D-183); under Off there is no transition.
6. The back stack and open overlays survive recreation and process death (F07).
7. Leaving Game pauses the play clock and saves (F07).

**Edge cases.**
- Back on the What's new sheet dismisses it; it was already stored as seen (F24).
- Back while the first-launch hint is visible goes home (the hint is inline, not modal).
- Back during a size-switch load goes home; the old game was already saved.
- Opening the current destination again from the menu adds no duplicate entry.

**Settings involved.** `motion` (transitions).

**Persistence touched.** None (saveable UI state only).

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F21-AC1 | A fresh install at SDK 24 and 37 | The app launches | The board node exists, the title reads "2048", and there is no navigate-up node | `LaunchSmokeTest.launchesIntoGameWithoutTopBar` |
| F21-AC2 | The Game menu | Tap Statistics, Settings, How to play and About in turn (Back in between) | Each destination shows with a back-navigation node | `GameScreenTest.menuOpensEveryDestination` |
| F21-AC3 | Only `GameKey` on the stack, nothing open | `onBackPressedDispatcher.onBackPressed()` once | `activity.isFinishing == true` | `BackOnRootTest.singleBackFromRootFinishes` (also AC-BACK-2) |
| F21-AC4 | `SettingsKey` on top; then `AboutKey` → `LicensesKey` | Back | Pops to `GameKey`; then `LicensesKey` → `AboutKey` → `GameKey` | `BackOnRootTest.backFromSubScreenPops` |
| F21-AC5 | The size sheet open; then the game menu open | Back | Only the sheet (then the menu) closes; the activity is not finishing | `BackOnRootTest.backDismissesSheetOrMenuOnly` |
| F21-AC6 | The new-game dialog open | Back | Only the dialog closes; the game is unchanged | `BackOnRootTest.backDismissesDialogOnly` |
| F21-AC7 | The win card visible | Back | The activity finishes; `game-4.json` still holds `WON` | `BackOnRootTest.backWithWinCardGoesHome` |
| F21-AC8 | The game-over card visible | Back | The activity finishes; `game-4.json` still holds `OVER` | `BackOnRootTest.backWithGameOverCardGoesHome` |
| F21-AC9 | `GameKey` → `AboutKey` → `LicensesKey` | `ActivityScenario.recreate()` | The stack is unchanged | `BackOnRootTest.backStackSurvivesRecreation` |
| F21-AC10 | Motion Off | Open Settings | The Settings content is fully displayed on the next frame | `GameScreenTest.navigationIsInstantWhenMotionOff` |

### F22 — First-launch hint

**User story.** As a new player, I want one short line telling me how to play, shown once, that gets out of my way.

**Behavior.**
1. It shows when `onboardingDone == false` and the starting game has `moveCount == 0` and `undoCount == 0`; a missing game file counts as `moveCount == 0` (D-201). In practice that means a fresh install, including one closed before its first move.
2. The hint card reads `hint_first_launch` "Swipe to move all tiles. Equal tiles merge. Reach %1$s." with a "Got it" button (DESIGN_SYSTEM.md §6.2). While touch exploration is on, it reads `hint_first_launch_talkback` "Use the arrow buttons to move all tiles. Equal tiles merge. Reach %1$s." instead, because TalkBack consumes swipes (D-202). `%1$s` is the current size's target as a game numeral (2048, or 256 on 3×3) and follows size switches.
3. Placement (D-188): above the board in STACKED; in the actions rail in THREE_COLUMN, replacing Undo and New game while shown; in the panel in SIDE_PANEL.
4. The first accepted move (on any size) or "Got it" dismisses it and sets `onboardingDone = true`. It never shows again.
5. It is not modal: swipes work while it is visible, and invalid moves do not dismiss it.
6. It is a polite live region, read once. Under Full it exits with shrink + fade and, while the arrangement stays the same, the board grows back with `animateBounds`; otherwise it disappears instantly. If its exit switches the arrangement, the board appears at its new size at once, static (F18 item 4).
7. It replaces the legacy "Long click to toggle fullscreen" toast, which appeared on every `onCreate` (S5). The app shows no toast at all.

**Edge cases.**
- A size switch while it is visible keeps it and updates the target.
- After an update or a backup restore, the starting game normally has moves, so no hint shows even if `onboardingDone` is false (for example after a corrupt `settings.json`).
- A fresh install closed before any move: the fresh game saved at first launch still has `moveCount == 0`, so the next launch shows the hint again (D-201).

**Settings involved.** `onboardingDone`.

**Persistence touched.** `settings.json` (`onboardingDone`).

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F22-AC1 | No files at all | A `GameViewModel` loads | `uiState.showFirstLaunchHint == true` | `GameViewModelTest.hintShownOnFreshInstall` |
| F22-AC2 | `game-4.json` exists with `moveCount == 0` and `undoCount == 0`; `onboardingDone == false` | Loads | `showFirstLaunchHint == true` (D-201) | `GameViewModelTest.hintShownWhileStartingGameHasNoMove` |
| F22-AC3 | The hint is showing | The first accepted move | `showFirstLaunchHint == false`; a settings write with `onboardingDone = true` | `GameViewModelTest.firstAcceptedMoveDismissesHint` |
| F22-AC4 | The hint is showing | An invalid move | The hint stays; no settings write | `GameViewModelTest.invalidMoveKeepsHint` |
| F22-AC5 | The hint is showing | Tap "Got it"; rebuild the container on the same files | Hidden at once; not shown after the rebuild | `GameScreenTest.gotItDismissesHintPermanently` |
| F22-AC6 | The hint on 4×4 | Read it; switch to 3×3; read again | "… Reach 2048." then "… Reach 256." | `GameScreenTest.hintTextUsesBoardTarget` |
| F22-AC7 | SDK 24 and 37 | Launch, then `recreate()` | `ShadowToast.getLatestToast() == null` | `LaunchSmokeTest.noToastOnLaunchOrRecreate` |
| F22-AC8 | `FIX_FRESH_4`, `onboardingDone = false` | Golden 45 | It matches | `GameScreenshotTest.firstLaunchHintGolden` |
| F22-AC9 | Touch exploration on; fresh install | Game shows | The hint text is `hint_first_launch_talkback`, not `hint_first_launch` (D-202) | `GameScreenTest.hintTextUnderTalkBack` |
| F22-AC10 | `game-4.json` with `moveCount ≥ 1`; `onboardingDone == false` | Loads | `showFirstLaunchHint == false` | `GameViewModelTest.hintHiddenAfterAMove` |

### F23 — How to play

**User story.** As a player, I want a short reference for the rules, board sizes, undo and keys that I can open at any time.

**Behavior.**
1. `HowToPlayKey`, opened from the Game menu; top app bar "How to play" with back navigation; a scrolling column at most 640 dp wide (DESIGN_SYSTEM.md §6.10).
2. Sections, in order, each with a heading (`semantics { heading() }`) and body:
   1. Goal: build 2048; 256 on 3×3;
   2. Moving;
   3. Merging, with the forced-LTR illustration "2 2 2 2 → 4 4" and its a11y text;
   4. New tiles: "a 2 nine times out of ten, otherwise a 4";
   5. Board sizes: a table 3×3…8×8 with goals 256 / 2048 ×5, and "Each size keeps its own game, best score and statistics.";
   6. Undo: unlimited, works after game over, brings back the same next tile, counted, and excluded from Best without undo;
   7. Keyboard and controllers: the D-053 table (key letters stay Latin in every locale; only key names are translated);
   8. TalkBack: the direction pad and the actions menu.
3. Every text is a string resource in all 21 locales (F28).

**Edge cases.**
- At `fontScale` 2.0 the column scrolls and nothing truncates.
- Under RTL the chrome mirrors, but the merge illustration stays left-to-right.

**Settings involved.** None.

**Persistence touched.** None.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F23-AC1 | How to play opens | Read the heading nodes | Goal, Moving, Merging, New tiles, Board sizes, Undo, Keyboard and controllers, TalkBack, in that order | `HowToPlayScreenTest.sectionsInOrder` |
| F23-AC2 | Locale en | Read the Goal body | It contains "2048" and "256" | `HowToPlayScreenTest.goalStatesBothTargets` |
| F23-AC3 | Locale en | Read New tiles and Undo | "nine times out of ten", "also works after game over" and "same next tile" appear | `HowToPlayScreenTest.spawnAndUndoRulesStated` |
| F23-AC4 | The Board sizes table | Read it | 6 rows: 3×3 → 256, 4×4…8×8 → 2048 | `HowToPlayScreenTest.boardSizeTableHasSixRows` |
| F23-AC5 | Locale en | Read the key table | It names W A S D, H J K L, U, Ctrl+Z, Backspace, N and R, and `KeyMap` maps exactly those keys | `HowToPlayScreenTest.keyTableMatchesKeyMap` |
| F23-AC6 | Locale `fa` | Read the merge illustration | The first mini tile is the leftmost; its description is the `htp_merging_illustration_cd` string | `HowToPlayScreenTest.mergeIllustrationIsLtrWithDescription` |
| F23-AC7 | Default | Golden 20 | It matches | `ScreensScreenshotTest.howToPlayGolden` |

### F24 — What's new

**User story.** As a returning player who just updated, I want a short, dismissible list of what changed, and never a changelog popup on a fresh install.

**Behavior.**
1. `settings.lastSeenVersionCode` (default 0) and the current versionCode (`AppContainer.versionCode`, the compile-time `BuildConfig.VERSION_CODE`; no `PackageManager` lookup, ARCHITECTURE.md §4.2, D-134) feed `decideWhatsNew` (ARCHITECTURE.md §4.13), evaluated once after settings load:
   - `lastSeen == 0` (fresh install): store the current code; show nothing;
   - `lastSeen < current`: show the entries for every versionCode in `(lastSeen, current]`, oldest first, and store the current code **immediately**, before the sheet is visible;
   - `lastSeen ≥ current`: show nothing and write nothing, so a downgrade keeps the higher value.
2. The sheet is a `GameModalSheet` over the Game root (DESIGN_SYSTEM.md §6.13; its opt-in is isolated by D-181): "What's new", "Version %1$s", bullet rows and a "Got it" button. Drag, Back, a scrim tap or "Got it" dismiss it. It is modal (F15, F16).
3. `about/Changelog.kt` maps versionCode → `List<@StringRes Int>`. v1.0 ships 30000 with `whatsnew_30000_1` … `_5`.
4. About always shows the full history (F25).
5. The sheet cannot appear in a v1.0 build, because 30000 is the first versionCode of this app (D-009 note); `WhatsNewTest` verifies the mechanism.

**Edge cases.**
- A corrupt `settings.json` resets `lastSeenVersionCode` to 0, which reads as a fresh install: no sheet.
- Process death while the sheet is open: it does not come back, because the code was stored first.
- Several skipped versions: every entry in the range, oldest first.

**Settings involved.** `lastSeenVersionCode`.

**Persistence touched.** `settings.json`.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F24-AC1 | `lastSeen 0`, current 30000 | `decideWhatsNew` | No entries; `storeVersionCode == 30000` | `WhatsNewTest.freshInstallStoresVersionAndShowsNothing` |
| F24-AC2 | `lastSeen 29999`, current 30000, the real `CHANGELOG` | Decided | The 5 entries of 30000; store 30000 | `WhatsNewTest.updateShowsEntriesNewerThanLastSeen` |
| F24-AC3 | A test changelog `{20100: [a], 30000: [b]}` | `lastSeen` 20000, then 20100 | `[a, b]`, then `[b]` | `WhatsNewTest.entriesCoverExactlyTheSkippedRange` |
| F24-AC4 | `lastSeen 30000`, current 30000 | Decided | Nothing shown; nothing stored | `WhatsNewTest.sameVersionShowsNothing` |
| F24-AC5 | `lastSeen 40000`, current 30000 | Decided | Nothing shown; `lastSeen` stays 40000 | `WhatsNewTest.downgradeShowsNothingAndKeepsLastSeen` |
| F24-AC6 | An `AppViewModel` with `lastSeen 29999` | It evaluates | The settings write with 30000 is submitted before `whatsNew` emits a non-null list | `WhatsNewTest.versionStoredBeforeSheetIsShown` |
| F24-AC7 | The sheet visible over `FIX_MID_4` | A swipe; then "Got it" | The swipe does nothing; "Got it" closes the sheet | `GameScreenTest.whatsNewSheetIsModalAndDismissible` |
| F24-AC8 | Each of the 21 locales | Resolve every `CHANGELOG` string id | Each resolves to a non-blank string | `LocalizationTest.changelogStringsResolveInEveryLocale` |
| F24-AC9 | `lastSeenVersionCode = 20000` | Golden 44 | It matches | `ScreensScreenshotTest.whatsNewSheetGolden` |

### F25 — About and credits

**User story.** As a curious player, I want to see the version, what changed, where the source lives, who built the game before me, and how my data is handled.

**Behavior** (DESIGN_SYSTEM.md §6.11).
1. Header: the app icon composed from its layers at 72 dp, "2048", and "Version 3.0.0 (30000)", formatted from the compile-time `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE` (no `PackageManager`, no I/O in composition; D-134).
2. What's new: every `CHANGELOG` entry, newest version first.
3. Source code row: the supporting text "github.com/clayboicardi/2048-android-modern-refresh" opens `ACTION_VIEW` on `https://github.com/clayboicardi/2048-android-modern-refresh`. On `ActivityNotFoundException` the snackbar "No app can open this link" shows (D-009).
4. Privacy: the D-123 text (F27).
5. Open-source licenses: pushes `LicensesKey` (F26).
6. Credits, from `res/raw/legacy_history.json` `credits[]` (English static data, D-124): "Gabriele Cirulli — original game"; "Paul Sarbinowski (uberspot) — Android port"; one "Contributors" row listing the deduplicated legacy contributors captured in `docs/modernization/legacy-credits.md` in M0, plus "gurshabad", whom legacy commit `f889036` credits for the night-mode idea (D-209); "clayboicardi — native rewrite".
7. Legacy history: the 9 releases of `changelog_master.xml` (research/01-legacy-android-shell.md §5.2), newest first: 2.2 (25), 2.1 (24), 2.08 (23), 2.06 (22), 2.05 (21), 2.0 (20), 1.96 (19), 1.95 (18), 1.91 (18). Both 1.95 and 1.91 carry versionCode 18 in the legacy file and are shown as such.
8. `AboutViewModel` reads both raw JSON files on `Dispatchers.IO`.

**Edge cases.**
- No browser installed: snackbar, no crash.
- Under RTL the chrome mirrors; the URL stays left-to-right.

**Settings involved.** None.

**Persistence touched.** None.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F25-AC1 | The debug build | About opens | The text "Version 3.0.0 (30000)" is shown | `AboutScreenTest.versionLine` |
| F25-AC2 | `lastSeenVersionCode = 30000` | About opens | The What's new section shows "3.0.0" with its 5 bullets | `AboutScreenTest.whatsNewHistoryAlwaysShown` |
| F25-AC3 | A browser activity is resolvable | Tap Source code | The started intent is `ACTION_VIEW` with data `https://github.com/clayboicardi/2048-android-modern-refresh` | `AboutScreenTest.sourceLinkOpensRepository` |
| F25-AC4 | `checkActivities(true)` with no handler | Tap Source code | The snackbar "No app can open this link"; no exception | `AboutScreenTest.sourceLinkWithoutBrowserShowsSnackbar` |
| F25-AC5 | About | Tap Open-source licenses | `LicensesKey` is on top of the stack | `AboutScreenTest.licensesRowOpensLicenses` |
| F25-AC6 | `legacy_history.json` | About renders Credits | Four rows in the order above; the Contributors row contains every contributor name from `legacy-credits.md` | `AboutScreenTest.creditsRows` |
| F25-AC7 | `legacy_history.json` | About renders Legacy history | Nine entries with the titles and order listed above | `AboutScreenTest.legacyHistoryListsNineReleases` |
| F25-AC8 | Default | Golden 21 | It matches | `ScreensScreenshotTest.aboutGolden` |

### F26 — Open-source licenses and legal files

**User story.** As a user or redistributor, I want every license and attribution available in the app and in the repository.

**Behavior.**
1. `LicensesKey` (DESIGN_SYSTEM.md §6.12) renders `res/raw/third_party_licenses.json` (entries with `name`, `groupOrPath`, `licenseId`, `copyright`), grouped as MIT, OFL-1.1, Apache-2.0. Each group has a "Show license text" / "Hide license text" toggle revealing `license_mit.txt`, `ofl_google_sans_flex.txt` or `license_apache_2_0.txt` (D-122).
2. MIT entries: `gabrielecirulli/2048` (© 2014 Gabriele Cirulli: rules, palette, test vectors) and `uberspot/2048-android` (© 2014 Paul Sarbinowski: strings, icon glyph paths) (D-121).
3. OFL-1.1: the Google Sans Flex subset. If the D-076 fallback was used, both the font and its OFL entry and text are absent.
4. Apache-2.0: Material Symbols, AndroidX, Kotlin and kotlinx.
5. Coverage (D-122): `config/license-groups.txt` maps every Maven group on `:app:releaseRuntimeClasspath` to a license id; `scripts/check-licenses.sh` fails on a missing group; `LicensesCoverageTest` checks that every listed group appears in the JSON.
6. Repository files: `LICENSE` (MIT) with three copyright lines in the D-120 order; `NOTICE` with the five D-121 attributions, including the lineage line "A small clone of 1024, based on Saming's 2048 (also a clone)".

**Edge cases.**
- A dependency update that adds a group fails `check-licenses.sh` until `license-groups.txt` and the JSON are updated in the same commit.
- Under the font fallback, no OFL group is shown, and the coverage checks still pass.

**Settings involved.** None.

**Persistence touched.** None.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F26-AC1 | `config/license-groups.txt` | Parsed against `third_party_licenses.json` | Every listed group has at least one JSON entry with the mapped `licenseId` | `LicensesCoverageTest.everyRuntimeGroupHasAnEntry` |
| F26-AC2 | `:app:releaseRuntimeClasspath` | `scripts/check-licenses.sh` | Every resolved group is listed in `config/license-groups.txt` | Gate: check-licenses.sh |
| F26-AC3 | Licenses opens | Read the group headers | "MIT License", "SIL Open Font License 1.1", "Apache License 2.0", in that order; the MIT group names both 2014 copyright holders | `AboutScreenTest.licensesGroupedMitOflApache` |
| F26-AC4 | The MIT group | Tap "Show license text", then "Hide license text" | The MIT text appears, then disappears | `AboutScreenTest.licenseTextToggles` |
| F26-AC5 | The repository | `scripts/check-metadata.sh` | `LICENSE` has exactly the three D-120 copyright lines in order (`check_license_file`); `LICENSE` has sha256 `ad96d837…fd19`, `res/raw/license_mit.txt` is byte-identical to it and `license_apache_2_0.txt` has its canonical sha256 (`check_legal_texts`, D-190); `NOTICE` contains the five D-121 items and the lineage line (`check_legal_and_privacy`, D-190); `ofl_google_sans_flex.txt` exists iff `google_sans_flex_numerals.ttf` exists (`check_font`) | Gate: check-metadata.sh `check_license_file`, `check_legal_texts`, `check_legal_and_privacy`, `check_font` |
| F26-AC6 | MIT group expanded | Golden 22 | It matches | `ScreensScreenshotTest.licensesGolden` |

### F27 — Privacy and the offline guarantee

**User story.** As a privacy-minded player, I want a game that collects nothing, never touches the network, asks for no permissions, and says so plainly.

**Behavior.**
1. The merged manifest has no `INTERNET`, no runtime or dangerous permission and no `VIBRATE`. Its only `uses-permission` is androidx.core's `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (`protectionLevel=signature`), which README and PRIVACY.md document (D-002).
2. No analytics, ads, crash reporting, Google Play Services, Firebase, Play Core, UMP or downloadable fonts; the dependency deny-list enforces it (D-148 step 7).
3. Exported components are exactly `MainActivity` and `androidx.profileinstaller.ProfileInstallReceiver` in release; debug also allows `androidx.activity.ComponentActivity` from `ui-test-manifest` (D-148 step 3).
4. The only outbound intents are `ACTION_VIEW` to the repository (F25), the `ACTION_SEND` chooser (F10) and `ACTION_APP_LOCALE_SETTINGS` (F11). None needs a permission.
5. The privacy text (D-123) appears localized in About and in English in `PRIVACY.md`: "This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup."
6. The EmojiCompat initializer is removed from the manifest, so no font-provider lookup happens (D-090).
7. Nothing is logged or reported, including corruption fallbacks (D-045).

**Edge cases.**
- Store and README wording is "no runtime permissions" (research/07 C25), because F-Droid lists the signature self-permission.
- Backups are performed by the OS, not by the app (F29).

**Settings involved.** None.

**Persistence touched.** None.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F27-AC1 | `app-debug.apk` and `app-release.apk` | `aapt2 dump permissions` | `uses-permission` is exactly `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Gate: check-apk.sh step 2 |
| F27-AC2 | Both APKs | `aapt2 dump xmltree` | The exported set equals the D-148 allowlist | Gate: check-apk.sh step 3 |
| F27-AC3 | `:app:releaseRuntimeClasspath` | Matched against the deny-list | No match | Gate: check-apk.sh step 7 |
| F27-AC4 | Locale en | About renders | The Privacy section equals the D-123 text verbatim | `AboutScreenTest.privacyTextMatchesPolicy` |
| F27-AC5 | The repository | `scripts/check-metadata.sh` | `PRIVACY.md` contains the D-123 text verbatim; `README.md` links `PRIVACY.md` and `NOTICE`; both name `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (D-190) | Gate: check-metadata.sh `check_legal_and_privacy` |
| F27-AC6 | The merged manifest | Robolectric reads the `androidx.startup.InitializationProvider` meta-data | No `androidx.emoji2.text.EmojiCompatInitializer` key | `ManifestPolicyTest.emojiCompatInitializerRemoved` |

### F28 — Localization, per-app language, RTL and digits

**User story.** As a player in any of the 21 supported languages, I want the whole app in my language (or the one I choose for this app), with the board behaving identically in right-to-left languages.

**Behavior.**
1. Locales: English (`values/`) plus **cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, pt, ru, sl, sr, tr, uk**. This was re-verified from the legacy `res/` on 2026-10-28: exactly those 20 `values-xx` directories exist besides `values-sw720dp-land`. `sr` stays Cyrillic and `pt` stays generic Portuguese (D-100).
2. Every string that contains words is translated in all 20 locales before merge. Lint `MissingTranslation`, `ExtraTranslation` and `Untranslatable` are errors (D-100, D-142, TC-3). Word-free format strings (placeholders and separators only, such as `board_size_supporting`, `game_over_summary` and `stats_wins_value`) are `translatable="false"`. English strings added in M3–M4 live in `values/strings_pending.xml` (`tools:ignore="MissingTranslation"` with its same-line reason) until M5 merges them into `values/strings.xml` and deletes the file (`check_strings_final`) (D-208).
3. Legacy migration (D-101):
   - `action_settings` becomes `settings` and keeps its 20 translations;
   - the two hint strings are deleted;
   - the `values-cs` BOM is stripped, and `check_no_bom` keeps every tracked text file free of a leading U+FEFF (D-179);
   - `app_name` is deleted from every locale (including the `fa` "۲۰۴۸"), and the default "2048" is `translatable="false"`;
   - the ru/uk game strings are seeded from `js/i18n.js`, with uk "Cancel" = "Скасувати";
   - "Immersive mode" is seeded from the legacy "full screen" nouns (research/01 §8.1).
4. `docs/modernization/i18n-glossary.md` is committed before any translation; README states that translations are machine-assisted (D-105).
5. Per-app language: `generateLocaleConfig = true` plus `resources.properties` `unqualifiedResLocale=en-US`. On API 33+ the Settings row "App language" opens the system page (F11). There is no `androidx.appcompat:appcompat`, no `AppCompatDelegate` and no in-app picker; below API 33 the app follows the system language (D-102). `androidx.appcompat:appcompat-resources`, pulled in by `core-splashscreen`, is allowed and listed in the licenses (D-208).
6. Every count uses `<plurals>` and `pluralStringResource`: moves, undos, tiles and merges. There is no `games` plural, because no v1.0 sentence counts games. cs, es, fr, it, lt and pt carry a grammatical `many` item (D-103, D-208). Sentences are never concatenated from fragments (D-103); a chip's label and value are joined by `a11y_chip_value` = `%1$s %2$s`.
7. Digits (D-103, DESIGN_SYSTEM.md §3.7):
   - game numerals use `Long.toString()` (Latin, no grouping);
   - prose numbers use `formatCount(n, locale)` (Latin digits via `-u-nu-latn`, locale grouping);
   - durations use `formatElapsed` (`Locale.ROOT`, `m:ss` or `h:mm:ss`);
   - percentages use `formatPercent` (the `latn` percent instance);
   - `toLatinDigits()` maps Arabic-Indic and Persian digits to ASCII (L10N_STORE_LEGAL.md §1.10). All four live in `ui/components/Formatting.kt` (M3).
8. RTL (D-104): `supportsRtl="true"`. The chrome mirrors. The board subtree, the gesture mapping, the key map, the TalkBack move actions and the direction pad are forced LTR.
9. Google Sans Flex styles only digit-only numerals and the title; every translated string uses the system font, which covers Persian, Cyrillic, Greek and Esperanto (D-076).
10. Debug builds enable the pseudo-locales `en-XA` and `ar-XB` for goldens (D-106).

**Edge cases.**
- A language change through the system page recreates the activity; the game restores `Static` (F07).
- A system language outside the 21 falls back to English.
- A pt-BR or pt-PT device uses the generic `pt` resources.
- Long German strings wrap to two lines (golden 24).
- Under `fa`, game numerals stay Latin; prose numbers use Latin digits with Persian grouping.

**Settings involved.** None stored by the app (the per-app locale is stored by the system).

**Persistence touched.** None.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F28-AC1 | All `values-xx/strings.xml` | `:app:lintDebug` | No `MissingTranslation`, `ExtraTranslation`, `Untranslatable` or `MissingQuantity` issue (all errors) | Gate: lintDebug MissingTranslation |
| F28-AC2 | Each of the 21 locales | Resolve `R.string.app_name` | It equals the default-locale value (no locale overrides it) | `LocalizationTest.appNameUntranslatedInEveryLocale` |
| F28-AC3 | The merged debug resources | Parse `R.xml._generated_res_locale_config` in the debug unit test | Its locale set equals the 21 locales (en-US cs da de el eo es fa fr hu it ko lt nl pl pt ru sl sr tr uk) plus exactly the D-106 pseudo-locales en-XA and ar-XB | `LocalizationTest.localeConfigListsTwentyOneLocales` |
| F28-AC4 | Locales de, ru, fa, cs | Resolve `R.string.settings` | "Einstellungen", "Настройки", "تنظیمات", "Nastavení" (no U+FEFF) | `LocalizationTest.legacySettingsTranslationsKept` |
| F28-AC5 | Locale ru | `moves_count` for 1, 3 and 5 | Three distinct strings | `LocalizationTest.pluralsDistinguishRussianCategories` |
| F28-AC6 | Locale `fa`, `FIX_MID_4` | The Score chip and tile labels render | Their texts match `[0-9KMGTPE]+` (ASCII only) | `LocalizationTest.gameNumeralsAsciiInPersian` |
| F28-AC7 | 24680 | `formatCount` in en, de, fa and ru | "24,680"; "24.680"; fa and ru contain only ASCII digits and exactly the digits 2, 4, 6, 8, 0 in order | `FormattingTest.formatCountLatinDigitsWithLocaleGrouping` |
| F28-AC8 | 0; 401,000; 3,600,000; 360,000,000 ms | `formatElapsed` under the en and fa default locales | "0:00", "6:41", "1:00:00", "100:00:00" in both | `FormattingTest.formatElapsedFormats` |
| F28-AC9 | 9 of 37 games | The win-rate percent in en and fa | Both contain the ASCII digits "24" | `FormattingTest.percentUsesLatinDigits` |
| F28-AC10 | The merged manifest | Robolectric reads `ApplicationInfo.flags` | `FLAG_SUPPORTS_RTL` is set | `ManifestPolicyTest.supportsRtlEnabled` |
| F28-AC11 | `fa`, `FIX_MID_4` | Golden 23 | It matches: mirrored chrome, LTR board | `LocaleScreenshotTest.persianGameGolden` |
| F28-AC12 | `de` | Golden 24 | It matches | `LocaleScreenshotTest.germanSettingsGolden` |
| F28-AC13 | `en-XA`, `ar-XB` | Goldens 25–26 (dropped together with D-106 if the DSL fails) | They match | `LocaleScreenshotTest.pseudoLocaleGoldens` |

### F29 — Backup and device transfer

**User story.** As a player moving to a new phone, I want my games, records and settings to come along through Android's own backup, and nothing else.

**Behavior** (D-048).
1. The manifest sets `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+) and `android:fullBackupContent="@xml/backup_rules"` (API 24–30).
2. `data_extraction_rules.xml` has `<include domain="file" path="datastore/"/>` in both `<cloud-backup>` and `<device-transfer>`.
3. `backup_rules.xml` has the same include inside `<full-backup-content>`.
4. Only `files/datastore/` is included: settings, records and every game. Caches are excluded.
5. The app performs no backup itself. Export/import is v1.x (§6.2).
6. Restored data is validated when read: `lastBoardSize` is coerced, API-gated flags are ignored on lower APIs, and unknown values from a newer version take defaults (F07, F11, F12).

**Edge cases.**
- A backup made by a newer app version restores with defaults for unknown fields.
- The data is small (6 games, each under the 256 KiB budget, plus two small files), far below Auto Backup's 25 MB quota.
- The debug variant is a different package and never shares data with release.

**Settings involved.** All (they are backed up).

**Persistence touched.** All files (read by the OS).

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F29-AC1 | The merged manifest | Robolectric reads `ApplicationInfo` | `FLAG_ALLOW_BACKUP` set; `dataExtractionRulesRes` and `fullBackupContent` point to the two XML resources | `BackupRulesTest.manifestReferencesBothRuleFiles` |
| F29-AC2 | `R.xml.data_extraction_rules` | Parsed | `<cloud-backup>` and `<device-transfer>` each contain exactly `<include domain="file" path="datastore/"/>` | `BackupRulesTest.dataExtractionRulesIncludeDatastore` |
| F29-AC3 | `R.xml.backup_rules` | Parsed | `<full-backup-content>` contains exactly that include | `BackupRulesTest.fullBackupContentIncludesDatastore` |

### F30 — Identity: label, icon, splash, versions and store listing

**User story.** As a player, I want to find the app as "2048" with a clean icon, see it start without a flash of the wrong board, and have every CI build update the previous one in place.

**Behavior.**
1. `applicationId` = `namespace` = `com.clayboicardi.game2048`; launcher label "2048", non-translatable. Debug uses the `.debug` suffix and the label "2048 Debug", so both install side by side (D-110).
2. `versionName = "3.0.0"`, `versionCode = 30000`, both literals (D-130). The app reads them only as the generated `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`, a class that holds AGP's standard fields and nothing else (no `buildConfigField`). No main source calls `PackageManager` for the version, so the startup path (`Application.onCreate` → `AppContainer.create`) makes no binder call for it (D-134, ARCHITECTURE.md §4.2).
3. Adaptive icon (D-111): background `#E6A11A`; foreground = the 4 legacy glyph paths in `#3A2900` (6.33:1), scaled 1.411 and translated 16.667 in a 108-unit viewport; `<monochrome>` = the same paths; a vector fallback for API 24–25.
4. Splash (D-112): `core-splashscreen` 1.2.0, theme `Theme.Game2048.Starting`: background `#FAF8EF` (`#1B1916` in `values-night`), animated icon `@drawable/ic_launcher_foreground`, icon background `#E6A11A`, post-splash theme `Theme.Game2048`. It is held until state loads, at most 1,000 ms (F07).
5. Both build types are signed with the committed, intentionally public CI sideload key, so every CI APK updates the previous one in place. The key is never used for stores (D-131).
6. Release is minified with R8 and `shrinkResources`; the APK is ≤ 3.0 MiB and 16 KB-aligned (D-132, D-148).
7. Store listing (D-113–D-115, D-190): `fastlane/metadata/android/` with exactly 22 locale directories; `title.txt` = "2048" (≤ 30 characters); short description ≤ 80, full ≤ 4000, `changelogs/30000.txt` ≤ 500 characters. The full description states offline, no ads, no tracking, no runtime permissions, sizes 3×3–8×8, unlimited undo, themes and accessibility, MIT with the repository link, credits and the side-by-side note. Every full description contains `com.clayboicardi.game2048` and the repository URL. It never contains "port", "WebView", "long touch" or uberspot store links. Graphics under `en-US/images/` are rendered by `StoreAssetsTest` (opt-in, `-PrecordStoreAssets=true`): each frame is captured with `captureToImage()` and written by the test-only `store/OpaquePngWriter` as an 8-bit PNG, color type 2 for every screenshot and the feature graphic (D-190).
8. The new package is not an update of `com.uberspot.a2048`: both install side by side, and no score is imported (D-005). README and the full description say so.

**Edge cases.**
- Debug and release can be installed together; their data is separate.
- `aapt2` from build-tools 37.0.0 prints `minSdkVersion:'24'`; `check-apk.sh` matches that form (technical correction TC-2).

**Settings involved.** None.

**Persistence touched.** None.

| AC | Given | When | Then | Test |
|---|---|---|---|---|
| F30-AC1 | Both APKs | `aapt2 dump badging` | Release: package `com.clayboicardi.game2048`, versionCode 30000, versionName 3.0.0, label 2048, min SDK 24, target SDK 37. Debug: package `…game2048.debug`, label "2048 Debug" | Gate: check-apk.sh step 1 |
| F30-AC2 | Both APKs | `apksigner verify --print-certs` | The `certificate SHA-256 digest:` line(s) equal `signing/ci-sideload.sha256` (TC-2) | Gate: check-apk.sh step 6 |
| F30-AC3 | `app-release.apk` | Size measured | ≤ 3,145,728 bytes | Gate: check-apk.sh step 4 |
| F30-AC4 | Both APKs | `zipalign -c -P 16 -v 4` | Passes | Gate: check-apk.sh step 5 |
| F30-AC5 | SDK 24 and 37 | `ActivityScenario.launch(MainActivity)` | The activity reaches `RESUMED` with the Game content composed | `LaunchSmokeTest.launchesOnMinAndTargetSdk` |
| F30-AC6 | SDK 36 | `applicationInfo.loadIcon(packageManager)` | An `AdaptiveIconDrawable` whose `monochrome` is non-null | `ManifestPolicyTest.launcherIconIsAdaptiveWithMonochrome` |
| F30-AC7 | `Theme.Game2048.Starting` under `notnight` and `night` | Its attributes are resolved | `windowSplashScreenBackground` `#FAF8EF` / `#1B1916`; `windowSplashScreenIconBackgroundColor` `#E6A11A` | `ManifestPolicyTest.splashThemeUsesIdentityColors` |
| F30-AC8 | `fastlane/metadata/android/` | `scripts/check-metadata.sh` | The D-190 listing assertions pass: exactly D-113's 22 directories; `title.txt` equals `2048` (≤ 30 characters); short ≤ 80, full ≤ 4000, `changelogs/30000.txt` ≤ 500 characters; no BOM or CR and exactly one trailing newline; non-en-US texts differ from en-US; every full description contains `com.clayboicardi.game2048` and the repository URL; native-script letters for ru, uk, sr, el, fa and ko; no non-Latin digits; none of D-114's deny-words (`\bport\b`, `WebView`, `long touch`, case-insensitive); no listing file contains `com.uberspot.a2048` | Gate: check-metadata.sh `check_store_listing` |
| F30-AC9 | `en-US/images/` | `scripts/check-metadata.sh` | Exactly the D-115 image set at its exact pixel sizes; 8-bit PNGs; color type 2 for every screenshot and the feature graphic, 2 or 6 for `icon.png` (D-190) | Gate: check-metadata.sh `check_store_images` |
| F30-AC10 | A `ContextWrapper` over the Robolectric application whose `getPackageManager()` throws `AssertionError` and whose `getApplicationContext()` returns the wrapper | `AppContainer.create(wrapper)` | It returns without throwing; `versionCode == 30000` | `AppContainerTest.createDoesNotTouchPackageManager` |
| F30-AC11 | The debug unit-test classpath | Reflect the non-synthetic fields of `BuildConfig` | Exactly `DEBUG`, `APPLICATION_ID`, `BUILD_TYPE`, `VERSION_CODE`, `VERSION_NAME`; `VERSION_CODE == 30000`; `VERSION_NAME == "3.0.0"` | `AppContainerTest.versionComesFromBuildConfigStandardFields` |

---

## 2. Settings catalog

All stored settings live in **one** document, `settings.json` (`files/datastore/settings.json`, D-040), class `data/settings/Settings.kt` (ARCHITECTURE.md §4.3). The Kotlin data class, the D-042 field table and this catalog list the same 14 keys; `SettingsRepositoryTest.defaultsMatchCatalog` (F11-AC10) keeps them equal.

### 2.1 `settings.json` fields

| # | Key | Type (JSON) | Default | Allowed values | UI row (D-006 order) | Read by | Notes and migration |
|---|---|---|---|---|---|---|---|
| 1 | `schemaVersion` | `Int` (number) | `1` | `1` in v1.0 | — | Serializer; any future `DataMigration` | Bumped only for a breaking change (D-041) |
| 2 | `themeMode` | `ThemeMode` (string) | `SYSTEM` | `SYSTEM`, `LIGHT`, `DARK` | 1 Theme (choice) | F12 (`GameTheme`, system-bar icons) | Successor of the legacy night-mode button, which was never persisted (S18, B10). Nothing to migrate |
| 3 | `palette` | `PaletteId` (string) | `CLASSIC` | `CLASSIC`, `NEON` | 2 Palette (choice with swatches) | F12, F02 (mini-board glyph), F03 (badge) | Future palettes add enum names; an older version reads an unknown name as `CLASSIC` (§2.4) |
| 4 | `highContrast` | `Boolean` | `false` | | 3 High-contrast tiles (switch) | F12, F17 | Effective high contrast = setting **or** system contrast ≥ 0.5 (API 34+). The system lock never rewrites this value |
| 5 | `dynamicChrome` | `Boolean` | `false` | | 4 Dynamic color (switch; API 31+ only) | F12 | Ignored below API 31 even when `true` (for example after a restore) |
| 6 | `motion` | `MotionPreference` (string) | `FULL` | `FULL`, `REDUCED`, `OFF` | 5 Motion (choice) | F13, F03 and F04 (card timing), F09, F14 (haptic timing), F21 (transitions), F22 (hint exit) | The effective mode is `OFF` while the animator scale is 0; that is not stored |
| 7 | `haptics` | `Boolean` | `true` | | 6 Haptics (switch) | F14 | The system touch-feedback setting can still silence haptics |
| 8 | `moveTiming` | `MoveTiming` (string) | `DURING_SWIPE` | `DURING_SWIPE` ("As you swipe"), `ON_RELEASE` ("When you lift your finger") | 7 Move timing (choice) | F15 | DECISIONS OI-1 (settled): `DURING_SWIPE` stays the default (O3), with `ON_RELEASE` as the option |
| 9 | `swipeSensitivity` | `SwipeSensitivity` (string) | `MEDIUM` | `LOW` (40 dp), `MEDIUM` (24 dp), `HIGH` (12 dp) | 8 Swipe sensitivity (choice) | F15 | The enum **name** is stored; the dp values live in code (`SwipeSensitivity.thresholdDp`). Changing a dp value later needs no migration but changes behavior for existing players, so it must appear in What's new |
| 10 | `immersive` | `Boolean` | `false` | | 9 Immersive mode (switch) | F19 | Successor of the legacy `is_fullscreen_pref` (inverted naming, default "bars shown"); not migrated (D-005, S4) |
| 11 | `keepScreenOn` | `Boolean` | `false` | | 10 Keep screen on while playing (switch) | F20 | |
| 12 | `lastBoardSize` | `Int` | `4` | 3..8; anything else reads as 4 | none (written by the size sheet) | F02, F07 (start load), F08 (initial Statistics tab), F22 (hint target) | Written on every size switch |
| 13 | `lastSeenVersionCode` | `Int` | `0` | ≥ 0; 0 means fresh install | none | F24 | Written when What's new is evaluated on a fresh install or an update; never lowered |
| 14 | `onboardingDone` | `Boolean` | `false` | | none | F22 | Set by the first accepted move or "Got it" |

Row 11 of D-006, **App language**, is not stored by the app (§2.2).

### 2.2 Inputs that are not stored settings

| Input | Source | Observation | Effect | Features |
|---|---|---|---|---|
| App language | System per-app locale (API 33+), reached through `ACTION_APP_LOCALE_SETTINGS`; the system language otherwise | Activity recreation | Resource locale | F11, F28 |
| System dark theme | `isSystemInDarkTheme()` (`uiMode` night) | Recreation | `themeMode = SYSTEM` | F12 |
| Animator duration scale | `Settings.Global.ANIMATOR_DURATION_SCALE` with a `ContentObserver` | Live | 0 forces effective Off and locks the Motion row; other values scale durations and delays | F11, F13 |
| System contrast | `UiModeManager.getContrast()` with `addContrastChangeListener` (API 34+) | Live | ≥ 0.5 forces high contrast and locks its row | F11, F12 |
| Touch exploration | `AccessibilityManager.isTouchExplorationEnabled` with a listener | Live | Direction pad and status line; the controls-bar Undo is removed | F17 |
| System touch feedback | Platform setting | Honored by `performHapticFeedback` | Haptics may be silent although On | F14 |
| Wallpaper colors | `dynamicLightColorScheme`/`dynamicDarkColorScheme` (API 31+) | On theme composition | Chrome colors when `dynamicChrome` is On | F12 |
| Window size | `currentWindowAdaptiveInfoV2()` | Live | Layout mode | F18 |
| Font scale | Configuration | Recreation | Chrome text size; tile numerals ignore it | F17 |
| System gesture insets | `WindowInsets.systemGestures` | Live | Board side margin | F15 |
| API level | `Build.VERSION.SDK_INT` | Static | Row visibility (31, 33) and system contrast (34) | F11, F12 |

### 2.3 Not settings in v1.0

| Candidate | Status | Reason |
|---|---|---|
| New-game confirmation toggle | Never | Confirmation is unconditional while in progress (O12, D-007) |
| Undo on/off (purist mode) | v1.x (§6.2) | D-003 defers it; `bestScoreNoUndo` already gives honest scoring |
| Animation speed multiplier | Never | Replaced by Motion; it would quadruple the test matrix (D-006, research/07 C14) |
| Haptic intensity (Off/Subtle/Full) | Never | O5 fixes On/Off (D-065) |
| Sound | v1.x (§6.2) | No sounds ship in v1.0 |
| Moves/time HUD | v1.x (§6.2) | D-006 |
| TalkBack verbosity | Deferred | D-202 completes the single status-line level (spawn, undo, new game, size switch); a Brief/Detailed setting stays deferred |
| In-app language picker | Never on API < 33 | Needs AppCompat (D-102) |
| Reset statistics | Not a setting | An action in Statistics (D-006, F08) |

### 2.4 Migration and compatibility rules

1. v1.0 writes `schemaVersion = 1`, the first schema of this package. No legacy value is read: `is_fullscreen_pref`, the WebView localStorage keys and the night-mode inline style have no successor value (D-005).
2. **Adding** a setting: add a field with a default. No `schemaVersion` bump. Older versions ignore the key (`ignoreUnknownKeys`).
3. **Adding an enum value**: older versions decode the unknown name as the field default (`coerceInputValues`), so downgrades stay safe.
4. **Renaming, removing or re-typing** a field, or changing what a stored value means: bump `schemaVersion`, add a `DataMigration<Settings>` with `shouldMigrate = schemaVersion < N`, and keep the never-edited v1 fixture decoding (`PersistenceFixturesTest`, D-041).
5. Values are validated when read and never trusted: `lastBoardSize` outside 3..8 becomes 4; API-gated flags are ignored below their API level.
6. Corruption yields `Settings()` (D-045). That also resets `lastSeenVersionCode` and `onboardingDone`, which only suppresses the What's new sheet; the hint stays hidden unless the starting game has no move yet (F22, D-201).
7. Settings are backed up with the other DataStore files (F29), so preferences move to a new device.

---

## 3. Statistics catalog

Statistics are kept **per board size** in `records.json` (`Records.sizes`, keys `"3"` … `"8"`, D-042). A missing key reads as an all-zero `SizeRecord`. Any other key is preserved on write but ignored by v1.0 (no tab, not aggregated), so a later version can add mode-qualified buckets such as `"daily:4"` without older versions folding them into classic statistics (§6.1 H8, F08-AC7). All arithmetic lives in the pure `RecordsMath` (ARCHITECTURE.md §4.9): `progress` on every accepted move, `finalize` when New game starts on that size, and in its load-time recovery (idempotent per `gameId`, D-044; and, without persisting, for the display-time fold through `displayed`, D-198), `reset` for Reset statistics, `aggregate` for the "All sizes" tab.

### 3.1 Stored per-size fields (`SizeRecord`)

| Field | Type | Definition | Updated | Undo interaction | Shown in v1.0 | Display format | "All sizes" |
|---|---|---|---|---|---|---|---|
| `bestScore` | `Long` | Highest score reached by any game on this size, including the current one | Live in `progress` when `score > bestScore` | Never lowered: a score reached and later undone still counts | Stats hero "Best score"; header Best chip as `max(bestScore, score)`; size sheet "Best …"; game-over card "Best" | Stats and sheet: `formatCount`. Chip and card: game numeral | max |
| `bestScoreNoUndo` | `Long` | Highest score reached by a game while that game's `undoCount == 0` | Live in `progress`, only while `undoCount == 0` | The game's first undo freezes its contribution; the value already reached stays | Stats "Best score without undo" | `formatCount` | max |
| `bestTileExp` | `Int` | Largest exponent ever on a board of this size | Live in `progress` | Never lowered | Stats hero `MiniTile`, a11y `a11y_best_tile` with the full value | Tile with its label; full decimal in a11y | max |
| `gamesPlayed` | `Int` | Finalized games: `OVER`, or abandoned through New game with `score > 0` | `finalize` | Counts the final state | Stats "Games played" | `formatCount` | sum |
| `gamesWon` | `Int` | Finalized games whose final board holds the size's target (`maxExp ≥ targetExp`); a `KEEP_PLAYING` game abandoned through New game counts as won (D-198) | `finalize` | Judged on the final board, after undos (D-198) | Stats "Wins" with the rate | `"%1$s (%2$s)"`: `formatCount` and the percent | sum |
| `gamesAbandoned` | `Int` | Finalized games that were not `OVER` | `finalize` | — | No (v1.x) | — | sum |
| `totalScore` | `Long` | Σ final scores of finalized games | `finalize` | Final score after undos | Through "Average score" | — | sum |
| `totalMoves` | `Long` | Σ final `moveCount`: accepted moves still in the log; undone moves are excluded | `finalize` | Undone moves do not count | Stats "Total moves" | `formatCount` | sum |
| `totalTimeMs` | `Long` | Σ `elapsedMs` of finalized games (play time per F07) | `finalize` | Undo never rewinds time | Stats "Time played" | `formatElapsed` | sum |
| `totalUndos` | `Long` | Σ `undoCount` of finalized games | `finalize` | Every undo counts | Stats "Undos used" | `formatCount` | sum |
| `tileReach` | `List<Int>` | Index k = number of finalized games whose final max exponent is ≥ k, for k ≥ 1. Index 0 is unused (always 0). The list grows to the largest exponent seen + 1 | `finalize` | Final board | No (v1.x histogram) | — | Element-wise sum |
| `currentWinStreak` | `Int` | Consecutive wins among this size's finalized games, ending at the latest one; a finalized non-win resets it to 0 (D-198) | `finalize` | — | No (v1.x) | — | Not aggregated |
| `longestWinStreak` | `Int` | Maximum `currentWinStreak` ever reached | `finalize` | — | No (v1.x) | — | Not aggregated |

Beside `sizes`, `records.json` holds `lastFinalizedGameId: Map<String, Long>` (same keys), the highest `gameId` finalized on each size (D-044). It is bookkeeping, not a statistic: it is never displayed, never aggregated, never part of the empty-state test, and Reset statistics keeps it.

### 3.2 Derived statistics

| Statistic | Formula | Format | Zero case |
|---|---|---|---|
| Win rate | `gamesWon / gamesPlayed` | `NumberFormat.getPercentInstance(latnLocale)`, 0 fraction digits, JDK default rounding (HALF_EVEN) | "0%" (the tab is in its empty state anyway) |
| Average score | `totalScore / gamesPlayed` (integer division, floor) | `formatCount` | "–" (U+2013) |
| All sizes: counts and totals | Σ over sizes | as above | — |
| All sizes: bests and best tile | max over sizes | as above | — |
| All sizes: win rate and average | From the sums (each size counts wins against its own target) | as above | — |
| Empty state | Every field of the tab's record is 0 ("All sizes": every size) | DESIGN_SYSTEM.md §6.9 | — |

Worked values for `FIX_RECORDS` are in F08-AC9 and F08-AC10.

### 3.3 Per-game statistics (`game-N.json`)

| Field | Definition | Shown in v1.0 |
|---|---|---|
| `score` | The current score | Header Score chip (game numeral, odometer), cards, share |
| `moveCount` | Accepted moves currently in the log | Game-over card ("{n} moves"), share |
| `undoCount` | Undos taken in this game; never decremented | Game-over card ("{n} undos" / "No undo"), share; its 0 → 1 step shows the "Undo used" snackbar until the first finalized game that used undo (D-186) |
| `elapsedMs` | Play time (F07 rule) | Game-over card, share |
| Board max exponent | Derived from `cells` | Game-over card "Best tile", share "best tile" |
| `bestAtStart` | `bestScore` when the game began | Not shown; drives F09 |
| `newBestAnnounced` | Whether the new-best moment fired | NEW BEST chip state |
| `status`, `keepGoingAtMove` | D-036 | Cards |
| `gameId` | Finalization identity: ≥ 1, strictly increasing per size (D-044) | Not shown |
| `previousFinalization` | Recovery entry: the replaced game's `gameId`, `status`, `score`, `moveCount`, `elapsedMs`, `undoCount` and `maxExp`, present only when that game was counted (D-044) | Not shown; counted at load if the records lack it |

A live moves/time HUD is v1.x (§6.2).

### 3.4 When each document is written

| Event | `game-N.json` | `records.json` (progress) | `records.json` (finalize / reset) | `settings.json` |
|---|---|---|---|---|
| Accepted move | Write | Only if a best increased | — | `onboardingDone` if the hint is visible |
| Invalid or blocked move | — | — | — | — |
| Undo | Write | — | — | — |
| Keep going | Write | — | — | — |
| New game | Write (fresh game, with `previousFinalization` when the old game was counted) | — | `finalize` if the old game is `OVER` or `score > 0` (sets `lastFinalizedGameId`; no write when already finalized) | — |
| Game load (start size, size switch) | Write a fresh game only when R2 completes an interrupted New game | — | R1: `finalize` of a `previousFinalization` not yet counted (D-044) | — |
| Size switch | Write the old size; write the new size if a fresh game is created | — | — | `lastBoardSize` |
| `ON_PAUSE` / leaving Game | Write (elapsed folded) | — | — | — |
| Reset statistics | — | — | `RecordsMath.reset`: `sizes` empty, `lastFinalizedGameId` kept | — |
| A settings change | — | — | — | Write |
| What's new evaluation | — | — | — | `lastSeenVersionCode` (fresh install or update) |

### 3.5 Undo and the statistics

1. `bestScore` and `bestTileExp` include values reached on lines that were later undone; reaching them is a fact, and D-044 forbids lowering a best.
2. `bestScoreNoUndo` counts only what a game reached before its first undo.
3. Finalization uses the final state: final score, final `moveCount` (undone moves excluded), final board (for wins and `tileReach`), the full `undoCount`, and `elapsedMs` (undo never rewinds time).
4. The new-best moment fires at most once per game, even if an undo moves the score back below `bestAtStart` (F09).
5. A game that ended `OVER` is persisted into the records only when New game starts on its size; Statistics displays it earlier through the display-time fold, and an undo before New game simply continues the game and removes it from the display, so nothing is double-counted (D-044 rationale, D-198). The fold skips a game whose `gameId` is already finalized, so a game restored after its records write is not shown twice (D-044).

---

## 4. Legacy parity matrix

Every row of research/01-legacy-android-shell.md §9 (shell, S1–S29) and research/02-legacy-game.md §9 (game, G1–G33) appears once, with the disposition of DECISIONS.md §1. "Proof" names the acceptance criteria whose tests demonstrate the v1.0 behavior. "M0 exit check" is ARCHITECTURE.md §1.3: after M0, `git submodule status` prints nothing and `git ls-files | grep -E '^(src|res|metadata|assets)/'` prints nothing.

### 4.1 Shell (research/01 §9)

| # | Legacy behavior | v1.0 behavior | Feature | Proof |
|---|---|---|---|---|
| S1 | Launches straight into the game; no title or action bar | `GameKey` is the start destination; Game has no top app bar; other screens via the menu | F21 | F21-AC1 |
| S2 | `FLAG_HARDWARE_ACCELERATED` set by hand | Dropped (default) | — | M0 exit check |
| S3 | Long press > 2000 ms toggles fullscreen | Settings switch "Immersive mode"; no gesture | F19 | F19-AC2, F19-AC6 |
| S4 | Persisted `is_fullscreen_pref` (inverted naming, default "bars shown") | `immersive = false` in `settings.json`; no migration | F11, F19 | F11-AC10 |
| S5 | Toast "Long click to toggle fullscreen" on every `onCreate` | No toast ever; a one-time first-launch hint | F22 | F22-AC7, F22-AC1 |
| S6 | Double-back-to-exit (3500 ms) | One Back on the Game root goes home (predictive back) | F21 | F21-AC3 |
| S7 | Manifest `screenOrientation="portrait"` | No lock; adaptive layouts | F18 | F18-AC6, F18-AC1 |
| S8 | Sensor rotation on large/xlarge screens with auto-rotate on | No orientation request; the system rotation honors the user's lock | F18 | F18-AC6, F18-AC5 |
| S9 | `configChanges="orientation\|keyboardHidden\|screenSize"` | Normal recreation; state from ViewModel and DataStore | F07, F18 | F18-AC6, F07-AC7 |
| S10 | ckChangeLog dialog on first run and on upgrade | What's new sheet only after an update, never on a fresh install | F24 | F24-AC1, F24-AC2 |
| S11 | `changelog_master.xml` history | About → Legacy history (9 releases) | F25 | F25-AC7 |
| S12 | WebView host with JS, DOM storage and WebSQL | Native Kotlin/Compose; no WebView | — | M0 exit check |
| S13 | `setRenderPriority` / `setDatabasePath` | Dropped | — | M0 exit check |
| S14 | `WebView.saveState` / `restoreState` | DataStore for game state; Nav3 back stack and `rememberSaveable` for transient UI | F07 | F07-AC1, F07-AC9 |
| S15 | `onResume()` reload of `index.html` | Never reload or replay | F07 | F07-AC8 |
| S16 | Locale passed via `?lang=` | Android resources + generated `localeConfig` | F28 | F28-AC3 |
| S17 | localStorage `bestScore` / `gameState` / `gameStatesStack` | Typed DataStore documents; no import (D-005) | F07 | F07-AC14 |
| S18 | Night-mode button (not persisted) | Persisted Theme: System / Light / Dark | F12 | F12-AC1, F11-AC11 |
| S19 | Shell strings × 20 locales | `settings` kept with its translations; `app_name` non-translatable; hint strings dropped | F28 | F28-AC4, F28-AC2 |
| S20 | Web i18n (en/ru/uk) and CSS "Score"/"Best" | All strings in `strings.xml` for 21 locales | F28 | F28-AC1 |
| S21 | Non-adaptive `ic_2048` vector | Adaptive + monochrome icon reusing the glyph paths | F30 | F30-AC6 |
| S22 | `allowBackup="true"` with no rules | Explicit rules including `datastore/` | F29 | F29-AC1, F29-AC2 |
| S23 | Zero `<uses-permission>` | Only androidx.core's signature self-permission, gated | F27 | F27-AC1 |
| S24 | `android:Theme.Light` (API 1) | Material 3 (`MaterialExpressiveTheme`) | F12 | F12-AC11 |
| S25 | Unused dimens, including the `sw720dp-land` 128 dp margin | Dropped; wide gutters survive only as the side-panel layout | F18 | M0 exit check, F18-AC4 |
| S26 | Travis CI + Gradle 4.10.3 + AGP 3.2.1 | GitHub Actions running `scripts/verify.sh`; Gradle 9.7.1; AGP 9.4.1 | — | `ci.yml` green on the PR (D-151) |
| S27 | Submodule `assets/2048` | Removed in M0 | — | M0 exit check; Gate: check-metadata.sh `check_no_submodule` (`.gitmodules` absent; D-152, D-179) |
| S28 | `metadata/<locale>/` listing | `fastlane/metadata/android/<22 locales>/` | F30 | F30-AC8 |
| S29 | MIT LICENSE (Sarbinowski) + game MIT (Cirulli) | LICENSE with three lines, NOTICE, in-app licenses | F26 | F26-AC5, F26-AC3 |

### 4.2 Game (research/02 §9)

| # | Legacy behavior | v1.0 behavior | Feature | Proof |
|---|---|---|---|---|
| G1 | 4×4 grid, 2 start tiles | Sizes 3×3–8×8, default 4×4, 2 start tiles | F01, F02 | F01-AC13, F02-AC4 |
| G2 | Spawn 2 (90 %) / 4 (10 %) in a uniform empty cell, only after an effective move | Same rule and draw order with SplitMix64 | F01 | F01-AC15, F01-AC4 |
| G3 | Leading-edge traversal; one merge per tile per move | Same; legacy rows are goldens | F01 | F01-AC1, F01-AC3 |
| G4 | Score = sum of merged values | Same | F01 | F01-AC5 |
| G5 | Win at the first exact 2048; overlay; Keep going; win + game over edge case | Same, with a per-size target (3×3 = 256) | F03 | F03-AC1, F03-AC3, F02-AC2 |
| G6 | Input locked until Keep going | Modal win card whose buttons work from the end of its entry; Undo available | F03 | F03-AC11, F03-AC15 |
| G7 | No-op move: no spawn, save or history | Same, plus the nudge and a rate-limited `Reject` | F01 | F01-AC4, F01-AC21 |
| G8 | Game over = no empty cell and no equal neighbour | Same, evaluated after the spawn | F01 | F01-AC10, F01-AC17 |
| G9 | Best score persists forever and never decreases | Per size; only Reset statistics clears it | F08 | F08-AC4, F08-AC14 |
| G10 | Auto-save on every move; resume on launch | Same, with the full undo history | F07 | F07-AC1, F07-AC2 |
| G11 | Save wiped on game over | Final board persisted; undo works from game over | F04 | F04-AC10, F04-AC9 |
| G12 | Undo capped at 50 levels | Unlimited | F05 | F05-AC6 |
| G13 | Restart confirmation dialog | Shown only when in progress; Cancel changes nothing | F06 | F06-AC1, F06-AC2 |
| G14 | Keep going / Try again overlay buttons | Win card (Keep going / New game / Undo) and game-over card (New game / Undo / Share) | F03, F04 | F03-AC8, F04-AC2 |
| G15 | Tile palette 2 → 2048 hexes | Classic Refined: same identity, fixed contrast | F12 | F12-AC4 |
| G16 | One `#3c3a32` above 2048 | Distinct super-tile ramp | F12 | F12-AC3 |
| G17 | Gold glow on 128–2048 | Refined glow (0.8 × legacy alpha) | F12 | F12-AC10 |
| G18 | Fixed 500/280 px board, 15/10 px gap | Responsive N×N geometry | F02, F18 | F02-AC8, F18-AC2 |
| G19 | Clear Sans webfont | Google Sans Flex digit subset + system font | F28 | F28-AC11; Gate: check-metadata.sh `check_font` (font ≤ 32 KiB, D-152) |
| G20 | Font-size ladder 55/45/35/30 (mobile 35/25/15/10) | Digit-count sizing in dp plus compact labels | F02 | F02-AC10, F02-AC9 |
| G21 | 100 ms slide, 200 ms appear/pop, 600 ms "+N", overlay after 1200 ms | Spring tokens; ~400 ms settle; cards interactive at 700–800 ms after T0 | F13 | F13-AC5, F13-AC8 |
| G22 | Appear replay on resume or undo; "+whole score" on load | Dropped: restores and undos are `Static`, no delta on load | F07 | F07-AC7, F07-AC10 |
| G23 | Arrow / WASD / HJKL keys; R restarts immediately | Plus D-pad, U / Ctrl+Z / Backspace, N/R with confirmation when in progress | F16 | F16-AC1, F16-AC3 |
| G24 | IE10 `MSPointer` branch and polyfills | Dropped | — | M0 exit check |
| G25 | Swipe on `.container`, > 10 CSS px, fires on lift | dp threshold (24 dp); commit during the drag by default; "On release" option | F15 | F15-AC3, F15-AC4 |
| G26 | Long-press fullscreen (shell side) | See S3 | F19 | F19-AC6 |
| G27 | Double-back toast (shell side) | See S6 | F21 | F21-AC3 |
| G28 | Night-mode button | See S18 | F12 | F12-AC1 |
| G29 | i18n en/ru/uk with 11 keys; Score/Best not localized | `strings.xml` in 21 locales | F28 | F28-AC1 |
| G30 | `values-fa` `app_name` = "۲۰۴۸" | "2048" everywhere | F28 | F28-AC2 |
| G31 | localStorage schema | See S17 | F07 | F07-AC14 |
| G32 | MIT attribution to Gabriele Cirulli 2014 | LICENSE, NOTICE, About credits, Licenses | F26, F25 | F26-AC5, F25-AC6 |
| G33 | No ads or analytics; zero permissions | Same, gated in CI | F27 | F27-AC1, F27-AC3 |

### 4.3 Legacy release lineage (research/01 §5.2)

research/01 §5.2 lists the feature lineage the native app must at least match.

| Legacy feature | v1.0 | Proof |
|---|---|---|
| Undo (bounded history, 2.0 / 2.06) | Unlimited, persisted undo | F05-AC6, F05-AC15 |
| Night mode (1.95) | Theme System / Light / Dark, persisted | F12-AC1 |
| System-language localization (2.1/2.2) | Resources in 21 locales plus per-app language | F28-AC3 |
| Multi-window ("floating windows", 2.08) | No orientation lock; recreation restores `Static`; adaptive modes | F18-AC5, F18-AC1 |

---

## 5. Legacy bug-fix list

Each confirmed legacy defect, the v1.0 fix, and the acceptance criterion that proves it.

### 5.1 Game bugs (research/02 §8, B1–B21)

| Bug | Legacy behavior (evidence) | v1.0 fix | Proof (Given / When / Then) | AC |
|---|---|---|---|---|
| B1 | Undo after "undo, then move" skips a state (`local_storage_manager.js:68-91`) | The log is truncated on undo; reconstruction from snapshots | Given S0 → m1 → m2 → m3, When undo, m4, undo, Then the state is S2 | F05-AC7 |
| B2 | Undo is not persisted; a resume reverts it | Every undo is saved | Given 10 moves and 1 undo, When the process restarts, Then `moveCount == 9` and `undoCount == 1` | F05-AC15 |
| B3 | The first undo after a reload does nothing | Restoring appends nothing to history | Given a restart after 10 moves, When undo, Then `moveCount == 9` | F05-AC16 |
| B4 | Undo after game over soft-locks the board | Undo from `OVER` restores a playable state | Given `OVER`, When undo, Then status `PLAYING`/`KEEP_PLAYING`, the card closes and a swipe applies | F04-AC8, F04-AC9 |
| B5 | "Cancel" sets `keepPlaying`, so the win never shows | Cancel is a pure dismissal | Given a confirmation, When Cancel, Then nothing changes; a later 2048 still shows the win card | F06-AC2, F06-AC6 |
| B6 | `keepPlaying` field shadows the method | Not applicable: no JS. `Game.keepGoing` is a plain function with no same-named state | Given `WON`, When `keepGoing`, Then `KEEP_PLAYING` | F03-AC4 |
| B7 | "+whole score" float on every load | No delta on load | Given a saved game, When it loads, Then `lastDelta == 0` | F07-AC10 |
| B8 | Appear animation replays on load and undo | Restores and undos render `Static` | Given a restore or an undo, Then no `Spawned` tile | F07-AC11, F05-AC9 |
| B9 | The game-over board is lost on resume | `OVER` is persisted and its card restored | Given `OVER`, When the process restarts, Then the board and card return | F04-AC10 |
| B10 | Night mode broken, partial and not persisted | Persisted Theme with full dark palettes | Given Theme Dark, When the app restarts, Then it is still dark, board and tiles included | F11-AC11, F12-AC1, F12-AC11 |
| B11 | A swipe starting on a button activates the button | A drag that starts on a control is consumed as a swipe | Given a drag starting on New game, Then the board moves and no dialog opens | F15-AC8 |
| B12 | R and "Try again" restart without confirmation | N/R confirm while in progress; cards act at once because they are the decision point | Given a game in progress, When N or R, Then the dialog | F16-AC3, F06-AC5 |
| B13 | 10 px threshold, fires only on lift, diagonals resolve vertical | 24 dp default, commit during the drag, ratio 1.4, diagonals ignored | Given a 24 dp drag, Then a move before lift; a 1:1 diagonal does nothing | F15-AC1, F15-AC3 |
| B14 | The long-press toggle collides with slow swipes | No long-press gesture | Given a 3 s press, Then nothing toggles | F19-AC6 |
| B15 | 10–15 px super-tile text; one super color | dp sizing by digit count, compact labels, distinct ramp | Given 1–7 character labels, Then sizes per D-077; super tiles per D-072 | F02-AC10, F12-AC3 |
| B16 | White-on-gold contrast 1.4–1.7:1 | Contrast policy D-083 | Given every palette, Then ink ≥ 3.0 / 4.5:1 | F12-AC4 |
| B17 | "Score"/"Best" not localized | String resources in 21 locales | Lint finds no missing translation | F28-AC1 |
| B18 | Fixed 280 px board on phones | Responsive board | Given 360×800 dp, Then the board is 328 dp | F18-AC2 |
| B19 | Overlay corners bleed past the board radius | The scrim is clipped to the board shape | Goldens of both cards | F03-AC17, F04-AC13 |
| B20 | O(stack) JSON parse and stringify on every move | Compact Base64 documents with budgets | Given 10,000 moves on 8×8, Then ≤ 256 KiB and ≤ 50 ms | F07-AC18 |
| B21 | `nightmode.js` clobbers `window.onload` | Not applicable: no JS ships | — | M0 exit check |

### 5.2 Shell defects (research/01 §7, D1–D32)

| # | Defect | v1.0 fix | Proof |
|---|---|---|---|
| D1 | Gradle 4.10.3 cannot run on JDK 21 | Wrapper 9.7.1 (D-010) | `scripts/verify.sh` on JDK 21, locally and in CI |
| D2 | AGP 3.2.1 incompatible | AGP 9.4.1 (D-011) | `scripts/verify.sh` |
| D3 | `ckchangelog` SNAPSHOT is unresolvable | Dependency removed; native What's new | F24-AC1, F24-AC2 |
| D4 | `jcenter()` | `google()` + `mavenCentral()`, `FAIL_ON_PROJECT_REPOS` (D-015) | `scripts/verify.sh` resolves |
| D5 | Travis is dead | `ci.yml` (D-151) | CI run on the PR |
| D6 | compile/target 28 | 37 / 37 (D-013) | F30-AC1 |
| D7 | minSdk 14 | 24 (D-013) | F30-AC1 |
| D8 | No `android:exported` on the launcher | `exported="true"` on `MainActivity` only | F27-AC2, F30-AC5 |
| D9 | `onBackPressed` not called at target 36+ | Predictive back; Back never intercepted on the root | F21-AC3 |
| D10 | `onResume` full reload | No reload | F07-AC8 |
| D11 | One-shot rotation read, sensor override, ignored locks | No orientation request; `appCategory="game"` | F18-AC6, F18-AC7 |
| D12 | `FLAG_FULLSCREEN`, inverted naming, layout jump | Edge-to-edge + `WindowInsetsControllerCompat` immersive setting | F19-AC1, F19-AC2 |
| D13 | Long press conflicts with swipes; no a11y path | Gesture removed; Settings switch | F19-AC6 |
| D14 | `allowBackup` with no rules | Explicit rules | F29-AC2 |
| D15 | Store assets non-compliant (alpha, 405 px) | Rendered from code; opaque 8-bit PNGs through `OpaquePngWriter` (D-190) | F30-AC9 |
| D16 | Deprecated WebView APIs | WebView removed | M0 exit check |
| D17 | Deprecated `PreferenceManager` | DataStore | F11-AC11 |
| D18 | `onSaveInstanceState` without `super` | Compose state + `rememberSaveable` | F07-AC9 |
| D19 | Redundant hardware-acceleration flag | Removed | M0 exit check |
| D20 | Hint toast on every `onCreate` | No toast | F22-AC7 |
| D21 | Back toast shorter than its window | Feature removed | F21-AC3 |
| D22 | Partial `configChanges` | None; recreation restores `Static` | F18-AC6 |
| D23 | No `supportsRtl` although `fa` ships | `supportsRtl="true"` with a forced-LTR board | F28-AC10, F15-AC10 |
| D24 | `android:Theme.Light`, no dark theme | M3 light and dark | F12-AC11 |
| D25 | Eclipse source sets; missing ProGuard file; R8 off | Standard layout; R8 + `shrinkResources` in release (D-132) | `scripts/verify.sh` `:app:assembleRelease` produces `app/build/outputs/mapping/release/mapping.txt`; F30-AC3 |
| D26 | Unused resources | Lint `UnusedResources` is fatal under `warningsAsErrors` | Gate: lintDebug UnusedResources |
| D27 | BOM, typos, Persian-digit label | Strings migrated per D-101; `check_no_bom` keeps the BOM out (D-179) | F28-AC4, F28-AC2 |
| D28 | Duplicate versionCode 18; English-only changelog | Legacy history shown as data; literal 3.0.0/30000 | F25-AC7, F30-AC1 |
| D29 | Machine-translated listing ("harbour", "robot") | 16 listing-only locales deleted; texts rewritten | F30-AC8 |
| D30 | Listing says "3+ seconds" (code: 2 s) | "long touch" removed from the listing | F30-AC8 |
| D31 | Submodule, foreign store badges, Eclipse docs | Submodule removed; README rewritten (D-116) | M0 exit check; Gate: check-metadata.sh `check_no_submodule` (`.gitmodules` absent) |
| D32 | Non-adaptive icon, 3.37:1 on white | Adaptive + monochrome icon, 6.33:1 | F30-AC6 |

### 5.3 Other legacy behaviors replaced (research/05-features.md §2)

| Legacy behavior | v1.0 | Proof |
|---|---|---|
| Every undo asks "Undo the current move?" | No confirmation | F05-AC13 |
| In-memory `fakeStorage` fallback that silently loses all state | Per-file DataStore with per-file corruption fallback | F07-AC16, F02-AC12 |
| Undo stack actually holds 51 entries | Unlimited | F05-AC6 |
| Swipe area limited to the 280 px column | The whole Game content area | F15-AC12 |
| Diagonal swipes resolve vertical | Ignored | F15-AC1 |

---

## 6. Roadmap beyond v1.0

Tiers follow the D-003 scope table: **v1.x** = the next increments, **backlog** = later, **never** = §7. Effort uses research/05-features.md's scale (S ≤ 1 day, M 2–5 days, L > 1 week). Every item below is outside the execution PR (D-004) and needs its own decision under D-173 before it is built.

The governing design rule: v1.0 already stores what these features need, so none of them requires a breaking migration. Where an item needs a new field, the field is **additive with a default**, which D-041 handles with no `schemaVersion` bump and no `DataMigration`. The one exception (timed spawns in the backlog) says so explicitly.

### 6.1 Data-model hooks shipped in v1.0

| Hook | Exact v1.0 field or API | Enables |
|---|---|---|
| H1 | `GameDocument.seed` (`Long`) + `moveLog` (Base64 2-bit log, D-039) + `keepGoingAtMove`; engine `Game.newGame` / `Game.move` / `Game.keepGoing` are deterministic (ARCHITECTURE.md §3.2) | Exact replay of the surviving line of any game: history, replay viewer, shared game files |
| H2 | App-owned `SplitMix64` with golden vectors (D-038); the frozen draw order of D-034; `Game.newGame(size, seed)` | Daily challenges: one seed → the identical board on every device and version |
| H3 | `rngState` restored on undo (D-039) | Redo needs only the undone directions; the rewind animation can recompute a move |
| H4 | `GameDocument.undoCount`; `SizeRecord.bestScoreNoUndo`, `totalUndos` | Purist mode (Undo Off), no-undo achievements |
| H5 | `SizeRecord.tileReach`, `gamesAbandoned`, `currentWinStreak`, `longestWinStreak`, stored from the first release | v1.x statistics with complete history back to v1.0; achievement backfill |
| H6 | `GameDocument.elapsedMs` (monotonic); `PlayClock` | Moves/time HUD; time-based records |
| H7 | `schemaVersion` + lenient `Json` + defaults on every field + never-edited v1 fixtures (D-041) | Every additive field below; enum additions are downgrade-safe |
| H8 | `Records.sizes: Map<String, SizeRecord>` keyed by string | Mode-qualified buckets such as `"daily:4"` without changing the type |
| H9 | `PaletteId` stored by name; `TilePalette` interface; `PaletteContrastTest` gates | Extra palettes; a Material You tile ramp |
| H10 | `LayoutMode` from `rememberLayoutMode()` (`ui/components/WindowMode.kt`) | Posture modes |
| H11 | `HapticsMap` → one `MoveHaptic` per move by priority | Sound effects reuse the same event resolution |
| H12 | Pure `ShareText`; `GameSummary.cells` | Daily share text; image share card |
| H13 | `Snapshot` binary layout (21 + N² bytes, D-042) | Checkpoints and bookmarks |
| H14 | The D-148 permission, exported-component and deny-list gates | Proof that each new feature adds no permission; the widget needs one allowlist entry |

### 6.2 v1.x

**Undo Off switch (purist mode).** Effort S. Adds the setting `undoEnabled: Boolean = true`. When Off, the controls-bar Undo, the TalkBack Undo action, the pad's Undo and both cards' Undo are hidden, and the undo keys show a snackbar "Undo is off". Games then keep `undoCount == 0`, so their scores already feed `bestScoreNoUndo` (H4); no record field is added. Switching it Off mid-game does not reset that game's `undoCount`. The v1.0 tests that assert Undo's presence gain an "Undo On" precondition.

**Local top-10 per size.** Effort S. Adds `SizeRecord.topGames: List<TopGame> = emptyList()`, with `TopGame(score: Long, maxExp: Int, moves: Int, elapsedMs: Long, undoCount: Int, finishedAtEpochMs: Long)`, filled by `RecordsMath.finalize` (sorted by score, ties by earlier finish, capped at 10). `finishedAtEpochMs` is a wall-clock read in `:app`; the engine stays clock-free (D-021). It cannot be backfilled, because v1.0 keeps no per-game history. Sixty entries keep `records.json` within its 64 KiB budget.

**Statistics display additions.** Effort S. Presentation of data v1.0 already stores, plus one optional counter:
- tiles reached: a tile-distribution histogram from `tileReach` for k = 7…17 (128 … 131072);
- a streak row with the current and longest win streak, next to games abandoned (H5);
- a friendlier long-duration format for "Time played" (for example "11 h 27 min" beside the v1.0 `formatElapsed` "11:27:14"), localized through `<plurals>`;
- a Best-chip shortcut: tapping the header Best chip opens Statistics on the current size's tab.

History is complete back to v1.0 because these fields were written from the first release. "Games won without undo" needs a new counter `gamesWonNoUndo: Int = 0`, which counts from the version that adds it.

**Moves and time HUD.** Effort S. Adds `showMovesAndTime: Boolean = false`. It reads `moveCount` and `PlayClock.elapsed(now)` (H6) and updates its text once per second only while Game is resumed and the game is in progress, so no infinite animation exists (D-063). Statistics are recorded whether or not it is shown.

**Daily seeded challenge.** Effort M.
- Board 4×4. The date is the local `LocalDate.now()`. The seed is the first 8 bytes, big-endian, of SHA-256 over the UTF-8 text `"2048-daily-v1|" + yyyy-MM-dd`, computed in `:app` with `java.security.MessageDigest`; the engine only receives the `Long` (H2).
- Puzzle number = `epochDay − launchEpochDay + 1`, where `launchEpochDay` is a constant frozen in the release that ships the feature.
- One scored attempt per day with undo hidden (scored only if `undoCount == 0`); unscored replays are allowed; past days are playable offline.
- Storage: `daily.json` = `{schemaVersion, date, seed, game: <the GameDocument fields>, scored: Boolean}`; records under the key `"daily:4"` (H8) plus an additive `Records.daily: DailyRecord = DailyRecord()` with days played, current and longest daily streak, best daily score and last played epoch day.
- Share header "2048 Daily #N" through `ShareText` (H12). No reminder notifications (runtime permission; §7).
- Before release, a golden test freezes date → seed → first board.

**History (last 100 games) and replay viewer.** Effort M.
- `history.json` is a ring buffer of 100 `HistoryEntry(size, seed, moveLog: String, moveCount, keepGoingAtMove, finalScore, maxExp, undoCount, elapsedMs, status, finishedAtEpochMs)`, appended at finalization only (never per move).
- Replay = `Game.newGame(size, seed)` folded with `Game.move` over the log, calling `Game.keepGoing` when `moveCount == keepGoingAtMove` (H1). The v1.0 test helper `Game.replay` throws on `Blocked(WON)`, so the viewer adds a keep-going-aware overload.
- Undone branches are not in the log (D-039 truncates it), so a replay shows the final line; `undoCount` tells how many moves were taken back.
- Viewer: play/pause, step ±1, speed 0.5×–8×, scrubbing through in-memory snapshots every 32 moves.
- Budget: 100 entries of a 10,000-move game is ≈ 260 KB worst case; `PersistenceBudgetTest` gains a ≤ 512 KiB bound for `history.json`.

**Redo.** Effort S. Adds `GameDocument.redoLog: String = ""` in the `moveLog` byte layout. Undo pushes the undone direction (`moveLog[t]`); Redo pops one and applies `Game.move`, which reproduces the exact spawn because undo restored the RNG (H3). Any new accepted move clears `redoLog`. Redo never decrements `undoCount`. Ctrl+Y and Ctrl+Shift+Z, ignored in v1.0 (F16-AC8), become Redo, next to a Redo button enabled iff `redoLog` is non-empty.

**Sound effects.** Effort M. Adds `sound: Boolean = false`. `SoundPool` with `AudioAttributes.USAGE_GAME` plays about five mono OGG one-shots (≤ 20 KB each, ≤ 150 KB total) from the CC0 Kenney packs (research/05 §4.15). The merge sound's rate is 0.8–1.6 by exponent. One sound per move is chosen from the same `MoveHaptic` resolution (H11). No permission. The CC0 assets are listed in `third_party_licenses.json` and `NOTICE`; the 3.0 MiB APK budget holds.

**Local achievements.** Effort M.
- `achievements.json` = `{schemaVersion, unlocked: Map<String, Long>}` (id → unlock epoch ms) with about 15 ids (research/05 §4.7), evaluated on accepted moves and at finalization.
- First run of the shipping version backfills from v1.0 records: tile milestones from `tileReach` per size, game counts from `gamesPlayed` (H5). "2048 without undo" and "2048 in under 10 minutes" cannot be backfilled (v1.0 stores no per-game win metadata) and count from that version on.
- Unlock UI: a snackbar plus a `Confirm` haptic, never while a card is shown. Assisted games (see Hints) never unlock anything.

**Hints (expectimax).** Effort M–L.
- Adds the setting `hints: Boolean = false`; a Hint button highlights one direction for 1.5 s.
- The search lives in `:core:engine`, pure and deterministic: `Hints.suggest(board, depth, shouldStop: () -> Boolean): Direction?`. It takes the expectation over **all** spawn outcomes (a 2 at 0.9, a 4 at 0.1, every empty cell) and never samples, so it never touches `GameState.rngState` and cannot alter spawns.
- 4×4 uses a `Long` bitboard with 65,536-entry row tables (research/05 §4.11); other sizes use a depth-2 array search.
- The 100 ms budget is enforced from `:app` on `Dispatchers.Default` through `shouldStop`, because the engine has no clock and no coroutines (D-021). Input cancels it.
- The first hint sets `GameDocument.assisted: Boolean = false` → `true`. `RecordsMath` excludes assisted games from bests and wins but keeps them in `gamesPlayed` and totals.
- Needs a low-end-device benchmark before shipping.

**Image share card.** Effort S–M. A composable of the board and summary is rendered with `rememberGraphicsLayer()` + `toImageBitmap()` to a PNG in `cacheDir/share/` (excluded from backup). It is shared through a `FileProvider` with `exported="false"`, `grantUriPermissions="true"` and a `<cache-path>`, using `ACTION_SEND` `image/png` with `FLAG_GRANT_READ_URI_PERMISSION`, a `ClipData` preview and the F10 text as `EXTRA_TEXT` (H12). The provider is not exported, so the D-148 allowlist is unchanged.

**Home-screen widget.** Effort M. A classic `AppWidgetProvider` with `RemoteViews`, never Glance, which pulls in WorkManager and four permissions; the D-148 deny-list already blocks `androidx.glance` and `androidx.work`. It shows the current game's score and the best for `lastBoardSize`, plus a "Continue" `PendingIntent` (`FLAG_IMMUTABLE`) to `MainActivity`. `updatePeriodMillis = 0`; the app pushes `AppWidgetManager.updateAppWidget` after a finalization or a new best, at most once per 2 s. **Gate impact:** the receiver must be `exported="true"` to receive `APPWIDGET_UPDATE`, so the D-148 exported allowlist changes; the widget's own decision entry must record that change.

**Static app shortcuts.** Effort S. `res/xml/shortcuts.xml` with at most 4 shortcuts: "New game (4×4)", "Statistics", and later "Daily challenge". Their intents target `.MainActivity` with the actions `com.clayboicardi.game2048.action.NEW_GAME` (extra `size`) and `com.clayboicardi.game2048.action.OPEN_STATISTICS`, handled in `onCreate` and `onNewIntent`. "New game" never discards a game in progress: it switches size and then shows the F06 confirmation. Labels are translated in all 21 locales. No new component and no permission.

**Export and import (SAF JSON).** Effort S–M. Export uses `ACTION_CREATE_DOCUMENT` (`application/json`, name `2048-backup-YYYYMMDD.json`) and writes `{"format":"game2048-backup","formatVersion":1,"settings":{…},"records":{…},"games":{"3":{…},…}}`, where each embedded document is the exact v1 JSON with its own `schemaVersion` (H7). Import uses `ACTION_OPEN_DOCUMENT`, decodes every part with the production serializers plus `GameInvariants`, rejects the whole file on any failure (no partial import), asks "Replace all data?", and writes through the repositories. SAF grants per-URI access, so no permission is needed. This serves players without Google backup, such as F-Droid users.

**Extra palettes and the Material You tile ramp.** Effort S per static palette, M for the dynamic ramp. New `PaletteId` names, which older versions read as `CLASSIC` (H9); every static palette must pass the `PaletteContrastTest` gates (D-083). The dynamic ramp (API 31+) derives 17 tile tones from the system seed in HCT (vendored material-color-utilities, Apache-2.0, about 2,100 lines; research/04 §8 B) or OKLCH. At runtime the generated ramp is checked with the same contrast and ΔE math and falls back to Classic when it fails, because some seeds produce duplicate tones (research/04 §3.4). Tests cover a corpus of at least 24 seeds, including `#B3261E`.

**Foldable posture layouts.** Effort M. Adds `LayoutMode.TABLETOP` (board above the hinge, controls below) and `LayoutMode.BOOK` (board in one pane, panel in the other) from the `androidx.window` `FoldingFeature` (H10). Prerequisite: prove that `androidx.window:window-testing` works under Robolectric, which is the reason D-091 defers this; then add posture goldens. No permission.

**Undo rewind animation.** Effort S. Under Full, undo plays the undone move backwards: the app recomputes its transitions by applying `moveLog[t]` to the restored state (deterministic, H3) and reverses them. Reduced and Off keep the crossfade. Presentation only.

### 6.3 Backlog

**Time trial and time attack.** Effort M. Adds `GameDocument.mode: String = "CLASSIC"` and separate records keys such as `"time:4"` (H8). A timed spawn is not a move, and the 2-bit log has no free code for it, so this mode needs a new log layout: it is the one roadmap item that **requires** a `schemaVersion` bump and a `DataMigration` for game documents (D-041). Pause semantics follow `ON_PAUSE`; time comes from `MonotonicClock`.

**Zen, limited moves, custom rules and spawn variants.** Effort M each. Adds `GameDocument.rules: RulesDto = RulesDto()`, whose defaults equal the classic rules (`fourOneIn = 10`, 2 start tiles, the per-size target, spawn values 2/4), so every v1 document decodes as classic. The engine takes the rules as a parameter while the classic golden vectors stay unchanged. Non-classic games are recorded under separate keys and never touch classic bests.

**Power-ups, checkpoints and bookmarks.** Effort M. Power-ups (swap two tiles, delete one value) set `assisted = true` and are logged. Checkpoints reuse the `Snapshot` byte layout (H13) as named entries in a new `bookmarks.json`; restoring one is an assisted action.

**AI autoplay.** Effort S after Hints. It applies `Hints.suggest` repeatedly; such games are always assisted and never enter records.

**Share or import whole games as files.** Effort M. Exports one game (seed, log, metadata) as JSON through a `FileProvider`; imports through SAF, validated by `GameDocumentSerializer` and `GameInvariants`, and replayable from the seed (H1). Imported games carry `imported: Boolean = true` and never touch records.

**Multiple save slots per size.** Effort M. Files `game-N-slotK.json` next to `game-N.json` (slot 0), with a slot picker in the size sheet.

**Copy diagnostic info.** Effort S. Shares, through `ACTION_SEND`, the version, API level, device model, layout mode and the settings JSON (no game data). No network and no logs.

**Wear OS and an Android TV launcher entry.** Effort L. TV needs a `LEANBACK_LAUNCHER` category and a banner; D-pad play already works (F16). Wear needs a separate module, which O2 allows only with a concrete reason. Both are non-goals for v1.0 (D-004).

---

## 7. Non-goals

| # | Non-goal | Reason | Source |
|---|---|---|---|
| N1 | Any network feature: ads, analytics, crash reporting, cloud sync, online leaderboards, Play Games Services, remote config, downloadable fonts | No `INTERNET`; offline by decision | U6, D-002, D-004 |
| N2 | Notifications, reminders, exact alarms, WorkManager, Glance widgets | Runtime `POST_NOTIFICATIONS`; WorkManager merges 4 permissions | D-004, research/05 §1 |
| N3 | `VIBRATE`, `Vibrator`, custom vibration patterns | Adds a permission; `performHapticFeedback` needs none | D-066 |
| N4 | Importing `com.uberspot.a2048` data, or a manual best-score entry | The data sits in another app's sandbox; manual entry can be faked | D-005, research/05 §4.20 |
| N5 | Rectangular boards | No surveyed app ships them; layout and stats get complicated for little value | D-003 #38 |
| N6 | Game modes other than Classic + Keep going in v1.0 | Scope of one hands-off PR | D-004 |
| N7 | A configurable win target | Breaks score comparability | D-036 |
| N8 | Quick Settings tile | A service whose only job is launching the game | D-003 #39 |
| N9 | In-app language picker on API < 33; AppCompat | Needs AppCompat only for old APIs | D-102 |
| N10 | Store uploads, real release signing, a release workflow, tags, GitHub Releases | No secrets; CI artifacts only | U5, D-133 |
| N11 | Wear OS and Android TV targets; posture layouts in v1.0 | Out of scope; postures are not JVM-testable yet | D-004, D-091 |
| N12 | Animation-speed multiplier | Replaced by Motion; it would quadruple the test matrix | D-006 |
| N13 | A "Confirm new game" toggle | Confirmation is unconditional while a game is in progress | O12, D-007 |
| N14 | Haptic intensity levels | On/Off only | O5, D-065 |
| N15 | An undo confirmation dialog | Undo is reversible and unlimited | D-039, research/02 §9 |
| N16 | Redo in v1.0 | O8 | D-039 |
| N17 | Double-back-to-exit; intercepting Back on the Game root | Breaks predictive back; state is always saved | D-094 |
| N18 | The long-press fullscreen gesture | Conflicts with swipes; no accessible path | D-093 |
| N19 | An orientation lock | Ignored on large screens; wastes landscape | D-090 |
| N20 | `systemGestureExclusion` on the board | The inset is the mitigation; exclusion is capped at 200 dp | D-051 |
| N21 | An input queue, delayed input, or a velocity-preserving "fluid" animation mode | Every move applies at once; snapping keeps visual order equal to model order | D-052, D-062 |
| N22 | Locale digits (for example Persian) in game numerals | Legacy parity; the numeral font has no Persian digits | D-103 |
| N23 | A Material You tile ramp in v1.0 | Duplicate tones for some seeds; about 2,100 vendored lines | D-074 |
| N24 | Per-tile semantics; swipe-only TalkBack play | Tiles are transient; TalkBack consumes swipes | D-080 |
| N25 | Error dialogs or logs for corrupt data | Silent per-file defaults; nothing leaves the device | D-045 |
| N26 | A separate gamepad button mapping (A/B/Start) | TV is out of scope; D-pads and hats already work | D-053 |
| N27 | Hilt, Koin, KSP, kapt, Room, protobuf, extra modules, convention plugins | Two modules and manual DI suffice | D-020, D-022 |
| N28 | Instrumented or emulator tests, macrobenchmarks, baseline profiles as merge gates | No device in CI | D-153 |
| N29 | An `fdroiddata` submission; renaming `master` to `main` | Outside this repository's PRs | D-004 |
| N30 | Toasts of any kind | Replaced by one-time hints and snackbars | D-009, DESIGN_SYSTEM.md §7.6 |

---

## 8. Test class index

Every test class named in §1, plus the motion classes of MOTION_SPEC.md §8.5 and the localization classes of L10N_STORE_LEGAL.md, with kind, location and milestone. Paths are relative to `core/engine/src/test/kotlin/com/clayboicardi/game2048/engine/` (engine) or `app/src/test/kotlin/com/clayboicardi/game2048/` (app). "M" follows D-211 and PLAN.md §5; when a class spans milestones, the ACs column gives each AC's milestone in parentheses. The milestone gate's AC audit (PLAN.md §5.7 A12, D-211) uses these assignments. Every new test file raises `config/test-count-floor.txt` in the same commit (D-149).

| Class | Kind | Path | M | ACs |
|---|---|---|---|---|
| `EngineGoldenTest` | Engine | `EngineGoldenTest.kt` | M1 | F01-AC1, F01-AC2 |
| `EnginePropertiesTest` | Engine | `EnginePropertiesTest.kt` | M1 | F01-AC3…AC12, F03-AC6, F05-AC2, F07-AC20 (P1–P13) |
| `EngineReplayGoldenTest` | Engine | `EngineReplayGoldenTest.kt` | M1 | F01-AC13, F01-AC14 |
| `SpawnDistributionTest` | Engine | `SpawnDistributionTest.kt` | M1 | F01-AC15 |
| `SplitMix64GoldenTest` | Engine | `SplitMix64GoldenTest.kt` | M1 | F01-AC16 |
| `StatusMachineTest` | Engine | `StatusMachineTest.kt` | M1 | F01-AC17, F01-AC18, F02-AC1, F02-AC2, F03-AC1…AC5 |
| `UndoTest` | Engine | `UndoTest.kt` | M1 | F03-AC7, F04-AC8, F05-AC1, F05-AC3…AC7 |
| `HistoryCodecTest` | Engine | `HistoryCodecTest.kt` | M1 | F07-AC21 |
| `EngineImportsGuardTest` | Engine | `EngineImportsGuardTest.kt` | M1 | F01-AC19 |
| `EnginePerformanceTest` | Engine | `EnginePerformanceTest.kt` | M1 | F01-AC20 |
| `PersistenceFixturesTest` | App JVM | `data/` | M2 | F07-AC14, F07-AC15 |
| `PersistenceCorruptionTest` | App JVM | `data/` | M2 | F02-AC12, F07-AC16, F07-AC17, F07-AC23 |
| `PersistenceBudgetTest` | App JVM | `data/` | M2 | F07-AC18, F07-AC19 |
| `ConflatedWriterTest` | App JVM | `data/` | M2 | F07-AC5, F07-AC6 |
| `SettingsRepositoryTest` | App JVM | `data/` | M2 | F02-AC11, F11-AC10, F11-AC11 |
| `RecordsMathTest` | App JVM | `data/` | M2 | F08-AC1…AC7, F08-AC19, F08-AC20 |
| `LongGameGeneratorTest` | App JVM | `data/` | M2 | F07-AC22 |
| `ProcessDeathRestoreTest` | App JVM | root | M2 | F04-AC10, F05-AC15, F05-AC16, F07-AC1 |
| `FinalizationRecoveryTest` | App JVM | root | M2 | F06-AC14…AC19, F08-AC22 |
| `GameViewModelTest` | App JVM | `game/` | M2 | F01-AC21, F01-AC22, F02-AC3…AC6, F03-AC8…AC10, F03-AC15, F04-AC1, F04-AC9, F04-AC11, F05-AC8, F06-AC7…AC9, F06-AC13, F07-AC2, F07-AC3, F07-AC10, F07-AC24, F08-AC8, F08-AC16, F09-AC1…AC4, F13-AC14, F22-AC1…AC4, F22-AC10 |
| `InputBurstOrderingTest` | App JVM | `game/` | M2 | F15-AC13 |
| `PlayClockTest` | App JVM | `game/` | M2 | F04-AC12, F05-AC14, F07-AC4 |
| `BoardPresenterTest` | App JVM | `game/board/` | M2 | F05-AC9, F06-AC11, F07-AC25 |
| `BoardGeometryTest` | App JVM | `game/board/` | M3–M4 | F02-AC8, F15-AC14 (M3); F18-AC2, F18-AC3, F18-AC8, F18-AC9 (M4) |
| `TileLabelsTest` | App JVM | `game/board/` | M3 | F02-AC9, F02-AC10 |
| `SwipeClassifierTest` | App JVM | `game/input/` | M3 | F15-AC1, F15-AC2 |
| `ShareTextTest` | App JVM | `game/share/` | M3 | F10-AC1…AC5 |
| `MotionPlanTest` | App JVM | `ui/theme/` | M3 | F13-AC5…AC7 |
| `FormattingTest` | App JVM | `ui/components/` | M3, M5 | F28-AC7…AC9 (M3); L-AC5 `toLatinDigitsMapsArabicIndicAndPersian` (M5) |
| `DelayedSpecTest` | App JVM | `motion/` | M3 | No feature AC: `delayedSpec` value and velocity at play times 0, delay − 1 ns, delay and delay + 16 ms against the plain spec shifted by the delay (D-183) |
| `WhatsNewTest` | App JVM | `about/` | M4 | F24-AC1…AC6 |
| `LicensesCoverageTest` | App JVM | `about/` | M4 | F26-AC1 |
| `PaletteContrastTest` | App JVM | `ui/theme/` | M4 | F12-AC3, F12-AC4 |
| `StatsViewModelTest` | App JVM | `stats/` | M4 | F08-AC9…AC11, F08-AC18, F08-AC21 |
| `LayoutModeTest` | App JVM | `ui/components/` | M4 | F18-AC1 |
| `LaunchSmokeTest` | Robolectric `@Config(sdk = [24, 37])` | root | M0, M3 | F30-AC5 (M0; its assertion switches to the Game content in M3); F21-AC1, F22-AC7 (M3) |
| `SplashGateTest` | Robolectric | root | M2–M3 | F07-AC11, F07-AC12 (M2); F07-AC13 (M3) |
| `RecreateNoReplayTest` | Robolectric | root | M2–M4 | F07-AC7, F07-AC8 (M2); F07-AC9, F07-AC26…AC30 (M3 part 2); F18-AC5 (M4) |
| `BackOnRootTest` | Robolectric | root | M3 | F21-AC3 (also AC-BACK-2), F21-AC4…AC9 |
| `BackupRulesTest` | Robolectric | root | M2 | F29-AC1…AC3 |
| `AppContainerTest` | Robolectric | `di/` | M2 | F30-AC10, F30-AC11 |
| `ManifestPolicyTest` | Robolectric | root | M4 | F16-AC9, F18-AC6, F18-AC7, F27-AC6, F28-AC10, F30-AC6, F30-AC7; L-AC12 `legacyLauncherIconOnApi24` |
| `ImmersiveModeTest` | Robolectric | root | M4 | F19-AC1…AC5 |
| `KeepScreenOnTest` | Robolectric | root | M4 | F20-AC1…AC5 |
| `LocalizationTest` | Robolectric | root | M5 | F24-AC8, F28-AC2…AC6 |
| `TranslationQualityTest` | Robolectric | root | M5 | L-AC4: the 9 methods of L10N_STORE_LEGAL.md §1.9 (class KDoc `/** L-AC4 */`) |
| `GameScreenTest` | Robolectric | `game/` | M3–M4 | M3: F02-AC7, F03-AC11…AC14, F03-AC16, F03-AC19, F04-AC2…AC7, F05-AC10…AC13, F06-AC1…AC6, F06-AC10, F09-AC5, F09-AC6, F10-AC6…AC8, F13-AC9, F13-AC10, F13-AC13, F21-AC2, F21-AC10. M4: F03-AC18, F04-AC14, F05-AC17, F17-AC15, F17-AC21, F18-AC10, F18-AC12, F22-AC5, F22-AC6, F22-AC9, F24-AC7 |
| `TalkBackModeTest` | Robolectric | `game/` | M4 | F17-AC8…AC14, F17-AC19, F17-AC20 |
| `TileRecompositionTest` | Robolectric | `game/board/` | M3 | F13-AC12 (also AC-PERF-1) |
| `BoardSemanticsTest` | Robolectric | `game/board/` | M3–M4 | F17-AC1…AC6 (M3); F17-AC7 (M4) |
| `SwipeInputTest` | Robolectric | `game/input/` | M3–M4 | F15-AC3…AC12, F19-AC6 (M3; F15-AC11's What's new sheet case is added in M4 with the sheet); F18-AC11 (M4) |
| `KeyboardInputTest` | Robolectric | `game/input/` | M3–M4 | F16-AC1…AC8, F16-AC10, F16-AC11 (M3; F16-AC6's What's new sheet case is added in M4 with the sheet) |
| `HapticsMapTest` | Robolectric | `game/feedback/` | M2–M3 | F14-AC1 (M2); F14-AC2…AC7, F14-AC9 and MOTION_SPEC.md §9.11 (AC-HAP-*, except AC-HAP-4) (M3) |
| `SettingsScreenTest` | Robolectric | `settings/` | M4 | F11-AC1…AC9, F14-AC8 (also AC-HAP-4); L-AC6 `appLanguageSummaryDefaultsToSystem`; AC-SCALE-4 |
| `StatsScreenTest` | Robolectric | `stats/` | M4 | F08-AC12…AC15 |
| `AboutScreenTest` | Robolectric | `about/` | M4 | F25-AC1…AC7, F26-AC3, F26-AC4, F27-AC4 |
| `HowToPlayScreenTest` | Robolectric | `about/` | M4 | F23-AC1…AC6 |
| `GameThemeTest` | Robolectric | `ui/theme/` | M4 | F12-AC1, F12-AC2, F12-AC5…AC9 |
| `EffectiveMotionTest` | Robolectric | `ui/theme/` | M3 | F13-AC1…AC4, F13-AC11 |
| `MiniTileTest` | Robolectric | `ui/components/` | M3 | No feature AC: `MiniTile`, `MiniBoardGlyph` and `PaletteSwatch` per DESIGN_SYSTEM.md §7.8 |
| `GameScreenshotTest` | Screenshot | `screenshots/` | M3–M4 | F02-AC13, F02-AC14, F03-AC17, F04-AC13, F06-AC12, F12-AC10, F12-AC11, F17-AC18, F22-AC8. M3 part 1 records rows 1, 3 and 43 and M3 part 2 rows 13, 15 and 42; M4 records the remaining rows and re-records every row (D-204) |
| `ScreensScreenshotTest` | Screenshot | `screenshots/` | M4 | F08-AC17, F11-AC12, F17-AC17, F23-AC7, F24-AC9, F25-AC8, F26-AC6 |
| `LocaleScreenshotTest` | Screenshot | `screenshots/` | M5 | F28-AC11…AC13 |
| `LayoutScreenshotTest` | Screenshot | `screenshots/` | M4 | F18-AC4 |
| `MotionFilmstripTest` | Screenshot | `screenshots/` | M3 | F13-AC8 (also AC-GOLD-1; rows 37–41) |
| `MotionTokensTest` | App JVM | `motion/` | M3 | MOTION_SPEC.md §1.1, §1.5; AC-TOKENS-1 |
| `TimelineOriginTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §0.1 (the origin) |
| `MoveTimelineTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §3.1, §3.2; §9.1 (AC-MOVE-*) |
| `MergePopTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §3.3 (tiers, sheen, glow pulse, flash limiter, cap); §9.1 (AC-MOVE-*) |
| `SpawnDelayTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §3.1 delay table, I1 on 8×8; §9.1 (AC-MOVE-*) |
| `ScoreMotionTest` | Robolectric | `motion/` | M3 | F13-AC15 (also AC-SCORE-5); MOTION_SPEC.md §9.2 (AC-SCORE-*) |
| `NewBestTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.3 (AC-BEST-*) |
| `InvalidNudgeTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.4 (AC-NUDGE-*) |
| `NewGameCascadeTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.5 (AC-NEW-*) |
| `UndoCrossfadeTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.5 (AC-UNDO-*) |
| `SizeSwitchTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.5 (AC-SIZE-*) |
| `RestoreNoMotionTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.9 AC-RESTORE-1, AC-RESTORE-2: board cases in M3 part 1; the card cases (AC-RESTORE-1's card end state, AC-RESTORE-2's `FIX_WIN_PRE_4` case) are added in part 2 with `OverlayController` |
| `InterruptionTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.12 AC-CONS-2 |
| `ConsistencyPropertyTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.12 AC-CONS-1 |
| `FinalFrameEqualsStaticTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.12 AC-CONS-3 |
| `BoardRecompositionBudgetTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.12 AC-PERF-2 |
| `ConfettiSystemTest` | App JVM | `motion/` | M3 | MOTION_SPEC.md §9.12 AC-PERF-3, AC-PERF-4 |
| `WinSequenceTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.6 (AC-WIN-*) |
| `CardExitTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.7 (AC-EXIT-*) |
| `GameOverSequenceTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.8 (AC-OVER-*) |
| `NavTransitionsTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.9 AC-NAV-1 |
| `PredictiveBackTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.9 AC-BACK-1 (AC-BACK-2 is proved by F21-AC3's method) |
| `ReducedMotionTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.10 (AC-RED-*): board methods in M3 part 1, overlay methods in part 2 |
| `MotionOffTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.10 (AC-OFF-*): board methods in M3 part 1, overlay methods in part 2 |
| `AnimatorScaleTest` | Robolectric | `motion/` | M3 | MOTION_SPEC.md §9.10 (AC-SCALE-0, AC-SCALE-2, AC-SCALE-3): board methods in M3 part 1, overlay methods in part 2 |
| `ThemeCrossfadeTest` | Robolectric | `motion/` | M4 | MOTION_SPEC.md §9.9 AC-THEME-1 |
| `LayoutMotionTest` | Robolectric | `motion/` | M4 | F18-AC13 (also AC-LAYOUT-2); MOTION_SPEC.md §9.9 AC-LAYOUT-1 |

Tests with no AC of their own (infrastructure):
- `DirectionTest` (engine, M0 skeleton);
- `PlaceholderScreenshotTest` (M0, deleted with its PNG in M3 part 1);
- `data/DataMigrationSeamTest` (M2, the D-041 migration seam);
- `store/StoreAssetsTest` (M5; runs only with `-PrecordStoreAssets=true`; D-115, D-190; its output is checked by F30-AC9 and inspected per L10N_STORE_LEGAL.md L-AC9).

Gate-mapped ACs (no test class):
- F17-AC16: the ATF rule on every golden except `motion_*` (from M3);
- F26-AC2: `check-licenses.sh` (from M0);
- F27-AC1…AC3, F30-AC1…AC4: `check-apk.sh` steps 1–7 (from M0);
- F28-AC1: Lint (from M0);
- F26-AC5: `check-metadata.sh` `check_license_file` (M0), `check_font` (M4), `check_legal_texts` and `check_legal_and_privacy` (M5);
- F27-AC5: `check_legal_and_privacy` (M5);
- F30-AC8: `check_store_listing` (M5);
- F30-AC9: `check_store_images` (M5).

Golden coverage: §1 claims all 45 goldens of DESIGN_SYSTEM.md §10.4 (D-204): 1–4 F12-AC11, 5–7 F02-AC13, 8–12 F12-AC10, 13–14 F03-AC17, 15–16 F04-AC13, 17–18 F11-AC12, 19 F08-AC17, 20 F23-AC7, 21 F25-AC8, 22 F26-AC6, 23–26 F28-AC11…AC13, 27–28 F17-AC17, 29–35 F18-AC4, 36 F17-AC18, 37–41 F13-AC8, 42 F02-AC14, 43 F06-AC12, 44 F24-AC9, 45 F22-AC8.

The remaining Robolectric observability risk and its fallback are OI-F13 (manifest facts and locale-config parsing).

Seams this file relies on (all pure or test-only, none changes production behavior):
- `layoutModeFor(widthDp: Int, heightDp: Int): LayoutMode` beside `rememberLayoutMode()` in `ui/components/WindowMode.kt` (F18-AC1);
- the pure `resolveGameLayout(window: LayoutWindow, chrome: ChromeMeasure): GameLayoutSpec` in `game/board/BoardGeometry.kt`, driven by a private nominal `ChromeMeasure` inside `BoardGeometryTest` (F18-AC2, AC3, AC8, AC9);
- `ShareStrings`, the pre-resolved templates passed to `ShareText.build` (F10);
- `TileLabels.fullDecimal(exp)`, exact through `BigInteger` above exponent 62 (F02, F17);
- the internal `slide` of `engine/Slide.kt`, visible to engine tests in the same module (F01-AC1);
- `LocalRecompositionCounter` (`game/board/RecompositionCounter.kt`: `onTile(id)`, `onBoard()`, `onScreen()`; null in production; ARCHITECTURE.md §5.7) and `FakeHapticFeedback` (`testing/FakeHapticFeedback.kt`);
- the seams adopted up front (PLAN.md §2.6 E): `SystemBarsController` behind `ImmersiveMode.apply` (F19), the injectable `ContrastLevel` source (F11-AC8, F12-AC6) and the `AnimatorScaleSource` owned by `AppContainer` (F13), each with a fake in tests.

---

## Open issues

This section keeps only verification risks that remain open, each with its fallback.

- **OI-F13 (Robolectric observability).** `ManifestPolicyTest` relies on Robolectric's package parser exposing `ApplicationInfo.category`, `reqFeatures`, the `InitializationProvider` meta-data, the adaptive icon's monochrome layer and the API 24 legacy icon (L-AC12). F28-AC3 parses the generated `R.xml._generated_res_locale_config` resource in the debug unit test. Neither was smoke-run. Verified at first use (M4 for the manifest facts, M5 for the locale config). Fallback, recorded in the PR body: assert the same facts with `aapt2 dump xmltree` on the debug APK's manifest and on its generated locale-config XML, as added checks in `scripts/check-apk.sh`; the test methods keep the facts Robolectric does expose, and an AC whose fact moves to the gate names that gate step instead.

Resolved during planning: OI-F1 → D-198; OI-F2, OI-F3 → D-199; OI-F4 → D-190; OI-F5 → D-204 and the canonical names; OI-F6 → `BigInteger` in `TileLabels.fullDecimal` and `ShareText` (F02, F10 text); OI-F7 → QUALITY V4; OI-F8 → D-198; OI-F9 → D-198; OI-F10 → D-201; OI-F11 → D-186; OI-F12 → D-181; OI-F14 → `SystemBarsController` seam adopted; OI-F15 → as specified in F10/F04 (D-103).
