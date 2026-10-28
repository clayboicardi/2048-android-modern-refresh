# 03 — Toolchain research (as of 2026-10-28)

Track: toolchain. Scope: the current stable Android toolchain for a brand-new, single-app Kotlin + Jetpack Compose + Material 3 project
(`com.clayboicardi.game2048`, fully offline, zero runtime permissions), verified against primary metadata and **smoke-built locally**.

> Method. Every version below was read from the artifact repository's own `maven-metadata.xml` (Google Maven `dl.google.com/android/maven2`,
> Maven Central `repo1.maven.org` / its Google mirror `maven-central.storage-download.googleapis.com`, Gradle Plugin Portal `plugins.gradle.org/m2`)
> or from `services.gradle.org/versions/*`. Behavior claims were checked against (a) AAR manifests / `aar-metadata.properties` / sources jars
> downloaded from Google Maven, and (b) a throw-away smoke project built with the proposed catalog
> (`<sandbox-scratchpad>/smoke`). Docs URLs are cited inline.

---

## 0. TL;DR — the pick

| Item | Pick | Why (short) |
|---|---|---|
| Gradle wrapper | **9.7.1** (latest GA is 9.8.2 — also verified) | AGP 9.4 needs >= 9.6.0; KGP 2.4.21 "fully supports" Gradle <= 9.7.0 (9.7.1 = patch). Smoke builds: 9.7.1 = **zero deprecation warnings**; 9.8.2 = one AGP-originated deprecation (`Configuration.setVisible`). Both pass + reuse the configuration cache. See §2/§6.1. |
| AGP | **9.4.1** | Latest stable (9.4.1 POM dated 2026-09-18). Supports API 37. Built-in Kotlin. |
| Kotlin (KGP, Compose compiler plugin, serialization plugin) | **2.4.21** | Latest stable (2026-10-08). Declaring it in the root `plugins {}` upgrades AGP's bundled KGP 2.2.10 -> 2.4.21 (verified with `buildEnvironment`). |
| Compose BOM | **2026.09.00** | Latest BOM. Maps Compose UI/foundation/runtime/animation **1.12.1**, material3 **1.4.0**, adaptive **1.3.0**. |
| Material 3 | **override to 1.5.0-beta02** | 1.4.0 (BOM) has *no* Expressive components and keeps `MotionScheme.expressive()` / `MaterialExpressiveTheme` `internal`. 1.5.0-beta02 makes them public; depends only on Compose 1.12.0 (compatible with BOM). Verified compile + render. |
| compileSdk | **37** (= android-37.0) | Compose 1.12.x AARs declare `minCompileSdk=37`, `minCompileMinorSdk=0`, `minAndroidGradlePluginVersion=9.1.0`. |
| targetSdk | **37** | Android 17 is final (released 2026-06-16). Play floor today is 36. |
| minSdk | **24** | Floor imposed by Navigation 3 1.2.0, DataStore 1.3.0, Navigation 2.10.2, Material3 1.5.0-beta02 (all `minSdkVersion="24"` in their AARs). Compose 1.12.1 / core 1.19.1 are at 23. |
| JDK | **21** (local + CI); bytecode target **17** | AGP 9.4 min JDK 17; Robolectric SDK 36/37 needs JDK 21. |
| Modules | `:app` (Android, Compose) + `:core:engine` (pure Kotlin/JVM) | §(h). |
| Unit/UI tests | JUnit 6.1.3 (engine), JUnit 4.13.2 + Robolectric 4.17 + Compose ui-test **v2** + Roborazzi 1.77.0 (app), kotest-property 6.2.6, Turbine 1.2.1, Truth 1.4.5 | Verified: engine tests + Robolectric@SDK37 screenshot test pass. |
| Format/lint | Spotless 8.10.4 + ktfmt 0.64 (or ktlint 1.8.0), Android Lint + Slack compose-lint-checks 1.6.1; detekt only as 2.0.0-alpha.6 (1.23.8 is incompatible with Kotlin 2.4 / AGP 9) | §1.10 (all three smoke-verified). |

**Must-know gotchas (all hit or verified in the smoke builds):**
1. Do **not** apply `org.jetbrains.kotlin.android` (AGP 9 built-in Kotlin). Declaring `kotlin-compose`/`kotlin-serialization`/`kotlin-jvm` 2.4.21 in the root `plugins {}` lifts AGP's KGP 2.2.10 -> 2.4.21.
2. `compileSdk = 37` resolves to `platforms;android-37.0`; Compose 1.12 *requires* it (AAR metadata `minCompileSdk=37`, min AGP 9.1.0).
3. Robolectric 4.17 on JDK 21 needs the `--add-opens` jvmArgs list, and **`espresso-core` must be forced to 3.7.0** (ui-test-junit4 1.12.1 drags 3.5.1, which crashes on SDK 37: `NoSuchMethodException: InputManager.getInstance()`).
4. Compose tests: use `androidx.compose.ui.test.junit4.v2.createComposeRule` (v1 is deprecated, StandardTestDispatcher semantics).
5. JUnit Jupiter silently skips expression-bodied tests that return non-Unit (`= runBlocking { checkAll ... }`).
6. APK is **not** native-free (DataStore + graphics-path `.so`), but is 16 KB-aligned (verified `zipalign -c -P 16`).
7. Merged manifest's only `uses-permission` is the signature-level `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` from androidx.core — no INTERNET.
8. AGP 9.4.1 default build-tools 36.0.0 is not preinstalled locally (AGP auto-installs it; or pin `buildToolsVersion = "37.0.0"`).
9. Sandbox only: Maven Central returns intermittent HTTP 429 — use the GCS mirror init script (§6).

### Legacy toolchain inventory (what gets replaced)

| File:line | Legacy | Disposition |
|---|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | Gradle **4.10.3** `-all` | replace with 9.7.1 `-bin` + sha256 |
| `build.gradle:1-11` | `buildscript {}` with `jcenter()` and `oss.sonatype.org/content/repositories/snapshots/`; AGP **3.2.1** | delete; plugins via catalog |
| `build.gradle:13-19` | `allprojects { repositories {...} }` | delete; `dependencyResolutionManagement` + `FAIL_ON_PROJECT_REPOS` |
| `build.gradle:24-25` | `compileSdkVersion 28`, `buildToolsVersion '28.0.3'` | `compileSdk = 37`; no buildToolsVersion (or 37.0.0) |
| `build.gradle:28-32` | `applicationId "com.uberspot.a2048"`, `minSdkVersion 14`, `targetSdkVersion 28`, `versionCode 25`, `versionName "2.2"` | `com.clayboicardi.game2048`, min 24, target 37; new versionCode line |
| `build.gradle:35-45` | custom `sourceSets` (`src`, `res`, `assets` at repo root; manifest at root) | standard `app/src/main/{kotlin,res}` layout |
| `build.gradle:49-50` | `minifyEnabled false`, `proguard-android.txt` (disallowed in AGP 9) | R8 on, `proguard-android-optimize.txt`, `isShrinkResources = true` |
| `build.gradle:54-56` | `lintOptions { disable 'GoogleAppIndexingWarning' }` | `lint {}` block |
| `build.gradle:58-62` | Java 1.8 | Java 17 |
| `build.gradle:66` | `de.cketti.library.changelog:ckchangelog-legacy-dialog:2.0.0-SNAPSHOT` (Sonatype snapshots; OSSRH is gone) | drop; in-app "What's new" in Compose if wanted |
| `AndroidManifest.xml:3` | `package="com.uberspot.a2048"` | `namespace` in build file (manifest `package` is an error in AGP 8+) |
| `AndroidManifest.xml:6` | `allowBackup="true"` | keep + `dataExtractionRules`/`fullBackupContent` for DataStore files |
| `AndroidManifest.xml:12` | `configChanges="orientation|keyboardHidden|screenSize"` | drop (Compose + ViewModel); Android 17 already avoids recreation for keyboard/navigation changes |
| `AndroidManifest.xml:14` | `screenOrientation="portrait"` | ignored on sw600dp at targetSdk 37 unless `android:appCategory="game"`; build adaptive layout regardless |
| `.travis.yml:1-23` | Travis, `oraclejdk8`, `build-tools-28.0.3`, `android-28` | GitHub Actions (JDK 21) |
| `.gitmodules` | `assets/2048` -> `github.com/uberspot/2048.git` | remove submodule (WebView dropped) |
| `src/com/uberspot/a2048/MainActivity.java` (177 lines) | WebView host | replaced by Compose `MainActivity.kt` |
| `res/values-*` (20 locale dirs: cs da de el eo es fa fr hu it ko lt nl pl pt ru sl sr tr uk, plus `values-sw720dp-land`) | strings | port to `app/src/main/res/values-*`; `generateLocaleConfig = true` |

---

## 1. Verified versions (primary metadata)

