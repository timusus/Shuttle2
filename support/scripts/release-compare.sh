#!/usr/bin/env bash
# Matched old-vs-new Android screenshots of the real UI against the Emby test library, for a release
# comparison (and the Remotion video made from manifest.json).
#
#   support/scripts/release-compare.sh --old <apk|git-ref> --new <apk|git-ref>
#                                      [--version old|new|both] [--phase setup|screens|all] [--keep]
#                                      [--screens a,b,...] [--out <dir>] [--build-type debug|release]
#                                      [--flow-timeout <secs>] [--import-timeout <secs>] [--refresh-old] [--fresh]
#
#     --refresh-old      recapture the old build. A full old run is cached under $S2_RC_CACHE (default
#                        ~/.cache/s2-release-compare) by APK sha256 and flows hash and reused without the device.
#     --fresh            full setup (install, onboarding, sign-in, import) even when a matching AVD snapshot exists.
#                        Local AVD only: after setup settles the script saves a snapshot keyed by APK sha256 and the
#                        Emby host, and a rerun loads it instead (the onboarding shots come from the cache).
#
#     --resolve-only     build/resolve both APKs, print old=<apk> and new=<apk>, touch no device
#
#     --old / --new      an APK path, or a git ref that is built here (a bare branch name means origin/<name>, fetched
#                        first; the current tree when the ref is HEAD and
#                        the tree is clean, else a detached worktree under tmp/release-compare; the APK is cached
#                        per commit and build type)
#     --version          which build(s) to run (default both, old first)
#     --phase            setup: fresh install, demo mode, the onboarding captures, sign in to Emby, wait for the
#                        import. screens: artwork prewarm and the library captures on the app setup left installed.
#                        all (default): both. Run one phase per call; screens takes ~1 min of prewarm plus ~1 min
#                        a screen, so capture them in batches of about 3 with --screens. The lane's AVD is
#                        read-only, so screens needs the lane setup ran on: setup always leaves it up, screens and
#                        all stop it unless --keep.
#     --screens a,b      only capture these ids (the other setup steps still run, so the library exists). Ids:
#                          launch permission analytics source add-source emby-form sign-in import-progress
#                          home artist-list artist-detail album-list album-detail song-list playlist-list
#                          playlist-detail genre-list genre-detail folder-list search mini-player now-playing
#                          queue sleep-timer sound lyrics notification settings equaliser folder-detail
#                        folder-detail (new only) runs last, after the local music is un-parked and imported.
#                        A screen a version doesn't have is null in the manifest (pairing old_only/new_only).
#     --out <dir>        output directory (default build/release-compare/<old>-vs-<new>, from the APK version
#                        or the ref name). Runs merge into it: a capture replaces only its own screen's row.
#     --build-type       how a git ref is built (default debug). release is a minified release build signed with
#                        the debug keystore (CI=true CONFIGURATION=Release), since the Play keystore stays in
#                        release-android. The lane has no Play Store, so a release build never learns it is Pro
#                        and refuses to add a server: debug (always Pro) is the default.
#
# Typical run, one foreground call per line:
#   release-compare.sh --old <apk> --new main --version old --phase setup
#   release-compare.sh --old <apk> --new main --version old --phase screens --keep --screens home,album-list,album-detail
#   ... one screens call per batch of ~3 ids, then the same for --version new
#   adb -s emulator-5554 emu kill (local AVD) or remote-emu.sh stop (lane) when done
#
# Device: a box lane (remote-emu.sh); when the box can't run one (status exit 3) the local AVD $S2_LOCAL_AVD (default
# s2test), booted headless if none is running. Park the AVD's seeded /sdcard/Music/music (move it out of Music) before
# new's setup, or the new build imports it alongside Emby; put it back when done.
#
# Maestro drives the real UI by visible text (flows in support/maestro/release-compare/, shared, with old/ and new/
# overrides) and this script owns the capture under SystemUI demo mode. Setup screens: adb screencap every ~0.5 s until
# 2 consecutive frames are identical. Library screens run in one Maestro session per version: a generated flow runs
# each screen's flow, then takes K frames in a row; afterwards the script takes the first identical pair with no artwork
# placeholder, and the screens that failed rerun in a second session with more frames (still unsettled: a manifest
# warning naming the screen).
# Credentials come from ~/.config/s2-test/emby-appreview.env (URL, USER_NAME, PASSWORD), stay unexported and reach
# Maestro only as MAESTRO_URL/MAESTRO_USER_NAME/MAESTRO_PASSWORD env of that one invocation; Maestro output is scrubbed of them and kept in tmp/release-compare/<run>/, never next to the shots.
#
# Output: <out>/{old,new}/NN-<id>.png, <out>/manifest.json (steps, old/new paths, versions, warnings),
# <out>/montage/NN-<id>.png (old beside new, when ImageMagick is installed), run state in <out>/.state/.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CALLER_PWD="$PWD"
cd "$REPO_ROOT" || exit 1
PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

