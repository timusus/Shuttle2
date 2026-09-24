#!/usr/bin/env bash
# Shuffle by tap (support/maestro/queue-shuffle.yaml): with the `playback` fixture queued in order
# from "Playback One", a tap on Shuffle keeps the current song first and shuffles the rest, and the
# queue sheet shows that order; skipping next plays the second song shown. Shuffle off restores
# the original order on the still-open queue sheet, with the current song kept.
source "$(dirname "$0")/_lib.sh"

device="${MAESTRO_DEVICE:-$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" serial)}"
start_playback
s2 PAUSE >/dev/null
out="${MAESTRO_OUT:-${CHECKS_ROOT}/tmp/maestro}"
mkdir -p "$out"
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true \
    "${MAESTRO:-$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}" --device "$device" test --test-output-dir "$out" \
    "${CHECKS_ROOT}/support/maestro/queue-shuffle.yaml" || fail "the Maestro flow failed (output in ${out})"
[ "$(state shuffle)" = "On" ] || fail "shuffle is $(state shuffle) after the tap"
shuffled="$(queue_titles)"
echo "  shuffled queue: ${shuffled}"
ordered="Playback One,Playback Two,Playback Three,Playback Four,Playback Five"
[ "${shuffled%%,*}" = "Playback One" ] || fail "the current song isn't first in the shuffled queue: ${shuffled}"
[ "$shuffled" != "$ordered" ] || fail "the queue shows the original order after shuffling"
[ "$(tr ',' '\n' <<<"$shuffled" | sort | tr '\n' ',')" = "$(tr ',' '\n' <<<"$ordered" | sort | tr '\n' ',')" ] \
    || fail "the shuffled queue isn't the same five songs: ${shuffled}"
second="$(cut -d, -f2 <<<"$shuffled")"
s2 NEXT >/dev/null
wait_for 5 "s['title'] == '${second}' and s['queuePosition'] == 1"
s2 PAUSE >/dev/null
s2 SHUFFLE --ez enabled false >/dev/null
wait_for 5 "s['shuffle'] == 'Off' and s['title'] == '${second}'"
restored="$(queue_titles)"
echo "  unshuffled queue: ${restored}"
[ "$restored" = "$ordered" ] || fail "shuffle off didn't restore the original order: ${restored}"
screenshot c-unshuffled-queue
pass
