#!/usr/bin/env bash
#
# build-ffmpeg.sh — the FFmpeg decode libraries for S2's iOS player (#588).
#
# Adapted from Shuttle Podcasts' `mobile/ios/scripts/build-ffmpeg.sh` (podcasts@9ee6e0954). Same
# shape — one merged static library per slice, wrapped in an xcframework with a `CFFmpeg` module
# map — with the component list widened from podcast audio (mp3/aac in mp3/mp4) to the formats a
# music library holds.
#
# Output: ios/Playback/Frameworks/FFmpeg.xcframework — ONE static library per platform (arm64
# device, arm64 simulator, arm64 macOS) holding libavformat + libavcodec + libswresample +
# libavutil, plus their headers. Gitignored; ios/Playback/README.md records flags, size and licence.
#
# LICENCE: plain LGPL v2.1+. No --enable-gpl, no --enable-version3, no --enable-nonfree, and no
# external libraries are linked (the Opus and Vorbis decoders are FFmpeg's own native ones, not
# libopus/libvorbis), so the only third-party code in the framework is FFmpeg's LGPL-2.1 tree. The
# app ships the licence text and a relink offer; see README.md. Do not add a GPL-only component.
#
# Usage:
#   ios/Playback/scripts/build-ffmpeg.sh                 # clones n7.1 into $BUILD_ROOT/ffmpeg-src
#   FFMPEG_SRC=/path/to/ffmpeg-n7.1 ios/Playback/scripts/build-ffmpeg.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="${OUT_DIR:-$PACKAGE_DIR/Frameworks}"
BUILD_ROOT="${BUILD_ROOT:-${TMPDIR:-/tmp}/s2-ffmpeg-ios}"

FFMPEG_TAG="${FFMPEG_TAG:-n7.1}"
DEPLOYMENT_TARGET="${DEPLOYMENT_TARGET:-17.0}"
MACOS_DEPLOYMENT_TARGET="${MACOS_DEPLOYMENT_TARGET:-14.0}"

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

# The four libraries the decode path needs, in link order.
LIBS=(libavformat libavcodec libswresample libavutil)

# --disable-everything switches off every component; the --enable-* lines are the complete
# allow-list. Decoders: the lossless and lossy codecs S2 plays locally or direct-plays from
# Jellyfin/Emby/Plex, plus the PCM variants WAV and AIFF carry. No network protocols: bytes arrive
# through the AVIO callbacks from `FileByteReader` / `HTTPRangeByteSource`.
# --disable-autodetect: otherwise configure picks up whatever the host offers (VideoToolbox,
# Homebrew's X11 and SDL2 on the macOS slice, bzlib, lzma) and libavutil's hwcontext drags those
# link dependencies in. zlib and iconv are the two system libraries kept, as in Podcasts: the ID3v2,
# MP4 `cmov` and matroska paths call zlib, and metadata conversion calls iconv.
CONFIGURE_FLAGS=(
    --disable-everything
    --disable-programs
    --disable-doc
    --disable-htmlpages
    --disable-manpages
    --disable-podpages
    --disable-txtpages
    --disable-avdevice
    --disable-swscale
    --disable-postproc
    --disable-avfilter
    --disable-network
    --disable-protocols
    --disable-devices
    --disable-filters
    --disable-bsfs
    --disable-encoders
    --disable-muxers
    --disable-debug
    --disable-symver
    --disable-audiotoolbox
    --disable-autodetect
    --enable-zlib
    --enable-iconv
    --enable-decoder=flac,alac,opus,vorbis,mp3,mp3float,aac,aac_latm,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,pcm_f64le,pcm_u8,pcm_s16be,pcm_s24be,pcm_s32be,pcm_f32be,pcm_f64be
    --enable-demuxer=ogg,matroska,wav,flac,mov,mp3,aac,aiff
    --enable-parser=flac,opus,vorbis,mpegaudio,aac,aac_latm
    --enable-swresample
    --enable-avformat
    --enable-avcodec
    --enable-avutil
    --enable-static
    --disable-shared
    --enable-pic
    --enable-small
    --enable-cross-compile
    --target-os=darwin
    --arch=arm64
)

log() { printf '\n=== %s\n' "$*" >&2; }

# ── FFmpeg source ────────────────────────────────────────────────────────────
mkdir -p "$BUILD_ROOT"
FFMPEG="${FFMPEG_SRC:-$BUILD_ROOT/ffmpeg-src}"
if [ ! -f "$FFMPEG/configure" ]; then
    # A directory without `configure` is an interrupted clone; git refuses a non-empty target.
    if [ -d "$FFMPEG" ]; then
        log "Removing torn FFmpeg clone (no configure) at $FFMPEG"
        rm -rf "$FFMPEG"
    fi
    log "Cloning FFmpeg $FFMPEG_TAG into $FFMPEG"
    git clone --depth 1 --branch "$FFMPEG_TAG" https://git.ffmpeg.org/ffmpeg.git "$FFMPEG"
