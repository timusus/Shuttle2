#!/usr/bin/env bash
# A worker's wrap-up in one call: format + lint the changed Kotlin, run the changed tests, commit.
#
#   support/scripts/worker-finish.sh [--no-test] "<commit message>"
#
# Steps: lint -F, native test names, `unit-test --changed-tests`, then (driven by the diff vs the
# merge-base with origin/main) :android:architecture-tests when Kotlin changed, a module's
# compileTestKotlinIosSimulatorArm64 when a KMP module's commonMain/commonTest/iosMain/iosTest changed
# (deletions count), and verifyRoborazziDebug (plus :android:app's) when main source (src/main,
# src/androidMain, src/commonMain) changed in a Roborazzi module, a docs/design file changed, or a
# @Composable file elsewhere changed, then commit.
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
  local line
  line="$(grep -m1 -E '(\.kts?:[0-9]+|^e: |FAILED|error:)' "$log" | sed "s|$PWD/||; s|file://||" | cut -c1-240)"
  if [ -n "$line" ]; then
    printf '%s\n' "$line"
  else
    tail -n 5 "$log" | cut -c1-240
  fi
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

if ! support/scripts/native-test-names >"$log" 2>&1; then
  echo "native test names: FAILED (not committed)"
  cat "$log"
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

# Checks the landing verify would otherwise be the first to run, driven by the diff vs the merge-base
# with origin/main plus the working tree. Skipped with --no-test (docs-only).
changed_files() {
  local base
  base="$(git merge-base HEAD origin/main 2>/dev/null || echo HEAD)"
  {
    git -c core.quotePath=false diff --name-only "$base" --
    git -c core.quotePath=false ls-files --others --exclude-standard
  } | sort -u
}

# gradle_check <label> <hint> <gradle args...>: foreground Gradle on this Mac; on failure print the
# first error plus the failing tests and fail worker-finish.
gradle_check() {
  local label=$1 hint=$2 start=$SECONDS
  shift 2
  if support/scripts/remote-build.sh --local -q "$@" >"$log" 2>&1; then
    echo "$label: ok ($((SECONDS - start))s)"
    return 0
  fi
  echo "$label: FAILED (not committed)"
  first_error
  grep -E '^\s*[A-Za-z0-9_.$]+ > .*FAILED' "$log" | sed 's/^ *//' | head -n 10
  [ -n "$hint" ] && echo "$hint"
  exit 1
}

if [ "$run_tests" = 1 ]; then
  changed="$(changed_files)"
  kt="$(printf '%s\n' "$changed" | grep -E '\.kt$' || true)"

  if [ -n "$kt" ]; then
    gradle_check "architecture tests" "" :android:architecture-tests:testDebugUnitTest
  fi

  # Kotlin/Native compiles commonMain/commonTest (and iosMain/iosTest) that the JVM run never sees.
  ios_tasks=()
  if [ -n "$kt" ]; then
    kt_args=()
    while IFS= read -r f; do kt_args+=("$f"); done <<EOF3
$kt
EOF3
    if ! native_tasks="$(support/scripts/unit-test --kmp-native-tasks "${kt_args[@]}" 2>"$log")"; then
      echo "ios test compile: FAILED (not committed): unit-test --kmp-native-tasks exited non-zero"
      first_error
      exit 1
    fi
    while IFS= read -r t; do
      [ -n "$t" ] && ios_tasks+=("${t%:iosSimulatorArm64Test}:compileTestKotlinIosSimulatorArm64")
    done <<EOF4
$native_tasks
EOF4
  fi
  if [ "${#ios_tasks[@]}" -gt 0 ]; then
    gradle_check "ios test compile (${#ios_tasks[@]} modules)" "" "${ios_tasks[@]}"
  fi

  # Roborazzi baselines: a changed main source in a module that applies the plugin, or a changed
  # @Composable file anywhere (:android:app's screenshots render other modules' components).
  shot_tasks=()
  add_shot_task() {
    local t="$1:verifyRoborazziDebug" e
    for e in ${shot_tasks[@]+"${shot_tasks[@]}"}; do [ "$e" = "$t" ] && return 0; done
    shot_tasks+=("$t")
  }
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    case "$f" in
      docs/design/*) add_shot_task :android:app ;;
      android/*/src/main/*|android/*/src/androidMain/*|android/*/src/commonMain/*|shared/*/src/main/*|shared/*/src/androidMain/*|shared/*/src/commonMain/*)
        mdir="${f%%/src/*}"
        if grep -Eq 'libs\.plugins\.roborazzi|io\.github\.takahirom\.roborazzi' "$mdir"/build.gradle* 2>/dev/null; then
          add_shot_task ":${mdir//\//:}"
        elif [ "${f%.kt}" != "$f" ] && grep -q '@Composable' "$f" 2>/dev/null; then
          add_shot_task :android:app
        fi ;;
      *.kt)
        if grep -q '@Composable' "$f" 2>/dev/null; then add_shot_task :android:app; fi ;;
    esac
  done <<EOF2
$changed
EOF2
  # :android:app's screenshots render other modules' components (designsystem), so any trigger runs it too.
  if [ "${#shot_tasks[@]}" -gt 0 ]; then add_shot_task :android:app; fi
  if [ "${#shot_tasks[@]}" -gt 0 ]; then
    gradle_check "screenshots (${shot_tasks[*]})" \
      "intended UI change? re-record with record mode (recordRoborazziDebug), inspect the compare image, commit the PNGs" \
      "${shot_tasks[@]}"
  fi
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
