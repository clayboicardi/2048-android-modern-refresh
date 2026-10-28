# DECISIONS.md: canonical decision log for the 2048 native rewrite

Status: **canonical**, dated 2026-10-28. Every other document under `docs/modernization/` and every execution commit must follow this file. If you find a conflict, this file wins. If a decision here turns out to be technically impossible, change it only through the change-control rule in D-173.

Repository: `clayboicardi/2048-android-modern-refresh`, a GitHub fork of `uberspot/2048-android`. The default branch is **`master`**: when the user says "main", they mean `master`.

## Conventions

- **IDs.** IDs are `D-NNN`, grouped by area. Each area starts at a multiple of ten (D-001, D-010, D-020, …); Quality and Process use more than ten numbers. The gaps are intentional, and IDs are never reused.
- **Source tags:**
  - **[USER]** is locked by the user (U1–U7) and is never relitigated.
  - **[ORCH]** is set by the orchestrator (O1–O17).
  - **[RESEARCH]** was picked here from the research evidence.
  - A decision can carry more than one tag. The first tag is the authority.
- **Evidence pointers** are paths relative to `docs/modernization/`, for example `research/04-design-motion.md §10.4`. The verified Gradle baseline is `research/smoke-build/`.
- **Verified 2026-10-28.** This marks facts checked while writing this log: Maven Central plugin markers, the `ui-test-manifest` AAR manifest, SplitMix64 outputs, and the contrast and ΔE of the super-tile ramp.
- **Status names.** "Accepted move" means a move that changed the board (D-035). "In progress" means `moveCount ≥ 1` and status `PLAYING` or `KEEP_PLAYING` (D-036).
- **Planning entries.** D-174–D-211 and Appendix B (TC-1–TC-7) were added during planning, before the plan PR merged. They are canonical, not deviations. Execution-time entries under D-173 start at D-212.
- **Back-notes.** When a later entry extends, refines, clarifies or amends an entry, the touched entry gets a line `- **Note:** <Verb>ed by D-NNN.` after its Source line. A technical correction is marked inline as "(technical correction TC-n)".

---

## 0. Decision summary

| ID | Area | Decision |
|---|---|---|
| D-001–D-009 | Product scope | Native Kotlin/Compose/M3 rewrite; offline with only the androidx self-permission; v1.0 scope table + non-goals; 11 settings; new game confirms only while in progress; one saved game per size; win/game-over cards; What's new only after an update |
| D-010–D-019 | Platform & toolchain | Gradle 9.7.1 (sha256 `acd53f1e…d20a`); AGP 9.4.1 with built-in Kotlin; Kotlin 2.4.21; BOM 2026.09.00 + material3 1.5.0-beta02 (no experimental opt-in); compile/target 37, min 24; JDK 21 → bytecode 17; `google()` + `mavenCentral()` only |
| D-020–D-026 | Architecture | `:app` + `:core:engine` (stdlib only); manual `AppContainer` DI; Nav3 with 6 keys; state-first ViewModel; conflated DataStore writer; per-tile composables keyed by ID |
| D-030–D-039 | Engine | N = 3–8, row-major exponent bytes; leading-wall merge-once; spawn value-then-cell, 1/10 fours; targets 256 / 2048; `WON` / `KEEP_PLAYING` / `OVER`; SplitMix64; unlimited undo via a 2-bit log + 32-move snapshots; no redo |
| D-040–D-048 | Data & persistence | `settings` / `records` / `game-N` JSON DataStores; `schemaVersion` + fixtures; Base64 blobs; a write per change; bests never drop; ≤ 256 KiB at 10k moves; backup includes `datastore/` |
| D-050–D-054 | Input | Commit during the drag by default (24 dp, ratio 1.4), on-release option, 600 dp/s fling; no queue; arrows/WASD/HJKL/D-pad, U/Ctrl+Z/Backspace, N/R; Back never intercepted on the Game root |
| D-060–D-066 | Motion & haptics | `GameMotion` tokens; slide 0.9/1400; pop and spawn 0.6/800; Motion Full/Reduced/Off; one haptic per move by priority; no VIBRATE |
| D-070–D-079 | Visual design | Classic Refined default + Neon Arcade; distinct super tiles; high contrast; dynamic color for chrome only; N×N geometry; Google Sans Flex digit subset ≤ 32 KiB; compact labels below 12 dp |
| D-080–D-085 | Accessibility | N×N collection semantics + custom actions; live regions; TalkBack direction pad; contrast and CVD tests; ATF |
| D-090–D-095 | Adaptive layout | No orientation lock; `appCategory="game"`; 3-column / side panel / stacked; edge-to-edge + Immersive mode; predictive back |
| D-100–D-106 | Localization & RTL | en + 20 locales complete; `MissingTranslation` = error; plurals; Latin digits; forced-LTR board; system per-app language page |
| D-110–D-116 | Identity & store | `com.clayboicardi.game2048` "2048"; adaptive icon `#3A2900` on `#E6A11A`; splash gated on load; fastlane tree with 22 locales; store art rendered from code |
| D-120–D-124 | Legal & privacy | MIT with 3 copyright lines; NOTICE; in-app licenses with a coverage check; PRIVACY.md; credits |
| D-130–D-134 | Signing & versioning | 3.0.0 / 30000; committed public sideload keystore for debug and release; R8; no release workflow |
| D-140–D-153 | Quality & CI | `verify.sh` = CI; Spotless + ktfmt 0.65; Lint warnings as errors; `allWarningsAsErrors`; detekt optional; Kover 90/85; Robolectric + Roborazzi goldens Read by the agent; APK gates; `ubuntu-24.04` with SHA-pinned actions |
| D-160–D-173 | Process | Plan PR, then one execution PR on the re-cut branch; M0–M6 commits; `verify.sh` before every push; `@codex review` after M2 and at the end; merge on clean Codex + green CI, valve after 5 rounds; stop conditions |
| D-174–D-211 | Planning additions | Opt-in isolation; test-task inputs; check-script additions; no model names in tracked files; epoch boards, motion timing (`MoveTimeline`, `delayedSpec`), level-dependent `MotionScheme`, haptic ownership; layout, theme, contrast and golden refinements; card fit, keys on cards, TalkBack completeness; store and legal checks with opaque PNGs; Codex heading, PR lifecycle, roles, GitHub API operations, review rounds and escalation; milestone refinements |
| TC-1–TC-7 | Technical corrections (Appendix B) | keytool escaping; aapt2/apksigner spellings; Lint issue id; wrapper bootstrap; detekt path; kotest seeding; nudge settle time |

### 0.1 Decision index

| ID | Area | Decision |
|---|---|---|
| D-001 | Scope | Full native Kotlin + Compose + M3 rewrite. WebView, `assets/2048` submodule and legacy build are deleted in M0 |
| D-002 | Scope | Offline. The only merged permission is androidx.core's signature self-permission. No ads, analytics or GMS |
| D-003 | Scope | v1.0 = the "In v1.0" rows of the scope table (§ Product scope) |
| D-004 | Scope | Explicit non-goals list (store signing, network, notifications, widgets, …) |
| D-005 | Scope | No legacy data import. The new package installs side by side with `com.uberspot.a2048` |
| D-006 | Scope | Canonical settings list and defaults (11 settings + Reset statistics) |
| D-007 | Scope | New game confirms only when a game is in progress. A size chip switches between per-size saved games |
| D-008 | Scope | Win card (Keep going / New game / Undo) and game-over card (New game / Undo / Share) |
| D-009 | Scope | One-time first-launch hint, How to play, What's new only after an update, About |
| D-010 | Toolchain | Gradle wrapper 9.7.1-bin, sha256 `acd53f1e…d20a`, `validateDistributionUrl=true` |
| D-011 | Toolchain | AGP 9.4.1 with built-in Kotlin. Kotlin 2.4.21 plugins; never `org.jetbrains.kotlin.android` |
| D-012 | Toolchain | Compose BOM 2026.09.00 with material3 pinned to 1.5.0-beta02; no experimental-expressive opt-in |
| D-013 | Toolchain | compileSdk 37, targetSdk 37, minSdk 24 |
| D-014 | Toolchain | JDK 21 runs the build; bytecode 17 |
| D-015 | Toolchain | Repositories `google()` + `mavenCentral()` only, plugins included; `FAIL_ON_PROJECT_REPOS` |
| D-016 | Toolchain | Exact version catalog (TOML in D-016) |
| D-017 | Toolchain | Kotlin DSL, root `plugins {}` aliases, smoke-build `gradle.properties`, no convention plugins |
| D-018 | Toolchain | No `buildToolsVersion` pin (AGP default 36.0.0). Inspection scripts use build-tools 37.0.0 |
| D-019 | Toolchain | The Maven-mirror and Robolectric-repo overrides are local-only hooks, never committed values |
| D-020 | Architecture | Modules `:app` + `:core:engine` only |
| D-021 | Architecture | Engine uses the Kotlin stdlib only. No serialization, Android, clock or platform RNG; enforced by a test |
| D-022 | Architecture | Manual DI through `AppContainer`. No Hilt, KSP or kapt |
| D-023 | Architecture | Single activity; Navigation 3 with 6 `@Serializable` keys; overlays are not destinations |
| D-024 | Architecture | State-first `GameViewModel`; moves applied synchronously; a conflated persistence writer |
| D-025 | Architecture | Per-tile composables keyed by app-assigned IDs; per-frame reads only in layout/draw phases |
| D-026 | Architecture | M3 1.5-only APIs confined to `ui/theme` and `ui/components`; `currentWindowAdaptiveInfoV2()`; Material Symbols XML |
| D-030 | Engine | Board N∈{3..8}: row-major `ByteArray` of exponents; score `Long` |
| D-031 | Engine | Direction codes Up 0, Right 1, Down 2, Left 3 |
| D-032 | Engine | Leading-wall traversal, merge-once, legacy golden rows |
| D-033 | Engine | Score += each merged value. No bonuses |
| D-034 | Engine | Spawn only after an accepted move: value first (1/10 → 4), then a uniform row-major empty cell; 2 start tiles |
| D-035 | Engine | A no-op move consumes nothing (no RNG, history, score or count) |
| D-036 | Engine | Targets: 3×3 = 256, else 2048. Statuses PLAYING/WON/KEEP_PLAYING/OVER; win announced once |
| D-037 | Engine | Game over after the spawn when no empty cell and no equal orthogonal pair remain |
| D-038 | Engine | App-owned SplitMix64 with golden vectors and unbiased bounded draws |
| D-039 | Engine | Unlimited undo: 2-bit move log + snapshot every 32 moves; RNG restored; works after game over; no redo |
| D-040 | Data | Typed JSON DataStores: `settings.json`, `records.json`, `game-3.json` … `game-8.json` |
| D-041 | Data | `schemaVersion` + lenient `Json` + committed v1 fixtures + `DataMigration` for breaking changes |
| D-042 | Data | Exact document fields; Base64 for cells, the log and binary snapshots |
| D-043 | Data | Persist after every accepted move, undo, keep-going and new game; conflated, never cancelled mid-write |
| D-044 | Data | Per-size records. Bests never decrease (except Reset). Games finalize on New game |
| D-045 | Data | A corrupt file falls back to that file's defaults only. Never crash |
| D-046 | Data | Budgets: 8×8 game with 10k moves ≤ 256 KiB and ≤ 50 ms encode+decode; records ≤ 64 KiB |
| D-047 | Data | The UI is a pure function of persisted state; no replay on resume or recreate; JVM process-death test |
| D-048 | Data | Auto Backup and D2D include `datastore/`; both rule XMLs present and tested |
| D-050 | Input | Default: commit during the drag at 24 dp with dominant ratio ≥ 1.4. "On release" setting; fling ≥ 600 dp/s |
| D-051 | Input | Swipe anywhere on the game content; a swipe that starts on a button never triggers it; board inset from gesture insets |
| D-052 | Input | No input queue: every accepted move applies synchronously; in-flight slides snap |
| D-053 | Input | Arrows/WASD/HJKL/D-pad move; U, Ctrl+Z, Backspace undo; N/R new game; auto-repeat ≤ 1 per 120 ms |
| D-054 | Input | Overlays are modal; Back dismisses only sheets, dialogs and sub-screens |
| D-060 | Motion | `GameMotion` token object with the M3 spring values; independent of `MaterialTheme.motionScheme` |
| D-061 | Motion | Move timeline: slide t=0, merge pop +80 ms, spawn +90 ms, ghosts removed +200 ms |
| D-062 | Motion | Interruption: snap slides, drop ghosts, let effects finish; at most 3 "+N" floats alive |
| D-063 | Motion | Motion: Full (default) / Reduced / Off. System animator scale 0 forces Off; no infinite animations |
| D-064 | Motion | Signature moments per research/04 §10, generalized to N×N |
| D-065 | Haptics | One haptic per move, by priority: win Confirm×2 > new best GestureThresholdActivate > Confirm (≥512) > SegmentTick; Reject for invalid moves and game over |
| D-066 | Haptics | `LocalHapticFeedback` only. No VIBRATE; never ignore the system setting |
| D-070 | Visual | Classic Refined (research/04 §8A) is the default, light + dark, following the system |
| D-071 | Visual | Theme System/Light/Dark × palette Classic Refined / Neon Arcade |
| D-072 | Visual | Distinct super tiles 4096 … ≥131072 (exact hexes in D-072, gates verified) |
| D-073 | Visual | High contrast: system contrast ≥ 0.5 (API 34+) or setting → black/white ink + 1.5 dp outline |
| D-074 | Visual | Dynamic color for app chrome only, opt-in, default Off, API 31+; dynamic tile ramp deferred |
| D-075 | Visual | Responsive N×N geometry: gap = r·tile (Classic 0.14, Neon 0.18); board 280–720 dp |
| D-076 | Visual | Google Sans Flex digit subset (≤ 32 KiB) for game numerals + title only; system font for all strings |
| D-077 | Visual | Numeral size by digit count; compact K/M/G… labels below 12 dp; full number in a11y text |
| D-078 | Visual | Classic glow on 128–2048; Neon fill-colored glows; flat otherwise |
| D-079 | Visual | Committed Material Symbols Rounded vectors; no `material-icons-*` |
| D-080 | A11y | Board: `CollectionInfo(N,N)`, stable cell nodes, custom actions Move ×4 / Undo / New game |
| D-081 | A11y | Polite live-region status line; assertive win/game-over with `paneTitle` + focus |
| D-082 | A11y | Direction pad auto-shown when touch exploration is on |
| D-083 | A11y | Contrast: tile ink ≥ 3.0 (1–3 digits) / ≥ 4.5 (≥ 4 digits); CVD ΔE gates; `PaletteContrastTest` |
| D-084 | A11y | Touch targets ≥ 48 dp; chrome survives 200 % font scale; visible focus ≥ 3:1 |
| D-085 | A11y | ATF checks (Roborazzi, LATEST preset, Error level) on every screen golden |
| D-090 | Layout | No orientation lock; `appCategory="game"`; no `configChanges`; touchscreen not required |
| D-091 | Layout | Modes: height < 480 → 3-column; width ≥ 840 → board + side panel; else stacked. Postures deferred |
| D-092 | Layout | `currentWindowAdaptiveInfoV2()`; Robolectric qualifier matrix |
| D-093 | Layout | Always edge-to-edge; "Immersive mode" setting hides the system bars (default Off) |
| D-094 | Layout | Predictive back: never intercept Back on the Game root; no double-back |
| D-095 | Layout | Recreation never replays tile animations; mode changes use `animateBounds` under Full motion |
| D-100 | i18n | en + 20 legacy locales, fully translated before merge; `MissingTranslation` = error |
| D-101 | i18n | Legacy strings: keep `settings`, drop the back/fullscreen hints, strip the cs BOM, remove every locale `app_name` |
| D-102 | i18n | `generateLocaleConfig`; a Settings row opens the system per-app language page (API 33+); no AppCompat |
| D-103 | i18n | Plurals for every count; Latin digits; prose gets pre-formatted `%1$s` arguments |
| D-104 | i18n | `supportsRtl=true`; board, gestures, key map and move actions forced LTR; chrome mirrors |
| D-105 | i18n | Agent-translated with a fixed glossary; README discloses machine assistance |
| D-106 | i18n | Pseudo-locales `en-XA`/`ar-XB` in debug for goldens |
| D-110 | Identity | `com.clayboicardi.game2048`, label "2048"; debug `.debug` and "2048 Debug" |
| D-111 | Identity | Adaptive + monochrome icon: legacy glyphs `#3A2900` on `#E6A11A` |
| D-112 | Identity | core-splashscreen 1.2.0, held until persisted state loads (cap 1000 ms) |
| D-113 | Identity | `fastlane/metadata/android/<22 locales>/`; delete 16 listing-only locales |
| D-114 | Identity | Store text rules; `changelogs/30000.txt` |
| D-115 | Identity | Store graphics rendered from code; opaque feature graphic; 1080×1920 phone shots |
| D-116 | Identity | README rewritten (no foreign badges; side-by-side note; privacy link) |
| D-120 | Legal | LICENSE (MIT): Sarbinowski 2014 + Cirulli 2014 + clayboicardi 2026 |
| D-121 | Legal | `NOTICE` with every attribution |
| D-122 | Legal | In-app licenses from a hand-curated JSON; CI checks coverage of the runtime classpath |
| D-123 | Legal | `PRIVACY.md` + localized in-app privacy text: collects nothing, no network |
| D-124 | Legal | About lists credits and the legacy release history (static English data) |
| D-130 | Signing | versionName `3.0.0` / versionCode `30000`, both literals |
| D-131 | Signing | Committed, intentionally public CI sideload keystore signs debug and release |
| D-132 | Signing | debug: `.debug`, not minified. release: R8 + shrinkResources (smoke-verified DSL) |
| D-133 | Signing | No release workflow, tags or store uploads in this overhaul; CI artifacts only |
| D-134 | Signing | Reproducibility: `dependenciesInfo` off, no BuildConfig, no named companions on `@Serializable` |
| D-140 | Quality | `scripts/verify.sh` is the single gate; CI runs exactly it |
| D-141 | Quality | Spotless 8.10.4 + ktfmt 0.65 kotlinlang style (fallback 0.64) |
| D-142 | Quality | Lint `warningsAsErrors`, no baseline, Slack compose-lint-checks 1.6.1 |
| D-143 | Quality | Kotlin `allWarningsAsErrors` in both modules; no opt-ins outside the files of D-174 and D-181 |
| D-144 | Quality | detekt 2.0.0-alpha.6 for generic rules; dropped if it crashes |
| D-145 | Quality | Engine: JUnit 6 + kotest-property; golden + 13 properties; Kover ≥ 90 % line / 85 % branch |
| D-146 | Quality | App: JUnit 4 + Robolectric 4.17 (sdk 36, smoke at 24/37) + Compose ui-test v2 + Turbine |
| D-147 | Quality | Roborazzi goldens committed; record locally, verify in CI; every changed PNG Read by the agent |
| D-148 | Quality | APK gates: permission and exported-component allowlists, ≤ 3 MiB, 16 KB alignment, signer, deny-list |
| D-149 | Quality | Test-count floor per module |
| D-150 | Quality | JVM-checkable performance budgets, including zero tile recompositions during a slide |
| D-151 | Quality | `ci.yml`: one job on `ubuntu-24.04`, SHA-pinned actions, uploads both APKs |
| D-152 | Quality | Metadata, font-size and license-coverage checks |
| D-153 | Quality | Not gated: instrumented tests, macrobenchmark, baseline profiles, dependency verification, Dependabot |
| D-160 | Process | Two PRs: the plan PR (docs, `ci.yml`, `AGENTS.md`, `CLAUDE.md`; D-177), then one execution PR |
| D-161 | Process | Execution branch `claude/2048-modernization-plan-8eneix`, re-cut from `master` after the plan merges |
| D-162 | Process | Milestones M0–M6, one or more commits each |
| D-163 | Process | `scripts/verify.sh` passes locally before every push |
| D-164 | Process | Commit message format `M<n>: …` / `review(r<k>): …`; no history rewrite after the PR opens |
| D-165 | Process | Fork-safe PRs (`clayboicardi/2048-android-modern-refresh`, base `master`) via the GitHub API; PR body contents |
| D-166 | Process | `@codex review` after the M2 push, after the M6 push, and after every fix push |
| D-167 | Process | Clean = 👍 from `chatgpt-codex-connector[bot]`, or a review or comment with no new findings, on the head SHA (D-196) |
| D-168 | Process | Merge gate: Codex clean + CI green on the head SHA. Valve after 5 rounds |
| D-169 | Process | Every finding gets a fix or a reasoned reply; fixed threads are resolved |
| D-170 | Process | Merge method: plan PR squash; execution PR merge commit |
| D-171 | Process | Root `AGENTS.md` + `core/engine/AGENTS.md` with `## Code Review Rules` (D-192) |
| D-172 | Process | Stop conditions and blocker reporting |
| D-173 | Process | Change control for this file |
| D-174 | Quality | ATF opt-in only in `testing/Accessibility.kt` |
| D-175 | Quality | Spotless excludes `docs/**` |
| D-176 | Quality | Golden, license-list and engine-source test inputs; test results never cached |
| D-177 | Process | Plan PR = docs + `ci.yml` + root `AGENTS.md` + `CLAUDE.md` |
| D-178 | Quality | `verify.sh` levels; one local build at a time (flock); detached, logged runs |
| D-179 | Quality | M0 check-script additions (floors, orphans, BOM, icon glyphs, opt-in allowlist, …) |
| D-180 | Process | No AI model names in tracked files; trailers and footers verbatim |
| D-181 | Quality | `ExperimentalMaterial3Api` opt-in only in `GameModalSheet.kt` |
| D-182 | Architecture | Epoch boards are `Static`; outgoing-tiles layer; undo keeps unchanged ids |
| D-183 | Motion | Origin time base; `MoveTimeline` + `delayedSpec`; spawn-delay table; per-tile pop; flash limiter; deflate step; 0.94 end state; frame-rate vote |
| D-184 | Motion | Level-dependent `MotionScheme`; dialogs and snackbars keep platform fades |
| D-185 | Motion | `HapticsMap` owns priority; scheduling and interruption; no invalid-move haptic below API 30 |
| D-186 | Scope | Undo snackbar until the first finalized game with undo; never over the controls |
| D-187 | Motion | Two effect canvases; tile-local sheen; "+N" pill |
| D-188 | Layout | Per-axis margins, measured chrome, fit guard, hint and side-panel placement |
| D-189 | Visual | Neon light surface; `TileSkin.titleColor` |
| D-190 | Store | M5 listing, image and legal checks; `OpaquePngWriter`; title ≤ 30 |
| D-191 | Quality | Report-only reproducibility probe |
| D-192 | Process | Review rules under `## Code Review Rules`; Codex reports P0/P1 only |
| D-193 | Process | Execution PR ready for review from M0; push gate; automatic reviews handled |
| D-194 | Process | Orchestrator vs implementer roles; push handoff |
| D-195 | Process | GitHub steps via the GitHub API (MCP or gh); no admin calls |
| D-196 | Process | Rounds, clean signal, resolved P0/P1, valve, round-10 escalation, owner ledger |
| D-197 | Process | Branch re-cut with a merged-content check |
| D-198 | Data | Streak and win readings; Statistics shows a finished, unfinalized game |
| D-199 | Input | Chrome and U/N/R keys act while a card is shown |
| D-200 | Scope | Cards fit the board; enabled-color buttons with gated interactivity; Share on the win card |
| D-201 | Scope | Hint shown while the starting game has no move |
| D-202 | Accessibility | Status line announces spawn, undo, new game and size switch; TalkBack hint text |
| D-203 | Input | System keyboard-shortcuts helper |
| D-204 | Quality | 45 goldens; filmstrip frame names; ATF scope |
| D-205 | Accessibility | Unquantized ΔE; `assertTouchTargets()` gate |
| D-206 | Visual | High-contrast empty-cell stroke |
| D-207 | Visual | Classic dark 2-tile `#524A42` |
| D-208 | Localization | Word-free strings untranslatable; no games plural; appcompat-resources allowed; `strings_pending.xml` |
| D-209 | Legal | "gurshabad" credited |
| D-210 | Quality | Motion verification: binding event frames, library-derived curve values |
| D-211 | Process | Milestone refinements: M2 presenter, M3 in two pushes, M4/M5 goldens, per-milestone AC audit |

---

## 1. Legacy behavior disposition

Every row of the two parity checklists, research/01-legacy-android-shell.md §9 (shell) and research/02-legacy-game.md §9 (game), appears below exactly once. Duplicates between the two lists are merged into one row.

### 1.1 Shell (research/01 §9)

| # | Legacy behavior | Disposition | D-ID |
|---|---|---|---|
| S1 | Launches straight into the game; no title or action bar | **Keep.** `GameKey` is the start destination with no top app bar | D-023 |
| S2 | `FLAG_HARDWARE_ACCELERATED` set by hand | **Drop** (it is the default) | D-001 |
| S3 | Long press > 2000 ms toggles fullscreen | **Replace with** the Settings switch "Immersive mode". The gesture is gone | D-093 |
| S4 | Persisted `is_fullscreen_pref` (inverted naming, default "bars shown") | **Replace with** `immersive: Boolean = false` in `settings.json`. No migration | D-006, D-093 |
| S5 | Toast "Long click to toggle fullscreen" on every `onCreate` | **Drop.** Replaced by a one-time first-launch hint | D-009 |
| S6 | Double-back-to-exit with a 3500 ms window | **Drop.** Predictive back goes home | D-094 |
| S7 | Manifest `screenOrientation="portrait"` | **Replace with** no lock plus adaptive layouts | D-090, D-091 |
| S8 | Sensor rotation only on large/xlarge screens with auto-rotate on | **Replace with** system rotation, which honors the user's lock | D-090 |
| S9 | `configChanges="orientation\|keyboardHidden\|screenSize"` | **Replace with** normal recreation; state lives in the ViewModel and DataStore | D-090, D-095 |
| S10 | ckChangeLog dialog on first run and on upgrade | **Replace with** a "What's new" sheet shown only after an update, never on a fresh install | D-009 |
| S11 | `res/raw/changelog_master.xml` history | **Keep as data:** About → Legacy history | D-124 |
| S12 | WebView host with JS, DOM storage and WebSQL | **Drop** | D-001 |
| S13 | `setRenderPriority` / `setDatabasePath` | **Drop** | D-001 |
| S14 | `WebView.saveState` / `restoreState` | **Replace with** DataStore for game state, plus Nav3 back stack / `rememberSaveable` for transient UI | D-047 |
| S15 | `onResume()` reload of `index.html` | **Drop.** Never reload or replay on resume | D-047, D-095 |
| S16 | Locale passed via `?lang=` | **Replace with** Android resources + `localeConfig` | D-102 |
| S17 | localStorage `bestScore` / `gameState` / `gameStatesStack` | **Replace with** typed DataStore documents. No import | D-040, D-005 |
| S18 | Night-mode button (web, not persisted) | **Replace with** a persisted Theme setting: System / Light / Dark | D-071 |
| S19 | Shell strings × 20 locales | **Keep partially:** `settings` and the non-translatable `app_name`; drop the 2 hints | D-101 |
| S20 | Web i18n (en/ru/uk) plus CSS "Score"/"Best" | **Keep (migrate)** into `strings.xml` for all 21 locales | D-100, D-101 |
| S21 | Non-adaptive `ic_2048` vector | **Replace with** an adaptive + monochrome icon that reuses the glyph paths | D-111 |
| S22 | `allowBackup="true"` with no rules | **Keep, tightened:** explicit rules including `datastore/` | D-048 |
| S23 | Zero `<uses-permission>` | **Keep.** Only the signature self-permission is allowed, and CI gates it | D-002, D-148 |
| S24 | `android:Theme.Light` (API 1) | **Replace with** an M3 theme | D-070 |
| S25 | Unused dimens, including the `sw720dp-land` 128 dp margin | **Drop.** Only the idea of wide gutters survives, in the side-panel layout | D-091 |
| S26 | Travis CI + Gradle 4.10.3 + AGP 3.2.1 | **Replace with** GitHub Actions + Gradle 9.7.1 + AGP 9.4.1 | D-010, D-011, D-151 |
| S27 | Submodule `assets/2048` | **Drop** (M0) | D-001, D-162 |
| S28 | `metadata/<locale>/` store listing | **Replace (restructure)** as `fastlane/metadata/android/<locale>/` | D-113 |
| S29 | MIT LICENSE (Sarbinowski) + game MIT (Cirulli) | **Keep.** LICENSE carries both notices, plus NOTICE and an in-app screen | D-120–D-122 |

### 1.2 Game (research/02 §9)

| # | Legacy behavior | Disposition | D-ID |
|---|---|---|---|
| G1 | 4×4 grid with 2 start tiles | **Keep and generalize:** sizes 3×3–8×8, default 4×4, 2 start tiles | D-030, D-034 |
| G2 | Spawn 2 at 90 % / 4 at 10 % in a uniform empty cell, only after an effective move | **Keep exactly,** using the app-owned PRNG | D-034, D-038 |
| G3 | Leading-edge traversal; one merge per tile per move | **Keep;** legacy rows become golden tests | D-032 |
| G4 | Score = sum of merged values | **Keep** | D-033 |
| G5 | Win at the first exact 2048; overlay; Keep going; win + game over edge case | **Keep,** with a per-size target (3×3 = 256) | D-036 |
| G6 | Input locked until Keep going | **Replace with** a modal win card whose buttons work from the end of its entry animation; Undo is available | D-008, D-054 |
| G7 | No-op move: no spawn, save or history | **Keep,** plus a nudge and a rate-limited Reject haptic | D-035, D-064, D-065 |
| G8 | Game over = no empty cell and no equal neighbour | **Keep** | D-037 |
| G9 | Best score persists forever and never decreases | **Keep,** per board size (`Long`); only an explicit Reset clears it | D-044 |
| G10 | Auto-save on every move; resume on launch | **Keep,** with DataStore and the full undo history | D-043 |
| G11 | Save wiped on game over | **Replace with** a persisted final board; undo works from game over | D-039, D-043 |
| G12 | Undo capped at 50 levels in localStorage | **Replace with** unlimited log-based undo | D-039 |
| G13 | Restart confirmation dialog | **Keep:** an M3 dialog shown only when a game is in progress. Cancel changes nothing | D-007 |
| G14 | Keep going / Try again overlay buttons | **Replace with** the win card (Keep going / New game / Undo) and the game-over card (New game / Undo / Share) | D-008 |
| G15 | Tile palette 2→2048 hexes | **Replace with** Classic Refined (same identity, fixed contrast) | D-070 |
| G16 | One `#3c3a32` for every tile above 2048 | **Replace with** a distinct super-tile ramp | D-072 |
| G17 | Gold glow on 128–2048 | **Keep (refined):** alphas at 0.8 × legacy | D-078 |
| G18 | Fixed 500/280 px board, 15/10 px gap, 3/6 px radii | **Replace with** responsive N×N geometry | D-075 |
| G19 | Clear Sans webfont | **Replace with** a Google Sans Flex digit subset plus the system font | D-076 |
| G20 | Font-size ladder 55/45/35/30 (mobile 35/25/15/10) | **Replace with** digit-count sizing plus compact labels | D-077 |
| G21 | 100 ms slide, 200 ms appear/pop, 600 ms "+N", 800 ms overlay after 1200 ms | **Replace with** spring tokens. The move budget stays about the same (~400 ms settle) and overlays become interactive sooner | D-060–D-064 |
| G22 | Appear replay on resume or undo; "+whole score" on load | **Drop** | D-025, D-047 |
| G23 | Arrow / WASD / HJKL keys; R restarts immediately | **Keep and extend:** D-pad, U / Ctrl+Z / Backspace undo, N/R new game, which now asks for confirmation when a game is in progress | D-053 |
| G24 | IE10 `MSPointer` branch and polyfills | **Drop** | D-001 |
| G25 | Swipe on `.container`, > 10 CSS px, fires on lift | **Replace with** a dp threshold; commit during the drag by default, with an "On release" option | D-050, D-051 |
| G26 | Long-press fullscreen (shell side) | **Drop** (see S3) | D-093 |
| G27 | Double-back toast (shell side) | **Drop** (see S6) | D-094 |
| G28 | Night-mode button | **Replace** (see S18) | D-071 |
| G29 | i18n en/ru/uk with 11 keys; Score/Best not localized | **Replace with** `strings.xml` in 21 locales | D-100 |
| G30 | `values-fa` `app_name` = "۲۰۴۸" | **Drop.** The label is "2048" everywhere | D-101, D-110 |
| G31 | localStorage schema | **Drop** (see S17) | D-040 |
| G32 | MIT attribution to Gabriele Cirulli 2014 | **Keep** | D-120, D-121 |
| G33 | No ads or analytics; zero permissions | **Keep** | D-002 |

