#!/usr/bin/env bash
#
# build-ffmpeg.sh — the FFmpeg decode libraries for S2's iOS player (#588), as DYNAMIC frameworks.
#
# Adapted from Shuttle Podcasts' `mobile/ios/scripts/build-ffmpeg.sh` (podcasts@9ee6e0954), with the
# component list widened from podcast audio (mp3/aac in mp3/mp4) to the formats a music library holds,
# and built shared instead of static: FFmpeg is LGPL, and a dynamically linked copy is what lets a user
# replace it in the app bundle (the relink right). See ios/Playback/README.md, "FFmpeg build".
#
# Output: four xcframeworks, one per FFmpeg library, each holding a dynamic framework per slice
# (iOS arm64, iOS Simulator arm64, and macOS arm64 so `swift test` in ios/Playback runs without a
# simulator):
#
#   libavutil  libswresample  libavcodec  libavformat   .xcframework
#
# A framework is named after its library so `#include <libavcodec/avcodec.h>` resolves through the
# framework search path, and its install name is `@rpath/libavcodec.framework/libavcodec`.
#
# Built once per version of this script (the cache key is its SHA-256, so any change to the pin,
# flags or packaging rebuilds) into the cache, outside git:
#   ${S2_FFMPEG_CACHE:-~/Library/Caches/s2-ffmpeg-ios}/<tag>-<key>/
# with VERSION.txt (tag, commit, flags, library versions), SHA256SUMS of every binary and the LGPL
# text, then copied into ios/Playback/Frameworks/ (gitignored), which Package.swift points at. Reruns
# are idempotent: an up-to-date install does nothing, a new worktree only copies from the cache, and
# every copy is checked against SHA256SUMS first.
#
# LICENCE: plain LGPL v2.1+. No --enable-gpl, no --enable-version3, no --enable-nonfree, and no
# external libraries are linked (the Opus and Vorbis decoders are FFmpeg's own native ones, not
# libopus/libvorbis), so the only third-party code in the frameworks is FFmpeg's LGPL-2.1 tree. The
# app ships the notice and the relink note (ios/S2/Settings.bundle). Do not add a GPL-only component.
#
# Usage:
#   ios/scripts/build-ffmpeg.sh             # build if the cache has no match, then install
#   ios/scripts/build-ffmpeg.sh --force     # rebuild the cache entry even if it exists
#   FFMPEG_SRC=/path/to/ffmpeg ios/scripts/build-ffmpeg.sh   # use an existing checkout of the tag
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
IOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$IOS_DIR/Playback/Frameworks"
CACHE_ROOT="${S2_FFMPEG_CACHE:-$HOME/Library/Caches/s2-ffmpeg-ios}"

# The pinned release. Bump both together; the commit is checked after the clone.
FFMPEG_TAG="n7.1.5"
FFMPEG_COMMIT="3a0867c2bfda4a4d4309ca1a8cbdc6175e67f587"
DEPLOYMENT_TARGET="17.0"
MACOS_DEPLOYMENT_TARGET="14.0"

force=0
for arg in "$@"; do
    case "$arg" in
        --force) force=1 ;;
        -h|--help) sed -n '2,36p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown argument: $arg" >&2; exit 2 ;;
    esac
done

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

# The four libraries the decode path needs, dependencies first.
LIBS=(libavutil libswresample libavcodec libavformat)

# --disable-everything switches off every component; the --enable-* lines are the complete
# allow-list. Decoders: the lossless and lossy codecs S2 plays locally or direct-plays from
# Jellyfin/Emby/Plex, plus the PCM variants WAV and AIFF carry. No network protocols: bytes arrive
# through the AVIO callbacks from `FileByteReader` / `HTTPRangeByteSource`.
# --disable-autodetect: otherwise configure picks up whatever the host offers (VideoToolbox,
# Homebrew's X11 and SDL2 on the macOS slice, bzlib, lzma) and libavutil's hwcontext drags those
# link dependencies in. zlib is the one system library kept: the ID3v2, MP4 `cmov` and matroska
# paths call it, and libavformat links it itself. iconv (Podcasts' static build kept it) is off: its
# only caller is libavcodec's subtitle charset conversion, and no subtitle decoder is built.
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
    --disable-audiotoolbox
    --disable-autodetect
    --enable-zlib
    --enable-decoder=flac,alac,opus,vorbis,mp3,mp3float,aac,aac_latm,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,pcm_f64le,pcm_u8,pcm_s16be,pcm_s24be,pcm_s32be,pcm_f32be,pcm_f64be
    --enable-demuxer=ogg,matroska,wav,flac,mov,mp3,aac,aiff
    --enable-parser=flac,opus,vorbis,mpegaudio,aac,aac_latm
    --enable-swresample
    --enable-avformat
    --enable-avcodec
    --enable-avutil
    --enable-shared
    --disable-static
    --enable-pic
    --enable-small
    --enable-cross-compile
    --target-os=darwin
    --arch=arm64
)

log() { printf '\n=== %s\n' "$*" >&2; }

KEY="$(shasum -a 256 "$0" | cut -c1-12)"
ENTRY="$CACHE_ROOT/$FFMPEG_TAG-$KEY"
WORK="$CACHE_ROOT/work"

