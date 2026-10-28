#!/usr/bin/env bash
# Repository, legal, asset and store-listing checks (D-152, extended by D-179 and D-190;
# docs/modernization/QUALITY.md §1.16). Each check_* function is added in the milestone named in
# its comment, in the same commit as the content it checks, and is never removed afterwards.
set -uo pipefail
cd "$(dirname "$0")/.."
fail=0
err() { echo "::error::check-metadata: $*"; fail=1; }

# [M0] D-019: no tracked file outside docs/ names the sandbox-only Maven mirror host.
check_mirror_leak() {
  local needle='storage-download.''googleapis.com' # split so this file never matches itself
  local hits
  hits="$(git grep -l -F "$needle" -- . ':(exclude)docs' || true)"
  [[ -z "$hits" ]] || err "sandbox mirror host found in tracked files: $hits"
}

# [M0] D-001/D-152: the legacy submodule is gone.
check_no_submodule() {
  [[ ! -e .gitmodules ]] || err ".gitmodules exists"
  [[ -z "$(git ls-files -s | awk '$1 == "160000"')" ]] || err "a gitlink (submodule) is still tracked"
}

# [M0] D-151/D-179: scripts and the wrapper are committed executable.
check_executable_bits() {
  local bad
  bad="$(git ls-files -s -- gradlew 'scripts/*.sh' 'scripts/**/*.sh' | awk '$1 != "100755" { print $4 }')"
  [[ -z "$bad" ]] || err "not committed as 100755: $bad"
}

# [M0] D-142/D-144, D-179: no Lint or detekt baseline files.
check_no_baselines() {
  local hits
  hits="$(git ls-files | grep -iE '(^|/)[^/]*baseline[^/]*\.xml$' || true)"
  [[ -z "$hits" ]] || err "baseline files are not allowed: $hits"
}

# [M0] D-147, D-179: goldens are PNGs and total at most 10 MiB.
check_golden_budget() {
  local dir=app/src/test/screenshots total=0 n=0 f size
  [[ -d "$dir" ]] || return 0
  while IFS= read -r -d '' f; do
    case "$f" in *.png) ;; *) err "non-PNG file in $dir: $f" ;; esac
    size="$(stat -c %s "$f")"
    total=$((total + size))
    n=$((n + 1))
  done < <(find "$dir" -type f -print0)
  echo "check-metadata: $n goldens, $total bytes"
  ((total <= 10485760)) || err "goldens total $total bytes > 10 MiB (D-147)"
}

# [M0] D-120, D-179: LICENSE carries the three copyright lines, in order.
check_license_file() {
  local got
  got="$(grep -E '^Copyright \(c\) ' LICENSE)"
  [[ "$got" == $'Copyright (c) 2014 Paul Sarbinowski\nCopyright (c) 2014 Gabriele Cirulli\nCopyright (c) 2026 clayboicardi' ]] ||
    err "LICENSE copyright lines differ from D-120"
}

# [M0] D-143, D-174, D-181: opt-ins appear only in the two allowed files.
check_optin_allowlist() {
  local allowed='app/src/test/kotlin/com/clayboicardi/game2048/testing/Accessibility.kt
app/src/main/kotlin/com/clayboicardi/game2048/ui/components/GameModalSheet.kt'
  local hits extra
  hits="$(git grep -l -E '@(file:)?OptIn\(|^[[:space:]]*@Experimental[A-Za-z0-9]*Api\b|optIn\.add|-opt-in' \
    -- '*.kt' '*.kts' ':(exclude)docs' || true)"
  extra="$(comm -23 <(sort -u <<< "$hits" | sed '/^$/d') <(sort -u <<< "$allowed"))"
  [[ -z "$extra" ]] || err "opt-in outside the D-174/D-181 allowlist (a new one needs a D-173 entry): $extra"
}

# [M0] D-179 (L10N_STORE_LEGAL.md §1.2): no tracked file starts with a UTF-8 BOM. Lint's
# ByteOrderMark check does not report a leading BOM, and the legacy values-cs file had one.
check_no_bom() {
  python3 -I - << 'PY' || fail=1
import os, subprocess, sys
paths = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True).stdout.split(b"\0")
bad = []
for p in paths:
    if p and os.path.isfile(p):
        with open(p, "rb") as f:
            if f.read(3) == b"\xef\xbb\xbf":
                bad.append(p.decode("utf-8", "replace"))
for p in bad:
    print(f"::error::check-metadata: {p} starts with a UTF-8 BOM")
sys.exit(1 if bad else 0)
PY
}

# [M0] D-111, D-179 (L10N_STORE_LEGAL.md §3.4): the adaptive foreground and both API 24-25
# fallback icons carry the same four glyph paths. Glyph paths start with a lower-case "m",
# background shapes with an upper-case "M".
check_icon_glyphs() {
  local res=app/src/main/res ref f
  ref="$(grep -o 'pathData="m[^"]*"' "$res/drawable/ic_launcher_foreground.xml")"
  [[ "$(grep -c . <<< "$ref")" == 4 ]] || err "ic_launcher_foreground.xml: expected 4 glyph paths"
  for f in "$res/mipmap-anydpi/ic_launcher.xml" "$res/mipmap-anydpi/ic_launcher_round.xml"; do
    [[ "$(grep -o 'pathData="m[^"]*"' "$f")" == "$ref" ]] ||
      err "glyph paths in $f differ from ic_launcher_foreground.xml"
  done
}

check_mirror_leak
check_no_submodule
check_executable_bits
check_no_baselines
check_golden_budget
check_license_file
check_optin_allowlist
check_no_bom
check_icon_glyphs

[[ "$fail" -eq 0 ]] && echo "check-metadata: OK"
exit "$fail"
