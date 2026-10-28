# QUALITY.md: tests, static analysis, verification gates, CI and review protocol

Status: plan document for the execution PR (D-160), dated 2026-10-28.

`DECISIONS.md` is canonical. This file turns the quality and process decisions (D-140–D-153, D-160–D-173, and the planning entries D-174–D-211 with Appendix B) into concrete test classes, scripts, commands, CI configuration and done-criteria. When this file and `DECISIONS.md` disagree, `DECISIONS.md` wins. Where this file extends or corrects an older decision, the DECISIONS entry that records it is cited by ID (for example D-174, D-176, D-179, TC-2). A deviation found during execution is an execution-time D-173 entry (D-212 and later).

Audience: the orchestrator and the implementer agents that execute M0–M6 (D-162, D-194) with no other context. Read `DECISIONS.md` and `ARCHITECTURE.md` first. This file owns:
- the test strategy and test inventory per layer (§1), with the generators, vectors, fixtures and budgets the tests use;
- the static-analysis configuration and the Gradle test-task integrity rules (§2);
- `scripts/verify.sh` and every `scripts/check-*.sh` (§1.13–§1.17, §3), as reference implementations;
- coverage (§4), the CI workflow `.github/workflows/ci.yml` (§5), the Codex review protocol (§6) and the Definition of Done (§7).

It does not restate feature acceptance criteria (`FEATURES.md` §1, test index §8), motion acceptance criteria (`MOTION_SPEC.md` §8–§9) or the golden list (`DESIGN_SYSTEM.md` §10). It points to them.

