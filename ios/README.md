# 2048 for iPhone

A standalone [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) app that
shares the exact game engine (`:core:engine`) with the Android app, so the rules are identical on
both. This is a **separate Gradle build** from the Android project (decision D-272): it is not in
the root `settings.gradle.kts`, and `scripts/verify.sh` never touches it. Run it with the repo's
root Gradle wrapper and `-p ios`.

## Layout

```
ios/
├── settings.gradle.kts          # own build; not included by the root
├── gradle/libs.versions.toml    # own version catalog (root D-016 catalog untouched)
├── composeApp/
│   ├── build.gradle.kts         # KMP: jvm + iosArm64 + iosSimulatorArm64; shares the engine by srcDir
│   └── src/
│       ├── commonMain/kotlin/   # shared UI + state + persistence (English v1)
│       ├── iosMain/kotlin/      # MainViewController + iOS file paths
│       ├── jvmMain/kotlin/      # desktop/CI actuals (local validation only)
│       └── commonTest/kotlin/   # engine-parity test
├── iosApp/                      # Xcode project (SwiftUI host) → the .app/.ipa
└── DISTRIBUTION.md              # how to get it onto an iPhone (free Apple ID + SideStore)
```

## Build

Most development needs a Mac with Xcode (the Kotlin/Native iOS toolchain and `xcodebuild` are
macOS-only). From the repo root:

```bash
# Typecheck the shared code + run the engine-parity test on any OS (no Xcode needed):
./gradlew -p ios :composeApp:jvmTest

# On a Mac: open the Xcode project and run on a simulator or device:
open ios/iosApp/iosApp.xcodeproj
```

CI (`.github/workflows/ios.yml`) builds an **unsigned** `.ipa` on a free GitHub macOS runner and
uploads it as an artifact. See [DISTRIBUTION.md](DISTRIBUTION.md) to install and keep it on a phone
with a free Apple ID via SideStore.

## What's shared vs. iOS-only (v1)

- **Shared verbatim:** `:core:engine` — board, moves, merges, spawning, win/over, the seeded
  SplitMix64 RNG. Same bytes, same outcomes as Android.
- **iOS-only, v1:** a fresh Compose UI reusing the Android Classic Refined palette/geometry;
  English strings; JSON-on-file persistence (current game + best score per size); simplified
  haptics; snap-with-pop tiles.
- **Follow-ups (not v1):** slide motion, the full resource/i18n set, Android save compatibility.
