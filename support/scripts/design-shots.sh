#!/usr/bin/env bash
# Deterministic Maestro screenshot tour of both apps, for design audits (#728). One call produces a
# fixed set of screenshots across themes, text sizes and form factors; the mobile-design skill reads
# the PNGs afterwards (.claude/skills/mobile-design/evaluate.md), so nobody drives a device
# screenshot-by-screenshot.
#
#   support/scripts/design-shots.sh --platform android|ios|both [--screens a,b,...]
#                                   [--devices a,b] [--matrix quick|full] [--contact-sheet] [--flow-timeout <secs>]
#                                   [--apk <path>] [--remote-build] [--no-ios-build]
#
#     --platform         android (a remote-emu.sh lane), ios (a simulator leased from the shared pool,
#                        S2_SIM_HOLDER, never `simctl create`), or both (one after the other)
#     --screens a,b      only these screens (default: all). Names:
#                          home  library-songs  library-albums  library-artists  album-detail
#                          artist-detail  now-playing  queue  mini-player  search  settings
#                          empty-state (a search with no results)
#     --devices a,b      form factors. Android: phone tablet foldable; iOS: iphone ipad.
#                        Default: phone,iphone
#     --matrix quick     light + dark; default text size. (default)
#     --matrix full      light + dark; default + large text. Recommended for audits (with the
#                        default devices: phone x 4 cells per screen).
#     --contact-sheet    one ImageMagick `montage` per platform and screen, if `montage` is installed
#     --flow-timeout     per-flow timeout in seconds (default 300)
#     --apk <path>       Android: install this APK instead of building/reusing the cached one
#     --remote-build     Android: build via remote-build.sh (the Mac or the WSL box)
#     --no-ios-build     iOS: install the last build (ios/build/DerivedData) instead of rebuilding
#
# Matrix cells are applied outside the flows, with adb / simctl, then every screen's flow runs:
#   theme   Android `cmd uimode night yes|no`;     iOS `simctl ui appearance light|dark`
#   text    Android font_scale 1.0 | 2.0;          iOS content size large | accessibility-extra-extra-extra-large (AX5)
#   device  Android has no tablet or foldable AVD, so those are `wm size` + `wm density` overrides on
#           the lane's phone AVD (tablet 1600x2560 @ 320 dpi = 800x1280 dp; foldable, unfolded
#           1840x2208 @ 420 dpi = ~877x1052 dp; no hinge or posture); the manifest says so.
#           iOS: the leased iPhone, and the existing "S2 iPad" simulator (booted, then shut down).
#
# Library: Android seeds the `library` fixture (seed-test-media.sh: 16 albums with embedded covers,
# the screenshot tests' sample library, so artwork and ArtworkTheme colour show). One album (Blue
# Hours) is queued and paused for the player screens. iOS signs in to the Jellyfin test server in
# ~/.config/s2-test/jellyfin.env (ios/maestro/sign-in-jellyfin.yaml), once per simulator.
#
# Flows: support/maestro/design/<screen>.yaml (Android), ios/maestro/design/<screen>.yaml (iOS); each
# ends in `takeScreenshot: <screen>`.
#
# Output (run = YYYYmmdd-HHMMSS):
#   shots/<run>/<platform>/<screen>__<device>__<theme>__<text>.png    text = default | large
#   shots/<run>/manifest.md                  every shot, every failure (flow, failing step, last
#                                            Maestro error line), the device notes
#   shots/<run>/contact-sheets/<platform>__<screen>.png    with --contact-sheet
# Full output: tmp/design-shots/<run>/ (one Maestro log per flow run) and the run log it prints.
# One failing flow never aborts the rest. Exits non-zero only if no shot was produced at all.
#
# Always stops the Android lane and releases the simulator on exit (trap). Run it in the background
# through longjob, and read manifest.md first, then the PNGs:
#   support/scripts/longjob.sh start design-shots -- support/scripts/design-shots.sh --platform both
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "$REPO_ROOT" || exit 1
# platform-tools (adb) is not on a stock Mac PATH; remote-emu.sh adds the same dir.
PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

# shellcheck source=support/scripts/checks/_timeout_fallback.sh
source "${SCRIPT_DIR}/checks/_timeout_fallback.sh"
# shellcheck source=support/scripts/emu-lane.sh
source "${SCRIPT_DIR}/emu-lane.sh"

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }

ALL_SCREENS=(home library-songs library-albums library-artists album-detail artist-detail now-playing queue mini-player search settings empty-state)
PLATFORM=""
SCREENS_ARG=""
MATRIX=quick
DEVICES_ARG=phone,iphone
SEED_FIXTURE=library
CONTACT=0
FLOW_TIMEOUT=300
APK=""
REMOTE_BUILD="${S2_REMOTE_BUILD:-0}"
IOS_BUILD=1
NO_RESET=0
NO_SEED=0
REMOTE=""

while [ $# -gt 0 ]; do
    case "$1" in
        --platform) PLATFORM="${2:?design-shots: --platform needs android, ios or both}"; shift 2 ;;
        --screens) SCREENS_ARG="${2:?design-shots: --screens needs a comma-separated list}"; shift 2 ;;
        --devices) DEVICES_ARG="${2:?design-shots: --devices needs a comma-separated list}"; shift 2 ;;
        --matrix) MATRIX="${2:?design-shots: --matrix needs quick or full}"; shift 2 ;;
        --contact-sheet) CONTACT=1; shift ;;
        --flow-timeout) FLOW_TIMEOUT="${2:?design-shots: --flow-timeout needs seconds}"; shift 2 ;;
        --apk) APK="${2:?design-shots: --apk needs a path}"; shift 2 ;;
        --remote-build) REMOTE_BUILD=1; shift ;;
        --no-ios-build) IOS_BUILD=0; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "design-shots: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
    esac
done

case "$PLATFORM" in android | ios | both) ;; *) echo "design-shots: --platform must be android, ios or both" >&2; exit 2 ;; esac
case "$MATRIX" in
    quick) THEMES=(light dark); TEXTS=(default) ;;
    full) THEMES=(light dark); TEXTS=(default large) ;;
    *) echo "design-shots: --matrix must be quick or full" >&2; exit 2 ;;
esac

IFS=',' read -ra DEVICES <<<"$DEVICES_ARG"
ANDROID_DEVICES=(); IOS_DEVICES=()
for d in "${DEVICES[@]}"; do
    case "$d" in
        phone | tablet | foldable) ANDROID_DEVICES+=("$d") ;;
        iphone | ipad) IOS_DEVICES+=("$d") ;;
        *) echo "design-shots: unknown device '$d' (want: phone tablet foldable iphone ipad)" >&2; exit 2 ;;
    esac
