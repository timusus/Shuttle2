#!/usr/bin/env bash
# Runs the iOS tests: ios/scripts/build-app.sh (`build-for-testing`, skipped when ios/ and shared/ are
# unchanged since the last build), then `test-without-building`. Does not relink Shared.framework: run ios/scripts/build-framework.sh after a Kotlin change.
#
#   ios/scripts/test.sh               # the S2 scheme (S2Tests) on an available iPhone simulator
#   ios/scripts/test.sh --package     # `swift test` in ios/Playback, on the Mac (no simulator)
#
# The simulator is $S2_SIMULATOR_UDID if set, else the devtools `sim` pool's (`sim destination`;
# SIM_RUNTIME=26.5 picks the iOS 26 one). Other arguments go to xcodebuild or `swift test`
# (e.g. `-only-testing:S2Tests/NowPlayingPlayerTests`, `--filter StreamWarmUpTests`).
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

if [[ "$package" == 1 ]]; then
  cd "$ios_dir/Playback"
  echo "==> swift test ${args[*]+"${args[*]}"}"
  exec swift test ${args[@]+"${args[@]}"}
fi

if [[ ! -d "$ios_dir/../shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework" ]]; then
  echo "ERROR: no simulator Shared.framework; run ios/scripts/build-framework.sh first" >&2
  exit 1
fi

"$ios_dir/scripts/build-app.sh"
# The exact xctestrun the build recorded in its stamp, never a glob.
xctestrun="$(sed -n 's/^xctestrun=//p' "$ios_dir/build/DerivedData/.s2-build-stamp")"
[[ -n "$xctestrun" && -f "$xctestrun" ]] || { echo "ERROR: no xctestrun recorded by build-app.sh" >&2; exit 1; }

if [ -n "${S2_SIMULATOR_UDID:-}" ]; then
  destination="id=$S2_SIMULATOR_UDID"
else
  destination="$(sim destination)" || { echo "ERROR: no simulator from the sim pool" >&2; exit 1; }
fi

cd "$ios_dir"
echo "==> xcodebuild test-without-building -xctestrun ${xctestrun##*/} -destination $destination ${args[*]+"${args[*]}"}"
# -quiet drops Swift Testing issue lines, so the reasons are read back from a result bundle (a fresh path:
# xcodebuild refuses an existing one).
bundle="${LAND_ARTIFACT_DIR:-$PWD/build}/ios-tests-$(date +%Y%m%d-%H%M%S)-$$.xcresult"
mkdir -p "$(dirname "$bundle")"
test_rc=0
xcodebuild test-without-building -xctestrun "$xctestrun" -destination "$destination" \
  -parallel-testing-enabled NO -derivedDataPath build/DerivedData -collect-test-diagnostics never -resultBundlePath "$bundle" -quiet \
  ${args[@]+"${args[@]}"} || test_rc=$?
if [ "$test_rc" -ne 0 ]; then
  echo "==> result bundle: $bundle" >&2
  python3 "$ios_dir/scripts/xcresult-failures.py" "$bundle" >&2 || true
  exit "$test_rc"
fi
echo "==> S2 tests passed"
