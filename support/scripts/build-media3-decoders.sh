#!/usr/bin/env bash
#
# Builds the Media3 FLAC and Opus decoder AARs from androidx/media and copies them into
# android/app/libs. Media3 does not publish these modules, so we build them from source.
#
# Usage: support/scripts/build-media3-decoders.sh [media3-version]
#
# The version defaults to the media3 version in gradle/libs.versions.toml. After bumping it,
# run this script, delete the old AARs and update the file names in android/app/build.gradle.kts.
#
# Requires: git, a JDK 17+, the Android SDK (ANDROID_HOME or ANDROID_SDK_ROOT) with CMake 3.21+
# and the NDK below installed (sdkmanager "ndk;$NDK_VERSION" "cmake;3.22.1").

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CATALOG="$REPO_ROOT/gradle/libs.versions.toml"
OUT_DIR="$REPO_ROOT/android/app/libs"

MEDIA3_VERSION="${1:-$(sed -n 's/^media3 = "\(.*\)"/\1/p' "$CATALOG")}"
# NDK r28+ links with 16 KB max page size by default; we also pass the flag explicitly.
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
LIBFLAC_TAG="${LIBFLAC_TAG:-1.5.0}"
LIBOPUS_TAG="${LIBOPUS_TAG:-v1.6.1}"
ABIS='"armeabi-v7a", "arm64-v8a", "x86", "x86_64"'

if [[ -z "$MEDIA3_VERSION" ]]; then
  echo "Could not determine the media3 version; pass it as the first argument." >&2
  exit 1
fi

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [[ ! -d "$SDK_DIR/ndk/$NDK_VERSION" ]]; then
  echo "NDK $NDK_VERSION not found in $SDK_DIR/ndk. Install it with sdkmanager \"ndk;$NDK_VERSION\"." >&2
  exit 1
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/media3-decoders.XXXXXX")"
trap 'rm -rf "$WORK_DIR"' EXIT
echo "Building Media3 $MEDIA3_VERSION decoders in $WORK_DIR"

git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$MEDIA3_VERSION" https://github.com/androidx/media.git "$WORK_DIR/media"
cd "$WORK_DIR/media"
echo "sdk.dir=$SDK_DIR" >local.properties

JNI_FLAC=libraries/decoder_flac/src/main/jni
JNI_OPUS=libraries/decoder_opus/src/main/jni
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$LIBFLAC_TAG" https://github.com/xiph/flac.git "$JNI_FLAC/libflac"
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$LIBOPUS_TAG" https://github.com/xiph/opus.git "$JNI_OPUS/libopus"

# Pin the NDK, restrict to the app's ABIs and force 16 KB page alignment.
for module in decoder_flac decoder_opus; do
  cat >>"libraries/$module/build.gradle.kts" <<EOF

android {
  ndkVersion = "$NDK_VERSION"
  defaultConfig {
    ndk { abiFilters += listOf($ABIS) }
    externalNativeBuild {
      cmake {
        arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
        arguments("-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384")
      }
    }
  }
}
EOF
done

./gradlew --quiet :lib-decoder-flac:assembleRelease :lib-decoder-opus:assembleRelease

READELF="$(ls "$SDK_DIR/ndk/$NDK_VERSION"/toolchains/llvm/prebuilt/*/bin/llvm-readelf | head -1)"
mkdir -p "$OUT_DIR"
for module in flac opus; do
  aar="libraries/decoder_$module/buildout/outputs/aar/lib-decoder-$module-release.aar"
  [[ -f "$aar" ]] || aar="$(find "libraries/decoder_$module" -path '*outputs/aar/*release.aar' | head -1)"
  dest="$OUT_DIR/media3-decoder-$module-$MEDIA3_VERSION.aar"
  cp "$aar" "$dest"

  # Verify every ABI is present and each .so is 16 KB aligned.
  check_dir="$WORK_DIR/check-$module"
  mkdir -p "$check_dir"
  (cd "$check_dir" && unzip -q "$dest" 'jni/*')
  for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    for so in "$check_dir/jni/$abi"/*.so; do
      [[ -f "$so" ]] || { echo "Missing $abi native library in $dest" >&2; exit 1; }
      if "$READELF" -lW "$so" | awk '$1 == "LOAD" && $NF != "0x4000" { bad = 1 } END { exit !bad }'; then
        echo "$so is not 16 KB aligned" >&2
        exit 1
      fi
    done
  done
  echo "Wrote $dest"
done
