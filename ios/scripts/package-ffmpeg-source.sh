#!/usr/bin/env bash
#
# package-ffmpeg-source.sh — the corresponding source for the FFmpeg frameworks Shuttle Music for iOS
# ships (#610), laid out for upload as release assets (LGPL-2.1 section 6: offer the source).
#
# Writes build/ffmpeg-source/ at the repo root:
#   ffmpeg-7.1.5.tar.xz       the unmodified upstream release, checked against FFMPEG_SHA256
#   ffmpeg-7.1.5.tar.xz.asc   upstream's PGP signature
#   build-ffmpeg.sh           the script that builds the frameworks (pins, configure flags, packaging)
#   README.txt                flags, licence and rebuild steps
#   SHA256SUMS
#
# They are attached to the GitHub release tagged ffmpeg-n7.1.5-source on timusus/Shuttle2 (created as a
# draft, published after review): every file in that directory, README.txt as the release notes.
#
# Bump FFMPEG_VERSION and FFMPEG_SHA256 together with FFMPEG_TAG in build-ffmpeg.sh.

set -euo pipefail

FFMPEG_VERSION="7.1.5"
FFMPEG_SHA256="de668509caf9e35e3cd162473441fdb29538c6d96ed080292b3cf9e6fc5d558f"
BASE_URL="https://ffmpeg.org/releases"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT="$REPO_ROOT/build/ffmpeg-source"
TARBALL="ffmpeg-$FFMPEG_VERSION.tar.xz"

grep -q "^FFMPEG_TAG=\"n$FFMPEG_VERSION\"" "$SCRIPT_DIR/build-ffmpeg.sh" \
    || { echo "ERROR: build-ffmpeg.sh no longer pins n$FFMPEG_VERSION; update this script" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT"
curl -fsSL -o "$OUT/$TARBALL" "$BASE_URL/$TARBALL"
curl -fsSL -o "$OUT/$TARBALL.asc" "$BASE_URL/$TARBALL.asc"

actual="$(shasum -a 256 "$OUT/$TARBALL" | cut -d' ' -f1)"
[[ "$actual" == "$FFMPEG_SHA256" ]] || { echo "ERROR: $TARBALL is $actual, expected $FFMPEG_SHA256" >&2; exit 1; }

cp "$SCRIPT_DIR/build-ffmpeg.sh" "$OUT/"

# The configure options, read from the build script so the README cannot drift from it.
flags="$(awk '/^CONFIGURE_FLAGS=\(/{f=1;next} f&&/^\)/{exit} f{gsub(/^ +/,""); print "  " $0}' "$SCRIPT_DIR/build-ffmpeg.sh")"
commit="$(sed -n 's/^FFMPEG_COMMIT="\(.*\)"/\1/p' "$SCRIPT_DIR/build-ffmpeg.sh")"

cat > "$OUT/README.txt" <<README
FFmpeg $FFMPEG_VERSION source for Shuttle Music (iOS)
==================================================

Shuttle Music for iOS decodes audio with FFmpeg n$FFMPEG_VERSION (https://ffmpeg.org), licensed under the
GNU Lesser General Public License, version 2.1 or later. FFmpeg is linked dynamically: the app ships
libavutil, libswresample, libavcodec and libavformat as separate frameworks in S2.app/Frameworks, so
you can replace them with your own build.

Contents
  $TARBALL      the unmodified upstream release (SHA-256 $FFMPEG_SHA256)
  $TARBALL.asc  upstream's PGP signature
  build-ffmpeg.sh        the script that builds the frameworks from it (ios/scripts/build-ffmpeg.sh in
                         https://github.com/timusus/Shuttle2)
  SHA256SUMS

No patches are applied. The build script fetches git tag n$FFMPEG_VERSION (commit $commit),
the same source as the tarball.

Configure options (the script adds only the per-slice SDK, prefix and cross-compile flags)
$flags

How to rebuild
  1. Extract $TARBALL, or git clone tag n$FFMPEG_VERSION of https://git.ffmpeg.org/ffmpeg.git.
  2. From a checkout of https://github.com/timusus/Shuttle2, run ios/scripts/build-ffmpeg.sh (this
     directory's copy is identical). It needs Xcode, and builds from a git checkout at the pinned
     commit: set FFMPEG_SRC to your clone, or let it clone the tag itself.
  3. Copy the resulting frameworks over those in S2.app/Frameworks and re-sign the app.

The full licence text is in the app (Settings > Acknowledgements) and at
https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
README

( cd "$OUT" && shasum -a 256 "$TARBALL" "$TARBALL.asc" build-ffmpeg.sh README.txt > SHA256SUMS )
echo "Wrote $OUT"
ls -l "$OUT"
