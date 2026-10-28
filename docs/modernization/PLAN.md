# PLAN.md: master execution plan for the 2048 native rewrite

Status: plan document, dated 2026-10-28. It ships in the plan PR (D-160 PR 1) and drives the single execution PR (D-160 PR 2).

Audience: the orchestrator and the implementer agents (milestone integrator and WP implementers) that execute milestones M0–M6 hands-off (U7); roles in §6.1 (D-194). Nobody is available to answer questions during execution; every choice the specs leave open is settled here or in `DECISIONS.md`.

---

## 0. How to use this plan

### 0.1 Reading order

1. `DECISIONS.md`: canonical. If anything below disagrees with it, `DECISIONS.md` wins, and the disagreement is a bug in this file (fix it with a `docs:` commit).
2. This file, §0–§6 in full. §7–§9 are reference tables.
3. The spec sections that your milestone or work package (WP) names. Each task lists them.
4. `research/` only when a spec points there.

### 0.2 Precedence between documents

| Question | Authority |
|---|---|
| What is decided (behavior, versions, gates, process) | `DECISIONS.md` (D-001–D-211 and Appendix B TC-1–TC-7) |
| Where a file lives, which milestone creates it, which canonical name a type or test gets | Canonical names: this file §2 (naming index); file map: `ARCHITECTURE.md` §10, which already uses them |
| Code shape, data flow, build files | `ARCHITECTURE.md` |
| Feature behavior and acceptance criteria (ACs) `Fnn-ACk` | `FEATURES.md` |
| Visual tokens, screens, golden list | `DESIGN_SYSTEM.md` |
| Motion, haptics timing, motion ACs `AC-*` | `MOTION_SPEC.md` (for timing it wins over the `ARCHITECTURE.md` §5.4–§5.8 code sketches; D-183) |
| Tests infrastructure, scripts, CI, Codex protocol | `QUALITY.md` |
| Strings, translations, store listing, legal files, signing distribution | `L10N_STORE_LEGAL.md` |

### 0.3 Conventions

- Task IDs are `M<n>.<k>`. Work packages are `WP M<n>-<letter>`; the milestone integrator owns everything not assigned to a WP.
- "verify full" means `./scripts/verify.sh` with no arguments (QUALITY.md §3.1). "verify quick" and "verify --record" are the other levels.
- "Read a PNG" means open it with the file-reading tool, look at it, and describe it in the commit body (QUALITY.md §1.9 Read rule).
- Paths are repository-relative. `app/…/game2048/` abbreviates `app/src/main/kotlin/com/clayboicardi/game2048/`; `test/…/game2048/` abbreviates `app/src/test/kotlin/com/clayboicardi/game2048/`; `engine/` abbreviates `core/engine/src/main/kotlin/com/clayboicardi/game2048/engine/` and `engine-test/` its test twin.
- GitHub operations name the repository `clayboicardi/2048-android-modern-refresh` (QUALITY.md §6.8).

---

## 1. Goal, success criteria, scope

### 1.1 Goal

Replace the 2014 WebView shell (`MainActivity.java` around the `assets/2048` web game) with a native Kotlin + Jetpack Compose + Material 3 app, `com.clayboicardi.game2048` "2048" 3.0.0 (30000): offline, permission-free, accessible, localized in 21 locales, verified on the JVM locally and in GitHub Actions, and merged into `master` through one execution PR that passes CI and the Codex review gate (U1–U7).

### 1.2 Success criteria (all measurable; checked in M6.1 and at merge)

| # | Criterion | Measured by |
|---|---|---|
| S1 | `./scripts/verify.sh` passes on the merge head locally and in a fresh clone | Last line `verify.sh: level=full record=false result=PASS …`; M6.2 clean-clone run |
| S2 | CI job `verify` is green on the merge head SHA | The `verify` check run on the head SHA, read via the GitHub API (`pull_request_read` `get_check_runs`, or `gh pr checks`), concludes `success` |
| S3 | The latest triggered Codex round on the merge head is clean (D-196), or the D-168 valve applies; no open P0/P1; every thread answered | Review-rounds table in the PR body (§6.9, §6.10) |
| S4 | Every acceptance criterion is proved: every `Fnn-ACk` of `FEATURES.md`, every `AC-*` of `MOTION_SPEC.md` §9 and every `L-ACn` of `L10N_STORE_LEGAL.md` maps to a test method whose first KDoc line is the ID, or to a named gate | M6.1 AC audit script (§5.7) prints `0 missing` |
| S5 | Legacy shell gone | `git submodule status` prints nothing; `git ls-files \| grep -E '^(src\|res\|metadata\|assets)/'` prints nothing; no `.travis.yml`, `.gitmodules` |
| S6 | Release APK ≤ 3,145,728 bytes; exactly one `uses-permission` (`…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`); exported set exactly the D-148 allowlist; 16 KB aligned; signer digest = `signing/ci-sideload.sha256`; deny-list clean | `scripts/check-apk.sh` output |
| S7 | Engine coverage ≥ 90 % line, ≥ 85 % branch; engine 100,000 moves on 8×8 ≤ 2 s | `:core:engine:koverVerify`; `EnginePerformanceTest` |
| S8 | Persistence budgets: 10,000-move 8×8 document ≤ 262,144 B and encode+decode ≤ 50 ms; records ≤ 65,536 B; settings ≤ 4,096 B | `PersistenceBudgetTest` |
| S9 | Zero tile recompositions during a slide; `BoardView` ≤ 2 and `GameScreen` 0 recompositions per move | `TileRecompositionTest`, `BoardRecompositionBudgetTest` |
| S10 | 45 goldens (`DESIGN_SYSTEM.md` §10.4) committed, each compared by a test, total ≤ 10 MiB, each Read | `check-test-counts.sh` golden rules G1–G5 (QUALITY §1.15: results match the goldens exactly, every result `unchanged`, no results fails); `check_golden_budget`; commit bodies |
| S11 | 20 locales complete; Lint translation issues 0; `TranslationQualityTest` green | `:app:lintDebug`; test report |
| S12 | 22 store-listing directories valid; 8 store images generated from code and reproducible | `check_store_listing`, `check_store_images`; second `StoreAssetsTest` run leaves `git diff --exit-code fastlane/` clean |
| S13 | No opt-in outside the two allowlisted files; no Lint/detekt baseline; every suppression carries a reason | `check_optin_allowlist`, `check_no_baselines`; suppression inventory in the PR body |
| S14 | "Deviations from DECISIONS.md" in the PR body reads None or lists only entries added during execution (D-212 and later); D-174–D-211 and TC-1–TC-7 were decided in the plan PR | PR body |
| S15 | No AI model name or model identifier in any tracked file | The orchestrator's out-of-repo term grep over tracked files before each push prints nothing (D-180) |

### 1.3 Scope summary

v1.0 is exactly the "In v1.0" rows of the D-003 scope table, specified feature by feature in `FEATURES.md` F01–F30 (index: `FEATURES.md` §0.4). In one line each: classic rules on 3×3–8×8 with per-size saves and records (F01, F02); win/Keep going and game over cards (F03, F04); unlimited undo with RNG restore (F05); confirmed New game (F06); auto-save and process-death safety (F07); statistics (F08); new-best moment (F09); text share (F10); settings (F11); Classic Refined and Neon Arcade themes with high contrast and chrome-only dynamic color (F12); Full/Reduced/Off motion (F13); haptics (F14); swipe, keyboard, D-pad (F15, F16); accessibility (F17); adaptive layouts (F18); edge-to-edge and Immersive mode (F19); keep screen on (F20); Navigation 3 and predictive back (F21); first-launch hint, How to play, What's new, About (F22–F25); licenses, privacy, offline guarantee (F26, F27); 21 locales and RTL (F28); backup (F29); identity, splash, signing, store listing (F30).

### 1.4 Non-goals

Everything in D-004 and `FEATURES.md` §7 (N1–N30). Plan-specific additions:
- No device or emulator QA, no manual TalkBack session, no human translation review (U7 is hands-off; the substitutes are §5 gates and `TranslationQualityTest`).
- No release, tag, store upload or real signing (D-133).
- No change to `gradle/libs.versions.toml` beyond D-016 without a D-173 entry, including test-only artifacts.
- No refactor of research files under `docs/modernization/research/` (evidence is frozen; only `docs:` fixes of factual links).

---

## 2. Canonical naming index

Every document already uses the canonical names. The left column lists names that earlier drafts used; outside this index they appear only where a DECISIONS entry records a change. `ARCHITECTURE.md` §10 is the file map. Paths are under `app/…/game2048/` and tests under `test/…/game2048/` unless shown.

### 2.1 Paths

| Earlier name | Canonical | Notes |
|---|---|---|
| `game/motion/AnimatorScaleSource.kt` | `ui/theme/EffectiveMotion.kt` (`AnimatorScaleSource`, `SystemAnimatorScaleSource`, `effectiveMotion()`, `LocalEffectiveMotion`) | `AppContainer` owns the source |
| `game/TileAnimator.kt`, `TileAnimator` | `game/board/BoardAnimator.kt`: `BoardAnimator` holding one `TileAnim` per tile id | |
| `game/BoardView.kt`; composable `Board(...)` | `game/board/Board.kt` containing `@Composable fun BoardView(...)` | The name avoids the engine type `Board` |
| `game/BoardPresenter.kt` | `game/board/BoardPresenter.kt` + `game/board/BoardUi.kt` | Created in M2 (D-211) |
| `game/BoardEffectsCanvas.kt`, `game/ContentEffectsCanvas.kt`, DS `EffectsCanvas` / `ConfettiCanvas` | `game/board/EffectsCanvas.kt` holding `BoardEffectsCanvas` (ring) and `ContentEffectsCanvas` (confetti, sparkles) | D-187 |
| `game/OverlayController.kt` | `game/overlay/OverlayController.kt` | Exposes `interactive` state (D-200) |
| `game/GameHaptics.kt`, `GameHaptics` | `game/feedback/HapticsController.kt`, `HapticsController` (schedules only); `game/feedback/HapticsMap.kt` (owns priority, M2) | D-185 |
| `ui/components/ScoreChip.kt` | `ui/components/ScoreCard.kt` (`@Composable fun ScoreChip`); score extras in `game/header/ScoreOdometer.kt`, `game/header/ScoreFloats.kt`, `game/header/NewBestBadge.kt` | Sparkles are drawn by `ContentEffectsCanvas` |
| `ui/components/OverlayCard.kt` | `game/overlay/BoardScrim.kt` (`OverlayCard` + clipped scrim); `WinBadge` in `game/overlay/WinCard.kt` | |
| `ui/theme/TilePalettes.kt` | `ui/theme/TilePalette.kt` | |
| `ui/theme/GameFonts.kt` | `ui/theme/NumeralFont.kt` containing `object GameFonts` | |
| `ui/theme/BoardGeometry.kt` | `game/board/BoardGeometry.kt` | API in §2.2 |
| `ui/theme/LayoutMode.kt` | `ui/components/WindowMode.kt` | API in §2.2 |
| `game/BoardSizeSheet.kt` | `game/overlay/BoardSizeSheet.kt` | |
| `GameButtons.kt`; ButtonGroup in `GameControls.kt` | `ui/components/AppButtons.kt` (`GameButton*` wrappers with `ButtonDefaults.shapes()`); no `ButtonGroup` | |
| `ui/components/DirectionPad.kt` | `game/input/DirectionPad.kt`; status line `game/StatusLine.kt` | |
| `SettingsRows.kt` | `ui/components/SettingRows.kt` | |
| `testing/Fixtures.kt` | `fixtures/GoldenFixtures.kt` + `fixtures/MotionFixtures.kt` (test source set) | |

### 2.2 Types and APIs

| Earlier name | Canonical | Notes |
|---|---|---|
| `MotionSetting` | `MotionPreference` | Persisted (D-042) |
| `LocalMotionLevel` | `LocalEffectiveMotion: ProvidableCompositionLocal<MotionLevel>` | Provided by `GameTheme` (not `App()`) |
| ARCH enum `EffectiveMotion`, `EffectiveMotion.OFF` | `MotionLevel { FULL, REDUCED, OFF }` in `ui/theme/GameMotion.kt`, `MotionLevel.OFF` | |
| `BoardEvent.Restore` / `Move` / `SizeSwitch` | `BoardEvent.Restored` / `Moved(direction, mergedExps, mergeCount, maxMergedExp, maxSlideCells, spawnIndex, scoreAfter, haptic, newBest, becameWon, becameOver)` / `SizeSwitched`; plus `Undo`, `NewGame` | `haptic: MoveHaptic` (D-185) |
| `GameEffect.Nudge(direction)` | `Nudge(id, direction)` on `GameViewModel.nudge: StateFlow<Nudge?>` | |
| `vm.onNewGameConfirmed()` | `vm.onNewGame()` | The screen shows the dialog first when in progress |
| `record(id)` (recomposition) | `RecompositionCounter.onTile(id)`, `onBoard()`, `onScreen()` in `game/board/RecompositionCounter.kt` | |
| `delayScaled(ms)` | `MoveTimeline.awaitOffset(moveId, ms)` / `awaitRealTime(moveId, ms)` | D-183 |
| animation-core `delayed(spec, nanos)` | `delayedSpec(spec, delayMs)` in `game/motion/DelayedSpec.kt`; test `motion/DelayedSpecTest` | `delayed()` is internal |
| (no frame-rate hint) | `BoardView` applies `Modifier.preferredFrameRate(FrameRateCategory.High)` while any board animation runs, `Default` when idle | D-183 |
| `tileLabel(exp, tileDp): TileLabel(text, sizeDp, …)` | `TileLabels.label(exp: Int, tileSizeDp: Float): TileLabel(text, fontSizeDp, wdth, compact)`, `TileLabels.fullDecimal(exp)` | |
| `BoardGeometry.compute(boardPx, n, gapRatio, density)` | `boardGeometry(n: Int, board: Dp, skin: TileSkin, density: Float): BoardGeometry` and `resolveGameLayout(window: LayoutWindow, chrome: ChromeMeasure): GameLayoutSpec` (with `HeaderStyle`) | Resolution in DS §5.3 (D-188) |
| `cellTopLeft(row: Int, col: Int)`; `Offset(col, row)` | `fun cellTopLeft(x: Float, y: Float): IntOffset` (x = column, y = row, fractional allowed, rounded per DS §4.7) | |
| `GameLayoutMode`, `gameLayoutMode(widthDp: Float, heightDp: Float)` | `enum class LayoutMode { STACKED, THREE_COLUMN, SIDE_PANEL, SCROLL }`, `layoutModeFor(widthDp: Int, heightDp: Int)` (the preference; never SCROLL), `rememberLayoutMode()` | In `ui/components/WindowMode.kt` |
| `formatDuration`; `formatCount(n)` | `formatElapsed`; `formatCount(n, locale)`; plus `formatPercent`, `toLatinDigits` (L10N §1.10) | `ui/components/Formatting.kt`, M3 |
| `PropTestConfig(seed = 0x2048L)`, `Arbs.CONFIG` | `@BeforeEach fun seed() { PropertyTesting.defaultSeed = 0x2048L; PropertyTesting.writeFailedSeed = false }` + `checkAll(Arbs.ITERATIONS, …)` | TC-6 |
| `ListItem(headlineContent = …)` | `ListItem(modifier, …, supportingContent, trailingContent) { headline }`; `ListItem(checked, onCheckedChange) { … }`; `ListItem(selected, onClick) { … }` | DS §7.9 |
| `Modifier.innerShadow(shape) { radius = 12.dp … }` | `radius = 12.dp.toPx()` (`ShadowScope` values are px) | |
| `captureRoboImage(tmpFile.path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))` | `composeRule.onNodeWithTag("store:frame").captureToImage().asAndroidBitmap()` → `OpaquePngWriter.write(bitmap, background, file)` | D-190 |
| `setHasAlpha(false)` + `featureGraphic.jpg` fallback | `OpaquePngWriter` (8-bit, color type 2) | D-190 |

### 2.3 Tests and methods

| Earlier name | Canonical | Notes |
|---|---|---|
| `RecordingHaptics` | `FakeHapticFeedback` (`test/…/testing/FakeHapticFeedback.kt`) | Records `(type, mainClock.currentTime − origin)` |
| `MotionFilmstripGoldenTest`, `motion/MotionFilmstripGoldenTest` | `screenshots/MotionFilmstripTest` | Method `mergeAndSpawnFilmstrip` (F13-AC8, AC-GOLD-1) |
| `motion/HapticsMapTest` | `game/feedback/HapticsMapTest` | Created in M2 (F14-AC1), extended in M3 |
| `TileLabelTest` | `game/board/TileLabelsTest` | |
| `BoardLayoutTest` | `game/board/BoardGeometryTest` (`boardSizePerMode`, `narrowWindowFloor`) | |
| QUALITY `rowsLeft`, `rowsRight`, `allDirections` | `EngineGoldenTest.rowLeftGoldens`, `rowRightAndVerticalGoldens` (plus `largerBoards`, `noOpDrawsNothing`, `winRows`) | |
| `StatusMachineTest.blockedInWon` | `StatusMachineTest.blockedInWonAndOver` | |
| AC-SCALE-0's Settings-row clause | new `AC-SCALE-4` (`SettingsScreenTest`, M4) | |
| `KeyboardInputTest.ignoredWhileModalOpen` covering cards | `ignoredWhileModalOpen` = dialogs and sheets only; cards = `KeyboardInputTest.cardKeysActLikeChrome` (F16-AC11) | D-199 |
| `GameScreenTest.undoUsedSnackbarShownOncePerGame` | `GameScreenTest.undoUsedSnackbarUntilFirstFinalizedUndoGame` | D-186 |
| `GameScreenTest.shareOnlyOnGameOverCard` | `GameScreenTest.shareOnBothCardsNotInMenu` | D-200 |
| `GameViewModelTest.hintHiddenWhenAGameFileExists` | `GameViewModelTest.hintShownWhileStartingGameHasNoMove` (F22-AC2) + `hintHiddenAfterAMove` (F22-AC10) | D-201 |

### 2.4 String keys and gate names

| Earlier name | Canonical | Notes |
|---|---|---|
| `whats_new_30000_N` | `whatsnew_30000_1` … `whatsnew_30000_5` | |
| `theme_system` (App language summary) | `setting_app_language_system` | |
| `"$label $valueLatin"` concatenation | `a11y_chip_value` = `%1$s %2$s` | |
| unnamed `onClickLabel` on choice / link rows | `a11y_action_change` / `a11y_action_open` | |
| “Best tile, {full value}” | `a11y_best_tile` | |
| Loading-state board description | `a11y_board_loading` | |
| `ic_add` (New game) | `ic_restart` (Material Symbols Rounded `restart_alt`) | DS §8.1 icon list |
| `## Review guidelines` | `## Code Review Rules` | D-192 |
| gate names `no-bom`, `icon-glyphs` | `check_no_bom`, `check_icon_glyphs` (M0) | QUALITY §1.16 |
| gate names `store-listing`, `store-images` | `check_store_listing`, `check_store_images` (M5) | |
| gate names `legal-files`, `privacy-policy` | `check_legal_and_privacy` (M5) | |
| gate name `legal-texts` (LICENSE / Apache sha256, `license_mit` == LICENSE) | `check_legal_texts` (M5) | New host |
| gate name `strings-pending-absent` | `check_strings_final` (M5) | New host |
| L10N script style `python3 -`, `fail=1` | QUALITY style `python3 -I -`, `err …` | |

### 2.5 Milestones and process terms

| Earlier name | Canonical | Notes |
|---|---|---|
| Milestone of `BoardUi.kt`, `BoardPresenter.kt`, `HapticsMap.kt`, `BoardPresenterTest`, `InputBurstOrderingTest` = M3 | M2 | D-211 |
| Milestone of `Formatting.kt`, `FormattingTest`, `SubScreenScaffold.kt` = M4 | M3 (the `FormattingTest` L-AC5 method in M5) | |
| Milestone of `ThemeCrossfadeTest`, `LayoutMotionTest` = M3 | M4 | |
| M3 as one commit | M3 part 1 and M3 part 2 (two pushed commits, subjects `M3: …`) | D-211 |
| “lead agent”, “WP agents” | orchestrator; milestone integrator; WP implementers | D-194 |
| draft PR, `--draft`, `gh pr ready` | PR opened ready for review (`draft = false`) after the M0 push | D-193 |
| `gh …` as the only form of a GitHub step | “via the GitHub API (MCP tool or gh)”, per QUALITY §6.8 | D-195 |
| “M6 reconcile”, “needs a D-173 entry” (for settled items) | “Resolved by D-NNN” or the decided text | Plan review (2026-10-28) |
| `<scratch-dir>` / sandbox absolute paths | relative sibling paths beside the checkout (`../2048-wt/<WP>`, `../2048-pr-body.md`, `../2048-clean`) or `$HOME/.cache/game2048/…` | |

