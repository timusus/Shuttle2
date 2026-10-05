#!/usr/bin/env bash
# Builds the S2 app and its test bundle for the simulator (Debug, `build-for-testing`) into the one
# shared DerivedData, ios/build/DerivedData, so incremental builds stay warm. The build is skipped when
# the stamp in DerivedData matches: nothing under ios/ or shared/ (tracked, modified or untracked), the
# gitignored local xcconfigs, ios/Playback/Frameworks (file list, sizes, mtimes), the linked
# Shared.framework, Xcode and the simulator SDK are unchanged, and S2.app and the recorded xctestrun
# still exist. test.sh and run-sim-server.sh call this, then run `test-without-building` / install the
# built S2.app. Does not rebuild Shared.framework: run ios/scripts/build-framework.sh after a Kotlin
# change. Shared.framework is linked statically and Xcode doesn't track it as an input, so when its
# mtime or size differs from the stamp (or with --force) the S2 and S2Tests link products in
# DerivedData are deleted first, which makes Xcode rerun those links.
# A per-DerivedData lock (waits up to 10 minutes) keeps concurrent runs apart.
#
#   ios/scripts/build-app.sh          # build unless the stamp matches
#   ios/scripts/build-app.sh --force  # build regardless, relinking
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(cd "$ios_dir/.." && pwd)"
dd="$ios_dir/build/DerivedData"
stamp="$dd/.s2-build-stamp"
products="$dd/Build/Products"
app="$products/Debug-iphonesimulator/S2.app"
framework="$repo/shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework"
force=0
[[ "${1:-}" == "--force" ]] && force=1

# mkdir lock next to DerivedData; a lock whose pid is dead is stale and taken over.
lock="$dd.lock"
mkdir -p "$(dirname "$dd")"
waited=0
until mkdir "$lock" 2>/dev/null; do
  holder="$(cat "$lock/pid" 2>/dev/null || true)"
  if [[ -n "$holder" ]] && ! kill -0 "$holder" 2>/dev/null; then
    rm -f "$lock/pid"
    rmdir "$lock" 2>/dev/null || true
    continue
  fi
  if [[ "$waited" == 0 ]]; then
    echo "==> waiting for the build-app.sh run (pid ${holder:-unknown}) that holds $lock"
  fi
  if (( waited >= 600 )); then
    echo "ERROR: gave up after 10 minutes waiting for the lock $lock (pid ${holder:-unknown})" >&2
    exit 1
  fi
  sleep 5
  waited=$((waited + 5))
done
echo "$$" > "$lock/pid"
trap 'rm -f "$lock/pid"; rmdir "$lock" 2>/dev/null || true' EXIT

# The Shared.framework files' sizes and mtimes: what a relink depends on.
framework_state() {
  find "$framework" -type f -exec stat -f '%N %z %m' {} + 2>/dev/null | sort || true
}

state() {
  cd "$repo"
  {
    git rev-parse HEAD:ios HEAD:shared 2>/dev/null || true
    git diff HEAD -- ios shared | shasum
    git ls-files -o --exclude-standard -z ios shared | xargs -0 shasum 2>/dev/null || true
    # Gitignored inputs: the Telemetry and Last.fm keys, and the Playback frameworks (by listing).
    cat ios/Config/*.local.xcconfig 2>/dev/null || true
    find ios/Playback/Frameworks -type f -exec stat -f '%N %z %m' {} + 2>/dev/null | sort || true
    # The framework is built outside ios/ (Kotlin in android/ and shared/), so its binary is state too.
    framework_state
    xcodebuild -version 2>&1 || true
    xcrun --sdk iphonesimulator --show-sdk-version 2>&1 || true
  } | shasum | cut -d' ' -f1
}

current="$(state)"
current_framework="$(framework_state | shasum | cut -d' ' -f1)"

stamped_state="" stamped_framework="" stamped_xctestrun=""
if [[ -f "$stamp" ]]; then
  stamped_state="$(sed -n 's/^state=//p' "$stamp")"
  stamped_framework="$(sed -n 's/^framework=//p' "$stamp")"
  stamped_xctestrun="$(sed -n 's/^xctestrun=//p' "$stamp")"
fi

if [[ "$force" == 0 && "$stamped_state" == "$current" && -d "$app" \
  && -n "$stamped_xctestrun" && -f "$stamped_xctestrun" ]]; then
  echo "==> S2 build is current (no ios/ or shared/ change since the last build); skipping"
  exit 0
fi

"$ios_dir/scripts/build-ffmpeg.sh" >/dev/null
cd "$ios_dir"
rm -f "$stamp"
if [[ "$force" == 1 || "$stamped_framework" != "$current_framework" ]]; then
  echo "==> Shared.framework changed (or --force): deleting the S2 and S2Tests link products to force a relink"
  rm -f "$app/S2" "$app/S2.debug.dylib" "$app/PlugIns/S2Tests.xctest/S2Tests"
fi
echo "==> xcodebuild build-for-testing -scheme S2 (Debug, simulator)"
# One SPM clone cache for every worktree; no index store, which only Xcode's own UI reads.
xcodebuild build-for-testing -project S2.xcodeproj -scheme S2 -configuration Debug \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath "$dd" \
  -clonedSourcePackagesDirPath "$HOME/Library/Caches/s2-spm" COMPILER_INDEX_STORE_ENABLE=NO -quiet
xctestrun="$(ls -t "$products"/*.xctestrun 2>/dev/null | head -n1)"
[[ -n "$xctestrun" && -d "$app" ]] || { echo "ERROR: the build produced no xctestrun or S2.app" >&2; exit 1; }
printf 'state=%s\nframework=%s\nxctestrun=%s\n' "$current" "$current_framework" "$xctestrun" > "$stamp"
