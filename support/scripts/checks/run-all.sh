#!/usr/bin/env bash
# Run every check in this directory, in order, and summarise. Exits non-zero if any failed.
# Clears the crash/main/system log buffers first so no-crashes.sh, run last, only sees this run.
set -uo pipefail
dir="$(dirname "$0")"
adb logcat -c -b crash -b main -b system 2>/dev/null

# fail() (checks/_lib.sh) drops a marker here when it runs, so a check that instead dies under
# `set -e` (e.g. a failing grep inside a $(...), never reaching fail()) can still be named below
# instead of silently folding into "N check(s) failed".
FAIL_MARKER_DIR="$(mktemp -d)"
export FAIL_MARKER_DIR
trap 'rm -rf "$FAIL_MARKER_DIR"' EXIT

failed=0
failed_names=""

run_check() {
    local check="$1" name status=0
    name="$(basename "$check" .sh)"
    "$check" || status=$?
    if [ "$status" -ne 0 ]; then
        failed=$((failed + 1))
        failed_names="${failed_names:+$failed_names }${name}"
        [ -f "${FAIL_MARKER_DIR}/${name}.failed" ] || echo "FAIL ${name} (exit ${status})" >&2
    fi
}

for check in "$dir"/[a-z]*.sh; do
    name="$(basename "$check")"
    [ "$name" = "run-all.sh" ] && continue
    [ "$name" = "no-crashes.sh" ] && continue
    run_check "$check"
done
run_check "$dir/no-crashes.sh"

if [ "$failed" -eq 0 ]; then
    echo "all checks passed"
else
    echo "${failed} check(s) failed: ${failed_names}" >&2
    exit 1
fi
