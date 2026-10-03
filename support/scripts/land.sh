#!/usr/bin/env bash
# land.sh — the landing queue: cherry-pick approved worktree branches onto origin/main, verify
# once under machine-lock, push, close issues and clean up the worktrees. Meant to run as
#   support/scripts/longjob.sh start land -- support/scripts/land.sh <branch>... [options]
# from the orchestrator's primary checkout (not a feature worktree), so `git push origin
# HEAD:main` pushes the right ref.
#
#   support/scripts/land.sh <branch>... [--close N ...] [--no-push] [--dry-run]
#
#   --close N     close GitHub issue N with "Landed in <sha>" after a successful push
#                 (repeatable)
#   --no-push     do everything except `git push`, issue close and worktree cleanup
#   --dry-run     implies --no-push, and also skips issue close / worktree cleanup
#   --no-device-install
#                 skip the best-effort iPhone install after the push (see below)
#
# Env:
#   LAND_SKIP_DEVICE_INSTALL=1   same as --no-device-install.
#   LAND_SKIP_VERIFY=1   skip the machine-lock verify step entirely. Plumbing tests only —
#                        never use this to land real work.
#
# Sequence: refuse a dirty tree; `git fetch origin main`; hard-reset the current branch onto
# origin/main; for each branch, cherry-pick $(git merge-base origin/main <branch>)..<branch>. A
# branch whose cherry-pick conflicts is aborted and marked "conflict"; later branches still get
# a chance. Verify runs once, in a single `machine-lock --name verify` hold (land.sh re-invokes
# itself with an internal --verify-only mode), over everything landed so far:
# `unit-test --changed`, an assembleDebug, and — only if the picked commits touch ios/, shared/
# or android/domain|presentation|core — a light iOS check: the framework build, an app build
# (`xcodebuild build`, only when no test class maps), and `test.sh -only-testing:` for the test classes mapped from the
# changed files (rule below; no mapped class = build only, no simulator lease). The whole iOS
# suite and the full Android verify run less often, in support/scripts/full-verify.sh (watermark,
# always before a Play release).
# iOS test mapping (`land.sh --print-ios-tests <files...>` prints it; deleted files are ignored):
#   ios/S2Tests/*Tests.swift      runs itself
#   other ios/S2*/ .swift         stem (and stem minus a View/Content/Model suffix): every
#                                 S2Tests/*Tests.swift named <stem>* or mentioning <stem> as a word
#   shared/, android/domain|presentation|core .kt   the same word match on the stem (and, for
#                                 *ViewModel, <name>UiState)
#   ios/Playback/                 also `ios/scripts/test.sh --package`
#   nothing mapped                build only, no simulator lease
# If the verify fails and more than one branch landed, branches are
# dropped one at a time from the end (each drop retried once) until it passes or none remain;
# each dropped branch is reported as having broken verify. On a pass: push (retrying network
# failures up to 3 times), close --close issues, then unlock and worktree-clean.sh each landed
# branch's worktree.
set -uo pipefail

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
REPO_ROOT=$(git rev-parse --show-toplevel) || { echo "land.sh: not a git repo" >&2; exit 2; }
cd "$REPO_ROOT" || exit 2

# Sessions that skip ~/.zshrc (Remote Control, headless workers) lack /usr/sbin on PATH (#718).
export PATH="/usr/sbin:/sbin:$PATH"

# No ssh-agent in those sessions either: when SSH to origin fails, use HTTPS through gh for this run
# only, via env-var git config (nothing written to any config file). Children inherit it.
if [ "${1:-}" != "--verify-only" ] && ! git ls-remote -q origin HEAD >/dev/null 2>&1 \
   && command -v gh >/dev/null 2>&1; then
  export GIT_CONFIG_COUNT=2
  export GIT_CONFIG_KEY_0="url.https://github.com/.insteadOf" GIT_CONFIG_VALUE_0="git@github.com:"
  export GIT_CONFIG_KEY_1="credential.https://github.com.helper" GIT_CONFIG_VALUE_1="!gh auth git-credential"
  echo "land.sh: SSH to origin failed, using HTTPS via gh for this run" >&2