### 1.3 Legacy bugs (research/02 §8, B1–B21)

| Bug | Fixed by |
|---|---|
| B1 Undo after "undo, then move" skips a state | D-039. The log is truncated on undo, and `EnginePropertiesTest` asserts `undo(move(s)) == s` |
| B2 Undo is not persisted | D-043. Undo is persisted like a move |
| B3 First undo after a reload does nothing | D-039, D-047. Restoring appends nothing to the history |
| B4 Undo after game over soft-locks the game | D-039. Undo from `OVER` restores a playable state |
| B5 "Cancel" sets keepPlaying | D-007. Cancel is a pure dismissal |
| B6 `keepPlaying` field shadows the method | Not applicable (the JS is gone) |
| B7 "+whole score" float on load | D-025, D-047. Restored boards render `Static` |
| B8 Appear animation replays on load and undo | D-025, D-064. Undo crossfades; restore is static |
| B9 Game-over board lost on resume | D-043. The `OVER` state is persisted |
| B10 Night mode broken and not persisted | D-071 |
| B11 A swipe starting on a button activates the button | D-051 |
| B12 R and "Try again" restart without confirmation | D-007, D-053 |
| B13 10 px threshold, fires only on lift | D-050 |
| B14 Long-press collides with slow swipes | D-093 (the gesture is removed) |
| B15 Unreadable 10–15 px super-tile text; one super color | D-072, D-077 |
| B16 White-on-gold contrast 1.4–1.7:1 | D-070, D-083 |
| B17 Score/Best not localized | D-100 |
| B18 Fixed 280 px board on phones | D-075 |
| B19 Overlay corners bleed past the board radius | D-008 (the scrim is clipped to the board shape) |
| B20 O(stack) JSON parse and stringify on every move | D-042, D-046 |
| B21 `nightmode.js` clobbers `window.onload` | Not applicable |

---

## 2. Product scope & non-goals

#### D-001 — Full native rewrite
- **Decision:**
  - The app becomes native Kotlin + Jetpack Compose + Material 3.
  - Milestone M0 deletes all of the following:
    - the `assets/2048` submodule (and `.gitmodules`);
    - `src/com/uberspot/a2048/MainActivity.java`;
    - the root `AndroidManifest.xml` and `build.gradle`;
    - the legacy `gradlew*` scripts and wrapper;
    - `.travis.yml`;
    - `res/layout`, `res/menu`, `res/values/styles.xml`, `res/values/dimens.xml` and `res/values-sw720dp-land`;
    - the rest of the root `res/` tree (`res/drawable/ic_2048.xml`, `res/raw/changelog_master.xml`). This happens only after the strings are moved (D-101), the glyph paths are reused in the new icon (D-111), and the changelog and credits are captured (D-124).
  - No WebView and no web code ship.
- **Rationale:**
  - The legacy project cannot build: Gradle 4.10.3 cannot run on JDK 21, jcenter and OSSRH are gone, and the only dependency returns 404 (research/01-legacy-android-shell.md §0.4, §2).
  - The WebView is the root of the resume and undo defects (research/01 §4.9; research/02-legacy-game.md §8).
- **Rejected:** a build-fix PR (no viable path, research/01 §0.4); keeping the JS game inside a modernized WebView.
- **Source:** [USER] U1.

#### D-002 — Offline, permission-free, no third-party services
- **Decision:**
  - The app has no `INTERNET`, no runtime or dangerous permissions, no `VIBRATE`, and no analytics, ads, crash reporting, Google Play Services, Firebase, Play Core, UMP or downloadable fonts.
  - The single permission allowed in the merged manifest is androidx.core's `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (`protectionLevel=signature`). It is documented in README and PRIVACY.md and gated by D-148.
  - Two intents are allowed because they need no permission:
    - `ACTION_VIEW` to the source repository URL;
    - `ACTION_SEND` for the share text.
- **Rationale:**
  - The smoke release's merged manifest contains only that self-permission (research/03-toolchain.md §6, §9).
  - Removing it with `tools:node="remove"` would break `ContextCompat.registerReceiver` below API 33 (research/03 §9; research/07-critique.md C25).
  - Haptics need no permission (research/04-design-motion.md §5).
- **Rejected:** removing the self-permission; Glance widgets, which merge 4 permissions (research/05-features.md §4.9); `Vibrator`.
- **Source:** [USER] U6.

#### D-003 — v1.0 scope
- **Decision:**
  - v1.0, delivered by the single execution PR, contains exactly the rows marked **In v1.0** in the scope table below.
  - Everything else is deferred or never built.
  - Work outside the table needs a new decision under D-173.
- **Rationale:** research/05 §5 roadmap; research/07 §4 D3; U*/O* items.
- **Rejected:**
  - research/07 D3's v1.0 "Undo Off switch" and "local top-10". Both are deferred: no O* item requires them, the no-undo best (D-044) already gives honest scoring, and top-10 can be added later as a defaulted field.
  - research/05 tiering "What's new" as v1.x. O11 requires it in v1.0.
- **Source:** [ORCH] O7, O8, O11, O12 · [RESEARCH] research/05 §5.

**v1.0 scope table.** It covers every row of the research/05 §5 roadmap. Tiers:
- **In v1.0** ships in the execution PR.
- **Deferred (v1.x)** is the next increments.
- **Deferred (backlog)** is later than v1.x.
- **Never** conflicts with the constraints.

| # | Feature (research/05 §5) | Tier | Notes / D-ID |
|---|---|---|---|
| 1 | Classic rules parity | **In v1.0** | D-030–D-037 |
| 2 | Seeded app-owned PRNG | **In v1.0** | SplitMix64 only (D-038) |
| 3 | Per-game move log + snapshots | **In v1.0** | D-039, D-042 |
| 4 | Board sizes 3×3–8×8, per-size saves and bests, 3×3 target 256 | **In v1.0** | D-030, D-036, D-007, D-044 |
| 5 | Unlimited undo, no prompt, works after game over, counter, PRNG restore | **In v1.0** | D-039. The "Undo Off" switch is **Deferred (v1.x)** |
| 6 | Robust save/resume (every move, atomic, process death) | **In v1.0** | D-043, D-047 |
| 7 | Input model | **In v1.0, changed:** commit during the drag by default, no queue, no gesture exclusion | D-050–D-053 |
| 8 | Settings | **In v1.0, changed:** the D-006 list. No animation-speed, confirm-new-game, undo or HUD toggles | D-006 |
| 9 | System/Light/Dark + high contrast + distinct super tiles | **In v1.0** | D-071–D-073 |
| 10 | Haptics via `performHapticFeedback` | **In v1.0** | D-065, D-066 |
| 11 | Statistics schema + stats screen + local top-10 | **In v1.0** for schema and screen; top-10 **Deferred (v1.x)** | D-044 |
| 12 | Win and game-over cards with score, best tile, moves, time and undos; Keep going; text share | **In v1.0** | D-008 |
| 13 | Onboarding + How to play | **In v1.0, changed:** a one-time hint card instead of coach marks | D-009 |
| 14 | Accessibility | **In v1.0** | D-080–D-085 |
| 15 | Adaptive layouts | **In v1.0** for size classes; posture layouts **Deferred (v1.x)** | D-091 |
| 16 | Backup rules | **In v1.0** | D-048 |
| 17 | Per-app language + RTL + translations | **In v1.0, expanded** to all 20 locales | D-100–D-104 |
| 18 | CI permission gate | **In v1.0** | D-148 |
| 19 | About + OSS licences + source link | **In v1.0** (plus privacy) | D-009, D-122, D-123 |
| 20 | Daily challenge | Deferred (v1.x) | Needs only the v1.0 PRNG, log and records |
| 21 | History (last 100) + replay viewer | Deferred (v1.x) | |
| 22 | Sound effects (CC0, `SoundPool`, default off) | Deferred (v1.x) | |
| 23 | Local achievements | Deferred (v1.x) | |
| 24 | Hints (expectimax) | Deferred (v1.x) | |
| 25 | Image share card | Deferred (v1.x) | |
| 26 | Home-screen widget (RemoteViews, never Glance) | Deferred (v1.x) | |
| 27 | Static app shortcuts | Deferred (v1.x) | |
| 28 | Export/import data (SAF JSON) | Deferred (v1.x) | |
| 29 | Redo | Deferred (v1.x) | O8: no redo in v1.0 |
| 30 | Extra palettes, including the Material You dynamic tile ramp | Deferred (v1.x) | D-074 |
| 31 | "What's new" sheet | **In v1.0** (moved up from v1.x) | D-009 (O11) |
| 32 | Time trial / time attack | Deferred (backlog) | |
| 33 | Zen / limited moves / custom rules / spawn variants | Deferred (backlog) | |
| 34 | Power-ups, checkpoints, bookmarks | Deferred (backlog) | |
| 35 | AI autoplay | Deferred (backlog) | |
| 36 | Share/import whole games as files | Deferred (backlog) | |
| 37 | Wear OS, Android TV launcher entry | Deferred (backlog) | |
| 38 | Rectangular boards | **Never** | No surveyed app has them (research/05 §4.2) |
| 39 | Quick Settings tile | **Never** | research/05 §4.13 |
| 40 | Notifications / reminders | **Never** | Runtime `POST_NOTIFICATIONS` |
| 41 | Play Games, online leaderboards, cloud sync, ads, analytics | **Never** | INTERNET (U6) |
| 42 | Custom vibration patterns (`Vibrator`) | **Never** | Adds `VIBRATE` |
| 43 | Legacy best-score import | **Never** | D-005 |

Not in the research/05 roadmap but **In v1.0**:
- Neon Arcade palette (D-071);
- Immersive mode (D-093);
- Keep-screen-on (D-006);
- Move timing and swipe sensitivity settings (D-050);
- splash gating (D-112);
- privacy text (D-123);
- the "Best (no undo)" record (D-044).

#### D-004 — Non-goals
- **Decision.** The following are explicitly out of scope for the execution PR:
  1. Play Store or other store uploads, real release signing, a release workflow, git tags and GitHub Releases (U5; D-133).
  2. Any network use: ads, analytics, crash reporting, cloud sync, online leaderboards, Play Games Services, remote config, downloadable fonts (U6).
  3. Notifications, reminders, exact alarms, `WorkManager`, Glance widgets, `VIBRATE`.
  4. Migration or import of `com.uberspot.a2048` data (D-005).
  5. Rectangular boards; game modes other than Classic + Keep going.
  6. An in-app language picker on API < 33, and AppCompat (D-102).
  7. Hilt, Koin, KSP, kapt, Room, protobuf, extra Gradle modules, convention plugins (D-020, D-022).
  8. Wear OS and Android TV targets; foldable posture-specific layouts (D-091).
  9. Instrumented or emulator tests, macrobenchmarks or custom baseline profiles as merge gates (D-153).
  10. An `fdroiddata` submission. Renaming the default branch to `main`.
  11. Redo, sounds, history/replay, daily challenge, hints, achievements, widgets, shortcuts and export/import (all deferred, see the scope table).
- **Rationale:** U5 and U6 constraints; research/05-features.md §1 (permission traps), §5 (tiers); research/07-critique.md §4 D3.
- **Rejected:** shipping any deferred item in the execution PR. It widens the single PR that U7 requires to land hands-off.
- **Source:** [USER] U5, U6 · [ORCH] O2, O8 · [RESEARCH] research/05 §5, research/07 §4 D3.

#### D-005 — No legacy data import; side-by-side install
- **Decision:**
  - There is no code path that reads, imports or references `com.uberspot.a2048` data.
  - README and the store full description state: "This is a new app with a new package name (`com.clayboicardi.game2048`); the old app can stay installed side by side; scores do not carry over."
- **Rationale:**
  - The legacy data is WebView LevelDB inside another app's private sandbox, signed with keys we do not hold (research/02 §2 "Migration"; research/05 §4.20).
- **Rejected:** a manual best-score entry UI (it can be faked; research/05 §4.20).
- **Source:** [RESEARCH].

#### D-006 — Canonical v1.0 settings
- **Decision:**
  - The Settings screen contains exactly these rows, in this order. Defaults are in bold.
  - All values persist in `settings.json` (D-042).

| Setting | Values | Notes | D-ID |
|---|---|---|---|
| Theme | **System** / Light / Dark | | D-071 |
| Palette | **Classic Refined** / Neon Arcade | Each option shows a swatch row of tiles 2, 8, 128 and 2048 | D-071 |
| High-contrast tiles | **Off** / On | Forced On, with an explanatory summary line and the switch disabled, while system contrast ≥ 0.5 (API 34+) | D-073 |
| Dynamic color (app chrome) | **Off** / On | Shown only on API 31+ | D-074 |
| Motion | **Full** / Reduced / Off | Shows "Off (system animations are off)" and is disabled when the animator scale is 0 | D-063 |
| Haptics | **On** / Off | | D-065 |
| Move timing | **As you swipe** / When you lift your finger | | D-050 |
| Swipe sensitivity | Low (40 dp) / **Medium (24 dp)** / High (12 dp) | | D-050 |
| Immersive mode | **Off** / On | "Hide the status and navigation bars" | D-093 |
| Keep screen on while playing | **Off** / On | Applied only while the Game destination is visible and a game is in progress | — |
| App language | Opens the system per-app language page | Shown only on API 33+ | D-102 |

- **Reset statistics** lives in the Statistics screen's top bar (D-044), not in Settings.
- Not settings in v1.0:
  - new-game confirmation (always on, D-007);
  - an Undo on/off switch (v1.x);
  - an animation-speed multiplier (replaced by Motion);
  - sound (v1.x);
  - a moves/time HUD (v1.x).
- **Rationale:** research/05 §4.14 (setting set and defaults); research/07 C14 (one motion setting); O3–O6, O11.
- **Rejected:**
  - research/05's "Animation speed" (it quadruples the test matrix; research/07 C14);
  - research/05's "Confirm new game" toggle (O12 makes confirmation unconditional);
  - research/04 §5's "Haptics: Off/Subtle/Full" (O5).
- **Source:** [ORCH] O3, O4, O5, O6, O11 · [RESEARCH] research/05 §4.14.

#### D-007 — New game, confirmation, board-size switching
- **Decision.** Entry points:
  - **New game** comes from the button, the N or R key, the TalkBack "New game" action, or the cards in D-008.
    - If the current game is *in progress* (`moveCount ≥ 1` and status `PLAYING` or `KEEP_PLAYING`), an M3 `AlertDialog` asks "Start a new game?" with the buttons "New game" and "Cancel".
    - Cancel, Back and tapping outside dismiss the dialog with **no state change** (this fixes B5).
    - From the win card (status `WON`) and the game-over card (status `OVER`), New game starts immediately, because the card is the decision point.
    - A new game first finalizes the old one (D-044), then calls `Game.newGame(size, seed)` with a fresh seed (D-038).
  - **Board size** is a chip in the game header labeled "N×N", for example "4×4".
    - It opens a `ModalBottomSheet` listing 3×3 to 8×8. Each row shows that size's best score and an "In progress" label when a saved game is in progress.
    - Selecting a size switches to that size's saved game, whatever its status. If none exists, a new one starts. No confirmation is shown, because nothing is lost.
    - The current size persists as `lastBoardSize` (default 4).
- **Rationale:**
  - research/02 B5, B12 (inconsistent and state-mutating confirmations).
  - research/05 §4.2 (one in-progress game per size); §9 ("confirmation only where it protects work").
- **Rejected:** a size picker inside the new-game flow, which would ask twice; confirming switches between sizes.
- **Source:** [ORCH] O7, O12 · [RESEARCH].
- **Note:** Clarified by D-199 (New game from cards).

#### D-008 — Win and game-over cards
- **Decision.** Both cards are M3 cards centered over the board on a scrim. The scrim is clipped to the board's rounded shape (this fixes B19).
- **Win card** (status `WON`):
  - Content: title "You win!" and a hero badge showing the target value (2048, or 256 on 3×3).
  - Buttons: **Keep going** (filled), **New game** (tonal), **Undo** (text).
  - Buttons are enabled when the entry animation ends: T0 + 800 ms under Full motion, 150 ms under Reduced, immediately under Off (D-064).
  - The card never locks input longer than that.
  - Keep going → `Game.keepGoing()` (D-036). If the board has no moves left, the game-over card follows at once.
- **Game-over card** (status `OVER`):
  - Content: title "Game over", plus score, best score for this size, best tile, moves, time and undos used (plurals, D-103).
  - Buttons: **New game** (filled), **Undo** (tonal), **Share** (icon + text).
- **Share** sends `ACTION_SEND` (`text/plain`) through `Intent.createChooser`. The text is:
  - a localized summary line, "2048 · {N}×{N} · {score} · best tile {tile} · {moves} · {time} · {undos}", with Latin digits;
  - followed by an N-line emoji grid with this mapping: empty ⬜, ≤ 8 🟫, 16–64 🟨, 128–256 🟧, 512 🟥, 1024 🟪, 2048 🟦, ≥ 4096 ⬛.
- **Rationale:**
  - research/02 §9 ("allow undo from" the win overlay; keep the final board).
  - research/05 §4.1 (undo count on the game-over screen), §4.8 (text share, no permission).
  - research/04 §10.9–10.10 (timings).
- **Rejected:** the legacy 2.0 s overlay delay; the image share card (v1.x).
- **Source:** [ORCH] O8, O12 · [RESEARCH] research/05 §4.8.
- **Note:** Extended by D-200.

#### D-009 — First launch, How to play, What's new, About
- **Decision.**
- **First launch:** a dismissible hint card appears under the condition of D-201, in the place D-188 gives it.
  - Text: "Swipe to move all tiles. Equal tiles merge. Reach 2048." On 3×3 it says 256.
  - It disappears after the first accepted move or a tap on "Got it", then sets `onboardingDone = true`. It is never shown again.
- **Note:** versionCode 30000 is the first for this appId, so the What's new sheet cannot appear in v1.0. `WhatsNewTest` covers the update path.
- **How to play** (`HowToPlayKey`) covers:
  - the rules;
  - the 90/10 spawn;
  - per-size targets;
  - undo semantics, including "undo restores the same next tile";
  - the keyboard keys from D-053.
- **What's new:**
  - `settings.json` stores `lastSeenVersionCode` (default 0). On start:
    - if it is 0 (fresh install), store the current versionCode and show nothing;
    - if it is lower than the current versionCode, show a non-blocking `ModalBottomSheet` with the localized entries for versionCodes > `lastSeenVersionCode`, then store the current one.
  - Entries live in `app/src/main/kotlin/com/clayboicardi/game2048/about/Changelog.kt` as `versionCode → List<@StringRes Int>`.
  - v1.0 ships the entry for 30000. A unit test covers the update path with a faked `lastSeenVersionCode`.
- **About** (`AboutKey`) contains:
  - version "3.0.0 (30000)";
  - What's new history;
  - source link `https://github.com/clayboicardi/2048-android-modern-refresh` via `ACTION_VIEW`. If no browser exists, show a snackbar; never crash;
  - privacy text (D-123);
  - a link to `LicensesKey`;
  - credits and legacy history (D-124).
- **Rationale:**
  - research/01 §4.6, §9 #10 (the changelog dialog must never show on a fresh install).
  - research/05 §4.12 (lightweight onboarding).
  - O11.
- **Rejected:** research/07 G16 "no What's new UI in v1.0" (O11 overrides it); a multi-page onboarding carousel; coach marks (more UI for the same message).
- **Source:** [ORCH] O11 · [RESEARCH] research/05 §4.12.
- **Note:** Amended by D-201; clarified by D-188.

---

## 3. Platform & toolchain

#### D-010 — Gradle wrapper 9.7.1
- **Decision:**
  - `gradle/wrapper/gradle-wrapper.properties` is exactly the smoke-build file:
    - `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip`
    - `distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`
    - `validateDistributionUrl=true`
    - `networkTimeout=10000`
  - Bootstrap the wrapper with a locally installed Gradle 8.14.3. Run the bootstrap in a new, empty temporary directory outside the repository:
    1. `touch settings.gradle.kts`;
    2. then `gradle wrapper --gradle-version 9.7.1 --distribution-type bin --gradle-distribution-sha256-sum acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`;
    3. then `./gradlew wrapper` with the same arguments in that same directory, so the JAR comes from 9.7.1;
    4. copy `gradlew`, `gradlew.bat` and `gradle/wrapper/*` into the repository, but not the temporary `settings.gradle.kts`.

    The resulting `gradle-wrapper.properties` is byte-identical to the file above (technical correction TC-4).
  - Commit `gradlew` as executable (`git update-index --chmod=+x gradlew`).
- **Rationale:**
  - AGP 9.4 needs Gradle ≥ 9.6.0.
  - With 9.7.1 the smoke build produced zero deprecations and reused the configuration cache (research/03 §0, §6.1; research/07 C1).
  - Running 8.14.3 inside the project would try to configure AGP 9 (research/03 §7).
- **Rejected:** 8.14.3 (cannot run AGP 9; research/01 is wrong here); 9.8.2 (emits an AGP-originated deprecation, which blocks `--warning-mode=fail`).
- **Source:** [ORCH] O1.
- **Note:** Technical correction TC-4.

#### D-011 — AGP 9.4.1 with built-in Kotlin
- **Decision:**
  - AGP `9.4.1`. Kotlin `2.4.21` for `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization`.
  - Declare them `apply false` in the root `plugins {}`. That lifts AGP's bundled KGP from 2.2.10 to 2.4.21.
  - **Never** apply `org.jetbrains.kotlin.android`. No `kotlinOptions {}`; use `kotlin { compilerOptions {} }`.
  - No `android.*` flags in `gradle.properties`.
- **Rationale:** verified in `buildEnvironment` and in the smoke build (research/03 §1.2, §1.3, §6).
- **Rejected:** applying the Android Kotlin plugin (a hard error under AGP 9); kapt; a `buildscript {}` classpath.
- **Source:** [ORCH] O1.

#### D-012 — Compose BOM and Material 3 version
- **Decision:**
  - Compose BOM `2026.09.00`: Compose UI, foundation, animation and runtime 1.12.1; adaptive 1.3.0.
  - `androidx.compose.material3:material3` pinned to `1.5.0-beta02` over the BOM's 1.4.0.
  - **No** opt-in to `@ExperimentalMaterial3ExpressiveApi`. Delete the smoke build's `optIn.add(...)` line. `MaterialShapes` and `LoadingIndicator` are not used.
  - Shapes and morphs use `androidx.graphics:graphics-shapes:1.1.0` (stable), which overrides material3's transitive 1.0.1.
  - Moving to material3 1.5.0 stable is a separate future PR.
- **Rationale:**
  - 1.5.0-beta02 makes `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `ButtonGroup` and emphasized type public without an opt-in. 1.4.0 keeps them `internal` (research/03 §1.5; research/04 §0.1).
  - The experimental surface is limited to MaterialShapes, LoadingIndicator, PullToRefresh, Menu and Slider (research/03 §1.5).
- **Rejected:** staying on 1.4.0 (no expressive theme or motion scheme); opting into experimental APIs.
- **Source:** [ORCH] O1.

#### D-013 — SDK levels
- **Decision:**
  - `compileSdk = 37`, which resolves to `platforms;android-37.0`.
  - `targetSdk = 37`.
  - `minSdk = 24`.
- **Rationale:**
  - Compose 1.12.x, Navigation 3 1.2.0 and material3 1.5.0-beta02 all declare `minCompileSdk=37`.
  - DataStore 1.3.0, Navigation 3 1.2.0 and material3 1.5.0-beta02 declare minSdk 24 (research/03 §3; research/07 C2, C3).
  - Play has required target 36 since 2026-08-31. Target 37 pre-empts the next floor (research/03 §3b).
- **Rejected:** minSdk 23 (research/01 D7; DataStore alone forces 24); targetSdk 36.
- **Source:** [ORCH] O1.

#### D-014 — JDK and bytecode
- **Decision:**
  - Gradle runs on **JDK 21**: Temurin 21 in CI, OpenJDK 21 locally.
  - `settings.gradle.kts` fails fast if `!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)`, with the message "JDK 21 is required (Robolectric SDK 36/37)".
  - Bytecode is **17**:
    - `:app` uses `compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }`; under built-in Kotlin, `jvmTarget` follows.
    - `:core:engine` uses `java { source/targetCompatibility = 17 }` and `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }`.
  - There is no `jvmToolchain()` and no foojay resolver.
- **Rationale:**
  - Robolectric SDK 36/37 refuses JDK 17 (research/03 §2).
  - The engine compiled to major version 61 in the smoke build (research/03 §6.1).
  - A single JDK keeps the build reproducible (research/06-quality-ci.md §4.3).
- **Rejected:** toolchain auto-provisioning (network plus extra plugin); bytecode 21.
- **Source:** [ORCH] O1.

#### D-015 — Repositories
- **Decision:**
  - `settings.gradle.kts`:
    - `pluginManagement.repositories` = `google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }`, `mavenCentral()`.
    - `dependencyResolutionManagement { repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS; repositories { google(); mavenCentral() } }`.
  - This **drops `gradlePluginPortal()`** from the smoke build.
- **Rationale:**
  - O1 allows `google()` + `mavenCentral()` only.
  - Verified 2026-10-28 on the Maven Central mirror: the markers for Kover 0.9.11, Spotless 8.10.4, detekt 2.0.0-alpha.6, Roborazzi 1.77.0, and the Kotlin jvm/compose/serialization plugins 2.4.21 are all published there.
  - This corrects research/06 §7, which claims the Kover and Kotlin Compose markers are Portal-only.
- **Rejected:** keeping `gradlePluginPortal()` (not needed; widens the supply chain).
- **Source:** [ORCH] O1 · [RESEARCH] (verified 2026-10-28). OI-2 is settled: the Portal-free plugin resolution was built (ARCHITECTURE.md §0).

#### D-016 — Version catalog (canonical)
- **Decision:** `gradle/libs.versions.toml` is exactly this. Additions need a decision under D-173.

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.21"
composeBom = "2026.09.00"            # Compose 1.12.1, adaptive 1.3.0
material3 = "1.5.0-beta02"           # overrides BOM 1.4.0 (D-012)
activityCompose = "1.13.0"
coreKtx = "1.19.1"
coreSplashscreen = "1.2.0"
lifecycle = "2.11.0"
navigation3 = "1.2.0"
datastore = "1.3.0"
graphicsShapes = "1.1.0"
profileinstaller = "1.4.1"
kotlinxSerialization = "1.12.0"
kotlinxCoroutines = "1.11.0"
kotlinxCollectionsImmutable = "0.5.2"
junit4 = "4.13.2"
junitBom = "6.1.3"
kotest = "6.2.6"
robolectric = "4.17"
roborazzi = "1.77.0"
truth = "1.4.5"
turbine = "1.2.1"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"
espresso = "3.7.0"                   # forced: ui-test-junit4 1.12.1 drags 3.5.1, which crashes on SDK 37
spotless = "8.10.4"
ktfmt = "0.65"
kover = "0.9.11"
detekt = "2.0.0-alpha.6"
composeLintChecks = "1.6.1"

[libraries]
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-animation = { module = "androidx.compose.animation:animation" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
androidx-compose-material3-adaptive = { module = "androidx.compose.material3.adaptive:adaptive" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }
androidx-graphics-shapes = { module = "androidx.graphics:graphics-shapes", version.ref = "graphicsShapes" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junitBom" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
kotest-property = { module = "io.kotest:kotest-property", version.ref = "kotest" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
roborazzi-accessibility-check = { module = "io.github.takahirom.roborazzi:roborazzi-accessibility-check", version.ref = "roborazzi" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
compose-lint-checks = { module = "com.slack.lint.compose:compose-lint-checks", version.ref = "composeLintChecks" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
detekt = { id = "dev.detekt", version.ref = "detekt" }
# No org.jetbrains.kotlin.android (D-011).
```

- **Changes from the smoke build:**
  - Removed: `datastore-preferences` (D-040), the experimental opt-in (D-012).
  - Added: `graphics-shapes`, `material3-adaptive`, `roborazzi-accessibility-check`, `compose-lint-checks`, and Spotless, Kover and detekt.
- **Rationale:** every version comes from research/03 §10 / research/06 §7 and was re-verified in research/07 §3 (last paragraph).
- **Rejected:** `material-icons-*` (unmaintained; research/03 §1.6); `navigation-compose` 2.x; `kotest-assertions-core` (not needed).
- **Source:** [ORCH] O1, O16 · [RESEARCH].

#### D-017 — Build-logic shape
- **Decision:**
  - Kotlin DSL everywhere. The root `build.gradle.kts` declares every plugin alias `apply false`, except Spotless, which is applied at the root.
  - No `buildscript {}`, `allprojects {}` or `build-logic` / convention plugins.
  - `gradle.properties` holds exactly the smoke-build five lines:
    - `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`
    - `org.gradle.caching=true`
    - `org.gradle.configuration-cache=true`
    - `org.gradle.parallel=true`
    - `kotlin.code.style=official`
  - `rootProject.name = "game2048"`.
  - `include(":app", ":core:engine")`.
- **Rationale:** smoke-verified; configuration cache clean (research/03 §6, §7).
- **Rejected:** convention plugins for two modules (research/03 §7); Isolated Projects (incubating).
- **Source:** [ORCH] O1 · [RESEARCH].

#### D-018 — Build-tools
- **Decision:**
  - No `buildToolsVersion` pin. AGP 9.4.1 uses its default 36.0.0; it is preinstalled on runners and auto-installed locally.
  - APK inspection scripts use `ANDROID_BUILD_TOOLS=37.0.0` for `aapt2`, `zipalign -c -P 16 -v 4` and `apksigner`.
  - CI installs `build-tools;36.0.0` and `build-tools;37.0.0` idempotently.
- **Rationale:**
  - research/03 §1.2 (auto-install); research/07 U10.
  - `zipalign -P 16` was verified with 37.0.0 (research/03 §5).
  - Both are preinstalled on `ubuntu-24.04` (research/06 §3.3).
