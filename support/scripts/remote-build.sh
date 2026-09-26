#!/usr/bin/env bash
# Run this worktree's Gradle build on whichever host has room (#451, #546): the Mac when it isn't
# loaded, else the WSL box (16 cores) when a box build slot is free, else the Mac anyway -- never
# queue for a box slot while the Mac could build.
#
#   support/scripts/remote-build.sh [--box|--local] <gradle args...>
#   support/scripts/remote-build.sh --max-workers=8 :android:app:assembleDebug
#
#   --box     force the box, waiting for a slot if all are busy (exit 3 if it's unreachable)
#   --local   force the Mac
#
# Picking the host (no flag): the Mac's 1-min load average against its core count (`sysctl -n
# hw.ncpu`, `nproc` on Linux). Under REMOTE_BUILD_LOAD_RATIO x cores (default 0.8) the build runs
# here; otherwise it goes to the box without waiting for a slot, and falls back to here if the box
# is unreachable or has no free slot. One line says where it ran and why.
#
# On the Mac: ./gradlew with the same args, --max-workers capped at the idle cores (cores minus the
# 1-min load, at least 2); prints the Gradle wall time at the end.
#
# On the box:
# 1. rsyncs the worktree to ~/s2-builds/<worktree name> on the box (no build/, .gradle/, .idea/,
#    .git, .claude/ or local.properties; untracked files the build reads come along);
# 2. takes a box-side build slot (#462: three concurrent remote builds once starved sshd) --
#    at most REMOTE_BUILD_SLOTS (default 2) run at a time, admission via `flock` on
#    ~/s2-builds/.slots/N so a dead holder's slot is never wedged. Picked automatically, the
#    box is only used when a probe before the sync sees a free slot, and if the slots filled up
#    during the sync the box side exits 75 and the build runs here instead. With --box a caller
#    that has to wait prints one line, then rescans all slots every few seconds and takes whichever
#    frees first (not just the last one it tried);
# 3. writes the box's own local.properties and runs ./gradlew there, under `nice`, with the
#    box-side JDK and Gradle user home (~/s2-builds/.gradle-home, shared by every worktree so the
#    build cache and daemons stay warm; its gradle.properties caps the daemon heap and worker count
#    for remote runs without touching this worktree's own gradle.properties) and
#    --max-workers=6 unless the args name their own (REMOTE_BUILD_MAX_WORKERS changes the default;
#    the default drops to 4 when remote-emu.sh shows a lane leased on the box);
# 4. streams a condensed log -- failed tasks and tests, compiler errors, the "What went wrong"
#    block, the BUILD line -- while the whole log goes to build/remote-build/gradle.log; prints the
#    slot wait time and the Gradle wall time separately once the build finishes, so a slow run can
#    be told apart from a starved one;
# 5. syncs back APKs (build/outputs/apk), test results, reports and Roborazzi outputs into the
#    same paths here, plus the full log, then drops any of those report dirs the box no longer has
#    so one a previous run wrote and this one didn't re-run can't linger (#459).
#
# On a failing test task (either host), prints "Failed tests:" followed by each failing
# Class.method and the first line of its failure message (cap 20, then "+N more"), read from
# TEST-*.xml under build/test-results/ written since the build started -- so stale XML from an
# earlier run is never reported (#468). A box run's own "See the report at: file:///home/..."
# line is rewritten to the local synced copy.
#
# The version comes from the latest vYYMMDDNN tag, read here and passed as -PversionCode and
# -PversionName, since a worktree's .git is a pointer file that means nothing on the box.
#
# Exits with Gradle's exit code wherever it ran. Concurrent calls from different worktrees build in
# separate directories (and separate daemons, as a busy daemon is never shared); two calls from the
# same worktree queue on a local lock; the box-side slot above additionally caps how many builds
# (from any worktree) run there at once. One-time box setup: support/scripts/remote-build-setup.sh.
# Exit 3: --box and the box isn't reachable.
set -euo pipefail

BOX="${REMOTE_BUILD_BOX:-tim@192.168.50.131}"
SLOTS="${REMOTE_BUILD_SLOTS:-2}"
NICE="${REMOTE_BUILD_NICE:-10}"
LOAD_RATIO="${REMOTE_BUILD_LOAD_RATIO:-0.8}"
NO_SLOT=75 # the box side's exit code when no slot is free and it was told not to wait
SSH=(ssh -o ConnectTimeout=5 -o BatchMode=yes -o ServerAliveInterval=30)

host=auto
case "${1:-}" in
    --box) host=box; shift ;;
    --local) host=local; shift ;;
