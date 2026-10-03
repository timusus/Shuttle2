#!/usr/bin/env bash
# Uploads S2's dSYMs to Sentry (org simplecity-apps, project s2-ios; #776) so crashes symbolicate. archive-and-upload.sh
# runs it after a successful archive; run it by hand for an archive or a build products folder:
#
#   ios/scripts/upload-dsyms.sh                                  # ios/build/S2.xcarchive
#   ios/scripts/upload-dsyms.sh path/to/S2.xcarchive
#   ios/scripts/upload-dsyms.sh ios/build/DerivedData/Build/Products/Release-iphoneos
#
# Shared.framework is static, so Kotlin/Native writes no dSYM of its own: its code links into S2's binary, and the
# archive's dsymutil folds Shared.o's debug info into S2.app.dSYM. A Shared.framework.dSYM is uploaded too if one is
# ever there.
#
# SENTRY_AUTH_TOKEN comes from the environment, else from ~/.config/s2-telemetry/ios.env (S2_TELEMETRY_ENV picks
# another file), parsed as generate-telemetry-config.sh does. Without a token or sentry-cli it says why and exits 0:
# a missing upload never fails a release. Never prints the token.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
target="${1:-$repo_root/ios/build/S2.xcarchive}"
env_file="${S2_TELEMETRY_ENV:-$HOME/.config/s2-telemetry/ios.env}"
org=simplecity-apps
project=s2-ios

skip() {
  echo "==> dSYM upload skipped: $1"
  exit 0
}

# The env file is parsed, never sourced: only SENTRY_AUTH_TOKEN is read, and nothing in it runs.
from_file() {
  [[ -r "$env_file" ]] || return 0
  local line
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?$1=" "$env_file" | tail -n 1)" || return 0
  line="${line#*=}"
  line="${line%$'\r'}"
  line="${line#\"}"; line="${line%\"}"
  line="${line#\'}"; line="${line%\'}"
  printf '%s' "$line"
}

[[ -e "$target" ]] || { echo "No archive or build products at $target" >&2; exit 2; }
# An archive keeps its dSYMs in dSYMs/; a build products folder keeps them next to the products
search="$target"
[[ -d "$target/dSYMs" ]] && search="$target/dSYMs"

dsyms=()
[[ -d "$search/S2.app.dSYM" ]] && dsyms+=("$search/S2.app.dSYM")
[[ -d "$search/Shared.framework.dSYM" ]] && dsyms+=("$search/Shared.framework.dSYM")
[[ ${#dsyms[@]} -gt 0 ]] || { echo "No S2.app.dSYM in $search: is DEBUG_INFORMATION_FORMAT dwarf-with-dsym?" >&2; exit 1; }

command -v sentry-cli >/dev/null 2>&1 || skip "sentry-cli not installed (brew install getsentry/tools/sentry-cli)"
token="${SENTRY_AUTH_TOKEN:-$(from_file SENTRY_AUTH_TOKEN)}"
[[ -n "$token" ]] || skip "no SENTRY_AUTH_TOKEN in the environment or $env_file"

echo "==> Uploading ${dsyms[*]##*/} to Sentry $org/$project"
SENTRY_AUTH_TOKEN="$token" sentry-cli debug-files upload --org "$org" --project "$project" --type dsym "${dsyms[@]}"
