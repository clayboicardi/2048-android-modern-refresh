# 07: Completeness critique of research files 01–06

Track: **completeness critic**. Date: 2026-10-28.

Inputs, all read in full:
- 01-legacy-android-shell (607 lines)
- 02-legacy-game (336)
- 03-toolchain (723)
- 04-design-motion (934)
- 05-features (611)
- 06-quality-ci (842)

How I checked things. I re-verified every disputed fact against a live primary source or an artifact. Each verdict below names its evidence:
- maven-metadata from `dl.google.com` and Google's Maven Central mirror. `repo1.maven.org` returned empty or 429 in this sandbox.
- AAR manifests and `aar-metadata.properties`.
- `adaptive-android-1.3.0-sources.jar`.
- `spotless-lib-4.10.4.jar` bytecode.
- fontTools on the Google Sans Flex TTF.
- developer.android.com pages.
- `git ls-remote` against GitHub.
- The GitHub repo page.
- A Robolectric/Roborazzi render test I ran myself (§3, U6).

Scratch artifacts are under `.../scratchpad/critic/`.

---

## 0. TL;DR

The research is strong and mostly consistent. The **material defects** are:
1. **01 says the Gradle wrapper should be 8.14.3. That is impossible for this stack.** Compose UI 1.12.1 requires AGP ≥ 9.1.0 and compileSdk 37 (AAR metadata). AGP 9.4.x requires Gradle ≥ 9.6.0. Use wrapper **9.7.1**; `/opt/gradle-8.14.3` is only for bootstrapping the wrapper in an empty directory.
2. **minSdk is 24, not 23.** DataStore 1.3.0 alone declares `minSdkVersion="24"` (verified for `datastore-android`, `datastore-core-android` and `datastore-preferences-android`).
3. **03 recommends `currentWindowAdaptiveInfo()`, which is `@Deprecated(level = WARNING)` in adaptive 1.3.0.** Under 06's `allWarningsAsErrors = true` the build would fail. Use `currentWindowAdaptiveInfoV2()`, as 04 says.
4. **The repo is a GitHub fork** (`"isFork":true`, parent `uberspot/2048-android`). No file accounts for the consequences:
   - "Workflows don't run in forked repositories by default" (GitHub docs).
   - `gh pr create` may default the base repo to the upstream parent.
   - Scheduled workflows are disabled on forks.

   This can silently break the hands-off CI → Codex → merge loop, or open a PR against uberspot's repo.
5. **There is no `main` branch.** The default is `master` (`git ls-remote --symref origin HEAD` → `refs/heads/master`). "Merge to main" means `master`.
6. **Play requires a privacy policy for every app**, including apps that collect nothing: in-app text plus a Console link (Developer Program Policy, 2026-09-30). No file covers this.
7. **Robolectric downloads its android-all jars itself** from `repo1.maven.org`, not through Gradle, so the sandbox's mirror init script does not cover them. My first SDK-36 run failed with `Failed to fetch maven artifact org.robolectric:android-all-instrumented:16-robolectric-13921718-i7`. Setting the system property `robolectric.dependency.repo.url=https://maven-central.storage-download.googleapis.com/maven2` fixed it (verified). Executor agents will hit this.
8. Several cross-file product conflicts need a single answer (§1 C11–C16, C21–C24):
   - swipe commit timing;
   - input queueing;
   - haptic thresholds;
   - motion setting shape;
   - Material You tile ramp vs chrome-only dynamic color;
   - locale scope at v1.0;
   - board-size scope.

Verified good: every version pin in 03 and 06 matches live metadata, and all five GitHub Action SHAs in 06 match their tags. A Robolectric 4.17 render at SDK 36 shows that the following all work on the JVM:
- **Persian, Cyrillic, Greek, Korean and Czech system-font rendering**;
- **RTL mirroring** (only with `android:supportsRtl="true"`);
- **forced-LTR sub-trees**.

So device-free UI verification is viable for every shipped locale.

---

## 1. Contradictions between files, with verdicts

