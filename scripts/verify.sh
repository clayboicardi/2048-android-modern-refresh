#!/usr/bin/env bash
# The single merge gate (D-140; levels per docs/modernization/QUALITY.md §3).
# CI runs `./scripts/verify.sh` with no arguments, which is the full gate.
#
#   ./scripts/verify.sh [full]         every gate CI runs; required before every push (D-163)
#   ./scripts/verify.sh quick          spotlessCheck + engine tests; inner loop only, never a push gate
#   ./scripts/verify.sh --record       re-record Roborazzi goldens, then list every new or changed PNG
#   ./scripts/verify.sh full --record  --record, then the full gate
#
# Local-only Gradle arguments (an out-of-repo init script, -ProbolectricRepoUrl=...) come from
# the VERIFY_GRADLE_ARGS environment variable (D-019). CI sets nothing.
set -uo pipefail
cd "$(dirname "$0")/.."
export ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-37.0.0}"
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"

level=""
record=false
for arg in "$@"; do
  case "$arg" in
    full | quick) level="$arg" ;;
    --record) record=true ;;
    -h | --help) sed -n '2,11p' "$0"; exit 0 ;;
    *) echo "verify.sh: unknown argument '$arg' (see --help)" >&2; exit 2 ;;
  esac
done
if [[ "$record" == true && "$level" == quick ]]; then
  echo "verify.sh: --record cannot be combined with quick" >&2
  exit 2
fi
if [[ -z "$level" ]]; then
  if [[ "$record" == true ]]; then level=none; else level=full; fi
fi

start=$SECONDS
fail=0

if [[ "$record" == true ]]; then
  # --rerun forces the test task to execute even when Gradle considers it up to date.
  ./gradlew "${EXTRA[@]}" :app:testDebugUnitTest --rerun -Proborazzi.test.record=true || fail=1
  echo "== Goldens added or changed (Read every file before committing; D-147):"
  git status --porcelain --untracked-files=all -- app/src/test/screenshots
fi

case "$level" in
  quick)
    ./gradlew "${EXTRA[@]}" --continue spotlessCheck :core:engine:test || fail=1
    ;;
  full)
    # The engine tests run alone first, so the 2 s budget of EnginePerformanceTest (D-150) is not
    # measured against the app's parallel tasks; the second invocation still runs after a failure (D-223).
    ./gradlew "${EXTRA[@]}" --continue :core:engine:test :core:engine:koverVerify || fail=1
    ./gradlew "${EXTRA[@]}" --continue \
      spotlessCheck detektMain detektTest \
      :app:testDebugUnitTest -Proborazzi.test.verify=true \
      :app:lintDebug :app:assembleDebug :app:assembleRelease || fail=1
    for s in check-test-counts check-apk check-licenses check-metadata; do
      "./scripts/$s.sh" || { echo "FAILED: $s"; fail=1; }
    done
    ;;
esac

result=PASS
[[ "$fail" -eq 0 ]] || result=FAIL
echo "verify.sh: level=$level record=$record result=$result elapsed=$((SECONDS - start))s"
exit "$fail"
