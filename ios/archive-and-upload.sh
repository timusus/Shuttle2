#!/usr/bin/env bash
set -euo pipefail

# Archive Shuttle Music (S2) and upload it to TestFlight.
#
# One script for both lanes: the tag-driven pipeline (.github/workflows/ios-deploy.yml) and a
# developer's Mac. The workflow passes an App Store Connect API key so signing never needs an
# Apple-ID session; run without the key flags and Xcode's signed-in account does the signing, which
# is today's local behaviour. See DEPLOY.md.
#
# Prerequisites (local lane):
#   1. Signed into Xcode with developer@simplecityapps.com (paid team 9HYNX943MQ; ExportOptions.plist
#      carries it and the archive passes it below), OR the three --api-key-* flags.
#      Either way an "Apple Distribution: Simplecity Apps Pty Ltd" certificate must be in the login
#      keychain: export signs manually with the App Store profiles named in ExportOptions.plist,
#      which scripts/ensure-store-profiles.sh creates/installs when the key flags are given.
#   2. Gradle and the FFmpeg frameworks: scripts/build-framework.sh installs ios/Playback/Frameworks
#      (scripts/build-ffmpeg.sh) and links the Release iosArm64 Shared.framework.
#
# Usage:
#   ./archive-and-upload.sh [--build-number N] [--marketing-version V] [--no-upload]
#                           [--skip-shared-framework]
#                           [--api-key-path P --api-key-id K --api-issuer-id I]
#
#   --build-number N         CFBundleVersion (YYMMDDNN, e.g. 26091601). Without it the archive
#                            carries project.yml's S2_BUILD_NUMBER default.
#   --marketing-version V    CFBundleShortVersionString. Derived from --build-number
#                            (20YY.MM.DD) when omitted.
#   --no-upload              Export the IPA to build/export instead of uploading; used by the
#                            workflow_dispatch smoke run and for local dry runs.
#   --skip-shared-framework  Do not build FFmpeg or link Shared.framework (the workflow does it in
#                            its own step).
#   --api-key-path P         App Store Connect API key (.p8); with --api-key-id and
#                            --api-issuer-id this makes signing and upload headless.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
BUILD_DIR="$PROJECT_DIR/build"
ARCHIVE_PATH="$BUILD_DIR/S2.xcarchive"
EXPORT_PATH="$BUILD_DIR/export"

BUILD_NUMBER=""
MARKETING_VERSION=""
UPLOAD=1
BUILD_SHARED=1
API_KEY_PATH=""
API_KEY_ID=""
API_ISSUER_ID=""

usage() { sed -n '/^# Usage:/,/^$/p' "$0" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
    case "$1" in
        --build-number)         BUILD_NUMBER="$2"; shift 2 ;;
        --marketing-version)    MARKETING_VERSION="$2"; shift 2 ;;
        --no-upload)            UPLOAD=0; shift ;;
        --skip-shared-framework) BUILD_SHARED=0; shift ;;
        --api-key-path)         API_KEY_PATH="$2"; shift 2 ;;
        --api-key-id)           API_KEY_ID="$2"; shift 2 ;;
        --api-issuer-id)        API_ISSUER_ID="$2"; shift 2 ;;
        -h|--help)              usage; exit 0 ;;
        *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [ -n "$BUILD_NUMBER" ] && ! [[ "$BUILD_NUMBER" =~ ^[0-9]{8}$ ]]; then
    echo "--build-number must be eight digits (YYMMDDNN), got '$BUILD_NUMBER'" >&2
    exit 2
fi
if [ -z "$MARKETING_VERSION" ] && [ -n "$BUILD_NUMBER" ]; then
    MARKETING_VERSION="20${BUILD_NUMBER:0:2}.${BUILD_NUMBER:2:2}.${BUILD_NUMBER:4:2}"
fi

# All three key flags or none: a partial set would silently fall back to the Apple-ID session,
# which is exactly the surprise a headless runner cannot recover from.
AUTH_ARGS=()
if [ -n "$API_KEY_PATH$API_KEY_ID$API_ISSUER_ID" ]; then
    if [ -z "$API_KEY_PATH" ] || [ -z "$API_KEY_ID" ] || [ -z "$API_ISSUER_ID" ]; then
        echo "--api-key-path, --api-key-id and --api-issuer-id must be given together" >&2
        exit 2
    fi
    [ -f "$API_KEY_PATH" ] || { echo "API key not found: $API_KEY_PATH" >&2; exit 2; }
    AUTH_ARGS=(
        -authenticationKeyPath "$API_KEY_PATH"
        -authenticationKeyID "$API_KEY_ID"
        -authenticationKeyIssuerID "$API_ISSUER_ID"
    )
    echo "==> Signing with App Store Connect API key $API_KEY_ID"
else
    echo "==> Signing with the Xcode Apple-ID session (no API key given)"
fi

