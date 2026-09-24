#!/usr/bin/env bash
# #185: playlist detail multi-select (long-press -> contextual toolbar, back exits selection,
# back again navigates up). Needs a "TestList" playlist with >= 2 songs -- create it once via the
# Songs tab's per-item "Add to Playlist" -> "New Playlist" flow before running this check.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
s2 PAUSE >/dev/null 2>&1 || true
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/playlists-multiselect-back.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