Notation:
- `D-NNN` is a decision in `DECISIONS.md`. `research/0N-….md §X` is a file under `docs/modernization/research/`.
- `[M0]` … `[M6]` is the milestone (D-162, D-211) that creates an item.
- `Q-OI-n` names a planning-time issue of this file; all are resolved (see [Open issues](#open-issues)).
- Paths are relative to the repository root. `R=clayboicardi/2048-android-modern-refresh` in shell snippets.
- Names of classes, files, tests and string keys are the canonical ones of `PLAN.md` §2.

---

## 0. Verification log for this document

Everything below was run on 2026-10-28 on the same kind of machine the implementer agents use (Ubuntu 24.04, JDK 21, Android SDK with `platforms;android-37.0` and build-tools 37.0.0). The Gradle runs used a private copy of the scratch project from `ARCHITECTURE.md` §0, extended with the scripts and build-file rules of this document. Maven Central was reached through the local-only mirror of D-019 (§3.3); nothing about it is in any committed file.

| # | Checked | Result |
|---|---|---|
| V1 | Action tags → commit SHAs, `git ls-remote https://github.com/<action> refs/tags/<tag> 'refs/tags/<tag>^{}'` | All five SHAs of research/06 §3.1 match (§5.8). `gradle/actions` v6.4.0 is an annotated tag (`b9bee63e…`) that peels to the pinned commit `3f5f9ada…` |
| V2 | `action.yml` of each pinned commit | setup-gradle: `cache-provider` (default `enhanced`), `cache-read-only` (default true off the default branch), `validate-wrappers` (default `true`). upload-artifact: `overwrite`, `if-no-files-found`, `retention-days`. checkout: `persist-credentials` (default `true`). All run on `node24` |
| V3 | `scripts/verify.sh` (§3.1) on the M0-shaped scratch project | `full` PASS in 29 s warm; 101 s with all build directories deleted and `--no-build-cache` (dependencies already cached); `quick` 2 s; `--record` 42 s including the first download of the SDK 24 jar |
| V4 | `LaunchSmokeTest` with `@Config(sdk = [24, 37])` on JDK 21 | Both executions pass. SDK 24 uses `org.robolectric:android-all-instrumented:7.0.0_r1-robolectric-r1-i7` (88,212,080 B). Resolves DECISIONS "Verification-pending" item 5 |
| V5 | Roborazzi ATF 1.77.0 under Robolectric 4.17, SDK 36 (item 10) | Works: a clean screen passes; a 12 dp unlabeled clickable fails with `SpeakableTextPresentCheck` and `TouchTargetSizeCheck`. **But** every ATF API (`RoborazziATFAccessibilityCheckOptions`, `RoborazziATFAccessibilityChecker`, `CheckLevel`, `AccessibilityCheckAfterTestStrategy`, the `roborazziAccessibilityOptions` parameter) is `@ExperimentalRoborazziApi` (`RequiresOptIn`, level WARNING). Under `allWarningsAsErrors` the build fails with 7 "This declaration needs opt-in" errors. A file-level `@file:OptIn` in one test file fixes it (D-174) |
| V6 | Touch-target sizes | Compose extends every pointer-input node's touch bounds to 48 dp (measured: a 40 dp `clickable` Box and an M3 `IconButton` report layout 40 × 40 dp, touch 48 × 48 dp). ATF's check accepted 32 dp at the screen edge. `assertTouchTargets()` (§1.12) therefore checks size **and** non-overlap: two adjacent 40 dp clickables fail, spaced M3 buttons pass |
| V7 | A golden-only change with `ARCHITECTURE.md` §2.6 as written | `:app:testDebugUnitTest UP-TO-DATE`, `BUILD SUCCESSFUL`: a wrong golden passes. With the §2.6 rules of this file the task re-runs and fails, and `launch_placeholder_actual.png` / `_compare.png` appear in `app/build/outputs/roborazzi/` (D-176) |
| V8 | `outputs.cacheIf { false }` on test tasks | With build directories deleted, compile tasks load `FROM-CACHE` and both test tasks execute |
| V9 | Roborazzi result files | `app/build/test-results/roborazzi/debug/results/*.json` and `results-summary.json`, each with an absolute `golden_file_path` and a `type` (`recorded`, `unchanged`, …). The directory is rewritten on every run. A seeded orphan golden was detected (§1.15) |
| V10 | Kover 0.9.11 thresholds (`ARCHITECTURE.md` §2.7 DSL) | An uncovered branchy function made `koverVerify` fail on both rules ("lines covered percentage is 53.846200, but expected minimum is 90"; branches 33.3 % vs 85) |
| V11 | Reproducibility probe (§1.17) | The release APK is bit-identical across the checkout, a fresh clone at another path built with `--no-build-cache`, and a rebuild after `clean` (equal sha256). A build in a `git worktree` differs only in `META-INF/version-control-info.textproto` (`generate_error_reason: NO_VALID_GIT_FOUND`), so the probe uses a clone |
| V12 | `check-apk.sh` (§1.13) on the scratch debug and release APKs | All assertions pass. `apksigner verify --print-certs` prints `V2 Signer: certificate SHA-256 digest: …` for these minSdk-24 APKs, not `Signer #1 …`; the script parses any `Signer` line (technical correction TC-2). A wrong `signing/ci-sideload.sha256` fails both APKs |
| V13 | `check-licenses.sh` (§1.16) | The skeleton's `releaseRuntimeClasspath` resolves 37 groups, all Apache-2.0, including `androidx.appcompat` (`appcompat-resources`, pulled by `core-splashscreen` 1.2.0), `com.google.guava` (`listenablefuture`), `com.squareup.okio` and `org.jspecify`. Missing, stale and malformed lines each fail |
| V14 | `check-metadata.sh` (§1.16) | Every assertion was triggered once with a seeded defect: mirror host in a tracked file, `.gitmodules`, a script without `+x`, a `lint-baseline.xml`, an opt-in outside the allowlist, a 40 KB font without its OFL text, a 4,001-character description, "WebView port" in an en-US text, a feature graphic with alpha |
| V15 | `check-test-counts.sh` (§1.15) | A floor above the executed count, a skipped test and an orphan golden each fail. The golden rules G1 and G3–G5 (an empty result set, a result without a golden, a result other than `unchanged`, an unreadable result) were added afterwards, in plan review, and were run only against synthetic result trees; M0 proves them in a real build with seeds S4–S7 of §1.15 |
| V16 | `.github/workflows/ci.yml` (§5) | Parses with PyYAML. The Detect step was run against five trees: this repository (`present=false`, exit 0), an M0-shaped tree (`present=true`), and three broken shapes (exit 1) |
| V17 | detekt task coverage | Root-level `detektTest` also runs `:app:detektDebugUnitTest`; it flagged `CyclomaticComplexMethod` in a first draft of the touch-target helper. §1.12 shows the refactored helper that passes |
| V18 | Seeded expectations of §1.3–§1.4 (transliteration of D-038 in Python) | The D-038 vectors reproduce. From seed `0x2048`: 60,000 × `nextInt(6)` gives 9,933–10,079 per value; 100,000 spawns give a 4-rate of 0.10154 and 16,551–16,751 per cell of 6; the `HistoryCodecTest` golden bytes match |

---

## 1. Test pyramid

### 1.1 Layers

```
                 ┌──────────────────────────────────────────┐  report-only, never a gate
                 │ reproducibility probe (§1.17)            │  M6 locally + workflow_dispatch
                 ├──────────────────────────────────────────┤
                 │ APK / manifest / repo gates (§1.13–1.16) │  scripts/check-*.sh, every verify
                 ├──────────────────────────────────────────┤
                 │ Roborazzi goldens + ATF + filmstrips     │  Robolectric SDK 36, 45 PNGs
                 ├──────────────────────────────────────────┤
                 │ Compose behaviour tests (Robolectric)    │  ui-test v2, SDK 36; smoke 24/37
                 ├──────────────────────────────────────────┤
                 │ ViewModel / repository / persistence     │  JVM, Turbine, real DataStore on temp files
                 ├──────────────────────────────────────────┤
                 │ Engine: golden + property + vectors      │  pure JVM, JUnit 6 + kotest-property, Kover
                 └──────────────────────────────────────────┘
```

| Layer | Module / source set | Framework (D-016 versions) | Runs in | Owner of the acceptance criteria |
|---|---|---|---|---|
| Engine unit, golden, vectors | `core/engine/src/test/kotlin/com/clayboicardi/game2048/engine/` | JUnit Jupiter 6.1.3 (`useJUnitPlatform()`), kotest-property 6.2.6 | `:core:engine:test` | `ARCHITECTURE.md` §3.5–§3.7, `FEATURES.md` F01–F05 |
| Engine coverage | same | Kover 0.9.11 | `:core:engine:koverVerify` | §4 |
| App JVM (persistence, ViewModels, pure UI logic) | `app/src/test/kotlin/com/clayboicardi/game2048/` | JUnit 4.13.2, kotlinx-coroutines-test 1.11.0, Turbine 1.2.1, Truth 1.4.5, DataStore 1.3.0 | `:app:testDebugUnitTest` | `FEATURES.md` §8 |
| Robolectric behaviour | same | Robolectric 4.17, Compose ui-test **v2**, espresso-core 3.7.0 (forced) | same | `FEATURES.md` §8, `MOTION_SPEC.md` §9 |
| Screenshot goldens + ATF | `…/screenshots/` + `app/src/test/screenshots/*.png` | Roborazzi 1.77.0 + `roborazzi-accessibility-check` | same, `-Proborazzi.test.verify=true` | `DESIGN_SYSTEM.md` §10 |
| Gates | `scripts/` | bash + python3 (preinstalled on `ubuntu-24.04`) | `scripts/verify.sh` | §1.13–§1.16 |

Rules for every test in both modules:
- **Block bodies only.** `@Test fun x() { … }`. Never `@Test fun x() = runBlocking { … }`: JUnit 6 silently skips a test whose return type is not `Unit` (research/03 §1.9). `= runTest { … }` is allowed (it returns `Unit`/`TestResult`).
- **Determinism.** No wall clock, no unseeded randomness, no network, no sleeps. Seeds are `0x2048` (or `0x2048 + i` in loops). Robolectric tests use `mainClock.autoAdvance = false` whenever timing matters.
- **One AC, at least one method.** The first KDoc line of a test method lists the ID of every criterion it proves (`/** F05-AC4 */`, or `/** F13-AC8, AC-GOLD-1 */` when several ACs assert the same fact), per `FEATURES.md` §0.1. The milestone AC audit (`PLAN.md` §5.7 A12, D-211) reads these lines.
- **No skips.** `@Ignore`, `@Disabled` and failed assumptions are not allowed; `check-test-counts.sh` fails on any skipped test (§1.15).
- **Floors.** Every commit that adds tests sets `config/test-count-floor.txt` to the new executed counts (§1.15).

### 1.2 Engine golden tests ported from the legacy game

The legacy rules live in `assets/2048/js/game_manager.js` and `grid.js`, audited in research/02-legacy-game.md §1 and research/06-quality-ci.md §1.1. The ported behaviours and the tests that freeze them:

| Legacy behaviour (source) | Test |
|---|---|
| Traversal from the wall the move goes toward (`game_manager.js:253-266`) | `EngineGoldenTest.rowLeftGoldens`, `rowRightAndVerticalGoldens` |
| A tile merges at most once per move (`mergedFrom`, `:202-204`) | `EngineGoldenTest.rowLeftGoldens` rows `2 2 2 2`, `4 2 2 0`, `8 8 8 0` |
| `score += merged.value` (`:213`) | `EngineGoldenTest` Δ column |
| Spawn only after a move that changed the board (`:228-229`); no-op consumes no randomness (research/02 harness T5) | `EngineGoldenTest.noOpDrawsNothing`, property P3 |
| 2 at 90 %, 4 at 10 % (`:115`); uniform empty cell (`grid.js:37-43`); value drawn before cell (D-034) | `SpawnDistributionTest`, `EngineReplayGoldenTest` |
| Game over is checked after the spawn; right/down neighbour scan is complete (`:231-233`, `:284-314`) | `StatusMachineTest`, property P8 |
| Win on the first exact target, reported once (`:216`, harness T10); win + no moves shows the win first (fork fix `6be2116`) | `StatusMachineTest`, property P9 |
| Input ignored while the win overlay is up (harness T9) → `Blocked(WON)`, and in `OVER` → `Blocked(OVER)` (D-036) | `StatusMachineTest.blockedInWonAndOver` |

`EngineGoldenTest` (`[M1]`) drives the internal `slide` of `engine/Slide.kt` (visible to tests in the same module) and `Game.move`. It holds the 13 row-left rows and 3 row-right rows of D-032 (the union of research/02 T6 and research/06 §1.1, as research/07 §1 "Minor inconsistencies" requires):

| Input (left) | Output | Δ | Moved |
|---|---|---|---|
| `2 2 2 2` | `4 4 0 0` | +8 | yes |
| `2 2 2 0` | `4 2 0 0` | +4 | yes |
| `4 2 2 0` | `4 4 0 0` | +4 | yes |
| `8 0 8 8` | `16 8 0 0` | +16 | yes |
| `2 2 4 4` | `4 8 0 0` | +12 | yes |
| `2 2 4 0` | `4 4 0 0` | +4 | yes |
| `4 4 8 0` | `8 8 0 0` | +8 | yes |
| `8 8 8 0` | `16 8 0 0` | +16 | yes |
| `2 0 2 4` | `4 4 0 0` | +4 | yes |
| `2 2 0 2` | `4 2 0 0` | +4 | yes |
| `0 0 0 2` | `2 0 0 0` | 0 | yes |
| `2 4 8 16` | unchanged | 0 | **no** |
| `1024 1024 0 0` | `2048 0 0 0` | +2048 | yes, `becameWon` on 4×4 |

Row-right: `2 2 2 2 → 0 0 4 4`, `2 2 2 0 → 0 0 2 4`, `8 0 8 8 → 0 0 8 16`.

Methods (each `@ParameterizedTest @MethodSource`; `junit-jupiter` already aggregates `junit-jupiter-params`):

| Method | Asserts |
|---|---|
| `rowLeftGoldens` | Each of the 13 row-left cases placed in each of the 4 row positions of an otherwise empty 4×4 board: output row, Δ and `moved` exactly as the table; the other rows unchanged (larger sizes: `largerBoards`) |
| `rowRightAndVerticalGoldens` | The 3 row-right cases, placed the same way; then all 16 cases mapped to RIGHT (row reversed), UP (row written down column c) and DOWN (column reversed): the output maps the same way |
| `largerBoards` | Each row-left case padded with trailing zeros to N = 5…8: output padded the same, same Δ |
| `noOpDrawsNothing` | Through `Game.move`: the `2 4 8 16` row returns `MoveOutcome.Invalid` and leaves the state, `rngState` included, unchanged (nothing drawn); an accepted move advances `rngState` by exactly `k · 0x9E3779B97F4A7C15` (mod 2⁶⁴) with `k ≥ 2`. Through the internal `spawnInto` with a counting `RandomSource`: exactly 2 `nextInt` calls, bound 10 first, then bound = the empty-cell count (value before cell, D-034) |
| `winRows` | `1024 1024 0 0` on 4×4 → `becameWon`; `128 128 0` on 3×3 (target 256, D-036) → `becameWon`; `128 128 0 0` on 4×4 → not won |

### 1.3 PRNG and replay golden vectors

| Test | `[M]` | Vectors (all from `DECISIONS.md` D-038 and `ARCHITECTURE.md` §3.6, §9.4) |
|---|---|---|
| `SplitMix64GoldenTest` | M1 | Seed `0`: `0xE220A8397B1DCDAF`, `0x6E789E6AA1B965F4`, `0x06C45D188009454F`, `0xF88BB8A8724C81EC`. Seed `0x2048`: `0xF7F3D05A9C290B51`, `0x390005F078622C95`, `0xC35B79300DF431F5`, `0x2B84F35B753B4E34`. From seed `0x2048`: `nextInt(10)` ×8 = `8, 6, 0, 0, 9, 9, 8, 3`; `nextInt(16)` ×8 = `8, 10, 10, 10, 1, 13, 4, 9`. `nextInt(0)` and `nextInt(-1)` throw `IllegalArgumentException`. `nextInt(1)` returns 0. Range: for every bound 1…64, 1,000 draws from seed `0x2048` lie in `0 until bound`. Uniformity: 60,000 draws of `nextInt(6)` give each value 10,000 ± 500 |
| `EngineReplayGoldenTest` | M1 | `newGame` for N = 3, 4, 8 from seed `0x2048` (cells, both spawns, `rngState = 0x78DDE6E5FD2A109C`); the 8-move replay table of `ARCHITECTURE.md` §3.6 (cells, score, Δ, spawn and `rngState` after every move); move 3's transitions `[(2,3,3), (1,3,3), (0,2,null), (5,7,null)]`, `mergeCount = 1`, `maxMergedExp = 2` |
| `HistoryCodecTest` | M1 | Golden bytes (below), every rejection case of `ARCHITECTURE.md` §3.4 (bad length, ordinal 4, exponent 128, non-zero high bits in the last log byte) |
| `LongGameGeneratorTest` (app) | M2 | The 10,000-move 8×8 game of `ARCHITECTURE.md` §9.4: win at move 1,678 (`keepGoingAtMove = 1678`), score 208,236, `rngState = 0x26E3A2DE9847F93C`, max exponent 12, `KEEP_PLAYING`, 313 snapshots, 2,500-byte packed log |
| Motion fixtures (`MOTION_SPEC.md` §8.6) | M3 | Each fixture's `ENGINE_SPAWN_CHECK`, e.g. `FIX_FILM` with `rngState = 0x2048`: value draw `nextInt(10) = 8` → exponent 1; cell draw `nextInt(14) = 2` → index 4 |

`HistoryCodecTest` golden bytes (hex literals in the test, so a layout change shows in review as a diff of the test, not of a binary file):

| Value | Bytes |
|---|---|
| `MoveLog` of `[UP, RIGHT, DOWN, LEFT, LEFT]` | `E4 03` (size 5; move i at byte i/4, bits 2·(i%4), LSB-first) |
| `Snapshot` of `newGame(BoardSize.of(3), 0x2048)` at move 0 | `00000000` `0000000000000000` `78DDE6E5FD2A109C` `00` `000001020000000000` (30 bytes: moveCount, score, rngState, status ordinal, 9 cells) |

A vector changes only through an execution-time D-173 entry. If Kotlin output differs from a vector, fix the Kotlin against `ARCHITECTURE.md` §3.3 first.

### 1.4 Engine property tests

`EnginePropertiesTest` (`[M1]`) asserts P1–P13 of `ARCHITECTURE.md` §3.5 (research/06 §1.1, adapted per D-145). Every property runs **1,000 iterations** from seed `0x2048` (technical correction TC-6). Every property test class has `@BeforeEach fun seed() { PropertyTesting.defaultSeed = 0x2048L; PropertyTesting.writeFailedSeed = false }`, and properties call `checkAll(Arbs.ITERATIONS, genA, genB) { … }` inside a block-bodied test that wraps `checkAll` in `runBlocking { }`. kotest's config-object overload of `checkAll` needs `@ExperimentalKotest` in 6.2.6 and is not used. A failure prints the shrunk counterexample; copy it into `EngineRegressionTest` (created on first need) as a named example test before fixing the bug.

Generators live in `core/engine/src/test/kotlin/com/clayboicardi/game2048/engine/Arbs.kt`:

```kotlin
object Arbs {
    const val ITERATIONS = 1_000

    val size: Arb<BoardSize> = Arb.int(BoardSize.MIN_N..BoardSize.MAX_N).map(BoardSize::of)
    val direction: Arb<Direction> = Arb.enum<Direction>()

    /** Exponents 0..17, weighted toward empty: 50 % empty, 40 % 2..2048, 10 % 4096..131072. */
    val exponent: Arb<Int> =
        Arb.choose(5 to Arb.constant(0), 4 to Arb.int(1..11), 1 to Arb.int(12..17))

    fun board(size: BoardSize): Arb<Board> =
        Arb.list(exponent, size.cellCount..size.cellCount)
            .map { e -> Board.of(size, ByteArray(e.size) { e[it].toByte() }) }

    val board: Arb<Board> = size.flatMap(::board)

    /** Full board with no equal orthogonal pair: each cell avoids its left and top neighbour. */
    val lockedBoard: Arb<Board> = arbitrary { rs -> /* row-major fill from 1..17 minus left/top */ }

    /** lockedBoard with one cell copied onto a neighbour, so exactly one merge exists. */
    val nearlyLockedBoard: Arb<Board> = arbitrary { /* … */ }

    /** A played game: seed, size and up to 300 directions; Invalid moves are skipped,
     *  WON is continued with Game.keepGoing before the next move. */
    val playedGame: Arb<GameState> = arbitrary {
        play(size.bind(), Arb.long().bind(), Arb.list(direction, 0..300).bind())
    }

    /** 3×3 games played from consecutive seeds until OVER (≤ 2,000 moves each). */
    val overGame: Arb<GameState> = arbitrary { /* … */ }
}
```

The bodies marked `/* … */` are the implementer's to write exactly as their KDoc says. Usage, after the `@BeforeEach` seeding above: `checkAll(Arbs.ITERATIONS, Arbs.board, Arbs.direction) { b, d -> … }`.

| # | Property (`ARCHITECTURE.md` §3.5) | Generator(s) | Assertion |
|---|---|---|---|
| P1 | Sum conservation | `board` × `direction`; `playedGame` × `direction` | Σ `tileValue` after `slide` == before; after an accepted move the sum grows by exactly the spawned value (2 or 4) |
| P2 | Merge-once equals a naive oracle | `board` × `direction` | Each line equals the oracle "drop zeros, merge equal neighbours left-to-right once, pad", applied in move order |
| P3 | No-op | `board` × `direction` | `Invalid` iff `slide` changes nothing; the returned state is `==` the input, `rngState` included, so nothing was drawn (`Game.move` owns its RNG; the counting `RandomSource` of `Arbs.kt` is used where an RNG is injectable, `spawnInto`) |
| P4 | Score delta | `playedGame` × `direction` | `scoreDelta == Σ tileValue(merged exponents)`; `score` never decreases except through `undo` |
| P5 | Tile count | `board` × `direction` | `tileCount(after slide) == tileCount(before) − mergeCount`; an accepted move then adds exactly 1 tile |
| P6 | Max tile monotonic | `playedGame` × `Arb.list(direction, 1..50)` | `maxExp` never decreases across accepted moves |
| P7 | Metamorphic | `board` × `direction` | For the 4 rotations and the transpose `T`: `slide(T(b), T(d)) == T(slide(b, d))` |
| P8 | Game over iff locked | mix of `board`, `lockedBoard`, `nearlyLockedBoard` (1:1:1 via `Arb.choice`) | `!canMove(b)` ⇔ no empty cell and no equal orthogonal pair ⇔ all 4 directions return `Invalid` |
| P9 | Win once | `playedGame` (all sizes) | `becameWon` is true at most once per game, exactly on the first merge that produces `targetExp`, never in `KEEP_PLAYING` |
| P10 | Determinism | `size` × `Arb.long()` × `Arb.list(direction, 0..300)` | `replay` twice gives `==` states (filtered to accepted move lists) |
| P11 | Undo | `playedGame` × `direction`; `overGame` | For every accepted `m = move(s, d)`: `undo(m.state)!!.copy(undoCount = s.undoCount) == s`; `undo` of an `OVER` state is `PLAYING` or `KEEP_PLAYING`; `undo` never returns `WON` or `OVER` |
| P12 | History bytes round-trip | `playedGame` | `MoveLog.fromPackedBytes(log.toPackedBytes(), log.size) == log`; `Snapshot.fromBytes(s.toBytes(), size) == s` for every snapshot |
| P13 | Spawn validity | `playedGame` × `direction` | The spawn lands on a cell that was empty after the slide, with `exp ∈ {1, 2}` |

Every engine test that produces a state also asserts `GameInvariants.violations(state).isEmpty()` (I1–I8).

Other engine suites (`ARCHITECTURE.md` §10.2):

| Class | `[M]` | Asserts |
|---|---|---|
| `DirectionTest` | M0 | `fromCode(code)` round-trips; `fromCode(4)` throws |
| `SpawnDistributionTest` | M1 | 100,000 spawns into a fixed 4×4 board with 6 empty cells (indices 0, 3, 5, 10, 12, 15), RNG continuing from `SplitMix64(0x2048)`: the 4-rate is in [0.095, 0.105]; each of the 6 cells receives 16,667 ± 10 % |
| `StatusMachineTest` | M1 | Win once; win + no moves stays `WON`; `keepGoing` → `KEEP_PLAYING`, or `OVER` when no move exists; 3×3 target 256; `Blocked(WON)` and `Blocked(OVER)`; `keepGoing` outside `WON` throws |
| `UndoTest` | M1 | `undo` at move 0 → `null`; across snapshot boundaries (moves 31, 32, 33, 64); from `WON`, `KEEP_PLAYING` and `OVER`; `keepGoingAtMove` reset when undoing below it (D-036); RNG restored: the same move after an undo yields the same spawn; `undoCount` increments and never decreases |
| `EngineImportsGuardTest` | M1 | Text scan of `src/main/kotlin` for the D-021 tokens (`ARCHITECTURE.md` §3.7). Its inputs are declared (§2.6) |
| `EnginePerformanceTest` | M1 | 100,000 accepted random moves on 8×8 in ≤ 2 s (D-150), timed with `System.nanoTime()` in the test only, after one 10,000-move warm-up |

### 1.5 Serialization fixtures

Committed, write-once fixtures (D-041), created in M2 and never edited afterwards:

| File (under `app/src/test/resources/fixtures/v1/`) | Content | `PersistenceFixturesTest` asserts |
|---|---|---|
| `settings.json` | Every field set to a **non-default** value: `themeMode DARK`, `palette NEON`, `highContrast true`, `dynamicChrome true`, `motion REDUCED`, `haptics false`, `moveTiming ON_RELEASE`, `swipeSensitivity HIGH`, `immersive true`, `keepScreenOn true`, `lastBoardSize 6`, `lastSeenVersionCode 30000`, `onboardingDone true` | Decodes to exactly those values (a field that silently fell back to its default would fail) |
| `records.json` | `FIX_RECORDS` of `DESIGN_SYSTEM.md` §10.3 for sizes "4" and "3", plus `tileReach` `[37,37,37,37,37,36,34,30,24,17,12,9]` for "4" and `[5,5,5,5,5,5,4,3,2]` for "3", plus `lastFinalizedGameId` `{"4": 6, "3": 5}` (D-044) | Every listed value; other sizes absent; `RecordsMath.aggregate` of it equals literals written in the test; together with `game-4.json`, `isFinalized(…, 4×4, 6)` and not `isFinalized(…, 4×4, 7)` (the consistency rule below) |
| `game-4.json` | `Game.replay(BoardSize.of(4), 0x2048, the 8 moves of ARCHITECTURE.md §3.6)`, then `undo`, then `UP` again; `elapsedMs = 61000`, `bestAtStart = 0`, `newBestAnnounced = false`, `gameId = 7`, `previousFinalization` = `{gameId 6, status OVER, score 3012, moveCount 302, elapsedMs 401000, undoCount 2, maxExp 7}` (D-044) | Cells `1 3 1 1 / 2 1 1 0 / 0 0 0 0 / 0 0 0 0`, score 16, `rngState = 0x5C55827DF1D1D1EC`, `moveCount = 8`, `undoCount = 1`, status `PLAYING`, 1 snapshot, `gameId 7` and every `previousFinalization` value; decode → encode → decode yields an equal `SavedGame` (not a byte comparison with the file: a later defaulted field would change the bytes legitimately, D-041) |
| `game-8-long.json` | `LongGameGenerator` output (§1.3; `gameId = 1`, no `previousFinalization`) | The `LongGameGeneratorTest` literals; `gameId 1`; `GameInvariants.violations` empty |

**Consistency rule** (D-041, D-044): the fixtures describe one coherent install, so a container that loads `records.json` and `game-4.json` together runs neither recovery step of `ARCHITECTURE.md` §4.9. The 4×4 watermark 6 equals the recovery entry's `gameId`, so R1 finds that entry already counted (6 ≤ 6), and it is below the game's `gameId` 7, so R2 does not treat game 7 as finalized (7 > 6). The 3×3 watermark 5 guards no fixture. A test that needs a finalized or an uncounted game builds it with `copy(gameId = …)` or its own `Records`, never by editing a fixture.

Write-once protocol (`ARCHITECTURE.md` §9.4): when a fixture is missing, the test writes the generated document to `app/build/generated-fixtures/<name>` and fails with "copy app/build/generated-fixtures/* to app/src/test/resources/fixtures/v1/ and commit". After the first commit the files are never regenerated. A later failure means the on-disk format changed: bump `schemaVersion` and add a `DataMigration` (D-041), never a new v1 fixture.

There are no separate binary fixture files. The two engine byte layouts are pinned as hex literals in `HistoryCodecTest` (§1.3), and inside the JSON fixtures they are Base64 (D-042).

Downgrade and forward-compatibility cases are built in code from the v1 fixtures, so the committed files stay minimal:
- an extra unknown key at the top level and inside a `SizeRecord` → ignored;
- an unknown enum name (`"palette": "PLASMA"`) → coerced to the default, not treated as corruption;
- a missing field whose default is a valid value → that default, not treated as corruption. The cases remove `keepScreenOn` from `settings.json` (→ `false`; every other non-default value intact) and `newBestAnnounced` and `elapsedMs` from `game-4.json` (→ `false` and 0; `gameId` 7, the cells, history and `previousFinalization` as in the fixture) (F07-AC15).

A default is what a missing key decodes to; it is not always a valid value. `gameId` has **no valid default**: its default 0 exists only so that `GameDocument()` (`size = 0`, "no saved game") decodes, and a game document with `size != 0` and no `gameId` key decodes to `gameId = 0`, fails the D-044 identity check (`gameId ≥ 1`, `ARCHITECTURE.md` §4.4) and is corrupt, so it falls back to a fresh game like any other invalid document (D-045). `PersistenceCorruptionTest` covers that case (F07-AC23, §1.6), and no compatibility case removes `gameId`.

### 1.6 Persistence tests on real DataStore files

All use the real `DataStoreFactory.create(…)` of `ARCHITECTURE.md` §4.2 over a fresh JUnit `TemporaryFolder` per test, through `testing/TestAppContainer.kt`, with `MainDispatcherRule(StandardTestDispatcher())` and the container's dispatchers set to the test dispatcher. Never two DataStores on one file (D-022).

| Class | `[M]` | Asserts |
|---|---|---|
| `PersistenceFixturesTest` | M2 | §1.5 |
| `PersistenceCorruptionTest` | M2 | Table below |
| `PersistenceBudgetTest` | M2 | D-046: the 10,000-move 8×8 document encodes to ≤ 262,144 bytes (expected ≈ 40 KB) both in memory (`GameDocumentSerializer.writeTo` into a `ByteArrayOutputStream`) and on disk after `updateData` (`file.length()`); encode + decode ≤ 50 ms as the median of 5 runs after 3 warm-ups; `records.json` with all six sizes populated (every counter at 10⁹, `tileReach` of 40 entries) ≤ 65,536 bytes; `settings.json` ≤ 4,096 bytes |
| `ConflatedWriterTest` | M2 | (a) 1,000 submissions while one write is suspended produce exactly 2 writes, the in-flight one and the latest; (b) a write gated by a `CompletableDeferred` completes even after newer submissions (never cancelled, D-024); (c) a write lambda that throws `IOException` increments `failures`, and the next submission writes the full document |
| `SettingsRepositoryTest` | M2 | Defaults on a missing file; `lastBoardSize` outside 3..8 → 4; updates before load are applied after load in call order |
| `RecordsMathTest` | M2 | D-044 finalization arithmetic with the D-198 readings. The D-044 identity: `finalize` of a `gameId` at or below `lastFinalizedGameId` returns the same instance; `reset` keeps only `lastFinalizedGameId`; `nextGameId`; `displayed` applies the recovery entry before the `OVER` game (F08-AC19, F08-AC20). The no-best-decreases property over 1,000 seeded sequences of 50 random `progress`/`finalize` calls, each `finalize` with a random `gameId` in 1..60 so that repeats occur; it also asserts that `lastFinalizedGameId` never decreases and that a `finalize` with a `gameId` at or below it changes nothing. The sequences come from `SplitMix64(0x2048 + i)` because `:app` has no kotest dependency: D-016 is exact, and adding `kotest-property` to `:app` would need a D-173 entry and buys little |
| `DataMigrationSeamTest` | M2 | A test-only `DataMigration<Settings>` (`shouldMigrate = schemaVersion < 2`, `migrate = copy(schemaVersion = 2)`) passed to a store over a copy of `fixtures/v1/settings.json`: first read returns `schemaVersion = 2` with every other v1 value intact; the file is rewritten; a second store instance (after the first scope is cancelled) does not call `migrate` again. v1.0 ships no migrations (`migrations = emptyList()`, `ARCHITECTURE.md` §4.4), so this added test proves now the D-041 path that later versions will use; it changes no decision |

`PersistenceCorruptionTest` cases. For each case: the named file gets the bytes shown; the store's corruption handler yields that document's defaults; the file is rewritten with the defaults; every **other** file under the temp folder is byte-identical before and after (sha256); nothing throws out of the repository API.

| File | Bytes written | Expected |
|---|---|---|
| any of the 8 files | 64 × `0xFF` | Defaults |
| any of the 8 files | Valid JSON truncated after 10 bytes | Defaults |
| `settings.json` | `{"lastBoardSize": "six"}` (wrong type; not a case `coerceInputValues` covers) | `Settings()` |
| `records.json` | `{"sizes": {"4": {"bestScore": "x"}}}` | `Records()`; `game-4.json` untouched |
| `game-4.json` | `fixtures/v1/game-4.json` with `cells` = `"!!"` (invalid Base64) | `GameDocument()` (no saved game) → a fresh 4×4 game; `records.json` untouched |
| `game-4.json` | … with `cells` of 15 bytes | Same |
| `game-4.json` | … with `moveCount` 9 (log size mismatch, I2) | Same |
| `game-4.json` | … with an extra snapshot (I3) | Same |
| `game-4.json` | … with `keepGoingAtMove = 99` (I4) | Same |
| `game-4.json` | … with `status = "OVER"` on a board that can move (I8) | Same |
| `game-4.json` | … with `gameId = 0`; separately without the `gameId` key (it decodes to 0: no valid default, §1.5); with `previousFinalization.gameId = 7` (equal to `gameId`); with `previousFinalization.score = -1`; with `previousFinalization.maxExp = 128` (D-044 identity checks, F07-AC23) | Same; nothing is counted from the rejected entry |
| `game-4.json` | A stray `game-4.json.tmp` full of garbage next to a valid file (a write interrupted by process death) | The valid file loads; the `.tmp` file is ignored |

### 1.7 ViewModel tests (Turbine)

Conventions (`ARCHITECTURE.md` §4.10): `runTest` with `StandardTestDispatcher`; flows observed with Turbine (`vm.board.test { … }`); assertions on transitions, never on emission counts (`StateFlow` conflates); `advanceUntilIdle()` before reading repositories; `awaitIdle()` on writers before reading files.

| Class | `[M]` | Key assertions (full AC list: `FEATURES.md` §8) |
|---|---|---|
| `GameViewModelTest` | M2 | Accepted move: one new `BoardUi` (`moveId + 1`, event `BoardEvent.Moved` carrying its `haptic`), header score and `+N`, persisted `game-N.json` equals `Game.move`'s state after `awaitIdle()`. Invalid move: only `nudge` changes; no file write (file mtime and bytes unchanged). `Blocked`: nothing changes. Undo: `BoardEvent.Undo`, overlay `None`, file updated. New game (`vm.onNewGame()`): finalizes per D-044 only when `OVER` or `score > 0`; `bestAtStart` read after finalization; the fresh game's `gameId` is `max(old, lastFinalizedGameId) + 1` and its `previousFinalization` is the old game's finalization input exactly when it was counted (F06-AC13). Keep going: `KEEP_PLAYING`, or the game-over overlay at once. Size switch: saves the old size, loads or creates the new one, `lastBoardSize` persisted, no finalization. New best fires once per game and survives process death (`newBestAnnounced`). Records update live on best increases only. `ready` becomes true after settings, records and game load. First-launch hint (D-201): `hintShownWhileStartingGameHasNoMove` (F22-AC2), `hintHiddenAfterAMove` (F22-AC10). One-shot presentations (D-047, ARCHITECTURE.md §4.11): `presentationReportsSettleWithoutStateChange` (F07-AC24): after a winning move, `onBoardPresented`, `onHeaderPresented` and `onOverlayPresented` with its `moveId` settle board, header and card entry, change no game state, history, clock or records, submit no save, and a repeated or stale report changes nothing. Header event and epoch (D-062, ARCHITECTURE.md §4.8): `headerCarriesBoardEventAndEpoch` (F13-AC14): every header emitted with a `BoardUi` carries its `moveId`, `event.kind` and `epoch`, and the reports leave the header's `event` and `epoch` unchanged |
| `InputBurstOrderingTest` | M2 | Three `onMove` calls with no clock advance in between: the three outcomes apply to successive states; the final state equals `Game.replay` of the same directions; the persisted file after `awaitIdle()` equals that state (D-052) |
| `PlayClockTest` | M2 | Time accrues only while resumed, in progress and after the first move; pause folds time into the save |
| `BoardPresenterTest` | M2 | Epoch `BoardUi`s (`Restored`, `Undo`, `NewGame`, `SizeSwitched`) contain only `Static` tiles; `reset(state, Undo)` keeps the id of every cell whose exponent is unchanged, every other epoch assigns new ids (D-182; F05-AC9, F06-AC11). `settledKeepsIdentityAndIsStatic` (F07-AC25): `settled(b)` keeps `moveId`, `epoch`, `cells` and the non-`Ghost` tile ids, is all `Static` with event `Restored`, is idempotent, and leaves the presenter's next emission unchanged (D-047) |
| `StatsViewModelTest` | M4 | Per-size and "All sizes" figures from `FIX_RECORDS`; reset clears `sizes`, keeps `lastFinalizedGameId` and leaves games untouched; a saved `OVER` game is shown through `RecordsMath.displayed` without being persisted (D-198, F08-AC18); an already finalized `gameId` is skipped and an uncounted recovery entry is shown once (F08-AC21) |
| `WhatsNewTest` | M4 | `lastSeen = 0` → nothing shown, 30000 stored; 29999 → the 30000 entries; equal → nothing (D-009) |
| `ProcessDeathRestoreTest` | M2 | §1.11 |
| `FinalizationRecoveryTest` | M2 | §1.11: New game interrupted between its records and game writes counts the old game exactly once (D-044; F06-AC14–AC19, F08-AC22) |

### 1.8 Compose UI tests on Robolectric

Configuration (D-146, verified in the scratch build):
- `app/src/test/resources/robolectric.properties` = `sdk=36`. Every test runs at SDK 36 unless it says otherwise.
- `LaunchSmokeTest` is `@Config(sdk = [24, 37])`: it launches `MainActivity` content at minSdk and targetSdk and asserts the first screen renders. It captures nothing (a golden captured under two SDKs would write two different images to one file). Both SDKs ran on JDK 21 (V4).
- API-absent criteria ("absent below API 33") run with `@Config(sdk = [24])`, reusing the same jar (`FEATURES.md` §0.2).
- Every rendering test is `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.
- Compose ui-test **v2** only: `androidx.compose.ui.test.junit4.v2.createComposeRule()` or `createAndroidComposeRule<MainActivity>()`. v1 rules are deprecated and fail `allWarningsAsErrors`.
- Timing tests set `mainClock.autoAdvance = false` and advance explicitly.
- The test JVM uses the 9 `--add-opens` flags and 3 GiB heap of `ARCHITECTURE.md` §2.6; `espresso-core` is forced to 3.7.0.

Behaviour classes by milestone (D-211; the task tables of `PLAN.md` §5). Assertions are the ACs that `FEATURES.md` §8 and `MOTION_SPEC.md` §8.5 assign; "JVM" marks the plain JVM classes of the same milestone (§1.7 and `FEATURES.md` §8). A class that spans milestones gets the named methods in each.

| `[M]` | Classes |
|---|---|
| M0 | `LaunchSmokeTest.launchesOnMinAndTargetSdk` (F30-AC5, placeholder form) |
| M2 | `SplashGateTest` F07-AC11–AC12 (`firstBoardEqualsPersistedFixture`: the first composed `BoardUi` equals the persisted fixture, no `Spawned` tiles; `gateReleasesAtCapOrWhenReady`: release at 1,000 ms), `RecreateNoReplayTest` F07-AC7–AC8, `BackupRulesTest`, `HapticsMapTest.priorityResolution` (F14-AC1), `di/AppContainerTest` (`createDoesNotTouchPackageManager` F30-AC10: `AppContainer.create` over a context whose `getPackageManager()` throws returns with `versionCode` 30000; `versionComesFromBuildConfigStandardFields` F30-AC11: `BuildConfig` has only AGP's five standard fields, 30000 and "3.0.0"; D-134). JVM: `BoardPresenterTest` (incl. `settledKeepsIdentityAndIsStatic`, F07-AC25), `InputBurstOrderingTest`, the `GameViewModelTest` hint methods `hintShownWhileStartingGameHasNoMove` (F22-AC2) and `hintHiddenAfterAMove` (F22-AC10), `GameViewModelTest.presentationReportsSettleWithoutStateChange` (F07-AC24) |
| M3 part 1 | `GameScreenTest` (part-1 ACs, incl. `undoUsedSnackbarUntilFirstFinalizedUndoGame`, `nothingToUndoSnackbarRateLimited`), `SwipeInputTest`, `KeyboardInputTest` (incl. `providesShortcutGroup`; `ignoredWhileModalOpen` for the new-game dialog), `TileRecompositionTest`, `BoardSemanticsTest` F17-AC1–AC6, `EffectiveMotionTest`, `MiniTileTest`, `HapticsMapTest` (move haptics, `invalidMoveSilentBelowApi30`), `SplashGateTest.lateLoadRendersStaticTiles` (F07-AC13), `LaunchSmokeTest` (+`launchesIntoGameWithoutTopBar`, +`noToastOnLaunchOrRecreate`); the board and score motion classes of `MOTION_SPEC.md` §8.5: `TimelineOriginTest`, `MoveTimelineTest`, `MergePopTest`, `SpawnDelayTest`, `ScoreMotionTest`, `NewBestTest`, `InvalidNudgeTest`, `NewGameCascadeTest`, `UndoCrossfadeTest`, `SizeSwitchTest`, `RestoreNoMotionTest`, `InterruptionTest`, `ConsistencyPropertyTest`, `FinalFrameEqualsStaticTest`, `BoardRecompositionBudgetTest`, and the board methods of `ReducedMotionTest`, `MotionOffTest` and `AnimatorScaleTest`. JVM: `BoardGeometryTest` (M3 methods), `TileLabelsTest`, `SwipeClassifierTest`, `MotionPlanTest`, `MotionTokensTest`, `ConfettiSystemTest`, `DelayedSpecTest`, `FormattingTest` (F28-AC7–AC9) |
| M3 part 2 | `GameScreenTest` (overlay ACs, incl. `winCardFitsBoard`, `gameOverCardFitsBoard`, `cardButtonsNeverRenderDisabledColors`, `shareOnBothCardsNotInMenu`, `menuOpensEveryDestination`), `KeyboardInputTest` (+`cardKeysActLikeChrome`, F16-AC11; `ignoredWhileModalOpen` extended to the sheet), `RecreateNoReplayTest` (+`openSheetSurvivesRecreation`; under `MotionHarness.recreate()` +`recreateAfterMoveRendersFinalFrame`, +`recreateMidMoveSnapsToFinalFrame`, +`recreateDuringAndAfterWinShowsRestoredCard`, +`recreateDuringAndAfterGameOverShowsRestoredCard`, +`recreateAfterInvalidMoveDoesNotNudge`, F07-AC26–AC30), `BackOnRootTest`; the overlay and navigation motion classes `WinSequenceTest`, `CardExitTest`, `GameOverSequenceTest`, `NavTransitionsTest`, `PredictiveBackTest`, and the overlay methods of `ReducedMotionTest`, `MotionOffTest`, `AnimatorScaleTest` and `HapticsMapTest`, and the card cases of `RestoreNoMotionTest` (AC-RESTORE-1's card end state, AC-RESTORE-2's `FIX_WIN_PRE_4` case). JVM: `ShareTextTest` |
| M4 | `ManifestPolicyTest` (incl. `legacyLauncherIconOnApi24`, L-AC12), `ImmersiveModeTest`, `KeepScreenOnTest`, `TalkBackModeTest`, `SettingsScreenTest` (incl. `switchesFireToggleHaptics` F14-AC8 and AC-HAP-4, `motionRowDisabledAtSystemScaleZero` AC-SCALE-4, `appLanguageSummaryDefaultsToSystem` L-AC6), `StatsScreenTest`, `AboutScreenTest`, `HowToPlayScreenTest`, `GameThemeTest`, `ThemeCrossfadeTest` (AC-THEME-1), `LayoutMotionTest` (AC-LAYOUT-1; `arrangementSwitchRendersSettledBoard` F18-AC13, AC-LAYOUT-2); the M4 methods of `GameScreenTest` (incl. the short-window layout checks `shortWindowsKeepBoardAndChromeOnScreen` F18-AC10 and `scrollModeCardBringsBoardIntoView` F18-AC12), `BoardSemanticsTest` (F17-AC7, incl. SCROLL), `RecreateNoReplayTest` (F18-AC5), `SwipeInputTest` (incl. `scrollModeMovesOnlyFromBoard` F18-AC11) and `KeyboardInputTest` (What's new sheet cases). JVM: `PaletteContrastTest`, `LayoutModeTest`, `BoardGeometryTest` (M4 methods: `boardSizePerMode`, `narrowWindowFloor`, and the D-188 resolution tables `resolvedLayoutTable` F18-AC8 and `boardNeverBelowFloorOrOffWindow` F18-AC9, whose window grid fails if any arrangement yields a board below 220 dp, a negative board or chrome off the window), `StatsViewModelTest`, `WhatsNewTest`, `LicensesCoverageTest` |
| M5 | `LocalizationTest`, `TranslationQualityTest` (L10N_STORE_LEGAL.md §1.9). JVM: `FormattingTest.toLatinDigitsMapsArabicIndicAndPersian` (L-AC5) |

Locations follow `FEATURES.md` §8 and `ARCHITECTURE.md` §10.5; names follow `PLAN.md` §2.

### 1.9 Roborazzi screenshot goldens

| Item | Rule |
|---|---|
| Matrix | `DESIGN_SYSTEM.md` §10.4 rows 1–45 (D-204): the 41 D-147 rows plus rows 42–45 (board-size sheet, new-game dialog, What's new sheet, first-launch hint) |
| Naming | `DESIGN_SYSTEM.md` §10.1: `<screen>[_<state>[-<N>x<N>]]_<palette>-<mode>[-hc][_<variant>]`; layout goldens `layout_<qualifier>`; filmstrip frames `…_tNNN` (D-204: `_t000/_t040/_t080/_t160/_t400` hold the frames at 0, 48, 80, 160 and 400 ms from the origin). M0's placeholder is `launch_placeholder` |
| Directory | `app/src/test/screenshots/<name>.png`, flat, committed (`.gitattributes` marks `*.png` binary) |
| Helper | `testing/Goldens.kt` (below). It is the only caller of `captureRoboImage`. `StoreAssetsTest` (D-115, D-190) is not a golden test: it captures store graphics with `captureToImage()` and writes them through `OpaquePngWriter` into `fastlane/` (L10N_STORE_LEGAL.md §2.5) |
| Capture | SDK 36, `RobolectricDeviceQualifiers.Pixel7` unless the row names a qualifier, dynamic color off, bundled font, static fixtures, settings `motion = OFF` except the filmstrip, dark rows with `themeMode = DARK` and `+night` (`DESIGN_SYSTEM.md` §10.2). Dialogs and sheets are captured by node (`onNode(isDialog())`, test tags `sheet:boardSize` / `sheet:whatsNew`). Never `captureScreenRoboImage` (`@ExperimentalRoborazziApi`) |
| Comparison | Exact match (Roborazzi's default `ThresholdValidator(0F)`). If CI shows sub-pixel noise that a re-run reproduces, switch every capture to `RoborazziOptions(compareOptions = CompareOptions(changeThreshold = 0.001F))` in `captureGolden` and record it (D-147) |
| Budget | ≤ 10 MiB in total and PNG only, enforced by `check-metadata.sh` (§1.16); the full gate's Roborazzi results must match the directory exactly (every PNG compared with result `unchanged`, no result without a PNG, and no results at all fails), enforced by `check-test-counts.sh` rules G1–G5 (§1.15) |
| Classes | `GameScreenshotTest` (M3–M4: M3 part 1 records rows 1, 3 and 43, M3 part 2 rows 13, 15 and 42, M4 the remaining rows and re-records every row), `ScreensScreenshotTest` (M4), `LayoutScreenshotTest` (M4), `LocaleScreenshotTest` (M5, rows 23–26 need the M5 translations), `MotionFilmstripTest` (M3 part 1, rows 37–41, method `mergeAndSpawnFilmstrip`). M0's `PlaceholderScreenshotTest` and its PNG are deleted in M3 part 1, the commit that adds `GameScreenshotTest` |

```kotlin
// app/src/test/kotlin/com/clayboicardi/game2048/testing/Goldens.kt (verified in the scratch build)
package com.clayboicardi.game2048.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.captureRoboImage

/** Writes or verifies `app/src/test/screenshots/<name>.png` (D-147, QUALITY.md §1.9). */
fun SemanticsNodeInteraction.captureGolden(name: String) {
    require(Regex("[a-z0-9][A-Za-z0-9_.-]*").matches(name)) { "Bad golden name: $name" }
    captureRoboImage("src/test/screenshots/$name.png")
}
```

The Gradle test working directory of `:app` is `app/`, so the relative path lands in `app/src/test/screenshots/`.

Commands:

| Purpose | Command |
|---|---|
| Record (overwrites goldens, then lists new and changed PNGs) | `./scripts/verify.sh --record` (runs `:app:testDebugUnitTest --rerun -Proborazzi.test.record=true`). Locally every command of this table runs under the build lock (§3.1) |
| Verify (part of every full gate; CI) | `./scripts/verify.sh` (runs `:app:testDebugUnitTest -Proborazzi.test.verify=true`) |
| Compare without failing, to inspect a diff | `./gradlew :app:testDebugUnitTest -Proborazzi.test.compare=true`, then Read `app/build/outputs/roborazzi/<name>_compare.png` |
| Store graphics (not goldens) | `./gradlew :app:testDebugUnitTest -PrecordStoreAssets=true --tests 'com.clayboicardi.game2048.store.*'` (D-115) |

In verify mode a mismatch fails the test and writes `<name>_actual.png` and `<name>_compare.png` to `app/build/outputs/roborazzi/` (V7), plus a JSON result under `app/build/test-results/roborazzi/`.

**The Read rule (D-147).** The milestone integrator opens every new or changed PNG with its file-reading tool before committing, checks it against the list below, and lists it in the commit body and in the PR body's screenshot section. A PNG that shows a defect is never committed; the code is fixed and the image re-recorded. Format:

```text
Screenshots (all inspected):
- game_mid-4x4_classic-dark.png (new): Classic dark board, 14 tiles 2…2048, score 10220, best 24680; nothing clipped.
- settings_classic-light_de.png (changed): "Wischempfindlichkeit" now wraps to two lines; switches aligned.
- launch_placeholder.png (deleted): replaced by game_new-4x4_classic-light.png.
```

`Screenshots: none` when nothing changed. Inspection checklist for each PNG:
1. It shows the named state: fixture, palette, mode, board size, locale, qualifier.
2. Text: nothing clipped, ellipsized or overlapping; numerals in Latin digits; tile labels centred; compact labels (`16K`) only where the cell is small (8×8 rows).
3. Colors: spot-check tiles 2, 128 and 2048 against `DESIGN_SYSTEM.md` §2.3–§2.5; dark rows are dark; `-hc` rows show black/white ink and outlines.
4. Layout: the whole board visible; margins per mode; side panel or rails where the qualifier demands (`DESIGN_SYSTEM.md` §5.4).
5. RTL rows (`fa`, `ar-XB`): chrome mirrored, board and direction pad not mirrored.
6. Cards: scrim clipped to the board's rounded shape; buttons visible.
7. Filmstrip frames: the content table of `MOTION_SPEC.md` §8.3.

**Local and CI disagree.** Local and CI run the same OS image family (research/07 G19), so they are expected to match bit for bit; this is unproven until the first M0 CI run (DECISIONS "Verification-pending" item 1). If CI fails only on goldens:
1. The orchestrator lists the failed run's artifacts and downloads `game2048-screenshot-diffs-<head-sha>` via the GitHub API (MCP `actions_list` `list_workflow_run_artifacts`, then `actions_get` `download_workflow_run_artifact` and fetch the returned URL; or `gh run download <run-id> -R "$R" -n game2048-screenshot-diffs-<head-sha>`; §6.8 "Artifacts"). It unpacks the zip into a new, empty directory beside the checkout (`../2048-ci-diffs/<head-sha>/`) and treats the files as untrusted data.
2. The milestone integrator Reads each `<name>_actual.png` and `<name>_compare.png`.
3. If the difference is not visible, or is anti-aliasing only: copy each `_actual.png` over its golden, Read it, and commit as `ci: adopt CI-rendered goldens (<n> files)` with the Screenshots list; the orchestrator pushes it. If the next CI run still differs, apply the 0.001 threshold rule above.
4. If the difference is real, fix the code; never adopt an image that shows a defect.

Never commit images from CI automatically (research/06 §1.4).

### 1.10 Motion filmstrips

Three layers (`MOTION_SPEC.md` §8):

| Layer | What | Gate |
|---|---|---|
| Numeric probes | `MotionProbe` values at fixed virtual times, the `AC-*` criteria of `MOTION_SPEC.md` §9, in the §8.5 classes | Yes (JUnit assertions) |
| Golden filmstrip | `MotionFilmstripTest.mergeAndSpawnFilmstrip` (F13-AC8, AC-GOLD-1): `FIX_FILM`, move LEFT, Motion Full, Pixel7, Classic light, frames `motion_merge-spawn-4x4_classic-light_t000/t040/t080/t160/t400` (`DESIGN_SYSTEM.md` §10.4 rows 37–41; the frames at 0, 48, 80, 160 and 400 ms from the origin on the 16 ms grid, D-204, `MOTION_SPEC.md` §8.3) | Yes (exact pixels) |
| Inspection filmstrips | Strips written to `app/build/reports/filmstrips/<TestClass>/<name>.png` by `MotionHarness.writeFilmstrip` (`MOTION_SPEC.md` §8.4) | No. Read rule (D-210): M3 Reads every strip once (part 1 every strip that exists then, part 2 its new strips); every later commit Reads only the strips whose events changed. Each Read strip is listed under `Screenshots:` |

Inspection strips are under `build/reports`, so CI uploads them in the reports artifact (§5.5).

### 1.11 Process-death simulation (JVM)

`ProcessDeathRestoreTest` (`[M2]`, JVM, no Robolectric; D-047):
1. Build container A with `TestAppContainer` over a fresh `TemporaryFolder`, `FakeClock`, `FixedSeedSource(0x2048)`.
2. `onResumed()`. For each N in 3..8: `onSizeSelected(N)`; 40 `onMove` calls with directions from `SplitMix64(0x2048 + N).nextInt(4)` (invalid moves are allowed and change nothing); 3 `onUndo`; 5 more moves; advance the fake clock 1,000 ms between moves while resumed; `onPaused()`.
3. `awaitIdle()` on every writer of A, then cancel A's `applicationScope`.
4. Build container B over the **same** folder. For each N: `games.load(N)` equals A's last `SavedGame` (data-class equality: board, score, `rngState`, `moveLog`, `snapshots`, `undoCount`, `status`, `keepGoingAtMove`, `elapsedMs`, `bestAtStart`, `newBestAnnounced`, `gameId`, `previousFinalization`). Records equal; `lastBoardSize` = 8.
5. B's `GameViewModel` first `BoardUi` contains only `Static` tiles (no replay, D-047).

`FinalizationRecoveryTest` (`[M2]`, JVM, no Robolectric; D-044, `ARCHITECTURE.md` §4.9 finalization protocol). Each method simulates a process killed partway through New game's two writes, then relaunches:
1. **Failing writer.** Container A is a `TestAppContainer`. The test builds A's `GameViewModel` directly, from a `RecordsRepository(store, …)` and a `GameRepository(storeFor, …)` constructed over A's own stores (`ARCHITECTURE.md` §4.2, §4.6). Either the records store or the 4×4 game store is wrapped in `testing/FaultyDataStore` with `failWrites = true`. The repositories already take their stores, so no production seam is added. The wrapped file's writes throw `IOException`, which `ConflatedWriter` swallows (`ARCHITECTURE.md` §9.3). The other file is written normally. After `onNewGame()`, `awaitIdle()` on every writer, then cancel A's `applicationScope`. Exactly one of the two New game writes has landed.
2. **Relaunch.** Container B (no faults, `FixedSeedSource(0x2048)` again, so seeds repeat on purpose and only `gameId` can tell games apart) loads over the same folder, then `advanceUntilIdle()`. Assert `records.json`, B's current game and `game-4.json` as F06-AC14–AC17 state. Then `onNewGame()` in B, `awaitIdle()`, and container C: the old game is still counted once.
3. **All four disk outcomes** (`everyDiskOutcomeCountsExactlyOnce`, F06-AC18). A runs without faults. The bytes of `records.json` and `game-4.json` are captured before `onNewGame()` and after it plus `awaitIdle()`. Each of the four (records, game) combinations is copied into a fresh `TemporaryFolder`, and the B/C steps above run over it. This is done for an `OVER` and for an abandoned old game.
4. **Other sizes** (`otherSizesRecoveredAfterLoad`, F06-AC19): the interrupted New game is on 5×5 and B starts on 4×4. R1 updates the records after `ready`, and `game-5.json` stays byte-identical.
5. **Reset** (`resetIsNotUndoneByRecovery`, F08-AC22): `records.resetAll()` after a New game, then relaunch. `sizes` stays empty.

Every method would fail before D-044's amendment: without `gameId` and `lastFinalizedGameId`, step 2 would count the old game twice (F06-AC14, AC15, AC18) or not at all (F06-AC16, AC17, AC19).

Complements: `RecreateNoReplayTest` (Robolectric `ActivityScenario.recreate()` and a pause/resume cycle: only `Static` tiles, unchanged history; from M3 part 2, `MotionHarness.recreate()` after a settled move, mid-move, during and after the win and game-over sequences, and after an invalid move: the first frame is the final static frame and no haptic fires again, F07-AC26–AC30, ARCHITECTURE.md §4.11), `SplashGateTest` (§1.8), and the `game-4.json.tmp` corruption case (§1.6).

### 1.12 Accessibility checks

| Check | Where | Rule |
|---|---|---|
| ATF (D-085, D-204) | `atfRule(compose)` from `testing/Accessibility.kt` (below), applied with `RuleChain.outerRule(compose).around(atfRule(compose))` in every screen-level screenshot class (`GameScreenshotTest`, `ScreensScreenshotTest`, `LocaleScreenshotTest`, `LayoutScreenshotTest`) and in `PlaceholderScreenshotTest` from M0. It therefore runs on every `game_*`, `layout_*`, `settings_*`, `stats_*`, `howtoplay_*`, `about_*`, `licenses_*`, `sheet_*` and `dialog_*` golden; only `motion_*` (`MotionFilmstripTest`) is exempt | `AccessibilityCheckPreset.LATEST`, `CheckLevel.Error`, after every test. The rule captures no images of its own (verified). The opt-in it needs is D-174 |
| Touch targets (D-084, D-205) | `assertTouchTargets()` called after every screen-level capture, in the same classes | A gate, like ATF: every clickable's touch bounds ≥ 48 × 48 dp, and no two clickables' touch bounds overlap (V6). ATF's size-only check is vacuous under Compose's 48 dp touch-bounds expansion |
| Semantics | `BoardSemanticsTest`, `TalkBackModeTest` | D-080–D-082, D-202: `CollectionInfo(N, N)`, cell labels, custom actions, live regions, status lines, direction pad |
| Contrast and CVD | `PaletteContrastTest` (JVM) | `DESIGN_SYSTEM.md` §2.13 with the unquantized contrast and CVD ΔE method (D-205): the D-083 ink floors for exponents 1–17, the ΔE gates, every quoted contrast ratio, the chrome pairs of §2.9, HC ink rule |
| Font scale 2.0 | `GameScreenTest.chromeTextNotTruncatedAtFontScale2` + goldens 27–28 | No text node reports `hasVisualOverflow` (`FEATURES.md` F17-AC15) |
| Focus | `KeyboardInputTest` | Board focus on entry; Tab order; visible focus ring (D-053, D-084) |

```kotlin
// app/src/test/kotlin/com/clayboicardi/game2048/testing/Accessibility.kt (verified in the scratch build)
// The only opt-in in the test source set (D-174): every Roborazzi ATF API is
// @ExperimentalRoborazziApi in 1.77.0.
@file:OptIn(ExperimentalRoborazziApi::class)

package com.clayboicardi.game2048.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.AccessibilityCheckAfterTestStrategy
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziRule
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset

/** D-085: ATF after every test, LATEST preset, Error level. */
fun atfRule(compose: ComposeTestRule): RoborazziRule =
    RoborazziRule(
        composeRule = compose,
        captureRoot = compose.onRoot(),
        options =
            RoborazziRule.Options(
                roborazziAccessibilityOptions =
                    RoborazziATFAccessibilityCheckOptions(
                        checker =
                            RoborazziATFAccessibilityChecker(
                                preset = AccessibilityCheckPreset.LATEST,
                            ),
                        failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
                    ),
                accessibilityCheckStrategy = AccessibilityCheckAfterTestStrategy(),
            ),
    )

/**
 * D-084: every clickable has a touch area of at least 48 x 48 dp, and the touch areas of two
 * different clickables never overlap (Compose extends small targets to 48 dp, so overlap is the
 * failure that matters). Nested clickables (one an ancestor of the other) are exempt.
 */
fun ComposeTestRule.assertTouchTargets() {
    val minPx = with(density) { 48.dp.toPx() } - 0.5f
    val nodes = onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
    val tooSmall =
        nodes
            .filter { it.touchBoundsInRoot.width < minPx || it.touchBoundsInRoot.height < minPx }
            .map { "too small: ${it.id} ${it.touchBoundsInRoot}" }
    val problems = tooSmall + overlaps(nodes)
    check(problems.isEmpty()) { "Touch-target violations: " + problems.joinToString() }
}

private fun overlaps(nodes: List<SemanticsNode>): List<String> = nodes.flatMapIndexed { i, a ->
    nodes.drop(i + 1).mapNotNull { b ->
        val o = a.touchBoundsInRoot.intersect(b.touchBoundsInRoot)
        val nested = a.isAncestorOf(b) || b.isAncestorOf(a)
        if (!nested && o.width > 0.5f && o.height > 0.5f) "overlap: ${a.id}/${b.id} $o" else null
    }
}

private fun SemanticsNode.isAncestorOf(other: SemanticsNode): Boolean =
    generateSequence(other.parent) { it.parent }.any { it.id == id }
```

Usage in a screenshot class:

```kotlin
private val compose = createComposeRule()
@get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(atfRule(compose))

@Test
fun midGameLight() {
    compose.setContent { /* GameTheme + GameScreen over FIX_MID_4 */ }
    compose.onRoot().captureGolden("game_mid-4x4_classic-light")
    compose.assertTouchTargets()
}
```

### 1.13 Merged-manifest permission and exported-component allowlist

The gate inspects the **built** APKs, because the merged and packaged manifest is what users install (`ARCHITECTURE.md` §8.6). `scripts/check-apk.sh` `[M0]`, all seven D-148 assertions active from M0:

```bash
#!/usr/bin/env bash
# APK and merged-manifest gates (D-148; docs/modernization/QUALITY.md §1.13, ARCHITECTURE.md §8.6).
# Inspects the APKs written by :app:assembleDebug and :app:assembleRelease. Exit 0 = all hold.
set -uo pipefail
cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f local.properties ]]; then sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"; fi
BT="$sdk/build-tools/${ANDROID_BUILD_TOOLS:-37.0.0}"
for tool in aapt2 zipalign apksigner; do
  [[ -x "$BT/$tool" ]] || { echo "::error::check-apk: $BT/$tool not found (set ANDROID_HOME)"; exit 1; }
