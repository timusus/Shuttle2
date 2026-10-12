#!/usr/bin/env bash
# A worker's wrap-up in one call: format + lint the changed Kotlin, run the changed tests, commit.
#
#   support/scripts/worker-finish.sh [--no-test] [--dry-run] [--record-screenshots] "<commit message>"
#
# Steps: lint -F, native test names, `unit-test --changed-tests`, then Roborazzi for the modules whose
# UI changed (verify, or re-record with --record-screenshots), then commit. Anything wider (architecture
# tests, iOS test compile, dependents' test compile, the other modules' screenshots) is the landing
# verify's job (support/scripts/land-verify).
# Roborazzi runs for a module that applies the plugin when its main source (src/main, src/androidMain,
# src/commonMain) changed, for :android:app when docs/design changed, and for :android:app when a
# @Composable file changed in a module with no screenshots of its own.
# The message must carry the `Changelog:` trailer when the commit-msg hook asks for one
# (`Changelog: none` for tooling). --no-test skips the test run, for docs-only changes.
# --dry-run runs every check but stages and commits nothing. --record-screenshots re-records those
# modules' baselines instead of verifying them and commits the PNGs with the change.
# Exits non-zero WITHOUT committing on failure, naming the failing task/tests and keeping the log
# (build/worker-finish/last.log). Run it in the foreground: the tests can take minutes.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

run_tests=1
dry_run=0
record=0
while [ $# -gt 1 ]; do
  case "$1" in
    --no-test) run_tests=0 ;;
    --dry-run) dry_run=1 ;;
    --record-screenshots) record=1 ;;
    *) break ;;
  esac
  shift
done
msg="${1:-}"
if [ -z "$msg" ] || [ $# -ne 1 ]; then
  echo 'usage: worker-finish.sh [--no-test] [--dry-run] [--record-screenshots] "<commit message>"' >&2
  exit 2
fi

mkdir -p build/worker-finish
log="$PWD/build/worker-finish/last.log"

# Prints the failing Gradle task(s), the failing tests and the first error, then the log path.
fail_summary() {
  grep -E "Execution failed for task" "$log" | sed 's/^ *//' | sort -u | head -n 5
  local failed line
  failed="$(awk '/^Failed tests:/ {p=1; next} p && /^$/ {p=0} p' "$log" | cut -c1-240 | sort -u | head -n 20)"
  if [ -n "$failed" ]; then
    echo "failed tests:"
    printf '%s\n' "$failed"
  else
    line="$(grep -m1 -E '(\.kts?:[0-9]+|^e: |FAILED|error:)' "$log" | sed "s|$PWD/||; s|file://||" | cut -c1-240)"
    if [ -n "$line" ]; then printf '%s\n' "$line"; else tail -n 5 "$log" | cut -c1-240; fi
  fi
  echo "log: $log"
}

fail() {
  echo "$1: FAILED (not committed)"
  fail_summary
  exit 1
}

if support/scripts/lint -F >"$log" 2>&1; then
  if grep -q 'no changed Kotlin files' "$log"; then
    echo "lint: no changed Kotlin files"
  else
    echo "lint: ok"
  fi
else
  fail lint
fi

if ! support/scripts/native-test-names >"$log" 2>&1; then
  echo "native test names: FAILED (not committed)"
  cat "$log"
  echo "log: $log"
  exit 1
fi

changed_files() {
  local base
  base="$(git merge-base HEAD origin/main 2>/dev/null || echo HEAD)"
  {
    git -c core.quotePath=false diff --name-only "$base" --
    git -c core.quotePath=false ls-files --others --exclude-standard
  } | sort -u
}

if [ "$run_tests" = 1 ]; then
  start=$SECONDS
  if ! support/scripts/unit-test --changed-tests >"$log" 2>&1; then
    fail tests
  fi
  if grep -qE 'No modules affected|No changes against|nothing to run' "$log"; then
    echo "tests: nothing to run for these changes"
  else
    classes="$(grep -Eo '[0-9]+ class\(es\)' "$log" | awk '{n+=$1} END {print n+0}')"
    whole="$(grep -c 'running whole' "$log")"
    echo "tests: ok ($classes mapped classes, $whole whole-module runs, $((SECONDS - start))s)"
  fi

  # Roborazzi baselines, only for the module whose UI changed (see header).
  changed="$(changed_files)"
  shot_tasks=()
  add_shot_module() {
    local e
    for e in ${shot_tasks[@]+"${shot_tasks[@]}"}; do [ "$e" = "$1" ] && return 0; done
    shot_tasks+=("$1")
  }
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    case "$f" in
      docs/design/*) add_shot_module :android:app ;;
      android/*/src/main/*|android/*/src/androidMain/*|android/*/src/commonMain/*|shared/*/src/main/*|shared/*/src/androidMain/*|shared/*/src/commonMain/*)
        mdir="${f%%/src/*}"
        if grep -Eq 'libs\.plugins\.roborazzi|io\.github\.takahirom\.roborazzi' "$mdir"/build.gradle* 2>/dev/null; then
          add_shot_module ":${mdir//\//:}"
        elif [ "${f%.kt}" != "$f" ] && grep -q '@Composable' "$f" 2>/dev/null; then
          add_shot_module :android:app
        fi ;;
    esac
  done <<EOF2
$changed
EOF2

  if [ "${#shot_tasks[@]}" -gt 0 ]; then
    if [ "$record" = 1 ]; then
      verb=recordRoborazziDebug
    else
      verb=verifyRoborazziDebug
    fi
    gradle_args=()
    for m in "${shot_tasks[@]}"; do gradle_args+=("$m:$verb"); done
    start=$SECONDS
    # Record stays on the Mac (goldens are Mac-recorded), and --local keeps verify beside them.
    if support/scripts/remote-build.sh --local -q "${gradle_args[@]}" >"$log" 2>&1; then
      echo "screenshots ($verb ${shot_tasks[*]}): ok ($((SECONDS - start))s)"
    else
      echo "screenshots ($verb ${shot_tasks[*]}): FAILED (not committed)"
      fail_summary
      [ "$record" = 0 ] && echo "intended UI change? rerun with --record-screenshots, inspect the compare images"
      exit 1
    fi
  elif [ "$record" = 1 ]; then
    echo "screenshots: no Roborazzi module changed, nothing to record"
  fi
elif [ "$record" = 1 ]; then
  echo "--record-screenshots needs the test step; drop --no-test" >&2
  exit 2
else
  echo "tests: skipped (--no-test)"
fi

if [ "$dry_run" = 1 ]; then
  echo "dry run: nothing staged or committed"
  exit 0
fi

# Stage tracked changes, plus untracked files under source paths only; report the rest.
git add -u
skipped=""
while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    *.log|brief-*.md|*/brief-*.md|.claude/longjobs/*|build/*|*/build/*) skipped="$skipped $f"; continue ;;
    android/*|ios/*|shared/*|support/*|docs/*|.claude/rules/*|.claude/skills/*|.githooks/*) git add -- "$f" ;;
    *) skipped="$skipped $f" ;;
  esac
done <<EOF
$(git -c core.quotePath=false ls-files --others --exclude-standard)
EOF
[ -n "$skipped" ] && echo "skipped untracked:$skipped"

if ! git commit -q -m "$msg" >"$log" 2>&1; then
  echo "commit: FAILED"
  cat "$log"
  exit 1
fi
git log -1 --format='commit: %h %s'
git diff --stat HEAD~1 | tail -1
