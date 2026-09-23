#!/usr/bin/env bash
# Three NEXTs fired back to back, without waiting on each other, settle on the fourth track,
# playing, with no load left pending.
source "$(dirname "$0")/_lib.sh"

start_playback
next="am broadcast -f 32 -p ${APP_ID} -a com.simplecityapps.shuttle.debug.NEXT >/dev/null"
adb shell "${next}; ${next}; ${next}"
wait_for 10 "s['queuePosition'] == 3 and s['title'] == 'Playback Four' and s['state'] == 'Playing' \
    and s['reportedState'] == 'Playing' and not s['pendingLoad']"
assert_progressing
pass