VERSION_ARGS=()
[ -n "$MARKETING_VERSION" ] && VERSION_ARGS+=("S2_MARKETING_VERSION=$MARKETING_VERSION")
[ -n "$BUILD_NUMBER" ] && VERSION_ARGS+=("S2_BUILD_NUMBER=$BUILD_NUMBER")
if [ ${#VERSION_ARGS[@]} -gt 0 ]; then
    echo "==> Version ${MARKETING_VERSION:-<project default>} (${BUILD_NUMBER:-<project default>})"
else
    echo "==> Version: project.yml defaults"
fi

# Feed xcodebuild through xcbeautify when it is installed; the raw log is always kept.
pretty() { if command -v xcbeautify >/dev/null 2>&1; then xcbeautify; else cat; fi; }

mkdir -p "$BUILD_DIR"

if [ "$BUILD_SHARED" = 1 ]; then
    echo "==> Building FFmpeg frameworks and the shared KMP framework (release, iosArm64)..."
    "$SCRIPT_DIR/scripts/build-framework.sh" --device --release -q
fi

echo "==> Resolving SPM packages..."
xcodebuild -resolvePackageDependencies \
    -project "$PROJECT_DIR/S2.xcodeproj" \
    -scheme S2

echo "==> Archiving S2..."
rm -rf "$ARCHIVE_PATH"
xcodebuild archive \
    -project "$PROJECT_DIR/S2.xcodeproj" \
    -scheme S2 \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -archivePath "$ARCHIVE_PATH" \
    -allowProvisioningUpdates \
    ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"} \
    CODE_SIGN_STYLE=Automatic \
    APP_TEAM=9HYNX943MQ \
    ${VERSION_ARGS[@]+"${VERSION_ARGS[@]}"} \
    2>&1 | tee "$BUILD_DIR/archive.log" | pretty
test "${PIPESTATUS[0]}" -eq 0

# The override is the whole point of the tag pipeline: prove it landed in the binary before the
# build number is spent on an upload.
APP_PLIST="$ARCHIVE_PATH/Products/Applications/S2.app/Info.plist"
ARCHIVED_BUILD="$(plutil -extract CFBundleVersion raw -o - "$APP_PLIST")"
ARCHIVED_VERSION="$(plutil -extract CFBundleShortVersionString raw -o - "$APP_PLIST")"
echo "==> Archived CFBundleShortVersionString=$ARCHIVED_VERSION CFBundleVersion=$ARCHIVED_BUILD"
if [ -n "$BUILD_NUMBER" ] && [ "$ARCHIVED_BUILD" != "$BUILD_NUMBER" ]; then
    echo "Archive carries CFBundleVersion $ARCHIVED_BUILD, expected $BUILD_NUMBER" >&2
    exit 1
fi
if [ -n "$MARKETING_VERSION" ] && [ "$ARCHIVED_VERSION" != "$MARKETING_VERSION" ]; then
    echo "Archive carries CFBundleShortVersionString $ARCHIVED_VERSION, expected $MARKETING_VERSION" >&2
    exit 1
fi

EXPORT_OPTIONS="$PROJECT_DIR/ExportOptions.plist"
if [ "$UPLOAD" = 0 ]; then
    # Same options, but the IPA lands in build/export instead of App Store Connect.
    EXPORT_OPTIONS="$BUILD_DIR/ExportOptions-export.plist"
    cp "$PROJECT_DIR/ExportOptions.plist" "$EXPORT_OPTIONS"
    plutil -replace destination -string export "$EXPORT_OPTIONS"
    echo "==> Exporting archive to $EXPORT_PATH (no upload)..."
else
    echo "==> Exporting archive and uploading to App Store Connect..."
fi
# Export signs manually (ExportOptions.plist names the App Store profile) because the team's
# API keys cannot use a cloud-managed distribution certificate. With the key, make sure that
# profile exists for the certificate in this keychain and are installed before xcodebuild looks.
if [ ${#AUTH_ARGS[@]} -gt 0 ]; then
    echo "==> Ensuring App Store provisioning profiles..."
    "$SCRIPT_DIR/scripts/ensure-store-profiles.sh" \
        --api-key-path "$API_KEY_PATH" --api-key-id "$API_KEY_ID" --api-issuer-id "$API_ISSUER_ID"
fi
rm -rf "$EXPORT_PATH"
# -allowProvisioningUpdates is harmless with manual signing and still lets the upload step talk to
# App Store Connect with the key. The system-only PATH matters: Apple's /usr/bin/rsync (openrsync) spawns its server half via PATH,
# and a Homebrew rsync there rejects openrsync's -E flag, which surfaces as "Copy failed".
PATH=/usr/bin:/bin:/usr/sbin:/sbin xcodebuild -exportArchive \
    -archivePath "$ARCHIVE_PATH" \
    -exportPath "$EXPORT_PATH" \
    -exportOptionsPlist "$EXPORT_OPTIONS" \
    -allowProvisioningUpdates \
    ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"} \
    2>&1 | tee "$BUILD_DIR/export.log"
test "${PIPESTATUS[0]}" -eq 0

echo "==> Done"
echo "    Archive: $ARCHIVE_PATH"
echo "    Export:  $EXPORT_PATH"
if [ "$UPLOAD" = 1 ]; then
    echo ""
    echo "The build ($ARCHIVED_VERSION, $ARCHIVED_BUILD) has been uploaded to App Store Connect."
    echo "Visit https://appstoreconnect.apple.com to manage the TestFlight build."
fi
