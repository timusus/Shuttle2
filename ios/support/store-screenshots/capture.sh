#!/usr/bin/env bash
# Captures the App Store screenshots for Shuttle Music from an invented library: installs a fresh Debug app on a leased
# iPhone simulator and the project's iPad simulator, copies the sample library (build-library.py) into its Documents,
# walks the first-run setup on it, seeds a listening history (seed-history.py), turns the equalizer on, plays an
# album, then walks slots.json.
#
#   ./capture.sh                 build, set up and capture iPhone and iPad, plus the paywall (iPhone)
#   ./capture.sh --skip-build    install the last Debug build instead of rebuilding
#   ./capture.sh --device iphone|ipad
#   ./capture.sh --paywall-only  iPhone only, and only the paywall (raw/iphone/paywall.png)
#   ./capture.sh --skip-setup    keep the app and its state from the last run (no reinstall, no import, no play)
#
# Raw PNGs land in raw/<iphone|ipad>/<n>.png next to this script, and the paywall in raw/iphone/paywall.png;
# render.py frames the slots and copies the paywall out as it is.
#
# Each slot's `steps` (slots.json; `ipad_steps` replaces them on the iPad) are `hook:<action[?query]>` (an
# s2-debug://<action> URL written to Documents/screenshot_hook.url, which the Debug build polls; the actions are in
# ios/S2/Debug/ScreenshotHooks.swift), `maestro:<flow>` (a file in maestro/), `entitlement:<name>` (the debug
# entitlement override, applied by relaunching) and `sleep:<seconds>`.
#
# Artwork: every album is invented and carries its own generated cover (52fbd9343's covers, embedded in its files),
# so the frames show real-looking artwork with nothing commercial in them; nothing needs the debug generated-artwork
# switch, which swaps every cover for a muted placeholder-style tile.
#
# Simulators: the iPhone is leased from the shared ios-sim pool (lease-sim.sh, holder $S2_SIM_HOLDER, default
# store-screenshots) and released at the end, as is every status-bar override, by the EXIT trap; the iPad is
# the project's own "S2 iPad". Neither is ever created.
# The iPhone 16 is 1179x2556 and the iPad Pro 11-inch is 1668x2420: render.py frames them for the 6.9", 6.5" and
# 13" canvases, so the raw size only needs to be at least as large as the framed screen.
#
# Needs Maestro, xcodegen and ffmpeg on PATH; no media server.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "$HERE/../.." && pwd)"
BUNDLE_ID="com.simplecityapps.shuttle.dev"
SLOTS="$HERE/slots.json"
RAW="$HERE/raw"
LIBRARY="$HERE/library"
IPAD_NAME="S2 iPad"
export S2_SIM_HOLDER="${S2_SIM_HOLDER:-store-screenshots}"

SKIP_BUILD=0
SKIP_SETUP=0
PAYWALL_ONLY=0
DEVICES="iphone ipad"
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1 ;;
    --skip-setup) SKIP_SETUP=1 ;;
    --paywall-only) PAYWALL_ONLY=1; DEVICES=iphone ;;
    --device) DEVICES="$2"; shift ;;
    -h|--help) sed -n 2,30p "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

log() { printf '[capture] %s\n' "$*" >&2; }

# Whatever happened — success, a failed step, Ctrl-C — undo what this run did: clear every status-bar
# override we set and release the iPhone lease if we took one. A lease somebody else holds (a run that
# failed before the lease, or a pre-existing holder) is never released.
LEASE_TAKEN=0
STATUS_BAR_UDIDS=()

cleanup() {
  local udid
  for udid in ${STATUS_BAR_UDIDS[@]+"${STATUS_BAR_UDIDS[@]}"}; do
    xcrun simctl status_bar "$udid" clear >/dev/null 2>&1 || true
  done
  if [ "$LEASE_TAKEN" = 1 ]; then
    CLAUDE_CODE_SESSION_ID="$("$IOS_DIR/scripts/lease-sim.sh" --holder)" \
      ~/.claude/scripts/ios-sim/sim-lease.sh release >&2 || true
  fi
}
trap cleanup EXIT

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
  STATUS_BAR_UDIDS+=("$udid")
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

maestro_flow() {
  maestro --udid "$1" test --test-output-dir "${TMPDIR:-/tmp}/s2-store-screenshots/maestro" "$HERE/maestro/$2" >&2 </dev/null
}