# ── FFmpeg source ────────────────────────────────────────────────────────────
fetch_source() {
    FFMPEG="${FFMPEG_SRC:-$CACHE_ROOT/src/ffmpeg-$FFMPEG_TAG}"
    if [[ -z "${FFMPEG_SRC:-}" && ! -f "$FFMPEG/configure" ]]; then
        # A directory without `configure` is an interrupted clone; git refuses a non-empty target.
        rm -rf "$FFMPEG"
        log "Cloning FFmpeg $FFMPEG_TAG into $FFMPEG"
        mkdir -p "$(dirname "$FFMPEG")"
        git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$FFMPEG_TAG" https://git.ffmpeg.org/ffmpeg.git "$FFMPEG"
    fi
    [[ -f "$FFMPEG/configure" ]] || { echo "ERROR: no FFmpeg source at $FFMPEG" >&2; exit 1; }
    local head
    head="$(git -C "$FFMPEG" rev-parse HEAD 2>/dev/null || true)"
    if [[ "$head" != "$FFMPEG_COMMIT" ]]; then
        echo "ERROR: $FFMPEG is at ${head:-<not a git checkout>}, expected $FFMPEG_TAG ($FFMPEG_COMMIT)" >&2
        exit 1
    fi
}

# ── one slice: configure, make, install into $WORK/prefix-$NAME ─────────────
# $1 slice name (device|simulator|macos), $2 SDK, $3 clang -target triple
build_slice() {
    local NAME="$1" SDK="$2" TRIPLE="$3"
    local PREFIX="$WORK/prefix-$NAME" BUILD_DIR="$WORK/build-$NAME"
    local SYSROOT
    SYSROOT="$(xcrun --sdk "$SDK" --show-sdk-path)"

    log "Building FFmpeg for $NAME ($TRIPLE); logs in $WORK"
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
            --extra-ldflags="-target $TRIPLE -isysroot $SYSROOT -Wl,-dead_strip_dylibs" \
            "${CONFIGURE_FLAGS[@]}" >"$WORK/configure-$NAME.log"
        make -j"$(sysctl -n hw.ncpu)" >"$WORK/make-$NAME.log" 2>&1
        make install >>"$WORK/make-$NAME.log" 2>&1
    ) || { echo "ERROR: FFmpeg build for $NAME failed; see $WORK/configure-$NAME.log and $WORK/make-$NAME.log" >&2; exit 1; }
}

# The framework's install name: shallow bundle on iOS, versioned on macOS.
install_name() {
    local NAME="$1" LIB="$2"
    if [[ "$NAME" == macos ]]; then echo "@rpath/$LIB.framework/Versions/A/$LIB"; else echo "@rpath/$LIB.framework/$LIB"; fi
}

# ── one slice: wrap each dylib in a framework under $WORK/frameworks-$NAME ───
package_slice() {
    local NAME="$1" PLATFORM="$2" MIN_OS_KEY="$3" MIN_OS="$4"
    local PREFIX="$WORK/prefix-$NAME" DEST="$WORK/frameworks-$NAME"
    rm -rf "$DEST"
    mkdir -p "$DEST"
    for LIB in "${LIBS[@]}"; do
        local FW="$DEST/$LIB.framework" CONTENTS BINARY PLIST_DIR VERSION
        if [[ "$NAME" == macos ]]; then
            CONTENTS="$FW/Versions/A"
            PLIST_DIR="$CONTENTS/Resources"
        else
            CONTENTS="$FW"
            PLIST_DIR="$FW"
        fi
        mkdir -p "$CONTENTS/Headers" "$PLIST_DIR"
        BINARY="$CONTENTS/$LIB"
        # lib/libavcodec.dylib is a symlink chain to the real libavcodec.61.19.101.dylib.
        cp -L "$PREFIX/lib/$LIB.dylib" "$BINARY"
        cp -R "$PREFIX/include/$LIB/." "$CONTENTS/Headers/"
        VERSION="$(sed -n 's/^Version: //p' "$PREFIX/lib/pkgconfig/$LIB.pc")"

        # FFmpeg names itself and its siblings by prefix path (…/prefix-device/lib/libavutil.59.dylib);
        # rewrite every one of those to the framework's @rpath name.
        chmod u+w "$BINARY"
        install_name_tool -id "$(install_name "$NAME" "$LIB")" "$BINARY" 2>/dev/null
        local dep depname
        while read -r dep; do
            depname="$(basename "$dep")"
            depname="${depname%%.*}"
            install_name_tool -change "$dep" "$(install_name "$NAME" "$depname")" "$BINARY" 2>/dev/null
        done < <(otool -L "$BINARY" | tail -n +2 | awk '{print $1}' | grep "^$PREFIX/lib/")
        if otool -L "$BINARY" | grep -q "$PREFIX"; then
            echo "ERROR: $BINARY still references $PREFIX" >&2
            exit 1
        fi
        strip -x "$BINARY" 2>/dev/null

        cat > "$PLIST_DIR/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>en</string>
    <key>CFBundleExecutable</key><string>$LIB</string>
    <key>CFBundleIdentifier</key><string>org.ffmpeg.$LIB</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>CFBundleName</key><string>$LIB</string>
    <key>CFBundlePackageType</key><string>FMWK</string>
    <key>CFBundleShortVersionString</key><string>$VERSION</string>
    <key>CFBundleVersion</key><string>$VERSION</string>
    <key>CFBundleSupportedPlatforms</key><array><string>$PLATFORM</string></array>
    <key>$MIN_OS_KEY</key><string>$MIN_OS</string>
</dict>
</plist>
PLIST

        if [[ "$NAME" == macos ]]; then
            ln -s A "$FW/Versions/Current"
            ln -s "Versions/Current/$LIB" "$FW/$LIB"
            ln -s Versions/Current/Headers "$FW/Headers"
            ln -s Versions/Current/Resources "$FW/Resources"
        fi
        # The edits above void the linker's ad-hoc signature, and macOS arm64 refuses to load an
        # unsigned dylib. Sign the whole bundle ad hoc; Xcode re-signs the iOS copies with the app's
        # identity when it embeds them.
        codesign --force --sign - "$CONTENTS" 2>/dev/null
    done
}