### 2.6 Settled reconciliations

**A. Board epochs** (undo, new game, size switch, restore): D-182.

**B. Timing mechanism** (`MoveTimeline`, `delayedSpec`, no raw visible `delay()`): D-183.

**C. `check-metadata.sh`:** the M0 additions are D-179 and the M5 block is D-190; `QUALITY.md` §1.16 is the single canonical listing of functions and assertions.

**D. Milestone moves:** D-211. Per-class splits follow the task tables of §5: `SplashGateTest` F07-AC11–AC12 in M2 and F07-AC13 in M3; `RecreateNoReplayTest` F07-AC7–AC8 in M2, F07-AC9 and F07-AC26–AC30 in M3, F18-AC5 in M4; `LaunchSmokeTest.launchesOnMinAndTargetSdk` (F30-AC5, placeholder form) in M0, then `launchesIntoGameWithoutTopBar` (F21-AC1) and `noToastOnLaunchOrRecreate` (F22-AC7) in M3, when F30-AC5's assertion switches to the board; `SettingsScreenTest.appLanguageSummaryDefaultsToSystem` (L-AC6) and `ManifestPolicyTest.legacyLauncherIconOnApi24` (L-AC12) in M4.

**E. Seams adopted up front:** `SystemBarsController` behind `ImmersiveMode.apply`, an injectable `ContrastLevel` source, `AnimatorScaleSource` owned by `AppContainer`, each with a fake in tests.

**F. Root files and the mirror host.** `check_mirror_leak` fails on the sandbox mirror host in any tracked file outside `docs/`. `AGENTS.md`, `CLAUDE.md` and `README.md` therefore never spell the host; they point to `QUALITY.md` §3.3.

### 2.7 Naming acceptance check

```bash
grep -rnE 'onNewGameConfirmed|MotionSetting|RecordingHaptics|MotionFilmstripGoldenTest|TileLabelTest|testing/Fixtures|whats_new_30000|LocalMotionLevel|GameHaptics|TileAnimator|AnimatorScaleSource.kt|game/BoardView.kt|delayScaled|BoardLayoutTest|GameLayoutMode|TilePalettes.kt|GameButtons.kt|SettingsRows.kt|Review guidelines|PropTestConfig|RoborazziTaskType|gh pr ready' docs/modernization/*.md AGENTS.md CLAUDE.md
```

It prints only lines of this §2, of DECISIONS.md D-183, D-190, D-192 and Appendix B, and the fallback of DECISIONS.md verification-pending item 11. The M6.4 check runs it.

---

## 3. Entries added during planning

DECISIONS.md D-174–D-211 and Appendix B (TC-1–TC-7) were written during planning, before the plan PR merged. They are canonical, not deviations. Execution-time entries start at D-212 (D-173).

| ID | Title | Relation | Specified in |
|---|---|---|---|
| D-174 | ATF opt-in isolated to one test file | amends D-143 | QUALITY §0 V5, §1.12, §1.16 |
| D-175 | Spotless excludes `docs/**` | extends D-141 | ARCHITECTURE §2.5 |
| D-176 | Test-task inputs and no cached test results | extends D-146, D-147 | ARCHITECTURE §2.6, §2.7; QUALITY §2.6 |
| D-177 | Plan PR scope: workflow and agent files | refines D-151, D-162; clarifies D-160, D-165; extends D-171 | QUALITY §5.3, §5.9; root `AGENTS.md` |
| D-178 | `verify.sh` levels and local invocation | extends D-140, D-163 | QUALITY §3; §6.2 |
| D-179 | Check-script additions, M0 set | extends D-148, D-149, D-152 | QUALITY §1.13–§1.16; L10N §1.2, §3.4 |
| D-180 | No AI model identifiers in tracked files | clarifies D-164 | §6.4, §6.7 |
| D-181 | `ModalBottomSheet` opt-in isolated to `GameModalSheet.kt` | amends D-143 | DESIGN_SYSTEM §7.4 |
| D-182 | Epoch presentation of `Static` boards | clarifies D-025, D-064 | ARCHITECTURE §5.2 (`settled`), §5.5; MOTION_SPEC §3.8–§3.10 |
| D-183 | Motion tokens, timing mechanism and timeline extensions | extends D-060, D-061, D-062, D-063, D-064 | MOTION_SPEC §0–§3, §7 |
| D-184 | Level-dependent `MotionScheme`; platform dialog and snackbar motion | amends D-026; clarifies D-063 | MOTION_SPEC §2.2, §3.18 |
| D-185 | Haptic ownership, timing and interruption | extends D-065, D-066 | MOTION_SPEC §6; ARCHITECTURE §4.8, §5.9 |
| D-186 | Undo snackbars | extends D-039 | DESIGN_SYSTEM §5.4, §7.6 |
| D-187 | Effect layers and the "+N" pill | amends D-025; clarifies D-064 | DESIGN_SYSTEM §4.7, §7.2; MOTION_SPEC §3.3 |
| D-188 | Board layout amendments | amends D-051, D-075, D-091; clarifies D-009 | DESIGN_SYSTEM §5.2–§5.4 |
| D-189 | Theme token additions | extends D-070, D-071 | DESIGN_SYSTEM §2.2, §2.8 |
| D-190 | Store, legal and listing checks; opaque store PNGs | extends D-113, D-114, D-115, D-152 | QUALITY §1.16; L10N §2.5, §2.6 |
| D-191 | Reproducibility probe | extends D-140 | QUALITY §1.17 |
| D-192 | Codex review rules heading | clarifies D-171 | root `AGENTS.md`; QUALITY §6 |
| D-193 | Execution PR lifecycle: ready for review from M0 | refines D-162, D-165, D-166 | §6.4, §6.9 |
| D-194 | Roles: orchestrator and implementer agents | clarifies D-163, D-164, D-165, D-169 | §6.1, §6.4 |
| D-195 | GitHub operations through the GitHub API | clarifies D-165, D-166, D-170 | QUALITY §6.8; §6.8–§6.11 |
| D-196 | Codex rounds, clean signal, resolution and escalation | clarifies D-166, D-167, D-168, D-169, D-172 | QUALITY §6; §6.10–§6.12 |
| D-197 | Branch re-cut verification | clarifies D-161 | §6.3 |
| D-198 | Records and statistics readings | clarifies D-044 | FEATURES §3.1, F08 |
| D-199 | Controls and keys while a card is shown | clarifies D-007, D-054; amends D-053 | FEATURES F03, F16; DESIGN_SYSTEM §6.1 |
| D-200 | Overlay cards: fit to the board, button rendering, Share on the win card | extends D-008; clarifies D-064 | DESIGN_SYSTEM §5.3, §6.3, §6.3a, §6.4, §6.4a |
| D-201 | First-launch hint condition | amends D-009 | FEATURES F22 |
| D-202 | TalkBack status completeness | amends D-081 | FEATURES F17, F22; L10N §1.3 |
| D-203 | Keyboard shortcuts helper | extends D-053 | FEATURES F16 |
| D-204 | Golden matrix of 45 PNGs and filmstrip frame names | extends D-085, D-147 | DESIGN_SYSTEM §10.4; MOTION_SPEC §8.3 |
| D-205 | Contrast method and touch-target rule | clarifies D-072, D-083; extends D-084, D-085 | DESIGN_SYSTEM §2.13; QUALITY §1.12 |
| D-206 | High-contrast empty cells | extends D-073 | DESIGN_SYSTEM §2.2, §2.7 |
| D-207 | Classic dark 2-tile fill | amends D-070 | DESIGN_SYSTEM §2.4 |
| D-208 | Localization readings | clarifies D-100, D-102, D-103, D-142 | L10N_STORE_LEGAL §1 |
| D-209 | Night-mode idea credit | extends D-124 | L10N_STORE_LEGAL §4.5 |
| D-210 | Motion verification policy | refines D-147, D-150 | MOTION_SPEC §1, §8–§9 |
| D-211 | Milestone table refinements | refines D-162 | §4, §5 |

Technical corrections TC-1–TC-7 (of D-131, D-148, D-101, D-010, D-144, D-145 and D-064) are in DECISIONS.md Appendix B. M0 applies TC-1, TC-2, TC-4 and TC-5; M1 applies TC-6.

---

## 4. Milestones at a glance

| M | Commit subject(s) | Content (D-IDs) | WPs in parallel | Codex | Goldens recorded |
|---|---|---|---|---|---|
| M0 | `M0: replace legacy WebView shell with gated native skeleton` | Legacy removal, build layer, identity resources, signing, scripts, first golden (D-001, D-010–D-020, D-090, D-101, D-110–D-113 theme/icon/move, D-120, D-130–D-134, D-140–D-152 phase-in, D-171 (engine AGENTS.md); D-174–D-179; TC-1, TC-2, TC-4, TC-5) | WP M0-S (scripts, config, signing) | —; PR opened ready for review after the push (D-193) | `launch_placeholder` |
| M1 | `M1: implement pure 2048 engine with golden and property tests` | D-021, D-030–D-039, D-145 (TC-6) | M1-A, M1-B, M1-C | — | — |
| M2 | `M2: add DataStore persistence, game ViewModel and splash gate` | D-022–D-024, D-040–D-048, D-112 gate, presenter data side of D-025; `BoardUi`, `BoardPresenter`, `HapticsMap` (D-211) | M2-A, M2-B | **Mid checkpoint** (D-166 a) | — |
| M3 part 1 | `M3: add motion runtime, animated board, input and game screen` | D-007 dialog, D-025, D-050–D-053, D-060–D-066, D-076–D-078 (Classic), D-080, D-183–D-187, D-203 | A, B, C, D1, E1 (≤ 3 at once) | — | Rows 1, 3, 37–41, 43 |
| M3 part 2 | `M3: add overlays, board-size sheet, share and navigation` | D-008, D-023 nav, D-054, D-094, D-181, D-199, D-200 | D2, D3, E2 | — | Rows 13, 15, 42 |
| M4 | `M4: add screens, theming, adaptive layout and accessibility` | D-006, D-009, D-044 UI, D-047 arrangement scopes, D-070–D-085, D-091–D-095, D-122, D-124; D-186 placement, D-188, D-189, D-198, D-201, D-202, D-205–D-207, D-209 | M4-A … M4-D | — | Rows 1–22, 27–45 (re-record M3 rows) |
| M5 | `M5: add i18n glossary`; `M5: translate 20 locales and add store listing and legal files` | D-100–D-106, D-110, D-113–D-116, D-121, D-123, D-190, D-208 | M5-T1 … M5-T4 | — | Rows 23–26; 8 store images |
| M6 | `M6: harden gates and close out the native rewrite` | All D-148/D-152 assertions active; D-191; final Codex loop | — | Final loop (D-166 b, c; D-196) | Re-record only if a fix changes UI |

Every milestone ends with verify full passing on the exact pushed commit (D-163). A milestone is one commit, except M3, which is two pushed parts (D-211), and M5, which is two commits. Pushes follow the D-193 push gate.

---

## 5. Milestone details

Each milestone lists: objective; inputs; ordered tasks with the files they create (C), edit (E) or delete (D) and the spec sections they implement; work packages and ownership; tests; the verification gate; the commit message; exit criteria. File lists are complete for main and test sources; resource strings follow the `L10N_STORE_LEGAL.md` §1.3 inventory ("M" column). "Integrator" is the milestone integrator; tasks marked "orchestrator" are GitHub or push steps (§6.1).

### 5.0 M0 — Bootstrap

**Objective.** Remove the legacy shell and leave a buildable skeleton whose gates are identical locally and in CI, with identity resources, the public sideload key, every M0 check, one Robolectric smoke test, one golden, and the execution PR opened ready for review by the orchestrator.

**Inputs.** D-001, D-010–D-020, D-090, D-101, D-110–D-113, D-120, D-130–D-134, D-140–D-152, D-162 (M0 row), D-165, D-171, D-174–D-179, D-193, D-194; TC-1, TC-2, TC-4, TC-5; ARCH §1–§2, §8; QUALITY §1.9, §1.12–§1.16, §2, §3, §7.2 (M0); L10N §1.2, §2.1, §3, §4.2, §4.5, §4.9 (M0), §5.2.

**Tasks** (integrator, in order, except M0.1, which the orchestrator runs first, and M0.11–M0.12, which WP M0-S may do in parallel after M0.2):

