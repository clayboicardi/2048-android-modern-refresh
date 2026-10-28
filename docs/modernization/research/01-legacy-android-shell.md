# 01: Legacy Android shell audit (uberspot/2048-android fork)

Track: **legacy-shell**. Scope: every non-submodule file in `/home/user/2048-android-modern-refresh` at HEAD `e097460` (2021-05-14, "fixed issue #88 (add persian language)"), branch `claude/2048-modernization-plan-8eneix`. The web game (submodule `assets/2048` @ `7a477eb`) is out of scope. It is cited here only where it interacts with the shell: state persistence, `?lang=`, and i18n coverage.

Method: I read every file. I traced the `git log -S` history for the fullscreen, onResume and rotation code. I computed the vector geometry and sampled PNG pixels with scripts. API deprecation levels come from the SDK's own `platforms/android-37.0/data/api-versions.xml`. I checked fast-moving facts against live primary sources (URLs in §11).

---

## 0. TL;DR (plan-relevant)

1. The shell is a 177-line `Activity` hosting a `WebView` that loads `file:///android_asset/2048/index.html?lang=<iso639>`. All gameplay, persistence (WebView `localStorage`), night mode and undo live in JS. Only these features belong to the shell: **long-press fullscreen toggle (persisted)**, **start-up hint toast**, **double-back-to-exit (3500 ms)**, **portrait lock with sensor rotation on large/xlarge screens when auto-rotate is on**, **first-run/upgrade changelog dialog**, and the **onResume full reload**.
2. **The fullscreen flag naming is inverted.** `isFullScreen()` defaults to `true`, and `applyFullScreen(true)` *clears* `FLAG_FULLSCREEN`, so `true` means "status bar shown". The behavior is self-consistent: the first launch shows the status bar and a long press hides it. Only the name is wrong, and it has been since 2014 (`8a04f5d`).
3. **The onResume reload does not wipe a mid-game board.** The JS saves `gameState` to `localStorage` on every `actuate()`. It does cause these defects:
   - It loads twice on every cold start.
   - It replays the "appear" animation on all tiles, plus a "+<whole score>" float-up.
   - It drops open confirm overlays.
   - It silently replaces a **Game over** screen with a new game.
   - It reverts an undo that was not followed by a move.
   - It pushes a duplicate undo entry, so the first Undo after each resume does nothing.
   - It resets night mode.
   - It makes the `restoreState` branch dead code.
4. **The legacy project cannot be built today.**
   - Gradle 4.10.3 cannot run on JDK 21. Java 21 needs Gradle 8.5 or later.
   - AGP 3.2.1 is incompatible with Gradle 8.
   - `jcenter()` is gone: redirected to Central in Aug 2024 and the API removed in Gradle 9.0.
   - The only dependency, `ckchangelog-legacy-dialog:2.0.0-SNAPSHOT`, lived on oss.sonatype.org. OSSRH shut down on 2025-06-30, and the artifact was never on Maven Central (verified 404).

   The plan's decision to do a greenfield native rewrite is the only viable path. No build-fix PR is worth making.
5. **What is worth migrating:**
   - 20 locales × 4 shell strings. Only `app_name` and `action_settings` ("Settings") are directly reusable.
   - 11 web i18n keys × en/ru/uk, plus 2 CSS-hardcoded labels ("Score", "Best").
   - The 9-release changelog history, for credits/About.
   - The "2048" glyph vector: 4 paths, `#aa8800`, exactly 75 % × 25 % of the viewport, centered.
   - The fastlane/F-Droid metadata skeleton (29 locale dirs). The text in it is mostly machine-translated and wrong, and must be rewritten.
6. **Platform landmines for the native app (target 36 / compile 36–37):**
   - `onBackPressed` is not called on Android 16+.
   - Edge-to-edge cannot be opted out of.
   - On screens of sw ≥ 600dp, `screenOrientation`/`setRequestedOrientation` are ignored. The exception is **games flagged via `android:appCategory`**, and the developer opt-out is removed when targeting 37.
   - AndroidX stable now requires **minSdk 23** (verified from the AAR manifests).

---

## 1. File inventory (non-submodule)

