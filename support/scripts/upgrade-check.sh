#!/usr/bin/env bash
# Upgrade check: set up the shipped 1.0.10 build on a remote-emu lane (library, playlist, favourite, a changed setting),
# `adb install -r` the new build over it, and assert with Maestro that all four survive and nothing crashes.
#
#   support/scripts/upgrade-check.sh [--old <git-ref>] [--new <git-ref>] [--keep]
#
#     --old   default 2c586573c (the 1.0.10 bump); built as a debug APK, since Play's signed 1.0.10 can't be upgraded
#             over by a local build
#     --new   default origin/main
#     --keep  leave the lane up
#
# Coverage: the old side uses the Android Media Store provider over the playlist-basic fixture. Emby (and the other
# remote providers) are NOT covered: a locally built 1.0.10 signs in to Emby but every import fails "Failed to
# authenticate", while the Play build imports fine.
# Writes build/upgrade-check/<old>-vs-<new>/ with screenshots and summary.txt; exits non-zero if a check fails.
# Run it in one foreground call, ~10 min.
set -uo pipefail

cd "$(dirname "$0")/../.." || exit 2
# shellcheck source=support/scripts/_adb-path.sh
source support/scripts/_adb-path.sh

OLD_SPEC=2c586573c
NEW_SPEC=origin/main
KEEP=0
while [ $# -gt 0 ]; do
    case "$1" in
        --old) OLD_SPEC="${2:?upgrade-check: --old needs a ref}"; shift 2 ;;
        --new) NEW_SPEC="${2:?upgrade-check: --new needs a ref}"; shift 2 ;;
        --keep) KEEP=1; shift ;;
        -h | --help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
        *) echo "upgrade-check: unknown argument $1" >&2; exit 2 ;;
    esac
done

PKG=com.simplecityapps.shuttle.dev
FLOWS=support/maestro/upgrade-check
REMOTE=support/scripts/remote-emu.sh
LANE_STARTED=0
FAILED=0

cleanup() { [ "$LANE_STARTED" = 1 ] && [ "$KEEP" = 0 ] && "$REMOTE" stop >/dev/null 2>&1; }
trap cleanup EXIT

RESOLVED="$(support/scripts/release-compare.sh --old "$OLD_SPEC" --new "$NEW_SPEC" --resolve-only 2>&1)" || { echo "$RESOLVED" >&2; exit 1; }
OLD_APK="$(sed -n 's/^old=//p' <<<"$RESOLVED")"
NEW_APK="$(sed -n 's/^new=//p' <<<"$RESOLVED")"
[ -f "$OLD_APK" ] && [ -f "$NEW_APK" ] || { echo "upgrade-check: could not resolve both APKs" >&2; exit 1; }

OUT="build/upgrade-check/$(basename "$OLD_APK" -debug.apk)-vs-$(basename "$NEW_APK" -debug.apk)"
rm -rf "$OUT" && mkdir -p "$OUT"
SUMMARY="$OUT/summary.txt"
: >"$SUMMARY"

record() { # name PASS|FAIL [note]
    printf '%-6s %s%s\n' "$2" "$1" "${3:+ ($3)}" | tee -a "$SUMMARY"
    [ "$2" = PASS ] || FAILED=1
}

"$REMOTE" start >/dev/null || { echo "upgrade-check: lane did not start" >&2; exit 1; }
LANE_STARTED=1
eval "$("$REMOTE" env)"
REPO_ROOT="$PWD"
MAESTRO_DEVICE_ARGS=(--device "$("$REMOTE" serial)")
# shellcheck source=support/scripts/emu-lane.sh
source support/scripts/emu-lane.sh

installed_version() { adb shell dumpsys package "$PKG" | sed -n 's/^ *versionName=//p' | head -1 | tr -d '\r'; }

flow() { # <flow> <name>: screenshots land flat in $OUT; a failed run keeps its Maestro output in $OUT/failed-<name>
    local rc
    maestro_run "$FLOWS/$1" "$PWD/$OUT/.run-$2" >"$OUT/$2.log" 2>&1
    rc=$?
    maestro_collect_shots "$PWD/$OUT/.run-$2" "$PWD/$OUT" "$rc" "$PWD/$OUT/failed-$2"
    return "$rc"
}

"$REMOTE" reset >/dev/null
adb uninstall "$PKG" >/dev/null 2>&1 # a lane that last ran a newer build refuses the downgrade
"$REMOTE" install "$OLD_APK" >/dev/null || { echo "upgrade-check: old install failed" >&2; exit 1; }
support/scripts/seed-test-media.sh playlist-basic >"$OUT/seed.log" 2>&1 || { echo "upgrade-check: seeding failed, see $OUT/seed.log" >&2; exit 1; }
OLD_VER="$(installed_version)"

if flow old-setup.yaml old-setup; then record "old setup ($OLD_VER)" PASS; else record "old setup ($OLD_VER)" FAIL "see $OUT/old-setup.log"; exit 1; fi

adb logcat -b crash -c
"$REMOTE" install "$NEW_APK" >/dev/null || { echo "upgrade-check: upgrade install failed" >&2; exit 1; }
NEW_VER="$(installed_version)"
echo "versions: ${OLD_VER} -> ${NEW_VER}" | tee -a "$SUMMARY"
if [ "$OLD_VER" != "$NEW_VER" ] && [ -n "$NEW_VER" ]; then record "upgraded in place" PASS "$OLD_VER -> $NEW_VER"; else record "upgraded in place" FAIL "$OLD_VER -> $NEW_VER"; fi

for pair in "library:new-library.yaml" "playlist:new-playlist.yaml" "favourite:new-favourite.yaml" "setting:new-setting.yaml"; do
    name="${pair%%:*}"
    if flow "${pair#*:}" "$name"; then record "$name survives" PASS; else record "$name survives" FAIL "see $OUT/$name.log"; fi
done

CRASHES="$(adb logcat -b crash -d | grep -c "Process: ${PKG}" || true)"
if [ "${CRASHES:-0}" = 0 ]; then record "no crash on launch" PASS; else record "no crash on launch" FAIL "${CRASHES} in logcat -b crash"; fi

echo "upgrade-check: summary in $SUMMARY, screenshots in $OUT"
exit "$FAILED"
