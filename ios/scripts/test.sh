#!/usr/bin/env bash
# Runs the iOS tests. Installs the FFmpeg frameworks first (ios/scripts/build-ffmpeg.sh; a no-op once
# installed). Does not relink Shared.framework: run ios/scripts/build-framework.sh after a Kotlin change.
#
#   ios/scripts/test.sh               # the S2 scheme (S2Tests) on an available iPhone simulator
#   ios/scripts/test.sh --package     # `swift test` in ios/Playback, on the Mac (no simulator)
#
# The simulator is $S2_SIMULATOR_UDID if set; else, if the shared lease pool's device.sh exists, this
# session's leased device (set $S2_SIM_HOLDER to lease as a different holder, e.g. for a parallel
# worker); else a booted iPhone, or the iPhone on the newest iOS runtime. Other arguments go to
# xcodebuild or `swift test` (e.g. `-only-testing:S2Tests/NowPlayingControllerTests`,
# `--filter MusicPlaybackFormatsTests`).
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
package=0
args=()
for arg in "$@"; do
  case "$arg" in
    --package) package=1 ;;
    -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) args+=("$arg") ;;
  esac
done

"$ios_dir/scripts/build-ffmpeg.sh" >/dev/null

if [[ "$package" == 1 ]]; then
  cd "$ios_dir/Playback"
  echo "==> swift test ${args[*]+"${args[*]}"}"
  exec swift test ${args[@]+"${args[@]}"}
fi

if [[ ! -d "$ios_dir/../shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework" ]]; then
  echo "ERROR: no simulator Shared.framework; run ios/scripts/build-framework.sh first" >&2
  exit 1
fi

pick_simulator() {
  xcrun simctl list devices available -j | python3 -c '
import json, re, sys
devices = json.load(sys.stdin)["devices"]
def version(runtime):
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    return (int(m.group(1)), int(m.group(2))) if m else None
candidates = []
for runtime, entries in devices.items():
    v = version(runtime)
    if v is None:
        continue
    for d in entries:
        if d["name"].startswith("iPhone"):
            candidates.append((d["state"] == "Booted", v, d["name"], d["udid"]))
if not candidates:
    sys.exit("no available iPhone simulator; create one in Xcode > Devices and Simulators")
booted, v, name, udid = max(candidates)
print(udid)
state = ", booted" if booted else ""
print(f"==> simulator: {name} (iOS {v[0]}.{v[1]}{state})", file=sys.stderr)
'
}

lease_udid() {
  local device_sh="$HOME/.claude/scripts/ios-sim/device.sh"
  [ -x "$device_sh" ] || return 1
  if [ -n "${S2_SIM_HOLDER:-}" ]; then
    CLAUDE_CODE_SESSION_ID="$S2_SIM_HOLDER" "$device_sh"
  else
    "$device_sh"
  fi
}

udid="${S2_SIMULATOR_UDID:-$(lease_udid || pick_simulator)}"
cd "$ios_dir"
echo "==> xcodebuild test -scheme S2 -destination id=$udid ${args[*]+"${args[*]}"}"
xcodebuild test -project S2.xcodeproj -scheme S2 -destination "id=$udid" \
  -derivedDataPath build/DerivedData -quiet ${args[@]+"${args[@]}"}
echo "==> S2 tests passed"
