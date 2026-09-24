#!/usr/bin/env bash
# #223: the Playlists tab shows a fast scroller like the other library tabs. Not assertable by
# text (see the flow's comment), so this check only proves the flow runs; the screenshot in
# tmp/maestro/.../playlists-fast-scroller.png is the actual evidence, reported separately.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
s2 PAUSE >/dev/null 2>&1 || true
out="${CHECKS_ROOT}/tmp/maestro"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/playlists-fast-scroller.yaml" || fail "the Maestro flow failed (output in ${out})"
pass