fi

# ios_tests_for <file>...: print the S2Tests classes (one per line, sorted, unique) that the
# changed files map to, per the rule in the header, plus "--package" when ios/Playback changed.
# Needs REPO_ROOT as the cwd.
ios_tests_for() {
  local f stem s t
  for f in "$@"; do
    case "$f" in
      ios/S2Tests/*Tests.swift) [ -e "$f" ] && basename "${f%.swift}"; continue ;;
      ios/Playback/*) echo "--package"; continue ;;
      ios/S2/*.swift|ios/S2Tests/*.swift) stem=$(basename "${f%.swift}") ;;
      shared/*.kt|android/domain/*.kt|android/presentation/*.kt|android/core/*.kt)
        stem=$(basename "${f%.kt}") ;;
      *) continue ;;
    esac
    local stems=("$stem")
    case "$f" in
      *.swift) s=${stem%View}; s=${s%Content}; s=${s%Model}
               [ -n "$s" ] && [ "$s" != "$stem" ] && stems+=("$s") ;;
      *.kt)    case "$stem" in *ViewModel) stems+=("${stem%ViewModel}UiState") ;; esac ;;
    esac
    for s in "${stems[@]}"; do
      case "$f" in
        *.swift) for t in ios/S2Tests/"$s"*Tests.swift; do [ -e "$t" ] && basename "${t%.swift}"; done ;;
      esac
      for t in $(grep -lw -- "$s" ios/S2Tests/*Tests.swift 2>/dev/null); do basename "${t%.swift}"; done
    done
  done | sort -u
}

# Dry-check of the mapping: land.sh --print-ios-tests <files...>
if [ "${1:-}" = "--print-ios-tests" ]; then
  shift
  ios_tests_for "$@"
  exit 0
fi

# Internal mode: the Android and iOS verify phases, run by run_verify below under one
# `machine-lock --name verify` hold. Not for direct use.
#   land.sh --verify-only <origin-main-sha> <touches_ios 0|1> [<S2Tests class>...]
if [ "${1:-}" = "--verify-only" ]; then
  base_sha=${2:?} touches_ios=${3:-0}
  shift 3 || true
  support/scripts/unit-test --changed --base "$base_sha" || { echo "verify: android unit tests failed"; exit 1; }
  support/scripts/remote-build.sh --local -q :android:app:assembleDebug || { echo "verify: assembleDebug failed"; exit 1; }
  if [ "$touches_ios" = 1 ]; then
    rc=0
    classes=() pkg=0
    for c in "$@"; do
      if [ "$c" = "--package" ]; then pkg=1; else classes+=("$c"); fi
    done
    (
      set -e
      cd ios
      xcodegen -q
      scripts/build-framework.sh
      if [ "${#classes[@]}" -gt 0 ]; then
        echo "verify: ios test classes: ${classes[*]}"
        only=()
        for c in "${classes[@]}"; do only+=("-only-testing:S2Tests/$c"); done
        S2_SIM_HOLDER=land scripts/test.sh "${only[@]}"
      else
        echo "verify: no iOS test class maps to the changed files; build only"
        xcodebuild build -project S2.xcodeproj -scheme S2 \
          -destination 'generic/platform=iOS Simulator' -derivedDataPath build/DerivedData -quiet
      fi
      if [ "$pkg" = 1 ]; then
        echo "verify: ios Playback package tests"
        scripts/test.sh --package
      fi
    ) || rc=$?
    # Release under the same holder lease-sim.sh leased as (suffixed when S2_SIM_PROFILE is set).
    CLAUDE_CODE_SESSION_ID="$(S2_SIM_HOLDER=land ios/scripts/lease-sim.sh --holder)" \
      "$HOME/.claude/scripts/ios-sim/sim-lease.sh" release || true
    [ "$rc" -eq 0 ] || { echo "verify: ios step failed (rc=$rc)"; exit 1; }
  fi
  exit 0
fi

mkdir -p .claude/land-logs
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
LOG="$REPO_ROOT/.claude/land-logs/$STAMP.log"
: > "$LOG"
log()  { printf '%s\n' "$*" >> "$LOG"; }
say()  { printf '%s\n' "$*"; printf '%s\n' "$*" >> "$LOG"; }

NO_PUSH=0
DEVICE_INSTALL=1
[ "${LAND_SKIP_DEVICE_INSTALL:-0}" = 1 ] && DEVICE_INSTALL=0
BRANCHES=()
CLOSE_ISSUES=()

while [ $# -gt 0 ]; do
  case "$1" in
    --close)
      [ $# -ge 2 ] || { echo "land.sh: --close needs an issue number" >&2; exit 2; }
      CLOSE_ISSUES+=("$2"); shift 2 ;;
    --no-push) NO_PUSH=1; shift ;;
    --dry-run) NO_PUSH=1; shift ;;
    --no-device-install) DEVICE_INSTALL=0; shift ;;
    -h|--help) sed -n '2,/^set -/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    -*) echo "land.sh: unknown option: $1" >&2; exit 2 ;;
    *) BRANCHES+=("$1"); shift ;;
  esac
done

[ "${#BRANCHES[@]}" -gt 0 ] || { echo "land.sh: no branches given (see --help)" >&2; exit 2; }

if [ -n "$(git status --porcelain)" ]; then
  echo "land.sh: working tree is dirty; commit or stash before landing" >&2
  exit 1
fi

say "land.sh: log at $LOG"
say "land.sh: landing ${BRANCHES[*]}"

log "fetching origin main"
if ! git fetch -q origin main >> "$LOG" 2>&1; then
  say "land.sh: git fetch origin main failed"
  exit 1
fi

CUR_BRANCH=$(git rev-parse --abbrev-ref HEAD)

# Data-loss guard: the reset below discards anything on HEAD that origin/main lacks, and a
# branch being landed must not be the checkout that gets reset.
UNPUSHED=$(git rev-list origin/main..HEAD | wc -l | tr -d ' ')
if [ "$UNPUSHED" -gt 0 ]; then
  say "land.sh: refusing to reset $CUR_BRANCH: HEAD has $UNPUSHED commit(s) not on origin/main (land or push them first)"
  exit 2
fi
for b in "${BRANCHES[@]}"; do
  if [ "$b" = "$CUR_BRANCH" ]; then
    say "land.sh: refusing to land $b from its own checkout; run from the primary checkout"
    exit 2
  fi
done

ORIGIN_MAIN_SHA=$(git rev-parse origin/main)
log "hard-resetting $CUR_BRANCH ($(git rev-parse HEAD)) onto origin/main ($ORIGIN_MAIN_SHA)"
if ! git reset --hard origin/main >> "$LOG" 2>&1; then
  say "land.sh: git reset --hard origin/main failed"
  exit 1
fi

# --- environment guard (#699): the Gradle verify needs the gitignored local.properties (sdk.dir) ---
# Copy it from the primary checkout if this tree lacks it; if it is still missing the verify can't
# run, which is an environment fault, not a branch's: stop before picking so nothing gets blamed.
if [ "${LAND_SKIP_VERIFY:-0}" != 1 ] && [ ! -f local.properties ]; then
  primary=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
  if [ -n "$primary" ] && [ "$primary" != "$REPO_ROOT" ] && [ -f "$primary/local.properties" ]; then
    cp "$primary/local.properties" local.properties && log "copied local.properties from $primary"
  fi
  if [ ! -f local.properties ]; then
    say "land.sh: ENVIRONMENT error: local.properties is missing in $REPO_ROOT and could not be copied from the primary checkout (${primary:-unknown}); no branch was blamed"
    exit 3
  fi
fi

# --- cherry-pick each branch, tracking where it started so it can be dropped later ---------
STATUS=()       # landed | conflict | dropped, one per BRANCHES index
REASON=()
START_SHA=()    # HEAD before this branch's cherry-picks

pick_branch() {  # $1 = index into BRANCHES
  local i=$1 b base
  b=${BRANCHES[$i]}
  START_SHA[$i]=$(git rev-parse HEAD)
  base=$(git merge-base origin/main "$b" 2>/dev/null)
  if [ -z "$base" ]; then
    STATUS[$i]=conflict; REASON[$i]="no merge-base with origin/main"
    log "$b: no merge-base with origin/main"
    return
  fi
  log "$b: cherry-picking ${base}..$b"
  if git cherry-pick "$base..$b" >> "$LOG" 2>&1; then
    STATUS[$i]=landed; REASON[$i]=""
  else
    git cherry-pick --abort >> "$LOG" 2>&1
    STATUS[$i]=conflict; REASON[$i]="cherry-pick conflict"
    log "$b: cherry-pick conflict, aborted"
  fi
}

for i in "${!BRANCHES[@]}"; do
  pick_branch "$i"
done

landed_indices() {
  local i
  for i in "${!BRANCHES[@]}"; do
    [ "${STATUS[$i]}" = landed ] && echo "$i"
  done
}

drop_branch() {  # $1 = index; resets HEAD back to before this branch's picks
  local i=$1
  git reset --hard "${START_SHA[$i]}" >> "$LOG" 2>&1
  STATUS[$i]=dropped
  log "${BRANCHES[$i]}: dropped to isolate a verify failure"
}

# --- verify (once, one machine-lock hold for both phases) ------------------------------------------------------
run_verify() {
  if [ "${LAND_SKIP_VERIFY:-0}" = 1 ]; then
    log "verify: LAND_SKIP_VERIFY=1, skipping"
    return 0
  fi
  local touches_ios=0
  if git diff --name-only "$ORIGIN_MAIN_SHA" HEAD | grep -Eq '^(ios/|shared/|android/domain/|android/presentation/|android/core/)'; then
    touches_ios=1
  fi
  local ios_tests=() changed=() t
  if [ "$touches_ios" = 1 ]; then
    while IFS= read -r -d '' t; do changed+=("$t"); done < <(git diff --name-only -z --diff-filter=d "$ORIGIN_MAIN_SHA" HEAD)
    while IFS= read -r t; do [ -n "$t" ] && ios_tests+=("$t"); done < <(ios_tests_for "${changed[@]}")
  fi
  log "verify: touches_ios=$touches_ios ios_tests=${ios_tests[*]-}"

  local from
  from=$(( $(wc -c < "$LOG") + 1 ))
  if ! machine-lock --name verify -- "$SELF" --verify-only "$ORIGIN_MAIN_SHA" "$touches_ios" ${ios_tests[@]+"${ios_tests[@]}"} >> "$LOG" 2>&1; then
    # Gradle dying in configuration or a transform says nothing about the branch (#717).
    if tail -c +"$from" "$LOG" | grep -Eq 'Configuration cache state could not be cached|Failed to transform|Could not resolve all files for configuration|Execution failed for JdkImageTransform'; then
      log "verify: environment failure (see above)"
      return 2
    fi
    log "verify: failed (see above)"
    return 1
  fi
  return 0
}

LANDED_IDX=()
while IFS= read -r x; do [ -n "$x" ] && LANDED_IDX+=("$x"); done < <(landed_indices)

if [ "${#LANDED_IDX[@]}" -gt 0 ]; then
  say "land.sh: running verify over ${#LANDED_IDX[@]} landed branch(es)"
  run_verify; vrc=$?
  if [ "$vrc" -eq 2 ]; then
    say "land.sh: verify environment failure, branch kept (log: $LOG)"
    exit 1
  fi
  if [ "$vrc" -ne 0 ]; then
    if [ "${#LANDED_IDX[@]}" -eq 1 ]; then
      drop_branch "${LANDED_IDX[0]}"
    else
      remaining=("${LANDED_IDX[@]}")
      passed=0
      while [ "${#remaining[@]}" -gt 1 ]; do
        last=$(( ${#remaining[@]} - 1 ))
        drop_idx=${remaining[$last]}
        drop_branch "$drop_idx"
        say "land.sh: retrying verify without ${BRANCHES[$drop_idx]}"
        unset 'remaining[last]'
        remaining=("${remaining[@]}")
        run_verify; vrc=$?
        if [ "$vrc" -eq 2 ]; then
          say "land.sh: verify environment failure, branch kept (log: $LOG)"
          exit 1
        fi
        if [ "$vrc" -eq 0 ]; then passed=1; break; fi
      done
      if [ "$passed" -eq 0 ] && [ "${#remaining[@]}" -eq 1 ]; then
        drop_branch "${remaining[0]}"
      fi
    fi
  fi
fi

# --- report per-branch status ---------------------------------------------------------------
for i in "${!BRANCHES[@]}"; do
  case "${STATUS[$i]}" in
    landed)   say "${BRANCHES[$i]}: landed" ;;
    conflict) say "${BRANCHES[$i]}: conflict (${REASON[$i]})" ;;
    dropped)  say "${BRANCHES[$i]}: broke verify, dropped" ;;
  esac
done

LANDED_IDX=()
while IFS= read -r x; do [ -n "$x" ] && LANDED_IDX+=("$x"); done < <(landed_indices)
if [ "${#LANDED_IDX[@]}" -eq 0 ]; then
  say "land.sh: nothing landed"
  exit 1
fi

if [ "$NO_PUSH" = 1 ]; then
  say "land.sh: --no-push/--dry-run, stopping before push (log: $LOG)"
  exit 0
fi

# --- push (retry network failures only, up to 3 times) --------------------------------------
push_ok=0
for attempt in 1 2 3; do
  err=$(git push origin HEAD:main 2>&1)
  rc=$?
  log "push attempt $attempt: rc=$rc"
  log "$err"
  if [ "$rc" -eq 0 ]; then push_ok=1; break; fi
  if printf '%s' "$err" | grep -Eqi 'could not resolve host|connection (reset|refused|timed out)|network is unreachable|ssl|tls|timed out'; then
    sleep $((attempt * 3))
    continue
  fi
  break
done

if [ "$push_ok" -ne 1 ]; then
  say "land.sh: push failed after retries (log: $LOG)"
  exit 1
fi

SHA=$(git rev-parse HEAD)
say "land.sh: pushed $SHA"

for n in "${CLOSE_ISSUES[@]+"${CLOSE_ISSUES[@]}"}"; do
  if gh issue close "$n" --comment "Landed in $SHA" >> "$LOG" 2>&1; then
    log "closed issue #$n"
  else
    say "land.sh: could not close issue #$n (see log)"
  fi
done

for i in "${LANDED_IDX[@]}"; do
  b=${BRANCHES[$i]}
  wt_path=$(git worktree list --porcelain | awk -v b="$b" '
    /^worktree /{p=substr($0,10)}
    /^branch /{br=substr($0,8); sub("refs/heads/","",br); if (br==b) print p}')
  [ -n "$wt_path" ] && { git worktree unlock "$wt_path" >> "$LOG" 2>&1 || true; }
  support/scripts/worktree-clean.sh "$b" >> "$LOG" 2>&1 || true
done

# --- best-effort install on the owner's iPhone --------------------------------------------------
# After a push that touches the iOS app or what it links (same paths as the iOS verify), build and
# install the pushed tree with ios/scripts/install-device.sh, so the phone always runs the latest
# landed build. Skipped when the phone isn't reachable (USB or Wi-Fi); a failure is reported and
# never fails the landing. Takes the same machine-lock as verify, since it's an Xcode build.
IOS_DEVICE_ID=00008140-000539602E90401C
if [ "$DEVICE_INSTALL" = 1 ] \
   && git diff --name-only "$ORIGIN_MAIN_SHA" HEAD | grep -Eq '^(ios/|shared/|android/domain/|android/presentation/|android/core/)'; then
  if xcrun devicectl list devices 2>/dev/null | grep -F "$IOS_DEVICE_ID" | grep -Eq 'available|connected'; then
    say "land.sh: installing $SHA on the iPhone"
    if LAUNCH=0 machine-lock --name verify -- ios/scripts/install-device.sh "$IOS_DEVICE_ID" >> "$LOG" 2>&1; then
      say "land.sh: iPhone install done"
    else
      say "land.sh: iPhone install failed (see log; retry with ios/scripts/install-device.sh)"
    fi
  else
    say "land.sh: iPhone not reachable, skipping device install"
  fi
fi

say "land.sh: done (log: $LOG)"
