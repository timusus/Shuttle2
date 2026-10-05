#!/usr/bin/env bash
# Builds the S2 app and its test bundle for the simulator (Debug, `build-for-testing`) into the one
# shared DerivedData, ios/build/DerivedData, so incremental builds stay warm. The build is skipped when
# nothing under ios/ or shared/ (tracked, modified or untracked) and no linked Shared.framework changed
# since the last successful build; the state is a stamp in DerivedData. test.sh and run-sim-server.sh
# call this, then run `test-without-building` / install the built S2.app. Does not relink
# Shared.framework: run ios/scripts/build-framework.sh after a Kotlin change.
#
#   ios/scripts/build-app.sh          # build unless the stamp matches
#   ios/scripts/build-app.sh --force  # build regardless
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(cd "$ios_dir/.." && pwd)"
dd="$ios_dir/build/DerivedData"
stamp="$dd/.s2-build-stamp"
force=0
[[ "${1:-}" == "--force" ]] && force=1

state() {
  cd "$repo"
  {
    git rev-parse HEAD:ios HEAD:shared 2>/dev/null || true
    git diff HEAD -- ios shared | shasum
    git ls-files -o --exclude-standard -z ios shared | xargs -0 shasum 2>/dev/null || true
    # The framework is built outside ios/ (Kotlin in android/ and shared/), so its binary is state too.
    find shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework -type f -exec stat -f '%N %z %m' {} + 2>/dev/null || true
  } | shasum | cut -d' ' -f1
}

current="$(state)"
if [[ "$force" == 0 && -f "$stamp" && "$(cat "$stamp")" == "$current" ]] \
  && compgen -G "$dd/Build/Products/*.xctestrun" >/dev/null; then
  echo "==> S2 build is current (no ios/ or shared/ change since the last build); skipping"
  exit 0
fi

"$ios_dir/scripts/build-ffmpeg.sh" >/dev/null
cd "$ios_dir"
echo "==> xcodebuild build-for-testing -scheme S2 (Debug, simulator)"
rm -f "$stamp"
# One SPM clone cache for every worktree; no index store, which only Xcode's own UI reads.
xcodebuild build-for-testing -project S2.xcodeproj -scheme S2 -configuration Debug \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath "$dd" \
  -clonedSourcePackagesDirPath "$HOME/Library/Caches/s2-spm" COMPILER_INDEX_STORE_ENABLE=NO -quiet
echo "$current" > "$stamp"
