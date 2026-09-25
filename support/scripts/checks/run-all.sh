#!/usr/bin/env bash
# Run every check in this directory, in order, and summarise. Exits non-zero if any failed.
# --smoke runs only the checks named in smoke.txt (one per line, # comments), the device smoke set.
# Clears the crash/main/system log buffers first so no-crashes.sh, run last, only sees this run.
set -uo pipefail
dir="$(dirname "$0")"
# shellcheck source=support/scripts/checks/_suite_names.sh
source "$dir/_suite_names.sh"
smoke=0
case "${1:-}" in
    "") ;;
    --smoke) smoke=1 ;;
    *) echo "usage: $0 [--smoke]" >&2; exit 2 ;;
esac
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

if [ "$smoke" -eq 1 ]; then
    while IFS= read -r name; do
        if [ -f "$dir/${name}.sh" ]; then
            run_check "$dir/${name}.sh"
        else
            failed=$((failed + 1))
            failed_names="${failed_names:+$failed_names }${name}"
            echo "FAIL ${name} (listed in smoke.txt, but there is no ${name}.sh)" >&2
        fi
    done < <(suite_smoke_names "$dir")
else
    while IFS= read -r name; do
        run_check "$dir/${name}.sh"
    done < <(suite_all_names "$dir")
fi
run_check "$dir/no-crashes.sh"

if [ "$failed" -eq 0 ]; then
    echo "all checks passed"
else
    echo "${failed} check(s) failed: ${failed_names}" >&2
    exit 1
fi
