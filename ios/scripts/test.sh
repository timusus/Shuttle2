#!/usr/bin/env bash
# Runs the iOS tests. Installs the FFmpeg frameworks first (ios/scripts/build-ffmpeg.sh; a no-op once
# installed). Does not relink Shared.framework: run ios/scripts/build-framework.sh after a Kotlin change.
#
#   ios/scripts/test.sh               # the S2 scheme (S2Tests) on an available iPhone simulator
#   ios/scripts/test.sh --package     # `swift test` in ios/Playback, on the Mac (no simulator)
#
# The simulator is $S2_SIMULATOR_UDID if set; else, if the shared lease pool's device.sh exists, this
# session's leased device (set $S2_SIM_HOLDER to lease as a different holder, e.g. for a parallel
# worker, or $S2_SIM_PROFILE=ios26 to lease from the iOS 26 pool — see ios/scripts/lease-sim.sh;
# an unknown profile aborts with exit 2 before anything builds); else an iPhone on a released iOS
# runtime, a booted one first, else the newest (a beta only when there's no other). Other arguments go to
# xcodebuild or `swift test` (e.g. `-only-testing:S2Tests/NowPlayingControllerTests`,
# `--filter MusicPlaybackFormatsTests`).
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
package=0
args=()
for arg in "$@"; do
  case "$arg" in
    --package) package=1 ;;
    -h|--help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) args+=("$arg") ;;
  esac
done

if [[ "$package" == 1 ]]; then
  "$ios_dir/scripts/build-ffmpeg.sh" >/dev/null
  cd "$ios_dir/Playback"
  echo "==> swift test ${args[*]+"${args[*]}"}"
  exec swift test ${args[@]+"${args[@]}"}
fi

if [[ ! -d "$ios_dir/../shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework" ]]; then
  echo "ERROR: no simulator Shared.framework; run ios/scripts/build-framework.sh first" >&2
  exit 1
fi

# A beta runtime's build ends in a letter (27.2 beta is 24B5084k) and fails tests a release passes (#601),
# so a released runtime's iPhone wins even over a booted beta one; a beta only when there's no other.
pick_simulator() {
  xcrun simctl list -j devices available runtimes | python3 -c '
import json, re, sys
listing = json.load(sys.stdin)
beta = {r["identifier"]: r["buildversion"][-1:].isalpha() for r in listing["runtimes"]}
def version(runtime):
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    return (int(m.group(1)), int(m.group(2))) if m else None
candidates = []
for runtime, entries in listing["devices"].items():
    v = version(runtime)
    if v is None:
        continue
    for d in entries:
        if d["name"].startswith("iPhone"):
            candidates.append((not beta.get(runtime, False), d["state"] == "Booted", v, d["name"], d["udid"]))
if not candidates:
    sys.exit("no available iPhone simulator; create one in Xcode > Devices and Simulators")
released, booted, v, name, udid = max(candidates)
print(udid)
notes = ("" if released else ", beta") + (", booted" if booted else "")
print(f"==> simulator: {name} (iOS {v[0]}.{v[1]}{notes})", file=sys.stderr)
'
}

lease_rc=0
udid="${S2_SIMULATOR_UDID:-}"
if [ -z "$udid" ]; then
  udid="$("$ios_dir/scripts/lease-sim.sh")" || lease_rc=$?
  if [ "$lease_rc" -eq 2 ]; then
    exit 2 # unknown S2_SIM_PROFILE; lease-sim.sh already said why
  elif [ "$lease_rc" -ne 0 ]; then
    udid="$(pick_simulator)"
  fi
fi

"$ios_dir/scripts/build-ffmpeg.sh" >/dev/null
cd "$ios_dir"
echo "==> xcodebuild test -scheme S2 -destination id=$udid ${args[*]+"${args[*]}"}"
# One SPM clone cache for every worktree; no index store, which only Xcode's own UI reads.
xcodebuild test -project S2.xcodeproj -scheme S2 -destination "id=$udid" \
  -derivedDataPath build/DerivedData -clonedSourcePackagesDirPath "$HOME/Library/Caches/s2-spm" COMPILER_INDEX_STORE_ENABLE=NO -collect-test-diagnostics never -quiet ${args[@]+"${args[@]}"}
echo "==> S2 tests passed"
