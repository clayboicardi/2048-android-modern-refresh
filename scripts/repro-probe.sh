#!/usr/bin/env bash
# Reproducibility probe (report-only; docs/modernization/QUALITY.md §1.17).
# Builds the committed HEAD's release APK again in a fresh clone at another path (build cache off),
# then once more there after `clean`, and compares both with the APK in this checkout. A clone, not
# a git worktree: AGP's VCS info cannot read a worktree's .git file (NO_VALID_GIT_FOUND).
# Never fails: differences are printed as warnings with the differing zip entries.
set -uo pipefail
cd "$(dirname "$0")/.."
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"
apk=app/build/outputs/apk/release/app-release.apk

if ! git diff --quiet HEAD --; then
  echo "::warning::repro-probe: uncommitted changes; probe skipped (it compares builds of HEAD)"
  exit 0
fi
if [[ ! -f "$apk" ]]; then
  ./gradlew "${EXTRA[@]}" :app:assembleRelease ||
    { echo "::warning::repro-probe: first build failed"; exit 0; }
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git clone --quiet --no-hardlinks --no-checkout . "$tmp/tree"
git -C "$tmp/tree" checkout --quiet --detach "$(git rev-parse HEAD)"
[[ -f local.properties ]] && cp local.properties "$tmp/tree/"

build_second() {
  (cd "$tmp/tree" && ./gradlew "${EXTRA[@]}" --no-build-cache --no-configuration-cache "$@" :app:assembleRelease -q)
}

compare() { # label other-apk
  local label="$1" other="$2"
  sha256sum "$apk" "$other"
  if cmp -s "$apk" "$other"; then
    echo "repro-probe: $label: bit-identical"
    return
  fi
  echo "::warning::repro-probe: $label: release APK differs"
  python3 -I - "$apk" "$other" << 'PY'
import sys, zipfile
a, b = (zipfile.ZipFile(p) for p in sys.argv[1:3])
ea = {i.filename: i.CRC for i in a.infolist()}
eb = {i.filename: i.CRC for i in b.infolist()}
for name in sorted(set(ea) | set(eb)):
    if ea.get(name) != eb.get(name):
        print(f"  differs: {name}")
PY
}

if build_second; then
  compare "second path, no build cache" "$tmp/tree/$apk"
  if build_second clean; then
    compare "second path, after clean" "$tmp/tree/$apk"
  else
    echo "::warning::repro-probe: third build failed"
  fi
else
  echo "::warning::repro-probe: second build failed"
fi
exit 0