done
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"

APP_ID=com.clayboicardi.game2048
MAX_RELEASE_BYTES=3145728 # 3.0 MiB (D-148 item 4)
CERT="$(tr -d '[:space:]' < signing/ci-sideload.sha256)"
DENY_RE='com\.google\.(android\.gms|firebase|android\.play|android\.ump|android\.datatransport)|com\.google\.firebase|com\.crashlytics|io\.sentry|com\.squareup\.okhttp3|io\.ktor|ui-text-google-fonts|androidx\.work|androidx\.glance'
RELEASE_EXPORTED="$APP_ID.MainActivity
androidx.profileinstaller.ProfileInstallReceiver"
DEBUG_EXPORTED="$RELEASE_EXPORTED
androidx.activity.ComponentActivity"

fail=0
err() { echo "::error::check-apk: $*"; fail=1; }

# One fully qualified class per line for every component with android:exported=true.
exported_components() {
  "$BT/aapt2" dump xmltree --file AndroidManifest.xml "$1" | awk '
    /E: (activity|activity-alias|service|receiver|provider) / { name = ""; inside = 1; next }
    /E: / && !/E: (intent-filter|action|category|data|meta-data)/ { inside = 0 }
    inside && name == "" && /android:name\(0x01010003\)=/ {
      match($0, /="[^"]+"/); name = substr($0, RSTART + 2, RLENGTH - 3)
    }
    inside && /android:exported\(0x01010010\)=true/ { print name }
  ' | sort -u
}