build_entry() {
    fetch_source
    rm -rf "$WORK"
    mkdir -p "$WORK"
    build_slice device iphoneos "arm64-apple-ios${DEPLOYMENT_TARGET}"
    build_slice simulator iphonesimulator "arm64-apple-ios${DEPLOYMENT_TARGET}-simulator"
    # The macOS slice is not shipped; it lets `swift test` in ios/Playback run without a simulator.
    build_slice macos macosx "arm64-apple-macos${MACOS_DEPLOYMENT_TARGET}"
    package_slice device iPhoneOS MinimumOSVersion "$DEPLOYMENT_TARGET"
    package_slice simulator iPhoneSimulator MinimumOSVersion "$DEPLOYMENT_TARGET"
    package_slice macos MacOSX LSMinimumSystemVersion "$MACOS_DEPLOYMENT_TARGET"

    local STAGE="$ENTRY.partial"
    rm -rf "$STAGE" "$ENTRY"
    mkdir -p "$STAGE"
    for LIB in "${LIBS[@]}"; do
        log "Assembling $LIB.xcframework"
        xcodebuild -create-xcframework \
            -framework "$WORK/frameworks-device/$LIB.framework" \
            -framework "$WORK/frameworks-simulator/$LIB.framework" \
            -framework "$WORK/frameworks-macos/$LIB.framework" \
            -output "$STAGE/$LIB.xcframework" >/dev/null
    done

    # The licence text travels with the binaries.
    cp "$FFMPEG/COPYING.LGPLv2.1" "$STAGE/COPYING.LGPLv2.1"
    {
        echo "FFmpeg $FFMPEG_TAG ($FFMPEG_COMMIT), LGPL v2.1+, dynamic"
        echo "cache key: $KEY (build-ffmpeg.sh SHA-256), iOS $DEPLOYMENT_TARGET, macOS $MACOS_DEPLOYMENT_TARGET"
        for LIB in "${LIBS[@]}"; do
            echo "$LIB $(sed -n 's/^Version: //p' "$WORK/prefix-device/lib/pkgconfig/$LIB.pc")"
        done
        echo "configured: ${CONFIGURE_FLAGS[*]}"
    } > "$STAGE/VERSION.txt"
    (cd "$STAGE" && find . -type f ! -name SHA256SUMS | LC_ALL=C sort | xargs shasum -a 256 > SHA256SUMS)
    mv "$STAGE" "$ENTRY"
    rm -rf "$WORK"
}

# ── build (if needed), then install into ios/Playback/Frameworks ─────────────
if [[ "$force" == 1 || ! -f "$ENTRY/SHA256SUMS" ]]; then
    build_entry
else
    log "Cache hit: $ENTRY"
fi

if [[ -f "$OUT_DIR/VERSION.txt" ]] && cmp -s "$OUT_DIR/VERSION.txt" "$ENTRY/VERSION.txt" \
    && (cd "$OUT_DIR" && shasum -a 256 --quiet -c SHA256SUMS >/dev/null 2>&1); then
    log "ios/Playback/Frameworks is up to date ($FFMPEG_TAG-$KEY)"
else
    (cd "$ENTRY" && shasum -a 256 --quiet -c SHA256SUMS) \
        || { echo "ERROR: $ENTRY fails its checksums; rerun with --force" >&2; exit 1; }
    log "Installing $FFMPEG_TAG-$KEY into $OUT_DIR"
    rm -rf "$OUT_DIR"
    mkdir -p "$OUT_DIR"
    # ditto keeps the macOS frameworks' symlinks and the code signatures intact.
    ditto "$ENTRY" "$OUT_DIR"
fi

for LIB in "${LIBS[@]}"; do
    for slice in ios-arm64 ios-arm64-simulator; do
        printf '%-14s %-20s %8s B\n' "$LIB" "$slice" "$(stat -f %z "$OUT_DIR/$LIB.xcframework/$slice/$LIB.framework/$LIB")"
    done
done
