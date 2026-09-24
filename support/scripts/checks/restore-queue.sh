#!/usr/bin/env bash
# The whole queue survives a force-stop mid-song: with "Playback Two" removed and "Playback Three"
# playing at ~0:20, force-stop and relaunch; the app comes back paused on Three near 0:20, and the
# queue sheet (opened by taps, support/maestro/nav/open-queue.yaml) shows One, Three, Four, Five.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 REMOVE_QUEUE_ITEM --ei position 1 >/dev/null
s2 NEXT >/dev/null
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback Three' and s['queueSize'] == 4"
s2 SEEK --el ms 20000 >/dev/null
wait_for 5 "s['positionMs'] >= 21000"
before="$(state positionMs)"
adb shell am force-stop "$APP_ID"
launch_app
# PlaybackInitializer restores the queue asynchronously after the process starts.
wait_for 20 "s['queueSize'] == 4"
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback Three' and s['queuePosition'] == 1"
after="$(state positionMs)"
[ "$after" -ge $((before - 2000)) ] && [ "$after" -le $((before + 2000)) ] \
    || fail "restored at ${after} ms, expected near ${before} ms (the position before the force-stop)"
echo "  restored paused on Playback Three at ${after} ms (${before} ms before the force-stop)"
screenshot f-restored-mini-player
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/nav/open-queue.yaml" || fail "the Maestro flow failed (output in ${out})"
shown="$(queue_titles)"
echo "  restored queue: ${shown}"
[ "$shown" = "Playback One,Playback Three,Playback Four,Playback Five" ] || fail "the queue sheet shows ${shown}"
screenshot f-restored-queue
pass