| # | Topic | Claims (file §) | Verdict | Evidence |
|---|---|---|---|---|
| C1 | **Gradle version** | 01 §2 "Gradle 8.14.3 (locked) on JDK 21: OK"; 01 D1 and §9 #26 "Gradle 8.14.3 wrapper". 03 §0 and 06 §3.7: wrapper **9.7.1** | **03/06 are right.** 8.14.3 cannot run AGP 9.x, and Compose 1.12 requires AGP 9 | developer.android.com/build/releases/gradle-plugin (fetched): "AGP 9.4.0, September 2026 … Gradle 9.6.0 min/default, SDK Build Tools 36.0.0, JDK 17, API level 37". `ui-android-1.12.1.aar` metadata: `minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0`. AGP 9.1 → Gradle ≥ 9.3.1 (03 citing about-agp). Gradle current = **9.8.2** (services.gradle.org, buildTime 2026-10-19). 9.7.1 sha256 `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` (03; smoke run with zero deprecations). The task header's "Gradle 8.14.3 at /opt/gradle-8.14.3" describes the environment, not a version lock |
| C2 | **compileSdk / targetSdk** | 01 §0.6 and D6: "target 36 / compile 36–37". 03: compile **37** / target **37**. 04 discusses target-36 behaviours. 05/06 assume 37 | **compileSdk 37 is mandatory. targetSdk 37 is recommended** | compileSdk: `ui-android 1.12.1`, `navigation3-runtime-android 1.2.0` and `material3-android 1.5.0-beta02` all declare `minCompileSdk=37` (downloaded). targetSdk: Play floor is 36 since 2026-08-31 (01/03). Android 17 final 2026-06-16 (03). 37 pre-empts next year's floor, and nothing in the app depends on target-36-only behaviour. Robolectric can still render at `sdk=36` with target 37 (my run, §3 U6) |
| C3 | **minSdk** | 01 §0.6 and D7: 23 ("AndroidX stable requires minSdk 23"). 03/05/06: **24** | **24** | Downloaded AAR manifests: `datastore-android-1.3.0` = 24, `datastore-core-android-1.3.0` = 24, `datastore-preferences-android-1.3.0` = 24, `navigation3-runtime-android-1.2.0` = 24, `material3-android-1.5.0-beta02` = 24. `ui-android-1.12.1` = 23, `material3-android-1.4.0` = 21, `core-splashscreen-1.2.0` = 21. DataStore alone forces 24 even with M3 1.4.0 and no Nav3 |
| C4 | **Window size API** | 03 §1.7: "use `currentWindowAdaptiveInfo()` … `material3-window-size-class` is the older API". 04 §0.10/§7.1: `currentWindowAdaptiveInfoV2()`; the old one is deprecated | **04 is right** | `adaptive-android-1.3.0-sources.jar`, `WindowAdaptiveInfo.kt:53-59`: `@Deprecated("Please use V2 version of this function to support L and XL width size classes.", ReplaceWith("currentWindowAdaptiveInfoV2"), DeprecationLevel.WARNING)`. The Android no-arg overload is `DeprecationLevel.HIDDEN` (`AndroidWindowAdaptiveInfo.android.kt:33-39`). With 06 §2.2 `allWarningsAsErrors = true`, 03's call fails compilation. V2 = `computeFromDpSizeV2` (L/XL breakpoints 1200/1600) |
| C5 | **ktfmt version** | 03 §1.10: 0.64 ("0.65 not yet a Spotless 8.10.4 default"). 06 §2.3: 0.65 is the default | **06 is right** | `spotless-lib-4.10.4.jar`, `com/diffplug/spotless/kotlin/KtfmtStep.class` strings: `0.65`, `com.facebook:ktfmt:`, `org.jetbrains.kotlinx:ktfmt:`. Mirror metadata: `org.jetbrains.kotlinx:ktfmt` release 0.65 (lastUpdated 20261007); `com.facebook:ktfmt` 0.64. Use `ktfmt("0.65").kotlinlangStyle()`; 0.64 is the smoke-verified fallback |
| C6 | **R8 DSL** | 03 §11: `isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), …)`. 06 §4.1: `optimization { enable = true }` | **Both are valid.** Prefer the new DSL, but only 03's legacy form has been smoke-built | developer.android.com/topic/performance/app-optimization/enable-app-optimization (fetched): `optimization {}` was introduced in **AGP 9.3** ("optimized resource shrinking is always enabled, default Android keep rules are opt-out"). Keep rules go in `src/main/keepRules/*.keep`. The legacy DSL "is still supported"; `proguard-android.txt` is unsupported since AGP 9.0. Executor must run `:app:assembleRelease` once with the new DSL before relying on it |
| C7 | **Number of store-metadata locales** | 01 §6: 29. 06 §4.3: "30 locales" | **29 (01 is right)** | `ls metadata \| wc -l` = 29: af am ar bg ca de el en-US es et fi fr hi id it ja ko lt lv pt-BR pt-PT ro ru sv sw th tr uk vi |
| C8 | **Store-metadata location** | 01 §6: move to `fastlane/metadata/android/<locale>/`, graphics in `images/`. 06 §4.3/§6 M5: keep `metadata/en-US/changelogs/30000.txt` | **Both are read by F-Droid. Choose fastlane (01)** | F-Droid "All About Descriptions…" (via search index; f-droid.org does not resolve in this sandbox): "placed at … `metadata/<locale>/` [or] `fastlane/metadata/android/<locale>/`". Limits: title.txt max 50, short max 80, full max 4000, `changelogs/<versionCode>.txt` max 500, "literally, no padding". The Quick Start Guide lists the fastlane paths, and fastlane `supply` reads only those. Use `git mv` to keep history |
| C9 | **versionCode** | 03 §11 snippet: `versionCode = 1`, `versionName = "3.0.0"`. 01 §8.3 and 06 §4.5: **30000** / "3.0.0" | **30000 / 3.0.0, both literals** | Consistent with `changelogs/30000.txt`, 06's tag == versionName gate, and the MAJOR·10000+MINOR·100+PATCH scheme. The new appId means no constraint from legacy code 25 |
| C10 | **Merge target** | The user says "merge to main". 06 §0: default branch `master` | **`master`** | `git ls-remote --symref origin HEAD` → `ref: refs/heads/master`. `git ls-remote --heads origin` shows only `master` and the plan branch. Do not rename the default branch (that needs admin rights and churns CI and Codex). State the mapping in the PR body |
| C11 | **When a swipe commits** | 04 §4.4: commit **during the drag** at 24 dp with `\|major\| ≥ 1.4·\|minor\|`, plus a fling ≥ 600 dp/s fallback on release. 05 §4.14/§4.16/§6: default **on release**, with setting "Move on: Release / As soon as detected". 02 §9: "commit once the threshold is crossed" | **Unresolved product call.** Recommendation in §4 D6 | Legacy commits on lift (`keyboard_input_manager.js:127-129`). A review asks to "finalize the move … when the finger is lifted" [05 S10]. Misclassification complaints ("I swiped right and it went down") favour using the full vector at release |
| C12 | **Input during animation** | 04 §4.4: input "never blocked or queued". 05 §4.16 rule 5: "Queue depth is 1; drop anything beyond that". 06 §1.2: "3 swipes in quick succession sees all 3 applied in order" | **04 + 06** | With state-first apply, every committed gesture is applied synchronously to the latest model. Animations snap (`snapTo`) and restart, so no queue exists. 05's "drop beyond 1" contradicts 06's test and re-creates the "lost input" complaint |
| C13 | **Haptics** | 04 §5: setting Off/**Subtle**/Full; `Confirm` when max merge ≥ **128**; win = `Confirm` ×2 (+120/+270 ms); new best = `GestureThresholdActivate`. 05 §4.14/§4.23: On/Off; `Confirm` at ≥ **512**; new best/win = `Confirm` | **Pick one map** (§4 D9) | 04 quotes Android haptics principles: "less is more", and frequent events "should be very subtle". A 128 merge happens many times per 4×4 game, while 512 is rare. So ≥ 512 fits the principle better |
| C14 | **Motion setting** | 04 §6.1: "Motion: System (default) / Reduced / Full". 05 §4.14: "Animation speed: Off / Fast 0.6× / **Normal** / Relaxed 1.5×" | **One setting** (§4 D9) | Both rely on Compose's `MotionDurationScale`. 04 verified in `SuspendAnimation.kt:328-338` that scale 0 jumps to the end value. A speed multiplier quadruples the screenshot/behaviour matrix for little value. 04's reduced-motion substitution table (§6.2) is the accessibility requirement (WCAG 2.3.3) |
| C15 | **Tile palette under dynamic colour** | 04 §8: three directions; "Material You" generates the **tile ramp** at runtime in HCT and needs 7 vendored files / 2,098 lines. 05 §4.4: dynamic colour applies to **chrome only**, the tile palette stays classic, plus a **high-contrast tile palette** | **05 for v1.0; 04-B deferred to v1.x** | 04 §3.4 measured that the 13 system tonal slots produce duplicate tiles (seed `#B3261E`: 4 and 8 both `#FFDAD5`). A correct dynamic ramp needs HCT vendoring or an OKLCH approximation, which is extra code for a cosmetic option. Classic Refined (A) and Neon (C) are static hex tables (04 §8), so they are cheap |
| C16 | **Font** | 02 §9: Clear Sans (Apache-2.0, WOFF→TTF) "or switch". 04 §9: **Google Sans Flex** (OFL) for numerals and title, system font for UI | **04 is right**, with an extra caveat | Verified: `googlefonts/googlesans-flex` README "SIL Open Font License, without Reserved Font Names". Google Fonts FAQ: "Before November 2025 these fonts were only available for use by Google, but they are now available under the SIL Open Font License". TTF name ID 13 = OFL 1.1. fontTools on `GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf`: 686 glyphs, cmap 541; **Cyrillic 0/64; Greek only U+03BC**; Latin Ext-A 105/128, **missing ĈĉĜĝĤĥĴĵŜŝ (Esperanto)** plus ĔĕĬĭŊŋŎŏŦŧſĸŉ; no U+06F0–06F9; no Hangul; GSUB has `tnum lnum pnum zero ss01 ss02`. Clear Sans has no `tnum` (04 §1). So GSF must **never** style translated strings |
| C17 | **RTL screenshot locale** | 06 §1.4 matrix: "`ar` RTL" | **Use `fa`** (and pseudo-locale `ar-XB`) | `ar` exists only in the store metadata, while `fa` is the shipped RTL locale (`res/values-fa`). My Robolectric run with `qualifiers = "fa-w411dp-h914dp-xhdpi"` rendered Persian correctly |
| C18 | **Robolectric SDK for goldens** | 03 §6: `@Config(sdk = [37])` passed. 06 §1.3: pin `sdk=36` | **36 for goldens; one smoke at `[24, 37]`** | Both work: 03 ran 37 and I ran 36 with targetSdk 37. SDK 36 has two Robolectric releases of maturity |
| C19 | **Gesture exclusion** | 04 §7.4: exclude only the bottom 200 dp, on edge-hugging layouts. 05 §4.16/§6.5: "board edges carry gesture exclusion of ≤200 dp per side" | **Same cap, different emphasis.** Primary mitigation is insetting the board | The 200 dp cap is verified (§3 U2) and is consumed from the bottom up. Recommendation: inset the board by `max(16dp, systemGestures left/right)`; add exclusion only on edge-hugging layouts (phone landscape 3-column), on the bottom ≤ 200 dp. The acceptance criterion should read "≤ 200 dp **vertical** per edge" |
| C20 | **detekt** | 03 §1.10: optional. 06 §2.4: a gate with an exit criterion; no compose-rules | **06's approach** | 1.23.8 is incompatible (both files agree). 2.0.0-alpha.6 is the latest (mirror metadata, lastUpdated 20260804) |
| C21 | **Undo depth** | 01 §9 #17 and 02 §9: "bounded undo (legacy cap ~50)". 05 §4.1/§7: **unlimited** (move log + snapshot every 32 moves) | **Unlimited (05)**, with a size budget (§2 G12) | 05: a 10,000-move game is about 2.5 KB of log plus about 31 KB of snapshots. Persistence cost is bounded if the encoding is compact (§2 G10) |
| C22 | **Locales at v1.0** | 01 §8.1: ship en + **20 legacy** at v1.0. 05 §4.24: v1.0 = en + ru/uk only; 20 locales in v1.x. 06 §2.1: `MissingTranslation` informational | **en + 20 at v1.0**, and `MissingTranslation` **fatal before merge** (§4 D11) | Shipping partially-English UIs repeats the legacy defect: 18 of 20 locales saw an English game (01 §4.10, 02 §6). Translation is agent work inside the same PR |
| C23 | **Board sizes at v1.0** | 02 §9: 4×4 default, the engine "parameterised … modes later". 04: geometry `tile = (board − 5·gap)/4`, `CollectionInfo(4,4)`, numeral table up to 6 digits. 05 §4.2: **3×3–8×8 at v1.0** | **05 (3–8 at v1.0)**, with 04 generalised to N | Table stakes per the 05 survey (Androbaby 10M+, OFG, PF2048, Quovad). Generalise 04's formula to `tile = (board − (N+1)·gap)/N`, semantics `CollectionInfo(N,N)`, and 05's K-suffix labels |
| C24 | **Per-app language picker** | 01 §8.1: optional in-app picker via `AppCompatDelegate` + `autoStoreLocales`. 03 §9: it needs AppCompat 1.8.0 on API < 33. 05 §4.14: open the system per-app language settings on 33+ | **05** | `generateLocaleConfig` (03 smoke-verified) gives system per-app language on 33+ at no cost. Adding AppCompat to a pure-Compose app only for API 24–32 is not worth it |
| C25 | **APK permission wording** | 01 §9 #23 / 03 §9 / 06 §3.4 all keep androidx.core's signature self-permission. 05 notes that F-Droid lists it under "Permissions" | **Consistent.** Keep it, and word the listing as "no runtime permissions" | Verified in the smoke release APK: `permission` + `uses-permission` = `com.clayboicardi.game2048.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `protectionLevel=0x2` (signature) |

