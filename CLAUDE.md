@AGENTS.md

## Session notes

- **Android SDK.** If `ANDROID_HOME` is unset, locate the SDK the environment provides (for example with `sdkmanager --list_installed` or the environment's documentation) and export `ANDROID_HOME` for every agent; worktrees do not share an uncommitted `local.properties`. Never write the SDK path into a tracked file. Required: `platforms;android-37.0`, `build-tools;36.0.0` and `build-tools;37.0.0`.
- **JDK and Gradle.** `java` is JDK 21. A system Gradle 8.14.3 is used only for the one-time wrapper bootstrap of DECISIONS.md D-010 (TC-4): in a new empty temporary directory outside the repository, `touch settings.gradle.kts`, run `gradle wrapper` and then `./gradlew wrapper` with the same arguments there, and copy only `gradlew`, `gradlew.bat` and `gradle/wrapper/*` into the repository. Everything else uses `./gradlew`.
- **Maven Central rate limits.** If Gradle or Robolectric downloads fail with HTTP 429, follow `docs/modernization/QUALITY.md` §3.3: put the mirror init script at `$HOME/.config/game2048/mirror.init.gradle.kts` and pass it, with `-ProbolectricRepoUrl=…`, through `VERIFY_GRADLE_ARGS`. Never put it in `~/.gradle/init.d/`, never commit it, and never copy the mirror URL into this file or any tracked file outside `docs/`. Note `(sandbox mirror)` after the `Gates:` line when it was used.
- **One Gradle build at a time** (D-178). Wrap every local `./scripts/verify.sh` and every direct `./gradlew` call in `flock "$HOME/.cache/game2048-gradle.lock"`. Run a full verify detached, writing to `$HOME/.cache/game2048/verify-<short-sha>-<level>.log`, and decide only from its final `verify.sh` line.
- **Goldens.** Look at each new or changed PNG with the file-reading tool before committing it.
- **Roles and GitHub** (D-194, D-195). Implementer agents never push and never call GitHub. The orchestrator performs every GitHub step via the GitHub API (MCP tool or gh) against `clayboicardi/2048-android-modern-refresh` only.
