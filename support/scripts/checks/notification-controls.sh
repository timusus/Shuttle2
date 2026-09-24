#!/usr/bin/env bash
# The media notification's controls (support/maestro/notification-controls.yaml), tapped in the
# expanded shade: it shows "Playback Two" by "Playback Artist"; previous, pause, play and next
# work from it; shuffle and repeat toggle there and the app follows. Then the other way round:
# shuffle and repeat set in the app show on the notification's buttons.
source "$(dirname "$0")/_lib.sh"

emu="${CHECKS_ROOT}/support/scripts/remote-emu.sh"
device="${MAESTRO_DEVICE:-$("$emu" serial)}"
trap 'adb_retry shell cmd statusbar collapse >/dev/null 2>&1 || true' EXIT

start_playback
s2 PAUSE >/dev/null
s2 PLAY_ALL --ei index 1 >/dev/null
s2 PAUSE >/dev/null
s2 SEEK --el ms 0 >/dev/null
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback Two' and s['positionMs'] < 1000"
adb_retry shell cmd statusbar expand-notifications >/dev/null
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/notification-controls.yaml" || fail "the Maestro flow failed (output in ${out})"
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback Two' and s['shuffle'] == 'On' and s['repeat'] == 'All'"

# Set in the app, shown on the notification.
s2 SHUFFLE --ez enabled false >/dev/null
s2 REPEAT --es mode one >/dev/null
wait_for 5 "s['shuffle'] == 'Off' and s['repeat'] == 'One'"
deadline=$(($(date +%s) + 10))
until texts="$("$emu" dump-texts 2>/dev/null)" && grep -q 'desc="Shuffle off"' <<<"$texts" && grep -q 'desc="Repeat one"' <<<"$texts"; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "the notification doesn't show shuffle off and repeat one after setting them in the app"
    sleep 1
done
screenshot a-notification-app-set-shuffle-off-repeat-one
s2 REPEAT --es mode off >/dev/null
pass