check_apk() { # apk package label expected-exported-set
  local apk="$1" pkg="$2" label="$3" expected="$4" badging actual perms digests
  if [[ ! -f "$apk" ]]; then err "$apk is missing"; return; fi
  badging="$("$BT/aapt2" dump badging "$apk")" || { err "aapt2 dump badging failed: $apk"; return; }

  # 1. Badging.
  grep -qF "package: name='$pkg' versionCode='30000' versionName='3.0.0'" <<< "$badging" ||
    err "$apk: package line is not name='$pkg' versionCode='30000' versionName='3.0.0'"
  grep -qxF "application-label:'$label'" <<< "$badging" || err "$apk: label is not '$label'"
  grep -qxF "minSdkVersion:'24'" <<< "$badging" || err "$apk: minSdkVersion is not 24"
  grep -qxF "targetSdkVersion:'37'" <<< "$badging" || err "$apk: targetSdkVersion is not 37"

  # 2. Permissions: exactly androidx.core's signature-level self-permission (D-002).
  perms="$(grep -E '^uses-permission' <<< "$badging" | sort -u)"
  if [[ "$perms" != "uses-permission: name='$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'" ]]; then
    err "$apk: uses-permission set differs from the allowlist:"
    printf '%s\n' "$perms"
  fi

  # 3. Exported components.
  actual="$(exported_components "$apk")"
  if [[ "$actual" != "$(sort -u <<< "$expected")" ]]; then
    err "$apk: exported components differ (< expected, > actual):"
    diff <(sort -u <<< "$expected") <(printf '%s\n' "$actual")
  fi

  # 5. 16 KB alignment.
  "$BT/zipalign" -c -P 16 -v 4 "$apk" > /dev/null || err "$apk: zipalign -c -P 16 failed"

  # 6. Signer = the committed CI sideload certificate (D-131).
  digests="$("$BT/apksigner" verify --print-certs "$apk" 2> /dev/null |
    sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: //p' | sort -u)"
  [[ "$digests" == "$CERT" ]] || err "$apk: signer digest '$digests' != signing/ci-sideload.sha256 '$CERT'"
}

check_apk app/build/outputs/apk/release/app-release.apk "$APP_ID" "2048" "$RELEASE_EXPORTED"
check_apk app/build/outputs/apk/debug/app-debug.apk "$APP_ID.debug" "2048 Debug" "$DEBUG_EXPORTED"

# 4. Size (release only).
release=app/build/outputs/apk/release/app-release.apk
if [[ -f "$release" ]]; then
  size="$(stat -c %s "$release")"
  echo "check-apk: release APK $size bytes (budget $MAX_RELEASE_BYTES)"
  ((size <= MAX_RELEASE_BYTES)) || err "release APK is $size bytes > $MAX_RELEASE_BYTES"
fi

# 7. Dependency deny-list on the release runtime classpath.
if deps="$(./gradlew -q "${EXTRA[@]}" :app:dependencies --configuration releaseRuntimeClasspath)"; then
  if hits="$(grep -E "$DENY_RE" <<< "$deps")"; then
    err "denied dependency on releaseRuntimeClasspath:"
    printf '%s\n' "$hits"
  fi
else
  err ":app:dependencies failed"
fi

[[ "$fail" -eq 0 ]] && echo "check-apk: OK"
exit "$fail"
```

Notes:
- The badging line is `minSdkVersion:'24'` (technical correction TC-2 of D-148 item 1).
- The signer digest is read from the `certificate SHA-256 digest:` line(s) of `apksigner verify --print-certs`, which reads `V2 Signer: certificate SHA-256 digest:` for minSdk-24 APKs (technical correction TC-2 of D-148 item 6; V12). All signer lines must carry the one committed digest.
- The debug allowlist contains `androidx.activity.ComponentActivity` from `ui-test-manifest` (D-148). `ui-tooling` stays off the classpath because its `PreviewActivity` is exported (`ARCHITECTURE.md` §2.6).
- `ManifestPolicyTest` (M4, Robolectric) complements this gate with the manifest attributes the APK checks do not cover (`appCategory`, touchscreen not required, EmojiCompat initializer removed, `FEATURES.md` §8).
- Changing either allowlist needs an execution-time D-173 entry and Codex review under `AGENTS.md` `## Code Review Rules` → "Offline, permissions and dependencies" (D-171, D-192).

### 1.14 Budgets

| Budget | Limit | Enforced by | Reported |
|---|---|---|---|
| Release APK | ≤ 3,145,728 B (scratch skeleton 1,002,520 B) | `check-apk.sh` item 4 | Size printed on every run; the PR body records the M6 size |
| Golden PNGs | ≤ 10,485,760 B in total, PNG only | `check-metadata.sh` `check_golden_budget` | Count and bytes printed |
| Numeral font | ≤ 32,768 B | `check-metadata.sh` `check_font` (M4) | — |
| Persisted 8×8 game, 10,000 moves | ≤ 262,144 B; encode + decode ≤ 50 ms | `PersistenceBudgetTest` | Size and median time in the test output |
| `records.json` / `settings.json` | ≤ 65,536 B / ≤ 4,096 B | `PersistenceBudgetTest` | — |
| Engine | 100,000 accepted 8×8 moves ≤ 2 s | `EnginePerformanceTest` | Time in the test output |
| Tile recompositions during a slide | 0 | `TileRecompositionTest` | — |
| Board / screen recompositions per move | ≤ 2 / 0 | `BoardRecompositionBudgetTest` (`MOTION_SPEC.md` §7.2) | — |
| Confetti step | 10,000 steps of 160 particles ≤ 200 ms, ≤ 1 KiB allocated | `ConfettiSystemTest` | — |
| Device frame budgets | P95 ≤ 6 ms, P99 overrun ≤ 0 | Not gated (D-150, D-153) | `MOTION_SPEC.md` §7.6 recipe, optional |

A budget is never raised to make a change pass. Raising one needs an execution-time D-173 entry with the measurement.

### 1.15 Test-count floor and orphan goldens

The rules of this section are D-149 as extended by D-179. `config/test-count-floor.txt` `[M0]`:

```text
# Executed-test floors (D-149). Set to the exact executed counts in the commit that adds tests;
# never lower without a reason in the PR body.
engine=2
app=3
```

M0's counts are `DirectionTest` (2) and `LaunchSmokeTest` (2 executions: SDK 24 and 37) plus `PlaceholderScreenshotTest` (1), measured in the scratch build. Each later commit sets both lines to the executed counts it produced, so a single silently skipped or deleted test fails the gate.

`scripts/check-test-counts.sh` `[M0]`:

```bash
#!/usr/bin/env bash
# Test-count floor (D-149; rules in docs/modernization/QUALITY.md §1.15).
# Reads the JUnit XML that the last test run wrote. For each module it requires:
#   executed = tests - skipped > 0, executed >= the floor in config/test-count-floor.txt,
#   skipped == 0, failures + errors == 0.
# It also requires the Roborazzi results of the last verify run to match app/src/test/screenshots/
# exactly (D-179): no results at all fails, whatever the number of goldens; every golden was
# compared (no orphan); every result names a golden; every result is `unchanged`.
set -uo pipefail
cd "$(dirname "$0")/.."

python3 -I - <<'PY'
import glob, json, os, re, sys

SUITE = re.compile(r"<testsuite\b([^>]*)>")
ATTR = re.compile(r'(\w+)="([^"]*)"')

floors = {}
with open("config/test-count-floor.txt", encoding="utf-8") as f:
    for raw in f:
        line = raw.split("#", 1)[0].strip()
        if line:
            key, value = line.split("=", 1)
            floors[key.strip()] = int(value)

modules = {
    "engine": "core/engine/build/test-results/test",
    "app": "app/build/test-results/testDebugUnitTest",
}
ok = True
summary = []
for name, results in modules.items():
    tests = skipped = failures = errors = 0
    for path in sorted(glob.glob(os.path.join(results, "*.xml"))):
        with open(path, encoding="utf-8") as f:
            match = SUITE.search(f.read())
        if match is None:
            print(f"::error::check-test-counts: {path} has no <testsuite> element")
            ok = False
            continue
        attrs = dict(ATTR.findall(match.group(1)))
        tests += int(attrs.get("tests", 0))
        skipped += int(attrs.get("skipped", 0))
        failures += int(attrs.get("failures", 0))
        errors += int(attrs.get("errors", 0))
    executed = tests - skipped
    floor = floors.get(name)
    summary.append(f"{name}={executed}")
    problems = []
    if floor is None:
        problems.append(f"no '{name}=' line in config/test-count-floor.txt")
    elif executed < floor:
        problems.append(f"executed {executed} < floor {floor}")
    if executed == 0:
        problems.append(f"no executed tests under {results}")
    if skipped:
        problems.append(f"{skipped} skipped (ignored/disabled tests are not allowed)")
    if failures or errors:
        problems.append(f"{failures} failures, {errors} errors")
    for p in problems:
        print(f"::error::check-test-counts: {name}: {p}")
        ok = False

# Goldens (D-147, D-179; rules G1-G5 in QUALITY.md §1.15). The full gate runs
# :app:testDebugUnitTest -Proborazzi.test.verify=true, so its Roborazzi results must match the
# golden directory exactly. There is no "no results, skip" path.
GOLDEN_DIR = "app/src/test/screenshots"
RESULT_DIR = "app/build/test-results/roborazzi"
goldens = {os.path.normpath(p) for p in glob.glob(f"{GOLDEN_DIR}/**/*.png", recursive=True)}
results = {}  # golden path relative to the repository root -> set of Roborazzi result types
for path in sorted(glob.glob(f"{RESULT_DIR}/**/*.json", recursive=True)):
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:  # G5: a truncated result file could hide a comparison
        print(f"::error::check-test-counts: unreadable Roborazzi result {path}: {e}")
        ok = False
        continue
    stack = [data]
    while stack:
        item = stack.pop()
        if isinstance(item, dict):
            value = item.get("golden_file_path")
            if isinstance(value, str):
                full = value if os.path.isabs(value) else os.path.join("app", value)
                key = os.path.normpath(os.path.relpath(full))
                results.setdefault(key, set()).add(str(item.get("type")))
            stack.extend(item.values())
        elif isinstance(item, list):
            stack.extend(item)
if not results:  # G1
    print(
        f"::error::check-test-counts: {len(goldens)} golden(s) under {GOLDEN_DIR}/ but no"
        f" Roborazzi comparison results under {RESULT_DIR}/; screenshot verification did not run"
    )
    ok = False
else:
    for golden in sorted(goldens - results.keys()):  # G2
        print(f"::error::check-test-counts: golden {golden} was not compared by any test (orphan)")
        ok = False
    for golden in sorted(results.keys() - goldens):  # G3
        print(
            f"::error::check-test-counts: a test captured {golden}, which is not a golden"
            f" under {GOLDEN_DIR}/"
        )
        ok = False
    for golden in sorted(results.keys() & goldens):  # G4
        if results[golden] != {"unchanged"}:
            kinds = ", ".join(sorted(results[golden]))
            print(
                f"::error::check-test-counts: golden {golden} has result type {kinds};"
                " the verify run must report unchanged"
            )
            ok = False

print("check-test-counts: " + " ".join(summary) + (" OK" if ok else " FAILED"))
sys.exit(0 if ok else 1)
PY
```

The XML root element is read with a regular expression, not an XML parser: the files are Gradle's own output, and only four attributes are needed. In CI the build directories are fresh, so no stale result file can inflate a count; locally, run `./gradlew clean` if a test class was deleted or renamed.

**Golden rules (D-147, D-179).** The full gate runs `:app:testDebugUnitTest -Proborazzi.test.verify=true`. Roborazzi writes one JSON result per capture under `app/build/test-results/roborazzi/`, each with a `golden_file_path` and a `type` (`recorded`, `added`, `changed` or `unchanged`), and rewrites the directory on every executed run (V9). In the 1.77.0 bytecode (checked 2026-10-28), the Gradle plugin deletes and recreates that directory at the start of every executed test task and declares the record and verify modes as task inputs, so a run with no capture leaves the directory empty and a full run after `--record` executes again. In verify mode the result JSON is written before the assertion error, so a missing golden still leaves an `added` result. Let G be the PNGs under `app/src/test/screenshots/`; in CI and in the gating run on the committed HEAD these are exactly the committed goldens. Let R map each result's `golden_file_path`, made relative to the repository root, to the set of its `type` values. The script fails on each of:

| Rule | Fails when | Typical cause |
|---|---|---|
| G1 | R is empty, whatever the size of G. There is no skip path | Every `captureGolden` call was removed while the test methods, and therefore the floors, stayed; or the screenshot tests did not run. From M0 on the tree always holds at least one golden (`launch_placeholder.png`, then the §1.9 matrix), so an empty R is never valid |
| G2 | A golden in G has no result (orphan) | A capture call was removed or renamed while its PNG stayed |
| G3 | A result names a path that is not in G | A capture under a new name whose PNG was never recorded and committed, or a capture outside `app/src/test/screenshots/` (`captureGolden` is the only caller of `captureRoboImage`, §1.9) |
| G4 | The result types of a golden in G are not exactly {`unchanged`} | `changed` or `added`: a mismatch or a missing golden (the test fails too); `recorded`: the results come from a record run, not from a comparison |
| G5 | A JSON file under `app/build/test-results/roborazzi/` cannot be read | A truncated or partly written result file, which could hide a comparison |

G1–G3 compare G and R in both directions, so neither all goldens nor a subset of them can go unverified while the test count holds.