esac
[ "$#" -gt 0 ] || { echo "usage: remote-build.sh [--box|--local] <gradle args...>" >&2; exit 2; }

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
NAME="${REMOTE_BUILD_NAME:-$(basename "$ROOT")}"
REMOTE_DIR="s2-builds/$NAME" # relative to the box's home
LOG_DIR="$ROOT/build/remote-build"

# ---- one build per worktree at a time -------------------------------------------------------
LOCK="${TMPDIR:-/tmp}/remote-build/$NAME.lock"
mkdir -p "$(dirname "$LOCK")"
until mkdir "$LOCK" 2>/dev/null; do
    holder="$(cat "$LOCK/pid" 2>/dev/null || true)"
    if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
        rm -rf "$LOCK"
        continue
    fi
    echo "remote-build: waiting for this worktree's other build (pid ${holder:-?})" >&2
    sleep 10
done
echo $$ > "$LOCK/pid"
FAIL_MARKER="$(mktemp -t remote-build-marker)"
trap 'rm -rf "$LOCK" "$FAIL_MARKER"' EXIT

# ---- local load -----------------------------------------------------------------------------
CORES="$(sysctl -n hw.ncpu 2>/dev/null || nproc)"
if [ -r /proc/loadavg ]; then
    LOAD="$(cut -d' ' -f1 /proc/loadavg)"
else
    LOAD="$(sysctl -n vm.loadavg | awk '{ print $2 }')" # "{ 1-min 5-min 15-min }"
fi
THRESHOLD="$(awk -v c="$CORES" -v r="$LOAD_RATIO" 'BEGIN { printf "%.1f", c * r }')"
MAC_LOAD="Mac load $LOAD on $CORES cores"

# ---- build here -----------------------------------------------------------------------------
# $1: why. Runs the caller's args with --max-workers capped at the idle cores, then exits with
# Gradle's exit code.
run_local() {
    local idle workers="" a prev="" rc gradle_start
    local -a local_args=()
    idle="$(awk -v c="$CORES" -v l="$LOAD" 'BEGIN { f = int(c - l); print (f < 2 ? 2 : f) }')"
    for a in "${gradle_args[@]}"; do
        if [ "$prev" = --max-workers ]; then
            workers="$a"
        else
            case "$a" in
                --max-workers) ;;
                --max-workers=*) workers="${a#--max-workers=}" ;;
                *) local_args+=("$a") ;;
            esac
        fi
        prev="$a"
    done
    if [ -z "$workers" ] || [ "$workers" -gt "$idle" ]; then workers="$idle"; fi
    local_args+=("--max-workers=$workers")
    echo "remote-build: building on the Mac, $1; --max-workers=$workers" >&2
    gradle_start=$SECONDS
    set +e
    ./gradlew "${local_args[@]}"
    rc=$?
    set -e
    echo "remote-build: gradle wall time $((SECONDS - gradle_start))s on the Mac" >&2
    if [ "$rc" -ne 0 ]; then "$ROOT/support/scripts/report-test-failures.sh" "$FAIL_MARKER" "$ROOT" || true; fi
    exit "$rc"
}

# ---- box probe ------------------------------------------------------------------------------
# One ssh round trip for the reachability check, remote-emu.sh's lane count (a lane on the box
# competes for the same CPU/memory budget, so the default worker count drops while one is up) and
# how many build slots are free right now. Sets LANES_UP and FREE_SLOTS; fails if unreachable.
probe_box() {
    local out
    # shellcheck disable=SC2016 # $HOME expands on the box, not here
    out="$("${SSH[@]}" "$BOX" "bash -s -- $SLOTS" 2>/dev/null <<'PROBE'
lanes="$(ls -d "$HOME"/.emu-leases/lane-* 2>/dev/null | wc -l)"
slot_dir="$HOME/s2-builds/.slots"
mkdir -p "$slot_dir"
free=0
for s in $(seq 1 "$1"); do
    flock -n "$slot_dir/$s" true && free=$((free + 1))
done
echo "$lanes $free"
PROBE
)" || return 1
    read -r LANES_UP FREE_SLOTS <<<"$out"
    LANES_UP="${LANES_UP//[^0-9]/}"
    FREE_SLOTS="${FREE_SLOTS//[^0-9]/}"
    [ -n "$LANES_UP" ] || LANES_UP=0
    [ -n "$FREE_SLOTS" ] || FREE_SLOTS=0
}

