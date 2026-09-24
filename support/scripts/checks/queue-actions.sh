#!/usr/bin/env bash
# Queue actions by taps (support/maestro/queue-actions.yaml), from One..Five playing "Playback
# One": Play Next on Four, Add to Queue on Two, remove Three from the queue sheet, then drag Five
# up to second. The queue sheet and the player then agree on
# One, Five, Four, Two, Four, Two, still on "Playback One".
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 PAUSE >/dev/null
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/queue-actions.yaml" || fail "the Maestro flow failed (output in ${out})"
shown="$(queue_titles)"
echo "  queue: ${shown}"
[ "$shown" = "Playback One,Playback Five,Playback Four,Playback Two,Playback Four,Playback Two" ] \
    || fail "the queue sheet shows ${shown}"
wait_for 5 "s['queueSize'] == 6 and s['queuePosition'] == 0 and s['title'] == 'Playback One'"
# The player follows the order shown: next is Five, then Four.
s2 NEXT >/dev/null
wait_for 5 "s['title'] == 'Playback Five' and s['queuePosition'] == 1"
s2 NEXT >/dev/null
wait_for 5 "s['title'] == 'Playback Four' and s['queuePosition'] == 2"
s2 PAUSE >/dev/null
pass