Legend: **Stable** = latest non-alpha/beta/rc in `maven-metadata.xml`. "Pre" = latest pre-release when relevant.

### 1.1 Gradle

| What | Value | Source |
|---|---|---|
| Current GA | **9.8.2** (buildTime 2026-10-19T16:29:26Z, `"current": true`) | `https://services.gradle.org/versions/current` |
| distributionUrl | `https://services.gradle.org/distributions/gradle-9.8.2-bin.zip` | same |
| distributionSha256Sum | `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` | same |
| wrapper jar sha256 | `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98` | same |
| Recent GA list | 9.8.2 (10-19), 9.8.1 (10-07), 9.8.0 (09-24), 9.7.1 (08-19), 9.7.0 (08-06), 9.6.1 (06-26), 9.6.0 (06-18); 8.14.6 (10-08) is the 8.x LTS line | `https://services.gradle.org/versions/all` |
| Pre | 9.9.0-milestone-2 | same |
| 9.7.1 sha256 (fallback) | `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` | same |

Gradle 9.8.2 compatibility (https://docs.gradle.org/current/userguide/compatibility.html, fetched): runs on JVM **17–27**; toolchains up to 27;
"Gradle is tested with Kotlin 2.0.0 through 2.4.20-RC2"; "tested with Android Gradle Plugin 9.0 through 9.5.0-alpha02"; embedded Kotlin 2.4.10 (min Gradle 9.8.0).
9.8.2 release notes: Java 27 support; fixes for toolchain detection on Debian/Ubuntu-packaged JDKs (relevant: our JDK is Ubuntu's `openjdk 21.0.12.1`) (https://docs.gradle.org/9.8.2/release-notes.html).

Local note: `/opt/gradle-8.14.3` **cannot** build AGP 9.4 (min Gradle 9.6.0). Use the wrapper; it downloads from `services.gradle.org` through the proxy (verified). Note `downloads.gradle.org` is **blocked** by the sandbox proxy (the `*.sha256` sidecar URLs 301-redirect there) — take checksums from `services.gradle.org/versions/all` instead.

### 1.2 Android Gradle Plugin — `com.android.tools.build:gradle`

| | |
|---|---|
| Stable | **9.4.1** (9.4.x line: 9.4.0, 9.4.1; 9.3.x: up to 9.3.3) |
| Pre | 9.5.0-rc02, 9.6.0-alpha01 |
| Metadata | `https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml` (lastUpdated 2026-10-26) |
| Runtime deps | `kotlin-gradle-plugin` **2.2.10** + `kotlin-stdlib` 2.2.10 (from `gradle-9.4.1.pom` / `.module`; JVM attribute 17) |
| Lint | `com.android.tools.lint:*` **32.4.1** (= AGP + 23) |
| aapt2 | `9.4.1-15978811` |

AGP 9.4 compatibility (https://developer.android.com/build/releases/gradle-plugin, "AGP 9.4.0, September 2026"):
min/default Gradle **9.6.0**; min/default SDK Build Tools **36.0.0**; NDK default 28.2.13676358; min/default JDK **17**; "API level 37 supported by AGP 9.4.0".

AGP <-> Gradle minimums (https://developer.android.com/build/releases/about-agp): 9.4 -> 9.6.0, 9.3 -> 9.5.0, 9.2 -> 9.4.1, 9.1 -> 9.3.1, 9.0 -> 9.1.0.
API-level minimums (same page): **API 37.0 -> AGP 9.1.1** (Android Studio Panda 3 | 2025.3.3 Patch 1); API 36.1 -> AGP 8.13.0.
Android Studio support for AGP 9.4: **Quail 4 | 2026.1.4** and **Rabbit 1 | 2026.2.1**.

#### What changed in AGP 9.x (https://developer.android.com/build/releases/agp-9-0-0-release-notes, https://developer.android.com/build/migrate-to-built-in-kotlin)

1. **Built-in Kotlin** (`android.builtInKotlin` default `false -> true`). Do **not** apply `org.jetbrains.kotlin.android` — it now fails with
   "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0" / "Cannot add extension with name 'kotlin'".
   Remove it from root, module and `libs.versions.toml`. `kotlin-kapt` is incompatible (use KSP, or `com.android.legacy-kapt`).
   `android.kotlinOptions {}` is gone -> use `kotlin { compilerOptions { ... } }`; `jvmTarget` defaults to `android.compileOptions.targetCompatibility`.
   Kotlin sources: `src/main/kotlin` (or `src/main/java`); extra dirs via `android.sourceSets.named("main") { kotlin.directories += ... }`.
   Per-module disable: `android { enableKotlin = false }`. Opt-out (`android.builtInKotlin=false` + `android.newDsl=false`) disappears in AGP 10.
2. **KGP version selection**: AGP depends on KGP 2.2.10 at runtime; lower versions are auto-upgraded to 2.2.10. A higher KGP wins by normal
   Gradle conflict resolution when any Kotlin plugin is on the root build classpath. **Verified** in the smoke project:
   `./gradlew buildEnvironment` shows `com.android.tools.build:gradle:9.4.1 -> org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.21`
   (pulled up by `org.jetbrains.kotlin.plugin.compose` / `kotlin.jvm` / `plugin.serialization` 2.4.21 declared `apply false` in the root, all of which
   import `kotlin-gradle-plugins-bom:2.4.21`). So: **declare `kotlin-compose`, `kotlin-serialization`, `kotlin-jvm` at 2.4.21 in the root `plugins {}`; no buildscript classpath hack needed.**
3. **New DSL only** (`android.newDsl` `false -> true`): `BaseExtension`, `applicationVariants`, `variantFilter`, `dexOptions`, etc. are removed/hidden;
   use `androidComponents { beforeVariants/onVariants }`. `CommonExtension` lost its type parameters.
4. **Default property flips** (all `false -> true` unless noted): `android.uniquePackageNames`, `android.useAndroidx`,
   `android.default.androidx.test.runner`, `android.enableAppCompileTimeRClass`, `android.sdk.defaultTargetSdkToCompileSdkIfUnset`,
   `android.onlyEnableUnitTestForTheTestedBuildType`, `android.proguard.failOnMissingFiles`, `android.r8.optimizedResourceShrinking`,
   `android.r8.strictFullModeForKeepRules`, `android.r8.proguardAndroidTxt.disallowed`, `android.r8.globalOptionsInConsumerRules.disallowed`,
   `android.sourceset.disallowProvider`, `android.custom.shader.path.required`; `android.dependency.useConstraints` `true -> false`;
   `android.defaults.buildfeatures.resvalues` and `.shaders` `true -> false`. **Nothing needs to go in `gradle.properties`** for a new project
   (the AGP-9 migration skill says to *remove* `android.builtInKotlin`, `android.newDsl`, `android.uniquePackageNames`, `android.enableAppCompileTimeRClass`).
5. **R8**: `proguard-android.txt` is disallowed -> use `getDefaultProguardFile("proguard-android-optimize.txt")` (legacy `build.gradle:50` uses the disallowed file);
   strict full mode: `-keep class A` no longer implies `<init>()`; optimized resource shrinking on by default; `-processkotlinnullchecks` default `remove_message`.
6. Java default source/target is now **11** (was 8); set 17 explicitly.
7. AGP 9.4 adds per-module new-DSL opt-out `android.newDsl.optOut=:module`; AGP 10 makes the new Variant API mandatory.
8. Compose Preview Screenshot Testing moves to "AGP test suites" from **AGP 9.5.0-alpha03** (https://developer.android.com/agents/skills/testing/testing-setup/skill) — not stable yet; use Roborazzi now.

Gotcha found in smoke build: AGP 9.4.1 defaults to **build-tools 36.0.0**, which was *not* installed (`/opt/android-sdk/build-tools` has 36.1.0 and 37.0.0). AGP auto-installed it
("Installing Android SDK Build-Tools 36 in /opt/android-sdk/build-tools/36.0.0") because licenses are accepted. Either keep that (CI runners also auto-install) or pin
`buildToolsVersion = "37.0.0"` to reuse the preinstalled package.

Gotcha: Gradle 9.8.x prints `The Configuration.setVisible(boolean) method has been deprecated ... removed in Gradle 11`, attributed to plugin id `com.android.internal.application` (AGP 9.4.1 itself) — harmless, but it means `--warning-mode fail` cannot be used with 9.8.x + AGP 9.4.1. (See §2 for 9.7.1 comparison.)

### 1.3 Kotlin

| Artifact | Stable | Pre | Metadata |
|---|---|---|---|
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | **2.4.21** | 2.5.0-Beta2 | `repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml` |
| `org.jetbrains.kotlin.plugin.compose` (marker) / `compose-compiler-gradle-plugin` | **2.4.21** | 2.5.0-Beta2 | `.../org/jetbrains/kotlin/plugin/compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin/maven-metadata.xml` |
| `org.jetbrains.kotlin.plugin.serialization` (marker) / `kotlin-serialization` | **2.4.21** | 2.5.0-Beta2 | `.../org/jetbrains/kotlin/kotlin-serialization/maven-metadata.xml` |
| `org.jetbrains.kotlin.jvm` (marker) | **2.4.21** | — | `.../org/jetbrains/kotlin/jvm/org.jetbrains.kotlin.jvm.gradle.plugin/maven-metadata.xml` |
| `kotlin-stdlib` | **2.4.21** | — | |

Release cadence (https://kotlinlang.org/docs/releases.html): 2.4.0 (2026-06-03), 2.4.10 (07-14), 2.4.20 (09-07), **2.4.21 (2026-10-08)**; 2.5.0 planned Dec 2026; 2.4 line supported until 2027-12-03.
KGP compatibility (https://kotlinlang.org/docs/gradle-configure-project.html): **KGP 2.4.20–2.4.21: Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1** "fully supported";
newer Gradle/AGP "may work" with possible deprecation warnings. => With AGP 9.4.1 we are one minor beyond KGP's tested AGP max (Gradle side tests AGP 9.0–9.5.0-alpha02 and Kotlin up to 2.4.20-RC2). The smoke build was clean (no Kotlin warnings).
Compose compiler: version == Kotlin version; apply `org.jetbrains.kotlin.plugin.compose` in every Compose module (https://developer.android.com/develop/ui/compose/compiler). That page also states: "starting with Compose 1.12.0, projects are required to use `compileSdk 37` and Android Gradle Plugin (AGP) 9."

**KSP**: not needed (no Room/Hilt/Moshi codegen). If ever needed: KSP2 is now versioned independently — `com.google.devtools.ksp` **2.3.12** (marker + `symbol-processing-gradle-plugin`; lastUpdated 2026-09-09). The AGP-9 skill requires KSP >= 2.3.6.

### 1.4 Compose BOM — `androidx.compose:compose-bom`

Stable **2026.09.00** (metadata lastUpdated 2026-09-09; previous: 2026.08.00, 2026.06.01, 2026.06.00, 2026.05.01). From `compose-bom-2026.09.00.pom`:

| Group:artifact | BOM version |
|---|---|
| `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling`, `ui-tooling-preview`, `ui-test-junit4`, `ui-test-manifest` | **1.12.1** |
| `androidx.compose.foundation:foundation`, `foundation-layout` | **1.12.1** |
| `androidx.compose.runtime:runtime` (+ `runtime-tracing`) | **1.12.1** |
| `androidx.compose.animation:animation`, `animation-core`, `animation-graphics` | **1.12.1** |
| `androidx.compose.material:material`, `material-ripple`, `material-navigation` | 1.12.1 |
| `androidx.compose.material3:material3`, `material3-window-size-class`, `material3-adaptive-navigation-suite` | **1.4.0** |
| `androidx.compose.material3.adaptive:adaptive`, `adaptive-layout`, `adaptive-navigation`, `adaptive-navigation3` | **1.3.0** |
| `androidx.compose.material:material-icons-core`, `material-icons-extended` | 1.7.8 (frozen since 2025-02-12) |

Compose pre-release: 1.13.0-beta02 (its AARs require **minSdk 24** and **compileSdk 37.1** — `minCompileMinorSdk=1`).

### 1.5 Material 3 — `androidx.compose.material3:material3`

| | |
|---|---|
| Stable | **1.4.0** (what the BOM ships) |
| Pre | **1.5.0-beta02** (2026-10-21); beta01 2026-10-07; alpha29 2026-09-23 |
| Metadata | `https://dl.google.com/android/maven2/androidx/compose/material3/material3/maven-metadata.xml` |
| Release notes | https://developer.android.com/jetpack/androidx/releases/compose-material3 |

**Is M3 Expressive stable?** Verified by downloading `material3-android-1.4.0.aar` / `-sources.jar` and `1.5.0-beta02`:

| API | 1.4.0 (stable) | 1.5.0-beta02 |
|---|---|---|
| `MotionScheme` interface | public | public |
| `MotionScheme.expressive()` / `.standard()` | **`internal`** (bytecode `expressive$material3`) | **public, no opt-in** |
| `MaterialExpressiveTheme(...)` | **`internal`** (`MaterialTheme.kt:185`) | **public, no opt-in** (`MaterialTheme.kt:243`) |
| `MaterialTheme(colorScheme, motionScheme, shapes, typography, content)` overload | public | public |
| `ButtonGroup`, `ToggleButton`, `SplitButton`, `FloatingToolbar` (`HorizontalFloatingToolbar`…), `FloatingActionButtonMenu`, `LinearWavyProgressIndicator`/`CircularWavyProgressIndicator` | **absent** | **public, no opt-in** (ButtonGroup "promoted to stable" in alpha22; SplitButton alpha20; FloatingToolbar alpha22; TopAppBar family & expressive list items alpha23) |
| `MaterialShapes` (35 `RoundedPolygon`s: `Cookie9Sided`, `Sunny`, `Clover8Leaf`, `Burst`, `Pill`, `Gem`, …), `RoundedPolygon.toShape()`, `Morph.toPath()` | **absent** | present, still **`@ExperimentalMaterial3ExpressiveApi`** |
| `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults` | **absent** | present, still **`@ExperimentalMaterial3ExpressiveApi`** |
| Remaining expressive opt-ins in 1.5.0-beta02 (15 public decls total) | — | `LoadingIndicator.kt` (5), `MaterialShapes.kt` (4), `PullToRefresh.kt` (3), `Menu.kt` `DropdownMenuItem` overloads (2), `Slider.kt` `Thumb` (1) |
| AAR `minSdkVersion` | 21 | **24** |
| AAR `minCompileSdk` / min AGP | 35 / 8.6.0 | **37.0 / 9.1.0** |
| Compose deps | — | `foundation`/`ui`/`runtime`/`material-ripple`/`ui-text` **1.12.0**, `graphics-shapes` 1.0.1 (=> BOM-compatible, no Compose 1.13 / compileSdk 37.1 drag-in) |

Recommendation: **pin `material3 = "1.5.0-beta02"` on top of BOM 2026.09.00** (a BOM-managed module can be overridden by an explicit version) and add
`kotlin { compilerOptions { optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi") } }` for `MaterialShapes`/`LoadingIndicator`.
Bump to 1.5.0 stable when it lands. Smoke build compiled and rendered (Robolectric SDK 37) `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`,
`MaterialShapes.Cookie9Sided.toShape()`, `ButtonGroup { clickableItem(...) }`, `LoadingIndicator()`.
If the plan must stay strictly on stable artifacts: 1.4.0 gives no expressive motion scheme and no shape library — you would have to hand-roll springs/shapes
(`androidx.graphics:graphics-shapes` **1.1.0** stable gives `RoundedPolygon`/`Morph` directly).

### 1.6 Icons

`material-icons-core` / `material-icons-extended`: last version **1.7.8** (2025-02-12), still mapped by the BOM. Official guidance
(https://developer.android.com/develop/ui/compose/graphics/images/material): "this artifact is no longer maintained or recommended for use in your apps, as it contains an
older look and feel and can also increase the build time of your apps *significantly*. Instead, we recommend using Google Font Icons
(https://fonts.google.com/icons) and download the XML file from the Android Tab to create an up-to-date Material Symbols style Icon."
=> Commit the handful of Material Symbols (Rounded) vector drawables we need under `app/src/main/res/drawable/` and use `painterResource(...)`.
Offline-friendly (no font downloads). Do **not** use `ui-text-google-fonts` (downloadable fonts need GMS/network) — bundle fonts in `res/font/`.

### 1.7 AndroidX (Google Maven, `https://dl.google.com/android/maven2/<group>/<artifact>/maven-metadata.xml`)

| Artifact | Stable | Pre | AAR minSdk | AAR minCompileSdk / min AGP |
|---|---|---|---|---|
| `androidx.activity:activity-compose` | **1.13.0** | 1.14.0-alpha03 (minSdk 24) | 23 | 36 / 8.9.1 |
| `androidx.core:core-ktx` | **1.19.1** | — | 23 | 37.0 / 9.1.0 |
| `androidx.core:core-splashscreen` | **1.2.0** | — | 21 | 35 / 8.6.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | **2.11.0** | 2.12.0-alpha05 | 23 | 37.0 / 9.1.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | **2.11.0** | 2.12.0-alpha05 | 23 | — |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | **2.11.0** | 2.12.0-alpha05 | — | — |
| `androidx.navigation3:navigation3-runtime` / `-ui` | **1.2.0** (stable 2026-09-23) | 1.3.0-alpha03 | **24** | 37.0 / 9.1.0 |
| `androidx.navigation:navigation-compose` | 2.10.2 | — | **24** | 37.0 / 9.1.0 |
| `androidx.navigationevent:navigationevent-compose` | **1.2.0** | — | — | — |
| `androidx.datastore:datastore` / `datastore-preferences` / `datastore-core` | **1.3.0** (2026-10-21) | — | **24** | 34 / 8.1.1 |
| `androidx.window:window` | 1.5.1 | 1.6.0-alpha05 | 23 | 34 |
| `androidx.compose.material3.adaptive:adaptive` | 1.3.0 (via BOM) | 1.4.0-alpha03 | 23 | 37.0 / 9.1.0 |
| `androidx.graphics:graphics-shapes` | 1.1.0 | — | 23 | 34 |
| `androidx.profileinstaller:profileinstaller` | **1.4.1** | — | 21 | 34 |
| `androidx.baselineprofile` Gradle plugin | **1.5.0** (2026-09-09) | 1.6.0-alpha01 | — | — |
| `androidx.benchmark:benchmark-macro-junit4` / `benchmark-junit4` | **1.5.0** | 1.6.0-alpha01 | — | — |
| `androidx.test.uiautomator:uiautomator` | **2.4.0** | — | — | — |
| `androidx.test:core` / `runner` / `rules` | **1.7.0** | 1.8.0-alpha01 | — | — |
| `androidx.test.ext:junit` | **1.3.0** | 1.4.0-alpha01 | — | — |
| `androidx.test.ext:truth` | 1.7.0 | — | — | — |
| `androidx.test.espresso:espresso-core` | **3.7.0** | 3.8.0-alpha01 | — | — |
| `androidx.test:orchestrator` | 1.6.1 | 1.7.0-alpha01 | — | — |
| `androidx.appcompat:appcompat` (only if per-app language on API < 33 is wanted) | 1.8.0 | — | — | — |
| `com.android.tools:desugar_jdk_libs` (not needed) | 2.1.5 | — | — | — |

minSdk/compileSdk columns come from each AAR's `AndroidManifest.xml` and `META-INF/com/android/build/gradle/aar-metadata.properties` (downloaded 2026-10-28).

Notes:
- **Navigation**: Navigation 3 is stable (1.0.0 on 2025-11-19, 1.1.0 on 2026-04-08, **1.2.0 on 2026-09-23**; https://developer.android.com/jetpack/androidx/releases/navigation3).
  1.2.0 adds `NavigationBackHandler`/`rememberNavigationEventState` for predictive back, a Result API, deep links; 1.1.0 added shared elements between scenes, `SceneDecoratorStrategy`, metadata DSL.
  `rememberNavBackStack()` requires `@Serializable` `NavKey`s (kotlinx.serialization, polymorphic, registered in a `SerializersModule`). For ~4 destinations (Game, Settings, Stats, About) Navigation 3 is the right choice (Compose-first, back stack is plain state); `navigation-compose` 2.10.x is the legacy path.
- **Back handling**: `BackHandler` / `PredictiveBackHandler` in activity-compose 1.13.0 are **not deprecated** (checked sources). `activity-compose` 1.13.0 already depends on `navigationevent-compose` 1.0.0.
- **DataStore** (https://developer.android.com/jetpack/androidx/releases/datastore): 1.3.0 adds `DataStore.Builder<T>` (takes a `CoroutineContext` with an app-scoped `Job`), `datastore-tink`, baseline profiles, `androidx.tracing`. Options: Preferences DataStore (key/value), typed DataStore with a custom `Serializer<T>` (JSON via kotlinx-serialization) or Proto (needs protobuf plugin). Recommendation: **typed DataStore + kotlinx-serialization JSON** for game/session state and stats (single `@Serializable` data class, versioned field for migration), **Preferences DataStore** for settings (or fold settings into the typed one). No protobuf toolchain.
- **Window size classes**: use `currentWindowAdaptiveInfo()` (`androidx.compose.material3.adaptive:adaptive` 1.3.0 via BOM; also `currentWindowDpSize()`, `currentWindowSize()`, `collectFoldingFeaturesAsState()`)
  -> `windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` (`androidx.window:window-core` 1.5.1; constants WIDTH 600/840/1200/1600 dp, HEIGHT 480/900 dp — read from the class file).
  `material3-window-size-class` (1.4.0) is the older API; don't add it.
- **DataStore ships native code**: `datastore-core-android-1.3.0.aar` contains `jni/<abi>/libdatastore_shared_counter.so` (multi-process counter). `graphics-path-1.0.1` (pulled in transitively) ships `libandroidx.graphics.path.so`. See §(f).

### 1.8 Kotlin libraries (Maven Central)

| Artifact | Stable | Notes |
|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | **1.12.0** (2026-10-19) | + plugin `org.jetbrains.kotlin.plugin.serialization` 2.4.21 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-test` | **1.11.0** (2026-05-08) | |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | **0.5.2** (2026-08-28) | for stable `ImmutableList` board state in Compose |

### 1.9 Testing

| Artifact | Stable | Source / notes |
|---|---|---|
| `org.robolectric:robolectric` | **4.17** (2026-09-10) | Supports **API 23 -> 37** (https://robolectric.org/compatibility_table/); built with Gradle 9.6.1 / AGP 9.2.1. Java needed: **API 36 and 37 -> JDK 21**, 34/35 -> 17. android-all for 37: `org.robolectric:android-all-instrumented:17-robolectric-15733970-i7` |
| `io.github.takahirom.roborazzi:roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`, plugin `io.github.takahirom.roborazzi` | **1.77.0** (2026-10-22) | Supports Compose Testing v2 since 1.68.0 (experimental); preview-test generation 1.6x faster since 1.75.0 |
| `junit:junit` | **4.13.2** | Required by Robolectric runner and `ui-test-junit4` |
| `org.junit:junit-bom` / `junit-jupiter` / `junit-platform-launcher` | **6.1.3** (2026-08-07) | JUnit 6 requires Java 17 |
| `de.mannodermaus.android-junit5` plugin | **2.0.2** (2026-10-15) | needs AGP >= 8.2; JUnit 6 instrumentation tests need API 35+ devices. **Not needed** (see below). |
| `com.google.truth:truth` | **1.4.5** (2025-09-10) | |
| `com.willowtreeapps.assertk:assertk` | 0.28.1 (2024-04-17, stale) | prefer Truth or kotest-assertions |
| `app.cash.turbine:turbine` | **1.2.1** (2025-06-11) | Flow testing |
| `io.kotest:kotest-property` (+ `-jvm`) | **6.2.6** (2026-10-14) | standalone property testing inside JUnit tests (`checkAll` in `runTest`/`runBlocking`) |
| `net.jqwik:jqwik` | 1.10.2 (2026-10-10) | alternative PBT engine (JUnit Platform engine) |
| `io.mockk:mockk` | 1.14.11 | not needed (pure engine, fakes) |
| `androidx.compose.ui:ui-test-junit4` | 1.12.1 (BOM) | see v2 APIs below |

**Compose UI test v2** (verified in `ui-test-junit4-android-1.12.1` / `ui-test-android-1.12.1` sources): new packages
`androidx.compose.ui.test.junit4.v2` (`createComposeRule(effectContext)`, `createAndroidComposeRule<A>()`, `createEmptyComposeRule()`) and
`androidx.compose.ui.test.v2` (`runComposeUiTest`, `runAndroidComposeUiTest`, `runEmptyComposeUiTest`; still `@ExperimentalTestApi`).
v1 `androidx.compose.ui.test.junit4.createComposeRule(effectContext)` is `@Deprecated(level = WARNING)`: "Use `androidx.compose.ui.test.junit4.v2.createComposeRule` instead.
The v2 APIs use StandardTestDispatcher instead of UnconfinedTestDispatcher …". The no-arg v1 `createComposeRule()` is `DeprecationLevel.HIDDEN`. => **Write all new tests against v2.**

**JUnit choice**: `:core:engine` (pure JVM) -> **JUnit Jupiter 6.1.3** natively via `tasks.test { useJUnitPlatform() }` + kotest-property. `:app` local tests (Robolectric, Compose, Roborazzi) -> **JUnit 4** (Robolectric's runner and `ui-test-junit4` are JUnit 4). No need for the android-junit5 plugin.
Gotcha (hit in smoke build): in JUnit Jupiter, an expression-bodied test `@Test fun x() = runBlocking { checkAll(...) {...} }` returns non-`Unit` and is **silently not run** — use block bodies (`@Test fun x() { runBlocking { ... } }`) or `= runTest { ... }` returning `TestResult`/Unit.

### 1.10 Lint / format / static analysis

| Tool | Stable | Kotlin 2.4 / AGP 9 status |
|---|---|---|
| Spotless `com.diffplug.spotless` | **8.10.4** (2026-10-08) | Upcoming release bumps default ktfmt to 0.65 under new coordinates `org.jetbrains.kotlinx:ktfmt` (https://github.com/diffplug/spotless/releases) |
| ktfmt `com.facebook:ktfmt` | **0.64** (2026-06-24) | new coordinate `org.jetbrains.kotlinx:ktfmt` **0.65** (2026-10-07) is not yet a Spotless 8.10.4 default -> use `ktfmt("0.64").kotlinlangStyle()` |
| ktfmt-gradle `com.ncorti.ktfmt.gradle` | 0.27.0 (2026-08-03) | AGP 9 support, CC compatible, Kotlin 2.4.10 |
| ktlint `com.pinterest.ktlint:ktlint-cli` | **1.8.0** (2025-12-15) | embeds `kotlin-compiler-embeddable` **2.2.21** (from `ktlint-rule-engine-1.8.0.pom`): fine for ordinary code, may not parse brand-new 2.3/2.4 syntax |
| `org.jlleitschuh.gradle.ktlint` | 14.2.0 | |
| `org.jmailen.kotlinter` | 5.7.0 | |
| detekt 1.x `io.gitlab.arturbosch.detekt` | 1.23.8 (2025-02-20) | **built against Kotlin 2.0.21 / AGP 8.8.1 / Gradle 8.12.1 — not compatible with Kotlin 2.4 + AGP 9 built-in Kotlin. Do not use.** (https://detekt.dev/docs/introduction/compatibility/) |
| detekt 2.x `dev.detekt` | **2.0.0-alpha.6** (2026-08-04) | built against Kotlin 2.4.10, Gradle 9.6.1, AGP 9.3.1, JDK 25; AGP-9 built-in Kotlin supported since alpha.3; DGP declares CC compatibility (alpha.4). Alpha only. |
| compose-rules `io.nlopez.compose.rules:ktlint` / `:detekt` | **0.6.8** (2026-10-09) | ruleset for ktlint/detekt |
| Slack compose lint `com.slack.lint.compose:compose-lint-checks` | **1.6.1** (2026-10-07) | Android Lint checks (`lintChecks(...)`); runs inside AGP lint 32.4.1, no Kotlin-version coupling |
| Kover `org.jetbrains.kotlinx.kover` | 0.9.11 | optional coverage |
| dependency-analysis `com.autonomousapps.dependency-analysis` | 3.19.2 | optional |
| ben-manes versions `com.github.ben-manes.versions` | 0.66.0 | optional (`dependencyUpdates`) |
| foojay resolver `org.gradle.toolchains.foojay-resolver-convention` | 1.0.0 | only if toolchain auto-provisioning is used |

Recommendation: **Spotless + ktfmt 0.64 (kotlinlang style)** for formatting, **Android Lint** (`warningsAsErrors`, `checkDependencies`) **+ Slack compose-lint-checks 1.6.1** for Compose correctness. detekt is optional: only `dev.detekt` 2.0.0-alpha.6 (+ `io.nlopez.compose.rules:detekt` 0.6.8) works with our toolchain.

**Smoke-verified on Gradle 9.7.1 + AGP 9.4.1 + Kotlin 2.4.21** (`<sandbox-scratchpad>/smokelint`):
- Spotless 8.10.4 with `kotlin { target("**/*.kt"); targetExclude("**/build/**"); ktfmt("0.64").kotlinlangStyle() }` and the same for `kotlinGradle { target("**/*.gradle.kts") }`: `spotlessCheck` detects violations, `spotlessApply` fixes them, re-check passes (idempotent).
- `:app:lintDebug` with `lintChecks("com.slack.lint.compose:compose-lint-checks:1.6.1")`: runs; Slack rule `ComposeModifierMissing` fired as an **error** on a `@Composable` without `modifier`. Lint also notes "Lint will treat :core:engine as an external dependency and not analyze it" — apply `com.android.lint` (same version as AGP) to the JVM module if we want it linted. In AGP 9.4 `lint { textReport = ... }` is **deprecated** ("Lint reports are now always generated").
- detekt `dev.detekt` 2.0.0-alpha.6 applied to `:app` and `:core:engine`: tasks `detekt`, `detektMain`, `detektDebug…`, baselines generated; analysis ran under AGP 9 built-in Kotlin (no `android.builtInKotlin=false` needed) and reported `MatchingDeclarationName`, `FunctionNaming` (on a `@Composable` — needs `FunctionNaming: ignoreAnnotated: ['Composable']`), `MagicNumber` (color literals — needs config/`ignoreAnnotated`), compose-rules `ModifierMissing`. Works, but default config is noisy for Compose and it is alpha.

### 1.11 GitHub Actions (latest major tags, fetched from release pages 2026-10)

| Action | Latest | Notes |
|---|---|---|
| `actions/checkout` | **v7** (7.0.1, 2026-07-20) | v7 changed `pull_request_target` defaults (https://github.blog/changelog/2026-06-18-safer-pull_request_target-defaults-for-github-actions-checkout/) |
| `actions/setup-java` | **v6** (6.1.0, 2026-10-14) | `distribution: temurin`, `java-version: 21` |
| `gradle/actions/setup-gradle` (+ `wrapper-validation`) | **v6** (6.4.0) | Job summary flags EOL/out-of-date Gradle |
| `actions/upload-artifact` | **v7** (7.0.2, 2026-10-07) | Node 24 runtime |

---

## 2. Gradle <-> AGP <-> Kotlin <-> JDK matrix for our pick  — (d)

| Component | Requirement | Our pick | Status |
|---|---|---|---|
| AGP 9.4.1 | Gradle >= 9.6.0, JDK >= 17, build-tools >= 36.0.0 | Gradle 9.7.1 (or 9.8.2), JDK 21, BT 36.0.0 (auto) / 37.0.0 | OK |
| KGP 2.4.21 | fully supported Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1 | Gradle 9.7.1, AGP 9.4.1 | Gradle inside range (patch of 9.7); AGP one minor beyond KGP's tested max (Gradle docs list AGP 9.0–9.5.0-alpha02 as tested). Smoke builds: zero Kotlin warnings. |
| Gradle 9.7.1 / 9.8.2 | daemon JVM 17–26 / 17–27 | JDK 21 (`/usr/bin/java` = OpenJDK 21.0.12.1, Ubuntu) | OK |
| Robolectric 4.17 @ SDK 36/37 | JDK 21 at test runtime | JDK 21 | OK |
| Compose 1.12.1 | compileSdk 37.0, AGP >= 9.1.0, minSdk >= 23 | 37 / 9.4.1 / 24 | OK |
| Material3 1.5.0-beta02 | compileSdk 37.0, AGP >= 9.1.0, minSdk >= 24 | OK | OK |

**Pick Gradle 9.7.1** — inside KGP's tested Gradle range and >= AGP 9.4's 9.6.0; side-by-side smoke builds in §6.1 (9.7.1 warning-free; 9.8.2 one AGP deprecation). Gradle 9.7 supports running on JVM 17–26 (Java 26 support since 9.4.0; Java 27 needs 9.8.0) — irrelevant for our JDK 21.
CI: any JDK 21 distribution; pin `java-version: 21` (not 17 — Robolectric SDK 37 refuses to start on 17: "Android SDK 37 requires Java 21").

---

## 3. compileSdk / targetSdk / minSdk  — (a), (b), (c)

### (a) compileSdk 37 and the "37.0" minor-SDK syntax

- SDK repository (`https://dl.google.com/android/repository/repository2-4.xml`, channel-0 = stable) now names platforms with a minor:
  `platforms;android-37.0` (api-level `37.0`, rev 2, ext 22), `platforms;android-37.1` (ext 23), `platforms;android-37.2` (ext 24); betas `android-37.2-beta1..3`.
  API 36 is `platforms;android-36` and `platforms;android-36.1`. Locally installed: `/opt/android-sdk/platforms/android-37.0` (`AndroidVersion.ApiLevel=37.0`, `Platform.Version=17`, `PreviewSdkInt=0`) and `android-36`.
- DSL: `compileSdk = 37` **resolves to `platforms;android-37.0`** (verified: build used `/opt/android-sdk/platforms/android-37.0`; `aapt2 dump badging` of the APK reports
  `compileSdkVersion='37'`, `platformBuildVersionName='17'`). To target a minor release use the AGP 8.13+ `CompileSdkSpec` block
  (https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/CompileSdkSpec, https://developer.android.com/build/gradle-build-overview):
  ```kotlin
  android {
      compileSdk { version = release(37) { minorApiLevel = 1 } }   // -> platforms;android-37.1
  }
  ```
  Not needed today: Compose 1.12.x / M3 1.5.0-beta02 / core 1.19.1 declare `minCompileSdk=37` + `minCompileMinorSdk=0`. Compose **1.13** (beta) declares `minCompileMinorSdk=1`, so the bump to 37.1 will be forced when we move to Compose 1.13 — install `platforms;android-37.1` then.
- Minimum tooling for API 37.0: AGP 9.1.1 / Android Studio Panda 3 Patch 1 (https://developer.android.com/build/releases/about-agp). Android 17 SDK setup page: build-tools 37.x.x recommended (https://developer.android.com/about/versions/17/setup-sdk).

### (b) Google Play target API requirement in late 2026

https://developer.android.com/google/play/requirements/target-sdk (fetched 2026-10-28):
- **Since 2026-08-31: new apps and app updates must target Android 16 (API 36) or higher** (Wear OS/AAOS 35+, TV/XR 34+). Extension available until **2026-11-01**.
- Existing apps must target >= API 35 to remain available to new users on newer devices.
- No 2027 requirement published yet (historic cadence suggests API 37 by ~Aug 2027 — unverified).
=> **targetSdk 37** satisfies today's rule and pre-empts next year's. Android 17 is final: "Android 17 is Here" (2026-06-16, https://developer.android.com/blog/posts/android-17-is-here).

### (c) minSdk floor and recommendation

From AAR manifests (downloaded):
- minSdk **23**: Compose ui/foundation/runtime/animation 1.12.1, activity(-compose) 1.13.0, core 1.19.1, lifecycle 2.11.0, window 1.5.1, adaptive 1.3.0, graphics-shapes 1.1.0, ui-test 1.12.1. (AndroidX default moved 21 -> 23 in 2025.)
- minSdk **24**: **navigation3 1.2.0**, **datastore 1.3.0**, navigation-compose 2.10.2, **material3 1.5.0-beta02**, and every next-wave pre-release checked (compose ui 1.13.0-beta02, activity-compose 1.14.0-alpha03).
- minSdk 21: material3 1.4.0, core-splashscreen 1.2.0, profileinstaller 1.4.1.

**Recommendation: `minSdk = 24`.** It is the hard floor of the libraries we want (Nav3, DataStore 1.3, M3 1.5) and the direction AndroidX is moving; nothing in the app needs < 24.
Legacy app was `minSdkVersion 14` (`build.gradle:29`) — irrelevant now that the applicationId changes (new Play listing). Going to 26 would only buy native adaptive icons without PNG fallbacks and `java.time` without desugaring; not required (we do not use `java.time`; adaptive icon PNG fallbacks for 24–25 are generated by Android Studio's Image Asset tool, or ship a `mipmap-anydpi-v26` + legacy PNGs).

---

## 4. Platform behaviors at targetSdk 37  — (e)

Edge-to-edge
- Enforced for apps targeting 35+ on Android 15+ devices; for **targetSdk 36+ `R.attr#windowOptOutEdgeToEdgeEnforcement` is deprecated and disabled** on Android 16+ devices (https://developer.android.com/about/versions/16/behavior-changes-16).
- Do: `enableEdgeToEdge()` (activity 1.13.0) in `onCreate` before `setContent` so API 24–34 behave the same; consume insets via `Scaffold`/`WindowInsets.safeDrawing`/`safeContent`; draw the board inside `safeDrawing` and the background full-bleed.

Predictive back
- targetSdk 36+ on Android 16+: predictive back system animations (back-to-home, cross-task, cross-activity) are **on by default**; **`onBackPressed` is not called and `KEYCODE_BACK` is not dispatched**
  (same page). `android:enableOnBackInvokedCallback` is effectively true (merged manifest shows `enableOnBackInvokedCallback=true`); opting out is only a temporary escape hatch.
- Use `BackHandler`/`PredictiveBackHandler` (activity-compose 1.13.0) or Navigation 3's `NavDisplay` predictive pop (`predictivePopTransitionSpec`, `NavigationBackHandler` in nav3 1.2.0 / navigationevent-compose 1.2.0).

Large screens / orientation (important: legacy manifest locks `android:screenOrientation="portrait"`, `AndroidManifest.xml:14`)
- targetSdk 36: orientation/resizability/aspect-ratio restrictions ignored on sw >= 600dp (temporary opt-out `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`).
- **targetSdk 37: the opt-out is removed** (https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored). Ignored: `screenOrientation`, `resizeableActivity`, `minAspectRatio`, `maxAspectRatio`, `setRequestedOrientation()`.
- **Exemption: games, "based on the `android:appCategory` flag"** (Android 17 blog: "Games (based on app category in Google Play) remain exempt"). => set `android:appCategory="game"` (verified: merged manifest emits `appCategory=0` = `CATEGORY_GAME`) **and** choose Game > Puzzle in Play Console; still build an adaptive layout (portrait + landscape, board as `min(width,height)` square, side panel in landscape/expanded) because desktop windowing/bubbles/split-screen still resize the window.

Other Android 17 (targetSdk 37) changes relevant to us (https://developer.android.com/about/versions/17/behavior-changes-17, release notes):
- **Activity recreation**: the system no longer restarts activities by default for config changes that don't need a full redraw (e.g. `CONFIG_KEYBOARD`, `CONFIG_KEYBOARD_HIDDEN`, `CONFIG_NAVIGATION`); opt back in with `android:recreateOnConfigChanges`. Legacy `configChanges="orientation|keyboardHidden|screenSize"` (`AndroidManifest.xml:12`) is not needed for a Compose app that keeps state in a `ViewModel` + `rememberSaveable`.
- Background audio hardening (only if sounds play while backgrounded — stop SFX `onStop`).
- `static final` fields unmodifiable via reflection; lock-free `MessageQueue`; Safer native DCL (`System.load` files must be read-only) — none affect us.
- Android 16: `elegantTextHeight` ignored at targetSdk 36 — irrelevant for Compose text.

---

## 5. 16 KB page size  — (f)

- Requirement (https://developer.android.com/guide/practices/page-sizes): "all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play. Starting **February 1, 2027**, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates." Pure Kotlin apps are compatible by definition; AGP >= 8.5.1 zip-aligns uncompressed `.so` at 16 KB.
- **We are not "no native code"**: the release APK contains AndroidX native libs
  `lib/{arm64-v8a,armeabi-v7a,x86,x86_64}/libdatastore_shared_counter.so` (from `datastore-core-android:1.3.0`) and `libandroidx.graphics.path.so` (from `androidx.graphics:graphics-path:1.0.1`, transitive).
- **Verified compliant**: ELF `PT_LOAD p_align = 0x4000` for every ABI (parsed program headers), libs stored uncompressed (`compress_type=0`), and
  `zipalign -c -P 16 -v 4 app-release.apk` (build-tools 37.0.0) -> "Verification successful".
- CI gate: run `$ANDROID_HOME/build-tools/37.0.0/zipalign -c -P 16 -v 4 <apk>` on the release APK.

---

## 6. Local smoke build — what was actually verified

Projects: `<sandbox-scratchpad>/smoke` (Gradle 9.8.2 wrapper, AGP 9.4.1, Kotlin 2.4.21, BOM 2026.09.00, M3 1.5.0-beta02, `:app` + `:core:engine`),
`.../scratchpad/smoke971` (same, Gradle 9.7.1, engine at JVM 17), `.../scratchpad/smoke982b` (9.8.2, engine at JVM 17), `.../scratchpad/smokelint` (9.7.1 + Spotless/ktfmt, Lint+compose-lint-checks, detekt 2.0.0-alpha.6).

| Check | Result |
|---|---|
| `:core:engine:test` (JUnit Jupiter 6.1.3 + kotest-property 6.2.6, Kotlin JVM + serialization plugin) | PASS (2/2) |
| `:app:assembleDebug` (compileSdk 37, minSdk 24, targetSdk 37, built-in Kotlin, Compose plugin, serialization plugin) | PASS |
| `:app:testDebugUnitTest` — Robolectric 4.17 `@Config(sdk = [37])`, `@GraphicsMode(NATIVE)`, **v2** `createComposeRule()`, Roborazzi `captureRoboImage` | PASS **after two fixes** (below); screenshot rendered expressive shape, ButtonGroup, LoadingIndicator |
| `:app:assembleRelease` (R8 full mode + `isShrinkResources`, `proguard-android-optimize.txt`) | PASS; **APK 1,034,747 bytes**; baseline profile `assets/dexopt/baseline.prof` (4,527 B) from library profiles + profileinstaller |
| Merged manifest | only `uses-permission` = `com.clayboicardi.game2048.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (app-private, `protectionLevel="signature"`, injected by androidx.core); **no INTERNET, no dangerous/runtime permissions** |
| `androidResources { generateLocaleConfig = true }` + `res/resources.properties` (`unqualifiedResLocale=en-US`) | PASS — generated `xml/_generated_res_locale_config.xml` (`defaultLocale="en-US"`, locales from our `values-*`); manifest gets `android:localeConfig` |
| `android:appCategory="game"` | emitted |
| 16 KB alignment | PASS (see §5) |
| Configuration cache (`org.gradle.configuration-cache=true`) | entry stored, no CC problems with AGP 9.4.1 + KGP 2.4.21 + Roborazzi 1.77.0 (reuse check: see §6.1) |
| KGP on classpath | `kotlin-gradle-plugin:2.2.10 -> 2.4.21` (`buildEnvironment`) |

Fixes required (put these in the real build):
1. **Robolectric on JDK 21** fails with `Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?` / `IllegalAccessException ... jdk.internal.access.SharedSecrets` (also reported as robolectric/robolectric#11434). Add the `--add-opens` set from https://robolectric.org/getting-started/:
   ```kotlin
   android { testOptions { unitTests { isIncludeAndroidResources = true; all { it.jvmArgs(
       "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
       "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
       "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
       "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
       "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED") } } } }
   ```
2. **Espresso 3.5.1 breaks on SDK 37**: `ui-test-junit4:1.12.1` transitively resolves `espresso-core:3.5.1`, whose `InputManagerEventInjectionStrategy` reflects `android.hardware.input.InputManager.getInstance()` (gone in API 37) -> `NoSuchMethodException` during `Espresso.onIdle`. **Force `testImplementation("androidx.test.espresso:espresso-core:3.7.0")`** (also in `androidTestImplementation`). With 3.7.0 the SDK-37 test passes.

Environment gotchas (sandbox only, not CI):
- Maven Central (`repo.maven.apache.org`, `repo1.maven.org`) intermittently returns **HTTP 429** through the proxy; Gradle then caches "plugin not found". Work-around used: an **init script** (not committed) that prepends Google's Maven Central mirror
  `https://maven-central.storage-download.googleapis.com/maven2/` to `pluginManagement` and `dependencyResolutionManagement` repositories
  (`<sandbox-scratchpad>/tools/mirror.init.gradle.kts`, run with `./gradlew --init-script ...`).
- `detekt.dev`, `docs.gradle.org`, `downloads.gradle.org` are not reachable directly from the sandbox (DNS/proxy); `services.gradle.org`, `dl.google.com`, `plugins.gradle.org`, `repo1.maven.org`, the GCS mirror are.
- AGP auto-downloads build-tools 36.0.0 into `/opt/android-sdk` (writable, licenses present).

### 6.1 Gradle 9.7.1 vs 9.8.2 (same project, sequential runs, engine compiled to JVM 17)

Task set: `:core:engine:test :app:assembleDebug :app:testDebugUnitTest`, `--warning-mode all`, run twice.

| Gradle | 1st run | 2nd run | Deprecations |
|---|---|---|---|
| **9.7.1** (`distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`) | BUILD SUCCESSFUL in 20 s | **"Reusing configuration cache."** BUILD SUCCESSFUL in 1 s | **none** |
| 9.8.2 | BUILD SUCCESSFUL in 14 s | "Reusing configuration cache." BUILD SUCCESSFUL in 955 ms | 1x `Configuration.setVisible(boolean)` (from plugin `com.android.internal.application`, i.e. AGP 9.4.1) |

=> Recommend **Gradle 9.7.1** for a warning-free build (lets CI use `--warning-mode=fail`) that sits inside every tool's tested range; move to 9.8.x together with AGP 9.5 (whose release should drop the deprecated call — unverified). 9.8.2 is an acceptable alternative.
Engine bytecode check: `javap -v EngineKt` -> `major version: 61` (Java 17) with `java { source/targetCompatibility = 17 }` + `jvmTarget = JVM_17` on JDK 21; app consumed it fine.

---

## 7. Build-logic best practice  — (g)

- **Version catalog** `gradle/libs.versions.toml` only; `plugins { alias(libs.plugins.x) apply false }` in root; no `buildscript {}`, no `allprojects {}` (legacy `build.gradle:1-19` uses both plus `jcenter()` and the dead Sonatype OSS snapshots repo for `ckchangelog 2.0.0-SNAPSHOT`, `build.gradle:5,17,66` — all dropped).
- `settings.gradle.kts`: `pluginManagement { repositories { google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }; mavenCentral(); gradlePluginPortal() } }`
  and `dependencyResolutionManagement { repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS; repositories { google(); mavenCentral() } }`.
- `gradle.properties` (minimal, verified):
  ```properties
  org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
  org.gradle.caching=true
  org.gradle.configuration-cache=true
  org.gradle.parallel=true
  kotlin.code.style=official
  ```
  No `android.*` flags (AGP 9 defaults are what we want). Optionally `org.gradle.configuration-cache.problems=fail` (default) to keep CC clean.
- Kotlin DSL everywhere (`*.gradle.kts`); typed accessors `libs.plugins.*`; `kotlin { compilerOptions { ... } }` (never `kotlinOptions`).
- Java/Kotlin bytecode: `compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }` in `:app` (Kotlin `jvmTarget` follows automatically under built-in Kotlin);
  in `:core:engine` set `java { sourceCompatibility/targetCompatibility = 17 }` + `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }` and run on JDK 21 — avoids requiring a second JDK and keeps the engine consumable by the app. (Smoke build with `jvmToolchain(21)` in the engine also worked — D8 accepted Java 21 class files — but 17 is the cleaner contract.)
- Wrapper: commit `gradle/wrapper/gradle-wrapper.properties` with `distributionSha256Sum` and `validateDistributionUrl=true`; CI runs `gradle/actions/wrapper-validation@v6` (or setup-gradle's built-in validation). Verified content:
  ```properties
  distributionBase=GRADLE_USER_HOME
  distributionPath=wrapper/dists
  distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
  distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
  networkTimeout=10000
  validateDistributionUrl=true
  zipStoreBase=GRADLE_USER_HOME
  zipStorePath=wrapper/dists
  ```
  Generate `gradlew`/`gradle-wrapper.jar` with the local Gradle in an **empty** directory (`gradle wrapper --gradle-version 9.7.1 --distribution-type bin --gradle-distribution-sha256-sum <sha>`) — running `/opt/gradle-8.14.3` inside the project would try to configure AGP 9 and fail/hang. The legacy repo's wrapper is Gradle 4.10.3 (`gradle/wrapper/gradle-wrapper.properties`) and must be replaced wholesale.
- No convention plugins / `build-logic` for two modules — not worth it; revisit if a `:baselineprofile` module and more feature modules appear.
- Isolated Projects: still incubating; do not enable.

## 8. Module structure  — (h)

```
:app                 com.android.application  + kotlin.plugin.compose + kotlin.plugin.serialization (+ roborazzi)
                     UI (Compose/M3), ViewModel, DataStore persistence, Navigation 3, resources/locales
:core:engine         org.jetbrains.kotlin.jvm + kotlin.plugin.serialization   (NO Android deps)
                     pure 2048 rules: Board, Tile(id), move/merge/spawn, score, win/lose, undo history,
                     injectable RNG (kotlin.random.Random seed), @Serializable state snapshot
(:baselineprofile)   com.android.test + androidx.baselineprofile 1.5.0 — optional, phase 2
```
Rationale: the engine is the part worth exhaustive property testing; a JVM module runs JUnit 6 + kotest-property without Robolectric/AGP overhead and cannot accidentally depend on Android. Verified buildable in the smoke project (Android app consuming a `kotlin("jvm")` module under AGP 9 built-in Kotlin).

---

## 9. Other offline / zero-permission notes

- No INTERNET permission is added by any of the proposed libraries (verified merged manifest). Keep a CI check: `aapt2 dump permissions app-release.apk` must list only `com.clayboicardi.game2048.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature-level, self-defined by androidx.core for `ContextCompat.registerReceiver(RECEIVER_NOT_EXPORTED)` on API < 33; removing it with `tools:node="remove"` would make such calls throw on old devices — keep it; it is not a runtime permission).
- `profileinstaller`'s receiver is guarded by `android.permission.DUMP` (`android:permission` on the receiver, not a `uses-permission`).
- Backup: legacy `android:allowBackup="true"` (`AndroidManifest.xml:6`). For targetSdk 31+ provide `android:dataExtractionRules` (and `android:fullBackupContent` for <= 30) including the DataStore files so best score/stats survive device transfer; no network permission needed (system performs backup).
- Per-app language: `generateLocaleConfig` gives Android 13+ system per-app language for free. In-app language picker on API 24–32 would require AppCompat 1.8.0 + `AppCompatActivity` (`AppCompatDelegate.setApplicationLocales` + `AppLocalesMetadataHolderService autoStoreLocales`) — decide in the UX plan; otherwise link to system settings on 33+ only.
- Splash: `core-splashscreen` 1.2.0 `installSplashScreen()` before `super.onCreate`.
- Lint noise to pre-empt (seen in smoke `lintDebug`): `UnusedAttribute` for `android:appCategory` (API 26+) and `android:enableOnBackInvokedCallback` (API 33+) with minSdk 24 — add `tools:targetApi` or simply omit `enableOnBackInvokedCallback` (it defaults to true at targetSdk 36+); `MissingApplicationIcon` until the adaptive icon exists.

---

## 10. Proposed `gradle/libs.versions.toml` (verified versions only)

```toml
# gradle/libs.versions.toml — verified 2026-10-28 against maven-metadata.xml
[versions]
agp = "9.4.1"
kotlin = "2.4.21"                      # KGP + Compose compiler + serialization plugin (must match)
composeBom = "2026.09.00"              # Compose 1.12.1, M3 1.4.0, adaptive 1.3.0
material3 = "1.5.0-beta02"             # override BOM's 1.4.0 for M3 Expressive (public MotionScheme.expressive(), ButtonGroup, MaterialShapes, ...)
activityCompose = "1.13.0"
coreKtx = "1.19.1"
coreSplashscreen = "1.2.0"
lifecycle = "2.11.0"
navigation3 = "1.2.0"
navigationevent = "1.2.0"
datastore = "1.3.0"
profileinstaller = "1.4.1"
benchmark = "1.5.0"                    # androidx.baselineprofile plugin + benchmark-macro-junit4
uiautomator = "2.4.0"
graphicsShapes = "1.1.0"
kotlinxSerialization = "1.12.0"
kotlinxCoroutines = "1.11.0"
kotlinxCollectionsImmutable = "0.5.2"
# testing
junit4 = "4.13.2"
junitBom = "6.1.3"
kotest = "6.2.6"
robolectric = "4.17"
roborazzi = "1.77.0"
truth = "1.4.5"
turbine = "1.2.1"
androidxTestCore = "1.7.0"
androidxTestRunner = "1.7.0"
androidxTestRules = "1.7.0"
androidxTestExtJunit = "1.3.0"
androidxTestOrchestrator = "1.6.1"
espresso = "3.7.0"                     # force: ui-test-junit4 1.12.1 pulls 3.5.1, which crashes on SDK 37
# code quality
spotless = "8.10.4"
ktfmt = "0.64"
composeLintChecks = "1.6.1"
kover = "0.9.11"
# ksp = "2.3.12"                       # only if annotation processing is ever needed

[libraries]
# Compose (versions from BOM)
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-animation = { module = "androidx.compose.animation:animation" }
androidx-compose-runtime-tracing = { module = "androidx.compose.runtime:runtime-tracing" }
androidx-compose-material3-adaptive = { module = "androidx.compose.material3.adaptive:adaptive" }
# Material 3 (explicit override of the BOM)
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
# AndroidX
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "coreSplashscreen" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
androidx-navigationevent-compose = { module = "androidx.navigationevent:navigationevent-compose", version.ref = "navigationevent" }
androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
androidx-graphics-shapes = { module = "androidx.graphics:graphics-shapes", version.ref = "graphicsShapes" }
# Kotlin
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }
# Testing — JVM engine
junit-bom = { module = "org.junit:junit-bom", version.ref = "junitBom" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
kotest-property = { module = "io.kotest:kotest-property", version.ref = "kotest" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
# Testing — Android local (Robolectric) + instrumented
junit4 = { module = "junit:junit", version.ref = "junit4" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-test-rules = { module = "androidx.test:rules", version.ref = "androidxTestRules" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
androidx-test-orchestrator = { module = "androidx.test:orchestrator", version.ref = "androidxTestOrchestrator" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
androidx-test-uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }
androidx-benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
# Lint rules
compose-lint-checks = { module = "com.slack.lint.compose:compose-lint-checks", version.ref = "composeLintChecks" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-test = { id = "com.android.test", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
# NOTE: no org.jetbrains.kotlin.android — AGP 9 built-in Kotlin replaces it (applying it is an error).
```

Smoke-tested in a real build: agp, kotlin (jvm/compose/serialization plugins), composeBom, material3 1.5.0-beta02, activity-compose, core-ktx, core-splashscreen, lifecycle (runtime-compose, viewmodel-compose, viewmodel-navigation3), navigation3 runtime/ui, datastore + datastore-preferences, profileinstaller, kotlinx-serialization-json, kotlinx-collections-immutable, junit4, junit-bom/jupiter/launcher, kotest-property, robolectric, roborazzi (+compose, +junit-rule, plugin), truth, turbine, kotlinx-coroutines-test, androidx-test-ext-junit, espresso-core 3.7.0, ui-test-junit4/ui-test-manifest, spotless + ktfmt 0.64, compose-lint-checks, (detekt 2.0.0-alpha.6, compose-rules detekt 0.6.8 — not in the catalog above, optional).
Versions verified in metadata only (not exercised): `navigationevent-compose` (transitively present via activity-compose 1.13.0 at 1.0.0), `runtime-tracing`, `material3-adaptive`, `graphics-shapes` (1.0.1 transitively via M3), `androidx-test-core/runner/rules/orchestrator`, `uiautomator`, `benchmark-macro-junit4`, `baselineprofile`, `android-test`, `kover`, `kotest-assertions-core`.

## 11. Ready-to-adapt build snippets (smoke-verified)

`build.gradle.kts` (root)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
}
```

`app/build.gradle.kts` (essentials)
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}
android {
    namespace = "com.clayboicardi.game2048"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.clayboicardi.game2048"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    androidResources { generateLocaleConfig = true }   // + src/main/res/resources.properties: unqualifiedResLocale=en-US
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.jvmArgs(/* Robolectric --add-opens list, §6 */) } } }
}
kotlin { compilerOptions { optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi") } }
```

`core/engine/build.gradle.kts`
```kotlin
plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(platform(libs.junit.bom)); testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher); testImplementation(libs.kotest.property)
}
tasks.test { useJUnitPlatform() }
```

CI skeleton (`.github/workflows/ci.yml`): `actions/checkout@v7` -> `actions/setup-java@v6` (temurin, 21) -> `gradle/actions/setup-gradle@v6` ->
`./gradlew spotlessCheck lint :core:engine:test :app:testDebugUnitTest :app:assembleRelease` -> zipalign `-P 16` check + permission check -> `actions/upload-artifact@v7` (APK, Roborazzi diffs on failure).
Ubuntu runners need `platforms;android-37.0` (AGP auto-installs missing platforms/build-tools when licenses are accepted, which they are on GitHub-hosted runners).

---

## 12. Sources (primary)

- Gradle: https://services.gradle.org/versions/current, https://services.gradle.org/versions/all, https://docs.gradle.org/current/userguide/compatibility.html, https://docs.gradle.org/9.8.2/release-notes.html
- AGP: https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml, https://developer.android.com/build/releases/gradle-plugin, https://developer.android.com/build/releases/about-agp, https://developer.android.com/build/releases/agp-9-0-0-release-notes, https://developer.android.com/build/migrate-to-built-in-kotlin, https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/skill, https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/CompileSdkSpec
- Kotlin: https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml, https://kotlinlang.org/docs/releases.html, https://kotlinlang.org/docs/gradle-configure-project.html, https://developer.android.com/develop/ui/compose/compiler
- Compose/M3: https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom, https://developer.android.com/jetpack/androidx/releases/compose-material3, material3-android 1.4.0 / 1.5.0-beta02 `.aar` + `-sources.jar` + `.module`, https://developer.android.com/develop/ui/compose/graphics/images/material
- AndroidX releases: https://developer.android.com/jetpack/androidx/releases/navigation3, https://developer.android.com/jetpack/androidx/releases/datastore, https://developer.android.com/jetpack/androidx/releases/benchmark
- Platform: https://dl.google.com/android/repository/repository2-4.xml, https://developer.android.com/about/versions/17/setup-sdk, https://developer.android.com/about/versions/16/behavior-changes-16, https://developer.android.com/about/versions/17/behavior-changes-17, https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored, https://developer.android.com/about/versions/17/release-notes, https://developer.android.com/blog/posts/android-17-is-here
- Play: https://developer.android.com/google/play/requirements/target-sdk, https://developer.android.com/guide/practices/page-sizes
- Testing: https://robolectric.org/compatibility_table/, https://robolectric.org/getting-started/, https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17, https://github.com/robolectric/robolectric/issues/11434, https://github.com/takahirom/roborazzi/releases, https://github.com/mannodermaus/android-junit-framework
- Lint/format: https://detekt.dev/docs/introduction/compatibility/, https://github.com/detekt/detekt/releases, https://github.com/diffplug/spotless/releases, https://github.com/Kotlin/ktfmt/releases, https://github.com/cortinico/ktfmt-gradle/blob/main/CHANGELOG.md
- CI: https://github.com/actions/checkout/releases, https://github.com/actions/setup-java/releases, https://github.com/gradle/actions/releases, https://github.com/actions/upload-artifact/releases