gradle_args=("$@")
case "$host" in
    local) run_local "as asked (--local)" ;;
    box)
        probe_box || { echo "remote-build: $BOX is not reachable within 5 s" >&2; exit 3; }
        wait_for_slot=yes
        echo "remote-build: building on the box, as asked (--box)" >&2
        ;;
    auto)
        if awk -v l="$LOAD" -v t="$THRESHOLD" 'BEGIN { exit !(l < t) }'; then
            run_local "$MAC_LOAD is under $THRESHOLD"
        fi
        probe_box || run_local "$MAC_LOAD is over $THRESHOLD but $BOX is not reachable"
        [ "$FREE_SLOTS" -gt 0 ] || run_local "$MAC_LOAD is over $THRESHOLD but every box build slot is busy"
        wait_for_slot=no
        echo "remote-build: building on the box, $MAC_LOAD is over $THRESHOLD and a box slot is free" >&2
        ;;
esac

# ---- box gradle args --------------------------------------------------------------------------
MAX_WORKERS="${REMOTE_BUILD_MAX_WORKERS:-}"
if [ -z "$MAX_WORKERS" ]; then
    if [ "$LANES_UP" -gt 0 ]; then
        MAX_WORKERS=4
        echo "remote-build: $LANES_UP emulator lane(s) leased on $BOX; defaulting --max-workers=$MAX_WORKERS" >&2
    else
        MAX_WORKERS=6
    fi
fi
args=("$@")
case " $* " in *" --max-workers"*) ;; *) args+=("--max-workers=$MAX_WORKERS") ;; esac
case " $* " in
    *" -PversionCode="*) ;;
    *)
        tag="$(git -C "$ROOT" describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)"
        tag="${tag#v}"
        if [[ "$tag" =~ ^[0-9]{6,}$ ]]; then
            args+=("-PversionCode=$((10#$tag))" "-PversionName=20${tag:0:2}.${tag:2:2}.${tag:4:2}")
        fi
        ;;
esac

# ---- sync up --------------------------------------------------------------------------------
# $HOME on the box (not assumed) is needed below to rewrite the box's own
# "See the report at: file:///home/.../s2-builds/..." lines to the local
# synced copy (#468).
BOX_HOME="$("${SSH[@]}" "$BOX" "mkdir -p $REMOTE_DIR && printf '%s' \"\$HOME\"")"
start=$SECONDS
rsync -a --delete -e "${SSH[*]}" \
    --exclude='build/' --exclude='.gradle/' --exclude='.idea/' --exclude='.kotlin/' \
    --exclude='/local.properties' --exclude='/.git' --exclude='/.claude/' \
    --exclude='.DS_Store' --exclude='*.iml' --exclude='captures/' \
    "$ROOT/" "$BOX:$REMOTE_DIR/"
echo "remote-build: synced $NAME to $BOX:~/$REMOTE_DIR in $((SECONDS - start))s; gradle ${args[*]}" >&2

# ---- build ----------------------------------------------------------------------------------
# ssh joins every trailing argument with a space into one string for the box's login shell to
# re-parse, so an unquoted glob or space in a gradle arg (e.g. --tests '*Queue*') would be mangled;
# %q-quote each one here and pass the whole thing as a single ssh argument instead.
remote_cmd="bash -s --"
for a in "$SLOTS" "$NICE" "$wait_for_slot" "$NO_SLOT" "$REMOTE_DIR" "${args[@]}"; do
    remote_cmd+=" $(printf '%q' "$a")"
done
set +e
"${SSH[@]}" "$BOX" "$remote_cmd" <<'REMOTE' | sed "s#file://$BOX_HOME/$REMOTE_DIR#file://$ROOT#g"
set -uo pipefail
slots="$1"; nice_level="$2"; wait_for_slot="$3"; no_slot="$4"
cd "$HOME/$5" || exit 1
shift 5

# ---- box-side admission control (#462): at most $slots builds run at once, so three concurrent
# remote builds can no longer starve sshd the way they did on 2026-09-26. flock on an fd held by
# this shell for the rest of the script: the lock releases itself the moment this process ends,
# on a normal exit or an ssh disconnect, so a dead holder never wedges a later build.
slot_dir="$HOME/s2-builds/.slots"
mkdir -p "$slot_dir"
slot=""
try_slots() {
    for s in $(seq 1 "$slots"); do
        exec {slot_fd}>"$slot_dir/$s"
        if flock -n "$slot_fd"; then
            slot="$s"
            return 0
        fi
        eval "exec ${slot_fd}>&-"
    done
    return 1
}
slot_wait_start=$SECONDS
if ! try_slots; then
    [ "$wait_for_slot" = yes ] || exit "$no_slot"
    echo "remote-build: waiting for a box build slot (all $slots busy)" >&2
    until try_slots; do
        sleep 3
    done
