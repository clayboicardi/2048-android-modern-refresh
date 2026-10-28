# 06 — Quality + CI research (as of 2026-10-28)

Track: quality-ci. Scope: test pyramid, static analysis, GitHub Actions, release hygiene, AGENTS.md / Codex review, and per-milestone verification gates for the native Kotlin + Compose + M3 rewrite (`com.clayboicardi.game2048`, offline, zero runtime permissions).

Versions were checked against primary metadata (Google Maven `dl.google.com/dl/android/maven2/.../maven-metadata.xml`, Maven Central `repo1.maven.org`, Gradle Plugin Portal `plugins.gradle.org/m2`, `git ls-remote` against GitHub repos, tagged source files). Sources are in §9. This doc lines up with `03-toolchain.md` (AGP 9.4.1, Kotlin 2.4.21, compileSdk/targetSdk 37, minSdk 24, JDK 21, modules `:app` + `:core:engine`). Where this doc corrects 03, it says so explicitly.

---

## 0. TL;DR — decisions

| Area | Pick | Why (1 line) |
|---|---|---|
| Engine tests (`:core:engine`, pure JVM) | JUnit Jupiter 6.1.3 + **kotest-property 6.2.6** + golden tables, injected app-owned PRNG | Fast, no Android. Exhaustive invariants (§1.1). |
| Coverage | **Kover 0.9.11** on `:core:engine`, `koverVerify` ≥ 90 % line / ≥ 85 % branch | Engine is the critical logic. UI is covered by behaviour and screenshot tests instead. |
| State-holder tests | kotlinx-coroutines-test **1.11.0** + **Turbine 1.2.1**, JUnit 4.13.2 | Standard. Compose v2 test APIs also use `StandardTestDispatcher`. |
| Compose UI tests | **Robolectric 4.17** (supports SDK 23→37; JDK 21 for 36/37) + Compose **ui-test v2** (`androidx.compose.ui.test.junit4.v2.createComposeRule`) | JVM-only, so CI needs no emulator. |
| Screenshot tests | **Roborazzi 1.77.0** (2026-10-22) | Paparazzi stable is 1.3.5 (2024-11), and AGP 9 support exists only in the 2.0 alphas. Google's standalone plugin is "no longer maintained", and its replacement needs AGP ≥ 9.5.0-alpha03. |
| A11y checks | `roborazzi-accessibility-check` 1.77.0 (ATF 4.1.1) on JVM. Optional `ui-test-junit4-accessibility` 1.12.1 for instrumented runs. | Runs inside the same Robolectric tests. |
| Instrumented / GMD | **Not** a PR gate. Optional `workflow_dispatch` job using KVM, which standard Linux runners support. | Slow: an image download and emulator boot add about 5–10 min. |
| Macrobenchmark / baseline profile | **Manual / deferred** (benchmark + `androidx.baselineprofile` 1.5.0) | Needs a device. `baseline.prof` is a known F-Droid reproducibility hazard. |
| Formatter | **Spotless 8.10.4 + ktfmt 0.65** (`org.jetbrains.kotlinx:ktfmt`, kotlinlang style) | 0.65 is Spotless 8.10.4's built-in default (verified in `spotless-lib-4.10.4.jar`) and embeds Kotlin compiler 2.4.10. 0.64, which 03 smoke-tested, embeds 2.3.20. |
| Lint | **AGP Lint 32.4.1** with `warningsAsErrors`, no baseline, plus **Slack `compose-lint-checks` 1.6.1** (built against lint 32.4.1) | Stable, and its lint API matches AGP 9.4.1. Compose rules live here, not in detekt. |
| detekt | **`dev.detekt` 2.0.0-alpha.6**: generic Kotlin rules only, no compose-rules (to avoid double-reporting) | detekt 1.23.8 cannot handle Kotlin 2.4 or AGP 9. The 2.0 alpha is built against Kotlin 2.4.10 + AGP 9.3.1 and was smoke-verified in 03. If it breaks, drop it (§2.4). |
| CI runner | **`ubuntu-24.04` (pinned)**, not `ubuntu-latest` | `ubuntu-latest` moves to Ubuntu 26.04 in **November 2026** (runner-images #14748). The local sandbox is Ubuntu 24.04.5 / glibc 2.39 / 4 CPU / 15 GB, matching the runner, so locally recorded screenshot references verify bit-exactly. |
| Actions | `actions/checkout@v7.0.1`, `actions/setup-java@v6.1.0`, `gradle/actions/setup-gradle@v6.4.0` (`cache-provider: basic`), `actions/cache@v6.1.0`, `actions/upload-artifact@v7.0.2`, all pinned by SHA (§3.1) | Latest majors as of 2026-10. |
| Default branch | **`master`** (verified: `git branch -a` → `remotes/origin/master`; there is no `main`) | Workflows, cache scoping and the user's "merge to main" all mean `master`. |
| Codex review | Add a **`## Code Review Rules`** section to the root `AGENTS.md` (current doc wording; older docs said "Review guidelines"). Trigger with a PR comment `@codex review`. Codex reacts 👀 while working and flags only P0/P1. | §5 |

---

## 1. Test pyramid

```
            ┌──────────────────────────────┐  manual / workflow_dispatch only
            │ GMD instrumented smoke,      │  (KVM on ubuntu-24.04; ~15–25 min)
            │ macrobenchmark, baseline prof│
            ├──────────────────────────────┤
            │ Roborazzi screenshots + ATF  │  JVM (Robolectric 4.17, SDK 36), every PR
            ├──────────────────────────────┤
            │ Compose UI behaviour tests   │  JVM (Robolectric, ui-test v2), every PR
            ├──────────────────────────────┤
            │ ViewModel / repository tests │  JVM (coroutines-test, Turbine, real DataStore on tmp files)
            ├──────────────────────────────┤
            │ Engine: unit + golden +      │  pure JVM, JUnit 6 + kotest-property, Kover ≥ 90 %
            │ property-based + replay      │
            └──────────────────────────────┘
```

### 1.1 Engine (`:core:engine`) — pure JVM

**Libraries:** `org.junit:junit-bom:6.1.3` (`junit-jupiter`, `junit-platform-launcher`), `io.kotest:kotest-property:6.2.6` (KMP root; Gradle resolves `kotest-property-jvm`), `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0` (only if `checkAll` runs inside `runTest`), `kotlinx-serialization-json:1.12.0` for round-trip tests. Use `tasks.test { useJUnitPlatform() }`.

> Gotcha (hit in 03's smoke build): in JUnit Jupiter, `@Test fun x() = runBlocking { checkAll(...) {} }` returns non-Unit and is **silently not run**. Use block bodies (`@Test fun x() { runBlocking { … } }`) or `= runTest { … }`. Add a detekt/Lint-independent guard: the CI step prints the test count (`build/test-results/test/*.xml`), and the gate checks that the count is > 0 and does not drop (§6).

**RNG injection design (required for determinism):**
- `fun interface RandomSource { fun nextInt(until: Int): Int }`. Production uses an **app-owned PRNG** (e.g. SplitMix64 or xoshiro128** implemented in the engine) seeded per game. Features track §1: do not use `kotlin.random.Random(seed)`, because its algorithm may change between Kotlin versions, which would break replays and daily seeds.
- The engine **must not** read the clock, `Math.random`, `Random.Default` or Android APIs. Enforce this with an AGENTS.md review rule and a cheap test that greps the engine sources for those imports and fails if any are found.
- **PRNG golden test:** for seed `0x2048`, the first 32 outputs equal a committed array. If the algorithm drifts, this test catches it before saved games or replays break.

**Golden vectors (ported from the legacy JS semantics):** traversal goes farthest-first in the move direction (`assets/2048/js/game_manager.js:253-266`). A tile merges at most once per move via `mergedFrom` (`:202-204`). Score `+= merged.value` (`:213`). Win on the first 2048 (`:216`). A spawn happens only if the board moved (`:228-229`): 2 at 90 %, 4 at 10 % (`:115`), in a uniform empty cell (`grid.js:37-43`). Game over is checked only after a move that changed the board (`:231-233`). Row-left cases (mirror them for the other directions):

| Input row (→ left) | Output | Score Δ | moved |
|---|---|---|---|
| `[2,2,2,2]` | `[4,4,0,0]` | +8 | yes |
| `[2,2,4,0]` | `[4,4,0,0]` (**not** `[8,0,0,0]`) | +4 | yes |
| `[4,4,8,0]` | `[8,8,0,0]` | +8 | yes |
| `[8,8,8,0]` | `[16,8,0,0]` (the pair nearest the wall merges first) | +16 | yes |
| `[2,0,2,4]` | `[4,4,0,0]` | +4 | yes |
| `[2,2,0,2]` | `[4,2,0,0]` | +4 | yes |
| `[0,0,0,2]` | `[2,0,0,0]` | 0 | yes |
| `[2,4,8,16]` | unchanged | 0 | **no** → no spawn, no RNG draw, no undo entry |
| `[1024,1024,0,0]` | `[2048,0,0,0]` | +2048 | yes, `won = true` (once) |
| Right: `[2,2,2,0]` | `[0,0,2,4]` | +4 | yes |

**Property-based invariants (kotest `checkAll`).** Use a board `Arb` with size ∈ 3..8 (features: 3×3–8×8) and cells ∈ {0, 2¹…2¹⁷}, weighted toward 0. Pin `PropTestConfig(seed = …)` for CI reproducibility. Run 1 000 iterations per property locally and in CI. A failure prints its seed; copy it into a regression test.
1. **Sum conservation (pre-spawn):** `sum(slide(b,d).board) == sum(b)`. **Post-spawn:** `sum(after) − sum(b) ∈ {0}` if not moved, else `∈ {2,4}`.
2. **No double merge:** each output tile is made from ≤ 2 input tiles, so the output tile count is ≥ ⌈input tile count / 2⌉ per line. Check equivalently against a reference line-slide oracle written in the simplest possible style (list filter, then pairwise merge).
3. **No-op idempotence:** if `!result.moved`, then `result.state == state` structurally: score, undo stack, move counter and RNG cursor are all unchanged, and `rng` was **not** called (use a counting `RandomSource`).
4. **Score delta = Σ merged values**, and score never decreases except through undo.
5. **Tile count:** `count(after_slide) = count(before) − merges`. A spawn adds exactly 1 when moved.
6. **Max tile is monotonic** (without undo).
7. **Rotation/reflection metamorphic:** `slide(rot(b), rot(d)) == rot(slide(b, d))` for all 4 rotations and transposition. This is very strong and catches direction-mapping bugs.
8. **Game over ⇔** no empty cell and no orthogonally-adjacent equal pair ⇔ all 4 directions are no-ops.
9. **Win/keep-playing:** `won` turns true exactly when the first 2048 tile is created. After "continue", reaching 2048 again does not re-trigger.
10. **Determinism/replay:** the same seed plus the same move list gives an identical final state and score.
11. **Undo/redo (features: unlimited, works on game-over):** `undo(move(s)) == s` when moved. `redo(undo(s′)) == s′`. Undo of the game-over move restores a playable state.
12. **Serialization round-trip:** `decode(encode(s)) == s` for arbitrary states, including long histories (e.g. 20 000 moves on 8×8), with a time budget asserted loosely (e.g. < 1 s on CI) so that unlimited undo cannot silently make every save O(n²).
13. **Spawn distribution (statistical, fixed seed):** over 100 000 spawns, the frequency of 4 is in [0.095, 0.105], and each empty cell is chosen within ±10 % of uniform.

**Coverage gate (Kover 0.9.11, plugin `org.jetbrains.kotlinx.kover` on the Plugin Portal):**
```kotlin
// core/engine/build.gradle.kts
plugins { alias(libs.plugins.kover) }
kover {
    reports {
        verify {
            rule { minBound(90) }                                   // line coverage %
            rule("branches") {
                bound { minValue = 85; coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH }
            }
        }
    }
}
```
Tasks: `:core:engine:koverVerify`, `:core:engine:koverHtmlReport`. Kover 0.9.9 fixed a Gradle 9.6.0 deprecation, 0.9.10 fixed reporter concurrency, and 0.9.11 (2026-09-29) reverted a 0.9.10 change. (`minBound(Int)` is in the Kover docs; the field names inside `bound {}` should be confirmed against the 0.9.11 DSL at first use.)

### 1.2 State holders (`:app` JVM tests, no Robolectric needed)

- `kotlinx-coroutines-test:1.11.0`. A `MainDispatcherRule` (JUnit 4 `TestWatcher`) calls `Dispatchers.setMain(StandardTestDispatcher())`.
- `app.cash.turbine:turbine:1.2.1`: `vm.uiState.test { awaitItem(); vm.onSwipe(LEFT); val s = awaitItem(); … cancelAndIgnoreRemainingEvents() }`. `StateFlow` conflates and is distinct-until-changed, so assert on transitions, not on emission counts.
- **Persistence:** test the real DataStore 1.3.0 on JVM, not a fake. Use `DataStoreFactory.create(serializer = GameStateSerializer, scope = backgroundScope, produceFile = { tmp.newFile("game.json") })` (JUnit 4 `TemporaryFolder`, or `@TempDir` in Jupiter). Cases:
  1. Round-trip.
  2. **Corrupt file** → `ReplaceFileCorruptionHandler` yields defaults and does not crash.
  3. **Old-schema JSON** (a committed fixture `v1-game.json`) decodes after new fields are added. Use `Json { ignoreUnknownKeys = true; explicitNulls = false }` and give every new field a default.
  4. Best score never decreases.
  5. Process-death restore via `SavedStateHandle` for transient UI state.
- Input during animation: a test that fires 3 swipes in quick succession sees all 3 applied in order to the latest state. No input may be lost or applied to a stale board.

### 1.3 Compose UI behaviour tests on the JVM (Robolectric)

- `org.robolectric:robolectric:4.17` (2026-09-10). The release notes say "supports SDK 37". 4.16 notes: "you need to use JDK 21 if running tests with SDK 36 target". The SDK→jar map in the 4.17 source (`DefaultSdkProvider.java`) is: SDK 36 → `org.robolectric:android-all-instrumented:16-robolectric-13921718-i7` (213 MB), SDK 37 (`CINNAMON_BUN`) → `17-robolectric-15733970-i7` (239 MB). Both need Java 21.
- **Which SDK to emulate:** Robolectric defaults to `targetSdk` (37). Recommendation: pin **`sdk=36`** for the bulk of tests and all screenshots, in `app/src/test/resources/robolectric.properties` (`sdk=36`). SDK 36 support has matured across 4.16 and 4.17, and 37 is new in 4.17. Add one `LaunchSmokeTest` with `@Config(sdk = [24, 37])` for minSdk and targetSdk. Robolectric 4.16 dropped 21/22, and its floor is 23, so minSdk 24 is testable. (03 smoke-ran a screenshot at SDK 37 successfully. Either works; pinning is what matters for stable screenshots.)
- **Required config (from 03's smoke build):** JDK 21 `--add-opens` jvmArgs, and **force `androidx.test.espresso:espresso-core:3.7.0`**. ui-test-junit4 1.12.1 pulls in 3.5.1, which crashes on SDK 37 with `InputManager.getInstance()`.
- Compose test API: **v2 only** (`androidx.compose.ui.test.junit4.v2.createComposeRule()`). v1 is deprecated. v2 uses `StandardTestDispatcher`, so call `waitForIdle()` or `mainClock.advanceTimeBy(…)` explicitly. Source: Android "Migrate to v2 testing APIs" (Compose ui-test 1.11.0-alpha03+).
- Dependencies: `testImplementation(platform(libs.compose.bom))`, `testImplementation("androidx.compose.ui:ui-test-junit4")` (1.12.1 via BOM 2026.09.00), `debugImplementation("androidx.compose.ui:ui-test-manifest")`, `testImplementation("androidx.test.ext:junit:1.3.0")`, `testImplementation("androidx.test:core:1.7.0")`.
- `android.testOptions.unitTests { isIncludeAndroidResources = true; all { it.maxHeapSize = "3g"; it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") } }`. The `pixelCopyRenderMode` setting is the Roborazzi-recommended accuracy setting.
- Typical behaviour tests: swipe on the board → score node text; keyboard (arrows/WASD/HJKL/D-pad) → move; undo/redo buttons enabled state; win dialog → continue; game-over → undo works; settings toggles persist; RTL locale mirrors layout but **not** swipe semantics (decide and test explicitly); font scale 2.0 does not clip the score.

### 1.4 Screenshot testing — comparison and choice

| | **Roborazzi 1.77.0** | Paparazzi | Compose Preview Screenshot Testing (Google) |
|---|---|---|---|
| Latest | 1.77.0, 2026-10-22. Monthly cadence (1.74 → 1.77 in Sep–Oct 2026) | Stable **1.3.5 (2024-11-06)**. **2.0.0-alpha05.1 (2026-09-28)** | Standalone plugin `com.android.compose.screenshot` **0.0.1-alpha16**, marked "**no longer maintained**". Replacement is AGP test suites (`screenshotTests.create(...)`) and needs **AGP 9.5.0-alpha03+** plus `android.experimental.enableScreenshotTest=true` and `android.experimental.testSuiteSupport=true`, documented as "Experimental" |
| Engine | Robolectric (real framework from android-all + native graphics) | layoutlib (Studio renderer) | layoutlib |
| AGP 9 | Supported since **1.56.0 (2026-01)** (commit "Add AGP 9.0 compatibility to RoborazziPlugin"). Built against AGP 8.13.2 for backward compatibility. The configuration-cache fix shipped in 1.77.0 | Only 2.0.0-alpha05.1 ("[Gradle Plugin] Android Gradle Plugin 9.0.0"). Needs Java 21 (since alpha04) | Not on stable AGP 9.4.1. AGP 9.5 is rc02 as of 2026-10-26 |
| Interactions | Yes: capture after clicks/swipes with the Compose test rule, ATF a11y checks, Preview scanning (ComposablePreviewScanner) | Static snapshots, accessibility snapshot. Its goldens for a11y and scroll modes changed in alpha05.1 | `@PreviewTest` previews only |
| CI | Linux is fine. Record and verify on the same OS (FAQ: macOS/Ubuntu/Windows differ) | Same | Same |
| Verdict | **Use** | Alpha-only for our AGP; skip | Not viable until AGP 9.5 is stable; revisit later |

**Roborazzi setup:**
```kotlin
// app/build.gradle.kts
plugins { alias(libs.plugins.roborazzi) }          // id io.github.takahirom.roborazzi (marker on Maven Central)
roborazzi { outputDir.set(file("src/test/screenshots")) }   // reference PNGs committed to git
dependencies {
  testImplementation(libs.roborazzi)                // io.github.takahirom.roborazzi:roborazzi:1.77.0
  testImplementation(libs.roborazzi.compose)        // …:roborazzi-compose:1.77.0
  testImplementation(libs.roborazzi.junit.rule)     // …:roborazzi-junit-rule:1.77.0
  testImplementation(libs.roborazzi.accessibility.check) // …:roborazzi-accessibility-check:1.77.0 (ATF 4.1.1)
}
```
`gradle.properties`: `roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory`, so `captureRoboImage("board_mid_light.png")` lands in `src/test/screenshots/`. Optional: `roborazzi.cleanupOldScreenshots=true` when recording.

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)   // "w411dp-h914dp-…-420dpi…"
class BoardScreenshotTest {
  @Test fun midGame_light() = captureRoboImage("board_mid_light.png") {
    GameTheme(darkTheme = false, dynamicColor = false) { GameScreen(state = Fixtures.midGame, onEvent = {}) }
  }
}
```
- **Determinism rules:** `dynamicColor = false` in tests. No downloadable/Google Fonts: bundle fonts in `res/font` (Google Fonts also needs a GMS provider and the network). Static fixture states. Animations disabled or settled (`mainClock.autoAdvance = false` + explicit advance, or an `animationsEnabled` parameter). Fixed `@Config(sdk=[36])`.
- **Comparison threshold:** the Roborazzi default `ThresholdValidator(0F)` is an exact match (`RoborazziOptions.common.kt:153`). Keep it at 0 because local and CI are the same OS. If CI shows sub-pixel noise, switch to a shared `RoborazziOptions(compareOptions = CompareOptions(changeThreshold = 0.001F))`.
- **Matrix (target about 24–30 PNGs, roughly 3–6 MB in git):** {new game, mid game 4×4, 8×8 dense, win dialog, game over, settings, stats} × {light, dark} + {`ar` RTL, `de` long strings} + {`fontScale` 2.0} + {`SmallPhone` w360dp, `MediumTablet` w1280dp-h800dp landscape}. Device constants exist in `RobolectricDeviceQualifiers`.
- **Tasks:** record `./gradlew :app:recordRoborazziDebug`. Verify `./gradlew :app:verifyRoborazziDebug`, or `:app:testDebugUnitTest -Proborazzi.test.verify=true` (one test pass that also runs the behaviour tests). Compare `./gradlew :app:compareRoborazziDebug` writes `*_compare.png` and JSON under `build/outputs/roborazzi` and `build/test-results/roborazzi`. Report: `app/build/reports/roborazzi/index.html`.
- **Record/verify workflow for the agent:** record locally (Ubuntu 24.04 x86_64 = CI) and commit the PNGs with a PR-body note listing changed images. CI only verifies. Do **not** auto-commit from CI: commits pushed with `GITHUB_TOKEN` do not trigger new workflow runs. Fallback if CI disagrees with local: download the `reports` artifact (actual images) and commit those.

### 1.5 Accessibility checks

- **JVM (every PR):** Roborazzi ATF. `RoborazziRule(options = Options(roborazziAccessibilityOptions = RoborazziATFAccessibilityCheckOptions(checker = RoborazziATFAccessibilityChecker(preset = AccessibilityCheckPreset.LATEST), failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error), accessibilityCheckStrategy = AccessibilityCheckAfterTestStrategy()))`, or per-node `onNodeWithTag("board").checkRoboAccessibility(...)`. It catches missing labels (`SpeakableTextPresentCheck`), `TextContrastCheck` (it reports the ratio; tiles need ≥ 4.5:1), and touch target size.
- **Compose-native ATF:** `androidx.compose.ui:ui-test-junit4-accessibility` 1.12.1 (in BOM 2026.09.00), with `composeTestRule.enableAccessibilityChecks()` and `onRoot().tryPerformAccessibilityChecks()`. Available since Compose 1.8.0. The docs show it with `createAndroidComposeRule` and do not state Robolectric support. Use it in the optional instrumented job only.
- Semantics conventions to test: each tile has a `contentDescription` such as "2048, row 2, column 3". The board has a `stateDescription` announcing the score. Moves are announced via `LiveRegion.Polite` on the score. Custom accessibility actions "Move up/down/left/right" exist on the board, so TalkBack users can play without swipes.

### 1.6 Instrumented tests / Gradle Managed Devices (GMD)

- **KVM on GitHub-hosted Linux:** available on standard runners. The GitHub changelog of 2024-04-02 says 2-vCPU Linux runners got hardware acceleration ("previously … only available on runners with 4 or more vCPUs"). Public repos get 4 vCPU / 16 GB / 14 GB SSD on `ubuntu-24.04` (docs: GitHub-hosted runners reference). The runner user must be granted the kvm group first:
  ```bash
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
  sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
  ```
- **System images available (sdk repo XML, 2026-10-28):** `system-images;android-36;default;x86_64`, `…;android-37.0;default;x86_64`, `…;android-36;aosp_atd;x86_64`, `…;android-37.0;aosp_atd;x86_64` (ATD exists for 33→37.0, although the GMD doc page still says "ATDs currently support only API level 30").
- GMD DSL (AGP docs): `testOptions { managedDevices { localDevices { create("pixel6Api36") { device = "Pixel 6"; apiLevel = 36; systemImageSource = "aosp-atd" } } } }`. Run with `./gradlew :app:pixel6Api36DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect`; the docs require the swiftshader flag on GitHub Actions. ATD caveat: "Screenshot tests that depend on hardware rendering currently aren't supported".
- **Cost/time:** free for public repos on standard runners. Estimate (not measured): image download about 1 GB, plus boot and test, so **15–25 min per run**. Hence a `workflow_dispatch` + weekly schedule only, not a PR gate. Alternative runner action: `reactivecircus/android-emulator-runner@v2.38.0` (2026-07-05). Its README recommends ubuntu runners plus the KVM udev step and AVD snapshot caching with `actions/cache@v5+`.
- Instrumented scope (small): a launch smoke test on the **release-like `benchmark` build type** (minified, debug-signed), which is the only place R8 keep-rule mistakes surface before users. Also Compose ATF via `enableAccessibilityChecks()` and process-death restore (`ActivityScenario.recreate()`).

### 1.7 Macrobenchmark + baseline profiles

- Versions: `androidx.benchmark:benchmark-macro-junit4` **1.5.0**, plugin `androidx.baselineprofile` **1.5.0** (2026-09-09; 1.6.0-alpha01 exists), `androidx.profileinstaller` **1.4.1**, `androidx.test.uiautomator` **2.4.0**.
- Needs a `com.android.test` module (`:baselineprofile`) and a device. On GMD in CI it is feasible with KVM, but emulators yield noisy timing (`androidx.benchmark.suppressErrors=EMULATOR`).
- **Recommendation:** defer it, and document it as a manual milestone (M6). Compose, runtime and others already ship library baseline profiles, which AGP merges into `assets/dexopt/baseline.prof`. 03 saw 4 527 B in a smoke release. F-Droid lists "baseline.prof not deterministic" and "baseline.profm not deterministic" among reproducibility bugs, and a custom profile adds surface area. Re-evaluate after M5's reproducibility check (§4.4).

---

## 2. Static analysis

### 2.1 Android Lint (AGP 9.4.1 → lint 32.4.1)

```kotlin
// app/build.gradle.kts
android {
  lint {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = true
    checkDependencies = true
    // Time-/network-dependent checks would start failing CI on their own when new versions ship. Never gate on them.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    // New UI strings land before translations. ExtraTranslation (fatal) still catches stale keys.
    informational += setOf("MissingTranslation")
    sarifReport = true
    // NO baseline: a fresh rewrite starts at zero findings. If one is ever needed:
    // baseline = file("lint-baseline.xml") and the PR must explain it (AGENTS.md rule).
  }
}
dependencies { lintChecks("com.slack.lint.compose:compose-lint-checks:1.6.1") }
```
- AGP 9.4: `lint { textReport = … }` is deprecated ("Lint reports are now always generated"; 03 §1.10).
- Pre-empt known noise (03 §9): `UnusedAttribute` for `android:appCategory` with minSdk 24 → add `tools:targetApi="26"`. `MissingApplicationIcon` until the adaptive icon exists. Legacy strings `press_back_again_to_exit` and `toggle_fullscreen` (`res/values*/strings.xml`, 4 strings × 20 locales) become `UnusedResources` errors, so delete them with the WebView.
- `:core:engine` is treated by Lint as an external dependency and is not analysed. Optionally apply `com.android.lint` (same version as AGP) to the JVM module.
- Slack `compose-lint-checks` 1.6.1 (2026-10-07): "Build against lint `32.4.1`", Kotlin 2.4.20. It includes `ComposeModifierMissing` (03 saw it fire as an error), `ComposeModifierReused`, `ComposeParameterOrder`, `ComposeNonRestartableComposable` (new in 1.6.0) and others.

### 2.2 Kotlin compiler

`kotlin { compilerOptions { allWarningsAsErrors = true } }` in both modules. This also forces the v2 Compose test APIs, because the deprecated v1 APIs would fail the build.

### 2.3 Formatting — Spotless 8.10.4 + ktfmt 0.65

```kotlin
// root build.gradle.kts
plugins { alias(libs.plugins.spotless) }   // com.diffplug.spotless 8.10.4 (Maven Central + Plugin Portal)
spotless {
  kotlin { target("**/*.kt"); targetExclude("**/build/**"); ktfmt("0.65").kotlinlangStyle() }
  kotlinGradle { target("**/*.gradle.kts"); targetExclude("**/build/**"); ktfmt("0.65").kotlinlangStyle() }
}
```
- Spotless `plugin-gradle` 8.10.4 (2026-10-08) changelog: "Bump default `ktfmt` version `0.64` -> `0.65` … moved to the `org.jetbrains.kotlinx:ktfmt` coordinate". Confirmed in bytecode: `spotless-lib-4.10.4.jar` `KtfmtStep.class` contains `0.65`, `org.jetbrains.kotlinx:ktfmt:` and `com.facebook:ktfmt:`. **This corrects 03**, which said 0.65 is "not yet a Spotless 8.10.4 default".
- ktfmt 0.65 (2026-10-07) embeds `kotlin-compiler` **2.4.10**; 0.64 embeds 2.3.20. Fallback: `ktfmt("0.64")`, which 03 smoke-verified as idempotent.
- ktfmt kotlinlang style wraps at **100 columns**. Set detekt `MaxLineLength` to 100 to match, or disable it because the formatter owns layout.
- Alternative if ktfmt is rejected: ktlint 1.8.0 (2025-12-15, embeds Kotlin 2.2.21) + `io.nlopez.compose.rules:ktlint:0.6.8`. Not recommended: its Kotlin parser is older.

### 2.4 detekt 2.0.0-alpha.6 (optional but recommended gate)

- Plugin id `dev.detekt` (renamed from `io.gitlab.arturbosch.detekt`; artifacts in group `dev.detekt`), version **2.0.0-alpha.6** (2026-08-04). The changelog says: "Detekt 2.0.0 is still in alpha. While it's stable enough to use in your projects, you can expect further breaking changes". Built against Kotlin 2.4.10 / AGP 9.3.1 (`libs.versions.toml` at the tag). The migration guide says "detekt itself is built and tested against the latest JDK 25, Kotlin 2.4, Gradle 9.x and AGP 9.x" and "works with both the standalone Kotlin Gradle Plugin and AGP 9's built-in Kotlin support". DGP declares configuration-cache compatibility (alpha.4, #9311).
- detekt **1.23.8 must not be used** (built for Kotlin 2.0.21 / AGP 8).
- Tasks with type resolution: `detektMain` (Android: all production source sets) and `detektTest`. Report renames: `xml` → `checkstyle`, `md` → `markdown`.
- Config: `detekt { buildUponDefaultConfig = true; config.setFrom("config/detekt/detekt.yml"); parallel = true }`. Generate the starting file with `./gradlew detektGenerateConfig` and edit it. Do not hand-write rule keys: 2.0 renamed rules, and detekt validates config keys. Tuning that 03's smoke build showed is needed: `FunctionNaming: ignoreAnnotated: ['Composable']`; `MagicNumber` excluded for `**/ui/theme/**` and tests (colour, dp and duration literals); `LongParameterList`/`LongMethod` `ignoreAnnotated: ['Composable']`; unused-private rules `ignoreAnnotated: ['Preview']`; `MaxLineLength: 100`.
- **Do not add** `io.nlopez.compose.rules:detekt:0.6.8` (it targets detekt 2.0.0-alpha.6 / Kotlin 2.4.10 per its support matrix). Slack compose-lints already runs the same rule family inside Lint, and two tools reporting each finding doubles the fix churn. If Lint-based Compose rules are ever removed, swap in compose-rules here.
- **Exit criterion:** if `detektMain` fails for tool reasons (crash, not findings) on AGP 9.4.1 / Kotlin 2.4.21 and isn't fixed within one attempt, remove detekt from the gate and note it in the PR. Lint + Spotless + `allWarningsAsErrors` remain.
- No baseline file at the start (fresh code). Same AGENTS.md rule as Lint.

---

## 3. GitHub Actions

### 3.1 Action versions (git tags, 2026-10-28) and pinned SHAs

| Action | Latest tag (date) | Commit SHA to pin |
|---|---|---|
| `actions/checkout` | **v7.0.1** (2026-07-17; v7 majors exist) | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | **v6.1.0** (2026-10-14) | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` |
| `gradle/actions` (`setup-gradle`, `dependency-submission`, `wrapper-validation`) | **v6.4.0** (commit 2026-09-07, annotated tag 2026-09-28) | `3f5f9adaf7d9fecd50b5935e54106014257a94e6` (peeled) |
| `actions/cache` | **v6.1.0** | `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` |
| `actions/upload-artifact` | **v7.0.2** (2026-10-07) | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` |
| `actions/download-artifact` | v8.0.2 | `9000827ccba6bdab643e8b6fd33ac0654aef8333` |
| `reactivecircus/android-emulator-runner` | v2.38.0 (2026-07-05) | `a421e43855164a8197daf9d8d40fe71c6996bb0d` (peeled) |
| `softprops/action-gh-release` | v3.0.3 (not needed; use the `gh` CLI) | `efb35369e0ad2afab669f228072c1b0d510eae64` |

Notes: checkout v7.0.0 blocks checkout of fork PRs for `pull_request_target`/`workflow_run` (irrelevant here; we use `pull_request`). upload-artifact v7 adds `archive: false` (single file, unzipped). setup-java README: "v1 through v4 are deprecated". setup-gradle v6 runs on `node24`.

### 3.2 setup-gradle v6 behaviour (from `docs/setup-gradle.md` and `setup-gradle/action.yml` @ v6.4.0)

- `cache-provider`: `enhanced` (default) is a "commercial `gradle-actions-caching` library … free for all public repositories". `basic` is MIT, a thin wrapper over `actions/cache` that caches `~/.gradle/caches` and `~/.gradle/wrapper` keyed on hashes of `**/*.gradle*`, `**/gradle-wrapper.properties`, `gradle/*.versions.toml`, with **no restore keys** (any build-file change means a cold cache). `external` is a third option. **Pick `basic`** to keep CI fully OSS. Switch to `enhanced` only if cold builds hurt.
- `cache-read-only` defaults to `github.ref_name != default_branch`: PR runs read the `master` cache and don't write. The first PR before any `master` run is cold.
- `validate-wrappers: true` by default (wrapper JAR checksum validation is built in, so no separate `wrapper-validation` job is needed). `cache-cleanup: on-success` (enhanced only). `add-job-summary: always`. `add-job-summary-as-pr-comment` needs `pull-requests: write` (left off to keep permissions minimal).
- Dependency graph: the separate `gradle/actions/dependency-submission@v6` workflow on push to `master` needs `contents: write`. It feeds Dependabot alerts.

### 3.3 Runner image facts (`actions/runner-images`, image `20261023.334.3` for Ubuntu 24.04)

| | ubuntu-24.04 | ubuntu-26.04 (`ubuntu-latest` from Nov 2026) |
|---|---|---|
| Android cmdline-tools | 12.0 | 20.0 |
| Build-tools | 37.0.0, 36.0.0, 36.1.0, 35.0.0, 35.0.1, 34.0.0 | same |
| Platforms | android-37.2-beta3/-beta2/-beta1, 37.2, 37.1, **37.0 (rev 2)**, 36.1, 36-ext19, 36-ext18, **36 (rev 2)**, 35(+ext), 34(+ext) | same |
| Platform-tools | 37.0.1 | 37.0.1 |
| JDKs | 8, 11, **17 (default)**, 21.0.12, 25.0.4 | 11, 17, 21, **25 (default)** |
| Gradle / Kotlin preinstalled | 9.8.1 / 2.4.21 (unused: we use the wrapper) | 9.8.1 / 2.4.21 |
| `ANDROID_HOME` | `/usr/local/lib/android/sdk` | same |

- **android-36 and android-37.0, plus build-tools 36.0.0 (AGP 9.4.1's default), 36.1.0 and 37.0.0, are all preinstalled.** Local `/opt/android-sdk` matches revisions (`android-37.0` rev 2, `android-36` rev 2), which also matters for F-Droid's "platform revisions" reproducibility note. Licenses are pre-accepted on runner images. The workflow still runs an idempotent `sdkmanager --licenses` + `--install` step as insurance against image drift.
- Runner sizes (GitHub docs): public repo `ubuntu-24.04` = **4 CPU / 16 GB / 14 GB SSD**. A private repo would be 2 CPU / 8 GB. The fork `clayboicardi/2048-android-modern-refresh` is public (HTTP 200 anonymously).

### 3.4 Proposed `.github/workflows/ci.yml` (PR gate)

```yaml
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
  ANDROID_PLATFORM: platforms;android-37.0
  ANDROID_BUILD_TOOLS: '36.0.0'

jobs:
  verify:
    name: verify (format · detekt · tests · screenshots · lint · assemble)
    runs-on: ubuntu-24.04          # pinned: ubuntu-latest becomes 26.04 in Nov 2026
    timeout-minutes: 30
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

      - name: Set up JDK 21
        uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
        with:
          cache-provider: basic      # MIT-licensed provider; wrapper validation is on by default

      - name: Cache Robolectric android-all jars
        uses: actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0
        with:
          path: ~/.m2/repository/org/robolectric   # Robolectric's runtime download dir (MavenDependencyResolver)
          key: robolectric-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml', 'app/src/test/resources/robolectric.properties') }}
          restore-keys: |
            robolectric-${{ runner.os }}-

      - name: Android SDK licenses + packages (idempotent)
        run: |
          SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKM" --licenses > /dev/null || true
          "$SDKM" --install "$ANDROID_PLATFORM" "build-tools;$ANDROID_BUILD_TOOLS" > /dev/null

      - name: Format + static analysis
        run: ./gradlew spotlessCheck detektMain detektTest --continue

      - name: Engine tests + coverage, app unit/Robolectric/screenshot tests
        if: ${{ !cancelled() }}
        run: >-
          ./gradlew :core:engine:test :core:engine:koverVerify
          :app:testDebugUnitTest -Proborazzi.test.verify=true --continue

      - name: Test-count guard (no silently skipped suites)
        if: ${{ !cancelled() }}
        run: ./scripts/check-test-counts.sh

      - name: Android Lint
        if: ${{ !cancelled() }}
        run: ./gradlew :app:lintDebug

      - name: Assemble debug + release (R8)
        if: ${{ !cancelled() }}
        run: ./gradlew :app:assembleDebug :app:assembleRelease

      - name: APK gates (permissions, proprietary deps)
        if: ${{ !cancelled() }}
        run: ./scripts/check-apk.sh

      - name: Upload APKs
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: apks-${{ github.sha }}
          path: |
            app/build/outputs/apk/debug/*.apk
            app/build/outputs/apk/release/*.apk
            app/build/outputs/mapping/release/mapping.txt
          if-no-files-found: warn
          retention-days: 14

      - name: Upload reports (lint, detekt, tests, screenshot diffs)
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: reports-${{ github.sha }}
          path: |
            **/build/reports/
            **/build/test-results/
            app/build/outputs/roborazzi/
          if-no-files-found: ignore
          retention-days: 14
```
- Design choice: every gate step uses `if: !cancelled()`, so **one CI run reports all failures** (format, tests, lint, permissions) instead of one per round-trip. That matters for the hands-off Codex/CI loop. The job still fails if any step fails.
- A single job avoids duplicate Gradle configuration and cache restores. Configuration cache (`org.gradle.configuration-cache=true`, verified working in 03) makes the 5 Gradle invocations cheap after the first.
- **Time budget (estimates, not measured):** cold (no cache) ≈ 12–16 min: Gradle dist 150 MB, deps about 0.7–1 GB, Robolectric jar 213–239 MB, compile, detekt type resolution, about 30 Robolectric tests, Lint, R8. Warm ≈ 6–9 min. `timeout-minutes: 30`. Add ~4 min if `@Config(sdk=[24,37])` smoke jars are not cached. Measure on the first PR and record actuals in the plan.

`scripts/check-apk.sh` (proposed):
```bash
#!/usr/bin/env bash
# Gate: no permission except androidx.core's app-private signature permission; no proprietary/network SDKs.
set -euo pipefail
BT="${ANDROID_HOME:?}/build-tools/${ANDROID_BUILD_TOOLS:-36.0.0}"
ALLOWED_RE="name='com\.clayboicardi\.game2048(\.debug)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'"
status=0
for apk in app/build/outputs/apk/debug/*.apk app/build/outputs/apk/release/*.apk; do
  [ -f "$apk" ] || continue
  bad=$("$BT/aapt2" dump badging "$apk" | grep -E '^uses-permission' | grep -vE "$ALLOWED_RE" || true)
  if [ -n "$bad" ]; then echo "::error file=$apk::forbidden permission(s)"; echo "$bad"; status=1; fi
done
deps=$(./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath)
if echo "$deps" | grep -E 'com\.google\.(android\.gms|firebase|android\.play|android\.ump|android\.datatransport)|com\.crashlytics|io\.sentry|com\.squareup\.okhttp3|io\.ktor|ui-text-google-fonts'; then
  echo "::error::proprietary or network dependency on releaseRuntimeClasspath"; status=1
fi
exit $status
```
Why the allowlist: androidx.core 1.19.1's AAR manifest declares `<permission ${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION protectionLevel=signature>` plus a matching `<uses-permission>` (verified in the AAR). It is **not a runtime permission**. Removing it with `tools:node="remove"` would make `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)` throw on API < 33 (03 §9). This is consistent with "zero runtime permissions". If the plan instead wants literally zero manifest permissions, remove both elements with `tools:node="remove"` and add a Robolectric test asserting nothing calls `ContextCompat.registerReceiver`. Riskier, so not recommended.

`scripts/check-test-counts.sh` (proposed): sum `tests="N"` from `core/engine/build/test-results/test/*.xml` and `app/build/test-results/testDebugUnitTest/*.xml`. Fail if either is 0 or below a committed floor in `config/test-count-floor.txt`, and bump the floor in the same PR that adds tests. This catches the JUnit 6 "expression-bodied test silently not run" trap and accidental `@Ignore` sprees.

### 3.5 Proposed `.github/workflows/release.yml` (no secrets available)

```yaml
name: Release
on:
  push:
    tags: ['v*.*.*']
  workflow_dispatch:
permissions:
  contents: write
concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false
jobs:
  release:
    runs-on: ubuntu-24.04
    timeout-minutes: 40
    env:
      ANDROID_BUILD_TOOLS: '36.0.0'
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
        with: { cache-provider: basic, cache-read-only: true }
      - name: Tag must equal versionName
        run: |
          VN=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
          echo "VN=$VN" >> "$GITHUB_ENV"
          if [ "$GITHUB_EVENT_NAME" = push ] && [ "v$VN" != "$GITHUB_REF_NAME" ]; then
            echo "::error::tag $GITHUB_REF_NAME != versionName $VN"; exit 1; fi
      - name: Full verification
        run: >-
          ./gradlew spotlessCheck detektMain detektTest :core:engine:test :core:engine:koverVerify
          :app:testDebugUnitTest -Proborazzi.test.verify=true :app:lintRelease :app:assembleRelease
      - run: ./scripts/check-apk.sh
      - name: Reproducibility probe (second clean build must be byte-identical)
        continue-on-error: true      # report-only until proven stable; F-Droid cares
        run: |
          cp app/build/outputs/apk/release/app-release-unsigned.apk /tmp/build1.apk
          ./gradlew --no-build-cache --no-configuration-cache clean :app:assembleRelease
          sha256sum /tmp/build1.apk app/build/outputs/apk/release/app-release-unsigned.apk
          cmp /tmp/build1.apk app/build/outputs/apk/release/app-release-unsigned.apk \
            || echo "::warning::release APK is not bit-reproducible (check assets/dexopt/baseline.prof[m])"
      - name: Sign
        env:
          KS_B64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
          KS_PASS: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
          KEY_PASS: ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: |
          BT="$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS"; mkdir -p dist
          IN=app/build/outputs/apk/release/app-release-unsigned.apk
          cp "$IN" "dist/game2048-$VN-unsigned.apk"
          if [ -n "${KS_B64:-}" ]; then
            echo "$KS_B64" | base64 -d > /tmp/release.jks
            "$BT/apksigner" sign --ks /tmp/release.jks --ks-pass env:KS_PASS --ks-key-alias "$KEY_ALIAS" \
              --key-pass env:KEY_PASS --out "dist/game2048-$VN.apk" "$IN"
          else
            # No secrets: ephemeral key so the APK installs. Updates will NOT install over it (different cert).
            keytool -genkeypair -keystore /tmp/ci.jks -storepass android -keypass android -alias ci \
              -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=2048 CI ephemeral (not for updates)"
            "$BT/apksigner" sign --ks /tmp/ci.jks --ks-pass pass:android --out "dist/game2048-$VN-ci-ephemeral.apk" "$IN"
          fi
          for f in dist/*.apk; do "$BT/apksigner" verify --print-certs "$f" || true; done
          (cd dist && sha256sum *.apk > SHA256SUMS)
      - name: GitHub Release
        env: { GH_TOKEN: '${{ github.token }}' }
        run: gh release create "${GITHUB_REF_NAME}" dist/* --title "2048 ${VN}" --generate-notes --verify-tag
```
- Signing reality: with no secrets, every release would carry a different certificate, so users would have to uninstall (losing local data) to update. A **persistent key is a user decision** (see risks). The default debug keystore is also per-machine on runners, so it is no better.
- F-Droid note: their docs say `apksigner` from build-tools ≥ 35.0.0-rc1 produces APKs that `apksigcopier` cannot verify, which only matters if F-Droid later publishes upstream-signed APKs. It doesn't matter for GitHub releases.

### 3.6 Other workflows (optional, small)

- `.github/workflows/dependency-submission.yml`: on `push` to `master`, `permissions: contents: write`, steps checkout → setup-java 21 → `gradle/actions/dependency-submission@3f5f9ada… # v6.4.0`.
- `.github/workflows/instrumented.yml`: `workflow_dispatch` + `schedule: '17 7 * * 1'`. Steps: KVM udev step (§1.6), then `./gradlew :app:pixel6Api36BenchmarkAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect`, `timeout-minutes: 45`. Not required for merge.
- `.github/dependabot.yml`: `github-actions` monthly only (it updates SHA pins and comments). Gradle updates manual, to avoid PR noise that would trigger automatic Codex reviews in a hands-off setup.

### 3.7 Gradle wrapper

- 03 recommends **Gradle 9.7.1** (zero deprecations, which allows `--warning-mode=fail`) with `distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`. The current GA is 9.8.2 (2026-10-19): `distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` (computed from the download and **matches gradle.org/release-checksums**). 9.8.2 emits one AGP-originated `Configuration.setVisible` deprecation.
- Bootstrapping: local `/opt/gradle-8.14.3` cannot configure AGP 9.4 (min Gradle 9.6.0). Run `gradle wrapper --gradle-version 9.7.1 --distribution-type bin --gradle-distribution-sha256-sum <sha>` **in an empty temp dir** (or before adding AGP build scripts), copy `gradlew`, `gradlew.bat` and `gradle/wrapper/*` in, then re-run `./gradlew wrapper` with the same arguments so the wrapper JAR comes from 9.7.1. Replace the legacy 4.10.3 wrapper (`gradle/wrapper/gradle-wrapper.properties`: `gradle-4.10.3-all.zip`).
- Sandbox: `services.gradle.org` → 307 → `release-assets.githubusercontent.com` works. `downloads.gradle.org` (the `.sha256` sidecars) is **blocked**, so take checksums from `gradle.org/release-checksums` / `services.gradle.org/versions/all`.

---

## 4. Release hygiene

### 4.1 R8 / shrinking (AGP 9.4.1)

- AGP 9.3+ simplified DSL (Android "Enable app optimization" page):
  ```kotlin
  android { buildTypes { release { optimization { enable = true } } } }
  ```
  This "enables both code and resource optimization" and "includes a set of default keep rules … equivalent to `proguard-android-optimize.txt`". Keep rules go in `src/main/keepRules/*.keep`. The legacy DSL (`isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), …)`) still works. 03 smoke-verified the legacy form (release APK 1 034 747 B). Pick one; the new DSL is preferred on 9.4.
- AGP 9.0 dropped `proguard-android.txt` (legacy `build.gradle:49-50` used it), filters global options (e.g. `-dontobfuscate`) out of library consumer rules, and applies optimized resource shrinking automatically. R8 **full mode is the default** since AGP 8.0. Do not set `android.enableR8.fullMode=false`.
- **kotlinx.serialization:** the library ships R8 rules that "keep serializers for _all_ serializable classes that are retained after shrinking, so you don't need additional setup". The exception is classes with **named companion objects** (README v1.12.0, "Android"). Rule: never give `@Serializable` classes a named companion. Then no custom keep file is needed.
- Debuggable release-like build type for the instrumented smoke and benchmark: `create("benchmark") { initWith(getByName("release")); signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release" }`.
- Release config also needs:
  ```kotlin
  android {
    dependenciesInfo { includeInApk = false; includeInBundle = false }  // F-Droid rejects Google's encrypted "Dependency metadata" signing block
    // keep vcsInfo default (AGP ≥ 8.3 writes META-INF/version-control-info.textproto); build releases from a clean tagged tree
  }
  ```

### 4.2 Backup / data extraction (DataStore lives in `files/datastore/`)

`AndroidManifest.xml`:
```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules" … >
```
`res/xml/data_extraction_rules.xml` (API 31+):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="file" path="datastore/" />
    </cloud-backup>
    <device-transfer>
        <include domain="file" path="datastore/" />
    </device-transfer>
</data-extraction-rules>
```
`res/xml/backup_rules.xml` (API ≤ 30, needed because minSdk 24):
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="file" path="datastore/" />
</full-backup-content>
```
- An `include` list makes everything else excluded. Data is non-sensitive (scores, settings, game state), so there is no `disableIfNoEncryptionCapabilities`/`requireFlags` restriction, which maximises restores. Quota is 25 MB per app user, so the unlimited undo history must stay well under it. Add a Robolectric/JVM test that an 8×8 game with 20 000 moves serialises to < 5 MB. Without a `<device-transfer>` section, "that mode is fully enabled for all content except for `no-backup` and `cache`", so declare it explicitly.
- Backup itself needs no permission (the system performs it).

### 4.3 F-Droid / FOSS checklist

- No Google Play Services, Firebase, Play Core/in-app review/update, UMP, ads, analytics, crash reporters or downloadable Google Fonts. Enforced by `check-apk.sh` grep and an AGENTS.md rule.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }`. F-Droid build fails with "found extra signing block 'Dependency metadata'" otherwise; the block is encrypted to a Google key.
- Single JDK (21) everywhere, because mixed JDKs change dex annotations (F-Droid "Mismatched Toolchains"). F-Droid's buildserver offers JDK 25/21/17/11/8. Fixed build-tools and platform revisions. No NDK code. No timestamps or build paths baked into resources (no `BuildConfig` build time). Release from a clean tagged commit (vcsInfo).
- No PNGs generated from vectors at minSdk 24 (generation happens only below 21). Commit pre-optimised PNGs if any.
- Known non-determinism sources to watch: `assets/dexopt/baseline.prof`/`.profm` (workaround: disable `*ArtProfile*` tasks via `tasks.whenTaskAdded { if (name.contains("ArtProfile")) enabled = false }`, only if the reproducibility probe fails); R8 non-determinism (fixed in R8 ≥ 8.6.33/8.7.20/8.8); resource shrinker "might increase APK size … non-deterministic" for apps with few resources. The §3.5 probe detects all of these.
- Store metadata: legacy `metadata/<locale>/` (fastlane layout: `full_description.txt`, `short_description.txt`, `featureGraphic.png`, `icon.png`, `phoneScreenshots/`, `sevenInchScreenshots/`, 30 locales). Add `metadata/en-US/changelogs/<versionCode>.txt` per release; F-Droid reads changelogs by versionCode. Refresh screenshots from Roborazzi output, which is deterministic.

### 4.4 Dependency integrity

- Wrapper: `distributionSha256Sum` pinned plus setup-gradle wrapper-JAR validation (default on).
- Dependency graph submitted to GitHub (§3.6), giving Dependabot alerts.
- **Gradle dependency verification** (`gradle/verification-metadata.xml`): optional, M5 only. Command: `./gradlew --write-verification-metadata sha256 spotlessCheck detektMain detektTest :core:engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`. Costs: every dependency bump must regenerate it in the same commit. `aapt2` is OS-classified (Linux-only metadata breaks macOS/Windows devs). Robolectric's android-all jars are fetched **outside** Gradle (to `~/.m2`), so they are not covered unless Robolectric is switched to offline mode: put the two `android-all-instrumented` coordinates in a Gradle configuration, `Sync` them to `build/robolectric-jars/`, and set `robolectric.offline=true` + `robolectric.dependency.dir=<that dir>`. Robolectric's `LocalDependencyResolver` expects files named `<artifactId>-<version>.jar`. The offline variant also removes the separate `~/.m2` cache step. Recommendation: skip verification for v1. Revisit if the project goes to F-Droid.

### 4.5 Version scheme

- Legacy: `versionCode 25`, `versionName "2.2"` (`build.gradle:31-32`), for a different applicationId (`build.gradle:28`), so there is no upgrade path and no constraint.
- New: `versionName = "3.0.0"` (SemVer, continuing the lineage), `versionCode = MAJOR*10000 + MINOR*100 + PATCH` = **30000**. Write both as **literals** in `app/build.gradle.kts` so F-Droid's update checker and the release workflow's `grep` can parse them. No git-count-derived codes (not reproducible from a tarball). Tag `v3.0.0`; the release workflow asserts tag == versionName.

---

## 5. AGENTS.md and the Codex GitHub reviewer

### 5.1 What the official docs say (learn.chatgpt.com/docs/third-party/github; `developers.openai.com/codex/integrations/github` now redirects there)

- **Trigger:** "In a pull request comment, mention `@codex review`. Wait for Codex to react (👀) and post a review." A one-off focus is allowed: `@codex review for issues in the database migration`. `@codex <anything else>` starts a cloud task instead, e.g. `@codex fix the P1 issue`.
- **Automatic reviews:** turned on by the user at chatgpt.com/settings/code-review ("Automatic review", "Review trigger"). Agents cannot set this.
- **Severity:** "In GitHub, Codex flags only P0 and P1 issues". Inline comments are badged `[P1]` etc. The bot account appears as **`chatgpt-codex-connector[bot]`**.
- **Repository guidance:** "Codex searches your repository for `AGENTS.md` files and follows the applicable code review rules. Add a **`## Code Review Rules`** section to the file closest to the code the rules govern. Use `###` headings to group related checks". Root rules apply repo-wide; nested `AGENTS.md` (e.g. `core/engine/AGENTS.md`) adds rules for files under it. Advice: "Start with two or three concise rules … State the safe path or exception … Prefer outcomes over function names … **Leave mechanical checks in CI**" (formatting and lint stay out of review rules). Older docs used the heading "Review guidelines". Use the current heading; adding the older one as well is harmless, but avoid duplicate text.
- **Clean signal (community-observed, not in the official docs):** when Codex finds nothing it reacts **👍** (`+1`) and/or posts "Didn't find any major issues" instead of a review with findings (daleseo.com; PrefectHQ/fastmcp review-pr skill). Treat clean as: a `+1` reaction from `chatgpt-codex-connector[bot]` on the PR or on the triggering comment, **or** a bot comment/review with no `[P0]`/`[P1]` findings, for the **current head SHA** (`review.commit_id == headRefOid`).

### 5.2 Polling recipe for the hands-off loop

The repo must be attached with `add_repo(access: "push")` first; `gh api` is blocked for unattached repos in this session.
```bash
R=clayboicardi/2048-android-modern-refresh; PR=<n>
HEAD=$(gh pr view $PR -R $R --json headRefOid -q .headRefOid)
gh pr comment $PR -R $R --body "@codex review"
# poll every 60 s, give up after ~25 min, then re-comment once
gh api repos/$R/issues/$PR/reactions --jq '.[]|select(.user.login=="chatgpt-codex-connector[bot]")|.content'   # "+1" = clean, "eyes" = running
gh api repos/$R/issues/comments/<trigger_comment_id>/reactions --jq '.[]|select(.user.login=="chatgpt-codex-connector[bot]")|.content'
gh api repos/$R/pulls/$PR/reviews --jq '.[]|select(.user.login=="chatgpt-codex-connector[bot]")|{commit_id,state,body}'
gh api repos/$R/pulls/$PR/comments --jq '.[]|select(.user.login=="chatgpt-codex-connector[bot]")|{path,line,body}'
gh api repos/$R/issues/$PR/comments --jq '.[]|select(.user.login=="chatgpt-codex-connector[bot]")|.body'
```
After pushing fixes, re-trigger with a new `@codex review` (reviews are per commit). Reply to and resolve each bot thread (GitHub MCP `resolve_review_thread`) so the next round is unambiguous. Merge only when CI is green **and** the clean signal applies to the head SHA.

### 5.3 Proposed root `AGENTS.md`

```markdown
# AGENTS.md — 2048 (native Kotlin + Jetpack Compose + Material 3)

App id `com.clayboicardi.game2048`, label "2048". Fully offline; no runtime permissions; F-Droid-friendly (no proprietary deps).
Modules: `:core:engine` (pure Kotlin/JVM rules — no Android), `:app` (Compose UI, ViewModel, DataStore).
Default branch: `master`.

## Build & verify
JDK 21; Android SDK platform `android-37.0`, build-tools 36.0.0.
- All gates (same as CI): `./scripts/verify.sh`
- Record screenshots after intended UI changes: `./gradlew :app:recordRoborazziDebug` → commit PNGs in
  `app/src/test/screenshots/` and list them in the PR body.

## Code Review Rules

### Offline, permissions, and dependencies
- Flag any change that adds a permission to the merged manifest (other than androidx.core's app-private
  `*.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) or any network, analytics, ads, crash-reporting, Google Play
  services, Firebase, Play Core, or downloadable-font dependency, including transitive ones.
  Safe path: implement locally; haptics via `LocalHapticFeedback` (no VIBRATE); fonts bundled in `res/font`.

### Game rules (`:core:engine`)
- Flag changes that break 2048 semantics: a tile merges at most once per move; merging starts from the wall in
  the move direction; score increases by exactly the merged values; a new tile (2 at 90 %, 4 at 10 %, uniformly
  random empty cell) spawns only after a move that changed the board; a no-op move must not consume randomness,
  add undo history, or change score; the win is reported once, when the first 2048 tile appears, and play can continue.
- Flag clock reads, unseeded randomness (`Math.random`, `Random.Default`, `kotlin.random.Random(seed)`), or Android
  imports in `:core:engine`. Safe path: the injected app-owned PRNG.

### Saved data
- Flag changes to persisted/serialized state that would fail to read data written by the previous release, lose the
  best score or settings on decode errors, or lower a best score. Safe path: new fields get defaults; corrupt data
  falls back to defaults via the DataStore corruption handler; add a fixture test for the old format.

### Input, state, and motion
- Flag code where swipes or key presses can be dropped or applied to a stale board while animations run, state not
  restored after rotation/process death, or animations that ignore the system "remove animations" setting.

### Accessibility and text
- Flag interactive elements without accessibility labels, touch targets below 48dp, tile text/background pairs under
  4.5:1 contrast, or user-visible text not in string resources.

### Verification integrity
- Flag PRs that weaken gates: `@Ignore`/disabled tests without a stated reason, new Lint/detekt baselines or broad
  suppressions, lowered coverage or test-count floors, removed `-Proborazzi.test.verify`, or changed screenshot
  references not explained in the PR body. Also flag release regressions: R8 optimization off, blanket `-keep`
  rules, `dependenciesInfo` included, or a release without a higher `versionCode`.
```
Optionally add `core/engine/AGENTS.md` that holds only the "Game rules" block, so engine rules apply only to engine diffs (the docs recommend this placement). If Codex output is noisy, trim to the top three blocks (offline, game rules, saved data), as the docs advise.

---

## 6. Verification gates per milestone

Shared definitions:
- `G-fmt`: `./gradlew spotlessCheck`
- `G-static`: `./gradlew detektMain detektTest` (drop if detekt is removed per §2.4)
- `G-engine`: `./gradlew :core:engine:test :core:engine:koverVerify`
- `G-app-test`: `./gradlew :app:testDebugUnitTest -Proborazzi.test.verify=true`
- `G-lint`: `./gradlew :app:lintDebug` (M5: also `:app:lintRelease`)
- `G-asm`: `./gradlew :app:assembleDebug :app:assembleRelease`
- `G-apk`: `./scripts/check-apk.sh`
- `G-count`: `./scripts/check-test-counts.sh`
- `G-ci`: the PR's CI run is green on `ubuntu-24.04`
- `G-codex`: Codex clean signal (§5.1) on the head SHA

`./scripts/verify.sh` = `./gradlew spotlessCheck detektMain detektTest :core:engine:test :core:engine:koverVerify :app:testDebugUnitTest -Proborazzi.test.verify=true :app:lintDebug :app:assembleDebug :app:assembleRelease --continue && ./scripts/check-test-counts.sh && ANDROID_BUILD_TOOLS=36.0.0 ./scripts/check-apk.sh`. In the sandbox only, add `--init-script <mirror.init.gradle.kts>` if Maven Central returns 429 (03 §6).

| Milestone | Must pass (in addition to everything from earlier milestones) | Extra acceptance checks |
|---|---|---|
| **M0 Bootstrap** (wrapper 9.7.1, AGP 9.4.1, Kotlin 2.4.21, empty Compose app, delete WebView + `assets/2048` submodule + `.travis.yml`, add CI and AGENTS.md) | `./gradlew --version` (Gradle 9.7.1, JVM 21); `G-fmt`, `G-static`, `G-lint`, `G-asm`, `G-apk`; one trivial Robolectric test plus one Roborazzi screenshot recorded and verified (`G-app-test`); `G-ci`; `G-codex` | `git submodule status` is empty and `.gitmodules` is removed. `aapt2 dump badging` shows `package: name='com.clayboicardi.game2048'` and `application-label:'2048'`. **The first push must include `.github/workflows/ci.yml`**, which proves the token can push workflow files (needs the `workflows` scope). The first `@codex review` gets a 👀 reaction, which proves the Codex connection works. |
| **M1 Engine** | `G-engine` (golden table + 13 properties + PRNG golden sequence + determinism replay; Kover ≥ 90 % line / ≥ 85 % branch); `G-count` | A grep test proves `:core:engine` has no `android.`, `java.time`, `System.currentTimeMillis`, `Math.random` or `Random.Default`. |
| **M2 State + persistence** | `G-app-test` (ViewModel + Turbine, DataStore round-trip, corruption, old-fixture decode, 20k-move size < 5 MB, input-burst ordering) | Unlimited undo works across process death (`SavedStateHandle` + DataStore). Undo works on game over. |
| **M3 Board UI + motion** | `G-app-test` (behaviour tests: swipe/keys/undo/new game; screenshots for new/mid/win/over × light/dark) | Animations settle deterministically in tests. Reduce-motion path covered (animator scale 0 means no animation, same final state). |
| **M4 Theming, settings, stats, i18n, a11y** | `G-app-test` incl. ATF `CheckLevel.Error`; screenshot matrix (RTL `ar`, `de`, fontScale 2.0, SmallPhone, MediumTablet); `G-lint` (ExtraTranslation fatal, MissingTranslation informational) | TalkBack custom actions present (semantics test). Per-app locale config generated. |
| **M5 Release hardening** | `G-asm` with `optimization { enable = true }`; `:app:lintRelease`; `G-apk`; release workflow dry run via `workflow_dispatch`; reproducibility probe reports identical APKs (or the cause is documented) | `aapt2 dump badging` shows `versionCode='30000' versionName='3.0.0'`. Backup XMLs are present and Lint-clean. `metadata/en-US/changelogs/30000.txt` exists. Optional instrumented smoke on the `benchmark` build type passes. |
| **M6 (optional, manual)** | GMD job green; macrobenchmark startup numbers recorded; baseline profile only if the reproducibility probe stays green | Not required for merge. |

Every milestone PR also needs: `G-ci` green, `G-codex` clean (or a 👍), the PR body listing changed screenshot references, and no new baselines or suppressions without justification.

---

## 7. Proposed version-catalog excerpt (quality/test only; all verified)

```toml
[versions]
junit4 = "4.13.2"
junitBom = "6.1.3"
kotestProperty = "6.2.6"
coroutines = "1.11.0"
turbine = "1.2.1"
robolectric = "4.17"
roborazzi = "1.77.0"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"
espresso = "3.7.0"            # force: ui-test-junit4 1.12.1 drags 3.5.1 (crashes on SDK 37)
kover = "0.9.11"
spotless = "8.10.4"
ktfmt = "0.65"
detekt = "2.0.0-alpha.6"
slackComposeLints = "1.6.1"
benchmark = "1.5.0"
baselineprofile = "1.5.0"
profileinstaller = "1.4.1"
uiautomator = "2.4.0"

[libraries]
junit4 = { module = "junit:junit", version.ref = "junit4" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junitBom" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
kotest-property = { module = "io.kotest:kotest-property", version.ref = "kotestProperty" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
roborazzi-accessibility-check = { module = "io.github.takahirom.roborazzi:roborazzi-accessibility-check", version.ref = "roborazzi" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }                       # BOM 2026.09.00 → 1.12.1
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
compose-ui-test-junit4-accessibility = { module = "androidx.compose.ui:ui-test-junit4-accessibility" }
slack-compose-lints = { module = "com.slack.lint.compose:compose-lint-checks", version.ref = "slackComposeLints" }
benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }

[plugins]
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }   # marker on Maven Central
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }              # Plugin Portal
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
detekt = { id = "dev.detekt", version.ref = "detekt" }
baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineprofile" }   # Google Maven
```
`pluginManagement.repositories`: `google()`, `mavenCentral()`, `gradlePluginPortal()`. Kover and the Kotlin Compose plugin markers are only on the Portal; the Roborazzi marker is only on Central.

Robolectric unit-test block (adds 03's verified fixes):
```kotlin
android.testOptions.unitTests {
  isIncludeAndroidResources = true
  isReturnDefaultValues = true
  all {
    it.maxHeapSize = "3g"
    it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
    it.jvmArgs(
      "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
      "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
      "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
      "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
  }
}
```

---

## 8. Risks and open questions

1. **Persistent release signing key.** No secrets exist. Without a stable key every GitHub release has a new certificate, so users cannot update in place. Options: (a) the user adds 4 repo secrets later; (b) the agent generates a keystore and stores it via `gh secret set`, which needs admin scope and handling of secret material; (c) ship unsigned plus an ephemeral-signed APK and say so. Worth asking the user up front, because the hands-off run cannot resolve it safely.
2. **Workflow-file push permission.** Pushing `.github/workflows/*.yml` needs a token with the `workflows` permission. If the session's git credentials lack it, the M0 push is rejected. Detect this on the first push.
3. **Codex trigger identity and clean signal.** Codex must be connected to the repo by the user, and the `@codex review` comment must come from an account Codex accepts. The 👍 "clean" behaviour is community-documented, not in the official docs. Mitigation: accept "a bot review/comment for the head SHA with zero P0/P1 findings" as clean too, plus a timeout and a one-time re-trigger.
4. **detekt is alpha.** Mitigated by the exit criterion in §2.4. Compose rules come from stable Slack Lint, not detekt.
5. **Robolectric SDK 37 maturity**, and the espresso 3.5.1 crash (fixed by forcing 3.7.0). Screenshots are pinned to SDK 36.
6. **Screenshot drift between local and CI.** This should not happen (same OS, glibc, JDK major, android-all jars), but it is unproven until M0. Fallback: commit CI-produced images.
7. **`ubuntu-latest` → 26.04 in November 2026**, which changes the default JDK to 25 and the cmdline-tools to 20.0. Pinning `ubuntu-24.04` avoids it. Ubuntu 24.04 images remain supported; only the 22.04 images are being deprecated. The runner-images announcement says "will begin deprecation on September 17th and will be fully unsupported by April 17th" without giving years. Re-check the 24.04 lifecycle yearly.
8. **F-Droid reproducibility** (baseline.prof, R8, resource shrinker). Only detected by the probe; mitigations are listed in §4.3.
9. **Unlimited undo vs persistence cost** (DataStore rewrites the whole file on each move). Size and time property tests guard it. The 25 MB backup quota also applies.
10. **Sandbox Maven Central 429s** (03 §6). Local gates may need the mirror init script. CI is unaffected.

---

## 9. Sources (fetched 2026-10-28 unless noted)

Version metadata (primary):
- AGP: https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml (9.4.1 POM last-modified 2026-09-18; 9.5.0-rc02 2026-10-26; 9.6.0-alpha01)
- Compose BOM 2026.09.00 POM (ui/ui-test-* 1.12.1, material3 1.4.0): https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom
- androidx.test (core 1.7.0, ext:junit 1.3.0, espresso 3.7.0, uiautomator 2.4.0), benchmark/baselineprofile 1.5.0, profileinstaller 1.4.1, datastore 1.3.0: `https://dl.google.com/dl/android/maven2/<group>/<artifact>/maven-metadata.xml`
- ATF 4.1.1: https://dl.google.com/dl/android/maven2/com/google/android/apps/common/testing/accessibility/framework/accessibility-test-framework/maven-metadata.xml
- androidx.core 1.19.1 AAR manifest (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) and Compose UI 1.12.1 AAR (minSdk 23): dl.google.com AARs
- Maven Central: Robolectric 4.17, android-all-instrumented `16-robolectric-13921718-i7` (213 431 622 B) / `17-robolectric-15733970-i7` (238 744 924 B); Roborazzi 1.77.0; Paparazzi 2.0.0-alpha05.1; detekt `dev.detekt` 2.0.0-alpha.6; detekt 1.23.8; compose-rules 0.6.8; ktlint 1.8.0; ktfmt `com.facebook` 0.64 (kotlin-compiler 2.3.20) / `org.jetbrains.kotlinx:ktfmt` 0.65 (kotlin-compiler 2.4.10); Spotless 8.10.4 (spotless-lib 4.10.4); coroutines-test 1.11.0; Turbine 1.2.1; kotest-property 6.2.6; JUnit 6.1.3; junit 4.13.2; Truth 1.4.5; Kover 0.9.11; kotlinx-serialization 1.12.0; Slack compose-lint-checks 1.6.1 — https://repo1.maven.org/maven2/
- Plugin Portal markers: https://plugins.gradle.org/m2/
- Gradle current 9.8.2: https://services.gradle.org/versions/current ; checksums: https://gradle.org/release-checksums/

Testing:
- Robolectric releases (4.17 "supports SDK 37"; 4.16 "JDK 21 if running tests with SDK 36"): https://github.com/robolectric/robolectric/releases
- Robolectric 4.17 source `DefaultSdkProvider.java`, `MavenDependencyResolver.java` (`~/.m2/repository`), `LegacyDependencyResolver.java`/`LocalDependencyResolver.java`: https://github.com/robolectric/robolectric/tree/robolectric-4.17
- Robolectric configuration (`robolectric.offline`, `robolectric.dependency.dir`, sdk defaults to targetSdk): https://robolectric.org/configuring/
- Roborazzi docs @1.77.0 (build_setup, faq, gradle_properties_options, preview_support, github_actions, accessibility-check README; `ThresholdValidator(0F)` default; `RobolectricDeviceQualifiers`): https://github.com/takahirom/roborazzi/tree/1.77.0
- Paparazzi changelog: https://github.com/cashapp/paparazzi/blob/2.0.0-alpha05.1/CHANGELOG.md
- Compose Preview Screenshot Testing ("no longer maintained"): https://developer.android.com/studio/preview/compose-screenshot-testing ; AGP test suites (AGP 9.5.0-alpha03+, experimental): https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites
- Compose v2 testing APIs: https://developer.android.com/develop/ui/compose/testing/migrate-v2
- Compose accessibility testing: https://developer.android.com/develop/ui/compose/accessibility/testing
- Gradle Managed Devices: https://developer.android.com/studio/test/gradle-managed-devices
- System images: https://dl.google.com/android/repository/sys-img/android/sys-img2-4.xml , …/aosp_atd/sys-img2-4.xml , …/google_apis/sys-img2-4.xml
- KVM on GitHub Linux runners: https://github.blog/changelog/2024-04-02-github-actions-hardware-accelerated-android-virtualization-now-available/
- android-emulator-runner v2.38.0 README: https://github.com/ReactiveCircus/android-emulator-runner
- Kover docs/changelog @v0.9.11: https://github.com/Kotlin/kotlinx-kover/tree/v0.9.11/kover-gradle-plugin/docs

Static analysis:
- detekt 2.0 changelog + migration guide @v2.0.0-alpha.6: https://github.com/detekt/detekt/tree/v2.0.0-alpha.6/website
- compose-rules support matrix (`docs/detekt.md` @v0.6.8): https://github.com/mrmans0n/compose-rules
- Slack compose-lints changelog 1.6.1 (lint 32.4.1): https://github.com/slackhq/compose-lints/blob/main/CHANGELOG.md
- Spotless Gradle changes (8.10.4 ktfmt 0.65): https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md

CI:
- gradle/actions v6.4.0 README, `docs/setup-gradle.md`, `setup-gradle/action.yml`: https://github.com/gradle/actions
- actions/checkout CHANGELOG (v7.0.1): https://github.com/actions/checkout/blob/main/CHANGELOG.md ; upload-artifact v7 README: https://github.com/actions/upload-artifact ; setup-java v6 README: https://github.com/actions/setup-java
- Runner images: https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md (image 20261023.334.3), https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2604-Readme.md , announcement https://github.com/actions/runner-images/issues/14748
- Runner hardware: https://docs.github.com/en/actions/reference/runners/github-hosted-runners

Release:
- AGP 9.4 release notes (Gradle 9.6.0 min, API 37): https://developer.android.com/build/releases/gradle-plugin
- App optimization (AGP 9.3 `optimization {}` DSL, keepRules, full mode default): https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- Auto Backup / data extraction rules: https://developer.android.com/identity/data/autobackup
- kotlinx.serialization Android/R8 section (v1.12.0 README): https://github.com/Kotlin/kotlinx.serialization/blob/v1.12.0/README.md
- F-Droid reproducible builds: https://f-droid.org/docs/Reproducible_Builds/ ; extra signing block: https://forum.f-droid.org/t/build-fails-with-found-extra-signing-block/29220 ; DSL: https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/DependenciesInfo

Codex:
- Review GitHub PRs with Codex (`@codex review`, 👀, P0/P1 only, `## Code Review Rules`): https://learn.chatgpt.com/docs/third-party/github (redirect target of https://developers.openai.com/codex/integrations/github)
- Code review overview: https://learn.chatgpt.com/docs/code-review
- Community reports of 👍 / "Didn't find any major issues": https://daleseo.com/codex-code-review/ , https://tessl.io/registry/skills/github/PrefectHQ/fastmcp/review-pr

Legacy code references: `build.gradle:24-25` (compileSdk 28 / build-tools 28.0.3), `:28-32` (appId, min 14, target 28, versionCode 25, versionName 2.2), `:49-50` (`minifyEnabled false`, `proguard-android.txt`), `:54-56` (`lintOptions` only disables `GoogleAppIndexingWarning`); `AndroidManifest.xml:3,6,12,14`; `.travis.yml:1-23`; `gradle/wrapper/gradle-wrapper.properties` (4.10.3); `assets/2048/js/game_manager.js:7,115,202-216,228-233,240-266`; `assets/2048/js/grid.js:37-43`; `res/values*/strings.xml` (4 strings × 20 locales).