# shellcheck source=support/scripts/checks/_timeout_fallback.sh
source "${SCRIPT_DIR}/checks/_timeout_fallback.sh"
# shellcheck source=support/scripts/emu-lane.sh
source "${SCRIPT_DIR}/emu-lane.sh"

# ---- Targets: in the Emby test library with server artwork, shared by both versions so the pairs match ----
ARTIST="Radiohead"
ALBUM="Hail to the Thief"
# a track on ALBUM, matched as a Maestro regex (the real titles carry punctuation and subtitles)
ALBUM_TRACK=".*Sail to the Moon.*"
# the now-playing song comes from another album, so the mini player, now-playing and queue differ from the artist screens
PLAYING_ARTIST=".*Flume.*"
PLAYING_ALBUM="Skin"
PLAYING_TRACK=".*Never Be Like You.*"
QUERY="Radiohead"
# an artist near the top of the A-Z artist list, which is all the import-wait poll sees
IMPORT_MARKER="Alice In Chains"
GENRE="Hard Rock"
PLAYLIST="Recently Added"
# ---------------------------------------------------------------------------------------------

ENV_FILE="$HOME/.config/s2-test/emby-appreview.env"
FLOWS="support/maestro/release-compare"
STEPS=(launch permission analytics source add-source emby-form sign-in import-progress home artist-list artist-detail
    album-list album-detail song-list playlist-list playlist-detail genre-list genre-detail folder-list
    search mini-player now-playing queue sleep-timer sound lyrics notification settings equaliser folder-detail)
SETUP_IDS=(launch permission analytics source add-source emby-form sign-in)
SCREEN_IDS=(home artist-list artist-detail album-list album-detail song-list playlist-list playlist-detail genre-list
    genre-detail folder-list search mini-player now-playing queue sleep-timer sound lyrics notification
    settings equaliser)
# folder-detail needs the AVD's local music, so it is captured after every Emby screen (local_folder_phase); its file
# number is 31, the slot after equaliser, and the old 20 stays unused so every other screen keeps its number
LOCAL_ID=folder-detail
# screens that start playback: paused before capture, since a playing screen never goes still
PLAYBACK_IDS=" mini-player now-playing queue sleep-timer sound lyrics notification equaliser "
# never captured, with why (manifest not_captured)
SKIPPED=("widget	a home-screen widget needs a launcher setup the lane doesn't have"
    "paywall	the debug build is always Pro, and a release build can't add a server without Play billing")

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }

OLD_SPEC=""; NEW_SPEC=""; SCREENS_ARG=""; OUT=""; BUILD_TYPE=debug; VERSION=both; PHASE=all; KEEP=0
REFRESH_OLD=0; FRESH=0; RESOLVE_ONLY=0
FLOW_TIMEOUT=240; IMPORT_TIMEOUT=300; SESSION_TIMEOUT=900
while [ $# -gt 0 ]; do
    case "$1" in
        --old) OLD_SPEC="${2:?release-compare: --old needs an APK or git ref}"; shift 2 ;;
        --new) NEW_SPEC="${2:?release-compare: --new needs an APK or git ref}"; shift 2 ;;
        --version) VERSION="${2:?release-compare: --version needs old, new or both}"; shift 2 ;;
        --phase) PHASE="${2:?release-compare: --phase needs setup, screens or all}"; shift 2 ;;
        --keep) KEEP=1; shift ;;
        --refresh-old) REFRESH_OLD=1; shift ;;
        --fresh) FRESH=1; shift ;;
        --resolve-only) RESOLVE_ONLY=1; shift ;;
        --screens) SCREENS_ARG="${2:?release-compare: --screens needs a comma-separated list}"; shift 2 ;;
        --out) OUT="${2:?release-compare: --out needs a directory}"; shift 2 ;;
        --build-type) BUILD_TYPE="${2:?release-compare: --build-type needs debug or release}"; shift 2 ;;
        --flow-timeout) FLOW_TIMEOUT="${2:?release-compare: --flow-timeout needs seconds}"; shift 2 ;;
        --import-timeout) IMPORT_TIMEOUT="${2:?release-compare: --import-timeout needs seconds}"; shift 2 ;;
        -h | --help) usage; exit 0 ;;
        *) echo "release-compare: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
    esac