Minor inconsistencies:
- 06 §1.1 golden table and 02 §1 harness rows overlap but use different rows. Merge both sets into the golden test (02 T6: `2222`, `222_`, `422_`, `8_88`, `2244`; 06: `2240`, `4480`, `8880`, `2024`, `2202`, `0002`, `2 4 8 16`, `1024 1024`).
- 03 §1.7 lists `navigationevent-compose` 1.2.0 while activity-compose 1.13.0 pins 1.0.0. Declaring 1.2.0 explicitly is fine (Gradle picks the higher version), but it was not smoke-built.

---

## 2. Gaps: what a full overhaul plan needs that no file covers (or covers only partially)

### G1. Fork mechanics: PR base, Actions enablement, Codex install, workflow scope (not covered; blocks the hands-off loop)

- **Fact:** the GitHub page for `clayboicardi/2048-android-modern-refresh` embeds `"isFork":true` and links `uberspot/2048-android`.
- **Actions.** GitHub docs ("Events that trigger workflows"): "Workflows don't run in forked repositories by default. You must enable GitHub Actions in the **Actions** tab of the forked repository." Also: "When a public repository is forked, scheduled workflows are disabled by default". So 06 §3.6's weekly `instrumented.yml` cron will not fire. A community report (github.com/orgs/community/discussions/53510) says pushing a branch that adds a new file under `.github/workflows/` silently enables Actions. That is **unverified**.
  - Plan step: after the first push containing `ci.yml`, poll `gh run list -R clayboicardi/2048-android-modern-refresh --branch <branch> --limit 1` for up to 5 min.
  - If no run appears, try `gh api -X PUT repos/clayboicardi/2048-android-modern-refresh/actions/permissions -F enabled=true` (needs admin).
  - If that fails, record the blocker in the PR body and post the full local `./scripts/verify.sh` output as the CI substitute. Tell the user at the end.
- **PR base.** For a fork, `gh` can resolve the base repository to the parent. **Always** use one of:
  - `gh pr create -R clayboicardi/2048-android-modern-refresh --base master --head <branch>`;
  - run `gh repo set-default clayboicardi/2048-android-modern-refresh` first;
  - GitHub MCP `create_pull_request(owner="clayboicardi", repo="2048-android-modern-refresh", base="master")`.

  Never open a PR against `uberspot/2048-android`.
- **Codex.** The Codex GitHub connector must be installed on *this* fork. Detect it by the 👀 reaction within about 2 min of `@codex review` (06 §6 M0 has this check; keep it).
- **Workflow scope.** Pushing `.github/workflows/*.yml` needs the `workflows` permission (06 risk 2). Detect it on the first push. The fallback is GitHub MCP `create_or_update_file`, which may have the same limitation.

### G2. Legacy repo cleanup: exact sequence (01 has verdicts per file but no executable sequence)

Do this in the **first commit** of the execution PR, so Lint and Spotless never see legacy files:
1. Remove the submodule:
   ```
   git submodule deinit -f assets/2048 && git rm -f assets/2048 && rm -rf .git/modules/assets/2048 && git rm -f .gitmodules
   ```
   Check: `git submodule status` is empty (06 M0).
2. `git rm`:
   - `build.gradle`, root `AndroidManifest.xml`, `src/`, `.travis.yml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (regenerated at 9.7.1);
   - `res/layout/`, `res/menu/`, `res/values/styles.xml`, `res/values/dimens.xml`, `res/values-sw720dp-land/`.
3. Mine first, then delete:
   - `res/raw/changelog_master.xml` → static About/"Legacy history" data (01 §5.2);
   - `res/drawable/ic_2048.xml` → the 4 glyph paths go into the new adaptive icon (01 §5.3).
4. `git mv res/values*/strings.xml app/src/main/res/values*/strings.xml`. Then:
   - keep `action_settings` renamed to `settings`;
   - delete `press_back_again_to_exit` and `toggle_fullscreen`, which are `UnusedResources` and fatal under 06's Lint config;
   - strip the UTF-8 BOM from `values-cs`;
   - remove the `values-fa` `app_name` (`۲۰۴۸`) and mark `app_name` `translatable="false"`.
5. `git mv metadata fastlane/metadata/android` (C8), then restructure:
   - `en-US/{icon,featureGraphic}.png` → `en-US/images/`;
   - `phoneScreenshots/`, `sevenInchScreenshots/` → `images/…`;
   - delete `promoGraphic.png` (no longer a Play asset type, 01 §6).
6. Keep `LICENSE` (MIT © 2014 Paul Sarbinowski) and add a line such as `Copyright (c) 2026 clayboicardi`. Rewrite `README.md` (drop the Travis and uberspot store badges).
7. Replace `.gitignore` with a modern Android one: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.kotlin/`, `captures/`, `app/build/outputs/roborazzi/`.

### G3. License and attribution obligations (scattered across 01 §8.5, 02 §7.10, 04 §9.1; no consolidated matrix)