fi
[ -f "$FFMPEG/configure" ] || { echo "ERROR: no FFmpeg source at $FFMPEG" >&2; exit 1; }

# ── one platform ─────────────────────────────────────────────────────────────
# $1 slice name (device|simulator|macos), $2 SDK, $3 clang -target triple
build_slice() {
    local NAME="$1" SDK="$2" TRIPLE="$3"
    local PREFIX="$BUILD_ROOT/prefix-$NAME"
    local BUILD_DIR="$BUILD_ROOT/build-$NAME"
    local SYSROOT
    SYSROOT="$(xcrun --sdk "$SDK" --show-sdk-path)"

    log "Building FFmpeg for $NAME ($TRIPLE)"
    rm -rf "$BUILD_DIR" "$PREFIX"
    mkdir -p "$BUILD_DIR"
    (
        cd "$BUILD_DIR"
        "$FFMPEG/configure" \
            --prefix="$PREFIX" \
            --cc="xcrun --sdk $SDK clang" \
            --cxx="xcrun --sdk $SDK clang++" \
            --ar="$(xcrun --sdk "$SDK" -f ar)" \
            --ranlib="$(xcrun --sdk "$SDK" -f ranlib)" \
            --sysroot="$SYSROOT" \
            --extra-cflags="-target $TRIPLE -isysroot $SYSROOT -O2 -fno-common" \
            --extra-ldflags="-target $TRIPLE -isysroot $SYSROOT" \
            "${CONFIGURE_FLAGS[@]}" >"$BUILD_ROOT/configure-$NAME.log"
        make -j"$(sysctl -n hw.ncpu)" >"$BUILD_ROOT/make-$NAME.log" 2>&1
        make install >>"$BUILD_ROOT/make-$NAME.log" 2>&1
    )
}

# One static library per slice: an xcframework `-library` slice takes exactly one archive.
merge_slice() {
    local NAME="$1"
    local PREFIX="$BUILD_ROOT/prefix-$NAME"
    local MERGED="$BUILD_ROOT/merged-$NAME"
    rm -rf "$MERGED"
    mkdir -p "$MERGED"
    local ARCHIVES=()
    for l in "${LIBS[@]}"; do ARCHIVES+=("$PREFIX/lib/$l.a"); done
    xcrun libtool -static -o "$MERGED/libffmpeg.a" "${ARCHIVES[@]}" 2>/dev/null
    cp -R "$PREFIX/include" "$MERGED/include"
    # A `-library` xcframework has no module of its own; this names the headers the C decoder uses.
    cat > "$MERGED/include/module.modulemap" <<'MODMAP'
module CFFmpeg {
    header "libavformat/avformat.h"
    header "libavcodec/avcodec.h"
    header "libswresample/swresample.h"
    header "libavutil/avutil.h"
    header "libavutil/opt.h"
    export *
}
MODMAP
}

build_slice device iphoneos "arm64-apple-ios${DEPLOYMENT_TARGET}"
build_slice simulator iphonesimulator "arm64-apple-ios${DEPLOYMENT_TARGET}-simulator"
# The macOS slice is not shipped; it lets `swift test` in ios/Playback run without a simulator.
build_slice macos macosx "arm64-apple-macos${MACOS_DEPLOYMENT_TARGET}"
merge_slice device
merge_slice simulator
merge_slice macos

log "Assembling $OUT_DIR/FFmpeg.xcframework"
rm -rf "$OUT_DIR/FFmpeg.xcframework"
mkdir -p "$OUT_DIR"
xcodebuild -create-xcframework \
    -library "$BUILD_ROOT/merged-device/libffmpeg.a" -headers "$BUILD_ROOT/merged-device/include" \
    -library "$BUILD_ROOT/merged-simulator/libffmpeg.a" -headers "$BUILD_ROOT/merged-simulator/include" \
    -library "$BUILD_ROOT/merged-macos/libffmpeg.a" -headers "$BUILD_ROOT/merged-macos/include" \
    -output "$OUT_DIR/FFmpeg.xcframework" >/dev/null

# The licence text travels with the binary.
cp "$FFMPEG/COPYING.LGPLv2.1" "$OUT_DIR/FFmpeg.xcframework/COPYING.LGPLv2.1"
{
    echo "$FFMPEG_TAG"
    echo "configured: ${CONFIGURE_FLAGS[*]}"
} > "$OUT_DIR/FFmpeg.xcframework/VERSION.txt"

log "Done"
du -sh "$OUT_DIR/FFmpeg.xcframework"
find "$OUT_DIR/FFmpeg.xcframework" -name 'libffmpeg.a' -exec ls -la {} \;