**Seeded-defect proof (M0, D-179).** In the throwaway clone `../2048-seeded` (§1.16), with every `verify.sh` and Gradle call under the build lock (D-178), reset the clone before each seed with `git checkout -- . && git clean -fdxq`, so that no result file of an earlier seed survives. Each seed must make `check-test-counts.sh` exit 1 and print the line shown (after the `::error::check-test-counts: ` prefix). Paste the printed lines into the M0 commit body; never commit a seed.

| Seed | Defect and commands | Expected line |
|---|---|---|
| S1 (floor) | `app=4` in `config/test-count-floor.txt`; `./scripts/verify.sh` | `app: executed 3 < floor 4` |
| S2 (skip) | `@Ignore` on `LaunchSmokeTest.launchesOnMinAndTargetSdk`; `./scripts/verify.sh` | `app: <k> skipped (ignored/disabled tests are not allowed)` |
| S3 (G2) | `cp app/src/test/screenshots/launch_placeholder.png app/src/test/screenshots/seeded_orphan.png`; `./scripts/verify.sh` | `golden app/src/test/screenshots/seeded_orphan.png was not compared by any test (orphan)` |
| S4 (G1) | Delete the `captureGolden("launch_placeholder")` call and its import from `PlaceholderScreenshotTest.kt`; the test method, `atfRule` and `assertTouchTargets()` stay; `./scripts/verify.sh` | `1 golden(s) under app/src/test/screenshots/ but no Roborazzi comparison results under app/build/test-results/roborazzi/; screenshot verification did not run`. The script's last line, `check-test-counts: engine=2 app=3 FAILED`, shows that the floors alone still held |
| S5 (G3) | Change the call to `captureGolden("seeded_missing")`; `./scripts/verify.sh` | `a test captured app/src/test/screenshots/seeded_missing.png, which is not a golden under app/src/test/screenshots/`, plus the G2 line for `launch_placeholder.png` (the test also fails, because Roborazzi finds no original) |
| S6 (G4) | No source change; `./scripts/verify.sh` (passes), then `./scripts/verify.sh --record`, then `./scripts/check-test-counts.sh` | `golden app/src/test/screenshots/launch_placeholder.png has result type recorded; the verify run must report unchanged` |
| S7 (G5) | No source change; `./scripts/verify.sh` (passes), then truncate one per-capture file under `app/build/test-results/roborazzi/` to 0 bytes, then `./scripts/check-test-counts.sh` | `unreadable Roborazzi result <that path>: …` |

### 1.16 Repository, legal, store-listing and license checks

`scripts/check-metadata.sh` is D-152 as extended by D-179 (the `[M0]` additions) and D-190 (the `[M5]` listing, image and legal checks). **This listing is the single canonical one**: `L10N_STORE_LEGAL.md` §1.2, §2.6 and §3.4 and `FEATURES.md` F26-AC5, F27-AC5, F30-AC8 and F30-AC9 describe the same assertions and point here. Each `check_*` function and its call are added in the milestone in its comment, in the same commit as the content it checks; none is removed afterwards. This is the M6 state; the M0 file contains only the `[M0]` functions and calls.

Provenance: V14 exercised the earlier form of the M0 functions and of the listing and image checks. `check_no_bom` and `check_icon_glyphs` are ported from `L10N_STORE_LEGAL.md` §1.2 and §3.4, and the stricter listing and image logic from its §2.6 block, which was run on a synthetic 22-locale tree (L10N_STORE_LEGAL.md §0.3 item 11). D-179 requires every new `[M0]` function to be proven once during M0 against a seeded defect in a throwaway clone beside the checkout (`../2048-seeded`), with the failing line in the M0 commit body; a seeded defect is never committed.

```bash
#!/usr/bin/env bash
# Repository, legal, asset and store-listing checks (D-152, extended by D-179 and D-190;
# docs/modernization/QUALITY.md §1.16). Each check_* function is added in the milestone named in
# its comment, in the same commit as the content it checks, and is never removed afterwards.
set -uo pipefail
cd "$(dirname "$0")/.."
fail=0
err() { echo "::error::check-metadata: $*"; fail=1; }

# [M0] D-019: no tracked file outside docs/ names the sandbox-only Maven mirror host.
check_mirror_leak() {
  local needle='storage-download.''googleapis.com' # split so this file never matches itself
  local hits
  hits="$(git grep -l -F "$needle" -- . ':(exclude)docs' || true)"
  [[ -z "$hits" ]] || err "sandbox mirror host found in tracked files: $hits"
}

# [M0] D-001/D-152: the legacy submodule is gone.
check_no_submodule() {
  [[ ! -e .gitmodules ]] || err ".gitmodules exists"
  [[ -z "$(git ls-files -s | awk '$1 == "160000"')" ]] || err "a gitlink (submodule) is still tracked"
}

# [M0] D-151/D-179: scripts and the wrapper are committed executable.
check_executable_bits() {
  local bad
  bad="$(git ls-files -s -- gradlew 'scripts/*.sh' 'scripts/**/*.sh' | awk '$1 != "100755" { print $4 }')"
  [[ -z "$bad" ]] || err "not committed as 100755: $bad"
}

# [M0] D-142/D-144, D-179: no Lint or detekt baseline files.
check_no_baselines() {
  local hits
  hits="$(git ls-files | grep -iE '(^|/)[^/]*baseline[^/]*\.xml$' || true)"
  [[ -z "$hits" ]] || err "baseline files are not allowed: $hits"
}

# [M0] D-147, D-179: goldens are PNGs and total at most 10 MiB.
check_golden_budget() {
  local dir=app/src/test/screenshots total=0 n=0 f size
  [[ -d "$dir" ]] || return 0
  while IFS= read -r -d '' f; do
    case "$f" in *.png) ;; *) err "non-PNG file in $dir: $f" ;; esac
    size="$(stat -c %s "$f")"
    total=$((total + size))
    n=$((n + 1))
  done < <(find "$dir" -type f -print0)
  echo "check-metadata: $n goldens, $total bytes"
  ((total <= 10485760)) || err "goldens total $total bytes > 10 MiB (D-147)"
}

# [M0] D-120, D-179: LICENSE carries the three copyright lines, in order.
check_license_file() {
  local got
  got="$(grep -E '^Copyright \(c\) ' LICENSE)"
  [[ "$got" == $'Copyright (c) 2014 Paul Sarbinowski\nCopyright (c) 2014 Gabriele Cirulli\nCopyright (c) 2026 clayboicardi' ]] ||
    err "LICENSE copyright lines differ from D-120"
}

# [M0] D-143, D-174, D-181: opt-ins appear only in the two allowed files.
check_optin_allowlist() {
  local allowed='app/src/test/kotlin/com/clayboicardi/game2048/testing/Accessibility.kt
app/src/main/kotlin/com/clayboicardi/game2048/ui/components/GameModalSheet.kt'
  local hits extra
  hits="$(git grep -l -E '@(file:)?OptIn\(|^[[:space:]]*@Experimental[A-Za-z0-9]*Api\b|optIn\.add|-opt-in' \
    -- '*.kt' '*.kts' ':(exclude)docs' || true)"
  extra="$(comm -23 <(sort -u <<< "$hits" | sed '/^$/d') <(sort -u <<< "$allowed"))"
  [[ -z "$extra" ]] || err "opt-in outside the D-174/D-181 allowlist (a new one needs a D-173 entry): $extra"
}

# [M0] D-179 (L10N_STORE_LEGAL.md §1.2): no tracked file starts with a UTF-8 BOM. Lint's
# ByteOrderMark check does not report a leading BOM, and the legacy values-cs file had one.
check_no_bom() {
  python3 -I - << 'PY' || fail=1
import os, subprocess, sys
paths = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True).stdout.split(b"\0")
bad = []
for p in paths:
    if p and os.path.isfile(p):
        with open(p, "rb") as f:
            if f.read(3) == b"\xef\xbb\xbf":
                bad.append(p.decode("utf-8", "replace"))
for p in bad:
    print(f"::error::check-metadata: {p} starts with a UTF-8 BOM")
sys.exit(1 if bad else 0)
PY
}

# [M0] D-111, D-179 (L10N_STORE_LEGAL.md §3.4): the adaptive foreground and both API 24-25
# fallback icons carry the same four glyph paths. Glyph paths start with a lower-case "m",
# background shapes with an upper-case "M".
check_icon_glyphs() {
  local res=app/src/main/res ref f
  ref="$(grep -o 'pathData="m[^"]*"' "$res/drawable/ic_launcher_foreground.xml")"
  [[ "$(grep -c . <<< "$ref")" == 4 ]] || err "ic_launcher_foreground.xml: expected 4 glyph paths"
  for f in "$res/mipmap-anydpi/ic_launcher.xml" "$res/mipmap-anydpi/ic_launcher_round.xml"; do
    [[ "$(grep -o 'pathData="m[^"]*"' "$f")" == "$ref" ]] ||
      err "glyph paths in $f differ from ic_launcher_foreground.xml"
  done
}

# [M4] D-076/D-152: the numeral font subset is at most 32 KiB; the OFL text ships with it.
check_font() {
  local font=app/src/main/res/font/google_sans_flex_numerals.ttf
  local ofl=app/src/main/res/raw/ofl_google_sans_flex.txt
  if [[ -f "$font" ]]; then
    (($(stat -c %s "$font") <= 32768)) || err "$font is larger than 32,768 bytes"
    [[ -f "$ofl" ]] || err "$font exists but $ofl does not (OFL-1.1)"
  else
    [[ ! -f "$ofl" ]] || err "$ofl exists without the font (D-076 fallback omits both)"
  fi
}

# [M5] D-113/D-114/D-152, D-190 + FEATURES F30-AC8: store-listing texts.
check_store_listing() {
  python3 -I - << 'PY' || fail=1
import os, re, sys
root = "fastlane/metadata/android"
LOCALES = sorted("en-US cs da de el eo es fa fr hu it ko lt nl pl pt-BR pt-PT ru sl sr tr uk".split())
TEXTS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000,
         "changelogs/30000.txt": 500}  # Google Play limits; the title limit is 30
SCRIPT = {"ru": "Ѐ-ӿ", "uk": "Ѐ-ӿ", "sr": "Ѐ-ӿ",
          "el": "Ͱ-Ͽ", "fa": "؀-ۿ", "ko": "가-힣"}
NEEDLES = ("com.clayboicardi.game2048", "https://github.com/clayboicardi/2048-android-modern-refresh")
DENY = re.compile(r"\bport\b|webview|long touch", re.I)  # D-114 deny-words, every locale
NON_LATIN_DIGITS = re.compile("[٠-٩۰-۹]")
ok = True
def err(msg):
    global ok
    ok = False
    print(f"::error::check-metadata: {msg}")
if not os.path.isdir(root):
    err(f"{root} is missing")
    sys.exit(1)
dirs = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
if dirs != LOCALES:
    err(f"locale directories {dirs} != {LOCALES} (D-113)")
en = {}
for loc in ["en-US"] + [l for l in LOCALES if l != "en-US"]:  # en-US first: the others are compared with it
    base = os.path.join(root, loc)
    for name, limit in TEXTS.items():
        path = os.path.join(base, name)
        if not os.path.isfile(path):
            err(f"{path} is missing")
            continue
        with open(path, "rb") as f:
            raw = f.read()
        if raw.startswith(b"\xef\xbb\xbf") or b"\r" in raw:
            err(f"{path} has a BOM or a CR")
        try:
            t = raw.decode("utf-8")
        except UnicodeDecodeError:
            err(f"{path} is not UTF-8")
            continue
        if not t.endswith("\n") or t.endswith("\n\n"):
            err(f"{path} must end with exactly one newline")
        body = t[:-1] if t.endswith("\n") else t
        if not body.strip():
            err(f"{path} is empty")
        if len(body) > limit:
            err(f"{path} has {len(body)} characters > {limit}")
        if "com.uberspot.a2048" in body:
            err(f"{path} mentions com.uberspot.a2048")
        if NON_LATIN_DIGITS.search(body):
            err(f"{path} has non-Latin digits (use 0-9, D-103)")
        if name == "title.txt":
            if body != "2048":
                err(f"{path} is not exactly '2048' (U2)")
            continue
        if DENY.search(body):
            err(f"{path} contains a D-114 deny-word (port / WebView / long touch)")
        if loc == "en-US":
            en[name] = body
        elif body == en.get(name):
            err(f"{path} is identical to en-US (untranslated)")
        if loc in SCRIPT and not re.search(f"[{SCRIPT[loc]}]", body):
            err(f"{path} has no {loc} script letters")
        if name == "full_description.txt":
            for needle in NEEDLES:
                if needle not in body:
                    err(f"{path} must contain {needle} (D-005, D-114)")
    if loc != "en-US" and os.path.exists(os.path.join(base, "images")):
        err(f"{base}/images exists; graphics live only under en-US/images (D-113)")
sys.exit(0 if ok else 1)
PY
}

# [M5] D-115/D-152, D-190 + FEATURES F30-AC9: exactly the D-115 image set, 8-bit PNGs at exact
# sizes; color type 2 (RGB, no alpha) for the feature graphic and every screenshot, 2 or 6 for the icon.
check_store_images() {
  python3 -I - << 'PY' || fail=1
import os, struct, sys
img = "fastlane/metadata/android/en-US/images"
spec = {
    "icon.png": (512, 512, {2, 6}),
    "featureGraphic.png": (1024, 500, {2}),
    **{f"phoneScreenshots/{i}.png": (1080, 1920, {2}) for i in range(1, 5)},
    "sevenInchScreenshots/1.png": (2134, 1200, {2}),
    "tenInchScreenshots/1.png": (2560, 1440, {2}),
}
ok = True
def err(msg):
    global ok
    ok = False
    print(f"::error::check-metadata: {msg}")
have = sorted(
    os.path.relpath(os.path.join(d, f), img).replace(os.sep, "/")
    for d, _, files in os.walk(img) for f in files
)
if have != sorted(spec):
    err(f"{img} holds {have}; expected exactly {sorted(spec)} (D-113, D-115)")
for rel, (w, h, types) in spec.items():
    path = os.path.join(img, rel)
    try:
        with open(path, "rb") as f:
            head = f.read(26)
    except OSError:
        continue  # reported by the file-set check above
    if len(head) < 26 or head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        err(f"{path} is not a PNG")
        continue
    width, height, depth, color_type = struct.unpack(">IIBB", head[16:26])
    if (width, height) != (w, h):
        err(f"{path} is {width}x{height}, expected {w}x{h}")
    if depth != 8:
        err(f"{path} has bit depth {depth}, expected 8")
    if color_type not in types:
        err(f"{path} has PNG color type {color_type}, expected {sorted(types)} (2 = RGB, no alpha)")
sys.exit(0 if ok else 1)
PY
}

# [M5] D-121/D-123, D-190 + FEATURES F26-AC5, F27-AC5: NOTICE, PRIVACY.md and README links.
check_legal_and_privacy() {
  local item privacy
  local -a items=('gabrielecirulli/2048' "A small clone of 1024, based on Saming's 2048 (also a clone)"
    'uberspot/2048-android' 'Material Symbols' 'Apache-2.0' 'AndroidX')
  # NOTICE item 3 exists only with the font; the D-076 fallback omits both (L10N_STORE_LEGAL.md §4.1).
  [[ -f app/src/main/res/font/google_sans_flex_numerals.ttf ]] && items+=('Google Sans Flex' 'OFL-1.1')
  for item in "${items[@]}"; do
    grep -qF "$item" NOTICE || err "NOTICE does not contain: $item"
  done
  privacy="This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup."
  grep -qF "$privacy" PRIVACY.md || err "PRIVACY.md does not contain the D-123 text verbatim"
  grep -qF '(PRIVACY.md)' README.md || err "README.md does not link PRIVACY.md"
  grep -qF '(NOTICE)' README.md || err "README.md does not link NOTICE"
  grep -qF 'DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' README.md || err "README.md does not name the self-permission"
  grep -qF 'DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' PRIVACY.md || err "PRIVACY.md does not name the self-permission"
}

# [M5] D-120/D-122, D-190 + FEATURES F26-AC5: LICENSE is the final text of L10N_STORE_LEGAL.md §4.2,
# the in-app MIT text is a byte copy of it, and the Apache-2.0 text is the canonical one.
check_legal_texts() {
  local mit=app/src/main/res/raw/license_mit.txt apache=app/src/main/res/raw/license_apache_2_0.txt
  sha256sum --status -c <<< "ad96d837d59c50cb3c8e23a17d7ee19a396c5a1a6c4bc952a269896fcbdbfd19  LICENSE" ||
    err "LICENSE sha256 differs from L10N_STORE_LEGAL.md §4.2"
  cmp -s LICENSE "$mit" || err "$mit is not byte-identical to LICENSE"
  sha256sum --status -c <<< "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30  $apache" ||
    err "$apache is not the canonical Apache-2.0 text (sha256)"
}

# [M5] D-208, D-190 (L10N_STORE_LEGAL.md §1.4): the M3-M4 holding file for untranslated English
# strings is gone; from M5 on every string arrives with its 20 translations.
check_strings_final() {
  local pending=app/src/main/res/values/strings_pending.xml
  [[ ! -e "$pending" ]] || err "$pending exists; merge it into values/strings.xml (D-208)"
}

check_mirror_leak
check_no_submodule
check_executable_bits
check_no_baselines
check_golden_budget
check_license_file
check_optin_allowlist
check_no_bom
check_icon_glyphs
check_font              # [M4]
check_store_listing     # [M5]
check_store_images      # [M5]
check_legal_and_privacy # [M5]
check_legal_texts       # [M5]
check_strings_final     # [M5]

[[ "$fail" -eq 0 ]] && echo "check-metadata: OK"
exit "$fail"
```

`scripts/check-licenses.sh` `[M0]` (D-122):

```bash
#!/usr/bin/env bash
# License coverage of the release runtime classpath (D-122; QUALITY.md §1.16).
# Every Maven group resolved on :app:releaseRuntimeClasspath must have exactly one line
# "<group> <licenseId>" in config/license-groups.txt, and the file lists no other group.
set -uo pipefail
cd "$(dirname "$0")/.."
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"
ALLOWED_LICENSES='^(Apache-2\.0|MIT|OFL-1\.1)$'

deps="$(./gradlew -q "${EXTRA[@]}" :app:dependencies --configuration releaseRuntimeClasspath)" ||
  { echo "::error::check-licenses: :app:dependencies failed"; exit 1; }
resolved="$(sed -nE 's/^[|\\+ -]*--- ([^: ]+):[^: ]+.*$/\1/p' <<< "$deps" | sort -u)"
listed="$(sed -E 's/#.*$//' config/license-groups.txt | awk 'NF { print $1 }' | sort)"

fail=0
if [[ -z "$resolved" ]]; then echo "::error::check-licenses: no groups parsed"; fail=1; fi
bad="$(sed -E 's/#.*$//' config/license-groups.txt | awk 'NF' | awk -v re="$ALLOWED_LICENSES" 'NF != 2 || $2 !~ re')"
if [[ -n "$bad" ]]; then echo "::error::check-licenses: malformed lines (need '<group> <licenseId>'):"; echo "$bad"; fail=1; fi
dupes="$(uniq -d <<< "$listed")"
if [[ -n "$dupes" ]]; then echo "::error::check-licenses: duplicate groups: $dupes"; fail=1; fi
missing="$(comm -23 <(printf '%s\n' "$resolved") <(sort -u <<< "$listed"))"
if [[ -n "$missing" ]]; then
  echo "::error::check-licenses: groups on releaseRuntimeClasspath missing from config/license-groups.txt:"
  echo "$missing"
  fail=1
fi
stale="$(comm -13 <(printf '%s\n' "$resolved") <(sort -u <<< "$listed"))"
if [[ -n "$stale" ]]; then
  echo "::error::check-licenses: config/license-groups.txt lists groups that are not on the classpath:"
  echo "$stale"
  fail=1
fi
[[ "$fail" -eq 0 ]] && echo "check-licenses: $(wc -l <<< "$resolved") groups covered"
exit "$fail"
```

`config/license-groups.txt` at M0 has one `<group> Apache-2.0` line per group the script reports (37 in the scratch skeleton, V13). The `androidx.appcompat` group is `appcompat-resources`, a transitive dependency of `core-splashscreen`; it is not the AppCompat activity library that D-102 excludes. Generate the initial file from the script's "missing" output, then confirm each license from the artifact's POM.

### 1.17 Reproducibility probe (report-only)

`scripts/repro-probe.sh` `[M6]` (D-191) builds the committed `HEAD` again in a fresh clone at another path and compares the release APKs. It never fails a gate (D-133 has no release workflow; F-Droid reproducibility is a goal, not a requirement, research/06 §4.3).

