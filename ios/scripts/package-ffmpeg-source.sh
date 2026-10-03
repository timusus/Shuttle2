#!/usr/bin/env bash
#
# package-ffmpeg-source.sh — the corresponding source for the FFmpeg frameworks Shuttle Music for iOS
# ships (#610), laid out for upload as release assets (LGPL-2.1 section 6: offer the source).
#
# Writes build/ffmpeg-source/ at the repo root:
#   ffmpeg-n7.1.5-<commit>.tar.xz  git archive of FFMPEG_COMMIT: the exact source build-ffmpeg.sh builds
#   ffmpeg-7.1.5.tar.xz            the upstream release tarball, checked against FFMPEG_SHA256
#   ffmpeg-7.1.5.tar.xz.asc        upstream's PGP signature, verified with gpg against the release key
#   build-ffmpeg.sh                the script that builds the frameworks (pins, configure flags, packaging)
#   README.txt                     flags, licence and rebuild steps
#   SHA256SUMS
#
# They are attached to the GitHub release tagged ffmpeg-n7.1.5-source on timusus/Shuttle2 (created as a
# draft, published after review): every file in that directory, README.txt as the release notes.
#
# Bump FFMPEG_VERSION and FFMPEG_SHA256 together with FFMPEG_TAG in build-ffmpeg.sh.

set -euo pipefail

FFMPEG_VERSION="7.1.5"
FFMPEG_SHA256="de668509caf9e35e3cd162473441fdb29538c6d96ed080292b3cf9e6fc5d558f"
FFMPEG_KEY_FPR="FCF986EA15E6E293A5644F10B4322F04D67658D8" # FFmpeg release signing key
BASE_URL="https://ffmpeg.org/releases"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT="$REPO_ROOT/build/ffmpeg-source"
TARBALL="ffmpeg-$FFMPEG_VERSION.tar.xz"

grep -q "^FFMPEG_TAG=\"n${FFMPEG_VERSION//./\\.}\"" "$SCRIPT_DIR/build-ffmpeg.sh" \
    || { echo "ERROR: build-ffmpeg.sh no longer pins n$FFMPEG_VERSION; update this script" >&2; exit 1; }

# FFMPEG_COMMIT is the pin build-ffmpeg.sh checks the source against; fail rather than package nothing.
commit="$(sed -n 's/^FFMPEG_COMMIT="\([0-9a-f]\{40\}\)"$/\1/p' "$SCRIPT_DIR/build-ffmpeg.sh")"
[[ -n "$commit" ]] || { echo "ERROR: could not read FFMPEG_COMMIT from build-ffmpeg.sh" >&2; exit 1; }
ARCHIVE="ffmpeg-n$FFMPEG_VERSION-${commit:0:9}.tar.xz"