done
[ -n "$OLD_SPEC" ] && [ -n "$NEW_SPEC" ] || { echo "release-compare: --old and --new are required" >&2; usage >&2; exit 2; }
# relative paths mean the caller's cwd, not the repo root we cd to; an existing file is an APK, anything else a git ref
abs_spec() { if [ -f "${CALLER_PWD}/$1" ] || [[ "$1" == *.apk ]]; then printf '%s\n' "${CALLER_PWD}/$1"; else printf '%s\n' "$1"; fi; }
case "$OLD_SPEC" in /*) ;; *) OLD_SPEC="$(abs_spec "$OLD_SPEC")" ;; esac
case "$NEW_SPEC" in /*) ;; *) NEW_SPEC="$(abs_spec "$NEW_SPEC")" ;; esac
case "$OUT" in "" | /*) ;; *) OUT="${CALLER_PWD}/${OUT}" ;; esac
case "$BUILD_TYPE" in debug | release) ;; *) echo "release-compare: --build-type must be debug or release" >&2; exit 2 ;; esac
case "$VERSION" in old | new) VERSIONS=("$VERSION") ;; both) VERSIONS=(old new) ;; *) echo "release-compare: --version must be old, new or both" >&2; exit 2 ;; esac
case "$PHASE" in setup | screens | all) ;; *) echo "release-compare: --phase must be setup, screens or all" >&2; exit 2 ;; esac
command -v jq >/dev/null || { echo "release-compare: jq is required" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "release-compare: no $ENV_FILE (URL, USER_NAME, PASSWORD)" >&2; exit 1; }

SELECTED=("${STEPS[@]}")
if [ -n "$SCREENS_ARG" ]; then
    IFS=',' read -ra SELECTED <<<"$SCREENS_ARG"
    for s in "${SELECTED[@]}"; do
        case " ${STEPS[*]} " in *" $s "*) ;; *) echo "release-compare: unknown screen '$s' (want: ${STEPS[*]})" >&2; exit 2 ;; esac
    done
fi

RUN="$(date +%Y%m%d-%H%M%S)"
WORK="${REPO_ROOT}/tmp/release-compare/${RUN}"
mkdir -p "$WORK"
LOG="${WORK}/run.log"
: >"$LOG"
LANE_STARTED=0
DEVICE=""
LOCAL_AVD="${S2_LOCAL_AVD:-s2test}"
# outside the worktree, so every worktree and run shares the APKs, the old-build shots and the snapshot shots
RC_CACHE="${S2_RC_CACHE:-$HOME/.cache/s2-release-compare}"
EXTRA_WORKTREES=()

# label <spec>: the output-path name of a build: an APK's versionName (first word), else the ref.
label() {
    if [ -f "$1" ]; then local vn; vn="$(emu_apk_info "$1" versionName)"; printf '%s\n' "${vn%% *}"
    else printf '%s\n' "${1//\//-}"; fi
}
[ -n "$OUT" ] || OUT="${REPO_ROOT}/build/release-compare/$(label "$OLD_SPEC")-vs-$(label "$NEW_SPEC")"
STATE="${OUT}/.state"
mkdir -p "$STATE"
ROWS="${STATE}/rows.tsv"     # version<TAB>id<TAB>path
WARNS="${STATE}/warnings.tsv" # version<TAB>id<TAB>message
NOTES="${STATE}/not-captured.tsv" # version<TAB>id<TAB>why: expected gaps, not failures
touch "$ROWS" "$WARNS" "$NOTES"

step() {
    local desc="$1" start rc; shift
    start=$(date +%s)
    "$@" >>"$LOG" 2>&1
    rc=$?
    if [ "$rc" -eq 0 ]; then echo "release-compare: ${desc} -- ok ($(($(date +%s) - start))s)"; return 0; fi
    echo "release-compare: ${desc} -- FAILED ($(($(date +%s) - start))s, see $LOG)" >&2
    return "$rc"
}

warn() { # version id message
    printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$WARNS"
    echo "release-compare: WARNING $1/$2: $3" >&2
}

# forget <version> <id...>: drops earlier rows and warnings for these screens, so a re-run replaces them.
forget() {
    local ver="$1" f; shift
    for f in "$ROWS" "$WARNS" "$NOTES"; do
        awk -F'\t' -v v="$ver" -v ids=" $* " '!($1 == v && index(ids, " " $2 " "))' "$f" >"${f}.tmp" && mv -f "${f}.tmp" "$f"
    done
}

cleanup() {
    local wt
    if [ "$LANE_STARTED" = 1 ]; then
        emu_demo_mode off
        # the AVD's seeded music is parked while an app imports (see park_music); a snapshot load reverts the disk, so
        # put it back whenever it's found parked
        [ "$DEVICE" = local ] && [ -n "${ANDROID_SERIAL:-}" ] &&
            adb shell '[ -d /sdcard/rc-parked/music ] && mv /sdcard/rc-parked/music /sdcard/Music/' >>"$LOG" 2>&1
        if [ "$PHASE" = setup ] || [ "$KEEP" = 1 ]; then
            if [ "$DEVICE" = local ]; then echo "release-compare: local AVD left up for the next phase; adb -s ${ANDROID_SERIAL:-emulator-5554} emu kill when done"
            else echo "release-compare: lane left up for the next phase; support/scripts/remote-emu.sh stop when done"; fi
        elif [ "$DEVICE" = local ]; then
            [ -n "${ANDROID_SERIAL:-}" ] && adb emu kill >>"$LOG" 2>&1
        else
            support/scripts/remote-emu.sh stop >>"$LOG" 2>&1 || echo "release-compare: WARNING: remote-emu.sh stop failed, check the lane" >&2
        fi
    fi
    for wt in ${EXTRA_WORKTREES[@]+"${EXTRA_WORKTREES[@]}"}; do git worktree remove --force "$wt" >>"$LOG" 2>&1; done
    git worktree prune >>"$LOG" 2>&1
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# shellcheck source=lib/release-compare-apk.sh
. "${SCRIPT_DIR}/lib/release-compare-apk.sh"
# shellcheck source=lib/release-compare-maestro.sh
. "${SCRIPT_DIR}/lib/release-compare-maestro.sh"
# shellcheck source=lib/release-compare-capture.sh
. "${SCRIPT_DIR}/lib/release-compare-capture.sh"
# shellcheck source=lib/release-compare-snapshot.sh
. "${SCRIPT_DIR}/lib/release-compare-snapshot.sh"
# shellcheck source=lib/release-compare-screens.sh
. "${SCRIPT_DIR}/lib/release-compare-screens.sh"

# ---- Main ----

# not exported: only run_flow hands these to Maestro (export -n in case the file says `export`)
# shellcheck source=/dev/null
. "$ENV_FILE"
export -n URL USER_NAME PASSWORD 2>/dev/null
for v in URL USER_NAME PASSWORD; do [ -n "${!v:-}" ] || { echo "release-compare: $v missing from $ENV_FILE" >&2; exit 1; }; done
# an *.apk spec that isn't a file would otherwise be tried as a git ref, with a confusing error
for spec in "$OLD_SPEC" "$NEW_SPEC"; do
    case "$spec" in *.apk) [ -f "$spec" ] || { echo "release-compare: no such APK: $spec" >&2; exit 1; } ;; esac
done
# so a branch name resolves to what was pushed (resolve_ref); offline keeps the last fetch
if [ ! -f "$OLD_SPEC" ] || [ ! -f "$NEW_SPEC" ]; then
    git fetch -q origin >>"$LOG" 2>&1 || echo "release-compare: WARNING: git fetch origin failed, refs are as last fetched" >&2
fi
echo "release-compare: ${VERSIONS[*]} / ${PHASE}, output ${OUT}, logs ${WORK}"

# upgrade-check.sh builds both sides through the same cache; stdout is exactly "old=<apk>" and "new=<apk>"
if [ "$RESOLVE_ONLY" = 1 ]; then
    resolve_apk "$OLD_SPEC" old >&2 || exit 1
    echo "old=${APK_PATH}"
    resolve_apk "$NEW_SPEC" new >&2 || exit 1
    echo "new=${APK_PATH}"
    exit 0
fi
prune_stale
# unquoted on purpose below: the emptied element must disappear, not stay as ""
# shellcheck disable=SC2206
case " ${VERSIONS[*]} " in
    *" old "*) if old_cache_restore; then VERSIONS=(${VERSIONS[@]/old/}); fi ;;
esac
# the cached old capture needs no device
if [ ${#VERSIONS[@]} -eq 0 ]; then
    write_manifest
    write_montages
    echo "release-compare: manifest ${OUT}/manifest.json, warnings: $(wc -l <"$WARNS" | tr -d ' ')"
    exit 0
fi

# armed first so the EXIT trap stops a partially started lane
LANE_STARTED=1
support/scripts/remote-emu.sh status >>"$LOG" 2>&1
case $? in
    0) DEVICE=lane ;;
    3) DEVICE=local; echo "release-compare: the box can't run a lane (see $LOG), using the local AVD ${LOCAL_AVD}" ;;
    *) echo "release-compare: remote-emu.sh status failed (see $LOG)" >&2; exit 1 ;;
esac
# shellcheck disable=SC2034  # read by run_flow
if [ "$DEVICE" = lane ]; then
    step "remote-emu: start" support/scripts/remote-emu.sh start || exit 1
    eval "$(support/scripts/remote-emu.sh env)"
    MAESTRO_DEVICE_ARGS=(--device "$(support/scripts/remote-emu.sh serial)")
    # The lane's tether (Gnirehtet) asks to set up a VPN on a fresh boot; the dialog would sit over the first frames.
    support/scripts/remote-emu.sh tap-text OK >>"$LOG" 2>&1 || true
else
    step "local AVD: start" local_avd_up || exit 1
    MAESTRO_DEVICE_ARGS=(--device "$ANDROID_SERIAL")
fi

failed=0
for ver in "${VERSIONS[@]}"; do
    vfailed=0
    if [ "$PHASE" != screens ]; then setup "$ver" || { echo "release-compare: ${ver} setup incomplete" >&2; failed=1; continue; }; fi
    if [ "$PHASE" != setup ]; then
        screens "$ver" || { echo "release-compare: ${ver} screens incomplete" >&2; failed=1; vfailed=1; }
        [ "$ver" = new ] && [ "$vfailed" = 0 ] && local_folder_phase
    fi
    [ "$ver" = old ] && [ "$vfailed" = 0 ] && old_cache_save
done
write_manifest
write_montages
echo "release-compare: manifest ${OUT}/manifest.json, warnings: $(wc -l <"$WARNS" | tr -d ' ')"
[ -s "$WARNS" ] && cat "$WARNS" >&2
exit "$failed"