```bash
#!/usr/bin/env bash
# Reproducibility probe (report-only; docs/modernization/QUALITY.md §1.17).
# Builds the committed HEAD's release APK again in a fresh clone at another path (build cache off),
# then once more there after `clean`, and compares both with the APK in this checkout. A clone, not
# a git worktree: AGP's VCS info cannot read a worktree's .git file (NO_VALID_GIT_FOUND).
# Never fails: differences are printed as warnings with the differing zip entries.
set -uo pipefail
cd "$(dirname "$0")/.."
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"
apk=app/build/outputs/apk/release/app-release.apk

if ! git diff --quiet HEAD --; then
  echo "::warning::repro-probe: uncommitted changes; probe skipped (it compares builds of HEAD)"
  exit 0
fi
if [[ ! -f "$apk" ]]; then
  ./gradlew "${EXTRA[@]}" :app:assembleRelease ||
    { echo "::warning::repro-probe: first build failed"; exit 0; }
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git clone --quiet --no-hardlinks --no-checkout . "$tmp/tree"
git -C "$tmp/tree" checkout --quiet --detach "$(git rev-parse HEAD)"
[[ -f local.properties ]] && cp local.properties "$tmp/tree/"

build_second() {
  (cd "$tmp/tree" && ./gradlew "${EXTRA[@]}" --no-build-cache --no-configuration-cache "$@" :app:assembleRelease -q)
}

compare() { # label other-apk
  local label="$1" other="$2"
  sha256sum "$apk" "$other"
  if cmp -s "$apk" "$other"; then
    echo "repro-probe: $label: bit-identical"
    return
  fi
  echo "::warning::repro-probe: $label: release APK differs"
  python3 -I - "$apk" "$other" << 'PY'
import sys, zipfile
a, b = (zipfile.ZipFile(p) for p in sys.argv[1:3])
ea = {i.filename: i.CRC for i in a.infolist()}
eb = {i.filename: i.CRC for i in b.infolist()}
for name in sorted(set(ea) | set(eb)):
    if ea.get(name) != eb.get(name):
        print(f"  differs: {name}")
PY
}

if build_second; then
  compare "second path, no build cache" "$tmp/tree/$apk"
  if build_second clean; then
    compare "second path, after clean" "$tmp/tree/$apk"
  else
    echo "::warning::repro-probe: third build failed"
  fi
else
  echo "::warning::repro-probe: second build failed"
fi
exit 0
```

When it runs: once locally in M6 on the final commit, under the build lock (§3.1), with its result line handed to the orchestrator for the PR body; and in CI on every `workflow_dispatch` run (§5.2, `continue-on-error`). The release APKs are signed with the same committed key, and APK Signature Scheme v2 signing is deterministic for identical content, so comparing the signed files is meaningful (V11). Known sources of difference, if it ever reports one: `assets/dexopt/baseline.prof[m]`, R8 output order, resource shrinking, `META-INF/version-control-info.textproto` (worktree builds). Record the differing entries in the PR body; do not change the build to "fix" them inside this PR (D-134 already removed the avoidable ones).

### 1.18 Where each class is specified

| Area | Specification | This file adds |
|---|---|---|
| Engine | `ARCHITECTURE.md` §3, §10.2 | §1.2–§1.4 generators, golden bytes, methods |
| Persistence | `ARCHITECTURE.md` §4.3–§4.5, §9 | §1.5 fixture contents, §1.6 corruption table, `DataMigrationSeamTest` |
| Features | `FEATURES.md` §1, §8 | — |
| Motion | `MOTION_SPEC.md` §8–§9 | — |
| Goldens | `DESIGN_SYSTEM.md` §10 | §1.9 Read rule and CI fallback |
| Gates | D-148, D-149, D-152, D-179, D-190, D-191 | §1.13–§1.17 scripts |

---

## 2. Static analysis

### 2.1 Formatting: Spotless 8.10.4 + ktfmt 0.65

Root `build.gradle.kts`, exactly `ARCHITECTURE.md` §2.5: `kotlin { target("**/*.kt"); targetExclude("**/build/**", "docs/**"); ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle() }` and the same for `kotlinGradle` on `**/*.gradle.kts`. The `docs/**` exclusion keeps the unformatted research evidence under `docs/modernization/research/smoke-build/` out of the check (D-175).

- Run `./gradlew spotlessApply` (under the build lock, §3.1) before every commit; `spotlessCheck` is part of every gate level.
- ktfmt kotlinlang style wraps at 100 columns.
- Fallback (D-141; DECISIONS verification-pending item 6, checked in M1 and M3 by running `spotlessApply` twice, then `spotlessCheck`): if 0.65 fails to resolve or is not idempotent, set `ktfmt = "0.64"` in the catalog and record it.

### 2.2 Android Lint

`app/build.gradle.kts` `lint {}` is exactly D-142 (`ARCHITECTURE.md` §2.6): `abortOnError`, `warningsAsErrors`, `checkReleaseBuilds`, `checkDependencies`, the four time-dependent checks disabled, `MissingTranslation` and `ExtraTranslation` as errors (a translated `app_name` is reported as `Untranslatable`, also fatal; technical correction TC-3), `sarifReport = true`, plus `lintChecks(libs.compose.lint.checks)` (Slack compose-lint-checks 1.6.1).

- Gate task: `:app:lintDebug`. `assembleRelease` also runs `lintVitalRelease` (`checkReleaseBuilds`).
- Reports: `app/build/reports/lint-results-debug.{html,xml,sarif}`, uploaded by CI.
- `:core:engine` is not analysed by Lint ("Lint will treat :core:engine as an external dependency"); that line is expected. The engine is covered by detekt and its tests.
- **No baseline** (`check_no_baselines`, §1.16).
- Every composable that emits UI takes `modifier: Modifier = Modifier` (Slack `ComposeModifierMissing` is an error).

### 2.3 Kotlin compiler

`kotlin { compilerOptions { allWarningsAsErrors = true } }` in both modules (D-143). No `optIn.add(…)`, no `-opt-in` flags. The opt-in allowlist is enforced by `check_optin_allowlist` (§1.16):

| File | Opt-in | Why | Decision |
|---|---|---|---|
| `app/src/test/kotlin/com/clayboicardi/game2048/testing/Accessibility.kt` | `@file:OptIn(ExperimentalRoborazziApi::class)` | Every Roborazzi ATF API is experimental (V5) | D-174 |
| `app/src/main/kotlin/com/clayboicardi/game2048/ui/components/GameModalSheet.kt` | `@OptIn(ExperimentalMaterial3Api::class)` | `ModalBottomSheet`, `rememberBottomSheetState`, `SheetValue` (`DESIGN_SYSTEM.md` §7.4) | D-181 |

A new opt-in anywhere else needs an execution-time D-173 entry and a matching change to `check_optin_allowlist` in the same commit.

A deprecation is never suppressed to pass `allWarningsAsErrors`; migrate to the replacement API (D-143).

### 2.4 detekt 2.0.0-alpha.6 (optional, with an exit criterion)

Configuration: `ARCHITECTURE.md` §2.7–§2.8 (`buildUponDefaultConfig`, one root `config/detekt/detekt.yml` generated by `detektGenerateConfig` and tuned exactly as §2.8 lists, `parallel = true`). Gate tasks: `detektMain detektTest` at the root; Gradle runs them in both modules, and on `:app` they cover production and unit-test sources (`detektDebug`, `detektRelease`, `detektDebugUnitTest`; V17).

- Findings are fixed in code. Rule tuning beyond `ARCHITECTURE.md` §2.8 needs a reason in the commit body; `@Suppress("RuleId")` follows §2.5.
- **Exit criterion (D-144).** A *tool failure* is a detekt task that fails with a stack trace from detekt or the Kotlin analysis it runs (for example `NoSuchMethodError`, `IllegalStateException` in `dev.detekt.*`, or a compiler-plugin crash), not "N issues found". On a tool failure, make one fix attempt (a configuration change only). If it still fails, remove detekt in one commit `ci: remove detekt (tool failure, D-144)`: the `detekt` plugin alias and blocks in both modules and the root, `config/detekt/`, and `detektMain detektTest` from `scripts/verify.sh`. Paste the stack-trace head into the PR body.

### 2.5 Suppression policy

- `@Suppress("Id") // <reason>` and `tools:ignore="Id"` with an adjacent `<!-- reason -->` comment, at the narrowest scope (statement or declaration), never file-level for deprecations.
- No `lint-baseline.xml` or detekt baseline (§1.16).
- M6 lists every suppression in the PR body: `git grep -n -E '@Suppress\(|tools:ignore=' -- app core`.
- `AGENTS.md` `## Code Review Rules` → "Verification integrity" (D-171, D-192) asks Codex to flag new suppressions without a reason.

### 2.6 Gradle test-task integrity rules

Two rules (D-176; V7, V8):

1. **Files that tests read by path are declared task inputs.** Gradle only re-runs a test task when a declared input changes. The goldens are read by path, not as resources, so without the input a golden-only change leaves `:app:testDebugUnitTest` `UP-TO-DATE` and a wrong golden passes (V7). The `:app` unit-test tasks declare `src/test/screenshots/**/*.png` (`goldenScreenshots`) and `config/license-groups.txt` (`licenseGroups`); `:core:engine:test` declares `src/main/kotlin` (`engineSources`), which `EngineImportsGuardTest` reads as text. All use relative path sensitivity.
2. **Test results are never restored from the build cache.** `setup-gradle` caches `~/.gradle/caches`, which holds the local build cache (V2), so a CI run could otherwise load a test result produced by another run (V8). Both test tasks use `outputs.cacheIf("test results are never reused from the build cache") { false }`.

The snippets are part of `ARCHITECTURE.md` §2.6 and §2.7 (D-176); they are added in M0, before the first golden, and compiled and behaved as described under the configuration cache in the scratch build. Any later test that reads a repository file by path (outside `src/test/resources` and Android resources) adds that file to these inputs in the same commit.

---

## 3. `scripts/verify.sh`

### 3.1 Levels and listing

D-140's command list is unchanged; it is the `full` level, the only push gate. `quick`, `--record` and `full --record` are local conveniences around it and never replace `full` before a push (D-178).

| Invocation | Runs | Use |
|---|---|---|
| `./scripts/verify.sh` or `./scripts/verify.sh full` | `spotlessCheck detektMain detektTest :core:engine:test :core:engine:koverVerify :app:testDebugUnitTest -Proborazzi.test.verify=true :app:lintDebug :app:assembleDebug :app:assembleRelease` with `--continue`, then `check-test-counts`, `check-apk`, `check-licenses`, `check-metadata` | Before every push (D-163). CI runs exactly this |
| `./scripts/verify.sh quick` | `spotlessCheck :core:engine:test` | Inner loop while editing the engine; never a push gate |
| `./scripts/verify.sh --record` | `:app:testDebugUnitTest --rerun -Proborazzi.test.record=true`, then `git status --porcelain --untracked-files=all -- app/src/test/screenshots` | After an intended UI change; then Read every listed PNG (§1.9) |
| `./scripts/verify.sh full --record` | `--record`, then `full` | Record and gate in one call |

Exit codes: 0 = every stage passed; 1 = a stage failed (the script still runs every later stage so one run shows every failure); 2 = bad arguments. The last line is always `verify.sh: level=<…> record=<…> result=PASS|FAIL elapsed=<s>s`.

**Local invocation (D-178).**
- **One local build at a time** across all checkouts and worktrees (4 CPUs, about 15 GB of RAM; research/07 G19). Every local `./scripts/verify.sh …` and every direct `./gradlew …` runs as `flock "$HOME/.cache/game2048-gradle.lock" <command>`. The scripts never take the lock themselves, because CI runs them too; the Gradle calls inside `verify.sh` and the check scripts run under the outer lock.
- **Detached, logged runs.** A local full run starts detached and writes its output to `$HOME/.cache/game2048/verify-<short-sha>-<level>.log`. Only the log's final line, `verify.sh: level=<level> record=<bool> result=PASS|FAIL …`, decides the outcome. A tool timeout or a killed run is never a result: re-run, or wait for the final line.
- `./gradlew --stop` (under the lock) runs at the end of each milestone.

```bash
mkdir -p "$HOME/.cache/game2048"
LOG="$HOME/.cache/game2048/verify-$(git rev-parse --short HEAD)-full.log"
nohup flock "$HOME/.cache/game2048-gradle.lock" ./scripts/verify.sh > "$LOG" 2>&1 &
tail -n 1 "$LOG"   # poll until it prints the verify.sh line
```

CI runs `./scripts/verify.sh` directly, with no lock and no log file.

```bash
#!/usr/bin/env bash
# The single merge gate (D-140; levels per docs/modernization/QUALITY.md §3).
# CI runs `./scripts/verify.sh` with no arguments, which is the full gate.
#
#   ./scripts/verify.sh [full]         every gate CI runs; required before every push (D-163)
#   ./scripts/verify.sh quick          spotlessCheck + engine tests; inner loop only, never a push gate
#   ./scripts/verify.sh --record       re-record Roborazzi goldens, then list every new or changed PNG
#   ./scripts/verify.sh full --record  --record, then the full gate
#
# Local-only Gradle arguments (an out-of-repo init script, -ProbolectricRepoUrl=...) come from
# the VERIFY_GRADLE_ARGS environment variable (D-019). CI sets nothing.
set -uo pipefail
cd "$(dirname "$0")/.."
export ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-37.0.0}"
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"

level=""
record=false
for arg in "$@"; do
  case "$arg" in
    full | quick) level="$arg" ;;
    --record) record=true ;;
    -h | --help) sed -n '2,11p' "$0"; exit 0 ;;
    *) echo "verify.sh: unknown argument '$arg' (see --help)" >&2; exit 2 ;;
  esac
done
if [[ "$record" == true && "$level" == quick ]]; then
  echo "verify.sh: --record cannot be combined with quick" >&2
  exit 2
fi
if [[ -z "$level" ]]; then
  if [[ "$record" == true ]]; then level=none; else level=full; fi
fi

start=$SECONDS
fail=0

if [[ "$record" == true ]]; then
  # --rerun forces the test task to execute even when Gradle considers it up to date.
  ./gradlew "${EXTRA[@]}" :app:testDebugUnitTest --rerun -Proborazzi.test.record=true || fail=1
  echo "== Goldens added or changed (Read every file before committing; D-147):"
  git status --porcelain --untracked-files=all -- app/src/test/screenshots
fi

case "$level" in
  quick)
    ./gradlew "${EXTRA[@]}" --continue spotlessCheck :core:engine:test || fail=1
    ;;
  full)
    ./gradlew "${EXTRA[@]}" --continue \
      spotlessCheck detektMain detektTest \
      :core:engine:test :core:engine:koverVerify \
      :app:testDebugUnitTest -Proborazzi.test.verify=true \
      :app:lintDebug :app:assembleDebug :app:assembleRelease || fail=1
    for s in check-test-counts check-apk check-licenses check-metadata; do
      "./scripts/$s.sh" || { echo "FAILED: $s"; fail=1; }
    done
    ;;
esac

result=PASS
[[ "$fail" -eq 0 ]] || result=FAIL
echo "verify.sh: level=$level record=$record result=$result elapsed=$((SECONDS - start))s"
exit "$fail"
```

`--record` does not list deleted goldens: when a test or golden is renamed, delete the old PNG; the orphan check of the next `full` run fails until it is gone (§1.15).

### 3.2 Expected runtime

Measured (V3) on the M0-shaped scratch project, 4 CPUs, dependencies already in the Gradle cache:

| Level | Warm (build cache, configuration cache) | Build directories deleted, `--no-build-cache` |
|---|---|---|
| `full` | 29–45 s | 101 s |
| `quick` | 2 s (up to date) | ≈ 20 s (estimate) |
| `--record` | 42 s (first run, including the SDK 24 jar download) | — |

Projection for the end of M6 (estimate, to be replaced by measurements in the M3 part 1 and M6 commit bodies): engine ≈ 20 s (13 properties × 1,000, 100,000 spawns, the 2 s performance test); app ≈ 4–8 min (about 60 behaviour and motion classes plus 45 goldens on Robolectric); Lint ≈ 1 min; R8 ≈ 1 min. **Full gate warm: 8–12 min locally**, longer than a typical foreground tool limit, hence the detached runs of §3.1. First run on a new machine adds the dependency download (≈ 1 GB) and the three Robolectric jars (88 + 213 + 239 MB).

If a local warm full run exceeds 20 min or a CI warm run exceeds 30 min, the first lever is `maxParallelForks = 2` on `:app:testDebugUnitTest`, through a D-173 entry with the measurements. (Two forks of 3 GiB each fit the 16 GB runner; the entry changes `ARCHITECTURE.md` §2.6.)

### 3.3 Sandbox-only network workarounds (local, never committed)

Maven Central may return HTTP 429 in the execution sandbox (research/03 §6). Robolectric downloads its `android-all` jars itself, outside Gradle (research/07 §0 item 7). Both are handled **outside the repository** (D-019):

1. Create the init script outside the working tree, for example `$HOME/.config/game2048/mirror.init.gradle.kts`:

   ```kotlin
   // Local-only. Prepends Google's Maven Central mirror; never commit this file (D-019).
   val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
   beforeSettings {
       pluginManagement.repositories.maven(mirror) { name = "MavenCentralGcsMirror" }
       dependencyResolutionManagement.repositories.maven(mirror) { name = "MavenCentralGcsMirror" }
   }
   ```

2. Pass it and the Robolectric repository through `VERIFY_GRADLE_ARGS`:

   ```bash
   export VERIFY_GRADLE_ARGS="--init-script $HOME/.config/game2048/mirror.init.gradle.kts -ProbolectricRepoUrl=https://maven-central.storage-download.googleapis.com/maven2"
   flock "$HOME/.cache/game2048-gradle.lock" ./scripts/verify.sh   # detached and logged as in §3.1
   ```

   `robolectricRepoUrl` feeds the inert hook in `app/build.gradle.kts` (`robolectric.dependency.repo.url`, `ARCHITECTURE.md` §2.6). The check scripts that call Gradle (`check-apk.sh`, `check-licenses.sh`, `repro-probe.sh`) read the same variable.

Rules:
- Never put the init script in `~/.gradle/init.d/`: plain `./gradlew` runs would then differ silently from CI.
- Never write the mirror URL into a tracked file outside `docs/`. `check_mirror_leak` fails the gate if one appears (§1.16).
- The commit body notes `(sandbox mirror)` after the gate result when the mirror was used. CI never uses it, so CI is the proof that the build works without it.

### 3.4 Gate result in commit bodies

```text
Gates: scripts/verify.sh passed (engine=<n> tests, app=<m> tests) [sandbox mirror]
```

`<n>` and `<m>` are the executed counts printed by `check-test-counts.sh`; they equal the new `config/test-count-floor.txt` lines (D-164). The run behind this line is the detached full run on the committed HEAD (§3.1); the integrator hands its log path and final line to the orchestrator (D-194).

### 3.5 Failure triage

| Failing stage | First action |
|---|---|
| `spotlessCheck` | `./gradlew spotlessApply`, commit |
| `detekt*` | Fix the finding; tool failure → §2.4 |
| `:core:engine:test` | Read `core/engine/build/reports/tests/test/index.html`; a property failure prints its seed and shrunk input → `EngineRegressionTest` first |
| `koverVerify` | Add tests; never exclude code (§4) |
| `:app:testDebugUnitTest` | `app/build/reports/tests/testDebugUnitTest/index.html`; golden mismatch → Read `app/build/outputs/roborazzi/*_compare.png`; ATF → the failure lists the check and the node bounds |
| `:app:lintDebug` | `app/build/reports/lint-results-debug.html`; fix, or suppress per §2.5 |
| `assembleRelease` | R8 output in the build log; never add a blanket `-keep` (D-134) |
| `check-test-counts` | Floor below the count → update the floor in this commit; floor above → find the lost test; skipped → remove the skip; orphan (G2) → delete the PNG only if its test was intentionally removed, otherwise restore the `captureGolden` call; no Roborazzi results (G1) → restore the removed `captureGolden` calls or the screenshot classes, never delete goldens to pass; a result without a golden (G3) → `--record`, Read and commit the PNG, or fix the name; result `recorded` (G4) → the results come from a record run, not from the full gate (for example the script was run by hand after `--record`): run `./scripts/verify.sh`, and if it still reports `recorded`, `./gradlew clean` (under the lock) first; `changed`/`added` (G4) → the test failure above it; unreadable result (G5) → re-run the full gate |
| `check-apk` | A new permission or exported component → find the dependency (`./gradlew :app:dependencies`), remove it or `tools:node="remove"` the component; never widen the allowlist without an execution-time D-173 entry |
| `check-licenses` | Add or remove `config/license-groups.txt` lines; update `third_party_licenses.json` (D-122) |
| `check-metadata` | The message names the file and rule |