- **Rejected:** research/06's 36.0.0 for inspection scripts (`-P 16` is not verified there).
- **Source:** [RESEARCH].

#### D-019 — Sandbox-only network workarounds
- **Decision:**
  - The Maven Central mirror (`https://maven-central.storage-download.googleapis.com/maven2/`) is used **only** through a Gradle init script stored **outside the repository**.
  - `scripts/verify.sh` reads extra Gradle arguments from the environment variable `VERIFY_GRADLE_ARGS`. It never contains a mirror URL.
  - `app/build.gradle.kts` contains one inert hook: `providers.gradleProperty("robolectricRepoUrl").orNull?.let { url -> it.systemProperty("robolectric.dependency.repo.url", url) }` inside `testOptions.unitTests.all {}`.
  - CI sets neither. No committed file outside `docs/` contains the mirror URL; `scripts/check-metadata.sh` greps tracked files outside `docs/` for `storage-download.googleapis.com` and fails if it finds it.
- **Rationale:**
  - Maven Central returns intermittent 429 errors in the sandbox (research/03 §6).
  - Robolectric fetches android-all jars outside Gradle (research/07 §0.7, G19).
- **Rejected:** committing the init script; requiring the mirror in CI.
- **Source:** [USER] U3 · [RESEARCH] research/07 G19.

---

## 4. Architecture & modules

#### D-020 — Modules and packages
- **Decision:**
  - Exactly two modules:
    - `:app` (`com.android.application` + `kotlin.plugin.compose` + `kotlin.plugin.serialization` + Roborazzi), package root `com.clayboicardi.game2048`;
    - `:core:engine` (`org.jetbrains.kotlin.jvm` + Kover), package `com.clayboicardi.game2048.engine`.
  - Layout: `app/src/main/kotlin/…`, `core/engine/src/main/kotlin/…`.
  - Top-level packages in `:app`: `ui/theme`, `ui/components`, `game`, `settings`, `stats`, `about`, `data`, `di`.
- **Rationale:** the engine is the part worth exhaustive JVM testing, and it cannot depend on Android by construction (research/03 §8).
- **Rejected:** a `:baselineprofile` module (D-153); feature modules (no concrete reason).
- **Source:** [ORCH] O2.

#### D-021 — Engine purity and API shape
- **Decision.** `:core:engine` depends on the Kotlin stdlib only. It has no kotlinx-serialization, no coroutines and no Android. It never reads any of:
  - `java.time`;
  - `System.currentTimeMillis` or `System.nanoTime`;
  - `Math.random`, `kotlin.random.Random` or `java.util.Random`;
  - `SecureRandom`.
- `EngineImportsGuardTest` greps `core/engine/src/main` for those identifiers and `import android.` and fails on any match.
- **Public API** (names fixed):
  - `enum class Direction(val code: Int) { UP(0), RIGHT(1), DOWN(2), LEFT(3) }`
  - `enum class GameStatus { PLAYING, WON, KEEP_PLAYING, OVER }`
  - `class SplitMix64`
  - `fun interface RandomSource { fun nextInt(bound: Int): Int }`
  - `data class GameState` (D-030, D-039)
  - `object Game` with `newGame(size, seed)`, `move(state, direction): MoveOutcome`, `keepGoing(state)`, `undo(state): GameState?`, `canMove(state)`
  - `sealed interface MoveOutcome` with:
    - `Moved(state, transitions, spawn, scoreDelta, mergeCount, maxMergedExp, becameWon, becameOver)`;
    - `Invalid(direction)` for a no-op;
    - `Blocked(status)` for status `WON` or `OVER`.
  - A transition is `(fromIndex, toIndex, mergedIntoIndex?)`. The spawn is `(index, exp)`.
- The engine assigns **no tile IDs**; `:app` maps transitions to IDs (D-025).
- Persistence DTOs live in `:app` (`data` package), not in the engine.
- **Rationale:**
  - Determinism and replay (research/05 §0.2; research/06 §1.1).
  - Separating persistence DTOs means an engine refactor cannot silently change the on-disk format, which the fixtures guard (D-041).
- **Rejected:**
  - research/03 §8's `@Serializable` engine snapshot and `kotlin.random.Random` injection;
  - engine-owned tile IDs (session-only presentation data).
- **Source:** [ORCH] O2, O9 · [RESEARCH].

#### D-022 — Manual DI
- **Decision:**
  - `Game2048Application : Application` creates a single `AppContainer` holding:
    - `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`;
    - one DataStore per file (D-040), with game stores created lazily per size;
    - `SettingsRepository`, `RecordsRepository` and `GameRepository`;
    - `MonotonicClock` (`SystemClock.elapsedRealtime()` behind an interface);
    - `SeedSource` (`java.security.SecureRandom().nextLong()` behind an interface).
  - ViewModels are created with `viewModelFactory { initializer { … } }`.
  - Tests build an `AppContainer` over temporary files and fake clock and seed sources.
- **Rationale:** two DataStore instances on one file throw `IllegalStateException`, so there is exactly one owner (research/07 G9, G22).
- **Rejected:** Hilt, Koin, KSP, kapt (O2).
- **Source:** [ORCH] O2.

#### D-023 — Single activity and Navigation 3
- **Decision:**
  - `MainActivity : ComponentActivity` calls, in order:
    1. `installSplashScreen()`
    2. `super.onCreate`
    3. `enableEdgeToEdge()`
    4. `setContent { App() }`
  - Navigation 3 1.2.0 keys, all `@Serializable data object : NavKey`: `GameKey` (start), `StatsKey`, `SettingsKey`, `HowToPlayKey`, `AboutKey`, `LicensesKey`.
  - `rememberNavBackStack(GameKey)` and `NavDisplay` with predictive-pop transitions.
  - These are not destinations: the board-size sheet, the new-game dialog, the win and game-over cards, the What's new sheet, the reset-statistics dialogs and the first-launch hint.
  - The Game screen has no top app bar. Its actions are Undo, New game, the Board-size chip, and a menu with Statistics, Settings, How to play and About.
- **Rationale:** Navigation 3 is stable and Compose-first, and its back stack is plain state (research/03 §1.7; research/07 G22).
- **Rejected:** navigation-compose 2.x; making overlays destinations.
- **Source:** [ORCH] O2 · [RESEARCH].

#### D-024 — State holder and persistence writer
- **Decision.** `GameViewModel` owns the current `GameState` and applies `Game.move` **synchronously on the main thread** in the input callback (D-052). It exposes:
  - `StateFlow<GameUiState>` (`@Immutable`, `kotlinx.collections.immutable` lists);
  - separate `StateFlow`s for the score/best header and for overlay state, so a score tick never recomposes the board.
- Every state change calls `GameRepository.save(size, state)`. This writes the latest value into a `MutableStateFlow`, which a single collector in `applicationScope` drains onto `Dispatchers.IO`:
  - it uses a plain `collect`, which is conflated (latest wins);
  - a write in progress is **never cancelled**.
- Records and settings use the same writer pattern.
- **Rationale:**
  - State-first apply plus snap-and-animate (research/04 §4.4; research/07 C12, G22).
  - `collectLatest` would starve writes during long bursts.
- **Rejected:** an input queue (O3); `collectLatest`; writing from composition.
- **Source:** [ORCH] O2, O3, O10.

#### D-025 — Tile UI model and rendering
- **Decision.** `BoardPresenter` in `:app` keeps a `cellIndex → tileId` map and converts each `MoveOutcome.Moved` into `BoardUi(moveId, tiles, scoreDelta, event)` with `TileUi(id: Long, exp: Int, row, col, fromRow, fromCol, kind)`.
- `kind` is one of:
  - `Static`: restored, undone or new-game-initial boards, which never animate;
  - `Moved`;
  - `Merged`: a new ID;
  - `Ghost`: one of the two sources, removed after 200 ms or at the next commit;
  - `Spawned`.
- Rendering rules:
  - A `Box` of per-tile composables uses `key(tile.id)`.
  - Translation goes through `Modifier.offset { }`. Scale and alpha go through `graphicsLayer { }`. Fill and text go through `drawBehind { }` with a cached `TextMeasurer` layout keyed by `(exp, tileSizePx, paletteId)`.
  - `Animatable.value` is never read in a composition body.
  - Empty cells are drawn once in one `drawBehind`.
  - Confetti, rings and sparkles share one `Canvas` with structure-of-arrays state.
- **Rationale:** a tile composes once, and per-frame work stays in the layout and draw phases (research/04 §4.1–4.2, §4.5).
- **Rejected:** a single-Canvas board (loses semantics and keys); `LookaheadScope` for tiles; `graphicsLayer` translation (frame-rate voting, research/04 §4.2).
- **Source:** [RESEARCH] research/04 §4.
- **Note:** Clarified by D-182; amended by D-187.

#### D-026 — Compose and M3 API usage policy
- **Decision:**
  - `GameTheme` wraps `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(), typography, shapes)`.
  - Game motion never reads `MaterialTheme.motionScheme`; it uses `GameMotion` (D-060).
  - Every call to a material3-1.5-only public API is made from `ui/theme` or `ui/components` wrappers. Examples: `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `ButtonGroup`, `ButtonDefaults.shapes()`, `*Emphasized` type styles. A fallback to 1.4.0 then touches only those two packages.
  - Window size uses `currentWindowAdaptiveInfoV2()`.
  - Icons are committed Material Symbols vectors (D-079). There is no `ui-text-google-fonts`.
- **Rationale:**
  - `currentWindowAdaptiveInfo()` is `@Deprecated(WARNING)` in adaptive 1.3.0 and would fail `allWarningsAsErrors` (research/07 C4).
  - research/04 §0.1 (own motion tokens).
- **Rejected:** using the `motionScheme` springs directly; the experimental expressive APIs.
- **Source:** [ORCH] O1, O4 · [RESEARCH].
- **Note:** Amended by D-184.

---

## 5. Engine rules

#### D-030 — Board representation
- **Decision:**
  - Size `N ∈ {3,4,5,6,7,8}`, default 4.
  - `cells: ByteArray(N*N)`, row-major (`index = row*N + col`, row 0 at the top, col 0 at the left).
  - Each cell holds the **exponent** (`0` = empty, `k` = value `2^k`, max 127).
  - `score: Long`, with saturating addition at `Long.MAX_VALUE`; this is unreachable in practice.
  - Tile values are never stored as `Int` or `Long` values.
- **Rationale:** 8×8 can theoretically reach 2^65, which does not fit in a `Long` (research/05 §4.2).
- **Rejected:** column-major legacy layout (`cells[x][y]`, research/02 §1); value storage.
- **Source:** [ORCH] O7.

#### D-031 — Direction codes
- **Decision:** `UP=0`, `RIGHT=1`, `DOWN=2`, `LEFT=3`. These are the legacy codes, and the 2-bit move log uses them.
- **Rationale:** legacy parity (research/02 §1).
- **Rejected:** a new code order (it breaks parity with the legacy traces and gains nothing).
- **Source:** [RESEARCH].

#### D-032 — Slide and merge
- **Decision.** For each line along the move direction:
  - Process tiles **starting from the wall the move goes toward**.
  - Each tile slides to the farthest empty cell.
  - If the next occupied cell holds the **same exponent** and was **not created by a merge in this move**, the two merge into exponent + 1 at that cell.
  - A merged tile cannot merge again in the same move.
  - `moved` is true iff any tile changed cell or merged.
- `EngineGoldenTest` holds these row-left cases (input → output, Δscore); 0 = empty:

| Input | Output | Δ |
|---|---|---|
| 2 2 2 2 | 4 4 0 0 | +8 |
| 2 2 2 0 | 4 2 0 0 | +4 |
| 4 2 2 0 | 4 4 0 0 | +4 |
| 8 0 8 8 | 16 8 0 0 | +16 |
| 2 2 4 4 | 4 8 0 0 | +12 |
| 2 2 4 0 | 4 4 0 0 | +4 |
| 4 4 8 0 | 8 8 0 0 | +8 |
| 8 8 8 0 | 16 8 0 0 | +16 |
| 2 0 2 4 | 4 4 0 0 | +4 |
| 2 2 0 2 | 4 2 0 0 | +4 |
| 0 0 0 2 | 2 0 0 0 | 0, moved |
| 2 4 8 16 | unchanged | 0, **not moved** |
| 1024 1024 0 0 | 2048 0 0 0 | +2048, becomes `WON` |

- Plus these row-right cases:
  - `2 2 2 2 → 0 0 4 4`;
  - `2 2 2 0 → 0 0 2 4`;
  - `8 0 8 8 → 0 0 8 16`.
- Up and down use the same rows transposed.
- **Rationale:** research/02 §1 (harness T6, verified); research/06 §1.1; research/07 minor inconsistencies (merge both sets).
- **Rejected:** chained merges (`4 2 2 → 8`); merging from the trailing wall.
- **Source:** [RESEARCH].

#### D-033 — Score
- **Decision:**
  - `score += 2^(exp+1)` for every merge in the move. There are no bonuses or multipliers.
  - `scoreDelta` is the sum of those merges and drives the "+N" float.
  - The score decreases only through undo.
- **Rationale:** research/02 §1 "Scoring".
- **Rejected:** combo or bonus scoring (not in the legacy game; it would break score comparability).
- **Source:** [RESEARCH].

#### D-034 — Start and spawn
- **Decision.** A spawn happens only after an accepted move (and twice at the start of a new game), and only if an empty cell exists:
  1. `exp = if (rng.nextInt(10) == 0) 2 else 1`, so a 4 has probability exactly 1/10.
  2. Collect the empty cells in row-major order.
  3. `cell = empty[rng.nextInt(empty.size)]`.
- The draw order is value, then cell, and is frozen by golden tests.
- `newGame(size, seed)` = empty board, `rngState = seed`, two spawns.
- **Rationale:** legacy rule and order (value first, then cell; research/02 §1); research/07 G10.
- **Rejected:** cell first; column-major enumeration.
- **Source:** [RESEARCH].

#### D-035 — No-op move
- **Decision:**
  - If `moved == false`, return `MoveOutcome.Invalid(direction)`. Nothing happens:
    - no RNG draw;
    - no spawn;
    - no history entry;
    - no change to score, `moveCount`, `undoCount` or persistence.
  - The UI shows the nudge (D-064) and the rate-limited Reject haptic (D-065).
- **Rationale:** research/02 §1 (verified T5); research/06 §1.1 property 3.
- **Rejected:** spawning on, or recording, a no-op (the legacy game does neither, and it would enable spawn-fishing).
- **Source:** [RESEARCH].

#### D-036 — Win targets, status machine, Keep going
- **Decision.** The target exponent is `8` (256) for N = 3 and `11` (2048) for N = 4..8. Status transitions:
  - **`PLAYING` → `WON`** when an accepted move creates a tile whose exponent equals the target. `becameWon = true`.
    - If the same move also leaves no moves, the status is still `WON`; `OVER` is evaluated after Keep going.
  - **In `WON`**, `move()` returns `Blocked(WON)`. Undo is allowed.
  - **`keepGoing()`** sets `KEEP_PLAYING` and `keepGoingAtMove = moveCount`. If `!canMove`, it immediately sets `OVER`.
  - **In `KEEP_PLAYING`**, tiles are unbounded and `WON` is never re-entered.
  - **Undo** to a state with `moveCount < keepGoingAtMove` resets `keepGoingAtMove = -1`, so re-reaching the target shows the win card again.
  - **Reconstruction rule** (used by undo, D-039): before each replayed move and after reconstruction ends, if `0 ≤ keepGoingAtMove ≤ moveCount` and the status is `WON`, set the status to `KEEP_PLAYING`.
- **Rationale:**
  - research/02 §1 (exact-match win, single report, the win + over edge case).
  - research/05 §4.2 (3×3 target 256).
  - research/06 §1.1 property 9.
- **Rejected:** a configurable target; re-announcing later target tiles.
- **Source:** [ORCH] O7, O12 · [RESEARCH].

#### D-037 — Game over
- **Decision:**
  - After the spawn of an accepted move: if no cell is empty and no orthogonally adjacent pair has equal exponents, status becomes `OVER` (unless `WON` per D-036).
  - In `OVER`, `move()` returns `Blocked(OVER)`. Only undo and new game act.
  - `canMove(state)` checks each tile against its right and down neighbours.
- **Rationale:** research/02 §1 (complete with two directions); research/06 §1.1 property 8.
- **Rejected:** checking before the spawn (the legacy check runs after it); a 4-direction scan (redundant, research/02 §1).
- **Source:** [RESEARCH].

#### D-038 — PRNG (SplitMix64)
- **Decision.** `SplitMix64(state: Long)`. `next()` does:

  ```
  state += 0x9E3779B97F4A7C15
  z = state
  z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9
  z = (z xor (z ushr 27)) * 0x94D049BB133111EB
  return z xor (z ushr 31)
  ```

  Arithmetic is on `Long` with wraparound. Bounded draw: `nextInt(bound)`:

  ```
  require(bound > 0)
  u = next() ushr 1
  r = u % bound
  while (u - r + (bound - 1) < 0) { u = next() ushr 1; r = u % bound }
  return r
  ```

  This is rejection sampling with no modulo bias.
- Golden vectors (computed 2026-10-28; asserted by `SplitMix64GoldenTest`):
  - seed `0`: first four outputs `0xE220A8397B1DCDAF`, `0x6E789E6AA1B965F4`, `0x06C45D188009454F`, `0xF88BB8A8724C81EC`;
  - seed `0x2048`: first four outputs `0xF7F3D05A9C290B51`, `0x390005F078622C95`, `0xC35B79300DF431F5`, `0x2B84F35B753B4E34`;
  - `nextInt(10)` ×8 from seed `0x2048` = `8, 6, 0, 0, 9, 9, 8, 3`;
  - `nextInt(16)` ×8 from seed `0x2048` = `8, 10, 10, 10, 1, 13, 4, 9`.
- The 64-bit state lives in `GameState.rngState`.
- The per-game seed comes from `SeedSource` (`SecureRandom`) in `:app` and is stored as `seed`.
- `kotlin.random.Random(seed)` and `java.util.Random` are forbidden for game logic.
- **Rationale:**
  - Kotlin's seeded `Random` may change its algorithm between versions (research/05 §0.2, S30).
  - research/07 G10, U14 (the seed-0 reference value matches).
- **Rejected:** xoshiro/PCG (unnecessary); modulo reduction (biased).
- **Source:** [ORCH] O9.

#### D-039 — Undo and history
- **Decision:**
  - **History:**
    - Every accepted move appends its 2-bit direction code to `moveLog`. Move `i` sits in byte `i/4`, bits `2*(i%4)` and `2*(i%4)+1`, LSB-first.
    - A **snapshot** is stored whenever `moveCount % 32 == 0`, including move 0, so `snapshots[k]` is the state at move `32*k` (binary layout in D-042).
  - **Undo:**
    - Target `t = moveCount - 1`.
    - Load `snapshots[t / 32]` and replay `moveLog[32*(t/32) until t]` with the RNG restored from the snapshot. Apply the D-036 reconstruction rule before each replayed move and at the end.
    - Truncate `moveLog` to `t` moves and drop snapshots beyond `t / 32`.
    - Increment `undoCount`. This counter is never decremented and is not part of snapshots.
    - The **RNG state is restored**, so repeating the same move yields the same spawn.
  - **Where undo works:** in `PLAYING`, `KEEP_PLAYING`, `WON` and `OVER`. Undo from `OVER` restores a playable state.
  - Undo is unavailable at `moveCount == 0` (button disabled; `undo()` returns `null`).
  - Depth is unlimited. There is no confirmation dialog and **no redo**.
- **Rationale:**
  - research/05 §4.1 (unlimited, anti-reroll, works on game over, counter).
  - research/02 B1–B4.
  - research/07 C21.
- **Rejected:**
  - the legacy 50-level stack;
  - full-state stacks (O(n) memory per move);
  - an undo confirmation (research/02 §9);
  - redo (O8).
- **Source:** [ORCH] O8.
- **Note:** Extended by D-186.

---

## 6. Data & persistence

#### D-040 — DataStore files
- **Decision:**
  - Typed DataStore (`androidx.datastore:datastore` 1.3.0) only, with a kotlinx-serialization JSON `Serializer<T>` per document.
  - Files, via `context.dataStoreFile(name)` → `files/datastore/<name>`:
    - `settings.json`;
    - `records.json`;
    - `game-3.json` … `game-8.json`.
  - Each store is created once in `AppContainer` with `ReplaceFileCorruptionHandler` (D-045) and `scope = applicationScope + Dispatchers.IO`.
- **Rationale:**
  - Splitting files means a corrupt game cannot destroy the bests (research/07 G10).
  - DataStore writes atomically via temp file + rename (research/05 §4.17).
- **Rejected:** Preferences DataStore for settings (research/03 §1.7, research/07 G10; O2 says typed); Room; protobuf.
- **Source:** [ORCH] O2, O10.

#### D-041 — Schema versioning, decoding and fixtures
- **Decision:**
  - Every document has `schemaVersion: Int = 1`.
  - Decoder: `Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }`.
  - **Every** field has a default. Enums are encoded as names; an unknown name coerces to the default.
  - A breaking change bumps `schemaVersion` and adds a `DataMigration<T>`.
  - From M2 on, the repository commits fixtures that are never edited afterwards:
    - `app/src/test/resources/fixtures/v1/settings.json`;
    - `records.json`;
    - `game-4.json`;
    - `game-8-long.json` (an 8×8 game with 10,000 moves).
  - `PersistenceFixturesTest` decodes them in every later build.
- **Rationale:** research/06 §1.2 case 3; research/07 G10. `coerceInputValues` keeps a downgrade from a newer enum value safe.
- **Rejected:** strict decoding (one unknown key would wipe a save); untyped Preferences keys.
- **Source:** [ORCH] O10 · [RESEARCH].

#### D-042 — Document fields and encodings
- **Decision.** All Base64 uses `kotlin.io.encoding.Base64.Default`.

`game-N.json` fields:

| Field | Type | Content |
|---|---|---|
| `schemaVersion` | `Int` | `1` |
| `size` | `Int` | N |
| `seed` | `Long` | Per-game seed |
| `cells` | `String` | Base64 of the N² exponent bytes |
| `score` | `Long` | |
| `rngState` | `Long` | |
| `status` | `String` | `PLAYING`, `WON`, `KEEP_PLAYING` or `OVER` |
| `moveCount` | `Int` | |
| `keepGoingAtMove` | `Int` | `-1` when unset |
| `moveLog` | `String` | Base64 of the packed log (D-039) |
| `snapshots` | `List<String>` | Base64 of each snapshot |
| `undoCount` | `Int` | |
| `elapsedMs` | `Long` | |
| `bestAtStart` | `Long` | |
| `newBestAnnounced` | `Boolean` | |

Snapshot binary layout (big-endian, `21 + N²` bytes, 85 B for 8×8):

| Bytes | Field |
|---|---|
| 0–3 | `moveCount` (`Int`) |
| 4–11 | `score` (`Long`) |
| 12–19 | `rngState` (`Long`) |
| 20 | status ordinal at the time the snapshot was taken |
| 21… | N² cell bytes |

`keepGoingAtMove` is deliberately not part of snapshots. Keep going can happen at the same `moveCount` as a snapshot, so a stored copy could go stale. The game-level field plus the D-036 reconstruction rule is the only source.

`records.json`: `schemaVersion`, then `sizes: Map<String, SizeRecord>` keyed `"3"`…`"8"`. A `SizeRecord` holds:
- `bestScore: Long`, `bestScoreNoUndo: Long`, `bestTileExp: Int`;
- `gamesPlayed`, `gamesWon`, `gamesAbandoned`: `Int`;
- `totalScore`, `totalMoves`, `totalTimeMs`, `totalUndos`: `Long`;
- `tileReach: List<Int>`, where index k = the number of games that reached ≥ 2^k;
- `currentWinStreak`, `longestWinStreak`: `Int`.

`settings.json`:

| Field | Values (default) |
|---|---|
| `schemaVersion` | `1` |
| `themeMode` | `SYSTEM` / `LIGHT` / `DARK` |
| `palette` | `CLASSIC` / `NEON` |
| `highContrast` | `false` |
| `dynamicChrome` | `false` |
| `motion` | `FULL` / `REDUCED` / `OFF` |
| `haptics` | `true` |
| `moveTiming` | `DURING_SWIPE` / `ON_RELEASE` |
| `swipeSensitivity` | `LOW` / `MEDIUM` / `HIGH` |
| `immersive` | `false` |
| `keepScreenOn` | `false` |
| `lastBoardSize` | `4` |
| `lastSeenVersionCode` | `0` |
| `onboardingDone` | `false` |

- **Rationale:**
  - The JSON number arrays that kotlinx produces for `ByteArray` cost about 4 characters per byte, and DataStore rewrites the whole file on every move (research/07 G10; research/06 §8 risk 9).
  - `kotlin.io.encoding.Base64` works in plain JVM tests. `java.util.Base64` needs API 26; `android.util.Base64` is not available on the plain JVM.
- **Rejected:** a JSON array for cells; protobuf.
- **Source:** [ORCH] O10 · [RESEARCH] research/05 §7.

#### D-043 — Write policy
- **Decision.** Enqueue a save of the full game document (D-024) after each of:
  - every accepted move;
  - undo;
  - keep-going;
  - new game;
  - board-size switch;
  - `ON_PAUSE`, which also folds in the elapsed time.
- Records are updated **live** on accepted moves when `bestScore`, `bestScoreNoUndo` (only if `undoCount == 0`) or `bestTileExp` increases.
- Writes are conflated and never cancelled. If the process dies, at most the single in-flight change is lost; this is documented in README.
- `elapsedMs` accumulates `MonotonicClock` time only while the Game destination is `RESUMED` and the status is `PLAYING` or `KEEP_PLAYING` after the first move.
- **Rationale:** O10 ("commit after every accepted move"); research/07 G9.
- **Rejected:** writing only in `onStop` (loses moves on a kill); blocking `onStop`.
- **Source:** [ORCH] O10.

#### D-044 — Records and statistics
- **Decision.**
- **Finalization.** A game is finalized when New game starts on its size (D-007), and only if `status == OVER || score > 0`. Finalization does the following:
  - `gamesPlayed++`;
  - `gamesAbandoned++` if not `OVER`;
  - `gamesWon++` and the streak update if the max exponent ≥ target;
  - adds `totalScore`, `totalMoves`, `totalTimeMs` and `totalUndos`;
  - increments `tileReach[k]` for every k ≤ the max exponent.
- **Bests.** `bestScore`, `bestScoreNoUndo` and `bestTileExp` never decrease, except through **Reset statistics**.
  - Reset lives in the Statistics screen's top bar. It takes two sequential confirmation dialogs.
  - It clears all sizes' records and leaves in-progress games untouched.
- **New best.**
  - `bestAtStart` = `bestScore` when the game starts.
  - The new-best moment fires once per game, at the first accepted move where `score > bestAtStart`, and only if `bestAtStart > 0`.
  - `newBestAnnounced` persists across process death.
- **Statistics screen** (`StatsKey`): one tab per size (3×3…8×8) plus "All sizes". It shows:
  - games played;
  - wins and win rate;
  - best score;
  - best score without undo;
  - best tile;
  - average score;
  - total moves;
  - time played;
  - undos used.
- **Rationale:** research/05 §4.5 (schema; histogram stored for later); O7 (per size); O8 (undo count).
- **Rejected:** finalizing on game over, which double-counts after an undo; a top-10 list (v1.x).
- **Source:** [ORCH] O7, O8 · [RESEARCH].
- **Note:** Clarified by D-198.

#### D-045 — Corruption and failure handling
- **Decision.** Each store's `ReplaceFileCorruptionHandler` returns that document's defaults:
  - a corrupt game file means a new game of that size;
  - a corrupt records file means empty records;
  - a corrupt settings file means default settings.
- No failure crashes the app or deletes another file. Nothing is logged or sent anywhere.
- `PersistenceCorruptionTest` writes garbage into each file and asserts the defaults and the survival of the other files.
- **Rationale:** research/06 §1.2 case 2; research/07 G10.
- **Rejected:** crashing or showing an error dialog; one combined file (a single corruption would lose everything).
- **Source:** [RESEARCH].

#### D-046 — Size and time budgets
- **Decision.** `PersistenceBudgetTest` (JVM) asserts:
  - an 8×8 game document with 10,000 moves encodes to **≤ 256 KiB**;
  - encode + decode takes **≤ 50 ms** (median of 5 runs after 3 warm-up runs);
  - `records.json` with all sizes populated is **≤ 64 KiB**;
  - `settings.json` is **≤ 4 KiB**.
- Expected size for the 10k-move game: about 3.4 KB of Base64 log plus 313 snapshots × 85 B ≈ 36 KB of Base64 snapshots, so about 40 KB in total.
- **Rationale:** research/07 G12 (stricter than research/06 §4.2's "< 5 MB at 20k moves", because the whole file is rewritten on every move).
- **Rejected:** research/06 §4.2's 5 MB at 20k moves (too loose for a file that is rewritten on every move).
- **Source:** [ORCH] O10 · [RESEARCH].

#### D-047 — Process death and restore
- **Decision:**
  - The UI is a pure function of persisted state.
  - On cold start, after recreation or on resume, the board renders the persisted state with `TileUi.kind = Static`: no spawn or "+N" replay, and no reload.
  - **Tests:**
    - `ProcessDeathRestoreTest` (JVM): container A applies N moves and undos on every size to temporary files, then its scope is cancelled. Container B, built on the same files, must reproduce board, score, `rngState`, log, snapshots, `undoCount`, `elapsedMs` and status exactly.
    - `RecreateNoReplayTest` (Robolectric): `ActivityScenario.recreate()` and a pause/resume cycle produce only `Static` tiles and an unchanged history.
  - Transient UI state (open sheets) uses `rememberSaveable` and the Nav3 back stack.
- **Rationale:** research/01 §4.9 (the legacy reload defects); research/07 G9.
- **Rejected:** rebuilding from scratch on resume (legacy B7–B9); `SavedStateHandle` as the game store (not durable for long histories).
- **Source:** [ORCH] O10, O11.

#### D-048 — Backup and device transfer
- **Decision.** The manifest sets `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/backup_rules"`.
  - The first file has `<include domain="file" path="datastore/"/>` in both `<cloud-backup>` and `<device-transfer>`.
  - The second has the same include (API 24–30).
  - `BackupRulesTest` (Robolectric) asserts both resources exist and are referenced.
- **Rationale:** research/06 §4.2; research/07 G15; legacy `allowBackup` with no rules (research/01 D14).
- **Rejected:** `allowBackup="false"` (loses bests on a device change); backing up everything (the include list keeps caches out).
- **Source:** [RESEARCH].

---

## 7. Input

#### D-050 — Swipe recognition
- **Decision.** A single pointer input on the gesture surface (D-051) works as follows:
  - Displacement is measured from pointer-down, once the system touch slop (`LocalViewConfiguration.current.touchSlop`) is exceeded.
  - **Move timing = As you swipe (default).** Commit **during the drag** at the first frame where:
    - `|major| ≥ T`, and
    - `|major| ≥ 1.4 · |minor|`.
  - **Move timing = When you lift your finger.** Evaluate the same two conditions on the full displacement vector at pointer-up.
  - **Both modes:** if nothing has committed by pointer-up, commit when the release velocity along the dominant axis is **≥ 600 dp/s**, the ratio condition holds, and the displacement exceeds touch slop.