done
case "$PLATFORM" in
    android) [ ${#ANDROID_DEVICES[@]} -gt 0 ] || { echo "design-shots: --platform android needs one of phone, tablet, foldable in --devices" >&2; exit 2; } ;;
    ios) [ ${#IOS_DEVICES[@]} -gt 0 ] || { echo "design-shots: --platform ios needs iphone or ipad in --devices" >&2; exit 2; } ;;
    both) { [ ${#ANDROID_DEVICES[@]} -gt 0 ] && [ ${#IOS_DEVICES[@]} -gt 0 ]; } || { echo "design-shots: --platform both needs an Android and an iOS device in --devices" >&2; exit 2; } ;;
esac

SCREENS=("${ALL_SCREENS[@]}")
if [ -n "$SCREENS_ARG" ]; then
    IFS=',' read -ra SCREENS <<<"$SCREENS_ARG"
    for s in "${SCREENS[@]}"; do
        case " ${ALL_SCREENS[*]} " in
            *" $s "*) ;;
            *) echo "design-shots: unknown screen '$s' (want: ${ALL_SCREENS[*]})" >&2; exit 2 ;;
        esac
    done
fi

RUN="$(date +%Y%m%d-%H%M%S)"
OUT="${REPO_ROOT}/shots/${RUN}"
WORK="${REPO_ROOT}/tmp/design-shots/${RUN}"
mkdir -p "$OUT" "$WORK"
LOG="${WORK}/run.log"
: >"$LOG"
echo "design-shots: run ${RUN}, output ${OUT}, log ${LOG}"

SHOT_ROWS="${WORK}/shots.rows"
FAIL_ROWS="${WORK}/fails.rows"
NOTES="${WORK}/notes.rows"
: >"$SHOT_ROWS"; : >"$FAIL_ROWS"; : >"$NOTES"
SHOT_COUNT=0
LANE_STARTED=0
SIM_LEASED=0
IPAD_BOOTED_UDID=""
IOS_MATRIX_UDIDS=""
START_TS=$(date +%s)

# ---- helpers ----

# step <description> <command...>: command output to the log, one ok/FAILED line to stdout.
step() {
    local desc="$1" start rc; shift
    start=$(date +%s)
    if "$@" >>"$LOG" 2>&1; then
        echo "design-shots: ${desc} -- ok ($(($(date +%s) - start))s)"
        return 0
    fi
    rc=$?
    echo "design-shots: ${desc} -- FAILED ($(($(date +%s) - start))s, see $LOG)" >&2
    return "$rc"
}

cell() { printf '%s' "${1//|/\\|}"; }

record_fail() { # platform, screen/stage, cell, step, error
    printf '| %s | %s | %s | %s | %s |\n' "$(cell "$1")" "$(cell "$2")" "$(cell "$3")" "$(cell "$4")" "$(cell "$5")" >>"$FAIL_ROWS"
    echo "design-shots: FAIL $1 $2 ($3): $5" >&2
}

write_manifest() {
    local m="${OUT}/manifest.md"
    {
        echo "# Design shots ${RUN}"
        echo
        echo "- commit: $(git rev-parse --short HEAD 2>/dev/null) ($(git rev-parse --abbrev-ref HEAD 2>/dev/null))"
        echo "- platform: ${PLATFORM}, matrix: ${MATRIX}, screens: ${SCREENS[*]}"
        echo "- shots: ${SHOT_COUNT}, failures: $(wc -l <"$FAIL_ROWS" | tr -d ' ')"
        echo "- logs: ${WORK}"
        if [ -s "$NOTES" ]; then
            echo
            echo "## Devices"
            echo
            cat "$NOTES"
        fi
        echo
        echo "## Shots"
        echo
        echo "| Platform | Screen | Device | Theme | Text | File |"
        echo "|---|---|---|---|---|---|"
        cat "$SHOT_ROWS"
        echo
        echo "## Failures"
        echo
        if [ -s "$FAIL_ROWS" ]; then
            echo "| Platform | Screen | Cell | Failing step | Last error |"
            echo "|---|---|---|---|---|"
            cat "$FAIL_ROWS"
        else
            echo "None."
        fi
    } >"$m"
}

# android_stop: puts the display and theme back, then stops the lane. Idempotent, and a no-op once the
# lane is down, so the end of the Android phase and the EXIT trap can both call it.
android_stop() {
    [ "$LANE_STARTED" = "1" ] || return 0
    run_with_timeout 60 android_reset_display
    echo "design-shots: stopping lane ..."
    support/scripts/remote-emu.sh stop >>"$LOG" 2>&1 \
        || echo "design-shots: WARNING: remote-emu.sh stop failed, check the lane manually (see $LOG)" >&2
    LANE_STARTED=0
}

cleanup() {
    write_manifest
    android_stop
    ios_release
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# fail_info <maestro log>: sets FSTEP (the first failed step) and FERR (the last error line).
fail_info() {
    local clean
    clean="$(sed $'s/\x1b\\[[0-9;]*[A-Za-z]//g' "$1" 2>/dev/null)"
    FSTEP="$(grep -a -m1 -E 'FAILED|❌' <<<"$clean" | sed 's/^[[:space:]]*//' | cut -c1-160)"
    FERR="$(grep -a -E 'Assertion|not found|Error|error|Exception|Timeout|timed out|Unable' <<<"$clean" | tail -1 | sed 's/^[[:space:]]*//' | cut -c1-200)"
    [ -n "$FERR" ] || FERR="$(grep -a -v '^[[:space:]]*$' <<<"$clean" | tail -1 | cut -c1-200)"
    [ -n "$FSTEP" ] || FSTEP="-"
    [ -n "$FERR" ] || FERR="no output"
}

# run_screen <platform> <screen> <device> <theme> <text> <flow> [maestro args...]
run_screen() {
    local plat="$1" screen="$2" dev="$3" theme="$4" text="$5" flow="$6" rc png name dest mlog fail_dir
    shift 6
    name="${screen}__${dev}__${theme}__${text}"
    mlog="${WORK}/${plat}-${name}.log"
    if [ ! -f "$flow" ]; then
        record_fail "$plat" "$screen" "${dev}/${theme}/${text}" "-" "no such flow ($flow)"
        return
    fi
    png="${WORK}/collected-${plat}/${screen}.png"
    rm -f -- "$png" # never reuse a PNG from an earlier cell
    fail_dir="${REPO_ROOT}/tmp/maestro/failed-${plat}-${name}"
    run_with_timeout "$FLOW_TIMEOUT" maestro_run "$flow" "${WORK}/scratch-${plat}" "$@" >"$mlog" 2>&1
    rc=$?
    cat "$mlog" >>"$LOG"
    maestro_collect_shots "${WORK}/scratch-${plat}" "${WORK}/collected-${plat}" "$rc" "$fail_dir"
    [ "$rc" -eq 0 ] && rm -rf -- "$fail_dir"
    if [ "$rc" -eq 0 ] && [ -f "$png" ]; then
        dest="${OUT}/${plat}"
        mkdir -p "$dest"
        mv -f "$png" "${dest}/${name}.png"
        printf '| %s | %s | %s | %s | %s | %s/%s.png |\n' "$plat" "$screen" "$dev" "$theme" "$text" "$plat" "$name" >>"$SHOT_ROWS"
        SHOT_COUNT=$((SHOT_COUNT + 1))
        echo "design-shots: ${plat}/${name} -- ok"
    elif [ "$rc" -eq 124 ]; then
        record_fail "$plat" "$screen" "${dev}/${theme}/${text}" "(flow timed out)" "timed out after ${FLOW_TIMEOUT}s (log ${mlog}, Maestro debug output ${fail_dir})"
    else
        fail_info "$mlog"
        [ "$rc" -eq 0 ] && { FSTEP="-"; FERR="flow passed but took no '${screen}' screenshot"; }
        [ "$rc" -eq 0 ] || FERR="${FERR} (Maestro debug output ${fail_dir})"
        record_fail "$plat" "$screen" "${dev}/${theme}/${text}" "$FSTEP" "$FERR"
    fi
}

# ---- Android ----

# adb_set <description> <adb args...>: a matrix setting that failed to apply is a failure, not a silent
# wrong-theme screenshot.
adb_set() {
    local desc="$1"; shift
    adb "$@" >>"$LOG" 2>&1 || { record_fail android setup - "$desc" "adb $* failed, see $LOG"; return 1; }
}

android_device() { # phone | tablet | foldable
    case "$1" in
        phone) adb_set "wm size reset" shell wm size reset && adb_set "wm density reset" shell wm density reset ;;
        tablet) adb_set "wm size (tablet)" shell wm size 1600x2560 && adb_set "wm density (tablet)" shell wm density 320 ;;
        foldable) adb_set "wm size (foldable)" shell wm size 1840x2208 && adb_set "wm density (foldable)" shell wm density 420 ;;
    esac
}

android_reset_display() {
    adb shell wm size reset >/dev/null 2>&1
    adb shell wm density reset >/dev/null 2>&1
    adb shell settings put system font_scale 1.0 >/dev/null 2>&1
    adb shell cmd uimode night no >/dev/null 2>&1
}

android_phase() {
    local dev theme text screen scale
    echo "design-shots: == Android =="
    if ! emu_resolve_apk; then record_fail android setup - "build" "could not build or find the debug APK"; return; fi
    if ! emu_lane_up; then record_fail android setup - "lane start / install / seed" "see $LOG"; return; fi
    MAESTRO_DEVICE_ARGS=(--device "$(support/scripts/remote-emu.sh serial)")
    # A queue for the player screens, then paused: playing music keeps the UI from ever going idle.
    step "queue Blue Hours and pause" bash -c "support/scripts/s2-debug.sh PLAY_ALL --es album \"'Blue Hours'\" && support/scripts/s2-debug.sh PAUSE" \
        || record_fail android setup - "queue the fixture" "player screens will show no queue"
    {
        echo "- Android: Pixel-class AVD on the remote-emu.sh lane; \`library\` fixture (16 albums with embedded covers); Blue Hours queued and paused."
        case " ${ANDROID_DEVICES[*]} " in *" tablet "*)
            echo "- Android tablet / foldable: no such AVD, so \`wm size\`/\`wm density\` overrides on the phone AVD: tablet 1600x2560 @ 320 dpi (800x1280 dp), foldable unfolded 1840x2208 @ 420 dpi (~877x1052 dp), no hinge or posture." ;;
        esac
    } >>"$NOTES"
    for dev in "${ANDROID_DEVICES[@]}"; do
        android_device "$dev" || continue
        for theme in "${THEMES[@]}"; do
            adb_set "theme $theme" shell cmd uimode night "$([ "$theme" = dark ] && echo yes || echo no)" || continue
            for text in "${TEXTS[@]}"; do
                scale=1.0; [ "$text" = large ] && scale=2.0
                adb_set "font_scale $scale" shell settings put system font_scale "$scale" || continue
                for screen in "${SCREENS[@]}"; do
                    run_screen android "$screen" "$dev" "$theme" "$text" "support/maestro/design/${screen}.yaml"
                done
            done
        done
    done
}

