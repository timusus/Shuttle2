#!/usr/bin/env bash
# #196: playlist overflow -> "Export as m3u" opens the system create-document picker. Needs a
# "TestList" playlist (see playlists-multiselect-back.sh's comment for how it's created).
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
s2 PAUSE >/dev/null 2>&1 || true
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/playlist-export-m3u.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
