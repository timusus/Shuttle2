#!/usr/bin/env bash
# Run every check in this directory, in order, and summarise. Exits non-zero if any failed.
set -uo pipefail
dir="$(dirname "$0")"
failed=0
for check in "$dir"/[a-z]*.sh; do
    [ "$(basename "$check")" = "run-all.sh" ] && continue
    "$check" || failed=$((failed + 1))
done
[ "$failed" -eq 0 ] && echo "all checks passed" || { echo "${failed} check(s) failed" >&2; exit 1; }
