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

# [M0] D-143, D-174, D-181 (+ D-215 in M2): opt-ins appear only in the three allowed files.
check_optin_allowlist() {
  local allowed='app/src/test/kotlin/com/clayboicardi/game2048/testing/Accessibility.kt
app/src/main/kotlin/com/clayboicardi/game2048/ui/components/GameModalSheet.kt
app/src/test/kotlin/com/clayboicardi/game2048/testing/MainDispatcherRule.kt'
  local hits extra
  hits="$(git grep -l -E '@(file:)?OptIn\(|^[[:space:]]*@Experimental[A-Za-z0-9]*Api\b|optIn\.add|-opt-in' \
    -- '*.kt' '*.kts' ':(exclude)docs' || true)"
  extra="$(comm -23 <(sort -u <<< "$hits" | sed '/^$/d') <(sort -u <<< "$allowed"))"
  [[ -z "$extra" ]] || err "opt-in outside the D-174/D-181/D-215 allowlist (a new one needs a D-173 entry): $extra"
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

# [M4] D-076/D-152: the numeral font subset is at most 32 KiB; the OFL text ships with it.
check_font() {
  local font=app/src/main/res/font/google_sans_flex_numerals.ttf
  local ofl=app/src/main/res/raw/ofl_google_sans_flex.txt
  if [[ -f "$font" ]]; then
    (($(stat -c %s "$font") <= 32768)) || err "$font is larger than 32,768 bytes"
    [[ -f "$ofl" ]] || err "$font exists but $ofl does not (OFL-1.1)"
  else
    [[ ! -f "$ofl" ]] || err "$ofl exists without the font (D-076 fallback omits both)"
  fi
}

# [M5] D-113/D-114/D-152, D-190 + FEATURES F30-AC8: store-listing texts.
check_store_listing() {
  python3 -I - << 'PY' || fail=1
import os, re, sys
root = "fastlane/metadata/android"
LOCALES = sorted("en-US cs da de el eo es fa fr hu it ko lt nl pl pt-BR pt-PT ru sl sr tr uk".split())
TEXTS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000,
         "changelogs/30000.txt": 500}  # Google Play limits; the title limit is 30
SCRIPT = {"ru": "Ѐ-ӿ", "uk": "Ѐ-ӿ", "sr": "Ѐ-ӿ",
          "el": "Ͱ-Ͽ", "fa": "؀-ۿ", "ko": "가-힣"}
NEEDLES = ("com.clayboicardi.game2048", "https://github.com/clayboicardi/2048-android-modern-refresh")
DENY = re.compile(r"\bport\b|webview|long touch", re.I)  # D-114 deny-words, every locale
NON_LATIN_DIGITS = re.compile("[٠-٩۰-۹]")
ok = True
def err(msg):
    global ok
    ok = False
    print(f"::error::check-metadata: {msg}")
if not os.path.isdir(root):
    err(f"{root} is missing")
    sys.exit(1)
dirs = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
if dirs != LOCALES:
    err(f"locale directories {dirs} != {LOCALES} (D-113)")
en = {}
for loc in ["en-US"] + [l for l in LOCALES if l != "en-US"]:  # en-US first: the others are compared with it
    base = os.path.join(root, loc)
    for name, limit in TEXTS.items():
        path = os.path.join(base, name)
        if not os.path.isfile(path):
            err(f"{path} is missing")
            continue
        with open(path, "rb") as f:
            raw = f.read()
        if raw.startswith(b"\xef\xbb\xbf") or b"\r" in raw:
            err(f"{path} has a BOM or a CR")
        try:
            t = raw.decode("utf-8")
        except UnicodeDecodeError:
            err(f"{path} is not UTF-8")
            continue
        if not t.endswith("\n") or t.endswith("\n\n"):
            err(f"{path} must end with exactly one newline")
        body = t[:-1] if t.endswith("\n") else t
        if not body.strip():
            err(f"{path} is empty")
        if len(body) > limit:
            err(f"{path} has {len(body)} characters > {limit}")
        if "com.uberspot.a2048" in body:
            err(f"{path} mentions com.uberspot.a2048")
        if NON_LATIN_DIGITS.search(body):
            err(f"{path} has non-Latin digits (use 0-9, D-103)")
        if name == "title.txt":
            if body != "2048":
                err(f"{path} is not exactly '2048' (U2)")
            continue
        if DENY.search(body):
            err(f"{path} contains a D-114 deny-word (port / WebView / long touch)")
        if loc == "en-US":
            en[name] = body
        elif body == en.get(name):
            err(f"{path} is identical to en-US (untranslated)")
        if loc in SCRIPT and not re.search(f"[{SCRIPT[loc]}]", body):
            err(f"{path} has no {loc} script letters")
        if name == "full_description.txt":
            for needle in NEEDLES:
                if needle not in body:
                    err(f"{path} must contain {needle} (D-005, D-114)")
    if loc != "en-US" and os.path.exists(os.path.join(base, "images")):
        err(f"{base}/images exists; graphics live only under en-US/images (D-113)")
sys.exit(0 if ok else 1)
PY
}

