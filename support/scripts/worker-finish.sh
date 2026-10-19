#!/usr/bin/env bash
# A worker's wrap-up in one call: format + lint the changed Kotlin, run the changed tests, commit.
#
#   support/scripts/worker-finish.sh [--no-test] [--dry-run] [--record-screenshots] "<commit message>"
#
# Steps: lint -F, native test names, `unit-test --changed-tests`, then Roborazzi for the modules whose
# UI changed (verify, or re-record with --record-screenshots), then commit. Anything wider (architecture
# tests, dependents' test compile, the other modules' screenshots) is the landing verify's job
# (support/scripts/land-verify). The iOS test target is compiled when the diff touches what Swift sees.
# Roborazzi runs for a module that applies the plugin when its main source (src/main, src/androidMain,
# src/commonMain) or tests/snapshots (src/test) changed, for :android:designsystem when
# docs/design/catalog changed, for :android:app when other docs/design changed, and for :android:app
# when a @Composable file changed in a module with no screenshots of its own. :android:app is always
# verified alongside, as its goldens render the other modules' components.
# --record-screenshots lists the PNGs it changed (designsystem via support/scripts/catalog, which also
# rewrites the index pages); with --dry-run it restores them afterwards.
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
if [ -z "$msg" ] || [ $# -ne 1 ] || [ "${msg#--}" != "$msg" ]; then
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
    line="$(grep -m1 -E '(\.kts?:[0-9]+|\.swift:[0-9]+|^e:|FAILED|error:)' "$log" | sed "s|$PWD/||; s|file://||" | cut -c1-240)"
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
    # --no-renames lists both sides of a move, so a file moved out of a module still triggers it.
    git -c core.quotePath=false diff --no-renames --name-only "$base" --
    git -c core.quotePath=false ls-files --others --exclude-standard
  } | sort -u
}

