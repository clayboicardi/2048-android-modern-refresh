#!/usr/bin/env bash
# Release-key APK gate (D-272; docs/modernization/QUALITY.md §5.11). release.yml runs it after its
# second assembleRelease, the one signed with the owner's private release key.
#
#   ./scripts/check-release-apk.sh <gated.apk> <signed.apk>
#
# <gated.apk> is the release APK that scripts/verify.sh built and checked (signed with ciSideload);
# <signed.apk> is the APK to publish. Exit 0 = the signed APK holds exactly the gated APK's entries,
# byte for byte, and its only signer is the committed signing/release.sha256.
set -uo pipefail
cd "$(dirname "$0")/.."

[[ $# -eq 2 ]] || { echo "usage: $0 <gated.apk> <signed.apk>" >&2; exit 2; }
gated="$1" signed="$2"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f local.properties ]]; then sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"; fi
BT="$sdk/build-tools/${ANDROID_BUILD_TOOLS:-37.0.0}"
for tool in aapt2 zipalign apksigner; do
  [[ -x "$BT/$tool" ]] || { echo "::error::check-release-apk: $BT/$tool not found (set ANDROID_HOME)"; exit 1; }
done

fail=0
err() { echo "::error::check-release-apk: $*"; fail=1; }

# 1. The pinned public certificate digest of the release key.
[[ -f signing/release.sha256 ]] || { err "signing/release.sha256 is missing (signing/README.md)"; exit 1; }
CERT="$(tr -d '[:space:]' < signing/release.sha256)"
CI_CERT="$(tr -d '[:space:]' < signing/ci-sideload.sha256)"
[[ "$CERT" =~ ^[0-9a-f]{64}$ ]] || err "signing/release.sha256 is not 64 lowercase hex digits"
[[ "$CERT" != "$CI_CERT" ]] || err "signing/release.sha256 names the public CI sideload key"
for apk in "$gated" "$signed"; do [[ -f "$apk" ]] || { err "$apk is missing"; exit 1; }; done

# 2. Signer: exactly the release key, and the signature verifies.
if out="$("$BT/apksigner" verify --print-certs "$signed" 2> /dev/null)"; then
  digests="$(sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: //p' <<< "$out" | sort -u)"
  [[ "$digests" == "$CERT" ]] || err "$signed: signer digest '$digests' != signing/release.sha256 '$CERT'"
else
  err "$signed: apksigner verify failed"
fi

# 3. Release flags and 16 KB alignment.
badging="$("$BT/aapt2" dump badging "$signed")" || err "$signed: aapt2 dump badging failed"
grep -q "^package: name='com.clayboicardi.game2048' " <<< "$badging" ||
  err "$signed: package is not com.clayboicardi.game2048"
! grep -q '^application-debuggable' <<< "$badging" || err "$signed: android:debuggable is set"
! grep -q "testOnly='-1'" <<< "$badging" || err "$signed: android:testOnly is set"
"$BT/zipalign" -c -P 16 -v 4 "$signed" > /dev/null || err "$signed: zipalign -c -P 16 failed"

# 4. Same content as the gated APK: entry names, order and bytes. Only the signing block differs.
python3 -I - "$gated" "$signed" << 'PY' || err "$signed: entries differ from the gated $gated"
import sys, zipfile
a, b = (zipfile.ZipFile(p) for p in sys.argv[1:3])
na, nb = a.namelist(), b.namelist()
if na != nb:
    print("entry lists differ:", sorted(set(na) ^ set(nb)) or "same names, other order")
    sys.exit(1)
diff = [n for n in na if a.read(n) != b.read(n)]
for n in diff:
    print("differs:", n)
sys.exit(1 if diff else 0)
PY

[[ "$fail" -eq 0 ]] && echo "check-release-apk: OK ($signed, signer $CERT)"
exit "$fail"