---

## 4. Coverage thresholds (Kover)

| Scope | Rule | Threshold | Task |
|---|---|---|---|
| `:core:engine` | Line coverage | ≥ 90 % | `:core:engine:koverVerify` |
| `:core:engine` | Branch coverage | ≥ 85 % | same |
| `:app` | No coverage gate | — | — |

DSL: `ARCHITECTURE.md` §2.7 (`rule("line") { minBound(90, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE) }`, `rule("branch") { minBound(85, CoverageUnit.BRANCH, …) }`); both rules were seen to fail with an uncovered function (V10). HTML report: `./gradlew :core:engine:koverHtmlReport` → `core/engine/build/reports/kover/html/index.html`.

Why these numbers and this scope (D-145):
- The engine holds the irreversible logic: rules, PRNG and history layouts are persisted and replayed (D-039, D-042). A missed branch there corrupts saves or scores.
- It is small, pure and fully reachable from tests, and the property suite drives most branches. The headroom below 100 % is for defensive `require` messages and `toString`, which are not worth dedicated tests.
- Branch coverage is the stronger signal for merge-once and status-machine code; 85 % leaves room for the `require` branches of `fromBytes` validators that only the rejection tests reach.
- `:app` has no coverage gate: Compose and Robolectric coverage numbers are dominated by generated lambdas and inline functions, so a percentage would measure the instrumentation more than the tests. The app is gated by behaviour tests mapped 1:1 to ACs (`FEATURES.md` §0.1), goldens, ATF and the exact test-count floor.

Rules: thresholds are floors that may be raised (an execution-time D-173 entry) and are never lowered; no class or package exclusions; a drop below a threshold is fixed with tests.

---

## 5. CI design

### 5.1 Overview

`.github/workflows/ci.yml` is committed **in the plan PR** and is the only workflow (D-151 as refined by D-177); M0 keeps it unchanged. One job, `verify`, on `ubuntu-24.04`. Its first step after checkout decides what to run:

| Tree | Detect result | Job |
|---|---|---|
| Plan-only: no `settings.gradle.kts`, legacy Groovy `build.gradle` present, no Kotlin-DSL build files | `present=false` | Every later step is skipped; the job is a green no-op with a notice |
| Kotlin-DSL project (M0 onward): `settings.gradle.kts` present and `scripts/verify.sh` executable | `present=true` | Full verification (§5.2) |
| Anything else (no `settings.gradle.kts` but Kotlin-DSL files exist or the legacy build is gone; or `verify.sh` missing or not executable) | — | Fails with an error annotation |

After M0 deletes the legacy `build.gradle` (D-001), the no-op path can never be taken again; deleting `settings.gradle.kts` fails CI instead of skipping it. A job-level `if:` cannot use `hashFiles()`, so the decision is a step output and every later step carries `if: steps.project.outputs.present == 'true'`.

Pushing the plan PR's `ci.yml` is also the first test of the token's `workflows` scope and of Actions being enabled on the fork (D-165 first-push check, research/07 G1, U8; DECISIONS verification-pending item 2). Those checks therefore happen on the plan PR, not in M0 (D-177). Actions and Codex are already enabled on the fork, and no step needs an admin API call (D-195).

### 5.2 Steps

| # | Step | Condition | Notes |
|---|---|---|---|
| 1 | `actions/checkout` v7.0.1 | always | `persist-credentials: false`: no job step pushes |
| 2 | Detect Gradle Kotlin-DSL project | always | §5.1 |
| 3 | `actions/setup-java` v6.1.0 | present | Temurin 21 (D-014) |
| 4 | `gradle/actions/setup-gradle` v6.4.0 | present | `cache-provider: basic`; `cache-read-only: false`; `validate-wrappers: true` (every `gradle-wrapper.jar` in the repository is checksum-validated; this is the wrapper-validation step, D-151) |
| 5 | `actions/cache` v6.1.0 | present | `~/.m2/repository/org/robolectric` (≈ 540 MB for SDK 24, 36, 37) |
| 6 | Android SDK packages | present | Idempotent `sdkmanager --install "platforms;android-37.0" "build-tools;36.0.0" "build-tools;37.0.0"` |
| 7 | `./scripts/verify.sh` | present | Exactly the local `full` gate (D-140) |
| 8 | Upload APKs | present, not cancelled | Even when step 7 failed, so a partially green build can be inspected |
| 9 | Upload reports | present, not cancelled | |
| 10 | Upload screenshot diffs | present, failure | Small artifact for §1.9's CI fallback |
| 11 | Reproducibility probe | present, `workflow_dispatch`, script exists | `continue-on-error: true`; report-only (§1.17) |

### 5.3 Differences from D-151's listing

These are the differences that D-177 records (it refines D-151). None of them weakens a gate.

| Change | Reason |
|---|---|
| Detect step and step-level conditions | The plan PR has no buildable project (D-177) |
| `persist-credentials: false` on checkout | Least privilege; nothing in the job uses git credentials |
| `validate-wrappers: true` written out | It is the default; writing it documents the wrapper-validation gate |
| `cache-read-only: false` | The default is read-only off `master`. Until the execution PR merges, `master` has no Gradle cache, so every execution-PR run would start cold. Writing from PR runs lets later pushes to the same PR start warm (caches are scoped to the PR ref) |
| `HEAD_SHA` in artifact names | `github.sha` of a `pull_request` run is the synthetic merge commit; artifacts are named after the PR head so `game2048-apks-<sha>` matches the commit the PR body lists |
| `overwrite: true` on uploads | A re-run attempt would otherwise fail on an existing artifact name |
| Screenshot-diff artifact (failure only) | §1.9 fallback without downloading the full reports |
| Reproducibility probe on `workflow_dispatch` | §1.17; report-only |

Everything else (triggers, `contents: read`, concurrency, `ubuntu-24.04`, `timeout-minutes: 45`, `GRADLE_OPTS`, `ANDROID_BUILD_TOOLS=37.0.0`, the SDK step, APK and report paths, retention 30/14 days) is D-151 verbatim.

### 5.4 Caching

| Cache | Provider | Key | Size |
|---|---|---|---|
| Gradle user home (`caches`, `notifications`; includes dependencies, wrapper, configuration and build cache) | setup-gradle `basic` (actions/cache) | Hash of `**/*.gradle*`, `**/gradle-wrapper.properties`, `gradle/*.versions.toml`; no restore keys (research/06 §3.2) | ≈ 1.5–3 GB |
| Robolectric jars | actions/cache | `robolectric-<os>-<hash of libs.versions.toml, robolectric.properties>` with restore key `robolectric-<os>-` | ≈ 540 MB |

Test results never come from the build cache (§2.6), so caching only speeds up compilation and dependency resolution. The repository cache limit (10 GB) is not a concern for one workflow.

`cache-read-only: false` (§5.3) lets PR runs write Gradle caches scoped to the PR ref. It costs repository cache quota (far from the limit) and buys warm runs for the long-lived execution PR. If cache poisoning from PR branches ever becomes a concern, for example with several contributors, return to the default; that changes speed only, never a result.

### 5.5 Artifacts

| Name | Contents | When | Retention |
|---|---|---|---|
| `game2048-apks-<head-sha>` | `app/build/outputs/apk/debug/*.apk`, `app/build/outputs/apk/release/*.apk`, `app/build/outputs/mapping/release/mapping.txt` | Every non-cancelled run with a project | 30 days |
| `game2048-reports-<head-sha>` | `**/build/reports/` (tests, Lint SARIF/HTML, detekt, Kover, Roborazzi report, inspection filmstrips), `**/build/test-results/` (JUnit XML, Roborazzi JSON), `app/build/outputs/roborazzi/` | same | 14 days |
| `game2048-screenshot-diffs-<head-sha>` | `app/build/outputs/roborazzi/` (`*_actual.png`, `*_compare.png`), `app/build/test-results/roborazzi/`, `app/build/reports/roborazzi/` | Failed runs only | 14 days |

Both APKs are signed with the committed CI sideload key (D-131), so each installs over the previous CI build of the same variant; debug (`com.clayboicardi.game2048.debug`) and release install side by side.

To fetch an artifact, the orchestrator lists the `ci.yml` runs on `claude/2048-modernization-plan-8eneix`, picks the run whose head SHA is the pushed SHA, lists that run's artifacts and downloads one, all via the GitHub API (MCP tool or gh; §6.8 rows "Workflow runs on branch" and "Artifacts"). MCP `actions_get` `download_workflow_run_artifact` returns a URL to fetch; gh is `gh run download <run-id> -R "$R" -n game2048-apks-<head-sha>`. Each download goes into a new, empty directory beside the checkout (for example `../2048-ci-artifacts/<head-sha>/`) and is untrusted data.

### 5.6 Concurrency

`group: ci-${{ github.event.pull_request.number || github.ref }}` with `cancel-in-progress` only for `pull_request` events (D-151). A new push to the PR cancels the run of the superseded commit; `master` pushes queue instead of cancelling, so every merge commit gets a complete result.

### 5.7 Time budget

| Run | Estimate |
|---|---|
| Plan PR (no-op) | < 1 min |
| M0, cold | 6–9 min (Gradle distribution, ≈ 1 GB dependencies, 540 MB Robolectric jars, ≈ 2 min of build) |
| M6, cold | 15–25 min |
| M6, warm | 10–15 min |
| Timeout | 45 min (D-151) |

Record the measured durations of the M0 and M6 runs in the PR body. If a local warm full run exceeds 20 min or a CI warm run exceeds 30 min, the first lever is `maxParallelForks = 2` on `:app:testDebugUnitTest`, through a D-173 entry with the measurements (§3.2).

### 5.8 Action pins

Re-verified 2026-10-28 with `git ls-remote https://github.com/<repo> refs/tags/<tag> 'refs/tags/<tag>^{}'` (V1):

| Action | Tag | Pinned commit | Tag object |
|---|---|---|---|
| `actions/checkout` | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | lightweight |
| `actions/setup-java` | v6.1.0 | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` | lightweight |
| `gradle/actions/setup-gradle` | v6.4.0 | `3f5f9adaf7d9fecd50b5935e54106014257a94e6` | annotated `b9bee63ef481cc545e4517ec730544a4d8b1dfa0`, peeled |
| `actions/cache` | v6.1.0 | `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` | lightweight |
| `actions/upload-artifact` | v7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` | lightweight |

The other actions in research/06 §3.1 (`download-artifact`, `android-emulator-runner`, `action-gh-release`) are not used and were not re-verified. Pins change only in a commit that re-runs the `git ls-remote` check and records the output (no Dependabot, D-153). `ubuntu-24.04` is pinned because `ubuntu-latest` moves to 26.04 with JDK 25 as default (research/06 §0, §3.3).

### 5.9 The workflow file

The committed `.github/workflows/ci.yml` is the source of truth; this is its content:

```yaml
# The only workflow of this repository (D-151, as refined by D-177). Design and rationale:
# docs/modernization/QUALITY.md §5; merge protocol: QUALITY.md §6.
#
# Bootstrap behaviour. Until execution milestone M0 lands, the tree holds the legacy Groovy build
# (not buildable) plus the plan documents. The "Detect" step then sets present=false and every
# later step is skipped, so the job is a green no-op. From M0 on, settings.gradle.kts exists and
# the job runs scripts/verify.sh, the same gate the executor runs locally (D-140, D-163). Once the
# legacy build.gradle is deleted (M0), a missing settings.gradle.kts fails the job instead.
name: CI

on:
  pull_request:
    branches: [master]
  push:
    branches: [master]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ci-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

env:
  GRADLE_OPTS: -Dorg.gradle.console=plain
  ANDROID_BUILD_TOOLS: '37.0.0'
  # Artifacts are named after the PR head commit, not the synthetic merge commit.
  HEAD_SHA: ${{ github.event.pull_request.head.sha || github.sha }}

defaults:
  run:
    shell: bash

jobs:
  verify:
    name: verify
    runs-on: ubuntu-24.04 # pinned: ubuntu-latest moves to Ubuntu 26.04 (research/06 §0, §3.3)
    timeout-minutes: 45
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          persist-credentials: false

      - name: Detect Gradle Kotlin-DSL project
        id: project
        run: |
          if [[ -f settings.gradle.kts ]]; then
            if [[ ! -x scripts/verify.sh ]]; then
              echo "::error file=scripts/verify.sh::settings.gradle.kts exists but scripts/verify.sh is missing or not executable (D-140, D-151)"
              exit 1
            fi
            echo "present=true" >> "$GITHUB_OUTPUT"
            echo "Gradle Kotlin-DSL project found: running scripts/verify.sh." >> "$GITHUB_STEP_SUMMARY"
          elif [[ -f build.gradle && ! -e gradle/libs.versions.toml && ! -e app/build.gradle.kts && ! -e core/engine/build.gradle.kts ]]; then
            echo "present=false" >> "$GITHUB_OUTPUT"
            msg="No settings.gradle.kts: plan-only tree with the legacy Groovy build. Build and verify steps are skipped until milestone M0 lands."
            echo "::notice::$msg"
            echo "$msg" >> "$GITHUB_STEP_SUMMARY"
          else
            echo "::error file=settings.gradle.kts::settings.gradle.kts is missing although the Kotlin-DSL build exists or the legacy build.gradle is gone. The gate is never skipped after M0."
            exit 1
          fi

      - name: Set up JDK 21
        if: steps.project.outputs.present == 'true'
        uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle (validates every wrapper JAR checksum)
        if: steps.project.outputs.present == 'true'
        uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
        with:
          cache-provider: basic # MIT-licensed provider (D-151)
          cache-read-only: false # PR runs also save, so later pushes to the same PR start warm
          validate-wrappers: true

      - name: Cache Robolectric android-all jars
        if: steps.project.outputs.present == 'true'
        uses: actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0
        with:
          path: ~/.m2/repository/org/robolectric
          key: robolectric-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml', 'app/src/test/resources/robolectric.properties') }}
          restore-keys: robolectric-${{ runner.os }}-

      - name: Android SDK packages (idempotent)
        if: steps.project.outputs.present == 'true'
        run: |
          SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKM" --licenses > /dev/null || true
          "$SDKM" --install "platforms;android-37.0" "build-tools;36.0.0" "build-tools;37.0.0" > /dev/null

      - name: Verify (scripts/verify.sh, identical to local)
        if: steps.project.outputs.present == 'true'
        run: ./scripts/verify.sh

      - name: Upload APKs
        if: ${{ !cancelled() && steps.project.outputs.present == 'true' }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: game2048-apks-${{ env.HEAD_SHA }}
          path: |
            app/build/outputs/apk/debug/*.apk
            app/build/outputs/apk/release/*.apk
            app/build/outputs/mapping/release/mapping.txt
          if-no-files-found: warn
          retention-days: 30
          overwrite: true

      - name: Upload reports
        if: ${{ !cancelled() && steps.project.outputs.present == 'true' }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: game2048-reports-${{ env.HEAD_SHA }}
          path: |
            **/build/reports/
            **/build/test-results/
            app/build/outputs/roborazzi/
          if-no-files-found: ignore
          retention-days: 14
          overwrite: true

      - name: Upload screenshot diffs (failed runs only)
        if: ${{ failure() && steps.project.outputs.present == 'true' }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: game2048-screenshot-diffs-${{ env.HEAD_SHA }}
          path: |
            app/build/outputs/roborazzi/
            app/build/test-results/roborazzi/
            app/build/reports/roborazzi/
          if-no-files-found: ignore
          retention-days: 14
          overwrite: true

      - name: Reproducibility probe (report-only; manual runs)
        if: ${{ github.event_name == 'workflow_dispatch' && steps.project.outputs.present == 'true' && hashFiles('scripts/repro-probe.sh') != '' }}
        continue-on-error: true
        run: ./scripts/repro-probe.sh
```

No expression interpolates untrusted event text into a `run:` script; `HEAD_SHA` is a commit SHA and is used only in artifact names.

### 5.10 Local and CI equivalence

| Aspect | Local | CI |
|---|---|---|
| Gate | `./scripts/verify.sh` | `./scripts/verify.sh` |
| JDK | OpenJDK 21 | Temurin 21 |
| OS | Ubuntu 24.04 | `ubuntu-24.04` |
| Build-tools for scripts | 37.0.0 | 37.0.0 |
| Extra arguments | `VERIFY_GRADLE_ARGS` (mirror, sandbox only) | none |
| Goldens | Recorded here | Verified only; never recorded or committed |

---

## 6. Codex review protocol

Applies to the plan PR and the execution PR (U4). The orchestrator (D-194) performs every step via the GitHub API (MCP tool or gh); §6.8 maps each step. Repository `clayboicardi/2048-android-modern-refresh`; bot `chatgpt-codex-connector[bot]`.

Implementer agents never call GitHub. The milestone integrator implements review fixes and commits them locally; the orchestrator pushes, replies, resolves and merges (D-194). Codex reads the review rules under `## Code Review Rules` in root `AGENTS.md` and `core/engine/AGENTS.md` (D-192), and on GitHub it reports only P0 and P1 findings.

### 6.1 Triggering a round

Post a PR comment whose **entire body** is `@codex review` (§6.8 "Trigger"):
- (a) after the M2 push (mid checkpoint);
- (b) after the M6 push;
- (c) after each fix push in a review loop.

On the plan PR, round 1 is triggered once the PR exists, then (c) applies.