| Task | Files | Spec |
|---|---|---|
| M0.1 Preflight (orchestrator) | none. Branch re-cut (§6.3, D-197); `git submodule update --init assets/2048` (the credit capture needs the submodule history `fc1ef4f..7a477eb`); `java -version` is 21; `ANDROID_HOME` exported, and `$ANDROID_HOME/platforms/android-37.0`, `build-tools/36.0.0` and `build-tools/37.0.0` exist; GitHub identity through the API is `clayboicardi` (MCP `get_me` or `gh api user`); `git push --dry-run origin HEAD:refs/heads/claude/2048-modernization-plan-8eneix` succeeds; sandbox mirror prepared if Maven Central returns 429 (`CLAUDE.md`) | §6.2 |
| M0.2 Read decisions | none. Confirm DECISIONS.md holds D-174–D-211 and Appendix B; no edit | D-173 |
| M0.3 Legacy capture | C `docs/modernization/legacy-credits.md` with the four sections of L10N §4.5 (commands and raw output, `f889036` credit, translator table, `res/raw/changelog_master.xml` verbatim) | D-124, D-209; L10N §4.5 |
| M0.4 Identity resources from the legacy glyphs (before `ic_2048.xml` is deleted) | C `app/src/main/res/drawable/ic_launcher_foreground.xml` (L10N §3.2 verbatim); `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` (§3.3); `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` (§3.4); `values/colors.xml` (`window_background #FAF8EF`, `ic_launcher_background #E6A11A`, `ic_launcher_glyph #3A2900`); `values-night/colors.xml` (`window_background #1B1916`); `values/themes.xml`, `values-night/themes.xml` (ARCH §8.3) | D-111, D-112 (theme only) |
| M0.5 String and listing migration | `git mv` `res/values*/strings.xml` → `app/src/main/res/values*/strings.xml` and the L10N §1.2 edit script, then its check (prints nothing); L10N §2.1 metadata commands (16 locales and `promoGraphic.png` deleted, `metadata` → `fastlane/metadata/android`, legacy images under `en-US/images/`) | D-101, D-113; L10N §1.2, §2.1 |
| M0.6 Legacy removal | D per ARCH §1.3: submodule (`git submodule deinit -f assets/2048 && git rm -f assets/2048 && rm -rf .git/modules/assets/2048 && git rm -f .gitmodules`), `src/`, root `AndroidManifest.xml`, `build.gradle`, `.travis.yml`, `res/` remainder (`layout`, `menu`, `values/styles.xml`, `values/dimens.xml`, `values-sw720dp-land`, `drawable/ic_2048.xml`, `raw/changelog_master.xml`) | D-001 |
| M0.7 Gradle wrapper | C/E `gradlew` (+x), `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (ARCH §2.4 exact) by the D-010 bootstrap as TC-4: in a new, empty temporary directory outside the repository (for example `$(mktemp -d)`), `touch settings.gradle.kts`, run `gradle wrapper` and then `./gradlew wrapper` with the same arguments in that directory; copy only `gradlew`, `gradlew.bat` and `gradle/wrapper/*` into the repository (not the temporary `settings.gradle.kts`); `git update-index --chmod=+x gradlew` | D-010, TC-4 |
| M0.8 Build layer | C `settings.gradle.kts` (ARCH §2.3), `build.gradle.kts` (§2.5, D-175), `gradle.properties` (§2.4), `gradle/libs.versions.toml` (D-016 byte-exact), `app/build.gradle.kts` and `core/engine/build.gradle.kts` (ARCH §2.6/§2.7, which already contain the D-176 inputs), `app/proguard-rules.pro` (§2.10), `.gitattributes` (§2.10), `app/src/main/res/resources.properties`, `app/src/test/resources/robolectric.properties` (`sdk=36`), `config/detekt/detekt.yml` (`./gradlew :core:engine:detektGenerateConfig`, then exactly the ARCH §2.8 edits; root-relative config path per TC-5); E `.gitignore` (§2.10) | D-011–D-017, D-019 hook, D-106, D-130–D-132, D-134, D-141–D-144, D-175, D-176, TC-5 |
| M0.9 App skeleton | C `app/src/main/AndroidManifest.xml` (ARCH §8.1), `app/src/main/res/xml/data_extraction_rules.xml`, `backup_rules.xml` (§8.2), `app/src/debug/res/values/strings.xml` (§8.5); `app/…/game2048/Game2048Application.kt` (empty subclass), `MainActivity.kt` (`installSplashScreen()` → `super.onCreate` → `enableEdgeToEdge()` → `setContent { App() }`), `App.kt` (placeholder: `GameTheme` + a centered `Text(stringResource(R.string.settings), color = MaterialTheme.colorScheme.onBackground)` with test tag `placeholder`; the explicit role is D-083's, because the theme provides no content color and nothing draws a background over the window), `ui/theme/GameTheme.kt` (minimal `MaterialExpressiveTheme(colorScheme = light/dark default scheme, motionScheme = MotionScheme.expressive())`) | D-023 order, D-048, D-090, D-110 |
| M0.10 Engine skeleton | C `engine/Direction.kt` (ARCH §3.2), `engine-test/DirectionTest.kt` (2 tests: round trip; `fromCode(4)` throws), `core/engine/AGENTS.md` (content below) | D-020, D-031, D-171, D-192 |
| M0.11 CI sideload key **[WP M0-S]** | C `scripts/signing/generate-ci-sideload-keystore.sh` (ARCH §2.9, with TC-1), run once → `signing/ci-sideload.p12`, `signing/ci-sideload.sha256`; C `signing/README.md` (the five items of L10N §5.2, in order) | D-131, TC-1 |
| M0.12 Gate scripts and config **[WP M0-S]** | C `scripts/verify.sh` (QUALITY §3.1), `scripts/check-apk.sh` (§1.13, with TC-2), `scripts/check-test-counts.sh` (§1.15), `scripts/check-licenses.sh` (§1.16), `scripts/check-metadata.sh` (the QUALITY §1.16 `[M0]` functions per D-179, including `check_no_bom` and `check_icon_glyphs`, written as functions with `err`), `config/test-count-floor.txt` (`engine=2`, `app=3`), `config/license-groups.txt` (L10N §4.4; the M0 `check-licenses.sh` output wins if it differs, listed in the commit body); all scripts and `gradlew` committed `100755` via `git update-index --chmod=+x` | D-140, D-148, D-149, D-152, D-174 (allowlist), D-178, D-179, TC-2 |
| M0.13 Test infrastructure and first golden | C `test/…/testing/Goldens.kt` (QUALITY §1.9), `test/…/testing/Accessibility.kt` (QUALITY §1.12, the D-174 opt-in), `test/…/LaunchSmokeTest.kt` (`@Config(sdk = [24, 37])`, one method `launchesOnMinAndTargetSdk`: the activity reaches RESUMED and the `placeholder` node shows "Settings", and its resolved text color reaches D-083's 4.5:1 against the window background in light mode and, after `RuntimeEnvironment.setQualifiers("+night")` and a recreation, in dark mode), `test/…/PlaceholderScreenshotTest.kt` (SDK 36, `atfRule`, `captureGolden("launch_placeholder")`, `assertTouchTargets()`), `app/src/test/screenshots/launch_placeholder.png` (recorded) | D-085, D-146, D-147, D-174 |
| M0.14 Legal and README | E `LICENSE` (L10N §4.2 text exactly: 1,151 bytes, sha256 `ad96d837d59c50cb3c8e23a17d7ee19a396c5a1a6c4bc952a269896fcbdbfd19`); E `README.md` (L10N §4.9 M0: remove the Travis badge and the `com.uberspot.a2048` badges; "Building" becomes "Build with JDK 21: `./scripts/verify.sh`") | D-116 (M0 part), D-120 |
| M0.15 Gate and hand off | §5.0 gate; local commit; handoff (D-194); the orchestrator pushes, opens the PR ready for review (§6.9) and confirms CI (§6.8) | D-163, D-165, D-193, D-194 |

`core/engine/AGENTS.md` content (D-171, D-192; concise per research/07 G20):

```markdown
# AGENTS.md — `:core:engine`

Pure Kotlin 2048 rules. Kotlin stdlib only: no Android, kotlinx, clock or platform RNG.

## Code Review Rules

### Game rules
- Flag any change to these semantics (DECISIONS.md D-030–D-039), which are legacy parity and are pinned by
  golden tests: lines are processed from the wall the move goes toward; a tile merges at most once per move;
  score grows by exactly each merged value; a no-op move draws no random number and changes nothing; after an
  accepted move exactly one tile spawns, value first (4 when `nextInt(10) == 0`) then a uniform empty cell in
  row-major order; the win is reported once, on the exact target (256 on 3×3, else 2048); game over is
  evaluated after the spawn; undo restores the RNG state, so the same move gives the same spawn.
- Flag clock reads, `kotlin.random`, `java.util.Random`, `Math.random`, `SecureRandom`, `java.time`,
  `android.*` or `kotlinx.*` imports in this module. The safe path is `SplitMix64` with a stored seed.
- Flag changes to `MoveLog` or `Snapshot` byte layouts, or to `GameStatus` order, without a `schemaVersion`
  bump and a `DataMigration` in `:app` (D-041, D-042).
- Changing a golden vector needs a DECISIONS.md D-173 entry.
```

**Work packages.**

| WP | Owner | Files (exclusive) | Starts after | Hands back |
|---|---|---|---|---|
| M0-S | WP implementer S | `scripts/**`, `signing/**`, `config/test-count-floor.txt`, `config/license-groups.txt` | M0.2 | The scripts, the keystore, its digest; `bash -n` on every script; `shellcheck` if available; the `wip/M0-S` tip SHA (§6.5) |
| (integrator) | milestone integrator | everything else | — | — |

The integrator merges M0-S before M0.13. Generate the keystore exactly once (it is random); never regenerate it after it is committed.

**Verification gate.**
1. `./scripts/verify.sh --record`, then Read `launch_placeholder.png` (expected: the placeholder text centered on the Classic background, no clipping).
2. `./scripts/verify.sh` → PASS.
3. Extra checks, outputs pasted into the commit body: `check-apk.sh` badging lines for both APKs; `git submodule status` (empty); `git ls-files | grep -E '^(src|res|metadata|assets)/'` (empty); `ls fastlane/metadata/android | wc -l` = 13; `test ! -e metadata`; the L10N §1.2 string check (prints nothing); `signing/README.md` has the five items (L-AC14). In a throwaway clone beside the checkout (`../2048-seeded`), seed one defect per new check function (QUALITY V14–V15) and, for `check-test-counts.sh`, run each seed S1–S7 of QUALITY §1.15 (one per rule: floor, skip, G1–G5; S4 deletes the `captureGolden("launch_placeholder")` call while `PlaceholderScreenshotTest` keeps its method, so `app=3` still holds and G1 must fail with "no Roborazzi comparison results"), run the script, paste each failing line into the commit body, delete the clone; never commit a seeded defect (D-179).
4. After the orchestrator's push and PR creation: CI run appears within 5 minutes, its Detect step reports `present=true`, `verify` is green (this also proves local == CI goldens, verification-pending item 1).

**Commit.**

```text
M0: replace legacy WebView shell with gated native skeleton

D-IDs: D-001, D-010–D-020, D-090, D-101, D-110–D-113, D-120, D-130–D-134,
D-140–D-152 (M0 phase-in), D-171 (engine file), D-174–D-179; TC-1, TC-2, TC-4, TC-5.
Gates: scripts/verify.sh passed (engine=2 tests, app=3 tests)
Screenshots: launch_placeholder.png (new): "Settings" centered on #FAF8EF; nothing clipped.
<check-apk badging lines, exit-check outputs, seeded-defect failing lines>

<trailer lines exactly as supplied by the execution environment>
```

**Exit criteria.** QUALITY §7.2 M0 row; ARCH §1.2; all M0 checks active; PR open (ready for review) with the §6.9 body; CI green on the M0 head.

### 5.1 M1 — Engine

**Objective.** The complete pure engine of D-030–D-039 with golden vectors, 13 properties, history codecs, invariants and coverage.

**Inputs.** D-021, D-030–D-039, D-145 (TC-6), D-150; ARCH §3 (all), §10.1–§10.2; QUALITY §1.2–§1.4, §4; FEATURES F01, F03 (engine ACs), F05 (engine ACs), F07-AC20–AC21.

**Tasks.**

| Task | Owner | Files | Spec |
|---|---|---|---|
| M1.1 Engine contracts | integrator | C every main file of ARCH §10.1 except `Direction.kt` with the public declarations of ARCH §3.2 and the internal `Core`, `SlideResult`, `slide`, `spawnInto`, `step` signatures of §3.3; bodies `TODO("M1")`. Local `wip(M1)` commit on the execution branch (never pushed; squashed into the milestone commit) | ARCH §3.2–§3.3 |
| M1.2 Engine implementation | WP M1-A | E `engine/GameStatus.kt`, `BoardSize.kt`, `Board.kt`, `SplitMix64.kt`, `MoveLog.kt`, `Snapshot.kt`, `GameState.kt`, `MoveOutcome.kt`, `Slide.kt`, `Spawner.kt`, `Game.kt`, `GameInvariants.kt` | ARCH §3.2–§3.7; D-038 pseudocode; `tileValue` saturating (FEATURES OI-F6 needs `BigInteger` only in `:app`); named private constants for shifts, packing and snapshot offsets (ARCH §3.2–§3.3); no `MagicNumber` suppression |
| M1.3 Golden, vector and state tests | WP M1-B | C `engine-test/EngineGoldenTest.kt` (`rowLeftGoldens`, `rowRightAndVerticalGoldens`, `largerBoards`, `noOpDrawsNothing`, `winRows`), `SplitMix64GoldenTest.kt`, `EngineReplayGoldenTest.kt`, `HistoryCodecTest.kt` (QUALITY §1.3 golden bytes), `StatusMachineTest.kt` (incl. `blockedInWonAndOver`), `UndoTest.kt` | QUALITY §1.2–§1.3; FEATURES F01-AC1, AC2, AC13–AC18, F02-AC1–AC2, F03-AC1–AC5, AC7, F04-AC8, F05-AC1, AC3–AC7, F07-AC21 |
| M1.4 Property, distribution, guard, performance | WP M1-C | C `engine-test/Arbs.kt` (generators and `Arbs.ITERATIONS = 1_000`), `EnginePropertiesTest.kt` (`p1SumConservedPlusSpawn` … `p13SpawnLandsOnEmptyCell`, FEATURES names), `SpawnDistributionTest.kt`, `EngineImportsGuardTest.kt`, `EnginePerformanceTest.kt`. Property seeding per TC-6: `@BeforeEach` sets `PropertyTesting.defaultSeed = 0x2048L` and `PropertyTesting.writeFailedSeed = false`; properties call `checkAll(Arbs.ITERATIONS, …)` | QUALITY §1.4; FEATURES F01-AC3–AC12, AC19–AC20, F03-AC6, F05-AC2, F07-AC20; D-145, TC-6 |
| M1.5 Integrate | integrator | E `config/test-count-floor.txt` | §6.5 |

M1-B and M1-C write tests against the M1.1 contracts in parallel with M1-A. Expected values come only from DECISIONS D-032/D-038, ARCH §3.6 and QUALITY §1.3; if a test and the implementation disagree, re-derive from ARCH §3.3 before touching either (a vector changes only through D-173).

**Verification gate.** verify quick while integrating; then verify full PASS. Record in the commit body: Kover line and branch percentages (`./gradlew :core:engine:koverHtmlReport`), `EnginePerformanceTest` time, engine test count.

**Commit.** `M1: implement pure 2048 engine with golden and property tests`; body D-IDs D-021, D-030–D-039, D-145 (TC-6), D-150; `Screenshots: none`.

**Exit criteria.** QUALITY §7.2 M1 row; every engine test class of FEATURES §8 exists; `GameInvariants.violations` asserted empty by every state-producing test; floors set.

### 5.2 M2 — State and persistence

**Objective.** Typed DataStore persistence, manual DI, the state-first `GameViewModel`, records arithmetic, the splash gate, and the irreversible on-disk format, frozen by write-once fixtures and reviewed by Codex.

**Inputs.** D-022–D-025 (data side), D-040–D-048, D-112, D-182 (presenter), D-185 (`HapticsMap`), D-201, D-211; ARCH §4 (all), §5.1–§5.2, §9; QUALITY §1.5–§1.7, §1.11; FEATURES F01-AC21–AC22, F02-AC3–AC6, AC11–AC12, F03-AC8–AC10, AC15, F04-AC1, AC9–AC12, F05-AC8–AC9, AC14–AC16, F06-AC7–AC9, AC11, AC13–AC23, F07, F08-AC1–AC8, AC16, AC19, AC20, AC22, F09-AC1–AC4, F11-AC10–AC11, F14-AC1, F15-AC13, F22-AC1–AC4, F22-AC10, F29, F30-AC10–AC11 (D-134 version source).

**Tasks.**

| Task | Owner | Files | Spec |
|---|---|---|---|
| M2.1 Contracts, models, harness | integrator | C `data/settings/Settings.kt`, `data/records/Records.kt`, `data/records/Finalization.kt`, `data/game/GameDocument.kt`, `data/game/SavedGame.kt`, `game/GameUiState.kt`, `game/board/BoardUi.kt` (full; models are data), repository class signatures with `TODO` bodies (`data/settings/SettingsRepository.kt`, `data/records/RecordsRepository.kt`, `data/game/GameRepository.kt`), `di/AppContainer.kt` signature; C `test/…/testing/MainDispatcherRule.kt` (the D-215 opt-in), `TestAppContainer.kt`, `FakeClock.kt`, `FixedSeedSource.kt`, `test/…/fixtures/GoldenFixtures.kt` (`FIX_MID_4`, `FIX_MID_3`, `FIX_OVER_4`, `FIX_RECORDS` and `fixtureGame`, D-216) | ARCH §4.2–§4.7, §5.1; D-215, D-216 |
| M2.2 Data layer and persistence tests | WP M2-A | C/E `data/AppJson.kt`, `JsonDocumentSerializer.kt`, `ConflatedWriter.kt`, `DocumentHolder.kt`, `DataStoreFiles.kt`, `data/platform/MonotonicClock.kt`, `data/platform/SeedSource.kt`, `data/settings/SettingsRepository.kt`, `data/records/RecordsMath.kt`, `data/records/RecordsRepository.kt`, `data/game/GameDocumentMapper.kt`, `data/game/GameDocumentSerializer.kt`, `data/game/GameRepository.kt`, `di/AppContainer.kt` (`create` passes `versionCode = BuildConfig.VERSION_CODE`; no `PackageManager`, D-134); tests `data/PersistenceFixturesTest.kt`, `PersistenceCorruptionTest.kt`, `PersistenceBudgetTest.kt`, `ConflatedWriterTest.kt`, `SettingsRepositoryTest.kt`, `RecordsMathTest.kt`, `DataMigrationSeamTest.kt`, `LongGameGeneratorTest.kt`, `test/…/testing/LongGameGenerator.kt`, `BackupRulesTest.kt`, `di/AppContainerTest.kt` (`createDoesNotTouchPackageManager` F30-AC10, `versionComesFromBuildConfigStandardFields` F30-AC11) | ARCH §4.2–§4.6, §4.9 `RecordsMath` and finalization protocol (identity-guarded `finalize`, `reset`, `nextGameId`, `RecordsRepository.recover`/`isFinalized`/`isDurable`/`retryFailedWrite`, the §4.4 identity checks and 16-entry cap, the §4.5 durable reports and `retryFailed()` of `ConflatedWriter` and `DocumentHolder`), §9; QUALITY §1.5–§1.6; FEATURES F07, F08-AC1–AC7, AC19–AC20, F11-AC10–AC11, F29, F30-AC10–AC11; D-044, D-134 |
| M2.3 ViewModel, presenter, splash gate, state tests | WP M2-B | C `game/GameViewModel.kt`, `game/PlayClock.kt`, `game/board/BoardPresenter.kt`, `game/feedback/HapticsMap.kt`, `SplashGate.kt`, `di/ViewModelFactories.kt` (`gameViewModelFactory`); E `Game2048Application.kt` (creates `AppContainer`), `MainActivity.kt` (activity-scoped `GameViewModel`, `SplashGate` keep condition, ARCH §4.12); tests `game/GameViewModelTest.kt` (incl. `hintShownWhileStartingGameHasNoMove` F22-AC2 and `hintHiddenAfterAMove` F22-AC10, D-201; `presentationReportsSettleWithoutStateChange` F07-AC24, D-047; `headerCarriesBoardEventAndEpoch` F13-AC14, D-062; `newGameDropsOnlyWrittenEntries` F06-AC23, D-044; `engineCheckFailureIsCorruption` F07-AC32, D-214), `game/InputBurstOrderingTest.kt`, `game/PlayClockTest.kt`, `game/board/BoardPresenterTest.kt` (incl. `settledKeepsIdentityAndIsStatic` F07-AC25), `game/feedback/HapticsMapTest.kt` (F14-AC1 `priorityResolution`), `ProcessDeathRestoreTest.kt`, `FinalizationRecoveryTest.kt` (F06-AC14–AC22, F08-AC22), `test/…/testing/FaultyDataStore.kt`, `SplashGateTest.kt` (`firstBoardEqualsPersistedFixture`, `gateReleasesAtCapOrWhenReady`), `RecreateNoReplayTest.kt` (`recreateRendersStaticBoard`, `pauseResumeDoesNotReplay`) | ARCH §4.7–§4.12 (incl. the §4.9 finalization protocol: `replaceWithFresh`, `restore`, `recoverOtherSizes`; the §4.11 presentation reports and `BoardPresenter.settled`), §5.1–§5.2; QUALITY §1.11; D-044; D-182 presenter id rule; D-185; D-201 |
| M2.4 Write-once fixtures | integrator | C `app/src/test/resources/fixtures/v1/settings.json`, `records.json`, `game-4.json`, `game-8-long.json` by the ARCH §9.4 protocol (run → copy `app/build/generated-fixtures/*` → re-run); contents exactly QUALITY §1.5 | D-041; QUALITY §1.5 |
| M2.5 Integrate and hand off | integrator | E `config/test-count-floor.txt` | §6.4–§6.5 |
| M2.6 Codex mid checkpoint (orchestrator, §6.10) | orchestrator | none (PR comments) | §6.10; D-166 a, D-196 |

Ownership exceptions for this milestone (§6.5): M2-A owns `di/AppContainer.kt` and `test/…/testing/LongGameGenerator.kt`; M2-B owns `di/ViewModelFactories.kt`, `MainActivity.kt` and `Game2048Application.kt`. Integration order: A → B.

`App.kt` stays the M0 placeholder through M2 (no UI change, no goldens).

**Verification gate.** verify full PASS. Commit body records `PersistenceBudgetTest` sizes and median times, and the fixture sha256 values.

**Commit.** `M2: add DataStore persistence, game ViewModel and splash gate`; body D-IDs D-022–D-025 (data side), D-040–D-048, D-112, D-182, D-185 (`HapticsMap`), D-201, D-211; `Screenshots: none`.

**Exit criteria.** QUALITY §7.2 M2 row; `InputBurstOrderingTest` passes; fixtures committed and never edited afterwards; the mid-checkpoint loop has ended per §6.10 before M3 part 1 is pushed.

### 5.3 M3 — Board, input, motion, haptics, game screen

**Objective.** The animated N×N board with stable tile ids and zero-recomposition slides, swipe and keyboard input with no queue, haptics, the Game screen with header, controls, cards, dialog, size sheet, share and navigation to stub sub-screens; motion verified numerically and by golden filmstrip. M3 is two gated, pushed commits (D-211).

**Inputs.** D-007, D-008, D-023, D-025, D-047 (one-shot presentations), D-050–D-054, D-060–D-066, D-076–D-078 (Classic only; GSF font arrives in M4, numerals use the `FontFamily.Default` Bold `tnum` fallback until then), D-080 (board), D-104 (board subtree), D-181, D-183–D-187, D-199, D-200, D-203, D-204, D-210, D-211; ARCH §4.11, §5–§7; MOTION_SPEC (all); DS §2.1–§2.4, §2.10, §3.3–§3.4, §4, §6.1, §6.3–§6.7, §7.1–§7.9, §8.1; FEATURES F02-AC7–AC10, AC13–AC14, F03-AC11–AC17, F04-AC2–AC7, AC13, F05-AC10–AC13, F06-AC1–AC6, AC10, AC12, F07-AC26–AC30, F09-AC5–AC6, F10, F13, F14, F15, F16, F17-AC1–AC6, F21; L10N §1.3 rows with M = M3.

At most three WPs run at once in either part. Event frames and frame tables are binding; curve values follow D-210; harness plumbing may be fixed with a note in the commit body.

#### M3 part 1 — motion runtime, animated board, input, game screen shell

**Tasks.**

| Task | Owner | Files (main) | Tests | Spec |
|---|---|---|---|---|
| M3.1 Contracts and shared infra | integrator | C `ui/theme/GameMotion.kt` (MS §1.5 listing verbatim plus `MotionPlan` and `GameMotion.plan(level)` signatures), `ui/theme/TilePalette.kt` (complete), `ui/theme/GameDimens.kt` (complete: every DS §4.1 and §5 token M3 uses), `ui/theme/Shapes.kt`, `ui/theme/EffectiveMotion.kt` (API: `AnimatorScaleSource`, `SystemAnimatorScaleSource`, `effectiveMotion()` signature, `LocalEffectiveMotion`), `game/motion/DelayedSpec.kt` (complete), `game/motion/MoveTimeline.kt` (API), `game/motion/MotionProbe.kt` (API), `game/feedback/HapticsController.kt` (API), `game/board/Board.kt` (`BoardView` signature), `game/input/SwipeDetector.kt` (`Modifier.swipeToMove` signature), `NavKeys.kt` (complete, ARCH §7.1); E `di/AppContainer.kt` (wires `AnimatorScaleSource`); C `app/src/main/res/values/strings_pending.xml` with empty regions `M3-B`, `M3-C`, `M3-D1`, `M3-D2`, `M3-D3` (L10N §1.4 header) | C `test/…/fixtures/GoldenFixtures.kt` (DS §10.3), `test/…/testing/FakeHapticFeedback.kt`, `motion/DelayedSpecTest.kt` (value and velocity at play times 0, delay − 1 ns, delay and delay + 16 ms, against the plain spec shifted by the delay) | §2; MS §1.5, §2.1, §2.4, §8.2; DS §4.1, §5; D-183 |
| M3.2 Theme tokens and motion runtime | WP M3-A | E `ui/theme/GameMotion.kt` (bodies), `ui/theme/EffectiveMotion.kt` (bodies); C `ui/theme/GameMotionScheme.kt`, `ClassicRefinedPalette.kt`, `GameNavTransitions.kt`; E `ui/theme/GameTheme.kt` (palette and skin locals, `LocalEffectiveMotion`, `gameMotionScheme(level)`, Classic chrome only); C `game/motion/FlashLimiter.kt`, `ParticleRandom.kt`, `ConfettiSystem.kt`, `ParticleDraw.kt`; E `game/motion/MoveTimeline.kt`, `MotionProbe.kt` (bodies) | C `ui/theme/MotionPlanTest.kt`, `motion/MotionTokensTest.kt`, `motion/ConfettiSystemTest.kt` | MS §1–§2, §3.11 confetti, §7.3; DS §2.1–§2.4, §2.8 (Classic), §4.4; D-183, D-184, D-207 |
| M3.3 Board rendering and animation | WP M3-B | C `game/board/BoardGeometry.kt`, `TileLabels.kt`, `TileTextCache.kt`, `TileView.kt`, `BoardAnimator.kt`, `BoardEffects.kt`, `EffectsCanvas.kt`, `RecompositionCounter.kt`; E `game/board/Board.kt` (body, including the D-183 frame-rate vote, the `onPresented` report at the origin and the first-composition nudge rule of ARCH §4.11), `game/board/BoardPresenter.kt` (undo id retention, D-182); strings in region `M3-B` (`a11y_board_description`, `a11y_board_loading`, `a11y_tiles`, `a11y_cell_tile`, `a11y_cell_empty`, `move_up`, `move_down`, `move_left`, `move_right`) | C `game/board/BoardGeometryTest.kt` (`classicGeometryAllSizes`, `sideMarginRespectsGestureInsets`), `TileLabelsTest.kt`, `TileRecompositionTest.kt`, `BoardSemanticsTest.kt` (F17-AC1–AC6) | ARCH §5.1–§5.7; MS §3.1–§3.10, §3.13, §4, §7; DS §3.3–§3.4, §4.2–§4.7; D-182, D-183, D-187 |
| M3.4 Input and haptics | WP M3-C | E `game/input/SwipeDetector.kt` (body); C `game/input/SwipeClassifier.kt`, `KeyboardInput.kt`; E `game/feedback/HapticsController.kt` (body; presented inputs and the first-composition nudge rule of ARCH §4.11) | C `game/input/SwipeClassifierTest.kt`, `SwipeInputTest.kt`, `KeyboardInputTest.kt` (incl. `providesShortcutGroup`; `ignoredWhileModalOpen` for the new-game dialog) | ARCH §4.11, §6.1–§6.6; MS §6; FEATURES F15, F16, F19-AC6; D-185, D-203 |
| M3.5 Game screen shell | WP M3-D1 | C `game/GameScreen.kt` (each flow collected once; the screen scope's `rememberPresented` states and, until M4.3, the one arrangement's, ARCH §4.11 item 2), `game/PresentedState.kt`, `GameControls.kt`, `game/header/GameHeader.kt` (incl. the `onHeaderPresented` report and the removals keyed by `HeaderUi.event` and `epoch`, ARCH §4.11 item 4), `ScoreOdometer.kt`, `ScoreFloats.kt`, `NewBestBadge.kt`, `game/overlay/NewGameDialog.kt`, `ui/components/AppButtons.kt`, `ScoreCard.kt`, `SizeChip.kt`, `Dialogs.kt`, `GameSnackbar.kt`, `FocusRing.kt`, `MiniTile.kt`, `Formatting.kt`; E `App.kt` (`GameTheme` + `GameScreen`; `App.kt` is assigned to D1 in part 1), `game/GameViewModel.kt` (only if a contract gap appears; report it); C icons `res/drawable/ic_undo.xml`, `ic_restart.xml`, `ic_grid_view.xml`, `ic_expand_more.xml` (DS §8.1 download and normalization); strings in region `M3-D1` (the L10N §1.3 rows with M = M3 that its files use) | C `game/GameScreenTest.kt` (part-1 ACs of FEATURES §8, incl. `undoUsedSnackbarUntilFirstFinalizedUndoGame`, `nothingToUndoSnackbarRateLimited`), `ui/components/FormattingTest.kt`, `ui/components/MiniTileTest.kt`, `screenshots/GameScreenshotTest.kt` (rows 1, 3, 43); E `LaunchSmokeTest.kt` (+`launchesIntoGameWithoutTopBar`, +`noToastOnLaunchOrRecreate`), `SplashGateTest.kt` (+`lateLoadRendersStaticTiles`); D `PlaceholderScreenshotTest.kt`, `app/src/test/screenshots/launch_placeholder.png` | ARCH §4.8, §4.11, §6.6; DS §6.1, §6.5, §7.1–§7.3, §7.5–§7.8; MS §3.4–§3.7, §3.14, §3.18, §4.2; FEATURES F02, F05, F06, F09; D-007, D-047, D-062, D-186, D-187 |
| M3.6 Board and score motion tests, filmstrip | WP M3-E1 | none | C `fixtures/MotionFixtures.kt`, `motion/MotionHarness.kt` (incl. `recreate()`, MS §8.1), `motion/TimelineOriginTest.kt`, `MoveTimelineTest.kt`, `MergePopTest.kt`, `SpawnDelayTest.kt`, `ScoreMotionTest.kt` (incl. `resetsRemoveScoreFloats`, F13-AC15, AC-SCORE-5), `NewBestTest.kt`, `InvalidNudgeTest.kt`, `NewGameCascadeTest.kt`, `UndoCrossfadeTest.kt`, `SizeSwitchTest.kt`, `RestoreNoMotionTest.kt` (AC-RESTORE-1, AC-RESTORE-2; board cases), `InterruptionTest.kt`, `ConsistencyPropertyTest.kt`, `FinalFrameEqualsStaticTest.kt`, the board methods of `ReducedMotionTest.kt`, `MotionOffTest.kt` and `AnimatorScaleTest.kt`, `BoardRecompositionBudgetTest.kt`, `ui/theme/EffectiveMotionTest.kt`, E `game/feedback/HapticsMapTest.kt` (move haptics and `invalidMoveSilentBelowApi30`), C `screenshots/MotionFilmstripTest.kt` (rows 37–41) | MS §8–§9; FEATURES F13-AC1–AC4, AC8, AC11, AC15, F14; D-062, D-185, D-204, D-210 |
| M3.7 Integrate part 1, record, Read, hand off | integrator | E `MainActivity.kt` (`onProvideKeyboardShortcuts`, D-203), `config/test-count-floor.txt`, `strings_pending.xml` (regions only) | — | §6.4–§6.5 |

Start A, B and C first; D1 and E1 take the next free slots. M3-E1 writes its tests from the MOTION_SPEC numbers and the contracts alone. Integration order: A → B → C → D1 → E1.

**Verification gate (part 1).**
1. verify full while integrating, fixing failures.
2. `./scripts/verify.sh --record`; Read every new golden (rows 1, 3, 37–41, 43) against QUALITY §1.9's checklist; check rows 37–41 against MS §8.3's frame table.
3. Read every inspection filmstrip of MS §8.4 under `app/build/reports/filmstrips/` once (D-210) and list them in the commit body (`Screenshots:` section).
4. verify full PASS. Record the elapsed time (QUALITY §3.2 projection: replace the estimate with the measurement).

**Commit (part 1).** `M3: add motion runtime, animated board, input and game screen`; body D-IDs D-007 (dialog), D-025, D-050–D-053, D-060–D-066, D-076–D-078 (Classic), D-080, D-104 (board subtree), D-183–D-187, D-203; the screenshots list.

**Exit criteria (part 1).** Every MOTION_SPEC AC of the part-1 classes passes; `TileRecompositionTest` and `BoardRecompositionBudgetTest` pass; `PlaceholderScreenshotTest` and its PNG deleted.

#### M3 part 2 — overlays, board-size sheet, share, navigation

**Tasks.**

| Task | Owner | Files (main) | Tests | Spec |
|---|---|---|---|---|
| M3.8 Contracts | integrator | C `game/overlay/OverlayController.kt` (API, including `interactive`), `ui/components/GameModalSheet.kt` (signature), `AppNavHost.kt` (signature) | — | MS §3.11–§3.13; DS §7.4; ARCH §7; D-181, D-200 |
| M3.9 Overlays, sheet, share | WP M3-D2 | C `game/overlay/WinCard.kt`, `GameOverCard.kt`, `BoardScrim.kt`, `BoardSizeSheet.kt`, `game/share/ShareText.kt`, `ui/components/Intents.kt`; E `game/overlay/OverlayController.kt` (incl. the `onOverlayPresented` report, ARCH §4.11, §5.8), `ui/components/GameModalSheet.kt` (bodies), `game/GameScreen.kt` (overlay host, size-chip sheet and share call sites); C icons `ic_share.xml`, `ic_check.xml`; strings in region `M3-D2` | E `game/GameScreenTest.kt` (overlay ACs, incl. `winCardFitsBoard`, `gameOverCardFitsBoard`, `cardButtonsNeverRenderDisabledColors`, `shareOnBothCardsNotInMenu`), `game/input/KeyboardInputTest.kt` (+`cardKeysActLikeChrome`, F16-AC11; `ignoredWhileModalOpen` extended to the sheet), `RecreateNoReplayTest.kt` (+`openSheetSurvivesRecreation`; under `MotionHarness.recreate()` +`recreateAfterMoveRendersFinalFrame`, +`recreateMidMoveSnapsToFinalFrame`, +`recreateDuringAndAfterWinShowsRestoredCard`, +`recreateDuringAndAfterGameOverShowsRestoredCard`, +`recreateAfterInvalidMoveDoesNotNudge`, F07-AC26–AC30), `screenshots/GameScreenshotTest.kt` (rows 13, 15, 42); C `game/share/ShareTextTest.kt` | ARCH §4.11; DS §6.3–§6.4a, §6.7, §7.4; MS §3.11–§3.14, §3.18; FEATURES F03, F04, F07-AC26–AC30, F10; D-008, D-047, D-054, D-181, D-199, D-200 |
| M3.10 Navigation | WP M3-D3 | E `AppNavHost.kt` (body); C `game/GameMenu.kt`, `ui/components/SubScreenScaffold.kt`, stubs `settings/SettingsScreen.kt`, `stats/StatsScreen.kt`, `about/HowToPlayScreen.kt`, `about/AboutScreen.kt`, `about/LicensesScreen.kt`; E `App.kt` (`AppNavHost`), `game/GameControls.kt` (More-menu call site); C icons `ic_more_vert.xml`, `ic_arrow_back.xml`, `ic_bar_chart.xml`, `ic_settings.xml`, `ic_help.xml`, `ic_info.xml`; strings in region `M3-D3` | C `BackOnRootTest.kt`; E `game/GameScreenTest.kt` (+`menuOpensEveryDestination`) | ARCH §7; MS §3.17, §3.19; FEATURES F21; D-023, D-094 |
| M3.11 Overlay and navigation motion tests | WP M3-E2 | none | C `motion/WinSequenceTest.kt`, `CardExitTest.kt`, `GameOverSequenceTest.kt`, `NavTransitionsTest.kt`, `PredictiveBackTest.kt`; E the overlay methods of `ReducedMotionTest.kt`, `MotionOffTest.kt`, `AnimatorScaleTest.kt` and `game/feedback/HapticsMapTest.kt`, and the card cases of `motion/RestoreNoMotionTest.kt` (AC-RESTORE-1's card end state, AC-RESTORE-2's `FIX_WIN_PRE_4` case) | MS §3.11–§3.14, §3.17–§3.19, §8–§9; D-047, D-210 |
| M3.12 Integrate part 2, record, Read, hand off | integrator | E `config/test-count-floor.txt`, `strings_pending.xml` (regions only) | — | §6.4–§6.5 |

In part 2, `game/GameScreen.kt` belongs to M3-D2, and `game/GameControls.kt` and `App.kt` belong to M3-D3. M3-D2 and M3-D3 both add methods to `game/GameScreenTest.kt`; the integrator merges D2 first and applies D3's method by hand if the squash merge conflicts. Integration order: D2 → D3 → E2.

**Verification gate (part 2).**
1. verify full while integrating, fixing failures.
2. `./scripts/verify.sh --record`; Read rows 13, 15 and 42 and every other golden whose bytes changed.
3. Read only the inspection filmstrips whose events changed in part 2 (D-210) and list them in the commit body.
4. verify full PASS.

**Commit (part 2).** `M3: add overlays, board-size sheet, share and navigation`; body D-IDs D-008, D-023 (nav), D-054, D-094, D-181, D-199, D-200; the screenshots list.

**Exit criteria (part 2).** QUALITY §7.2 M3 row; every MOTION_SPEC AC except AC-THEME-1, AC-LAYOUT-1–AC-LAYOUT-3 (they need M4's `GameLayout`), AC-HAP-4 and AC-SCALE-4 passes; the D-181 opt-in is the only `@OptIn` in `app/src/main`.

### 5.4 M4 — Screens, theming, adaptive layout, accessibility

**Objective.** Complete theming (Neon, high contrast, dynamic chrome, the Google Sans Flex numeral subset), adaptive layouts, the TalkBack pad and status line, Immersive mode, keep-screen-on, Settings, Statistics, How to play, About, Licenses, What's new and the first-launch hint, with the golden matrix minus locales.

**Inputs.** D-006, D-009, D-044 (UI), D-047 (arrangement presentation scopes), D-070–D-085, D-090–D-095, D-102 (row), D-122, D-124, D-186 (placement), D-188, D-189, D-198, D-201, D-202, D-204–D-207, D-209; ARCH §4.11 item 2, §4.13, §6.2, §6.4–§6.5, §8.6; DS §2–§9, §10; MS §3.5, §3.15–§3.16; FEATURES F08-AC9–AC18, AC21, F11, F12, F14-AC8, F17-AC7–AC24, F18, F19, F20, F22-AC5–AC9, F23, F24, F25, F26-AC1–AC4, AC6, F27-AC4, AC6, F28 (non-translation ACs), F30-AC6–AC7; L10N §1.3 rows with M = M4, §1.13, §4.4, §4.5.

**Tasks.**

| Task | Owner | Files (main) | Tests | Spec |
|---|---|---|---|---|
| M4.1 Contracts and wiring | integrator | E `di/ViewModelFactories.kt` (`appViewModelFactory`, `settingsViewModelFactory`, `statsViewModelFactory`, `aboutViewModelFactory`), `App.kt` (collect `AppViewModel.appearance`, provide `LocalHapticsEnabled`, What's new host), `MainActivity.kt` (`ImmersiveMode.apply` on focus); C `AppViewModel.kt` signature; E `ui/theme/GameDimens.kt` (M4 tokens); C `ui/theme/ContrastLevel.kt` and `ui/components/ImmersiveMode.kt` with their interface declarations (the `ContrastLevel` source, `SystemBarsController`); E `di/AppContainer.kt` (wiring of the `ContrastLevel` source and `SystemBarsController`); E `strings_pending.xml` (regions `M4-A`…`M4-D`; new strings per L10N §1.3) | — | ARCH §4.7, §4.12, §4.13; §2.6 E |
| M4.2 Theming and numeral font | WP M4-A | C `ui/theme/ChromeColorSchemes.kt` (DS §2.8 all 48 roles × 4), `NeonArcadePalette.kt`, `HighContrastPalette.kt`, `Typography.kt`, `NumeralFont.kt`; E `ui/theme/ContrastLevel.kt` (injectable source, body), `ui/theme/GameTheme.kt` (dynamic chrome, HC resolution, palette crossfade MS §3.15); C `scripts/fonts/subset-google-sans-flex.sh`, `app/src/main/res/font/google_sans_flex_numerals.ttf`, `app/src/main/res/raw/ofl_google_sans_flex.txt`; E `scripts/check-metadata.sh` (add `check_font` only) | C `ui/theme/PaletteContrastTest.kt` (DS §2.13 unquantized method, D-205), `ui/theme/GameThemeTest.kt`, `motion/ThemeCrossfadeTest.kt` | DS §2, §3.1–§3.2, §3.5, §9; D-070–D-076, D-189, D-205–D-207 |
| M4.3 Adaptive layout and accessibility chrome | WP M4-B | C `game/GameLayout.kt` (a `SubcomposeLayout`: the D-188 measurement probe under `LocalChromeProbe`, the four arrangements including SCROLL with `swipeToMove` on the board, each its own subcomposition slot that first makes its own presentation scope (`rememberPresented` over the `board` and `header` collectors, ARCH §4.11 item 2), so an arrangement switch renders statically and replays nothing already shown (D-047, D-095, no `movableContentOf`, no slot kept for reuse), D-186 snackbar placement and D-188 side-panel alignment), `ui/components/WindowMode.kt` (with `LayoutMode.SCROLL`), `game/StatusLine.kt` (incl. the D-202 status lines), `game/input/DirectionPad.kt`, `ui/components/Accessibility.kt`, `ui/components/KeepScreenOn.kt`, `game/overlay/FirstLaunchHint.kt` (incl. the TalkBack hint variant, D-202), `ui/components/HintCard.kt`, `game/header/ScoreFit.kt` (`scoreFit`, `scoreLines`; the D-084 fit chain, DS §7.2); E `ui/components/ImmersiveMode.kt` (with `SystemBarsController`), `game/board/BoardGeometry.kt` (`resolveGameLayout`, `LayoutWindow`, `ChromeMeasure`, `GameLayoutSpec`, `HeaderStyle`, incl. the FULL → COMPACT fallback after the re-measure; DS §5.3), `game/GameScreen.kt`, `game/header/GameHeader.kt` (compact header; the INLINE, OWN_ROW and STACKED chip layouts of DS §6.1; its effects off under `LocalChromeProbe`, whose placeholder scores carry the live digit count, DS §5.3), `ui/components/ScoreCard.kt` (`ScoreValue(value, fit)`: wrapped lines and the 28 sp line box, DS §7.2), `game/header/ScoreOdometer.kt` (digit positions in the `scoreLines` lines); C icons `ic_pad_up.xml`, `ic_pad_down.xml`, `ic_pad_left.xml`, `ic_pad_right.xml` | C `ui/components/LayoutModeTest.kt`, `game/TalkBackModeTest.kt`, `ImmersiveModeTest.kt`, `KeepScreenOnTest.kt`, `ManifestPolicyTest.kt` (incl. L-AC12), `motion/LayoutMotionTest.kt` (AC-LAYOUT-1; `arrangementSwitchRendersSettledBoard`, F18-AC13; `arrangementSwitchAfterPresentedMoveReplaysNothing`, F18-AC14), `screenshots/LayoutScreenshotTest.kt` (rows 29–35), `game/header/ScoreFitTest.kt` (`fallbackChainTable`, F17-AC22); E `game/board/BoardGeometryTest.kt` (+`boardSizePerMode`, +`narrowWindowFloor`, +`resolvedLayoutTable`, +`boardNeverBelowFloorOrOffWindow`, +`growingHeaderKeepsBoardAndChromeInWindow` F18-AC15), `game/GameScreenTest.kt` (+`longScoresFitHeaderWithoutTruncation`, F17-AC23), `game/board/BoardSemanticsTest.kt` (+`traversalOrderInAllLayoutModes`, incl. SCROLL), `RecreateNoReplayTest.kt` (+`rotationKeepsStateStatic`) | DS §5, §6.1 (header layouts), §6.2, §6.6, §7.2 (fit chain); ARCH §6.2, §6.4–§6.5; MS §3.5, §3.16; D-082, D-084, D-090–D-095, D-186, D-188, D-201, D-202 |
| M4.4 Settings and statistics | WP M4-C | C `settings/SettingsViewModel.kt`, `settings/AppLanguage.kt`, `ui/components/SettingRows.kt`, `stats/StatsViewModel.kt` (incl. the D-198 display fold), `stats/StatsUi.kt`, `stats/ResetStatsDialogs.kt`; E `settings/SettingsScreen.kt`, `stats/StatsScreen.kt` (replace stubs); C icons `ic_delete.xml`, `ic_open_in_new.xml` | C `settings/SettingsScreenTest.kt` (incl. L-AC6, `switchesFireToggleHaptics` F14-AC8 and AC-HAP-4, `motionRowDisabledAtSystemScaleZero` AC-SCALE-4), `stats/StatsScreenTest.kt`, `stats/StatsViewModelTest.kt` | DS §6.8–§6.9; FEATURES F08, F11; L10N §1.13; D-006, D-044, D-102, D-198 |
| M4.5 About, How to play, licenses, What's new | WP M4-D | E `AppViewModel.kt` (body); C `about/AboutViewModel.kt`, `LicenseCatalog.kt`, `LegacyHistory.kt`, `Changelog.kt`, `WhatsNewPolicy.kt`, `WhatsNewSheet.kt`; E `about/AboutScreen.kt` (version line from `BuildConfig.VERSION_NAME`/`VERSION_CODE`, no `PackageManager`, D-134), `about/HowToPlayScreen.kt`, `about/LicensesScreen.kt` (replace stubs); C `app/src/main/res/raw/license_mit.txt` (byte copy of `LICENSE`), `license_apache_2_0.txt` (sha256 `cfc7749b…3d30`), `third_party_licenses.json` (L10N §4.4), `legacy_history.json` (L10N §4.5, with the D-209 credit); C icons `ic_chevron_right.xml`, `ic_expand_less.xml` | C `about/WhatsNewTest.kt`, `about/LicensesCoverageTest.kt`, `about/AboutScreenTest.kt`, `about/HowToPlayScreenTest.kt` | DS §6.10–§6.13; ARCH §4.13; L10N §4.4–§4.5; D-009, D-122, D-124, D-209 |
| M4.6 Golden matrix, integrate, hand off | integrator | — | C `screenshots/ScreensScreenshotTest.kt` (rows 17–22, 27–28, 44); E `screenshots/GameScreenshotTest.kt` (+ rows 2, 4–12, 14, 16, 36, 45), `game/GameScreenTest.kt` (F03-AC18, F04-AC14, F05-AC17, F17-AC15, F17-AC21, F18-AC10, F18-AC12, F22-AC5–AC6, F22-AC9, F24-AC7), `game/input/SwipeInputTest.kt` (F18-AC11), `game/TalkBackModeTest.kt` (F17-AC19, F17-AC20), `stats/StatsViewModelTest.kt` (F08-AC18) | DS §10; QUALITY §1.9, §1.12; D-204 |

Ownership exceptions for this milestone (§6.5): M4-A also edits `game/board/Board.kt` (the D-206 high-contrast empty-cell stroke) and `ui/theme/ClassicRefinedPalette.kt` (the D-207 value check and its fallback). Call sites in `GameScreen.kt` and `App.kt` for components owned by other WPs (What's new sheet, hint, pad) are added by the integrator during integration, after M4-B's `GameScreen.kt` changes are merged. Integration order: A → B → C → D (D last, so it reads A's font outcome for `third_party_licenses.json`).

**Font fallback.** If the Google Sans Flex source or fontTools 4.68 cannot be fetched (D-076), M4-A ships `GameFonts` returning `FontFamily.Default` Bold `tnum`, omits the TTF, the OFL file and its JSON entry, keeps `check_font` (it passes when both files are absent), and the commit body and the PR body record it.

**Verification gate.**
1. `./scripts/verify.sh --record`; Read rows 1–22 and 27–45 (M3 rows change with the font; check rows 37–41 against MOTION_SPEC §8.3).
2. `assertTouchTargets()` and ATF are clean on every screen-level golden (they run inside verify; D-204, D-205).
3. `check_font` active; release APK size printed by `check-apk.sh` is recorded.
4. verify full PASS.

**Commit.** `M4: add screens, theming, adaptive layout and accessibility`; body D-IDs per §4 (M4 row); the screenshots list.

**Exit criteria.** QUALITY §7.2 M4 row; every FEATURES AC that FEATURES §8 assigns to M4 passes; AC-THEME-1, AC-LAYOUT-1–AC-LAYOUT-3, AC-HAP-4 and AC-SCALE-4 pass; goldens 1–22 and 27–45 recorded and Read; `strings_pending.xml` holds every English key of L10N §1.3 except the translations.

### 5.5 M5 — Localization, store listing, legal files

**Objective.** All 20 translations, the 22-locale store listing, store graphics rendered from code, `NOTICE`, `PRIVACY.md`, the full README, and the M5 checks.

**Inputs.** D-100–D-106, D-110, D-113–D-116, D-121, D-123, D-190, D-208; L10N §1, §2, §4.3, §4.6–§4.10; FEATURES F24-AC8, F26-AC5, F27-AC5, F28, F30-AC8–AC9.

**Tasks.**

| Task | Owner | Files | Spec |
|---|---|---|---|
| M5.1 Glossary commit | integrator | C `docs/modernization/i18n-glossary.md` (L10N §1.6 verbatim). Its own commit `M5: add i18n glossary`, verify full, handoff; the orchestrator pushes it (D-105: before any translation) | L10N §1.6, §1.8 step 2 |
| M5.2 English freeze | integrator | Prepare (not yet committed) the final `app/src/main/res/values/strings.xml`: every `strings_pending.xml` entry merged in L10N §1.3 order with its context comment; hand the file to the translators | L10N §1.3, §1.4 step 4; D-208 |
| M5.3 Translations and listing texts | WP M5-T1 {de, ru, fa, uk, el}; T2 {cs, pl, lt, sl, sr}; T3 {fr, es, it, pt (store: pt-BR and pt-PT), eo}; T4 {da, nl, hu, tr, ko} | Each WP: E `app/src/main/res/values-<xx>/strings.xml` for its locales (legacy `settings` line unchanged, then every other key of L10N §1.3 in §1.3 order, no comments); C `fastlane/metadata/android/<xx>/title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/30000.txt`; a back-translation table for the 8 L10N §1.8 step 6 keys per locale; the §1.8 step 5 self-check results | L10N §1.5–§1.8, §2.2, §2.4; D-208 |
| M5.4 Legal files and README | integrator | C `NOTICE` (L10N §4.3), `PRIVACY.md` (§4.6); E `README.md` (§4.9 M5 rewrite, ten sections); C/E `fastlane/metadata/android/en-US/*.txt` (§2.3) | D-116, D-121, D-123 |
| M5.5 Localization tests and locale goldens | integrator | C `test/…/TranslationQualityTest.kt` (9 methods of L10N §1.9; L10N maps L-AC4 to the whole class, so the class KDoc's first line is `/** L-AC4 */`), `test/…/LocalizationTest.kt` (F24-AC8, F28-AC2–AC6), `app/src/test/resources/i18n/identical-to-english.txt` (seed lines of §1.9); E `ui/components/FormattingTest.kt` (+`toLatinDigitsMapsArabicIndicAndPersian`, L-AC5); C `screenshots/LocaleScreenshotTest.kt` (rows 23–26) | L10N §1.9–§1.12; FEATURES F28 |
| M5.6 Store graphics | integrator | C `test/…/store/StoreAssetsTest.kt` (captures each frame with `composeRule.onNodeWithTag("store:frame").captureToImage().asAndroidBitmap()`), `store/OpaquePngWriter.kt` (L10N §2.5 verbatim; every store PNG written through it), `store/FeatureGraphic.kt` (DS §8.5); run `./gradlew :app:testDebugUnitTest -PrecordStoreAssets=true --tests 'com.clayboicardi.game2048.store.*'` twice; `git diff --exit-code fastlane/` clean after the second run (L-AC9); D the legacy `images/phoneScreenshots/screen1.png`, `images/sevenInchScreenshots/screen_tablet.png`; Read all 8 images | D-115, D-190; DS §8.4–§8.6 |
| M5.7 Checks, integrate, hand off | integrator | E `scripts/check-metadata.sh` (`check_store_listing`, `check_store_images`, `check_legal_and_privacy`, `check_legal_texts`, `check_strings_final`; QUALITY §1.16), `values/strings.xml` (final), D `values/strings_pending.xml`; E `config/test-count-floor.txt` | QUALITY §1.16; L10N §2.6; D-190, D-208 |

Translation WPs start after M5.2 and edit only their locale files, so they never conflict. At most three run at once; T4 takes the first free slot. Each WP follows L10N §1.7 (register, script and per-locale rules) and §1.6 (glossary) exactly; T1 delivers de, ru and fa first so the integrator can run Lint and `TranslationQualityTest` on them early (L10N §1.8 step 3).

**Verification gate.**
1. `./gradlew :app:lintDebug` clean; `./gradlew :app:testDebugUnitTest --tests '*LocalizationTest' --tests '*TranslationQualityTest' --tests '*FormattingTest'`.
2. `./scripts/verify.sh --record`; Read rows 23–26 (L10N §1.11 and §1.12 Read checklists, including the `en-XA` hard-coded-text detector, L-AC7) and every other golden whose bytes changed.
3. Read the 8 store images (L10N §2.5 "After generation").
4. verify full PASS with every M5 check active.
5. The back-translation table (160 rows) goes into the PR body in a collapsed `<details>` block (the integrator hands it off; the orchestrator writes it).

**Commits.** `M5: add i18n glossary` (M5.1, pushed alone); `M5: translate 20 locales and add store listing and legal files` (everything else; D-IDs D-100–D-106, D-110, D-113–D-116, D-121, D-123, D-190, D-208; screenshots and store images listed).

**Exit criteria.** QUALITY §7.2 M5 row; L-AC1–L-AC14 hold; locale goldens 23–26 recorded and Read; `strings_pending.xml` gone.

### 5.6 M6 — Hardening and close-out

**Objective.** Prove every gate is active and honest, finish the PR body and run the final Codex loop to the merge gate.

**Tasks.**

| Task | Owner | Files | Spec |
|---|---|---|---|
| M6.1 Gate audit | integrator | none unless a finding needs a fix (each fix is its own `M6:` change in the M6 commit) | §5.7 audit list |
| M6.2 Clean-clone verification | integrator | none. `git clone` the branch into `../2048-clean` (beside the checkout), then run `./scripts/verify.sh` there under the lock (with `VERIFY_GRADLE_ARGS` in the sandbox) → PASS. Detects reliance on untracked or ignored local files | D-163, D-178 |
| M6.3 Reproducibility probe | integrator | C `scripts/repro-probe.sh` (QUALITY §1.17, +x); run it once locally; its result line goes into the handoff for the PR body | D-191; QUALITY §1.17 |
| M6.4 Docs consistency check | integrator | Run the §2.7 naming acceptance grep; fix any drift with a `docs:` change inside the M6 commit | §2.7 |
| M6.5 PR body complete (§6.9) | orchestrator | PR body (§6.9) | D-165, D-193 |
| M6.6 Final Codex loop and merge | orchestrator (fixes by the integrator) | §6.10–§6.11 | D-166–D-170, D-196 |

**Verification gate.** verify full PASS on the M6 head; clean-clone verify PASS; §5.7 audit clean; CI green.

**Commit.** `M6: harden gates and close out the native rewrite`; body lists the audit results, the suppression inventory, the repro-probe line, D-191.

**Exit criteria.** QUALITY §7.2 M6 row and §7.4; success criteria S1–S15.

### 5.7 M6.1 audit list

Run each from the repository root on the M6 candidate commit; paste results into the M6 commit body. Every item must print nothing (or the stated value).

| # | Check | Command or method |
|---|---|---|
| A1 | Catalog is D-016 plus recorded D-entries only | `` diff <(sed -n '/^```toml/,/^```/p' docs/modernization/DECISIONS.md \| sed '1d;$d') gradle/libs.versions.toml `` (the D-016 block is the only `toml` block in DECISIONS.md; differences must each have a D-entry; ktfmt 0.64 fallback recorded) |
| A2 | `gradle.properties` is exactly the five D-017 lines | `cat gradle.properties` |
| A3 | Material 3 1.5-only APIs only in `ui/theme` and `ui/components` (D-026) | `git grep -l -E 'MaterialExpressiveTheme\|MotionScheme\.expressive\|ButtonGroup\|ButtonDefaults\.shapes\|IconButtonDefaults\.shapes\|Emphasized\b' -- app/src/main/kotlin \| grep -v -E '/ui/(theme\|components)/'` |
| A4 | No raw visible `delay(` in game UI code (D-183) | `git grep -n -E '\bdelay\(' -- app/src/main/kotlin/com/clayboicardi/game2048/game app/src/main/kotlin/com/clayboicardi/game2048/ui` (each hit justified as non-visible in the commit body) |
| A5 | No platform RNG or clock in game logic outside `data/platform` | `git grep -n -E 'kotlin\.random\|java\.util\.Random\|Math\.random\|currentTimeMillis\|nanoTime' -- app/src/main/kotlin` |
| A6 | No `Vibrator`, `VibrationEffect`, `INTERNET`, `FLAG_IGNORE_GLOBAL_SETTING`, `material-icons`, `ui-text-google-fonts`, `ui-tooling` on the classpath | `git grep -n -E 'Vibrator\|VibrationEffect\|android\.permission\.INTERNET\|FLAG_IGNORE_GLOBAL_SETTING\|material-icons\|ui-text-google-fonts' -- app gradle`; `grep -n 'ui.tooling' app/build.gradle.kts` |
| A7 | No named companion object on a `@Serializable` class; no `buildConfigField`; no `PackageManager` in main sources, so the version comes only from `BuildConfig` (D-134) | Review every `@Serializable` declaration listed by `git grep -n '@Serializable' -- app/src/main`; `git grep -n -E 'buildConfigField' -- app/build.gradle.kts` and `git grep -n -E 'PackageManager\|packageManager\|PackageInfo\|getPackageInfo' -- app/src/main/kotlin` print nothing |
| A8 | Suppression inventory, each with a same-line reason (D-142, QUALITY §2.5) | `git grep -n -E '@Suppress\(\|tools:ignore=' -- app core` |
| A9 | Workflows, modules and non-gates (D-133, D-153) | `ls .github/workflows` = `ci.yml`; no `app/src/androidTest`, no `.github/dependabot.yml`, no `gradle/verification-metadata.xml`, no `:baselineprofile` |
| A10 | Every check-metadata function of QUALITY §1.16 (D-179, D-190) is called | `grep -c '^check_' scripts/check-metadata.sh` equals the number of call lines |
| A11 | 45 goldens (D-204), all compared | `ls app/src/test/screenshots/*.png \| wc -l` = 45; last verify's `check-test-counts.sh` prints no G1–G5 error (QUALITY §1.15), so all 45 have an `unchanged` result |
| A12 | AC coverage (S4) | the script below prints `0 missing` |

```bash
python3 -I - <<'PY'
import pathlib, re, subprocess, sys
d = pathlib.Path("docs/modernization")
need, gate = set(), set()
for doc in ("FEATURES.md", "L10N_STORE_LEGAL.md"):
    for line in (d / doc).read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\| (F\d\d-AC\d+|L-AC\d+) \|", line)
        if m:
            last = line.rstrip().rstrip("|").rsplit("|", 1)[-1].strip()
            (gate if re.match(r"(Gate|Inspection|ATF rule)", last) else need).add(m.group(1))
need |= set(re.findall(r"\*\*(AC-[A-Z0-9]+-\d+)\*\*", (d / "MOTION_SPEC.md").read_text(encoding="utf-8")))
out = subprocess.run(["git", "grep", "-h", "-E", r"/\*\* ?(F[0-9]{2}-AC[0-9]+|L-AC[0-9]+|AC-[A-Z0-9]+-[0-9]+)",
                      "--", "app/src/test", "core/engine/src/test"], capture_output=True, text=True).stdout
found = set(re.findall(r"(F\d\d-AC\d+|L-AC\d+|AC-[A-Z0-9]+-\d+)", out))
missing = sorted(need - found)
print(f"test-mapped={len(need)} gate-mapped={len(gate)} missing={len(missing)}")
print("\n".join(missing))
sys.exit(1 if missing else 0)
PY
```

The A12 script also runs in report mode at every milestone gate (§6.4 step 9): no AC that FEATURES §8 assigns to a finished milestone may be missing (D-211). A method may list several AC IDs on its first KDoc line.

---

## 6. Execution protocol

### 6.1 Roles (D-194)

| Role | Who | Duties |
|---|---|---|
| Orchestrator | The session that runs the execution workflow | The only role that pushes, opens or updates the PR, triggers and polls Codex, replies to and resolves review threads, merges, escalates and resumes. It verifies each handoff before pushing (below). |
| Milestone integrator | One implementer agent per milestone | Writes the `M<n>.1` contracts; integrates work packages in the stated order; records and Reads goldens and sets floors; runs verify full and self-reviews (§6.6); commits locally on the execution branch. For review findings it implements the fixes and regression tests, runs verify full and commits `review(r<k>): …` locally (§6.10). |
| WP implementers | One implementer agent per work package | Edit only their owned files, in their own worktree (§6.5), and run targeted tests. Before handing off, they commit all their work on `wip/<WP>`, which is never pushed. |

Implementer agents never push and never call GitHub.

**Push handoff** (integrator → orchestrator): `{sha, last verify line, log path, engine and app test counts, Screenshots list, D-IDs}`; for a review fix, plus one line per finding id. Before pushing, the orchestrator checks that `git status --porcelain` is empty, that `git rev-parse HEAD` equals the handed-off sha, and that the log ends with `verify.sh: level=full … result=PASS …`; then it applies the push gate of §6.4 step 11.

### 6.2 Environment and preflight

- **Toolchain.** JDK 21 as `java`. `ANDROID_HOME` is exported for every agent (worktrees do not share an uncommitted `local.properties`) and points at an SDK with `platforms;android-37.0`, `build-tools;36.0.0` (AGP 9.4.1's default) and `build-tools;37.0.0` (check scripts). A system Gradle 8.14.3 is used only for the TC-4 wrapper bootstrap. `CLAUDE.md` says how to locate the SDK.
- **Mirror.** If Maven Central returns HTTP 429: the out-of-repo init script and `VERIFY_GRADLE_ARGS` of `QUALITY.md` §3.3, including `-ProbolectricRepoUrl=…` for the Robolectric jars. Never put the script in `~/.gradle/init.d/`; never commit it or the mirror host (D-019). Commit bodies append `(sandbox mirror)` to the Gates line when it was used.
- **One local build at a time (D-178).** The machine has 4 CPUs and about 15 GB of RAM (research/07 G19). Every local `./scripts/verify.sh …` and every direct `./gradlew …`, in any checkout or worktree, runs as `flock "$HOME/.cache/game2048-gradle.lock" <command>`; the scripts never take the lock themselves, because CI runs them too. Editing runs in parallel; builds queue. A local full run starts detached and writes its output to `$HOME/.cache/game2048/verify-<short-sha>-<level>.log`; only its final line `verify.sh: level=<level> record=<bool> result=PASS|FAIL …` decides the outcome. A tool timeout or a killed run is never a result. `./gradlew --stop` runs at the end of each milestone.

  ```bash
  mkdir -p "$HOME/.cache/game2048"
  LOG="$HOME/.cache/game2048/verify-$(git rev-parse --short HEAD)-full.log"
  nohup flock "$HOME/.cache/game2048-gradle.lock" ./scripts/verify.sh > "$LOG" 2>&1 &
  tail -n 1 "$LOG"     # poll until it prints the verify.sh line
  ```
- **GitHub (D-195).** Every GitHub step is performed by the orchestrator via the GitHub API (MCP tool or gh); `QUALITY.md` §6.8 is the canonical operation table. Every operation targets `clayboicardi/2048-android-modern-refresh` (MCP `owner`/`repo`; gh `-R clayboicardi/2048-android-modern-refresh`); PRs use base `master`; nothing targets `uberspot/2048-android`. No step depends on a gh-only command, and no step needs an admin API call: Actions and Codex are already enabled on the fork.

### 6.3 Branch re-cut (D-161, D-197)

The orchestrator runs these five steps after the plan PR is merged and before any execution commit:

1. Confirm via the GitHub API (MCP tool or gh) that the plan PR is merged, and record its `merge_commit_sha`.
2. `git status --porcelain` prints nothing.
3. `git fetch --prune origin`.
4. If `git rev-parse --verify -q origin/claude/2048-modernization-plan-8eneix` prints a SHA (the old branch still exists), `git diff --quiet origin/master origin/claude/2048-modernization-plan-8eneix` must succeed. If it fails, stop and report.
5. `git checkout -B claude/2048-modernization-plan-8eneix origin/master`, then `git push --force-with-lease origin claude/2048-modernization-plan-8eneix`.

`--prune` removes a stale remote-tracking ref if GitHub deleted the merged head branch, so `--force-with-lease` does not refuse. This force-push is allowed only because the old branch's content is fully merged and no PR is open on it. From the moment the execution PR exists, pushed history is never rewritten: no force-push, rebase or amend of pushed commits (D-161). Unpushed local commits may be reshaped freely. The orchestrator keeps the commands and their output and puts them into the PR body's Notes (re-cut log) when it creates the PR.

### 6.4 Per-milestone loop

For M0 … M6, in order. M3 runs the loop once per part and M5 once per commit (D-211).

Milestone integrator:

1. **Contracts.** Re-read this milestone's section and the spec sections it lists. Write the `M<n>.1` contracts and commit them locally as the integration base (`wip(M<n>): contracts`, never pushed).
2. **Worktrees.** For each WP: `git worktree add -b wip/<WP> ../2048-wt/<WP> <base>`. Each WP implementer is briefed with its WP table row, §2 of this plan, the spec sections, the ownership list (§6.5), the lock rule (§6.2) and the handoff format (§6.5). At most three WPs run at once.
3. **Implement.** WP implementers work in their worktrees; the integrator does integrator-owned tasks meanwhile.
4. **Integrate** WPs in the stated order (§6.5); resolve contract gaps; run `./gradlew spotlessApply`; run verify quick, then the milestone's targeted tests, then verify full.
5. **Goldens.** When the milestone adds or changes UI: `./scripts/verify.sh --record`; Read every listed PNG and the affected inspection filmstrips (D-210); fix any defect and re-record; never commit a PNG that shows a defect (QUALITY §1.9).
6. **Self-review the diff adversarially** (§6.6). Fix what it finds; return to step 4.
7. **Floors.** `config/test-count-floor.txt` = the executed counts that `check-test-counts.sh` printed in the last verify full.
8. **Commit** locally on the execution branch with the §6.7 message, squashing the `wip(M<n>)` commits.
9. **Gate.** verify full, detached and under the lock (§6.2), on the committed HEAD with `git status --porcelain` empty; its final line is `result=PASS`. Verify runs on the commit, so the log name carries the handed-off SHA (D-163). Also run the A12 script (§5.7) in report mode: no AC that FEATURES §8 assigns to a finished milestone may be missing (D-211). Any later edit, even a comment, means a new commit and a new run.
10. **Handoff** (§6.1) to the orchestrator, with the A12 summary line.

Orchestrator:

11. **Push gate.** Push only when (1) the handoff passes the D-194 checks; (2) the verify check of the previously pushed head has concluded green, or this push fixes its failure (a cancelled run is not green); (3) no triggered Codex round is pending on the pushed head; (4) the D-180 term grep prints nothing. The term grep is `git grep -i -E -f <term file>` over the tracked files, with a term file kept outside the checkout and never committed. Review-fix pushes (§6.10) need (1) and (4) and may supersede a running CI run.
12. **Push** `git push origin <sha>:refs/heads/claude/2048-modernization-plan-8eneix`. At M0, create the PR right after the push (§6.9), because CI runs only on `pull_request` and `master` pushes. Confirm CI (§6.8).
13. **Update the PR body** (§6.9): milestone checkbox with SHA, screenshots, the latest verify summary, artifacts, deviations.
14. **Codex.** After the M2 push (mid checkpoint) and after the M6 push (final), run §6.10. Other milestones do not trigger Codex. The next milestone proceeds locally while a round is pending.

### 6.5 Parallel work protocol

- **Worktrees** live beside the checkout: `git worktree add -b wip/<WP> ../2048-wt/<WP> <integration-base>`. `wip/*` branches are never pushed and are removed after integration (`git worktree remove ../2048-wt/<WP>; git branch -D wip/<WP>`). At most three WPs run at once. `ANDROID_HOME` must be exported in every worktree's environment (worktrees do not share `local.properties`).
- **Ownership.** A WP edits only the files its table lists, plus new test files it is told to create. Integrator-owned in every milestone, unless a WP row of this milestone assigns the file: `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/**`, `config/**`, `scripts/**`, `docs/**`, `AGENTS.md`, `CLAUDE.md`, `README.md`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `MainActivity.kt`, `Game2048Application.kt`, `di/**`, `NavKeys.kt`, `ui/theme/GameDimens.kt`, `test/…/testing/**`, `test/…/fixtures/GoldenFixtures.kt`, `app/src/test/screenshots/**`. A WP that needs a change in an integrator-owned or another WP's file stops and reports the exact change; the integrator applies it. WPs request new dp tokens through the handoff; the integrator adds them to `ui/theme/GameDimens.kt`.
- **Shared resource files.** New English strings go into `app/src/main/res/values/strings_pending.xml` inside the WP's region, which the integrator pre-creates:

  ```xml
  <!-- region M3-B: begin -->
  <!-- region M3-B: end -->
  ```

  Each WP inserts only between its own markers, with the L10N §1.3 context comment above each key, so line-based merges never conflict. Icons are files; each is assigned to exactly one WP.
- **Contract stability.** A WP never changes a contract signature from `M<n>.1`; it reports the needed change instead.
- **WP handoff** (the WP implementer's final message, kept out of the repository): files changed; tests added (class, method count, AC IDs); strings added; dp tokens requested; commands run with their last result line; known failures and their cause; any contract gap; the tip SHA of `wip/<WP>`. Before handing off, the WP commits all its changes on `wip/<WP>`; the handoff names the tip SHA; the integrator rejects a worktree whose `git status --porcelain` is not empty.
- **Integration.** The integrator verifies ownership before merging: `git diff --name-only <base>...wip/<WP>` lists only owned files. Then `git merge --squash wip/<WP>` and a local `wip(M<n>): integrate <WP>` commit per WP, in the milestone's stated order, running verify quick (and the WP's tests) after each. These local commits are squashed into the milestone commit before handoff.
- **Floors and goldens** are set only by the integrator, from integrated runs.

### 6.6 Self-review checklist (before every commit)

Run `git diff --stat <last-pushed>..` and read the full diff with these questions. Each "yes" is fixed before committing.

1. Does any file fall outside this milestone's file lists, or is any listed file missing?
2. Does any change meet a `## Code Review Rules` P0/P1 item in `AGENTS.md` or `core/engine/AGENTS.md`? (permissions, network, saved-data compatibility, lowered bests, state loss, main-thread I/O, unbounded growth, engine rule changes, RTL board mirroring, accessibility regressions, animation/engine divergence, weakened gates)
3. Does any behavior differ from a D-ID the commit claims, or from an AC, without a D-entry?
4. Is any user-visible text hard-coded instead of a string resource? Is any count not a plural? Any concatenated sentence (D-103)?
5. Is any hex, dp, sp or millisecond literal outside `ui/theme/` (the D-112 splash cap excepted, D-217)? Any `Animatable.value` read in a composition body? Any state write during composition?
6. Is any DataStore, file or `PackageManager` I/O reachable from the main thread or from composition?
7. Does any test lack its AC KDoc line (the first KDoc line is the `Fnn-ACk`, `AC-*` or `L-ACn` ID it proves, or several IDs; the A12 audit depends on it), use an expression body returning non-`Unit`, depend on wall time or unseeded randomness, or carry `@Ignore`?
8. Was every new or changed PNG Read and listed?
9. Does any tracked file you changed contain an AI model name or identifier?

### 6.7 Commit conventions (D-164, D-180)

- Subject ≤ 72 characters, imperative: `M<n>: …`, `review(r<k>): …`, `ci: …`, `docs: …`. Local integration commits may use `wip(M<n>): …` subjects; they are squashed before handoff and never pushed.
- Body, in order: `D-IDs: …` (implemented, plus any entry added during execution); `Gates: scripts/verify.sh passed (engine=<n> tests, app=<m> tests)` with ` (sandbox mirror)` when used; `Screenshots: …` in the QUALITY §1.9 format or `Screenshots: none`; milestone-specific extras.
- Trailer lines: exactly as the execution environment supplies, verbatim (D-164, D-180).
- No WIP commit is pushed. No merge commits on the branch (it is linear; the merge commit happens on `master` at the end).

### 6.8 CI monitoring, red CI, Actions not triggering

After every push of a branch that has an open PR, the orchestrator follows the run via the GitHub API (MCP tool or gh; operations in QUALITY §6.8):
- list the runs of `ci.yml` on `claude/2048-modernization-plan-8eneix` and pick the one whose head SHA is the pushed SHA;
- read its status and conclusion every 5 minutes until `verify` concludes;
- on failure, read the failed jobs' logs;
- rerun failed jobs, dispatch `ci.yml` on the branch, list and download a run's artifacts when the steps below need it.

Rules:
- **No run within 5 minutes** of a push with an open PR (D-195): (1) check that the PR exists and targets `master`; (2) dispatch `ci.yml` on the branch once (its `verify` check attaches to the same head SHA); (3) if still no run, apply D-172 item 1: stop, write the blocker and the full local verify output into the PR body, and report.
- **Red CI, local verify passed on the same SHA:**
  1. Read the failed jobs' logs. Golden-only failures follow QUALITY §1.9 "Local and CI disagree" (download the `game2048-screenshot-diffs-<sha>` artifact, Read `_actual` and `_compare`, adopt only images without a visible defect in a `ci: adopt CI-rendered goldens (<n> files)` commit, then the 0.001 threshold fallback of D-147 if it repeats).
  2. A timing budget or flaky-looking test: rerun the failed jobs once. If it fails again, treat it as real; never raise a budget without an execution-time D-173 entry with measurements (QUALITY §1.14).
  3. Anything else: the integrator reproduces locally with the failing task, fixes in a `ci:` commit (or the milestone's next commit if the cause is milestone code), runs verify full and hands off; the orchestrator pushes.
- Before pushing the next milestone, wait for the verify check on the current head to conclude; a cancelled run is not green; review-fix pushes may supersede runs; only the merge head needs green for the merge gate.

### 6.9 PR creation and body (D-165, D-193)

Right after the M0 push, the orchestrator creates the PR, ready for review, via the GitHub API: MCP `create_pull_request(owner=clayboicardi, repo=2048-android-modern-refresh, base=master, head=claude/2048-modernization-plan-8eneix, draft=false, title, body)` or `gh pr create -R clayboicardi/2048-android-modern-refresh --base master --head claude/2048-modernization-plan-8eneix --title … --body-file ../2048-pr-body.md`. The title is `Native rewrite: Kotlin + Compose + Material 3 (3.0.0)`.

The body's working file is `../2048-pr-body.md`, beside the checkout and never committed. Before every update, the orchestrator re-reads the live body (MCP `pull_request_read` `get`, or gh) and keeps owner-added text, the ledger block and the footer; then it updates the PR body via the GitHub API.

PR body template (kept current after every push):

```markdown
Native rewrite of 2048 as a Kotlin + Jetpack Compose + Material 3 app (`com.clayboicardi.game2048`, 3.0.0 / 30000).
Plan: `docs/modernization/PLAN.md`. Decisions: `docs/modernization/DECISIONS.md`.
The default branch is `master` ("main" in the request).

### Milestones
- [ ] M0 Bootstrap — `<sha>`
- [ ] M1 Engine — `<sha>`
- [ ] M2 State and persistence — `<sha>` (Codex mid checkpoint)
- [ ] M3 Board, input, motion, haptics, game screen — `<sha>` (part 1), `<sha>` (part 2)
- [ ] M4 Screens, theming, layout, accessibility — `<sha>`
- [ ] M5 Localization, store listing, legal — `<sha>`, `<sha>`
- [ ] M6 Hardening and close-out — `<sha>`

### Review map
| Milestone | Main directories | Specs |
|---|---|---|
| M0 | build files, `scripts/`, `signing/`, `config/`, `fastlane/` (moves) | ARCHITECTURE §1–§2, QUALITY §1.13–§3 |
| M1 | `core/engine/` | ARCHITECTURE §3 |
| M2 | `app/…/data/`, `app/…/di/`, `app/…/game/GameViewModel.kt`, `app/…/game/board/BoardPresenter.kt` | ARCHITECTURE §4, §9 |
| M3 | `app/…/game/`, `app/…/ui/theme/`, `app/…/ui/components/` | ARCHITECTURE §5–§7, MOTION_SPEC, DESIGN_SYSTEM §6–§7 |
| M4 | `app/…/settings/`, `stats/`, `about/`, `ui/theme/` | DESIGN_SYSTEM, FEATURES |
| M5 | `values-*/`, `fastlane/`, `NOTICE`, `PRIVACY.md`, `README.md` | L10N_STORE_LEGAL |

### Deviations from DECISIONS.md
None, or entries added during execution (D-212 and later), one line each. D-174–D-211 and TC-1–TC-7 were decided in the plan PR.

### Screenshots (all inspected)
<cumulative QUALITY §1.9 list>

### Verification
- Latest `scripts/verify.sh` on `<sha>`: `verify.sh: level=full record=false result=PASS elapsed=<s>s` (engine=<n>, app=<m> tests)
- `check-apk.sh`: release <bytes> B; permission `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; exported {MainActivity, ProfileInstallReceiver}; signer `<digest>`
- CI durations: M0 <min>, M6 <min>; reproducibility probe: <result>

### CI artifacts
`game2048-apks-<sha>`, `game2048-reports-<sha>`

### Review rounds
Automatic review on the M0 head within 30 minutes: <yes/no>

| Round | Head SHA | Trigger comment id | Trigger time | Result | Follow-up |
|---|---|---|---|---|---|

### Notes
- `ui-tooling` stays off the classpath (ARCHITECTURE §2.6); 45 goldens (D-204); branch re-cut log (§6.3); other recorded fallbacks.

### Blockers
None.

<!-- orchestrator-ledger: <comment ids> -->
<environment-supplied PR footer, verbatim>
```

### 6.10 Codex review protocol (D-166–D-169, D-196; operations in QUALITY §6)

1. **Rounds.** Round k is the k-th `@codex review` trigger on a distinct head SHA of the PR. The mid checkpoint and the final loop are counted together. A re-post on the same SHA stays in the same round. Automatic (untriggered) reviews do not advance the count.
2. **Trigger.** The orchestrator posts a PR comment whose entire body is `@codex review`, via the GitHub API: (a) after the M2 push; (b) after the M6 push; (c) after every fix push in a review loop. It records the round number, head SHA, trigger comment id, trigger time and the comment's `+1` baseline in the rounds table, and the comment id in the ledger.
3. **Poll.** First 👀 check 2 minutes after the trigger, then every 3–5 minutes, using scheduled wake-ups and minimal-output API calls; CI is checked every 5 minutes until `verify` concludes. Deadlines: 👀 from `chatgpt-codex-connector[bot]` within 10 minutes; a result within 30 minutes; then one re-post (same round); still nothing after another 30 minutes: D-172 item 2.
4. **Clean** (D-167, D-196). The head SHA is unchanged and the signal comes after the trigger time. Any one of these counts: (a) a `+1` by `chatgpt-codex-connector[bot]` on the trigger comment or the PR, identified through the REST reactions endpoint (served without authentication for this public repository; keep polling under 60 requests per hour); (b) a bot review whose `commit_id` is the head, with no `P0`–`P3` token, that is not an error, usage-limit or status message; (c) a bot PR comment that explicitly reports no issues. A message about a limit or an error, or one saying "unable" or "could not", is no result. If reaction authorship cannot be read, a `+1` counts only when the trigger comment's `+1` count rose above its recorded baseline and no human reaction is known; otherwise (b) or (c) decides.
5. **Findings** (D-169), for each:
   - Verify the claim first: reproduce it (a failing test, a grep, a reading of the cited code against the cited D-ID). Never fix by reflex and never dismiss by reflex.
   - Valid: the review-fix procedure below.
   - Invalid or contrary to `DECISIONS.md`: reply with evidence (command output, test name, D-ID, research pointer). P0 and P1 are declined only with concrete evidence that the finding is wrong. A finding that is right but contradicts a decision is either declined citing the D-ID or accepted with an execution-time D-173 entry in the fix commit.
   - Summary-level items are answered with a PR comment quoting them.
   - Never dismiss, hide or minimize a bot review.
6. **Resolved P0/P1** (D-196). A P0 or P1 finding is resolved when it is fixed (the fix is pushed, the thread has a `Fixed in <sha>: …` reply, and the thread is resolved), or when it is declined with concrete evidence in a reply and the bot's next result (on a later head, or after a re-trigger on the same head) does not raise it again; the orchestrator then resolves the thread. A declined P0/P1 that the bot raises again stays open. Any open P0/P1 blocks the merge, including under the valve. Declined P2/P3 threads may stay unresolved, with a reply.
7. **Review-fix procedure.** The orchestrator passes the findings with their ids to the integrator. If no unpushed commits sit on top of the pushed head, the integrator fixes on the branch. Otherwise it runs `git worktree add --detach ../2048-wt/review <pushed-head-sha>`, commits `review(r<k>): …` there (with a regression test for behavioural findings), runs verify full there under the lock, and returns the D-194 handoff plus one line per finding id. The orchestrator pushes with `git push origin <fix-sha>:refs/heads/claude/2048-modernization-plan-8eneix`, replies `Fixed in <sha>: <line>` and resolves the threads. The integrator then rebases the unpushed milestone commits onto `<fix-sha>` (allowed; they were never pushed) and re-runs verify full on the rebased tip before it may be pushed.
8. **Re-trigger** after each fix push (rule 2c). A review fix is pushed before the next milestone commit.
9. **Automatic reviews** (D-193). Because the PR is open for review, Codex may review a head without a trigger. Their findings are handled like triggered ones; they do not count as rounds. The PR body records whether one appeared on the M0 head within 30 minutes.
10. **Owner ledger** (D-196). The orchestrator posts as the repository owner, so it keeps a ledger of the ids of every comment, reply and review it posts, in the hidden `<!-- orchestrator-ledger … -->` block of the PR body and in `../2048-orchestrator-ledger.txt` beside the checkout. Any owner-authored comment, review or reaction that is not in the ledger is a human instruction: the orchestrator pauses at the next safe point and follows it (D-169).
11. **Mid checkpoint flow.** The M2 loop runs until a round is clean or a stop condition applies. M3 work continues locally meanwhile. Because fix pushes come first and each is re-triggered, M3 part 1 is pushed only after the M2 loop has ended.
12. **Final loop.** After M6: rounds continue until the merge gate (§6.11) holds or a stop condition applies.

### 6.11 Merge gate, valve, merge method (D-168, D-170, D-196)

- Merge only when, for the same head SHA, the CI `verify` check has concluded `success` (read via the GitHub API: `pull_request_read` `get_check_runs`, or `gh pr checks`) and the latest triggered Codex round on that SHA is clean (§6.10 rule 4).
- **Valve:** after 5 rounds, if the latest round raised only new P2 or P3 items, merge once every P0 and P1 from all rounds is resolved (§6.10 rule 6) and every remaining thread has a reply. Record "valve applied after round k" in the PR body. Because Codex on GitHub reports only P0/P1, the valve is expected not to apply.
- Never merge while any P0/P1 is open (D-196).
- Merge via the GitHub API with the expected head SHA: MCP `merge_pull_request(owner=clayboicardi, repo=2048-android-modern-refresh, pullNumber, merge_method, expectedHeadSha=<sha>)`, or `gh pr merge <n> -R clayboicardi/2048-android-modern-refresh --merge|--squash --match-head-commit <sha>`. The execution PR uses a merge commit (`merge`); the plan PR was squash-merged (`squash`).
- After the merge, the orchestrator follows the `master` push run of `ci.yml` via the GitHub API until it concludes and reports its result. A red `master` run after merge is reported, not fixed silently.

### 6.12 Stop conditions (D-172)

Stop autonomous progress, leave the PR open, write the blocker into the PR body's "Blockers" section, and report to the user when:
1. no CI run appears after a push with an open PR, after the D-195 dispatch attempt (§6.8), or a workflow file cannot be pushed;
2. Codex gives no response after the re-post (§6.10 rule 3);
3. round 10 (D-196) still raises P0 or P1 findings: no further pushes; post a summary to the user (rounds table, open P0/P1 with evidence, head SHA, CI state) and wait for the user. Never merge with an open P0/P1;
4. a decision is technically impossible and no D-173 alternative stays within U1–U7;
5. a push is rejected by push protection (for example over `signing/ci-sideload.p12`).

Otherwise continue without asking. The report names the milestone, the head SHA, what was tried, and what the user can do.

### 6.13 Resuming after an interruption

| Where | What survives |
|---|---|
| Durable on GitHub | Pushed commits; the PR body (milestone checklist, rounds table with trigger comment id and time, ledger); review threads |
| In the checkout, if it survives | Local integration commits with structured subjects (`wip(M<n>): …`, `M<n>: …`, `review(r<k>): …`); `wip/<WP>` branches |
| Volatile | Worktrees, Gradle caches, the mirror init script, the exported environment, the PR-body working file |

Steps:
1. Re-create the environment: export `ANDROID_HOME`, restore the mirror init script if needed (QUALITY §3.3), create the lock and log directory (`mkdir -p "$HOME/.cache/game2048"`).
2. `git fetch` and compare local and remote heads (`git log --oneline origin/claude/2048-modernization-plan-8eneix..HEAD` and the reverse).
3. Reconcile the PR checklist with the PR's commits, read via the GitHub API (MCP `pull_request_read` `get_commits`, or gh).
4. If a round is recorded without a result, resume polling from its recorded trigger time; never re-trigger.
5. `git worktree prune`; integrate `wip/<WP>` branches that have commits; re-run WPs that have none.
6. Continue at the first step whose output is missing. Pushed milestones are never redone.

---

## 7. Traceability matrix

"Task" names where the decision is implemented; "Verified by" names the tests (class.method where FEATURES or MOTION_SPEC fix the method) or gates. Gates: `check-apk` items 1–7 (D-148), `check-metadata` functions (QUALITY §1.16), `check-licenses`, `check-test-counts`, Lint, Spotless, detekt, Kover.

| D-ID | Topic | Task(s) | Verified by |
|---|---|---|---|
| D-001 | Native rewrite; legacy deleted | M0.6 | S5 commands; `check_no_submodule` |
| D-002 | Offline; one self-permission | M0.9, M0.12 | `check-apk` items 2, 3, 7; `ManifestPolicyTest.emojiCompatInitializerRemoved` |
| D-003 | v1.0 scope | M0–M5 | FEATURES ACs (S4 audit) |
| D-004 | Non-goals | all | `check-apk` item 7; M6.1 A6, A9; AGENTS.md review rules |
| D-005 | No legacy import; side by side | M5.4 | `check_store_listing` needle `com.clayboicardi.game2048`; README text |
| D-006 | Settings list | M4.4 | `SettingsScreenTest.rowsInCanonicalOrderWithDefaults`; `SettingsRepositoryTest.defaultsMatchCatalog` |
| D-007 | New game confirm; size chip | M2.3, M3.5, M3.9 | `GameScreenTest.newGameAsksWhenInProgress`, `cancelNewGameChangesNothing`; `GameViewModelTest.sizeSwitchLoadsSavedGameWithoutConfirmation` |
| D-008 | Win and game-over cards; share | M3.9 | `GameScreenTest.winCardInteractiveAtT0Plus800Full`, `gameOverCardContent`, `shareLaunchesChooserWithText`, `winCardFitsBoard`, `gameOverCardFitsBoard`, `cardButtonsNeverRenderDisabledColors`, `shareOnBothCardsNotInMenu`; `ShareTextTest.*` |
| D-009 | Hint, How to play, What's new, About | M2.3, M4.3, M4.5 | `GameViewModelTest.hintShownOnFreshInstall`, `hintShownWhileStartingGameHasNoMove`; `WhatsNewTest.*`; `HowToPlayScreenTest.*`; `AboutScreenTest.*` |
| D-010 | Gradle 9.7.1 wrapper | M0.7 | CI setup-gradle `validate-wrappers`; `gradle-wrapper.properties` diff (ARCH §2.4) |
| D-011 | AGP 9.4.1, built-in Kotlin | M0.8 | verify full builds; M6.1 A1 |
| D-012 | BOM + material3 1.5.0-beta02, no expressive opt-in | M0.8 | M6.1 A1; `check_optin_allowlist` |
| D-013 | SDK 37/37/24 | M0.8 | `check-apk` item 1 |
| D-014 | JDK 21, bytecode 17 | M0.8 | `settings.gradle.kts` check; CI Temurin 21 |
| D-015 | `google()` + `mavenCentral()` only | M0.8 | CI resolves without the Portal |
| D-016 | Exact catalog | M0.8 | M6.1 A1 |
| D-017 | Build-logic shape | M0.8 | M6.1 A2 |
| D-018 | Build-tools 37.0.0 for scripts | M0.12 | `check-apk` tool path |
| D-019 | Local-only mirror hooks | M0.8, M0.12 | `check_mirror_leak` |
| D-020 | Two modules | M0.8 | `settings.gradle.kts` `include` |
| D-021 | Engine purity and API | M1.1–M1.2 | `EngineImportsGuardTest.noClockRngOrAndroidInEngine` |
| D-022 | Manual DI | M2.2 | `TestAppContainer` in every container test; `ProcessDeathRestoreTest.everySizeRestoresExactly` |
| D-023 | Single activity, Nav3 | M0.9, M3.10 | `BackOnRootTest.*`; `GameScreenTest.menuOpensEveryDestination`; `LaunchSmokeTest.launchesIntoGameWithoutTopBar` |
| D-024 | State-first VM, conflated writer | M2.2–M2.3 | `InputBurstOrderingTest.threeSwipesApplyInOrder`; `ConflatedWriterTest.conflatesWithoutCancellingInFlightWrite` |
| D-025 | Tile ids, render phases | M2.3, M3.3 | `BoardPresenterTest.*`; `TileRecompositionTest.noTileRecompositionDuringSlide`; `BoardRecompositionBudgetTest` |
| D-026 | M3 1.5 API confinement; V2 window info | M3.2, M4.2 | M6.1 A3; compile under `allWarningsAsErrors` |
| D-030 | Board representation | M1.2 | `HistoryCodecTest.rejectsMalformedHistoryBytes`; `GameInvariants` assertions |
| D-031 | Direction codes | M0.10 | `DirectionTest` |
| D-032 | Slide and merge-once | M1.2 | `EngineGoldenTest.rowLeftGoldens`, `rowRightAndVerticalGoldens`; `EnginePropertiesTest.p2MergeOnceMatchesOracle`, `p7RotationAndTransposeCommute` |
| D-033 | Score | M1.2 | `EnginePropertiesTest.p4ScoreDeltaEqualsMergedValues` |
| D-034 | Start and spawn | M1.2 | `SpawnDistributionTest.fourRateAndUniformCells`; `EngineReplayGoldenTest.newGameVectors`; `EngineGoldenTest.noOpDrawsNothing` |
| D-035 | No-op move | M1.2, M2.3 | `EnginePropertiesTest.p3NoOpConsumesNothing`; `GameViewModelTest.invalidMoveEmitsNudgeOnly` |
| D-036 | Targets, statuses, Keep going | M1.2 | `StatusMachineTest.*`; `EnginePropertiesTest.p9WinReportedOnce`; `UndoTest.undoBelowKeepGoingResetsWin` |
| D-037 | Game over after spawn | M1.2 | `StatusMachineTest.overEvaluatedAfterSpawn`; `EnginePropertiesTest.p8GameOverEquivalence` |
| D-038 | SplitMix64 | M1.2 | `SplitMix64GoldenTest.vectorsAndBoundedDraws` |
| D-039 | Unlimited undo | M1.2 | `UndoTest.*`; `EnginePropertiesTest.p11UndoInvertsAcceptedMove` |
| D-040 | DataStore files | M2.2 | `PersistenceCorruptionTest.corruptGameFileAffectsOnlyItsSize` |
| D-041 | Schema versioning, fixtures | M2.2, M2.4 | `PersistenceFixturesTest.v1FixturesDecode`, `newerDocumentsDecodeWithDefaults`; `PersistenceCorruptionTest.invalidFinalizationIdentityIsCorruption` (missing `gameId`); `DataMigrationSeamTest` |
| D-042 | Fields and encodings | M2.2 | `PersistenceFixturesTest`; `PersistenceCorruptionTest.invalidFinalizationIdentityIsCorruption` (F07-AC23); `HistoryCodecTest`; `EnginePropertiesTest.p12HistoryBytesRoundTrip` |
| D-043 | Write policy | M2.3 | `GameViewModelTest.everyStateChangeSubmitsOneSave`, `pauseFoldsElapsedAndSaves`; `PlayClockTest.*`; `FinalizationRecoveryTest.everyDiskOutcomeCountsExactlyOnce` (F06-AC18), `overflowKeepsNewestEntriesAndPauseRetriesRecords` (F06-AC22, records retry at pause); `ConflatedWriterTest.reportsWrittenValuesAndRetriesFailedOne` (F07-AC31) |
| D-044 | Records and statistics | M2.2, M2.3, M4.4 | `RecordsMathTest.*` (incl. `finalizeIsIdempotentPerGameId`, `resetKeepsOnlyFinalizationIdentity`); `GameViewModelTest.newBestFiresOnceAboveBestAtStart`, `newGameAssignsIdentityAndRecoveryEntry` (F06-AC13), `newGameDropsOnlyWrittenEntries` (F06-AC23); `FinalizationRecoveryTest.*` (F06-AC14–AC22, F08-AC22); `ConflatedWriterTest.reportsWrittenValuesAndRetriesFailedOne` (F07-AC31); `StatsScreenTest.resetRequiresTwoConfirmations`; `StatsViewModelTest.overGameCountedBeforeNextNewGame`, `foldCountsEachGameIdOnce` |
| D-045 | Corruption handling | M2.2, M2.3 | `PersistenceCorruptionTest.*`; `GameViewModelTest.engineCheckFailureIsCorruption` (F07-AC32, D-214) |
| D-046 | Budgets | M2.2, M2.3 | `PersistenceBudgetTest.*`; `PersistenceCorruptionTest.invalidFinalizationIdentityIsCorruption` (17 recovery entries, F07-AC23); `FinalizationRecoveryTest.overflowKeepsNewestEntriesAndPauseRetriesRecords` (F06-AC22) |
| D-047 | Process death, no replay, one-shot presentations, per-scope retention | M2.3, M3.3–M3.6, M3.9, M3.11, M4.3 | `ProcessDeathRestoreTest.everySizeRestoresExactly`; `GameViewModelTest.presentationReportsSettleWithoutStateChange`; `BoardPresenterTest.settledKeepsIdentityAndIsStatic`; `RestoreNoMotionTest.reportsDoNotDisturbLivePresentation` (AC-RESTORE-2); `RecreateNoReplayTest.*`; `LayoutMotionTest.arrangementSwitchAfterPresentedMoveReplaysNothing` (F18-AC14, AC-LAYOUT-3) |
| D-048 | Backup rules | M0.9 | `BackupRulesTest.*` |
| D-050 | Swipe recognition | M3.4 | `SwipeClassifierTest.*`; `SwipeInputTest.commitsDuringDragOncePerGesture`, `onReleaseCommitsAtPointerUp`, `sensitivityThresholds` |
| D-051 | Gesture surface | M3.4, M3.3, M4.3 | `SwipeInputTest.dragStartingOnButtonDoesNotClick`, `swipeAnywhereOnGameContent`, `scrollModeMovesOnlyFromBoard` (M4); `BoardGeometryTest.sideMarginRespectsGestureInsets` |
| D-052 | No input queue | M2.3, M3.3 | `InputBurstOrderingTest.threeSwipesApplyInOrder`; `GameScreenTest.newMoveSnapsInFlightSlides`; `InterruptionTest` (AC-CONS-2) |
| D-053 | Keyboard, D-pad | M3.4, M3.7, M3.9 | `KeyboardInputTest.*`, including `cardKeysActLikeChrome`, `providesShortcutGroup` |
| D-054 | Modal overlays; Back | M3.4, M3.9, M3.10 | `SwipeInputTest.ignoredWhileModalOpen`; `KeyboardInputTest.ignoredWhileModalOpen` (dialogs and sheets); `BackOnRootTest.backWithWinCardGoesHome` |
| D-060 | `GameMotion` tokens | M3.2 | `MotionTokensTest` (AC-TOKENS-1); `MotionPlanTest.fullPlanUsesGameMotionTokens` |
| D-061 | Move timeline | M3.3 | `MoveTimelineTest` (AC-MOVE-1…7); `MotionFilmstripTest.mergeAndSpawnFilmstrip` |
| D-062 | Interruption | M2.3, M3.3 | `InterruptionTest`; `GameScreenTest.atMostThreeScoreFloats`; `GameViewModelTest.headerCarriesBoardEventAndEpoch` (F13-AC14); `ScoreMotionTest.resetsRemoveScoreFloats` (F13-AC15, AC-SCORE-5) |
| D-063 | Motion setting, reduced motion | M3.2 | `EffectiveMotionTest.*`; `ReducedMotionTest`; `MotionOffTest`; `AnimatorScaleTest` |
| D-064 | Signature moments | M3.3, M3.5, M3.9 | `WinSequenceTest`; `GameOverSequenceTest`; `NewBestTest`; `InvalidNudgeTest`; `NewGameCascadeTest`; `UndoCrossfadeTest`; `ScoreMotionTest` |
| D-065 | Haptic map | M2.3, M3.4 | `HapticsMapTest.*` (F14-AC1–AC7, AC-HAP-1–4), `invalidMoveSilentBelowApi30` |
| D-066 | Haptic implementation | M3.4 | `HapticsMapTest`; `check-apk` item 2 (no VIBRATE); M6.1 A6 |
| D-070 | Classic Refined default | M3.2, M4.2 | `PaletteContrastTest.*`; `GameThemeTest.paletteSelectionProvidesTilePalette`; goldens 1–4 |
| D-071 | Theme mode, Neon | M4.2 | `GameThemeTest.themeModeFollowsSystemAndOverrides`; goldens 10–11 |
| D-072 | Super tiles | M3.2, M4.2 | `PaletteContrastTest.superTileHexesMatchD072`; goldens 8–11 |
| D-073 | High contrast | M4.2 | `GameThemeTest.highContrastSettingInkAndOutline`, `systemContrastForcesHighContrast`; golden 12 |
| D-074 | Dynamic chrome only | M4.2 | `GameThemeTest.dynamicChromeLeavesTilesUnchanged`, `dynamicChromeIgnoredBelowApi31` |
| D-075 | Board geometry | M3.3, M4.3 | `BoardGeometryTest.classicGeometryAllSizes`, `boardSizePerMode`, `narrowWindowFloor`; goldens 29–35 |
| D-076 | Numeral font subset | M4.2 | `check_font`; golden Reads |
| D-077 | Label sizing, compact labels | M3.3 | `TileLabelsTest.compactLabelsBelowTwelveDp`, `sizeByCharacterCount` |
| D-078 | Tile effects | M3.3, M4.2 | `MergePopTest` (AC-MOVE-9, AC-MOVE-10); goldens 8–11 |
| D-079 | Material Symbols vectors | M3.5, M3.9, M3.10, M4.3–M4.5 | M6.1 A6 (`material-icons` absent); Lint |
| D-080 | Board semantics | M3.3 | `BoardSemanticsTest.*` |
| D-081 | Announcements | M4.3 | `TalkBackModeTest.statusLineDescribesMove`, `cardsAnnounceAndTakeFocus`, `statusLineAfterUndo`, `statusLineAfterNewGameAndSizeSwitch` |
| D-082 | TalkBack pad | M4.3 | `TalkBackModeTest.padAndStatusLineFollowTouchExploration`; golden 36 |
| D-083 | Contrast and CVD | M4.2 | `PaletteContrastTest.inkContrastAndAdjacentDeltaE` |
| D-084 | Touch targets, font scale, focus, long-score fit | M3.5, M4.3 | `assertTouchTargets()` on every screen golden; `GameScreenTest.chromeTextNotTruncatedAtFontScale2`, `longScoresFitGameOverCard`, `longScoresFitHeaderWithoutTruncation` (F17-AC23); `ScoreFitTest.fallbackChainTable` (F17-AC22); goldens 27–28; `KeyboardInputTest.boardTakesFocusAndTabReachesControls` |
| D-085 | ATF | M0.13 onward | `atfRule` in every screen-level screenshot class (F17-AC16) |
| D-090 | Orientation, manifest | M0.9 | `ManifestPolicyTest.noOrientationLockOrConfigChanges`, `appCategoryIsGame`, `touchscreenNotRequired`; `check-apk` item 3 |
| D-091 | Layout modes (the preference) | M4.3 | `LayoutModeTest.modeTableAndBoundaries`; `BoardGeometryTest.resolvedLayoutTable`; goldens 29–35 |
| D-092 | Window size API; qualifiers | M4.3 | `LayoutScreenshotTest.qualifierMatrixGoldens` |
| D-093 | Edge-to-edge, Immersive | M0.9, M4.3 | `ImmersiveModeTest.*`; `GameThemeTest.systemBarIconsFollowEffectiveTheme` |
| D-094 | Predictive back | M3.10 | `BackOnRootTest.singleBackFromRootFinishes`; `PredictiveBackTest` (AC-BACK-1, AC-BACK-2) |
| D-095 | Recreation, mode transitions | M2.3, M4.3 | `RecreateNoReplayTest.rotationKeepsStateStatic`; `LayoutMotionTest` (AC-LAYOUT-1), `LayoutMotionTest.arrangementSwitchRendersSettledBoard` (F18-AC13, AC-LAYOUT-2), `LayoutMotionTest.arrangementSwitchAfterPresentedMoveReplaysNothing` (F18-AC14, AC-LAYOUT-3) |
| D-100 | 21 locales complete | M5.3 | Lint `MissingTranslation`; `TranslationQualityTest.everyKeyResolvesNonBlankInEveryLocale` |
| D-101 | Legacy string migration | M0.5 | L10N §1.2 check; `check_no_bom`; `LocalizationTest.appNameUntranslatedInEveryLocale`, `legacySettingsTranslationsKept` |
| D-102 | Locale config, per-app language | M0.8, M4.4 | `LocalizationTest.localeConfigListsTwentyOneLocales`; `SettingsScreenTest.appLanguageRowOpensSystemPage`, `apiGatedRowsAbsentOnApi24` |
| D-103 | Plurals, digits | M3.5, M5.3 | `FormattingTest.*`; `LocalizationTest.pluralsDistinguishRussianCategories`, `gameNumeralsAsciiInPersian`; `TranslationQualityTest.pluralsCarryTheNumber`, `noNonLatinDigits` |
| D-104 | RTL | M3.3, M3.4, M4.3 | `SwipeInputTest.leftSwipeIsLeftInRtl`; `TalkBackModeTest.directionPadForcedLtrUnderPersian`; golden 23 |
| D-105 | Translation provenance, glossary | M5.1, M5.3, M5.4 | glossary commit precedes translations (`git log`); README sentence; `TranslationQualityTest` |
| D-106 | Pseudo-locales | M0.8, M5.5 | goldens 25–26 |
| D-110 | Identity | M0.8 | `check-apk` item 1 (both APKs) |
| D-111 | Launcher icon | M0.4 | `ManifestPolicyTest.launcherIconIsAdaptiveWithMonochrome`, `legacyLauncherIconOnApi24`; `check_icon_glyphs` |
| D-112 | Splash | M0.4, M2.3 | `SplashGateTest.*`; `ManifestPolicyTest.splashThemeUsesIdentityColors` |
| D-113 | Store tree, 22 locales | M0.5, M5.3 | `check_store_listing` |
| D-114 | Store text | M5.3, M5.4 | `check_store_listing` (limits, deny-words, needles) |
| D-115 | Store graphics from code | M5.6 | `check_store_images`; L-AC9 |
| D-116 | README | M0.14, M5.4 | `check_legal_and_privacy` (links, permission name) |
| D-120 | LICENSE | M0.14 | `check_license_file`; LICENSE sha256 (M5) |
| D-121 | NOTICE | M5.4 | `check_legal_and_privacy` |
| D-122 | In-app licenses, coverage | M0.12, M4.5 | `check-licenses`; `LicensesCoverageTest.everyRuntimeGroupHasAnEntry`; `AboutScreenTest.licensesGroupedMitOflApache` |
| D-123 | Privacy | M4.5, M5.4 | `AboutScreenTest.privacyTextMatchesPolicy`; `check_legal_and_privacy` |
| D-124 | Credits, legacy history | M0.3, M4.5 | `AboutScreenTest.creditsRows`, `legacyHistoryListsNineReleases` |
| D-130 | 3.0.0 / 30000 literals | M0.8 | `check-apk` item 1; `AppContainerTest.versionComesFromBuildConfigStandardFields` |
| D-131 | Public sideload key | M0.11 | `check-apk` item 6 |
| D-132 | Build types | M0.8 | `check-apk` items 1, 4; `mapping.txt` in the CI artifact |
| D-133 | No release workflow | all | M6.1 A9 |
| D-134 | Reproducibility hygiene; BuildConfig (standard fields) as the only version source | M0.8, M2.2 | M6.1 A7; `repro-probe.sh` (M6.3); `AppContainerTest.createDoesNotTouchPackageManager`, `versionComesFromBuildConfigStandardFields` |
| D-140 | `verify.sh` is the gate | M0.12 | CI step 7 runs it |
| D-141 | Spotless + ktfmt 0.65 | M0.8 | `spotlessCheck` |
| D-142 | Lint as errors | M0.8 | `:app:lintDebug`; `check_no_baselines` |
| D-143 | `allWarningsAsErrors`, no opt-ins | M0.8 | compile; `check_optin_allowlist` |
| D-144 | detekt | M0.8 | `detektMain detektTest` |
| D-145 | Engine tests, Kover | M1 | engine suites; `koverVerify` |
| D-146 | App test stack | M0.8, M0.13 | `robolectric.properties`; `LaunchSmokeTest` at SDK 24 and 37 |
| D-147 | Goldens | M0.13 onward | `Goldens.kt`; `check-test-counts` golden rules G1–G5 (QUALITY §1.15); `check_golden_budget`; Read rule |
| D-148 | APK gates | M0.12 | `check-apk` items 1–7 |
| D-149 | Test-count floor | M0.12 | `check-test-counts` |
| D-150 | Performance budgets | M1, M2, M3 | `EnginePerformanceTest`; `PersistenceBudgetTest`; `TileRecompositionTest`; `check-apk` item 4; `check_font` |
| D-151 | CI workflow (refined by D-177) | plan PR | CI run with `present=true` green from M0 |
| D-152 | Metadata, font, license checks | M0.12, M4.2, M5.7 | `check-metadata` functions |
| D-153 | Not gated | all | M6.1 A9 |
| D-160 | Two PRs | plan PR, §6 | PR history |
| D-161 | Branch | §6.3 | `git log` linear; no force-push after PR open |
| D-162 | Milestones | §4–§5 | PR milestone checklist |
| D-163 | Local gate per push | §6.4 | commit `Gates:` lines; handoff logs (D-194) |
| D-164 | Commit conventions (clarified by D-180) | §6.7 | trailers kept verbatim; D-180 term grep before each push |
| D-165 | PR mechanics and body | §6.9 | PR body |
| D-166 | Codex triggers | §6.10 | review-rounds table |
| D-167 | Clean signal | §6.10 | review-rounds table |
| D-168 | Merge gate and valve | §6.11 | merge with the expected head SHA (D-195) |
| D-169 | Handling findings | §6.10 | thread replies |
| D-170 | Merge method | §6.11 | `master` history |
| D-171 | AGENTS.md | plan PR (root), M0.10 (engine) | files exist with `## Code Review Rules` (D-192) |
| D-172 | Stop conditions | §6.12 | PR "Blockers" section |
| D-173 | Change control | §3 (planning entries); execution-time entries from D-212 | PR "Deviations" section |
| D-174 | ATF opt-in file | M0.13 | `check_optin_allowlist` |
| D-175 | Spotless `docs/**` exclusion | M0.8 | `spotlessCheck` passes with research files present |
| D-176 | Test inputs, no cached results | M0.8 | QUALITY V7 behavior: a golden-only change re-runs `:app:testDebugUnitTest` |
| D-177 | Plan PR scope | plan PR | `ci.yml`, root `AGENTS.md`, `CLAUDE.md` unchanged by M0 |
| D-178 | `verify.sh` levels; one local build at a time | M0.12, §6.2 | `./scripts/verify.sh --help`; handoff log paths and final lines (§6.4) |
| D-179 | M0 check additions | M0.12 | each new function fails on a seeded defect once during M0 (QUALITY V14–V15); `check-test-counts.sh` seeds S1–S7 (QUALITY §1.15, S4 = all captures removed → G1); failing lines in the M0 commit body |
| D-180 | No model identifiers in tracked files | §6.4 step 11, §6.7 | orchestrator term grep before each push (S15) |
| D-181 | Sheet opt-in file | M3.8, M3.9 | `check_optin_allowlist` |
| D-182 | Epoch presentation | M2.3, M3.3 | `BoardPresenterTest.undoEpochHasOnlyStaticTiles`, `newGameEpochHasOnlyStaticTiles`, `settledKeepsIdentityAndIsStatic`; `UndoCrossfadeTest` (AC-UNDO-1) |
| D-183 | Motion tokens and timing | M3.1–M3.3 | `DelayedSpecTest`; `SpawnDelayTest` (AC-MOVE-11); `MergePopTest` (AC-MOVE-8, 10, 12); `GameOverSequenceTest` (AC-OVER-2, 5); M6.1 A4 |
| D-184 | Level-dependent `MotionScheme`; platform dialog and snackbar fades | M3.2 | `ReducedMotionTest`; `MotionOffTest` (sheets snap under Off) |
| D-185 | Haptic ownership, timing, interruption | M2.3, M3.4 | `HapticsMapTest` (F14-AC1 `priorityResolution`, AC-HAP-3, `invalidMoveSilentBelowApi30`); `ReducedMotionTest` (AC-RED-2 haptics) |
| D-186 | Undo snackbars | M3.5, M4.3 | `GameScreenTest.undoUsedSnackbarUntilFirstFinalizedUndoGame`, `nothingToUndoSnackbarRateLimited`; layout goldens |
| D-187 | Effect layers, "+N" pill | M3.3, M3.5 | `WinSequenceTest` (AC-WIN-2, AC-WIN-3); `ScoreMotionTest`; golden Reads |
| D-188 | Layout amendments | M4.3, M4.6 | `BoardGeometryTest.boardSizePerMode`, `narrowWindowFloor`, `resolvedLayoutTable`, `boardNeverBelowFloorOrOffWindow`, `growingHeaderKeepsBoardAndChromeInWindow` (F18-AC15); `LayoutModeTest`; `GameScreenTest.shortWindowsKeepBoardAndChromeOnScreen`, `scrollModeCardBringsBoardIntoView`; `SwipeInputTest.scrollModeMovesOnlyFromBoard`; `BoardSemanticsTest.traversalOrderInAllLayoutModes`; goldens 27, 29–36 |
| D-189 | Theme token additions | M4.2 | `PaletteContrastTest` (chrome pairs); `GameThemeTest.paletteSelectionProvidesTilePalette` |
| D-190 | Listing, image and legal checks; opaque PNGs | M5.6, M5.7 | `check_store_listing`, `check_store_images`, `check_legal_and_privacy`, `check_legal_texts`, `check_strings_final` |
| D-191 | Reproducibility probe | M6.3 | probe result line in the PR body |
| D-192 | `## Code Review Rules` heading | plan PR (root), M0.10 (engine) | verification-pending item 11 on the plan PR's Codex round |
| D-193 | PR ready for review from M0; push gate | §6.4, §6.9 | PR history (opened ready for review); one green `verify` run per pushed milestone head |
| D-194 | Roles and push handoff | §6.1, §6.4 | handoff records; every push and GitHub action comes from the orchestrator |
| D-195 | GitHub operations via the API | §6.2, §6.8–§6.11 | PR and run history; merge with the expected head SHA |
| D-196 | Rounds, clean signal, resolution, escalation | §6.10–§6.12 | review-rounds table; ledger block in the PR body |
| D-197 | Branch re-cut check | §6.3 | re-cut log in the PR body |
| D-198 | Records and statistics readings | M2.2, M4.4 | `RecordsMathTest.*`; `StatsViewModelTest.overGameCountedBeforeNextNewGame` (F08-AC18), `foldCountsEachGameIdOnce` (F08-AC21) |
| D-199 | Chrome and keys on cards | M3.9 | `KeyboardInputTest.cardKeysActLikeChrome` (F16-AC11); `GameScreenTest` card ACs |
| D-200 | Card fit, button rendering, Share on the win card | M3.9 | `GameScreenTest.winCardFitsBoard`, `gameOverCardFitsBoard`, `cardButtonsNeverRenderDisabledColors`, `shareOnBothCardsNotInMenu`; goldens 13, 15 |
| D-201 | Hint condition | M2.3, M4.3 | `GameViewModelTest.hintShownWhileStartingGameHasNoMove` (F22-AC2), `hintHiddenAfterAMove` (F22-AC10) |
| D-202 | TalkBack status completeness | M4.3 | `TalkBackModeTest.statusLineAfterUndo`, `statusLineAfterNewGameAndSizeSwitch` (F17-AC19, F17-AC20), `statusLineDescribesMove` |
| D-203 | Keyboard shortcuts helper | M3.7 | `KeyboardInputTest.providesShortcutGroup` |
| D-204 | 45 goldens, filmstrip names, ATF scope | M0.13, M3–M5 | M6.1 A11; `atfRule` in every non-`motion_*` screenshot class |
| D-205 | Contrast method, touch-target rule | M4.2, every screen-level screenshot class | `PaletteContrastTest.inkContrastAndAdjacentDeltaE`; `assertTouchTargets()` |
| D-206 | High-contrast empty-cell stroke | M4.2 | golden 12 Read |
| D-207 | Classic dark 2-tile fill | M3.2, M4.2 | `PaletteContrastTest` |
| D-208 | Localization readings | M3–M5 | Lint (`MissingTranslation`, `Untranslatable`); `TranslationQualityTest`; `check_strings_final` |
| D-209 | "gurshabad" credit | M0.3, M4.5 | `AboutScreenTest.creditsRows`; `legacy-credits.md` |
| D-210 | Motion verification policy | M3 | `MotionTokensTest` and the motion classes of MOTION_SPEC §8.5 |
| D-211 | Milestone refinements | §4, §5 | PR milestone checklist; A12 report at every milestone gate |

---

## 8. Risk register

Likelihood and impact: L (low), M (medium), H (high).

| # | Risk | L | I | Mitigation and fallback | Owner |
|---|---|---|---|---|---|
| R1 | material3 1.5.0-beta02 ships in the release APK (beta behavior changes or bugs) | M | M | All 1.5-only calls confined to `ui/theme` and `ui/components` (D-026, M6.1 A3); goldens catch visual regressions. A blocking beta bug is worked around in the wrapper; a fallback to 1.4.0 needs an execution-time D-173 entry because 1.4.0 keeps `MaterialExpressiveTheme` internal | M3, M4 |
| R2 | Robolectric SDK 37 fragility (`LaunchSmokeTest` at 37; espresso 3.5.1 crash) | M | M | espresso 3.7.0 forced (D-016); goldens at SDK 36; SDK 24 and 37 proven on JDK 21 (QUALITY V4). If SDK 37 breaks after a dependency-free change, record the failure and keep 24 + 36 only through a D-173 entry | M0 |
| R3 | Maven Central 429 in the sandbox (Gradle and Robolectric jars) | H | M | Out-of-repo init script and `-ProbolectricRepoUrl` via `VERIFY_GRADLE_ARGS` (QUALITY §3.3); `check_mirror_leak`; CI never uses the mirror and proves the build without it | M0–M6 |
| R4 | Screenshot bytes differ between local and CI (verification-pending 1) | L | H | M0 has one golden so parity is proven before the matrix exists; QUALITY §1.9 adoption procedure; D-147 threshold 0.001 fallback | M0, M3–M5 |
| R5 | Codex clean-signal ambiguity: 👍 authorship may not be readable; Codex on GitHub reports only P0/P1, so the valve may never apply | M | M | D-196 clean signal (a bot 👍, a no-finding review on the head, or a no-issue comment) with its authorship fallback; verification-pending items 11–12; findings tracked by head SHA; round-10 escalation (D-196) | M2, M6 |
| R6 | Token cannot push workflow files | L | H | Proven by the plan PR push (D-177; verification-pending item 2); the execution PR does not modify `ci.yml`. If a later change to `ci.yml` is needed and rejected: D-172 item 1 | Plan PR, M0 |
| R7 | Review depth on a giant PR (hundreds of files, 45 PNGs, 3,180 translations) | H | M | Mid checkpoint after M2 reviews the irreversible engine and data model while the diff is small; milestone commits and the PR "Review map"; concise P0/P1-focused `AGENTS.md` rules; the final round reviews the full diff | M2, M6 |
| R8 | Translation quality with no fluent reviewer | H | M | Glossary first (D-105); `TranslationQualityTest` 9 rules; Lint plural and format checks; back-translation table in the PR body; README discloses machine assistance | M5 |
| R9 | 7×7 and 8×8 label fit (compact labels, wide `M`, Neon wght 900) | M | M | DS §3.3 auto-fit guard; `TileLabelsTest` full table (DS §3.4); goldens 6–7 Read; FEATURES F02-AC9 | M3, M4 |
| R10 | DataStore rewrites the full game document on every move (I/O cost, jank on slow storage) | M | M | Compact Base64 documents (≈ 40 KB at 10,000 moves); conflated writer on `Dispatchers.IO`, never cancelled; `PersistenceBudgetTest` ≤ 50 ms encode+decode; no I/O on the input path (ARCH §4.8) | M2 |
| R11 | Actions do not trigger on the execution PR | L | H | D-195 steps (§6.8: PR targets `master`, one dispatch); then D-172 item 1 | M0 |
| R12 | detekt 2.0.0-alpha.6 crashes | M | L | D-144 exit criterion (QUALITY §2.4): one configuration fix, then remove detekt in one commit | M0–M6 |
| R13 | ktfmt 0.65 not idempotent on larger sources (verification-pending 6) | L | L | Run `spotlessApply` twice on M1 and M3 sources and confirm `spotlessCheck`; fallback 0.64 (D-141) recorded | M1, M3 |
| R14 | Robolectric cannot observe some facts: manifest attributes, system contrast, locale config, immersive insets, predictive-back progress | M | M | Seams adopted up front (§2.6 E); fallbacks of FEATURES OI-F13 and MS AC-BACK-1, each recorded in the PR body | M3, M4 |
| R15 | CI time nears the 45-minute timeout (≈ 90 app test classes, 45 goldens) | M | M | Measure at M3 and M4; if the local warm full gate exceeds 20 min or the CI warm run exceeds 30 min, set `maxParallelForks = 2` on `:app:testDebugUnitTest` with a D-173 entry (QUALITY §3.2) | M3, M4, M6 |
| R16 | Push protection rejects `signing/ci-sideload.p12` | L | H | D-172 item 5: stop and report | M0 |
| R17 | Google Sans Flex source or fontTools unavailable | M | L | D-076 fallback (§5.4) | M4 |
| R18 | Material Symbols downloads fail | L | M | Retry; then a sparse clone of `google/material-design-icons` at a pinned commit; if both fail, D-172 item 4 | M3 |
| R19 | The execution checkout lacks the submodule history, so credits cannot be captured | M | M | M0.1 runs `git submodule update --init assets/2048` before M0.3; M0.3 fails loudly if `git -C assets/2048 log fc1ef4f..7a477eb` errors | M0 |
| R20 | Timing tests flake on CI (2 s engine, 50 ms persistence, 200 ms confetti) | M | M | Warm-ups and medians as specified; one rerun of the failed jobs; no budget raise without a D-173 entry and measurements | M1–M3 |
| R21 | R8 breaks release-only behavior (serializers, Nav3 keys), and no test runs the minified APK | L | H | Explicit `NavKeysConfiguration` (ARCH §7.1); kotlinx-serialization's own R8 rules; no named companions (D-134, M6.1 A7); no reflective serializer lookup; `mapping.txt` kept as an artifact; residual risk documented | M2, M3 |
| R22 | Memory pressure from parallel Gradle builds | M | M | One local build at a time through `flock`; detached, logged full runs (D-178, §6.2) | M1–M5 |
| R23 | Long hands-off run loses context or drifts | M | M | Milestone commits; the PR body as the progress record (checklist, rounds table, ledger); §6.13 resume procedure | all |
| R24 | Golden budget > 10 MiB or APK > 3 MiB | L | M | QUALITY estimates ≈ 5 MiB goldens and 1.0 MiB skeleton APK; sizes printed on every verify; investigate before any budget change | M4, M6 |
| R25 | Codex not installed on the fork, or silent | L | H | D-196 re-post rule; D-172 item 2 | M2 |
| R26 | Codex ignores or over-applies `## Code Review Rules` | L | M | Verification-pending item 11 fallback: narrow or remove the noisy rule group in a `docs:` commit (D-192) | Plan PR |
| R27 | Automatic Codex reviews on every push | M | L | Handled like triggered reviews and not counted as rounds (D-193, D-196); the PR body records whether one appeared on the M0 head | M0–M6 |

---

## 9. Resolved during planning

Every open issue raised by the spec writers, with its disposition. "D-NNN" and "TC-n" refer to `DECISIONS.md`; "risk" to §8. Items that stay open until first use are in §10.

| Source | Issue | Resolution |
|---|---|---|
| DECISIONS OI-1 | Commit-during-drag default vs research recommendation | Kept (O3); the "When you lift your finger" setting ships |
| DECISIONS OI-2 | Plugin resolution without the Portal never smoke-built | Resolved: ARCH §0 built it Portal-free; D-015 holds |
| DECISIONS OI-3 | Font scope "tile numerals + title" | D-076's reading kept (digit-only game numerals) |
| DECISIONS OI-4 | Round-10 stop beyond U4 | D-196 (the orchestrator's stop rule; U4's valve unchanged) |
| DECISIONS OI-5 | Codex severities and 👍 | D-196; risk R5 |
| DECISIONS OI-6 | What's new cannot appear in v1.0 | Noted in D-009; `WhatsNewTest` plus golden 44 |
| DECISIONS OI-7 | Beta material3 in release | Risk R1 |
| DECISIONS OI-8 | 22 store directories | D-113's 22 directories |
| Verification-pending 1 | Local vs CI goldens | Open until the M0 CI run (§5.0 gate step 4); risk R4; §10 |
| Verification-pending 2 | Workflow push scope | Open until the plan PR's first push (D-177); risk R6; §10 |
| Verification-pending 3, 4, 7, 9 | Kover DSL, pseudo-locales, Base64, V2 window info | Resolved by ARCH §0 |
| Verification-pending 5, 10 | Robolectric SDK 24 on JDK 21; Roborazzi ATF | Resolved by QUALITY V4, V5 (ATF with D-174) |
| Verification-pending 6 | ktfmt 0.65 idempotence | Open, checked in M1 and M3; risk R13; §10 |
| Verification-pending 8 | Feature graphic color type | Replaced by `OpaquePngWriter` (D-190) |
| Verification-pending 11, 12 | Codex follows `## Code Review Rules`; reaction authorship readable | Open, checked on the plan PR's Codex round (D-192, D-196); risks R5, R26; §10 |
| ARCH OI-A1 | keytool escaping | TC-1 |
| ARCH OI-A2 | Badging and signer spelling | TC-2 |
| ARCH OI-A3 | `ui-tooling` exported `PreviewActivity` | Not on the classpath; no `@Preview`; PR-body note |
| ARCH OI-A4 | Spotless and research files | D-175 |
| ARCH OI-A5 | Brief names vs D-021 | D-021 names |
| ARCH OI-A6, MS-OI-1 | `Static` tiles vs epoch animations | D-182 |
| ARCH OI-A7 | Invalid moves do not snap slides | Accepted |
| ARCH OI-A8 | Verification items | Rows above |
| ARCH OI-A9 | detekt config path, Gradle-10 deprecation | TC-5 (root path); never `--warning-mode=fail` |
| DS-OI-1, FEATURES OI-F12 | Sheet opt-in | D-181 |
| DS-OI-2, DS-OI-3, DS-OI-7 | Margins, measured chrome, hint placement | D-188 |
| DS-OI-4, MS-OI-9 | "+N" color; canvas layers; sheen | D-187 |
| DS-OI-5, Q-OI-12, FEATURES OI-F5 (goldens) | Four extra goldens | D-204 (45 goldens) |
| DS-OI-6, Q-OI-8 | ΔE method; touch-target overlap check | D-205 |
| DS-OI-8, FEATURES OI-F11 | Undo snackbars | D-186 |
| DS-OI-9, DS-OI-10 | Neon surface; title color | D-189 |
| DS `captureScreenRoboImage` note | Experimental Roborazzi API | Dialogs and sheets captured by node (DS §10.2) |
| MS-OI-2, -3, -4, -6, -8, -13, -18, -19 | Token, timing and end-state extensions | D-183 |
| MS-OI-5 | Level-dependent `MotionScheme` | D-184 |
| MS-OI-7, MS-OI-17 | Haptic timing and interruption | D-185 |
| MS-OI-10 | JankStats overlay | Not built; device tools documented (MS §7.6) |
| MS-OI-11 | Dialog animations under Off | D-184 (dialogs and snackbars keep platform fades; the check is removed) |
| MS-OI-12 | Battery Saver | D-063 kept: not treated as Off |
| MS-OI-14 | Numeric differences from research/04 | TC-7; D-210 |
| MS-OI-15 | `Reject` on API 24–29 | D-185 (no invalid-move haptic below API 30; game-over `Reject` kept) |
| MS-OI-16 | Filmstrip frame names | D-204 |
| FEATURES OI-F1, OI-F8, OI-F9 | Streak and win definitions; statistics lag; reset vs `bestAtStart` | D-198 |
| FEATURES OI-F2, OI-F3 | Keyboard shortcuts on cards; chrome controls under a card | D-199 |
| FEATURES OI-F4, L-OI-5 | Added listing, legal and privacy checks; opaque PNGs | D-190 |
| FEATURES OI-F5 (inventory), Q-OI-7, L-OI-2, L-OI-4 | Test inventory, names, string keys | §2 (naming index); OI-F5's goldens: D-204 |
| FEATURES OI-F6 | Full decimal for exponents ≥ 63 | `BigInteger` in `TileLabels.fullDecimal` and `ShareText` |
| FEATURES OI-F7 | SDK 24 for API-absent tests | Resolved by QUALITY V4 |
| FEATURES OI-F10 | Hint after no move | D-201 |
| FEATURES OI-F13 | Robolectric observability of system contrast | Seam adopted (§2.6 E); its fallback stays open until first use (§10); risk R14 |
| FEATURES OI-F14 | Robolectric observability of immersive insets | Seam adopted (§2.6 E) |
| FEATURES OI-F15 | Numerals in share | Accepted as specified |
| Q-OI-1 | ATF opt-in | D-174 |
| Q-OI-2 | Test inputs, cache | D-176 |
| Q-OI-3 | `ci.yml` in the plan PR | D-177 |
| Q-OI-4 | `verify.sh` levels | D-178 |
| Q-OI-5 | Check-script additions | D-179 (M0), D-190 (M5); TC-2 |
| Q-OI-6 | Repro probe | D-191 |
| Q-OI-9 | Verification items | Rows above |
| Q-OI-10, Q-OI-11, Q-OI-13 | Migration seam test; seeded loops in `:app`; `cache-read-only: false` | Adopted as QUALITY states |
| L-OI-1, L-OI-3, L-OI-6, L-OI-7, L-OI-12 | `strings_pending.xml`; word-free format strings; `many` categories; no games plural; `appcompat-resources` | D-208 |
| L-OI-8, L-OI-14 | F-Droid recipe; Play-side items | Out of scope (D-004) |
| L-OI-9 | "gurshabad" credit | D-209 |
| L-OI-10, L-OI-11 | Duplicate menu keys; API 24–25 icon proportion | Accepted as L10N states |
| L-OI-13 | Greek upper-case tonos | Checked when Reading the el captures in M5; one-line fix in `ScoreCard.kt` if seen (§10) |
| L10N `Untranslatable` note | D-101 wording | TC-3 |
| L10N BOM finding | Lint misses a leading BOM | `check_no_bom` (D-179) |
| L10N observation | Untracked `.github/` | It is the plan PR's `ci.yml` (D-177) |
| This plan | Presenter model, timing mechanism, check-metadata union, milestone moves | D-182, D-183, D-190, D-211 |
| This plan | Attribution and model identifiers | D-180 |
| Plan review (2026-10-28) | Review heading, PR lifecycle, roles, GitHub API operations, rounds and escalation, re-cut check, spec reconciliations | D-192–D-211, TC-4–TC-7 |

---

## 10. Open issues of this plan

None beyond the verification-at-first-use items, each with its fallback: DECISIONS verification items 1, 2, 6, 11 and 12; MOTION_SPEC AC-BACK-1; FEATURES OI-F13; L10N L-OI-13.