| Component | License | Obligation | Where |
|---|---|---|---|
| Original game rules, palette hexes, test vectors (gabrielecirulli/2048) | MIT © 2014 Gabriele Cirulli (`assets/2048/LICENSE.txt`) | Keep the notice when porting the semantics, exact hex values and golden behaviours. Credit the lineage from upstream README:2: "A small clone of 1024 … based on Saming's 2048" | `NOTICE.md` + in-app Licenses |
| Shell, translations, icon glyph paths (uberspot/2048-android) | MIT © 2014 Paul Sarbinowski (`LICENSE`) | Keep the notice (reused strings and paths) | `LICENSE` + Licenses |
| Legacy translators and contributors | covered by MIT | Credit by name in About → "Legacy history": BValeo, urmatej, Mongata, RyuzakiKK, Robin van der Vliet, diogopereira, fraggerfox, gurshabad, a4vi2r, TacoTheDank (from `changelog_master.xml` and git log) | About |
| Google Sans Flex subset | OFL-1.1, no Reserved Font Name | Ship the OFL text with the font. Keep name records (`pyftsubset --name-IDs='*'`). Do not sell the font alone | `app/src/main/res/raw/ofl_google_sans_flex.txt` + Licenses |
| Material Symbols vector XMLs (03 §1.6) | Apache-2.0 | Notice in Licenses | Licenses |
| AndroidX, Kotlin stdlib, kotlinx-* | Apache-2.0 | List them in Licenses. Use a **hand-curated** `res/raw/licenses.json`, **not** `play-services-oss-licenses` (GMS) and not an extra plugin | Licenses |
| (v1.x) HCT from material-color-utilities; Kenney CC0 sounds | Apache-2.0 / CC0 | Keep headers / note the source | when added |

### G4. App icon (adaptive + themed): geometry exists in 01 §5.3, identity undecided

- **Recommendation: a "2048 tile" identity.**
  - Background: solid `#E6A11A` (Classic Refined 2048 fill, 04 §8A).
  - Foreground: the 4 legacy glyph paths (01 §5.3) in `#3A2900`. That is **6.33:1** on `#E6A11A` (04 table). Legacy `#aa8800` on white is 3.37:1, and white on gold is 1.58:1; both fail.
  - Group transform in a 108-unit viewport: `scaleX/Y = 1.411`, `translateX/Y = 16.667`. That gives a 56 × 18.67 dp band inside the 66 dp safe zone.
  - `<monochrome>` = the same paths (themed icons, API 33+).
- **Files:**
  - `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` (adaptive);
  - for API 24–25, a plain `<vector>` fallback under `mipmap-anydpi/` (01 §5.3);
  - `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml`.
- **Store icon `images/icon.png` 512×512.** Render it from the *same* vector inside a Robolectric test: `AdaptiveIconDrawable` → `Bitmap` → PNG. That keeps the store and launcher icons identical (01 D15/D32) with no design tool, and it is deterministic.
- **Feature graphic 1024×500.** Must be a 24-bit PNG with no alpha (01 §6: Play rejects alpha). Render it as a Roborazzi capture of a composable, then strip alpha.

### G5. Splash screen (only 03 §9 mentions `installSplashScreen()`)

- Use `androidx.core:core-splashscreen:1.2.0`: latest stable, lastUpdated 2025-11-05, minSdk 21 (verified).
- Theme `Theme.App.Starting`, parent `Theme.SplashScreen`:
  - `windowSplashScreenBackground` = `#FAF8EF` (light) and, in `values-night`, `#1B1916` (04 §8A);
  - `windowSplashScreenAnimatedIcon` = adaptive foreground;
  - `postSplashScreenTheme` = the app theme.
- Call `installSplashScreen()` before `super.onCreate`.
- **`setKeepOnScreenCondition { !vm.ready }` until the persisted game has loaded from DataStore.** Otherwise the UI first composes a default board and then swaps in the restored one. That replays spawn animations, which is legacy bug B7/B8 reborn.
- Fallback: release the splash after at most 1,000 ms. The corruption handler supplies defaults.
- Test: a Robolectric test that the first composed `BoardUi` equals the persisted fixture, with no `Spawned` kinds.

### G6. Store listing and screenshots (01 §6 has the requirements; generation is missing)

- Path `fastlane/metadata/android/<locale>/` (C8). Files:
  - `title.txt` = `2048`;
  - `short_description.txt` ≤ 80;
  - `full_description.txt` ≤ 4000;
  - `changelogs/30000.txt` ≤ 500;
  - `images/icon.png`;
  - `images/featureGraphic.png`;
  - `images/phoneScreenshots/1..4.png`;
  - `images/sevenInchScreenshots/`;
  - `images/tenInchScreenshots/`.
- **Screenshots from Roborazzi with exact pixel sizes:**
  - phone: `qualifiers = "w360dp-h640dp-xxhdpi"` → **1080×1920**. That meets 01's "≥ 4 at 1080×1920" for recommendation eligibility.
  - ten-inch: `"w1280dp-h720dp-land-xhdpi"` → **2560×1440** (16:9, sw720).
  - seven-inch: `"w1067dp-h600dp-land-xhdpi"` → 2134×1200 (≈16:9, sw600).
- **Locales:** en-US plus the 20 app locales. Map app `pt` to both `pt-BR` and `pt-PT`, which gives 22 directories. **Delete the 16 listing-only locales** (af, am, ar, bg, ca, et, fi, hi, id, ja, lv, ro, sv, sw, th, vi). Their text is machine-translated nonsense ("harbour", "robot", "door", 01 §6), and they would advertise UI languages the app does not ship. Play `supply` language-code mapping (de-DE etc., 01 §6.7) is out of scope; there is no Play upload.
- Copy must say: "new app, new package name; the old `com.uberspot.a2048` can stay installed" (05 §4.20).

### G7. Localization strategy (scope conflict C22; mechanics missing)

- **v1.0 locales:** default en, plus cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, pt, ru, sl, sr, tr, uk (20).
- Seeds:
  - `settings` from the legacy shell (20 locales);
  - 11 game keys from `js/i18n.js` for ru/uk, fixing uk `cancel` "Відміна" → "Скасувати" (02 §6);
  - "full screen" noun seeds (01 §8.1).