# [M5] D-115/D-152, D-190 + FEATURES F30-AC9: exactly the D-115 image set, 8-bit PNGs at exact
# sizes; color type 2 (RGB, no alpha) for the feature graphic and every screenshot, 2 or 6 for the icon.
check_store_images() {
  python3 -I - << 'PY' || fail=1
import os, struct, sys
img = "fastlane/metadata/android/en-US/images"
spec = {
    "icon.png": (512, 512, {2, 6}),
    "featureGraphic.png": (1024, 500, {2}),
    **{f"phoneScreenshots/{i}.png": (1080, 1920, {2}) for i in range(1, 5)},
    "sevenInchScreenshots/1.png": (2134, 1200, {2}),
    "tenInchScreenshots/1.png": (2560, 1440, {2}),
}
ok = True
def err(msg):
    global ok
    ok = False
    print(f"::error::check-metadata: {msg}")
have = sorted(
    os.path.relpath(os.path.join(d, f), img).replace(os.sep, "/")
    for d, _, files in os.walk(img) for f in files
)
if have != sorted(spec):
    err(f"{img} holds {have}; expected exactly {sorted(spec)} (D-113, D-115)")
for rel, (w, h, types) in spec.items():
    path = os.path.join(img, rel)
    try:
        with open(path, "rb") as f:
            head = f.read(26)
    except OSError:
        continue  # reported by the file-set check above
    if len(head) < 26 or head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        err(f"{path} is not a PNG")
        continue
    width, height, depth, color_type = struct.unpack(">IIBB", head[16:26])
    if (width, height) != (w, h):
        err(f"{path} is {width}x{height}, expected {w}x{h}")
    if depth != 8:
        err(f"{path} has bit depth {depth}, expected 8")
    if color_type not in types:
        err(f"{path} has PNG color type {color_type}, expected {sorted(types)} (2 = RGB, no alpha)")
sys.exit(0 if ok else 1)
PY
}

# [M5] D-121/D-123, D-190 + FEATURES F26-AC5, F27-AC5: NOTICE, PRIVACY.md and README links.
check_legal_and_privacy() {
  local item privacy
  local -a items=('gabrielecirulli/2048' "A small clone of 1024, based on Saming's 2048 (also a clone)"
    'uberspot/2048-android' 'Material Symbols' 'Apache-2.0' 'AndroidX')
  # NOTICE item 3 exists only with the font; the D-076 fallback omits both (L10N_STORE_LEGAL.md §4.1).
  [[ -f app/src/main/res/font/google_sans_flex_numerals.ttf ]] && items+=('Google Sans Flex' 'OFL-1.1')
  for item in "${items[@]}"; do
    grep -qF "$item" NOTICE || err "NOTICE does not contain: $item"
  done
  privacy="This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup."
  grep -qF "$privacy" PRIVACY.md || err "PRIVACY.md does not contain the D-123 text verbatim"
  grep -qF '(PRIVACY.md)' README.md || err "README.md does not link PRIVACY.md"
  grep -qF '(NOTICE)' README.md || err "README.md does not link NOTICE"
  grep -qF 'DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' README.md || err "README.md does not name the self-permission"
  grep -qF 'DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' PRIVACY.md || err "PRIVACY.md does not name the self-permission"
}

# [M5] D-120/D-122, D-190 + FEATURES F26-AC5: LICENSE is the final text of L10N_STORE_LEGAL.md §4.2,
# the in-app MIT text is a byte copy of it, and the Apache-2.0 text is the canonical one.
check_legal_texts() {
  local mit=app/src/main/res/raw/license_mit.txt apache=app/src/main/res/raw/license_apache_2_0.txt
  sha256sum --status -c <<< "ad96d837d59c50cb3c8e23a17d7ee19a396c5a1a6c4bc952a269896fcbdbfd19  LICENSE" ||
    err "LICENSE sha256 differs from L10N_STORE_LEGAL.md §4.2"
  cmp -s LICENSE "$mit" || err "$mit is not byte-identical to LICENSE"
  sha256sum --status -c <<< "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30  $apache" ||
    err "$apache is not the canonical Apache-2.0 text (sha256)"
}

# [M5] D-208, D-190 (L10N_STORE_LEGAL.md §1.4): the M3-M4 holding file for untranslated English
# strings is gone; from M5 on every string arrives with its 20 translations.
check_strings_final() {
  local pending=app/src/main/res/values/strings_pending.xml
  [[ ! -e "$pending" ]] || err "$pending exists; merge it into values/strings.xml (D-208)"
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
check_font              # [M4]
check_store_listing     # [M5]
check_store_images      # [M5]
check_legal_and_privacy # [M5]
check_legal_texts       # [M5]
check_strings_final     # [M5]

[[ "$fail" -eq 0 ]] && echo "check-metadata: OK"
exit "$fail"
