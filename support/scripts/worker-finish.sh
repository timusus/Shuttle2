#!/usr/bin/env bash
# A worker's wrap-up in one call: format + lint the changed Kotlin, run the changed tests, commit.
#
#   support/scripts/worker-finish.sh [--no-test] "<commit message>"
#
# The message must carry the `Changelog:` trailer when the commit-msg hook asks for one
# (`Changelog: none` for tooling). --no-test skips the test run, for docs-only changes.
# Exits non-zero WITHOUT committing if lint or the tests fail, naming the first error.
# Run it in the foreground: the tests can take minutes.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

run_tests=1
if [ "${1:-}" = "--no-test" ]; then
  run_tests=0
  shift
fi
msg="${1:-}"
if [ -z "$msg" ] || [ $# -ne 1 ]; then
  echo 'usage: worker-finish.sh [--no-test] "<commit message>"' >&2
  exit 2
fi

log="$(mktemp -t worker-finish.XXXXXX)"
trap 'rm -f "$log"' EXIT

# First error line, with file:line when the tool gave one.
first_error() {
  grep -m1 -E '(\.kts?:[0-9]+|^e: |FAILED|error:)' "$log" | sed "s|$PWD/||; s|file://||" | cut -c1-240
}

if support/scripts/lint -F >"$log" 2>&1; then
  if grep -q 'no changed Kotlin files' "$log"; then
    echo "lint: no changed Kotlin files"
  else
    echo "lint: ok"
  fi
else
  echo "lint: FAILED (not committed)"
  first_error
  exit 1
fi

if [ "$run_tests" = 1 ]; then
  if ! support/scripts/unit-test --changed-tests >"$log" 2>&1; then
    echo "tests: FAILED (not committed)"
    first_error
    exit 1
  fi
  if grep -qE 'No modules affected|nothing to run' "$log"; then
    if ! support/scripts/unit-test --changed >"$log" 2>&1; then
      echo "tests: FAILED (not committed)"
      first_error
      exit 1
    fi
  fi
  if grep -qE 'No modules affected|No changes against|nothing to run' "$log"; then
    echo "tests: nothing to run for these changes"
  else
    classes="$(grep -Eo '[0-9]+ class\(es\)' "$log" | awk '{n+=$1} END {print n+0}')"
    whole="$(grep -c 'running whole' "$log")"
    echo "tests: ok ($classes mapped classes, $whole whole-module runs)"
  fi
else
  echo "tests: skipped (--no-test)"
fi

git add -A
if ! git commit -q -m "$msg" >"$log" 2>&1; then
  echo "commit: FAILED"
  head -n 3 "$log"
  exit 1
fi
git log -1 --format='commit: %h %s'
git diff --stat HEAD~1 | tail -1
