#!/usr/bin/env bash
# Builds the game-numeral subset of Google Sans Flex (D-076; docs/modernization/DESIGN_SYSTEM.md
# §3.1): app/src/main/res/font/google_sans_flex_numerals.ttf, at most 32,768 bytes (D-152,
# check-metadata.sh check_font).
#
# Source: google/fonts, ofl/googlesansflex/GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf (OFL-1.1,
# no Reserved Font Name), at commit
#   9b59ea9f333c5b8a99fa44e2a698496d6cde5b8e
# with sha256
#   a9ac770798857d5e8ac593ced42b22e40a7c08cec98319f92b0eccc2783a77ba
# The OFL text of the same directory is copied verbatim to
# app/src/main/res/raw/ofl_google_sans_flex.txt (D-122), sha256
#   fc13d69f63e36d284b6e383d4d1463d8ad404f0aa065e57cf961252d51063137
# Output: app/src/main/res/font/google_sans_flex_numerals.ttf, 19,016 bytes, sha256
#   3d8049577691260aca85af47ae79e9cf4b9239444b61cde2612394225654d6a7
#
# Tool: fontTools 4.68.0 (`pip install fonttools==4.68.0`; `fonttools` and `pyftsubset` on PATH,
# or FONTTOOLS_BIN set to the directory that holds them).
#
# Usage: scripts/fonts/subset-google-sans-flex.sh [SOURCE_DIR]
#   SOURCE_DIR holds the downloaded source TTF and OFL.txt (default
#   $HOME/.cache/game2048/fonts/src). Both are checked against the sha256 above before use; the
#   download stays outside the repository. Pass --fetch as the first argument to download both
#   files from the pinned commit into SOURCE_DIR first.
#
# The instancer prints harmless OTLOffsetOverflowError repair notices.
set -euo pipefail
cd "$(dirname "$0")/../.."

COMMIT=9b59ea9f333c5b8a99fa44e2a698496d6cde5b8e
FONT_SHA256=a9ac770798857d5e8ac593ced42b22e40a7c08cec98319f92b0eccc2783a77ba
OFL_SHA256=fc13d69f63e36d284b6e383d4d1463d8ad404f0aa065e57cf961252d51063137
FONT_NAME='GoogleSansFlex[ROND,opsz,slnt,wdth,wght].ttf'
FONT_URL_NAME='GoogleSansFlex%5BROND%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf'
BASE_URL="https://raw.githubusercontent.com/google/fonts/$COMMIT/ofl/googlesansflex"

fetch=0
if [[ "${1:-}" == "--fetch" ]]; then
  fetch=1
  shift
fi
src="${1:-$HOME/.cache/game2048/fonts/src}"
bin="${FONTTOOLS_BIN:+$FONTTOOLS_BIN/}"
out=app/src/main/res/font/google_sans_flex_numerals.ttf
ofl=app/src/main/res/raw/ofl_google_sans_flex.txt

if [[ "$fetch" -eq 1 ]]; then
  mkdir -p "$src"
  curl -fsSL -o "$src/$FONT_NAME" "$BASE_URL/$FONT_URL_NAME"
  curl -fsSL -o "$src/OFL.txt" "$BASE_URL/OFL.txt"
fi

check_sha() {
  local file="$1" want="$2" got
  got="$(sha256sum "$file" | cut -d' ' -f1)"
  [[ "$got" == "$want" ]] || {
    echo "subset-google-sans-flex: $file has sha256 $got, expected $want" >&2
    exit 1
  }
}
check_sha "$src/$FONT_NAME" "$FONT_SHA256"
check_sha "$src/OFL.txt" "$OFL_SHA256"

version="$("${bin}python3" -I -c 'import fontTools; print(fontTools.version)' 2> /dev/null ||
  echo missing)"
[[ "$version" == "4.68.0" ]] || {
  echo "subset-google-sans-flex: fontTools 4.68.0 is required, found '$version'" >&2
  exit 1
}

# Reproducible output: fontTools stamps head.modified from SOURCE_DATE_EPOCH; this is the source
# font's own head.modified (2026-10-09 19:58:44 UTC), so the subset keeps it.
export SOURCE_DATE_EPOCH=1791575924

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$(dirname "$out")" "$(dirname "$ofl")"

"${bin}fonttools" varLib.instancer "$src/$FONT_NAME" \
  wght=700:900 wdth=75:100 ROND=0:100 opsz=18 slnt=0 -o "$work/gsf-instanced.ttf"
"${bin}pyftsubset" "$work/gsf-instanced.ttf" \
  --unicodes="U+0020,U+002B,U+0030-0039,U+0045,U+0047,U+004B,U+004D,U+0050,U+0054" \
  --layout-features="tnum,lnum,pnum,kern" --name-IDs='*' \
  --output-file="$out"
cp "$src/OFL.txt" "$ofl"

size="$(stat -c %s "$out")"
echo "subset-google-sans-flex: $out is $size bytes (budget 32,768)"
((size <= 32768)) || {
  echo "subset-google-sans-flex: $out exceeds 32,768 bytes" >&2
  exit 1
}
