#!/usr/bin/env bash
# Run this worktree's Gradle build on whichever host has room (#451, #546). Thin wrapper over
# devtools' `remote-gradle`, which owns host choice, sync, box slots and the condensed log (full
# log: build/remote-gradle/gradle.log); see its header.
#
#   support/scripts/remote-build.sh [--box|--local] <gradle args...>
#
# Kept here: the version from the latest vYYMMDDNN tag, passed as -PversionCode/-PversionName
# (a worktree's .git is a pointer file that means nothing on the box), and, on a non-zero exit,
# the failing tests from TEST-*.xml written since the build started (#468).
#
# Box dirs are named after the worktree (~/s2-builds/<worktree>). One-time box setup:
# support/scripts/remote-build-setup.sh.
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT" || exit 1

# Box runs only: two test forks per module is ~35% faster on the 16-core box.
export REMOTE_GRADLE_BOX_ARGS="${REMOTE_GRADLE_BOX_ARGS:--Ps2.testForks=2}"

# Goldens verify on Linux within the changeThreshold; record stays on the Mac.
export REMOTE_GRADLE_MAC_ONLY="${REMOTE_GRADLE_MAC_ONLY:-record* *Record* connected* *Ios* *Xcode* linkDebugFramework* install*}"

flags=()
case "${1:-}" in
    --box|--local) flags+=("$1"); shift ;;
esac
[ "$#" -gt 0 ] || { echo "usage: remote-build.sh [--box|--local] <gradle args...>" >&2; exit 2; }

args=("$@")
case " $* " in
    *" -PversionCode="*) ;;
    *)
        tag="$(git describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)"
        tag="${tag#v}"
        if [[ "$tag" =~ ^[0-9]{6,}$ ]]; then
            args+=("-PversionCode=$((10#$tag))" "-PversionName=20${tag:0:2}.${tag:2:2}.${tag:4:2}")
        fi
        ;;
esac

FAIL_MARKER="$(mktemp -t remote-build-marker)"
trap 'rm -f "$FAIL_MARKER"' EXIT

remote-gradle ${flags[@]+"${flags[@]}"} --name "$(basename "$ROOT")" -- "${args[@]}"
rc=$?
if [ "$rc" -ne 0 ]; then "$ROOT/support/scripts/report-test-failures.sh" "$FAIL_MARKER" "$ROOT" || true; fi
exit "$rc"
