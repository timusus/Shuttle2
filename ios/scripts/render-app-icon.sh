#!/usr/bin/env bash
# Renders the app icon PNGs in S2/Assets.xcassets/AppIcon.appiconset from the SVG masters in
# ios/design, which are the source of truth: edit the SVGs, then run this and commit both.
#
#   ios/scripts/render-app-icon.sh
#
# Needs rsvg-convert (brew install librsvg) and ImageMagick (brew install imagemagick). The PNGs
# are flattened onto their own opaque background with the alpha channel removed, since the App
# Store rejects an app icon with transparency.
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
design="$ios_dir/design"
out="$ios_dir/S2/Assets.xcassets/AppIcon.appiconset"

for tool in rsvg-convert magick; do
  command -v "$tool" >/dev/null || { echo "render-app-icon: $tool not found (brew install librsvg imagemagick)" >&2; exit 1; }
done

render() {
  local svg="$1" png="$2"
  local tmp
  tmp="$(mktemp -t s2-icon).png"
  rsvg-convert --width 1024 --height 1024 "$design/$svg" --output "$tmp"
  magick "$tmp" -background black -alpha remove -alpha off -strip "PNG24:$out/$png"
  rm -f "$tmp"
  echo "$out/$png"
}

render AppIcon.svg AppIcon.png
render AppIcon-dark.svg AppIcon-dark.png
render AppIcon-tinted.svg AppIcon-tinted.png
