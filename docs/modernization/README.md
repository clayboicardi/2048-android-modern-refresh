# Modernization docs

Specification and plan for rewriting 2048 for Android as a native Kotlin + Jetpack Compose + Material 3 app (`com.clayboicardi.game2048`, first released as 1.0.0, DECISIONS.md D-271). The work lands in two pull requests (DECISIONS.md D-160): the plan PR, which adds this directory, `.github/workflows/ci.yml`, `AGENTS.md` and `CLAUDE.md`, and one execution PR that implements milestones M0–M6.

## Reading order

| # | Document | What it holds |
|---|---|---|
| 1 | [DECISIONS.md](DECISIONS.md) | Canonical decision log D-001–D-211 plus Appendix B technical corrections TC-1–TC-7: scope, toolchain, engine rules, persistence, input, motion, design, accessibility, localization, legal, signing, gates, process, change control. Wins every conflict |
| 2 | [PLAN.md](PLAN.md) | Execution plan: success criteria, canonical naming index, entries added during planning, milestones M0–M6 with tasks, work packages, gates and commits, execution protocol, traceability, risks |
| 3 | [ARCHITECTURE.md](ARCHITECTURE.md) | Repository layout, build files, engine API and algorithms, app architecture, rendering pipeline, input, navigation, manifest, file map |
| 4 | [FEATURES.md](FEATURES.md) | Features F01–F30 with acceptance criteria mapped to test methods, settings catalog, statistics, legacy parity, roadmap |
| 5 | [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Palettes, typography, board geometry, layout modes, screens, components, icons, golden list (45 PNGs) |
| 6 | [MOTION_SPEC.md](MOTION_SPEC.md) | Motion tokens, per-event timelines, invariants, haptics, motion test harness and `AC-*` criteria |
| 7 | [QUALITY.md](QUALITY.md) | Test pyramid and infrastructure, check scripts, `scripts/verify.sh`, CI workflow, Codex review protocol, definition of done |
| 8 | [L10N_STORE_LEGAL.md](L10N_STORE_LEGAL.md) | String inventory and translation workflow for 21 locales, store listing, identity assets, LICENSE, NOTICE, privacy, credits, signing distribution |

Research (evidence; frozen, cited by the documents above):

| Document | What it holds |
|---|---|
| [research/01-legacy-android-shell.md](research/01-legacy-android-shell.md) | Audit of the legacy WebView shell |
| [research/02-legacy-game.md](research/02-legacy-game.md) | Audit of the legacy web game rules and bugs |
| [research/03-toolchain.md](research/03-toolchain.md) | Toolchain versions and their verification |
| [research/04-design-motion.md](research/04-design-motion.md) | Design and motion research |
| [research/05-features.md](research/05-features.md) | Feature landscape and prioritized roadmap |
| [research/06-quality-ci.md](research/06-quality-ci.md) | Testing, CI and Codex review research |
| [research/07-critique.md](research/07-critique.md) | Completeness critique of 01–06 |
| [research/smoke-build/](research/smoke-build/) | Minimal Gradle project used to verify the toolchain |

Files added later in this directory: `legacy-credits.md` (M0, legacy authors and translators captured before the submodule is removed) and `i18n-glossary.md` (M5, committed before any translation).

Related files outside this directory: [`AGENTS.md`](../../AGENTS.md) (agent instructions and Codex review rules), [`CLAUDE.md`](../../CLAUDE.md) (agent session notes), [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) (CI, a no-op until M0 adds `settings.gradle.kts`), [`.github/workflows/release.yml`](../../.github/workflows/release.yml) (a GitHub Release per version, dispatched by hand, D-271), and `core/engine/AGENTS.md` (engine review rules, added in M0).

## Status

| Item | State |
|---|---|
| Plan PR (this directory, `ci.yml`, `AGENTS.md`, `CLAUDE.md`) | Open for review |

Execution progress lives in the execution PR body's milestone checklist (PLAN.md §6.9).