For each trigger, record in the PR body (§6.6): round k, the head SHA, the trigger comment id, `TRIGGER_AT` (the comment's `created_at`), the baseline `+1` counts of the trigger comment and of the PR (from the reaction rollups of §6.8), and the ledger entry for the comment. A round is a trigger on a distinct head SHA; the mid checkpoint and the final loop are counted together. Re-posts on the same SHA do not count as new rounds, and automatic reviews are not counted (D-196).

### 6.2 Polling (D-196)

1. First 👀 check 2 minutes after the trigger, then every 3–5 minutes, using scheduled wake-ups and minimal-output API calls. CI is checked every 5 minutes until `verify` concludes (§6.8 "Check runs on head").
2. Expect a 👀 (`eyes`) reaction from the bot on the trigger comment within 10 minutes. If none appears, note it in the rounds table and keep polling to the next deadline.
3. Expect a result within 30 minutes of the trigger.
4. No result after 30 minutes: re-post `@codex review` once on the same SHA. It stays in the same round and keeps the round's `TRIGGER_AT`; its comment id goes into the ledger.
5. Still nothing 30 minutes after the re-post: stop condition D-172 item 2 (§6.7).

Reviews, PR comments, review threads and reaction counts come from the MCP tools or gh (§6.8). Reactions **with their author** come from the REST reactions endpoints, which this public repository serves without authentication (60 requests per hour per address). Poll each reactions endpoint at most once every 3 minutes (two endpoints: at most 40 requests per hour). Example, printing only the bot's reactions:

```bash
R=clayboicardi/2048-android-modern-refresh
curl -fsS -H 'Accept: application/vnd.github+json' \
  "https://api.github.com/repos/$R/issues/comments/$TRIGGER_ID/reactions?per_page=100" |
  python3 -I -c 'import json, sys
for r in json.load(sys.stdin):
    if r["user"]["login"] == "chatgpt-codex-connector[bot]":
        print(r["content"], r["created_at"])'
```

The same call on `repos/$R/issues/<n>/reactions` reads the reactions on the PR itself.

### 6.3 Clean signal (D-167, D-196)

The head SHA must be unchanged since the trigger, and the signal must come after `TRIGGER_AT`. Any one of these counts:
- (a) a `+1` (👍) by `chatgpt-codex-connector[bot]` on the trigger comment or on the PR, identified through the REST reactions endpoint (§6.2);
- (b) a bot review whose `commit_id` is the head, with no `P0`–`P3` token, that is not an error, usage-limit or status message;
- (c) a bot PR comment that explicitly reports no issues.

A message about a limit or an error, or one saying "unable" or "could not", is no result: keep polling to the deadlines of §6.2. If reaction authorship cannot be read, a `+1` counts only when the trigger comment's `+1` count rose above its recorded baseline and no human reaction is known; otherwise (b) or (c) decides (DECISIONS verification-pending item 12).

If the head changed between the trigger and the signal, the signal does not count; trigger again on the new head.

### 6.4 Handling findings (D-169, D-194)

For each finding of round k, and of any automatic review:
1. **Parse.** A finding's priority is the first `P0`–`P3` token in its body (Codex badges render as `![P1 Badge](…)` or `[P1]`).
2. **Verify the claim** first: reproduce it with a failing test, a grep, or a reading of the cited code against the cited D-ID. Never fix by reflex and never dismiss by reflex.
3. **Fix** a valid finding. The orchestrator passes the finding ids and texts to the milestone integrator. The integrator implements the fix, with a regression test for a behavioural finding, runs verify full (§3.1) and commits `review(r<k>): <summary>` locally (D-164); several findings may share one commit. It hands off per D-194 with one line per finding id (`PLAN.md` §6.10 rule 7). The orchestrator checks the handoff and pushes, then replies in each thread `Fixed in <sha>: <one line>` and resolves the thread (§6.8 "Reply in thread", "Resolve thread").
4. **Decline** an invalid finding, or one that contradicts `DECISIONS.md`: reply with the reason, the D-ID and the evidence (command output, a test name, a research pointer). P0 and P1 are declined only with concrete evidence that the finding is wrong, and they count as resolved only under the rule of §6.5. A finding that is right but contradicts a decision is either declined citing the D-ID, or accepted through an execution-time D-173 entry in the fix commit.
5. **Summary-level items** (in a review body or a PR comment, not inline) are answered with a PR comment that quotes the item (`> [P1] <title>`), followed by the same `Fixed in <sha>: …` or decline line.
6. **Automatic reviews** (D-193): a bot review that no trigger requested is handled the same way. It does not count as a round.
7. **Owner comments** (D-196). The orchestrator posts as the repository owner, so it records the id of every comment, reply and review it posts in the ledger (§6.6). An owner-authored comment, review or reaction that is not in the ledger is a human instruction: the orchestrator pauses the loop at the next safe point and follows it. Owner instructions take precedence over the bot (D-169).
8. After each fix push, trigger the next round (§6.1 c).

Never dismiss, hide or minimize a bot review.

### 6.5 Merge gate and valve (D-168, D-170, D-196)

Merge only when all of these hold for the **same head SHA**:
1. CI `verify` has concluded `success` on it, read via the GitHub API (§6.8 "Check runs on head"). A pending or cancelled run is not green.
2. The latest triggered round on it is clean (§6.3).
3. No P0 or P1 from any round is open. A P0 or P1 is resolved when it is either:
   - fixed: the fix is pushed, the thread has a `Fixed in <sha>: …` reply, and the thread is resolved; or
   - declined with concrete evidence in a reply, and the bot's next result (on a later head, or after a re-trigger on the same head) does not raise it again; the orchestrator then resolves the thread.

   A declined P0/P1 that the bot raises again stays open.
4. Every thread has a reply. Declined P2/P3 threads may stay unresolved.

**Valve** (D-168, U4, unchanged): after 5 rounds, if the latest round raised only new P2 or P3 items, merge once every P0 and P1 from all rounds is resolved and every remaining thread has a reply. Record "valve applied after round k" in the PR body. The valve is expected not to apply because Codex on GitHub reports only P0/P1. An open P0/P1 blocks the merge under the valve too.

**Merge** via the GitHub API with the expected head SHA (§6.8 "Merge"): the execution PR with a merge commit (`merge`), the plan PR squash-merged (`squash`) (D-170). The API refuses the merge if the head moved after the gate was checked. After the merge, follow the `master` push run of `ci.yml` until it concludes and report its result; a red `master` run is reported, not fixed silently.

### 6.6 Bookkeeping in the PR body

The rounds table and the ledger live in the PR body (`PLAN.md` §6.9 template). Before every body update the orchestrator re-reads the live body and keeps owner-added text, the ledger block and the environment-supplied footer verbatim (D-180). The ledger is also kept in `../2048-orchestrator-ledger.txt` beside the checkout.

```markdown
### Review rounds
Automatic review on the M0 head within 30 minutes: no

| Round | Head SHA | Trigger comment id | Trigger time | Result | Follow-up |
|---|---|---|---|---|---|
| 1 (M2 checkpoint) | `abc1234` | 2412345678 | 2026-11-02T10:04:11Z | 2 findings (P1, P1); 👀 at +3 min | `review(r1)` `def5678` |
| 2 | `def5678` | 2412399001 | 2026-11-02T11:20:45Z | clean: 👍 by the bot (trigger `+1` baseline 0) | — |

<!-- orchestrator-ledger:
issue-comment 2412345678 (trigger r1)
review-reply 1834455667 (r1, P1 "Fixed in def5678")
review-reply 1834455702 (r1, P1 "Fixed in def5678")
issue-comment 2412399001 (trigger r2)
-->
```

### 6.7 Stop conditions (D-172, D-196)

Stop autonomous progress, leave the PR open, write the blocker into the PR body's "Blockers" section and report to the user when:
1. no CI run appears after a push with an open PR, after the D-195 dispatch attempt (§6.8 "Dispatch ci.yml"), or a workflow file cannot be pushed;
2. Codex gives no response 30 minutes after the re-post (§6.2);
3. round 10 still raises a P0 or P1 finding: no further pushes; post a summary to the user (the rounds table, the open P0/P1 with evidence, the head SHA and the CI state) and wait for the user. Never merge with an open P0/P1;
4. a decision is technically impossible and no D-173 alternative stays within U1–U7;
5. a push is rejected by push protection (for example over `signing/ci-sideload.p12`).

Otherwise continue without asking. The report names the milestone, the head SHA, what was tried and what the user can do.

### 6.8 GitHub operations

Every GitHub step of `PLAN.md` §6 and of this file is one of these operations (D-195). `{R}` is `clayboicardi/2048-android-modern-refresh` (MCP `owner: clayboicardi`, `repo: 2048-android-modern-refresh`; gh `-R clayboicardi/2048-android-modern-refresh`) and `{n}` is the PR number. PRs use base `master`; nothing targets `uberspot/2048-android`. No step depends on a gh-only command and none needs an admin call: Actions and Codex are already enabled on the fork. When polling, use minimal-output calls and small pages (5–10 items).

| Action | REST/GraphQL resource | GitHub MCP tool | gh |
|---|---|---|---|
| Identity | `GET /user` | `get_me` | `gh api user` |
| Create PR ready for review | `POST /repos/{R}/pulls` | `create_pull_request` (base `master`, head = the branch, `draft: false`) | `gh pr create -R … --base master --head …` |
| Read PR, head SHA, merged state, body | `GET /repos/{R}/pulls/{n}` | `pull_request_read` `get` | `gh pr view -R … --json …` |
| Update body | `PATCH /repos/{R}/pulls/{n}` | `update_pull_request` (`body`) | `gh pr edit -R … --body-file …` |
| PR commits | `GET /repos/{R}/pulls/{n}/commits` | `pull_request_read` `get_commits` | `gh pr view -R … --json commits` |
| Trigger | `POST /repos/{R}/issues/{n}/comments` | `add_issue_comment` | `gh pr comment -R …` |
| PR comments | `GET /repos/{R}/issues/{n}/comments` | `issue_read` `get_comments`, or `pull_request_read` `get_comments` | `gh api` |
| Reviews | `GET /repos/{R}/pulls/{n}/reviews` | `pull_request_read` `get_reviews` | `gh api` |
| Review threads (node ids, resolved state) | GraphQL `reviewThreads` | `pull_request_read` `get_review_comments` | `gh api graphql` |
| Reply in thread | `POST /repos/{R}/pulls/{n}/comments/{id}/replies` | `add_reply_to_pull_request_comment` | `gh api` |
| Resolve thread | GraphQL `resolveReviewThread` | `resolve_review_thread` | `gh api graphql` |
| Reactions with author | `GET /repos/{R}/issues/comments/{id}/reactions` and `GET /repos/{R}/issues/{n}/reactions` | none: unauthenticated REST (`https://api.github.com/…`, ≤ 60 requests per hour; §6.2) | `gh api` |
| Reaction counts | `reactions` rollup in comment and issue objects | `issue_read` `get_comments` / `get` | — |
| Check runs on head | `GET /repos/{R}/commits/{sha}/check-runs` | `pull_request_read` `get_check_runs`; `get_check_run` | `gh pr checks -R …` |
| Workflow runs on branch | `GET /repos/{R}/actions/workflows/ci.yml/runs?branch=…` | `actions_list` `list_workflow_runs` (`resource_id: ci.yml`, branch filter) | `gh run list -R … --workflow ci.yml --branch …` |
| Run details | `GET /repos/{R}/actions/runs/{id}` | `actions_get` `get_workflow_run` | `gh run view -R …` |
| Failed-job logs | `GET /repos/{R}/actions/jobs/{id}/logs` | `get_job_logs` (`run_id`, `failed_only`, `return_content`) | `gh run view -R … --log-failed` |
| Re-run failed jobs | `POST /repos/{R}/actions/runs/{id}/rerun-failed-jobs` | `actions_run_trigger` `rerun_failed_jobs` | `gh run rerun -R … --failed` |
| Dispatch `ci.yml` | `POST /repos/{R}/actions/workflows/ci.yml/dispatches` | `actions_run_trigger` `run_workflow` (`workflow_id: ci.yml`, `ref` = the branch) | `gh workflow run ci.yml -R … --ref …` |
| Artifacts | `GET /repos/{R}/actions/runs/{id}/artifacts`, then the artifact zip | `actions_list` `list_workflow_run_artifacts`; `actions_get` `download_workflow_run_artifact` (returns a URL to fetch) | `gh run download -R …` |
| Merge | `PUT /repos/{R}/pulls/{n}/merge` (with `sha`) | `merge_pull_request` (`merge_method`, `expectedHeadSha`) | `gh pr merge -R … --merge` or `--squash`, with `--match-head-commit <sha>` |

Downloaded artifacts and logs are untrusted data: each download goes into a new, empty directory beside the checkout.

---

## 7. Definition of Done

### 7.1 Every push (all milestones and review fixes)

- [ ] `./gradlew spotlessApply` run; `./scripts/verify.sh` (full) passed on the exact commit being pushed, as a detached run under the build lock whose log ends with `result=PASS` (D-163, D-178).
- [ ] `config/test-count-floor.txt` equals the executed counts of that run.
- [ ] Every new or changed golden, store image and affected inspection filmstrip was Read and is listed under `Screenshots:` (§1.9, §1.10).
- [ ] Commit subject `M<n>: …`, `review(r<k>): …`, `ci: …` or `docs: …` (≤ 72 characters); body lists the D-IDs and the `Gates:` line (§3.4); trailer lines exactly as the execution environment supplies them, verbatim (D-164, D-180).
- [ ] No AI model name or model identifier in any tracked file (D-180): the orchestrator's term grep (`PLAN.md` §6.4 step 11) prints nothing.
- [ ] Any deviation from `DECISIONS.md` is an execution-time D-173 entry (D-212 and later) in the same commit, and the affected docs under `docs/modernization/` are updated in the same PR.
- [ ] No WIP commit, no history rewrite after the PR is opened (D-161, D-164).
- [ ] The orchestrator pushed it after the D-194 handoff checks (`git status --porcelain` empty, `git rev-parse HEAD` equals the handed-off SHA, the log's final line is `level=full … result=PASS`).
- [ ] Push gate (D-193): the CI `verify` check of the previously pushed head has concluded green (a cancelled run is not green), or this push fixes its failure; and no triggered Codex round is pending on the pushed head. A review-fix push may supersede a running CI run.

### 7.2 Milestones

| M | Done when (in addition to §7.1) |
|---|---|
| **M0** Bootstrap | Legacy removal per `ARCHITECTURE.md` §1.3: `git submodule status` prints nothing; `git ls-files \| grep -E '^(src\|res\|metadata\|assets)/'` prints nothing; `.travis.yml` gone. `legacy-credits.md` captured before the submodule was removed. Build layer of `ARCHITECTURE.md` §2 **plus** the D-176 test inputs (§2.6); technical corrections TC-1, TC-2, TC-4 and TC-5 applied. `scripts/verify.sh`, `check-test-counts.sh`, `check-apk.sh` (all 7 assertions), `check-licenses.sh`, `check-metadata.sh` (the `[M0]` functions, including `check_no_bom` and `check_icon_glyphs`) and `config/test-count-floor.txt` (`engine=2`, `app=3`), `config/license-groups.txt`, all committed `100755` where executable. Each new D-179 check function proven once against a seeded defect in a throwaway clone beside the checkout, with the failing line in the M0 commit body; for `check-test-counts.sh` these are seeds S1–S7 of §1.15, including S4 (every capture removed, test methods kept: G1 fails although the floors hold); no seeded defect committed. `testing/Goldens.kt` and `testing/Accessibility.kt` (D-174 opt-in). `LaunchSmokeTest` passes at SDK 24 and 37; `PlaceholderScreenshotTest` records `launch_placeholder.png` with `atfRule` and `assertTouchTargets()`, and the PNG was Read. `core/engine/AGENTS.md` created with `## Code Review Rules` (D-171, D-192); `ci.yml`, root `AGENTS.md` and `CLAUDE.md` unchanged from the plan PR (D-177). Badging shows the package and label. The first push's CI run shows `present=true` and step 7 executed and green (this also proves local == CI goldens, verification-pending item 1). Execution PR opened ready for review by the orchestrator (D-193), with whether an automatic Codex review appeared on the M0 head within 30 minutes recorded in its body |
| **M1** Engine | All §1.2–§1.4 engine classes, the property classes seeded per TC-6; `koverVerify` passes; golden vectors, golden bytes and P1–P13 pass; `EnginePerformanceTest` ≤ 2 s; floors raised |
| **M2** State & persistence | §1.5 fixtures committed through the write-once protocol; §1.6 and §1.7 classes, `ProcessDeathRestoreTest` and `FinalizationRecoveryTest` pass, including `BoardPresenterTest` (with `settledKeepsIdentityAndIsStatic`), `InputBurstOrderingTest`, the `GameViewModelTest` hint tests `hintShownWhileStartingGameHasNoMove` and `hintHiddenAfterAMove`, and `GameViewModelTest.presentationReportsSettleWithoutStateChange` (F07-AC24); the M2 classes of §1.8 pass: `SplashGateTest` (F07-AC11–AC12), `RecreateNoReplayTest` (F07-AC7–AC8), `BackupRulesTest`, `HapticsMapTest.priorityResolution`; `PersistenceBudgetTest` sizes and times recorded in the commit body; **Codex mid-checkpoint round triggered by the orchestrator after the push** (§6.1 a) |
| **M3 part 1** Motion runtime, board, input, game screen shell | The M3 part 1 classes of §1.8 pass, including the board and score motion classes of `MOTION_SPEC.md` §8.5 (`TimelineOriginTest` … `BoardRecompositionBudgetTest`, and the board methods of `ReducedMotionTest`, `MotionOffTest` and `AnimatorScaleTest`); `TileRecompositionTest` and `BoardRecompositionBudgetTest` pass; goldens rows 1, 3, 37–41 and 43 recorded and Read, rows 37–41 checked against `MOTION_SPEC.md` §8.3's frame table; every inspection filmstrip Read once (D-210); `PlaceholderScreenshotTest` and its PNG deleted in this commit, which adds `GameScreenshotTest` |
| **M3 part 2** Overlays, board-size sheet, share, navigation | The M3 part 2 classes of §1.8 pass, including `RecreateNoReplayTest`'s recreation cases F07-AC26–AC30 and the overlay and navigation motion classes (`WinSequenceTest`, `CardExitTest`, `GameOverSequenceTest`, `NavTransitionsTest`, `PredictiveBackTest`, and the overlay methods of `ReducedMotionTest`, `MotionOffTest`, `AnimatorScaleTest` and `HapticsMapTest`); goldens rows 13, 15 and 42 recorded and Read, plus every golden whose bytes changed; the new and changed inspection filmstrips Read (D-210); every `MOTION_SPEC.md` AC except AC-THEME-1, AC-LAYOUT-1, AC-HAP-4 and AC-SCALE-4 passes; the D-181 opt-in is the only `@OptIn` in `app/src/main` |
| **M4** Screens, theming, layout, a11y | `DESIGN_SYSTEM.md` §10.4 rows 1–22 and 27–45 recorded or re-recorded and Read (D-204; the M3 rows change with M4 theming); ATF and `assertTouchTargets()` clean on every screen-level golden (D-204, D-205); `PaletteContrastTest` passes with the unquantized method (D-205); `ThemeCrossfadeTest` (AC-THEME-1) and `LayoutMotionTest` (AC-LAYOUT-1, AC-LAYOUT-2 with F18-AC13) pass; `SettingsScreenTest.switchesFireToggleHaptics` (F14-AC8, AC-HAP-4) and `SettingsScreenTest.motionRowDisabledAtSystemScaleZero` (AC-SCALE-4) pass; the other M4 classes and methods of §1.8 pass; `check_font` added (or the D-076 fallback recorded) |
| **M5** Localization & identity | `docs/modernization/i18n-glossary.md` committed before any translation (D-105); all 20 locales complete (`lintDebug` clean of `MissingTranslation`, `ExtraTranslation` and `Untranslatable`); `TranslationQualityTest` and `LocalizationTest` pass, and `FormattingTest.toLatinDigitsMapsArabicIndicAndPersian` (L-AC5); rows 23–26 recorded and Read; store assets generated twice with `-PrecordStoreAssets=true` (byte-identical) and each Read; `check_store_listing`, `check_store_images`, `check_legal_and_privacy`, `check_legal_texts` and `check_strings_final` added and passing (D-190); `strings_pending.xml` gone (D-208) |
| **M6** Hardening & close-out | Every D-148, D-152, D-179 and D-190 assertion active; `scripts/repro-probe.sh` added, run locally, result in the PR body (D-191); suppression inventory (§2.5) in the PR body; `PLAN.md` §2.7 naming check clean; measured CI durations recorded; final Codex loop (§6) |

### 7.3 The plan PR

- [ ] Only `docs/modernization/**`, `.github/workflows/ci.yml`, `AGENTS.md` and `CLAUDE.md` change; no app code (D-160 as clarified by D-177).
- [ ] `ci.yml` parses (`python3 -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))' .github/workflows/ci.yml`).
- [ ] The push containing `ci.yml` succeeded, and a `ci.yml` run on the branch appeared within 5 minutes, checked via the GitHub API (D-165, §6.8); otherwise D-172 item 1.
- [ ] CI `verify` is green with the "plan-only tree" notice, read via the GitHub API.
- [ ] Codex clean on the head SHA (or the valve), no open P0/P1, every thread answered (§6).
- [ ] The plan PR's Codex loop settles DECISIONS verification items 11 and 12; record the outcome in the plan PR conversation.
- [ ] Squash-merged via the GitHub API with the expected head SHA (D-170, D-195).

### 7.4 The execution PR (merge)

- [ ] M0–M6 done (§7.2); the PR body has the M0–M6 checklist with SHAs, the `master`-is-default line, "Deviations from DECISIONS.md" reading `None, or entries added during execution (D-212 and later)`, the screenshot list, the latest `verify.sh` summary, the CI artifact names, the review-rounds table, the ledger and blockers (D-165, `PLAN.md` §6.9).
- [ ] CI `verify` green on the head SHA, read via the GitHub API; the `game2048-apks-<head-sha>` artifact contains both APKs and `mapping.txt`.
- [ ] `check-apk.sh` output on the head: release size, the one permission, the exported set, the signer digest.
- [ ] Latest triggered Codex round on the head is clean, or the valve applies (§6.5); every thread has a reply.
- [ ] No open P0/P1 (D-196).
- [ ] Merged via the GitHub API with the expected head SHA, as a merge commit (D-170, D-195).

---

## Open issues

None open. Resolved during planning: Q-OI-1 → D-174; Q-OI-2 → D-176; Q-OI-3 → D-177; Q-OI-4 → D-178; Q-OI-5 → D-179, D-190, TC-2; Q-OI-6 → D-191; Q-OI-7 → the canonical names (`PLAN.md` §2); Q-OI-8 → D-205; Q-OI-9 → verification items resolved (DECISIONS verification-pending items 5 and 10; items 1 and 2 stay open there); Q-OI-10, Q-OI-11, Q-OI-13 → adopted as written in §1.6 and §5.4; Q-OI-12 → D-204.