# A fresh Debug app with the invented library imported, a listening history, and an album paused part-way through, so
# Home has shelves rather than its first-run hint, and the mini player, Now Playing and the queue have a song.
prepare_app() {
  local udid="$1" data
  if [ "$SKIP_BUILD" = 1 ] || [ "$BUILT" = 1 ]; then BUILD=0; else BUILD=1; BUILT=1; fi
  [ "$SKIP_SETUP" = 0 ] && xcrun simctl uninstall "$udid" "$BUNDLE_ID" 2>/dev/null || true
  # Debug switches live in the simulator's own defaults, which outlive an uninstall. Covers come from the library's
  # files, so generated artwork is off; every screen but the paywall is shot as Pro, so the entitlement override goes.
  xcrun simctl spawn "$udid" defaults delete "$BUNDLE_ID" S2GeneratedArtwork 2>/dev/null || true
  xcrun simctl spawn "$udid" defaults delete "$BUNDLE_ID" debug.entitlementOverride 2>/dev/null || true
  log "installing the Debug app (BUILD=$BUILD)"
  BUILD="$BUILD" RESET="$((1 - SKIP_SETUP))" S2_SIMULATOR_UDID="$udid" "$IOS_DIR/scripts/run-sim-server.sh" >&2
  if [ "$SKIP_SETUP" = 1 ]; then sleep 3; return 0; fi
  xcrun simctl terminate "$udid" "$BUNDLE_ID" 2>/dev/null || true
  data="$(container "$udid")"
  log "copying the sample library into Documents"
  "$HERE/build-library.py" "$LIBRARY"
  cp -R "$LIBRARY/." "$data/Documents/"
  log "first-run setup and import"
  maestro_flow "$udid" onboard-local.yaml
  xcrun simctl terminate "$udid" "$BUNDLE_ID" 2>/dev/null || true
  "$HERE/seed-history.py" "$data/Library/Application Support/song.db"
  # The equalizer on with a shaped preset, written while the app is stopped: the Settings keys Android has always used
  # (EqualizerSettings, KeyValueEqualizerPresetStore). Maestro's taps don't reach that screen's SwiftUI switch.
  xcrun simctl spawn "$udid" defaults write "$BUNDLE_ID" equalizer_enabled -bool true
  xcrun simctl spawn "$udid" defaults write "$BUNDLE_ID" preset_name -string "Bass Boost"
  xcrun simctl launch "$udid" "$BUNDLE_ID" >/dev/null
  sleep 3
  hook "$udid" "reset"
  hook "$udid" "library?category=albums"
  maestro_flow "$udid" play-album.yaml
}

walk_slots() {
  local udid="$1" device="$2"
  mkdir -p "$RAW/$device"
  # One line per slot: n<TAB>appearance<TAB>steps joined by a space (steps contain no spaces). The iPhone's paywall
  # (App Store Connect's in-app purchase review screenshot) follows the slots, unframed.
  python3 -c "
import json
data = json.load(open('$SLOTS'))
extras = [dict(data['paywall'], n='paywall')] if '$device' == 'iphone' else []
for s in ([] if $PAYWALL_ONLY else data['slots']) + extras:
    print(s['n'], s['appearance'], ' '.join(s.get('$device' + '_steps', s['steps'])), sep='\t')
" | while IFS=$'\t' read -r n appearance steps; do
    log "$device slot $n ($appearance)"
    xcrun simctl ui "$udid" appearance "$appearance"
    sleep 3 # the switch animates; a frame taken straight after shows the old appearance
    for step in $steps; do
      case "$step" in
        hook:*) hook "$udid" "${step#hook:}" ;;
        maestro:*) maestro_flow "$udid" "${step#maestro:}" ;;
        sleep:*) sleep "${step#sleep:}" ;;
        # The debug entitlement override (Settings > Pro's debug picker: None is Pro, Free shows the offer), which the
        # app applies at launch, so it's written with the app stopped and the app relaunched.
        entitlement:*)
          xcrun simctl terminate "$udid" "$BUNDLE_ID" 2>/dev/null || true
          xcrun simctl spawn "$udid" defaults write "$BUNDLE_ID" debug.entitlementOverride "${step#entitlement:}"
          xcrun simctl launch "$udid" "$BUNDLE_ID" >/dev/null
          sleep 4 ;;
        *) log "unknown step '$step'"; exit 1 ;;
      esac
    done
    xcrun simctl io "$udid" screenshot --type png "$RAW/$device/$n.png" >/dev/null 2>&1
  done
  xcrun simctl ui "$udid" appearance light
}

BUILT=0
for device in $DEVICES; do
  case "$device" in
    iphone) udid="$("$IOS_DIR/scripts/lease-sim.sh")"; LEASE_TAKEN=1 ;;
    ipad) udid="$(ipad_udid)" ;;
    *) log "unknown device '$device' (iphone|ipad)"; exit 2 ;;
  esac
  log "$device simulator $udid"
  boot_sim "$udid"
  prepare_app "$udid"
  walk_slots "$udid" "$device"
  xcrun simctl terminate "$udid" "$BUNDLE_ID" 2>/dev/null || true
  log "$device done: $(ls "$RAW/$device" | wc -l | tr -d ' ') PNGs in $RAW/$device"
done