| Path | Size / lines | Role | Verdict for rewrite |
|---|---|---|---|
| `build.gradle` | 1521 B / 67 | Single-module AGP 3.2.1 build, Eclipse-style source sets | Replace (settings.gradle.kts + version catalog + `app/` module) |
| `gradle/wrapper/gradle-wrapper.properties` | 203 B / 5 | `gradle-4.10.3-all.zip` | Replace (8.14.3, add `distributionSha256Sum`) |
| `gradle/wrapper/gradle-wrapper.jar` | 56 177 B | Manifest says `Implementation-Version: 4.10.2` (mismatch with the properties file) | Regenerate |
| `gradlew`, `gradlew.bat` | 172 / 84 lines | 4.x-era scripts | Regenerate |
| `.travis.yml` | 336 B / 23 | `oraclejdk8`, build-tools 28.0.3, android-28, `./gradlew build` | Delete; GitHub Actions |
| `.gitmodules` | 90 B / 3 | submodule `assets/2048` → `https://github.com/uberspot/2048.git` | Delete (`git rm assets/2048`, clean `.git/modules`) |
| `.gitignore` | 309 B / 26 | Eclipse/IntelliJ/Gradle ignores and `assets/2048/*` (ignoring a submodule's contents has no effect) | Replace with a modern Android ignore |
| `AndroidManifest.xml` | 801 B / 22 | 1 activity, no permissions | Replace (see §3) |
| `src/com/uberspot/a2048/MainActivity.java` | 6638 B / 177 | The entire shell | Replace (see §4) |
| `res/layout/activity_main.xml` | 12 lines | `LinearLayout(fill_parent)` > `WebView#mainWebView(fill_parent)` | Drop |
| `res/menu/main.xml` | 9 lines | Empty `<menu>` (no `xmlns:android`), commented-out `action_settings` item | Drop |
| `res/drawable/ic_2048.xml` | 2573 B / 30 | Launcher icon vector (see §5.3) | Mine geometry → adaptive icon |
| `res/raw/changelog_master.xml` | 2360 B / 60 | ckChangeLog source, 9 releases (see §5.2) | Mine → About/credits |
| `res/values/styles.xml` | 20 lines | `AppBaseTheme` ← `android:Theme.Light`; `AppTheme` ← `AppBaseTheme`, both empty | Drop (M3 theme) |
| `res/values/dimens.xml` | 7 lines | `activity_horizontal_margin` 16dp, `activity_vertical_margin` 16dp, **unused** | Drop |
| `res/values-sw720dp-land/dimens.xml` | 9 lines | `activity_horizontal_margin` 128dp (10" tablets, landscape), **unused** | Drop (idea: wide gutters on expanded width) |
| `res/values/strings.xml` + 20 `values-xx/strings.xml` | 4 keys each | See §5.1 | Partially migrate |
| `metadata/<29 locales>/…` | see §6 | F-Droid/fastlane-style store listing | Restructure and rewrite |
| `README.md` | 48 lines | WebView port blurb, Travis badge, Play/F-Droid badges for `com.uberspot.a2048`, Eclipse/AS build steps | Rewrite |
| `LICENSE` | MIT, "Copyright (c) 2014 Paul Sarbinowski" | Keep the notice (MIT requires it if any portion is reused, e.g. translations or icon paths); add your own copyright line |

Submodule license (for attribution of the reimplemented game): `assets/2048/LICENSE.txt` is MIT, "Copyright (c) 2014 Gabriele Cirulli".

---

## 2. Build, toolchain and CI (legacy)

`build.gradle` (line refs):
- `:2-5` and `:14-18`: repositories `google()`, `jcenter()`, `maven { url "https://oss.sonatype.org/content/repositories/snapshots/" }`.
- `:9`: `classpath 'com.android.tools.build:gradle:3.2.1'`.
- `:24-25`: `compileSdkVersion 28`, `buildToolsVersion '28.0.3'`.
- `:28-32`: `applicationId "com.uberspot.a2048"`, `minSdkVersion 14`, `targetSdkVersion 28`, `versionCode 25`, `versionName "2.2"`.
- `:35-45`: legacy Eclipse layout. `manifest.srcFile 'AndroidManifest.xml'`; java/resources/aidl/renderscript = `src`; res = `res`; assets = `assets`.
- `:47-52`: release `minifyEnabled false` plus `proguardFiles … 'proguard-rules.pro'`. **That file does not exist.**
- `:54-56`: `lintOptions { disable 'GoogleAppIndexingWarning' }`.
- `:58-62`: Java 1.8, UTF-8.
- `:66`: `implementation 'de.cketti.library.changelog:ckchangelog-legacy-dialog:2.0.0-SNAPSHOT'`, the only dependency.

Live status (2026-10-28):

| Item | Status | Evidence |
|---|---|---|
| Gradle 4.10.3 on JDK 21 | **Cannot run.** Java 11 needs Gradle ≥ 5.0; Java 21 needs ≥ 8.5; Java 25 needs ≥ 9.1.0. Current Gradle is 9.8.2 (requires JVM 17–27) | docs.gradle.org compatibility matrix |
| Gradle 8.14.3 (locked) on JDK 21 | OK (21 supported from 8.5). 8.14.x is the last line that runs on Java 8–16 | same |
| `jcenter()` | Deprecated in Gradle 7.0, **removed in Gradle 9.0.0**. JCenter was redirected to Maven Central in Aug 2024 | Gradle 9 upgrade guide; Gradle blog |
| OSSRH snapshots repo | **Shut down 2025-06-30**; replaced by Central Portal (`central.sonatype.com/repository/maven-snapshots/`) | central.sonatype.org/pages/ossrh-eol |
| `ckchangelog-legacy-dialog` | **404 on Maven Central**. Only `de.cketti.library.changelog:ckchangelog` 1.2.0/1.2.1/1.2.2 exist (lastUpdated 20150109). The 2.0.0-SNAPSHOT was never released | `curl repo1.maven.org/.../maven-metadata.xml` (run in this session) |
| AGP 3.2.1 POM | Still downloadable from dl.google.com (HTTP 200), but it cannot work with Gradle 8 or JDK 17+ | curl (this session) |
| Travis CI | travis-ci.org was disabled in June 2021; the README badge is dead | docs.travis-ci.com migration page, plus secondary reports |
| Play target API | **New apps and updates must target API 36 from 2026-08-31.** Existing apps need API 35 to stay available to new users; an extension to 2026-11-01 is available on request | developer.android.com/google/play/requirements/target-sdk |
| AndroidX minSdk | Latest stable AARs declare `minSdkVersion="23"`: `compose.ui:ui-android` 1.12.1, `core:core` 1.19.1, `activity:activity-compose` 1.13.0 (latest prereleases: ui 1.13.0-beta02, activity-compose 1.14.0-alpha03) | AAR manifests downloaded from dl.google.com in this session |

Consequence: **minSdk 14 → at least 23**, forced by AndroidX. Below 23 there are no Compose or AndroidX updates.

---

## 3. AndroidManifest.xml (legacy)

| Line | Content | Note |
|---|---|---|
| 3 | `package="com.uberspot.a2048"` | AGP 8 requires `namespace` in the build script instead |
| 6 | `android:allowBackup="true"` | No `fullBackupContent` / `dataExtractionRules`. Auto Backup (API 23+) would include WebView data by default |
| 7 | `android:icon="@drawable/ic_2048"` | Non-adaptive vector in `drawable/` (not `mipmap/`); no `roundIcon` |
| 8 | `android:label="@string/app_name"` | |
| 9 | `android:theme="@style/AppTheme"` | → `android:Theme.Light` (pre-Holo, API 1 theme) |
| 11 | `.MainActivity` | |
| 12 | `android:configChanges="orientation\|keyboardHidden\|screenSize"` | Rotation does not recreate the activity. Missing `screenLayout\|smallestScreenSize\|density\|uiMode\|locale\|layoutDirection`, so multi-window resize, fold/unfold, dark-mode switch and locale change all recreate it |
| 14 | `android:screenOrientation="portrait"` | Overridden at runtime on large/xlarge (see §4.5) |
| 15-19 | `MAIN`/`LAUNCHER` intent filter | **No `android:exported`.** Targeting 31+ makes this a manifest-merger build error, and the app "can't be installed on Android 12+" |

Absent: `<uses-permission>` (zero permissions, matching the README claim), `supportsRtl` (even though `fa` is translated), `appCategory`, `localeConfig`, `enableOnBackInvokedCallback`, `resizeableActivity`.

---

## 4. MainActivity behavior inventory (`src/com/uberspot/a2048/MainActivity.java`)

### 4.1 Fields and constants
- `:27` `MAIN_ACTIVITY_TAG = "2048_MainActivity"`
- `:30` `mLastBackPress` (long, initially 0)
- `:31` `mBackPressThreshold = 3500` (ms)
- `:32` `IS_FULLSCREEN_PREF = "is_fullscreen_pref"`
- `:33` `mLastTouch`
- `:34` `mTouchThreshold = 2000` (ms)
- `:35` `pressBackToast`
- `:37`: `@SuppressLint({"SetJavaScriptEnabled","ShowToast","ClickableViewAccessibility"})` on `onCreate`.

### 4.2 Window setup (onCreate order)
1. `:43` `requestWindowFeature(Window.FEATURE_NO_TITLE)`: no title or action bar. The store screenshots predate this; they show an action bar.
2. `:46-47` `setFlags(FLAG_HARDWARE_ACCELERATED)`: redundant, since it is the default for targetSdk ≥ 14.
3. `:50` `applyFullScreen(isFullScreen())`: applies the persisted bar visibility before `setContentView`.
4. `:53-68` orientation logic (§4.5).
5. `:70` `setContentView(R.layout.activity_main)`.
6. `:72-75` changelog dialog (§4.6).
7. `:78-84` WebView settings (§4.7).
8. `:87-92` restore or load (§4.8).
9. `:94` toast `R.string.toggle_fullscreen` ("Long click to toggle fullscreen"), `LENGTH_SHORT`, using `getApplication()` as context. It is shown on **every** `onCreate`: every cold start and every recreation (locale or dark-mode change, multi-window resize, fold).
10. `:96-111` long-press listener (§4.3).
11. `:113-114` creates (does not show) `pressBackToast` with `R.string.press_back_again_to_exit`, `LENGTH_SHORT`.

### 4.3 Long-press fullscreen toggle (`:96-111`)
- `OnTouchListener` on the WebView. On `ACTION_DOWN` it records `mLastTouch = System.currentTimeMillis()`. On `ACTION_UP`, if `Math.abs(now - mLastTouch) > 2000`, it toggles: `toggled = !isFullScreen(); saveFullScreen(toggled); applyFullScreen(toggled)`.
- It returns `false`, so the WebView (and the JS game) still receives the events.
- Consequences and defects:
  - The toggle fires **on release**, not at 2000 ms. There is no haptic or visual feedback.
  - **It conflicts with gameplay.** A slow swipe lasting more than 2 s both moves the tiles (JS `touchend`) *and* toggles fullscreen. The JS buttons bind `touchend` (`keyboard_input_manager.js:162`), so holding "New Game" for 2 s opens the restart prompt *and* toggles fullscreen.
  - It uses the wall clock (`System.currentTimeMillis`, not `event.getEventTime()`/uptime), and `Math.abs` masks clock jumps.
  - It does not handle `ACTION_CANCEL`. `getAction()` is unmasked, so pointer events are simply ignored, which is fine.
  - Accessibility: the `ClickableViewAccessibility` lint is suppressed and there is no alternative path. TalkBack users cannot toggle fullscreen.
  - The store copy says "Long touch for **3+ seconds**" (`metadata/en-US/full_description.txt`), but the code uses **2000 ms**.

### 4.4 Persisted preference and the inverted naming
- Storage: `PreferenceManager.getDefaultSharedPreferences(this)`, which is the file `shared_prefs/com.uberspot.a2048_preferences.xml`, key `is_fullscreen_pref` (boolean). It is written with `apply()` (`:134-139`) and read with default **`true`** (`:141-144`). `android.preference.PreferenceManager` has been deprecated since API 29.
- `applyFullScreen(boolean isFullScreen)` (`:151-158`):

| Pref value | `applyFullScreen(arg)` | Window op | Visible result |
|---|---|---|---|
| absent (first run) → default `true` | `true` | `clearFlags(FLAG_FULLSCREEN)` | **Status bar shown** (not fullscreen) |
| `true` | `true` | `clearFlags(FLAG_FULLSCREEN)` | Status bar shown |
| `false` (after first long press) | `false` | `setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN)` | **Status bar hidden** (fullscreen) |

- **Verdict: the naming is inverted.** `isFullScreen()==true` actually means "not fullscreen" (semantically `showStatusBar`). Toggling is internally consistent, so users never see a bug. History: the inverted `applyFullScreen` dates from `8a04f5d` (2014-08-20, uberspot). `b6334dd` (2018-12-10) only inlined `DEF_FULLSCREEN = true` → `true`; the default was always "bars shown".
- `FLAG_FULLSCREEN` has been deprecated since API 30 (api-versions.xml). Toggling it at runtime resizes the WebView by the status-bar height, causing a visible layout jump.
- **Migration:** none is possible. The new `applicationId` gets a new sandbox. The native setting should be correctly named (`immersiveMode`, default `false`).

### 4.5 Orientation (`AndroidManifest.xml:14` and `MainActivity.java:53-68`)
- The default is the manifest lock to `portrait`.
- At `onCreate`, the code reads `Settings.System.ACCELEROMETER_ROTATION` **once** (`:55-56`; `SettingNotFoundException` is logged at `:58`). If the screen size bucket (`screenLayout & SCREENLAYOUT_SIZE_MASK`) is `LARGE` or `XLARGE` **and** auto-rotate is on, it calls `setRequestedOrientation(SCREEN_ORIENTATION_SENSOR)` (`:67`).
- Phones (SMALL/NORMAL) are always portrait, even with auto-rotate on.
- Defects:
  - **Stale.** There is no `ContentObserver`, so toggling the system rotation lock while the app is open is ignored until the activity is recreated.
  - `SENSOR` itself ignores the user's lock. `SCREEN_ORIENTATION_FULL_USER`/`USER` would honor the lock natively, with no Settings read.
  - It relies on the deprecated size buckets instead of window size classes.
  - Because `configChanges` covers `orientation|screenSize`, rotation reflows the WebView instead of recreating the activity.
- **Native target platform:** for apps targeting 36, `screenOrientation`, `resizeableActivity`, `min/maxAspectRatio` and `setRequestedOrientation` are **ignored on displays with sw ≥ 600dp**. Exceptions: **"Games (based on the `android:appCategory` flag)"**, users who opt in via aspect-ratio settings, and screens < sw600dp. The temporary developer opt-out (`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`) **is removed for apps targeting 37**.

### 4.6 First-run / upgrade changelog dialog (`:72-75`)
- Code: `DialogChangeLog.newInstance(this)`, then `if (changeLog.isFirstRun()) changeLog.getLogDialog().show()`. The source is `res/raw/changelog_master.xml`.
- Semantics (ckChangeLog 1.x API, assumed unchanged in the 2.0 snapshot; *unverified*): `isFirstRun()` is true on the first launch of each new `versionCode`, which covers both a fresh install and an update. `getLogDialog()` shows the changes since the last stored version code; on a fresh install it shows the full log.
- Issues:
  - It pops a blocking dialog on a fresh install, which is a poor first-run experience.
  - It is English-only: there are no localized `raw` variants.
  - The `~~…~~` strike markup in the 2.1 entry is probably shown verbatim.
  - It is styled by `android:Theme.Light`, which looks Gingerbread-era.
  - It shows in `onCreate` before the WebView is configured.
  - The dependency cannot be resolved today (§2).

### 4.7 WebView settings (`:78-84`)

| Line | Call | Purpose / status |
|---|---|---|
| 80 | `setJavaScriptEnabled(true)` | Required by the game |
| 81 | `setDomStorageEnabled(true)` | Required: the game persists `bestScore`, `gameState`, `gameStatesStack` in `localStorage` (`local_storage_manager.js:22-24`). Without it, the game falls back to in-memory `fakeStorage` |
| 82 | `setDatabaseEnabled(true)` | WebSQL; unused by the game; **deprecated API 35** |
| 83 | `setRenderPriority(RenderPriority.HIGH)` | **Deprecated API 18**, no-op |
| 84 | `setDatabasePath(getFilesDir().getParentFile().getPath()+"/databases")` | **Deprecated API 19**, no-op |

- Not set:
  - No `WebViewClient`, so any navigation would open an external browser. The page has no links.
  - No `setTextZoom`, so the system font scale inflates the CSS layout.
  - No `setAllowFileAccess`. That is fine: `file:///android_asset` is exempt even though the default flipped to `false` at target 30.
  - No JS interface (good).
- `index.html:17` viewport uses `target-densitydpi=160dpi` (ignored by Chromium) and `user-scalable=no`.

### 4.8 saveState / restoreState (`:87-92`, `:123-126`)
- `onSaveInstanceState(outState)` → `mWebView.saveState(outState)`. It **does not call `super.onSaveInstanceState`**, so view-hierarchy state is lost (lint `MissingSuperCall`). `WebView.saveState` only stores the back/forward list, not page or JS state.
- `onCreate`: if `savedInstanceState != null`, it calls `restoreState`; otherwise it calls `loadUrl(index.html?lang=…)`.
- **The restore branch is effectively dead.** `onResume()` always runs after `onCreate`/`onStart` and immediately issues a fresh `loadUrl` (§4.9).

### 4.9 `onResume()` reload (`:117-121`): does it wipe in-progress state?
Code: `mWebView.loadUrl("file:///android_asset/2048/index.html?lang=" + Locale.getDefault().getLanguage())` on **every** resume. It was added by `b5af619` (2018-10-19) as a blunt fix for "language changed while minimized". It replaced a `TODO` in the restore branch.

Trace through the JS (submodule `js/game_manager.js`, `js/local_storage_manager.js`, `js/html_actuator.js`):
- On every move, `GameManager.actuate()` (`game_manager.js:123-144`) runs `setBestScore` if higher. If `over`, it runs `clearGameState()`, which removes `gameState` **and** `gameStatesStack`. Otherwise it runs `setGameState(serialize())` and then `pushGameState(serialize())`.
- On page load, `setup()` (`:79-103`) runs `getGameState()`. If it exists, it rebuilds the grid, score, over, won and keepPlaying; otherwise it starts a new game with 2 random tiles. It then calls `actuate()`, which **pushes the restored state onto the undo stack again**.

Results:

| Situation at pause | After resume reload | Data loss? |
|---|---|---|
| Mid-game (after a move) | Board, score, best, won/keepPlaying restored | **No** |
| Mid-game, after Undo with no subsequent move | `undoMove()` (`:27-53`) calls `this.actuator.actuate`, **not** `this.actuate`, so the undone state was never persisted. The reload restores the **pre-undo** board, while the stack was already popped | **Yes: undo reverted, history desynced** |
| Game over screen showing | State was cleared on game over, so the reload **starts a new game silently**. The final score and "Game over!" disappear (best score survives) | **Yes: end screen lost** |
| Won, before "Keep going" | Win overlay re-shown (800 ms fade after a 1200 ms delay) | No |
| Restart/Undo confirmation open | Overlay gone | Transient UI lost |
| Night mode on (`nightmode.js`, inline style only, never persisted) | Back to light `#faf8ef` | Yes (preference) |
| Any | A duplicate entry is pushed onto `gameStatesStack` (`[…, A, B]` → `[…, A, B, B]`). The next Undo pops B, finds it equal to the current state, pops again and gets **B again**, so the **first Undo after each resume is a no-op**. Duplicates also consume the ~50-entry cap (`local_storage_manager.js:98`) | History quality |
| Any | All tiles re-render as `tile-new` and replay the `appear` 200 ms animation (100 ms delay). `HTMLActuator.score` starts at 0, so `updateScore` floats **"+<entire score>"** (`move-up` 600 ms). There is also a full page-reload flash | Visual glitch |
| Cold start | `onCreate` `loadUrl` and then `onResume` `loadUrl` load the page **twice** | Wasted work |

Answer: **in-progress board and score are not wiped.** `localStorage` persists across reloads and process death. The reload does lose end-of-game screens, un-persisted undos, overlays and night mode, and it corrupts undo history and replays animations. The native app should persist on every committed move and must never rebuild the UI from scratch on resume.

### 4.10 Locale passing via `?lang=` (`:91`, `:120`)
- The value is `Locale.getDefault().getLanguage()`: a bare ISO-639 code, unencoded, and only `language` (no region or script). On Android, `getLanguage()` returns legacy codes for he/id/yi (`iw`/`in`/`ji`), which is irrelevant here but a known pitfall.
- The web side, `js/i18n.js`, recognizes **only `ru` and `uk`**. Everything else gets English. So for 18 of the 20 shell locales, the in-game UI was English while the toasts were localized.
- The "Score" and "Best" labels are CSS `content:` (`style/main.scss:110,114`), so they are **never** localized, even in ru/uk.

### 4.11 Double-back-to-exit (`:166-176`)
- First press (or more than 3500 ms since the last): shows `pressBackToast` ("Press back again to exit") and records the time.
- A second press within 3500 ms calls `pressBackToast.cancel()` and then `super.onBackPressed()`.
- Defects:
  - The toast is `LENGTH_SHORT` (≈2000 ms) while the window is 3500 ms (= `LENGTH_LONG`), so the hint disappears about 1.5 s before the window closes.
  - It uses the wall clock.
  - `onBackPressed` has been deprecated since API 33. For apps **targeting 36 running on Android 16+, `onBackPressed` is not called** and `KEYCODE_BACK` is not dispatched, so the feature would silently vanish. The temporary opt-out is `android:enableOnBackInvokedCallback="false"`.
  - It defeats the predictive back-to-home animation.
- Since the game state autosaves, an accidental exit loses nothing, so this guard has no purpose.

### 4.12 Other
- `Log.d` is only used on the settings failure.
- No `onPause`/`onDestroy` WebView lifecycle calls (`onPause()`/`pauseTimers()`/`destroy()`).
- No `onConfigurationChanged` override; the 2014 empty one was removed.

---

## 5. Resources

### 5.1 Strings: every locale and its keys
All 21 files have exactly the same 4 keys: `app_name`, `action_settings`, `press_back_again_to_exit`, `toggle_fullscreen`. There are no plurals or arrays. **`action_settings` is never displayed:** the only menu item that used it is commented out.

| Locale | app_name | action_settings | press_back_again_to_exit | toggle_fullscreen | Issues |
|---|---|---|---|---|---|
| default (en) | 2048 | Settings | Press back again to exit | Long click to toggle fullscreen | |
| cs | 2048 | Nastavení | Pro odchod stisknětě znovu tlačítko zpět | Dlohým kliknutím přepnete na celou obrazovku | **UTF-8 BOM**; typos "stisknětě"→"stiskněte", "Dlohým"→"Dlouhým" |
| da | 2048 | Indstillinger | Tryk tilbage igen for at slutte | Hold knappen inde for at skifte til fuld skærm | no XML declaration (harmless) |
| de | 2048 | Einstellungen | Drücke nochmal Zurück zum Beenden | Lange drücken, um Vollbildschirm anzuzeigen | "Vollbildschirm" is non-standard (Android de uses "Vollbild") |
| el | 2048 | Ρυθμίσεις | Πατήστε \'πίσω\' ξανά για έξοδο | Πατήστε για ώρα την οθόνη για να μεγιστοποίηση/σμίκρυνση παραθύρου | awkward grammar |
| eo | 2048 | Agordoj | Premu reen denove por eliri | Premu longe por baskuligi tutekrane | |
| es | 2048 | Ajustes | Pulse "Atrás" de nuevo para salir | Pulsación larga para alternar pantalla completa | |
| fa | **۲۰۴۸** (Persian digits) | تنظیمات | دوباره کلیک کنید تا از بازی خارج شوید | کلیک کنید و نگه دارید تا صفحه فول اسکرین شود | label conflicts with locked "2048"; "فول اسکرین" is a transliteration |
| fr | 2048 | Paramètres | Appuyez encore sur retour pour quitter | Appuyez longtemps pour passer en mode plein écran | |
| hu | 2048 | Beállítások | A kilépéshez nyomja meg kétszer a vissza gombot | Hosszú kattintás a teljes képernyőre váltáshoz | |
| it | 2048 | Impostazioni | Premere ancora indietro per uscire | Tenere premuto per attivare e disattivare il fullscreen | English "fullscreen" |
| ko | 2048 | 설정 | \'뒤로\' 버튼을 한번 더 누르시면 종료됩니다. | 화면 모드 변경을 하려면 화면을 길게 누르세요. | |
| lt | 2048 | Nustatymai | Paspauskite atgal norėdami išeiti | Paspauskite ilgiau norėdami įjungti pilną ekraną | missing "again" (dar kartą) |
| nl | 2048 | Instellingen | Druk opnieuw op terug om af te sluiten | Druk lang in om volledig scherm om te schakelen | |
| pl | 2048 | Ustawienia | Naciśnij ponownie, aby wyjść | Trzymaj nacisk na pare sekund, aby zmienic na pełny ekran | missing diacritics "parę", "zmienić" |
| pt | 2048 | Configurações | Pressione voltar novamente para sair | Pressione e segure para entrar no modo de tela cheia | Brazilian wording in generic `pt` |
| ru | 2048 | Настройки | Нажмите кнопку [Назад] ещё раз, чтобы выйти | Долгое нажатие переключает полноэкранный режим | |
| sl | 2048 | Nastavitve | Ponovni pritisk nazaj konča program. | Z dolgim pritiskom se preklopi celozaslonski način. | |
| sr | 2048 | Подешавања | Притисните поново тастер за враћање ради изласка из апликације | Додирните и задржите прст на екрану за пребацивање апликације у режим пуног екрана | Cyrillic |
| tr | 2048 | Ayarlar | Çıkmak için tekrar geriye basın | Tam ekrana geçmek için uzun tıklayın | |
| uk | 2048 | Налаштування | Натисніть назад ще раз, щоб вийти | Довге натиснення для того, щоб перейти у повноекранний режим | |

Encoding: all files are UTF-8 with no CRLF. `cs` has a BOM. `eo`, `it`, `nl` and the default are pure ASCII.

### 5.2 `res/raw/changelog_master.xml`: full version history

| versionName | versionCode | Entries (verbatim summary) |
|---|---|---|
| 2.2 | 25 | Bumped minSdk 8 (Froyo) → 14 (ICS); updated changelog library (breaking changes); (2.1 items:) detect and use the phone's system language (BValeo); Slovenian (urmatej); Czech (Mongata); Italian (RyuzakiKK) |
| 2.1 | 24 | "~~Bad build~~": crashed on startup after the project rearrangement ("*cough* Taco *cough*"); changes rolled into 2.2 |
| 2.08 | 23 | Esperanto and Dutch (Robin van der Vliet); fixed a bug with floating windows (diogopereira) |
| 2.06 | 22 | Undo limited to the last 50 moves to save memory |
| 2.05 | 21 | Added in-app changelog |
| 2.0 | 20 | Added undo button (fraggerfox) |
| 1.96 | 19 | Rebuilt APK with new buildTools |
| 1.95 | **18** | German, Spanish, Polish and Serbian translations; experimental night mode button; responsiveness optimizations |
| 1.91 | **18** | Greek and French; minor build improvements. **Duplicate versioncode 18** |

Unreleased after 2.2 (in git; `build.gradle` still 25/2.2): Hungarian (`825e39f`, 2019-10-16), the new vector icon replacing the `drawable-*dpi/ic_launcher.png` board icon (`cca7106`, 2020-01-10), Turkish (`5432be9`, 2021-01-27), Persian (`e097460`, 2021-05-14). Feature lineage that the native app must at least match: **undo (bounded history), night mode, system-language localization, multi-window ("floating windows") support**.

### 5.3 `res/drawable/ic_2048.xml`: icon geometry
- `<vector>` declared 200dp × 200dp, viewport 52.916 × 52.916. This is an Inkscape export: 52.916 mm = 200 px at 96 dpi.
- 4 `<path>`s, one per glyph "2", "0", "4", "8". Each has `fillColor="#aa8800"`, `fillAlpha="1"`, `strokeColor="#00000000"` (invisible) and `strokeWidth="0.29534554"`. There is **no background**. The glyphs are a bold rounded sans resembling Clear Sans Bold (the game's font); "0" and "8" have counters drawn with nested subpaths.
- Computed bounding boxes (viewport units, including Bézier control points):
  - "2": x 6.614–15.402, y 19.843–32.832
  - "0": x 16.947–25.830, y 19.843–33.072
  - "4": x 26.912–36.356, y 20.084–32.832
  - "8": x 37.611–46.301, y 19.843–33.072
  - **Union: 39.687 × 13.229 = exactly 75.0 % × 25.0 % of the viewport, centered at (26.458, 26.458), the exact viewport center.** Gaps between glyphs: 1.545 / 1.082 / 1.255.
- Color: `#aa8800` is HSL(48°, 100 %, 33.3 %). Contrast: 3.37:1 vs white, 3.17:1 vs `#faf8ef`, 6.23:1 vs black, 5.56:1 vs `#121212`, 1.54:1 vs board `#bbada0`, 1.98:1 vs 2048-gold `#edc22e`. It fails 4.5:1 on light launchers.
- **The store icon does not match** the launcher icon since 2020: `metadata/en-US/icon.png` and the pre-2020 `ic_launcher.png` are a 4×4 mini-board. Sampled colors from that icon:

  | Element | Hex |
  |---|---|
  | board / gaps | `#bbada0` |
  | 2 | `#eee4da` |
  | 4 | `#ede0c8` |
  | 8 | `#f2b179` |
  | 16 | `#f59563` |
  | 128 | `#edcf72` |
  | dark digits | `#776e65` |
  | light digits | `#f9f6f2` |
  | rounded transparent corners | – |

- Adaptive-icon translation:
  - The spec is a 108 × 108 dp canvas with a 66 dp safe zone; the logo should be 48–66 dp.
  - A 3:1 band fits in a 66 dp circle at width ≤ 62.6 dp. **Recommended:** band width 56 dp → scale 1.4110 dp/unit, height 18.67 dp, diagonal 59.0 dp. In a 108-unit viewport use `<group android:scaleX="1.411" android:scaleY="1.411" android:translateX="16.667" android:translateY="16.667">` around the 4 legacy paths.
  - The same paths are the `<monochrome>` layer (themed icons, API 33+; Android 16 QPR2 auto-themes icons that lack one).
  - The legacy (API 23–25) fallback can be a plain vector in `mipmap-anydpi` with the adaptive one in `mipmap-anydpi-v26`.

### 5.4 Styles, dimens, layout, menu
- `styles.xml`: `AppBaseTheme` (parent `android:Theme.Light`) and `AppTheme`, both empty. There are no `values-v11`/`v14` overrides, despite the template comment. Dialogs and toasts render with the API-1 light theme. There is no dark theme.
- `dimens.xml`: 16dp/16dp margins. `values-sw720dp-land/dimens.xml`: horizontal 128dp. **Neither is referenced** (ADT template leftovers).
- `activity_main.xml`: a `LinearLayout` with `fill_parent` (the deprecated alias of `match_parent`) wrapping a single `WebView`. This is a lint `UselessParent`.
- `menu/main.xml`: an empty `<menu>` with no namespace.

---

## 6. Store metadata (`metadata/`)

Layout: `metadata/<locale>/…`. This is one of the two in-repo locations F-Droid reads (`metadata/<locale>/` or `fastlane/metadata/android/<locale>/`). Commit history: `39d08f5` "move graphics into standard F-Droid/fastlane paths" and `ff7754b` (2017-07-17, Hans-Christoph Steiner), `e49fa8a`, `1d12c0a` (fr fix 2019-01-09).

Locales (29): af, am, ar, bg, ca, de, el, **en-US**, es, et, fi, fr, hi, id, it, ja, ko, lt, lv, pt-BR, pt-PT, ro, ru, sv, sw, th, tr, uk, vi.
- Every locale has `short_description.txt` only. 26 lack a trailing newline.
- `en-US` additionally has `full_description.txt`, `icon.png`, `featureGraphic.png`, `promoGraphic.png`, `phoneScreenshots/screen1.png` and `sevenInchScreenshots/screen_tablet.png`.
- There is no `title.txt`, no `changelogs/`, no `images/` subfolder and no `video.txt` anywhere.

Locale overlap with in-app strings:
- Both: de, el, es, fr, it, ko, lt, pt(-BR/-PT), ru, tr, uk.
- In-app only (no listing): cs, da, eo, fa, hu, nl, pl, sl, sr.
- Listing only (no in-app strings): af, am, ar, bg, ca, en-US, et, fi, hi, id, ja, lv, ro, sv, sw, th, vi.

Short descriptions (character count; all ≤ 80). They are mostly machine translations of "Android open-source port of the '2048' puzzle game", and **"port" was often translated as harbour**:

| Locale | Chars | Text | Problem |
|---|---|---|---|
| en-US | 51 | Android open-source port of the "2048" puzzle game. | obsolete framing |
| af | 56 | 2048 legkaart spel. Dit is die Android open source hawe. | "hawe" = harbours |
| am | 45 | 2048 እንቆቅልሽ ጨዋታ. ይህ የ Android ክፍት ምንጭ ወደብ ነው. | "ወደብ" = harbour |
| ar | 50 | 2048 لعبة اللغز. هذا هو الروبوت منفذ مفتوح المصدر. | "الروبوت" = robot (for Android) |
| bg | 52 | 2048 Puzzle Game. Това е Android с отворен код порт. | untranslated "Puzzle Game" |
| ca | 68 | 2048 Joc de Trencaclosques. Aquest és el port de codi obert Android. | ambiguous |
| de | 57 | 2048 Puzzle-Spiel. Dies ist die Open-Source-Android-Port. | gender error ("der Port") |
| el | 61 | 2048 παιχνίδι παζλ. Αυτό είναι το Android λιμάνι open source. | "λιμάνι" = harbour |
| es | 66 | 2048 Juego de Puzzle. Este es el puerto de código abierto Android. | "puerto" = harbour |
| et | 60 | 2048 puzzle mäng. See on Android avatud lähtekoodiga sadama. | "sadama" = harbour |
| fi | 61 | 2048 pulmapeli. Tämä on Android avoimen lähdekoodin satamaan. | "satamaan" = into the harbour |
| fr | 61 | Jeu de puzzle 2048. Il s'agit du portage open source Android. | OK (fixed 2019) |
| hi | 49 | 2048 पहेली खेल. इस Android खुला स्रोत बंदरगाह है. | "बंदरगाह" = harbour |
| id | 59 | 2048 Puzzle Game. Ini adalah android pelabuhan open source. | "pelabuhan" = harbour |
| it | 63 | 2048 Puzzle Game. Questa è la versione open source per Android. | OK-ish |
| ja | 36 | 2048パズルゲーム。これはAndroidのオープンソースのポートです。 | OK-ish |
| ko | 32 | 2048 퍼즐 게임. 이 안드로이드 오픈 소스 포트입니다. | awkward |
| lt | 44 | 2048 dėlionė. Tai Android atviro kodo uosto. | "uosto" = harbour |
| lv | 52 | 2048 Puzzle spēle. Tas ir android open source portu. | mixed English |
| pt-BR / pt-PT | 58 / 58 | 2048 Jogo Puzzle. Esta é a porta de código aberto Android. (identical) | "porta" = door |
| ro | 59 | 2048 joc de puzzle. Acesta este portul open source Android. | ambiguous |
| ru | 61 | 2048 Puzzle Game. Это андроид порт с открытым исходным кодом. | untranslated "Puzzle Game" |
| sv | 57 | 2048 pusselspel. Detta är den Android öppen källkod port. | ungrammatical |
| sw | 56 | 2048 Puzzle michezo. Hii ni android wazi chanzo bandari. | "bandari" = harbour |
| th | 52 | 2048 เกมปริศนา นี้เป็นพอร์ตเปิดแหล่งที่มาของหุ่นยนต์ | "หุ่นยนต์" = robot |
| tr | 52 | 2048 Puzzle Oyunu. Bu android açık kaynak limanıdır. | "limanı" = harbour |
| uk | 61 | 2048 Puzzle Game. Це андроїд порт з відкритим вихідним кодом. | untranslated "Puzzle Game" |
| vi | 47 | 2048 Puzzle Game. Đây là cổng nguồn mở Android. | "cổng" = gate |

`en-US/full_description.txt` (14 lines) describes "just a WebView", says it is "a bit slower than native code", gives "Long touch for 3+ seconds…" (code: 2 s) and links uberspot/2048 and uberspot/2048-android. **All of it is obsolete.** The reusable spirit is "No ads, and no permissions required. Just open-source."

Images (`en-US`), checked against the current Play requirements:

| File | Size | PNG type | Content | Play compliance |
|---|---|---|---|---|
| `icon.png` | 512×512, 26 322 B | RGBA (transparent rounded corners) | 4×4 mini-board | OK format (32-bit PNG with alpha, ≤1024 KB), **but mismatches the current launcher icon** |
| `featureGraphic.png` | 1024×500, 29 983 B | **RGBA with a transparent background** | Same mini-board centered on transparent | **Violates "JPEG or 24-bit PNG (no alpha)"** |
| `promoGraphic.png` | 180×120, 8 557 B | RGBA (opaque) | Mini-board crop | **No longer a Play asset type** (current list: icon, short description, feature graphic, screenshots, preview video, TV banner) |
| `phoneScreenshots/screen1.png` | 405×727 | RGB | 2014-era UI: KitKat status bar, **action bar with old icon**, "HOW TO PLAY: Use your arrow keys" | Meets the 320 px minimum but below the 1080 px needed for recommendation eligibility (≥4 at 1080×1920 for apps, ≥3 for games); does not match the app |
| `sevenInchScreenshots/screen_tablet.png` | 451×727 | RGB | Same era, tablet | Large-screen screenshots should be 1080–7680 px, 16:9/9:16 |

Changes required for the new `com.clayboicardi.game2048` / "2048" app:
1. Move to **`fastlane/metadata/android/<locale>/`**. Both fastlane `supply` and F-Droid read it, so one tree serves Play and F-Droid/IzzyOnDroid.
2. Put graphics under **`<locale>/images/`** (`icon.png`, `featureGraphic.png`, `phoneScreenshots/1.png…`, `sevenInchScreenshots/`, `tenInchScreenshots/`). This is the layout F-Droid currently documents. The legacy flat placement directly under `<locale>/` may be ignored (*unverified*).
3. Add `title.txt` = `2048` (F-Droid max 50 chars; Play's app-name limit is 30, *unverified*).
4. Add `changelogs/<versionCode>.txt` (≤ 500 chars, literal versionCode, no padding). F-Droid's "Latest" tab requires a What's New entry.
5. Rewrite every `short_description.txt` (≤ 80) and `full_description.txt` (≤ 4000): drop "port", "WebView", "long touch" and the uberspot links. Keep "offline, no ads, no permissions, open source (MIT)".
6. Regenerate all images from the native app: icon to match the adaptive icon; an opaque feature graphic; ≥4 phone screenshots at 1080×1920, plus tablet screenshots. Delete `promoGraphic.png`.
7. If uploading to Play via `supply`, locale directory names must match Play's language codes. Some are region-qualified (e.g. `de-DE`, `fr-FR`, `es-ES`). *Unverified list; run `fastlane supply init` against the listing or check the Play Console language dropdown.*
8. README: remove the Travis badge and the Play/F-Droid badges for `com.uberspot.a2048`. They advertise someone else's listing, which is misleading for a fork with a new id. Credit upstream instead.

---

## 7. Defects and tech-debt register

Severity: B = blocker, H = high, M = medium, L = low.

| # | Sev | Where | Defect | Native resolution |
|---|---|---|---|---|
| D1 | B | wrapper props/jar | Gradle 4.10.3 (jar 4.10.2) cannot run on JDK 21 (Java 21 support starts at 8.5) | Gradle 8.14.3 wrapper with `distributionSha256Sum`; CI wrapper validation |
| D2 | B | build.gradle:9 | AGP 3.2.1 incompatible with Gradle 8 / JDK 17+ | Current AGP compatible with Gradle 8.14.3 (toolchain track to pin) |
| D3 | B | build.gradle:5,17,66 | `ckchangelog-legacy-dialog:2.0.0-SNAPSHOT` from OSSRH snapshots: **repo shut down 2025-06-30**; artifact 404 on Central; SNAPSHOT is non-reproducible | Drop the dependency; native "What's new" |
| D4 | H | build.gradle:4,16 | `jcenter()`: redirected Aug 2024, API removed in Gradle 9.0 | `google()` + `mavenCentral()` only, with content filters |
| D5 | H | .travis.yml, README:4 | Travis (travis-ci.org) dead since June 2021; oraclejdk8 | GitHub Actions (JDK 21, SDK 36/37) |
| D6 | H | build.gradle:24,30 | compile/target 28; Play requires target 36 for new apps since 2026-08-31 | compileSdk 36/37, targetSdk 36 |
| D7 | H | build.gradle:29 | minSdk 14; AndroidX/Compose stable require 23 | minSdk 23 (or higher by decision) |
| D8 | H | Manifest:10-20 | No `android:exported` on the launcher activity (an install/build failure at target ≥ 31) | `exported="true"` on the launcher activity |
| D9 | H | MainActivity:166-176 | `onBackPressed` deprecated in 33; **not invoked at target 36 on Android 16+** | `BackHandler`/`PredictiveBackHandler` only for in-app overlays/screens; no exit guard |
| D10 | M | MainActivity:117-121 | onResume full reload: double load, replayed animations, lost Game-over screen and un-persisted undo, dead first undo, night-mode reset (§4.9) | Persist on every committed move; never reload on resume |
| D11 | M | MainActivity:53-68, Manifest:14 | One-shot `ACCELEROMETER_ROTATION` read; `SENSOR` overrides the user lock; size buckets; manifest portrait/`setRequestedOrientation` **ignored on sw ≥ 600dp at target 36/37** unless `appCategory` is game | No orientation requests; adaptive layouts per window size class; `android:appCategory="game"` |
| D12 | M | MainActivity:151-158 | `FLAG_FULLSCREEN` deprecated in 30; inverted naming; layout jump; edge-to-edge enforced (no opt-out at target 36 on Android 16) | `enableEdgeToEdge()` + `WindowInsetsControllerCompat` for optional immersive mode |
| D13 | M | MainActivity:96-111 | Long press conflicts with swipes and buttons; undiscoverable; no a11y path; wall clock; fires on release | Drop the gesture; Settings switch |
| D14 | M | Manifest:6 | `allowBackup` with no rules | Explicit `dataExtractionRules` / `fullBackupContent` covering the DataStore files (best score, stats, settings); exclude transient caches |
| D15 | M | metadata/en-US | Feature graphic has alpha (Play rejects); screenshots 405/451 px and stale; promo graphic obsolete; store icon ≠ launcher icon | Regenerate all assets |
| D16 | L | MainActivity:83,84,82 | `setRenderPriority` (dep. 18), `setDatabasePath` (dep. 19), `setDatabaseEnabled` (dep. 35) | Gone with the WebView |
| D17 | L | MainActivity:136,142 | `android.preference.PreferenceManager` deprecated in 29 | Jetpack DataStore |
| D18 | L | MainActivity:123-126 | `onSaveInstanceState` lacks the `super` call; restore branch dead | `rememberSaveable`/`SavedStateHandle` |
| D19 | L | MainActivity:46-47 | Redundant `FLAG_HARDWARE_ACCELERATED` | n/a |
| D20 | L | MainActivity:94 | Hint toast on every `onCreate`, including recreations | No toast; optional one-time onboarding |
| D21 | L | MainActivity:113-114 | Back toast `LENGTH_SHORT` (~2 s) vs 3.5 s window | Feature dropped |
| D22 | L | Manifest:12 | Partial `configChanges` (recreates on multi-window, fold, uiMode, locale) | No `configChanges`; ViewModel and persisted state survive recreation |
| D23 | L | Manifest | No `supportsRtl` although fa is shipped | `supportsRtl="true"`, but **force LTR on the board** so swipe directions stay physical |
| D24 | L | styles.xml | `android:Theme.Light` (API 1), no dark theme | M3 DayNight theme with a Compose theme |
| D25 | L | build.gradle:35-52 | Eclipse source sets; missing `proguard-rules.pro`; R8 off; legacy `lintOptions`; pinned `buildToolsVersion` | Standard `app/src/main`; R8 + resource shrinking in release; `lint {}` with warningsAsErrors in CI |
| D26 | L | res/ | Unused `action_settings`, `menu/main.xml`, both `dimens.xml`, `fill_parent`, useless parent | Not carried over |
| D27 | L | values-cs/pl/lt/el/it/fa | BOM, typos, missing diacritics or meaning, English loanword, localized app label | Fix while migrating `settings`; `app_name` non-translatable |
| D28 | L | changelog_master.xml | Duplicate versioncode 18; English-only; post-2.2 changes never released or logged | Legacy history in About; new versioning scheme |
| D29 | L | metadata/*/short_description.txt | Machine-translated mistranslations ("harbour", "robot", "door", "gate") | Rewrite all |
| D30 | L | metadata/en-US/full_description.txt | "3+ seconds" contradicts the 2000 ms code; WebView claims | Rewrite |
| D31 | L | .gitmodules/.gitignore/README | Submodule to uberspot/2048; `assets/2048/*` ignore; Eclipse build docs; foreign store badges | Remove the submodule; new README |
| D32 | L | ic_2048.xml | Non-adaptive, no background, in `drawable/`, 3.37:1 contrast on white, no monochrome/round | Adaptive icon (§5.3) |

Security note (legacy, low): a JS-enabled WebView loads only `file:///android_asset` with no INTERNET permission and no JS bridge, so it is low risk. It disappears with the rewrite.

---

## 8. Assets worth migrating

### 8.1 Translation inventory: native (shell) vs web (`js/i18n.js`)

| Proposed native key | Source | en value | Locales that already have a translation |
|---|---|---|---|
| `app_name` | shell `app_name` | 2048 | 21 (fa = "۲۰۴۸"). **Mark `translatable="false"`** per the locked label "2048" |
| `settings` | shell `action_settings` | Settings | **20 + en, reusable verbatim** (fix cs BOM) |
| *(dropped)* `press_back_again_to_exit` | shell | Press back again to exit | 20 + en (feature dropped) |
| *(dropped)* `toggle_fullscreen` | shell | Long click to toggle fullscreen | 20 + en (gesture dropped; see the "full screen" noun seeds below) |
| `intro` | web | Join the numbers and get to the **2048 tile!** (`<strong>` markup) | en, ru, uk |
| `new_game` | web | New Game | en, ru, uk |
| `undo` | web | Undo | en, ru, uk |
| `keep_going` | web | Keep going | en, ru, uk |
| `try_again` | web | Try again | en, ru, uk |
| `ok` | web | OK | en, ru, uk |
| `cancel` | web | Cancel | en, ru, uk |
| `start_a_new_game` | web | Start a new game? | en, ru, uk |
| `undo_the_current_move` | web | Undo the current move? | en, ru, uk |
| `you_win` | web | You win! | en, ru, uk |
| `game_over` | web | Game over! | en, ru, uk |
| `score` | web CSS `content:` | Score (rendered uppercase) | en only |
| `best` | web CSS `content:` | Best (rendered uppercase) | en only |

ru / uk web values to import verbatim (from `js/i18n.js`):

| Key | ru | uk |
|---|---|---|
| intro | Объединяйте числа и получите **2048!** | Об'єднуйте числа і отримаєте **2048!** |
| new_game | Новая игра | Нова гра |
| undo | Отменить ход | Скасувати хід |
| keep_going | Продолжайте | Продовжуйте |
| try_again | Попробовать ещё раз | Спробувати ще раз |
| ok | ОК | ОК |
| cancel | Отмена | Відміна |
| start_a_new_game | Начать новую игру? | Почати нову гру? |
| undo_the_current_move | Отменить текущий ход? | Скасувати поточний хід? |
| you_win | Вы победили! | Ви виграли! |
| game_over | Игра закончена! | Гра завершена! |

Coverage reality: only **ru and uk** were ever fully localized in-game. The other 18 shell locales showed English game UI. The native app therefore needs new translations for ~13+ game strings, plus all new feature strings, in every locale it keeps.

Recommendation: ship en + the 20 legacy locales (cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, pt, ru, sl, sr, tr, uk). Seed them from the legacy strings and the ru/uk set, and flag the rest for translation. Enable `androidResources { generateLocaleConfig = true }` (AGP ≥ 8.1, compileSdk ≥ 33) with `res/resources.properties` `unqualifiedResLocale=en-US` so the app appears in Android 13+ per-app language settings. An in-app picker is optional (`AppCompatDelegate.setApplicationLocales` plus `autoStoreLocales` for API ≤ 32).

"Full screen" noun seeds mined from `toggle_fullscreen`, for an "Immersive / Full screen" switch label. These are my normalization to the nominative form and **need native review**:

| Locale | Seed |
|---|---|
| cs | Celá obrazovka |
| da | Fuld skærm |
| de | Vollbild |
| el | Πλήρης οθόνη |
| eo | Tutekrana reĝimo |
| es | Pantalla completa |
| fa | تمام صفحه |
| fr | Plein écran |
| hu | Teljes képernyő |
| it | Schermo intero |
| ko | 전체 화면 |
| lt | Visas ekranas |
| nl | Volledig scherm |
| pl | Pełny ekran |
| pt | Tela cheia |
| ru | Полноэкранный режим |
| sl | Celozaslonski način |
| sr | Цео екран |
| tr | Tam ekran |
| uk | Повноекранний режим |

### 8.2 Store metadata: keep the skeleton, rewrite the content (see §6 for the required changes).

### 8.3 Changelog history
Keep the 9 legacy releases (§5.2) as static data in an About → "Legacy history (uberspot/2048-android)" section with contributor credits. Start a new changelog for the native app. Suggested `versionName "3.0.0"` / `versionCode 30000` (MAJOR·10000 + MINOR·100 + PATCH); this is a decision point. The id is new, so any monotonic scheme works, and fastlane `changelogs/30000.txt` must match it literally.

### 8.4 Icon geometry
Reuse the 4 glyph paths (§5.3) inside an adaptive-icon foreground group (scale 1.411, translate 16.667 in a 108 viewport), and reuse them as the monochrome layer. Decide between the gold-text identity (`#aa8800`) and the classic mini-board/tile identity, which uses palette hexes from the store icon. Either way the store icon must match the launcher icon.

### 8.5 Licensing
Keep the MIT notices: Paul Sarbinowski 2014 (shell) for any reused strings, icon paths or text, and Gabriele Cirulli 2014 (game) for the game. Show both in an in-app "Open-source licenses / credits" screen.

---

## 9. Parity checklist (legacy shell → native)

| # | Legacy behavior (ref) | Decision | Native equivalent / reason |
|---|---|---|---|
| 1 | Launches straight into the game, no title/action bar (MainActivity:43) | **Keep** | Single-activity Compose; game screen without a top app bar; `enableEdgeToEdge()` with inset-aware padding |
| 2 | Hardware acceleration flag (:46-47) | **Drop** | Default; irrelevant to Compose |
| 3 | Long press > 2000 ms toggles fullscreen (:96-111) | **Replace** | Settings switch "Immersive mode" → `WindowInsetsControllerCompat.hide(systemBars())` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. The gesture is dropped: it conflicts with swipe input, is undiscoverable and has no a11y path |
| 4 | Persisted `is_fullscreen_pref` (inverted naming, default = bars shown) (:134-158) | **Replace** | DataStore `immersive_mode: Boolean = false`, correctly named. No migration (new appId sandbox) |
| 5 | Start-up toast "Long click to toggle fullscreen" (:94) | **Drop** | No gesture left to explain. Optional one-time "How to play" onboarding instead |
| 6 | Double-back-to-exit with 3500 ms toast (:166-176) | **Drop** | Never called at target 36 on Android 16+; blocks predictive back-to-home; state autosaves so exit is harmless. Use `BackHandler` only to close dialogs/sheets/sub-screens |
| 7 | Manifest portrait lock (Manifest:14) | **Replace** | No `screenOrientation`. Responsive layouts: portrait = board under the header; landscape/expanded = board beside the controls. Add `android:appCategory="game"` |
| 8 | Large/xlarge + auto-rotate → `SCREEN_ORIENTATION_SENSOR` (:53-68) | **Replace** | Let the system handle rotation (honors the user's lock natively); window size classes instead of size buckets |
| 9 | `configChanges=orientation\|keyboardHidden\|screenSize` (Manifest:12) | **Replace** | Allow recreation; state in ViewModel + `SavedStateHandle` + DataStore; tile animations restart cleanly |
| 10 | First-run/upgrade changelog dialog via ckChangeLog (:72-75) | **Replace** | No library. Non-blocking "What's new" sheet or snackbar **only after an update** (versionCode increased, stored in DataStore); never on a fresh install. Full history in About |
| 11 | `changelog_master.xml` history | **Keep (as data)** | About → Legacy history and credits |
| 12 | WebView host + JS/DOM storage/WebSQL (:78-84) | **Drop** | Native Kotlin engine and Compose UI |
| 13 | `setRenderPriority` / `setDatabasePath` | **Drop** | Deprecated no-ops |
| 14 | `WebView.saveState/restoreState` (:87-92, :123-126) | **Replace** | `rememberSaveable`/`SavedStateHandle` for transient UI; DataStore (or file) for game, undo stack, best score, stats; written on every committed move |
| 15 | `onResume()` reload of index.html (:117-121) | **Drop** | Root cause of the replayed animations and lost end screens and undos. Locale changes trigger normal recreation with Android resources |
| 16 | Locale via `?lang=` (:91, :120) | **Replace** | Android resource localization + `generateLocaleConfig` (per-app language, Android 13+); optional in-app picker |
| 17 | Web `localStorage` keys `bestScore`, `gameState`, `gameStatesStack` | **Replace** | Typed persisted model (Kotlin serialization or proto); bounded undo (legacy cap ~50); no duplicate pushes on restore |
| 18 | Night mode button (web, not persisted) | **Replace (upgrade)** | System / Light / Dark theme setting, persisted; dynamic color optional |
| 19 | Shell strings ×20 locales | **Keep partially** | `app_name` (non-translatable "2048"), `settings` (reuse); drop the back/fullscreen hints; mine "full screen" seeds |
| 20 | Web i18n (en/ru/uk) + CSS "Score"/"Best" | **Keep (migrate)** | `strings.xml` keys of §8.1 |
| 21 | Launcher icon `ic_2048` vector (Manifest:7) | **Replace** | Adaptive icon (bg + fg + monochrome) in `mipmap-anydpi-v26`, plus a vector fallback for API 23–25; glyph geometry reused; store icon matched |
| 22 | `allowBackup="true"` without rules (Manifest:6) | **Keep (tighten)** | Explicit `dataExtractionRules` (API 31+) and `fullBackupContent` (≤30) that include the DataStore files |
| 23 | Zero `<uses-permission>` (Manifest) | **Keep** | No INTERNET. Note: `androidx.core` 1.19.1 merges a **signature-level** `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` uses-permission, and `profileinstaller` 1.4.1 merges an exported receiver guarded by `android.permission.DUMP` (a caller permission, not requested). Neither is a runtime or dangerous permission. Add a CI check on the merged manifest: no `android.permission.*` uses-permission at all |
| 24 | Light pre-Holo theme (styles.xml) | **Replace** | Material 3 theme |
| 25 | Unused dimens (incl. sw720dp-land 128dp margin) | **Drop** | Use the idea only: wide gutters / max board width on expanded windows |
| 26 | Travis CI + Gradle 4.10.3 + AGP 3.2.1 | **Replace** | GitHub Actions (JDK 21, Gradle 8.14.3 wrapper with checksum, SDK 36/37) |
| 27 | Submodule `assets/2048` | **Drop** | Deleted per the locked decision |
| 28 | `metadata/<locale>/` store listing | **Replace (restructure)** | `fastlane/metadata/android/<locale>/` with `title.txt`, `images/`, `changelogs/`; all text rewritten; new screenshots |
| 29 | MIT LICENSE (Sarbinowski) + game MIT (Cirulli) | **Keep** | LICENSE file plus an in-app credits/licenses screen |

---

## 10. Plan-relevant gotchas uncovered by this audit
1. **RTL locales (fa; ar in the listings):** enabling `supportsRtl` mirrors Compose `Row`s. The board grid and swipe-direction mapping must be wrapped in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`, or a left swipe would move tiles right.
2. **Large screens at target 36/37:** orientation locks are ignored on sw ≥ 600dp, with no developer opt-out at 37. `android:appCategory="game"` keeps the exemption, but designing for all orientations is the robust path.
3. **Back handling:** on Android 16+ at target 36, nothing like `onBackPressed` exists. Use `BackHandler`, and test predictive back.
4. **No legacy data import:** a different applicationId means a different sandbox. Users of `com.uberspot.a2048` keep their own app. Say so in the README; no migration code is needed.
5. **"Zero permissions" wording:** keep the store claim, since there are no runtime or dangerous permissions, but expect the AndroidX signature permission in the merged manifest.
6. **Store compliance:** the opaque feature graphic, ≥ 1080 px screenshots and a matching icon are needed before any Play submission. This is not needed for CI.

---

## 11. Sources
- Repository files and git history (local): commits `8a04f5d`, `b5af619`, `b6334dd`, `cca7106`, `825e39f`, `5432be9`, `e097460`.
- SDK API levels: `/opt/android-sdk/platforms/android-37.0/data/api-versions.xml`. `WebSettings.setRenderPriority` deprecated 18; `setDatabasePath` deprecated 19; `setDatabaseEnabled` deprecated 35; `setAppCacheEnabled` deprecated 30 and removed 33; `Activity.onBackPressed` deprecated 33; `WindowManager.LayoutParams.FLAG_FULLSCREEN` deprecated 30; `android.preference.PreferenceManager` deprecated 29; `R.attr.appCategory` since 26; `enableOnBackInvokedCallback` since 33; `windowOptOutEdgeToEdgeEnforcement` since 35.
- AndroidX AAR manifests (minSdk 23; core permission; profileinstaller receiver) from https://dl.google.com/dl/android/maven2/ (`androidx/compose/ui/ui-android/1.12.1`, `androidx/core/core/1.19.1`, `androidx/activity/activity-compose/1.13.0`, `androidx/profileinstaller/profileinstaller/1.4.1`).
- Maven Central ckchangelog metadata: https://repo1.maven.org/maven2/de/cketti/library/changelog/ckchangelog/maven-metadata.xml (1.2.2 latest; `ckchangelog-legacy-dialog` 404).
- Play target API: https://developer.android.com/google/play/requirements/target-sdk and https://support.google.com/googleplay/android-developer/answer/11926878
- Android 16 behavior changes: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17 behavior changes: https://developer.android.com/about/versions/17/behavior-changes-17 and https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored
- Android 12 exported requirement: https://developer.android.com/about/versions/12/behavior-changes-12
- AGP 8.0 namespace: https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes
- Gradle Java compatibility: https://docs.gradle.org/current/userguide/compatibility.html
- Gradle 9 `jcenter()` removal: https://docs.gradle.org/current/userguide/upgrading_major_version_9.html
- JCenter: https://blog.gradle.org/jcenter-shutdown and https://blog.gradle.org/portal-jcenter-impact
- OSSRH EOL: https://central.sonatype.org/pages/ossrh-eol/ and https://central.sonatype.org/news/20250326_ossrh_sunset/
- Travis: https://docs.travis-ci.com/user/migrate/open-source-repository-migration
- F-Droid metadata layout: https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/
- Play preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Per-app languages: https://developer.android.com/guide/topics/resources/app-languages
- Adaptive icons: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive
- ckChangeLog: https://github.com/cketti/ckChangeLog
