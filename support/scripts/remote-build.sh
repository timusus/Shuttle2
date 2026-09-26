#!/usr/bin/env bash
# Run this worktree's Gradle build on the WSL box instead of the Mac (#451): the Mac is CPU-bound
# when several sessions build at once, the box has 16 idle cores.
#
#   support/scripts/remote-build.sh <gradle args...>
#   support/scripts/remote-build.sh --max-workers=8 :android:app:assembleDebug
#
# 1. rsyncs the worktree to ~/s2-builds/<worktree name> on the box (no build/, .gradle/, .idea/,
#    .git, .claude/ or local.properties; untracked files the build reads come along);
# 2. takes a box-side build slot (#462: three concurrent remote builds once starved sshd) --
#    at most REMOTE_BUILD_SLOTS (default 2) run at a time, admission via `flock` on
#    ~/s2-builds/.slots/N so a dead holder's slot is never wedged; a caller that has to wait prints
#    one line, then rescans all slots every few seconds and takes whichever frees first (not just
#    the last one it tried);
# 3. writes the box's own local.properties and runs ./gradlew there, under `nice`, with the
#    box-side JDK and Gradle user home (~/s2-builds/.gradle-home, shared by every worktree so the
#    build cache and daemons stay warm; its gradle.properties caps the daemon heap and worker count
#    for remote runs without touching this worktree's own gradle.properties) and
#    --max-workers=6 unless the args name their own (REMOTE_BUILD_MAX_WORKERS changes the default;
#    the default drops to 4 when remote-emu.sh shows a lane leased on the box);
# 4. streams a condensed log -- failed tasks and tests, compiler errors, the "What went wrong"
#    block, the BUILD line -- while the whole log goes to build/remote-build/gradle.log;
# 5. syncs back APKs (build/outputs/apk), test results, reports and Roborazzi outputs into the
#    same paths here, plus the full log, then drops any of those report dirs the box no longer has
#    so one a previous run wrote and this one didn't re-run can't linger (#459) -- and exits with
#    Gradle's exit code.
#
# The version comes from the latest vYYMMDDNN tag, read here and passed as -PversionCode and
# -PversionName, since a worktree's .git is a pointer file that means nothing on the box.
#
# Concurrent calls from different worktrees build in separate directories (and separate daemons,
# as a busy daemon is never shared); two calls from the same worktree queue on a local lock; the
# box-side slot above additionally caps how many builds (from any worktree) run at once.
# One-time box setup: support/scripts/remote-build-setup.sh. Exit 3: the box isn't reachable.
set -euo pipefail

BOX="${REMOTE_BUILD_BOX:-tim@192.168.50.131}"
SLOTS="${REMOTE_BUILD_SLOTS:-2}"
NICE="${REMOTE_BUILD_NICE:-10}"
SSH=(ssh -o ConnectTimeout=5 -o BatchMode=yes -o ServerAliveInterval=30)

[ "$#" -gt 0 ] || { echo "usage: remote-build.sh <gradle args...>" >&2; exit 2; }

ROOT="$(git rev-parse --show-toplevel)"
NAME="${REMOTE_BUILD_NAME:-$(basename "$ROOT")}"
REMOTE_DIR="s2-builds/$NAME" # relative to the box's home
LOG_DIR="$ROOT/build/remote-build"

# One ssh round trip for both the reachability check and remote-emu.sh's lane count (a lane on the
# box competes for the same CPU/memory budget, so the default worker count drops while one is up).
# shellcheck disable=SC2016 # $HOME expands on the box, not here
LANES_UP="$("${SSH[@]}" "$BOX" 'ls -d "$HOME"/.emu-leases/lane-* 2>/dev/null | wc -l' 2>/dev/null)" \
    || { echo "remote-build: $BOX is not reachable within 5 s" >&2; exit 3; }
LANES_UP="${LANES_UP//[^0-9]/}"
[ -n "$LANES_UP" ] || LANES_UP=0

MAX_WORKERS="${REMOTE_BUILD_MAX_WORKERS:-}"
if [ -z "$MAX_WORKERS" ]; then
    if [ "$LANES_UP" -gt 0 ]; then
        MAX_WORKERS=4
        echo "remote-build: $LANES_UP emulator lane(s) leased on $BOX; defaulting --max-workers=$MAX_WORKERS" >&2
    else
        MAX_WORKERS=6
    fi
fi

# ---- one build per worktree at a time -------------------------------------------------------
LOCK="${TMPDIR:-/tmp}/remote-build/$NAME.lock"
mkdir -p "$(dirname "$LOCK")"
until mkdir "$LOCK" 2>/dev/null; do
    holder="$(cat "$LOCK/pid" 2>/dev/null || true)"
    if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
        rm -rf "$LOCK"
        continue
    fi
    echo "remote-build: waiting for this worktree's other remote build (pid ${holder:-?})" >&2
    sleep 10
done
echo $$ > "$LOCK/pid"
trap 'rm -rf "$LOCK"' EXIT

# ---- gradle args ----------------------------------------------------------------------------
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
"${SSH[@]}" "$BOX" "mkdir -p $REMOTE_DIR"
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
for a in "$SLOTS" "$NICE" "$REMOTE_DIR" "${args[@]}"; do
    remote_cmd+=" $(printf '%q' "$a")"
done
set +e
"${SSH[@]}" "$BOX" "$remote_cmd" <<'REMOTE'
set -uo pipefail
slots="$1"; nice_level="$2"
cd "$HOME/$3" || exit 1
shift 3

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
if ! try_slots; then
    echo "remote-build: waiting for a box build slot (all $slots busy)" >&2
    until try_slots; do
        sleep 3
    done
fi
echo "remote-build: using box build slot $slot" >&2

export JAVA_HOME="$HOME/opt/jdk-21"
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export GRADLE_USER_HOME="$HOME/s2-builds/.gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
[ -x "$JAVA_HOME/bin/java" ] || { echo "remote-build: no JDK on the box; run support/scripts/remote-build-setup.sh" >&2; exit 1; }
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
mkdir -p build/remote-build
nice -n "$nice_level" ./gradlew --console=plain "$@" </dev/null 2>&1 | tee build/remote-build/gradle.log | awk '
    /^\* What went wrong:/ { block = 1 }
    /^\* Try:/ { block = 0 }
    block || /^e: / || /: error:/ || / FAILED$/ || /^FAILURE:/ || /^BUILD (SUCCESSFUL|FAILED)/ \
        || /tests completed/ || / actionable tasks:/ { print; fflush() }
'
exit "${PIPESTATUS[0]}"
REMOTE
rc=$?
set -e

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
exit "$rc"
