# AGENTS.md

2048 for Android. This repository is being rewritten from a 2014 WebView shell into a native Kotlin + Jetpack Compose + Material 3 app: package `com.clayboicardi.game2048`, label "2048", version 1.0.0 (10000; D-271). The app is fully offline, has no runtime permissions, and keeps no data off the device. The rewrite is specified in `docs/modernization/`; `docs/modernization/DECISIONS.md` is canonical and `docs/modernization/PLAN.md` is the execution plan.

- Default branch: `master`. This repository is a fork of `uberspot/2048-android`: every GitHub operation targets `clayboicardi/2048-android-modern-refresh` (API tools: `owner`/`repo`; gh: `-R clayboicardi/2048-android-modern-refresh`), and pull requests target `master` of this repository only.
- Status: until the M0 milestone commit lands, the tree still holds the legacy WebView project (root `build.gradle`, `src/`, `res/`, the `assets/2048` submodule). It does not build with current tools and is not a gate; CI reports it and passes. From M0 on, the layout is `docs/modernization/ARCHITECTURE.md` §1. Progress is tracked in the execution PR body (`docs/modernization/PLAN.md` §6.9).

## Build & verify

- JDK 21 (bytecode target 17). Android SDK with `platforms;android-37.0`, `build-tools;36.0.0` (AGP 9.4.1's default) and `build-tools;37.0.0` (check scripts), located through an exported `ANDROID_HOME` (worktrees do not share an uncommitted `local.properties`). Use `./gradlew` (Gradle 9.7.1) only.
- Run local builds one at a time: wrap `./scripts/verify.sh` and any direct `./gradlew` in `flock "$HOME/.cache/game2048-gradle.lock"` (`docs/modernization/DECISIONS.md` D-178).
- The gate is `scripts/verify.sh`. CI runs exactly the same script.

| Command | Runs | Use |
|---|---|---|
| `./scripts/verify.sh` | Spotless, detekt, engine tests and Kover, app unit and screenshot tests, Lint, debug and release builds, then `check-test-counts.sh`, `check-apk.sh`, `check-licenses.sh`, `check-metadata.sh` | Required on the exact commit before every push |
| `./scripts/verify.sh quick` | `spotlessCheck :core:engine:test` | Inner loop only; never a push gate |
| `./scripts/verify.sh --record` | Re-records Roborazzi goldens and lists new or changed PNGs | After an intended UI change |
| `./scripts/verify.sh full --record` | `--record`, then the full gate | Record and gate in one call |

- Format before committing: `./gradlew spotlessApply`.
- Goldens live in `app/src/test/screenshots/`. After `--record`, open and look at every PNG it lists, and describe each in the commit body (`docs/modernization/QUALITY.md` §1.9). Never commit a PNG that shows a defect; never edit or delete a golden without changing the test that compares it.
- Test-count floors in `config/test-count-floor.txt` are raised to the executed counts at the end of each milestone and never lowered.
- If Maven Central rate-limits your environment, use only an out-of-repo Gradle init script passed through `VERIFY_GRADLE_ARGS` (`docs/modernization/QUALITY.md` §3.3). Never commit that script, never place it in `~/.gradle/init.d/`, and never write a mirror URL into a tracked file outside `docs/`.

## Where things are specified

| Topic | Document |
|---|---|
| Every decision, with change control (D-173) | `docs/modernization/DECISIONS.md` |
| Milestones M0–M6, file ownership, canonical names, review and merge protocol | `docs/modernization/PLAN.md` |
| Modules, build files, engine API, data layer, rendering pipeline, file map | `docs/modernization/ARCHITECTURE.md` |
| Feature behavior and acceptance criteria `Fnn-ACk` | `docs/modernization/FEATURES.md` |
| Colors, type, geometry, layouts, screens, components, golden list | `docs/modernization/DESIGN_SYSTEM.md` |
| Motion timelines, haptics, motion tests `AC-*` | `docs/modernization/MOTION_SPEC.md` |
| Test infrastructure, check scripts, CI, Codex protocol | `docs/modernization/QUALITY.md` |
| Strings, translations, store listing, legal files, signing | `docs/modernization/L10N_STORE_LEGAL.md` |

## Conventions

- Kotlin is formatted by ktfmt through Spotless and compiled with `allWarningsAsErrors`; Lint warnings are errors; detekt runs on both modules. No Lint or detekt baselines; every `@Suppress` or `tools:ignore` carries a same-line reason.
- `@OptIn` appears only in `app/src/test/kotlin/com/clayboicardi/game2048/testing/Accessibility.kt` (`ExperimentalRoborazziApi`), `app/src/test/kotlin/com/clayboicardi/game2048/testing/MainDispatcherRule.kt` (`ExperimentalCoroutinesApi`, D-215) and `app/src/main/kotlin/com/clayboicardi/game2048/ui/components/GameModalSheet.kt` (`ExperimentalMaterial3Api`). Material 3 1.5-only APIs are called only from `ui/theme/` and `ui/components/`.
- `:core:engine` is pure Kotlin (stdlib only) and deterministic: all randomness comes from its `SplitMix64` with a stored seed. Its rules are in `core/engine/AGENTS.md`.
- Compose: screens collect state with `collectAsStateWithLifecycle`; ViewModels own state; tiles are keyed by stable tile ids; animated values are read in draw or layout lambdas (`graphicsLayer`, `drawBehind`, `offset {}`), never in a composition body; no state writes during composition; anything visible is timed through `MoveTimeline` or animation specs, not a raw `delay()`.
- Every hex color, dp, sp and millisecond value lives in `ui/theme/`. Every user-visible string is a resource; counts use plurals; sentences are never concatenated.
- No new permission, network access, analytics, ads, Google Play Services, Firebase or downloadable fonts. Haptics use `HapticFeedback` or `View.performHapticFeedback`, never `Vibrator`. DataStore, file and `PackageManager` work stays off the main thread.
- Dependencies are exactly the version catalog of DECISIONS.md D-016. Adding or upgrading one needs a D-173 entry.
- Tests: the first KDoc line of an acceptance-criterion test is its AC ID (for example `/** F05-AC3 */`). No `@Ignore`, no wall-clock time, no unseeded randomness. Robolectric runs at SDK 36 unless a test says otherwise. A test proving several criteria lists all their IDs on that first line. Property tests seed with `PropertyTesting.defaultSeed = 0x2048L` (D-145).

## Commits and pull requests

- Subjects of at most 72 characters, imperative: `M<n>: …` for milestone work, `review(r<k>): …` for fixes in Codex round k, `release: …` for a version bump (D-271), `ci: …`, `docs: …`.
- Body: `D-IDs: …`; `Gates: scripts/verify.sh passed (engine=<n> tests, app=<m> tests)`; `Screenshots: <names> (all inspected)` or `Screenshots: none`.
- A change to decided behavior carries its D-173 entry in `docs/modernization/DECISIONS.md` in the same commit.
- No AI model names or model identifiers in tracked files (code, docs, resources, test names, golden metadata). Commit trailers and PR footers are exactly what the execution environment supplies, kept verbatim (D-164, D-180).
- Implementer agents commit locally (the execution branch, or `wip/<WP>`) and never push or call GitHub; only the orchestrator pushes, opens or updates the PR, requests Codex reviews, replies and merges (D-194).
- Pushed history of an open PR is never rewritten (no force-push, rebase or amend).

## Code Review Rules

Review against `docs/modernization/DECISIONS.md`. Severity: **P0** breaks saved games, privacy, the permission set, or makes the game unplayable; **P1** is a user-visible bug or regression covered by a rule below; **P2/P3** is everything else. Engine rules are in `core/engine/AGENTS.md`. Codex on GitHub reports only P0 and P1.

### Offline, permissions and dependencies
- Flag any merged permission other than androidx.core's `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (D-002), any new exported component, and any network, analytics, ads, crash-reporting, Google Play Services, Firebase or downloadable-font dependency.
- Flag manifest additions: orientation locks, `configChanges`, new activities, services, receivers or providers, cleartext traffic.

### Saved data and game state
- Flag changes that cannot read the previous on-disk format, lose data on a decode error, or lower a best score. The fixtures under `app/src/test/resources/fixtures/v1/` are write-once. The safe path is defaults + fixtures + `DataMigration` (D-041, D-045).
- Flag state loss: a state change that is not saved (D-043), state lost or animations replayed after process death or recreation (D-047).
- Flag DataStore, file or `PackageManager` I/O on the main thread or in composition.
- Flag unbounded growth: history, records, caches, particles or score floats without the caps of D-046 and MOTION_SPEC.md.
- Flag any change to game rules outside `:core:engine`.

### Input, rendering and motion
- Flag mirroring of the board, swipe directions or the direction pad in RTL; they stay left-to-right (D-104).
- Flag divergence between what is drawn and the engine state: the board must render from the committed state, a new move snaps in-flight animations (D-052, D-062), and the last frame equals the static render.
- Flag input queues, dropped moves, or moves accepted while a modal is open (D-052, D-054).
- Flag tile recomposition during a slide, and haptics fired outside `HapticsController`.

### Accessibility, text and translations
- Flag regressions in board semantics, announcements, focus order, touch targets below 48 dp, text truncated at font scale 2, or contrast (D-080–D-085).
- Flag hard-coded user-visible text, a key missing from any of the 21 locales, a translated `app_name`, broken placeholders or plurals, concatenated sentences, and non-Latin digits in game numerals (D-100–D-104).

### Verification integrity
- Flag weakened gates: ignored or skipped tests, baselines, lowered floors or thresholds, removed check-script assertions, unexplained golden changes, R8 off, blanket `-keep`, `dependenciesInfo` on, and new `@OptIn` outside the three files named in Conventions.

### Intentional, do not flag
- The committed `signing/ci-sideload.p12` and its public password (D-131). It is a non-secret sideload key, never used for stores.
- androidx.core's signature self-permission.
- Latin digits in game numerals in every locale.
- Classic tile 32 at 3.66:1 contrast (D-083).
- Wording in `docs/modernization/research/` (frozen evidence) and wording-only nits in other docs.
- The pixels of generated goldens and store images; only a golden change that no commit body lists is a finding.
- Legacy behavior changes recorded in `docs/modernization/DECISIONS.md` §1 or in a D-entry, such as the new package id, the removed WebView game, and the fixed legacy bugs B1–B21.
