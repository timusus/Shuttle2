#!/usr/bin/env bash
# Links Shared.framework (the :shared KMP module) for the Xcode project in ios/. Run it before every
# Xcode build that follows a Kotlin change: Xcode links whatever framework is on disk and never rebuilds it.
# It also writes the telemetry and Last.fm keys' xcconfigs (ios/scripts/generate-telemetry-config.sh, generate-lastfm-config.sh).
#
#   ios/scripts/build-framework.sh              # Debug, simulator (the default dev loop)
#   ios/scripts/build-framework.sh --device     # Release, device (iosArm64): Debug device builds link it too
#   ios/scripts/build-framework.sh --all        # Debug simulator, Release device
#   ios/scripts/build-framework.sh --release    # Release simulator instead of Debug (device is always Release)
#
# Any other argument goes to Gradle (-q, --offline, ...).
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
config=Debug
targets=(IosSimulatorArm64)
gradle_args=()

for arg in "$@"; do
  case "$arg" in
    --device) targets=(IosArm64) ;;
    --all) targets=(IosSimulatorArm64 IosArm64) ;;
    --release) config=Release ;;
    -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) gradle_args+=("$arg") ;;
  esac
done

tasks=()
for target in "${targets[@]}"; do
  # Unoptimised Kotlin/Native is ~13x slower on a phone (docs/performance/ios-startup.md)
  target_config="$config"
  [[ "$target" == IosArm64 ]] && target_config=Release
  tasks+=(":shared:link${target_config}Framework${target}")
done

cd "$repo_root"
# The Sentry and PostHog keys, from the environment or ~/.config/s2-telemetry/ios.env, into a gitignored xcconfig
ios/scripts/generate-telemetry-config.sh
# The Last.fm API key and secret, from the environment, local.properties or ~/.config/s2-lastfm/ios.env
ios/scripts/generate-lastfm-config.sh
echo "==> ./gradlew ${tasks[*]}"
./gradlew "${tasks[@]}" ${gradle_args[@]+"${gradle_args[@]}"}
