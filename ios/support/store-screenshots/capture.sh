#!/usr/bin/env bash
# Captures the App Store screenshots for Shuttle Music: signs in to the Jellyfin test server on a leased
# iPhone simulator and the project's iPad simulator, then walks slots.json.
#
#   ./capture.sh                 build, sign in, capture iPhone and iPad
#   ./capture.sh --skip-build    install the last Debug build instead of rebuilding
#   ./capture.sh --device iphone|ipad
#   ./capture.sh --skip-setup    keep the signed-in state from the last run (no reset, no sign-in)
#
# Raw PNGs land in raw/<iphone|ipad>/<n>.png next to this script; render.py frames them.
#
# Each slot's `steps` (slots.json) are `hook:<action[?query]>` (an s2-debug://<action> URL written to
# Documents/screenshot_hook.url, which the Debug build polls; the actions are in ios/S2/Debug/ScreenshotHooks.swift),
# `maestro:<flow>` (a file in maestro/, run through ios/scripts/maestro-sim.sh) and `sleep:<seconds>`.
#
# Simulators: the iPhone is leased from the shared ios-sim pool (lease-sim.sh, holder $S2_SIM_HOLDER, default
# store-screenshots) and released at the end; the iPad is the project's own "S2 iPad". Neither is ever created.
# The iPhone 16 is 1179x2556 and the iPad Pro 11-inch is 1668x2420: render.py frames them for the 6.9", 6.5" and
# 13" canvases, so the raw size only needs to be at least as large as the framed screen.
#
# Needs ~/.config/s2-test/jellyfin.env (see ios/scripts/maestro-sim.sh), Maestro and xcodegen on PATH.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "$HERE/../.." && pwd)"
BUNDLE_ID="com.simplecityapps.shuttle.dev"
SLOTS="$HERE/slots.json"
RAW="$HERE/raw"
IPAD_NAME="S2 iPad"
export S2_SIM_HOLDER="${S2_SIM_HOLDER:-store-screenshots}"

SKIP_BUILD=0
SKIP_SETUP=0
DEVICES="iphone ipad"
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1 ;;
    --skip-setup) SKIP_SETUP=1 ;;
    --device) DEVICES="$2"; shift ;;
    -h|--help) sed -n 2,22p "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

log() { printf '[capture] %s\n' "$*" >&2; }

ipad_udid() {
  xcrun simctl list devices -j | python3 -c "
import json, sys
for devs in json.load(sys.stdin)['devices'].values():
    for d in devs:
        if d['name'] == '$IPAD_NAME' and d['isAvailable']:
            print(d['udid']); sys.exit()
sys.exit('no simulator named $IPAD_NAME')"
}

boot_sim() {
  local udid="$1"
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b >/dev/null
  xcrun simctl status_bar "$udid" override --time 9:41 --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularBars 4
}

container() { xcrun simctl get_app_container "$1" "$BUNDLE_ID" data; }

# A hook is a file the Debug build polls and deletes; wait for the delete so the next one doesn't overwrite it.
hook() {
  local udid="$1" action="$2" file
  file="$(container "$udid")/Documents/screenshot_hook.url"
  printf 's2-debug://%s\n' "$action" > "$file"
  for _ in $(seq 1 20); do [ -f "$file" ] || return 0; sleep 0.5; done
  log "hook '$action' was not picked up in 10 s; is the Debug build running?"; exit 1
}

maestro_flow() { S2_SIMULATOR_UDID="$1" "$IOS_DIR/scripts/maestro-sim.sh" "../support/store-screenshots/maestro/$2" >&2; }

# Installs (and builds, once) the Debug app, erases its state, signs in and plays a song so the mini player has one.
prepare_app() {
  local udid="$1"
  if [ "$SKIP_BUILD" = 1 ] || [ "$BUILT" = 1 ]; then BUILD=0; else BUILD=1; BUILT=1; fi
  log "installing the Debug app (BUILD=$BUILD)"
  BUILD="$BUILD" RESET="$((1 - SKIP_SETUP))" S2_SIMULATOR_UDID="$udid" "$IOS_DIR/scripts/run-sim-server.sh" >&2
  [ "$SKIP_SETUP" = 1 ] && return 0
  log "signing in"
  maestro_flow "$udid" sign-in.yaml
  # Wait for the import before the first hook: the library lists are empty until it is done.
  hook "$udid" "reset"
  hook "$udid" "library?category=songs"
  maestro_flow "$udid" play-song.yaml
}

walk_slots() {
  local udid="$1" device="$2"
  mkdir -p "$RAW/$device"
  # One line per slot: n<TAB>appearance<TAB>steps joined by a space (steps contain no spaces).
  python3 -c "
import json
for s in json.load(open('$SLOTS'))['slots']:
    print(s['n'], s['appearance'], ' '.join(s['steps']), sep='\t')
" | while IFS=$'\t' read -r n appearance steps; do
    log "$device slot $n ($appearance)"
    xcrun simctl ui "$udid" appearance "$appearance"
    for step in $steps; do
      case "$step" in
        hook:*) hook "$udid" "${step#hook:}" ;;
        maestro:*) maestro_flow "$udid" "${step#maestro:}" ;;
        sleep:*) sleep "${step#sleep:}" ;;
        *) log "unknown step '$step'"; exit 1 ;;
      esac
    done
    xcrun simctl io "$udid" screenshot --type png "$RAW/$device/$n.png" >/dev/null 2>&1
  done
  xcrun simctl ui "$udid" appearance light
}

BUILT=0
RELEASE_LEASE=0
for device in $DEVICES; do
  case "$device" in
    iphone) udid="$("$IOS_DIR/scripts/lease-sim.sh")"; RELEASE_LEASE=1 ;;
    ipad) udid="$(ipad_udid)" ;;
    *) log "unknown device '$device' (iphone|ipad)"; exit 2 ;;
  esac
  log "$device simulator $udid"
  boot_sim "$udid"
  prepare_app "$udid"
  walk_slots "$udid" "$device"
  xcrun simctl status_bar "$udid" clear 2>/dev/null || true
  xcrun simctl terminate "$udid" "$BUNDLE_ID" 2>/dev/null || true
  log "$device done: $(ls "$RAW/$device" | wc -l | tr -d ' ') PNGs in $RAW/$device"
done
if [ "$RELEASE_LEASE" = 1 ]; then
  CLAUDE_CODE_SESSION_ID="$("$IOS_DIR/scripts/lease-sim.sh" --holder)" ~/.claude/scripts/ios-sim/sim-lease.sh release >&2 || true
fi
