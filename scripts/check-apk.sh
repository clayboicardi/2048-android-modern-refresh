#!/usr/bin/env bash
# APK and merged-manifest gates (D-148; docs/modernization/QUALITY.md §1.13, ARCHITECTURE.md §8.6).
# Inspects the APKs written by :app:assembleDebug and :app:assembleRelease. Exit 0 = all hold.
set -uo pipefail
cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f local.properties ]]; then sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"; fi
BT="$sdk/build-tools/${ANDROID_BUILD_TOOLS:-37.0.0}"
for tool in aapt2 zipalign apksigner; do
  [[ -x "$BT/$tool" ]] || { echo "::error::check-apk: $BT/$tool not found (set ANDROID_HOME)"; exit 1; }
done
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"

APP_ID=com.clayboicardi.game2048
MAX_RELEASE_BYTES=3145728 # 3.0 MiB (D-148 item 4)
CERT="$(tr -d '[:space:]' < signing/ci-sideload.sha256)"
DENY_RE='com\.google\.(android\.gms|firebase|android\.play|android\.ump|android\.datatransport)|com\.google\.firebase|com\.crashlytics|io\.sentry|com\.squareup\.okhttp3|io\.ktor|ui-text-google-fonts|androidx\.work|androidx\.glance'
RELEASE_EXPORTED="$APP_ID.MainActivity
androidx.profileinstaller.ProfileInstallReceiver"
DEBUG_EXPORTED="$RELEASE_EXPORTED
androidx.activity.ComponentActivity"

fail=0
err() { echo "::error::check-apk: $*"; fail=1; }

# One fully qualified class per line for every component with android:exported=true.
exported_components() {
  "$BT/aapt2" dump xmltree --file AndroidManifest.xml "$1" | awk '
    /E: (activity|activity-alias|service|receiver|provider) / { name = ""; inside = 1; next }
    /E: / && !/E: (intent-filter|action|category|data|meta-data)/ { inside = 0 }
    inside && name == "" && /android:name\(0x01010003\)=/ {
      match($0, /="[^"]+"/); name = substr($0, RSTART + 2, RLENGTH - 3)
    }
    inside && /android:exported\(0x01010010\)=true/ { print name }
  ' | sort -u
}

check_apk() { # apk package label expected-exported-set
  local apk="$1" pkg="$2" label="$3" expected="$4" badging actual perms digests
  if [[ ! -f "$apk" ]]; then err "$apk is missing"; return; fi
  badging="$("$BT/aapt2" dump badging "$apk")" || { err "aapt2 dump badging failed: $apk"; return; }

  # 1. Badging (D-130, D-271).
  grep -qF "package: name='$pkg' versionCode='10000' versionName='1.0.0'" <<< "$badging" ||
    err "$apk: package line is not name='$pkg' versionCode='10000' versionName='1.0.0'"
  grep -qxF "application-label:'$label'" <<< "$badging" || err "$apk: label is not '$label'"
  grep -qxF "minSdkVersion:'24'" <<< "$badging" || err "$apk: minSdkVersion is not 24"
  grep -qxF "targetSdkVersion:'37'" <<< "$badging" || err "$apk: targetSdkVersion is not 37"

  # 2. Permissions: exactly androidx.core's signature-level self-permission (D-002).
  perms="$(grep -E '^uses-permission' <<< "$badging" | sort -u)"
  if [[ "$perms" != "uses-permission: name='$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'" ]]; then
    err "$apk: uses-permission set differs from the allowlist:"
    printf '%s\n' "$perms"
  fi

  # 3. Exported components.
  actual="$(exported_components "$apk")"
  if [[ "$actual" != "$(sort -u <<< "$expected")" ]]; then
    err "$apk: exported components differ (< expected, > actual):"
    diff <(sort -u <<< "$expected") <(printf '%s\n' "$actual")
  fi

  # 5. 16 KB alignment.
  "$BT/zipalign" -c -P 16 -v 4 "$apk" > /dev/null || err "$apk: zipalign -c -P 16 failed"

  # 6. Signer = the committed CI sideload certificate (D-131).
  digests="$("$BT/apksigner" verify --print-certs "$apk" 2> /dev/null |
    sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: //p' | sort -u)"
  [[ "$digests" == "$CERT" ]] || err "$apk: signer digest '$digests' != signing/ci-sideload.sha256 '$CERT'"
}

check_apk app/build/outputs/apk/release/app-release.apk "$APP_ID" "2048" "$RELEASE_EXPORTED"
check_apk app/build/outputs/apk/debug/app-debug.apk "$APP_ID.debug" "2048 Debug" "$DEBUG_EXPORTED"

# 4. Size (release only).
release=app/build/outputs/apk/release/app-release.apk
if [[ -f "$release" ]]; then
  size="$(stat -c %s "$release")"
  echo "check-apk: release APK $size bytes (budget $MAX_RELEASE_BYTES)"
  ((size <= MAX_RELEASE_BYTES)) || err "release APK is $size bytes > $MAX_RELEASE_BYTES"
fi

# 7. Dependency deny-list on the release runtime classpath.
if deps="$(./gradlew -q "${EXTRA[@]}" :app:dependencies --configuration releaseRuntimeClasspath)"; then
  if hits="$(grep -E "$DENY_RE" <<< "$deps")"; then
    err "denied dependency on releaseRuntimeClasspath:"
    printf '%s\n' "$hits"
  fi
else
  err ":app:dependencies failed"
fi

[[ "$fail" -eq 0 ]] && echo "check-apk: OK"
exit "$fail"