- Thresholds: `T` = 40 dp (Low), **24 dp (Medium)**, 12 dp (High).
- Each gesture produces **exactly one move**:
  - after a commit, the rest of the gesture is consumed;
  - a second pointer before the commit cancels the gesture;
  - an ambiguous diagonal (ratio < 1.4) is ignored.
- **Rationale:**
  - research/04 §4.4 (commit during the drag, 24 dp, 1.4 ratio, 600 dp/s).
  - research/05 §4.14 (sensitivity values; a release option for players who want it).
  - research/02 B13 (the legacy 10 px threshold fired only on lift).
- **Rejected:** release as the default (research/05, research/07 D6; see OI-1); time-based slow-drag rejection (it conflicts with commit-during-drag).
- **Source:** [ORCH] O3 · [RESEARCH].

#### D-051 — Gesture surface and system gestures
- **Decision:**
  - The gesture surface is the whole Game content area. Buttons, the size chip and the menu handle their own taps.
  - A drag that **starts on a button** is a swipe: the button must not fire (this fixes B11).
  - The board keeps a side margin of at least `max(16 dp, systemGestures left/right inset)`.
  - `Modifier.systemGestureExclusion` is **not** used in v1.0.
- **Rationale:**
  - research/07 C19: the inset is the primary mitigation; exclusion is capped at 200 dp vertical per edge (verified, research/07 U2).
  - No layout puts the board against a screen edge (D-091).
- **Rejected:** board-edge exclusion (research/05 §4.16 rule 6).
- **Source:** [RESEARCH].

#### D-052 — State-first input, no queue
- **Decision:**
  - Every committed swipe, key or accessibility action calls `Game.move` synchronously on the latest state.
  - Slides in flight `snapTo` their targets and ghost tiles are removed. Then the new move animates (D-062).
  - No input is queued, delayed or dropped because of animation.
  - `InputBurstOrderingTest` fires 3 swipes within 50 ms and asserts that all 3 apply in order to successive states.
- **Rationale:** research/04 §4.4; research/06 §1.2; research/07 C12.
- **Rejected:** research/05 §4.16 "queue depth 1, drop beyond" (it reintroduces lost inputs).
- **Source:** [ORCH] O3.

#### D-053 — Keyboard, D-pad and gamepad
- **Decision.** Key map:

| Action | Keys |
|---|---|
| Up | ↑, W, K, `KEYCODE_DPAD_UP` |
| Right | →, D, L, `KEYCODE_DPAD_RIGHT` |
| Down | ↓, S, J, `KEYCODE_DPAD_DOWN` |
| Left | ←, A, H, `KEYCODE_DPAD_LEFT` |
| Undo | U, Ctrl+Z, Backspace |
| New game | N, R (confirmation per D-007) |

- Gamepad D-pads and hats deliver `DPAD_*` key codes.
- **Modifiers.** Keys pressed with Alt, Meta or Shift are ignored. Ctrl is ignored except for Ctrl+Z.
- **Auto-repeat.** Events with `repeatCount > 0` are accepted at most once per **120 ms**.
- **Focus:**
  - The board is focusable and requests focus when the Game screen appears.
  - Tab and Shift+Tab move focus to the controls, and arrows then navigate focus normally.
  - W/A/S/D/H/J/K/L, U and N/R always act on the Game screen unless a dialog or sheet is open.
  - The focus indicator contrasts ≥ 3:1.
- **Rationale:**
  - research/02 §5 (legacy keys);
  - research/07 G23 (map; R now confirms);
  - research/04 §4.4 (120 ms auto-repeat).
- **Rejected:** a separate gamepad button mapping (TV is out of scope).
- **Source:** [ORCH] O3 · [RESEARCH].
- **Note:** Amended by D-199; extended by D-203.

#### D-054 — Input during overlays; Back
- **Decision:**
  - The win card, the game-over card, dialogs and sheets are modal. While they are visible, board swipes and move keys are ignored, and keys go to the overlay.
  - **Back:**
    - dismisses sheets and dialogs;
    - pops sub-screens through `NavDisplay`;
    - on the Game root, it is **never intercepted**, including while the win or game-over card is shown. The system goes home.
- **Rationale:** O11; research/01 §4.11; research/04 §7.4.
- **Rejected:** Back = Keep going on the win card (it overloads system Back); a dismissible game-over card (it recreates the B4 dead board).
- **Source:** [ORCH] O11, O12.
- **Note:** Clarified by D-199.

---

## 8. Motion & haptics

#### D-060 — `GameMotion` tokens
- **Decision.** `ui/theme/GameMotion.kt` defines these tokens, each with damping ratio / stiffness:

| Token | Damping / stiffness | M3 equivalent |
|---|---|---|
| `slide` | 0.9 / 1400, visibility threshold 0.5 px | Standard fastSpatial |
| `popSpatial` | 0.6 / 800 | Expressive fastSpatial |
| `overlaySpatial` | 0.8 / 380 | Expressive defaultSpatial |
| `slowSpatial` | 0.8 / 200 | Expressive slowSpatial |
| `fastEffects` | 1.0 / 3800 | |
| `defaultEffects` | 1.0 / 1600 | |
| `slowEffects` | 1.0 / 800 | |
| `NUDGE` | 0.3 / 1800 | |

  Game constants:

| Constant | Value |
|---|---|
| `SPAWN_DELAY_MS` | 90 |
| `MERGE_REVEAL_MS` | 80 |
| `GHOST_REMOVE_MS` | 200 |
| `POP_IMPULSE` | 6 / 8 / 10 / 12 per second (max merged ≤ 64, 128–512, 1024, ≥ 2048) |
| `NUDGE_VELOCITY_DP_S` | 330 |

- Slides **never** use an expressive spring. Expressive springs are used only for spawn, merge pop, overlays and celebrations.
- **Rationale:**
  - research/04 §3.1 (token values; the expressive fast spring overshoots 9.3 %);
  - research/04 §10 (the object);
  - O4 (independence from material3).
- **Rejected:** tween slides (legacy 100 ms ease-in-out); expressive slides.
- **Source:** [ORCH] O4 · [RESEARCH] research/04 §3.1, §10.
- **Note:** Extended by D-183.

#### D-061 — Per-move timeline (Full motion)
- **Decision:**

| Time | Event |
|---|---|
| t = 0 | Commit. All slides start with `slide` |
| t = 80 ms | Merged tiles are revealed above the ghosts and pop from scale 1.0 with `popSpatial` and initial velocity `POP_IMPULSE` (peaks ≈ 1.10–1.21). The "+N" float and score odometer start |
| t = 90 ms | Spawn: scale 0.6 → 1 with `popSpatial`, alpha 0 → 1 with `fastEffects` |
| t = 200 ms | Ghosts are removed |
| ≈ 400 ms | Everything is visually settled |

- Delays are multiplied by the system animator scale. A scale of 0 means Off (D-063).
- **Rationale:** research/04 §10.1–10.4.
- **Rejected:** the legacy timeline (pop and spawn from scale 0 after a 100 ms delay; research/04 §1).
- **Source:** [RESEARCH].
- **Note:** Extended by D-183.

#### D-062 — Interruption
- **Decision.** When a new move commits:
  - in-flight slides `snapTo` their targets and ghosts are removed;
  - spawn, pop, "+N", rings and confetti keep running;
  - at most 3 "+N" floats are alive, and the oldest is removed early.
- There is no "fluid" (velocity-preserving) mode.
- **Rationale:** research/04 §4.4 (snapping keeps the visual order equal to the model order).
- **Rejected:** a velocity-preserving "fluid" mode (transient tile overlap; research/04 §4.4); queueing (O3).
- **Source:** [ORCH] O3, O4.
- **Note:** Extended by D-183.

#### D-063 — Motion setting and reduced motion
- **Decision.** The effective motion is `OFF` if `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (observed live). Otherwise it is the setting: **Full** (default), Reduced or Off.
- **Reduced** uses the substitutions in research/04 §6.2:
  - instant slides;
  - alpha-only spawn (`fastEffects`);
  - a 120 ms color crossfade instead of the pop;
  - "+N" fades in place over 400 ms;
  - the nudge becomes a board-outline pulse (0 → 0.6 → 0 over 300 ms);
  - new best becomes a label swap plus a container crossfade;
  - win and game over become a 150 ms fade with a static badge;
  - no confetti, rings, morph or rotation;
  - the score changes instantly.
- **Off** is instant for everything, including the overlay entries, "+N" and the nudge.
- Rules for every mode:
  - Coroutine `delay`s are multiplied by the duration scale.
  - Custom `withFrameNanos` loops check `coroutineContext[MotionDurationScale]` and the effective motion, and render the end state when either is zero or Off.
  - There are **no infinite animations**, and nothing flashes more than 3 times per second.
- **Rationale:** research/04 §6.1–6.2 (WCAG 2.3.3, 2.2.2, 2.3.1); research/07 C14.
- **Rejected:** research/04 "System/Reduced/Full"; research/05 "Animation speed" multipliers.
- **Source:** [ORCH] O4.
- **Note:** Extended by D-183; clarified by D-184.

#### D-064 — Signature moments (Full motion values)
- **Decision.** Adopt research/04 §10.5–§10.12 verbatim, with these bindings:
- **"+N" float:** −28 dp over `tween(650, EmphasizedDecelerate)`; alpha holds for 250 ms, then fades over `tween(400)`.
- **Score odometer:** `AnimatedContent` per digit, `slide` + `fastEffects`.
- **Invalid-move nudge:** a board offset along the swipe with `NUDGE` and initial velocity 330 dp/s. It peaks at +5.2 dp and settles at 346 ms under Compose's estimator (technical correction TC-7).
- **New best** (once per game, D-044): the BEST card crossfades to `tertiaryContainer`; the "BEST" → "NEW BEST" label; a 10-point soft star from `RoundedPolygon.star(10, innerRadius = 0.82f, rounding = CornerRounding(0.15f))`; 8 sparkles; 1.5 s total.
- **Win**, with T0 = merge reveal:
  - pop with v0 = 12;
  - glow ring at T0 + 60 ms;
  - confetti at T0 + 120 ms: 96 particles on compact, 144 on expanded, cap 160, done in ≤ 2.4 s;
  - scrim 0 → 0.60 at T0 + 600 ms;
  - card scale 0.92 → 1 with `overlaySpatial`;
  - hero badge morph Circle → 12-point cookie (`RoundedPolygon.star(12, innerRadius = 0.88f, rounding = CornerRounding(0.14f))`, via graphics-shapes `Morph`);
  - rotation 0 → 60° over 5000 ms, then it stops;
  - buttons stagger by 50 ms and are **interactive at T0 + 800 ms**.
- **Game over**, with T0 = commit + 300 ms:
  - desaturate 1.0 → 0.2 over `tween(600)`;
  - deflate ripple to scale 0.94, staggered 30 ms per Manhattan step;
  - scrim 0 → 0.78 at T0 + 450 ms;
  - card at T0 + 500 ms, **interactive at T0 + 700 ms**.
- **Undo:** a `fastEffects` crossfade between the boards. The rewind animation is deferred to v1.x.
- **New game:** tiles shrink to 0.6 and fade, staggered 15 ms in reading order; then the two start tiles spawn 60 ms apart.
- **Layout:** re-layout between modes uses `animateBounds` with `overlaySpatial`. A theme switch crossfades colors with `slowEffects`.
- **Generalization:** every spec applies to any N.
- **Rationale:** research/04 §10; research/07 D9.
- **Rejected:** the legacy 1200 ms overlay delay; the experimental `MaterialShapes` (D-012).
- **Source:** [ORCH] O4, O12 · [RESEARCH] research/04 §10.
- **Note:** Clarified by D-182, D-187, D-200; extended by D-183; technical correction TC-7.

#### D-065 — Haptic map
- **Decision.** With Haptics = On, each accepted move fires **at most one** of these, chosen by priority (first match wins). Timing for Full motion is measured from commit; under Reduced or Off, pulses fire at commit.

| Priority | Event | Haptic | Timing (Full) | Timing (Reduced / Off) |
|---|---|---|---|---|
| 1 | Win | `Confirm`, then `Confirm` | +120 ms and +270 ms | +0 and +150 ms |
| 2 | New best (D-044) | `GestureThresholdActivate` | +80 ms | +0 |
| 3 | Max merged value ≥ 512 | `Confirm` | +80 ms | +0 |
| 4 | ≥ 1 merge | `SegmentTick` | +80 ms | +0 |
| 5 | Otherwise | none | | |

  Separate events:

| Event | Haptic | Rule |
|---|---|---|
| Game over | `Reject` | At T0 = commit + 300 ms in Full; at commit otherwise |
| Invalid move | `Reject` | At most 1 per 400 ms |
| Settings switches | `ToggleOn` / `ToggleOff` | |
| Undo, New game, other buttons | none | |

- **Rationale:**
  - O5;
  - research/04 §5 ("less is more"; Compose `HapticFeedbackType` in ui 1.12.1 includes `SegmentTick`, `Confirm`, `GestureThresholdActivate`, `Reject`, `ToggleOn`, `ToggleOff`);
  - research/07 C13 (512 rather than 128).
- **Rejected:**
  - research/04's ≥ 128 threshold, Subtle/Full levels, and `ToggleOff` on undo;
  - stacking several haptics on one move.
- **Source:** [ORCH] O5.
- **Note:** Extended by D-185 (from origin; no invalid-move haptic below API 30).

#### D-066 — Haptic implementation rules
- **Decision:**
  - Use `LocalHapticFeedback.current.performHapticFeedback(type)` only, fired from `LaunchedEffect(board.moveId)` on the same frame as the visual cue.
  - Never use `Vibrator`, `VibrationEffect` or `FLAG_IGNORE_GLOBAL_SETTING`.
  - The system touch-feedback setting is always respected.
  - `HapticsMapTest` asserts the event → type mapping and the rate limit with a fake `HapticFeedback`.
- **Rationale:** research/04 §5 (no `VIBRATE`; fallbacks via `HapticFeedbackConstantsCompat`).
- **Rejected:** `Vibrator` / `VibrationEffect` primitives (they need VIBRATE); haptic calls scattered across the UI (one wrapper keeps the rate limit testable).
- **Source:** [ORCH] O5, [USER] U6.
- **Note:** Extended by D-185.

---

## 9. Visual design & theming

#### D-070 — Classic Refined is the default palette
- **Decision:**
  - The default palette is **Classic Refined**, in light and dark. Theme mode System follows `isSystemInDarkTheme()`.
  - Tile fills, inks, board, empty cell, background and the chrome scheme are **exactly** the tables in research/04-design-motion.md §8 A (light and dark), for tiles 2…2048.
  - Super tiles are defined in D-072.
  - The chrome is M3 `SchemeContent` from `#8F7A66`, with background and surface overridden (research/04 §8 A).
  - Score cards use `surfaceContainerHigh`: the value in `onSurface`, the label in `onSurfaceVariant`.
- **Rationale:** it keeps the 2048 identity while every tile reaches ≥ 3.66:1 and normal-vision ΔE ≥ 3.5 (research/04 §1, §8 A).
- **Rejected:** shipping the legacy hexes (1.42–2.96:1); Material You as the default.
- **Source:** [ORCH] O6 · [RESEARCH] research/04 §8 A.
- **Note:** Extended by D-189; amended by D-207 (one value).

#### D-071 — Theme mode and Neon Arcade
- **Decision:**
  - Theme mode: **System** / Light / Dark.
  - Palette: **Classic Refined** / **Neon Arcade**.
  - Neon Arcade is exactly research/04 §8 C. Its board is always dark (`#1A1730`, empty `#25213F`). The light theme changes only the app background (`#F3F1FA`) and the chrome scheme (seed `#5B5BD6`).
  - Palettes implement `interface TilePalette { fun fill(exp: Int): Color; fun ink(exp: Int): Color; fun glow(exp: Int): Glow?; val board: Color; val empty: Color; val background: Color }`. They are provided through `LocalTilePalette`.
- **Rationale:** research/04 §8; research/02 §3.3 (the broken legacy night mode).
- **Rejected:** research/04 B "Material You" as a selectable tile palette in v1.0 (D-074).
- **Source:** [ORCH] O6.
- **Note:** Extended by D-189.

#### D-072 — Super tiles (exponent ≥ 12)
- **Decision.** Each palette gets a distinct ramp. Exponents ≥ 17 share the last color.

| Palette | Ink | 4096 | 8192 | 16384 | 32768 | 65536 | ≥ 131072 |
|---|---|---|---|---|---|---|---|
| Classic light | `#FFFFFF` | `#3C3A32` | `#013D3D` | `#062D59` | `#36164A` | `#420420` | `#201710` |
| Classic dark | `#1B1916` | `#F1E8D8` | `#B1EDEC` | `#C3D6FE` | `#E4BDE6` | `#EFAFB3` | `#FAFAFA` |
| Neon (both modes) | `#12101F` | `#EEF6FE` | `#AEF9EE` | `#DDD9FE` | `#FBBFE6` | `#FEB6A4` | `#FDFDFD` |

  Verified 2026-10-28 with WCAG and OKLab math matching research/04 §8. The minimum ink contrast is 11.39 (Classic light), 9.57 (Classic dark) and 11.11 (Neon). The minimum adjacent ΔE_OK×100, including 2048 → 4096, is:

| Palette | Normal | Protan | Deutan | Tritan |
|---|---|---|---|---|
| Classic light | 6.3 | 2.1 | 3.0 | 2.9 |
| Classic dark | 6.9 | 3.0 | 3.5 | 5.1 |
| Neon | 7.1 | 2.2 | 3.5 | 5.0 |

  All pass the research/04 §6.6 gates (≥ 3.0 normal, ≥ 2.0 under CVD).
- **Rationale:**
  - A reviewer complained that every tile above 2048 is the same color (research/05 §4.4).
  - research/04 §8 defines only one super color.
- **Rejected:** a single super color (legacy and research/04); hue-only ramps at constant lightness, which fail deutan.
- **Source:** [RESEARCH] (computed for this log).
- **Note:** Clarified by D-205.

#### D-073 — High-contrast variant
- **Decision:**
  - High contrast is active when the setting is On, or when on API 34+ `UiModeManager.getContrast() ≥ 0.5`, observed with `addContrastChangeListener`.
  - When active:
    - every tile's ink becomes whichever of `#000000` and `#FFFFFF` has the higher contrast against its fill;
    - every tile gets a 1.5 dp outline in the chrome `outline` color;
    - the chrome colors are unchanged.
  - Board and fills come from the selected palette.
- **Rationale:** research/04 §3.4; research/05 §4.4.
- **Rejected:** a separate high-contrast palette with new hues (more goldens; the black/white ink rule already guarantees contrast).
- **Source:** [ORCH] O6.
- **Note:** Extended by D-206.

#### D-074 — Dynamic color is for chrome only
- **Decision:**
  - With "Dynamic color (app chrome)" On (API 31+ only; default Off), the M3 color scheme comes from `dynamicLightColorScheme(context)` or `dynamicDarkColorScheme(context)`.
  - Board, empty cells, tiles and the app background always come from the palette.
  - A dynamic **tile ramp** (research/04 §8 B, HCT vendoring) is deferred to v1.x.
- **Rationale:**
  - The 13 system tonal slots produce duplicate tiles for some seeds (research/04 §3.4).
  - The HCT ramp costs about 2,100 vendored lines for a cosmetic option (research/07 C15).
- **Rejected:** research/04 §8 B in v1.0.
- **Source:** [ORCH] O6.

#### D-075 — Board geometry (N×N)
- **Decision.** Inputs:
  - `availW` and `availH` = window minus `WindowInsets.safeDrawing` minus margins (compact 16 dp, medium 24 dp, expanded 32 dp).
  - The side margin is also at least the system-gesture inset (D-051).

  Board size per mode (D-091):

| Mode | Board size |
|---|---|
| Stacked | `clamp(min(availW, availH − 240dp), 280dp, 720dp)` |
| 3-column | `min(availH, availW − 2·rail − 24dp)`, with `rail = clamp(0.22·W, 160dp, 240dp)` |
| Side panel | `min(availH, availW − panel − 32dp, 720dp)`, with `panel = clamp(0.32·W, 280dp, 400dp)` |

  - If `availW < 280dp`: `board = max(220dp, availW)`, and the title row is hidden.

  Cells:
  - `tileRaw = board / (N + r·(N+1))`, with `r = 0.14` (Classic) or `0.18` (Neon).
  - `gap = clamp(round_px(r·tileRaw), 2dp, 20dp)`.
  - `tile = (board − (N+1)·gap) / N`.
  - Tile radius: Classic `clamp(0.07·tile, 2dp, 10dp)`; Neon `clamp(0.22·tile, 4dp, 34dp)`.
  - Board radius = tile radius + gap.

  Check: 4×4 Classic on a 328 dp board gives gap 10 dp and tile 69.5 dp, matching research/04 §7.3.
- **Rationale:** research/04 §7.3 (4×4 formula, legacy gap/tile ratio 0.141); research/07 C23 (generalize to N).
- **Rejected:** a gap of 3 % of the board for every N, which makes 8×8 gaps a third of the tile width.
- **Source:** [ORCH] O7 · [RESEARCH].
- **Note:** Amended by D-188.

#### D-076 — Typography and bundled font
- **Decision.**
- **Asset:** `app/src/main/res/font/google_sans_flex_numerals.ttf`, a subset of Google Sans Flex (OFL-1.1, no Reserved Font Name). Specification:
  - glyphs U+0020, U+002B "+", U+0030–U+0039, and E, G, K, M, P, T;
  - axes instanced to `wght 700–900`, `wdth 75–100`, `ROND 0–100`; `opsz` pinned to 18; `slnt` pinned to 0;
  - features kept: `tnum lnum pnum kern`, with `--name-IDs='*'`;
  - **≤ 32 KiB**, enforced by D-152.
- **Generation:** `scripts/fonts/subset-google-sans-flex.sh`, using fontTools 4.68 (`fonttools varLib.instancer` then `pyftsubset`).
  - Source: `google/fonts` `ofl/googlesansflex/GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf`.
  - The script header records the source commit SHA and the file's sha256.
  - The OFL text is committed as `app/src/main/res/raw/ofl_google_sans_flex.txt`.
- **Usage:** the font is used **only** for digit-only game numerals (tile labels, score and best values, "+N", compact labels) and the "2048" title.
- **Everything else** uses `FontFamily.Default`: every string resource, prose with numbers, and the board-size chip.
- **Weights:**
  - Classic numerals `wght 800, ROND 0`; Neon `wght 900, ROND 100`.
  - Title `wght 850`, 56 sp compact / 64 sp expanded, `maxLines = 1`, auto-size down to 32 sp.
  - Score values `wght 750`, `"tnum, lnum"`, `TextAutoSize.StepBased(14.sp, 28.sp)`.
  - "+N" 16 sp `wght 800` tnum.
- UI type follows the M3 scale in research/04 §9.4.
- **Fallback:** if the source TTF or fontTools cannot be obtained during execution, numerals use `FontFamily.Default` Bold with `"tnum"`, the OFL file is omitted, and the PR body records it.
- **Rationale:**
  - GSF has no Cyrillic, Greek, Persian digits or Esperanto ĉĝĥĵŝ (research/07 C16, U5), so it must never style translated text.
  - A digit subset is about 18 KB (research/04 §9.1).
- **Rejected:** Clear Sans (no `tnum`, Latin only; research/04 §9.2); Inter for all UI text; downloadable fonts.
- **Source:** [ORCH] O6 · [RESEARCH]. O6's "tile numerals + title" is read to include every digit-only game numeral listed under **Usage**, because they need `tnum` and carry no translated text (OI-3, settled).

#### D-077 — Tile label sizing and compact labels
- **Decision.** Font size in **dp** (it ignores `fontScale`) as a fraction of the tile side `t`, with letter spacing −0.02 em and `LineHeightStyle(Center, Trim.Both)`:

| Characters | Size | `wdth` |
|---|---|---|
| 1–2 | 0.50·t | 100 |
| 3 | 0.40·t | 100 |
| 4 | 0.32·t | 87.5 |
| 5 | 0.27·t | 75 |
| 6 | 0.23·t | 75 |
| ≥ 7 | 0.84·t / (chars·0.603) | 75 |

- **Compact labels:**
  - If the full decimal label would be **< 12 dp** and `exp ≥ 10`, render `2^(exp % 10)` followed by the suffix for `exp / 10`: 1 → K, 2 → M, 3 → G, 4 → T, 5 → P, 6 → E.
  - Examples: 2^14 → "16K", 2^20 → "1M", 2^65 → "32E".
  - The compact label is sized by its own character count.
  - Values < 1024 always show the full number.
- The cell's accessibility text always carries the full decimal value (D-080).
- **Rationale:** research/04 §9.3 (ratios); research/05 §4.2 (OFG-style suffixes); O7 ("when a cell is too small").
- **Rejected:** research/05's fixed rule "5 digits on boards ≥ 6×6" (it ignores the actual cell size).
- **Source:** [ORCH] O7 · [RESEARCH].

#### D-078 — Tile effects
- **Decision.**
- **Classic:**
  - Tiles 128–2048 get `Modifier.dropShadow` with color `#F3D774`, alpha 0.19 / 0.25 / 0.32 / 0.38 / 0.44, radius `0.20·t` and spread `0.03·t`.
  - Inner highlight: `innerShadow(white, 0.05 → 0.11)`.
  - In dark mode, alphas are multiplied by 1.15.
- **Neon:** tiles ≥ 128 glow in their own fill color with alpha 0.35 → 0.65; a glassy `innerShadow`; the board is an inset well (research/04 §8 C).
- **All other tiles are flat.** There are no infinite glows. The ≥ 512 glow pulse happens once per merge (research/04 §10.4).
- **Rationale:** research/04 §8 A/C (the legacy glow formula carried at 0.8×); research/04 §6.1 (WCAG 2.2.2: no infinite animation).
- **Rejected:** the legacy box-shadow strength (glare in dark mode); breathing glows.
- **Source:** [RESEARCH] research/04 §8.

#### D-079 — Icons
- **Decision:**
  - Material Symbols **Rounded** vector XMLs (Apache-2.0), downloaded once and committed under `app/src/main/res/drawable/ic_*.xml`, used with `painterResource`.
  - Direction-pad arrows use `android:autoMirrored="false"`.
- **Rationale:** `material-icons-*` is frozen and not recommended (research/03 §1.6).
- **Rejected:** `material-icons-extended` (frozen at 1.7.8, slows builds); an icon font (a download or a large bundle).
- **Source:** [RESEARCH].

---

## 10. Accessibility

#### D-080 — Board semantics
- **Decision.** The board is one node with:
  - `collectionInfo = CollectionInfo(N, N)`;
  - `contentDescription` "Game board, N by N. {count} tiles. Highest tile {value}.";
  - custom actions "Move up", "Move down", "Move left", "Move right", "Undo" (present only when undo is available) and "New game".
- Each of the N² **cells** is a stable semantics node:
  - `collectionItemInfo(row, 1, col, 1)`;
  - label "Row {r}, column {c}, {full value}" or "Row {r}, column {c}, empty".
- Animated tiles carry no semantics.
- Traversal groups run in this order: score group → board → controls. `traversalIndex` is set in the 3-column and side-panel layouts.
- **Rationale:** research/04 §6.3; research/05 §4.21 (TalkBack consumes swipes).
- **Rejected:** per-tile semantics (tiles are transient during animation, research/04 §6.3); swipe-only play under TalkBack.
- **Source:** [RESEARCH].

#### D-081 — Announcements
- **Decision:**
  - A status line node with `liveRegion = Polite` is composed only while touch exploration is enabled, above the direction pad (D-082).
  - Its text after each move contains:
    - the direction;
    - the merged values (up to 3, then "and {n} more");
    - the score.
  - After an invalid move it reads "Can't move {direction}".
  - Win and game over use `LiveRegionMode.Assertive` once, set `paneTitle` on the card, and move focus to the card's primary button.
  - `View.announceForAccessibility` is never used. There is no verbosity setting in v1.0.
- **Rationale:** `announceForAccessibility` is `@Deprecated` in API 36 and 37 (research/04 §2, §6.3).
- **Rejected:** research/04's Brief/Detailed verbosity setting (deferred).
- **Source:** [RESEARCH].
- **Note:** Amended by D-202.

#### D-082 — TalkBack direction pad
- **Decision:**
  - While `AccessibilityManager.isTouchExplorationEnabled` is true (observed through a listener), a direction pad appears below the board: four 64 dp buttons (Up, Left, Right, Down) plus Undo.
  - The pad is forced LTR (D-104).
- **Rationale:** research/04 §6.3.
- **Rejected:** always showing the pad (clutter for sighted players); relying on custom actions alone (slow for frequent moves).
- **Source:** [RESEARCH].

#### D-083 — Contrast policy and test
- **Decision:**
  - Tile ink ≥ **3.0:1** for values with 1–3 digits (bold, large text) and ≥ **4.5:1** for ≥ 4 digits and for compact labels.
  - UI text ≥ 4.5:1 through the M3 roles. Non-text indicators ≥ 3:1.
  - Adjacent tile ΔE_OK×100 ≥ 3.0 for normal vision and ≥ 2.0 under the Machado-2009 protan, deutan and tritan simulations (severity 1.0).
  - `PaletteContrastTest` (JVM) computes all of these for every palette (Classic light and dark, Neon light and dark, each high-contrast variant) and exponents 1–17.
- **Rationale:** research/04 §6.4, §6.6.
- **Rejected:** research/06 §5.3's blanket "4.5:1 for all tile text" (Classic 32 is 3.66:1 by design).
- **Source:** [RESEARCH].
- **Note:** Clarified by D-205.

#### D-084 — Touch targets, font scale, focus
- **Decision:**
  - Every interactive element is ≥ 48×48 dp (`minimumInteractiveComponentSize`).
  - Chrome text is in sp and must not clip or truncate at `fontScale` 2.0. Labels wrap to two lines.
  - Tile numerals ignore `fontScale` (D-077).
  - Focus indicators are visible with ≥ 3:1 contrast.
  - Color is never the only cue, because the number is always printed.
- **Rationale:** research/04 §6.3–§6.5 (48 dp; non-linear font scaling to 200 %); WCAG 1.4.11.
- **Rejected:** scaling tile numerals with `fontScale` (they already fill the fixed geometry, research/04 §6.5).
- **Source:** [RESEARCH] research/04 §6.3–6.5.
- **Note:** Extended by D-205.

#### D-085 — Automated accessibility checks
- **Decision:**
  - Every screen-level Roborazzi test runs ATF through `roborazzi-accessibility-check` 1.77.0 with `RoborazziATFAccessibilityChecker(preset = AccessibilityCheckPreset.LATEST)`, `failureLevel = CheckLevel.Error` and `AccessibilityCheckAfterTestStrategy()`.
  - Semantics tests assert the D-080 actions and labels.
- **Rationale:** research/06 §1.5.
- **Rejected:** `ui-test-junit4-accessibility` (documented for instrumented runs only, research/06 §1.5); manual TalkBack QA as the only check.
- **Source:** [ORCH] O16 · [RESEARCH].
- **Note:** Extended by D-204, D-205.

---

## 11. Adaptive layout & orientation

