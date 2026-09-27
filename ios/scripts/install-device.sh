#!/usr/bin/env bash
# Build the S2 app for a physical iPhone/iPad and install it. Ported from Shuttle Podcasts'
# mobile/ios/scripts/install-device.sh — same device discovery, team default and automatic-signing
# approach; see that script's header for the fuller rationale.
#
# The default team is the paid Apple Developer Program team 9HYNX943MQ (Simplecity Apps Pty Ltd),
# the same team Podcasts ships under. Passing any other team signs with that team instead (a free
# personal team works too): S2 declares no entitlements file and no capability a free team can't
# hold, so unlike Podcasts there is no empty-entitlements swap here — automatic signing is enough,
# but a free team's provisioning profile expires after 7 days where the paid team's does not.
# Either way the device must have trusted the developer profile once
# (Settings > General > VPN & Device Management), and switching teams changes the app's signing
# identity, so iOS refuses to upgrade an install across teams (MismatchedApplicationIdentifierEntitlement)
# — delete the app from the phone before the first install with a new team.
#
# usage: ios/scripts/install-device.sh [DEVICE_ID] [TEAM_ID]
#   DEVICE_ID  from `xcrun devicectl list devices` (default: the owner's iPhone 16)
#   TEAM_ID    a team the signed-in Apple ID holds (default: 9HYNX943MQ)
#
# env:
#   CONFIGURATION  Debug (default) or Release.
#   LAUNCH         1 (default) or 0 to install without launching.
set -euo pipefail

DEVICE="${1:-00008140-000539602E90401C}"
TEAM="${2:-9HYNX943MQ}"
CONFIGURATION="${CONFIGURATION:-Debug}"
LAUNCH="${LAUNCH:-1}"
case "$CONFIGURATION" in
  Debug|Release) ;;
  *) echo "CONFIGURATION must be Debug or Release, got $CONFIGURATION" >&2; exit 2 ;;
esac

IOS_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# xcode-select can point at CommandLineTools, which has no xcodebuild; fall back to an installed Xcode.
if [[ -z "${DEVELOPER_DIR:-}" ]]; then
    current="$(xcode-select -p 2>/dev/null || true)"
    if [[ -z "$current" || ! -x "$current/usr/bin/xcodebuild" ]]; then
        for candidate in /Applications/Xcode.app/Contents/Developer /Applications/Xcode-beta.app/Contents/Developer; do
            if [[ -x "$candidate/usr/bin/xcodebuild" ]]; then export DEVELOPER_DIR="$candidate"; break; fi
        done
        [[ -n "${DEVELOPER_DIR:-}" ]] || { echo "ERROR: no Xcode with xcodebuild found" >&2; exit 1; }
    fi
fi

OUT="${S2_DEVICE_BUILD_DIR:-$HOME/Library/Caches/s2-device}"
mkdir -p "$OUT"

echo "==> Building FFmpeg frameworks"
"$IOS_DIR/scripts/build-ffmpeg.sh"

echo "==> Building the shared framework (device, $CONFIGURATION)"
CONFIG_FLAG=(--device)
[[ "$CONFIGURATION" == "Release" ]] && CONFIG_FLAG+=(--release)
"$IOS_DIR/scripts/build-framework.sh" "${CONFIG_FLAG[@]}" -q

echo "==> Building S2 ($CONFIGURATION) for device $DEVICE with team $TEAM"
(cd "$IOS_DIR" && xcodebuild build \
  -project S2.xcodeproj \
  -scheme S2 \
  -configuration "$CONFIGURATION" \
  -destination "id=$DEVICE" \
  -derivedDataPath "$OUT/dd" \
  -allowProvisioningUpdates \
  -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM="$TEAM" \
  CODE_SIGN_STYLE=Automatic \
  > "$OUT/xcodebuild.log" 2>&1) || { grep -E 'error:|BUILD FAILED' "$OUT/xcodebuild.log" | sort -u | head -30; exit 1; }

APP="$OUT/dd/Build/Products/$CONFIGURATION-iphoneos/S2.app"
echo "==> Installing $APP"
# devicectl is chatty; keep the URL and any error line, but fail on devicectl's own exit code
# (a Wi-Fi transfer can die mid-way and must not be reported as installed).
install_rc=0
xcrun devicectl device install app --device "$DEVICE" "$APP" 2>&1 \
  | { grep -E 'installationURL|rror' || true; } \
  || install_rc=${PIPESTATUS[0]}
if [ "$install_rc" != "0" ]; then
  echo "Install failed (devicectl exit $install_rc): is the phone unlocked and on USB or the same Wi-Fi?" >&2
  exit "$install_rc"
fi
if [ "$LAUNCH" = "1" ]; then
  echo "==> Launching"
  xcrun devicectl device process launch --device "$DEVICE" com.simplecityapps.shuttle.dev \
    || echo "Launch refused: unlock the phone and trust the developer profile, then open the app manually."
else
  echo "==> Installed; not launching (LAUNCH=0)"
fi