fi
slot_wait=$((SECONDS - slot_wait_start))
echo "remote-build: using box build slot $slot (waited ${slot_wait}s)" >&2

export JAVA_HOME="$HOME/opt/jdk-21"
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export GRADLE_USER_HOME="$HOME/s2-builds/.gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
[ -x "$JAVA_HOME/bin/java" ] || { echo "remote-build: no JDK on the box; run support/scripts/remote-build-setup.sh" >&2; exit 1; }
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
mkdir -p build/remote-build
gradle_start=$SECONDS
nice -n "$nice_level" ./gradlew --console=plain "$@" </dev/null 2>&1 | tee build/remote-build/gradle.log | awk '
    /^\* What went wrong:/ { block = 1 }
    /^\* Try:/ { block = 0 }
    block || /^e: / || /: error:/ || / FAILED$/ || /^FAILURE:/ || /^BUILD (SUCCESSFUL|FAILED)/ \
        || /tests completed/ || / actionable tasks:/ { print; fflush() }
'
gradle_rc="${PIPESTATUS[0]}"
gradle_wall=$((SECONDS - gradle_start))
echo "remote-build: slot wait ${slot_wait}s; gradle wall time ${gradle_wall}s" >&2
exit "$gradle_rc"
REMOTE
rc="${PIPESTATUS[0]}"
set -e
if [ "$wait_for_slot" = no ] && [ "$rc" -eq "$NO_SLOT" ]; then
    run_local "every box build slot filled up during the sync"
fi

# ---- sync back --------------------------------------------------------------------------------
start=$SECONDS
if rsync -a --prune-empty-dirs -e "${SSH[*]}" \
    --exclude='.gradle/' --exclude='src/' --exclude='.git/' \
    --exclude='build/intermediates/' --exclude='build/tmp/' --exclude='build/generated/' \
    --exclude='build/kotlin/' --exclude='build/.transforms/' --exclude='build/snapshot/' \
    --include='/build/remote-build/***' \
    --include='*/' \
    --include='build/outputs/apk/***' --include='build/outputs/roborazzi/***' \
    --include='build/reports/***' --include='build/test-results/***' \
    --exclude='*' \
    "$BOX:$REMOTE_DIR/" "$ROOT/"
then
    # ---- clear stale reports (#459, #462) ------------------------------------------------------
    # The sync above only adds/updates, so a report dir this run didn't regenerate (a test class
    # that got removed, a module that wasn't built) would otherwise linger from a previous run.
    # A plain `rsync --delete` on this same command isn't safe: the include/exclude filter above
    # allows recursion into every directory in the tree (needed to reach nested report dirs), so
    # --delete would consider every directory under $ROOT for removal -- confirmed by a dry run
    # that tried to delete android/app, .claude/skills/* and other unrelated dirs (rsync only
    # refused because they're non-empty; a genuinely empty unrelated dir would have gone). Instead,
    # ask the box which report dirs it actually has now, using the same restrictive path match as
    # before, and drop only the local ones it doesn't -- one confirmed-stale directory at a time.
    if [ -n "$ROOT" ] && [ -n "$REMOTE_DIR" ]; then
        if remote_dirs="$("${SSH[@]}" "$BOX" "cd \"\$HOME/$REMOTE_DIR\" && find . -type d \( -path '*/build/test-results' -o -path '*/build/reports' -o -path '*/build/outputs/roborazzi' \) 2>/dev/null")"; then
            while IFS= read -r d; do
                [ -n "$d" ] || continue
                rel="${d#"$ROOT"/}"
                # A here-string, not a pipe: under pipefail, grep -q exiting on a match SIGPIPEs the
                # writer and fails the pipeline, which would delete a dir the box still has.
                grep -qxF "./$rel" <<<"$remote_dirs" || rm -rf "$d"
            done < <(find "$ROOT" -type d \( -path '*/build/test-results' -o -path '*/build/reports' -o -path '*/build/outputs/roborazzi' \) 2>/dev/null)
        else
            echo "remote-build: listing remote report dirs failed; leaving existing local reports as-is" >&2
        fi
    else
        echo "remote-build: ROOT or REMOTE_DIR is empty, skipping stale-report cleanup" >&2
    fi
else
    echo "remote-build: syncing the outputs back failed (the build's exit code is kept); leaving existing local reports as-is" >&2
fi
echo "remote-build: outputs synced back in $((SECONDS - start))s; full log: ${LOG_DIR#"$ROOT"/}/gradle.log" >&2
if [ "$rc" -ne 0 ]; then "$ROOT/support/scripts/report-test-failures.sh" "$FAIL_MARKER" "$ROOT" || true; fi
exit "$rc"