#### D-090 — Orientation and manifest
- **Decision.** The manifest has none of these: `screenOrientation`, `configChanges`, `resizeableActivity`, aspect-ratio attributes, `enableOnBackInvokedCallback` (it defaults to true at target 36+).

  `<application>` attributes:

| Attribute | Value |
|---|---|
| `android:name` | `.Game2048Application` |
| `android:label` | `@string/app_name` |
| `android:icon` | `@mipmap/ic_launcher` |
| `android:roundIcon` | `@mipmap/ic_launcher_round` |
| `android:theme` | `@style/Theme.Game2048.Starting` |
| `android:supportsRtl` | `true` |
| `android:appCategory` | `game` (with `tools:targetApi="26"`) |
| backup attributes | per D-048 |

  Other elements:
  - `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`.
  - `<provider android:name="androidx.startup.InitializationProvider" tools:node="merge">` containing `<meta-data android:name="androidx.emoji2.text.EmojiCompatInitializer" tools:node="remove"/>`.
  - The single activity `.MainActivity` has `exported="true"` and a `MAIN`/`LAUNCHER` filter.
- **Rationale:**
  - At target 37, orientation locks are ignored on sw ≥ 600 dp, with games exempt through `appCategory` (research/01 §4.5; research/03 §4).
  - `appCategory` is a conscious product choice here (research/04 §11.7).
  - Keyboard and D-pad play allows touch-less Chromebooks (research/07 G11).
  - EmojiCompat is unused (research/07 G13).
- **Rejected:** portrait lock; `configChanges`.
- **Source:** [ORCH] O11 · [RESEARCH].

#### D-091 — Layout modes
- **Decision.** Evaluated in this order:
  1. **Window height < 480 dp:** 3-column, `[stats rail] [board] [actions rail]`.
  2. **Window width ≥ 840 dp:** board + side panel. The panel holds the score cards, actions, size chip and menu, and the board is vertically centered.
  3. **Otherwise:** stacked. The header (title + score + best + size chip) is 112 dp, then the board, then the controls bar (80 dp).
- Margins are given in D-075.
- Foldable posture layouts (tabletop/book) are **deferred to v1.x**.
- Example window sizes and their mode:

| Window | Mode |
|---|---|
| 360×800 | Stacked |
| 915×412 | 3-column |
| 673×841 | Stacked |
| 841×673 | Side panel |
| 800×1280 | Stacked |
| 1280×800 | Side panel |

- **Rationale:**
  - research/04 §7.2 (modes);
  - research/07 G11 (`window-testing` is unverified under Robolectric, so postures are deferred);
  - O11 (tablet landscape = board + side panel).
- **Rejected:** a portrait-only layout (wastes landscape, split-screen and desktop windows; research/05 §4.22); posture layouts in v1.0 (untestable on the JVM).
- **Source:** [ORCH] O11 · [RESEARCH].
- **Note:** Amended by D-188.

#### D-092 — Window size API and layout tests
- **Decision:**
  - Use `currentWindowAdaptiveInfoV2().windowSizeClass` with `isWidthAtLeastBreakpoint(840)` / `isHeightAtLeastBreakpoint(480)` (the `WindowSizeClass` constants).
  - Layout goldens (D-147) cover these Robolectric qualifiers:
    - `w360dp-h800dp`
    - `w412dp-h915dp`
    - `w915dp-h412dp-land`
    - `w673dp-h841dp`
    - `w841dp-h673dp-land`
    - `w800dp-h1280dp`
    - `w1280dp-h800dp-land`
- **Rationale:** research/07 C4 (the V1 API is deprecated), G11.
- **Rejected:** `material3-window-size-class` (older API, research/03 §1.7); legacy screen-size buckets.
- **Source:** [RESEARCH].

#### D-093 — Edge-to-edge and Immersive mode
- **Decision:**
  - `enableEdgeToEdge()` runs before `setContent`, always.
  - The background draws behind the system bars. Content pads with `safeDrawing`.
  - System-bar icon appearance follows the effective theme.
  - **Immersive mode** (setting, default Off) calls `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. It is applied app-wide whenever it is On and re-applied on window-focus gain.
  - The legacy long-press gesture is removed.
- **Rationale:** research/01 §4.3–4.4, D12–D13; research/03 §4 (edge-to-edge is enforced at target 36+).
- **Rejected:** `FLAG_FULLSCREEN`; a gesture toggle.
- **Source:** [ORCH] O11.

#### D-094 — Predictive back
- **Decision:**
  - No `onBackPressed` override, and no enabled `OnBackPressedCallback` or `BackHandler` on the Game root.
  - `BackHandler` / `NavDisplay` handle only sheets, dialogs and sub-screens (D-054).
  - There is no double-back-to-exit.
- **Rationale:** at target 36+ on Android 16+, `onBackPressed` is not called and back-to-home is predictive (research/01 §4.11; research/03 §4).
- **Rejected:** `android:enableOnBackInvokedCallback="false"` (a temporary opt-out); "press back again to exit" (research/01 §4.11).
- **Source:** [ORCH] O11.

#### D-095 — Recreation and mode transitions
- **Decision:**
  - Activity recreation (rotation, resize, fold, locale, `uiMode`) restores from the ViewModel and DataStore with `Static` tiles (D-047).
  - Changes between layout modes animate the board and panel bounds with `animateBounds` under Full motion only.
- **Rationale:** research/07 G9 (recreation must not replay animations); research/04 §7.3 (`animateBounds` for mode changes).
- **Rejected:** `configChanges` to avoid recreation (fold, locale and `uiMode` changes still recreate; research/01 §3).
- **Source:** [RESEARCH] research/04 §7.3.

---

## 12. Localization & RTL

#### D-100 — Locale set and completeness
- **Decision:**
  - Ship English (`values/`) plus the 20 legacy app locales, verified from `res/`: **cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, pt, ru, sl, sr, tr, uk**.
  - Every string resource is translated in all 20 before merge.
  - Lint `MissingTranslation` and `ExtraTranslation` are errors (D-142).
  - `sr` stays Cyrillic. `pt` stays generic Portuguese.
- **Rationale:**
  - 18 of the 20 legacy locales saw an English game (research/01 §4.10; research/02 §6);
  - research/07 C22.
- **Rejected:**
  - research/05 §4.24 (only ru/uk at v1.0);
  - research/06 §2.1 (`MissingTranslation` informational);
  - adding the 16 listing-only locales.
- **Source:** [ORCH] O13.
- **Note:** Clarified by D-208.

#### D-101 — Legacy string migration
- **Decision.** In M0, `git mv res/values*/strings.xml` to `app/src/main/res/values*/strings.xml`, then:
  - rename `action_settings` to `settings` and keep its 20 translations;
  - delete `press_back_again_to_exit` and `toggle_fullscreen`;
  - strip the UTF-8 BOM from `values-cs`;
  - delete `app_name` from **all 20** locale files, including the `values-fa` "۲۰۴۸" override, and mark the default `app_name` (`2048`) `translatable="false"`. Lint rejects translations of an untranslatable string (issue `Untranslatable`; technical correction TC-3).
- The typos listed in research/01 §5.1 (cs, pl, lt) are all in the two deleted keys, so they need no fix. The surviving `settings` values are correct as shipped.
- Seed the ru and uk game strings from `js/i18n.js` (research/01 §8.1), with uk `cancel` changed to "Скасувати".
- Seed the "Immersive mode" translations from the "full screen" noun table (research/01 §8.1).
- **Rationale:** research/07 G2 step 4, G7.
- **Rejected:** dropping the legacy translations (it loses 20 shipped `settings` translations); keeping the hint strings (`UnusedResources` is fatal under D-142).
- **Source:** [ORCH] O13 · [RESEARCH].
- **Note:** Technical correction TC-3.

#### D-102 — Locale config and per-app language
- **Decision:**
  - `androidResources { generateLocaleConfig = true }`, plus `app/src/main/res/resources.properties` containing `unqualifiedResLocale=en-US` (smoke-verified).
  - On API 33+, Settings shows "App language", which opens `Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null))`. The row is hidden below API 33.
  - No AppCompat and no in-app picker.
- **Rationale:** research/03 §6, §9; research/07 C24.
- **Rejected:** `AppCompatDelegate.setApplicationLocales` (it adds AppCompat for API 24–32).
- **Source:** [ORCH] O13.
- **Note:** Clarified by D-208.

#### D-103 — Plurals and number formatting
- **Decision:**
  - Every count uses `<plurals>` + `pluralStringResource`: moves, games, undos, tiles, merges. ru/uk/pl/cs/lt/sl have 3–4 plural categories.
  - No string concatenation for sentences.
  - **Game numerals** (tiles, score, best, "+N", the share summary) use `Long.toString()`, which always gives Latin digits and no grouping.
  - **Prose numbers** are pre-formatted by one helper, `formatCount(n, locale)` in `ui/components/Formatting.kt`. It uses `NumberFormat.getIntegerInstance(Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build())`, which gives Latin digits with the locale's grouping. The result is passed as a `%1$s` argument.
- **Rationale:**
  - research/07 G7 (plurals; `fa` must not mix ۲۰۴۸ and 2048);
  - research/02 §6 (legacy digits are ASCII);
  - O11.
- **Rejected:** locale digits for game numerals (Google Sans Flex has no Persian digits; legacy parity); `String.format` with the default locale.
- **Source:** [ORCH] O11, O13.
- **Note:** Clarified by D-208.

#### D-104 — RTL
- **Decision:**
  - `android:supportsRtl="true"`. Chrome (header, controls, settings, stats) mirrors.
  - These run inside `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`:
    - the board subtree;
    - the gesture detector and its direction mapping;
    - the key map;
    - the TalkBack move actions;
    - the direction pad.
  - In every locale, a physical left swipe means `Direction.LEFT`.
  - RTL goldens use `fa`.
- **Rationale:**
  - research/07 G8 verified both behaviours under Robolectric: Compose mirrors only with `supportsRtl`, and forced-LTR subtrees hold;
  - research/07 C17 (`fa`, not `ar`).
- **Rejected:** mirroring the board in RTL (a left swipe would move tiles right, research/01 §10.1); `supportsRtl=false` (breaks `fa` chrome).
- **Source:** [ORCH] O11.

#### D-105 — Translation provenance and glossary
- **Decision.**
- **Provenance:** translations are produced by the executing agent and reviewed for:
  - placeholder and plural correctness;
  - length against the `de` goldens.
- **Glossary:** a fixed per-locale glossary is committed as `docs/modernization/i18n-glossary.md` before any translation. It covers tile, merge, undo, best, score, board size, move, keep going, new game, immersive mode and haptics.
- README states that translations are machine-assisted and corrections are welcome.
- **Rationale:** research/07 G7.
- **Rejected:** English fallbacks for untranslated strings (`MissingTranslation` is fatal, D-100); importing the machine-translated listing texts.
- **Source:** [RESEARCH].

#### D-106 — Pseudo-locales
- **Decision:**
  - `buildTypes.debug { isPseudoLocalesEnabled = true }`.
  - Goldens capture `en-XA` (expansion) and `ar-XB` (bidi).
  - If the DSL fails on AGP 9.4.1, drop it and those two goldens, and record this in the PR body.
- **Rationale:** research/07 G7, U13.
- **Rejected:** using real `ar` for bidi goldens (not a shipped locale; research/07 C17).
- **Source:** [RESEARCH].

---

## 13. Store listing, icon, splash, identity

#### D-110 — Identity
- **Decision:**
  - `applicationId` = `namespace` = `com.clayboicardi.game2048`.
  - The launcher label is `2048`, non-translatable.
  - Debug: `applicationIdSuffix = ".debug"`, with label `2048 Debug` from `app/src/debug/res/values/strings.xml` (`translatable="false"`).
- **Rationale:** debug and release install side by side; AGP 9 turns `resValues` off by default (research/03 §1.2), so the label uses a source-set override.
- **Rejected:** a translated or localized-digit label (U2); `resValue` for the debug label (needs `buildFeatures.resValues`).
- **Source:** [USER] U2 · [ORCH] O14.

#### D-111 — Launcher icon
- **Decision.** "2048 tile" identity. The adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`) has these layers:
  - background `@color/ic_launcher_background` = `#E6A11A`;
  - foreground `drawable/ic_launcher_foreground.xml`: the 4 legacy glyph paths from `res/drawable/ic_2048.xml`, filled `#3A2900` (6.33:1), wrapped in `<group android:scaleX="1.411" android:scaleY="1.411" android:translateX="16.667" android:translateY="16.667">` in a 108-unit viewport. That gives a 56 × 18.67 dp band inside the 66 dp safe zone;
  - `<monochrome>` = the same paths.
- API 24–25 fallback: `mipmap-anydpi/ic_launcher.xml`, a `<vector>` with a rounded square in `#E6A11A` and the glyphs.
- The icon is built in **M0**, because the manifest references `@mipmap/ic_launcher` and Lint fails without it. It is built from `res/drawable/ic_2048.xml`'s path data before that file is deleted (D-001).
- **Rationale:** research/01 §5.3 (geometry, contrast failures of `#aa8800`); research/07 G4.
- **Rejected:** the gold-text-on-transparent identity (3.37:1 on white); the mini-board icon.
- **Source:** [RESEARCH].

#### D-112 — Splash screen
- **Decision:**
  - `androidx.core:core-splashscreen:1.2.0`. Theme `Theme.Game2048.Starting` (parent `Theme.SplashScreen`):
    - `windowSplashScreenBackground` = `#FAF8EF`, and `#1B1916` in `values-night`;
    - `windowSplashScreenAnimatedIcon` = `@drawable/ic_launcher_foreground`;
    - `windowSplashScreenIconBackgroundColor` = `#E6A11A`;
    - `postSplashScreenTheme` = `@style/Theme.Game2048`, whose parent is `android:Theme.Material.Light.NoActionBar` (`android:Theme.Material.NoActionBar` in `values-night`), with `windowBackground` set to the Classic background.
  - `installSplashScreen().setKeepOnScreenCondition { !ready }`, where `ready` becomes true once settings, records and the current game are loaded, or after **1000 ms**, whichever comes first. If the game arrives after release, it renders `Static`.
  - `SplashGateTest` asserts that the first composed `BoardUi` equals the persisted fixture and contains no `Spawned` tiles.
- **Rationale:** research/07 G5 (otherwise a default board is composed and then swapped, the B7/B8 defect again).
- **Rejected:** releasing the splash before the state loads (a default board, then a swap: B7/B8); an unbounded hold (a slow disk would block start).
- **Source:** [ORCH] O10.

#### D-113 — Store metadata layout and locales
- **Decision.** `git mv metadata fastlane/metadata/android` in M0. The final tree has exactly **22** locale directories:
  - en-US, cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, **pt-BR, pt-PT**, ru, sl, sr, tr, uk.
  - The app's `pt` maps to both pt-BR and pt-PT.
  - The 16 listing-only locales are **deleted**: af, am, ar, bg, ca, et, fi, hi, id, ja, lv, ro, sv, sw, th, vi.
  - Graphics live only under `en-US/images/`.
  - `promoGraphic.png` is deleted.
- **Rationale:**
  - F-Droid and fastlane both read this path (research/01 §6; research/07 C8, G6).
  - The listing-only texts are mistranslations such as "harbour" and "robot" (research/01 §6).
- **Rejected:** keeping `metadata/` (fastlane `supply` reads only the fastlane path); keeping the 16 listing-only locales.
- **Source:** [ORCH] O13.
- **Note:** Extended by D-190.

#### D-114 — Store text
- **Decision.** Each locale directory contains:
  - `title.txt` = `2048`;
  - `short_description.txt` (≤ 80 characters). en-US: "Classic 2048 sliding-tile puzzle. Offline, no ads, no tracking.";
  - `full_description.txt` (≤ 4000);
  - `changelogs/30000.txt` (≤ 500).
- The full description must state:
  - offline;
  - no ads;
  - no tracking;
  - no runtime permissions;
  - board sizes 3×3–8×8;
  - unlimited undo;
  - themes and accessibility;
  - MIT, with the repository link;
  - credits;
  - the D-005 side-by-side note.
- It must not contain "port", "WebView", "long touch" or uberspot store links.
- **Rationale:** research/01 §6 (limits, obsolete copy).
- **Rejected:** reusing the legacy texts ("port", "WebView", the 3-second long touch; research/01 §6).
- **Source:** [ORCH] O13 · [RESEARCH].
- **Note:** Extended by D-190.

#### D-115 — Store graphics from code
- **Decision.** `app/src/test/kotlin/com/clayboicardi/game2048/store/StoreAssetsTest.kt` (Robolectric, SDK 36) writes into `fastlane/metadata/android/en-US/images/`. It is excluded from normal test runs and included only when `-PrecordStoreAssets=true` is passed. It produces:

| Asset | How it is rendered | Pixels |
|---|---|---|
| `icon.png` | The adaptive icon drawable | 512×512 |
| `featureGraphic.png` | A composable at `w512dp-h250dp-xhdpi`, captured with `captureToImage()` and written by `OpaquePngWriter` (D-190) | 1024×500, **no alpha** (PNG color type 2) |
| `phoneScreenshots/1.png`…`4.png` | `w360dp-h640dp-xxhdpi` | 1080×1920 |
| `sevenInchScreenshots/1.png` | `w1067dp-h600dp-land-xhdpi` | 2134×1200 |
| `tenInchScreenshots/1.png` | `w1280dp-h720dp-land-xhdpi` | 2560×1440 |

- Phone screenshot contents: a mid game, a win, dark Neon, and the statistics screen.
- `scripts/check-metadata.sh` validates the exact image set, dimensions, bit depth and color types (D-190).
- **Rationale:** research/01 §6 (Play forbids alpha; ≥ 1080 px screenshots); research/07 G4, G6.
- **Rejected:** hand-made or design-tool graphics (not reproducible; they drift from the app); the legacy images (405 px, alpha in the feature graphic).
- **Source:** [ORCH] O13.
- **Note:** Extended by D-190.

#### D-116 — README
- **Decision.** Rewrite `README.md`. It contains:
  - what the app is;
  - build steps (JDK 21, `./scripts/verify.sh`);
  - the side-by-side note (D-005);
  - the permission note (D-002);
  - links to PRIVACY.md and NOTICE;
  - credits;
  - the machine-assisted-translation note;
  - the meaning of the CI sideload key (D-131).
