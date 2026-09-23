#!/usr/bin/env bash
# Run every check in this directory, in order, and summarise. Exits non-zero if any failed.
# Clears the crash/main/system log buffers first so no-crashes.sh, run last, only sees this run.
set -uo pipefail
dir="$(dirname "$0")"
adb logcat -c -b crash -b main -b system 2>/dev/null
failed=0
for check in "$dir"/[a-z]*.sh; do
    name="$(basename "$check")"
    [ "$name" = "run-all.sh" ] && continue
    [ "$name" = "no-crashes.sh" ] && continue
    "$check" || failed=$((failed + 1))
done
"$dir/no-crashes.sh" || failed=$((failed + 1))
[ "$failed" -eq 0 ] && echo "all checks passed" || { echo "${failed} check(s) failed" >&2; exit 1; }
