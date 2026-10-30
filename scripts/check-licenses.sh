#!/usr/bin/env bash
# License coverage of the release runtime classpath (D-122; QUALITY.md §1.16).
# Every Maven group resolved on :app:releaseRuntimeClasspath must have exactly one line
# "<group> <licenseId>" in config/license-groups.txt, and the file lists no other group.
set -uo pipefail
cd "$(dirname "$0")/.."
read -r -a EXTRA <<< "${VERIFY_GRADLE_ARGS:-}"
ALLOWED_LICENSES='^(Apache-2\.0|MIT|OFL-1\.1)$'

deps="$(./gradlew -q "${EXTRA[@]}" :app:dependencies --configuration releaseRuntimeClasspath)" ||
  { echo "::error::check-licenses: :app:dependencies failed"; exit 1; }
resolved="$(sed -nE 's/^[|\\+ -]*--- ([^: ]+):[^: ]+.*$/\1/p' <<< "$deps" | sort -u)"
listed="$(sed -E 's/#.*$//' config/license-groups.txt | awk 'NF { print $1 }' | sort)"

fail=0
if [[ -z "$resolved" ]]; then echo "::error::check-licenses: no groups parsed"; fail=1; fi
bad="$(sed -E 's/#.*$//' config/license-groups.txt | awk 'NF' | awk -v re="$ALLOWED_LICENSES" 'NF != 2 || $2 !~ re')"
if [[ -n "$bad" ]]; then echo "::error::check-licenses: malformed lines (need '<group> <licenseId>'):"; echo "$bad"; fail=1; fi
dupes="$(uniq -d <<< "$listed")"
if [[ -n "$dupes" ]]; then echo "::error::check-licenses: duplicate groups: $dupes"; fail=1; fi
missing="$(comm -23 <(printf '%s\n' "$resolved") <(sort -u <<< "$listed"))"
if [[ -n "$missing" ]]; then
  echo "::error::check-licenses: groups on releaseRuntimeClasspath missing from config/license-groups.txt:"
  echo "$missing"
  fail=1
fi
stale="$(comm -13 <(printf '%s\n' "$resolved") <(sort -u <<< "$listed"))"
if [[ -n "$stale" ]]; then
  echo "::error::check-licenses: config/license-groups.txt lists groups that are not on the classpath:"
  echo "$stale"
  fail=1
fi
[[ "$fail" -eq 0 ]] && echo "check-licenses: $(wc -l <<< "$resolved") groups covered"
exit "$fail"