- Remove the Travis badge and the `com.uberspot.a2048` Play and F-Droid badges.
- **Rationale:** research/01 §6.8.
- **Rejected:** keeping the legacy README (a dead Travis badge; it advertises someone else's listing).
- **Source:** [RESEARCH].

---

## 14. Legal & privacy

#### D-120 — LICENSE
- **Decision.** `LICENSE` keeps the MIT text with three copyright lines, in this order:
  - `Copyright (c) 2014 Paul Sarbinowski`
  - `Copyright (c) 2014 Gabriele Cirulli`
  - `Copyright (c) 2026 clayboicardi`
- **Rationale:** both MIT notices are required because translations, icon paths, rules, hexes and golden behaviours are reused (research/01 §8.5; research/02 §7.10; research/07 G3).
- **Rejected:** replacing or relicensing (both upstream MIT notices must stay with reused material).
- **Source:** [ORCH] O15.

#### D-121 — NOTICE
- **Decision.** A plain-text `NOTICE` at the repository root lists:
  1. the game rules, palette and test vectors from `gabrielecirulli/2048` (MIT © 2014 Gabriele Cirulli), with its lineage line: "A small clone of 1024, based on Saming's 2048 (also a clone)";
  2. strings and icon glyph paths from `uberspot/2048-android` (MIT © 2014 Paul Sarbinowski) and its contributors;
  3. Google Sans Flex (OFL-1.1, subset);
  4. Material Symbols (Apache-2.0);
  5. AndroidX, Kotlin and kotlinx (Apache-2.0).
- **Rationale:** research/07 G3 (license matrix); research/02 §7.10; research/04 §9.1 (OFL); upstream `assets/2048/README.md` line 2 for the lineage text.
- **Rejected:** attribution only inside the app (a NOTICE file travels with the source).
- **Source:** [ORCH] O15 · [RESEARCH] research/07 G3.

#### D-122 — In-app licenses and coverage
- **Decision.**
  - `LicensesKey` renders `app/src/main/res/raw/third_party_licenses.json`, which is hand-curated. Each entry has `name`, `groupOrPath`, `licenseId` and `copyright`.
  - License texts live in `res/raw/license_mit.txt`, `license_apache_2_0.txt` and `ofl_google_sans_flex.txt`.
  - `config/license-groups.txt` maps every Maven group on `:app:releaseRuntimeClasspath` to a license ID.
  - `scripts/check-licenses.sh` fails if any resolved group is missing from that file.
  - `LicensesCoverageTest` asserts every listed group appears in the JSON.
- **Rationale:** research/07 G3 (a hand-curated list avoids GMS and extra plugins). The coverage check stops it from drifting as dependencies change.
- **Rejected:** `play-services-oss-licenses` (GMS); third-party license plugins (research/07 G3).
- **Source:** [ORCH] O15.

#### D-123 — Privacy
- **Decision.**
  - `PRIVACY.md` and the localized in-app About → Privacy text both say: "This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup."
  - README links to PRIVACY.md.
- **Rationale:** Play requires a privacy policy even for apps that collect no data (research/07 §0.6, G14).
- **Rejected:** no privacy text ("collects nothing" still needs a policy, research/07 G14); a hosted policy URL (the app is offline, and the repo file is linkable).
- **Source:** [ORCH] O15.

#### D-124 — Credits and legacy history
- **Decision.**
  - About → Credits names:
    - Gabriele Cirulli (original game);
    - Paul Sarbinowski / uberspot (Android port);
    - every legacy contributor (git authors of this repository up to `e097460` and of the submodule fork range `fc1ef4f..7a477eb`, deduplicated);
    - the fork owner.
  - About → Legacy history shows the 9 releases of `changelog_master.xml` (research/01 §5.2).
  - Both come from `res/raw/legacy_history.json` (English static data, not string resources).
  - M0 captures the raw names and history into `docs/modernization/legacy-credits.md` before deleting the submodule. M4 builds the JSON from it.
- **Rationale:** research/01 §5.2, §8.3 (9 releases, contributor credits); research/07 G3 (named contributors).
- **Rejected:** dropping the legacy history; translating it (credits and history are static English data).
- **Source:** [ORCH] O11, O15 · [RESEARCH] research/01 §8.3.
- **Note:** Extended by D-209.

---

## 15. Signing & versioning

#### D-130 — Version numbers
- **Decision:**
  - `versionName = "3.0.0"`, `versionCode = 30000`, written as **literals** in `app/build.gradle.kts`.
  - Scheme: `MAJOR·10000 + MINOR·100 + PATCH`.
- **Rationale:** research/01 §8.3; research/06 §4.5; research/07 C9. This also fixes the smoke build's `versionCode = 1`.
- **Rejected:** research/03's `versionCode = 1` snippet; git-count-derived codes (not reproducible from a tarball, research/06 §4.5).
- **Source:** [ORCH] O14.

#### D-131 — CI sideload keystore (intentionally public)
- **Decision.**
- **Generate once (M0)** with `scripts/signing/generate-ci-sideload-keystore.sh`, which runs:

  ```bash
  keytool -genkeypair -v -keystore signing/ci-sideload.p12 -storetype PKCS12 \
    -storepass game2048-ci-sideload -keypass game2048-ci-sideload -alias ci-sideload \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=2048 CI sideload key (public\; not for store uploads), O=clayboicardi"
  ```

  (technical correction TC-1: keytool needs the `;` escaped; the subject is unchanged).

- **Commit:**
  - `signing/ci-sideload.p12`;
  - `signing/ci-sideload.sha256`, the certificate SHA-256 digest;
  - `signing/README.md`, stating that the password is public, the key is for CI and sideload builds only, and it is never for store uploads.
- **Gradle:** `signingConfigs.create("ciSideload") { storeFile = rootProject.file("signing/ci-sideload.p12"); storePassword = "game2048-ci-sideload"; keyAlias = "ci-sideload"; keyPassword = "game2048-ci-sideload"; storeType = "PKCS12" }`.
  - **Both** the debug and release build types use it, so every CI-built APK of each variant updates in place.
- **Gate:** `check-apk.sh` asserts both APKs' signer digest equals `signing/ci-sideload.sha256`.
- **Rationale:** U5. The default debug keystore and ephemeral keys differ per runner (research/06 §3.5, §8 risk 1).
- **Rejected:** unsigned release artifacts (research/07 G18); ephemeral keys; GitHub secrets (none exist).
- **Source:** [USER] U5.
- **Note:** Technical correction TC-1.

#### D-132 — Build types
- **Decision:**
  - **debug:**
    - `applicationIdSuffix = ".debug"`;
    - not minified;
    - `isPseudoLocalesEnabled = true`;
    - signed with `ciSideload`.
  - **release:**
    - `isMinifyEnabled = true`, `isShrinkResources = true`;
    - `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`. The `proguard-rules.pro` file exists, holding only a header comment unless a rule is proven necessary;
    - signed with `ciSideload`.
  - No `benchmark` build type.
- **Rationale:** smoke-verified DSL (a 1,034,747 B release; research/03 §6). The `optimization {}` DSL has not been smoke-built (research/07 C6).
- **Rejected:** `optimization { enable = true }` for v1.0; `proguard-android.txt` (disallowed in AGP 9).
- **Source:** [ORCH] O14.

#### D-133 — No release workflow or store signing
- **Decision:**
  - No `release.yml`, no git tags, no GitHub Releases and no store uploads.
  - The deliverables are the CI artifacts: debug APK, release APK and `mapping.txt` (D-151).
  - Play or real release signing waits until the user adds secrets.
- **Rationale:** U5; research/07 G18.
- **Rejected:** research/06 §3.5's release workflow.
- **Source:** [USER] U5.

#### D-134 — Reproducibility hygiene and R8 rules
- **Decision.** The build:
  - sets `android { dependenciesInfo { includeInApk = false; includeInBundle = false } }`;
  - keeps BuildConfig generation off (AGP 9 default), and embeds no timestamps or build paths;
  - keeps the `vcsInfo` default;
  - uses one JDK (21);
  - generates no custom baseline profile; library profiles merged by AGP are fine;
  - has **no `@Serializable` class with a named companion object**, so kotlinx-serialization's bundled R8 rules suffice.
- **Rationale:** research/06 §4.1, §4.3; F-Droid rejects the encrypted dependency block.
- **Rejected:** custom baseline profiles (a known F-Droid nondeterminism, research/06 §1.7); `-dontobfuscate` or blanket `-keep` rules.
- **Source:** [RESEARCH].

---

## 16. Quality & CI gates

#### D-140 — `scripts/verify.sh` is the gate
- **Decision.** The executor runs the script locally before every push. CI runs exactly the same script (D-151). The canonical content:

```bash
#!/usr/bin/env bash
# Single merge gate. CI runs exactly this script. Exit 0 = every stage passed.
set -uo pipefail
cd "$(dirname "$0")/.."
export ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-37.0.0}"
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"   # local-only extras, e.g. an out-of-repo init script (D-019)
fail=0
./gradlew "${EXTRA[@]}" --continue \
  spotlessCheck detektMain detektTest \
  :core:engine:test :core:engine:koverVerify \
  :app:testDebugUnitTest -Proborazzi.test.verify=true \
  :app:lintDebug :app:assembleDebug :app:assembleRelease || fail=1
for s in check-test-counts check-apk check-licenses check-metadata; do
  "./scripts/$s.sh" || { echo "FAILED: $s"; fail=1; }
done
exit "$fail"
```

- If detekt is dropped (D-144), the same commit removes `detektMain detektTest` from the script.
- **Rationale:** research/06 §6 (`G-*` gates); one script means local == CI.
- **Rejected:** CI-only gates that differ from local runs (they break D-163); separate CI steps with separate Gradle invocations.
- **Source:** [ORCH] O16, O17.
- **Note:** Extended by D-178, D-191.

#### D-141 — Formatting
- **Decision:**
  - Spotless 8.10.4 at the root:
    - `kotlin { target("**/*.kt"); targetExclude("**/build/**", "docs/**"); ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle() }` (D-175);
    - the same for `kotlinGradle` on `**/*.gradle.kts`.
  - ktfmt is **0.65**. If 0.65 fails to resolve or is not idempotent, use 0.64 (smoke-verified) and record it.
  - `spotlessApply` runs before every commit.
- **Rationale:** 0.65 is the Spotless 8.10.4 default (research/06 §2.3; research/07 C5).
- **Rejected:** ktlint 1.8.0 (older Kotlin parser, research/06 §2.3); no formatter.
- **Source:** [ORCH] O16.
- **Note:** Extended by D-175.

#### D-142 — Android Lint
- **Decision.** In `:app`:

```kotlin
lint {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = true
    checkDependencies = true
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    error += setOf("MissingTranslation", "ExtraTranslation")
    sarifReport = true
}
```

  Plus `lintChecks(libs.compose.lint.checks)`.
- **No baseline file.** Any `tools:ignore` or `@Suppress` needs a same-line comment giving the reason.
- **Rationale:** research/06 §2.1; research/07 C22 (`MissingTranslation` is an error).
- **Rejected:** a lint baseline (fresh code starts at zero findings, research/06 §2.1); informational `MissingTranslation`.
- **Source:** [ORCH] O16.
- **Note:** Clarified by D-208.

#### D-143 — Kotlin compiler strictness
- **Decision:**
  - `kotlin { compilerOptions { allWarningsAsErrors = true } }` in both modules.
  - **No** `optIn` entries.
  - Any needed opt-in requires its own decision entry, and its use is isolated to one file. The planning-time opt-ins are D-174 and D-181; any later one goes through D-173.
- **Rationale:** it forces the v2 test APIs and `currentWindowAdaptiveInfoV2` (research/06 §2.2; research/07 C4).
- **Rejected:** per-file `@Suppress` of deprecations (it hides required migrations such as the V2 APIs).
- **Source:** [ORCH] O1, O16.
- **Note:** Amended by D-174, D-181.

#### D-144 — detekt
- **Decision:**
  - `dev.detekt` 2.0.0-alpha.6 applied to both modules:
    - `detekt { buildUponDefaultConfig = true; config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml")); parallel = true }` (technical correction TC-5);
    - the config is generated with `detektGenerateConfig`, then tuned per research/06 §2.4: `FunctionNaming ignoreAnnotated Composable`, `MagicNumber` excluded for `**/ui/theme/**` and tests, `MaxLineLength 100`.
  - No compose-rules plugin and no baseline.
  - **Exit criterion:** if `detektMain` fails for tool reasons (a crash, not findings) and one fix attempt does not resolve it, remove detekt entirely in one commit and say so in the PR body.
- **Rationale:** 1.23.8 is incompatible; the alpha is smoke-verified (research/03 §1.10; research/06 §2.4; research/07 C20).
- **Rejected:** detekt 1.23.8 (incompatible); compose-rules in detekt (they double-report the Slack lint findings, research/06 §2.4).
- **Source:** [ORCH] O16.
- **Note:** Technical correction TC-5.

#### D-145 — Engine tests and coverage
- **Decision:**
  - JUnit Jupiter 6.1.3 (`useJUnitPlatform()`) + kotest-property 6.2.6. Each property class seeds with `PropertyTesting.defaultSeed = 0x2048L` (and `writeFailedSeed = false`) in `@BeforeEach`, and runs 1,000 iterations through `checkAll(1_000, …)` (technical correction TC-6).
  - Boards are generated with N ∈ 3..8 and exponents 0..17, weighted toward 0.
  - Test bodies are **block-bodied**; there are no expression-bodied `= runBlocking { … }` tests.
  - Suites:
    - `EngineGoldenTest` (D-032 rows);
    - `SplitMix64GoldenTest` (D-038);
    - `EnginePropertiesTest`, with the 13 properties of research/06 §1.1 adapted: undo instead of undo/redo, and history-bytes round-trip instead of JSON;
    - `SpawnDistributionTest`: 100,000 spawns; the 4-rate lies in [0.095, 0.105] and every empty cell is within ±10 % of uniform;
    - `EngineImportsGuardTest` (D-021);
    - `EnginePerformanceTest` (D-150).
  - Kover 0.9.11 on `:core:engine`: `koverVerify` line ≥ **90 %** and branch ≥ **85 %**. The DSL follows research/06 §1.1; its field names were verified during planning (`minBound(…, CoverageUnit, AggregationType)`, ARCHITECTURE.md §0).
- **Rationale:** research/06 §1.1; research/03 §1.9 (the silently skipped expression-bodied test trap).
- **Rejected:** JUnit 4 for the engine (a pure JVM module runs Jupiter natively); jqwik (kotest-property is smoke-verified).
- **Source:** [ORCH] O16.
- **Note:** Technical correction TC-6.

#### D-146 — App tests
- **Decision.**
- **Frameworks:** JUnit 4.13.2, Robolectric 4.17, Compose ui-test **v2** (`androidx.compose.ui.test.junit4.v2.createComposeRule`), kotlinx-coroutines-test 1.11.0 (a `MainDispatcherRule` with `StandardTestDispatcher`), Turbine 1.2.1, Truth 1.4.5, and the real DataStore on JUnit `TemporaryFolder` files.
- **Robolectric configuration:**
  - `app/src/test/resources/robolectric.properties` = `sdk=36`;
  - `LaunchSmokeTest` uses `@Config(sdk = [24, 37])`;
  - every rendering test is annotated `@GraphicsMode(GraphicsMode.Mode.NATIVE)`;
  - `testOptions.unitTests { isIncludeAndroidResources = true; all { it.maxHeapSize = "3g"; it.jvmArgs(<the 9 --add-opens of research/03 §6>) } }`;
  - `testImplementation(libs.androidx.test.espresso.core)` at 3.7.0, forced.
- **Named suites** (behaviour assertions are written per milestone in the plan):
  - `GameViewModelTest`, `InputBurstOrderingTest`, `PersistenceFixturesTest`, `PersistenceCorruptionTest`, `PersistenceBudgetTest`;
  - `ProcessDeathRestoreTest`, `RecreateNoReplayTest`, `SplashGateTest`;
  - `KeyboardInputTest`, `SwipeInputTest`, `HapticsMapTest`, `BoardSemanticsTest`;
  - `WhatsNewTest`, `BackupRulesTest`, `LicensesCoverageTest`, `PaletteContrastTest`, `TileRecompositionTest`.
- **Rationale:** research/03 §6 (the two required fixes), §1.9; research/06 §1.2–1.3; research/07 C18.
- **Rejected:** `robolectric.pixelCopyRenderMode` and `isReturnDefaultValues` (not in the verified baseline).
- **Source:** [ORCH] O16.
- **Note:** Extended by D-176.

#### D-147 — Screenshot tests
- **Decision.**
- **Setup:** Roborazzi 1.77.0. Goldens live in `app/src/test/screenshots/<name>.png`, written by a helper `captureGolden(name)` that calls `captureRoboImage("src/test/screenshots/$name.png")`.
- **Capture conditions:** SDK 36, default qualifiers `RobolectricDeviceQualifiers.Pixel7`, dynamic color off, the bundled font only, static fixtures, `mainClock.autoAdvance = false` with an explicit settle.
- **Comparison:** exact match (threshold 0). If CI shows sub-pixel noise, switch to `changeThreshold = 0.001F` and record it.
- **Matrix** (45 PNGs, DESIGN_SYSTEM.md §10.4, D-204; ≤ 10 MiB):
  - Game: new 4×4 light/dark; mid 4×4 light/dark; 3×3 light; dense 8×8 light/dark; super tiles light/dark; Neon light/dark; high contrast; win card light/dark; game-over card light/dark.
  - Screens: settings light/dark; statistics; How to play; About; licenses.
  - Locales: `fa` game (RTL); `de` settings (long strings); `en-XA` settings; `ar-XB` game.
  - Font scale 2.0: game and settings.
  - The 7 layout qualifiers of D-092.
  - The direction pad visible.
  - A motion **filmstrip** of one scripted move with a merge and a spawn, named t000/t040/t080/t160/t400 and captured at 0, 48, 80, 160 and 400 ms from the origin (D-204).
- **Workflow:**
  - Record locally with `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true`. CI only verifies.
  - The agent **opens (Reads) every new or changed PNG** and lists them in the commit body and the PR body.
  - Never auto-commit from CI. If local and CI differ, download the reports artifact, Read the actual images, and commit them.
- **Rationale:** research/06 §1.4; research/07 G19 (record is opt-in; Read PNGs; filmstrips).
- **Rejected:** Paparazzi (alpha only for AGP 9); Compose Preview Screenshot Testing (needs AGP 9.5).
- **Source:** [ORCH] O16.
- **Note:** Extended by D-176, D-204; refined by D-210.

#### D-148 — APK and manifest gates (`scripts/check-apk.sh`)
- **Decision.** For `app-debug.apk` and `app-release.apk`, using build-tools 37.0.0, the script asserts:
  1. **Badging.** Release: `package: name='com.clayboicardi.game2048' versionCode='30000' versionName='3.0.0'`, label `2048`, `minSdkVersion:'24'` (technical correction TC-2), `targetSdkVersion:'37'`. Debug: package `com.clayboicardi.game2048.debug`, label `2048 Debug`.
  2. **Permissions.** `uses-permission` is exactly `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, nothing else.
  3. **Exported components.** From `aapt2 dump xmltree`, the set with `exported=true` is exactly {`com.clayboicardi.game2048.MainActivity`, `androidx.profileinstaller.ProfileInstallReceiver`} for release. Debug additionally allows `androidx.activity.ComponentActivity`, which `ui-test-manifest` merges (verified 2026-10-28).
  4. **Size.** The release APK is ≤ **3,145,728 bytes** (3.0 MiB).
  5. **Alignment.** `zipalign -c -P 16 -v 4` passes.
  6. **Signer.** The `certificate SHA-256 digest:` line(s) of `apksigner verify --print-certs` equal `signing/ci-sideload.sha256` (technical correction TC-2).
  7. **Dependency deny-list.** `:app:dependencies --configuration releaseRuntimeClasspath` matches none of `com\.google\.(android\.gms|firebase|android\.play|android\.ump|android\.datatransport)|com\.google\.firebase|com\.crashlytics|io\.sentry|com\.squareup\.okhttp3|io\.ktor|ui-text-google-fonts|androidx\.work|androidx\.glance`.
- **Rationale:** research/06 §3.4; research/07 G12, G13; research/03 §5.
- **Rejected:** a literal zero-permission manifest via `tools:node="remove"` (breaks API < 33, research/06 §3.4); checking the release APK only (debug APKs are distributed too).
- **Source:** [USER] U6 · [ORCH] O16.
- **Note:** Extended by D-179; technical correction TC-2.

#### D-149 — Test-count floor
- **Decision:**
  - `scripts/check-test-counts.sh` sums `tests="N"` from `core/engine/build/test-results/test/*.xml` and `app/build/test-results/testDebugUnitTest/*.xml`.
  - It fails if either sum is 0 or below `config/test-count-floor.txt` (lines `engine=<n>` and `app=<n>`).
  - The same commit that adds tests raises the floor. Lowering it needs a stated reason in the PR body.
- **Rationale:** research/06 §3.4 (it catches silently skipped JUnit 6 tests).
- **Rejected:** trusting Gradle's exit status alone (expression-bodied JUnit 6 tests are silently skipped; research/03 §1.9).
- **Source:** [ORCH] O16.
- **Note:** Extended by D-179.

#### D-150 — Performance budgets (CI-checkable)
- **Decision:**

| Budget | Value | Checked by |
|---|---|---|
| Release APK | ≤ 3.0 MiB | D-148 |
| Font subset | ≤ 32 KiB | D-152 |
| Persistence | D-046 budgets | `PersistenceBudgetTest` |
| Engine | 100,000 random accepted moves on 8×8 in ≤ 2 s on the CI JVM | `EnginePerformanceTest` |
| Tile recompositions during a slide | **0** | `TileRecompositionTest`, via a test-only `LocalRecompositionCounter` (default `null`, no-op in production) |

- Device-only targets (frame CPU P95 ≤ 6 ms, `frameOverrunMs` P99 ≤ 0) are documented and not gated.
- **Rationale:** research/07 G12; research/04 §4.6.
- **Rejected:** device-only budgets as merge gates (no device in CI, research/06 §1.6).
- **Source:** [RESEARCH].
- **Note:** Refined by D-210.

#### D-151 — CI workflow
- **Decision.** `.github/workflows/ci.yml` (the only workflow):

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
  ANDROID_BUILD_TOOLS: '37.0.0'
jobs:
  verify:
    runs-on: ubuntu-24.04
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
        with: { cache-provider: basic }
      - uses: actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9 # v6.1.0
        with:
          path: ~/.m2/repository/org/robolectric
          key: robolectric-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml', 'app/src/test/resources/robolectric.properties') }}
          restore-keys: robolectric-${{ runner.os }}-
      - name: Android SDK packages (idempotent)
        run: |
          SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKM" --licenses > /dev/null || true
          "$SDKM" --install "platforms;android-37.0" "build-tools;36.0.0" "build-tools;37.0.0" > /dev/null
      - name: Verify (identical to local)
        run: ./scripts/verify.sh
      - name: Upload APKs
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: game2048-apks-${{ github.sha }}
          path: |
            app/build/outputs/apk/debug/*.apk
            app/build/outputs/apk/release/*.apk
            app/build/outputs/mapping/release/mapping.txt
          if-no-files-found: warn
          retention-days: 30
      - name: Upload reports
        if: ${{ !cancelled() }}
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: game2048-reports-${{ github.sha }}
          path: |
            **/build/reports/
            **/build/test-results/
            app/build/outputs/roborazzi/
          if-no-files-found: ignore
          retention-days: 14
```

- `scripts/*.sh` and `gradlew` are committed executable.
- **Rationale:**
  - SHAs verified (research/06 §3.1; research/07 U9).
  - `ubuntu-latest` moves to 26.04 (research/06 §0).
  - Everything is preinstalled on the runner (research/06 §3.3).
- **Rejected:** multiple jobs; the `enhanced` cache provider; a separate wrapper-validation job (setup-gradle already validates).
- **Source:** [USER] U3 · [ORCH] O16.
- **Note:** Refined by D-177.

#### D-152 — Metadata, font and license checks
- **Decision.**
- **`scripts/check-metadata.sh`** asserts:
  - exactly the 22 locale directories of D-113;
  - `title.txt == "2048"`, short description ≤ 80 characters, full ≤ 4000, `changelogs/30000.txt` ≤ 500;
  - image dimensions and the feature graphic's PNG color type 2 (D-115);
  - `google_sans_flex_numerals.ttf` ≤ 32,768 bytes;
  - no tracked file outside `docs/` contains `storage-download.googleapis.com` (D-019);
  - `.gitmodules` is absent.
- **`scripts/check-licenses.sh`** is defined in D-122.
- **Phase-in.** Every check script is created in M0 with the assertions that already hold at M0: the mirror grep, `.gitmodules` absent, the D-148 APK gates, the license-group coverage, and the test-count floor. Later assertions are added in the **same commit** as the content they check:
  - the font-size assertion in M4;
  - the store-listing assertions in M5.

  From M6 on, every assertion in D-148 and D-152 is active. Disabling one afterwards is a verification-integrity violation (D-171).
- **Rationale:** research/01 §6 (listing limits and graphics rules); research/04 §9.1 (subset size); research/07 G6, G19 (the sandbox mirror must not leak).
- **Rejected:** manual review of store assets only.
- **Source:** [ORCH] O13, O15 · [RESEARCH].
- **Note:** Extended by D-179, D-190.

#### D-153 — Not gated in v1.0
- **Decision.** None of the following exist in v1.0:
  - instrumented or Gradle Managed Device tests;
  - macrobenchmark and a `:baselineprofile` module;
  - Gradle dependency verification metadata;
  - Dependabot;
  - the dependency-submission workflow;
  - `release.yml`;
  - Compose Preview Screenshot Testing;
  - `ui-test-junit4-accessibility`.
- **Rationale:** each needs a device, adds churn, or was rejected (research/06 §1.6–1.7, §3.6, §4.4).
- **Rejected:** an emulator job on every PR (15–25 min per run, research/06 §1.6); Dependabot Gradle PRs (each one would trigger a CI and Codex loop in a hands-off repo).
- **Source:** [RESEARCH].

---

## 17. Process

#### D-160 — Two PRs
- **Decision:**
  - **PR 1 (plan):** the docs under `docs/modernization/`, plus `.github/workflows/ci.yml`, root `AGENTS.md` and `CLAUDE.md` (D-177). It changes no app code; legacy code stays until M0.
  - **PR 2 (execution):** the complete rewrite, delivered as milestone commits M0–M6 in **one** PR.
- **Rationale:** U7 (hands-off: one execution PR after the plan merges); O17.
- **Rejected:** one PR per milestone (assumed by research/06 §6; U7 requires one PR).
- **Source:** [USER] U7 · [ORCH] O17.
- **Note:** Clarified by D-177.

#### D-161 — Branch
- **Decision:**
  - Both PRs use `claude/2048-modernization-plan-8eneix`.
  - After PR 1 merges, and before any execution commit, re-cut the branch (D-197):

    ```bash
    # 1. Via the GitHub API (MCP tool or gh): confirm the plan PR is merged; record its merge_commit_sha.
    # 2. The working tree is clean: this prints nothing.
    git status --porcelain
    # 3.
    git fetch --prune origin
    # 4. Only if origin/claude/2048-modernization-plan-8eneix still exists. If it fails, stop and report.
    git diff --quiet origin/master origin/claude/2048-modernization-plan-8eneix
    # 5.
    git checkout -B claude/2048-modernization-plan-8eneix origin/master
    git push --force-with-lease origin claude/2048-modernization-plan-8eneix
    ```

  - After PR 2 is opened, history is never rewritten: no force-push, rebase or amend of pushed commits.
- **Rationale:** a rewritten history breaks Codex comment anchors.
- **Rejected:** a new branch name (O17 fixes it); rebasing after review starts.
- **Source:** [ORCH] O17.
- **Note:** Clarified by D-197.

#### D-162 — Milestones
- **Decision.** Commits are in order. Each milestone ends with a passing `scripts/verify.sh` (D-163).

| M | Content | Exit checks beyond `verify.sh` |
|---|---|---|
| **M0** Bootstrap | Capture `docs/modernization/legacy-credits.md` and the icon paths (D-124, D-111). Remove the submodule (`git submodule deinit -f assets/2048 && git rm -f assets/2048 && rm -rf .git/modules/assets/2048 && git rm -f .gitmodules`). `git rm` the D-001 files. `git mv` strings (D-101) and metadata (D-113). Wrapper (D-010), settings, catalog and build files (D-011–D-017). Placeholder Game screen that uses `R.string.settings`. Manifest (D-090). Adaptive icon (D-111). Splash theme (D-112 theme only). Keystore (D-131). All `scripts/` (D-140, D-148, D-149, D-152 phase-in), `config/test-count-floor.txt`, `config/license-groups.txt`. `core/engine/AGENTS.md` (D-171); `ci.yml`, root `AGENTS.md` and `CLAUDE.md` come from the plan PR (D-177). Modern `.gitignore`. LICENSE lines (D-120). `LaunchSmokeTest` + 1 golden. A `:core:engine` skeleton with one function and its JUnit 6 test, as in `research/smoke-build/`, so Kover and the test-count floor have data from M0 | `git submodule status` empty. Badging shows the package and label. The first push includes `ci.yml` and a CI run appears within 5 min (D-165). The orchestrator opens the execution PR ready for review (D-193) |
| **M1** Engine | D-030–D-039, D-145 | Kover thresholds met. Golden vectors pass |
| **M2** State & persistence | D-022–D-024, D-040–D-048, D-112 gate, ViewModel. `BoardUi`, `BoardPresenter`, `HapticsMap` (D-211) | Fixtures committed. Process-death and budget tests pass. Burst-ordering test passes. **Codex mid-checkpoint review** (D-166) |
| **M3** Board, input, motion, haptics | D-025, D-050–D-054, D-060–D-066, the D-007/D-008 overlays and dialogs (two pushed parts, D-211) | Filmstrip goldens Read. Every MOTION_SPEC AC assigned to M3 passes |
| **M4** Screens, theming, layout, a11y | D-006, D-009, D-023 screens, D-044 stats UI, D-070–D-079, D-080–D-085, D-091–D-095, D-122, D-124 | Goldens 1–22 and 27–45 recorded and Read (D-204). ATF is clean |
| **M5** Localization & identity | D-100–D-106 (all 20 locales), D-110, D-113–D-116, D-121, D-123 | Lint translation checks are clean. Store assets are generated with `-PrecordStoreAssets=true` and Read. Locale goldens 23–26 recorded and Read |
| **M6** Hardening & close-out | The D-148 gates all green, the reproducibility probe (D-191), the final Codex loop (D-166–D-169, D-196) | Merge gate D-168 |

- **Rationale:** research/06 §6; research/07 G2, G21.
- **Rejected:** one big commit (unreviewable; it loses the milestone gates).
- **Source:** [ORCH] O17 · [RESEARCH].
- **Note:** Refined by D-177, D-193, D-211.

#### D-163 — Local gate per push
- **Decision:**
  - No push without a passing `./scripts/verify.sh` on the exact commit being pushed.
  - Sandbox-only extras go through `VERIFY_GRADLE_ARGS` and `-ProbolectricRepoUrl` (D-019).
  - The commit body records the result line.
- **Rationale:** U3 (verified locally and in CI); a failure caught locally does not cost a CI and Codex round.
- **Rejected:** pushing and letting CI find failures.
- **Source:** [ORCH] O17 · [USER] U3.
- **Note:** Extended by D-178; clarified by D-194.

#### D-164 — Commit conventions
- **Decision.** Commit subjects (≤ 72 characters, imperative mood):
  - `M<n>: <summary>` for milestone work;
  - `review(r<k>): <summary>` for fixes in Codex round k;
  - `ci: <summary>` for CI-only fixes;
  - `docs: <summary>` for plan or doc changes.
- The body lists:
  - the D-IDs implemented;
  - `Gates: scripts/verify.sh passed (engine=<n> tests, app=<m> tests)`;
  - `Screenshots: <added/changed PNG names> (all inspected)`, or `Screenshots: none`.
- Messages end with the attribution trailer lines that the executing environment supplies, verbatim.
- WIP commits are never pushed.
- **Rationale:** Milestone-traceable history in a merge-commit PR (D-170); screenshots must be inspected (O16).
- **Rejected:** Conventional Commit types alone (they carry no milestone).
- **Source:** [ORCH] O17.
- **Note:** Clarified by D-180, D-194.

#### D-165 — Fork-safe PR mechanics and PR body
- **Decision.**
- **PR creation:** always create PRs in `clayboicardi/2048-android-modern-refresh` with base `master` and head `claude/2048-modernization-plan-8eneix`, via the GitHub API: MCP `create_pull_request(owner="clayboicardi", repo="2048-android-modern-refresh", base="master", head=…)` or `gh pr create -R clayboicardi/2048-android-modern-refresh --base master --head …` (D-195). **Never** open a PR against `uberspot/2048-android`.
- **First push check:** after the first push that contains `ci.yml`, list the `ci.yml` workflow runs on the branch through the GitHub API for up to 5 minutes.
  - If no run appears, or the push of workflow files is rejected for lacking the `workflows` scope, apply D-172.
- **Execution PR:**
  - Opened ready for review right after the M0 push (D-193).
  - Title: "Native rewrite: Kotlin + Compose + Material 3 (3.0.0)".
  - The body contains:
    - a summary;
    - a checklist M0–M6 with commit SHAs;
    - a line stating that `master` is the default branch ("main" in the request);
    - a "Deviations from DECISIONS.md" section, which reads None or lists the D-173 entries added during execution (D-212 and later);
    - the changed-screenshot list;
    - the latest `verify.sh` summary;
    - the CI artifact names;
    - blockers, if any.
- **Rationale:** the repository is a fork, and `gh` may resolve to the parent (research/07 §0.4, G1).
- **Rejected:** creating a PR without naming the target repository (`gh` without `-R` may target the parent repository, research/07 G1).
- **Source:** [USER] U3, U4 · [RESEARCH].
- **Note:** Clarified by D-177, D-194, D-195; refined by D-193.

#### D-166 — Codex review requests
- **Decision.** Post a PR comment whose exact body is `@codex review`:
  - (a) after the M2 push (mid checkpoint);
  - (b) after the M6 push;
  - (c) after every fix push in the review loop.
- Polling:
  - poll as D-196 states;
  - expect a 👀 reaction within 10 minutes and a result within 30 minutes;
  - if nothing arrives in 30 minutes, re-post once;
  - if there is still nothing after another 30 minutes, apply D-172.
- **Every trigger on a distinct head SHA counts as one round**, including the mid checkpoint.
- **Rationale:** research/06 §5.1–5.2; research/07 G21.
- **Rejected:** a review only at the end (late findings on the irreversible data model, research/07 G21); automatic reviews on every push (the agent cannot control them, research/06 §5.1).
- **Source:** [USER] U4 · [ORCH] O17.
- **Note:** Refined by D-193; clarified by D-195, D-196.

#### D-167 — Clean signal
- **Decision.** A round is **clean** when, for the head SHA it was triggered on, `chatgpt-codex-connector[bot]` does either of these:
  - (a) adds a `+1` (👍) reaction to the PR or to the trigger comment after the trigger;
  - (b) posts a review or comment with **no new findings**.
- A **new finding** is an inline or summary item, badged P0–P3, that does not repeat an item already answered.
- The head SHA must not change between the trigger and the signal.
- **Rationale:** U4; the 👍 behaviour is community-observed (research/06 §5.1; research/07 U1).
- **Rejected:** waiting for an "approved" review state (Codex does not approve PRs, research/06 §5.1).
- **Source:** [USER] U4.
- **Note:** Clarified by D-196.

#### D-168 — Merge gate and valve
- **Decision.** Merge PRs into `master` only when both hold for the **same head SHA**:
  1. CI `verify` is green.
  2. The latest Codex round is clean (D-167).
- **Valve:** after **5** rounds, if the latest round raised only new P2 or P3 items, merge anyway once:
  - every P0 and P1 from all rounds is resolved, and
  - every remaining thread has a reply.
- **Rationale:** U4 verbatim.
- **Rejected:** merging on CI alone, or on Codex alone (U4 requires both).
- **Source:** [USER] U4.
- **Note:** Clarified by D-196.

#### D-169 — Handling findings
- **Decision:**
  - For each finding, push a fix in a `review(r<k>)` commit (implemented and committed by the milestone integrator, pushed by the orchestrator, D-194), reply in its thread with `Fixed in <sha>: <one line>`, and resolve the thread.
  - Alternatively, reply with evidence and a D-ID explaining why no change is correct. P0 and P1 may only be declined with concrete evidence that the finding is wrong.
  - Bot reviews are never dismissed, hidden or minimized.
  - Comments from the repository owner take precedence; because the orchestrator posts as the owner, D-196's ledger tells them apart.
- **Rationale:** U4 (every remaining thread has a reply); unambiguous next rounds (research/06 §5.2).
- **Rejected:** leaving any P2/P3 finding without a reply.
- **Source:** [USER] U4 · [RESEARCH] research/06 §5.2.
- **Note:** Clarified by D-194, D-196.

#### D-170 — Merge method
- **Decision:**
  - Plan PR: squash merge.
  - Execution PR: a merge commit, which keeps the M0–M6 milestone commits on `master`.
  - Both go through the GitHub API with the expected head SHA (MCP `merge_pull_request(merge_method, expectedHeadSha)` or `gh pr merge -R clayboicardi/2048-android-modern-refresh --squash|--merge --match-head-commit <sha>`; D-195).
- **Rationale:** The plan PR's intermediate revisions have no lasting value; the execution PR's milestone commits are the audit trail (D-162).
- **Rejected:** rebase-merge for the execution PR (it rewrites SHAs that review threads reference).
- **Source:** [ORCH] O17.
- **Note:** Clarified by D-195.

#### D-171 — AGENTS.md
- **Decision.**
- **Root `AGENTS.md`** contains:
  - a short project paragraph;
  - "Build & verify": JDK 21; `./scripts/verify.sh`; record goldens and Read every changed PNG; never commit the mirror init script.
  - `## Code Review Rules`, with these `###` groups:
    1. **Offline, permissions and dependencies.** Flag any new merged permission other than D-002's, and any network, analytics, ads, GMS, Firebase or downloadable-font dependency.
    2. **Saved data.** Flag changes that cannot read the previous format, lose data on decode errors, or lower a best score. The safe path is defaults + fixtures + `DataMigration`.
    3. **Verification integrity.** Flag weakened gates: ignored tests, baselines, lowered floors or thresholds, unexplained golden changes, R8 off, blanket `-keep`, `dependenciesInfo` on.
    4. **Intentional, do not flag.**
       - The committed `signing/ci-sideload.p12` and its public password (D-131).
       - androidx.core's signature self-permission.
       - Latin digits in game numerals.
       - Classic tile 32 at 3.66:1 (D-083).
- **`core/engine/AGENTS.md`** contains `## Code Review Rules` → `### Game rules`:
  - D-032–D-038 semantics;
  - no clock, platform RNG or Android imports in the engine.
- The heading follows D-192.
- **Rationale:** research/06 §5.1, §5.3; research/07 G20 (keep the rule set short; engine rules nearest the code).
- **Rejected:** research/06 §5.3's six-group rule set in one file (Codex advises a few concise rules, research/07 G20).
- **Source:** [USER] U4 · [RESEARCH].
- **Note:** Extended by D-177; clarified by D-192.

#### D-172 — Stop conditions and blockers
- **Decision.** Stop autonomous progress, leave the PR open, write the blocker into the PR body, and report it to the user when any of these occurs:
  1. No CI run appears after a push with an open PR, after the D-195 dispatch attempt, or the workflow file cannot be pushed.
  2. Codex gives no response after the re-trigger (D-166).
  3. Round 10 (D-196) still raises P0 or P1 findings: no further pushes; post a summary to the user (rounds table, open P0/P1 with evidence, head SHA, CI state) and wait. Never merge with an open P0/P1.
  4. A decision is technically impossible and no D-173 alternative stays within U1–U7.
  5. A push is rejected by push protection, for example over the keystore.
- Otherwise, continue without asking.
- **Rationale:** U7 requires hands-off progress, but U4's merge gate cannot be met without CI and Codex, so stopping cleanly beats looping.
- **Rejected:** merging without a gate signal; asking the user at every obstacle.
- **Source:** [USER] U7 · [RESEARCH] research/07 G1. Condition 3 is the orchestrator's stop rule (D-196); U4's valve is unchanged.
- **Note:** Clarified by D-196.

#### D-173 — Change control for this file
- **Decision.** To change a decision during execution:
  - add a new decision `D-NNN — supersedes D-MMM`, with evidence (a command output or a research pointer), in the same commit as the code change;
  - mark the superseded entry "Superseded by D-NNN";
  - list it under "Deviations" in the PR body.
- Other docs under `docs/modernization/` are updated in the same PR to stay consistent.
- [USER] decisions are never superseded.
- **Rationale:** O17 and the house rule "never silently diverge"; every doc stays consistent with one source.
- **Rejected:** editing decisions in place without a trace.
- **Source:** [ORCH] O17.

---

## 18. Entries added during planning

#### D-174 — ATF opt-in isolated to one test file (amends D-143)
- **Decision:**
  - `@file:OptIn(ExperimentalRoborazziApi::class)` is allowed only in `app/src/test/kotlin/com/clayboicardi/game2048/testing/Accessibility.kt`, which wraps the Roborazzi accessibility-check API for `atfRule`.
  - `check_optin_allowlist` (`scripts/check-metadata.sh`, QUALITY.md §1.16) enforces this. No production code is affected.
- **Rationale:** every Roborazzi 1.77.0 ATF API is `@ExperimentalRoborazziApi`. Without the opt-in, `allWarningsAsErrors` fails with 7 "needs opt-in" errors (QUALITY.md §0 V5). D-085 requires ATF. With the opt-in, ATF runs under Robolectric 4.17 at SDK 36 (V5).
- **Rejected:** dropping ATF and keeping semantics tests only; a project-wide `optIn.add(…)`.
- **Source:** [ORCH] O16 · [RESEARCH] QUALITY.md §0 V5.

#### D-175 — Spotless excludes `docs/**` (extends D-141)
- **Decision:** both Spotless targets use `targetExclude("**/build/**", "docs/**")`: `kotlin` on `**/*.kt` and `kotlinGradle` on `**/*.gradle.kts`. ARCHITECTURE.md §2.5 is the build file.
- **Rationale:** with `**/build/**` alone, `spotlessCheck` fails on the frozen evidence under `docs/modernization/research/smoke-build/` (3 Kotlin sources and 2 build scripts; verified), and `spotlessApply` would rewrite it.
- **Rejected:** reformatting or moving the research files (they are frozen evidence).
- **Source:** [RESEARCH] ARCHITECTURE.md §0, §2.5.

#### D-176 — Test-task inputs and no cached test results (extends D-146, D-147)
- **Decision:**
  - The `:app` unit-test tasks, inside `testOptions.unitTests.all {}`, declare two inputs with relative path sensitivity:
    - `src/test/screenshots/**/*.png` (property `goldenScreenshots`);
    - `config/license-groups.txt` (property `licenseGroups`).
  - `:core:engine:test` declares `src/main/kotlin` (property `engineSources`) as an input, because `EngineImportsGuardTest` reads it as text.
  - Both use `outputs.cacheIf("test results are never reused from the build cache") { false }`.
  - Any later test that reads a repository file by path adds that file to these inputs in the same commit.
  - The exact snippets are in ARCHITECTURE.md §2.6 and §2.7.
- **Rationale:**
  - Without the inputs, a golden-only change leaves `:app:testDebugUnitTest` UP-TO-DATE and a wrong golden passes (QUALITY.md V7, reproduced).
  - setup-gradle caches the local build cache, so CI could restore a stale test result (V8).
- **Rejected:** `--rerun-tasks` in `verify.sh` (slower, and it hides the cause); `upToDateWhen { false }` alone (the results stay cacheable).
- **Source:** [RESEARCH] QUALITY.md §2.6, V7, V8.

#### D-177 — Plan PR scope: workflow and agent files (refines D-151, D-162; clarifies D-160, D-165; extends D-171)
- **Decision:**
  - **Contents.** The plan PR contains `docs/modernization/**`, `.github/workflows/ci.yml`, root `AGENTS.md` and root `CLAUDE.md`, and no app code. D-165's first-push workflow check runs on the plan PR's first push.
  - **Workflow.** `.github/workflows/ci.yml`, as the plan PR commits it, is the D-151 workflow. QUALITY.md §5.3 lists how it differs from D-151's listing; none of the differences weakens a gate:
    - bootstrap detection;
    - `persist-credentials: false`;
    - explicit `validate-wrappers`;
    - `cache-read-only: false`;
    - head-SHA artifact names and `overwrite: true`;
    - a failure-only diff artifact;
    - the dispatch-only reproducibility probe.
  - **M0.** M0 keeps `ci.yml`, root `AGENTS.md` and `CLAUDE.md` unchanged and creates only `core/engine/AGENTS.md`.
  - **Root `AGENTS.md`.** It carries a severity paragraph and six `###` groups under `## Code Review Rules` (D-192):
    - D-171's four groups, with "Saved data" widened to "Saved data and game state" (state loss, main-thread I/O, unbounded growth);
    - "Input, rendering and motion";
    - "Accessibility, text and translations".

    "Intentional, do not flag" also covers research wording, generated golden pixels and the legacy changes of §1.
  - **Trim path.** If Codex output on the execution PR is noisy, a `docs:` commit trims the file to D-171's four groups (research/07 G20).
  - **Unchanged.** The [USER] facts behind D-151, D-160, D-165 and D-171 stay as they are: U3 (CI builds), U4 (review gate), U7 (one execution PR).
- **Rationale:**
  - The plan PR proves workflow-push scope and Actions triggering before execution starts.
  - Agents executing the plan need the root instruction files from the first execution commit.
- **Rejected:** adding `ci.yml` and `AGENTS.md` in M0 (the first CI signal would arrive only after M0's large commit).
- **Source:** [USER] U3, U4, U7 · [ORCH] orchestrator task for this plan · [RESEARCH] QUALITY.md §5.3.

#### D-178 — `verify.sh` levels and local invocation (extends D-140, D-163)
- **Decision:**
  - **Levels.**
    - `./scripts/verify.sh` (= `full`) runs D-140's list unchanged. CI runs it, and it is the only push gate.
    - `quick` (`spotlessCheck :core:engine:test`), `--record` (re-record goldens and list new or changed PNGs) and `full --record` are local conveniences. None of them ever replaces `full` before a push.
  - **One local build at a time** across all checkouts and worktrees. Every local `./scripts/verify.sh …` and every direct `./gradlew …` is wrapped as `flock "$HOME/.cache/game2048-gradle.lock" <command>`. The scripts never take the lock themselves, because CI runs them too.
  - **Detached runs.** A local full run starts detached and writes its output to `$HOME/.cache/game2048/verify-<short-sha>-<level>.log`.
    - Only its final line, `verify.sh: level=<level> record=<bool> result=PASS|FAIL …`, decides the outcome.
    - A tool timeout or a killed run is never a result.
  - `./gradlew --stop` runs at the end of each milestone.
- **Rationale:**
  - A warm full gate is projected at 8–12 minutes, longer than a typical foreground tool limit (QUALITY.md §3.2).
  - The machine has 4 CPUs and about 15 GB of RAM, so parallel Gradle builds risk memory exhaustion (research/07 G19).
- **Rejected:** a lock inside `verify.sh` (it couples CI to a local path).
- **Source:** [ORCH] O16 · [RESEARCH] QUALITY.md §3.

#### D-179 — Check-script additions, M0 set (extends D-148, D-149, D-152)
- **Decision.** Every addition is stricter than the decisions, never weaker.
  - **`check-test-counts.sh`:**
    - per module, `executed = tests − skipped`, `executed ≥ floor`, `skipped == 0` and `failures + errors == 0`;
    - floors equal the executed counts of the commit's verify run;
    - an orphan golden (a committed PNG that no test compared) fails.
  - **`check-metadata.sh` `[M0]` functions:**
    - `check_mirror_leak`;
    - `check_no_submodule`;
    - `check_executable_bits`;
    - `check_no_baselines`;
    - `check_golden_budget`: PNG only, total ≤ 10 MiB;
    - `check_license_file`: the three D-120 copyright lines, in order;
    - `check_optin_allowlist`: opt-ins only in the files of D-174 and D-181;
    - `check_no_bom`: no tracked text file starts with U+FEFF (L10N_STORE_LEGAL.md §1.2);
    - `check_icon_glyphs`: the adaptive and legacy launcher icons use identical glyph paths (L10N_STORE_LEGAL.md §3.4).
  - **`check-licenses.sh`:** stale or malformed lines in `config/license-groups.txt` fail.
  - **Seeded-defect proof.** Each new function is proven once during M0 against a seeded defect, in a throwaway clone beside the checkout (QUALITY.md V14–V15). The failing line goes into the M0 commit body. A seeded defect is never committed.
- **Rationale:** QUALITY.md §1.13–§1.16, V12–V15; L10N_STORE_LEGAL.md §1.2 (Lint misses a leading BOM) and §3.4.
- **Rejected:** trusting the Gradle exit status alone (JUnit 6 tests can be skipped silently, D-149).
- **Source:** [ORCH] O16 · [RESEARCH] QUALITY.md §1.13–§1.16.

#### D-180 — No AI model identifiers in tracked files (clarifies D-164)
- **Decision:**
  - No tracked file contains an AI model name or model identifier. This covers code, docs, resources, test names, golden metadata, scripts and the store listing.
  - Commit trailers and PR footers are exactly what the execution environment supplies, kept verbatim (D-164).
    - Repository files never tell agents to change, strip or replace them.
    - Repository files never claim that the environment defers to them on attribution.
  - **Check.** Before each push, the orchestrator runs `git grep -i -E -f <term file kept outside the checkout>` over the tracked files, and it must print nothing.
    - The term list is never committed, because it would itself contain model names.
  - The file name `CLAUDE.md`, which the agent tool reads, and the branch name fixed by D-161 are tool- and environment-assigned identifiers, not model names.
- **Rationale:** orchestrator directive for this rewrite. D-164 already keeps environment trailers verbatim.
- **Rejected:** rewriting or hard-coding attribution trailers in repository instructions; a committed check script (its search terms would break the rule).
- **Source:** [ORCH] orchestrator directive (plan review, 2026-10-28).

#### D-181 — `ModalBottomSheet` opt-in isolated to `GameModalSheet.kt` (amends D-143)
- **Decision:**
  - `@OptIn(ExperimentalMaterial3Api::class)` appears only in `app/src/main/kotlin/com/clayboicardi/game2048/ui/components/GameModalSheet.kt`, for `ModalBottomSheet`, `rememberBottomSheetState` and `SheetValue`.
  - `BoardSizeSheet` (D-007) and `WhatsNewSheet` (D-009) call only `GameModalSheet`.
  - The deprecated `rememberModalBottomSheetState` is never used.
  - `check_optin_allowlist` enforces this.
- **Rationale:** in material3 1.5.0-beta02 (and 1.4.0) these APIs carry `@ExperimentalMaterial3Api` (verified 2026-10-28 from class files; DESIGN_SYSTEM.md §7.4). D-007 and D-009 require a modal bottom sheet.
- **Rejected:** a bottom-anchored `BasicAlertDialog` panel (it loses drag-to-dismiss); opt-ins at each call site.
- **Source:** [ORCH] O1 · [RESEARCH] DESIGN_SYSTEM.md §7.4.

#### D-182 — Epoch presentation of `Static` boards (clarifies D-025, D-064)
- **Decision:**
  - **Static only.** An epoch `BoardUi` (event `Restored`, `Undo`, `NewGame` or `SizeSwitched`) contains only `Static` tiles. The presenter never emits `Ghost`, `Moved`, `Merged` or `Spawned` tiles for an epoch.
  - **Tile ids.** `BoardPresenter.reset(state, event)` keeps a cell's tile id when `event == Undo` and the cell's exponent is unchanged. Every other epoch assigns new ids.
  - **Outgoing tiles** are ids present before the epoch and absent after it.
    - `OutgoingTilesLayer` draws them from the previous `BoardUi` and animates them as MOTION_SPEC.md §3.8 (new game) and §3.9 (undo) describe.
    - `MotionProbe` reports them as outgoing, not as a `TileUi.kind`.
    - Ids present in both boards neither animate nor recompose.
  - **Size switch.** `SizeSwitched` uses MOTION_SPEC.md §3.10's `AnimatedContent(targetState = boardSize)` instead of the outgoing layer, because cell geometry changes with N.
  - **Restore.** `Restored` never animates (D-047).
- **Rationale:** this reconciles three requirements:
  - D-025: `Static` tiles never animate;
  - D-064: the undo crossfade and the new-game cascade;
  - FEATURES.md F05-AC9 and F06-AC11 ("only Static") and MOTION_SPEC.md AC-UNDO-1 (unchanged tiles do not recompose).
- **Rejected:** `Ghost` tiles in epoch boards; crossfading every tile on undo.
- **Source:** [RESEARCH] ARCHITECTURE.md §5.5; MOTION_SPEC.md §3.8–§3.10.

#### D-183 — Motion tokens, timing mechanism and timeline extensions (extends D-060, D-061, D-062, D-063, D-064)
- **Decision:**
  - **Time origin.** "From commit" in D-061, D-064 and D-065 means "from the origin". The origin is F1, the first animation frame after the frame that composes the new `BoardUi` (MOTION_SPEC.md §0.1).
    - An offset is due on the first frame at or after origin + offset × animator scale.
    - Tests state times on the 16 ms test-frame grid.
  - **Tokens added:**
    - `standardSpatial` 0.9/700, for the deflate and the screen-transition shift;
    - the visibility thresholds of MOTION_SPEC.md §1.2;
    - the tween constants of MOTION_SPEC.md §1.5.
  - **Timing mechanism.** No raw `delay()` is used for anything visible.
    - State flips at an offset use `MoveTimeline.awaitOffset(moveId, ms)`, which scales with the animator scale, or `awaitRealTime(moveId, ms)`, which does not.
    - A delay before an animation is part of its spec: `tween(…, delayMillis)`, `snap(delayMillis)`, or the app-local `delayedSpec(spec, delayMs)` in `game/motion/DelayedSpec.kt`.
    - `delayedSpec` holds the initial value and velocity during the delay, then plays the wrapped spec at play time − delay. Its duration is the delay plus the wrapped duration.
    - animation-core's `delayed()` is Kotlin-internal and is not used.
  - **Spawn delay**, by the move's longest slide:

    | Longest slide (cells) | ≤ 3 | 4 | 5 | 6 | 7 |
    |---|---|---|---|---|---|
    | Delay (ms) | 90 (`SPAWN_DELAY_MS`) | 100 | 105 | 110 | 115 |

  - **Pop impulse.** Each merged tile takes its impulse from its own value: ≤ 64 → 6 s⁻¹, 128–512 → 8, 1024 → 10, ≥ 2048 → 12.
    - The impulse is capped so a scaled tile never reaches a resting neighbour: `v0 = min(tier, (2 · gapPx / tilePx) / POP_PEAK_PER_VELOCITY)`.
    - The move-level `maxMergedExp` still drives the ≥ 512 haptic and the "+N" pill.
  - **Flash limiter:** at most 3 sheen or glow pulses per rolling 1000 ms of real time.
  - **Game over.**
    - The deflate stagger is `step = min(30, 180 / mMax)` ms per Manhattan step.
    - In the `OVER` end state, tiles sit at scale 0.94 and saturation 0.2. This includes static renders and goldens.
  - **Reduced motion** keeps the undo crossfade (`fastEffects` alpha) and drops the undo icon spin.
  - **Gap fills.** These follow MOTION_SPEC.md §3.10, §3.12, §3.17 and §3.19:
    - the board-size switch;
    - card exits, including Keep going → `OVER`;
    - the screen-transition and predictive-back specs;
    - the Reduced variants of undo, size switch, new game and card exits.
  - **Frame-rate vote.** `BoardView` applies `Modifier.preferredFrameRate(FrameRateCategory.High)` while any board animation runs, and `Default` when idle.
  - **`MotionPlan`** (`GameMotion.plan(level)`) carries exactly these values.
- **Rationale:**
  - MOTION_SPEC.md's numeric ACs depend on frame-exact scheduling.
  - With a fixed 90 ms delay, a spawn overlaps a tile still finishing a 4–7-cell slide by up to 0.49 tile.
  - A fixed 30 ms stagger on 8×8 would still be deflating tiles under the scrim.
  - A compile probe showed `delayed()` is internal in animation-core 1.12.1, although its bytecode looks public.
- **Rejected:** `kotlinx.coroutines.delay` for visible state; flip-started springs as the default; a per-move impulse tier.
- **Source:** [ORCH] O4 · [RESEARCH] MOTION_SPEC.md §0–§3, §7; research/04 §10.

#### D-184 — Level-dependent `MotionScheme`; platform dialog and snackbar motion (amends D-026; clarifies D-063)
- **Decision:**
  - **`MotionScheme` by level.** `GameTheme` passes `gameMotionScheme(level)` to `MaterialExpressiveTheme`:
    - Full: `MotionScheme.expressive()`;
    - Reduced: spatial specs are `snap()` and effects specs stay expressive (MOTION_SPEC.md §2.2);
    - Off: every spec is `snap()`.
  - **Dialogs and snackbars.** Dialog windows and `SnackbarHost` keep their platform and Material 3 enter and exit fades at every in-app motion level.
    - These are opacity fades, not spatial motion.
    - When the system animator scale is 0, the platform disables them.
- **Rationale:**
  - `MotionScheme` is a public, non-experimental interface, and sheets read `defaultSpatialSpec` and `fastEffectsSpec` (verified 2026-10-28 from class files).
  - Per-level dialog window animations would need a `DialogWindowProvider` workaround that cannot be verified on the JVM.
  - A hand-rolled Off snackbar would duplicate M3's accessibility timeout.
- **Rejected:** a fixed `MotionScheme.expressive()`; rewriting dialog window animations; a custom Off snackbar renderer.
- **Source:** [ORCH] O4 · [RESEARCH] MOTION_SPEC.md §2.2, §3.18.

#### D-185 — Haptic ownership, timing and interruption (extends D-065, D-066)
- **Decision:**
  - **Ownership.**
    - The pure `game/feedback/HapticsMap` (M2) resolves D-065's priority in `GameViewModel` and ships the result as `BoardEvent.Moved.haptic` (`MoveHaptic`).
    - `game/feedback/HapticsController` (M3) only schedules `event.haptic` and the separate events, applies interruption and the invalid-move rate limit, and calls `performHapticFeedback`.
    - Nothing else fires haptics.
  - **Timing under Full motion.** Offsets are measured from the origin (D-183) and scale with the animator scale (`awaitOffset`):

    | Event | Offset (ms) | Test frame (ms) |
    |---|---|---|
    | Win, first pulse | 120 | 128 |
    | Win, second pulse | 270 | 272 |
    | Priorities 2–4 | 80 | 80 |
    | Game over | 300 | 304 |

  - **Timing under Reduced and Off.** Offsets are real time (`awaitRealTime`), so the two win pulses stay 150 ms apart even at animator scale 0.
  - **Interruption.** When a newer change publishes its origin before a scheduled haptic fires:
    - pending priority 1–2 haptics fire on the newer change's F1;
    - priority 3–4 haptics are dropped;
    - a pending game-over `Reject` is dropped.
  - **Game over** fires `Reject` once per entry into `OVER`. This includes Keep going on a board with no moves left (`WON` → `OVER`). The Keep going tap itself is silent.
  - **Invalid moves below API 30** fire no haptic, because the `Reject` fallback there is `LONG_PRESS`. The game-over `Reject` is kept on every API level.
- **Rationale:**
  - One owner of priority avoids duplicating it between the ViewModel and the UI wrapper (ARCHITECTURE.md §4.8, §5.9).
  - MOTION_SPEC.md §6.
- **Rejected:** resolving priority in the UI wrapper; scaling Reduced/Off haptics; `LONG_PRESS` on every invalid swipe on old devices.
- **Source:** [ORCH] O5 · [USER] U6 (no VIBRATE; unchanged) · [RESEARCH] MOTION_SPEC.md §6.

#### D-186 — Undo snackbars (extends D-039)
- **Decision:**
  - **"Undo used."** `undo_hint_no_undo_best` ("Undo used. This game no longer counts for Best without undo.") shows when a game's `undoCount` goes 0 → 1.
    - It shows only while `RecordsMath.aggregate(records).totalUndos == 0`, that is, until the first finalized game that used undo.
    - There is no new persisted key.
  - **"Nothing to undo."** `nothing_to_undo` ("Nothing to undo") shows for U, Ctrl+Z or Backspace at `moveCount == 0`, at most once per 2 s.
  - **Placement on Game.** A snackbar never covers the controls bar.
    - STACKED: it sits in the gap between the board and the controls when that gap is at least its measured height + 16 dp. Otherwise it overlays the board, bottom-centred, with width board − 16 dp, 8 dp above the board's bottom edge.
    - THREE_COLUMN: at the bottom of the actions rail.
    - SIDE_PANEL: at the bottom of the panel, aligned with the board's bottom edge.
    - Any accepted move or undo dismisses it.
  - At most one snackbar is visible at a time. Each uses `SnackbarDuration.Short` and has no action.
- **Rationale:**
  - The explanation matters once; repeating it every game breaks DESIGN_SYSTEM.md's "Calm" principle.
  - On 360×640 the gap between the board and the controls is 24 dp, so the earlier placement rules could not both hold.
- **Rejected:** showing it once per game; a persisted "hint seen" flag; covering the controls bar.
- **Source:** [ORCH] orchestrator brief (undo-hint snackbars) · [RESEARCH] DESIGN_SYSTEM.md §5.4, §7.6.

#### D-187 — Effect layers and the "+N" pill (amends D-025; clarifies D-064)
- **Decision:**
  - **Two canvases** share one particle engine (`game/motion/ConfettiSystem`). Both live in `game/board/EffectsCanvas.kt`:
    - `BoardEffectsCanvas` draws the win glow ring, below the scrim;
    - `ContentEffectsCanvas` draws confetti and the new-best sparkles, above the scrim and below the card.
  - **Sheen.** The merge sheen is drawn inside the tile's own `drawBehind`, so it follows the tile if the next move slides it.
  - **"+N" pill.** For `maxMergedExp ≥ 9`, the "+N" float is a pill using the merged tile's fill and ink (≥ 6.07:1 in every palette).
- **Rationale:**
  - A single canvas cannot draw the ring below the scrim and confetti above it.
  - Tinted "+N" text fails contrast (about 1.5:1 in Classic light).
- **Rejected:** one canvas; tinted "+N" text.
- **Source:** [RESEARCH] DESIGN_SYSTEM.md §4.7, §7.2; MOTION_SPEC.md §3.3.

#### D-188 — Board layout amendments (amends D-075, D-091; clarifies D-009)
- **Decision:**
  - **Margins.** D-075's margins (16 / 24 / 32 dp) apply per axis: horizontal margins by width class, vertical margins by height class.
  - **STACKED chrome** is measured, not fixed: header + controls + 2 × 24 dp, which is 240 dp at fontScale 1.0.
  - **Fit guard**, applied in order:
    1. If `availH − chromeH < 280 dp`, use the compact 64 dp header (size chip, Score, Best; no title).
    2. Then allow the board down to `BoardFloor` = 220 dp.
    3. If 220 dp still does not fit, use THREE_COLUMN.
  - **First-launch hint placement:**
    - STACKED: above the board;
    - THREE_COLUMN: in the actions rail, replacing Undo and New game while shown;
    - SIDE_PANEL: in the panel.
  - **SIDE_PANEL alignment.** The panel's height equals the board's, and its top aligns with the board's top. The title row is at the top of the panel; the snackbar host is at the board's bottom edge.
  - Formulas are in DESIGN_SYSTEM.md §5.2–§5.4.
- **Rationale:**
  - Width-class margins alone would shrink the 915×412 board from 356 to 324 dp.
  - A fixed 240 dp chrome overflows at fontScale 2.0, with the TalkBack pad, and in windows 480–640 dp tall.
- **Rejected:** the literal fixed chrome; a scrolling Game screen.
- **Source:** [RESEARCH] DESIGN_SYSTEM.md §5.2–§5.4.

#### D-189 — Theme token additions (extends D-070, D-071)
- **Decision:**
  - **Neon light surface.** Neon light overrides `surface` to `#F3F1FA`, equal to its background, as Classic does (D-070).
  - **Title color.** `TileSkin` carries a palette-owned `titleColor`; for Classic light it is `#776E65`, at 4.70:1.
    - It sits beside the D-071 `TilePalette` interface, which stays verbatim.
    - Dynamic chrome never changes it.
- **Rationale:** top app bars and the scrim must equal the background, and the title color is part of the identity (DESIGN_SYSTEM.md §1 P2, §2.2, §2.8).
- **Rejected:** widening the D-071 `TilePalette` interface.
- **Source:** [RESEARCH] DESIGN_SYSTEM.md §2.2, §2.8.

#### D-190 — Store, legal and listing checks; opaque store PNGs (extends D-113, D-114, D-115, D-152)
- **Decision:**
  - **New checks in M5.** `scripts/check-metadata.sh` gains these functions. QUALITY.md §1.16 is the single canonical listing.
    - `check_store_listing`:
      - exactly D-113's 22 directories;
      - `title.txt` equals `2048` and is ≤ 30 characters (Google Play's limit);
      - short description ≤ 80 characters, full description ≤ 4000, `changelogs/30000.txt` ≤ 500;
      - no BOM or CR, and exactly one trailing newline;
      - non-en-US text is not identical to en-US;
      - every full description contains `com.clayboicardi.game2048` and the repository URL;
      - native-script letters for ru, uk, sr, el, fa and ko;
      - no non-Latin digits;
      - none of D-114's deny-words.
    - `check_store_images`:
      - exactly the image set of D-115;
      - 8-bit PNGs;
      - color type 2 for every screenshot and the feature graphic; {2, 6} for `icon.png`;
      - exact pixel sizes.
    - `check_legal_and_privacy`: the NOTICE items; the D-123 privacy text in `PRIVACY.md`; the README links; the self-permission name.
    - `check_legal_texts`:
      - `LICENSE` sha256 is `ad96d837d59c50cb3c8e23a17d7ee19a396c5a1a6c4bc952a269896fcbdbfd19`;
      - `app/src/main/res/raw/license_mit.txt` is byte-identical to `LICENSE`;
      - `app/src/main/res/raw/license_apache_2_0.txt` sha256 is `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
    - `check_strings_final`: `app/src/main/res/values/strings_pending.xml` is absent.
  - **Store assets.** `StoreAssetsTest` captures each frame with `composeRule.onNodeWithTag("store:frame").captureToImage().asAndroidBitmap()`. It writes every store PNG through the test-only `store/OpaquePngWriter` (`java.util.zip`, depth 8; color type 2 for screenshots and the feature graphic). It uses no Roborazzi API and writes no JPEG.
- **Rationale:**
  - Play requires screenshots without alpha.
  - `OpaquePngWriter` removes the dependence on Skia's encoder.
  - Roborazzi's `RoborazziTaskType` is `@ExperimentalRoborazziApi` (compile probe), which `check_optin_allowlist` forbids outside `Accessibility.kt`.
- **Rejected:**
  - a JPEG fallback (it fails `check_store_images`);
  - JVM tests that read repository-root files (those files are not declared task inputs).
- **Source:** [ORCH] O13 · [RESEARCH] L10N_STORE_LEGAL.md §2.5, §2.6; FEATURES.md F26-AC5, F27-AC5, F30-AC8.

#### D-191 — Reproducibility probe (extends D-140)
- **Decision:**
  - `scripts/repro-probe.sh` (QUALITY.md §1.17) rebuilds the committed HEAD's release APK in a fresh clone at another path with the build cache off, then rebuilds it again after `clean`. It compares both with the local APK.
  - It uses a clone, not a worktree.
  - It never fails and is never a gate. CI runs it only on `workflow_dispatch`.
  - It runs once locally in M6, and its result line goes into the PR body.
- **Rationale:** it checks D-134's hygiene without making third-party non-determinism a merge blocker (QUALITY.md V11).
- **Rejected:** a gating reproducibility check.
- **Source:** [ORCH] O16 · [RESEARCH] QUALITY.md §1.17.

#### D-192 — Codex review rules heading (clarifies D-171)
- **Decision:**
  - **Heading.** The review-rules section of root `AGENTS.md` and of `core/engine/AGENTS.md` is headed `## Code Review Rules`, with `###` groups beneath it. Every document refers to the section by that heading. Codex applies the root file plus the nearest `AGENTS.md` covering each changed file, so engine-only rules live in `core/engine/AGENTS.md`.
  - Codex on GitHub reports only P0 and P1 findings.
  - Mechanical checks (formatting, lint, test counts, manifest gates) stay in CI and are not restated as review rules.
  - D-171's [USER] U4 facts are unchanged: the files, the rule groups and the merge gate.
  - **Verification at first use** (the plan PR's Codex round): no item listed under "Intentional, do not flag" is raised.
    - Fallback: narrow or remove the rule group that produces the noise, in a `docs:` commit.
- **Rationale:** a live fetch of the official Codex GitHub-integration page (https://developers.openai.com/codex/integrations/github, which redirects to learn.chatgpt.com/docs/third-party/github) on 2026-10-28 reads "Codex searches your repository for `AGENTS.md` files and follows the applicable code review rules. Add a `## Code Review Rules` section to the file closest to the code the rules govern", "Leave mechanical checks in CI", and "In GitHub, Codex flags only P0 and P1 issues". An earlier search-index snippet suggesting `## Review guidelines` was stale.
- **Rejected:** `## Review guidelines` (not the heading the current docs name); duplicating the rules under two headings (noise, drift).
- **Source:** [RESEARCH] live official docs, 2026-10-28 · [USER] U4 (unchanged).

#### D-193 — Execution PR lifecycle: ready for review from M0 (refines D-162, D-165, D-166)
- **Decision:**
  - **Opening.** The execution PR is opened ready for review (`draft = false`) right after the M0 push. There is no draft state and no "mark ready" step.
  - **Push gate.** Each later milestone commit, or milestone part (D-211), is pushed only when all of these hold:
    - verify full passed on that exact commit (D-163, D-178);
    - the CI `verify` check of the previously pushed head has concluded green, or this push fixes its failure. A cancelled run is not green.
    - no triggered Codex round is pending on the pushed head.
  - **Codex triggers.** Codex is triggered after the M2 push, after the M6 push, and after every fix push in a review loop (D-166 a–c).
  - **Automatic reviews.** Because the PR is open for review, automatic (untriggered) Codex reviews may arrive on any head.
    - Their findings are handled like triggered ones (D-169, D-196).
    - The PR body records whether one appeared on the M0 head within 30 minutes.
- **Rationale:** CI runs only on `pull_request` and `master` pushes, so the PR must exist from M0. Waiting for the previous head's CI keeps each milestone's green run provable despite `cancel-in-progress`.
- **Rejected:** a draft PR marked ready at M2 or M6.
- **Source:** [ORCH] orchestrator directive (plan review, 2026-10-28) · [USER] U3, U4, U7 (unchanged).

#### D-194 — Roles: orchestrator and implementer agents (clarifies D-163, D-164, D-165, D-169)
- **Decision:**
  - **Orchestrator.** The session that runs the execution workflow is the only role that:
    - pushes;
    - opens or updates the PR;
    - triggers and polls Codex;
    - replies to and resolves review threads;
    - merges, escalates and resumes.

    It verifies each handoff before pushing.
  - **Milestone integrator.** An implementer agent, one per milestone. It:
    - writes the `M<n>.1` contracts;
    - integrates work packages in the stated order;
    - records and Reads goldens and sets floors;
    - runs verify full and self-reviews (PLAN.md §6.6);
    - commits locally on the execution branch.

    For review findings, it implements the fixes and regression tests, runs verify full and commits `review(r<k>)` locally.
  - **WP implementers.** They edit only their owned files, in their own worktree, and run targeted tests. Before handing off, they commit all their work on `wip/<WP>`, which is never pushed.
  - Implementer agents never push and never call GitHub.
  - **Push handoff.** The integrator returns `{sha, last verify line, log path, engine and app test counts, Screenshots list, D-IDs}`; for a review fix, it adds one line per finding id. Before pushing, the orchestrator checks that:
    - `git status --porcelain` is empty;
    - `git rev-parse HEAD` equals the sha;
    - the log ends with `level=full … result=PASS`.
- **Rationale:** GitHub state has one owner, and the pushed SHA is provably the verified SHA (D-163).
- **Rejected:** a lead agent that both implements and pushes.
- **Source:** [ORCH] orchestrator directive (plan review, 2026-10-28) · [USER] U7.

#### D-195 — GitHub operations through the GitHub API (clarifies D-165, D-166, D-170)
- **Decision:**
  - **Form of each step.** Every GitHub step names its resource and is performed "via the GitHub API (MCP tool or gh)". No step depends on a gh-only command. The canonical operation table is QUALITY.md §6.8.
  - **Target.** Every operation targets `clayboicardi/2048-android-modern-refresh` (MCP `owner`/`repo`; gh `-R …`). PRs use base `master`. Nothing targets `uberspot/2048-android`.
  - **No admin calls.** Actions and Codex are already enabled on the fork. If no CI run appears:
    1. check that the PR targets `master`;
    2. dispatch `ci.yml` on the branch once;
    3. apply D-172 item 1.
  - **Merging** passes the expected head SHA (`expectedHeadSha`, or `--match-head-commit`) and D-170's method.
  - **Reaction authorship** comes from the REST reactions endpoints, which this public repository serves without authentication (60 requests per hour). Polling stays under that limit.
- **Rationale:** the orchestrating session has no gh CLI. The GitHub MCP tools cover every other step.
- **Rejected:** gh-only procedures; admin API calls.
- **Source:** [ORCH] orchestrator directive (plan review, 2026-10-28) · [USER] U3, U4.

#### D-196 — Codex rounds, clean signal, resolution and escalation (clarifies D-166, D-167, D-168, D-169, D-172)
- **Decision:**
  - **Round.** Round k is the k-th `@codex review` trigger on a distinct head SHA of the PR. The mid checkpoint and the final loop are counted together. A re-post on the same SHA stays in the same round. Automatic reviews do not advance the count.
  - **Polling.**
    - Cadence: first 👀 check 2 minutes after the trigger, then every 3–5 minutes, using scheduled wake-ups and minimal-output API calls. CI is checked every 5 minutes until `verify` concludes.
    - Deadlines are unchanged: 👀 within 10 minutes, a result within 30 minutes, then one re-post, then D-172 item 2 after another 30 minutes.
  - **Clean** (D-167). The head SHA must be unchanged, and the signal must come after the trigger time. Any one of these counts:
    - (a) a `+1` by `chatgpt-codex-connector[bot]` on the trigger comment or the PR, identified through the REST reactions endpoint;
    - (b) a bot review whose `commit_id` is the head, with no `P0`–`P3` token, that is not an error, usage-limit or status message;
    - (c) a bot PR comment that explicitly reports no issues.

    A message about a limit or an error, or one saying "unable" or "could not", is no result. If reaction authorship cannot be read, a `+1` counts only when the trigger comment's `+1` count rose above its recorded baseline and no human reaction is known; otherwise (b) or (c) decides.
  - **Resolved P0/P1.** A P0 or P1 finding is resolved in one of two ways:
    - it is fixed: the fix is pushed, the thread has a `Fixed in <sha>: …` reply, and the thread is resolved; or
    - it is declined with concrete evidence in a reply, and the bot's next result (on a later head, or after a re-trigger on the same head) does not raise it again. The orchestrator then resolves the thread.

    A declined P0/P1 that the bot raises again stays open. Any open P0/P1 blocks the merge, including under the valve. Declined P2/P3 threads may stay unresolved, with a reply.
  - **Valve** (D-168, U4, unchanged): after 5 rounds, if the latest round raised only new P2 or P3 items, merge once every P0 and P1 from all rounds is resolved and every remaining thread has a reply. Because Codex on GitHub reports only P0/P1, the valve is expected not to apply.
  - **Stop at round 10.** If round 10 still raises a P0 or P1:
    - no further pushes;
    - post a summary to the user: the rounds table, the open P0/P1 with evidence, the head SHA and the CI state;
    - wait for the user.

    Never merge with an open P0/P1.
  - **Owner comments.** The orchestrator posts as the repository owner, so it keeps a ledger of the ids of every comment, reply and review it posts. The ledger lives in a hidden `<!-- orchestrator-ledger … -->` block in the PR body and in a file beside the checkout.
    - Any owner-authored comment, review or reaction that is not in the ledger is a human instruction.
    - The orchestrator pauses at the next safe point and follows it (D-169).
- **Rationale:**
  - The GitHub MCP tools expose reaction counts but not who reacted.
  - The bot's error comments carry no P-token.
  - Counting automatic reviews as rounds could exhaust the round budget before the final loop.
- **Rejected:** counting automatic reviews as rounds; merging with a declined P0/P1 that the bot repeats.
- **Source:** [USER] U4 (merge gate and valve unchanged) · [ORCH] orchestrator directive (plan review, 2026-10-28).

#### D-197 — Branch re-cut verification (clarifies D-161)
- **Decision.** After the plan PR merges and before any execution commit:
  1. Confirm through the GitHub API that the plan PR is merged, and record its `merge_commit_sha`.
  2. Check that `git status --porcelain` prints nothing.
  3. Run `git fetch --prune origin`.
  4. If `origin/claude/2048-modernization-plan-8eneix` still exists, `git diff --quiet origin/master origin/claude/2048-modernization-plan-8eneix` must succeed. If it fails, stop and report.
  5. Run `git checkout -B claude/2048-modernization-plan-8eneix origin/master`, then `git push --force-with-lease origin claude/2048-modernization-plan-8eneix`.

  This force-push is allowed only because the old branch's content is fully merged and no PR is open on it. Once the execution PR exists, pushed history is never rewritten (D-161).
- **Rationale:** a squash merge leaves the old branch commits outside `master`'s ancestry, so an unguarded force-push could silently discard late commits.
- **Rejected:** re-cutting without the content check.
- **Source:** [ORCH] O17.

#### D-198 — Records and statistics readings (clarifies D-044)
- **Decision:**
  - A finalized non-win resets `currentWinStreak` to 0.
  - A win is judged on the final board (max exponent ≥ target at finalization), so an undone winning tile does not count.
  - A `KEEP_PLAYING` game abandoned through New game counts as both won and abandoned.
  - Reset statistics leaves in-progress games untouched, including their `bestAtStart`.
  - **Display-time fold.**
    - `StatsViewModel` combines `records` with `GameRepository.peek(n)` for n = 3..8.
    - For each size whose saved game is `OVER`, it displays `RecordsMath.finalize(record, game)` without persisting it.
    - Persisted finalization still happens only at New game, so nothing is counted twice. Undoing out of `OVER` removes the game from the display.
- **Rationale:** FEATURES.md §3.1 and F08-AC3. Without the fold, a player who loses their first game sees an empty Statistics screen.
- **Rejected:** finalizing at game over (it double-counts after an undo).
- **Source:** [ORCH] O7, O8 · [RESEARCH] FEATURES.md F08.

#### D-199 — Controls and keys while a card is shown (clarifies D-007, D-054; amends D-053)
- **Decision:**
  - **Cards.** While the win or game-over card is shown:
    - board swipes and direction keys are ignored;
    - the controls-bar Undo and New game, the size chip and the More menu stay enabled and act like the card's buttons.
  - **Keys on cards** match the enabled chrome:
    - U, Ctrl+Z and Backspace undo;
    - N and R start a new game with no confirmation (`WON` and `OVER` are not "in progress", D-007);
    - Enter and Space activate the focused card button.
  - **Dialogs and sheets** stay fully modal.
- **Rationale:** touch and keyboard players get the same actions (FEATURES.md F03 behavior 9, F16; DESIGN_SYSTEM.md §6.1).
- **Rejected:** disabling the chrome under a card; ignoring U, N and R on cards.
- **Source:** [ORCH] O3, O11 · [RESEARCH] FEATURES.md F16.

#### D-200 — Overlay cards: fit to the board, button rendering, Share on the win card (extends D-008; clarifies D-064)
- **Decision:**
  - **Fit.** A card's height budget is the board size − 16 dp.
    - Each card has a Regular layout (DESIGN_SYSTEM.md §6.3 and §6.4) and a Compact layout (§6.3a and §6.4a). Regular is used when it fits the budget, otherwise Compact. If even Compact does not fit, the card scrolls inside itself.
    - At fontScale 1.0, on every golden qualifier, the card never extends past the board.
  - **Button rendering.** Card buttons are composed with `enabled = true` from their first frame.
    - Interactivity follows `OverlayController.interactive`, which flips at D-008's times.
    - Until then, clicks are ignored, the buttons carry `disabled()` semantics, and they cannot take focus.
  - **Share on the win card.** The win card has an icon-only Share button (`ic_share`, content description `share`) at its top end. It sends D-008's text for the current state.
- **Rationale:**
  - The card stacks (≈ 348 dp and ≈ 436 dp) are taller than 280–379 dp phone boards, including the win store screenshot.
  - Buttons drawn in disabled colors snap to enabled colors at the two signature moments.
  - Reaching the target is the moment players most want to share.
- **Rejected:** shrinking the board; dismissible cards (D-054); Share only at game over.
- **Source:** [ORCH] O8, O12 · [RESEARCH] DESIGN_SYSTEM.md §5.3, §6.3, §6.4.

#### D-201 — First-launch hint condition (amends D-009)
- **Decision:**
  - **Condition.** The hint shows when `onboardingDone == false` and the starting game has `moveCount == 0` and `undoCount == 0`. A missing game file counts as `moveCount == 0`.
  - **Dismissal.** It still disappears after the first accepted move or "Got it", and then `onboardingDone` is set to true.
  - **TalkBack.** While touch exploration is on, it shows `hint_first_launch_talkback` instead (D-202).
- **Rationale:** the fresh game is saved at first load (ARCHITECTURE.md §4.9), so D-009's "no game file exists" condition hides the hint for good once the app has been opened.
- **Rejected:** the literal D-009 condition.
- **Source:** [ORCH] O11 · [RESEARCH] FEATURES.md F22.

#### D-202 — TalkBack status completeness (amends D-081)
- **Decision.** While touch exploration is on, the polite status line also reads:
  - **after an accepted move,** `a11y_spawned` = "New %1$s at row %2$s, column %3$s.", placed after the merged sentence and before the score. The value is a game numeral; row and column are 1-based, as in `a11y_cell_tile`. Example: "Moved left. Merged 64 and 16. New 2 at row 4, column 1. Score 1,240.";
  - **after every undo,** `a11y_undone` = "Move undone. Score %1$s.";
  - **after New game,** `a11y_new_game` = "New game started.", then one `a11y_spawned` per start tile;
  - **after a size switch,** `a11y_size_switched` = "Board %1$s. Score %2$s.", with the board as `"$n×$n"` and the score from `formatCount`.

  The hint uses `hint_first_launch_talkback` = "Use the arrow buttons to move all tiles. Equal tiles merge. Reach %1$s.". There is still no verbosity setting.
- **Rationale:**
  - The spawn position is the most decision-relevant fact of a move.
  - Silence after Undo leaves a blind player unsure whether it worked.
  - TalkBack consumes swipes, so "Swipe to move" is wrong under TalkBack.
- **Rejected:** a Brief/Detailed verbosity setting (still deferred).
- **Source:** [RESEARCH] research/04 §6.3; FEATURES.md F17, F22.

#### D-203 — Keyboard shortcuts helper (extends D-053)
- **Decision:** `MainActivity.onProvideKeyboardShortcuts` returns one `KeyboardShortcutGroup` titled with `app_name`. It lists:
  - `move_up`, `move_down`, `move_left` and `move_right` (the arrow keys);
  - `undo` (U and Ctrl+Z);
  - `new_game` (N).

  It reuses existing strings. The system shows it with Meta+/.
- **Rationale:** keyboard, Chromebook and desktop-window players get a discoverable list. The API exists since API 24.
- **Rejected:** an in-app shortcuts screen (How to play already lists the keys).
- **Source:** [RESEARCH] FEATURES.md F16.

#### D-204 — Golden matrix of 45 PNGs and filmstrip frame names (extends D-085, D-147)
- **Decision:**
  - **Matrix.** The golden matrix is DESIGN_SYSTEM.md §10.4: 45 PNGs within the 10 MiB budget. It adds four rows to D-147's set: the board-size sheet, the new-game dialog, the What's new sheet and the first-launch hint. Each golden is compared by a test and Read.
  - **Filmstrip names.** The filmstrip goldens keep the names `…_t000`, `_t040`, `_t080`, `_t160` and `_t400`. On the 16 ms test-frame grid, they capture the frames at 0, 48, 80, 160 and 400 ms from the origin.
  - **ATF** (D-085) runs on every `game_*`, `layout_*`, `settings_*`, `stats_*`, `howtoplay_*`, `about_*`, `licenses_*`, `sheet_*` and `dialog_*` golden. Only `motion_*` is exempt.
  - **Milestones:**
    - M3 part 1: rows 1, 3, 37–41 and 43;
    - M3 part 2: rows 13, 15 and 42;
    - M4: rows 1–22 and 27–45, recorded or re-recorded, and Read;
    - M5: rows 23–26.
- **Rationale:** DESIGN_SYSTEM.md DS-OI-5; MOTION_SPEC.md §8.3 (the test clock advances in 16 ms frames).
- **Rejected:** renaming the filmstrip goldens.
- **Source:** [ORCH] O16 · [RESEARCH] DESIGN_SYSTEM.md §10.4.

#### D-205 — Contrast method and touch-target rule (clarifies D-072, D-083; extends D-084, D-085)
- **Decision:**
  - **Contrast method.** `PaletteContrastTest` uses the unquantized contrast and CVD ΔE method of DESIGN_SYSTEM.md §2.13 (simulated colors are not re-quantized to 8 bits). D-072's and D-083's minima are evaluated with it.
  - **Touch targets.** Every screen-level screenshot test runs `assertTouchTargets()`, QUALITY.md §1.12's size-and-overlap rule. It gates like ATF does.
- **Rationale:**
  - With the quantized method, protan 4096 → 8192 sits exactly on the 2.0 gate.
  - ATF's size-only touch-target check is vacuous under Compose's 48 dp touch-bounds expansion (QUALITY.md V6).
- **Rejected:** the quantized research/04 numbers as the oracle; ATF alone for D-084.
- **Source:** [ORCH] O16 · [RESEARCH] DESIGN_SYSTEM.md §2.13; QUALITY.md §1.12, V6.

#### D-206 — High-contrast empty cells (extends D-073)
- **Decision:** with high contrast on, the empty-cell `drawBehind` pass also strokes each empty cell:
  - a 1 dp line in `palette.ink(1)`;
  - inset 0.5 dp;
  - using the cell's corner radius.

  The chrome `outline` color is not used for this.
- **Rationale:**
  - Empty cells are only 1.13–1.24:1 against the board.
  - `ink(1)` against the empty fill is ≥ 4.31:1 in every palette.
  - `outline` against Classic light empty cells is 2.55:1.
- **Rejected:** stroking with `outline`; changing the empty fills.
- **Source:** [RESEARCH] DESIGN_SYSTEM.md §2.2, §2.7.

#### D-207 — Classic dark 2-tile fill (amends D-070)
- **Decision:**
  - Classic dark `fill(1)` becomes `#524A42` instead of research/04 §8 A's `#4A433C`.
  - Ink stays `#F1E9E0` (7.23:1). HC ink stays `#FFFFFF` (8.69:1).
  - Every other Classic value stays exactly research/04 §8 A.
  - Fallback: if `PaletteContrastTest` fails with this value, keep `#4A433C` and record that in the PR body.
- **Rationale:**
  - `#4A433C` is 1.26:1 against the dark empty cell `#3A342E`. `#524A42` is 1.41:1, matching light mode.
  - The 2 → 4 ΔE stays 4.3 / 4.1 / 4.4 / 4.2, and the ramp minima are unchanged (computed 2026-10-28).
- **Rejected:** keeping `#4A433C`.
- **Source:** [RESEARCH] DESIGN_SYSTEM.md §2.4.

#### D-208 — Localization readings (clarifies D-100, D-102, D-103, D-142)
- **Decision:**
  - **Word-free format strings.** The format strings that L10N_STORE_LEGAL.md §1.3 marks `translatable="false"` hold only placeholders and separators, such as `board_size_supporting`, `game_over_summary` and `stats_wins_value`. D-100's completeness rule applies to strings that contain words.
  - **Plurals.** D-103's plural list is moves, undos, tiles and merges. There is no `games` plural, because no v1.0 sentence counts games.
  - **AppCompat.** D-102's "No AppCompat" means no `androidx.appcompat:appcompat` and no `AppCompatDelegate`. `androidx.appcompat:appcompat-resources` 1.7.0, pulled in by `core-splashscreen` 1.2.0, is allowed and listed in the licenses.
  - **Pending strings.** English strings added in M3–M4 go into `app/src/main/res/values/strings_pending.xml`.
    - Its `tools:ignore="MissingTranslation"` carries the same-line reason that D-142 requires.
    - In M5 it is merged into `values/strings.xml` and deleted (`check_strings_final`).
  - **`many` items.** cs, es, fr, it, lt and pt carry a grammatical `many` item.
- **Rationale:** L10N_STORE_LEGAL.md §0.3, §1.3–§1.5 (verified with Lint 32.4.1).
- **Rejected:** translating placeholder-only strings; translating as you go in M3–M4.
- **Source:** [ORCH] O13 · [RESEARCH] L10N_STORE_LEGAL.md §1.

#### D-209 — Night-mode idea credit (extends D-124)
- **Decision:** the legacy credits list the git authors plus "gurshabad", whose night-mode idea legacy commit `f889036` credits. The credit appears in `legacy_history.json` and `docs/modernization/legacy-credits.md`.
- **Rationale:** research/02 §7 item 3.
- **Rejected:** git authors only.
- **Source:** [RESEARCH] research/02 §7; L10N_STORE_LEGAL.md §4.5.

#### D-210 — Motion verification policy (refines D-147, D-150)
- **Decision:**
  - **Binding event frames.** Assertions on when things happen are exact and are never edited to fit an implementation. They cover:
    - the origin;
    - the reveal, spawn-start and ghost-hide frames;
    - "+N" creation;
    - card compose and interactive frames;
    - haptic frames;
    - settle bounds;
    - recomposition counts;
    - invariants I1–I4.
  - **Curve values.** An assertion on an animated value at an intermediate frame takes its expected value from the token spec through `spec.vectorize(converter).getValueFromNanos(playTimeNanos, initial, target, initialVelocity)`, with the play time implied by the event frames.
    - The numbers in MOTION_SPEC.md §3 and §9 are reference values.
    - If the library value and the reference differ by more than the tolerance (scale ±0.002, alpha ±0.01), the test keeps the library value and the commit body records both.
  - **Tokens.** `MotionTokensTest` asserts the token literals exactly. Compose's settle estimate must lie within one 16 ms frame of MOTION_SPEC.md §1.1. A larger gap is fixed in MOTION_SPEC.md by a `docs:` change, with the library value as evidence.
  - **Harness plumbing** may be fixed, with a note in the commit body. Expectations and frame tables may not be changed.
  - **Inspection filmstrips.** M3 Reads every strip once. Later commits Read only the strips whose events changed.
- **Rationale:**
  - Event timing is what players see and what the app controls.
  - Intermediate spring values are library arithmetic.
- **Rejected:** deleting the numeric motion classes; editing event frames.
- **Source:** [ORCH] O16 · [RESEARCH] MOTION_SPEC.md §1, §8–§9.

#### D-211 — Milestone table refinements (refines D-162)
- **Decision:**
  - **M2** also delivers `game/board/BoardUi.kt`, `game/board/BoardPresenter.kt` (with `BoardPresenterTest`) and `game/feedback/HapticsMap.kt`, and `InputBurstOrderingTest` passes in M2.
  - **M3** is two gated, pushed commits, both with subjects that start `M3:`.
    - Part 1 delivers:
      - the motion runtime and the animated board;
      - input and haptics;
      - the Game screen shell: header, score motion, controls, the new-game dialog and snackbars;
      - the board and score motion tests;
      - goldens 1, 3, 37–41 and 43.
    - Part 2 delivers:
      - overlays;
      - the board-size sheet and share;
      - navigation with stub sub-screens;
      - their motion tests;
      - goldens 13, 15 and 42.
  - **M4** adds `ThemeCrossfadeTest`, `LayoutMotionTest` and the Settings parts of AC-HAP-4 and AC-SCALE-4. It records or re-records goldens 1–22 and 27–45 and Reads them.
  - **M5** adds goldens 23–26.
  - **M0** creates `core/engine/AGENTS.md`. `ci.yml`, root `AGENTS.md` and `CLAUDE.md` come from the plan PR (D-177).
  - **M6** has no docs reconcile.
  - **AC audit.** The coverage script (PLAN.md §5.7 A12) runs in report mode at every milestone gate. It must report no missing AC that FEATURES.md §8 assigns to a finished milestone.
- **Rationale:** sequencing; a pushed recovery point in the largest milestone; early detection of missing AC coverage.
- **Rejected:** a single M3 commit.
- **Source:** [ORCH] O17 · orchestrator directive (plan review, 2026-10-28).

---

## Appendix A: Conflict-resolution index

### A.1 Research contradictions (research/07 §1, C1–C25)

| C | Topic | Resolution | D-ID |
|---|---|---|---|
| C1 | Gradle version | 9.7.1 (not 8.14.3) | D-010 |
| C2 | compile/target SDK | 37 / 37 | D-013 |
| C3 | minSdk | 24 | D-013 |
| C4 | Window size API | `currentWindowAdaptiveInfoV2()` | D-026, D-092 |
| C5 | ktfmt | 0.65 (fallback 0.64) | D-141 |
| C6 | R8 DSL | Legacy, smoke-verified DSL | D-132 |
| C7 | Metadata locale count | 29 legacy (01 is right). Final tree has 22: 13 kept, 9 added, 16 deleted | D-113 |
| C8 | Metadata path | `fastlane/metadata/android/` | D-113 |
| C9 | versionCode | 30000 / 3.0.0, literals | D-130 |
| C10 | Merge target | `master` | D-165, D-168 |
| C11 | Swipe commit timing | During the drag by default (O3); release as a setting | D-050 |
| C12 | Input during animation | No queue; synchronous apply; snap | D-052 |
| C13 | Haptics | O5 map; ≥ 512 for Confirm; On/Off | D-065 |
| C14 | Motion setting | Full / Reduced / Off | D-063 |
| C15 | Dynamic color | Chrome only; tile ramp deferred | D-074 |
| C16 | Font | GSF digit subset + system font | D-076 |
| C17 | RTL golden locale | `fa` (+ `ar-XB`) | D-104, D-106 |
| C18 | Robolectric SDK | 36 for goldens; smoke at 24 and 37 | D-146 |
| C19 | Gesture exclusion | None; inset the board | D-051 |
| C20 | detekt | 2.0.0-alpha.6 with exit criterion | D-144 |
| C21 | Undo depth | Unlimited | D-039 |
| C22 | Locales at v1.0 | en + 20, `MissingTranslation` error | D-100 |
| C23 | Board sizes at v1.0 | 3×3–8×8, geometry generalized | D-030, D-075 |
| C24 | Per-app language picker | System settings, API 33+ | D-102 |
| C25 | Permission wording | "No runtime permissions"; self-permission documented | D-002 |

### A.2 Additional cross-file contradictions found while writing this log

| # | Conflict | Resolution | D-ID |
|---|---|---|---|
| X1 | research/06 §7 says the Kover and Kotlin Compose markers are Portal-only | Verified on Maven Central; Portal dropped | D-015 |
| X2 | research/03 §8: engine uses `kotlin.random.Random` and `@Serializable` snapshots | SplitMix64; DTOs live in `:app` | D-021, D-038 |
| X3 | research/03 §1.7 / research/07 G10: Preferences DataStore for settings | Typed JSON for all three files (O2) | D-040 |
| X4 | research/06 "< 5 MB at 20k moves" vs research/07 "≤ 256 KiB at 10k" | The stricter research/07 budget | D-046 |
| X5 | research/06 §5.3 4.5:1 rule for all tile text vs research/04 §6.4 | research/04 policy; AGENTS rule aligned | D-083, D-171 |
| X6 | research/06 inspects with build-tools 36.0.0 vs `-P 16` verified on 37.0.0 | 37.0.0 for scripts | D-018 |
| X7 | research/03 §11 / smoke build opt into `ExperimentalMaterial3ExpressiveApi` | No opt-in (O1); graphics-shapes | D-012 |
| X8 | research/04 §5 Off/Subtle/Full levels and `ToggleOff` on undo | On/Off; no undo haptic | D-065 |
| X9 | research/04 §8 "Material You" selectable tile theme | Deferred (O6) | D-074 |
| X10 | research/05 "Confirm new game" toggle | Always confirm when in progress (O12) | D-007 |
| X11 | research/07 G16 "no What's new UI in v1.0" | Mechanism ships (O11); fires only after an update | D-009 |
| X12 | research/07 D3 "Undo Off switch, top-10 in v1.0" | Deferred; no-undo best kept | D-003, D-044 |
| X13 | research/06 §3.5 release workflow with an ephemeral key; smoke release signed with debug | Committed sideload key; no release workflow | D-131, D-133 |
| X14 | research/04 §7.2 posture layouts vs research/07 G11 | Deferred | D-091 |
| X15 | research/06 §1.3 `pixelCopyRenderMode` / `isReturnDefaultValues` | Not adopted (outside the baseline) | D-146 |
| X16 | Smoke manifest sets `enableOnBackInvokedCallback` and a platform theme | Omitted / splash theme | D-090, D-112 |
| X17 | research/05 §4.2 compact labels by board size vs O7 "when the cell is too small" | Size-based rule (< 12 dp) | D-077 |
| X18 | research/04 §6.3 verbosity setting | One brief level | D-081 |
| X19 | research/06 Roborazzi `outputDir` + `filePathStrategy` | Explicit paths (baseline) | D-147 |
| X20 | research/05 §4.17 "persist in `onStop`" vs conflated per-move writes | Per-change conflated writes | D-043 |
| X21 | research/04 §6.1 "System" as a motion option | System scale 0 forces Off; options Full/Reduced/Off | D-063 |
| X22 | research/04 board formula hardcodes 4 tiles; research/04 §6.3 `CollectionInfo(4,4)` | N-generalized | D-075, D-080 |
| X23 | research/05 settings "Animation speed", "Show moves and time" | Not in v1.0 | D-006 |

### A.3 research/07 §4 top decisions D1–D15

| research/07 | Resolution here | Agreement |
|---|---|---|
| D1 Toolchain | D-010–D-014, D-018 | Agree |
| D2 Fork/PR mechanics | D-160–D-170, D-193–D-197 | Agree (mid checkpoint after M2) |
| D3 v1.0 scope | D-003 table | Agree, **except** Undo-Off and top-10 deferred, What's new added |
| D4 Data model | D-030, D-038–D-046 | Agree; layouts made exact |
| D5 Architecture | D-020–D-025, D-112 | Agree; 6 Nav keys |
| D6 Input | D-050–D-053 | **Differs:** default commits during the drag (O3) |
| D7 M3 version | D-012, D-026 | Agree |
| D8 Visuals | D-070–D-074 | Agree; super-tile hexes added (D-072) |
| D9 Motion/haptics | D-060–D-066 | Agree; one-haptic-per-move priority added |
| D10 Typography | D-076 | Agree; digit-only subset |
| D11 Localization | D-100–D-106 | Agree |
| D12 Verification | D-146, D-147, D-085 | Agree |
| D13 CI gates | D-140–D-152 | Agree |
| D14 Identity assets | D-111, D-112, D-113–D-115 | Agree |
| D15 Release/privacy/manifest | D-090, D-123, D-130–D-134, D-048 | **Differs:** APKs are signed with the committed sideload key (U5), not left unsigned |

### A.4 research/07 §2 gaps G1–G23

| Gap | D-ID |
|---|---|
| G1 Fork mechanics | D-165, D-172, D-195 |
| G2 Legacy cleanup sequence | D-162 (M0), D-101, D-113 |
| G3 License matrix | D-120–D-122 |
| G4 Icon | D-111 |
| G5 Splash | D-112 |
| G6 Store listing | D-113–D-115 |
| G7 Localization mechanics | D-100–D-106 |
| G8 RTL | D-104 |
| G9 Process death | D-047 |
| G10 Data versioning and encoding | D-040–D-042 |
| G11 Tablet/foldable | D-090, D-091, D-092 |
| G12 Performance budgets | D-046, D-150 |
| G13 Exported components; EmojiCompat | D-148, D-090 |
| G14 Privacy | D-123 |
| G15 Backup | D-048 |
| G16 Versioning; What's new | D-130, D-009 |
| G17 F-Droid | D-134, D-004 |
| G18 Release policy | D-133 |
| G19 Device-free verification | D-147, D-019 |
| G20 AGENTS.md | D-171, D-177, D-192 |
| G21 One PR with milestones | D-162, D-166, D-193, D-211 |
| G22 Architecture | D-022–D-024 |
| G23 Keyboard map | D-053 |

---

## Appendix B: Technical corrections

A technical correction changes only the spelling of a command, or an expected tool output, that does not work as written. The decided fact stays the same. Each corrected entry marks the corrected text with "(technical correction TC-n)".

| ID | Entry | Correction (decided fact unchanged) | Evidence |
|---|---|---|---|
| TC-1 | D-131 ([USER] U5) | keytool's `-dname` escapes the `;` as `\;`: `-dname "CN=2048 CI sideload key (public\; not for store uploads), O=clayboicardi"`. The resulting subject is unchanged: `CN="2048 CI sideload key (public; not for store uploads)", O=clayboicardi` | ARCHITECTURE.md §0, §2.9: the unescaped command prints `keytool error: java.io.IOException: Incorrect AVA format` (JDK 21.0.12) |
| TC-2 | D-148 ([USER] U6) | Item 1: `aapt2 dump badging` (build-tools 37.0.0) prints `minSdkVersion:'24'`, not `sdkVersion:'24'`. Item 6: the signer digest is read from the `apksigner verify --print-certs` lines that contain `certificate SHA-256 digest:` | ARCHITECTURE.md §0; QUALITY.md V12 |
| TC-3 | D-101 | Lint reports a translated `app_name` as `Untranslatable`, not `ExtraTranslation`. Both are fatal under D-142 | L10N_STORE_LEGAL.md §0.3 item 15 |
| TC-4 | D-010 | Run the bootstrap in a new, empty temporary directory outside the repository: `touch settings.gradle.kts`; then `gradle wrapper --gradle-version 9.7.1 --distribution-type bin --gradle-distribution-sha256-sum acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`; then `./gradlew wrapper` with the same arguments in that same directory. Copy `gradlew`, `gradlew.bat` and `gradle/wrapper/*` into the repository, but not the temporary `settings.gradle.kts`. Then run `git update-index --chmod=+x gradlew`. The resulting `gradle-wrapper.properties` is byte-identical to D-010's | A directory without a settings file is not a Gradle build, and both 8.14.3 and 9.7.1 fail there. The repository root is not a build between M0.6 and M0.8. With the empty settings file, both steps succeed (reproduced 2026-10-28) |
| TC-5 | D-144 | `config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))` | ARCHITECTURE.md §0: the relative path resolves per module (for example `app/config/…`), which does not exist |
| TC-6 | D-145 | Each property-test class seeds in `@BeforeEach` with `PropertyTesting.defaultSeed = 0x2048L` and `PropertyTesting.writeFailedSeed = false`, and properties call `checkAll(1_000, genA, genB) { … }`. `PropTestConfig` is not used. The seed 0x2048 and the 1,000 iterations are unchanged | Compile probe: `PropTestConfig(…)` in every form needs `@ExperimentalKotest` in kotest 6.2.6. The replacement compiled and ran 1,000 iterations. `PropertyTesting.defaultSeed` and `writeFailedSeed` carry no opt-in marker (class files, 2026-10-28) |
| TC-7 | D-064 | The invalid-move nudge (`NUDGE` 0.3/1800, 330 dp/s) settles at 346 ms under Compose's estimator with a 0.1 dp threshold. research/04's 393 ms used an extra velocity criterion. The spring itself is unchanged | MOTION_SPEC.md §1.1 (computed 2026-10-28) |

---

## Open issues

None are open. The planning-time issues were settled as follows:
- **OI-1:** D-050's commit-during-drag default stays (O3), with the "When you lift your finger" option.
- **OI-2:** Portal-free plugin resolution was built (ARCHITECTURE.md §0).
- **OI-3:** D-076's reading stands.
- **OI-4:** the round-10 stop is the orchestrator's rule (D-196).
- **OI-5:** covered by D-196 and PLAN.md risk R5.
- **OI-6:** noted in D-009.
- **OI-7:** PLAN.md risk R1.
- **OI-8:** D-113's 22 directories.

## Verification-pending assumptions (confirm at first use; fallback in parentheses)

Resolved during planning:
- items 3, 4, 7 and 9 (ARCHITECTURE.md §0);
- items 5 and 10 (QUALITY.md V4, V5; ATF with D-174);
- item 8, replaced by `OpaquePngWriter` (D-190).

Still open:
- **Item 1.** Local and CI screenshots are bit-identical (research/07 U7). The M0 CI run proves it. (Fallback: commit the CI-produced images after Reading them, then threshold 0.001, D-147.)
- **Item 2.** The session token can push `.github/workflows/*` (research/07 U8). The plan PR's first push proves it. (Fallback: D-172.)
- **Item 6.** ktfmt 0.65 through Spotless 8.10.4 is idempotent on the real sources. Checked in M1 and M3 by running `spotlessApply` twice, then `spotlessCheck`. (Fallback: 0.64, D-141.)
- **Item 11.** Codex follows the `## Code Review Rules` section (D-192). Checked on the plan PR's Codex round: no item under "Intentional, do not flag" is raised. (Fallback: narrow or remove the noisy rule group in a `docs:` commit.)
- **Item 12.** The bot's reactions can be read with their author through the unauthenticated REST reactions endpoints, and comment objects expose reaction counts (D-196). Checked on the plan PR's Codex loop. (Fallback: use only the review or no-issue-comment clean signal. If the bot signals clean only by reaction and that cannot be read, apply D-172 item 2 once.)