- Everything else is agent-translated with a glossary: tile, merge, undo, best, score, board size, move.
- Add a "translations are machine-assisted; corrections welcome" note in README.
- **Plurals:** use `<plurals>` + `pluralStringResource` for every count ("%d moves", "%d games", "%d undos"). ru/uk/pl/cs/lt/sl have 3–4 plural categories. No file mentions plurals.
- **Lint at merge:** `MissingTranslation` = **error** (overrides 06's "informational"); `ExtraTranslation` fatal.
- **Pseudo-locales:** set `buildTypes.debug { isPseudoLocalesEnabled = true }`. Screenshot `en-XA` for expansion and `ar-XB` for bidi without real translations. This DSL property is not smoke-verified on AGP 9.4.1.
- **Digits:**
  - Game numerals (tiles, score, best, stats values) always use Latin digits: `Long.toString()` or `String.format(Locale.ROOT, …)`.
  - Reasons: legacy parity; GSF has no U+06F0–06F9 (verified); visual consistency.
  - Prose strings receive **pre-formatted** `%1$s` arguments, so `fa` does not mix ۲۰۴۸ and 2048 in one screen.
  - My render test showed that a literal "۲۰۴۸" renders via the system font, so this is a choice, not a capability limit.
- `res/resources.properties`: `unqualifiedResLocale=en-US` (03 smoke-verified).

### G8. RTL (01/02/04 cover the rule; verification was missing; now verified)

- `android:supportsRtl="true"` is **required** for Compose to mirror.
  - My Robolectric run with qualifier `fa` rendered an un-mirrored Row without it.
  - With it, it rendered "B RTL-row-A" (mirrored).
  - A `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` sub-tree stayed "LTR-forced-A B".
  - Artifact: `scratchpad/critic/rtl/app/build/outputs/roborazzi/scripts_fa.png`.
- Wrap these in forced LTR: board, gesture detector, D-pad/arrow key mapping, the TalkBack "Move left/right" actions, and any on-screen direction-pad icons (`autoMirrored = false`).
- Chrome (scores, toolbars, settings) mirrors.

### G9. Process death and state restoration (05/06 partial; test mechanics missing)

- No instrumented device is available, so **simulate process death on the JVM**:
  1. Repository instance A writes N moves to a temp DataStore file.
  2. Cancel its scope.
  3. Construct instance B on the same file.
  4. Assert board, score, RNG state, undo history, timer and per-size games are equal.
- **DataStore pitfall:** two active DataStore instances on one file in one process throw `IllegalStateException`. Hold one instance per file in an Application-scoped `AppContainer`, and use unique temp files per test.
- Config change: Robolectric `ActivityScenario.recreate()` must not replay spawn animations or the "+N" float.
- Transient UI (an open sheet or dialog): use `rememberSaveable`/`SavedStateHandle`. Nav3 uses `rememberNavBackStack` with `@Serializable` keys (03 §1.7).
- Durability: DataStore writes atomically (temp file + rename). The worst case on a kill is losing the single in-flight move. Document this.

### G10. Data-model versioning and encoding (05 §7 and 06 §1.2 partial)

- Every persisted document carries `schemaVersion: Int`.
- Decoder: `Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }`. Every new field gets a default.
- Breaking changes go through DataStore `DataMigration<T>` keyed on `schemaVersion`.
- From the first commit that persists, commit fixture files under `app/src/test/resources/fixtures/` and add decode tests (06 §1.2 case 3).
- **Split files** so corruption cannot destroy what matters:
  - `settings` (Preferences DataStore);
  - `records.json` (bests, no-undo bests, stats histograms per mode × size);
  - `game-<N>.json` per board size (board, RNG, move log, snapshots).

  Then a corrupt game file falls back to "new game" without losing the best score. 06's Code Review Rule requires that bests are never lowered.
- **Compact encoding.** kotlinx-serialization encodes `ByteArray` as a JSON number array (~4 chars/byte). Store `cells` and `moveLog` as Base64 strings and snapshots as Base64 blobs. This matters because DataStore rewrites the whole file on every move (06 risk 9).
- **PRNG:**
  - SplitMix64: state is one `Long`; golden vector seed 0 → first output `0xE220A8397B1DCDAF` (standard reference value; confirm in the first test run).
  - Bounded draws by rejection sampling (no modulo bias).
  - Spawn `value = if (nextInt(10) == 0) 4 else 2`, then cell = `nextInt(emptyCount)`, in that fixed order, frozen by golden test.
  - Undo restores the RNG state (05 §4.1 anti-reroll).

### G11. Tablet and foldable (04 §7 covers layouts; testability missing)

- Size-class layouts are testable in Robolectric with qualifiers. Matrix:
  - 360×800, 412×915, 915×412 land, 673×841, 841×673, 800×1280, 1280×800;
  - Roborazzi `RobolectricDeviceQualifiers.SmallPhone/Pixel7/MediumTablet`.
- **Postures** (tabletop/book, 04 §7.2) need `FoldingFeature`. That requires `androidx.window:window-testing` (`WindowLayoutInfoPublisherRule`), which is not in any catalog and not verified under Robolectric. **Defer posture-specific layouts to v1.x** unless an executor proves `window-testing` works on the JVM. Size-class layouts already cover unfolded and folded states.
- Declare `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`. Keyboard and D-pad play (05 §4.16) then lets Chromebooks without touch install. Not covered anywhere.

### G12. Performance budgets (04 §4.6 gives device-only targets; CI-checkable budgets missing)

| Budget | Value | Gate |
|---|---|---|
| Release APK size | **≤ 3.0 MiB**. Baseline: 03 smoke release 1,034,747 B; GSF Latin subset +180 KB (04 §9.1); 21 locales of strings | CI (`stat -c %s`) |
| Persisted active game, 8×8, 10,000 moves | **≤ 256 KiB**; encode+decode ≤ 50 ms on CI JVM | JVM test (stricter than 06's "< 5 MB for 20k moves", since the whole file is rewritten each move) |
| Engine move | 100,000 random 8×8 moves ≤ 2 s on CI JVM (loose) | JVM test |
| Tile recompositions during a slide | **0** (debug-only composition counter in `Tile`) | Robolectric test |
| Cold start TTID, frame CPU P95 ≤ 6 ms, `frameOverrunMs` P99 ≤ 0 (04) | device only | manual / M6, not a merge gate |

### G13. Security (manifest policy): not covered as a gate

- Verified in the smoke release APK (`aapt2 dump xmltree`):
  - `MainActivity` `exported=true` (LAUNCHER);
  - `androidx.profileinstaller.ProfileInstallReceiver` `exported=true` with `android:permission="android.permission.DUMP"`;
  - `androidx.startup.InitializationProvider` `exported=false`.
- **Gate:** the set of `exported=true` components equals exactly {MainActivity, ProfileInstallReceiver}. Add it to `check-apk.sh`.
- No `sharedUserId`, no WebView, no deep links or app links in v1.0. A future `FileProvider` must be `exported=false` + `grantUriPermissions=true`.
- The smoke manifest also merges `androidx.emoji2.text.EmojiCompatInitializer`. The app renders no emoji (only share text contains them), so remove it with `<meta-data android:name="androidx.emoji2.text.EmojiCompatInitializer" tools:node="remove"/>`. That avoids startup work and a font-provider lookup.

### G14. Privacy (missing entirely)

- Play Developer Program Policy (support.google.com/googleplay/android-developer/answer/17517561, dated 2026-09-30): "All apps must post a privacy policy link in the designated field within Play Console, and a privacy policy link or text within the app itself … Apps that do not access any personal and sensitive user data must still submit a privacy policy."
- The Data safety form is required even with no data (answer/10787469).
- Add:
  - repo `PRIVACY.md` ("No data is collected, transmitted or shared. Game data stays on the device; Android's own backup may copy it to your Google account if you enabled device backup.");
  - in-app About → Privacy (string resources, localized);
  - a README link.
- Keep 06's dependency deny-list gate (GMS, Firebase, crash SDKs, OkHttp, Ktor, `ui-text-google-fonts`).

### G15. Backup (06 §4.2 XML is correct; two refinements)

- Include `datastore/` in both `<cloud-backup>` and `<device-transfer>`, as 06 does. With G10's file split, all three files back up.
- Add a Robolectric or Lint check that `@xml/data_extraction_rules` and `@xml/backup_rules` exist and are referenced. Lint validates their syntax.

### G16. Versioning, changelog and "What's new"

- `versionName "3.0.0"`, `versionCode 30000`, both literals (C9).
- `fastlane/metadata/android/en-US/changelogs/30000.txt` (≤ 500 chars).
- No "What's new" UI in v1.0. No earlier versionCode exists for this appId, and 01 §9 #10 says never show it on a fresh install.
- Legacy history goes in About.

### G17. F-Droid (06 §4.3 is good; additions)

- A new appId needs a new `fdroiddata` submission. That is out of scope; note it in README.
- Tag `v3.0.0` on the release commit (F-Droid Quick Start: "if its AndroidManifest contains versionName '1.0', the commit needs a v1.0 tag").
- `dependenciesInfo { includeInApk = false; includeInBundle = false }` (06).

### G18. No CI secrets: release policy (06 §3.5 drafts a release workflow; a decision is needed)

- Without a persistent key, every GitHub release is signed with a different certificate, so users cannot update in place (06 risk 1). The user is hands-off.
- **Recommendation:** do **not** ship `release.yml` in this overhaul, or ship it `workflow_dispatch`-only and producing **unsigned** APKs. CI uploads the debug APK and unsigned release APK as artifacts (06 §3.4).
- Record the signing decision as a follow-up for the user.

### G19. How executor agents verify UI with no device (06 covers Roborazzi; these operational facts were missing; all verified)

1. **Record mode is opt-in.** `:app:testDebugUnitTest` alone wrote **no PNG**. `-Proborazzi.test.record=true` (or `recordRoborazziDebug`) wrote `build/outputs/roborazzi/*.png`. Verify mode is `-Proborazzi.test.verify=true`.
2. **Agents can and must look at the PNGs.** The Read tool renders them; I inspected `scripts_fa.png`. Plan rule: after every UI change, the agent Reads each changed golden and lists them in the PR body.
3. **Robolectric jar fetch bypasses Gradle.** In this sandbox, `repo1.maven.org` throttles. Wire an *optional* Gradle property:
   ```kotlin
   providers.gradleProperty("robolectricRepoUrl").orNull?.let { url ->
       it.systemProperty("robolectric.dependency.repo.url", url)
   }
   ```
   `scripts/verify.sh` passes the GCS mirror only in the sandbox; CI uses the default. Each android-all jar is about 213–239 MB; `~/.m2/repository/org/robolectric` was 228 MB with one SDK.
4. Motion cannot be checked from a static golden. Build a **filmstrip**: set `mainClock.autoAdvance = false`, then capture at t = 0, 40, 80, 160, 400 ms for one scripted move, and Read the strip.
5. Sandbox and CI match: Ubuntu 24.04.5, glibc 2.39, 4 CPU, 15 GB, the same as `ubuntu-24.04` runners (06 §0). That supports 06's exact-match threshold. Equality is still unproven until the first CI run (§3 U6).

### G20. Codex AGENTS.md (06 §5 is right; a scope note)

- The heading **`## Code Review Rules`** is current. Verified on developers.openai.com/codex/cloud/code-review and learn.chatgpt.com/docs/third-party/github: "Add a `## Code Review Rules` section to the file closest to the code the rules govern. Use `###` headings". The cached developers.openai.com/codex/integrations/github excerpt still shows the older "**Review guidelines**" wording.
- Use only the new heading; no duplicates.
- The docs advise "Start with two or three concise rules". 06's draft has 6 groups and about 15 bullets. Put Offline/permissions, Saved data and Verification integrity in root `AGENTS.md`, and Game rules in `core/engine/AGENTS.md`. Keep Input/A11y only if Codex output stays focused.

### G21. One execution PR vs milestone gates (06 §6 assumes per-milestone G-codex)

- The user mandates **one** execution PR. Implement M0–M5 as ordered commits on one branch.
- Run `./scripts/verify.sh` locally at each milestone.
- Post `@codex review` at two checkpoints:
  1. after M2 (engine + persistence: the irreversible parts);
  2. at the end.

  Each review covers the full diff.
- Merge when CI is green **and** the Codex clean signal (06 §5.1) applies to the head SHA.

### G22. App architecture: DI, navigation, state (only 03 §1.7 touches navigation)

- Manual DI: an `AppContainer` created in `Application` holds the singleton DataStores, repositories and clock. No Hilt, KSP or kapt.
- One `GameViewModel` exposes an `@Immutable` `StateFlow<GameUiState>`. Moves are applied synchronously in the ViewModel (state-first, C12). Persistence is a **conflated** writer: latest state wins, writes run on `Dispatchers.IO`, and the flush happens in `onStop`.
- Nav3 1.2.0 keys: `Game`, `Settings`, `Stats`, `HowToPlay`, `About`/`Licenses`. New-game size picker and win/over cards are sheets or overlays, not destinations.

### G23. Keyboard map (02/05/04 differ slightly)

| Action | Keys |
|---|---|
| Move | Arrows, WASD, HJKL, `DPAD_*` |
| Undo | U, Ctrl+Z, Backspace |
| New game (confirm if a game is in progress) | N, R |

Legacy R restarted with no confirmation (02 B12), so R now prompts. Key auto-repeat is limited to 1 move per 120 ms (04).

---

## 3. Unverified claims that matter: my verdicts

| # | Claim (file) | Verdict | Basis |
|---|---|---|---|
| U1 | Codex signals "clean" with 👍 / "Didn't find any major issues" (06 §5.1, community) | **Plausible; not in the official docs.** The user names 👍 too. Keep 06's fallback | Official docs (verified): `@codex review` → 👀 → review; "flags only P0 and P1"; automatic reviews via Review trigger |
| U2 | 200 dp gesture-exclusion cap (04 §7.4 secondary source; 05 S34) | **Verified** | AOSP `WindowManagerConstants`: `MIN_GESTURE_EXCLUSION_LIMIT_DP = 200`. androidx `SystemGestureExclusionHelper`: "limited to 200dp total per side … honored starting from the bottom". The `View` javadoc (mirrored on learn.microsoft.com) says "the system will put a limit of 200dp on the vertical extent of the exclusions" |
| U3 | "When the navigation bar is stickily hidden, the 200dp cap does not apply" (04 §7.4) | **Unverified; don't rely on it** | AOSP only shows `KEY_SYSTEM_GESTURES_EXCLUDED_BY_PRE_Q_STICKY_IMMERSIVE` (pre-Q apps). The gesture-nav doc says immersive mode "disables the system gestures … lets the user re-enable … by swiping from the bottom", which concerns the home gesture. Not needed if the board is inset (C19) |
| U4 | `offset{}` votes `FrameRateCategory.High` on ARR, while `graphicsLayer` translation does not (04 §4.2) | **Plausible from source reading; device-only.** Adopt `offset{}` anyway (no downside) | Cannot be measured without an Android 15+ ARR device |
| U5 | Google Sans Flex lacks Cyrillic and Greek (04 §9.1) | **Verified and extended**: Esperanto ĉĝĥĵŝ also missing | fontTools (C16) |
| U6 | Robolectric renders non-Latin scripts and RTL, so screenshot goldens are meaningful for fa/ru/el/sr/ko (implicit in 06's matrix) | **Verified** at SDK 36 with `@GraphicsMode(NATIVE)`. Requires `supportsRtl="true"` | My test `critic/rtl/.../ScriptRenderTest.kt` → `scripts_fa.png` |
| U7 | Local and CI screenshots are bit-identical (06 risk 6) | **Unverified until the first CI run.** Environments match: Ubuntu 24.04.5 / glibc 2.39 / 4 CPU. Fallback: commit CI-produced images from the reports artifact | `/etc/os-release`, `ldd --version` |
| U8 | Pushing workflow files needs `workflows` scope (06 risk 2) | **Unverified; detect on the first push** (G1) | — |
| U9 | Pinned action SHAs (06 §3.1) | **Verified** all 5 | `git ls-remote`: checkout v7.0.1 `3d3c42e5aac5…`, setup-java v6.1.0 `b843ebf9b913…`, gradle/actions v6.4.0 `3f5f9adaf7d9…`, cache v6.1.0 `55cc8345863c…`, upload-artifact v7.0.2 `cf430e030ddb…` |
| U10 | AGP auto-installs build-tools 36.0.0 (03 §1.2) | **Verified** | `/opt/android-sdk/build-tools` now contains 36.0.0, 36.1.0 and 37.0.0 |
| U11 | Kover 0.9.11 `bound { minValue; coverageUnits }` field names (06 §1.1) | **Unverified.** Confirm at first use; `minBound(Int)` is documented | — |
| U12 | `ubuntu-latest` → 26.04 in Nov 2026 (06) | **Unverified**, but pinning `ubuntu-24.04` is harmless either way | — |
| U13 | `isPseudoLocalesEnabled` on AGP 9.4.1 (my G7 suggestion) | **Unverified.** Long-standing DSL; check at first use | — |
| U14 | SplitMix64(seed 0) first output `0xE220A8397B1DCDAF` (my G10) | **Widely published reference value**, not executed here. The first engine test must assert it | — |
| U15 | M3 1.5.0 is not stable yet (03/04) | **Verified**: material3 latest = 1.5.0-beta02 (lastUpdated 2026-10-21); BOM latest 2026.09.00 → material3 1.4.0 | `maven-metadata.xml` |
| U16 | Glance pulls WorkManager permissions (05) | Not re-verified; irrelevant to v1.0 (no widget) | — |
| U17 | Workflows on forks / the "auto-enable by adding a workflow file" behaviour | **The first half is verified** (GitHub docs). The auto-enable is community-reported only | G1 |

All other version facts were spot-checked live: AGP stable 9.4.1; Kotlin 2.4.21; Compose BOM 2026.09.00; DataStore 1.3.0; Nav3 1.2.0; activity-compose 1.13.0; adaptive 1.3.0; Spotless 8.10.4; Robolectric 4.17; Roborazzi 1.77.0; detekt 2.0.0-alpha.6; kotlinx-serialization 1.12.0. They match 03 and 06 exactly.

---

## 4. Top 15 decisions the plan must make (ranked by blast radius and irreversibility), with recommended answers

**D1. Toolchain baseline.**
- Gradle wrapper **9.7.1** (bin, sha256 `acd53f1e…d20a`, `validateDistributionUrl=true`).
- AGP **9.4.1**, Kotlin/Compose-compiler/serialization **2.4.21**, BOM **2026.09.00**.
- **compileSdk 37 / targetSdk 37 / minSdk 24**, JDK 21 (bytecode 17).
- No buildToolsVersion pin; AGP's default is 36.0.0, which is installed.
- Bootstrap the wrapper with `/opt/gradle-8.14.3` in an empty directory.

Evidence: C1–C3 (AGP 9.4 needs Gradle 9.6; Compose 1.12.1 needs AGP 9.1 and compileSdk 37; DataStore 1.3.0 needs minSdk 24).

**D2. Repo, PR and CI mechanics on a fork.**
- Merge target `master`.
- Create every PR with an explicit `-R clayboicardi/2048-android-modern-refresh --base master`.
- After the first push, verify Actions runs exist and Codex reacts 👀. Fall back as in G1.
- One execution PR with Codex checkpoints after M2 and at the end (G21).
- Clean signal per 06 §5.1 on the head SHA.

Evidence: C10, G1 (`isFork:true`; GitHub docs on fork workflows).

**D3. v1.0 scope.**
- Ship 05 roadmap #1–#19:
  - parity rules;
  - app-owned PRNG;
  - move log;
  - sizes **3×3–8×8** (3×3 target 256);
  - unlimited undo, with no prompt, available on game over, with a counter and an Off switch;
  - robust save;
  - input model;
  - settings;
  - themes;
  - haptics;
  - stats + local top-10;
  - win/over cards with text share;
  - onboarding + How to play;
  - accessibility;
  - adaptive layouts;
  - backup rules;
  - locales;
  - CI permission gate;
  - About/Licenses/Privacy.
- Defer to v1.x: daily challenge, history/replay, sounds, achievements, hints, widget, shortcuts, export/import, redo, Material You tile ramp, posture layouts.

Evidence: C23; 05 §3 survey; G11.

**D4. Persisted data model and determinism (irreversible once shipped).**
- Engine state uses exponent bytes.
- SplitMix64 RNG with golden vectors and unbiased bounded draws; undo restores the RNG.
- Move log + snapshot every 32 moves.
- `schemaVersion` + `ignoreUnknownKeys` + fixtures.
- **Three files** (settings / records / game-per-size) with compact Base64 encoding.
- Conflated writer.
- Budget: ≤ 256 KiB at 10k moves.

Evidence: 05 §7, 06 §1.2 and risk 9, G10, G12.

**D5. Architecture.**
- `:app` + `:core:engine` (pure JVM, no Android imports; grep test).
- Manual DI `AppContainer`.
- Single-activity Compose, Nav3 1.2.0 with 5 keys.
- State-first ViewModel; `@Immutable` UI models with stable tile IDs (04 §4.1).
- Splash gated on the first load (G5).

Evidence: 03 §8, 04 §4, G22.

**D6. Input model.**
- One move per gesture, applied synchronously; animations snap; **no queue** (C12).
- Default commit **on release** using the full displacement vector:
  - threshold 24 dp (Medium; Low 40 dp, High 12 dp);
  - dominant axis with `|major| ≥ 1.4·|minor|`, else ignore;
  - fling ≥ 600 dp/s is accepted.
- Setting "Move on: Release / As soon as detected" (05) for speed players.
- Board inset from the gesture insets; exclusion only bottom ≤ 200 dp on edge-hugging layouts.
- Keyboard map per G23.

Evidence: C11, C19; legacy commit-on-lift; review evidence in 05 §9.

**D7. Material 3 version.**
- Pin **material3 1.5.0-beta02** over BOM 1.4.0, for `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `ButtonGroup` and emphasized type. These are public and non-experimental in beta02 (03 and 04 verified sources).
- **Ban `@ExperimentalMaterial3ExpressiveApi`** (MaterialShapes, LoadingIndicator) and use `androidx.graphics:graphics-shapes:1.1.0` (stable) for morphs.
- Own `GameMotion` tokens, so a fallback to 1.4.0 changes only theme wiring.
- Bump to 1.5.0 stable when it ships (not out as of 2026-10-21).

Evidence: U15, 03 §1.5, 04 §0.1.

**D8. Visual direction and theming.**
- Default **Classic Refined** with light and dark hex tables (04 §8A). It passes ≥ 3.66:1 on every tile, and adjacent CVD ΔE ≥ 2.7 (deutan, 32→64).
- Second palette **Neon Arcade** (04 §8C).
- Theme System/Light/Dark.
- Dynamic colour **chrome-only** toggle on API 31+, default Off.
- High contrast:
  - automatic when `UiModeManager.getContrast() ≥ 0.5` (API 34+);
  - manual setting: black/white ink + 1.5 dp outline (04 §3.4).
- Distinct super-tile colours above 2048.
- Material You tile ramp deferred.

Evidence: C15, 04 §8.

**D9. Motion, reduced motion and haptics settings.**
- Adopt 04 §10 verbatim:
  - slide = std fastSpatial 0.9/1400;
  - spawn and pop = expr fastSpatial 0.6/800;
  - overlays = expr defaultSpatial 0.8/380;
  - game-over interactive at T0 + 700 ms; win card interactive at T0 + 800 ms.
- One setting **Motion: Full / Reduced / Off**. System animator scale 0 ⇒ Off. Reduced uses 04 §6.2 substitutions. No speed multiplier.
- Haptics: **On/Off** (default On).
  - `SegmentTick` once per merging move;
  - `Confirm` when max merge ≥ 512;
  - win = `Confirm` + `Confirm` 150 ms later;
  - new best = `GestureThresholdActivate`;
  - `Reject` for invalid moves (≤ 1 per 400 ms) and game over;
  - `ToggleOn/Off` in settings.
- No `VIBRATE`.

Evidence: C13, C14.

**D10. Typography.**
- **Google Sans Flex** (OFL-1.1, no RFN; verified):
  - Latin subset instanced `wght 400–900`, `wdth 75–100`, `ROND 0–100` (≈ 180 KB, 04 §9.1);
  - only for tile numerals, score/best values (`tnum`) and the "2048" title.
- All translatable text uses `FontFamily.Default`, because GSF has no Cyrillic, Greek or Esperanto ĉĝĥĵŝ and no Persian digits (verified).
- Ship the OFL text.

Evidence: C16, U5.

**D11. Localization and RTL.**
- en + 20 legacy locales, fully translated before merge; `MissingTranslation` = error.
- Plurals everywhere counts appear.
- `generateLocaleConfig` + `unqualifiedResLocale=en-US`; system per-app language link on API 33+ (no AppCompat).
- `supportsRtl="true"` with the board, gestures and direction actions forced LTR.
- Latin digits for game numerals; pre-formatted `%1$s` args.
- `app_name` non-translatable "2048".
- Store listing locales match the app locales (22 dirs); delete the 16 listing-only locales.

Evidence: C22, C24, G6–G8.

**D12. Device-free verification strategy.**
- Robolectric 4.17 + Compose ui-test **v2** + Roborazzi 1.77.0 at `sdk=36` (plus a launch smoke at `[24, 37]`), `@GraphicsMode(NATIVE)`.
- Matrix: 06's list plus `fa` instead of `ar`, `en-XA`/`ar-XB` pseudo-locales, and 3 store-size captures (G6).
- Record locally with `-Proborazzi.test.record=true`; **agent must Read every changed PNG**.
- Filmstrip captures for motion.
- ATF `CheckLevel.Error`.
- JVM process-death simulation (G9).
- Robolectric repo-URL property for the sandbox (G19).
- Required config: espresso-core 3.7.0 forced and the `--add-opens` list (03 §6).

**D13. CI gate composition.**
- Single job, `ubuntu-24.04`, SHA-pinned actions (all verified), setup-gradle `cache-provider: basic`.
- Gates:
  - `spotlessCheck` (ktfmt **0.65**);
  - detekt 2.0.0-alpha.6 with 06's exit criterion;
  - Kotlin `allWarningsAsErrors` (forces `currentWindowAdaptiveInfoV2` and ui-test v2);
  - Lint `warningsAsErrors` + Slack compose-lint-checks 1.6.1;
  - Kover ≥ 90 % line / ≥ 85 % branch on the engine;
  - test-count floor;
  - `assembleRelease` with `optimization { enable = true }`;
  - APK gates: permission allowlist, **exported-component allowlist**, size ≤ 3.0 MiB, `zipalign -c -P 16`, dependency deny-list.

Evidence: C4–C6, C20, G12, G13.

**D14. Identity assets.**
- Adaptive + monochrome icon in the "2048 tile" style:
  - background `#E6A11A`, glyphs `#3A2900` (6.33:1);
  - legacy glyph paths at scale 1.411 / translate 16.667;
  - vector fallback for API 24–25.
- Store icon and feature graphic rendered from code in Robolectric (no alpha on the feature graphic).
- Splash via core-splashscreen 1.2.0, gated on state load.
- `fastlane/metadata/android/` restructure with `title.txt`, `images/`, `changelogs/30000.txt`, and 1080×1920 phone shots.

Evidence: G4–G6.

**D15. Release, privacy, backup and manifest policy.**
- `versionName 3.0.0` / `versionCode 30000`, both literals; tag `v3.0.0` only when the user decides on signing.
- **No signed release workflow** in this overhaul; CI produces artifacts only.
- `dependenciesInfo` excluded.
- `PRIVACY.md` + in-app privacy text (Play policy).
- Backup:
  - `allowBackup="true"`;
  - `dataExtractionRules` (API 31+) and `fullBackupContent` (API 24–30), both including `datastore/`.
- Manifest:
  - `appCategory="game"` (with `tools:targetApi="26"`);
  - `touchscreen required="false"`;
  - `EmojiCompatInitializer` removed;
  - no `screenOrientation`, `configChanges` or `enableOnBackInvokedCallback` (default true at target 36+);
  - exported components = {MainActivity, ProfileInstallReceiver}.

Evidence: C9, G13–G18.

---

## 5. Evidence log (commands and URLs used by this critique)

- `git branch -a`; `git ls-remote --symref origin HEAD` → `refs/heads/master`; `ls metadata | wc -l` → 29; `git submodule status` → `7a477eb… assets/2048`.
- Google Maven `maven-metadata.xml`:
  - AGP: latest stable 9.4.1; pre 9.5.0-rc02, 9.6.0-alpha01; lastUpdated 20261026;
  - material3: 1.5.0-beta02 (20261021);
  - compose-bom: 2026.09.00;
  - datastore: 1.3.0 (20261021);
  - navigation3-runtime: 1.2.0 stable, 1.3.0-alpha03;
  - core-splashscreen: 1.2.0 (20251105);
  - adaptive: 1.3.0 stable, 1.4.0-alpha03;
  - activity-compose: 1.13.0 stable.
- Google Maven Central mirror metadata: kotlin-gradle-plugin 2.4.21 (pre 2.5.0-Beta2); spotless-lib 4.10.4; spotless-plugin-gradle 8.10.4; robolectric 4.17; roborazzi 1.77.0; detekt-gradle-plugin 2.0.0-alpha.6; `org.jetbrains.kotlinx:ktfmt` 0.65; `com.facebook:ktfmt` 0.64; kotlinx-serialization-json 1.12.0.
- `services.gradle.org/versions/current` → 9.8.2.
- AAR manifests and aar-metadata (downloaded to `scratchpad/critic/aars/`): see C2/C3.
- `adaptive-android-1.3.0-sources.jar` (`scratchpad/critic/adaptive/`): see C4.
- `spotless-lib-4.10.4.jar` `KtfmtStep.class`: see C5.
- fontTools coverage script `scratchpad/critic/scripts/cov.py` on `scratchpad/fonts_dl/GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf`: see C16.
- Robolectric render test: `scratchpad/critic/rtl/app/src/test/kotlin/com/clayboicardi/game2048/ScriptRenderTest.kt` → `scratchpad/critic/rtl/app/build/outputs/roborazzi/scripts_fa.png`, with and without `supportsRtl`. The first run failed on the android-all fetch; this was fixed with `robolectric.dependency.repo.url`.
- `aapt2 dump xmltree --file AndroidManifest.xml scratchpad/smoke/app/build/outputs/apk/release/app-release.apk`: see G13.
- `git ls-remote` for the 5 action tags: see U9.
- `https://github.com/clayboicardi/2048-android-modern-refresh` HTML: `"isFork":true`.
- Docs:
  - https://developer.android.com/build/releases/gradle-plugin
  - https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
  - https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored
  - https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav
  - https://developers.openai.com/codex/cloud/code-review
  - https://learn.chatgpt.com/docs/third-party/github
  - https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows
  - https://docs.github.com/actions/using-workflows/disabling-and-enabling-a-workflow
  - https://github.com/orgs/community/discussions/53510
  - https://support.google.com/googleplay/android-developer/answer/17517561
  - https://support.google.com/googleplay/android-developer/answer/10787469
  - https://github.com/googlefonts/googlesans-flex
  - https://fonts.google.com/faq
  - https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-mainline-release/services/core/java/com/android/server/wm/WindowManagerConstants.java
  - https://android.googlesource.com/platform/frameworks/support/+/2cba1950335165bd8e8250f15d62ab0a08ed05d9/pdf/pdf-viewer/src/main/java/androidx/pdf/util/SystemGestureExclusionHelper.java
  - https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots (via search index)
  - https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/ (via search index)
- Not reachable from this sandbox: `developers.openai.com` and `f-droid.org` (DNS), and `api.github.com` for unattached repos. Content was taken from the search index's cached excerpts instead.
