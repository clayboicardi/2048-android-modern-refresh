#!/usr/bin/env bash
# Test-count floor (D-149; rules in docs/modernization/QUALITY.md §1.15).
# Reads the JUnit XML that the last test run wrote. For each module it requires:
#   executed = tests - skipped > 0, executed >= the floor in config/test-count-floor.txt,
#   skipped == 0, failures + errors == 0.
# It also requires the Roborazzi results of the last verify run to match app/src/test/screenshots/
# exactly (D-179): no results at all fails, whatever the number of goldens; every golden was
# compared (no orphan); every result names a golden; every result is `unchanged`.
set -uo pipefail
cd "$(dirname "$0")/.."

python3 -I - <<'PY'
import glob, json, os, re, sys

SUITE = re.compile(r"<testsuite\b([^>]*)>")
ATTR = re.compile(r'(\w+)="([^"]*)"')

floors = {}
with open("config/test-count-floor.txt", encoding="utf-8") as f:
    for raw in f:
        line = raw.split("#", 1)[0].strip()
        if line:
            key, value = line.split("=", 1)
            floors[key.strip()] = int(value)

modules = {
    "engine": "core/engine/build/test-results/test",
    "app": "app/build/test-results/testDebugUnitTest",
}
ok = True
summary = []
for name, results in modules.items():
    tests = skipped = failures = errors = 0
    for path in sorted(glob.glob(os.path.join(results, "*.xml"))):
        with open(path, encoding="utf-8") as f:
            match = SUITE.search(f.read())
        if match is None:
            print(f"::error::check-test-counts: {path} has no <testsuite> element")
            ok = False
            continue
        attrs = dict(ATTR.findall(match.group(1)))
        tests += int(attrs.get("tests", 0))
        skipped += int(attrs.get("skipped", 0))
        failures += int(attrs.get("failures", 0))
        errors += int(attrs.get("errors", 0))
    executed = tests - skipped
    floor = floors.get(name)
    summary.append(f"{name}={executed}")
    problems = []
    if floor is None:
        problems.append(f"no '{name}=' line in config/test-count-floor.txt")
    elif executed < floor:
        problems.append(f"executed {executed} < floor {floor}")
    if executed == 0:
        problems.append(f"no executed tests under {results}")
    if skipped:
        problems.append(f"{skipped} skipped (ignored/disabled tests are not allowed)")
    if failures or errors:
        problems.append(f"{failures} failures, {errors} errors")
    for p in problems:
        print(f"::error::check-test-counts: {name}: {p}")
        ok = False

# Goldens (D-147, D-179; rules G1-G5 in QUALITY.md §1.15). The full gate runs
# :app:testDebugUnitTest -Proborazzi.test.verify=true, so its Roborazzi results must match the
# golden directory exactly. There is no "no results, skip" path.
GOLDEN_DIR = "app/src/test/screenshots"
RESULT_DIR = "app/build/test-results/roborazzi"
goldens = {os.path.normpath(p) for p in glob.glob(f"{GOLDEN_DIR}/**/*.png", recursive=True)}
results = {}  # golden path relative to the repository root -> set of Roborazzi result types
for path in sorted(glob.glob(f"{RESULT_DIR}/**/*.json", recursive=True)):
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:  # G5: a truncated result file could hide a comparison
        print(f"::error::check-test-counts: unreadable Roborazzi result {path}: {e}")
        ok = False
        continue
    stack = [data]
    while stack:
        item = stack.pop()
        if isinstance(item, dict):
            value = item.get("golden_file_path")
            if isinstance(value, str):
                full = value if os.path.isabs(value) else os.path.join("app", value)
                key = os.path.normpath(os.path.relpath(full))
                results.setdefault(key, set()).add(str(item.get("type")))
            stack.extend(item.values())
        elif isinstance(item, list):
            stack.extend(item)
if not results:  # G1
    print(
        f"::error::check-test-counts: {len(goldens)} golden(s) under {GOLDEN_DIR}/ but no"
        f" Roborazzi comparison results under {RESULT_DIR}/; screenshot verification did not run"
    )
    ok = False
else:
    for golden in sorted(goldens - results.keys()):  # G2
        print(f"::error::check-test-counts: golden {golden} was not compared by any test (orphan)")
        ok = False
    for golden in sorted(results.keys() - goldens):  # G3
        print(
            f"::error::check-test-counts: a test captured {golden}, which is not a golden"
            f" under {GOLDEN_DIR}/"
        )
        ok = False
    for golden in sorted(results.keys() & goldens):  # G4
        if results[golden] != {"unchanged"}:
            kinds = ", ".join(sorted(results[golden]))
            print(
                f"::error::check-test-counts: golden {golden} has result type {kinds};"
                " the verify run must report unchanged"
            )
            ok = False

print("check-test-counts: " + " ".join(summary) + (" OK" if ok else " FAILED"))
sys.exit(0 if ok else 1)
PY
