# 2048

![CI](https://github.com/clayboicardi/2048-android-modern-refresh/actions/workflows/ci.yml/badge.svg?branch=master)

2048 is the classic sliding-tile puzzle, rebuilt from scratch as a native Android app in Kotlin with Jetpack Compose and Material 3. Swipe to move every tile at once; two tiles with the same number merge into one. Reach the 2048 tile, then keep going as far as you can. The app works fully offline, asks for no runtime permissions and keeps no data off your device.

## Features

- Board sizes from 3×3 to 8×8, each with its own saved game, best score and statistics.
- Unlimited undo, even after game over, with the same next tile each time.
- Two palettes, Classic Refined and Neon Arcade, in light and dark themes, plus a high-contrast option.
- Accessibility: full TalkBack support with a direction pad, keyboard, D-pad and game-controller input, and text that stays readable at large font sizes.
- 21 languages, with per-app language selection on Android 13 and later.
- Offline: no internet access, no ads, no tracking, no analytics and no accounts.

## A new app

This is a new app with a new package name (`com.clayboicardi.game2048`); the old app can stay installed side by side; scores do not carry over.

## Privacy and permissions

The privacy policy is in [PRIVACY.md](PRIVACY.md).

The app requests no runtime permissions and never shows a permission prompt. Its manifest contains one permission, `com.clayboicardi.game2048.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which the AndroidX Core library adds automatically. It is a signature-level permission: only apps signed with the same key can hold it, and on Android versions before 13 it keeps the app's own internal broadcast receivers private. It gives the app no access to your data, your device or other apps. The debug build carries the same permission under its own package name, `com.clayboicardi.game2048.debug`.

Exported components: `MainActivity` (the launcher entry) and AndroidX `ProfileInstallReceiver`, which only the system shell can call (it requires `android.permission.DUMP`) and which installs ahead-of-time performance profiles. The app itself requests no such permission.

## Install a CI build

The app is not in any app store yet; an F-Droid submission is planned.

1. Open the [CI workflow](https://github.com/clayboicardi/2048-android-modern-refresh/actions/workflows/ci.yml) and choose a green run on `master` or on a pull request.
2. Download the artifact `game2048-apks-<sha>` (kept for 30 days; GitHub requires a signed-in account) and unzip it. It contains `apk/release/app-release.apk` (recommended for playing), `apk/debug/app-debug.apk` (package `com.clayboicardi.game2048.debug`, label "2048 Debug", installs next to the release build with separate data) and `mapping/release/mapping.txt`.
3. Optionally check the signer with `apksigner verify --print-certs apk/release/app-release.apk`: the certificate SHA-256 digest equals the content of `signing/ci-sideload.sha256`.
4. Install with `adb install -r apk/release/app-release.apk`, or copy the APK to the device, open it in a file manager and allow "Install unknown apps" for that file manager when Android asks.

Every CI build installs over the previous CI build of the same variant and keeps its data.

CI builds are signed with a key whose password is public (see `signing/README.md`); install them only from this repository's Actions runs.

## Build from source

- JDK 21.
- Android SDK with `platforms;android-37.0` and `build-tools;37.0.0` (plus `build-tools;36.0.0`, the Android Gradle Plugin's default), located through `ANDROID_HOME`.
- `./scripts/verify.sh` runs the full gate, the same script CI runs: formatting, static analysis, unit and screenshot tests, Lint, debug and release builds and the repository checks.
- `./gradlew :app:assembleDebug` builds the debug APK only.
- `./scripts/verify.sh --record` re-records the screenshot goldens in `app/src/test/screenshots/` after an intended UI change and lists the new or changed images.
- `./gradlew :app:testDebugUnitTest -PrecordStoreAssets=true --tests 'com.clayboicardi.game2048.store.*'` renders the store graphics in `fastlane/metadata/android/en-US/images/` from code.

## Saved games

The game is saved after every move. If Android stops the app while a save is being written, at most that one change is lost.

## Translations

The 20 translations were produced with machine assistance and checked automatically; corrections are welcome as pull requests to `app/src/main/res/values-*/strings.xml`. Please follow `docs/modernization/i18n-glossary.md`.

## Credits

- Gabriele Cirulli, for the original [2048](https://github.com/gabrielecirulli/2048) game.
- Paul Sarbinowski (uberspot) and contributors, for the original [2048 Android app](https://github.com/uberspot/2048-android).
- The translators of the original app, whose strings this app keeps; they are listed in [NOTICE](NOTICE) and in the app under About > Credits.

## License

MIT; see [LICENSE](LICENSE). Third-party notices: [NOTICE](NOTICE).