# ---- iOS ----

# ios_reset_appearance: puts every simulator the matrix touched back to light / default text size.
# Runs before the shutdown / release, and tolerates a simulator that is already gone.
ios_reset_appearance() {
    local u
    for u in $IOS_MATRIX_UDIDS; do
        run_with_timeout 60 xcrun simctl ui "$u" appearance light >>"$LOG" 2>&1
        run_with_timeout 60 xcrun simctl ui "$u" content_size large >>"$LOG" 2>&1
    done
    IOS_MATRIX_UDIDS=""
    return 0
}

ios_release() {
    local holder
    [ "$SIM_LEASED" = "1" ] || [ -n "$IPAD_BOOTED_UDID" ] || return 0
    ios_reset_appearance
    if [ -n "$IPAD_BOOTED_UDID" ]; then
        xcrun simctl shutdown "$IPAD_BOOTED_UDID" >>"$LOG" 2>&1
        IPAD_BOOTED_UDID=""
    fi
    if [ "$SIM_LEASED" = "1" ]; then
        echo "design-shots: releasing simulator ..."
        holder="$(ios/scripts/lease-sim.sh --holder)"
        CLAUDE_CODE_SESSION_ID="$holder" "$HOME/.claude/scripts/ios-sim/sim-lease.sh" release >>"$LOG" 2>&1 \
            || echo "design-shots: WARNING: sim release failed, check ~/.claude/scripts/ios-sim/sim-lease.sh status" >&2
        SIM_LEASED=0
    fi
}

