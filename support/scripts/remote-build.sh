#!/usr/bin/env bash
# Run this worktree's Gradle build on the WSL box instead of the Mac (#451): the Mac is CPU-bound
# when several sessions build at once, the box has 16 idle cores.
#
#   support/scripts/remote-build.sh <gradle args...>
#   support/scripts/remote-build.sh --max-workers=8 :android:app:assembleDebug
#
# 1. rsyncs the worktree to ~/s2-builds/<worktree name> on the box (no build/, .gradle/, .idea/,
#    .git, .claude/ or local.properties; untracked files the build reads come along);
# 2. writes the box's own local.properties and runs ./gradlew there with the box-side JDK and
#    Gradle user home (~/s2-builds/.gradle-home, shared by every worktree so the build cache and
#    daemons stay warm) and --max-workers=8 unless the args name their own (REMOTE_BUILD_MAX_WORKERS
#    changes the default), leaving cores for the emulator lanes;
# 3. streams a condensed log -- failed tasks and tests, compiler errors, the "What went wrong"
#    block, the BUILD line -- while the whole log goes to build/remote-build/gradle.log;
# 4. syncs back APKs (build/outputs/apk), test results and reports (build/test-results,
#    build/reports) and Roborazzi outputs (build/outputs/roborazzi) into the same paths here, plus
#    the full log, and exits with Gradle's exit code.
#
# The version comes from the latest vYYMMDDNN tag, read here and passed as -PversionCode and
# -PversionName, since a worktree's .git is a pointer file that means nothing on the box.
#
# Concurrent calls from different worktrees build in separate directories (and separate daemons,
# as a busy daemon is never shared); two calls from the same worktree queue on a local lock.
# One-time box setup: support/scripts/remote-build-setup.sh. Exit 3: the box isn't reachable.
set -euo pipefail

BOX="${REMOTE_BUILD_BOX:-tim@192.168.50.131}"
MAX_WORKERS="${REMOTE_BUILD_MAX_WORKERS:-8}"
SSH=(ssh -o ConnectTimeout=5 -o BatchMode=yes -o ServerAliveInterval=30)

[ "$#" -gt 0 ] || { echo "usage: remote-build.sh <gradle args...>" >&2; exit 2; }

ROOT="$(git rev-parse --show-toplevel)"
NAME="${REMOTE_BUILD_NAME:-$(basename "$ROOT")}"
REMOTE_DIR="s2-builds/$NAME" # relative to the box's home
LOG_DIR="$ROOT/build/remote-build"

"${SSH[@]}" "$BOX" true 2>/dev/null || { echo "remote-build: $BOX is not reachable within 5 s" >&2; exit 3; }

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
set +e
"${SSH[@]}" "$BOX" bash -s -- "$REMOTE_DIR" "${args[@]}" <<'REMOTE'
set -uo pipefail
cd "$HOME/$1" || exit 1
shift
export JAVA_HOME="$HOME/opt/jdk-21"
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export GRADLE_USER_HOME="$HOME/s2-builds/.gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
[ -x "$JAVA_HOME/bin/java" ] || { echo "remote-build: no JDK on the box; run support/scripts/remote-build-setup.sh" >&2; exit 1; }
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
mkdir -p build/remote-build
./gradlew --console=plain "$@" </dev/null 2>&1 | tee build/remote-build/gradle.log | awk '
    /^\* What went wrong:/ { block = 1 }
    /^\* Try:/ { block = 0 }
    block || /^e: / || /: error:/ || / FAILED$/ || /^FAILURE:/ || /^BUILD (SUCCESSFUL|FAILED)/ \
        || /tests completed/ || / actionable tasks:/ { print; fflush() }
'
exit "${PIPESTATUS[0]}"
REMOTE
rc=$?
set -e

# ---- sync back ------------------------------------------------------------------------------
start=$SECONDS
rsync -a --prune-empty-dirs -e "${SSH[*]}" \
    --exclude='.gradle/' --exclude='src/' --exclude='.git/' \
    --exclude='build/intermediates/' --exclude='build/tmp/' --exclude='build/generated/' \
    --exclude='build/kotlin/' --exclude='build/.transforms/' --exclude='build/snapshot/' \
    --include='/build/remote-build/***' \
    --include='*/' \
    --include='build/outputs/apk/***' --include='build/outputs/roborazzi/***' \
    --include='build/reports/***' --include='build/test-results/***' \
    --exclude='*' \
    "$BOX:$REMOTE_DIR/" "$ROOT/" \
    || echo "remote-build: syncing the outputs back failed (the build's exit code is kept)" >&2
echo "remote-build: outputs synced back in $((SECONDS - start))s; full log: ${LOG_DIR#"$ROOT"/}/gradle.log" >&2
exit "$rc"