# The configure options, read from the build script so the README cannot drift from it: the shared
# CONFIGURE_FLAGS array, and the per-slice flags build_slice passes before it.
flags="$(awk '/^CONFIGURE_FLAGS=\(/{f=1;next} f&&/^\)/{exit} f{gsub(/^ +/,""); print "  " $0}' "$SCRIPT_DIR/build-ffmpeg.sh")"
slice_flags="$(awk '/^ *"\$FFMPEG\/configure" \\$/{f=1;next} f&&/CONFIGURE_FLAGS\[@\]/{exit} f{gsub(/^ +/,""); sub(/ *\\$/,""); print "  " $0}' "$SCRIPT_DIR/build-ffmpeg.sh")"
[[ -n "$flags" && -n "$slice_flags" ]] || { echo "ERROR: could not read the configure flags from build-ffmpeg.sh" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT"
curl -fsSL -o "$OUT/$TARBALL" "$BASE_URL/$TARBALL"
curl -fsSL -o "$OUT/$TARBALL.asc" "$BASE_URL/$TARBALL.asc"

actual="$(shasum -a 256 "$OUT/$TARBALL" | cut -d' ' -f1)"
[[ "$actual" == "$FFMPEG_SHA256" ]] || { echo "ERROR: $TARBALL is $actual, expected $FFMPEG_SHA256" >&2; exit 1; }

# Check the signature against FFmpeg's release key, pinned by fingerprint, in a throwaway keyring.
GNUPGHOME="$(mktemp -d)"
export GNUPGHOME
CLONE="$(mktemp -d)"
trap 'rm -rf "$GNUPGHOME" "$CLONE"' EXIT
curl -fsSL -o "$GNUPGHOME/key.asc" https://ffmpeg.org/ffmpeg-devel.asc
gpg --batch --quiet --import "$GNUPGHOME/key.asc" 2>/dev/null
gpg --batch --status-fd 1 --verify "$OUT/$TARBALL.asc" "$OUT/$TARBALL" 2>/dev/null \
    | grep -q "^\[GNUPG:\] VALIDSIG .* $FFMPEG_KEY_FPR\$" \
    || { echo "ERROR: $TARBALL.asc is not a valid signature by $FFMPEG_KEY_FPR" >&2; exit 1; }

# The exact commit build-ffmpeg.sh builds, as git archive makes it.
git -C "$CLONE" init -q
git -C "$CLONE" fetch -q --depth 1 https://git.ffmpeg.org/ffmpeg.git "$commit"
[[ "$(git -C "$CLONE" rev-parse FETCH_HEAD)" == "$commit" ]] || { echo "ERROR: fetched commit is not $commit" >&2; exit 1; }
git -C "$CLONE" archive --format=tar --prefix="ffmpeg-n$FFMPEG_VERSION/" "$commit" | xz -9 > "$OUT/$ARCHIVE"

cp "$SCRIPT_DIR/build-ffmpeg.sh" "$OUT/"

cat > "$OUT/README.txt" <<README
FFmpeg $FFMPEG_VERSION source for Shuttle Music (iOS)
==================================================

Shuttle Music for iOS decodes audio with FFmpeg n$FFMPEG_VERSION (https://ffmpeg.org), licensed under the
GNU Lesser General Public License, version 2.1 or later. FFmpeg is linked dynamically: the app ships
libavutil, libswresample, libavcodec and libavformat as separate frameworks in S2.app/Frameworks, so
you can replace them with your own build.

Contents
  $ARCHIVE
        the source the frameworks are built from: git tag n$FFMPEG_VERSION, commit $commit,
        unmodified, as 'git archive' of that commit
  $TARBALL
        FFmpeg's own release tarball for the same version (SHA-256 $FFMPEG_SHA256)
  $TARBALL.asc
        its PGP signature, by the FFmpeg release key $FFMPEG_KEY_FPR
  build-ffmpeg.sh
        the script that builds the frameworks (ios/scripts/build-ffmpeg.sh in
        https://github.com/timusus/Shuttle2)
  SHA256SUMS

No patches are applied.

Configure options
build-ffmpeg.sh runs configure once per slice (device, simulator, macOS) from a separate build directory.
Per slice it passes the install prefix, the toolchain and SDK for that slice (SDK and TRIPLE are the
slice's Xcode SDK and clang -target triple):
$slice_flags
followed by the options shared by every slice:
$flags

How to rebuild
  1. Get the source at the pinned commit as a git checkout, which build-ffmpeg.sh requires:
       git clone https://git.ffmpeg.org/ffmpeg.git ffmpeg
       git -C ffmpeg checkout $commit
     ($ARCHIVE holds the same files without git history; build-ffmpeg.sh rejects a directory that is not
     a checkout of that commit.)
  2. With Xcode installed, from a checkout of https://github.com/timusus/Shuttle2 (or this directory):
       FFMPEG_SRC=\$PWD/ffmpeg ios/scripts/build-ffmpeg.sh
  3. Copy the resulting frameworks over those in S2.app/Frameworks and re-sign the app.

The full licence text is in the app (Settings > Acknowledgements) and at
https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
README

( cd "$OUT" && shasum -a 256 "$ARCHIVE" "$TARBALL" "$TARBALL.asc" build-ffmpeg.sh README.txt > SHA256SUMS )
echo "Wrote $OUT"
ls -l "$OUT"