ios_sign_in() { # udid
    local env_file="$HOME/.config/s2-test/jellyfin.env" URL="" API_KEY=""
    [ -f "$env_file" ] || { echo "missing $env_file (URL=, API_KEY=)" >"${WORK}/ios-signin.log"; return 1; }
    # shellcheck disable=SC1090
    . "$env_file"
    [ -n "$URL" ] && [ -n "$API_KEY" ] || { echo "$env_file must set URL and API_KEY" >"${WORK}/ios-signin.log"; return 1; }
    MAESTRO_SERVER_URL="${URL%/}" MAESTRO_API_KEY="$API_KEY" MAESTRO_SERVER_USER="${SERVER_USER:-shuttle-test}" \
        run_with_timeout "$FLOW_TIMEOUT" maestro_run ios/maestro/sign-in-jellyfin.yaml "${WORK}/scratch-signin" >"${WORK}/ios-signin.log" 2>&1
    local rc=$?
    cat "${WORK}/ios-signin.log" >>"$LOG"
    maestro_collect_shots "${WORK}/scratch-signin" "${WORK}/collected-signin"
    return "$rc"
}

ios_run_device() { # label udid
    local dev="$1" udid="$2" theme text screen ctext
    MAESTRO_DEVICE_ARGS=(--udid "$udid")
    IOS_MATRIX_UDIDS="${IOS_MATRIX_UDIDS} ${udid}"
    if ! ios_sign_in "$udid"; then
        fail_info "${WORK}/ios-signin.log"
        record_fail ios "setup ($dev)" - "sign in to the Jellyfin test server: ${FSTEP}" "$FERR"
        return
    fi
    for theme in "${THEMES[@]}"; do
        xcrun simctl ui "$udid" appearance "$theme" >>"$LOG" 2>&1
        for text in "${TEXTS[@]}"; do
            ctext=large; [ "$text" = large ] && ctext=accessibility-extra-extra-extra-large
            xcrun simctl ui "$udid" content_size "$ctext" >>"$LOG" 2>&1
            for screen in "${SCREENS[@]}"; do
                run_screen ios "$screen" "$dev" "$theme" "$text" "ios/maestro/design/${screen}.yaml"
            done
        done
    done
}