# Roborazzi baselines, only for the module whose UI changed (see header).
changed="$(changed_files)"
shot_tasks=()
add_shot_module() {
  local e
  for e in ${shot_tasks[@]+"${shot_tasks[@]}"}; do [ "$e" = "$1" ] && return 0; done
  shot_tasks+=("$1")
}
has_roborazzi() {
  grep -Eq 'libs\.plugins\.roborazzi|io\.github\.takahirom\.roborazzi' "$1"/build.gradle* 2>/dev/null
}
# Where a module's goldens live (the app's tests write to docs/design, designsystem's to its catalogue).
snap_dir() {
  if [ "$1" = :android:designsystem ]; then
    echo docs/design/catalog
  elif [ "$1" = :android:app ]; then
    echo docs/design
  else
    local p="${1#:}"
    echo "${p//://}/src/test/snapshots/images"
  fi
}
while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    docs/design/catalog/*) add_shot_module :android:designsystem ;;
    docs/design/*) add_shot_module :android:app ;;
    android/*/src/main/*|android/*/src/androidMain/*|android/*/src/commonMain/*|shared/src/main/*|shared/src/androidMain/*|shared/src/commonMain/*)
      mdir="${f%%/src/*}"
      if has_roborazzi "$mdir"; then
        add_shot_module ":${mdir//\//:}"
      elif [ "${f%.kt}" != "$f" ] && { [ ! -e "$f" ] || grep -q '@Composable' "$f" 2>/dev/null; }; then
        # A deleted or moved .kt can't be grepped, so it counts as a UI change.
        add_shot_module :android:app
      fi ;;
    android/*/src/test/*|shared/src/test/*)
      mdir="${f%%/src/*}"
      has_roborazzi "$mdir" && add_shot_module ":${mdir//\//:}" ;;
  esac
done <<EOF2
$changed
EOF2

# Only what this run changed, so a dry run restores nothing the worker had already touched.
list_recorded() {
  local after
  after="$(git -c core.quotePath=false status --porcelain -uall -- "${snap_dirs[@]}")"
  recorded="$(comm -13 <(printf '%s\n' "$before_pngs" | sort) <(printf '%s\n' "$after" | sort) | grep -v '^$' || true)"
}

# Tracked PNGs revert, new ones are removed; called on success and failure so a dry run never leaves the tree dirty.
restore_recorded() {
  local line p
  list_recorded
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    p="${line:3}"
    case "$line" in
      '??'*) rm -f -- "$p" ;;
      *) git checkout -- "$p" ;;
    esac
  done <<EOF3
$recorded
EOF3
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

  if [ "${#shot_tasks[@]}" -gt 0 ]; then
    # The app's goldens render the other modules' components, so any UI change verifies it too.
    add_shot_module :android:app
    if [ "$record" = 1 ]; then
      verb=recordRoborazziDebug
    else
      verb=verifyRoborazziDebug
    fi
    snap_dirs=()
    for m in "${shot_tasks[@]}"; do snap_dirs+=("$(snap_dir "$m")"); done
    before_pngs="$(git -c core.quotePath=false status --porcelain -uall -- "${snap_dirs[@]}")"
    gradle_args=()
    catalog=0
    for m in "${shot_tasks[@]}"; do
      # The catalogue script records designsystem and regenerates its index pages.
      if [ "$record" = 1 ] && [ "$m" = :android:designsystem ]; then
        catalog=1
      else
        gradle_args+=("$m:$verb")
      fi
    done
    start=$SECONDS
    # Record stays on the Mac (goldens are Mac-recorded), and --local keeps verify beside them.
    ok=1
    if [ "${#gradle_args[@]}" -gt 0 ]; then
      support/scripts/remote-build.sh --local -q "${gradle_args[@]}" >"$log" 2>&1 || ok=0
    fi
    if [ "$ok" = 1 ] && [ "$catalog" = 1 ]; then
      support/scripts/catalog >>"$log" 2>&1 || ok=0
    fi
    if [ "$ok" = 1 ]; then
      echo "screenshots ($verb ${shot_tasks[*]}): ok ($((SECONDS - start))s)"
    else
      echo "screenshots ($verb ${shot_tasks[*]}): FAILED (not committed)"
      fail_summary
      [ "$record" = 1 ] && [ "$dry_run" = 1 ] && restore_recorded
      [ "$record" = 0 ] && echo "intended UI change? rerun with --record-screenshots, inspect the compare images"
      exit 1
    fi
    if [ "$record" = 1 ]; then
      list_recorded
      if [ -n "$recorded" ]; then
        echo "re-recorded PNGs:"
        printf '%s\n' "$recorded"
      else
        echo "re-recorded PNGs: none changed"
      fi
    fi
  elif [ "$record" = 1 ]; then
    echo "screenshots: no Roborazzi module changed, nothing to record"
  fi
elif [ "$record" = 1 ]; then
  echo "--record-screenshots needs the test step; drop --no-test" >&2
  exit 2
elif [ "${#shot_tasks[@]}" -gt 0 ]; then
  echo "--no-test would skip the screenshot verify for ${shot_tasks[*]}; drop --no-test" >&2
  exit 2
else
  echo "tests: skipped (--no-test)"
fi

# iOS test-target compile, only when a file the Swift side sees changed. Cheap next to a failed land verify.
ios_touch="$(printf '%s\n' "$changed" | grep -E '^(ios/(S2|S2Tests|S2Widgets|SystemSurfaces|Config|project\.yml)|shared/.*\.kt$|android/(domain|presentation|core)/.*\.kt$)' || true)"
if [ "$run_tests" = 1 ] && [ -n "$ios_touch" ] && command -v xcodebuild >/dev/null 2>&1 && command -v xcodegen >/dev/null 2>&1; then
  start=$SECONDS
  if ! { (cd ios && xcodegen -q) && ios/scripts/build-framework.sh && ios/scripts/build-app.sh; } >"$log" 2>&1; then
    fail "ios test compile"
  fi
  echo "ios test compile: ok ($((SECONDS - start))s)"
fi

if [ "$dry_run" = 1 ]; then
  if [ "$record" = 1 ] && [ -n "${recorded:-}" ]; then
    restore_recorded
    echo "dry run: re-recorded PNGs restored"
  fi
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