ios_phase() {
    local udid ipad dev lease_out="${WORK}/lease.out"
    echo "design-shots: == iOS =="
    export S2_SIM_HOLDER="${S2_SIM_HOLDER:-design-shots-$$}"
    run_with_timeout 300 ios/scripts/lease-sim.sh >"$lease_out" 2>>"$LOG"
    udid="$(tail -1 "$lease_out" 2>/dev/null)"
    if [ -z "$udid" ]; then
        record_fail ios setup - "lease a simulator (ios/scripts/lease-sim.sh)" "no simulator leased within 5 minutes (pool missing or full)"
        return
    fi
    SIM_LEASED=1
    echo "design-shots: leased simulator ${udid}"
    if ! BUILD="$IOS_BUILD" S2_SIMULATOR_UDID="$udid" step "ios: build + install" ios/scripts/run-sim-server.sh; then
        record_fail ios setup - "ios/scripts/run-sim-server.sh" "build or install failed, see $LOG"
        return
    fi
    echo "- iOS: iPhone = the leased pool simulator ($(xcrun simctl list devices -j | python3 -c 'import json,sys; u=sys.argv[1]; print(next((d["name"] for v in json.load(sys.stdin)["devices"].values() for d in v if d["udid"]==u), u))' "$udid")); library = the Jellyfin test server." >>"$NOTES"
    for dev in "${IOS_DEVICES[@]}"; do
        case "$dev" in
            iphone) ios_run_device iphone "$udid" ;;
            ipad)
                ipad="$(xcrun simctl list devices available -j | python3 -c 'import json,sys; print(next((d["udid"] for v in json.load(sys.stdin)["devices"].values() for d in v if d["name"]=="S2 iPad"), ""))')"
                if [ -z "$ipad" ]; then record_fail ios setup ipad "find the \"S2 iPad\" simulator" "not installed"; continue; fi
                if xcrun simctl list devices | grep "$ipad" | grep -q Booted; then
                    record_fail ios setup ipad "boot the \"S2 iPad\" simulator" "already booted by another session"
                    continue
                fi
                step "ios: boot iPad" xcrun simctl boot "$ipad" || { record_fail ios setup ipad "simctl boot" "see $LOG"; continue; }
                IPAD_BOOTED_UDID="$ipad"
                step "ios: wait for iPad boot" xcrun simctl bootstatus "$ipad" -b || { record_fail ios setup ipad "simctl bootstatus" "see $LOG"; continue; }
                if ! BUILD=0 S2_SIMULATOR_UDID="$ipad" step "ios: install on iPad" ios/scripts/run-sim-server.sh; then
                    record_fail ios setup ipad "install" "see $LOG"
                    continue
                fi
                echo "- iOS iPad: the existing \"S2 iPad\" simulator, booted for this run and shut down after." >>"$NOTES"
                ios_run_device ipad "$ipad"
                ;;
        esac
    done
}

# ---- run ----

case "$PLATFORM" in
    android) android_phase ;;
    ios) ios_phase ;;
    both) android_phase; android_stop; ios_phase ;;
esac

# ---- contact sheets ----
if [ "$CONTACT" = 1 ]; then
    if command -v montage >/dev/null 2>&1; then
        mkdir -p "${OUT}/contact-sheets"
        for plat_dir in "$OUT"/android "$OUT"/ios; do
            [ -d "$plat_dir" ] || continue
            plat="$(basename "$plat_dir")"
            for screen in "${SCREENS[@]}"; do
                files=("$plat_dir"/"${screen}"__*.png)
                [ -f "${files[0]}" ] || continue
                montage -label '%t' -pointsize 14 "${files[@]}" -tile 4x -geometry 360x+8+8 "${OUT}/contact-sheets/${plat}__${screen}.png" >>"$LOG" 2>&1 \
                    || montage "${files[@]}" -tile 4x -geometry 360x+8+8 "${OUT}/contact-sheets/${plat}__${screen}.png" >>"$LOG" 2>&1 \
                    || echo "design-shots: contact sheet for ${plat} ${screen} failed (see $LOG)" >&2
            done
        done
    else
        echo "- Contact sheets skipped: ImageMagick \`montage\` is not installed." >>"$NOTES"
        echo "design-shots: --contact-sheet skipped, montage not installed" >&2
    fi
fi

write_manifest
FAIL_COUNT=$(wc -l <"$FAIL_ROWS" | tr -d ' ')
echo "design-shots: ${SHOT_COUNT} shot(s), ${FAIL_COUNT} failure(s) in $(($(date +%s) - START_TS))s -- manifest: ${OUT}/manifest.md"
[ "$SHOT_COUNT" -gt 0 ]
